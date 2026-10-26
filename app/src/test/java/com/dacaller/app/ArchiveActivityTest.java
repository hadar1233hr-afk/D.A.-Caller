package com.dacaller.app;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.provider.ContactsContract;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowContentResolver;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=33)
public class ArchiveActivityTest {
 private Application app;
 private ActivityController<ArchiveActivity> controller;
 private ExecutorService io;
 @Before public void setUp() throws Exception{
  app=RuntimeEnvironment.getApplication();app.deleteDatabase("calls_archive.db");
  shadowOf(app).denyPermissions(Manifest.permission.READ_CONTACTS);ContactPhotos.clearCache();
  shadowOf(app).grantPermissions(Manifest.permission.READ_CALL_LOG);
  CallArchive.settings(app).edit().clear().putString(CallArchive.PREF,CallArchive.FOREVER).putString("language","en").commit();
  long now=System.currentTimeMillis();
  CallArchive.syncAt(app,Arrays.asList(new CallHistory.Entry(1,"+972-50-1111111",now,65,2),new CallHistory.Entry(2,"+972-52-2222222",now-1000,10,1)),now);
  CallArchive.syncAt(app,Collections.emptyList(),now+1);
  Field field=ArchiveActivity.class.getDeclaredField("IO");field.setAccessible(true);io=(ExecutorService)field.get(null);
  controller=Robolectric.buildActivity(ArchiveActivity.class).setup();drain();
 }
 private void drain() throws Exception{
  // Load completion schedules a filter; filter completion updates the UI.
  for(int i=0;i<3;i++){io.submit(()->{}).get(5,TimeUnit.SECONDS);shadowOf(Looper.getMainLooper()).idle();}
 }
 @After public void tearDown() throws Exception{if(controller!=null)controller.pause().stop().destroy();drain();ContactPhotos.clearCache();app.deleteDatabase("calls_archive.db");}
 private static <T extends View>T find(View view,Class<T> type){
  if(type.isInstance(view))return type.cast(view);
  if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){
   T result=find(((ViewGroup)view).getChildAt(i),type);if(result!=null)return result;
  }
  return null;
 }
 private View root(){return controller.get().findViewById(android.R.id.content);}
 private TextView text(View view,String value){
  if(view instanceof TextView&&value.contentEquals(((TextView)view).getText()))return (TextView)view;
  if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){
   TextView result=text(((ViewGroup)view).getChildAt(i),value);if(result!=null)return result;
  }
  return null;
 }
 private ArchiveProvider contacts() throws Exception{
  return contacts(new ArchiveProvider());
 }
 private ArchiveProvider contacts(ArchiveProvider provider) throws Exception{
  ProviderInfo info=new ProviderInfo();info.authority=ContactsContract.AUTHORITY;
  provider.attachInfo(app,info);ShadowContentResolver.registerProviderInternal(ContactsContract.AUTHORITY,provider);
  shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS);
  controller.pause().stop().destroy();controller=Robolectric.buildActivity(ArchiveActivity.class).setup();drain();return provider;
 }
 @Test public void normalizedNumberSearchAndRestorationKeepCorrectArchiveSubset() throws Exception{
  EditText search=find(root(),EditText.class);ListView list=find(root(),ListView.class);
  assertEquals(2,list.getAdapter().getCount());search.setText("972501111111");drain();assertEquals(1,list.getAdapter().getCount());
  Bundle state=new Bundle();controller.saveInstanceState(state).pause().stop().destroy();
  controller=Robolectric.buildActivity(ArchiveActivity.class).create(state).start().resume().visible();drain();
  assertEquals("972501111111",find(root(),EditText.class).getText().toString());
  assertEquals(1,find(root(),ListView.class).getAdapter().getCount());
 }
 @Test public void englishArchiveUsesLtrAndRecycledRowDialogDialsItsBoundNumber() throws Exception{
  ViewGroup content=(ViewGroup)root();assertEquals(View.LAYOUT_DIRECTION_LTR,content.getChildAt(0).getLayoutDirection());
  ListView list=find(root(),ListView.class);View recycled=list.getAdapter().getView(0,null,list);
  recycled=list.getAdapter().getView(1,recycled,list);recycled.performClick();
  AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();assertNotNull(dialog);
  dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle();
  Intent outgoing=shadowOf(controller.get()).getNextStartedActivity();assertEquals(Intent.ACTION_DIAL,outgoing.getAction());
  assertEquals("+972-52-2222222",outgoing.getData().getSchemeSpecificPart());
 }
 @Test public void namesAndOriginalNumbersAppearAndNameSearchCoversEntireArchive() throws Exception{
  contacts();ListView list=find(root(),ListView.class);
  View first=list.getAdapter().getView(0,null,list),second=list.getAdapter().getView(1,null,list);
  assertNotNull(text(first,"Alice Cohen"));assertNotNull(text(first,"+972-50-1111111"));
  assertNotNull(text(second,"דנה לוי"));assertNotNull(text(second,"+972-52-2222222"));
  find(root(),EditText.class).setText("לוי");drain();assertEquals(1,list.getAdapter().getCount());
  assertEquals("+972-52-2222222",((CallHistory.Entry)list.getAdapter().getItem(0)).number);
  find(root(),EditText.class).setText("ALICE");drain();assertEquals(1,list.getAdapter().getCount());
  assertEquals("+972-50-1111111",((CallHistory.Entry)list.getAdapter().getItem(0)).number);
 }
 @Test public void contactCardActionUsesBoundIdentityAndContactEditsRefreshOnReturn() throws Exception{
  ArchiveProvider provider=contacts();ListView list=find(root(),ListView.class);
  View recycled=list.getAdapter().getView(0,null,list);recycled=list.getAdapter().getView(1,recycled,list);
  recycled.performClick();AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
  dialog.getButton(DialogInterface.BUTTON_NEUTRAL).performClick();
  shadowOf(Looper.getMainLooper()).idle();
  Intent card=shadowOf(controller.get()).getNextStartedActivity();
  assertEquals(ContactDetailActivity.class.getName(),card.getComponent().getClassName());assertEquals(99,card.getLongExtra("contact_id",-1));
  provider.aliceName="Alice Updated";controller.pause().resume();drain();
  assertNotNull(text(list.getAdapter().getView(0,null,list),"Alice Updated"));
  find(root(),EditText.class).setText("Updated");drain();assertEquals(1,list.getAdapter().getCount());
 }
 @Test public void permissionRevocationRemovesNamesAndNameBasedSearchWithoutLosingArchive() throws Exception{
  contacts();EditText search=find(root(),EditText.class);ListView list=find(root(),ListView.class);
  search.setText("Alice");drain();assertEquals(1,list.getAdapter().getCount());
  controller.pause();shadowOf(app).denyPermissions(Manifest.permission.READ_CONTACTS);controller.resume();drain();
  assertEquals(0,list.getAdapter().getCount());search.setText("");drain();assertEquals(2,list.getAdapter().getCount());
  View row=list.getAdapter().getView(0,null,list);assertNull(text(row,"Alice Cohen"));assertNotNull(text(row,"+972-50-1111111"));
  assertEquals(2,CallArchive.load(app).size());
 }
 @Test public void nameSearchNeverConvertsLettersIntoUnrelatedT9Number() throws Exception{
  contacts();long now=System.currentTimeMillis();
  CallArchive.syncAt(app,Collections.singletonList(new CallHistory.Entry(3,"+9725425423",now,1,1)),now);
  CallArchive.syncAt(app,Collections.emptyList(),now+1);
  controller.pause().resume();drain();
  ListView list=find(root(),ListView.class);assertEquals(3,list.getAdapter().getCount());
  find(root(),EditText.class).setText("ALICE");drain();
  assertEquals(1,list.getAdapter().getCount());
  assertEquals("+972-50-1111111",((CallHistory.Entry)list.getAdapter().getItem(0)).number);
 }
 @Test public void privateAndUnavailableArchiveEntriesNeverOfferDialOrContactActions() throws Exception{
  contacts();long now=System.currentTimeMillis();
  CallArchive.syncAt(app,Arrays.asList(new CallHistory.Entry(3,"-1",now,0,1),
    new CallHistory.Entry(4,"-2",now,0,1),new CallHistory.Entry(5,"-3",now,0,1),
    new CallHistory.Entry(6,"Unavailable",now,0,1),new CallHistory.Entry(7,"מספר חסוי",now,0,1)),now);
  CallArchive.syncAt(app,Collections.emptyList(),now+1);controller.pause().resume();drain();
  ListView list=find(root(),ListView.class);int checked=0;
  for(int i=0;i<list.getAdapter().getCount();i++){
   CallHistory.Entry entry=(CallHistory.Entry)list.getAdapter().getItem(i);
   if(CallLauncher.isCallable(entry.number))continue;
   list.getAdapter().getView(i,null,list).performClick();AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
   assertEquals(View.GONE,dialog.getButton(DialogInterface.BUTTON_POSITIVE).getVisibility());
   assertEquals(View.GONE,dialog.getButton(DialogInterface.BUTTON_NEUTRAL).getVisibility());
   dialog.dismiss();checked++;
  }
  assertEquals(5,checked);assertNull(shadowOf(controller.get()).getNextStartedActivity());
 }
 @Test public void delayedPhotoForRecycledContactCannotReplaceNewBoundPhoto() throws Exception{
  ArchiveProvider provider=new ArchiveProvider();provider.firstPhotoGate=new CountDownLatch(1);contacts(provider);
  ListView list=find(root(),ListView.class);View recycled=list.getAdapter().getView(0,null,list);
  assertTrue(provider.firstPhotoRead.await(3,TimeUnit.SECONDS));
  recycled=list.getAdapter().getView(1,recycled,list);ImageView photo=find(recycled,ImageView.class);
  await(()->photo.getVisibility()==View.VISIBLE&&photo.getDrawable()!=null);
  assertEquals(Color.BLUE,((BitmapDrawable)photo.getDrawable()).getBitmap().getPixel(0,0));
  AtomicInteger callbacks=new AtomicInteger();ContactPhotos.load(app,88,image->callbacks.incrementAndGet());
  provider.firstPhotoGate.countDown();await(()->callbacks.get()==1);
  assertEquals(Color.BLUE,((BitmapDrawable)photo.getDrawable()).getBitmap().getPixel(0,0));
 }
 private void await(BooleanSupplier complete)throws Exception{
  long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
  while(!complete.getAsBoolean()&&System.nanoTime()<deadline){shadowOf(Looper.getMainLooper()).idle();Thread.yield();}
  shadowOf(Looper.getMainLooper()).idle();assertTrue("Timed out waiting for archived contact photo",complete.getAsBoolean());
 }
 private static final class ArchiveProvider extends ContentProvider {
  volatile String aliceName="Alice Cohen";
  volatile CountDownLatch firstPhotoGate=new CountDownLatch(0);
  final CountDownLatch firstPhotoRead=new CountDownLatch(1),firstPhotoDone=new CountDownLatch(1);
  public boolean onCreate(){return true;}
  public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
   MatrixCursor rows=new MatrixCursor(projection);
   if(uri.getPath().startsWith("/phone_lookup/")){
    String number=uri.getLastPathSegment();
    if("+972501111111".equals(number))rows.addRow(new Object[]{88L,aliceName});
    if("+972522222222".equals(number))rows.addRow(new Object[]{99L,"דנה לוי"});
   }else if(uri.getPath().endsWith("/photo")){
    boolean first=uri.getPath().contains("/88/");
    if(first){firstPhotoRead.countDown();try{firstPhotoGate.await(4,TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}}
    Bitmap image=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888);image.eraseColor(first?Color.RED:Color.BLUE);
    ByteArrayOutputStream output=new ByteArrayOutputStream();image.compress(Bitmap.CompressFormat.PNG,100,output);
    rows.addRow(new Object[]{output.toByteArray()});if(first)firstPhotoDone.countDown();
   }
   return rows;
  }
  public String getType(Uri uri){return null;}
  public Uri insert(Uri uri,ContentValues values){return null;}
  public int update(Uri uri,ContentValues values,String selection,String[] args){return 0;}
  public int delete(Uri uri,String selection,String[] args){return 0;}
 }
}
