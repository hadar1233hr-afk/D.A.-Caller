package com.dacaller.app;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Looper;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
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

/** Overlay lifecycle must preserve the app underneath it and never end a replacement call. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=33) @LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CallIslandControllerTest {
 public static class Host extends Activity implements CallIslandController.ManualHost {
  LinearLayout page;
  EditText query;
  ListView list;
  @Override public void onCreate(Bundle saved){super.onCreate(saved);replacePage("Original query",11);}
  void replacePage(String value,int top){
   page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);
   query=new EditText(this);query.setSingleLine(true);query.setText(value);
   page.addView(query,new LinearLayout.LayoutParams(-1,64));
   list=new ListView(this);list.setDivider(null);list.setAdapter(new BaseAdapter(){
    public int getCount(){return 80;}
    public Object getItem(int position){return "Contact "+position;}
    public long getItemId(int position){return 9000+position;}
    public boolean hasStableIds(){return true;}
    public View getView(int position,View convert,ViewGroup parent){
     TextView row=convert instanceof TextView?(TextView)convert:new TextView(Host.this);
     row.setText(String.valueOf(getItem(position)));row.setTextSize(16);row.setMinimumHeight(56);return row;
    }
   });
   page.addView(list,new LinearLayout.LayoutParams(-1,0,1));setContentView(page);
   FrameLayout.LayoutParams params=(FrameLayout.LayoutParams)page.getLayoutParams();
   params.setMargins(7,top,9,13);page.setLayoutParams(params);
  }
 }
 private static final class FakeSession implements CallIslandSession {
  CallIslandState state=active("original-call","Dana");
  final List<String> executed=new ArrayList<>();
  public CallIslandState current(){return state;}
  public boolean perform(String token,String action){
   if(state==null||!state.token.equals(token))return false;
   executed.add(token+":"+action);
   if("END".equals(action)||"DECLINE".equals(action))state=null;
   return true;
  }
  public void open(Activity host,String token){
   if(state!=null&&state.token.equals(token))executed.add(token+":OPEN");
  }
 }
 private ActivityController<Host> activity;
 private Host host;
 private SharedPreferences prefs;
 private FakeSession session;
 private CallIslandController island;
 @Before public void setUp(){
  prefs=RuntimeEnvironment.getApplication().getSharedPreferences("da_caller",Context.MODE_PRIVATE);
  prefs.edit().clear().putString("language","en").putBoolean("haptics",false)
    .putBoolean("island_enabled",true).putBoolean("island_animations",false)
    .putBoolean("island_confirm_end",true).commit();
  activity=Robolectric.buildActivity(Host.class).create().start().resume().visible();host=activity.get();
  session=new FakeSession();island=new CallIslandController(host,session);layout();
 }
 @After public void tearDown(){
  if(island!=null)island.stop();
  AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();if(dialog!=null&&dialog.isShowing())dialog.dismiss();
  if(activity!=null)activity.pause().stop().destroy();shadowOf(Looper.getMainLooper()).idle();
 }
 private static CallIslandState active(String token,String name){
  return new CallIslandState(token,name,"0500099901",Call.STATE_ACTIVE,CallAudioState.ROUTE_EARPIECE,
    CallAudioState.ROUTE_EARPIECE|CallAudioState.ROUTE_SPEAKER,false,true,true,System.currentTimeMillis()-12_000,-1);
 }
 private FrameLayout content(){return host.findViewById(android.R.id.content);}
 private void layout(){
  for(int i=0;i<3;i++){
   shadowOf(Looper.getMainLooper()).idle();FrameLayout content=content();
   content.measure(View.MeasureSpec.makeMeasureSpec(480,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(854,View.MeasureSpec.EXACTLY));
   content.layout(0,0,480,854);content.getViewTreeObserver().dispatchOnGlobalLayout();
  }
  shadowOf(Looper.getMainLooper()).idle();
 }
 private View tag(String value){return content().findViewWithTag(value);}
 private boolean visibleIsland(){View view=tag("island-root");return view!=null&&view.getVisibility()==View.VISIBLE&&view.getParent()!=null;}
 private FrameLayout.LayoutParams pageParams(){return (FrameLayout.LayoutParams)host.page.getLayoutParams();}
 private void assertOriginalMargins(int top){
  FrameLayout.LayoutParams params=pageParams();assertEquals(7,params.leftMargin);assertEquals(top,params.topMargin);
  assertEquals(9,params.rightMargin);assertEquals(13,params.bottomMargin);
 }
 private void click(String tag){View view=tag(tag);assertNotNull("Missing island control: "+tag,view);assertTrue(view.performClick());layout();}
 private void expand(){click("island-expand");assertTrue(island.isExpanded());}
 private long anchor(){return host.list.getAdapter().getItemId(host.list.getFirstVisiblePosition());}
 private int offset(){assertNotNull(host.list.getChildAt(0));return host.list.getChildAt(0).getTop();}

 @Test public void appearingAndExpandingReserveSpaceWithoutLosingQueryFocusOrListAnchor(){
  host.query.requestFocus();host.list.setSelectionFromTop(22,-8);layout();long id=anchor();int top=offset();
  island.start();layout();assertTrue(visibleIsland());
  assertEquals("Original query",host.query.getText().toString());assertTrue(host.query.hasFocus());
  assertEquals(id,anchor());assertEquals(top,offset());
  View capsule=tag("island-root");assertTrue("App content must start below the capsule",host.page.getTop()>=capsule.getBottom());
  expand();assertEquals("Original query",host.query.getText().toString());assertEquals(id,anchor());assertEquals(top,offset());
  assertTrue(host.page.getTop()>=tag("island-root").getBottom());assertTrue(session.executed.isEmpty());
 }
 @Test public void endedCallRemovesIslandAndRestoresOriginalAppMargins(){
  island.start();layout();assertTrue(visibleIsland());assertTrue(pageParams().topMargin>11);
  session.state=null;PhoneSessionService.notifyViews();layout();
  assertFalse(visibleIsland());assertOriginalMargins(11);assertEquals("Original query",host.query.getText().toString());
  assertTrue(session.executed.isEmpty());
 }
 @Test public void stopAndRestartPreserveCallAndRestoreAppLayout(){
  CallIslandState original=session.state;island.start();layout();expand();
  island.stop();layout();assertFalse(visibleIsland());assertOriginalMargins(11);assertSame(original,session.state);
  PhoneSessionService.notifyViews();layout();assertFalse(visibleIsland());assertTrue(session.executed.isEmpty());
  island.start();layout();assertTrue(visibleIsland());assertSame(original,session.state);assertTrue(session.executed.isEmpty());
 }
 @Test public void disablingAndReenablingPreferenceNeverChangesTheLiveCall(){
  CallIslandState original=session.state;island.start();layout();assertTrue(visibleIsland());
  prefs.edit().putBoolean("island_enabled",false).commit();layout();assertFalse(visibleIsland());assertOriginalMargins(11);
  assertSame(original,session.state);assertTrue(session.executed.isEmpty());
  prefs.edit().putBoolean("island_enabled",true).commit();layout();assertTrue(visibleIsland());assertSame(original,session.state);
 }
 @Test public void pageReplacementReattachesIslandWithoutResettingTheNewPage(){
  island.start();layout();LinearLayout old=host.page;
  host.replacePage("Replacement query",23);layout();host.list.setSelectionFromTop(31,-6);layout();long id=anchor();int top=offset();
  island.refresh();layout();assertTrue(visibleIsland());assertNotSame(old,host.page);
  assertEquals("Replacement query",host.query.getText().toString());assertEquals(id,anchor());assertEquals(top,offset());
  assertTrue(host.page.getTop()>=tag("island-root").getBottom());
  island.stop();layout();assertOriginalMargins(23);assertTrue(session.executed.isEmpty());
 }
 @Test public void endCallRequiresConfirmationAndCancelKeepsItRunning(){
  island.start();layout();expand();click("island-action-END");
  AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();assertNotNull(dialog);assertTrue(dialog.isShowing());
  assertTrue(session.executed.isEmpty());dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();layout();
  assertNotNull(session.state);assertTrue(session.executed.isEmpty());
  click("island-action-END");dialog=ShadowAlertDialog.getLatestAlertDialog();assertTrue(dialog.isShowing());
  dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();layout();
  assertNull(session.state);assertEquals(java.util.Collections.singletonList("original-call:END"),session.executed);
  assertFalse(visibleIsland());assertOriginalMargins(11);
 }
 @Test public void delayedConfirmationForOldCallCannotEndItsReplacement(){
  island.start();layout();expand();click("island-action-END");AlertDialog old=ShadowAlertDialog.getLatestAlertDialog();assertNotNull(old);
  CallIslandState replacement=active("replacement-call","Alex");session.state=replacement;PhoneSessionService.notifyViews();layout();
  // A delayed click may already be queued even when a refreshed UI dismissed the old dialog.
  old.getButton(DialogInterface.BUTTON_POSITIVE).performClick();layout();
  assertSame(replacement,session.state);assertTrue(session.executed.isEmpty());assertTrue(visibleIsland());
 }
 @Test public void repeatedStartAndStopDoNotLeakServiceListeners()throws Exception{
  Field field=PhoneSessionService.class.getDeclaredField("listeners");field.setAccessible(true);
  CopyOnWriteArrayList<?> listeners=(CopyOnWriteArrayList<?>)field.get(null);int before=listeners.size();
  island.start();island.start();layout();assertEquals(before+1,listeners.size());
  island.stop();island.stop();layout();assertEquals(before,listeners.size());assertOriginalMargins(11);
  island.start();layout();assertEquals(before+1,listeners.size());island.stop();layout();assertEquals(before,listeners.size());
  assertTrue(session.executed.isEmpty());
 }
}
