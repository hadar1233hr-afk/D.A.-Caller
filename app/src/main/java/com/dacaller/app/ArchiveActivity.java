package com.dacaller.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CallLog;
import android.telephony.PhoneNumberUtils;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Searchable, virtualized archive separate from the current Android call log. */
public final class ArchiveActivity extends Activity {
 private static final ExecutorService IO=Executors.newSingleThreadExecutor();
 private final ArrayList<CallHistory.Entry> calls=new ArrayList<>();
 private List<CallHistory.Entry> allCalls=new ArrayList<>();
 private SharedPreferences prefs;
 private BaseAdapter adapter;
 private TextView subtitle;
 private EditText search;
 private volatile int filterGeneration;
 private boolean loaded;
 private int dp(float x){return (int)(getResources().getDisplayMetrics().density*x+0.5f);}
 private boolean dark(){return prefs.getBoolean("dark",false)||prefs.getBoolean("amoled",false);}
 private int surface(){return prefs.getBoolean("amoled",false)?Color.BLACK:dark()?0xFF151515:0xFFF8F8F7;}
 private int card(){return dark()?0xFF232323:Color.WHITE;}
 private int ink(){return dark()?Color.WHITE:0xFF1B1B1B;}
 private int muted(){return dark()?0xFFBBBBBB:0xFF666666;}
 private int gold(){return dark()?0xFFE1C58D:0xFF8D672E;}
 private Locale locale(){return I18n.isEnglish(this)?Locale.ENGLISH:new Locale("he","IL");}
 private String tr(String he,String en){return I18n.isEnglish(this)?en:he;}
 private GradientDrawable shape(int color,int radius){GradientDrawable bg=new GradientDrawable();bg.setColor(color);bg.setCornerRadius(dp(radius));return bg;}
 private TextView text(String s,int sp,boolean bold,int color){
  TextView view=new TextView(this);view.setText(I18n.text(this,s));view.setTextColor(color);view.setTextSize(sp);
  view.setGravity(Gravity.CENTER_VERTICAL);view.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));return view;
 }
 @Override public void onCreate(Bundle state){
  super.onCreate(state);prefs=getSharedPreferences("da_caller",MODE_PRIVATE);
  getWindow().setStatusBarColor(surface());getWindow().setNavigationBarColor(surface());
  getWindow().getDecorView().setSystemUiVisibility(dark()?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
  root.setLayoutDirection(I18n.isEnglish(this)?View.LAYOUT_DIRECTION_LTR:View.LAYOUT_DIRECTION_RTL);
  root.setBackgroundColor(surface());setContentView(root);
  LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(8),dp(4),dp(8),dp(4));
  root.addView(header,new LinearLayout.LayoutParams(-1,-2));
  TextView back=text(I18n.isEnglish(this)?"‹":"›",32,false,gold());back.setGravity(Gravity.CENTER);
  back.setContentDescription(tr("חזרה","Back"));header.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
  back.setOnClickListener(v->finish());
  TextView title=text("ארכיון שיחות",20,true,ink());header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
  TextView reload=text("↻",25,false,gold());reload.setGravity(Gravity.CENTER);
  reload.setContentDescription(tr("רענון ארכיון","Refresh archive"));header.addView(reload,new LinearLayout.LayoutParams(dp(48),dp(48)));
  reload.setOnClickListener(v->load());
  subtitle=text("שיחות שנשמרו לאחר שנעלמו מיומן המכשיר",12,false,muted());
  subtitle.setPadding(dp(16),0,dp(16),dp(8));root.addView(subtitle,new LinearLayout.LayoutParams(-1,-2));
  search=new EditText(this);search.setSingleLine(true);search.setTextColor(ink());search.setHintTextColor(muted());
  search.setTextSize(15);search.setHint(tr("חיפוש מספר בארכיון","Search archive by number"));
  search.setContentDescription(search.getHint());search.setMinHeight(dp(48));search.setBackground(shape(card(),12));
  search.setPadding(dp(14),dp(6),dp(14),dp(6));
  LinearLayout.LayoutParams searchParams=new LinearLayout.LayoutParams(-1,-2);searchParams.setMargins(dp(12),0,dp(12),dp(8));root.addView(search,searchParams);
  ListView list=new ListView(this);list.setDivider(null);list.setSelector(android.R.color.transparent);
  root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
  adapter=new BaseAdapter(){
   private final DateFormat df=DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT,locale());
   @Override public int getCount(){return calls.size();}
   @Override public Object getItem(int p){return calls.get(p);}
   @Override public long getItemId(int p){return calls.get(p).id;}
   @Override public View getView(int position,View convert,ViewGroup parent){
    LinearLayout row;TextView number,description;
    if(convert instanceof LinearLayout){
     row=(LinearLayout)convert;number=(TextView)row.getChildAt(0);description=(TextView)row.getChildAt(1);
    }else{
     row=new LinearLayout(ArchiveActivity.this);row.setOrientation(LinearLayout.VERTICAL);
     row.setPadding(dp(16),dp(10),dp(16),dp(10));row.setBackground(shape(card(),12));
     number=text("",16,true,ink());number.setSingleLine(true);number.setEllipsize(TextUtils.TruncateAt.END);
     row.addView(number,new LinearLayout.LayoutParams(-1,-2));
     description=text("",12,false,muted());description.setPadding(0,dp(5),0,0);row.addView(description,new LinearLayout.LayoutParams(-1,-2));
    }
    CallHistory.Entry entry=calls.get(position);
    String display=entry.number.isEmpty()?I18n.text(ArchiveActivity.this,"מספר חסוי"):entry.number;
    String details=String.format(locale(),"%s  ·  %s  ·  %d:%02d",type(entry.type),df.format(new Date(entry.date)),entry.duration/60,entry.duration%60);
    number.setText(display);description.setText(details);
    row.setContentDescription(String.format(locale(),"%s. %s",display,details));
    // Capture immutable entry/details, never text from a potentially recycled row.
    row.setOnClickListener(v->{
     AlertDialog.Builder dialog=new AlertDialog.Builder(ArchiveActivity.this).setTitle(display).setMessage(details)
       .setNegativeButton(I18n.text(ArchiveActivity.this,"סגור"),null);
     String dialable=PhoneNumberUtils.extractNetworkPortion(entry.number);
     if(!dialable.isEmpty())dialog.setPositiveButton(I18n.text(ArchiveActivity.this,"חייג"),(d,which)->{
      try{startActivity(new Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",entry.number,null)));}
      catch(RuntimeException unavailable){Toast.makeText(ArchiveActivity.this,I18n.text(ArchiveActivity.this,"לא ניתן להתקשר"),Toast.LENGTH_LONG).show();}
     });
     dialog.show();
    });
    return row;
   }
  };
  list.setAdapter(adapter);
  search.addTextChangedListener(new TextWatcher(){
   public void beforeTextChanged(CharSequence s,int start,int count,int after){}
   public void onTextChanged(CharSequence s,int start,int before,int count){if(loaded)filter();}
   public void afterTextChanged(Editable value){}
  });
  if(state!=null)search.setText(state.getString("query",""));
  load();
 }
 private String type(int type){
  if(type==CallLog.Calls.MISSED_TYPE)return I18n.text(this,"לא נענתה");
  if(type==CallLog.Calls.OUTGOING_TYPE)return I18n.text(this,"יוצאת");
  if(type==CallLog.Calls.REJECTED_TYPE)return I18n.text(this,"נדחתה");
  if(type==CallLog.Calls.BLOCKED_TYPE)return I18n.text(this,"נחסמה");
  if(type==CallLog.Calls.VOICEMAIL_TYPE)return tr("תא קולי","Voicemail");
  if(type==CallLog.Calls.ANSWERED_EXTERNALLY_TYPE)return tr("נענתה במכשיר אחר","Answered on another device");
  return I18n.text(this,"נכנסת");
 }
 private void load(){
  loaded=false;int request=++filterGeneration;subtitle.setText(I18n.text(this,"טוען נתוני ארכיון…"));
  IO.execute(()->{
   List<CallHistory.Entry> snapshot=CallArchive.load(getApplicationContext());boolean failed=CallArchive.lastOperationFailed;
   runOnUiThread(()->{
    if(isFinishing()||isDestroyed()||request!=filterGeneration)return;
    loaded=true;allCalls=snapshot;
    if(failed){subtitle.setText(tr("לא ניתן לטעון את הארכיון. נסה לרענן.","Could not load archive. Try refreshing."));return;}
    filter();
   });
  });
 }
 private void filter(){
  int request=++filterGeneration;String query=search.getText().toString().trim();
  String digits=PhoneNumberUtils.normalizeNumber(query);List<CallHistory.Entry> snapshot=allCalls;
  IO.execute(()->{
   ArrayList<CallHistory.Entry> matches=new ArrayList<>();
   for(CallHistory.Entry call:snapshot){
    if(request!=filterGeneration)return;
    if(query.isEmpty()||call.number.contains(query)||
       (!digits.isEmpty()&&PhoneNumberUtils.normalizeNumber(call.number).contains(digits)))matches.add(call);
   }
   runOnUiThread(()->{
    if(isFinishing()||isDestroyed()||request!=filterGeneration)return;
    calls.clear();calls.addAll(matches);adapter.notifyDataSetChanged();
    subtitle.setText(matches.isEmpty()?tr("אין שיחות תואמות בארכיון","No matching archived calls"):
      String.format(locale(),tr("%d מתוך %d שיחות בארכיון","%d of %d archived calls"),matches.size(),allCalls.size()));
   });
  });
 }
 @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("query",search.getText().toString());}
 @Override protected void onDestroy(){filterGeneration++;super.onDestroy();}
}
