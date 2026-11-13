package com.dacaller.app;

import android.content.Context;
import android.app.Dialog;
import android.app.AlertDialog;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.widget.Switch;
import android.widget.TextView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

/** Shared offline typography, wine controls and navigation strokes. */
final class AppAppearance {
 private AppAppearance(){}
 static void font(TextView v,boolean strong){
  Typeface family=v.getResources().getFont(R.font.app_round);
  v.setTypeface(Build.VERSION.SDK_INT>=28?Typeface.create(family,strong?650:500,false):
    Typeface.create(family,strong?Typeface.BOLD:Typeface.NORMAL));
  v.setFontVariationSettings(strong?"'wght' 650":"'wght' 500");
 }
 /** Bitmap initials use the same licensed family, including Hebrew, on API26+. */
 static void font(Paint paint,Context context,boolean strong){
  Typeface family=context.getResources().getFont(R.font.app_round);
  paint.setTypeface(Build.VERSION.SDK_INT>=28?Typeface.create(family,strong?650:500,false):
    Typeface.create(family,strong?Typeface.BOLD:Typeface.NORMAL));
  paint.setFontVariationSettings(strong?"'wght' 650":"'wght' 500");
 }
 /** Android's native alert layouts explicitly override the theme's font family.
  * Apply after show; layout observation also covers lazily inflated choice rows. */
 static void dialog(Dialog dialog){
  if(dialog.getWindow()==null)return;
  View root=dialog.getWindow().getDecorView();
  if(Boolean.TRUE.equals(root.getTag(R.id.appearance_dialog_fonts)))return;
  root.setTag(R.id.appearance_dialog_fonts,Boolean.TRUE);
  int title=root.getResources().getIdentifier("alertTitle","id","android");
  dialogFonts(root,title);
  ViewTreeObserver.OnGlobalLayoutListener listener=()->dialogFonts(root,title);
  root.getViewTreeObserver().addOnGlobalLayoutListener(listener);
  root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
   @Override public void onViewAttachedToWindow(View view){}
   @Override public void onViewDetachedFromWindow(View view){
    if(view.getViewTreeObserver().isAlive())view.getViewTreeObserver().removeOnGlobalLayoutListener(listener);
    view.setTag(R.id.appearance_dialog_fonts,null);
    view.removeOnAttachStateChangeListener(this);
   }
  });
 }
 static AlertDialog show(AlertDialog.Builder builder){AlertDialog shown=builder.show();dialog(shown);return shown;}
 static void show(Dialog shown){shown.show();dialog(shown);}
 private static void dialogFonts(View view,int title){
  if(view instanceof TextView){
   TextView text=(TextView)view;Typeface old=text.getTypeface();
   boolean strong=view.getId()==title&&title!=0||old!=null&&(old.isBold()||Build.VERSION.SDK_INT>=28&&old.getWeight()>=600);
   // A global layout can follow our first font change; skip views whose applied
   // Typeface is still current, so observation cannot cause a relayout loop.
   if(old==null||text.getTag(R.id.appearance_dialog_font)!=old){font(text,strong);text.setTag(R.id.appearance_dialog_font,text.getTypeface());}
  }
  if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;
   for(int i=0;i<group.getChildCount();i++)dialogFonts(group.getChildAt(i),title);
  }
 }
 /** The light shade meets ordinary-text contrast on our light cards. */
 static int gold(boolean dark){return dark?0xFFF5CB63:0xFF85620A;}
 private static int dp(Context c,float value){return Math.round(c.getResources().getDisplayMetrics().density*value);}
 private static GradientDrawable capsule(Context c,int color,int stroke,int width,int height){
  GradientDrawable shape=new GradientDrawable();shape.setColor(color);shape.setCornerRadius(dp(c,height/2f));
  shape.setSize(dp(c,width),dp(c,height));
  if(stroke!=0)shape.setStroke(dp(c,1),stroke);
  return shape;
 }
 private static Drawable thumb(Context c,int color){
  GradientDrawable circle=capsule(c,color,0,24,24);
  int side=dp(c,2),vertical=dp(c,4);
  // InsetDrawable exposes its inset as an optical inset too. Native Switch
  // subtracts those optical insets from the track, shrinking its capsule.
  // Keep drawing padding while inheriting Drawable's zero optical insets.
  return new Drawable(){
   @Override public void draw(Canvas canvas){
    Rect bounds=getBounds();circle.setBounds(bounds.left+side,bounds.top+vertical,bounds.right-side,bounds.bottom-vertical);
    circle.draw(canvas);
   }
   @Override public int getIntrinsicWidth(){return dp(c,28);}
   @Override public int getIntrinsicHeight(){return dp(c,32);}
   @Override public boolean getPadding(Rect padding){padding.set(side,vertical,side,vertical);return true;}
   @Override public void setAlpha(int alpha){circle.setAlpha(alpha);invalidateSelf();}
   @Override public void setColorFilter(ColorFilter filter){circle.setColorFilter(filter);invalidateSelf();}
   @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
  };
 }
 private static Drawable track(Context c,int color,int stroke){
  // Native Switch uses this padding to keep the entire moving thumb inside its
  // capsule, including on Android26 where GradientDrawable has no setPadding.
  GradientDrawable shape=new GradientDrawable(){
   @Override public boolean getPadding(Rect padding){padding.set(dp(c,4),0,dp(c,4),0);return true;}
  };
  shape.setColor(color);shape.setCornerRadius(dp(c,16));shape.setSize(dp(c,56),dp(c,32));
  if(stroke!=0)shape.setStroke(dp(c,1),stroke);return shape;
 }
 /** Keeps Android's real Switch drag, checked state, RTL and accessibility behavior. */
 static void styleSwitch(Switch s,boolean dark){
  Context c=s.getContext();
  StateListDrawable track=new StateListDrawable(),thumb=new StateListDrawable();
  int enabled=android.R.attr.state_enabled,checked=android.R.attr.state_checked;
  track.addState(new int[]{-enabled,checked},track(c,dark?0xFF654051:0xFFCCABB9,0));
  track.addState(new int[]{-enabled},track(c,dark?0xFF342C30:0xFFE7E0E3,0));
  track.addState(new int[]{checked},track(c,dark?0xFFC76589:0xFF8F2D52,0));
  track.addState(new int[]{},track(c,dark?0xFF3E2E37:0xFFE8DFE3,dark?0xFF9D788A:0xFF806571));
  thumb.addState(new int[]{-enabled},thumb(c,dark?0xFF8E7882:0xFFB19AA4));
  thumb.addState(new int[]{checked},thumb(c,0xFFFFF7FA));
  thumb.addState(new int[]{},thumb(c,dark?0xFFD4B4C2:0xFF6E4B5A));
  s.setThumbTintList(null);s.setTrackTintList(null);s.setThumbDrawable(thumb);s.setTrackDrawable(track);
  s.setShowText(false);s.setSplitTrack(false);s.setThumbTextPadding(0);s.setSwitchPadding(dp(c,10));
  s.setSwitchMinWidth(dp(c,56));s.setMinimumWidth(dp(c,64));s.setMinimumHeight(dp(c,48));
  s.setPadding(dp(c,4),0,dp(c,4),0);font(s,false);
 }
 static Drawable chevron(Context c,boolean expanded,int color){return new NavigationStroke(c,expanded?1:0,color);}
 static Drawable backArrow(Context c,int color){return new NavigationStroke(c,I18n.isEnglish(c)?2:3,color);}
 /** Forward disclosure shares the same geometry and follows the app's selected language. */
 static Drawable forwardArrow(Context c,int color){return new NavigationStroke(c,I18n.isEnglish(c)?3:2,color);}
 private static final class NavigationStroke extends Drawable {
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
  private final int direction,size;
  NavigationStroke(Context c,int direction,int color){
   this.direction=direction;size=dp(c,24);paint.setColor(color);paint.setStyle(Paint.Style.STROKE);
   paint.setStrokeWidth(2.25f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
  }
  @Override public void draw(Canvas canvas){
   int save=canvas.save();canvas.translate(getBounds().left,getBounds().top);
   canvas.scale(getBounds().width()/24f,getBounds().height()/24f);Path path=new Path();
   if(direction==0){path.moveTo(6,9);path.lineTo(12,15);path.lineTo(18,9);}
   else if(direction==1){path.moveTo(6,15);path.lineTo(12,9);path.lineTo(18,15);}
   else if(direction==2){path.moveTo(14.5f,6);path.lineTo(8.5f,12);path.lineTo(14.5f,18);}
   else{path.moveTo(9.5f,6);path.lineTo(15.5f,12);path.lineTo(9.5f,18);}
   canvas.drawPath(path,paint);canvas.restoreToCount(save);
  }
  @Override public int getIntrinsicWidth(){return size;}
  @Override public int getIntrinsicHeight(){return size;}
  @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
  @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
  @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 }
}
