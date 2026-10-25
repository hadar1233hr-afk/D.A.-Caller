package com.dacaller.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.ParcelFileDescriptor;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.ViewGroup;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Real Android 13 render/interaction checks using synthetic data, never live calls. */
public final class CallLayoutSnapshotTest extends InstrumentationTestCase {
 private static final String PUBLIC_DIR="/sdcard/Download/DA-CALLER-snapshots";
 private static final String[] PANELS={"normal","audio","more","keypad"};
 private SharedPreferences settings;
 private Map<String,?> originalSettings;
 private String originalFontScale;

 @Override protected void setUp() throws Exception {
  super.setUp();
  Context target=getInstrumentation().getTargetContext();
  settings=target.getSharedPreferences("da_caller",Context.MODE_PRIVATE);
  originalSettings=settings.getAll();
  originalFontScale=shell("settings get system font_scale").trim();
  shell("mkdir -p "+PUBLIC_DIR);
 }

 @Override protected void tearDown() throws Exception {
  try{
   String scale=originalFontScale!=null&&originalFontScale.matches("[0-9]+(?:\\.[0-9]+)?")
     ?originalFontScale:"1.0";
   shell("settings put system font_scale "+scale);
   SharedPreferences.Editor restore=settings.edit().clear();
   for(Map.Entry<String,?> entry:originalSettings.entrySet()){
    Object value=entry.getValue();String key=entry.getKey();
    if(value instanceof String)restore.putString(key,(String)value);
    else if(value instanceof Boolean)restore.putBoolean(key,(Boolean)value);
    else if(value instanceof Integer)restore.putInt(key,(Integer)value);
    else if(value instanceof Long)restore.putLong(key,(Long)value);
    else if(value instanceof Float)restore.putFloat(key,(Float)value);
    else if(value instanceof Set)restore.putStringSet(key,new HashSet<>((Set<String>)value));
   }
   restore.commit();
  }finally{super.tearDown();}
 }

 public void testFiveDesignsBothThemesFourPanelsAndLargeFonts() throws Exception {
  StringBuilder failures=new StringBuilder();
  for(boolean largeFont:new boolean[]{false,true}){
   shell("settings put system font_scale "+(largeFont?"1.3":"1.0"));
   Thread.sleep(350);
   for(String style:CallThemes.IDS){
    for(boolean dark:new boolean[]{false,true}){
     configure(style,dark,"he");
     for(String panel:PANELS){
      Activity activity=launch(panel,true);
      String name="call-"+style+"-"+(dark?"dark":"light")+"-"+panel+"-"+
          (largeFont?"largefont":"standard");
      try{
       snapshot(activity,name);
       assertEquals("Font scale did not apply",largeFont?1.3f:1.0f,
          activity.getResources().getConfiguration().fontScale,0.05f);
       assertEquals("Hebrew root must use RTL",View.LAYOUT_DIRECTION_RTL,
          tagged(activity,"call-root").getLayoutDirection());
       assertCoreControlsVisible(activity,panel);
      }catch(AssertionError failure){
       failures.append(name).append(": ").append(failure.getMessage()).append('\n');
      }finally{finish(activity);}
     }
    }
   }
  }
  assertEquals("Visual bounds failures (all captures retained)","",failures.toString());
 }

 public void testSequentialPanelsCloseAndNeverOverlap() throws Exception {
  shell("settings put system font_scale 1.0");
  for(String style:CallThemes.IDS){
   configure(style,true,"he");
   Activity activity=launch("normal",false);
   try{
    assertPanels(activity,"normal");
    for(String panel:new String[]{"more","audio","keypad","more","keypad","audio"}){
     click(tagged(activity,"call-action-"+panel));
     assertPanels(activity,panel);
     assertCoreControlsVisible(activity,panel);
    }
    click(tagged(activity,"call-sheet-close"));
    assertPanels(activity,"normal");
    // A system Back closes the current sheet rather than losing the call UI.
    click(tagged(activity,"call-action-keypad"));
    getInstrumentation().runOnMainSync(activity::onBackPressed);
    settle();
    assertFalse(activity.isFinishing());
    assertPanels(activity,"normal");
   }finally{finish(activity);}
  }
 }

 public void testNoBluetoothRouteWithoutConnectedDeviceAndEnglishLayout() throws Exception {
  configure("pixel",false,"en");
  Activity activity=launch("audio",false);
  try{
   assertEquals(View.LAYOUT_DIRECTION_LTR,tagged(activity,"call-root").getLayoutDirection());
   assertFalse("Synthetic Bluetooth must be opt-in",hasText(activity.getWindow().getDecorView(),"Demo headset"));
   assertFalse("Disconnected Bluetooth must not be offered",hasText(activity.getWindow().getDecorView(),"Bluetooth"));
   assertCoreControlsVisible(activity,"audio");
   snapshot(activity,"call-pixel-light-audio-english-no-bluetooth");
  }finally{finish(activity);}
 }

 private void configure(String theme,boolean dark,String language){
  assertTrue(settings.edit().putString("call_theme",theme).putString("language",language)
     .putBoolean("dark",dark).putBoolean("amoled",false)
     .putBoolean("animations",true).putBoolean("call_btn_keypad",true)
     .putBoolean("call_btn_mute",true).putBoolean("call_btn_speaker",true).commit());
 }

 private Activity launch(String panel,boolean bluetooth) throws Exception {
  Context target=getInstrumentation().getTargetContext();
  Intent intent=new Intent(target,CallScreenActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
     .putExtra("preview_ui",true).putExtra("preview_panel",panel)
     .putExtra("preview_bluetooth",bluetooth);
  Activity activity=getInstrumentation().startActivitySync(intent);
  settle();
  assertFalse("Preview unexpectedly finished",activity.isFinishing());
  return activity;
 }

 private void settle() throws Exception {
  getInstrumentation().waitForIdleSync();
  Thread.sleep(400);
  getInstrumentation().waitForIdleSync();
 }

 private void finish(Activity activity) throws Exception {
  getInstrumentation().runOnMainSync(activity::finish);
  getInstrumentation().waitForIdleSync();
 }

 private void click(View view) throws Exception {
  assertFullyVisible(view);
  getInstrumentation().runOnMainSync(()->assertTrue("Control ignored click: "+view.getTag(),view.performClick()));
  settle();
 }

 private View tagged(Activity activity,String tag){
  View view=activity.getWindow().getDecorView().findViewWithTag(tag);
  assertNotNull("Missing control "+tag,view);return view;
 }

 private void assertCoreControlsVisible(Activity activity,String panel){
  View end=tagged(activity,"call-end");assertFullyVisible(end);
  assertNotNull("Hangup must have a TalkBack label",end.getContentDescription());
  float density=activity.getResources().getDisplayMetrics().density;
  assertTrue("Hangup touch height too small",end.getHeight()>=47*density);
  for(String action:new String[]{"more","audio","mute","keypad"})
   assertFullyVisible(tagged(activity,"call-action-"+action));
  assertPanels(activity,panel);
  if(!"normal".equals(panel))assertFullyVisible(tagged(activity,"call-sheet-close"));
  if("keypad".equals(panel))for(char digit:"123456789*0#".toCharArray())
   assertFullyVisible(tagged(activity,"dtmf-"+digit));
 }

 private void assertPanels(Activity activity,String expected){
  int shown=0;
  for(String panel:new String[]{"audio","more","keypad"}){
   View sheet=activity.getWindow().getDecorView().findViewWithTag("call-sheet-"+panel);
   boolean visible=sheet!=null&&sheet.isShown();
   assertEquals("Sheet state mismatch: "+panel,expected.equals(panel),visible);
   if(visible)shown++;
  }
  assertEquals("Overlapping sheets",expected.equals("normal")?0:1,shown);
 }

 private void assertFullyVisible(View view){
  assertTrue("Hidden control "+view.getTag(),view.isShown());
  Rect visible=new Rect();
  assertTrue("Offscreen control "+view.getTag(),view.getGlobalVisibleRect(visible));
  assertTrue("Clipped width "+view.getTag()+": "+visible.width()+"/"+view.getWidth(),
     visible.width()>=view.getWidth()-2);
  assertTrue("Clipped height "+view.getTag()+": "+visible.height()+"/"+view.getHeight(),
     visible.height()>=view.getHeight()-2);
 }

 private boolean hasText(View view,String needle){
  if(view instanceof android.widget.TextView&&
     ((android.widget.TextView)view).getText().toString().contains(needle))return true;
  if(view instanceof ViewGroup){
   ViewGroup group=(ViewGroup)view;
   for(int i=0;i<group.getChildCount();i++)if(hasText(group.getChildAt(i),needle))return true;
  }
  return false;
 }

 private void snapshot(Activity activity,String name) throws Exception {
  // Preserve failures too, so a clipped or blank screen can be reviewed.
  shell("screencap -p "+PUBLIC_DIR+"/"+name+".png");
  Bitmap screenshot=getInstrumentation().getUiAutomation().takeScreenshot();
  assertNotNull("Screenshot unavailable",screenshot);
  try{
   assertEquals("CI viewport width",480,screenshot.getWidth());
   assertEquals("CI viewport height",854,screenshot.getHeight());
   Set<Integer> colors=new HashSet<>();
   for(int y=80;y<screenshot.getHeight()-60;y+=9)
    for(int x=12;x<screenshot.getWidth()-12;x+=9)colors.add(screenshot.getPixel(x,y));
   assertTrue("Blank rendering: "+name,colors.size()>12);
   File dir=new File(activity.getExternalFilesDir(null),"snapshots");
   assertTrue(dir.isDirectory()||dir.mkdirs());
   try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){
    assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG,100,out));
   }
  }finally{screenshot.recycle();}
 }

 private String shell(String command) throws Exception {
  ParcelFileDescriptor result=getInstrumentation().getUiAutomation().executeShellCommand(command);
  try(ParcelFileDescriptor.AutoCloseInputStream input=new ParcelFileDescriptor.AutoCloseInputStream(result);
      ByteArrayOutputStream output=new ByteArrayOutputStream()){
   byte[] buffer=new byte[4096];int count;
   while((count=input.read(buffer))!=-1)output.write(buffer,0,count);
   return new String(output.toByteArray(),StandardCharsets.UTF_8);
  }
 }
}
