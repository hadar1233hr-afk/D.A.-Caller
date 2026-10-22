package com.dacaller.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.CallLog;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class CallHistory {
 static class Entry {
  final String number;
  final long date, duration;
  final int type;
  Entry(String n,long when,long seconds,int kind){number=n;date=when;duration=seconds;type=kind;}
 }
 static class Summary {
  final long[] seconds=new long[4];
  final int[] calls=new int[4],incoming=new int[4],outgoing=new int[4],missed=new int[4];
 }
 static final ExecutorService worker=Executors.newSingleThreadExecutor();
 static final Handler ui=new Handler(Looper.getMainLooper());
 static volatile List<Entry> entries=Collections.emptyList();
 static volatile Summary summary=new Summary();
 static volatile long fetchedAt=0;
 static volatile boolean loading=false;
 static void refresh(Context c,boolean force,Runnable complete){
  if(c.checkSelfPermission(Manifest.permission.READ_CALL_LOG)!=PackageManager.PERMISSION_GRANTED){if(complete!=null)ui.post(complete);return;}
  if(loading){return;}
  if(!force && System.currentTimeMillis()-fetchedAt<12000){if(complete!=null)ui.post(complete);return;}
  loading=true;
  Context app=c.getApplicationContext();
  worker.execute(()->{
   ArrayList<Entry> latest=new ArrayList<>();Summary stats=new Summary();
   Calendar day=Calendar.getInstance();day.set(Calendar.HOUR_OF_DAY,0);day.set(Calendar.MINUTE,0);day.set(Calendar.SECOND,0);day.set(Calendar.MILLISECOND,0);long today=day.getTimeInMillis();
   day.setFirstDayOfWeek(Calendar.SUNDAY);day.set(Calendar.DAY_OF_WEEK,day.getFirstDayOfWeek());long week=day.getTimeInMillis();
   day.setTimeInMillis(today);day.set(Calendar.DAY_OF_MONTH,1);long month=day.getTimeInMillis();
   day.set(Calendar.DAY_OF_YEAR,1);long year=day.getTimeInMillis();
   long[] starts={today,week,month,year};
   String[] cols={CallLog.Calls.NUMBER,CallLog.Calls.DATE,CallLog.Calls.DURATION,CallLog.Calls.TYPE};
   try(Cursor cursor=app.getContentResolver().query(CallLog.Calls.CONTENT_URI,cols,null,null,CallLog.Calls.DATE+" DESC")){
    if(cursor!=null)while(cursor.moveToNext()){
     String n=cursor.getString(0);if(n==null)n="מספר חסוי";
     long at=cursor.getLong(1),secs=Math.max(0,cursor.getLong(2));int type=cursor.getInt(3);
     if(latest.size()<180)latest.add(new Entry(n,at,secs,type));
     for(int i=0;i<4;i++)if(at>=starts[i]){
      stats.calls[i]++;
      if(type==CallLog.Calls.MISSED_TYPE)stats.missed[i]++;
      else if(type==CallLog.Calls.OUTGOING_TYPE){stats.outgoing[i]++;stats.seconds[i]+=secs;}
      else if(type==CallLog.Calls.INCOMING_TYPE){stats.incoming[i]++;stats.seconds[i]+=secs;}
     }
    }
   }catch(Exception ignored){}
   entries=Collections.unmodifiableList(latest);summary=stats;
   fetchedAt=System.currentTimeMillis();loading=false;
   if(complete!=null)ui.post(complete);
  });
 }
}
