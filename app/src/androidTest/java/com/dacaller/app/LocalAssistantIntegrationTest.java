package com.dacaller.app;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
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
import android.telecom.Call;
import android.telecom.TelecomManager;
import android.test.InstrumentationTestCase;
import android.test.InstrumentationTestRunner;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.inspector.WindowInspector;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/** Real production windows and providers on a disposable emulator. Seeded provider rows are
 * explicit isolated fixtures, never represented as owner data. Only the final confirmed
 * numeric proposal reaches actual Android Telecom. No physical audio/upgrade claim. */
@SuppressWarnings({"deprecation","unchecked"})
@android.annotation.TargetApi(33)
public final class LocalAssistantIntegrationTest extends InstrumentationTestCase {
 private static final String DIRECTORY="/sdcard/Download/DA-CALLER-assistant-snapshots";
 private static final String FIRST="0500099951",SECOND="0500099952",PREFIX="DA Assistant Fixture";
 private Context target;private SharedPreferences prefs;private Map<String,?> original,originalNavigation;
 private String originalFont,originalDialer,originalContactOp,originalLogOp;private int originalAccessibilityFlags;
 private boolean runtimeDenied,failed,confirmedCall,telecomShellObservationUsed;private MainActivity main;private CallScreenActivity callScreen;
 private long started;private Call ownedCall;private String ownedToken;private int confirmedRowsDeleted;
 private List<CallHistory.Entry> originalCalls;private CallHistory.Summary originalSummary;private boolean originalReadFailed;private long originalFetchedAt;
 private final List<Long> rawContacts=new ArrayList<>(),fixtureCallRows=new ArrayList<>();
 private final JSONArray pointers=new JSONArray();private final JSONObject captures=new JSONObject();private JSONObject result;
 private Application application;
 private final Application.ActivityLifecycleCallbacks callbacks=new Application.ActivityLifecycleCallbacks(){
  public void onActivityCreated(Activity a,android.os.Bundle b){}
  public void onActivityStarted(Activity a){}
  public void onActivityResumed(Activity a){if(a instanceof CallScreenActivity)callScreen=(CallScreenActivity)a;}
  public void onActivityPaused(Activity a){}
  public void onActivityStopped(Activity a){}
  public void onActivitySaveInstanceState(Activity a,android.os.Bundle b){}
  public void onActivityDestroyed(Activity a){}
 };
 @Override protected void setUp()throws Exception{
  super.setUp();target=getInstrumentation().getTargetContext();
  assertEquals("Refuse real phones and private device data","1",shell("getprop ro.kernel.qemu").trim());assertEquals(33,android.os.Build.VERSION.SDK_INT);
  assertEquals(240,target.getResources().getDisplayMetrics().densityDpi);
  runtimeDenied="runtime-denied".equals(((InstrumentationTestRunner)getInstrumentation()).getArguments().getString("assistant_phase","main"));
  prefs=target.getSharedPreferences("da_caller",0);original=prefs.getAll();originalNavigation=target.getSharedPreferences(CallReturnNavigation.PREFERENCES,0).getAll();
  CallHistory.worker.submit(()->{}).get(5,TimeUnit.SECONDS);getInstrumentation().waitForIdleSync();
  originalCalls=CallHistory.entries;originalSummary=CallHistory.summary;originalReadFailed=CallHistory.readFailed;originalFetchedAt=CallHistory.fetchedAt;
  originalFont=shell("settings get system font_scale").trim();originalDialer=target.getSystemService(TelecomManager.class).getDefaultDialerPackage();
  originalContactOp=shell("cmd appops get "+target.getPackageName()+" READ_CONTACTS");originalLogOp=shell("cmd appops get "+target.getPackageName()+" READ_CALL_LOG");
  AccessibilityServiceInfo info=getInstrumentation().getUiAutomation().getServiceInfo();originalAccessibilityFlags=info.flags;
  info.flags|=AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS|AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;getInstrumentation().getUiAutomation().setServiceInfo(info);
  application=(Application)target.getApplicationContext();application.registerActivityLifecycleCallbacks(callbacks);
  shell("mkdir -p "+DIRECTORY);shell("settings put system font_scale 1.3");shell("cmd statusbar collapse");
  if(runtimeDenied){assertEquals(PackageManager.PERMISSION_DENIED,target.checkSelfPermission(Manifest.permission.READ_CONTACTS));
   assertEquals(PackageManager.PERMISSION_DENIED,target.checkSelfPermission(Manifest.permission.READ_CALL_LOG));}
  else{
   shell("cmd role add-role-holder --user 0 android.app.role.DIALER "+target.getPackageName());
   await(()->target.getSystemService(RoleManager.class).isRoleHeld(RoleManager.ROLE_DIALER),"Default dialer role required");
   for(String p:new String[]{Manifest.permission.READ_CONTACTS,Manifest.permission.WRITE_CONTACTS,Manifest.permission.READ_CALL_LOG,Manifest.permission.WRITE_CALL_LOG,
     Manifest.permission.CALL_PHONE,Manifest.permission.READ_PHONE_STATE,Manifest.permission.POST_NOTIFICATIONS})shell("pm grant "+target.getPackageName()+" "+p);
   appOp("READ_CONTACTS","allow");appOp("READ_CALL_LOG","allow");
  }
  assertNull("Never replace another Telecom call",PhoneSessionService.current);assertFalse(telecomInCall());
  prefs.edit().putBoolean("role_prompted",true).putBoolean("notifications_prompted",true).putBoolean("voice_permission_prompted",true)
   .putBoolean("haptics",false).putBoolean("island_enabled",false).putBoolean("island_outside_app",false).putString(CallArchive.PREF,CallArchive.OFF)
   .putString("start_page","contacts").putString("language","en").putBoolean("dark",false).putBoolean("amoled",false).commit();
  started=System.currentTimeMillis();
 }
 @Override protected void runTest()throws Throwable{
  try{super.runTest();}catch(Throwable error){failed=true;try{capture("assistant-failed-"+getName()+".png");}catch(Throwable diagnostic){error.addSuppressed(diagnostic);}throw error;}
 }
 private interface Cleanup{void run()throws Exception;}
 private void cleanup(Cleanup action,List<Throwable> errors){try{action.run();}catch(Throwable e){errors.add(e);}}
 @Override protected void tearDown()throws Exception{
  List<Throwable> errors=new ArrayList<>();
  if(!runtimeDenied){cleanup(()->appOp("READ_CONTACTS","allow"),errors);cleanup(()->appOp("READ_CALL_LOG","allow"),errors);}
  if(confirmedCall)cleanup(()->{
   Call active=PhoneSessionService.current;if(active!=null){if(ownedCall!=null)assertSame(ownedCall,active);assertEquals(FIRST,active.getDetails().getHandle().getSchemeSpecificPart());
    CallIslandState snapshot=PhoneSessionService.islandSnapshot();assertNotNull(snapshot);if(ownedToken!=null)assertEquals(ownedToken,snapshot.token);else ownedToken=snapshot.token;
    ui(()->assertTrue(PhoneSessionService.performIslandAction(ownedToken,"END")));}
   await(()->PhoneSessionService.current==null&&PhoneSessionService.instance==null,"Owned Telecom service must unbind");
  },errors);
  if(callScreen!=null)cleanup(()->ui(callScreen::finish),errors);if(main!=null)cleanup(()->ui(main::finish),errors);cleanup(this::settle,errors);
  if(!runtimeDenied){
   for(long id:rawContacts)cleanup(()->assertEquals(1,target.getContentResolver().delete(ContactsContract.RawContacts.CONTENT_URI.buildUpon()
    .appendPath(String.valueOf(id)).appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER,"true").build(),null,null)),errors);
   for(long id:fixtureCallRows)cleanup(()->assertEquals(1,target.getContentResolver().delete(Uri.withAppendedPath(CallLog.Calls.CONTENT_URI,String.valueOf(id)),null,null)),errors);
   if(confirmedCall)cleanup(()->{
    JSONArray ownedRows=newOutgoingRows();assertEquals("Only one owned confirmed call may be cleaned up",1,ownedRows.length());
    JSONObject row=ownedRows.getJSONObject(0);assertEquals(FIRST,row.getString("number"));assertEquals(CallLog.Calls.OUTGOING_TYPE,row.getInt("type"));
    int deleted=target.getContentResolver().delete(Uri.withAppendedPath(CallLog.Calls.CONTENT_URI,String.valueOf(row.getLong("id"))),
     CallLog.Calls.NUMBER+"=? AND "+CallLog.Calls.TYPE+"=? AND "+CallLog.Calls.DATE+">=?",
     new String[]{FIRST,String.valueOf(CallLog.Calls.OUTGOING_TYPE),String.valueOf(started)});
    assertEquals("Delete only this native-confirmed call row",1,deleted);confirmedRowsDeleted=deleted;
   },errors);
  }
  if(!runtimeDenied){cleanup(()->restoreOp("READ_CONTACTS",originalContactOp),errors);cleanup(()->restoreOp("READ_CALL_LOG",originalLogOp),errors);}
  else{cleanup(()->assertOp("READ_CONTACTS",originalContactOp),errors);cleanup(()->assertOp("READ_CALL_LOG",originalLogOp),errors);}
  cleanup(()->{restore(prefs,original);assertEquals(original,prefs.getAll());},errors);
  cleanup(()->{SharedPreferences navigation=target.getSharedPreferences(CallReturnNavigation.PREFERENCES,0);restore(navigation,originalNavigation);assertEquals(originalNavigation,navigation.getAll());},errors);
  cleanup(()->{CallHistory.worker.submit(()->{}).get(5,TimeUnit.SECONDS);getInstrumentation().waitForIdleSync();
   CallHistory.entries=originalCalls;CallHistory.summary=originalSummary;CallHistory.readFailed=originalReadFailed;CallHistory.fetchedAt=originalFetchedAt;},errors);
  cleanup(()->shell("settings put system font_scale "+(originalFont.matches("[0-9.]+")?originalFont:"1.0")),errors);
  cleanup(()->{if(!runtimeDenied&&!target.getPackageName().equals(originalDialer)){
   shell("cmd role remove-role-holder --user 0 android.app.role.DIALER "+target.getPackageName()+" 1");
   if(originalDialer!=null&&originalDialer.matches("[A-Za-z0-9_.]+"))shell("cmd role add-role-holder --user 0 android.app.role.DIALER "+originalDialer+" 1");}},errors);
  application.unregisterActivityLifecycleCallbacks(callbacks);
  cleanup(()->{AccessibilityServiceInfo info=getInstrumentation().getUiAutomation().getServiceInfo();info.flags=originalAccessibilityFlags;getInstrumentation().getUiAutomation().setServiceInfo(info);},errors);
  if(!failed&&errors.isEmpty()&&result!=null){
   result.put("preferences_restored",prefs.getAll().equals(original)).put("navigation_preferences_restored",target.getSharedPreferences(CallReturnNavigation.PREFERENCES,0).getAll().equals(originalNavigation))
    .put("appops_restored",originalMode(shell("cmd appops get "+target.getPackageName()+" READ_CONTACTS"),"READ_CONTACTS").equals(originalMode(originalContactOp,"READ_CONTACTS"))&&originalMode(shell("cmd appops get "+target.getPackageName()+" READ_CALL_LOG"),"READ_CALL_LOG").equals(originalMode(originalLogOp,"READ_CALL_LOG")))
    .put("call_history_cache_restored",CallHistory.entries==originalCalls&&CallHistory.summary==originalSummary&&CallHistory.readFailed==originalReadFailed&&CallHistory.fetchedAt==originalFetchedAt)
    .put("fixture_contacts_deleted",rawContacts.size()).put("fixture_call_rows_deleted",fixtureCallRows.size()).put("confirmed_call_rows_deleted",confirmedRowsDeleted)
    .put("cleanup_completed",true).put("pointers",pointers).put("captures",captures).put("api",33).put("density_dpi",240).put("font_scale",1.3)
    .put("package",target.getPackageName()).put("version_name",BuildConfig.VERSION_NAME).put("version_code",BuildConfig.VERSION_CODE).put("method",getName())
    .put("phase",runtimeDenied?"runtime-denied":"main").put("fixture_scope","Isolated disposable-emulator provider rows; no owner data")
    .put("physical_carrier_audio_verified",false).put("matching_key_upgrade_verified",false).put("text_input_method","Production EditText.setText on UI thread; real native pointer for actions");
   write("assistant-native-"+getName()+"-"+(runtimeDenied?"runtime-denied":"main")+".json",result.toString(2));
  }
  super.tearDown();if(!errors.isEmpty()){AssertionError error=new AssertionError("Assistant cleanup failed");for(Throwable e:errors)error.addSuppressed(e);throw error;}
 }
 private interface Check{boolean ready()throws Exception;}
 private void await(Check c,String message)throws Exception{long deadline=SystemClock.uptimeMillis()+12000;do{getInstrumentation().waitForIdleSync();if(c.ready())return;Thread.sleep(60);}while(SystemClock.uptimeMillis()<deadline);fail(message);}
 private void ui(Runnable action)throws Exception{AtomicReference<Throwable> error=new AtomicReference<>();getInstrumentation().runOnMainSync(()->{try{action.run();}catch(Throwable e){error.set(e);}});if(error.get()!=null)throw new AssertionError(error.get());}
 private void settle()throws Exception{getInstrumentation().waitForIdleSync();Thread.sleep(220);getInstrumentation().waitForIdleSync();}
 private String shell(String command)throws Exception{try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(getInstrumentation().getUiAutomation().executeShellCommand(command));ByteArrayOutputStream out=new ByteArrayOutputStream()){
  byte[] data=new byte[4096];for(int n;(n=in.read(data))!=-1;)out.write(data,0,n);return out.toString("UTF-8");}}
 private void appOp(String name,String mode)throws Exception{shell("cmd appops set "+target.getPackageName()+" "+name+" "+mode);}
 private String originalMode(String record,String op){
  Matcher matcher=Pattern.compile(Pattern.quote(op)+":\\s*(allow|ignore|deny|default|foreground)\\b").matcher(record);Set<String> modes=new HashSet<>();while(matcher.find())modes.add(matcher.group(1));
  if(modes.isEmpty()){assertTrue("Actual AppOps snapshot must be explicit",record.trim().equals("No operations.")||record.trim().equals("No operations")||record.trim().equals("No operations defined."));return "default";}
  assertEquals("AppOps snapshot must not contain conflicting modes",1,modes.size());return modes.iterator().next();
 }
 private void restoreOp(String operation,String originalRecord)throws Exception{
  String expected=originalMode(originalRecord,operation);appOp(operation,expected);
  assertOp(operation,originalRecord);
 }
 private void assertOp(String operation,String originalRecord)throws Exception{
  String expected=originalMode(originalRecord,operation);
  assertEquals("Actual AppOps restoration must be observed",expected,originalMode(shell("cmd appops get "+target.getPackageName()+" "+operation),operation));
 }
 private void restore(SharedPreferences p,Map<String,?> values){SharedPreferences.Editor e=p.edit().clear();for(Map.Entry<String,?> v:values.entrySet()){
  Object x=v.getValue();if(x instanceof String)e.putString(v.getKey(),(String)x);else if(x instanceof Boolean)e.putBoolean(v.getKey(),(Boolean)x);else if(x instanceof Integer)e.putInt(v.getKey(),(Integer)x);
  else if(x instanceof Long)e.putLong(v.getKey(),(Long)x);else if(x instanceof Float)e.putFloat(v.getKey(),(Float)x);else if(x instanceof java.util.Set)e.putStringSet(v.getKey(),(java.util.Set<String>)x);}assertTrue(e.commit());}
 private int dp(int n){return Math.round(n*target.getResources().getDisplayMetrics().density);}
 private void launch(boolean dark,boolean amoled,String language)throws Exception{
  prefs.edit().putBoolean("dark",dark).putBoolean("amoled",amoled).putString("language",language).commit();
  main=(MainActivity)getInstrumentation().startActivitySync(new Intent(target,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));settle();
 }
 private View find(View root,String tag){if(tag.equals(root.getTag()))return root;if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++){View v=find(((ViewGroup)root).getChildAt(n),tag);if(v!=null)return v;}return null;}
 private void collect(View root,String tag,List<View> out){if(tag.equals(root.getTag()))out.add(root);if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++)collect(((ViewGroup)root).getChildAt(n),tag,out);}
 private View windowTag(String tag){for(View root:WindowInspector.getGlobalWindowViews()){View v=find(root,tag);if(v!=null&&v.isShown())return v;}return null;}
 private List<View> tagged(String tag){List<View> out=new ArrayList<>();for(View root:WindowInspector.getGlobalWindowViews())if(root.isShown())collect(root,tag,out);return out;}
 private View requireTag(String tag)throws Exception{await(()->windowTag(tag)!=null,"Actual native view missing: "+tag);return windowTag(tag);}
 private boolean hasText(View v,String expected){if(v instanceof TextView&&((TextView)v).getText().toString().contains(expected))return true;
  if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++)if(hasText(((ViewGroup)v).getChildAt(n),expected))return true;return false;}
 private boolean textInWindows(String expected){for(View root:WindowInspector.getGlobalWindowViews())if(root.isShown()&&hasText(root,expected))return true;return false;}
 private Rect geometry(View view)throws Exception{
  AtomicReference<Rect> last=new AtomicReference<>(),accepted=new AtomicReference<>();
  await(()->{AtomicReference<Rect> candidate=new AtomicReference<>();ui(()->{
   if(!view.isShown()||!view.isAttachedToWindow()||!view.hasWindowFocus()||view.isLayoutRequested()||view.getRootView().isLayoutRequested()||view.getWidth()<=0||view.getHeight()<=0)return;
   int[] xy=new int[2],rs=new int[2],rw=new int[2];view.getLocationOnScreen(xy);View root=view.getRootView();root.getLocationOnScreen(rs);root.getLocationInWindow(rw);
   Rect physical=new Rect(xy[0],xy[1],xy[0]+view.getWidth(),xy[1]+view.getHeight()),visible=new Rect();
   if(!view.getGlobalVisibleRect(visible)||visible.width()!=view.getWidth()||visible.height()!=view.getHeight())return;visible.offset(rs[0]-rw[0],rs[1]-rw[1]);
   AccessibilityNodeInfo node=view.createAccessibilityNodeInfo();try{Rect ax=new Rect();node.getBoundsInScreen(ax);if(!physical.equals(visible)||!physical.equals(ax)||!new Rect(0,0,480,782).contains(physical))return;
    for(AccessibilityWindowInfo window:getInstrumentation().getUiAutomation().getWindows())try{
     if(window.getId()==node.getWindowId()&&window.isFocused()&&window.isActive())candidate.set(physical);
    }finally{window.recycle();}
   }finally{node.recycle();}
  });Rect r=candidate.get();if(r==null){last.set(null);return false;}if(!r.equals(last.get())){last.set(r);return false;}accepted.set(r);return true;},"Native target must have full stable exact physical/global/AX bounds");return accepted.get();
 }
 private void reveal(View view)throws Exception{ui(()->view.requestRectangleOnScreen(new Rect(0,0,view.getWidth(),view.getHeight()),true));settle();}
 private JSONObject tap(View view)throws Exception{
  reveal(view);Rect r=geometry(view);assertTrue("At least48dp native action",r.width()>=dp(48)&&r.height()>=dp(48));long time=SystemClock.uptimeMillis();
  for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){MotionEvent e=MotionEvent.obtain(time,SystemClock.uptimeMillis(),action,r.centerX(),r.centerY(),0);
   e.setSource(InputDevice.SOURCE_TOUCHSCREEN);try{assertTrue(getInstrumentation().getUiAutomation().injectInputEvent(e,true));}finally{e.recycle();}}
  JSONObject point=new JSONObject().put("bounds",r.toShortString()).put("x",r.centerX()).put("y",r.centerY()).put("full_native_ax_geometry",true).put("native_pointer",true);pointers.put(point);settle();return point;
 }
 private void open()throws Exception{tap(requireTag("main_assistant_button"));requireTag("assistant_input");assertNotNull(requireTag("assistant_dialog_body"));}
 private LinearLayout query(String value)throws Exception{
  EditText input=(EditText)requireTag("assistant_input");ui(()->input.setText(value));tap(requireTag("assistant_submit"));
  await(()->{View r=windowTag("assistant_results");return r instanceof LinearLayout&&((LinearLayout)r).getChildCount()>0&&!hasText(r,"Searching device")&&!hasText(r,"מחפש במכשיר");},"Actual query must complete");return (LinearLayout)requireTag("assistant_results");
 }
 private boolean telecomInCall(){
  boolean adopted=false;
  try{
   if(target.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED){
    getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(Manifest.permission.READ_PHONE_STATE);adopted=true;telecomShellObservationUsed=true;
   }
   // No provider read or UI action occurs under this observation-only identity.
   return target.getSystemService(TelecomManager.class).isInCall();
  }finally{
   if(adopted)getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
   if(runtimeDenied){assertEquals(PackageManager.PERMISSION_DENIED,target.checkSelfPermission(Manifest.permission.READ_CONTACTS));
    assertEquals(PackageManager.PERMISSION_DENIED,target.checkSelfPermission(Manifest.permission.READ_CALL_LOG));}
  }
 }
 private void noCall()throws Exception{assertNull("No unconfirmed Telecom call",PhoneSessionService.current);assertFalse(telecomInCall());if(!runtimeDenied)assertEquals(0,newOutgoingRows().length());}
 private JSONArray newOutgoingRows(){JSONArray rows=new JSONArray();try(Cursor c=target.getContentResolver().query(CallLog.Calls.CONTENT_URI,new String[]{CallLog.Calls._ID,CallLog.Calls.NUMBER,CallLog.Calls.TYPE},CallLog.Calls.DATE+">=? AND "+CallLog.Calls.TYPE+"=?",new String[]{String.valueOf(started),String.valueOf(CallLog.Calls.OUTGOING_TYPE)},null)){
  assertNotNull(c);while(c.moveToNext())rows.put(new JSONObject().put("id",c.getLong(0)).put("number",c.getString(1)).put("type",c.getInt(2)));}catch(org.json.JSONException e){throw new AssertionError(e);}return rows;}
 private void contact(String name,String number)throws Exception{
  ArrayList<ContentProviderOperation> ops=new ArrayList<>();ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI).withValue(ContactsContract.RawContacts.ACCOUNT_TYPE,null).withValue(ContactsContract.RawContacts.ACCOUNT_NAME,null).build());
  ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI).withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID,0).withValue(ContactsContract.Data.MIMETYPE,ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE).withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,name).build());
  ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI).withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID,0).withValue(ContactsContract.Data.MIMETYPE,ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE).withValue(ContactsContract.CommonDataKinds.Phone.NUMBER,number).withValue(ContactsContract.CommonDataKinds.Phone.TYPE,ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE).build());
  ContentProviderResult[] created=target.getContentResolver().applyBatch(ContactsContract.AUTHORITY,ops);rawContacts.add(Long.parseLong(created[0].uri.getLastPathSegment()));
 }
 private void callRow(String number,int type,long age)throws Exception{android.content.ContentValues values=new android.content.ContentValues();values.put(CallLog.Calls.NUMBER,number);values.put(CallLog.Calls.TYPE,type);values.put(CallLog.Calls.DATE,started-age);values.put(CallLog.Calls.DURATION,0);values.put(CallLog.Calls.NEW,0);values.put(CallLog.Calls.IS_READ,1);Uri uri=target.getContentResolver().insert(CallLog.Calls.CONTENT_URI,values);assertNotNull(uri);fixtureCallRows.add(Long.parseLong(uri.getLastPathSegment()));}
 private void capture(String name)throws Exception{
  settle();Bitmap image=getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(image);try{assertEquals(480,image.getWidth());assertEquals(854,image.getHeight());ByteArrayOutputStream out=new ByteArrayOutputStream();assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));byte[] bytes=out.toByteArray();
   MessageDigest hash=MessageDigest.getInstance("SHA-256");StringBuilder hex=new StringBuilder();for(byte b:hash.digest(bytes))hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
   File local=new File(target.getExternalFilesDir(null),name);try(FileOutputStream file=new FileOutputStream(local)){file.write(bytes);}shell("cp "+local.getAbsolutePath()+" "+DIRECTORY+"/"+name);
   captures.put(name,new JSONObject().put("sha256",hex.toString()).put("width",480).put("height",854).put("method","UiAutomation.takeScreenshot"));
  }finally{image.recycle();}
 }
 private void write(String name,String value)throws Exception{File local=new File(target.getExternalFilesDir(null),name);try(FileOutputStream out=new FileOutputStream(local)){out.write(value.getBytes(StandardCharsets.UTF_8));}shell("cp "+local.getAbsolutePath()+" "+DIRECTORY+"/"+name);}
 private JSONObject palette()throws Exception{
  View body=requireTag("assistant_dialog_body");EditText input=(EditText)requireTag("assistant_input");AtomicReference<JSONObject> value=new AtomicReference<>();
  ui(()->{try{boolean amoled=prefs.getBoolean("amoled",false),dark=prefs.getBoolean("dark",false)||amoled,rtl="he".equals(prefs.getString("language","he"));int color=((ColorDrawable)body.getBackground()).getColor();
   assertEquals(rtl?View.LAYOUT_DIRECTION_RTL:View.LAYOUT_DIRECTION_LTR,body.getLayoutDirection());assertEquals(NexPalette.background(dark,amoled),color);
   assertFalse(input.isSaveEnabled());assertTrue((input.getImeOptions()&android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)!=0);
   value.set(new JSONObject().put("rtl",rtl).put("dark",dark).put("amoled",amoled).put("background_argb",color).put("input_state_saving_disabled",true)
    .put("ime_personalized_learning_request_disabled",true).put("external_keyboard_retention","NOT_TESTED"));
  }catch(Exception error){throw new RuntimeException(error);}});return value.get();
 }
 private JSONObject base(String scope)throws Exception{return new JSONObject().put("scope",scope).put("emulator_only",true).put("no_network_permission",target.getPackageManager().checkPermission(Manifest.permission.INTERNET,target.getPackageName())==PackageManager.PERMISSION_DENIED)
  .put("telecom_shell_identity_used",telecomShellObservationUsed).put("telecom_state_observation",telecomShellObservationUsed?
   "READ_PHONE_STATE-only UiAutomation shell identity for Telecom status observation; dropped before provider reads/actions":"Target READ_PHONE_STATE permission");}

 public void testAssistantProductionSearchDisambiguationAndCancel()throws Exception{
  contact(PREFIX+" Alpha",FIRST);contact(PREFIX+" Alpine",SECOND);launch(false,false,"en");open();capture("assistant-entry-light-en.png");
  LinearLayout rows=query("find contact "+PREFIX);assertEquals(2,tagged("assistant_result_row").size());assertEquals(2,tagged("assistant_select_call").size());
  assertTrue(hasText(rows,FIRST));assertTrue(hasText(rows,SECOND));noCall();reveal(tagged("assistant_select_call").get(1));capture("assistant-contact-matches-light-en.png");
  tap(tagged("assistant_select_call").get(0));requireTag("assistant_confirmation");noCall();capture("assistant-confirmation-cancel-light-en.png");
  tap(requireTag("assistant_cancel_call"));await(()->windowTag("assistant_confirmation")==null,"Cancel must dismiss only confirmation");noCall();assertNotNull(requireTag("assistant_input"));
  EditText input=(EditText)requireTag("assistant_input");ui(()->input.setText("find contact unmatched-fixture-never-exists"));await(()->tagged("assistant_select_call").isEmpty(),"Edited text must invalidate old results");noCall();
  result=base("Production toolbar; real provider contact fixtures; explicit disambiguation/cancel").put("palette",palette()).put("fixture_contacts",2).put("actual_contact_rows",2).put("no_call_before_confirmation",true).put("cancel_did_not_dial",true).put("edit_invalidated_results",true);
 }
 public void testAssistantLocalHistoryBusinessAndUnsupportedCommands()throws Exception{
  callRow(FIRST,CallLog.Calls.MISSED_TYPE,0);callRow(SECOND,CallLog.Calls.INCOMING_TYPE,1);launch(true,false,"he");open();
  LinearLayout rows=query("שיחות שלא נענו");assertTrue(hasText(rows,FIRST));assertFalse(hasText(rows,SECOND));noCall();reveal(tagged("assistant_result_row").get(0));capture("assistant-history-dark-he.png");
  rows=query("חזור למתקשר האחרון");assertTrue(hasText(rows,FIRST));assertFalse(hasText(rows,SECOND));assertEquals(1,tagged("assistant_select_call").size());noCall();
  BusinessDirectory.Page businesses=BusinessDirectory.search(target,"1599",0);assertEquals(BusinessDirectory.Status.AVAILABLE,businesses.status);assertFalse(businesses.entries.isEmpty());
  rows=query("find business 1599");assertTrue(hasText(rows,businesses.entries.get(0).name));assertTrue(hasText(rows,businesses.manifest.sourceUrl));assertTrue(hasText(rows,businesses.manifest.license));noCall();reveal(tagged("assistant_result_row").get(0));capture("assistant-business-dark-he.png");
  rows=query("send a message automatically");assertTrue(tagged("assistant_select_call").isEmpty());assertTrue(hasText(rows,"לא זוהתה")||hasText(rows,"Unsupported"));noCall();capture("assistant-unsupported-dark-he.png");
  rows=query("call ０５０１２３４５６７");assertTrue(tagged("assistant_select_call").isEmpty());assertTrue(hasText(rows,"לא זוהתה")||hasText(rows,"Unsupported"));noCall();
  assertEquals(View.LAYOUT_DIRECTION_RTL,requireTag("assistant_dialog_body").getLayoutDirection());
  result=base("Real call-log fixtures and packaged licensed directory; no cloud/model inference").put("palette",palette()).put("fixture_call_rows",2).put("missed_filter_exact",true).put("callback_latest_incoming_or_missed",true)
   .put("business_source_url",businesses.manifest.sourceUrl).put("business_license",businesses.manifest.license).put("business_source_date",businesses.manifest.updatedAt)
   .put("unsupported_honest",true).put("unicode_numeric_dial_refused",true).put("rtl",true).put("dark",true).put("no_unconfirmed_call",true);
 }
 public void testAssistantEffectivePermissionRevocationAndRuntimeDeniedPhase()throws Exception{
  if(runtimeDenied){
   launch(false,false,"en");open();LinearLayout rows=query("find contact "+PREFIX);assertTrue(hasText(rows,"Permission")||hasText(rows,"הרשאה"));assertTrue(tagged("assistant_select_call").isEmpty());noCall();
   rows=query("missed calls");assertTrue(hasText(rows,"Permission")||hasText(rows,"הרשאה"));assertTrue(tagged("assistant_select_call").isEmpty());noCall();capture("assistant-runtime-permissions-denied.png");
   result=base("Actual runtime flags revoked by host BETWEEN instrumentation processes").put("palette",palette()).put("read_contacts_granted",false).put("read_call_log_granted",false).put("no_sensitive_results",true).put("no_unconfirmed_call",true).put("live_same_process_runtime_revoke","NOT_TESTED_PROCESS_MAY_BE_KILLED");return;
  }
  contact(PREFIX+" Revoked",FIRST);launch(true,true,"he");open();query("find contact "+PREFIX);assertFalse(tagged("assistant_select_call").isEmpty());noCall();
  View old=tagged("assistant_select_call").get(0);appOp("READ_CONTACTS","ignore");assertEquals(PackageManager.PERMISSION_GRANTED,target.checkSelfPermission(Manifest.permission.READ_CONTACTS));
  assertEquals(android.app.AppOpsManager.MODE_IGNORED,target.getSystemService(android.app.AppOpsManager.class).noteOpNoThrow(android.app.AppOpsManager.OPSTR_READ_CONTACTS,android.os.Process.myUid(),target.getPackageName()));
  tap(old);await(()->tagged("assistant_select_call").isEmpty(),"Effective provider revocation must clear cached actions");assertNull(windowTag("assistant_confirmation"));noCall();capture("assistant-permission-revoked-amoled-he.png");
  appOp("READ_CONTACTS","allow");query("find contact "+PREFIX);tap(tagged("assistant_select_call").get(0));requireTag("assistant_confirmation");appOp("READ_CONTACTS","ignore");tap(requireTag("assistant_confirm_call"));noCall();
  appOp("READ_CONTACTS","allow");appOp("READ_CALL_LOG","ignore");query("missed calls");assertTrue(tagged("assistant_select_call").isEmpty());appOp("READ_CALL_LOG","allow");noCall();
  assertEquals(View.LAYOUT_DIRECTION_RTL,requireTag("assistant_dialog_body").getLayoutDirection());
  result=base("Live AppOps effective provider access revocation; runtime flag remains granted").put("palette",palette()).put("runtime_read_contacts_flag_remained_granted",true).put("appops_denial_checked",true)
   .put("cached_selection_refused",true).put("pending_confirmation_refused",true).put("call_log_effective_denial_refused",true).put("no_unconfirmed_call",true).put("rtl",true).put("amoled",true)
   .put("live_same_process_runtime_revoke","NOT_TESTED_PROCESS_MAY_BE_KILLED");
 }
 public void testAssistantExplicitConfirmedCallUsesActualTelecom()throws Exception{
  launch(true,true,"he");open();query("call "+FIRST);noCall();tap(requireTag("assistant_select_call"));requireTag("assistant_confirmation");noCall();capture("assistant-confirmation-amoled-he.png");JSONObject actualPalette=palette();
  confirmedCall=true;tap(requireTag("assistant_confirm_call"));await(()->PhoneSessionService.current!=null&&PhoneSessionService.current.getDetails()!=null&&PhoneSessionService.current.getDetails().getHandle()!=null,"Confirmed production action must reach Android Telecom");
  ownedCall=PhoneSessionService.current;assertEquals(FIRST,ownedCall.getDetails().getHandle().getSchemeSpecificPart());assertTrue(ownedCall.getState()==Call.STATE_DIALING||ownedCall.getState()==Call.STATE_CONNECTING||ownedCall.getState()==Call.STATE_ACTIVE);
  CallIslandState state=PhoneSessionService.islandSnapshot();assertNotNull(state);ownedToken=state.token;assertTrue(PhoneSessionService.commandMatches(ownedCall,ownedToken));
  await(()->callScreen!=null&&find(callScreen.getWindow().getDecorView(),"call-end")!=null,"Actual production call screen required");capture("assistant-real-call-confirmed.png");
  tap(find(callScreen.getWindow().getDecorView(),"call-end"));await(()->ownedCall.getState()==Call.STATE_DISCONNECTED&&PhoneSessionService.current==null&&PhoneSessionService.instance==null,"Native hangup must end exact owned call");
  await(()->newOutgoingRows().length()==1,"Telecom must persist exactly one real outgoing row");JSONArray rows=newOutgoingRows();assertEquals(FIRST,rows.getJSONObject(0).getString("number"));
  result=base("Explicit native confirmation -> production dialFromList -> actual Android Telecom").put("palette",actualPalette).put("number",FIRST).put("telecom_handle",FIRST).put("token",ownedToken)
   .put("actual_outgoing_rows",rows).put("no_call_before_confirmation",true).put("same_call_ended_by_native_button",true).put("exactly_one_confirmed_call",true).put("amoled",true).put("rtl",true);
 }
}
