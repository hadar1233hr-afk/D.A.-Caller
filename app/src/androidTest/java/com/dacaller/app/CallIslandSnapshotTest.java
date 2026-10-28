package com.dacaller.app;

import android.app.Activity;
import android.animation.ValueAnimator;
import android.app.Instrumentation;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.test.InstrumentationTestCase;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inspector.WindowInspector;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;
import org.json.JSONArray;
import org.xmlpull.v1.XmlSerializer;

/** Native window/layout/interaction evidence. Session state is deliberately synthetic. */
@SuppressWarnings({"deprecation","unchecked"})
@android.annotation.TargetApi(33)
public final class CallIslandSnapshotTest extends InstrumentationTestCase {
 private static final String DIRECTORY="/sdcard/Download/DA-CALLER-snapshots";
 private static boolean matrixPassed,actionsPassed,settingsPassed,animationsPassed,appMainPassed,settingsBarsPassed;
 private int settingsBarCaptures;
 private Context target;
 private SharedPreferences prefs;
 private Map<String,?> original;
 private String originalFont,originalDuration;
 private int originalAccessibilityFlags;
 private Activity diagnosticHost;
 private String lastTap="none";
 private CallIslandFixtureActivity host;
 private final List<Activity> launched=new ArrayList<>();
 @Override protected void setUp()throws Exception{
  super.setUp();target=getInstrumentation().getTargetContext();prefs=target.getSharedPreferences("da_caller",Context.MODE_PRIVATE);
  original=prefs.getAll();originalFont=shell("settings get system font_scale").trim();
  originalDuration=shell("settings get global animator_duration_scale").trim();
  AccessibilityServiceInfo accessibility=getInstrumentation().getUiAutomation().getServiceInfo();originalAccessibilityFlags=accessibility.flags;
  accessibility.flags|=AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS|AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
  getInstrumentation().getUiAutomation().setServiceInfo(accessibility);
  shell("mkdir -p "+DIRECTORY);shell("settings put system font_scale 1.3");Thread.sleep(300);
  prefs.edit().putBoolean("island_enabled",true).putBoolean("island_animations",false)
   .putBoolean("island_show_timer",true).putBoolean("island_show_photo",true)
   .putBoolean("island_show_mute",true).putBoolean("island_show_speaker",true).putBoolean("island_show_hold",true)
   .putBoolean("island_confirm_end",true).putString("island_size","compact").putString("avatar_shape","circle")
   .putBoolean("haptics",false).putBoolean("amoled",false).commit();
 }
 @Override protected void tearDown()throws Exception{
  try{
   for(int i=launched.size()-1;i>=0;i--){Activity activity=launched.get(i);if(activity!=null&&!activity.isDestroyed())ui(activity::finish);}
   settle();restore(prefs,original);shell("settings put system font_scale "+(originalFont.matches("[0-9.]+")?originalFont:"1.0"));
   shell("settings put global animator_duration_scale "+(originalDuration.matches("[0-9.]+")?originalDuration:"1.0"));
  }finally{
   AccessibilityServiceInfo accessibility=getInstrumentation().getUiAutomation().getServiceInfo();accessibility.flags=originalAccessibilityFlags;
   getInstrumentation().getUiAutomation().setServiceInfo(accessibility);super.tearDown();
  }
 }
 private void ui(Runnable action)throws Exception{
  AtomicReference<Throwable> error=new AtomicReference<>();getInstrumentation().runOnMainSync(()->{try{action.run();}catch(Throwable failure){error.set(failure);}});
  if(error.get()!=null)throw new AssertionError(error.get());
 }
 private void settle()throws Exception{getInstrumentation().waitForIdleSync();Thread.sleep(180);getInstrumentation().waitForIdleSync();}
 private interface Check{boolean ready();}
 private void await(Check check,String failure)throws Exception{
  long end=SystemClock.uptimeMillis()+7000;
  do{getInstrumentation().waitForIdleSync();if(check.ready())return;Thread.sleep(40);}while(SystemClock.uptimeMillis()<end);fail(failure);
 }
 private int dp(float n){return Math.round(n*target.getResources().getDisplayMetrics().density);}
 private View view(Activity activity,String tag){return activity.getWindow().getDecorView().findViewWithTag(tag);}
 private View tagged(Activity activity,String tag){View result=view(activity,tag);assertNotNull("Missing "+tag,result);return result;}
 private <T extends View>T find(View root,Class<T> type){
  if(type.isInstance(root))return type.cast(root);
  if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){
   T match=find(((ViewGroup)root).getChildAt(i),type);if(match!=null)return match;
  }return null;
 }
 private void visible(View view){
  Rect bounds=new Rect();assertTrue("Hidden "+view.getTag(),view.isShown()&&view.getGlobalVisibleRect(bounds));
  assertTrue("Horizontal clipping "+view.getTag(),bounds.width()>=view.getWidth()-1);
  assertTrue("Vertical clipping "+view.getTag(),bounds.height()>=view.getHeight()-1);
 }
 private void textFits(View root){
  if(!root.isShown())return;
  if(root instanceof TextView&&((TextView)root).length()>0){
   TextView text=(TextView)root;assertNotNull(text.getLayout());
   int inner=text.getHeight()-text.getCompoundPaddingTop()-text.getCompoundPaddingBottom();
   assertTrue("Clipped glyph rows "+text.getText(),text.getLayout().getHeight()<=inner+1);
  }
  if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)textFits(((ViewGroup)root).getChildAt(i));
 }
 private void tap(View view)throws Exception{
  // getLocationOnScreen maps the local origin through rotations. Adding an
  // untransformed half-size misses rotated controls (the collapse chevron is
  // rotated 180 degrees). Use the actual visible screen bounds instead.
  int[] point=new int[2];String[] details=new String[1];ui(()->{
   visible(view);Rect bounds=new Rect();assertTrue(view.getGlobalVisibleRect(bounds));
   int[] screen=new int[2],window=new int[2];view.getLocationOnScreen(screen);view.getLocationInWindow(window);
   int offsetX=screen[0]-window[0],offsetY=screen[1]-window[1];
   // A dialog has its own window origin. The same transformed local origin in
   // both calls cancels rotation while retaining the window-to-screen offset.
   point[0]=bounds.centerX()+offsetX;point[1]=bounds.centerY()+offsetY;
   details[0]=String.valueOf(view.getTag())+" visible="+bounds.toShortString()+" window-offset="+offsetX+","+offsetY;
  });
  lastTap=details[0]+" @ "+point[0]+","+point[1]
   +(view.getId()==android.R.id.button1?" native-positive="+nativePositiveButtonBounds():"");
  tapPoint(point[0],point[1]);
 }
 private String nativePositiveButtonBounds(){
  AccessibilityNodeInfo active=getInstrumentation().getUiAutomation().getRootInActiveWindow();
  String found=nativePositiveButtonBounds(active,0,new int[]{4000});if(found!=null)return found;
  for(AccessibilityWindowInfo window:getInstrumentation().getUiAutomation().getWindows()){
   found=nativePositiveButtonBounds(window.getRoot(),0,new int[]{4000});if(found!=null)return found;
  }return "not exposed";
 }
 private String nativePositiveButtonBounds(AccessibilityNodeInfo node,int depth,int[] budget){
  if(node==null||depth>60||--budget[0]<0)return null;
  if(node.isVisibleToUser()&&"android:id/button1".equals(String.valueOf(node.getViewIdResourceName()))
    &&target.getPackageName().contentEquals(String.valueOf(node.getPackageName()))){
   Rect bounds=new Rect();node.getBoundsInScreen(bounds);return bounds.toShortString()+" text="+node.getText();
  }
  for(int i=0;i<node.getChildCount();i++){String result=nativePositiveButtonBounds(node.getChild(i),depth+1,budget);if(result!=null)return result;}
  return null;
 }
 private void tapPoint(int x,int y)throws Exception{
  long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0);
  MotionEvent up=MotionEvent.obtain(now,now+45,MotionEvent.ACTION_UP,x,y,0);
  getInstrumentation().sendPointerSync(down);getInstrumentation().sendPointerSync(up);down.recycle();up.recycle();settle();
 }
 private void snapshot(String name)throws Exception{
  settle();String path=DIRECTORY+"/island-"+name+".png";shell("screencap -p "+path);
  assertEquals("regular file",shell("stat -c %F "+path).trim());assertTrue(Long.parseLong(shell("stat -c %s "+path).trim())>1000);
 }
 private void launch(String language,boolean dark,String style,int state,boolean noAudio)throws Exception{
  if(host!=null&&!host.isDestroyed()){ui(host::finish);settle();}
  prefs.edit().putString("language",language).putBoolean("dark",dark).putBoolean("amoled",false)
   .putString("island_style",style).putBoolean("island_enabled",true).commit();
  host=(CallIslandFixtureActivity)getInstrumentation().startActivitySync(new Intent(target,CallIslandFixtureActivity.class)
   .putExtra("state",state).putExtra("no_audio",noAudio).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));launched.add(host);settle();
  diagnosticHost=host;
  await(()->host.hasWindowFocus()&&view(host,"island-root")!=null,"Island did not attach to the focused native Activity");
  assertEquals(1.3f,host.getResources().getConfiguration().fontScale,0.05f);
 }
 private void expanded()throws Exception{
  if(!host.controller.isExpanded())tap(tagged(host,"island-expand"));
  await(()->host.controller.isExpanded(),"Island did not expand");settle();
 }
 private void geometry()throws Exception{
  ui(()->{
   View island=tagged(host,"island-root");visible(island);textFits(island);
   assertTrue("Island must preserve usable contacts below it",host.list.getHeight()>=dp(72));
   Rect islandBounds=new Rect(),contentBounds=new Rect();island.getGlobalVisibleRect(islandBounds);host.content.getGlobalVisibleRect(contentBounds);
   assertTrue("Island overlays the first content row",contentBounds.top>=islandBounds.bottom);
   assertTrue("Island has a usable touch height",island.getHeight()>=dp(48));
   assertTrue("Caller must be present",((TextView)tagged(host,"island-caller")).getText().toString().contains(host.session.value.name));
  });
 }
 public void testAllStylesLanguagesThemesAndCallStatesAtLargeFont()throws Exception{
  checked("matrix",this::matrixScenario);
 }
 private void matrixScenario()throws Exception{
  for(boolean dark:new boolean[]{false,true})for(String language:new String[]{"he","en"})for(String style:new String[]{"apple","glass","emerald"}){
   launch(language,dark,style,Call.STATE_ACTIVE,false);String suffix=style+"-"+(dark?"dark":"light")+"-"+language;
   assertFalse(host.controller.isExpanded());geometry();snapshot(suffix+"-compact");
   expanded();geometry();snapshot(suffix+"-expanded");
   tap(tagged(host,"island-collapse"));await(()->!host.controller.isExpanded(),"Header collapse failed");geometry();
  }
  int[] states={Call.STATE_RINGING,Call.STATE_DIALING,Call.STATE_HOLDING,Call.STATE_ACTIVE};
  String[] labels={"incoming","outgoing","held","noaudio"};
  for(int i=0;i<states.length;i++){
   launch("he",true,"apple",states[i],i==3);expanded();geometry();
   if(i==0){visible(tagged(host,"island-action-ANSWER"));visible(tagged(host,"island-action-DECLINE"));}
   if(i==2)visible(tagged(host,"island-action-RESUME"));
   if(i==3){assertNull(view(host,"island-action-MUTE_ON"));assertNull(view(host,"island-action-SPEAKER_ON"));}
   snapshot("state-"+labels[i]);
  }
  actualMainScreens();
  matrixPassed=true;writeEvidence();
 }
 private Object mainField(MainActivity main,String name){
  try{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return field.get(main);}
  catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
 }
 private void mainField(MainActivity main,String name,Object value){
  try{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);field.set(main,value);}
  catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
 }
 private void renderMain(MainActivity main,String page){
  mainField(main,"page",page);
  try{Method render=MainActivity.class.getDeclaredMethod("render");render.setAccessible(true);render.invoke(main);}
  catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
 }
 private boolean historyLoading(){
  synchronized(CallHistory.class){
   try{Field loading=CallHistory.class.getDeclaredField("loading");loading.setAccessible(true);return loading.getBoolean(null);}
   catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
  }
 }
 private void isolateMainProviderFixture(MainActivity app)throws Exception{
  assertFalse("Provider fences must never block the UI thread",android.os.Looper.myLooper()==android.os.Looper.getMainLooper());
  android.os.Handler owner=(android.os.Handler)mainField(app,"handler");
  Runnable contactsReload=(Runnable)mainField(app,"reloadChangedContacts"),callsReload=(Runnable)mainField(app,"reloadChangedCalls");
  ui(()->{
   // Only this screenshot Activity is isolated. A late real-provider refresh
   // must not replace its fictional rows after the compact capture. Production
   // observers and provider behavior remain unchanged, including other tests.
   app.getContentResolver().unregisterContentObserver((android.database.ContentObserver)mainField(app,"contactsObserver"));
   app.getContentResolver().unregisterContentObserver((android.database.ContentObserver)mainField(app,"callsObserver"));
   owner.removeCallbacks(contactsReload);owner.removeCallbacks(callsReload);
   mainField(app,"reloadContactsPending",false);
  });
  ExecutorService contactsWorker=(ExecutorService)mainField(app,"CONTACTS_IO");
  long deadline=SystemClock.uptimeMillis()+5000,quietSince=0;
  while(SystemClock.uptimeMillis()<deadline){
   // Unregistration cannot retract observer notifications already posted to
   // the main Looper. Drain those first, then remove only their debounced work.
   getInstrumentation().waitForIdleSync();
   boolean[] pending={false};
   ui(()->{
    pending[0]=owner.hasCallbacks(contactsReload)||owner.hasCallbacks(callsReload)
      ||Boolean.TRUE.equals(mainField(app,"loadingContacts"))||Boolean.TRUE.equals(mainField(app,"reloadContactsPending"));
    owner.removeCallbacks(contactsReload);owner.removeCallbacks(callsReload);mainField(app,"reloadContactsPending",false);
   });
   long remaining=deadline-SystemClock.uptimeMillis();assertTrue("Contacts provider fence exceeded its deadline",remaining>0);
   contactsWorker.submit(()->{}).get(remaining,TimeUnit.MILLISECONDS);
   remaining=deadline-SystemClock.uptimeMillis();assertTrue("Call-history provider fence exceeded its deadline",remaining>0);
   CallHistory.worker.submit(()->{}).get(remaining,TimeUnit.MILLISECONDS);
   // Worker completion can post cache assignments and renders to the UI. They
   // must finish before synthetic state is installed, not merely loading=false.
   getInstrumentation().waitForIdleSync();
   boolean[] quiet={false};
   ui(()->quiet[0]=!Boolean.TRUE.equals(mainField(app,"loadingContacts"))
     &&!Boolean.TRUE.equals(mainField(app,"reloadContactsPending"))
     &&!owner.hasCallbacks(contactsReload)&&!owner.hasCallbacks(callsReload));
   long now=SystemClock.uptimeMillis();
   if(!pending[0]&&quiet[0]&&!historyLoading()){
    if(quietSince==0)quietSince=now;
    if(now-quietSince>=500)return;
   }else quietSince=0;
   // Observe quiescence longer than the350ms contacts debounce. Three immediate
   // passes alone could finish before an already-sent Binder callback arrives.
   Thread.sleep(40);
  }
  fail("Screenshot provider workers and observer callbacks did not become quiescent");
 }
 private void assertFictionalMainData(MainActivity app,ListView list)throws Exception{
  ui(()->{
   assertEquals("A real contacts refresh replaced the isolated fictional dataset",20,((List<?>)mainField(app,"contacts")).size());
   assertEquals("A real history refresh replaced the isolated fictional calls",20,CallHistory.entries.size());
   assertSame("Provider refresh rebuilt the screenshot list",tagged(app,"call-island-page-list"),list);
   assertTrue("The measured screenshot list must remain attached",list.isAttachedToWindow());
   assertNotNull("The screenshot list lost its fictional adapter",list.getAdapter());
   assertEquals("The current screenshot adapter must retain all20 fictional rows",20,list.getAdapter().getCount());
   assertEquals("The six fictional favorites must remain configured",6,prefs.getStringSet("favorites",Collections.emptySet()).size());
  });
 }
 /** Real MainActivity/FastPages chrome and lists; only the call state and contact rows are fictional. */
 private void actualMainScreens()throws Exception{
  if(host!=null&&!host.isDestroyed()){ui(host::finish);settle();}
  shell("pm grant "+target.getPackageName()+" android.permission.READ_CONTACTS");
  shell("pm grant "+target.getPackageName()+" android.permission.READ_CALL_LOG");
  List<CallHistory.Entry> oldEntries=CallHistory.entries;CallHistory.Summary oldSummary=CallHistory.summary;
  long oldFetched=CallHistory.fetchedAt;boolean oldFailure=CallHistory.readFailed;
  SharedPreferences navigation=target.getSharedPreferences(CallReturnNavigation.PREFERENCES,Context.MODE_PRIVATE);
  Map<String,?> oldNavigation=navigation.getAll();
  prefs.edit().putString("language","he").putBoolean("dark",true).putBoolean("amoled",false)
   .putString("island_style","apple").putBoolean("island_enabled",true).putBoolean("island_animations",false)
   .putBoolean("role_prompted",true).putBoolean("notifications_prompted",true).putBoolean("voice_permission_prompted",true)
   .putString(CallArchive.PREF,CallArchive.OFF).putString("start_page","contacts")
   .putBoolean("show_stats",true).putBoolean("favorites_expanded",true).putBoolean("stats_expanded",false).commit();
  MainActivity main=null;CallIslandController appIsland=null;
  try{
   main=(MainActivity)getInstrumentation().startActivitySync(new Intent(target,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   diagnosticHost=main;
   launched.add(main);final MainActivity app=main;
   await(()->app.hasWindowFocus(),"Actual MainActivity never received window focus");
   isolateMainProviderFixture(app);
   String[] names={"דנה לדוגמה","אורי לדוגמה","נועה לדוגמה","אדם לדוגמה","שירה לדוגמה","יוסף לדוגמה","תמר לדוגמה","יעל לדוגמה","דניאל לדוגמה","אריאל לדוגמה"};
   ArrayList<Contacts.Person> people=new ArrayList<>();Set<String> favorites=new HashSet<>();
   for(int i=0;i<20;i++){Contacts.Person person=new Contacts.Person(names[i%names.length]+" "+String.format(java.util.Locale.ROOT,"%02d",i),"0500098"+String.format(java.util.Locale.ROOT,"%03d",i),640000+i);people.add(person);if(i<6)favorites.add(person.key);}
   ui(()->{
    prefs.edit().putStringSet("favorites",favorites).commit();
    mainField(app,"contacts",Collections.unmodifiableList(people));mainField(app,"contactsLoaded",true);mainField(app,"fastPages",new FastPages(app));
    app.nameIndex().clear();for(Contacts.Person person:people)app.nameIndex().put(Contacts.canonical(person.phone),person.name);
    ArrayList<CallHistory.Entry> calls=new ArrayList<>();long now=System.currentTimeMillis();
    for(int i=0;i<people.size();i++)calls.add(new CallHistory.Entry(880000+i,people.get(i).phone,now-i*60000,60+i*9,i%3+1));
    CallHistory.entries=Collections.unmodifiableList(calls);CallHistory.readFailed=false;CallHistory.fetchedAt=now;
    CallHistory.Summary summary=new CallHistory.Summary();
    for(int period=0;period<4;period++){summary.calls[period]=20;summary.seconds[period]=2400;summary.incoming[period]=11;summary.outgoing[period]=5;summary.missed[period]=4;}
    CallHistory.summary=summary;renderMain(app,"contacts");
   });settle();
   CallIslandState sample=new CallIslandState("island-actual-main","נועה בדיקת אי","0500099971",Call.STATE_ACTIVE,CallAudioState.ROUTE_EARPIECE,
     CallAudioState.ROUTE_EARPIECE|CallAudioState.ROUTE_SPEAKER,false,true,true,System.currentTimeMillis()-11_000,-1);
   CallIslandSession readOnly=new CallIslandSession(){
    public CallIslandState current(){return sample;}
    public boolean perform(String token,String action){throw new AssertionError("Actual-app screenshot must not dispatch a call command: "+action);}
    public void open(Activity activity,String token){throw new AssertionError("Actual-app screenshot must not launch a call");}
   };
   appIsland=new CallIslandController(app,readOnly);final CallIslandController controller=appIsland;ui(controller::start);settle();
   assertEquals(1.3f,app.getResources().getConfiguration().fontScale,0.05f);
   for(String page:new String[]{"contacts","recents"}){
    ui(()->renderMain(app,page));settle();await(()->view(app,"island-root")!=null,"Island did not reattach to actual MainActivity "+page);
    if(controller.isExpanded()){tap(tagged(app,"island-collapse"));settle();}
    if("contacts".equals(page)){ui(()->{EditText search=find(app.getWindow().getDecorView(),EditText.class);assertNotNull(search);search.setShowSoftInputOnFocus(false);search.setText("לדוגמה");});settle();}
    ListView list=find(app.getWindow().getDecorView(),ListView.class);assertNotNull("Actual FastPages list missing",list);
    await(()->list.getAdapter()!=null&&list.getAdapter().getCount()==20,"Actual FastPages fictional dataset was overwritten");
    ui(()->list.setSelectionFromTop(4,-7));settle();
    assertFictionalMainData(app,list);
    long identity=list.getAdapter().getItemId(list.getFirstVisiblePosition());int top=list.getChildAt(0).getTop();
    actualMainGeometry(app,false,"recents".equals(page));snapshot("app-"+page+"-compact");
    assertFictionalMainData(app,list);
    tap(tagged(app,"island-expand"));await(controller::isExpanded,"Actual MainActivity island did not expand");settle();
    assertFictionalMainData(app,list);
    actualMainGeometry(app,true,"recents".equals(page));
    if("contacts".equals(page))assertEquals("לדוגמה",find(app.getWindow().getDecorView(),EditText.class).getText().toString());
    if(list.getHeight()>=dp(48)&&list.getChildCount()>0){assertEquals(identity,list.getAdapter().getItemId(list.getFirstVisiblePosition()));assertEquals(top,list.getChildAt(0).getTop());}
    snapshot("app-"+page+"-expanded");
    tap(tagged(app,"island-collapse"));await(()->!controller.isExpanded(),"Actual MainActivity island did not collapse");settle();
    assertFictionalMainData(app,list);
    visible(tagged(app,"floating_dial"));visible(tagged(app,"call-island-secondary"));
    assertEquals("Main list identity changed after expand/collapse",identity,list.getAdapter().getItemId(list.getFirstVisiblePosition()));
    assertEquals("Main list offset changed after expand/collapse",top,list.getChildAt(0).getTop());
   }
   appMainPassed=true;
  }catch(Throwable failure){captureFailure("app-main",failure);throwFailure(failure);
  }finally{
   final CallIslandController cleanup=appIsland;if(cleanup!=null)ui(cleanup::stop);
   final MainActivity cleanupActivity=main;if(cleanupActivity!=null&&!cleanupActivity.isDestroyed())ui(cleanupActivity::finish);settle();
   CallHistory.entries=oldEntries;CallHistory.summary=oldSummary;CallHistory.fetchedAt=oldFetched;CallHistory.readFailed=oldFailure;restore(navigation,oldNavigation);
  }
 }
 private void actualMainGeometry(MainActivity app,boolean expanded,boolean recents)throws Exception{
  ui(()->{
   View island=tagged(app,"island-root");visible(island);textFits(island);
   View page=(View)mainField(app,"root"),nav=(View)mainField(app,"nav");visible(nav);visible(tagged(app,"favorites_strip"));
   if(recents)visible(tagged(app,"stats_summary"));
   Rect islandBounds=new Rect(),pageBounds=new Rect();island.getGlobalVisibleRect(islandBounds);assertTrue(page.getGlobalVisibleRect(pageBounds));
   assertTrue("Actual MainActivity content overlaps call controls",pageBounds.top>=islandBounds.bottom);
   ListView list=find(app.getWindow().getDecorView(),ListView.class);assertNotNull(list);
   assertTrue((expanded?"Expanded":"Compact")+" island must leave a usable real app list",list.getHeight()>=dp(60));
   View dial=tagged(app,"floating_dial");
   if(dial.isShown()){
    Rect dialBounds=new Rect();assertTrue(dial.getGlobalVisibleRect(dialBounds));
    ArrayList<View> protectedControls=new ArrayList<>();protectedControls.add(tagged(app,"favorites_strip"));
    if(recents)protectedControls.add(tagged(app,"stats_summary"));
    EditText search=find(page,EditText.class);if(search!=null&&search.isShown())protectedControls.add(search);
    for(View control:protectedControls){Rect bounds=new Rect();assertTrue(control.getGlobalVisibleRect(bounds));
     assertFalse("Floating dial overlaps "+control.getTag(),Rect.intersects(dialBounds,bounds));}
   }
  });
 }
 private View dialogButton(int id){
  for(View window:WindowInspector.getGlobalWindowViews()){View button=window.findViewById(id);if(button!=null&&button.isShown())return button;}
  return null;
 }
 private void assertAnchor(long identity,int top,String query)throws Exception{
  ui(()->{
   assertEquals(query,host.query.getText().toString());assertNotNull(host.list.getChildAt(0));
   assertEquals(identity,host.list.getAdapter().getItemId(host.list.getFirstVisiblePosition()));assertEquals(top,host.list.getChildAt(0).getTop());
  });
 }
 private ValueAnimator animator(View waveform){
  if(waveform==null)return null;
  try{Field field=IslandWaveformView.class.getDeclaredField("animator");field.setAccessible(true);return (ValueAnimator)field.get(waveform);}
  catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
 }
 private float phase(View waveform){
  try{Field field=IslandWaveformView.class.getDeclaredField("phase");field.setAccessible(true);return field.getFloat(waveform);}
  catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
 }
 private boolean animationRunning(View waveform){ValueAnimator animator=animator(waveform);return animator!=null&&animator.isRunning();}
 public void testCommandsConfirmationPauseReturnRecreationAndContentRestore()throws Exception{
  checked("actions",this::interactionScenario);
 }
 private void interactionScenario()throws Exception{
  launch("en",false,"apple",Call.STATE_ACTIVE,false);
  ui(()->{host.query.setText("Preserved query");host.list.setSelectionFromTop(28,-9);});settle();
  final long identity=host.list.getAdapter().getItemId(host.list.getFirstVisiblePosition());final int top=host.list.getChildAt(0).getTop();
  expanded();tap(tagged(host,"island-action-MUTE_ON"));await(()->host.session.value.muted,"Mute action was not acknowledged");
  tap(tagged(host,"island-action-MUTE_OFF"));await(()->!host.session.value.muted,"Unmute action was not acknowledged");
  tap(tagged(host,"island-action-SPEAKER_ON"));await(()->host.session.value.route==CallAudioState.ROUTE_SPEAKER,"Speaker on was not acknowledged");
  tap(tagged(host,"island-action-SPEAKER_OFF"));await(()->host.session.value.route==CallAudioState.ROUTE_EARPIECE,"Speaker off was not acknowledged");
  tap(tagged(host,"island-action-HOLD"));await(()->host.session.value.state==Call.STATE_HOLDING,"Hold was not acknowledged");
  tap(tagged(host,"island-action-RESUME"));await(()->host.session.value.state==Call.STATE_ACTIVE,"Resume was not acknowledged");
  assertEquals(6,host.session.actions);assertAnchor(identity,top,"Preserved query");
  Instrumentation.ActivityMonitor callMonitor=getInstrumentation().addMonitor(CallScreenActivity.class.getName(),null,false);
  try{
   tap(tagged(host,"island-open"));Activity call=callMonitor.waitForActivityWithTimeout(7000);assertNotNull("Open action must launch full call UI",call);launched.add(call);settle();
   assertEquals(1,host.session.opens);getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
   await(()->host.hasWindowFocus(),"System Back did not return to the same app screen");settle();
  }finally{getInstrumentation().removeMonitor(callMonitor);}
  assertAnchor(identity,top,"Preserved query");expanded();
  tap(tagged(host,"island-action-END"));await(()->dialogButton(android.R.id.button1)!=null,"Hang-up confirmation was not shown");
  assertNotNull("Merely opening confirmation must not end a call",host.session.value);assertEquals(6,host.session.actions);
  tap(dialogButton(android.R.id.button2));await(()->dialogButton(android.R.id.button1)==null,"Cancel did not dismiss confirmation");
  assertNotNull(host.session.value);assertEquals(6,host.session.actions);
  tap(tagged(host,"island-action-END"));await(()->dialogButton(android.R.id.button1)!=null,"Second confirmation missing");tap(dialogButton(android.R.id.button1));
  await(()->host.session.value==null&&view(host,"island-root")==null,"Confirmed end did not remove the island");settle();
  assertEquals(host.originalTopMargin,((FrameLayout.LayoutParams)host.content.getLayoutParams()).topMargin);assertAnchor(identity,top,"Preserved query");
  ui(()->host.setState(Call.STATE_ACTIVE,false));await(()->view(host,"island-root")!=null,"New session did not appear");settle();
  int actions=host.session.actions;prefs.edit().putBoolean("island_enabled",false).commit();
  await(()->view(host,"island-root")==null,"Disabled island still intercepts the screen");settle();
  assertNotNull("Disabling UI must leave call state intact",host.session.value);assertEquals(actions,host.session.actions);
  assertEquals(host.originalTopMargin,((FrameLayout.LayoutParams)host.content.getLayoutParams()).topMargin);assertAnchor(identity,top,"Preserved query");
  prefs.edit().putBoolean("island_enabled",true).commit();await(()->view(host,"island-root")!=null,"Re-enabled island missing");settle();
  if(host.controller.isExpanded())tap(tagged(host,"island-collapse"));
  try{
  shell("settings put global animator_duration_scale 1");await(ValueAnimator::areAnimatorsEnabled,"System animation setting did not propagate");
  prefs.edit().putBoolean("island_animations",true).commit();
  await(()->animationRunning(view(host,"island-activity")),"Attached activity bars never started their native animator");
  View running=tagged(host,"island-activity");float initialPhase=phase(running);
  await(()->Math.abs(phase(running)-initialPhase)>.02f,"Native animated activity bars did not advance");
  prefs.edit().putBoolean("island_animations",false).commit();
  await(()->animator(running)==null&&animator(view(host,"island-activity"))==null,"Disabling island motion left a running animator");
  View reduced=tagged(host,"island-activity");float reducedPhase=phase(reduced);Thread.sleep(180);
  assertEquals("Reduced-motion bars must remain stationary",reducedPhase,phase(reduced),.001f);
  prefs.edit().putBoolean("island_animations",true).commit();
  await(()->animationRunning(view(host,"island-activity")),"Re-enabling motion did not start a native animator");
  View beforePause=tagged(host,"island-activity");
  getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_HOME);await(()->!host.hasWindowFocus(),"Fixture did not pause");settle();
  assertNotNull(host.session.value);assertEquals(actions,host.session.actions);assertNull("Paused island should detach and stop animation",view(host,"island-root"));
  assertFalse(beforePause.isAttachedToWindow());assertNull("Detached activity bars retain no animator",animator(beforePause));
  returnThroughNativeRecents();
  await(()->host.hasWindowFocus()&&view(host,"island-root")!=null,"Resuming the same app task did not restore the island");settle();
  await(()->animationRunning(view(host,"island-activity")),"Resumed activity bars did not restart");
  assertAnchor(identity,top,"Preserved query");
  shell("settings put global animator_duration_scale 0");await(()->!ValueAnimator.areAnimatorsEnabled(),"System reduced-motion setting did not propagate");
  prefs.edit().putBoolean("island_animations",false).commit();settle();prefs.edit().putBoolean("island_animations",true).commit();settle();
  View systemReduced=tagged(host,"island-activity");assertNull("System reduced motion must prevent native animator creation",animator(systemReduced));
  float systemPhase=phase(systemReduced);Thread.sleep(180);assertEquals(systemPhase,phase(systemReduced),.001f);
  animationsPassed=true;
  }finally{
   prefs.edit().putBoolean("island_animations",false).commit();
   shell("settings put global animator_duration_scale "+(originalDuration.matches("[0-9.]+")?originalDuration:"1.0"));settle();
  }
  Instrumentation.ActivityMonitor recreation=getInstrumentation().addMonitor(CallIslandFixtureActivity.class.getName(),null,false);
  String token=host.session.value.token;
  try{
   ui(host::recreate);Activity next=recreation.waitForActivityWithTimeout(7000);assertNotNull(next);host=(CallIslandFixtureActivity)next;diagnosticHost=host;launched.add(host);
   await(()->host.hasWindowFocus()&&view(host,"island-root")!=null,"Recreated window did not restore island");settle();
  }finally{getInstrumentation().removeMonitor(recreation);}
  assertNotNull(host.session.value);assertEquals(token,host.session.value.token);assertEquals(actions,host.session.actions);assertAnchor(identity,top,"Preserved query");
  actionsPassed=true;writeEvidence();
 }
 private boolean launcher(AccessibilityNodeInfo node){
  if(node==null||node.getPackageName()==null)return false;String name=node.getPackageName().toString();
  return name.equals("com.google.android.apps.nexuslauncher")||name.equals("com.android.launcher3");
 }
 private boolean namesOurApp(AccessibilityNodeInfo node){
  return (String.valueOf(node.getText())+" "+String.valueOf(node.getContentDescription())).toLowerCase(java.util.Locale.ROOT).contains("da caller");
 }
 private AccessibilityNodeInfo taskForCaller(AccessibilityNodeInfo node,int depth,int[] budget){
  if(node==null||depth>60||--budget[0]<0)return null;
  if(node.isVisibleToUser()&&namesOurApp(node)){
   AccessibilityNodeInfo candidate=node;
   for(int level=0;candidate!=null&&level<10;level++,candidate=candidate.getParent()){
    Rect bounds=new Rect();candidate.getBoundsInScreen(bounds);
    String kind=(String.valueOf(candidate.getViewIdResourceName())+" "+String.valueOf(candidate.getClassName())).toLowerCase(java.util.Locale.ROOT);
    if(candidate.isVisibleToUser()&&candidate.isClickable()&&bounds.width()>=dp(120)&&bounds.height()>=dp(150)
       &&(namesOurApp(candidate)||kind.contains("task")))return candidate;
   }
  }
  for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo result=taskForCaller(node.getChild(i),depth+1,budget);if(result!=null)return result;}
  return null;
 }
 private AccessibilityNodeInfo recentAppCard(){
  AccessibilityNodeInfo active=getInstrumentation().getUiAutomation().getRootInActiveWindow();
  if(launcher(active)){AccessibilityNodeInfo card=taskForCaller(active,0,new int[]{4000});if(card!=null)return card;}
  for(AccessibilityWindowInfo window:getInstrumentation().getUiAutomation().getWindows()){
   AccessibilityNodeInfo root=window.getRoot();if(launcher(root)){AccessibilityNodeInfo card=taskForCaller(root,0,new int[]{4000});if(card!=null)return card;}
  }return null;
 }
 private boolean recentsTutorial(AccessibilityNodeInfo node,int depth,int[] budget){
  if(node==null||depth>60||--budget[0]<0)return false;
  // Observed on a fresh Android 13 Pixel Launcher: this first-use floating
  // tutorial hides task cards from accessibility until the user dismisses it.
  if(node.isVisibleToUser()&&launcher(node)
    &&"com.google.android.apps.nexuslauncher:id/text".equals(String.valueOf(node.getViewIdResourceName()))
    &&"Select text and images to copy, share, and more".equals(String.valueOf(node.getText())))return true;
  for(int i=0;i<node.getChildCount();i++)if(recentsTutorial(node.getChild(i),depth+1,budget))return true;
  return false;
 }
 private boolean recentsTutorialVisible(){
  AccessibilityNodeInfo active=getInstrumentation().getUiAutomation().getRootInActiveWindow();
  if(launcher(active)&&recentsTutorial(active,0,new int[]{4000}))return true;
  for(AccessibilityWindowInfo window:getInstrumentation().getUiAutomation().getWindows()){
   AccessibilityNodeInfo root=window.getRoot();if(launcher(root)&&recentsTutorial(root,0,new int[]{4000}))return true;
  }return false;
 }
 private void nativeKey(int key)throws Exception{
  long now=SystemClock.uptimeMillis();
  assertTrue(getInstrumentation().getUiAutomation().injectInputEvent(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,key,0),true));
  assertTrue(getInstrumentation().getUiAutomation().injectInputEvent(new KeyEvent(now,now+40,KeyEvent.ACTION_UP,key,0),true));
 }
 private void returnThroughNativeRecents()throws Exception{
  // Reopen the actual existing task as a user would. The test Activity remains
  // non-exported; a shell component launch would be a different permission path.
  nativeKey(KeyEvent.KEYCODE_APP_SWITCH);
  // A fresh Launcher can expose its task card before the first-use tutorial
  // appears. Watch for the exact tutorial throughout this bounded wait, rather
  // than making one early check and then waiting behind the floating window.
  long deadline=SystemClock.uptimeMillis()+7000,stableSince=0;
  boolean tutorialDismissed=false;AccessibilityNodeInfo card=null;
  while(SystemClock.uptimeMillis()<deadline){
   getInstrumentation().waitForIdleSync();
   if(recentsTutorialVisible()){
    stableSince=0;card=null;
    if(!tutorialDismissed){
     lastTap="native BACK dismisses verified Pixel Launcher Recents tutorial";
     nativeKey(KeyEvent.KEYCODE_BACK);tutorialDismissed=true;
    }
   }else{
    card=recentAppCard();long now=SystemClock.uptimeMillis();
    if(card==null)stableSince=0;
    else if(stableSince==0)stableSince=now;
    else if(now-stableSince>=500)break;
   }
   Thread.sleep(40);
  }
  assertTrue("Native Recents did not expose this app's own stable task card",card!=null&&stableSince>0&&SystemClock.uptimeMillis()-stableSince>=500);
  assertFalse("Native Recents tutorial still covers the task card",recentsTutorialVisible());
  card=recentAppCard();assertNotNull(card);assertTrue(launcher(card));
  Rect bounds=new Rect();card.getBoundsInScreen(bounds);assertTrue(bounds.width()>=dp(120)&&bounds.height()>=dp(150));
  lastTap="native Recents verified DA CALLER task @ "+bounds.centerX()+","+bounds.centerY();
  long touchTime=SystemClock.uptimeMillis();
  MotionEvent down=MotionEvent.obtain(touchTime,touchTime,MotionEvent.ACTION_DOWN,bounds.centerX(),bounds.centerY(),0);
  MotionEvent up=MotionEvent.obtain(touchTime,touchTime+45,MotionEvent.ACTION_UP,bounds.centerX(),bounds.centerY(),0);
  down.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);up.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
  try{assertTrue(getInstrumentation().getUiAutomation().injectInputEvent(down,true));assertTrue(getInstrumentation().getUiAutomation().injectInputEvent(up,true));}
  finally{down.recycle();up.recycle();}settle();
 }
 private interface Checked {void run()throws Exception;}
 private void checked(String name,Checked action)throws Exception{
  try{action.run();}catch(Throwable failure){
   try{captureFailure(name,failure);}catch(Exception diagnostic){failure.addSuppressed(diagnostic);}throwFailure(failure);
  }
 }
 private void throwFailure(Throwable failure)throws Exception{
  if(failure instanceof Exception)throw (Exception)failure;if(failure instanceof Error)throw (Error)failure;throw new AssertionError(failure);
 }
 private void captureFailure(String name,Throwable failure)throws Exception{
  String prefix="island-failed-"+name;shell("screencap -p "+DIRECTORY+"/"+prefix+".png");
  JSONObject json=new JSONObject();json.put("failure",failure.toString());json.put("last_raw_tap",lastTap);
  if(host!=null){json.put("fixture_expanded",host.controller!=null&&host.controller.isExpanded());json.put("fixture_focus",host.hasWindowFocus());}
  JSONArray views=new JSONArray();
  ui(()->{if(diagnosticHost!=null)dumpView(diagnosticHost.getWindow().getDecorView(),views,0,new int[]{2500});});json.put("activity_views",views);
  diagnosticFile(prefix+".json",json.toString(2));
  StringWriter writer=new StringWriter();XmlSerializer xml=android.util.Xml.newSerializer();xml.setOutput(writer);
  xml.startDocument("UTF-8",true);xml.startTag(null,"accessibility-windows");xml.attribute(null,"failure",failure.toString());
  int[] budget={5000};xml.startTag(null,"active");dumpAccessibility(getInstrumentation().getUiAutomation().getRootInActiveWindow(),xml,0,budget);xml.endTag(null,"active");
  for(AccessibilityWindowInfo window:getInstrumentation().getUiAutomation().getWindows()){
   xml.startTag(null,"window");xml.attribute(null,"id",String.valueOf(window.getId()));xml.attribute(null,"active",String.valueOf(window.isActive()));
   dumpAccessibility(window.getRoot(),xml,0,budget);xml.endTag(null,"window");
  }
  xml.endTag(null,"accessibility-windows");xml.endDocument();diagnosticFile(prefix+".xml",writer.toString());
 }
 private void dumpView(View view,JSONArray result,int depth,int[] budget){
  if(view==null||depth>60||--budget[0]<0)return;
  try{
   Rect bounds=new Rect();view.getGlobalVisibleRect(bounds);JSONObject item=new JSONObject();item.put("class",view.getClass().getName());
   item.put("tag",String.valueOf(view.getTag()));item.put("bounds",bounds.toShortString());item.put("size",view.getWidth()+"x"+view.getHeight());
   item.put("clickable",view.isClickable());item.put("shown",view.isShown());item.put("attached",view.isAttachedToWindow());
   item.put("description",String.valueOf(view.getContentDescription()));if(view instanceof TextView)item.put("text",((TextView)view).getText());result.put(item);
   if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)dumpView(((ViewGroup)view).getChildAt(i),result,depth+1,budget);
  }catch(org.json.JSONException error){throw new AssertionError(error);}
 }
 private void dumpAccessibility(AccessibilityNodeInfo node,XmlSerializer xml,int depth,int[] budget)throws Exception{
  if(node==null||depth>70||--budget[0]<0)return;Rect bounds=new Rect();node.getBoundsInScreen(bounds);xml.startTag(null,"node");
  xml.attribute(null,"class",String.valueOf(node.getClassName()));xml.attribute(null,"id",String.valueOf(node.getViewIdResourceName()));
  xml.attribute(null,"package",String.valueOf(node.getPackageName()));xml.attribute(null,"text",String.valueOf(node.getText()));
  xml.attribute(null,"description",String.valueOf(node.getContentDescription()));xml.attribute(null,"bounds",bounds.toShortString());
  xml.attribute(null,"visible",String.valueOf(node.isVisibleToUser()));xml.attribute(null,"clickable",String.valueOf(node.isClickable()));
  xml.attribute(null,"actions",node.getActionList().toString());
  for(int i=0;i<node.getChildCount();i++)dumpAccessibility(node.getChild(i),xml,depth+1,budget);xml.endTag(null,"node");
 }
 private void diagnosticFile(String name,String value)throws Exception{
  File file=new File(target.getExternalFilesDir(null),name);try(FileOutputStream out=new FileOutputStream(file)){out.write(value.getBytes(StandardCharsets.UTF_8));}
  shell("cp "+file.getAbsolutePath()+" "+DIRECTORY+"/"+name);
 }
 private int contrastingBarPixels(Bitmap capture,int top,int bottom,boolean dark){
  int count=0;
  for(int y=Math.max(0,top);y<Math.min(capture.getHeight(),bottom);y++)for(int x=0;x<capture.getWidth();x++){
   int pixel=capture.getPixel(x,y),brightness=Color.red(pixel)+Color.green(pixel)+Color.blue(pixel);
   if(dark?brightness>600:brightness<384)count++;
  }return count;
 }
 private void settingsSnapshot(Activity settings,boolean dark,String name)throws Exception{
  // Capture the actual compositor, including native status/navigation icons.
  // Requested appearance flags alone do not prove the rendered icon contrast
  // while Android transfers bar ownership between Activity windows.
  long deadline=SystemClock.uptimeMillis()+5000;int stable=0,statusPixels=0,navigationPixels=0;
  final int[] bars=new int[4];Bitmap capture=null;boolean passed=false;JSONArray samples=new JSONArray();
  try{
   do{
    ui(()->{
     bars[0]=0;bars[1]=0;bars[2]=0;bars[3]=settings.hasWindowFocus()?1:0;
     android.view.WindowInsets insets=settings.getWindow().getDecorView().getRootWindowInsets();
     if(insets!=null){
      bars[0]=insets.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.statusBars()).top;
      bars[1]=insets.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.navigationBars()).bottom;
      bars[2]=insets.isVisible(android.view.WindowInsets.Type.navigationBars())?1:0;
     }
    });
    if(capture!=null)capture.recycle();capture=getInstrumentation().getUiAutomation().takeScreenshot();
    statusPixels=capture==null?0:contrastingBarPixels(capture,0,bars[0],dark);
    navigationPixels=capture==null?0:contrastingBarPixels(capture,capture.getHeight()-bars[1],capture.getHeight(),dark);
    boolean good=bars[3]==1&&bars[0]>0&&bars[1]>=dp(48)&&bars[2]==1&&statusPixels>=40&&navigationPixels>=40;
    stable=good?stable+1:0;
    JSONObject sample=new JSONObject();sample.put("uptime_ms",SystemClock.uptimeMillis());sample.put("focused",bars[3]==1);
    sample.put("status_top_px",bars[0]);sample.put("navigation_bottom_px",bars[1]);sample.put("navigation_visible",bars[2]==1);
    sample.put("contrasting_status_pixels",statusPixels);sample.put("contrasting_navigation_pixels",navigationPixels);
    sample.put("consecutive_valid_samples",stable);samples.put(sample);
    if(stable>=3){passed=true;break;}
    Thread.sleep(100);
   }while(SystemClock.uptimeMillis()<deadline);
   // Save precisely the Bitmap whose native bar pixels were checked, on both
   // success and failure; no editing or separately timed shell capture.
   String filename="island-"+name+".png";
   if(capture!=null){
    File file=new File(target.getExternalFilesDir(null),filename);
    try(FileOutputStream out=new FileOutputStream(file)){assertTrue(capture.compress(Bitmap.CompressFormat.PNG,100,out));}
    shell("cp "+file.getAbsolutePath()+" "+DIRECTORY+"/"+filename);
   }
   if(!passed){
    JSONObject diagnostic=new JSONObject();diagnostic.put("screenshot",filename);diagnostic.put("dark",dark);
    diagnostic.put("required_navigation_px",dp(48));diagnostic.put("required_contrasting_pixels_per_bar",40);
    diagnostic.put("required_consecutive_valid_samples",3);diagnostic.put("samples",samples);
    diagnosticFile("island-"+name+"-bars-failed.json",diagnostic.toString(2));
   }
   assertNotNull("Native settings compositor screenshot missing",capture);
   assertTrue("Settings native status/navigation bars must have stable rendered contrast: "+name
    +" status="+statusPixels+" navigation="+navigationPixels+" navInset="+bars[1],passed);
   settingsBarCaptures++;
  }finally{if(capture!=null)capture.recycle();}
 }
 public void testCustomizationPreviewBothThemesAndBottomControlsAtLargeFont()throws Exception{
  settingsBarsPassed=false;settingsBarCaptures=0;
  for(boolean dark:new boolean[]{false,true}){
   prefs.edit().putBoolean("dark",dark).putBoolean("amoled",false).putString("language","he").putString("island_style","apple").commit();
   Activity settings=getInstrumentation().startActivitySync(new Intent(target,CallIslandSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));launched.add(settings);settle();
   await(settings::hasWindowFocus,"Customization must own the window before capture");
   ScrollView scroll=find(settings.getWindow().getDecorView(),ScrollView.class);assertNotNull(scroll);
   ui(()->scroll.scrollTo(0,0));settle();ui(()->visible(tagged(settings,"island-settings-preview")));
   settingsSnapshot(settings,dark,"settings-"+(dark?"dark":"light"));
   ui(()->scroll.fullScroll(View.FOCUS_DOWN));settle();
   ui(()->{visible(tagged(settings,"island_confirm_end"));textFits(tagged(settings,"island_confirm_end"));});
   settingsSnapshot(settings,dark,"settings-"+(dark?"dark":"light")+"-bottom");
   boolean confirmed=prefs.getBoolean("island_confirm_end",true);tap(tagged(settings,"island_confirm_end"));
   await(()->prefs.getBoolean("island_confirm_end",true)!=confirmed,"Native setting switch did not persist its selection");
   tap(tagged(settings,"island_confirm_end"));await(()->prefs.getBoolean("island_confirm_end",true)==confirmed,"Native setting could not be restored");
   ui(settings::finish);settle();
  }
  assertEquals("Both themes and scroll positions require native bar contrast",4,settingsBarCaptures);
  settingsBarsPassed=true;settingsPassed=true;writeEvidence();
 }
 private void writeEvidence()throws Exception{
  JSONObject proof=new JSONObject();proof.put("api",android.os.Build.VERSION.SDK_INT);proof.put("real_renderer_and_controller",true);
  proof.put("matrix_native_layout_assertions",matrixPassed);proof.put("app_main_integration",appMainPassed);
  proof.put("app_main_screens","Actual MainActivity/FastPages contacts and recents, compact/expanded, favorites/summary/navigation, synthetic rows, no call commands");
  proof.put("native_action_dispatch_and_acknowledgement",actionsPassed);
  proof.put("confirmed_end_cancel_and_accept",actionsPassed);proof.put("disabled_ui_preserves_session",actionsPassed);
  proof.put("content_margin_and_list_anchor_restored",actionsPassed);proof.put("pause_stops_surface_and_resume_restores",actionsPassed);
  proof.put("activity_recreation_preserves_fixture_session_and_query",actionsPassed);proof.put("customization_preview_and_bottom_controls",settingsPassed);
  proof.put("animations_lifecycle",animationsPassed);proof.put("native_preference_persistence",settingsPassed);
  proof.put("native_settings_bar_contrast",settingsBarsPassed);
  proof.put("session_source","Non-exported DEBUG fixture. Fictional contacts and state; real production renderer/controller. No Telecom call or physical audio route is created.");
  proof.put("full_call_navigation","Real CallScreenActivity in explicit DEBUG preview; system Back returns to same native host and preserved list.");
  File file=new File(target.getExternalFilesDir(null),"island-native.json");try(FileOutputStream out=new FileOutputStream(file)){out.write(proof.toString(2).getBytes(StandardCharsets.UTF_8));}
  shell("cp "+file.getAbsolutePath()+" "+DIRECTORY+"/island-native.json");
 }
 private void restore(SharedPreferences settings,Map<String,?> values){
  SharedPreferences.Editor edit=settings.edit().clear();for(Map.Entry<String,?> item:values.entrySet()){
   Object value=item.getValue();String key=item.getKey();if(value instanceof String)edit.putString(key,(String)value);
   else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);else if(value instanceof Integer)edit.putInt(key,(Integer)value);
   else if(value instanceof Long)edit.putLong(key,(Long)value);else if(value instanceof Float)edit.putFloat(key,(Float)value);
   else if(value instanceof Set)edit.putStringSet(key,new HashSet<>((Set<String>)value));
  }assertTrue(edit.commit());
 }
 private String shell(String command)throws Exception{
  ParcelFileDescriptor descriptor=getInstrumentation().getUiAutomation().executeShellCommand(command);
  try(ParcelFileDescriptor.AutoCloseInputStream in=new ParcelFileDescriptor.AutoCloseInputStream(descriptor)){
   ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);return out.toString("UTF-8");
  }
 }
}
