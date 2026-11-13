package com.dacaller.app;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.NotificationManager;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.telecom.Call;
import android.telecom.TelecomManager;
import android.test.InstrumentationTestCase;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.inspector.WindowInspector;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;

/** Additive API33 evidence for the owner's eleven new requests. Screens are real compositor
 * captures. List-only examples use the existing no-dial debug host; the island uses the
 * actual disposable emulator modem, Android Telecom and production service/window owners. */
@SuppressWarnings({"deprecation","unchecked"})
@android.annotation.TargetApi(33)
public final class ModernFeaturesIntegrationTest extends InstrumentationTestCase {
 private static final String DIRECTORY="/sdcard/Download/DA-CALLER-next-snapshots";
 private static final String BRIDGE="/sdcard/Download/DA-CALLER-snapshots";
 private static final String NUMBER="0500099971";
 private final List<Activity> activities=new ArrayList<>();
 private final JSONObject captures=new JSONObject();
 private Context target;
 private SharedPreferences prefs;
 private Map<String,?> original,originalNavigation;
 private String originalFont,originalIcon,originalOverlay,originalDialer;
 private int originalAccessibilityFlags;
 private boolean modemRequested;
 private long sequence;
 private CallHistory.Summary originalSummary;
 private List<CallHistory.Entry> originalCalls;
 private boolean originalReadFailed;private long originalFetchedAt;
 private final JSONArray modemCommands=new JSONArray();

 @Override protected void setUp()throws Exception {
  super.setUp();target=getInstrumentation().getTargetContext();
  assertEquals("Native integration is restricted to the disposable emulator","1",shell("getprop ro.kernel.qemu").trim());
  assertEquals(33,android.os.Build.VERSION.SDK_INT);assertEquals(240,target.getResources().getDisplayMetrics().densityDpi);
  prefs=target.getSharedPreferences("da_caller",Context.MODE_PRIVATE);original=prefs.getAll();originalIcon=AppIcons.selected(target);
  originalNavigation=target.getSharedPreferences(CallReturnNavigation.PREFERENCES,Context.MODE_PRIVATE).getAll();
  originalFont=shell("settings get system font_scale").trim();originalOverlay=shell("cmd appops get "+target.getPackageName()+" SYSTEM_ALERT_WINDOW");
  originalDialer=target.getSystemService(TelecomManager.class).getDefaultDialerPackage();
  originalCalls=CallHistory.entries;originalSummary=CallHistory.summary;originalReadFailed=CallHistory.readFailed;originalFetchedAt=CallHistory.fetchedAt;
  AccessibilityServiceInfo service=getInstrumentation().getUiAutomation().getServiceInfo();originalAccessibilityFlags=service.flags;
  service.flags|=AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS|AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
  getInstrumentation().getUiAutomation().setServiceInfo(service);
  shell("mkdir -p "+DIRECTORY);shell("settings put system font_scale 1.3");
 }
 @Override protected void tearDown()throws Exception {
  try {
   if(modemRequested)try{modem("cancel");}catch(Throwable ignored){}
   for(int i=activities.size()-1;i>=0;i--){Activity activity=activities.get(i);if(!activity.isDestroyed())ui(activity::finish);}
   settle();restore(prefs,original);assertTrue(AppIcons.apply(target,originalIcon));
   restore(target.getSharedPreferences(CallReturnNavigation.PREFERENCES,Context.MODE_PRIVATE),originalNavigation);
   CallHistory.entries=originalCalls;CallHistory.summary=originalSummary;CallHistory.readFailed=originalReadFailed;CallHistory.fetchedAt=originalFetchedAt;
   String mode="default";for(String candidate:new String[]{"allow","ignore","deny","default"})if(originalOverlay.contains("SYSTEM_ALERT_WINDOW: "+candidate)){mode=candidate;break;}
   shell("cmd appops set "+target.getPackageName()+" SYSTEM_ALERT_WINDOW "+mode);
   if(!target.getPackageName().equals(originalDialer)){
    shell("cmd role remove-role-holder --user 0 android.app.role.DIALER "+target.getPackageName()+" 1");
    if(originalDialer!=null&&originalDialer.matches("[A-Za-z0-9_.]+"))shell("cmd role add-role-holder --user 0 android.app.role.DIALER "+originalDialer+" 1");
   }
   shell("settings put system font_scale "+(originalFont.matches("[0-9.]+")?originalFont:"1.0"));
   AccessibilityServiceInfo service=getInstrumentation().getUiAutomation().getServiceInfo();service.flags=originalAccessibilityFlags;getInstrumentation().getUiAutomation().setServiceInfo(service);
   target.getContentResolver().delete(BackupDocumentProvider.DOCUMENT,null,null);
  }finally{super.tearDown();}
 }
 private interface Check {boolean ready()throws Exception;}
 private void await(Check condition,String message)throws Exception {
  long deadline=SystemClock.uptimeMillis()+12000;
  do{getInstrumentation().waitForIdleSync();if(condition.ready())return;Thread.sleep(60);}while(SystemClock.uptimeMillis()<deadline);
  fail(message);
 }
 private void ui(Runnable action)throws Exception {
  AtomicReference<Throwable> error=new AtomicReference<>();getInstrumentation().runOnMainSync(()->{try{action.run();}catch(Throwable failure){error.set(failure);}});
  if(error.get()!=null)throw new AssertionError(error.get());
 }
 private void settle()throws Exception {getInstrumentation().waitForIdleSync();Thread.sleep(220);getInstrumentation().waitForIdleSync();}
 private int dp(float value){return Math.round(value*target.getResources().getDisplayMetrics().density);}
 private String shell(String command)throws Exception {
  try(InputStream input=new ParcelFileDescriptor.AutoCloseInputStream(getInstrumentation().getUiAutomation().executeShellCommand(command));ByteArrayOutputStream output=new ByteArrayOutputStream()){
   byte[] bytes=new byte[4096];for(int n;(n=input.read(bytes))!=-1;)output.write(bytes,0,n);return output.toString("UTF-8");
  }
 }
 private void write(String directory,String name,String content)throws Exception {
  File file=new File(target.getExternalFilesDir(null),"modern-"+name);try(FileOutputStream out=new FileOutputStream(file)){out.write(content.getBytes(StandardCharsets.UTF_8));}
  shell("cp "+file.getAbsolutePath()+" "+directory+"/"+name);
 }
 private void proof(String name,JSONObject value)throws Exception {
  value.put("api",33).put("density_dpi",240).put("font_scale",1.3).put("captures",captures)
   .put("package",target.getPackageName()).put("version_name",BuildConfig.VERSION_NAME).put("version_code",BuildConfig.VERSION_CODE)
   .put("physical_carrier_audio_verified",false).put("matching_key_upgrade_verified",false);
  write(DIRECTORY,name,value.toString(2));
 }
 private Bitmap capture(String name)throws Exception {
  settle();Bitmap bitmap=getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(bitmap);
  assertEquals(480,bitmap.getWidth());assertEquals(854,bitmap.getHeight());
  File file=new File(target.getExternalFilesDir(null),name);java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
  try(java.security.DigestOutputStream output=new java.security.DigestOutputStream(new FileOutputStream(file),digest)){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,output));}
  StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
  captures.put(name,new JSONObject().put("sha256",hash.toString()).put("width",480).put("height",854).put("method","UiAutomation.takeScreenshot"));
  shell("cp "+file.getAbsolutePath()+" "+DIRECTORY+"/"+name);return bitmap;
 }
 private void screenshot(String name)throws Exception {Bitmap capture=capture(name);capture.recycle();}
 private <T extends Activity>T launch(Class<T> type)throws Exception {
  T activity=type.cast(getInstrumentation().startActivitySync(new Intent(target,type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));activities.add(activity);settle();return activity;
 }
 private <T extends View>T find(View root,Class<T> type){
  if(type.isInstance(root))return type.cast(root);
  if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){T result=find(((ViewGroup)root).getChildAt(i),type);if(result!=null)return result;}return null;
 }
 private TextView text(View root,String value){
  if(root instanceof TextView&&value.equals(((TextView)root).getText().toString()))return (TextView)root;
  if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){TextView result=text(((ViewGroup)root).getChildAt(i),value);if(result!=null)return result;}return null;
 }
 private View tag(Activity activity,String value){View view=activity.getWindow().getDecorView().findViewWithTag(value);assertNotNull("Missing "+value,view);return view;}
 private View windowTag(String value){for(View root:WindowInspector.getGlobalWindowViews()){View view=root.findViewWithTag(value);if(view!=null&&view.isShown())return view;}return null;}
 private Rect bounds(View view)throws Exception {
  assertNotNull("Missing actual touch target",view);Rect result=new Rect();ui(()->{
   int[] location=new int[2];view.getLocationOnScreen(location);
   // Android TextView can include its own internal RTL scroll in this API.
   result.set(location[0]-view.getScrollX(),location[1]-view.getScrollY(),location[0]-view.getScrollX()+view.getWidth(),location[1]-view.getScrollY()+view.getHeight());
   assertTrue("Target must be shown",view.isShown());assertTrue("Target outside native display "+result,new Rect(0,0,480,854).contains(result));
   AccessibilityNodeInfo node=view.createAccessibilityNodeInfo();Rect nativeBounds=new Rect();node.getBoundsInScreen(nativeBounds);assertEquals("View and native AX disagree",nativeBounds,result);
  });return result;
 }
 private void pointer(long down,int action,float x,float y)throws Exception {
  MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
  try{assertTrue("Native touchscreen injection rejected",getInstrumentation().getUiAutomation().injectInputEvent(event,true));}finally{event.recycle();}
 }
 private void tap(View view)throws Exception {Rect rect=bounds(view);long now=SystemClock.uptimeMillis();pointer(now,MotionEvent.ACTION_DOWN,rect.centerX(),rect.centerY());Thread.sleep(50);pointer(now,MotionEvent.ACTION_UP,rect.centerX(),rect.centerY());settle();}
 private void drag(View view,int dx,int dy,boolean cancel)throws Exception {
  Rect rect=bounds(view);long now=SystemClock.uptimeMillis();int x=rect.centerX(),y=rect.centerY();pointer(now,MotionEvent.ACTION_DOWN,x,y);
  for(int n=1;n<=8;n++){Thread.sleep(25);pointer(now,MotionEvent.ACTION_MOVE,x+dx*n/8f,y+dy*n/8f);}
  pointer(now,cancel?MotionEvent.ACTION_CANCEL:MotionEvent.ACTION_UP,x+dx,y+dy);settle();
 }
 private void key(int code)throws Exception {
  long now=SystemClock.uptimeMillis();assertTrue(getInstrumentation().getUiAutomation().injectInputEvent(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,code,0),true));
  assertTrue(getInstrumentation().getUiAutomation().injectInputEvent(new KeyEvent(now,now+40,KeyEvent.ACTION_UP,code,0),true));settle();
 }
 private void reveal(View view)throws Exception {
  assertNotNull(view);ui(()->view.requestRectangleOnScreen(new Rect(0,0,view.getWidth(),view.getHeight()),true));settle();
 }
 private void field(Object object,String name,Object value)throws Exception {Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);field.set(object,value);}
 private void show(MainActivity activity,String page)throws Exception {ui(()->{try{field(activity,"page",page);Method method=MainActivity.class.getDeclaredMethod("render");method.setAccessible(true);method.invoke(activity);}catch(Exception failure){throw new AssertionError(failure);}});settle();}

 public void testModernInlineNavigationNineIconsAndWineSwitchPointer()throws Exception {
  JSONArray observations=new JSONArray();
  for(boolean dark:new boolean[]{false,true}){
   String language=dark?"he":"en",suffix=dark?"dark-he":"light-en";
   ListDispatchHostActivity host=launch(ListDispatchHostActivity.class);SharedPreferences local=host.getSharedPreferences("da_caller",Context.MODE_PRIVATE);
   ArrayList<Contacts.Person> contacts=new ArrayList<>();contacts.add(new Contacts.Person(dark?"דנה לדוגמה":"Dana Example","0500099011",8100));
   ui(()->{
    local.edit().putString("language",language).putBoolean("dark",dark).putBoolean("show_stats",true).putBoolean("stats_expanded",false)
     .putBoolean("favorites_expanded",false).putStringSet("favorites",new HashSet<>(Collections.singleton("0500099011"))).putBoolean("settings_group_open_0",true).commit();
    try{field(host,"contacts",contacts);field(host,"contactsLoaded",true);field(host,"fastPages",new FastPages(host));}catch(Exception failure){throw new AssertionError(failure);}
    CallHistory.entries=Collections.singletonList(new CallHistory.Entry(991201,"0500099011",System.currentTimeMillis(),73,2));CallHistory.readFailed=false;
    CallHistory.Summary summary=new CallHistory.Summary();for(int n=0;n<4;n++){summary.calls[n]=12;summary.outgoing[n]=4;summary.incoming[n]=5;summary.missed[n]=3;summary.seconds[n]=864;}
    CallHistory.summary=summary;
   });show(host,"recents");
   assertNull("Floating dialpad removed",host.getWindow().getDecorView().findViewWithTag("floating_dial"));
   Rect settings=bounds(tag(host,"main_settings_button"));assertTrue("Settings must be physically top-left",settings.left<dp(20));assertTrue(settings.width()>=dp(48)&&settings.height()>=dp(48));
   Rect bottom=bounds(tag(host,"main_nav_keypad"));assertTrue(bottom.top>854/2);assertTrue(bottom.height()>=dp(48));
   tap(tag(host,"recent_expand"));assertNotNull(tag(host,"recent_actions"));assertTrue("Expansion must not dial",host.requestedNumbers.isEmpty());
   for(String action:new String[]{"recent_action_call","recent_sms","recent_contact","recent_details"}){Rect control=bounds(tag(host,action));assertTrue(action,control.height()>=dp(48));}
   screenshot("modern-recents-inline-"+suffix+".png");key(KeyEvent.KEYCODE_BACK);assertNull(host.getWindow().getDecorView().findViewWithTag("recent_actions"));
   tap(tag(host,"main_nav_keypad"));await(()->windowFind(EditText.class)!=null,"Bottom dialpad did not open");
   assertTrue(host.requestedNumbers.isEmpty());screenshot("modern-bottom-dialpad-"+suffix+".png");key(KeyEvent.KEYCODE_BACK);
   tap(tag(host,"main_settings_button"));View icons=tag(host,"settings_icons");reveal(icons);tap(icons);
   assertEquals("All durable icon choices retained",9,AppIcons.IDS.length);
   View last=windowTag("launcher-icon-"+AppIcons.IDS[8]);assertNotNull(last);reveal(last);
   assertNotNull(find(last,ImageView.class));bounds(last);screenshot("modern-icons-all-nine-"+suffix+".png");tap(last);
   await(()->"signal".equals(AppIcons.selected(host)),"Ninth icon choice was not persisted");
   // The old suite verifies every alias; this checks the added final picker entry itself.
   prefs.edit().putString("language",language).putBoolean("dark",dark).commit();CallIslandSettingsActivity island=launch(CallIslandSettingsActivity.class);
   View enabled=tag(island,"island_enabled");reveal(enabled);assertTrue(enabled instanceof Switch);Switch control=(Switch)enabled;boolean checked=control.isChecked();
   Rect switchBounds=bounds(control);assertTrue(switchBounds.width()>=dp(48)&&switchBounds.height()>=dp(48));
   boolean rtl=control.getLayoutDirection()==View.LAYOUT_DIRECTION_RTL;
   // A native drag from the thumb to the opposite track end (not a synthetic checked setter).
   int direction=(checked?-1:1)*(rtl?-1:1);long down=SystemClock.uptimeMillis();int start=direction>0?switchBounds.left+dp(13):switchBounds.right-dp(13);
   pointer(down,MotionEvent.ACTION_DOWN,start,switchBounds.centerY());for(int n=1;n<=10;n++){Thread.sleep(20);pointer(down,MotionEvent.ACTION_MOVE,start+direction*dp(36)*n/10f,switchBounds.centerY());}
   pointer(down,MotionEvent.ACTION_UP,start+direction*dp(36),switchBounds.centerY());settle();
   assertEquals("Native Switch drag must move the checked state",!checked,control.isChecked());assertEquals(control.isChecked(),prefs.getBoolean("island_enabled",true));
   AccessibilityNodeInfo node=control.createAccessibilityNodeInfo();assertTrue(node.isCheckable());assertEquals(control.isChecked(),node.isChecked());
   assertEquals("android.widget.Switch",String.valueOf(node.getClassName()));screenshot("modern-wine-controls-"+suffix+".png");
   observations.put(new JSONObject().put("theme",dark?"dark":"light").put("language",language).put("font_scale",host.getResources().getConfiguration().fontScale)
    .put("inline_expansion_dial_requests",host.requestedNumbers.size()).put("settings_bounds",settings.toShortString()).put("dialpad_nav_bounds",bottom.toShortString())
    .put("icon_choices",9).put("selected_last_icon","signal").put("switch_before",checked).put("switch_after",control.isChecked()).put("switch_native_pointer_drag",true)
    .put("switch_accessibility_checkable",node.isCheckable()).put("switch_bounds",switchBounds.toShortString()));
   ui(island::finish);ui(host::finish);settle();
  }
  proof("modern-presentation-native.json",new JSONObject().put("list_host","ListDispatchHostActivity: real views and pointer dispatch, records rather than places calls")
   .put("observations",observations).put("no_floating_dialpad",true).put("all_inline_actions_retained",true).put("back_collapses_without_dial",true));
 }
 private <T extends View>T windowFind(Class<T> type){for(View root:WindowInspector.getGlobalWindowViews()){T view=find(root,type);if(view!=null&&view.isShown())return view;}return null;}

 public void testSameFileArchiveBackupRequiresSeparateExportAndRestoreOptIns()throws Exception {
  prefs.edit().putString("language","en").putBoolean("dark",false).putBoolean("amoled",false).putString(CallArchive.PREF,CallArchive.OFF).commit();
  CallArchive.Backup prior=CallArchive.captureBackup(target);long stamp=System.currentTimeMillis()-86_400_000L;
  CallArchive.BackupRow added=row(912300001,"0500099021",stamp,31),conflict=row(912300002,"0500099022",stamp+1,42);
  insert(added);insert(conflict);JSONArray observations=new JSONArray();
  try {
   Activity settingsOnly=exportBackup(false,"modern-backup-options-off.png");byte[] legacy=readDocument();
   JSONObject settingsJson=new JSONObject(new String(legacy,StandardCharsets.UTF_8));assertEquals(1,settingsJson.getInt("version"));assertFalse(settingsJson.has("archive"));
   ui(settingsOnly::finish);settle();
   Activity withArchive=exportBackup(true,"modern-backup-options-on.png");byte[] bundle=readDocument();
   JSONObject archiveJson=new JSONObject(new String(bundle,StandardCharsets.UTF_8));assertEquals(2,archiveJson.getInt("version"));assertTrue(archiveJson.has("archive"));
   SettingsBackup.Snapshot parsed=SettingsBackup.decode(bundle);assertNotNull(parsed.archive);assertEquals(prior.rows.size()+2,parsed.archive.rows.size());
   assertTrue("Archive OFF must not omit stored records",contains(parsed.archive,added)&&contains(parsed.archive,conflict));
   ui(withArchive::finish);settle();
   try(CallArchive helper=new CallArchive(target)){SQLiteDatabase db=helper.getWritableDatabase();db.delete("saved","key=?",new String[]{added.key});db.execSQL("UPDATE saved SET seconds=999 WHERE key=?",new Object[]{conflict.key});}
   Activity skipped=importBackup(false,"modern-archive-restore-off.png");
   await(()->text(skipped.getWindow().getDecorView(),"Settings restored. Existing calls and contacts were preserved.")!=null,"Opt-out restore did not finish");
   CallArchive.Backup afterSkip=CallArchive.captureBackup(target);assertFalse(containsKey(afterSkip,added.key));assertEquals(999,stored(afterSkip,conflict.key).seconds);assertPreserved(prior,afterSkip);
   ui(skipped::finish);settle();
   Activity merged=importBackup(true,"modern-archive-restore-on.png");
   await(()->findStatus(merged,"Added 1 archive records"),"Explicit insert-only archive merge did not finish");
   CallArchive.Backup afterMerge=CallArchive.captureBackup(target);assertTrue(contains(afterMerge,added));assertEquals("Existing same-key row wins conflicting bytes",999,stored(afterMerge,conflict.key).seconds);
   assertPreserved(prior,afterMerge);assertFalse("Completed restore must clear the durable journal",SettingsBackup.hasPendingRestore(target));
   screenshot("modern-archive-merged.png");
   observations.put(new JSONObject().put("legacy_version",1).put("legacy_contains_archive",false).put("bundle_version",2)
    .put("export_archive_default",false).put("restore_archive_default",false).put("archive_policy_at_export","off")
    .put("stored_rows_before",prior.rows.size()+2).put("stored_rows_exported",parsed.archive.rows.size()).put("opt_out_added",0)
    .put("opt_in_added",1).put("same_identity_conflict_retained_seconds",999).put("preexisting_rows_preserved",prior.rows.size())
    .put("pending_recovery_after_success",false).put("raw_archive_uploaded",false));
   proof("modern-backup-native.json",new JSONObject().put("document_flow","Production SAF intents intercepted by test ActivityMonitor; isolated test ContentProvider, no human DocumentsUI claim")
    .put("observations",observations).put("independent_export_and_restore_opt_ins",true).put("insert_only_conflicts_preserved",true).put("settings_only_legacy_compatible",true));
  }finally {
   try(CallArchive helper=new CallArchive(target)){helper.getWritableDatabase().delete("saved","key IN (?,?)",new String[]{added.key,conflict.key});}
   assertPreserved(prior,CallArchive.captureBackup(target));
  }
 }
 private boolean findStatus(Activity activity,String prefix){return containsText(activity.getWindow().getDecorView(),prefix);}
 private boolean containsText(View root,String prefix){
  if(root instanceof TextView&&((TextView)root).getText().toString().contains(prefix))return true;
  if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)if(containsText(((ViewGroup)root).getChildAt(i),prefix))return true;return false;
 }
 private CallArchive.BackupRow row(long id,String number,long at,long seconds){return new CallArchive.BackupRow(id+"|"+at+"|2|"+number,id,number,at,seconds,2,at+1000,at+2000,0);}
 private void insert(CallArchive.BackupRow row)throws Exception {
  try(CallArchive helper=new CallArchive(target)){
   helper.getWritableDatabase().execSQL("INSERT INTO saved(key,call_id,number,at_ms,seconds,kind,last_seen_ms,missing_since_ms,seen_generation) VALUES(?,?,?,?,?,?,?,?,?)",
    new Object[]{row.key,row.callId,row.number,row.atMs,row.seconds,row.kind,row.lastSeenMs,row.missingSinceMs,row.seenGeneration});
  }
 }
 private CallArchive.BackupRow stored(CallArchive.Backup backup,String key){for(CallArchive.BackupRow row:backup.rows)if(row.key.equals(key))return row;return null;}
 private boolean containsKey(CallArchive.Backup backup,String key){return stored(backup,key)!=null;}
 private boolean contains(CallArchive.Backup backup,CallArchive.BackupRow row){CallArchive.BackupRow match=stored(backup,row.key);return match!=null&&row.same(match);}
 private void assertPreserved(CallArchive.Backup before,CallArchive.Backup after){for(CallArchive.BackupRow row:before.rows)assertTrue("Existing archive metadata must remain exact",contains(after,row));}
 private Activity exportBackup(boolean archive,String filename)throws Exception {
  IntentFilter filter=new IntentFilter(Intent.ACTION_CREATE_DOCUMENT);filter.addCategory(Intent.CATEGORY_OPENABLE);filter.addDataType("application/json");
  Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(filter,new Instrumentation.ActivityResult(Activity.RESULT_OK,new Intent().setData(BackupDocumentProvider.DOCUMENT)
   .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION)),true);
  try {
   Activity activity=getInstrumentation().startActivitySync(SettingsBackupActivity.intent(target,true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));activities.add(activity);settle();
   CheckBox check=find(activity.getWindow().getDecorView(),CheckBox.class);assertNotNull(check);assertFalse("Archive inclusion is always opt-in",check.isChecked());
   assertEquals("No SAF output before user chooses",0,monitor.getHits());if(archive)tap(check);assertEquals(archive,check.isChecked());screenshot(filename);
   TextView save=text(activity.getWindow().getDecorView(),"Choose file and save backup");assertNotNull(save);reveal(save);tap(save);
   assertEquals(1,monitor.getHits());await(()->text(activity.getWindow().getDecorView(),"Backup saved to the file you selected.")!=null,"Export did not finish");return activity;
  }finally{getInstrumentation().removeMonitor(monitor);}
 }
 private Activity importBackup(boolean archive,String filename)throws Exception {
  IntentFilter filter=new IntentFilter(Intent.ACTION_OPEN_DOCUMENT);filter.addCategory(Intent.CATEGORY_OPENABLE);filter.addDataType("application/json");
  Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(filter,new Instrumentation.ActivityResult(Activity.RESULT_OK,new Intent().setData(BackupDocumentProvider.DOCUMENT)
   .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)),true);
  try {
   Activity activity=getInstrumentation().startActivitySync(SettingsBackupActivity.intent(target,false).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));activities.add(activity);
   await(()->windowFind(CheckBox.class)!=null,"Archive restore choice missing");assertEquals(1,monitor.getHits());
   CheckBox check=windowFind(CheckBox.class);assertFalse("Importing the bundle must not implicitly import archive",check.isChecked());if(archive){reveal(check);tap(check);}assertEquals(archive,check.isChecked());
   screenshot(filename);View restore=windowButton(android.R.id.button1);assertNotNull(restore);tap(restore);return activity;
  }finally{getInstrumentation().removeMonitor(monitor);}
 }
 private View windowButton(int id){for(View root:WindowInspector.getGlobalWindowViews()){View button=root.findViewById(id);if(button!=null&&button.isShown())return button;}return null;}
 private byte[] readDocument()throws Exception {try(InputStream input=target.getContentResolver().openInputStream(BackupDocumentProvider.DOCUMENT);ByteArrayOutputStream out=new ByteArrayOutputStream()){
  byte[] buffer=new byte[4096];for(int n;(n=input.read(buffer))!=-1;)out.write(buffer,0,n);return out.toByteArray();}}

 public void testGenuineTelecomIslandDragCancelHideRestoreTopAreaAndTransparency()throws Exception {
  for(String permission:new String[]{Manifest.permission.READ_CONTACTS,Manifest.permission.READ_CALL_LOG,Manifest.permission.READ_PHONE_STATE,Manifest.permission.CALL_PHONE,Manifest.permission.POST_NOTIFICATIONS})
   shell("pm grant "+target.getPackageName()+" "+permission);
  shell("cmd role add-role-holder --user 0 android.app.role.DIALER "+target.getPackageName());
  RoleManager roles=target.getSystemService(RoleManager.class);TelecomManager telecom=target.getSystemService(TelecomManager.class);
  await(()->roles.isRoleHeld(RoleManager.ROLE_DIALER)&&target.getPackageName().equals(telecom.getDefaultDialerPackage()),"Production app must hold actual default-dialer role");
  shell("cmd appops set "+target.getPackageName()+" SYSTEM_ALERT_WINDOW allow");assertTrue(Settings.canDrawOverlays(target));
  prefs.edit().putBoolean("role_prompted",true).putBoolean("notifications_prompted",true).putBoolean("voice_permission_prompted",true)
   .putString("language","en").putBoolean("dark",false).putBoolean("amoled",false).putBoolean("haptics",false).putString(CallArchive.PREF,CallArchive.OFF)
   .putString("start_page","recents").putBoolean("island_enabled",true).putBoolean("island_outside_app",true).putBoolean("island_animations",false)
   .putBoolean("island_confirm_end",true).putString("island_style","transparent").putInt("island_background_opacity",35)
   .putString(CallIslandPosition.MODE,"below").remove(CallIslandPosition.SET).remove(CallIslandPosition.X).remove(CallIslandPosition.Y).remove("island_hidden_token").commit();
  MainActivity main=launch(MainActivity.class);await(main::hasWindowFocus,"Main did not become visible");
  JSONObject proof=new JSONObject();JSONArray gestures=new JSONArray(),surfaces=new JSONArray();
  modem("call");await(()->state(Call.STATE_RINGING)&&PhoneSessionService.islandSnapshot()!=null,"Actual modem call did not bind");
  Call call=PhoneSessionService.current;String token=PhoneSessionService.islandSnapshot().token;
  assertEquals(NUMBER,call.getDetails().getHandle().getSchemeSpecificPart());key(KeyEvent.KEYCODE_HOME);answerNative();
  await(()->external()!=null,"Actual answered call must show external island on Home");assertOwned(call,token);
  Rect before=bounds(external());drag(windowTag("island-expand"),0,dp(105),false);
  await(()->prefs.getBoolean(CallIslandPosition.SET,false),"Native drag did not save normalized position");Rect moved=bounds(external());
  assertTrue("The actual external window must move",moved.top>before.top+dp(50));assertOwned(call,token);
  int x=prefs.getInt(CallIslandPosition.X,-1),y=prefs.getInt(CallIslandPosition.Y,-1);assertTrue(x>=0&&x<=10000&&y>0&&y<=10000);
  gestures.put(new JSONObject().put("kind","completed_external_drag").put("before",before.toShortString()).put("after",moved.toShortString()).put("x",x).put("y",y).put("call_state",Call.STATE_ACTIVE));
  drag(windowTag("island-expand"),0,dp(40),true);assertEquals(x,prefs.getInt(CallIslandPosition.X,-1));assertEquals(y,prefs.getInt(CallIslandPosition.Y,-1));assertEquals(moved,bounds(external()));assertOwned(call,token);
  gestures.put(new JSONObject().put("kind","cancelled_external_drag").put("normalized_position_preserved",true).put("geometry_restored",true).put("call_state",Call.STATE_ACTIVE));
  tap(windowTag("island-expand"));await(()->windowTag("island-action-END")!=null,"External call controls missing");
  View end=windowTag("island-action-END");reveal(end);drag(end,0,dp(50),false);
  assertOwned(call,token);assertNull("Dragging over End must cancel the child before click",windowButton(android.R.id.button1));
  gestures.put(new JSONObject().put("kind","drag_started_on_end_control").put("call_state",Call.STATE_ACTIVE).put("end_confirmation_opened",false));
  screenshot("modern-island-dragged.png");View hide=windowTag("island-hide");reveal(hide);tap(hide);
  await(()->external()==null,"Hide must remove external window");assertEquals(token,prefs.getString("island_hidden_token",""));assertOwned(call,token);assertTrue(ongoingNotification());
  screenshot("modern-island-hide-home.png");
  CallIslandSettingsActivity settings=launch(CallIslandSettingsActivity.class);View restore=tag(settings,"island_restore");reveal(restore);tap(restore);
  assertFalse(prefs.contains("island_hidden_token"));key(KeyEvent.KEYCODE_HOME);await(()->external()!=null,"Settings Restore must show island again on Home");assertOwned(call,token);
  screenshot("modern-island-restore-home.png");
  prefs.edit().putString(CallIslandPosition.MODE,"status_area").remove(CallIslandPosition.SET).remove(CallIslandPosition.X).remove(CallIslandPosition.Y).commit();settle();
  Rect externalTop=bounds(external());int statusTop=statusInset(main);assertTrue("Unprivileged external overlay must remain below protected status bar",externalTop.top>=statusTop+dp(8));
  assertOwned(call,token);surfaces.put(surface("external-protected-status-fallback",externalTop,token));screenshot("modern-island-status-external.png");
  ui(settings::finish);target.startActivity(new Intent(target,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
  await(()->main.hasWindowFocus()&&main.getWindow().getDecorView().findViewWithTag("island-root")!=null&&external()==null,"Own app must use one in-app island only");
  View inapp=tag(main,"island-root");Rect inappTop=bounds(inapp);assertTrue(statusInset(main)>0);assertEquals(Color.TRANSPARENT,main.getWindow().getStatusBarColor());
  assertTrue("Supported central lane must enter own status-bar area",inappTop.top<statusInset(main));
  assertTrue("Leave native clock lane clear",inappTop.left>=dp(60));assertTrue("Leave native status icon lane clear",inappTop.right<=480-dp(60));
  assertTrue(main.getWindow().getDecorView().getRootWindowInsets().isVisible(WindowInsets.Type.statusBars()));assertOwned(call,token);
  surfaces.put(surface("inapp-supported-central-status-area",inappTop,token));screenshot("modern-island-status-inapp.png");
  prefs.edit().putString(CallIslandPosition.MODE,"below").remove(CallIslandPosition.SET).commit();settle();
  Rect below=bounds(tag(main,"island-root"));assertTrue(below.top>=statusInset(main));assertTrue("Normal host status color restored",main.getWindow().getStatusBarColor()!=Color.TRANSPARENT);
  assertOwned(call,token);surfaces.put(surface("inapp-default-below-restored",below,token));screenshot("modern-island-below-inapp.png");
  // Show an unmodified real app surface behind the same capsule. Sampling its empty
  // top padding avoids opaque caller-label backplates and proves compositor blending.
  prefs.edit().putBoolean(CallIslandPosition.SET,true).putInt(CallIslandPosition.X,5000).putInt(CallIslandPosition.Y,6000).commit();settle();
  Rect composition=bounds(tag(main,"island-root"));prefs.edit().putString("island_hidden_token",token).commit();await(()->main.getWindow().getDecorView().findViewWithTag("island-root")==null,"Composition baseline must have no island");
  Bitmap background=capture("modern-island-composition-background.png");Bitmap[] levels=new Bitmap[3];int[] opacity={0,35,100};
  try {
   for(int i=0;i<opacity.length;i++){
    prefs.edit().remove("island_hidden_token").putInt("island_background_opacity",opacity[i]).commit();
    await(()->main.getWindow().getDecorView().findViewWithTag("island-root")!=null,"Transparent island did not return");settle();
    assertEquals(composition,bounds(tag(main,"island-root")));assertEquals(1f,tag(main,"island-root").getAlpha(),0f);assertEquals(1f,tag(main,"island-caller").getAlpha(),0f);
    levels[i]=capture("modern-island-opacity-"+opacity[i]+".png");assertOwned(call,token);
   }
   JSONArray pixels=new JSONArray();
   for(int offset=-2;offset<=2;offset++){
    int px=composition.centerX()+offset*dp(2),py=composition.top+dp(3);
    int base=background.getPixel(px,py),zero=levels[0].getPixel(px,py),partial=levels[1].getPixel(px,py),opaque=levels[2].getPixel(px,py);
    assertEquals("Zero background opacity must expose exact real pixels",base,zero);int difference=0;
    for(int shift:new int[]{16,8,0}){int bg=(base>>shift)&255,fg=(opaque>>shift)&255,actual=(partial>>shift)&255;
     int expected=Math.round(fg*(89f/255f)+bg*(166f/255f));assertTrue("35% native compositor blend differs from real baseline",Math.abs(expected-actual)<=3);difference+=Math.abs(bg-fg);}
    assertTrue("Samples must distinguish an opaque capsule from the real background",difference>80);
    pixels.put(new JSONObject().put("x",px).put("y",py).put("background",base).put("opacity0",zero).put("opacity35",partial).put("opacity100",opaque));
   }
   proof.put("transparency",new JSONObject().put("sample_region",composition.toShortString()).put("background_is_real_app_compositor",true).put("view_alpha",1)
    .put("caller_alpha",1).put("background_alpha_35",89).put("rgb_tolerance",3).put("samples",pixels));
  }finally{background.recycle();for(Bitmap bitmap:levels)if(bitmap!=null)bitmap.recycle();}
  prefs.edit().putString("island_hidden_token",token).commit();modem("cancel");await(()->PhoneSessionService.current==null||PhoneSessionService.current.getState()==Call.STATE_DISCONNECTED,"First actual call did not end");
  key(KeyEvent.KEYCODE_HOME);modem("call");await(()->state(Call.STATE_RINGING)&&PhoneSessionService.islandSnapshot()!=null,"Second actual call did not bind");
  String second=PhoneSessionService.islandSnapshot().token;assertFalse("Call UUID must not be reused",token.equals(second));key(KeyEvent.KEYCODE_HOME);answerNative();
  await(()->external()!=null,"New actual call must show despite prior call's hidden token");assertEquals(token,prefs.getString("island_hidden_token",""));assertEquals(second,PhoneSessionService.islandSnapshot().token);
  screenshot("modern-island-new-call-shown.png");assertTrue(ongoingNotification());modem("cancel");await(()->external()==null&&(PhoneSessionService.current==null||PhoneSessionService.current.getState()==Call.STATE_DISCONNECTED),"Modem end must remove island");
  proof.put("role_held",roles.isRoleHeld(RoleManager.ROLE_DIALER)).put("default_dialer",telecom.getDefaultDialerPackage()).put("data_origin","Emulator GSM modem -> Android Telecom -> production PhoneSessionService; no injected Call/session")
   .put("first_token",token).put("second_token",second).put("modem_commands",modemCommands).put("gestures",gestures).put("surfaces",surfaces)
   .put("hide_preserves_call_and_notification",true).put("settings_restore_on_home",true).put("new_call_ignores_previous_hidden_token",true)
   .put("single_owned_surface",true).put("protected_status_icons_remain_visible",true).put("modem_end_removes_island",true);
  proof("modern-island-native.json",proof);
 }
 private JSONObject surface(String stage,Rect bounds,String token)throws Exception {
  int overlays=0;for(View root:WindowInspector.getGlobalWindowViews())if(root.isAttachedToWindow()&&root.getLayoutParams() instanceof WindowManager.LayoutParams&&
   ((WindowManager.LayoutParams)root.getLayoutParams()).type==WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY&&root.findViewWithTag("island-root")!=null)overlays++;
  assertTrue("At most one owned overlay",overlays<=1);
  return new JSONObject().put("stage",stage).put("bounds",bounds.toShortString()).put("token",token).put("call_state",Call.STATE_ACTIVE).put("external_overlay_count",overlays);
 }
 private int statusInset(Activity activity){WindowInsets insets=activity.getWindow().getDecorView().getRootWindowInsets();return insets==null?0:insets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top;}
 private View external(){for(View root:WindowInspector.getGlobalWindowViews())if(root.isAttachedToWindow()&&root.getLayoutParams() instanceof WindowManager.LayoutParams&&
  ((WindowManager.LayoutParams)root.getLayoutParams()).type==WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY){View island=root.findViewWithTag("island-root");if(island!=null&&island.isShown())return island;}return null;}
 private boolean state(int state){return PhoneSessionService.current!=null&&PhoneSessionService.current.getState()==state;}
 private void assertOwned(Call call,String token){assertSame(call,PhoneSessionService.current);assertEquals(Call.STATE_ACTIVE,call.getState());assertNotNull(PhoneSessionService.islandSnapshot());assertEquals(token,PhoneSessionService.islandSnapshot().token);}
 private boolean ongoingNotification(){for(StatusBarNotification item:target.getSystemService(NotificationManager.class).getActiveNotifications())if(item.getId()==1502&&
  CallNotifications.ONGOING_CHANNEL.equals(item.getNotification().getChannelId())&&item.getNotification().contentIntent!=null)return true;return false;}
 private void modem(String operation)throws Exception {
  String id=String.valueOf(System.currentTimeMillis()*10+(sequence++%10));write(BRIDGE,"external-gsm-request.json",new JSONObject().put("id",id).put("operation",operation).put("number",NUMBER).toString());
  modemRequested=true;AtomicReference<JSONObject> response=new AtomicReference<>();
  await(()->{try{JSONObject result=new JSONObject(shell("cat "+BRIDGE+"/external-gsm-response.json"));if(id.equals(result.optString("id"))){response.set(result);return true;}}catch(Exception ignored){}return false;},"Actual host modem bridge did not acknowledge "+operation);
  JSONObject result=response.get();assertEquals(0,result.getInt("returncode"));assertTrue(result.getString("serial").startsWith("emulator-"));if("call".equals(operation))assertTrue(result.getString("stdout").contains("OK"));
  modemCommands.put(result);modemRequested="call".equals(operation);
 }
 private AccessibilityNodeInfo answerNode(AccessibilityNodeInfo node){
  if(node==null)return null;String label=String.valueOf(node.getText()),description=String.valueOf(node.getContentDescription());
  if(node.isVisibleToUser()&&node.isClickable()&&node.isEnabled()&&("Answer".equalsIgnoreCase(label)||"Answer".equalsIgnoreCase(description)||"ענה".equals(label)||"ענה".equals(description)))return node;
  for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo answer=answerNode(node.getChild(i));if(answer!=null)return answer;}return null;
 }
 private AccessibilityNodeInfo nativeAnswer(){for(AccessibilityWindowInfo window:getInstrumentation().getUiAutomation().getWindows()){
  AccessibilityNodeInfo root=window.getRoot();if(root!=null&&"com.android.systemui".contentEquals(String.valueOf(root.getPackageName()))){AccessibilityNodeInfo answer=answerNode(root);if(answer!=null)return answer;}}return null;}
 private void answerNative()throws Exception {
  String configured=shell("cmd overlay lookup com.android.systemui com.android.systemui:integer/touch_acceptance_delay").trim();assertTrue(configured.matches("[0-9]+"));int delay=Integer.parseInt(configured);assertTrue(delay>=0&&delay<=3000);
  long required=Math.max(350,delay+100),started=SystemClock.uptimeMillis(),stable=-1;Rect previous=new Rect();int window=-1;AccessibilityNodeInfo answer=null;
  while(SystemClock.uptimeMillis()-started<12000){
   AccessibilityNodeInfo candidate=nativeAnswer();Rect now=new Rect();if(candidate!=null)candidate.getBoundsInScreen(now);
   if(candidate!=null&&!now.isEmpty()&&state(Call.STATE_RINGING)){
    if(stable<0||!now.equals(previous)||window!=candidate.getWindowId())stable=SystemClock.uptimeMillis();previous.set(now);window=candidate.getWindowId();
    if(SystemClock.uptimeMillis()-stable>=required){answer=candidate;break;}
   }else stable=-1;Thread.sleep(50);
  }
  assertNotNull("Real SystemUI Answer must settle beyond native touch guard",answer);assertTrue(new Rect(0,0,480,854).contains(previous));
  long down=SystemClock.uptimeMillis();pointer(down,MotionEvent.ACTION_DOWN,previous.centerX(),previous.centerY());Thread.sleep(50);pointer(down,MotionEvent.ACTION_UP,previous.centerX(),previous.centerY());
  await(()->state(Call.STATE_ACTIVE),"Native SystemUI pointer answer did not receive Telecom acknowledgement");
 }
 private static void restore(SharedPreferences preferences,Map<String,?> values){
  SharedPreferences.Editor editor=preferences.edit().clear();for(Map.Entry<String,?> item:values.entrySet()){
   Object value=item.getValue();String key=item.getKey();if(value instanceof String)editor.putString(key,(String)value);else if(value instanceof Boolean)editor.putBoolean(key,(Boolean)value);
   else if(value instanceof Integer)editor.putInt(key,(Integer)value);else if(value instanceof Long)editor.putLong(key,(Long)value);else if(value instanceof Float)editor.putFloat(key,(Float)value);
   else if(value instanceof java.util.Set)editor.putStringSet(key,new HashSet<>((java.util.Set<String>)value));
  }assertTrue(editor.commit());
 }
}
