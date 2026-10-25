package com.dacaller.app;

import android.bluetooth.BluetoothDevice;
import android.telecom.CallAudioState;
import java.util.Collections;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Exercises Telecom state and command boundaries, without simulating a radio. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=33)
public class CallAudioRoutesTest {
 private PhoneSessionService service;
 @Before public void create(){
  service=mock(PhoneSessionService.class);PhoneSessionService.instance=service;
 }
 @After public void clear(){PhoneSessionService.instance=null;}
 private CallAudioState state(int route,int supported){
  CallAudioState state=mock(CallAudioState.class);
  when(state.getRoute()).thenReturn(route);when(state.getSupportedRouteMask()).thenReturn(supported);
  when(state.getSupportedBluetoothDevices()).thenReturn(Collections.emptyList());
  when(service.getCallAudioState()).thenReturn(state);return state;
 }
 @Test public void unboundServiceNeverOffersOrSelectsPhantomRoutes(){
  PhoneSessionService.instance=null;
  assertNull(CallAudioRoutes.state());assertEquals(0,CallAudioRoutes.route());
  assertEquals(0,CallAudioRoutes.supported());assertEquals("",CallAudioRoutes.bluetoothName());
  assertFalse(CallAudioRoutes.select(CallAudioState.ROUTE_BLUETOOTH));
 }
 @Test public void stateProviderFailureIsSafe(){
  when(service.getCallAudioState()).thenThrow(new SecurityException("simulated revoked binding"));
  assertNull(CallAudioRoutes.state());assertEquals(0,CallAudioRoutes.supported());
  assertFalse(CallAudioRoutes.select(CallAudioState.ROUTE_SPEAKER));
 }
 @Test public void bluetoothIsHiddenWhenTelecomDoesNotSupportItEvenWithStaleDevice(){
  CallAudioState state=state(CallAudioState.ROUTE_EARPIECE,CallAudioState.ROUTE_EARPIECE|CallAudioState.ROUTE_SPEAKER);
  BluetoothDevice stale=mock(BluetoothDevice.class);when(state.getActiveBluetoothDevice()).thenReturn(stale);
  assertEquals("",CallAudioRoutes.bluetoothName());
  assertFalse(CallAudioRoutes.select(CallAudioState.ROUTE_BLUETOOTH));
  verify(stale,never()).getName();verify(service,never()).setAudioRoute(anyInt());
 }
 @Test public void bluetoothLabelUsesTheActualActiveDevice(){
  CallAudioState state=state(CallAudioState.ROUTE_BLUETOOTH,CallAudioState.ROUTE_BLUETOOTH|CallAudioState.ROUTE_SPEAKER);
  BluetoothDevice device=mock(BluetoothDevice.class);when(device.getName()).thenReturn("Test headset 7");
  when(state.getActiveBluetoothDevice()).thenReturn(device);
  assertEquals("Test headset 7",CallAudioRoutes.bluetoothName());
 }
 @Test public void availableBluetoothLabelUsesSupportedDeviceWhenSpeakerIsActive(){
  CallAudioState state=state(CallAudioState.ROUTE_SPEAKER,CallAudioState.ROUTE_BLUETOOTH|CallAudioState.ROUTE_SPEAKER);
  BluetoothDevice device=mock(BluetoothDevice.class);when(device.getName()).thenReturn("Test handsfree");
  when(state.getSupportedBluetoothDevices()).thenReturn(Collections.singletonList(device));
  assertEquals("Test handsfree",CallAudioRoutes.bluetoothName());
 }
 @Test public void deniedBluetoothNamePermissionKeepsGenericRealRouteLabel(){
  CallAudioState state=state(CallAudioState.ROUTE_BLUETOOTH,CallAudioState.ROUTE_BLUETOOTH);
  BluetoothDevice device=mock(BluetoothDevice.class);when(state.getActiveBluetoothDevice()).thenReturn(device);
  when(device.getName()).thenThrow(new SecurityException("BLUETOOTH_CONNECT denied"));
  assertEquals("Bluetooth",CallAudioRoutes.bluetoothName());
  assertEquals(CallAudioState.ROUTE_BLUETOOTH,CallAudioRoutes.route());
 }
 @Test public void supportedSelectionWaitsForTelecomAcknowledgementBeforeChangingUiState(){
  CallAudioState state=state(CallAudioState.ROUTE_EARPIECE,CallAudioState.ROUTE_EARPIECE|CallAudioState.ROUTE_SPEAKER);
  assertTrue(CallAudioRoutes.select(CallAudioState.ROUTE_SPEAKER));
  verify(service).setAudioRoute(CallAudioState.ROUTE_SPEAKER);
  assertEquals("command is not an acknowledgement",CallAudioState.ROUTE_EARPIECE,CallAudioRoutes.route());
  when(state.getRoute()).thenReturn(CallAudioState.ROUTE_SPEAKER);
  assertEquals(CallAudioState.ROUTE_SPEAKER,CallAudioRoutes.route());
 }
 @Test public void rejectedRouteCommandDoesNotCrashOrInventSelection(){
  state(CallAudioState.ROUTE_EARPIECE,CallAudioState.ROUTE_EARPIECE|CallAudioState.ROUTE_SPEAKER);
  doThrow(new SecurityException("simulated Telecom denial")).when(service).setAudioRoute(CallAudioState.ROUTE_SPEAKER);
  assertFalse(CallAudioRoutes.select(CallAudioState.ROUTE_SPEAKER));
  assertEquals(CallAudioState.ROUTE_EARPIECE,CallAudioRoutes.route());
 }
 @Test public void arbitraryMasksCannotBeSubmittedAsARoute(){
  state(CallAudioState.ROUTE_EARPIECE,CallAudioState.ROUTE_EARPIECE|CallAudioState.ROUTE_SPEAKER);
  assertFalse(CallAudioRoutes.select(0));
  assertFalse(CallAudioRoutes.select(CallAudioState.ROUTE_SPEAKER|0x4000));
  assertFalse(CallAudioRoutes.select(CallAudioState.ROUTE_SPEAKER|CallAudioState.ROUTE_EARPIECE));
  verify(service,never()).setAudioRoute(anyInt());
 }
}
