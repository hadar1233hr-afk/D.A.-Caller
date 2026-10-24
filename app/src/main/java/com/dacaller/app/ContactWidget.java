package com.dacaller.app;
import android.appwidget.*;
import android.content.*;
public class ContactWidget extends AppWidgetProvider {
 @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){for(int id:ids)WidgetSupport.update(c,m,id,false);}
 @Override public void onDeleted(Context c,int[] ids){SharedPreferences.Editor e=WidgetSupport.prefs(c).edit();for(int id:ids){e.remove("widget_contact_"+id);e.remove("widget_name_"+id);e.remove("widget_phone_"+id);}e.apply();}
}