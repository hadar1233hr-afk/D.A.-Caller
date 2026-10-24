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
 @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){
  final android.content.BroadcastReceiver.PendingResult pending=goAsync();
  update(c,m,ids,()->{if(pending!=null)pending.finish();});
 }
 static void refresh(Context c){
  Context app=c.getApplicationContext();
  AppWidgetManager manager=AppWidgetManager.getInstance(app);
  int[] ids=manager.getAppWidgetIds(new ComponentName(app,FavoritesWidget.class));
  if(ids.length>0)update(app,manager,ids);
 }
 static void update(Context c,AppWidgetManager manager,int[] ids){update(c,manager,ids,()->{});}
 private static void update(Context c,AppWidgetManager manager,int[] ids,Runnable completed){
  if(ids==null||ids.length==0){completed.run();return;}
  Context context=c.getApplicationContext();int[] widgetIds=ids.clone();
  IO.execute(()->{
   try{
   Set<String> starred=new HashSet<>(WidgetSupport.prefs(context).getStringSet("favorites",Collections.emptySet()));
   ArrayList<Contacts.Person> matches=new ArrayList<>();
   if(!starred.isEmpty()){
    for(Contacts.Person person:Contacts.all(context)){
     if(starred.contains(person.key)&&!person.phone.isEmpty()){matches.add(person);if(matches.size()==3)break;}
    }
   }
   int[] slots={R.id.fav1,R.id.fav2,R.id.fav3};
   for(int id:widgetIds){
    RemoteViews layout=new RemoteViews(context.getPackageName(),R.layout.widget_favorites);
    WidgetSupport.theme(context,layout,R.id.widget_favorites_root);
    layout.setTextViewText(R.id.widget_favorites_title,"DA CALLER   ★  "+I18n.text(context,"מועדפים"));
    for(int i=0;i<3;i++){
     layout.setTextColor(slots[i],WidgetSupport.dark(context)?0xFFF2F2F2:0xFF1E1E1E);
     if(i<matches.size()){
      Contacts.Person p=matches.get(i);
      layout.setTextViewText(slots[i],"☎  "+p.name);
      layout.setOnClickPendingIntent(slots[i],WidgetSupport.target(context,id*10+i,p.name,p.phone,true));
     }else{
      layout.setTextViewText(slots[i],i==0?(I18n.isEnglish(context)?"☆ Open favorites":"☆ פתח את המועדפים"):"");
      Intent intent=new Intent(context,MainActivity.class);
      intent.setAction("com.dacaller.app.FAVORITES");
      intent.putExtra("favorites",true);
      PendingIntent open=PendingIntent.getActivity(context,id*10+i,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
      layout.setOnClickPendingIntent(slots[i],open);
     }
    }
    manager.updateAppWidget(id,layout);
   }
   }finally{completed.run();}
  });
 }
}
