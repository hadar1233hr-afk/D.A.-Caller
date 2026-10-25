package com.dacaller.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.content.Context;
import android.os.Build;
import android.telecom.Call;
import android.telecom.CallAudioState;

/** Native call notification presentation shared by real Telecom sessions and rendering tests. */
final class CallNotifications {
 interface Commands {PendingIntent action(String action,int code);}
 static final String INCOMING_CHANNEL="dacaller_incoming_v5",ONGOING_CHANNEL="dacaller_ongoing_v5";
 private static String tr(Context context,String he,String en){return I18n.isEnglish(context)?en:he;}
 static String stateLabel(Context context,int state){
  if(state==Call.STATE_RINGING)return tr(context,"שיחה נכנסת","Incoming call");
  if(state==Call.STATE_DIALING||state==Call.STATE_CONNECTING||state==Call.STATE_SELECT_PHONE_ACCOUNT)
   return tr(context,"שיחה יוצאת","Outgoing call");
  if(state==Call.STATE_HOLDING)return tr(context,"שיחה בהמתנה","Call on hold");
  if(state==Call.STATE_DISCONNECTING)return tr(context,"מסיים שיחה","Ending call");
  return tr(context,"שיחה פעילה","Active call");
 }
 static boolean controllable(int state){
  return state==Call.STATE_ACTIVE||state==Call.STATE_DIALING||state==Call.STATE_CONNECTING||state==Call.STATE_HOLDING;
 }
 /** Zero means Telecom has no supported destination for this explicit action. */
 static int speakerDestination(boolean enable,int supported){
  if(enable)return (supported&CallAudioState.ROUTE_SPEAKER)!=0?CallAudioState.ROUTE_SPEAKER:0;
  int privateRoutes=supported&~CallAudioState.ROUTE_SPEAKER;
  if((privateRoutes&CallAudioState.ROUTE_WIRED_HEADSET)!=0)return CallAudioState.ROUTE_WIRED_HEADSET;
  if((privateRoutes&CallAudioState.ROUTE_EARPIECE)!=0)return CallAudioState.ROUTE_EARPIECE;
  if((privateRoutes&CallAudioState.ROUTE_BLUETOOTH)!=0)return CallAudioState.ROUTE_BLUETOOTH;
  return 0;
 }
 static Notification create(Context context,int state,String name,String number,boolean intrusive,
     int route,int supported,long connectAt,PendingIntent open,Commands commands){
  boolean ringing=state==Call.STATE_RINGING;
  String channel=intrusive?INCOMING_CHANNEL:ONGOING_CHANNEL;
  NotificationManager manager=context.getSystemService(NotificationManager.class);
  if(manager!=null){
   NotificationChannel settings=new NotificationChannel(channel,
    tr(context,intrusive?"שיחה נכנסת":"שיחות פעילות",intrusive?"Incoming calls":"Ongoing calls"),
    intrusive?NotificationManager.IMPORTANCE_HIGH:NotificationManager.IMPORTANCE_LOW);
   // Telecom owns ringing. Notification refreshes must never play a second ringtone.
   settings.setSound(null,null);settings.enableVibration(false);
   settings.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);manager.createNotificationChannel(settings);
  }
  String identity=name==null||name.trim().isEmpty()?number:name;
  if(identity==null||identity.trim().isEmpty())identity=tr(context,"מספר לא מזוהה","Unknown caller");
  String label=stateLabel(context,state);
  String details=label+(number!=null&&!number.isEmpty()&&!number.equals(identity)?" · "+number:"");
  Notification.Builder builder=new Notification.Builder(context,channel)
   .setSmallIcon(R.drawable.ic_stat_call).setContentTitle(identity).setContentText(details).setSubText(label)
   .setContentIntent(open).setVisibility(Notification.VISIBILITY_PUBLIC).setCategory(Notification.CATEGORY_CALL)
   .setOnlyAlertOnce(true).setOngoing(true).setAutoCancel(false)
   .setPriority(intrusive?Notification.PRIORITY_HIGH:Notification.PRIORITY_LOW);
  if(intrusive)builder.setFullScreenIntent(open,true);
  if(state==Call.STATE_ACTIVE&&connectAt>0){builder.setWhen(connectAt).setUsesChronometer(true).setShowWhen(true);}
  else builder.setShowWhen(false);
  if(Build.VERSION.SDK_INT>=31){
   Person caller=new Person.Builder().setName(identity).setImportant(true).build();
   builder.setStyle(ringing?Notification.CallStyle.forIncomingCall(caller,
    commands.action("DECLINE",2001),commands.action("ANSWER",2002)):
    Notification.CallStyle.forOngoingCall(caller,commands.action("HANGUP",2003)));
  }else if(ringing){
   builder.addAction(android.R.drawable.ic_menu_close_clear_cancel,tr(context,"דחה","Decline"),commands.action("DECLINE",2001));
   builder.addAction(android.R.drawable.sym_action_call,tr(context,"ענה","Answer"),commands.action("ANSWER",2002));
  }else builder.addAction(android.R.drawable.ic_menu_close_clear_cancel,tr(context,"סיום שיחה","Hang up"),commands.action("HANGUP",2003));
  boolean speaker=route==CallAudioState.ROUTE_SPEAKER;
  if(controllable(state)&&speakerDestination(!speaker,supported)!=0)
   builder.addAction(R.drawable.ic_control_speaker,
     speaker?tr(context,"כבה רמקול","Speaker off"):tr(context,"הפעל רמקול","Speaker on"),
     commands.action(speaker?"SPEAKER_OFF":"SPEAKER_ON",speaker?2005:2004));
  builder.addAction(R.drawable.ic_phone_line,tr(context,"חזרה לשיחה","Return to call"),open);
  return builder.build();
 }
 private CallNotifications(){}
}
