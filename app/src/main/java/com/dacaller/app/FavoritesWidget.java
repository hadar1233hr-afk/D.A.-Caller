package com.dacaller.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FavoritesWidget extends AppWidgetProvider {
 private static final ExecutorService IO=Executors.newSingleThreadExecutor();
 @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){update(c,m,ids);}
 static void refresh(Context c){
  Context app=c.getApplicationContext();
  AppWidgetManager manager=AppWidgetManager.getInstance(app);
  int[] ids=manager.getAppWidgetIds(new ComponentName(app,FavoritesWidget.class));
  if(ids.length>0)update(app,manager,ids);
 }
 static void update(Context c,AppWidgetManager manager,int[] ids){
  if(ids==null||ids.length==0)return;
  Context context=c.getApplicationContext();int[] widgetIds=ids.clone();
  IO.execute(()->{
   Set<String> starred=new HashSet<>(WidgetSupport.prefs(context).getStringSet("favorites",Collections.emptySet()));
   ArrayList<Contacts.Person> matches=new ArrayList<>();
   if(!starred.isEmpty()){
    for(Contacts.Person person:Contacts.all(context)){
     if(starred.contains(person.key))matches.add(person);
    }
   }
   int[] slots={R.id.fav1,R.id.fav2,R.id.fav3};
   for(int id:widgetIds){
    RemoteViews layout=new RemoteViews(context.getPackageName(),R.layout.widget_favorites);
    for(int i=0;i<3;i++){
     if(i<matches.size()){
      Contacts.Person p=matches.get(i);
      layout.setTextViewText(slots[i],"☎  "+p.name);
      layout.setOnClickPendingIntent(slots[i],WidgetSupport.target(context,id*10+i,p.name,p.phone,true));
     }else{
      layout.setTextViewText(slots[i],i==0?"☆ פתח את המועדפים":"");
      Intent intent=new Intent(context,MainActivity.class);
      intent.setAction("com.dacaller.app.FAVORITES");
      intent.putExtra("favorites",true);
      PendingIntent open=PendingIntent.getActivity(context,id*10+i,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
      layout.setOnClickPendingIntent(slots[i],open);
     }
    }
    manager.updateAppWidget(id,layout);
   }
  });
 }
}
