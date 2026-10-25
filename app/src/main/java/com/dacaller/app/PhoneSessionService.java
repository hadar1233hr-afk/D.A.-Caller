package com.dacaller.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.InCallService;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public final class PhoneSessionService extends InCallService {
 static volatile PhoneSessionService instance;
 static volatile Call current;
 private static final int NOTIFICATION_ID=1502;
 private static final CopyOnWriteArrayList<Runnable> listeners=new CopyOnWriteArrayList<>();
 private final IdentityHashMap<Call,String> calls=new IdentityHashMap<>();
 private final ArrayList<Call> order=new ArrayList<>();
 private final Handler handler=new Handler(Looper.getMainLooper());
 private boolean screenVisible;
 private PowerManager.WakeLock proximityLock;
 private final Runnable refreshHistory=()->CallHistory.refresh(getApplicationContext(),true,null);
 private final Call.Callback callback=new Call.Callback(){
  @Override public void onStateChanged(Call call,int state){
   if(state==Call.STATE_DISCONNECTED)clear(call);
   else changed();
  }
  @Override public void onDetailsChanged(Call call,Call.Details details){changed();}
 };
 static void register(Runnable cb){listeners.addIfAbsent(cb);}
 static void unregister(Runnable cb){listeners.remove(cb);}
 static void notifyViews(){for(Runnable cb:listeners){try{cb.run();}catch(Exception ignored){}}}
 @Override public void onCreate(){
  super.onCreate();instance=this;
  try{
   PowerManager power=getSystemService(PowerManager.class);
   if(power!=null&&power.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)){
    proximityLock=power.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,"DA_CALLER:proximity");
    proximityLock.setReferenceCounted(false);
   }
  }catch(RuntimeException failure){CallFailureReporter.record(this,"incall-create-proximity",failure);proximityLock=null;}
 }
 @Override public void onDestroy(){
  for(Call call:new ArrayList<>(order))try{call.unregisterCallback(callback);}
   catch(RuntimeException failure){CallFailureReporter.record(this,"incall-destroy",failure);}
  calls.clear();order.clear();handler.removeCallbacksAndMessages(null);releaseProximity();
  if(instance==this){instance=null;current=null;notifyViews();}
  NotificationManager manager=getSystemService(NotificationManager.class);
  if(manager!=null)manager.cancel(NOTIFICATION_ID);
  super.onDestroy();
 }
 @Override public void onCallAdded(Call call){
  super.onCallAdded(call);
  if(call==null||calls.containsKey(call))return;
  calls.put(call,UUID.randomUUID().toString());order.add(call);
  current=call;
  try{call.registerCallback(callback);}
  catch(RuntimeException failure){CallFailureReporter.record(this,"incall-register-callback",failure);}
  try{changed();}
  catch(RuntimeException failure){
   CallFailureReporter.record(this,"incall-call-added",failure);
   current=call;notifyViews();
  }
  try{if(call.getState()!=Call.STATE_RINGING)showCallScreen();}
  catch(RuntimeException failure){CallFailureReporter.record(this,"incall-open-screen",failure);}
 }
 @Override public void onCallRemoved(Call call){
  clear(call);super.onCallRemoved(call);
 }
 @Override public void onBringToForeground(boolean showDialpad){
  super.onBringToForeground(showDialpad);if(current!=null)showCallScreen();
 }
 private void showCallScreen(){
  try{startActivity(new Intent(this,CallScreenActivity.class)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP));}
  catch(RuntimeException failure){CallFailureReporter.record(this,"incall-start-activity",failure);}
 }
 private void changed(){
  try{current=preferredCall();}
  catch(RuntimeException failure){CallFailureReporter.record(this,"incall-select-call",failure);}
  try{updateProximity();}
  catch(RuntimeException failure){CallFailureReporter.record(this,"incall-proximity",failure);safeReleaseProximity();}
  try{updateNotification(current);}
  catch(RuntimeException failure){CallFailureReporter.record(this,"incall-notification",failure);}
  notifyViews();
 }
 static boolean useProximity(int state,int route){
  return (state==Call.STATE_ACTIVE||state==Call.STATE_DIALING||state==Call.STATE_CONNECTING)
    &&route==CallAudioState.ROUTE_EARPIECE;
 }
 private void updateProximity(){
  if(proximityLock==null)return;
  CallAudioState audio=getCallAudioState();
  boolean needed=current!=null&&audio!=null&&useProximity(current.getState(),audio.getRoute());
  if(needed&&!proximityLock.isHeld()){
   // State/route/end/destroy release this lock. Timeout also bounds an abandoned session.
   try{proximityLock.acquire(24*60*60*1000L);}catch(SecurityException ignored){}
  }else if(!needed)releaseProximity();
 }
 private void releaseProximity(){if(proximityLock!=null&&proximityLock.isHeld())proximityLock.release();}
 private void safeReleaseProximity(){try{releaseProximity();}
  catch(RuntimeException failure){CallFailureReporter.record(this,"incall-release-proximity",failure);}}
 private Call preferredCall(){
  Call best=null;int priority=-1;
  for(int i=order.size()-1;i>=0;i--){
   Call candidate=order.get(i);int next=callPriority(candidate.getState());
   if(next>priority){best=candidate;priority=next;}
  }
  return best;
 }
 static int callPriority(int state){
  if(state==Call.STATE_RINGING)return 4;
  if(state==Call.STATE_DIALING||state==Call.STATE_CONNECTING||state==Call.STATE_SELECT_PHONE_ACCOUNT)return 3;
  if(state==Call.STATE_ACTIVE)return 2;
  if(state==Call.STATE_HOLDING)return 1;
  return state==Call.STATE_DISCONNECTED||state==Call.STATE_DISCONNECTING?-1:0;
 }
 @Override public void onCallAudioStateChanged(CallAudioState state){
  super.onCallAudioStateChanged(state);
  try{updateProximity();}
  catch(RuntimeException failure){CallFailureReporter.record(this,"incall-audio-route",failure);safeReleaseProximity();}
  notifyViews();
 }
 @Override public void onMuteStateChanged(boolean muted){super.onMuteStateChanged(muted);notifyViews();}
 @Override public void onCanAddCallChanged(boolean canAddCall){
  super.onCanAddCallChanged(canAddCall);notifyViews();
 }
 static boolean muted(){CallAudioState state=audioState();return state!=null&&state.isMuted();}
 static boolean speaker(){CallAudioState state=audioState();return state!=null&&(state.getRoute()&CallAudioState.ROUTE_SPEAKER)!=0;}
 private static CallAudioState audioState(){
  PhoneSessionService service=instance;return service==null?null:service.getCallAudioState();
 }
 static void mute(boolean value){
  PhoneSessionService service=instance;
  if(service!=null)try{service.setMuted(value);}catch(SecurityException ignored){}
 }
 static int privateRoute(int supported){
  if((supported&CallAudioState.ROUTE_WIRED_HEADSET)!=0)return CallAudioState.ROUTE_WIRED_HEADSET;
  if((supported&CallAudioState.ROUTE_EARPIECE)!=0)return CallAudioState.ROUTE_EARPIECE;
  if((supported&CallAudioState.ROUTE_BLUETOOTH)!=0)return CallAudioState.ROUTE_BLUETOOTH;
  return CallAudioState.ROUTE_SPEAKER;
 }
 static void speaker(boolean value){
  PhoneSessionService service=instance;CallAudioState state=audioState();
  if(service==null||state==null)return;
  try{service.setAudioRoute(value?CallAudioState.ROUTE_SPEAKER:privateRoute(state.getSupportedRouteMask()));}
  catch(SecurityException ignored){}
 }
 static boolean bluetooth(){CallAudioState state=audioState();return state!=null&&(state.getRoute()&CallAudioState.ROUTE_BLUETOOTH)!=0;}
 static boolean bluetoothAvailable(){CallAudioState state=audioState();return state!=null&&(state.getSupportedRouteMask()&CallAudioState.ROUTE_BLUETOOTH)!=0;}
 static boolean switchBluetooth(){
  PhoneSessionService service=instance;CallAudioState state=audioState();
  if(service==null||state==null||!bluetoothAvailable())return false;
  try{
   int supportedWithoutBluetooth=state.getSupportedRouteMask()&~CallAudioState.ROUTE_BLUETOOTH;
   service.setAudioRoute(bluetooth()?privateRoute(supportedWithoutBluetooth):CallAudioState.ROUTE_BLUETOOTH);
   return true;
  }catch(SecurityException e){return false;}
 }
 private PendingIntent command(String action,int code,Call call){
  String token=calls.get(call);
  Intent intent=new Intent(this,CallCommandReceiver.class);
  intent.setAction("com.dacaller.app.CALL_"+action);
  intent.putExtra("call_token",token);
  // Identity in the URI keeps a delayed action for an ended call from targeting its successor.
  intent.setData(android.net.Uri.parse("dacaller-call:"+token));
  return PendingIntent.getBroadcast(this,code,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
 }
 static boolean commandMatches(Call call,String token){
  PhoneSessionService service=instance;
  return service!=null&&call!=null&&token!=null&&token.equals(service.calls.get(call));
 }
 static void setScreenVisible(boolean visible){
  PhoneSessionService service=instance;
  if(service!=null){service.screenVisible=visible;
   try{service.updateNotification(current);}
   catch(RuntimeException failure){CallFailureReporter.record(service,"incall-screen-notification",failure);}
  }
 }
 static boolean intrusiveNotification(int state,boolean visible){return state==Call.STATE_RINGING&&!visible;}
 static void notifyChanged(){PhoneSessionService service=instance;if(service!=null)service.changed();else notifyViews();}
 private void clear(Call call){
  if(!calls.containsKey(call))return;
  try{call.unregisterCallback(callback);}
  catch(RuntimeException failure){CallFailureReporter.record(this,"incall-unregister-callback",failure);}
  calls.remove(call);order.remove(call);
  CallHistory.fetchedAt=0;
  handler.removeCallbacks(refreshHistory);handler.postDelayed(refreshHistory,2500);
  changed();
 }
 private void updateNotification(Call call){
  NotificationManager nm=getSystemService(NotificationManager.class);if(nm==null)return;
  if(call==null||call.getState()==Call.STATE_DISCONNECTED){nm.cancel(NOTIFICATION_ID);return;}
  boolean ringing=call.getState()==Call.STATE_RINGING;
  boolean intrusive=intrusiveNotification(call.getState(),screenVisible);
  String channel=intrusive?"dacaller_incoming_v5":"dacaller_ongoing_v5";
  NotificationChannel ch=new NotificationChannel(channel,
    I18n.text(this,intrusive?"שיחה נכנסת":"שיחה פעילה"),
    intrusive?NotificationManager.IMPORTANCE_HIGH:NotificationManager.IMPORTANCE_LOW);
  // Telecom plays the ringtone, independently of notification permission/channel sound.
  ch.setSound(null,null);ch.enableVibration(false);
  ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);nm.createNotificationChannel(ch);
  Intent intent=new Intent(this,CallScreenActivity.class)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
  PendingIntent open=PendingIntent.getActivity(this,NOTIFICATION_ID,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  String number=I18n.text(this,"מספר לא מזוהה");
  Call.Details details=call.getDetails();
  if(details!=null&&details.getHandle()!=null)number=details.getHandle().getSchemeSpecificPart();
  Notification.Builder b=new Notification.Builder(this,channel);
  b.setSmallIcon(R.drawable.ic_stat_call)
   .setContentTitle(I18n.text(this,ringing?"שיחה נכנסת":"שיחה פעילה")+" · DA CALLER")
   .setContentText(number).setContentIntent(open).setVisibility(Notification.VISIBILITY_PUBLIC)
   .setCategory(Notification.CATEGORY_CALL).setOnlyAlertOnce(true)
   .setPriority(intrusive?Notification.PRIORITY_HIGH:Notification.PRIORITY_LOW).setOngoing(true);
  if(intrusive)b.setFullScreenIntent(open,true);
  if(Build.VERSION.SDK_INT>=31){
   Person caller=new Person.Builder().setName(number.isEmpty()?I18n.text(this,"מספר לא מזוהה"):number).setImportant(true).build();
   b.setStyle(ringing?Notification.CallStyle.forIncomingCall(caller,
      command("DECLINE",2001,call),command("ANSWER",2002,call)):
      Notification.CallStyle.forOngoingCall(caller,command("HANGUP",2003,call)));
  }else if(ringing){
   b.addAction(android.R.drawable.ic_menu_close_clear_cancel,I18n.text(this,"דחה"),command("DECLINE",2001,call));
   b.addAction(android.R.drawable.sym_action_call,I18n.text(this,"ענה"),command("ANSWER",2002,call));
  }else b.addAction(android.R.drawable.ic_menu_close_clear_cancel,I18n.text(this,"סיום שיחה"),command("HANGUP",2003,call));
  try{nm.notify(NOTIFICATION_ID,b.build());}catch(SecurityException ignored){}
 }
}
