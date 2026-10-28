package com.dacaller.app;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
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
}
