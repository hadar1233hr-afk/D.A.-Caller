package com.dacaller.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.TelecomManager;
import android.telephony.PhoneNumberUtils;

/** One guarded outgoing path shared by lists, contact cards and widgets. */
final class CallLauncher {
 static boolean isCallable(String number){
  if(number==null||number.trim().isEmpty()||number.trim().matches("-[123]"))return false;
  if(!number.trim().matches("[+0-9*#,;N() .\\s-]+"))return false;
  String stripped=PhoneNumberUtils.stripSeparators(number.trim());
  return stripped!=null&&stripped.matches("[+]?[0-9*#][0-9*#,;N]*");
 }
 static boolean place(Activity activity,String number){
  if(!isCallable(number))return false;
  if(activity.checkSelfPermission(Manifest.permission.CALL_PHONE)!=PackageManager.PERMISSION_GRANTED)
   return dialExternally(activity,number);
  Uri tel=Uri.fromParts("tel",number.trim(),null);
  try{
   TelecomManager telecom=activity.getSystemService(TelecomManager.class);
   if(telecom!=null&&activity.getPackageName().equals(telecom.getDefaultDialerPackage())){
    telecom.placeCall(tel,new Bundle());return true;
   }
   activity.startActivity(new Intent(Intent.ACTION_CALL,tel));return true;
  }catch(SecurityException|android.content.ActivityNotFoundException|IllegalStateException failure){
   // Never silently transfer an outgoing call to another dialer after DA CALLER fails.
   CallFailureReporter.record(activity,"outgoing-place-call",failure);
   return false;
  }
 }
 static boolean dialExternally(Activity activity,String number){
  if(!isCallable(number))return false;
  Intent dial=new Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",number.trim(),null));
  // DA CALLER also handles ACTION_DIAL: explicitly select another dialer to avoid a fallback loop.
  for(ResolveInfo candidate:activity.getPackageManager().queryIntentActivities(dial,PackageManager.MATCH_DEFAULT_ONLY)){
   if(candidate.activityInfo==null||activity.getPackageName().equals(candidate.activityInfo.packageName))continue;
   if(!candidate.activityInfo.exported)continue;
   try{
    activity.startActivity(new Intent(dial).setClassName(candidate.activityInfo.packageName,candidate.activityInfo.name));
    return true;
   }catch(SecurityException|android.content.ActivityNotFoundException ignored){}
  }
  return false;
 }
 private CallLauncher(){}
}
