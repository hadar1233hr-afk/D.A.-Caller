package com.dacaller.app;

import android.bluetooth.BluetoothDevice;
import android.os.Build;
import android.telecom.CallAudioState;
import java.util.Collection;

/** Telecom is the source of truth; Bluetooth labels never imply a nonexistent route. */
final class CallAudioRoutes {
 static CallAudioState state(){
  PhoneSessionService service=PhoneSessionService.instance;
  try{return service==null?null:service.getCallAudioState();}catch(RuntimeException ignored){return null;}
 }
 static int route(){CallAudioState s=state();return s==null?0:s.getRoute();}
 static int supported(){CallAudioState s=state();return s==null?0:s.getSupportedRouteMask();}
 /** A row owns this snapshot so a later tap cannot silently select a different headset. */
 static final class BluetoothTarget {
  final BluetoothDevice device;
  final String name;
  private BluetoothTarget(BluetoothDevice device,String name){this.device=device;this.name=name;}
 }
 static BluetoothTarget bluetoothTarget(){return bluetoothTarget(state());}
 private static BluetoothTarget bluetoothTarget(CallAudioState s){
  if(s==null||(s.getSupportedRouteMask()&CallAudioState.ROUTE_BLUETOOTH)==0)return null;
  BluetoothDevice device=null;
  if(Build.VERSION.SDK_INT>=28)try{
   device=s.getActiveBluetoothDevice();
   if(device==null){
    Collection<BluetoothDevice> devices=s.getSupportedBluetoothDevices();
    if(devices!=null&&!devices.isEmpty())device=devices.iterator().next();
   }
  }catch(RuntimeException ignored){/* Telecom can expose the route without endpoint details. */}
  String name="Bluetooth";
  if(device!=null)try{
   String actual=device.getName();if(actual!=null&&!actual.trim().isEmpty())name=actual;
  }catch(RuntimeException ignored){/* The generic route remains usable without name permission. */}
  return new BluetoothTarget(device,name);
 }
 static String bluetoothName(){
  BluetoothTarget target=bluetoothTarget();return target==null?"":target.name;
 }
 static boolean select(int route){
  PhoneSessionService service=PhoneSessionService.instance;CallAudioState s=state();
  if(route!=CallAudioState.ROUTE_EARPIECE&&route!=CallAudioState.ROUTE_WIRED_HEADSET&&
     route!=CallAudioState.ROUTE_SPEAKER&&route!=CallAudioState.ROUTE_BLUETOOTH)return false;
  if(service==null||s==null||(s.getSupportedRouteMask()&route)==0)return false;
  if(route==CallAudioState.ROUTE_BLUETOOTH)return selectBluetooth(service,s,bluetoothTarget(s));
  try{service.setAudioRoute(route);return true;}catch(RuntimeException ignored){return false;}
 }
 static boolean selectBluetooth(BluetoothTarget target){
  PhoneSessionService service=PhoneSessionService.instance;CallAudioState s=state();
  return service!=null&&s!=null&&selectBluetooth(service,s,target);
 }
 private static boolean selectBluetooth(PhoneSessionService service,CallAudioState s,BluetoothTarget target){
  if(target==null||(s.getSupportedRouteMask()&CallAudioState.ROUTE_BLUETOOTH)==0)return false;
  if(Build.VERSION.SDK_INT>=28&&target.device!=null)try{
   boolean present=target.device.equals(s.getActiveBluetoothDevice());
   if(!present){Collection<BluetoothDevice> available=s.getSupportedBluetoothDevices();present=available!=null&&available.contains(target.device);}
   // A removed row is stale. Do not redirect its tap to another connected headset.
   if(!present)return false;
   service.requestBluetoothAudio(target.device);return true;
  }catch(SecurityException ignored){/* Preserve generic Telecom routing when device access is denied. */}
  catch(RuntimeException ignored){return false;}
  try{service.setAudioRoute(CallAudioState.ROUTE_BLUETOOTH);return true;}
  catch(RuntimeException ignored){return false;}
 }
 private CallAudioRoutes(){}
}
