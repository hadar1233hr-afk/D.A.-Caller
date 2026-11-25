package com.dacaller.app;

/** Immutable, permission-scoped presentation of one currently owned Telecom call. */
final class CallIslandState {
 final String token,name,number;
 final int state,route,supportedRoutes;
 final boolean muted,canHold,canMute;
 final long connectedAtMillis,contactId;

 CallIslandState(String token,String name,String number,int state,int route,int supportedRoutes,
     boolean muted,boolean canHold,boolean canMute,long connectedAtMillis,long contactId){
  this.token=token==null?"":token;this.name=name==null?"":name;this.number=number==null?"":number;
  this.state=state;this.route=route;this.supportedRoutes=supportedRoutes;
  this.muted=muted;this.canHold=canHold;this.canMute=canMute;
  this.connectedAtMillis=connectedAtMillis;this.contactId=contactId;
 }
}
