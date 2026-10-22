package com.dacaller.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

public class WidgetTap extends Activity {
 private String phone;
 private boolean direct;
 @Override public void onCreate(Bundle b){
  super.onCreate(b);
  phone=getIntent().getStringExtra("phone");
  direct=getIntent().getBooleanExtra("call",false);
  if(phone==null||phone.trim().isEmpty()){finish();return;}
  if(!direct){
   if(!Contacts.openContact(this,phone)){
    Intent intent=new Intent(this,MainActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
    intent.putExtra("contact_search",phone);
    startActivity(intent);
   }
   finish();return;
  }
  if(checkSelfPermission(Manifest.permission.CALL_PHONE)==PackageManager.PERMISSION_GRANTED)call(true);
  else requestPermissions(new String[]{Manifest.permission.CALL_PHONE},12);
 }
 @Override public void onRequestPermissionsResult(int request,String[] p,int[] results){
  super.onRequestPermissionsResult(request,p,results);
  call(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED);
 }
 private void call(boolean directCall){
  Intent intent=new Intent(directCall?Intent.ACTION_CALL:Intent.ACTION_DIAL,Uri.fromParts("tel",phone,null));
  try{startActivity(intent);}
  catch(Exception error){
   if(directCall){call(false);return;}
  }
  finish();
 }
}
