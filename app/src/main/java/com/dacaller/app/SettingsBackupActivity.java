package com.dacaller.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** System document picker and explicit, non-destructive settings restore confirmation. */
public final class SettingsBackupActivity extends Activity {
 private static final int DOCUMENT=731;
 private static final String EXPORT="export";
 private static final ExecutorService IO=Executors.newSingleThreadExecutor();
 private Session session;
 private TextView status;
 private ProgressBar progress;
 private Button close;
 private AlertDialog confirmation;
 static Intent intent(Context context,boolean export){return new Intent(context,SettingsBackupActivity.class).putExtra(EXPORT,export);}
 private String tr(String he,String en){return I18n.isEnglish(this)?en:he;}
 private int dp(float n){return Math.round(getResources().getDisplayMetrics().density*n);}
 @Override protected void onCreate(Bundle state){
  super.onCreate(state);setResult(RESULT_CANCELED);
  SharedPreferences prefs=SettingsBackup.preferences(this);boolean dark=prefs.getBoolean("dark",false)||prefs.getBoolean("amoled",false);
  int background=prefs.getBoolean("amoled",false)?Color.BLACK:dark?0xFF141414:0xFFF7F7F5;
  int ink=dark?0xFFF6F3EC:0xFF17291F;
  getWindow().setStatusBarColor(background);getWindow().setNavigationBarColor(background);
  getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setGravity(Gravity.CENTER_VERTICAL);
  root.setLayoutDirection(I18n.isEnglish(this)?View.LAYOUT_DIRECTION_LTR:View.LAYOUT_DIRECTION_RTL);
  root.setPadding(dp(24),dp(24),dp(24),dp(24));root.setBackgroundColor(background);
  ScrollView viewport=new ScrollView(this);viewport.setFillViewport(true);viewport.setBackgroundColor(background);
  viewport.addView(root,new ScrollView.LayoutParams(-1,-2));setContentView(viewport);
  TextView title=new TextView(this);title.setText(tr("גיבוי ושחזור הגדרות","Settings backup and restore"));title.setTextSize(24);title.setTextColor(ink);
  root.addView(title,new LinearLayout.LayoutParams(-1,-2));
  TextView note=new TextView(this);note.setText(tr(
    "הקובץ כולל הגדרות, מועדפים והערות שיחה מקומיות. הוא עשוי לכלול מספרי טלפון. אנשי הקשר, תמונותיהם, יומן השיחות והארכיון אינם נכללים ואינם נמחקים. הרשאות מערכת ומיקומי ווידג׳טים אינם ניתנים להעברה. ניתן לשמור קובץ במכשיר ללא אינטרנט.",
    "The file includes settings, favorites and local call notes, and may contain phone numbers. Contacts, photos, call history and the archive are not included or deleted. System permissions and launcher widget placements cannot be transferred. Save locally to work offline."));
  note.setTextSize(14);note.setTextColor(ink);note.setPadding(0,dp(18),0,dp(18));root.addView(note,new LinearLayout.LayoutParams(-1,-2));
  progress=new ProgressBar(this);progress.setContentDescription(tr("מעבד קובץ","Processing file"));root.addView(progress,new LinearLayout.LayoutParams(dp(48),dp(48)));
  status=new TextView(this);status.setTextSize(16);status.setTextColor(ink);status.setPadding(0,dp(18),0,dp(18));root.addView(status,new LinearLayout.LayoutParams(-1,-2));
  close=new Button(this);close.setText(tr("סגור","Close"));close.setMinHeight(dp(48));close.setOnClickListener(v->finish());root.addView(close,new LinearLayout.LayoutParams(-1,-2));
  Object retained=getLastNonConfigurationInstance();
  if(retained instanceof Session)session=(Session)retained;
  else{
   session=new Session(getApplicationContext(),getIntent().getBooleanExtra(EXPORT,true));
   if(state!=null){
    String uri=state.getString("document_uri");String phase=state.getString("phase",Phase.PICKING.name());
    if(uri!=null)session.uri=Uri.parse(uri);
    if(phase.equals(Phase.READING.name())||phase.equals(Phase.CONFIRM.name()))session.read();
    else if(phase.equals(Phase.EXPORTING.name()))session.export();
    else if(phase.equals(Phase.SUCCESS.name()))session.phase=Phase.SUCCESS;
    else if(phase.equals(Phase.APPLYING.name())||phase.equals(Phase.ERROR.name()))session.phase=Phase.ERROR;
   }
  }
  session.observer=this;render();
  if(state==null&&retained==null)chooseDocument();
 }
 private void chooseDocument(){
  Intent document=new Intent(session.exportMode?Intent.ACTION_CREATE_DOCUMENT:Intent.ACTION_OPEN_DOCUMENT);
  document.addCategory(Intent.CATEGORY_OPENABLE);document.setType("application/json");
  if(session.exportMode)document.putExtra(Intent.EXTRA_TITLE,"DA-CALLER-settings-"+
    new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.US).format(new java.util.Date())+".json");
  try{startActivityForResult(document,DOCUMENT);}
  catch(RuntimeException failure){session.phase=Phase.ERROR;render();}
 }
 @Override protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);
  if(request!=DOCUMENT)return;
  if(result!=RESULT_OK||data==null||data.getData()==null){finish();return;}
  Uri uri=data.getData();
  if(!"content".equals(uri.getScheme())){session.phase=Phase.ERROR;render();return;}
  session.uri=uri;if(session.exportMode)session.export();else session.read();
 }
 private void render(){
  if(isFinishing()||isDestroyed())return;
  Phase phase=session.phase;boolean working=phase==Phase.EXPORTING||phase==Phase.READING||phase==Phase.APPLYING;
  progress.setVisibility(working?View.VISIBLE:View.GONE);close.setVisibility(working?View.GONE:View.VISIBLE);
  if(phase==Phase.PICKING)status.setText(tr("בחר קובץ באמצעות מנהל הקבצים של Android.","Choose a file using Android's document picker."));
  else if(phase==Phase.EXPORTING)status.setText(tr("שומר גיבוי…","Saving backup…"));
  else if(phase==Phase.READING)status.setText(tr("בודק את קובץ הגיבוי…","Validating backup file…"));
  else if(phase==Phase.APPLYING)status.setText(tr("משחזר הגדרות…","Restoring settings…"));
  else if(phase==Phase.ERROR)status.setText(tr(
    "לא ניתן להשלים את הפעולה. בדוק שהקובץ הוא גיבוי הגדרות נתמך של DA CALLER ושיש הרשאת גישה ומקום פנוי. נתוני השיחות לא נמחקו.",
    "Could not complete the operation. Check that this is a supported DA CALLER settings backup and that storage access and space are available. Call data was not deleted."));
  else if(phase==Phase.SUCCESS){
   status.setText(session.exportMode?tr("הגיבוי נשמר בקובץ שבחרת.","Backup saved to the file you selected."):
     tr("ההגדרות שוחזרו. אנשי הקשר והארכיון נשמרו.","Settings restored. Contacts and the archive were preserved."));
   setResult(RESULT_OK,new Intent().putExtra("settings_restored",!session.exportMode));
  }else if(phase==Phase.CONFIRM){
   status.setText(tr("הקובץ תקין. יש לאשר לפני שינוי הגדרות.","The file is valid. Confirm before changing settings."));
   if(confirmation!=null&&confirmation.isShowing())return;
   SettingsBackup.Snapshot snapshot=session.snapshot;
   String summary=String.format(I18n.locale(this),tr(
     "לשחזר %d הגדרות, %d מועדפים ו־%d הערות שיחה?\n\nהערכים שבקובץ יחליפו את הערכים הקיימים. הערות שאינן בקובץ יישמרו. שיחות ואנשי קשר לא יימחקו. שחזור הגדרות ארכיון עשוי לשנות את תקופת השמירה בעדכוני הארכיון הבאים.",
     "Restore %d settings, %d favorites and %d call notes?\n\nValues in the file will replace existing values. Notes absent from the file will be kept. Calls and contacts will not be deleted. Restoring archive preferences may change retention during future archive updates."),
     snapshot.settings(),snapshot.favorites(),snapshot.notes());
   if(snapshot.values.containsKey(CallArchive.PREF))summary+="\n\n"+tr("שמירת ארכיון לאחר השחזור: ","Archive retention after restore: ")+
     retention((String)snapshot.values.get(CallArchive.PREF));
   confirmation=new AlertDialog.Builder(this).setTitle(tr("שחזור הגדרות","Restore settings")).setMessage(summary)
     .setNegativeButton(tr("ביטול","Cancel"),(dialog,which)->finish())
     .setPositiveButton(tr("שחזר","Restore"),(dialog,which)->session.restore())
     .setOnCancelListener(dialog->finish()).show();
  }
 }
 private String retention(String policy){
  if(CallArchive.FOREVER.equals(policy))return tr("ללא הגבלת זמן","Unlimited");
  if(CallArchive.YEAR.equals(policy))return tr("שנה נוספת","One extra year");
  if(CallArchive.HALF.equals(policy))return tr("חצי שנה נוספת","Six extra months");
  if(CallArchive.QUARTER.equals(policy))return tr("שלושה חודשים נוספים","Three extra months");
  if(CallArchive.MONTH.equals(policy))return tr("חודש נוסף","One extra month");
  return tr("כבוי — נתונים קיימים אינם נמחקים","Off — existing data is kept");
 }
 @Override public Object onRetainNonConfigurationInstance(){return session;}
 @Override protected void onSaveInstanceState(Bundle state){
  super.onSaveInstanceState(state);state.putString("phase",session.phase.name());
  if(session.uri!=null)state.putString("document_uri",session.uri.toString());
 }
 @Override protected void onDestroy(){
  if(session!=null&&session.observer==this)session.observer=null;
  if(confirmation!=null)confirmation.dismiss();super.onDestroy();
 }
 private enum Phase {PICKING,EXPORTING,READING,CONFIRM,APPLYING,SUCCESS,ERROR}
 /** Retained work owns application context; dialogs and Activities never escape their lifecycle. */
 private static final class Session {
  final Context app;final boolean exportMode;final Handler main=new Handler(Looper.getMainLooper());
  Phase phase=Phase.PICKING;Uri uri;SettingsBackup.Snapshot snapshot;SettingsBackupActivity observer;
  Session(Context app,boolean exportMode){this.app=app;this.exportMode=exportMode;}
  void update(Phase value){phase=value;if(observer!=null)observer.render();}
  void export(){
   if(uri==null){update(Phase.ERROR);return;}update(Phase.EXPORTING);
   IO.execute(()->{
    boolean ok=false;
    try{
     // Validate and encode completely before opening/truncating the selected output.
     byte[] bytes=SettingsBackup.encode(SettingsBackup.capture(SettingsBackup.preferences(app)));
     try(OutputStream output=app.getContentResolver().openOutputStream(uri,"wt")){
      if(output==null)throw new java.io.IOException("Missing output stream");output.write(bytes);output.flush();
     }
     ok=true;
    }catch(Exception ignored){}
    final boolean success=ok;main.post(()->update(success?Phase.SUCCESS:Phase.ERROR));
   });
  }
  void read(){
   if(uri==null){update(Phase.ERROR);return;}update(Phase.READING);
   IO.execute(()->{
    SettingsBackup.Snapshot read=null;
    try(InputStream input=app.getContentResolver().openInputStream(uri)){read=SettingsBackup.read(input);}catch(Exception ignored){}
    final SettingsBackup.Snapshot parsed=read;main.post(()->{snapshot=parsed;update(parsed==null?Phase.ERROR:Phase.CONFIRM);});
   });
  }
  void restore(){
   if(phase!=Phase.CONFIRM||snapshot==null)return;update(Phase.APPLYING);
   IO.execute(()->{
    boolean ok=false;
    try{ok=SettingsBackup.restore(SettingsBackup.preferences(app),snapshot,id->AppIcons.apply(app,id));}catch(Exception ignored){}
    final boolean success=ok;main.post(()->{
     if(success){ContactPhotos.clearCache();WidgetSupport.refreshAll(app);}
     update(success?Phase.SUCCESS:Phase.ERROR);
    });
   });
  }
 }
}
