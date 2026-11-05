package com.dacaller.app;

import static org.junit.Assert.*;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Collections;
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

/** Native font metrics protect the small Contacts entry and its separate touch target. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=33,qualifiers="w320dp-h569dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class ContactsBusinessChipTest {
 public static class Host extends MainActivity {
  boolean contactsGranted=true;
  int directoryOpens,refreshes,calls;
  @Override public int checkSelfPermission(String permission){
   return !contactsGranted&&Manifest.permission.READ_CONTACTS.equals(permission)
     ?PackageManager.PERMISSION_DENIED:PackageManager.PERMISSION_GRANTED;
  }
  @Override void openBusinessDirectory(){directoryOpens++;}
  @Override void reloadContacts(){refreshes++;}
  @Override void dialFromList(String number){calls++;}
 }
 private ActivityController<Host> controller;
 private Host host;
 private SharedPreferences prefs;
 private float originalFontScale;
 @Before public void setup(){
  originalFontScale=RuntimeEnvironment.getApplication().getResources().getConfiguration().fontScale;
  RuntimeEnvironment.setFontScale(1.3f);
  controller=Robolectric.buildActivity(Host.class);host=controller.get();
  prefs=host.getSharedPreferences("da_caller",Context.MODE_PRIVATE);
  prefs.edit().clear().putBoolean("haptics",false).commit();
 }
 @After public void cleanup(){RuntimeEnvironment.setFontScale(originalFontScale);}
 private int dp(int value){return Math.round(value*host.getResources().getDisplayMetrics().density);}
 private View page(String language,boolean permission){
  prefs.edit().putString("language",language).commit();host.contactsGranted=permission;
  View page=new FastPages(host).contacts(Collections.emptyList(),false);
  FrameLayout frame=new FrameLayout(host);frame.addView(page,new FrameLayout.LayoutParams(-1,-1));
  host.setContentView(frame);controller.visible();
  int width=dp(320),height=dp(569);
  frame.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
    View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
  frame.layout(0,0,width,height);Shadows.shadowOf(Looper.getMainLooper()).idle();
  return page;
 }
 private TextView chip(View page){
  TextView chip=page.findViewWithTag("contacts_directory_tab");assertNotNull(chip);return chip;
 }
 private void fullLabelFits(TextView chip,String language){
  assertEquals("en".equals(language)?"Businesses":"בתי עסק",chip.getText().toString());
  assertEquals(1.3f,chip.getResources().getConfiguration().fontScale,.001f);
  assertEquals(dp(48),chip.getHeight());assertTrue(chip.getWidth()>=dp(48));
  assertNotNull(chip.getLayout());assertEquals(1,chip.getLayout().getLineCount());
  assertEquals("The full label must be visible",0,chip.getLayout().getEllipsisCount(0));
  float available=chip.getWidth()-chip.getCompoundPaddingLeft()-chip.getCompoundPaddingRight();
  assertTrue("Full native-measured label must fit: "+chip.getText()+" width="+chip.getPaint().measureText(chip.getText().toString())+" available="+available,
    chip.getPaint().measureText(chip.getText().toString())<=available);
  assertTrue(chip.getLayout().getHeight()<=chip.getHeight()-chip.getCompoundPaddingTop()-chip.getCompoundPaddingBottom());
  assertEquals("en".equals(language)?"Search businesses separately from personal contacts":"חיפוש בתי עסק בנפרד מאנשי הקשר האישיים",
    chip.getContentDescription().toString());
 }
 private Rect bounds(View child){return new Rect(child.getLeft(),child.getTop(),child.getRight(),child.getBottom());}
 private void contained(View child,View parent){
  Rect area=new Rect(0,0,parent.getWidth(),parent.getHeight());assertTrue(child.toString(),area.contains(bounds(child)));
 }
 private void tap(View root,View target){
  // TextView can scroll its single-line text internally; include that offset
  // before converting its drawing bounds to the parent's touch coordinates.
  Rect rect=new Rect();target.getDrawingRect(rect);((ViewGroup)root).offsetDescendantRectToMyCoords(target,rect);
  long now=android.os.SystemClock.uptimeMillis();
  MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,rect.centerX(),rect.centerY(),0);
  root.dispatchTouchEvent(down);down.recycle();
  MotionEvent up=MotionEvent.obtain(now,now+20,MotionEvent.ACTION_UP,rect.centerX(),rect.centerY(),0);
  root.dispatchTouchEvent(up);up.recycle();Shadows.shadowOf(Looper.getMainLooper()).idle();
 }
 @Test public void fullEnglishAndHebrewLabelsFitBesideFlexibleCountAnd48dpRefresh(){
  if(!BuildConfig.BUSINESS_DIRECTORY_ENABLED){for(String language:new String[]{"en","he"}){assertNull(page(language,true).findViewWithTag("contacts_directory_tab"));assertEquals(0,host.calls);assertEquals(0,host.directoryOpens);}return;}
  for(String language:new String[]{"en","he"}){
   View page=page(language,true);TextView chip=chip(page);fullLabelFits(chip,language);
   LinearLayout row=(LinearLayout)chip.getParent();View count=page.findViewWithTag("contacts_personal_tab"),refresh=row.getChildAt(2);
   assertEquals(dp(48),row.getHeight());assertEquals(3,row.getChildCount());
   assertEquals("en".equals(language)?View.LAYOUT_DIRECTION_LTR:View.LAYOUT_DIRECTION_RTL,row.getLayoutDirection());
   assertEquals(1f,((LinearLayout.LayoutParams)count.getLayoutParams()).weight,0f);assertTrue(count.getWidth()>0);
   assertEquals(dp(48),refresh.getWidth());assertEquals(dp(48),refresh.getHeight());
   assertEquals("en".equals(language)?"Refresh contacts":"רענון אנשי קשר",refresh.getContentDescription().toString());
   for(View target:new View[]{count,chip,refresh})contained(target,row);
   assertFalse(Rect.intersects(bounds(count),bounds(chip)));assertFalse(Rect.intersects(bounds(chip),bounds(refresh)));
   assertFalse(Rect.intersects(bounds(count),bounds(refresh)));
  }
 }
 @Test public void onlyExplicitBusinessTouchOpensDirectoryAndRefreshKeepsItsOwnAction(){
  if(!BuildConfig.BUSINESS_DIRECTORY_ENABLED){for(String language:new String[]{"en","he"}){assertNull(page(language,true).findViewWithTag("contacts_directory_tab"));assertEquals(0,host.calls);assertEquals(0,host.directoryOpens);}return;}
  for(String language:new String[]{"en","he"}){
   int opens=host.directoryOpens,refreshes=host.refreshes;
   View page=page(language,true);TextView chip=chip(page);LinearLayout row=(LinearLayout)chip.getParent();
   assertEquals("Rendering personal contacts must not open the business directory",opens,host.directoryOpens);
   tap(page,page.findViewWithTag("contacts_personal_tab"));assertEquals(opens,host.directoryOpens);
   tap(page,row.getChildAt(2));assertEquals(refreshes+1,host.refreshes);assertEquals(opens,host.directoryOpens);
   tap(page,chip);assertEquals(opens+1,host.directoryOpens);assertEquals(refreshes+1,host.refreshes);assertEquals(0,host.calls);
  }
 }
 @Test public void businessEntryAlsoFitsAndOpensWithoutPersonalContactsPermission(){
  if(!BuildConfig.BUSINESS_DIRECTORY_ENABLED){for(String language:new String[]{"en","he"}){assertNull(page(language,false).findViewWithTag("contacts_directory_tab"));assertEquals(0,host.calls);assertEquals(0,host.directoryOpens);}return;}
  for(String language:new String[]{"en","he"}){
   View page=page(language,false);TextView chip=chip(page);fullLabelFits(chip,language);
   assertEquals(dp(48),((View)chip.getParent()).getHeight());contained(chip,(View)chip.getParent());
   int opens=host.directoryOpens;tap(page,chip);assertEquals(opens+1,host.directoryOpens);
   assertEquals(0,host.refreshes);assertEquals(0,host.calls);
  }
 }
}
