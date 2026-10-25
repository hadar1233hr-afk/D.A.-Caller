package com.dacaller.app;

import android.Manifest;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationManager;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.ContactsContract;
import android.service.notification.StatusBarNotification;
import android.telecom.Call;
import android.test.InstrumentationTestCase;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;

/** Real SystemUI and Back events. Call state is synthetic; this suite never places a call. */
@SuppressWarnings({"deprecation","unchecked"})
public final class CallIntegrationSnapshotTest extends InstrumentationTestCase {
 private static final String DIRECTORY="/sdcard/Download/DA-CALLER-snapshots";
 private static boolean notificationsPassed,backPassed;
 private Context target;
 private SharedPreferences settings;
 private Map<String,?> original;
 private Map<String,?> originalNavigation;
 private int originalAccessibilityFlags;
 private String originalFont;
 private MainActivity main;
 private final List<Activity> launched=new ArrayList<>();
 private final List<Uri> rawContacts=new ArrayList<>();

 @Override protected void setUp()throws Exception{
  super.setUp();target=getInstrumentation().getTargetContext();settings=target.getSharedPreferences("da_caller",Context.MODE_PRIVATE);
  original=settings.getAll();originalFont=shell("settings get system font_scale").trim();
  originalNavigation=target.getSharedPreferences(CallReturnNavigation.PREFERENCES,Context.MODE_PRIVATE).getAll();
  AccessibilityServiceInfo accessibility=getInstrumentation().getUiAutomation().getServiceInfo();
  originalAccessibilityFlags=accessibility.flags;accessibility.flags|=AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
  getInstrumentation().getUiAutomation().setServiceInfo(accessibility);
  shell("mkdir -p "+DIRECTORY);shell("settings put system font_scale 1.3");
  // Disposable CI emulator grants. Revoking these mid-instrumentation would kill
  // the test process, so the emulator is discarded after the complete suite.
  shell("pm grant "+target.getPackageName()+" "+Manifest.permission.POST_NOTIFICATIONS);
  settings.edit().putBoolean("role_prompted",true).putBoolean("notifications_prompted",true)
   .putBoolean("voice_permission_prompted",true).putBoolean("haptics",false).putString(CallArchive.PREF,CallArchive.OFF)
   .putString("call_theme","pixel").putBoolean("dark",true).putBoolean("amoled",false)
   .putString("start_page","settings").putString("language","he").commit();
  main=(MainActivity)getInstrumentation().startActivitySync(new Intent(target,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  launched.add(main);settle();
 }
 @Override protected void tearDown()throws Exception{
  try{
   shell("cmd statusbar collapse");target.getSystemService(NotificationManager.class).cancel(NotificationFixtureReceiver.ID);
   for(int i=launched.size()-1;i>=0;i--){Activity activity=launched.get(i);if(activity!=null&&!activity.isDestroyed())ui(activity::finish);}
   settle();
   if(!rawContacts.isEmpty()){
    ArrayList<ContentProviderOperation> deletes=new ArrayList<>();
    for(Uri uri:rawContacts)deletes.add(ContentProviderOperation.newDelete(uri).build());
    target.getContentResolver().applyBatch(ContactsContract.AUTHORITY,deletes);ContactPhotos.clearCache();
   }
   restore(settings,original);restore(target.getSharedPreferences(CallReturnNavigation.PREFERENCES,Context.MODE_PRIVATE),originalNavigation);
   target.getSharedPreferences(NotificationFixtureReceiver.PREF,Context.MODE_PRIVATE).edit().clear().commit();
   AccessibilityServiceInfo accessibility=getInstrumentation().getUiAutomation().getServiceInfo();accessibility.flags=originalAccessibilityFlags;
   getInstrumentation().getUiAutomation().setServiceInfo(accessibility);
   shell("settings put system font_scale "+(originalFont.matches("[0-9.]+")?originalFont:"1.0"));
  }finally{super.tearDown();}
 }
 private void ui(Runnable action)throws Exception{
  AtomicReference<Throwable> failure=new AtomicReference<>();
  getInstrumentation().runOnMainSync(()->{try{action.run();}catch(Throwable error){failure.set(error);}});
  if(failure.get()!=null)throw new AssertionError(failure.get());
 }
 private void settle()throws Exception{getInstrumentation().waitForIdleSync();Thread.sleep(250);getInstrumentation().waitForIdleSync();}
 private interface Check {boolean ready();}
 private void await(Check check,String message)throws Exception{
  long deadline=SystemClock.uptimeMillis()+7000;
  do{getInstrumentation().waitForIdleSync();if(check.ready())return;Thread.sleep(40);}while(SystemClock.uptimeMillis()<deadline);
  fail(message);
 }
 private void field(String name,Object value){
  try{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);field.set(main,value);}
  catch(ReflectiveOperationException error){throw new AssertionError(error);}
 }
 private void show(String page){
  field("page",page);
  try{Method render=MainActivity.class.getDeclaredMethod("render");render.setAccessible(true);render.invoke(main);}
  catch(ReflectiveOperationException error){throw new AssertionError(error);}
 }
 private <T extends View>T find(View root,Class<T> type){
  if(type.isInstance(root))return type.cast(root);
  if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){
   T result=find(((ViewGroup)root).getChildAt(i),type);if(result!=null)return result;
  }
  return null;
 }
 private StatusBarNotification posted(){
  for(StatusBarNotification item:target.getSystemService(NotificationManager.class).getActiveNotifications())
   if(item.getId()==NotificationFixtureReceiver.ID)return item;
  return null;
 }
 private AccessibilityNodeInfo text(String label){
  AccessibilityNodeInfo root=getInstrumentation().getUiAutomation().getRootInActiveWindow();if(root==null)return null;
  List<AccessibilityNodeInfo> nodes=root.findAccessibilityNodeInfosByText(label);
  for(AccessibilityNodeInfo node:nodes){
   String value=node.getText()==null?"":node.getText().toString();
   if(node.isVisibleToUser()&&(value.equalsIgnoreCase(label)||value.contains(label)))return node;
  }
  return null;
 }
 private void clickText(String label)throws Exception{
  await(()->text(label)!=null,"Missing native notification action: "+label);
  AccessibilityNodeInfo node=text(label);
  for(AccessibilityNodeInfo at=node;at!=null;at=at.getParent())if(at.isClickable()&&at.performAction(AccessibilityNodeInfo.ACTION_CLICK)){settle();return;}
  fail("Native notification control was not clickable: "+label);
 }
 private void shade(String action)throws Exception{
  shell("cmd statusbar expand-notifications");settle();
  if(text(action)!=null)return;
  AccessibilityNodeInfo root=getInstrumentation().getUiAutomation().getRootInActiveWindow();
  expand(root);settle();
  await(()->text(action)!=null,"Native CallStyle action not visible: "+action);
 }
 private boolean expand(AccessibilityNodeInfo node){
  if(node==null)return false;
  String description=node.getContentDescription()==null?"":node.getContentDescription().toString();
  String id=node.getViewIdResourceName()==null?"":node.getViewIdResourceName();
  boolean notificationArrow=id.endsWith("/expand_button")||description.toLowerCase(java.util.Locale.ROOT).contains("expand notification");
  if(notificationArrow&&node.isClickable()&&node.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;
  for(int i=0;i<node.getChildCount();i++)if(expand(node.getChild(i)))return true;return false;
 }
 private void post(int state,String language,boolean speaker)throws Exception{
  settings.edit().putString("language",language).commit();
  String name="he".equals(language)?"דנה בדיקת התראה":"Dana Notification Test";
  ui(()->NotificationFixtureReceiver.post(target,state,name,speaker));
  await(()->posted()!=null,"Native notification was not posted");
  assertEquals(name,posted().getNotification().extras.getCharSequence(Notification.EXTRA_TITLE).toString());
  assertEquals(Notification.CATEGORY_CALL,posted().getNotification().category);
  assertTrue((posted().getNotification().flags&Notification.FLAG_ONGOING_EVENT)!=0);
 }
 private void assertNativeIdentity(String language,int state)throws Exception{
  String name="he".equals(language)?"דנה בדיקת התראה":"Dana Notification Test";
  await(()->text(name)!=null,"SystemUI did not display contact name");
  String label=CallNotifications.stateLabel(target,state);await(()->text(label)!=null,"SystemUI did not display call state: "+label);
  AccessibilityNodeInfo active=getInstrumentation().getUiAutomation().getRootInActiveWindow();
  assertNotNull(active);assertEquals("com.android.systemui",active.getPackageName().toString());
 }

 public void testNativeNotificationStatesActionsAndDirectReturn()throws Exception{
  getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_HOME);settle();
  assertFalse("Call notification must also work while the app is in the background",main.hasWindowFocus());
  post(Call.STATE_RINGING,"he",false);shade("חזרה לשיחה");assertNativeIdentity("he",Call.STATE_RINGING);
  assertEquals(3,posted().getNotification().actions.length);snapshot("notification-incoming-he");
  post(Call.STATE_DIALING,"he",false);shade("הפעל רמקול");assertNativeIdentity("he",Call.STATE_DIALING);
  assertEquals(3,posted().getNotification().actions.length);snapshot("notification-outgoing-he");
  for(String language:new String[]{"he","en"}){
   post(Call.STATE_ACTIVE,language,false);
   String on="he".equals(language)?"הפעל רמקול":"Speaker on",off="he".equals(language)?"כבה רמקול":"Speaker off";
   shade(on);assertNativeIdentity(language,Call.STATE_ACTIVE);snapshot("notification-active-private-"+language);
   clickText(on);await(()->NotificationFixtureReceiver.state(target).getBoolean("speaker",false),"Native speaker-on action was not dispatched");
   shade(off);assertNativeIdentity(language,Call.STATE_ACTIVE);snapshot("notification-active-speaker-"+language);
   clickText(off);await(()->!NotificationFixtureReceiver.state(target).getBoolean("speaker",true),"Native speaker-off action was not dispatched");
   assertEquals(2,NotificationFixtureReceiver.state(target).getInt("commands",0));
  }
  shade("Return to call");
  Instrumentation.ActivityMonitor callMonitor=getInstrumentation().addMonitor(CallScreenActivity.class.getName(),null,false);
  try{
   clickText("Return to call");Activity call=callMonitor.waitForActivityWithTimeout(7000);assertNotNull("Return must directly open call Activity",call);
   launched.add(call);settle();assertFalse(call.isFinishing());assertNotNull(call.getWindow().getDecorView().findViewWithTag("call-root"));
   assertTrue(call.hasWindowFocus());
   ui(call::finish);settle();
  }finally{getInstrumentation().removeMonitor(callMonitor);}
  shade("Speaker on");
  String hangup=null;
  for(Notification.Action action:posted().getNotification().actions){
   if(action.title!=null&&action.title.toString().toLowerCase(java.util.Locale.ROOT).contains("hang")){hangup=action.title.toString();break;}
  }
  assertNotNull("Native CallStyle hang-up action missing",hangup);clickText(hangup);
  await(()->posted()==null,"Hang-up action did not remove ended notification");
  assertEquals("HANGUP",NotificationFixtureReceiver.state(target).getString("last_action",""));
  notificationsPassed=true;writeEvidence();
 }

 private void seedContacts()throws Exception{
  shell("pm grant "+target.getPackageName()+" "+Manifest.permission.READ_CONTACTS);
  shell("pm grant "+target.getPackageName()+" "+Manifest.permission.WRITE_CONTACTS);
  ArrayList<ContentProviderOperation> operations=new ArrayList<>();
  for(int i=0;i<70;i++){
   int back=operations.size();
   operations.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE,null).withValue(ContactsContract.RawContacts.ACCOUNT_NAME,null).build());
   operations.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI).withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID,back)
    .withValue(ContactsContract.Data.MIMETYPE,ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,String.format(java.util.Locale.ROOT,"דנה בדיקת חזרה %02d",i)).build());
   operations.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI).withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID,back)
    .withValue(ContactsContract.Data.MIMETYPE,ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER,String.format(java.util.Locale.ROOT,"0500088%03d",i)).build());
  }
  ContentProviderResult[] inserted=target.getContentResolver().applyBatch(ContactsContract.AUTHORITY,operations);
  for(int i=0;i<inserted.length;i+=3)rawContacts.add(inserted[i].uri);
  ui(main::reloadContacts);settle();
 }
 private Activity openCall(String panel)throws Exception{
  Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(CallScreenActivity.class.getName(),null,false);
  try{
   ui(()->main.startActivity(new Intent(main,CallScreenActivity.class).putExtra("preview_ui",true).putExtra("preview_panel",panel)));
   Activity activity=monitor.waitForActivityWithTimeout(7000);assertNotNull(activity);launched.add(activity);settle();return activity;
  }finally{getInstrumentation().removeMonitor(monitor);}
 }
 public void testSystemBackReturnsToLastContactQueryAnchorAndSettingsScroll()throws Exception{
  seedContacts();ui(()->show("contacts"));settle();
  await(()->{ListView list=find(main.getWindow().getDecorView(),ListView.class);return list!=null&&list.getAdapter().getCount()>=70;},"Contacts provider fixture not loaded");
  ui(()->find(main.getWindow().getDecorView(),EditText.class).setText("דנה בדיקת חזרה"));settle();
  ui(()->find(main.getWindow().getDecorView(),ListView.class).setSelectionFromTop(28,-9));settle();
  final long[] identity={-1};final int[] offset={0};
  ui(()->{ListView list=find(main.getWindow().getDecorView(),ListView.class);identity[0]=list.getAdapter().getItemId(list.getFirstVisiblePosition());offset[0]=list.getChildAt(0).getTop();});
  for(String panel:new String[]{"normal","audio","more","keypad"}){
   Activity call=openCall(panel);getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
   await(()->main.hasWindowFocus(),"Back from "+panel+" went outside DA CALLER");settle();
   ui(()->{
    assertTrue(call.isFinishing()||call.isDestroyed());assertFalse(main.isFinishing());
    assertEquals("דנה בדיקת חזרה",find(main.getWindow().getDecorView(),EditText.class).getText().toString());
    ListView list=find(main.getWindow().getDecorView(),ListView.class);
    assertEquals(identity[0],list.getAdapter().getItemId(list.getFirstVisiblePosition()));assertEquals(offset[0],list.getChildAt(0).getTop());
   });
  }
  snapshot("return-contacts");
  ui(()->{settings.edit().putBoolean("settings_group_open_0",true).commit();show("settings");});settle();
  ScrollView before=find(main.getWindow().getDecorView(),ScrollView.class);ui(()->before.scrollTo(0,180));settle();int y=before.getScrollY();assertTrue(y>0);
  Activity call=openCall("keypad");getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
  await(()->main.hasWindowFocus(),"Back did not return to DA CALLER settings");settle();
  ui(()->{
   assertTrue(call.isFinishing()||call.isDestroyed());ScrollView after=find(main.getWindow().getDecorView(),ScrollView.class);
   assertSame("Same-task return should preserve the actual settings page",before,after);assertEquals(y,after.getScrollY());
  });snapshot("return-settings");backPassed=true;writeEvidence();
 }
 private void snapshot(String name)throws Exception{
  settle();String file=DIRECTORY+"/call-integration-"+name+".png";shell("screencap -p "+file);
  assertEquals("regular file",shell("stat -c %F "+file).trim());assertTrue(Long.parseLong(shell("stat -c %s "+file).trim())>1000);
 }
 private void writeEvidence()throws Exception{
  JSONObject evidence=new JSONObject();evidence.put("api",android.os.Build.VERSION.SDK_INT);evidence.put("real_systemui",notificationsPassed);
  evidence.put("notification_actions_acknowledged",notificationsPassed);evidence.put("native_back_returns_to_app",backPassed);
  evidence.put("call_state_source","debug-only synthetic snapshots, shared production CallNotifications builder");
  evidence.put("telecom_commands","Unit-tested separately; no physical carrier/SIM assertion");
  evidence.put("native_back_panels","normal/audio/more/keypad; contact query, row identity/offset and settings scroll");
  File file=new File(target.getExternalFilesDir(null),"call-integration-native.json");
  try(FileOutputStream out=new FileOutputStream(file)){out.write(evidence.toString(2).getBytes(StandardCharsets.UTF_8));}
  shell("cp "+file.getAbsolutePath()+" "+DIRECTORY+"/call-integration-native.json");
 }
 private void restore(SharedPreferences prefs,Map<String,?> values){
  SharedPreferences.Editor editor=prefs.edit().clear();
  for(Map.Entry<String,?> item:values.entrySet()){
   Object value=item.getValue();String key=item.getKey();
   if(value instanceof String)editor.putString(key,(String)value);else if(value instanceof Boolean)editor.putBoolean(key,(Boolean)value);
   else if(value instanceof Integer)editor.putInt(key,(Integer)value);else if(value instanceof Long)editor.putLong(key,(Long)value);
   else if(value instanceof Float)editor.putFloat(key,(Float)value);else if(value instanceof Set)editor.putStringSet(key,new HashSet<>((Set<String>)value));
  }
  assertTrue(editor.commit());
 }
 private String shell(String command)throws Exception{
  ParcelFileDescriptor descriptor=getInstrumentation().getUiAutomation().executeShellCommand(command);
  try(ParcelFileDescriptor.AutoCloseInputStream in=new ParcelFileDescriptor.AutoCloseInputStream(descriptor)){
   ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int read;
   while((read=in.read(buffer))!=-1)bytes.write(buffer,0,read);return bytes.toString("UTF-8");
  }
 }
}
