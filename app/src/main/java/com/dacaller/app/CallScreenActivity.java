package com.dacaller.app;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Call;
import android.telecom.VideoProfile;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;

public final class CallScreenActivity extends Activity {
 private static final int GOLD=0xFFE2C68D;
 private static final int SURFACE=0xFF191919;
 private static final int CONTROL=0xFF252525;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final Runnable changed=()->runOnUiThread(this::refresh);
 private final Runnable timer=new Runnable(){
  @Override public void run(){updateTimer();updateNetworkBadge();handler.postDelayed(this,1500);}
 };
 private LinearLayout root,buttons;
 private TextView number,callStatus,time,networkBadge;
 private String lastState="";
 private int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
 private boolean compact(){return getResources().getDisplayMetrics().heightPixels/getResources().getDisplayMetrics().density<630;}
 private boolean narrow(){return getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density<330;}
 private GradientDrawable shape(int c,int r){
  GradientDrawable g=new GradientDrawable();
  g.setColor(c);g.setCornerRadius(dp(r));return g;
 }
 private GradientDrawable glass(int selected){
  GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
    selected==1?new int[]{0xFF615139,0xFF28221A}:
    selected==2?new int[]{0xFFCC4747,0xFF922C2C}:new int[]{0xFF313131,0xFF1B1B1B});
  g.setCornerRadius(dp(18));
  g.setStroke(dp(1),selected==1?0xFF8F774E:selected==2?0xFFDD6666:0xFF494949);
  return g;
 }
 private TextView label(String str,int sp,int color,boolean strong){
  TextView v=new TextView(this);
  v.setText(str);v.setTextSize(sp);v.setTextColor(color);
  v.setGravity(Gravity.CENTER);v.setIncludeFontPadding(false);
  v.setTypeface(Typeface.create(strong?"sans-serif-medium":"sans-serif",Typeface.NORMAL));
  return v;
 }
 private LinearLayout vertical(){
  LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);
  v.setGravity(Gravity.CENTER);return v;
 }
 private LinearLayout horizontal(){
  LinearLayout v=new LinearLayout(this);v.setGravity(Gravity.CENTER);return v;
 }
 private void tick(View v){
  if(v!=null&&getSharedPreferences("da_caller",MODE_PRIVATE).getBoolean("haptics",true)){
   v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
  }
 }
 @Override public void onCreate(Bundle b){
  super.onCreate(b);setShowWhenLocked(true);setTurnScreenOn(true);
  getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  getWindow().setStatusBarColor(0xFF070707);
  getWindow().setNavigationBarColor(0xFF070707);
  build();PhoneSessionService.register(changed);
  handler.post(timer);refresh();
 }
 @Override protected void onNewIntent(android.content.Intent intent){
  super.onNewIntent(intent);setIntent(intent);refresh();
 }
 private void build(){
  root=vertical();root.setBackgroundColor(0xFF080808);
  root.setPadding(dp(narrow()?10:17),dp(compact()?6:16),dp(narrow()?10:17),dp(12));
  setContentView(root);
  TextView monogram=label("DA",12,GOLD,true);
  root.addView(monogram,new LinearLayout.LayoutParams(-1,dp(compact()?22:29)));
  callStatus=label("",14,0xFFC6C6C6,false);
  root.addView(callStatus,new LinearLayout.LayoutParams(-1,dp(compact()?29:34)));
  number=label("",compact()?24:30,Color.WHITE,true);
  number.setSingleLine(true);
  number.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
  root.addView(number,new LinearLayout.LayoutParams(-1,dp(compact()?43:57)));
  LinearLayout meta=horizontal();
  root.addView(meta,new LinearLayout.LayoutParams(-1,dp(28)));
  time=label("",14,GOLD,false);meta.addView(time,new LinearLayout.LayoutParams(-2,-1));
  networkBadge=label("VoLTE",10,GOLD,true);
  networkBadge.setBackground(shape(0xFF262015,9));
  networkBadge.setPadding(dp(8),0,dp(8),0);
  LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,dp(23));bp.leftMargin=dp(7);
  meta.addView(networkBadge,bp);networkBadge.setVisibility(View.GONE);
  TextView avatar=label("☎",compact()?36:43,GOLD,true);
  avatar.setBackground(glass(1));
  int avatarSize=compact()?72:95;
  LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(dp(avatarSize),dp(avatarSize));
  ap.topMargin=dp(compact()?6:15);ap.bottomMargin=dp(compact()?4:12);
  root.addView(avatar,ap);
  root.addView(new View(this),new LinearLayout.LayoutParams(1,0,1));
  buttons=vertical();
  root.addView(buttons,new LinearLayout.LayoutParams(-1,-2));
  root.addView(new View(this),new LinearLayout.LayoutParams(1,0,1));
 }
 private void actionTile(LinearLayout row,String symbol,String title,int style,Runnable click){
  LinearLayout tile=vertical();tile.setBackground(glass(style));
  tile.setElevation(dp(2));
  TextView icon=label(symbol,compact()?25:29,style==2?Color.WHITE:GOLD,true);
  tile.addView(icon,new LinearLayout.LayoutParams(-1,dp(compact()?38:46)));
  TextView caption=label(title,compact()?12:13,0xFFEFEFEF,true);
  tile.addView(caption,new LinearLayout.LayoutParams(-1,dp(25)));
  LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,dp(compact()?76:91),1);
  tp.setMargins(dp(5),dp(5),dp(5),dp(5));row.addView(tile,tp);
  tile.setOnClickListener(v->{tick(tile);click.run();});
 }
 private void endButton(Call call){
  TextView end=label("✕     סיום שיחה",compact()?16:18,Color.WHITE,true);
  end.setBackground(glass(2));
  LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(compact()?56:65));
  params.setMargins(dp(5),dp(10),dp(5),dp(6));
  buttons.addView(end,params);
  end.setOnClickListener(v->{tick(end);try{call.disconnect();}catch(Exception ignored){}finish();});
 }
 private void updateTimer(){
  Call call=PhoneSessionService.current;
  if(call==null||time==null)return;
  if(call.getState()!=Call.STATE_ACTIVE){time.setText("");return;}
  try{
   long started=call.getDetails().getConnectTimeMillis();
   long total=started>0?Math.max(0,(System.currentTimeMillis()-started)/1000):0;
   time.setText(String.format(Locale.US,"%02d:%02d",total/60,total%60));
  }catch(Exception ignored){}
 }
 private void updateNetworkBadge(){
  Call call=PhoneSessionService.current;
  if(networkBadge==null||call==null||call.getState()!=Call.STATE_ACTIVE){
   if(networkBadge!=null)networkBadge.setVisibility(View.GONE);
   return;
  }
  boolean knownVoLTE=VolteStatus.isVoiceOnLte(this);
  networkBadge.setVisibility(knownVoLTE?View.VISIBLE:View.GONE);
 }
 private void refresh(){
  Call call=PhoneSessionService.current;
  if(call==null||call.getState()==Call.STATE_DISCONNECTED){finish();return;}
  int state=call.getState();
  String phase=state==Call.STATE_RINGING?"שיחה נכנסת":
   state==Call.STATE_DIALING?"מחייג…":state==Call.STATE_CONNECTING?"מתחבר…":
   state==Call.STATE_HOLDING?"בהמתנה":state==Call.STATE_ACTIVE?"שיחה פעילה":"שיחה";
  callStatus.setText(phase);
  String shown="מספר לא מזוהה";
  try{
   if(call.getDetails()!=null&&call.getDetails().getHandle()!=null)
    shown=call.getDetails().getHandle().getSchemeSpecificPart();
  }catch(Exception ignored){}
  number.setText(shown);updateTimer();updateNetworkBadge();
  String configuration=state+"/"+PhoneSessionService.muted()+"/"+PhoneSessionService.speaker();
  if(configuration.equals(lastState))return;
  lastState=configuration;buttons.removeAllViews();
  if(state==Call.STATE_RINGING){
   LinearLayout incoming=horizontal();buttons.addView(incoming,new LinearLayout.LayoutParams(-1,-2));
   actionTile(incoming,"✓","ענה",1,()->{try{call.answer(VideoProfile.STATE_AUDIO_ONLY);}catch(Exception ignored){}});
   actionTile(incoming,"✕","דחה",2,()->{try{call.reject(false,null);}catch(Exception ignored){}});
  }else{
   LinearLayout first=horizontal();buttons.addView(first,new LinearLayout.LayoutParams(-1,-2));
   actionTile(first,PhoneSessionService.muted()?"⊘":"♩",
     PhoneSessionService.muted()?"בטל השתקה":"השתק",
     PhoneSessionService.muted()?1:0,
     ()->PhoneSessionService.mute(!PhoneSessionService.muted()));
   actionTile(first,"◖",PhoneSessionService.speaker()?"אפרכסת":"רמקול",
     PhoneSessionService.speaker()?1:0,
     ()->PhoneSessionService.speaker(!PhoneSessionService.speaker()));
   LinearLayout second=horizontal();buttons.addView(second,new LinearLayout.LayoutParams(-1,-2));
   actionTile(second,state==Call.STATE_HOLDING?"▶":"Ⅱ",state==Call.STATE_HOLDING?"המשך":"המתן",
     state==Call.STATE_HOLDING?1:0,()->{
      try{if(call.getState()==Call.STATE_HOLDING)call.unhold();else call.hold();}catch(Exception ignored){}
     });
   actionTile(second,"▦","מקשים",0,()->openDtmf(call));
   endButton(call);
  }
 }
 private void openDtmf(Call call){
  Dialog dialog=new Dialog(this);
  LinearLayout pad=vertical();pad.setPadding(dp(15),dp(11),dp(15),dp(16));
  pad.setBackground(shape(0xFF161616,22));
  pad.addView(label("מקשי שיחה",18,GOLD,true),new LinearLayout.LayoutParams(-1,dp(40)));
  String[] keys={"1","2","3","4","5","6","7","8","9","*","0","#"};
  for(int r=0;r<4;r++){
   LinearLayout row=horizontal();pad.addView(row,new LinearLayout.LayoutParams(-1,dp(compact()?49:57)));
   for(int c=0;c<3;c++){
    String key=keys[r*3+c];TextView tile=label(key,23,Color.WHITE,true);
    tile.setBackground(glass(0));
    LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(compact()?43:51),1);
    p.setMargins(dp(3),dp(2),dp(3),dp(2));row.addView(tile,p);
    tile.setOnClickListener(v->{
     tick(tile);
     try{call.playDtmfTone(key.charAt(0));
      handler.postDelayed(()->{try{call.stopDtmfTone();}catch(Exception ignored){}},140);
     }catch(Exception ignored){}
    });
   }
  }
  TextView close=label("סגור",16,GOLD,true);pad.addView(close,new LinearLayout.LayoutParams(-1,dp(43)));
  close.setOnClickListener(v->{tick(close);dialog.dismiss();});
  dialog.setContentView(pad);dialog.show();
  Window w=dialog.getWindow();
  if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setLayout(-1,-2);w.setGravity(Gravity.BOTTOM);}
 }
 @Override protected void onDestroy(){
  handler.removeCallbacks(timer);PhoneSessionService.unregister(changed);super.onDestroy();
 }
 @Override public void onBackPressed(){moveTaskToBack(true);}
}
