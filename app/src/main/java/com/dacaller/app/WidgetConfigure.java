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
 private android.widget.LinearLayout rows;
 private android.widget.EditText search;
 private java.util.List<Contacts.Person> items=java.util.Collections.emptyList();
 private static final java.util.concurrent.ExecutorService IO=java.util.concurrent.Executors.newSingleThreadExecutor();
 private void populate(){
  android.widget.ScrollView scroll=new android.widget.ScrollView(this);
  scroll.setBackgroundColor(0xFF101010);
  android.widget.LinearLayout panel=new android.widget.LinearLayout(this);
  panel.setOrientation(1);panel.setPadding(18,24,18,24);
  scroll.addView(panel);setContentView(scroll);
  TextView head=new TextView(this);
  head.setText(direct?"בחר איש קשר לחיוג מהיר":"בחר איש קשר לפתיחת הכרטיס");
  head.setTextSize(21);head.setTextColor(0xFFE4C589);head.setPadding(0,0,0,18);
  panel.addView(head);
  search=new android.widget.EditText(this);
  search.setSingleLine(true);search.setTextSize(16);search.setTextColor(0xFFFFFFFF);
  search.setHintTextColor(0xFF999999);search.setHint("חיפוש שם או מספר");
  panel.addView(search,new android.widget.LinearLayout.LayoutParams(-1,58));
  rows=new android.widget.LinearLayout(this);rows.setOrientation(1);panel.addView(rows);
  TextView loading=new TextView(this);loading.setText("טוען אנשי קשר...");loading.setTextColor(-1);rows.addView(loading);
  search.addTextChangedListener(new android.text.TextWatcher(){
   public void beforeTextChanged(CharSequence s,int st,int count,int after){}
   public void onTextChanged(CharSequence s,int st,int before,int count){showFiltered();}
   public void afterTextChanged(android.text.Editable editable){}
  });
  IO.execute(()->{
   java.util.List<Contacts.Person> found=Contacts.all(getApplicationContext());
   runOnUiThread(()->{
    if(isFinishing()||isDestroyed())return;
    items=found;showFiltered();
   });
  });
 }
 private void showFiltered(){
  if(rows==null||search==null)return;
  rows.removeAllViews();
  String query=search.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
  int count=0;
  for(Contacts.Person person:items){
   if(!query.isEmpty()&&!person.name.toLowerCase(java.util.Locale.ROOT).contains(query)&&!person.phone.contains(query))continue;
   if(count++>=60)continue;
   TextView line=new TextView(this);line.setText(person.name+"\n"+person.phone);
   line.setTextSize(16);line.setTextColor(0xFFFFFFFF);
   line.setPadding(14,12,14,12);rows.addView(line);
   line.setOnClickListener(v->{
    WidgetSupport.prefs(this).edit().putString("widget_name_"+widgetId,person.name).putString("widget_phone_"+widgetId,person.phone).apply();
    WidgetSupport.update(this,AppWidgetManager.getInstance(this),widgetId,direct);
    Intent result=new Intent();result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId);setResult(RESULT_OK,result);finish();
   });
  }
  if(count>60){
   TextView hint=new TextView(this);hint.setText("מוצגים 60 אנשי קשר. השתמש בחיפוש.");
   hint.setTextColor(0xFFE4C589);hint.setPadding(0,12,0,0);rows.addView(hint);
  }else if(count==0){
   TextView empty=new TextView(this);empty.setText("לא נמצאו תוצאות");empty.setTextColor(-1);rows.addView(empty);
  }
 }
}
