package com.dacaller.app;

import android.Manifest;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Debug-only test host: records requests; cannot place calls or read private providers. */
public final class ListDispatchHostActivity extends MainActivity {
 private static final String TEST_PREFERENCES="da_caller_touch_instrumentation";
 public final List<String> requestedNumbers=new ArrayList<>();
 public int contactsOpened,detailsOpened,favoriteChanges;
 private Context isolatedApplication;
 private boolean listsReady;
 @Override public SharedPreferences getSharedPreferences(String name,int mode){
  return super.getSharedPreferences("da_caller".equals(name)?TEST_PREFERENCES:name,mode);
 }
 @Override public Context getApplicationContext(){
  if(isolatedApplication==null)isolatedApplication=new ContextWrapper(super.getApplicationContext()){
   @Override public Context getApplicationContext(){return this;}
   @Override public int checkSelfPermission(String permission){return PackageManager.PERMISSION_DENIED;}
   @Override public SharedPreferences getSharedPreferences(String name,int mode){return ListDispatchHostActivity.this.getSharedPreferences(name,mode);}
  };
  return isolatedApplication;
 }
 @Override public int checkSelfPermission(String permission){
  // Core list permission branches are exercised without granting telephony or provider access.
  if(listsReady&&(Manifest.permission.READ_CALL_LOG.equals(permission)||Manifest.permission.READ_CONTACTS.equals(permission)))return PackageManager.PERMISSION_GRANTED;
  return PackageManager.PERMISSION_DENIED;
 }
 @Override public void onCreate(Bundle saved){
  getSharedPreferences("da_caller",MODE_PRIVATE).edit().clear().putBoolean("role_prompted",true)
   .putBoolean("notifications_prompted",true).putBoolean("voice_permission_prompted",true)
   .putBoolean("show_stats",false).putBoolean("haptics",false).putString("swipe_direction","right").apply();
  super.onCreate(saved);
 }
 @Override public void onResume(){super.onResume();listsReady=true;}
 @Override void dialFromList(String number){requestedNumbers.add(number);}
 @Override void openContactDetail(long id){contactsOpened++;}
 @Override void openContactFromNumber(String number){contactsOpened++;}
 @Override void showCallNumber(CallHistory.Entry entry){detailsOpened++;}
 @Override void favoriteFromList(Contacts.Person person){favoriteChanges++;}
 @Override Map<String,String> nameIndex(){return Collections.emptyMap();}
}
