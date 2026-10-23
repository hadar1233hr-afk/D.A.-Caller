package com.dacaller.app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.CallLog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Fast, virtualized page contents. Adapters reuse rows: no "show more" cap.
 * Separate navigation shell remains persistent in MainActivity.
 */
final class FastPages {
 private final MainActivity host;
 private final SharedPreferences prefs;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final DateFormat format=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT,new Locale("he","IL"));
 private final int gold=0xFFD2AE69;
 private int period=0;
 FastPages(MainActivity activity){
  host=activity;
  prefs=activity.getSharedPreferences("da_caller",Context.MODE_PRIVATE);
 }
 private int dp(float n){return (int)(host.getResources().getDisplayMetrics().density*n+0.5f);}
 private boolean dark(){return prefs.getBoolean("dark",false)||prefs.getBoolean("amoled",false);}
 private int background(){return prefs.getBoolean("amoled",false)?Color.BLACK:dark()?0xFF131313:0xFFF5F5F4;}
 private int panel(){return prefs.getBoolean("amoled",false)?0xFF090909:dark()?0xFF1D1D1D:0xFFFFFFFF;}
 private int ink(){return dark()?0xFFF6F6F6:0xFF161616;}
 private int subtle(){return dark()?0xFF9A9A9A:0xFF696969;}
 private int tint(){return "silver".equals(prefs.getString("accent","gold"))?(dark()?0xFFE4E4E4:0xFF616161):(dark()?0xFFE1C58D:0xFF947030);}
 private int soft(){return dark()?0xFF28231B:0xFFF1EBDD;}
 private GradientDrawable bg(int color,int radius){
  GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;
 }
 private GradientDrawable edge(int color,int radius){
  GradientDrawable g=bg(color,radius);g.setStroke(dp(1),dark()?0xFF383838:0xFFE9E7E2);return g;
 }
 private TextView text(String value,int sp,int color,boolean bold){
  TextView t=new TextView(host);
  t.setText(value);t.setTextColor(color);t.setTextSize(sp);
  t.setIncludeFontPadding(false);t.setGravity(Gravity.CENTER_VERTICAL);
  t.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));
  return t;
 }
 private ImageView icon(int res,int color){
  ImageView v=new ImageView(host);v.setImageResource(res);
  v.setImageTintList(ColorStateList.valueOf(color));
  v.setPadding(dp(9),dp(9),dp(9),dp(9));v.setScaleType(ImageView.ScaleType.FIT_CENTER);
  return v;
 }
 private LinearLayout vertical(){
  LinearLayout v=new LinearLayout(host);v.setOrientation(1);v.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);return v;
 }
 private LinearLayout horizontal(){
  LinearLayout v=new LinearLayout(host);v.setOrientation(0);v.setGravity(Gravity.CENTER_VERTICAL);
  v.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);return v;
 }
 private LinearLayout.LayoutParams lp(int w,int h){
  return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));
 }
 private void pulse(View v){TouchFeedback.pulse(host,prefs,v);}
 private void touch(View v){TouchFeedback.bind(v,prefs);}
 private void swipe(View view,String phone){
  final float[] down=new float[2];
  view.setOnTouchListener((v,event)->{
   switch(event.getActionMasked()){
    case MotionEvent.ACTION_DOWN:
     down[0]=event.getX();down[1]=event.getY();return false;
    case MotionEvent.ACTION_UP:
     float dx=event.getX()-down[0],dy=event.getY()-down[1];
     if(Math.abs(dx)>dp(72)&&Math.abs(dx)>Math.abs(dy)*1.45f){
      String direction=prefs.getString("swipe_direction","right");
      if(!"off".equals(direction)){
       boolean physicalRight=dx>0;
       if(("right".equals(direction)&&physicalRight)||("left".equals(direction)&&!physicalRight)){
        pulse(view);host.dialFromList(phone);return true;
       }
      }
     }
     break;
   }
   return false;
  });
 }
 View recents(){
  LinearLayout page=vertical();
  page.setBackgroundColor(background());
  page.setPadding(dp(12),dp(6),dp(12),0);
  if(host.checkSelfPermission(Manifest.permission.READ_CALL_LOG)!=PackageManager.PERMISSION_GRANTED){
   return permission("יומן השיחות דורש הרשאה","אפשר גישה לשיחות",()->host.requestLog());
  }
  if(prefs.getBoolean("show_stats",true)){
   android.widget.HorizontalScrollView stats=new android.widget.HorizontalScrollView(host);
   stats.setHorizontalScrollBarEnabled(false);stats.setFillViewport(true);
   LinearLayout line=horizontal();line.setPadding(dp(7),dp(3),dp(7),dp(3));line.setBackground(edge(panel(),13));
   stats.addView(line,new android.widget.FrameLayout.LayoutParams(-2,dp(39)));
   CallHistory.Summary s=CallHistory.summary;
   String[] periods={"היום","השבוע","החודש","השנה"};
   TextView selector=text(periods[period]+" ▾",12,tint(),true);selector.setPadding(dp(10),0,dp(10),0);
   selector.setBackground(bg(soft(),9));line.addView(selector,lp(-2,31));
   touch(selector);
   selector.setOnClickListener(v->{pulse(selector);period=(period+1)%4;host.refreshPage();});
   stat(line,Math.round(s.seconds[period]/60.0)+" דק׳",true);
   stat(line,s.calls[period]+" שיחות",false);
   stat(line,"↗ "+s.outgoing[period],false);
   stat(line,"↙ "+s.incoming[period],false);
   stat(line,"✕ "+s.missed[period],false);
   page.addView(stats,lp(-1,42));
  }
  LinearLayout top=horizontal();page.addView(top,lp(-1,41));
  TextView title=text("כל השיחות",15,ink(),true);
  top.addView(title,new LinearLayout.LayoutParams(0,-1,1));
  TextView count=text(String.valueOf(CallHistory.entries.size()),11,subtle(),false);
  top.addView(count,lp(-2,38));
  ImageView refresh=icon(R.drawable.ic_refresh,tint());top.addView(refresh,lp(42,40));
  touch(refresh);refresh.setContentDescription("רענון יומן השיחות");
  refresh.setOnClickListener(v->{pulse(refresh);host.reloadCallLog();});
  ListView list=new ListView(host);
  list.setDivider(null);list.setDividerHeight(0);
  list.setCacheColorHint(Color.TRANSPARENT);list.setScrollingCacheEnabled(true);
  list.setFastScrollEnabled(true);list.setSmoothScrollbarEnabled(true);
  list.setVerticalScrollBarEnabled(false);
  list.setSelector(android.R.color.transparent);
  list.setAdapter(new RecentsAdapter());
  page.addView(list,new LinearLayout.LayoutParams(-1,0,1));
  if(CallHistory.entries.isEmpty()){
   TextView empty=text("אין שיחות להצגה",14,subtle(),false);
   empty.setGravity(Gravity.CENTER);
   page.addView(empty,lp(-1,80));
  }
  return page;
 }
 private void stat(LinearLayout line,String value,boolean strong){
  TextView t=text(value,11,strong?tint():subtle(),strong);
  t.setPadding(dp(9),0,dp(9),0);line.addView(t,lp(-2,32));
 }
 View contacts(List<Contacts.Person> contacts,boolean favorites){
  if(host.checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED){
   return permission("כדי לראות אנשי קשר נדרשת הרשאה","אפשר גישה לאנשי קשר",()->host.requestContacts());
  }
  LinearLayout page=vertical();page.setBackgroundColor(background());
  page.setPadding(dp(12),dp(7),dp(12),0);
  if(favorites){
   LinearLayout header=horizontal();page.addView(header,lp(-1,41));
   header.addView(text("חיוג מיידי למועדפים",14,ink(),true),new LinearLayout.LayoutParams(0,-1,1));
   TextView edit=text("ניהול ★",12,tint(),true);
   edit.setGravity(Gravity.CENTER);header.addView(edit,lp(83,38));
   touch(edit);edit.setOnClickListener(v->{pulse(edit);host.openContactsTab();});
   GridView grid=new GridView(host);
   grid.setNumColumns(host.getResources().getDisplayMetrics().widthPixels/dp(170)>=2?2:1);
   grid.setColumnWidth(dp(152));grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
   grid.setHorizontalSpacing(dp(10));grid.setVerticalSpacing(dp(10));
   grid.setPadding(0,dp(7),0,dp(18));grid.setClipToPadding(false);
   grid.setSelector(android.R.color.transparent);
   Set<String> starred=new HashSet<>(prefs.getStringSet("favorites",java.util.Collections.emptySet()));
   ArrayList<Contacts.Person> chosen=new ArrayList<>();
   for(Contacts.Person c:contacts)if(!c.phone.isEmpty()&&starred.contains(c.key))chosen.add(c);
   grid.setAdapter(new FavoriteAdapter(chosen));
   page.addView(grid,new LinearLayout.LayoutParams(-1,0,1));
   if(chosen.isEmpty()){
    TextView empty=text("עדיין אין מועדפים. סמן ★ באנשי קשר.",14,subtle(),false);
    empty.setGravity(Gravity.CENTER);grid.setEmptyView(empty);
   }
   return page;
  }
  LinearLayout head=horizontal();page.addView(head,lp(-1,48));
  EditText search=new EditText(host);
  search.setSingleLine(true);search.setHint("חיפוש שם או מספר");
  search.setHintTextColor(subtle());search.setTextColor(ink());search.setTextSize(15);
  search.setPadding(dp(12),0,dp(12),0);search.setBackground(edge(panel(),14));
  head.addView(search,new LinearLayout.LayoutParams(0,dp(46),1));
  TextView add=text("＋",28,tint(),true);add.setGravity(Gravity.CENTER);
  add.setContentDescription("הוספת איש קשר חדש");
  head.addView(add,lp(51,46));touch(add);
  add.setOnClickListener(v->{pulse(add);host.newContact(null);});
  LinearLayout small=horizontal();page.addView(small,lp(-1,31));
  TextView count=text(contacts.size()+" אנשי קשר",11,subtle(),false);
  small.addView(count,new LinearLayout.LayoutParams(0,-1,1));
  ImageView refresh=icon(R.drawable.ic_refresh,tint());small.addView(refresh,lp(38,30));
  touch(refresh);refresh.setOnClickListener(v->{pulse(refresh);host.reloadContacts();});
  ContactAdapter adapter=new ContactAdapter(contacts);
  ListView list=new ListView(host);
  list.setDivider(null);list.setFastScrollEnabled(true);list.setVerticalScrollBarEnabled(false);
  list.setSelector(android.R.color.transparent);
  list.setCacheColorHint(Color.TRANSPARENT);
  list.setAdapter(adapter);
  page.addView(list,new LinearLayout.LayoutParams(-1,0,1));
  search.addTextChangedListener(new TextWatcher(){
   public void beforeTextChanged(CharSequence v,int a,int b,int c){}
   public void afterTextChanged(Editable e){}
   public void onTextChanged(CharSequence str,int a,int b,int c){
    String query=str.toString();
    handler.removeCallbacksAndMessages(null);
    handler.postDelayed(()->adapter.filter(query),80);
   }
  });
  if(contacts.isEmpty()) {
   TextView empty=text("אנשי הקשר נטענים… אפשר גם לרענן.",14,subtle(),false);
   empty.setGravity(Gravity.CENTER);list.setEmptyView(empty);
  }
  return page;
 }
 private View permission(String info,String action,Runnable callback){
  LinearLayout box=vertical();box.setGravity(Gravity.CENTER);box.setPadding(dp(20),dp(20),dp(20),dp(20));
  box.setBackgroundColor(background());
  TextView t=text(info,16,ink(),true);t.setGravity(Gravity.CENTER);box.addView(t,lp(-1,61));
  TextView button=text(action,15,Color.BLACK,true);button.setGravity(Gravity.CENTER);
  button.setBackground(bg(gold,14));box.addView(button,lp(-1,54));
  touch(button);button.setOnClickListener(v->{pulse(button);callback.run();});
  return box;
 }
 private String contactName(String number){
  Map<String,String> names=host.nameIndex();
  String name=names.get(Contacts.canonical(number));
  return name==null||name.isEmpty()?number:name;
 }
 private String category(int type,long seconds){
  if(type==CallLog.Calls.MISSED_TYPE)return "לא נענתה";
  if(type==CallLog.Calls.REJECTED_TYPE)return "נדחתה";
  if(type==CallLog.Calls.BLOCKED_TYPE)return "נחסמה";
  if(type==CallLog.Calls.OUTGOING_TYPE)return seconds==0?"יוצאת · ללא זמן שיחה":"יוצאת";
  if(type==CallLog.Calls.INCOMING_TYPE)return "נכנסת";
  return "שיחה";
 }
 private int color(int type,long seconds){
  if(type==CallLog.Calls.MISSED_TYPE)return 0xFFE85D67;
  if(type==CallLog.Calls.REJECTED_TYPE||type==CallLog.Calls.OUTGOING_TYPE&&seconds==0)return 0xFFE9A25A;
  if(type==CallLog.Calls.BLOCKED_TYPE)return 0xFFAA83BF;
  if(type==CallLog.Calls.OUTGOING_TYPE)return 0xFF21B18B;
  if(type==CallLog.Calls.INCOMING_TYPE)return 0xFF669FE8;
  return subtle();
 }
 private final class RecentsAdapter extends BaseAdapter {
  public int getCount(){return CallHistory.entries.size();}
  public Object getItem(int position){return CallHistory.entries.get(position);}
  public long getItemId(int position){return position;}
  public View getView(int position,View convert,ViewGroup parent){
   CallRow recycled=convert instanceof LinearLayout && convert.getTag() instanceof CallRow?(CallRow)convert.getTag():null;
   final CallRow row=recycled==null?new CallRow():recycled;
   CallHistory.Entry entry=CallHistory.entries.get(position);
   row.name.setText(contactName(entry.number));
   row.arrow.setText(entry.type==CallLog.Calls.OUTGOING_TYPE?"↗":
      entry.type==CallLog.Calls.REJECTED_TYPE||entry.type==CallLog.Calls.BLOCKED_TYPE?"⊘":"↙");
   int color=color(entry.type,entry.duration);
   row.arrow.setTextColor(color);
   row.arrow.setContentDescription(category(entry.type,entry.duration));
   row.info.setText(category(entry.type,entry.duration)+"  ·  "+format.format(new Date(entry.date))+
      (entry.duration>0?" · "+Math.max(1,(int)Math.ceil(entry.duration/60.0))+" דק׳":""));
   row.root.setOnClickListener(v->{pulse(row.root);host.openDialPad(entry.number);});
   row.call.setOnClickListener(v->{pulse(row.call);host.dialFromList(entry.number);});
   swipe(row.root,entry.number);
   return row.root;
  }
 }
 private final class CallRow {
  final LinearLayout root;
  final TextView arrow,name,info;
  final FrameLayout call;
  CallRow(){
   root=horizontal();root.setPadding(dp(5),dp(3),dp(5),dp(3));
   root.setBackground(bg(panel(),15));root.setMinimumHeight(dp(69));
   LinearLayout.LayoutParams ignored=lp(-1,69);
   arrow=text("",25,ink(),true);arrow.setGravity(Gravity.CENTER);
   arrow.setBackground(bg(dark()?0xFF2D2D2D:0xFFF2F0EC,13));
   root.addView(arrow,lp(45,48));
   LinearLayout detail=vertical();detail.setPadding(dp(11),0,dp(7),0);
   root.addView(detail,new LinearLayout.LayoutParams(0,-2,1));
   name=text("",15,ink(),true);name.setSingleLine(true);
   name.setEllipsize(android.text.TextUtils.TruncateAt.END);detail.addView(name);
   info=text("",11,subtle(),false);info.setSingleLine(true);
   info.setEllipsize(android.text.TextUtils.TruncateAt.END);detail.addView(info);
   call=new FrameLayout(host);
   call.setBackground(edge(dark()?0xFF242424:0xFFF5F4F2,13));
   ImageView symbol=icon(R.drawable.ic_phone_line,tint());
   call.addView(symbol,new FrameLayout.LayoutParams(dp(39),dp(39),Gravity.CENTER));
   root.addView(call,lp(44,48));
   touch(call);touch(root);
   root.setTag(this);
  }
 }
 private final class ContactAdapter extends BaseAdapter {
  private final List<Contacts.Person> all;
  private final ArrayList<Contacts.Person> filtered=new ArrayList<>();
  ContactAdapter(List<Contacts.Person> contacts){all=contacts;filtered.addAll(all);}
  void filter(String q){
   filtered.clear();String value=q.trim().toLowerCase(Locale.ROOT);
   if(value.isEmpty())filtered.addAll(all);
   else for(Contacts.Person p:all)if(p.name.toLowerCase(Locale.ROOT).contains(value)||
      p.phone.contains(value))filtered.add(p);
   notifyDataSetChanged();
  }
  public int getCount(){return filtered.size();}
  public Object getItem(int p){return filtered.get(p);}
  public long getItemId(int p){return filtered.get(p).id;}
  public View getView(int position,View convert,ViewGroup parent){
   ContactRow row=convert!=null && convert.getTag() instanceof ContactRow?(ContactRow)convert.getTag():new ContactRow();
   Contacts.Person person=filtered.get(position);
   boolean favorite=prefs.getStringSet("favorites",java.util.Collections.emptySet()).contains(person.key);
   row.avatar.setText(person.name.isEmpty()?"?":person.name.substring(0,1));
   row.title.setText(person.name);
   row.phone.setText(person.phone.isEmpty()?"ללא מספר":person.phone);
   row.star.setText(favorite?"★":"☆");
   row.star.setTextColor(favorite?tint():subtle());
   row.root.setOnClickListener(v->{pulse(row.root);host.editContact(person.id,null);});
   row.quick.setAlpha(person.phone.isEmpty()?0.2f:1);
   row.quick.setOnClickListener(v->{if(!person.phone.isEmpty()){pulse(row.quick);host.dialFromList(person.phone);}});
   row.star.setOnClickListener(v->{pulse(row.star);host.favoriteFromList(person);});
   if(!person.phone.isEmpty())swipe(row.root,person.phone);else row.root.setOnTouchListener(null);
   return row.root;
  }
 }
 private final class ContactRow {
  final LinearLayout root;
  final TextView avatar,title,phone,star;
  final FrameLayout quick;
  ContactRow(){
   root=horizontal();root.setMinimumHeight(dp(69));root.setPadding(dp(5),dp(3),dp(5),dp(3));
   avatar=text("",19,tint(),true);avatar.setGravity(Gravity.CENTER);
   avatar.setBackground(bg(soft(),16));root.addView(avatar,lp(47,47));
   LinearLayout description=vertical();description.setPadding(dp(12),0,dp(5),0);
   root.addView(description,new LinearLayout.LayoutParams(0,-2,1));
   title=text("",15,ink(),true);title.setSingleLine(true);
   title.setEllipsize(android.text.TextUtils.TruncateAt.END);description.addView(title);
   phone=text("",11,subtle(),false);phone.setSingleLine(true);description.addView(phone);
   star=text("☆",27,subtle(),false);star.setGravity(Gravity.CENTER);
   root.addView(star,lp(43,51));
   quick=new FrameLayout(host);
   ImageView icon=icon(R.drawable.ic_phone_line,tint());
   quick.addView(icon,new FrameLayout.LayoutParams(dp(42),dp(42),Gravity.CENTER));
   root.addView(quick,lp(44,49));
   touch(star);touch(quick);touch(root);root.setTag(this);
  }
 }
 private final class FavoriteAdapter extends BaseAdapter {
  final ArrayList<Contacts.Person> favorite;
  FavoriteAdapter(ArrayList<Contacts.Person> favorites){favorite=favorites;}
  public int getCount(){return favorite.size();}
  public Object getItem(int position){return favorite.get(position);}
  public long getItemId(int position){return favorite.get(position).id;}
  public View getView(int position,View convert,ViewGroup parent){
   FavoriteTile tile=convert!=null&&convert.getTag() instanceof FavoriteTile?(FavoriteTile)convert.getTag():new FavoriteTile();
   Contacts.Person who=favorite.get(position);
   tile.avatar.setText(who.name.isEmpty()?"?":who.name.substring(0,1));
   tile.name.setText(who.name);
   tile.number.setText(who.phone);
   tile.root.setOnClickListener(v->{pulse(tile.root);host.dialFromList(who.phone);});
   tile.root.setOnLongClickListener(v->{pulse(tile.root);host.editContact(who.id,null);return true;});
   return tile.root;
  }
 }
 private final class FavoriteTile {
  final LinearLayout root; final TextView avatar,name,number;
  FavoriteTile(){
   root=vertical();root.setGravity(Gravity.CENTER);
   root.setBackground(edge(panel(),22));root.setPadding(dp(8),dp(10),dp(8),dp(8));
   root.setMinimumHeight(dp(155));
   avatar=text("",27,tint(),true);avatar.setGravity(Gravity.CENTER);
   avatar.setBackground(bg(soft(),40));root.addView(avatar,lp(62,62));
   name=text("",14,ink(),true);name.setGravity(Gravity.CENTER);name.setSingleLine(true);
   name.setEllipsize(android.text.TextUtils.TruncateAt.END);
   root.addView(name,lp(-1,29));
   number=text("",12,subtle(),false);number.setGravity(Gravity.CENTER);
   number.setSingleLine(true);root.addView(number,lp(-1,24));
   ImageView call=icon(R.drawable.ic_phone_line,tint());
   root.addView(call,lp(35,35));touch(root);root.setTag(this);
  }
 }
}
