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
  PhoneSessionService.current=call;
 }
 @After public void clean(){if(controller!=null)controller.pause().stop().destroy();PhoneSessionService.current=null;}
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
  assertTrue("emergency UI must show an end-call label",hasText(root,I18n.text(activity,"סיום שיחה")));
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
}
