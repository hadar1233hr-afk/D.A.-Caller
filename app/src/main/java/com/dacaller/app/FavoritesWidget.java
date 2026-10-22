package com.dacaller.app;
import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.widget.RemoteViews;
import java.util.*;
public class FavoritesWidget extends AppWidgetProvider {
 @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){update(c,m,ids);}
 static void refresh(Context c){AppWidgetManager m=AppWidgetManager.getInstance(c);update(c,m,m.getAppWidgetIds(new ComponentName(c,FavoritesWidget.class)));}
 static void update(Context c,AppWidgetManager m,int[] ids){
  Set<String> starred=WidgetSupport.prefs(c).getStringSet("favorites",Collections.emptySet());
  ArrayList<Contacts.Person> matches=new ArrayList<>();
  for(Contacts.Person p:Contacts.all(c))if(starred.contains(p.key))matches.add(p);
  for(int id:ids){RemoteViews r=new RemoteViews(c.getPackageName(),R.layout.widget_favorites);
   int[] slots={R.id.fav1,R.id.fav2,R.id.fav3};
   for(int i=0;i<3;i++){
    if(i<matches.size()){Contacts.Person person=matches.get(i);r.setTextViewText(slots[i],"☎  "+person.name);
     r.setOnClickPendingIntent(slots[i],WidgetSupport.target(c,id*10+i,person.name,person.phone,true));
    }else{r.setTextViewText(slots[i],i==0?"☆ פתח את המועדפים":"");
     Intent intent=new Intent(c,MainActivity.class);intent.putExtra("favorites",true);
     r.setOnClickPendingIntent(slots[i],PendingIntent.getActivity(c,id*10+i,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
    }
   }
   m.updateAppWidget(id,r);
  }
 }
}