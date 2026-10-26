package com.dacaller.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Current device contact identities for an archive snapshot; never persisted into the archive. */
final class ArchiveContacts {
 static final class Match {
  final long id;
  final String name;
  Match(long id,String name){this.id=id;this.name=name==null?"":name.trim();}
 }
 static boolean allowed(Context context){
  return context.checkSelfPermission(Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED;
 }
 static String key(String raw){
  String number=raw==null?"":raw.trim();
  if(!CallLauncher.isCallable(number))return "";
  // Do not alias service codes or extension sequences to unrelated plain numbers.
  if(number.indexOf('*')>=0||number.indexOf('#')>=0||number.indexOf(',')>=0||number.indexOf(';')>=0)return number;
  return PhoneNumberUtils.normalizeNumber(number);
 }
 static Map<String,Match> resolve(Context context,List<CallHistory.Entry> calls,BooleanSupplier cancelled){
  if(!allowed(context)||cancelled.getAsBoolean())return Collections.emptyMap();
  LinkedHashMap<String,String> unique=new LinkedHashMap<>();
  for(CallHistory.Entry call:calls){
   if(cancelled.getAsBoolean())return Collections.emptyMap();
   String key=key(call.number);
   if(!key.isEmpty())unique.putIfAbsent(key,call.number);
  }
  LinkedHashMap<String,Match> names=new LinkedHashMap<>();
  for(Map.Entry<String,String> entry:unique.entrySet()){
   if(cancelled.getAsBoolean()||!allowed(context))return Collections.emptyMap();
   String number=PhoneNumberUtils.extractNetworkPortion(entry.getValue());
   if(number.isEmpty()||number.indexOf('*')>=0||number.indexOf('#')>=0)continue;
   // PhoneLookup uses the provider's phone-number index and country/number matching.
   // Query each distinct number once, including contacts' secondary numbers; never
   // guess an identity from a fixed suffix or scan the full contacts table on the UI.
   Uri uri=Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,Uri.encode(number));
   try(Cursor rows=context.getContentResolver().query(uri,new String[]{
     ContactsContract.PhoneLookup._ID,ContactsContract.PhoneLookup.DISPLAY_NAME},null,null,null)){
    if(rows!=null&&rows.moveToFirst()){
     long id=rows.getLong(0);String name=rows.getString(1);
     if(id>0&&name!=null&&!name.trim().isEmpty())names.put(entry.getKey(),new Match(id,name));
    }
   }catch(SecurityException revoked){return Collections.emptyMap();}
   catch(RuntimeException unavailable){/* A missing identity keeps the original number visible. */}
  }
  return cancelled.getAsBoolean()||!allowed(context)?Collections.emptyMap():Collections.unmodifiableMap(names);
 }
 private ArchiveContacts(){}
}
