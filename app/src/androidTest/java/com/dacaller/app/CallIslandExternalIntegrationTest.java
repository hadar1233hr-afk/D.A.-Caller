package com.dacaller.app;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.TelecomManager;
import android.test.InstrumentationTestCase;
import android.view.KeyEvent;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.inspector.WindowInspector;
import android.widget.EditText;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;

/** End-to-end modem -> Telecom -> production service -> actual external window.
 * Run only on the disposable emulator with scripts/emulator-call-driver.py.
 * No Call, session, action acknowledgement or system window is mocked.
 */
@SuppressWarnings({"deprecation","unchecked"})
@android.annotation.TargetApi(33)
public final class CallIslandExternalIntegrationTest extends InstrumentationTestCase {
 private static final String DIRECTORY="/sdcard/Download/DA-CALLER-snapshots";
 private static final String NUMBER="0500099971",NAME="נועה שיחת אמולטור",QUERY="נועה";
 private Context target;
 private SharedPreferences prefs;
 private Map<String,?> original,originalNavigation;
 private String originalFont,originalOverlayMode,originalDialer,launcherPackage;
 private boolean originalDialerKnown;
 private int originalAccessibilityFlags;
 private MainActivity main;
 private Uri insertedContact;
 private boolean modemRequested;
 private long requestSequence;
 private String lastTap="none";
 private final JSONArray observations=new JSONArray();
 private final JSONObject checks=new JSONObject();
 private final JSONObject roleAttempt=new JSONObject();
 private final JSONObject homeResolution=new JSONObject();

 @Override protected void setUp()throws Exception{
  super.setUp();target=getInstrumentation().getTargetContext();
  assertEquals("Refuse telephony tests on a physical device","1",shell("getprop ro.kernel.qemu").trim());
  assertEquals(33,android.os.Build.VERSION.SDK_INT);
  prefs=target.getSharedPreferences("da_caller",Context.MODE_PRIVATE);original=prefs.getAll();
  originalNavigation=target.getSharedPreferences(CallReturnNavigation.PREFERENCES,Context.MODE_PRIVATE).getAll();
  originalFont=shell("settings get system font_scale").trim();
  originalOverlayMode=shell("cmd appops get "+target.getPackageName()+" SYSTEM_ALERT_WINDOW").trim();
  TelecomManager telecom=target.getSystemService(TelecomManager.class);
  originalDialer=telecom.getDefaultDialerPackage();originalDialerKnown=true;
  AccessibilityServiceInfo accessibility=getInstrumentation().getUiAutomation().getServiceInfo();
  originalAccessibilityFlags=accessibility.flags;
  accessibility.flags|=AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS|AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
  getInstrumentation().getUiAutomation().setServiceInfo(accessibility);
  Intent home=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
  android.content.pm.ResolveInfo local=target.getPackageManager().resolveActivity(home,0);
  homeResolution.put("target_pm_component",local==null?JSONObject.NULL:
   new ComponentName(local.activityInfo.packageName,local.activityInfo.name).flattenToString());
  // The target's package-visibility scope can return a resolver/fallback rather
  // than the real launcher. Ask the platform resolver under the shell identity.
  String homeOutput=shell("cmd package resolve-activity --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME");
  homeResolution.put("shell_output",homeOutput);ComponentName resolvedHome=null;
  for(String line:homeOutput.split("\\n")){
   ComponentName candidate=ComponentName.unflattenFromString(line.trim());if(candidate!=null)resolvedHome=candidate;
  }
  assertNotNull("Platform must resolve a real HOME component: "+homeOutput,resolvedHome);
  launcherPackage=resolvedHome.getPackageName();
  homeResolution.put("shell_component",resolvedHome.flattenToString()).put("expected_package",launcherPackage);
  shell("mkdir -p "+DIRECTORY);shell("settings put system font_scale 1.3");
  for(String permission:new String[]{Manifest.permission.READ_CONTACTS,Manifest.permission.WRITE_CONTACTS,
    Manifest.permission.READ_CALL_LOG,Manifest.permission.READ_PHONE_STATE,Manifest.permission.CALL_PHONE,
    Manifest.permission.POST_NOTIFICATIONS})shell("pm grant "+target.getPackageName()+" "+permission);
  RoleManager roles=target.getSystemService(RoleManager.class);
  roleAttempt.put("original_default_dialer",originalDialer==null?JSONObject.NULL:originalDialer)
   .put("available",roles.isRoleAvailable(RoleManager.ROLE_DIALER))
   .put("add_output",shell("cmd role add-role-holder --user 0 android.app.role.DIALER "+target.getPackageName()));
  writeFile("external-role-attempt.json",roleAttempt.toString(2));
  // Android13's shell interface does not guarantee a get-role-holders command.
  // Verify actual public APIs, including Telecom's selected dialer, instead.
  await(()->roles.isRoleHeld(RoleManager.ROLE_DIALER)&&target.getPackageName().equals(telecom.getDefaultDialerPackage()),
   "Test app must hold the actual dialer role and be Telecom's default dialer");
  roleAttempt.put("role_held",roles.isRoleHeld(RoleManager.ROLE_DIALER)).put("default_dialer",telecom.getDefaultDialerPackage());
  writeFile("external-role-attempt.json",roleAttempt.toString(2));
  shell("cmd appops set "+target.getPackageName()+" SYSTEM_ALERT_WINDOW ignore");
  assertFalse("First call must begin without native overlay special access",Settings.canDrawOverlays(target));
  insertContact();
  prefs.edit().putBoolean("role_prompted",true).putBoolean("notifications_prompted",true)
   .putBoolean("voice_permission_prompted",true).putBoolean("haptics",false).putString(CallArchive.PREF,CallArchive.OFF)
   .putString("language","he").putBoolean("dark",true).putBoolean("amoled",false)
   .putString("start_page","contacts").putString("call_theme","pixel")
   .putBoolean("island_enabled",true).putBoolean("island_outside_app",true)
   .putBoolean("island_animations",false).putBoolean("island_confirm_end",true)
   .putBoolean("island_show_mute",true).putBoolean("island_show_speaker",true)
   .putString("island_style","apple").putString("island_size","compact").commit();
  main=(MainActivity)getInstrumentation().startActivitySync(new Intent(target,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  await(()->main.hasWindowFocus()&&find(main.getWindow().getDecorView(),EditText.class)!=null,"Actual contacts screen did not open");
  assertEquals(1.3f,main.getResources().getConfiguration().fontScale,.05f);
  assertEquals(240,main.getResources().getDisplayMetrics().densityDpi);
  ui(()->find(main.getWindow().getDecorView(),EditText.class).setText(QUERY));settle();
 }
 @Override protected void tearDown()throws Exception{
  try{
   if(modemRequested)try{modem("cancel");}catch(Throwable ignored){}
   if(main!=null&&!main.isDestroyed())ui(main::finish);
   if(insertedContact!=null){target.getContentResolver().delete(insertedContact,null,null);ContactPhotos.clearCache();}
   if(prefs!=null&&original!=null)restore(prefs,original);
   if(originalNavigation!=null)restore(target.getSharedPreferences(CallReturnNavigation.PREFERENCES,Context.MODE_PRIVATE),originalNavigation);
   if(originalOverlayMode!=null){
    String mode="default";
    for(String candidate:new String[]{"allow","ignore","deny","default"})if(originalOverlayMode.contains("SYSTEM_ALERT_WINDOW: "+candidate)){mode=candidate;break;}
    shell("cmd appops set "+target.getPackageName()+" SYSTEM_ALERT_WINDOW "+mode);
   }
   if(originalDialerKnown&&!target.getPackageName().equals(originalDialer)){
    // The preceding native notification test already holds this role for the
    // disposable emulator. Never revoke/regrant the identical original holder:
    // permission revocation can terminate the instrumentation UID. Flag1 is the
    // shell command's DONT_KILL_APP option, not a qualification bypass.
    shell("cmd role remove-role-holder --user 0 android.app.role.DIALER "+target.getPackageName()+" 1");
    if(originalDialer!=null&&originalDialer.matches("[A-Za-z0-9_.]+"))
     shell("cmd role add-role-holder --user 0 android.app.role.DIALER "+originalDialer+" 1");
   }
   if(originalFont!=null)shell("settings put system font_scale "+(originalFont.matches("[0-9.]+")?originalFont:"1.0"));
   AccessibilityServiceInfo accessibility=getInstrumentation().getUiAutomation().getServiceInfo();accessibility.flags=originalAccessibilityFlags;
   getInstrumentation().getUiAutomation().setServiceInfo(accessibility);
  }finally{super.tearDown();}
 }
 private void insertContact()throws Exception{
  ArrayList<ContentProviderOperation> batch=new ArrayList<>();
  batch.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
   .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE,null).withValue(ContactsContract.RawContacts.ACCOUNT_NAME,null).build());
  batch.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
   .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID,0)
   .withValue(ContactsContract.Data.MIMETYPE,ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
   .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,NAME).build());
  batch.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
   .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID,0)
   .withValue(ContactsContract.Data.MIMETYPE,ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
   .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER,NUMBER)
   .withValue(ContactsContract.CommonDataKinds.Phone.TYPE,ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE).build());
  ContentProviderResult[] result=target.getContentResolver().applyBatch(ContactsContract.AUTHORITY,batch);insertedContact=result[0].uri;
 }
 private interface Check{boolean ready()throws Exception;}
 private void await(Check check,String message)throws Exception{
  long deadline=SystemClock.uptimeMillis()+10000;
  do{getInstrumentation().waitForIdleSync();if(check.ready())return;Thread.sleep(60);}while(SystemClock.uptimeMillis()<deadline);
  diagnostics(message);fail(message);
 }
 private void ui(Runnable action)throws Exception{
  AtomicReference<Throwable> failure=new AtomicReference<>();getInstrumentation().runOnMainSync(()->{try{action.run();}catch(Throwable error){failure.set(error);}});
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
  JSONObject request=new JSONObject().put("id",id).put("operation",operation).put("number",NUMBER);
  writeFile("external-gsm-request.json",request.toString());modemRequested=true;
  AtomicReference<JSONObject> response=new AtomicReference<>();
  await(()->{
   String value=shell("cat "+DIRECTORY+"/external-gsm-response.json");
   try{JSONObject result=new JSONObject(value);if(id.equals(result.optString("id"))){response.set(result);return true;}}catch(Exception ignored){}return false;
  },"Host emulator GSM driver did not acknowledge "+operation);
  JSONObject result=response.get();observations.put(new JSONObject().put("event","modem-"+operation).put("response",result));
  assertEquals("Host modem command failed",0,result.getInt("returncode"));
  if("call".equals(operation))assertTrue("Host console must acknowledge incoming simulated modem call",result.getString("stdout").contains("OK"));
 }
 private void key(int code)throws Exception{
  long now=SystemClock.uptimeMillis();
  assertTrue(getInstrumentation().getUiAutomation().injectInputEvent(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,code,0),true));
  assertTrue(getInstrumentation().getUiAutomation().injectInputEvent(new KeyEvent(now,now+40,KeyEvent.ACTION_UP,code,0),true));settle();
 }
 private boolean resumed(String packageName)throws Exception{
  boolean resumed=false;
  for(String line:shell("dumpsys activity activities").split("\\n"))
   if((line.contains("mResumedActivity")||line.contains("topResumedActivity"))&&line.contains(" "+packageName+"/")){resumed=true;break;}
  if(!resumed)return false;
  // A text occurrence in dumpsys alone does not establish a visible native app.
  for(AccessibilityWindowInfo window:getInstrumentation().getUiAutomation().getWindows()){
   AccessibilityNodeInfo root=window.getRoot();
   if(window.getType()==AccessibilityWindowInfo.TYPE_APPLICATION&&root!=null&&root.isVisibleToUser()
     &&packageName.contentEquals(String.valueOf(root.getPackageName())))return true;
  }
  return false;
 }
 private List<View> overlayRoots(){
  List<View> result=new ArrayList<>();
  for(View root:WindowInspector.getGlobalWindowViews())if(root.isAttachedToWindow()&&root.getLayoutParams() instanceof WindowManager.LayoutParams
    &&((WindowManager.LayoutParams)root.getLayoutParams()).type==WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)result.add(root);
  return result;
 }
 private View external(){for(View root:overlayRoots()){View view=root.findViewWithTag("island-root");if(view!=null&&view.isShown())return view;}return null;}
 private int externalCount(){int count=0;for(View root:overlayRoots())if(root.findViewWithTag("island-root")!=null)count++;return count;}
 private View tag(String value){View root=external();return root==null?null:root.findViewWithTag(value);}
 private <T extends View>T find(View view,Class<T> type){
  if(type.isInstance(view))return type.cast(view);if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){
   T found=find(((ViewGroup)view).getChildAt(i),type);if(found!=null)return found;
  }return null;
 }
 private View dialogButton(int id){for(View root:overlayRoots()){View view=root.findViewById(id);if(view!=null&&view.isShown())return view;}return null;}
 private Rect islandScreenBounds()throws Exception{
  Rect result=new Rect();ui(()->{
   View island=external();if(island==null||!island.isShown())return;
   int[] position=new int[2];island.getLocationOnScreen(position);
   result.set(position[0],position[1],position[0]+island.getWidth(),position[1]+island.getHeight());
  });return result;
 }
 private boolean callerLabel(AccessibilityNodeInfo node){
  CharSequence text=node.getText();if(text==null)return false;String value=text.toString();
  return value.contains(NAME)||value.replaceAll("[^0-9]","").contains(NUMBER);
 }
 private Rect coveringIncomingRow(AccessibilityNodeInfo node,Rect island,int depth,int[] budget){
  if(node==null||depth>70||--budget[0]<0)return null;
  if(node.isVisibleToUser()&&callerLabel(node)){
   AccessibilityNodeInfo ancestor=node;
   for(int parents=0;ancestor!=null&&parents<24;parents++,ancestor=ancestor.getParent()){
    String id=String.valueOf(ancestor.getViewIdResourceName()),type=String.valueOf(ancestor.getClassName());
    if((id.endsWith("/expandableNotificationRow")||type.endsWith("ExpandableNotificationRow"))&&ancestor.isVisibleToUser()){
     Rect bounds=new Rect();ancestor.getBoundsInScreen(bounds);if(Rect.intersects(bounds,island))return bounds;break;
    }
   }
  }
  for(int i=0;i<node.getChildCount();i++){
   Rect found=coveringIncomingRow(node.getChild(i),island,depth+1,budget);if(found!=null)return found;
  }return null;
 }
 private Rect incomingNotificationCovering(Rect island){
  if(island.isEmpty())return null;
  for(AccessibilityWindowInfo window:getInstrumentation().getUiAutomation().getWindows()){
   AccessibilityNodeInfo root=window.getRoot();
   if(root==null||!"com.android.systemui".contentEquals(String.valueOf(root.getPackageName())))continue;
   Rect found=coveringIncomingRow(root,island,0,new int[]{5000});if(found!=null)return found;
  }return null;
 }
 private void awaitUnobscuredIncoming(String stage)throws Exception{
  long start=SystemClock.uptimeMillis(),deadline=start+8000,clearSince=0;int blockedSamples=0;Rect lastBlocking=null;
  while(SystemClock.uptimeMillis()<deadline){
   getInstrumentation().waitForIdleSync();Rect bounds=islandScreenBounds();Rect blocking=incomingNotificationCovering(bounds);
   if(!bounds.isEmpty()&&blocking==null){
    if(clearSince==0)clearSince=SystemClock.uptimeMillis();
    if(SystemClock.uptimeMillis()-clearSince>=350){
     observations.put(new JSONObject().put("event","unobscured-native-input").put("stage",stage)
      .put("wait_millis",SystemClock.uptimeMillis()-start).put("blocked_samples",blockedSamples)
      .put("island_bounds",bounds.toShortString()));return;
    }
   }else{clearSince=0;if(blocking!=null){blockedSamples++;lastBlocking=blocking;}}
   Thread.sleep(50);
  }
  String message="Actual incoming SystemUI row must clear the external island before native input: "+stage+" blocking="+lastBlocking;
  diagnostics(message);fail(message);
 }
 private void tap(View view)throws Exception{
  assertNotNull("Missing native external control",view);int[] point=new int[2];
  ui(()->{
   Rect bounds=new Rect();assertTrue("Control must be visible "+view.getTag(),view.isShown()&&view.getGlobalVisibleRect(bounds));
   int[] screen=new int[2],window=new int[2];view.getLocationOnScreen(screen);view.getLocationInWindow(window);
   point[0]=bounds.centerX()+screen[0]-window[0];point[1]=bounds.centerY()+screen[1]-window[1];
   lastTap=String.valueOf(view.getTag())+" id="+view.getId()+" bounds="+bounds+" @ "+point[0]+","+point[1];
  });
  long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,point[0],point[1],0);
  MotionEvent up=MotionEvent.obtain(now,now+50,MotionEvent.ACTION_UP,point[0],point[1],0);
  // UiAutomation does not add Instrumentation.sendPointerSync's default source.
  // Pointer hit-testing is essential while a different app owns keyboard focus.
  down.setSource(InputDevice.SOURCE_TOUCHSCREEN);up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
  try{assertTrue(getInstrumentation().getUiAutomation().injectInputEvent(down,true));assertTrue(getInstrumentation().getUiAutomation().injectInputEvent(up,true));}
  finally{down.recycle();up.recycle();}settle();
 }
 private void geometry()throws Exception{
  ui(()->{
   View island=external();assertNotNull(island);assertEquals("Exactly one external island window",1,externalCount());
   assertEquals(1.3f,island.getResources().getConfiguration().fontScale,.05f);
   Rect bounds=new Rect();assertTrue(island.getGlobalVisibleRect(bounds));
   int[] position=new int[2];island.getLocationOnScreen(position);
   assertTrue("External island horizontally clipped",position[0]>=0&&position[0]+island.getWidth()<=480);
   assertTrue("External island must leave status icons clear",position[1]>=24);
   assertTrue("External island must leave native navigation clear",position[1]+island.getHeight()<=854-60);
   assertTrue("External island needs a usable touch height",island.getHeight()>=Math.round(48*target.getResources().getDisplayMetrics().density));
   View caller=tag("island-caller");assertTrue(caller instanceof TextView);assertEquals(NAME,((TextView)caller).getText().toString());
   textFits(island);
  });
 }
 private void textFits(View view){
  if(!view.isShown())return;
  if(view instanceof TextView&&((TextView)view).length()>0){TextView text=(TextView)view;assertNotNull(text.getLayout());
   assertTrue("Vertically clipped external label: "+text.getText(),text.getLayout().getHeight()<=text.getHeight()-text.getCompoundPaddingTop()-text.getCompoundPaddingBottom()+1);}
  if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)textFits(((ViewGroup)view).getChildAt(i));
 }
 private void snapshot(String name)throws Exception{
  settle();Bitmap bitmap=getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(bitmap);
  assertEquals(480,bitmap.getWidth());assertEquals(854,bitmap.getHeight());
  File file=new File(target.getExternalFilesDir(null),"external-"+name+".png");
  try(FileOutputStream output=new FileOutputStream(file)){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,output));}finally{bitmap.recycle();}
  shell("cp "+file.getAbsolutePath()+" "+DIRECTORY+"/"+file.getName());
 }
 private Call live(){return PhoneSessionService.current;}
 private boolean state(int state){Call call=live();return call!=null&&call.getState()==state;}
 private CallAudioState audio(){PhoneSessionService service=PhoneSessionService.instance;return service==null?null:service.getCallAudioState();}
 private void note(String event)throws Exception{
  CallIslandState state=PhoneSessionService.islandSnapshot();JSONObject item=new JSONObject().put("event",event)
   .put("overlay_windows",externalCount()).put("overlay_permission",Settings.canDrawOverlays(target));
  if(state!=null)item.put("token",state.token).put("state",state.state).put("route",state.route).put("muted",state.muted).put("caller",state.name);
  observations.put(item);
 }
 public void testActualTelecomExternalIslandOnLauncherAndSettings()throws Exception{
  try{scenario();}catch(Throwable failure){
   diagnostics(String.valueOf(failure));if(failure instanceof Exception)throw (Exception)failure;throw (Error)failure;
  }
 }
 private void scenario()throws Exception{
  modem("call");await(()->state(Call.STATE_RINGING)&&PhoneSessionService.islandSnapshot()!=null,"Actual Telecom incoming call did not bind to production service");
  Call originalCall=live();String token=PhoneSessionService.islandSnapshot().token;
  await(()->NAME.equals(PhoneSessionService.islandSnapshot().name),"Actual contact provider identity did not reach production service");
  checks.put("real_modem_telecom_incallservice",true);note("incoming-bound");
  key(KeyEvent.KEYCODE_HOME);await(()->resumed(launcherPackage)&&external()==null,"Denied overlay access must leave actual launcher without an island");
  assertTrue("Lack of optional special access must preserve the ringing Telecom call",state(Call.STATE_RINGING));
  snapshot("permission-denied-launcher");
  shell("cmd appops set "+target.getPackageName()+" SYSTEM_ALERT_WINDOW allow");
  await(()->Settings.canDrawOverlays(target)&&external()!=null,"External island did not appear after actual special-access grant");
  awaitUnobscuredIncoming("first-incoming");
  geometry();snapshot("launcher-incoming-compact");
  tap(tag("island-expand"));await(()->tag("island-action-ANSWER")!=null,"Incoming external controls did not expand");geometry();snapshot("launcher-incoming-expanded");
  tap(tag("island-action-ANSWER"));await(()->state(Call.STATE_ACTIVE),"Native external answer did not activate actual Telecom call");
  assertSame(originalCall,live());assertEquals(token,PhoneSessionService.islandSnapshot().token);
  await(()->tag("island-action-MUTE_ON")!=null&&tag("island-action-SPEAKER_ON")!=null,"Active external controls did not follow actual audio state");
  geometry();snapshot("launcher-active-expanded");checks.put("native_answer_acknowledged_by_telecom",true);
  tap(tag("island-action-MUTE_ON"));await(()->audio()!=null&&audio().isMuted(),"Actual Telecom mute acknowledgement missing");
  tap(tag("island-action-MUTE_OFF"));await(()->audio()!=null&&!audio().isMuted(),"Actual Telecom unmute acknowledgement missing");
  tap(tag("island-action-SPEAKER_ON"));await(()->audio()!=null&&audio().getRoute()==CallAudioState.ROUTE_SPEAKER,"Actual Telecom speaker acknowledgement missing");
  tap(tag("island-action-SPEAKER_OFF"));await(()->audio()!=null&&audio().getRoute()!=CallAudioState.ROUTE_SPEAKER,"Actual Telecom speaker off acknowledgement missing");
  checks.put("native_mute_speaker_acknowledged_by_telecom",true);note("audio-controls-acknowledged");
  tap(tag("island-open"));await(()->resumed(target.getPackageName())&&external()==null,"Opening full call must hide external window");
  assertSame(originalCall,live());key(KeyEvent.KEYCODE_BACK);
  await(()->main.hasWindowFocus()&&main.findViewById(android.R.id.content).findViewWithTag("island-root")!=null,"Back did not restore actual app window and in-app island");
  assertEquals("External duplicate over own app",0,externalCount());
  assertEquals(QUERY,find(main.getWindow().getDecorView(),EditText.class).getText().toString());
  assertSame(originalCall,live());snapshot("inapp-return");checks.put("return_back_preserves_call_query_without_duplicate",true);
  key(KeyEvent.KEYCODE_HOME);await(()->resumed(launcherPackage)&&external()!=null,"External island did not return after leaving own app");
  target.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  await(()->resumed("com.android.settings")&&external()!=null,"External island did not remain over actual Android Settings");
  if(tag("island-expand")!=null)tap(tag("island-expand"));await(()->tag("island-action-END")!=null,"Settings overlay did not expand");
  geometry();snapshot("settings-active-expanded");checks.put("launcher_and_other_app_real_windows",true);
  tap(tag("island-action-END"));await(()->dialogButton(android.R.id.button1)!=null,"External end confirmation did not use native overlay window");
  assertSame(originalCall,live());snapshot("end-confirmation");tap(dialogButton(android.R.id.button2));
  await(()->dialogButton(android.R.id.button1)==null,"Cancel did not dismiss external confirmation");assertTrue(state(Call.STATE_ACTIVE));
  tap(tag("island-action-END"));await(()->dialogButton(android.R.id.button1)!=null,"Second end confirmation missing");tap(dialogButton(android.R.id.button1));
  await(()->(live()==null||live().getState()==Call.STATE_DISCONNECTED)&&external()==null,"Native confirmed end did not terminate real Telecom call and remove window");
  key(KeyEvent.KEYCODE_HOME);await(()->resumed(launcherPackage)&&external()==null,"Ended external island leaked over launcher");snapshot("ended-launcher");
  checks.put("native_end_cancel_confirm_and_cleanup",true);note("ended");
  modem("call");await(()->state(Call.STATE_RINGING),"Second actual incoming call did not bind");
  assertFalse("Real second call must receive fresh UUID",token.equals(PhoneSessionService.islandSnapshot().token));
  key(KeyEvent.KEYCODE_HOME);await(()->resumed(launcherPackage)&&external()!=null,"Second call external island missing");
  awaitUnobscuredIncoming("second-incoming");
  prefs.edit().putBoolean("island_outside_app",false).commit();await(()->external()==null,"Disabled external preference left a window");
  assertTrue("Disabling external UI must preserve actual call",state(Call.STATE_RINGING));
  prefs.edit().putBoolean("island_outside_app",true).commit();await(()->external()!=null,"Re-enabled external UI did not return");
  shell("cmd appops set "+target.getPackageName()+" SYSTEM_ALERT_WINDOW ignore");
  await(()->!Settings.canDrawOverlays(target)&&external()==null,"Revoking actual overlay special access did not remove window");
  assertTrue("Permission loss must not decline a call",state(Call.STATE_RINGING));
  shell("cmd appops set "+target.getPackageName()+" SYSTEM_ALERT_WINDOW allow");
  await(()->Settings.canDrawOverlays(target)&&external()!=null,"Regranting actual overlay special access did not restore window");
  checks.put("disabled_or_permission_denied_preserves_real_call",true);note("permission-regranted");
  modem("cancel");await(()->(live()==null||live().getState()==Call.STATE_DISCONNECTED)&&external()==null,"Modem cancellation leaked external window");
  checks.put("modem_end_removes_window",true);
  JSONObject evidence=new JSONObject().put("api",33).put("font_scale",1.3).put("density_dpi",240)
   .put("data_origin","Emulator GSM modem -> Android Telecom -> production PhoneSessionService. No injected Call or session.")
   .put("physical_cellular_audio_verified",false).put("role",roleAttempt).put("home_resolution",homeResolution).put("checks",checks).put("observations",observations);
  writeFile("external-island-native.json",evidence.toString(2));
 }
 private void diagnostics(String message)throws Exception{
  try{snapshot("failed");}catch(Throwable ignored){}
  JSONObject evidence=new JSONObject().put("failure",message).put("last_tap",lastTap).put("checks",checks).put("observations",observations)
   .put("activities",shell("dumpsys activity activities")).put("windows",shell("dumpsys window windows"))
   .put("role",roleAttempt).put("home_resolution",homeResolution).put("role_shell_help",shell("cmd role help")).put("role_service",shell("dumpsys role"))
   .put("default_dialer",target.getSystemService(TelecomManager.class).getDefaultDialerPackage())
   .put("role_held",target.getSystemService(RoleManager.class).isRoleHeld(RoleManager.ROLE_DIALER))
   .put("recent_logcat",shell("logcat -d -t 1000")).put("accessibility_windows",accessibilityDiagnostics());
  writeFile("external-island-failure.json",evidence.toString(2));
 }
 private JSONArray accessibilityDiagnostics()throws Exception{
  JSONArray windows=new JSONArray();int[] budget={7000};
  for(AccessibilityWindowInfo window:getInstrumentation().getUiAutomation().getWindows()){
   JSONArray nodes=new JSONArray();dumpAccessibility(window.getRoot(),nodes,0,budget);
   windows.put(new JSONObject().put("id",window.getId()).put("layer",window.getLayer()).put("type",window.getType()).put("nodes",nodes));
  }return windows;
 }
 private void dumpAccessibility(AccessibilityNodeInfo node,JSONArray nodes,int depth,int[] budget)throws Exception{
  if(node==null||depth>70||--budget[0]<0)return;Rect bounds=new Rect();node.getBoundsInScreen(bounds);
  nodes.put(new JSONObject().put("package",String.valueOf(node.getPackageName())).put("class",String.valueOf(node.getClassName()))
   .put("id",String.valueOf(node.getViewIdResourceName())).put("text",String.valueOf(node.getText()))
   .put("description",String.valueOf(node.getContentDescription())).put("bounds",bounds.toShortString())
   .put("visible",node.isVisibleToUser()).put("clickable",node.isClickable()));
  for(int i=0;i<node.getChildCount();i++)dumpAccessibility(node.getChild(i),nodes,depth+1,budget);
 }
 private static void restore(SharedPreferences preferences,Map<String,?> values){
  SharedPreferences.Editor edit=preferences.edit().clear();for(Map.Entry<String,?> entry:values.entrySet()){
   Object value=entry.getValue();String key=entry.getKey();
   if(value instanceof String)edit.putString(key,(String)value);else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);
   else if(value instanceof Integer)edit.putInt(key,(Integer)value);else if(value instanceof Long)edit.putLong(key,(Long)value);
   else if(value instanceof Float)edit.putFloat(key,(Float)value);else if(value instanceof java.util.Set)edit.putStringSet(key,(java.util.Set<String>)value);
  }edit.commit();
 }
}
