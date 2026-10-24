package com.dacaller.app;

import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.ContactsContract;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=33)
public class ContactDataTest {
 private MatrixCursor data(){return new MatrixCursor(ContactData.PROJECTION);}
 private void add(MatrixCursor cursor,long id,String mime,String... fields){
  Object[] row=new Object[ContactData.PROJECTION.length];row[0]=id;row[1]=5L;row[2]=mime;
  for(int i=0;i<fields.length;i++)row[3+i]=fields[i];cursor.addRow(row);
 }
 @Test public void serviceNumberLookupAndSearchKeysDoNotAliasOrdinaryNumbers(){
  assertNotEquals(Contacts.canonical("*123#"),Contacts.canonical("123"));
  assertNotEquals(Contacts.canonical("123,45"),Contacts.canonical("12345"));
  assertEquals("*123#",Contacts.canonical("* 123 #"));
  assertEquals(Contacts.canonical("+972501234567"),Contacts.canonical("050-123-4567"));
 }
 @Test public void organizationAndPostalUseTheirActualProviderColumns(){
  MatrixCursor cursor=data();
  add(cursor,21,ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE,"Company","1","custom label","Engineer","Research");
  add(cursor,22,ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE,"Formatted address","2","custom label","Street","PO box","Neighborhood","City","Region","12345","Country");
  ContactData result=ContactData.read(cursor);
  assertEquals("Engineer",result.values.get("job"));assertEquals("Research",result.values.get("department"));
  assertEquals("Street",result.values.get("street"));assertEquals("City",result.values.get("city"));
  assertEquals("Region",result.values.get("region"));assertEquals("12345",result.values.get("zipcode"));assertEquals("Country",result.values.get("country"));
  assertEquals(Long.valueOf(22),result.ids.get("country"));
 }
 @Test public void customPhoneAndEmailTypesAndRowIdentitiesSurviveLoading(){
  MatrixCursor cursor=data();
  add(cursor,31,ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,"12345","0","Emergency desk");
  add(cursor,32,ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,"person@example.com","2");
  ContactData result=ContactData.read(cursor);
  assertEquals("0",result.types.get("phone0"));assertEquals("2",result.types.get("email0"));assertEquals(Long.valueOf(31),result.ids.get("phone0"));
 }
 @Test public void emptyPhoneEmailAndEventDoNotInsertTypeOnlyRows(){
  String[] mime={ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE};
  for(String type:mime){
   ArrayList<ContentProviderOperation> ops=new ArrayList<>();
   ContactEditorActivity.addData(ops,5,false,-1,type,new String[]{ContactsContract.Data.DATA1,ContactsContract.Data.DATA2},new String[]{"","2"});
   assertTrue(type,ops.isEmpty());
  }
 }
 @Test public void clearingExistingNumberDeletesOnlyItsDataRow()throws Exception{
  ArrayList<ContentProviderOperation> ops=new ArrayList<>();
  ContactEditorActivity.addData(ops,5,false,31,ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,new String[]{ContactsContract.Data.DATA1,ContactsContract.Data.DATA2},new String[]{"","2"});
  RecordingProvider provider=new RecordingProvider();ops.get(0).apply(provider,new ContentProviderResult[0],0);
  assertEquals("delete",provider.method);assertEquals(ContactsContract.Data._ID+"=?",provider.selection);assertArrayEquals(new String[]{"31"},provider.args);
 }
 @Test public void updateKeepsExactNumberAndStoredType()throws Exception{
  ArrayList<ContentProviderOperation> ops=new ArrayList<>();
  ContactEditorActivity.addData(ops,5,false,31,ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,new String[]{ContactsContract.Data.DATA1,ContactsContract.Data.DATA2},new String[]{"+972 50 123 4567","0"});
  RecordingProvider provider=new RecordingProvider();ops.get(0).apply(provider,new ContentProviderResult[0],0);
  assertEquals("update",provider.method);assertEquals("+972 50 123 4567",provider.values.getAsString(ContactsContract.Data.DATA1));assertEquals("0",provider.values.getAsString(ContactsContract.Data.DATA2));
 }
 @Test public void serviceNumbersDoNotShareFavoriteKeysWithOrdinaryNumbers(){
  assertNotEquals(new Contacts.Person("Service","*123#",11).key,new Contacts.Person("Ordinary","123",12).key);
 }
 @Test public void contactsWithoutPhonesHaveDistinctFavoriteKeys(){
  assertNotEquals(new Contacts.Person("First","",11).key,new Contacts.Person("Second","",12).key);
 }
 @Test public void highResolutionPhotoIsSampledBeforeDecoding(){
  assertTrue(ContactPhotos.sampleSize(12000,8000,512)>=8);assertEquals(1,ContactPhotos.sampleSize(96,96,512));
 }
 private static final class RecordingProvider extends ContentProvider {
  String method,selection;String[] args;ContentValues values;
  public boolean onCreate(){return true;}
  public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){return null;}
  public String getType(Uri uri){return null;}
  public Uri insert(Uri uri,ContentValues values){method="insert";this.values=values;return Uri.withAppendedPath(uri,"100");}
  public int update(Uri uri,ContentValues values,String selection,String[] args){method="update";this.values=values;this.selection=selection;this.args=args;return 1;}
  public int delete(Uri uri,String selection,String[] args){method="delete";this.selection=selection;this.args=args;return 1;}
 }
}
