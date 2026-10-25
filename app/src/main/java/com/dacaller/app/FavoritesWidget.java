package com.dacaller.app;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;

public class FavoritesWidget extends AppWidgetProvider {
 @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){
  final android.content.BroadcastReceiver.PendingResult pending=goAsync();
  WidgetSupport.updateFavorites(c,m,ids,()->{if(pending!=null)pending.finish();});
 }
 @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager m,int id,Bundle options){onUpdate(c,m,new int[]{id});}
 static void refresh(Context c){
  Context app=c.getApplicationContext();AppWidgetManager manager=AppWidgetManager.getInstance(app);
  int[] ids=manager.getAppWidgetIds(new ComponentName(app,FavoritesWidget.class));
  if(ids.length>0)WidgetSupport.updateFavorites(app,manager,ids,()->{});
 }
 static void update(Context c,AppWidgetManager m,int[] ids){WidgetSupport.updateFavorites(c,m,ids,()->{});}
}
