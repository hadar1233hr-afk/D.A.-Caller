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
   // Launch/finish transitions can show a temporary white splash even with
   // animations disabled; wait for the full window, not Activity.onCreate.
   Thread.sleep(650);
   getInstrumentation().waitForIdleSync();
   assertFalse(activity.isFinishing());
   Bitmap screenshot=getInstrumentation().getUiAutomation().takeScreenshot();
   assertNotNull("No screenshot "+theme,screenshot);
   assertTrue("Blank white activity frame for "+theme,
      screenshot.getPixel(screenshot.getWidth()/2,screenshot.getHeight()-95)!=android.graphics.Color.WHITE);
   File image=new File(destination,"call-"+i+"-"+theme+".png");
   try(FileOutputStream out=new FileOutputStream(image)){
    assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG,100,out));
   }
   // CI test logs survive automatic emulator app uninstall. Log small JPEG
   // chunks of synthetic layout screenshots only (never private call data).
   java.io.ByteArrayOutputStream jpeg=new java.io.ByteArrayOutputStream();
   assertTrue(screenshot.compress(Bitmap.CompressFormat.JPEG,70,jpeg));
   String base64=android.util.Base64.encodeToString(jpeg.toByteArray(),android.util.Base64.NO_WRAP);
   int part=0;
   for(int cursor=0;cursor<base64.length();cursor+=2500){
    android.util.Log.i("DA_UI_SHOT","SCREEN:"+theme+":"+part+++":"+
      base64.substring(cursor,Math.min(cursor+2500,base64.length())));
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
