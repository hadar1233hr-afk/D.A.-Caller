package com.dacaller.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Organized Material-inspired settings, fully scoped to the app's theme/language. */
final class SettingsScreen {
 private static final ExecutorService IO=Executors.newSingleThreadExecutor();
 private final MainActivity host;
 private final SharedPreferences prefs;
 private final boolean dark;
 private final int bg,ink,subtle,surface,accent,divider;
 private LinearLayout root,card;
 private int dp(float n){return (int)(host.getResources().getDisplayMetrics().density*n+0.5f);}
 private String tr(String heb){return I18n.text(host,heb);}
 SettingsScreen(MainActivity host){
  this.host=host;
  prefs=host.getSharedPreferences("da_caller",Context.MODE_PRIVATE);
  dark=prefs.getBoolean("dark",false)||prefs.getBoolean("amoled",false);
  bg=prefs.getBoolean("amoled",false)?Color.BLACK:dark?0xFF141414:0xFFF7F7F5;
  ink=dark?Color.WHITE:0xFF161616;
  subtle=dark?0xFFA9A9A9:0xFF6B6B6B;
  surface=dark?0xFF232323:Color.WHITE;
  accent=dark?0xFFE1C38A:0xFF926C37;
  divider=dark?0xFF383838:0xFFE5E5E2;
 }
 private GradientDrawable card(int c,int radius){
  GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(radius));return g;
 }
 private TextView text(String heb,int size,boolean bold,int color){
  TextView v=new TextView(host);v.setText(tr(heb));v.setTextSize(size);v.setTextColor(color);
  v.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));
  v.setGravity(Gravity.CENTER_VERTICAL);v.setIncludeFontPadding(false);return v;
 }
 private LinearLayout column(){LinearLayout l=new LinearLayout(host);l.setOrientation(LinearLayout.VERTICAL);
  l.setLayoutDirection(I18n.isEnglish(host)?View.LAYOUT_DIRECTION_LTR:View.LAYOUT_DIRECTION_RTL);return l;
 }
 private LinearLayout row(){LinearLayout l=new LinearLayout(host);l.setGravity(Gravity.CENTER_VERTICAL);
  l.setLayoutDirection(I18n.isEnglish(host)?View.LAYOUT_DIRECTION_LTR:View.LAYOUT_DIRECTION_RTL);return l;
 }
 ScrollView create(){
  ScrollView scroll=new ScrollView(host);scroll.setVerticalScrollBarEnabled(false);
  scroll.setBackgroundColor(bg);scroll.setFillViewport(true);
  root=column();root.setPadding(dp(13),dp(14),dp(13),dp(24));scroll.addView(root);
  TextView headline=text("התאמה אישית",23,true,ink);
  root.addView(headline,new LinearLayout.LayoutParams(-1,dp(40)));
  TextView sub=text("כל ההגדרות נשמרות במכשיר",11,false,subtle);
  root.addView(sub,new LinearLayout.LayoutParams(-1,dp(27)));
  section("מראה","◈");
  cardStart();
  choice("מצב תצוגה","בהיר · כהה · AMOLED","display_mode",
    new String[]{"בהיר","כהה","שחור AMOLED"},new String[]{"light","dark","amoled"},
    prefs.getBoolean("amoled",false)?"amoled":prefs.getBoolean("dark",false)?"dark":"light",value->{
     prefs.edit().putBoolean("dark",!value.equals("light"))
       .putBoolean("amoled",value.equals("amoled")).apply();
     host.refreshPagePreserveScroll();
    });
  choice("שפה","עברית או English","language",
    new String[]{"עברית","אנגלית"},new String[]{"he","en"},
    prefs.getString("language","he"),value->{
     prefs.edit().putString("language",value).apply();
     host.refreshPagePreserveScroll();
    });
  choice("צבע לחצן חיוג ביומן","זהב או ירוק","recent_color",
    new String[]{"זהב","ירוק"},new String[]{"gold","green"},
    prefs.getBoolean("recents_green",false)?"green":"gold",value->{
     prefs.edit().putBoolean("recents_green",value.equals("green")).apply();
     host.refreshPagePreserveScroll();
    });
  toggle("הצגת סיכום פעילות","סיכום דקות ושיחות בשורה קומפקטית",
     "show_stats",prefs.getBoolean("show_stats",true));
  cardEnd();
  section("חמישה עיצובים שונים למסך שיחה","✦");
  cardStart();
  for(int i=0;i<CallThemes.IDS.length;i++){
   final String id=CallThemes.IDS[i];
   String label=CallThemes.NAMES[i];
   String description=I18n.isEnglish(host)?new String[]{
    "Two-column command deck","Google Expressive-inspired action dock","Oversized vertical controls",
    "Three-column dashboard","Minimal two-column arrangement"}[i]:CallThemes.DESCRIPTIONS[i];
   String val=prefs.getString("call_theme","graphite");
   int[] shades={0xFFD1AD70,0xFF6198E9,0xFF23B188,0xFF9AA1E9,0xFFC5956D};
   LinearLayout item=row();item.setPadding(dp(12),dp(6),dp(12),dp(6));
   item.setMinimumHeight(dp(69));
   LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
   card.addView(item,params);
   View swatch=new View(host);swatch.setBackground(card(shades[i],14));
   item.addView(swatch,new LinearLayout.LayoutParams(dp(39),dp(39)));
   LinearLayout copy=column();copy.setPadding(dp(12),0,0,0);
   item.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
   copy.addView(text(label,14,true,ink));
   copy.addView(text(description,11,false,subtle));
   TextView check=text(id.equals(val)?"●":"○",23,true,id.equals(val)?accent:subtle);
   item.addView(check);
   item.setOnClickListener(v->{
    TouchFeedback.pulse(host,prefs,item);
    prefs.edit().putString("call_theme",id).apply();
    host.refreshPagePreserveScroll();
   });
   sep();
  }
  cardEnd();
  section("שיחות ומענה","☎");
  cardStart();
  choice("אופן מענה לשיחה","מחוות החלקה לבחירה","answer_mode",
    new String[]{"החלקה ימינה למענה","החלקה שמאלה למענה",
      "למעלה למענה, למטה לדחייה","iOS · ימינה למענה, הקשה לדחייה"},
    new String[]{"right","left","vertical","ios"},
    prefs.getString("answer_mode","ios"),v->{
     prefs.edit().putString("answer_mode",v).apply();
     host.refreshPagePreserveScroll();
    });
  choice("גובה כפתורי השיחה","קומפקטי או מרווח","call_button_height",
    new String[]{"קומפקטי","רגיל","מרווח"},
    new String[]{"56","65","75"},String.valueOf(prefs.getInt("call_button_height",65)),v->{
     prefs.edit().putInt("call_button_height",Integer.parseInt(v)).apply();
     host.refreshPagePreserveScroll();
    });
  for(int i=0;i<InCallControls.IDS.length;i++){
   String id=InCallControls.IDS[i];
   toggle(InCallControls.NAMES[i],InCallControls.EXPLAIN[i],
    "call_btn_"+id,InCallControls.enabled(prefs,id));
  }
  cardEnd();
  section("ארכיון אישי","◷");
  cardStart();
  choice("שמירה מעבר ליומן אנדרואיד",
    "רק שיחות שנעלמו מיומן המכשיר מוצגות בארכיון","archive_extra",
    new String[]{"כבוי","חודש נוסף","3 חודשים נוספים","חצי שנה נוספת","שנה נוספת","ללא הגבלת זמן"},
    CallArchive.OPTIONS,prefs.getString(CallArchive.PREF,CallArchive.OFF),value->{
     prefs.edit().putString(CallArchive.PREF,value).apply();
     CallArchive.schedule(host);
     if(!CallArchive.OFF.equals(value))host.reloadCallLog();
     else IO.execute(()->CallArchive.pruneNow(host));
     host.refreshPagePreserveScroll();
    });
  TextView archiveNote=text("השמירה מתחילה כשהאפליקציה רואה את השיחה ביומן המערכת. התקופה הנוספת נמדדת מהמועד הראשון שבו זוהתה היעלמות הרשומה.",11,false,subtle);
  archiveNote.setPadding(dp(12),dp(10),dp(12),dp(11));
  card.addView(archiveNote);
  TextView offNote=text("כבוי מפסיק שמירה חדשה. למחיקת נתונים קיימים בחר מחיקת ארכיון.",11,false,subtle);
  offNote.setPadding(dp(12),0,dp(12),dp(11));card.addView(offNote);
  option("פתיחת ארכיון שיחות","סקירת ההיסטוריה שנשמרה",()->host.openArchive());
  option("מחיקת ארכיון","מחיקה מקומית שאינה משנה את יומן אנדרואיד",()->{
   new AlertDialog.Builder(host)
    .setTitle(tr("מחיקת כל שיחות הארכיון?"))
    .setMessage(tr("הנתונים יימחקו מהמכשיר ולא ניתן יהיה לשחזרם."))
    .setNegativeButton(tr("ביטול"),null)
    .setPositiveButton(tr("מחק"),(dialog,which)->{
     Context app=host.getApplicationContext();
     IO.execute(()->{
      CallArchive.erase(app);
      boolean failed=CallArchive.lastOperationFailed;
      host.runOnUiThread(()->{
       if(host.isFinishing()||host.isDestroyed())return;
       android.widget.Toast.makeText(host,tr(failed?"לא ניתן למחוק את הארכיון":"נמחק"),android.widget.Toast.LENGTH_LONG).show();
      });
     });
    }).show();
  });
  cardEnd();
  section("מחוות ונוחות","↔");
  cardStart();
  choice("החלקה לחיוג","החלקה על רשומה ביומן ובאנשי קשר","swipe_direction",
    new String[]{"ימינה","שמאלה","כבוי"},new String[]{"right","left","off"},
    prefs.getString("swipe_direction","right"),v->{
     prefs.edit().putString("swipe_direction",v).apply();
     host.refreshPagePreserveScroll();
    });
  toggle("רטט בלחיצה","משוב מישושי בלחצנים מרכזיים","haptics",prefs.getBoolean("haptics",true));
  choice("עוצמת רטט","מותאם לעוצמת משוב ההקשה","haptic_strength",
    new String[]{"חלש","בינוני","חזק"},new String[]{"1","2","3"},
    String.valueOf(prefs.getInt("haptic_strength",2)),value->{
     prefs.edit().putInt("haptic_strength",Integer.parseInt(value)).apply();
     TouchFeedback.pulse(host,prefs,null);
     host.refreshPagePreserveScroll();
    });
  choice("מסך פתיחה","לאיזו לשונית לעבור בעת פתיחת האפליקציה","start_page",
    new String[]{"שיחות","אנשי קשר","מועדפים"},
    new String[]{"recents","contacts","favorites"},
    prefs.getString("start_page","recents"),v->{
     prefs.edit().putString("start_page",v).apply();
     host.refreshPagePreserveScroll();
    });
  cardEnd();
  section("מצב המערכת","✓");
  cardStart();
  option("הגדר כחייגן ברירת מחדל","ניהול שיחות נכנסות ויוצאות",()->host.requestDefaultDialer());
  option("הרשאת יומן שיחות","לקריאת ההיסטוריה ואחסון ארכיון",()->host.requestLog());
  option("הרשאת אנשי קשר","הצגת תמונות, אנשי קשר ועריכה",()->host.requestContacts());
  option("התראות שיחה","התראות במסך הנעילה ובשורת המצב",()->host.openNotificationSettings());
  cardEnd();
  TextView bottom=text("DA CALLER · v"+BuildConfig.VERSION_NAME,11,false,subtle);
  bottom.setGravity(Gravity.CENTER);
  LinearLayout.LayoutParams foot=new LinearLayout.LayoutParams(-1,dp(51));root.addView(bottom,foot);
  return scroll;
 }
 private void section(String label,String symbol){
  LinearLayout line=row();
  LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(46));
  p.topMargin=dp(13);root.addView(line,p);
  TextView bullet=text(symbol,22,true,accent);
  line.addView(bullet,new LinearLayout.LayoutParams(dp(37),dp(43)));
  TextView name=text(label,16,true,ink);
  line.addView(name,new LinearLayout.LayoutParams(0,-2,1));
 }
 private void cardStart(){
  card=column();card.setBackground(card(surface,18));card.setPadding(dp(6),dp(3),dp(6),dp(3));
  root.addView(card,new LinearLayout.LayoutParams(-1,-2));
 }
 private void cardEnd(){card=null;}
 private void sep(){
  if(card==null)return;
  View dividerView=new View(host);dividerView.setBackgroundColor(divider);
  LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(1));
  p.leftMargin=dp(10);p.rightMargin=dp(10);
  card.addView(dividerView,p);
 }
 private void option(String label,String description,Runnable click){
  LinearLayout item=row();item.setPadding(dp(12),dp(12),dp(12),dp(12));
  item.setMinimumHeight(dp(69));card.addView(item,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout descriptionBlock=column();
  item.addView(descriptionBlock,new LinearLayout.LayoutParams(0,-2,1));
  descriptionBlock.addView(text(label,14,true,ink));
  descriptionBlock.addView(text(description,11,false,subtle));
  TextView arrow=text("›",25,false,accent);item.addView(arrow);
  item.setOnClickListener(v->{TouchFeedback.pulse(host,prefs,item);click.run();});
  sep();
 }
 private void toggle(String label,String description,String key,boolean checked){
  LinearLayout item=row();item.setPadding(dp(12),dp(5),dp(12),dp(5));
  item.setMinimumHeight(dp(65));card.addView(item,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout descriptionBlock=column();
  item.addView(descriptionBlock,new LinearLayout.LayoutParams(0,-2,1));
  descriptionBlock.addView(text(label,14,true,ink));
  descriptionBlock.addView(text(description,11,false,subtle));
  Switch sw=new Switch(host);sw.setChecked(checked);sw.setContentDescription(tr(label));item.addView(sw);
  sw.setOnCheckedChangeListener((widget,value)->{
   prefs.edit().putBoolean(key,value).apply();
   TouchFeedback.pulse(host,prefs,sw);
   if(key.equals("show_stats"))host.reloadCallLog();
  });
  item.setOnClickListener(v->sw.setChecked(!sw.isChecked()));
  sep();
 }
 private interface Change {void value(String value);}
 private void choice(String label,String description,String key,String[] names,String[] values,String selected,Change changed){
  LinearLayout item=row();item.setPadding(dp(12),dp(7),dp(12),dp(7));
  item.setMinimumHeight(dp(72));card.addView(item,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout copy=column();item.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
  copy.addView(text(label,14,true,ink));
  String found="";for(int i=0;i<values.length;i++)if(selected.equals(values[i]))found=tr(names[i]);
  String second=found.isEmpty()?tr(description):found;
  copy.addView(text(second,12,false,accent));
  TextView arrow=text("⌄",23,false,subtle);item.addView(arrow);
  item.setOnClickListener(v->{
   TouchFeedback.pulse(host,prefs,item);
   String[] translated=new String[names.length];
   int index=0;
   for(int i=0;i<names.length;i++){translated[i]=tr(names[i]);if(values[i].equals(selected))index=i;}
   new AlertDialog.Builder(host)
    .setTitle(tr(label))
    .setSingleChoiceItems(translated,index,(dialog,which)->{
     dialog.dismiss();changed.value(values[which]);
    }).setNegativeButton(tr("ביטול"),null).show();
  });
  sep();
 }
}
