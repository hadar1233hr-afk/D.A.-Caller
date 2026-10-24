package com.dacaller.app;

import android.Manifest;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.pm.PackageManager;

/** OS-scheduled, offline archival reconciliation; no persistent foreground daemon. */
public final class ArchiveSyncJob extends JobService {
 private JobParameters active;
 @Override public boolean onStartJob(JobParameters params){
  if(!canRun())return false;
  active=params;
  CallHistory.refresh(this,true,()->{
   // The system can stop a job while its shared background refresh is still running.
   if(active==params){active=null;jobFinished(params,CallHistory.readFailed||CallArchive.lastOperationFailed);}
  });
  return true;
 }
 private boolean canRun(){return CallArchive.enabled(this)&&checkSelfPermission(Manifest.permission.READ_CALL_LOG)==PackageManager.PERMISSION_GRANTED;}
 @Override public boolean onStopJob(JobParameters params){if(active==params)active=null;return canRun();}
}
