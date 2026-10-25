package com.dacaller.app;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.VideoProfile;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;

/** Five original presentations over the existing Telecom session. No networking or data migration. */
public final class CallScreenActivity extends Activity {
 private final Handler clockHandler=new Handler(Looper.getMainLooper());
 private final Runnable changed=()->runOnUiThread(this::refreshSafely);
 private final Runnable tickTask=new Runnable(){public void run(){
  try{updateTimer();updateBadge();}catch(RuntimeException e){CallFailureReporter.record(CallScreenActivity.this,"incall-timer",e);}
  clockHandler.postDelayed(this,1000);
 }};
 private SharedPreferences prefs;
 private CallThemes theme;
 private String themeKey="",drawnState="",panel="normal",lastNumber="";
 private FrameLayout shell,portraitFrame;
 private LinearLayout buttons,hero,compactHero;
 private TextView caller,phase,timer,badge,identity,initials,compactCaller,compactIdentity,compactTimer;
 private ImageView portrait;
 private Call toneCall,displayedCall;
 private View animatedSheet;
 private boolean previewMuted;
 private int previewRoute=CallAudioState.ROUTE_EARPIECE;
 private final Runnable stopTone=()->{
  if(toneCall!=null){try{toneCall.stopDtmfTone();}catch(RuntimeException ignored){}toneCall=null;}
 };
 private int dp(float value){return (int)(getResources().getDisplayMetrics().density*value+0.5f);}
 private boolean largeType(){return compact()&&getResources().getConfiguration().fontScale>1.15f;}
 private String previewHeadsetName(){String name=getIntent().getStringExtra("preview_headset_name");return name==null?"F901":name;}
 private boolean compact(){return getResources().getDisplayMetrics().heightPixels/getResources().getDisplayMetrics().density<640;}
 private boolean preview(){return BuildConfig.DEBUG&&getIntent()!=null&&getIntent().getBooleanExtra("preview_ui",false);}
 private boolean previewBluetooth(){return preview()&&getIntent().getBooleanExtra("preview_bluetooth",false);}
 private boolean muted(){return preview()?previewMuted:PhoneSessionService.muted();}
 private boolean speaker(){return preview()?previewRoute==CallAudioState.ROUTE_SPEAKER:PhoneSessionService.speaker();}
 private int route(){return preview()?previewRoute:CallAudioRoutes.route();}
 private String tr(String heb){return I18n.text(this,heb);}
 private void feedback(View view){TouchFeedback.pulse(this,prefs,view);}
 private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setGravity(Gravity.CENTER_HORIZONTAL);return l;}
 private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
 private TextView text(String s,int size,int color,boolean bold){return styledText(new TextView(this),s,size,color,bold);}
 private <T extends TextView>T styledText(T v,String s,int size,int color,boolean bold){
  v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setIncludeFontPadding(false);v.setGravity(Gravity.CENTER);
  v.setTypeface(Typeface.create(theme!=null&&theme.style==4?"monospace":bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));return v;
 }
 private GradientDrawable rounded(int color,float radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
 private ImageView icon(int resource,int color,int size){
  ImageView v=new ImageView(this);v.setImageResource(resource);v.setImageTintList(ColorStateList.valueOf(color));
  v.setScaleType(ImageView.ScaleType.FIT_CENTER);if(resource==R.drawable.ic_control_more)v.setRotation(90);v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);return v;
 }
 private void add(LinearLayout parent,View child,int width,int height,int top){
  LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(width<0?width:dp(width),height<0?height:dp(height));
  p.topMargin=dp(top);parent.addView(child,p);
 }
 private String currentThemeKey(){return prefs.getString("call_theme","pixel")+"/"+prefs.getBoolean("dark",false)+"/"+prefs.getBoolean("amoled",false)+"/"+prefs.getString("language","he")+"/"+AvatarShapes.selected(this);}
 private String initialPanel(){
  String value=preview()?getIntent().getStringExtra("preview_panel"):null;
  return "audio".equals(value)||"more".equals(value)||"keypad".equals(value)?value:"normal";
 }
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);prefs=getSharedPreferences("da_caller",MODE_PRIVATE);
  panel=saved==null?initialPanel():saved.getString("call_panel","normal");
  previewRoute=previewBluetooth()?CallAudioState.ROUTE_BLUETOOTH:CallAudioState.ROUTE_EARPIECE;
  if(Build.VERSION.SDK_INT>=27){setShowWhenLocked(true);setTurnScreenOn(true);}
  else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED|WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
  try{createShell();}catch(RuntimeException failure){CallFailureReporter.record(this,"incall-create-ui",failure);showEmergencyCallControls();}
  PhoneSessionService.register(changed);refreshSafely();
 }
 @Override protected void onSaveInstanceState(Bundle out){out.putString("call_panel",panel);super.onSaveInstanceState(out);}
 @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(preview())panel=initialPanel();drawnState="";refreshSafely();}
 @Override protected void onResume(){super.onResume();PhoneSessionService.setScreenVisible(true);clockHandler.removeCallbacks(tickTask);clockHandler.post(tickTask);drawnState="";refreshSafely();}
 @Override protected void onPause(){cancelSheetAnimation();clockHandler.removeCallbacks(tickTask);clockHandler.removeCallbacks(stopTone);stopTone.run();PhoneSessionService.setScreenVisible(false);super.onPause();}
 private void createShell(){
  theme=new CallThemes(this);themeKey=currentThemeKey();
  getWindow().setStatusBarColor(theme.background);getWindow().setNavigationBarColor(theme.style==1?theme.surface:theme.background);
  getWindow().getDecorView().setSystemUiVisibility(theme.dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
  shell=new FrameLayout(this);shell.setTag("call-root");shell.setLayoutDirection(I18n.direction(this));shell.setBackgroundColor(theme.background);
  if(theme.style==2&&!theme.amoled){
   GradientDrawable glass=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{theme.background,theme.dark?0xFF344451:0xFFD9E4EF,theme.background});shell.setBackground(glass);
  }
  if(theme.style==4)shell.addView(new DotField(this,theme.dark?0xFF252525:0xFFE1E1DE),new FrameLayout.LayoutParams(-1,-1));
  hero=col();hero.setPadding(dp(theme.style==1?18:24),0,dp(theme.style==1?18:24),0);
  FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);shell.addView(hero,hp);
  caller=text("",compact()?28:34,theme.ink,theme.style==0||theme.style==3);caller.setTag("call-caller");caller.setSingleLine(true);caller.setEllipsize(android.text.TextUtils.TruncateAt.END);
  identity=text("",15,theme.muted,false);identity.setTag("call-identity");identity.setSingleLine(true);identity.setEllipsize(android.text.TextUtils.TruncateAt.END);
  phase=text("",14,theme.muted,false);phase.setTag("call-phase");phase.setSingleLine(true);timer=text("",theme.style==4?38:theme.style==3?34:17,theme.muted,false);timer.setTag("call-timer");
  badge=text("VoLTE",10,theme.accent,true);badge.setVisibility(View.GONE);
  portraitFrame=AvatarShapes.frame(this);portraitFrame.setTag("call-portrait");portraitFrame.setBackgroundColor(theme.surface2);
  portrait=new ImageView(this);portrait.setScaleType(ImageView.ScaleType.CENTER_CROP);portrait.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
  portraitFrame.addView(portrait,new FrameLayout.LayoutParams(-1,-1));initials=text("DA",theme.style==4?18:28,theme.accent,true);portraitFrame.addView(initials,new FrameLayout.LayoutParams(-1,-1));
  if(theme.style==1){
   add(hero,timer,-1,-2,compact()?48:66);add(hero,caller,-1,-2,17);add(hero,identity,-1,-2,10);add(hero,portraitFrame,compact()?98:112,compact()?98:112,20);add(hero,phase,-1,-2,10);
  }else if(theme.style==0||theme.style==2){
   int avatar=theme.style==0?(compact()?76:100):(compact()?66:88);
   add(hero,portraitFrame,avatar,avatar,compact()?20:40);add(hero,caller,-1,-2,14);add(hero,identity,-1,-2,8);add(hero,phase,-1,-2,10);add(hero,timer,-1,-2,5);
   if(theme.style==2){portraitFrame.setElevation(dp(6));portraitFrame.setBackground(theme.card(this,1));}
  }else{
   TextView brand=text(theme.style==4?"DA / 01":"DA CALLER",12,theme.muted,true);brand.setGravity(Gravity.START);add(hero,brand,-1,-2,26);
   LinearLayout profile=row();profile.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);add(hero,profile,-1,-2,theme.style==4?24:28);
   LinearLayout names=col();names.setGravity(Gravity.START);profile.addView(names,new LinearLayout.LayoutParams(0,-2,1));
   caller.setGravity(Gravity.START);identity.setGravity(Gravity.START);add(names,caller,-1,-2,0);add(names,identity,-1,-2,8);
   LinearLayout.LayoutParams avatar=new LinearLayout.LayoutParams(dp(theme.style==4?48:62),dp(theme.style==4?48:62));avatar.leftMargin=dp(12);profile.addView(portraitFrame,avatar);
   timer.setGravity(Gravity.START);phase.setGravity(Gravity.START);add(hero,timer,-1,-2,theme.style==4?28:22);add(hero,phase,-1,-2,7);
  }
  AvatarShapes.apply(portraitFrame);
  add(hero,badge,-2,-2,4);
  compactHero=col();compactHero.setPadding(dp(20),0,dp(20),0);compactHero.setVisibility(View.GONE);
  boolean google=theme.style==1;
  compactTimer=text("",google?15:14,theme.muted,false);
  compactCaller=text("",google?24:21,theme.ink,theme.style==0||theme.style==3);compactCaller.setSingleLine(true);compactCaller.setEllipsize(android.text.TextUtils.TruncateAt.END);
  compactIdentity=text("",google?13:12,theme.muted,false);compactIdentity.setSingleLine(true);compactIdentity.setEllipsize(android.text.TextUtils.TruncateAt.END);
  add(compactHero,compactTimer,-1,-2,google?14:4);add(compactHero,compactCaller,-1,-2,google?6:4);add(compactHero,compactIdentity,-1,-2,4);
  shell.addView(compactHero,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
  buttons=col();buttons.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
  FrameLayout.LayoutParams controls=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);shell.addView(buttons,controls);
  setContentView(shell);lastNumber="";
 }
 private static final class DotField extends View {
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);DotField(Context c,int color){super(c);paint.setColor(color);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
  @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float d=getResources().getDisplayMetrics().density;
   for(float x=14*d;x<getWidth();x+=12*d)for(float y=12*d;y<getHeight()/2f;y+=12*d)canvas.drawCircle(x,y,0.9f*d,paint);
  }
 }
 private void updateCaller(Call call){
  String number=tr("מספר לא מזוהה");try{if(call.getDetails()!=null&&call.getDetails().getHandle()!=null)number=call.getDetails().getHandle().getSchemeSpecificPart();}catch(RuntimeException ignored){}
  if(lastNumber.equals(number))return;caller.setText(number);identity.setText("");lastNumber=number;final String expected=number;
  portrait.setImageDrawable(null);initials.setVisibility(View.VISIBLE);
  ContactPhotos.lookup(this,number,(id,name,picture)->{
   if(isFinishing()||isDestroyed()||!expected.equals(lastNumber))return;
   boolean named=name!=null&&!name.equals(expected)&&!name.trim().isEmpty();caller.setText(named?name:expected);identity.setText(named?expected:"");
   initials.setText(named?name.substring(0,name.offsetByCodePoints(0,Math.min(2,name.codePointCount(0,name.length())))):"DA");
   portrait.setImageBitmap(picture);initials.setVisibility(picture==null?View.VISIBLE:View.GONE);syncCompactHeader();
  });
 }
 private void updateTimer(){
  if(timer==null||preview())return;Call call=PhoneSessionService.current;if(call==null)return;
  if(call.getState()!=Call.STATE_ACTIVE){timer.setText("");return;}
  long connected=0;try{if(call.getDetails()!=null)connected=call.getDetails().getConnectTimeMillis();}catch(RuntimeException ignored){}
  long seconds=connected>0?Math.max(0,(System.currentTimeMillis()-connected)/1000):0;timer.setText(String.format(Locale.US,"%02d:%02d",seconds/60,seconds%60));if(compactTimer!=null)compactTimer.setText(timer.getText());
 }
 private void updateBadge(){
  if(badge==null||preview())return;Call call=PhoneSessionService.current;
  badge.setVisibility(call!=null&&call.getState()==Call.STATE_ACTIVE&&VolteStatus.isVoiceOnLte(this)?View.VISIBLE:View.GONE);
 }
 private void refreshSafely(){try{refresh();}catch(RuntimeException failure){CallFailureReporter.record(this,"incall-render",failure);showEmergencyCallControls();}}
 private void refresh(){
  if(isFinishing()||isDestroyed())return;
  Call call=preview()?null:PhoneSessionService.current;
  if(!preview()&&(call==null||call.getState()==Call.STATE_DISCONNECTED)){stopTone.run();finish();return;}
  if(!currentThemeKey().equals(themeKey)){createShell();drawnState="";}
  if(displayedCall!=call){clockHandler.removeCallbacks(stopTone);stopTone.run();displayedCall=call;panel="normal";}
  int state=preview()?Call.STATE_ACTIVE:call.getState();
  if(state!=Call.STATE_ACTIVE){clockHandler.removeCallbacks(stopTone);stopTone.run();}
  if(state==Call.STATE_RINGING||state==Call.STATE_DIALING||state==Call.STATE_CONNECTING)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  String stage=state==Call.STATE_RINGING?"שיחה נכנסת":state==Call.STATE_DIALING?"מחייג…":state==Call.STATE_CONNECTING?"מתחבר…":state==Call.STATE_HOLDING?"בהמתנה":"שיחה פעילה";
  phase.setText(tr(stage));phase.setVisibility(state==Call.STATE_ACTIVE&&(theme.style==1||largeType()&&theme.style!=4)?View.GONE:View.VISIBLE);
  if(preview())setPreviewIdentity();
  else{updateCaller(call);updateTimer();updateBadge();}
  applyPanelHeader();
  String config=System.identityHashCode(call)+"/"+state+"/"+muted()+"/"+speaker()+"/"+CallAudioRoutes.route()+"/"+CallAudioRoutes.supported()+"/"+CallAudioRoutes.bluetoothName()+"/"+panel+"/"+prefs.getString("answer_mode","ios")+"/"+canHold(call,state)+"/"+canAdd(call,state);
  for(String id:InCallControls.IDS)config+="/"+InCallControls.enabled(prefs,id);
  if(config.equals(drawnState))return;drawnState=config;cancelSheetAnimation();buttons.removeAllViews();
  if(state==Call.STATE_RINGING){panel="normal";buttons.setPadding(dp(18),dp(10),dp(18),dp(20));incoming(call);}else active(call,state);
 }
 /** Synthetic strings are fixture data, never production caller information. */
 @android.annotation.SuppressLint("SetTextI18n")
 private void setPreviewIdentity(){
  String name=getIntent().getStringExtra("preview_name");caller.setText(name==null?(I18n.isEnglish(this)?"Dana Abraham":"דנה אברהם"):name);identity.setText("050-123-4567");timer.setText("00:11");initials.setText("DA");badge.setVisibility(View.GONE);
 }
 private void syncCompactHeader(){
  if(compactCaller==null)return;compactCaller.setText(caller.getText());compactIdentity.setText(identity.getText());compactTimer.setText(timer.getText());
 }
 private void applyPanelHeader(){
  boolean condensed=!"normal".equals(panel)&&!("more".equals(panel)&&theme.style==1&&!largeType());
  hero.setVisibility(condensed?View.GONE:View.VISIBLE);compactHero.setVisibility(condensed?View.VISIBLE:View.GONE);
  boolean keypad="keypad".equals(panel);compactCaller.setVisibility(keypad?View.GONE:View.VISIBLE);compactIdentity.setVisibility(keypad?View.GONE:View.VISIBLE);
  // Canonical tags always describe the visible presentation (or a wholly hidden keypad title).
  caller.setTag(condensed?"call-caller-primary":"call-caller");compactCaller.setTag(condensed?"call-caller":"call-caller-condensed");
  identity.setTag(condensed?"call-identity-primary":"call-identity");compactIdentity.setTag(condensed?"call-identity":"call-identity-condensed");
  timer.setTag(condensed?"call-timer-primary":"call-timer");compactTimer.setTag(condensed?"call-timer":"call-timer-condensed");
  LinearLayout.LayoutParams time=(LinearLayout.LayoutParams)compactTimer.getLayoutParams();time.topMargin=dp(keypad?(theme.style==1?(largeType()?40:48):24):(theme.style==1?(largeType()?12:32):4));compactTimer.setLayoutParams(time);
  syncCompactHeader();
 }
 private void showEmergencyCallControls(){
  try{
   Call call=PhoneSessionService.current;if(call==null){if(!preview())finish();return;}
   LinearLayout fallback=col();fallback.setGravity(Gravity.CENTER);fallback.setPadding(dp(20),dp(20),dp(20),dp(20));fallback.setBackgroundColor(Color.BLACK);
   fallback.addView(text("DA CALLER · "+tr("שיחה פעילה"),22,Color.WHITE,true),new LinearLayout.LayoutParams(-1,0,1));
   if(call.getState()==Call.STATE_RINGING){TextView accept=text(tr("ענה"),20,Color.WHITE,true);accept.setBackgroundColor(0xFF16754D);add(fallback,accept,-1,64,12);accept.setOnClickListener(v->{if(call==PhoneSessionService.current)try{call.answer(VideoProfile.STATE_AUDIO_ONLY);}catch(RuntimeException ignored){}});}
   FrameLayout end=endButton(call);add(fallback,end,136,64,16);setContentView(fallback);
  }catch(RuntimeException secondary){CallFailureReporter.record(this,"incall-emergency-screen",secondary);}
 }
 private TextView tile(LinearLayout parent,int resource,String caption,int kind,int height,Runnable action){
  LinearLayout cell=col();cell.setGravity(Gravity.CENTER);cell.setBackground(theme.card(this,kind));
  cell.addView(icon(resource,kind==2?Color.WHITE:theme.accent,24),new LinearLayout.LayoutParams(dp(24),dp(24)));
  TextView label=text(tr(caption),13,kind==2?Color.WHITE:theme.ink,true);add(cell,label,-1,-2,6);
  LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(height),1);p.setMargins(dp(4),dp(4),dp(4),dp(4));parent.addView(cell,p);
  cell.setContentDescription(tr(caption));cell.setFocusable(true);TouchFeedback.bind(cell,prefs);cell.setOnClickListener(v->{feedback(cell);action.run();});return label;
 }
 private static final class GesturePrompt extends TextView {GesturePrompt(Context c){super(c);}@Override public boolean performClick(){return super.performClick();}}
 private void incoming(Call call){
  String mode=IncomingGesture.mode(prefs.getString("answer_mode","ios"));
  GesturePrompt prompt=styledText(new GesturePrompt(this),"",compact()?14:16,theme.accent,true);
  prompt.setBackground(theme.card(this,1));
  String title=mode.equals("vertical")?"↑ למעלה למענה · למטה לדחייה ↓":
    mode.equals("left")?"← שמאלה למענה · ימינה לדחייה →":
    mode.equals("right")?"→ ימינה למענה · שמאלה לדחייה ←":"החלק ימינה כדי לענות  →";
  // Keep both safety actions reachable by TalkBack, switch access and a simple tap.
  prompt.setText(tr(title));prompt.setContentDescription(tr(title));prompt.setFocusable(true);
  prompt.setOnClickListener(v->v.announceForAccessibility(tr(title)));
  LinearLayout.LayoutParams gesture=new LinearLayout.LayoutParams(-1,dp(compact()?78:100));
  gesture.setMargins(dp(7),dp(6),dp(7),dp(13));buttons.addView(prompt,gesture);
  final float[] down={0,0};final boolean[] tracking={false};
  prompt.setOnTouchListener((v,event)->{
   switch(event.getActionMasked()){
    case MotionEvent.ACTION_DOWN:
     tracking[0]=true;down[0]=event.getX();down[1]=event.getY();
     v.getParent().requestDisallowInterceptTouchEvent(true);return true;
    case MotionEvent.ACTION_POINTER_DOWN:
    case MotionEvent.ACTION_CANCEL:tracking[0]=false;return true;
    case MotionEvent.ACTION_UP:
     v.getParent().requestDisallowInterceptTouchEvent(false);
     if(!tracking[0])return true;
     tracking[0]=false;
     IncomingGesture.Action action=IncomingGesture.resolve(mode,event.getX()-down[0],event.getY()-down[1],dp(64));
     if(action==IncomingGesture.Action.ANSWER)answer(call,prompt);
     else if(action==IncomingGesture.Action.DECLINE)decline(call,prompt);
     else v.performClick();
     return true;
    default:return true;
   }
  });
  LinearLayout controls=row();buttons.addView(controls,new LinearLayout.LayoutParams(-1,-2));
  tile(controls,R.drawable.ic_phone_line,"ענה",1,compact()?65:77,()->answer(call,prompt));
  tile(controls,R.drawable.ic_control_close,"דחה",2,compact()?65:77,()->decline(call,prompt));
 }
 private void answer(Call call,View view){
  if(call!=PhoneSessionService.current||call.getState()!=Call.STATE_RINGING)return;
  feedback(view);
  try{call.answer(VideoProfile.STATE_AUDIO_ONLY);}catch(Exception e){callError();}
 }
 private void decline(Call call,View view){
  if(call!=PhoneSessionService.current||call.getState()!=Call.STATE_RINGING)return;
  feedback(view);
  try{call.reject(false,null);}catch(Exception e){callError();}
 }
 private void callError(){Toast.makeText(this,tr("פעולת השיחה לא הצליחה"),Toast.LENGTH_SHORT).show();}
 private void active(Call call,int state){
  buttons.setPadding(0,0,0,0);
  LinearLayout deck=col();deck.setTag("call-dock");
  int side=theme.style==1?16:20;deck.setPadding(dp(side),0,dp(side),dp(compact()?12:20));
  if(theme.style==1){
   GradientDrawable bg=rounded(theme.surface,32);bg.setCornerRadii(new float[]{dp(28),dp(28),dp(28),dp(28),0,0,0,0});deck.setBackground(bg);
  }else if(theme.style==2){
   GradientDrawable glass=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{theme.dark?0xD93C4E61:0xE6FFFFFF,theme.dark?0xD9253445:0xC9DCE6EF});
   glass.setCornerRadius(dp(34));glass.setStroke(dp(1),theme.dark?0xFF657A90:0xFFFFFFFF);deck.setBackground(glass);deck.setElevation(dp(4));
  }else if(theme.style==3||theme.style==4)deck.setBackground(rounded(theme.surface,theme.style==4?16:28));
  LinearLayout.LayoutParams deckSize=new LinearLayout.LayoutParams(-1,-2);
  if(theme.style==2)deckSize.setMargins(dp(12),0,dp(12),dp(12));buttons.addView(deck,deckSize);
  if(!"normal".equals(panel))addSheet(deck,call,state);
  boolean condensed=!"normal".equals(panel);
  if(theme.style==1||condensed){
   LinearLayout actions=row();actions.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);add(deck,actions,-1,-2,12);
   String[] order=theme.style==1?new String[]{"more","audio","mute","keypad"}:CallLayouts.primary(theme.style);
   for(String id:order)if(theme.style==1||actionEnabled(id))addMainAction(actions,id,call,state,true);
  }else{
   String[] order=CallLayouts.primary(theme.style);
   if(theme.style==0||theme.style==2){
    String[] six={order[0],order[1],order[2],order[3],"hold","addcall"};
    for(int r=0;r<2;r++){LinearLayout line=row();line.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);add(deck,line,-1,-2,r==0?6:8);for(int c=0;c<3;c++)if(actionEnabled(six[r*3+c]))addMainAction(line,six[r*3+c],call,state,false);}
   }else{
    for(int r=0;r<2;r++){LinearLayout line=row();line.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);add(deck,line,-1,-2,r==0?14:8);for(int c=0;c<2;c++)if(actionEnabled(order[r*2+c]))addMainAction(line,order[r*2+c],call,state,false);}
   }
  }
  FrameLayout end=endButton(call);
  int width=theme.style==1?134:theme.style==0?62:theme.style==2?154:theme.style==3?240:180;
  LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(dp(width),dp(theme.style==0?60:theme.style==2?56:theme.style==1&&compact()?54:compact()?62:68));ep.gravity=Gravity.CENTER_HORIZONTAL;ep.topMargin=dp(theme.style==1?12:18);deck.addView(end,ep);
 }
 private void addMainAction(LinearLayout line,String id,Call call,int state,boolean condensed){
  boolean selected="more".equals(id)?"more".equals(panel):"keypad".equals(id)?"keypad".equals(panel):"mute".equals(id)?muted():"audio".equals(id)?speaker()||route()==CallAudioState.ROUTE_BLUETOOTH:state==Call.STATE_HOLDING;
  String title=actionTitle(id,state);int resource=actionIcon(id,state);
  boolean enabled=!"hold".equals(id)||canHold(call,state);if("addcall".equals(id))enabled=canAdd(call,state);
  String visibleTitle=enabled?title:tr("לא זמין");
  LinearLayout cell=col();cell.setGravity(Gravity.CENTER);cell.setTag("call-action-"+id);cell.setContentDescription(title);cell.setFocusable(true);cell.setSelected(selected);
  if(Build.VERSION.SDK_INT>=30)cell.setStateDescription(tr(enabled?(selected?"נבחר":"לא נבחר"):"לא זמין בשיחה זו"));
  int cellHeight=condensed?80:theme.style==4?60:theme.style==3?76:82;
  LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(cellHeight),1);cp.setMargins(dp(3),0,dp(3),0);line.addView(cell,cp);
  if(theme.style==3&&!condensed||theme.style==4&&!condensed){
   cell.setBackground(rounded(selected?theme.selected:theme.surface2,theme.style==4?8:18));
   LinearLayout inside=row();inside.setGravity(Gravity.CENTER);inside.setPadding(dp(9),0,dp(9),0);cell.addView(inside,new LinearLayout.LayoutParams(-1,-1));
   inside.addView(icon(resource,selected?theme.selectedInk:theme.ink,25),new LinearLayout.LayoutParams(dp(25),dp(25)));
   TextView label=text(visibleTitle,13,selected?theme.selectedInk:theme.ink,true);label.setTag("call-action-label-"+id);label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.leftMargin=dp(8);inside.addView(label,lp);
  }else{
   FrameLayout circle=new FrameLayout(this);// Leave a complete scaled font line below the icon; do not borrow glyph rows.
   int diameter=condensed?(theme.style==2?52:largeType()?52:56):theme.style==2?56:54;
   GradientDrawable bg=rounded(selected?theme.selected:theme.surface2,selected&&theme.style==1?16:999);
   if(theme.style==2&&!selected){bg.setStroke(dp(1),theme.dark?0xFF7A8A9E:0xFFFFFFFF);circle.setElevation(dp(2));}
   circle.setBackground(bg);cell.addView(circle,new LinearLayout.LayoutParams(dp(diameter),dp(diameter)));
   ImageView glyph=icon(resource,selected?theme.selectedInk:theme.ink,27);circle.addView(glyph,new FrameLayout.LayoutParams(dp(27),dp(27),Gravity.CENTER));
   if(theme.style==1&&"audio".equals(id)&&route()==CallAudioState.ROUTE_BLUETOOTH){glyph.setTranslationX(dp(7));ImageView arrow=icon(R.drawable.ic_call_chevron,selected?theme.selectedInk:theme.ink,13);arrow.setTranslationX(dp(-14));circle.addView(arrow,new FrameLayout.LayoutParams(dp(13),dp(13),Gravity.CENTER));}
   TextView label=text(visibleTitle,12,theme.style==1?theme.muted:theme.ink,false);label.setTag("call-action-label-"+id);label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);add(cell,label,-1,-2,5);
  }
  if(!enabled){cell.setAlpha(0.45f);cell.setContentDescription(title+" · "+tr("לא זמין בשיחה זו"));cell.setEnabled(false);}
  final boolean allowed=enabled;TouchFeedback.bind(cell,prefs);
  cell.setOnClickListener(v->{feedback(cell);if(!allowed){Toast.makeText(this,tr("לא זמין בשיחה זו"),Toast.LENGTH_SHORT).show();return;}performAction(id,call,state);});
 }
 private boolean actionEnabled(String id){
  if("more".equals(id))return true;
  if("audio".equals(id))return InCallControls.enabled(prefs,"speaker")||InCallControls.enabled(prefs,"bluetooth");
  return InCallControls.enabled(prefs,id);
 }
 private String actionTitle(String id,int state){
  switch(id){case "audio":return audioTitle();case "mute":return tr("השתקה");case "keypad":return tr("לוח חיוג");case "more":return tr("עוד");case "hold":return tr(state==Call.STATE_HOLDING?"המשך":"המתנה");case "addcall":return tr("שיחה חדשה");case "contacts":return tr("אנשי קשר");default:return tr("הערה");}
 }
 private int actionIcon(String id,int state){
  switch(id){case "audio":return (route()==CallAudioState.ROUTE_BLUETOOTH)?R.drawable.ic_control_bluetooth:speaker()?R.drawable.ic_control_speaker:R.drawable.ic_phone_line;
   case "mute":return muted()?R.drawable.ic_control_mic_off:R.drawable.ic_control_mic;case "keypad":return theme.style==1?R.drawable.ic_call_dialpad:R.drawable.ic_control_dialpad;case "more":return R.drawable.ic_control_more;
   case "hold":return state==Call.STATE_HOLDING?R.drawable.ic_control_play:R.drawable.ic_control_pause;case "addcall":return R.drawable.ic_call_add;case "contacts":return R.drawable.ic_contacts;default:return R.drawable.ic_control_note;}
 }
 private String audioTitle(){if(preview()&&route()==CallAudioState.ROUTE_BLUETOOTH)return previewHeadsetName();if(route()==CallAudioState.ROUTE_BLUETOOTH)return CallAudioRoutes.bluetoothName();return tr(speaker()?"רמקול":"טלפון");}
 private boolean canHold(Call call,int state){if(preview())return true;return call!=null&&call.getDetails()!=null&&(state==Call.STATE_ACTIVE||state==Call.STATE_HOLDING)&&call.getDetails().can(Call.Details.CAPABILITY_HOLD);}
 private boolean canAdd(Call call,int state){return preview()||call!=null&&(state==Call.STATE_ACTIVE||state==Call.STATE_HOLDING)&&PhoneSessionService.instance!=null&&PhoneSessionService.instance.canAddCall();}
 private void performAction(String id,Call call,int state){
  switch(id){
   case "more":setPanel("more");break;case "audio":setPanel("audio");break;case "keypad":setPanel("keypad");break;
   case "mute":if(preview()){previewMuted=!previewMuted;drawnState="";refreshSafely();}else PhoneSessionService.mute(!PhoneSessionService.muted());break;
   case "hold":if(call!=PhoneSessionService.current||call==null||!canHold(call,call.getState()))return;try{if(call.getState()==Call.STATE_HOLDING)call.unhold();else call.hold();}catch(RuntimeException e){callError();}break;
   case "addcall":if(!preview()&&call==PhoneSessionService.current&&call!=null&&canAdd(call,call.getState())){Intent dial=new Intent(this,MainActivity.class).setAction(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);startActivity(dial);}break;
   case "contacts":if(!preview()){Intent open=new Intent(this,MainActivity.class).putExtra("show_contacts",true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);startActivity(open);}break;
   case "notes":if(!preview())openNote(call);break;
  }
 }
 private void setPanel(String value){
  cancelSheetAnimation();String next=value.equals(panel)?"normal":value;clockHandler.removeCallbacks(stopTone);stopTone.run();panel=next;drawnState="";refreshSafely();
  View sheet=shell.findViewWithTag("call-sheet-"+panel);
  if(sheet!=null&&ValueAnimator.areAnimatorsEnabled()){
   animatedSheet=sheet;sheet.setAlpha(0.35f);
   android.view.ViewPropertyAnimator animation=sheet.animate().alpha(1).setDuration(180);
   switch(theme.style){
    case 0:sheet.setTranslationX(dp(10));animation.translationX(0);break;
    case 2:sheet.setScaleX(0.97f);sheet.setScaleY(0.97f);animation.scaleX(1).scaleY(1);break;
    case 4:break; // A clean fade suits the monochrome geometry.
    default:sheet.setTranslationY(dp(theme.style==3?8:10));animation.translationY(0);break;
   }
   animation.start();
  }
 }
 private void cancelSheetAnimation(){
  if(animatedSheet==null)return;animatedSheet.animate().cancel();animatedSheet.setAlpha(1);animatedSheet.setTranslationX(0);animatedSheet.setTranslationY(0);animatedSheet.setScaleX(1);animatedSheet.setScaleY(1);animatedSheet=null;
 }
 private FrameLayout endButton(Call call){
  FrameLayout end=new FrameLayout(this);end.setTag("call-end");end.setContentDescription(tr("סיום שיחה"));end.setFocusable(true);
  int fill=theme==null?0xFFF46359:theme.end,ink=theme==null?Color.BLACK:theme.endInk;
  end.setBackground(rounded(fill,theme!=null&&theme.style==4?12:999));end.addView(icon(R.drawable.ic_call_hangup,ink,31),new FrameLayout.LayoutParams(dp(31),dp(31),Gravity.CENTER));
  TouchFeedback.bind(end,prefs);end.setOnClickListener(v->{if(call==null||call!=PhoneSessionService.current)return;feedback(end);try{if(call.getState()==Call.STATE_RINGING)call.reject(false,null);else call.disconnect();}catch(RuntimeException e){callError();}});return end;
 }
 /** Physical right matches the reference's fixed LTR row; Hebrew retains its text direction. */
 @android.annotation.SuppressLint("RtlHardcoded")
 private void alignSheetTextRight(TextView text){text.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);text.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);}
 private void addSheet(LinearLayout deck,Call call,int state){
  LinearLayout sheet=col();sheet.setTag("call-sheet-"+panel);sheet.setPadding(dp(theme.style==1?6:0),0,dp(theme.style==1?6:0),0);
  if(theme.style!=1){sheet.setBackground(rounded(theme.surface,theme.style==4?8:24));}
  add(deck,sheet,-1,-2,0);
  LinearLayout header=row();header.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);add(sheet,header,-1,48,0);
  FrameLayout close=new FrameLayout(this);close.setTag("call-sheet-close");close.setContentDescription(tr("סגור"));close.setFocusable(true);
  close.addView(icon(R.drawable.ic_control_close,theme.muted,23),new FrameLayout.LayoutParams(dp(23),dp(23),Gravity.CENTER));header.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48)));TouchFeedback.bind(close,prefs);close.setOnClickListener(v->setPanel(panel));
  TextView title=text("audio".equals(panel)?(previewBluetooth()||CallAudioRoutes.supported()==CallAudioState.ROUTE_BLUETOOTH?"Bluetooth":tr("פלט שמע")):"more".equals(panel)?tr("אפשרויות נוספות"):"",15,theme.ink,"audio".equals(panel));alignSheetTextRight(title);
  header.addView(title,new LinearLayout.LayoutParams(0,-1,1));
  if("keypad".equals(panel))addKeypad(sheet,call);else{
   ScrollView viewport=new ScrollView(this);viewport.setVerticalScrollBarEnabled(false);LinearLayout options=col();options.setPadding(0,dp(4),0,dp(2));viewport.addView(options);
   if("audio".equals(panel)){
    int supported=preview()?CallAudioState.ROUTE_EARPIECE|CallAudioState.ROUTE_SPEAKER|(previewBluetooth()?CallAudioState.ROUTE_BLUETOOTH:0):CallAudioRoutes.supported();
    if((supported&CallAudioState.ROUTE_BLUETOOTH)!=0){
     CallAudioRoutes.BluetoothTarget endpoint=preview()?null:CallAudioRoutes.bluetoothTarget();
     if(preview()||endpoint!=null)addAudioOption(options,previewBluetooth()?previewHeadsetName():endpoint.name,CallAudioState.ROUTE_BLUETOOTH,R.drawable.ic_control_bluetooth,endpoint);
    }
    if((supported&CallAudioState.ROUTE_SPEAKER)!=0)addAudioOption(options,tr("רמקול"),CallAudioState.ROUTE_SPEAKER,R.drawable.ic_control_speaker);
    if((supported&CallAudioState.ROUTE_WIRED_HEADSET)!=0)addAudioOption(options,tr("אוזניות קוויות"),CallAudioState.ROUTE_WIRED_HEADSET,R.drawable.ic_phone_line);
    if((supported&CallAudioState.ROUTE_EARPIECE)!=0)addAudioOption(options,tr("טלפון"),CallAudioState.ROUTE_EARPIECE,R.drawable.ic_phone_line);
    if(supported==0)add(options,text(tr("פלט השמע אינו זמין כרגע"),14,theme.muted,false),-1,56,0);
   }else{
    if(InCallControls.enabled(prefs,"addcall"))addMoreOption(options,"addcall",call,state,canAdd(call,state));if(InCallControls.enabled(prefs,"hold"))addMoreOption(options,"hold",call,state,canHold(call,state));
    // Existing optional actions remain reachable without crowding the primary dock.
    if(InCallControls.enabled(prefs,"contacts"))addMoreOption(options,"contacts",call,state,true);
    if(InCallControls.enabled(prefs,"notes"))addMoreOption(options,"notes",call,state,true);
   }
   int rows=options.getChildCount();int height=Math.min(rows*(compact()?54:60)+6,compact()?174:256);
   add(sheet,viewport,-1,height,0);
  }
 }
 private void addAudioOption(LinearLayout options,String label,int route,int resource){addAudioOption(options,label,route,resource,null);}
 private void addAudioOption(LinearLayout options,String label,int route,int resource,CallAudioRoutes.BluetoothTarget endpoint){
  boolean selected=route()==route;
  LinearLayout option=optionRow(options,label,resource,selected);option.setTag("audio-route-"+route);
  option.setContentDescription(label+(selected?" · "+tr("נבחר"):""));option.setSelected(selected);TouchFeedback.bind(option,prefs);
  option.setOnClickListener(v->{feedback(option);if(preview()){previewRoute=route;}else if(!(route==CallAudioState.ROUTE_BLUETOOTH&&endpoint!=null?CallAudioRoutes.selectBluetooth(endpoint):CallAudioRoutes.select(route))){callError();return;}panel="normal";drawnState="";refreshSafely();});
 }
 private void addMoreOption(LinearLayout options,String id,Call call,int state,boolean enabled){
  LinearLayout option=optionRow(options,actionTitle(id,state),actionIcon(id,state),false);option.setTag("call-option-"+id);
  option.setContentDescription(actionTitle(id,state)+(enabled?"":" · "+tr("לא זמין בשיחה זו")));
  if(!enabled){option.setAlpha(0.45f);option.setEnabled(false);TextView label=(TextView)option.getChildAt(1);label.setText(getString(R.string.call_action_status,actionTitle(id,state),tr("לא זמין בשיחה זו")));label.setSingleLine(false);label.setTextSize(13);}TouchFeedback.bind(option,prefs);
  option.setOnClickListener(v->{feedback(option);if(!enabled){Toast.makeText(this,tr("לא זמין בשיחה זו"),Toast.LENGTH_SHORT).show();return;}performAction(id,call,state);});
 }
 private LinearLayout optionRow(LinearLayout parent,String label,int resource,boolean selected){
  LinearLayout option=row();option.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);option.setPadding(dp(12),0,dp(12),0);option.setBackground(rounded(theme.surface2,theme.style==4?6:18));
  LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(-1,dp(compact()?52:58));op.topMargin=dp(2);parent.addView(option,op);
  TextView check=text(selected?"✓":"",20,theme.dark?theme.selected:theme.selectedInk,false);option.addView(check,new LinearLayout.LayoutParams(dp(24),-2));
  TextView name=text(label,16,theme.ink,false);alignSheetTextRight(name);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,-1,1);np.rightMargin=dp(12);option.addView(name,np);
  FrameLayout round=new FrameLayout(this);round.setBackground(rounded(selected?theme.selected:theme.surface,999));round.addView(icon(resource,selected?theme.selectedInk:theme.ink,23),new FrameLayout.LayoutParams(dp(23),dp(23),Gravity.CENTER));option.addView(round,new LinearLayout.LayoutParams(dp(36),dp(36)));option.setFocusable(true);return option;
 }
 private void addKeypad(LinearLayout sheet,Call call){
  LinearLayout keys=col();keys.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);add(sheet,keys,-1,-2,0);
  String[] digits={"1","2","3","4","5","6","7","8","9","*","0","#"};String[] letters={"","ABC","DEF","GHI","JKL","MNO","PQRS","TUV","WXYZ","","+",""};
  for(int r=0;r<4;r++){
   LinearLayout line=row();add(keys,line,-1,compact()?52:64,0);
   for(int c=0;c<3;c++){
    int i=r*3+c;String digit=digits[i];LinearLayout key=col();key.setGravity(Gravity.CENTER);key.setTag("dtmf-"+digit);key.setContentDescription(digit);key.setFocusable(true);
    boolean available=preview()||call!=null&&call.getState()==Call.STATE_ACTIVE;
    if(!available){key.setEnabled(false);key.setAlpha(0.4f);key.setContentDescription(digit+" · "+tr("לא זמין בשיחה זו"));}
    key.setBackground(rounded(theme.surface2,theme.style==4?8:theme.style==3?18:999));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(compact()?48:60),1);p.setMargins(dp(3),dp(2),dp(3),dp(2));line.addView(key,p);
    TextView number=text(digit,largeType()?21:compact()?26:30,theme.ink,false);number.setTag("dtmf-digit-"+digit);add(key,number,-1,-2,0);
    if(!letters[i].isEmpty()){TextView caption=text(letters[i],largeType()?9:10,theme.muted,false);caption.setTag("dtmf-caption-"+digit);add(key,caption,-1,-2,0);}
    TouchFeedback.bind(key,prefs);key.setOnClickListener(v->playDigit(call,digit.charAt(0),key));
   }
  }
 }
 private void playDigit(Call call,char digit,View key){
  if(preview())return;if(call==null||call!=PhoneSessionService.current||call.getState()!=Call.STATE_ACTIVE)return;
  feedback(key);clockHandler.removeCallbacks(stopTone);stopTone.run();try{toneCall=call;call.playDtmfTone(digit);clockHandler.postDelayed(stopTone,145);}catch(RuntimeException ignored){stopTone.run();}
 }
 private void openNote(Call call){
  String n="";
  try{if(call.getDetails()!=null&&call.getDetails().getHandle()!=null)n=call.getDetails().getHandle().getSchemeSpecificPart();}catch(Exception ignored){}
  final String key="note_"+Contacts.canonical(n);
  Dialog dialog=new Dialog(this);
  LinearLayout wrap=col();wrap.setPadding(dp(17),dp(16),dp(17),dp(16));wrap.setBackground(theme.card(this,0));
  wrap.addView(text(tr("הערה לשיחה"),18,theme.ink,true),new LinearLayout.LayoutParams(-1,dp(45)));
  EditText editor=new EditText(this);editor.setTextColor(theme.ink);editor.setHintTextColor(theme.muted);
  editor.setText(prefs.getString(key,""));editor.setHint(tr("כתוב הערה"));
  editor.setMinLines(3);editor.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
  wrap.addView(editor,new LinearLayout.LayoutParams(-1,dp(125)));
  TextView save=text(tr("שמירה"),17,theme.accent,true);
  wrap.addView(save,new LinearLayout.LayoutParams(-1,dp(45)));
  save.setOnClickListener(v->{feedback(save);prefs.edit().putString(key,editor.getText().toString()).apply();dialog.dismiss();});
  dialog.setContentView(wrap);dialog.show();
  Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.setLayout(-1,-2);}
 }
 @Override protected void onDestroy(){cancelSheetAnimation();clockHandler.removeCallbacksAndMessages(null);stopTone.run();PhoneSessionService.unregister(changed);super.onDestroy();}
 @Override public void onBackPressed(){if(!"normal".equals(panel)){setPanel(panel);return;}moveTaskToBack(true);}
}
