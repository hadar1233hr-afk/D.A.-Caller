package com.dacaller.app;

import static org.junit.Assert.*;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Looper;
import android.provider.CallLog;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

/** Real View dispatch and native font geometry; no mocked Telecom success. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=33)
@LooperMode(LooperMode.Mode.PAUSED) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class RecentInlinePresentationTest {
 public static class Host extends MainActivity {
  final List<String> calls=new ArrayList<>(),messages=new ArrayList<>(),created=new ArrayList<>(),contacts=new ArrayList<>();
  final Map<String,String> names=new HashMap<>();
  long details=-1;
  @Override public int checkSelfPermission(String permission){return PackageManager.PERMISSION_GRANTED;}
  @Override void dialFromList(String number){calls.add(number);}
  @Override void sendMessageFromList(String number){messages.add(number);}
  @Override void newContact(String number){created.add(number);}
  @Override void openContactFromNumber(String number){contacts.add(number);}
  @Override void showCallNumber(CallHistory.Entry entry){details=entry.id;}
  @Override Map<String,String> nameIndex(){return names;}
 }
 private ActivityController<Host> controller;
 private Host host;
 private FastPages pages;
 private List<CallHistory.Entry> previous;
 private long previousFetched;
 private float previousFont;
 private CallHistory.Entry entry(long id,String number){return new CallHistory.Entry(id,number,100000+id,42,CallLog.Calls.OUTGOING_TYPE);}
 @Before public void setup(){
  previous=CallHistory.entries;previousFetched=CallHistory.fetchedAt;
  previousFont=RuntimeEnvironment.getApplication().getResources().getConfiguration().fontScale;
  controller=Robolectric.buildActivity(Host.class);host=controller.get();
  host.getSharedPreferences("da_caller",Activity.MODE_PRIVATE).edit().clear().putString("language","en")
   .putBoolean("haptics",false).putBoolean("show_stats",false).putString("swipe_direction","right").commit();
  CallHistory.fetchedAt=1;CallHistory.entries=Arrays.asList(entry(101,"+972 50-123-4567"),entry(102,"*123#"),entry(103,"0507654321"));
  pages=new FastPages(host);
 }
 @After public void cleanup(){CallHistory.entries=previous;CallHistory.fetchedAt=previousFetched;RuntimeEnvironment.setFontScale(previousFont);}
 private int dp(int value){return Math.round(value*host.getResources().getDisplayMetrics().density);}
 private ListView attach(){return attach(dp(360));}
 private ListView attach(int width){
  View page=pages.recents();FrameLayout parent=new FrameLayout(host);parent.addView(page,new FrameLayout.LayoutParams(width,dp(650)));
  host.setContentView(parent);controller.visible();parent.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(dp(650),View.MeasureSpec.EXACTLY));
  parent.layout(0,0,width,dp(650));Shadows.shadowOf(Looper.getMainLooper()).idle();return (ListView)find(page,"call-island-page-list");
 }
 private View find(View node,String tag){
  if(tag.equals(node.getTag()))return node;
  if(node instanceof ViewGroup)for(int i=0;i<((ViewGroup)node).getChildCount();i++){View found=find(((ViewGroup)node).getChildAt(i),tag);if(found!=null)return found;}
  return null;
 }
 private void layout(ListView list){list.measure(View.MeasureSpec.makeMeasureSpec(list.getWidth(),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(list.getHeight(),View.MeasureSpec.EXACTLY));list.layout(list.getLeft(),list.getTop(),list.getRight(),list.getBottom());Shadows.shadowOf(Looper.getMainLooper()).idle();}
 private void tap(ListView list,View target){
  android.graphics.Rect bounds=new android.graphics.Rect();target.getDrawingRect(bounds);list.offsetDescendantRectToMyCoords(target,bounds);
  assertTrue("Visible target "+bounds,bounds.top>=0&&bounds.bottom<=list.getHeight());
  motion(list,MotionEvent.ACTION_DOWN,bounds.exactCenterX(),bounds.exactCenterY(),100);
  motion(list,MotionEvent.ACTION_UP,bounds.exactCenterX(),bounds.exactCenterY(),150);
  Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200));layout(list);
 }
 private void motion(ListView list,int action,float x,float y,long time){MotionEvent event=MotionEvent.obtain(50,time,action,x,y,0);list.dispatchTouchEvent(event);event.recycle();}
 @Test public void touchExpandsOneRowAndBackCollapsesWithoutDialOrDialog(){
  ListView list=attach();View first=list.getChildAt(0),second=list.getChildAt(1);
  tap(list,find(first,"recent_expand"));assertEquals(View.VISIBLE,find(first,"recent_actions").getVisibility());
  assertTrue(host.calls.isEmpty());assertEquals(-1,host.details);
  // Re-read the row after its height changes rather than retaining an adapter position.
  second=list.getChildAt(1);tap(list,find(second,"recent_expand"));
  assertEquals(View.GONE,find(list.getChildAt(0),"recent_actions").getVisibility());assertEquals(View.VISIBLE,find(second,"recent_actions").getVisibility());
  assertTrue(pages.collapseExpandedRecent());layout(list);assertFalse(pages.collapseExpandedRecent());assertTrue(host.calls.isEmpty());
 }
 @Test public void explicitActionsKeepTheUnmodifiedSelectedNumberAndDetailsIdentity(){
  ListView list=attach();View row=list.getChildAt(0);tap(list,find(row,"recent_expand"));
  assertEquals("+972 50-123-4567",((TextView)find(row,"recent_number")).getText().toString());
  tap(list,find(row,"recent_sms"));assertEquals(Collections.singletonList("+972 50-123-4567"),host.messages);assertTrue(host.calls.isEmpty());
  tap(list,find(row,"recent_contact"));assertEquals(Collections.singletonList("+972 50-123-4567"),host.created);assertTrue(host.calls.isEmpty());
  tap(list,find(row,"recent_details"));assertEquals(101,host.details);assertTrue(host.calls.isEmpty());
  tap(list,find(row,"recent_action_call"));assertEquals(Collections.singletonList("+972 50-123-4567"),host.calls);
 }
 @Test public void knownContactUsesItsExistingCardInsteadOfCreatingAnother(){
  host.names.put(Contacts.canonical("+972 50-123-4567"),"Dana");ListView list=attach();View row=list.getChildAt(0);
  tap(list,find(row,"recent_expand"));tap(list,find(row,"recent_contact"));
  assertEquals(Collections.singletonList("+972 50-123-4567"),host.contacts);assertTrue(host.created.isEmpty());assertTrue(host.calls.isEmpty());
 }
 @Test public void contactsArrivingAfterRowBindingCannotOfferADuplicateCard(){
  ListView list=attach();View row=list.getChildAt(0);tap(list,find(row,"recent_expand"));
  host.names.put(Contacts.canonical("+972 50-123-4567"),"Dana");tap(list,find(row,"recent_contact"));
  assertEquals(Collections.singletonList("+972 50-123-4567"),host.contacts);assertTrue(host.created.isEmpty());assertTrue(host.calls.isEmpty());
 }
 @Test public void expandedOwnershipSurvivesSaveAndPrependButNotReusedIdentity(){
  ListView list=attach();tap(list,find(list.getChildAt(1),"recent_expand"));pages.setContactQuery("Dana");Bundle saved=new Bundle();pages.saveState(saved);
  assertEquals(102,saved.getLong("recent_expanded_id"));assertEquals("*123#",saved.getString("recent_expanded_number"));assertEquals("Dana",saved.getString("contact_query"));
  pages=new FastPages(host);pages.restoreState(saved);CallHistory.entries=Arrays.asList(entry(99,"999"),entry(101,"+972 50-123-4567"),entry(102,"*123#"));
  list=attach();assertEquals(View.GONE,find(list.getChildAt(1),"recent_actions").getVisibility());assertEquals(View.VISIBLE,find(list.getChildAt(2),"recent_actions").getVisibility());
  CallHistory.entries=Arrays.asList(entry(102,"555"));list=attach();assertEquals(View.GONE,find(list.getChildAt(0),"recent_actions").getVisibility());assertFalse(pages.collapseExpandedRecent());
 }
 @Test public void recycledRowsCannotRetainAnotherCallsExpandedActions(){
  ListView list=attach();View first=list.getAdapter().getView(0,null,list);find(first,"recent_expand").performClick();
  View recycled=list.getAdapter().getView(1,first,list);assertSame(first,recycled);assertEquals(View.GONE,find(recycled,"recent_actions").getVisibility());
  find(recycled,"recent_call").performClick();assertEquals(Collections.singletonList("*123#"),host.calls);
 }
 @Test public void expansionAndCollapseKeepTheCurrentListAnchor(){
  ArrayList<CallHistory.Entry> entries=new ArrayList<>();for(int i=0;i<1000;i++)entries.add(entry(i+1,"050100"+i));CallHistory.entries=entries;
  ListView list=attach();list.setSelectionFromTop(400,-7);layout(list);int first=list.getFirstVisiblePosition(),offset=list.getChildAt(0).getTop();
  tap(list,find(list.getChildAt(1),"recent_expand"));assertEquals(first,list.getFirstVisiblePosition());assertEquals(offset,list.getChildAt(0).getTop());
  assertTrue(pages.collapseExpandedRecent());layout(list);assertEquals(first,list.getFirstVisiblePosition());assertEquals(offset,list.getChildAt(0).getTop());assertTrue(host.calls.isEmpty());
 }
 @Test public void sameFrameRebuildAndNewTouchCannotReplayAnOlderExpansionAnchor(){
  ArrayList<CallHistory.Entry> entries=new ArrayList<>();for(int i=0;i<1000;i++)entries.add(entry(i+1,"050100"+i));CallHistory.entries=entries;
  ListView list=attach();list.setSelectionFromTop(400,-7);layout(list);long expectedId=list.getAdapter().getItemId(list.getFirstVisiblePosition());
  find(list.getChildAt(1),"recent_expand").performClick();pages.remember("recents");Bundle saved=new Bundle();pages.saveState(saved);
  assertEquals("The pending height layout still owns the real visible anchor",expectedId,saved.getLong("recent_id"));
  motion(list,MotionEvent.ACTION_DOWN,dp(130),dp(150),300);list.setSelectionFromTop(600,-9);layout(list);
  int moved=list.getFirstVisiblePosition();assertTrue(moved>=599);Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(moved,list.getFirstVisiblePosition());
  motion(list,MotionEvent.ACTION_CANCEL,dp(130),dp(150),400);assertTrue(host.calls.isEmpty());
 }
 @Test public void emptyColdStartSnapshotRetainsSavedExpansionUntilActualRowsLoad(){
  ListView list=attach();tap(list,find(list.getChildAt(1),"recent_expand"));Bundle saved=new Bundle();pages.saveState(saved);
  pages=new FastPages(host);pages.restoreState(saved);CallHistory.entries=Collections.emptyList();CallHistory.fetchedAt=0;attach();
  CallHistory.entries=Arrays.asList(entry(101,"+972 50-123-4567"),entry(102,"*123#"));CallHistory.fetchedAt=1;list=attach();
  assertEquals(View.VISIBLE,find(list.getChildAt(1),"recent_actions").getVisibility());assertTrue(host.calls.isEmpty());
 }
 @Test public void hiddenCallerKeepsDetailsButDisablesCallMessageAndCreate(){
  CallHistory.entries=Collections.singletonList(entry(501,"-1"));ListView list=attach();View row=list.getChildAt(0);tap(list,find(row,"recent_expand"));
  for(String tag:new String[]{"recent_call","recent_action_call","recent_sms","recent_contact"})assertFalse(tag,find(row,tag).isEnabled());
  assertTrue(find(row,"recent_details").isEnabled());tap(list,find(row,"recent_details"));assertEquals(501,host.details);assertTrue(host.calls.isEmpty());
 }
 @Test public void gesturesStartingOnInlineControlsNeverBecomeSwipeCalls(){
  ListView list=attach();View row=list.getChildAt(0);tap(list,find(row,"recent_expand"));View control=find(row,"recent_sms");
  android.graphics.Rect bounds=new android.graphics.Rect();control.getDrawingRect(bounds);list.offsetDescendantRectToMyCoords(control,bounds);
  motion(list,MotionEvent.ACTION_DOWN,bounds.exactCenterX(),bounds.exactCenterY(),300);motion(list,MotionEvent.ACTION_MOVE,bounds.exactCenterX()+dp(80),bounds.exactCenterY(),340);
  motion(list,MotionEvent.ACTION_UP,bounds.exactCenterX()+dp(100),bounds.exactCenterY(),380);Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200));
  assertTrue("A drag from Message must not dial: "+host.calls,host.calls.isEmpty());
  assertTrue("A drag must not become a Message tap: "+host.messages,host.messages.isEmpty());
  assertTrue("Fast scroll must be restored after the control gesture",list.isFastScrollEnabled());
 }
 @Test public void englishAndHebrewLargeFontActionsHaveNativeVisibleTextAnd48dpTargets(){
  RuntimeEnvironment.setFontScale(1.3f);
  for(String language:new String[]{"en","he"}){
   host.getSharedPreferences("da_caller",Activity.MODE_PRIVATE).edit().putString("language",language).commit();pages=new FastPages(host);
   ListView list=attach(dp(320));View row=list.getChildAt(0);tap(list,find(row,"recent_expand"));View actions=find(row,"recent_actions");
   assertTrue(actions.getWidth()>0);assertTextFits(actions);
   for(String tag:new String[]{"recent_call","recent_action_call","recent_sms","recent_contact","recent_details","recent_avatar","recent_expand"}){
    View target=find(row,tag);assertTrue(tag+" width="+target.getWidth(),target.getWidth()>=dp(48));assertTrue(tag+" height="+target.getHeight(),target.getHeight()>=dp(48));
   }
   assertTrue(host.calls.isEmpty());
  }
 }
 private void assertTextFits(View node){
  if(node instanceof TextView){TextView text=(TextView)node;assertNotNull(text.getLayout());
   assertTrue(text.getText()+" clipped vertically",text.getLayout().getHeight()<=text.getHeight()-text.getCompoundPaddingTop()-text.getCompoundPaddingBottom());
   for(int i=0;i<text.getLayout().getLineCount();i++)assertEquals(text.getText()+" ellipsized",0,text.getLayout().getEllipsisCount(i));
  }
  if(node instanceof ViewGroup)for(int i=0;i<((ViewGroup)node).getChildCount();i++)assertTextFits(((ViewGroup)node).getChildAt(i));
 }
}
