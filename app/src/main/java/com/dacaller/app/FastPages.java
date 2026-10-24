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
import android.os.Bundle;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.provider.CallLog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
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
 private DateFormat format;
 private final int gold=0xFFD2AE69;
 private int period=0;
 private SwipeListView liveRecents,liveContacts;
 private int recentPos=0,recentOffset=0,contactPos=0,contactOffset=0;
 private long recentId=-1,contactId=-1;
 private String contactQuery="";
 private boolean contactQueryChanged;
 private Runnable filterTask;
 FastPages(MainActivity activity){
  host=activity;
  prefs=activity.getSharedPreferences("da_caller",Context.MODE_PRIVATE);
  format=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT,
    I18n.isEnglish(host)?Locale.ENGLISH:new Locale("he","IL"));
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
  LinearLayout v=new LinearLayout(host);v.setOrientation(LinearLayout.VERTICAL);v.setLayoutDirection(I18n.isEnglish(host)?View.LAYOUT_DIRECTION_LTR:View.LAYOUT_DIRECTION_RTL);return v;
 }
 private LinearLayout horizontal(){
  LinearLayout v=new LinearLayout(host);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);
  v.setLayoutDirection(I18n.isEnglish(host)?View.LAYOUT_DIRECTION_LTR:View.LAYOUT_DIRECTION_RTL);return v;
 }
 private LinearLayout.LayoutParams lp(int w,int h){
  return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));
 }
 private void pulse(View v){TouchFeedback.pulse(host,prefs,v);}
 private void touch(View v){TouchFeedback.bind(v,prefs);}
 private String tr(String heb){return I18n.text(host,heb);}
 void setContactQuery(String query){
  contactQuery=query==null?"":query;contactPos=0;contactOffset=0;contactId=-1;contactQueryChanged=true;
 }
 boolean isInteracting(){
  return (liveRecents!=null&&liveRecents.touching)||(liveContacts!=null&&liveContacts.touching);
 }
 void remember(String tab){
  if(tab.equals("contacts")&&contactQueryChanged)return;
  SwipeListView view=tab.equals("recents")?liveRecents:
    tab.equals("contacts")?liveContacts:null;
  if(view==null||view.getChildCount()==0)return;
  int pos=view.getFirstVisiblePosition();
  int offset=view.getChildAt(0).getTop();
  if(view.getAdapter()==null||pos>=view.getAdapter().getCount())return;
  long id=view.getAdapter().getItemId(pos);
  if(tab.equals("recents")){recentPos=pos;recentOffset=offset;recentId=id;}
  else if(tab.equals("contacts")){contactPos=pos;contactOffset=offset;contactId=id;}
 }
 void saveState(Bundle state){
  state.putInt("recent_position",recentPos);state.putInt("recent_offset",recentOffset);
  state.putInt("contact_position",contactPos);state.putInt("contact_offset",contactOffset);
  state.putLong("recent_id",recentId);state.putLong("contact_id",contactId);
  state.putString("contact_query",contactQuery);state.putInt("stats_period",period);
 }
 void restoreState(Bundle state){
  if(state==null)return;
  recentPos=state.getInt("recent_position");recentOffset=state.getInt("recent_offset");
  contactPos=state.getInt("contact_position");contactOffset=state.getInt("contact_offset");
  recentId=state.getLong("recent_id",-1);contactId=state.getLong("contact_id",-1);
  contactQuery=state.getString("contact_query","");period=Math.max(0,Math.min(3,state.getInt("stats_period")));
 }
 void restore(String tab){
  SwipeListView view=tab.equals("recents")?liveRecents:
    tab.equals("contacts")?liveContacts:null;
  if(view==null)return;
  int pos=tab.equals("recents")?recentPos:contactPos;
  int offset=tab.equals("recents")?recentOffset:contactOffset;
  long id=tab.equals("recents")?recentId:contactId;
  android.widget.ListAdapter adapter=view.getAdapter();
  int max=adapter==null?0:Math.max(0,adapter.getCount()-1);
  if(adapter!=null&&id>=0&&(pos>=adapter.getCount()||adapter.getItemId(pos)!=id)){
   for(int i=0;i<adapter.getCount();i++)if(adapter.getItemId(i)==id){pos=i;break;}
  }
  int finalPos=Math.max(0,Math.min(pos,max));
  // A queued restore must never override a finger/flick that started meanwhile.
  int revision=view.touchRevision;
  view.post(()->{
   if(view.touchRevision==revision&&!view.touching)view.setSelectionFromTop(finalPos,offset);
  });
 }
 interface NumberAt {String phone(int position);}
 private final class SwipeListView extends ListView {
  private NumberAt numbers;
  private float downX,downY;
  private int touched=-1;
  private String downNumber;
  private boolean sliding,vertical,actionRegion;
  private int touchRevision;
  private boolean touching,fastScrollPaused;
  private final int slop=ViewConfiguration.get(host).getScaledTouchSlop();
  SwipeListView(){super(host);setItemsCanFocus(true);}
  void setNumberAt(NumberAt source){numbers=source;}
  private boolean allowed(float dx,float dy){
   String mode=prefs.getString("swipe_direction","right");
   if(mode.equals("off")||vertical||actionRegion)return false;
   if(Math.abs(dx)<dp(50)||Math.abs(dx)<Math.abs(dy)*1.5f)return false;
   return (dx>0&&mode.equals("right"))||(dx<0&&mode.equals("left"));
  }
  private boolean inside(View child,float x,float y){
   Rect rect=new Rect();child.getDrawingRect(rect);offsetDescendantRectToMyCoords(child,rect);
   return rect.contains((int)x,(int)y);
  }
  private boolean dedicatedControl(float x,float y){
   View row=getChildAt(touched-getFirstVisiblePosition());
   if(row==null)return true;
   Object tag=row.getTag();
   if(tag instanceof CallRow){
    CallRow r=(CallRow)tag;return inside(r.avatar,x,y)||inside(r.call,x,y);
   }
   if(tag instanceof ContactRow){
    ContactRow r=(ContactRow)tag;return inside(r.avatar,x,y)||inside(r.star,x,y)||inside(r.quick,x,y);
   }
   return true; // Footers and non-data rows never initiate a call gesture.
  }
  @Override public boolean dispatchTouchEvent(MotionEvent event){
   if(event.getActionMasked()==MotionEvent.ACTION_DOWN){
    touchRevision++;touching=true;
    downX=event.getX();downY=event.getY();touched=pointToPosition((int)downX,(int)downY);
    downNumber=numbers==null?null:numbers.phone(touched);
    sliding=false;vertical=false;actionRegion=dedicatedControl(downX,downY);
    // Android FastScroller intercepts the whole scrollbar edge before child
    // dispatch. On RTL/small screens that edge overlaps the quick-call icon.
    // Give explicit row actions priority for this gesture; retain fast scroll
    // everywhere else, including its visible thumb/track outside row controls.
    fastScrollPaused=actionRegion&&isFastScrollEnabled();
    if(fastScrollPaused)setFastScrollEnabled(false);
   }else if(event.getActionMasked()==MotionEvent.ACTION_POINTER_DOWN){
    vertical=true;sliding=false;downNumber=null;
   }
   boolean handled=super.dispatchTouchEvent(event);
   if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL){
    touching=false;sliding=false;downNumber=null;
    if(fastScrollPaused){fastScrollPaused=false;setFastScrollEnabled(true);}
   }
   return handled;
  }
  @Override public boolean onInterceptTouchEvent(MotionEvent event){
   if(event.getActionMasked()==MotionEvent.ACTION_MOVE){
    float dx=event.getX()-downX,dy=event.getY()-downY;
    if(!sliding&&Math.abs(dy)>slop&&Math.abs(dy)>=Math.abs(dx))vertical=true;
    if(sliding||(!vertical&&touched>=0&&allowed(dx,dy))){
     sliding=true;
     if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);
     return true;
    }
   }
   // Never clear sliding on UP here: dispatch still needs it in onTouchEvent.
   return super.onInterceptTouchEvent(event);
  }
  // Ordinary taps are delegated to ListView, which already performs accessible
  // child clicks. This override only consumes configured swipes; a second
  // performClick() here would duplicate a click or turn a swipe into a call.
  @android.annotation.SuppressLint("ClickableViewAccessibility")
  @Override public boolean onTouchEvent(MotionEvent event){
   if(sliding){
    if(event.getActionMasked()==MotionEvent.ACTION_UP){
     float dx=event.getX()-downX,dy=event.getY()-downY;
     String current=numbers==null?null:numbers.phone(touched);
     if(allowed(dx,dy)&&downNumber!=null&&!downNumber.isEmpty()&&downNumber.equals(current)){
      pulse(this);host.dialFromList(downNumber);
     }
     sliding=false;return true;
    }
    if(event.getActionMasked()==MotionEvent.ACTION_CANCEL){sliding=false;return true;}
    return true;
   }
   return super.onTouchEvent(event);
  }
  @Override public boolean performClick(){return super.performClick();}
 }
 View recents(){
  format=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT,I18n.locale(host));
  LinearLayout page=vertical();
  page.setBackgroundColor(background());
  page.setPadding(dp(12),dp(6),dp(12),0);
  if(host.checkSelfPermission(Manifest.permission.READ_CALL_LOG)!=PackageManager.PERMISSION_GRANTED){
   return permission("יומן השיחות דורש הרשאה","אפשר גישה לשיחות",()->host.requestLog());
  }
  if(prefs.getBoolean("show_stats",true)){
   CallHistory.Summary statsData=CallHistory.summary;
   LinearLayout summary=vertical();
   summary.setPadding(dp(12),dp(9),dp(12),dp(10));
   summary.setBackground(edge(panel(),18));
   LinearLayout.LayoutParams block=new LinearLayout.LayoutParams(-1,-2);
   block.bottomMargin=dp(8);page.addView(summary,block);
   LinearLayout header=horizontal();
   summary.addView(header,lp(-1,dp(33)));
   TextView heading=text(tr("סיכום פעילות"),14,ink(),true);
   header.addView(heading,new LinearLayout.LayoutParams(0,-1,1));
   String[] periods={tr("היום"),tr("השבוע"),tr("החודש"),tr("השנה")};
   TextView selector=text(periods[period]+"  ▾",12,tint(),true);
   selector.setPadding(dp(11),0,dp(10),0);
   selector.setGravity(Gravity.CENTER);
   selector.setBackground(bg(soft(),12));
   header.addView(selector,lp(-2,31));
   selector.setContentDescription(tr("תקופת הסיכום")+": "+periods[period]);
   touch(selector);
   selector.setOnClickListener(v->{pulse(selector);period=(period+1)%4;host.refreshPage();});
   LinearLayout totals=horizontal();
   totals.setGravity(Gravity.CENTER_VERTICAL);
   LinearLayout.LayoutParams totalsParams=lp(-1,49);
   totalsParams.topMargin=dp(4);summary.addView(totals,totalsParams);
   LinearLayout countBlock=vertical();
   totals.addView(countBlock,new LinearLayout.LayoutParams(0,-2,1));
   countBlock.addView(text(statsData.calls[period]+" "+tr("שיחות"),20,ink(),true));
   countBlock.addView(text(tr("סך שיחות לתקופה"),10,subtle(),false));
   LinearLayout timeBlock=vertical();
   totals.addView(timeBlock,new LinearLayout.LayoutParams(0,-2,1));
   TextView duration=text(durationText(statsData.seconds[period]),18,tint(),true);
   duration.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);timeBlock.addView(duration);
   TextView timeLabel=text(tr("זמן דיבור כולל"),10,subtle(),false);
   timeLabel.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);timeBlock.addView(timeLabel);
   LinearLayout breakdown=horizontal();
   LinearLayout.LayoutParams bp=lp(-1,53);bp.topMargin=dp(7);summary.addView(breakdown,bp);
   summaryMetric(breakdown,tr("יוצאות"),"↗",statsData.outgoing[period],0xFF2375B4);
   summaryMetric(breakdown,tr("נכנסות"),"↙",statsData.incoming[period],0xFF25815F);
   summaryMetric(breakdown,tr("שלא נענו"),"×",statsData.missed[period],0xFFBD4748);
   int answered=statsData.outgoing[period]+statsData.incoming[period];
   TextView average=text(tr("משך שיחה ממוצע")+": "+
     durationText(answered>0?statsData.seconds[period]/answered:0),11,subtle(),false);
   LinearLayout.LayoutParams ap=lp(-1,24);ap.topMargin=dp(3);
   summary.addView(average,ap);
  }
  LinearLayout top=horizontal();page.addView(top,lp(-1,41));
  TextView title=text(tr("כל השיחות"),15,ink(),true);
  top.addView(title,new LinearLayout.LayoutParams(0,-1,1));
  TextView count=text(String.valueOf(CallHistory.entries.size()),11,subtle(),false);
  top.addView(count,lp(-2,38));
  ImageView refresh=icon(R.drawable.ic_refresh,tint());top.addView(refresh,lp(42,40));
  touch(refresh);refresh.setContentDescription(tr("רענון יומן השיחות"));
  refresh.setOnClickListener(v->{pulse(refresh);host.reloadCallLog();});
  SwipeListView list=new SwipeListView();
  liveRecents=list;
  list.setDivider(null);list.setDividerHeight(0);
  list.setCacheColorHint(Color.TRANSPARENT);list.setScrollingCacheEnabled(true);
  list.setFastScrollEnabled(true);list.setSmoothScrollbarEnabled(true);
  list.setVerticalScrollBarEnabled(false);
  list.setSelector(android.R.color.transparent);
  RecentsAdapter adapter=new RecentsAdapter();
  list.setNumberAt(position->adapter.phoneAt(position));
  if(CallArchive.enabled(host)){
   int archived=CallArchive.knownMissing();
   TextView archive=text(tr("ארכיון שיחות")+(archived>0?" ("+archived+")":"")+"   ◈",14,tint(),true);
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
  list.setAdapter(adapter);
  page.addView(list,new LinearLayout.LayoutParams(-1,0,1));
  if(CallHistory.readFailed){
   TextView error=text(I18n.isEnglish(host)?"Could not refresh call history. Tap refresh to retry.":"לא ניתן לרענן את יומן השיחות. אפשר לנסות שוב בלחצן הרענון.",13,subtle(),false);
   error.setGravity(Gravity.CENTER);page.addView(error,lp(-1,64));
  }else if(CallHistory.entries.isEmpty()){
   TextView empty=text("אין שיחות להצגה",14,subtle(),false);
   empty.setGravity(Gravity.CENTER);
   page.addView(empty,lp(-1,80));
  }
  return page;
 }
 private String durationText(long seconds){
  long total=Math.max(0,seconds);
  long hours=total/3600,minutes=(total%3600)/60;
  if(hours>0)return hours+" "+tr("שעות")+" "+minutes+" "+tr("דק׳");
  if(minutes>0)return minutes+" "+tr("דק׳");
  return total+" "+tr("שניות");
 }
 private void summaryMetric(LinearLayout parent,String title,String symbol,int value,int baseColor){
  LinearLayout item=vertical();
  item.setGravity(Gravity.CENTER);
  item.setPadding(dp(1),dp(2),dp(1),dp(2));
  item.setBackground(bg(dark()?0xFF262626:0xFFF5F3F0,12));
  LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(0,-1,1);
  ip.leftMargin=dp(3);ip.rightMargin=dp(3);
  parent.addView(item,ip);
  int tone=dark()?ink():baseColor;
  TextView count=text(symbol+" "+value,16,tone,true);
  count.setGravity(Gravity.CENTER);item.addView(count);
  TextView label=text(title,10,subtle(),false);
  label.setGravity(Gravity.CENTER);item.addView(label);
 }
 View contacts(List<Contacts.Person> contacts,boolean favorites){
  if(filterTask!=null){handler.removeCallbacks(filterTask);filterTask=null;}
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
    empty.setGravity(Gravity.CENTER);page.addView(empty,lp(-1,100));grid.setEmptyView(empty);
   }
   return page;
  }
  LinearLayout head=horizontal();page.addView(head,lp(-1,48));
  EditText search=new EditText(host);
  search.setSingleLine(true);search.setHint(tr("חיפוש שם או מספר"));
  search.setHintTextColor(subtle());search.setTextColor(ink());search.setTextSize(15);
  search.setPadding(dp(12),0,dp(12),0);search.setBackground(edge(panel(),14));
  head.addView(search,new LinearLayout.LayoutParams(0,dp(48),1));
  TextView add=text("＋",28,tint(),true);add.setGravity(Gravity.CENTER);
  add.setContentDescription(tr("הוספת איש קשר חדש"));
  head.addView(add,lp(51,48));touch(add);
  add.setOnClickListener(v->{pulse(add);host.newContact(null);});
  // Keep the 48dp refresh target inside a matching-height parent on small screens.
  LinearLayout small=horizontal();page.addView(small,lp(-1,48));
  TextView count=text(I18n.count(host,contacts.size(),"אנשי קשר","contacts"),11,subtle(),false);
  small.addView(count,new LinearLayout.LayoutParams(0,-1,1));
  ImageView refresh=icon(R.drawable.ic_refresh,tint());small.addView(refresh,lp(48,48));
  refresh.setContentDescription(I18n.isEnglish(host)?"Refresh contacts":"רענון אנשי קשר");
  touch(refresh);refresh.setOnClickListener(v->{pulse(refresh);host.reloadContacts();});
  contactQueryChanged=false;
  ContactAdapter adapter=new ContactAdapter(contacts);
  adapter.filter(contactQuery);search.setText(contactQuery);
  count.setText(I18n.count(host,adapter.getCount(),"אנשי קשר","contacts"));
  SwipeListView list=new SwipeListView();
  liveContacts=list;
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
    contactQuery=query;
    if(filterTask!=null)handler.removeCallbacks(filterTask);
    filterTask=()->{
     adapter.filter(query);contactPos=0;contactOffset=0;contactId=-1;
     count.setText(I18n.count(host,adapter.getCount(),"אנשי קשר","contacts"));
     list.setSelectionFromTop(0,0);
    };
    handler.postDelayed(filterTask,80);
   }
  });
  TextView empty=text(I18n.isEnglish(host)?"No matching contacts":"לא נמצאו אנשי קשר",14,subtle(),false);
  empty.setGravity(Gravity.CENTER);page.addView(empty,lp(-1,100));list.setEmptyView(empty);
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
  return name==null||name.isEmpty()?(CallLauncher.isCallable(number)?number:tr("מספר חסוי")):name;
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
  // CallHistory publishes an immutable snapshot; retain its identity for this adapter.
  private final List<CallHistory.Entry> entries=CallHistory.entries;
  @Override public int getCount(){return entries.size();}
  @Override public Object getItem(int p){return entries.get(p);}
  @Override public long getItemId(int p){return entries.get(p).id;}
  @Override public boolean hasStableIds(){return true;}
  String phoneAt(int p){return p>=0&&p<entries.size()?entries.get(p).number:null;}
  @Override public View getView(int position,View convert,ViewGroup parent){
   CallRow recycled=convert!=null&&convert.getTag() instanceof CallRow?(CallRow)convert.getTag():null;
   final CallRow row=recycled!=null?recycled:new CallRow();
   CallHistory.Entry entry=entries.get(position);
   row.bound=entry.number+"|"+entry.date;
   row.initials.setText(entry.number.isEmpty()?"?":entry.number.substring(0,1));
   row.photo.setVisibility(View.GONE);
   row.name.setText(contactName(entry.number));
   row.arrow.setText(entry.type==CallLog.Calls.OUTGOING_TYPE?"↗":
      entry.type==CallLog.Calls.REJECTED_TYPE||entry.type==CallLog.Calls.BLOCKED_TYPE?"⊘":"↙");
   row.arrow.setTextColor(color(entry.type,entry.duration));
   row.arrow.setContentDescription(tr(category(entry.type,entry.duration)));
   String phone=entry.number;
   String duration=entry.duration>0?String.format(Locale.ROOT," · %d:%02d",entry.duration/60,entry.duration%60):"";
   row.info.setText(String.format(I18n.isEnglish(host)?Locale.ENGLISH:new Locale("he","IL"),"%s  ·  %s%s",
    category(entry.type,entry.duration),format.format(new Date(entry.date)),duration));
   row.dialIcon.setImageTintList(ColorStateList.valueOf(recentCallColor()));
   row.avatar.setOnClickListener(v->{pulse(row.avatar);host.openContactFromNumber(phone);});
   row.center.setOnClickListener(v->{pulse(row.center);host.showCallNumber(entry);});
   row.call.setEnabled(CallLauncher.isCallable(phone));row.call.setAlpha(row.call.isEnabled()?1f:0.35f);
   row.call.setContentDescription(tr("התקשר")+" "+phone);
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
   root.addView(center,new LinearLayout.LayoutParams(0,dp(59),1));
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
  private final ArrayList<String> normalizedNumbers=new ArrayList<>();
  ContactAdapter(List<Contacts.Person> contacts){
   all=new ArrayList<>(contacts);filtered.addAll(all);
   for(Contacts.Person person:all)normalizedNumbers.add(Contacts.canonical(person.phone));
  }
  void filter(String q){
   filtered.clear();String value=q.trim().toLowerCase(Locale.ROOT);
   if(value.isEmpty())filtered.addAll(all);
   else {
    String digits=Contacts.canonical(value);
    for(int i=0;i<all.size();i++){
     Contacts.Person p=all.get(i);
     if(p.name.toLowerCase(Locale.ROOT).contains(value)||p.phone.contains(value)||
       (!digits.isEmpty()&&normalizedNumbers.get(i).contains(digits)))filtered.add(p);
    }
   }
   notifyDataSetChanged();
  }
  public int getCount(){return filtered.size();}
  public Object getItem(int p){return filtered.get(p);}
  public long getItemId(int p){return filtered.get(p).id;}
  @Override public boolean hasStableIds(){return true;}
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
   row.quick.setEnabled(!person.phone.isEmpty());
   row.quick.setContentDescription(tr("התקשר")+" "+person.name+" "+person.phone);
   row.star.setContentDescription((I18n.isEnglish(host)?(favorite?"Remove favorite ":"Add favorite "):(favorite?"הסר מהמועדפים ":"הוסף למועדפים "))+person.name);
   row.avatar.setContentDescription(tr("כרטיס איש קשר")+" "+person.name);
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
   root.addView(star,lp(48,51));
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
   String expected=String.valueOf(who.id);
   tile.bound=expected;tile.photo.setVisibility(View.GONE);
   ContactPhotos.load(host,who.id,image->{
    if(expected.equals(tile.bound)&&image!=null){
     tile.photo.setImageBitmap(image);tile.photo.setVisibility(View.VISIBLE);
    }
   });
   tile.name.setText(who.name);
   tile.number.setText(who.phone);
   tile.root.setOnClickListener(v->{pulse(tile.root);host.dialFromList(who.phone);});
   tile.root.setOnLongClickListener(v->{pulse(tile.root);host.editContact(who.id,null);return true;});
   return tile.root;
  }
 }
 private final class FavoriteTile {
  final LinearLayout root; final TextView avatar,name,number;
  final ImageView photo;
  String bound="";
  FavoriteTile(){
   root=vertical();root.setGravity(Gravity.CENTER);
   root.setBackground(edge(panel(),22));root.setPadding(dp(8),dp(10),dp(8),dp(8));
   root.setMinimumHeight(dp(155));
   FrameLayout picture=new FrameLayout(host);
   picture.setBackground(bg(soft(),40));root.addView(picture,lp(62,62));
   avatar=text("",27,tint(),true);avatar.setGravity(Gravity.CENTER);
   picture.addView(avatar,new FrameLayout.LayoutParams(-1,-1));
   photo=new ImageView(host);photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
   photo.setClipToOutline(true);photo.setVisibility(View.GONE);
   picture.addView(photo,new FrameLayout.LayoutParams(-1,-1));
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
