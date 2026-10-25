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
import android.widget.HorizontalScrollView;
import android.util.SparseArray;
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
 private FavoriteStrip liveFavoriteStrip;
 private int favoriteScroll;
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
  return (liveRecents!=null&&liveRecents.touching)||(liveContacts!=null&&liveContacts.touching)||
    (liveFavoriteStrip!=null&&liveFavoriteStrip.touching);
 }
 void remember(String tab){
  if(tab.equals("contacts")&&contactQueryChanged)return;
  SwipeListView view=tab.equals("recents")?liveRecents:
    tab.equals("contacts")?liveContacts:null;
  // A freshly rebuilt list can still show its temporary first row until the
  // queued selection has completed a layout. Never replace the saved ID with
  // that transient top row when another provider/navigation refresh arrives.
  if(view==null||view.restorePending||view.getChildCount()==0)return;
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
  state.putInt("favorite_scroll",favoriteScroll);
 }
 void restoreState(Bundle state){
  if(state==null)return;
  recentPos=state.getInt("recent_position");recentOffset=state.getInt("recent_offset");
  contactPos=state.getInt("contact_position");contactOffset=state.getInt("contact_offset");
  recentId=state.getLong("recent_id",-1);contactId=state.getLong("contact_id",-1);
  contactQuery=state.getString("contact_query","");period=Math.max(0,Math.min(3,state.getInt("stats_period")));
  favoriteScroll=Math.max(0,state.getInt("favorite_scroll"));
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
  if(adapter==null||adapter.getCount()==0)return; // Retain the anchor while data loads.
  int finalPos=Math.max(0,Math.min(pos,max));
  // Do not capture this list's default top position until selection is laid out.
  // Two render callbacks in the same frame must retain the original contact ID.
  int revision=view.touchRevision,restore=++view.restoreRevision;
  view.restorePending=true;
  view.post(()->{
   SwipeListView current=tab.equals("recents")?liveRecents:liveContacts;
   if(current!=view||view.touchRevision!=revision||view.touching||view.restoreRevision!=restore){
    if(view.restoreRevision==restore)view.restorePending=false;
    return;
   }
   view.restoreRequested=true;
   view.setSelectionFromTop(finalPos,offset);
  });
 }
 interface NumberAt {String phone(int position);}
 private final class SwipeListView extends ListView {
  private NumberAt numbers;
  private float downX,downY;
  private int touched=-1;
  private String downNumber;
  private boolean sliding,vertical,actionRegion;
  private int touchRevision,restoreRevision;
  private boolean touching,fastScrollPaused,restorePending,restoreRequested;
  private final int slop=ViewConfiguration.get(host).getScaledTouchSlop();
  SwipeListView(){super(host);setItemsCanFocus(true);}
  void setNumberAt(NumberAt source){numbers=source;}
  private void cancelRestore(){
   restoreRevision++;restorePending=false;restoreRequested=false;
  }
  @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){
   super.onLayout(changed,left,top,right,bottom);
   if(restoreRequested){restoreRequested=false;restorePending=false;}
  }
  @Override public boolean performAccessibilityAction(int action,Bundle arguments){
   // Accessibility scrolling need not generate a touch-down event.
   if(action==android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD||
      action==android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)cancelRestore();
   return super.performAccessibilityAction(action,arguments);
  }
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
    touchRevision++;touching=true;cancelRestore();
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
  addFavorites(page,host.contactsSnapshot());
  if(prefs.getBoolean("show_stats",true))addSummary(page);
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
  page.setFocusableInTouchMode(true);page.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
  page.setPadding(dp(12),dp(7),dp(12),0);
  if(favorites)prefs.edit().putBoolean("favorites_expanded",true).apply(); // Legacy deep link.
  addFavorites(page,contacts);
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
    // A render during the debounce must use the new query's top, not remember
    // the old query's visible ID. Old detached search callbacks cannot restore.
    contactPos=0;contactOffset=0;contactId=-1;contactQueryChanged=true;
    list.cancelRestore();
    if(filterTask!=null)handler.removeCallbacks(filterTask);
    filterTask=()->{
     if(liveContacts!=list||!query.equals(contactQuery))return;
     adapter.filter(query);contactQueryChanged=false;
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
   avatar=AvatarShapes.frame(host);
   avatar.setBackground(bg(soft(),16));AvatarShapes.apply(avatar);
   LinearLayout.LayoutParams avatarParams=lp(52,52);
   avatarParams.setMargins(dp(2),0,dp(5),0);
   root.addView(avatar,avatarParams);
   initials=text("?",20,tint(),true);initials.setGravity(Gravity.CENTER);
   avatar.addView(initials,new FrameLayout.LayoutParams(-1,-1));
   photo=new ImageView(host);photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
   photo.setVisibility(View.GONE);
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
   avatar=AvatarShapes.frame(host);avatar.setBackground(bg(soft(),17));AvatarShapes.apply(avatar);
   root.addView(avatar,lp(49,49));
   letter=text("",19,tint(),true);letter.setGravity(Gravity.CENTER);
   avatar.addView(letter,new FrameLayout.LayoutParams(-1,-1));
   photo=new ImageView(host);photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
   photo.setVisibility(View.GONE);
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
 private void addSummary(LinearLayout page){
  CallHistory.Summary data=CallHistory.summary;
  boolean expanded=prefs.getBoolean("stats_expanded",false);
  String[] periods={tr("היום"),tr("השבוע"),tr("החודש"),tr("השנה")};
  LinearLayout summary=vertical();summary.setTag("stats_summary");
  summary.setPadding(dp(10),0,dp(3),expanded?dp(5):0);summary.setBackground(edge(panel(),14));
  LinearLayout.LayoutParams block=lp(-1,-2);block.bottomMargin=dp(6);page.addView(summary,block);
  LinearLayout header=horizontal();header.setMinimumHeight(dp(48));summary.addView(header,lp(-1,-2));
  String compact=periods[period]+" · "+data.calls[period]+" "+tr("שיחות")+" · "+durationText(data.seconds[period]);
  TextView selector=text(compact,12,ink(),true);selector.setTag("stats_period");
  selector.setSingleLine(!expanded);selector.setMaxLines(expanded?2:1);selector.setEllipsize(android.text.TextUtils.TruncateAt.END);
  selector.setMinimumHeight(dp(48));selector.setContentDescription(tr("סיכום פעילות")+". "+compact+". "+tr("תקופת הסיכום"));
  header.addView(selector,new LinearLayout.LayoutParams(0,-2,1));touch(selector);
  selector.setOnClickListener(v->{pulse(selector);period=(period+1)%4;host.refreshPage();});
  TextView toggle=text(expanded?"⌃":"⌄",24,tint(),true);toggle.setTag("stats_toggle");toggle.setGravity(Gravity.CENTER);
  toggle.setContentDescription(I18n.isEnglish(host)?(expanded?"Collapse call summary":"Expand call summary"):(expanded?"הקטנת סיכום שיחות":"הרחבת סיכום שיחות"));
  header.addView(toggle,lp(48,48));touch(toggle);
  toggle.setOnClickListener(v->{pulse(toggle);prefs.edit().putBoolean("stats_expanded",!expanded).apply();host.refreshPage();});
  if(!expanded)return;
  LinearLayout breakdown=horizontal();summary.addView(breakdown,lp(-1,42));
  summaryMetric(breakdown,tr("יוצאות"),"↗",data.outgoing[period],0xFF2375B4);
  summaryMetric(breakdown,tr("נכנסות"),"↙",data.incoming[period],0xFF25815F);
  summaryMetric(breakdown,tr("שלא נענו"),"×",data.missed[period],0xFFBD4748);
  int answered=data.outgoing[period]+data.incoming[period];
  TextView average=text(tr("משך שיחה ממוצע")+": "+durationText(answered>0?data.seconds[period]/answered:0),11,subtle(),false);
  average.setPadding(dp(4),dp(6),dp(4),dp(3));summary.addView(average,lp(-1,-2));
 }
 private void addFavorites(LinearLayout page,List<Contacts.Person> contacts){
  boolean expanded=prefs.getBoolean("favorites_expanded",true);
  Set<String> starred=new HashSet<>(prefs.getStringSet("favorites",java.util.Collections.emptySet()));
  ArrayList<Contacts.Person> chosen=new ArrayList<>();Set<String> added=new HashSet<>();
  for(Contacts.Person person:contacts)if(!person.phone.isEmpty()&&starred.contains(person.key)&&added.add(person.key))chosen.add(person);
  LinearLayout band=horizontal();band.setTag("favorites_strip");band.setBackground(edge(panel(),14));
  LinearLayout.LayoutParams block=lp(-1,expanded&&!chosen.isEmpty()?82:48);block.bottomMargin=dp(6);page.addView(band,block);
  TextView toggle=text(tr("מועדפים")+" ★\n"+chosen.size()+"  "+(expanded?"⌃":"⌄"),11,tint(),true);
  toggle.setTag("favorites_toggle");toggle.setGravity(Gravity.CENTER);toggle.setPadding(dp(3),0,dp(3),0);
  toggle.setContentDescription((I18n.isEnglish(host)?(expanded?"Collapse favorites":"Expand favorites"):(expanded?"הקטנת מועדפים":"הרחבת מועדפים"))+" · "+chosen.size());
  band.addView(toggle,lp(expanded&&!chosen.isEmpty()?68:96,-1));touch(toggle);
  toggle.setOnClickListener(v->{pulse(toggle);prefs.edit().putBoolean("favorites_expanded",!expanded).apply();host.refreshPage();});
  liveFavoriteStrip=null;
  if(expanded&&!chosen.isEmpty()){
   FavoriteStrip strip=new FavoriteStrip(chosen);strip.setTag("favorites_scroll");liveFavoriteStrip=strip;
   band.addView(strip,new LinearLayout.LayoutParams(0,-1,1));
  }else{
   TextView hint=text(chosen.isEmpty()?(I18n.isEnglish(host)?"Star contacts to add favorites":"סמן ★ באנשי קשר"):
     (I18n.isEnglish(host)?"Tap to expand":"הקש להרחבה"),11,subtle(),false);
   hint.setSingleLine(true);hint.setEllipsize(android.text.TextUtils.TruncateAt.END);hint.setPadding(dp(8),0,dp(8),0);
   band.addView(hint,new LinearLayout.LayoutParams(0,-1,1));
   if(!chosen.isEmpty()){
    touch(hint);hint.setOnClickListener(v->{pulse(hint);prefs.edit().putBoolean("favorites_expanded",true).apply();host.refreshPage();});
   }
  }
 }
 /** Only visible favorite tiles exist; even thousands of favorites keep a small view tree. */
 private final class FavoriteStrip extends HorizontalScrollView {
  final ArrayList<Contacts.Person> people;
  final FrameLayout track;
  final SparseArray<FavoriteTile> tiles=new SparseArray<>();
  final int cell=dp(76);
  final boolean rtl=!I18n.isEnglish(host);
  boolean touching,initialized;
  FavoriteStrip(ArrayList<Contacts.Person> chosen){
   super(host);people=chosen;setLayoutDirection(View.LAYOUT_DIRECTION_LTR);setHorizontalScrollBarEnabled(false);
   setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);setContentDescription(I18n.isEnglish(host)?"Favorites. Swipe horizontally for all contacts":"מועדפים. החלק ימינה ושמאלה לכל אנשי הקשר");
   track=new FrameLayout(host);track.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
   // HorizontalScrollView measures its child with UNSPECIFIED width; reserve the
   // whole virtual track before the first visible tile is created.
   track.setMinimumWidth(cell*chosen.size());
   addView(track,new HorizontalScrollView.LayoutParams(cell*chosen.size(),-1));
  }
  @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
   super.onSizeChanged(w,h,oldw,oldh);
   post(()->{
    if(liveFavoriteStrip!=this||getWidth()==0)return;
    int max=Math.max(0,cell*people.size()-getWidth());
    scrollTo(rtl?Math.max(0,max-favoriteScroll):Math.min(max,favoriteScroll),0);
    initialized=true;populate();
   });
  }
  @Override protected void onScrollChanged(int x,int y,int oldx,int oldy){
   super.onScrollChanged(x,y,oldx,oldy);
   if(initialized&&liveFavoriteStrip==this){favoriteScroll=rtl?Math.max(0,cell*people.size()-getWidth()-x):x;populate();}
  }
  @Override public boolean dispatchTouchEvent(MotionEvent event){
   if(event.getActionMasked()==MotionEvent.ACTION_DOWN)touching=true;
   boolean handled=super.dispatchTouchEvent(event);
   if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL)touching=false;
   return handled;
  }
  private void populate(){
   if(getWidth()==0)return;
   int first=Math.max(0,getScrollX()/cell-1),last=Math.min(people.size()-1,(getScrollX()+getWidth())/cell+1);
   for(int at=tiles.size()-1;at>=0;at--){int index=tiles.keyAt(at);if(index<first||index>last){track.removeView(tiles.valueAt(at).root);tiles.removeAt(at);}}
   for(int index=first;index<=last;index++)if(tiles.get(index)==null){
    Contacts.Person person=people.get(rtl?people.size()-1-index:index);FavoriteTile tile=new FavoriteTile(person);tiles.put(index,tile);
    FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(cell,ViewGroup.LayoutParams.MATCH_PARENT);params.leftMargin=index*cell;
    track.addView(tile.root,params);
   }
  }
 }
 private final class FavoriteTile {
  final LinearLayout root;
  FavoriteTile(Contacts.Person person){
   root=vertical();root.setTag("favorite_tile:"+person.id);root.setGravity(Gravity.CENTER);root.setPadding(dp(3),dp(4),dp(3),dp(3));
   FrameLayout picture=AvatarShapes.frame(host);picture.setBackground(bg(soft(),24));AvatarShapes.apply(picture);
   root.addView(picture,lp(40,40));
   TextView initials=text(person.name.isEmpty()?"?":person.name.substring(0,1),19,tint(),true);initials.setGravity(Gravity.CENTER);
   picture.addView(initials,new FrameLayout.LayoutParams(-1,-1));
   ImageView photo=new ImageView(host);photo.setScaleType(ImageView.ScaleType.CENTER_CROP);photo.setVisibility(View.GONE);
   picture.addView(photo,new FrameLayout.LayoutParams(-1,-1));
   ContactPhotos.load(host,person.id,image->{if(image!=null){photo.setImageBitmap(image);photo.setVisibility(View.VISIBLE);}});
   TextView name=text(person.name,11,ink(),true);name.setGravity(Gravity.CENTER);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
   root.addView(name,lp(-1,27));
   root.setContentDescription(tr("התקשר")+" "+person.name+" "+person.phone);touch(root);
   root.setOnClickListener(v->{pulse(root);host.dialFromList(person.phone);});
   root.setOnLongClickListener(v->{pulse(root);host.openContactDetail(person.id);return true;});
  }
 }
}
