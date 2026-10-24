package com.dacaller.app;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.PowerManager;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.VideoProfile;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowPowerManager;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=33)
public class PhoneSessionServiceTest {
 private ServiceController<PhoneSessionService> controller;
 private PhoneSessionService service;
 private NotificationManager manager;
 @Before public void create(){
  controller=Robolectric.buildService(PhoneSessionService.class);service=controller.get();
  shadowOf(service.getSystemService(PowerManager.class)).setIsWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,true);
  controller.create();
  manager=service.getSystemService(NotificationManager.class);
 }
 @After public void destroy(){if(controller!=null)controller.destroy();PhoneSessionService.current=null;}
 private Call call(int state){Call call=mock(Call.class);when(call.getState()).thenReturn(state);return call;}
 private Notification notification(){return shadowOf(manager).getNotification(1502);}
 @Test public void visibleIncomingScreenUsesQuietNotificationWithoutFullscreen(){
  Call incoming=call(Call.STATE_RINGING);service.onCallAdded(incoming);
  assertNotNull(notification().fullScreenIntent);
  PhoneSessionService.setScreenVisible(true);
  assertNull(notification().fullScreenIntent);
  assertEquals(NotificationManager.IMPORTANCE_LOW,manager.getNotificationChannel(notification().getChannelId()).getImportance());
  ArgumentCaptor<Call.Callback> captor=ArgumentCaptor.forClass(Call.Callback.class);
  verify(incoming).registerCallback(captor.capture());
  captor.getValue().onDetailsChanged(incoming,null);
  assertNull(notification().fullScreenIntent);
 }
 @Test public void removingSecondCallRestoresFirstAndKeepsNotification(){
  Call active=call(Call.STATE_ACTIVE),incoming=call(Call.STATE_RINGING);
  service.onCallAdded(active);service.onCallAdded(incoming);assertSame(incoming,PhoneSessionService.current);
  service.onCallRemoved(incoming);assertSame(active,PhoneSessionService.current);assertNotNull(notification());
  service.onCallRemoved(active);assertNull(PhoneSessionService.current);assertNull(notification());
 }
 @Test public void delayedNotificationAnswerCannotAnswerAnotherCall(){
  Call old=call(Call.STATE_RINGING);service.onCallAdded(old);
  Intent answer=null;
  for(Notification.Action action:notification().actions){
   Intent saved=shadowOf(action.actionIntent).getSavedIntent();
   if("com.dacaller.app.CALL_ANSWER".equals(saved.getAction()))answer=saved;
  }
  assertNotNull(answer);
  service.onCallRemoved(old);
  Call next=call(Call.STATE_RINGING);service.onCallAdded(next);
  new CallCommandReceiver().onReceive(service,answer);
  verify(next,never()).answer(anyInt());
  for(Notification.Action action:notification().actions){
   Intent saved=shadowOf(action.actionIntent).getSavedIntent();
   if("com.dacaller.app.CALL_ANSWER".equals(saved.getAction()))new CallCommandReceiver().onReceive(service,saved);
  }
  verify(next).answer(VideoProfile.STATE_AUDIO_ONLY);
 }
 @Test public void providerFailureCannotCrashInCallBindingOrForceSystemFallback(){
  // A real OEM may throw while fetching Telecom details during early binding.
  Call dialing=call(Call.STATE_DIALING);
  when(dialing.getDetails()).thenThrow(new IllegalStateException("Simulated OEM Telecom failure"));
  service.onCallAdded(dialing);
  assertSame("keep ownership of the call despite notification failure",dialing,PhoneSessionService.current);
  service.onCallRemoved(dialing);
  assertNull(PhoneSessionService.current);
 }
 @Test public void outgoingNotificationIsAlwaysQuiet(){
  service.onCallAdded(call(Call.STATE_DIALING));assertNull(notification().fullScreenIntent);
  assertEquals(NotificationManager.IMPORTANCE_LOW,manager.getNotificationChannel(notification().getChannelId()).getImportance());
 }
 @Test public void proximityLockReleasesOnSpeakerCallEndAndServiceDestroy(){
  service.setAudioRoute(CallAudioState.ROUTE_EARPIECE);
  Call active=call(Call.STATE_ACTIVE);service.onCallAdded(active);
  PowerManager.WakeLock lock=ShadowPowerManager.getLatestWakeLock();assertNotNull(lock);assertTrue(lock.isHeld());
  service.setAudioRoute(CallAudioState.ROUTE_SPEAKER);service.onCallAudioStateChanged(service.getCallAudioState());assertFalse(lock.isHeld());
  service.setAudioRoute(CallAudioState.ROUTE_EARPIECE);service.onCallAudioStateChanged(service.getCallAudioState());assertTrue(lock.isHeld());
  service.onCallRemoved(active);assertFalse(lock.isHeld());
  service.onCallAdded(call(Call.STATE_ACTIVE));assertTrue(lock.isHeld());
  controller.destroy();controller=null;assertFalse(lock.isHeld());
 }
 @Test public void proximityOnlyProtectsEarpieceCalls(){
  assertTrue(PhoneSessionService.useProximity(Call.STATE_ACTIVE,CallAudioState.ROUTE_EARPIECE));
  assertTrue(PhoneSessionService.useProximity(Call.STATE_DIALING,CallAudioState.ROUTE_EARPIECE));
  assertFalse(PhoneSessionService.useProximity(Call.STATE_RINGING,CallAudioState.ROUTE_EARPIECE));
  assertFalse(PhoneSessionService.useProximity(Call.STATE_HOLDING,CallAudioState.ROUTE_EARPIECE));
  assertFalse(PhoneSessionService.useProximity(Call.STATE_ACTIVE,CallAudioState.ROUTE_SPEAKER));
  assertFalse(PhoneSessionService.useProximity(Call.STATE_ACTIVE,CallAudioState.ROUTE_BLUETOOTH));
  assertFalse(PhoneSessionService.useProximity(Call.STATE_ACTIVE,CallAudioState.ROUTE_WIRED_HEADSET));
 }
 @Test public void speakerOffRespectsWiredHeadset(){
  assertEquals(CallAudioState.ROUTE_WIRED_HEADSET,PhoneSessionService.privateRoute(CallAudioState.ROUTE_WIRED_HEADSET|CallAudioState.ROUTE_EARPIECE));
  assertEquals(CallAudioState.ROUTE_EARPIECE,PhoneSessionService.privateRoute(CallAudioState.ROUTE_EARPIECE|CallAudioState.ROUTE_BLUETOOTH));
  assertEquals(CallAudioState.ROUTE_BLUETOOTH,PhoneSessionService.privateRoute(CallAudioState.ROUTE_BLUETOOTH));
 }
}
