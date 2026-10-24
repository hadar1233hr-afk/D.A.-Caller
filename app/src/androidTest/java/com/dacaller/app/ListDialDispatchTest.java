package com.dacaller.app;

import android.content.Intent;
import android.graphics.Rect;
import android.os.SystemClock;
import android.provider.CallLog;
import android.test.InstrumentationTestCase;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Actual Android dispatch regression. Debug fixture records calls and denies CALL_PHONE. */
@SuppressWarnings("deprecation")
public final class ListDialDispatchTest extends InstrumentationTestCase {
 private ListDispatchHostActivity host;
 private FastPages pages;
 private ListView list;
 private List<CallHistory.Entry> original;
 @Override protected void setUp()throws Exception{
  super.setUp();original=CallHistory.entries;
  Intent intent=new Intent(getInstrumentation().getTargetContext(),ListDispatchHostActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
  host=(ListDispatchHostActivity)getInstrumentation().startActivitySync(intent);getInstrumentation().waitForIdleSync();
  ui(()->pages=new FastPages(host));
 }
 @Override protected void tearDown()throws Exception{
  ui(()->{CallHistory.entries=original;host.finish();});getInstrumentation().waitForIdleSync();super.tearDown();
 }
 private void ui(Runnable action)throws Exception{
  AtomicReference<Throwable> error=new AtomicReference<>();
  getInstrumentation().runOnMainSync(()->{try{action.run();}catch(Throwable failure){error.set(failure);}});
  if(error.get()!=null)throw new AssertionError(error.get());
 }
 private ListView find(View node){
  if(node instanceof ListView)return (ListView)node;
  if(node instanceof ViewGroup)for(int i=0;i<((ViewGroup)node).getChildCount();i++){
   ListView found=find(((ViewGroup)node).getChildAt(i));if(found!=null)return found;
  }
  return null;
 }
 private void showContacts()throws Exception{
  ui(()->{
   List<Contacts.Person> people=new ArrayList<>();for(int i=0;i<1000;i++)people.add(new Contacts.Person("Contact "+i,"050000"+i,i+1));
   View page=pages.contacts(people,false);host.setContentView(page);list=find(page);list.setFastScrollAlwaysVisible(true);
  });getInstrumentation().waitForIdleSync();
 }
 private void showRecents()throws Exception{
  ui(()->{
   List<CallHistory.Entry> entries=new ArrayList<>();for(int i=0;i<1000;i++)entries.add(new CallHistory.Entry(i+1,"050100"+i,1000L+i,30,CallLog.Calls.INCOMING_TYPE));
   CallHistory.entries=entries;View page=pages.recents();host.setContentView(page);list=find(page);list.setFastScrollAlwaysVisible(true);
  });getInstrumentation().waitForIdleSync();
 }
 private View callControl(View row){ViewGroup group=(ViewGroup)row;return group.getChildAt(group.getChildCount()-1);}
 private void motion(int action,float x,float y,long start,long at){
  MotionEvent event=MotionEvent.obtain(start,at,action,x,y,0);list.dispatchTouchEvent(event);event.recycle();
 }
 private void tap(View control){
  Rect area=new Rect();control.getDrawingRect(area);list.offsetDescendantRectToMyCoords(control,area);
  long now=SystemClock.uptimeMillis();motion(MotionEvent.ACTION_DOWN,area.exactCenterX(),area.exactCenterY(),now,now);
  motion(MotionEvent.ACTION_UP,area.exactCenterX(),area.exactCenterY(),now,now+50);
 }
 public void testContactsTopMiddleEndDialExactDisplayedNumberWithoutJump()throws Exception{
  showContacts();
  for(int target:new int[]{0,500,999}){
   ui(()->list.setSelectionFromTop(target,0));getInstrumentation().waitForIdleSync();
   final int[] first=new int[1],top=new int[1];final String[] expected=new String[1];
   ui(()->{
    first[0]=list.getFirstVisiblePosition();top[0]=list.getChildAt(0).getTop();int child=target==999?list.getChildCount()-1:0;
    expected[0]=((Contacts.Person)list.getAdapter().getItem(first[0]+child)).phone;tap(callControl(list.getChildAt(child)));
   });getInstrumentation().waitForIdleSync();
   ui(()->{
    assertEquals(expected[0],host.requestedNumbers.get(host.requestedNumbers.size()-1));assertEquals(first[0],list.getFirstVisiblePosition());assertEquals(top[0],list.getChildAt(0).getTop());
   });
  }
  ui(()->assertEquals(3,host.requestedNumbers.size()));
 }
 public void testRecentDialExactNumberAndIndependentDetailsRegion()throws Exception{
  showRecents();ui(()->list.setSelectionFromTop(500,0));getInstrumentation().waitForIdleSync();
  final int[] first=new int[1];final String[] expected=new String[1];
  ui(()->{
   first[0]=list.getFirstVisiblePosition();expected[0]=((CallHistory.Entry)list.getAdapter().getItem(first[0])).number;
   ViewGroup row=(ViewGroup)list.getChildAt(0);tap(row.getChildAt(2));
  });getInstrumentation().waitForIdleSync();ui(()->{assertEquals(1,host.detailsOpened);assertTrue(host.requestedNumbers.isEmpty());tap(callControl(list.getChildAt(0)));});
  getInstrumentation().waitForIdleSync();ui(()->{assertEquals(1,host.requestedNumbers.size());assertEquals(expected[0],host.requestedNumbers.get(0));assertEquals(first[0],list.getFirstVisiblePosition());});
 }
 public void testVerticalFlickStartingAtCallIconNeverCalls()throws Exception{
  showContacts();
  ui(()->{
   View control=callControl(list.getChildAt(2));Rect area=new Rect();control.getDrawingRect(area);list.offsetDescendantRectToMyCoords(control,area);
   float x=area.exactCenterX(),y=area.exactCenterY();long now=SystemClock.uptimeMillis();
   motion(MotionEvent.ACTION_DOWN,x,y,now,now);motion(MotionEvent.ACTION_MOVE,x+2,y-100,now,now+30);motion(MotionEvent.ACTION_UP,x+2,y-140,now,now+60);
  });getInstrumentation().waitForIdleSync();ui(()->assertTrue(host.requestedNumbers.isEmpty()));
 }
 public void testConfiguredHorizontalSwipeCallsCorrectContactOnce()throws Exception{
  showContacts();
  final String[] expected=new String[1];
  ui(()->{
   View row=list.getChildAt(1);expected[0]=((Contacts.Person)list.getAdapter().getItem(list.getFirstVisiblePosition()+1)).phone;
   float x=list.getWidth()/2f,y=row.getTop()+row.getHeight()/2f,delta=60*host.getResources().getDisplayMetrics().density;
   long now=SystemClock.uptimeMillis();motion(MotionEvent.ACTION_DOWN,x,y,now,now);motion(MotionEvent.ACTION_MOVE,x+delta,y+1,now,now+30);motion(MotionEvent.ACTION_UP,x+delta+2,y+2,now,now+60);
  });getInstrumentation().waitForIdleSync();ui(()->{assertEquals(1,host.requestedNumbers.size());assertEquals(expected[0],host.requestedNumbers.get(0));});
 }
 public void testContactRowAndStarHaveIndependentActionsWithoutCalling()throws Exception{
  showContacts();ui(()->tap(((ViewGroup)list.getChildAt(0)).getChildAt(1)));getInstrumentation().waitForIdleSync();
  ui(()->{assertEquals(1,host.contactsOpened);assertTrue(host.requestedNumbers.isEmpty());tap(((ViewGroup)list.getChildAt(0)).getChildAt(2));});
  getInstrumentation().waitForIdleSync();ui(()->{assertEquals(1,host.favoriteChanges);assertTrue(host.requestedNumbers.isEmpty());});
 }

}
