package com.dacaller.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.test.InstrumentationTestCase;
import java.io.File;
import java.io.FileOutputStream;

/** On-device visual snapshots of five synthetic in-call layouts (no phone/SIM data). */
public final class CallLayoutSnapshotTest extends InstrumentationTestCase {
 public void testFiveDesignsFitOnSmallAndroid13Display() throws Exception {
  Context target=getInstrumentation().getTargetContext();
  File destination=new File(target.getExternalFilesDir(null),"snapshots");
  assertTrue(destination.isDirectory()||destination.mkdirs());
  SharedPreferences settings=target.getSharedPreferences("da_caller",Context.MODE_PRIVATE);
  for(int i=0;i<CallThemes.IDS.length;i++){
   final String theme=CallThemes.IDS[i];
   settings.edit().putString("call_theme",theme)
     .putString("language","he").putBoolean("dark",false).putBoolean("amoled",false)
     .putBoolean("call_btn_keypad",true).putBoolean("call_btn_mute",true)
     .putBoolean("call_btn_speaker",true).commit();
   Intent intent=new Intent(target,CallScreenActivity.class);
   intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
   intent.putExtra("preview_ui",true);
   Activity activity=getInstrumentation().startActivitySync(intent);
   getInstrumentation().waitForIdleSync();
   assertFalse(activity.isFinishing());
   Bitmap screenshot=getInstrumentation().getUiAutomation().takeScreenshot();
   assertNotNull("No screenshot "+theme,screenshot);
   File image=new File(destination,"call-"+i+"-"+theme+".png");
   try(FileOutputStream out=new FileOutputStream(image)){
    assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG,100,out));
   }
   screenshot.recycle();
   // Gradle uninstalls the app after tests. Keep reference screenshots in a public
   // shell-owned directory so the CI job can pull them after test teardown.
   String publicPath="/sdcard/Download/DA-CALLER-snapshots";
   String command="mkdir -p "+publicPath+" && screencap -p "+publicPath+"/call-"+i+"-"+theme+".png";
   android.os.ParcelFileDescriptor result=getInstrumentation().getUiAutomation().executeShellCommand(command);
   try(android.os.ParcelFileDescriptor.AutoCloseInputStream output=
       new android.os.ParcelFileDescriptor.AutoCloseInputStream(result)){
    byte[] buffer=new byte[128];
    while(output.read(buffer)!=-1){}
   }
   getInstrumentation().runOnMainSync(activity::finish);
   getInstrumentation().waitForIdleSync();
  }
 }
}
