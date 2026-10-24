package com.dacaller.app;

import static org.junit.Assert.*;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
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
public class MainActivityRegressionTest {
 @Before public void setup(){
  Context ctx=RuntimeEnvironment.getApplication();
  ctx.getSharedPreferences("da_caller",Context.MODE_PRIVATE).edit().clear().putBoolean("role_prompted",true).apply();
  Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.READ_CONTACTS,Manifest.permission.READ_CALL_LOG,Manifest.permission.CALL_PHONE);
 }
 @Test public void rerenderCannotConsumePendingPermissionDestination(){
  try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
   MainActivity activity=controller.get();activity.dialFromList("0501234567");
   activity.refreshPage(); // A provider callback or navigation used to turn this into a keypad.
   Shadows.shadowOf(activity.getApplication()).grantPermissions(Manifest.permission.CALL_PHONE);
   activity.onRequestPermissionsResult(23,new String[]{Manifest.permission.CALL_PHONE},new int[]{PackageManager.PERMISSION_GRANTED});
   Intent call=Shadows.shadowOf(activity).getNextStartedActivity();
   assertNotNull(call);assertEquals(Intent.ACTION_CALL,call.getAction());
   assertEquals("0501234567",call.getData().getSchemeSpecificPart());
  }
 }
 @Test public void repeatedTapWhilePermissionPendingKeepsFirstDestination(){
  try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
   MainActivity activity=controller.get();activity.dialFromList("12345");activity.dialFromList("67890");
   Shadows.shadowOf(activity.getApplication()).grantPermissions(Manifest.permission.CALL_PHONE);
   activity.onRequestPermissionsResult(23,new String[]{Manifest.permission.CALL_PHONE},new int[]{PackageManager.PERMISSION_GRANTED});
   assertEquals("12345",Shadows.shadowOf(activity).getNextStartedActivity().getData().getSchemeSpecificPart());
   Intent extra;int permissionRequests=0;
   while((extra=Shadows.shadowOf(activity).getNextStartedActivity())!=null){
    assertNotEquals(Intent.ACTION_CALL,extra.getAction());
    if("android.content.pm.action.REQUEST_PERMISSIONS".equals(extra.getAction()))permissionRequests++;
   }
   assertEquals(1,permissionRequests);
  }
 }
 @Test public void tabAndPendingPermissionSurviveActivityRecreation(){
  ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup();
  MainActivity activity=controller.get();activity.openContactsTab();activity.dialFromList("12345");
  Bundle state=new Bundle();activity.onSaveInstanceState(state);controller.pause().stop().destroy();
  assertEquals("contacts",state.getString("page"));
  try(ActivityController<MainActivity> restored=Robolectric.buildActivity(MainActivity.class).create(state).start().resume()){
   MainActivity next=restored.get();Shadows.shadowOf(next.getApplication()).grantPermissions(Manifest.permission.CALL_PHONE);
   next.onRequestPermissionsResult(23,new String[]{Manifest.permission.CALL_PHONE},new int[]{PackageManager.PERMISSION_GRANTED});
   assertEquals("12345",Shadows.shadowOf(next).getNextStartedActivity().getData().getSchemeSpecificPart());
  }
 }
 @Test public void providerRefreshDuringFingerDownDefersRebuildAndStillCallsCorrectNumber()throws Exception{
  try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).create().start().visible()){
   MainActivity activity=controller.get();
   Shadows.shadowOf(activity.getApplication()).grantPermissions(Manifest.permission.READ_CONTACTS,Manifest.permission.CALL_PHONE);
   java.lang.reflect.Field field=MainActivity.class.getDeclaredField("contacts");field.setAccessible(true);
   field.set(activity,java.util.Collections.singletonList(new Contacts.Person("Fixture","12345",99)));
   activity.openContactsTab();
   View decor=activity.getWindow().getDecorView();
   decor.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(569,View.MeasureSpec.EXACTLY));decor.layout(0,0,320,569);
   android.widget.ListView list=(android.widget.ListView)findType(decor,android.widget.ListView.class);
   assertNotNull(list);assertTrue(list.getChildCount()>0);
   View control=findDescription(list,I18n.text(activity,"התקשר")+" Fixture 12345");assertNotNull(control);
   android.graphics.Rect rect=new android.graphics.Rect();control.getDrawingRect(rect);list.offsetDescendantRectToMyCoords(control,rect);
   long at=android.os.SystemClock.uptimeMillis();
   android.view.MotionEvent down=android.view.MotionEvent.obtain(at,at,0,rect.centerX(),rect.centerY(),0);
   list.dispatchTouchEvent(down);down.recycle();
   activity.refreshPage();assertSame(list,findType(decor,android.widget.ListView.class));
   android.view.MotionEvent up=android.view.MotionEvent.obtain(at,at+30,1,rect.centerX(),rect.centerY(),0);
   list.dispatchTouchEvent(up);up.recycle();
   Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50));
   Intent call=Shadows.shadowOf(activity).getNextStartedActivity();assertNotNull(call);
   assertEquals(Intent.ACTION_CALL,call.getAction());assertEquals("12345",call.getData().getSchemeSpecificPart());
   Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100));
   assertNotSame(list,findType(decor,android.widget.ListView.class));
  }
 }
 private View findType(View view,Class<?> type){
  if(type.isInstance(view))return view;
  if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=findType(((ViewGroup)view).getChildAt(i),type);if(found!=null)return found;}
  return null;
 }
 private View findDescription(View view,String description){
  if(description.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;
  if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=findDescription(((ViewGroup)view).getChildAt(i),description);if(found!=null)return found;}
  return null;
 }
 @Test public void secondDialIntentUpdatesAlreadyVisibleKeypad(){
  try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
   MainActivity activity=controller.get();activity.openDialPad("12345");
   activity.onNewIntent(new Intent(Intent.ACTION_DIAL,android.net.Uri.parse("tel:67890")));
   Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
   Bundle state=new Bundle();activity.onSaveInstanceState(state);
   assertEquals("67890",state.getString("dialpad"));
  }
 }
 @Test public void rapidRepeatedGrantedTapsPlaceOnlyOneCall(){
  try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
   MainActivity activity=controller.get();
   Shadows.shadowOf(activity.getApplication()).grantPermissions(Manifest.permission.CALL_PHONE);
   activity.dialFromList("12345");activity.dialFromList("12345");
   Intent call=Shadows.shadowOf(activity).getNextStartedActivity();assertEquals(Intent.ACTION_CALL,call.getAction());
   assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
  }
 }
 @Test public void englishNavigationIsLeftToRightAndSettingsUsesActualVersion(){
  RuntimeEnvironment.getApplication().getSharedPreferences("da_caller",Context.MODE_PRIVATE).edit().putString("language","en").putString("start_page","settings").apply();
  try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
   MainActivity activity=controller.get();
   View root=((ViewGroup)activity.findViewById(android.R.id.content)).getChildAt(0);
   assertEquals(View.LAYOUT_DIRECTION_LTR,root.getLayoutDirection());
   assertTrue(hasText(root,"DA CALLER · v"+BuildConfig.VERSION_NAME));
  }
 }
 private boolean hasText(View view,String expected){
  if(view instanceof android.widget.TextView&&expected.contentEquals(((android.widget.TextView)view).getText()))return true;
  if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)if(hasText(((ViewGroup)view).getChildAt(i),expected))return true;
  return false;
 }
}
