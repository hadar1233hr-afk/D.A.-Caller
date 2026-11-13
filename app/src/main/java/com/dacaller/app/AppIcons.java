package com.dacaller.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import java.util.ArrayList;

/** Launcher-only aliases; the enabled MainActivity continues to handle dial intents. */
final class AppIcons {
 static final String PREF="launcher_icon";
 static final String DEFAULT="crest";
 // Append choices: existing IDs, aliases and order are durable preference/backup identities.
 static final String[] IDS={"crest","orbit","monogram","prism","wave","aureole","facet","wine","signal"};
 private static final String[] ALIASES={"LauncherCrest","LauncherOrbit","LauncherMonogram","LauncherPrism","LauncherWave",
   "LauncherAureole","LauncherFacet","LauncherWine","LauncherSignal"};
 private static final String[] HE={"חותם אמרלד","מסלול זהב","מונוגרמת DA","מנסרה","גלי קול",
   "הילה זהובה","יהלום זהב","חותם יין","אות זוהר"};
 private static final String[] EN={"Emerald crest","Golden orbit","DA monogram","Prism","Sound waves",
   "Golden aureole","Golden facet","Wine seal","Luminous signal"};
 private static final int[] ICONS={R.mipmap.ic_launcher_crest,R.mipmap.ic_launcher_orbit,
   R.mipmap.ic_launcher_monogram,R.mipmap.ic_launcher_prism,R.mipmap.ic_launcher_wave,
   R.mipmap.ic_launcher_aureole,R.mipmap.ic_launcher_facet,R.mipmap.ic_launcher_wine,R.mipmap.ic_launcher_signal};
 static boolean valid(String id){return index(id)>=0;}
 private static int index(String id){for(int i=0;i<IDS.length;i++)if(IDS[i].equals(id))return i;return -1;}
 static String selected(Context context){
  String id=context.getSharedPreferences("da_caller",Context.MODE_PRIVATE).getString(PREF,DEFAULT);
  return valid(id)?id:DEFAULT;
 }
 static String label(Context context,String id){int i=index(id);return i<0?"":(I18n.isEnglish(context)?EN:HE)[i];}
 static int iconResource(String id){int i=index(id);return ICONS[i<0?0:i];}
 static Drawable preview(Context context,String id){return context.getDrawable(iconResource(id));}
 static ComponentName component(Context context,String id){
  int i=index(id);if(i<0)throw new IllegalArgumentException("Unknown launcher icon");
  return new ComponentName(context.getPackageName(),context.getPackageName()+"."+ALIASES[i]);
 }
 /** Apply aliases only. Backup restore can commit all preferences in one transaction. */
 static boolean apply(Context context,String id){
  int chosen=index(id);if(chosen<0)return false;
  PackageManager manager=context.getPackageManager();int[] prior=new int[IDS.length];boolean snapshotReady=false;
  try{
   for(int i=0;i<IDS.length;i++)prior[i]=manager.getComponentEnabledSetting(component(context,IDS[i]));
   snapshotReady=true;
   if(Build.VERSION.SDK_INT>=33){
    ArrayList<PackageManager.ComponentEnabledSetting> changes=new ArrayList<>();
    for(int i=0;i<IDS.length;i++)changes.add(new PackageManager.ComponentEnabledSetting(component(context,IDS[i]),
      i==chosen?PackageManager.COMPONENT_ENABLED_STATE_ENABLED:PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP));
    // Android 13 applies the complete set atomically, so there is never a moment
    // when a launcher queries zero enabled icons.
    manager.setComponentEnabledSettings(changes);
   }else{
    // Older systems get a launchable replacement before the previous alias is disabled.
    manager.setComponentEnabledSetting(component(context,id),PackageManager.COMPONENT_ENABLED_STATE_ENABLED,PackageManager.DONT_KILL_APP);
    for(int i=0;i<IDS.length;i++)if(i!=chosen)
     manager.setComponentEnabledSetting(component(context,IDS[i]),PackageManager.COMPONENT_ENABLED_STATE_DISABLED,PackageManager.DONT_KILL_APP);
   }
   return true;
  }catch(RuntimeException failure){
   if(!snapshotReady)return false;
   // Android 13 also restores the complete snapshot atomically. Older systems
   // restore their launchable original entry before removing the replacement.
   try{
    if(Build.VERSION.SDK_INT>=33){
     ArrayList<PackageManager.ComponentEnabledSetting> restore=new ArrayList<>();
     for(int i=0;i<IDS.length;i++)restore.add(new PackageManager.ComponentEnabledSetting(component(context,IDS[i]),prior[i],PackageManager.DONT_KILL_APP));
     manager.setComponentEnabledSettings(restore);
    }else{
     for(int i=0;i<IDS.length;i++)if(prior[i]==PackageManager.COMPONENT_ENABLED_STATE_ENABLED||
         (prior[i]==PackageManager.COMPONENT_ENABLED_STATE_DEFAULT&&i==0))
      manager.setComponentEnabledSetting(component(context,IDS[i]),prior[i],PackageManager.DONT_KILL_APP);
     for(int i=0;i<IDS.length;i++)if(!(prior[i]==PackageManager.COMPONENT_ENABLED_STATE_ENABLED||
         (prior[i]==PackageManager.COMPONENT_ENABLED_STATE_DEFAULT&&i==0)))
      manager.setComponentEnabledSetting(component(context,IDS[i]),prior[i],PackageManager.DONT_KILL_APP);
    }
   }catch(RuntimeException ignored){/* Preserve the first error; never disable MainActivity. */}
   return false;
  }
 }
 @android.annotation.SuppressLint("ApplySharedPref") // A failed durable write must roll back the launcher.
 static boolean select(Context context,String id){
  if(!valid(id))return false;
  SharedPreferences prefs=context.getSharedPreferences("da_caller",Context.MODE_PRIVATE);
  String old=selected(context),oldStored=prefs.getString(PREF,null);boolean hadPreference=prefs.contains(PREF);
  if(!apply(context,id))return false;
  if(prefs.edit().putString(PREF,id).commit())return true;
  SharedPreferences.Editor restore=prefs.edit();
  if(hadPreference)restore.putString(PREF,oldStored);else restore.remove(PREF);
  restore.commit();
  apply(context,old);return false;
 }
 /** Reconcile persisted choice only when needed, for example after a system restore. */
 static boolean ensure(Context context){
  String id=selected(context);PackageManager manager=context.getPackageManager();
  try{
   for(int i=0;i<IDS.length;i++){
    int state=manager.getComponentEnabledSetting(component(context,IDS[i]));
    boolean enabled=state==PackageManager.COMPONENT_ENABLED_STATE_ENABLED||
      (state==PackageManager.COMPONENT_ENABLED_STATE_DEFAULT&&i==0);
    if(enabled!=IDS[i].equals(id))return apply(context,id);
   }
   return true;
  }catch(RuntimeException failure){return false;}
 }
 private AppIcons(){}
}
