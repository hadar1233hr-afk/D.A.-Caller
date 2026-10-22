package com.dacaller.app;
import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
public class WidgetTap extends Activity {
 private String phone;private boolean direct;
 @Override public void onCreate(Bundle b){super.onCreate(b);
  phone=getIntent().getStringExtra("phone");direct=getIntent().getBooleanExtra("call",false);
  if(phone==null||phone.isEmpty()){finish();return;}
  if(!direct){Intent i=new Intent(this,MainActivity.class);i.putExtra("phone",phone);startActivity(i);finish();return;}
  if(checkSelfPermission(Manifest.permission.CALL_PHONE)==PackageManager.PERMISSION_GRANTED)call(true);
  else requestPermissions(new String[]{Manifest.permission.CALL_PHONE},12);
 }
 @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);call(g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED);}
 private void call(boolean direct){
  try{startActivity(new Intent(direct?Intent.ACTION_CALL:Intent.ACTION_DIAL,Uri.fromParts("tel",phone,null)));}
  catch(Exception e){if(direct){call(false);return;}}
  finish();
 }
}