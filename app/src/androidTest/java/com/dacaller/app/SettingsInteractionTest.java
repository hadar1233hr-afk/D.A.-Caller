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
   getInstrumentation().waitForIdleSync();
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
   snapshot("settings-"+(dark?"dark":"light"));
   // fullScroll delegates to smoothScrollBy on Android 13. An idle message
   // queue does not mean its future Choreographer animation frames have run.
   ui(()->viewport[0].fullScroll(View.FOCUS_DOWN));
   awaitBottomAndDiagnostic(viewport[0],"settings-"+(dark?"dark":"light"));
   snapshot("settings-"+(dark?"dark":"light")+"-bottom");
  }
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
    reached[0]=viewport.getScrollY()==maximum&&shown&&visible.height()==diagnostic.getHeight()
      &&diagnostic.getHeight()>0&&diagnostic.isClickable();
    geometry[0]="scroll="+viewport.getScrollY()+"/"+maximum+", diagnostic visible="+visible.height()
      +"/"+diagnostic.getHeight()+", viewport="+viewport.getHeight();
   });
   if(reached[0])return;
   Thread.sleep(20);
  }while(SystemClock.uptimeMillis()<deadline);
  snapshot(screenshotPrefix+"-unreachable");
  fail("Diagnostic action must be fully reachable after the scroll animation settles: "+geometry[0]);
 }
 private void reveal(ScrollView viewport,View target)throws Exception{
  ui(()->{
   // Section headers are direct children of the scroll content column.
   viewport.scrollTo(0,target.getTop());
  });getInstrumentation().waitForIdleSync();
 }
 private void tap(View target)throws Exception{
  final int[] point=new int[2];
  ui(()->{target.getLocationOnScreen(point);point[0]+=target.getWidth()/2;point[1]+=target.getHeight()/2;});
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
