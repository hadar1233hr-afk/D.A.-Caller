package com.dacaller.app;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.SeekBar;
import org.robolectric.shadows.ShadowAlertDialog;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.telecom.Call;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowSettings;

/** A saved preference is an opt-in request, never an Android permission grant. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=33)
@LooperMode(LooperMode.Mode.PAUSED)
public final class CallIslandSettingsActivityTest {
 private SharedPreferences prefs;
 private RoleManager roles;
 private ActivityController<CallIslandSettingsActivity> controller;
 private CallIslandSettingsActivity activity;

 @Before public void setup(){
  Application app=RuntimeEnvironment.getApplication();
  prefs=app.getSharedPreferences("da_caller",Context.MODE_PRIVATE);
  prefs.edit().clear().putString("language","en").putBoolean("haptics",false).commit();
  roles=app.getSystemService(RoleManager.class);
  shadowOf(roles).addAvailableRole(RoleManager.ROLE_DIALER);
  if(roles.isRoleHeld(RoleManager.ROLE_DIALER))shadowOf(roles).removeHeldRole(RoleManager.ROLE_DIALER);
  ShadowSettings.setCanDrawOverlays(false);
  PhoneSessionService.instance=null;PhoneSessionService.current=null;
 }
 @After public void cleanup(){
  if(controller!=null)controller.pause().stop().destroy();
  ShadowSettings.setCanDrawOverlays(false);
  if(roles.isRoleHeld(RoleManager.ROLE_DIALER))shadowOf(roles).removeHeldRole(RoleManager.ROLE_DIALER);
  shadowOf(roles).removeAvailableRole(RoleManager.ROLE_DIALER);
  PhoneSessionService.instance=null;PhoneSessionService.current=null;
 }
 private void open(){
  controller=Robolectric.buildActivity(CallIslandSettingsActivity.class).setup();activity=controller.get();
 }
 private View required(String tag){
  View view=activity.findViewById(android.R.id.content).findViewWithTag(tag);assertNotNull(tag,view);return view;
 }
 private Switch outside(){return (Switch)required("island_outside_app");}
 private String status(){return ((TextView)required("island_overlay_permission")).getText().toString();}
 private void assertNoPermissionRequest(){
  assertNull("Opening settings must not launch another screen",shadowOf(activity).getNextStartedActivity());
  assertNull("Special access must not use a runtime-permission prompt",shadowOf(activity).getLastRequestedPermission());
 }

 @Test public void openingSettingsLeavesExternalIslandOffAndRequestsNoAndroidAccess(){
  open();assertFalse(outside().isChecked());assertFalse(prefs.getBoolean("island_outside_app",false));
  assertFalse(Settings.canDrawOverlays(activity));assertTrue(status().contains("needed"));
  assertTrue(((TextView)required("island_phone_role")).getText().toString().contains("needed"));
  assertNoPermissionRequest();
 }

 @Test public void restoredEnabledPreferenceDoesNotGrantAccessOrAutomaticallyOpenPermissionScreen()throws Exception{
  prefs.edit().putBoolean("island_outside_app",true).commit();
  SettingsBackup.Snapshot backup=SettingsBackup.decode(SettingsBackup.encode(SettingsBackup.capture(prefs)));
  prefs.edit().putBoolean("island_outside_app",false).commit();
  assertTrue(SettingsBackup.restore(prefs,backup,id->true));
  open();assertTrue(outside().isChecked());assertFalse(Settings.canDrawOverlays(activity));
  assertTrue(status().contains("needed"));assertNoPermissionRequest();
 }

 @Test public void explicitEnableRequestsOnlyOwnOverlaySettingsAndDisableDoesNotPromptAgain(){
  open();assertNoPermissionRequest();outside().performClick();
  assertTrue(prefs.getBoolean("island_outside_app",false));
  Intent request=shadowOf(activity).getNextStartedActivity();assertNotNull(request);
  assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,request.getAction());
  assertEquals("package:com.dacaller.app",request.getDataString());
  assertFalse("The switch cannot grant Android access",Settings.canDrawOverlays(activity));
  assertTrue(status().contains("needed"));assertNoPermissionRequest();
  outside().performClick();assertFalse(prefs.getBoolean("island_outside_app",true));assertNoPermissionRequest();
 }

 @Test public void existingGrantAvoidsPromptAndStatusTracksRevocationWithoutChangingOptIn(){
  ShadowSettings.setCanDrawOverlays(true);open();assertTrue(status().contains("allowed"));
  outside().performClick();assertTrue(prefs.getBoolean("island_outside_app",false));assertNoPermissionRequest();
  controller.pause();ShadowSettings.setCanDrawOverlays(false);controller.resume();
  assertTrue(status().contains("needed"));assertTrue(outside().isChecked());
  assertTrue("Revoking access must preserve the user's choice",prefs.getBoolean("island_outside_app",false));assertNoPermissionRequest();
  controller.pause();ShadowSettings.setCanDrawOverlays(true);controller.resume();
  assertTrue(status().contains("allowed"));assertTrue(outside().isChecked());assertNoPermissionRequest();
  required("island_overlay_permission").performClick();
  assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,shadowOf(activity).getNextStartedActivity().getAction());
 }

 @Test public void defaultPhoneRoleIsRequestedOnlyOnExplicitTapAndHeldRoleHasNoPrompt(){
  open();assertNoPermissionRequest();required("island_phone_role").performClick();
  Intent request=shadowOf(activity).getNextStartedActivity();assertNotNull(request);
  Intent expected=roles.createRequestRoleIntent(RoleManager.ROLE_DIALER);
  assertTrue("The platform role dialog must target the dialer role",expected.filterEquals(request));
  assertNotNull(expected.getExtras());assertNotNull(request.getExtras());
  for(String key:expected.getExtras().keySet())assertEquals(key,expected.getExtras().get(key),request.getExtras().get(key));
  controller.pause();shadowOf(roles).addHeldRole(RoleManager.ROLE_DIALER);controller.resume();
  assertTrue(((TextView)required("island_phone_role")).getText().toString().contains("is the default"));
  required("island_phone_role").performClick();assertNoPermissionRequest();
 }

 @Test public void previewControlsStayLocalEvenWhenAnActualCallReferenceExists(){
  Call live=mock(Call.class);PhoneSessionService.current=live;
  prefs.edit().putBoolean("island_outside_app",true).commit();ShadowSettings.setCanDrawOverlays(true);open();
  assertEquals("Demo only · no call is placed",((TextView)required("island-preview-demo-label")).getText().toString());
  required("island_preview_expand").performClick();
  required("island-action-MUTE_ON").performClick();required("island-action-MUTE_OFF").performClick();
  required("island-action-SPEAKER_ON").performClick();required("island-action-SPEAKER_OFF").performClick();
  required("island-action-HOLD").performClick();required("island-action-RESUME").performClick();
  required("island-open").performClick();assertNoPermissionRequest();
  required("island-action-END").performClick();assertSame(live,PhoneSessionService.current);
  assertEquals(View.GONE,required("island-settings-preview").getVisibility());
  verifyNoInteractions(live);assertNoPermissionRequest();
 }
 private void choose(String tag,int index){
  required(tag).performClick();AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();assertNotNull(dialog);
  dialog.getListView().performItemClick(dialog.getListView().getChildAt(index),index,index);
 }
 @Test public void topAreaChoiceIsExplicitExplainsFallbackAndResetPreservesOtherPreferences(){
  prefs.edit().putBoolean(CallIslandPosition.SET,true).putInt(CallIslandPosition.X,8000).putInt(CallIslandPosition.Y,6000).putBoolean("island_show_timer",false).commit();
  open();assertEquals("below",prefs.getString(CallIslandPosition.MODE,"below"));
  assertTrue(((TextView)required("island_position_compatibility")).getText().toString().contains("protected status bar"));
  choose("island_position_mode",1);assertEquals("status_area",prefs.getString(CallIslandPosition.MODE,"below"));assertFalse(prefs.contains(CallIslandPosition.SET));
  assertFalse(prefs.getBoolean("island_show_timer",true));assertNoPermissionRequest();
  prefs.edit().putBoolean(CallIslandPosition.SET,true).putInt(CallIslandPosition.X,9999).commit();required("island_position_reset").performClick();
  assertFalse(prefs.contains(CallIslandPosition.SET));assertFalse(prefs.contains(CallIslandPosition.X));assertEquals("status_area",prefs.getString(CallIslandPosition.MODE,"below"));assertNoPermissionRequest();
 }
 @Test public void hiddenCallHasAReachableRestoreControlWithoutPhoneOrPermissionCommands(){
  prefs.edit().putString("island_hidden_token","current-call").putBoolean("island_enabled",true).commit();open();
  required("island_restore").performClick();assertFalse(prefs.contains("island_hidden_token"));assertTrue(prefs.getBoolean("island_enabled",false));assertNoPermissionRequest();
 }
 @Test public void seeThroughIsAnAdditionalStyleWithAccessibleBackgroundOnlyOpacity(){
  open();SeekBar slider=(SeekBar)required("island_background_opacity");assertFalse(slider.isEnabled());assertEquals(35,slider.getProgress());
  choose("island_style",3);assertEquals("transparent",prefs.getString("island_style","apple"));assertTrue(slider.isEnabled());
  Bundle value=new Bundle();value.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,0);
  assertTrue(slider.performAccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),value));
  assertEquals(0,prefs.getInt("island_background_opacity",-1));assertEquals(1f,required("island-settings-preview").getAlpha(),0f);
  value.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,100);assertTrue(slider.performAccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),value));assertEquals(100,prefs.getInt("island_background_opacity",-1));
  choose("island_style",0);assertEquals("apple",prefs.getString("island_style",""));assertFalse(slider.isEnabled());assertEquals(100,prefs.getInt("island_background_opacity",-1));assertNoPermissionRequest();
 }

 @Test public void pendingRestoreKeepsPersistedIslandChoicesAndDragPositionWhileTransientRestoreWorks()throws Exception{
  prefs.edit().putString("island_style","transparent").putBoolean(CallIslandPosition.SET,true).putInt(CallIslandPosition.X,8000)
    .putString("island_hidden_token","current-call").commit();open();
  java.io.File journal=new java.io.File(activity.getFilesDir(),"settings_archive_restore.pending");
  java.nio.file.Files.write(journal.toPath(),"synthetic pending restore".getBytes(java.nio.charset.StandardCharsets.UTF_8));
  try{
   outside().performClick();assertFalse(outside().isChecked());assertFalse(prefs.getBoolean("island_outside_app",false));
   Switch enabled=(Switch)required("island_enabled");enabled.performClick();assertTrue(enabled.isChecked());assertTrue(prefs.getBoolean("island_enabled",true));
   choose("island_style",0);assertEquals("transparent",prefs.getString("island_style",""));
   choose("island_position_mode",1);assertEquals("below",prefs.getString(CallIslandPosition.MODE,"below"));
   choose("island_size",1);assertEquals("compact",prefs.getString("island_size","compact"));
   required("island_position_reset").performClick();assertTrue(prefs.getBoolean(CallIslandPosition.SET,false));
   CallIslandPosition.save(activity,prefs,new android.graphics.Rect(12,8,308,780),180,56,12,8);assertEquals(8000,prefs.getInt(CallIslandPosition.X,-1));
   SeekBar slider=(SeekBar)required("island_background_opacity");Bundle value=new Bundle();value.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,100);
   assertTrue(slider.performAccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),value));assertEquals(35,slider.getProgress());assertEquals(35,prefs.getInt("island_background_opacity",35));
   required("island_restore").performClick();assertFalse(prefs.contains("island_hidden_token"));assertNoPermissionRequest();
  }finally{new android.util.AtomicFile(journal).delete();}
 }

}
