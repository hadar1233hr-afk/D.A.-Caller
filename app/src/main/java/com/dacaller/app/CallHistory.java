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
   String raw=number==null?"":number.trim();
   this.id=id;this.number=("-1".equals(raw)||"-2".equals(raw)||"-3".equals(raw))?"":raw;
   this.date=date;this.duration=Math.max(0,duration);this.type=type;
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
 static volatile long fetchedAt;
 static volatile boolean readFailed;
 private static boolean loading,refreshAgain;
 private static long permissionGeneration;
 private static final ArrayList<Runnable> callbacks=new ArrayList<>();
 static void refresh(Context context,boolean force,Runnable complete){
  final Context app=context.getApplicationContext();
  synchronized(CallHistory.class){
   if(!hasPermission(app)){
    permissionGeneration++;clearSensitiveCache();
    if(complete!=null)ui.post(complete);return;
   }
   if(complete!=null)callbacks.add(complete);
   if(loading){if(force)refreshAgain=true;return;}
   if(!force&&System.currentTimeMillis()-fetchedAt<18000){finishCallbacks();return;}
   loading=true;
  }
  worker.execute(()->{
   boolean repeat;
   do{
    long generation;
    synchronized(CallHistory.class){generation=permissionGeneration;refreshAgain=false;}
    read(app,generation);
    synchronized(CallHistory.class){
     repeat=refreshAgain&&hasPermission(app);
     if(!repeat){loading=false;finishCallbacks();}
    }
   }while(repeat);
  });
 }
 private static boolean hasPermission(Context c){return c.checkSelfPermission(Manifest.permission.READ_CALL_LOG)==PackageManager.PERMISSION_GRANTED;}
 private static void clearSensitiveCache(){entries=Collections.emptyList();summary=new Summary();fetchedAt=0;readFailed=false;}
 private static void read(Context app,long generation){
  ArrayList<Entry> found=new ArrayList<>();Summary stats=new Summary();
  Calendar c=Calendar.getInstance();c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);
  c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);long today=c.getTimeInMillis();
  c.setFirstDayOfWeek(Calendar.SUNDAY);c.set(Calendar.DAY_OF_WEEK,Calendar.SUNDAY);long week=c.getTimeInMillis();
  c.setTimeInMillis(today);c.set(Calendar.DAY_OF_MONTH,1);long month=c.getTimeInMillis();
  c.set(Calendar.DAY_OF_YEAR,1);long year=c.getTimeInMillis();long[] starts={today,week,month,year};
  boolean completeRead=false;
  String[] columns={CallLog.Calls._ID,CallLog.Calls.NUMBER,CallLog.Calls.DATE,CallLog.Calls.DURATION,CallLog.Calls.TYPE};
  try(Cursor cursor=app.getContentResolver().query(CallLog.Calls.CONTENT_URI,columns,null,null,
    CallLog.Calls.DATE+" DESC,"+CallLog.Calls._ID+" DESC")){
   if(cursor!=null){
    while(cursor.moveToNext()){
     Entry entry=new Entry(cursor.getLong(0),cursor.getString(1),cursor.getLong(2),cursor.getLong(3),cursor.getInt(4));
     found.add(entry);
     for(int i=0;i<4;i++)if(entry.date>=starts[i]){
      stats.calls[i]++;
      if(entry.type==CallLog.Calls.MISSED_TYPE)stats.missed[i]++;
      else if(entry.type==CallLog.Calls.OUTGOING_TYPE){stats.outgoing[i]++;stats.seconds[i]+=entry.duration;}
      else if(entry.type==CallLog.Calls.INCOMING_TYPE){stats.incoming[i]++;stats.seconds[i]+=entry.duration;}
     }
    }
    completeRead=true;
   }
  }catch(RuntimeException ignored){/* Preserve last complete snapshot on provider failure. */}
  synchronized(CallHistory.class){
   if(!hasPermission(app)||generation!=permissionGeneration){clearSensitiveCache();return;}
   readFailed=!completeRead;
   if(completeRead){entries=Collections.unmodifiableList(found);summary=stats;fetchedAt=System.currentTimeMillis();}
  }
  // A null/failed/partial cursor must never mark all saved calls as disappeared.
  if(completeRead&&hasPermission(app)&&CallArchive.enabled(app))CallArchive.sync(app,found);
 }
 private static void finishCallbacks(){
  ArrayList<Runnable> ready=new ArrayList<>(callbacks);callbacks.clear();
  for(Runnable item:ready)ui.post(item);
 }
}
