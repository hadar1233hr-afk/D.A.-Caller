package com.dacaller.app;

import static org.junit.Assert.*;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.SQLiteMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowContentResolver;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class) @Config(sdk=33) @GraphicsMode(GraphicsMode.Mode.NATIVE) @SQLiteMode(SQLiteMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public final class SettingsBackupActivityTest {
 private Context app;private SharedPreferences prefs;
 @Before public void setup(){
  app=RuntimeEnvironment.getApplication();prefs=app.getSharedPreferences("da_caller",Context.MODE_PRIVATE);
  prefs.edit().clear().putBoolean("dark",true).putString("note_111","private note").commit();
 }
 @Test public void exportUsesAndroidCreateDocumentWithoutBroadStoragePermission(){
  try(ActivityController<SettingsBackupActivity> ctl=Robolectric.buildActivity(SettingsBackupActivity.class,SettingsBackupActivity.intent(app,true)).setup()){
   CheckBox archive=find(ctl.get().getWindow().getDecorView(),CheckBox.class);assertNotNull(archive);assertFalse(archive.isChecked());
   assertWineAndRoundedFont(archive,0xFFC76589);
   assertNull("Export options must precede the document picker",shadowOf(ctl.get()).getNextStartedActivity());
   clickExport(ctl.get());
   Intent picker=shadowOf(ctl.get()).getNextStartedActivity();
   assertEquals(Intent.ACTION_CREATE_DOCUMENT,picker.getAction());assertEquals("application/json",picker.getType());
   assertTrue(picker.hasCategory(Intent.CATEGORY_OPENABLE));assertTrue(picker.getStringExtra(Intent.EXTRA_TITLE).endsWith(".json"));
   assertNull(picker.getData());
  }
 }
 @Test public void exportArchiveChoiceSurvivesRotationAndPickerCancellationPreservesData(){
  Map<String,?> before=prefs.getAll();
  try(ActivityController<SettingsBackupActivity> ctl=Robolectric.buildActivity(SettingsBackupActivity.class,SettingsBackupActivity.intent(app,true)).setup()){
   find(ctl.get().getWindow().getDecorView(),CheckBox.class).setChecked(true);ctl.recreate();
   assertTrue(find(ctl.get().getWindow().getDecorView(),CheckBox.class).isChecked());clickExport(ctl.get());
   assertEquals(Intent.ACTION_CREATE_DOCUMENT,shadowOf(ctl.get()).getNextStartedActivity().getAction());
   ctl.get().onActivityResult(731,Activity.RESULT_CANCELED,null);assertTrue(ctl.get().isFinishing());assertEquals(before,prefs.getAll());
  }
 }
 @Test public void archiveRestoreRequiresExplicitChoiceAndCancelLeavesPreferencesAndArchiveUntouched()throws Exception{
  Map<String,?> before=prefs.getAll();app.deleteDatabase("calls_archive.db");
  SettingsBackup.Snapshot source=new SettingsBackup.Snapshot(Collections.singletonMap("dark",false),new CallArchive.Backup(500,Collections.singletonList(
    new CallArchive.BackupRow("1|100|2|synthetic",1,"synthetic",100,2,2,200,201,8))));
  Uri uri=Uri.parse("content://synthetic.backup/settings.json");shadowOf(app.getContentResolver()).registerInputStream(uri,new ByteArrayInputStream(SettingsBackup.encode(source)));
  try(ActivityController<SettingsBackupActivity> ctl=Robolectric.buildActivity(SettingsBackupActivity.class,SettingsBackupActivity.intent(app,false)).setup()){
   ctl.get().onActivityResult(731,Activity.RESULT_OK,new Intent().setData(uri));
   long until=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
   while(ShadowAlertDialog.getLatestAlertDialog()==null&&System.nanoTime()<until){shadowOf(android.os.Looper.getMainLooper()).idle();Thread.sleep(10);}
   android.app.AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();assertNotNull(dialog);
   CheckBox archive=find(dialog.getWindow().getDecorView(),CheckBox.class);assertNotNull(archive);assertFalse(archive.isChecked());
   assertWineAndRoundedFont(archive,0xFFC76589);
   assertEquals(500,dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).getTypeface().getWeight());
   archive.setChecked(true);dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick();shadowOf(android.os.Looper.getMainLooper()).idle();
   assertTrue(ctl.get().isFinishing());assertEquals(before,prefs.getAll());assertFalse(app.getDatabasePath("calls_archive.db").exists());
  }
 }
 @Test public void confirmedArchiveChoiceMergesThroughActivityWhileArchiveRemainsOff()throws Exception{
  app.deleteDatabase("calls_archive.db");CallArchive.BackupRow saved=new CallArchive.BackupRow("2|100|2|synthetic-existing",2,"synthetic-existing",100,4,2,200,201,8);
  try(CallArchive helper=new CallArchive(app)){
   helper.getWritableDatabase().execSQL("INSERT INTO saved VALUES(?,?,?,?,?,?,?,?,?)",new Object[]{saved.key,saved.callId,saved.number,saved.atMs,saved.seconds,saved.kind,saved.lastSeenMs,saved.missingSinceMs,saved.seenGeneration});
  }
  CallArchive.BackupRow added=new CallArchive.BackupRow("1|100|2|synthetic-import",1,"synthetic-import",100,3,2,200,201,8);
  SettingsBackup.Snapshot source=new SettingsBackup.Snapshot(Collections.singletonMap("dark",false),new CallArchive.Backup(500,Collections.singletonList(added)));
  Uri uri=Uri.parse("content://synthetic.backup/confirmed.json");shadowOf(app.getContentResolver()).registerInputStream(uri,new ByteArrayInputStream(SettingsBackup.encode(source)));
  try(ActivityController<SettingsBackupActivity> ctl=Robolectric.buildActivity(SettingsBackupActivity.class,SettingsBackupActivity.intent(app,false)).setup()){
   ctl.get().onActivityResult(731,Activity.RESULT_OK,new Intent().setData(uri));
   long until=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
   while(ShadowAlertDialog.getLatestAlertDialog()==null&&System.nanoTime()<until){shadowOf(android.os.Looper.getMainLooper()).idle();Thread.sleep(10);}
   android.app.AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();assertNotNull(dialog);
   assertTrue(prefs.getBoolean("dark",false));assertEquals(1,CallArchive.captureBackup(app).rows.size());
   CheckBox archive=find(dialog.getWindow().getDecorView(),CheckBox.class);assertFalse(archive.isChecked());archive.setChecked(true);
   dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
   until=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
   while(shadowOf(ctl.get()).getResultCode()!=Activity.RESULT_OK&&System.nanoTime()<until){shadowOf(android.os.Looper.getMainLooper()).idle();Thread.sleep(10);}
   assertEquals(Activity.RESULT_OK,shadowOf(ctl.get()).getResultCode());assertFalse(prefs.getBoolean("dark",true));assertEquals(CallArchive.OFF,CallArchive.policy(app));
   java.util.List<CallArchive.BackupRow> rows=CallArchive.captureBackup(app).rows;assertEquals(2,rows.size());assertTrue(added.same(rows.get(0)));assertTrue(saved.same(rows.get(1)));
  }finally{app.deleteDatabase("calls_archive.db");}
 }
 private static void clickExport(SettingsBackupActivity activity){
  Button save=exportButton(activity.getWindow().getDecorView());assertNotNull(save);save.performClick();
 }
 private static void assertWineAndRoundedFont(CheckBox checkBox,int wine){
  android.util.TypedValue activated=new android.util.TypedValue();assertTrue(checkBox.getContext().getTheme().resolveAttribute(android.R.attr.colorControlActivated,activated,true));
  assertEquals(wine,activated.data);assertEquals(500,checkBox.getTypeface().getWeight());
  assertTrue(checkBox.getMinHeight()>=Math.round(48*checkBox.getResources().getDisplayMetrics().density));
 }
 private static Button exportButton(View view){
  if(view instanceof Button&&!(view instanceof android.widget.CompoundButton)&&view.getVisibility()==View.VISIBLE)return (Button)view;
  if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){Button match=exportButton(((ViewGroup)view).getChildAt(i));if(match!=null)return match;}
  return null;
 }
 private static <T extends View>T find(View view,Class<T> kind){
  if(kind.isInstance(view)&&view.getVisibility()==View.VISIBLE)return kind.cast(view);
  if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){T match=find(((ViewGroup)view).getChildAt(i),kind);if(match!=null)return match;}
  return null;
 }
 @Test public void importUsesAndroidOpenDocumentAndCancellationPreservesEveryPreference(){
  Map<String,?> before=prefs.getAll();
  try(ActivityController<SettingsBackupActivity> ctl=Robolectric.buildActivity(SettingsBackupActivity.class,SettingsBackupActivity.intent(app,false)).setup()){
   Intent picker=shadowOf(ctl.get()).getNextStartedActivity();assertEquals(Intent.ACTION_OPEN_DOCUMENT,picker.getAction());
   assertTrue(picker.hasCategory(Intent.CATEGORY_OPENABLE));assertEquals("application/json",picker.getType());
   ctl.get().onActivityResult(731,Activity.RESULT_CANCELED,null);assertTrue(ctl.get().isFinishing());
   assertEquals(before,prefs.getAll());assertEquals(Activity.RESULT_CANCELED,shadowOf(ctl.get()).getResultCode());
  }
 }
 @Test public void unsupportedUriAndMissingPickerResultCannotRestore(){
  Map<String,?> before=prefs.getAll();
  try(ActivityController<SettingsBackupActivity> ctl=Robolectric.buildActivity(SettingsBackupActivity.class,SettingsBackupActivity.intent(app,false)).setup()){
   ctl.get().onActivityResult(731,Activity.RESULT_OK,new Intent().setData(Uri.parse("https://example.com/settings.json")));
   assertEquals(before,prefs.getAll());assertEquals(Activity.RESULT_CANCELED,shadowOf(ctl.get()).getResultCode());
   ctl.get().onActivityResult(731,Activity.RESULT_OK,new Intent());assertTrue(ctl.get().isFinishing());assertEquals(before,prefs.getAll());
  }
 }
}
