package com.dacaller.app;

import static org.junit.Assert.*;

import android.app.Activity;
import android.os.Bundle;
import android.os.Looper;
import android.provider.CallLog;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;

/** Real View dispatch; host callback records requests and never invokes telephony. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=33)
public class FastPagesTouchTest {
 public static class Host extends MainActivity {
  final List<String> calls=new ArrayList<>();
  int contactOpened,detailsOpened,favoritesChanged,archivesOpened;
  @Override public int checkSelfPermission(String permission){return android.content.pm.PackageManager.PERMISSION_GRANTED;}
  @Override void openArchive(){archivesOpened++;}
  @Override void dialFromList(String number){calls.add(number);}
  @Override void openContactDetail(long id){contactOpened++;}
  @Override void openContactFromNumber(String number){contactOpened++;}
  @Override void showCallNumber(CallHistory.Entry entry){detailsOpened++;}
  @Override void favoriteFromList(Contacts.Person person){favoritesChanged++;}
  @Override java.util.Map<String,String> nameIndex(){return Collections.emptyMap();}
 }
 private Host host;
 private ActivityController<Host> controller;
 private FastPages pages;
 private List<CallHistory.Entry> previous;
 @Before public void setUp(){
  previous=CallHistory.entries;
  controller=Robolectric.buildActivity(Host.class);host=controller.get();
  host.getSharedPreferences("da_caller",Activity.MODE_PRIVATE).edit().clear()
   .putBoolean("haptics",false).putBoolean("show_stats",false).putString("swipe_direction","right").commit();
  pages=new FastPages(host);
 }
 @After public void tearDown(){CallHistory.entries=previous;}
 private View attach(View page){
  FrameLayout frame=new FrameLayout(host);frame.addView(page,new FrameLayout.LayoutParams(600,700));
  host.setContentView(frame);controller.visible();
  frame.measure(View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(700,View.MeasureSpec.EXACTLY));
  frame.layout(0,0,600,700);
  Shadows.shadowOf(Looper.getMainLooper()).idle();
  return frame;
 }
 private <T extends View> T find(View node,Class<T> type){
  if(type.isInstance(node))return type.cast(node);
  if(node instanceof ViewGroup)for(int i=0;i<((ViewGroup)node).getChildCount();i++){
   T value=find(((ViewGroup)node).getChildAt(i),type);if(value!=null)return value;
  }
  return null;
 }
 private View visibleRow(ListView list){
  for(int i=0;i<list.getChildCount();i++){
   View row=list.getChildAt(i);if(row.getTop()>=0&&row.getBottom()<=list.getHeight())return row;
  }
  throw new AssertionError("No fully visible row");
 }
 private View action(View row){return ((ViewGroup)row).getChildAt(((ViewGroup)row).getChildCount()-1);}
 private void motion(ListView list,int type,float x,float y,long time){
  MotionEvent event=MotionEvent.obtain(100,time,type,x,y,0);list.dispatchTouchEvent(event);event.recycle();
 }
 private void tap(ListView list,View target){
  android.graphics.Rect rect=new android.graphics.Rect();target.getDrawingRect(rect);list.offsetDescendantRectToMyCoords(target,rect);
  assertTrue("Touch must be inside visible list: "+rect,rect.exactCenterY()>=0&&rect.exactCenterY()<list.getHeight());
  motion(list,MotionEvent.ACTION_DOWN,rect.exactCenterX(),rect.exactCenterY(),100);
  motion(list,MotionEvent.ACTION_UP,rect.exactCenterX(),rect.exactCenterY(),150);
  Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200));
 }
 private List<Contacts.Person> people(){
  List<Contacts.Person> people=new ArrayList<>();for(int i=0;i<1000;i++)people.add(new Contacts.Person("Person "+i,"050000"+i,i+1));return people;
 }
 private ListView contacts(){View page=pages.contacts(people(),false);attach(page);return find(page,ListView.class);}
 private ListView recents(){
  List<CallHistory.Entry> rows=new ArrayList<>();for(int i=0;i<1000;i++)rows.add(new CallHistory.Entry(i+1,"050100"+i,1000L+i,12,CallLog.Calls.INCOMING_TYPE));
  CallHistory.entries=rows;View page=pages.recents();attach(page);return find(page,ListView.class);
 }
 @Test public void contactCallButtonDispatchesExactlyOneNumberWithoutMovingList(){
  ListView list=contacts();list.setFastScrollAlwaysVisible(true);list.setSelectionFromTop(400,-9);list.layout(0,0,576,600);
  int first=list.getFirstVisiblePosition(),top=list.getChildAt(0).getTop();
  View row=visibleRow(list);String expected=((Contacts.Person)list.getAdapter().getItem(list.getPositionForView(row))).phone;
  tap(list,action(row));
  assertEquals("scroll before="+first+", after="+list.getFirstVisiblePosition(),Arrays.asList(expected),host.calls);
  assertEquals(first,list.getFirstVisiblePosition());assertEquals(top,list.getChildAt(0).getTop());
 }
 @Test public void recentCallButtonDispatchesExactlyOneNumberWithoutMovingList(){
  ListView list=recents();list.setFastScrollAlwaysVisible(true);list.setSelectionFromTop(600,-11);list.layout(0,0,576,600);
  int first=list.getFirstVisiblePosition(),top=list.getChildAt(0).getTop();
  View row=visibleRow(list);String expected=((CallHistory.Entry)list.getAdapter().getItem(list.getPositionForView(row))).number;
  tap(list,action(row));
  assertEquals("scroll before="+first+", after="+list.getFirstVisiblePosition(),Arrays.asList(expected),host.calls);
  assertEquals(first,list.getFirstVisiblePosition());assertEquals(top,list.getChildAt(0).getTop());
 }
 @Test public void verticalFlickStartingAtDialControlNeverCalls(){
  ListView list=contacts();View button=action(list.getChildAt(0));
  android.graphics.Rect r=new android.graphics.Rect();button.getDrawingRect(r);list.offsetDescendantRectToMyCoords(button,r);
  motion(list,MotionEvent.ACTION_DOWN,r.exactCenterX(),r.exactCenterY(),100);
  motion(list,MotionEvent.ACTION_MOVE,r.exactCenterX()+3,r.exactCenterY()-120,130);
  motion(list,MotionEvent.ACTION_UP,r.exactCenterX()+3,r.exactCenterY()-160,160);
  Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200));
  assertTrue(host.calls.isEmpty());
 }
 @Test public void horizontalSwipeCallsCorrectRowOnce(){
  ListView list=contacts();View row=list.getChildAt(1);float y=row.getTop()+row.getHeight()/2f;
  motion(list,MotionEvent.ACTION_DOWN,220,y,100);
  motion(list,MotionEvent.ACTION_MOVE,360,y+2,130);
  motion(list,MotionEvent.ACTION_UP,390,y+3,160);
  assertEquals(Arrays.asList("0500001"),host.calls);
 }
 @Test public void disabledSwipeDoesNotCall(){
  host.getSharedPreferences("da_caller",Activity.MODE_PRIVATE).edit().putString("swipe_direction","off").commit();
  ListView list=contacts();float y=list.getChildAt(1).getTop()+20;
  motion(list,MotionEvent.ACTION_DOWN,220,y,100);motion(list,MotionEvent.ACTION_MOVE,360,y,130);motion(list,MotionEvent.ACTION_UP,390,y,160);
  assertTrue(host.calls.isEmpty());
 }
 @Test public void stalePostedRestoreCannotJumpAfterTouchStarts(){
  ListView list=contacts();pages.remember("contacts");pages.restore("contacts");
  motion(list,MotionEvent.ACTION_DOWN,220,150,100);
  list.setSelectionFromTop(30,-5);list.layout(0,0,576,600);
  int moved=list.getFirstVisiblePosition();assertTrue(moved>0);
  Shadows.shadowOf(Looper.getMainLooper()).idle();
  assertEquals(moved,list.getFirstVisiblePosition());
  motion(list,MotionEvent.ACTION_CANCEL,220,150,200);
 }
 @Test public void filteredAndRecycledRowsCallDisplayedPerson(){
  View page=pages.contacts(people(),false);attach(page);ListView list=find(page,ListView.class);
  find(page,EditText.class).setText("Person 999");Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
  list.layout(0,0,576,600);tap(list,action(list.getChildAt(0)));
  assertEquals(Arrays.asList("050000999"),host.calls);
 }
 @Test public void recentAvatarDetailsAndCallHaveIndependentActions(){
  ListView list=recents();ViewGroup row=(ViewGroup)list.getChildAt(0);
  tap(list,row.getChildAt(0));assertEquals(1,host.contactOpened);assertTrue(host.calls.isEmpty());
  tap(list,row.getChildAt(2));assertEquals(1,host.detailsOpened);assertTrue(host.calls.isEmpty());
  tap(list,action(row));assertEquals(Arrays.asList("0501000"),host.calls);
 }
 @Test public void swipeBeginningOnDedicatedCallControlDoesNotCall(){
  ListView list=contacts();View target=action(list.getChildAt(1));
  android.graphics.Rect rect=new android.graphics.Rect();target.getDrawingRect(rect);list.offsetDescendantRectToMyCoords(target,rect);
  float x=rect.exactCenterX(),y=rect.exactCenterY();
  motion(list,MotionEvent.ACTION_DOWN,x,y,100);motion(list,MotionEvent.ACTION_MOVE,x+140,y,130);motion(list,MotionEvent.ACTION_UP,x+160,y,160);
  Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200));
  assertTrue(host.calls.isEmpty());assertTrue(list.isFastScrollEnabled());
 }
 @Test public void verticalGestureCannotTurnIntoAccidentalDiagonalCall(){
  ListView list=contacts();float y=list.getChildAt(2).getTop()+20;
  motion(list,MotionEvent.ACTION_DOWN,220,y,100);motion(list,MotionEvent.ACTION_MOVE,222,y-60,130);
  motion(list,MotionEvent.ACTION_MOVE,400,y-65,145);motion(list,MotionEvent.ACTION_UP,420,y-65,160);
  assertTrue(host.calls.isEmpty());
 }
 @Test public void configuredLeftSwipeCallsAndWrongDirectionDoesNot(){
  host.getSharedPreferences("da_caller",Activity.MODE_PRIVATE).edit().putString("swipe_direction","left").commit();
  ListView list=contacts();float y=list.getChildAt(1).getTop()+20;
  motion(list,MotionEvent.ACTION_DOWN,300,y,100);motion(list,MotionEvent.ACTION_MOVE,180,y,130);motion(list,MotionEvent.ACTION_UP,150,y,160);
  assertEquals(Arrays.asList("0500001"),host.calls);host.calls.clear();
  motion(list,MotionEvent.ACTION_DOWN,220,y,200);motion(list,MotionEvent.ACTION_MOVE,360,y,230);motion(list,MotionEvent.ACTION_UP,390,y,260);
  assertTrue(host.calls.isEmpty());
 }
 @Test public void contactSearchSurvivesPageRebuild(){
  View page=pages.contacts(people(),false);attach(page);find(page,EditText.class).setText("Person 999");
  Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
  Bundle state=new Bundle();pages.saveState(state);FastPages recreated=new FastPages(host);recreated.restoreState(state);
  View rebuilt=recreated.contacts(people(),false);attach(rebuilt);
  assertEquals("Person 999",find(rebuilt,EditText.class).getText().toString());
  ListView list=find(rebuilt,ListView.class);assertEquals(1,list.getAdapter().getCount());tap(list,action(list.getChildAt(0)));
  assertEquals(Arrays.asList("050000999"),host.calls);
 }
 @Test public void asynchronousRecentSnapshotReplacementCannotChangeBoundRowNumber(){
  ListView list=recents();CallHistory.entries=Arrays.asList(new CallHistory.Entry(2000,"999",2000,0,CallLog.Calls.OUTGOING_TYPE));
  tap(list,action(list.getChildAt(0)));
  assertEquals(Arrays.asList("0501000"),host.calls);assertEquals(1000,list.getAdapter().getCount());
 }

 @Test public void delayedPhotoForOldContactNeverAppearsInRecycledRow()throws Exception{
  android.app.Application app=host.getApplication();Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.READ_CONTACTS);
  ContactPhotos.clearCache();PhotoProvider provider=new PhotoProvider();android.content.pm.ProviderInfo info=new android.content.pm.ProviderInfo();
  info.authority=android.provider.ContactsContract.AUTHORITY;provider.attachInfo(app,info);
  org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(info.authority,provider);
  View page=pages.contacts(Arrays.asList(new Contacts.Person("Old","0501",1),new Contacts.Person("New","0502",2)),false);
  ListView list=find(page,ListView.class);android.widget.ListAdapter adapter=list.getAdapter();
  View old=adapter.getView(0,null,list);assertTrue(provider.firstRead.await(3,java.util.concurrent.TimeUnit.SECONDS));
  View recycled=adapter.getView(1,old,list);assertSame(old,recycled);
  assertTrue(provider.secondRead.await(3,java.util.concurrent.TimeUnit.SECONDS));
  java.util.concurrent.atomic.AtomicInteger delivered=new java.util.concurrent.atomic.AtomicInteger();
  ContactPhotos.load(host,1,image->delivered.incrementAndGet());ContactPhotos.load(host,2,image->delivered.incrementAndGet());
  long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
  while(delivered.get()<2&&System.nanoTime()<deadline){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.yield();}
  assertEquals(2,delivered.get());
  android.widget.ImageView photo=(android.widget.ImageView)((ViewGroup)((ViewGroup)recycled).getChildAt(0)).getChildAt(1);
  assertEquals(View.GONE,photo.getVisibility());
 }
 @Test public void topMiddleAndEndContactsUseDisplayedNumbersAndKeepPosition(){
  ListView list=contacts();list.setFastScrollAlwaysVisible(true);
  for(int requested:new int[]{0,500,999}){
   // Keep the measured viewport: a forced 600px height beyond the parent
   // shrinks on the next traversal and legitimately reanchors the last row.
   int width=list.getWidth(),height=list.getHeight();
   list.setSelectionFromTop(requested,0);list.layout(0,0,width,height);
   int first=list.getFirstVisiblePosition();View row=visibleRow(list);
   if(requested==999)for(int i=list.getChildCount()-1;i>=0;i--){View candidate=list.getChildAt(i);if(candidate.getTop()>=0&&candidate.getBottom()<=list.getHeight()){row=candidate;break;}}
   String expected=((Contacts.Person)list.getAdapter().getItem(list.getPositionForView(row))).phone;
   tap(list,action(row));
   assertEquals(expected,host.calls.get(host.calls.size()-1));assertEquals(first,list.getFirstVisiblePosition());
  }
  assertEquals(3,host.calls.size());
 }

 @Test public void archiveIsOnlyAnOptionalFinalFooterOutsideCurrentRows(){
  host.getSharedPreferences("da_caller",Activity.MODE_PRIVATE).edit().putString(CallArchive.PREF,CallArchive.FOREVER).commit();
  ListView list=recents();assertEquals(1,list.getFooterViewsCount());assertEquals(1001,list.getAdapter().getCount());
  assertTrue(list.getAdapter().getItem(999) instanceof CallHistory.Entry);assertNull(list.getAdapter().getItem(1000));
  list.getAdapter().getView(1000,null,list).performClick();assertEquals(1,host.archivesOpened);assertTrue(host.calls.isEmpty());
  host.getSharedPreferences("da_caller",Activity.MODE_PRIVATE).edit().putString(CallArchive.PREF,CallArchive.OFF).commit();
  View page=pages.recents();attach(page);ListView disabled=find(page,ListView.class);
  assertEquals(0,disabled.getFooterViewsCount());assertEquals(1000,disabled.getAdapter().getCount());
 }
 @Test public void contactDetailsAndFavoriteStarNeverPlaceCalls(){
  ListView list=contacts();ViewGroup row=(ViewGroup)visibleRow(list);
  tap(list,row.getChildAt(1));assertEquals(1,host.contactOpened);assertTrue(host.calls.isEmpty());
  tap(list,row.getChildAt(2));assertEquals(1,host.favoritesChanged);assertTrue(host.calls.isEmpty());
 }

 private static final class PhotoProvider extends android.content.ContentProvider {
  final java.util.concurrent.CountDownLatch firstRead=new java.util.concurrent.CountDownLatch(1),secondRead=new java.util.concurrent.CountDownLatch(1);
  private final byte[] image;
  PhotoProvider(){android.graphics.Bitmap b=android.graphics.Bitmap.createBitmap(8,8,android.graphics.Bitmap.Config.ARGB_8888);java.io.ByteArrayOutputStream stream=new java.io.ByteArrayOutputStream();b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream);image=stream.toByteArray();}
  public boolean onCreate(){return true;}
  public android.database.Cursor query(android.net.Uri uri,String[] projection,String selection,String[] args,String sort){
   android.database.MatrixCursor cursor=new android.database.MatrixCursor(projection);
   if(uri.toString().contains("/1/")){cursor.addRow(new Object[]{image});firstRead.countDown();}else{secondRead.countDown();}
   return cursor;
  }
  public String getType(android.net.Uri uri){return "image/png";}
  public android.net.Uri insert(android.net.Uri uri,android.content.ContentValues values){return null;}
  public int update(android.net.Uri uri,android.content.ContentValues values,String selection,String[] args){return 0;}
  public int delete(android.net.Uri uri,String selection,String[] args){return 0;}
 }

}
