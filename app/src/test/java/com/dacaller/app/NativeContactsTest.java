package com.dacaller.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.provider.ContactsContract;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=33)
public class NativeContactsTest {
 @Test public void nativeCreatePrefillsExactNameAndPhone(){
  Intent intent=NativeContacts.intent(-1,"Ada","+972 50 1234567");
  assertEquals(Intent.ACTION_INSERT,intent.getAction());assertEquals(ContactsContract.Contacts.CONTENT_URI,intent.getData());
  assertEquals("Ada",intent.getStringExtra(ContactsContract.Intents.Insert.NAME));assertEquals("+972 50 1234567",intent.getStringExtra(ContactsContract.Intents.Insert.PHONE));
 }
 @Test public void nativeEditTargetsExactContactInsteadOfCreatingDuplicate(){
  Intent intent=NativeContacts.intent(42,"Ada","123");
  assertEquals(Intent.ACTION_EDIT,intent.getAction());assertEquals("42",intent.getData().getLastPathSegment());assertEquals(ContactsContract.Contacts.CONTENT_ITEM_TYPE,intent.getType());
 }
 @Test public void unavailableNativeEditorShowsEnglishFailureWithoutCrashing(){
  RuntimeEnvironment.getApplication().getSharedPreferences("da_caller",0).edit().putString("language","en").commit();
  Activity activity=Robolectric.buildActivity(MissingEditor.class).setup().get();
  assertFalse(NativeContacts.open(activity,42,"Ada","123"));assertEquals("Contacts editor unavailable",ShadowToast.getTextOfLatestToast());
 }
 public static class MissingEditor extends Activity {
  @Override public void startActivityForResult(Intent intent,int request){throw new ActivityNotFoundException();}
 }
}
