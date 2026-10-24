package com.dacaller.app;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Looper;
import android.provider.CallLog;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=33)
public class CallHistoryTest {
 private Application app;
 private Provider provider;
 private static final String[] COLUMNS={"_id","number","date","duration","type"};
 public static class Provider extends ContentProvider {
  int rowCount=1,queries;
  boolean fail,nullCursor,partialFailure;
  CountDownLatch entered,release;
  String sort;
  public boolean onCreate(){return true;}
  public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){
   queries++;sort=order;
   if(entered!=null){entered.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException ex){Thread.currentThread().interrupt();}}
   if(fail)throw new SecurityException("synthetic provider failure");if(nullCursor)return null;
   MatrixCursor rows=new MatrixCursor(COLUMNS){
    @Override public long getLong(int column){if(partialFailure&&getPosition()==1)throw new IllegalStateException("partial cursor");return super.getLong(column);}
   };
   long now=System.currentTimeMillis();
   for(int i=0;i<rowCount;i++)rows.addRow(new Object[]{i,i==0?null:"+97250"+i,now-i, i==0?-5:65,CallLog.Calls.OUTGOING_TYPE});
   return rows;
  }
  public String getType(Uri uri){return null;}
  public Uri insert(Uri uri,ContentValues values){return null;}
  public int delete(Uri uri,String where,String[] args){return 0;}
  public int update(Uri uri,ContentValues values,String where,String[] args){return 0;}
 }
 @Before public void setUp() throws Exception{
  app=RuntimeEnvironment.getApplication();shadowOf(app).grantPermissions(Manifest.permission.READ_CALL_LOG);
  app.deleteDatabase("calls_archive.db");CallArchive.settings(app).edit().clear().putString(CallArchive.PREF,CallArchive.FOREVER).commit();
  provider=new Provider();ShadowContentResolver.registerProviderInternal("call_log",provider);
  CallHistory.worker.submit(()->{}).get(5,TimeUnit.SECONDS);
  CallHistory.entries=Collections.emptyList();CallHistory.summary=new CallHistory.Summary();CallHistory.fetchedAt=0;CallHistory.readFailed=false;
 }
 @After public void tearDown() throws Exception{if(provider.release!=null)provider.release.countDown();awaitWorker();app.deleteDatabase("calls_archive.db");}
 private void awaitWorker() throws Exception{CallHistory.worker.submit(()->{}).get(10,TimeUnit.SECONDS);shadowOf(Looper.getMainLooper()).idle();}
 @Test public void legacyPrivateNumberSentinelsCannotBecomeDialableNumbers(){
  for(String hidden:new String[]{null,"","-1","-2","-3"})assertEquals("",new CallHistory.Entry(1,hidden,1,0,1).number);
  assertEquals("+972-50-1234567",new CallHistory.Entry(1," +972-50-1234567 ",1,0,1).number);
 }
 @Test public void completeLogIsNotTruncatedAndHiddenNumberRemainsUndialable() throws Exception{
  provider.rowCount=1200;CallHistory.refresh(app,true,null);awaitWorker();
  assertEquals(1200,CallHistory.entries.size());assertEquals("",CallHistory.entries.get(0).number);
  assertEquals(0,CallHistory.entries.get(0).duration);assertEquals(1200,CallHistory.summary.calls[3]);
  assertFalse(CallHistory.readFailed);assertFalse(provider.sort.toUpperCase(java.util.Locale.ROOT).contains("LIMIT"));
 }
 @Test public void nullOrPartialCursorCannotArchiveEveryCurrentCall() throws Exception{
  provider.rowCount=2;CallHistory.refresh(app,true,null);awaitWorker();
  provider.nullCursor=true;CallHistory.refresh(app,true,null);awaitWorker();
  assertEquals(2,CallHistory.entries.size());assertTrue(CallHistory.readFailed);assertEquals(0,CallArchive.count(app));
  provider.nullCursor=false;provider.partialFailure=true;CallHistory.refresh(app,true,null);awaitWorker();
  assertEquals(2,CallHistory.entries.size());assertTrue(CallHistory.readFailed);assertEquals(0,CallArchive.count(app));
 }
 @Test public void permissionRevocationClearsSensitiveCachedHistoryAndCallsCallback() throws Exception{
  CallHistory.refresh(app,true,null);awaitWorker();assertEquals(1,CallHistory.entries.size());
  shadowOf(app).denyPermissions(Manifest.permission.READ_CALL_LOG);boolean[] completed={false};
  CallHistory.refresh(app,false,()->completed[0]=true);awaitWorker();
  assertTrue(completed[0]);assertTrue(CallHistory.entries.isEmpty());assertEquals(0,CallHistory.fetchedAt);assertEquals(0,CallArchive.count(app));
 }
 @Test public void revokedPermissionDuringReadCannotRepublishSensitiveHistory() throws Exception{
  provider.entered=new CountDownLatch(1);provider.release=new CountDownLatch(1);
  CallHistory.refresh(app,true,null);assertTrue(provider.entered.await(5,TimeUnit.SECONDS));
  shadowOf(app).denyPermissions(Manifest.permission.READ_CALL_LOG);CallHistory.refresh(app,false,null);
  provider.release.countDown();awaitWorker();assertTrue(CallHistory.entries.isEmpty());
 }
 @Test public void forcedRefreshWhileReadingIsNotLost() throws Exception{
  provider.entered=new CountDownLatch(1);provider.release=new CountDownLatch(1);
  CallHistory.refresh(app,true,null);assertTrue(provider.entered.await(5,TimeUnit.SECONDS));
  CallHistory.refresh(app,true,null);provider.release.countDown();awaitWorker();assertEquals(2,provider.queries);
 }
}
