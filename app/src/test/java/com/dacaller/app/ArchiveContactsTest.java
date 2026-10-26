package com.dacaller.app;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.ContactsContract;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=33)
public class ArchiveContactsTest {
 private Application app;
 private LookupProvider provider;
 @Before public void setup(){
  app=RuntimeEnvironment.getApplication();shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS);
  provider=new LookupProvider();ProviderInfo info=new ProviderInfo();info.authority=ContactsContract.AUTHORITY;
  provider.attachInfo(app,info);ShadowContentResolver.registerProviderInternal(ContactsContract.AUTHORITY,provider);
 }
 private CallHistory.Entry entry(String number){return new CallHistory.Entry(1,number,1,1,1);}
 @Test public void distinctArchivedNumbersUseIndexedLookupOnceAndFindSecondaryNumbers(){
  provider.names.put("+972501111111",new Object[]{88L,"Alice Cohen"});
  provider.names.put("+972522222222",new Object[]{88L,"Alice Cohen"});
  Map<String,ArchiveContacts.Match> names=ArchiveContacts.resolve(app,Arrays.asList(entry("+972-50-1111111"),
    entry("+972501111111"),entry("+972522222222")),()->false);
  assertEquals(2,provider.queries.size());assertEquals(2,names.size());
  assertEquals(88,names.get(ArchiveContacts.key("+972522222222")).id);
  assertEquals("Alice Cohen",names.get(ArchiveContacts.key("+972501111111")).name);
  for(Uri uri:provider.queries)assertTrue(uri.toString().startsWith(ContactsContract.PhoneLookup.CONTENT_FILTER_URI.toString()));
 }
 @Test public void similarlyEndingNumbersNeverShareGuessedIdentityAndServicesStayDistinct(){
  provider.names.put("+972501111111",new Object[]{88L,"Alice"});
  Map<String,ArchiveContacts.Match> names=ArchiveContacts.resolve(app,Arrays.asList(
    entry("+972501111111"),entry("+441111111"),entry("*1111111#"),entry("")),()->false);
  assertEquals(2,provider.queries.size());assertEquals(1,names.size());
  assertNull(names.get(ArchiveContacts.key("+441111111")));
  assertNotEquals(ArchiveContacts.key("*1111111#"),ArchiveContacts.key("1111111"));
  assertNotEquals(ArchiveContacts.key("123,456"),ArchiveContacts.key("123456"));
 }
 @Test public void deniedPermissionOrCancelledSnapshotDoesNotReadProvider(){
  shadowOf(app).denyPermissions(Manifest.permission.READ_CONTACTS);
  assertTrue(ArchiveContacts.resolve(app,Collections.singletonList(entry("1234567")),()->false).isEmpty());
  shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS);
  assertTrue(ArchiveContacts.resolve(app,Collections.singletonList(entry("1234567")),()->true).isEmpty());
  assertTrue(provider.queries.isEmpty());
 }
 @Test public void permissionRevokedMidLookupDiscardsPreviouslyResolvedIdentities(){
  provider.names.put("1234567",new Object[]{88L,"Alice"});
  provider.afterQuery=()->shadowOf(app).denyPermissions(Manifest.permission.READ_CONTACTS);
  assertTrue(ArchiveContacts.resolve(app,Arrays.asList(entry("1234567"),entry("7654321")),()->false).isEmpty());
  assertEquals(1,provider.queries.size());
 }
 @Test public void securityFailureReturnsOnlyNumberFallbackAndLaterResolveUsesCurrentName(){
  provider.fail=true;
  assertTrue(ArchiveContacts.resolve(app,Collections.singletonList(entry("1234567")),()->false).isEmpty());
  provider.fail=false;provider.names.put("1234567",new Object[]{88L,"Before"});
  assertEquals("Before",ArchiveContacts.resolve(app,Collections.singletonList(entry("1234567")),()->false).get("1234567").name);
  provider.names.put("1234567",new Object[]{88L,"After"});
  assertEquals("After",ArchiveContacts.resolve(app,Collections.singletonList(entry("1234567")),()->false).get("1234567").name);
 }
 @Test public void privateSentinelsAndUnavailableLabelsNeverBecomeRealContactNumbers(){
  for(String value:new String[]{"-1","-2","-3","Private","Unknown","Unavailable","מספר חסוי",""}){
   assertEquals("Rejected identity: "+value,"",ArchiveContacts.key(value));
  }
  assertTrue(ArchiveContacts.resolve(app,Arrays.asList(entry("-1"),entry("-2"),entry("-3"),
    entry("Private"),entry("Unknown"),entry("Unavailable"),entry("מספר חסוי")),()->false).isEmpty());
  assertTrue(provider.queries.isEmpty());
 }
 private static final class LookupProvider extends ContentProvider {
  final Map<String,Object[]> names=new HashMap<>();final ArrayList<Uri> queries=new ArrayList<>();
  Runnable afterQuery=()->{};boolean fail;
  public boolean onCreate(){return true;}
  public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
   queries.add(uri);if(fail)throw new SecurityException("Permission revoked");
   MatrixCursor rows=new MatrixCursor(projection);Object[] value=names.get(uri.getLastPathSegment());
   if(value!=null)rows.addRow(value);afterQuery.run();return rows;
  }
  public String getType(Uri uri){return null;}
  public Uri insert(Uri uri,ContentValues values){return null;}
  public int update(Uri uri,ContentValues values,String selection,String[] args){return 0;}
  public int delete(Uri uri,String selection,String[] args){return 0;}
 }
}
