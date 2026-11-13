package com.dacaller.app;

import android.app.Application;

/** Install app-window UI and the lifecycle guard for the optional call-only external island. */
public final class DaCallerApplication extends Application {
 @Override public void onCreate(){
  super.onCreate();
  if(SettingsBackup.hasPendingRestore(this)){
   // A pending restore suppresses destructive archive jobs while recovery runs.
   // Never block Telecom startup or turn a storage failure into a dialer crash.
   new Thread(()->{
    try{
     SettingsBackup.recoverPendingRestore(this,id->AppIcons.apply(this,id));
     new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{
      if(!SettingsBackup.hasPendingRestore(this))SettingsBackup.reconcileAfterRecovery(this);
     });
    }
    catch(java.io.IOException incomplete){android.util.Log.e("DA_CALLER","Backup recovery is pending; archive pruning remains paused");}
   },"da-caller-backup-recovery").start();
  }
  CallIslandOverlay.install(this);CallIslandController.install(this);
 }
}
