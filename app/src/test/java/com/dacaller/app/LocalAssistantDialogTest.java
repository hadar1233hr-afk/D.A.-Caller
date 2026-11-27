package com.dacaller.app;

import static org.junit.Assert.*;
import android.Manifest;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Looper;
import android.os.Process;
import android.provider.ContactsContract;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
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
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowContentResolver;

@RunWith(RobolectricTestRunner.class) @Config(sdk=33,qualifiers="w320dp-h569dp") @LooperMode(LooperMode.Mode.PAUSED)
public class LocalAssistantDialogTest {
 private PeopleProvider people;
 public static class RecordingHost extends MainActivity {
  final List<String> calls=new ArrayList<>();
  @Override void dialFromList(String number){calls.add(number);}
  @Override Map<String,String> nameIndex(){return Collections.emptyMap();}
 }
 private static class PeopleProvider extends ContentProvider {
  CountDownLatch entered,release;
  int queries;
  public boolean onCreate(){return true;}
  public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){
   queries++;
   if(entered!=null){entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Fixture read timed out");}
    catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException(interrupted);}}
   String[] columns=projection==null?new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER}:projection;
   MatrixCursor rows=new MatrixCursor(columns);
   String[][] values={{"Alex One","0501234567"},{"Alex Two","0507654321"}};
   for(int n=0;n<values.length;n++){
    Object[] row=new Object[columns.length];
    for(int i=0;i<columns.length;i++){
     if(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME.equals(columns[i]))row[i]=values[n][0];
     else if(ContactsContract.CommonDataKinds.Phone.NUMBER.equals(columns[i]))row[i]=values[n][1];
     else if("_id".equals(columns[i])||"contact_id".equals(columns[i]))row[i]=n+1;
    }rows.addRow(row);
   }return rows;
  }
  public String getType(Uri uri){return null;}
  public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException("Read-only fixture");}
  public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException("Read-only fixture");}
  public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException("Read-only fixture");}
 }
 @Before public void setup()throws Exception{
  awaitAssistantWorker();
  Context ctx=RuntimeEnvironment.getApplication();
  ctx.getSharedPreferences("da_caller",Context.MODE_PRIVATE).edit().clear().putBoolean("role_prompted",true).putString("language","en").commit();
  Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.READ_CONTACTS,Manifest.permission.READ_CALL_LOG,Manifest.permission.CALL_PHONE);
  setContactsAppOp(AppOpsManager.MODE_ALLOWED);
 }
 @After public void cleanup()throws Exception{if(people!=null&&people.release!=null)people.release.countDown();awaitAssistantWorker();}
 private static void awaitAssistantWorker()throws Exception{
  Field field=LocalAssistantDialog.class.getDeclaredField("IO");field.setAccessible(true);
  ((ExecutorService)field.get(null)).submit(()->{}).get(5,TimeUnit.SECONDS);
  Shadows.shadowOf(Looper.getMainLooper()).idle();
 }
 private static void setContactsAppOp(int mode){
  Application app=RuntimeEnvironment.getApplication();
  Shadows.shadowOf(app.getSystemService(AppOpsManager.class)).setMode(AppOpsManager.OPSTR_READ_CONTACTS,Process.myUid(),app.getPackageName(),mode);
 }
 private void registerPeople(){
  people=new PeopleProvider();ProviderInfo info=new ProviderInfo();info.authority=ContactsContract.AUTHORITY;
  people.attachInfo(RuntimeEnvironment.getApplication(),info);ShadowContentResolver.registerProviderInternal(ContactsContract.AUTHORITY,people);
  Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.READ_CONTACTS);
 }
 private static AlertDialog open(MainActivity host){LocalAssistantDialog.show(host);return ShadowAlertDialog.getLatestAlertDialog();}
 private static View root(AlertDialog dialog){return dialog.getWindow().getDecorView();}
 private static void submit(AlertDialog dialog,String text){((EditText)root(dialog).findViewWithTag("assistant_input")).setText(text);root(dialog).findViewWithTag("assistant_submit").performClick();}
 private static LinearLayout results(AlertDialog dialog){return root(dialog).findViewWithTag("assistant_results");}
 private static List<View> actions(AlertDialog dialog){
  List<View> result=new ArrayList<>();LinearLayout list=results(dialog);
  for(int i=0;i<list.getChildCount();i++)if("assistant_select_call".equals(list.getChildAt(i).getTag()))result.add(list.getChildAt(i));
  return result;
 }
 private static AlertDialog select(AlertDialog assistant,int index){actions(assistant).get(index).performClick();return ShadowAlertDialog.getLatestAlertDialog();}
 private static void assertPermissionUnavailable(AlertDialog assistant){
  assertEquals(1,results(assistant).getChildCount());assertEquals("Permission is unavailable.",((TextView)results(assistant).getChildAt(0)).getText().toString());assertTrue(actions(assistant).isEmpty());
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
   assistant.dismiss();
   // Android dispatches Dialog.OnDismissListener through the main Handler.
   // Drain that real lifecycle callback before checking private input cleanup.
   Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
   assertFalse(assistant.isShowing());assertEquals("",input.getText().toString());
   assertEquals(0,((LinearLayout)root.findViewWithTag("assistant_results")).getChildCount());
   assertNull(Shadows.shadowOf(host).getNextStartedActivity());
  }
 }
 @Test public void editingInputDisarmsOldRowsAndConfirmationUntilNewExplicitSubmission(){
  try(ActivityController<RecordingHost> controller=Robolectric.buildActivity(RecordingHost.class).setup().visible()){
   RecordingHost host=controller.get();AlertDialog assistant=open(host);submit(assistant,"call 0501234567");
   View oldAction=actions(assistant).get(0);AlertDialog oldConfirm=select(assistant,0);assertTrue(host.calls.isEmpty());
   ((EditText)root(assistant).findViewWithTag("assistant_input")).setText("call 0507654321");
   assertFalse(oldConfirm.isShowing());assertEquals(0,results(assistant).getChildCount());
   oldAction.performClick();oldConfirm.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertTrue(host.calls.isEmpty());
   root(assistant).findViewWithTag("assistant_submit").performClick();AlertDialog current=select(assistant,0);
   assertTrue(host.calls.isEmpty());current.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
   assertEquals(Collections.singletonList("0507654321"),host.calls);
  }
 }
 @Test public void closingAssistantAlsoClosesAndDisarmsItsSeparateConfirmation(){
  try(ActivityController<RecordingHost> controller=Robolectric.buildActivity(RecordingHost.class).setup().visible()){
   RecordingHost host=controller.get();AlertDialog assistant=open(host);submit(assistant,"call 0501234567");AlertDialog confirm=select(assistant,0);
   View oldAction=actions(assistant).get(0);assistant.dismiss();Shadows.shadowOf(Looper.getMainLooper()).idle();
   assertFalse(confirm.isShowing());assertEquals("",((EditText)root(assistant).findViewWithTag("assistant_input")).getText().toString());assertEquals(0,results(assistant).getChildCount());
   assertEquals("",((TextView)root(confirm).findViewById(android.R.id.message)).getText().toString());
   oldAction.performClick();confirm.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertTrue(host.calls.isEmpty());
  }
 }
 @Test public void onlyCurrentConfirmationCanDispatchAndOnlyOnce(){
  try(ActivityController<RecordingHost> controller=Robolectric.buildActivity(RecordingHost.class).setup().visible()){
   RecordingHost host=controller.get();AlertDialog assistant=open(host);submit(assistant,"call 0501234567");
   AlertDialog first=select(assistant,0),second=select(assistant,0);assertNotSame(first,second);assertFalse(first.isShowing());
   first.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertTrue(host.calls.isEmpty());
   second.getButton(AlertDialog.BUTTON_POSITIVE).performClick();second.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
   assertEquals(Collections.singletonList("0501234567"),host.calls);
  }
 }
 @Test public void realContactRowsRequireDisambiguationAndRevokedReadDisarmsConfirmation()throws Exception{
  try(ActivityController<RecordingHost> controller=Robolectric.buildActivity(RecordingHost.class).setup().visible()){
   RecordingHost host=controller.get();registerPeople();AlertDialog assistant=open(host);submit(assistant,"find contact Alex");awaitAssistantWorker();
   assertEquals(2,actions(assistant).size());assertTrue(host.calls.isEmpty());AlertDialog confirm=select(assistant,1);
   assertTrue(((TextView)root(confirm).findViewById(android.R.id.message)).getText().toString().contains("0507654321"));assertTrue(host.calls.isEmpty());
   Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.READ_CONTACTS);
   confirm.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertFalse(confirm.isShowing());assertPermissionUnavailable(assistant);assertTrue(host.calls.isEmpty());
  }
 }
 @Test public void cancelledConfirmationCannotDispatchBeforeItsDismissCallback(){
  for(boolean cancelButton:new boolean[]{true,false}){
   try(ActivityController<RecordingHost> controller=Robolectric.buildActivity(RecordingHost.class).setup().visible()){
    RecordingHost host=controller.get();AlertDialog assistant=open(host);submit(assistant,"call 0501234567");
    AlertDialog confirm=select(assistant,0);View stalePositive=confirm.getButton(AlertDialog.BUTTON_POSITIVE);
    if(cancelButton)confirm.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();else confirm.cancel();
    // A queued or cached click must not revive an already cancelled window,
    // even before Android dispatches its asynchronous OnDismiss callback.
    stalePositive.performClick();assertTrue(host.calls.isEmpty());
    Shadows.shadowOf(Looper.getMainLooper()).idle();assertTrue(host.calls.isEmpty());
    assertFalse(confirm.isShowing());assertTrue(assistant.isShowing());assistant.dismiss();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
   }
  }
 }
 @Test public void grantedRuntimePermissionDoesNotKeepCachedRowsCallableAfterAppOpsDenial()throws Exception{
  try(ActivityController<RecordingHost> controller=Robolectric.buildActivity(RecordingHost.class).setup().visible()){
   RecordingHost host=controller.get();registerPeople();AlertDialog assistant=open(host);submit(assistant,"find contact Alex");awaitAssistantWorker();
   View cached=actions(assistant).get(0);setContactsAppOp(AppOpsManager.MODE_IGNORED);
   assertEquals(PackageManager.PERMISSION_GRANTED,host.checkSelfPermission(Manifest.permission.READ_CONTACTS));
   cached.performClick();assertPermissionUnavailable(assistant);assertSame(assistant,ShadowAlertDialog.getLatestAlertDialog());assertTrue(host.calls.isEmpty());
   setContactsAppOp(AppOpsManager.MODE_ALLOWED);cached.performClick();assertPermissionUnavailable(assistant);assertTrue(host.calls.isEmpty());
  }
 }
 @Test public void deniedOrUnresolvedAppOpCannotUseRuntimeGrantAsADataReadFallback()throws Exception{
  try(ActivityController<RecordingHost> controller=Robolectric.buildActivity(RecordingHost.class).setup().visible()){
   RecordingHost host=controller.get();registerPeople();AlertDialog assistant=open(host);int reads=people.queries;
   for(int mode:new int[]{AppOpsManager.MODE_IGNORED,AppOpsManager.MODE_ERRORED,AppOpsManager.MODE_DEFAULT}){
    setContactsAppOp(mode);assertEquals(PackageManager.PERMISSION_GRANTED,host.checkSelfPermission(Manifest.permission.READ_CONTACTS));
    submit(assistant,"find contact Alex");awaitAssistantWorker();
    assertEquals(reads,people.queries);assertTrue(actions(assistant).isEmpty());assertTrue(host.calls.isEmpty());
    assertTrue(((TextView)results(assistant).getChildAt(0)).getText().toString().contains("Permission is required"));
   }assistant.dismiss();
  }
 }
 @Test public void editedInputRejectsAReadThatWasAlreadyInFlight()throws Exception{
  try(ActivityController<RecordingHost> controller=Robolectric.buildActivity(RecordingHost.class).setup().visible()){
   RecordingHost host=controller.get();registerPeople();people.entered=new CountDownLatch(1);people.release=new CountDownLatch(1);
   AlertDialog assistant=open(host);submit(assistant,"find contact Alex");assertTrue(people.entered.await(5,TimeUnit.SECONDS));
   ((EditText)root(assistant).findViewWithTag("assistant_input")).setText("call 0507654321");people.release.countDown();awaitAssistantWorker();
   assertEquals(0,results(assistant).getChildCount());assertTrue(actions(assistant).isEmpty());assertTrue(host.calls.isEmpty());assistant.dismiss();
  }
 }
 @Test public void appOpsLossDuringProviderReadNeverPublishesItsPersonalRows()throws Exception{
  try(ActivityController<RecordingHost> controller=Robolectric.buildActivity(RecordingHost.class).setup().visible()){
   RecordingHost host=controller.get();registerPeople();people.entered=new CountDownLatch(1);people.release=new CountDownLatch(1);
   AlertDialog assistant=open(host);submit(assistant,"find contact Alex");assertTrue(people.entered.await(5,TimeUnit.SECONDS));
   setContactsAppOp(AppOpsManager.MODE_IGNORED);assertEquals(PackageManager.PERMISSION_GRANTED,host.checkSelfPermission(Manifest.permission.READ_CONTACTS));
   people.release.countDown();awaitAssistantWorker();assertPermissionUnavailable(assistant);assertTrue(host.calls.isEmpty());assistant.dismiss();
  }
 }
 @Test public void editingCancelsQueuedObsoleteReadsWithoutInterruptingTheCurrentProvider()throws Exception{
  try(ActivityController<RecordingHost> controller=Robolectric.buildActivity(RecordingHost.class).setup().visible()){
   RecordingHost host=controller.get();registerPeople();people.entered=new CountDownLatch(1);people.release=new CountDownLatch(1);
   AlertDialog assistant=open(host);submit(assistant,"find contact Alex");assertTrue(people.entered.await(5,TimeUnit.SECONDS));
   int begun=people.queries;submit(assistant,"find contact Two");
   ((EditText)root(assistant).findViewWithTag("assistant_input")).setText("unsubmitted edit");
   people.release.countDown();awaitAssistantWorker();
   assertEquals("Only the read already inside the real fixture provider may finish",begun,people.queries);
   assertEquals(0,results(assistant).getChildCount());assertTrue(host.calls.isEmpty());assistant.dismiss();
  }
 }
}
