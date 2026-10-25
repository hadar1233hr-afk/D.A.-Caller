package com.dacaller.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

/** One contact-avatar shape preference for photos, initials and offline widgets. */
final class AvatarShapes {
 static final String PREF="avatar_shape";
 static final String[] IDS={"circle","rounded_square","square","hexagon"};
 private static final String[] HEBREW={"עיגול","ריבוע מעוגל","ריבוע","משושה"};
 private static final String[] ENGLISH={"Circle","Rounded square","Square","Hexagon"};
 static String selected(Context context){
  return normalize(context.getSharedPreferences("da_caller",Context.MODE_PRIVATE).getString(PREF,"circle"));
 }
 static String normalize(String id){for(String valid:IDS)if(valid.equals(id))return id;return "circle";}
 static String label(Context context,String id){
  String selected=normalize(id);for(int i=0;i<IDS.length;i++)if(IDS[i].equals(selected))return I18n.isEnglish(context)?ENGLISH[i]:HEBREW[i];
  return I18n.isEnglish(context)?ENGLISH[0]:HEBREW[0];
 }
 /** Use this container for avatars: arbitrary path clipping also works on API 26–32. */
 static FrameLayout frame(Context context){Frame result=new Frame(context);apply(result);return result;}
 static void apply(View view){apply(view,selected(view.getContext()));}
 static void apply(View view,String requested){
  final String id=normalize(requested);
  // The container's path controls geometry; old per-screen radii must not leave
  // circular holes in a newly selected square or hexagonal avatar.
  if(view.getBackground() instanceof GradientDrawable){
   GradientDrawable background=(GradientDrawable)view.getBackground().mutate();background.setCornerRadius(0);
  }
  view.setOutlineProvider(new ViewOutlineProvider(){
   @Override public void getOutline(View v,Outline outline){
    float side=Math.min(v.getWidth(),v.getHeight());float x=(v.getWidth()-side)/2f,y=(v.getHeight()-side)/2f;
    if("circle".equals(id))outline.setOval(Math.round(x),Math.round(y),Math.round(x+side),Math.round(y+side));
    else if("rounded_square".equals(id))outline.setRoundRect(Math.round(x),Math.round(y),Math.round(x+side),Math.round(y+side),side*.22f);
    else if("square".equals(id))outline.setRect(Math.round(x),Math.round(y),Math.round(x+side),Math.round(y+side));
    else if(side>0)outline.setConvexPath(path(id,v.getWidth(),v.getHeight()));
   }
  });
  if(view instanceof Frame){((Frame)view).setShape(id);view.setClipToOutline(false);}
  else view.setClipToOutline(true);
  view.invalidateOutline();view.invalidate();
 }
 static Path path(String requested,float width,float height){
  String id=normalize(requested);float side=Math.min(width,height),left=(width-side)/2f,top=(height-side)/2f;
  RectF bounds=new RectF(left,top,left+side,top+side);Path result=new Path();
  if("circle".equals(id))result.addOval(bounds,Path.Direction.CW);
  else if("rounded_square".equals(id))result.addRoundRect(bounds,side*.22f,side*.22f,Path.Direction.CW);
  else if("square".equals(id))result.addRect(bounds,Path.Direction.CW);
  else{
   result.moveTo(left+side*.25f,top);result.lineTo(left+side*.75f,top);
   result.lineTo(left+side,top+side*.5f);result.lineTo(left+side*.75f,top+side);
   result.lineTo(left+side*.25f,top+side);result.lineTo(left,top+side*.5f);result.close();
  }
  return result;
 }
 /** Settings uses the exact geometry shared by photos and widget bitmaps. */
 static Bitmap preview(Context context,String id,int size){
  Bitmap image=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(image);
  Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(0xFF123D30);canvas.drawPath(path(id,size,size),paint);
  paint.setColor(0xFFE7C779);paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
  paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(size*.34f);Paint.FontMetrics metrics=paint.getFontMetrics();
  canvas.drawText("DA",size/2f,size/2f-(metrics.ascent+metrics.descent)/2f,paint);return image;
 }
 private static final class Frame extends FrameLayout {
  private String shape="circle";private Path clipping=new Path();
  Frame(Context context){super(context);}
  void setShape(String value){shape=value;clipping=path(shape,getWidth(),getHeight());invalidate();}
  @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);clipping=path(shape,w,h);}
  @Override public void draw(Canvas canvas){
   int saved=canvas.save();canvas.clipPath(clipping);super.draw(canvas);canvas.restoreToCount(saved);
  }
 }
 private AvatarShapes(){}
}
