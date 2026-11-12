package com.dacaller.app;

import static org.junit.Assert.*;

import android.app.AlertDialog;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.text.PositionedGlyphs;
import android.graphics.text.TextRunShaper;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.Switch;
import android.widget.TextView;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.android.controller.ActivityController;

/** Actual API33 native shaping/drawing; these are host tests, not emulator proof. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=33,qualifiers="w320dp-h569dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public final class AppAppearanceTest {
 private Context context;
 private float previousScale;
 @Before public void setup(){
  context=new ContextThemeWrapper(RuntimeEnvironment.getApplication(),R.style.AppTheme);
  context.getSharedPreferences("da_caller",Context.MODE_PRIVATE).edit().clear().putString("language","he").commit();
  previousScale=context.getResources().getConfiguration().fontScale;
 }
 @After public void restore(){RuntimeEnvironment.setFontScale(previousScale);}
 private int dp(float n){return Math.round(n*context.getResources().getDisplayMetrics().density);}
 private void layout(View v,int width,int height){
  v.measure(View.MeasureSpec.makeMeasureSpec(dp(width),View.MeasureSpec.EXACTLY),
    View.MeasureSpec.makeMeasureSpec(dp(height),View.MeasureSpec.EXACTLY));v.layout(0,0,v.getMeasuredWidth(),v.getMeasuredHeight());
 }
 @Test public void bundledFontContainsHebrewAndLatinWithoutSystemFallbackAndHasRetainedLicense() throws Exception {
  byte[] data;try(InputStream in=context.getResources().openRawResource(R.font.rubik_round)){data=in.readAllBytes();}
  StringBuilder hash=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(data))hash.append(String.format("%02x",b&255));
  assertEquals("1b3a7437ba2af80e465e773ed60c5036d1ba6ace492d89046dbcf18fb31e4e88",hash.toString());
  ByteBuffer font=ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);int cmap=0;
  for(int i=0;i<(font.getShort(4)&65535);i++){int table=12+i*16;if(font.getInt(table)==0x636d6170)cmap=font.getInt(table+8);}
  assertTrue("Actual sfnt cmap table",cmap>0);int unicode=0;
  for(int i=0;i<(font.getShort(cmap+2)&65535);i++){
   int offset=cmap+font.getInt(cmap+8+i*8);
   if(font.getShort(offset)==4){unicode=offset;break;}
  }
  assertTrue("Actual Unicode format4 table",unicode>0);
  for(int code=0x20;code<=0x7e;code++)assertTrue("Latin U+"+Integer.toHexString(code),glyph(font,unicode,code)>0);
  for(int code=0x5d0;code<=0x5ea;code++)assertTrue("Hebrew U+"+Integer.toHexString(code),glyph(font,unicode,code)>0);
  try(InputStream in=context.getAssets().open("licenses/Rubik-OFL.txt")){
   String license=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
   assertTrue(license.contains("SIL OPEN FONT LICENSE Version 1.1"));assertTrue(license.contains("Copyright"));
  }
 }
 private int glyph(ByteBuffer font,int table,int code){
  int count=(font.getShort(table+6)&65535)/2,end=table+14,start=end+2*count+2,delta=start+2*count,range=delta+2*count;
  for(int i=0;i<count;i++)if(code<=(font.getShort(end+2*i)&65535)){
   if(code<(font.getShort(start+2*i)&65535))return 0;
   int add=font.getShort(delta+2*i),offset=font.getShort(range+2*i)&65535;
   if(offset==0)return (code+add)&65535;
   int found=font.getShort(range+2*i+offset+2*(code-(font.getShort(start+2*i)&65535)))&65535;
   return found==0?0:(found+add)&65535;
  }
  return 0;
 }
 @Test public void canvasTypographyShapesHebrewAndLatinWithTheActualBundledFontAtBothWeights() throws Exception {
  for(boolean strong:new boolean[]{false,true})for(String label:new String[]{"דנה","DA"}){
   Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);AppAppearance.font(paint,context,strong);paint.setTextSize(28);
   assertEquals(strong?650:500,paint.getTypeface().getWeight());
   PositionedGlyphs shaped=TextRunShaper.shapeTextRun(label,0,label.length(),0,label.length(),0,0,label.startsWith("ד"),paint);
   assertTrue(shaped.glyphCount()>0);assertTrue(shaped.getAdvance()>0);
   for(int i=0;i<shaped.glyphCount();i++){
    assertTrue("Canvas initials must have a real glyph",shaped.getGlyphId(i)>0);
    ByteBuffer actual=shaped.getFont(i).getBuffer().asReadOnlyBuffer();actual.rewind();
    MessageDigest digest=MessageDigest.getInstance("SHA-256");digest.update(actual);
    StringBuilder hash=new StringBuilder();for(byte value:digest.digest())hash.append(String.format("%02x",value&255));
    assertEquals("Canvas shaping must use licensed Rubik, including Hebrew, without platform fallback",
      "1b3a7437ba2af80e465e773ed60c5036d1ba6ace492d89046dbcf18fb31e4e88",hash.toString());
   }
  }
 }
 @Test public void themeAndDialogsUseRoundedFontAndBothWeightsFitLargerHebrewAndEnglish(){
  RuntimeEnvironment.setFontScale(1.5f);
  TextView inherited=new TextView(context);assertEquals(500,inherited.getTypeface().getWeight());
  for(String label:new String[]{"שמות אנשי קשר ושיחות אחרונות","Contact names and recent calls"}){
   for(boolean strong:new boolean[]{false,true}){
    TextView text=new TextView(context);text.setText(label);text.setTextSize(16);AppAppearance.font(text,strong);
    assertEquals(strong?650:500,text.getTypeface().getWeight());
    assertTrue(text.getPaint().hasGlyph(label.startsWith("ש")?"ש":"C"));
    text.measure(View.MeasureSpec.makeMeasureSpec(dp(286),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
    text.layout(0,0,text.getMeasuredWidth(),text.getMeasuredHeight());
    assertEquals(1.5f,text.getResources().getConfiguration().fontScale,.001f);
    assertTrue(text.getLayout().getHeight()<=text.getHeight()-text.getCompoundPaddingTop()-text.getCompoundPaddingBottom());
    for(int i=0;i<text.getLineCount();i++){assertEquals(0,text.getLayout().getEllipsisCount(i));assertTrue(text.getLayout().getLineWidth(i)<=dp(286));}
   }
  }
  AtomicInteger dismissals=new AtomicInteger();
  AlertDialog dialog=new AlertDialog.Builder(context).setMessage("עברית · English").setPositiveButton("OK",null)
    .setOnDismissListener(ignored->dismissals.incrementAndGet()).create();
  AppAppearance.show(dialog);TextView message=dialog.findViewById(android.R.id.message);
  assertNotNull(message);assertEquals(500,message.getTypeface().getWeight());
  assertEquals(500,dialog.getButton(AlertDialog.BUTTON_POSITIVE).getTypeface().getWeight());dialog.dismiss();
  Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(1,dismissals.get());
 }
 @Test public void dialogFontsAlsoReachLazyNativeChoiceRowsInBothPalettes(){
  for(int theme:new int[]{R.style.AppDialogLight,R.style.AppDialogDark}){
   AlertDialog dialog=new AlertDialog.Builder(new ContextThemeWrapper(context,theme))
     .setTitle("בחירה · Choose").setSingleChoiceItems(new String[]{"עברית","English"},0,null).create();
   AppAppearance.show(dialog);layout(dialog.getListView(),280,120);
   dialog.getWindow().getDecorView().getViewTreeObserver().dispatchOnGlobalLayout();
   int title=context.getResources().getIdentifier("alertTitle","id","android");
   assertEquals(650,((TextView)dialog.findViewById(title)).getTypeface().getWeight());
   assertEquals(2,dialog.getListView().getChildCount());
   for(int i=0;i<2;i++)assertEquals(500,((TextView)dialog.getListView().getChildAt(i)).getTypeface().getWeight());
   dialog.dismiss();
  }
 }
 @Test public void brightGoldKeepsOrdinaryTextContrastOnLightDarkAndAmoledSurfaces(){
  for(int background:new int[]{Color.WHITE,0xFFF7F7F5,0xFFF5F5F4})assertTrue(contrast(AppAppearance.gold(false),background)>=4.5);
  for(int background:new int[]{0xFF141414,0xFF232323,Color.BLACK})assertTrue(contrast(AppAppearance.gold(true),background)>=4.5);
 }
 private double luminance(int color){double sum=0;int[] components={Color.red(color),Color.green(color),Color.blue(color)};double[] factors={.2126,.7152,.0722};
  for(int i=0;i<3;i++){double channel=components[i]/255.;sum+=factors[i]*(channel<=.04045?channel/12.92:Math.pow((channel+.055)/1.055,2.4));}return sum;}
 private double contrast(int a,int b){double x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
 private Rect draw(Switch control){
  control.jumpDrawablesToCurrentState();Bitmap bitmap=Bitmap.createBitmap(control.getWidth(),control.getHeight(),Bitmap.Config.ARGB_8888);
  control.draw(new Canvas(bitmap));bitmap.recycle();return new Rect(control.getThumbDrawable().getBounds());
 }
 private void event(Switch control,long down,long time,int action,float x,float y){
  MotionEvent event=MotionEvent.obtain(down,time,action,x,y,0);control.dispatchTouchEvent(event);event.recycle();
 }
 @Test public void nativeWineSwitchKeeps48dpTargetSemanticsRtlThumbContainmentAndActualDrag(){
  try(ActivityController<Activity> controller=Robolectric.buildActivity(Activity.class).setup()){
   Activity activity=controller.get();activity.setTheme(R.style.AppTheme);
  for(boolean dark:new boolean[]{false,true})for(boolean rtl:new boolean[]{false,true}){
   FrameLayout parent=new FrameLayout(activity);Switch control=new Switch(activity);AppAppearance.styleSwitch(control,dark);
   parent.setLayoutDirection(rtl?View.LAYOUT_DIRECTION_RTL:View.LAYOUT_DIRECTION_LTR);
   control.setLayoutDirection(rtl?View.LAYOUT_DIRECTION_RTL:View.LAYOUT_DIRECTION_LTR);control.setContentDescription("Call reminder");
   parent.addView(control,new FrameLayout.LayoutParams(-2,-2));activity.setContentView(parent);controller.visible();layout(parent,320,80);
   assertTrue(control.getWidth()>=dp(48));assertTrue(control.getHeight()>=dp(48));
   AccessibilityNodeInfo node=control.createAccessibilityNodeInfo();assertEquals("android.widget.Switch",node.getClassName());
   assertTrue(node.isCheckable());assertTrue(node.isClickable());assertFalse(node.isChecked());node.recycle();
   AtomicInteger changes=new AtomicInteger();control.setOnCheckedChangeListener((button,value)->changes.incrementAndGet());
   Rect off=draw(control);Rect track=new Rect(control.getTrackDrawable().getBounds());
   assertTrue("Off thumb "+off+" stays inside track "+track,track.contains(off));
   long down=android.os.SystemClock.uptimeMillis();float y=off.exactCenterY(),end=rtl?track.left+dp(2):track.right-dp(2);
   event(control,down,down,MotionEvent.ACTION_DOWN,off.exactCenterX(),y);
   event(control,down,down+60,MotionEvent.ACTION_MOVE,(off.exactCenterX()+end)/2,y);
   event(control,down,down+120,MotionEvent.ACTION_MOVE,end,y);
   event(control,down,down+180,MotionEvent.ACTION_UP,end,y);
   Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
   assertTrue("Native drag must select in "+(rtl?"RTL":"LTR"),control.isChecked());assertEquals(1,changes.get());
   Rect on=draw(control);assertTrue("On thumb "+on+" stays inside track "+track,track.contains(on));
   assertTrue(rtl?on.centerX()<off.centerX():on.centerX()>off.centerX());
   node=control.createAccessibilityNodeInfo();assertTrue(node.isChecked());assertEquals("Call reminder",node.getContentDescription());node.recycle();
   control.setEnabled(false);down+=1000;
   event(control,down,down,MotionEvent.ACTION_DOWN,on.exactCenterX(),y);event(control,down,down+30,MotionEvent.ACTION_UP,on.exactCenterX(),y);
   assertTrue(control.isChecked());assertEquals(1,changes.get());
   node=control.createAccessibilityNodeInfo();assertFalse(node.isEnabled());node.recycle();
  }
  }
 }
 @Test public void sharedChevronsHaveRoundedStrokesAndBackArrowMirrorsSelectedLanguage(){
  for(boolean expanded:new boolean[]{false,true}){Drawable arrow=AppAppearance.chevron(context,expanded,Color.WHITE);assertEquals(dp(24),arrow.getIntrinsicWidth());}
  context.getSharedPreferences("da_caller",Context.MODE_PRIVATE).edit().putString("language","en").commit();Bitmap ltr=arrow(AppAppearance.backArrow(context,Color.WHITE));
  context.getSharedPreferences("da_caller",Context.MODE_PRIVATE).edit().putString("language","he").commit();Bitmap rtl=arrow(AppAppearance.backArrow(context,Color.WHITE));
  int pixels=0;for(int y=0;y<24;y++)for(int x=0;x<24;x++){
   int a=Color.alpha(ltr.getPixel(x,y)),b=Color.alpha(rtl.getPixel(23-x,y));
   assertTrue("Native stroke coverage mirrors within one alpha rounding unit",Math.abs(a-b)<=1);if(a>0)pixels++;
  }
  assertTrue(pixels>30);ltr.recycle();rtl.recycle();
 }
 private Bitmap arrow(Drawable arrow){Bitmap image=Bitmap.createBitmap(24,24,Bitmap.Config.ARGB_8888);arrow.setBounds(0,0,24,24);arrow.draw(new Canvas(image));return image;}
}
