package com.dacaller.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsetsController;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

/** Non-exported native rendering fixture. It never places or controls a Telecom call. */
public final class CallIslandFixtureActivity extends Activity implements CallIslandController.ManualHost {
 static final int QUERY_ID=0x00c18001,LIST_ID=0x00c18002;
 LinearLayout content;
 EditText query;
 ListView list;
 CallIslandController controller;
 FixtureSession session;
 int originalTopMargin;
 private boolean dark;
 private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
 private String tr(String he,String en){return I18n.isEnglish(this)?en:he;}
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);dark=getSharedPreferences("da_caller",MODE_PRIVATE).getBoolean("dark",false);
  content=new LinearLayout(this);content.setTag("island-fixture-content");content.setOrientation(LinearLayout.VERTICAL);
  content.setLayoutDirection(I18n.isEnglish(this)?View.LAYOUT_DIRECTION_LTR:View.LAYOUT_DIRECTION_RTL);
  content.setBackgroundColor(dark?0xFF151515:0xFFF8F8F7);content.setPadding(dp(12),dp(6),dp(12),0);
  TextView title=new TextView(this);title.setText(tr("אנשי קשר · הדגמה","Contacts · demonstration"));title.setTextSize(18);
  title.setTextColor(dark?Color.WHITE:0xFF1B1B1B);title.setGravity(Gravity.CENTER_VERTICAL);
  content.addView(title,new LinearLayout.LayoutParams(-1,dp(48)));
  query=new EditText(this);query.setId(QUERY_ID);query.setTag("island-fixture-query");query.setSingleLine(true);
  query.setText(tr("בדיקת חיפוש","Search fixture"));query.setTextSize(15);query.setTextColor(dark?Color.WHITE:0xFF222222);
  query.setShowSoftInputOnFocus(false);content.addView(query,new LinearLayout.LayoutParams(-1,dp(52)));
  list=new ListView(this);list.setId(LIST_ID);list.setTag("island-fixture-list");list.setDivider(null);
  list.setAdapter(new BaseAdapter(){
   public int getCount(){return 80;}public Object getItem(int position){return position;}
   public long getItemId(int position){return 700000+position;}public boolean hasStableIds(){return true;}
   public View getView(int position,View convert,ViewGroup parent){
    TextView row=convert instanceof TextView?(TextView)convert:new TextView(CallIslandFixtureActivity.this);
    row.setText(tr("איש קשר לדוגמה ","Example contact ")+String.format(java.util.Locale.ROOT,"%02d",position));
    row.setTextSize(16);row.setGravity(Gravity.CENTER_VERTICAL);row.setTextColor(dark?0xFFE5E5E5:0xFF242424);
    row.setPadding(dp(12),dp(12),dp(12),dp(12));row.setMinHeight(dp(58));return row;
   }
  });
  content.addView(list,new LinearLayout.LayoutParams(-1,0,1));setContentView(content);
  originalTopMargin=((FrameLayout.LayoutParams)content.getLayoutParams()).topMargin;
  session=new FixtureSession();
  if(saved!=null){session.restore(saved);query.setText(saved.getString("fixture_query",query.getText().toString()));
   int position=saved.getInt("fixture_position",0),offset=saved.getInt("fixture_offset",0);list.post(()->list.setSelectionFromTop(position,offset));}
  else session.value=example(getIntent().getIntExtra("state",Call.STATE_ACTIVE),getIntent().getBooleanExtra("no_audio",false));
  controller=new CallIslandController(this,session);applyBars();
 }
 private void applyBars(){
  getWindow().setStatusBarColor(dark?0xFF151515:0xFFF8F8F7);getWindow().setNavigationBarColor(dark?0xFF151515:0xFFF8F8F7);
  if(android.os.Build.VERSION.SDK_INT>=30){
   WindowInsetsController bars=getWindow().getInsetsController();
   if(bars!=null){int mask=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;bars.setSystemBarsAppearance(dark?0:mask,mask);}
  }else getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
 }
 @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)applyBars();}
 @Override protected void onResume(){super.onResume();if(controller!=null)controller.start();}
 @Override protected void onPause(){if(controller!=null)controller.stop();super.onPause();}
 @Override protected void onSaveInstanceState(Bundle state){
  super.onSaveInstanceState(state);session.save(state);state.putString("fixture_query",query.getText().toString());
  state.putInt("fixture_position",list.getFirstVisiblePosition());state.putInt("fixture_offset",list.getChildCount()==0?0:list.getChildAt(0).getTop());
 }
 CallIslandState example(int state,boolean noAudio){
  return new CallIslandState("island-native-fixture",tr("נועה בדיקת אי","Noa Island Test"),"0500099971",state,
   noAudio?0:CallAudioState.ROUTE_EARPIECE,noAudio?0:CallAudioState.ROUTE_EARPIECE|CallAudioState.ROUTE_SPEAKER,
   false,true,!noAudio,System.currentTimeMillis()-11000,-1);
 }
 void setState(int state,boolean noAudio){session.value=example(state,noAudio);PhoneSessionService.notifyViews();}
 final class FixtureSession implements CallIslandSession {
  CallIslandState value;int actions,opens;String lastAction="";
  public CallIslandState current(){return value;}
  public boolean perform(String token,String action){
   CallIslandState old=value;if(old==null||!old.token.equals(token))return false;
   int state=old.state,route=old.route;boolean muted=old.muted;
   switch(action){
    case "MUTE_ON":case "MUTE_OFF":if(!old.canMute||!CallNotifications.controllable(state))return false;muted=action.equals("MUTE_ON");break;
    case "SPEAKER_ON":case "SPEAKER_OFF":
     if(!CallNotifications.controllable(state))return false;
     int target=CallNotifications.speakerDestination(action.equals("SPEAKER_ON"),old.supportedRoutes);
     if(target==0||target==route)return false;route=target;break;
    case "HOLD":if(!old.canHold||state!=Call.STATE_ACTIVE)return false;state=Call.STATE_HOLDING;break;
    case "RESUME":if(!old.canHold||state!=Call.STATE_HOLDING)return false;state=Call.STATE_ACTIVE;break;
    case "ANSWER":if(state!=Call.STATE_RINGING)return false;state=Call.STATE_ACTIVE;break;
    case "DECLINE":if(state!=Call.STATE_RINGING)return false;value=null;break;
    case "END":if(state==Call.STATE_RINGING||state==Call.STATE_DISCONNECTED||state==Call.STATE_DISCONNECTING)return false;value=null;break;
    default:return false;
   }
   if(value!=null)value=new CallIslandState(old.token,old.name,old.number,state,route,old.supportedRoutes,muted,old.canHold,old.canMute,old.connectedAtMillis,old.contactId);
   actions++;lastAction=action;PhoneSessionService.notifyViews();return true;
  }
  public void open(Activity host,String token){
   if(value==null||!value.token.equals(token))return;opens++;
   host.startActivity(new Intent(host,CallScreenActivity.class).putExtra("preview_ui",true));
  }
  void save(Bundle out){
   out.putBoolean("fixture_present",value!=null);out.putInt("fixture_actions",actions);out.putInt("fixture_opens",opens);out.putString("fixture_last",lastAction);
   if(value==null)return;
   out.putInt("fixture_state",value.state);out.putInt("fixture_route",value.route);out.putInt("fixture_supported",value.supportedRoutes);
   out.putBoolean("fixture_muted",value.muted);out.putBoolean("fixture_hold",value.canHold);out.putBoolean("fixture_mute",value.canMute);out.putLong("fixture_connected",value.connectedAtMillis);
  }
  void restore(Bundle in){
   actions=in.getInt("fixture_actions");opens=in.getInt("fixture_opens");lastAction=in.getString("fixture_last","");
   if(in.getBoolean("fixture_present")){
    CallIslandState example=example(in.getInt("fixture_state"),false);
    value=new CallIslandState(example.token,example.name,example.number,in.getInt("fixture_state"),in.getInt("fixture_route"),in.getInt("fixture_supported"),in.getBoolean("fixture_muted"),in.getBoolean("fixture_hold"),in.getBoolean("fixture_mute"),in.getLong("fixture_connected"),-1);
   }
  }
 }
}
