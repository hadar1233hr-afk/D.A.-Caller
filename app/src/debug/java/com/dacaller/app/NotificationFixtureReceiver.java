package com.dacaller.app;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.telecom.Call;
import android.telecom.CallAudioState;

/** Debug-only synthetic session. Native notification presentation uses the production builder. */
@android.annotation.SuppressLint("ApplySharedPref") // Test acknowledgements deliberately complete before the next assertion.
public final class NotificationFixtureReceiver extends BroadcastReceiver {
 static final int ID=9442;
 static final String PREF="da_caller_notification_instrumentation";
 private static final String PREFIX="com.dacaller.app.DEBUG_NOTIFICATION_";
 static SharedPreferences state(Context context){return context.getSharedPreferences(PREF,Context.MODE_PRIVATE);}
 static void post(Context context,int phase,String name,boolean speaker){
  state(context).edit().clear().putInt("phase",phase).putString("name",name).putBoolean("speaker",speaker)
   .putInt("commands",0).commit();
  DebugNotificationFixtureService.refresh(context);
 }
 static Notification notification(Context context){
  SharedPreferences values=state(context);
  String name=values.getString("name","Synthetic caller");
  int phase=values.getInt("phase",Call.STATE_ACTIVE);
  Intent returnIntent=new Intent(context,CallScreenActivity.class)
   .setData(Uri.parse("dacaller-debug-notification:return"))
   .putExtra("preview_ui",true).putExtra("preview_name",name)
   .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
  PendingIntent open=PendingIntent.getActivity(context,ID,returnIntent,
   PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  // Quiet mode keeps the synthetic session from opening a full-screen Activity.
  // Real incoming/full-screen policy is exercised in PhoneSessionService tests.
  return CallNotifications.create(context,phase,name,"050-009-9902",false,
   values.getBoolean("speaker",false)?CallAudioState.ROUTE_SPEAKER:CallAudioState.ROUTE_EARPIECE,
   CallAudioState.ROUTE_SPEAKER|CallAudioState.ROUTE_EARPIECE,System.currentTimeMillis()-11000,open,
   (action,code)->PendingIntent.getBroadcast(context,code,
    new Intent(context,NotificationFixtureReceiver.class).setAction(PREFIX+action)
     .setData(Uri.parse("dacaller-debug-notification:"+action)),
    PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
 }
 @Override public void onReceive(Context context,Intent intent){
  String action=intent==null?null:intent.getAction();
  if(action==null||!action.startsWith(PREFIX))return;
  action=action.substring(PREFIX.length());
  SharedPreferences values=state(context);
  SharedPreferences.Editor edit=values.edit().putString("last_action",action)
    .putInt("commands",values.getInt("commands",0)+1);
  if("SPEAKER_ON".equals(action))edit.putBoolean("speaker",true);
  else if("SPEAKER_OFF".equals(action))edit.putBoolean("speaker",false);
  else if("ANSWER".equals(action))edit.putInt("phase",Call.STATE_ACTIVE);
  else if("HANGUP".equals(action)||"DECLINE".equals(action)){
   edit.putBoolean("ended",true).commit();
   DebugNotificationFixtureService.stop(context);return;
  }else return;
  edit.commit();DebugNotificationFixtureService.refresh(context);
 }
}
