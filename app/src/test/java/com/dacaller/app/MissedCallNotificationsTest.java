package com.dacaller.app;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
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
import android.telecom.TelecomManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowContentResolver;

/** Authoritative broadcasts, notification actions, privacy and snapshot-only acknowledgement. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=33) @LooperMode(LooperMode.Mode.PAUSED)
public final class MissedCallNotificationsTest {
 private Application app;private NotificationManager manager;private LogProvider logs;private PeopleProvider people;
 private static final String FIRST="0500099971",SECOND="0500099972";
 public static final class Row {
  final long id,date;final String number;final int type,presentation;int fresh=1,read;
  Row(long id,String number,int type,int presentation){this.id=id;date=100000+id;this.number=number;this.type=type;this.presentation=presentation;}
 }
 public static final class LogProvider extends ContentProvider {
  final List<Row> rows=new ArrayList<>();final List<Long> updated=new ArrayList<>();int queries,deletes;boolean fail;
  volatile boolean queryOnMain;
  public boolean onCreate(){return true;}
  public Cursor query(Uri uri,String[] projection,String selection,String[] arguments,String sort){
   queries++;queryOnMain|=Looper.myLooper()==Looper.getMainLooper();if(fail)throw new IllegalStateException("Synthetic log unavailable");
   assertTrue(selection.contains(CallLog.Calls.TYPE+"=?"));assertTrue(selection.contains(CallLog.Calls.NEW+"=1"));
   assertArrayEquals(new String[]{String.valueOf(CallLog.Calls.MISSED_TYPE)},arguments);
   MatrixCursor result=new MatrixCursor(projection);ArrayList<Row> ordered=new ArrayList<>(rows);
   ordered.sort(Comparator.comparingLong((Row row)->row.date).reversed());
   for(Row row:ordered)if(row.type==CallLog.Calls.MISSED_TYPE&&row.fresh==1){
    Map<String,Object> values=new HashMap<>();values.put(CallLog.Calls._ID,row.id);values.put(CallLog.Calls.NUMBER,row.number);
    values.put(CallLog.Calls.DATE,row.date);values.put(CallLog.Calls.NUMBER_PRESENTATION,row.presentation);
    Object[] selected=new Object[projection.length];for(int n=0;n<projection.length;n++)selected[n]=values.get(projection[n]);result.addRow(selected);
   }
   return result;
  }
  public int update(Uri uri,ContentValues values,String where,String[] arguments){
   assertEquals(2,values.size());assertEquals(Integer.valueOf(0),values.getAsInteger(CallLog.Calls.NEW));
   assertEquals(Integer.valueOf(1),values.getAsInteger(CallLog.Calls.IS_READ));assertTrue(where.contains(CallLog.Calls._ID+" IN ("));
   assertEquals(String.valueOf(CallLog.Calls.MISSED_TYPE),arguments[0]);int changed=0;
   for(int n=1;n<arguments.length;n++)for(Row row:rows)if(row.id==Long.parseLong(arguments[n])&&row.type==CallLog.Calls.MISSED_TYPE){
    row.fresh=0;row.read=1;updated.add(row.id);changed++;
   }
   return changed;
  }
  public int delete(Uri uri,String selection,String[] arguments){deletes++;throw new AssertionError("Missed notifications must never delete calls");}
  public Uri insert(Uri uri,ContentValues values){return null;}public String getType(Uri uri){return null;}
 }
 public static final class PeopleProvider extends ContentProvider {
  final Map<String,String> names=new HashMap<>();int queries;volatile boolean queryOnMain;
  public boolean onCreate(){return true;}
  public Cursor query(Uri uri,String[] projection,String selection,String[] arguments,String sort){
   queries++;queryOnMain|=Looper.myLooper()==Looper.getMainLooper();MatrixCursor result=new MatrixCursor(projection);
   String name=names.get(Uri.decode(uri.getLastPathSegment()));if(name!=null)result.addRow(new Object[]{name});return result;
  }
  public int update(Uri uri,ContentValues values,String where,String[] arguments){throw new AssertionError("Contact data is read-only");}
  public int delete(Uri uri,String selection,String[] arguments){throw new AssertionError("Contact data is read-only");}
  public Uri insert(Uri uri,ContentValues values){throw new AssertionError("Contact data is read-only");}public String getType(Uri uri){return null;}
 }
 @Before public void setup()throws Exception{
  app=RuntimeEnvironment.getApplication();MissedCallNotifications.worker.submit(()->{}).get(10,TimeUnit.SECONDS);
  app.getSharedPreferences("da_caller",0).edit().clear().putString("language","en").commit();state().edit().clear().commit();
  shadowOf(app).grantPermissions(Manifest.permission.READ_CALL_LOG,Manifest.permission.WRITE_CALL_LOG,
   Manifest.permission.READ_CONTACTS,Manifest.permission.POST_NOTIFICATIONS);
  shadowOf(app.getSystemService(TelecomManager.class)).setDefaultDialerPackage(app.getPackageName());
  manager=app.getSystemService(NotificationManager.class);shadowOf(manager).setNotificationsEnabled(true);
  logs=new LogProvider();people=new PeopleProvider();
  ProviderInfo logInfo=new ProviderInfo();logInfo.authority="call_log";logs.attachInfo(app,logInfo);ShadowContentResolver.registerProviderInternal("call_log",logs);
  ProviderInfo peopleInfo=new ProviderInfo();peopleInfo.authority=ContactsContract.AUTHORITY;people.attachInfo(app,peopleInfo);
  ShadowContentResolver.registerProviderInternal(ContactsContract.AUTHORITY,people);
  people.names.put(FIRST,"Dana Example");people.names.put(SECOND,"Noa Example");
 }
 @After public void cleanup()throws Exception{MissedCallNotifications.worker.submit(()->{}).get(10,TimeUnit.SECONDS);}
 private SharedPreferences state(){return app.getSharedPreferences(MissedCallNotifications.PREFERENCES,0);}
 private Row missed(long id,String number){Row row=new Row(id,number,CallLog.Calls.MISSED_TYPE,TelecomManager.PRESENTATION_ALLOWED);logs.rows.add(row);return row;}
 private Intent canonical(int count,String number){
  Intent intent=new Intent(TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION).putExtra(TelecomManager.EXTRA_NOTIFICATION_COUNT,count);
  if(number!=null)intent.putExtra(TelecomManager.EXTRA_NOTIFICATION_PHONE_NUMBER,number);return intent;
 }
 private void process(Intent intent)throws Exception{MissedCallNotifications.worker.submit(()->MissedCallNotifications.receive(app,intent)).get(10,TimeUnit.SECONDS);}
 private void clear(Intent intent)throws Exception{MissedCallNotifications.worker.submit(()->MissedCallNotifications.userClear(app,intent)).get(10,TimeUnit.SECONDS);}
 private Notification notification(){return shadowOf(manager).getNotification(MissedCallNotifications.ID);}
 private Intent clearIntent(){return new Intent(shadowOf(notification().deleteIntent).getSavedIntent());}
 @Test public void canonicalSingleMissedShowsLocalContactWithoutChangingCallData()throws Exception{
  Row row=missed(1,FIRST);process(canonical(1,FIRST));Notification note=notification();assertNotNull(note);
  assertEquals("Missed call",note.extras.getCharSequence(Notification.EXTRA_TITLE).toString());
  assertEquals("Dana Example",note.extras.getCharSequence(Notification.EXTRA_TEXT).toString());
  assertEquals(1,note.extras.getInt(MissedCallNotifications.EXTRA_COUNT));assertEquals(1,note.number);
  assertEquals(MissedCallNotifications.CHANNEL,note.getChannelId());assertEquals(Notification.CATEGORY_MISSED_CALL,note.category);
  assertEquals(1,row.fresh);assertEquals(0,row.read);assertTrue(logs.updated.isEmpty());assertEquals(0,logs.deletes);
 }
 @Test public void canonicalCountIsAnAbsoluteCountAndDuplicatesDoNotAccumulateOrAlertTwice()throws Exception{
  missed(1,FIRST);process(canonical(1,FIRST));String nonce=state().getString("nonce","");
  process(canonical(1,FIRST));assertEquals(nonce,state().getString("nonce",""));
  assertEquals(1,notification().extras.getInt(MissedCallNotifications.EXTRA_COUNT));
  assertEquals(2,state().getInt("platform_events",0));assertTrue((notification().flags&Notification.FLAG_ONLY_ALERT_ONCE)!=0);
 }
 @Test public void multipleMissedCallsUseOneNativeInboxAndRetainAllIdentities()throws Exception{
  missed(1,FIRST);process(canonical(1,FIRST));missed(2,SECOND);process(canonical(2,null));
  Notification note=notification();assertEquals("2 missed calls",note.extras.getCharSequence(Notification.EXTRA_TITLE).toString());
  assertEquals(2,note.number);CharSequence[] lines=note.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
  assertEquals(2,lines.length);assertTrue(lines[0].toString().contains("Noa Example"));assertTrue(lines[1].toString().contains("Dana Example"));
  assertEquals("dacaller.missed",note.getGroup());assertTrue((note.flags&Notification.FLAG_GROUP_SUMMARY)!=0);
  assertEquals(2,logs.rows.size());assertTrue(logs.updated.isEmpty());
 }
 @Test public void underreportedPlatformCounterCannotHideActualUnacknowledgedMissedRows()throws Exception{
  missed(1,FIRST);missed(2,SECOND);process(canonical(1,FIRST));
  Notification note=notification();assertEquals(2,note.extras.getInt(MissedCallNotifications.EXTRA_COUNT));
  assertEquals(1,note.extras.getInt(MissedCallNotifications.EXTRA_PLATFORM_COUNT));assertEquals(2,note.number);
  assertEquals("2 missed calls",note.extras.getCharSequence(Notification.EXTRA_TITLE).toString());
  assertEquals(2,note.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES).length);
  assertEquals("[2,1]",state().getString("snapshot_ids",""));assertTrue(logs.updated.isEmpty());
 }
 @Test public void answeredRejectedAndOutgoingRowsAreNeverPresentedOrAcknowledgedAsMissed()throws Exception{
  Row actual=missed(1,FIRST);
  Row rejected=new Row(2,SECOND,CallLog.Calls.REJECTED_TYPE,TelecomManager.PRESENTATION_ALLOWED);
  Row answered=new Row(3,SECOND,CallLog.Calls.INCOMING_TYPE,TelecomManager.PRESENTATION_ALLOWED);
  Row outgoing=new Row(4,SECOND,CallLog.Calls.OUTGOING_TYPE,TelecomManager.PRESENTATION_ALLOWED);
  logs.rows.add(rejected);logs.rows.add(answered);logs.rows.add(outgoing);process(canonical(1,FIRST));
  assertEquals(1,notification().extras.getInt(MissedCallNotifications.EXTRA_COUNT));
  assertEquals("Dana Example",notification().extras.getCharSequence(Notification.EXTRA_TEXT).toString());clear(clearIntent());
  assertEquals(Arrays.asList(1L),logs.updated);assertEquals(0,actual.fresh);
  assertEquals(1,rejected.fresh);assertEquals(1,answered.fresh);assertEquals(1,outgoing.fresh);
  assertEquals(4,logs.rows.size());assertEquals(0,logs.deletes);
 }
 @Test public void publicLockscreenVersionDoesNotDiscloseCallerNameOrNumber()throws Exception{
  missed(1,FIRST);process(canonical(1,FIRST));Notification note=notification();
  assertEquals(Notification.VISIBILITY_PRIVATE,note.visibility);assertNotNull(note.publicVersion);
  String publicBody=String.valueOf(note.publicVersion.extras);assertFalse(publicBody.contains("Dana Example"));assertFalse(publicBody.contains(FIRST));
 }
 @Test public void actionsAreImmutableAndOpeningUsesActivityWithoutAnyDialRequest()throws Exception{
  missed(1,FIRST);process(canonical(1,FIRST));Notification note=notification();
  assertTrue(shadowOf(note.contentIntent).isActivity());assertTrue(shadowOf(note.contentIntent).isImmutable());
  Intent open=shadowOf(note.contentIntent).getSavedIntent();assertEquals(MainActivity.class.getName(),open.getComponent().getClassName());
  assertEquals(MissedCallNotifications.ACTION_OPEN,open.getAction());assertFalse(open.hasExtra("place_call"));assertFalse(open.hasExtra("phone"));
  assertTrue(shadowOf(note.deleteIntent).isBroadcast());assertTrue(shadowOf(note.deleteIntent).isImmutable());
  assertEquals(MissedCallClearReceiver.class.getName(),shadowOf(note.deleteIntent).getSavedIntent().getComponent().getClassName());
  assertEquals(1,note.actions.length);assertEquals("Clear",note.actions[0].title.toString());
  assertEquals(note.deleteIntent,note.actions[0].actionIntent);
 }
 @Test public void explicitClearMarksOnlyItsCapturedMissedRowsReadAndNeverDeletes()throws Exception{
  Row old=missed(1,FIRST);process(canonical(1,FIRST));Intent intent=clearIntent();clear(intent);
  assertNull(notification());assertEquals(0,state().getInt("count",-1));assertEquals(Arrays.asList(1L),logs.updated);
  assertEquals(0,old.fresh);assertEquals(1,old.read);assertEquals(1,logs.rows.size());assertEquals(0,logs.deletes);
 }
 @Test public void zeroFromTelecomCancelsWithoutSilentlyMarkingAnythingRead()throws Exception{
  Row row=missed(1,FIRST);process(canonical(1,FIRST));process(canonical(0,null));
  assertNull(notification());assertEquals(1,row.fresh);assertEquals(0,row.read);assertTrue(logs.updated.isEmpty());
 }
 @Test public void oldNotificationCannotAcknowledgeANewerGeneration()throws Exception{
  Row first=missed(1,FIRST);process(canonical(1,FIRST));Intent old=clearIntent();String oldNonce=state().getString("nonce","");
  Row second=missed(2,SECOND);process(canonical(2,null));assertNotEquals(oldNonce,state().getString("nonce",""));clear(old);
  assertNotNull(notification());assertEquals(2,notification().extras.getInt(MissedCallNotifications.EXTRA_COUNT));
  assertEquals(1,first.fresh);assertEquals(1,second.fresh);assertTrue(logs.updated.isEmpty());
 }
 @Test public void newlyLoggedCallIsNotChangedByAnEarlierStillValidNotification()throws Exception{
  Row first=missed(1,FIRST);process(canonical(1,FIRST));Intent old=clearIntent();
  Row second=missed(2,SECOND);clear(old); // The new call has been logged; its canonical broadcast is still pending.
  assertEquals(Arrays.asList(1L),logs.updated);assertEquals(0,first.fresh);assertEquals(1,second.fresh);
  assertNotNull(notification());assertEquals(1,notification().extras.getInt(MissedCallNotifications.EXTRA_COUNT));
  assertEquals("Noa Example",notification().extras.getCharSequence(Notification.EXTRA_TEXT).toString());assertEquals(0,logs.deletes);
 }
 @Test public void deniedWritePermissionAcknowledgesLocallyWithoutModifyingOrRepeatingOldRows()throws Exception{
  Row row=missed(1,FIRST);process(canonical(1,FIRST));Intent intent=clearIntent();shadowOf(app).denyPermissions(Manifest.permission.WRITE_CALL_LOG);clear(intent);
  assertNull(notification());assertEquals(1,row.fresh);assertTrue(logs.updated.isEmpty());
  process(canonical(1,FIRST));assertNull(notification());
  missed(2,SECOND);process(canonical(2,null));assertEquals(1,notification().extras.getInt(MissedCallNotifications.EXTRA_COUNT));
  assertEquals("Noa Example",notification().extras.getCharSequence(Notification.EXTRA_TEXT).toString());
 }
 @Test public void deniedNotificationPermissionPreservesUnreadDataAndCanBeGrantedLater()throws Exception{
  Row row=missed(1,FIRST);shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS);process(canonical(1,FIRST));
  assertNull(notification());assertEquals(1,state().getInt("count",0));assertEquals(1,row.fresh);assertTrue(logs.updated.isEmpty());
  shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);process(canonical(1,FIRST));assertNotNull(notification());assertEquals(1,row.fresh);
 }
 @Test public void deniedContactsPermissionFallsBackToNumberWithoutContactProviderAccess()throws Exception{
  missed(1,FIRST);shadowOf(app).denyPermissions(Manifest.permission.READ_CONTACTS);process(canonical(1,FIRST));
  assertEquals(FIRST,notification().extras.getCharSequence(Notification.EXTRA_TEXT).toString());assertEquals(0,people.queries);
 }
 @Test public void restrictedNumbersNeverLeakStoredProviderNumberOrResolveAContact()throws Exception{
  logs.rows.add(new Row(1,FIRST,CallLog.Calls.MISSED_TYPE,TelecomManager.PRESENTATION_RESTRICTED));process(canonical(1,FIRST));
  assertEquals("Private number",notification().extras.getCharSequence(Notification.EXTRA_TEXT).toString());
  assertFalse(String.valueOf(notification().extras).contains(FIRST));assertEquals(0,people.queries);
 }
 @Test public void deniedCallLogPermissionUsesTrustedCanonicalFallbackWithoutQueryingLogs()throws Exception{
  shadowOf(app).denyPermissions(Manifest.permission.READ_CALL_LOG);process(canonical(1,FIRST));
  assertNotNull(notification());assertEquals("Dana Example",notification().extras.getCharSequence(Notification.EXTRA_TEXT).toString());
  assertEquals(0,logs.queries);assertTrue(logs.updated.isEmpty());
 }
 @Test public void providerFailureDoesNotDropTheCanonicalNotificationOrAlterData()throws Exception{
  missed(1,FIRST);logs.fail=true;process(canonical(1,FIRST));assertNotNull(notification());
  assertEquals(1,notification().extras.getInt(MissedCallNotifications.EXTRA_COUNT));assertTrue(logs.updated.isEmpty());assertEquals(0,logs.deletes);
 }
 @Test public void wrongActionNegativeCountAndMissingCountAreIgnored()throws Exception{
  process(new Intent("other").putExtra(TelecomManager.EXTRA_NOTIFICATION_COUNT,1));process(canonical(-1,FIRST));
  process(new Intent(TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION));
  assertNull(notification());assertEquals(0,state().getInt("platform_events",0));assertEquals(0,logs.queries);
 }
 @Test public void nonDefaultDialerDoesNotPostOrTouchAnotherCallsNotification()throws Exception{
  manager.createNotificationChannel(new NotificationChannel("other", "Other",NotificationManager.IMPORTANCE_LOW));
  manager.notify(1502,new Notification.Builder(app,"other").setSmallIcon(android.R.drawable.sym_call_incoming).build());
  shadowOf(app.getSystemService(TelecomManager.class)).setDefaultDialerPackage("other.phone");missed(1,FIRST);process(canonical(1,FIRST));
  assertNull(notification());assertNotNull(shadowOf(manager).getNotification(1502));assertEquals(0,logs.queries);assertTrue(logs.updated.isEmpty());
 }
 @Test public void openingAcknowledgesThroughTheSameValidatedNonceWithoutReplayingOtherIntentExtras()throws Exception{
  missed(1,FIRST);process(canonical(1,FIRST));Intent open=new Intent(shadowOf(notification().contentIntent).getSavedIntent());
  open.putExtra("place_call","0500099999");assertTrue(MissedCallNotifications.handleOpenIntent(app,open));
  MissedCallNotifications.worker.submit(()->{}).get(10,TimeUnit.SECONDS);assertNull(notification());assertEquals(Arrays.asList(1L),logs.updated);
  assertFalse(MissedCallNotifications.handleOpenIntent(app,new Intent(Intent.ACTION_DIAL)));assertEquals(0,logs.deletes);
 }
 @Test public void realBroadcastReceiverRunsAllProviderLookupsOffTheMainLooper()throws Exception{
  missed(1,FIRST);new MissedCallNotificationReceiver().onReceive(app,canonical(1,FIRST));
  MissedCallNotifications.worker.submit(()->{}).get(10,TimeUnit.SECONDS);assertNotNull(notification());
  assertFalse(logs.queryOnMain);assertFalse(people.queryOnMain);
 }
}
