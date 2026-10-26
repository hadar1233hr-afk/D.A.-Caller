package com.dacaller.app;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.widget.FrameLayout;
import android.widget.ImageView;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=33)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AvatarShapesTest {
 private Application context;
 @Before public void setup(){context=RuntimeEnvironment.getApplication();context.getSharedPreferences("da_caller",0).edit().clear().commit();}
 @Test public void absentOrUnknownPreferencesUseCircleWithoutChangingStoredSettings(){
  assertEquals("circle",AvatarShapes.selected(context));
  context.getSharedPreferences("da_caller",0).edit().putString(AvatarShapes.PREF,"future-shape").commit();
  assertEquals("circle",AvatarShapes.selected(context));
  assertEquals("future-shape",context.getSharedPreferences("da_caller",0).getString(AvatarShapes.PREF,""));
 }
 @Test public void widgetAndSettingsPreviewUseAllFourChosenGeometries(){
  for(String id:AvatarShapes.IDS){
   context.getSharedPreferences("da_caller",0).edit().putString(AvatarShapes.PREF,id).commit();
   assertEquals(id,AvatarShapes.selected(context));
   Bitmap widget=WidgetSupport.avatar(context,-1,"DA",96);
   Bitmap preview=AvatarShapes.preview(context,id,96);
   boolean square="square".equals(id);
   assertEquals(id+" widget corner",square?255:0,Color.alpha(widget.getPixel(1,1)));
   assertEquals(id+" preview corner",square?255:0,Color.alpha(preview.getPixel(1,1)));
   assertEquals(id+" widget center",255,Color.alpha(widget.getPixel(48,48)));
   assertEquals(id+" preview center",255,Color.alpha(preview.getPixel(48,48)));
   assertEquals(id+" shared shoulder",Color.alpha(preview.getPixel(12,4)),Color.alpha(widget.getPixel(12,4)));
  }
 }
 @Test public void clippingIncludesTheActualPhotoChildAndInitialBackground(){assertContainerClipping();}
 @Test @Config(sdk=28) public void polygonClipsPhotoOnAndroidBeforePathOutlineClipping(){assertContainerClipping();}
 private void assertContainerClipping(){
  FrameLayout avatar=AvatarShapes.frame(context);GradientDrawable oldBackground=new GradientDrawable();
  oldBackground.setColor(Color.RED);oldBackground.setCornerRadius(999);avatar.setBackground(oldBackground);
  ImageView photo=new ImageView(context);photo.setBackgroundColor(Color.BLUE);avatar.addView(photo,new FrameLayout.LayoutParams(-1,-1));
  for(String id:AvatarShapes.IDS){
   AvatarShapes.apply(avatar,id);avatar.measure(exact(96),exact(96));avatar.layout(0,0,96,96);
   Bitmap rendered=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888);avatar.draw(new Canvas(rendered));
   assertEquals(id+" photo center",Color.BLUE,rendered.getPixel(48,48));
   assertEquals(id+" photo corner","square".equals(id)?Color.BLUE:Color.TRANSPARENT,rendered.getPixel(1,1));
   photo.setVisibility(android.view.View.GONE);
   Bitmap initials=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888);avatar.draw(new Canvas(initials));
   assertEquals(id+" initials center",Color.RED,initials.getPixel(48,48));
   assertEquals(id+" initials corner","square".equals(id)?Color.RED:Color.TRANSPARENT,initials.getPixel(1,1));
   photo.setVisibility(android.view.View.VISIBLE);
  }
 }
 private int exact(int px){return android.view.View.MeasureSpec.makeMeasureSpec(px,android.view.View.MeasureSpec.EXACTLY);}
}
