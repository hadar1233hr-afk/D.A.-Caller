package com.dacaller.app;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.SharedPreferences;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=33)
public final class SettingsBackupTest {
 private SharedPreferences prefs;
 @Before public void setup(){prefs=RuntimeEnvironment.getApplication().getSharedPreferences("da_caller",Context.MODE_PRIVATE);prefs.edit().clear().commit();}
 private byte[] file(String settings){return ("{\"format\":\"DA_CALLER_SETTINGS\",\"version\":1,\"package\":\"com.dacaller.app\",\"createdAt\":\"2026-10-25T10:00:00Z\",\"settings\":"+settings+"}").getBytes(StandardCharsets.UTF_8);}
 @Test public void roundTripIncludesEffectiveDefaultsFavoritesUnicodeNotesAndNewPresentationPrefs()throws Exception{
  prefs.edit().putBoolean("dark",true).putString("language","en").putString("launcher_icon","prism")
    .putString("avatar_shape","hexagon").putBoolean("favorites_expanded",false).putBoolean("stats_expanded",true)
    .putStringSet("favorites",new HashSet<>(Arrays.asList("+972501112233","contact:42")))
    .putString("note_0501112233","פגישה ביום א׳ 🕊️\nsecond line").putBoolean("settings_group_open_5",true).commit();
  SettingsBackup.Snapshot original=SettingsBackup.capture(prefs);
  SettingsBackup.Snapshot parsed=SettingsBackup.read(new ByteArrayInputStream(SettingsBackup.encode(original)));
  assertEquals(original.values,parsed.values);assertEquals(2,parsed.favorites());assertEquals(1,parsed.notes());
  assertEquals("pixel",parsed.values.get("call_theme"));assertEquals(65,parsed.values.get("call_button_height"));
  assertEquals("hexagon",parsed.values.get("avatar_shape"));assertTrue(parsed.values.containsKey("call_btn_notes"));
 }
 @Test public void islandPreferencesRoundTripAndLegacyBackupLeavesIslandChoicesUntouched()throws Exception{
  assertEquals(false,SettingsBackup.capture(prefs).values.get("island_outside_app"));
  prefs.edit().putBoolean("island_enabled",false).putString("island_style","emerald").putString("island_size","comfortable")
    .putBoolean("island_show_timer",false).putBoolean("island_show_photo",false).putBoolean("island_animations",false)
    .putBoolean("island_show_mute",false).putBoolean("island_show_speaker",false).putBoolean("island_show_hold",false)
    .putBoolean("island_confirm_end",false).putBoolean("island_outside_app",true).commit();
  SettingsBackup.Snapshot saved=SettingsBackup.decode(SettingsBackup.encode(SettingsBackup.capture(prefs)));
  prefs.edit().putBoolean("island_enabled",true).putString("island_style","apple").putString("island_size","compact")
    .putBoolean("island_show_timer",true).putBoolean("island_show_photo",true).putBoolean("island_animations",true)
    .putBoolean("island_show_mute",true).putBoolean("island_show_speaker",true).putBoolean("island_show_hold",true)
    .putBoolean("island_confirm_end",true).putBoolean("island_outside_app",false).commit();
  assertTrue(SettingsBackup.restore(prefs,saved,id->true));
  for(String key:new String[]{"island_enabled","island_show_timer","island_show_photo","island_animations", "island_show_mute","island_show_speaker","island_show_hold","island_confirm_end"})assertFalse(key,prefs.getBoolean(key,true));
  assertEquals("emerald",prefs.getString("island_style",null));assertEquals("comfortable",prefs.getString("island_size",null));
  assertTrue(SettingsBackup.restore(prefs,SettingsBackup.decode(file("{\"dark\":true}")),id->true));
  assertEquals("Legacy sparse backup must preserve choices absent from its file","emerald",prefs.getString("island_style",null));
  assertFalse(prefs.getBoolean("island_enabled",true));
  assertTrue(prefs.getBoolean("island_outside_app",false));
 }
 @Test public void exportNeverIncludesPermissionsRuntimeFlagsOrLauncherInstanceBindings()throws Exception{
  prefs.edit().putBoolean("role_prompted",true).putBoolean("notifications_prompted",true)
    .putBoolean("settings_permission_asked_android.permission.CALL_PHONE",true)
    .putString("last_call_failure","private diagnostic").putString("widget_phone_17","0501112233")
    .putLong("widget_contact_17",92).putString("widget_name_17","Jane").commit();
  String json=new String(SettingsBackup.encode(SettingsBackup.capture(prefs)),StandardCharsets.UTF_8);
  assertFalse(json.contains("prompted"));assertFalse(json.contains("permission"));assertFalse(json.contains("failure"));assertFalse(json.contains("widget_"));
 }
 @Test public void successfulRestoreReplacesOnlyValuesInFileAndPreservesUnrelatedData()throws Exception{
  prefs.edit().putBoolean("dark",false).putBoolean("role_prompted",true).putString("widget_phone_8","123")
    .putString("note_111","keep note").putString("archive_extra","forever").commit();
  byte[] bytes=file("{\"dark\":true,\"favorites\":[\"+972555\"],\"note_222\":\"new note\"}");
  AtomicInteger iconCalls=new AtomicInteger();assertTrue(SettingsBackup.restore(prefs,SettingsBackup.decode(bytes),id->{iconCalls.incrementAndGet();return true;}));
  assertTrue(prefs.getBoolean("dark",false));assertTrue(prefs.getBoolean("role_prompted",false));
  assertEquals("123",prefs.getString("widget_phone_8",null));assertEquals("keep note",prefs.getString("note_111",null));
  assertEquals("new note",prefs.getString("note_222",null));assertEquals("forever",prefs.getString("archive_extra",null));assertEquals(0,iconCalls.get());
 }
 @Test public void invalidFilesNeverMutatePreferencesOrReachIconSwitch()throws Exception{
  prefs.edit().putBoolean("dark",true).putStringSet("favorites",new HashSet<>(Arrays.asList("111"))).commit();Map<String,?> before=prefs.getAll();
  for(String settings:new String[]{
    "{\"dark\":\"false\"}","{\"dark\":false,\"dark\":true}","{\"dark\":false,\"INTERNET\":true}",
    "{\"role_prompted\":false}","{\"widget_phone_3\":\"444\"}","{\"haptic_strength\":4}",
    "{\"call_button_height\":64}","{\"font_contacts\":5000}","{\"language\":\"xx\"}",
    "{\"launcher_icon\":\"arbitrary.component\"}","{\"avatar_shape\":\"invalid\"}","{\"archive_extra\":\"erase\"}",
    "{\"island_style\":\"remote_overlay\"}","{\"island_size\":\"huge\"}","{\"island_enabled\":\"false\"}",
    "{\"favorites\":[\"111\",\"111\"]}","{\"favorites\":[123]}","{\"favorites\":null}","{\"dark\":1.0}",
    "{\"note_anything\":\"x\"}","{\"stats_expanded\":{}}","{}"}){
   try{SettingsBackup.Snapshot invalid=SettingsBackup.decode(file(settings));SettingsBackup.restore(prefs,invalid,id->{fail("No icon change before validation");return true;});fail(settings);}
   catch(IOException expected){}assertEquals(settings,before,prefs.getAll());
  }
 }
 @Test public void rejectsUnsupportedVersionsOtherProductsDuplicateMetadataAndTrailingData()throws Exception{
  String valid=new String(file("{\"dark\":true}"),StandardCharsets.UTF_8);
  for(String input:new String[]{valid.replace("\"version\":1","\"version\":2"),valid.replace("com.dacaller.app","com.dabusinessbook.app"),
    valid.replace("\"version\":1","\"version\":1,\"version\":1"),valid+"{}",valid.substring(0,valid.length()-1),
    valid.replace("\"version\":1","\"version\":1e0"),valid.replace("\"createdAt\":\"2026-10-25T10:00:00Z\",","")}){
   try{SettingsBackup.decode(input.getBytes(StandardCharsets.UTF_8));fail(input);}catch(IOException expected){}
  }
 }
 @Test public void boundedReaderRejectsLargeFilesMalformedUtf8AndOversizedNotes()throws Exception{
  byte[] large=new byte[SettingsBackup.MAX_BYTES+1];
  try{SettingsBackup.read(new ByteArrayInputStream(large));fail("Oversize");}catch(IOException expected){}
  byte[] malformed=file("{\"note_1\":\"a\"}");
  for(int i=0;i<malformed.length;i++)if(malformed[i]=='a'&&i>110){malformed[i]=(byte)0xC0;break;}
  try{SettingsBackup.decode(malformed);fail("Malformed UTF-8");}catch(IOException expected){}
  try{SettingsBackup.decode(file("{\"note_1\":\""+"x".repeat(20001)+"\"}"));fail("Long note");}catch(IOException expected){}
 }
 @Test public void iconFailureLeavesAllSettingsUntouched()throws Exception{
  prefs.edit().putString("launcher_icon","crest").putBoolean("dark",false).commit();Map<String,?> before=prefs.getAll();
  SettingsBackup.Snapshot snapshot=SettingsBackup.decode(file("{\"dark\":true,\"launcher_icon\":\"wave\"}"));
  assertFalse(SettingsBackup.restore(prefs,snapshot,id->false));assertEquals(before,prefs.getAll());
 }
 @Test public void commitFailureRollsBackInMemoryPreferencesAndLauncher()throws Exception{
  prefs.edit().putString("launcher_icon","crest").putBoolean("dark",false).putString("note_7","original").commit();Map<String,?> before=prefs.getAll();
  // Interface proxies avoid trying to redefine Android's final EditorImpl under Robolectric.
  // The first real commit mutates memory and then reports a disk failure, which is
  // precisely the difficult SharedPreferences failure mode the rollback must handle.
  java.util.concurrent.atomic.AtomicBoolean failFirst=new java.util.concurrent.atomic.AtomicBoolean(true);
  SharedPreferences failing=(SharedPreferences)java.lang.reflect.Proxy.newProxyInstance(
    SharedPreferences.class.getClassLoader(),new Class<?>[]{SharedPreferences.class},(proxy,method,args)->{
     if(!method.getName().equals("edit"))return method.invoke(prefs,args);
     SharedPreferences.Editor delegate=prefs.edit();
     return java.lang.reflect.Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),
       new Class<?>[]{SharedPreferences.Editor.class},(editor,operation,parameters)->{
        Object result=operation.invoke(delegate,parameters);
        if(operation.getName().equals("commit")&&failFirst.getAndSet(false))return false;
        return result instanceof SharedPreferences.Editor?editor:result;
       });
    });
  java.util.ArrayList<String> applied=new java.util.ArrayList<>();
  SettingsBackup.Snapshot snapshot=SettingsBackup.decode(file("{\"dark\":true,\"launcher_icon\":\"orbit\",\"note_7\":\"replace\",\"stats_expanded\":true}"));
  assertFalse(SettingsBackup.restore(failing,snapshot,id->{applied.add(id);return true;}));
  assertEquals(Arrays.asList("orbit","crest"),applied);assertEquals(before,prefs.getAll());
 }
 @Test public void snapshotIsDetachedAndImmutableEvenForFavorites()throws Exception{
  prefs.edit().putStringSet("favorites",new HashSet<>(Arrays.asList("111"))).commit();SettingsBackup.Snapshot snapshot=SettingsBackup.capture(prefs);
  prefs.edit().putStringSet("favorites",new HashSet<>(Arrays.asList("222"))).commit();assertEquals(new HashSet<>(Arrays.asList("111")),snapshot.values.get("favorites"));
  try{snapshot.values.put("dark",true);fail("Immutable map");}catch(UnsupportedOperationException expected){}
  try{((java.util.Set<String>)snapshot.values.get("favorites")).add("333");fail("Immutable set");}catch(UnsupportedOperationException expected){}
 }
 @Test public void malformedInMemorySettingsAreRejectedBeforeExport()throws Exception{
  prefs.edit().putString("dark","wrong type").commit();
  try{SettingsBackup.capture(prefs);fail("Corrupt settings");}catch(IOException expected){}
 }
 @Test public void exportingAndRestoringRetentionDoesNotReadDeleteOrRewriteArchiveDatabase()throws Exception{
  android.app.Application app=RuntimeEnvironment.getApplication();app.deleteDatabase("calls_archive.db");
  org.robolectric.Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.READ_CALL_LOG);
  try{
   prefs.edit().putString(CallArchive.PREF,CallArchive.FOREVER).commit();long now=System.currentTimeMillis();
   CallArchive.syncAt(app,Arrays.asList(new CallHistory.Entry(88,"+972509876543",now,123,2)),now);
   CallArchive.syncAt(app,java.util.Collections.emptyList(),now+1);
   java.io.File file=app.getDatabasePath("calls_archive.db");assertTrue(file.isFile());
   byte[] before=java.nio.file.Files.readAllBytes(file.toPath());
   String exported=new String(SettingsBackup.encode(SettingsBackup.capture(prefs)),StandardCharsets.UTF_8);
   assertFalse("Archive phone metadata is excluded from settings backup",exported.contains("+972509876543"));
   SettingsBackup.Snapshot snapshot=SettingsBackup.decode(file("{\"archive_extra\":\"month\",\"dark\":true}"));
   assertTrue(SettingsBackup.restore(prefs,snapshot,id->true));assertEquals(CallArchive.MONTH,prefs.getString(CallArchive.PREF,null));
   assertArrayEquals("Restoring retention must never prune or rewrite existing history",before,java.nio.file.Files.readAllBytes(file.toPath()));
  }finally{app.deleteDatabase("calls_archive.db");}
 }
}
