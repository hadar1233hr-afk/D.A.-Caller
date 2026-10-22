package com.dacaller.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.telephony.TelephonyManager;

/**
 * Conservative radio-level VoLTE hint. Shown only during an active call
 * when Android reports LTE as the current VOICE network, not merely LTE data.
 * Hidden if carrier/OEM or permission does not expose this state.
 */
final class VolteStatus {
 static boolean isVoiceOnLte(Context context){
  if(context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED)return false;
  try{
   TelephonyManager manager=(TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
   return manager!=null && manager.getVoiceNetworkType()==TelephonyManager.NETWORK_TYPE_LTE;
  }catch(SecurityException denied){return false;}catch(Exception ignored){return false;}
 }
}
