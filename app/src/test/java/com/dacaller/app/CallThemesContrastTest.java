package com.dacaller.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** WCAG relative luminance checks against actual drawable gradient stops. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=33)
public class CallThemesContrastTest {
 @Test public void labelsAndGesturePromptsRemainReadableInEveryPaletteAndMode(){
  Context context=RuntimeEnvironment.getApplication();
  SharedPreferences preferences=context.getSharedPreferences("da_caller",Context.MODE_PRIVATE);
  for(String id:CallThemes.IDS)for(int mode=0;mode<3;mode++){
   preferences.edit().clear().putString("call_theme",id)
    .putBoolean("dark",mode==1).putBoolean("amoled",mode==2).commit();
   CallThemes theme=new CallThemes(context);String name=id+" mode="+mode;
   readable(name+" status",theme.accent,theme.background);
   for(int kind=0;kind<3;kind++){
    int[] stops=theme.card(context,kind).getColors();assertNotNull(stops);
    for(int stop:stops){
     readable(name+" card="+kind+" caption",kind==2?Color.WHITE:theme.ink,stop);
     if(kind!=2)readable(name+" card="+kind+" accent",theme.accent,stop);
    }
   }
  }
 }
 private static void readable(String description,int text,int fill){
  double a=luminance(text),b=luminance(fill);
  double contrast=(Math.max(a,b)+0.05)/(Math.min(a,b)+0.05);
  assertTrue(description+" contrast="+contrast,contrast>=4.5);
 }
 private static double luminance(int color){
  return 0.2126*linear(Color.red(color))+0.7152*linear(Color.green(color))+0.0722*linear(Color.blue(color));
 }
 private static double linear(int channel){
  double value=channel/255.0;return value<=0.04045?value/12.92:Math.pow((value+0.055)/1.055,2.4);
 }
}
