package com.dacaller.app;

import android.app.Service;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/** Debug-only real foreground service, with synthetic state and the production notification builder. */
@android.annotation.SuppressLint("ApplySharedPref") // Native tests acknowledge foreground publication before proceeding.
public final class DebugNotificationFixtureService extends Service {
 private static volatile DebugNotificationFixtureService instance;
 private boolean stopping;
 static void refresh(Context context){
  DebugNotificationFixtureService service=instance;
  if(service!=null&&!service.stopping){service.publish();return;}
  Intent start=new Intent(context,DebugNotificationFixtureService.class);
  context.startForegroundService(start);
 }
 static void stop(Context context){
  DebugNotificationFixtureService service=instance;
  if(service!=null){
   service.stopping=true;service.stopForeground(STOP_FOREGROUND_REMOVE);service.stopSelf();
  }else context.stopService(new Intent(context,DebugNotificationFixtureService.class));
 }
 @Override public void onCreate(){super.onCreate();instance=this;}
 @Override public int onStartCommand(Intent intent,int flags,int startId){publish();return START_NOT_STICKY;}
 private void publish(){
  if(Build.VERSION.SDK_INT>=29){
   RoleManager roles=getSystemService(RoleManager.class);
   if(roles==null||!roles.isRoleHeld(RoleManager.ROLE_DIALER))
    throw new IllegalStateException("Native phone-call fixture requires the disposable emulator's dialer role");
  }else throw new IllegalStateException("The native notification fixture requires API 29 or later");
  // startForeground supplies Android's real foreground-service notification flag.
  // A plain notify() of a quiet CallStyle notification is rejected by API 33.
  if(Build.VERSION.SDK_INT>=29)startForeground(NotificationFixtureReceiver.ID,
    NotificationFixtureReceiver.notification(this),ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL);
  else startForeground(NotificationFixtureReceiver.ID,NotificationFixtureReceiver.notification(this));
  NotificationFixtureReceiver.state(this).edit().putBoolean("foreground_started",true)
    .putBoolean("default_dialer_held",true)
    .putInt("foreground_type",Build.VERSION.SDK_INT>=29?getForegroundServiceType():0).commit();
 }
 @Override public void onDestroy(){
  stopping=true;stopForeground(STOP_FOREGROUND_REMOVE);
  if(instance==this)instance=null;super.onDestroy();
 }
 @Override public IBinder onBind(Intent intent){return null;}
}
