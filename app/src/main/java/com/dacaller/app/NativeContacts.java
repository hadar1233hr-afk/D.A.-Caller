package com.dacaller.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.Intent;
import android.provider.ContactsContract;
import android.widget.Toast;

/** Optional system contact editor, including devices where direct provider writes are restricted. */
final class NativeContacts {
 static final int REQUEST_EDIT=90;
 static Intent intent(long contactId,String name,String phone){
  Intent intent=contactId>0
   ?new Intent(Intent.ACTION_EDIT).setDataAndType(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI,contactId),ContactsContract.Contacts.CONTENT_ITEM_TYPE)
   :new Intent(Intent.ACTION_INSERT).setDataAndType(ContactsContract.Contacts.CONTENT_URI,ContactsContract.Contacts.CONTENT_TYPE);
  if(name!=null&&!name.trim().isEmpty())intent.putExtra(ContactsContract.Intents.Insert.NAME,name.trim());
  if(phone!=null&&!phone.trim().isEmpty())intent.putExtra(ContactsContract.Intents.Insert.PHONE,phone.trim());
  intent.putExtra("finishActivityOnSaveCompleted",true);return intent;
 }
 static boolean open(Activity activity,long contactId,String name,String phone){
  try{activity.startActivityForResult(intent(contactId,name,phone),REQUEST_EDIT);return true;}
  catch(ActivityNotFoundException|SecurityException failure){Toast.makeText(activity,I18n.text(activity,"עורך אנשי הקשר אינו זמין"),Toast.LENGTH_LONG).show();return false;}
 }
 private NativeContacts(){}
}
