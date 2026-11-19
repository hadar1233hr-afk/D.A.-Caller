package com.dacaller.app;

import android.os.Bundle;
import java.util.List;

/** One expanded call, bound to the actual log identity rather than an adapter position. */
final class RecentExpansionState {
 private long id=Long.MIN_VALUE,date;
 private String number="";
 boolean isExpanded(CallHistory.Entry entry){
  return entry!=null&&id==entry.id&&date==entry.date&&number.equals(entry.number);
 }
 void toggle(CallHistory.Entry entry){
  if(isExpanded(entry)){collapse();return;}
  id=entry.id;date=entry.date;number=entry.number;
 }
 boolean collapse(){
  if(id==Long.MIN_VALUE)return false;
  id=Long.MIN_VALUE;date=0;number="";return true;
 }
 void retain(List<CallHistory.Entry> entries){
  if(id==Long.MIN_VALUE)return;
  for(CallHistory.Entry entry:entries)if(isExpanded(entry))return;
  collapse();
 }
 void saveState(Bundle state){
  state.putLong("recent_expanded_id",id);state.putLong("recent_expanded_date",date);
  state.putString("recent_expanded_number",number);
 }
 void restoreState(Bundle state){
  id=state.getLong("recent_expanded_id",Long.MIN_VALUE);
  date=state.getLong("recent_expanded_date");number=state.getString("recent_expanded_number","");
  if(number==null)number="";
 }
}
