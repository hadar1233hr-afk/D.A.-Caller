package com.dacaller.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import android.provider.Settings;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.TelecomManager;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsetsController;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Call island customization, optional system grant and clearly labelled local demonstration. */
public final class CallIslandSettingsActivity extends Activity {
 private SharedPreferences prefs;
 private boolean dark,expanded,muted,speaker,ended;
 private int phase=Call.STATE_ACTIVE;
 private long connectedAt=System.currentTimeMillis()-11_000;
 private int background,surface,ink,secondary,accent;
 private CallIslandView preview;
 private TextView phaseChoice,styleChoice,sizeChoice,previewToggle,previewEnd;
 private TextView outsidePermission,phoneRole;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final Runnable clock=new Runnable(){@Override public void run(){
  if(preview!=null&&!ended)preview.tick(System.currentTimeMillis());handler.postDelayed(this,1000);
 }};
 private String tr(String he,String en){return I18n.isEnglish(this)?en:he;}
 private int dp(float n){return Math.round(getResources().getDisplayMetrics().density*n);}
 private GradientDrawable shape(int color,int radius){GradientDrawable drawable=new GradientDrawable();drawable.setColor(color);drawable.setCornerRadius(dp(radius));return drawable;}
 private TextView text(String value,int sp,boolean strong,int color){
  TextView view=new TextView(this);view.setText(value);view.setTextSize(sp);view.setTextColor(color);view.setGravity(Gravity.CENTER_VERTICAL);
  view.setTypeface(Typeface.create(strong?"sans-serif-medium":"sans-serif",Typeface.NORMAL));return view;
 }
 private LinearLayout column(){LinearLayout result=new LinearLayout(this);result.setOrientation(LinearLayout.VERTICAL);return result;}
 @Override public void onCreate(Bundle state){
  super.onCreate(state);prefs=getSharedPreferences("da_caller",Context.MODE_PRIVATE);
  dark=prefs.getBoolean("dark",false)||prefs.getBoolean("amoled",false);
  background=prefs.getBoolean("amoled",false)?Color.BLACK:dark?0xFF121413:0xFFF5F6F3;
  surface=dark?0xFF202521:Color.WHITE;ink=dark?0xFFF5F7F4:0xFF17231B;
  secondary=dark?0xFFB8C4BA:0xFF596B5D;accent=dark?0xFF9CE9AE:0xFF236B3B;
  if(state!=null){expanded=state.getBoolean("expanded");muted=state.getBoolean("muted");speaker=state.getBoolean("speaker");
   ended=state.getBoolean("ended");phase=state.getInt("phase",Call.STATE_ACTIVE);connectedAt=state.getLong("connected_at",connectedAt);}
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(background);scroll.setTag("island-settings-scroll");
  LinearLayout root=column();root.setPadding(dp(16),dp(12),dp(16),dp(28));root.setLayoutDirection(I18n.isEnglish(this)?View.LAYOUT_DIRECTION_LTR:View.LAYOUT_DIRECTION_RTL);
  scroll.addView(root,new ScrollView.LayoutParams(-1,-2));setContentView(scroll);
  LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);root.addView(header,new LinearLayout.LayoutParams(-1,-2));
  TextView back=text(I18n.isEnglish(this)?"‹":"›",32,false,accent);back.setGravity(Gravity.CENTER);back.setContentDescription(tr("חזרה","Back"));
  header.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));back.setOnClickListener(v->finish());
  TextView title=text(tr("אי שיחה דינמי","Dynamic call island"),23,true,ink);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
  TextView introduction=text(tr("השיחה נשארת בהישג יד באפליקציה, וגם במסך הבית ובאפליקציות אחרות אם בוחרים בכך.","Keep your call within reach in the app, and optionally on Home and above other apps."),14,false,secondary);
  introduction.setPadding(0,dp(4),0,dp(16));root.addView(introduction,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout outside=group(root,tr("הפעלת האי","Enable the island"));
  toggle(outside,tr("הצגת האי בזמן שיחה","Show island during calls"),
    tr("מופיע במסכי האפליקציה לאחר חזרה ממסך השיחה. אפשר להוסיף גם הצגה מחוץ לאפליקציה.","Appears in the app after returning from the full call screen. Outside-app display can also be enabled below."),"island_enabled",true);
  Switch outsideSwitch=toggle(outside,tr("הצגה במסך הבית ובאפליקציות אחרות","Show on Home and above other apps"),
    tr("בזמן חיוג, שיחה פעילה והמתנה. שיחה נכנסת מוצגת בהתראת Android.","Shown during dialing, active and held calls. Incoming calls stay in Android's call notification."),"island_outside_app",false);
  outsideSwitch.setOnCheckedChangeListener((button,value)->{
   prefs.edit().putBoolean("island_outside_app",value).apply();updateIntegrationStatus();
   if(value&&!canDrawOutside())openOutsidePermission();
  });
  outsidePermission=choice(outside,"","island_overlay_permission",this::openOutsidePermission);
  phoneRole=choice(outside,"","island_phone_role",this::requestPhoneRole);
  TextView systemLimit=text(tr("במסכי מערכת מסוימים Android מסתיר חלונות צפים. אפשר להמשיך לשלוט בשיחה דרך ההתראה.",
    "Android hides floating windows on some system screens. The call notification remains available."),12,false,secondary);
  systemLimit.setPadding(dp(14),dp(6),dp(14),dp(14));outside.addView(systemLimit,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout demo=group(root,tr("תצוגה מקדימה","Live preview"));
  TextView demoNote=text(tr("הדגמה בלבד · לא מתבצעת שיחה","Demo only · no call is placed"),12,true,accent);
  demoNote.setTag("island-preview-demo-label");demoNote.setPadding(dp(12),dp(10),dp(12),dp(8));demo.addView(demoNote,new LinearLayout.LayoutParams(-1,-2));
  preview=new CallIslandView(this);preview.setTag("island-settings-preview");
  LinearLayout.LayoutParams previewParams=new LinearLayout.LayoutParams(-1,-2);previewParams.setMargins(dp(4),dp(4),dp(4),dp(8));demo.addView(preview,previewParams);
  previewEnd=text(tr("הדגמת השיחה הסתיימה","Demo call ended"),15,true,secondary);previewEnd.setGravity(Gravity.CENTER);
  previewEnd.setPadding(dp(12),dp(20),dp(12),dp(20));demo.addView(previewEnd,new LinearLayout.LayoutParams(-1,-2));
  preview.setListener(new CallIslandView.Listener(){
   public void onExpand(){expanded=true;renderPreview();}
   public void onCollapse(){expanded=false;renderPreview();}
   public void onAction(String action){demoAction(action);}
   public void onOpen(){Toast.makeText(CallIslandSettingsActivity.this,tr("בהדגמה זו אין שיחה אמיתית. בזמן שיחה הכפתור פותח את מסך השיחה.","This is a demonstration. During a call, this opens the full call screen."),Toast.LENGTH_LONG).show();}
  });
  phaseChoice=choice(demo,tr("מצב ההדגמה","Preview state"),"island_preview_state",()->choosePhase());
  previewToggle=choice(demo,"","island_preview_expand",()->{ended=false;expanded=!expanded;renderPreview();});
  LinearLayout visibility=group(root,tr("מתי ואיך להציג","Presentation"));
  styleChoice=choice(visibility,tr("סגנון האי","Island style"),"island_style",()->chooseStyle());
  sizeChoice=choice(visibility,tr("גודל האי","Island size"),"island_size",()->chooseSize());
  toggle(visibility,tr("משך השיחה","Call duration"),tr("הצגת הטיימר בשיחה פעילה.","Show the timer during an active call."),"island_show_timer",true);
  toggle(visibility,tr("תמונת איש הקשר","Contact photo"),tr("תמונה או ראשי תיבות ליד פרטי השיחה.","Show a photo or initials beside the call details."),"island_show_photo",true);
  toggle(visibility,tr("אנימציה עדינה","Gentle animation"),tr("מעברים וחיווי תנועה עדין. מכבד את הגדרות האנימציה במכשיר.","Subtle transitions and activity animation, respecting the device animation setting."),"island_animations",true);
  LinearLayout controls=group(root,tr("פקדים מהירים","Quick controls"));
  toggle(controls,tr("כפתור השתקה","Mute control"),tr("הפעלה וכיבוי של המיקרופון באי המורחב.","Control the microphone from the expanded island."),"island_show_mute",true);
  toggle(controls,tr("כפתור רמקול","Speaker control"),tr("מעבר לרמקול וחזרה לפלט השמע הזמין.","Switch to speaker and back to an available private audio route."),"island_show_speaker",true);
  toggle(controls,tr("כפתור המתנה","Hold control"),tr("מוצג כשאפשר להשהות את השיחה או להמשיך אותה.","Shown when the call supports hold or resume."),"island_show_hold",true);
  toggle(controls,tr("אישור לפני סיום שיחה","Confirm before ending a call"),tr("אישור נוסף לסיום או לדחיית שיחה מתוך האי.","Ask before ending or declining a call from the island."),"island_confirm_end",true);
  TextView tip=text(tr("נגיעה באי פותחת את הפקדים. לחיצה ממושכת מחזירה למסך השיחה.","Tap the island for controls. Touch and hold to return to the call screen."),13,false,secondary);
  tip.setPadding(dp(4),dp(18),dp(4),0);root.addView(tip,new LinearLayout.LayoutParams(-1,-2));
  applySystemBars();renderPreview();updateIntegrationStatus();
 }
 private LinearLayout group(LinearLayout root,String label){
  TextView heading=text(label,16,true,ink);heading.setPadding(dp(3),dp(18),dp(3),dp(9));root.addView(heading,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout group=column();group.setBackground(shape(surface,22));root.addView(group,new LinearLayout.LayoutParams(-1,-2));return group;
 }
 private TextView choice(LinearLayout parent,String label,String tag,Runnable action){
  TextView view=text(label,15,true,ink);view.setTag(tag);view.setPadding(dp(14),dp(12),dp(14),dp(12));view.setMinimumHeight(dp(52));
  view.setBackground(shape(surface,18));view.setFocusable(true);view.setClickable(true);
  parent.addView(view,new LinearLayout.LayoutParams(-1,-2));view.setOnClickListener(v->{TouchFeedback.pulse(this,prefs,v);action.run();});return view;
 }
 private Switch toggle(LinearLayout parent,String label,String description,String key,boolean fallback){
  LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(14),dp(10),dp(12),dp(10));row.setMinimumHeight(dp(68));
  parent.addView(row,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout words=column();TextView name=text(label,15,true,ink),detail=text(description,12,false,secondary);detail.setPadding(0,dp(4),0,0);
  words.addView(name,new LinearLayout.LayoutParams(-1,-2));words.addView(detail,new LinearLayout.LayoutParams(-1,-2));
  row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
  Switch control=new Switch(this);control.setTag(key);control.setContentDescription(label);control.setChecked(prefs.getBoolean(key,fallback));control.setMinimumWidth(dp(52));control.setMinHeight(dp(48));
  control.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{accent,dark?0xFF9EAAA1:0xFFB3BDB5}));
  control.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{dark?0xFF3C6546:0xFFA8CEB0,dark?0xFF384039:0xFFDCE3DE}));
  LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,-2);params.setMarginStart(dp(12));row.addView(control,params);
  control.setOnCheckedChangeListener((button,value)->{prefs.edit().putBoolean(key,value).apply();renderPreview();});
  row.setOnClickListener(v->control.setChecked(!control.isChecked()));
  return control;
 }
 private boolean canDrawOutside(){try{return Settings.canDrawOverlays(this);}catch(RuntimeException unavailable){return false;}}
 private boolean holdsPhoneRole(){
  try{
   if(Build.VERSION.SDK_INT>=29){RoleManager roles=getSystemService(RoleManager.class);return roles!=null&&roles.isRoleHeld(RoleManager.ROLE_DIALER);}
   TelecomManager telecom=getSystemService(TelecomManager.class);return telecom!=null&&getPackageName().equals(telecom.getDefaultDialerPackage());
  }catch(RuntimeException unavailable){return false;}
 }
 private void updateIntegrationStatus(){
  if(outsidePermission==null||phoneRole==null)return;
  outsidePermission.setText(canDrawOutside()?tr("הרשאת הצגה מעל אפליקציות: מאושרת","Display-over-other-apps access: allowed"):
    tr("הרשאת הצגה מעל אפליקציות: נדרשת · פתיחת הגדרות","Display-over-other-apps access: needed · open settings"));
  phoneRole.setText(holdsPhoneRole()?tr("DA CALLER הוא חייגן ברירת המחדל","DA CALLER is the default phone app"):
    tr("נדרש חייגן ברירת מחדל · הגדרת DA CALLER","Default phone app needed · choose DA CALLER"));
 }
 private void openOutsidePermission(){
  try{startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));}
  catch(RuntimeException unavailable){
   Toast.makeText(this,tr("פתח בהגדרות המכשיר: גישה מיוחדת לאפליקציות ← הצגה מעל אפליקציות אחרות ← DA CALLER.",
     "In device settings, open Special app access → Display over other apps → DA CALLER."),Toast.LENGTH_LONG).show();
  }
 }
 private void requestPhoneRole(){
  if(holdsPhoneRole()){updateIntegrationStatus();return;}
  try{
   if(Build.VERSION.SDK_INT>=29){RoleManager roles=getSystemService(RoleManager.class);
    if(roles!=null&&roles.isRoleAvailable(RoleManager.ROLE_DIALER)){startActivity(roles.createRequestRoleIntent(RoleManager.ROLE_DIALER));return;}}
   startActivity(new Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME,getPackageName()));
  }catch(RuntimeException unavailable){Toast.makeText(this,tr("בחר ב־DA CALLER כחייגן ברירת המחדל בהגדרות המכשיר.","Choose DA CALLER as the default phone app in device settings."),Toast.LENGTH_LONG).show();}
 }
 private void chooseStyle(){
  String[] values={"apple","glass","emerald"};String[] labels={tr("קלאסי שחור","Classic black"),tr("זכוכית רכה","Soft glass"),tr("אמרלד","Emerald")};
  choose(tr("סגנון האי","Island style"),values,labels,prefs.getString("island_style","apple"),value->{prefs.edit().putString("island_style",value).apply();renderPreview();});
 }
 private void chooseSize(){
  String[] values={"compact","comfortable"};String[] labels={tr("קומפקטי","Compact"),tr("מרווח","Comfortable")};
  choose(tr("גודל האי","Island size"),values,labels,prefs.getString("island_size","compact"),value->{prefs.edit().putString("island_size",value).apply();renderPreview();});
 }
 private void choosePhase(){
  String[] values={"active","incoming","outgoing","held"};String[] labels={tr("שיחה פעילה","Active call"),tr("שיחה נכנסת","Incoming call"),tr("שיחה יוצאת","Outgoing call"),tr("שיחה בהמתנה","Call on hold")};
  String selected=phase==Call.STATE_RINGING?"incoming":phase==Call.STATE_DIALING?"outgoing":phase==Call.STATE_HOLDING?"held":"active";
  choose(tr("מצב ההדגמה","Preview state"),values,labels,selected,value->{
   ended=false;muted=false;speaker=false;connectedAt=System.currentTimeMillis()-11_000;
   phase=value.equals("incoming")?Call.STATE_RINGING:value.equals("outgoing")?Call.STATE_DIALING:value.equals("held")?Call.STATE_HOLDING:Call.STATE_ACTIVE;renderPreview();
  });
 }
 private interface Selection {void select(String value);}
 private void choose(String title,String[] values,String[] labels,String selected,Selection selection){
  int index=java.util.Arrays.asList(values).indexOf(selected);
  new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(labels,Math.max(0,index),(dialog,which)->{selection.select(values[which]);dialog.dismiss();})
   .setNegativeButton(tr("ביטול","Cancel"),null).show();
 }
 private void demoAction(String action){
  if("MUTE_ON".equals(action))muted=true;else if("MUTE_OFF".equals(action))muted=false;
  else if("SPEAKER_ON".equals(action))speaker=true;else if("SPEAKER_OFF".equals(action))speaker=false;
  else if("HOLD".equals(action))phase=Call.STATE_HOLDING;
  else if("ANSWER".equals(action)){phase=Call.STATE_ACTIVE;connectedAt=System.currentTimeMillis();}
  else if("RESUME".equals(action))phase=Call.STATE_ACTIVE;
  else if("END".equals(action)||"DECLINE".equals(action))ended=true;
  renderPreview();
 }
 private void renderPreview(){
  if(preview==null)return;
  String state=phase==Call.STATE_RINGING?tr("שיחה נכנסת","Incoming call"):phase==Call.STATE_DIALING?tr("שיחה יוצאת","Outgoing call"):
    phase==Call.STATE_HOLDING?tr("שיחה בהמתנה","Call on hold"):tr("שיחה פעילה","Active call");
  phaseChoice.setText(tr("מצב ההדגמה","Preview state")+"  ·  "+state);
  previewToggle.setText(ended?tr("להפעיל שוב את ההדגמה","Restart preview"):expanded?tr("תצוגה קומפקטית","Compact preview"):tr("הרחבת האי","Expand island"));
  styleChoice.setText(tr("סגנון האי","Island style")+"  ·  "+styleLabel());
  sizeChoice.setText(tr("גודל האי","Island size")+"  ·  "+("comfortable".equals(prefs.getString("island_size","compact"))?tr("מרווח","Comfortable"):tr("קומפקטי","Compact")));
  preview.setVisibility(ended?View.GONE:View.VISIBLE);previewEnd.setVisibility(ended?View.VISIBLE:View.GONE);
  if(!ended){
   boolean controls=phase!=Call.STATE_RINGING;
   preview.bind(new CallIslandState("island-demo",tr("דנה לדוגמה","Dana Example"),"050-000-0000",phase,
     speaker?CallAudioState.ROUTE_SPEAKER:CallAudioState.ROUTE_EARPIECE,CallAudioState.ROUTE_EARPIECE|CallAudioState.ROUTE_SPEAKER,
     muted,phase==Call.STATE_ACTIVE||phase==Call.STATE_HOLDING,controls,phase==Call.STATE_RINGING||phase==Call.STATE_DIALING?0:connectedAt,-1),expanded);
   preview.tick(System.currentTimeMillis());
  }
 }
 private String styleLabel(){String value=prefs.getString("island_style","apple");return "glass".equals(value)?tr("זכוכית רכה","Soft glass"):"emerald".equals(value)?tr("אמרלד","Emerald"):tr("קלאסי שחור","Classic black");}
 private void applySystemBars(){
  getWindow().setStatusBarColor(background);getWindow().setNavigationBarColor(background);
  if(Build.VERSION.SDK_INT>=30){WindowInsetsController controller=getWindow().getInsetsController();if(controller!=null){
   int mask=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;controller.setSystemBarsAppearance(dark?0:mask,mask);
  }}else getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
 }
 @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused&&prefs!=null)applySystemBars();}
 @Override protected void onResume(){super.onResume();handler.removeCallbacks(clock);handler.post(clock);updateIntegrationStatus();getWindow().getDecorView().post(this::applySystemBars);}
 @Override protected void onPause(){handler.removeCallbacks(clock);super.onPause();}
 @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putBoolean("expanded",expanded);state.putBoolean("muted",muted);state.putBoolean("speaker",speaker);state.putBoolean("ended",ended);state.putInt("phase",phase);state.putLong("connected_at",connectedAt);}
}
