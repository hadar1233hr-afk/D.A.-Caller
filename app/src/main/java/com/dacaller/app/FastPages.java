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
  t.setText(I18n.text(host,value));t.setTextColor(color);t.setTextSize(sp);
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
 private String tr(String heb){return I18n.text(host,heb);}
 interface NumberAt {String phone(int position);}
 private final class SwipeListView extends ListView {
  private NumberAt numbers;
  private float downX,downY;
  private int touched=-1;
  private boolean sliding=false;
  SwipeListView(){super(host);}
  void setNumberAt(NumberAt source){numbers=source;}
  private boolean allowed(float dx,float dy){
   String mode=prefs.getString("swipe_direction","right");
   if(mode.equals("off"))return false;
   if(Math.abs(dx)<dp(50)||Math.abs(dx)<Math.abs(dy)*1.35f)return false;
   return (dx>0&&mode.equals("right"))||(dx<0&&mode.equals("left"));
  }
  @Override public boolean onInterceptTouchEvent(MotionEvent event){
   switch(event.getActionMasked()){
    case MotionEvent.ACTION_DOWN:
     downX=event.getX();downY=event.getY();touched=pointToPosition((int)downX,(int)downY);
     sliding=false;break;
    case MotionEvent.ACTION_MOVE:
     if(allowed(event.getX()-downX,event.getY()-downY)&&touched>=0){
      sliding=true;
      getParent().requestDisallowInterceptTouchEvent(true);
      return true;
     }
     break;
    case MotionEvent.ACTION_UP:
    case MotionEvent.ACTION_CANCEL:sliding=false;break;
   }
   return super.onInterceptTouchEvent(event);
  }
  @Override public boolean onTouchEvent(MotionEvent event){
   if(sliding){
    if(event.getActionMasked()==MotionEvent.ACTION_UP){
     float dx=event.getX()-downX,dy=event.getY()-downY;
     if(allowed(dx,dy)&&numbers!=null){
      String phone=numbers.phone(touched);
      if(phone!=null&&!phone.isEmpty()){
       pulse(this);host.dialFromList(phone);
      }
     }
     sliding=false;return true;
    }
    if(event.getActionMasked()==MotionEvent.ACTION_CANCEL){sliding=false;return true;}
    return true;
   }
   return super.onTouchEvent(event);
  }
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
   String[] periods={tr("היום"),tr("השבוע"),tr("החודש"),tr("השנה")};
   TextView selector=text(periods[period]+" ▾",12,tint(),true);selector.setPadding(dp(10),0,dp(10),0);
   selector.setBackground(bg(soft(),9));line.addView(selector,lp(-2,31));
   touch(selector);
   selector.setOnClickListener(v->{pulse(selector);period=(period+1)%4;host.refreshPage();});
   stat(line,Math.round(s.seconds[period]/60.0)+" "+tr("דק׳"),true);
   stat(line,s.calls[period]+" "+tr("שיחות"),false);
   stat(line,"↗ "+s.outgoing[period],false);
   stat(line,"↙ "+s.incoming[period],false);
   stat(line,"✕ "+s.missed[period],false);
   page.addView(stats,lp(-1,42));
  }
  LinearLayout top=horizontal();page.addView(top,lp(-1,41));
  TextView title=text(tr("כל השיחות"),15,ink(),true);
  top.addView(title,new LinearLayout.LayoutParams(0,-1,1));
  TextView count=text(String.valueOf(CallHistory.entries.size()),11,subtle(),false);
  top.addView(count,lp(-2,38));
  ImageView refresh=icon(R.drawable.ic_refresh,tint());top.addView(refresh,lp(42,40));
  touch(refresh);refresh.setContentDescription("רענון יומן השיחות");
  refresh.setOnClickListener(v->{pulse(refresh);host.reloadCallLog();});
  SwipeListView list=new SwipeListView();
  list.setDivider(null);list.setDividerHeight(0);
  list.setCacheColorHint(Color.TRANSPARENT);list.setScrollingCacheEnabled(true);
  list.setFastScrollEnabled(true);list.setSmoothScrollbarEnabled(true);
  list.setVerticalScrollBarEnabled(false);
  list.setSelector(android.R.color.transparent);
  list.setNumberAt(position->position>=0&&position<CallHistory.entries.size()?
      CallHistory.entries.get(position).number:null);
  if(CallArchive.enabled(host)){
   TextView archive=text(tr("ארכיון שיחות")+"   ◈",14,tint(),true);
   archive.setGravity(Gravity.CENTER);
   archive.setBackground(edge(panel(),16));
   archive.setPadding(dp(10),dp(16),dp(10),dp(16));
   archive.setMinHeight(dp(72));
   touch(archive);
   archive.setContentDescription(tr("ארכיון שיחות"));
   archive.setOnClickListener(v->{pulse(archive);host.openArchive();});
   // Footer appears only AFTER the last current Android call.
   list.addFooterView(archive,null,true);
  }
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
   header.addView(text(tr("חיוג מיידי למועדפים"),14,ink(),true),new LinearLayout.LayoutParams(0,-1,1));
   TextView edit=text(tr("ניהול ★"),12,tint(),true);
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
  search.setSingleLine(true);search.setHint(tr("חיפוש שם או מספר"));
  search.setHintTextColor(subtle());search.setTextColor(ink());search.setTextSize(15);
  search.setPadding(dp(12),0,dp(12),0);search.setBackground(edge(panel(),14));
  head.addView(search,new LinearLayout.LayoutParams(0,dp(46),1));
  TextView add=text("＋",28,tint(),true);add.setGravity(Gravity.CENTER);
  add.setContentDescription("הוספת איש קשר חדש");
  head.addView(add,lp(51,46));touch(add);
  add.setOnClickListener(v->{pulse(add);host.newContact(null);});
  LinearLayout small=horizontal();page.addView(small,lp(-1,31));
  TextView count=text(I18n.count(host,contacts.size(),"אנשי קשר","contacts"),11,subtle(),false);
  small.addView(count,new LinearLayout.LayoutParams(0,-1,1));
  ImageView refresh=icon(R.drawable.ic_refresh,tint());small.addView(refresh,lp(38,30));
  touch(refresh);refresh.setOnClickListener(v->{pulse(refresh);host.reloadContacts();});
  ContactAdapter adapter=new ContactAdapter(contacts);
  SwipeListView list=new SwipeListView();
  list.setDivider(null);list.setFastScrollEnabled(true);list.setVerticalScrollBarEnabled(false);
  list.setSelector(android.R.color.transparent);
  list.setCacheColorHint(Color.TRANSPARENT);
  list.setAdapter(adapter);
  list.setNumberAt(position->adapter.phoneAt(position));
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
  if(type==CallLog.Calls.MISSED_TYPE)return tr("לא נענתה");
  if(type==CallLog.Calls.REJECTED_TYPE)return tr("נדחתה");
  if(type==CallLog.Calls.BLOCKED_TYPE)return tr("נחסמה");
  if(type==CallLog.Calls.OUTGOING_TYPE)return seconds==0?tr("יוצאת · ללא זמן שיחה"):tr("יוצאת");
  if(type==CallLog.Calls.INCOMING_TYPE)return tr("נכנסת");
  return tr("שיחה");
 }
 private int color(int type,long seconds){
  if(type==CallLog.Calls.MISSED_TYPE)return 0xFFE85D67;
  if(type==CallLog.Calls.REJECTED_TYPE||type==CallLog.Calls.OUTGOING_TYPE&&seconds==0)return 0xFFE9A25A;
  if(type==CallLog.Calls.BLOCKED_TYPE)return 0xFFAA83BF;
  if(type==CallLog.Calls.OUTGOING_TYPE)return 0xFF21B18B;
  if(type==CallLog.Calls.INCOMING_TYPE)return 0xFF669FE8;
  return subtle();
 }
 private int recentCallColor(){
  return prefs.getBoolean("recents_green",false)?0xFF128758:tint();
 }
 private final class RecentsAdapter extends BaseAdapter {
  @Override public int getCount(){return CallHistory.entries.size();}
  @Override public Object getItem(int p){return CallHistory.entries.get(p);}
  @Override public long getItemId(int p){return CallHistory.entries.get(p).id;}
  @Override public View getView(int position,View convert,ViewGroup parent){
   CallRow recycled=convert!=null&&convert.getTag() instanceof CallRow?(CallRow)convert.getTag():null;
   final CallRow row=recycled!=null?recycled:new CallRow();
   CallHistory.Entry entry=CallHistory.entries.get(position);
   row.bound=entry.number+"|"+entry.date;
   row.initials.setText(entry.number.isEmpty()?"?":entry.number.substring(0,1));
   row.photo.setVisibility(View.GONE);
   row.name.setText(contactName(entry.number));
   row.arrow.setText(entry.type==CallLog.Calls.OUTGOING_TYPE?"↗":
      entry.type==CallLog.Calls.REJECTED_TYPE||entry.type==CallLog.Calls.BLOCKED_TYPE?"⊘":"↙");
   row.arrow.setTextColor(color(entry.type,entry.duration));
   row.arrow.setContentDescription(tr(category(entry.type,entry.duration)));
   String phone=entry.number;
   row.info.setText(category(entry.type,entry.duration)+"  ·  "+
    format.format(new Date(entry.date))+
    (entry.duration>0?" · "+Math.max(1,(int)Math.ceil(entry.duration/60.0))+" "+tr("דק׳"):""));
   row.dialIcon.setImageTintList(ColorStateList.valueOf(recentCallColor()));
   row.avatar.setOnClickListener(v->{pulse(row.avatar);host.openContactFromNumber(phone);});
   row.center.setOnClickListener(v->{pulse(row.center);host.showCallNumber(entry);});
   row.call.setOnClickListener(v->{pulse(row.call);host.dialFromList(phone);});
   String expected=row.bound;
   ContactPhotos.lookup(host,phone,(id,n,bitmap)->{
    if(!expected.equals(row.bound))return;
    if(n!=null&&!n.isEmpty()&&!n.equals(phone))row.name.setText(n);
    if(bitmap!=null){row.photo.setImageBitmap(bitmap);row.photo.setVisibility(View.VISIBLE);}
   });
   return row.root;
  }
 }
 private final class CallRow {
  final LinearLayout root,center;
  final FrameLayout avatar,call;
  final TextView initials,arrow,name,info;
  final ImageView photo,dialIcon;
  String bound="";
  CallRow(){
   root=horizontal();
   root.setPadding(dp(6),dp(6),dp(6),dp(6));
   root.setBackground(bg(panel(),16));
   root.setMinimumHeight(dp(76));
   avatar=new FrameLayout(host);
   avatar.setBackground(bg(soft(),16));
   LinearLayout.LayoutParams avatarParams=lp(52,52);
   avatarParams.setMargins(dp(2),0,dp(5),0);
   root.addView(avatar,avatarParams);
   initials=text("?",20,tint(),true);initials.setGravity(Gravity.CENTER);
   avatar.addView(initials,new FrameLayout.LayoutParams(-1,-1));
   photo=new ImageView(host);photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
   photo.setClipToOutline(true);photo.setVisibility(View.GONE);
   avatar.addView(photo,new FrameLayout.LayoutParams(-1,-1));
   View lineA=new View(host);lineA.setBackgroundColor(dark()?0xFF3A3A3A:0xFFE6E6E6);
   root.addView(lineA,lp(1,34));
   center=horizontal();center.setPadding(dp(9),0,dp(9),0);
   root.addView(center,new LinearLayout.LayoutParams(0,d(59),1));
   LinearLayout details=vertical();center.addView(details,new LinearLayout.LayoutParams(0,-2,1));
   name=text("",15,ink(),true);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
   details.addView(name);
   info=text("",11,subtle(),false);info.setSingleLine(true);info.setEllipsize(android.text.TextUtils.TruncateAt.END);
   details.addView(info);
   arrow=text("",19,subtle(),true);arrow.setGravity(Gravity.CENTER);
   center.addView(arrow,lp(27,40));
   View lineB=new View(host);lineB.setBackgroundColor(dark()?0xFF3A3A3A:0xFFE6E6E6);
   root.addView(lineB,lp(1,34));
   call=new FrameLayout(host);
   call.setBackground(bg(dark()?0xFF22372D:0xFFE7F4EA,15));
   LinearLayout.LayoutParams cp=lp(60,56);cp.setMargins(dp(5),0,0,0);
   root.addView(call,cp);
   dialIcon=icon(R.drawable.ic_phone_line,recentCallColor());
   dialIcon.setPadding(dp(11),dp(11),dp(11),dp(11));
   call.addView(dialIcon,new FrameLayout.LayoutParams(-1,-1));
   touch(avatar);touch(center);touch(call);
   avatar.setContentDescription(tr("כרטיס איש קשר"));
   center.setContentDescription(tr("הצג מספר"));
   call.setContentDescription(tr("התקשר"));
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
  String phoneAt(int pos){return pos>=0&&pos<filtered.size()?filtered.get(pos).phone:null;}
  public View getView(int position,View convert,ViewGroup parent){
   ContactRow row=convert!=null && convert.getTag() instanceof ContactRow?(ContactRow)convert.getTag():new ContactRow();
   Contacts.Person person=filtered.get(position);
   boolean favorite=prefs.getStringSet("favorites",java.util.Collections.emptySet()).contains(person.key);
   row.letter.setText(person.name.isEmpty()?"?":person.name.substring(0,1));
   row.bound=String.valueOf(person.id);row.photo.setVisibility(View.GONE);
   String expected=row.bound;
   ContactPhotos.load(host,person.id,image->{
    if(expected.equals(row.bound)&&image!=null){row.photo.setImageBitmap(image);row.photo.setVisibility(View.VISIBLE);}
   });
   row.title.setText(person.name);
   row.phone.setText(person.phone.isEmpty()?tr("ללא מספר"):person.phone);
   row.star.setText(favorite?"★":"☆");
   row.star.setTextColor(favorite?tint():subtle());
   row.root.setOnClickListener(v->{pulse(row.root);host.openContactDetail(person.id);});
   row.quick.setAlpha(person.phone.isEmpty()?0.2f:1);
   row.quick.setOnClickListener(v->{if(!person.phone.isEmpty()){pulse(row.quick);host.dialFromList(person.phone);}});
   row.star.setOnClickListener(v->{pulse(row.star);host.favoriteFromList(person);});
   row.avatar.setOnClickListener(v->{pulse(row.avatar);host.openContactDetail(person.id);});
   return row.root;
  }
 }
 private final class ContactRow {
  final LinearLayout root;
  final FrameLayout avatar,quick;
  final TextView letter,title,phone,star;
  final ImageView photo;
  String bound="";
  ContactRow(){
   root=horizontal();root.setMinimumHeight(dp(71));root.setPadding(dp(5),dp(4),dp(5),dp(4));
   avatar=new FrameLayout(host);avatar.setBackground(bg(soft(),17));
   root.addView(avatar,lp(49,49));
   letter=text("",19,tint(),true);letter.setGravity(Gravity.CENTER);
   avatar.addView(letter,new FrameLayout.LayoutParams(-1,-1));
   photo=new ImageView(host);photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
   photo.setClipToOutline(true);photo.setVisibility(View.GONE);
   avatar.addView(photo,new FrameLayout.LayoutParams(-1,-1));
   LinearLayout description=vertical();description.setPadding(dp(11),0,dp(5),0);
   root.addView(description,new LinearLayout.LayoutParams(0,-2,1));
   title=text("",15,ink(),true);title.setSingleLine(true);
   title.setEllipsize(android.text.TextUtils.TruncateAt.END);description.addView(title);
   phone=text("",11,subtle(),false);phone.setSingleLine(true);description.addView(phone);
   star=text("☆",26,subtle(),false);star.setGravity(Gravity.CENTER);
   root.addView(star,lp(42,51));
   quick=new FrameLayout(host);quick.setBackground(bg(dark()?0xFF29352F:0xFFE7F4EA,14));
   ImageView icon=icon(R.drawable.ic_phone_line,0xFF128758);
   quick.addView(icon,new FrameLayout.LayoutParams(dp(49),dp(49),Gravity.CENTER));
   root.addView(quick,lp(49,50));
   touch(star);touch(quick);touch(root);touch(avatar);root.setTag(this);
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
