package com.dacaller.app;

import android.bluetooth.BluetoothDevice;
import android.os.Build;
import android.telecom.CallAudioState;

/** Telecom is the source of truth; Bluetooth labels never imply a nonexistent route. */
final class CallAudioRoutes {
 static CallAudioState state(){
  PhoneSessionService service=PhoneSessionService.instance;
  try{return service==null?null:service.getCallAudioState();}catch(RuntimeException ignored){return null;}
 }
 static int route(){CallAudioState s=state();return s==null?0:s.getRoute();}
 static int supported(){CallAudioState s=state();return s==null?0:s.getSupportedRouteMask();}
 static String bluetoothName(){
  CallAudioState s=state();if(s==null||(s.getSupportedRouteMask()&CallAudioState.ROUTE_BLUETOOTH)==0)return "";
  if(Build.VERSION.SDK_INT>=28)try{
   BluetoothDevice device=s.getActiveBluetoothDevice();
   if(device==null&&!s.getSupportedBluetoothDevices().isEmpty())device=s.getSupportedBluetoothDevices().iterator().next();
   if(device!=null){String name=device.getName();if(name!=null&&!name.trim().isEmpty())return name;}
  }catch(SecurityException ignored){/* Android 12+ may deny BLUETOOTH_CONNECT. */}
  catch(RuntimeException ignored){}
  return "Bluetooth";
 }
 static boolean select(int route){
  PhoneSessionService service=PhoneSessionService.instance;CallAudioState s=state();
  if(route!=CallAudioState.ROUTE_EARPIECE&&route!=CallAudioState.ROUTE_WIRED_HEADSET&&
     route!=CallAudioState.ROUTE_SPEAKER&&route!=CallAudioState.ROUTE_BLUETOOTH)return false;
  if(service==null||s==null||(s.getSupportedRouteMask()&route)==0)return false;
  try{service.setAudioRoute(route);return true;}catch(RuntimeException ignored){return false;}
 }
 private CallAudioRoutes(){}
}
