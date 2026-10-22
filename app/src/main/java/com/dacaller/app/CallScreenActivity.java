package com.dacaller.app;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.VideoProfile;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.concurrent.TimeUnit;

public class CallScreenActivity extends Activity {
 private final Handler handler=new Handler(Looper.getMainLooper());
 private LinearLayout root,buttons;
 private TextView status,title,timer;
 private boolean muted=false,speaker=false;
 private final Runnable listener=()->runOnUiThread(this::refresh);
 private final Runnable tick=new Runnable(){public void run(){refresh();handler.postDelayed(this,1000);}};
 private int d(float n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
 private GradientDrawable card(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(d(radius));return g;}
 private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setGravity(Gravity.CENTER);return l;}
 private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setGravity(Gravity.CENTER);return l;}
 private TextView text(String t,int size,int color,boolean bold){
  TextView v=new TextView(this);v.setText(t);v.setTextSize(size);v.setTextColor(color);v.setGravity(Gravity.CENTER);
  if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;
 }
 private TextView control(String name,String symbol,int color,Runnable action){
  LinearLayout tile=col();TextView icon=text(symbol,26,Color.WHITE,true);icon.setBackground(card(color,36));tile.addView(icon,new LinearLayout.LayoutParams(d(61),d(61)));
  TextView label=text(name,13,0xFFD1D1D1,false);LinearLayout.LayoutParams t=new LinearLayout.LayoutParams(-1,d(25));t.topMargin=d(6);tile.addView(label,t);
  tile.setOnClickListener(v->action.run());
  buttons.addView(tile,new LinearLayout.LayoutParams(0,d(105),1));return icon;
 }
 @Override public void onCreate(Bundle b){super.onCreate(b);setShowWhenLocked(true);setTurnScreenOn(true);
  getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  root=col();root.setBackgroundColor(Color.BLACK);root.setPadding(d(20),d(20),d(20),d(18));setContentView(root);
  TextView brand=text("DA CALLER",14,0xFFD9B96E,true);root.addView(brand,new LinearLayout.LayoutParams(-1,d(45)));
  status=text("",15,0xFF9DA3AC,false);root.addView(status,new LinearLayout.LayoutParams(-1,d(38)));
  title=text("",32,Color.WHITE,true);root.addView(title,new LinearLayout.LayoutParams(-1,d(65)));
  timer=text("",16,0xFFD9B96E,false);root.addView(timer,new LinearLayout.LayoutParams(-1,d(39)));
  TextView avatar=text("☎",65,0xFFD9B96E,false);avatar.setBackground(card(0xFF171717,120));
  LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(d(138),d(138));ap.topMargin=d(16);ap.bottomMargin=d(16);root.addView(avatar,ap);
  View space=new View(this);root.addView(space,new LinearLayout.LayoutParams(1,0,1));
  buttons=row();root.addView(buttons,new LinearLayout.LayoutParams(-1,-2));
  PhoneSessionService.register(listener);handler.post(tick);refresh();
 }
 private void refresh(){
  Call call=PhoneSessionService.current;
  if(call==null||call.getState()==Call.STATE_DISCONNECTED){finish();return;}
  int state=call.getState();boolean ringing=state==Call.STATE_RINGING;
  status.setText(ringing?"שיחה נכנסת":state==Call.STATE_DIALING?"מחייג...":state==Call.STATE_CONNECTING?"מתחבר...":state==Call.STATE_HOLDING?"בהמתנה":"שיחה פעילה");
  String number="מספר לא מזוהה";
  try{if(call.getDetails()!=null&&call.getDetails().getHandle()!=null)number=call.getDetails().getHandle().getSchemeSpecificPart();}catch(Exception ignored){}
  title.setText(number);timer.setText("");
  try{
   if(state==Call.STATE_ACTIVE&&call.getDetails()!=null){
    long start=call.getDetails().getConnectTimeMillis();
    long seconds=Math.max(0,(System.currentTimeMillis()-start)/1000);
    timer.setText(String.format(java.util.Locale.US,"%02d:%02d",seconds/60,seconds%60));
   }
  }catch(Exception ignored){}
  buttons.removeAllViews();
  if(ringing){
   control("ענה","✓",0xFF137E5D,()->{try{call.answer(VideoProfile.STATE_AUDIO_ONLY);}catch(Exception ignored){}});
   control("דחה","✕",0xFFBF3434,()->{try{call.reject(false,null);}catch(Exception ignored){}});
  }else{
   control(muted?"בטל השתקה":"השתק","◉",0xFF272727,()->{
    muted=!muted;try{
     AudioManager am=(AudioManager)getSystemService(Context.AUDIO_SERVICE);
     am.setMicrophoneMute(muted);
    }catch(Exception ignored){}
    refresh();
   });
   control(speaker?"אפרכסת":"רמקול","◖",0xFF272727,()->{
    speaker=!speaker;
    try{
     AudioManager am=(AudioManager)getSystemService(Context.AUDIO_SERVICE);
     am.setSpeakerphoneOn(speaker);
    }catch(Exception ignored){}
    refresh();
   });
   control("סיים","✕",0xFFBE3131,()->{try{call.disconnect();}catch(Exception ignored){}finish();});
  }
 }
 @Override protected void onDestroy(){handler.removeCallbacks(tick);PhoneSessionService.unregister(listener);super.onDestroy();}
 @Override public void onBackPressed(){moveTaskToBack(true);}
}
