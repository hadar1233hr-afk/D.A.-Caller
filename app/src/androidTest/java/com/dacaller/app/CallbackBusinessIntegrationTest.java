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

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.widget.EditText;
import java.util.TimeZone;

/** Disposable Android13 device integration. No Call/CallLog row, business seed,
 * protected Telecom broadcast or reminder delivery is fabricated by this test.
 * Alarm delivery is accelerated with the production private immutable PI;
 * this verifies OS delivery, not waiting until the user's21:00 wall-clock time.
 */
@SuppressWarnings({"deprecation","unchecked"})
@android.annotation.TargetApi(33)
public final class CallbackBusinessIntegrationTest extends InstrumentationTestCase {
 private static final String DIRECTORY="/sdcard/Download/DA-CALLER-snapshots";
 private static final String NUMBER="0500099971",NAME="נועה תזכורת לשיחה";
 private static JSONObject completedReminder,completedBusiness;
 private Context target;private Application application;
 private SharedPreferences settings,missedState,reminderState;
 private Map<String,?> original,originalNavigation,originalMissed,originalReminder;
 private String originalFont;private int originalAccessibilityFlags;
 private volatile MainActivity main;private volatile CallbackReminderActivity reminder;private volatile CallScreenActivity callScreen;private volatile ContactEditorActivity editor;private boolean reminderScenario;
 private final List<Activity> launched=new ArrayList<>();
 private Uri insertedContact;private boolean modemRequested;private long requestSequence,started;
 private final Set<Long> baselineIds=new HashSet<>();
 private final JSONArray modemCommands=new JSONArray(),calls=new JSONArray(),events=new JSONArray(),readObservations=new JSONArray();
 private String lastAction="none";
 private final Application.ActivityLifecycleCallbacks activityCallbacks=new Application.ActivityLifecycleCallbacks(){
  public void onActivityCreated(Activity activity,android.os.Bundle state){}
  public void onActivityStarted(Activity activity){}
  public void onActivityResumed(Activity activity){if(activity instanceof MainActivity)main=(MainActivity)activity;
   if(activity instanceof CallbackReminderActivity)reminder=(CallbackReminderActivity)activity;
   if(activity instanceof CallScreenActivity)callScreen=(CallScreenActivity)activity;
   if(activity instanceof ContactEditorActivity)editor=(ContactEditorActivity)activity;}
  public void onActivityPaused(Activity activity){}
  public void onActivityStopped(Activity activity){}
  public void onActivitySaveInstanceState(Activity activity,android.os.Bundle state){}
  public void onActivityDestroyed(Activity activity){}
 };
 @Override protected void setUp()throws Exception{
  super.setUp();target=getInstrumentation().getTargetContext();
  assertEquals("Refuse real modem/user data","1",shell("getprop ro.kernel.qemu").trim());assertEquals(33,android.os.Build.VERSION.SDK_INT);
  application=(Application)target.getApplicationContext();application.registerActivityLifecycleCallbacks(activityCallbacks);
  settings=target.getSharedPreferences("da_caller",0);original=settings.getAll();
  originalNavigation=target.getSharedPreferences(CallReturnNavigation.PREFERENCES,0).getAll();
  missedState=target.getSharedPreferences(MissedCallNotifications.PREFERENCES,0);originalMissed=missedState.getAll();
  reminderState=target.getSharedPreferences(CallbackReminder.STATE,0);originalReminder=reminderState.getAll();
  originalFont=shell("settings get system font_scale").trim();AccessibilityServiceInfo service=getInstrumentation().getUiAutomation().getServiceInfo();
  originalAccessibilityFlags=service.flags;service.flags|=AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS|AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
  getInstrumentation().getUiAutomation().setServiceInfo(service);shell("mkdir -p "+DIRECTORY);shell("settings put system font_scale 1.3");
  shell("cmd role add-role-holder --user 0 android.app.role.DIALER "+target.getPackageName());
  await(()->target.getSystemService(RoleManager.class).isRoleHeld(RoleManager.ROLE_DIALER)&&
   target.getPackageName().equals(target.getSystemService(TelecomManager.class).getDefaultDialerPackage()),"Default dialer role required");
  for(String permission:new String[]{Manifest.permission.READ_CONTACTS,Manifest.permission.WRITE_CONTACTS,Manifest.permission.READ_CALL_LOG,
   Manifest.permission.WRITE_CALL_LOG,Manifest.permission.READ_PHONE_STATE,Manifest.permission.CALL_PHONE,Manifest.permission.POST_NOTIFICATIONS})shell("pm grant "+target.getPackageName()+" "+permission);
  settings.edit().putBoolean("role_prompted",true).putBoolean("notifications_prompted",true).putBoolean("voice_permission_prompted",true)
   .putString("call_theme","pixel").putString("answer_mode","ios").putBoolean("haptics",false).putString(CallArchive.PREF,CallArchive.OFF).putBoolean("island_enabled",false)
   .putBoolean("island_outside_app",false).putBoolean("dark",false).putBoolean("amoled",false).putString("language","he")
   .putInt(CallbackReminder.PREF_MINUTE,1439).remove(CallbackReminder.PREF_ENABLED).putBoolean("settings_group_open_6",true).commit();
  CallbackReminder.schedule(target);reminderState.edit().clear().commit();
  try(Cursor cursor=target.getContentResolver().query(CallLog.Calls.CONTENT_URI,new String[]{CallLog.Calls._ID},null,null,null)){
   assertNotNull(cursor);while(cursor.moveToNext())baselineIds.add(cursor.getLong(0));}
  started=System.currentTimeMillis();
 }
 private interface Cleanup{void run()throws Exception;}
 private void cleanup(Cleanup action,List<Throwable> failures){try{action.run();}catch(Throwable failure){failures.add(failure);}}
 @Override protected void tearDown()throws Exception{
  List<Throwable> failures=new ArrayList<>();
  // Every restoration runs even when actual modem/notification cleanup fails;
  // one failing feature must not change the environment of the old233 renders.
  cleanup(()->shell("cmd statusbar collapse"),failures);
  if(modemRequested)cleanup(()->{
   Call active=PhoneSessionService.current;int before=ownRows().length();boolean pending=active!=null&&active.getState()!=Call.STATE_DISCONNECTED;
   modem("cancel");await(()->PhoneSessionService.current==null&&PhoneSessionService.instance==null,"Cleanup must await actual Telecom unbind");
   if(pending)await(()->ownRows().length()>before,"Cleanup must retain Telecom's final call-log row");
   acknowledgeOnlyOwnMissedSnapshot();
  },failures);
  cleanup(()->CallbackReminder.setEnabled(target,false),failures);
  for(int n=launched.size()-1;n>=0;n--){Activity value=launched.get(n);if(value!=null&&!value.isDestroyed())cleanup(()->ui(value::finish),failures);}
  if(insertedContact!=null)cleanup(()->{target.getContentResolver().delete(insertedContact,null,null);ContactPhotos.clearCache();},failures);
  cleanup(()->restore(settings,original),failures);
  cleanup(()->restore(target.getSharedPreferences(CallReturnNavigation.PREFERENCES,0),originalNavigation),failures);
  cleanup(()->restore(reminderState,originalReminder),failures);if(modemRequested)cleanup(()->restore(missedState,originalMissed),failures);
  cleanup(()->CallbackReminder.schedule(target),failures);
  cleanup(()->{AccessibilityServiceInfo service=getInstrumentation().getUiAutomation().getServiceInfo();service.flags=originalAccessibilityFlags;
   getInstrumentation().getUiAutomation().setServiceInfo(service);},failures);
  cleanup(()->shell("settings put system font_scale "+(originalFont.matches("[0-9.]+")?originalFont:"1.0")),failures);
  cleanup(()->{if(application!=null)application.unregisterActivityLifecycleCallbacks(activityCallbacks);},failures);cleanup(()->super.tearDown(),failures);
  if(!failures.isEmpty()){AssertionError failure=new AssertionError("Native cleanup failed: "+failures);for(Throwable item:failures)failure.addSuppressed(item);throw failure;}
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
 private void pointer(long downTime,int action,float x,float y)throws Exception{
  MotionEvent event=MotionEvent.obtain(downTime,SystemClock.uptimeMillis(),action,x,y,0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
  try{assertTrue("Native touchscreen injection rejected",getInstrumentation().getUiAutomation().injectInputEvent(event,true));}finally{event.recycle();}
 }
 private JSONObject answerByNativeSwipe(Call connected)throws Exception{
  // Open the real production call screen, never invoke a View listener or Call.answer from the test.
  assertNotNull(notification(1502).getNotification().contentIntent);notification(1502).getNotification().contentIntent.send();
  await(()->callScreen!=null&&!callScreen.isFinishing()&&tag(callScreen,"incoming-swipe-answer")!=null,"Real incoming swipe screen must appear");
  shell("cmd statusbar collapse");ready(callScreen);View prompt=tag(callScreen,"incoming-swipe-answer");
  Rect bounds=viewBounds(prompt);int threshold=Math.round(64*target.getResources().getDisplayMetrics().density);
  int x=bounds.left+bounds.width()/4,y=bounds.centerY();
  assertTrue("Swipe travel must fit real display",x+threshold+24<480);
  JSONObject proof=new JSONObject().put("input_method","UiAutomation.injectInputEvent SOURCE_TOUCHSCREEN")
   .put("answer_mode","ios").put("threshold_px",threshold).put("bounds",new JSONArray(new int[]{bounds.left,bounds.top,bounds.right,bounds.bottom}));
  proof.put("resting_bars",snapshot("incoming-swipe-resting",false));
  tapPoint(x,y);assertEquals("A real tap must not answer",Call.STATE_RINGING,connected.getState());proof.put("tap_stayed_ringing",true);
  long at=SystemClock.uptimeMillis();pointer(at,MotionEvent.ACTION_DOWN,x,y);Thread.sleep(35);
  pointer(at,MotionEvent.ACTION_MOVE,x+threshold/3f,y);Thread.sleep(35);pointer(at,MotionEvent.ACTION_UP,x+threshold/3f,y);settle();
  assertEquals("Short real drag must not answer",Call.STATE_RINGING,connected.getState());proof.put("short_drag_stayed_ringing",true);
  at=SystemClock.uptimeMillis();pointer(at,MotionEvent.ACTION_DOWN,x,y);Thread.sleep(35);pointer(at,MotionEvent.ACTION_MOVE,x+threshold+12,y);
  final float[] translated={0};ui(()->translated[0]=prompt.getTranslationX());assertTrue("Prompt must visibly follow native finger",translated[0]>0);
  proof.put("drag_translation_px",translated[0]);proof.put("dragging_bars",snapshot("incoming-swipe-dragging",false));
  pointer(at,MotionEvent.ACTION_CANCEL,x+threshold+12,y);settle();assertEquals("Cancelled real drag must not answer",Call.STATE_RINGING,connected.getState());
  ui(()->assertEquals(0f,prompt.getTranslationX(),0.01f));proof.put("cancelled_drag_stayed_ringing",true).put("cancel_reset_translation",true);
  at=SystemClock.uptimeMillis();pointer(at,MotionEvent.ACTION_DOWN,x,y);
  for(int step=1;step<=6;step++){Thread.sleep(35);pointer(at,MotionEvent.ACTION_MOVE,x+(threshold+18)*step/6f,y);}
  pointer(at,MotionEvent.ACTION_UP,x+threshold+18,y);await(()->connected.getState()==Call.STATE_ACTIVE,"Completed native swipe must activate the same real Telecom call");
  assertSame(connected,PhoneSessionService.current);proof.put("completed_swipe_activated_owned_call",true);
  return proof;
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

 private View tag(Activity activity,String key){return activity.getWindow().getDecorView().findViewWithTag(key);}
 private String text(Activity activity,String key){View value=tag(activity,key);assertTrue(key,value instanceof TextView);return ((TextView)value).getText().toString();}
 private ScrollView scrollAncestor(View view){for(View at=view;at!=null;){if(at instanceof ScrollView)return (ScrollView)at;
  android.view.ViewParent parent=at.getParent();at=parent instanceof View?(View)parent:null;}return null;}
 private void reveal(View view)throws Exception{
  assertNotNull(view);ScrollView scroll=scrollAncestor(view);assertNotNull(scroll);
  ui(()->{Rect rectangle=new Rect(0,0,view.getWidth(),view.getHeight());view.requestRectangleOnScreen(rectangle,true);});settle();
  await(()->{final boolean[] visible={false};ui(()->{Rect rectangle=new Rect();visible[0]=view.isAttachedToWindow()&&view.getGlobalVisibleRect(rectangle)
   &&rectangle.height()==view.getHeight()&&rectangle.bottom<=782;});return visible[0];},"Native control must be fully visible above navigation bar");
 }
 private void textFits(View view){
  if(!view.isShown())return;if(view instanceof TextView&&((TextView)view).length()>0){TextView label=(TextView)view;
   assertNotNull(label.getLayout());assertTrue("Clipped native text: "+label.getText(),label.getLayout().getHeight()<=label.getHeight()-label.getCompoundPaddingTop()-label.getCompoundPaddingBottom()+1);}
  if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++)textFits(((ViewGroup)view).getChildAt(n));
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

 public void testCallbackReminderFromActualTelecomAndPrivateOsAlarm()throws Exception{
  reminderScenario=true;
  try{reminderScenario();}catch(Throwable failure){failureEvidence(failure);if(failure instanceof Exception)throw (Exception)failure;throw (Error)failure;}
 }
 private StatusBarNotification notification(int id){for(StatusBarNotification item:target.getSystemService(NotificationManager.class).getActiveNotifications())
  if(item.getId()==id&&target.getPackageName().equals(item.getPackageName()))return item;return null;}
 private JSONArray ownRows()throws Exception{
  JSONArray result=new JSONArray();String[] projection={CallLog.Calls._ID,CallLog.Calls.NUMBER,CallLog.Calls.DATE,CallLog.Calls.DURATION,
   CallLog.Calls.TYPE,CallLog.Calls.NEW,CallLog.Calls.IS_READ};
  try(Cursor cursor=target.getContentResolver().query(CallLog.Calls.CONTENT_URI,projection,CallLog.Calls.DATE+">=?",new String[]{String.valueOf(started-2000)},CallLog.Calls.DATE+" ASC,"+CallLog.Calls._ID+" ASC")){
   assertNotNull(cursor);while(cursor.moveToNext()){long id=cursor.getLong(0);String phone=cursor.getString(1);
    if(baselineIds.contains(id)||!CallbackReminderModel.key(NUMBER).equals(CallbackReminderModel.key(phone)))continue;
    result.put(new JSONObject().put("id",id).put("number",phone).put("date",cursor.getLong(2)).put("duration",cursor.getLong(3))
     .put("type",cursor.getInt(4)).put("new",cursor.getInt(5)).put("is_read",cursor.getInt(6)));}
  }return result;
 }
 private CallbackReminder.Result loadReadOnly()throws Exception{
  JSONArray before=ownRows();CallbackReminder.Result result=CallbackReminder.load(target,System.currentTimeMillis(),null);
  JSONArray after=ownRows();assertEquals("Read-only reminder query must preserve exact real platform rows and flags",before.toString(),after.toString());
  readObservations.put(new JSONObject().put("before",before).put("after",after).put("observed_at",System.currentTimeMillis()));
  assertEquals(CallbackReminder.ERROR_NONE,result.error);return result;
 }
 private CallbackReminderModel.Entry caller(CallbackReminder.Result result){
  for(CallbackReminderModel.Entry entry:result.entries)if(CallbackReminderModel.key(NUMBER).equals(entry.key))return entry;return null;
 }
 private JSONObject entry(CallbackReminderModel.Entry value)throws Exception{
  assertNotNull(value);return new JSONObject().put("key",value.key).put("number",value.number).put("name",value.name)
   .put("missed_count",value.missedCount).put("missed_today",value.missedCountToday).put("callback_attempts",value.callbackAttempts)
   .put("latest_missed_id",value.latestMissedId).put("latest_missed_at",value.latestMissedAt);
 }
 private void insertContact()throws Exception{
  ArrayList<ContentProviderOperation> changes=new ArrayList<>();changes.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
   .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE,null).withValue(ContactsContract.RawContacts.ACCOUNT_NAME,null).build());
  changes.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI).withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID,0)
   .withValue(ContactsContract.Data.MIMETYPE,ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
   .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,NAME).build());
  changes.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI).withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID,0)
   .withValue(ContactsContract.Data.MIMETYPE,ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
   .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER,NUMBER).build());
  ContentProviderResult[] result=target.getContentResolver().applyBatch(ContactsContract.AUTHORITY,changes);insertedContact=result[0].uri;ContactPhotos.clearCache();
 }
 private Call incoming()throws Exception{
  modem("call");await(()->PhoneSessionService.current!=null&&PhoneSessionService.current.getState()==Call.STATE_RINGING&&PhoneSessionService.islandSnapshot()!=null,"Real emulator Telecom must ring");
  await(()->NAME.equals(PhoneSessionService.islandSnapshot().name),"Contact must resolve through the actual provider");return PhoneSessionService.current;
 }
 private void finishRemote(Call live,int expectedRows)throws Exception{
  modem("cancel");await(()->live.getState()==Call.STATE_DISCONNECTED&&PhoneSessionService.current==null&&PhoneSessionService.instance==null,"Actual modem call must disconnect/unbind");
  await(()->ownRows().length()==expectedRows,"Telecom must persist its own call-log row");
  calls.put(new JSONObject().put("token",live.getDetails().getHandle().toString()).put("disconnect_cause",live.getDetails().getDisconnectCause().getCode())
   .put("row",ownRows().getJSONObject(expectedRows-1)));
 }
 private boolean missedSnapshotContains(long id){
  try{JSONArray selected=new JSONArray(missedState.getString("snapshot_ids","[]"));for(int n=0;n<selected.length();n++)if(selected.getLong(n)==id)return true;}catch(Exception invalid){}return false;
 }
 private void miss(int expectedRows)throws Exception{
  int previousEvents=missedState.getInt("platform_events",0);Call live=incoming();Thread.sleep(350);assertEquals(Call.STATE_RINGING,live.getState());finishRemote(live,expectedRows);
  assertEquals(DisconnectCause.MISSED,live.getDetails().getDisconnectCause().getCode());JSONObject row=ownRows().getJSONObject(expectedRows-1);
  assertEquals(CallLog.Calls.MISSED_TYPE,row.getInt("type"));assertEquals(0,row.getLong("duration"));
  assertEquals(1,row.getInt("new"));assertEquals(0,row.getInt("is_read"));
  long latestId=row.getLong("id");await(()->missedState.getInt("platform_events",0)>previousEvents&&notification(MissedCallNotifications.ID)!=null&&missedSnapshotContains(latestId),
   "Actual protected Telecom broadcast must include this latest missed row in its current notification snapshot");
 }
 private CallbackReminderActivity openReminderViaSettings()throws Exception{
  View link=tag(main,"settings_callback_reminder_list");reveal(link);
  Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(CallbackReminderActivity.class.getName(),null,false);
  try{tapView(link);Activity opened=monitor.waitForActivityWithTimeout(9000);assertTrue(opened instanceof CallbackReminderActivity);
   launched.add(opened);ready(opened);await(()->tag(opened,"callback-reminder-entry-"+CallbackReminderModel.key(NUMBER))!=null,"Actual callback entry must load");return (CallbackReminderActivity)opened;
  }finally{getInstrumentation().removeMonitor(monitor);}
 }
 private void assertReminderUi(CallbackReminderActivity activity,boolean dark)throws Exception{
  View root=tag(activity,"callback-reminder-root");assertNotNull(root);assertEquals(View.LAYOUT_DIRECTION_RTL,root.getLayoutDirection());
  assertEquals(NAME,text(activity,"callback-reminder-name-"+CallbackReminderModel.key(NUMBER)));
  assertTrue(text(activity,"callback-reminder-counts-"+CallbackReminderModel.key(NUMBER)).contains("2"));
  View number=tag(activity,"callback-reminder-number-"+CallbackReminderModel.key(NUMBER));assertNotNull(number);assertEquals(View.TEXT_DIRECTION_LTR,number.getTextDirection());
  ui(()->textFits(root));assertEquals(1.3f,activity.getResources().getConfiguration().fontScale,.05f);
  JSONObject bars=snapshot("callback-list-"+(dark?"dark":"light"),dark);assertTrue(bars.getInt("status")>=40&&bars.getInt("navigation")>=40);
  events.put(new JSONObject().put("event","reminder-list-palette").put("palette",dark?"dark":"light").put("name",NAME)
   .put("counts",text(activity,"callback-reminder-counts-"+CallbackReminderModel.key(NUMBER))).put("number_ltr",true).put("root_rtl",true)
   .put("font_scale",activity.getResources().getConfiguration().fontScale).put("bar_contrast",bars));
  key(KeyEvent.KEYCODE_BACK);ready(main);assertEquals("settings",page(main));assertTrue(activity.isFinishing()||activity.isDestroyed());
 }
 private void reminderScenario()throws Exception{
  assertFalse("Feature must default off without a preference",CallbackReminder.enabled(target));assertNull(notification(CallbackReminder.ID));
  insertContact();
  for(boolean dark:new boolean[]{false,true}){
   settings.edit().putBoolean("dark",dark).putBoolean("amoled",false).putString("language","he").commit();startMain("settings");
   View toggle=tag(main,"settings_callback_reminder_enabled");reveal(toggle);
   if(!dark){android.widget.Switch control=findSwitch(toggle);assertNotNull(control);assertFalse(control.isChecked());tapView(control);
    await(()->CallbackReminder.enabled(target),"Native opt-in switch must persist enabled");}
   assertTrue(CallbackReminder.enabled(target));View time=tag(main,"settings_callback_reminder_time");assertNotNull(time);
   ui(()->textFits(toggle));JSONObject bars=snapshot("callback-settings-"+(dark?"dark":"light"),dark);
   assertTrue(bars.getInt("status")>=40&&bars.getInt("navigation")>=40);
   events.put(new JSONObject().put("event","settings-opt-in").put("palette",dark?"dark":"light").put("enabled",CallbackReminder.enabled(target))
    .put("minute",CallbackReminder.minuteOfDay(target)).put("bar_contrast",bars).put("input_method",dark?"persisted-native-switch":"native-pointer"));
  }
  // Keep the normal user cutoff in the future. Only the scheduled private OS
  // alarm below may trigger this accelerated-delivery proof.
  assertEquals(1439,CallbackReminder.minuteOfDay(target));
  miss(1);miss(2);CallbackReminder.Result pending=loadReadOnly();CallbackReminderModel.Entry before=caller(pending);
  assertNotNull(before);assertEquals(NAME,before.name);assertEquals(2,before.missedCount);assertEquals(0,before.callbackAttempts);
  JSONArray rowsBefore=ownRows();assertEquals("",reminderState.getString("notified_day",""));
  // Let the completed call return to Main, then leave it through a native Home
  // key. Main.onResume schedule() must finish before we replace its daily alarm.
  startMain("settings");key(KeyEvent.KEYCODE_HOME);await(()->!nativeApplicationVisible()&&!main.hasWindowFocus(),"Native Home must be foreground before accelerated OS alarm");
  settle();long scheduledAt=System.currentTimeMillis(),due=scheduledAt+1000;TimeZone zone=TimeZone.getDefault();
  PendingIntent operation=CallbackReminder.alarmOperation(target,due,zone);assertTrue(operation.isImmutable());
  target.getSystemService(AlarmManager.class).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,due,operation);
  long deadline=SystemClock.uptimeMillis()+45000;while(notification(CallbackReminder.ID)==null&&SystemClock.uptimeMillis()<deadline)Thread.sleep(100);
  StatusBarNotification delivered=notification(CallbackReminder.ID);assertNotNull("OS AlarmManager must deliver the real private receiver",delivered);
  Notification notification=delivered.getNotification();long observed=System.currentTimeMillis();
  assertEquals(CallbackReminder.CHANNEL,notification.getChannelId());assertEquals(Notification.CATEGORY_REMINDER,notification.category);
  assertTrue(notification.extras.getInt(CallbackReminder.EXTRA_PEOPLE)>=1);assertTrue(notification.extras.getInt(CallbackReminder.EXTRA_MISSED)>=2);
  assertTrue(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains(NAME));assertNotNull(notification.contentIntent);
  assertTrue(delivered.getPostTime()>=due);assertEquals(rowsBefore.toString(),ownRows().toString());
  JSONObject alarm=new JSONObject().put("method","production-private-immutable-PendingIntent-via-AlarmManager.setAndAllowWhileIdle")
   .put("accelerated_delivery",true).put("waited_until_user_cutoff",false).put("app_background_before_alarm",true).put("scheduled_at",scheduledAt).put("due",due)
   .put("observed_at",observed).put("post_time",delivered.getPostTime()).put("day",notification.extras.getString(CallbackReminder.EXTRA_DAY))
   .put("channel",notification.getChannelId()).put("category",notification.category).put("id",delivered.getId())
   .put("people",notification.extras.getInt(CallbackReminder.EXTRA_PEOPLE)).put("missed",notification.extras.getInt(CallbackReminder.EXTRA_MISSED));
  settings.edit().putBoolean("dark",false).commit();startMain("settings");assertReminderUi(openReminderViaSettings(),false);
  settings.edit().putBoolean("dark",true).commit();startMain("settings");
  Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(CallbackReminderActivity.class.getName(),null,false);
  try{notification.contentIntent.send();Activity opened=monitor.waitForActivityWithTimeout(9000);assertTrue(opened instanceof CallbackReminderActivity);
   launched.add(opened);ready(opened);await(()->tag(opened,"callback-reminder-entry-"+CallbackReminderModel.key(NUMBER))!=null,"Notification direct Activity destination must load");
   assertNull("Opening callback details must never dial",PhoneSessionService.current);assertReminderUi((CallbackReminderActivity)opened,true);
  }finally{getInstrumentation().removeMonitor(monitor);}
  Call connected=incoming();await(()->notification(1502)!=null,"Actual incoming CallStyle required");
  JSONObject swipe=answerByNativeSwipe(connected);
  await(()->connected.getDetails()!=null&&connected.getDetails().getConnectTimeMillis()>0,"Actual Telecom connection details must acknowledge connect time");
  long connectedAt=connected.getDetails().getConnectTimeMillis();assertTrue(connectedAt>0);
  long until=SystemClock.uptimeMillis()+12000;while(System.currentTimeMillis()-connectedAt<2200&&SystemClock.uptimeMillis()<until)Thread.sleep(60);
  assertTrue(System.currentTimeMillis()-connectedAt>=2200);assertNull("Actual active connection>1s resolves reminder before call log exists",caller(loadReadOnly()));
  finishRemote(connected,3);JSONObject answered=ownRows().getJSONObject(2);assertEquals(CallLog.Calls.INCOMING_TYPE,answered.getInt("type"));assertTrue(answered.getLong("duration")>=2);
  assertNull("Later connected call resolves previous missed calls",caller(loadReadOnly()));
  miss(4);CallbackReminderModel.Entry again=caller(loadReadOnly());assertNotNull(again);assertEquals(1,again.missedCount);assertTrue(again.missedCountToday>=3);
  JSONArray retained=ownRows();assertEquals(4,retained.length());
  JSONObject checks=new JSONObject();for(String flag:new String[]{"default_off_native_opt_in","actual_modem_calls_and_protected_missed_receiver","actual_contact_identity_and_grouped_counts",
   "private_os_alarm_delivered","reminder_load_and_delivery_preserve_call_rows_and_read_flags","actual_active_connection_over_one_second_resolves",
   "later_missed_call_restores_pending","native_settings_and_list_light_dark","direct_notification_details_never_autodial","native_back_to_previous_settings"})checks.put(flag,true);
  completedReminder=new JSONObject().put("checks",checks).put("modem_commands",new JSONArray(modemCommands.toString())).put("calls",calls).put("pending_before_connection",entry(before))
   .put("pending_after_later_miss",entry(again)).put("alarm",alarm).put("answered_call",answered).put("rows_before_delivery",rowsBefore)
   .put("rows_after_all_reminder_reads",retained).put("read_observations",readObservations).put("observations",events).put("answer_method","production-real-touchscreen-swipe").put("incoming_swipe",swipe);writeCombined();
 }
 private android.widget.Switch findSwitch(View root){if(root instanceof android.widget.Switch)return (android.widget.Switch)root;
  if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++){android.widget.Switch found=findSwitch(((ViewGroup)root).getChildAt(n));if(found!=null)return found;}return null;}
 private void acknowledgeOnlyOwnMissedSnapshot()throws Exception{
  StatusBarNotification missed=notification(MissedCallNotifications.ID);if(missed==null)return;
  Set<Long> own=new HashSet<>();JSONArray rows=ownRows();for(int n=0;n<rows.length();n++)if(rows.getJSONObject(n).getInt("type")==CallLog.Calls.MISSED_TYPE)own.add(rows.getJSONObject(n).getLong("id"));
  String selected=missedState.getString("snapshot_ids","");JSONArray ids=new JSONArray(selected.isEmpty()?"[]":selected);
  for(int n=0;n<ids.length();n++)assertTrue("Cleanup may acknowledge only this disposable test's actual missed rows",own.contains(ids.getLong(n)));
  assertNotNull(missed.getNotification().deleteIntent);missed.getNotification().deleteIntent.send();await(()->notification(MissedCallNotifications.ID)==null,"Own explicit missed notification cleanup must finish");
  JSONArray after=ownRows();assertEquals(rows.length(),after.length());for(int n=0;n<after.length();n++){JSONObject row=after.getJSONObject(n);
   if(own.contains(row.getLong("id"))){assertEquals(0,row.getInt("new"));assertEquals(1,row.getInt("is_read"));}}
  if(reminderScenario&&completedReminder!=null){completedReminder.put("cleanup",new JSONObject().put("method","production-own-missed-notification-delete-PendingIntent-after-read-only-proof")
   .put("retained_rows",after).put("acknowledged_ids",ids).put("all_missed_rows_read",true).put("no_call_log_delete",true));writeCombined();}
 }

 private AccessibilityNodeInfo nativeExactText(String value){
  AccessibilityNodeInfo root=getInstrumentation().getUiAutomation().getRootInActiveWindow();if(root==null)return null;
  for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(value))if(node.isVisibleToUser()&&value.contentEquals(node.getText()==null?"":node.getText()))return node;
  return null;
 }
 private void tapNativeText(String value)throws Exception{
  await(()->nativeExactText(value)!=null,"Native text missing: "+value);AccessibilityNodeInfo node=nativeExactText(value);
  Rect bounds=new Rect();node.getBoundsInScreen(bounds);assertTrue(bounds.width()>0&&bounds.height()>0);tapPoint(bounds.centerX(),bounds.centerY());
 }
 private Set<Long> rawContactIds(){
  Set<Long> ids=new HashSet<>();try(Cursor cursor=target.getContentResolver().query(ContactsContract.RawContacts.CONTENT_URI,new String[]{ContactsContract.RawContacts._ID},null,null,null)){
   assertNotNull(cursor);while(cursor.moveToNext())ids.add(cursor.getLong(0));}return ids;
 }
 public void testUnknownActualRecentCallerOpensNewEditorWithoutDialOrWrite()throws Exception{
  ContactPhotos.clearCache();Set<Long> contactsBefore=rawContactIds();
  modem("call");await(()->PhoneSessionService.current!=null&&PhoneSessionService.current.getState()==Call.STATE_RINGING,"Actual unknown caller must ring");
  Call unknown=PhoneSessionService.current;Thread.sleep(350);finishRemote(unknown,1);
  MainActivity activity=startMain("recents");assertEquals("recents",page(activity));
  await(()->nativeExactText(NUMBER)!=null,"Actual unknown Telecom row must appear in Recents");
  tapNativeText(NUMBER);tapNativeText("כרטיס איש קשר");
  await(()->nativeExactText("יצירת איש קשר חדש")!=null,"Unknown number must offer explicit new-contact action");
  assertNull("Opening unknown-number actions must never dial",PhoneSessionService.current);
  JSONObject proof=new JSONObject().put("api",33).put("number",NUMBER).put("origin","actual emulator Telecom missed call")
   .put("actions_bars",snapshot("recents-unknown-number-actions",false)).put("call_log_before_editor",ownRows());
  tapNativeText("יצירת איש קשר חדש");await(()->editor!=null&&!editor.isFinishing(),"Explicit action must launch the production editor");
  launched.add(editor);ready(editor);assertEquals(NUMBER,editor.getIntent().getStringExtra("incoming_number"));assertFalse(editor.getIntent().hasExtra("contact_id"));
  EditText phone=editor.findViewById(0x01000000 | ("phone0".hashCode() & 0x00ffffff));assertNotNull(phone);
  await(()->NUMBER.contentEquals(phone.getText()),"Exact unknown number must prefill new contact");
  assertEquals(contactsBefore,rawContactIds());assertNull(PhoneSessionService.current);
  ui(()->editor.getWindow().getInsetsController().hide(WindowInsets.Type.ime()));reveal(phone);viewBounds(phone);
  proof.put("editor_bars",snapshot("recents-new-contact-editor",false));key(KeyEvent.KEYCODE_BACK);ready(activity);
  assertEquals("recents",page(activity));assertNull(PhoneSessionService.current);assertEquals(contactsBefore,rawContactIds());
  JSONArray retained=ownRows();assertEquals(proof.getJSONArray("call_log_before_editor").toString(),retained.toString());
  proof.put("exact_number_prefilled",true).put("new_contact_without_existing_id",true).put("no_automatic_dial",true)
   .put("cancel_keeps_contacts_and_call_rows",true).put("back_to_recents",true).put("call_log_after_cancel",retained)
   .put("modem_commands",modemCommands).put("calls",calls);writeFile("recents-create-contact-native.json",proof.toString(2));
 }

 public void testBundledBusinessDirectoryNativeIsolationSearchAndProvenance()throws Exception{
  try{businessScenario();}catch(Throwable failure){failureEvidence(failure);if(failure instanceof Exception)throw (Exception)failure;throw (Error)failure;}
 }
 private long contactRows(){try(Cursor cursor=target.getContentResolver().query(ContactsContract.RawContacts.CONTENT_URI,new String[]{ContactsContract.RawContacts._ID},null,null,null)){
  assertNotNull(cursor);return cursor.getCount();}}
 private String contactDigest()throws Exception{
  JSONArray values=new JSONArray();String[] columns={ContactsContract.CommonDataKinds.Phone._ID,ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
   ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,ContactsContract.CommonDataKinds.Phone.NUMBER};
  try(Cursor cursor=target.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,columns,null,null,columns[0]+" ASC")){
   assertNotNull(cursor);while(cursor.moveToNext())values.put(new JSONArray().put(cursor.getLong(0)).put(cursor.getLong(1)).put(cursor.getString(2)).put(cursor.getString(3)));}
  byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(values.toString().getBytes(StandardCharsets.UTF_8));
  StringBuilder hex=new StringBuilder();for(byte part:digest)hex.append(String.format(java.util.Locale.ROOT,"%02x",part&255));return hex.toString();
 }
 private BusinessDirectory.Page businessPage(String query){
  assertTrue("Directory I/O must run off the UI thread",android.os.Looper.myLooper()!=android.os.Looper.getMainLooper());return BusinessDirectory.search(target,query,0);
 }
 private AlertDialog businessDialog()throws Exception{
  java.lang.reflect.Field field=MainActivity.class.getDeclaredField("businessDirectory");field.setAccessible(true);Object directory=field.get(main);assertNotNull(directory);
  java.lang.reflect.Field dialog=BusinessDirectoryView.class.getDeclaredField("dialog");dialog.setAccessible(true);return (AlertDialog)dialog.get(directory);
 }
 private void businessScenario()throws Exception{
  long privateBefore=contactRows();String privateDigest=contactDigest();BusinessDirectory.Page initial=businessPage("");
  // A missing/empty/invalid source is a real release blocker. Unit-only fake
  // databases cannot turn this gate into native production-dataset evidence.
  assertEquals("BLOCKED: no verified licensed business asset is bundled",BusinessDirectory.Status.AVAILABLE,initial.status);
  assertNotNull(initial.manifest);assertTrue("BLOCKED: production directory contains no sourced businesses",initial.manifest.recordCount>0);
  assertFalse(initial.entries.isEmpty());assertTrue(initial.entries.size()<=BusinessDirectory.PAGE_SIZE);
  BusinessDirectory.Entry selected=initial.entries.get(0);assertTrue(BusinessDirectory.https(selected.sourceUrl));assertFalse(selected.sourceLicense.isEmpty());assertFalse(selected.sourceUpdatedAt.isEmpty());
  JSONArray palettes=new JSONArray();
  for(boolean dark:new boolean[]{false,true}){
   settings.edit().putBoolean("dark",dark).putBoolean("amoled",false).putString("language","he").commit();startMain("contacts");
   View tab=tag(main,"contacts_directory_tab");assertNotNull("Small entry must be inside personal Contacts",tab);Rect tabBounds=tapView(tab);
   await(()->tag(main,"business_directory_name_"+selected.id)!=null,"Actual licensed dataset must render first page");ready(main);
   View root=tag(main,"business_directory_root");assertNotNull(root);assertEquals(View.LAYOUT_DIRECTION_RTL,root.getLayoutDirection());
   assertEquals(selected.name,text(main,"business_directory_name_"+selected.id));assertEquals(selected.phoneDisplay,text(main,"business_directory_phone_"+selected.id));
   assertEquals(View.TEXT_DIRECTION_LTR,tag(main,"business_directory_phone_"+selected.id).getTextDirection());
   assertNull("Opening public tab must never dial",PhoneSessionService.current);ui(()->textFits(root));
   JSONObject bars=snapshot("business-tab-"+(dark?"dark":"light"),dark);assertTrue(bars.getInt("status")>=40&&bars.getInt("navigation")>=40);
   palettes.put(new JSONObject().put("palette",dark?"dark":"light").put("native_entry_tag","contacts_directory_tab")
    .put("entry_bounds",tabBounds.toShortString()).put("root_rtl",true).put("phone_ltr",true).put("bar_contrast",bars)
    .put("font_scale",main.getResources().getConfiguration().fontScale).put("first_record_id",selected.id));
  }
  EditText query=(EditText)tag(main,"business_directory_search");assertNotNull(query);tapView(query);
  getInstrumentation().sendStringSync(selected.phone);key(KeyEvent.KEYCODE_BACK);
  await(()->selected.phone.contentEquals(((EditText)tag(main,"business_directory_search")).getText())&&tag(main,"business_directory_name_"+selected.id)!=null,
   "Native typed phone query must return the exact source identity");
  BusinessDirectory.Page matched=businessPage(selected.phone);assertEquals(BusinessDirectory.Status.AVAILABLE,matched.status);boolean exact=false;
  for(BusinessDirectory.Entry row:matched.entries)if(row.id==selected.id&&selected.phone.equals(row.phone))exact=true;assertTrue(exact);
  assertNull(PhoneSessionService.current);ui(()->textFits(tag(main,"business_directory_root")));snapshot("business-search",true);
  tapView(tag(main,"business_directory_name_"+selected.id));await(()->businessDialog()!=null&&businessDialog().isShowing(),"Native source row must open its own details");
  AlertDialog dialog=businessDialog();View decor=dialog.getWindow().getDecorView();View title=decor.findViewWithTag("business_directory_detail_title");
  View body=decor.findViewWithTag("business_directory_detail_text");assertTrue(title instanceof TextView);assertTrue(body instanceof TextView);
  assertEquals(selected.name,((TextView)title).getText().toString());String content=((TextView)body).getText().toString();
  assertTrue(content.contains(selected.sourceUrl));assertTrue(content.contains(selected.sourceLicense));assertTrue(content.contains(selected.sourceUpdatedAt));
  assertNull("Inspecting provenance must never dial a real business",PhoneSessionService.current);snapshot("business-detail",true);
  key(KeyEvent.KEYCODE_BACK);ready(main);assertFalse(dialog.isShowing());assertNotNull(tag(main,"business_directory_root"));
  assertEquals(selected.phone,((EditText)tag(main,"business_directory_search")).getText().toString());
  tapView(tag(main,"business_directory_personal_tab"));await(()->tag(main,"contacts_directory_tab")!=null,"Native personal tab must remain available");
  assertNull(tag(main,"business_directory_root"));tapView(tag(main,"contacts_directory_tab"));
  await(()->tag(main,"business_directory_name_"+selected.id)!=null,"Returning to businesses must retain query and exact identity");
  assertEquals(selected.phone,((EditText)tag(main,"business_directory_search")).getText().toString());assertEquals(privateBefore,contactRows());assertEquals(privateDigest,contactDigest());assertNull(PhoneSessionService.current);
  JSONObject checks=new JSONObject();for(String flag:new String[]{"actual_nonempty_licensed_bundled_dataset","native_small_contacts_tab","native_light_dark_rtl_ltr_phone",
   "native_phone_query_keeps_source_identity","details_show_source_license_and_source_date","private_contacts_unchanged","no_automatic_business_call",
   "native_detail_back_and_private_public_switch_preserve_query","bounded_production_pages"})checks.put(flag,true);
  completedBusiness=new JSONObject().put("checks",checks).put("readiness","AVAILABLE").put("dataset",new JSONObject().put("version",initial.manifest.datasetVersion)
   .put("record_count",initial.manifest.recordCount).put("sha256",initial.manifest.sha256).put("source_name",initial.manifest.sourceName)
   .put("source_url",initial.manifest.sourceUrl).put("license",initial.manifest.license).put("license_url",initial.manifest.licenseUrl).put("updated_at",initial.manifest.updatedAt))
   .put("selected_record",new JSONObject().put("id",selected.id).put("name",selected.name).put("phone",selected.phone).put("source_url",selected.sourceUrl)
    .put("source_id",selected.sourceId).put("source_license",selected.sourceLicense).put("source_updated_at",selected.sourceUpdatedAt))
   .put("first_page_rows",initial.entries.size()).put("search_rows",matched.entries.size()).put("private_contacts_before",privateBefore).put("private_contacts_after",contactRows())
   .put("private_contacts_digest_before",privateDigest).put("private_contacts_digest_after",contactDigest())
   .put("palettes",palettes).put("query",selected.phone).put("detail_text",content).put("business_numbers_dialed",0);writeCombined();
 }
 private void writeCombined()throws Exception{
  // Preserve independently measured reminder proof when data acquisition blocks
  // the separate business test; partial evidence never satisfies the full gate.
  if(completedReminder!=null)writeFile("callback-reminder-native.json",completedReminder.toString(2));
  if(completedBusiness!=null)writeFile("business-directory-native.json",completedBusiness.toString(2));
  if(completedReminder==null||completedBusiness==null)return;
  writeFile("callback-business-native.json",new JSONObject().put("api",33).put("size","480x854").put("density_dpi",240).put("font_scale",1.3)
   .put("physical_cellular_audio_verified",false).put("data_origin","Actual Android Telecom emulator modem calls and call-log rows; production private OS alarm; immutable licensed packaged business dataset")
   .put("reminder",completedReminder).put("business",completedBusiness).toString(2));
 }
 private void failureEvidence(Throwable failure)throws Exception{
  writeFile("callback-business-failure-"+getName()+".json",new JSONObject().put("test",getName()).put("failure",String.valueOf(failure)).put("last_action",lastAction)
   .put("modem_commands",modemCommands).put("calls",calls).put("events",events).put("activities",shell("dumpsys activity activities"))
   .put("windows",shell("dumpsys window windows")).put("alarms",shell("dumpsys alarm")).put("notifications",shell("dumpsys notification --noredact"))
   .put("recent_logcat",shell("logcat -d -t 500")).toString(2));snapshot("callback-business-failed-"+getName(),false);
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
