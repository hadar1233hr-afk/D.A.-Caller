package com.dacaller.app;

import android.app.Activity;

/** Presentation boundary shared by the app island and its isolated rendering fixture. */
interface CallIslandSession {
 CallIslandState current();
 boolean perform(String token,String action);
 void open(Activity host,String token);

 static CallIslandSession telecom(){return Telecom.INSTANCE;}

 final class Telecom implements CallIslandSession {
  private static final Telecom INSTANCE=new Telecom();
  private Telecom(){}
  public CallIslandState current(){return PhoneSessionService.islandSnapshot();}
  public boolean perform(String token,String action){return PhoneSessionService.performIslandAction(token,action);}
  public void open(Activity host,String token){PhoneSessionService.openIslandCall(host,token);}
 }
}
