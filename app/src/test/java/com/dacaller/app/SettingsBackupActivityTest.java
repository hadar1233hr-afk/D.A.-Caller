package com.dacaller.app;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class) @Config(sdk=33) @LooperMode(LooperMode.Mode.PAUSED)
public final class SettingsBackupActivityTest {
 private Context app;private SharedPreferences prefs;
 @Before public void setup(){
  app=RuntimeEnvironment.getApplication();prefs=app.getSharedPreferences("da_caller",Context.MODE_PRIVATE);
  prefs.edit().clear().putBoolean("dark",true).putString("note_111","private note").commit();
 }
 @Test public void exportUsesAndroidCreateDocumentWithoutBroadStoragePermission(){
  try(ActivityController<SettingsBackupActivity> ctl=Robolectric.buildActivity(SettingsBackupActivity.class,SettingsBackupActivity.intent(app,true)).setup()){
   Intent picker=shadowOf(ctl.get()).getNextStartedActivity();
   assertEquals(Intent.ACTION_CREATE_DOCUMENT,picker.getAction());assertEquals("application/json",picker.getType());
   assertTrue(picker.hasCategory(Intent.CATEGORY_OPENABLE));assertTrue(picker.getStringExtra(Intent.EXTRA_TITLE).endsWith(".json"));
   assertNull(picker.getData());
  }
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
