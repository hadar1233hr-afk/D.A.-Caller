package com.dacaller.app;

import android.Manifest;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.test.InstrumentationTestCase;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RemoteViews;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;

/** Real API33 rendering and integration, using fictional contacts and isolated document bytes. */
@SuppressWarnings({"deprecation","unchecked"})
public final class PersonalizationSnapshotTest extends InstrumentationTestCase {
 private static final String DIRECTORY="/sdcard/Download/DA-CALLER-snapshots";
 private Context target;
 private SharedPreferences settings;
 private Map<String,?> originalSettings;
 private String originalFontScale,originalIcon;
 private ListDispatchHostActivity host;
 private final List<Activity> launched=new ArrayList<>();
 private List<CallHistory.Entry> originalCalls;
 private CallHistory.Summary originalSummary;

 @Override protected void setUp()throws Exception{
  super.setUp();target=getInstrumentation().getTargetContext();
  settings=target.getSharedPreferences("da_caller",Context.MODE_PRIVATE);
  originalSettings=settings.getAll();originalIcon=AppIcons.selected(target);
  originalFontScale=shell("settings get system font_scale").trim();
  originalCalls=CallHistory.entries;originalSummary=CallHistory.summary;
  shell("mkdir -p "+DIRECTORY);
 }
 @Override protected void tearDown()throws Exception{
  try{
   for(Activity activity:launched)if(activity!=null&&!activity.isDestroyed())ui(activity::finish);
   getInstrumentation().waitForIdleSync();
   restorePreferences(settings,originalSettings);assertTrue(AppIcons.apply(target,originalIcon));
   CallHistory.entries=originalCalls;CallHistory.summary=originalSummary;
   target.getContentResolver().delete(BackupDocumentProvider.DOCUMENT,null,null);
   shell("settings put system font_scale "+(originalFontScale.matches("[0-9.]+")?originalFontScale:"1.0"));
  }finally{super.tearDown();}
 }
 private void ui(Runnable action)throws Exception{
  AtomicReference<Throwable> error=new AtomicReference<>();
  getInstrumentation().runOnMainSync(()->{try{action.run();}catch(Throwable failure){error.set(failure);}});
  if(error.get()!=null)throw new AssertionError(error.get());
 }
 private void settle()throws Exception{getInstrumentation().waitForIdleSync();Thread.sleep(180);getInstrumentation().waitForIdleSync();}
 private int dp(float value){return Math.round(value*target.getResources().getDisplayMetrics().density);}
 private void field(Object object,String key,Object value){
  try{Field field=MainActivity.class.getDeclaredField(key);field.setAccessible(true);field.set(object,value);}
  catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
 }
 private void show(String page){
  field(host,"page",page);
  try{Method render=MainActivity.class.getDeclaredMethod("render");render.setAccessible(true);render.invoke(host);}
  catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
 }
 private List<Contacts.Person> people(String language,int count){
  String[] he={"דנה לדוגמה","אורי","נועה","אדם","שירה","יוסף","תמר","יעל","דניאל","אריאל","מיכל","רות"};
  String[] en={"Dana Example","Alex","Noa","Adam","Shira","Joseph","Tamar","Yael","Daniel","Ariel","Michelle","Ruth"};
  ArrayList<Contacts.Person> result=new ArrayList<>();
  for(int i=0;i<count;i++)result.add(new Contacts.Person(("en".equals(language)?en:he)[i%he.length],"0500099"+String.format(java.util.Locale.ROOT,"%03d",i),8000+i));
  return result;
 }
 private void launchHost(String language,boolean dark,float font)throws Exception{
  if(host!=null&&!host.isDestroyed()){ui(host::finish);settle();}
  shell("settings put system font_scale "+font);Thread.sleep(300);
  host=(ListDispatchHostActivity)getInstrumentation().startActivitySync(new Intent(target,ListDispatchHostActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  launched.add(host);settle();
  List<Contacts.Person> people=people(language,12);Set<String> favorites=new HashSet<>();
  for(Contacts.Person person:people)favorites.add(person.key);
  ui(()->{
   host.getSharedPreferences("da_caller",Context.MODE_PRIVATE).edit().putString("language",language)
     .putBoolean("dark",dark).putBoolean("amoled",false).putBoolean("show_stats",true)
     .putBoolean("favorites_expanded",true).putBoolean("stats_expanded",false)
     .putString("avatar_shape","circle").putStringSet("favorites",favorites).commit();
   field(host,"contacts",people);field(host,"contactsLoaded",true);field(host,"fastPages",new FastPages(host));
   ArrayList<CallHistory.Entry> calls=new ArrayList<>();long now=System.currentTimeMillis();
   for(int i=0;i<12;i++)calls.add(new CallHistory.Entry(990000+i,people.get(i).phone,now-i*60000,65+i*10,i%3+1));
   CallHistory.entries=Collections.unmodifiableList(calls);CallHistory.readFailed=false;
   CallHistory.Summary summary=new CallHistory.Summary();
   for(int i=0;i<4;i++){summary.calls[i]=12+i*20;summary.seconds[i]=3600+i*121;summary.incoming[i]=5+i;summary.outgoing[i]=4+i;summary.missed[i]=3+i;}
   CallHistory.summary=summary;show("contacts");
  });settle();
  assertEquals(font,host.getResources().getConfiguration().fontScale,0.05f);
 }
 private View tagged(Activity activity,String tag){View view=activity.getWindow().getDecorView().findViewWithTag(tag);assertNotNull("Missing "+tag,view);return view;}
 private <T extends View>T find(View root,Class<T> kind){
  if(kind.isInstance(root))return kind.cast(root);
  if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){T result=find(((ViewGroup)root).getChildAt(i),kind);if(result!=null)return result;}
  return null;
 }
 private boolean containsText(View root,String value){
  if(root instanceof TextView&&((TextView)root).getText().toString().contains(value))return true;
  if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)if(containsText(((ViewGroup)root).getChildAt(i),value))return true;
  return false;
 }
 private TextView exactText(View root,String value){
  if(root instanceof TextView&&value.equalsIgnoreCase(((TextView)root).getText().toString()))return (TextView)root;
  if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){
   TextView result=exactText(((ViewGroup)root).getChildAt(i),value);if(result!=null)return result;
  }
  return null;
 }
 private void fullyVisible(View view){
  assertNotNull(view);Rect visible=new Rect();assertTrue("Hidden "+view.getTag(),view.isShown()&&view.getGlobalVisibleRect(visible));
  assertTrue("Vertically clipped "+view.getTag()+" "+visible.height()+"/"+view.getHeight(),visible.height()>=view.getHeight()-1);
  assertTrue("Horizontally clipped "+view.getTag(),visible.width()>=view.getWidth()-1);
 }
 private void textFits(View view){
  if(!view.isShown())return;
  if(view instanceof TextView&&((TextView)view).length()>0){
   TextView text=(TextView)view;assertNotNull(text.getLayout());
   int inner=text.getHeight()-text.getCompoundPaddingTop()-text.getCompoundPaddingBottom();
   assertTrue("Clipped glyph rows '"+text.getText()+"': "+text.getLayout().getHeight()+"/"+inner,text.getLayout().getHeight()<=inner+1);
  }
  if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)textFits(((ViewGroup)view).getChildAt(i));
 }
 private void tap(View view)throws Exception{
  final int[] location=new int[2];ui(()->{fullyVisible(view);view.getLocationOnScreen(location);location[0]+=view.getWidth()/2;location[1]+=view.getHeight()/2;});
  tap(location[0],location[1]);settle();
 }
 private void tap(int x,int y){
  long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0),up=MotionEvent.obtain(now,now+50,MotionEvent.ACTION_UP,x,y,0);
  getInstrumentation().sendPointerSync(down);getInstrumentation().sendPointerSync(up);down.recycle();up.recycle();
 }
 private void screenshot(String name)throws Exception{
  settle();String path=DIRECTORY+"/personalization-"+name+".png";shell("screencap -p "+path);
  assertEquals("regular file",shell("stat -c %F "+path).trim());assertTrue(Long.parseLong(shell("stat -c %s "+path).trim())>1000);
 }

 public void testContactsAndRecentsCompactPresentationInBothLanguagesThemesAndFontSizes()throws Exception{
  for(float font:new float[]{1f,1.3f})for(boolean dark:new boolean[]{false,true})for(String language:new String[]{"he","en"}){
   launchHost(language,dark,font);String suffix=(dark?"dark":"light")+"-"+language+"-"+(font>1?"largefont":"standard");
   View band=tagged(host,"favorites_strip");
   ui(()->{fullyVisible(band);assertEquals(dp(82),band.getHeight());textFits(band);assertTrue(find(host.getWindow().getDecorView(),ListView.class).getHeight()>=dp(140));});
   screenshot("contacts-"+suffix+"-expanded");
   tap(tagged(host,"favorites_toggle"));
   ui(()->{assertEquals(dp(48),tagged(host,"favorites_strip").getHeight());assertNull(host.getWindow().getDecorView().findViewWithTag("favorites_scroll"));textFits(tagged(host,"favorites_strip"));});
   screenshot("contacts-"+suffix+"-collapsed");
   ui(()->show("recents"));settle();
   ui(()->{
    assertEquals(dp(48),tagged(host,"favorites_strip").getHeight());assertEquals(dp(48),tagged(host,"stats_summary").getHeight());
    fullyVisible(tagged(host,"stats_toggle"));textFits(tagged(host,"stats_summary"));
    assertTrue(containsText(tagged(host,"stats_period"),"12"));assertTrue(find(host.getWindow().getDecorView(),ListView.class).getHeight()>=dp(140));
   });screenshot("recents-"+suffix+"-compact");
   tap(tagged(host,"stats_toggle"));
   ui(()->{
    View summary=tagged(host,"stats_summary");assertTrue(summary.getHeight()>dp(48));assertTrue("Expanded summary wastes space",summary.getHeight()<=dp(150));
    fullyVisible(summary);textFits(summary);
    String average="en".equals(language)?"Average call length":"משך שיחה ממוצע";
    assertTrue("Expanded summary must retain its average-duration label in "+language,containsText(summary,average));
    assertNotNull("Outgoing count must remain visible after expansion",exactText(summary,"↗ 4"));
    assertNotNull("Incoming count must remain visible after expansion",exactText(summary,"↙ 5"));
    assertNotNull("Missed count must remain visible after expansion",exactText(summary,"× 3"));
    assertTrue(host.requestedNumbers.isEmpty());
   });screenshot("recents-"+suffix+"-expanded");
  }
 }

 public void testHorizontalFavoritesReachAllContactsWithoutDialingAndRememberSharedCollapse()throws Exception{
  for(String language:new String[]{"he","en"}){
   launchHost(language,true,1.3f);
   HorizontalScrollView scroll=(HorizontalScrollView)tagged(host,"favorites_scroll");
   ui(()->scroll.scrollTo("he".equals(language)?0:scroll.getChildAt(0).getWidth(),0));settle();
   View last=tagged(host,"favorite_tile:8011");ui(()->fullyVisible(last));
   assertTrue(host.requestedNumbers.isEmpty());tap(last);assertEquals("0500099011",host.requestedNumbers.get(0));
   // The fixture records the production click; it cannot place a cellular call.
   tap(tagged(host,"favorites_toggle"));ui(()->show("recents"));settle();
   assertEquals(dp(48),tagged(host,"favorites_strip").getHeight());tap(tagged(host,"favorites_toggle"));
   assertEquals(dp(82),tagged(host,"favorites_strip").getHeight());ui(()->show("contacts"));settle();
   assertNotNull(tagged(host,"favorites_scroll"));assertEquals(1,host.requestedNumbers.size());
  }
 }

 public void testSettingsIconAndAvatarChoosersAtLargeFontInBothThemes()throws Exception{
  for(boolean dark:new boolean[]{false,true}){
   launchHost("he",dark,1.3f);
   ui(()->{host.getSharedPreferences("da_caller",Context.MODE_PRIVATE).edit().putBoolean("settings_group_open_0",true).commit();show("settings");});settle();
   for(String kind:new String[]{"icons","avatar_shapes"}){
    View action=tagged(host,"settings_"+kind);ScrollView viewport=find(host.getWindow().getDecorView(),ScrollView.class);
    ui(()->{Rect rect=new Rect();action.getDrawingRect(rect);viewport.offsetDescendantRectToMyCoords(action,rect);viewport.scrollTo(0,rect.top);});settle();tap(action);
    String first="icons".equals(kind)?AppIcons.label(host,AppIcons.IDS[0]):AvatarShapes.label(host,AvatarShapes.IDS[0]);
    awaitAccessible(first);screenshot("chooser-"+(dark?"dark":"light")+"-"+kind);
    String last="icons".equals(kind)?AppIcons.label(host,AppIcons.IDS[4]):AvatarShapes.label(host,AvatarShapes.IDS[3]);
    // Native scroll accessibility must expose the final choice even when the dialog is short.
    revealAccessible(last);assertNotNull(accessible(last));
    screenshot("chooser-"+(dark?"dark":"light")+"-"+kind+"-bottom");clickAccessible(last);
    if("icons".equals(kind)){
     await(()->"wave".equals(AppIcons.selected(host)),"Icon choice was not saved");settle();
     List<ResolveInfo> matches=target.getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN)
       .addCategory(Intent.CATEGORY_LAUNCHER).setPackage(target.getPackageName()),0);
     assertEquals(1,matches.size());assertEquals(AppIcons.component(host,"wave").getClassName(),matches.get(0).activityInfo.name);
    }else{
     await(()->"hexagon".equals(AvatarShapes.selected(host)),"Avatar shape choice was not saved");settle();
    }
   }
  }
 }

 public void testEveryRealLauncherAliasRemainsUniqueAndDialIntentStillResolves()throws Exception{
  PackageManager manager=target.getPackageManager();
  for(String id:AppIcons.IDS){
   ui(()->assertTrue("Cannot select icon "+id,AppIcons.select(target,id)));settle();
   Intent launcher=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(target.getPackageName());
   List<ResolveInfo> matches=manager.queryIntentActivities(launcher,0);assertEquals("Duplicate or missing launcher icon "+id,1,matches.size());
   assertEquals(AppIcons.component(target,id).getClassName(),matches.get(0).activityInfo.name);
   assertEquals(id,AppIcons.selected(target));
   int main=manager.getComponentEnabledSetting(new ComponentName(target,MainActivity.class));assertTrue(main==PackageManager.COMPONENT_ENABLED_STATE_DEFAULT||main==PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
   Intent dial=new Intent(Intent.ACTION_DIAL,Uri.parse("tel:0500000000")).setPackage(target.getPackageName());
   List<ResolveInfo> dialers=manager.queryIntentActivities(dial,0);assertEquals(1,dialers.size());assertEquals(MainActivity.class.getName(),dialers.get(0).activityInfo.name);
  }
 }

 public void testAllAvatarShapesClipActualContactPhotoAndWidgetInitials()throws Exception{
  launchHost("he",true,1.3f);
  Bitmap photo=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(photo);canvas.drawColor(0xFFB99242);Paint p=new Paint();p.setColor(0xFF194B39);canvas.drawCircle(48,42,23,p);
  for(String shape:AvatarShapes.IDS){
   ui(()->{host.getSharedPreferences("da_caller",Context.MODE_PRIVATE).edit().putString("avatar_shape",shape).commit();show("contacts");});settle();
   ui(()->{
    ListView list=find(host.getWindow().getDecorView(),ListView.class);assertTrue(list.getChildCount()>1);
    Object row=list.getChildAt(0).getTag();
    try{
     Field avatarField=row.getClass().getDeclaredField("avatar"),photoField=row.getClass().getDeclaredField("photo");avatarField.setAccessible(true);photoField.setAccessible(true);
     FrameLayout avatar=(FrameLayout)avatarField.get(row);ImageView image=(ImageView)photoField.get(row);image.setImageBitmap(photo);image.setVisibility(View.VISIBLE);
     Bitmap raster=Bitmap.createBitmap(avatar.getWidth(),avatar.getHeight(),Bitmap.Config.ARGB_8888);avatar.draw(new Canvas(raster));
     assertEquals("Contact center missing "+shape,255,Color.alpha(raster.getPixel(raster.getWidth()/2,raster.getHeight()/2)));
     if("square".equals(shape))assertEquals(255,Color.alpha(raster.getPixel(1,1)));else assertEquals("Photo escaped avatar shape "+shape,0,Color.alpha(raster.getPixel(1,1)));raster.recycle();
    }catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
   });screenshot("contacts-shape-"+shape);
   ui(()->{
    LinearLayout root=new LinearLayout(host);root.setOrientation(LinearLayout.VERTICAL);root.setGravity(Gravity.CENTER);root.setPadding(dp(8),dp(16),dp(8),dp(16));root.setBackgroundColor(0xFF101512);
    TextView title=new TextView(host);title.setText("צורת תמונה · "+AvatarShapes.label(host,shape));title.setTextColor(Color.WHITE);title.setTextSize(18);title.setGravity(Gravity.CENTER);root.addView(title,new LinearLayout.LayoutParams(-1,dp(64)));
    Bundle dimensions=new Bundle();dimensions.putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,288);
    RemoteViews widget=WidgetSupport.renderFavorites(host,994711,dimensions,people("he",5),true);
    View rendered=widget.apply(host,root);root.addView(rendered,new LinearLayout.LayoutParams(dp(304),dp(160)));host.setContentView(root);
    Bitmap avatar=WidgetSupport.avatar(host,-1,"דנה",96);assertEquals(255,Color.alpha(avatar.getPixel(48,48)));
    if("square".equals(shape))assertEquals(255,Color.alpha(avatar.getPixel(1,1)));else assertEquals(0,Color.alpha(avatar.getPixel(1,1)));avatar.recycle();
   });screenshot("widgets-shape-"+shape);
   // Restore the shell's root reference after the separate RemoteViews rendering fixture.
   field(host,"root",null);
  }
  photo.recycle();
 }

 public void testArchiveResolvesRealProviderNamePhotoAndSearchesNames()throws Exception{
  // Only the disposable CI emulator receives synthetic provider rows and these grants.
  shell("pm grant "+target.getPackageName()+" android.permission.READ_CONTACTS");
  shell("pm grant "+target.getPackageName()+" android.permission.WRITE_CONTACTS");
  String name="דנה בדיקת ארכיון",phone="0500099901";Uri raw=null;String key="99009901|1700000000000|2|"+phone;
  try{
   ArrayList<ContentProviderOperation> operations=new ArrayList<>();
   operations.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI).withValue(ContactsContract.RawContacts.ACCOUNT_TYPE,null).withValue(ContactsContract.RawContacts.ACCOUNT_NAME,null).build());
   operations.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI).withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID,0).withValue(ContactsContract.Data.MIMETYPE,ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE).withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,name).build());
   operations.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI).withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID,0).withValue(ContactsContract.Data.MIMETYPE,ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE).withValue(ContactsContract.CommonDataKinds.Phone.NUMBER,phone).withValue(ContactsContract.CommonDataKinds.Phone.TYPE,ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE).build());
   Bitmap image=AvatarShapes.preview(target,"square",128);ByteArrayOutputStream png=new ByteArrayOutputStream();image.compress(Bitmap.CompressFormat.PNG,100,png);image.recycle();
   operations.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI).withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID,0).withValue(ContactsContract.Data.MIMETYPE,ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE).withValue(ContactsContract.CommonDataKinds.Photo.PHOTO,png.toByteArray()).build());
   ContentProviderResult[] inserted=target.getContentResolver().applyBatch(ContactsContract.AUTHORITY,operations);raw=inserted[0].uri;ContactPhotos.clearCache();
   settings.edit().putString(CallArchive.PREF,CallArchive.FOREVER).putString("language","he").putString("avatar_shape","circle").commit();
   try(CallArchive helper=new CallArchive(target)){
    SQLiteDatabase db=helper.getWritableDatabase();android.content.ContentValues values=new android.content.ContentValues();
    values.put("key",key);values.put("call_id",99009901);values.put("number",phone);values.put("at_ms",1700000000000L);values.put("seconds",95);values.put("kind",CallLog.Calls.OUTGOING_TYPE);values.put("last_seen_ms",System.currentTimeMillis()-1000);values.put("missing_since_ms",System.currentTimeMillis()-500);values.put("seen_generation",1);db.insertOrThrow("saved",null,values);
   }
   shell("settings put system font_scale 1.3");Thread.sleep(300);
   for(boolean dark:new boolean[]{false,true}){
    settings.edit().putBoolean("dark",dark).putBoolean("amoled",false).commit();
    ArchiveActivity archive=(ArchiveActivity)getInstrumentation().startActivitySync(new Intent(target,ArchiveActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));launched.add(archive);
    await(()->containsText(archive.getWindow().getDecorView(),name),"Archive never resolved the contact name");
    ui(()->find(archive.getWindow().getDecorView(),EditText.class).setText("דנה בדיקת"));
    await(()->find(archive.getWindow().getDecorView(),ListView.class).getAdapter().getCount()==1,"Name search did not find the saved contact");
    await(()->{ListView list=find(archive.getWindow().getDecorView(),ListView.class);ImageView photo=find(list,ImageView.class);return photo!=null&&photo.getVisibility()==View.VISIBLE&&photo.getDrawable()!=null;},"Real provider contact photo did not appear");
    ui(()->{
     ListView list=find(archive.getWindow().getDecorView(),ListView.class);assertEquals(1,list.getAdapter().getCount());
     assertTrue(containsText(list,name));assertTrue(containsText(list,phone));textFits(list);
     int lightMask=android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|
       android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
     int appearance=archive.getWindow().getInsetsController().getSystemBarsAppearance();
     assertEquals("Archive system icons must contrast with the actual theme",dark?0:lightMask,appearance&lightMask);
    });
    screenshot("archive-"+(dark?"dark":"light"));ui(archive::finish);settle();
   }
  }finally{
   if(raw!=null)target.getContentResolver().delete(raw,null,null);
   try(CallArchive helper=new CallArchive(target)){helper.getWritableDatabase().delete("saved","key=?",new String[]{key});}
   ContactPhotos.clearCache();
  }
 }

 public void testDocumentBackupRestoreRequiresConfirmationAndCancelKeepsPreferences()throws Exception{
  shell("settings put system font_scale 1.3");Thread.sleep(300);
  byte[] saved=null;
  for(boolean dark:new boolean[]{false,true}){
   settings.edit().putString("language","en").putBoolean("dark",dark).putBoolean("amoled",false).putString("avatar_shape","hexagon")
     .putBoolean("favorites_expanded",false).putBoolean("stats_expanded",true).putStringSet("favorites",new HashSet<>(Arrays.asList("0500099011","0500099000")))
     .putString("note_0500099011","Synthetic test note").putString(CallArchive.PREF,CallArchive.OFF).commit();
   Activity export=backup(true,Activity.RESULT_OK);await(()->containsText(export.getWindow().getDecorView(),"Backup saved to the file you selected."),"Export did not complete");
   try(InputStream input=target.getContentResolver().openInputStream(BackupDocumentProvider.DOCUMENT)){saved=readAll(input);}
   assertTrue(saved.length>100);assertEquals("DA_CALLER_SETTINGS",new JSONObject(new String(saved,java.nio.charset.StandardCharsets.UTF_8)).getString("format"));
   ui(()->{
    TextView result=exactText(export.getWindow().getDecorView(),"Backup saved to the file you selected.");
    assertNotNull("Export outcome must be present",result);fullyVisible(result);textFits(result);
   });
   screenshot("backup-saved-"+(dark?"dark":"light"));
   final TextView[] close={null};
   ui(()->{
    close[0]=exactText(export.getWindow().getDecorView(),"Close");assertNotNull("Export must provide Close",close[0]);
    close[0].requestRectangleOnScreen(new Rect(0,0,close[0].getWidth(),close[0].getHeight()),true);
   });settle();tap(close[0]);
  }
  assertNotNull(saved);
  settings.edit().putBoolean("dark",false).putString("avatar_shape","square").putBoolean("stats_expanded",false).commit();
  Map<String,?> before=settings.getAll();Activity canceled=backup(false,Activity.RESULT_CANCELED);settle();
  assertTrue(canceled.isFinishing()||canceled.isDestroyed());assertEquals(before,settings.getAll());
  Activity importCanceled=backup(false,Activity.RESULT_OK);awaitAccessible("Restore settings");
  assertEquals("Nothing may change before confirmation",before,settings.getAll());clickAccessible("Cancel");settle();assertEquals(before,settings.getAll());
  Activity importing=backup(false,Activity.RESULT_OK);awaitAccessible("Restore settings");screenshot("backup-restore-confirmation");
  assertEquals(before,settings.getAll());clickAccessible("Restore");
  await(()->containsText(importing.getWindow().getDecorView(),"Settings restored. Contacts and the archive were preserved."),"Restore did not complete");
  assertEquals("hexagon",settings.getString("avatar_shape",""));assertTrue(settings.getBoolean("stats_expanded",false));assertTrue(settings.getBoolean("dark",false));
  assertEquals(new HashSet<>(Arrays.asList("0500099011","0500099000")),settings.getStringSet("favorites",Collections.emptySet()));
  assertEquals("Synthetic test note",settings.getString("note_0500099011",""));
 }
 private Activity backup(boolean export,int result)throws Exception{
  IntentFilter filter=new IntentFilter(export?Intent.ACTION_CREATE_DOCUMENT:Intent.ACTION_OPEN_DOCUMENT);filter.addCategory(Intent.CATEGORY_OPENABLE);filter.addDataType("application/json");
  Intent data=new Intent().setData(BackupDocumentProvider.DOCUMENT).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
  Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(filter,new Instrumentation.ActivityResult(result,data),true);
  try{
   Activity activity=getInstrumentation().startActivitySync(SettingsBackupActivity.intent(target,export).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));launched.add(activity);settle();
   assertEquals("Production SAF action was not launched",1,monitor.getHits());return activity;
  }finally{getInstrumentation().removeMonitor(monitor);}
 }
 private interface Condition {boolean ready();}
 private void await(Condition condition,String reason)throws Exception{
  long deadline=SystemClock.uptimeMillis()+5000;final boolean[] ready={false};
  do{getInstrumentation().waitForIdleSync();ui(()->ready[0]=condition.ready());if(ready[0])return;Thread.sleep(30);}while(SystemClock.uptimeMillis()<deadline);
  fail(reason);
 }
 private AccessibilityNodeInfo accessible(String text){
  AccessibilityNodeInfo root=getInstrumentation().getUiAutomation().getRootInActiveWindow();if(root==null)return null;
  List<AccessibilityNodeInfo> matches=root.findAccessibilityNodeInfosByText(text);
  for(AccessibilityNodeInfo node:matches){
   boolean matchesText=node.getText()!=null&&text.equalsIgnoreCase(node.getText().toString());
   String description=node.getContentDescription()==null?"":node.getContentDescription().toString();
   boolean matchesDescription=description.equalsIgnoreCase(text)||description.startsWith(text+", ");
   if((matchesText||matchesDescription)&&accessibleFullyVisible(node))return node;
  }
  return null;
 }
 private boolean accessibleFullyVisible(AccessibilityNodeInfo node){
  if(!node.isVisibleToUser())return false;Rect bounds=new Rect();node.getBoundsInScreen(bounds);if(bounds.isEmpty())return false;
  for(AccessibilityNodeInfo parent=node.getParent();parent!=null;parent=parent.getParent()){
   if(parent.isScrollable()||"android.widget.ScrollView".contentEquals(parent.getClassName())){
    Rect viewport=new Rect();parent.getBoundsInScreen(viewport);if(!viewport.contains(bounds))return false;
   }
  }
  return true;
 }
 private void awaitAccessible(String text)throws Exception{
  long deadline=SystemClock.uptimeMillis()+4000;do{if(accessible(text)!=null)return;Thread.sleep(40);}while(SystemClock.uptimeMillis()<deadline);fail("Missing accessible text: "+text);
 }
 private void revealAccessible(String text)throws Exception{
  for(int n=0;n<4;n++){
   AccessibilityNodeInfo match=accessible(text);if(match!=null)return;
   AccessibilityNodeInfo root=getInstrumentation().getUiAutomation().getRootInActiveWindow();scrollAccessible(root);settle();
  }
 }
 private boolean scrollAccessible(AccessibilityNodeInfo node){
  if(node==null)return false;if(node.isScrollable()&&node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))return true;
  for(int i=0;i<node.getChildCount();i++)if(scrollAccessible(node.getChild(i)))return true;return false;
 }
 private void clickAccessible(String text)throws Exception{
  awaitAccessible(text);AccessibilityNodeInfo node=accessible(text);Rect bounds=new Rect();node.getBoundsInScreen(bounds);assertFalse(bounds.isEmpty());tap(bounds.centerX(),bounds.centerY());settle();
 }
 private void restorePreferences(SharedPreferences preferences,Map<String,?> values){
  SharedPreferences.Editor edit=preferences.edit().clear();
  for(Map.Entry<String,?> item:values.entrySet()){
   Object value=item.getValue();String key=item.getKey();
   if(value instanceof String)edit.putString(key,(String)value);else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);
   else if(value instanceof Integer)edit.putInt(key,(Integer)value);else if(value instanceof Long)edit.putLong(key,(Long)value);
   else if(value instanceof Float)edit.putFloat(key,(Float)value);else if(value instanceof Set)edit.putStringSet(key,new HashSet<>((Set<String>)value));
  }
  assertTrue(edit.commit());
 }
 private byte[] readAll(InputStream input)throws Exception{
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] chunk=new byte[4096];int count;while((count=input.read(chunk))!=-1)bytes.write(chunk,0,count);return bytes.toByteArray();
 }
 private String shell(String command)throws Exception{
  ParcelFileDescriptor descriptor=getInstrumentation().getUiAutomation().executeShellCommand(command);
  try(ParcelFileDescriptor.AutoCloseInputStream input=new ParcelFileDescriptor.AutoCloseInputStream(descriptor)){return new String(readAll(input),java.nio.charset.StandardCharsets.UTF_8);}
 }
}
