package com.dacaller.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Not exported: reached only by our immutable, generation-scoped notification PendingIntent. */
public final class MissedCallClearReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context,Intent intent){
  if(intent==null||!MissedCallNotifications.ACTION_CLEAR.equals(intent.getAction()))return;
  Context app=context.getApplicationContext();Intent copy=new Intent(intent);PendingResult pending=goAsync();
  MissedCallNotifications.worker.execute(()->{
   try{MissedCallNotifications.userClear(app,copy);}
   catch(RuntimeException failure){CallFailureReporter.record(app,"missed-notification-user-clear",failure);}
   finally{if(pending!=null)pending.finish();}
  });
 }
}
