package com.dacaller.app;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** Read-only daily missed-call classification; no provider or Android clock side effects. */
final class CallbackReminderModel {
 static final int INCOMING=1,OUTGOING=2,MISSED=3,ALLOWED=1,RESTRICTED=2;
 static final class Day {
  final long start,end;final String key,zone;
  Day(long start,long end,String key,String zone){this.start=start;this.end=end;this.key=key;this.zone=zone;}
 }
 static final class Row {
  final long id,date,duration;final String number;final int type,presentation;
  Row(long id,String number,long date,long duration,int type,int presentation){
   this.id=id;this.number=number;this.date=date;this.duration=Math.max(0,duration);this.type=type;this.presentation=presentation;
  }
 }
 static final class Entry {
  final String key,number,name;final long latestMissedAt,latestMissedId,contactId;
  final int missedCount,missedCountToday,callbackAttempts;final boolean privateNumber;
  Entry(String key,String number,String name,long at,long id,long contactId,int missed,int total,int attempts,boolean hidden){
   this.key=key;this.number=number;this.name=name;latestMissedAt=at;latestMissedId=id;this.contactId=contactId;
   missedCount=missed;missedCountToday=total;callbackAttempts=attempts;privateNumber=hidden;
  }
  Entry identified(String name,long id){return new Entry(key,number,name,latestMissedAt,latestMissedId,id,missedCount,missedCountToday,callbackAttempts,privateNumber);}
 }
 static Day day(long now,TimeZone zone){
  Calendar value=Calendar.getInstance(zone);value.setTimeInMillis(now);return day(value);
 }
 static Day selectedDay(String key,long now,TimeZone zone){
  if(key!=null&&key.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))try{
   Calendar value=Calendar.getInstance(zone);value.clear();value.setLenient(false);
   value.set(Integer.parseInt(key.substring(0,4)),Integer.parseInt(key.substring(5,7))-1,Integer.parseInt(key.substring(8,10)));
   long at=value.getTimeInMillis();if(at<=now)return day(value);
  }catch(IllegalArgumentException invalid){/* An invalid notification date never broadens a query. */}
  return day(now,zone);
 }
 private static Day day(Calendar value){
  value=(Calendar)value.clone();value.set(Calendar.HOUR_OF_DAY,0);value.set(Calendar.MINUTE,0);value.set(Calendar.SECOND,0);value.set(Calendar.MILLISECOND,0);
  String key=String.format(Locale.ROOT,"%04d-%02d-%02d",value.get(Calendar.YEAR),value.get(Calendar.MONTH)+1,value.get(Calendar.DAY_OF_MONTH));
  long start=value.getTimeInMillis();value.add(Calendar.DAY_OF_MONTH,1);
  return new Day(start,value.getTimeInMillis(),key,value.getTimeZone().getID());
 }
 static long nextAlarm(long now,int minuteOfDay,TimeZone zone){
  int bounded=Math.max(0,Math.min(1439,minuteOfDay));Calendar value=Calendar.getInstance(zone);value.setTimeInMillis(now);
  value.set(Calendar.HOUR_OF_DAY,bounded/60);value.set(Calendar.MINUTE,bounded%60);value.set(Calendar.SECOND,0);value.set(Calendar.MILLISECOND,0);
  if(value.getTimeInMillis()<=now)value.add(Calendar.DAY_OF_MONTH,1);
  return value.getTimeInMillis();
 }
 static String safeNumber(String number){
  if(number==null)return "";String value=number.trim();
  // Android's historical unknown/private/payphone sentinels must never become
  // the callable numbers1,2 or3 when separators are stripped.
  if(value.matches("-[123]"))return "";
  String dial=value.replaceAll("[() .\\s-]","");
  return dial.matches("[+]?[0-9*#][0-9*#,;]*")?value:"";
 }
 static String key(String number){
  String safe=safeNumber(number);if(safe.isEmpty())return "";
  String dial=safe.replaceAll("[^+0-9*#,;]","");
  if(dial.indexOf('*')>=0||dial.indexOf('#')>=0||dial.indexOf(',')>=0||dial.indexOf(';')>=0)return dial;
  String digits=safe.replaceAll("[^0-9]","");
  if(digits.startsWith("00972")&&digits.length()>=13)return "0"+digits.substring(5);
  if(digits.startsWith("972")&&digits.length()>=11)return "0"+digits.substring(3);
  return digits;
 }
 private static int compare(Row left,Row right){int time=Long.compare(left.date,right.date);return time!=0?time:Long.compare(left.id,right.id);}
 static List<Entry> reduce(List<Row> rows,Day day,long observedAt){
  long until=Math.min(observedAt,day.end-1);Map<String,List<Row>> missed=new LinkedHashMap<>();Map<String,Row> latest=new HashMap<>();
  for(Row row:rows){
   if(row.type!=MISSED||row.date<day.start||row.date>until)continue;
   String number=row.presentation==ALLOWED?safeNumber(row.number):"";
   String key=number.isEmpty()?(row.presentation==RESTRICTED?"hidden:restricted":"hidden:unknown"):key(number);
   missed.computeIfAbsent(key,ignored->new ArrayList<>()).add(row);
   Row previous=latest.get(key);if(previous==null||compare(row,previous)>0)latest.put(key,row);
  }
  Map<String,Row> conversations=new HashMap<>();Map<String,Integer> attemptsByNumber=new HashMap<>();
  for(Row row:rows){
   if(row.date<day.start||row.date>until||row.presentation!=ALLOWED)continue;
   String rowKey=key(row.number);Row last=latest.get(rowKey);
   if(last==null||rowKey.startsWith("hidden:"))continue;
   if(row.type==OUTGOING&&compare(row,last)>0)attemptsByNumber.put(rowKey,attemptsByNumber.getOrDefault(rowKey,0)+1);
   if((row.type==INCOMING||row.type==OUTGOING)&&row.duration>=2){
    Row previous=conversations.get(rowKey);if(previous==null||compare(row,previous)>0)conversations.put(rowKey,row);
   }
  }
  ArrayList<Entry> result=new ArrayList<>();
  for(Map.Entry<String,List<Row>> group:missed.entrySet()){
   String key=group.getKey();Row last=latest.get(key),conversation=conversations.get(key);int attempts=attemptsByNumber.getOrDefault(key,0);
   if(conversation!=null&&compare(conversation,last)>0)continue;
   int remaining=0;for(Row row:group.getValue())if(conversation==null||compare(row,conversation)>0)remaining++;
   String number=last.presentation==ALLOWED?safeNumber(last.number):"";
   result.add(new Entry(key,number,"",last.date,last.id,-1,remaining,group.getValue().size(),attempts,last.presentation==RESTRICTED));
  }
  result.sort(Comparator.comparingLong((Entry value)->value.latestMissedAt).reversed().thenComparing(value->value.key));
  return Collections.unmodifiableList(result);
 }
 private CallbackReminderModel(){}
}
