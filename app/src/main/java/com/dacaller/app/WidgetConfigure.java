package com.dacaller.app;

import android.Manifest;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Uses a virtualized list; every contact remains selectable even on a small screen. */
public class WidgetConfigure extends Activity {
 private static final ExecutorService IO=Executors.newSingleThreadExecutor();
 private int widgetId;private boolean direct;
 private List<Contacts.Person> items=new ArrayList<>(),matched=new ArrayList<>();
 private ArrayAdapter<String> adapter;private EditText search;
 private boolean dark(){android.content.SharedPreferences p=getSharedPreferences("da_caller",0);return p.getBoolean("dark",false)||p.getBoolean("amoled",false);}
 private int bg(){return getSharedPreferences("da_caller",0).getBoolean("amoled",false)?Color.BLACK:dark()?0xFF101010:0xFFF8F8F6;}
 private int ink(){return dark()?Color.WHITE:0xFF1E1E1E;}
 private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
 private String tr(String s){return I18n.text(this,s);}
 @Override public void onCreate(Bundle state){
  super.onCreate(state);widgetId=getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID);
  Intent cancel=new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId);setResult(RESULT_CANCELED,cancel);
  if(widgetId==AppWidgetManager.INVALID_APPWIDGET_ID){finish();return;}
  AppWidgetProviderInfo info=AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId);
  direct=info==null||!info.provider.getClassName().contains("ContactWidget");
  populate();
  if(checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.READ_CONTACTS},25);
  else load();
 }
 @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){
  super.onRequestPermissionsResult(request,permissions,grants);
  if(request==25){if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)load();else finish();}
 }
 private void populate(){
  getWindow().setStatusBarColor(bg());getWindow().setNavigationBarColor(bg());
  getWindow().getDecorView().setSystemUiVisibility(dark()?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(bg());
  root.setLayoutDirection(I18n.isEnglish(this)?View.LAYOUT_DIRECTION_LTR:View.LAYOUT_DIRECTION_RTL);root.setPadding(dp(14),dp(16),dp(14),dp(8));setContentView(root);
  TextView heading=new TextView(this);heading.setText(tr("בחר איש קשר"));heading.setTextSize(21);heading.setTextColor(ink());root.addView(heading);
  search=new EditText(this);search.setSingleLine(true);search.setTextColor(ink());search.setHintTextColor(dark()?0xFFAAAAAA:0xFF777777);search.setHint(tr("חיפוש שם או מספר"));root.addView(search,new LinearLayout.LayoutParams(-1,dp(56)));
  ListView list=new ListView(this);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
  TextView empty=new TextView(this);empty.setText(tr("לא נמצאו תוצאות"));empty.setTextColor(ink());empty.setPadding(0,dp(16),0,0);root.addView(empty);list.setEmptyView(empty);
  adapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,new ArrayList<>()){
   @Override public View getView(int position,View convert,ViewGroup parent){TextView text=(TextView)super.getView(position,convert,parent);text.setTextColor(ink());text.setMinimumHeight(dp(64));return text;}
  };
  list.setAdapter(adapter);list.setOnItemClickListener((parent,view,position,id)->select(matched.get(position)));
  search.addTextChangedListener(new TextWatcher(){
   public void beforeTextChanged(CharSequence s,int start,int count,int after){}
   public void afterTextChanged(Editable e){}
   public void onTextChanged(CharSequence s,int start,int before,int count){showFiltered();}
  });
 }
 private void load(){IO.execute(()->{List<Contacts.Person> loaded=Contacts.all(getApplicationContext());runOnUiThread(()->{if(isFinishing()||isDestroyed())return;items=loaded;showFiltered();});});}
 private void showFiltered(){
  String query=search.getText().toString().trim().toLowerCase(Locale.ROOT);matched=new ArrayList<>();ArrayList<String> names=new ArrayList<>();
  for(Contacts.Person person:items){
   if(direct&&person.phone.isEmpty())continue;
   if(!query.isEmpty()&&!person.name.toLowerCase(Locale.ROOT).contains(query)&&!person.phone.contains(query))continue;
   matched.add(person);names.add(String.format(Locale.getDefault(),"%s\n%s",person.name,person.phone));
  }
  adapter.clear();adapter.addAll(names);adapter.notifyDataSetChanged();
 }
 private void select(Contacts.Person person){
  WidgetSupport.prefs(this).edit().putString("widget_name_"+widgetId,person.name).putString("widget_phone_"+widgetId,person.phone).putLong("widget_contact_"+widgetId,person.id).apply();
  WidgetSupport.update(this,AppWidgetManager.getInstance(this),widgetId,direct);
  setResult(RESULT_OK,new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId));finish();
 }
}
