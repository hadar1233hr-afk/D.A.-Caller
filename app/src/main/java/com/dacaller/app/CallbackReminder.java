package com.dacaller.app;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.CancellationSignal;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.telecom.Call;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;

/** Optional daily summary, computed only from local call/contacts providers. Never changes either provider. */
final class CallbackReminder {
 static final String PREF_ENABLED="callback_reminder_enabled",PREF_MINUTE="callback_reminder_minute";
 static final int DEFAULT_MINUTE=21*60,ID=1504;
 static final String CHANNEL="dacaller_callbacks_v1",STATE="da_callback_reminder_state";
 static final String ACTION_ALARM="com.dacaller.app.CALLBACK_REMINDER_ALARM";
 static final String ACTION_RECENTS="com.dacaller.app.CALLBACK_REMINDER_RECENTS";
 static final String ACTION_DISMISS="com.dacaller.app.CALLBACK_REMINDER_DISMISS";
 static final String EXTRA_DAY="callback_reminder_day",EXTRA_ZONE="callback_reminder_zone",EXTRA_DUE="callback_reminder_due";
 static final String EXTRA_PEOPLE="com.dacaller.app.CALLBACK_PEOPLE",EXTRA_MISSED="com.dacaller.app.CALLBACK_MISSED";
 static final String EXTRA_MISSED_TODAY="com.dacaller.app.CALLBACK_MISSED_TODAY";
 static final ExecutorService worker=Executors.newFixedThreadPool(2);
 private static final ScheduledExecutorService timeouts=Executors.newSingleThreadScheduledExecutor();
 private static final AtomicBoolean delivering=new AtomicBoolean();
 private static final AtomicBoolean refreshing=new AtomicBoolean();
 private static final AtomicBoolean refreshAgain=new AtomicBoolean();
 private static final Set<Long> thresholdScheduledFor=ConcurrentHashMap.newKeySet();
 static final String ERROR_NONE="",ERROR_PERMISSION="permission",ERROR_ROLE="role",ERROR_PROVIDER="provider";
 static final class Result {
  final CallbackReminderModel.Day day;final List<CallbackReminderModel.Entry> entries;final String error;
  Result(CallbackReminderModel.Day day,List<CallbackReminderModel.Entry> entries,String error){this.day=day;this.entries=entries;this.error=error;}
 }
 private static SharedPreferences prefs(Context context){return context.getSharedPreferences("da_caller",Context.MODE_PRIVATE);}
 static boolean enabled(Context context){return prefs(context).getBoolean(PREF_ENABLED,false);}
 static int minuteOfDay(Context context){return Math.max(0,Math.min(1439,prefs(context).getInt(PREF_MINUTE,DEFAULT_MINUTE)));}
 static void setEnabled(Context context,boolean value){if(!SettingsBackup.allowPreferenceChange(context))return;prefs(context).edit().putBoolean(PREF_ENABLED,value).apply();schedule(context);}
 static void setMinuteOfDay(Context context,int minute){
  if(minute<0||minute>1439)throw new IllegalArgumentException("Reminder minute must be0..1439");
  if(!SettingsBackup.allowPreferenceChange(context))return;
  prefs(context).edit().putInt(PREF_MINUTE,minute).apply();schedule(context);
 }
 static String tr(Context context,String he,String en){return I18n.isEnglish(context)?en:he;}
 private static boolean permitted(Context context,String permission){return context.checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED;}
 /** Pending preferences may be transient; live privacy revocation still removes our summary. */
 private static boolean deferForRecovery(Context app){
  if(!SettingsBackup.hasPendingRestore(app))return false;
  if(!MissedCallNotifications.isDefaultDialer(app)||!permitted(app,Manifest.permission.READ_CALL_LOG)
    ||Build.VERSION.SDK_INT>=33&&!permitted(app,Manifest.permission.POST_NOTIFICATIONS))cancelNotification(app);
  return true;
 }
 static Intent alarmIntent(Context context,long due,TimeZone zone){
  return new Intent(context,CallbackReminderReceiver.class).setAction(ACTION_ALARM)
   .putExtra(EXTRA_DAY,CallbackReminderModel.day(due,zone).key).putExtra(EXTRA_ZONE,zone.getID()).putExtra(EXTRA_DUE,due);
 }
 static PendingIntent alarmOperation(Context context,long due,TimeZone zone){
  return PendingIntent.getBroadcast(context,15040,alarmIntent(context,due,zone),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
 }
 static void schedule(Context context){schedule(context,System.currentTimeMillis());}
 static void schedule(Context context,long now){
  Context app=context.getApplicationContext();
  // Recovery still owns these preferences; retain the last valid alarm/summary.
  if(deferForRecovery(app))return;
  AlarmManager manager=app.getSystemService(AlarmManager.class);if(manager==null)return;
  if(!enabled(app)){
   PendingIntent old=PendingIntent.getBroadcast(app,15040,new Intent(app,CallbackReminderReceiver.class).setAction(ACTION_ALARM),
    PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);
   if(old!=null){manager.cancel(old);old.cancel();}cancelNotification(app);return;
  }
  TimeZone zone=TimeZone.getDefault();long due=CallbackReminderModel.nextAlarm(now,minuteOfDay(app),zone);
  PendingIntent operation=alarmOperation(app,due,zone);
  try{
   // End-of-day summaries do not justify exact-alarm special access. Android may defer delivery.
   manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,due,operation);
  }catch(RuntimeException failure){CallFailureReporter.record(app,"callback-reminder-schedule",failure);}
  // Reboot, a time-zone change or restoring the opt-in after today's cutoff
  // can otherwise silently skip today's reminder. The normal daily guard still applies.
  maybeRefresh(app,now);
 }
 static void maybeRefresh(Context context){maybeRefresh(context,System.currentTimeMillis());}
 private static void maybeRefresh(Context context,long now){
  Context app=context.getApplicationContext();if(deferForRecovery(app))return;
  if(!enabled(app)){refreshAgain.set(false);cancelNotification(app);return;}
  for(PhoneSessionService.CallbackConnection active:PhoneSessionService.callbackConnections()){
   if(active.connectedAtMillis<=0)continue;long elapsed=now-active.connectedAtMillis;
   if(elapsed>=0&&elapsed<2000&&thresholdScheduledFor.add(active.connectedAtMillis))
    timeouts.schedule(()->{thresholdScheduledFor.remove(active.connectedAtMillis);maybeRefresh(app);},2100-elapsed,TimeUnit.MILLISECONDS);
  }
  if(!refreshing.compareAndSet(false,true)){refreshAgain.set(true);return;}
  worker.execute(()->{
   try{refreshNow(app,now);}
   finally{refreshing.set(false);if(!delivering.get()&&refreshAgain.getAndSet(false))maybeRefresh(app);}
  });
 }
 /** Runs on the reminder worker. Existing summaries remain current if the user postpones the cutoff. */
 static void refreshNow(Context context,long now){
  Context app=context.getApplicationContext();if(deferForRecovery(app))return;
  if(!enabled(app)){cancelNotification(app);return;}
  TimeZone zone=TimeZone.getDefault();CallbackReminderModel.Day today=CallbackReminderModel.day(now,zone);
  // Calculating via the next-day helper retains local-clock/DST semantics.
  long cutoff=CallbackReminderModel.nextAlarm(today.start-1,minuteOfDay(app),zone);
  boolean alreadyNotified=today.key.equals(app.getSharedPreferences(STATE,Context.MODE_PRIVATE).getString("notified_day",""));
  if(now>=cutoff||alreadyNotified)deliver(app,alarmIntent(app,Math.min(cutoff,now),zone),now);
  else cancelStaleNotification(app,today.key);
 }
 private static void cancelStaleNotification(Context context,String today){
  NotificationManager manager=context.getSystemService(NotificationManager.class);if(manager==null)return;
  try{for(android.service.notification.StatusBarNotification active:manager.getActiveNotifications()){
   if(active.getId()==ID&&!today.equals(active.getNotification().extras.getString(EXTRA_DAY)))manager.cancel(ID);
  }}catch(RuntimeException unavailable){CallFailureReporter.record(context,"callback-reminder-stale",unavailable);}
 }
 private static Result failure(CallbackReminderModel.Day day,String error){return new Result(day,Collections.emptyList(),error);}
 static Result load(Context context,long observedAt,String selectedDay){
  Context app=context.getApplicationContext();CallbackReminderModel.Day day=CallbackReminderModel.selectedDay(selectedDay,observedAt,TimeZone.getDefault());
  if(!MissedCallNotifications.isDefaultDialer(app))return failure(day,ERROR_ROLE);
  if(!permitted(app,Manifest.permission.READ_CALL_LOG))return failure(day,ERROR_PERMISSION);
  CancellationSignal cancellation=new CancellationSignal();ScheduledFuture<?> timeout=timeouts.schedule(cancellation::cancel,6,TimeUnit.SECONDS);
  try{
   long until=Math.min(observedAt,day.end-1);ArrayList<CallbackReminderModel.Row> rows=new ArrayList<>();
   String[] columns={CallLog.Calls._ID,CallLog.Calls.NUMBER,CallLog.Calls.DATE,CallLog.Calls.DURATION,CallLog.Calls.TYPE,CallLog.Calls.NUMBER_PRESENTATION};
   try(Cursor cursor=app.getContentResolver().query(CallLog.Calls.CONTENT_URI,columns,CallLog.Calls.DATE+">=? AND "+CallLog.Calls.DATE+"<=?",
     new String[]{String.valueOf(day.start),String.valueOf(until)},CallLog.Calls.DATE+" ASC,"+CallLog.Calls._ID+" ASC",cancellation)){
    if(cursor==null)return failure(day,ERROR_PROVIDER);
    while(cursor.moveToNext()){
     cancellation.throwIfCanceled();rows.add(new CallbackReminderModel.Row(cursor.getLong(0),cursor.getString(1),cursor.getLong(2),cursor.getLong(3),cursor.getInt(4),cursor.getInt(5)));
    }
   }
   // A live conversation is not in CallLog until disconnect. Its immutable
   // production Telecom snapshot prevents a false reminder while already talking.
   for(PhoneSessionService.CallbackConnection active:PhoneSessionService.callbackConnections()){
    if((active.state==Call.STATE_ACTIVE||active.state==Call.STATE_HOLDING)&&active.connectedAtMillis>0
      &&active.connectedAtMillis<=observedAt-2000&&active.connectedAtMillis>=day.start&&active.connectedAtMillis<day.end
      &&!CallbackReminderModel.safeNumber(active.number).isEmpty()){
     rows.add(new CallbackReminderModel.Row(Long.MAX_VALUE,active.number,active.connectedAtMillis,(observedAt-active.connectedAtMillis)/1000,
       CallbackReminderModel.INCOMING,CallbackReminderModel.ALLOWED));
    }
   }
   List<CallbackReminderModel.Entry> pending=CallbackReminderModel.reduce(rows,day,observedAt);
   Map<String,Identity> names=identities(app,pending,cancellation);ArrayList<CallbackReminderModel.Entry> resolved=new ArrayList<>();
   boolean contacts=permitted(app,Manifest.permission.READ_CONTACTS);
   for(CallbackReminderModel.Entry entry:pending){
    Identity identity=contacts?names.get(entry.key):null;
    String fallback=entry.number.isEmpty()?tr(app,entry.privateNumber?"מספר חסוי":"מספר לא מזוהה",entry.privateNumber?"Private number":"Unknown caller"):entry.number;
    resolved.add(entry.identified(identity==null?fallback:identity.name,identity==null?-1:identity.id));
   }
   if(!MissedCallNotifications.isDefaultDialer(app))return failure(day,ERROR_ROLE);
   if(!permitted(app,Manifest.permission.READ_CALL_LOG))return failure(day,ERROR_PERMISSION);
   return new Result(day,Collections.unmodifiableList(resolved),ERROR_NONE);
  }catch(RuntimeException unavailable){return failure(day,ERROR_PROVIDER);}
  finally{timeout.cancel(false);}
 }
 private static final class Identity {
  final String name;final long id;Identity(String name,long id){this.name=name;this.id=id;}
 }
 private static Map<String,Identity> identities(Context app,List<CallbackReminderModel.Entry> entries,CancellationSignal cancellation){
  if(entries.isEmpty()||!permitted(app,Manifest.permission.READ_CONTACTS))return Collections.emptyMap();
  HashMap<String,Boolean> wanted=new HashMap<>();for(CallbackReminderModel.Entry entry:entries)if(!entry.number.isEmpty())wanted.put(entry.key,true);
  if(wanted.isEmpty())return Collections.emptyMap();HashMap<String,Identity> names=new HashMap<>();
  String[] columns={ContactsContract.CommonDataKinds.Phone.NUMBER,ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,ContactsContract.CommonDataKinds.Phone.CONTACT_ID};
  try(Cursor cursor=app.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,columns,null,null,
    ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY+" DESC,"+ContactsContract.CommonDataKinds.Phone.CONTACT_ID+" ASC",cancellation)){
   if(cursor!=null)while(cursor.moveToNext()){
    cancellation.throwIfCanceled();String key=CallbackReminderModel.key(cursor.getString(0)),name=cursor.getString(1);long id=cursor.getLong(2);
    if(wanted.containsKey(key)&&!names.containsKey(key)&&name!=null&&!name.trim().isEmpty()&&id>0)names.put(key,new Identity(name.trim(),id));
   }
  }catch(RuntimeException denied){return Collections.emptyMap();}
  return permitted(app,Manifest.permission.READ_CONTACTS)?names:Collections.emptyMap();
 }
 static Intent openIntent(Context context,String day){
  return new Intent(context,CallbackReminderActivity.class).setAction("com.dacaller.app.OPEN_CALLBACK_REMINDER")
   .setData(Uri.parse("dacaller-callback:"+day)).putExtra(EXTRA_DAY,day).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
 }
 static void dismissed(Context context,Intent intent){
  String day=intent==null?null:intent.getStringExtra(EXTRA_DAY);
  if(day==null||!day.equals(context.getSharedPreferences(STATE,Context.MODE_PRIVATE).getString("notified_day","")))return;
  context.getSharedPreferences(STATE,Context.MODE_PRIVATE).edit().putString("dismissed_day",day).apply();
 }
 static boolean deliver(Context context,Intent alarm,long now){
  long began=android.os.SystemClock.elapsedRealtime();
  Context app=context.getApplicationContext();if(deferForRecovery(app))return false;
  CallbackReminderModel.Day today=CallbackReminderModel.day(now,TimeZone.getDefault());
  if(alarm==null||!ACTION_ALARM.equals(alarm.getAction())||!enabled(app))return false;
  if(!today.key.equals(alarm.getStringExtra(EXTRA_DAY))||!today.zone.equals(alarm.getStringExtra(EXTRA_ZONE))
    ||alarm.getLongExtra(EXTRA_DUE,Long.MAX_VALUE)>now)return false;
  if(!MissedCallNotifications.isDefaultDialer(app)||!permitted(app,Manifest.permission.READ_CALL_LOG)
    ||Build.VERSION.SDK_INT>=33&&!permitted(app,Manifest.permission.POST_NOTIFICATIONS)){cancelNotification(app);return false;}
  NotificationManager manager=app.getSystemService(NotificationManager.class);
  if(manager==null||!manager.areNotificationsEnabled())return false;
  if(!delivering.compareAndSet(false,true)){refreshAgain.set(true);return false;}
  try{
   SharedPreferences runtime=app.getSharedPreferences(STATE,Context.MODE_PRIVATE);
   boolean alerted=today.key.equals(runtime.getString("notified_day",""));
   boolean visible=false;for(android.service.notification.StatusBarNotification active:manager.getActiveNotifications())
    if(active.getId()==ID&&today.key.equals(active.getNotification().extras.getString(EXTRA_DAY))){visible=true;break;}
   // A swipe or opening the list must not repost the same day's notification.
   // Existing visible summaries can still shrink after successful callbacks.
   if(alerted&&(!visible||today.key.equals(runtime.getString("dismissed_day",""))))return false;
   NotificationChannel channel=new NotificationChannel(CHANNEL,tr(app,"תזכורת לחזרה לשיחות","Calls to return"),NotificationManager.IMPORTANCE_DEFAULT);
   channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);manager.createNotificationChannel(channel);
   if(manager.getNotificationChannel(CHANNEL).getImportance()==NotificationManager.IMPORTANCE_NONE)return false;
   Result result=load(app,now,today.key);
   if(!result.error.isEmpty()||result.entries.isEmpty()||!enabled(app)||!MissedCallNotifications.isDefaultDialer(app)){
    cancelNotification(app);return false;
   }
   // Provider work can straddle midnight or a time-zone change. The payload is
   // valid for its local day only; never post yesterday's names after it expires.
   long completedAt=now+Math.max(0,android.os.SystemClock.elapsedRealtime()-began);
   CallbackReminderModel.Day completedDay=CallbackReminderModel.day(completedAt,TimeZone.getDefault());
   if(!today.key.equals(completedDay.key)||!today.zone.equals(completedDay.zone)){cancelNotification(app);return false;}
   // A swipe/open can happen while the local provider is loading. It must win
   // over a background refresh that started with the old visible notification.
   if(alerted){
    if(today.key.equals(runtime.getString("dismissed_day","")))return false;
    boolean stillVisible=false;for(android.service.notification.StatusBarNotification active:manager.getActiveNotifications())
     if(active.getId()==ID&&today.key.equals(active.getNotification().extras.getString(EXTRA_DAY))){stillVisible=true;break;}
    if(!stillVisible)return false;
   }
   if(!permitted(app,Manifest.permission.READ_CALL_LOG)||Build.VERSION.SDK_INT>=33&&!permitted(app,Manifest.permission.POST_NOTIFICATIONS))return false;
   int missed=0,totalToday=0;StringBuilder people=new StringBuilder();Notification.InboxStyle lines=new Notification.InboxStyle();
   for(int i=0;i<result.entries.size();i++){
    CallbackReminderModel.Entry entry=result.entries.get(i);missed+=entry.missedCount;totalToday+=entry.missedCountToday;
    if(i<3){if(people.length()>0)people.append(tr(app,", ",", "));people.append(entry.name);}
    if(i<5)lines.addLine(entry.name+" · "+entry.missedCountToday+" "+tr(app,"שיחות שלא נענו היום","missed calls today"));
   }
   if(result.entries.size()>3)people.append(tr(app," ועוד "," and ")).append(result.entries.size()-3);
   String title=tr(app,"לא חזרת אליהם היום","Calls still waiting for your return");
   lines.setBigContentTitle(title);if(result.entries.size()>5)lines.setSummaryText("+"+(result.entries.size()-5)+" "+tr(app,"נוספים","more"));
   PendingIntent content=PendingIntent.getActivity(app,15041,openIntent(app,today.key),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
   PendingIntent delete=PendingIntent.getBroadcast(app,15042,new Intent(app,CallbackReminderReceiver.class).setAction(ACTION_DISMISS)
     .setData(Uri.parse("dacaller-callback-dismiss:"+today.key))
     .putExtra(EXTRA_DAY,today.key),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
   android.os.Bundle extras=new android.os.Bundle();extras.putInt(EXTRA_PEOPLE,result.entries.size());extras.putInt(EXTRA_MISSED,missed);
   extras.putInt(EXTRA_MISSED_TODAY,totalToday);extras.putString(EXTRA_DAY,today.key);
   Notification publicVersion=new Notification.Builder(app,CHANNEL).setSmallIcon(android.R.drawable.stat_notify_missed_call)
    .setContentTitle(tr(app,"תזכורת לחזרה לשיחות","Call reminder")).setContentText(tr(app,"פתח את האפליקציה לפרטים","Open the app for details")).build();
   Notification notification=new Notification.Builder(app,CHANNEL).setSmallIcon(android.R.drawable.stat_notify_missed_call).setColor(0xFF296849)
    .setContentTitle(title).setContentText(tr(app,"לא חזרת ל־","You have not returned calls from ")+people).setStyle(lines)
    .setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(publicVersion).setCategory(Notification.CATEGORY_REMINDER)
    .setAutoCancel(true).setContentIntent(content).setDeleteIntent(delete).setOnlyAlertOnce(alerted)
    .setNumber(result.entries.size()).addExtras(extras).build();
   manager.notify(ID,notification);runtime.edit().putString("notified_day",today.key).apply();return true;
  }catch(RuntimeException unavailable){CallFailureReporter.record(app,"callback-reminder-post",unavailable);return false;}
  finally{delivering.set(false);if(!refreshing.get()&&refreshAgain.getAndSet(false))maybeRefresh(app);}
 }
 static void cancelNotification(Context context){
  try{NotificationManager manager=context.getSystemService(NotificationManager.class);if(manager!=null)manager.cancel(ID);}
  catch(RuntimeException unavailable){CallFailureReporter.record(context,"callback-reminder-cancel",unavailable);}
 }
 private CallbackReminder(){}
}
