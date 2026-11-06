package com.dacaller.app;

import android.content.Context;
import android.content.Intent;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.concurrent.atomic.AtomicReference;

/** Actual Android small-screen settings interactions with isolated debug preferences. */
@SuppressWarnings("deprecation")
public final class SettingsInteractionTest extends InstrumentationTestCase {
 private ListDispatchHostActivity host;
 @Override protected void setUp()throws Exception{
  super.setUp();
  Intent intent=new Intent(getInstrumentation().getTargetContext(),ListDispatchHostActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
  host=(ListDispatchHostActivity)getInstrumentation().startActivitySync(intent);getInstrumentation().waitForIdleSync();
  // The fixture mounts SettingsScreen directly below. Keep MainActivity's
  // logical page consistent so a pending recents/provider callback cannot
  // replace that screen while this test still holds the detached viewport.
  java.lang.reflect.Field page=MainActivity.class.getDeclaredField("page");page.setAccessible(true);
  ui(()->{try{page.set(host,"settings");}catch(IllegalAccessException failure){throw new AssertionError(failure);}});
 }
 @Override protected void tearDown()throws Exception{
  ui(()->host.finish());getInstrumentation().waitForIdleSync();super.tearDown();
 }
 private void ui(Runnable action)throws Exception{
  AtomicReference<Throwable> error=new AtomicReference<>();
  getInstrumentation().runOnMainSync(()->{try{action.run();}catch(Throwable failure){error.set(failure);}});
  if(error.get()!=null)throw new AssertionError(error.get());
 }
 public void testLastSettingsCategoryStaysInteractiveAndCallsCategoryHasNoPhoneSymbol()throws Exception{
  for(boolean dark:new boolean[]{false,true}){
   final ScrollView[] viewport=new ScrollView[1];
   final View[] header=new View[1],content=new View[1];
   ui(()->{
    host.getSharedPreferences("da_caller",Context.MODE_PRIVATE).edit().putBoolean("dark",dark)
     .putBoolean("amoled",false).putBoolean("settings_group_open_2",false)
     .putBoolean("settings_group_open_5",false).apply();
    viewport[0]=new SettingsScreen(host).create();host.setContentView(viewport[0]);
    host.getWindow().setStatusBarColor(dark?0xFF141414:0xFFF7F7F5);
    host.getWindow().setNavigationBarColor(dark?0xFF141414:0xFFF7F7F5);
    host.getWindow().getDecorView().setSystemUiVisibility(dark?0:
      View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    ViewGroup calls=(ViewGroup)find(viewport[0],"שיחות ומענה").getParent();
    assertEquals(2,calls.getChildCount());
    header[0]=(View)find(viewport[0],"הרשאות וחיבור למערכת").getParent();
    ViewGroup parent=(ViewGroup)header[0].getParent();content[0]=parent.getChildAt(parent.indexOfChild(header[0])+1);
   });
   awaitCurrentViewport(viewport[0],"settings-"+(dark?"dark":"light"));
   ui(()->assertCurrentViewport(viewport[0]));
   for(int n=0;n<6;n++){
    reveal(viewport[0],header[0]);tap(header[0]);
    ui(()->assertEquals(View.VISIBLE,content[0].getVisibility()));
    reveal(viewport[0],header[0]);tap(header[0]);
    ui(()->assertEquals(View.GONE,content[0].getVisibility()));
   }
   reveal(viewport[0],header[0]);tap(header[0]);
   ui(()->{
    assertNotNull(find(content[0],"הרשאת שיחות"));assertNotNull(find(content[0],"אבחון תקלה בחיוג"));
    SettingsScreen.refreshAttached(host);assertEquals(View.VISIBLE,content[0].getVisibility());
   });
   reveal(viewport[0],header[0]);Thread.sleep(220);getInstrumentation().waitForIdleSync();
   ui(()->assertCurrentViewport(viewport[0]));
   snapshot("settings-"+(dark?"dark":"light"));
   ui(()->assertCurrentViewport(viewport[0]));
   // fullScroll delegates to smoothScrollBy on Android 13. An idle message
   // queue does not mean its future Choreographer animation frames have run.
   ui(()->viewport[0].fullScroll(View.FOCUS_DOWN));
   awaitBottomAndDiagnostic(viewport[0],"settings-"+(dark?"dark":"light"));
   ui(()->assertCurrentViewport(viewport[0]));
   snapshot("settings-"+(dark?"dark":"light")+"-bottom");
   ui(()->assertCurrentViewport(viewport[0]));
  }
 }
 private void awaitCurrentViewport(ScrollView viewport,String screenshotPrefix)throws Exception{
  // startActivitySync and setContentView finish before Android necessarily
  // grants window focus. Wait for that real state instead of sampling it once.
  long start=SystemClock.uptimeMillis(),stableSince=-1;
  final boolean[] ready=new boolean[1];final String[] state=new String[1];
  java.util.ArrayDeque<String> samples=new java.util.ArrayDeque<>();
  while(SystemClock.uptimeMillis()-start<7000){
   getInstrumentation().waitForIdleSync();
   ui(()->{
    View decor=host.getWindow().getDecorView();
    boolean current=inCurrentContent(viewport),shown=viewport.isShown();
    boolean focused=host.hasWindowFocus()&&decor.hasWindowFocus();
    ready[0]=!host.isFinishing()&&!host.isDestroyed()&&current&&shown&&focused
      &&decor.isAttachedToWindow()&&decor.isShown()&&decor.getWindowVisibility()==View.VISIBLE;
    state[0]="current_content="+current+", viewport_attached="+viewport.isAttachedToWindow()
      +", viewport_shown="+shown+", activity_focus="+host.hasWindowFocus()
      +", decor_focus="+decor.hasWindowFocus()+", decor_attached="+decor.isAttachedToWindow()
      +", decor_shown="+decor.isShown()+", decor_visibility="+decor.getWindowVisibility()
      +", finishing="+host.isFinishing()+", destroyed="+host.isDestroyed();
   });
   long now=SystemClock.uptimeMillis();
   if(samples.size()==24)samples.removeFirst();
   samples.addLast((now-start)+"ms: "+state[0]);
   if(ready[0]){
    if(stableSince<0)stableSince=now;
    if(now-stableSince>=350)return;
   }else stableSince=-1;
   Thread.sleep(40);
  }
  StringBuilder report=new StringBuilder("Settings viewport did not become stably visible/focused.\n");
  for(String sample:samples)report.append(sample).append('\n');
  report.append("\nACTIVITIES\n").append(shell("dumpsys activity activities"));
  report.append("\nWINDOWS\n").append(shell("dumpsys window windows"));
  java.io.File evidence=new java.io.File(host.getExternalFilesDir(null),screenshotPrefix+"-not-ready.txt");
  try(java.io.FileOutputStream output=new java.io.FileOutputStream(evidence)){
   output.write(report.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }
  shell("mkdir -p /sdcard/Download/DA-CALLER-snapshots");
  shell("cp "+evidence.getAbsolutePath()+" /sdcard/Download/DA-CALLER-snapshots/"+evidence.getName());
  snapshot(screenshotPrefix+"-not-ready");
  fail("Settings viewport must be stably attached, shown and focused before interaction: "+state[0]);
 }
 private void awaitBottomAndDiagnostic(ScrollView viewport,String screenshotPrefix)throws Exception{
  final boolean[] reached=new boolean[1];
  final String[] geometry=new String[1];
  long deadline=SystemClock.uptimeMillis()+1500;
  do{
   getInstrumentation().waitForIdleSync();
   ui(()->{
    View diagnostic=(View)find(viewport,"אבחון תקלה בחיוג").getParent().getParent();
    int maximum=Math.max(0,viewport.getChildAt(0).getHeight()-viewport.getHeight()
      +viewport.getPaddingTop()+viewport.getPaddingBottom());
    android.graphics.Rect visible=new android.graphics.Rect();
    boolean shown=diagnostic.getGlobalVisibleRect(visible);
    boolean current=inCurrentContent(viewport)&&host.hasWindowFocus()&&viewport.isShown();
    reached[0]=current&&viewport.getScrollY()==maximum&&shown&&visible.height()==diagnostic.getHeight()
      &&diagnostic.getHeight()>0&&diagnostic.isClickable();
    geometry[0]="scroll="+viewport.getScrollY()+"/"+maximum+", diagnostic visible="+visible.height()
      +"/"+diagnostic.getHeight()+", viewport="+viewport.getHeight()+", current attached viewport="+current;
   });
   if(reached[0])return;
   Thread.sleep(20);
  }while(SystemClock.uptimeMillis()<deadline);
  snapshot(screenshotPrefix+"-unreachable");
  fail("Diagnostic action must be fully reachable after the scroll animation settles: "+geometry[0]);
 }
 private boolean inCurrentContent(View view){
  if(!view.isAttachedToWindow())return false;
  View content=host.findViewById(android.R.id.content);
  for(View ancestor=view;ancestor!=null;){
   if(ancestor==content)return true;
   android.view.ViewParent parent=ancestor.getParent();ancestor=parent instanceof View?(View)parent:null;
  }
  return false;
 }
 private void assertCurrentViewport(ScrollView viewport){
  assertTrue("Settings viewport must be attached to the actual current content",inCurrentContent(viewport));
  assertTrue("Settings viewport must be shown in the focused Activity",host.hasWindowFocus()&&viewport.isShown());
 }
 private void reveal(ScrollView viewport,View target)throws Exception{
  ui(()->{
   assertCurrentViewport(viewport);
   assertTrue("Settings header must belong to the current content before scrolling",inCurrentContent(target));
   // Section headers are direct children of the scroll content column.
   viewport.scrollTo(0,target.getTop());
  });getInstrumentation().waitForIdleSync();
 }
 private void tap(View target)throws Exception{
  final int[] point=new int[2];
  ui(()->{
   assertTrue("Settings touch target must belong to the shown current content",inCurrentContent(target)&&target.isShown()&&host.hasWindowFocus());
   target.getLocationOnScreen(point);point[0]+=target.getWidth()/2;point[1]+=target.getHeight()/2;
   android.graphics.Rect visible=new android.graphics.Rect();
   assertTrue("Settings touch center must be visibly reachable",target.getLocalVisibleRect(visible)
    &&visible.contains(target.getWidth()/2,target.getHeight()/2));
  });
  long at=SystemClock.uptimeMillis();
  MotionEvent down=MotionEvent.obtain(at,at,MotionEvent.ACTION_DOWN,point[0],point[1],0);
  MotionEvent up=MotionEvent.obtain(at,at+40,MotionEvent.ACTION_UP,point[0],point[1],0);
  getInstrumentation().sendPointerSync(down);getInstrumentation().sendPointerSync(up);down.recycle();up.recycle();
  getInstrumentation().waitForIdleSync();
 }
 private void snapshot(String name)throws Exception{
  String directory="/sdcard/Download/DA-CALLER-snapshots";
  String path=directory+"/"+name+".png";
  // UiAutomation executes one argv command; it does not interpret shell &&.
  shell("mkdir -p "+directory);shell("screencap -p "+path);
  String size=shell("stat -c %s "+path).trim();
  assertTrue("Missing or empty settings screenshot: "+path,Long.parseLong(size)>1000);
 }
 private String shell(String command)throws Exception{
  java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream();
  ParcelFileDescriptor fd=getInstrumentation().getUiAutomation().executeShellCommand(command);
  try(ParcelFileDescriptor.AutoCloseInputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){
   byte[] buffer=new byte[256];int read;while((read=in.read(buffer))!=-1)output.write(buffer,0,read);
  }
  return output.toString("UTF-8");
 }
 private TextView find(View node,String text){
  if(node instanceof TextView&&text.contentEquals(((TextView)node).getText()))return (TextView)node;
  if(node instanceof ViewGroup)for(int i=0;i<((ViewGroup)node).getChildCount();i++){
   TextView found=find(((ViewGroup)node).getChildAt(i),text);if(found!=null)return found;
  }
  return null;
 }
}
