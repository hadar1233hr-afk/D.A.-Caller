package com.dacaller.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/** Test-APK-only document endpoint. It never reads a user-selected document. */
public final class BackupDocumentProvider extends ContentProvider {
 static final Uri DOCUMENT=Uri.parse("content://com.dacaller.app.instrumentation.backupfixture/settings.json");
 private File file(Uri uri)throws FileNotFoundException{
  if(!DOCUMENT.equals(uri))throw new FileNotFoundException("Unknown fixture document");
  return new File(getContext().getCacheDir(),"personalization-settings-fixture.json");
 }
 @Override public boolean onCreate(){return true;}
 @Override public String getType(Uri uri){return "application/json";}
 @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
  return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.parseMode(mode));
 }
 @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
  String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
  MatrixCursor result=new MatrixCursor(columns);Object[] row=new Object[columns.length];
  for(int i=0;i<columns.length;i++){
   if(OpenableColumns.DISPLAY_NAME.equals(columns[i]))row[i]="DA-CALLER-settings-fixture.json";
   else if(OpenableColumns.SIZE.equals(columns[i]))try{row[i]=file(uri).length();}catch(FileNotFoundException ignored){row[i]=0;}
  }
  result.addRow(row);return result;
 }
 @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
 @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException();}
 @Override public int delete(Uri uri,String selection,String[] args){
  try{return file(uri).delete()?1:0;}catch(FileNotFoundException ignored){return 0;}
 }
}
