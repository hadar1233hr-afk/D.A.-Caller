package com.dacaller.app;

import static org.junit.Assert.*;
import android.app.Application;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.SQLiteMode;

/** These are real API33 host SQLite operations on synthetic metadata, not emulator claims. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=33) @SQLiteMode(SQLiteMode.Mode.NATIVE)
public final class SettingsArchiveBackupTest {
 private Application app;private SharedPreferences prefs;
 @Before public void setup(){app=RuntimeEnvironment.getApplication();new android.util.AtomicFile(new java.io.File(app.getFilesDir(),"settings_archive_restore.pending")).delete();app.deleteDatabase("calls_archive.db");prefs=SettingsBackup.preferences(app);prefs.edit().clear().putBoolean("dark",false).putString(CallArchive.PREF,CallArchive.OFF).commit();}
 @After public void cleanup(){new android.util.AtomicFile(new java.io.File(app.getFilesDir(),"settings_archive_restore.pending")).delete();app.deleteDatabase("calls_archive.db");}
 private CallArchive.BackupRow row(long id,long seconds,long missing){return new CallArchive.BackupRow(id+"|100|2|synthetic@example.invalid",id,"synthetic@example.invalid",100,seconds,2,200,missing,8);}
 private SettingsBackup.Snapshot backup(CallArchive.BackupRow... rows){
  Map<String,Object> values=new LinkedHashMap<>();values.put("dark",true);values.put("archive_extra",CallArchive.FOREVER);
  return new SettingsBackup.Snapshot(values,new CallArchive.Backup(500,Arrays.asList(rows)));
 }
 private void insert(CallArchive.BackupRow row){try(CallArchive helper=new CallArchive(app)){helper.getWritableDatabase().execSQL("INSERT INTO saved VALUES(?,?,?,?,?,?,?,?,?)",new Object[]{row.key,row.callId,row.number,row.atMs,row.seconds,row.kind,row.lastSeenMs,row.missingSinceMs,row.seenGeneration});}}
 private List<CallArchive.BackupRow> stored()throws Exception{return CallArchive.captureBackup(app).rows;}
 private String encoded(SettingsBackup.Snapshot snapshot)throws Exception{return new String(SettingsBackup.encode(snapshot),StandardCharsets.UTF_8);}
 @Test public void optionalRoundTripPreservesEveryStoredRowAndRetentionMetadataWhileOff()throws Exception{
  CallArchive.BackupRow missing=row(1,13,201),current=row(2,0,0);insert(missing);insert(current);
  long version=CallArchive.version();SettingsBackup.Snapshot captured=SettingsBackup.capture(app,true);
  assertEquals(2,captured.archive.rows.size());assertTrue(captured.archive.capturedAtMs>0);assertEquals(version,CallArchive.version());
  assertTrue(missing.same(captured.archive.rows.get(0)));assertTrue(current.same(captured.archive.rows.get(1)));
  SettingsBackup.Snapshot parsed=SettingsBackup.decode(SettingsBackup.encode(captured));assertEquals(captured.archive.capturedAtMs,parsed.archive.capturedAtMs);
  app.deleteDatabase("calls_archive.db");long before=CallArchive.version();
  CallArchive.MergeResult result=SettingsBackup.restore(app,prefs,parsed,true,id->true);
  assertNotNull(result);assertEquals(2,result.added);assertTrue(CallArchive.version()>before);assertEquals(0,CallArchive.knownMissing());
  assertTrue(missing.same(stored().get(0)));assertTrue(current.same(stored().get(1)));
 }
 @Test public void captureNeverPrunesExpiredRowsAndLegacyOptOutNeverOpensArchive()throws Exception{
  insert(row(1,1,201));prefs.edit().putString(CallArchive.PREF,CallArchive.MONTH).commit();
  assertEquals(1,SettingsBackup.capture(app,true).archive.rows.size());assertEquals(1,stored().size());
  java.io.File db=app.getDatabasePath("calls_archive.db");byte[] before=java.nio.file.Files.readAllBytes(db.toPath());
  String settings=encoded(SettingsBackup.capture(app,false));assertTrue(settings.contains("\"version\": 1"));assertFalse(settings.contains("synthetic@example.invalid"));
  SettingsBackup.Snapshot withArchive=backup(row(2,2,201));assertNotNull(SettingsBackup.restore(app,prefs,withArchive,false,id->true));
  assertArrayEquals(before,java.nio.file.Files.readAllBytes(db.toPath()));assertEquals(1,stored().size());
  app.deleteDatabase("calls_archive.db");assertNull(SettingsBackup.capture(app,false).archive);assertFalse(db.exists());
  assertNotNull(SettingsBackup.restore(app,prefs,withArchive,false,id->true));assertFalse(db.exists());
 }
 @Test public void archiveExportRejectsUnsupportedStoredSchemaWithoutRunningMigration()throws Exception{
  try(SQLiteDatabase db=app.openOrCreateDatabase("calls_archive.db",0,null)){
   db.execSQL("CREATE TABLE saved (key TEXT PRIMARY KEY,call_id INTEGER NOT NULL,number TEXT NOT NULL,at_ms INTEGER NOT NULL,seconds INTEGER NOT NULL,kind INTEGER NOT NULL,last_seen_ms INTEGER NOT NULL,missing_since_ms INTEGER NOT NULL DEFAULT 0)");
   db.execSQL("INSERT INTO saved VALUES(?,?,?,?,?,?,?,?)",new Object[]{"1|100|2|-1",1,"-1",100,13,2,200,201});db.setVersion(1);
  }
  java.io.File file=app.getDatabasePath("calls_archive.db");byte[] before=java.nio.file.Files.readAllBytes(file.toPath());
  try{CallArchive.captureBackup(app);fail("Explicitly reject unsupported schema without mutation");}catch(IOException expected){}
  assertArrayEquals(before,java.nio.file.Files.readAllBytes(file.toPath()));
  try(SQLiteDatabase db=SQLiteDatabase.openDatabase(file.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY)){assertEquals(1,db.getVersion());}
 }
 @Test public void mergeDeduplicatesOnlyExactIdentityAndPreservesExistingConflicts()throws Exception{
  CallArchive.BackupRow existing=row(1,10,201),newRow=row(2,20,202);insert(existing);
  CallArchive.MergeResult result=SettingsBackup.restore(app,prefs,backup(row(1,99,999),newRow),true,id->true);
  assertNotNull(result);assertEquals(1,result.added);assertEquals(0,result.duplicates);assertEquals(1,result.conflicts);
  assertTrue(existing.same(stored().get(0)));assertTrue(newRow.same(stored().get(1)));assertEquals(2,CallArchive.knownMissing());
  result=SettingsBackup.restore(app,prefs,backup(existing,newRow),true,id->true);
  assertEquals(0,result.added);assertEquals(2,result.duplicates);assertEquals(0,result.conflicts);assertEquals(2,stored().size());
 }
 @Test public void corruptArchiveIsRejectedEntirelyBeforeSettingsOrArchiveWritesEvenWhenOptedOut()throws Exception{
  insert(row(9,1,201));Map<String,?> before=prefs.getAll();String good=encoded(backup(row(1,13,201)));
  String[] corrupt={good.replace("\"schema\": 2","\"schema\": 3"),good.replace("\"callId\": 1","\"callId\": 1.0"),
   good.replace("\"callId\": 1","\"callId\": \"1\""),good.replace("\"kind\": 2","\"kind\": 8"),
   good.replace("\"atMs\": 100","\"atMs\": -1"),good.replace("\"seenGeneration\": 8","\"seenGeneration\": 9223372036854775807"),
   good.replace("1|100|2|","7|100|2|"),good.replace("\"seconds\": 13","\"seconds\": 13, \"seconds\": 13"),
   good.replace("\"seconds\": 13","\"seconds\": 9223372036854775808"),good.replace("\"number\": \"synthetic@example.invalid\"","\"number\": \"\\ud800\""),
   good.replace("\"seconds\": 13","\"audio\": true, \"seconds\": 13"),good.replace("\"schema\": 2","\"schema\": 2, \"schema\": 2")};
  for(String input:corrupt){try{SettingsBackup.Snapshot parsed=SettingsBackup.decode(input.getBytes(StandardCharsets.UTF_8));SettingsBackup.restore(app,prefs,parsed,false,id->{fail("Must validate first");return true;});fail(input);}catch(IOException expected){}assertEquals(before,prefs.getAll());assertEquals(1,stored().size());}
  String rowJson=good.substring(good.indexOf("{",good.indexOf("\"records\"")),good.indexOf("\n    ]"));
  String duplicate=good.replace(rowJson,rowJson+","+rowJson);try{SettingsBackup.decode(duplicate.getBytes(StandardCharsets.UTF_8));fail("duplicate");}catch(IOException expected){}
 }
 @Test public void sqlFailureAfterEarlierInsertRollsBackAllRowsAndNeverAppliesSettings()throws Exception{
  insert(row(9,1,201));Map<String,?> before=prefs.getAll();
  try(CallArchive helper=new CallArchive(app)){helper.getWritableDatabase().execSQL("CREATE TRIGGER fail_second BEFORE INSERT ON saved WHEN NEW.call_id=2 BEGIN SELECT RAISE(ABORT,'synthetic failure'); END");}
  try{SettingsBackup.restore(app,prefs,backup(row(1,1,201),row(2,2,202)),true,id->{fail("SQL must finish first");return true;});fail("failure");}catch(IOException expected){}
  assertEquals(before,prefs.getAll());assertEquals(1,stored().size());assertEquals(9,stored().get(0).callId);
 }
 @Test public void preferenceCommitFailureRollsBackStagedArchiveAndExistingSettings()throws Exception{
  insert(row(9,1,201));Map<String,?> before=prefs.getAll();AtomicBoolean failFirst=new AtomicBoolean(true);
  SharedPreferences failing=(SharedPreferences)java.lang.reflect.Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class<?>[]{SharedPreferences.class},(proxy,method,args)->{
   if(!method.getName().equals("edit"))return method.invoke(prefs,args);SharedPreferences.Editor delegate=prefs.edit();
   return java.lang.reflect.Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),new Class<?>[]{SharedPreferences.Editor.class},(editor,operation,parameters)->{
    Object value=operation.invoke(delegate,parameters);if(operation.getName().equals("commit")&&failFirst.getAndSet(false))return false;
    return value instanceof SharedPreferences.Editor?editor:value;
   });
  });
  assertNull(SettingsBackup.restore(app,failing,backup(row(1,1,201)),true,id->true));assertEquals(before,prefs.getAll());assertEquals(1,stored().size());assertEquals(9,stored().get(0).callId);
 }
 @Test public void failureAfterDurablePreferenceWriteRecoversBothStoresAndLauncher()throws Exception{
  insert(row(9,1,201));prefs.edit().putString("launcher_icon","crest").commit();Map<String,?> before=prefs.getAll();
  try(CallArchive helper=new CallArchive(app)){
   SQLiteDatabase db=helper.getWritableDatabase();db.execSQL("CREATE TABLE backup_restore_state (id INTEGER PRIMARY KEY CHECK(id=1),token TEXT NOT NULL)");
   db.execSQL("CREATE TRIGGER fail_marker BEFORE INSERT ON backup_restore_state BEGIN SELECT RAISE(ABORT,'synthetic commit marker failure'); END");
  }
  SettingsBackup.Snapshot source=backup(row(1,1,201));Map<String,Object> values=new LinkedHashMap<>(source.values);values.put("launcher_icon","wave");
  java.util.ArrayList<String> icons=new java.util.ArrayList<>();
  try{SettingsBackup.restore(app,prefs,new SettingsBackup.Snapshot(values,source.archive),true,id->{icons.add(id);return true;});fail("marker failure");}catch(IOException expected){}
  assertEquals(Arrays.asList("wave","crest"),icons);assertEquals(before,prefs.getAll());assertEquals(1,stored().size());assertEquals(9,stored().get(0).callId);assertFalse(SettingsBackup.hasPendingRestore(app));
 }
 @Test public void iconFailureCannotLeaveStagedArchiveOrPreferenceChanges()throws Exception{
  SettingsBackup.Snapshot source=backup(row(1,1,201));Map<String,Object> values=new LinkedHashMap<>(source.values);values.put("launcher_icon","wave");
  java.util.ArrayList<String> icons=new java.util.ArrayList<>();
  assertNull(SettingsBackup.restore(app,prefs,new SettingsBackup.Snapshot(values,source.archive),true,id->{icons.add(id);return "crest".equals(id);}));
  assertEquals(Arrays.asList("wave","crest"),icons);assertTrue(stored().isEmpty());assertFalse(prefs.getBoolean("dark",false));assertFalse(SettingsBackup.hasPendingRestore(app));
 }
 @Test public void failedIconRollbackKeepsJournalAndArchivePausedUntilRecoverySucceeds()throws Exception{
  SettingsBackup.Snapshot source=backup(row(1,1,201));Map<String,Object> values=new LinkedHashMap<>(source.values);values.put("launcher_icon","wave");
  try{SettingsBackup.restore(app,prefs,new SettingsBackup.Snapshot(values,source.archive),true,id->false);fail("Unconfirmed icon rollback must remain pending");}catch(IOException expected){}
  assertTrue(SettingsBackup.hasPendingRestore(app));assertTrue(stored().isEmpty());assertFalse(prefs.getBoolean("dark",false));
  SettingsBackup.recoverPendingRestore(app,id->true);assertFalse(SettingsBackup.hasPendingRestore(app));assertTrue(stored().isEmpty());
 }
 @Test public void boundedRowsAndInvalidInMemoryIdentityFailBeforeDatabaseCreation()throws Exception{
  CallArchive.BackupRow entry=row(1,1,201);CallArchive.Backup huge=new CallArchive.Backup(500,Collections.nCopies(CallArchive.Backup.MAX_ROWS+1,entry));
  for(CallArchive.Backup invalid:Arrays.asList(huge,new CallArchive.Backup(500,Arrays.asList(entry,entry)),new CallArchive.Backup(500,Collections.singletonList(new CallArchive.BackupRow("wrong",1,"",100,0,2,200,201,8))))){
   try{SettingsBackup.restore(app,prefs,new SettingsBackup.Snapshot(backup().values,invalid),true,id->true);fail("invalid");}catch(IOException expected){}
   assertFalse(app.getDatabasePath("calls_archive.db").exists());assertFalse(prefs.getBoolean("dark",false));
  }
 }
 private SettingsBackup.PendingRestore journal()throws Exception{
  Map<String,Object> prior=new LinkedHashMap<>();prior.put("dark",false);prior.put(CallArchive.PREF,CallArchive.MONTH);
  SettingsBackup.PendingRestore journal=new SettingsBackup.PendingRestore(app,java.util.UUID.randomUUID().toString(),"crest",new SettingsBackup.Snapshot(prior),new java.util.HashSet<>(Arrays.asList("note_777","launcher_icon")));
  journal.write();return journal;
 }
 @Test public void reopenedUncommittedRestoreUndoesPreferencesAndKeepsArchiveWithoutPruning()throws Exception{
  insert(row(9,10,201));journal();
  // Simulate death after preference commit but before SQLite transaction commit.
  try(CallArchive helper=new CallArchive(app)){
   SQLiteDatabase db=helper.getWritableDatabase();db.beginTransaction();
   try{db.execSQL("INSERT INTO saved VALUES(?,?,?,?,?,?,?,?,?)",new Object[]{row(1,20,202).key,1,"synthetic@example.invalid",100,20,2,200,202,8});
    prefs.edit().putBoolean("dark",true).putString(CallArchive.PREF,CallArchive.FOREVER).putString("note_777","synthetic staged").putString("launcher_icon","wave").commit();
   }finally{db.endTransaction();}
  }
  assertTrue(SettingsBackup.hasPendingRestore(app));java.util.ArrayList<String> icons=new java.util.ArrayList<>();
  SettingsBackup.recoverPendingRestore(app,id->{icons.add(id);return true;});
  assertEquals(Arrays.asList("crest"),icons);assertFalse(prefs.getBoolean("dark",true));assertEquals(CallArchive.MONTH,prefs.getString(CallArchive.PREF,null));
  assertFalse(prefs.contains("note_777"));assertFalse(prefs.contains("launcher_icon"));assertFalse(SettingsBackup.hasPendingRestore(app));assertEquals(1,stored().size());assertEquals(9,stored().get(0).callId);
 }
 @Test public void reopenedCommittedRestoreKeepsDurablePreferencesAndImportedRows()throws Exception{
  SettingsBackup.PendingRestore journal=journal();
  CallArchive.MergeResult result=CallArchive.mergeBackup(app,backup(row(1,20,202)).archive,journal.token,new CallArchive.RestoreAction(){
   @Override public boolean apply(){return prefs.edit().putBoolean("dark",true).putString(CallArchive.PREF,CallArchive.FOREVER).putString("note_777","keep synthetic").commit();}
  });
  assertEquals(1,result.added);assertTrue(SettingsBackup.hasPendingRestore(app));
  SettingsBackup.recoverPendingRestore(app,id->{fail("Committed restore must retain launcher choice");return false;});
  assertFalse(SettingsBackup.hasPendingRestore(app));assertTrue(prefs.getBoolean("dark",false));assertEquals("keep synthetic",prefs.getString("note_777",null));
  assertEquals(1,stored().size());assertEquals(1,CallArchive.knownMissing());
  SettingsBackup.recoverPendingRestore(app,id->{fail("idempotent");return false;});assertEquals(1,stored().size());
 }
 @Test public void failedJournalCleanupCannotReplayCompletedUndoOverLaterUserEdits()throws Exception{
  insert(row(9,10,201));SettingsBackup.PendingRestore journal=journal();prefs.edit().putBoolean("dark",true).commit();
  java.util.concurrent.atomic.AtomicInteger undos=new java.util.concurrent.atomic.AtomicInteger();
  try{
   CallArchive.recoverBackup(app,journal.token,()->{undos.incrementAndGet();return prefs.edit().putBoolean("dark",false).commit();},()->{throw new IOException("synthetic cleanup failure");});
   fail("Cleanup failure must not report success");
  }catch(IOException expected){}
  assertEquals(1,undos.get());assertTrue(SettingsBackup.hasPendingRestore(app));assertFalse(prefs.getBoolean("dark",true));
  prefs.edit().putBoolean("dark",true).putString("note_777","synthetic user edit after undo").commit();
  SettingsBackup.recoverPendingRestore(app,id->{fail("Completed undo must not replay");return false;});
  assertFalse(SettingsBackup.hasPendingRestore(app));assertTrue(prefs.getBoolean("dark",false));assertEquals("synthetic user edit after undo",prefs.getString("note_777",null));
  assertEquals(1,stored().size());assertEquals(9,stored().get(0).callId);
 }
 @Test public void failedJournalPublicationOrRemovalIsReportedBeforeAnyRestoreMutation()throws Exception{
  java.io.File path=new java.io.File(app.getFilesDir(),"settings_archive_restore.pending");assertTrue(path.mkdir());
  java.io.File child=new java.io.File(path,"synthetic-blocker");java.nio.file.Files.write(child.toPath(),new byte[]{1});Map<String,?> before=prefs.getAll();
  try{
   SettingsBackup.PendingRestore pending=new SettingsBackup.PendingRestore(app,java.util.UUID.randomUUID().toString(),"crest",new SettingsBackup.Snapshot(Collections.singletonMap("dark",false)),Collections.emptySet());
   try{pending.write();fail("AtomicFile rename failure must be detected");}catch(IOException expected){}
   assertEquals(before,prefs.getAll());assertFalse(app.getDatabasePath("calls_archive.db").exists());
   try{pending.clear();fail("AtomicFile delete failure must be detected");}catch(IOException expected){}
   assertTrue(SettingsBackup.hasPendingRestore(app));
  }finally{assertTrue(child.delete());assertTrue(path.delete());new java.io.File(path+".new").delete();}
 }
 @Test public void restoreUpgradesV1WithoutPruningOrReplacingItsExistingIdentity()throws Exception{
  CallArchive.BackupRow original=row(1,13,201);
  try(SQLiteDatabase db=app.openOrCreateDatabase("calls_archive.db",0,null)){
   db.execSQL("CREATE TABLE saved (key TEXT PRIMARY KEY,call_id INTEGER NOT NULL,number TEXT NOT NULL,at_ms INTEGER NOT NULL,seconds INTEGER NOT NULL,kind INTEGER NOT NULL,last_seen_ms INTEGER NOT NULL,missing_since_ms INTEGER NOT NULL DEFAULT 0)");
   db.execSQL("INSERT INTO saved VALUES(?,?,?,?,?,?,?,?)",new Object[]{original.key,original.callId,original.number,original.atMs,original.seconds,original.kind,original.lastSeenMs,original.missingSinceMs});db.setVersion(1);
  }
  CallArchive.MergeResult result=SettingsBackup.restore(app,prefs,backup(row(1,99,999),row(2,20,202)),true,id->true);
  assertEquals(1,result.added);assertEquals(1,result.conflicts);assertEquals(2,stored().size());
  CallArchive.BackupRow migrated=stored().get(0);assertEquals(original.key,migrated.key);assertEquals(13,migrated.seconds);assertEquals(201,migrated.missingSinceMs);assertEquals(0,migrated.seenGeneration);
 }
 @Test public void pendingOrCorruptJournalPreventsEveryDestructiveArchivePath()throws Exception{
  org.robolectric.Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.READ_CALL_LOG);insert(row(9,10,201));journal();
  prefs.edit().putString(CallArchive.PREF,CallArchive.MONTH).commit();long before=CallArchive.version();
  assertEquals(1,CallArchive.loadAt(app,Long.MAX_VALUE).size());assertEquals(1,CallArchive.count(app));CallArchive.pruneNow(app);CallArchive.erase(app);
  CallArchive.syncAt(app,Collections.emptyList(),Long.MAX_VALUE);CallArchive.schedule(app);assertEquals(before,CallArchive.version());assertEquals(1,stored().size());
  try{SettingsBackup.capture(app,false);fail("Must recover first");}catch(IOException expected){}
  java.nio.file.Files.write(new java.io.File(app.getFilesDir(),"settings_archive_restore.pending").toPath(),"invalid synthetic journal".getBytes(StandardCharsets.UTF_8));
  try{SettingsBackup.recoverPendingRestore(app,id->true);fail("corrupt");}catch(IOException expected){}
  assertTrue(SettingsBackup.hasPendingRestore(app));CallArchive.erase(app);assertEquals(1,stored().size());
 }
}
