package com.dacaller.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsetsController;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/** Offline version history. Expanding a note never changes app or call preferences. */
public final class ChangelogActivity extends Activity {
 private final Set<String> expanded=new HashSet<>();
 private SharedPreferences prefs;
 private ScrollView scroll;
 private boolean english,dark;
 private int background,surface,ink,secondary,accent;
 private int dp(float value){return Math.round(getResources().getDisplayMetrics().density*value);}
 private String tr(String he,String en){return english?en:he;}
 private LinearLayout column(){LinearLayout view=new LinearLayout(this);view.setOrientation(LinearLayout.VERTICAL);return view;}
 private TextView text(String value,int sp,boolean strong,int color){
  TextView view=new TextView(this);view.setText(value);view.setTextSize(sp);view.setTextColor(color);
  view.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);view.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
  view.setTypeface(Typeface.create(strong?"sans-serif-medium":"sans-serif",Typeface.NORMAL));
  view.setLineSpacing(dp(2),1f);return view;
 }
 private GradientDrawable shape(int color,int radius,boolean highlighted){
  GradientDrawable view=new GradientDrawable();view.setColor(color);view.setCornerRadius(dp(radius));
  if(highlighted)view.setStroke(dp(1),accent);return view;
 }
 @Override public void onCreate(Bundle state){
  super.onCreate(state);prefs=getSharedPreferences("da_caller",Context.MODE_PRIVATE);
  english=I18n.isEnglish(this);dark=prefs.getBoolean("dark",false)||prefs.getBoolean("amoled",false);
  background=prefs.getBoolean("amoled",false)?Color.BLACK:dark?0xFF141414:0xFFF7F7F5;
  surface=dark?0xFF232323:Color.WHITE;ink=dark?0xFFF5F5F3:0xFF161C17;
  secondary=dark?0xFFB8BEB9:0xFF59635B;accent=dark?0xFFE1C38A:0xFF805B27;
  if(state==null)expanded.add(BuildConfig.VERSION_NAME);
  else{ArrayList<String> saved=state.getStringArrayList("expanded_versions");if(saved!=null)expanded.addAll(saved);}
  scroll=new ScrollView(this);scroll.setTag("changelog-scroll");scroll.setFillViewport(true);scroll.setBackgroundColor(background);
  LinearLayout root=column();root.setTag("changelog-root");root.setLayoutDirection(english?View.LAYOUT_DIRECTION_LTR:View.LAYOUT_DIRECTION_RTL);
  root.setPadding(dp(15),dp(10),dp(15),dp(24));scroll.addView(root,new ScrollView.LayoutParams(-1,-2));setContentView(scroll);
  LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);root.addView(header,new LinearLayout.LayoutParams(-1,-2));
  TextView back=text(english?"‹":"›",28,false,accent);back.setGravity(Gravity.CENTER);back.setContentDescription(tr("חזרה","Back"));
  back.setTag("changelog-back");back.setFocusable(true);header.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
  back.setOnClickListener(v->finish());
  TextView title=text(tr("יומן שינויים","What's new"),23,true,ink);title.setTag("changelog-title");
  header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
  String version=android.text.BidiFormatter.getInstance(!english).unicodeWrap(BuildConfig.VERSION_NAME,android.text.TextDirectionHeuristics.LTR);
  TextView installed=text(tr("הגרסה המותקנת: ","Installed version: ")+version,13,true,accent);
  installed.setTag("changelog-installed-version");installed.setPadding(dp(4),dp(10),dp(4),dp(4));root.addView(installed,new LinearLayout.LayoutParams(-1,-2));
  TextView introduction=text(tr("מה התחדש בכל גרסה, מהחדש לישן. הקש על גרסה כדי לראות את הפירוט.",
    "Updates from newest to oldest. Tap a version to see its details."),13,false,secondary);
  introduction.setPadding(dp(4),dp(4),dp(4),dp(14));root.addView(introduction,new LinearLayout.LayoutParams(-1,-2));
  boolean installedListed=false;
  for(ChangelogCatalog.Entry entry:ChangelogCatalog.entries()){
   installedListed|=entry.version.equals(BuildConfig.VERSION_NAME);addEntry(root,entry);
  }
  if(!installedListed){
   TextView pending=text(tr("פירוט השינויים לגרסה המותקנת עדיין לא נוסף לרשימה.",
     "Notes for the installed version have not been added yet."),13,false,secondary);
   pending.setPadding(dp(4),dp(12),dp(4),0);root.addView(pending,new LinearLayout.LayoutParams(-1,-2));
  }
  TextView end=text(tr("כל יומן השינויים שמור באפליקציה וזמין גם ללא אינטרנט.",
    "This version history is stored in the app and is available offline."),12,false,secondary);
  end.setTag("changelog-history-end");end.setPadding(dp(4),dp(18),dp(4),dp(4));root.addView(end,new LinearLayout.LayoutParams(-1,-2));
  applySystemBars();
  if(state!=null){int y=Math.max(0,state.getInt("scroll_y",0));scroll.post(()->scroll.scrollTo(0,y));}
 }
 private void addEntry(LinearLayout root,ChangelogCatalog.Entry entry){
  boolean current=entry.version.equals(BuildConfig.VERSION_NAME);
  LinearLayout card=column();card.setTag("changelog-version-"+entry.version);
  card.setBackground(shape(surface,20,current));
  LinearLayout.LayoutParams cardParams=new LinearLayout.LayoutParams(-1,-2);cardParams.topMargin=dp(10);root.addView(card,cardParams);
  LinearLayout header=new LinearLayout(this);header.setTag("changelog-toggle-"+entry.version);header.setGravity(Gravity.CENTER_VERTICAL);
  header.setPadding(dp(14),dp(12),dp(9),dp(12));header.setMinimumHeight(dp(72));header.setFocusable(true);header.setClickable(true);
  card.addView(header,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout labels=column();header.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
  TextView version=text(entry.version,17,true,ink);version.setTextDirection(View.TEXT_DIRECTION_LTR);
  version.setTag("changelog-version-label-"+entry.version);labels.addView(version,new LinearLayout.LayoutParams(-1,-2));
  TextView summary=text(entry.title(english),13,false,secondary);summary.setPadding(0,dp(3),0,0);labels.addView(summary,new LinearLayout.LayoutParams(-1,-2));
  TextView badge=text(current?(entry.preview()?tr("גרסת הבדיקה המותקנת","Installed test build"):tr("הגרסה המותקנת","Installed version")):
    entry.preview()?tr("גרסת בדיקה קודמת","Earlier test build"):tr("גרסה קודמת","Earlier version"),11,current,accent);
  badge.setTag("changelog-status-"+entry.version);badge.setPadding(0,dp(5),0,0);labels.addView(badge,new LinearLayout.LayoutParams(-1,-2));
  TextView chevron=text("",23,false,accent);chevron.setGravity(Gravity.CENTER);
  chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);header.addView(chevron,new LinearLayout.LayoutParams(dp(38),dp(48)));
  LinearLayout details=column();details.setTag("changelog-details-"+entry.version);details.setPadding(dp(14),0,dp(14),dp(14));
  card.addView(details,new LinearLayout.LayoutParams(-1,-2));
  for(ChangelogCatalog.Change change:entry.changes){
   LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.TOP);details.addView(row,new LinearLayout.LayoutParams(-1,-2));
   TextView bullet=text("•",15,true,accent);bullet.setGravity(Gravity.CENTER);bullet.setPadding(0,dp(7),0,0);
   bullet.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(bullet,new LinearLayout.LayoutParams(dp(19),-2));
   TextView copy=text(change.text(english),14,false,ink);copy.setPadding(dp(2),dp(7),dp(2),0);row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
  }
  Runnable bind=()->{
   boolean open=expanded.contains(entry.version);details.setVisibility(open?View.VISIBLE:View.GONE);chevron.setText(open?"⌃":"⌄");
   header.setContentDescription(entry.version+". "+entry.title(english)+". "+tr(open?"הסתר פירוט":"הצג פירוט",open?"Hide details":"Show details"));
   if(Build.VERSION.SDK_INT>=30)header.setStateDescription(tr(open?"מורחב":"מכווץ",open?"Expanded":"Collapsed"));
  };
  header.setOnClickListener(v->{TouchFeedback.pulse(this,prefs,header);
   if(!expanded.remove(entry.version))expanded.add(entry.version);bind.run();});bind.run();
 }
 private void applySystemBars(){
  getWindow().setStatusBarColor(background);getWindow().setNavigationBarColor(background);
  if(Build.VERSION.SDK_INT>=30){WindowInsetsController bars=getWindow().getInsetsController();if(bars!=null){
   int mask=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
   bars.setSystemBarsAppearance(dark?0:mask,mask);
  }}else getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
 }
 @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused&&prefs!=null)applySystemBars();}
 @Override protected void onResume(){super.onResume();if(prefs!=null)getWindow().getDecorView().post(this::applySystemBars);}
 @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);
  state.putStringArrayList("expanded_versions",new ArrayList<>(expanded));if(scroll!=null)state.putInt("scroll_y",scroll.getScrollY());
 }
}
