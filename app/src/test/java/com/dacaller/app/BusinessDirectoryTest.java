package com.dacaller.app;

import static org.junit.Assert.*;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.SQLiteMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=33)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
public class BusinessDirectoryTest {
 private File root;private SQLiteDatabase database;private File file;
 private final BusinessDirectory.Manifest metadata=new BusinessDirectory.Manifest("test-1",repeat('0',64),121,"Test open source","https://example.org/source","Test license","https://example.org/license","2026-11-02");
 @Before public void setup()throws Exception{
  root=Files.createTempDirectory(RuntimeEnvironment.getApplication().getCacheDir().toPath(),"business-fixture-").toFile();
  file=new File(root,"fixture.sqlite");database=SQLiteDatabase.openOrCreateDatabase(file,null);database.setVersion(1);
  database.execSQL("CREATE TABLE metadata(key TEXT PRIMARY KEY,value TEXT NOT NULL)");
  database.execSQL("INSERT INTO metadata VALUES('dataset_version','test-1')");
  database.execSQL("CREATE TABLE businesses(id INTEGER PRIMARY KEY,name TEXT NOT NULL,phone TEXT NOT NULL,phone_display TEXT,city TEXT,category TEXT,address TEXT,source_url TEXT,source_id TEXT,source_license TEXT,source_updated_at TEXT,normalized_phone TEXT NOT NULL)");
  database.execSQL("CREATE INDEX business_name ON businesses(name COLLATE NOCASE,id)");
  database.execSQL("CREATE INDEX business_phone ON businesses(normalized_phone COLLATE NOCASE)");
  database.execSQL("CREATE VIRTUAL TABLE business_search USING fts4(name,city,category,phone,tokenize=unicode61)");
 }
 @After public void close(){if(database!=null&&database.isOpen())database.close();}
 private static String repeat(char ch,int count){char[] result=new char[count];java.util.Arrays.fill(result,ch);return new String(result);}
 private void row(long id,String name,String phone,String city){
  database.execSQL("INSERT INTO businesses VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",new Object[]{id,name,phone,phone,city,"בית קפה","Test address","https://example.org/business/"+id,"test/"+id,"Test license","2026-11-02",BusinessDirectory.normalizedPhone(phone)});
  database.execSQL("INSERT INTO business_search(docid,name,city,category,phone) VALUES(?,?,?,?,?)",new Object[]{id,name,city,"בית קפה",phone});
 }
 private int count(){try(Cursor value=database.rawQuery("SELECT count(*) FROM businesses",null)){assertTrue(value.moveToFirst());return value.getInt(0);}}
 private String nativeMatches(String query){
  String expression=BusinessDirectory.matchExpression(query);StringBuilder rows=new StringBuilder("expression=").append(expression).append("; native matches=");
  try(Cursor result=database.rawQuery("SELECT docid,name,city FROM business_search WHERE business_search MATCH ?",new String[]{expression})){
   while(result.moveToNext())rows.append(result.getLong(0)).append(':').append(result.getString(1)).append(':').append(result.getString(2)).append(';');
  }
  return rows.toString();
 }
 @Test public void nameAndCityPrefixSearchUsesUnicodeIndexAndNeverGuessesAnotherBusiness(){
  row(1,"קפה השדרה","+972501234567","תל אביב");row(2,"קפה השדה","0521234567","חיפה");row(3,"Other store","031234567","תל אביב");
  BusinessDirectory.Page result=BusinessDirectory.query(database,metadata,"קפה השדר",0);
  assertEquals(nativeMatches("קפה השדר"),1,result.entries.size());assertEquals(1,result.entries.get(0).id);
  assertEquals("קפה השדרה",result.entries.get(0).name);
  result=BusinessDirectory.query(database,metadata,"קפה חיפ",0);assertEquals(2,result.entries.get(0).id);
  assertTrue(BusinessDirectory.query(database,metadata,"Coffee nowhere",0).entries.isEmpty());assertEquals(3,count());
 }
 @Test public void internationalAndPartialPhoneAliasesUseWholePrefixWithoutSuffixGuessing(){
  row(1,"First","+972501234567","A");row(2,"Second","0521234567","B");
  for(String query:new String[]{"050123","+972 50 123","00972-50-123","97250123"}){
   BusinessDirectory.Page page=BusinessDirectory.query(database,metadata,query,0);assertEquals(query,1,page.entries.size());assertEquals(1,page.entries.get(0).id);
   assertEquals("0501234567",page.entries.get(0).phone);
  }
  assertTrue(BusinessDirectory.query(database,metadata,"1234567",0).entries.isEmpty());
  assertEquals("0501234567",BusinessDirectory.normalizedPhone("+972 50-123-4567"));
  assertEquals("*3456",BusinessDirectory.normalizedPhone("*3456"));
  for(String invalid:new String[]{"+1 2025550100","0501234567;123","0501234567,0521234567","phone 0501234567","טלפון0501234567","050\u202e1234567","+972+501234567"})assertEquals(invalid,"",BusinessDirectory.normalizedPhone(invalid));
 }
 @Test public void ftsAndSqlOperatorsAreLiteralDataAndCannotBroadenTheQuery(){
  row(1,"Coffee shop","0501234567","Haifa");row(2,"A different store","0521234567","Tel Aviv");
  for(String query:new String[]{"coffee\" OR name:*","' OR 1=1 --","%","*","()\""}){
   assertTrue(query,BusinessDirectory.query(database,metadata,query,0).entries.isEmpty());
  }
  assertEquals(2,count());assertTrue(BusinessDirectory.matchExpression("בֵּית-קָפֶה").contains("בית"));
 }
 @Test public void boundedPagesPreserveStableIdsAcrossEqualNames(){
  for(int n=1;n<=121;n++)row(n,n<3?"Same name":String.format(java.util.Locale.ROOT,"Store %03d",n),"050"+String.format(java.util.Locale.ROOT,"%07d",n),"Test city");
  Set<Long> ids=new HashSet<>();
  for(int offset:new int[]{0,50,100}){
   BusinessDirectory.Page page=BusinessDirectory.query(database,metadata,"",offset);
   assertEquals(offset,page.offset);assertEquals(offset<100,page.hasMore);assertEquals(offset<100?50:21,page.entries.size());
   for(BusinessDirectory.Entry entry:page.entries)assertTrue(ids.add(entry.id));
  }
  assertEquals(121,ids.size());
  BusinessDirectory.Page first=BusinessDirectory.query(database,metadata,"",0);assertEquals(1,first.entries.get(0).id);assertEquals(2,first.entries.get(1).id);
 }
 @Test public void displayedAndDialledNumbersMustBeTheSameIdentity(){
  row(1,"Name","0501234567","City");database.execSQL("UPDATE businesses SET phone_display='0521234567'");
  try{BusinessDirectory.query(database,metadata,"",0);fail("Mismatched visible phone must not be dialled");}catch(IllegalStateException expected){}
  database.execSQL("UPDATE businesses SET phone_display=phone,normalized_phone='0521234567'");
  try{BusinessDirectory.query(database,metadata,"",0);fail("False normalized identity must be rejected");}catch(IllegalStateException expected){}
 }
 @Test public void unsafeSourceUrlsAndMissingBusinessNamesAreRejected(){
  row(1,"Name","0501234567","City");database.execSQL("UPDATE businesses SET source_url='javascript:alert(1)'");
  try{BusinessDirectory.query(database,metadata,"",0);fail();}catch(IllegalStateException expected){}
  database.execSQL("UPDATE businesses SET source_url='https://example.org/source',name=''");
  try{BusinessDirectory.query(database,metadata,"",0);fail();}catch(IllegalStateException expected){}
  assertFalse(BusinessDirectory.https("https://user:password@example.org/path"));assertFalse(BusinessDirectory.https("file:///private-data"));
 }
 private JSONObject manifestJson(String sha,int count)throws Exception{
  return new JSONObject().put("format","DA_CALLER_BUSINESSES").put("schema_version",1).put("dataset_version","test-1")
   .put("database_sha256",sha).put("record_count",count).put("source_name","Test source").put("source_url","https://example.org/source")
   .put("license","Test license").put("license_url","https://example.org/license").put("updated_at","2026-11-02");
 }
 private BusinessDirectory.Manifest manifest(JSONObject json)throws Exception{return BusinessDirectory.readManifest(new ByteArrayInputStream(json.toString().getBytes(StandardCharsets.UTF_8)));}
 @Test public void manifestRejectsUnsupportedOrMisTypedProvenance()throws Exception{
  JSONObject good=manifestJson(repeat('0',64),1);assertEquals(1,manifest(good).recordCount);
  for(JSONObject bad:new JSONObject[]{manifestJson(repeat('0',64),1).put("record_count",1.5),manifestJson(repeat('0',64),1).put("schema_version","1"),
    manifestJson(repeat('0',64),1).put("schema_version",2),manifestJson(repeat('0',64),1).put("dataset_version","../private"),
    manifestJson(repeat('0',64),1).put("source_url","http://example.org/source"),manifestJson(repeat('0',64),1).put("database_sha256","bad")}){
   try{manifest(bad);fail(bad.toString());}catch(java.io.IOException expected){}
  }
 }
 private static String hash(byte[] data)throws Exception{StringBuilder out=new StringBuilder();for(byte value:MessageDigest.getInstance("SHA-256").digest(data))out.append(String.format(java.util.Locale.ROOT,"%02x",value&255));return out.toString();}
 private byte[] finished()throws Exception{database.close();return Files.readAllBytes(file.toPath());}
 @Test public void validPublicDatabaseInstallsReadOnlyWithoutTouchingPrivateFiles()throws Exception{
  row(1,"Name","0501234567","City");byte[] data=finished();File cache=new File(root,"public-cache");File privateFile=new File(root,"private-archive.sqlite");Files.write(privateFile.toPath(),new byte[]{1,3,7});
  File installed=BusinessDirectory.install(new ByteArrayInputStream(data),cache,manifest(manifestJson(hash(data),1)));
  assertTrue(installed.isFile());assertArrayEquals(data,Files.readAllBytes(installed.toPath()));assertArrayEquals(new byte[]{1,3,7},Files.readAllBytes(privateFile.toPath()));
  try(SQLiteDatabase readonly=SQLiteDatabase.openDatabase(installed.getPath(),null,SQLiteDatabase.OPEN_READONLY)){
   assertTrue(readonly.isReadOnly());try{readonly.execSQL("DELETE FROM businesses");fail();}catch(SQLiteException expected){}
  }
 }
 @Test public void checksumFailureKeepsEarlierVerifiedCacheAndNoTemporaryPartialFile()throws Exception{
  row(1,"Name","0501234567","City");byte[] data=finished();File cache=new File(root,"public-cache");
  File previous=BusinessDirectory.install(new ByteArrayInputStream(data),cache,manifest(manifestJson(hash(data),1)));
  try{BusinessDirectory.install(new ByteArrayInputStream(new byte[]{0,1,2}),cache,manifest(manifestJson(repeat('f',64),1)));fail();}catch(java.io.IOException expected){}
  assertArrayEquals(data,Files.readAllBytes(previous.toPath()));for(File child:cache.listFiles())assertFalse(child.getName().endsWith(".part"));
 }
 @Test public void matchingChecksumCannotHideIncorrectDeclaredRecordCount()throws Exception{
  row(1,"Name","0501234567","City");byte[] data=finished();
  try{BusinessDirectory.install(new ByteArrayInputStream(data),new File(root,"public-cache"),manifest(manifestJson(hash(data),2)));fail();}catch(java.io.IOException expected){assertTrue(expected.getMessage().contains("count"));}
 }
 @Test public void missingSearchRowsRejectsDatasetRatherThanSilentlyLosingSearchResults()throws Exception{
  row(1,"Name","0501234567","City");database.execSQL("DELETE FROM business_search");byte[] data=finished();
  try{BusinessDirectory.install(new ByteArrayInputStream(data),new File(root,"public-cache"),manifest(manifestJson(hash(data),1)));fail();}catch(java.io.IOException expected){assertTrue(expected.getMessage().contains("search count"));}
 }
 @Test public void equalSearchCountCannotHideMismatchedRecordIdentities()throws Exception{
  row(1,"Name","0501234567","City");database.execSQL("UPDATE business_search SET docid=9 WHERE docid=1");byte[] data=finished();
  try{BusinessDirectory.install(new ByteArrayInputStream(data),new File(root,"public-cache"),manifest(manifestJson(hash(data),1)));fail();}catch(java.io.IOException expected){assertTrue(expected.getMessage().contains("search identities"));}
 }
 @Test public void countryCodeWithoutPlusIsDisplayedAsSourcedButDialledAsNationalIdentity(){
  row(1,"Name","972501234567","City");BusinessDirectory.Entry result=BusinessDirectory.query(database,metadata,"",0).entries.get(0);
  assertEquals("972501234567",result.phoneDisplay);assertEquals("0501234567",result.phone);
 }
}
