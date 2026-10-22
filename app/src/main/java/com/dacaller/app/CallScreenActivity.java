package com.dacaller.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Call;
import android.telecom.VideoProfile;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;

public final class CallScreenActivity extends Activity {
 private static final int GOLD=0xFFE2C385;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final Runnable changed=()->runOnUiThread(this::refresh);
 private final Runnable clock=new Runnable(){public void run(){updateTimer();handler.postDelayed(this,1000);}};
 private LinearLayout controls,layout;
 private TextView title,status,time;
 private String shownState="";
 private int dp(float v){return (int)(getResources().getDisplayMetrics().density*v+0.5f);}
 private GradientDrawable shape(int color,int radius){GradientDrawable bg=new GradientDrawable();bg.setColor(color);bg.setCornerRadius(dp(radius));return bg;}
 private TextView text(String s,int sp,int color,boolean bold){
  TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(sp);
  t.setGravity(Gravity.CENTER);if(bold)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return t;
 }
 private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setGravity(Gravity.CENTER);return l;}
 private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setGravity(Gravity.CENTER);return l;}
 private void control(String label,String symbol,int bg,Runnable action){
  LinearLayout item=col();
  TextView icon=text(symbol,23,Color.WHITE,true);icon.setBackground(shape(bg,32));
  item.addView(icon,new LinearLayout.LayoutParams(dp(54),dp(54)));
  TextView caption=text(label,11,0xFFD6D6D6,false);
  LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(27));p.topMargin=dp(5);item.addView(caption,p);
  controls.addView(item,new LinearLayout.LayoutParams(0,dp(92),1));
  item.setOnClickListener(v->action.run());
 }
 @Override public void onCreate(Bundle b){
  super.onCreate(b);setShowWhenLocked(true);setTurnScreenOn(true);
  getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  build();
  PhoneSessionService.register(changed);
  handler.post(clock);refresh();
 }
 @Override protected void onNewIntent(android.content.Intent i){super.onNewIntent(i);refresh();}
 private void build(){
  layout=col();layout.setPadding(dp(16),dp(11),dp(16),dp(14));layout.setBackgroundColor(Color.BLACK);
  setContentView(layout);
  TextView brand=text("DA CALLER",13,GOLD,true);brand.setLetterSpacing(0.17f);layout.addView(brand,new LinearLayout.LayoutParams(-1,dp(32)));
  status=text("",14,0xFFBBBBBB,false);layout.addView(status,new LinearLayout.LayoutParams(-1,dp(34)));
  title=text("",Math.min(28,getResources().getDisplayMetrics().widthPixels/dp(13)),Color.WHITE,true);
  title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
  layout.addView(title,new LinearLayout.LayoutParams(-1,dp(58)));
  time=text("",16,GOLD,false);layout.addView(time,new LinearLayout.LayoutParams(-1,dp(36)));
  TextView avatar=text("DA",35,GOLD,true);avatar.setBackground(shape(0xFF181818,72));
  LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(dp(112),dp(112));ap.topMargin=dp(8);ap.bottomMargin=dp(8);layout.addView(avatar,ap);
  layout.addView(new View(this),new LinearLayout.LayoutParams(1,0,1));
  controls=row();layout.addView(controls,new LinearLayout.LayoutParams(-1,dp(185)));
 }
 private void updateTimer(){
  Call call=PhoneSessionService.current;if(call==null||time==null)return;
  if(call.getState()!=Call.STATE_ACTIVE){time.setText("");return;}
  try{
   long start=call.getDetails().getConnectTimeMillis();
   long seconds=start>0?Math.max(0,(System.currentTimeMillis()-start)/1000):0;
   time.setText(String.format(Locale.US,"%02d:%02d",seconds/60,seconds%60));
  }catch(Exception ignored){}
 }
 private void refresh(){
  Call call=PhoneSessionService.current;
  if(call==null||call.getState()==Call.STATE_DISCONNECTED){finish();return;}
  int state=call.getState();
  boolean ringing=state==Call.STATE_RINGING;
  String stateLabel=ringing?"שיחה נכנסת":state==Call.STATE_DIALING?"מחייג...":state==Call.STATE_CONNECTING?"מתחבר...":state==Call.STATE_HOLDING?"שיחה בהמתנה":state==Call.STATE_ACTIVE?"שיחה פעילה":"שיחה";
  status.setText(stateLabel);
  String number="מספר לא ידוע";
  try{if(call.getDetails()!=null&&call.getDetails().getHandle()!=null)
   number=call.getDetails().getHandle().getSchemeSpecificPart();}catch(Exception ignored){}
  title.setText(number);
  updateTimer();
  // Do not rebuild buttons every second; only on call or audio state transition.
  String now=state+"/"+PhoneSessionService.muted()+"/"+PhoneSessionService.speaker();
  if(now.equals(shownState))return;shownState=now;
  controls.removeAllViews();
  if(ringing){
   control("ענה","✓",0xFF18865E,()->{try{call.answer(VideoProfile.STATE_AUDIO_ONLY);}catch(Exception ignored){}});
   control("דחה","✕",0xFFBA3636,()->{try{call.reject(false,null);}catch(Exception ignored){}});
   return;
  }
  control(PhoneSessionService.muted()?"בטל השתקה":"השתק","◉",0xFF323232,()->{PhoneSessionService.mute(!PhoneSessionService.muted());});
  control(PhoneSessionService.speaker()?"אפרכסת":"רמקול","◖",0xFF323232,()->{PhoneSessionService.speaker(!PhoneSessionService.speaker());});
  control(state==Call.STATE_HOLDING?"המשך":"המתן",state==Call.STATE_HOLDING?"▶":"Ⅱ",0xFF323232,()->{
   try{if(call.getState()==Call.STATE_HOLDING)call.unhold();else call.hold();}catch(Exception ignored){}
  });
  LinearLayout extra=row();layout.addView(extra,new LinearLayout.LayoutParams(0,0)); // no extra height: keypad in second controls row
  control("מקשים","▦",0xFF323232,()->openDtmf(call));
  control("סיים","✕",0xFFBA3636,()->{try{call.disconnect();}catch(Exception ignored){}finish();});
 }
 private void openDtmf(Call call){
  Dialog dialog=new Dialog(this);
  LinearLayout list=col();list.setPadding(dp(14),dp(15),dp(14),dp(15));
  list.setBackground(shape(0xFF191919,22));
  TextView heading=text("מקשים במהלך שיחה",17,GOLD,true);list.addView(heading,new LinearLayout.LayoutParams(-1,dp(44)));
  String[] digits={"1","2","3","4","5","6","7","8","9","*","0","#"};
  for(int r=0;r<4;r++){
   LinearLayout strip=row();list.addView(strip,new LinearLayout.LayoutParams(-1,dp(55)));
   for(int c=0;c<3;c++){
    String digit=digits[r*3+c];
    TextView key=text(digit,25,Color.WHITE,true);key.setBackground(shape(0xFF323232,12));
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(49),1);lp.setMargins(dp(3),dp(2),dp(3),dp(2));strip.addView(key,lp);
    key.setOnClickListener(v->{
     try{call.playDtmfTone(digit.charAt(0));handler.postDelayed(()->{try{call.stopDtmfTone();}catch(Exception ignored){}},145);}catch(Exception ignored){}
    });
   }
  }
  TextView close=text("סגור",16,GOLD,true);list.addView(close,new LinearLayout.LayoutParams(-1,dp(42)));close.setOnClickListener(v->dialog.dismiss());
  dialog.setContentView(list);dialog.show();
  Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setLayout(-1,-2);w.setGravity(Gravity.BOTTOM);}
 }
 @Override protected void onDestroy(){
  handler.removeCallbacks(clock);PhoneSessionService.unregister(changed);super.onDestroy();
 }
 @Override public void onBackPressed(){moveTaskToBack(true);}
}
