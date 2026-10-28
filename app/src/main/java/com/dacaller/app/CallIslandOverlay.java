package com.dacaller.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.ContextThemeWrapper;
import android.widget.ScrollView;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Optional, call-bound window outside the app. The system permission is never requested here. */
final class CallIslandOverlay implements CallIslandView.Listener {
 interface Privacy {
  boolean permitted();
  boolean privateScreen();
  boolean insideApp();
 }
 interface Visibility {void changed(String token,boolean visible);}
 private static final Set<Application> installed=Collections.newSetFromMap(new WeakHashMap<>());
 private static final Set<Activity> resumed=Collections.newSetFromMap(new WeakHashMap<>());
 private static CallIslandOverlay active;
 private final Context context;
 private final CallIslandSession session;
 private final WindowManager manager;
 private final Privacy privacy;
 private final Visibility visibility;
 private final SharedPreferences prefs;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private CallIslandView view;
 private ScrollView window;
 private WindowManager.LayoutParams layout;
 private CallIslandState shown;
 private String token,reportedToken;
 private AlertDialog confirmation;
 private boolean started,attached,expanded,posted,screenOff;
 private final Runnable changed=this::schedule;
 private final Runnable update=()->{posted=false;refresh();};
 private final Runnable clock=this::refresh;
 private final SharedPreferences.OnSharedPreferenceChangeListener preferenceChanged=(p,key)->{
  if(key==null||key.startsWith("island_")||"language".equals(key)||"avatar_shape".equals(key))schedule();
 };
 private final android.app.AppOpsManager.OnOpChangedListener permissionChanged=(op,pkg)->{
  if(pkg==null||contextPackage().equals(pkg))schedule();
 };
 private final BroadcastReceiver screen=new BroadcastReceiver(){
  @Override public void onReceive(Context ignored,Intent event){
   screenOff=Intent.ACTION_SCREEN_OFF.equals(event.getAction());
   refresh();
  }
 };

 static void install(Application app){
  if(!installed.add(app))return;
  // Register before the in-app surface so there cannot be two visible islands.
  app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){
   public void onActivityCreated(Activity activity,Bundle state){}
   public void onActivityStarted(Activity activity){}
   public void onActivityResumed(Activity activity){
    resumed.add(activity);CallIslandOverlay owner=active;
    if(owner!=null){owner.handler.removeCallbacks(owner.update);owner.posted=false;owner.hide();}
   }
   public void onActivityPaused(Activity activity){
    resumed.remove(activity);CallIslandOverlay owner=active;
    if(owner!=null){
     owner.handler.removeCallbacks(owner.update);owner.posted=true;
     // Let the next own Activity resume before considering a window on Home.
     owner.handler.postDelayed(owner.update,180);
    }
   }
   public void onActivityStopped(Activity activity){}
   public void onActivitySaveInstanceState(Activity activity,Bundle state){}
   public void onActivityDestroyed(Activity activity){resumed.remove(activity);}
  });
 }

 static CallIslandOverlay create(PhoneSessionService service){
  Context ui=service;
  if(Build.VERSION.SDK_INT>=30){
   try{
    DisplayManager displays=service.getSystemService(DisplayManager.class);
    Display display=displays==null?null:displays.getDisplay(Display.DEFAULT_DISPLAY);
    if(display!=null)ui=service.createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,null);
   }catch(RuntimeException unavailable){CallFailureReporter.record(service,"island-window-context",unavailable);}
  }
  Context themed=new ContextThemeWrapper(ui,R.style.AppTheme);
  Privacy privacy=new Privacy(){
   public boolean permitted(){return Settings.canDrawOverlays(service);}
   public boolean privateScreen(){
    KeyguardManager lock=service.getSystemService(KeyguardManager.class);
    PowerManager power=service.getSystemService(PowerManager.class);
    return lock==null||power==null||lock.isKeyguardLocked()||!power.isInteractive();
   }
   public boolean insideApp(){return !resumed.isEmpty();}
  };
  return new CallIslandOverlay(themed,CallIslandSession.telecom(),themed.getSystemService(WindowManager.class),privacy,
    service::islandVisibilityChanged);
 }

 CallIslandOverlay(Context context,CallIslandSession session,WindowManager manager,Privacy privacy){
  this(context,session,manager,privacy,(token,visible)->{});
 }
 CallIslandOverlay(Context context,CallIslandSession session,WindowManager manager,Privacy privacy,Visibility visibility){
  this.context=context;this.session=session;this.manager=manager;this.privacy=privacy;
  this.visibility=visibility;
  prefs=context.getSharedPreferences("da_caller",Context.MODE_PRIVATE);
 }
 void start(){
  if(started)return;
  if(active!=null&&active!=this)active.stop();active=this;started=true;
  PhoneSessionService.register(changed);prefs.registerOnSharedPreferenceChangeListener(preferenceChanged);
  IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_SCREEN_OFF);
  filter.addAction(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_USER_PRESENT);
  if(Build.VERSION.SDK_INT>=33)context.registerReceiver(screen,filter,Context.RECEIVER_NOT_EXPORTED);
  else context.registerReceiver(screen,filter);
  try{
   android.app.AppOpsManager ops=context.getSystemService(android.app.AppOpsManager.class);
   if(ops!=null)ops.startWatchingMode(android.app.AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,context.getPackageName(),permissionChanged);
  }catch(RuntimeException unavailable){CallFailureReporter.record(context,"island-overlay-permission-watch",unavailable);}
  refresh();
 }
 void stop(){
  if(!started)return;started=false;
  if(active==this)active=null;
  PhoneSessionService.unregister(changed);prefs.unregisterOnSharedPreferenceChangeListener(preferenceChanged);
  handler.removeCallbacksAndMessages(null);posted=false;
  try{context.unregisterReceiver(screen);}catch(IllegalArgumentException ignored){}
  try{
   android.app.AppOpsManager ops=context.getSystemService(android.app.AppOpsManager.class);
   if(ops!=null)ops.stopWatchingMode(permissionChanged);
  }catch(RuntimeException unavailable){CallFailureReporter.record(context,"island-overlay-permission-unwatch",unavailable);}
  hide();
 }
 void configurationChanged(){schedule();}
 boolean isShowing(){return attached;}
 private String contextPackage(){return context.getPackageName();}
 private void schedule(){
  if(Looper.myLooper()!=Looper.getMainLooper()){handler.post(this::schedule);return;}
  if(started&&!posted){posted=true;handler.post(update);}
 }
 private boolean allowed(){
  return started&&prefs.getBoolean("island_enabled",true)&&prefs.getBoolean("island_outside_app",false)
    &&privacy.permitted()&&!screenOff&&!privacy.privateScreen()&&!privacy.insideApp();
 }
 private void refresh(){
  if(!started)return;
  handler.removeCallbacks(clock);
  CallIslandState live=null;
  try{
   if(!prefs.getBoolean("island_enabled",true)||!prefs.getBoolean("island_outside_app",false)){hide();return;}
   live=session.current();
   if(live==null||!allowed()){hide();return;}
   render(live);
  }catch(RuntimeException unavailable){CallFailureReporter.record(context,"island-overlay-ui",unavailable);hide();}
  finally{
   // Call/service callbacks handle the first/last call. Poll only during an owned live call.
   // The same check also removes a window if permission is revoked without an app-op callback.
   if(started&&live!=null)handler.postDelayed(clock,1000);
  }
 }
 private int dp(float value){return Math.round(context.getResources().getDisplayMetrics().density*value);}
 private void render(CallIslandState live){
  if(manager==null)return;
  if(!live.token.equals(token)){clearVisibility();expanded=false;token=live.token;dismissConfirmation();}
  if(view==null){
   view=new CallIslandView(context);view.setListener(this);
   window=new ScrollView(context);window.setTag("island-external-window");
   window.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
    public void onViewAttachedToWindow(View root){handler.post(CallIslandOverlay.this::reportVisibility);}
    public void onViewDetachedFromWindow(View root){clearVisibility();}
   });
   window.setFillViewport(false);window.setClipToPadding(false);window.setVerticalScrollBarEnabled(false);
   window.addView(view,new ScrollView.LayoutParams(-1,-2));
  }
  shown=live;view.bind(live,expanded);view.tick(System.currentTimeMillis());
  int availableWidth=context.getResources().getDisplayMetrics().widthPixels;
  int availableHeight=context.getResources().getDisplayMetrics().heightPixels;
  if(Build.VERSION.SDK_INT>=30){
   android.view.WindowMetrics metrics=manager.getCurrentWindowMetrics();
   if(metrics!=null){
    android.graphics.Rect bounds=metrics.getBounds();
    android.graphics.Insets insets=metrics.getWindowInsets().getInsetsIgnoringVisibility(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout());
    availableWidth=bounds.width()-insets.left-insets.right;
    availableHeight=bounds.height()-insets.top-insets.bottom;
   }
  }
  int maximum=dp(expanded?420:"comfortable".equals(prefs.getString("island_size","compact"))?300:260);
  int width=Math.max(dp(160),Math.min(maximum,availableWidth-dp(24)));
  view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
  int height=Math.min(view.getMeasuredHeight(),Math.max(dp(56),availableHeight-dp(32)));
  if(!attached){
   layout=new WindowManager.LayoutParams(width,height,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
     WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
   layout.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;layout.y=dp(8);layout.setTitle("DA CALLER call island");
   layout.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
   manager.addView(window,layout);attached=true;
  }else if(layout.width!=width||layout.height!=height){
   layout.width=width;layout.height=height;manager.updateViewLayout(window,layout);
  }
  reportVisibility();
 }
 private void clearVisibility(){
  String previous=reportedToken;reportedToken=null;
  if(previous!=null)visibility.changed(previous,false);
 }
 private void reportVisibility(){
  try{
   CallIslandState live=shown==null?null:session.current();
   if(!attached||window==null||!window.isAttachedToWindow()||!window.isShown()||window.getWindowVisibility()!=View.VISIBLE||
     live==null||!live.token.equals(shown.token)||!allowed()){
    clearVisibility();return;
   }
   if(live.token.equals(reportedToken))return;
   clearVisibility();reportedToken=live.token;visibility.changed(live.token,true);
  }catch(RuntimeException unavailable){CallFailureReporter.record(context,"island-overlay-visibility",unavailable);clearVisibility();}
 }
 private void dismissConfirmation(){
  AlertDialog dialog=confirmation;confirmation=null;
  if(dialog!=null)try{dialog.dismiss();}
   catch(RuntimeException unavailable){CallFailureReporter.record(context,"island-overlay-confirmation-dismiss",unavailable);}
 }
 private void hide(){
  clearVisibility();
  dismissConfirmation();shown=null;token=null;expanded=false;
  if(attached){
   attached=false;
   try{manager.removeViewImmediate(window);}catch(RuntimeException unavailable){CallFailureReporter.record(context,"island-overlay-remove",unavailable);}
  }
  if(view!=null){view.animate().cancel();view.bind(null,false);}
 }
 @Override public void onExpand(){if(shown!=null){expanded=true;schedule();}}
 @Override public void onCollapse(){if(shown!=null){expanded=false;schedule();}}
 @Override public void onOpen(){
  try{
   CallIslandState state=shown,live=session.current();
   if(state!=null&&live!=null&&state.token.equals(live.token)&&allowed())session.openOutside(context,state.token);
   else refresh();
  }catch(RuntimeException unavailable){CallFailureReporter.record(context,"island-overlay-open",unavailable);hide();}
 }
 @Override public void onAction(String action){
  try{action(action);}
  catch(RuntimeException unavailable){CallFailureReporter.record(context,"island-overlay-action",unavailable);hide();}
 }
 private void action(String action){
  CallIslandState state=shown,live=session.current();
  if(state==null||live==null||!state.token.equals(live.token)||!allowed()){refresh();return;}
  if(("END".equals(action)||"DECLINE".equals(action))&&prefs.getBoolean("island_confirm_end",true)){
   if(confirmation!=null)return;boolean english=I18n.isEnglish(context);
   confirmation=new AlertDialog.Builder(context).setTitle(english?"End this call?":"לסיים את השיחה?")
    .setMessage(state.name).setNegativeButton(english?"Cancel":"ביטול",null)
    .setPositiveButton(english?"End call":"סיים שיחה",(dialog,which)->{
     CallIslandState current=session.current();
     if(allowed()&&current!=null&&state.token.equals(current.token))session.perform(state.token,action);
     refresh();
    }).create();
   confirmation.setOnDismissListener(dialog->confirmation=null);
   confirmation.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
   confirmation.show();
  }else{session.perform(state.token,action);refresh();}
 }
}
