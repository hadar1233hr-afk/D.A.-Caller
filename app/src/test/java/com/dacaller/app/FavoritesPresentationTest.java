package com.dacaller.app;

import static org.junit.Assert.*;
import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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

/** Presentation controls must never dispatch telephony or lose the favorite dataset. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=33) @LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class FavoritesPresentationTest {
 public static class Host extends MainActivity {
  final List<Contacts.Person> people=new ArrayList<>();
  final List<String> calls=new ArrayList<>();
  long detail=-1;
  int refreshes;
  @Override public int checkSelfPermission(String permission){return PackageManager.PERMISSION_GRANTED;}
  @Override List<Contacts.Person> contactsSnapshot(){return people;}
  @Override void dialFromList(String number){calls.add(number);}
  @Override void openContactDetail(long id){detail=id;}
  @Override void refreshPage(){refreshes++;}
 }
 private ActivityController<Host> controller;
 private Host host;
 private SharedPreferences prefs;
 private FastPages pages;
 private CallHistory.Summary previousSummary;
 private List<CallHistory.Entry> previousEntries;
 @Before public void setup(){
  controller=Robolectric.buildActivity(Host.class);host=controller.get();
  prefs=host.getSharedPreferences("da_caller",Context.MODE_PRIVATE);
  prefs.edit().clear().putBoolean("haptics",false).putString("language","en").commit();
  pages=new FastPages(host);previousSummary=CallHistory.summary;previousEntries=CallHistory.entries;
  CallHistory.entries=Collections.emptyList();
  CallHistory.Summary summary=new CallHistory.Summary();summary.calls[0]=12;summary.seconds[0]=360;
  summary.outgoing[0]=5;summary.incoming[0]=6;summary.missed[0]=1;CallHistory.summary=summary;
 }
 @After public void cleanup(){CallHistory.summary=previousSummary;CallHistory.entries=previousEntries;}
 private void people(int count){
  HashSet<String> favorites=new HashSet<>();
  for(int i=0;i<count;i++){Contacts.Person person=new Contacts.Person("Favorite "+i,"050000"+i,i+1);host.people.add(person);favorites.add(person.key);}
  prefs.edit().putStringSet("favorites",favorites).commit();
 }
 private int dp(int value){return Math.round(value*host.getResources().getDisplayMetrics().density);}
 private View attach(View page){return attach(page,480);}
 private View attach(View page,int width){
  FrameLayout parent=new FrameLayout(host);parent.addView(page,new FrameLayout.LayoutParams(width,720));host.setContentView(parent);controller.visible();
  parent.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY));
  parent.layout(0,0,width,720);Shadows.shadowOf(Looper.getMainLooper()).idle();return page;
 }
 private View tag(View view,String expected){
  if(expected.equals(view.getTag()))return view;
  if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=tag(((ViewGroup)view).getChildAt(i),expected);if(found!=null)return found;}
  return null;
 }
 private boolean hasText(View view,String expected){
  if(view instanceof TextView&&((TextView)view).getText().toString().contains(expected))return true;
  if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)if(hasText(((ViewGroup)view).getChildAt(i),expected))return true;
  return false;
 }
 @Test public void defaultSummaryIsSingleRowAndDedicatedExpandRestoresAllMetrics(){
  View page=attach(pages.recents());View summary=tag(page,"stats_summary");assertNotNull(summary);
  assertEquals(dp(48),summary.getHeight());assertTrue(hasText(summary,"12"));assertTrue(hasText(summary,"6 min"));
  assertNotNull(tag(summary,"stats_toggle"));tag(summary,"stats_toggle").performClick();
  assertTrue(prefs.getBoolean("stats_expanded",false));assertTrue(host.calls.isEmpty());
  page=attach(pages.recents());summary=tag(page,"stats_summary");
  assertTrue(summary.getHeight()>dp(48));assertTrue("Expanded summary remains below150dp: "+summaryGeometry(summary),summary.getHeight()<dp(150));
  assertTrue(hasText(summary,"Outgoing"));assertTrue(hasText(summary,"Incoming"));assertTrue(hasText(summary,"Missed"));assertTrue(hasText(summary,"Average call"));
  assertTrue(hasText(summary,"5"));assertTrue(hasText(summary,"6"));assertTrue(hasText(summary,"1"));
  tag(summary,"stats_toggle").performClick();assertFalse(prefs.getBoolean("stats_expanded",true));
 }
 @Test public void expandedHebrewSummaryFitsNativeLineHeightsAtLargeFont(){
  float original=RuntimeEnvironment.getApplication().getResources().getConfiguration().fontScale;
  RuntimeEnvironment.setFontScale(1.3f);
  try{
   prefs.edit().putString("language","he").putBoolean("stats_expanded",true).commit();
   View summary=tag(attach(pages.recents(),dp(320)),"stats_summary");
   assertTrue("Large-font summary remains below150dp: "+summaryGeometry(summary),summary.getHeight()<dp(150));assertMeasuredTextFits(summary);
  }finally{RuntimeEnvironment.setFontScale(original);}
 }
 private String summaryGeometry(View view){
  StringBuilder geometry=new StringBuilder(view.getClass().getSimpleName()+"["+view.getWidth()+"x"+view.getHeight()+"]");
  if(view instanceof TextView)geometry.append(' ').append(((TextView)view).getText());
  if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)geometry.append(" { ").append(summaryGeometry(((ViewGroup)view).getChildAt(i))).append(" }");
  return geometry.toString();
 }
 private void assertMeasuredTextFits(View view){
  if(view instanceof TextView){
   TextView text=(TextView)view;assertNotNull(text.getLayout());
   assertTrue("Text cropped: "+text.getText()+" line height="+text.getLayout().getHeight()+" view="+text.getHeight(),
    text.getLayout().getHeight()<=text.getHeight()-text.getCompoundPaddingTop()-text.getCompoundPaddingBottom());
  }
  if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)assertMeasuredTextFits(((ViewGroup)view).getChildAt(i));
 }
 @Test public void sameFavoriteBandAppearsAboveBothListsAndRemembersCollapse(){
  people(12);View contacts=attach(pages.contacts(host.people,false));
  View band=tag(contacts,"favorites_strip");assertNotNull(band);assertEquals(dp(82),band.getHeight());assertNotNull(tag(band,"favorites_scroll"));
  tag(band,"favorites_toggle").performClick();assertFalse(prefs.getBoolean("favorites_expanded",true));
  View recents=attach(pages.recents());View collapsed=tag(recents,"favorites_strip");
  assertEquals(dp(48),collapsed.getHeight());assertNull(tag(collapsed,"favorites_scroll"));assertTrue(host.calls.isEmpty());
  tag(collapsed,"favorites_toggle").performClick();assertTrue(prefs.getBoolean("favorites_expanded",false));
 }
 @Test public void thousandFavoritesAreReachableWithoutThousandInflatedTiles(){
  people(1000);View page=attach(pages.contacts(host.people,false));
  HorizontalScrollView scroll=(HorizontalScrollView)tag(page,"favorites_scroll");ViewGroup track=(ViewGroup)scroll.getChildAt(0);
  assertTrue(track.getChildCount()>0);assertTrue(track.getChildCount()<12);assertNotNull(tag(track,"favorite_tile:1"));
  scroll.scrollTo(dp(1000*76),0);Shadows.shadowOf(Looper.getMainLooper()).idle();
  assertTrue(track.getChildCount()<12);View last=tag(track,"favorite_tile:1000");assertNotNull(last);
  last.performClick();assertEquals(Collections.singletonList("050000999"),host.calls);
  last.performLongClick();assertEquals(1000,host.detail);assertEquals(1,host.calls.size());
 }
 @Test public void rtlStartsAtLogicalFirstAndCanReachLogicalLast(){
  prefs.edit().putString("language","he").commit();people(30);View page=attach(pages.contacts(host.people,false));
  HorizontalScrollView scroll=(HorizontalScrollView)tag(page,"favorites_scroll");
  assertTrue(scroll.getScrollX()>0);assertNotNull(tag(scroll,"favorite_tile:1"));
  scroll.scrollTo(0,0);Shadows.shadowOf(Looper.getMainLooper()).idle();
  View last=tag(scroll,"favorite_tile:30");assertNotNull(last);last.performClick();assertEquals(Collections.singletonList("05000029"),host.calls);
 }
 @Test public void horizontalBrowsingDoesNotPlaceACall(){
  people(30);View page=attach(pages.contacts(host.people,false));
  HorizontalScrollView scroll=(HorizontalScrollView)tag(page,"favorites_scroll");
  long start=1000;
  for(int[] point:new int[][]{{MotionEvent.ACTION_DOWN,100},{MotionEvent.ACTION_MOVE,20},{MotionEvent.ACTION_UP,5}}){
   MotionEvent event=MotionEvent.obtain(start,start+=40,point[0],point[1],35,0);scroll.dispatchTouchEvent(event);event.recycle();
  }
  Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300));assertTrue(host.calls.isEmpty());
 }
 @Test public void contactsSharingOneStarredNumberRemainIndividuallyVisibleAndCallable(){
  Contacts.Person first=new Contacts.Person("Dana","0500012345",51);
  Contacts.Person second=new Contacts.Person("Family line","0500012345",52);
  host.people.add(first);host.people.add(second);
  prefs.edit().putStringSet("favorites",Collections.singleton(first.key)).commit();
  View page=attach(pages.contacts(host.people,false));
  View a=tag(page,"favorite_tile:51"),b=tag(page,"favorite_tile:52");
  assertNotNull("The first starred contact must remain visible",a);
  assertNotNull("A shared number must not hide the second contact",b);
  assertTrue(hasText(a,"Dana"));assertTrue(hasText(b,"Family line"));
  assertTrue(host.calls.isEmpty());a.performClick();b.performClick();
  assertEquals(java.util.Arrays.asList(first.phone,second.phone),host.calls);
  assertEquals(Collections.singleton(first.key),prefs.getStringSet("favorites",Collections.emptySet()));
 }
 @Test public void favoriteScrollSurvivesRecreationAndEmptyFavoritesRemainUseful(){
  people(30);View page=attach(pages.contacts(host.people,false));HorizontalScrollView scroll=(HorizontalScrollView)tag(page,"favorites_scroll");
  scroll.scrollTo(dp(10*76),0);Bundle saved=new Bundle();pages.saveState(saved);
  pages=new FastPages(host);pages.restoreState(saved);page=attach(pages.contacts(host.people,false));
  assertEquals(dp(10*76),((HorizontalScrollView)tag(page,"favorites_scroll")).getScrollX());
  prefs.edit().putStringSet("favorites",Collections.emptySet()).commit();page=attach(pages.contacts(host.people,false));
  assertEquals(dp(48),tag(page,"favorites_strip").getHeight());assertTrue(hasText(page,"Star contacts"));
 }
 @Test public void legacyFavoriteStartAndWidgetIntentOpenContactsWithoutLosingSavedPreference()throws Exception{
  prefs.edit().putBoolean("role_prompted",true).putString("start_page","favorites").putBoolean("favorites_expanded",false).commit();
  Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.READ_CONTACTS,Manifest.permission.READ_CALL_LOG);
  try(ActivityController<MainActivity> main=Robolectric.buildActivity(MainActivity.class).setup()){
   MainActivity activity=main.get();Bundle saved=new Bundle();activity.onSaveInstanceState(saved);
   assertEquals("contacts",saved.getString("page"));assertEquals("favorites",prefs.getString("start_page",""));assertTrue(prefs.getBoolean("favorites_expanded",false));
   java.lang.reflect.Field field=MainActivity.class.getDeclaredField("nav");field.setAccessible(true);
   assertEquals(3,((LinearLayout)field.get(activity)).getChildCount());
   prefs.edit().putBoolean("favorites_expanded",false).commit();activity.onNewIntent(new Intent().putExtra("favorites",true));
   activity.onSaveInstanceState(saved);assertEquals("contacts",saved.getString("page"));assertTrue(prefs.getBoolean("favorites_expanded",false));
  }
 }
}
