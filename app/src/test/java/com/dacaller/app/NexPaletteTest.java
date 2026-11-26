package com.dacaller.app;

import org.junit.Test;
import static org.junit.Assert.*;

/** Pure color-token checks, not a substitute for native screenshots or layout tests. */
public final class NexPaletteTest {
 private static double linear(int n){double x=n/255.;return x<=.04045?x/12.92:Math.pow((x+.055)/1.055,2.4);}
 private static double lum(int c){return .2126*linear((c>>>16)&255)+.7152*linear((c>>>8)&255)+.0722*linear(c&255);}
 private static void contrast(int a,int b){double x=lum(a),y=lum(b);assertTrue("Text contrast", (Math.max(x,y)+.05)/(Math.min(x,y)+.05)>=4.5);}
 @Test public void textAndActionColorsPassContrastInAllDisplayModes(){
  for(boolean dark:new boolean[]{false,true})for(boolean amoled:new boolean[]{false,true}){
   if(amoled&&!dark)continue;
   for(int bg:new int[]{NexPalette.background(dark,amoled),NexPalette.surface(dark,amoled),NexPalette.soft(dark)}){
    contrast(NexPalette.ink(dark),bg);
    contrast(NexPalette.muted(dark),bg);
    contrast(NexPalette.primaryText(dark),bg);
    contrast(AppAppearance.gold(dark),bg);
    contrast(dark?0xFFE4E4E4:0xFF616161,bg);
   }
   contrast(NexPalette.onPrimary(dark),NexPalette.primary(dark));
   contrast(NexPalette.onCall(dark),NexPalette.call(dark));
  }
 }
 @Test public void amoledRetainsBlackBackground(){
  assertEquals(0xFF000000,NexPalette.background(true,true));
  assertEquals(0xFF000000,NexPalette.background(false,true));
  assertEquals(0xFF0A0A0A,NexPalette.surface(true,true));
  assertEquals(0xFFF6F0E8,NexPalette.background(false,false));
  assertEquals(0xFF241A17,NexPalette.background(true,false));
 }
}
