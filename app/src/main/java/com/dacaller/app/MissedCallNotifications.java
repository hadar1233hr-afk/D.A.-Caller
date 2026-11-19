package com.dacaller.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.telecom.TelecomManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;

/** Owns only our missed-call notification; Telecom remains the authority on what was missed. */
final class MissedCallNotifications {
 static final String CHANNEL="dacaller_missed_v1",PREFERENCES="da_caller_missed";
 static final int ID=1503;
 static final String ACTION_OPEN="com.dacaller.app.MISSED_OPEN",ACTION_CLEAR="com.dacaller.app.MISSED_CLEAR";
 static final String EXTRA_COUNT="com.dacaller.app.MISSED_COUNT",EXTRA_PLATFORM_COUNT="com.dacaller.app.MISSED_PLATFORM_COUNT";
 static final ExecutorService worker=Executors.newSingleThreadExecutor();
 private static final String ACKNOWLEDGED="acknowledged_ids";
 private static String tr(Context context,String he,String en){return I18n.isEnglish(context)?en:he;}
 private static SharedPreferences prefs(Context context){return context.getSharedPreferences(PREFERENCES,Context.MODE_PRIVATE);}
 private static boolean permitted(Context context,String permission){return context.checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED;}
 static boolean isDefaultDialer(Context context){
  try{TelecomManager manager=context.getSystemService(TelecomManager.class);
   return manager!=null&&context.getPackageName().equals(manager.getDefaultDialerPackage());
  }catch(RuntimeException unavailable){return false;}
 }
 /** Direct Activity PendingIntent avoids Android12 notification-trampoline restrictions. */
 static boolean handleOpenIntent(Context context,Intent intent){
  if(intent==null||!ACTION_OPEN.equals(intent.getAction()))return false;
  Context app=context.getApplicationContext();Intent copy=new Intent(intent);
  worker.execute(()->userClear(app,copy));return true;
 }
 static void receive(Context context,Intent intent){
  if(intent==null||!TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION.equals(intent.getAction())
    ||!intent.hasExtra(TelecomManager.EXTRA_NOTIFICATION_COUNT))return;
  if(!isDefaultDialer(context)){cancelOwn(context);return;}
  final int requested;
  try{requested=intent.getIntExtra(TelecomManager.EXTRA_NOTIFICATION_COUNT,-1);}catch(RuntimeException invalid){return;}
  if(requested<0)return;
  SharedPreferences state=prefs(context);
  state.edit().putInt("platform_events",state.getInt("platform_events",0)+1)
   .putInt("last_platform_count",requested).apply();
  if(requested==0){
   String clearing=state.getString("pending_clear_nonce","");
   // An old clear response must not erase a notification that replaced it.
   if(!clearing.isEmpty()&&state.getInt("count",0)>0&&!clearing.equals(state.getString("nonce",""))){
    state.edit().remove("pending_clear_nonce").apply();return;
   }
   acknowledgeLocally(state,ids(state));
   state.edit().putInt("count",0).remove("nonce").remove("snapshot_ids").remove("pending_clear_nonce").apply();
   cancelOwn(context);return;
  }
  String number=requested==1?intent.getStringExtra(TelecomManager.EXTRA_NOTIFICATION_PHONE_NUMBER):"";
  Snapshot snapshot=read(context,requested,number);
  if(snapshot.suppressed){state.edit().putInt("count",0).remove("nonce").remove("snapshot_ids").apply();cancelOwn(context);return;}
  show(context,snapshot,requested);
 }
 private static final class Entry {
  final long id,date;final String number,name;
  Entry(long id,long date,String number,String name){this.id=id;this.date=date;this.number=number;this.name=name;}
 }
 private static final class Snapshot {
  final List<Entry> entries=new ArrayList<>();final List<Long> ids=new ArrayList<>();
  int count;boolean readable,suppressed;
 }
 private static String safeNumber(String number){return CallLauncher.isCallable(number)?number.trim():"";}
 private static String identity(Context context,String number,int presentation){
  if(presentation==TelecomManager.PRESENTATION_RESTRICTED)return tr(context,"מספר חסוי","Private number");
  if(number.isEmpty())return tr(context,"מספר לא מזוהה","Unknown caller");
  if(permitted(context,Manifest.permission.READ_CONTACTS)){
   Uri lookup=Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,Uri.encode(number));
   try(Cursor cursor=context.getContentResolver().query(lookup,new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME},null,null,null)){
    if(cursor!=null&&cursor.moveToFirst()){
     String name=cursor.getString(0);if(name!=null&&!name.trim().isEmpty()&&permitted(context,Manifest.permission.READ_CONTACTS))return name.trim();
    }
   }catch(RuntimeException unavailable){/* The number is a complete local fallback. */}
  }
  return number;
 }
 private static Snapshot read(Context context,int requested,String fallback){
  Snapshot result=new Snapshot();Set<String> acknowledged=new HashSet<>(prefs(context).getStringSet(ACKNOWLEDGED,Collections.emptySet()));
  if(permitted(context,Manifest.permission.READ_CALL_LOG)){
   String[] projection={CallLog.Calls._ID,CallLog.Calls.NUMBER,CallLog.Calls.DATE,CallLog.Calls.NUMBER_PRESENTATION};
   try(Cursor cursor=context.getContentResolver().query(CallLog.Calls.CONTENT_URI,projection,
     CallLog.Calls.TYPE+"=? AND "+CallLog.Calls.NEW+"=1",new String[]{String.valueOf(CallLog.Calls.MISSED_TYPE)},
     CallLog.Calls.DATE+" DESC,"+CallLog.Calls._ID+" DESC")){
    if(cursor!=null){
     while(cursor.moveToNext()){
      long id=cursor.getLong(0);if(id<=0)continue;
      if(acknowledged.contains(String.valueOf(id)))continue;
      int presentation=cursor.getInt(3);String number=presentation==TelecomManager.PRESENTATION_ALLOWED?safeNumber(cursor.getString(1)):"";
      result.ids.add(id);
      if(result.entries.size()<5)result.entries.add(new Entry(id,cursor.getLong(2),number,identity(context,number,presentation)));
     }
     result.readable=true;
    }
   }catch(RuntimeException unavailable){result.ids.clear();result.entries.clear();}
  }
  if(!permitted(context,Manifest.permission.READ_CALL_LOG)){result.ids.clear();result.entries.clear();result.readable=false;}
  if(result.readable&&result.ids.isEmpty()){
   result.suppressed=true;return result; // No actual unacknowledged missed rows remain.
  }
  // Telecom's counter can reset while a newer row is being logged. A complete
  // permitted query is the authoritative unread snapshot; never truncate it to
  // an older platform count. The broadcast remains the required trusted trigger.
  result.count=result.readable?result.ids.size():requested;
  if(result.entries.isEmpty()){
   String number=safeNumber(fallback);
   result.entries.add(new Entry(-1,System.currentTimeMillis(),number,identity(context,number,TelecomManager.PRESENTATION_ALLOWED)));
  }
  return result;
 }
 private static String serialized(List<Long> ids){JSONArray array=new JSONArray();for(Long id:ids)array.put(id);return array.toString();}
 private static List<Long> ids(SharedPreferences state){
  ArrayList<Long> result=new ArrayList<>();
  try{JSONArray array=new JSONArray(state.getString("snapshot_ids","[]"));for(int n=0;n<array.length();n++){
   long id=array.getLong(n);if(id>0&&!result.contains(id))result.add(id);
  }}catch(Exception invalid){/* Never broaden a corrupt snapshot to all call-log rows. */}
  return result;
 }
 private static void show(Context context,Snapshot snapshot,int platformCount){
  SharedPreferences state=prefs(context);String selected=serialized(snapshot.ids);
  String number=snapshot.entries.get(0).number;
  boolean same=state.getInt("count",0)==snapshot.count&&selected.equals(state.getString("snapshot_ids","[]"))
   &&number.equals(state.getString("number",""));
  String nonce=same?state.getString("nonce",""):"";if(nonce.isEmpty())nonce=UUID.randomUUID().toString();
  state.edit().putInt("count",snapshot.count).putInt("platform_count",platformCount).putString("number",number)
   .putString("snapshot_ids",selected).putString("nonce",nonce).apply();
  NotificationManager manager=context.getSystemService(NotificationManager.class);
  if(manager==null||!manager.areNotificationsEnabled()||Build.VERSION.SDK_INT>=33&&!permitted(context,Manifest.permission.POST_NOTIFICATIONS))return;
  NotificationChannel channel=new NotificationChannel(CHANNEL,tr(context,"שיחות שלא נענו","Missed calls"),NotificationManager.IMPORTANCE_DEFAULT);
  channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);manager.createNotificationChannel(channel);
  if(manager.getNotificationChannel(CHANNEL).getImportance()==NotificationManager.IMPORTANCE_NONE)return;
  Intent open=new Intent(context,MainActivity.class).setAction(ACTION_OPEN).setData(Uri.parse("dacaller-missed:open/"+nonce))
   .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
  Intent clear=new Intent(context,MissedCallClearReceiver.class).setAction(ACTION_CLEAR).setData(Uri.parse("dacaller-missed:clear/"+nonce));
  PendingIntent content=PendingIntent.getActivity(context,15031,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  PendingIntent dismiss=PendingIntent.getBroadcast(context,15032,clear,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  String title=snapshot.count==1?tr(context,"שיחה שלא נענתה","Missed call"):
   snapshot.count+" "+tr(context,"שיחות שלא נענו","missed calls");
  String latest=snapshot.entries.get(0).name;
  Notification.InboxStyle lines=new Notification.InboxStyle().setBigContentTitle(title);
  for(Entry entry:snapshot.entries){
   String line=entry.name;
   if(!entry.number.isEmpty()&&!entry.number.equals(entry.name))line+=" · "+entry.number;
   lines.addLine(line);
  }
  if(snapshot.count>snapshot.entries.size())lines.setSummaryText("+"+(snapshot.count-snapshot.entries.size())+" "+tr(context,"נוספות","more"));
  android.os.Bundle extras=new android.os.Bundle();extras.putInt(EXTRA_COUNT,snapshot.count);extras.putInt(EXTRA_PLATFORM_COUNT,platformCount);
  Notification publicVersion=new Notification.Builder(context,CHANNEL).setSmallIcon(android.R.drawable.stat_notify_missed_call)
   .setContentTitle(title).setContentText(tr(context,"פתח את האפליקציה לפרטים","Open the app for details")).build();
  Notification notification=new Notification.Builder(context,CHANNEL).setSmallIcon(android.R.drawable.stat_notify_missed_call)
   .setColor(0xFFE05E69).setContentTitle(title).setContentText(latest).setStyle(lines).setNumber(snapshot.count)
   .setCategory(Build.VERSION.SDK_INT>=31?Notification.CATEGORY_MISSED_CALL:Notification.CATEGORY_CALL)
   .setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(publicVersion).setWhen(snapshot.entries.get(0).date)
   .setShowWhen(true).setOnlyAlertOnce(same).setAutoCancel(false).setContentIntent(content).setDeleteIntent(dismiss)
   .setGroup("dacaller.missed").setGroupSummary(true).addExtras(extras)
   .addAction(android.R.drawable.ic_menu_close_clear_cancel,tr(context,"ניקוי","Clear"),dismiss).build();
  try{manager.notify(ID,notification);}catch(RuntimeException unavailable){CallFailureReporter.record(context,"missed-notification-post",unavailable);}
 }
 private static boolean validUserIntent(SharedPreferences state,Intent intent){
  if(intent==null||!(ACTION_OPEN.equals(intent.getAction())||ACTION_CLEAR.equals(intent.getAction()))||state.getInt("count",0)<=0)return false;
  String nonce=state.getString("nonce","");if(nonce.isEmpty())return false;
  Uri data=intent.getData();String operation=ACTION_OPEN.equals(intent.getAction())?"open/":"clear/";
  return data!=null&&("dacaller-missed:"+operation+nonce).equals(data.toString());
 }
 private static void acknowledgeLocally(SharedPreferences state,List<Long> selected){
  Set<String> acknowledged=new HashSet<>(state.getStringSet(ACKNOWLEDGED,Collections.emptySet()));
  for(Long id:selected)acknowledged.add(String.valueOf(id));state.edit().putStringSet(ACKNOWLEDGED,acknowledged).apply();
 }
 static void userClear(Context context,Intent intent){
  SharedPreferences state=prefs(context);
  if(!validUserIntent(state,intent))return;
  if(!isDefaultDialer(context)){state.edit().putInt("count",0).remove("nonce").remove("snapshot_ids").apply();cancelOwn(context);return;}
  List<Long> selected=ids(state);String nonce=state.getString("nonce","");
  acknowledgeLocally(state,selected);
  // Only an explicit current notification action may change read flags, and
  // only for its captured IDs. Never remove calls or modify the user's archive.
  if(permitted(context,Manifest.permission.WRITE_CALL_LOG)){
   ContentValues values=new ContentValues();values.put(CallLog.Calls.NEW,0);values.put(CallLog.Calls.IS_READ,1);
   for(int start=0;start<selected.size();start+=200){
    int end=Math.min(selected.size(),start+200);StringBuilder where=new StringBuilder(CallLog.Calls.TYPE+"=? AND "+CallLog.Calls._ID+" IN (");
    String[] arguments=new String[end-start+1];arguments[0]=String.valueOf(CallLog.Calls.MISSED_TYPE);
    for(int n=start;n<end;n++){if(n>start)where.append(',');where.append('?');arguments[n-start+1]=String.valueOf(selected.get(n));}
    where.append(')');
    try{context.getContentResolver().update(CallLog.Calls.CONTENT_URI,values,where.toString(),arguments);}
    catch(RuntimeException unavailable){CallFailureReporter.record(context,"missed-notification-read",unavailable);}
   }
  }
  state.edit().putInt("count",0).remove("nonce").remove("snapshot_ids").apply();cancelOwn(context);
  Snapshot remaining=read(context,Integer.MAX_VALUE,"");
  if(remaining.readable&&!remaining.ids.isEmpty()){
   // A newer logged call may arrive before its broadcast. Leave it unread and
   // show it with a new nonce; do not clear Telecom's newer global count.
   show(context,remaining,state.getInt("last_platform_count",remaining.count));return;
  }
  state.edit().putString("pending_clear_nonce",nonce).apply();
  clearTelecomCounter(context);
 }
 /**
  * Android13 TelecomServiceImpl.cancelMissedCallsNotification enforces
  * MODIFY_PHONE_STATE OR its verified default/system dialer package via
  * enforcePermissionOrPrivilegedDialer. The SDK annotation models only the
  * signature-permission alternative. Recheck our actual role at the API call;
  * loss of that role is also caught by the server and handled below.
  * https://github.com/aosp-mirror-neo/platform_packages_services_Telecomm/blob/android-13.0.0_r1/src/com/android/server/telecom/TelecomServiceImpl.java#L1116
  */
 @android.annotation.SuppressLint("MissingPermission")
 private static void clearTelecomCounter(Context context){
  if(!isDefaultDialer(context))return;
  try{TelecomManager telecom=context.getSystemService(TelecomManager.class);if(telecom!=null)telecom.cancelMissedCallsNotification();}
  catch(RuntimeException unavailable){CallFailureReporter.record(context,"missed-notification-clear",unavailable);}
 }
 private static void cancelOwn(Context context){
  try{NotificationManager manager=context.getSystemService(NotificationManager.class);if(manager!=null)manager.cancel(ID);}
  catch(RuntimeException unavailable){CallFailureReporter.record(context,"missed-notification-cancel",unavailable);}
 }
 private MissedCallNotifications(){}
}
