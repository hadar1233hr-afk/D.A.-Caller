package com.dacaller.app;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;
public class QuickWidget extends AppWidgetProvider {
 @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){
  final android.content.BroadcastReceiver.PendingResult pending=goAsync();
  WidgetSupport.update(c,m,ids,true,()->{if(pending!=null)pending.finish();});
 }
 @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager m,int id,Bundle options){onUpdate(c,m,new int[]{id});}
 @Override public void onDeleted(Context c,int[] ids){WidgetSupport.delete(c,ids);}
}
