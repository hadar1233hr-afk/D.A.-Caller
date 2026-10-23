package com.dacaller.app;

import android.Manifest;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.pm.PackageManager;

/** OS-scheduled, offline archival reconciliation; no persistent foreground daemon. */
public final class ArchiveSyncJob extends JobService {
 @Override public boolean onStartJob(JobParameters params){
  if(!CallArchive.enabled(this)||checkSelfPermission(Manifest.permission.READ_CALL_LOG)!=PackageManager.PERMISSION_GRANTED)return false;
  CallHistory.refresh(this,true,()->jobFinished(params,false));
  return true;
 }
 @Override public boolean onStopJob(JobParameters params){return true;}
}
