package com.dacaller.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Searchable, virtualized archive with current, permission-scoped device contact identities. */
public final class ArchiveActivity extends Activity {
 private static final ExecutorService IO=Executors.newSingleThreadExecutor();
 private final Handler ui=new Handler(Looper.getMainLooper());
 private final ArrayList<CallHistory.Entry> calls=new ArrayList<>();
 private List<CallHistory.Entry> allCalls=new ArrayList<>();
 private Map<String,ArchiveContacts.Match> identities=Collections.emptyMap();
 private SharedPreferences prefs;
 private BaseAdapter adapter;
 private TextView subtitle,contactNotice;
 private EditText search;
 private volatile int filterGeneration;
 private boolean loaded,resumedOnce,observing;
 private final Runnable contactReload=()->{ContactPhotos.clearCache();load();};
 private final ContentObserver contactChanges=new ContentObserver(ui){
  @Override public void onChange(boolean selfChange){
   ui.removeCallbacks(contactReload);ui.postDelayed(contactReload,250);
  }
 };
 private int dp(float x){return (int)(getResources().getDisplayMetrics().density*x+0.5f);}
 private boolean dark(){return prefs.getBoolean("dark",false)||prefs.getBoolean("amoled",false);}
 private int surface(){return prefs.getBoolean("amoled",false)?Color.BLACK:dark()?0xFF151515:0xFFF8F8F7;}
 private int card(){return dark()?0xFF232323:Color.WHITE;}
 private int ink(){return dark()?Color.WHITE:0xFF1B1B1B;}
 private int muted(){return dark()?0xFFBBBBBB:0xFF666666;}
 private int gold(){return dark()?0xFFE1C58D:0xFF8D672E;}
 private Locale locale(){return I18n.isEnglish(this)?Locale.ENGLISH:new Locale("he","IL");}
 private String tr(String he,String en){return I18n.isEnglish(this)?en:he;}
 private void applySystemBars(){
  getWindow().setStatusBarColor(surface());getWindow().setNavigationBarColor(surface());
  View decor=getWindow().getDecorView();
  decor.setSystemUiVisibility(dark()?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
  if(android.os.Build.VERSION.SDK_INT>=30){
   android.view.WindowInsetsController controller=getWindow().getInsetsController();
   if(controller!=null){
    int mask=android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|
      android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
    controller.setSystemBarsAppearance(dark()?0:mask,mask);
   }
  }
 }
 private GradientDrawable shape(int color,int radius){GradientDrawable bg=new GradientDrawable();bg.setColor(color);bg.setCornerRadius(dp(radius));return bg;}
 private TextView text(String s,int sp,boolean bold,int color){
  TextView view=new TextView(this);view.setText(I18n.text(this,s));view.setTextColor(color);view.setTextSize(sp);
  view.setGravity(Gravity.CENTER_VERTICAL);view.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));return view;
 }
 @Override public void onCreate(Bundle state){
  super.onCreate(state);prefs=getSharedPreferences("da_caller",MODE_PRIVATE);
  applySystemBars();
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
  root.setLayoutDirection(I18n.isEnglish(this)?View.LAYOUT_DIRECTION_LTR:View.LAYOUT_DIRECTION_RTL);
  root.setBackgroundColor(surface());setContentView(root);
  LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(8),dp(4),dp(8),dp(4));
  root.addView(header,new LinearLayout.LayoutParams(-1,-2));
  TextView back=text(I18n.isEnglish(this)?"‹":"›",32,false,gold());back.setGravity(Gravity.CENTER);
  back.setContentDescription(tr("חזרה","Back"));header.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
  back.setOnClickListener(v->finish());
  TextView title=text("ארכיון שיחות",20,true,ink());header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
  TextView reload=text("↻",25,false,gold());reload.setGravity(Gravity.CENTER);
  reload.setContentDescription(tr("רענון ארכיון","Refresh archive"));header.addView(reload,new LinearLayout.LayoutParams(dp(48),dp(48)));
  reload.setOnClickListener(v->{ContactPhotos.clearCache();load();});
  subtitle=text("שיחות שנשמרו לאחר שנעלמו מיומן המכשיר",12,false,muted());
  subtitle.setPadding(dp(16),0,dp(16),dp(8));root.addView(subtitle,new LinearLayout.LayoutParams(-1,-2));
  contactNotice=text(tr("להצגת שמות ותמונות יש לאפשר גישה לאנשי קשר בהגדרות",
    "Allow contacts access in settings to show names and photos"),12,false,muted());
  contactNotice.setPadding(dp(16),0,dp(16),dp(8));root.addView(contactNotice,new LinearLayout.LayoutParams(-1,-2));
  refreshContactAccess();
  search=new EditText(this);search.setSingleLine(true);search.setTextColor(ink());search.setHintTextColor(muted());
  search.setTextSize(15);search.setHint(tr("חיפוש שם או מספר בארכיון","Search archive by name or number"));
  search.setContentDescription(search.getHint());search.setMinHeight(dp(48));search.setBackground(shape(card(),12));
  search.setPadding(dp(14),dp(6),dp(14),dp(6));
  LinearLayout.LayoutParams searchParams=new LinearLayout.LayoutParams(-1,-2);searchParams.setMargins(dp(12),0,dp(12),dp(8));root.addView(search,searchParams);
  ListView list=new ListView(this);list.setDivider(null);list.setSelector(android.R.color.transparent);
  root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
  adapter=new BaseAdapter(){
   private final DateFormat df=DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT,locale());
   @Override public int getCount(){return calls.size();}
   @Override public Object getItem(int p){return calls.get(p);}
   @Override public long getItemId(int p){return calls.get(p).id;}
   @Override public View getView(int position,View convert,ViewGroup parent){
    ArchiveRow row=convert!=null&&convert.getTag() instanceof ArchiveRow?(ArchiveRow)convert.getTag():new ArchiveRow();
    CallHistory.Entry entry=calls.get(position);
    ArchiveContacts.Match match=identity(entry);
    String number=entry.number.isEmpty()?I18n.text(ArchiveActivity.this,"מספר חסוי"):entry.number;
    String display=match==null?number:match.name;
    String details=String.format(locale(),"%s  ·  %s  ·  %d:%02d",type(entry.type),df.format(new Date(entry.date)),entry.duration/60,entry.duration%60);
    row.entry=entry;long binding=++row.binding;
    row.name.setText(display);row.number.setText(number);row.number.setVisibility(match==null?View.GONE:View.VISIBLE);
    row.description.setText(details);
    row.initial.setText(match==null?"?":initials(match.name));
    row.photo.setImageDrawable(null);row.photo.setVisibility(View.GONE);
    AvatarShapes.apply(row.avatar);
    row.avatar.setOnClickListener(match==null?null:v->openContact(match));
    row.avatar.setContentDescription(match==null?null:tr("כרטיס איש קשר","Contact card")+": "+display);
    row.avatar.setImportantForAccessibility(match==null?View.IMPORTANT_FOR_ACCESSIBILITY_NO:View.IMPORTANT_FOR_ACCESSIBILITY_YES);
    if(match!=null)ContactPhotos.load(ArchiveActivity.this,match.id,image->{
     if(isFinishing()||isDestroyed()||row.entry!=entry||row.binding!=binding||!ArchiveContacts.allowed(ArchiveActivity.this))return;
     row.photo.setImageBitmap(image);row.photo.setVisibility(image==null?View.GONE:View.VISIBLE);
    });
    row.root.setContentDescription(display+(match==null?"":". "+number)+". "+details);
    // Capture immutable identity and entry; recycled view text is never the dial target.
    row.root.setOnClickListener(v->{
     boolean permitted=ArchiveContacts.allowed(ArchiveActivity.this);
     String shown=permitted?display:number;
     AlertDialog.Builder dialog=new AlertDialog.Builder(ArchiveActivity.this).setTitle(shown)
       .setMessage((permitted&&match!=null?number+"\n":"")+details)
       .setNegativeButton(I18n.text(ArchiveActivity.this,"סגור"),null);
     if(permitted&&match!=null)dialog.setNeutralButton(tr("איש קשר","Contact"),(d,which)->openContact(match));
     if(CallLauncher.isCallable(entry.number))dialog.setPositiveButton(I18n.text(ArchiveActivity.this,"חייג"),(d,which)->{
      try{startActivity(new Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",entry.number,null)));}
      catch(RuntimeException unavailable){Toast.makeText(ArchiveActivity.this,I18n.text(ArchiveActivity.this,"לא ניתן להתקשר"),Toast.LENGTH_LONG).show();}
     });
     dialog.show();
    });
    return row.root;
   }
  };
  list.setAdapter(adapter);
  search.addTextChangedListener(new TextWatcher(){
   public void beforeTextChanged(CharSequence s,int start,int count,int after){}
   public void onTextChanged(CharSequence s,int start,int before,int count){if(loaded)filter();}
   public void afterTextChanged(Editable value){}
  });
  if(state!=null)search.setText(state.getString("query",""));
  load();
 }
 private final class ArchiveRow {
  final LinearLayout root=new LinearLayout(ArchiveActivity.this);
  final FrameLayout avatar=AvatarShapes.frame(ArchiveActivity.this);
  final TextView initial=text("?",20,true,gold()),name=text("",16,true,ink()),number=text("",12,false,muted()),description=text("",11,false,muted());
  final ImageView photo=new ImageView(ArchiveActivity.this);
  CallHistory.Entry entry;long binding;
  ArchiveRow(){
   root.setTag(this);root.setGravity(Gravity.CENTER_VERTICAL);
   root.setPadding(dp(12),dp(10),dp(12),dp(10));root.setMinimumHeight(dp(78));root.setBackground(shape(card(),12));
   avatar.setBackgroundColor(dark()?0xFF343029:0xFFF0E6D6);AvatarShapes.apply(avatar);
   LinearLayout.LayoutParams avatarParams=new LinearLayout.LayoutParams(dp(48),dp(48));avatarParams.setMarginEnd(dp(12));root.addView(avatar,avatarParams);
   initial.setGravity(Gravity.CENTER);avatar.addView(initial,new FrameLayout.LayoutParams(-1,-1));
   photo.setScaleType(ImageView.ScaleType.CENTER_CROP);avatar.addView(photo,new FrameLayout.LayoutParams(-1,-1));
   LinearLayout copy=new LinearLayout(ArchiveActivity.this);copy.setOrientation(LinearLayout.VERTICAL);
   root.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
   name.setSingleLine(true);name.setEllipsize(TextUtils.TruncateAt.END);copy.addView(name,new LinearLayout.LayoutParams(-1,-2));
   number.setTextDirection(View.TEXT_DIRECTION_LTR);number.setSingleLine(true);number.setEllipsize(TextUtils.TruncateAt.END);
   copy.addView(number,new LinearLayout.LayoutParams(-1,-2));
   description.setPadding(0,dp(4),0,0);copy.addView(description,new LinearLayout.LayoutParams(-1,-2));
  }
 }
 private String initials(String name){
  String clean=name.trim();return clean.isEmpty()?"?":clean.substring(0,clean.offsetByCodePoints(0,1)).toUpperCase(locale());
 }
 private ArchiveContacts.Match identity(CallHistory.Entry entry){
  return ArchiveContacts.allowed(this)?identities.get(ArchiveContacts.key(entry.number)):null;
 }
 private void openContact(ArchiveContacts.Match match){
  if(!ArchiveContacts.allowed(this))return;
  startActivity(new Intent(this,ContactDetailActivity.class).putExtra("contact_id",match.id));
 }
 private String type(int type){
  if(type==CallLog.Calls.MISSED_TYPE)return I18n.text(this,"לא נענתה");
  if(type==CallLog.Calls.OUTGOING_TYPE)return I18n.text(this,"יוצאת");
  if(type==CallLog.Calls.REJECTED_TYPE)return I18n.text(this,"נדחתה");
  if(type==CallLog.Calls.BLOCKED_TYPE)return I18n.text(this,"נחסמה");
  if(type==CallLog.Calls.VOICEMAIL_TYPE)return tr("תא קולי","Voicemail");
  if(type==CallLog.Calls.ANSWERED_EXTERNALLY_TYPE)return tr("נענתה במכשיר אחר","Answered on another device");
  return I18n.text(this,"נכנסת");
 }
 private void refreshContactAccess(){
  boolean granted=ArchiveContacts.allowed(this);
  if(contactNotice!=null)contactNotice.setVisibility(granted?View.GONE:View.VISIBLE);
  if(!granted){identities=Collections.emptyMap();if(adapter!=null)adapter.notifyDataSetChanged();}
 }
 private void load(){
  if(isFinishing()||isDestroyed())return;
  refreshContactAccess();loaded=false;int request=++filterGeneration;
  subtitle.setText(I18n.text(this,"טוען נתוני ארכיון…"));
  IO.execute(()->{
   List<CallHistory.Entry> snapshot=CallArchive.load(getApplicationContext());boolean failed=CallArchive.lastOperationFailed;
   Map<String,ArchiveContacts.Match> contacts=failed?Collections.emptyMap():ArchiveContacts.resolve(getApplicationContext(),snapshot,()->request!=filterGeneration);
   runOnUiThread(()->{
    if(isFinishing()||isDestroyed()||request!=filterGeneration)return;
    loaded=true;allCalls=snapshot;identities=ArchiveContacts.allowed(this)?contacts:Collections.emptyMap();
    if(failed){subtitle.setText(tr("לא ניתן לטעון את הארכיון. נסה לרענן.","Could not load archive. Try refreshing."));return;}
    filter();
   });
  });
 }
 private void filter(){
  int request=++filterGeneration;String query=search.getText().toString().trim();
  String folded=query.toLowerCase(Locale.ROOT);
  // Name text must not turn into T9 digits and accidentally match another caller.
  String digits=query.codePoints().anyMatch(Character::isDigit)&&query.codePoints().noneMatch(Character::isLetter)
    ?PhoneNumberUtils.normalizeNumber(query):"";
  List<CallHistory.Entry> snapshot=allCalls;
  Map<String,ArchiveContacts.Match> names=ArchiveContacts.allowed(this)?identities:Collections.emptyMap();
  IO.execute(()->{
   ArrayList<CallHistory.Entry> matches=new ArrayList<>();
   for(CallHistory.Entry call:snapshot){
    if(request!=filterGeneration)return;
    ArchiveContacts.Match contact=names.get(ArchiveContacts.key(call.number));
    if(query.isEmpty()||call.number.contains(query)||(contact!=null&&contact.name.toLowerCase(Locale.ROOT).contains(folded))||
       (!digits.isEmpty()&&PhoneNumberUtils.normalizeNumber(call.number).contains(digits)))matches.add(call);
   }
   runOnUiThread(()->{
    if(isFinishing()||isDestroyed()||request!=filterGeneration)return;
    // Revocation while filtering must not retain a name-based search result.
    if(!names.isEmpty()&&!ArchiveContacts.allowed(this)){refreshContactAccess();filter();return;}
    calls.clear();calls.addAll(matches);adapter.notifyDataSetChanged();
    subtitle.setText(matches.isEmpty()?tr("אין שיחות תואמות בארכיון","No matching archived calls"):
      String.format(locale(),tr("%d מתוך %d שיחות בארכיון","%d of %d archived calls"),matches.size(),allCalls.size()));
   });
  });
 }
 @Override protected void onResume(){
  super.onResume();refreshContactAccess();
  // Reapply after the decor attaches: an early legacy visibility flag can be
  // replaced while Android transfers system-bar ownership from the last window.
  getWindow().getDecorView().post(this::applySystemBars);
  if(ArchiveContacts.allowed(this))try{
   getContentResolver().registerContentObserver(ContactsContract.Contacts.CONTENT_URI,true,contactChanges);observing=true;
  }catch(RuntimeException unavailable){/* Refresh on return remains available. */}
  if(resumedOnce){ContactPhotos.clearCache();load();}else resumedOnce=true;
 }
 @Override protected void onPause(){
  ui.removeCallbacks(contactReload);
  if(observing){getContentResolver().unregisterContentObserver(contactChanges);observing=false;}
  super.onPause();
 }
 @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("query",search.getText().toString());}
 @Override protected void onDestroy(){filterGeneration++;ui.removeCallbacks(contactReload);super.onDestroy();}
}
