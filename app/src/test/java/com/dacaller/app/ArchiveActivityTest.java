package com.dacaller.app;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Application;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ListView;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
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

@RunWith(RobolectricTestRunner.class)
@Config(sdk=33)
public class ArchiveActivityTest {
 private Application app;
 private ActivityController<ArchiveActivity> controller;
 private ExecutorService io;
 @Before public void setUp() throws Exception{
  app=RuntimeEnvironment.getApplication();app.deleteDatabase("calls_archive.db");
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
 @After public void tearDown() throws Exception{if(controller!=null)controller.pause().stop().destroy();drain();app.deleteDatabase("calls_archive.db");}
 private static <T extends View>T find(View view,Class<T> type){
  if(type.isInstance(view))return type.cast(view);
  if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){
   T result=find(((ViewGroup)view).getChildAt(i),type);if(result!=null)return result;
  }
  return null;
 }
 private View root(){return controller.get().findViewById(android.R.id.content);}
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
}
