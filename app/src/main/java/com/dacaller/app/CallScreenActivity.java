package com.dacaller.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Call;
import android.telecom.VideoProfile;
import android.text.InputType;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Locale;

public final class CallScreenActivity extends Activity {
 private static final int GOLD=0xFFE2C68D;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final Runnable changed=()->runOnUiThread(this::refresh);
 private final Runnable timer=new Runnable(){
  @Override public void run(){updateTimer();updateNetworkBadge();handler.postDelayed(this,1500);}
 };
 private LinearLayout root,buttons;
 private TextView number,callStatus,time,networkBadge;
 private SharedPreferences prefs;
 private String lastState="";
 private int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
 private boolean compact(){return getResources().getDisplayMetrics().heightPixels/getResources().getDisplayMetrics().density<630;}
 private boolean narrow(){return getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density<330;}
 private int buttonHeight(){
  int h=prefs.getInt("call_button_height",68);
  return Math.max(54,Math.min(84,compact()?Math.min(h,72):h));
 }
 private GradientDrawable shape(int color,int radius){
  GradientDrawable bg=new GradientDrawable();bg.setColor(color);bg.setCornerRadius(dp(radius));return bg;
 }
 private GradientDrawable glass(int type){
  GradientDrawable surface=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
   type==1?new int[]{0xFF564631,0xFF252019}:
   type==2?new int[]{0xFFBA4444,0xFF922B2B}:
   new int[]{0xFF303030,0xFF1C1C1C});
  surface.setCornerRadius(dp(15));
  surface.setStroke(dp(1),type==1?0xFF8F7955:type==2?0xFFDB6262:0xFF474747);
  return surface;
 }
 private TextView label(String content,int size,int color,boolean strong){
  TextView view=new TextView(this);view.setText(content);view.setTextSize(size);
  view.setTextColor(color);view.setGravity(Gravity.CENTER);view.setIncludeFontPadding(false);
  view.setTypeface(Typeface.create(strong?"sans-serif-medium":"sans-serif",Typeface.NORMAL));
  return view;
 }
 private LinearLayout vertical(){
  LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);
  l.setGravity(Gravity.CENTER);return l;
 }
 private LinearLayout horizontal(){
  LinearLayout l=new LinearLayout(this);l.setGravity(Gravity.CENTER);return l;
 }
 private void tick(View view){
  if(view!=null&&prefs.getBoolean("haptics",true))
   view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
 }
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);prefs=getSharedPreferences("da_caller",MODE_PRIVATE);
  setShowWhenLocked(true);setTurnScreenOn(true);
  getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  getWindow().setStatusBarColor(0xFF070707);getWindow().setNavigationBarColor(0xFF070707);
  build();PhoneSessionService.register(changed);handler.post(timer);refresh();
 }
 @Override protected void onResume(){super.onResume();lastState="";refresh();}
 @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);lastState="";refresh();}
 private void build(){
  root=vertical();root.setBackgroundColor(0xFF090909);
  root.setPadding(dp(narrow()?9:16),dp(compact()?6:13),dp(narrow()?9:16),dp(10));
  setContentView(root);
  TextView brand=label("DA",12,GOLD,true);
  root.addView(brand,new LinearLayout.LayoutParams(-1,dp(compact()?22:28)));
  callStatus=label("",14,0xFFCACACA,false);
  root.addView(callStatus,new LinearLayout.LayoutParams(-1,dp(compact()?27:33)));
  number=label("",compact()?24:29,Color.WHITE,true);
  number.setSingleLine(true);number.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
  root.addView(number,new LinearLayout.LayoutParams(-1,dp(compact()?43:55)));
  LinearLayout meta=horizontal();root.addView(meta,new LinearLayout.LayoutParams(-1,dp(28)));
  time=label("",14,GOLD,false);meta.addView(time,new LinearLayout.LayoutParams(-2,-1));
  networkBadge=label("VoLTE",10,GOLD,true);networkBadge.setBackground(shape(0xFF262015,9));
  networkBadge.setPadding(dp(8),0,dp(8),0);
  LinearLayout.LayoutParams badge=new LinearLayout.LayoutParams(-2,dp(23));badge.leftMargin=dp(7);
  meta.addView(networkBadge,badge);networkBadge.setVisibility(View.GONE);
  TextView avatar=label("☎",compact()?35:42,GOLD,true);avatar.setBackground(glass(1));
  int dimension=compact()?68:92;
  LinearLayout.LayoutParams avatarParams=new LinearLayout.LayoutParams(dp(dimension),dp(dimension));
  avatarParams.topMargin=dp(compact()?5:12);avatarParams.bottomMargin=dp(compact()?4:11);
  root.addView(avatar,avatarParams);
  root.addView(new View(this),new LinearLayout.LayoutParams(1,0,1));
  buttons=vertical();
  int widthDp=(int)(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density);
  root.addView(buttons,new LinearLayout.LayoutParams(dp(Math.max(1,Math.min(widthDp-24,330))),-2));
  root.addView(new View(this),new LinearLayout.LayoutParams(1,0,1));
 }
 private void actionTile(LinearLayout row,String symbol,String title,int style,int height,Runnable action){
  LinearLayout tile=vertical();
  tile.setBackground(glass(style));
  tile.setElevation(dp(1));
  int iconHeight=Math.max(27,height-25);
  TextView glyph=label(symbol,compact()?21:24,style==2?Color.WHITE:GOLD,true);
  tile.addView(glyph,new LinearLayout.LayoutParams(-1,dp(iconHeight)));
  TextView caption=label(title,compact()?10:12,0xFFE7E7E7,true);
  tile.addView(caption,new LinearLayout.LayoutParams(-1,dp(22)));
  LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(height),1);
  params.setMargins(dp(3),dp(4),dp(3),dp(4));row.addView(tile,params);
  tile.setOnClickListener(v->{tick(tile);action.run();});
 }
 private void safeEnd(Call call){
  TextView end=label("✕     סיום שיחה",compact()?15:17,Color.WHITE,true);
  end.setBackground(glass(2));
  LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(compact()?50:55));
  params.setMargins(dp(4),dp(9),dp(4),dp(4));
  buttons.addView(end,params);
  end.setOnClickListener(v->{tick(end);try{call.disconnect();}catch(Exception ignored){}finish();});
 }
 private void updateTimer(){
  Call call=PhoneSessionService.current;
  if(call==null||time==null)return;
  if(call.getState()!=Call.STATE_ACTIVE){time.setText("");return;}
  try{
   long connected=call.getDetails().getConnectTimeMillis();
   long secs=connected>0?Math.max(0,(System.currentTimeMillis()-connected)/1000):0;
   time.setText(String.format(Locale.US,"%02d:%02d",secs/60,secs%60));
  }catch(Exception ignored){}
 }
 private void updateNetworkBadge(){
  Call call=PhoneSessionService.current;
  if(networkBadge==null||call==null||call.getState()!=Call.STATE_ACTIVE){
   if(networkBadge!=null)networkBadge.setVisibility(View.GONE);return;
  }
  networkBadge.setVisibility(VolteStatus.isVoiceOnLte(this)?View.VISIBLE:View.GONE);
 }
 private void refresh(){
  Call call=PhoneSessionService.current;
  if(call==null||call.getState()==Call.STATE_DISCONNECTED){finish();return;}
  int state=call.getState();
  String phase=state==Call.STATE_RINGING?"שיחה נכנסת":
   state==Call.STATE_DIALING?"מחייג…":state==Call.STATE_CONNECTING?"מתחבר…":
   state==Call.STATE_HOLDING?"בהמתנה":state==Call.STATE_ACTIVE?"שיחה פעילה":"שיחה";
  callStatus.setText(phase);
  String caller="מספר לא מזוהה";
  try{
   if(call.getDetails()!=null&&call.getDetails().getHandle()!=null)
    caller=call.getDetails().getHandle().getSchemeSpecificPart();
  }catch(Exception ignored){}
  number.setText(caller);updateTimer();updateNetworkBadge();
  String configuration=state+"/"+PhoneSessionService.muted()+"/"+
   PhoneSessionService.speaker()+"/"+PhoneSessionService.bluetooth();
  if(configuration.equals(lastState))return;
  lastState=configuration;buttons.removeAllViews();
  if(state==Call.STATE_RINGING){
   LinearLayout incoming=horizontal();
   buttons.addView(incoming,new LinearLayout.LayoutParams(-1,-2));
   actionTile(incoming,"✓","ענה",1,buttonHeight()+7,()->{
    try{call.answer(VideoProfile.STATE_AUDIO_ONLY);}catch(Exception ignored){}
   });
   actionTile(incoming,"✕","דחה",2,buttonHeight()+7,()->{
    try{call.reject(false,null);}catch(Exception ignored){}
   });
   return;
  }
  // In-call preferences only affect optional controls.
  // End call and incoming-call answer/decline remain visible for safety.
  ArrayList<String> active=new ArrayList<>();
  for(String id:InCallControls.IDS)if(InCallControls.enabled(prefs,id))active.add(id);
  // Four or fewer actions use two balanced columns; larger sets use three.
  int columns=active.size()<=4?2:3;
  LinearLayout row=null;
  for(int i=0;i<active.size();i++){
   if(i%columns==0){
    row=horizontal();buttons.addView(row,new LinearLayout.LayoutParams(-1,-2));
   }
   renderControl(row,active.get(i),call,state);
  }
  int remainder=active.size()%columns;
  if(row!=null&&remainder!=0){
   for(int filler=remainder;filler<columns;filler++){
    row.addView(new View(this),new LinearLayout.LayoutParams(0,dp(buttonHeight()),1));
   }
  }
  safeEnd(call);
 }
 private void renderControl(LinearLayout row,String id,Call call,int state){
  switch(id){
   case "mute":
    actionTile(row,PhoneSessionService.muted()?"⊘":"♩",
     PhoneSessionService.muted()?"בטל השתקה":"השתק",
     PhoneSessionService.muted()?1:0,buttonHeight(),
     ()->PhoneSessionService.mute(!PhoneSessionService.muted()));
    break;
   case "speaker":
    actionTile(row,"◖",PhoneSessionService.speaker()?"אפרכסת":"רמקול",
     PhoneSessionService.speaker()?1:0,buttonHeight(),
     ()->PhoneSessionService.speaker(!PhoneSessionService.speaker()));
    break;
   case "keypad":
    actionTile(row,"▦","מקשים",0,buttonHeight(),()->openDtmf(call));break;
   case "hold":
    actionTile(row,state==Call.STATE_HOLDING?"▶":"Ⅱ",
     state==Call.STATE_HOLDING?"המשך":"המתן",
     state==Call.STATE_HOLDING?1:0,buttonHeight(),()->{
      try{
       if(call.getState()==Call.STATE_HOLDING)call.unhold();else call.hold();
      }catch(Exception e){Toast.makeText(this,"המתנה אינה נתמכת בשיחה זו",Toast.LENGTH_SHORT).show();}
     });
    break;
   case "bluetooth":
    actionTile(row,"ᛒ",PhoneSessionService.bluetooth()?"לאפרכסת":"Bluetooth",
     PhoneSessionService.bluetooth()?1:0,buttonHeight(),()->{
      if(!PhoneSessionService.switchBluetooth())
       Toast.makeText(this,"לא נמצאה אוזניית Bluetooth זמינה לשיחה",Toast.LENGTH_SHORT).show();
     });
    break;
   case "addcall":
    actionTile(row,"＋","הוסף שיחה",0,buttonHeight(),()->{
     try{
      Intent dial=new Intent(this,MainActivity.class);
      dial.setAction(Intent.ACTION_DIAL);
      dial.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
      startActivity(dial);
     }catch(Exception e){Toast.makeText(this,"לא ניתן להוסיף שיחה כעת",Toast.LENGTH_SHORT).show();}
    });break;
   case "contacts":
    actionTile(row,"☷","אנשי קשר",0,buttonHeight(),()->{
     Intent contacts=new Intent(this,MainActivity.class);
     contacts.putExtra("show_contacts",true);
     contacts.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
     startActivity(contacts);
    });break;
   case "notes":
    actionTile(row,"✎","הערה",0,buttonHeight(),()->showNote(call));break;
   default:break;
  }
 }
 private void showNote(Call call){
  String phone="";
  try{if(call.getDetails()!=null&&call.getDetails().getHandle()!=null)
   phone=call.getDetails().getHandle().getSchemeSpecificPart();}catch(Exception ignored){}
  String id="note_"+(phone.isEmpty()?"unknown":Contacts.canonical(phone));
  Dialog dialog=new Dialog(this);
  LinearLayout body=vertical();body.setPadding(dp(15),dp(15),dp(15),dp(15));
  body.setBackground(shape(0xFF202020,19));
  body.addView(label("הערה לשיחה",17,GOLD,true),new LinearLayout.LayoutParams(-1,dp(42)));
  EditText note=new EditText(this);
  note.setTextColor(Color.WHITE);note.setHintTextColor(0xFF999999);
  note.setHint("כתוב הערה קצרה…");
  note.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
  note.setMinLines(3);note.setMaxLines(5);
  note.setText(prefs.getString(id,""));
  body.addView(note,new LinearLayout.LayoutParams(-1,dp(130)));
  TextView save=label("שמירה",15,GOLD,true);
  body.addView(save,new LinearLayout.LayoutParams(-1,dp(48)));
  save.setOnClickListener(v->{
   tick(save);
   prefs.edit().putString(id,note.getText().toString()).apply();
   dialog.dismiss();
  });
  dialog.setContentView(body);dialog.show();
  Window window=dialog.getWindow();
  if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);
   window.setLayout(-1,-2);window.setGravity(Gravity.CENTER);}
 }
 private void openDtmf(Call call){
  Dialog dialog=new Dialog(this);
  LinearLayout pad=vertical();
  pad.setPadding(dp(14),dp(10),dp(14),dp(14));
  pad.setBackground(shape(0xFF171717,20));
  pad.addView(label("מקשי שיחה",17,GOLD,true),new LinearLayout.LayoutParams(-1,dp(38)));
  String[] digits={"1","2","3","4","5","6","7","8","9","*","0","#"};
  for(int i=0;i<4;i++){
   LinearLayout line=horizontal();pad.addView(line,new LinearLayout.LayoutParams(-1,dp(compact()?49:55)));
   for(int j=0;j<3;j++){
    String digit=digits[i*3+j];
    TextView key=label(digit,24,Color.WHITE,true);key.setBackground(glass(0));
    LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(compact()?44:50),1);
    params.setMargins(dp(3),dp(2),dp(3),dp(2));
    line.addView(key,params);
    key.setOnClickListener(v->{
     tick(key);
     try{
      call.playDtmfTone(digit.charAt(0));
      handler.postDelayed(()->{try{call.stopDtmfTone();}catch(Exception ignored){}},140);
     }catch(Exception ignored){}
    });
   }
  }
  TextView close=label("סגור",16,GOLD,true);
  pad.addView(close,new LinearLayout.LayoutParams(-1,dp(42)));
  close.setOnClickListener(v->{tick(close);dialog.dismiss();});
  dialog.setContentView(pad);dialog.show();
  Window window=dialog.getWindow();
  if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);
   window.setLayout(-1,-2);window.setGravity(Gravity.BOTTOM);}
 }
 @Override protected void onDestroy(){
  handler.removeCallbacks(timer);PhoneSessionService.unregister(changed);
  super.onDestroy();
 }
 @Override public void onBackPressed(){moveTaskToBack(true);}
}
