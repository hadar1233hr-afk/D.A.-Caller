package com.dacaller.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telecom.Call;
import android.telecom.VideoProfile;

public final class CallCommandReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context,Intent intent){
  Call call=PhoneSessionService.current;
  if(call==null||intent==null)return;
  String action=intent.getAction();
  try{
   if("com.dacaller.app.CALL_ANSWER".equals(action)&&call.getState()==Call.STATE_RINGING)call.answer(VideoProfile.STATE_AUDIO_ONLY);
   else if("com.dacaller.app.CALL_DECLINE".equals(action)&&call.getState()==Call.STATE_RINGING)call.reject(false,null);
   else if("com.dacaller.app.CALL_HANGUP".equals(action))call.disconnect();
  }catch(Exception ignored){}
  PhoneSessionService.notifyChanged();
 }
}
