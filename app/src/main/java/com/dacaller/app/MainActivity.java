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
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.CallLog;
import android.telecom.TelecomManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.content.res.ColorStateList;
import android.widget.FrameLayout;
import android.provider.ContactsContract;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {
 private final Handler handler=new Handler(Looper.getMainLooper());
 private SharedPreferences prefs;
 private LinearLayout root,nav;
 private FrameLayout workspace;
 private FastPages fastPages;
 private String page="recents";
 private List<Contacts.Person> contacts=Collections.emptyList();
 private boolean loadingContacts=false;
 private boolean contactsLoaded=false;
 private static final java.util.concurrent.ExecutorService CONTACTS_IO=java.util.concurrent.Executors.newSingleThreadExecutor();
 private String dialpadNumber=null;
 private String requestedCallNumber=null;
 private String pendingRoleCallNumber=null;
 private long lastCallRequestAt=-1;
 private final Runnable reloadChangedContacts=()->{ContactPhotos.clearCache();loadContacts(true);};
 private final Runnable reloadChangedCalls=()->refreshLog(true);
 private final Runnable deferredRender=()->render();
 private boolean reloadContactsPending=false;
 private int contactsGeneration=0;
 private Dialog keypadDialog;
 private EditText keypadInput;
 private final java.util.Map<String,String> contactNames=new java.util.HashMap<>();
 private ScrollView pageScroll;
 private String appliedTheme="";
 private String lastPage="";
 private final android.database.ContentObserver contactsObserver=new android.database.ContentObserver(new Handler(Looper.getMainLooper())){
  @Override public void onChange(boolean selfChange){handler.removeCallbacks(reloadChangedContacts);handler.postDelayed(reloadChangedContacts,350);}
 };
 private final android.database.ContentObserver callsObserver=new android.database.ContentObserver(handler){
  @Override public void onChange(boolean selfChange){handler.removeCallbacks(reloadChangedCalls);handler.postDelayed(reloadChangedCalls,250);}
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
  TouchFeedback.pulse(this,prefs,target);
 }
 private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);v.setLayoutDirection(I18n.direction(this));return v;}
 private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);v.setLayoutDirection(I18n.direction(this));return v;}
 private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:d(w),h<0?h:d(h));}
 private void pad(View v,int x,int y){v.setPadding(d(x),d(y),d(x),d(y));}
 private void gap(LinearLayout parent,int size){parent.addView(new View(this),lp(1,size));}
 private TextView t(String content,int size,boolean strong,int color){
  TextView v=new TextView(this);v.setText(I18n.text(this,content));v.setTextSize(TypedValue.COMPLEX_UNIT_SP,scaled(size));
  v.setTextColor(color);v.setGravity(Gravity.CENTER_VERTICAL);v.setIncludeFontPadding(false);
  v.setTypeface(Typeface.create(strong?"sans-serif-medium":"sans-serif",Typeface.NORMAL));return v;
 }
 private TextView center(String content,int size,boolean bold,int color){
  TextView v=t(content,size,bold,color);v.setGravity(Gravity.CENTER);return v;
 }
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);
  prefs=getSharedPreferences("da_caller",MODE_PRIVATE);
  CallArchive.schedule(this);
  page=validPage(saved==null?prefs.getString("start_page","recents"):saved.getString("page","recents"));
  if(saved==null)handleIntent(getIntent());
  else {requestedCallNumber=saved.getString("requested_call");pendingRoleCallNumber=saved.getString("pending_role_call");dialpadNumber=saved.getString("dialpad");}
  if(saved!=null){fastPages=new FastPages(this);fastPages.restoreState(saved);}
  render();loadContacts();
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
  if(intent.hasExtra("contact_search")){
   page="contacts";
   if(fastPages==null)fastPages=new FastPages(this);
   fastPages.setContactQuery(intent.getStringExtra("contact_search"));
  }
  if(intent.hasExtra("place_call")){
   String number=intent.getStringExtra("place_call");
   if(CallLauncher.isCallable(number))handler.post(()->makeCall(number));
   return;
  }
  if(Intent.ACTION_DIAL.equals(intent.getAction())||intent.hasExtra("phone")){page="recents";dialpadNumber=intent.getStringExtra("phone");
   if(Intent.ACTION_DIAL.equals(intent.getAction())&&dialpadNumber==null)dialpadNumber="";
   if(intent.getData()!=null)dialpadNumber=intent.getData().getSchemeSpecificPart();
  }
 }
 @Override public void onResume(){
  super.onResume();
  if(prefs==null)return;
  refreshLog();
  if(checkSelfPermission(Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED){
   if(!contactsLoaded&&!loadingContacts)loadContacts(true);
  }else if(contactsLoaded||!contacts.isEmpty()){
   contactsGeneration++;contactsLoaded=false;contacts=Collections.emptyList();contactNames.clear();
   ContactPhotos.clearCache();render();
  }
  SettingsScreen.refreshAttached(this);
 }
 @Override protected void onStart(){
  super.onStart();
  try{getContentResolver().registerContentObserver(ContactsContract.Contacts.CONTENT_URI,true,contactsObserver);
  }catch(SecurityException ignored){}
  try{getContentResolver().registerContentObserver(CallLog.Calls.CONTENT_URI,true,callsObserver);
  }catch(SecurityException ignored){}
 }
 @Override protected void onStop(){
  if(fastPages!=null)fastPages.remember(lastPage);
  try{getContentResolver().unregisterContentObserver(contactsObserver);}catch(Exception ignored){}
  try{getContentResolver().unregisterContentObserver(callsObserver);}catch(Exception ignored){}
  handler.removeCallbacks(reloadChangedContacts);handler.removeCallbacks(reloadChangedCalls);
  contactsLoaded=false;
  super.onStop();
 }
 @Override public void onRequestPermissionsResult(int request,String[] requested,int[] results){
  super.onRequestPermissionsResult(request,requested,results);
  if(request==21){contactsLoaded=false;loadContacts(true);render();}
  if(request==22){CallArchive.schedule(this);refreshLog();render();if(isDefault())ensureCallNotifications();}
  if(request==24)ensureVoiceNetworkPermission();
  if(request==23&&requestedCallNumber!=null){
   String number=requestedCallNumber;requestedCallNumber=null;
   if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)makeCall(number);
   else{
    if(!CallLauncher.dialExternally(this,number))showKeypad(number);
   }
  }
  SettingsScreen.refreshAttached(this);
 }
 @Override protected void onActivityResult(int code,int result,Intent data){
  super.onActivityResult(code,result,data);
  if(code==100){
   String resumeNumber=pendingRoleCallNumber;
   pendingRoleCallNumber=null;
   render();refreshLog();
   if(resumeNumber!=null){
    if(isDefault())handler.post(()->makeCall(resumeNumber));
    else{
     Toast.makeText(this,I18n.text(this,"כדי להציג שיחות בתוך DA CALLER יש להגדיר אותה כחייגן ברירת המחדל"),Toast.LENGTH_LONG).show();
     showKeypad(resumeNumber);
    }
   }
   if(isDefault()){
    if(checkSelfPermission(Manifest.permission.READ_CALL_LOG)!=PackageManager.PERMISSION_GRANTED)ensureCallLogPermission();
    else ensureCallNotifications();
   }
  }
  if(code==201){contactsLoaded=false;loadContacts(true);}
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
  }catch(Exception e){Toast.makeText(this,I18n.text(this,"פתח הגדרות > אפליקציות ברירת מחדל > טלפון"),Toast.LENGTH_LONG).show();}
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
  if(checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED)return;
  if(loadingContacts){reloadContactsPending|=force;return;}
  if(!force&&contactsLoaded)return;
  loadingContacts=true;
  if(force)ContactPhotos.clearCache();
  final int generation=++contactsGeneration;
  final Context app=getApplicationContext();
  CONTACTS_IO.execute(()->{
   List<Contacts.Person> result=Contacts.all(app);
   handler.post(()->{
    loadingContacts=false;
    if(isFinishing()||isDestroyed())return;
    if(checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED)return;
    if(generation!=contactsGeneration){reloadContactsPending=false;loadContacts(true);return;}
    contacts=result;contactsLoaded=true;
    contactNames.clear();
    for(Contacts.Person item:result)if(!item.phone.isEmpty())contactNames.put(Contacts.canonical(item.phone),item.name);
    if(page.equals("contacts")||page.equals("favorites")||page.equals("recents"))render();
    FavoritesWidget.refresh(this);
    if(reloadContactsPending){reloadContactsPending=false;loadContacts(true);}
   });
  });
 }
 private void refreshLog(){refreshLog(false);}
 private void refreshLog(boolean force){
  if(prefs==null)return;
  CallHistory.refresh(this,force,()->{
   if(!isFinishing()&&!isDestroyed()&&page.equals("recents"))render();
  });
 }
 private ImageView icon(int drawable,int color,int width,int height){
  ImageView image=new ImageView(this);
  image.setImageResource(drawable);
  image.setImageTintList(ColorStateList.valueOf(color));
  image.setScaleType(ImageView.ScaleType.FIT_CENTER);
  image.setPadding(d(3),d(3),d(3),d(3));
  return image;
 }
 private void decorate(View view){TouchFeedback.bind(view,prefs);}
 private void render(){
  // Provider callbacks must not detach the row currently under a finger.
  if(fastPages!=null&&fastPages.isInteracting()){
   handler.removeCallbacks(deferredRender);handler.postDelayed(deferredRender,80);return;
  }
  handler.removeCallbacks(deferredRender);
  getWindow().setStatusBarColor(bg());
  getWindow().setNavigationBarColor(bg());
  getWindow().getDecorView().setSystemUiVisibility(dark()?0:
    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
  String theme=bg()+"_"+panel()+"_"+tint()+"_"+prefs.getString("language","he");
  boolean rebuild=root==null||!theme.equals(appliedTheme);
  if(fastPages!=null)fastPages.remember(lastPage);
  if(rebuild){
   appliedTheme=theme;
   root=column();root.setBackgroundColor(bg());
   workspace=new FrameLayout(this);
   root.addView(workspace,new LinearLayout.LayoutParams(-1,0,1));
   nav=row();nav.setGravity(Gravity.CENTER_VERTICAL);pad(nav,9,5);
   nav.setBackground(stroked(panel(),17));
   nav.setMinimumHeight(d(63));root.addView(nav,lp(-1,-2));
   setContentView(root);
  }
  if(fastPages==null)fastPages=new FastPages(this);
  workspace.removeAllViews();
  pageScroll=null;
  if(page.equals("settings")){
   pageScroll=new SettingsScreen(this).create();
   workspace.addView(pageScroll,new FrameLayout.LayoutParams(-1,-1));
  }else{
   View current;
   if(page.equals("contacts"))current=fastPages.contacts(contacts,false);
   else if(page.equals("favorites"))current=fastPages.contacts(contacts,true);
   else current=fastPages.recents();
   workspace.addView(current,new FrameLayout.LayoutParams(-1,-1));
   fastPages.restore(page);
   addFloatingDial();
  }
  lastPage=page;
  nav.removeAllViews();
  addNav("שיחות",R.drawable.ic_recent,"recents");
  addNav("אנשי קשר",R.drawable.ic_contacts,"contacts");
  addNav("מועדפים",R.drawable.ic_star,"favorites");
  addNav("הגדרות",R.drawable.ic_settings,"settings");
  if(dialpadNumber!=null){
   String n=dialpadNumber;dialpadNumber=null;
   if(!page.equals("settings"))handler.post(()->showKeypad(n));
  }
 }
 private void addFloatingDial(){
  FrameLayout button=new FrameLayout(this);
  button.setBackground(rounded(dark()?0xFFE2C68D:0xFF242424,22));
  button.setElevation(d(8));
  ImageView glyph=icon(R.drawable.ic_dialpad_float,dark()?0xFF151515:Color.WHITE,32,32);
  button.addView(glyph,new FrameLayout.LayoutParams(d(34),d(34),Gravity.CENTER));
  FrameLayout.LayoutParams fab=new FrameLayout.LayoutParams(d(66),d(64),
      Gravity.BOTTOM|Gravity.END);
  fab.setMargins(d(13),d(10),d(16),d(18));
  workspace.addView(button,fab);
  button.setContentDescription(I18n.text(this,"הצגת מקשי חיוג"));
  decorate(button);
  button.setOnClickListener(v->{haptic(button);showKeypad(null);});
 }
 private void addNav(String title,int drawable,String target){
  boolean active=page.equals(target);
  LinearLayout tile=column();tile.setGravity(Gravity.CENTER);
  tile.setBackground(active?rounded(soft(),15):rounded(panel(),15));
  ImageView glyph=icon(drawable,active?tint():muted(),22,22);
  tile.addView(glyph,lp(24,25));
  TextView name=center(I18n.text(this,title),11,active,active?tint():muted());
  tile.addView(name,lp(-1,-2));
  tile.setMinimumHeight(d(51));
  LinearLayout.LayoutParams tabs=new LinearLayout.LayoutParams(0,-2,1);
  tabs.setMargins(d(3),0,d(3),0);
  nav.addView(tile,tabs);
  decorate(tile);tile.setContentDescription(I18n.text(this,title));
  tile.setOnClickListener(v->{haptic(tile);if(!page.equals(target)){page=target;render();}});
 }
 // FastPages callbacks: only use device-local phone and contacts providers.
 void openArchive(){
  if(!CallArchive.enabled(this)){
   Toast.makeText(this,I18n.text(this,"יש להפעיל שמירת ארכיון בהגדרות"),Toast.LENGTH_LONG).show();return;
  }
  startActivity(new Intent(this,ArchiveActivity.class));
 }
 void openContactDetail(long id){
  if(id<=0){Toast.makeText(this,I18n.text(this,"אין איש קשר תואם"),Toast.LENGTH_SHORT).show();return;}
  if(fastPages!=null)fastPages.remember(lastPage);
  Intent detail=new Intent(this,ContactDetailActivity.class);
  detail.putExtra("contact_id",id);startActivityForResult(detail,201);
 }
 void openContactFromNumber(String number){
  if(checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED){
   showNumberOnly(number);return;
  }
  ContactPhotos.lookup(this,number,(id,name,photo)->{
   if(isFinishing()||isDestroyed())return;
   if(id>0)openContactDetail(id);else showNumberOnly(number);
  });
 }
 private void showNumberOnly(String phone){
  new android.app.AlertDialog.Builder(this)
   .setTitle(phone).setMessage(I18n.text(this,"אין איש קשר תואם"))
   .setPositiveButton(I18n.text(this,"התקשר"),(dialog,which)->makeCall(phone))
   .setNegativeButton(I18n.text(this,"סגור"),null).show();
 }
 void showCallNumber(CallHistory.Entry call){
  String kind=call.type==CallLog.Calls.OUTGOING_TYPE?"יוצאת":
      call.type==CallLog.Calls.INCOMING_TYPE?"נכנסת":
      call.type==CallLog.Calls.MISSED_TYPE?"לא נענתה":
      call.type==CallLog.Calls.REJECTED_TYPE?"נדחתה":"שיחה";
  String description=I18n.text(this,kind)+"\n"+
    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT,I18n.locale(this)).format(new java.util.Date(call.date))+
    "\n"+I18n.text(this,"משך השיחה")+": "+call.duration+" "+I18n.text(this,"שניות");
  new android.app.AlertDialog.Builder(this)
   .setTitle(CallLauncher.isCallable(call.number)?call.number:I18n.text(this,"מספר חסוי")).setMessage(description)
   .setPositiveButton(I18n.text(this,"התקשר"),(dialog,which)->makeCall(call.number))
   .setNeutralButton(I18n.text(this,"כרטיס איש קשר"),(dialog,which)->openContactFromNumber(call.number))
   .setNegativeButton(I18n.text(this,"סגור"),null).show();
 }
 void refreshPagePreserveScroll(){
  int y=pageScroll==null?0:pageScroll.getScrollY();
  render();
  if(page.equals("settings")&&pageScroll!=null)
   pageScroll.post(()->pageScroll.scrollTo(0,y));
 }
 void requestDefaultDialer(){askDefault();}
 void openNotificationSettings(){
  try{
   Intent settings=new Intent("android.settings.APP_NOTIFICATION_SETTINGS");
   settings.putExtra("android.provider.extra.APP_PACKAGE",getPackageName());
   startActivity(settings);
  }catch(Exception ignored){}
 }
 void dialFromList(String phone){makeCall(phone);}
 void openDialPad(String phone){showKeypad(phone);}
 void refreshPage(){render();}
 void reloadCallLog(){CallHistory.refresh(this,true,()->{if(!isFinishing()&&!isDestroyed())render();});}
 void requestLog(){ensureCallLogPermission();}
 void requestContacts(){requestPermissions(new String[]{Manifest.permission.READ_CONTACTS},21);}
 void openContactsTab(){page="contacts";render();}
 void newContact(String phone){
  Intent intent=new Intent(this,ContactEditorActivity.class);
  if(phone!=null&&!phone.trim().isEmpty())intent.putExtra("incoming_number",phone);
  startActivityForResult(intent,201);
 }
 void editContact(long id,String extraNumber){
  Intent intent=new Intent(this,ContactEditorActivity.class);
  intent.putExtra("contact_id",id);
  if(extraNumber!=null)intent.putExtra("incoming_number",extraNumber);
  startActivityForResult(intent,201);
 }
 void chooseExistingContact(String phone){
  Intent intent=new Intent(this,ContactChooseActivity.class);
  intent.putExtra("incoming_number",phone);startActivityForResult(intent,201);
 }
 void favoriteFromList(Contacts.Person person){toggleFavorite(person);}
 void reloadContacts(){contactsLoaded=false;loadContacts(true);}
 java.util.Map<String,String> nameIndex(){return contactNames;}
 private Set<String> favorites(){return new HashSet<>(prefs.getStringSet("favorites",Collections.emptySet()));}
 private void toggleFavorite(Contacts.Person person){
  Set<String> set=favorites();if(!set.add(person.key))set.remove(person.key);
  prefs.edit().putStringSet("favorites",set).apply();
  FavoritesWidget.refresh(this);render();
 }
 private boolean shouldRequestPhoneRole(){
  try{
   if(android.os.Build.VERSION.SDK_INT>=29){
    RoleManager rm=getSystemService(RoleManager.class);
    return rm!=null&&rm.isRoleAvailable(RoleManager.ROLE_DIALER)&&!rm.isRoleHeld(RoleManager.ROLE_DIALER);
   }
   TelecomManager tm=getSystemService(TelecomManager.class);
   return tm!=null&&!getPackageName().equals(tm.getDefaultDialerPackage());
  }catch(Exception e){return false;}
 }
 private void makeCall(String phone){
  if(!CallLauncher.isCallable(phone)){Toast.makeText(this,I18n.text(this,"הזן מספר תקין לחיוג"),Toast.LENGTH_SHORT).show();return;}
  if(requestedCallNumber!=null||pendingRoleCallNumber!=null)return; // Never mix destination across prompts.
  if(shouldRequestPhoneRole()){
   pendingRoleCallNumber=phone;
   askDefault();
   return;
  }
  if(checkSelfPermission(Manifest.permission.CALL_PHONE)!=PackageManager.PERMISSION_GRANTED){
   requestedCallNumber=phone;requestPermissions(new String[]{Manifest.permission.CALL_PHONE},23);return;
  }
  long now=android.os.SystemClock.elapsedRealtime();
  if(lastCallRequestAt>=0&&now-lastCallRequestAt<800)return;
  lastCallRequestAt=now;
  if(!CallLauncher.place(this,phone))Toast.makeText(this,I18n.text(this,"לא ניתן לבצע שיחה"),Toast.LENGTH_LONG).show();
 }
 private void showKeypad(String preset){
  if(keypadDialog!=null&&keypadDialog.isShowing()){
   if(preset!=null&&keypadInput!=null){keypadInput.setText(preset);keypadInput.setSelection(keypadInput.length());}
   return;
  }
  Dialog dialog=new Dialog(this);keypadDialog=dialog;
  dialog.setOnDismissListener(ignored->{keypadDialog=null;keypadInput=null;});
  LinearLayout sheet=column();
  pad(sheet,15,13);
  sheet.setBackground(rounded(panel(),27));
  TextView handle=center("━━━━",17,false,muted());
  sheet.addView(handle,lp(-1,23));
  LinearLayout entryBar=row();
  sheet.addView(entryBar,lp(-1,56));
  final EditText input=new EditText(this);keypadInput=input;
  input.setSingleLine(true);
  input.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
  input.setShowSoftInputOnFocus(false);
  input.setTextColor(text());input.setHintTextColor(muted());
  input.setTextSize(TypedValue.COMPLEX_UNIT_SP,29);
  input.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));
  input.setGravity(Gravity.CENTER);
  input.setTextDirection(View.TEXT_DIRECTION_LTR);
  input.setBackgroundColor(Color.TRANSPARENT);
  input.setHint(I18n.text(this,"מספר טלפון"));
  if(preset!=null)input.setText(preset);
  entryBar.addView(input,new LinearLayout.LayoutParams(0,d(56),1));
  // Close belongs in the title row; the primary action row now has symmetrical utilities.
  ImageView closeIcon=icon(R.drawable.ic_control_close,muted(),20,20);
  FrameLayout dismiss=new FrameLayout(this);
  dismiss.setBackground(rounded(soft(),16));
  dismiss.addView(closeIcon,new FrameLayout.LayoutParams(d(24),d(24),Gravity.CENTER));
  entryBar.addView(dismiss,lp(44,44));
  dismiss.setContentDescription(I18n.text(this,"סגירת מקשי החיוג"));
  decorate(dismiss);
  dismiss.setOnClickListener(v->{haptic(dismiss);dialog.dismiss();});
  gap(sheet,8);
  String[] digit={"1","2","3","4","5","6","7","8","9","*","0","#"};
  String[] letters={"","ABC","DEF","GHI","JKL","MNO","PQRS","TUV","WXYZ","","+",""};
  int buttonHeight=screenHeight()<650?53:62;
  for(int r=0;r<4;r++){
   LinearLayout row= row();
   row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
   sheet.addView(row,lp(-1,buttonHeight+6));
   for(int c=0;c<3;c++){
    final int idx=r*3+c;
    String key=digit[idx];
    LinearLayout tile=column();tile.setGravity(Gravity.CENTER);
    GradientDrawable glass=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
      dark()?new int[]{0xFF30353D,0xFF20242B}:new int[]{0xFFFFFEFC,0xFFF3F4F7});
    glass.setCornerRadius(d(24));
    glass.setStroke(d(1),dark()?0xFF3F4752:0xFFE7E8ED);
    tile.setBackground(glass);tile.setMinimumHeight(d(48));
    LinearLayout.LayoutParams tileP=new LinearLayout.LayoutParams(0,d(buttonHeight),1);
    tileP.setMargins(d(5),d(2),d(5),d(2));row.addView(tile,tileP);
    TextView number=center(key,key.equals("*")||key.equals("#")?26:29,true,text());
    number.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));
    tile.addView(number,lp(-1,35));
    if(!letters[idx].isEmpty()){
     TextView subtitle=center(letters[idx],10,true,muted());
     subtitle.setLetterSpacing(0.1f);
     tile.addView(subtitle,lp(-1,14));
    }
    decorate(tile);
    tile.setContentDescription(key.equals("0")?"0, לחיצה ארוכה לסימן פלוס":key);
    tile.setOnClickListener(v->{haptic(tile);input.append(key);});
    if(key.equals("0"))tile.setOnLongClickListener(v->{haptic(tile);input.append("+");return true;});
   }
  }
  gap(sheet,11);
  LinearLayout actions=row();actions.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
  sheet.addView(actions,lp(-1,73));
  ImageView removeIcon=icon(R.drawable.ic_backspace,tint(),28,28);
  FrameLayout remove=new FrameLayout(this);
  remove.setBackground(stroked(soft(),22));
  remove.addView(removeIcon,new FrameLayout.LayoutParams(d(32),d(32),Gravity.CENTER));
  LinearLayout.LayoutParams removeP=new LinearLayout.LayoutParams(0,d(64),1);
  removeP.setMargins(d(3),0,d(6),0);
  actions.addView(remove,removeP);
  remove.setContentDescription(I18n.text(this,"מחיקת ספרה"));
  decorate(remove);
  remove.setOnClickListener(v->{
   haptic(remove);
   String n=input.getText().toString();
   if(!n.isEmpty()){input.setText(n.substring(0,n.length()-1));input.setSelection(input.length());}
  });
  remove.setOnLongClickListener(v->{haptic(remove);input.setText("");return true;});
  FrameLayout call=new FrameLayout(this);
  call.setBackground(rounded(0xFF168D61,31));call.setElevation(d(3));
  ImageView callIcon=icon(R.drawable.ic_phone_line,Color.WHITE,38,38);
  call.addView(callIcon,new FrameLayout.LayoutParams(d(44),d(44),Gravity.CENTER));
  LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,d(68),2);
  cp.setMargins(d(8),0,d(8),0);actions.addView(call,cp);
  call.setContentDescription(I18n.text(this,"חיוג למספר"));
  decorate(call);
  call.setOnClickListener(v->{
   haptic(call);
   String number=input.getText().toString().trim();
   if(number.isEmpty()){Toast.makeText(this,I18n.text(this,"הזן מספר לחיוג"),Toast.LENGTH_SHORT).show();return;}
   dialog.dismiss();makeCall(number);
  });
  ImageView addIcon=icon(R.drawable.ic_control_add,tint(),26,26);
  FrameLayout addContact=new FrameLayout(this);
  addContact.setBackground(stroked(soft(),22));
  addContact.addView(addIcon,new FrameLayout.LayoutParams(d(32),d(32),Gravity.CENTER));
  LinearLayout.LayoutParams addP=new LinearLayout.LayoutParams(0,d(64),1);
  addP.setMargins(d(6),0,d(3),0);
  actions.addView(addContact,addP);
  addContact.setContentDescription(I18n.text(this,"הוספת מספר לאנשי קשר"));
  decorate(addContact);
  addContact.setOnClickListener(v->{
   haptic(addContact);
   String number=input.getText().toString().trim();
   if(number.isEmpty()){
    Toast.makeText(this,I18n.text(this,"הזן מספר לפני הוספה לאנשי קשר"),Toast.LENGTH_SHORT).show();return;
   }
   new android.app.AlertDialog.Builder(this)
    .setTitle(I18n.text(this,"הוספת מספר לאנשי קשר"))
    .setItems(new String[]{I18n.text(this,"יצירת איש קשר חדש"),I18n.text(this,"הוספה לאיש קשר קיים")},
      (chooser,index)->{
       dialog.dismiss();
       if(index==0)newContact(number);else chooseExistingContact(number);
      })
    .setNegativeButton(I18n.text(this,"ביטול"),null).show();
  });
  ScrollView keypadScroll=new ScrollView(this);keypadScroll.setFillViewport(true);keypadScroll.addView(sheet);
  dialog.setContentView(keypadScroll);
  dialog.show();
  Window window=dialog.getWindow();
  if(window!=null){
   window.setBackgroundDrawableResource(android.R.color.transparent);
   window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
   window.setDimAmount(0.45f);window.setGravity(Gravity.BOTTOM);window.setLayout(-1,-2);
   window.setAttributes(limitDialogHeight(window.getAttributes()));
  }
 }
 private WindowManager.LayoutParams limitDialogHeight(WindowManager.LayoutParams attributes){
  // Jelly Star has a 3-inch display; allow the entire keypad to scroll at large font sizes.
  int available=getResources().getDisplayMetrics().heightPixels-d(32);
  if(keypadDialog!=null){keypadDialog.getWindow().getDecorView().measure(
   View.MeasureSpec.makeMeasureSpec(getResources().getDisplayMetrics().widthPixels,View.MeasureSpec.EXACTLY),
   View.MeasureSpec.makeMeasureSpec(available,View.MeasureSpec.AT_MOST));
   if(keypadDialog.getWindow().getDecorView().getMeasuredHeight()>=available)attributes.height=available;
  }
  return attributes;
 }
 private String validPage(String value){
  return "contacts".equals(value)||"favorites".equals(value)||"settings".equals(value)?value:"recents";
 }
 @Override protected void onSaveInstanceState(Bundle state){
  state.putString("page",page);state.putString("requested_call",requestedCallNumber);state.putString("pending_role_call",pendingRoleCallNumber);
  if(fastPages!=null){fastPages.remember(lastPage);fastPages.saveState(state);}
  if(keypadDialog!=null&&keypadDialog.isShowing())state.putString("dialpad",keypadInput==null?"":keypadInput.getText().toString());
  super.onSaveInstanceState(state);
 }
 @Override protected void onDestroy(){
  handler.removeCallbacksAndMessages(null);
  if(keypadDialog!=null)keypadDialog.dismiss();
  super.onDestroy();
 }
}
