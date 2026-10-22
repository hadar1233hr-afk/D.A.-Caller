package com.dacaller.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.telecom.Call;
import android.telecom.InCallService;
import java.util.concurrent.CopyOnWriteArrayList;

public class PhoneSessionService extends InCallService {
 static volatile Call current;
 private static final CopyOnWriteArrayList<Runnable> listeners=new CopyOnWriteArrayList<>();
 static void register(Runnable cb){listeners.addIfAbsent(cb);}
 static void unregister(Runnable cb){listeners.remove(cb);}
 static void notifyViews(){for(Runnable cb:listeners){try{cb.run();}catch(Exception ignored){}}}
 private final Call.Callback callback=new Call.Callback(){
  @Override public void onStateChanged(Call call,int state){notifyViews();if(state==Call.STATE_DISCONNECTED)clear(call);}
  @Override public void onDetailsChanged(Call call,Call.Details details){notifyViews();}
 };
 @Override public void onCallAdded(Call call){
  super.onCallAdded(call);current=call;call.registerCallback(callback);
  Intent screen=new Intent(this,CallScreenActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
  try{startActivity(screen);}catch(Exception ignored){}
  notifyViews();
  showNotification(call);
 }
 @Override public void onCallRemoved(Call call){
  try{call.unregisterCallback(callback);}catch(Exception ignored){}
  clear(call);super.onCallRemoved(call);
 }
 private void clear(Call call){
  if(current==call)current=null;notifyViews();
  ((NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE)).cancel(1502);
 }
 private void showNotification(Call call){
  NotificationManager nm=(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
  String channel="dacaller_incall";
  if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(channel,"DA CALLER • שיחות",NotificationManager.IMPORTANCE_HIGH));
  Intent i=new Intent(this,CallScreenActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
  PendingIntent pi=PendingIntent.getActivity(this,55,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,channel):new Notification.Builder(this);
  boolean ringing=call.getState()==Call.STATE_RINGING;
  builder.setSmallIcon(R.drawable.ic_launcher).setContentTitle(ringing?"שיחה נכנסת • DA CALLER":"שיחה פעילה • DA CALLER")
    .setContentText("הקש לפתיחת מסך השיחה").setContentIntent(pi).setOngoing(true).setCategory(Notification.CATEGORY_CALL)
    .setPriority(Notification.PRIORITY_HIGH);
  if(ringing)builder.setFullScreenIntent(pi,true);
  try{nm.notify(1502,builder.build());}catch(Exception ignored){}
 }
}
