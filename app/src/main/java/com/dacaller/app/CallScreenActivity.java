package com.dacaller.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Call;
import android.telecom.VideoProfile;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Locale;

public final class CallScreenActivity extends Activity {
 private final Handler clockHandler=new Handler(Looper.getMainLooper());
 private final Runnable changed=()->runOnUiThread(this::refresh);
 private final Runnable tickTask=new Runnable(){public void run(){
  updateTimer();updateBadge();clockHandler.postDelayed(this,1000);
 }};
 private SharedPreferences prefs;
 private CallThemes theme;
 private String themeKey="",drawnState="";
 private LinearLayout shell,body,buttons;
 private TextView caller,phase,timer,badge,identity;
 private ImageView portrait;
 private Call toneCall;
 private final Runnable stopTone=()->{
  if(toneCall!=null){try{toneCall.stopDtmfTone();}catch(Exception ignored){}toneCall=null;}
 };
 private long shownContactId=-1;
 private int dp(float x){return (int)(getResources().getDisplayMetrics().density*x+0.5f);}
 private boolean compact(){return getResources().getDisplayMetrics().heightPixels/getResources().getDisplayMetrics().density<640;}
 private boolean narrow(){return getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density<340;}
 private static final class GesturePrompt extends TextView {
  GesturePrompt(Context context){super(context);}
  @Override public boolean performClick(){return super.performClick();}
 }
 private TextView text(String s,int size,int color,boolean bold){
  return styledText(new TextView(this),s,size,color,bold);
 }
 private <T extends TextView>T styledText(T v,String s,int size,int color,boolean bold){
  v.setText(s);v.setTextSize(size);v.setTextColor(color);
  v.setIncludeFontPadding(false);v.setGravity(Gravity.CENTER);
  v.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));return v;
 }
 private LinearLayout col(){LinearLayout a=new LinearLayout(this);a.setOrientation(LinearLayout.VERTICAL);a.setGravity(Gravity.CENTER);return a;}
 private LinearLayout row(){LinearLayout a=new LinearLayout(this);a.setGravity(Gravity.CENTER);return a;}
 private String tr(String heb){return I18n.text(this,heb);}
 private void feedback(View view){TouchFeedback.pulse(this,prefs,view);}
 private String currentThemeKey(){
  return prefs.getString("call_theme","graphite")+"/"+prefs.getBoolean("dark",false)+"/"+prefs.getBoolean("amoled",false)+"/"+prefs.getString("language","he");
 }
 @Override public void onCreate(Bundle b){
  super.onCreate(b);prefs=getSharedPreferences("da_caller",MODE_PRIVATE);
  if(Build.VERSION.SDK_INT>=27){setShowWhenLocked(true);setTurnScreenOn(true);}
  else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED|WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
  theme=new CallThemes(this);themeKey=currentThemeKey();
  createShell();PhoneSessionService.register(changed);refresh();
 }
 @Override protected void onNewIntent(Intent intent){
  super.onNewIntent(intent);setIntent(intent);drawnState="";refresh();
 }
 @Override protected void onResume(){
  super.onResume();PhoneSessionService.setScreenVisible(true);
  clockHandler.removeCallbacks(tickTask);clockHandler.post(tickTask);drawnState="";refresh();
 }
 @Override protected void onPause(){
  clockHandler.removeCallbacks(tickTask);clockHandler.removeCallbacks(stopTone);stopTone.run();
  PhoneSessionService.setScreenVisible(false);super.onPause();
 }
 private void createShell(){
  theme=new CallThemes(this);themeKey=currentThemeKey();
  getWindow().setStatusBarColor(theme.background);
  getWindow().setNavigationBarColor(theme.background);
  int lightBars=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
  lightBars|=View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
  getWindow().getDecorView().setSystemUiVisibility(theme.dark?0:lightBars);
  shell=col();shell.setLayoutDirection(I18n.isEnglish(this)?View.LAYOUT_DIRECTION_LTR:View.LAYOUT_DIRECTION_RTL);shell.setPadding(dp(narrow()?11:18),dp(compact()?7:14),dp(narrow()?11:18),dp(9));
  shell.setBackgroundColor(theme.background);
  ScrollView viewport=new ScrollView(this);viewport.setFillViewport(true);
  viewport.setBackgroundColor(theme.background);viewport.addView(shell);
  setContentView(viewport);
  TextView brand=text("DA",12,theme.accent,true);brand.setLetterSpacing(0.2f);
  shell.addView(brand,new LinearLayout.LayoutParams(-1,dp(compact()?24:29)));
  phase=text("",14,theme.muted,false);
  shell.addView(phase,new LinearLayout.LayoutParams(-1,dp(compact()?25:32)));
  caller=text("",compact()?23:29,theme.ink,true);
  caller.setSingleLine(true);caller.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
  shell.addView(caller,new LinearLayout.LayoutParams(-1,dp(compact()?41:52)));
  identity=text("",13,theme.muted,false);
  shell.addView(identity,new LinearLayout.LayoutParams(-1,dp(19)));
  LinearLayout metadata=row();shell.addView(metadata,new LinearLayout.LayoutParams(-1,dp(30)));
  timer=text("",15,theme.accent,false);
  metadata.addView(timer,new LinearLayout.LayoutParams(-2,-1));
  badge=text("VoLTE",10,theme.accent,true);
  badge.setBackground(theme.card(this,0));badge.setPadding(dp(7),0,dp(7),0);
  LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,dp(23));bp.leftMargin=dp(8);
  metadata.addView(badge,bp);badge.setVisibility(View.GONE);
  FrameLayout portraitFrame=new FrameLayout(this);
  portraitFrame.setBackground(theme.card(this,0));
  int avatar=compact()?75:100;
  LinearLayout.LayoutParams avatarP=new LinearLayout.LayoutParams(dp(avatar),dp(avatar));
  avatarP.topMargin=dp(compact()?7:15);avatarP.bottomMargin=dp(compact()?5:12);
  shell.addView(portraitFrame,avatarP);
  portrait=new ImageView(this);portrait.setScaleType(ImageView.ScaleType.CENTER_CROP);
  portrait.setClipToOutline(true);
  portraitFrame.addView(portrait,new FrameLayout.LayoutParams(-1,-1));
  TextView monogram=text("DA",compact()?28:34,theme.accent,true);
  portraitFrame.addView(monogram,new FrameLayout.LayoutParams(-1,-1));
  portrait.setTag(monogram);
  body=col();body.setGravity(Gravity.CENTER);
  shell.addView(body,new LinearLayout.LayoutParams(-1,-2));
  buttons=col();body.addView(buttons,new LinearLayout.LayoutParams(-1,-2));
  shownContactId=-1;lastNumber="";
 }
 private String lastNumber="";
 private void updateCaller(Call call){
  String number=tr("מספר לא מזוהה");
  try{
   if(call.getDetails()!=null&&call.getDetails().getHandle()!=null)
    number=call.getDetails().getHandle().getSchemeSpecificPart();
  }catch(Exception ignored){}
  caller.setText(number);
  if(lastNumber.equals(number))return;
  lastNumber=number;
  final String expected=number;
  ContactPhotos.lookup(this,number,(id,name,picture)->{
   if(isFinishing()||isDestroyed()||!expected.equals(lastNumber))return;
   shownContactId=id;
   identity.setText(name!=null&&!name.equals(expected)?name:"");
   View initials=(View)portrait.getTag();
   if(picture!=null){
    portrait.setImageBitmap(picture);portrait.setVisibility(View.VISIBLE);
    if(initials!=null)initials.setVisibility(View.GONE);
   }else{
    portrait.setImageDrawable(null);
    if(initials!=null)initials.setVisibility(View.VISIBLE);
   }
  });
 }
 private void updateTimer(){
  Call call=PhoneSessionService.current;if(call==null||timer==null)return;
  if(call.getState()!=Call.STATE_ACTIVE){timer.setText("");return;}
  try{
   long connected=call.getDetails().getConnectTimeMillis();
   long seconds=connected>0?Math.max(0,(System.currentTimeMillis()-connected)/1000):0;
   timer.setText(String.format(Locale.US,"%02d:%02d",seconds/60,seconds%60));
  }catch(Exception ignored){}
 }
 private void updateBadge(){
  Call call=PhoneSessionService.current;
  if(badge==null||call==null||call.getState()!=Call.STATE_ACTIVE){if(badge!=null)badge.setVisibility(View.GONE);return;}
  badge.setVisibility(VolteStatus.isVoiceOnLte(this)?View.VISIBLE:View.GONE);
 }
 private void refresh(){
  if(isFinishing()||isDestroyed())return;
  Call call=PhoneSessionService.current;
  if(call==null||call.getState()==Call.STATE_DISCONNECTED){finish();return;}
  if(!currentThemeKey().equals(themeKey)){createShell();drawnState="";}
  int state=call.getState();
  if(state==Call.STATE_RINGING||state==Call.STATE_DIALING||state==Call.STATE_CONNECTING)
   getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  String stage=state==Call.STATE_RINGING?"שיחה נכנסת":
      state==Call.STATE_DIALING?"מחייג…":
      state==Call.STATE_CONNECTING?"מתחבר…":
      state==Call.STATE_HOLDING?"בהמתנה":
      state==Call.STATE_ACTIVE?"שיחה פעילה":"שיחה";
  phase.setText(tr(stage));updateCaller(call);updateTimer();updateBadge();
  String config=System.identityHashCode(call)+"/"+state+"/"+PhoneSessionService.muted()+"/"+PhoneSessionService.speaker()+"/"+
    PhoneSessionService.bluetooth()+"/"+prefs.getString("answer_mode","ios")+"/"+prefs.getInt("call_button_height",64);
  for(String id:InCallControls.IDS)config+="/"+InCallControls.enabled(prefs,id);
  if(config.equals(drawnState))return;
  drawnState=config;
  buttons.removeAllViews();
  if(state==Call.STATE_RINGING)incoming(call);
  else active(call,state);
 }
 private TextView tile(LinearLayout line,int res,String caption,int kind,int height,Runnable click){
  LinearLayout box=col();box.setBackground(theme.card(this,kind));
  box.setElevation(dp(theme.amoled?0:2));
  ImageView icon=new ImageView(this);
  icon.setImageResource(res);icon.setImageTintList(ColorStateList.valueOf(kind==2?Color.WHITE:theme.accent));
  icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
  box.addView(icon,new LinearLayout.LayoutParams(dp(compact()?24:28),dp(compact()?24:28)));
  TextView desc=text(tr(caption),compact()?11:12,kind==2?Color.WHITE:theme.ink,true);
  LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(25));cp.topMargin=dp(5);
  box.addView(desc,cp);
  LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(height),1);
  params.setMargins(dp(4),dp(4),dp(4),dp(4));line.addView(box,params);
  box.setContentDescription(tr(caption));
  TouchFeedback.bind(box,prefs);
  box.setOnClickListener(v->{feedback(box);click.run();});
  return desc;
 }
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
  ArrayList<String> ids=new ArrayList<>();
  for(String id:InCallControls.IDS)if(InCallControls.enabled(prefs,id))ids.add(id);
  int columns=ids.size()<=4?2:3;
  LinearLayout line=null;
  int height=Math.max(52,Math.min(76,prefs.getInt("call_button_height",64)));
  for(int i=0;i<ids.size();i++){
   if(i%columns==0){line=row();buttons.addView(line,new LinearLayout.LayoutParams(-1,-2));}
   addOptional(line,ids.get(i),call,state,height);
  }
  if(ids.size()%columns!=0 && line!=null){
   for(int i=ids.size()%columns;i<columns;i++)
    line.addView(new View(this),new LinearLayout.LayoutParams(0,dp(height),1));
  }
  TextView end=text(tr("סיום שיחה")+"   ✕",16,Color.WHITE,true);
  end.setBackgroundColor(theme.decline);
  end.setBackground(theme.card(this,2));
  LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,dp(compact()?53:62));
  ep.setMargins(dp(4),dp(14),dp(4),dp(4));buttons.addView(end,ep);
  end.setContentDescription(tr("סיום שיחה"));
  TouchFeedback.bind(end,prefs);
  end.setOnClickListener(v->{
   if(call!=PhoneSessionService.current)return;
   feedback(end);try{call.disconnect();}catch(Exception e){callError();}
  });
 }
 private void addOptional(LinearLayout parent,String id,Call call,int state,int height){
  switch(id){
   case "mute":
    tile(parent,PhoneSessionService.muted()?R.drawable.ic_control_mic_off:R.drawable.ic_control_mic,
      PhoneSessionService.muted()?"בטל השתקה":"השתק",PhoneSessionService.muted()?1:0,height,
      ()->PhoneSessionService.mute(!PhoneSessionService.muted()));break;
   case "speaker":
    tile(parent,R.drawable.ic_control_speaker,
      PhoneSessionService.speaker()?"אפרכסת":"רמקול",PhoneSessionService.speaker()?1:0,height,
      ()->PhoneSessionService.speaker(!PhoneSessionService.speaker()));break;
   case "hold":
    tile(parent,state==Call.STATE_HOLDING?R.drawable.ic_control_play:R.drawable.ic_control_pause,
      state==Call.STATE_HOLDING?"המשך":"המתן",state==Call.STATE_HOLDING?1:0,height,()->{
       if(call!=PhoneSessionService.current)return;
       Call.Details details=call.getDetails();
       if(details==null||!details.can(Call.Details.CAPABILITY_HOLD)){
        Toast.makeText(this,tr("המתנה לא זמינה"),Toast.LENGTH_SHORT).show();return;
       }
       try{if(call.getState()==Call.STATE_HOLDING)call.unhold();else call.hold();}
       catch(Exception e){Toast.makeText(this,tr("המתנה לא זמינה"),Toast.LENGTH_SHORT).show();}
      });break;
   case "keypad":
    tile(parent,R.drawable.ic_control_dialpad,"מקשים",0,height,()->openDtmf(call));break;
   case "bluetooth":
    tile(parent,R.drawable.ic_control_bluetooth,"Bluetooth",PhoneSessionService.bluetooth()?1:0,height,()->{
     if(!PhoneSessionService.switchBluetooth())
      Toast.makeText(this,tr("אין אוזניית Bluetooth זמינה"),Toast.LENGTH_SHORT).show();
    });break;
   case "addcall":
    tile(parent,R.drawable.ic_control_add,"הוסף שיחה",0,height,()->{
     Intent dial=new Intent(this,MainActivity.class);dial.setAction(Intent.ACTION_DIAL);
     dial.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);startActivity(dial);
    });break;
   case "contacts":
    tile(parent,R.drawable.ic_contacts,"אנשי קשר",0,height,()->{
     Intent open=new Intent(this,MainActivity.class);open.putExtra("show_contacts",true);
     open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);startActivity(open);
    });break;
   case "notes":
    tile(parent,R.drawable.ic_control_note,"הערה",0,height,()->openNote(call));break;
  }
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
 private void openDtmf(Call call){
  Dialog dialog=new Dialog(this);
  LinearLayout keypad=col();keypad.setPadding(dp(12),dp(14),dp(12),dp(14));keypad.setBackground(theme.card(this,0));
  keypad.addView(text(tr("מקשי שיחה"),17,theme.ink,true),new LinearLayout.LayoutParams(-1,dp(42)));
  String[] keys={"1","2","3","4","5","6","7","8","9","*","0","#"};
  for(int r=0;r<4;r++){
   LinearLayout line=row();keypad.addView(line,new LinearLayout.LayoutParams(-1,dp(compact()?49:57)));
   for(int c=0;c<3;c++){
    String digit=keys[r*3+c];
    TextView btn=text(digit,24,theme.ink,true);btn.setBackground(theme.card(this,0));
    LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(compact()?45:53),1);
    p.setMargins(dp(3),dp(2),dp(3),dp(2));line.addView(btn,p);
    TouchFeedback.bind(btn,prefs);
    btn.setOnClickListener(v->{
     if(call!=PhoneSessionService.current||call.getState()!=Call.STATE_ACTIVE)return;
     feedback(btn);try{
      clockHandler.removeCallbacks(stopTone);stopTone.run();
      toneCall=call;call.playDtmfTone(digit.charAt(0));
      clockHandler.postDelayed(stopTone,145);
     }catch(Exception ignored){}
    });
   }
  }
  TextView close=text(tr("סגור"),16,theme.accent,true);
  keypad.addView(close,new LinearLayout.LayoutParams(-1,dp(43)));
  close.setOnClickListener(v->dialog.dismiss());
  dialog.setOnDismissListener(ignored->{clockHandler.removeCallbacks(stopTone);stopTone.run();});
  dialog.setContentView(keypad);dialog.show();
  Window window=dialog.getWindow();
  if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.setLayout(-1,-2);window.setGravity(Gravity.BOTTOM);}
 }
 @Override protected void onDestroy(){
  clockHandler.removeCallbacksAndMessages(null);stopTone.run();PhoneSessionService.unregister(changed);super.onDestroy();
 }
 @Override public void onBackPressed(){moveTaskToBack(true);}
}
