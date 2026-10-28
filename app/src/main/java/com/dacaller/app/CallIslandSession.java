package com.dacaller.app;

import android.app.Activity;
import android.content.Context;

/** Presentation boundary shared by the app island and its isolated rendering fixture. */
interface CallIslandSession {
 CallIslandState current();
 boolean perform(String token,String action);
 void open(Activity host,String token);
 default void openOutside(Context host,String token){throw new UnsupportedOperationException("This session has no external call navigation");}

 static CallIslandSession telecom(){return Telecom.INSTANCE;}

 final class Telecom implements CallIslandSession {
  private static final Telecom INSTANCE=new Telecom();
  private Telecom(){}
  public CallIslandState current(){return PhoneSessionService.islandSnapshot();}
  public boolean perform(String token,String action){return PhoneSessionService.performIslandAction(token,action);}
  public void open(Activity host,String token){PhoneSessionService.openIslandCall(host,token);}
  public void openOutside(Context host,String token){PhoneSessionService.openIslandCall(host,token);}
 }
}
