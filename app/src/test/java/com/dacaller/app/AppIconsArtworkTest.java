package com.dacaller.app;

import static org.junit.Assert.*;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.HashSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Host Skia rendering checks; launcher/OEM and physical-device behavior need native verification. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=33) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class AppIconsArtworkTest {
 @Test public void nineSmallColorAndThemedSilhouettesAreVisibleAndDistinct()throws Exception{
  Context app=RuntimeEnvironment.getApplication();HashSet<Integer> colors=new HashSet<>(),themed=new HashSet<>();
  File previews=new File("build/reports/launcher-icons");assertTrue(previews.isDirectory()||previews.mkdirs());
  for(String id:AppIcons.IDS){
   AdaptiveIconDrawable icon=(AdaptiveIconDrawable)AppIcons.preview(app,id);
   Bitmap color=render(icon,48);assertTrue(id+" distinct full-color icon",colors.add(hash(color)));
   int gold=0;for(int pixel:pixels(color))if(Color.red(pixel)>230&&Color.green(pixel)>165&&Color.blue(pixel)<230)gold++;
   assertTrue(id+" has visible brilliant gold at 48px",gold>45);
   Bitmap mono=render(icon.getMonochrome(),48);assertTrue(id+" distinct themed silhouette",themed.add(hash(mono)));
   int visible=0;for(int pixel:pixels(mono))if(Color.alpha(pixel)>127)visible++;
   assertTrue(id+" themed mark remains readable",visible>55);assertTrue(id+" themed mark retains negative space",visible<48*48/2);
   save(render(icon,192),new File(previews,id+"-color.png"));
   save(render(icon.getMonochrome(),192),new File(previews,id+"-monochrome.png"));
  }
  assertEquals(9,colors.size());assertEquals(9,themed.size());
 }
 @Test @Config(sdk=26) public void allNineAdaptiveIconsInflateWithoutAndroid13OnlyResources(){
  Context app=RuntimeEnvironment.getApplication();HashSet<Integer> rendered=new HashSet<>();
  for(String id:AppIcons.IDS){
   Drawable icon=AppIcons.preview(app,id);assertTrue(icon instanceof AdaptiveIconDrawable);
   assertTrue(rendered.add(hash(render(icon,48))));
  }
  assertEquals(9,rendered.size());
 }
 private static Bitmap render(Drawable drawable,int size){
  Bitmap image=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);drawable.setBounds(0,0,size,size);drawable.draw(new Canvas(image));return image;
 }
 private static int[] pixels(Bitmap bitmap){int[] pixels=new int[bitmap.getWidth()*bitmap.getHeight()];bitmap.getPixels(pixels,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());return pixels;}
 private static int hash(Bitmap bitmap){return Arrays.hashCode(pixels(bitmap));}
 private static void save(Bitmap image,File file)throws Exception{try(FileOutputStream output=new FileOutputStream(file)){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,output));}}
}
