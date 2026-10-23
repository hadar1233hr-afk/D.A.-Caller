package com.dacaller.app;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.util.LruCache;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;

/** Contact names / real photos, loaded asynchronously and cached locally. */
final class ContactPhotos {
 interface Listener {void loaded(Bitmap image);}
 interface LookupListener {void found(long id,String name,Bitmap image);}
 private static final Handler main=new Handler(Looper.getMainLooper());
 private static final ExecutorService io=Executors.newFixedThreadPool(2);
 private static final LruCache<Long,Bitmap> bitmaps=new LruCache<Long,Bitmap>(1024*1024*6){
  @Override protected int sizeOf(Long key,Bitmap bmp){return bmp.getByteCount();}
 };
 private static final ConcurrentHashMap<String,Contact> lookups=new ConcurrentHashMap<>();
 private static final class Contact {
  final long id;final String name;
  Contact(long id,String name){this.id=id;this.name=name;}
 }
 static void clearCache(){lookups.clear();bitmaps.evictAll();}
 static void lookup(Context ctx,String raw,LookupListener listener){
  Context app=ctx.getApplicationContext();
  if(raw==null||raw.isEmpty()||app.checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED){
   main.post(()->listener.found(-1,null,null));return;
  }
  String key=Contacts.canonical(raw);
  Contact cached=lookups.get(key);
  if(cached!=null){
   load(app,cached.id,b->listener.found(cached.id,cached.name,b));return;
  }
  io.execute(()->{
   long id=-1;String name=raw;
   Uri uri=Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,Uri.encode(raw));
   String[] cols={ContactsContract.PhoneLookup._ID,ContactsContract.PhoneLookup.DISPLAY_NAME};
   try(Cursor cursor=app.getContentResolver().query(uri,cols,null,null,null)){
    if(cursor!=null&&cursor.moveToFirst()){id=cursor.getLong(0);name=cursor.getString(1);}
   }catch(Exception ignored){}
   final Contact result=new Contact(id,name);
   lookups.put(key,result);
   load(app,result.id,bitmap->listener.found(result.id,result.name,bitmap));
  });
 }
 static void load(Context ctx,long id,Listener listener){
  if(id<0||ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED){
   main.post(()->listener.loaded(null));return;
  }
  Bitmap cached=bitmaps.get(id);
  if(cached!=null){main.post(()->listener.loaded(cached));return;}
  Context app=ctx.getApplicationContext();
  io.execute(()->{
   Bitmap loaded=null;
   try{
    Uri uri=ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI,id);
    try(InputStream stream=ContactsContract.Contacts.openContactPhotoInputStream(app.getContentResolver(),uri,true)){
     if(stream!=null){
      BitmapFactory.Options options=new BitmapFactory.Options();
      options.inSampleSize=2;
      loaded=BitmapFactory.decodeStream(stream,null,options);
     }
    }
   }catch(Exception ignored){}
   if(loaded!=null)bitmaps.put(id,loaded);
   final Bitmap picture=loaded;
   main.post(()->listener.loaded(picture));
  });
 }
 private ContactPhotos(){}
}
