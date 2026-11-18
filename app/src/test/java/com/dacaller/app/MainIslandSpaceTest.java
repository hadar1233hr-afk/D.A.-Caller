package com.dacaller.app;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.telecom.Call;
import android.telecom.CallAudioState;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

/** Real FastPages headers at the remaining viewport observed in the failed native Main capture. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=33)
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class MainIslandSpaceTest {
 public static class Host extends MainActivity {
  final List<Contacts.Person> people=new ArrayList<>();
  @Override public int checkSelfPermission(String permission){return PackageManager.PERMISSION_GRANTED;}
  @Override List<Contacts.Person> contactsSnapshot(){return people;}
 }
 private Host host;private FrameLayout workspace;private FastPages pages;private SharedPreferences prefs;
 private float oldFont;private List<CallHistory.Entry> oldCalls;private CallHistory.Summary oldSummary;private boolean oldReadFailed;
 @Before public void setup()throws Exception{
  oldFont=RuntimeEnvironment.getApplication().getResources().getConfiguration().fontScale;
  RuntimeEnvironment.setFontScale(1.3f);
  ActivityController<Host> controller=Robolectric.buildActivity(Host.class);host=controller.get();
  prefs=host.getSharedPreferences("da_caller",Context.MODE_PRIVATE);
  HashSet<String> favorites=new HashSet<>();
  for(int i=0;i<50;i++){Contacts.Person person=new Contacts.Person("איש לדוגמה "+i,"0500098"+String.format(java.util.Locale.ROOT,"%03d",i),80000+i);host.people.add(person);if(i<6)favorites.add(person.key);}
  prefs.edit().clear().putString("language","he").putBoolean("haptics",false).putBoolean("favorites_expanded",true)
   .putStringSet("favorites",favorites).putBoolean("stats_expanded",true).putBoolean("show_stats",true).commit();
  oldCalls=CallHistory.entries;oldSummary=CallHistory.summary;oldReadFailed=CallHistory.readFailed;
  ArrayList<CallHistory.Entry> entries=new ArrayList<>();for(int i=0;i<50;i++)entries.add(new CallHistory.Entry(90000+i,host.people.get(i).phone,1000000-i*60000,30,2));
  CallHistory.entries=entries;CallHistory.summary=new CallHistory.Summary();CallHistory.readFailed=false;
  pages=new FastPages(host);workspace=new FrameLayout(host);field("workspace",workspace);host.setContentView(workspace);controller.visible();
 }
 @After public void cleanup(){CallHistory.entries=oldCalls;CallHistory.summary=oldSummary;CallHistory.readFailed=oldReadFailed;RuntimeEnvironment.setFontScale(oldFont);}
 private void field(String key,Object value)throws Exception{Field field=MainActivity.class.getDeclaredField(key);field.setAccessible(true);field.set(host,value);}
 private int dp(int value){return Math.round(value*host.getResources().getDisplayMetrics().density);}
 private View attach(String page)throws Exception{
  field("page",page);View content="contacts".equals(page)?pages.contacts(host.people,false):pages.recents();
  workspace.removeAllViews();workspace.addView(content,new FrameLayout.LayoutParams(-1,-1));layout(480);return content;
 }
 private void layout(int height){
  Shadows.shadowOf(Looper.getMainLooper()).idle();
  workspace.measure(View.MeasureSpec.makeMeasureSpec(dp(320),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(dp(height),View.MeasureSpec.EXACTLY));
  workspace.layout(0,0,dp(320),dp(height));
 }
 private String dimensions(View content){
  ViewGroup parent=(ViewGroup)content;StringBuilder result=new StringBuilder("page=").append(content.getHeight()).append(" readFailed=").append(CallHistory.readFailed);
  for(int i=0;i<parent.getChildCount();i++){
   View child=parent.getChildAt(i);result.append(" [").append(child.getTag()).append(':').append(child.getClass().getSimpleName())
    .append(" h=").append(child.getHeight()).append(" measured=").append(child.getMeasuredHeight()).append(" visibility=").append(child.getVisibility()).append(']');
  }
  return result.toString();
 }
 @Test public void zeroHeightListsRecoverAcrossRepeatedLayoutsWithoutChangingPreferencesOrAnchors()throws Exception{
  for(String page:new String[]{"contacts","recents"}){
   View content=attach(page);ListView list=content.findViewWithTag("call-island-page-list");
   View favorites=content.findViewWithTag("favorites_strip"),secondary=content.findViewWithTag("call-island-secondary"),summary=content.findViewWithTag("stats_summary");
   list.setSelectionFromTop(4,-7);layout(480);long anchor=list.getAdapter().getItemId(list.getFirstVisiblePosition());int top=list.getChildAt(0).getTop();
   layout(120);assertEquals("Fixture must reproduce a clamped zero-height list",0,list.getHeight());
   for(int repeat=0;repeat<8;repeat++){
    host.adaptCallIslandContent(true);layout(120);
    assertTrue(page+" must leave one usable list row: "+dimensions(content),list.getHeight()>=dp(60));
    assertEquals(View.GONE,secondary.getVisibility());assertEquals(View.GONE,favorites.getVisibility());
   }
   assertTrue(prefs.getBoolean("favorites_expanded",false));assertTrue(prefs.getBoolean("stats_expanded",false));assertEquals(6,prefs.getStringSet("favorites",java.util.Collections.emptySet()).size());
   host.adaptCallIslandContent(false);layout(480);
   assertEquals(View.VISIBLE,favorites.getVisibility());assertEquals(View.VISIBLE,secondary.getVisibility());if(summary!=null)assertEquals(View.VISIBLE,summary.getVisibility());
   assertEquals(anchor,list.getAdapter().getItemId(list.getFirstVisiblePosition()));assertEquals(top,list.getChildAt(0).getTop());
  }
 }
 @Test public void replacingThePageRestoresDetachedCardsAndAdaptsOnlyTheNewPage()throws Exception{
  View first=attach("contacts");View oldFavorites=first.findViewWithTag("favorites_strip");
  layout(120);host.adaptCallIslandContent(true);layout(120);assertEquals(View.GONE,oldFavorites.getVisibility());
  View next=attach("recents");layout(120);host.adaptCallIslandContent(true);layout(120);
  assertEquals("The old page retains its original visibility",View.VISIBLE,oldFavorites.getVisibility());
  ListView list=next.findViewWithTag("call-island-page-list");assertTrue("New page must leave one usable list row: "+dimensions(next),list.getHeight()>=dp(60));
  host.adaptCallIslandContent(false);layout(480);assertEquals(View.VISIBLE,next.findViewWithTag("favorites_strip").getVisibility());assertTrue(prefs.getBoolean("favorites_expanded",false));
 }
 private void layoutWindow(int height){
  for(int repeat=0;repeat<3;repeat++){
   Shadows.shadowOf(Looper.getMainLooper()).idle();FrameLayout content=host.findViewById(android.R.id.content);
   content.measure(View.MeasureSpec.makeMeasureSpec(dp(320),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(dp(height),View.MeasureSpec.EXACTLY));
   content.layout(0,0,dp(320),dp(height));content.getViewTreeObserver().dispatchOnGlobalLayout();
  }
 }
 private LinearLayout attachMainChrome()throws Exception{
  LinearLayout chrome=new LinearLayout(host);chrome.setOrientation(LinearLayout.VERTICAL);
  FrameLayout toolbar=new FrameLayout(host);toolbar.setTag("main_settings_toolbar");chrome.addView(toolbar,new LinearLayout.LayoutParams(-1,dp(48)));
  if(workspace.getParent() instanceof ViewGroup)((ViewGroup)workspace.getParent()).removeView(workspace);
  chrome.addView(workspace,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout nav=new LinearLayout(host);nav.setTag("main_bottom_navigation");chrome.addView(nav,new LinearLayout.LayoutParams(-1,dp(64)));
  field("root",chrome);field("settingsToolbar",toolbar);field("nav",nav);host.setContentView(chrome);return chrome;
 }
 private CallIslandController readOnlyIsland(){
  CallIslandState state=new CallIslandState("synthetic-space-call","Synthetic caller","0500099971",Call.STATE_ACTIVE,CallAudioState.ROUTE_EARPIECE,
   CallAudioState.ROUTE_EARPIECE|CallAudioState.ROUTE_SPEAKER,false,true,true,System.currentTimeMillis()-12000,-1);
  return new CallIslandController(host,new CallIslandSession(){
   public CallIslandState current(){return state;}
   public boolean perform(String token,String action){throw new AssertionError("Layout cannot dispatch a call action");}
   public void open(android.app.Activity activity,String token){throw new AssertionError("Layout cannot open a call");}
  });
 }
 private void fullyVisible(View view,String name){
  android.graphics.Rect bounds=new android.graphics.Rect(),frame=new android.graphics.Rect();view.getWindowVisibleDisplayFrame(frame);
  boolean shown=view.isShown()&&view.getGlobalVisibleRect(bounds);int[] location=new int[2];view.getLocationOnScreen(location);
  String geometry=name+" @"+location[0]+","+location[1]+" size="+view.getWidth()+"x"+view.getHeight()+" visible="+bounds+" frame="+frame;
  assertEquals(geometry+" visibility",View.VISIBLE,view.getVisibility());assertTrue(geometry+" shown",shown);
  assertEquals(geometry+" full height",view.getHeight(),bounds.height());assertEquals(geometry+" full width",view.getWidth(),bounds.width());
 }
 // The actual app-content viewports below must fit inside the host decor;
 // Robolectric's default shorter display would clip this manually laid-out window.
 @Test @Config(qualifiers="w320dp-h700dp-mdpi") public void nativeSizedViewportKeepsFavoritesAndSummaryVisibleWhileExpandedControlsRemainReachable()throws Exception{
  prefs.edit().putBoolean("island_enabled",true).putBoolean("island_animations",false).commit();LinearLayout chrome=attachMainChrome();
  for(String page:new String[]{"contacts","recents"})for(boolean expandedSummary:new boolean[]{false,true}){
   prefs.edit().putBoolean("stats_expanded",expandedSummary).commit();View personal=attach(page);layoutWindow(569);
   ListView list=personal.findViewWithTag("call-island-page-list");list.setSelectionFromTop(4,-7);layoutWindow(569);
   long anchor=list.getAdapter().getItemId(list.getFirstVisiblePosition());int top=list.getChildAt(0).getTop();CallIslandController island=readOnlyIsland();
   try{
    island.start();layoutWindow(569);island.onExpand();
    for(int height:new int[]{569,520,497,569}){
     for(int repeat=0;repeat<4;repeat++){
      layoutWindow(height);assertTrue("Native-size personal list remains usable: "+dimensions(personal),list.getHeight()>=dp(60));
      fullyVisible(personal.findViewWithTag("favorites_strip"),page+" favorites");
      if("recents".equals(page))fullyVisible(personal.findViewWithTag("stats_summary"),"Recents summary");
      fullyVisible(chrome.findViewWithTag("main_settings_toolbar"),"Settings toolbar");fullyVisible(chrome.findViewWithTag("main_bottom_navigation"),"Bottom navigation");
     }
     View overflow=host.findViewById(android.R.id.content).findViewWithTag("island-in-app-window");assertTrue("Full controls scroll inside the bounded viewport",overflow instanceof ScrollView);
     fullyVisible(overflow,"Island viewport");View hide=overflow.findViewWithTag("island-hide");assertNotNull(hide);
     ((ScrollView)overflow).scrollTo(0,((ScrollView)overflow).getChildAt(0).getHeight());layoutWindow(height);
     fullyVisible(hide,"Hide control after scrolling");assertTrue(hide.getHeight()>=dp(48));
     ((ScrollView)overflow).scrollTo(0,0);layoutWindow(height);View collapse=overflow.findViewWithTag("island-collapse");
     fullyVisible(collapse,"Collapse control after returning to top");assertTrue(collapse.getHeight()>=dp(48));
    }
    island.onCollapse();layoutWindow(569);assertEquals(View.VISIBLE,personal.findViewWithTag("call-island-secondary").getVisibility());
    assertEquals(anchor,list.getAdapter().getItemId(list.getFirstVisiblePosition()));assertEquals(top,list.getChildAt(0).getTop());
    assertTrue(prefs.getBoolean("favorites_expanded",false));assertEquals(expandedSummary,prefs.getBoolean("stats_expanded",!expandedSummary));
   }finally{island.stop();layoutWindow(569);}
  }
 }
 @Test public void realControllerCapsPersonalPagesIncludingTheirVisibleReadErrorWithoutChangingTheListAnchor()throws Exception{
  prefs.edit().putBoolean("island_enabled",true).putBoolean("island_animations",false).commit();LinearLayout chrome=attachMainChrome();
  for(String page:new String[]{"contacts","recents"}){
   CallHistory.readFailed="recents".equals(page);View personal=attach(page);layoutWindow(760);
   ListView list=personal.findViewWithTag("call-island-page-list");list.setSelectionFromTop(4,-7);layoutWindow(760);
   long anchor=list.getAdapter().getItemId(list.getFirstVisiblePosition());int top=list.getChildAt(0).getTop();
   View error=CallHistory.readFailed?((ViewGroup)personal).getChildAt(((ViewGroup)personal).getChildCount()-1):null;
   if(error!=null)assertEquals("The fixture retains the real fixed read-error header",dp(64),error.getHeight());
   CallIslandController island=readOnlyIsland();
   try{
    island.start();layoutWindow(480);island.onExpand();
    for(int repeat=0;repeat<8;repeat++){
     layoutWindow(480);assertTrue("Controller reserves a usable personal list: "+dimensions(personal),list.getHeight()>=dp(60));
     assertEquals(dp(48),chrome.findViewWithTag("main_settings_toolbar").getHeight());assertEquals(dp(64),chrome.findViewWithTag("main_bottom_navigation").getHeight());
     if(error!=null){assertEquals(View.VISIBLE,error.getVisibility());assertEquals(dp(64),error.getHeight());}
    }
    island.onCollapse();layoutWindow(480);
    assertEquals(View.VISIBLE,personal.findViewWithTag("favorites_strip").getVisibility());assertEquals(View.VISIBLE,personal.findViewWithTag("call-island-secondary").getVisibility());
    assertTrue(prefs.getBoolean("favorites_expanded",false));assertTrue(prefs.getBoolean("stats_expanded",false));
   }finally{island.stop();layoutWindow(760);}
   assertEquals("List identity survives expansion and restoration",anchor,list.getAdapter().getItemId(list.getFirstVisiblePosition()));assertEquals(top,list.getChildAt(0).getTop());
  }
 }
 @Test public void businessHeadersKeepTheirListAndFullIslandControlsScrollOnlyUntilViewportOrPositionChanges()throws Exception{
  field("page","contacts");prefs.edit().putBoolean("island_enabled",true).putBoolean("island_animations",false).commit();
  LinearLayout chrome=attachMainChrome();View toolbar=chrome.findViewWithTag("main_settings_toolbar"),nav=chrome.findViewWithTag("main_bottom_navigation");
  BusinessDirectory.Manifest metadata=new BusinessDirectory.Manifest("synthetic",String.join("",java.util.Collections.nCopies(64,"0")),1,"Synthetic source","https://example.invalid/source","Synthetic license","https://example.invalid/license","2026-11-18");
  BusinessDirectory.Entry entry=new BusinessDirectory.Entry(1,"Synthetic business","0500099971","0500099971","Synthetic city","shop:books","Synthetic address","https://example.invalid/business/1","synthetic/1","Synthetic license","2026-11-18");
  BusinessDirectoryView directory=new BusinessDirectoryView(host,()->{},number->{throw new AssertionError("Layout cannot dial");},
   (context,query,offset)->new BusinessDirectory.Page(BusinessDirectory.Status.AVAILABLE,metadata,java.util.Collections.singletonList(entry),0,false),Runnable::run);
  View business=directory.create();workspace.removeAllViews();workspace.addView(business,new FrameLayout.LayoutParams(-1,-1));layoutWindow(480);
  ListView list=business.findViewWithTag("call-island-page-list");
  CallIslandController island=readOnlyIsland();
  try{
   island.start();layoutWindow(480);island.onExpand();layoutWindow(480);
   View overflow=host.findViewById(android.R.id.content).findViewWithTag("island-in-app-window");assertTrue(overflow instanceof ScrollView);
   assertTrue("Business list remains usable below all three fixed headers",list.getHeight()>=dp(60));
   assertEquals(View.VISIBLE,business.findViewWithTag("business_directory_search").getVisibility());assertEquals(View.VISIBLE,business.findViewWithTag("business_directory_status").getVisibility());
   assertEquals(dp(48),toolbar.getHeight());assertEquals(dp(64),nav.getHeight());
   View hide=overflow.findViewWithTag("island-hide");assertNotNull("Scroll container retains the full controls",hide);
   ((ScrollView)overflow).scrollTo(0,((ScrollView)overflow).getChildAt(0).getHeight());layoutWindow(480);
   android.graphics.Rect visible=new android.graphics.Rect();assertTrue("Bottom control can be scrolled into view",hide.getGlobalVisibleRect(visible));assertTrue(visible.height()>=dp(48));
   layoutWindow(760);assertNull("Larger viewport restores the ordinary full island",host.findViewById(android.R.id.content).findViewWithTag("island-in-app-window"));
   layoutWindow(480);assertNotNull(host.findViewById(android.R.id.content).findViewWithTag("island-in-app-window"));
   prefs.edit().putBoolean(CallIslandPosition.SET,true).putInt(CallIslandPosition.X,5000).putInt(CallIslandPosition.Y,5000).commit();layoutWindow(480);
   assertNull("Custom floating position keeps its existing behavior",host.findViewById(android.R.id.content).findViewWithTag("island-in-app-window"));
   assertEquals(0,((FrameLayout.LayoutParams)chrome.getLayoutParams()).topMargin);
   prefs.edit().putBoolean(CallIslandPosition.SET,false).commit();layoutWindow(480);assertNotNull(host.findViewById(android.R.id.content).findViewWithTag("island-in-app-window"));
   island.onCollapse();layoutWindow(480);assertNull(host.findViewById(android.R.id.content).findViewWithTag("island-in-app-window"));assertTrue(list.getHeight()>=dp(60));
  }finally{island.stop();directory.close();}
  layoutWindow(480);assertEquals(0,((FrameLayout.LayoutParams)chrome.getLayoutParams()).topMargin);assertTrue(prefs.getBoolean("favorites_expanded",false));assertTrue(prefs.getBoolean("stats_expanded",false));
 }

}
