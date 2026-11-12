package com.dacaller.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.provider.CallLog;

/** Small original vector strokes; no external artwork or proprietary Phone assets. */
final class RecentIcons extends Drawable {
 static final int MESSAGE=0,DETAILS=1,INCOMING=2,OUTGOING=3,BLOCKED=4;
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
 private final int kind,size;
 RecentIcons(Context context,int kind,int color){
  this.kind=kind;size=Math.round(24*context.getResources().getDisplayMetrics().density);
  paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.8f);
  paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
 }
 static Drawable direction(Context context,int callType,int color){
  return new RecentIcons(context,callType==CallLog.Calls.OUTGOING_TYPE?OUTGOING:
   callType==CallLog.Calls.REJECTED_TYPE||callType==CallLog.Calls.BLOCKED_TYPE?BLOCKED:INCOMING,color);
 }
 @Override public void draw(Canvas canvas){
  int saved=canvas.save();canvas.translate(getBounds().left,getBounds().top);
  canvas.scale(getBounds().width()/24f,getBounds().height()/24f);Path path=new Path();
  if(kind==MESSAGE){
   path.moveTo(5,4);path.lineTo(19,4);path.quadTo(21,4,21,6);path.lineTo(21,15);
   path.quadTo(21,17,19,17);path.lineTo(10,17);path.lineTo(4,21);path.lineTo(4,6);
   path.quadTo(4,4,5,4);canvas.drawPath(path,paint);
   canvas.drawLine(8,8.5f,17,8.5f,paint);canvas.drawLine(8,12.5f,14,12.5f,paint);
  }else if(kind==DETAILS){
   canvas.drawCircle(12,12,9,paint);canvas.drawLine(12,10.5f,12,16.5f,paint);
   canvas.drawPoint(12,7,paint);
  }else if(kind==BLOCKED){
   canvas.drawCircle(12,12,8,paint);canvas.drawLine(6.5f,17.5f,17.5f,6.5f,paint);
  }else if(kind==OUTGOING){
   path.moveTo(5,19);path.lineTo(18,6);path.moveTo(8,6);path.lineTo(18,6);path.lineTo(18,16);
   canvas.drawPath(path,paint);
  }else{
   path.moveTo(19,5);path.lineTo(6,18);path.moveTo(6,8);path.lineTo(6,18);path.lineTo(16,18);
   canvas.drawPath(path,paint);
  }
  canvas.restoreToCount(saved);
 }
 @Override public int getIntrinsicWidth(){return size;}
 @Override public int getIntrinsicHeight(){return size;}
 @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
 @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
 @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
