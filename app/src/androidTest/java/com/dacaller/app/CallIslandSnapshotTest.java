package com.dacaller.app;

import android.app.Activity;
import android.animation.ValueAnimator;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.test.InstrumentationTestCase;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inspector.WindowInspector;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;

/** Native window/layout/interaction evidence. Session state is deliberately synthetic. */
@SuppressWarnings({"deprecation","unchecked"})
@android.annotation.TargetApi(33)
public final class CallIslandSnapshotTest extends InstrumentationTestCase {
 private static final String DIRECTORY="/sdcard/Download/DA-CALLER-snapshots";
 private static boolean matrixPassed,actionsPassed,settingsPassed,animationsPassed;
 private Context target;
 private SharedPreferences prefs;
 private Map<String,?> original;
 private String originalFont,originalDuration;
 private CallIslandFixtureActivity host;
 private final List<Activity> launched=new ArrayList<>();
 @Override protected void setUp()throws Exception{
  super.setUp();target=getInstrumentation().getTargetContext();prefs=target.getSharedPreferences("da_caller",Context.MODE_PRIVATE);
  original=prefs.getAll();originalFont=shell("settings get system font_scale").trim();
  originalDuration=shell("settings get global animator_duration_scale").trim();
  shell("mkdir -p "+DIRECTORY);shell("settings put system font_scale 1.3");Thread.sleep(300);
  prefs.edit().putBoolean("island_enabled",true).putBoolean("island_animations",false)
   .putBoolean("island_show_timer",true).putBoolean("island_show_photo",true)
   .putBoolean("island_show_mute",true).putBoolean("island_show_speaker",true).putBoolean("island_show_hold",true)
   .putBoolean("island_confirm_end",true).putString("island_size","compact").putString("avatar_shape","circle")
   .putBoolean("haptics",false).putBoolean("amoled",false).commit();
 }
 @Override protected void tearDown()throws Exception{
  try{
   for(int i=launched.size()-1;i>=0;i--){Activity activity=launched.get(i);if(activity!=null&&!activity.isDestroyed())ui(activity::finish);}
   settle();restore(prefs,original);shell("settings put system font_scale "+(originalFont.matches("[0-9.]+")?originalFont:"1.0"));
   shell("settings put global animator_duration_scale "+(originalDuration.matches("[0-9.]+")?originalDuration:"1.0"));
  }finally{super.tearDown();}
 }
 private void ui(Runnable action)throws Exception{
  AtomicReference<Throwable> error=new AtomicReference<>();getInstrumentation().runOnMainSync(()->{try{action.run();}catch(Throwable failure){error.set(failure);}});
  if(error.get()!=null)throw new AssertionError(error.get());
 }
 private void settle()throws Exception{getInstrumentation().waitForIdleSync();Thread.sleep(180);getInstrumentation().waitForIdleSync();}
 private interface Check{boolean ready();}
 private void await(Check check,String failure)throws Exception{
  long end=SystemClock.uptimeMillis()+7000;
  do{getInstrumentation().waitForIdleSync();if(check.ready())return;Thread.sleep(40);}while(SystemClock.uptimeMillis()<end);fail(failure);
 }
 private int dp(float n){return Math.round(n*target.getResources().getDisplayMetrics().density);}
 private View view(Activity activity,String tag){return activity.getWindow().getDecorView().findViewWithTag(tag);}
 private View tagged(Activity activity,String tag){View result=view(activity,tag);assertNotNull("Missing "+tag,result);return result;}
 private <T extends View>T find(View root,Class<T> type){
  if(type.isInstance(root))return type.cast(root);
  if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){
   T match=find(((ViewGroup)root).getChildAt(i),type);if(match!=null)return match;
  }return null;
 }
 private void visible(View view){
  Rect bounds=new Rect();assertTrue("Hidden "+view.getTag(),view.isShown()&&view.getGlobalVisibleRect(bounds));
  assertTrue("Horizontal clipping "+view.getTag(),bounds.width()>=view.getWidth()-1);
  assertTrue("Vertical clipping "+view.getTag(),bounds.height()>=view.getHeight()-1);
 }
 private void textFits(View root){
  if(!root.isShown())return;
  if(root instanceof TextView&&((TextView)root).length()>0){
   TextView text=(TextView)root;assertNotNull(text.getLayout());
   int inner=text.getHeight()-text.getCompoundPaddingTop()-text.getCompoundPaddingBottom();
   assertTrue("Clipped glyph rows "+text.getText(),text.getLayout().getHeight()<=inner+1);
  }
  if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)textFits(((ViewGroup)root).getChildAt(i));
 }
 private void tap(View view)throws Exception{
  int[] point=new int[2];ui(()->{visible(view);view.getLocationOnScreen(point);point[0]+=view.getWidth()/2;point[1]+=view.getHeight()/2;});
  long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,point[0],point[1],0);
  MotionEvent up=MotionEvent.obtain(now,now+45,MotionEvent.ACTION_UP,point[0],point[1],0);
  getInstrumentation().sendPointerSync(down);getInstrumentation().sendPointerSync(up);down.recycle();up.recycle();settle();
 }
 private void snapshot(String name)throws Exception{
  settle();String path=DIRECTORY+"/island-"+name+".png";shell("screencap -p "+path);
  assertEquals("regular file",shell("stat -c %F "+path).trim());assertTrue(Long.parseLong(shell("stat -c %s "+path).trim())>1000);
 }
 private void launch(String language,boolean dark,String style,int state,boolean noAudio)throws Exception{
  if(host!=null&&!host.isDestroyed()){ui(host::finish);settle();}
  prefs.edit().putString("language",language).putBoolean("dark",dark).putBoolean("amoled",false)
   .putString("island_style",style).putBoolean("island_enabled",true).commit();
  host=(CallIslandFixtureActivity)getInstrumentation().startActivitySync(new Intent(target,CallIslandFixtureActivity.class)
   .putExtra("state",state).putExtra("no_audio",noAudio).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));launched.add(host);settle();
  await(()->host.hasWindowFocus()&&view(host,"island-root")!=null,"Island did not attach to the focused native Activity");
  assertEquals(1.3f,host.getResources().getConfiguration().fontScale,0.05f);
 }
 private void expanded()throws Exception{
  if(!host.controller.isExpanded())tap(tagged(host,"island-expand"));
  await(()->host.controller.isExpanded(),"Island did not expand");settle();
 }
 private void geometry()throws Exception{
  ui(()->{
   View island=tagged(host,"island-root");visible(island);textFits(island);
   assertTrue("Island must preserve usable contacts below it",host.list.getHeight()>=dp(72));
   Rect islandBounds=new Rect(),contentBounds=new Rect();island.getGlobalVisibleRect(islandBounds);host.content.getGlobalVisibleRect(contentBounds);
   assertTrue("Island overlays the first content row",contentBounds.top>=islandBounds.bottom);
   assertTrue("Island has a usable touch height",island.getHeight()>=dp(48));
   assertTrue("Caller must be present",((TextView)tagged(host,"island-caller")).getText().toString().contains(host.session.value.name));
  });
 }
 public void testAllStylesLanguagesThemesAndCallStatesAtLargeFont()throws Exception{
  for(boolean dark:new boolean[]{false,true})for(String language:new String[]{"he","en"})for(String style:new String[]{"apple","glass","emerald"}){
   launch(language,dark,style,Call.STATE_ACTIVE,false);String suffix=style+"-"+(dark?"dark":"light")+"-"+language;
   assertFalse(host.controller.isExpanded());geometry();snapshot(suffix+"-compact");
   expanded();geometry();snapshot(suffix+"-expanded");
   tap(tagged(host,"island-collapse"));await(()->!host.controller.isExpanded(),"Header collapse failed");geometry();
  }
  int[] states={Call.STATE_RINGING,Call.STATE_DIALING,Call.STATE_HOLDING,Call.STATE_ACTIVE};
  String[] labels={"incoming","outgoing","held","noaudio"};
  for(int i=0;i<states.length;i++){
   launch("he",true,"apple",states[i],i==3);expanded();geometry();
   if(i==0){visible(tagged(host,"island-action-ANSWER"));visible(tagged(host,"island-action-DECLINE"));}
   if(i==2)visible(tagged(host,"island-action-RESUME"));
   if(i==3){assertNull(view(host,"island-action-MUTE_ON"));assertNull(view(host,"island-action-SPEAKER_ON"));}
   snapshot("state-"+labels[i]);
  }
  matrixPassed=true;writeEvidence();
 }
 private View dialogButton(int id){
  for(View window:WindowInspector.getGlobalWindowViews()){View button=window.findViewById(id);if(button!=null&&button.isShown())return button;}
  return null;
 }
 private void assertAnchor(long identity,int top,String query)throws Exception{
  ui(()->{
   assertEquals(query,host.query.getText().toString());assertNotNull(host.list.getChildAt(0));
   assertEquals(identity,host.list.getAdapter().getItemId(host.list.getFirstVisiblePosition()));assertEquals(top,host.list.getChildAt(0).getTop());
  });
 }
 private ValueAnimator animator(View waveform){
  if(waveform==null)return null;
  try{Field field=IslandWaveformView.class.getDeclaredField("animator");field.setAccessible(true);return (ValueAnimator)field.get(waveform);}
  catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
 }
 private float phase(View waveform){
  try{Field field=IslandWaveformView.class.getDeclaredField("phase");field.setAccessible(true);return field.getFloat(waveform);}
  catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
 }
 private boolean animationRunning(View waveform){ValueAnimator animator=animator(waveform);return animator!=null&&animator.isRunning();}
 public void testCommandsConfirmationPauseReturnRecreationAndContentRestore()throws Exception{
  launch("en",false,"apple",Call.STATE_ACTIVE,false);
  ui(()->{host.query.setText("Preserved query");host.list.setSelectionFromTop(28,-9);});settle();
  final long identity=host.list.getAdapter().getItemId(host.list.getFirstVisiblePosition());final int top=host.list.getChildAt(0).getTop();
  expanded();tap(tagged(host,"island-action-MUTE_ON"));await(()->host.session.value.muted,"Mute action was not acknowledged");
  tap(tagged(host,"island-action-MUTE_OFF"));await(()->!host.session.value.muted,"Unmute action was not acknowledged");
  tap(tagged(host,"island-action-SPEAKER_ON"));await(()->host.session.value.route==CallAudioState.ROUTE_SPEAKER,"Speaker on was not acknowledged");
  tap(tagged(host,"island-action-SPEAKER_OFF"));await(()->host.session.value.route==CallAudioState.ROUTE_EARPIECE,"Speaker off was not acknowledged");
  tap(tagged(host,"island-action-HOLD"));await(()->host.session.value.state==Call.STATE_HOLDING,"Hold was not acknowledged");
  tap(tagged(host,"island-action-RESUME"));await(()->host.session.value.state==Call.STATE_ACTIVE,"Resume was not acknowledged");
  assertEquals(6,host.session.actions);assertAnchor(identity,top,"Preserved query");
  Instrumentation.ActivityMonitor callMonitor=getInstrumentation().addMonitor(CallScreenActivity.class.getName(),null,false);
  try{
   tap(tagged(host,"island-open"));Activity call=callMonitor.waitForActivityWithTimeout(7000);assertNotNull("Open action must launch full call UI",call);launched.add(call);settle();
   assertEquals(1,host.session.opens);getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
   await(()->host.hasWindowFocus(),"System Back did not return to the same app screen");settle();
  }finally{getInstrumentation().removeMonitor(callMonitor);}
  assertAnchor(identity,top,"Preserved query");expanded();
  tap(tagged(host,"island-action-END"));await(()->dialogButton(android.R.id.button1)!=null,"Hang-up confirmation was not shown");
  assertNotNull("Merely opening confirmation must not end a call",host.session.value);assertEquals(6,host.session.actions);
  tap(dialogButton(android.R.id.button2));await(()->dialogButton(android.R.id.button1)==null,"Cancel did not dismiss confirmation");
  assertNotNull(host.session.value);assertEquals(6,host.session.actions);
  tap(tagged(host,"island-action-END"));await(()->dialogButton(android.R.id.button1)!=null,"Second confirmation missing");tap(dialogButton(android.R.id.button1));
  await(()->host.session.value==null&&view(host,"island-root")==null,"Confirmed end did not remove the island");settle();
  assertEquals(host.originalTopMargin,((FrameLayout.LayoutParams)host.content.getLayoutParams()).topMargin);assertAnchor(identity,top,"Preserved query");
  ui(()->host.setState(Call.STATE_ACTIVE,false));await(()->view(host,"island-root")!=null,"New session did not appear");settle();
  int actions=host.session.actions;prefs.edit().putBoolean("island_enabled",false).commit();
  await(()->view(host,"island-root")==null,"Disabled island still intercepts the screen");settle();
  assertNotNull("Disabling UI must leave call state intact",host.session.value);assertEquals(actions,host.session.actions);
  assertEquals(host.originalTopMargin,((FrameLayout.LayoutParams)host.content.getLayoutParams()).topMargin);assertAnchor(identity,top,"Preserved query");
  prefs.edit().putBoolean("island_enabled",true).commit();await(()->view(host,"island-root")!=null,"Re-enabled island missing");settle();
  if(host.controller.isExpanded())tap(tagged(host,"island-collapse"));
  try{
  shell("settings put global animator_duration_scale 1");await(ValueAnimator::areAnimatorsEnabled,"System animation setting did not propagate");
  prefs.edit().putBoolean("island_animations",true).commit();
  await(()->animationRunning(view(host,"island-activity")),"Attached activity bars never started their native animator");
  View running=tagged(host,"island-activity");float initialPhase=phase(running);
  await(()->Math.abs(phase(running)-initialPhase)>.02f,"Native animated activity bars did not advance");
  prefs.edit().putBoolean("island_animations",false).commit();
  await(()->animator(running)==null&&animator(view(host,"island-activity"))==null,"Disabling island motion left a running animator");
  View reduced=tagged(host,"island-activity");float reducedPhase=phase(reduced);Thread.sleep(180);
  assertEquals("Reduced-motion bars must remain stationary",reducedPhase,phase(reduced),.001f);
  prefs.edit().putBoolean("island_animations",true).commit();
  await(()->animationRunning(view(host,"island-activity")),"Re-enabling motion did not start a native animator");
  View beforePause=tagged(host,"island-activity");
  getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_HOME);await(()->!host.hasWindowFocus(),"Fixture did not pause");settle();
  assertNotNull(host.session.value);assertEquals(actions,host.session.actions);assertNull("Paused island should detach and stop animation",view(host,"island-root"));
  assertFalse(beforePause.isAttachedToWindow());assertNull("Detached activity bars retain no animator",animator(beforePause));
  // Simulate an explicit launcher/user return, avoiding background-start policy
  // differences unrelated to this app-owned surface's lifecycle.
  shell("am start -n "+target.getPackageName()+"/.CallIslandFixtureActivity --activity-reorder-to-front --activity-single-top");
  await(()->host.hasWindowFocus()&&view(host,"island-root")!=null,"Resuming the same app task did not restore the island");settle();
  await(()->animationRunning(view(host,"island-activity")),"Resumed activity bars did not restart");
  assertAnchor(identity,top,"Preserved query");
  shell("settings put global animator_duration_scale 0");await(()->!ValueAnimator.areAnimatorsEnabled(),"System reduced-motion setting did not propagate");
  prefs.edit().putBoolean("island_animations",false).commit();settle();prefs.edit().putBoolean("island_animations",true).commit();settle();
  View systemReduced=tagged(host,"island-activity");assertNull("System reduced motion must prevent native animator creation",animator(systemReduced));
  float systemPhase=phase(systemReduced);Thread.sleep(180);assertEquals(systemPhase,phase(systemReduced),.001f);
  animationsPassed=true;
  }finally{
   prefs.edit().putBoolean("island_animations",false).commit();
   shell("settings put global animator_duration_scale "+(originalDuration.matches("[0-9.]+")?originalDuration:"1.0"));settle();
  }
  Instrumentation.ActivityMonitor recreation=getInstrumentation().addMonitor(CallIslandFixtureActivity.class.getName(),null,false);
  String token=host.session.value.token;
  try{
   ui(host::recreate);Activity next=recreation.waitForActivityWithTimeout(7000);assertNotNull(next);host=(CallIslandFixtureActivity)next;launched.add(host);
   await(()->host.hasWindowFocus()&&view(host,"island-root")!=null,"Recreated window did not restore island");settle();
  }finally{getInstrumentation().removeMonitor(recreation);}
  assertNotNull(host.session.value);assertEquals(token,host.session.value.token);assertEquals(actions,host.session.actions);assertAnchor(identity,top,"Preserved query");
  actionsPassed=true;writeEvidence();
 }
 public void testCustomizationPreviewBothThemesAndBottomControlsAtLargeFont()throws Exception{
  for(boolean dark:new boolean[]{false,true}){
   prefs.edit().putBoolean("dark",dark).putBoolean("amoled",false).putString("language","he").putString("island_style","apple").commit();
   Activity settings=getInstrumentation().startActivitySync(new Intent(target,CallIslandSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));launched.add(settings);settle();
   await(settings::hasWindowFocus,"Customization must own the window before capture");
   ScrollView scroll=find(settings.getWindow().getDecorView(),ScrollView.class);assertNotNull(scroll);
   ui(()->scroll.scrollTo(0,0));settle();ui(()->visible(tagged(settings,"island-settings-preview")));
   snapshot("settings-"+(dark?"dark":"light"));
   ui(()->scroll.fullScroll(View.FOCUS_DOWN));settle();
   ui(()->{visible(tagged(settings,"island_confirm_end"));textFits(tagged(settings,"island_confirm_end"));});
   snapshot("settings-"+(dark?"dark":"light")+"-bottom");
   boolean confirmed=prefs.getBoolean("island_confirm_end",true);tap(tagged(settings,"island_confirm_end"));
   await(()->prefs.getBoolean("island_confirm_end",true)!=confirmed,"Native setting switch did not persist its selection");
   tap(tagged(settings,"island_confirm_end"));await(()->prefs.getBoolean("island_confirm_end",true)==confirmed,"Native setting could not be restored");
   ui(settings::finish);settle();
  }
  settingsPassed=true;writeEvidence();
 }
 private void writeEvidence()throws Exception{
  JSONObject proof=new JSONObject();proof.put("api",android.os.Build.VERSION.SDK_INT);proof.put("real_renderer_and_controller",true);
  proof.put("matrix_native_layout_assertions",matrixPassed);proof.put("native_action_dispatch_and_acknowledgement",actionsPassed);
  proof.put("confirmed_end_cancel_and_accept",actionsPassed);proof.put("disabled_ui_preserves_session",actionsPassed);
  proof.put("content_margin_and_list_anchor_restored",actionsPassed);proof.put("pause_stops_surface_and_resume_restores",actionsPassed);
  proof.put("activity_recreation_preserves_fixture_session_and_query",actionsPassed);proof.put("customization_preview_and_bottom_controls",settingsPassed);
  proof.put("animations_lifecycle",animationsPassed);proof.put("native_preference_persistence",settingsPassed);
  proof.put("session_source","Non-exported DEBUG fixture. Fictional contacts and state; real production renderer/controller. No Telecom call or physical audio route is created.");
  proof.put("full_call_navigation","Real CallScreenActivity in explicit DEBUG preview; system Back returns to same native host and preserved list.");
  File file=new File(target.getExternalFilesDir(null),"island-native.json");try(FileOutputStream out=new FileOutputStream(file)){out.write(proof.toString(2).getBytes(StandardCharsets.UTF_8));}
  shell("cp "+file.getAbsolutePath()+" "+DIRECTORY+"/island-native.json");
 }
 private void restore(SharedPreferences settings,Map<String,?> values){
  SharedPreferences.Editor edit=settings.edit().clear();for(Map.Entry<String,?> item:values.entrySet()){
   Object value=item.getValue();String key=item.getKey();if(value instanceof String)edit.putString(key,(String)value);
   else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);else if(value instanceof Integer)edit.putInt(key,(Integer)value);
   else if(value instanceof Long)edit.putLong(key,(Long)value);else if(value instanceof Float)edit.putFloat(key,(Float)value);
   else if(value instanceof Set)edit.putStringSet(key,new HashSet<>((Set<String>)value));
  }assertTrue(edit.commit());
 }
 private String shell(String command)throws Exception{
  ParcelFileDescriptor descriptor=getInstrumentation().getUiAutomation().executeShellCommand(command);
  try(ParcelFileDescriptor.AutoCloseInputStream in=new ParcelFileDescriptor.AutoCloseInputStream(descriptor)){
   ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);return out.toString("UTF-8");
  }
 }
}
