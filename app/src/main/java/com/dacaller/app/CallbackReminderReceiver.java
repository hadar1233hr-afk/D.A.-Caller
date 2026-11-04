package com.dacaller.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Private alarm endpoint; system boot/time/package broadcasts only restore the user's opt-in schedule. */
public final class CallbackReminderReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context,Intent intent){
  if(intent==null)return;String action=intent.getAction();Context app=context.getApplicationContext();
  if(CallbackReminder.ACTION_ALARM.equals(action)){
   Intent copy=new Intent(intent);PendingResult pending=goAsync();
   CallbackReminder.worker.execute(()->{
    try{CallbackReminder.deliver(app,copy,System.currentTimeMillis());}
    finally{try{CallbackReminder.schedule(app);}finally{if(pending!=null)pending.finish();}}
   });
  }else if(CallbackReminder.ACTION_DISMISS.equals(action))CallbackReminder.dismissed(app,intent);
  else if(Intent.ACTION_BOOT_COMPLETED.equals(action)||Intent.ACTION_TIME_CHANGED.equals(action)
    ||Intent.ACTION_TIMEZONE_CHANGED.equals(action)||Intent.ACTION_MY_PACKAGE_REPLACED.equals(action))CallbackReminder.schedule(app);
 }
}
