package com.dacaller.app;

import static org.junit.Assert.*;
import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=33)
public class CallLauncherTest {
 @Test public void hiddenAndMissingNumbersNeverLaunchCalls(){
  Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
  for(String number:new String[]{"", " ", "מספר חסוי", "Private number", "-1", "tel:123", "++"}){
   assertFalse(number,CallLauncher.isCallable(number));
   assertFalse(CallLauncher.place(activity,number));
  }
  assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
 }
 @Test public void formattedInternationalAndServiceNumbersAreSupported(){
  assertTrue(CallLauncher.isCallable("+972 (50) 123-4567"));
  assertTrue(CallLauncher.isCallable("*123#"));
  assertTrue(CallLauncher.isCallable("123,456;7"));
 }
 @Test public void grantedCallUsesExactDisplayedDestination(){
  Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
  Shadows.shadowOf(activity.getApplication()).grantPermissions(Manifest.permission.CALL_PHONE);
  assertTrue(CallLauncher.place(activity,"+972501234567"));
  Intent call=Shadows.shadowOf(activity).getNextStartedActivity();
  assertEquals(Intent.ACTION_CALL,call.getAction());
  assertEquals("+972501234567",call.getData().getSchemeSpecificPart());
 }
 @Test public void deniedPermissionSelectsExternalDialerAndNeverRecurses(){
  Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
  Shadows.shadowOf(activity.getApplication()).denyPermissions(Manifest.permission.CALL_PHONE);
  Intent dial=new Intent(Intent.ACTION_DIAL,Uri.fromParts("tel","12345",null));
  ResolveInfo own=handler(activity.getPackageName(),MainActivity.class.getName());
  ResolveInfo other=handler("com.example.systemphone","com.example.systemphone.Dial");
  Shadows.shadowOf(activity.getPackageManager()).addResolveInfoForIntent(dial,own);
  Shadows.shadowOf(activity.getPackageManager()).addResolveInfoForIntent(dial,other);
  assertTrue(CallLauncher.place(activity,"12345"));
  Intent started=Shadows.shadowOf(activity).getNextStartedActivity();
  assertEquals(Intent.ACTION_DIAL,started.getAction());
  assertEquals("com.example.systemphone",started.getComponent().getPackageName());
 }
 private ResolveInfo handler(String pkg,String name){
  ResolveInfo info=new ResolveInfo();info.activityInfo=new ActivityInfo();
  info.activityInfo.packageName=pkg;info.activityInfo.name=name;info.activityInfo.exported=true;return info;
 }
}
