package com.dacaller.app;

import static org.junit.Assert.*;
import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.RemoteViews;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=33)
public final class WidgetSupportTest {
 private Application context;
 @Before public void setup(){
  context=RuntimeEnvironment.getApplication();WidgetSupport.prefs(context).edit().clear().commit();
  Shadows.shadowOf(context).denyPermissions(Manifest.permission.READ_CONTACTS);
 }
 private View apply(RemoteViews remote){return remote.apply(context,new FrameLayout(context));}
 @Test public void quickAndContactCardPreserveConfiguredIdentityAndHaveDifferentLayouts(){
  WidgetSupport.prefs(context).edit().putLong("widget_contact_42",99).putString("widget_name_42","דנה לדוגמה").putString("widget_phone_42","0501234567").commit();
  View quick=apply(WidgetSupport.renderSingle(context,42,true));
  View card=apply(WidgetSupport.renderSingle(context,42,false));
  assertNull(quick.findViewById(R.id.widget_subtitle));assertNotNull(card.findViewById(R.id.widget_subtitle));
  assertTrue(((TextView)card.findViewById(R.id.widget_subtitle)).getText().toString().contains("0501234567"));
  assertEquals("דנה לדוגמה",((TextView)quick.findViewById(R.id.widget_name)).getText().toString());
  assertTrue(quick.performClick());Intent quickTap=Shadows.shadowOf(context).getNextStartedActivity();
  assertEquals(WidgetTap.class.getName(),quickTap.getComponent().getClassName());
  assertTrue(quickTap.getBooleanExtra("call",false));assertEquals("0501234567",quickTap.getStringExtra("phone"));assertEquals(99,quickTap.getLongExtra("contact_id",-1));
  assertTrue(card.performClick());Intent cardTap=Shadows.shadowOf(context).getNextStartedActivity();
  assertFalse(cardTap.getBooleanExtra("call",true));assertEquals(99,cardTap.getLongExtra("contact_id",-1));
 }
 @Test public void eachFavoriteSlotCarriesItsExactNumberAndContactId(){
  List<Contacts.Person> people=people(5);Bundle wide=new Bundle();wide.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,304);
  View view=apply(WidgetSupport.renderFavorites(context,7,wide,people,true));
  int[] slots={R.id.fav1,R.id.fav2,R.id.fav3,R.id.fav4,R.id.fav5};
  for(int i=0;i<5;i++){
   assertEquals(View.VISIBLE,view.findViewById(slots[i]).getVisibility());
   assertTrue(view.findViewById(slots[i]).performClick());Intent tap=Shadows.shadowOf(context).getNextStartedActivity();
   assertEquals(people.get(i).phone,tap.getStringExtra("phone"));assertEquals(people.get(i).id,tap.getLongExtra("contact_id",-1));
   assertTrue(tap.getBooleanExtra("call",false));
  }
 }
 @Test public void resizeExposesFourFavoritesAtTwoCellsAndFiveAtFourCells(){
  Bundle small=new Bundle();small.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,148);
  Bundle wide=new Bundle();wide.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,304);
  assertEquals(4,WidgetSupport.favoriteCapacity(small));assertEquals(5,WidgetSupport.favoriteCapacity(wide));
  View narrow=apply(WidgetSupport.renderFavorites(context,7,small,people(5),true));
  assertNotNull(narrow.findViewById(R.id.fav4));assertNull(narrow.findViewById(R.id.fav5));
  View wideView=apply(WidgetSupport.renderFavorites(context,7,wide,people(5),true));assertNotNull(wideView.findViewById(R.id.fav5));
 }
 @Test public void fewerFavoritesCannotLeaveAStaleCallableCell(){
  View view=apply(WidgetSupport.renderFavorites(context,7,new Bundle(),people(2),true));
  assertEquals(View.VISIBLE,view.findViewById(R.id.fav2).getVisibility());
  assertEquals(View.INVISIBLE,view.findViewById(R.id.fav3).getVisibility());
  assertEquals(View.INVISIBLE,view.findViewById(R.id.fav4).getVisibility());
 }
 @Test public void noPermissionOrNoFavoritesProvidesAnExplicitSafeOpenAction(){
  View missing=apply(WidgetSupport.renderFavorites(context,7,new Bundle(),people(5),false));
  assertEquals(View.VISIBLE,missing.findViewById(R.id.widget_empty).getVisibility());
  assertEquals(View.GONE,missing.findViewById(R.id.widget_favorites_grid).getVisibility());
  assertTrue(((TextView)missing.findViewById(R.id.widget_empty)).getText().toString().contains("הרשאת"));
  assertTrue(missing.findViewById(R.id.widget_empty).performClick());
  Intent open=Shadows.shadowOf(context).getNextStartedActivity();assertEquals(MainActivity.class.getName(),open.getComponent().getClassName());assertTrue(open.getBooleanExtra("favorites",false));
  View empty=apply(WidgetSupport.renderFavorites(context,7,new Bundle(),people(0),true));
  assertEquals(View.VISIBLE,empty.findViewById(R.id.widget_empty).getVisibility());
 }
 @Test public void pendingIntentIdentityIncludesWidgetSlotContactAndAction(){
  PendingIntent first=WidgetSupport.target(context,7,0,101,"א","0501000000",true);
  PendingIntent sameNumberOtherSlot=WidgetSupport.target(context,7,1,101,"א","0501000000",true);
  PendingIntent otherWidget=WidgetSupport.target(context,8,0,101,"א","0501000000",true);
  PendingIntent otherContact=WidgetSupport.target(context,7,0,102,"ב","0501000000",true);
  PendingIntent card=WidgetSupport.target(context,7,0,101,"א","0501000000",false);
  assertNotEquals(first,sameNumberOtherSlot);assertNotEquals(first,otherWidget);assertNotEquals(first,otherContact);assertNotEquals(first,card);
  PendingIntent changed=WidgetSupport.target(context,7,0,101,"שם חדש","0509999999",true);
  assertNotEquals(first,changed);assertEquals("0501000000",Shadows.shadowOf(first).getSavedIntent().getStringExtra("phone"));
 }
 @Test public void unconfiguredWidgetReopensItsOwnConfiguration(){
  View quick=apply(WidgetSupport.renderSingle(context,63,true));assertTrue(quick.performClick());
  Intent intent=Shadows.shadowOf(context).getNextStartedActivity();assertEquals(WidgetConfigure.class.getName(),intent.getComponent().getClassName());assertEquals(63,intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1));
 }
 @Test public void deletionOnlyRemovesTheDeletedWidgetsConfiguration(){
  WidgetSupport.prefs(context).edit().putString("widget_phone_1","111").putString("widget_phone_2","222").putString("language","en").putStringSet("favorites",new HashSet<>(Arrays.asList("111","222"))).commit();
  WidgetSupport.delete(context,new int[]{1});
  assertFalse(WidgetSupport.prefs(context).contains("widget_phone_1"));assertEquals("222",WidgetSupport.prefs(context).getString("widget_phone_2",""));assertEquals(2,WidgetSupport.prefs(context).getStringSet("favorites",new HashSet<>()).size());assertEquals("en",WidgetSupport.prefs(context).getString("language",""));
 }
 @Test public void initialsSupportHebrewAndSurrogatePairsWithoutContactsPermission(){
  assertEquals("דל",WidgetSupport.initials("דנה לדוגמה"));assertEquals("?",WidgetSupport.initials(""));
  Bitmap avatar=WidgetSupport.avatar(context,99,"דנה לדוגמה",96);
  assertEquals("😀ל",WidgetSupport.initials("😀 לדוגמה"));
  assertEquals(96,avatar.getWidth());assertEquals(96,avatar.getHeight());
  // Pixel geometry is asserted by WidgetSnapshotTest on Android; Robolectric legacy Canvas does not rasterize.
 }
 @Test public void permissionDenialNeverDispatchesACallFromWidgetTap(){
  Shadows.shadowOf(context.getSystemService(android.telecom.TelecomManager.class)).setDefaultDialerPackage(context.getPackageName());
  Intent intent=new Intent(context,WidgetTap.class).putExtra("call",true).putExtra("phone","0501234567");
  Activity tap=Robolectric.buildActivity(WidgetTap.class,intent).create().get();
  Intent request=Shadows.shadowOf(tap).getNextStartedActivity();
  assertEquals("android.content.pm.action.REQUEST_PERMISSIONS",request.getAction());
  tap.onRequestPermissionsResult(12,new String[]{Manifest.permission.CALL_PHONE},new int[]{android.content.pm.PackageManager.PERMISSION_DENIED});
  assertTrue(tap.isFinishing());assertNull(Shadows.shadowOf(tap).getNextStartedActivity());
 }
 private static List<Contacts.Person> people(int size){ArrayList<Contacts.Person> p=new ArrayList<>();for(int i=0;i<size;i++)p.add(new Contacts.Person("דוגמה "+i,"050100000"+i,100+i));return p;}
}
