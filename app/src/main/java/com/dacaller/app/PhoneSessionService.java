package com.dacaller.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.InCallService;
import java.util.concurrent.CopyOnWriteArrayList;

public final class PhoneSessionService extends InCallService {
 static volatile PhoneSessionService instance;
 static volatile Call current;
 private static final CopyOnWriteArrayList<Runnable> listeners=new CopyOnWriteArrayList<>();
 private final Call.Callback callback=new Call.Callback(){
  @Override public void onStateChanged(Call call,int state){
   notifyViews();
   if(state==Call.STATE_DISCONNECTED)clear(call);
   else updateNotification(call);
  }
  @Override public void onDetailsChanged(Call call,Call.Details details){
   notifyViews();
   updateNotification(call);
  }
 };
 static void register(Runnable cb){listeners.addIfAbsent(cb);}
 static void unregister(Runnable cb){listeners.remove(cb);}
 static void notifyViews(){for(Runnable cb:listeners){try{cb.run();}catch(Exception ignored){}}}
 @Override public void onCreate(){super.onCreate();instance=this;}
 @Override public void onDestroy(){
  instance=null;current=null;notifyViews();
  super.onDestroy();
 }
 @Override public void onCallAdded(Call call){
  super.onCallAdded(call);
  current=call;
  call.registerCallback(callback);
  updateNotification(call);
  notifyViews();
  // Opening via the full-screen notification is reliable when Android disallows
  // background Activity launches; opening directly is attempted for outgoing calls.
  if(call.getState()!=Call.STATE_RINGING){
   try{
    Intent ui=new Intent(this,CallScreenActivity.class);
    ui.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
    startActivity(ui);
   }catch(Exception ignored){}
  }
 }
 @Override public void onCallRemoved(Call call){
  try{call.unregisterCallback(callback);}catch(Exception ignored){}
  clear(call);
  super.onCallRemoved(call);
 }
 @Override public void onCallAudioStateChanged(CallAudioState state){super.onCallAudioStateChanged(state);notifyViews();}
 @Override public void onMuteStateChanged(boolean muted){super.onMuteStateChanged(muted);notifyViews();}
 static boolean muted(){
  PhoneSessionService service=instance;
  if(service==null)return false;
  CallAudioState state=service.getCallAudioState();
  return state!=null&&state.isMuted();
 }
 static boolean speaker(){
  PhoneSessionService service=instance;
  if(service==null)return false;
  CallAudioState state=service.getCallAudioState();
  return state!=null && (state.getRoute() & CallAudioState.ROUTE_SPEAKER)!=0;
 }
 static void mute(boolean value){
  PhoneSessionService service=instance;
  if(service!=null)try{service.setMuted(value);}catch(Exception ignored){}
 }
 static void speaker(boolean value){
  PhoneSessionService service=instance;
  if(service==null)return;
  try{service.setAudioRoute(value?CallAudioState.ROUTE_SPEAKER:CallAudioState.ROUTE_EARPIECE);}catch(Exception ignored){}
 }
 static boolean bluetooth(){
  PhoneSessionService service=instance;
  if(service==null)return false;
  CallAudioState state=service.getCallAudioState();
  return state!=null&&(state.getRoute()&CallAudioState.ROUTE_BLUETOOTH)!=0;
 }
 static boolean bluetoothAvailable(){
  PhoneSessionService service=instance;
  if(service==null)return false;
  CallAudioState state=service.getCallAudioState();
  return state!=null&&(state.getSupportedRouteMask()&CallAudioState.ROUTE_BLUETOOTH)!=0;
 }
 static boolean switchBluetooth(){
  PhoneSessionService service=instance;
  if(service==null||!bluetoothAvailable())return false;
  try{
   service.setAudioRoute(bluetooth()?CallAudioState.ROUTE_EARPIECE:CallAudioState.ROUTE_BLUETOOTH);
   return true;
  }catch(Exception ignored){return false;}
 }

 private static PendingIntent command(Context ctx,String command,int code){
  Intent intent=new Intent(ctx,CallCommandReceiver.class);
  intent.setAction("com.dacaller.app.CALL_"+command);
  return PendingIntent.getBroadcast(ctx,code,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
 }
 static void notifyChanged(){notifyViews();}
 private void clear(Call call){
  if(current==call)current=null;
  CallHistory.fetchedAt=0;
  new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
   ()->CallHistory.refresh(getApplicationContext(),true,null),2500);
  notifyViews();
  try{((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancel(1502);}catch(Exception ignored){}
 }
 private void updateNotification(Call call){
  if(call==null)return;
  int state=call.getState();
  if(state==Call.STATE_DISCONNECTED)return;
  NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
  boolean ringing=state==Call.STATE_RINGING;
  // A quiet, low-importance ongoing channel prevents a heads-up drawer from
  // repeatedly obscuring an outgoing or already active in-call screen.
  String channel=ringing?"dacaller_incoming_v4":"dacaller_ongoing_v4";
  NotificationChannel ch=new NotificationChannel(channel,
    ringing?"DA CALLER · שיחה נכנסת":"DA CALLER · שיחה פעילה",
    ringing?NotificationManager.IMPORTANCE_HIGH:NotificationManager.IMPORTANCE_LOW);
  ch.setDescription(ringing?"התראה על שיחה נכנסת":"שיחה פעילה בשורת ההתראות בלבד");
  if(!ringing){ch.setSound(null,null);ch.enableVibration(false);}
  ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
  nm.createNotificationChannel(ch);
  Intent intent=new Intent(this,CallScreenActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
  PendingIntent open=PendingIntent.getActivity(this,1502,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  String number="מספר לא ידוע";
  try{
   if(call.getDetails()!=null&&call.getDetails().getHandle()!=null)number=call.getDetails().getHandle().getSchemeSpecificPart();
  }catch(Exception ignored){}
  Notification.Builder b=new Notification.Builder(this,channel);
  b.setSmallIcon(R.drawable.ic_stat_call)
   .setContentTitle(ringing?"שיחה נכנסת · DA CALLER":"שיחה פעילה · DA CALLER")
   .setContentText(number)
   .setContentIntent(open).setVisibility(Notification.VISIBILITY_PUBLIC)
   .setCategory(Notification.CATEGORY_CALL)
   .setOnlyAlertOnce(true)
   .setPriority(ringing?Notification.PRIORITY_HIGH:Notification.PRIORITY_LOW)
   .setOngoing(true);
  if(ringing)b.setFullScreenIntent(open,true);
  else {b.setSound(null);b.setVibrate(null);}
  if(Build.VERSION.SDK_INT>=31){
   try{
    Person caller=new Person.Builder().setName(number).setImportant(true).build();
    if(ringing)b.setStyle(Notification.CallStyle.forIncomingCall(caller,
      command(this,"DECLINE",2001),command(this,"ANSWER",2002)));
    else b.setStyle(Notification.CallStyle.forOngoingCall(caller,command(this,"HANGUP",2003)));
   }catch(Exception ignored){}
  }else{
   if(ringing){
    b.addAction(android.R.drawable.ic_menu_close_clear_cancel,"דחה",command(this,"DECLINE",2001));
    b.addAction(android.R.drawable.sym_action_call,"ענה",command(this,"ANSWER",2002));
   }else b.addAction(android.R.drawable.ic_menu_close_clear_cancel,"סיים",command(this,"HANGUP",2003));
  }
  try{nm.notify(1502,b.build());}catch(Exception ignored){}
 }
}
