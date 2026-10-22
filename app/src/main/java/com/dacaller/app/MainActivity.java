package com.dacaller.app;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.CallLog;
import android.telecom.TelecomManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.widget.ImageView;
import android.provider.ContactsContract;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final int GOLD=0xFFD2AE69;
 private SharedPreferences prefs;
 private LinearLayout root,body,nav;
 private String page="recents";
 private int period=0;
 private List<Contacts.Person> contacts=Collections.emptyList();
 private boolean loadingContacts=false;
 private boolean contactsLoaded=false;
 private static final java.util.concurrent.ExecutorService CONTACTS_IO=java.util.concurrent.Executors.newSingleThreadExecutor();
 private String pendingNumber=null;
 private boolean keypadOpen=false;
 private final java.util.Map<String,String> contactNames=new java.util.HashMap<>();
 private int recentDisplayCount=35;
 private String currentSearch="";
 private final android.database.ContentObserver contactsObserver=new android.database.ContentObserver(new Handler(Looper.getMainLooper())){
  @Override public void onChange(boolean selfChange){handler.postDelayed(()->loadContacts(true),500);}
 };
 private int d(float n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
 private boolean dark(){return prefs.getBoolean("dark",false)||prefs.getBoolean("amoled",false);}
 private boolean amoled(){return prefs.getBoolean("amoled",false);}
 private int bg(){return amoled()?Color.BLACK:dark()?0xFF131313:0xFFF5F5F4;}
 private int panel(){return amoled()?0xFF0A0A0A:dark()?0xFF1E1E1E:Color.WHITE;}
 private int text(){return dark()?0xFFF7F7F7:0xFF181818;}
 private int muted(){return dark()?0xFF9D9D9D:0xFF747474;}
 private int border(){return dark()?0xFF343434:0xFFE6E6E5;}
 private int tint(){
  if("silver".equals(prefs.getString("accent","gold")))return dark()?0xFFE0E0E0:0xFF626262;
  return dark()?0xFFE5C987:0xFF99702D;
 }
 private int soft(){return dark()?0xFF24211B:0xFFF3EEE4;}
 private int scaled(int value){return Math.round(value*prefs.getInt("font_"+page,100)/100f);}
 private int screenHeight(){return (int)(getResources().getDisplayMetrics().heightPixels/getResources().getDisplayMetrics().density);}
 private GradientDrawable rounded(int c,int radius){GradientDrawable b=new GradientDrawable();b.setColor(c);b.setCornerRadius(d(radius));return b;}
 private GradientDrawable stroked(int c,int radius){
  int upper=dark()?0xFF282828:0xFFFDFDFD;
  GradientDrawable b=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{upper,c});
  b.setCornerRadius(d(radius));b.setStroke(d(1),border());return b;
 }
 private void haptic(View target){
  if(target!=null&&prefs!=null&&prefs.getBoolean("haptics",true)){
   target.setHapticFeedbackEnabled(true);
   target.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
  }
 }
 private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(1);v.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);return v;}
 private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(0);v.setGravity(Gravity.CENTER_VERTICAL);v.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);return v;}
 private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:d(w),h<0?h:d(h));}
 private void pad(View v,int x,int y){v.setPadding(d(x),d(y),d(x),d(y));}
 private void gap(LinearLayout parent,int size){parent.addView(new View(this),lp(1,size));}
 private TextView t(String content,int size,boolean strong,int color){
  TextView v=new TextView(this);v.setText(content);v.setTextSize(TypedValue.COMPLEX_UNIT_SP,scaled(size));
  v.setTextColor(color);v.setGravity(Gravity.CENTER_VERTICAL);v.setIncludeFontPadding(false);
  v.setTypeface(Typeface.create(strong?"sans-serif-medium":"sans-serif",Typeface.NORMAL));return v;
 }
 private TextView center(String content,int size,boolean bold,int color){
  TextView v=t(content,size,bold,color);v.setGravity(Gravity.CENTER);return v;
 }
 private void heading(String title,String subtitle){
  if(!page.equals("settings"))return;
  body.addView(t("התאמה אישית",19,true,text()));gap(body,12);
 }
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);
  prefs=getSharedPreferences("da_caller",MODE_PRIVATE);
  page=prefs.getString("start_page","recents");
  handleIntent(getIntent());
  render();loadContacts();refreshLog();
  handler.postDelayed(()->{
   if(!prefs.getBoolean("role_prompted",false)&&!isDefault()){
    prefs.edit().putBoolean("role_prompted",true).apply();askDefault();
   }else if(isDefault()&&checkSelfPermission(Manifest.permission.READ_CALL_LOG)==PackageManager.PERMISSION_GRANTED){
    ensureCallNotifications();
   }
  },650);
 }
 @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);handleIntent(intent);render();}
 private void handleIntent(Intent intent){
  if(intent==null)return;
  if(intent.getBooleanExtra("favorites",false))page="favorites";
  if(intent.getBooleanExtra("show_contacts",false))page="contacts";
  if(Intent.ACTION_DIAL.equals(intent.getAction())||intent.hasExtra("phone")){page="recents";pendingNumber=intent.getStringExtra("phone");
   if(Intent.ACTION_DIAL.equals(intent.getAction())&&pendingNumber==null)pendingNumber="";
   if(intent.getData()!=null)pendingNumber=intent.getData().getSchemeSpecificPart();
  }
 }
 @Override public void onResume(){
  super.onResume();
  if(prefs!=null)refreshLog();
  if(checkSelfPermission(Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED && !contactsLoaded)loadContacts(true);
 }
 @Override protected void onStart(){
  super.onStart();
  try{getContentResolver().registerContentObserver(ContactsContract.Contacts.CONTENT_URI,true,contactsObserver);}catch(Exception ignored){}
 }
 @Override protected void onStop(){
  try{getContentResolver().unregisterContentObserver(contactsObserver);}catch(Exception ignored){}
  super.onStop();
 }
 @Override public void onRequestPermissionsResult(int request,String[] requested,int[] results){
  super.onRequestPermissionsResult(request,requested,results);
  if(request==21){contactsLoaded=false;loadContacts(true);render();}
  if(request==22){refreshLog();render();if(isDefault())ensureCallNotifications();}
  if(request==24)ensureVoiceNetworkPermission();
  if(request==23&&pendingNumber!=null){
   String number=pendingNumber;pendingNumber=null;
   if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)makeCall(number);
   else{
    try{startActivity(new Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",number,null)));}
    catch(Exception ignored){Toast.makeText(this,"לא ניתן לפתוח חייגן",Toast.LENGTH_SHORT).show();}
   }
  }
 }
 @Override protected void onActivityResult(int code,int result,Intent data){
  super.onActivityResult(code,result,data);
  if(code==100){
   render();refreshLog();
   if(isDefault()){
    if(checkSelfPermission(Manifest.permission.READ_CALL_LOG)!=PackageManager.PERMISSION_GRANTED)ensureCallLogPermission();
    else ensureCallNotifications();
   }
  }
 }
 private boolean isDefault(){
  try{if(android.os.Build.VERSION.SDK_INT>=29){
   RoleManager rm=(RoleManager)getSystemService(ROLE_SERVICE);
   return rm!=null&&rm.isRoleHeld(RoleManager.ROLE_DIALER);
  }
   TelecomManager tm=(TelecomManager)getSystemService(TELECOM_SERVICE);
   return tm!=null&&getPackageName().equals(tm.getDefaultDialerPackage());
  }catch(Exception e){return false;}
 }
 private void askDefault(){
  try{
   if(android.os.Build.VERSION.SDK_INT>=29){
    RoleManager roles=(RoleManager)getSystemService(ROLE_SERVICE);
    if(roles!=null&&roles.isRoleAvailable(RoleManager.ROLE_DIALER)&&!roles.isRoleHeld(RoleManager.ROLE_DIALER)){
     startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_DIALER),100);
    }
   }else{
    Intent i=new Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER);
    i.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME,getPackageName());
    startActivityForResult(i,100);
   }
  }catch(Exception e){Toast.makeText(this,"פתח הגדרות > אפליקציות ברירת מחדל > טלפון",Toast.LENGTH_LONG).show();}
 }
 private void ensureCallLogPermission(){
  if(checkSelfPermission(Manifest.permission.READ_CALL_LOG)!=PackageManager.PERMISSION_GRANTED)
   requestPermissions(new String[]{Manifest.permission.READ_CALL_LOG},22);
 }
 private void ensureCallNotifications(){
  if(android.os.Build.VERSION.SDK_INT<33 ||
     checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED ||
     prefs.getBoolean("notifications_prompted",false)){
    ensureVoiceNetworkPermission();return;
  }
  prefs.edit().putBoolean("notifications_prompted",true).apply();
  requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},24);
 }
 private void ensureVoiceNetworkPermission(){
  if(checkSelfPermission(Manifest.permission.READ_PHONE_STATE)==PackageManager.PERMISSION_GRANTED ||
     prefs.getBoolean("voice_permission_prompted",false))return;
  prefs.edit().putBoolean("voice_permission_prompted",true).apply();
  requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE},25);
 }
 private void loadContacts(){loadContacts(false);}
 private void loadContacts(boolean force){
  if(checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED||loadingContacts)return;
  if(!force&&contactsLoaded)return;
  loadingContacts=true;
  CONTACTS_IO.execute(()->{
   List<Contacts.Person> result=Contacts.all(getApplicationContext());
   handler.post(()->{
    loadingContacts=false;
    if(isFinishing()||isDestroyed())return;
    contacts=result;contactsLoaded=true;
    contactNames.clear();
    for(Contacts.Person item:result){
     if(!item.phone.isEmpty())contactNames.put(Contacts.canonical(item.phone),item.name);
    }
    if(page.equals("contacts")||page.equals("favorites")||page.equals("recents"))render();
    FavoritesWidget.refresh(this);
   });
  });
 }
 private void refreshLog(){
  if(prefs==null)return;
  CallHistory.refresh(this,false,()->{
   if(!isFinishing()&&page.equals("recents"))render();
  });
 }
 private void render(){
  getWindow().setStatusBarColor(bg());getWindow().setNavigationBarColor(bg());
  getWindow().getDecorView().setSystemUiVisibility(dark()?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
  root=column();root.setBackgroundColor(bg());setContentView(root);
  LinearLayout top=row();pad(top,15,5);root.addView(top,lp(-1,52));
  ImageView logo=new ImageView(this);logo.setImageResource(R.mipmap.ic_launcher);
  logo.setScaleType(ImageView.ScaleType.FIT_CENTER);top.addView(logo,lp(38,38));
  String screen=page.equals("contacts")?"אנשי קשר":page.equals("favorites")?"מועדפים":page.equals("settings")?"הגדרות":"שיחות";
  TextView title=t(screen,19,true,text());title.setPadding(d(12),0,0,0);
  top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
  TextView settings=center(page.equals("settings")?"✓":"⚙",23,false,muted());
  top.addView(settings,lp(44,44));
  settings.setOnClickListener(v->{haptic(settings);page=page.equals("settings")?"recents":"settings";render();});
  View rule=new View(this);rule.setBackgroundColor(border());root.addView(rule,lp(-1,1));
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
  root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  body=column();pad(body,13,9);scroll.addView(body);
  if(page.equals("contacts"))contactsPage(false);
  else if(page.equals("favorites"))contactsPage(true);
  else if(page.equals("settings"))settingsPage();
  else recentsPage();
  LinearLayout footer=row();footer.setGravity(Gravity.CENTER_VERTICAL);pad(footer,15,8);
  footer.setBackground(stroked(panel(),17));root.addView(footer,lp(-1,65));
  nav=footer;
  if(page.equals("settings")){
   TextView back=center("←  חזרה לשיחות",15,true,tint());
   footer.addView(back,new LinearLayout.LayoutParams(0,d(53),1));
   back.setOnClickListener(v->{page="recents";render();});
   TextView keypad=center("▦",26,true,Color.BLACK);
   keypad.setBackground(rounded(GOLD,19));footer.addView(keypad,lp(65,53));
   keypad.setOnClickListener(v->{haptic(keypad);showKeypad(null);});
  }else{
   addNav("שיחות","◷","recents");
   addNav("אנשי קשר","☷","contacts");
   addNav("מועדפים","★","favorites");
   TextView dial=center("▦",27,true,Color.BLACK);
   dial.setBackground(rounded(GOLD,23));
   LinearLayout.LayoutParams fp=lp(60,55);fp.setMargins(d(5),0,d(1),0);
   footer.addView(dial,fp);dial.setElevation(d(5));
   dial.setOnClickListener(v->{haptic(dial);showKeypad(null);});
  }
  if(pendingNumber!=null){String n=pendingNumber;pendingNumber=null;handler.post(()->showKeypad(n));}
 }
 private void addNav(String label,String glyph,String dest){
  boolean chosen=page.equals(dest);
  LinearLayout tile=column();tile.setGravity(Gravity.CENTER);
  TextView i=center(glyph,23,chosen,tintOrMute(chosen));
  tile.addView(i,lp(-1,30));
  TextView name=center(label,10,chosen,tintOrMute(chosen));
  tile.addView(name,lp(-1,17));
  nav.addView(tile,new LinearLayout.LayoutParams(0,-1,1));
  tile.setOnClickListener(v->{haptic(tile);page=dest;render();});
 }
 private int tintOrMute(boolean chosen){return chosen?tint():muted();}
 private void roleBanner(){
  if(isDefault())return;
  LinearLayout banner=column();pad(banner,15,15);banner.setBackground(stroked(soft(),17));
  body.addView(banner,lp(-1,-2));
  banner.addView(t("הפעל כחייגן ברירת המחדל",15,true,text()));
  gap(banner,5);banner.addView(t("כדי לקבל שיחות ולנהל אותן מתוך DA CALLER",12,false,muted()));
  gap(banner,12);
  TextView go=center("הגדר כחייגן הראשי   ←",14,true,Color.BLACK);
  go.setBackground(rounded(GOLD,13));banner.addView(go,lp(-1,45));
  go.setOnClickListener(v->askDefault());gap(body,18);
 }
 private void recentsPage(){
  heading("שיחות אחרונות","יומן השיחות והפעילות שלך");
  if(checkSelfPermission(Manifest.permission.READ_CALL_LOG)!=PackageManager.PERMISSION_GRANTED){
   permissionPanel("יומן השיחות דורש הרשאה","אפשר גישה ליומן השיחות",()->ensureCallLogPermission());return;
  }
  if(prefs.getBoolean("show_stats",true)){summary();gap(body,8);}
  roleBanner();
  LinearLayout labelLine=row();body.addView(labelLine,lp(-1,35));
  labelLine.addView(t("אחרונות",15,true,text()),new LinearLayout.LayoutParams(0,-2,1));
  TextView reload=center("↻",23,true,tint());labelLine.addView(reload,lp(40,38));
  reload.setOnClickListener(v->CallHistory.refresh(this,true,()->render()));
  gap(body,5);
  List<CallHistory.Entry> entries=CallHistory.entries;
  if(entries.isEmpty()){empty("אין שיחות להצגה");return;}
  int limit=Math.min(entries.size(),recentDisplayCount);
  for(int i=0;i<limit;i++)addCall(entries.get(i));
  if(limit<entries.size()){
   TextView more=center("הצג עוד שיחות   ↓",14,true,tint());
   more.setBackground(stroked(panel(),14));
   LinearLayout.LayoutParams moreParams=lp(-1,53);moreParams.topMargin=d(9);body.addView(more,moreParams);
   more.setOnClickListener(v->{recentDisplayCount+=35;render();});
  }
 }
 private void summary(){
  final String[] ranges={"היום","השבוע","החודש","השנה"};
  CallHistory.Summary totals=CallHistory.summary;
  android.widget.HorizontalScrollView scroll=new android.widget.HorizontalScrollView(this);
  scroll.setHorizontalScrollBarEnabled(false);scroll.setFillViewport(true);
  scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
  LinearLayout bar=row();
  bar.setPadding(d(9),d(3),d(9),d(3));
  bar.setBackground(stroked(panel(),13));
  scroll.addView(bar,new android.widget.FrameLayout.LayoutParams(-2,d(42)));
  TextView picker=center(ranges[period]+"  ▾",11,true,tint());
  picker.setBackground(rounded(soft(),9));picker.setPadding(d(9),0,d(9),0);
  bar.addView(picker,lp(-2,31));
  picker.setOnClickListener(v->{haptic(picker);period=(period+1)%4;render();});
  addStat(bar,String.valueOf(Math.round(totals.seconds[period]/60.0))+" דק׳",true);
  addStat(bar,totals.calls[period]+" שיחות",false);
  addStat(bar,"↑ "+totals.outgoing[period],false);
  addStat(bar,"↓ "+totals.incoming[period],false);
  addStat(bar,"✕ "+totals.missed[period],false);
  body.addView(scroll,lp(-1,44));
 }
 private void addStat(LinearLayout bar,String value,boolean highlight){
  TextView label=center(value,11,highlight,highlight?tint():muted());
  label.setPadding(d(9),0,d(9),0);
  bar.addView(label,lp(-2,34));
 }
 private String contactName(String phone){
  if(phone==null)return "";
  String name=contactNames.get(Contacts.canonical(phone));
  return name!=null?name:phone;
 }
 private void addCall(CallHistory.Entry e){
  LinearLayout item=row();pad(item,9,5);
  LinearLayout.LayoutParams margin=lp(-1,62);margin.bottomMargin=d(3);
  body.addView(item,margin);
  int iconColor=e.type==CallLog.Calls.MISSED_TYPE?0xFFCA5656:tint();
  TextView icon=center(e.type==CallLog.Calls.OUTGOING_TYPE?"↗":e.type==CallLog.Calls.MISSED_TYPE?"↙":"↘",23,true,iconColor);
  icon.setBackground(rounded(panel(),15));item.addView(icon,lp(45,45));
  LinearLayout details=column();details.setPadding(d(11),0,d(4),0);
  item.addView(details,new LinearLayout.LayoutParams(0,-2,1));
  TextView who=t(contactName(e.number),15,true,text());who.setSingleLine(true);who.setEllipsize(android.text.TextUtils.TruncateAt.END);details.addView(who);
  String when=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT,new Locale("he","IL")).format(new Date(e.date));
  details.addView(t(when+"  ·  "+(e.duration>0?Math.round(e.duration/60.0)+" דק׳":e.type==CallLog.Calls.MISSED_TYPE?"לא נענתה":"ללא מענה"),11,false,muted()));
  TextView dial=center("☎",22,true,tint());item.addView(dial,lp(44,48));
  dial.setOnClickListener(v->makeCall(e.number));
  item.setOnClickListener(v->showKeypad(e.number));
  View divider=new View(this);divider.setBackgroundColor(border());body.addView(divider,lp(-1,1));
 }
 private void contactsPage(boolean onlyFav){
  heading(onlyFav?"מועדפים":"אנשי קשר",onlyFav?"האנשים שאתה מתקשר אליהם הכי הרבה":"חיפוש מהיר ונוח לפי שם או מספר");
  if(checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED){
   permissionPanel("אנשי הקשר נמצאים בטלפון שלך","אפשר גישה לאנשי קשר",()->requestPermissions(new String[]{Manifest.permission.READ_CONTACTS},21));return;
  }
  EditText search=new EditText(this);search.setSingleLine(true);
  search.setTextSize(scaled(15));search.setTextColor(text());search.setHintTextColor(muted());
  search.setHint("חיפוש שם או מספר");search.setBackground(stroked(panel(),15));pad(search,15,3);
  LinearLayout searchBar=row();searchBar.addView(search,new LinearLayout.LayoutParams(0,d(49),1));
  TextView reload=center("↻",22,true,tint());searchBar.addView(reload,lp(46,49));
  reload.setOnClickListener(v->{haptic(reload);contactsLoaded=false;loadContacts(true);});
  body.addView(searchBar,lp(-1,52));gap(body,7);
  if(contactsLoaded)body.addView(t(contacts.size()+" אנשי קשר במכשיר",11,false,muted()));
  gap(body,6);
  LinearLayout list=column();body.addView(list,lp(-1,-2));
  final int[] maxRows={55};
  Runnable filter=()->{
   list.removeAllViews();String query=search.getText().toString().toLowerCase(Locale.ROOT).trim();
   Set<String> starred=favorites();int shown=0;int totalMatches=0;
   for(Contacts.Person person:contacts){
    if(onlyFav&&!starred.contains(person.key))continue;
    if(!query.isEmpty()&&!person.name.toLowerCase(Locale.ROOT).contains(query)&&!person.phone.contains(query))continue;
    totalMatches++;
    if(shown<maxRows[0]){addContact(list,person,starred.contains(person.key));shown++;}
   }
   if(shown==0){
    if(!contactsLoaded||loadingContacts)emptyIn(list,"טוען אנשי קשר...");
    else if(contacts.isEmpty())emptyIn(list,"לא נמצאו אנשי קשר. בדוק חשבון והרשאות, ונסה לרענן.");
    else emptyIn(list,onlyFav?"עדיין אין אנשי קשר מועדפים":"אין תוצאות");
   }
   if(totalMatches>shown){
    TextView more=center("הצג עוד אנשי קשר ("+(totalMatches-shown)+")",13,true,tint());
    more.setBackground(stroked(panel(),14));list.addView(more,lp(-1,51));
    more.setOnClickListener(v->{maxRows[0]+=55;filterClick(list,search,onlyFav,maxRows[0]);});
   }
  };
  search.addTextChangedListener(new TextWatcher(){
   public void beforeTextChanged(CharSequence s,int st,int count,int after){}
   public void onTextChanged(CharSequence s,int st,int before,int count){maxRows[0]=55;handler.removeCallbacks(filter);handler.postDelayed(filter,55);}
   public void afterTextChanged(Editable e){}
  });
  filter.run();
 }
 private void filterClick(LinearLayout list,EditText search,boolean onlyFav,int max){
  list.removeAllViews();
  String q=search.getText().toString().toLowerCase(Locale.ROOT).trim();
  Set<String> starred=favorites();int shown=0;
  for(Contacts.Person person:contacts){
   if(onlyFav&&!starred.contains(person.key))continue;
   if(!q.isEmpty()&&!person.name.toLowerCase(Locale.ROOT).contains(q)&&!person.phone.contains(q))continue;
   addContact(list,person,starred.contains(person.key));shown++;if(shown>=max)break;
  }
  int matching=0;
  for(Contacts.Person item:contacts){
   if(onlyFav&&!starred.contains(item.key))continue;
   if(!q.isEmpty()&&!item.name.toLowerCase(Locale.ROOT).contains(q)&&!item.phone.contains(q))continue;
   matching++;
  }
  if(shown<matching){
   TextView more=center("הצג עוד ("+(matching-shown)+")",14,true,tint());more.setBackground(stroked(panel(),13));
   list.addView(more,lp(-1,49));
   more.setOnClickListener(v->filterClick(list,search,onlyFav,max+55));
  }
 }
 private Set<String> favorites(){return new HashSet<>(prefs.getStringSet("favorites",Collections.emptySet()));}
 private void toggleFavorite(Contacts.Person person){
  Set<String> set=favorites();if(!set.add(person.key))set.remove(person.key);
  prefs.edit().putStringSet("favorites",set).apply();FavoritesWidget.refresh(this);render();
 }
 private void addContact(LinearLayout list,Contacts.Person person,boolean starred){
  LinearLayout item=row();pad(item,5,5);list.addView(item,lp(-1,65));
  TextView avatar=center(person.name.substring(0,1).toUpperCase(Locale.ROOT),18,true,tint());
  avatar.setBackground(rounded(soft(),15));item.addView(avatar,lp(44,44));
  LinearLayout data=column();data.setPadding(d(12),0,0,0);item.addView(data,new LinearLayout.LayoutParams(0,-2,1));
  TextView name=t(person.name,15,true,text());name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
  data.addView(name);data.addView(t(person.phone.isEmpty()?"ללא מספר טלפון":person.phone,11,false,muted()));
  TextView star=center(starred?"★":"☆",24,false,starred?tint():muted());item.addView(star,lp(42,42));
  star.setOnClickListener(v->{haptic(star);toggleFavorite(person);});
  TextView call=center("☎",22,false,tint());item.addView(call,lp(40,42));
  call.setAlpha(person.phone.isEmpty()?0.28f:1f);
  call.setOnClickListener(v->{if(!person.phone.isEmpty()){haptic(call);makeCall(person.phone);}});
  data.setOnClickListener(v->{
   haptic(data);
   if(!Contacts.openById(this,person.id) && !person.phone.isEmpty())showKeypad(person.phone);
  });
  View line=new View(this);line.setBackgroundColor(border());list.addView(line,lp(-1,1));
 }
 private void empty(String message){emptyIn(body,message);}
 private void emptyIn(LinearLayout parent,String message){
  TextView label=center(message,13,false,muted());parent.addView(label,lp(-1,80));
 }
 private void permissionPanel(String message,String button,Runnable click){
  LinearLayout card=column();pad(card,15,18);card.setBackground(stroked(panel(),15));body.addView(card,lp(-1,-2));
  card.addView(t(message,14,false,muted()));gap(card,17);
  TextView grant=center(button,15,true,Color.BLACK);grant.setBackground(rounded(GOLD,14));card.addView(grant,lp(-1,48));grant.setOnClickListener(v->click.run());
 }
 private void makeCall(String phone){
  if(phone==null||phone.trim().isEmpty()){Toast.makeText(this,"הזן מספר",Toast.LENGTH_SHORT).show();return;}
  if(checkSelfPermission(Manifest.permission.CALL_PHONE)!=PackageManager.PERMISSION_GRANTED){
   pendingNumber=phone;requestPermissions(new String[]{Manifest.permission.CALL_PHONE},23);return;
  }
  Uri tel=Uri.fromParts("tel",phone,null);
  try{
   if(isDefault()){
    TelecomManager telecom=(TelecomManager)getSystemService(TELECOM_SERVICE);
    if(telecom!=null){telecom.placeCall(tel,new Bundle());return;}
   }
   startActivity(new Intent(Intent.ACTION_CALL,tel));
  }catch(Exception e){
   try{startActivity(new Intent(Intent.ACTION_DIAL,tel));}
   catch(Exception other){Toast.makeText(this,"לא ניתן לבצע שיחה",Toast.LENGTH_SHORT).show();}
  }
 }
 private void showKeypad(String preset){
  Dialog dialog=new Dialog(this);
  LinearLayout sheet=column();pad(sheet,16,13);sheet.setBackground(rounded(panel(),25));
  TextView grip=center("━━━━",18,false,muted());sheet.addView(grip,lp(-1,21));
  TextView header=center("חיוג מהיר",17,true,text());sheet.addView(header,lp(-1,31));
  final EditText value=new EditText(this);value.setSingleLine(true);
  value.setInputType(android.text.InputType.TYPE_CLASS_PHONE);value.setShowSoftInputOnFocus(false);
  value.setTextColor(text());value.setTextSize(25);
  value.setGravity(Gravity.CENTER);value.setTextDirection(View.TEXT_DIRECTION_LTR);
  value.setBackground(stroked(bg(),12));value.setHint("הזן מספר");value.setHintTextColor(muted());
  if(preset!=null)value.setText(preset);sheet.addView(value,lp(-1,49));
  gap(sheet,8);
  String[] digits={"1","2","3","4","5","6","7","8","9","*","0","#"};
  int keyHeight=screenHeight()<680?43:51;
  for(int i=0;i<4;i++){
   LinearLayout line=row();line.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
   sheet.addView(line,lp(-1,keyHeight+5));
   for(int j=0;j<3;j++){
    String key=digits[i*3+j];
    TextView btn=center(key,24,true,text());btn.setBackground(stroked(bg(),14));
    LinearLayout.LayoutParams b=new LinearLayout.LayoutParams(0,d(keyHeight),1);b.setMargins(d(3),d(2),d(3),d(2));
    line.addView(btn,b);btn.setOnClickListener(v->{haptic(btn);value.append(key);});
    if(key.equals("0"))btn.setOnLongClickListener(v->{value.append("+");return true;});
   }
  }
  gap(sheet,9);
  LinearLayout actions=row();sheet.addView(actions,lp(-1,52));
  TextView erase=center("⌫",24,true,text());actions.addView(erase,new LinearLayout.LayoutParams(0,-1,1));
  erase.setOnClickListener(v->{haptic(erase);String s=value.getText().toString();if(!s.isEmpty())value.setText(s.substring(0,s.length()-1));});
  erase.setOnLongClickListener(v->{value.setText("");return true;});
  TextView call=center("☎  התקשר",16,true,Color.BLACK);call.setBackground(rounded(GOLD,15));
  LinearLayout.LayoutParams callp=new LinearLayout.LayoutParams(0,d(51),3);callp.setMargins(d(5),0,d(5),0);actions.addView(call,callp);
  call.setOnClickListener(v->{haptic(call);String n=value.getText().toString();dialog.dismiss();makeCall(n);});
  TextView close=center("✕",20,true,muted());actions.addView(close,new LinearLayout.LayoutParams(0,-1,1));
  close.setOnClickListener(v->dialog.dismiss());
  dialog.setContentView(sheet);
  Window w=dialog.getWindow();
  if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
   w.setDimAmount(0.5f);w.setGravity(Gravity.BOTTOM);w.setLayout(-1,-2);}
  dialog.show();
  if(dialog.getWindow()!=null)dialog.getWindow().setLayout(-1,-2);
 }
 private void section(String title){gap(body,18);body.addView(t(title,16,true,text()));gap(body,10);}
 private void toggleSetting(String label,String key,boolean value){
  LinearLayout item=row();pad(item,14,7);item.setBackground(stroked(panel(),15));
  LinearLayout.LayoutParams p=lp(-1,58);p.bottomMargin=d(7);body.addView(item,p);
  item.addView(t(label,14,true,text()),new LinearLayout.LayoutParams(0,-2,1));
  Switch sw=new Switch(this);sw.setChecked(value);item.addView(sw);
  sw.setOnCheckedChangeListener((v,on)->{
   prefs.edit().putBoolean(key,on).apply();
   if(key.equals("amoled")&&on)prefs.edit().putBoolean("dark",true).apply();
   render();
  });
 }
 private void inCallControlsSettings(){
  section("כפתורים בזמן שיחה");
  TextView intro=t("בחר אילו פעולות יוצגו בשיחה. מענה, דחייה וניתוק נשארים תמיד זמינים.",12,false,muted());
  body.addView(intro);
  gap(body,10);
  final TextView count=t("",12,true,tint());
  for(int i=0;i<InCallControls.IDS.length;i++){
   final String id=InCallControls.IDS[i];
   LinearLayout item=row();pad(item,12,8);
   item.setBackground(stroked(panel(),14));
   LinearLayout.LayoutParams params=lp(-1,63);params.bottomMargin=d(6);
   body.addView(item,params);
   LinearLayout description=column();
   item.addView(description,new LinearLayout.LayoutParams(0,-2,1));
   description.addView(t(InCallControls.NAMES[i],14,true,text()));
   description.addView(t(InCallControls.EXPLAIN[i],11,false,muted()));
   Switch selected=new Switch(this);
   selected.setChecked(InCallControls.enabled(prefs,id));
   item.addView(selected,lp(47,50));
   item.setOnClickListener(v->{haptic(item);selected.setChecked(!selected.isChecked());});
   selected.setOnCheckedChangeListener((button,on)->{
    prefs.edit().putBoolean("call_btn_"+id,on).apply();
    count.setText(InCallControls.count(prefs)+" פעולות נבחרו");
   });
  }
  count.setText(InCallControls.count(prefs)+" פעולות נבחרו");
  body.addView(count);
  gap(body,8);
  TextView density=t("גודל לחצני השיחה",14,true,text());
  body.addView(density);
  gap(body,7);
  LinearLayout sizeBox=column();pad(sizeBox,12,8);
  sizeBox.setBackground(stroked(panel(),14));
  body.addView(sizeBox,lp(-1,-2));
  int initial=prefs.getInt("call_button_height",68);
  TextView value=t(initial+"dp",13,true,tint());
  sizeBox.addView(value);
  SeekBar bar=new SeekBar(this);
  bar.setMax(30);bar.setProgress(Math.max(0,Math.min(30,initial-54)));
  sizeBox.addView(bar,lp(-1,42));
  bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
   public void onStartTrackingTouch(SeekBar seek){}
   public void onStopTrackingTouch(SeekBar seek){}
   public void onProgressChanged(SeekBar seek,int progress,boolean user){
    if(!user)return;
    int height=54+progress;
    prefs.edit().putInt("call_button_height",height).apply();
    value.setText(height+"dp");
   }
  });
 }
 private void settingsPage(){
  heading("הגדרות","התאמה אישית מדויקת ואלגנטית");
  section("ניהול חייגן");
  TextView role=center(isDefault()?"✓  DA CALLER מוגדר כחייגן ראשי":"הגדר כחייגן ברירת המחדל",14,true,Color.BLACK);
  role.setBackground(rounded(GOLD,14));body.addView(role,lp(-1,51));role.setOnClickListener(v->{haptic(role);askDefault();});
  section("תצוגה");
  toggleSetting("מצב כהה · שחור גרפיט","dark",prefs.getBoolean("dark",false));
  toggleSetting("מצב AMOLED · שחור מוחלט","amoled",amoled());
  toggleSetting("רטט בעת לחיצה על מקשים ופעולות","haptics",prefs.getBoolean("haptics",true));
  toggleSetting("הצג סיכום שיחות בראש היומן","show_stats",prefs.getBoolean("show_stats",true));
  inCallControlsSettings();
  section("צבע הדגשה");
  LinearLayout themes=row();body.addView(themes,lp(-1,46));
  for(String option:new String[]{"gold","silver"}){
   boolean active=option.equals(prefs.getString("accent","gold"));
   TextView choice=center(option.equals("gold")?"זהב שמפניה":"כסף אלגנטי",13,active,active?tint():muted());
   choice.setBackground(stroked(panel(),12));
   LinearLayout.LayoutParams weights=new LinearLayout.LayoutParams(0,d(44),1);weights.setMargins(d(3),0,d(3),0);
   themes.addView(choice,weights);
   choice.setOnClickListener(v->{prefs.edit().putString("accent",option).apply();render();});
  }
  section("צפיפות ומראה");
  String current=prefs.getString("edit_page","recents");
  LinearLayout chooser=row();body.addView(chooser,lp(-1,46));
  String[] pages={"recents","contacts","favorites","settings"};
  String[] labels={"שיחות","אנשי קשר","מועדפים","הגדרות"};
  for(int i=0;i<pages.length;i++){
   String value=pages[i];
   TextView option=center(labels[i],10,current.equals(value),current.equals(value)?tint():muted());
   option.setBackground(stroked(panel(),10));
   LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(0,d(42),1);op.setMargins(d(2),0,d(2),0);chooser.addView(option,op);
   option.setOnClickListener(v->{prefs.edit().putString("edit_page",value).apply();render();});
  }
  slider("גודל טקסט במסך שנבחר","font_"+current,85,130,100,"%");
  section("מסך פתיחה");
  LinearLayout home=row();body.addView(home,lp(-1,45));
  for(int i=0;i<3;i++){
   String pg=pages[i];String name=labels[i];boolean selected=prefs.getString("start_page","recents").equals(pg);
   TextView choose=center(name,12,selected,selected?tint():muted());choose.setBackground(stroked(panel(),10));
   LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,d(42),1);params.setMargins(d(2),0,d(2),0);home.addView(choose,params);
   choose.setOnClickListener(v->{prefs.edit().putString("start_page",pg).apply();render();});
  }
  TextView note=t("התאמה למסכים קטנים (כולל מסכי 4 אינץ׳): כפתורים קומפקטיים, גלילה חלקה וחייגן צף בכל מסך.",12,false,muted());body.addView(note);
  section("הרשאות");
  TextView access=center("בדיקת הרשאות ליומן שיחות",14,true,tint());
  access.setBackground(stroked(panel(),14));body.addView(access,lp(-1,50));
  access.setOnClickListener(v->ensureCallLogPermission());
  gap(body,8);
  TextView notifications=center("הגדרות התראות שיחה",14,true,tint());
  notifications.setBackground(stroked(panel(),14));body.addView(notifications,lp(-1,50));
  notifications.setOnClickListener(v->{
   try{
    Intent settings=new Intent("android.settings.APP_NOTIFICATION_SETTINGS");
    settings.putExtra("android.provider.extra.APP_PACKAGE",getPackageName());
    startActivity(settings);
   }catch(Exception ignored){ensureCallNotifications();}
  });
  TextView voltePermission=center("גישה למצב הרשת עבור חיווי VoLTE",13,true,tint());
  voltePermission.setBackground(stroked(panel(),14));body.addView(voltePermission,lp(-1,48));
  voltePermission.setOnClickListener(v->{
   haptic(voltePermission);
   if(checkSelfPermission(Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED)
    requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE},25);
   else android.widget.Toast.makeText(this,"הרשאת רשת השיחה פעילה",android.widget.Toast.LENGTH_SHORT).show();
  });
  section("הקלטת שיחות");
  TextView recording=t("הקלטת שני הצדדים אינה זמינה לאפליקציות רגילות באנדרואיד 13 ללא הרשאות מערכת מיוחדות. לא הוספנו כפתור הקלטה שאינו עובד.",13,false,muted());body.addView(recording);
  section("אודות");
  body.addView(t("DA CALLER 0.5.0 · PREMIUM BETA",12,false,muted()));
 }
 private void slider(String title,String key,int min,int max,int initial,String unit){
  LinearLayout box=column();pad(box,12,9);box.setBackground(stroked(panel(),15));
  body.addView(box,lp(-1,-2));
  TextView value=t(title+": "+prefs.getInt(key,initial)+unit,13,false,text());box.addView(value);
  SeekBar bar=new SeekBar(this);bar.setMax(max-min);bar.setProgress(prefs.getInt(key,initial)-min);
  box.addView(bar,lp(-1,45));
  bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
   public void onStartTrackingTouch(SeekBar b){}
   public void onStopTrackingTouch(SeekBar b){render();}
   public void onProgressChanged(SeekBar b,int n,boolean user){
    if(user){int v=min+n;prefs.edit().putInt(key,v).apply();value.setText(title+": "+v+unit);}
   }
  });
 }
}
