package com.dacaller.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class IncomingGestureTest {
 @Test public void allFourModesHaveExplicitPhysicalDirections(){
  assertEquals(IncomingGesture.Action.ANSWER,IncomingGesture.resolve("right",80,2,64));
  assertEquals(IncomingGesture.Action.DECLINE,IncomingGesture.resolve("right",-80,2,64));
  assertEquals(IncomingGesture.Action.ANSWER,IncomingGesture.resolve("left",-80,2,64));
  assertEquals(IncomingGesture.Action.DECLINE,IncomingGesture.resolve("left",80,2,64));
  assertEquals(IncomingGesture.Action.ANSWER,IncomingGesture.resolve("vertical",2,-80,64));
  assertEquals(IncomingGesture.Action.DECLINE,IncomingGesture.resolve("vertical",2,80,64));
  assertEquals(IncomingGesture.Action.ANSWER,IncomingGesture.resolve("ios",80,2,64));
  assertEquals(IncomingGesture.Action.NONE,IncomingGesture.resolve("ios",-80,2,64));
 }
 @Test public void tapsShortDragsAndDiagonalScrollCannotAnswer(){
  for(String mode:new String[]{"ios","right","left","vertical"}){
   assertEquals(IncomingGesture.Action.NONE,IncomingGesture.resolve(mode,0,0,64));
   assertEquals(IncomingGesture.Action.NONE,IncomingGesture.resolve(mode,40,40,64));
   assertEquals(IncomingGesture.Action.NONE,IncomingGesture.resolve(mode,80,80,64));
  }
 }
 @Test public void unknownStoredModeSafelyFallsBackToIos(){
  assertEquals("ios",IncomingGesture.mode("invalid"));
  assertEquals("ios",IncomingGesture.mode(null));
  assertEquals(IncomingGesture.Action.NONE,IncomingGesture.resolve("invalid",-100,0,64));
 }
}
