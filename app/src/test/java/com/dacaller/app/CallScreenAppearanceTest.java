package com.dacaller.app;

import static org.junit.Assert.*;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

/** Native font metrics on the smallest supported call viewport; no Telecom fixture claims. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=33,application=Application.class,qualifiers="w320dp-h570dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class CallScreenAppearanceTest {
 private ActivityController<CallScreenActivity> controller;
 private SharedPreferences prefs;
 private float originalFontScale;
 private ViewGroup root;
 private static final String[] PANELS={"normal","audio","keypad","more"};

 @Before public void prepare(){
  Application app=RuntimeEnvironment.getApplication();
  prefs=app.getSharedPreferences("da_caller",Context.MODE_PRIVATE);prefs.edit().clear().commit();
  originalFontScale=app.getResources().getConfiguration().fontScale;
  RuntimeEnvironment.setFontScale(1.3f);PhoneSessionService.current=null;PhoneSessionService.instance=null;
 }
 @After public void clean(){close();RuntimeEnvironment.setFontScale(originalFontScale);PhoneSessionService.current=null;PhoneSessionService.instance=null;}
 private void close(){if(controller!=null){controller.pause().stop().destroy();controller=null;}}
 private CallScreenActivity open(String style,String panel,String language,String name){
  close();prefs.edit().putString("call_theme",style).putString("language",language).commit();
  Intent preview=new Intent(RuntimeEnvironment.getApplication(),CallScreenActivity.class)
    .putExtra("preview_ui",true).putExtra("preview_panel",panel).putExtra("preview_name",name);
  controller=Robolectric.buildActivity(CallScreenActivity.class,preview).setup().visible();
  root=(ViewGroup)controller.get().findViewById(android.R.id.content).findViewWithTag("call-root");
  assertNotNull(root);
  // 570dp screen minus the native 24dp status and 48dp navigation areas.
  root.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),
    View.MeasureSpec.makeMeasureSpec(498,View.MeasureSpec.EXACTLY));root.layout(0,0,320,498);
  return controller.get();
 }
 private View tag(String value){View found=root.findViewWithTag(value);assertNotNull(value,found);return found;}
 private Rect bounds(View view){
  // TextView's single-line RTL scrolling can be enormous. Map its viewport,
  // rather than mistaking its internal text origin for the physical control.
  Rect rect=new Rect(view.getScrollX(),view.getScrollY(),view.getScrollX()+view.getWidth(),view.getScrollY()+view.getHeight());
  root.offsetDescendantRectToMyCoords(view,rect);return rect;
 }
 private void targetFits(View view){
  Rect rect=bounds(view);assertTrue(view.getTag()+" outside root: "+rect,rect.left>=0&&rect.top>=0&&rect.right<=root.getWidth()&&rect.bottom<=root.getHeight());
  assertTrue(view.getTag()+" narrower than48dp",view.getWidth()>=48);assertTrue(view.getTag()+" shorter than48dp",view.getHeight()>=48);
 }
 private void textFits(View view){
  if(!view.isShown())return;
  if(view instanceof TextView){TextView text=(TextView)view;if(text.length()>0){
   assertNotNull("Missing glyph layout "+text.getTag(),text.getLayout());
   int available=text.getHeight()-text.getCompoundPaddingTop()-text.getCompoundPaddingBottom();
   assertTrue("Clipped glyph rows "+text.getTag()+" '"+text.getText()+"' "+text.getLayout().getHeight()+"/"+available,text.getLayout().getHeight()<=available+1);
  }}
  if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)textFits(((ViewGroup)view).getChildAt(i));
 }
 private void noAppBrand(View view){
  if(view instanceof TextView){String text=((TextView)view).getText().toString();assertFalse("Redundant call-screen brand: "+text,text.equals("DA CALLER")||text.equals("DA / 01")||text.startsWith("DA CALLER ·"));}
  if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)noAppBrand(((ViewGroup)view).getChildAt(i));
 }

 @Test public void allFiveUnbrandedDesignsKeepLongCallerAndReachablePanelsAtLargeFont(){
  for(String style:CallThemes.IDS)for(String language:new String[]{"he","en"})for(String panel:PANELS){
   String name=language.equals("he")?"דנה לדוגמה — שם ארוך לבדיקת תצוגה":"Dana Example — Long display name for testing";
   open(style,panel,language,name);noAppBrand(root);targetFits(tag("call-end"));
   for(String action:new String[]{"more","audio","mute","keypad"})targetFits(tag("call-action-"+action));
   TextView caller=(TextView)tag("call-caller");assertEquals(name,caller.getText().toString());
   Rect barrier=bounds(tag("normal".equals(panel)?"call-dock":"call-sheet-"+panel));
   for(String value:new String[]{"call-caller","call-identity","call-timer"}){
    TextView text=(TextView)tag(value);if(text.isShown()&&text.length()>0){assertFalse(style+" "+panel+" covers "+value,Rect.intersects(bounds(text),barrier));textFits(text);}
   }
   textFits(tag("call-dock"));
  }
 }
 @Test public void nothingCallerStackHasTheSameHorizontalCenterAsGoogle(){
  for(String language:new String[]{"he","en"})for(String style:new String[]{"pixel","ivory"}){
   open(style,"normal",language,"Dana Example");
   for(String value:new String[]{"call-caller","call-identity","call-portrait","call-timer"})assertEquals(value,root.getWidth()/2f,bounds(tag(value)).exactCenterX(),1f);
   if(style.equals("ivory")){
    assertEquals(Gravity.CENTER,((TextView)tag("call-phase")).getGravity());
    assertEquals(I18n.text(controller.get(),"שיחה פעילה"),((TextView)tag("call-phase")).getText().toString());
    assertTrue(bounds(tag("call-portrait")).bottom<=bounds(tag("call-caller")).top);
    assertTrue(bounds(tag("call-identity")).bottom<=bounds(tag("call-phase")).top);
    assertTrue(bounds(tag("call-phase")).bottom<=bounds(tag("call-timer")).top);
   }
  }
 }
 @Test public void allFiveKeypadsRetainLargeDigitsAndGoogleRetainsBundledNunito650(){
  for(String style:CallThemes.IDS){
   CallScreenActivity activity=open(style,"keypad","en","Dana Example");
   for(char digit:"123456789*0#".toCharArray()){
    targetFits(tag("dtmf-"+digit));TextView number=(TextView)tag("dtmf-digit-"+digit);
    assertTrue(style+" keypad digit shrank",number.getTextSize()/activity.getResources().getDisplayMetrics().scaledDensity>=21.9f);
    if(style.equals("pixel")){
     assertEquals("'wght' 650",number.getFontVariationSettings());
     Paint expected=new Paint(number.getPaint());expected.setTypeface(activity.getResources().getFont(R.font.nunito_round));expected.setFontVariationSettings("'wght' 650");
     assertEquals("Google digits lost Nunito glyph metrics",expected.measureText("0123456789*#"),number.getPaint().measureText("0123456789*#"),0.01f);
     assertEquals(expected.getFontMetrics().ascent,number.getPaint().getFontMetrics().ascent,0.01f);
    }else assertEquals("'wght' 500",number.getFontVariationSettings());
   }
  }
 }
 @Test public void removingAppBrandDoesNotRemoveARealContactNamedDaCaller(){
  open("ivory","normal","en","DA CALLER");assertEquals("DA CALLER",((TextView)tag("call-caller")).getText().toString());
  assertEquals("Actual caller initials are not application branding","DA",((TextView)tag("call-initials")).getText().toString());
  assertEquals("050-123-4567",((TextView)tag("call-identity")).getText().toString());
  assertEquals("00:11",((TextView)tag("call-timer")).getText().toString());
 }
}
