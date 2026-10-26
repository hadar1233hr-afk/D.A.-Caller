package com.dacaller.app;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.app.PendingIntent;
import android.app.role.RoleManager;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.net.Uri;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=33)
public final class AppIconsTest {
 private Context app;
 private SharedPreferences prefs;
 @Before public void setup(){
  app=RuntimeEnvironment.getApplication();prefs=app.getSharedPreferences("da_caller",Context.MODE_PRIVATE);
  prefs.edit().clear().commit();
  for(String id:AppIcons.IDS)app.getPackageManager().setComponentEnabledSetting(AppIcons.component(app,id),
    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,PackageManager.DONT_KILL_APP);
 }
 @Test public void allFiveIconsHaveDistinctAdaptiveResourcesAndThemedForeground(){
  HashSet<Integer> resources=new HashSet<>();HashSet<String> labels=new HashSet<>();
  for(String id:AppIcons.IDS){
   assertTrue(AppIcons.valid(id));assertTrue(resources.add(AppIcons.iconResource(id)));
   assertTrue(labels.add(AppIcons.label(app,id)));
   assertTrue(AppIcons.preview(app,id) instanceof AdaptiveIconDrawable);
   AdaptiveIconDrawable icon=(AdaptiveIconDrawable)AppIcons.preview(app,id);
   assertNotNull(icon.getBackground());assertNotNull(icon.getForeground());assertNotNull(icon.getMonochrome());
  }
  assertEquals(5,resources.size());assertEquals("crest",AppIcons.selected(app));
 }
 @Test public void everyChoiceLeavesExactlyOneLauncherAndKeepsDialActivityEnabled()throws Exception{
  Intent launcher=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(app.getPackageName());
  for(String id:AppIcons.IDS){
   assertTrue(AppIcons.select(app,id));assertEquals(id,AppIcons.selected(app));
   List<ResolveInfo> activities=app.getPackageManager().queryIntentActivities(launcher,0);
   assertEquals("only one launcher for "+id,1,activities.size());
   assertEquals(AppIcons.component(app,id).getClassName(),activities.get(0).activityInfo.name);
   assertTrue(app.getPackageManager().getActivityInfo(new ComponentName(app,MainActivity.class),0).enabled);
   Intent dial=new Intent(Intent.ACTION_DIAL,Uri.parse("tel:12345")).setPackage(app.getPackageName());
   ResolveInfo resolved=app.getPackageManager().resolveActivity(dial,0);assertNotNull(resolved);
   assertEquals(MainActivity.class.getName(),resolved.activityInfo.name);
  }
 }
 @Test public void iconChangesPreserveLegacyExplicitMainIntentAndHeldDialerRole()throws Exception{
  RoleManager roles=app.getSystemService(RoleManager.class);Shadows.shadowOf(roles).addAvailableRole(RoleManager.ROLE_DIALER);
  Shadows.shadowOf(roles).addHeldRole(RoleManager.ROLE_DIALER);
  try{
   // Existing widget/notification/pinned intents keep their original component.
   Intent legacy=new Intent(app,MainActivity.class).setAction(Intent.ACTION_DIAL).setData(Uri.parse("tel:12345"));
   PendingIntent pending=PendingIntent.getActivity(app,7791,legacy,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
   for(String id:AppIcons.IDS){
    assertTrue(AppIcons.select(app,id));assertTrue(roles.isRoleHeld(RoleManager.ROLE_DIALER));
    assertEquals(MainActivity.class.getName(),app.getPackageManager().resolveActivity(legacy,0).activityInfo.name);
    assertEquals(new ComponentName(app,MainActivity.class),Shadows.shadowOf(pending).getSavedIntent().getComponent());
    assertTrue(app.getPackageManager().getActivityInfo(legacy.getComponent(),0).enabled);
   }
  }finally{Shadows.shadowOf(roles).removeHeldRole(RoleManager.ROLE_DIALER);Shadows.shadowOf(roles).removeAvailableRole(RoleManager.ROLE_DIALER);}
 }
 @Test public void invalidChoiceCannotMutatePreferencesOrComponentState(){
  prefs.edit().putString("favorites_fixture","preserved").putString(AppIcons.PREF,"orbit").commit();
  PackageManager manager=mock(PackageManager.class);Context context=withManager(manager);
  assertFalse(AppIcons.select(context,"../../../MainActivity"));assertFalse(AppIcons.apply(context,null));
  assertEquals("orbit",AppIcons.selected(context));assertEquals("preserved",prefs.getString("favorites_fixture",null));
  verifyNoInteractions(manager);
 }
 @Test public void android13SwitchIsAtomicAndNeverKillsTheApp(){
  PackageManager manager=mock(PackageManager.class);Context context=withManager(manager);
  assertTrue(AppIcons.apply(context,"wave"));
  @SuppressWarnings("unchecked") ArgumentCaptor<List<PackageManager.ComponentEnabledSetting>> changes=ArgumentCaptor.forClass(List.class);
  verify(manager).setComponentEnabledSettings(changes.capture());
  assertEquals(5,changes.getValue().size());int enabled=0;
  for(PackageManager.ComponentEnabledSetting setting:changes.getValue()){
   assertEquals(PackageManager.DONT_KILL_APP,setting.getEnabledFlags());
   if(setting.getEnabledState()==PackageManager.COMPONENT_ENABLED_STATE_ENABLED){enabled++;assertEquals(AppIcons.component(context,"wave"),setting.getComponentName());}
  }
  assertEquals(1,enabled);verify(manager,never()).setComponentEnabledSetting(any(),anyInt(),anyInt());
 }
 @Test @Config(sdk=26) public void oldAndroidEnablesReplacementBeforeDisablingPreviousEntry(){
  PackageManager manager=mock(PackageManager.class);Context context=withManager(manager);
  assertTrue(AppIcons.apply(context,"prism"));
  InOrder order=inOrder(manager);
  for(String id:AppIcons.IDS)order.verify(manager).getComponentEnabledSetting(AppIcons.component(context,id));
  order.verify(manager).setComponentEnabledSetting(AppIcons.component(context,"prism"),PackageManager.COMPONENT_ENABLED_STATE_ENABLED,PackageManager.DONT_KILL_APP);
  order.verify(manager).setComponentEnabledSetting(AppIcons.component(context,"crest"),PackageManager.COMPONENT_ENABLED_STATE_DISABLED,PackageManager.DONT_KILL_APP);
 }
 @Test @Config(sdk=26) public void rejectedSwitchRestoresOriginalComponentStatesWithoutSavingChoice(){
  PackageManager manager=mock(PackageManager.class);Context context=withManager(manager);AtomicBoolean failOnce=new AtomicBoolean(true);
  doAnswer(call->{
   ComponentName component=call.getArgument(0);int state=call.getArgument(1);
   if(component.equals(AppIcons.component(context,"crest"))&&state==PackageManager.COMPONENT_ENABLED_STATE_DISABLED&&failOnce.getAndSet(false))
    throw new SecurityException("OEM refused change");
   return null;
  }).when(manager).setComponentEnabledSetting(any(),anyInt(),anyInt());
  assertFalse(AppIcons.select(context,"orbit"));assertFalse(prefs.contains(AppIcons.PREF));
  for(String id:AppIcons.IDS)verify(manager).setComponentEnabledSetting(AppIcons.component(context,id),PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,PackageManager.DONT_KILL_APP);
 }
 @Test public void inabilityToReadSnapshotDoesNotAttemptAnyMutation(){
  PackageManager manager=mock(PackageManager.class);Context context=withManager(manager);
  when(manager.getComponentEnabledSetting(any())).thenThrow(new SecurityException("No component access"));
  assertFalse(AppIcons.apply(context,"wave"));
  verify(manager,never()).setComponentEnabledSetting(any(),anyInt(),anyInt());
  verify(manager,never()).setComponentEnabledSettings(anyList());assertFalse(prefs.contains(AppIcons.PREF));
 }
 @Test public void startupOnlyReconcilesWhenSettingsAndSystemDiffer(){
  PackageManager manager=mock(PackageManager.class);Context context=withManager(manager);
  assertTrue(AppIcons.ensure(context));verify(manager,never()).setComponentEnabledSettings(anyList());
  prefs.edit().putString(AppIcons.PREF,"prism").commit();assertTrue(AppIcons.ensure(context));
  verify(manager).setComponentEnabledSettings(anyList());
 }
 private Context withManager(PackageManager manager){return new ContextWrapper(app){@Override public PackageManager getPackageManager(){return manager;}};}
}
