package com.dacaller.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** One bounded result page, separate from the private contacts adapter and its saved state. */
final class BusinessDirectoryView implements AutoCloseable {
 interface Loader {BusinessDirectory.Page load(Context context,String query,int offset);}
 private static final Executor IO=Executors.newSingleThreadExecutor();
 private static final String QUERY="business_query",PAGE="business_offset",
   POSITION="business_position",TOP="business_top",ID="business_id";
 private final Activity host;private final Runnable personal;private final Consumer<String> call;
 private final Loader loader;private final Executor io;private final SharedPreferences prefs;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private String query="",loadedQuery="";private int offset,position,top,generation;private long firstId=-1;
 private boolean closed,active,loading,touching,scrolling;
 private EditText searchField;
 private ResultsList list;private TextView status,empty,previous,next,details;
 private DirectoryAdapter adapter;private BusinessDirectory.Page page;
 private Runnable filter;private AlertDialog dialog;
 BusinessDirectoryView(Activity host,Runnable personal,Consumer<String> call){this(host,personal,call,BusinessDirectory::search,IO);}
 BusinessDirectoryView(Activity host,Runnable personal,Consumer<String> call,Loader loader,Executor executor){
  this.host=host;this.personal=personal;this.call=call;this.loader=loader;this.io=executor;
  prefs=host.getSharedPreferences("da_caller",Context.MODE_PRIVATE);
 }
 private boolean english(){return I18n.isEnglish(host);}
 private boolean dark(){return prefs.getBoolean("dark",false)||prefs.getBoolean("amoled",false);}
 private String tr(String he,String en){return english()?en:he;}
 private int dp(float value){return Math.round(host.getResources().getDisplayMetrics().density*value);}
 private int background(){return prefs.getBoolean("amoled",false)?Color.BLACK:dark()?0xFF131313:0xFFF5F5F4;}
 private int surface(){return prefs.getBoolean("amoled",false)?0xFF090909:dark()?0xFF1D1D1D:Color.WHITE;}
 private int ink(){return dark()?0xFFF7F7F7:0xFF181818;}
 private int muted(){return dark()?0xFFB5BBB6:0xFF646D66;}
 private int gold(){return dark()?0xFFE5C987:0xFF805B27;}
 private GradientDrawable shape(int color,int radius){GradientDrawable value=new GradientDrawable();value.setColor(color);value.setCornerRadius(dp(radius));return value;}
 private TextView text(String value,int size,boolean bold,int color){
  TextView view=new TextView(host);view.setText(value);view.setTextSize(size);view.setTextColor(color);
  view.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);view.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
  view.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));return view;
 }
 private LinearLayout vertical(){LinearLayout view=new LinearLayout(host);view.setOrientation(LinearLayout.VERTICAL);return view;}
 private LinearLayout horizontal(){LinearLayout view=new LinearLayout(host);view.setGravity(Gravity.CENTER_VERTICAL);return view;}
 private void action(View view,Runnable callback){
  TouchFeedback.bind(view,prefs);view.setFocusable(true);
  view.setOnClickListener(v->{if(closed||!active||host.isFinishing()||host.isDestroyed())return;TouchFeedback.pulse(host,prefs,v);callback.run();});
 }
 private TextView button(String label,Runnable callback){
  TextView view=text(label,12,true,gold());view.setGravity(Gravity.CENTER);view.setMaxLines(1);view.setEllipsize(TextUtils.TruncateAt.END);
  view.setMinimumHeight(dp(48));view.setPadding(dp(8),0,dp(8),0);action(view,callback);return view;
 }
 View create(){
  if(closed)throw new IllegalStateException("Closed business directory view");
  remember();generation++;cancelFilter();dismissDialog();touching=false;scrolling=false;active=true;
  LinearLayout root=vertical();root.setTag("business_directory_root");
  root.setLayoutDirection(english()?View.LAYOUT_DIRECTION_LTR:View.LAYOUT_DIRECTION_RTL);
  root.setFocusableInTouchMode(true);root.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
  root.setBackgroundColor(background());root.setPadding(dp(12),dp(4),dp(12),0);
  LinearLayout tabs=horizontal();tabs.setTag("business_directory_tabs");root.addView(tabs,new LinearLayout.LayoutParams(-1,dp(48)));
  TextView mine=button(tr("אנשי הקשר שלי","My contacts"),()->{remember();personal.run();});mine.setTag("business_directory_personal_tab");
  tabs.addView(mine,new LinearLayout.LayoutParams(0,-1,1));
  TextView businesses=button(tr("בתי עסק","Businesses"),()->{});businesses.setTag("business_directory_business_tab");businesses.setSelected(true);
  businesses.setBackground(shape(dark()?0xFF29251D:0xFFF0E8D9,13));businesses.setClickable(false);
  if(Build.VERSION.SDK_INT>=30)businesses.setStateDescription(tr("נבחר","Selected"));
  tabs.addView(businesses,new LinearLayout.LayoutParams(0,-1,1));
  EditText search=new EditText(host);searchField=search;search.setTag("business_directory_search");search.setSingleLine(true);
  search.setHint(tr("חיפוש עסק, יישוב או מספר","Search business, city or number"));search.setTextSize(14);
  search.setTextColor(ink());search.setHintTextColor(muted());search.setPadding(dp(12),0,dp(12),0);
  search.setBackground(shape(surface(),13));search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
  search.setFilters(new InputFilter[]{new InputFilter.LengthFilter(BusinessDirectory.MAX_QUERY_LENGTH)});search.setText(query);
  root.addView(search,new LinearLayout.LayoutParams(-1,dp(48)));
  status=text(tr("טוען את מאגר העסקים…","Loading business directory…"),10,false,muted());status.setTag("business_directory_status");
  status.setSingleLine(true);status.setEllipsize(TextUtils.TruncateAt.END);status.setPadding(dp(4),dp(2),dp(4),dp(2));
  root.addView(status,new LinearLayout.LayoutParams(-1,dp(25)));
  list=new ResultsList();ListView current=list;list.setTag("call-island-page-list");
  list.setDivider(null);list.setSelector(android.R.color.transparent);list.setItemsCanFocus(true);list.setCacheColorHint(Color.TRANSPARENT);
  list.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_INSET);
  empty=text("",14,false,muted());empty.setTag("business_directory_empty");empty.setPadding(dp(16),dp(22),dp(16),dp(22));empty.setGravity(Gravity.CENTER);empty.setMinimumHeight(dp(90));
  list.addHeaderView(empty,null,false);
  LinearLayout footer=horizontal();footer.setTag("business_directory_pagination");
  previous=button(tr("הקודם","Previous"),()->changePage(Math.max(0,offset-BusinessDirectory.PAGE_SIZE)));previous.setTag("business_directory_previous");
  details=button(tr("פרטי המאגר","About data"),this::showDataset);details.setTag("business_directory_about");
  next=button(tr("הבא","Next"),()->changePage(offset+BusinessDirectory.PAGE_SIZE));next.setTag("business_directory_next");
  footer.addView(previous,new LinearLayout.LayoutParams(0,dp(48),1));footer.addView(details,new LinearLayout.LayoutParams(0,dp(48),1));footer.addView(next,new LinearLayout.LayoutParams(0,dp(48),1));
  list.addFooterView(footer,null,false);adapter=new DirectoryAdapter();list.setAdapter(adapter);
  list.setOnScrollListener(new AbsListView.OnScrollListener(){
   public void onScrollStateChanged(AbsListView view,int state){if(list==current)scrolling=state!=SCROLL_STATE_IDLE;}
   public void onScroll(AbsListView view,int first,int count,int total){}
  });
  root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
  search.addTextChangedListener(new TextWatcher(){
   public void beforeTextChanged(CharSequence text,int start,int count,int after){}
   public void afterTextChanged(Editable text){}
   public void onTextChanged(CharSequence text,int start,int before,int count){
    if(!active||searchField!=search)return;
    query=text.toString();offset=0;position=0;top=0;firstId=-1;generation++;cancelFilter();loading=true;adapter.notifyDataSetChanged();
    filter=()->{filter=null;loadPage();};handler.postDelayed(filter,150);
   }
  });
  search.setOnEditorActionListener((v,actionId,event)->{
   if(!active||searchField!=search||actionId!=EditorInfo.IME_ACTION_SEARCH)return false;cancelFilter();loadPage();return true;
  });
  if(page!=null&&page.offset==offset&&query.equals(loadedQuery)){loading=false;bindPage(page,true);}
  else loadPage();
  return root;
 }
 private void cancelFilter(){if(filter!=null){handler.removeCallbacks(filter);filter=null;}}
 private void changePage(int nextOffset){
  if(loading)return;offset=nextOffset;position=0;top=0;firstId=-1;loadPage();
 }
 private void loadPage(){
  if(closed||!active||list==null)return;cancelFilter();final int ticket=++generation,requestedOffset=offset;
  final String requested=query;final ListView current=list;loading=true;
  status.setText(tr("טוען…","Loading…"));previous.setEnabled(false);next.setEnabled(false);adapter.notifyDataSetChanged();
  io.execute(()->{
   BusinessDirectory.Page result;
   try{result=loader.load(host.getApplicationContext(),requested,requestedOffset);}
   catch(RuntimeException unavailable){result=BusinessDirectory.Page.unavailable(BusinessDirectory.Status.UNAVAILABLE);}
   final BusinessDirectory.Page complete=result;
   handler.post(new Runnable(){public void run(){
    if(closed||!active||ticket!=generation||list!=current||host.isFinishing()||host.isDestroyed())return;
    if(isInteracting()){handler.postDelayed(this,80);return;}
    page=complete;loadedQuery=requested;loading=false;bindPage(complete,true);
   }});
  });
 }
 private void bindPage(BusinessDirectory.Page result,boolean restore){
  adapter.values=result.entries;adapter.boundQuery=loadedQuery;adapter.notifyDataSetChanged();
  boolean available=result.status==BusinessDirectory.Status.AVAILABLE;
  previous.setEnabled(available&&offset>0);next.setEnabled(available&&result.hasMore);details.setEnabled(available&&result.manifest!=null);
  previous.setAlpha(previous.isEnabled()?1f:.35f);next.setAlpha(next.isEnabled()?1f:.35f);
  empty.setVisibility(result.entries.isEmpty()?View.VISIBLE:View.GONE);
  if(!available){
   status.setText(tr("מאגר עסקים מקומי אינו זמין","Local business directory unavailable"));
   empty.setText(result.status==BusinessDirectory.Status.MISSING?
    tr("מאגר העסקים עדיין לא צורף לגרסה הזו. אנשי הקשר שלך נשארים בלשונית הנפרדת.","A business dataset has not been included in this build. Your contacts stay in their separate tab."):
    tr("לא ניתן לקרוא את מאגר העסקים המקומי. אפשר להמשיך להשתמש באנשי הקשר שלך.","The local business directory cannot be read. Your personal contacts remain available."));
  }else{
   BusinessDirectory.Manifest metadata=result.manifest;
   String source=android.text.BidiFormatter.getInstance(!english()).unicodeWrap(metadata.sourceName);
   status.setText(tr("מקור: ","Source: ")+source+" · "+NumberFormat.getIntegerInstance(english()?Locale.ENGLISH:new Locale("he","IL")).format(metadata.recordCount));
   empty.setText(query.trim().isEmpty()?tr("אין רשומות במאגר הזה.","This dataset contains no records."):tr("לא נמצאו עסקים לחיפוש הזה.","No businesses match this search."));
  }
  if(restore){
   int target=position;
   if(firstId>0)for(int n=0;n<result.entries.size();n++)if(result.entries.get(n).id==firstId){target=n+list.getHeaderViewsCount();break;}
   final int selected=Math.max(0,Math.min(target,result.entries.size()+list.getHeaderViewsCount()-1));
   final int savedTop=top,ticket=generation;ResultsList current=list;current.restorePending=true;
   current.post(()->{
    if(closed||!active||ticket!=generation||list!=current||isInteracting()){current.restorePending=false;return;}
    current.restoreRequested=true;current.setSelectionFromTop(selected,savedTop);
   });
  }
 }
 void remember(){
  if(!active||list==null||list.restorePending||loading||!query.equals(loadedQuery)||list.getChildCount()==0)return;
  position=list.getFirstVisiblePosition();top=list.getChildAt(0).getTop();
  int data=position-list.getHeaderViewsCount();firstId=page!=null&&data>=0&&data<page.entries.size()?page.entries.get(data).id:-1;
 }
 boolean isInteracting(){return active&&(touching||scrolling);}
 void saveState(Bundle state){remember();state.putString(QUERY,query);state.putInt(PAGE,offset);state.putInt(POSITION,position);state.putInt(TOP,top);state.putLong(ID,firstId);}
 void restoreState(Bundle state){
  if(state==null)return;detach();String restored=state.getString(QUERY,"");if(restored==null)restored="";query=restored.substring(0,Math.min(restored.length(),BusinessDirectory.MAX_QUERY_LENGTH));
  offset=Math.max(0,Math.min(2_000_000,state.getInt(PAGE,0)));position=Math.max(0,Math.min(BusinessDirectory.PAGE_SIZE,state.getInt(POSITION,0)));top=state.getInt(TOP,0);firstId=state.getLong(ID,-1);
 }
 void detach(){remember();active=false;generation++;cancelFilter();dismissDialog();touching=false;scrolling=false;}
 @Override public void close(){detach();closed=true;}
 private void dismissDialog(){if(dialog!=null){dialog.dismiss();dialog=null;}}
 private void showDataset(){
  if(page==null||page.manifest==null)return;BusinessDirectory.Manifest data=page.manifest;
  String content=tr("רשומות במאגר: ","Dataset records: ")+NumberFormat.getIntegerInstance().format(data.recordCount)+"\n"+
   tr("עדכון המקור: ","Source snapshot: ")+data.updatedAt+"\n\n"+data.sourceName+"\n"+data.sourceUrl+"\n\n"+data.license+"\n"+data.licenseUrl+"\n\n"+
   tr("פרטי עסקים ממקורות פתוחים. אין כאן אימות שבעל העסק עדיין משתמש במספר, והמאגר אינו כולל את כל בתי העסק בישראל. אנשי הקשר הפרטיים שלך אינם חלק מהמאגר.",
     "Public business records from open sources. Phone ownership/current use is not verified, and this dataset does not cover every Israeli business. Your private contacts are not part of this dataset.");
  dialog=new AlertDialog.Builder(host).setCustomTitle(dialogTitle(tr("על מאגר העסקים","About business data"))).setView(dialogText(content))
   .setPositiveButton(tr("סגור","Close"),null).setNeutralButton(tr("מקור הנתונים","Data source"),(d,w)->openSource(data.sourceUrl)).create();showDialog();
 }
 private View dialogTitle(String value){TextView title=text(value,19,true,ink());title.setTag("business_directory_detail_title");title.setBackgroundColor(surface());title.setPadding(dp(22),dp(18),dp(22),dp(6));title.setLayoutDirection(english()?View.LAYOUT_DIRECTION_LTR:View.LAYOUT_DIRECTION_RTL);return title;}
 private View dialogText(String value){ScrollView scroll=new ScrollView(host);scroll.setTag("business_directory_detail_body");scroll.setBackgroundColor(surface());TextView copy=text(value,14,false,ink());copy.setTag("business_directory_detail_text");copy.setLayoutDirection(english()?View.LAYOUT_DIRECTION_LTR:View.LAYOUT_DIRECTION_RTL);copy.setTextIsSelectable(true);copy.setPadding(dp(20),dp(12),dp(20),dp(12));scroll.addView(copy);return scroll;}
 private void showDialog(){
  dialog.show();if(dialog.getWindow()!=null)dialog.getWindow().setBackgroundDrawable(shape(surface(),22));
  for(int which:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL})if(dialog.getButton(which)!=null)dialog.getButton(which).setTextColor(gold());
 }
 private void showEntry(BusinessDirectory.Entry entry){
  if(closed||!active||loading||host.isFinishing()||host.isDestroyed())return;
  String phone=android.text.BidiFormatter.getInstance(!english()).unicodeWrap(entry.phoneDisplay,android.text.TextDirectionHeuristics.LTR);
  String content=phone+optional(entry.city)+optional(entry.address)+optional(entry.category)+"\n\n"+
   tr("מקור: ","Source: ")+entry.sourceUrl+"\n"+entry.sourceLicense+"\n"+tr("תאריך מקור: ","Source date: ")+entry.sourceUpdatedAt+"\n\n"+
   tr("רשומה ציבורית; המספר עשוי להשתנות. אין שינוי באנשי הקשר הפרטיים שלך.","A public record; the number may change. Your private contacts are unchanged.");
  dialog=new AlertDialog.Builder(host).setCustomTitle(dialogTitle(entry.name)).setView(dialogText(content))
   .setPositiveButton(tr("חיוג","Call"),(d,w)->{if(!closed&&active&&!loading)call.accept(entry.phone);})
   .setNegativeButton(tr("סגור","Close"),null).setNeutralButton(tr("המקור","Source"),(d,w)->openSource(entry.sourceUrl)).create();showDialog();
 }
 private String optional(String value){return value==null||value.isEmpty()?"":"\n"+value;}
 private void openSource(String source){
  if(closed||!active||host.isFinishing()||host.isDestroyed()||!BusinessDirectory.https(source))return;
  try{host.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(source)));}
  catch(RuntimeException unavailable){Toast.makeText(host,tr("אין דפדפן זמין לפתיחת המקור","No browser is available to open the source"),Toast.LENGTH_LONG).show();}
 }
 private final class ResultsList extends ListView {
  boolean restorePending,restoreRequested;
  ResultsList(){super(host);}
  @Override public boolean dispatchTouchEvent(MotionEvent event){
   int action=event.getActionMasked();
   if(this==list&&active&&action==MotionEvent.ACTION_DOWN)touching=true;
   // Observe the gesture while the platform ListView/child controls retain
   // their native click and accessibility dispatch; never generate a second click.
   try{return super.dispatchTouchEvent(event);}
   finally{if(this==list&&(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL))touching=false;}
  }
  @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){
   super.onLayout(changed,left,top,right,bottom);
   if(restoreRequested){restoreRequested=false;restorePending=false;}
  }
 }
 private final class DirectoryAdapter extends BaseAdapter {
  List<BusinessDirectory.Entry> values=Collections.emptyList();String boundQuery="";
  public int getCount(){return values.size();}public BusinessDirectory.Entry getItem(int position){return values.get(position);}
  public long getItemId(int position){return getItem(position).id;}public boolean hasStableIds(){return true;}
  public View getView(int position,View convert,ViewGroup parent){
   Row row=convert!=null&&convert.getTag() instanceof Row?(Row)convert.getTag():new Row();BusinessDirectory.Entry entry=getItem(position);
   row.name.setText(entry.name);row.name.setTag("business_directory_name_"+entry.id);row.phone.setText(entry.phoneDisplay);row.phone.setTag("business_directory_phone_"+entry.id);row.call.setTag("business_directory_call_"+entry.id);String extra=entry.city+(entry.city.isEmpty()||entry.category.isEmpty()?"":" · ")+entry.category;
   row.extra.setText(extra);row.extra.setVisibility(extra.isEmpty()?View.GONE:View.VISIBLE);
   String initial=entry.name.isEmpty()?"•":entry.name.substring(0,Character.charCount(entry.name.codePointAt(0)));row.mark.setText(initial);
   row.root.setContentDescription(entry.name+". "+entry.phoneDisplay+". "+extra);
   row.call.setContentDescription(tr("חיוג אל ","Call ")+entry.name+" "+entry.phoneDisplay);
   final String rowQuery=boundQuery;final int rowGeneration=generation;
   boolean enabled=!loading&&rowQuery.equals(query);row.call.setEnabled(enabled);row.root.setAlpha(enabled?1f:.55f);
   action(row.root,()->{if(!loading&&rowGeneration==generation&&rowQuery.equals(query))showEntry(entry);});
   action(row.call,()->{if(!loading&&rowGeneration==generation&&rowQuery.equals(query))call.accept(entry.phone);});
   return row.root;
  }
 }
 private final class Row {
  final LinearLayout root=horizontal();final TextView name=text("",14,true,ink()),phone=text("",13,false,gold()),extra=text("",10,false,muted()),mark=text("",19,true,0xFFE9CD90);
  final ImageView call=new ImageView(host);
  Row(){
   root.setTag(this);root.setPadding(dp(6),dp(7),dp(4),dp(7));root.setMinimumHeight(dp(76));root.setBackground(shape(surface(),14));
   FrameLayout avatar=AvatarShapes.frame(host);avatar.setBackgroundColor(0xFF173D31);mark.setGravity(Gravity.CENTER);avatar.addView(mark,new FrameLayout.LayoutParams(-1,-1));
   avatar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);root.addView(avatar,new LinearLayout.LayoutParams(dp(38),dp(38)));
   LinearLayout labels=vertical();labels.setPadding(dp(9),0,dp(5),0);name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);
   phone.setSingleLine(true);phone.setEllipsize(TextUtils.TruncateAt.END);phone.setTextDirection(View.TEXT_DIRECTION_LTR);
   extra.setSingleLine(true);extra.setEllipsize(TextUtils.TruncateAt.END);labels.addView(name);labels.addView(phone);labels.addView(extra);
   root.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
   call.setImageResource(android.R.drawable.sym_action_call);call.setImageTintList(android.content.res.ColorStateList.valueOf(gold()));call.setPadding(dp(12),dp(12),dp(12),dp(12));
   root.addView(call,new LinearLayout.LayoutParams(dp(48),dp(48)));
  }
 }
}
