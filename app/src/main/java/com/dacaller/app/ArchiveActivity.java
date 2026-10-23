package com.dacaller.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ArchiveActivity extends Activity {
 private static final ExecutorService IO=Executors.newSingleThreadExecutor();
 private final ArrayList<CallHistory.Entry> calls=new ArrayList<>();
 private SharedPreferences prefs;
 private BaseAdapter adapter;
 private TextView title,subtitle;
 private int dp(float x){return (int)(getResources().getDisplayMetrics().density*x+0.5f);}
 private boolean dark(){return prefs.getBoolean("dark",false)||prefs.getBoolean("amoled",false);}
 private int surface(){return prefs.getBoolean("amoled",false)?Color.BLACK:dark()?0xFF151515:0xFFF8F8F7;}
 private int card(){return dark()?0xFF232323:Color.WHITE;}
 private int ink(){return dark()?Color.WHITE:0xFF1B1B1B;}
 private int muted(){return dark()?0xFFA5A5A5:0xFF666666;}
 private int gold(){return dark()?0xFFE1C58D:0xFF8D672E;}
 private GradientDrawable shape(int c,int r){GradientDrawable b=new GradientDrawable();b.setColor(c);b.setCornerRadius(dp(r));return b;}
 private TextView text(String s,int sp,boolean bold,int color){
  TextView v=new TextView(this);v.setText(I18n.text(this,s));v.setTextColor(color);v.setTextSize(sp);
  v.setGravity(Gravity.CENTER_VERTICAL);v.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));return v;
 }
 @Override public void onCreate(Bundle b){
  super.onCreate(b);prefs=getSharedPreferences("da_caller",0);
  getWindow().setStatusBarColor(surface());getWindow().setNavigationBarColor(surface());
  getWindow().getDecorView().setSystemUiVisibility(dark()?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
  root.setBackgroundColor(surface());setContentView(root);
  LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(12),dp(5),dp(12),dp(5));
  root.addView(header,new LinearLayout.LayoutParams(-1,dp(57)));
  TextView back=text("‹",32,false,gold());back.setGravity(Gravity.CENTER);
  header.addView(back,new LinearLayout.LayoutParams(dp(45),-1));back.setOnClickListener(v->finish());
  title=text("ארכיון שיחות",20,true,ink());header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
  TextView privacy=text("◈",24,false,gold());header.addView(privacy);
  subtitle=text("שיחות שנשמרו לאחר שנעלמו מיומן המכשיר",12,false,muted());
  subtitle.setPadding(dp(17),0,dp(17),dp(10));root.addView(subtitle,new LinearLayout.LayoutParams(-1,dp(44)));
  ListView list=new ListView(this);list.setDivider(null);
  list.setVerticalScrollBarEnabled(false);
  list.setSelector(android.R.color.transparent);
  root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
  adapter=new BaseAdapter(){
   private final DateFormat df=DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT,new Locale("he","IL"));
   @Override public int getCount(){return calls.size();}
   @Override public Object getItem(int p){return calls.get(p);}
   @Override public long getItemId(int p){return calls.get(p).id;}
   @Override public View getView(int position,View convert,ViewGroup parent){
    LinearLayout row;TextView number,description;
    if(convert instanceof LinearLayout){
     row=(LinearLayout)convert;number=(TextView)row.getChildAt(0);description=(TextView)row.getChildAt(1);
    }else{
     row=new LinearLayout(ArchiveActivity.this);row.setOrientation(1);
     row.setPadding(dp(17),dp(8),dp(12),dp(8));row.setBackground(shape(card(),14));
     number=text("",16,true,ink());number.setSingleLine(true);number.setEllipsize(TextUtils.TruncateAt.END);
     row.addView(number,new LinearLayout.LayoutParams(-1,dp(27)));
     description=text("",12,false,muted());row.addView(description,new LinearLayout.LayoutParams(-1,dp(34)));
    }
    CallHistory.Entry entry=calls.get(position);
    number.setText(entry.number);
    String type=entry.type==android.provider.CallLog.Calls.MISSED_TYPE?I18n.text(this,"לא נענתה"):
      entry.type==android.provider.CallLog.Calls.OUTGOING_TYPE?I18n.text(this,"יוצאת"):I18n.text(this,"נכנסת");
    description.setText(type+"  ·  "+df.format(new Date(entry.date))+"  ·  "+Math.round(entry.duration/60.0)+" דק׳");
    row.setOnClickListener(v->new android.app.AlertDialog.Builder(ArchiveActivity.this)
      .setTitle(entry.number)
      .setMessage(description.getText())
      .setPositiveButton("חייג",(d,which)->{
       try{startActivity(new Intent(Intent.ACTION_DIAL,android.net.Uri.fromParts("tel",entry.number,null)));}
       catch(Exception ignored){}
      })
      .setNegativeButton("סגור",null).show());
    return row;
   }
  };
  list.setAdapter(adapter);
  subtitle.setText("טוען נתוני ארכיון…");
  IO.execute(()->{
   List<CallHistory.Entry> loaded=CallArchive.load(getApplicationContext());
   runOnUiThread(()->{
    if(isFinishing()||isDestroyed())return;
    calls.clear();calls.addAll(loaded);adapter.notifyDataSetChanged();
    subtitle.setText(calls.isEmpty()?"אין עדיין רשומות בארכיון":"נשמרו "+calls.size()+" שיחות שאינן מופיעות ביומן המכשיר");
   });
  });
 }
}
