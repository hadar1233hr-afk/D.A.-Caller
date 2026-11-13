package com.dacaller.app;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.graphics.Rect;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=33)
public final class CallIslandPositionTest {
 private SharedPreferences prefs;
 @Before public void setup(){prefs=RuntimeEnvironment.getApplication().getSharedPreferences("island-position-test",Context.MODE_PRIVATE);prefs.edit().clear().commit();}
 @Test public void defaultStaysCenteredBelowUntilAUserDragAndResetReturnsThere(){
  Rect portrait=new Rect(12,8,308,780);
  assertFalse(CallIslandPosition.topArea(prefs));assertEquals(new Point(30,8),CallIslandPosition.resolve(prefs,portrait,260,56));
  CallIslandPosition.save(RuntimeEnvironment.getApplication(),prefs,portrait,260,56,48,700);
  assertTrue(prefs.getBoolean(CallIslandPosition.SET,false));assertEquals(new Point(48,700),CallIslandPosition.resolve(prefs,portrait,260,56));
  CallIslandPosition.reset(RuntimeEnvironment.getApplication(),prefs);assertEquals(new Point(30,8),CallIslandPosition.resolve(prefs,portrait,260,56));
 }
 @Test public void savedFractionsSurviveRotationAndExpandedControlsStayInTheirViewport(){
  Rect portrait=new Rect(12,8,308,780);CallIslandPosition.save(RuntimeEnvironment.getApplication(),prefs,portrait,180,56,128,724);
  Rect landscape=new Rect(12,8,788,300);Point point=CallIslandPosition.resolve(prefs,landscape,260,220);
  assertEquals(528,point.x);assertTrue(point.y>=8&&point.y+220<=300);assertTrue(point.x+260<=788);
  Rect keyboard=new Rect(12,8,308,240);point=CallIslandPosition.resolve(prefs,keyboard,280,200);
  assertTrue(point.x>=12&&point.x+280<=308);assertTrue(point.y>=8&&point.y+200<=240);
 }
 @Test public void invalidStoredCoordinatesAreBoundedAndAnUnusableDimensionDoesNotDivideByZero(){
  prefs.edit().putBoolean(CallIslandPosition.SET,true).putInt(CallIslandPosition.X,Integer.MIN_VALUE).putInt(CallIslandPosition.Y,Integer.MAX_VALUE).commit();
  assertEquals(new Point(12,724),CallIslandPosition.resolve(prefs,new Rect(12,8,308,780),180,56));
  CallIslandPosition.save(RuntimeEnvironment.getApplication(),prefs,new Rect(12,8,112,58),180,56,-999,999);
  assertEquals(5000,prefs.getInt(CallIslandPosition.X,-1));assertEquals(0,prefs.getInt(CallIslandPosition.Y,-1));
 }
 @Test public void statusIconsAndCentralCutoutForceSafeActualFallbackWithoutPrivilegedWindows(){
  Rect viewport=new Rect(12,8,308,780);
  Point top=CallIslandPosition.clamp(viewport,176,56,-100,-100,24,60,Collections.emptyList(),8);
  assertEquals(new Point(72,8),top);
  Point expanded=CallIslandPosition.clamp(viewport,296,320,12,8,24,60,Collections.emptyList(),8);
  assertEquals(new Point(12,32),expanded);
  Point cutout=CallIslandPosition.clamp(viewport,176,56,72,8,24,60,Collections.singletonList(new Rect(140,0,180,36)),8);
  assertEquals(44,cutout.y);assertFalse(Rect.intersects(new Rect(cutout.x,cutout.y,cutout.x+176,cutout.y+56),new Rect(140,0,180,36)));
  assertEquals(new Point(12,200),CallIslandPosition.clamp(viewport,176,56,12,200,24,60,Collections.emptyList(),8));
 }
}
