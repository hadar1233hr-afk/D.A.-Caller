package com.dacaller.app;
import android.Manifest;
import android.app.Activity;
import android.appwidget.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.*;
public class WidgetConfigure extends Activity {
 private int widgetId;private boolean direct;
 @Override public void onCreate(Bundle b){super.onCreate(b);
  widgetId=getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID);
  Intent cancel=new Intent();cancel.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId);setResult(RESULT_CANCELED,cancel);
  if(widgetId==AppWidgetManager.INVALID_APPWIDGET_ID){finish();return;}
  AppWidgetProviderInfo info=AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId);
  direct=info==null||!info.provider.getClassName().contains("ContactWidget");
  if(checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.READ_CONTACTS},25);
  else populate();
 }
 @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)populate();else finish();}
 private void populate(){
  ScrollView sc=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(1);list.setPadding(18,24,18,24);sc.addView(list);setContentView(sc);
  TextView h=new TextView(this);h.setText(direct?"בחר איש קשר לחיוג מהיר":"בחר איש קשר לפתיחת הכרטיס");h.setTextSize(23);h.setTextColor(Color.BLACK);h.setPadding(0,0,0,20);list.addView(h);
  for(Contacts.Person person:Contacts.all(this)){
   TextView line=new TextView(this);line.setText(person.name+"\n"+person.phone);line.setTextSize(17);line.setTextColor(0xFF20304A);line.setPadding(8,15,8,15);list.addView(line);
   line.setOnClickListener(v->{
    WidgetSupport.prefs(this).edit().putString("widget_name_"+widgetId,person.name).putString("widget_phone_"+widgetId,person.phone).apply();
    WidgetSupport.update(this,AppWidgetManager.getInstance(this),widgetId,direct);
    Intent result=new Intent();result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId);setResult(RESULT_OK,result);finish();
   });
  }
 }
}