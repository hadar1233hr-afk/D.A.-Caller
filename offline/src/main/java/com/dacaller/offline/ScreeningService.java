package com.dacaller.offline;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.net.Uri;

public final class ScreeningService extends CallScreeningService {
    private static final String CHANNEL="da_id_local_caller";
    @Override public void onScreenCall(Call.Details details) {
        // A screening service must respond promptly; never perform network access here.
        if(Build.VERSION.SDK_INT>=29 && details.getCallDirection()!=Call.Details.DIRECTION_INCOMING)return;
        Uri handle=details.getHandle();
        String number=handle==null?"":handle.getSchemeSpecificPart();
        CallerDb.Entry entry=null;
        try(CallerDb db=new CallerDb(this)){entry=db.find(number);}catch(Exception ignored){}
        boolean spam=entry!=null && "spam".equals(entry.kind);
        boolean block=spam && getSharedPreferences("da_offline_id",MODE_PRIVATE)
            .getBoolean("block_spam",false)
            && CallerDb.canonical(number).length()>4; // never intentionally block short emergency/service codes
        CallResponse response=new CallResponse.Builder()
            .setDisallowCall(block).setRejectCall(block)
            .setSkipCallLog(false).setSkipNotification(false).build();
        respondToCall(details,response);
        if(entry!=null)notifyIdentified(entry,block);
    }
    private void notifyIdentified(CallerDb.Entry e,boolean blocked){
        if(Build.VERSION.SDK_INT>=33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        NotificationManager manager=(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager==null)return;
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel channel=new NotificationChannel(CHANNEL,"זיהוי מתקשרים אופליין",NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("שם מתקשר שמזוהה באמצעות מאגר מקומי");
            manager.createNotificationChannel(channel);
        }
        String title=blocked?"נחסמה שיחת ספאם":e.kind.equals("spam")?"שיחה חשודה: "+e.name:e.name;
        Notification n=new Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle(title)
            .setContentText(e.phone+" • זוהה ממאגר מקומי")
            .setCategory(Notification.CATEGORY_CALL)
            .setPriority(Notification.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build();
        manager.notify(1711,n);
    }
}
