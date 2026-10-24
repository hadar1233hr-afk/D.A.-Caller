package com.dacaller.app;

import android.database.Cursor;
import android.provider.ContactsContract;
import java.util.HashMap;
import java.util.Map;

/** Maps the provider's Data columns by name, preserving row identities and custom types. */
final class ContactData {
 static final String[] PROJECTION={ContactsContract.Data._ID,ContactsContract.Data.RAW_CONTACT_ID,
  ContactsContract.Data.MIMETYPE,ContactsContract.Data.DATA1,ContactsContract.Data.DATA2,
  ContactsContract.Data.DATA3,ContactsContract.Data.DATA4,ContactsContract.Data.DATA5,
  ContactsContract.Data.DATA6,ContactsContract.Data.DATA7,ContactsContract.Data.DATA8,
  ContactsContract.Data.DATA9,ContactsContract.Data.DATA10};
 final HashMap<String,String> values=new HashMap<>(),types=new HashMap<>();
 final HashMap<String,Long> ids=new HashMap<>();
 long rawId=-1,photoId=-1;
 static ContactData read(Cursor rows){
  ContactData result=new ContactData();int phone=0,email=0;
  while(rows.moveToNext()){
   long id=rows.getLong(rows.getColumnIndexOrThrow(ContactsContract.Data._ID));
   long raw=rows.getLong(rows.getColumnIndexOrThrow(ContactsContract.Data.RAW_CONTACT_ID));
   if(result.rawId<0)result.rawId=raw;
   String mime=result.column(rows,ContactsContract.Data.MIMETYPE);
   String a=result.column(rows,ContactsContract.Data.DATA1),b=result.column(rows,ContactsContract.Data.DATA2);
   if(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE.equals(mime)){
    result.put("first",b,id);result.put("last",result.column(rows,ContactsContract.Data.DATA3),id);result.ids.putIfAbsent("name",id);
   }else if(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE.equals(mime)&&phone<4){
    String key="phone"+phone++;result.put(key,a,id);result.types.put(key,b);
   }else if(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE.equals(mime)&&email<3){
    String key="email"+email++;result.put(key,a,id);result.types.put(key,b);
   }else if(ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE.equals(mime)){
    if(result.photoId<0)result.photoId=id;
   }else if(ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE.equals(mime))result.put("nickname",a,id);
   else if(ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE.equals(mime)){
    result.put("company",a,id);result.put("job",result.column(rows,ContactsContract.CommonDataKinds.Organization.TITLE),id);
    result.put("department",result.column(rows,ContactsContract.CommonDataKinds.Organization.DEPARTMENT),id);
   }else if(ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE.equals(mime)){
    result.put("street",result.column(rows,ContactsContract.CommonDataKinds.StructuredPostal.STREET),id);
    result.put("city",result.column(rows,ContactsContract.CommonDataKinds.StructuredPostal.CITY),id);
    result.put("region",result.column(rows,ContactsContract.CommonDataKinds.StructuredPostal.REGION),id);
    result.put("zipcode",result.column(rows,ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE),id);
    result.put("country",result.column(rows,ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY),id);
   }else if(ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE.equals(mime))result.put("website",a,id);
   else if(ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE.equals(mime))result.put("notes",a,id);
   else if(ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE.equals(mime)){
    if(String.valueOf(ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY).equals(b))result.put("birthday",a,id);
    else if(String.valueOf(ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY).equals(b))result.put("anniversary",a,id);
   }
  }
  return result;
 }
 private String column(Cursor row,String column){String value=row.getString(row.getColumnIndexOrThrow(column));return value==null?"":value;}
 private void put(String key,String value,long id){if(!values.containsKey(key)){values.put(key,value);ids.put(key,id);}}
}
