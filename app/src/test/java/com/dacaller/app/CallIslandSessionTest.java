package com.dacaller.app;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Looper;
import android.provider.ContactsContract;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.InCallService;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
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
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.robolectric.Shadows.shadowOf;

/** The production island crosses the same owned-call boundary as the Telecom notification. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=33) @LooperMode(LooperMode.Mode.PAUSED)
public class CallIslandSessionTest {
 private Application app;
 private PhoneSessionService service;
 private ServiceController<PhoneSessionService> controller;
 private final CallIslandSession session=CallIslandSession.telecom();
 @Before public void setup(){
  app=RuntimeEnvironment.getApplication();
  app.getSharedPreferences("da_caller",0).edit().clear().putString("language","en").commit();
  shadowOf(app).denyPermissions(Manifest.permission.READ_CONTACTS);ContactPhotos.clearCache();
  controller=Robolectric.buildService(PhoneSessionService.class).create();service=controller.get();
  audio(CallAudioState.ROUTE_EARPIECE,CallAudioState.ROUTE_EARPIECE|CallAudioState.ROUTE_SPEAKER);
 }
 @After public void cleanup(){
  if(controller!=null)controller.destroy();
  PhoneSessionService.current=null;PhoneSessionService.instance=null;ContactPhotos.clearCache();
 }
 private void audio(int route,int supported){
  shadowOf((InCallService)service).setSupportedRouteMask(supported);service.setAudioRoute(route);
 }
 private Call call(int state,String number,String name){
  Call call=mock(Call.class);Call.Details details=mock(Call.Details.class);
  when(call.getState()).thenReturn(state);when(call.getDetails()).thenReturn(details);
  when(details.getHandle()).thenReturn(Uri.fromParts("tel",number,null));
  when(details.getHandlePresentation()).thenReturn(TelecomManager.PRESENTATION_ALLOWED);
  when(details.getCallerDisplayName()).thenReturn(name);
  when(details.getCallerDisplayNamePresentation()).thenReturn(TelecomManager.PRESENTATION_ALLOWED);
  return call;
 }
 private String add(Call call){service.onCallAdded(call);CallIslandState current=session.current();assertNotNull(current);return current.token;}
 @Test public void snapshotsRequireOwnedLivingCallAndStopAfterServiceDestruction(){
  assertNull(session.current());
  PhoneSessionService.current=call(Call.STATE_ACTIVE,"0500099000","Unowned");assertNull(session.current());
  Call active=call(Call.STATE_ACTIVE,"0500099001","Dana");String token=add(active);
  assertFalse(token.isEmpty());
  when(active.getState()).thenReturn(Call.STATE_DISCONNECTING);assertNull(session.current());assertFalse(session.perform(token,"END"));
  when(active.getState()).thenReturn(Call.STATE_DISCONNECTED);assertNull(session.current());
  controller.destroy();controller=null;assertNull(session.current());assertFalse(session.perform(token,"ANSWER"));
 }
 @Test public void snapshotShowsPermissionScopedIdentityAudioTimerAndCurrentHoldCapability(){
  Call call=call(Call.STATE_ACTIVE,"0500099000","Dana Example");
  when(call.getDetails().can(Call.Details.CAPABILITY_HOLD)).thenReturn(true);
  when(call.getDetails().getConnectTimeMillis()).thenReturn(1700000000000L);String token=add(call);
  CallIslandState state=session.current();
  assertEquals(token,state.token);assertEquals("Dana Example",state.name);assertEquals("0500099000",state.number);
  assertEquals(Call.STATE_ACTIVE,state.state);assertEquals(CallAudioState.ROUTE_EARPIECE,state.route);
  assertEquals(CallAudioState.ROUTE_EARPIECE|CallAudioState.ROUTE_SPEAKER,state.supportedRoutes);
  assertFalse(state.muted);assertTrue(state.canMute);assertTrue(state.canHold);assertEquals(-1,state.contactId);
  assertEquals(1700000000000L,state.connectedAtMillis);
  audio(CallAudioState.ROUTE_SPEAKER,CallAudioState.ROUTE_SPEAKER);
  assertEquals("A captured presentation is immutable",CallAudioState.ROUTE_EARPIECE,state.route);
  assertEquals(CallAudioState.ROUTE_SPEAKER,session.current().route);
 }
 @Test public void staleIslandCannotChangeAnswerOrEndAReplacementOrWaitingCall(){
  Call old=call(Call.STATE_ACTIVE,"0500099000","Dana");String oldToken=add(old);
  Call waiting=call(Call.STATE_RINGING,"0500099001","Alex");String waitingToken=add(waiting);
  assertNotEquals(oldToken,waitingToken);
  for(String action:new String[]{"END","ANSWER","DECLINE","MUTE_ON","SPEAKER_ON","HOLD","RESUME"})assertFalse(session.perform(oldToken,action));
  verify(old,never()).disconnect();verify(waiting,never()).disconnect();verify(waiting,never()).answer(anyInt());
  verify(waiting,never()).reject(anyBoolean(),any());
  service.onCallRemoved(waiting);assertEquals(oldToken,session.current().token);
  service.onCallRemoved(old);Call next=call(Call.STATE_ACTIVE,"0500099002","Noa");add(next);
  assertFalse(session.perform(oldToken,"END"));assertFalse(session.perform(waitingToken,"END"));verify(next,never()).disconnect();
  assertEquals(CallAudioState.ROUTE_EARPIECE,service.getCallAudioState().getRoute());
 }
 @Test public void incomingOffersOnlyAudioAnswerAndDeclineInsteadOfOngoingActions(){
  Call incoming=call(Call.STATE_RINGING,"0500099000","Dana");String token=add(incoming);
  assertFalse(session.current().canMute);assertFalse(session.current().canHold);
  for(String action:new String[]{"END","MUTE_ON","MUTE_OFF","SPEAKER_ON","SPEAKER_OFF","HOLD","RESUME"})assertFalse(session.perform(token,action));
  assertTrue(session.perform(token,"ANSWER"));verify(incoming).answer(VideoProfile.STATE_AUDIO_ONLY);
  assertTrue(session.perform(token,"DECLINE"));verify(incoming).reject(false,null);verify(incoming,never()).disconnect();
 }
 @Test public void ongoingEndsOnlyBoundCallAndRejectsIncomingAndUnknownCommands(){
  Call active=call(Call.STATE_DIALING,"0500099000","Dana");String token=add(active);
  for(String action:new String[]{"ANSWER","DECLINE",null,"PLACE_CALL",""})assertFalse(session.perform(token,action));
  assertFalse(session.perform(null,"END"));assertFalse(session.perform("","END"));
  assertTrue(session.perform(token,"END"));verify(active).disconnect();
  verify(active,never()).answer(anyInt());verify(active,never()).reject(anyBoolean(),any());
  when(active.getState()).thenReturn(Call.STATE_DISCONNECTED);assertFalse(session.perform(token,"END"));
 }
 @Test public void holdAndResumeRequireLiveStateAndActionCapabilityNotOnlySupport(){
  Call call=call(Call.STATE_ACTIVE,"0500099000","Dana");String token=add(call);
  when(call.getDetails().can(Call.Details.CAPABILITY_SUPPORT_HOLD)).thenReturn(true);
  assertFalse(session.current().canHold);assertFalse(session.perform(token,"HOLD"));assertFalse(session.perform(token,"RESUME"));
  when(call.getDetails().can(Call.Details.CAPABILITY_HOLD)).thenReturn(true);
  assertTrue(session.current().canHold);assertTrue(session.perform(token,"HOLD"));verify(call).hold();verify(call,never()).unhold();
  when(call.getState()).thenReturn(Call.STATE_HOLDING);
  assertTrue(session.current().canHold);assertFalse(session.perform(token,"HOLD"));assertTrue(session.perform(token,"RESUME"));verify(call).unhold();
  when(call.getState()).thenReturn(Call.STATE_RINGING);assertFalse(session.current().canHold);assertFalse(session.perform(token,"HOLD"));
  when(call.getState()).thenReturn(Call.STATE_HOLDING);when(call.getDetails().can(Call.Details.CAPABILITY_HOLD)).thenReturn(false);
  assertFalse(session.perform(token,"RESUME"));
 }
 @Test public void muteIsExplicitAndPresentationWaitsForActualAudioState(){
  Call call=call(Call.STATE_ACTIVE,"0500099000","Dana");String token=add(call);
  PhoneSessionService observed=spy(service);PhoneSessionService.instance=observed;
  int supported=CallAudioState.ROUTE_EARPIECE|CallAudioState.ROUTE_SPEAKER;
  doReturn(new CallAudioState(false,CallAudioState.ROUTE_EARPIECE,supported)).when(observed).getCallAudioState();
  assertTrue(session.perform(token,"MUTE_ON"));verify(observed).setMuted(true);
  assertFalse("Sending a command must not invent an acknowledged state",session.current().muted);
  CallAudioState acknowledged=new CallAudioState(true,CallAudioState.ROUTE_EARPIECE,supported);
  doReturn(acknowledged).when(observed).getCallAudioState();observed.onCallAudioStateChanged(acknowledged);
  assertTrue(session.current().muted);assertFalse(session.perform(token,"MUTE_ON"));
  assertTrue(session.perform(token,"MUTE_OFF"));verify(observed).setMuted(false);
  assertTrue("Unmute also waits for actual state",session.current().muted);
  when(call.getState()).thenReturn(Call.STATE_RINGING);assertFalse(session.perform(token,"MUTE_OFF"));
 }
 @Test public void speakerCommandsRespectActualSupportedRouteAndDoNotInvertLaterBluetooth(){
  Call call=call(Call.STATE_ACTIVE,"0500099000","Dana");String token=add(call);
  assertFalse(session.perform(token,"SPEAKER_OFF"));assertTrue(session.perform(token,"SPEAKER_ON"));
  assertEquals(CallAudioState.ROUTE_SPEAKER,service.getCallAudioState().getRoute());assertFalse(session.perform(token,"SPEAKER_ON"));
  audio(CallAudioState.ROUTE_SPEAKER,CallAudioState.ROUTE_SPEAKER|CallAudioState.ROUTE_WIRED_HEADSET|CallAudioState.ROUTE_BLUETOOTH);
  assertTrue(session.perform(token,"SPEAKER_OFF"));assertEquals(CallAudioState.ROUTE_WIRED_HEADSET,service.getCallAudioState().getRoute());
  audio(CallAudioState.ROUTE_BLUETOOTH,CallAudioState.ROUTE_SPEAKER|CallAudioState.ROUTE_BLUETOOTH);
  assertFalse(session.perform(token,"SPEAKER_OFF"));assertEquals(CallAudioState.ROUTE_BLUETOOTH,service.getCallAudioState().getRoute());
  audio(CallAudioState.ROUTE_EARPIECE,CallAudioState.ROUTE_EARPIECE);assertFalse(session.perform(token,"SPEAKER_ON"));
 }
 @Test public void unavailableAudioAndProviderExceptionsDisablePresentationWithoutLosingCall(){
  Call call=call(Call.STATE_ACTIVE,"0500099000","Dana");String token=add(call);
  PhoneSessionService observed=spy(service);PhoneSessionService.instance=observed;doReturn(null).when(observed).getCallAudioState();
  assertFalse(session.current().canMute);assertEquals(0,session.current().supportedRoutes);
  assertFalse(session.perform(token,"MUTE_ON"));assertFalse(session.perform(token,"SPEAKER_ON"));
  when(call.getDetails()).thenThrow(new IllegalStateException("OEM details temporarily unavailable"));
  assertNull(session.current());assertSame(call,PhoneSessionService.current);assertFalse(session.perform(token,"HOLD"));
  verify(call,never()).disconnect();
 }
 @Test public void restrictedAndUnknownPresentationCannotExposeNumberPhotoOrName(){
  shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS);
  Call call=call(Call.STATE_RINGING,"0500099000","Confidential carrier name");
  when(call.getDetails().getContactDisplayName()).thenReturn("Confidential local name");
  when(call.getDetails().getHandlePresentation()).thenReturn(TelecomManager.PRESENTATION_RESTRICTED);add(call);
  assertEquals("Private number",session.current().name);assertEquals("",session.current().number);assertEquals(-1,session.current().contactId);
  when(call.getDetails().getHandlePresentation()).thenReturn(TelecomManager.PRESENTATION_UNKNOWN);
  assertEquals("Unknown caller",session.current().name);assertEquals("",session.current().number);assertEquals(-1,session.current().contactId);
 }
 @Test public void localIdentityRefreshNotifiesIslandOffThreadAndRevocationDropsPhotoAndName()throws Exception{
  NameProvider provider=provider();shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS);
  AtomicInteger updates=new AtomicInteger();Runnable listener=updates::incrementAndGet;PhoneSessionService.register(listener);
  try{
   Call call=call(Call.STATE_ACTIVE,"0500099000",null);add(call);int initialUpdates=updates.get();
   await(()->session.current()!=null&&session.current().contactId==77);
   assertEquals("Saved Dana",session.current().name);assertTrue(updates.get()>initialUpdates);
   assertNotEquals(Looper.getMainLooper().getThread(),provider.queryThread);
   shadowOf(app).denyPermissions(Manifest.permission.READ_CONTACTS);
   assertEquals("0500099000",session.current().name);assertEquals(-1,session.current().contactId);
  }finally{PhoneSessionService.unregister(listener);}
 }
 @Test public void lateProviderResultCannotRenameAnIslandForAReplacementCall()throws Exception{
  NameProvider provider=provider();provider.release=new CountDownLatch(1);shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS);
  Call old=call(Call.STATE_ACTIVE,"0500099000",null);String oldToken=add(old);
  assertTrue(provider.started.await(3,TimeUnit.SECONDS));service.onCallRemoved(old);
  Call next=call(Call.STATE_ACTIVE,"0500099001","Current Alex");String nextToken=add(next);
  AtomicBoolean delivered=new AtomicBoolean();
  ContactPhotos.lookup(app,"0500099000",(id,name,image)->delivered.set(true));
  provider.release.countDown();await(delivered::get);shadowOf(Looper.getMainLooper()).idle();
  assertEquals(nextToken,session.current().token);assertNotEquals(oldToken,session.current().token);
  assertEquals("Current Alex",session.current().name);assertEquals(-1,session.current().contactId);
 }
 @Test public void openUsesExplicitOwnScreenAndPreservesCurrentActivityStackWithoutDialing(){
  Call call=call(Call.STATE_ACTIVE,"0500099000","Dana");String token=add(call);
  ActivityController<Activity> host=Robolectric.buildActivity(Activity.class).setup();
  try{
   // onCallAdded already opens the outgoing call UI. Robolectric shares that
   // started-Activity queue with this host; isolate the island request below.
   while(shadowOf(host.get()).getNextStartedActivity()!=null){}
   session.open(host.get(),"stale");assertNull(shadowOf(host.get()).getNextStartedActivity());
   session.open(host.get(),token);Intent open=shadowOf(host.get()).getNextStartedActivity();assertNotNull(open);
   assertEquals(CallScreenActivity.class.getName(),open.getComponent().getClassName());
   assertEquals("dacaller-call-screen",open.getData().getScheme());assertTrue(open.getData().toString().contains(token));
   assertNull(open.getAction());assertEquals(Intent.FLAG_ACTIVITY_SINGLE_TOP,open.getFlags());assertNull(open.getExtras());
   verify(call,never()).disconnect();verify(call,never()).answer(anyInt());
   when(call.getState()).thenReturn(Call.STATE_DISCONNECTED);session.open(host.get(),token);assertNull(shadowOf(host.get()).getNextStartedActivity());
  }finally{host.pause().stop().destroy();}
 }
 private NameProvider provider(){
  NameProvider provider=new NameProvider();ProviderInfo info=new ProviderInfo();info.authority=ContactsContract.AUTHORITY;
  provider.attachInfo(app,info);ShadowContentResolver.registerProviderInternal(ContactsContract.AUTHORITY,provider);return provider;
 }
 private void await(BooleanSupplier condition)throws Exception{
  long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
  while(!condition.getAsBoolean()&&System.nanoTime()<until){shadowOf(Looper.getMainLooper()).idle();Thread.yield();}
  shadowOf(Looper.getMainLooper()).idle();assertTrue("Timed out waiting for local contact lookup",condition.getAsBoolean());
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
