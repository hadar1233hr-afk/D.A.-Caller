package com.dacaller.app;

import android.app.Activity;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.test.InstrumentationTestCase;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** A real Android AppWidgetHost binds/adds/resizes/removes production RemoteViews with fictional contacts only. */
@SuppressWarnings("deprecation")
public final class WidgetSnapshotTest extends InstrumentationTestCase {
 private ListDispatchHostActivity activity;
 private AppWidgetHost host;
 private AppWidgetManager manager;
 private final List<Integer> allocated=new ArrayList<>();
 @Override protected void setUp()throws Exception{
  super.setUp();
  getInstrumentation().getUiAutomation().adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET");
  Intent intent=new Intent(getInstrumentation().getTargetContext(),ListDispatchHostActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
  activity=(ListDispatchHostActivity)getInstrumentation().startActivitySync(intent);
  manager=AppWidgetManager.getInstance(activity);
  ui(()->{host=new AppWidgetHost(activity,4106);host.startListening();});
 }
 @Override protected void tearDown()throws Exception{
  try{
   ui(()->{for(int id:allocated)host.deleteAppWidgetId(id);host.stopListening();activity.finish();});
   for(int id:allocated)assertNull("Widget id survived removal",manager.getAppWidgetInfo(id));
  }finally{getInstrumentation().getUiAutomation().dropShellPermissionIdentity();super.tearDown();}
 }
 private void ui(Runnable r)throws Exception{
  AtomicReference<Throwable> failure=new AtomicReference<>();
  getInstrumentation().runOnMainSync(()->{try{r.run();}catch(Throwable t){failure.set(t);}});
  if(failure.get()!=null)throw new AssertionError(failure.get());
 }
 private int dp(int x){return Math.round(x*activity.getResources().getDisplayMetrics().density);}
 private int add(Class<?> provider)throws Exception{
  int id=host.allocateAppWidgetId();allocated.add(id);
  assertTrue("Cannot bind widget host "+provider.getSimpleName(),manager.bindAppWidgetIdIfAllowed(id,new ComponentName(activity,provider)));
  assertNotNull(manager.getAppWidgetInfo(id));
  activity.getSharedPreferences("da_caller",Context.MODE_PRIVATE).edit()
   .putLong("widget_contact_"+id,-1).putString("widget_name_"+id,"דנה לדוגמה").putString("widget_phone_"+id,"0500000000").commit();
  return id;
 }
 private Bundle options(int width,int height){
  Bundle b=new Bundle();b.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,width);b.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,width);
  b.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,height);b.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,height);return b;
 }
 private AppWidgetHostView show(int id,String heading,int width,int height,RemoteViews views)throws Exception{
  final AppWidgetHostView[] view=new AppWidgetHostView[1];
  ui(()->{
   boolean dark=WidgetSupport.dark(activity);int bg=dark?0xFF101512:0xFFE9EFE7;
   activity.getWindow().setStatusBarColor(bg);activity.getWindow().setNavigationBarColor(bg);
   activity.getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
   LinearLayout root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);root.setGravity(Gravity.CENTER);root.setPadding(dp(8),dp(16),dp(8),dp(16));root.setBackgroundColor(bg);
   TextView title=new TextView(activity);title.setText(heading);title.setTextColor(WidgetSupport.ink(activity));title.setTextSize(18);title.setGravity(Gravity.CENTER);root.addView(title,new LinearLayout.LayoutParams(-1,dp(64)));
   view[0]=host.createView(activity,id,manager.getAppWidgetInfo(id));
   root.addView(view[0],new LinearLayout.LayoutParams(dp(width),dp(height)));
   activity.setContentView(root);
  });
  manager.updateAppWidget(id,views);getInstrumentation().waitForIdleSync();Thread.sleep(350);getInstrumentation().waitForIdleSync();
  return view[0];
 }
 private void visibleWithin(View view,View boundary)throws Exception{
  ui(()->{
   assertNotNull(view);assertEquals(View.VISIBLE,view.getVisibility());Rect a=new Rect(),b=new Rect();
   assertTrue(view.getGlobalVisibleRect(a));assertTrue(boundary.getGlobalVisibleRect(b));
   assertTrue("Widget content outside host: "+a+" / "+b,b.contains(a));
   assertTrue("Widget content clipped vertically",a.height()>=view.getHeight()-1);
  });
 }
 private void screenshot(String name)throws Exception{
  android.os.ParcelFileDescriptor fd=getInstrumentation().getUiAutomation().executeShellCommand("mkdir -p /sdcard/Download/DA-CALLER-snapshots && screencap -p /sdcard/Download/DA-CALLER-snapshots/widget-"+name+".png");
  try(android.os.ParcelFileDescriptor.AutoCloseInputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] buffer=new byte[1024];while(in.read(buffer)!=-1){}}
 }
 private List<Contacts.Person> people(int count){
  String[] names={"דנה","אורי","נועה","אדם","שירה"};ArrayList<Contacts.Person> result=new ArrayList<>();
  for(int i=0;i<count;i++)result.add(new Contacts.Person(names[i],"050000000"+i,-1));return result;
 }
 public void testRealHostAdditionResizeThemeEmptyAndRemoval()throws Exception{
  android.graphics.Bitmap initials=WidgetSupport.avatar(activity,-1,"דנה לדוגמה",96);
  assertEquals(0,android.graphics.Color.alpha(initials.getPixel(0,0)));assertEquals(255,android.graphics.Color.alpha(initials.getPixel(48,48)));initials.recycle();
  int quick=add(QuickWidget.class),card=add(ContactWidget.class),favorites=add(FavoritesWidget.class);
  for(boolean dark:new boolean[]{false,true}){
   activity.getSharedPreferences("da_caller",Context.MODE_PRIVATE).edit().putBoolean("dark",dark).putBoolean("amoled",false).putString("language","he").commit();
   String mode=dark?"dark":"light";
   manager.updateAppWidgetOptions(quick,options(56,72));Thread.sleep(300);
   AppWidgetHostView q=show(quick,"קיצור חיוג · 1×1",72,88,WidgetSupport.renderSingle(activity,quick,true));
   visibleWithin(q.findViewById(R.id.widget_name),q);visibleWithin(q.findViewById(R.id.widget_photo),q);screenshot(mode+"-quick-1x1");
   manager.updateAppWidgetOptions(card,options(140,64));Thread.sleep(300);
   AppWidgetHostView c=show(card,"כרטיס איש קשר · 2×1",156,80,WidgetSupport.renderSingle(activity,card,false));
   visibleWithin(c.findViewById(R.id.widget_subtitle),c);visibleWithin(c.findViewById(R.id.widget_photo),c);screenshot(mode+"-contact-2x1");
   manager.updateAppWidgetOptions(favorites,options(132,144));Thread.sleep(300);
   AppWidgetHostView f=show(favorites,"מועדפים · 2×2",148,160,WidgetSupport.renderFavorites(activity,favorites,manager.getAppWidgetOptions(favorites),people(5),true));
   for(int name:new int[]{R.id.fav1_name,R.id.fav2_name,R.id.fav3_name,R.id.fav4_name})visibleWithin(f.findViewById(name),f);
   assertNull(f.findViewById(R.id.fav5));screenshot(mode+"-favorites-2x2");
   manager.updateAppWidgetOptions(favorites,options(288,144));Thread.sleep(300);
   assertEquals(288,manager.getAppWidgetOptions(favorites).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH));
   AppWidgetHostView wide=show(favorites,"מועדפים · 4×2",304,160,WidgetSupport.renderFavorites(activity,favorites,manager.getAppWidgetOptions(favorites),people(5),true));
   visibleWithin(wide.findViewById(R.id.fav5_name),wide);visibleWithin(wide.findViewById(R.id.fav5_photo),wide);screenshot(mode+"-favorites-4x2");
   show(favorites,"הרשאת אנשי קשר",304,160,WidgetSupport.renderFavorites(activity,favorites,manager.getAppWidgetOptions(favorites),people(0),false));screenshot(mode+"-permission");
   show(favorites,"מועדפים ריקים",304,160,WidgetSupport.renderFavorites(activity,favorites,manager.getAppWidgetOptions(favorites),people(0),true));screenshot(mode+"-empty");
   AppWidgetHostView fewer=show(favorites,"שני מועדפים",304,160,WidgetSupport.renderFavorites(activity,favorites,manager.getAppWidgetOptions(favorites),people(2),true));
   ui(()->assertEquals(View.INVISIBLE,fewer.findViewById(R.id.fav3).getVisibility()));screenshot(mode+"-two-favorites");
  }
  activity.getSharedPreferences("da_caller",Context.MODE_PRIVATE).edit().putString("widget_name_"+quick,"אורי").putString("widget_phone_"+quick,"0500000001").commit();
  AppWidgetHostView replaced=show(quick,"החלפת איש קשר",72,88,WidgetSupport.renderSingle(activity,quick,true));
  ui(()->assertEquals("אורי",((TextView)replaced.findViewById(R.id.widget_name)).getText().toString()));screenshot("reconfigured");
 }
}
