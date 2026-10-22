package com.dacaller.app;
import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.net.Uri;
import android.widget.RemoteViews;
final class WidgetSupport {
 static SharedPreferences prefs(Context c){return c.getSharedPreferences("da_caller",0);}
 static PendingIntent target(Context c,int id,String name,String phone,boolean call){
  Intent i=new Intent(c,WidgetTap.class);i.putExtra("name",name);i.putExtra("phone",phone);i.putExtra("call",call);
  i.setData(Uri.parse("dacaller://widget/"+id+"/"+Math.abs(phone.hashCode())+"/"+(call?1:0)));
  return PendingIntent.getActivity(c,Math.abs(id*31+phone.hashCode()),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
 }
 static void update(Context c,AppWidgetManager m,int id,boolean call){
  String name=prefs(c).getString("widget_name_"+id,"בחר איש קשר");
  String phone=prefs(c).getString("widget_phone_"+id,"");
  RemoteViews r=new RemoteViews(c.getPackageName(),R.layout.widget_one);
  r.setTextViewText(R.id.widget_name,name);r.setTextViewText(R.id.widget_icon,call?"☎":"☷");
  if(!phone.isEmpty())r.setOnClickPendingIntent(R.id.widget_root,target(c,id,name,phone,call));
  else r.setOnClickPendingIntent(R.id.widget_root,PendingIntent.getActivity(c,id,new Intent(c,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT));
  m.updateAppWidget(id,r);
 }
}