package com.dacaller.app;

import android.Manifest;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/** Private, offline call metadata snapshots. Retention starts at first observed disappearance. */
final class CallArchive extends SQLiteOpenHelper {
 static final String PREF="archive_extra";
 static final String OFF="off", MONTH="month", QUARTER="quarter", HALF="half", YEAR="year", FOREVER="forever";
 static final String[] OPTIONS={OFF,MONTH,QUARTER,HALF,YEAR,FOREVER};
 static final int JOB_ID=780234;
 private static final String DB_NAME="calls_archive.db";
 private static final int VERSION=2;
 // Serialize sync, purge and erase: a simultaneous refresh must not resurrect an erased snapshot.
 private static final Object LOCK=new Object();
 private static volatile long version;
 private static volatile int count;
 static volatile boolean lastOperationFailed;
 CallArchive(Context c){super(c.getApplicationContext(),DB_NAME,null,VERSION);}
 @Override public void onCreate(SQLiteDatabase db){
  db.execSQL("CREATE TABLE saved ("+
    "key TEXT PRIMARY KEY, call_id INTEGER NOT NULL, number TEXT NOT NULL,"+
    "at_ms INTEGER NOT NULL, seconds INTEGER NOT NULL, kind INTEGER NOT NULL,"+
    "last_seen_ms INTEGER NOT NULL, missing_since_ms INTEGER NOT NULL DEFAULT 0,"+
    "seen_generation INTEGER NOT NULL DEFAULT 0)");
  createIndexes(db);
 }
 private static void createIndexes(SQLiteDatabase db){
  db.execSQL("CREATE INDEX IF NOT EXISTS idx_saved_missing ON saved(missing_since_ms, at_ms DESC)");
  db.execSQL("CREATE INDEX IF NOT EXISTS idx_saved_seen ON saved(last_seen_ms)");
 }
 @Override public void onUpgrade(SQLiteDatabase db,int old,int next){
  if(old<2){
   db.execSQL("ALTER TABLE saved ADD COLUMN seen_generation INTEGER NOT NULL DEFAULT 0");
   // v1 stored hidden provider numbers as Hebrew text or -1/-2/-3. Re-key those
   // snapshots to the same canonical number used by current reads. Otherwise a
   // still-present call would be mistaken for a disappeared historical record.
   // SQLiteOpenHelper runs this entire migration in a transaction. The temporary
   // copy preserves real history and merges only aliases of the same identity;
   // the earliest retention start survives, and any current alias remains current.
   db.execSQL("CREATE TEMP TABLE da_caller_v1_normalized AS SELECT "+
     "call_id||'|'||at_ms||'|'||kind||'|'||normalized_number AS key,"+
     "call_id,normalized_number AS number,at_ms,MAX(seconds) AS seconds,kind,"+
     "MAX(last_seen_ms) AS last_seen_ms,MIN(missing_since_ms) AS missing_since_ms "+
     "FROM (SELECT *,CASE WHEN TRIM(number) IN ('מספר חסוי','-1','-2','-3') "+
     "THEN '' ELSE TRIM(number) END AS normalized_number FROM saved) "+
     "GROUP BY call_id,at_ms,kind,normalized_number");
   db.execSQL("DELETE FROM saved");
   db.execSQL("INSERT INTO saved(key,call_id,number,at_ms,seconds,kind,last_seen_ms,missing_since_ms,seen_generation) "+
     "SELECT key,call_id,number,at_ms,seconds,kind,last_seen_ms,missing_since_ms,0 FROM da_caller_v1_normalized");
   db.execSQL("DROP TABLE da_caller_v1_normalized");
  }
  createIndexes(db);
 }
 static String policy(Context c){
  String selected=settings(c).getString(PREF,OFF);
  for(String value:OPTIONS)if(value.equals(selected))return value;
  return OFF;
 }
 static boolean enabled(Context c){return !OFF.equals(policy(c));}
 static SharedPreferences settings(Context c){return c.getSharedPreferences("da_caller",Context.MODE_PRIVATE);}
 static long version(){return version;}
 static int knownMissing(){return count;}
 static String key(CallHistory.Entry e){return e.id+"|"+e.date+"|"+e.type+"|"+e.number;}
 static long cutoff(String policy,long removedAt){
  if(FOREVER.equals(policy))return Long.MAX_VALUE;
  int months=MONTH.equals(policy)?1:QUARTER.equals(policy)?3:HALF.equals(policy)?6:YEAR.equals(policy)?12:0;
  Calendar end=Calendar.getInstance();end.setTimeInMillis(removedAt);end.add(Calendar.MONTH,months);
  return end.getTimeInMillis();
 }
 /** Called only after a complete, successful system cursor read. Never pass a partial/failed read. */
 static void sync(Context c,List<CallHistory.Entry> all){syncAt(c,all,System.currentTimeMillis());}
 static void syncAt(Context c,List<CallHistory.Entry> all,long now){
  synchronized(LOCK){
   if(!enabled(c)){count=0;return;}
   if(c.checkSelfPermission(Manifest.permission.READ_CALL_LOG)!=PackageManager.PERMISSION_GRANTED)return;
   try(CallArchive helper=new CallArchive(c)){
    SQLiteDatabase db=helper.getWritableDatabase();
    db.beginTransaction();
    try{
     // A database generation, unlike wall-clock milliseconds, remains distinct for rapid refreshes.
     long generation=DatabaseUtils.longForQuery(db,"SELECT COALESCE(MAX(seen_generation),0)+1 FROM saved",null);
     try(SQLiteStatement upsert=db.compileStatement(
       "INSERT OR REPLACE INTO saved(key,call_id,number,at_ms,seconds,kind,last_seen_ms,missing_since_ms,seen_generation)"+
       " VALUES(?,?,?,?,?,?,?,0,?)")){
      for(CallHistory.Entry e:all){
       upsert.clearBindings();upsert.bindString(1,key(e));upsert.bindLong(2,e.id);
       upsert.bindString(3,e.number);upsert.bindLong(4,e.date);upsert.bindLong(5,e.duration);
       upsert.bindLong(6,e.type);upsert.bindLong(7,now);upsert.bindLong(8,generation);upsert.executeInsert();
      }
     }
     try(SQLiteStatement missing=db.compileStatement(
       "UPDATE saved SET missing_since_ms=? WHERE seen_generation!=? AND missing_since_ms=0")){
      missing.bindLong(1,Math.max(1,now));missing.bindLong(2,generation);missing.executeUpdateDelete();
     }
     pruneInTransaction(db,policy(c),now);db.setTransactionSuccessful();
    }finally{db.endTransaction();}
    count=readCount(db);version++;lastOperationFailed=false;
   }catch(RuntimeException ex){failed(ex);}
  }
 }
 private static void failed(RuntimeException ex){
  lastOperationFailed=true;
  // Do not log numbers or SQL contents from the private archive.
  Log.w("DA_CALLER_ARCHIVE","Archive operation failed: "+ex.getClass().getSimpleName());
 }
 private static void pruneInTransaction(SQLiteDatabase db,String policy,long now){
  if(FOREVER.equals(policy)||OFF.equals(policy))return;
  // Forward calendar arithmetic is essential: Jan 31 + 1 month expires Feb 28,
  // which is not equivalent to subtracting a month from today's date.
  ArrayList<Long> expired=new ArrayList<>();
  try(Cursor rows=db.rawQuery("SELECT DISTINCT missing_since_ms FROM saved WHERE missing_since_ms>0",null)){
   while(rows.moveToNext()){
    long removed=rows.getLong(0);if(cutoff(policy,removed)<=now)expired.add(removed);
   }
  }
  for(long removed:expired)db.delete("saved","missing_since_ms=?",new String[]{Long.toString(removed)});
 }
 private static int readCount(SQLiteDatabase db){
  return (int)DatabaseUtils.longForQuery(db,"SELECT COUNT(*) FROM saved WHERE missing_since_ms>0",null);
 }
 static List<CallHistory.Entry> load(Context c){return loadAt(c,System.currentTimeMillis());}
 static List<CallHistory.Entry> loadAt(Context c,long now){
  synchronized(LOCK){
   if(!enabled(c)){count=0;lastOperationFailed=false;return Collections.emptyList();}
   ArrayList<CallHistory.Entry> saved=new ArrayList<>();
   try(CallArchive helper=new CallArchive(c)){
    SQLiteDatabase db=helper.getWritableDatabase();pruneInTransaction(db,policy(c),now);
    try(Cursor rows=db.rawQuery(
      "SELECT call_id,number,at_ms,seconds,kind FROM saved WHERE missing_since_ms>0 ORDER BY at_ms DESC,call_id DESC",null)){
     while(rows.moveToNext())saved.add(new CallHistory.Entry(rows.getLong(0),rows.getString(1),rows.getLong(2),rows.getLong(3),rows.getInt(4)));
    }
    count=saved.size();lastOperationFailed=false;
   }catch(RuntimeException ex){failed(ex);}
   return saved;
  }
 }
 static int count(Context c){
  synchronized(LOCK){
   if(!enabled(c)){count=0;return 0;}
   try(CallArchive helper=new CallArchive(c)){
    SQLiteDatabase db=helper.getWritableDatabase();pruneInTransaction(db,policy(c),System.currentTimeMillis());
    count=readCount(db);lastOperationFailed=false;return count;
   }catch(RuntimeException ex){failed(ex);return count;}
  }
 }
 static void pruneNow(Context c){
  synchronized(LOCK){
   try(CallArchive helper=new CallArchive(c)){
    SQLiteDatabase db=helper.getWritableDatabase();db.beginTransaction();
    try{pruneInTransaction(db,policy(c),System.currentTimeMillis());db.setTransactionSuccessful();}
    finally{db.endTransaction();}
    count=enabled(c)?readCount(db):0;version++;lastOperationFailed=false;
   }catch(RuntimeException ex){failed(ex);}
  }
 }
 static void erase(Context c){
  synchronized(LOCK){
   try(CallArchive helper=new CallArchive(c)){
    helper.getWritableDatabase().delete("saved",null,null);count=0;version++;lastOperationFailed=false;
   }catch(RuntimeException ex){failed(ex);}
  }
 }
 static void schedule(Context c){
  try{
   JobScheduler scheduler=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
   if(scheduler==null)return;
   if(!enabled(c)){scheduler.cancel(JOB_ID);return;}
   JobInfo job=new JobInfo.Builder(JOB_ID,new ComponentName(c,ArchiveSyncJob.class))
    .setPeriodic(12*60*60*1000L).setPersisted(true).setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE).build();
   scheduler.schedule(job);
  }catch(RuntimeException ex){failed(ex);}
 }
}
