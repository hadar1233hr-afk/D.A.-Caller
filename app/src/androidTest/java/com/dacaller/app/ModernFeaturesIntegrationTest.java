package com.dacaller.app;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.NotificationManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Person;
import android.appwidget.AppWidgetManager;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.ContentObserver;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.Settings;
import android.provider.ContactsContract;
import android.provider.CallLog;
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
import android.widget.LinearLayout;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 private final JSONArray nativeAnswers=new JSONArray(),foregroundEvidence=new JSONArray();
 private final JSONArray nativeDragRouting=new JSONArray();
 private String launcherPackage;
 private JSONObject lastForegroundProbe;

 @Override protected void runTest()throws Throwable {
  try{super.runTest();}
  catch(Throwable failure){
   // Retain the actual failed surface before tearDown closes SystemUI. These
   // diagnostic-only names are outside the strict 27-frame passing profile.
   if(lastForegroundProbe!=null)try{write(DIRECTORY,"modern-failed-"+getName()+"-foreground.json",lastForegroundProbe.toString(2));}
    catch(Throwable diagnosticFailure){failure.addSuppressed(diagnosticFailure);}
   try{screenshot("modern-failed-"+getName()+".png");}catch(Throwable captureFailure){failure.addSuppressed(captureFailure);}
   throw failure;
  }
 }

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
  shell("mkdir -p "+DIRECTORY+" "+BRIDGE);shell("settings put system font_scale 1.3");collapseShade();
 }
 @Override protected void tearDown()throws Exception {
  try {
   if(modemRequested)try{modem("cancel");}catch(Throwable ignored){}
   for(int i=activities.size()-1;i>=0;i--){Activity activity=activities.get(i);if(!activity.isDestroyed())ui(activity::finish);}
   settle();activities.clear();restore(prefs,original);assertTrue(AppIcons.apply(target,originalIcon));
   restore(target.getSharedPreferences(CallReturnNavigation.PREFERENCES,Context.MODE_PRIVATE),originalNavigation);
   CallHistory.entries=originalCalls;CallHistory.summary=originalSummary;CallHistory.readFailed=originalReadFailed;CallHistory.fetchedAt=originalFetchedAt;
   String mode="default";for(String candidate:new String[]{"allow","ignore","deny","default"})if(originalOverlay.contains("SYSTEM_ALERT_WINDOW: "+candidate)){mode=candidate;break;}
   shell("cmd appops set "+target.getPackageName()+" SYSTEM_ALERT_WINDOW "+mode);
   if(!target.getPackageName().equals(originalDialer)){
    shell("cmd role remove-role-holder --user 0 android.app.role.DIALER "+target.getPackageName()+" 1");
    if(originalDialer!=null&&originalDialer.matches("[A-Za-z0-9_.]+"))shell("cmd role add-role-holder --user 0 android.app.role.DIALER "+originalDialer+" 1");
   }
   shell("settings put system font_scale "+(originalFont.matches("[0-9.]+")?originalFont:"1.0"));
   target.getContentResolver().delete(BackupDocumentProvider.DOCUMENT,null,null);
  }finally{try{collapseShade();}finally{
   try{AccessibilityServiceInfo service=getInstrumentation().getUiAutomation().getServiceInfo();service.flags=originalAccessibilityFlags;getInstrumentation().getUiAutomation().setServiceInfo(service);}
   finally{super.tearDown();}
  }}
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
 private boolean systemUiFocused(){
  for(AccessibilityWindowInfo window:getInstrumentation().getUiAutomation().getWindows()){
   if(!window.isFocused())continue;AccessibilityNodeInfo root=window.getRoot();
   if(root!=null&&"com.android.systemui".contentEquals(String.valueOf(root.getPackageName())))return true;
  }return false;
 }
 private void collapseShade()throws Exception {
  // Test-only cleanup of the real native surface. Answer broadcasts can leave
  // the shade open; finishing an underlying Activity does not close SystemUI.
  shell("cmd statusbar collapse");settle();await(()->!systemUiFocused(),"Native notification shade must relinquish focus before app interaction");
 }
 private boolean nativeWindowFocused(View view)throws Exception {
  AtomicReference<Integer> id=new AtomicReference<>();ui(()->id.set(view.createAccessibilityNodeInfo().getWindowId()));
  return nativeWindowFocused(id.get());
 }
 private boolean nativeWindowFocused(int id){
  for(AccessibilityWindowInfo window:getInstrumentation().getUiAutomation().getWindows())
   if(window.getId()==id&&window.isFocused())return true;
  return false;
 }
 private void awaitActivityForeground(Activity activity)throws Exception {
  await(()->activity.hasWindowFocus()&&activity.getWindow().getDecorView().hasWindowFocus()&&
    nativeWindowFocused(activity.getWindow().getDecorView()),"Production Activity must own its focused native window: "+activity.getClass().getSimpleName());
 }
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
 private int[] uniformBackgroundSamples(Bitmap background,Rect composition){
  int py=composition.top+dp(3),spacing=dp(2),span=2*spacing;
  int left=composition.left+composition.width()/4+span,right=composition.right-composition.width()/4-span;
  assertTrue("Real padding must contain the baseline sample guard",py-1>=composition.top&&py+1<composition.bottom&&left<=right);
  // Search nearest the center first. Selection sees only the no-island native
  // baseline, never any opacity result, and requires five guarded empty pixels.
  for(int distance=0;distance<=composition.width();distance++)for(int side=0;side<(distance==0?1:2);side++){
   int center=composition.centerX()+(side==0?distance:-distance);if(center<left||center>right)continue;
   int color=background.getPixel(center,py);
   if(Color.alpha(color)!=255||Color.red(color)<200||Color.green(color)<200||Color.blue(color)<200)continue;
   int[] points=new int[5];boolean uniform=true;
   for(int n=0;n<5;n++){
    points[n]=center+(n-2)*spacing;
    for(int gx=-1;gx<=1;gx++)for(int gy=-1;gy<=1;gy++)
     if(background.getPixel(points[n]+gx,py+gy)!=color)uniform=false;
   }
   if(uniform)return points;
  }
  return null; // The caller tries other real capsule positions before failing.
 }
 private <T extends Activity>T launch(Class<T> type)throws Exception {
  T activity=type.cast(getInstrumentation().startActivitySync(new Intent(target,type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));activities.add(activity);settle();awaitActivityForeground(activity);return activity;
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
  assertNotNull("Missing actual touch target",view);AtomicReference<Rect> accepted=new AtomicReference<>(),previous=new AtomicReference<>();
  AtomicReference<String> lastGeometry=new AtomicReference<>("No native layout sample");
  try{await(()->{AtomicReference<Rect> candidate=new AtomicReference<>();AtomicReference<Boolean> outside=new AtomicReference<>(false);AtomicReference<Integer> windowId=new AtomicReference<>();ui(()->{
   View root=view.getRootView();ViewGroup.LayoutParams raw=root.getLayoutParams();
   boolean overlay=raw instanceof WindowManager.LayoutParams&&((WindowManager.LayoutParams)raw).type==WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
   outside.set(overlay);
   lastGeometry.set("attached="+view.isAttachedToWindow()+", shown="+view.isShown()+", laidOut="+view.isLaidOut()+
    ", size="+view.getWidth()+"x"+view.getHeight()+", viewLayoutRequested="+view.isLayoutRequested()+", rootLayoutRequested="+root.isLayoutRequested());
   if(!view.isAttachedToWindow()||!view.isShown()||!view.isLaidOut()||view.getWidth()<=0||view.getHeight()<=0||
     view.isLayoutRequested()||root.isLayoutRequested())return;
   int[] location=new int[2];view.getLocationOnScreen(location);
   // Android TextView can include its own internal RTL scroll in this API.
   Rect direct=new Rect(location[0],location[1],location[0]+view.getWidth(),location[1]+view.getHeight());
   Rect adjusted=new Rect(direct);adjusted.offset(-view.getScrollX(),-view.getScrollY());
   Rect visible=new Rect();boolean shown=view.getGlobalVisibleRect(visible);
   // "Global" visibility is in the root/window coordinate space. Dialogs and
   // TYPE_APPLICATION_OVERLAY windows have a nonzero screen origin, whereas
   // getLocationOnScreen and native AX bounds already use screen coordinates.
   int[] rootScreen=new int[2],rootWindow=new int[2];root.getLocationOnScreen(rootScreen);root.getLocationInWindow(rootWindow);
   Rect visibleOnScreen=new Rect(visible);visibleOnScreen.offset(rootScreen[0]-rootWindow[0],rootScreen[1]-rootWindow[1]);
   AccessibilityNodeInfo node=view.createAccessibilityNodeInfo();Rect nativeBounds=new Rect();
   try{
    node.getBoundsInScreen(nativeBounds);windowId.set(node.getWindowId());
    lastGeometry.set("screen="+direct+", scrollAdjusted="+adjusted+", rootVisible="+visible+", rootScreen="+rootScreen[0]+","+rootScreen[1]+
     ", rootWindow="+rootWindow[0]+","+rootWindow[1]+", visibleOnScreen="+visibleOnScreen+", nativeAX="+nativeBounds+", window="+windowId.get());
   }finally{node.recycle();}
   // The raw screen origin may already exclude internal RTL text scroll.
   // Require exact independent agreement with BOTH global visibility and AX.
   Rect result=direct.equals(visibleOnScreen)&&direct.equals(nativeBounds)?direct:adjusted;
   if(result.width()<=0||result.height()<=0||!new Rect(0,0,480,854).contains(result)||!shown||
     visible.width()!=view.getWidth()||visible.height()!=view.getHeight()||!visibleOnScreen.equals(result)||!nativeBounds.equals(result))return;
   candidate.set(result);
  });
   Rect result=candidate.get();
   if(result==null||(outside.get()?systemUiFocused():!nativeWindowFocused(windowId.get()))){previous.set(null);return false;}
   // Capture readiness, layout geometry and AX bounds together on main, then
   // require the same full native target twice. A dialog relayout between the
   // old readiness check and geometry read could otherwise yield negative size.
   if(!result.equals(previous.get())){previous.set(new Rect(result));return false;}
   accepted.set(new Rect(result));return true;
  },"Target must be fully visible, stably laid out and focused in its actual native window: "+view.getTag());}
  catch(AssertionError failure){throw new AssertionError(failure.getMessage()+"; last native geometry: "+lastGeometry.get(),failure);}
  return accepted.get();
 }
 private void pointer(long down,int action,float x,float y)throws Exception {
  MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
  try{assertTrue("Native touchscreen injection rejected",getInstrumentation().getUiAutomation().injectInputEvent(event,true));}finally{event.recycle();}
 }
 private void tap(View view)throws Exception {Rect rect=bounds(view);long now=SystemClock.uptimeMillis();pointer(now,MotionEvent.ACTION_DOWN,rect.centerX(),rect.centerY());Thread.sleep(50);pointer(now,MotionEvent.ACTION_UP,rect.centerX(),rect.centerY());settle();}
 private static final String INPUT_REFERENCE="https://github.com/LineageOS/android_frameworks_native/blob/e424b61ebd9ecc1adb2a23d2ee4f18c6a8edd624/services/inputflinger/dispatcher/InputDispatcher.cpp";
 private static final Pattern INPUT_WINDOW=Pattern.compile("^\\s*(\\d+): name='(.*)', id=(-?\\d+), displayId=(-?\\d+), inputConfig=(.*?), alpha=([0-9.]+), frame=\\[(-?\\d+),(-?\\d+)\\]\\[(-?\\d+),(-?\\d+)\\], globalScale=([0-9.]+), applicationInfo\\.name=(.*?), applicationInfo\\.token=(.*?), touchableRegion=(.*?), ownerPid=(-?\\d+), ownerUid=(-?\\d+), dispatchingTimeout=(-?\\d+)ms, hasToken=(true|false), touchOcclusionMode=(\\S+)\\s*$");
 private static final Pattern INPUT_REGION=Pattern.compile("\\[(-?\\d+),(-?\\d+)\\]\\[(-?\\d+),(-?\\d+)\\]");
 private final class NativeInputDisplay {
  final double sx,sy,tx,ty;final String raw;
  NativeInputDisplay(String dispatcher)throws Exception {
   // Injection uses logical display coordinates; native window frames/regions
   // use display space. InputDispatcher::transformMotionEntryForInjectionLocked
   // applies this display matrix's inverse before native hit testing. Never use
   // a window's local transform, density, or guessed screenshot-size ratio.
   int start=dispatcher.indexOf("\n  Display: 0\n");assertTrue("Missing native display0 geometry",start>=0);
   int end=dispatcher.indexOf("\n    Windows:",start);assertTrue("Missing native display0 window boundary",end>start);
   raw=dispatcher.substring(start,end);String[] lines=raw.trim().split("\n");
   assertEquals("Unexpected native display matrix format",6,lines.length);
   assertEquals("logicalSize=480x854",lines[1].trim());
   assertTrue("This portrait fixture requires an explicit native ROT_0 matrix",lines[2].trim().matches("transform \\(ROT_0\\) \\((?:IDENTITY|SCALE|TRANSLATE|SCALE TRANSLATE)\\)"));
   double[][] matrix=new double[3][3];
   for(int row=0;row<3;row++){
    String[] values=lines[row+3].trim().split("\\s+");assertEquals("Native transform row must have three entries",3,values.length);
    for(int col=0;col<3;col++){matrix[row][col]=Double.parseDouble(values[col]);assertTrue("Non-finite native transform",Double.isFinite(matrix[row][col]));}
   }
   assertTrue("Unsupported native rotation/shear/perspective",matrix[0][1]==0&&matrix[1][0]==0&&matrix[2][0]==0&&matrix[2][1]==0&&matrix[2][2]==1);
   sx=matrix[0][0];sy=matrix[1][1];tx=matrix[0][2];ty=matrix[1][2];
   assertTrue("Invalid native portrait scale",sx>0.00005&&sx<=16&&sy>0.00005&&sy<=16&&Math.abs(tx)<=10000&&Math.abs(ty)<=10000);
  }
  void requireBoundedInverse(int x,int y){
   // A four-decimal matrix dump is not exact nativefloat. Bound the inverse
   // uncertainty, then require the same native target at all nine neighboring
   // integer points; no rounded coefficient can hide an adjacent SystemUI edge.
   double ux=0.00005*(Math.abs((x-tx)/sx)+1)/(sx-0.00005),uy=0.00005*(Math.abs((y-ty)/sy)+1)/(sy-0.00005);
   assertTrue("Native matrix dump cannot safely resolve this input point",ux<0.25&&uy<0.25);
  }
  int inputX(int logicalX){return (int)((logicalX-tx)/sx);}
  int inputY(int logicalY){return (int)((logicalY-ty)/sy);}
  double logicalX(int inputX){return sx*inputX+tx;}
  double logicalY(int inputY){return sy*inputY+ty;}
  JSONObject json()throws Exception {return new JSONObject().put("scale_x",sx).put("scale_y",sy).put("translate_x",tx).put("translate_y",ty).put("raw",raw);}
 }
 private final class NativeInputWindow {
  final String name,config,raw;final int id,uid,index;final Rect frame;final boolean token;final List<Rect> regions=new ArrayList<>();
  NativeInputWindow(Matcher match,String line)throws Exception {
   index=Integer.parseInt(match.group(1));name=match.group(2);id=Integer.parseInt(match.group(3));config=match.group(5);uid=Integer.parseInt(match.group(16));token=Boolean.parseBoolean(match.group(18));raw=line.trim();
   frame=new Rect(Integer.parseInt(match.group(7)),Integer.parseInt(match.group(8)),Integer.parseInt(match.group(9)),Integer.parseInt(match.group(10)));
   // ftl::Flags prints names joined by " | ", or 0x0. Unknown numeric bits
   // cannot silently become touchable under this pinned API33 parser.
   for(String flag:config.split(" \\| "))assertTrue("Unknown native input flag: "+config,"0x0".equals(flag)||flag.matches("[A-Z_]+"));
   String region=match.group(14);if(!"<empty>".equals(region)){
    Matcher rectangles=INPUT_REGION.matcher(region);StringBuilder reconstructed=new StringBuilder();
    while(rectangles.find()){
     if(reconstructed.length()>0)reconstructed.append('|');reconstructed.append(rectangles.group());
     Rect rect=new Rect(Integer.parseInt(rectangles.group(1)),Integer.parseInt(rectangles.group(2)),Integer.parseInt(rectangles.group(3)),Integer.parseInt(rectangles.group(4)));
     assertTrue("Invalid native touch region "+region,!rect.isEmpty());regions.add(rect);
    }
    assertEquals("Unrecognized native touch region",region,reconstructed.toString());
   }
  }
  boolean flag(String flag){for(String value:config.split(" \\| "))if(flag.equals(value))return true;return false;}
  boolean accepts(int x,int y){if(flag("NOT_VISIBLE")||flag("NOT_TOUCHABLE")||flag("SPY"))return false;for(Rect rect:regions)if(rect.contains(x,y))return true;return false;}
  JSONObject json()throws Exception {return new JSONObject().put("index",index).put("id",id).put("name",name).put("uid",uid).put("input_config",config).put("frame",frame.toShortString()).put("raw",raw);}
 }
 private List<NativeInputWindow> inputWindows(String raw)throws Exception {
  assertTrue("Native routing proof requires the actual API33 dispatcher dump",android.os.Build.VERSION.SDK_INT==33&&raw.contains("Input Dispatcher State:"));
  String dispatcher=raw.substring(raw.indexOf("Input Dispatcher State:"));int old=dispatcher.indexOf("Input Dispatcher State at time of last ANR:");if(old>=0)dispatcher=dispatcher.substring(0,old);
  assertTrue("Native input must be enabled and unfrozen",dispatcher.contains("DispatchEnabled: true")&&dispatcher.contains("DispatchFrozen: false"));
  assertTrue("Native input display must match the actual portrait capture",dispatcher.contains("logicalSize=480x854"));
  ArrayList<NativeInputWindow> windows=new ArrayList<>();int display=-1;
  for(String line:dispatcher.split("\\n")){
   Matcher header=Pattern.compile("^\\s*Display: (-?\\d+)\\s*$").matcher(line);if(header.matches()){display=Integer.parseInt(header.group(1));continue;}
   if(display!=0||!line.contains("inputConfig="))continue;
   Matcher match=INPUT_WINDOW.matcher(line);assertTrue("Unknown API33 input window record: "+line,match.matches());assertEquals(0,Integer.parseInt(match.group(4)));
   NativeInputWindow window=new NativeInputWindow(match,line);assertEquals("Native input windows must retain front-to-back order",windows.size(),window.index);windows.add(window);
  }
  assertFalse("No actual display0 input windows",windows.isEmpty());return windows;
 }
 private NativeInputWindow firstInputTarget(List<NativeInputWindow> windows,int x,int y){for(NativeInputWindow window:windows)if(window.accepts(x,y))return window;return null;}
 private boolean inputNeighborhoodMatches(List<NativeInputWindow> windows,NativeInputWindow targetWindow,int x,int y){
  if(targetWindow==null)return false;
  for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)if(firstInputTarget(windows,x+dx,y+dy)!=targetWindow)return false;
  return true;
 }
 private boolean ownsIslandInput(NativeInputWindow window){return window!=null&&window.uid==target.getApplicationInfo().uid&&window.token&&window.name.endsWith("DA CALLER call island")&&!window.flag("NO_INPUT_CHANNEL")&&!window.flag("PAUSE_DISPATCHING")&&!window.flag("DROP_INPUT")&&!window.flag("DROP_INPUT_IF_OBSCURED");}
 private int nativeStatusBottom(List<NativeInputWindow> windows,NativeInputDisplay display){
  int bottom=-1;
  for(NativeInputWindow window:windows){
   if(window.flag("NOT_VISIBLE")||!(window.name.equals("StatusBar")||window.name.endsWith(" StatusBar"))||window.frame.isEmpty())continue;
   double left=display.logicalX(window.frame.left),right=display.logicalX(window.frame.right),top=display.logicalY(window.frame.top),edge=display.logicalY(window.frame.bottom);
   // dumpsys prints four fractional digits. Permit at most one physical pixel
   // of matrix-print rounding at the screen edge, retaining full-width/top
   // SystemUI protection and rounding its bottom outward conservatively.
   if(Math.abs(left)<=1&&Math.abs(right-480)<=1&&Math.abs(top)<=1&&edge>top&&edge<854/3d)bottom=Math.max(bottom,(int)Math.ceil(edge));
  }
  return bottom;
 }
 private Rect awaitUnoccludedIslandInput(View view,JSONObject evidence)throws Exception {
  Call owned=PhoneSessionService.current;CallIslandState call=PhoneSessionService.islandSnapshot();assertNotNull(owned);assertNotNull(call);String token=call.token;
  evidence.put("owned_call_token",token);long deadline=SystemClock.uptimeMillis()+20000;String previous="";Rect accepted=null;
  do{
   assertSame("Waiting for native input must preserve the actual call",owned,PhoneSessionService.current);assertNotNull(PhoneSessionService.islandSnapshot());assertEquals(token,PhoneSessionService.islandSnapshot().token);
   Rect rect=bounds(view);int x=rect.centerX(),y=rect.centerY();String raw=shell("dumpsys input");
   int section=raw.indexOf("Input Dispatcher State:");String recorded=section<0?raw:raw.substring(section);
   evidence.put("last_raw_input",recorded.length()<=180000?recorded:recorded.substring(0,180000)).put("source_reference",INPUT_REFERENCE).put("point_x",x).put("point_y",y).put("target_bounds",rect.toShortString());
   List<NativeInputWindow> windows=inputWindows(raw);NativeInputDisplay display=new NativeInputDisplay(recorded);display.requireBoundedInverse(x,y);int inputX=display.inputX(x),inputY=display.inputY(y);NativeInputWindow first=firstInputTarget(windows,inputX,inputY);int status=nativeStatusBottom(windows,display);boolean interior=inputNeighborhoodMatches(windows,first,inputX,inputY);
   JSONArray sampled=new JSONArray();for(NativeInputWindow window:windows)sampled.put(window.json());evidence.put("windows",sampled).put("display_transform",display.json()).put("input_point_x",inputX).put("input_point_y",inputY).put("input_neighborhood_same_target",interior).put("first_target",first==null?JSONObject.NULL:first.json()).put("native_status_bar_bottom",status);
   boolean ready=ownsIslandInput(first)&&interior&&status>0&&y>=status&&!systemUiFocused();String current=ready?first.raw+"/"+rect.toShortString()+"/"+display.raw+"/"+inputX+","+inputY+"/"+status:"";
   if(ready&&current.equals(previous)){accepted=rect;break;}previous=current;Thread.sleep(60);
  }while(SystemClock.uptimeMillis()<deadline);
  assertNotNull("One native island drag requires its unobscured InputDispatcher target, not only Home focus: "+evidence,accepted);return accepted;
 }
 private JSONObject actualDragTarget(String raw)throws Exception {
  assertTrue("No native dispatcher after drag start",raw.contains("Input Dispatcher State:"));String dispatcher=raw.substring(raw.indexOf("Input Dispatcher State:"));int historical=dispatcher.indexOf("Input Dispatcher State at time of last ANR:");if(historical>=0)dispatcher=dispatcher.substring(0,historical);
  int start=dispatcher.indexOf("TouchStatesByDisplay:");assertTrue("No actual touch stream after first native MOVE",start>=0);String touched=dispatcher.substring(start);int end=touched.indexOf("\n  Display:");if(end>=0)touched=touched.substring(0,end);
  boolean display0=false;JSONObject actual=null;
  for(String line:touched.split("\\n")){
   Matcher display=Pattern.compile("^\\s*(-?\\d+): down=(true|false), split=.*$").matcher(line);if(display.matches()){display0="0".equals(display.group(1))&&"true".equals(display.group(2));continue;}
   if(!display0)continue;Matcher window=Pattern.compile("^\\s*\\d+: name='(.*)', pointerIds=0x([0-9a-fA-F]+), targetFlags=0x([0-9a-fA-F]+)\\s*$").matcher(line);
   if(window.matches()&&(Long.parseLong(window.group(3),16)&1)!=0){assertNull("A single pointer must have one native foreground target",actual);actual=new JSONObject().put("name",window.group(1)).put("pointer_ids",window.group(2)).put("target_flags",window.group(3)).put("raw",line.trim());}
  }
  assertNotNull("Missing native foreground target for the actual pointer stream",actual);assertTrue("Native drag stream was routed to a different window: "+actual,actual.getString("name").endsWith("DA CALLER call island"));return actual;
 }
 private void drag(View view,int dx,int dy,boolean cancel)throws Exception {
  JSONObject evidence=new JSONObject().put("target_tag",String.valueOf(view==null?null:view.getTag())).put("dx",dx).put("dy",dy).put("cancelled",cancel);nativeDragRouting.put(evidence);
  boolean down=false,finished=false;long now=0;int x=0,y=0;
  try{
  Rect rect=awaitUnoccludedIslandInput(view,evidence);now=SystemClock.uptimeMillis();x=rect.centerX();y=rect.centerY();int status=evidence.getInt("native_status_bar_bottom");
  assertTrue("Drag must begin inside a laid-out app target below protected SystemUI",rect.contains(x,y)&&y>=status);
  assertTrue("Drag must remain inside the native display",new Rect(0,status,480,854).contains(x+dx,y+dy));
  pointer(now,MotionEvent.ACTION_DOWN,x,y);down=true;boolean routed=false;int slop=android.view.ViewConfiguration.get(view.getContext()).getScaledTouchSlop();evidence.put("touch_slop",slop).put("down_uptime_millis",now);
  for(int n=1;n<=8;n++){
   Thread.sleep(25);pointer(now,MotionEvent.ACTION_MOVE,x+dx*n/8f,y+dy*n/8f);
   // Cross touch slop before reading the server stream so a diagnostic shell
   // round-trip cannot turn the DOWN into the child's long-press action.
   float mx=dx*n/8f,my=dy*n/8f;
   if(!routed&&mx*mx+my*my>slop*slop){String stream=shell("dumpsys input");int section=stream.indexOf("Input Dispatcher State:");String recorded=section<0?stream:stream.substring(section);evidence.put("actual_first_move_input",recorded.length()<=180000?recorded:recorded.substring(0,180000)).put("actual_foreground_target",actualDragTarget(stream)).put("verified_after_move",n);routed=true;}
  }
  assertTrue("A native drag must cross real touch slop and confirm its foreground target",routed);
  pointer(now,cancel?MotionEvent.ACTION_CANCEL:MotionEvent.ACTION_UP,x+dx,y+dy);finished=true;settle();evidence.put("native_stream_completed",true);
  }finally{
   if(down&&!finished)try{pointer(now,MotionEvent.ACTION_CANCEL,x,y);}catch(Throwable ignored){}
   write(DIRECTORY,"modern-drag-input-routing.json",new JSONObject().put("api",33).put("source_reference",INPUT_REFERENCE).put("gestures",nativeDragRouting).toString(2));
  }
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
   tap(tag(host,"recent_expand"));await(()->tag(host,"recent_actions").isShown(),"Native row pointer must visibly expand its actions");assertTrue("Expansion must not dial",host.requestedNumbers.isEmpty());
   for(String action:new String[]{"recent_action_call","recent_sms","recent_contact","recent_details"}){Rect control=bounds(tag(host,action));assertTrue(action,control.height()>=dp(48));}
   screenshot("modern-recents-inline-"+suffix+".png");key(KeyEvent.KEYCODE_BACK);
   await(()->host.hasWindowFocus()&&!host.isFinishing()&&tag(host,"recent_actions").getVisibility()==View.GONE&&!tag(host,"recent_actions").isShown(),
    "Back must collapse the retained row actions while keeping the current Activity");
   assertTrue("Back from the row must not dial",host.requestedNumbers.isEmpty());
   tap(tag(host,"main_nav_keypad"));await(()->windowFind(EditText.class)!=null,"Bottom dialpad did not open");
   assertTrue(host.requestedNumbers.isEmpty());screenshot("modern-bottom-dialpad-"+suffix+".png");key(KeyEvent.KEYCODE_BACK);
   tap(tag(host,"main_settings_button"));View icons=tag(host,"settings_icons");reveal(icons);tap(icons);
   assertEquals("All durable icon choices retained",9,AppIcons.IDS.length);
   for(String id:AppIcons.IDS){View choice=windowTag("launcher-icon-"+id);assertNotNull("Missing durable picker choice "+id,choice);assertNotNull(find(choice,ImageView.class));}
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
  JSONObject widgetConfiguration=widgetConfigurationPresentation();
  proof("modern-presentation-native.json",new JSONObject().put("list_host","ListDispatchHostActivity: real views and pointer dispatch, records rather than places calls")
   .put("observations",observations).put("widget_configuration",widgetConfiguration).put("no_floating_dialpad",true).put("all_inline_actions_retained",true).put("back_collapses_without_dial",true));
 }
 private <T extends View>T windowFind(Class<T> type){for(View root:WindowInspector.getGlobalWindowViews()){T view=find(root,type);if(view!=null&&view.isShown())return view;}return null;}

 /** Actual production configuration Activity, with an explicitly unbound preview ID.
  * No selection or injected input occurs; launcher-host binding is outside this proof. */
 private JSONObject widgetConfigurationPresentation()throws Exception {
  final int previewId=2147483000;AppWidgetManager manager=AppWidgetManager.getInstance(target);
  assertTrue(previewId!=AppWidgetManager.INVALID_APPWIDGET_ID);assertNull(manager.getAppWidgetInfo(previewId));
  assertEquals(android.content.pm.PackageManager.PERMISSION_GRANTED,target.checkSelfPermission(Manifest.permission.READ_CONTACTS));
  await(()->PhoneSessionService.current==null,"Read-only configuration previews require Telecom cleanup to finish");
  Map<String,?> before=prefs.getAll();JSONArray observations=new JSONArray();
  AtomicInteger providerChanges=new AtomicInteger();ContentObserver observer=new ContentObserver(new Handler(Looper.getMainLooper())){
   @Override public void onChange(boolean selfChange){providerChanges.incrementAndGet();}
  };
  target.getContentResolver().registerContentObserver(ContactsContract.AUTHORITY_URI,true,observer);
  target.getContentResolver().registerContentObserver(CallLog.Calls.CONTENT_URI,true,observer);
  try{
   for(boolean amoled:new boolean[]{false,true}){
    String language=amoled?"en":"he",frame="modern-nex-widget-"+(amoled?"amoled-en":"light-he")+".png";
    assertTrue(prefs.edit().putString("language",language).putBoolean("dark",false).putBoolean("amoled",amoled).commit());
    Map<String,?> configured=prefs.getAll();WidgetConfigure activity=null;JSONObject observation;
    try{
     Intent intent=new Intent(target,WidgetConfigure.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,previewId);
     activity=(WidgetConfigure)getInstrumentation().startActivitySync(intent);activities.add(activity);settle();awaitActivityForeground(activity);
     final WidgetConfigure shown=activity;await(()->activityForeground(shown)!=null,"Actual WidgetConfigure must be the resumed native application window");
     LinearLayout root=(LinearLayout)((ViewGroup)activity.findViewById(android.R.id.content)).getChildAt(0);
     TextView heading=text(root,amoled?"Choose a contact":"בחר איש קשר");EditText search=find(root,EditText.class);ImageView back=find(root,ImageView.class);
     assertNotNull(heading);assertNotNull(search);assertNotNull(back);assertFalse(activity.isFinishing());
     Rect rootBounds=bounds(root),headingBounds=bounds(heading),searchBounds=bounds(search),backBounds=bounds(back);
     assertTrue(backBounds.width()>=dp(48)&&backBounds.height()>=dp(48));assertTrue(searchBounds.width()>=dp(48)&&searchBounds.height()>=dp(54));
     JSONObject rootGeometry=nativeGeometry(root,rootBounds),headingGeometry=nativeGeometry(heading,headingBounds),searchGeometry=nativeGeometry(search,searchBounds),backGeometry=nativeGeometry(back,backBounds);
     AtomicReference<JSONObject> metrics=new AtomicReference<>();ui(()->{try{
      int background=((ColorDrawable)root.getBackground()).getColor(),surface=((GradientDrawable)search.getBackground()).getColor().getDefaultColor();
      int ink=amoled?0xFFF9F1E8:0xFF37291F,muted=amoled?0xFFF2E5DD:0xFF4C352A;
      assertEquals(amoled?Color.BLACK:0xFFF6F0E8,background);assertEquals(amoled?0xFF0A0A0A:0xFFFFFDFC,surface);
      assertEquals(ink,heading.getCurrentTextColor());assertEquals(ink,search.getCurrentTextColor());assertEquals(muted,search.getCurrentHintTextColor());
      assertEquals(amoled?View.LAYOUT_DIRECTION_LTR:View.LAYOUT_DIRECTION_RTL,root.getLayoutDirection());
      assertEquals(amoled?"Back":"חזרה",String.valueOf(back.getContentDescription()));assertTrue(back.isClickable()&&back.isFocusable());
      assertEquals("",search.getText().toString());assertEquals(amoled?"Search name or number":"חיפוש שם או מספר",String.valueOf(search.getHint()));
      android.text.Layout layout=heading.getLayout();assertNotNull(layout);JSONArray lines=new JSONArray();int covered=0;
      for(int i=0;i<layout.getLineCount();i++){
       assertEquals(covered,layout.getLineStart(i));covered=layout.getLineEnd(i);assertEquals(0,layout.getEllipsisCount(i));
       assertTrue(layout.getLineLeft(i)>=-1&&layout.getLineRight(i)<=layout.getWidth()+1);
       assertTrue(layout.getLineBottom(i)<=heading.getHeight()-heading.getCompoundPaddingTop()-heading.getCompoundPaddingBottom());
       lines.put(new JSONObject().put("start_utf16",layout.getLineStart(i)).put("end_utf16",covered).put("ellipsis_count",layout.getEllipsisCount(i))
        .put("left_px",layout.getLineLeft(i)).put("right_px",layout.getLineRight(i)).put("bottom_px",layout.getLineBottom(i)));
      }
      assertEquals(heading.length(),covered);assertEquals(750,heading.getTypeface().getWeight());assertEquals(600,search.getTypeface().getWeight());
      float scaled=shown.getResources().getDisplayMetrics().scaledDensity;assertEquals(22f,heading.getTextSize()/scaled,0.01f);assertEquals(16f,search.getTextSize()/scaled,0.01f);
      float hintWidth=search.getPaint().measureText(search.getHint().toString());int available=search.getWidth()-search.getCompoundPaddingLeft()-search.getCompoundPaddingRight();
      Paint.FontMetrics font=search.getPaint().getFontMetrics();int baseline=search.getBaseline();assertTrue(hintWidth<=available);
      assertTrue(baseline+font.top>=search.getCompoundPaddingTop()-1&&baseline+font.bottom<=search.getHeight()-search.getCompoundPaddingBottom()+1);
      metrics.set(new JSONObject().put("layout_direction",amoled?"LTR":"RTL").put("font_scale",shown.getResources().getConfiguration().fontScale)
       .put("background_argb",background).put("surface_argb",surface).put("heading_argb",heading.getCurrentTextColor()).put("search_argb",search.getCurrentTextColor()).put("hint_argb",search.getCurrentHintTextColor())
       .put("status_bar_argb",shown.getWindow().getStatusBarColor()).put("navigation_bar_argb",shown.getWindow().getNavigationBarColor())
       .put("heading",new JSONObject().put("text",heading.getText()).put("text_size_sp",heading.getTextSize()/scaled).put("font_weight",heading.getTypeface().getWeight()).put("font_variation",heading.getFontVariationSettings())
        .put("layout_width_px",layout.getWidth()).put("content_width_px",heading.getWidth()-heading.getCompoundPaddingLeft()-heading.getCompoundPaddingRight())
        .put("content_height_px",heading.getHeight()-heading.getCompoundPaddingTop()-heading.getCompoundPaddingBottom()).put("lines",lines))
       .put("search_hint",new JSONObject().put("text",search.getHint()).put("text_size_sp",search.getTextSize()/scaled).put("font_weight",search.getTypeface().getWeight()).put("font_variation",search.getFontVariationSettings())
        .put("measured_width_px",hintWidth).put("available_width_px",available).put("font_top_px",font.top).put("font_bottom_px",font.bottom).put("baseline_px",baseline)
        .put("padding_top_px",search.getCompoundPaddingTop()).put("padding_bottom_px",search.getCompoundPaddingBottom()).put("height_px",search.getHeight())));
     }catch(Exception failure){throw new RuntimeException(failure);}});
     JSONObject foreground=activityForeground(activity);assertNotNull(foreground);assertNull(manager.getAppWidgetInfo(previewId));
     Bitmap bitmap=capture(frame);JSONArray samples=new JSONArray();try{for(int n=0;n<5;n++){
      int x=rootBounds.left+dp(2)+n,y=rootBounds.top+dp(2),color=bitmap.getPixel(x,y);assertEquals(amoled?Color.BLACK:0xFFF6F0E8,color);
      samples.put(new JSONObject().put("x",x).put("y",y).put("argb",color));
     }}finally{bitmap.recycle();}
     observation=metrics.get().put("frame",frame).put("activity",WidgetConfigure.class.getName()).put("theme",amoled?"amoled":"light").put("language",language).put("amoled",amoled)
      .put("widget_id",previewId).put("intent_widget_id",activity.getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID)).put("widget_provider_info","null")
      .put("foreground",foreground).put("root_geometry",rootGeometry).put("heading_geometry",headingGeometry).put("search_geometry",searchGeometry).put("back_geometry",backGeometry).put("background_samples",samples);
    }finally{if(activity!=null){WidgetConfigure closing=activity;ui(closing::finish);settle();assertTrue(closing.isFinishing()||closing.isDestroyed());}}
    assertEquals("Preview must not select a contact or alter preferences",configured,prefs.getAll());assertNull("Preview must not create a Telecom call",PhoneSessionService.current);
    assertNull(manager.getAppWidgetInfo(previewId));assertEquals("No contacts/call-log provider changes during read-only preview",0,providerChanges.get());
    observations.put(observation.put("selection_actions",0).put("native_pointer_inputs",0).put("provider_writes_requested",0).put("provider_change_notifications",providerChanges.get())
     .put("preferences_unchanged",true).put("telecom_call_unchanged",true).put("finished",true));
   }
  }finally{target.getContentResolver().unregisterContentObserver(observer);restore(prefs,before);assertEquals(before,prefs.getAll());}
  return new JSONObject().put("scope","Actual WidgetConfigure Activity; synthetic unbound ID configuration preview").put("launcher_host_binding","NOT_TESTED")
   .put("preferences_restored",true).put("observations",observations);
 }
 // API33 closes the ActivityRecord component with `} tN}`; older dumps use
 // ` tN}`. Parse the exact resumed record/component, never a component prefix
 // or a historical paused entry that merely mentions the expected Activity.
 private static final Pattern RESUMED_ACTIVITY=Pattern.compile("^[ \\t]*(?:mResumedActivity|topResumedActivity)[ \\t]*[:=][ \\t]*ActivityRecord\\{[0-9a-fA-F]+[ \\t]+u[0-9]+[ \\t]+([^\\s}]+)(?:\\}[ \\t]+t[0-9]+\\}|[ \\t]+t[0-9]+\\})[ \\t]*$");
 private JSONObject activityForeground(Activity activity)throws Exception {
  ComponentName expected=new ComponentName(activity,activity.getClass());String record=null;
  JSONObject probe=new JSONObject().put("diagnostic_only",true).put("sampled_uptime_millis",SystemClock.uptimeMillis())
   .put("expected_component",expected.flattenToShortString()).put("outcome","INCOMPLETE_SAMPLE");lastForegroundProbe=probe;
  JSONArray resumedRows=new JSONArray();int omittedRows=0;
  for(String line:shell("dumpsys activity activities").split("\\n")){
   if(line.contains("ResumedActivity")||line.contains("resumedActivity")){
    if(resumedRows.length()<16)resumedRows.put(line.length()>1000?line.substring(0,1000)+" [TRUNCATED]":line);else omittedRows++;
   }
   Matcher match=RESUMED_ACTIVITY.matcher(line);
   if(record==null&&match.matches()&&expected.equals(ComponentName.unflattenFromString(match.group(1))))record=line.trim();
  }
  probe.put("actual_resumed_rows",resumedRows).put("omitted_resumed_rows",omittedRows).put("matched_record",record==null?JSONObject.NULL:record);
  AtomicReference<Integer> decorWindow=new AtomicReference<>(-1);AtomicReference<Rect> decorBounds=new AtomicReference<>();
  AtomicReference<JSONObject> decorState=new AtomicReference<>();
  ui(()->{try{
   View decor=activity.getWindow().getDecorView();
   boolean finishing=activity.isFinishing(),destroyed=activity.isDestroyed(),activityFocus=activity.hasWindowFocus(),decorFocus=decor.hasWindowFocus(),
    attached=decor.isAttachedToWindow(),shown=decor.isShown();int visibility=decor.getWindowVisibility();
   JSONObject observed=new JSONObject().put("finishing",finishing).put("destroyed",destroyed).put("activity_focus",activityFocus).put("decor_focus",decorFocus)
    .put("attached",attached).put("shown",shown).put("window_visibility",visibility).put("window_id",-1).put("bounds",JSONObject.NULL).put("ready",false);decorState.set(observed);
   if(finishing||destroyed||!activityFocus||!decorFocus||!attached||!shown||visibility!=View.VISIBLE)return;
   AccessibilityNodeInfo node=decor.createAccessibilityNodeInfo();try{
    Rect bounds=new Rect();node.getBoundsInScreen(bounds);observed.put("window_id",node.getWindowId()).put("bounds",bounds.toShortString());
    if(node.getWindowId()<0||bounds.isEmpty()||!new Rect(0,0,480,854).contains(bounds))return;
    decorWindow.set(node.getWindowId());decorBounds.set(bounds);observed.put("ready",true);
   }finally{node.recycle();}
  }catch(Exception failure){throw new RuntimeException(failure);}});
  probe.put("decor",decorState.get());JSONArray nativeWindows=new JSONArray();probe.put("native_windows",nativeWindows);int omittedWindows=0,acceptedWindow=-1,acceptedEdgeDelta=-1;
  Rect acceptedWindowBounds=null,acceptedRootBounds=null;
  for(AccessibilityWindowInfo window:getInstrumentation().getUiAutomation().getWindows()){
   try{
    Rect windowBounds=new Rect(),rootBounds=new Rect();window.getBoundsInScreen(windowBounds);
    JSONObject observed=new JSONObject().put("window_id",window.getId()).put("type",window.getType()).put("focused",window.isFocused())
     .put("active",window.isActive()).put("bounds",windowBounds.toShortString());
    AccessibilityNodeInfo root=window.getRoot();String rejection;
    if(root==null){observed.put("root",JSONObject.NULL);rejection="MISSING_AX_ROOT";}
    else try{
     root.getBoundsInScreen(rootBounds);Rect decor=decorBounds.get();int edgeDelta=decor==null?-1:Math.max(Math.max(Math.abs(windowBounds.left-decor.left),Math.abs(windowBounds.top-decor.top)),
      Math.max(Math.abs(windowBounds.right-decor.right),Math.abs(windowBounds.bottom-decor.bottom)));
     observed.put("root_package",root.getPackageName()==null?JSONObject.NULL:root.getPackageName().toString())
      .put("root_visible",root.isVisibleToUser()).put("root_bounds",rootBounds.toShortString()).put("max_window_edge_delta_px",edgeDelta);
     if(window.getId()!=decorWindow.get())rejection="NOT_ACTIVITY_DECOR_WINDOW";
     else if(window.getType()!=AccessibilityWindowInfo.TYPE_APPLICATION)rejection="NOT_APPLICATION_WINDOW";
     else if(!window.isActive()||!window.isFocused())rejection="NOT_ACTIVE_AND_FOCUSED";
     else if(!root.isVisibleToUser()||!target.getPackageName().contentEquals(String.valueOf(root.getPackageName())))rejection="ROOT_NOT_VISIBLE_TARGET_PACKAGE";
     else if(decor==null||decor.isEmpty()||windowBounds.isEmpty()||rootBounds.isEmpty()||!new Rect(0,0,480,854).contains(decor)||
       !new Rect(0,0,480,854).contains(windowBounds)||!new Rect(0,0,480,854).contains(rootBounds))rejection="ROOT_OR_WINDOW_BOUNDS_INVALID";
     else if(!rootBounds.equals(decor))rejection="NATIVE_ROOT_DIFFERS_FROM_ACTIVITY_DECOR";
     // Measured API33 AX window edges can round one pixel inward from its
     // exact root/Decor bounds. Keep all raw bounds and independently verify
     // this bounded representation difference; two pixels must still refuse.
     else if(edgeDelta>1)rejection="NATIVE_WINDOW_EDGE_DELTA_EXCEEDS_ONE_PIXEL";
     else{rejection="NONE";acceptedWindow=window.getId();acceptedEdgeDelta=edgeDelta;acceptedWindowBounds=new Rect(windowBounds);acceptedRootBounds=new Rect(rootBounds);}
    }finally{root.recycle();}
    observed.put("rejection_reason",rejection);
    if(nativeWindows.length()<32)nativeWindows.put(observed);else omittedWindows++;
   }finally{window.recycle();}
  }
  probe.put("omitted_native_windows",omittedWindows);
  if(record==null){probe.put("outcome","REJECTED_NO_EXACT_RESUMED_ACTIVITY");return null;}
  if(decorBounds.get()==null){probe.put("outcome","REJECTED_ACTIVITY_DECOR_NOT_READY");return null;}
  if(acceptedWindow<0){probe.put("outcome","REJECTED_NO_MATCHING_NATIVE_APPLICATION_WINDOW");return null;}
  probe.put("outcome","FOREGROUND_CHECK_ACCEPTED").put("accepted_window_id",acceptedWindow);
  return new JSONObject().put("component",expected.flattenToShortString()).put("resumed_record",record).put("top_resumed",true)
   .put("native_application_window_visible",true).put("window_id",acceptedWindow).put("decor_bounds",decorBounds.get().toShortString())
   .put("native_window_bounds",acceptedWindowBounds.toShortString()).put("native_root_bounds",acceptedRootBounds.toShortString()).put("max_window_edge_delta_px",acceptedEdgeDelta);
 }

 public void testSameFileArchiveBackupRequiresSeparateExportAndRestoreOptIns()throws Exception {
  prefs.edit().putString("language","en").putBoolean("dark",false).putBoolean("amoled",false).putString(CallArchive.PREF,CallArchive.OFF).commit();
  CallArchive.Backup prior=CallArchive.captureBackup(target);long stamp=System.currentTimeMillis()-86_400_000L;
  CallArchive.BackupRow added=row(912300001,"0500099021",stamp,31),conflict=row(912300002,"0500099022",stamp+1,42);
  assertFalse("Synthetic archive key must not alias existing data",containsKey(prior,added.key)||containsKey(prior,conflict.key));
  JSONArray observations=new JSONArray();
  try {
   insert(added);insert(conflict);
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
   Activity activity=getInstrumentation().startActivitySync(SettingsBackupActivity.intent(target,true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));activities.add(activity);settle();awaitActivityForeground(activity);
   CheckBox check=find(activity.getWindow().getDecorView(),CheckBox.class);assertNotNull(check);assertFalse("Archive inclusion is always opt-in",check.isChecked());
   assertEquals("No SAF output before user chooses",0,monitor.getHits());if(archive){reveal(check);tap(check);}assertEquals(archive,check.isChecked());screenshot(filename);
   TextView save=text(activity.getWindow().getDecorView(),"Choose file and save backup");assertNotNull(save);reveal(save);tap(save);
   await(()->monitor.getHits()==1,"Explicit Save pointer must launch exactly one native SAF output request");assertEquals(1,monitor.getHits());await(()->text(activity.getWindow().getDecorView(),"Backup saved to the file you selected.")!=null,"Export did not finish");return activity;
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
   screenshot(filename);View restore=windowButton(android.R.id.button1);assertNotNull(restore);reveal(restore);tap(restore);return activity;
  }finally{getInstrumentation().removeMonitor(monitor);}
 }
 private View windowButton(int id){for(View root:WindowInspector.getGlobalWindowViews()){View button=root.findViewById(id);if(button!=null&&button.isShown())return button;}return null;}
 private byte[] readDocument()throws Exception {try(InputStream input=target.getContentResolver().openInputStream(BackupDocumentProvider.DOCUMENT);ByteArrayOutputStream out=new ByteArrayOutputStream()){
  byte[] buffer=new byte[4096];for(int n;(n=input.read(buffer))!=-1;)out.write(buffer,0,n);return out.toByteArray();}}

 public void testGenuineTelecomIslandDragCancelHideRestoreTopAreaAndTransparency()throws Exception {
  resolveHome();
  for(String permission:new String[]{Manifest.permission.READ_CONTACTS,Manifest.permission.READ_CALL_LOG,Manifest.permission.READ_PHONE_STATE,Manifest.permission.CALL_PHONE,Manifest.permission.POST_NOTIFICATIONS})
   shell("pm grant "+target.getPackageName()+" "+permission);
  shell("cmd role add-role-holder --user 0 android.app.role.DIALER "+target.getPackageName());
  RoleManager roles=target.getSystemService(RoleManager.class);TelecomManager telecom=target.getSystemService(TelecomManager.class);
  await(()->roles.isRoleHeld(RoleManager.ROLE_DIALER)&&target.getPackageName().equals(telecom.getDefaultDialerPackage()),"Production app must hold actual default-dialer role");
  shell("cmd appops set "+target.getPackageName()+" SYSTEM_ALERT_WINDOW allow");assertTrue(Settings.canDrawOverlays(target));
  prefs.edit().putBoolean("role_prompted",true).putBoolean("notifications_prompted",true).putBoolean("voice_permission_prompted",true)
   .putString("language","en").putBoolean("dark",false).putBoolean("amoled",false).putBoolean("haptics",false).putString(CallArchive.PREF,CallArchive.OFF)
   .putString("start_page","recents").putBoolean("island_enabled",true).putBoolean("island_outside_app",true).putBoolean("island_animations",false)
   .putBoolean("island_confirm_end",true).putString("island_size","compact")
   .putBoolean("island_show_mute",true).putBoolean("island_show_speaker",true).putBoolean("island_show_hold",true)
   .putBoolean("island_show_photo",true).putBoolean("island_show_timer",true).putString("avatar_shape","circle").putString("island_style","transparent").putInt("island_background_opacity",35)
   .putString(CallIslandPosition.MODE,"below").remove(CallIslandPosition.SET).remove(CallIslandPosition.X).remove(CallIslandPosition.Y).remove("island_hidden_token").commit();
  MainActivity main=launch(MainActivity.class);await(main::hasWindowFocus,"Main did not become visible");
  JSONObject proof=new JSONObject();JSONArray gestures=new JSONArray(),surfaces=new JSONArray();
  modem("call");await(()->state(Call.STATE_RINGING)&&PhoneSessionService.islandSnapshot()!=null,"Actual modem call did not bind");
  Call call=PhoneSessionService.current;String token=PhoneSessionService.islandSnapshot().token;
  assertEquals(NUMBER,call.getDetails().getHandle().getSchemeSpecificPart());key(KeyEvent.KEYCODE_HOME);answerNative();
  await(()->external()!=null,"Actual answered call must show external island on Home");assertOwned(call,token);awaitHome("active-home");
  Rect before=bounds(external());drag(windowTag("island-expand"),0,dp(105),false);
  await(()->prefs.getBoolean(CallIslandPosition.SET,false),"Native drag did not save normalized position");Rect moved=bounds(external());
  assertTrue("The actual external window must move",moved.top>before.top+dp(50));assertOwned(call,token);
  int x=prefs.getInt(CallIslandPosition.X,-1),y=prefs.getInt(CallIslandPosition.Y,-1);assertTrue(x>=0&&x<=10000&&y>0&&y<=10000);
  gestures.put(new JSONObject().put("kind","completed_external_drag").put("before",before.toShortString()).put("after",moved.toShortString()).put("x",x).put("y",y).put("call_state",Call.STATE_ACTIVE));
  drag(windowTag("island-expand"),0,dp(40),true);assertEquals(x,prefs.getInt(CallIslandPosition.X,-1));assertEquals(y,prefs.getInt(CallIslandPosition.Y,-1));assertEquals(moved,bounds(external()));assertOwned(call,token);
  gestures.put(new JSONObject().put("kind","cancelled_external_drag").put("normalized_position_preserved",true).put("geometry_restored",true).put("call_state",Call.STATE_ACTIVE));
  tap(windowTag("island-expand"));await(()->windowTag("island-action-END")!=null,"External call controls missing");
  View end=windowTag("island-action-END");reveal(end);
  Rect endDragBefore=bounds(external());int endPositionBefore=prefs.getInt(CallIslandPosition.Y,-1);
  assertTrue("This fixture requires a complete, non-overflowing control surface",external().getParent() instanceof ScrollView);
  ScrollView externalScroll=(ScrollView)external().getParent();
  assertFalse("Use the non-overflow drag path, not body scrolling",externalScroll.canScrollVertically(1)||externalScroll.canScrollVertically(-1));
  drag(end,0,dp(50),false);Rect endDragAfter=bounds(external());
  assertTrue("Drag beginning on End must move the actual window",endDragAfter.top>endDragBefore.top+dp(25));
  assertTrue("Successful End-control drag must persist a new position",prefs.getInt(CallIslandPosition.Y,-1)>endPositionBefore);
  assertOwned(call,token);assertNull("Dragging over End must cancel the child before click",windowButton(android.R.id.button1));
  gestures.put(new JSONObject().put("kind","drag_started_on_end_control").put("call_state",Call.STATE_ACTIVE).put("end_confirmation_opened",false)
   .put("before",endDragBefore.toShortString()).put("after",endDragAfter.toShortString()).put("position_before",endPositionBefore).put("position_after",prefs.getInt(CallIslandPosition.Y,-1)).put("body_scrolling_required",false));
  screenshot("modern-island-dragged.png");View hide=windowTag("island-hide");reveal(hide);tap(hide);
  await(()->external()==null,"Hide must remove external window");assertEquals(token,prefs.getString("island_hidden_token",""));assertOwned(call,token);assertTrue(ongoingNotification());
  awaitHome("hide-home");screenshot("modern-island-hide-home.png");
  CallIslandSettingsActivity settings=launch(CallIslandSettingsActivity.class);View restore=tag(settings,"island_restore");reveal(restore);tap(restore);
  assertFalse(prefs.contains("island_hidden_token"));key(KeyEvent.KEYCODE_HOME);await(()->external()!=null,"Settings Restore must show island again on Home");assertOwned(call,token);
  awaitHome("restore-home");screenshot("modern-island-restore-home.png");
  prefs.edit().putString(CallIslandPosition.MODE,"status_area").remove(CallIslandPosition.SET).remove(CallIslandPosition.X).remove(CallIslandPosition.Y).commit();settle();
  Rect externalTop=bounds(external());int statusTop=statusInset(main);assertTrue("Unprivileged external overlay must remain below protected status bar",externalTop.top>=statusTop+dp(8));
  assertOwned(call,token);awaitHome("status-external");surfaces.put(surface("external-protected-status-fallback",externalTop,token));screenshot("modern-island-status-external.png");
  ui(settings::finish);target.startActivity(new Intent(target,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
  await(()->main.hasWindowFocus()&&main.getWindow().getDecorView().findViewWithTag("island-root")!=null&&external()==null,"Own app must use one in-app island only");
  View inapp=tag(main,"island-root");Rect inappTop=bounds(inapp);assertTrue(statusInset(main)>0);assertEquals(Color.TRANSPARENT,main.getWindow().getStatusBarColor());
  surfaces.put(statusModeSurface(main,inapp,inappTop,call,token));screenshot("modern-island-status-inapp.png");
  prefs.edit().putString(CallIslandPosition.MODE,"below").remove(CallIslandPosition.SET).commit();settle();
  Rect below=bounds(tag(main,"island-root"));assertTrue(below.top>=statusInset(main));assertTrue("Normal host status color restored",main.getWindow().getStatusBarColor()!=Color.TRANSPARENT);
  assertOwned(call,token);surfaces.put(surface("inapp-default-below-restored",below,token));screenshot("modern-island-below-inapp.png");
  proof.put("nex_active",nexIslandPresentation(main,call,token));
  // Show an unmodified real app surface behind the same capsule. Sampling its empty
  // top padding avoids opaque caller-label backplates and proves compositor blending.
  // Select a real uncluttered patch before any opacity captures. Do not
  // relax pixel uniformity, brightness, guard, blend or geometry assertions.
  Rect selectedComposition=null;JSONArray attemptedPositions=new JSONArray();
  for(int position:new int[]{6000,7500,8500,5000,4000,3000,2000,1000,9000,0,10000}){
   prefs.edit().remove("island_hidden_token").putBoolean(CallIslandPosition.SET,true)
    .putInt(CallIslandPosition.X,5000).putInt(CallIslandPosition.Y,position).commit();
   await(()->main.getWindow().getDecorView().findViewWithTag("island-root")!=null,"Candidate capsule must be shown");
   settle();Rect candidate=bounds(tag(main,"island-root"));assertOwned(call,token);
   prefs.edit().putString("island_hidden_token",token).commit();
   await(()->main.getWindow().getDecorView().findViewWithTag("island-root")==null,"Composition baseline must have no island");
   settle();Bitmap probe=getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(probe);
   boolean usable;try{assertEquals(480,probe.getWidth());assertEquals(854,probe.getHeight());usable=uniformBackgroundSamples(probe,candidate)!=null;}finally{probe.recycle();}
   attemptedPositions.put(new JSONObject().put("normalized_y",position).put("bounds",candidate.toShortString()).put("uniform_baseline",usable));
   if(usable){selectedComposition=candidate;break;}
  }
  assertNotNull("Real baseline has no uniform guarded background patch at any candidate capsule position",selectedComposition);
  final Rect composition=selectedComposition;Bitmap background=capture("modern-island-composition-background.png");
  // Choose empty, uniform real background using this baseline only, before
  // any opacity image exists. Text antialiasing below a transparent layer can
  // quantize by one channel; it is not the empty padding this proof samples.
  Bitmap[] levels=new Bitmap[3];int[] opacity={0,35,100};
  try {
   int[] sampleXs=uniformBackgroundSamples(background,composition);
   assertNotNull("Saved real baseline must retain the guarded uniform light patch",sampleXs);
   for(int i=0;i<opacity.length;i++){
    prefs.edit().remove("island_hidden_token").putInt("island_background_opacity",opacity[i]).commit();
    await(()->main.getWindow().getDecorView().findViewWithTag("island-root")!=null,"Transparent island did not return");settle();
    assertEquals(composition,bounds(tag(main,"island-root")));assertEquals(1f,tag(main,"island-root").getAlpha(),0f);assertEquals(1f,tag(main,"island-caller").getAlpha(),0f);
    levels[i]=capture("modern-island-opacity-"+opacity[i]+".png");assertOwned(call,token);
   }
   JSONArray pixels=new JSONArray();
   for(int px:sampleXs){
    int py=composition.top+dp(3);
    int base=background.getPixel(px,py),zero=levels[0].getPixel(px,py),partial=levels[1].getPixel(px,py),opaque=levels[2].getPixel(px,py);
    assertEquals("Zero background opacity must expose exact real pixels",base,zero);int difference=0;
    for(int shift:new int[]{16,8,0}){int bg=(base>>shift)&255,fg=(opaque>>shift)&255,actual=(partial>>shift)&255;
     int expected=Math.round(fg*(89f/255f)+bg*(166f/255f));assertTrue("35% native compositor blend differs from real baseline",Math.abs(expected-actual)<=3);difference+=Math.abs(bg-fg);}
    assertTrue("Samples must distinguish an opaque capsule from the real background",difference>80);
    pixels.put(new JSONObject().put("x",px).put("y",py).put("background",base).put("opacity0",zero).put("opacity35",partial).put("opacity100",opaque));
   }
   proof.put("transparency",new JSONObject().put("sample_region",composition.toShortString()).put("background_is_real_app_compositor",true).put("view_alpha",1)
    .put("caller_alpha",1).put("background_alpha_35",89).put("rgb_tolerance",3).put("samples",pixels)
    .put("sample_selection","baseline-only uniform light background in central top padding")
    .put("baseline_uniform_guard_pixels",1).put("baseline_min_rgb",200).put("attempted_baseline_positions",attemptedPositions));
  }finally{background.recycle();for(Bitmap bitmap:levels)if(bitmap!=null)bitmap.recycle();}
  prefs.edit().putString("island_hidden_token",token).commit();modem("cancel");await(()->PhoneSessionService.current==null||PhoneSessionService.current.getState()==Call.STATE_DISCONNECTED,"First actual call did not end");
  key(KeyEvent.KEYCODE_HOME);modem("call");await(()->state(Call.STATE_RINGING)&&PhoneSessionService.islandSnapshot()!=null,"Second actual call did not bind");
  String second=PhoneSessionService.islandSnapshot().token;assertFalse("Call UUID must not be reused",token.equals(second));key(KeyEvent.KEYCODE_HOME);answerNative();
  await(()->external()!=null,"New actual call must show despite prior call's hidden token");assertEquals(token,prefs.getString("island_hidden_token",""));assertEquals(second,PhoneSessionService.islandSnapshot().token);
  awaitHome("new-call-home");screenshot("modern-island-new-call-shown.png");assertTrue(ongoingNotification());modem("cancel");await(()->external()==null&&(PhoneSessionService.current==null||PhoneSessionService.current.getState()==Call.STATE_DISCONNECTED),"Modem end must remove island");
  proof.put("role_held",roles.isRoleHeld(RoleManager.ROLE_DIALER)).put("default_dialer",telecom.getDefaultDialerPackage()).put("data_origin","Emulator GSM modem -> Android Telecom -> production PhoneSessionService; no injected Call/session")
   .put("launcher_package",launcherPackage).put("foreground_evidence",foregroundEvidence).put("answers",nativeAnswers)
   .put("first_token",token).put("second_token",second).put("modem_commands",modemCommands).put("gestures",gestures).put("surfaces",surfaces)
   .put("compatible_wide_native_lane","NOT_TESTED_FIXED_480PX_VIEWPORT")
   .put("hide_preserves_call_and_notification",true).put("settings_restore_on_home",true).put("new_call_ignores_previous_hidden_token",true)
   .put("single_owned_surface",true).put("protected_status_icons_remain_visible",true).put("modem_end_removes_island",true);
  proof("modern-island-native.json",proof);
 }
 private Object islandField(CallIslandView view,String name)throws Exception {Field field=CallIslandView.class.getDeclaredField(name);field.setAccessible(true);return field.get(view);}
 private JSONObject nexIslandPresentation(MainActivity main,Call call,String token)throws Exception {
  assertOwned(call,token);Map<String,?> before=prefs.getAll();Rect previous=bounds(tag(main,"island-root"));int commandCount=modemCommands.length();JSONObject result;
  try{
   assertTrue(prefs.edit().putString("island_style","nex").putString("language","he").commit());
   await(()->{AtomicReference<Boolean> ready=new AtomicReference<>(false);ui(()->{try{
    View root=main.getWindow().getDecorView().findViewWithTag("island-root");if(!(root instanceof CallIslandView))return;
    CallIslandView island=(CallIslandView)root;CallIslandState bound=(CallIslandState)islandField(island,"state");
    ready.set(bound!=null&&token.equals(bound.token)&&bound.state==Call.STATE_ACTIVE&&island.getLayoutDirection()==View.LAYOUT_DIRECTION_RTL&&
     ((Integer)islandField(island,"bodyTop"))==0xFF342720&&((Integer)islandField(island,"accent"))==0xFFF6A773);
   }catch(Exception failure){throw new RuntimeException(failure);}});return ready.get();
   },"NEX selection must rebind the same actual ACTIVE Telecom call to the visible Hebrew island");
   awaitActivityForeground(main);CallIslandView island=(CallIslandView)tag(main,"island-root");Rect actual=bounds(island);JSONObject geometry=nativeGeometry(island,actual);
   assertOwned(call,token);assertNull("NEX in-app preview must retain one owned surface",external());AtomicReference<JSONObject> colors=new AtomicReference<>();
   ui(()->{try{
    CallIslandState bound=(CallIslandState)islandField(island,"state");assertEquals(token,bound.token);assertEquals(Call.STATE_ACTIVE,bound.state);assertEquals(NUMBER,bound.number);
    int[] gradient=((GradientDrawable)island.getBackground()).getColors();assertNotNull(gradient);assertEquals(2,gradient.length);
    int top=(Integer)islandField(island,"bodyTop"),bottom=(Integer)islandField(island,"bodyBottom"),edge=(Integer)islandField(island,"edge"),accent=(Integer)islandField(island,"accent");
    assertEquals(0xFF342720,top);assertEquals(0xFF241A17,bottom);assertEquals(0xFF634D43,edge);assertEquals(0xFFF6A773,accent);assertEquals(top,gradient[0]);assertEquals(bottom,gradient[1]);
    assertEquals(1f,island.getAlpha(),0f);assertEquals(1f,island.getScaleX(),0f);assertEquals(1f,island.getScaleY(),0f);
    TextView timer=(TextView)island.findViewWithTag("island-timer");assertNotNull(timer);assertEquals(accent,timer.getCurrentTextColor());
    colors.set(new JSONObject().put("body_top_argb",top).put("body_bottom_argb",bottom).put("edge_argb",edge).put("accent_argb",accent).put("timer_argb",timer.getCurrentTextColor())
     .put("bound_view_token",bound.token).put("bound_view_state",bound.state).put("view_alpha",island.getAlpha()).put("scale_x",island.getScaleX()).put("scale_y",island.getScaleY()));
   }catch(Exception failure){throw new RuntimeException(failure);}});
   JSONObject foreground=activityForeground(main);assertNotNull(foreground);Bitmap bitmap=capture("modern-nex-island-active-he.png");JSONArray samples=new JSONArray();
   try{for(int n=0;n<5;n++){int x=actual.centerX()+n-2,y=actual.top+dp(3),color=bitmap.getPixel(x,y);assertEquals(255,Color.alpha(color));
    for(int shift:new int[]{16,8,0}){int channel=(color>>shift)&255,first=(0xFF342720>>shift)&255,last=(0xFF241A17>>shift)&255;assertTrue(channel>=Math.min(first,last)-1&&channel<=Math.max(first,last)+1);}
    samples.put(new JSONObject().put("x",x).put("y",y).put("argb",color));
   }}finally{bitmap.recycle();}
   assertOwned(call,token);assertEquals(commandCount,modemCommands.length());
   result=colors.get().put("frame","modern-nex-island-active-he.png").put("style_id",prefs.getString("island_style","")).put("language",prefs.getString("language","")).put("layout_direction","RTL")
    .put("data_origin","Android Telecom -> production PhoneSessionService -> live CallIslandView").put("token",token).put("call_state",call.getState()).put("number",call.getDetails().getHandle().getSchemeSpecificPart())
    .put("foreground",foreground).put("geometry",geometry).put("background_samples",samples).put("extra_modem_commands",modemCommands.length()-commandCount).put("same_owned_call",true).put("single_owned_surface",true);
  }finally{restore(prefs,before);settle();assertEquals(before,prefs.getAll());assertOwned(call,token);assertEquals(previous,bounds(tag(main,"island-root")));}
  return result.put("preferences_restored",true).put("previous_layout_restored",true).put("previous_bounds",previous.toShortString()).put("restored_bounds",bounds(tag(main,"island-root")).toShortString());
 }
 private JSONObject statusModeSurface(Activity activity,View capsule,Rect capsuleBounds,Call call,String token)throws Exception {
  assertOwned(call,token);awaitActivityForeground(activity);
  TextView caller=(TextView)tag(activity,"island-caller");Rect callerBounds=bounds(caller);
  JSONObject capsuleGeometry=nativeGeometry(capsule,capsuleBounds),callerGeometry=nativeGeometry(caller,callerBounds);
  AtomicReference<JSONObject> metrics=new AtomicReference<>();
  ui(()->{try{
   android.util.DisplayMetrics display=new android.util.DisplayMetrics();activity.getDisplay().getRealMetrics(display);
   assertEquals(480,display.widthPixels);assertEquals(854,display.heightPixels);assertEquals(240,display.densityDpi);
   assertEquals(1.3f,activity.getResources().getConfiguration().fontScale,.0001f);
   assertEquals(capsuleBounds.width(),capsule.getWidth());assertEquals(callerBounds.width(),caller.getWidth());
   assertTrue(capsuleBounds.contains(callerBounds));assertNotNull(caller.getLayout());assertEquals(1,caller.getLayout().getLineCount());
   assertEquals(android.text.TextUtils.TruncateAt.END,caller.getEllipsize());
   String full=caller.getText().toString();int count=full.codePointCount(0,full.length());assertTrue(count>0);
   int required=Math.min(6,count),prefixEnd=full.offsetByCodePoints(0,required);
   String minimumText=full.substring(0,prefixEnd)+(prefixEnd<full.length()?"\u2026":"");
   int omitted=caller.getLayout().getEllipsisCount(0),visibleEnd=omitted==0?full.length():caller.getLayout().getEllipsisStart(0);
   assertTrue(visibleEnd>=0&&visibleEnd<=full.length());String visible=full.substring(0,visibleEnd);
   int visibleCount=visible.codePointCount(0,visible.length());assertTrue("Actual caller layout must retain six code points or its whole shorter identity",visibleCount>=required);
   int padding=caller.getCompoundPaddingLeft()+caller.getCompoundPaddingRight();double textWidth=caller.getPaint().measureText(minimumText);
   int minimum=Math.max(capsule.getMinimumWidth(),capsule.getWidth()-caller.getWidth()+(int)Math.ceil(textWidth)+padding);
   assertTrue("Final native caller viewport must contain the measured readable prefix",caller.getWidth()>=Math.ceil(textWidth)+padding);
   int inset=statusInset(activity),lane=display.widthPixels-Math.round(144*display.density);assertTrue(inset>0&&lane>0);
   boolean supported=minimum<=lane;
   if(supported){
    assertTrue("A readable compatible lane must enter the status area",capsuleBounds.top<inset);
    assertTrue(capsuleBounds.width()<=lane);assertTrue(capsuleBounds.left>=Math.round(60*display.density));
    assertTrue(capsuleBounds.right<=display.widthPixels-Math.round(60*display.density));
   }else{
    assertTrue("An unreadable status lane must retain the complete readable capsule",capsule.getWidth()>=minimum);
    assertTrue("Readable fallback must clear the protected status bar",capsuleBounds.top>=inset+Math.round(8*display.density));
   }
   assertEquals("status_area",prefs.getString(CallIslandPosition.MODE,"below"));assertFalse(prefs.getBoolean(CallIslandPosition.SET,false));
   assertTrue(activity.getWindow().getDecorView().getRootWindowInsets().isVisible(WindowInsets.Type.statusBars()));
   metrics.set(new JSONObject().put("stage",supported?"inapp-supported-central-status-area":"inapp-protected-readable-status-fallback")
    .put("display_width",display.widthPixels).put("display_height",display.heightPixels).put("density_dpi",display.densityDpi).put("font_scale",activity.getResources().getConfiguration().fontScale)
    .put("status_inset",inset).put("safe_lane_width",lane).put("minimum_readable_width",minimum).put("capsule_minimum_width",capsule.getMinimumWidth())
    .put("caller_line_count",caller.getLayout().getLineCount()).put("caller_ellipsize",caller.getEllipsize().name())
    .put("caller_full_text",full).put("caller_code_points",count).put("caller_visible_prefix",visible).put("caller_visible_code_points",visibleCount)
    .put("caller_ellipsis_start_utf16",caller.getLayout().getEllipsisStart(0)).put("caller_ellipsis_count_utf16",omitted)
    .put("caller_minimum_text",minimumText).put("caller_minimum_text_width",textWidth).put("caller_padding_horizontal",padding)
    .put("position_mode",prefs.getString(CallIslandPosition.MODE,"below")).put("custom_position_set",prefs.getBoolean(CallIslandPosition.SET,false)));
  }catch(Exception failure){throw new RuntimeException(failure);}});
  JSONObject result=surface(metrics.get().getString("stage"),capsuleBounds,token);
  for(java.util.Iterator<String> keys=metrics.get().keys();keys.hasNext();){String key=keys.next();result.put(key,metrics.get().get(key));}
  assertEquals(capsuleGeometry.getInt("window_id"),callerGeometry.getInt("window_id"));
  return result.put("capsule_geometry",capsuleGeometry).put("caller_geometry",callerGeometry).put("status_bar_protection",protectedStatusBar(activity,capsuleBounds));
 }
 private JSONObject nativeGeometry(View view,Rect expected)throws Exception {
  assertEquals(expected,bounds(view));AtomicReference<Integer> id=new AtomicReference<>();AtomicReference<Rect> actual=new AtomicReference<>();
  ui(()->{AccessibilityNodeInfo node=view.createAccessibilityNodeInfo();try{
   Rect rect=new Rect();node.getBoundsInScreen(rect);assertEquals(expected,rect);assertTrue(view.hasWindowFocus());id.set(node.getWindowId());actual.set(rect);
  }finally{node.recycle();}});
  assertTrue(nativeWindowFocused(id.get()));
  return new JSONObject().put("bounds",expected.toShortString()).put("native_ax_bounds",actual.get().toShortString()).put("window_id",id.get())
   .put("stable_samples",2).put("view_window_focus",true).put("native_window_focus",true);
 }
 private JSONObject protectedStatusBar(Activity activity,Rect capsule)throws Exception {
  int inset=statusInset(activity);
  String packageRows=shell("cmd package list packages -U --user 0 com.android.systemui");
  Matcher packageUid=Pattern.compile("(?m)^package:com\\.android\\.systemui uid:([0-9]+)[ \\t]*$").matcher(packageRows);
  assertTrue("Actual system package registry must identify SystemUI",packageUid.find());
  String packageRecord=packageUid.group().trim();int systemUid=Integer.parseInt(packageUid.group(1));
  assertTrue(systemUid>0);assertFalse("SystemUI package registry must identify one UID",packageUid.find());
  AtomicReference<JSONObject> accepted=new AtomicReference<>();AtomicReference<String> previous=new AtomicReference<>("");
  await(()->{
   assertTrue(activity.getWindow().getDecorView().getRootWindowInsets().isVisible(WindowInsets.Type.statusBars()));
   String dump=shell("dumpsys input");int dispatcher=dump.indexOf("Input Dispatcher State:");assertTrue(dispatcher>=0);
   NativeInputDisplay display=new NativeInputDisplay(dump.substring(dispatcher));NativeInputWindow bar=null;
   for(NativeInputWindow window:inputWindows(dump)){
    if(window.uid==systemUid&&window.token&&!window.flag("NOT_VISIBLE")&&!window.flag("NO_INPUT_CHANNEL")&&window.flag("TRUSTED_OVERLAY")&&(window.name.equals("StatusBar")||window.name.endsWith(" StatusBar"))){
     assertNull("There must be one current native SystemUI StatusBar window",bar);bar=window;
    }
   }
   if(bar==null){previous.set("");return false;}
   double left=display.logicalX(bar.frame.left),right=display.logicalX(bar.frame.right),top=display.logicalY(bar.frame.top),bottom=display.logicalY(bar.frame.bottom);
   assertTrue("Native SystemUI strip must match the actual display top and width",Math.abs(left)<=1&&Math.abs(right-480)<=1&&Math.abs(top)<=1);
   assertTrue("Native SystemUI strip must agree with actual root insets",bottom>=inset-1&&bottom<=inset+2);
   // Display transforms are printed to four decimals; round its protected
   // extent outward rather than declaring one edge pixel safe for app content.
   Rect strip=new Rect(Math.max(0,(int)Math.floor(left)),Math.max(0,(int)Math.floor(top)),Math.min(480,(int)Math.ceil(right)),(int)Math.ceil(bottom));
   Rect clock=new Rect(0,0,dp(60),strip.bottom),icons=new Rect(480-dp(60),0,480,strip.bottom);
   assertFalse("App capsule must clear the protected native clock lane",Rect.intersects(capsule,clock));
   assertFalse("App capsule must clear the protected native right icon lane",Rect.intersects(capsule,icons));
   String key=bar.raw+"/"+display.raw+"/"+strip.toShortString()+"/"+inset;
   if(!key.equals(previous.get())){previous.set(key);return false;}
   accepted.set(new JSONObject().put("system_ui_package","com.android.systemui").put("system_ui_package_record",packageRecord).put("system_ui_uid",systemUid)
    .put("native_window",bar.json()).put("window_has_input_token",bar.token).put("window_visible",!bar.flag("NOT_VISIBLE"))
    .put("mapped_status_bounds",strip.toShortString()).put("display_transform",display.json()).put("root_status_inset",inset)
    .put("root_status_bars_visible",true).put("clock_lane_bounds",clock.toShortString()).put("right_icon_lane_bounds",icons.toShortString())
    .put("clock_lane_clear",true).put("right_icon_lane_clear",true).put("stable_samples",2)
    .put("proof_scope","Native SystemUI StatusBar window, root WindowInsets and protected geometric lanes; icon visual presence is reviewed in the real capture"));
   return true;
  },"Actual native SystemUI status window and protected icon lanes must remain visible, stable and unobscured");return accepted.get();
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
 private boolean callerIdentity(AccessibilityNodeInfo node,String identity){
  String text=String.valueOf(node.getText()),description=String.valueOf(node.getContentDescription());
  return text.contains(identity)||description.contains(identity);
 }
 private AccessibilityNodeInfo callerRow(AccessibilityNodeInfo node,String identity,int depth,int[] budget){
  if(node==null||depth>70||--budget[0]<0)return null;
  if(node.isVisibleToUser()&&callerIdentity(node,identity)){
   AccessibilityNodeInfo ancestor=node;
   for(int parents=0;ancestor!=null&&parents<24;parents++,ancestor=ancestor.getParent()){
    String id=String.valueOf(ancestor.getViewIdResourceName()),type=String.valueOf(ancestor.getClassName());
    if((id.endsWith("/expandableNotificationRow")||type.endsWith("ExpandableNotificationRow"))&&ancestor.isVisibleToUser()){
     Rect bounds=new Rect();ancestor.getBoundsInScreen(bounds);if(!bounds.isEmpty())return ancestor;break;
    }
   }
  }
  for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo found=callerRow(node.getChild(i),identity,depth+1,budget);if(found!=null)return found;}
  return null;
 }
 private AccessibilityNodeInfo nativeAnswer(String identity){
  for(AccessibilityWindowInfo window:getInstrumentation().getUiAutomation().getWindows()){
   if(window.getType()!=AccessibilityWindowInfo.TYPE_SYSTEM)continue;
   AccessibilityNodeInfo root=window.getRoot();
   if(root!=null&&"com.android.systemui".contentEquals(String.valueOf(root.getPackageName()))){
    AccessibilityNodeInfo row=callerRow(root,identity,0,new int[]{5000});AccessibilityNodeInfo answer=answerNode(row);if(answer!=null)return answer;
   }
  }return null;
 }
 private boolean incomingNotificationMatches(String identity){
  for(StatusBarNotification item:target.getSystemService(NotificationManager.class).getActiveNotifications()){
   if(item.getId()!=1502||!target.getPackageName().equals(item.getPackageName()))continue;
   Notification notification=item.getNotification();
   android.app.NotificationChannel channel=target.getSystemService(NotificationManager.class).getNotificationChannel(CallNotifications.INCOMING_CHANNEL);
   Person person=notification.extras.getParcelable(Notification.EXTRA_CALL_PERSON,Person.class);
   return CallNotifications.INCOMING_CHANNEL.equals(notification.getChannelId())&&notification.fullScreenIntent!=null&&channel!=null
    &&channel.getImportance()==NotificationManager.IMPORTANCE_HIGH&&person!=null&&identity.contentEquals(person.getName())
    &&notification.extras.getParcelable(Notification.EXTRA_ANSWER_INTENT,PendingIntent.class)!=null
    &&notification.extras.getParcelable(Notification.EXTRA_DECLINE_INTENT,PendingIntent.class)!=null
    &&String.valueOf(notification.extras.getString(Notification.EXTRA_TEMPLATE)).endsWith("$CallStyle");
  }return false;
 }
 private void answerNative()throws Exception {
  Call owned=PhoneSessionService.current;CallIslandState initial=PhoneSessionService.islandSnapshot();assertNotNull(owned);assertNotNull(initial);
  String token=initial.token,identity=initial.name.trim().isEmpty()?initial.number:initial.name;
  assertEquals(NUMBER,initial.number);assertEquals(NUMBER,owned.getDetails().getHandle().getSchemeSpecificPart());
  String configured=shell("cmd overlay lookup com.android.systemui com.android.systemui:integer/touch_acceptance_delay").trim();assertTrue(configured.matches("[0-9]+"));int delay=Integer.parseInt(configured);assertTrue(delay>=0&&delay<=3000);
  long required=Math.max(350,delay+100),started=SystemClock.uptimeMillis(),stable=-1,stableMillis=0;Rect previous=new Rect();int window=-1;AccessibilityNodeInfo answer=null;
  while(SystemClock.uptimeMillis()-started<12000){
   CallIslandState current=PhoneSessionService.islandSnapshot();
   String currentIdentity=current==null?"":current.name.trim().isEmpty()?current.number:current.name;
   // Contact lookup may resolve after Telecom first binds. Keep the same owned
   // call/number, but restart the native touch guard for its newly rendered row.
   if(!identity.equals(currentIdentity)){identity=currentIdentity;stable=-1;}
   boolean currentOwned=current!=null&&PhoneSessionService.current==owned&&token.equals(current.token)&&NUMBER.equals(current.number);
   AccessibilityNodeInfo candidate=currentOwned&&!identity.isEmpty()?nativeAnswer(identity):null;
   Rect now=new Rect();if(candidate!=null)candidate.getBoundsInScreen(now);
   boolean ready=currentOwned&&candidate!=null&&candidate.isEnabled()&&!now.isEmpty()&&state(Call.STATE_RINGING)&&incomingNotificationMatches(identity);
   if(ready){
    if(stable<0||!now.equals(previous)||window!=candidate.getWindowId())stable=SystemClock.uptimeMillis();previous.set(now);window=candidate.getWindowId();
    if(SystemClock.uptimeMillis()-stable>=required){
     AccessibilityNodeInfo fresh=nativeAnswer(identity);Rect freshBounds=new Rect();if(fresh!=null)fresh.getBoundsInScreen(freshBounds);
     if(fresh!=null&&fresh.isEnabled()&&window==fresh.getWindowId()&&now.equals(freshBounds)){answer=fresh;stableMillis=SystemClock.uptimeMillis()-stable;break;}
     stable=-1;
    }
   }else stable=-1;Thread.sleep(50);
  }
  assertNotNull("Owned-caller SystemUI Answer must settle beyond native touch guard",answer);assertTrue(new Rect(0,0,480,854).contains(previous));
  assertSame(owned,PhoneSessionService.current);assertEquals(token,PhoneSessionService.islandSnapshot().token);assertTrue(state(Call.STATE_RINGING));assertTrue(incomingNotificationMatches(identity));
  long down=SystemClock.uptimeMillis();pointer(down,MotionEvent.ACTION_DOWN,previous.centerX(),previous.centerY());Thread.sleep(50);pointer(down,MotionEvent.ACTION_UP,previous.centerX(),previous.centerY());
  await(()->PhoneSessionService.current==owned&&state(Call.STATE_ACTIVE)&&PhoneSessionService.islandSnapshot()!=null&&token.equals(PhoneSessionService.islandSnapshot().token),"Native SystemUI pointer answer did not activate its owned Telecom call");
  collapseShade();
  nativeAnswers.put(new JSONObject().put("token",token).put("number",NUMBER).put("window_id",window).put("bounds",previous.toShortString())
   .put("stable_millis",stableMillis).put("configured_touch_acceptance_delay_millis",delay).put("input_method","native-pointer").put("native_caller_row",true));
 }
 private void resolveHome()throws Exception {
  String output=shell("cmd package resolve-activity --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME");ComponentName home=null;
  for(String line:output.split("\\n")){ComponentName candidate=ComponentName.unflattenFromString(line.trim());if(candidate!=null)home=candidate;}
  assertNotNull("Platform must resolve its actual Home component: "+output,home);launcherPackage=home.getPackageName();
  assertFalse("A resolver is not the Home app","android".equals(launcherPackage));
 }
 private boolean homeResumed()throws Exception {
  boolean resumed=false;for(String line:shell("dumpsys activity activities").split("\\n"))
   if((line.contains("mResumedActivity")||line.contains("topResumedActivity"))&&line.contains(" "+launcherPackage+"/")){resumed=true;break;}
  return resumed;
 }
 private boolean homeNativeVisible(){
  for(AccessibilityWindowInfo window:getInstrumentation().getUiAutomation().getWindows()){
   if(window.getType()!=AccessibilityWindowInfo.TYPE_APPLICATION)continue;AccessibilityNodeInfo root=window.getRoot();
   if(window.isFocused()&&root!=null&&root.isVisibleToUser()&&launcherPackage.contentEquals(String.valueOf(root.getPackageName())))return true;
  }return false;
 }
 private void awaitHome(String stage)throws Exception {
  await(()->homeResumed()&&homeNativeVisible(),"Actual native Home foreground missing: "+stage);
  assertTrue(homeResumed());assertTrue(homeNativeVisible());
  foregroundEvidence.put(new JSONObject().put("stage",stage).put("foreground_package",launcherPackage).put("top_resumed",true).put("native_application_window_visible",true));
 }
 private static void restore(SharedPreferences preferences,Map<String,?> values){
  SharedPreferences.Editor editor=preferences.edit().clear();for(Map.Entry<String,?> item:values.entrySet()){
   Object value=item.getValue();String key=item.getKey();if(value instanceof String)editor.putString(key,(String)value);else if(value instanceof Boolean)editor.putBoolean(key,(Boolean)value);
   else if(value instanceof Integer)editor.putInt(key,(Integer)value);else if(value instanceof Long)editor.putLong(key,(Long)value);else if(value instanceof Float)editor.putFloat(key,(Float)value);
   else if(value instanceof java.util.Set)editor.putStringSet(key,new HashSet<>((java.util.Set<String>)value));
  }assertTrue(editor.commit());
 }
}
