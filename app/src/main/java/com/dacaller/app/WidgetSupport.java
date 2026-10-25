package com.dacaller.app;

import android.Manifest;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.View;
import android.widget.RemoteViews;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Offline widget rendering. Provider/photo work and bitmap composition never run on the UI thread. */
final class WidgetSupport {
 private static final ExecutorService IO=Executors.newSingleThreadExecutor();
 private static final int[] SLOTS={R.id.fav1,R.id.fav2,R.id.fav3,R.id.fav4,R.id.fav5};
 private static final int[] NAMES={R.id.fav1_name,R.id.fav2_name,R.id.fav3_name,R.id.fav4_name,R.id.fav5_name};
 private static final int[] PHOTOS={R.id.fav1_photo,R.id.fav2_photo,R.id.fav3_photo,R.id.fav4_photo,R.id.fav5_photo};
 static SharedPreferences prefs(Context c){return c.getSharedPreferences("da_caller",0);}
 static boolean dark(Context c){return prefs(c).getBoolean("dark",false)||prefs(c).getBoolean("amoled",false);}
 static int ink(Context c){return dark(c)?0xFFF2F6F1:0xFF202720;}
 static int secondary(Context c){return dark(c)?0xFFBFCBBF:0xFF566356;}
 private static String label(Context c,String he,String en){return I18n.isEnglish(c)?en:he;}
 static void theme(Context c,RemoteViews views,int root){
  views.setInt(root,"setBackgroundResource",prefs(c).getBoolean("amoled",false)?R.drawable.widget_bg_amoled:dark(c)?R.drawable.widget_bg:R.drawable.widget_bg_light);
  views.setInt(root,"setLayoutDirection",I18n.direction(c));
 }
 /** Each identity contains widget, slot, contact and a destination digest; Java hash collisions cannot alias people. */
 static PendingIntent target(Context c,int widgetId,int slot,long contactId,String name,String phone,boolean call){
  Intent intent=new Intent(c,WidgetTap.class).putExtra("contact_id",contactId)
   .putExtra("name",name).putExtra("phone",phone).putExtra("call",call);
  intent.setData(new Uri.Builder().scheme("dacaller").authority("widget")
   .appendPath(Integer.toString(widgetId)).appendPath(Integer.toString(slot))
   .appendPath(call?"call":"contact").appendPath(Long.toString(contactId))
   .appendPath(digest(phone==null?"":phone)).build());
  return PendingIntent.getActivity(c,widgetId,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
 }
 static PendingIntent target(Context c,int id,String name,String phone,boolean call){
  return target(c,id,0,prefs(c).getLong("widget_contact_"+id,-1),name,phone,call);
 }
 private static String digest(String input){
  try{
   byte[] hash=MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
   StringBuilder out=new StringBuilder();for(byte b:hash)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();
  }catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
 }
 static PendingIntent configure(Context c,int id){
  Intent i=new Intent(c,WidgetConfigure.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id)
   .setData(Uri.parse("dacaller://configure/"+id));
  return PendingIntent.getActivity(c,id,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
 }
 private static PendingIntent openFavorites(Context c,int id){
  Intent i=new Intent(c,MainActivity.class).putExtra("favorites",true)
   .setAction("com.dacaller.app.FAVORITES").setData(Uri.parse("dacaller://favorites/"+id));
  return PendingIntent.getActivity(c,id,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
 }
 static void update(Context c,AppWidgetManager m,int id,boolean call){update(c,m,new int[]{id},call,()->{});}
 static void update(Context c,AppWidgetManager manager,int[] ids,boolean call,Runnable completed){
  Context app=c.getApplicationContext();int[] copy=ids.clone();
  IO.execute(()->{try{for(int id:copy)manager.updateAppWidget(id,renderSingle(app,id,call));}finally{completed.run();}});
 }
 /** Called from worker thread; retained package access allows isolated visual tests with synthetic preferences. */
 static RemoteViews renderSingle(Context c,int id,boolean call){
  String name=prefs(c).getString("widget_name_"+id,"");
  String phone=prefs(c).getString("widget_phone_"+id,"");
  long contactId=prefs(c).getLong("widget_contact_"+id,-1);
  boolean configured=call?CallLauncher.isCallable(phone):contactId>0||!phone.isEmpty();
  String display=configured?name:label(c,"בחר איש קשר","Choose contact");
  if(display==null||display.trim().isEmpty())display=phone;
  RemoteViews r=new RemoteViews(c.getPackageName(),call?R.layout.widget_one:R.layout.widget_contact);
  theme(c,r,R.id.widget_root);r.setTextColor(R.id.widget_name,ink(c));r.setTextViewText(R.id.widget_name,display);
  r.setImageViewBitmap(R.id.widget_photo,avatar(c,configured?contactId:-1,display,96));
  String action=configured?label(c,call?"חיוג אל ":"פתיחת איש קשר ",call?"Call ":"Open contact ")+display:display;
  r.setContentDescription(R.id.widget_root,action);
  if(!call){
   r.setTextColor(R.id.widget_subtitle,secondary(c));
   r.setTextViewText(R.id.widget_subtitle,configured?(phone.isEmpty()?label(c,"איש קשר","Contact"):"\u2066"+phone+"\u2069"):label(c,"הקשה להגדרה","Tap to configure"));
  }
  r.setOnClickPendingIntent(R.id.widget_root,configured?target(c,id,0,contactId,display,phone,call):configure(c,id));
  return r;
 }
 static int favoriteCapacity(Bundle options){return options!=null&&options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,0)>=260?5:4;}
 static List<Contacts.Person> favoriteMatches(List<Contacts.Person> people,Set<String> starred){
  ArrayList<Contacts.Person> matches=new ArrayList<>();
  for(Contacts.Person p:people)if(starred.contains(p.key)&&CallLauncher.isCallable(p.phone)){
   matches.add(p);if(matches.size()==5)break;
  }
  return matches;
 }
 static void updateFavorites(Context c,AppWidgetManager manager,int[] ids,Runnable completed){
  Context app=c.getApplicationContext();int[] copy=ids.clone();
  IO.execute(()->{
   try{
    boolean allowed=app.checkSelfPermission(Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED;
    Set<String> starred=new HashSet<>(prefs(app).getStringSet("favorites",Collections.emptySet()));
    List<Contacts.Person> matches=allowed?favoriteMatches(Contacts.all(app),starred):Collections.emptyList();
    for(int id:copy)manager.updateAppWidget(id,renderFavorites(app,id,manager.getAppWidgetOptions(id),matches,allowed));
   }finally{completed.run();}
  });
 }
 static RemoteViews renderFavorites(Context c,int id,Bundle options,List<Contacts.Person> people,boolean contactsAllowed){
  int capacity=favoriteCapacity(options);boolean wide=capacity==5;
  RemoteViews r=new RemoteViews(c.getPackageName(),wide?R.layout.widget_favorites_wide:R.layout.widget_favorites);
  theme(c,r,R.id.widget_favorites_root);
  r.setTextColor(R.id.widget_favorites_title,ink(c));
  r.setTextViewText(R.id.widget_favorites_title,label(c,"מועדפים","Favorites")+"  ·  DA CALLER");
  r.setContentDescription(R.id.widget_favorites_title,label(c,"פתיחת המועדפים","Open favorites"));
  r.setOnClickPendingIntent(R.id.widget_favorites_title,openFavorites(c,id));
  boolean empty=!contactsAllowed||people.isEmpty();
  r.setViewVisibility(R.id.widget_empty,empty?View.VISIBLE:View.GONE);
  r.setViewVisibility(R.id.widget_favorites_grid,empty?View.GONE:View.VISIBLE);
  if(!wide)r.setViewVisibility(R.id.widget_favorites_row2,empty?View.GONE:View.VISIBLE);
  r.setTextColor(R.id.widget_empty,secondary(c));
  r.setTextViewText(R.id.widget_empty,contactsAllowed?label(c,"הוסף מועדפים באפליקציה","Add favorites in the app"):label(c,"נדרשת הרשאת אנשי קשר\nהקשה לאישור באפליקציה","Contacts permission needed\nTap to allow in the app"));
  r.setOnClickPendingIntent(R.id.widget_empty,openFavorites(c,id));
  for(int slot=0;slot<capacity;slot++){
   boolean populated=contactsAllowed&&slot<people.size();
   // Preserve a balanced grid when fewer than four contacts exist; empty cells cannot dial old targets.
   r.setViewVisibility(SLOTS[slot],populated?View.VISIBLE:View.INVISIBLE);
   r.setOnClickPendingIntent(SLOTS[slot],openFavorites(c,id));
   if(!populated)continue;
   Contacts.Person p=people.get(slot);
   r.setTextViewText(NAMES[slot],p.name);r.setTextColor(NAMES[slot],ink(c));
   r.setImageViewBitmap(PHOTOS[slot],avatar(c,p.id,p.name,80));
   r.setContentDescription(SLOTS[slot],label(c,"חיוג אל ","Call ")+p.name);
   r.setOnClickPendingIntent(SLOTS[slot],target(c,id,slot,p.id,p.name,p.phone,true));
  }
  return r;
 }
 /** Thumbnail only, anti-aliased circle and original system-font initials; never accesses a network URI. */
 static Bitmap avatar(Context c,long contactId,String name,int size){
  Bitmap photo=null;
  if(contactId>0&&c.checkSelfPermission(Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED){
   try(InputStream in=ContactsContract.Contacts.openContactPhotoInputStream(c.getContentResolver(),ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI,contactId),false)){
    if(in!=null)photo=BitmapFactory.decodeStream(in);
   }catch(Exception ignored){}
  }
  Bitmap result=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
  Canvas canvas=new Canvas(result);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
  float center=size/2f;
  if(photo!=null&&photo.getWidth()>0&&photo.getHeight()>0){
   BitmapShader shader=new BitmapShader(photo,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);
   float scale=(float)size/Math.min(photo.getWidth(),photo.getHeight());Matrix matrix=new Matrix();
   matrix.setScale(scale,scale);matrix.postTranslate((size-photo.getWidth()*scale)/2f,(size-photo.getHeight()*scale)/2f);
   shader.setLocalMatrix(matrix);p.setShader(shader);canvas.drawCircle(center,center,center,p);
   photo.recycle();
  }else{
   p.setColor(dark(c)?0xFF405646:0xFFDCEADA);canvas.drawCircle(center,center,center,p);
   p.setColor(dark(c)?0xFFECF4E7:0xFF28452E);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextSize(size*.35f);p.setTextAlign(Paint.Align.CENTER);
   Paint.FontMetrics f=p.getFontMetrics();canvas.drawText(initials(name),center,center-(f.ascent+f.descent)/2f,p);
  }
  return result;
 }
 static String initials(String name){
  if(name==null||name.trim().isEmpty())return "?";
  String[] words=name.trim().split("\\s+");String a=words[0];
  String first=a.substring(0,a.offsetByCodePoints(0,1));
  if(words.length==1)return first;
  String last=words[words.length-1];return first+last.substring(0,last.offsetByCodePoints(0,1));
 }
 static void delete(Context c,int[] ids){
  SharedPreferences.Editor e=prefs(c).edit();for(int id:ids)e.remove("widget_contact_"+id).remove("widget_name_"+id).remove("widget_phone_"+id);e.apply();
 }
 static void refreshAll(Context c){
  Context app=c.getApplicationContext();AppWidgetManager manager=AppWidgetManager.getInstance(app);
  int[] quick=manager.getAppWidgetIds(new ComponentName(app,QuickWidget.class));
  int[] cards=manager.getAppWidgetIds(new ComponentName(app,ContactWidget.class));
  if(quick.length>0)update(app,manager,quick,true,()->{});
  if(cards.length>0)update(app,manager,cards,false,()->{});
  FavoritesWidget.refresh(app);
 }
 private WidgetSupport(){}
}
