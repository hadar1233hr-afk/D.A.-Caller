package com.dacaller.app;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

final class Contacts {
 static final class Person {
  final String name, phone, key;
  final long id;
  Person(String name,String phone,long id){
   this.name=name==null||name.trim().isEmpty()?"ללא שם":name.trim();
   this.phone=phone==null?"":phone.trim();
   this.key=this.phone.replaceAll("[^+0-9]","");
   this.id=id;
  }
  Person(String name,String phone){this(name,phone,-1);}
 }
 static String canonical(String phone){
  if(phone==null)return "";
  String digits=phone.replaceAll("[^0-9]","");
  if(digits.startsWith("972") && digits.length()>=11)digits="0"+digits.substring(3);
  if(digits.startsWith("00972") && digits.length()>=13)digits="0"+digits.substring(5);
  return digits;
 }
 static boolean openContact(Context context,String number){
  if(context.checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED)return false;
  if(number==null||number.trim().isEmpty())return false;
  Uri lookup=Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,Uri.encode(number));
  try(Cursor cursor=context.getContentResolver().query(lookup,new String[]{ContactsContract.PhoneLookup._ID},null,null,null)){
   if(cursor!=null && cursor.moveToFirst())return openById(context,cursor.getLong(0));
  }catch(Exception ignored){}
  return false;
 }
 static boolean openById(Context context,long id){
  if(id<=0)return false;
  try{
   Intent intent=new Intent(Intent.ACTION_VIEW,ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI,id));
   intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
   context.startActivity(intent);return true;
  }catch(Exception ignored){return false;}
 }
 static List<Person> all(Context context){
  ArrayList<Person> found=new ArrayList<>();
  if(context.checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED)return found;
  LinkedHashMap<Long,String> names=new LinkedHashMap<>();
  HashMap<Long,String> firstPhone=new HashMap<>();
  String[] contactsProjection={
   ContactsContract.Contacts._ID,
   ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
  };
  try(Cursor rows=context.getContentResolver().query(ContactsContract.Contacts.CONTENT_URI,contactsProjection,null,null,
      ContactsContract.Contacts.SORT_KEY_PRIMARY+" ASC")){
   if(rows!=null)while(rows.moveToNext()){
    long id=rows.getLong(0);
    String name=rows.getString(1);
    names.put(id,name==null||name.trim().isEmpty()?"ללא שם":name);
   }
  }catch(Exception ignored){}
  String[] numbersProjection={
   ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
   ContactsContract.CommonDataKinds.Phone.NUMBER
  };
  try(Cursor numbers=context.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
      numbersProjection,null,null,null)){
   if(numbers!=null)while(numbers.moveToNext()){
    long id=numbers.getLong(0);
    String n=numbers.getString(1);
    if(n!=null&&!n.trim().isEmpty()&&!firstPhone.containsKey(id)){
     firstPhone.put(id,n);
    }
   }
  }catch(Exception ignored){}
  for(java.util.Map.Entry<Long,String> item:names.entrySet()){
   long id=item.getKey();
   found.add(new Person(item.getValue(),firstPhone.get(id),id));
  }
  // OEM providers sometimes expose the phone table but not the regular contacts list.
  // In that case, use the phone table as a fallback instead of showing an empty screen.
  if(found.isEmpty()){
   String[] columns={
    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
    ContactsContract.CommonDataKinds.Phone.NUMBER
   };
   HashSet<Long> ids=new HashSet<>();
   try(Cursor rows=context.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,columns,null,null,null)){
    if(rows!=null)while(rows.moveToNext()){
     long id=rows.getLong(0);
     if(ids.add(id))found.add(new Person(rows.getString(1),rows.getString(2),id));
    }
   }catch(Exception ignored){}
  }
  Collator collator=Collator.getInstance(new Locale("he","IL"));
  Collections.sort(found,(left,right)->collator.compare(left.name,right.name));
  return found;
 }
}