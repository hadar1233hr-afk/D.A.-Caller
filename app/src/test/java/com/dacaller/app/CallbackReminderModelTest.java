package com.dacaller.app;

import static org.junit.Assert.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import org.junit.Test;

/** Real duration/order/day rules, independently of providers, notifications or the UI. */
public final class CallbackReminderModelTest {
 private static final TimeZone UTC=TimeZone.getTimeZone("UTC");
 private static final long NOW=Instant.parse("2026-11-02T21:00:00Z").toEpochMilli();
 private static final CallbackReminderModel.Day DAY=CallbackReminderModel.day(NOW,UTC);
 private static final String FIRST="0500099971",SECOND="0500099972";
 private CallbackReminderModel.Row row(long id,String number,long at,long duration,int type){
  return new CallbackReminderModel.Row(id,number,DAY.start+at*1000,duration,type,CallbackReminderModel.ALLOWED);
 }
 private CallbackReminderModel.Row miss(long id,String number,long at){return row(id,number,at,0,CallbackReminderModel.MISSED);}
 private List<CallbackReminderModel.Entry> reduce(CallbackReminderModel.Row... rows){return CallbackReminderModel.reduce(Arrays.asList(rows),DAY,NOW);}
 @Test public void onlyTodayObservedMissedCallsAppear(){
  assertEquals(1,reduce(miss(1,FIRST,-1),miss(2,FIRST,10),miss(3,SECOND,80000),miss(4,SECOND,86400)).size());
  assertEquals(1,reduce(miss(1,FIRST,-1),miss(2,FIRST,10),miss(3,SECOND,80000),miss(4,SECOND,86400)).get(0).missedCount);
 }
 @Test public void allDayMissesAreGroupedAcrossIsraeliAliases(){
  CallbackReminderModel.Entry entry=reduce(miss(1,FIRST,10),miss(2,"+972 50-009-9971",20),miss(3,"00972500099971",30)).get(0);
  assertEquals(FIRST,entry.key);assertEquals(3,entry.missedCount);assertEquals(3,entry.missedCountToday);assertEquals(3,entry.latestMissedId);
 }
 @Test public void zeroAndOneSecondCallbacksDoNotResolveAndAreAttempts(){
  CallbackReminderModel.Entry entry=reduce(miss(1,FIRST,10),row(2,FIRST,20,0,2),row(3,FIRST,30,1,2)).get(0);
  assertEquals(1,entry.missedCount);assertEquals(2,entry.callbackAttempts);
 }
 @Test public void outgoingConnectedTwoSecondsResolves(){assertTrue(reduce(miss(1,FIRST,10),row(2,FIRST,20,2,2)).isEmpty());}
 @Test public void incomingConnectedConversationAlsoResolves(){assertTrue(reduce(miss(1,FIRST,10),row(2,FIRST,20,55,1)).isEmpty());}
 @Test public void earlierConversationCannotResolveLaterMiss(){
  CallbackReminderModel.Entry entry=reduce(row(1,FIRST,10,100,1),miss(2,FIRST,20)).get(0);assertEquals(1,entry.missedCount);
 }
 @Test public void laterMissMakesEarlierSuccessfulCallbackInsufficient(){
  CallbackReminderModel.Entry entry=reduce(miss(1,FIRST,10),row(2,FIRST,20,2,2),miss(3,FIRST,30),miss(4,FIRST,40),row(5,FIRST,50,0,2)).get(0);
  assertEquals(2,entry.missedCount);assertEquals(3,entry.missedCountToday);assertEquals(1,entry.callbackAttempts);
 }
 @Test public void rejectedBlockedAndVoicemailDurationsNeverResolve(){
  assertEquals(1,reduce(miss(1,FIRST,10),row(2,FIRST,20,400,5),row(3,FIRST,30,400,6),row(4,FIRST,40,400,4)).size());
 }
 @Test public void equalTimestampUsesStableCallLogOrder(){
  assertTrue(reduce(miss(1,FIRST,10),row(2,FIRST,10,2,2)).isEmpty());
  assertEquals(1,reduce(row(1,FIRST,10,2,2),miss(2,FIRST,10)).size());
 }
 @Test public void otherCallerCannotResolveOrCountAsAttempt(){
  CallbackReminderModel.Entry entry=reduce(miss(1,FIRST,10),row(2,SECOND,20,50,2)).get(0);assertEquals(0,entry.callbackAttempts);
 }
 @Test public void hiddenNumbersAreNotDialableOrResolvedByVisibleSentinels(){
  CallbackReminderModel.Row privateCall=new CallbackReminderModel.Row(1,"-1",DAY.start+10000,0,3,2);
  CallbackReminderModel.Entry entry=reduce(privateCall,row(2,"1",20,30,2)).get(0);assertTrue(entry.privateNumber);assertEquals("",entry.number);
  for(String sentinel:Arrays.asList("-1","-2","-3")){
   assertEquals("",CallbackReminderModel.safeNumber(sentinel));assertEquals("",CallbackReminderModel.key(sentinel));
   assertEquals("",reduce(miss(4,sentinel,30)).get(0).number);
  }
 }
 @Test public void uriAndNonPhoneInputCannotBecomeNumbers(){
  for(String value:Arrays.asList("tel:0500099971","sip:someone@example.test","a0500099971","","+"))assertEquals("",CallbackReminderModel.safeNumber(value));
 }
 @Test public void serviceCodesAndPostdialIdentityAreNotMergedIntoOrdinaryNumbers(){
  assertEquals("*1234",CallbackReminderModel.key("*1234"));assertEquals("0500099971,4",CallbackReminderModel.key("0500099971,4"));
  assertEquals(2,reduce(miss(1,FIRST,10),miss(2,FIRST+",4",20)).size());
 }
 @Test public void outputIsNewestFirstImmutableAndInputOrderIndependent(){
  ArrayList<CallbackReminderModel.Row> rows=new ArrayList<>(Arrays.asList(miss(1,FIRST,10),miss(2,SECOND,20)));
  Collections.reverse(rows);List<CallbackReminderModel.Entry> result=CallbackReminderModel.reduce(rows,DAY,NOW);assertEquals(SECOND,result.get(0).key);
  try{result.clear();fail("Immutable snapshot required");}catch(UnsupportedOperationException expected){}
 }
 @Test public void localDaysFollowDstRatherThanTwentyFourHourArithmetic(){
  TimeZone ny=TimeZone.getTimeZone("America/New_York");
  CallbackReminderModel.Day spring=CallbackReminderModel.day(Instant.parse("2026-03-08T18:00:00Z").toEpochMilli(),ny);
  CallbackReminderModel.Day autumn=CallbackReminderModel.day(Instant.parse("2026-11-01T18:00:00Z").toEpochMilli(),ny);
  assertEquals(23*3600000L,spring.end-spring.start);assertEquals(25*3600000L,autumn.end-autumn.start);
 }
 @Test public void cutoffRollsToNextLocalDayAfterCurrentMinute(){
  assertEquals(Instant.parse("2026-11-03T21:00:00Z").toEpochMilli(),CallbackReminderModel.nextAlarm(NOW,1260,UTC));
  assertEquals(NOW,CallbackReminderModel.nextAlarm(NOW-1,1260,UTC));
 }
 @Test public void notificationDayValidationCannotBroadenToInvalidOrFutureDay(){
  assertEquals("2026-11-01",CallbackReminderModel.selectedDay("2026-11-01",NOW,UTC).key);
  for(String value:Arrays.asList("2026-02-30","2027-01-01","garbage","2026-11-01 OR1=1"))
   assertEquals(DAY.key,CallbackReminderModel.selectedDay(value,NOW,UTC).key);
 }
 @Test public void unusuallyLargeDayIsReducedWithoutPairwiseScanning(){
  ArrayList<CallbackReminderModel.Row> rows=new ArrayList<>();
  for(int n=0;n<12000;n++){String number="05"+(10000000+n);rows.add(miss(n*2,number,10));rows.add(row(n*2+1,number,20,2,2));}
  assertTrue(CallbackReminderModel.reduce(rows,DAY,NOW).isEmpty());
 }
}
