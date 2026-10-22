package com.dacaller.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
 private static final String[] TABS={"dial","contacts","favorites","settings"};
 private static final String[] NAMES={"חייגן","אנשי קשר","מועדפים","הגדרות"};
 private static final String[] ICONS={"☎","☷","★","⚙"};
 private static final int[] COLORS={0xFF2865E8,0xFF159B8B,0xFF7A4DE2,0xFF208457,0xFFE47D25,0xFFD94674};
 private SharedPreferences p;
 private LinearLayout root,content;
 private EditText number;
 private String tab="dial",pendingCall=null;
 private int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
 private boolean dark(){return p.getBoolean("dark",false);}
 private int pageBg(){return dark()?0xFF0D1626:0xFFF7F9FE;}
 private int surface(){return dark()?0xFF1B2940:Color.WHITE;}
 private int textColor(){return dark()?Color.WHITE:0xFF17243B;}
 private int muted(){return dark()?0xFFA8B7CE:0xFF66778F;}
 private int pale(){return dark()?0xFF263A57:0xFFEAF1FF;}
 private int accent(){return COLORS[Math.max(0,Math.min(COLORS.length-1,p.getInt("color_"+tab,0)))];}
 private float scaling(){return p.getInt("font_"+tab,100)/100f;}
 private GradientDrawable shape(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
 private GradientDrawable border(int color,int radius){GradientDrawable g=shape(color,radius);g.setStroke(dp(1),dark()?0xFF334662:0xFFE1E9F3);return g;}
 private LinearLayout vertical(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);v.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);return v;}
 private LinearLayout horizontal(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);v.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);return v;}
 private LinearLayout.LayoutParams size(int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
 private void padding(View v,int x,int y){v.setPadding(dp(x),dp(y),dp(x),dp(y));}
 private void spacer(LinearLayout parent,int height){parent.addView(new View(this),size(1,height));}
 private TextView label(String text,int sp,boolean bold,int color){
  TextView v=new TextView(this);v.setText(text);v.setTextColor(color);
  v.setTextSize(TypedValue.COMPLEX_UNIT_SP,sp*scaling());v.setGravity(Gravity.CENTER_VERTICAL);
  if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
  return v;
 }
 private TextView action(String t,boolean primary,int sp){
  TextView v=label(t,sp,true,primary?Color.WHITE:textColor());
  v.setGravity(Gravity.CENTER);v.setBackground(shape(primary?accent():surface(),20));return v;
 }
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);p=getSharedPreferences("da_caller",MODE_PRIVATE);
  getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
  if(getIntent().getBooleanExtra("favorites",false))tab="favorites";
  render();
 }
 @Override protected void onResume(){super.onResume();if(p!=null)FavoritesWidget.refresh(this);}
 @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){
  super.onRequestPermissionsResult(request,permissions,grants);
  if(request==10){render();return;}
  if(request==11 && pendingCall!=null){
   String value=pendingCall;pendingCall=null;
   startCall(value,grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED);
  }
 }
 private void render(){
  getWindow().setStatusBarColor(pageBg());getWindow().setNavigationBarColor(pageBg());
  getWindow().getDecorView().setSystemUiVisibility(dark()?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
  root=vertical();root.setBackgroundColor(pageBg());setContentView(root);
  LinearLayout header=horizontal();padding(header,20,6);root.addView(header,size(-1,66));
  TextView appLogo=action("☎",true,27);header.addView(appLogo,size(48,48));
  LinearLayout title=vertical();title.setPadding(dp(12),0,0,0);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
  title.addView(label("DA CALLER",21,true,textColor()));
  title.addView(label("חייגן ואנשי קשר • גרסת ניסיון",11,false,muted()));
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
  root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  content=vertical();padding(content,19,10);scroll.addView(content);
  if(tab.equals("contacts"))renderContacts(false);
  else if(tab.equals("favorites"))renderContacts(true);
  else if(tab.equals("settings"))renderSettings();
  else renderDialer();
  LinearLayout nav=horizontal();padding(nav,5,5);nav.setBackground(border(surface(),25));
  LinearLayout.LayoutParams navSize=size(-1,75);navSize.setMargins(dp(12),0,dp(12),dp(9));root.addView(nav,navSize);
  for(int i=0;i<TABS.length;i++){
   final String next=TABS[i];boolean selected=tab.equals(next);
   LinearLayout tile=vertical();tile.setGravity(Gravity.CENTER);
   if(selected)tile.setBackground(shape(pale(),18));
   TextView icon=label(ICONS[i],25,true,selected?accent():muted());icon.setGravity(Gravity.CENTER);tile.addView(icon);
   TextView name=label(NAMES[i],11,selected,selected?accent():muted());name.setGravity(Gravity.CENTER);tile.addView(name);
   nav.addView(tile,new LinearLayout.LayoutParams(0,-1,1));
   tile.setOnClickListener(v->{tab=next;render();});
  }
 }
 private void heading(String title,String subtitle){
  content.addView(label(title,25,true,textColor()));spacer(content,4);
  content.addView(label(subtitle,13,false,muted()));spacer(content,21);
 }
 private void renderDialer(){
  heading("חייגן","גדול, נוח ופשוט לחיוג");
  LinearLayout display=vertical();display.setGravity(Gravity.CENTER);
  display.setBackground(border(surface(),25));padding(display,15,13);content.addView(display,size(-1,92));
  number=new EditText(this);number.setSingleLine(true);number.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
  number.setShowSoftInputOnFocus(false);number.setTextDirection(View.TEXT_DIRECTION_LTR);
  number.setTextSize(TypedValue.COMPLEX_UNIT_SP,30*scaling());
  number.setTextColor(textColor());number.setHintTextColor(muted());number.setHint("הקלד מספר");
  number.setGravity(Gravity.CENTER);number.setBackgroundColor(Color.TRANSPARENT);display.addView(number,size(-1,60));
  String preload=getIntent().getStringExtra("phone");
  if(preload!=null){number.setText(preload);getIntent().removeExtra("phone");}
  spacer(content,19);
  String[] keys={"1","2","3","4","5","6","7","8","9","*","0","#"};
  String[] small={"","ABC","DEF","GHI","JKL","MNO","PQRS","TUV","WXYZ","","+",""};
  int h=p.getInt("keypad",74);
  float screenH=getResources().getDisplayMetrics().heightPixels/getResources().getDisplayMetrics().density;
  if(screenH<710)h=Math.min(h,62);
  for(int row=0;row<4;row++){
   LinearLayout line=horizontal();line.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
   content.addView(line,size(-1,h+10));
   for(int col=0;col<3;col++){
    int ix=row*3+col;String key=keys[ix];
    LinearLayout tile=vertical();tile.setGravity(Gravity.CENTER);
    tile.setBackground(border(surface(),p.getBoolean("square",false)?17:40));
    LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(h),1);
    params.setMargins(dp(4),dp(2),dp(4),dp(2));line.addView(tile,params);
    TextView main=label(key,30,true,textColor());main.setGravity(Gravity.CENTER);tile.addView(main);
    if(!small[ix].isEmpty()){
     TextView hint=label(small[ix],10,true,muted());hint.setGravity(Gravity.CENTER);tile.addView(hint);
    }
    tile.setOnClickListener(v->number.append(key));
    if(key.equals("0"))tile.setOnLongClickListener(v->{number.append("+");return true;});
   }
  }
  spacer(content,17);
  LinearLayout buttons=horizontal();buttons.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
  content.addView(buttons,size(-1,63));
  TextView erase=action("⌫",false,25);buttons.addView(erase,new LinearLayout.LayoutParams(0,dp(61),1));
  erase.setOnClickListener(v->{
   String s=number.getText().toString();
   if(!s.isEmpty()){number.setText(s.substring(0,s.length()-1));number.setSelection(number.length());}
  });
  erase.setOnLongClickListener(v->{number.setText("");return true;});
  TextView go=action("☎  התקשר",true,18);
  LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(0,dp(61),2);gp.setMargins(dp(10),0,dp(10),0);
  buttons.addView(go,gp);go.setOnClickListener(v->dial(number.getText().toString()));
  TextView add=action("＋",false,25);buttons.addView(add,new LinearLayout.LayoutParams(0,dp(61),1));
  add.setOnClickListener(v->{
   String n=number.getText().toString();if(n.trim().isEmpty())return;
   Intent intent=new Intent(Intent.ACTION_INSERT,ContactsContract.Contacts.CONTENT_URI);
   intent.putExtra(ContactsContract.Intents.Insert.PHONE,n);startActivity(intent);
  });
  spacer(content,15);
  TextView info=label("לחיצה ממושכת על 0 מוסיפה + • על מחיקה מנקה מספר",11,false,muted());
  info.setGravity(Gravity.CENTER);content.addView(info);
 }
 private void dial(String value){
  if(value==null||value.trim().isEmpty()){
   Toast.makeText(this,"נא להזין מספר",Toast.LENGTH_SHORT).show();return;
  }
  if(checkSelfPermission(Manifest.permission.CALL_PHONE)!=PackageManager.PERMISSION_GRANTED){
   pendingCall=value;requestPermissions(new String[]{Manifest.permission.CALL_PHONE},11);return;
  }
  startCall(value,true);
 }
 private void startCall(String n,boolean direct){
  try{startActivity(new Intent(direct?Intent.ACTION_CALL:Intent.ACTION_DIAL,Uri.fromParts("tel",n,null)));}
  catch(Exception e){
   if(direct)startCall(n,false);
   else Toast.makeText(this,"לא נמצאה אפליקציית טלפון",Toast.LENGTH_SHORT).show();
  }
 }
 private Set<String> favorites(){return new HashSet<>(p.getStringSet("favorites",Collections.emptySet()));}
 private void updateFavorite(Contacts.Person who){
  Set<String> fav=favorites();
  if(!fav.add(who.key))fav.remove(who.key);
  p.edit().putStringSet("favorites",fav).apply();
  FavoritesWidget.refresh(this);render();
 }
 private void renderContacts(boolean onlyFav){
  heading(onlyFav?"אנשי קשר מועדפים":"אנשי קשר",onlyFav?"חיוג מהיר לאנשים שחשובים לך":"כל אנשי הקשר מהמכשיר במקום אחד");
  if(checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED){
   LinearLayout intro=vertical();padding(intro,18,20);intro.setBackground(border(surface(),20));
   content.addView(intro,size(-1,-2));
   intro.addView(label("יש לאפשר גישה לאנשי קשר כדי להציג אותם",17,true,textColor()));
   spacer(intro,14);
   TextView permission=action("אפשר גישה לאנשי קשר",true,16);intro.addView(permission,size(-1,60));
   permission.setOnClickListener(v->requestPermissions(new String[]{Manifest.permission.READ_CONTACTS},10));return;
  }
  EditText search=new EditText(this);search.setSingleLine(true);
  search.setHint("חיפוש שם או מספר...");search.setHintTextColor(muted());search.setTextColor(textColor());
  search.setTextSize(TypedValue.COMPLEX_UNIT_SP,16*scaling());
  padding(search,16,3);search.setBackground(border(surface(),20));content.addView(search,size(-1,55));
  spacer(content,14);LinearLayout rows=vertical();content.addView(rows,size(-1,-2));
  List<Contacts.Person> all=Contacts.all(this);
  Runnable fill=()->{
   rows.removeAllViews();String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);
   Set<String> starred=favorites();int count=0;
   for(Contacts.Person person:all){
    if(onlyFav&&!starred.contains(person.key))continue;
    if(!q.isEmpty()&&!person.name.toLowerCase(Locale.ROOT).contains(q)&&!person.phone.contains(q))continue;
    LinearLayout item=horizontal();padding(item,10,8);item.setBackground(border(surface(),19));
    LinearLayout.LayoutParams ip=size(-1,75);ip.bottomMargin=dp(9);rows.addView(item,ip);
    TextView avatar=label(person.name.substring(0,1),22,true,accent());
    avatar.setGravity(Gravity.CENTER);avatar.setBackground(shape(pale(),17));item.addView(avatar,size(48,48));
    LinearLayout words=vertical();words.setPadding(dp(10),0,dp(8),0);
    item.addView(words,new LinearLayout.LayoutParams(0,-2,1));
    words.addView(label(person.name,16,true,textColor()));
    TextView phoneNumber=label(person.phone,13,false,muted());
    phoneNumber.setTextDirection(View.TEXT_DIRECTION_LTR);words.addView(phoneNumber);
    TextView star=label(starred.contains(person.key)?"★":"☆",27,false,starred.contains(person.key)?accent():muted());
    star.setGravity(Gravity.CENTER);item.addView(star,size(39,52));
    star.setOnClickListener(v->updateFavorite(person));
    TextView call=label("☎",25,true,accent());call.setGravity(Gravity.CENTER);item.addView(call,size(42,52));
    call.setOnClickListener(v->dial(person.phone));
    words.setOnClickListener(v->new AlertDialog.Builder(this)
      .setTitle(person.name).setMessage(person.phone)
      .setPositiveButton("התקשר",(dialog,which)->dial(person.phone))
      .setNegativeButton("סגור",null).show());
    if(++count>=250)break;
   }
   if(count==0){
    TextView message=label(onlyFav?"עדיין לא נבחרו מועדפים. לחץ ★ ליד איש קשר.":"לא נמצאו אנשי קשר מתאימים",15,false,muted());
    padding(message,12,25);rows.addView(message);
   }
   if(count==250)rows.addView(label("מוצגים 250 אנשי קשר. חיפוש לפי שם או מספר מצמצם את הרשימה.",12,false,muted()));
  };
  search.addTextChangedListener(new TextWatcher(){
   public void beforeTextChanged(CharSequence s,int a,int b,int c){}
   public void onTextChanged(CharSequence s,int start,int before,int count){fill.run();}
   public void afterTextChanged(Editable e){}
  });
  fill.run();
 }
 private void section(String t){spacer(content,18);content.addView(label(t,17,true,textColor()));spacer(content,12);}
 private void toggle(String t,String key){
  LinearLayout line=horizontal();padding(line,15,12);line.setBackground(border(surface(),18));
  LinearLayout.LayoutParams lineP=size(-1,62);lineP.bottomMargin=dp(10);content.addView(line,lineP);
  line.addView(label(t,15,true,textColor()),new LinearLayout.LayoutParams(0,-2,1));
  Switch sw=new Switch(this);sw.setChecked(p.getBoolean(key,false));line.addView(sw);
  sw.setOnCheckedChangeListener((v,on)->{p.edit().putBoolean(key,on).apply();render();});
 }
 private void slider(String title,String key,int min,int max,int defaultValue,String unit){
  LinearLayout container=vertical();padding(container,13,11);container.setBackground(border(surface(),18));
  LinearLayout.LayoutParams params=size(-1,-2);params.bottomMargin=dp(10);content.addView(container,params);
  TextView v=label(title+": "+p.getInt(key,defaultValue)+unit,13,true,textColor());container.addView(v);
  SeekBar bar=new SeekBar(this);bar.setMax(max-min);bar.setProgress(p.getInt(key,defaultValue)-min);
  container.addView(bar,size(-1,45));
  bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
   public void onStartTrackingTouch(SeekBar b){}
   public void onStopTrackingTouch(SeekBar b){render();}
   public void onProgressChanged(SeekBar b,int progress,boolean user){
    if(user){int val=min+progress;p.edit().putInt(key,val).apply();v.setText(title+": "+val+unit);}
   }
  });
 }
 private void renderSettings(){
  heading("התאמה אישית","התאמה לפי כל מסך • צבעים, טקסט ולחצנים");
  section("עיצוב כללי");
  toggle("מצב כהה","dark");
  toggle("מקשים מלבניים מעוגלים","square");
  slider("גודל מקשי החייגן","keypad",60,88,74,"dp");
  section("בחירת מסך לעיצוב");
  String target=p.getString("edit_tab","dial");
  LinearLayout picker=horizontal();content.addView(picker,size(-1,48));
  for(int i=0;i<TABS.length;i++){
   String value=TABS[i];TextView chip=action(NAMES[i],target.equals(value),12);
   LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(45),1);
   cp.setMargins(dp(2),0,dp(2),0);picker.addView(chip,cp);
   chip.setOnClickListener(v->{p.edit().putString("edit_tab",value).apply();render();});
  }
  section("צבע מוביל");
  LinearLayout palette=horizontal();content.addView(palette,size(-1,47));
  for(int i=0;i<COLORS.length;i++){
   int index=i;TextView swatch=label(p.getInt("color_"+target,0)==i?"✓":"",19,true,Color.WHITE);
   swatch.setGravity(Gravity.CENTER);swatch.setBackground(shape(COLORS[i],40));
   LinearLayout.LayoutParams s=new LinearLayout.LayoutParams(0,dp(43),1);
   s.setMargins(dp(3),0,dp(3),0);palette.addView(swatch,s);
   swatch.setOnClickListener(v->{p.edit().putInt("color_"+target,index).apply();render();});
  }
  section("טקסט");
  slider("גודל טקסט במסך "+NAMES[Arrays.asList(TABS).indexOf(target)],"font_"+target,85,135,100,"%");
  section("ווידג׳טים למסך הבית");
  content.addView(label("לחיצה ארוכה על מסך הבית ← ווידג׳טים ← DA CALLER. קיימים קיצור לחיוג, קיצור לאיש קשר ומועדפים.",14,false,muted()));
  section("פרטיות וגרסת ניסיון");
  content.addView(label("אנשי קשר ומועדפים נשמרים במכשיר בלבד, ללא הרשאת אינטרנט. הגרסה אינה מחליפה עדיין את ממשק השיחה הנכנסת או את יומן השיחות.",12,false,muted()));
 }
}
