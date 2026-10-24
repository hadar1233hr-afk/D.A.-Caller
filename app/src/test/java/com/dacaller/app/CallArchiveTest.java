package com.dacaller.app;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.provider.CallLog;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=33)
public class CallArchiveTest {
 private Application app;
 private TimeZone priorZone;
 private CallHistory.Entry entry;
 @Before public void setUp(){
  priorZone=TimeZone.getDefault();TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
  app=RuntimeEnvironment.getApplication();app.deleteDatabase("calls_archive.db");
  shadowOf(app).grantPermissions(Manifest.permission.READ_CALL_LOG);
  CallArchive.settings(app).edit().clear().putString(CallArchive.PREF,CallArchive.MONTH).commit();
  entry=new CallHistory.Entry(7,"+972501234567",at(2024,1,1),65,CallLog.Calls.OUTGOING_TYPE);
 }
 @After public void tearDown(){app.deleteDatabase("calls_archive.db");TimeZone.setDefault(priorZone);}
 private static long at(int year,int month,int day){
  Calendar date=new GregorianCalendar(TimeZone.getTimeZone("UTC"));date.clear();date.set(year,month-1,day,12,0,0);return date.getTimeInMillis();
 }
 @Test public void disappearanceDuringSameMillisecondIsDetected(){
  long now=at(2024,1,31);
  CallArchive.syncAt(app,Arrays.asList(entry,entry),now);
  assertEquals(0,CallArchive.loadAt(app,now).size());
  CallArchive.syncAt(app,Collections.emptyList(),now);
  assertEquals(1,CallArchive.loadAt(app,now).size());
 }
 @Test public void monthEndExpiresAtForwardCalendarBoundary(){
  long missing=at(2024,1,31),expiry=at(2024,2,29);
  CallArchive.syncAt(app,Collections.singletonList(entry),missing-1);
  CallArchive.syncAt(app,Collections.emptyList(),missing);
  assertEquals(expiry,CallArchive.cutoff(CallArchive.MONTH,missing));
  assertEquals(1,CallArchive.loadAt(app,expiry-1).size());
  assertTrue(CallArchive.loadAt(app,expiry).isEmpty());
 }
 @Test public void allPoliciesUseCalendarMonthsAndForeverIsUnbounded(){
  long missing=at(2024,1,31);
  assertEquals(at(2024,2,29),CallArchive.cutoff(CallArchive.MONTH,missing));
  assertEquals(at(2024,4,30),CallArchive.cutoff(CallArchive.QUARTER,missing));
  assertEquals(at(2024,7,31),CallArchive.cutoff(CallArchive.HALF,missing));
  assertEquals(at(2025,1,31),CallArchive.cutoff(CallArchive.YEAR,missing));
  assertEquals(Long.MAX_VALUE,CallArchive.cutoff(CallArchive.FOREVER,missing));
 }
 @Test public void repeatedMissingSyncDoesNotRestartRetentionAndCurrentRowsNeverExpire(){
  CallArchive.syncAt(app,Collections.singletonList(entry),at(2024,1,1));
  assertTrue(CallArchive.loadAt(app,at(2025,1,1)).isEmpty());
  CallArchive.syncAt(app,Collections.emptyList(),at(2024,1,2));
  CallArchive.syncAt(app,Collections.emptyList(),at(2024,1,25));
  assertEquals(1,CallArchive.loadAt(app,at(2024,2,2)-1).size());
  assertTrue(CallArchive.loadAt(app,at(2024,2,2)).isEmpty());
 }
 @Test public void reappearingSystemRowLeavesArchiveAndCanDisappearAgain(){
  long now=at(2024,1,1);
  CallArchive.syncAt(app,Collections.singletonList(entry),now);
  CallArchive.syncAt(app,Collections.emptyList(),now+1);
  assertEquals(1,CallArchive.loadAt(app,now+1).size());
  CallArchive.syncAt(app,Collections.singletonList(entry),now+2);
  assertTrue(CallArchive.loadAt(app,now+2).isEmpty());
  CallArchive.syncAt(app,Collections.emptyList(),now+3);
  assertEquals(1,CallArchive.loadAt(app,now+3).size());
 }
 @Test public void permissionDenialCannotMarkSystemRowsMissing(){
  long now=at(2024,1,1);CallArchive.syncAt(app,Collections.singletonList(entry),now);
  shadowOf(app).denyPermissions(Manifest.permission.READ_CALL_LOG);
  CallArchive.syncAt(app,Collections.emptyList(),now+1);
  assertTrue(CallArchive.loadAt(app,now+1).isEmpty());
  shadowOf(app).grantPermissions(Manifest.permission.READ_CALL_LOG);
  CallArchive.syncAt(app,Collections.emptyList(),now+2);
  assertEquals(1,CallArchive.loadAt(app,now+2).size());
 }
 @Test public void offStopsObservationWithoutErasingExistingPrivateArchive(){
  long now=at(2024,1,1);CallArchive.syncAt(app,Collections.singletonList(entry),now);
  CallArchive.syncAt(app,Collections.emptyList(),now+1);
  CallArchive.settings(app).edit().putString(CallArchive.PREF,CallArchive.OFF).commit();
  assertTrue(CallArchive.loadAt(app,now+1).isEmpty());
  CallArchive.syncAt(app,Collections.emptyList(),now+2);
  CallArchive.settings(app).edit().putString(CallArchive.PREF,CallArchive.FOREVER).commit();
  assertEquals(1,CallArchive.loadAt(app,at(2099,1,1)).size());
  CallArchive.erase(app);assertTrue(CallArchive.loadAt(app,now+3).isEmpty());
 }
 @Test public void migrationCanonicalizesHiddenCurrentRowsWithoutCreatingFalseArchive(){
  long now=at(2024,1,31);
  try(SQLiteDatabase db=app.openOrCreateDatabase("calls_archive.db",0,null)){
   db.execSQL("CREATE TABLE saved (key TEXT PRIMARY KEY,call_id INTEGER NOT NULL,number TEXT NOT NULL,at_ms INTEGER NOT NULL,seconds INTEGER NOT NULL,kind INTEGER NOT NULL,last_seen_ms INTEGER NOT NULL,missing_since_ms INTEGER NOT NULL DEFAULT 0)");
   String[] legacy={"מספר חסוי","-1","-2","-3"};
   for(int i=0;i<legacy.length;i++)db.execSQL("INSERT INTO saved VALUES(?,?,?,?,?,?,?,?)",
     new Object[]{(i+1)+"|"+now+"|1|"+legacy[i],i+1,legacy[i],now,10,1,now-1,0});
   // One genuinely disappeared hidden call must retain its history and expiry.
   db.execSQL("INSERT INTO saved VALUES(?,?,?,?,?,?,?,?)",new Object[]{"9|"+(now-1)+"|1|מספר חסוי",9,"מספר חסוי",now-1,20,1,now-2,now-1});
   db.setVersion(1);
  }
  java.util.ArrayList<CallHistory.Entry> observed=new java.util.ArrayList<>();
  for(int id=1;id<=4;id++)observed.add(new CallHistory.Entry(id,"",now,10,1));
  CallArchive.syncAt(app,observed,now);
  java.util.List<CallHistory.Entry> history=CallArchive.loadAt(app,now);
  assertFalse(CallArchive.lastOperationFailed);assertEquals(1,history.size());
  assertEquals(9,history.get(0).id);assertEquals("",history.get(0).number);
  try(CallArchive helper=new CallArchive(app);Cursor row=helper.getReadableDatabase().rawQuery("SELECT COUNT(*),SUM(CASE WHEN missing_since_ms=0 THEN 1 ELSE 0 END) FROM saved",null)){
   assertTrue(row.moveToFirst());assertEquals(5,row.getInt(0));assertEquals(4,row.getInt(1));
  }
 }
 @Test public void migrationMergesOnlyAliasesAndKeepsEarliestArchivedRetention(){
  long now=at(2024,1,31);
  try(SQLiteDatabase db=app.openOrCreateDatabase("calls_archive.db",0,null)){
   db.execSQL("CREATE TABLE saved (key TEXT PRIMARY KEY,call_id INTEGER NOT NULL,number TEXT NOT NULL,at_ms INTEGER NOT NULL,seconds INTEGER NOT NULL,kind INTEGER NOT NULL,last_seen_ms INTEGER NOT NULL,missing_since_ms INTEGER NOT NULL DEFAULT 0)");
   db.execSQL("INSERT INTO saved VALUES(?,?,?,?,?,?,?,?)",new Object[]{"1|"+now+"|1|-1",1,"-1",now,10,1,now-20,now-10});
   db.execSQL("INSERT INTO saved VALUES(?,?,?,?,?,?,?,?)",new Object[]{"1|"+now+"|1|מספר חסוי",1,"מספר חסוי",now,12,1,now-15,now-5});
   db.execSQL("INSERT INTO saved VALUES(?,?,?,?,?,?,?,?)",new Object[]{"2|"+now+"|1|-1",2,"-1",now,13,1,now-20,0});
   db.execSQL("INSERT INTO saved VALUES(?,?,?,?,?,?,?,?)",new Object[]{"2|"+now+"|1|",2,"",now,13,1,now-15,now-5});
   db.setVersion(1);
  }
  assertEquals(1,CallArchive.loadAt(app,now).size());
  try(CallArchive helper=new CallArchive(app);Cursor row=helper.getReadableDatabase().rawQuery("SELECT call_id,number,seconds,missing_since_ms FROM saved ORDER BY call_id",null)){
   assertTrue(row.moveToFirst());assertEquals(1,row.getLong(0));assertEquals("",row.getString(1));assertEquals(12,row.getLong(2));assertEquals(now-10,row.getLong(3));
   assertTrue(row.moveToNext());assertEquals(2,row.getLong(0));assertEquals(0,row.getLong(3));assertFalse(row.moveToNext());
  }
 }
 @Test public void migrationFromV1PreservesSnapshotAndMissingTimestamp(){
  long missing=at(2024,1,31);
  try(SQLiteDatabase db=app.openOrCreateDatabase("calls_archive.db",0,null)){
   db.execSQL("CREATE TABLE saved (key TEXT PRIMARY KEY,call_id INTEGER NOT NULL,number TEXT NOT NULL,at_ms INTEGER NOT NULL,seconds INTEGER NOT NULL,kind INTEGER NOT NULL,last_seen_ms INTEGER NOT NULL,missing_since_ms INTEGER NOT NULL DEFAULT 0)");
   db.execSQL("INSERT INTO saved VALUES(?,?,?,?,?,?,?,?)",new Object[]{CallArchive.key(entry),entry.id,entry.number,entry.date,entry.duration,entry.type,missing-1,missing});db.setVersion(1);
  }
  assertEquals(1,CallArchive.loadAt(app,missing).size());
  try(CallArchive helper=new CallArchive(app);Cursor row=helper.getReadableDatabase().rawQuery("SELECT missing_since_ms,seen_generation FROM saved",null)){
   assertEquals(2,helper.getReadableDatabase().getVersion());assertTrue(row.moveToFirst());
   assertEquals(missing,row.getLong(0));assertEquals(0,row.getLong(1));
  }
  assertEquals(1,CallArchive.loadAt(app,at(2024,2,29)-1).size());
  assertTrue(CallArchive.loadAt(app,at(2024,2,29)).isEmpty());
 }
}
