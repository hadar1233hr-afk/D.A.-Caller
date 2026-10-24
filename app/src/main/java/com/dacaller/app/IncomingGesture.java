package com.dacaller.app;

/** Physical directions stay the same in Hebrew and English; labels state them explicitly. */
final class IncomingGesture {
 enum Action { NONE, ANSWER, DECLINE }
 static String mode(String value){
  return "right".equals(value)||"left".equals(value)||"vertical".equals(value)?value:"ios";
 }
 static Action resolve(String selected,float dx,float dy,float threshold){
  String mode=mode(selected);
  if("vertical".equals(mode)){
   if(Math.abs(dy)<threshold||Math.abs(dy)<=Math.abs(dx)*1.2f)return Action.NONE;
   return dy<0?Action.ANSWER:Action.DECLINE;
  }
  if(Math.abs(dx)<threshold||Math.abs(dx)<=Math.abs(dy)*1.2f)return Action.NONE;
  boolean answer="left".equals(mode)?dx<0:dx>0;
  return answer?Action.ANSWER:"ios".equals(mode)?Action.NONE:Action.DECLINE;
 }
 private IncomingGesture(){}
}
