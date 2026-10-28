package com.dacaller.app;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.WindowManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowAlertDialog;

/** The external window is optional and must never outlive permission, privacy or its owned call. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=33) @LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CallIslandOverlayTest {
 private static final class Privacy implements CallIslandOverlay.Privacy {
  boolean permitted=true,locked,inside;
  public boolean permitted(){return permitted;}
  public boolean privateScreen(){return locked;}
  public boolean insideApp(){return inside;}
 }
 private static final class Session implements CallIslandSession {
  CallIslandState state=active("owned-call");
  int snapshots;
  final List<String> actions=new ArrayList<>();
  public CallIslandState current(){snapshots++;return state;}
  public boolean perform(String token,String action){
   if(state==null||!state.token.equals(token))return false;
   actions.add(token+":"+action);if("END".equals(action)||"DECLINE".equals(action))state=null;return true;
  }
  public void open(Activity host,String token){throw new AssertionError("External window must not invent an Activity");}
  public void openOutside(Context host,String token){if(state!=null&&state.token.equals(token))actions.add(token+":OPEN");}
 }
 private SharedPreferences prefs;
 private Session session;
 private Privacy privacy;
 private WindowManager windows;
 private CallIslandOverlay overlay;
 private View added;
 private WindowManager.LayoutParams params;
 @Before public void setup(){
  Context context=new ContextThemeWrapper(RuntimeEnvironment.getApplication(),R.style.AppTheme);
  prefs=context.getSharedPreferences("da_caller",0);prefs.edit().clear().putString("language","en")
    .putBoolean("island_animations",false).putBoolean("island_enabled",true).commit();
  session=new Session();privacy=new Privacy();windows=mock(WindowManager.class);
  doAnswer(invocation->{added=invocation.getArgument(0);params=invocation.getArgument(1);return null;})
    .when(windows).addView(any(View.class),any(WindowManager.LayoutParams.class));
  overlay=new CallIslandOverlay(context,session,windows,privacy);
 }
 @After public void cleanup(){
  overlay.stop();AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();if(dialog!=null&&dialog.isShowing())dialog.dismiss();
  shadowOf(Looper.getMainLooper()).idle();
 }
 private static CallIslandState active(String token){
  return new CallIslandState(token,"Dana Example","0500099971",Call.STATE_ACTIVE,CallAudioState.ROUTE_EARPIECE,
    CallAudioState.ROUTE_EARPIECE|CallAudioState.ROUTE_SPEAKER,false,true,true,System.currentTimeMillis()-11000,-1);
 }
 private static CallIslandState phase(String token,int state){
  CallIslandState active=active(token);
  return new CallIslandState(token,active.name,active.number,state,active.route,active.supportedRoutes,
    active.muted,state==Call.STATE_ACTIVE||state==Call.STATE_HOLDING,state!=Call.STATE_RINGING,active.connectedAtMillis,-1);
 }
 private void idle(){shadowOf(Looper.getMainLooper()).idle();}
 private void enable(){prefs.edit().putBoolean("island_outside_app",true).commit();idle();}
 private void refresh(){overlay.configurationChanged();idle();}
 @Test public void outsideSettingIsOptInAndPermissionAndOwnedCallAreAllRequired(){
  overlay.start();assertFalse(overlay.isShowing());verify(windows,never()).addView(any(),any());
  privacy.permitted=false;enable();assertFalse(overlay.isShowing());
  privacy.permitted=true;session.state=null;refresh();assertFalse(overlay.isShowing());
  session.state=active("owned-call");PhoneSessionService.notifyViews();idle();assertTrue(overlay.isShowing());
  assertTrue(session.actions.isEmpty());
 }
 @Test public void disabledFeatureDoesNotPollTelecomAndPreferenceEnableRestoresCurrentCall(){
  overlay.start();shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));assertEquals(0,session.snapshots);
  enable();assertTrue(overlay.isShowing());assertTrue(session.snapshots>0);
  prefs.edit().putBoolean("island_outside_app",false).commit();idle();assertFalse(overlay.isShowing());
  int stopped=session.snapshots;shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));assertEquals(stopped,session.snapshots);
  enable();assertTrue(overlay.isShowing());assertTrue(session.snapshots>stopped);
  prefs.edit().putBoolean("island_enabled",false).commit();idle();assertFalse(overlay.isShowing());
  stopped=session.snapshots;shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));assertEquals(stopped,session.snapshots);
  assertNotNull(session.state);assertTrue(session.actions.isEmpty());
 }
 @Test public void incomingAndUnknownCallStatesNeverCreateExternalUiOrDispatchCallControls(){
  enable();session.state=phase("incoming",Call.STATE_RINGING);overlay.start();idle();
  assertFalse(overlay.isShowing());verify(windows,never()).addView(any(),any());
  overlay.onExpand();overlay.onOpen();overlay.onAction("ANSWER");overlay.onAction("DECLINE");idle();
  assertEquals(Call.STATE_RINGING,session.state.state);assertTrue(session.actions.isEmpty());
  for(int state:new int[]{Call.STATE_NEW,Call.STATE_DISCONNECTING,Call.STATE_DISCONNECTED}){
   session.state=phase("not-presented",state);PhoneSessionService.notifyViews();idle();assertFalse(overlay.isShowing());
  }
  verify(windows,never()).addView(any(),any());assertTrue(session.actions.isEmpty());
 }
 @Test public void answeredIncomingCallAppearsWithoutChangingPermissionAndWaitingCallHidesIt(){
  enable();session.state=phase("incoming",Call.STATE_RINGING);overlay.start();idle();assertFalse(overlay.isShowing());
  session.state=phase("incoming",Call.STATE_ACTIVE);PhoneSessionService.notifyViews();idle();assertTrue(overlay.isShowing());
  session.state=phase("waiting",Call.STATE_RINGING);PhoneSessionService.notifyViews();idle();assertFalse(overlay.isShowing());
  assertTrue(session.actions.isEmpty());
  for(int state:new int[]{Call.STATE_DIALING,Call.STATE_CONNECTING,Call.STATE_SELECT_PHONE_ACCOUNT,Call.STATE_HOLDING}){
   session.state=phase("owned-next",state);PhoneSessionService.notifyViews();idle();assertTrue(overlay.isShowing());
  }
  assertTrue(session.actions.isEmpty());assertTrue(prefs.getBoolean("island_outside_app",false));assertTrue(privacy.permitted);
 }
 @Test public void windowHasOnlyCapsuleBoundsAndCannotStealKeyboardOrOutsideTouches(){
  enable();overlay.start();assertTrue(overlay.isShowing());assertNotNull(added);assertNotNull(params);
  assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,params.type);
  assertTrue((params.flags&WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)!=0);
  assertTrue((params.flags&WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)!=0);
  assertTrue(params.width>0);assertTrue(params.height>0);
  assertTrue(params.width<RuntimeEnvironment.getApplication().getResources().getDisplayMetrics().widthPixels);
  assertTrue(params.height<RuntimeEnvironment.getApplication().getResources().getDisplayMetrics().heightPixels);
  overlay.onExpand();idle();assertTrue(overlay.isShowing());verify(windows,atLeastOnce()).updateViewLayout(eq(added),any());
  overlay.onCollapse();idle();assertTrue(overlay.isShowing());assertTrue(session.actions.isEmpty());
 }
 @Test public void screenPrivacyOwnAppAndPermissionRevocationRemoveTheWindowWithoutEndingCall(){
  enable();overlay.start();assertTrue(overlay.isShowing());
  privacy.locked=true;refresh();assertFalse(overlay.isShowing());assertNotNull(session.state);
  privacy.locked=false;refresh();assertTrue(overlay.isShowing());
  privacy.inside=true;refresh();assertFalse(overlay.isShowing());
  privacy.inside=false;refresh();assertTrue(overlay.isShowing());
  privacy.permitted=false;shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));assertFalse(overlay.isShowing());
  assertNotNull(session.state);assertTrue(session.actions.isEmpty());verify(windows,atLeast(3)).removeViewImmediate(any());
 }
 @Test public void preferenceOffCallEndAndServiceStopCleanUpIdempotently(){
  enable();overlay.start();prefs.edit().putBoolean("island_outside_app",false).commit();idle();assertFalse(overlay.isShowing());
  enable();assertTrue(overlay.isShowing());prefs.edit().putBoolean("island_enabled",false).commit();idle();assertFalse(overlay.isShowing());
  prefs.edit().putBoolean("island_enabled",true).commit();idle();assertTrue(overlay.isShowing());
  session.state=null;PhoneSessionService.notifyViews();idle();assertFalse(overlay.isShowing());
  session.state=active("next-call");PhoneSessionService.notifyViews();idle();assertTrue(overlay.isShowing());
  overlay.stop();overlay.stop();assertFalse(overlay.isShowing());reset(windows);
  PhoneSessionService.notifyViews();prefs.edit().putBoolean("island_outside_app",false).commit();
  shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));verifyNoInteractions(windows);assertTrue(session.actions.isEmpty());
 }
 @Test public void fullCallNavigationAndAudioCommandsUseOnlyCurrentSessionToken(){
  enable();overlay.start();overlay.onAction("MUTE_ON");assertEquals("owned-call:MUTE_ON",session.actions.get(0));
  overlay.onOpen();assertEquals("owned-call:OPEN",session.actions.get(1));
  session.state=active("replacement");overlay.onAction("SPEAKER_ON");assertEquals(2,session.actions.size());
  overlay.onAction("SPEAKER_ON");assertEquals("replacement:SPEAKER_ON",session.actions.get(2));
  privacy.locked=true;overlay.onAction("END");assertEquals(3,session.actions.size());assertNotNull(session.state);
 }
 @Test public void confirmationCancelDoesNotEndCallAndAcceptCannotEndReplacement(){
  enable();overlay.start();overlay.onAction("END");AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
  assertNotNull(dialog);assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,dialog.getWindow().getAttributes().type);
  dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();idle();assertNotNull(session.state);assertTrue(session.actions.isEmpty());
  overlay.onAction("END");dialog=ShadowAlertDialog.getLatestAlertDialog();session.state=active("replacement");
  dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();idle();assertNotNull(session.state);assertTrue(session.actions.isEmpty());
  overlay.onAction("END");dialog=ShadowAlertDialog.getLatestAlertDialog();dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();idle();
  assertNull(session.state);assertFalse(overlay.isShowing());assertEquals(java.util.Collections.singletonList("replacement:END"),session.actions);
 }
 @Test public void resumingAnyOwnActivityHidesExternalSurfaceBeforeInAppUi(){
  enable();overlay.start();assertTrue(overlay.isShowing());
  ActivityController<Activity> host=Robolectric.buildActivity(Activity.class).setup();
  try{
   assertFalse(overlay.isShowing());
   host.pause().stop();shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(181));assertTrue(overlay.isShowing());
  }finally{host.destroy();}
  assertNotNull(session.state);assertTrue(session.actions.isEmpty());
 }
 @Test public void windowPermissionFailureCannotInterruptOrDisconnectTelecom(){
  doThrow(new WindowManager.BadTokenException("Permission revoked during add")).when(windows).addView(any(),any());
  enable();overlay.start();assertFalse(overlay.isShowing());assertNotNull(session.state);assertTrue(session.actions.isEmpty());
  assertTrue(prefs.getString("last_call_failure","").contains("island-overlay-ui"));
 }
}
