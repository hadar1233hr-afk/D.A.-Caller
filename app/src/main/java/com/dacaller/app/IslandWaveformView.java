package com.dacaller.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Decorative call activity indicator. It never reads microphone or audio data. */
final class IslandWaveformView extends View {
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
 private final float density;
 private ValueAnimator animator;
 private boolean requestedAnimation,paused;
 private float phase;
 private long lastFrameMillis;
 IslandWaveformView(Context context){
  super(context);density=getResources().getDisplayMetrics().density;
  paint.setColor(0xFF49DA8B);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
  setFocusable(false);setClickable(false);
 }
 void configure(int color,boolean animate,boolean paused){
  paint.setColor(color);requestedAnimation=animate;this.paused=paused;
  if(paused||!animate)phase=0;updateAnimation();invalidate();
 }
 private boolean shouldAnimate(){
  return requestedAnimation&&!paused&&isAttachedToWindow()&&getWindowVisibility()==VISIBLE&&isShown()
    &&ValueAnimator.areAnimatorsEnabled();
 }
 private void updateAnimation(){
  if(!shouldAnimate()){stop();return;}
  if(animator!=null)return;
  animator=ValueAnimator.ofFloat(0f,(float)(Math.PI*2));animator.setDuration(1400);
  animator.setInterpolator(new LinearInterpolator());animator.setRepeatCount(ValueAnimator.INFINITE);
  animator.addUpdateListener(value->{
   phase=(Float)value.getAnimatedValue();long now=android.os.SystemClock.uptimeMillis();
   if(now-lastFrameMillis>=33){lastFrameMillis=now;postInvalidateOnAnimation();}
  });animator.start();
 }
 private void stop(){if(animator!=null){animator.cancel();animator.removeAllUpdateListeners();animator=null;}}
 @Override protected void onAttachedToWindow(){super.onAttachedToWindow();updateAnimation();}
 @Override protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
 @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);updateAnimation();}
 @Override protected void onVisibilityChanged(View changedView,int visibility){super.onVisibilityChanged(changedView,visibility);updateAnimation();}
 @Override protected void onMeasure(int widthMeasureSpec,int heightMeasureSpec){
  setMeasuredDimension(resolveSize(Math.round(22*density),widthMeasureSpec),resolveSize(Math.round(24*density),heightMeasureSpec));
 }
 @Override protected void onDraw(Canvas canvas){
  super.onDraw(canvas);
  float width=getWidth(),height=getHeight(),bar=Math.min(2.3f*density,width/9f),gap=bar*.72f;
  float total=bar*5+gap*4,left=(width-total)/2f,center=height/2f;
  for(int i=0;i<5;i++){
   float envelope=1f-Math.abs(i-2)*.20f;
   float rhythm=.48f+.26f*(float)Math.sin(phase+i*.86f)+.11f*(float)Math.sin(phase*2-i*.47f);
   float length=paused?bar:Math.max(bar,Math.min(height*.82f,height*envelope*rhythm));
   float x=left+i*(bar+gap);canvas.drawRoundRect(x,center-length/2,x+bar,center+length/2,bar/2,bar/2,paint);
  }
 }
}
