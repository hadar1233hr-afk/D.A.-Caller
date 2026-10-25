package com.dacaller.app;

import android.Manifest;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Build;
import android.os.Looper;
import android.provider.ContactsContract;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.InCallService;
import android.telecom.TelecomManager;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowContentResolver;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.robolectric.Shadows.shadowOf;

/** Exercises actual notification PendingIntents and the acknowledged Telecom route boundary. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=33) @LooperMode(LooperMode.Mode.PAUSED)
public class CallNotificationsTest {
 private ServiceController<PhoneSessionService> controller;
 private PhoneSessionService service;
 private NotificationManager manager;
 private Application app;
 @Before public void setup(){
  app=RuntimeEnvironment.getApplication();
  app.getSharedPreferences("da_caller",0).edit().clear().putString("language","en").commit();
  shadowOf(app).denyPermissions(Manifest.permission.READ_CONTACTS);
  ContactPhotos.clearCache();
  controller=Robolectric.buildService(PhoneSessionService.class).create();service=controller.get();
  manager=service.getSystemService(NotificationManager.class);
  shadowOf(manager).setNotificationsEnabled(true);
  audio(CallAudioState.ROUTE_EARPIECE,CallAudioState.ROUTE_EARPIECE|CallAudioState.ROUTE_SPEAKER);
 }
 @After public void cleanup(){
  if(controller!=null)controller.destroy();PhoneSessionService.current=null;PhoneSessionService.instance=null;
  ContactPhotos.clearCache();
 }
 private void audio(int route,int supported){
  shadowOf((InCallService)service).setSupportedRouteMask(supported);service.setAudioRoute(route);
 }
 private Notification notification(){return shadowOf(manager).getNotification(1502);}
 private Call call(int state,String number,String carrier){
  Call call=mock(Call.class);Call.Details details=mock(Call.Details.class);
  when(call.getState()).thenReturn(state);when(call.getDetails()).thenReturn(details);
  when(details.getHandle()).thenReturn(Uri.fromParts("tel",number,null));
  when(details.getHandlePresentation()).thenReturn(TelecomManager.PRESENTATION_ALLOWED);
  when(details.getCallerDisplayName()).thenReturn(carrier);
  when(details.getCallerDisplayNamePresentation()).thenReturn(TelecomManager.PRESENTATION_ALLOWED);
  return call;
 }
 private String person(){
  if(Build.VERSION.SDK_INT<31)return String.valueOf(notification().extras.getCharSequence(Notification.EXTRA_TITLE));
  Person person=notification().extras.getParcelable(Notification.EXTRA_CALL_PERSON);return person.getName().toString();
 }
 private Notification.Action action(String suffix){
  for(Notification.Action action:notification().actions){
   Intent intent=shadowOf(action.actionIntent).getSavedIntent();
   if(("com.dacaller.app.CALL_"+suffix).equals(intent.getAction()))return action;
  }
  return null;
 }
 private void send(Notification.Action action)throws Exception{
  assertNotNull(action);action.actionIntent.send();shadowOf(Looper.getMainLooper()).idle();
 }
 @Test public void ongoingNotificationNamesCallerAndOffersThreeRealActions()throws Exception{
  Call call=call(Call.STATE_ACTIVE,"0500099000","Dana Example");service.onCallAdded(call);
  assertEquals("Dana Example",person());assertEquals(Notification.CATEGORY_CALL,notification().category);
  assertTrue((notification().flags&Notification.FLAG_ONGOING_EVENT)!=0);
  assertEquals(3,notification().actions.length);assertNotNull(action("HANGUP"));assertNotNull(action("SPEAKER_ON"));
  Notification.Action returnAction=null;
  for(Notification.Action action:notification().actions)if("Return to call".contentEquals(action.title))returnAction=action;
  assertNotNull(returnAction);assertEquals(notification().contentIntent,returnAction.actionIntent);
  Intent intent=shadowOf(returnAction.actionIntent).getSavedIntent();
  assertEquals(CallScreenActivity.class.getName(),intent.getComponent().getClassName());
  assertTrue((intent.getFlags()&Intent.FLAG_ACTIVITY_NEW_TASK)!=0);
  assertEquals("dacaller-call-screen",intent.getData().getScheme());
  send(action("HANGUP"));verify(call).disconnect();
 }
 @Test public void outgoingAndIncomingAreDistinctAndBackgroundRingingKeepsAnswerDecline(){
  Call outgoing=call(Call.STATE_DIALING,"0500099000","Dana");service.onCallAdded(outgoing);
  assertEquals("Outgoing call",notification().extras.getCharSequence(Notification.EXTRA_SUB_TEXT));
  assertNull(notification().fullScreenIntent);assertNotNull(action("HANGUP"));assertNotNull(action("SPEAKER_ON"));
  service.onCallRemoved(outgoing);
  Call incoming=call(Call.STATE_RINGING,"0500099001","Alex");service.onCallAdded(incoming);
  assertEquals("Incoming call",notification().extras.getCharSequence(Notification.EXTRA_SUB_TEXT));assertEquals("Alex",person());
  assertNotNull(notification().fullScreenIntent);assertNotNull(action("ANSWER"));assertNotNull(action("DECLINE"));
  assertNull(action("SPEAKER_ON"));assertNull(action("HANGUP"));assertEquals(3,notification().actions.length);
 }
 @Test public void speakerActionsUseActualRouteAndRefreshOnlyAfterAudioCallback()throws Exception{
  Call call=call(Call.STATE_ACTIVE,"0500099000","Dana");service.onCallAdded(call);
  Notification.Action on=action("SPEAKER_ON");assertNotNull(on);send(on);
  assertEquals(CallAudioState.ROUTE_SPEAKER,service.getCallAudioState().getRoute());
  assertNotNull("A request is not an audio-state acknowledgement",action("SPEAKER_ON"));assertNull(action("SPEAKER_OFF"));
  service.onCallAudioStateChanged(service.getCallAudioState());assertNotNull(action("SPEAKER_OFF"));assertNull(action("SPEAKER_ON"));
  send(on);assertEquals("A delayed explicit ON must never toggle speaker off",CallAudioState.ROUTE_SPEAKER,service.getCallAudioState().getRoute());
  send(action("SPEAKER_OFF"));assertEquals(CallAudioState.ROUTE_EARPIECE,service.getCallAudioState().getRoute());
  service.onCallAudioStateChanged(service.getCallAudioState());assertNotNull(action("SPEAKER_ON"));
 }
 @Test public void speakerOffUsesWiredRouteAndStaleOffCannotReplaceLaterBluetooth()throws Exception{
  audio(CallAudioState.ROUTE_SPEAKER,CallAudioState.ROUTE_SPEAKER|CallAudioState.ROUTE_WIRED_HEADSET|CallAudioState.ROUTE_BLUETOOTH);
  service.onCallAdded(call(Call.STATE_ACTIVE,"0500099000","Dana"));Notification.Action off=action("SPEAKER_OFF");
  send(off);assertEquals(CallAudioState.ROUTE_WIRED_HEADSET,service.getCallAudioState().getRoute());
  audio(CallAudioState.ROUTE_BLUETOOTH,CallAudioState.ROUTE_SPEAKER|CallAudioState.ROUTE_WIRED_HEADSET|CallAudioState.ROUTE_BLUETOOTH);
  send(off);assertEquals(CallAudioState.ROUTE_BLUETOOTH,service.getCallAudioState().getRoute());
 }
 @Test public void endedCallActionsCannotRouteOrDisconnectItsSuccessor()throws Exception{
  Call old=call(Call.STATE_ACTIVE,"0500099000","Dana");service.onCallAdded(old);
  Notification.Action hangup=action("HANGUP"),speaker=action("SPEAKER_ON");
  service.onCallRemoved(old);Call next=call(Call.STATE_ACTIVE,"0500099001","Alex");service.onCallAdded(next);
  send(hangup);send(speaker);verify(next,never()).disconnect();verify(old,never()).disconnect();
  assertEquals(CallAudioState.ROUTE_EARPIECE,service.getCallAudioState().getRoute());
 }
 @Test public void unsupportedOrChangedStateSpeakerActionsHaveNoEffect()throws Exception{
  Call call=call(Call.STATE_ACTIVE,"0500099000","Dana");service.onCallAdded(call);Notification.Action old=action("SPEAKER_ON");
  when(call.getState()).thenReturn(Call.STATE_RINGING);send(old);assertEquals(CallAudioState.ROUTE_EARPIECE,service.getCallAudioState().getRoute());
  when(call.getState()).thenReturn(Call.STATE_ACTIVE);audio(CallAudioState.ROUTE_EARPIECE,CallAudioState.ROUTE_EARPIECE);
  PhoneSessionService.notifyChanged();assertNull(action("SPEAKER_ON"));send(old);assertEquals(CallAudioState.ROUTE_EARPIECE,service.getCallAudioState().getRoute());
  audio(CallAudioState.ROUTE_SPEAKER,CallAudioState.ROUTE_SPEAKER);PhoneSessionService.notifyChanged();assertNull(action("SPEAKER_OFF"));
 }
 @Test public void privatePresentationNeverRevealsNumberOrContactName(){
  shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS);
  Call call=call(Call.STATE_RINGING,"0500099000","Confidential name");
  when(call.getDetails().getHandlePresentation()).thenReturn(TelecomManager.PRESENTATION_RESTRICTED);
  when(call.getDetails().getContactDisplayName()).thenReturn("Saved confidential contact");
  service.onCallAdded(call);assertEquals("Private number",person());
  assertFalse(notification().extras.toString().contains("0500099000"));
  assertFalse(notification().extras.toString().contains("Confidential"));
 }
 @Test public void localContactNameIsResolvedOffThreadAndRevocationDropsIt()throws Exception{
  NameProvider provider=provider();shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS);
  Call call=call(Call.STATE_ACTIVE,"0500099000",null);service.onCallAdded(call);
  await(()->"Saved Dana".equals(person()));
  assertNotEquals("Contact query must not block the UI thread",Looper.getMainLooper().getThread(),provider.queryThread);
  shadowOf(app).denyPermissions(Manifest.permission.READ_CONTACTS);PhoneSessionService.setScreenVisible(false);
  assertEquals("0500099000",person());
 }
 @Test public void lateContactLookupForRemovedCallCannotRenameNewCall()throws Exception{
  NameProvider provider=provider();provider.release=new CountDownLatch(1);shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS);
  Call old=call(Call.STATE_ACTIVE,"0500099000",null);service.onCallAdded(old);
  assertTrue(provider.started.await(3,TimeUnit.SECONDS));service.onCallRemoved(old);
  Call next=call(Call.STATE_ACTIVE,"0500099001","Current Alex");service.onCallAdded(next);
  java.util.concurrent.atomic.AtomicBoolean delivered=new java.util.concurrent.atomic.AtomicBoolean();
  ContactPhotos.lookup(app,"0500099000",(id,name,image)->delivered.set(true));
  provider.release.countDown();await(delivered::get);
  shadowOf(Looper.getMainLooper()).idle();assertEquals("Current Alex",person());
 }
 @Test @Config(sdk=26) public void android26HasNativeFallbackAndAllOngoingActions(){
  service.onCallAdded(call(Call.STATE_ACTIVE,"0500099000","Dana"));
  assertEquals("Dana",person());assertEquals(3,notification().actions.length);
  assertNotNull(action("HANGUP"));assertNotNull(action("SPEAKER_ON"));assertNull(notification().fullScreenIntent);
 }
 @Test public void enabledIncomingNotificationUsesFullscreenWithoutAnExtraActivityLaunch(){
  service.onCallAdded(call(Call.STATE_RINGING,"0500099000","Dana"));
  assertNotNull(notification().fullScreenIntent);assertNull(shadowOf(service).getNextStartedActivity());
 }
 @Test public void globallyBlockedIncomingNotificationsOpenOwnCallScreenOnlyOnce(){
  shadowOf(manager).setNotificationsEnabled(false);
  Call call=call(Call.STATE_RINGING,"0500099000","Dana");service.onCallAdded(call);
  Intent screen=shadowOf(service).getNextStartedActivity();assertNotNull(screen);
  assertEquals(CallScreenActivity.class.getName(),screen.getComponent().getClassName());
  assertEquals(service.getPackageName(),screen.getComponent().getPackageName());
  assertTrue((screen.getFlags()&Intent.FLAG_ACTIVITY_NEW_TASK)!=0);
  service.onCallAdded(call);PhoneSessionService.notifyChanged();service.onCallAudioStateChanged(service.getCallAudioState());
  assertNull("Notification refresh must not keep opening the call screen",shadowOf(service).getNextStartedActivity());
  verify(call,never()).answer(anyInt());verify(call,never()).disconnect();
 }
 @Test public void blockedIncomingChannelFallsBackWithoutRequestingADialerRole(){
  manager.createNotificationChannel(new NotificationChannel(CallNotifications.INCOMING_CHANNEL,"Blocked test calls",NotificationManager.IMPORTANCE_NONE));
  android.app.role.RoleManager roles=service.getSystemService(android.app.role.RoleManager.class);
  assertFalse(roles.isRoleHeld(android.app.role.RoleManager.ROLE_DIALER));
  service.onCallAdded(call(Call.STATE_RINGING,"0500099000","Dana"));
  Intent screen=shadowOf(service).getNextStartedActivity();assertNotNull(screen);
  assertEquals(CallScreenActivity.class.getName(),screen.getComponent().getClassName());
  assertNull("No permission or role prompt is launched by the service",shadowOf(service).getNextStartedActivity());
 }
 private NameProvider provider(){
  NameProvider provider=new NameProvider();ProviderInfo info=new ProviderInfo();info.authority=ContactsContract.AUTHORITY;
  provider.attachInfo(app,info);ShadowContentResolver.registerProviderInternal(ContactsContract.AUTHORITY,provider);return provider;
 }
 private void await(BooleanSupplier condition)throws Exception{
  long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
  while(!condition.getAsBoolean()&&System.nanoTime()<until){shadowOf(Looper.getMainLooper()).idle();Thread.yield();}
  shadowOf(Looper.getMainLooper()).idle();assertTrue("Timed out waiting for notification identity",condition.getAsBoolean());
 }
 private static final class NameProvider extends ContentProvider {
  final CountDownLatch started=new CountDownLatch(1);volatile CountDownLatch release=new CountDownLatch(0);
  volatile Thread queryThread;volatile boolean finished;
  public boolean onCreate(){return true;}
  public Cursor query(Uri uri,String[] columns,String selection,String[] arguments,String sort){
   MatrixCursor cursor=new MatrixCursor(columns);
   if(uri.toString().startsWith(ContactsContract.PhoneLookup.CONTENT_FILTER_URI.toString())){
    queryThread=Thread.currentThread();started.countDown();
    if("0500099000".equals(uri.getLastPathSegment())){
     try{if(!release.await(3,TimeUnit.SECONDS))throw new IllegalStateException("Delayed lookup timed out");}
     catch(InterruptedException interrupted){Thread.currentThread().interrupt();return cursor;}
     cursor.addRow(new Object[]{77L,"Saved Dana"});finished=true;
    }
   }
   return cursor;
  }
  public String getType(Uri uri){return null;}
  public Uri insert(Uri uri,ContentValues values){return null;}
  public int update(Uri uri,ContentValues values,String selection,String[] args){return 0;}
  public int delete(Uri uri,String selection,String[] args){return 0;}
 }
}
