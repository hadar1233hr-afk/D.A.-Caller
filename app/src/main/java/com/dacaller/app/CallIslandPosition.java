package com.dacaller.app;

import android.content.SharedPreferences;
import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import java.util.List;

/** Normalized user position, constrained again for each actual window and orientation. */
final class CallIslandPosition {
 static final int SCALE=10_000;
 static final String MODE="island_position_mode",SET="island_position_set",X="island_position_x",Y="island_position_y";
 static boolean topArea(SharedPreferences prefs){return "status_area".equals(prefs.getString(MODE,"below"));}
 static Point resolve(SharedPreferences prefs,Rect area,int width,int height){
  int dx=Math.max(0,area.width()-width),dy=Math.max(0,area.height()-height);
  if(!prefs.getBoolean(SET,false))return new Point(area.left+dx/2,area.top);
  return new Point(area.left+Math.round(dx*unit(prefs.getInt(X,SCALE/2))/(float)SCALE),
    area.top+Math.round(dy*unit(prefs.getInt(Y,0))/(float)SCALE));
 }
 static Point clamp(Rect area,int width,int height,float x,float y,int statusBottom,int iconSide,List<Rect> cutouts,int gap){
  int left=area.left,right=Math.max(left,area.right-width),top=area.top,bottom=Math.max(top,area.bottom-height);
  int px=Math.max(left,Math.min(right,Math.round(x))),py=Math.max(top,Math.min(bottom,Math.round(y)));
  // Status icons belong to SystemUI. Only the central lane may straddle their area.
  if(py<statusBottom){
   int centralLeft=Math.max(left,area.left+iconSide),centralRight=Math.min(right,area.right-iconSide-width);
   if(centralRight<centralLeft)py=Math.min(bottom,Math.max(py,statusBottom+gap));
   else px=Math.max(centralLeft,Math.min(centralRight,px));
  }
  if(cutouts!=null)for(Rect cutout:cutouts){
   if(Rect.intersects(new Rect(px,py,px+width,py+height),cutout))py=Math.min(bottom,Math.max(py,cutout.bottom+gap));
  }
  return new Point(px,py);
 }
 static void save(Context context,SharedPreferences prefs,Rect area,int width,int height,int x,int y){
  if(!SettingsBackup.allowPreferenceChange(context))return;
  int dx=Math.max(0,area.width()-width),dy=Math.max(0,area.height()-height);
  prefs.edit().putBoolean(SET,true).putInt(X,dx==0?SCALE/2:unit(Math.round((x-area.left)*(float)SCALE/dx)))
    .putInt(Y,dy==0?0:unit(Math.round((y-area.top)*(float)SCALE/dy))).apply();
 }
 static boolean reset(Context context,SharedPreferences prefs){
  if(!SettingsBackup.allowPreferenceChange(context))return false;
  prefs.edit().remove(SET).remove(X).remove(Y).apply();return true;
 }
 private static int unit(int value){return Math.max(0,Math.min(SCALE,value));}
 private CallIslandPosition(){}
}
