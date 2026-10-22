package com.dacaller.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/** Fast visual press indication and short tactile feedback without dependencies. */
final class TouchFeedback {
 private TouchFeedback(){}
 static void bind(View target,SharedPreferences preferences){
  if(target==null)return;
  GradientDrawable mask=new GradientDrawable();
  mask.setColor(Color.WHITE);mask.setCornerRadius(28f*target.getResources().getDisplayMetrics().density);
  target.setForeground(new RippleDrawable(
   ColorStateList.valueOf(0x32CDB47F),null,mask));
  target.setClickable(true);
  target.setOnTouchListener((view,event)->{
   switch(event.getActionMasked()){
    case MotionEvent.ACTION_DOWN:
     view.animate().cancel();
     view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(58).start();
     break;
    case MotionEvent.ACTION_UP:
    case MotionEvent.ACTION_CANCEL:
     view.animate().scaleX(1f).scaleY(1f).setDuration(90).start();
     break;
   }
   return false;
  });
 }
 static void pulse(Context context,SharedPreferences preferences,View target){
  if(preferences==null||!preferences.getBoolean("haptics",true))return;
  try{
   if(target!=null){
    target.setHapticFeedbackEnabled(true);
    if(target.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY))return;
   }
   Vibrator vibrator=(Vibrator)context.getSystemService(Context.VIBRATOR_SERVICE);
   if(vibrator!=null&&vibrator.hasVibrator()){
    if(Build.VERSION.SDK_INT>=29){
     vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
    }else vibrator.vibrate(VibrationEffect.createOneShot(14,95));
   }
  }catch(Exception ignored){}
 }
}
