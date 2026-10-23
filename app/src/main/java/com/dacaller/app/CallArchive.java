package com.dacaller.app;

import android.Manifest;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;
import android.provider.CallLog;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * Private on-device archive of Android call-log metadata, not audio recordings.
 *
 * Records still in Android's call log remain in the regular Recents tab.
 * The archive lists only snapshots that have disappeared from the device log.
 * Extra retention is measured from the FIRST OBSERVED disappearance (a conservative
 * approximation; Android doesn't report the actual deletion timestamp).
 */
final class CallArchive extends SQLiteOpenHelper {
 static final String PREF="archive_extra";
 static final String OFF="off", MONTH="month", QUARTER="quarter", HALF="half", YEAR="year", FOREVER="forever";
 static final String[] OPTIONS={OFF,MONTH,QUARTER,HALF,YEAR,FOREVER};
 static final int JOB_ID=780234;
 private static final String DB_NAME="calls_archive.db";
 private static final int VERSION=1;
 private static final long DAY=24L*60*60*1000;
 private static volatile long version=0;
 private static volatile int count=0;
 private final Context app;
 CallArchive(Context c){super(c.getApplicationContext(),DB_NAME,null,VERSION);app=c.getApplicationContext();}
 @Override public void onCreate(SQLiteDatabase db){
  db.execSQL("CREATE TABLE saved ("+
    "key TEXT PRIMARY KEY, call_id INTEGER NOT NULL, number TEXT NOT NULL,"+
    "at_ms INTEGER NOT NULL, seconds INTEGER NOT NULL, kind INTEGER NOT NULL,"+
    "last_seen_ms INTEGER NOT NULL, missing_since_ms INTEGER NOT NULL DEFAULT 0)");
  db.execSQL("CREATE INDEX idx_saved_missing ON saved(missing_since_ms, at_ms DESC)");
  db.execSQL("CREATE INDEX idx_saved_seen ON saved(last_seen_ms)");
 }
 @Override public void onUpgrade(SQLiteDatabase db,int old,int next){/* version 1 */}
 static boolean enabled(Context c){return !OFF.equals(settings(c).getString(PREF,OFF));}
 static SharedPreferences settings(Context c){return c.getSharedPreferences("da_caller",0);}
 static long version(){return version;}
 static int knownMissing(){return count;}
 static String key(CallHistory.Entry e){
  return e.id+"|"+e.date+"|"+e.type+"|"+e.number;
 }
 static long cutoff(String policy,long removedAt){
  if(FOREVER.equals(policy))return Long.MAX_VALUE;
  int months=MONTH.equals(policy)?1:QUARTER.equals(policy)?3:HALF.equals(policy)?6:YEAR.equals(policy)?12:0;
  Calendar end=Calendar.getInstance();
  end.setTimeInMillis(removedAt);
  end.add(Calendar.MONTH,months);
  return end.getTimeInMillis();
 }
 /** Called ONLY after a complete and successful system call-log cursor read. */
 static void sync(Context c,List<CallHistory.Entry> all){
  if(!enabled(c))return;
  if(c.checkSelfPermission(Manifest.permission.READ_CALL_LOG)!=PackageManager.PERMISSION_GRANTED)return;
  CallArchive helper=new CallArchive(c);
  SQLiteDatabase db=null;
  try{
   db=helper.getWritableDatabase();
   long now=System.currentTimeMillis();
   // Unique generation prevents duplicated entries and supports processing all rows.
   db.beginTransaction();
   android.database.sqlite.SQLiteStatement upsert=db.compileStatement(
    "INSERT OR REPLACE INTO saved(key,call_id,number,at_ms,seconds,kind,last_seen_ms,missing_since_ms)"+
    " VALUES(?,?,?,?,?,?,?,0)");
   for(CallHistory.Entry e:all){
    upsert.clearBindings();
    upsert.bindString(1,key(e));
    upsert.bindLong(2,e.id);
    upsert.bindString(3,e.number);
    upsert.bindLong(4,e.date);
    upsert.bindLong(5,e.duration);
    upsert.bindLong(6,e.type);
    upsert.bindLong(7,now);
    upsert.executeInsert();
   }
   android.database.sqlite.SQLiteStatement missing=db.compileStatement(
     "UPDATE saved SET missing_since_ms=? WHERE last_seen_ms!=? AND missing_since_ms=0");
   missing.bindLong(1,now);missing.bindLong(2,now);missing.executeUpdateDelete();
   pruneInTransaction(db,settings(c).getString(PREF,OFF),now);
   db.setTransactionSuccessful();
   db.endTransaction();
   db.close();
   db=helper.getReadableDatabase();
   count=readCount(db);
   version++;
  }catch(Exception ignored){
   if(db!=null&&db.inTransaction())try{db.endTransaction();}catch(Exception ignored2){}
  }finally{
   try{helper.close();}catch(Exception ignored){}
  }
 }
 private static void pruneInTransaction(SQLiteDatabase db,String policy,long now){
  if(FOREVER.equals(policy))return;
  if(OFF.equals(policy))return;
  // Filtering via calendar month arithmetic gives correct calendar-based expiry.
  int months=MONTH.equals(policy)?1:QUARTER.equals(policy)?3:HALF.equals(policy)?6:12;
  Calendar limit=Calendar.getInstance();
  limit.setTimeInMillis(now);limit.add(Calendar.MONTH,-months);
  db.delete("saved","missing_since_ms>0 AND missing_since_ms<?",
      new String[]{String.valueOf(limit.getTimeInMillis())});
 }
 private static int readCount(SQLiteDatabase db){
  try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM saved WHERE missing_since_ms>0",null)){
   return c.moveToFirst()?c.getInt(0):0;
  }
 }
 static List<CallHistory.Entry> load(Context c){
  if(!enabled(c))return Collections.emptyList();
  ArrayList<CallHistory.Entry> saved=new ArrayList<>();
  CallArchive helper=new CallArchive(c);
  try(SQLiteDatabase db=helper.getReadableDatabase();
      Cursor rows=db.rawQuery(
       "SELECT call_id,number,at_ms,seconds,kind FROM saved WHERE missing_since_ms>0 ORDER BY at_ms DESC",null)){
    while(rows.moveToNext()){
     saved.add(new CallHistory.Entry(rows.getLong(0),rows.getString(1),
         rows.getLong(2),rows.getLong(3),rows.getInt(4)));
    }
   count=saved.size();
  }catch(Exception ignored){}finally{helper.close();}
  return saved;
 }
 static int count(Context c){
  if(!enabled(c))return 0;
  CallArchive helper=new CallArchive(c);
  try(SQLiteDatabase db=helper.getReadableDatabase()){return readCount(db);}
  catch(Exception ignored){return 0;}finally{helper.close();}
 }
 static void pruneNow(Context c){
  CallArchive helper=new CallArchive(c);
  try(SQLiteDatabase db=helper.getWritableDatabase()){
   db.beginTransaction();
   pruneInTransaction(db,settings(c).getString(PREF,OFF),System.currentTimeMillis());
   db.setTransactionSuccessful();db.endTransaction();
   count=readCount(db);version++;
  }catch(Exception ignored){}finally{helper.close();}
 }
 static void erase(Context c){
  CallArchive helper=new CallArchive(c);
  try(SQLiteDatabase db=helper.getWritableDatabase()){db.delete("saved",null,null);count=0;version++;}
  catch(Exception ignored){}finally{helper.close();}
 }
 static void schedule(Context c){
  try{
   JobScheduler scheduler=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
   if(scheduler==null)return;
   if(!enabled(c)){scheduler.cancel(JOB_ID);return;}
   ComponentName service=new ComponentName(c,ArchiveSyncJob.class);
   JobInfo job=new JobInfo.Builder(JOB_ID,service)
    .setPeriodic(12*60*60*1000L)
    .setPersisted(true)
    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
    .build();
   scheduler.schedule(job);
  }catch(Exception ignored){}
 }
}
