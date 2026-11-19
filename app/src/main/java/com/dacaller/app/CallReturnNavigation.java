package com.dacaller.app;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import java.util.Map;
import java.util.WeakHashMap;

/** Return from Telecom UI without moving the whole application to Home or replaying a dial intent. */
final class CallReturnNavigation {
 static final String ACTION_RETURN="com.dacaller.app.RETURN_FROM_CALL";
 private static final String ACTION_CHILD="com.dacaller.app.RESTORE_CALL_DESTINATION";
 static final String PREFERENCES="da_caller_navigation";
 private static final String[] INTS={"recent_position","recent_offset","contact_position","contact_offset",
   "stats_period","favorite_scroll","settings_scroll","business_offset","business_position","business_top"};
 private static final String[] LONGS={"recent_id","contact_id","business_id","recent_expanded_id","recent_expanded_date"};
 private static final WeakHashMap<Application,Boolean> installed=new WeakHashMap<>();
 private static SharedPreferences prefs(Context context){return context.getSharedPreferences(PREFERENCES,Context.MODE_PRIVATE);}
 static boolean isReturn(Intent intent){return intent!=null&&ACTION_RETURN.equals(intent.getAction());}
 static Intent intent(Context context){
  return new Intent(context,MainActivity.class).setAction(ACTION_RETURN)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
 }
 static void returnToApp(Activity callScreen){
  if(callScreen.isFinishing())return;
  if(!callScreen.isTaskRoot()){
   // Keep the actual Activity below us: contacts, settings, archive or a detail
   // card already owns its view state, search and scroll position.
   callScreen.finish();return;
  }
  ActivityManager manager=callScreen.getSystemService(ActivityManager.class);
  if(manager!=null)try{
   for(ActivityManager.AppTask task:manager.getAppTasks()){
    ActivityManager.RecentTaskInfo info=task.getTaskInfo();
    if(info==null||info.id==callScreen.getTaskId()||info.topActivity==null||
       !callScreen.getPackageName().equals(info.topActivity.getPackageName())||
       CallScreenActivity.class.getName().equals(info.topActivity.getClassName()))continue;
    task.moveToFront();callScreen.finish();return;
   }
  }catch(RuntimeException unavailable){/* A cold internal return remains available. */}
  callScreen.startActivity(intent(callScreen));callScreen.finish();
 }
 /** A small, typed UI snapshot in runtime preferences, outside the settings backup. */
 static void saveMain(Context context,Bundle state){
  SharedPreferences.Editor edit=prefs(context).edit();
  String page=state.getString("page","recents");
  edit.putString("page",validPage(page));
  edit.putString("settings_return_page",returnPage(state.getString("settings_return_page","recents")));
  edit.putString("recent_expanded_number",bounded(state.getString("recent_expanded_number","")));
  edit.putString("contact_query",bounded(state.getString("contact_query","")));
  edit.putString("business_query",bounded(state.getString("business_query","")));
  edit.putBoolean("contacts_business_mode",state.getBoolean("contacts_business_mode",false));
  for(String key:INTS)edit.putInt(key,state.getInt(key,0));
  for(String key:LONGS)edit.putLong(key,state.getLong(key,longDefault(key)));
  edit.apply();
 }
 static Bundle mainState(Context context){
  Map<String,?> values=prefs(context).getAll();Bundle state=new Bundle();
  Object page=values.get("page");state.putString("page",validPage(page instanceof String?(String)page:"recents"));
  Object returnPage=values.get("settings_return_page");state.putString("settings_return_page",returnPage(returnPage instanceof String?(String)returnPage:"recents"));
  Object expanded=values.get("recent_expanded_number");state.putString("recent_expanded_number",bounded(expanded instanceof String?(String)expanded:""));
  Object query=values.get("contact_query");state.putString("contact_query",bounded(query instanceof String?(String)query:""));
  Object businessQuery=values.get("business_query");state.putString("business_query",bounded(businessQuery instanceof String?(String)businessQuery:""));
  state.putBoolean("contacts_business_mode",Boolean.TRUE.equals(values.get("contacts_business_mode")));
  for(String key:INTS){Object value=values.get(key);state.putInt(key,value instanceof Integer?(Integer)value:0);}
  for(String key:LONGS){Object value=values.get(key);state.putLong(key,value instanceof Long?(Long)value:longDefault(key));}
  return state;
 }
 private static long longDefault(String key){return "recent_expanded_id".equals(key)?Long.MIN_VALUE:"recent_expanded_date".equals(key)?0:-1;}
 private static String returnPage(String value){return "contacts".equals(value)?"contacts":"recents";}
 private static String validPage(String value){return "contacts".equals(value)||"settings".equals(value)?value:"recents";}
 private static String bounded(String value){return value==null?"":value.substring(0,Math.min(value.length(),4096));}
 /** Track only known, read-only destinations; never retain Activities or arbitrary launch extras. */
 static void install(Activity activity){
  Application application=activity.getApplication();
  synchronized(installed){if(installed.containsKey(application))return;installed.put(application,true);}
  application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){
   public void onActivityCreated(Activity activity,Bundle state){}
   public void onActivityStarted(Activity activity){}
   public void onActivityResumed(Activity activity){
    SharedPreferences.Editor edit=prefs(activity).edit();
    if(activity instanceof MainActivity)edit.putString("last_screen","main").apply();
    else if(activity instanceof ArchiveActivity){
     edit.putString("last_screen","archive").apply();
     Intent restored=activity.getIntent();
     if(restored!=null&&ACTION_CHILD.equals(restored.getAction())){
      EditText search=find(activity.findViewById(android.R.id.content),EditText.class);
      if(search!=null)search.setText(bounded(restored.getStringExtra("archive_query")));
      restored.setAction(null);restored.removeExtra("archive_query");
     }
    }else if(activity instanceof CallbackReminderActivity){
     edit.putString("last_screen","callback-reminder").apply();
    }else if(activity instanceof ContactDetailActivity){
     long id=activity.getIntent().getLongExtra("contact_id",-1);
     if(id>0)edit.putString("last_screen","contact").putLong("last_contact_id",id).apply();
    }
   }
   public void onActivityPaused(Activity activity){}
   public void onActivityStopped(Activity activity){
    if(activity instanceof ArchiveActivity){
     EditText search=find(activity.findViewById(android.R.id.content),EditText.class);
     if(search!=null)prefs(activity).edit().putString("archive_query",bounded(search.getText().toString())).apply();
    }
   }
   public void onActivitySaveInstanceState(Activity activity,Bundle state){}
   public void onActivityDestroyed(Activity activity){}
  });
 }
 static Intent lastDestination(Context context){
  SharedPreferences runtime=prefs(context);String screen=runtime.getString("last_screen","main");
  if("archive".equals(screen)&&CallArchive.enabled(context))return new Intent(context,ArchiveActivity.class)
    .setAction(ACTION_CHILD).putExtra("archive_query",bounded(runtime.getString("archive_query","")));
  if("contact".equals(screen)&&context.checkSelfPermission(Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED){
   long id=runtime.getLong("last_contact_id",-1);
   if(id>0)return new Intent(context,ContactDetailActivity.class).putExtra("contact_id",id);
  }
  if("callback-reminder".equals(screen))return new Intent(context,CallbackReminderActivity.class);
  return null;
 }
 private static <T extends View>T find(View view,Class<T> type){
  if(type.isInstance(view))return type.cast(view);
  if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){
   T result=find(((ViewGroup)view).getChildAt(i),type);if(result!=null)return result;
  }
  return null;
 }
 private CallReturnNavigation(){}
}
