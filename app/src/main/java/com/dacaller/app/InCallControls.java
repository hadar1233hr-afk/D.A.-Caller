package com.dacaller.app;

import android.content.SharedPreferences;

/** Central catalog for in-call actions. Safety actions cannot be hidden. */
final class InCallControls {
 static final String[] IDS={
  "mute","speaker","keypad","hold",
  "bluetooth","addcall","contacts","notes"
 };
 static final String[] NAMES={
  "השתק מיקרופון","רמקול","מקשי חיוג","המתנה",
  "אוזניית Bluetooth","הוספת שיחה","אנשי קשר","הערת שיחה"
 };
 static final String[] EXPLAIN={
  "השתקה והפעלה של המיקרופון","מעבר בין רמקול לאפרכסת",
  "שליחת ספרות במהלך השיחה","העברת שיחה להמתנה וחזרה",
  "מעבר לאוזנייה כשהיא זמינה","פתיחת חייגן לשיחה נוספת",
  "פתיחת אנשי הקשר במהלך שיחה","כתיבת הערה מקומית לשיחה"
 };
 static boolean enabled(SharedPreferences p,String id){
  boolean defaultValue=id.equals("mute")||id.equals("speaker")||
    id.equals("keypad")||id.equals("hold")||id.equals("addcall");
  return p.getBoolean("call_btn_"+id,defaultValue);
 }
 static int count(SharedPreferences p){
  int total=0;
  for(String id:IDS)if(enabled(p,id))total++;
  return total;
 }
 private InCallControls(){}
}
