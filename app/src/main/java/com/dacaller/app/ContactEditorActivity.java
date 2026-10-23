package com.dacaller.app;

import android.Manifest;
import android.app.Activity;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.net.Uri;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * First-party editable contact screen. Edits ContactsContract directly using
 * READ_CONTACTS/WRITE_CONTACTS; no other contacts app is launched.
 * All unspecified Data rows are preserved, including fields not exposed here.
 */
public final class ContactEditorActivity extends Activity {
 private static final ExecutorService IO=Executors.newSingleThreadExecutor();
 private final HashMap<String,EditText> controls=new HashMap<>();
 private final HashMap<String,Long> dataIds=new HashMap<>();
 private long contactId=-1, rawId=-1;
 private String extraPhone="";
 private ImageView avatar;
 private TextView avatarText;
 private byte[] newPhoto;
 private long existingPhotoId=-1;
 private android.content.SharedPreferences prefs;
 private boolean dark(){return prefs.getBoolean("dark",false)||prefs.getBoolean("amoled",false);}
 private int background(){return prefs.getBoolean("amoled",false)?Color.BLACK:dark()?0xFF141414:0xFFF7F7F5;}
 private int ink(){return dark()?Color.WHITE:0xFF202020;}
 private int surface(){return dark()?0xFF272727:Color.WHITE;}
 private int muted(){return dark()?0xFFAAAAAA:0xFF777777;}
 private int accent(){return dark()?0xFFE3C68A:0xFF987132;}
 private String tr(String heb){return I18n.text(this,heb);}
 private TextView progress,saveButton;
 private int dp(float value){return (int)(getResources().getDisplayMetrics().density*value+.5f);}
 private GradientDrawable shape(int color,int radius){
  GradientDrawable drawable=new GradientDrawable();drawable.setColor(color);drawable.setCornerRadius(dp(radius));return drawable;
 }
 private TextView text(String value,int size,int color,boolean bold){
  TextView label=new TextView(this);label.setText(tr(value));label.setTextSize(size);label.setTextColor(color);
  if(bold)label.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
  return label;
 }
 @Override public void onCreate(Bundle b){
  super.onCreate(b);
  prefs=getSharedPreferences("da_caller",MODE_PRIVATE);
  contactId=getIntent().getLongExtra("contact_id",-1);
  getWindow().setStatusBarColor(background());getWindow().setNavigationBarColor(background());
  getWindow().getDecorView().setSystemUiVisibility(dark()?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
  extraPhone=getIntent().getStringExtra("incoming_number");
  if(extraPhone==null)extraPhone="";
  createUi();
  if(contactId>0 && checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED){
   requestPermissions(new String[]{Manifest.permission.READ_CONTACTS},42);
  }else load();
 }
 private void createUi(){
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);
  root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);root.setBackgroundColor(background());
  setContentView(root);
  LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);
  toolbar.setPadding(dp(17),dp(9),dp(17),dp(9));root.addView(toolbar,new LinearLayout.LayoutParams(-1,dp(64)));
  TextView close=text("✕",23,ink(),false);close.setGravity(Gravity.CENTER);
  toolbar.addView(close,new LinearLayout.LayoutParams(dp(42),dp(43)));close.setOnClickListener(v->finish());
  TextView title=text(contactId>0?"עריכת איש קשר":"איש קשר חדש",21,ink(),true);
  title.setPadding(dp(12),0,0,0);toolbar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
  TextView mark=text("DA",15,accent(),true);toolbar.addView(mark);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);
  scroll.setVerticalScrollBarEnabled(false);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout form=new LinearLayout(this);form.setOrientation(1);
  form.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
  form.setPadding(dp(17),dp(10),dp(17),dp(22));scroll.addView(form);
  FrameLayout photoHolder=new FrameLayout(this);
  photoHolder.setBackground(shape(dark()?0xFF383328:0xFFEDE1CD,58));
  LinearLayout.LayoutParams holderParams=new LinearLayout.LayoutParams(dp(105),dp(105));
  holderParams.gravity=Gravity.CENTER_HORIZONTAL;holderParams.bottomMargin=dp(9);
  form.addView(photoHolder,holderParams);
  avatarText=text("＋",37,accent(),true);avatarText.setGravity(Gravity.CENTER);
  photoHolder.addView(avatarText,new FrameLayout.LayoutParams(-1,-1));
  avatar=new ImageView(this);avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
  avatar.setClipToOutline(true);avatar.setVisibility(View.GONE);
  photoHolder.addView(avatar,new FrameLayout.LayoutParams(-1,-1));
  photoHolder.setContentDescription(tr("הוספת תמונת איש קשר"));
  photoHolder.setOnClickListener(v->pickPhoto());
  TextView pictureHint=text("הוספת / שינוי תמונה",13,accent(),false);
  pictureHint.setGravity(Gravity.CENTER);
  form.addView(pictureHint,new LinearLayout.LayoutParams(-1,dp(33)));
  pictureHint.setOnClickListener(v->pickPhoto());
  if(contactId>0)ContactPhotos.load(this,contactId,bmp->{
   if(!isFinishing()&&!isDestroyed()&&bmp!=null){avatar.setImageBitmap(bmp);avatar.setVisibility(View.VISIBLE);}
  });
  section(form,"פרטים אישיים");
  field(form,"first","שם פרטי",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS);
  field(form,"last","שם משפחה",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS);
  field(form,"nickname","כינוי",InputType.TYPE_CLASS_TEXT);
  section(form,"מספרי טלפון");
  field(form,"phone0","נייד",InputType.TYPE_CLASS_PHONE);
  field(form,"phone1","בית",InputType.TYPE_CLASS_PHONE);
  field(form,"phone2","עבודה",InputType.TYPE_CLASS_PHONE);
  field(form,"phone3","טלפון נוסף",InputType.TYPE_CLASS_PHONE);
  section(form,"דוא״ל");
  field(form,"email0","כתובת דוא״ל",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
  field(form,"email1","כתובת נוספת",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
  field(form,"email2","כתובת שלישית",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
  section(form,"עבודה");
  field(form,"company","חברה / ארגון",InputType.TYPE_CLASS_TEXT);
  field(form,"job","תפקיד",InputType.TYPE_CLASS_TEXT);
  field(form,"department","מחלקה",InputType.TYPE_CLASS_TEXT);
  section(form,"כתובת");
  field(form,"street","רחוב ומספר בית",InputType.TYPE_CLASS_TEXT);
  field(form,"city","עיר / יישוב",InputType.TYPE_CLASS_TEXT);
  field(form,"region","אזור / מחוז",InputType.TYPE_CLASS_TEXT);
  field(form,"zipcode","מיקוד",InputType.TYPE_CLASS_TEXT);
  field(form,"country","מדינה",InputType.TYPE_CLASS_TEXT);
  section(form,"מידע נוסף");
  field(form,"website","אתר אינטרנט",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
  field(form,"birthday","תאריך יום הולדת (YYYY-MM-DD)",InputType.TYPE_CLASS_TEXT);
  field(form,"anniversary","תאריך לציון (YYYY-MM-DD)",InputType.TYPE_CLASS_TEXT);
  field(form,"notes","הערות",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
  progress=text("",12,accent(),false);form.addView(progress);
  saveButton=text("שמירת איש הקשר",16,Color.WHITE,true);
  saveButton.setGravity(Gravity.CENTER);saveButton.setBackground(shape(0xFF178F61,16));
  LinearLayout.LayoutParams sb=new LinearLayout.LayoutParams(-1,dp(57));sb.setMargins(dp(15),dp(7),dp(15),dp(13));
  root.addView(saveButton,sb);saveButton.setOnClickListener(v->save());
 }
 private void section(LinearLayout parent,String name){
  TextView heading=text(name,16,accent(),true);
  LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(42));
  p.topMargin=dp(10);parent.addView(heading,p);
 }
 private void field(LinearLayout parent,String key,String hint,int inputType){
  EditText entry=new EditText(this);controls.put(key,entry);
  entry.setSingleLine(!key.equals("notes"));entry.setHint(tr(hint));entry.setInputType(inputType);
  entry.setTextSize(15);entry.setTextColor(ink());entry.setHintTextColor(muted());
  entry.setPadding(dp(13),dp(6),dp(13),dp(6));entry.setBackground(shape(surface(),13));
  LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(key.equals("notes")?97:52));
  params.bottomMargin=dp(8);parent.addView(entry,params);
 }
 private void value(String key,String val,long dataId){
  EditText input=controls.get(key);
  if(input!=null){input.setText(val==null?"":val);if(dataId>0)dataIds.put(key,dataId);}
 }
 private String get(String key){
  EditText field=controls.get(key);
  return field==null?"":field.getText().toString().trim();
 }
 @Override public void onRequestPermissionsResult(int r,String[] p,int[] grants){
  super.onRequestPermissionsResult(r,p,grants);
  if(r==42 && grants.length>0 && grants[0]==PackageManager.PERMISSION_GRANTED)load();
  else if(r==45 && grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)save();
  else if(r==42||r==45)Toast.makeText(this,"נדרשת הרשאת אנשי קשר כדי לשמור את השינויים",Toast.LENGTH_LONG).show();
 }
 private void load(){
  if(contactId<1){if(!extraPhone.isEmpty())value("phone0",extraPhone,-1);return;}
  progress.setText("טוען פרטי איש קשר…");
  IO.execute(()->{
   final HashMap<String,String> values=new HashMap<>();
   final HashMap<String,Long> ids=new HashMap<>();
   long raw=-1;
   String[] cols={ContactsContract.Data._ID,ContactsContract.Data.RAW_CONTACT_ID,
     ContactsContract.Data.MIMETYPE,ContactsContract.Data.DATA1,ContactsContract.Data.DATA2,
     ContactsContract.Data.DATA3,ContactsContract.Data.DATA4,ContactsContract.Data.DATA5,
     ContactsContract.Data.DATA7,ContactsContract.Data.DATA8,ContactsContract.Data.DATA9};
   int phoneIndex=0,emailIndex=0;
   try(Cursor cursor=getContentResolver().query(ContactsContract.Data.CONTENT_URI,cols,
     ContactsContract.Data.CONTACT_ID+"=?",new String[]{String.valueOf(contactId)},null)){
    if(cursor!=null)while(cursor.moveToNext()){
     long id=cursor.getLong(0);long rowRaw=cursor.getLong(1);
     if(raw<0)raw=rowRaw;
     String mime=cursor.getString(2);
     String data1=cursor.getString(3),data2=cursor.getString(4),data3=cursor.getString(5);
     if(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE.equals(mime)){
      put(values,ids,"first",data2,id);put(values,ids,"last",data3,id);
      ids.put("name",id);
     }else if(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE.equals(mime)){
      if(phoneIndex<4)put(values,ids,"phone"+phoneIndex++,data1,id);
     }else if(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE.equals(mime)){
      if(emailIndex<3)put(values,ids,"email"+emailIndex++,data1,id);
     }else if(ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE.equals(mime)){
      existingPhotoId=id;
     }else if(ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE.equals(mime)){
      put(values,ids,"nickname",data1,id);
     }else if(ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE.equals(mime)){
      put(values,ids,"company",data1,id);put(values,ids,"job",data2,id);put(values,ids,"department",data3,id);
     }else if(ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE.equals(mime)){
      put(values,ids,"street",cursor.getString(6),id);
      put(values,ids,"city",cursor.getString(7),id);
      put(values,ids,"region",cursor.getString(8),id);
      put(values,ids,"zipcode",cursor.getString(9),id);
      put(values,ids,"country",cursor.getString(10),id);
     }else if(ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE.equals(mime)){
      put(values,ids,"website",data1,id);
     }else if(ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE.equals(mime)){
      put(values,ids,"notes",data1,id);
     }else if(ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE.equals(mime)){
      if("3".equals(data2))put(values,ids,"birthday",data1,id);
      else if("1".equals(data2))put(values,ids,"anniversary",data1,id);
     }
    }
   }catch(Exception ignored){}
   final long loadedRaw=raw;
   runOnUiThread(()->{
    if(isFinishing()||isDestroyed())return;
    rawId=loadedRaw;dataIds.putAll(ids);
    for(Map.Entry<String,String> item:values.entrySet()){
     EditText view=controls.get(item.getKey());if(view!=null)view.setText(item.getValue());
    }
    if(!extraPhone.isEmpty()){
     boolean exists=false;
     for(int i=0;i<4;i++)if(Contacts.canonical(get("phone"+i)).equals(Contacts.canonical(extraPhone)))exists=true;
     if(!exists){
      for(int i=0;i<4;i++)if(get("phone"+i).isEmpty()){controls.get("phone"+i).setText(extraPhone);break;}
     }
    }
    progress.setText("");
   });
  });
 }
 private void pickPhoto(){
  Intent select=new Intent(Intent.ACTION_OPEN_DOCUMENT);
  select.setType("image/*");
  select.addCategory(Intent.CATEGORY_OPENABLE);
  try{startActivityForResult(select,86);}
  catch(Exception e){Toast.makeText(this,tr("לא ניתן לפתוח בוחר תמונות"),Toast.LENGTH_SHORT).show();}
 }
 @Override protected void onActivityResult(int req,int result,Intent data){
  super.onActivityResult(req,result,data);
  if(req!=86||result!=RESULT_OK||data==null||data.getData()==null)return;
  Uri uri=data.getData();
  progress.setText(tr("טוען תמונה…"));
  IO.execute(()->{
   try(InputStream stream=getContentResolver().openInputStream(uri)){
    BitmapFactory.Options options=new BitmapFactory.Options();
    options.inSampleSize=2;
    Bitmap decoded=BitmapFactory.decodeStream(stream,null,options);
    if(decoded==null)throw new Exception("Invalid picture");
    int w=decoded.getWidth(),h=decoded.getHeight();
    float scale=Math.min(1f,512f/Math.max(w,h));
    Bitmap small=scale<1f?Bitmap.createScaledBitmap(decoded,Math.max(1,(int)(w*scale)),
         Math.max(1,(int)(h*scale)),true):decoded;
    ByteArrayOutputStream bytes=new ByteArrayOutputStream();
    small.compress(Bitmap.CompressFormat.JPEG,83,bytes);
    newPhoto=bytes.toByteArray();
    runOnUiThread(()->{
     avatar.setImageBitmap(small);avatar.setVisibility(View.VISIBLE);progress.setText("");
    });
   }catch(Exception error){
    runOnUiThread(()->{progress.setText("");Toast.makeText(this,tr("לא ניתן לטעון תמונה"),Toast.LENGTH_SHORT).show();});
   }
  });
 }
 private static void put(Map<String,String> values,Map<String,Long> ids,String key,String val,long id){
  if(!values.containsKey(key)){values.put(key,val==null?"":val);ids.put(key,id);}
 }
 private static void addData(ArrayList<ContentProviderOperation> operations,long raw,boolean creating,
                              long currentId,String mime,String[] columns,String[] values){
  boolean has=false;
  for(String value:values)if(value!=null&&!value.trim().isEmpty())has=true;
  if(!has&&currentId<1)return;
  ContentProviderOperation.Builder builder;
  if(currentId>0){
   if(!has){
    operations.add(ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
      .withSelection(ContactsContract.Data._ID+"=?",new String[]{String.valueOf(currentId)}).build());return;
   }
   builder=ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
     .withSelection(ContactsContract.Data._ID+"=?",new String[]{String.valueOf(currentId)});
  }else{
   builder=ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI);
   if(creating)builder.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID,0);
   else builder.withValue(ContactsContract.Data.RAW_CONTACT_ID,raw);
   builder.withValue(ContactsContract.Data.MIMETYPE,mime);
  }
  for(int i=0;i<columns.length;i++)builder.withValue(columns[i],values[i]);
  operations.add(builder.build());
 }
 private void save(){
  if(get("first").isEmpty()&&get("last").isEmpty()){
   Toast.makeText(this,"נא להזין שם פרטי או שם משפחה",Toast.LENGTH_SHORT).show();return;
  }
  if(checkSelfPermission(Manifest.permission.WRITE_CONTACTS)!=PackageManager.PERMISSION_GRANTED){
   requestPermissions(new String[]{Manifest.permission.WRITE_CONTACTS},45);return;
  }
  saveButton.setEnabled(false);progress.setText("שומר איש קשר…");
  final HashMap<String,String> values=new HashMap<>();
  for(Map.Entry<String,EditText> e:controls.entrySet())values.put(e.getKey(),e.getValue().getText().toString().trim());
  final HashMap<String,Long> ids=new HashMap<>(dataIds);
  final long useRaw=rawId;
  IO.execute(()->{
   try{
    boolean creating=contactId<1;
    if(!creating&&useRaw<1)throw new Exception("לא נמצא מזהה איש קשר שניתן לעריכה");
    ArrayList<ContentProviderOperation> ops=new ArrayList<>();
    if(creating){
     ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
      .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE,null)
      .withValue(ContactsContract.RawContacts.ACCOUNT_NAME,null).build());
    }
    addData(ops,useRaw,creating,id(ids,"name"),ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
     new String[]{ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
       ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME},
     new String[]{getVal(values,"first"),getVal(values,"last")});
    for(int i=0;i<4;i++){
     String key="phone"+i;
     int type=i==0?ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE:
      i==1?ContactsContract.CommonDataKinds.Phone.TYPE_HOME:
      i==2?ContactsContract.CommonDataKinds.Phone.TYPE_WORK:ContactsContract.CommonDataKinds.Phone.TYPE_OTHER;
     addData(ops,useRaw,creating,id(ids,key),ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
      new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER,ContactsContract.CommonDataKinds.Phone.TYPE},
      new String[]{getVal(values,key),String.valueOf(type)});
    }
    for(int i=0;i<3;i++){
     String key="email"+i;
     int type=i==0?ContactsContract.CommonDataKinds.Email.TYPE_HOME:
        i==1?ContactsContract.CommonDataKinds.Email.TYPE_WORK:ContactsContract.CommonDataKinds.Email.TYPE_OTHER;
     addData(ops,useRaw,creating,id(ids,key),ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
      new String[]{ContactsContract.CommonDataKinds.Email.ADDRESS,ContactsContract.CommonDataKinds.Email.TYPE},
      new String[]{getVal(values,key),String.valueOf(type)});
    }
    addData(ops,useRaw,creating,id(ids,"nickname"),ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE,
     new String[]{ContactsContract.CommonDataKinds.Nickname.NAME},new String[]{getVal(values,"nickname")});
    addData(ops,useRaw,creating,id(ids,"company"),ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE,
     new String[]{ContactsContract.CommonDataKinds.Organization.COMPANY,
      ContactsContract.CommonDataKinds.Organization.TITLE,
      ContactsContract.CommonDataKinds.Organization.DEPARTMENT},
     new String[]{getVal(values,"company"),getVal(values,"job"),getVal(values,"department")});
    addData(ops,useRaw,creating,id(ids,"street"),ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE,
     new String[]{ContactsContract.CommonDataKinds.StructuredPostal.STREET,
      ContactsContract.CommonDataKinds.StructuredPostal.CITY,
      ContactsContract.CommonDataKinds.StructuredPostal.REGION,
      ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE,
      ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY},
     new String[]{getVal(values,"street"),getVal(values,"city"),
      getVal(values,"region"),getVal(values,"zipcode"),getVal(values,"country")});
    addData(ops,useRaw,creating,id(ids,"website"),ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE,
     new String[]{ContactsContract.CommonDataKinds.Website.URL},new String[]{getVal(values,"website")});
    addData(ops,useRaw,creating,id(ids,"notes"),ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE,
     new String[]{ContactsContract.CommonDataKinds.Note.NOTE},new String[]{getVal(values,"notes")});
    addData(ops,useRaw,creating,id(ids,"birthday"),ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
     new String[]{ContactsContract.CommonDataKinds.Event.START_DATE,ContactsContract.CommonDataKinds.Event.TYPE},
     new String[]{getVal(values,"birthday"),String.valueOf(ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY)});
    addData(ops,useRaw,creating,id(ids,"anniversary"),ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
     new String[]{ContactsContract.CommonDataKinds.Event.START_DATE,ContactsContract.CommonDataKinds.Event.TYPE},
     new String[]{getVal(values,"anniversary"),String.valueOf(ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY)});
    android.content.ContentProviderResult[] applied=getContentResolver().applyBatch(ContactsContract.AUTHORITY,ops);
    if(newPhoto!=null){
     long updatedRaw=useRaw;
     if(creating && applied.length>0 && applied[0].uri!=null){
      updatedRaw=android.content.ContentUris.parseId(applied[0].uri);
     }
     if(updatedRaw>0){
      android.content.ContentValues photo=new android.content.ContentValues();
      photo.put(ContactsContract.Data.MIMETYPE,ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE);
      photo.put(ContactsContract.CommonDataKinds.Photo.PHOTO,newPhoto);
      if(existingPhotoId>0){
       getContentResolver().update(ContactsContract.Data.CONTENT_URI,photo,
        ContactsContract.Data._ID+"=?",new String[]{String.valueOf(existingPhotoId)});
      }else{
       photo.put(ContactsContract.Data.RAW_CONTACT_ID,updatedRaw);
       getContentResolver().insert(ContactsContract.Data.CONTENT_URI,photo);
      }
     }
    }
    ContactPhotos.clearCache();
    runOnUiThread(()->{
     Toast.makeText(this,"איש הקשר נשמר בהצלחה",Toast.LENGTH_SHORT).show();
     setResult(RESULT_OK);finish();
    });
   }catch(Exception e){
    String message=e.getMessage()==null?"שגיאה לא ידועה":e.getMessage();
    runOnUiThread(()->{
     saveButton.setEnabled(true);progress.setText("השמירה לא הצליחה");
     Toast.makeText(this,"לא ניתן לשמור: "+message,Toast.LENGTH_LONG).show();
    });
   }
  });
 }
 private static String getVal(HashMap<String,String> vals,String key){
  String result=vals.get(key);return result==null?"":result;
 }
 private static long id(HashMap<String,Long> ids,String key){
  Long value=ids.get(key);return value==null?-1:value;
 }
}
