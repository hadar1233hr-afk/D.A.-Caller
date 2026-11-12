package com.dacaller.app;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowAlertDialog;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=33)
@LooperMode(LooperMode.Mode.PAUSED)
public class BusinessDirectoryViewTest {
 private ActivityController<Activity> controller;private Activity activity;private BusinessDirectoryView directory;private View root;
 private final List<String> dialled=new ArrayList<>();private int personal;
 private final Tasks tasks=new Tasks();
 private final BusinessDirectory.Manifest metadata=new BusinessDirectory.Manifest("test",zeros(),5000,"Test open source","https://example.org/source","Test license","https://example.org/license","2026-11-02");
 private static String zeros(){return String.join("",Collections.nCopies(64,"0"));}
 private BusinessDirectory.Entry entry(long id){return new BusinessDirectory.Entry(id,"Test business "+id,"05000999"+String.format(java.util.Locale.ROOT,"%02d",id%100),"05000999"+String.format(java.util.Locale.ROOT,"%02d",id%100),"Test city","Test category","Test address","https://example.org/business/"+id,"test/"+id,"Test license","2026-11-02");}
 private BusinessDirectory.Page page(int offset,boolean more,BusinessDirectory.Entry... values){return new BusinessDirectory.Page(BusinessDirectory.Status.AVAILABLE,metadata,Arrays.asList(values),offset,more);}
 @Before public void setup(){
  activity=Robolectric.buildActivity(Activity.class).setup().get();
  shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.READ_CONTACTS,Manifest.permission.READ_CALL_LOG);
 }
 @After public void close(){if(directory!=null)directory.close();if(activity!=null)activity.finish();}
 private void show(BusinessDirectoryView.Loader loader){directory=new BusinessDirectoryView(activity,()->personal++,dialled::add,loader,tasks);root=directory.create();activity.setContentView(root);}
 private void complete(){tasks.runFirst();shadowOf(Looper.getMainLooper()).idle();}
 private ListView list(){return (ListView)root.findViewWithTag("call-island-page-list");}
 private View row(int dataIndex){return list().getAdapter().getView(list().getHeaderViewsCount()+dataIndex,null,list());}
 @Test public void noContactsPermissionNeededAndTypingNeverDials(){
  show((context,query,offset)->page(0,false,entry(1)));complete();
  assertEquals(3,list().getAdapter().getCount());assertTrue(dialled.isEmpty());
  EditText search=(EditText)root.findViewWithTag("business_directory_search");search.setText("Test");
  shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(160));assertTrue(dialled.isEmpty());complete();
  assertEquals("Test",search.getText().toString());assertTrue(dialled.isEmpty());
  root.findViewWithTag("business_directory_personal_tab").performClick();assertEquals(1,personal);assertTrue(dialled.isEmpty());
 }
 @Test public void recycledRowCallsOnlyItsBoundVisibleBusiness(){
  show((context,query,offset)->page(0,false,entry(1),entry(2)));complete();
  View reused=row(0);reused=list().getAdapter().getView(list().getHeaderViewsCount()+1,reused,list());
  assertTrue(reused.findViewWithTag("business_directory_call_2").performClick());
  assertEquals(Collections.singletonList(entry(2).phone),dialled);
 }
 @Test public void changingQueryDisarmsOldRowBeforeDebouncedResultsArrive(){
  show((context,query,offset)->page(0,false,query.isEmpty()?entry(1):entry(2)));complete();
  View oldCall=row(0).findViewWithTag("business_directory_call_1");
  ((EditText)root.findViewWithTag("business_directory_search")).setText("new");oldCall.performClick();assertTrue(dialled.isEmpty());
  shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(160));complete();
  row(0).findViewWithTag("business_directory_call_2").performClick();assertEquals(Collections.singletonList(entry(2).phone),dialled);
 }
 @Test public void staleBackgroundResultsCannotReplaceNewQuery(){
  show((context,query,offset)->page(0,false,query.isEmpty()?entry(1):entry(2)));
  ((EditText)root.findViewWithTag("business_directory_search")).setText("new");shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(160));
  assertEquals(2,tasks.values.size());tasks.runLast();shadowOf(Looper.getMainLooper()).idle();
  tasks.runFirst();shadowOf(Looper.getMainLooper()).idle();
  BusinessDirectory.Entry actual=(BusinessDirectory.Entry)list().getAdapter().getItem(list().getHeaderViewsCount());assertEquals(2,actual.id);
 }
 @Test public void unavailableDatasetShowsHonestStateAndPersonalTabStillWorks(){
  show((context,query,offset)->BusinessDirectory.Page.unavailable(BusinessDirectory.Status.MISSING));complete();
  assertEquals(2,list().getAdapter().getCount());
  assertTrue(((TextView)root.findViewWithTag("business_directory_empty")).getText().toString().contains("עדיין לא צורף"));
  assertFalse(root.findViewWithTag("business_directory_next").isEnabled());assertFalse(root.findViewWithTag("business_directory_about").isEnabled());
  root.findViewWithTag("business_directory_personal_tab").performClick();assertEquals(1,personal);assertTrue(dialled.isEmpty());
 }
 @Test public void pageAndSearchStateSurviveRecreationWithoutLoadingEarlierPages(){
  List<String> queries=new ArrayList<>();List<Integer> offsets=new ArrayList<>();
  BusinessDirectoryView.Loader loader=(context,query,offset)->{queries.add(query);offsets.add(offset);return page(offset,offset==0,entry(offset+1));};
  show(loader);complete();((EditText)root.findViewWithTag("business_directory_search")).setText("Test");shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(160));complete();
  root.findViewWithTag("business_directory_next").performClick();complete();Bundle saved=new Bundle();directory.saveState(saved);
  assertEquals("Test",saved.getString("business_query"));assertEquals(50,saved.getInt("business_offset"));directory.close();
  directory=new BusinessDirectoryView(activity,()->personal++,dialled::add,loader,tasks);directory.restoreState(saved);root=directory.create();activity.setContentView(root);complete();
  assertEquals(Integer.valueOf(50),offsets.get(offsets.size()-1));assertEquals("Test",queries.get(queries.size()-1));assertTrue(dialled.isEmpty());
 }
 @Test public void showingRecordDetailsDoesNotDialOrOpenSourceUntilExplicitAction(){
  show((context,query,offset)->page(0,false,entry(1)));complete();row(0).performClick();
  AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();assertNotNull(dialog);assertTrue(dialled.isEmpty());assertNull(shadowOf(activity).getNextStartedActivity());
  TextView title=dialog.getWindow().getDecorView().findViewWithTag("business_directory_detail_title"),body=dialog.getWindow().getDecorView().findViewWithTag("business_directory_detail_text");
  assertEquals(entry(1).name,title.getText().toString());assertTrue(body.getText().toString().contains(entry(1).sourceUrl));assertTrue(body.getText().toString().contains("Test license"));
  dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle();assertEquals(Collections.singletonList(entry(1).phone),dialled);
 }
 @Test public void closingDirectoryRejectsQueuedUiResult(){
  show((context,query,offset)->page(0,false,entry(1)));directory.close();complete();
  assertEquals(2,list().getAdapter().getCount());assertTrue(dialled.isEmpty());
 }
 @Test public void detachedRowsCannotDialAndDetachedSearchCannotChangeSavedQuery(){
  show((context,query,offset)->page(0,false,entry(1)));complete();
  View oldCall=row(0).findViewWithTag("business_directory_call_1");EditText oldSearch=(EditText)root.findViewWithTag("business_directory_search");
  directory.detach();oldCall.performClick();oldSearch.setText("detached query");
  Bundle saved=new Bundle();directory.saveState(saved);assertEquals("",saved.getString("business_query"));assertTrue(dialled.isEmpty());assertTrue(tasks.values.isEmpty());
 }
 @Test public void obsoleteSearchViewCannotOverwriteRecreatedDirectoryState(){
  show((context,query,offset)->page(offset,false,entry(offset+1)));complete();EditText oldSearch=(EditText)root.findViewWithTag("business_directory_search");
  Bundle restored=new Bundle();restored.putString("business_query","restored");restored.putInt("business_offset",50);directory.restoreState(restored);
  root=directory.create();activity.setContentView(root);oldSearch.setText("old view query");complete();
  Bundle actual=new Bundle();directory.saveState(actual);assertEquals("restored",actual.getString("business_query"));assertEquals(50,actual.getInt("business_offset"));assertTrue(dialled.isEmpty());
 }
 @Test public void detachDisarmsDialogCallUntilDirectoryIsVisibleAgain(){
  show((context,query,offset)->page(0,false,entry(1)));complete();row(0).performClick();AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
  directory.detach();dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle();assertTrue(dialled.isEmpty());assertFalse(dialog.isShowing());
 }
 @Test public void loaderFailureNeverDisplaysAnExceptionOrFabricatedBusinesses(){
  show((context,query,offset)->{throw new IllegalStateException("private/path/database error");});complete();
  String body=((TextView)root.findViewWithTag("business_directory_empty")).getText().toString();
  assertFalse(body.contains("private/path"));assertFalse(body.contains("IllegalStateException"));assertEquals(2,list().getAdapter().getCount());
 }
 @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
 public void nonemptyHeaderActuallyCollapsesAndReturnsForEmptySearchWithoutCalling(){
  show((context,query,offset)->query.isEmpty()?page(0,false,entry(1),entry(2)):page(0,false));complete();layoutSmall();
  assertEquals(1,list().getHeaderViewsCount());assertEquals(0,root.findViewWithTag("business_directory_empty_header").getHeight());
  assertEquals("No retained blank fixed-header gap",0,list().getChildAt(0).getHeight());
  EditText search=(EditText)root.findViewWithTag("business_directory_search");search.setText("no results");
  shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(160));complete();layoutSmall();
  assertEquals(1,list().getHeaderViewsCount());assertTrue(root.findViewWithTag("business_directory_empty_header").getHeight()>0);
  assertEquals(View.VISIBLE,root.findViewWithTag("business_directory_empty").getVisibility());
  search.setText("");shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(160));complete();layoutSmall();
  assertEquals(0,list().getChildAt(0).getHeight());assertTrue(dialled.isEmpty());
 }
 @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
 public void fullSourceAttributionFitsTwoLinesAndTopTargetOpensFullDetailsInBothLanguages(){
  BusinessDirectory.Manifest osm=new BusinessDirectory.Manifest("test",zeros(),2720,"OpenStreetMap contributors via Geofabrik","https://www.openstreetmap.org/copyright","ODbL 1.0","https://opendatacommons.org/licenses/odbl/1-0/","2026-11-03");
  for(String language:new String[]{"he","en"}){
   activity.getSharedPreferences("da_caller",0).edit().putString("language",language).commit();
   android.content.res.Configuration configuration=new android.content.res.Configuration(activity.getResources().getConfiguration());configuration.fontScale=1.3f;
   activity.getResources().updateConfiguration(configuration,activity.getResources().getDisplayMetrics());
   if(directory!=null)directory.close();show((context,query,offset)->new BusinessDirectory.Page(BusinessDirectory.Status.AVAILABLE,osm,Arrays.asList(entry(1)),0,false));complete();layoutSmall();
   TextView source=(TextView)root.findViewWithTag("business_directory_status");String text=source.getText().toString();
   assertTrue(text.contains("2,720"));assertTrue(text.contains("© OpenStreetMap contributors"));assertEquals(500,source.getTypeface().getWeight());
   assertTrue(source.getHeight()>=48*activity.getResources().getDisplayMetrics().density);assertNotNull(source.getLayout());
   assertTrue(source.getLayout().getLineCount()<=2);for(int n=0;n<source.getLayout().getLineCount();n++)assertEquals(0,source.getLayout().getEllipsisCount(n));
   assertEquals(text.length(),source.getLayout().getLineEnd(source.getLayout().getLineCount()-1));
   source.performClick();AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();TextView detail=dialog.getWindow().getDecorView().findViewWithTag("business_directory_detail_text");
   String body=detail.getText().toString();assertTrue(body.contains(osm.sourceName));assertTrue(body.contains(osm.sourceUrl));assertTrue(body.contains(osm.license));assertTrue(body.contains(osm.updatedAt));
   dialog.dismiss();assertTrue(dialled.isEmpty());assertNull(shadowOf(activity).getNextStartedActivity());
  }
 }
 @Test public void taxonomyLabelsAreDisplayOnlyAndPreserveRawSourceIdentity(){
  BusinessDirectory.Entry raw=new BusinessDirectory.Entry(1,"Test business","0500099901","0500099901","Test city","shop:mobile_phone_accessories","Test address","https://example.org/1","test/1","Test license","2026-11-02");
  show((context,query,offset)->page(0,false,raw));complete();View row=row(0);
  assertTrue(row.getContentDescription().toString().contains("אביזרי סלולר"));assertFalse(row.getContentDescription().toString().contains("shop:"));
  row.performClick();TextView detail=ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView().findViewWithTag("business_directory_detail_text");
  assertTrue(detail.getText().toString().contains("אביזרי סלולר"));assertEquals("shop:mobile_phone_accessories",raw.category);assertEquals("0500099901",raw.phone);
  assertEquals("Hostel",BusinessCategoryLabels.label("tourism:hostel",true));assertEquals("Local trade",BusinessCategoryLabels.label("craft:unmapped",true));assertEquals("Human label",BusinessCategoryLabels.label("Human label",true));assertTrue(dialled.isEmpty());
 }
 private void layoutSmall(){
  for(int n=0;n<2;n++){root.measure(View.MeasureSpec.makeMeasureSpec(480,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(700,View.MeasureSpec.EXACTLY));root.layout(0,0,480,700);shadowOf(Looper.getMainLooper()).idle();}
 }
 private static final class Tasks implements Executor {
  final List<Runnable> values=new ArrayList<>();public void execute(Runnable task){values.add(task);}
  void runFirst(){values.remove(0).run();}void runLast(){values.remove(values.size()-1).run();}
 }
}
