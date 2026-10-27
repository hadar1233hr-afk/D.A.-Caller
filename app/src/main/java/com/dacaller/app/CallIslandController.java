package com.dacaller.app;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.WeakHashMap;

/** One resumed app window. Space is reserved above content; list rows stay reachable. */
final class CallIslandController implements CallIslandView.Listener {
 interface ManualHost {}
 private static final WeakHashMap<Application,Boolean> installed=new WeakHashMap<>();
 private final Activity host;
 private final CallIslandSession session;
 private final SharedPreferences prefs;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final IdentityHashMap<View,Integer> margins=new IdentityHashMap<>();
 private FrameLayout content;
 private CallIslandView view;
 private CallIslandState shown;
 private boolean started,expanded,refreshPosted,layingOut;
 private String token;
 private AlertDialog confirmation;
 private final Runnable changed=this::scheduleRefresh;
 private final Runnable update=()->{refreshPosted=false;refresh();};
 private final Runnable clock=new Runnable(){public void run(){
  if(!started||view==null||view.getParent()==null)return;
  CallIslandState live=session.current();
  if(live==null||shown==null||!samePresentation(live,shown)){refresh();return;}
  view.tick(System.currentTimeMillis());handler.postDelayed(this,1000);
 }};
 private final SharedPreferences.OnSharedPreferenceChangeListener preferenceChanged=(p,key)->{
  if(key==null||key.startsWith("island_")||key.equals("language")||key.equals("avatar_shape"))scheduleRefresh();
 };
 private final ViewTreeObserver.OnGlobalLayoutListener layout=()->{
  if(!started||layingOut||shown==null||view==null)return;
  if(view.getParent()!=content){scheduleRefresh();return;}reserveSpace();
 };
 CallIslandController(Activity host,CallIslandSession session){
  this.host=host;this.session=session;prefs=host.getSharedPreferences("da_caller",Context.MODE_PRIVATE);
 }
 static void install(Application app){
  synchronized(installed){if(installed.containsKey(app))return;installed.put(app,true);}
  app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){
   final IdentityHashMap<Activity,CallIslandController> windows=new IdentityHashMap<>();
   public void onActivityCreated(Activity activity,Bundle state){}
   public void onActivityStarted(Activity activity){}
   public void onActivityResumed(Activity activity){
    if(activity instanceof ManualHost||activity instanceof CallScreenActivity||activity instanceof CallIslandSettingsActivity||
       activity instanceof WidgetTap||activity.isFinishing())return;
    CallIslandController controller=windows.get(activity);
    if(controller==null){controller=new CallIslandController(activity,CallIslandSession.telecom());windows.put(activity,controller);}
    controller.start();
   }
   public void onActivityPaused(Activity activity){CallIslandController controller=windows.get(activity);if(controller!=null)controller.stop();}
   public void onActivityStopped(Activity activity){}
   public void onActivitySaveInstanceState(Activity activity,Bundle state){}
   public void onActivityDestroyed(Activity activity){CallIslandController controller=windows.remove(activity);if(controller!=null)controller.stop();}
  });
 }
 void start(){
  if(started||host.isFinishing()||host.isDestroyed())return;
  View root=host.findViewById(android.R.id.content);if(!(root instanceof FrameLayout))return;
  content=(FrameLayout)root;started=true;
  content.getViewTreeObserver().addOnGlobalLayoutListener(layout);
  PhoneSessionService.register(changed);prefs.registerOnSharedPreferenceChangeListener(preferenceChanged);refresh();
 }
 void stop(){
  if(!started)return;started=false;
  PhoneSessionService.unregister(changed);prefs.unregisterOnSharedPreferenceChangeListener(preferenceChanged);
  handler.removeCallbacksAndMessages(null);refreshPosted=false;
  if(content!=null&&content.getViewTreeObserver().isAlive())content.getViewTreeObserver().removeOnGlobalLayoutListener(layout);
  hide();
 }
 boolean isExpanded(){return expanded;}
 private void scheduleRefresh(){if(started&&!refreshPosted){refreshPosted=true;handler.post(update);}}
 void refresh(){
  if(!started)return;
  try{render();}catch(RuntimeException failure){
   // This optional surface must never interrupt the actual Telecom session.
   CallFailureReporter.record(host,"call-island-ui",failure);hide();
  }
 }
 private void render(){
  if(BuildConfig.DEBUG)android.util.Log.d("DA_CALLER_ISLAND","render expanded="+expanded);
  CallIslandState state=prefs.getBoolean("island_enabled",true)?session.current():null;
  if(state==null){expanded=false;token=null;hide();return;}
  if(!state.token.equals(token)){expanded=false;token=state.token;dismissConfirmation();}
  if(view==null){view=new CallIslandView(host);view.setListener(this);}
  int oldWidth=view.getWidth(),oldHeight=view.getHeight();
  shown=state;view.bind(state,expanded);
  int available=content.getWidth()>0?content.getWidth():host.getResources().getDisplayMetrics().widthPixels;
  int maximum=dp(expanded?420:"comfortable".equals(prefs.getString("island_size","compact"))?300:260);
  int width=Math.max(dp(160),Math.min(maximum,available-dp(24)));
  FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(width,ViewGroup.LayoutParams.WRAP_CONTENT,Gravity.TOP|Gravity.CENTER_HORIZONTAL);
  params.topMargin=dp(8);params.bottomMargin=dp(8);
  if(view.getParent()!=content){
   if(view.getParent() instanceof ViewGroup)((ViewGroup)view.getParent()).removeView(view);
   content.addView(view,params);
  }else if(view.getLayoutParams().width!=width)view.setLayoutParams(params);
  view.bringToFront();view.tick(System.currentTimeMillis());reserveSpace();
  handler.removeCallbacks(clock);handler.postDelayed(clock,1000);
  if(oldWidth>0&&oldHeight>0&&prefs.getBoolean("island_animations",true)&&ValueAnimator.areAnimatorsEnabled()){
   view.post(()->{
    if(!started||view==null||view.getParent()==null)return;
    int height=view.getHeight();if(height<=0||oldWidth==view.getWidth()&&oldHeight==height)return;
    view.animate().cancel();view.setPivotX(view.getWidth()/2f);view.setPivotY(0);
    view.setScaleX(Math.max(.6f,Math.min(1.4f,oldWidth/(float)view.getWidth())));
    view.setScaleY(Math.max(.35f,Math.min(1.5f,oldHeight/(float)height)));
    view.animate().scaleX(1).scaleY(1).setDuration(260).setInterpolator(new OvershootInterpolator(.5f)).start();
   });
  }else{view.animate().cancel();view.setScaleX(1);view.setScaleY(1);}
 }
 private static boolean samePresentation(CallIslandState a,CallIslandState b){
  return java.util.Objects.equals(a.token,b.token)&&java.util.Objects.equals(a.name,b.name)&&
   java.util.Objects.equals(a.number,b.number)&&a.state==b.state&&a.route==b.route&&
   a.supportedRoutes==b.supportedRoutes&&a.muted==b.muted&&a.canHold==b.canHold&&a.canMute==b.canMute&&
   a.contactId==b.contactId&&a.connectedAtMillis==b.connectedAtMillis;
 }
 private int dp(float n){return Math.round(host.getResources().getDisplayMetrics().density*n);}
 private void reserveSpace(){
  if(content==null||view==null||shown==null||layingOut)return;
  layingOut=true;
  try{
   int width=view.getLayoutParams().width;
   view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
   int inset=view.getMeasuredHeight()+dp(16);
   for(int i=0;i<content.getChildCount();i++){
    View child=content.getChildAt(i);if(child==view)continue;
    ViewGroup.LayoutParams raw=child.getLayoutParams();if(!(raw instanceof FrameLayout.LayoutParams))continue;
    FrameLayout.LayoutParams params=(FrameLayout.LayoutParams)raw;
    if(!margins.containsKey(child))margins.put(child,params.topMargin);
    int expected=margins.get(child)+inset;
    if(params.topMargin!=expected){params.topMargin=expected;child.setLayoutParams(params);}
   }
   for(View child:new ArrayList<>(margins.keySet()))if(child.getParent()!=content){restoreMargin(child);margins.remove(child);}
  }finally{layingOut=false;}
 }
 private void restoreMargin(View child){
  ViewGroup.LayoutParams raw=child.getLayoutParams();if(raw instanceof FrameLayout.LayoutParams){
   FrameLayout.LayoutParams params=(FrameLayout.LayoutParams)raw;params.topMargin=margins.get(child);child.setLayoutParams(params);
  }
 }
 private void dismissConfirmation(){if(confirmation!=null){confirmation.dismiss();confirmation=null;}}
 private void hide(){
  shown=null;handler.removeCallbacks(clock);dismissConfirmation();
  if(view!=null){view.animate().cancel();if(view.getParent() instanceof ViewGroup)((ViewGroup)view.getParent()).removeView(view);}
  for(View child:new ArrayList<>(margins.keySet()))restoreMargin(child);margins.clear();
 }
 @Override public void onExpand(){
  if(BuildConfig.DEBUG)android.util.Log.d("DA_CALLER_ISLAND","expand requested");
  if(shown==null)return;
  // Keep the complete control surface reachable on small phones while searching.
  // The focused editor and its query remain intact; only the keyboard is hidden.
  View focus=host.getCurrentFocus();
  android.view.inputmethod.InputMethodManager keyboard=host.getSystemService(android.view.inputmethod.InputMethodManager.class);
  if(keyboard!=null&&focus instanceof android.widget.EditText)keyboard.hideSoftInputFromWindow(focus.getWindowToken(),0);
  expanded=true;scheduleRefresh();
 }
 @Override public void onCollapse(){
  if(BuildConfig.DEBUG)android.util.Log.d("DA_CALLER_ISLAND","collapse requested");
  if(shown==null)return;
  // Finish dispatching this gesture before replacing its touched child tree.
  // Otherwise the same UP can reach the newly installed compact expand target.
  expanded=false;scheduleRefresh();
 }
 @Override public void onOpen(){
  if(shown==null)return;CallIslandState live=session.current();
  if(live!=null&&shown.token.equals(live.token))session.open(host,shown.token);else refresh();
 }
 @Override public void onAction(String action){
  CallIslandState state=shown;if(state==null)return;
  CallIslandState live=session.current();if(live==null||!live.token.equals(state.token)){refresh();return;}
  if(("END".equals(action)||"DECLINE".equals(action))&&prefs.getBoolean("island_confirm_end",true)){
   if(confirmation!=null)return;boolean english=I18n.isEnglish(host);
   confirmation=new AlertDialog.Builder(host).setTitle(english?"End this call?":"לסיים את השיחה?")
    .setMessage(state.name).setNegativeButton(english?"Cancel":"ביטול",null)
    .setPositiveButton(english?"End call":"סיים שיחה",(dialog,which)->{
     if(started&&shown!=null&&state.token.equals(shown.token)&&prefs.getBoolean("island_enabled",true))session.perform(state.token,action);
     refresh();
    }).create();
   confirmation.setOnDismissListener(dialog->confirmation=null);confirmation.show();
  }else{session.perform(state.token,action);refresh();}
 }
}
