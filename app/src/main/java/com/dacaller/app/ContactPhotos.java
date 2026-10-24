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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Bounded thumbnail/name caches and coalesced asynchronous provider reads. */
final class ContactPhotos {
 interface Listener {void loaded(Bitmap image);}
 interface LookupListener {void found(long id,String name,Bitmap image);}
 private static final Handler main=new Handler(Looper.getMainLooper());
 private static final ExecutorService io=Executors.newFixedThreadPool(2);
 private static final Object lock=new Object();
 private static int generation;
 private static final LruCache<Long,Picture> pictures=new LruCache<Long,Picture>(6*1024*1024){
  @Override protected int sizeOf(Long key,Picture picture){return picture.bitmap==null?128:picture.bitmap.getByteCount();}
 };
 private static final LruCache<String,Contact> lookups=new LruCache<>(512);
 private static final HashMap<Long,ArrayList<Listener>> photoRequests=new HashMap<>();
 private static final HashMap<String,ArrayList<LookupListener>> lookupRequests=new HashMap<>();
 private static final class Picture {final Bitmap bitmap;Picture(Bitmap bitmap){this.bitmap=bitmap;}}
 private static final class Contact {
  final long id;final String name;
  Contact(long id,String name){this.id=id;this.name=name;}
 }
 static void clear(){clearCache();}
 static void clearCache(){synchronized(lock){generation++;lookups.evictAll();pictures.evictAll();}}
 static int sampleSize(int width,int height,int max){
  int sample=1;while(width/sample>max*2||height/sample>max*2)sample*=2;return sample;
 }
 static void lookup(Context ctx,String raw,LookupListener listener){
  Context app=ctx.getApplicationContext();
  if(raw==null||raw.trim().isEmpty()||app.checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED){
   main.post(()->listener.found(-1,null,null));return;
  }
  // Use the actual normalized dial string: distinct service/private numbers must not share an empty key.
  String key=android.telephony.PhoneNumberUtils.normalizeNumber(raw);
  if(key.isEmpty())key=raw.trim();
  final String lookupKey=key;final int requestedGeneration;
  synchronized(lock){
   Contact cached=lookups.get(key);
   if(cached!=null){final int cachedGeneration=generation;load(app,cached.id,b->{
    if(valid(app,cachedGeneration))listener.found(cached.id,cached.name,b);else listener.found(-1,null,null);
   });return;}
   ArrayList<LookupListener> pending=lookupRequests.get(key);
   if(pending!=null){pending.add(listener);return;}
   pending=new ArrayList<>();pending.add(listener);lookupRequests.put(key,pending);requestedGeneration=generation;
  }
  io.execute(()->{
   long id=-1;String name=raw;
   Uri uri=Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,Uri.encode(raw));
   try(Cursor cursor=app.getContentResolver().query(uri,new String[]{ContactsContract.PhoneLookup._ID,ContactsContract.PhoneLookup.DISPLAY_NAME},null,null,null)){
    if(cursor!=null&&cursor.moveToFirst()){id=cursor.getLong(0);name=cursor.getString(1);}
   }catch(Exception ignored){}
   Contact result=new Contact(id,name);final ArrayList<LookupListener> callbacks;boolean stale;
   synchronized(lock){stale=generation!=requestedGeneration;callbacks=lookupRequests.remove(lookupKey);if(!stale)lookups.put(lookupKey,result);}
   if(callbacks==null)return;
   if(stale){for(LookupListener callback:callbacks)lookup(app,raw,callback);return;}
   load(app,result.id,bitmap->{for(LookupListener callback:callbacks){
    if(valid(app,requestedGeneration))callback.found(result.id,result.name,bitmap);else callback.found(-1,null,null);
   }});
  });
 }
 static void load(Context ctx,long id,Listener listener){
  Context app=ctx.getApplicationContext();
  if(id<=0||app.checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED){
   main.post(()->listener.loaded(null));return;
  }
  final int requestedGeneration;
  synchronized(lock){
   Picture cached=pictures.get(id);
   if(cached!=null){final int cachedGeneration=generation;main.post(()->listener.loaded(valid(app,cachedGeneration)?cached.bitmap:null));return;}
   ArrayList<Listener> pending=photoRequests.get(id);
   if(pending!=null){pending.add(listener);return;}
   pending=new ArrayList<>();pending.add(listener);photoRequests.put(id,pending);requestedGeneration=generation;
  }
  io.execute(()->{
   Bitmap image=null;
   try(InputStream stream=ContactsContract.Contacts.openContactPhotoInputStream(app.getContentResolver(),ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI,id),false)){
    // Request the provider's thumbnail, not an unbounded full-resolution image for every recycled row.
    if(stream!=null)image=BitmapFactory.decodeStream(stream);
   }catch(Exception ignored){}
   final Bitmap result=image;final ArrayList<Listener> callbacks;boolean stale;
   synchronized(lock){stale=requestedGeneration!=generation;callbacks=photoRequests.remove(id);if(!stale)pictures.put(id,new Picture(result));}
   if(callbacks==null)return;
   if(stale){for(Listener callback:callbacks)load(app,id,callback);return;}
   main.post(()->{for(Listener callback:callbacks)callback.loaded(valid(app,requestedGeneration)?result:null);});
  });
 }
 private static boolean valid(Context context,int expected){
  synchronized(lock){return expected==generation&&context.checkSelfPermission(Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED;}
 }
 private ContactPhotos(){}
}
