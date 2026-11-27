package com.dacaller.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
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
 private int bg(){return NexPalette.background(dark(),getSharedPreferences("da_caller",0).getBoolean("amoled",false));}
 private int ink(){return NexPalette.ink(dark());}
 private int muted(){return NexPalette.muted(dark());}
 private int accent(){return "silver".equals(getSharedPreferences("da_caller",0).getString("accent","gold"))?(dark()?0xFFE4E4E4:0xFF616161):AppAppearance.gold(dark());}
 private String tr(String s){return I18n.text(this,s);}
 private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
 private GradientDrawable shape(int color,int radius){GradientDrawable value=new GradientDrawable();value.setColor(color);value.setCornerRadius(dp(radius));return value;}
 @Override public void onCreate(Bundle b){
  super.onCreate(b);
  incoming=getIntent().getStringExtra("incoming_number");
  if(incoming==null)incoming="";
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
  root.setLayoutDirection(I18n.isEnglish(this)?android.view.View.LAYOUT_DIRECTION_LTR:android.view.View.LAYOUT_DIRECTION_RTL);
  root.setBackgroundColor(bg());root.setPadding(dp(16),dp(8),dp(16),dp(12));
  getWindow().setStatusBarColor(bg());getWindow().setNavigationBarColor(bg());
  getWindow().getDecorView().setSystemUiVisibility(dark()?0:android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
  setContentView(root);
  LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);heading.setMinimumHeight(dp(56));
  root.addView(heading,new LinearLayout.LayoutParams(-1,-2));
  ImageView back=new ImageView(this);back.setImageDrawable(AppAppearance.backArrow(this,accent()));back.setScaleType(ImageView.ScaleType.CENTER);back.setFocusable(true);back.setContentDescription(tr("חזרה"));
  heading.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));back.setOnClickListener(v->finish());
  TextView title=new TextView(this);title.setText(tr("הוספה לאיש קשר קיים"));
  title.setTextSize(22);title.setTextColor(ink());title.setPadding(dp(4),dp(6),dp(4),dp(6));
  AppAppearance.font(title,true);
  heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));
  EditText query=new EditText(this);
  query.setSingleLine(true);query.setHint(tr("חיפוש שם או מספר"));
  query.setTextColor(ink());query.setHintTextColor(muted());
  query.setTextSize(16);AppAppearance.font(query,false);query.setMinimumHeight(dp(54));query.setPadding(dp(16),dp(10),dp(16),dp(10));query.setBackground(shape(NexPalette.surface(dark(),getSharedPreferences("da_caller",0).getBoolean("amoled",false)),19));
  LinearLayout.LayoutParams queryParams=new LinearLayout.LayoutParams(-1,-2);queryParams.topMargin=dp(11);queryParams.bottomMargin=dp(6);root.addView(query,queryParams);
  ListView list=new ListView(this);list.setDivider(null);list.setSelector(android.R.color.transparent);list.setCacheColorHint(Color.TRANSPARENT);
  root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
  adapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,new ArrayList<String>()){
   @Override public android.view.View getView(int position,android.view.View convert,android.view.ViewGroup parent){
    TextView label=(TextView)super.getView(position,convert,parent);
    label.setTextColor(ink());label.setTextSize(16);AppAppearance.font(label,true);label.setMinimumHeight(dp(76));label.setSingleLine(false);label.setMaxLines(2);label.setEllipsize(android.text.TextUtils.TruncateAt.END);
    label.setBackground(new InsetDrawable(shape(NexPalette.surface(dark(),getSharedPreferences("da_caller",0).getBoolean("amoled",false)),20),0,dp(5),0,dp(6)));
    label.setPadding(dp(16),dp(17),dp(16),dp(17));return label;
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
