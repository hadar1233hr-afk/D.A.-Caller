package com.dacaller.app;

import android.Manifest;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.role.RoleManager;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
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
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.StringWriter;
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
import org.xmlpull.v1.XmlSerializer;

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
  originalAccessibilityFlags=accessibility.flags;accessibility.flags|=AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
    |AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
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
   shell("cmd statusbar collapse");ui(()->DebugNotificationFixtureService.stop(target));
   await(()->posted()==null,"Native foreground fixture notification was not cleaned up");
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
  return text(systemUiRoot(),label);
 }
 private AccessibilityNodeInfo text(AccessibilityNodeInfo root,String label){
  if(root==null||label==null||label.isEmpty())return null;
  // Android 13's native CallStyle RemoteViews expose the exact caller and
  // action text in their accessibility nodes, but the framework's indexed
  // findAccessibilityNodeInfosByText query can return no results for that same
  // subtree. Walk the actual visible nodes: the failure XML from the device
  // demonstrated full text on conversation_text/action0 in an expanded row.
  return text(root,label,0,new int[]{5000});
 }
 private AccessibilityNodeInfo text(AccessibilityNodeInfo node,String label,int depth,int[] budget){
  if(node==null||depth>80||--budget[0]<0)return null;
  CharSequence content=node.getText();String value=content==null?"":content.toString();
  if(node.isVisibleToUser()&&(value.equalsIgnoreCase(label)||value.contains(label)))return node;
  for(int i=0;i<node.getChildCount();i++){
   AccessibilityNodeInfo found=text(node.getChild(i),label,depth+1,budget);if(found!=null)return found;
  }
  return null;
 }
 private AccessibilityNodeInfo systemUiRoot(){
  AccessibilityNodeInfo active=getInstrumentation().getUiAutomation().getRootInActiveWindow();
  String caller=NotificationFixtureReceiver.state(target).getString("name","");
  if(isSystemUi(active)&&text(active,caller)!=null)return active;
  for(AccessibilityWindowInfo window:getInstrumentation().getUiAutomation().getWindows()){
   AccessibilityNodeInfo root=window.getRoot();
   if(isSystemUi(root)&&text(root,caller)!=null)return root;
  }
  return isSystemUi(active)?active:null;
 }
 private boolean isSystemUi(AccessibilityNodeInfo root){
  return root!=null&&root.getPackageName()!=null&&"com.android.systemui".contentEquals(root.getPackageName());
 }
 private void clickText(String label)throws Exception{
  await(()->text(label)!=null,"Missing native notification action: "+label);
  AccessibilityNodeInfo node=text(label);
  for(AccessibilityNodeInfo at=node;at!=null;at=at.getParent())if(at.isClickable()&&at.performAction(AccessibilityNodeInfo.ACTION_CLICK)){settle();return;}
  fail("Native notification control was not clickable: "+label);
 }
 private void shade(String action)throws Exception{
  shell("cmd statusbar expand-notifications");settle();
  String caller=NotificationFixtureReceiver.state(target).getString("name","");
  // SystemUI inflates notification RemoteViews asynchronously. First locate this
  // particular call; expanding a different row or Quick Settings proves nothing.
  await(()->text(caller)!=null,"SystemUI did not expose the posted caller before expansion: "+caller);
  long deadline=SystemClock.uptimeMillis()+7000,nextAttempt=0;boolean arrowClicked=false;
  while(SystemClock.uptimeMillis()<deadline){
   if(text(action)!=null)return;
   if(SystemClock.uptimeMillis()>=nextAttempt){
    AccessibilityNodeInfo root=systemUiRoot(),identity=text(root,caller);
    boolean requested=expandAncestor(identity);
    if(!requested&&!arrowClicked)arrowClicked=expandOwnArrow(identity);
    nextAttempt=SystemClock.uptimeMillis()+500;
   }
   getInstrumentation().waitForIdleSync();Thread.sleep(80);
  }
  fail("Native CallStyle action not visible after expanding its notification: "+action);
 }
 private boolean expandAncestor(AccessibilityNodeInfo caller){
  // ExpandableNotificationRow publishes ACTION_EXPAND. The arrow ImageView is
  // often not independently clickable or important for accessibility on API 33.
  AccessibilityNodeInfo node=caller;
  for(int depth=0;node!=null&&depth<20;depth++,node=node.getParent()){
   if(notificationStack(node))break;
   for(AccessibilityNodeInfo.AccessibilityAction action:node.getActionList())
    if(action.getId()==AccessibilityNodeInfo.ACTION_EXPAND&&node.performAction(AccessibilityNodeInfo.ACTION_EXPAND))return true;
  }
  return false;
 }
 private boolean expandOwnArrow(AccessibilityNodeInfo caller){
  AccessibilityNodeInfo node=caller;
  for(int depth=0;node!=null&&depth<20;depth++,node=node.getParent()){
   if(notificationStack(node))break;
   if(expandArrow(node))return true;
  }
  return false;
 }
 private boolean notificationStack(AccessibilityNodeInfo node){
  String id=String.valueOf(node.getViewIdResourceName()).toLowerCase(java.util.Locale.ROOT);
  String type=String.valueOf(node.getClassName()).toLowerCase(java.util.Locale.ROOT);
  return id.contains("notification_stack_scroller")||type.contains("notificationstackscrolllayout");
 }
 private boolean expandArrow(AccessibilityNodeInfo node){
  if(node==null)return false;
  String description=node.getContentDescription()==null?"":node.getContentDescription().toString();
  String id=node.getViewIdResourceName()==null?"":node.getViewIdResourceName();
  boolean notificationArrow=id.endsWith("/expand_button")||description.toLowerCase(java.util.Locale.ROOT).contains("expand notification");
  if(notificationArrow&&!description.toLowerCase(java.util.Locale.ROOT).contains("collapse")){
   // Only the exact native arrow and its bounded container chain may be
   // clicked. Clicking the caller/content row would open the app instead.
   AccessibilityNodeInfo target=node;
   for(int depth=0;target!=null&&depth<3;depth++,target=target.getParent())
    if(target.isVisibleToUser()&&target.isClickable()&&target.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;
  }
  for(int i=0;i<node.getChildCount();i++)if(expandArrow(node.getChild(i)))return true;return false;
 }
 private void post(int state,String language,boolean speaker)throws Exception{
  settings.edit().putString("language",language).commit();
  String name="he".equals(language)?"דנה בדיקת התראה":"Dana Notification Test";
  String label=CallNotifications.stateLabel(target,state);
  ui(()->NotificationFixtureReceiver.post(target,state,name,speaker));
  await(()->{
   StatusBarNotification current=posted();if(current==null)return false;
   Notification notification=current.getNotification();
   CharSequence title=notification.extras.getCharSequence(Notification.EXTRA_TITLE);
   CharSequence phase=notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT);
   return title!=null&&name.contentEquals(title)&&phase!=null&&label.contentEquals(phase)
     &&(notification.flags&Notification.FLAG_FOREGROUND_SERVICE)!=0;
  },"Native foreground notification did not acknowledge the new identity and state");
  assertEquals(name,posted().getNotification().extras.getCharSequence(Notification.EXTRA_TITLE).toString());
  assertEquals(Notification.CATEGORY_CALL,posted().getNotification().category);
  assertTrue((posted().getNotification().flags&Notification.FLAG_ONGOING_EVENT)!=0);
  assertTrue("Quiet native CallStyle must be posted by a real foreground service",
    (posted().getNotification().flags&Notification.FLAG_FOREGROUND_SERVICE)!=0);
  assertTrue(NotificationFixtureReceiver.state(target).getBoolean("foreground_started",false));
  assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL,NotificationFixtureReceiver.state(target).getInt("foreground_type",0));
 }
 private void assertNativeIdentity(String language,int state)throws Exception{
  String name="he".equals(language)?"דנה בדיקת התראה":"Dana Notification Test";
  await(()->text(name)!=null,"SystemUI did not display contact name");
  String label=CallNotifications.stateLabel(target,state);await(()->text(label)!=null,"SystemUI did not display call state: "+label);
  AccessibilityNodeInfo active=systemUiRoot();
  assertNotNull(active);assertEquals("com.android.systemui",active.getPackageName().toString());
 }

 public void testNativeNotificationStatesActionsAndDirectReturn()throws Exception{
  try{
  // The disposable emulator grants the legitimate phoneCall FGS qualifier. The
  // role is retained until the emulator is discarded: revocation can terminate
  // the instrumentation UID and would invalidate later test cases.
  shell("cmd role add-role-holder --user 0 android.app.role.DIALER "+target.getPackageName());
  await(()->target.getSystemService(RoleManager.class).isRoleHeld(RoleManager.ROLE_DIALER),"Disposable emulator did not grant dialer role");
  assertTrue("Initial FGS start must happen while MainActivity is in the foreground",main.hasWindowFocus());
  post(Call.STATE_RINGING,"he",false);
  getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_HOME);settle();
  assertFalse("Call notification must also work while the app is in the background",main.hasWindowFocus());
  shade("חזרה לשיחה");assertNativeIdentity("he",Call.STATE_RINGING);
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
  }catch(Throwable failure){
   try{notificationFailureDiagnostics(failure);}catch(Exception diagnostic){failure.addSuppressed(diagnostic);}
   if(failure instanceof Exception)throw (Exception)failure;
   if(failure instanceof Error)throw (Error)failure;
   throw new AssertionError(failure);
  }
 }

 private void notificationFailureDiagnostics(Throwable failure)throws Exception{
  shell("screencap -p "+DIRECTORY+"/call-integration-failed-notification-shade.png");
  StringWriter xml=new StringWriter();XmlSerializer serializer=android.util.Xml.newSerializer();
  serializer.setOutput(xml);serializer.startDocument("UTF-8",true);serializer.startTag(null,"accessibility-windows");
  serializer.attribute(null,"failure",failure.toString());
  StringBuilder text=new StringBuilder(failure.toString()).append('\n');int[] budget={5000};
  serializer.startTag(null,"active-window");
  dumpNode(getInstrumentation().getUiAutomation().getRootInActiveWindow(),serializer,text,0,budget);
  serializer.endTag(null,"active-window");
  for(AccessibilityWindowInfo window:getInstrumentation().getUiAutomation().getWindows()){
   serializer.startTag(null,"window");serializer.attribute(null,"id",String.valueOf(window.getId()));
   serializer.attribute(null,"type",String.valueOf(window.getType()));serializer.attribute(null,"active",String.valueOf(window.isActive()));
   dumpNode(window.getRoot(),serializer,text,0,budget);serializer.endTag(null,"window");
  }
  serializer.endTag(null,"accessibility-windows");serializer.endDocument();
  diagnosticFile("call-integration-failed-notification-shade.xml",xml.toString());
  diagnosticFile("call-integration-failed-notification-shade.txt",text.toString());
 }
 private void dumpNode(AccessibilityNodeInfo node,XmlSerializer xml,StringBuilder text,int depth,int[] budget)throws Exception{
  if(node==null||depth>80||--budget[0]<0)return;
  android.graphics.Rect bounds=new android.graphics.Rect();node.getBoundsInScreen(bounds);
  String actions=node.getActionList().toString();xml.startTag(null,"node");
  String[] names={"class","id","package","text","description","bounds","visible","clickable","actions"};
  String[] values={String.valueOf(node.getClassName()),String.valueOf(node.getViewIdResourceName()),String.valueOf(node.getPackageName()),
   String.valueOf(node.getText()),String.valueOf(node.getContentDescription()),bounds.toShortString(),String.valueOf(node.isVisibleToUser()),
   String.valueOf(node.isClickable()),actions};
  for(int i=0;i<names.length;i++)xml.attribute(null,names[i],values[i]);
  for(int i=0;i<depth;i++)text.append("  ");text.append(values[0]).append(' ').append(values[1]).append(" text=").append(values[3])
   .append(" description=").append(values[4]).append(" bounds=").append(values[5]).append(" visible=").append(values[6])
   .append(" clickable=").append(values[7]).append(" actions=").append(actions).append('\n');
  for(int i=0;i<node.getChildCount();i++)dumpNode(node.getChild(i),xml,text,depth+1,budget);
  xml.endTag(null,"node");
 }
 private void diagnosticFile(String name,String content)throws Exception{
  File file=new File(target.getExternalFilesDir(null),name);
  try(FileOutputStream out=new FileOutputStream(file)){out.write(content.getBytes(StandardCharsets.UTF_8));}
  shell("cp "+file.getAbsolutePath()+" "+DIRECTORY+"/"+name);
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
  evidence.put("real_phone_call_foreground_service",notificationsPassed);
  evidence.put("foreground_service_type","phoneCall");
  evidence.put("dialer_role","Granted with Android role manager on disposable emulator before foreground start; emulator discarded after suite");
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
