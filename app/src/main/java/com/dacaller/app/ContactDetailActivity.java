package com.dacaller.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Google-like clean contact profile with contact image, number and edit actions. */
public final class ContactDetailActivity extends Activity {
 private static final ExecutorService IO=Executors.newSingleThreadExecutor();
 private android.content.SharedPreferences prefs;
 private long contactId=-1;
 private LinearLayout content;
 private ImageView photo;
 private TextView initials;
 private String title="";
 private int d(float n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
 private boolean dark(){return prefs.getBoolean("dark",false)||prefs.getBoolean("amoled",false);}
 private int page(){return prefs.getBoolean("amoled",false)?Color.BLACK:dark()?0xFF141414:0xFFF8F8F7;}
 private int surface(){return dark()?0xFF242424:Color.WHITE;}
 private int ink(){return dark()?Color.WHITE:0xFF151515;}
 private int muted(){return dark()?0xFFAEAEAE:0xFF737373;}
 private int accent(){return dark()?0xFFE0C385:0xFF88652C;}
 private String tr(String s){return I18n.text(this,s);}
 private GradientDrawable shape(int color,int radius){
  GradientDrawable bg=new GradientDrawable();bg.setColor(color);bg.setCornerRadius(d(radius));return bg;
 }
 private TextView label(String s,int sp,boolean bold,int color){
  TextView t=new TextView(this);t.setText(tr(s));t.setTextSize(sp);t.setTextColor(color);
  t.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));
  t.setGravity(Gravity.CENTER_VERTICAL);t.setIncludeFontPadding(false);return t;
 }
 private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setGravity(Gravity.CENTER_VERTICAL);l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);return l;}
 private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);return l;}
 @Override public void onCreate(Bundle b){
  super.onCreate(b);prefs=getSharedPreferences("da_caller",MODE_PRIVATE);
  contactId=getIntent().getLongExtra("contact_id",-1);
  build();fetch();
 }
 @Override protected void onActivityResult(int request,int result,Intent intent){
  super.onActivityResult(request,result,intent);
  if(request==51){ContactPhotos.clearCache();fetch();}
 }
 private void build(){
  getWindow().setStatusBarColor(page());getWindow().setNavigationBarColor(page());
  getWindow().getDecorView().setSystemUiVisibility(dark()?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
  LinearLayout root=col();root.setBackgroundColor(page());setContentView(root);
  LinearLayout top=row();top.setPadding(d(14),d(7),d(14),d(7));root.addView(top,new LinearLayout.LayoutParams(-1,d(58)));
  TextView back=label("‹",30,false,accent());back.setGravity(Gravity.CENTER);
  top.addView(back,new LinearLayout.LayoutParams(d(45),-1));back.setOnClickListener(v->finish());
  TextView heading=label("כרטיס איש קשר",17,true,ink());top.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
  TextView edit=label("עריכה",15,true,accent());edit.setPadding(d(10),d(10),d(10),d(10));top.addView(edit);
  edit.setOnClickListener(v->editContact());
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
  root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  content=col();content.setPadding(d(17),d(12),d(17),d(22));scroll.addView(content);
 }
 private void fetch(){
  if(contactId<0){finish();return;}
  IO.execute(()->{
   String name="";
   final ArrayList<String[]> phones=new ArrayList<>();
   final ArrayList<String[]> emails=new ArrayList<>();
   final ArrayList<String[]> misc=new ArrayList<>();
   try(Cursor data=getContentResolver().query(ContactsContract.Data.CONTENT_URI,
    new String[]{ContactsContract.Data.MIMETYPE,ContactsContract.Data.DATA1,
      ContactsContract.Data.DATA2,ContactsContract.Data.DATA3},
    ContactsContract.Data.CONTACT_ID+"=?",new String[]{String.valueOf(contactId)},null)){
    if(data!=null)while(data.moveToNext()){
     String mime=data.getString(0),v=data.getString(1),kind=data.getString(2);
     if(v==null||v.trim().isEmpty())continue;
     if(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE.equals(mime)){
      if(name.isEmpty())name=v;
     }else if(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE.equals(mime)){
      phones.add(new String[]{v,kind});
     }else if(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE.equals(mime)){
      emails.add(new String[]{v,kind});
     }else if(ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE.equals(mime)){
      misc.add(new String[]{I18n.text(this,"עבודה"),v});
     }else if(ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE.equals(mime)){
      misc.add(new String[]{I18n.text(this,"הערה"),v});
     }else if(ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE.equals(mime)){
      misc.add(new String[]{I18n.text(this,"כתובת"),v});
     }
    }
   }catch(Exception ignored){}
   if(name.isEmpty()){
    try(Cursor c=getContentResolver().query(ContactsContract.Contacts.CONTENT_URI,
     new String[]{ContactsContract.Contacts.DISPLAY_NAME_PRIMARY},
     ContactsContract.Contacts._ID+"=?",new String[]{String.valueOf(contactId)},null)){
     if(c!=null&&c.moveToFirst())name=c.getString(0);
    }catch(Exception ignored){}
   }
   final String display=name==null||name.isEmpty()?"איש קשר":name;
   runOnUiThread(()->{
    if(isFinishing()||isDestroyed())return;
    title=display;populate(display,phones,emails,misc);
   });
  });
 }
 private void populate(String name,ArrayList<String[]> phones,ArrayList<String[]> emails,ArrayList<String[]> misc){
  content.removeAllViews();
  LinearLayout hero=col();hero.setGravity(Gravity.CENTER);hero.setPadding(d(8),d(13),d(8),d(20));
  content.addView(hero,new LinearLayout.LayoutParams(-1,-2));
  FrameLayout avatar=new FrameLayout(this);
  avatar.setBackground(shape(dark()?0xFF343029:0xFFF1E8D8,70));
  hero.addView(avatar,new LinearLayout.LayoutParams(d(116),d(116)));
  initials=label(name.isEmpty()?"?":name.substring(0,1).toUpperCase(),45,true,accent());
  initials.setGravity(Gravity.CENTER);avatar.addView(initials,new FrameLayout.LayoutParams(-1,-1));
  photo=new ImageView(this);photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
  photo.setBackground(shape(surface(),60));photo.setClipToOutline(true);
  photo.setVisibility(View.GONE);avatar.addView(photo,new FrameLayout.LayoutParams(-1,-1));
  ContactPhotos.load(this,contactId,image->{
   if(isFinishing()||isDestroyed())return;
   if(image!=null){photo.setImageBitmap(image);photo.setVisibility(View.VISIBLE);}
   else photo.setVisibility(View.GONE);
  });
  TextView fullName=label(name,25,true,ink());
  fullName.setGravity(Gravity.CENTER);fullName.setSingleLine(true);
  fullName.setEllipsize(android.text.TextUtils.TruncateAt.END);
  LinearLayout.LayoutParams n=new LinearLayout.LayoutParams(-1,d(55));n.topMargin=d(8);
  hero.addView(fullName,n);
  LinearLayout actions=row();actions.setGravity(Gravity.CENTER);
  hero.addView(actions,new LinearLayout.LayoutParams(-1,d(66)));
  profileAction(actions,"☎",tr("התקשר"),()->{
   if(!phones.isEmpty())call(phones.get(0)[0]);
   else Toast.makeText(this,tr("אין מספר טלפון"),Toast.LENGTH_SHORT).show();
  });
  profileAction(actions,"✎",tr("עריכה"),this::editContact);
  profileAction(actions,"▤",tr("העתק"),()->{
   if(!phones.isEmpty()){
    ClipboardManager clipboard=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
    clipboard.setPrimaryClip(ClipData.newPlainText("phone",phones.get(0)[0]));
    Toast.makeText(this,tr("הועתק"),Toast.LENGTH_SHORT).show();
   }
  });
  if(!phones.isEmpty()){
   section("טלפון");
   for(String[] phone:phones){
    LinearLayout line=row();line.setPadding(d(13),d(10),d(13),d(10));
    line.setBackground(shape(surface(),15));
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,d(75));lp.bottomMargin=d(8);content.addView(line,lp);
    LinearLayout left=col();line.addView(left,new LinearLayout.LayoutParams(0,-2,1));
    left.addView(label(phone[0],17,true,ink()));
    left.addView(label(tr("מספר טלפון"),12,false,muted()));
    ImageView call=new ImageView(this);call.setImageResource(R.drawable.ic_phone_line);
    call.setImageTintList(ColorStateList.valueOf(accent()));call.setPadding(d(11),d(11),d(11),d(11));
    line.addView(call,new LinearLayout.LayoutParams(d(51),d(51)));
    call.setOnClickListener(v->call(phone[0]));left.setOnClickListener(v->new AlertDialog.Builder(this)
     .setTitle(phone[0])
     .setItems(new String[]{tr("התקשר"),tr("העתק")},(dialog,which)->{
      if(which==0)call(phone[0]);
      else{
       ClipboardManager clip=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
       clip.setPrimaryClip(ClipData.newPlainText("number",phone[0]));
      }
     }).show());
   }
  }
  if(!emails.isEmpty()){
   section("דוא״ל");
   for(String[] email:emails)info(email[0],"✉");
  }
  if(!misc.isEmpty()){
   section("פרטים נוספים");
   for(String[] pair:misc)info(pair[0]+" · "+pair[1],"◈");
  }
 }
 private void section(String name){
  TextView section=label(name,15,true,accent());
  LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,d(43));content.addView(section,p);
 }
 private void info(String line,String mark){
  TextView text=label(mark+"    "+line,14,false,ink());
  text.setPadding(d(15),d(15),d(15),d(15));text.setBackground(shape(surface(),15));
  LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=d(7);
  content.addView(text,p);
 }
 private void profileAction(LinearLayout group,String icon,String title,Runnable run){
  LinearLayout col=col();col.setGravity(Gravity.CENTER);
  TextView symbol=label(icon,23,true,accent());symbol.setGravity(Gravity.CENTER);
  col.addView(symbol,new LinearLayout.LayoutParams(-1,d(34)));
  TextView desc=label(title,11,false,ink());desc.setGravity(Gravity.CENTER);
  col.addView(desc,new LinearLayout.LayoutParams(-1,d(23)));
  LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,d(63),1);
  params.setMargins(d(6),0,d(6),0);group.addView(col,params);
  col.setBackground(shape(surface(),15));col.setOnClickListener(v->run.run());
 }
 private void editContact(){
  Intent edit=new Intent(this,ContactEditorActivity.class);
  edit.putExtra("contact_id",contactId);startActivityForResult(edit,51);
 }
 private void call(String number){
  if(number==null||number.isEmpty())return;
  try{startActivity(new Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",number,null)));}
  catch(Exception e){Toast.makeText(this,tr("לא ניתן להתקשר"),Toast.LENGTH_LONG).show();}
 }
}
