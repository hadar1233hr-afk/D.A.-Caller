package com.dacaller.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Safe synthetic contacts/detail/back regression; never accesses actual contacts or Telecom. */
@SuppressWarnings("deprecation")
public final class ContactScrollDispatchTest extends InstrumentationTestCase {
 private ListDispatchHostActivity host;
 private FastPages pages;
 private ListView list;
 private final List<Contacts.Person> contacts=new ArrayList<>();
 @Override protected void setUp()throws Exception{
  super.setUp();
  Intent launch=new Intent(getInstrumentation().getTargetContext(),ListDispatchHostActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
  host=(ListDispatchHostActivity)getInstrumentation().startActivitySync(launch);getInstrumentation().waitForIdleSync();
  for(int i=0;i<120;i++)contacts.add(new Contacts.Person("Contact "+i,"050000"+i,i+1));
  ui(()->{pages=new FastPages(host);show(contacts);});getInstrumentation().waitForIdleSync();
 }
 @Override protected void tearDown()throws Exception{
  if(host!=null)ui(()->host.finish());getInstrumentation().waitForIdleSync();super.tearDown();
 }
 private void ui(Runnable task)throws Exception{
  AtomicReference<Throwable> error=new AtomicReference<>();
  getInstrumentation().runOnMainSync(()->{try{task.run();}catch(Throwable failure){error.set(failure);}});
  if(error.get()!=null)throw new AssertionError(error.get());
 }
 private <T extends View>T find(View node,Class<T> type){
  if(type.isInstance(node))return type.cast(node);
  if(node instanceof ViewGroup)for(int i=0;i<((ViewGroup)node).getChildCount();i++){
   T found=find(((ViewGroup)node).getChildAt(i),type);if(found!=null)return found;
  }
  return null;
 }
 private void show(List<Contacts.Person> rows){View page=pages.contacts(rows,false);host.setContentView(page);list=find(page,ListView.class);}
 private long firstId(){return list.getAdapter().getItemId(list.getFirstVisiblePosition());}
 public void testContactReturnPreservesStableIdentityOffsetAndQueryAfterReorder()throws Exception{
  ui(()->list.setSelectionFromTop(45,-9));getInstrumentation().waitForIdleSync();
  final long[] expected=new long[1];final int[] top=new int[1];
  ui(()->{
   expected[0]=firstId();top[0]=list.getChildAt(0).getTop();pages.remember("contacts");
   // Follow the same page detach/reload/attach sequence as opening a detail card.
   ViewGroup row=(ViewGroup)list.getChildAt(1);row.performClick();
   TextView details=new TextView(host);details.setText("Synthetic contact detail");host.setContentView(details);
   List<Contacts.Person> reordered=new ArrayList<>(contacts);
   reordered.add(0,new Contacts.Person("Inserted contact","1234",5000));
   Bundle saved=new Bundle();pages.saveState(saved);pages=new FastPages(host);pages.restoreState(saved);
   show(reordered);pages.restore("contacts");
  });getInstrumentation().waitForIdleSync();
  ui(()->{
   assertEquals(1,host.contactsOpened);assertEquals(expected[0],firstId());assertEquals(top[0],list.getChildAt(0).getTop());
   assertTrue(host.requestedNumbers.isEmpty());
  });
 }
 public void testRepeatedRebuildBeforeSelectionLayoutDoesNotLoseAnchor()throws Exception{
  ui(()->list.setSelectionFromTop(55,-11));getInstrumentation().waitForIdleSync();
  final long[] expected=new long[1];final int[] top=new int[1];
  ui(()->{
   expected[0]=firstId();top[0]=list.getChildAt(0).getTop();pages.remember("contacts");
   show(contacts); // Force layout of the transient un-restored first row.
   int width=host.getResources().getDisplayMetrics().widthPixels,height=host.getResources().getDisplayMetrics().heightPixels;
   View page=(View)list.getParent();page.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height-120,View.MeasureSpec.EXACTLY));page.layout(0,0,width,height-120);
   pages.restore("contacts");pages.remember("contacts");show(contacts);pages.restore("contacts");
  });getInstrumentation().waitForIdleSync();
  ui(()->{assertEquals(expected[0],firstId());assertEquals(top[0],list.getChildAt(0).getTop());});
 }
 public void testNewTouchCancelsQueuedReturnRestore()throws Exception{
  ui(()->list.setSelectionFromTop(45,-9));getInstrumentation().waitForIdleSync();
  final long[] expected=new long[1];
  ui(()->{
   pages.remember("contacts");pages.restore("contacts");
   long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,list.getWidth()/2f,150,0);
   list.dispatchTouchEvent(down);down.recycle();
   MotionEvent cancel=MotionEvent.obtain(now,now+10,MotionEvent.ACTION_CANCEL,list.getWidth()/2f,150,0);list.dispatchTouchEvent(cancel);cancel.recycle();
   list.setSelectionFromTop(70,-5);expected[0]=contacts.get(70).id;
  });getInstrumentation().waitForIdleSync();ui(()->assertEquals(expected[0],firstId()));
 }
 public void testSearchFilterSurvivesDetailReturnWithoutReset()throws Exception{
  ui(()->find(host.findViewById(android.R.id.content),EditText.class).setText("Contact 11"));
  // Debounced filtering is explicitly awaited before navigating away.
  SystemClock.sleep(100);getInstrumentation().waitForIdleSync();
  ui(()->{pages.remember("contacts");show(contacts);pages.restore("contacts");});getInstrumentation().waitForIdleSync();
  ui(()->{
   assertEquals("Contact 11",find(host.findViewById(android.R.id.content),EditText.class).getText().toString());
   assertEquals(11,list.getAdapter().getCount());assertEquals(12,firstId());assertTrue(host.requestedNumbers.isEmpty());
  });
 }
}
