package com.dacaller.app;
import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.ContactsContract;
import java.util.*;
final class Contacts {
 static class Person {
  final String name,phone,key;
  Person(String n,String p){name=n;phone=p;key=p.replaceAll("[^+0-9]","");}
 }
 static boolean openContact(Context c,String phone){
  if(c.checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED)return false;
  Uri uri=Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,Uri.encode(phone));
  try(Cursor cursor=c.getContentResolver().query(uri,new String[]{ContactsContract.PhoneLookup._ID},null,null,null)){
   if(cursor!=null && cursor.moveToFirst()){
    long contactId=cursor.getLong(0);
    Intent intent=new Intent(Intent.ACTION_VIEW,android.content.ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI,contactId));
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);c.startActivity(intent);return true;
   }
  }catch(Exception ignored){}
  return false;
 }
 static List<Person> all(Context c) {
  ArrayList<Person> out=new ArrayList<>();
  if(c.checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED)return out;
  HashSet<String> seen=new HashSet<>();
  String[] columns={ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,ContactsContract.CommonDataKinds.Phone.NUMBER};
  try(Cursor cursor=c.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,columns,null,null,ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY+" ASC")){
   if(cursor!=null)while(cursor.moveToNext()){
    String n=cursor.getString(0),p=cursor.getString(1);
    if(n==null||p==null||p.trim().isEmpty())continue;
    Person person=new Person(n,p);
    if(seen.add(person.key))out.add(person);
   }
  }catch(Exception ignored){}
  return out;
 }
}