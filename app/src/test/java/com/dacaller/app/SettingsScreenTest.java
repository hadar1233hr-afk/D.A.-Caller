package com.dacaller.app;

import static org.junit.Assert.*;
import android.Manifest;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class) @Config(sdk=33) @LooperMode(LooperMode.Mode.PAUSED)
public final class SettingsScreenTest {
 private SharedPreferences prefs;
 @Before public void setup(){
  Context app=RuntimeEnvironment.getApplication();
  prefs=app.getSharedPreferences("da_caller",Context.MODE_PRIVATE);
  prefs.edit().clear().putBoolean("role_prompted",true).putBoolean("haptics",false).putString("start_page","settings").apply();
  Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.CALL_PHONE,Manifest.permission.READ_CONTACTS,
   Manifest.permission.READ_CALL_LOG,Manifest.permission.POST_NOTIFICATIONS);
 }
 @Test public void callsHeaderHasNoTelephoneAndLastSectionRepeatedlyOpensAndCloses(){
  try(ActivityController<MainActivity> ctl=Robolectric.buildActivity(MainActivity.class).setup()){
   MainActivity host=ctl.get();View root=host.findViewById(android.R.id.content);
   ViewGroup calls=(ViewGroup)findText(root,"שיחות ומענה").getParent();
   assertEquals("label and disclosure only",2,calls.getChildCount());
   ViewGroup section=(ViewGroup)findText(root,"הרשאות וחיבור למערכת").getParent();
   ViewGroup parent=(ViewGroup)section.getParent();
   View content=parent.getChildAt(parent.indexOfChild(section)+1);
   assertEquals(View.GONE,content.getVisibility());
   for(int i=0;i<20;i++){
    assertTrue(section.performClick());assertEquals(View.VISIBLE,content.getVisibility());
    assertTrue(prefs.getBoolean("settings_group_open_5",false));
    assertTrue(section.performClick());assertEquals(View.GONE,content.getVisibility());
    assertFalse(prefs.getBoolean("settings_group_open_5",true));
   }
   assertTrue(section.performClick());
   assertNotNull(findText(content,"הרשאת שיחות"));
   assertNotNull(findText(content,"אבחון תקלה בחיוג"));
   assertEquals("מורחב",section.getStateDescription());
  }
 }
 @Test public void returningFromPermissionUiRefreshesStatusWithoutReplacingOrScrollingCard(){
  prefs.edit().putBoolean("settings_group_open_5",true).apply();
  try(ActivityController<MainActivity> ctl=Robolectric.buildActivity(MainActivity.class).setup()){
   MainActivity host=ctl.get();View root=host.findViewById(android.R.id.content);
   View calls=permissionRow(root,"הרשאת שיחות");
   assertNotNull(findText(calls,"נדרש אישור"));
   ScrollView viewport=(ScrollView)((View)calls.getParent().getParent()).getParent();
   viewport.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(500,View.MeasureSpec.EXACTLY));
   viewport.layout(0,0,320,500);viewport.scrollTo(0,200);
   int scroll=viewport.getScrollY();
   Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.CALL_PHONE);
   SettingsScreen.refreshAttached(host);
   assertSame(calls,permissionRow(root,"הרשאת שיחות"));
   assertNotNull(findText(calls,"פעיל"));assertNotNull(findText(calls,"ניהול"));
   assertEquals(scroll,viewport.getScrollY());
   Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.CALL_PHONE);
   SettingsScreen.refreshAttached(host);assertNotNull(findText(calls,"נדרש אישור"));
  }
 }
 @Test public void grantedPermissionAndPermanentlyDeniedPermissionHaveUsefulSettingsAction(){
  prefs.edit().putBoolean("settings_group_open_5",true).apply();
  try(ActivityController<MainActivity> ctl=Robolectric.buildActivity(MainActivity.class).setup()){
   MainActivity host=ctl.get();View root=host.findViewById(android.R.id.content);
   Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.CALL_PHONE);
   SettingsScreen.refreshAttached(host);permissionRow(root,"הרשאת שיחות").performClick();
   Intent granted=Shadows.shadowOf(host).getNextStartedActivity();
   assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,granted.getAction());
   assertEquals("package:com.dacaller.app",granted.getDataString());
   Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.CALL_PHONE);
   prefs.edit().putBoolean("settings_permission_asked_"+Manifest.permission.CALL_PHONE,true).apply();
   SettingsScreen.refreshAttached(host);permissionRow(root,"הרשאת שיחות").performClick();
   assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Shadows.shadowOf(host).getNextStartedActivity().getAction());
  }
 }
 @Test public void defaultRoleAndNotificationPermissionUseActualAndroidState(){
  prefs.edit().putBoolean("settings_group_open_5",true).apply();
  RoleManager roles=RuntimeEnvironment.getApplication().getSystemService(RoleManager.class);
  Shadows.shadowOf(roles).addAvailableRole(RoleManager.ROLE_DIALER);
  try(ActivityController<MainActivity> ctl=Robolectric.buildActivity(MainActivity.class).setup()){
   MainActivity host=ctl.get();View root=host.findViewById(android.R.id.content);
   View dialer=permissionRow(root,"חייגן ברירת מחדל");
   assertNotNull(findText(dialer,"נדרש אישור"));
   Shadows.shadowOf(roles).addHeldRole(RoleManager.ROLE_DIALER);
   Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
   SettingsScreen.refreshAttached(host);
   assertNotNull(findText(dialer,"פעיל"));
   assertNotNull(findText(permissionRow(root,"התראות שיחה"),"פעיל"));
  }finally{Shadows.shadowOf(roles).removeHeldRole(RoleManager.ROLE_DIALER);Shadows.shadowOf(roles).removeAvailableRole(RoleManager.ROLE_DIALER);}
 }
 @Test @Config(qualifiers="w320dp-h569dp-mdpi") public void narrowRtlHeaderAndPermissionCardKeepTouchTargets(){
  RuntimeEnvironment.setFontScale(1.5f);
  prefs.edit().putBoolean("settings_group_open_5",true).apply();
  try(ActivityController<MainActivity> ctl=Robolectric.buildActivity(MainActivity.class).setup()){
   MainActivity host=ctl.get();View root=host.findViewById(android.R.id.content);
   View header=(View)findText(root,"הרשאות וחיבור למערכת").getParent();
   int minimum=Math.round(48*host.getResources().getDisplayMetrics().density);
   assertTrue(header.getMinimumHeight()>=minimum);
   View call=permissionRow(root,"הרשאת שיחות");
   assertTrue(call.getMinimumHeight()>=minimum);assertTrue(call.isFocusable());
   assertEquals(View.LAYOUT_DIRECTION_RTL,call.getLayoutDirection());
  }
 }
 private View permissionRow(View root,String label){return (View)findText(root,label).getParent().getParent();}
 private TextView findText(View view,String text){
  if(view instanceof TextView&&text.contentEquals(((TextView)view).getText()))return (TextView)view;
  if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){
   TextView found=findText(((ViewGroup)view).getChildAt(i),text);if(found!=null)return found;
  }
  return null;
 }
}
