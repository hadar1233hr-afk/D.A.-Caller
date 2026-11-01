package com.dacaller.app;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.role.RoleManager;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.service.notification.StatusBarNotification;
import android.telecom.Call;
import android.telecom.DisconnectCause;
import android.telecom.TelecomManager;
import android.test.InstrumentationTestCase;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;

/** Actual Android Settings/navigation and modem -> Telecom missed-call broadcasts.
 * No Call, missed-call broadcast, notification or action acknowledgement is injected.
 * The existing host modem bridge refuses physical devices and accepts one fictional number.
 */
@SuppressWarnings({"deprecation","unchecked"})
@android.annotation.TargetApi(33)
public final class FollowupIntegrationTest extends InstrumentationTestCase {
 private static final String DIRECTORY="/sdcard/Download/DA-CALLER-snapshots";
 private static final String NUMBER="0500099971",NAME="מיכל שיחה שלא נענתה";
 private static JSONObject completedChangelog,completedMissed;
 private Context target;
 private SharedPreferences settings,missedState;
 private Map<String,?> original,originalNavigation,originalMissed;
 private String originalFont;
 private int originalAccessibilityFlags;
 private volatile MainActivity main;
 private Application application;
 private final Application.ActivityLifecycleCallbacks activityCallbacks=new Application.ActivityLifecycleCallbacks(){
  public void onActivityCreated(Activity activity,android.os.Bundle state){}
  public void onActivityStarted(Activity activity){}
  public void onActivityResumed(Activity activity){if(activity instanceof MainActivity)main=(MainActivity)activity;}
  public void onActivityPaused(Activity activity){}
  public void onActivityStopped(Activity activity){}
  public void onActivitySaveInstanceState(Activity activity,android.os.Bundle state){}
  public void onActivityDestroyed(Activity activity){}
 };
 private final List<Activity> launched=new ArrayList<>();
 private Uri insertedContact;
 private boolean modemRequested;
 private long requestSequence,missedStart;
 private final Set<Long> baselineMissedIds=new HashSet<>();
 private final JSONArray modemCommands=new JSONArray();
 private final JSONArray missedCalls=new JSONArray(),missedNotifications=new JSONArray(),missedActions=new JSONArray();
 private final JSONArray changelogObservations=new JSONArray();
 private String lastAction="none";

 @Override protected void setUp()throws Exception{
  super.setUp();target=getInstrumentation().getTargetContext();
  application=(Application)target.getApplicationContext();application.registerActivityLifecycleCallbacks(activityCallbacks);
  assertEquals("Never run modem/call-log integration on a physical device","1",shell("getprop ro.kernel.qemu").trim());
  assertEquals(33,android.os.Build.VERSION.SDK_INT);
  settings=target.getSharedPreferences("da_caller",Context.MODE_PRIVATE);original=settings.getAll();
  originalNavigation=target.getSharedPreferences(CallReturnNavigation.PREFERENCES,Context.MODE_PRIVATE).getAll();
  missedState=target.getSharedPreferences(MissedCallNotifications.PREFERENCES,Context.MODE_PRIVATE);originalMissed=missedState.getAll();
  originalFont=shell("settings get system font_scale").trim();
  AccessibilityServiceInfo service=getInstrumentation().getUiAutomation().getServiceInfo();
  originalAccessibilityFlags=service.flags;
  service.flags|=AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS|AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
  getInstrumentation().getUiAutomation().setServiceInfo(service);
  shell("mkdir -p "+DIRECTORY);shell("settings put system font_scale 1.3");
  // This disposable emulator already keeps our dialer role after the existing
  // notification suite. Do not revoke a role/permission: that can kill its UID.
  shell("cmd role add-role-holder --user 0 android.app.role.DIALER "+target.getPackageName());
  await(()->target.getSystemService(RoleManager.class).isRoleHeld(RoleManager.ROLE_DIALER)
    &&target.getPackageName().equals(target.getSystemService(TelecomManager.class).getDefaultDialerPackage()),
    "Actual Telecom default dialer role is required");
  for(String permission:new String[]{Manifest.permission.READ_CONTACTS,Manifest.permission.WRITE_CONTACTS,
    Manifest.permission.READ_CALL_LOG,Manifest.permission.WRITE_CALL_LOG,Manifest.permission.READ_PHONE_STATE,
    Manifest.permission.CALL_PHONE,Manifest.permission.POST_NOTIFICATIONS})shell("pm grant "+target.getPackageName()+" "+permission);
  settings.edit().putBoolean("role_prompted",true).putBoolean("notifications_prompted",true)
   .putBoolean("voice_permission_prompted",true).putBoolean("haptics",false)
   .putString(CallArchive.PREF,CallArchive.OFF).putBoolean("island_enabled",false)
   .putBoolean("island_outside_app",false).putBoolean("dark",false).putBoolean("amoled",false)
   .putString("language","he").putString("start_page","settings").commit();
 }
 @Override protected void tearDown()throws Exception{
  try{
   shell("cmd statusbar collapse");
   if(modemRequested){try{modem("cancel");}catch(Throwable ignored){}
    // Only the disposable test's own notification ID is cleaned up. Call-log
    // records are retained; no CallLog delete is used anywhere in this test.
    target.getSystemService(NotificationManager.class).cancel(MissedCallNotifications.ID);
   }
   for(int i=launched.size()-1;i>=0;i--){Activity activity=launched.get(i);if(activity!=null&&!activity.isDestroyed())ui(activity::finish);}
   if(insertedContact!=null){target.getContentResolver().delete(insertedContact,null,null);ContactPhotos.clearCache();}
   restore(settings,original);restore(target.getSharedPreferences(CallReturnNavigation.PREFERENCES,Context.MODE_PRIVATE),originalNavigation);
   if(modemRequested)restore(missedState,originalMissed);
   AccessibilityServiceInfo service=getInstrumentation().getUiAutomation().getServiceInfo();service.flags=originalAccessibilityFlags;
   getInstrumentation().getUiAutomation().setServiceInfo(service);
   shell("settings put system font_scale "+(originalFont.matches("[0-9.]+")?originalFont:"1.0"));
  }finally{if(application!=null)application.unregisterActivityLifecycleCallbacks(activityCallbacks);super.tearDown();}
 }
 private interface Check{boolean ready()throws Exception;}
 private void await(Check check,String message)throws Exception{
  long deadline=SystemClock.uptimeMillis()+12000;
  do{getInstrumentation().waitForIdleSync();if(check.ready())return;Thread.sleep(60);}while(SystemClock.uptimeMillis()<deadline);
  fail(message);
 }
 private void ui(Runnable action)throws Exception{
  AtomicReference<Throwable> failure=new AtomicReference<>();
  getInstrumentation().runOnMainSync(()->{try{action.run();}catch(Throwable error){failure.set(error);}});
  if(failure.get()!=null)throw new AssertionError(failure.get());
 }
 private void settle()throws Exception{getInstrumentation().waitForIdleSync();Thread.sleep(220);getInstrumentation().waitForIdleSync();}
 private String shell(String command)throws Exception{
  ParcelFileDescriptor descriptor=getInstrumentation().getUiAutomation().executeShellCommand(command);
  try(java.io.InputStream input=new ParcelFileDescriptor.AutoCloseInputStream(descriptor);ByteArrayOutputStream output=new ByteArrayOutputStream()){
   byte[] buffer=new byte[4096];for(int count;(count=input.read(buffer))!=-1;)output.write(buffer,0,count);return output.toString("UTF-8");
  }
 }
 private void writeFile(String name,String value)throws Exception{
  File file=new File(target.getExternalFilesDir(null),name);
  try(FileOutputStream output=new FileOutputStream(file)){output.write(value.getBytes(StandardCharsets.UTF_8));}
  shell("cp "+file.getAbsolutePath()+" "+DIRECTORY+"/"+name);
 }
 private void modem(String operation)throws Exception{
  String id=String.valueOf(System.currentTimeMillis()*10+(requestSequence++%10));
  writeFile("external-gsm-request.json",new JSONObject().put("id",id).put("operation",operation).put("number",NUMBER).toString());
  modemRequested=true;AtomicReference<JSONObject> response=new AtomicReference<>();
  await(()->{try{JSONObject value=new JSONObject(shell("cat "+DIRECTORY+"/external-gsm-response.json"));
   if(id.equals(value.optString("id"))){response.set(value);return true;}}catch(Exception notReady){}return false;
  },"Actual emulator modem driver did not acknowledge "+operation);
  JSONObject value=response.get();modemCommands.put(value);assertEquals(0,value.getInt("returncode"));
  assertTrue(value.getString("serial").startsWith("emulator-"));if("call".equals(operation))assertTrue(value.getString("stdout").contains("OK"));
 }
 private void key(int code)throws Exception{
  long now=SystemClock.uptimeMillis();
  assertTrue(getInstrumentation().getUiAutomation().injectInputEvent(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,code,0),true));
  assertTrue(getInstrumentation().getUiAutomation().injectInputEvent(new KeyEvent(now,now+40,KeyEvent.ACTION_UP,code,0),true));settle();
 }
 private void tapPoint(int x,int y)throws Exception{
  long at=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(at,at,MotionEvent.ACTION_DOWN,x,y,0);
  MotionEvent up=MotionEvent.obtain(at,at+45,MotionEvent.ACTION_UP,x,y,0);down.setSource(InputDevice.SOURCE_TOUCHSCREEN);up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
  try{assertTrue(getInstrumentation().getUiAutomation().injectInputEvent(down,true));assertTrue(getInstrumentation().getUiAutomation().injectInputEvent(up,true));}
  finally{down.recycle();up.recycle();}settle();
 }
 private Rect viewBounds(View view)throws Exception{
  Rect result=new Rect();ui(()->{assertNotNull(view);assertTrue(view.isAttachedToWindow()&&view.isShown());
   assertTrue(view.getLocalVisibleRect(result));int[] location=new int[2];view.getLocationOnScreen(location);result.offset(location[0],location[1]);});return result;
 }
 private Rect tapView(View view)throws Exception{
  Rect bounds=viewBounds(view);assertTrue(bounds.width()>0&&bounds.height()>0);
  lastAction="view "+view.getTag()+" "+bounds.toShortString();tapPoint(bounds.centerX(),bounds.centerY());return bounds;
 }
 private boolean nativeApplicationVisible(){
  for(AccessibilityWindowInfo window:getInstrumentation().getUiAutomation().getWindows()){
   if(window.getType()!=AccessibilityWindowInfo.TYPE_APPLICATION||!window.isActive()||!window.isFocused())continue;
   AccessibilityNodeInfo root=window.getRoot();if(root!=null&&root.isVisibleToUser()&&target.getPackageName().contentEquals(String.valueOf(root.getPackageName())))return true;
  }return false;
 }
 private void ready(Activity activity)throws Exception{
  long deadline=SystemClock.uptimeMillis()+9000,stable=-1;
  while(SystemClock.uptimeMillis()<deadline){
   final boolean[] visible={false};ui(()->{View decor=activity.getWindow().getDecorView();visible[0]=!activity.isFinishing()&&!activity.isDestroyed()
    &&activity.hasWindowFocus()&&decor.hasWindowFocus()&&decor.isAttachedToWindow()&&decor.isShown()&&decor.getWindowVisibility()==View.VISIBLE;});
   long now=SystemClock.uptimeMillis();if(visible[0]&&nativeApplicationVisible()){
    if(stable<0)stable=now;if(now-stable>=350)return;
   }else stable=-1;Thread.sleep(50);
  }fail("Actual Activity/native window did not become focused: "+activity.getClass().getName());
 }
 private MainActivity startMain(String page)throws Exception{
  settings.edit().putString("start_page",page).commit();shell("cmd statusbar collapse");
  MainActivity activity=(MainActivity)getInstrumentation().startActivitySync(new Intent(target,MainActivity.class)
   // Main is singleTop. CLEAR_TOP can reuse it through onNewIntent, whereas
   // startActivitySync waits for a newly created Activity and never returns.
   // Each palette starts a fresh task on this disposable emulator; Back is
   // still exercised against the actual Main created for that palette.
   .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));launched.add(activity);main=activity;ready(activity);return activity;
 }
 private <T extends View>T find(View view,Class<T> type){
  if(type.isInstance(view))return type.cast(view);if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){
   T found=find(((ViewGroup)view).getChildAt(i),type);if(found!=null)return found;}return null;
 }
 private View tag(Activity activity,String tag){return activity.getWindow().getDecorView().findViewWithTag(tag);}
 private String text(Activity activity,String tag){View view=tag(activity,tag);assertTrue(tag+" must be a TextView",view instanceof TextView);return ((TextView)view).getText().toString();}
 private TextView findText(View view,String value){
  if(view instanceof TextView&&value.contentEquals(((TextView)view).getText()))return (TextView)view;
  if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++){
   TextView found=findText(((ViewGroup)view).getChildAt(n),value);if(found!=null)return found;
  }return null;
 }
 private void textFits(View view){
  if(!view.isShown())return;
  if(view instanceof TextView&&((TextView)view).length()>0){TextView text=(TextView)view;
   assertNotNull("Visible text was not laid out: "+text.getText(),text.getLayout());
   assertTrue("Clipped changelog text: "+text.getText(),text.getLayout().getHeight()<=text.getHeight()-text.getCompoundPaddingTop()-text.getCompoundPaddingBottom()+1);
  }
  if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)textFits(((ViewGroup)view).getChildAt(i));
 }
 private ScrollView scrollAncestor(View view){for(View at=view;at!=null;){if(at instanceof ScrollView)return (ScrollView)at;
  android.view.ViewParent parent=at.getParent();at=parent instanceof View?(View)parent:null;}return null;}
 private void scrollBottom(ScrollView scroll,View last)throws Exception{
  ui(()->scroll.fullScroll(View.FOCUS_DOWN));
  await(()->{final boolean[] reached={false};ui(()->{Rect visible=new Rect();int[] pos=new int[2];last.getLocationOnScreen(pos);
    int maximum=Math.max(0,scroll.getChildAt(0).getHeight()-scroll.getHeight()+scroll.getPaddingTop()+scroll.getPaddingBottom());
    reached[0]=scroll.isAttachedToWindow()&&last.isAttachedToWindow()&&last.isShown()&&last.getLocalVisibleRect(visible)
     &&visible.height()==last.getHeight()&&scroll.getScrollY()==maximum;});return reached[0];},"Actual bottom control/footer not fully reachable");settle();
 }
 private JSONObject snapshot(String name,boolean dark)throws Exception{
  settle();Bitmap bitmap=getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(bitmap);assertEquals(480,bitmap.getWidth());assertEquals(854,bitmap.getHeight());
  int status=0,navigation=0;
  for(int y=0;y<36;y++)for(int x=0;x<480;x++){int pixel=bitmap.getPixel(x,y),sum=Color.red(pixel)+Color.green(pixel)+Color.blue(pixel);if(dark?sum>600:sum<384)status++;}
  for(int y=782;y<854;y++)for(int x=0;x<480;x++){int pixel=bitmap.getPixel(x,y),sum=Color.red(pixel)+Color.green(pixel)+Color.blue(pixel);if(dark?sum>600:sum<384)navigation++;}
  File file=new File(target.getExternalFilesDir(null),name+".png");
  try(FileOutputStream output=new FileOutputStream(file)){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,output));}finally{bitmap.recycle();}
  shell("cp "+file.getAbsolutePath()+" "+DIRECTORY+"/"+file.getName());
  return new JSONObject().put("status",status).put("navigation",navigation);
 }
 private String page(MainActivity activity)throws Exception{java.lang.reflect.Field field=MainActivity.class.getDeclaredField("page");field.setAccessible(true);return String.valueOf(field.get(activity));}

 public void testChangelogNativeSettingsHistoryAndBack()throws Exception{
  try{changelogScenario();}catch(Throwable failure){failureEvidence(failure);if(failure instanceof Exception)throw (Exception)failure;throw (Error)failure;}
 }
 private ChangelogActivity openChangelog(boolean dark,boolean english,boolean amoled)throws Exception{
  settings.edit().putBoolean("dark",dark).putBoolean("amoled",amoled).putString("language",english?"en":"he").commit();
  startMain("settings");View entry=tag(main,"settings_changelog");assertNotNull("Real Settings footer must expose changelog",entry);
  ScrollView scroll=scrollAncestor(entry);assertNotNull(scroll);scrollBottom(scroll,entry);ready(main);
  Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(ChangelogActivity.class.getName(),null,false);
  try{Rect entryBounds=tapView(entry);Activity opened=monitor.waitForActivityWithTimeout(9000);
   assertTrue("Settings entry must open the real production Activity",opened instanceof ChangelogActivity);
   launched.add(opened);ready(opened);
   changelogObservations.put(new JSONObject().put("event","changelog-settings-open").put("palette",amoled?"amoled":dark?"dark":"light")
    .put("language",english?"en":"he").put("activity",opened.getClass().getName()).put("tag","settings_changelog")
    .put("bounds",entryBounds.toShortString()).put("input_method","native-pointer"));return (ChangelogActivity)opened;
  }finally{getInstrumentation().removeMonitor(monitor);}
 }
 private void backToSettings(ChangelogActivity activity)throws Exception{
  key(KeyEvent.KEYCODE_BACK);ready(main);assertTrue(activity.isFinishing()||activity.isDestroyed());assertEquals("settings",page(main));
  assertNotNull(tag(main,"settings_changelog"));changelogObservations.put(new JSONObject().put("event","changelog-native-back")
   .put("main_page",page(main)).put("main_focused",main.hasWindowFocus()).put("footer_entry_present",tag(main,"settings_changelog")!=null));
 }
 private void changelogScenario()throws Exception{
  List<ChangelogCatalog.Entry> catalog=ChangelogCatalog.entries();assertTrue(catalog.size()>=19);
  assertEquals(BuildConfig.VERSION_NAME,catalog.get(0).version);Set<String> unique=new HashSet<>();JSONArray versions=new JSONArray();
  for(ChangelogCatalog.Entry entry:catalog){assertTrue(unique.add(entry.version));assertFalse(entry.title(false).isEmpty());assertFalse(entry.title(true).isEmpty());
   assertFalse(entry.changes.isEmpty());for(ChangelogCatalog.Change change:entry.changes){assertFalse(change.text(false).isEmpty());assertFalse(change.text(true).isEmpty());}versions.put(entry.version);}
  String oldest=catalog.get(catalog.size()-1).version;assertEquals("0.1.0",oldest);
  for(boolean dark:new boolean[]{false,true}){
   ChangelogActivity activity=openChangelog(dark,false,false);View root=tag(activity,"changelog-root");ScrollView scroll=(ScrollView)tag(activity,"changelog-scroll");
   JSONObject opening=changelogObservations.getJSONObject(changelogObservations.length()-1);
   assertNotNull(root);assertEquals(View.LAYOUT_DIRECTION_RTL,root.getLayoutDirection());assertEquals("יומן שינויים",text(activity,"changelog-title"));
   assertTrue(text(activity,"changelog-installed-version").contains(BuildConfig.VERSION_NAME));
   JSONArray renderedVersions=new JSONArray();
   ui(()->{assertTrue(root instanceof ViewGroup);ViewGroup column=(ViewGroup)root;
    for(int n=0;n<column.getChildCount();n++){String tag=String.valueOf(column.getChildAt(n).getTag());
     if(tag.startsWith("changelog-version-"))renderedVersions.put(tag.substring("changelog-version-".length()));}
   });
   assertEquals("Actual native cards must preserve the catalog order",versions.toString(),renderedVersions.toString());
   for(ChangelogCatalog.Entry entry:catalog)assertEquals(entry.version,text(activity,"changelog-version-label-"+entry.version));
   assertEquals(View.VISIBLE,tag(activity,"changelog-details-"+BuildConfig.VERSION_NAME).getVisibility());
   assertEquals(1.3f,activity.getResources().getConfiguration().fontScale,.05f);assertEquals(240,activity.getResources().getDisplayMetrics().densityDpi);
   ui(()->textFits(root));JSONObject top=snapshot("changelog-"+(dark?"dark":"light"),dark);assertTrue(top.getInt("status")>=40&&top.getInt("navigation")>=40);
   View footer=tag(activity,"changelog-history-end");scrollBottom(scroll,footer);
   View toggle=tag(activity,"changelog-toggle-"+oldest),details=tag(activity,"changelog-details-"+oldest);assertEquals(View.GONE,details.getVisibility());
   tapView(toggle);await(()->details.getVisibility()==View.VISIBLE,"Native oldest version did not expand");scrollBottom(scroll,footer);
   ui(()->textFits(root));Rect footerBounds=viewBounds(footer);WindowInsets insets=activity.getWindow().getDecorView().getRootWindowInsets();assertNotNull(insets);
   int navigation=insets.getInsets(WindowInsets.Type.navigationBars()).bottom,status=insets.getInsets(WindowInsets.Type.statusBars()).top;
   assertEquals(72,navigation);assertEquals(36,status);assertTrue(footerBounds.bottom<=854-navigation);
   JSONObject bottom=snapshot("changelog-"+(dark?"dark":"light")+"-bottom",dark);assertTrue(bottom.getInt("status")>=40&&bottom.getInt("navigation")>=40);
   changelogObservations.put(new JSONObject().put("event","changelog-palette").put("palette",dark?"dark":"light")
    .put("activity",activity.getClass().getName()).put("root_rtl",root.getLayoutDirection()==View.LAYOUT_DIRECTION_RTL)
    .put("font_scale",activity.getResources().getConfiguration().fontScale).put("density_dpi",activity.getResources().getDisplayMetrics().densityDpi)
    .put("title",text(activity,"changelog-title")).put("installed_label",text(activity,"changelog-installed-version"))
    .put("versions_in_native_tree",renderedVersions)
    .put("current_expanded",tag(activity,"changelog-details-"+BuildConfig.VERSION_NAME).getVisibility()==View.VISIBLE)
    .put("oldest_version",oldest).put("oldest_expanded",details.getVisibility()==View.VISIBLE).put("bottom_reachable",true)
    .put("footer_bounds",footerBounds.toShortString()).put("nav_bottom_px",navigation).put("status_top_px",status)
    .put("settings_entry",new JSONObject().put("tag",opening.getString("tag")).put("bounds",opening.getString("bounds"))
     .put("input_method",opening.getString("input_method")))
    .put("bar_contrast",new JSONObject().put("top",top).put("bottom",bottom)));
   tapView(toggle);await(()->details.getVisibility()==View.GONE,"Native oldest version did not collapse");backToSettings(activity);
  }
  ChangelogActivity english=openChangelog(false,true,false);View englishRoot=tag(english,"changelog-root");
  assertEquals(View.LAYOUT_DIRECTION_LTR,englishRoot.getLayoutDirection());assertEquals("What's new",text(english,"changelog-title"));
  for(ChangelogCatalog.Entry entry:catalog)assertNotNull("Actual English version heading missing: "+entry.version,findText(englishRoot,entry.title(true)));
  for(ChangelogCatalog.Change change:catalog.get(0).changes)assertNotNull(findText(englishRoot,change.text(true)));
  ui(()->textFits(englishRoot));changelogObservations.put(new JSONObject().put("event","changelog-english").put("title",text(english,"changelog-title"))
   .put("root_rtl",false).put("current_version",BuildConfig.VERSION_NAME).put("catalog_bilingual",true));backToSettings(english);
  ChangelogActivity amoled=openChangelog(true,false,true);ScrollView black=(ScrollView)tag(amoled,"changelog-scroll");
  assertTrue(black.getBackground() instanceof ColorDrawable);int background=((ColorDrawable)black.getBackground()).getColor();assertEquals(Color.BLACK,background);
  assertEquals(Color.BLACK,amoled.getWindow().getStatusBarColor());assertEquals(Color.BLACK,amoled.getWindow().getNavigationBarColor());
  ui(()->textFits(tag(amoled,"changelog-root")));changelogObservations.put(new JSONObject().put("event","changelog-amoled").put("background_argb",background));backToSettings(amoled);
  JSONObject checks=new JSONObject();for(String flag:new String[]{"actual_settings_entry_opens_activity","catalog_newest_first_and_current_version",
   "hebrew_english_native_ui","native_light_dark_bar_contrast","large_font_rtl_and_text_layout",
   "native_expand_collapse_and_bottom_reachable","back_returns_to_settings"})checks.put(flag,true);
  completedChangelog=new JSONObject().put("checks",checks).put("current_version",BuildConfig.VERSION_NAME).put("versions",versions).put("observations",changelogObservations);
  writeCombined();
 }

 public void testMissedCallsFromActualTelecomAndNativeNotificationActions()throws Exception{
  try{missedScenario();}catch(Throwable failure){failureEvidence(failure);if(failure instanceof Exception)throw (Exception)failure;throw (Error)failure;}
 }
 private StatusBarNotification missedNotification(){for(StatusBarNotification value:target.getSystemService(NotificationManager.class).getActiveNotifications())
  if(value.getId()==MissedCallNotifications.ID&&target.getPackageName().equals(value.getPackageName()))return value;return null;}
 private JSONArray notificationKeys()throws Exception{
  String output=shell("cmd notification list");assertFalse("NotificationManager's actual list command must be supported",output.toLowerCase(java.util.Locale.ROOT).contains("unknown command"));
  JSONArray keys=new JSONArray();for(String line:output.split("\\n")){String trimmed=line.trim();if(trimmed.matches("[0-9]+\\|[A-Za-z0-9_.]+\\|.*"))keys.put(trimmed);}return keys;
 }
 private JSONArray stockKeys(JSONArray all,String stock)throws Exception{JSONArray keys=new JSONArray();for(int n=0;n<all.length();n++){
  String key=all.getString(n);if(key.contains("|"+stock+"|")||key.contains("|com.android.server.telecom|"))keys.put(key);
 }return keys;}
 private JSONArray rows()throws Exception{
  JSONArray result=new JSONArray();String[] columns={CallLog.Calls._ID,CallLog.Calls.NUMBER,CallLog.Calls.TYPE,CallLog.Calls.DATE,CallLog.Calls.DURATION,CallLog.Calls.NEW,CallLog.Calls.IS_READ};
  try(Cursor cursor=target.getContentResolver().query(CallLog.Calls.CONTENT_URI,columns,CallLog.Calls.TYPE+"=? AND "+CallLog.Calls.DATE+">=?",
    new String[]{String.valueOf(CallLog.Calls.MISSED_TYPE),String.valueOf(missedStart-2000)},CallLog.Calls.DATE+" ASC,"+CallLog.Calls._ID+" ASC")){
   assertNotNull("Read the real platform call log",cursor);while(cursor.moveToNext()){
    long id=cursor.getLong(0);String number=cursor.getString(1);if(baselineMissedIds.contains(id)||!Contacts.canonical(NUMBER).equals(Contacts.canonical(number)))continue;
    result.put(new JSONObject().put("id",id).put("number",number==null?"":number).put("type",cursor.getInt(2)).put("date",cursor.getLong(3))
     .put("duration",cursor.getLong(4)).put("new",cursor.getInt(5)).put("is_read",cursor.getInt(6)));
   }
  }return result;
 }
 private JSONObject callRow(int index)throws Exception{JSONArray values=rows();assertEquals(index,values.length());return values.getJSONObject(index-1);}
 private void insertContact()throws Exception{
  ArrayList<ContentProviderOperation> changes=new ArrayList<>();changes.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
   .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE,null).withValue(ContactsContract.RawContacts.ACCOUNT_NAME,null).build());
  changes.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI).withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID,0)
   .withValue(ContactsContract.Data.MIMETYPE,ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
   .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,NAME).build());
  changes.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI).withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID,0)
   .withValue(ContactsContract.Data.MIMETYPE,ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
   .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER,NUMBER).withValue(ContactsContract.CommonDataKinds.Phone.TYPE,ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE).build());
  ContentProviderResult[] results=target.getContentResolver().applyBatch(ContactsContract.AUTHORITY,changes);insertedContact=results[0].uri;ContactPhotos.clearCache();
 }
 private void unanswered(int index,int expectedCount)throws Exception{
  int previousEvents=missedState.getInt("platform_events",0);modem("call");
  await(()->PhoneSessionService.current!=null&&PhoneSessionService.current.getState()==Call.STATE_RINGING&&PhoneSessionService.islandSnapshot()!=null,
   "Actual incoming Telecom call did not bind");
  Call call=PhoneSessionService.current;String token=PhoneSessionService.islandSnapshot().token;
  await(()->NAME.equals(PhoneSessionService.islandSnapshot().name),"Actual inserted contact was not resolved");
  Thread.sleep(350);assertEquals(Call.STATE_RINGING,call.getState());modem("cancel");
  await(()->call.getState()==Call.STATE_DISCONNECTED&&(PhoneSessionService.current==null||PhoneSessionService.current.getState()==Call.STATE_DISCONNECTED),
   "Actual remote unanswered call did not disconnect");
  await(()->PhoneSessionService.instance==null&&PhoneSessionService.current==null,"Actual Telecom must unbind the completed InCallService");
  await(()->rows().length()==index,"Actual missed call-log row was not retained");
  await(()->missedState.getInt("platform_events",0)>previousEvents&&missedNotification()!=null
    &&missedNotification().getNotification().extras.getInt(MissedCallNotifications.EXTRA_COUNT,0)==expectedCount,
   "Actual protected Telecom missed-call broadcast did not produce the current app notification");
  assertNotNull(call.getDetails());assertEquals(DisconnectCause.MISSED,call.getDetails().getDisconnectCause().getCode());
  JSONObject row=callRow(index);assertEquals(CallLog.Calls.MISSED_TYPE,row.getInt("type"));assertEquals(0,row.getLong("duration"));
  assertEquals(1,row.getInt("new"));assertEquals(0,row.getInt("is_read"));
  missedCalls.put(new JSONObject().put("index",index).put("token",token).put("ringing_confirmed",true)
   .put("disconnect_cause",call.getDetails().getDisconnectCause().getCode()).put("call_log_row",row));
 }
 private AccessibilityNodeInfo nodeText(AccessibilityNodeInfo node,String value,int depth,int[] budget){
  if(node==null||depth>70||--budget[0]<0)return null;
  String text=String.valueOf(node.getText()),description=String.valueOf(node.getContentDescription());
  if(node.isVisibleToUser()&&(text.contains(value)||description.contains(value)))return node;
  for(int n=0;n<node.getChildCount();n++){AccessibilityNodeInfo found=nodeText(node.getChild(n),value,depth+1,budget);if(found!=null)return found;}return null;
 }
 private AccessibilityNodeInfo ownMissedRow(){
  for(AccessibilityWindowInfo window:getInstrumentation().getUiAutomation().getWindows()){
   AccessibilityNodeInfo root=window.getRoot();if(root==null||!"com.android.systemui".contentEquals(String.valueOf(root.getPackageName())))continue;
   AccessibilityNodeInfo caller=nodeText(root,NAME,0,new int[]{7000});
   for(AccessibilityNodeInfo at=caller;at!=null;at=at.getParent()){
    String id=String.valueOf(at.getViewIdResourceName()),type=String.valueOf(at.getClassName());
    if((id.endsWith("/expandableNotificationRow")||type.endsWith("ExpandableNotificationRow"))&&at.isVisibleToUser()
      &&nodeText(at,"DA CALLER",0,new int[]{1000})!=null)return at;
   }
  }return null;
 }
 private boolean expandOwn(AccessibilityNodeInfo row){
  if(row==null)return false;
  for(AccessibilityNodeInfo.AccessibilityAction action:row.getActionList())if(action.getId()==AccessibilityNodeInfo.ACTION_EXPAND&&row.performAction(AccessibilityNodeInfo.ACTION_EXPAND))return true;
  return expandArrow(row,0,new int[]{2000});
 }
 private boolean expandArrow(AccessibilityNodeInfo node,int depth,int[] budget){
  if(node==null||depth>60||--budget[0]<0)return false;String id=String.valueOf(node.getViewIdResourceName());String description=String.valueOf(node.getContentDescription());
  if(id.endsWith("/expand_button")&&!description.toLowerCase(java.util.Locale.ROOT).contains("collapse")){
   AccessibilityNodeInfo at=node;
   for(int parent=0;at!=null&&parent<3;parent++,at=at.getParent())if(at.isVisibleToUser()&&at.isClickable()&&at.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;
  }
  for(int n=0;n<node.getChildCount();n++)if(expandArrow(node.getChild(n),depth+1,budget))return true;return false;
 }
 private void missedShade()throws Exception{
  shell("cmd statusbar expand-notifications");settle();await(()->ownMissedRow()!=null,"Actual SystemUI did not show our missed caller");
  long deadline=SystemClock.uptimeMillis()+8000,next=0;
  while(SystemClock.uptimeMillis()<deadline){AccessibilityNodeInfo row=ownMissedRow();if(row!=null&&nodeText(row,"ניקוי",0,new int[]{2000})!=null)return;
   if(SystemClock.uptimeMillis()>=next){expandOwn(row);next=SystemClock.uptimeMillis()+600;}Thread.sleep(70);
  }fail("Real missed-call Clear action is not visible after expanding its own native notification");
 }
 private JSONObject noteNotification(String stage,String stock)throws Exception{
  StatusBarNotification sbn=missedNotification();assertNotNull(sbn);Notification notification=sbn.getNotification();JSONArray all=notificationKeys();boolean listed=false;
  for(int n=0;n<all.length();n++)if(sbn.getKey().equals(all.getString(n)))listed=true;assertTrue("Actual shell notification list must include the posted app key",listed);
  JSONArray other=stockKeys(all,stock);assertEquals("System dialer must not post a competing missed-call notification",0,other.length());
  assertEquals(MissedCallNotifications.CHANNEL,notification.getChannelId());assertEquals(Notification.CATEGORY_MISSED_CALL,notification.category);
  assertNotNull(notification.contentIntent);assertNotNull(notification.deleteIntent);assertNotNull(notification.actions);assertEquals(1,notification.actions.length);
  assertTrue("Native action must have an immutable PendingIntent",notification.actions[0].actionIntent.isImmutable());
  assertTrue("Direct Activity content intent prevents a notification trampoline",notification.contentIntent.isActivity());
  assertTrue("No stale active-call notification should remain after a miss",PhoneSessionService.current==null||PhoneSessionService.current.getState()==Call.STATE_DISCONNECTED);
  JSONArray ids=new JSONArray(missedState.getString("snapshot_ids","[]"));int count=notification.extras.getInt(MissedCallNotifications.EXTRA_COUNT,0);assertEquals(count,ids.length());
  JSONObject result=new JSONObject().put("stage",stage).put("package",sbn.getPackageName()).put("id",sbn.getId()).put("channel",notification.getChannelId())
   .put("category",notification.category).put("count",count).put("platform_count",notification.extras.getInt(MissedCallNotifications.EXTRA_PLATFORM_COUNT,0))
   .put("title",String.valueOf(notification.extras.getCharSequence(Notification.EXTRA_TITLE))).put("text",String.valueOf(notification.extras.getCharSequence(Notification.EXTRA_TEXT)))
   .put("name",NAME).put("snapshot_ids",ids).put("own_key",sbn.getKey()).put("all_native_notification_keys",all).put("stock_notification_keys",other)
   .put("native_caller_visible",ownMissedRow()!=null&&nodeText(ownMissedRow(),NAME,0,new int[]{2000})!=null).put("active_call_absent",PhoneSessionService.current==null)
   .put("incall_service_absent",PhoneSessionService.instance==null)
   .put("platform_events",missedState.getInt("platform_events",0));
  assertEquals(NAME,result.getString("text"));missedNotifications.put(result);return result;
 }
 private void assertReadRows(int expected)throws Exception{
  await(()->{JSONArray all=rows();if(all.length()!=expected)return false;for(int n=0;n<all.length();n++)if(all.getJSONObject(n).getInt("new")!=0||all.getJSONObject(n).getInt("is_read")!=1)return false;return true;},
   "Explicit notification acknowledgement must retain every missed row and change only its read flags");
 }
 private void missedScenario()throws Exception{
  assertEquals(PackageManager.PERMISSION_GRANTED,target.checkSelfPermission(Manifest.permission.READ_CALL_LOG));
  assertEquals(PackageManager.PERMISSION_GRANTED,target.checkSelfPermission(Manifest.permission.WRITE_CALL_LOG));
  TelecomManager telecom=target.getSystemService(TelecomManager.class);String stock=telecom.getSystemDialerPackage();
  assertNotNull(stock);assertTrue(stock.matches("[A-Za-z0-9_.]+"));assertFalse(target.getPackageName().equals(stock));
  assertNull("Fresh disposable test must not replace another pending app missed notification",missedNotification());
  JSONArray before=stockKeys(notificationKeys(),stock);assertEquals("Controlled emulator starts without stock dialer notifications",0,before.length());
  // Record existing IDs, not deleting or editing preexisting call-log rows.
  missedStart=0;JSONArray existing=rows();for(int n=0;n<existing.length();n++)baselineMissedIds.add(existing.getJSONObject(n).getLong("id"));
  missedStart=System.currentTimeMillis();insertContact();startMain("recents");key(KeyEvent.KEYCODE_HOME);
  unanswered(1,1);missedShade();JSONObject single=noteNotification("single",stock);assertEquals(1,single.getInt("count"));snapshot("missed-single-he",false);
  shell("cmd statusbar collapse");key(KeyEvent.KEYCODE_HOME);unanswered(2,2);missedShade();JSONObject multiple=noteNotification("multiple",stock);assertEquals(2,multiple.getInt("count"));snapshot("missed-multiple-he",false);
  AccessibilityNodeInfo clear=nodeText(ownMissedRow(),"ניקוי",0,new int[]{2000});assertNotNull(clear);Rect clearBounds=new Rect();clear.getBoundsInScreen(clearBounds);
  assertTrue(clear.isVisibleToUser()&&clear.isEnabled()&&!clearBounds.isEmpty());lastAction="native missed Clear "+clearBounds.toShortString();tapPoint(clearBounds.centerX(),clearBounds.centerY());
  await(()->missedNotification()==null&&missedState.getInt("count",-1)==0,"Native Clear did not acknowledge/remove the missed notification");assertReadRows(2);
  missedActions.put(new JSONObject().put("action","clear").put("input_method","native-pointer").put("bounds",clearBounds.toShortString())
   .put("acknowledged",true).put("notification_absent",missedNotification()==null).put("state_count",missedState.getInt("count",-1)).put("call_log_rows",rows()));
  shell("cmd statusbar collapse");key(KeyEvent.KEYCODE_HOME);unanswered(3,1);missedShade();JSONObject third=noteNotification("third",stock);assertEquals(1,third.getInt("count"));
  AccessibilityNodeInfo row=ownMissedRow();Rect rowBounds=new Rect();row.getBoundsInScreen(rowBounds);assertTrue(!rowBounds.isEmpty());
  lastAction="native missed content open "+rowBounds.toShortString();assertTrue("Actual native notification content must dispatch its Activity PendingIntent",row.performAction(AccessibilityNodeInfo.ACTION_CLICK));
  await(()->main!=null&&main.hasWindowFocus(),"Native content open did not resume a real MainActivity");ready(main);if(!launched.contains(main))launched.add(main);
  await(()->missedNotification()==null&&missedState.getInt("count",-1)==0,"Native content open did not acknowledge notification");
  assertEquals("recents",page(main));assertReadRows(3);
  missedActions.put(new JSONObject().put("action","open").put("input_method","native-accessibility-click").put("bounds",rowBounds.toShortString())
   .put("acknowledged",true).put("notification_absent",missedNotification()==null).put("state_count",missedState.getInt("count",-1))
   .put("main_page",page(main)).put("main_focused",main.hasWindowFocus()).put("call_log_rows",rows()));
  JSONArray after=stockKeys(notificationKeys(),stock);assertEquals(0,after.length());
  JSONObject checks=new JSONObject();for(String flag:new String[]{"real_modem_telecom_to_protected_receiver","default_dialer_owns_missed_notification",
   "actual_contact_identity","single_and_multiple_counts","notification_survives_call_service","native_open_returns_to_recents",
   "native_clear_acknowledged","call_log_rows_retained","no_new_stock_dialer_missed_notification"})checks.put(flag,true);
  completedMissed=new JSONObject().put("checks",checks).put("role",new JSONObject().put("role_held",target.getSystemService(RoleManager.class).isRoleHeld(RoleManager.ROLE_DIALER))
   .put("default_dialer",telecom.getDefaultDialerPackage()).put("system_dialer",stock)).put("modem_commands",modemCommands).put("calls",missedCalls)
   .put("notifications",missedNotifications).put("actions",missedActions).put("stock_notification_keys_before",before).put("stock_notification_keys_after",after)
   .put("stock_notification_packages",new JSONArray().put(stock).put("com.android.server.telecom"));
  writeCombined();
 }
 private void writeCombined()throws Exception{
  if(completedChangelog==null||completedMissed==null)return;
  writeFile("followup-native.json",new JSONObject().put("api",33).put("size","480x854").put("density_dpi",240).put("font_scale",1.3)
   .put("physical_cellular_audio_verified",false).put("data_origin","Production Settings and Changelog Activity; emulator GSM modem -> Android Telecom protected missed-call broadcast -> production receiver")
   .put("missed",completedMissed).put("changelog",completedChangelog).toString(2));
 }
 private void failureEvidence(Throwable failure)throws Exception{
  JSONObject evidence=new JSONObject().put("test",getName()).put("failure",String.valueOf(failure)).put("last_action",lastAction)
   .put("modem_commands",modemCommands).put("missed_calls",missedCalls).put("missed_notifications",missedNotifications).put("missed_actions",missedActions)
   .put("changelog_observations",changelogObservations).put("activities",shell("dumpsys activity activities")).put("windows",shell("dumpsys window windows"))
   .put("notifications",shell("dumpsys notification --noredact")).put("recent_logcat",shell("logcat -d -t 500"));
  writeFile("followup-failure-"+getName()+".json",evidence.toString(2));snapshot("followup-failed-"+getName(),false);
 }
 private static void restore(SharedPreferences preferences,Map<String,?> values){
  SharedPreferences.Editor edit=preferences.edit().clear();for(Map.Entry<String,?> item:values.entrySet()){
   Object value=item.getValue();String key=item.getKey();if(value instanceof String)edit.putString(key,(String)value);
   else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);else if(value instanceof Integer)edit.putInt(key,(Integer)value);
   else if(value instanceof Long)edit.putLong(key,(Long)value);else if(value instanceof Float)edit.putFloat(key,(Float)value);
   else if(value instanceof Set)edit.putStringSet(key,new HashSet<>((Set<String>)value));
  }edit.commit();
 }
}
