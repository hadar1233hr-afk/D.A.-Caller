package com.dacaller.app;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Organized Material-inspired settings, fully scoped to the app's theme/language. */
final class SettingsScreen {
 private static final ExecutorService IO=Executors.newSingleThreadExecutor();
 private static final int ROOT_ID=View.generateViewId();
 private final java.util.ArrayList<Runnable> permissionUpdates=new java.util.ArrayList<>();
 private final MainActivity host;
 private final SharedPreferences prefs;
 private final boolean dark;
 private final int bg,ink,subtle,surface,accent,divider;
 private LinearLayout root,card;
 private LinearLayout sectionHeader;
 private int sectionNumber=0;
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
  root=column();root.setId(ROOT_ID);root.setTag(this);
  root.setPadding(dp(13),dp(14),dp(13),dp(24));scroll.addView(root);
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
     WidgetSupport.refreshAll(host);host.refreshPagePreserveScroll();
    });
  choice("שפה","עברית או English","language",
    new String[]{"עברית","אנגלית"},new String[]{"he","en"},
    prefs.getString("language","he"),value->{
     prefs.edit().putString("language",value).apply();
     WidgetSupport.refreshAll(host);host.refreshPagePreserveScroll();
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
    "Round profile and spacious action grid","Four actions and rounded in-call sheets","Lightweight glass surfaces and a symmetric grid",
    "Horizontal profile and large action cards","Technical typography, original dots and monochrome"}[i]:CallThemes.DESCRIPTIONS[i];
   String val=prefs.getString("call_theme","pixel");
   int[] shades={0xFF95BCEB,0xFFFFDCC1,0xFFC6D3E5,0xFFA4D6C7,0xFFE85454};
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
  section("שיחות ומענה","");
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
  TextView controlNote=text("בעיצוב Google Phone ארבע פעולות הליבה נשארות זמינות תמיד; הגדרות הכפתורים חלות על פעולות נוספות ועל שאר העיצובים.",11,false,subtle);
  controlNote.setPadding(dp(12),dp(10),dp(12),dp(10));card.addView(controlNote);
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
  section("הרשאות וחיבור למערכת","✓");
  cardStart();
  permissionOption("חייגן ברירת מחדל","ניהול שיחות נכנסות ויוצאות",
    this::isDefaultDialer,()->{
     if(isDefaultDialer())openSystemSettings(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS);
     else host.requestDefaultDialer();
    });
  permissionOption("הרשאת שיחות","חיוג מאובטח דרך מערכת הטלפון",
    ()->granted(Manifest.permission.CALL_PHONE),()->requestPermission(Manifest.permission.CALL_PHONE,26));
  permissionOption("הרשאת אנשי קשר","הצגת תמונות, אנשי קשר ועריכה",
    ()->granted(Manifest.permission.READ_CONTACTS),()->requestPermission(Manifest.permission.READ_CONTACTS,21));
  permissionOption("הרשאת יומן שיחות","לקריאת ההיסטוריה ואחסון ארכיון",
    ()->granted(Manifest.permission.READ_CALL_LOG),()->requestPermission(Manifest.permission.READ_CALL_LOG,22));
  permissionOption("התראות שיחה","התראות במסך הנעילה ובשורת המצב",
    this::notificationsEnabled,()->{
     if(Build.VERSION.SDK_INT>=33&&!granted(Manifest.permission.POST_NOTIFICATIONS))
      requestPermission(Manifest.permission.POST_NOTIFICATIONS,24);
     else host.openNotificationSettings();
    });
  if(Build.VERSION.SDK_INT>=31)permissionOption("שם התקן Bluetooth","רשות אופציונלית להצגת שם האוזנייה המחוברת",
    ()->granted(Manifest.permission.BLUETOOTH_CONNECT),()->requestPermission(Manifest.permission.BLUETOOTH_CONNECT,27));
  option("אבחון תקלה בחיוג","סוג התקלה ומיקום בקוד, ללא מספר טלפון",()->{
   String report=prefs.getString("last_call_failure",tr("לא נרשמה תקלה בשירות השיחות בגרסה זו."));
   new AlertDialog.Builder(host)
     .setTitle(tr("אבחון שיחה"))
     .setMessage(report)
     .setPositiveButton(tr("העתק"),(dialog,which)->{
      android.content.ClipboardManager clipboard=(android.content.ClipboardManager)host.getSystemService(Context.CLIPBOARD_SERVICE);
      if(clipboard!=null)clipboard.setPrimaryClip(android.content.ClipData.newPlainText("DA CALLER diagnostic",report));
     })
     .setNegativeButton(tr("סגור"),null).show();
  });
  cardEnd();
  TextView bottom=text("DA CALLER · v"+BuildConfig.VERSION_NAME,11,false,subtle);
  bottom.setGravity(Gravity.CENTER);
  LinearLayout.LayoutParams foot=new LinearLayout.LayoutParams(-1,dp(51));root.addView(bottom,foot);
  refreshPermissions();return scroll;
 }
 /** Refresh the visible status rows after returning from Android permission/role UI.
  * Updates in place, preserving the open card and the user's exact scroll position. */
 static void refreshAttached(MainActivity activity){
  View root=activity.findViewById(ROOT_ID);
  if(root!=null&&root.getTag() instanceof SettingsScreen)((SettingsScreen)root.getTag()).refreshPermissions();
 }
 private interface Status {boolean active();}
 private boolean granted(String permission){return host.checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED;}
 private boolean isDefaultDialer(){
  try{
   if(Build.VERSION.SDK_INT>=29){RoleManager roles=host.getSystemService(RoleManager.class);
    return roles!=null&&roles.isRoleHeld(RoleManager.ROLE_DIALER);}
   TelecomManager telecom=host.getSystemService(TelecomManager.class);
   return telecom!=null&&host.getPackageName().equals(telecom.getDefaultDialerPackage());
  }catch(RuntimeException ignored){return false;}
 }
 private boolean notificationsEnabled(){
  if(Build.VERSION.SDK_INT>=33&&!granted(Manifest.permission.POST_NOTIFICATIONS))return false;
  NotificationManager notifications=host.getSystemService(NotificationManager.class);
  return notifications!=null&&notifications.areNotificationsEnabled();
 }
 private void openSystemSettings(String action){
  try{host.startActivity(new Intent(action));}
  catch(RuntimeException ignored){openAppSettings();}
 }
 private void openAppSettings(){
  try{host.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+host.getPackageName())));}
  catch(RuntimeException ignored){android.widget.Toast.makeText(host,tr("פתח את הרשאות האפליקציה בהגדרות המכשיר"),android.widget.Toast.LENGTH_LONG).show();}
 }
 private void requestPermission(String permission,int code){
  String asked="settings_permission_asked_"+permission;
  if(granted(permission)||(prefs.getBoolean(asked,false)&&!host.shouldShowRequestPermissionRationale(permission))){openAppSettings();return;}
  prefs.edit().putBoolean(asked,true).apply();host.requestPermissions(new String[]{permission},code);
 }
 private void permissionOption(String label,String description,Status status,Runnable action){
  LinearLayout item=row();item.setPadding(dp(12),dp(12),dp(12),dp(12));item.setMinimumHeight(dp(76));
  card.addView(item,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout copy=column();item.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
  copy.addView(text(label,14,true,ink));
  TextView detail=text(description,11,false,subtle);detail.setPadding(0,dp(3),dp(6),0);copy.addView(detail);
  LinearLayout state=column();state.setPadding(dp(8),0,0,0);state.setGravity(Gravity.CENTER);
  item.addView(state,new LinearLayout.LayoutParams(-2,-2));
  TextView current=text("",11,true,subtle);state.addView(current);
  TextView control=text("",12,true,accent);control.setPadding(dp(6),dp(5),dp(6),0);state.addView(control);
  permissionUpdates.add(()->{
   boolean active=status.active();current.setText(tr(active?"פעיל":"נדרש אישור"));
   current.setTextColor(active?(dark?0xFF9ED8AC:0xFF2C653C):subtle);
   control.setText(tr(active?"ניהול":"אשר"));
   item.setContentDescription(tr(label)+", "+current.getText()+", "+control.getText());
  });
  item.setFocusable(true);TouchFeedback.bind(item,prefs);
  item.setOnClickListener(v->{TouchFeedback.pulse(host,prefs,item);action.run();});sep();
 }
 private void refreshPermissions(){for(Runnable update:permissionUpdates)update.run();}
 private void section(String label,String symbol){
  final String key="settings_group_open_"+sectionNumber++;
  LinearLayout header=row();
  header.setPadding(dp(15),dp(3),dp(12),dp(3));
  header.setBackground(card(surface,19));
  header.setMinimumHeight(dp(57));
  LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);
  hp.topMargin=dp(12);
  root.addView(header,hp);
  if(!symbol.isEmpty()){
   TextView bullet=text(symbol,20,true,accent);
   header.addView(bullet,new LinearLayout.LayoutParams(dp(36),dp(48)));
  }
  TextView name=text(label,16,true,ink);
  header.addView(name,new LinearLayout.LayoutParams(0,-2,1));
  TextView arrow=text(prefs.getBoolean(key,false)?"⌃":"⌄",23,true,accent);
  arrow.setGravity(Gravity.CENTER);
  header.addView(arrow,new LinearLayout.LayoutParams(dp(35),dp(48)));
  header.setContentDescription(tr(label));
  if(Build.VERSION.SDK_INT>=30)header.setStateDescription(tr(prefs.getBoolean(key,false)?"מורחב":"מכווץ"));
  header.setFocusable(true);
  sectionHeader=header;
  header.setTag(new Object[]{key,arrow});
 }
 private void cardStart(){
  card=column();
  card.setBackground(card(surface,18));
  card.setPadding(dp(6),dp(8),dp(6),dp(8));
  LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
  cp.topMargin=dp(4);
  root.addView(card,cp);
  if(sectionHeader!=null){
   final LinearLayout content=card;
   final LinearLayout header=sectionHeader;
   final Object[] state=(Object[])header.getTag();
   final String key=(String)state[0];
   final TextView arrow=(TextView)state[1];
   content.setVisibility(prefs.getBoolean(key,false)?View.VISIBLE:View.GONE);
   header.setOnClickListener(v->{
    TouchFeedback.pulse(host,prefs,header);
    boolean expanded=content.getVisibility()!=View.VISIBLE;
    // Whole-tree AutoTransition used to animate every setting row and suppress
    // layout in this ScrollView while the last, tall section changed size. Apply
    // visibility immediately; fade only the new card without moving hit regions.
    content.animate().cancel();content.setAlpha(1f);
    content.setVisibility(expanded?View.VISIBLE:View.GONE);
    if(expanded&&ValueAnimator.areAnimatorsEnabled()){
     content.setAlpha(0f);content.animate().alpha(1f).setDuration(170).start();
    }
    if(Build.VERSION.SDK_INT>=30)header.setStateDescription(tr(expanded?"מורחב":"מכווץ"));
    arrow.setText(expanded?"⌃":"⌄");
    prefs.edit().putBoolean(key,expanded).apply();
   });
  }
  sectionHeader=null;
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
