package com.dacaller.app;

import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Looper;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
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
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

/** Real font metrics and touch geometry, plus command ownership at the view boundary. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=33,application=Application.class,qualifiers="w320dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class CallIslandViewTest {
 private ActivityController<Activity> activityController;
 private Activity activity;
 private FrameLayout content;
 private CallIslandView island;
 private SharedPreferences prefs;
 private Listener listener;
 private float originalFontScale;
 private long connected;

 private static final class Listener implements CallIslandView.Listener {
  int expands,collapses,opens;
  final List<String> actions=new ArrayList<>();
  @Override public void onExpand(){expands++;}
  @Override public void onCollapse(){collapses++;}
  @Override public void onAction(String action){actions.add(action);}
  @Override public void onOpen(){opens++;}
 }
 @Before public void setup(){
  originalFontScale=RuntimeEnvironment.getApplication().getResources().getConfiguration().fontScale;
  RuntimeEnvironment.setFontScale(1.3f);
  activityController=Robolectric.buildActivity(Activity.class).setup().visible();activity=activityController.get();
  prefs=activity.getSharedPreferences("da_caller",Context.MODE_PRIVATE);
  prefs.edit().clear().putString("language","en").putBoolean("island_animations",false).commit();
  content=new FrameLayout(activity);activity.setContentView(content);
  island=new CallIslandView(activity);content.addView(island,new FrameLayout.LayoutParams(-1,-2));
  listener=new Listener();island.setListener(listener);connected=System.currentTimeMillis()-10_000;
 }
 @After public void cleanup(){
  activityController.pause().stop().destroy();RuntimeEnvironment.setFontScale(originalFontScale);
 }
 private int dp(int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
 private CallIslandState state(String token,int phase,boolean muted,int route,boolean hold,boolean mute,int supported){
  return new CallIslandState(token,"Dana Notification Test","0500099902",phase,route,supported,muted,hold,mute,connected,-1);
 }
 private CallIslandState active(String token){
  return state(token,Call.STATE_ACTIVE,false,CallAudioState.ROUTE_EARPIECE,true,true,
    CallAudioState.ROUTE_EARPIECE|CallAudioState.ROUTE_SPEAKER);
 }
 private void layout(int viewportDp){
  int width=dp(viewportDp-24),height=dp(800);
  content.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
    View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
  content.layout(0,0,width,height);
  Shadows.shadowOf(Looper.getMainLooper()).idle();
 }
 private View tag(String value){return island.findViewWithTag(value);}
 private View required(String value){View found=tag(value);assertNotNull(value,found);return found;}
 private Rect bounds(View view){
  Rect result=new Rect(0,0,view.getWidth(),view.getHeight());island.offsetDescendantRectToMyCoords(view,result);return result;
 }
 private void touchTargetFits(View target){
  assertTrue(target.getTag()+" is narrower than48dp",target.getWidth()>=dp(48));
  assertTrue(target.getTag()+" is shorter than48dp",target.getHeight()>=dp(48));
  Rect rect=bounds(target);
  assertTrue(target.getTag()+" escaped capsule: "+rect+" inside "+island.getWidth()+"x"+island.getHeight(),
    rect.left>=0&&rect.top>=0&&rect.right<=island.getWidth()&&rect.bottom<=island.getHeight());
 }
 private void textFitsVertically(View view){
  if(view instanceof TextView){
   TextView text=(TextView)view;assertNotNull(text.getText().toString(),text.getLayout());
   assertTrue("Cropped text: "+text.getText(),text.getLayout().getHeight()<=text.getHeight()-text.getCompoundPaddingTop()-text.getCompoundPaddingBottom());
  }
  if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)textFitsVertically(((ViewGroup)view).getChildAt(i));
 }

 @Test public void largeFontControlsRemainTouchableAt320And240dpWithoutOverlapping(){
  for(String language:new String[]{"he","en"})for(int viewport:new int[]{320,240}){
   prefs.edit().putString("language",language).commit();island.bind(active("call"),true);layout(viewport);
   assertEquals("Dana Notification Test",((TextView)required("island-caller")).getText().toString());
   List<Rect> used=new ArrayList<>();
   for(String action:new String[]{"MUTE_ON","SPEAKER_ON","HOLD","END"}){
    View target=required("island-action-"+action);touchTargetFits(target);Rect rect=bounds(target);
    for(Rect previous:used)assertFalse("Call controls overlap",Rect.intersects(previous,rect));used.add(rect);
   }
   touchTargetFits(required("island-collapse"));touchTargetFits(required("island-open"));textFitsVertically(island);
  }
 }

 @Test public void unavailableAndUserHiddenControlsStayOutOfTheActionSurface(){
  island.bind(state("private",Call.STATE_ACTIVE,false,CallAudioState.ROUTE_EARPIECE,false,false,CallAudioState.ROUTE_EARPIECE),true);layout(320);
  assertNull(tag("island-action-MUTE_ON"));assertNull(tag("island-action-SPEAKER_ON"));assertNull(tag("island-action-HOLD"));required("island-action-END");
  prefs.edit().putBoolean("island_show_mute",false).putBoolean("island_show_speaker",false).putBoolean("island_show_hold",false).commit();
  island.bind(active("private"),true);layout(320);
  assertNull(tag("island-action-MUTE_ON"));assertNull(tag("island-action-SPEAKER_ON"));assertNull(tag("island-action-HOLD"));required("island-action-END");
  island.bind(state("incoming",Call.STATE_RINGING,false,CallAudioState.ROUTE_EARPIECE,false,false,0),true);layout(240);
  touchTargetFits(required("island-action-ANSWER"));touchTargetFits(required("island-action-DECLINE"));assertNull(tag("island-action-END"));
  assertEquals("Incoming call",((TextView)required("island-phase")).getText().toString());
 }

 @Test public void elapsedTicksDoNotReplaceControlsOrTheirFocus(){
  CallIslandState snapshot=active("clock");island.bind(snapshot,true);layout(320);
  View mute=required("island-action-MUTE_ON");mute.setFocusableInTouchMode(true);assertTrue(mute.requestFocus());TextView timer=(TextView)required("island-timer");
  island.tick(connected+10_000);assertEquals("00:10",timer.getText().toString());
  island.tick(connected+11_000);assertEquals("00:11",timer.getText().toString());
  assertSame(mute,required("island-action-MUTE_ON"));assertSame(timer,required("island-timer"));assertTrue(mute.hasFocus());assertTrue(listener.actions.isEmpty());
  island.bind(snapshot,true);layout(320);assertSame(mute,required("island-action-MUTE_ON"));
 }

 @Test public void actionIsExplicitAndSelectionWaitsForAcknowledgedSnapshot(){
  island.bind(active("audio"),true);layout(320);required("island-action-MUTE_ON").performClick();
  assertEquals(java.util.Collections.singletonList("MUTE_ON"),listener.actions);
  required("island-action-MUTE_ON");assertNull(tag("island-action-MUTE_OFF"));
  island.bind(state("audio",Call.STATE_ACTIVE,true,CallAudioState.ROUTE_EARPIECE,true,true,
    CallAudioState.ROUTE_EARPIECE|CallAudioState.ROUTE_SPEAKER),true);layout(320);
  required("island-action-MUTE_OFF").performClick();assertEquals("MUTE_OFF",listener.actions.get(1));
  required("island-action-SPEAKER_ON").performClick();assertEquals("SPEAKER_ON",listener.actions.get(2));
  island.bind(state("audio",Call.STATE_ACTIVE,true,CallAudioState.ROUTE_SPEAKER,true,true,
    CallAudioState.ROUTE_EARPIECE|CallAudioState.ROUTE_SPEAKER),true);layout(320);
  required("island-action-SPEAKER_OFF").performClick();assertEquals("SPEAKER_OFF",listener.actions.get(3));
 }

 @Test public void removedCallControlsCannotDispatchAgainstTheirSuccessor(){
  island.bind(active("old"),true);layout(320);View oldEnd=required("island-action-END"),oldOpen=required("island-open");
  island.bind(active("new"),true);layout(320);oldEnd.performClick();oldOpen.performClick();
  assertTrue(listener.actions.isEmpty());assertEquals(0,listener.opens);
  View newEnd=required("island-action-END");newEnd.performClick();assertEquals(java.util.Collections.singletonList("END"),listener.actions);
  island.bind(null,false);newEnd.performClick();assertEquals(1,listener.actions.size());assertEquals(0,island.getChildCount());
 }

 @Test public void compactExpansionCollapseAndOpenHaveSeparateCallbacks(){
  island.bind(active("navigation"),false);layout(320);assertFalse(island.isExpanded());
  required("island-expand").performClick();assertEquals(1,listener.expands);assertFalse(island.isExpanded());
  required("island-expand").performLongClick();assertEquals(1,listener.opens);assertTrue(listener.actions.isEmpty());
  island.bind(active("navigation"),true);layout(320);assertTrue(island.isExpanded());
  required("island-collapse").performClick();assertEquals(1,listener.collapses);
  required("island-open").performClick();assertEquals(2,listener.opens);assertTrue(listener.actions.isEmpty());
 }

 @Test public void sizesStylesAndTimerChoiceKeepAUsableCompactCapsule(){
  for(String style:new String[]{"apple","glass","emerald"}){
   prefs.edit().putString("island_style",style).putString("island_size","compact").commit();
   island.bind(active("preferences"),false);layout(320);int compactHeight=island.getHeight();
   assertTrue(compactHeight>=dp(56));required("island-timer");
   prefs.edit().putString("island_size","comfortable").putBoolean("island_show_timer",false).commit();
   island.bind(active("preferences"),false);layout(320);
   assertTrue(island.getHeight()>=dp(64));assertTrue(island.getHeight()>=compactHeight);assertNull(tag("island-timer"));
   touchTargetFits(required("island-expand"));textFitsVertically(island);
   prefs.edit().putBoolean("island_show_timer",true).commit();
  }
 }
}
