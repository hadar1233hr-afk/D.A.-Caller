package com.dacaller.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.provider.CallLog;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class CallHistory {
 static final class Entry {
  final long id;
  final String number;
  final long date,duration;
  final int type;
  Entry(long id,String number,long date,long duration,int type){
   this.id=id;this.number=number;this.date=date;this.duration=duration;this.type=type;
  }
  Entry(String n,long when,long seconds,int kind){this(when,n,when,seconds,kind);}
 }
 static final class Summary {
  final long[] seconds=new long[4];
  final int[] calls=new int[4],incoming=new int[4],outgoing=new int[4],missed=new int[4];
 }
 static final ExecutorService worker=Executors.newSingleThreadExecutor();
 static final Handler ui=new Handler(Looper.getMainLooper());
 static volatile List<Entry> entries=Collections.emptyList();
 static volatile Summary summary=new Summary();
 static volatile long fetchedAt=0;
 private static boolean loading=false;
 private static final ArrayList<Runnable> callbacks=new ArrayList<>();
 static void refresh(Context context,boolean force,Runnable complete){
  if(context.checkSelfPermission(Manifest.permission.READ_CALL_LOG)!=PackageManager.PERMISSION_GRANTED){
   if(complete!=null)ui.post(complete);return;
  }
  synchronized(CallHistory.class){
   if(complete!=null)callbacks.add(complete);
   if(loading)return;
   if(!force&&System.currentTimeMillis()-fetchedAt<18000){
    finishCallbacks();return;
   }
   loading=true;
  }
  final Context app=context.getApplicationContext();
  worker.execute(()->{
   ArrayList<Entry> found=new ArrayList<>();
   Summary stats=new Summary();
   Calendar c=Calendar.getInstance();
   c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);
   c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);
   long today=c.getTimeInMillis();
   c.setFirstDayOfWeek(Calendar.SUNDAY);c.set(Calendar.DAY_OF_WEEK,c.getFirstDayOfWeek());
   long week=c.getTimeInMillis();
   c.setTimeInMillis(today);c.set(Calendar.DAY_OF_MONTH,1);
   long month=c.getTimeInMillis();
   c.set(Calendar.DAY_OF_YEAR,1);long year=c.getTimeInMillis();
   long[] starts={today,week,month,year};
   boolean completeRead=false;
   String[] columns={CallLog.Calls._ID,CallLog.Calls.NUMBER,CallLog.Calls.DATE,
      CallLog.Calls.DURATION,CallLog.Calls.TYPE};
   try(Cursor cursor=app.getContentResolver().query(CallLog.Calls.CONTENT_URI,columns,null,null,CallLog.Calls.DATE+" DESC")){
    if(cursor!=null){
     while(cursor.moveToNext()){
      long id=cursor.getLong(0);
      String number=cursor.getString(1);
      if(number==null||number.isEmpty())number="מספר חסוי";
      long at=cursor.getLong(2);
      long seconds=Math.max(0,cursor.getLong(3));
      int kind=cursor.getInt(4);
      found.add(new Entry(id,number,at,seconds,kind));
      for(int i=0;i<4;i++)if(at>=starts[i]){
       stats.calls[i]++;
       if(kind==CallLog.Calls.MISSED_TYPE)stats.missed[i]++;
       else if(kind==CallLog.Calls.OUTGOING_TYPE){stats.outgoing[i]++;stats.seconds[i]+=seconds;}
       else if(kind==CallLog.Calls.INCOMING_TYPE){stats.incoming[i]++;stats.seconds[i]+=seconds;}
      }
     }
     completeRead=true;
    }
   }catch(Exception ignored){}
   if(completeRead){
    entries=Collections.unmodifiableList(found);
    summary=stats;fetchedAt=System.currentTimeMillis();
    if(CallArchive.enabled(app)){
     try{CallArchive.sync(app,found);}catch(Exception ignored){}
    }
   }
   synchronized(CallHistory.class){
    loading=false;finishCallbacks();
   }
  });
 }
 private static void finishCallbacks(){
  ArrayList<Runnable> ready=new ArrayList<>(callbacks);callbacks.clear();
  for(Runnable item:ready)ui.post(item);
 }
}
