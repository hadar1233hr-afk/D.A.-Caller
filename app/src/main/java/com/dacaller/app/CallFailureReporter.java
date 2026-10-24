package com.dacaller.app;

import android.content.Context;
import android.util.Log;

/** Privacy-safe local crash breadcrumbs for the default-dialer critical path. */
final class CallFailureReporter {
 private static final String TAG="DA_CALLER_CALL";
 static void record(Context context,String stage,RuntimeException failure){
  try{
   Log.e(TAG,stage,failure);
   String at="";
   StackTraceElement[] trace=failure.getStackTrace();
   if(trace!=null&&trace.length>0)
    at=trace[0].getClassName()+"."+trace[0].getMethodName()+":"+trace[0].getLineNumber();
   // Record only exception type and code location, never a phone number or call details.
   context.getSharedPreferences("da_caller",Context.MODE_PRIVATE).edit()
    .putString("last_call_failure",stage+" / "+failure.getClass().getSimpleName()+" / "+at)
    .apply();
  }catch(RuntimeException ignored){Log.e(TAG,"Failed to record local diagnostic");}
 }
 private CallFailureReporter(){}
}
