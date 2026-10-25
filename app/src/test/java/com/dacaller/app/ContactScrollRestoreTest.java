package com.dacaller.app;

import static org.junit.Assert.*;

import android.app.Activity;
import android.os.Bundle;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

/** Contacts/detail/back with a concurrent provider refresh must keep the visible person. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=33)
@LooperMode(LooperMode.Mode.PAUSED)
public class ContactScrollRestoreTest {
 private FastPagesTouchTest.Host host;
 private ActivityController<FastPagesTouchTest.Host> controller;
 private FastPages pages;
 private FrameLayout frame;
 private final List<Contacts.Person> people=new ArrayList<>();
 @Before public void setup(){
  controller=Robolectric.buildActivity(FastPagesTouchTest.Host.class);host=controller.get();
  host.getSharedPreferences("da_caller",Activity.MODE_PRIVATE).edit().clear()
   .putString("language","he").putBoolean("haptics",false).commit();
  pages=new FastPages(host);
  for(int i=0;i<120;i++)people.add(new Contacts.Person("Contact "+i,"050000"+i,i+1));
 }
 private ListView show(List<Contacts.Person> rows){
  View page=pages.contacts(rows,false);frame=new FrameLayout(host);
  frame.addView(page,new FrameLayout.LayoutParams(480,760));host.setContentView(frame);controller.visible();
  layout();return find(page,ListView.class);
 }
 private void layout(){
  frame.measure(View.MeasureSpec.makeMeasureSpec(480,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(760,View.MeasureSpec.EXACTLY));
  frame.layout(0,0,480,760);
 }
 private void settle(){Shadows.shadowOf(Looper.getMainLooper()).idle();layout();}
 private void scroll(ListView list,int position,int offset){list.setSelectionFromTop(position,offset);list.layout(0,0,456,640);}
 private long firstId(ListView list){return list.getAdapter().getItemId(list.getFirstVisiblePosition());}
 private <T extends View>T find(View node,Class<T> type){
  if(type.isInstance(node))return type.cast(node);
  if(node instanceof ViewGroup)for(int i=0;i<((ViewGroup)node).getChildCount();i++){
   T found=find(((ViewGroup)node).getChildAt(i),type);if(found!=null)return found;
  }
  return null;
 }
 @Test public void detailReturnAndTwoRefreshesBeforeRestoreKeepSameContactAndOffset(){
  ListView original=show(people);settle();scroll(original,50,-11);
  long expected=firstId(original);int top=original.getChildAt(0).getTop();assertTrue(expected>1);
  pages.remember("contacts");
  // Reproduce return from detail followed immediately by a provider callback:
  // the new list has laid out at the top but its posted restore has not run.
  ListView transientList=show(people);assertEquals(1,firstId(transientList));pages.restore("contacts");
  pages.remember("contacts");
  ListView returned=show(people);pages.restore("contacts");settle();
  assertEquals("The visible contact ID must survive same-frame page replacement",expected,firstId(returned));
  assertEquals(top,returned.getChildAt(0).getTop());assertTrue(host.calls.isEmpty());
 }
 @Test public void recreatedStateAndChangedProviderOrderRestoreIdRatherThanIndex(){
  ListView original=show(people);settle();scroll(original,45,-8);
  long expected=firstId(original);int top=original.getChildAt(0).getTop(),originalPosition=original.getFirstVisiblePosition();pages.remember("contacts");
  Bundle saved=new Bundle();pages.saveState(saved);pages=new FastPages(host);pages.restoreState(saved);
  List<Contacts.Person> reordered=new ArrayList<>(people);
  for(int i=0;i<7;i++)reordered.add(0,new Contacts.Person("New "+i,"123"+i,1000+i));
  ListView returned=show(reordered);pages.restore("contacts");settle();
  assertEquals(expected,firstId(returned));assertEquals(top,returned.getChildAt(0).getTop());
  assertEquals(originalPosition+7,returned.getFirstVisiblePosition());
 }
 @Test public void emptyLoadingPageDoesNotConsumeRestoredIdentity(){
  ListView original=show(people);settle();scroll(original,35,-6);long expected=firstId(original);pages.remember("contacts");
  ListView loading=show(java.util.Collections.emptyList());pages.restore("contacts");settle();pages.remember("contacts");
  assertEquals(0,loading.getAdapter().getCount());
  ListView returned=show(people);pages.restore("contacts");settle();assertEquals(expected,firstId(returned));
 }
 @Test public void searchDebounceCannotCarryOldQueryAnchorIntoNewFilter(){
  ListView original=show(people);settle();scroll(original,45,-8);pages.remember("contacts");
  find(frame,EditText.class).setText("Contact 11");
  // A provider render before the 80ms filter must reset the new query's anchor.
  pages.remember("contacts");ListView filtered=show(people);pages.restore("contacts");settle();
  assertEquals("Contact 11",find(frame,EditText.class).getText().toString());
  assertEquals(11,filtered.getAdapter().getCount());assertEquals(0,filtered.getFirstVisiblePosition());
  assertEquals(12,firstId(filtered));
 }
 @Test public void userTouchCancelsRestoreAcrossReleaseAndNextFrame(){
  ListView original=show(people);settle();scroll(original,40,-8);pages.remember("contacts");
  ListView returned=show(people);pages.restore("contacts");
  MotionEvent down=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,200,150,0);returned.dispatchTouchEvent(down);down.recycle();
  scroll(returned,65,-5);long expected=firstId(returned);
  MotionEvent cancel=MotionEvent.obtain(0,20,MotionEvent.ACTION_CANCEL,200,150,0);returned.dispatchTouchEvent(cancel);cancel.recycle();
  settle();assertEquals(expected,firstId(returned));
 }
 @Test public void accessibilityScrollCancelsQueuedRestore(){
  ListView original=show(people);settle();scroll(original,40,-8);pages.remember("contacts");
  ListView returned=show(people);pages.restore("contacts");
  returned.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,null);
  scroll(returned,65,-5);long expected=firstId(returned);settle();
  assertEquals(expected,firstId(returned));
 }
}
