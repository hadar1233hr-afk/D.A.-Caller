package com.dacaller.app;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.json.JSONObject;

/** Public, packaged business records. Never reads or writes the user's Contacts/CallLog. */
final class BusinessDirectory {
 static final int PAGE_SIZE=50,MAX_QUERY_LENGTH=160;
 static final String ASSET_DIRECTORY="business-directory/";
 private static final long MAX_DATABASE_BYTES=512L*1024*1024;
 private static final Object INSTALL_LOCK=new Object();
 private static Installed installed;
 enum Status { AVAILABLE, MISSING, INVALID, UNAVAILABLE }
 static final class Manifest {
  final String datasetVersion,sha256,sourceName,sourceUrl,license,licenseUrl,updatedAt;
  final int recordCount;
  Manifest(String version,String sha,int count,String sourceName,String sourceUrl,String license,String licenseUrl,String updatedAt){
   this.datasetVersion=version;this.sha256=sha;this.recordCount=count;this.sourceName=sourceName;
   this.sourceUrl=sourceUrl;this.license=license;this.licenseUrl=licenseUrl;this.updatedAt=updatedAt;
  }
 }
 static final class Entry {
  final long id;
  final String name,phone,phoneDisplay,city,category,address,sourceUrl,sourceId,sourceLicense,sourceUpdatedAt;
  Entry(long id,String name,String phone,String display,String city,String category,String address,String sourceUrl,String sourceId,String license,String updatedAt){
   this.id=id;this.name=name;this.phone=phone;this.phoneDisplay=display;this.city=city;this.category=category;
   this.address=address;this.sourceUrl=sourceUrl;this.sourceId=sourceId;this.sourceLicense=license;this.sourceUpdatedAt=updatedAt;
  }
 }
 static final class Page {
  final Status status;final Manifest manifest;final List<Entry> entries;final int offset;final boolean hasMore;
  Page(Status status,Manifest manifest,List<Entry> entries,int offset,boolean more){
   this.status=status;this.manifest=manifest;this.entries=Collections.unmodifiableList(new ArrayList<>(entries));this.offset=offset;this.hasMore=more;
  }
  static Page unavailable(Status status){return new Page(status,null,Collections.emptyList(),0,false);}
 }
 private static final class Installed {
  final File database;final Manifest manifest;final String appKey;
  Installed(File database,Manifest manifest,String appKey){this.database=database;this.manifest=manifest;this.appKey=appKey;}
 }
 static String normalizedPhone(String raw){
  if(raw==null)return "";
  String value=raw.trim().replace('\u00a0',' ');
  // Do not turn post-dial digits, extensions or multiple numbers into another identity.
  if(!value.matches("[+*0-9() .\\-]+"))return "";
  String dial=value.replaceAll("[^0-9+*]","");
  if(dial.startsWith("+972"))dial="0"+dial.substring(4);
  else if(dial.startsWith("00972"))dial="0"+dial.substring(5);
  else if(dial.startsWith("972"))dial="0"+dial.substring(3);
  if(dial.matches("0(?:[2-489][0-9]{7}|[57][0-9]{8})"))return dial;
  if(dial.matches("\\*[0-9]{3,6}")||dial.matches("1[0-9]{2,9}"))return dial;
  return "";
 }
 static String phonePrefix(String raw){
  if(raw==null)return "";
  String value=raw.trim().replace('\u00a0',' ');
  if(!value.matches("[+*0-9() .\\-]+"))return "";
  String digits=value.replaceAll("[^0-9+*]","");
  if(digits.startsWith("+972"))digits="0"+digits.substring(4);
  else if(digits.startsWith("00972"))digits="0"+digits.substring(5);
  else if(digits.startsWith("972"))digits="0"+digits.substring(3);
  if(digits.indexOf('+')>=0||!digits.matches("[0-9*]{1,16}"))return "";
  return digits;
 }
 static String matchExpression(String query){
  String limited=query==null?"":query.substring(0,Math.min(MAX_QUERY_LENGTH,query.length()));
  String normalized=Normalizer.normalize(limited,Normalizer.Form.NFKD).replaceAll("\\p{M}+","")
    .replaceAll("[^\\p{L}\\p{N}]+"," ").trim();
  if(normalized.isEmpty())return "";
  StringBuilder result=new StringBuilder();int count=0;
  for(String token:normalized.split(" +")){
   // Implicit intersection works with both standard and enhanced FTS4 query syntax.
   // The explicit AND keyword is a literal token on builds without FTS3_PARENTHESIS.
   if(count++>=10)break;if(result.length()>0)result.append(' ');
   // FTS4 treats a star outside the quotes as punctuation, losing prefix matching.
   result.append('"').append(token).append("*\"");
  }
  return result.toString();
 }
 static Manifest readManifest(InputStream input)throws IOException{
  if(input==null)throw new IOException("Directory manifest missing");
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];
  for(int count;(count=input.read(buffer))!=-1;){if(bytes.size()+count>65536)throw new IOException("Directory manifest too large");bytes.write(buffer,0,count);}
  try{
   JSONObject json=new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
   if(!"DA_CALLER_BUSINESSES".equals(json.getString("format"))||!(json.get("schema_version") instanceof Integer)||json.getInt("schema_version")!=1)throw new IOException("Unsupported directory format");
   String version=required(json,"dataset_version",80),sha=required(json,"database_sha256",64);
   Object measured=json.get("record_count");if(!(measured instanceof Integer)&&!(measured instanceof Long))throw new IOException("Invalid record count");
   long count=json.getLong("record_count");
   if(!version.matches("[A-Za-z0-9._-]{1,80}")||!sha.matches("[a-f0-9]{64}")||count<0||count>2_000_000)throw new IOException("Invalid directory identity");
   String source=required(json,"source_name",200),sourceUrl=required(json,"source_url",2048),license=required(json,"license",200),licenseUrl=required(json,"license_url",2048),updated=required(json,"updated_at",80);
   if(!https(sourceUrl)||!https(licenseUrl))throw new IOException("Invalid source URL");
   return new Manifest(version,sha,(int)count,source,sourceUrl,license,licenseUrl,updated);
  }catch(IOException error){throw error;}catch(Exception invalid){throw new IOException("Invalid directory manifest",invalid);}
 }
 private static String required(JSONObject object,String key,int max)throws Exception{
  Object value=object.get(key);if(!(value instanceof String))throw new IOException("Invalid manifest field");
  String text=((String)value).trim();if(text.isEmpty()||text.length()>max||text.indexOf('\u0000')>=0)throw new IOException("Invalid manifest field");return text;
 }
 static boolean https(String value){
  if(value==null||value.length()>2048)return false;
  try{java.net.URI uri=new java.net.URI(value);return "https".equalsIgnoreCase(uri.getScheme())&&uri.getHost()!=null&&uri.getUserInfo()==null;}catch(Exception invalid){return false;}
 }
 private static String digest(File file)throws IOException{
  try(InputStream input=new FileInputStream(file)){
   MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[65536];long size=0;
   for(int count;(count=input.read(buffer))!=-1;){size+=count;if(size>MAX_DATABASE_BYTES)throw new IOException("Directory database too large");digest.update(buffer,0,count);}
   StringBuilder value=new StringBuilder();for(byte part:digest.digest())value.append(String.format(Locale.ROOT,"%02x",part&255));return value.toString();
  }catch(IOException error){throw error;}catch(Exception error){throw new IOException("Cannot verify directory",error);}
 }
 /** The checksum is part of the app's immutable assets, never a network-supplied trust decision. */
 static File install(InputStream source,File directory,Manifest manifest)throws IOException{
  if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Cannot create directory cache");
  File output=new File(directory,"directory-"+manifest.sha256+".sqlite");
  if(output.isFile()&&manifest.sha256.equals(digest(output))){validateDatabase(output,manifest);return output;}
  File pending=File.createTempFile("copy-",".part",directory);boolean retained=false;
  try{
   try(FileOutputStream stream=new FileOutputStream(pending)){
    byte[] buffer=new byte[65536];long size=0;
    for(int count;(count=source.read(buffer))!=-1;){size+=count;if(size>MAX_DATABASE_BYTES)throw new IOException("Directory database too large");stream.write(buffer,0,count);}
    stream.getFD().sync();
   }
   if(!manifest.sha256.equals(digest(pending)))throw new IOException("Directory checksum mismatch");
   validateDatabase(pending,manifest);
   if(output.exists()&&!output.delete())throw new IOException("Cannot replace directory cache");
   if(!pending.renameTo(output))throw new IOException("Cannot install directory cache");
   retained=true;
   // This cache contains only packaged public data, never the archive or personal contacts.
   File[] old=directory.listFiles();if(old!=null)for(File candidate:old){
    if(!candidate.equals(output)&&candidate.isFile()&&candidate.getName().matches("directory-[a-f0-9]{64}\\.sqlite"))candidate.delete();
   }
   return output;
  }finally{if(!retained)pending.delete();}
 }
 private static void validateDatabase(File file,Manifest manifest)throws IOException{
  try(SQLiteDatabase database=SQLiteDatabase.openDatabase(file.getPath(),null,SQLiteDatabase.OPEN_READONLY)){
   if(database.getVersion()!=1)throw new IOException("Unsupported directory schema");
   try(Cursor check=database.rawQuery("PRAGMA quick_check(1)",null)){
    if(!check.moveToFirst()||!"ok".equals(check.getString(0)))throw new IOException("Invalid directory pages");
   }
   try(Cursor count=database.rawQuery("SELECT count(*) FROM businesses",null)){
    if(!count.moveToFirst()||count.getLong(0)!=manifest.recordCount)throw new IOException("Directory record count mismatch");
   }
   try(Cursor metadata=database.rawQuery("SELECT value FROM metadata WHERE key='dataset_version'",null)){
    if(!metadata.moveToFirst()||!manifest.datasetVersion.equals(metadata.getString(0)))throw new IOException("Directory metadata mismatch");
   }
   try(Cursor columns=database.rawQuery("SELECT id,name,phone,phone_display,city,category,address,source_url,source_id,source_license,source_updated_at,normalized_phone FROM businesses LIMIT 0",null)){
    if(columns.getColumnCount()!=12)throw new IOException("Invalid directory columns");
   }
   try(Cursor fts=database.rawQuery("SELECT sql FROM sqlite_master WHERE name='business_search'",null)){
    if(!fts.moveToFirst()||!fts.getString(0).toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").contains("using fts4"))throw new IOException("Missing compatible search index");
   }
   try(Cursor count=database.rawQuery("SELECT count(*) FROM business_search",null)){
    if(!count.moveToFirst()||count.getLong(0)!=manifest.recordCount)throw new IOException("Directory search count mismatch");
   }
   try(Cursor missing=database.rawQuery("SELECT b.id FROM businesses b LEFT JOIN business_search s ON s.docid=b.id WHERE s.docid IS NULL LIMIT 1",null)){
    if(missing.moveToFirst())throw new IOException("Directory search identities mismatch");
   }
  }catch(SQLiteException invalid){throw new IOException("Invalid directory database",invalid);}
 }
 private static Installed ensureInstalled(Context context)throws IOException{
  Context app=context.getApplicationContext();File root=new File(app.getNoBackupFilesDir(),"business-directory");
  String key=root.getAbsolutePath()+":"+app.getApplicationInfo().sourceDir;
  synchronized(INSTALL_LOCK){
   if(installed!=null&&installed.appKey.equals(key)&&installed.database.isFile())return installed;
   Manifest manifest;
   try(InputStream input=app.getAssets().open(ASSET_DIRECTORY+"manifest.json")){manifest=readManifest(input);}
   File database;
   try(InputStream source=app.getAssets().open(ASSET_DIRECTORY+"israel-businesses.sqlite")){database=install(source,root,manifest);}
   installed=new Installed(database,manifest,key);return installed;
  }
 }
 /** Call only from a background executor; every query loads at most fifty-one records. */
 static Page search(Context context,String text,int requestedOffset){
  try{
   Installed current=ensureInstalled(context);
   try(SQLiteDatabase database=SQLiteDatabase.openDatabase(current.database.getPath(),null,SQLiteDatabase.OPEN_READONLY)){
    return query(database,current.manifest,text,requestedOffset);
   }
  }catch(java.io.FileNotFoundException missing){return Page.unavailable(Status.MISSING);}
  catch(IOException invalid){return Page.unavailable(Status.INVALID);}
  catch(RuntimeException unavailable){synchronized(INSTALL_LOCK){installed=null;}return Page.unavailable(Status.UNAVAILABLE);}
 }
 static Page query(SQLiteDatabase database,Manifest manifest,String text,int requestedOffset){
  String query=text==null?"":text.trim();query=query.substring(0,Math.min(MAX_QUERY_LENGTH,query.length()));
  int offset=Math.max(0,Math.min(2_000_000,requestedOffset));String where="";String[] arguments;
  String prefix=phonePrefix(query),match=matchExpression(query);
  if(!query.isEmpty()&&!prefix.isEmpty()){
   where=" WHERE normalized_phone LIKE ?";arguments=new String[]{prefix+"%",String.valueOf(PAGE_SIZE+1),String.valueOf(offset)};
  }else if(!query.isEmpty()&&!match.isEmpty()){
   where=" WHERE id IN (SELECT docid FROM business_search WHERE business_search MATCH ?)";
   arguments=new String[]{match,String.valueOf(PAGE_SIZE+1),String.valueOf(offset)};
  }else if(!query.isEmpty())return new Page(Status.AVAILABLE,manifest,Collections.emptyList(),offset,false);
  else arguments=new String[]{String.valueOf(PAGE_SIZE+1),String.valueOf(offset)};
  ArrayList<Entry> entries=new ArrayList<>();
  try(Cursor rows=database.rawQuery("SELECT id,name,phone,phone_display,city,category,address,source_url,source_id,source_license,source_updated_at,normalized_phone FROM businesses"+
    where+" ORDER BY name COLLATE NOCASE,id LIMIT ? OFFSET ?",arguments)){
   while(rows.moveToNext()){
    String phone=rows.getString(2),name=rows.getString(1),url=rows.getString(7),source=rows.getString(8),display=clean(rows,3,phone),canonical=normalizedPhone(phone);
    if(rows.getLong(0)<=0||name==null||name.trim().isEmpty()||name.length()>300||name.matches(".*[\\p{Cc}\\p{Cf}].*")||canonical.isEmpty()||!canonical.equals(normalizedPhone(display))
      ||!canonical.equals(rows.getString(11))||!https(url)||source==null||source.isEmpty())throw new IllegalStateException("Invalid business record");
    // Dial the verified national identity rather than a bare country-code prefix such as 97250… .
    entries.add(new Entry(rows.getLong(0),name,canonical,display,clean(rows,4,""),clean(rows,5,""),clean(rows,6,""),url,source,clean(rows,9,manifest.license),clean(rows,10,manifest.updatedAt)));
   }
  }
  boolean more=entries.size()>PAGE_SIZE;if(more)entries.remove(PAGE_SIZE);
  return new Page(Status.AVAILABLE,manifest,entries,offset,more);
 }
 private static String clean(Cursor cursor,int column,String fallback){String value=cursor.getString(column);return value==null||value.trim().isEmpty()?fallback:value.trim();}
 private BusinessDirectory(){}
}
