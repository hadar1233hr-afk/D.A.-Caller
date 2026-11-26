package com.dacaller.app;

import static org.junit.Assert.*;
import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowAlertDialog;

@RunWith(RobolectricTestRunner.class) @Config(sdk=33,qualifiers="w320dp-h569dp") @LooperMode(LooperMode.Mode.PAUSED)
public class LocalAssistantDialogTest {
 @Before public void setup(){
  Context ctx=RuntimeEnvironment.getApplication();
  ctx.getSharedPreferences("da_caller",Context.MODE_PRIVATE).edit().clear().putBoolean("role_prompted",true).putString("language","en").commit();
  Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.READ_CONTACTS,Manifest.permission.READ_CALL_LOG,Manifest.permission.CALL_PHONE);
 }
 @Test public void toolbarOpensLocalAssistantAndDeniedReadDoesNotExposeData(){
  try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup().visible()){
   MainActivity host=controller.get();View entry=host.getWindow().getDecorView().findViewWithTag("main_assistant_button");
   assertNotNull(entry);entry.performClick();AlertDialog assistant=ShadowAlertDialog.getLatestAlertDialog();
   assertTrue(assistant.isShowing());View root=assistant.getWindow().getDecorView();
   EditText input=root.findViewWithTag("assistant_input");input.setText("find contact Dana");root.findViewWithTag("assistant_submit").performClick();
   LinearLayout results=root.findViewWithTag("assistant_results");
   assertEquals(1,results.getChildCount());assertTrue(((TextView)results.getChildAt(0)).getText().toString().contains("Permission is required"));
   assertNull(results.findViewWithTag("assistant_select_call"));assertNull(Shadows.shadowOf(host).getNextStartedActivity());assistant.dismiss();
  }
 }
 @Test public void numberProposalRequiresSeparateConfirmationAndCancellationNeverDials(){
  try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup().visible()){
   MainActivity host=controller.get();LocalAssistantDialog.show(host);AlertDialog assistant=ShadowAlertDialog.getLatestAlertDialog();View root=assistant.getWindow().getDecorView();
   EditText input=root.findViewWithTag("assistant_input");input.setText("call 0501234567");root.findViewWithTag("assistant_submit").performClick();
   assertNull(Shadows.shadowOf(host).getNextStartedActivity());
   View select=root.findViewWithTag("assistant_select_call");assertNotNull(select);select.performClick();
   AlertDialog confirm=ShadowAlertDialog.getLatestAlertDialog();assertNotSame(assistant,confirm);assertTrue(confirm.isShowing());
   assertNull(Shadows.shadowOf(host).getNextStartedActivity());confirm.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
   Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertNull(Shadows.shadowOf(host).getNextStartedActivity());assertTrue(assistant.isShowing());
   assistant.dismiss();assertEquals("",input.getText().toString());
  }
 }
}
