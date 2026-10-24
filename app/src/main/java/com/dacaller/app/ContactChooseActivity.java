package com.dacaller.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Choose a contact to append the dialed number using the in-app editor. */
public final class ContactChooseActivity extends Activity {
 private static final ExecutorService IO=Executors.newSingleThreadExecutor();
 private List<Contacts.Person> all=new ArrayList<>();
 private List<Contacts.Person> matched=new ArrayList<>();
 private ArrayAdapter<String> adapter;
 private String incoming="",searchQuery="";
 private boolean dark(){android.content.SharedPreferences p=getSharedPreferences("da_caller",0);return p.getBoolean("dark",false)||p.getBoolean("amoled",false);}
 private int bg(){return getSharedPreferences("da_caller",0).getBoolean("amoled",false)?Color.BLACK:dark()?0xFF141414:0xFFF7F7F5;}
 private int ink(){return dark()?Color.WHITE:0xFF1A1A1A;}
 private int muted(){return dark()?0xFFAEAEAE:0xFF777777;}
 private int accent(){return dark()?0xFFE2C48B:0xFF95662A;}
 private String tr(String s){return I18n.text(this,s);}
 private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
 @Override public void onCreate(Bundle b){
  super.onCreate(b);
  incoming=getIntent().getStringExtra("incoming_number");
  if(incoming==null)incoming="";
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
  root.setLayoutDirection(I18n.isEnglish(this)?android.view.View.LAYOUT_DIRECTION_LTR:android.view.View.LAYOUT_DIRECTION_RTL);
  root.setBackgroundColor(bg());root.setPadding(dp(14),dp(17),dp(14),dp(9));
  setContentView(root);
  TextView title=new TextView(this);title.setText(tr("הוספה לאיש קשר קיים"));
  title.setTextSize(20);title.setTextColor(accent());
  title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
  root.addView(title,new LinearLayout.LayoutParams(-1,dp(43)));
  EditText query=new EditText(this);
  query.setSingleLine(true);query.setHint(tr("חיפוש שם או מספר"));
  query.setTextColor(ink());query.setHintTextColor(muted());
  query.setTextSize(16);root.addView(query,new LinearLayout.LayoutParams(-1,dp(53)));
  ListView list=new ListView(this);list.setDividerHeight(dp(1));
  root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
  adapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,new ArrayList<String>()){
   @Override public android.view.View getView(int position,android.view.View convert,android.view.ViewGroup parent){
    TextView label=(TextView)super.getView(position,convert,parent);
    label.setTextColor(ink());label.setTextSize(16);label.setMinimumHeight(dp(56));return label;
   }
  };
  list.setAdapter(adapter);
  list.setOnItemClickListener((parent,view,position,id)->{
   Contacts.Person person=matched.get(position);
   Intent edit=new Intent(this,ContactEditorActivity.class);
   edit.putExtra("contact_id",person.id);edit.putExtra("incoming_number",incoming);
   startActivity(edit);finish();
  });
  query.addTextChangedListener(new TextWatcher(){
   public void beforeTextChanged(CharSequence text,int a,int b,int c){}
   public void afterTextChanged(Editable value){}
   public void onTextChanged(CharSequence s,int a,int b,int c){searchQuery=s.toString();filter(searchQuery);}
  });
  if(checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED)
   requestPermissions(new String[]{Manifest.permission.READ_CONTACTS},71);
  else load();
 }
 @Override public void onRequestPermissionsResult(int r,String[] p,int[] grants){
  super.onRequestPermissionsResult(r,p,grants);
  if(r==71&&grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)load();
  else Toast.makeText(this,tr("נדרשת הרשאת אנשי קשר"),Toast.LENGTH_LONG).show();
 }
 private void load(){
  IO.execute(()->{
   List<Contacts.Person> loaded=Contacts.all(getApplicationContext());
   runOnUiThread(()->{if(isFinishing()||isDestroyed())return;all=loaded;filter(searchQuery);});
  });
 }
 private void filter(String query){
  matched.clear();String needle=query.toLowerCase(Locale.ROOT).trim();
  ArrayList<String> names=new ArrayList<>();
  for(Contacts.Person p:all){
   if(!needle.isEmpty()&&!p.name.toLowerCase(Locale.ROOT).contains(needle)&&!p.phone.contains(needle))continue;
   matched.add(p);names.add(p.name+(p.phone.isEmpty()?"":" • "+p.phone));
  }
  adapter.clear();adapter.addAll(names);adapter.notifyDataSetChanged();
 }
}
