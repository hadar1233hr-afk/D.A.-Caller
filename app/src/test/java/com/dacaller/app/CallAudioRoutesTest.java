package com.dacaller.app;

import android.bluetooth.BluetoothDevice;
import android.telecom.CallAudioState;
import java.util.Arrays;
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
 @Test public void activeBluetoothSelectionTargetsTheDeviceWhoseNameIsShown(){
  CallAudioState state=state(CallAudioState.ROUTE_BLUETOOTH,CallAudioState.ROUTE_BLUETOOTH|CallAudioState.ROUTE_SPEAKER);
  BluetoothDevice active=mock(BluetoothDevice.class),other=mock(BluetoothDevice.class);
  when(active.getName()).thenReturn("Test active headset");
  when(state.getActiveBluetoothDevice()).thenReturn(active);
  when(state.getSupportedBluetoothDevices()).thenReturn(Arrays.asList(other,active));
  CallAudioRoutes.BluetoothTarget shown=CallAudioRoutes.bluetoothTarget();
  assertEquals("Test active headset",shown.name);assertSame(active,shown.device);
  assertTrue(CallAudioRoutes.selectBluetooth(shown));
  verify(service).requestBluetoothAudio(active);verify(service,never()).requestBluetoothAudio(other);
  verify(service,never()).setAudioRoute(anyInt());
 }
 @Test public void displayedBluetoothTargetSurvivesSupportedDeviceReordering(){
  CallAudioState state=state(CallAudioState.ROUTE_SPEAKER,CallAudioState.ROUTE_BLUETOOTH|CallAudioState.ROUTE_SPEAKER);
  BluetoothDevice shownDevice=mock(BluetoothDevice.class),other=mock(BluetoothDevice.class);
  when(shownDevice.getName()).thenReturn("Test displayed headset");
  when(state.getSupportedBluetoothDevices()).thenReturn(Arrays.asList(shownDevice,other));
  CallAudioRoutes.BluetoothTarget shown=CallAudioRoutes.bluetoothTarget();
  assertEquals("Test displayed headset",shown.name);
  when(state.getSupportedBluetoothDevices()).thenReturn(Arrays.asList(other,shownDevice));
  assertTrue(CallAudioRoutes.selectBluetooth(shown));
  verify(service).requestBluetoothAudio(shownDevice);verify(service,never()).requestBluetoothAudio(other);
 }
 @Test public void disconnectedDisplayedHeadsetCannotRedirectTapToAnotherHeadset(){
  CallAudioState state=state(CallAudioState.ROUTE_SPEAKER,CallAudioState.ROUTE_BLUETOOTH|CallAudioState.ROUTE_SPEAKER);
  BluetoothDevice old=mock(BluetoothDevice.class),next=mock(BluetoothDevice.class);
  when(state.getSupportedBluetoothDevices()).thenReturn(Collections.singletonList(old));
  CallAudioRoutes.BluetoothTarget shown=CallAudioRoutes.bluetoothTarget();
  when(state.getSupportedBluetoothDevices()).thenReturn(Collections.singletonList(next));
  assertFalse(CallAudioRoutes.selectBluetooth(shown));
  verify(service,never()).requestBluetoothAudio(any());verify(service,never()).setAudioRoute(anyInt());
 }
 @Test public void deviceRequestPermissionDenialFallsBackToTheSupportedGenericRoute(){
  CallAudioState state=state(CallAudioState.ROUTE_SPEAKER,CallAudioState.ROUTE_BLUETOOTH|CallAudioState.ROUTE_SPEAKER);
  BluetoothDevice device=mock(BluetoothDevice.class);
  when(state.getSupportedBluetoothDevices()).thenReturn(Collections.singletonList(device));
  doThrow(new SecurityException("BLUETOOTH_CONNECT denied")).when(service).requestBluetoothAudio(device);
  assertTrue(CallAudioRoutes.selectBluetooth(CallAudioRoutes.bluetoothTarget()));
  verify(service).setAudioRoute(CallAudioState.ROUTE_BLUETOOTH);
  assertEquals("no synthetic acknowledgement",CallAudioState.ROUTE_SPEAKER,CallAudioRoutes.route());
 }
 @Test public void deniedDeviceAndGenericCommandsReturnFailureWithoutChangingSelection(){
  CallAudioState state=state(CallAudioState.ROUTE_SPEAKER,CallAudioState.ROUTE_BLUETOOTH|CallAudioState.ROUTE_SPEAKER);
  BluetoothDevice device=mock(BluetoothDevice.class);
  when(state.getSupportedBluetoothDevices()).thenReturn(Collections.singletonList(device));
  doThrow(new SecurityException("device denied")).when(service).requestBluetoothAudio(device);
  doThrow(new SecurityException("route denied")).when(service).setAudioRoute(CallAudioState.ROUTE_BLUETOOTH);
  assertFalse(CallAudioRoutes.selectBluetooth(CallAudioRoutes.bluetoothTarget()));
  assertEquals(CallAudioState.ROUTE_SPEAKER,CallAudioRoutes.route());
 }
 @Test public void genericBluetoothRouteRemainsUsableWithoutEndpointDetails(){
  state(CallAudioState.ROUTE_SPEAKER,CallAudioState.ROUTE_BLUETOOTH|CallAudioState.ROUTE_SPEAKER);
  CallAudioRoutes.BluetoothTarget shown=CallAudioRoutes.bluetoothTarget();
  assertNotNull(shown);assertNull(shown.device);assertEquals("Bluetooth",shown.name);
  assertTrue(CallAudioRoutes.selectBluetooth(shown));
  verify(service).setAudioRoute(CallAudioState.ROUTE_BLUETOOTH);verify(service,never()).requestBluetoothAudio(any());
 }
 @Test public void deniedDeviceNameDoesNotPreventSelectingThatExactEndpoint(){
  CallAudioState state=state(CallAudioState.ROUTE_SPEAKER,CallAudioState.ROUTE_BLUETOOTH|CallAudioState.ROUTE_SPEAKER);
  BluetoothDevice device=mock(BluetoothDevice.class);
  when(state.getSupportedBluetoothDevices()).thenReturn(Collections.singletonList(device));
  when(device.getName()).thenThrow(new SecurityException("name denied"));
  CallAudioRoutes.BluetoothTarget shown=CallAudioRoutes.bluetoothTarget();
  assertEquals("Bluetooth",shown.name);assertSame(device,shown.device);
  assertTrue(CallAudioRoutes.selectBluetooth(shown));
  verify(service).requestBluetoothAudio(device);verify(service,never()).setAudioRoute(anyInt());
 }
 @Test public void endpointFailureOtherThanPermissionDoesNotRedirectToAnotherDevice(){
  CallAudioState state=state(CallAudioState.ROUTE_SPEAKER,CallAudioState.ROUTE_BLUETOOTH|CallAudioState.ROUTE_SPEAKER);
  BluetoothDevice device=mock(BluetoothDevice.class);
  when(state.getSupportedBluetoothDevices()).thenReturn(Collections.singletonList(device));
  doThrow(new IllegalStateException("endpoint disconnected")).when(service).requestBluetoothAudio(device);
  assertFalse(CallAudioRoutes.selectBluetooth(CallAudioRoutes.bluetoothTarget()));
  verify(service,never()).setAudioRoute(anyInt());
 }
}
