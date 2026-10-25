package com.dacaller.app;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.telecom.Call;
import android.telecom.VideoProfile;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
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
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=33)
@LooperMode(LooperMode.Mode.PAUSED)
public class CallScreenActivityTest {
 private ActivityController<CallScreenActivity> controller;
 private Call call;
 private SharedPreferences preferences;
 @Before public void prepare(){
  Application application=RuntimeEnvironment.getApplication();
  preferences=application.getSharedPreferences("da_caller",Context.MODE_PRIVATE);
  preferences.edit().clear().putString("language","en").commit();
  call=mock(Call.class);when(call.getState()).thenReturn(Call.STATE_RINGING);
  PhoneSessionService.current=call;PhoneSessionService.instance=null;
 }
 @After public void clean(){if(controller!=null)controller.pause().stop().destroy();PhoneSessionService.current=null;PhoneSessionService.instance=null;}
 private CallScreenActivity open(){controller=Robolectric.buildActivity(CallScreenActivity.class).setup();return controller.get();}
 private View find(View view,String description){
  if(description.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;
  if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View result=find(group.getChildAt(i),description);if(result!=null)return result;}}
  return null;
 }
 private View find(CallScreenActivity activity,String description){return find(activity.getWindow().getDecorView(),description);}
 private void send(View view,int action,float x,float y){
  MotionEvent event=MotionEvent.obtain(10,20,action,x,y,0);view.dispatchTouchEvent(event);event.recycle();
 }
 @Test public void dialingAndConnectedScreensRenderAllFiveLayoutsWithoutCrashing(){
  // Previous tests only covered RINGING. Real outgoing calls reach DIALING first.
  for(String theme:CallThemes.IDS){
   preferences.edit().putString("call_theme",theme).commit();
   for(int state:new int[]{Call.STATE_DIALING,Call.STATE_ACTIVE,Call.STATE_HOLDING}){
    when(call.getState()).thenReturn(state);
    controller=Robolectric.buildActivity(CallScreenActivity.class).setup();
    CallScreenActivity activity=controller.get();
    assertFalse("crashed with "+theme+" state "+state,activity.isFinishing());
    assertNotNull("custom layout fell back unexpectedly "+theme,activity.findViewById(android.R.id.content).findViewWithTag("call-root"));
    assertNotNull("missing end-call control "+theme,
      find(activity,I18n.text(activity,"סיום שיחה")));
    controller.pause().stop().destroy();
    controller=null;
   }
  }
 }
 @Test public void emergencyCallControlsHaveCallEndAction(){
  when(call.getState()).thenReturn(Call.STATE_DIALING);
  CallScreenActivity activity=open();
  try{
   java.lang.reflect.Method emergency=CallScreenActivity.class.getDeclaredMethod("showEmergencyCallControls");
   emergency.setAccessible(true);emergency.invoke(activity);
  }catch(ReflectiveOperationException e){throw new AssertionError(e);}
  ViewGroup root=(ViewGroup)activity.findViewById(android.R.id.content);
  assertNotNull(root);
  assertNotNull("emergency UI must retain accessible end action",find(activity,"End call"));
  assertFalse("end label is TalkBack-only",hasText(root,"End call"));
  find(activity,"End call").performClick();verify(call).disconnect();
 }
 private boolean hasText(View view,String value){
  if(view instanceof android.widget.TextView&&value.equals(((android.widget.TextView)view).getText()))return true;
  if(view instanceof ViewGroup){
   ViewGroup group=(ViewGroup)view;
   for(int i=0;i<group.getChildCount();i++)
    if(hasText(group.getChildAt(i),value))return true;
  }
  return false;
 }
 @Test public void iosModeKeepsAnAccessibleAnswerAndDeclineButton(){
  CallScreenActivity activity=open();View answer=find(activity,"Answer"),decline=find(activity,"Decline");
  assertNotNull(answer);assertNotNull(decline);
  answer.performClick();verify(call).answer(VideoProfile.STATE_AUDIO_ONLY);
 }
 @Test public void tappingOrCancellingGestureDoesNotAnswer(){
  CallScreenActivity activity=open();View prompt=find(activity,"Swipe right to answer →");assertNotNull(prompt);
  send(prompt,MotionEvent.ACTION_DOWN,0,0);send(prompt,MotionEvent.ACTION_UP,0,0);
  send(prompt,MotionEvent.ACTION_DOWN,0,0);send(prompt,MotionEvent.ACTION_CANCEL,100,0);send(prompt,MotionEvent.ACTION_UP,100,0);
  verify(call,never()).answer(anyInt());verify(call,never()).reject(anyBoolean(),any());
 }
 @Test public void completedSwipeAnswersExactlyOnce(){
  View prompt=find(open(),"Swipe right to answer →");assertNotNull(prompt);
  send(prompt,MotionEvent.ACTION_DOWN,0,0);send(prompt,MotionEvent.ACTION_UP,200,0);
  send(prompt,MotionEvent.ACTION_UP,200,0);
  verify(call,times(1)).answer(VideoProfile.STATE_AUDIO_ONLY);
 }
 @Test @Config(sdk=26) public void android26UsesCompatibleLockscreenFlags(){assertFalse(open().isFinishing());}
 @Test public void staleAnswerButtonCannotAnswerReplacementCall(){
  View answer=find(open(),"Answer");Call replacement=mock(Call.class);when(replacement.getState()).thenReturn(Call.STATE_RINGING);
  PhoneSessionService.current=replacement;answer.performClick();
  verify(call,never()).answer(anyInt());verify(replacement,never()).answer(anyInt());
 }
 @Test public void allFiveThemesRespectLightDarkAndAmoled(){
  for(int i=0;i<CallThemes.IDS.length;i++){
   preferences.edit().putString("call_theme",CallThemes.IDS[i]).putBoolean("dark",false).putBoolean("amoled",false).commit();
   CallThemes light=new CallThemes(RuntimeEnvironment.getApplication());assertEquals(i,light.style);assertFalse(light.dark);
   preferences.edit().putBoolean("dark",true).commit();
   CallThemes dark=new CallThemes(RuntimeEnvironment.getApplication());assertTrue(dark.dark);assertNotEquals(light.background,dark.background);
   preferences.edit().putBoolean("amoled",true).commit();
   CallThemes amoled=new CallThemes(RuntimeEnvironment.getApplication());assertTrue(amoled.dark);assertTrue(amoled.amoled);assertEquals(android.graphics.Color.BLACK,amoled.background);
  }
 }
 private View tagged(CallScreenActivity activity,String tag){return activity.findViewById(android.R.id.content).findViewWithTag(tag);}
 @Test public void newInstallDefaultsToGoogleWithoutOverwritingSavedChoice(){
  assertEquals(1,new CallThemes(RuntimeEnvironment.getApplication()).style);
  preferences.edit().putString("call_theme","graphite").commit();
  assertEquals(0,new CallThemes(RuntimeEnvironment.getApplication()).style);
  open();assertEquals("graphite",preferences.getString("call_theme",""));
 }
 @Test public void sheetTransitionsAreExclusiveAndBackClosesSheet(){
  when(call.getState()).thenReturn(Call.STATE_ACTIVE);CallScreenActivity a=open();
  for(String next:new String[]{"more","audio","keypad","more"}){
   tagged(a,"call-action-"+next).performClick();
   for(String sheet:new String[]{"more","audio","keypad"})assertEquals(sheet.equals(next),tagged(a,"call-sheet-"+sheet)!=null);
   assertNotNull(tagged(a,"call-end"));assertNotNull(tagged(a,"call-sheet-close"));
  }
  a.onBackPressed();assertNull(tagged(a,"call-sheet-more"));assertFalse(a.isFinishing());
 }
 @Test public void noBluetoothOptionWithoutAnActualSupportedRoute(){
  when(call.getState()).thenReturn(Call.STATE_ACTIVE);CallScreenActivity a=open();
  tagged(a,"call-action-audio").performClick();
  assertNull(tagged(a,"audio-route-"+android.telecom.CallAudioState.ROUTE_BLUETOOTH));
 }
 @Test public void allEndButtonsAreIconOnlyAccessibleAndDisconnectTheRightCall(){
  when(call.getState()).thenReturn(Call.STATE_ACTIVE);
  for(String theme:CallThemes.IDS){
   preferences.edit().putString("call_theme",theme).commit();CallScreenActivity a=open();
   View end=tagged(a,"call-end");assertNotNull(end);assertEquals("End call",end.getContentDescription());
   assertFalse(hasText(end,"End call"));end.performClick();
   controller.pause().stop().destroy();controller=null;
  }
  verify(call,times(5)).disconnect();
 }
 @Test public void closingKeypadAndPausingStopsDtmf(){
  when(call.getState()).thenReturn(Call.STATE_ACTIVE);CallScreenActivity a=open();
  tagged(a,"call-action-keypad").performClick();tagged(a,"dtmf-5").performClick();verify(call).playDtmfTone('5');
  tagged(a,"call-sheet-close").performClick();verify(call).stopDtmfTone();
  tagged(a,"call-action-keypad").performClick();tagged(a,"dtmf-0").performClick();
  controller.pause();verify(call,times(2)).stopDtmfTone();
 }
 @Test public void replacingCallStopsToneAndStaleKeyCannotAffectSuccessor(){
  when(call.getState()).thenReturn(Call.STATE_ACTIVE);CallScreenActivity a=open();
  tagged(a,"call-action-keypad").performClick();View key=tagged(a,"dtmf-5");key.performClick();
  Call next=mock(Call.class);when(next.getState()).thenReturn(Call.STATE_ACTIVE);PhoneSessionService.current=next;
  PhoneSessionService.notifyViews();verify(call).stopDtmfTone();key.performClick();
  verify(call,times(1)).playDtmfTone('5');verify(next,never()).playDtmfTone(anyChar());
 }
 @Test public void holdUsesCapabilityAndNewCallPreservesExistingDialerEntryPoint(){
  when(call.getState()).thenReturn(Call.STATE_ACTIVE);
  Call.Details details=mock(Call.Details.class);when(call.getDetails()).thenReturn(details);when(details.can(Call.Details.CAPABILITY_HOLD)).thenReturn(true);
  preferences.edit().putBoolean("call_btn_addcall",true).commit();
  PhoneSessionService service=mock(PhoneSessionService.class);when(service.canAddCall()).thenReturn(true);PhoneSessionService.instance=service;
  CallScreenActivity a=open();tagged(a,"call-action-more").performClick();
  tagged(a,"call-option-hold").performClick();verify(call).hold();
  tagged(a,"call-option-addcall").performClick();
  android.content.Intent launched=org.robolectric.Shadows.shadowOf(a).getNextStartedActivity();
  assertNotNull(launched);assertEquals(android.content.Intent.ACTION_DIAL,launched.getAction());
  assertEquals(MainActivity.class.getName(),launched.getComponent().getClassName());
 }
 @Test public void unsupportedHoldDoesNotIssueTelecomCommand(){
  when(call.getState()).thenReturn(Call.STATE_ACTIVE);CallScreenActivity a=open();tagged(a,"call-action-more").performClick();
  View hold=tagged(a,"call-option-hold");assertTrue(hold.getContentDescription().toString().contains("Unavailable"));
  hold.performClick();verify(call,never()).hold();
 }

}
