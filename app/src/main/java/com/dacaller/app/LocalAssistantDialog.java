package com.dacaller.app;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.drawable.GradientDrawable;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Local, transient assistant UI. The only call path is a separate explicit confirmation. */
final class LocalAssistantDialog {
 private static final ExecutorService IO=Executors.newSingleThreadExecutor();
 private static final int LIMIT=20;
 private final MainActivity host;
 private final boolean english,dark,amoled;
 private final LinearLayout body,results;
 private final EditText input;
 private AlertDialog dialog;
 private int generation;
 private LocalAssistantDialog(MainActivity host){
  this.host=host;english=I18n.isEnglish(host);
  android.content.SharedPreferences p=host.getSharedPreferences("da_caller",0);
  amoled=p.getBoolean("amoled",false);dark=p.getBoolean("dark",false)||amoled;
  body=new LinearLayout(host);body.setOrientation(LinearLayout.VERTICAL);
  body.setLayoutDirection(english?View.LAYOUT_DIRECTION_LTR:View.LAYOUT_DIRECTION_RTL);
  body.setPadding(dp(18),dp(12),dp(18),dp(12));body.setBackgroundColor(NexPalette.background(dark,amoled));
  TextView intro=label(tr("עוזר מקומי · פקודות טקסט ללא אינטרנט","Local assistant · offline text commands"),14,false);
  body.addView(intro);
  input=new EditText(host);input.setTag("assistant_input");input.setTextColor(NexPalette.ink(dark));
  input.setHintTextColor(NexPalette.muted(dark));input.setHint(tr("לדוגמה: חפש איש קשר דוד","For example: find contact Dana"));
  input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
  input.setSingleLine(true);input.setTextSize(16);input.setPadding(dp(12),0,dp(12),0);
  input.setBackground(shape(NexPalette.surface(dark,amoled),18));AppAppearance.font(input,false);
  input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(512)});
  input.setSaveEnabled(false);if(android.os.Build.VERSION.SDK_INT>=26)input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
  body.addView(input,new LinearLayout.LayoutParams(-1,dp(56)));
  TextView submit=button(tr("חפש / הבן פקודה","Run command"),true);submit.setTag("assistant_submit");
  body.addView(submit);submit.setOnClickListener(v->submit(input.getText().toString()));
  for(String example:new String[]{tr("שיחות שלא נענו","Missed calls"),tr("שיחות אחרונות","Recent calls"),tr("חזור למתקשר האחרון","Call back last caller")}){
   TextView chip=button(example,false);body.addView(chip);chip.setOnClickListener(v->{input.setText(example);submit(example);});
  }
  results=new LinearLayout(host);results.setOrientation(LinearLayout.VERTICAL);results.setTag("assistant_results");body.addView(results);
 }
 static void show(MainActivity host){new LocalAssistantDialog(host).open();}
 private void open(){
  ScrollView scroll=new ScrollView(host);scroll.addView(body,new ScrollView.LayoutParams(-1,-2));
  dialog=new AlertDialog.Builder(host,dark?R.style.AppDialogDark:R.style.AppDialogLight)
    .setTitle("DA Intelligence").setView(scroll).setNegativeButton(tr("סגור","Close"),null).create();
  dialog.setOnDismissListener(d->{generation++;input.setText("");results.removeAllViews();});
  AppAppearance.show(dialog);
  if(dialog.getWindow()!=null)dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
 }
 private String tr(String he,String en){return english?en:he;}
 private int dp(int n){return Math.round(n*host.getResources().getDisplayMetrics().density);}
 private GradientDrawable shape(int color,int radius){GradientDrawable s=new GradientDrawable();s.setColor(color);s.setCornerRadius(dp(radius));return s;}
 private TextView label(String text,int size,boolean strong){
  TextView v=new TextView(host);v.setText(text);v.setTextSize(size);v.setTextColor(NexPalette.ink(dark));AppAppearance.font(v,strong);
  v.setPadding(dp(8),dp(8),dp(8),dp(8));return v;
 }
 private TextView button(String text,boolean primary){
  TextView v=label(text,15,true);v.setGravity(Gravity.CENTER);v.setMinHeight(dp(48));v.setFocusable(true);
  v.setBackground(shape(primary?NexPalette.primary(dark):NexPalette.soft(dark),16));
  if(primary)v.setTextColor(NexPalette.onPrimary(dark));
  LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(6);v.setLayoutParams(p);return v;
 }
 private boolean permitted(String permission){return permission==null||host.checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED;}
 private boolean live(int token){return token==generation&&dialog!=null&&dialog.isShowing()&&!host.isFinishing()&&!host.isDestroyed();}
 private void message(String text){results.removeAllViews();results.addView(label(text,14,false));}
 private static final class Row{
  final String name,number;Row(String name,String number){this.name=name;this.number=number;}
 }
 private static final class Answer{
  final List<Row> rows=new ArrayList<>();String info;boolean more;
 }
 private void submit(String raw){
  final int token=++generation;LocalAssistant.Intent request=LocalAssistant.parse(raw);
  if(request.kind==LocalAssistant.Kind.UNKNOWN){message(tr("לא זוהתה פקודה נתמכת. נסה חיפוש איש קשר, עסק או שיחות אחרונות.","Unsupported command. Try contact search, business search or recent calls."));return;}
  String permission=permission(request);
  if(!permitted(permission)){message(tr("נדרשת הרשאה מתאימה בהגדרות החייגן. לא נקראו נתונים.","Permission is required in dialer settings. No data was read."));return;}
  message(tr("מחפש במכשיר…","Searching device…"));
  IO.execute(()->{
   Answer answer;
   try{answer=read(request);}catch(RuntimeException failure){answer=new Answer();answer.info=tr("לא ניתן לקרוא את הנתונים כרגע.","Data could not be read right now.");}
   final Answer result=answer;
   host.runOnUiThread(()->{
    if(!live(token))return;
    if(!permitted(permission)){message(tr("ההרשאה אינה זמינה.","Permission is unavailable."));return;}
    display(result,request,permission,token);
   });
  });
 }
 private String permission(LocalAssistant.Intent request){
  switch(request.kind){
   case CONTACT_SEARCH:return Manifest.permission.READ_CONTACTS;
   case CALL_PROPOSAL:return LocalAssistant.safeNumber(request.query)==null?Manifest.permission.READ_CONTACTS:null;
   case MISSED_CALLS:case RECENTS:case CALLBACK_PROPOSAL:return Manifest.permission.READ_CALL_LOG;
   default:return null;
  }
 }
 private Answer read(LocalAssistant.Intent request){
  Answer result=new Answer();
  if(request.kind==LocalAssistant.Kind.BUSINESS_SEARCH){
   if(!BuildConfig.BUSINESS_DIRECTORY_ENABLED){result.info=tr("מאגר העסקים אינו זמין.","Business directory is unavailable.");return result;}
   BusinessDirectory.Page page=BusinessDirectory.search(host,request.query,0);
   if(page.status!=BusinessDirectory.Status.AVAILABLE){result.info=tr("מאגר העסקים אינו זמין.","Business directory is unavailable.");return result;}
   for(BusinessDirectory.Entry entry:page.entries){if(result.rows.size()==LIMIT){result.more=true;break;}
    result.rows.add(new Row(entry.name+" · "+entry.city,entry.phone));}
   result.more|=page.hasMore;
   result.info=tr("מאגר חלקי, מספרים לא אומתו. מקור: ","Partial directory, numbers unverified. Source: ")+page.manifest.sourceName+" · "+page.manifest.license+" · "+page.manifest.updatedAt;
   return result;
  }
  if(request.kind==LocalAssistant.Kind.CALL_PROPOSAL){
   String number=LocalAssistant.safeNumber(request.query);if(number!=null){result.rows.add(new Row(tr("מספר שהוזן","Entered number"),number));return result;}
  }
  if(request.kind==LocalAssistant.Kind.CONTACT_SEARCH||request.kind==LocalAssistant.Kind.CALL_PROPOSAL){
   String[] columns={ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER};
   try(Cursor cursor=host.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,columns,null,null,ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME+" ASC")){
    if(cursor==null)throw new IllegalStateException("Contacts unavailable");
    while(cursor.moveToNext()){
     String name=cursor.getString(0),number=cursor.getString(1);
     if(name==null)name="";
     if(!name.toLowerCase(Locale.ROOT).contains(request.query)&&!(number!=null&&number.contains(request.query)))continue;
     if(result.rows.size()==LIMIT){result.more=true;break;}result.rows.add(new Row(name,number));
    }
   }return result;
  }
  String[] columns={CallLog.Calls.NUMBER,CallLog.Calls.DATE,CallLog.Calls.TYPE};
  String selection=request.kind==LocalAssistant.Kind.MISSED_CALLS?CallLog.Calls.TYPE+"=?":request.kind==LocalAssistant.Kind.CALLBACK_PROPOSAL?CallLog.Calls.TYPE+" IN (?,?)":null;
  String[] args=request.kind==LocalAssistant.Kind.MISSED_CALLS?new String[]{String.valueOf(CallLog.Calls.MISSED_TYPE)}:
   request.kind==LocalAssistant.Kind.CALLBACK_PROPOSAL?new String[]{String.valueOf(CallLog.Calls.INCOMING_TYPE),String.valueOf(CallLog.Calls.MISSED_TYPE)}:null;
  try(Cursor cursor=host.getContentResolver().query(CallLog.Calls.CONTENT_URI,columns,selection,args,CallLog.Calls.DATE+" DESC,"+CallLog.Calls._ID+" DESC")){
   if(cursor==null)throw new IllegalStateException("Call history unavailable");
   while(cursor.moveToNext()){
    if(result.rows.size()==LIMIT){result.more=true;break;}
    String when=android.text.format.DateFormat.getDateFormat(host).format(new java.util.Date(cursor.getLong(1)))+" "+android.text.format.DateFormat.getTimeFormat(host).format(new java.util.Date(cursor.getLong(1)));
    String kind=cursor.getInt(2)==CallLog.Calls.MISSED_TYPE?tr("לא נענתה","Missed"):cursor.getInt(2)==CallLog.Calls.OUTGOING_TYPE?tr("יוצאת","Outgoing"):cursor.getInt(2)==CallLog.Calls.INCOMING_TYPE?tr("נכנסת","Incoming"):tr("שיחה","Call");
    result.rows.add(new Row(kind+" · "+when,cursor.getString(0)));
    if(request.kind==LocalAssistant.Kind.CALLBACK_PROPOSAL)break;
   }
  }return result;
 }
 private void display(Answer answer,LocalAssistant.Intent request,String permission,int token){
  results.removeAllViews();
  if(answer.info!=null)results.addView(label(answer.info,13,false));
  if(answer.rows.isEmpty()){if(answer.info==null)results.addView(label(tr("לא נמצאו תוצאות.","No results found."),14,false));return;}
  for(Row row:answer.rows){
   String number=LocalAssistant.safeNumber(row.number);
   TextView item=label(row.name+"\n"+(row.number==null||row.number.isEmpty()?tr("מספר לא זמין","Number unavailable"):row.number),15,true);
   item.setBackground(shape(NexPalette.surface(dark,amoled),16));results.addView(item);
   if(request.requiresConfirmation()&&number!=null){
    TextView action=button(tr("בחר לחיוג","Select to call"),false);results.addView(action);
    action.setOnClickListener(v->{
     if(!live(token)||!permitted(permission))return;
     AppAppearance.show(new AlertDialog.Builder(host,dark?R.style.AppDialogDark:R.style.AppDialogLight)
      .setTitle(tr("אישור חיוג","Confirm call")).setMessage(row.name+"\n"+number)
      .setNegativeButton(tr("ביטול","Cancel"),null).setPositiveButton(tr("חייג","Call"),(d,w)->{
       if(live(token)&&permitted(permission)){dialog.dismiss();host.dialFromList(number);}
      }));
    });
   }
  }
  if(answer.more)results.addView(label(tr("מוצגות 20 תוצאות ראשונות. דייק את החיפוש.","Showing the first 20 results. Refine the search."),13,false));
 }
}
