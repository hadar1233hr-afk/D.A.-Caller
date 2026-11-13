package com.dacaller.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telecom.TelecomManager;

/** Android's protected broadcast is sent exclusively to the current default dialer. */
public final class MissedCallNotificationReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context,Intent intent){
  if(intent==null||!TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION.equals(intent.getAction()))return;
  Context app=context.getApplicationContext();Intent copy=new Intent(intent);PendingResult pending=goAsync();
  MissedCallNotifications.worker.execute(()->{
   try{MissedCallNotifications.receive(app,copy);}
   catch(RuntimeException failure){CallFailureReporter.record(app,"missed-notification-receive",failure);}
   finally{
    // Reminder reads only the provider; missed-call acknowledgement remains
    // exclusively owned by the notification's explicit Open/Clear actions.
    CallbackReminder.maybeRefresh(app);
    if(pending!=null)pending.finish();
   }
  });
 }
}
