package com.dacaller.app;

import android.Manifest;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.ContactsContract;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;
import org.robolectric.shadows.ShadowLooper;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=33)
public class ContactPhotosTest {
 private Application app;private PhotoProvider provider;
 @Before public void setup(){
  app=RuntimeEnvironment.getApplication();shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS);
  ContactPhotos.clearCache();provider=new PhotoProvider();
  ProviderInfo info=new ProviderInfo();info.authority=ContactsContract.AUTHORITY;provider.attachInfo(app,info);
  ShadowContentResolver.registerProviderInternal(ContactsContract.AUTHORITY,provider);
 }
 @Test public void simultaneousRequestsForSameContactReadProviderOnce()throws Exception{
  provider.release=new CountDownLatch(1);AtomicInteger callbacks=new AtomicInteger();
  ContactPhotos.load(app,88,image->{assertNotNull(image);callbacks.incrementAndGet();});
  assertTrue(provider.queried.await(3,TimeUnit.SECONDS));
  for(int i=0;i<15;i++)ContactPhotos.load(app,88,image->{assertNotNull(image);callbacks.incrementAndGet();});
  provider.release.countDown();await(()->callbacks.get()==16);
  assertEquals(1,provider.reads.get());
 }
 @Test public void deniedPermissionNeverQueriesProviderAndReturnsNull()throws Exception{
  shadowOf(app).denyPermissions(Manifest.permission.READ_CONTACTS);AtomicInteger callbacks=new AtomicInteger();
  ContactPhotos.load(app,88,image->{assertNull(image);callbacks.incrementAndGet();});await(()->callbacks.get()==1);
  assertEquals(0,provider.reads.get());
 }
 @Test public void invalidationAndPermissionRevocationPreventQueuedCachedPhotoDelivery()throws Exception{
  AtomicInteger callbacks=new AtomicInteger();
  ContactPhotos.load(app,88,image->{assertNotNull(image);callbacks.incrementAndGet();});await(()->callbacks.get()==1);
  AtomicReference<Bitmap> cached=new AtomicReference<>();AtomicInteger deliveries=new AtomicInteger();
  ContactPhotos.load(app,88,image->{cached.set(image);deliveries.incrementAndGet();});
  ContactPhotos.clearCache();shadowOf(app).denyPermissions(Manifest.permission.READ_CONTACTS);
  await(()->deliveries.get()==1);assertNull(cached.get());assertEquals(1,provider.reads.get());
 }
 @Test public void cacheInvalidationDuringProviderReadRetriesBeforeDelivering()throws Exception{
  provider.release=new CountDownLatch(1);AtomicInteger callbacks=new AtomicInteger();
  ContactPhotos.load(app,88,image->{assertNotNull(image);callbacks.incrementAndGet();});
  assertTrue(provider.queried.await(3,TimeUnit.SECONDS));ContactPhotos.clearCache();provider.release.countDown();
  await(()->callbacks.get()==1);assertEquals(2,provider.reads.get());
 }
 private static void await(BooleanSupplier complete)throws Exception{
  long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
  while(!complete.getAsBoolean()&&System.nanoTime()<deadline){ShadowLooper.idleMainLooper();Thread.yield();}
  ShadowLooper.idleMainLooper();assertTrue("Timed out waiting for photo callback",complete.getAsBoolean());
 }
 private static final class PhotoProvider extends ContentProvider {
  final AtomicInteger reads=new AtomicInteger();final CountDownLatch queried=new CountDownLatch(1);
  volatile CountDownLatch release=new CountDownLatch(0);
  private final byte[] image;
  PhotoProvider(){Bitmap bitmap=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888);ByteArrayOutputStream stream=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,stream);image=stream.toByteArray();}
  public boolean onCreate(){return true;}
  public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
   reads.incrementAndGet();queried.countDown();
   try{if(!release.await(3,TimeUnit.SECONDS))throw new IllegalStateException("Photo query stalled");}catch(InterruptedException error){Thread.currentThread().interrupt();return null;}
   MatrixCursor cursor=new MatrixCursor(projection);cursor.addRow(new Object[]{image});return cursor;
  }
  public String getType(Uri uri){return "image/png";}
  public Uri insert(Uri uri,ContentValues values){return null;}
  public int update(Uri uri,ContentValues values,String selection,String[] args){return 0;}
  public int delete(Uri uri,String selection,String[] args){return 0;}
 }
}
