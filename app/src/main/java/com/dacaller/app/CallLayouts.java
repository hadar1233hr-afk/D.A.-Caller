package com.dacaller.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Five genuinely different in-call control arrangements, independent of palette. */
final class CallLayouts {
 static final String[][] ORDERS={
  {"mute","speaker","keypad","hold","bluetooth","addcall","contacts","notes"},
  {"keypad","mute","speaker","hold","bluetooth","addcall","contacts","notes"},
  {"speaker","bluetooth","mute","keypad","hold","notes","contacts","addcall"},
  {"notes","contacts","keypad","speaker","mute","hold","addcall","bluetooth"},
  {"hold","mute","keypad","speaker","notes","contacts","bluetooth","addcall"}
 };
 static String[] primary(int style){
  switch(style){
   case 0:return new String[]{"mute","keypad","audio","more"};
   case 1:return new String[]{"more","audio","mute","keypad"};
   case 2:return new String[]{"audio","mute","keypad","more"};
   case 3:return new String[]{"keypad","audio","more","mute"};
   default:return new String[]{"mute","audio","keypad","more"};
  }
 }
 static List<String> ordered(int style,List<String> enabled){
  int which=Math.max(0,Math.min(ORDERS.length-1,style));
  List<String> result=new ArrayList<>();
  for(String id:ORDERS[which])if(enabled.contains(id))result.add(id);
  for(String id:enabled)if(!result.contains(id))result.add(id);
  return result;
 }
 static int columns(int style,boolean narrow,int size){
  if(size<=1)return 1;
  switch(style){
   case 0:return 2; // Command deck
   case 1:return narrow?2:Math.min(4,size); // Expressive compact row
   case 2:return 1; // Large accessibility action stack
   case 3:return narrow?2:3; // Frost dashboard
   case 4:return 2; // Ivory symmetrical
   default:return 2;
  }
 }
 static int buttonHeight(int style,boolean compact,int configured){
  int base=Math.max(52,Math.min(76,configured));
  switch(style){
   case 1:return compact?60:72;
   case 2:return compact?62:74;
   case 3:return compact?57:68;
   case 4:return compact?59:70;
   default:return base;
  }
 }
 static int avatarSize(int style,boolean compact){
  switch(style){
   case 1:return compact?88:132;
   case 2:return compact?58:78;
   case 3:return compact?62:93;
   case 4:return compact?54:68;
   default:return compact?75:100;
  }
 }
 static boolean endAboveControls(int style){return style==2;}
 private CallLayouts(){}
}
