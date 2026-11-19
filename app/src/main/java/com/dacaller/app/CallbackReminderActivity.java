package com.dacaller.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.CallLog;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsetsController;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.DateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** A read-only local daily list. Opening it never acknowledges or deletes call-log rows. */
public final class CallbackReminderActivity extends Activity {
 private final Handler handler=new Handler(Looper.getMainLooper());
 private SharedPreferences prefs;
 private ListView list;
 private TextView status,summary,date;
 private boolean english,dark,resumed,observing,loading,reloadPending;
 private int background,surface,ink,secondary,accent,generation;
 private int restorePosition,restoreTop;private boolean restorePending;
 private String selectedDay;
 private List<CallbackReminderModel.Entry> entries=Collections.emptyList();
 private final Entries adapter=new Entries();
 private final Runnable changed=()->{refresh();CallbackReminder.maybeRefresh(this);};
 private final ContentObserver observer=new ContentObserver(handler){
  @Override public void onChange(boolean selfChange){handler.removeCallbacks(changed);handler.postDelayed(changed,350);}
 };
 private int dp(float value){return Math.round(getResources().getDisplayMetrics().density*value);}
 private String tr(String he,String en){return english?en:he;}
 private LinearLayout column(){LinearLayout view=new LinearLayout(this);view.setOrientation(LinearLayout.VERTICAL);return view;}
 private TextView text(String value,int sp,boolean strong,int color){
  TextView view=new TextView(this);view.setText(value);view.setTextSize(sp);view.setTextColor(color);
  view.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);view.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
  AppAppearance.font(view,strong);view.setLineSpacing(dp(2),1f);return view;
 }
 private GradientDrawable shape(int color,int radius){GradientDrawable view=new GradientDrawable();view.setColor(color);view.setCornerRadius(dp(radius));return view;}
 @Override public void onCreate(Bundle state){
  super.onCreate(state);CallReturnNavigation.install(this);prefs=getSharedPreferences("da_caller",Context.MODE_PRIVATE);
  english=I18n.isEnglish(this);dark=prefs.getBoolean("dark",false)||prefs.getBoolean("amoled",false);
  background=prefs.getBoolean("amoled",false)?Color.BLACK:dark?0xFF141414:0xFFF7F7F5;
  surface=dark?0xFF232323:Color.WHITE;ink=dark?0xFFF5F5F3:0xFF161C17;secondary=dark?0xFFB8BEB9:0xFF59635B;
  accent="silver".equals(prefs.getString("accent","gold"))?(dark?0xFFE4E4E4:0xFF616161):AppAppearance.gold(dark);
  selectedDay=state==null?getIntent().getStringExtra(CallbackReminder.EXTRA_DAY):state.getString(CallbackReminder.EXTRA_DAY);
  LinearLayout root=column();root.setTag("callback-reminder-root");root.setBackgroundColor(background);
  root.setLayoutDirection(english?View.LAYOUT_DIRECTION_LTR:View.LAYOUT_DIRECTION_RTL);root.setPadding(dp(12),dp(8),dp(12),dp(10));setContentView(root);
  LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);root.addView(header,new LinearLayout.LayoutParams(-1,-2));
  ImageView back=new ImageView(this);back.setImageDrawable(AppAppearance.backArrow(this,accent));back.setScaleType(ImageView.ScaleType.CENTER);back.setContentDescription(tr("חזרה","Back"));
  back.setTag("callback-reminder-back");back.setFocusable(true);header.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));back.setOnClickListener(v->leave());
  TextView title=text(tr("שיחות לחזור אליהן","Calls to return"),22,true,ink);title.setTag("callback-reminder-title");header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
  date=text("",13,false,secondary);date.setTag("callback-reminder-date");date.setPadding(dp(6),dp(5),dp(6),0);root.addView(date,new LinearLayout.LayoutParams(-1,-2));
  summary=text("",15,true,accent);summary.setTag("callback-reminder-summary");summary.setPadding(dp(6),dp(7),dp(6),dp(4));root.addView(summary,new LinearLayout.LayoutParams(-1,-2));
  TextView explanation=text(tr("שיחה שהתחברה ליותר משנייה לאחר השיחה האחרונה שלא נענתה מסירה את המספר מהרשימה.",
    "A connected call lasting over one second after the latest missed call removes that number from this list."),12,false,secondary);
  explanation.setTag("callback-reminder-explanation");explanation.setPadding(dp(6),dp(3),dp(6),dp(8));root.addView(explanation,new LinearLayout.LayoutParams(-1,-2));
  status=text(tr("טוען שיחות מקומיות…","Loading local calls…"),14,false,secondary);status.setTag("callback-reminder-status");status.setPadding(dp(10),dp(10),dp(10),dp(10));root.addView(status,new LinearLayout.LayoutParams(-1,-2));
  list=new ListView(this);list.setTag("callback-reminder-scroll");list.setAdapter(adapter);list.setDivider(null);list.setClipToPadding(false);
  list.setPadding(0,0,0,dp(6));root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
  TextView recent=text(tr("לשיחות האחרונות","Recent calls"),14,true,accent);recent.setTag("callback-reminder-recents");recent.setGravity(Gravity.CENTER);
  recent.setFocusable(true);recent.setMinimumHeight(dp(48));recent.setBackground(shape(surface,16));root.addView(recent,new LinearLayout.LayoutParams(-1,-2));
  recent.setOnClickListener(v->{startActivity(new Intent(this,MainActivity.class).putExtra("show_recents",true)
    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));finish();});
  applySystemBars();
  if(state!=null){restorePosition=Math.max(0,state.getInt("list_position",0));restoreTop=state.getInt("list_top",0);restorePending=true;}
 }
 private void refresh(){
  if(!resumed)return;if(loading){reloadPending=true;return;}loading=true;int ticket=++generation;
  long observedAt=System.currentTimeMillis();String requested=selectedDay;
  CallbackReminder.worker.execute(()->{
   CallbackReminder.Result result=CallbackReminder.load(getApplicationContext(),observedAt,requested);
   handler.post(()->{
    if(!resumed||ticket!=generation||isFinishing()||isDestroyed())return;
    loading=false;int position=list.getFirstVisiblePosition(),top=list.getChildCount()>0?list.getChildAt(0).getTop():0;
    DateFormat format=DateFormat.getDateInstance(DateFormat.MEDIUM,english?Locale.ENGLISH:new Locale("he","IL"));
    date.setText(format.format(new Date(result.day.start)));entries=result.entries;adapter.notifyDataSetChanged();
    if(restorePending){list.setSelectionFromTop(restorePosition,restoreTop);restorePending=false;}else list.setSelectionFromTop(position,top);
    int missed=0;for(CallbackReminderModel.Entry entry:entries)missed+=entry.missedCountToday;
    summary.setText(entries.isEmpty()?tr("אין שיחות שממתינות לחזרה","No calls waiting for your return"):
      tr((entries.size()==1?"מספר אחד":entries.size()+" מספרים")+" · "+(missed==1?"שיחה אחת שלא נענתה ביום זה":missed+" שיחות שלא נענו ביום זה"),entries.size()+(entries.size()==1?" caller · ":" callers · ")+missed+(missed==1?" missed call on this day":" missed calls on this day")));
    if(!result.error.isEmpty()){
     summary.setText(tr("לא ניתן לטעון את השיחות כרגע","Calls are currently unavailable"));
     status.setText(CallbackReminder.ERROR_ROLE.equals(result.error)?tr("יש לבחור ב־DA CALLER כחייגן ברירת המחדל בהגדרות.","Choose DA CALLER as the default dialer in Settings."):
      CallbackReminder.ERROR_PERMISSION.equals(result.error)?tr("נדרשת הרשאת יומן שיחות בהגדרות האפליקציה.","Call-log permission is required in the app's Settings."):
       tr("יומן השיחות אינו זמין כרגע. נסה לפתוח את הרשימה שוב.","The call log is unavailable. Try opening this list again."));
     status.setVisibility(View.VISIBLE);
    }else if(entries.isEmpty()){
     status.setText(tr("הכול מעודכן. הרשימה משתמשת רק ביומן השיחות המקומי ואינה מוחקת ממנו מידע.",
       "You're up to date. This list uses the local call log and never removes its records."));status.setVisibility(View.VISIBLE);
    }else status.setVisibility(View.GONE);
    if(reloadPending){reloadPending=false;refresh();}
   });
  });
 }
 private final class Entries extends BaseAdapter {
  @Override public int getCount(){return entries.size();}
  @Override public Object getItem(int position){return entries.get(position);}
  @Override public long getItemId(int position){return entries.get(position).latestMissedId;}
  @Override public View getView(int position,View recycled,ViewGroup parent){
   CallbackReminderModel.Entry entry=entries.get(position);
   LinearLayout outer=column();outer.setPadding(0,dp(5),0,dp(5));outer.setTag("callback-reminder-entry-"+entry.key);
   LinearLayout card=column();card.setBackground(shape(surface,18));card.setPadding(dp(14),dp(12),dp(14),dp(9));outer.addView(card,new LinearLayout.LayoutParams(-1,-2));
   TextView name=text(entry.name,17,true,ink);name.setTag("callback-reminder-name-"+entry.key);card.addView(name,new LinearLayout.LayoutParams(-1,-2));
   if(!entry.number.isEmpty()&&!entry.number.equals(entry.name)){
    TextView number=text(entry.number,14,false,secondary);number.setTextDirection(View.TEXT_DIRECTION_LTR);number.setTag("callback-reminder-number-"+entry.key);
    number.setPadding(0,dp(3),0,0);card.addView(number,new LinearLayout.LayoutParams(-1,-2));
   }
   TextView counts=text(tr("שיחות שלא נענו ביום זה: "+entry.missedCountToday,"Missed on this day: "+entry.missedCountToday),14,true,accent);
   counts.setTag("callback-reminder-counts-"+entry.key);counts.setPadding(0,dp(5),0,0);card.addView(counts,new LinearLayout.LayoutParams(-1,-2));
   if(entry.missedCountToday>entry.missedCount){
    TextView total=text(tr("מהשיחה שהתחברה לאחרונה: "+entry.missedCount,"Since the last connected call: "+entry.missedCount),12,false,secondary);card.addView(total,new LinearLayout.LayoutParams(-1,-2));
   }
   if(entry.callbackAttempts>0){
    TextView attempts=text(tr("ניסיונות חזרה מאז השיחה האחרונה שלא נענתה, ללא חיבור של יותר משנייה: "+entry.callbackAttempts,
      "Return attempts after the latest missed call, without a connection over one second: "+entry.callbackAttempts),12,false,secondary);
    attempts.setTag("callback-reminder-attempts-"+entry.key);attempts.setPadding(0,dp(4),0,0);card.addView(attempts,new LinearLayout.LayoutParams(-1,-2));
   }
   if(!entry.number.isEmpty()&&CallLauncher.isCallable(entry.number)){
    TextView call=text(tr("חזרה לשיחה","Call back"),14,true,accent);call.setTag("callback-reminder-call-"+entry.key);call.setFocusable(true);call.setGravity(Gravity.CENTER);call.setMinimumHeight(dp(48));
    String spoken=BidiFormatter.getInstance(!english).unicodeWrap(entry.number,TextDirectionHeuristics.LTR);
    call.setContentDescription(tr("התקשר אל ","Call ")+entry.name+" "+spoken);card.addView(call,new LinearLayout.LayoutParams(-1,-2));
    call.setOnClickListener(v->{if(!resumed||!entries.contains(entry))return;
     if(!CallLauncher.place(CallbackReminderActivity.this,entry.number))Toast.makeText(CallbackReminderActivity.this,
       tr("לא ניתן להתחיל את השיחה","Unable to start the call"),Toast.LENGTH_SHORT).show();});
   }else{
    TextView hidden=text(tr("אין מספר זמין לחזרה","No number available to call back"),12,false,secondary);hidden.setPadding(0,dp(6),0,dp(3));card.addView(hidden,new LinearLayout.LayoutParams(-1,-2));
   }
   return outer;
  }
 }
 private void leave(){
  if(isTaskRoot())startActivity(new Intent(this,MainActivity.class).putExtra("show_recents",true)
    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));finish();
 }
 @Override public void onBackPressed(){leave();}
 private void applySystemBars(){
  getWindow().setStatusBarColor(background);getWindow().setNavigationBarColor(background);
  if(Build.VERSION.SDK_INT>=30){WindowInsetsController bars=getWindow().getInsetsController();if(bars!=null){
   int mask=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;bars.setSystemBarsAppearance(dark?0:mask,mask);
  }}else getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
 }
 @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused&&prefs!=null)applySystemBars();}
 @Override protected void onResume(){super.onResume();resumed=true;applySystemBars();
  try{getContentResolver().registerContentObserver(CallLog.Calls.CONTENT_URI,true,observer);observing=true;}catch(RuntimeException unavailable){observing=false;}
  refresh();
 }
 @Override protected void onStop(){resumed=false;generation++;loading=false;reloadPending=false;handler.removeCallbacks(changed);
  if(observing){try{getContentResolver().unregisterContentObserver(observer);}catch(RuntimeException ignored){}observing=false;}super.onStop();
 }
 @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString(CallbackReminder.EXTRA_DAY,selectedDay);
  if(list!=null){state.putInt("list_position",list.getFirstVisiblePosition());state.putInt("list_top",list.getChildCount()>0?list.getChildAt(0).getTop():0);}
 }
}
