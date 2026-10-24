package com.dacaller.app;
import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.net.Uri;
import android.widget.RemoteViews;
final class WidgetSupport {
 static SharedPreferences prefs(Context c){return c.getSharedPreferences("da_caller",0);}
 static boolean dark(Context context){return prefs(context).getBoolean("dark",false)||prefs(context).getBoolean("amoled",false);}
 static void theme(Context context,RemoteViews views,int root){
  views.setInt(root,"setBackgroundResource",prefs(context).getBoolean("amoled",false)?R.drawable.widget_bg_amoled:dark(context)?R.drawable.widget_bg:R.drawable.widget_bg_light);
  views.setInt(root,"setLayoutDirection",I18n.direction(context));
 }
 static PendingIntent target(Context c,int id,String name,String phone,boolean call){
  Intent i=new Intent(c,WidgetTap.class);i.putExtra("contact_id",prefs(c).getLong("widget_contact_"+id,-1));i.putExtra("name",name);i.putExtra("phone",phone);i.putExtra("call",call);
  i.setData(Uri.parse("dacaller://widget/"+id+"/"+Math.abs(phone.hashCode())+"/"+(call?1:0)));
  return PendingIntent.getActivity(c,Math.abs(id*31+phone.hashCode()),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
 }
 static void update(Context c,AppWidgetManager m,int id,boolean call){
  String name=prefs(c).getString("widget_name_"+id,I18n.text(c,"בחר איש קשר"));
  String phone=prefs(c).getString("widget_phone_"+id,"");
  RemoteViews r=new RemoteViews(c.getPackageName(),R.layout.widget_one);
  theme(c,r,R.id.widget_root);
  r.setTextColor(R.id.widget_name,dark(c)?0xFFF7F7F7:0xFF1E1E1E);
  r.setTextViewText(R.id.widget_name,name);r.setTextViewText(R.id.widget_icon,call?"☎":"☷");
  if(!phone.isEmpty()||(!call&&prefs(c).getLong("widget_contact_"+id,-1)>0))r.setOnClickPendingIntent(R.id.widget_root,target(c,id,name,phone,call));
  else r.setOnClickPendingIntent(R.id.widget_root,PendingIntent.getActivity(c,id,new Intent(c,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT));
  m.updateAppWidget(id,r);
 }
}