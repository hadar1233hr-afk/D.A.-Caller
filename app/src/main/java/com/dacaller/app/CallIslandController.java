package com.dacaller.app;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.WindowInsets;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ScrollView;
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
 private FrameLayout content,layer;
 private boolean topAreaActive,statusColorSaved;
 private int originalStatusColor,statusBottom;
 private Rect positionArea=new Rect();
 private final ArrayList<Rect> cutouts=new ArrayList<>();
 private String dragToken;
 private Point dragStart,dragCurrent;
 private CallIslandView view;
 private ScrollView overflow;
 private View floating;
 private int desiredWidth,capsuleHeight;
 private CallIslandState shown;
 private boolean started,expanded,refreshPosted,layingOut;
 private android.graphics.drawable.Drawable originalContentBackground;
 private boolean backgroundReserved;
 private String token;
 private AlertDialog confirmation;
 private final Runnable changed=this::scheduleRefresh;
 private final Runnable update=()->{refreshPosted=false;refresh();};
 private final Runnable clock=new Runnable(){public void run(){
  if(!started||view==null||floating==null||floating.getParent()==null)return;
  CallIslandState live=session.current();
  if(live==null||shown==null||!samePresentation(live,shown)){refresh();return;}
  view.tick(System.currentTimeMillis());handler.postDelayed(this,1000);
 }};
 private final SharedPreferences.OnSharedPreferenceChangeListener preferenceChanged=(p,key)->{
  if(key==null||key.startsWith("island_")||key.equals("language")||key.equals("avatar_shape"))scheduleRefresh();
 };
 private final ViewTreeObserver.OnGlobalLayoutListener layout=()->{
  if(!started||layingOut||shown==null||view==null)return;
  if(floating==null||floating.getParent()!=layer){scheduleRefresh();return;}reserveSpace();
 };
 private final ViewTreeObserver.OnPreDrawListener systemBar=()->{
  // Pages can apply their palette after resume or on a later focus change.
  // Keep the opted-in decor surface visible without changing the host's flags.
  if(started&&shown!=null&&topAreaActive)keepStatusTransparent();
  return true;
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
  content.getViewTreeObserver().addOnPreDrawListener(systemBar);
  PhoneSessionService.register(changed);prefs.registerOnSharedPreferenceChangeListener(preferenceChanged);refresh();
 }
 void stop(){
  if(!started)return;started=false;
  PhoneSessionService.unregister(changed);prefs.unregisterOnSharedPreferenceChangeListener(preferenceChanged);
  handler.removeCallbacksAndMessages(null);refreshPosted=false;
  if(content!=null&&content.getViewTreeObserver().isAlive()){
   content.getViewTreeObserver().removeOnGlobalLayoutListener(layout);
   content.getViewTreeObserver().removeOnPreDrawListener(systemBar);
  }
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
  if(state.token.equals(prefs.getString("island_hidden_token",""))){hide();return;}
  configureLayer();
  if(view==null){view=new CallIslandView(host);view.setListener(this);}
  int oldWidth=view.getWidth(),oldHeight=view.getHeight();
  shown=state;view.bind(state,expanded);
  positionView();floating.bringToFront();view.tick(System.currentTimeMillis());reserveSpace();
  handler.removeCallbacks(clock);handler.postDelayed(clock,1000);
  if(oldWidth>0&&oldHeight>0&&prefs.getBoolean("island_animations",true)&&ValueAnimator.areAnimatorsEnabled()){
   view.post(()->{
    if(!started||view==null||floating==null||floating.getParent()==null)return;
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
   positionView();
   boolean customPosition=prefs.getBoolean(CallIslandPosition.SET,false)||dragToken!=null;
   if(customPosition){releaseSpace();return;}
   reserveBackground();
   int width=desiredWidth;
   view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
   int inset=capsuleHeight+dp(16);
   if(topAreaActive){int[] origin=new int[2];content.getLocationOnScreen(origin);int[] layerOrigin=new int[2];layer.getLocationOnScreen(layerOrigin);
    inset=Math.max(0,((FrameLayout.LayoutParams)floating.getLayoutParams()).topMargin+capsuleHeight+layerOrigin[1]-origin[1])+dp(8);}
   for(int i=0;i<content.getChildCount();i++){
    View child=content.getChildAt(i);if(child==floating)continue;
    ViewGroup.LayoutParams raw=child.getLayoutParams();if(!(raw instanceof FrameLayout.LayoutParams))continue;
    FrameLayout.LayoutParams params=(FrameLayout.LayoutParams)raw;
    if(!margins.containsKey(child))margins.put(child,params.topMargin);
    int expected=margins.get(child)+inset;
    if(params.topMargin!=expected){params.topMargin=expected;child.setLayoutParams(params);}
   }
   for(View child:new ArrayList<>(margins.keySet()))if(child.getParent()!=content){restoreMargin(child);margins.remove(child);}
   if(host instanceof MainActivity)((MainActivity)host).adaptCallIslandContent(expanded);
  }finally{layingOut=false;}
 }
 private void configureLayer(){
  View decor=host.getWindow().getDecorView();
  boolean requested=CallIslandPosition.topArea(prefs)&&decor instanceof FrameLayout;
  FrameLayout next=requested?(FrameLayout)decor:content;
  topAreaActive=requested;layer=next;
  if(requested)keepStatusTransparent();else restoreStatusColor();
 }
 private void keepStatusTransparent(){
  int current=host.getWindow().getStatusBarColor();
  if(!statusColorSaved||current!=android.graphics.Color.TRANSPARENT){originalStatusColor=current;statusColorSaved=true;}
  if(current!=android.graphics.Color.TRANSPARENT)host.getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
 }
 private void restoreStatusColor(){
  if(statusColorSaved){host.getWindow().setStatusBarColor(originalStatusColor);statusColorSaved=false;}
 }
 private void positionView(){
  if(layer==null||view==null)return;
  int parentWidth=layer.getWidth()>0?layer.getWidth():host.getResources().getDisplayMetrics().widthPixels;
  int parentHeight=layer.getHeight()>0?layer.getHeight():host.getResources().getDisplayMetrics().heightPixels;
  int maximum=dp(expanded?420:"comfortable".equals(prefs.getString("island_size","compact"))?300:260);
  if(topAreaActive&&!expanded&&parentWidth-dp(144)>=dp(160))maximum=Math.min(maximum,parentWidth-dp(144));
  int width=Math.max(dp(48),Math.min(maximum,parentWidth-dp(24)));
  int[] origin=new int[2];layer.getLocationOnScreen(origin);
  Rect visible=new Rect();layer.getWindowVisibleDisplayFrame(visible);
  int bottom=visible.bottom>origin[1]?Math.min(parentHeight,visible.bottom-origin[1]):parentHeight;
  positionArea.set(dp(12),dp(8),Math.max(dp(12),parentWidth-dp(12)),Math.max(dp(8),bottom-dp(8)));
  statusBottom=0;cutouts.clear();
  WindowInsets insets=layer.getRootWindowInsets();
  if(topAreaActive&&insets!=null){
   int left=insets.getSystemWindowInsetLeft(),right=insets.getSystemWindowInsetRight(),top=insets.getSystemWindowInsetTop(),bottomInset=insets.getSystemWindowInsetBottom();
   if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());left=bars.left;right=bars.right;top=bars.top;bottomInset=bars.bottom;}
   statusBottom=top;
   positionArea.left=Math.max(positionArea.left,left+dp(12));positionArea.right=Math.min(positionArea.right,parentWidth-right-dp(12));
   positionArea.bottom=Math.min(positionArea.bottom,parentHeight-bottomInset-dp(8));
   if(android.os.Build.VERSION.SDK_INT>=28&&insets.getDisplayCutout()!=null){
    for(Rect rect:insets.getDisplayCutout().getBoundingRects())cutouts.add(new Rect(rect));
    positionArea.top=Math.max(positionArea.top,insets.getDisplayCutout().getSafeInsetTop()+dp(8));
   }
   if(width>positionArea.width()-dp(120))positionArea.top=Math.max(positionArea.top,statusBottom+dp(8));
  }
  desiredWidth=width=Math.min(width,Math.max(dp(48),positionArea.width()));
  view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
  capsuleHeight=Math.min(view.getMeasuredHeight(),Math.max(dp(48),positionArea.height()));
  boolean needsScroll=view.getMeasuredHeight()>capsuleHeight;
  if(needsScroll){
   if(overflow==null){overflow=new ScrollView(host);overflow.setTag("island-in-app-window");overflow.setVerticalScrollBarEnabled(false);overflow.setFillViewport(false);overflow.setClipToPadding(false);}
   if(view.getParent()!=overflow){if(view.getParent() instanceof ViewGroup)((ViewGroup)view.getParent()).removeView(view);overflow.addView(view,new ScrollView.LayoutParams(-1,-2));}
   floating=overflow;
  }else{
   if(overflow!=null&&view.getParent()==overflow)overflow.removeView(view);
   if(overflow!=null&&overflow.getParent() instanceof ViewGroup)((ViewGroup)overflow.getParent()).removeView(overflow);
   floating=view;
  }
  if(floating.getParent()!=layer){
   if(floating.getParent() instanceof ViewGroup)((ViewGroup)floating.getParent()).removeView(floating);
   layer.addView(floating,new FrameLayout.LayoutParams(width,needsScroll?capsuleHeight:ViewGroup.LayoutParams.WRAP_CONTENT,Gravity.TOP|Gravity.LEFT));
  }
  Point point=dragToken!=null&&dragCurrent!=null?dragCurrent:CallIslandPosition.resolve(prefs,positionArea,width,capsuleHeight);
  point=CallIslandPosition.clamp(positionArea,width,capsuleHeight,point.x,point.y,statusBottom,dp(60),cutouts,dp(8));
  if(dragToken!=null)dragCurrent=point;
  FrameLayout.LayoutParams params=(FrameLayout.LayoutParams)floating.getLayoutParams();
  int expectedHeight=needsScroll?capsuleHeight:ViewGroup.LayoutParams.WRAP_CONTENT;
  if(params.width!=width||params.height!=expectedHeight||params.gravity!=(Gravity.TOP|Gravity.LEFT)||params.leftMargin!=point.x||params.topMargin!=point.y){
   params.width=width;params.height=expectedHeight;params.gravity=Gravity.TOP|Gravity.LEFT;params.leftMargin=point.x;params.topMargin=point.y;params.rightMargin=0;params.bottomMargin=0;floating.setLayoutParams(params);
  }
 }
 private void releaseSpace(){
  for(View child:new ArrayList<>(margins.keySet()))restoreMargin(child);margins.clear();
  if(backgroundReserved&&content!=null){content.setBackground(originalContentBackground);originalContentBackground=null;backgroundReserved=false;}
  if(host instanceof MainActivity)((MainActivity)host).adaptCallIslandContent(false);
 }
 private void reserveBackground(){
  if(!backgroundReserved){originalContentBackground=content.getBackground();backgroundReserved=true;}
  int color=prefs.getBoolean("amoled",false)?android.graphics.Color.BLACK:
    prefs.getBoolean("dark",false)?0xFF131313:0xFFF5F5F4;
  // The inset exposes android.R.id.content, which otherwise retains the
  // platform's white window background even above a dark app page.
  for(int i=0;i<content.getChildCount();i++){
   View child=content.getChildAt(i);if(child==floating)continue;
   if(child.getBackground() instanceof android.graphics.drawable.ColorDrawable){
    color=((android.graphics.drawable.ColorDrawable)child.getBackground()).getColor();break;
   }
  }
  if(!(content.getBackground() instanceof android.graphics.drawable.ColorDrawable)||
    ((android.graphics.drawable.ColorDrawable)content.getBackground()).getColor()!=color)content.setBackground(new android.graphics.drawable.ColorDrawable(color));
 }
 private void restoreMargin(View child){
  ViewGroup.LayoutParams raw=child.getLayoutParams();if(raw instanceof FrameLayout.LayoutParams){
   FrameLayout.LayoutParams params=(FrameLayout.LayoutParams)raw;params.topMargin=margins.get(child);child.setLayoutParams(params);
  }
 }
 private void dismissConfirmation(){if(confirmation!=null){confirmation.dismiss();confirmation=null;}}
 private void hide(){
  shown=null;handler.removeCallbacks(clock);dismissConfirmation();
  if(view!=null){view.animate().cancel();if(floating!=null&&floating.getParent() instanceof ViewGroup)((ViewGroup)floating.getParent()).removeView(floating);}
  dragToken=null;dragStart=null;dragCurrent=null;releaseSpace();restoreStatusColor();
 }
 private boolean owns(String token){
  CallIslandState live=session.current();return started&&shown!=null&&shown.token.equals(token)&&live!=null&&live.token.equals(token)&&
    prefs.getBoolean("island_enabled",true)&&!token.equals(prefs.getString("island_hidden_token",""));
 }
 @Override public void onHide(String token){if(!owns(token))return;prefs.edit().putString("island_hidden_token",token).apply();hide();}
 @Override public boolean draggable(){return true;}
 @Override public boolean onDragStart(String token){
  if(!owns(token)||view==null)return false;dismissConfirmation();view.animate().cancel();view.setScaleX(1);view.setScaleY(1);dragToken=token;
  FrameLayout.LayoutParams params=(FrameLayout.LayoutParams)floating.getLayoutParams();dragStart=new Point(params.leftMargin,params.topMargin);dragCurrent=new Point(dragStart);return true;
 }
 @Override public void onDragMove(String token,float dx,float dy){
  if(dragToken==null||!dragToken.equals(token)||!owns(token)){scheduleRefresh();return;}
  dragCurrent=new Point(Math.round(dragStart.x+dx),Math.round(dragStart.y+dy));positionView();releaseSpace();
 }
 @Override public void onDragEnd(String token,boolean cancelled){
  boolean save=dragToken!=null&&dragToken.equals(token)&&!cancelled&&owns(token)&&dragCurrent!=null;
  Point point=dragCurrent;dragToken=null;dragStart=null;dragCurrent=null;
  if(save)CallIslandPosition.save(host,prefs,positionArea,desiredWidth,capsuleHeight,point.x,point.y);
  scheduleRefresh();
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
