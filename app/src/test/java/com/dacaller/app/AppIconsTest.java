package com.dacaller.app;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.app.PendingIntent;
import android.app.role.RoleManager;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.net.Uri;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Arrays;
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
 @Test public void allNineIconsHaveDistinctAdaptiveResourcesAndThemedForeground(){
  HashSet<Integer> resources=new HashSet<>();HashSet<String> labels=new HashSet<>();
  for(String id:AppIcons.IDS){
   assertTrue(AppIcons.valid(id));assertTrue(resources.add(AppIcons.iconResource(id)));
   assertTrue(labels.add(AppIcons.label(app,id)));
   assertTrue(AppIcons.preview(app,id) instanceof AdaptiveIconDrawable);
   AdaptiveIconDrawable icon=(AdaptiveIconDrawable)AppIcons.preview(app,id);
   assertNotNull(icon.getBackground());assertNotNull(icon.getForeground());assertNotNull(icon.getMonochrome());
  }
  assertEquals(9,resources.size());assertEquals("crest",AppIcons.selected(app));
 }
 @Test public void originalPreferenceIdsAliasesAndOrderRemainStable(){
  String[] oldIds={"crest","orbit","monogram","prism","wave"};
  String[] oldAliases={"LauncherCrest","LauncherOrbit","LauncherMonogram","LauncherPrism","LauncherWave"};
  assertArrayEquals(oldIds,Arrays.copyOf(AppIcons.IDS,oldIds.length));
  for(int i=0;i<oldIds.length;i++){
   prefs.edit().putString(AppIcons.PREF,oldIds[i]).commit();assertEquals(oldIds[i],AppIcons.selected(app));
   assertEquals(app.getPackageName()+"."+oldAliases[i],AppIcons.component(app,oldIds[i]).getClassName());
  }
  prefs.edit().putString(AppIcons.PREF,"future-choice").commit();
  assertEquals("crest",AppIcons.selected(app));assertEquals("future-choice",prefs.getString(AppIcons.PREF,null));
 }
 @Test public void bothLanguagesGiveEveryChoiceADistinctReadableLabel(){
  for(String language:new String[]{"he","en"}){
   prefs.edit().putString("language",language).commit();HashSet<String> labels=new HashSet<>();
   for(String id:AppIcons.IDS){String label=AppIcons.label(app,id);assertTrue(label.length()>3);assertTrue(labels.add(label));}
  }
 }
 @Test public void manifestKeepsOneDefaultLauncherAndEveryAliasTargetsOriginalDialActivity()throws Exception{
  PackageManager manager=app.getPackageManager();
  Intent launcher=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(app.getPackageName());
  List<ResolveInfo> enabled=manager.queryIntentActivities(launcher,0);assertEquals(1,enabled.size());
  assertEquals(AppIcons.component(app,"crest").getClassName(),enabled.get(0).activityInfo.name);
  for(String id:AppIcons.IDS){
   ActivityInfo alias=manager.getActivityInfo(AppIcons.component(app,id),PackageManager.MATCH_DISABLED_COMPONENTS);
   assertEquals(MainActivity.class.getName(),alias.targetActivity);assertTrue(alias.exported);
   assertEquals(AppIcons.iconResource(id),alias.icon);assertEquals(id.equals("crest"),alias.enabled);
  }
  assertTrue(manager.getActivityInfo(new ComponentName(app,MainActivity.class),0).enabled);
  Intent bareDial=new Intent(Intent.ACTION_DIAL).setPackage(app.getPackageName());
  assertEquals(MainActivity.class.getName(),manager.resolveActivity(bareDial,0).activityInfo.name);
 }
 @Test public void everyOldAndNewChoiceRoundTripsThroughSettingsBackupAndAliases()throws Exception{
  prefs.edit().putString("note_123","preserved private fixture").putBoolean("dark",true).commit();
  for(String id:AppIcons.IDS){
   assertTrue(AppIcons.select(app,id));
   SettingsBackup.Snapshot saved=SettingsBackup.decode(SettingsBackup.encode(SettingsBackup.capture(prefs)));
   assertEquals(id,saved.values.get(AppIcons.PREF));assertTrue(AppIcons.select(app,"crest"));
   assertTrue(SettingsBackup.restore(prefs,saved,choice->AppIcons.apply(app,choice)));
   assertEquals(id,AppIcons.selected(app));assertEquals("preserved private fixture",prefs.getString("note_123",null));
   assertTrue(prefs.getBoolean("dark",false));
   Intent launcher=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(app.getPackageName());
   List<ResolveInfo> enabled=app.getPackageManager().queryIntentActivities(launcher,0);
   assertEquals(1,enabled.size());assertEquals(AppIcons.component(app,id).getClassName(),enabled.get(0).activityInfo.name);
  }
 }
 @Test public void everyChoiceLeavesExactlyOneLauncherAndKeepsDialActivityEnabled()throws Exception{
  assertEveryChoice();
 }
 @Test @Config(sdk=26) public void everyChoiceAlsoWorksOnOldestSupportedAndroid()throws Exception{
  assertEveryChoice();
 }
 private void assertEveryChoice()throws Exception{
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
  assertEquals(9,changes.getValue().size());int enabled=0;HashSet<ComponentName> components=new HashSet<>();
  for(PackageManager.ComponentEnabledSetting setting:changes.getValue()){
   assertTrue(components.add(setting.getComponentName()));
   assertNotEquals(new ComponentName(context,MainActivity.class),setting.getComponentName());
   assertEquals(PackageManager.DONT_KILL_APP,setting.getEnabledFlags());
   if(setting.getEnabledState()==PackageManager.COMPONENT_ENABLED_STATE_ENABLED){enabled++;assertEquals(AppIcons.component(context,"wave"),setting.getComponentName());}
  }
  assertEquals(1,enabled);verify(manager,never()).setComponentEnabledSetting(any(),anyInt(),anyInt());
 }
 @Test public void android13RejectionRestoresTheExactSnapshotAtomically(){
  PackageManager manager=mock(PackageManager.class);Context context=withManager(manager);
  HashMap<ComponentName,Integer> states=new HashMap<>();
  for(String id:AppIcons.IDS)states.put(AppIcons.component(context,id),id.equals("wine")?PackageManager.COMPONENT_ENABLED_STATE_ENABLED:PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
  when(manager.getComponentEnabledSetting(any())).thenAnswer(call->states.get(call.getArgument(0)));
  AtomicBoolean first=new AtomicBoolean(true);
  doAnswer(call->{if(first.getAndSet(false))throw new SecurityException("OEM refused atomic change");return null;})
    .when(manager).setComponentEnabledSettings(anyList());
  prefs.edit().putString(AppIcons.PREF,"wine").putString("unrelated_fixture","preserved").commit();
  assertFalse(AppIcons.select(context,"signal"));assertEquals("wine",AppIcons.selected(context));
  assertEquals("preserved",prefs.getString("unrelated_fixture",null));
  @SuppressWarnings("unchecked") ArgumentCaptor<List<PackageManager.ComponentEnabledSetting>> changes=ArgumentCaptor.forClass(List.class);
  verify(manager,times(2)).setComponentEnabledSettings(changes.capture());
  List<PackageManager.ComponentEnabledSetting> restore=changes.getAllValues().get(1);assertEquals(9,restore.size());
  for(PackageManager.ComponentEnabledSetting setting:restore){
   assertEquals((int)states.get(setting.getComponentName()),setting.getEnabledState());
   assertEquals(PackageManager.DONT_KILL_APP,setting.getEnabledFlags());
  }
  verify(manager,never()).setComponentEnabledSetting(any(),anyInt(),anyInt());
 }
 @Test public void failedPreferenceCommitRestoresAliasesAndExactPreviousPreference(){
  prefs.edit().putString(AppIcons.PREF,"wine").putString("unrelated_fixture","preserved").commit();
  assertTrue(AppIcons.apply(app,"wine"));
  SharedPreferences failing=mock(SharedPreferences.class);AtomicBoolean first=new AtomicBoolean(true);
  when(failing.getString(anyString(),nullable(String.class))).thenAnswer(call->prefs.getString(call.getArgument(0),call.getArgument(1)));
  when(failing.contains(anyString())).thenAnswer(call->prefs.contains(call.getArgument(0)));
  when(failing.edit()).thenAnswer(call->{
   SharedPreferences.Editor real=prefs.edit(),editor=mock(SharedPreferences.Editor.class);
   when(editor.putString(anyString(),nullable(String.class))).thenAnswer(edit->{real.putString(edit.getArgument(0),edit.getArgument(1));return editor;});
   when(editor.remove(anyString())).thenAnswer(edit->{real.remove(edit.getArgument(0));return editor;});
   when(editor.commit()).thenAnswer(edit->{real.commit();return !first.getAndSet(false);});return editor;
  });
  Context context=new ContextWrapper(app){@Override public SharedPreferences getSharedPreferences(String name,int mode){return failing;}};
  assertFalse(AppIcons.select(context,"facet"));assertEquals("wine",prefs.getString(AppIcons.PREF,null));
  assertEquals("preserved",prefs.getString("unrelated_fixture",null));
  Intent launcher=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(app.getPackageName());
  List<ResolveInfo> enabled=app.getPackageManager().queryIntentActivities(launcher,0);
  assertEquals(1,enabled.size());assertEquals(AppIcons.component(app,"wine").getClassName(),enabled.get(0).activityInfo.name);
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
