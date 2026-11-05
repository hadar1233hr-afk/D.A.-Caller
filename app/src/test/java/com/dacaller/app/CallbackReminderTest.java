package com.dacaller.app;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Looper;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.telecom.Call;
import android.telecom.TelecomManager;
import android.view.View;
import android.widget.TextView;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowContentResolver;

/** Permission/privacy boundaries, actual notification contracts and read-only provider access. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=33) @LooperMode(LooperMode.Mode.PAUSED)
public final class CallbackReminderTest {
 private static final long NOW=Instant.parse("2026-11-02T21:00:00Z").toEpochMilli();
 private static final String FIRST="0500099971",SECOND="0500099972";
 private Application app;private NotificationManager manager;private LogProvider logs;private PeopleProvider people;private TimeZone oldZone;
 public static final class LogProvider extends ContentProvider {
  final List<CallbackReminderModel.Row> rows=new ArrayList<>();volatile int queries,writes;volatile boolean queryOnMain;boolean fail;Runnable duringQuery;
  public boolean onCreate(){return true;}
  public Cursor query(Uri uri,String[] projection,String selection,String[] arguments,String sort){
   queries++;queryOnMain|=Looper.myLooper()==Looper.getMainLooper();if(fail)throw new IllegalStateException("Synthetic local provider unavailable");
   assertTrue(selection.contains(CallLog.Calls.DATE+">=?"));assertTrue(selection.contains(CallLog.Calls.DATE+"<=?"));assertEquals(2,arguments.length);
   assertEquals(String.valueOf(day().start),arguments[0]);long from=Long.parseLong(arguments[0]),until=Long.parseLong(arguments[1]);
   MatrixCursor result=new MatrixCursor(projection);
   for(CallbackReminderModel.Row row:rows)if(row.date>=from&&row.date<=until){
    Map<String,Object> values=new HashMap<>();values.put(CallLog.Calls._ID,row.id);values.put(CallLog.Calls.NUMBER,row.number);
    values.put(CallLog.Calls.DATE,row.date);values.put(CallLog.Calls.DURATION,row.duration);values.put(CallLog.Calls.TYPE,row.type);values.put(CallLog.Calls.NUMBER_PRESENTATION,row.presentation);
    Object[] selected=new Object[projection.length];for(int n=0;n<projection.length;n++)selected[n]=values.get(projection[n]);result.addRow(selected);
   }
   if(duringQuery!=null)duringQuery.run();return result;
  }
  public int update(Uri uri,ContentValues values,String where,String[] arguments){writes++;throw new AssertionError("Daily reminders cannot mark calls read");}
  public int delete(Uri uri,String selection,String[] arguments){writes++;throw new AssertionError("Daily reminders cannot delete calls");}
  public Uri insert(Uri uri,ContentValues values){writes++;throw new AssertionError("Daily reminders cannot insert calls");}public String getType(Uri uri){return null;}
 }
 public static final class PeopleProvider extends ContentProvider {
  final Map<String,String> names=new HashMap<>();volatile int queries,writes;volatile boolean queryOnMain;
  public boolean onCreate(){return true;}
  public Cursor query(Uri uri,String[] projection,String selection,String[] arguments,String sort){
   queries++;queryOnMain|=Looper.myLooper()==Looper.getMainLooper();MatrixCursor result=new MatrixCursor(projection);
   for(Map.Entry<String,String> row:names.entrySet()){
    Map<String,Object> values=new HashMap<>();values.put(ContactsContract.CommonDataKinds.Phone.NUMBER,row.getKey());
    values.put(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,row.getValue());values.put(ContactsContract.CommonDataKinds.Phone.CONTACT_ID,12L);
    Object[] selected=new Object[projection.length];for(int n=0;n<projection.length;n++)selected[n]=values.get(projection[n]);result.addRow(selected);
   }
   return result;
  }
  public int update(Uri uri,ContentValues values,String where,String[] arguments){writes++;throw new AssertionError("Read-only contacts");}
  public int delete(Uri uri,String selection,String[] arguments){writes++;throw new AssertionError("Read-only contacts");}
  public Uri insert(Uri uri,ContentValues values){writes++;throw new AssertionError("Read-only contacts");}public String getType(Uri uri){return null;}
 }
 private static CallbackReminderModel.Day day(){return CallbackReminderModel.day(NOW,TimeZone.getTimeZone("UTC"));}
 @Before public void setup()throws Exception{
  app=RuntimeEnvironment.getApplication();oldZone=TimeZone.getDefault();TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
  prefs().edit().clear().putString("language","en").commit();state().edit().clear().commit();
  shadowOf(app).grantPermissions(Manifest.permission.READ_CALL_LOG,Manifest.permission.READ_CONTACTS,Manifest.permission.POST_NOTIFICATIONS);
  shadowOf(app.getSystemService(TelecomManager.class)).setDefaultDialerPackage(app.getPackageName());
  manager=app.getSystemService(NotificationManager.class);shadowOf(manager).setNotificationsEnabled(true);
  logs=new LogProvider();people=new PeopleProvider();ProviderInfo logInfo=new ProviderInfo();logInfo.authority="call_log";logs.attachInfo(app,logInfo);
  ShadowContentResolver.registerProviderInternal("call_log",logs);ProviderInfo peopleInfo=new ProviderInfo();peopleInfo.authority=ContactsContract.AUTHORITY;
  people.attachInfo(app,peopleInfo);ShadowContentResolver.registerProviderInternal(ContactsContract.AUTHORITY,people);
  people.names.put("+972500099971","Dana Example");people.names.put(SECOND,"Noa Example");PhoneSessionService.current=null;PhoneSessionService.instance=null;
 }
 @After public void cleanup(){
  prefs().edit().putBoolean(CallbackReminder.PREF_ENABLED,false).commit();CallbackReminder.cancelNotification(app);TimeZone.setDefault(oldZone);
  PhoneSessionService.current=null;PhoneSessionService.instance=null;
 }
 private SharedPreferences prefs(){return app.getSharedPreferences("da_caller",0);}private SharedPreferences state(){return app.getSharedPreferences(CallbackReminder.STATE,0);}
 private void enable(){prefs().edit().putBoolean(CallbackReminder.PREF_ENABLED,true).commit();}
 private CallbackReminderModel.Row add(long id,String number,long second,long duration,int type){
  CallbackReminderModel.Row row=new CallbackReminderModel.Row(id,number,day().start+second*1000,duration,type,1);logs.rows.add(row);return row;
 }
 private void miss(long id,String number,long second){add(id,number,second,0,3);}
 private boolean deliver(Intent intent,long now)throws Exception{return CallbackReminder.worker.submit(()->CallbackReminder.deliver(app,intent,now)).get(10,TimeUnit.SECONDS);}
 private boolean deliver()throws Exception{return deliver(CallbackReminder.alarmIntent(app,NOW,TimeZone.getDefault()),NOW);}
 private void refresh()throws Exception{CallbackReminder.worker.submit(()->CallbackReminder.refreshNow(app,NOW)).get(10,TimeUnit.SECONDS);}
 private CallbackReminder.Result load()throws Exception{return CallbackReminder.worker.submit(()->CallbackReminder.load(app,NOW,day().key)).get(10,TimeUnit.SECONDS);}
 private Notification note(){return shadowOf(manager).getNotification(CallbackReminder.ID);}
 @Test public void remindersAreOffByDefaultAndDoNotQueryPrivateProviders()throws Exception{
  assertFalse(CallbackReminder.enabled(app));assertEquals(1260,CallbackReminder.minuteOfDay(app));miss(1,FIRST,10);
  CallbackReminder.maybeRefresh(app);assertFalse(deliver());assertEquals(0,logs.queries);assertEquals(0,people.queries);assertNull(note());
 }
 @Test public void localContactNameAndMissedTotalsAreGroupedWithoutAnyProviderMutation()throws Exception{
  enable();miss(1,FIRST,10);miss(2,"+972500099971",20);miss(3,SECOND,30);assertTrue(deliver());Notification note=note();
  assertEquals(2,note.extras.getInt(CallbackReminder.EXTRA_PEOPLE));assertEquals(3,note.extras.getInt(CallbackReminder.EXTRA_MISSED));
  assertEquals(3,note.extras.getInt(CallbackReminder.EXTRA_MISSED_TODAY));assertEquals(Notification.CATEGORY_REMINDER,note.category);
  assertTrue(note.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("Dana Example"));assertEquals(3,logs.rows.size());
  assertEquals(0,logs.writes);assertEquals(0,people.writes);assertFalse(logs.queryOnMain);assertFalse(people.queryOnMain);
 }
 @Test public void staleWrongZoneOrFutureAlarmCannotPostOrReadHistory()throws Exception{
  enable();miss(1,FIRST,10);Intent alarm=CallbackReminder.alarmIntent(app,NOW,TimeZone.getDefault());
  assertFalse(deliver(new Intent(alarm).putExtra(CallbackReminder.EXTRA_DAY,"2026-11-01"),NOW));
  assertFalse(deliver(new Intent(alarm).putExtra(CallbackReminder.EXTRA_ZONE,"Asia/Jerusalem"),NOW));
  assertFalse(deliver(new Intent(alarm).putExtra(CallbackReminder.EXTRA_DUE,NOW+1),NOW));assertEquals(0,logs.queries);assertNull(note());
 }
 @Test public void nonDefaultDialerNeverReadsTheLogOrPosts(){
  shadowOf(app.getSystemService(TelecomManager.class)).setDefaultDialerPackage("another.dialer");
  CallbackReminder.Result result=CallbackReminder.load(app,NOW,day().key);assertEquals(CallbackReminder.ERROR_ROLE,result.error);assertEquals(0,logs.queries);
 }
 @Test public void callLogPermissionLossCancelsExistingSummaryWithoutChangingRows()throws Exception{
  enable();miss(1,FIRST,10);assertTrue(deliver());shadowOf(app).denyPermissions(Manifest.permission.READ_CALL_LOG);
  assertFalse(deliver());assertNull(note());assertEquals(1,logs.queries);assertEquals(1,logs.rows.size());assertEquals(0,logs.writes);
 }
 @Test public void notificationPermissionLossPreventsQueriesAndPosting()throws Exception{
  enable();miss(1,FIRST,10);shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS);assertFalse(deliver());assertEquals(0,logs.queries);assertNull(note());
 }
 @Test public void notificationChannelOptOutIsRespected()throws Exception{
  enable();miss(1,FIRST,10);manager.createNotificationChannel(new android.app.NotificationChannel(CallbackReminder.CHANNEL,"Calls",NotificationManager.IMPORTANCE_NONE));
  assertFalse(deliver());assertNull(note());assertEquals(0,logs.queries);
 }
 @Test public void contactPermissionRevocationFallsBackToNumberWithoutQueryingContacts()throws Exception{
  miss(1,FIRST,10);shadowOf(app).denyPermissions(Manifest.permission.READ_CONTACTS);CallbackReminder.Result result=load();
  assertEquals("",result.error);assertEquals(FIRST,result.entries.get(0).name);assertEquals(-1,result.entries.get(0).contactId);assertEquals(0,people.queries);
 }
 @Test public void unavailableProviderIsNotAnEmptySuccessfulResult()throws Exception{
  logs.fail=true;assertEquals(CallbackReminder.ERROR_PROVIDER,load().error);assertEquals(0,logs.writes);
 }
 @Test public void successfulCallbacksSilentlyShrinkAndThenCancelTheVisibleSummary()throws Exception{
  enable();miss(1,FIRST,10);miss(2,SECOND,20);assertTrue(deliver());
  add(3,FIRST,30,2,2);assertTrue(deliver());assertEquals(1,note().extras.getInt(CallbackReminder.EXTRA_PEOPLE));
  assertTrue((note().flags&Notification.FLAG_ONLY_ALERT_ONCE)!=0);add(4,SECOND,40,2,1);assertFalse(deliver());assertNull(note());
  assertEquals(4,logs.rows.size());assertEquals(0,logs.writes);
 }
 @Test public void openingOrDismissingCannotRepeatedlyAlertThatDay()throws Exception{
  enable();miss(1,FIRST,10);assertTrue(deliver());Intent dismiss=new Intent(shadowOf(note().deleteIntent).getSavedIntent());
  new CallbackReminderReceiver().onReceive(app,dismiss);manager.cancel(CallbackReminder.ID);miss(2,SECOND,20);assertFalse(deliver());assertNull(note());
  assertEquals(day().key,state().getString("dismissed_day",""));assertEquals(2,logs.rows.size());assertEquals(0,logs.writes);
 }
 @Test public void automaticCancellationAfterOpeningAlsoDoesNotRepost()throws Exception{
  enable();miss(1,FIRST,10);assertTrue(deliver());manager.cancel(CallbackReminder.ID);assertFalse(deliver());assertEquals(1,logs.queries);
 }
 @Test public void publicLockscreenVersionHidesCallerIdentityAndOpeningCannotDial()throws Exception{
  enable();miss(1,FIRST,10);assertTrue(deliver());Notification notification=note();assertEquals(Notification.VISIBILITY_PRIVATE,notification.visibility);
  String publicCopy=String.valueOf(notification.publicVersion.extras);assertFalse(publicCopy.contains("Dana Example"));assertFalse(publicCopy.contains(FIRST));
  assertTrue(shadowOf(notification.contentIntent).isActivity());assertTrue(shadowOf(notification.contentIntent).isImmutable());
  Intent open=shadowOf(notification.contentIntent).getSavedIntent();assertEquals(CallbackReminderActivity.class.getName(),open.getComponent().getClassName());
  assertEquals(day().key,open.getStringExtra(CallbackReminder.EXTRA_DAY));assertFalse(open.hasExtra("place_call"));assertNull(open.getStringExtra("phone"));
  assertTrue(shadowOf(notification.deleteIntent).isImmutable());assertTrue(shadowOf(notification.deleteIntent).isBroadcast());
 }
 @Test public void hiddenLegacyNumberIsNotLookedUpOrDialed()throws Exception{
  enable();miss(1,"-1",10);assertTrue(deliver());assertEquals(0,people.queries);CallbackReminderModel.Entry entry=load().entries.get(0);
  assertEquals("",entry.number);assertEquals("Unknown caller",entry.name);assertFalse(CallLauncher.isCallable(entry.number));
 }
 @Test public void lateMissReportsDayTotalSeparatelyFromUnreturnedMisses()throws Exception{
  enable();miss(1,FIRST,10);add(2,FIRST,20,2,2);miss(3,FIRST,30);assertTrue(deliver());
  assertEquals(1,note().extras.getInt(CallbackReminder.EXTRA_MISSED));assertEquals(2,note().extras.getInt(CallbackReminder.EXTRA_MISSED_TODAY));
  assertTrue(note().extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)[0].toString().contains("2 missed calls today"));
 }
 @Test public void disablingCancelsOnlyOurReminderAndPreservesAllSettingsAndRows()throws Exception{
  enable();miss(1,FIRST,10);assertTrue(deliver());prefs().edit().putString("note_111","keep this private note").commit();
  manager.notify(1502,new Notification.Builder(app,CallbackReminder.CHANNEL).setContentTitle("Ongoing call").setSmallIcon(android.R.drawable.stat_sys_phone_call).build());
  CallbackReminder.setEnabled(app,false);assertNull(note());assertNotNull(shadowOf(manager).getNotification(1502));
  assertEquals("keep this private note",prefs().getString("note_111",""));assertEquals(1,logs.rows.size());assertEquals(0,logs.writes);
 }
 @Test public void scheduleUsesUserLocalCutoffAndImmutableExplicitPrivateAlarm()throws Exception{
  enable();prefs().edit().putInt(CallbackReminder.PREF_MINUTE,1260).commit();CallbackReminder.schedule(app,NOW-1000);
  org.robolectric.shadows.ShadowAlarmManager.ScheduledAlarm scheduled=shadowOf(app.getSystemService(AlarmManager.class)).getNextScheduledAlarm();
  assertNotNull(scheduled);assertEquals(NOW,scheduled.triggerAtTime);assertEquals(AlarmManager.RTC_WAKEUP,scheduled.type);
  assertTrue(shadowOf(scheduled.operation).isImmutable());Intent alarm=shadowOf(scheduled.operation).getSavedIntent();
  assertEquals(CallbackReminderReceiver.class.getName(),alarm.getComponent().getClassName());assertEquals(day().key,alarm.getStringExtra(CallbackReminder.EXTRA_DAY));
 }
 @Test public void ringingDurationDoesNotResolveButActualConnectTimeDoes()throws Exception{
  miss(1,FIRST,10);Call call=mock(Call.class);Call.Details details=mock(Call.Details.class);when(call.getDetails()).thenReturn(details);
  when(details.getHandle()).thenReturn(Uri.fromParts("tel",FIRST,null));when(details.getHandlePresentation()).thenReturn(TelecomManager.PRESENTATION_ALLOWED);
  when(details.getCallerDisplayNamePresentation()).thenReturn(TelecomManager.PRESENTATION_ALLOWED);when(call.getState()).thenReturn(Call.STATE_RINGING);
  ServiceController<PhoneSessionService> controller=Robolectric.buildService(PhoneSessionService.class).create();
  try{
   controller.get().onCallAdded(call);when(details.getConnectTimeMillis()).thenReturn(NOW-10000);PhoneSessionService.notifyChanged();assertEquals(1,load().entries.size());
   when(call.getState()).thenReturn(Call.STATE_ACTIVE);when(details.getConnectTimeMillis()).thenReturn(NOW-1000);PhoneSessionService.notifyChanged();assertEquals(1,load().entries.size());
   when(details.getConnectTimeMillis()).thenReturn(NOW-2000);PhoneSessionService.notifyChanged();assertTrue(load().entries.isEmpty());
  }finally{controller.destroy();}
 }
 @Test public void reminderListIsReadOnlyAndDoesNotStartACallOnOpen()throws Exception{
  miss(1,FIRST,10);Intent intent=CallbackReminder.openIntent(app,day().key);
  try(ActivityController<CallbackReminderActivity> controller=Robolectric.buildActivity(CallbackReminderActivity.class,intent).setup()){
   CallbackReminderActivity activity=controller.get();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);TextView summary=activity.findViewById(android.R.id.content).findViewWithTag("callback-reminder-summary");
   while(!summary.getText().toString().contains("1 caller · 1 missed call on this day")&&System.nanoTime()<deadline){Thread.sleep(15);shadowOf(Looper.getMainLooper()).idle();}
   assertTrue(summary.getText().toString(),summary.getText().toString().contains("1 caller · 1 missed call on this day"));assertNull(shadowOf(activity).getNextStartedActivity());
   View root=activity.findViewById(android.R.id.content).findViewWithTag("callback-reminder-root");assertNotNull(root);assertFalse(logs.queryOnMain);assertEquals(0,logs.writes);assertEquals(0,people.writes);
  }
 }
 @Test public void activeOrHeldConversationIsNotHiddenByAnotherWaitingCaller()throws Exception{
  miss(1,FIRST,10);miss(2,SECOND,20);Call active=mock(Call.class),waiting=mock(Call.class);
  Call.Details activeDetails=mock(Call.Details.class),waitingDetails=mock(Call.Details.class);
  when(active.getDetails()).thenReturn(activeDetails);when(waiting.getDetails()).thenReturn(waitingDetails);
  when(activeDetails.getHandle()).thenReturn(Uri.fromParts("tel",FIRST,null));when(waitingDetails.getHandle()).thenReturn(Uri.fromParts("tel",SECOND,null));
  when(activeDetails.getHandlePresentation()).thenReturn(TelecomManager.PRESENTATION_ALLOWED);
  when(waitingDetails.getHandlePresentation()).thenReturn(TelecomManager.PRESENTATION_ALLOWED);
  when(active.getState()).thenReturn(Call.STATE_ACTIVE);when(waiting.getState()).thenReturn(Call.STATE_RINGING);
  when(activeDetails.getConnectTimeMillis()).thenReturn(NOW-5000);
  ServiceController<PhoneSessionService> controller=Robolectric.buildService(PhoneSessionService.class).create();
  try{
   controller.get().onCallAdded(active);controller.get().onCallAdded(waiting);assertSame(waiting,PhoneSessionService.current);
   CallbackReminder.Result result=load();assertEquals(1,result.entries.size());assertEquals(SECOND,result.entries.get(0).key);
   when(active.getState()).thenReturn(Call.STATE_HOLDING);PhoneSessionService.notifyChanged();result=load();
   assertEquals(1,result.entries.size());assertEquals(SECOND,result.entries.get(0).key);
   controller.get().onCallRemoved(active);assertEquals(2,load().entries.size());
  }finally{controller.destroy();}
 }
 @Test public void queryCrossingMidnightCannotPostYesterdayReminder()throws Exception{
  enable();miss(1,FIRST,10);long justBeforeMidnight=day().end-500;
  logs.duringQuery=()->org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(2));
  assertFalse(deliver(CallbackReminder.alarmIntent(app,justBeforeMidnight,TimeZone.getDefault()),justBeforeMidnight));
  assertNull(note());assertEquals("",state().getString("notified_day",""));assertEquals(0,logs.writes);
 }
 @Test public void timeZoneChangeDuringQueryCannotPostTheOldLocalDay()throws Exception{
  enable();miss(1,FIRST,10);logs.duringQuery=()->TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));
  assertFalse(deliver());assertNull(note());assertEquals("",state().getString("notified_day",""));assertEquals(0,logs.writes);
 }
 @Test public void movingCutoffLaterStillRefreshesAndResolvesTheExistingSummary()throws Exception{
  enable();miss(1,FIRST,10);miss(2,SECOND,20);assertTrue(deliver());
  prefs().edit().putInt(CallbackReminder.PREF_MINUTE,1439).commit();add(3,FIRST,30,2,2);refresh();
  assertNotNull(note());assertEquals(1,note().extras.getInt(CallbackReminder.EXTRA_PEOPLE));assertTrue((note().flags&Notification.FLAG_ONLY_ALERT_ONCE)!=0);
  add(4,SECOND,40,2,1);refresh();assertNull(note());
  miss(5,FIRST,50);refresh();assertNull("A cancelled same-day summary cannot repost",note());assertEquals(0,logs.writes);
 }
 @Test public void futureCutoffWithoutAnEarlierSummaryDoesNotPostOrReadCalls()throws Exception{
  enable();miss(1,FIRST,10);prefs().edit().putInt(CallbackReminder.PREF_MINUTE,1439).commit();refresh();
  assertNull(note());assertEquals(0,logs.queries);assertEquals("",state().getString("notified_day",""));
 }
 @Test public void openingTheSummaryDuringProviderRefreshCannotRepostIt()throws Exception{
  enable();miss(1,FIRST,10);assertTrue(deliver());logs.duringQuery=()->manager.cancel(CallbackReminder.ID);
  assertFalse(deliver());assertNull(note());assertEquals(0,logs.writes);
 }
}
