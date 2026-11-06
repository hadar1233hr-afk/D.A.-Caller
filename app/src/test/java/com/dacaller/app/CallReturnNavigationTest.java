package com.dacaller.app;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Looper;
import android.telecom.Call;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import org.junit.After;
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

/** Back must preserve the app destination and never issue a Telecom command or repeat a dial intent. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=33) @LooperMode(LooperMode.Mode.PAUSED)
public class CallReturnNavigationTest {
 private Context app;
 private SharedPreferences runtime;
 @Before public void prepare(){
  app=RuntimeEnvironment.getApplication();runtime=app.getSharedPreferences(CallReturnNavigation.PREFERENCES,Context.MODE_PRIVATE);runtime.edit().clear().commit();
  app.getSharedPreferences("da_caller",Context.MODE_PRIVATE).edit().clear().putBoolean("role_prompted",true).putBoolean("haptics",false).commit();
  Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.READ_CONTACTS,Manifest.permission.READ_CALL_LOG,Manifest.permission.CALL_PHONE);
  PhoneSessionService.current=null;PhoneSessionService.instance=null;
 }
 @After public void cleanup(){PhoneSessionService.current=null;PhoneSessionService.instance=null;}
 @Test public void backFinishesOnlyCallScreenInRingingOutgoingAndActiveIncludingEveryOpenPanel(){
  for(int state:new int[]{Call.STATE_RINGING,Call.STATE_DIALING,Call.STATE_CONNECTING,Call.STATE_ACTIVE,Call.STATE_HOLDING}){
   Call call=mock(Call.class);when(call.getState()).thenReturn(state);PhoneSessionService.current=call;
   for(String panel:state==Call.STATE_ACTIVE?new String[]{"normal","audio","more","keypad"}:new String[]{"normal"}){
    try(ActivityController<CallScreenActivity> controller=Robolectric.buildActivity(CallScreenActivity.class).setup()){
     CallScreenActivity activity=controller.get();Shadows.shadowOf(activity).setIsTaskRoot(false);
     if(!"normal".equals(panel))activity.findViewById(android.R.id.content).findViewWithTag("call-action-"+panel).performClick();
     activity.onBackPressed();
     assertTrue("Back must leave call screen in "+state+"/"+panel,activity.isFinishing());
     assertNull("Do not duplicate or clear the existing app stack",Shadows.shadowOf(activity).getNextStartedActivity());
     assertSame(call,PhoneSessionService.current);
    }
   }
   verify(call,never()).disconnect();verify(call,never()).answer(anyInt());verify(call,never()).reject(anyBoolean(),any());
   verify(call,never()).hold();verify(call,never()).unhold();
  }
 }
 @Test @Config(sdk=26) public void legacyBackColdStartOpensExplicitAppReturnWithoutAnyPhoneExtras(){
  Call call=mock(Call.class);when(call.getState()).thenReturn(Call.STATE_ACTIVE);PhoneSessionService.current=call;
  try(ActivityController<CallScreenActivity> controller=Robolectric.buildActivity(CallScreenActivity.class).setup()){
   CallScreenActivity activity=controller.get();Shadows.shadowOf(activity).setIsTaskRoot(true);activity.onBackPressed();
   Intent launched=Shadows.shadowOf(activity).getNextStartedActivity();assertNotNull(launched);
   assertEquals(MainActivity.class.getName(),launched.getComponent().getClassName());assertEquals(CallReturnNavigation.ACTION_RETURN,launched.getAction());
   assertNull(launched.getData());assertNull(launched.getExtras());
   assertTrue((launched.getFlags()&Intent.FLAG_ACTIVITY_CLEAR_TOP)!=0);assertTrue(activity.isFinishing());
   verify(call,never()).disconnect();
  }
 }
 @Test public void separateExistingAppTaskIsBroughtBackWithoutClearingItsDetailActivity(){
  Activity screen=mock(Activity.class);when(screen.isTaskRoot()).thenReturn(true);when(screen.getPackageName()).thenReturn(app.getPackageName());when(screen.getTaskId()).thenReturn(12);
  ActivityManager manager=mock(ActivityManager.class);when(screen.getSystemService(ActivityManager.class)).thenReturn(manager);
  ActivityManager.AppTask current=mock(ActivityManager.AppTask.class),other=mock(ActivityManager.AppTask.class);
  ActivityManager.RecentTaskInfo self=new ActivityManager.RecentTaskInfo();self.id=12;self.topActivity=new ComponentName(app,CallScreenActivity.class);
  ActivityManager.RecentTaskInfo details=new ActivityManager.RecentTaskInfo();details.id=3;details.topActivity=new ComponentName(app,ContactDetailActivity.class);
  when(current.getTaskInfo()).thenReturn(self);when(other.getTaskInfo()).thenReturn(details);when(manager.getAppTasks()).thenReturn(Arrays.asList(current,other));
  CallReturnNavigation.returnToApp(screen);verify(other).moveToFront();verify(current,never()).moveToFront();verify(screen).finish();verify(screen,never()).startActivity(any());
 }
 @Test public void runtimeSnapshotContainsOnlyTypedNavigationAndNeverCallDestinations(){
  Bundle state=new Bundle();state.putString("page","contacts");state.putString("contact_query","דנה");state.putLong("contact_id",47);
  state.putInt("contact_position",46);state.putInt("contact_offset",-11);state.putInt("favorite_scroll",180);state.putInt("stats_period",2);
  state.putString("requested_call","0501234567");state.putString("pending_role_call","0507654321");state.putString("dialpad","0509999999");
  CallReturnNavigation.saveMain(app,state);Bundle restored=CallReturnNavigation.mainState(app);
  assertEquals("contacts",restored.getString("page"));assertEquals("דנה",restored.getString("contact_query"));assertEquals(47,restored.getLong("contact_id"));
  assertEquals(-11,restored.getInt("contact_offset"));assertEquals(180,restored.getInt("favorite_scroll"));assertEquals(2,restored.getInt("stats_period"));
  for(String key:new String[]{"requested_call","pending_role_call","dialpad"}){assertFalse(restored.containsKey(key));assertFalse(runtime.contains(key));}
  assertFalse(app.getSharedPreferences("da_caller",Context.MODE_PRIVATE).contains("contact_query"));
 }
 @Test public void coldMainReturnRestoresLastTabSearchAndAnchorsAndSuppressesRolePrompt(){
  Bundle state=new Bundle();state.putString("page","contacts");state.putString("contact_query","Contact 47");state.putInt("contact_position",46);state.putLong("contact_id",47);state.putInt("contact_offset",-9);CallReturnNavigation.saveMain(app,state);
  app.getSharedPreferences("da_caller",Context.MODE_PRIVATE).edit().remove("role_prompted").putString("start_page","recents").commit();
  Intent intent=CallReturnNavigation.intent(app).putExtra("place_call","0501234567").putExtra("phone","0507654321");
  try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class,intent).setup()){
   MainActivity activity=controller.get();Bundle actual=new Bundle();activity.onSaveInstanceState(actual);
   assertEquals("contacts",actual.getString("page"));assertEquals("Contact 47",actual.getString("contact_query"));assertEquals(47,actual.getLong("contact_id"));assertEquals(-9,actual.getInt("contact_offset"));
   assertNull(actual.getString("requested_call"));assertNull(actual.getString("pending_role_call"));assertNull(actual.getString("dialpad"));
   Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
   assertFalse(app.getSharedPreferences("da_caller",Context.MODE_PRIVATE).getBoolean("role_prompted",false));
  }
 }
 @Test public void businessReturnPreservesOnlyTypedSearchAndAnchorsWithoutReplayingDial(){
  Bundle state=new Bundle();state.putString("page","contacts");state.putBoolean("contacts_business_mode",true);
  state.putString("business_query","בית קפה");state.putInt("business_offset",100);state.putInt("business_position",12);
  state.putInt("business_top",-9);state.putLong("business_id",9000000001L);state.putString("place_call","0501112233");
  CallReturnNavigation.saveMain(app,state);Bundle restored=CallReturnNavigation.mainState(app);
  assertTrue(restored.getBoolean("contacts_business_mode"));assertEquals("בית קפה",restored.getString("business_query"));
  assertEquals(100,restored.getInt("business_offset"));assertEquals(12,restored.getInt("business_position"));
  assertEquals(-9,restored.getInt("business_top"));assertEquals(9000000001L,restored.getLong("business_id"));
  assertFalse(restored.containsKey("place_call"));
  runtime.edit().putString("business_offset","bad").putString("contacts_business_mode","true").putInt("business_query",42).commit();
  Bundle corrupt=CallReturnNavigation.mainState(app);assertEquals(0,corrupt.getInt("business_offset"));
  assertFalse(corrupt.getBoolean("contacts_business_mode"));assertEquals("",corrupt.getString("business_query"));
 }
 @Test public void liveMainReturnKeepsItsActualPageAndIgnoresInjectedDialExtras(){
  try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
   MainActivity activity=controller.get();activity.openContactsTab();
   activity.onNewIntent(CallReturnNavigation.intent(app).putExtra("place_call","0501234567").putExtra("show_contacts",false));
   Bundle state=new Bundle();activity.onSaveInstanceState(state);assertEquals("contacts",state.getString("page"));
   Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));assertNull(Shadows.shadowOf(activity).getNextStartedActivity());assertNull(state.getString("requested_call"));
  }
 }
 @Test public void mainStopPersistsNavigationForColdReturnWhileNormalLaunchRetainsConfiguredStartPage(){
  try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
   MainActivity activity=controller.get();activity.openContactsTab();controller.pause().stop();
   assertEquals("contacts",CallReturnNavigation.mainState(app).getString("page"));
  }
  app.getSharedPreferences("da_caller",Context.MODE_PRIVATE).edit().putString("start_page","settings").commit();
  try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
   Bundle state=new Bundle();controller.get().onSaveInstanceState(state);assertEquals("settings",state.getString("page"));
  }
 }
 @Test public void businessColdReturnRestoresSeparateTabAndRecentShortcutNeverDials(){
  Bundle state=new Bundle();state.putString("page","contacts");state.putBoolean("contacts_business_mode",true);
  state.putString("business_query","קפה");state.putInt("business_offset",50);CallReturnNavigation.saveMain(app,state);
  try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class,CallReturnNavigation.intent(app)).setup()){
   MainActivity activity=controller.get();View content=activity.findViewById(android.R.id.content);
   if(BuildConfig.BUSINESS_DIRECTORY_ENABLED)assertNotNull(content.findViewWithTag("business_directory_root"));
   else {assertNull(content.findViewWithTag("business_directory_root"));assertNull(content.findViewWithTag("contacts_directory_tab"));}
   Bundle restored=new Bundle();activity.onSaveInstanceState(restored);
   assertEquals(BuildConfig.BUSINESS_DIRECTORY_ENABLED,restored.getBoolean("contacts_business_mode"));assertEquals("קפה",restored.getString("business_query"));
   assertEquals(50,restored.getInt("business_offset"));
   activity.onNewIntent(new Intent(activity,MainActivity.class).putExtra("show_recents",true));
   Bundle recents=new Bundle();activity.onSaveInstanceState(recents);assertEquals("recents",recents.getString("page"));
   assertNull("Navigation must never enqueue a call",Shadows.shadowOf(activity).getNextStartedActivity());
  }
 }
 @Test public void coldReadOnlyDestinationsAreExplicitAndPermissionScoped(){
  runtime.edit().putString("last_screen","contact").putLong("last_contact_id",99).commit();assertNull(CallReturnNavigation.lastDestination(app));
  Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.READ_CONTACTS);
  Intent contact=CallReturnNavigation.lastDestination(app);assertEquals(ContactDetailActivity.class.getName(),contact.getComponent().getClassName());assertEquals(99,contact.getLongExtra("contact_id",-1));
  runtime.edit().putString("last_screen","archive").putString("archive_query","דנה").commit();assertNull(CallReturnNavigation.lastDestination(app));
  app.getSharedPreferences("da_caller",Context.MODE_PRIVATE).edit().putString(CallArchive.PREF,CallArchive.FOREVER).commit();
  Intent archive=CallReturnNavigation.lastDestination(app);assertEquals(ArchiveActivity.class.getName(),archive.getComponent().getClassName());assertEquals("דנה",archive.getStringExtra("archive_query"));
  runtime.edit().putString("last_screen","com.other.app.Anything").commit();assertNull(CallReturnNavigation.lastDestination(app));
 }
 @Test public void coldContactReturnRebuildsReadOnlyDestinationOnceWhileSavedTaskRecreationDoesNotDuplicateIt(){
  Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.READ_CONTACTS);
  runtime.edit().putString("last_screen","contact").putLong("last_contact_id",99).commit();
  Bundle saved=new Bundle();
  try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class,CallReturnNavigation.intent(app)).setup()){
   MainActivity activity=controller.get();Shadows.shadowOf(Looper.getMainLooper()).idle();
   Intent destination=Shadows.shadowOf(activity).getNextStartedActivity();assertNotNull(destination);
   assertEquals(ContactDetailActivity.class.getName(),destination.getComponent().getClassName());assertEquals(99,destination.getLongExtra("contact_id",-1));
   assertNull(Shadows.shadowOf(activity).getNextStartedActivity());activity.onSaveInstanceState(saved);
  }
  runtime.edit().putString("last_screen","contact").putLong("last_contact_id",99).commit();
  try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class,CallReturnNavigation.intent(app)).create(saved).start().resume()){
   Shadows.shadowOf(Looper.getMainLooper()).idle();
   assertNull("Android already restores its saved Activity stack",Shadows.shadowOf(controller.get()).getNextStartedActivity());
  }
 }
 @Test public void incomingTouchRequiresCompletedSwipeAndCancelledDragCannotAnswer(){
  Call call=mock(Call.class);when(call.getState()).thenReturn(Call.STATE_RINGING);PhoneSessionService.current=call;
  try(ActivityController<CallScreenActivity> controller=Robolectric.buildActivity(CallScreenActivity.class).setup()){
   View prompt=controller.get().findViewById(android.R.id.content).findViewWithTag("incoming-swipe-answer");assertNotNull(prompt);
   float distance=100*controller.get().getResources().getDisplayMetrics().density;
   gestureEvent(prompt,android.view.MotionEvent.ACTION_DOWN,0);gestureEvent(prompt,android.view.MotionEvent.ACTION_UP,0);
   gestureEvent(prompt,android.view.MotionEvent.ACTION_DOWN,0);gestureEvent(prompt,android.view.MotionEvent.ACTION_UP,distance/4);
   gestureEvent(prompt,android.view.MotionEvent.ACTION_DOWN,0);gestureEvent(prompt,android.view.MotionEvent.ACTION_MOVE,distance);
   assertTrue("The swipe must visibly follow the finger",prompt.getTranslationX()>0);
   gestureEvent(prompt,android.view.MotionEvent.ACTION_CANCEL,distance);gestureEvent(prompt,android.view.MotionEvent.ACTION_UP,distance);
   verify(call,never()).answer(anyInt());assertEquals(0f,prompt.getTranslationX(),0f);
   gestureEvent(prompt,android.view.MotionEvent.ACTION_DOWN,0);gestureEvent(prompt,android.view.MotionEvent.ACTION_MOVE,distance);
   gestureEvent(prompt,android.view.MotionEvent.ACTION_UP,distance);verify(call,times(1)).answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY);
  }
 }
 @Test public void incomingAccessibilityCanAnswerButOldCallSwipeCannotAffectItsReplacement(){
  Call call=mock(Call.class);when(call.getState()).thenReturn(Call.STATE_RINGING);PhoneSessionService.current=call;
  try(ActivityController<CallScreenActivity> controller=Robolectric.buildActivity(CallScreenActivity.class).setup()){
   View prompt=controller.get().findViewById(android.R.id.content).findViewWithTag("incoming-swipe-answer");assertNotNull(prompt);
   assertTrue(prompt.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK,null));
   verify(call).answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY);
   Call replacement=mock(Call.class);when(replacement.getState()).thenReturn(Call.STATE_RINGING);PhoneSessionService.current=replacement;
   gestureEvent(prompt,android.view.MotionEvent.ACTION_DOWN,0);gestureEvent(prompt,android.view.MotionEvent.ACTION_UP,500);
   verify(call,times(1)).answer(anyInt());verify(replacement,never()).answer(anyInt());
  }
 }
 private static void gestureEvent(View target,int action,float x){
  long at=android.os.SystemClock.uptimeMillis();android.view.MotionEvent event=android.view.MotionEvent.obtain(at,at,action,x,0,0);
  try{target.dispatchTouchEvent(event);}finally{event.recycle();}
 }

}
