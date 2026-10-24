package com.dacaller.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

public class WidgetTap extends Activity {
 private String phone;
 @Override public void onCreate(Bundle state){
  super.onCreate(state);phone=getIntent().getStringExtra("phone");
  if(!getIntent().getBooleanExtra("call",false)){
   long id=getIntent().getLongExtra("contact_id",-1);
   if(id>0){openContact(id);return;}
   if(phone==null||phone.trim().isEmpty()){finish();return;}
   ContactPhotos.lookup(this,phone,(contactId,name,image)->{
    if(isFinishing()||isDestroyed())return;
    if(contactId>0)openContact(contactId);
    else{startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("contact_search",phone));finish();}
   });return;
  }
  if(!CallLauncher.isCallable(phone)){finish();return;}
  if(checkSelfPermission(Manifest.permission.CALL_PHONE)==PackageManager.PERMISSION_GRANTED)call();
  else requestPermissions(new String[]{Manifest.permission.CALL_PHONE},12);
 }
 private void openContact(long id){
  startActivity(new Intent(this,ContactDetailActivity.class).putExtra("contact_id",id));finish();
 }
 @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
  super.onRequestPermissionsResult(request,permissions,results);if(request==12)call();
 }
 private void call(){if(!CallLauncher.place(this,phone))android.widget.Toast.makeText(this,I18n.text(this,"לא ניתן להתקשר"),android.widget.Toast.LENGTH_LONG).show();finish();}
}
