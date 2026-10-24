package com.dacaller.app;

import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.List;
import java.util.HashSet;
import org.junit.Test;

/** Regression checks: real arrangement differences rather than five palette choices. */
public class CallLayoutsTest {
 @Test public void eachCallScreenHasADistinctControlOrder(){
  List<String> all=Arrays.asList(InCallControls.IDS);
  HashSet<String> fingerprints=new HashSet<>();
  for(int i=0;i<5;i++){
   List<String> ordered=CallLayouts.ordered(i,all);
   assertEquals(all.size(),ordered.size());
   assertEquals(new HashSet<>(all),new HashSet<>(ordered));
   assertTrue("duplicate layout "+i,fingerprints.add(ordered.toString()+CallLayouts.columns(i,false,8)));
  }
 }
 @Test public void adaptiveColumnsAreSafeOnNarrowPhone(){
  for(int style=0;style<5;style++)for(int size=1;size<=8;size++){
   int count=CallLayouts.columns(style,true,size);
   assertTrue(count>=1&&count<=size);
  }
 }
 @Test public void largeTypeLayoutsHaveReachableControls(){
  for(int style=0;style<5;style++){
   assertTrue(CallLayouts.buttonHeight(style,true,64)>=52);
   assertTrue(CallLayouts.avatarSize(style,true)>0);
  }
  assertTrue(CallLayouts.endAboveControls(2));
  assertFalse(CallLayouts.endAboveControls(1));
 }
 @Test public void orderRetainsOnlyEnabledActions(){
  List<String> enabled=Arrays.asList("speaker","keypad","notes");
  assertEquals(3,CallLayouts.ordered(2,enabled).size());
  assertEquals("speaker",CallLayouts.ordered(2,enabled).get(0));
 }
}
