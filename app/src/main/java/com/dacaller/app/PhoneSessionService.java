package com.dacaller.app;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.InCallService;
import android.telecom.TelecomManager;
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
 private final IdentityHashMap<Call,CallerIdentity> identities=new IdentityHashMap<>();
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
  calls.clear();order.clear();identities.clear();handler.removeCallbacksAndMessages(null);safeReleaseProximity();
  if(instance==this){instance=null;current=null;notifyViews();}
  removeCallNotification();
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
  try{
   if(call.getState()!=Call.STATE_RINGING||(!screenVisible&&!incomingNotificationAvailable()))showCallScreen();
  }
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
 private boolean incomingNotificationAvailable(){
  try{
   NotificationManager manager=getSystemService(NotificationManager.class);
   if(manager==null||!manager.areNotificationsEnabled())return false;
   android.app.NotificationChannel channel=manager.getNotificationChannel(CallNotifications.INCOMING_CHANNEL);
   return channel==null||channel.getImportance()!=NotificationManager.IMPORTANCE_NONE;
  }catch(RuntimeException failure){
   CallFailureReporter.record(this,"incall-notification-availability",failure);return false;
  }
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
  try{updateNotification(current);}
  catch(RuntimeException failure){CallFailureReporter.record(this,"incall-audio-notification",failure);}
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
 /** Explicit notification actions never invert a later route or act on a replacement call. */
 static boolean notificationSpeaker(Call expected,boolean enable){
  PhoneSessionService service=instance;
  if(service==null||current!=expected||expected==null||!CallNotifications.controllable(expected.getState()))return false;
  CallAudioState state;
  try{state=service.getCallAudioState();}catch(RuntimeException unavailable){return false;}
  if(state==null)return false;
  if(!enable&&state.getRoute()!=CallAudioState.ROUTE_SPEAKER)return false;
  int target=CallNotifications.speakerDestination(enable,state.getSupportedRouteMask());
  if(target==0||state.getRoute()==target)return false;
  // Audio callbacks, rather than an optimistic local flag, acknowledge the change.
  return CallAudioRoutes.select(target);
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
  calls.remove(call);order.remove(call);identities.remove(call);
  CallHistory.fetchedAt=0;
  handler.removeCallbacks(refreshHistory);handler.postDelayed(refreshHistory,2500);
  changed();
 }
 private void updateNotification(Call call){
  if(call==null||call.getState()==Call.STATE_DISCONNECTED){removeCallNotification();return;}
  boolean intrusive=intrusiveNotification(call.getState(),screenVisible);
  Intent intent=new Intent(this,CallScreenActivity.class)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
  intent.setData(android.net.Uri.parse("dacaller-call-screen:"+calls.get(call)));
  PendingIntent open=PendingIntent.getActivity(this,NOTIFICATION_ID,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  CallerIdentity caller=callerIdentity(call);
  CallAudioState audio=getCallAudioState();Call.Details details=call.getDetails();
  long connected=details==null?0:details.getConnectTimeMillis();
  Notification notification=CallNotifications.create(this,call.getState(),caller.name(),caller.number,intrusive,
    audio==null?0:audio.getRoute(),audio==null?0:audio.getSupportedRouteMask(),connected,open,
    (action,code)->command(action,code,call));
  try{
   // Android 13 accepts a quiet CallStyle only for an actual foreground service.
   // Telecom keeps this service bound; the phone-call foreground lifecycle lasts
   // exactly as long as at least one tracked call, including a waiting call.
   if(Build.VERSION.SDK_INT>=29)startForeground(NOTIFICATION_ID,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL);
   else startForeground(NOTIFICATION_ID,notification);
  }catch(RuntimeException failure){
   CallFailureReporter.record(this,"incall-start-foreground",failure);
   // A ringing fullscreen notification remains a permitted fallback. Quiet
   // ongoing notifications must never fake foreground flags or add a fullscreen
   // intent merely to evade the platform's CallStyle requirement.
   if(notification.fullScreenIntent!=null)try{
    NotificationManager manager=getSystemService(NotificationManager.class);
    if(manager!=null)manager.notify(NOTIFICATION_ID,notification);
   }catch(RuntimeException fallback){CallFailureReporter.record(this,"incall-ringing-notification",fallback);}
  }
 }
 private void removeCallNotification(){
  try{stopForeground(STOP_FOREGROUND_REMOVE);}
  catch(RuntimeException failure){CallFailureReporter.record(this,"incall-stop-foreground",failure);}
  try{
   NotificationManager manager=getSystemService(NotificationManager.class);
   if(manager!=null)manager.cancel(NOTIFICATION_ID);
  }catch(RuntimeException failure){CallFailureReporter.record(this,"incall-cancel-notification",failure);}
 }
 private static final class CallerIdentity {
  final String key,number,fallback;final boolean contactsAllowed;
  String contactName;boolean requested;
  CallerIdentity(String key,String number,String fallback,boolean contactsAllowed){
   this.key=key;this.number=number;this.fallback=fallback;this.contactsAllowed=contactsAllowed;
  }
  String name(){return contactName==null?fallback:contactName;}
 }
 private String text(String he,String en){return I18n.isEnglish(this)?en:he;}
 private CallerIdentity callerIdentity(Call call){
  Call.Details details=call.getDetails();
  boolean allowed=details!=null&&details.getHandlePresentation()==TelecomManager.PRESENTATION_ALLOWED;
  String raw=allowed&&details.getHandle()!=null?details.getHandle().getSchemeSpecificPart():"";
  if(!CallLauncher.isCallable(raw))raw="";
  String fallback=raw.isEmpty()?text(details!=null&&details.getHandlePresentation()==TelecomManager.PRESENTATION_RESTRICTED?
    "מספר חסוי":"מספר לא מזוהה",details!=null&&details.getHandlePresentation()==TelecomManager.PRESENTATION_RESTRICTED?
    "Private number":"Unknown caller"):raw;
  boolean contacts=checkSelfPermission(Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED;
  if(allowed){
   String telecomName=Build.VERSION.SDK_INT>=30&&contacts?details.getContactDisplayName():null;
   if(telecomName==null||telecomName.trim().isEmpty())telecomName=
    details.getCallerDisplayNamePresentation()==TelecomManager.PRESENTATION_ALLOWED?details.getCallerDisplayName():null;
   if(telecomName!=null&&!telecomName.trim().isEmpty())fallback=telecomName.trim();
  }
  String key=raw+"/"+fallback+"/"+contacts;
  CallerIdentity identity=identities.get(call);
  if(identity==null||!identity.key.equals(key)){
   identity=new CallerIdentity(key,raw,fallback,contacts);identities.put(call,identity);
  }
  if(contacts&&!raw.isEmpty()&&identity.contactName==null&&!identity.requested){
   identity.requested=true;CallerIdentity expected=identity;String number=raw;
   ContactPhotos.lookup(this,number,(id,name,image)->{
    if(instance!=this||!calls.containsKey(call)||identities.get(call)!=expected||
      checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED)return;
    if(id>0&&name!=null&&!name.trim().isEmpty()&&!name.equals(number))expected.contactName=name.trim();
    if(current==call)try{updateNotification(call);}
     catch(RuntimeException failure){CallFailureReporter.record(this,"incall-contact-notification",failure);}
   });
  }
  return identity;
 }
}
