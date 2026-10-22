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
  int level=Math.max(1,Math.min(3,preferences.getInt("haptic_strength",2)));
  try{
   Vibrator vibrator=(Vibrator)context.getSystemService(Context.VIBRATOR_SERVICE);
   if(vibrator!=null&&vibrator.hasVibrator()){
    int amplitude=level==1?85:level==3?210:145;
    long millis=level==1?12:level==3?26:19;
    vibrator.vibrate(VibrationEffect.createOneShot(millis,amplitude));
    return;
   }
  }catch(Exception ignored){}
  try{
   if(target!=null){
    target.setHapticFeedbackEnabled(true);
    target.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
   }
  }catch(Exception ignored){}
 }
}
