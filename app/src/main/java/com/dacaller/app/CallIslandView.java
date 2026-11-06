package com.dacaller.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;

/** App-owned call capsule. Its animated bars describe call activity, never microphone levels. */
final class CallIslandView extends FrameLayout {
 interface Listener {
  void onExpand();
  void onCollapse();
  void onAction(String action);
  void onOpen();
 }
 private final SharedPreferences prefs;
 private Listener listener;
 private CallIslandState state;
 private String rendered="";
 private boolean expanded;
 private TextView elapsed;
 private int accent=0xFF64E69A;
 private int bodyTop=0xFF1B1B1D,bodyBottom=0xFF070708,edge=0xFF38383B;
 private long avatarBinding;

 CallIslandView(Context context){
  super(context);
  prefs=context.getSharedPreferences("da_caller",Context.MODE_PRIVATE);
  setTag("island-root");setClipChildren(true);setClipToPadding(true);
  setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
  // The elapsed label changes once a second. Do not make the whole capsule a
  // live region, which would repeatedly interrupt a screen reader.
  setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_NONE);
  setMinimumWidth(dp(184));setElevation(dp(11));
 }
 void setListener(Listener value){listener=value;}
 boolean isExpanded(){return expanded;}
 private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
 private String tr(String he,String en){return I18n.isEnglish(getContext())?en:he;}
 private boolean setting(String key,boolean fallback){return prefs.getBoolean(key,fallback);}
 private boolean comfortable(){return "comfortable".equals(prefs.getString("island_size","compact"));}
 private String identity(CallIslandState value){
  if(value.name!=null&&!value.name.trim().isEmpty())return value.name;
  return value.number==null||value.number.trim().isEmpty()?tr("מספר לא מזוהה","Unknown caller"):value.number;
 }
 private boolean running(CallIslandState value){
  return value.state==Call.STATE_ACTIVE||value.state==Call.STATE_HOLDING;
 }
 private String phase(CallIslandState value){return CallNotifications.stateLabel(getContext(),value.state);}
 private String renderKey(CallIslandState value,boolean open){
  return value.token+"/"+value.name+"/"+value.number+"/"+value.state+"/"+value.route+"/"+value.supportedRoutes
    +"/"+value.muted+"/"+value.canHold+"/"+value.canMute+"/"+value.contactId+"/"+value.connectedAtMillis+"/"+open
    +"/"+prefs.getString("language","he")+"/"+prefs.getString("island_style","apple")
    +"/"+prefs.getString("island_size","compact")+"/"+prefs.getString("avatar_shape","circle")
    +"/"+setting("island_show_timer",true)+"/"+setting("island_show_photo",true)
    +"/"+setting("island_animations",true)+"/"+setting("island_show_mute",true)
    +"/"+setting("island_show_speaker",true)+"/"+setting("island_show_hold",true);
 }
 void bind(CallIslandState next,boolean open){
  if(next==null){state=null;rendered="";elapsed=null;++avatarBinding;removeAllViews();return;}
  String key=renderKey(next,open);state=next;
  if(rendered.equals(key)){tick(System.currentTimeMillis());return;}
  boolean morph=isLaidOut()&&!rendered.isEmpty()&&expanded!=open;
  rendered=key;expanded=open;elapsed=null;++avatarBinding;
  animate().cancel();setAlpha(1f);setScaleX(1f);setScaleY(1f);removeAllViews();
  palette();setBackground(surface(open?31:100));setClipToOutline(true);
  setLayoutDirection(I18n.isEnglish(getContext())?LAYOUT_DIRECTION_LTR:LAYOUT_DIRECTION_RTL);
  LinearLayout content=column();content.setGravity(Gravity.CENTER_VERTICAL);
  content.setPadding(dp(open?15:12),dp(open?10:4),dp(open?15:12),dp(open?10:4));
  addView(content,new FrameLayout.LayoutParams(-1,-2));
  setMinimumHeight(dp(open?0:comfortable()?64:56));
  String token=next.token;
  if(open)expanded(content,next,token);else compact(content,next,token);
  setContentDescription(identity(next)+". "+phase(next)+". "+tr(open?"פקדי שיחה":"הקש להרחבה",open?"Call controls":"Tap to expand"));
  setOnClickListener(open?null:v->expand(token));
  setOnLongClickListener(v->{open(token);return true;});
  tick(System.currentTimeMillis());
  if(morph&&motion()){
   setAlpha(.78f);setScaleX(.975f);setScaleY(.96f);
   animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).start();
  }
 }
 private void palette(){
  String style=prefs.getString("island_style","apple");
  if("glass".equals(style)){bodyTop=0xFF30343D;bodyBottom=0xFF151920;edge=0xFF616671;accent=0xFF94F0BE;}
  else if("emerald".equals(style)){bodyTop=0xFF194C3C;bodyBottom=0xFF071D17;edge=0xFF42614C;accent=0xFFE6CA85;}
  else{bodyTop=0xFF1B1B1D;bodyBottom=0xFF070708;edge=0xFF38383B;accent=0xFF64E69A;}
 }
 private boolean motion(){return setting("island_animations",true)&&ValueAnimator.areAnimatorsEnabled();}
 private GradientDrawable surface(float radius){
  GradientDrawable drawable=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{bodyTop,bodyBottom});
  drawable.setCornerRadius(dp(radius));drawable.setStroke(dp(1),edge);return drawable;
 }
 private GradientDrawable shape(int color,float radius){
  GradientDrawable drawable=new GradientDrawable();drawable.setColor(color);drawable.setCornerRadius(dp(radius));return drawable;
 }
 private LinearLayout column(){LinearLayout view=new LinearLayout(getContext());view.setOrientation(LinearLayout.VERTICAL);return view;}
 private LinearLayout row(){LinearLayout view=new LinearLayout(getContext());view.setGravity(Gravity.CENTER_VERTICAL);return view;}
 private TextView text(String value,float size,int color,boolean strong){
  TextView view=new TextView(getContext());view.setText(value);view.setTextSize(size);view.setTextColor(color);
  view.setTypeface(Typeface.create(strong?"sans-serif-medium":"sans-serif",Typeface.NORMAL));
  view.setIncludeFontPadding(false);view.setGravity(Gravity.CENTER_VERTICAL);return view;
 }
 private ImageView icon(int resource,int color){
  ImageView view=new ImageView(getContext());view.setImageResource(resource);view.setColorFilter(color);
  view.setScaleType(ImageView.ScaleType.CENTER_INSIDE);view.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);return view;
 }
 private boolean bound(String token){return state!=null&&java.util.Objects.equals(token,state.token);}
 private void expand(String token){if(bound(token)&&listener!=null)listener.onExpand();}
 private void collapse(String token){if(bound(token)&&listener!=null)listener.onCollapse();}
 private void open(String token){if(bound(token)&&listener!=null)listener.onOpen();}
 private void action(String token,String action){if(bound(token)&&listener!=null)listener.onAction(action);}

 private View avatar(CallIslandState value,int size){
  FrameLayout frame=AvatarShapes.frame(getContext());frame.setTag("island-avatar");
  frame.setBackgroundColor(0xFF34413C);frame.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
  boolean showPhoto=setting("island_show_photo",true);
  if(!showPhoto){
   ImageView phone=icon(R.drawable.ic_phone_line,accent);phone.setPadding(dp(7),dp(7),dp(7),dp(7));
   frame.addView(phone,new FrameLayout.LayoutParams(-1,-1));return frame;
  }
  String name=identity(value).trim();String initial=name.isEmpty()?"?":name.substring(0,name.offsetByCodePoints(0,1));
  TextView letter=text(initial.toUpperCase(Locale.ROOT),size*.4f,0xFFECF9EF,true);letter.setGravity(Gravity.CENTER);
  frame.addView(letter,new FrameLayout.LayoutParams(-1,-1));
  if(value.contactId>0){
   ImageView photo=new ImageView(getContext());photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
   frame.addView(photo,new FrameLayout.LayoutParams(-1,-1));
   long generation=avatarBinding;String token=value.token;
   ContactPhotos.load(getContext(),value.contactId,image->{
    if(generation!=avatarBinding||!bound(token)||!frame.isAttachedToWindow()||
      getContext().checkSelfPermission(android.Manifest.permission.READ_CONTACTS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;
    photo.setImageBitmap(image);photo.setVisibility(image==null?GONE:VISIBLE);
   });
  }
  return frame;
 }
 private void addAvatar(LinearLayout parent,CallIslandState value,int size){
  LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(dp(size),dp(size));params.setMarginEnd(dp(10));
  parent.addView(avatar(value,size),params);
 }
 private TextView clock(){
  TextView view=text("00:00",comfortable()?14:13,accent,true);view.setTag("island-timer");
  view.setTextDirection(TEXT_DIRECTION_LTR);view.setFontFeatureSettings("tnum");view.setSingleLine(true);
  view.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);return view;
 }
 private void compact(LinearLayout content,CallIslandState value,String token){
  LinearLayout line=row();line.setTag("island-expand");line.setMinimumHeight(dp(comfortable()?56:48));
  line.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
  content.addView(line,new LinearLayout.LayoutParams(-1,-2));
  addAvatar(line,value,comfortable()?36:32);
  LinearLayout copy=column();line.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
  TextView name=text(identity(value),comfortable()?15:14,Color.WHITE,true);name.setTag("island-caller");
  name.setTextDirection(TEXT_DIRECTION_FIRST_STRONG_LTR);
  name.setSingleLine(true);name.setEllipsize(TextUtils.TruncateAt.END);copy.addView(name,new LinearLayout.LayoutParams(-1,-2));
  TextView status=text(phase(value),11,0xFFB8BCBF,false);status.setTag("island-phase");
  status.setPadding(0,dp(3),0,0);status.setSingleLine(true);status.setEllipsize(TextUtils.TruncateAt.END);
  copy.addView(status,new LinearLayout.LayoutParams(-1,-2));
  if(setting("island_show_timer",true)&&running(value)&&value.connectedAtMillis>0){
   elapsed=clock();LinearLayout.LayoutParams time=new LinearLayout.LayoutParams(-2,-2);time.setMarginStart(dp(8));line.addView(elapsed,time);
  }
  IslandWaveformView bars=new IslandWaveformView(getContext());bars.setTag("island-activity");
  bars.configure(value.state==Call.STATE_HOLDING?0xFFFFCA6B:accent,motion(),value.state==Call.STATE_HOLDING);
  LinearLayout.LayoutParams waveform=new LinearLayout.LayoutParams(dp(20),dp(24));waveform.setMarginStart(dp(9));line.addView(bars,waveform);
  line.setContentDescription(identity(value)+". "+phase(value)+". "+tr("הרחבת פקדי שיחה","Expand call controls"));
  line.setOnClickListener(v->expand(token));line.setOnLongClickListener(v->{open(token);return true;});
 }
 private void expanded(LinearLayout content,CallIslandState value,String token){
  LinearLayout header=row();content.addView(header,new LinearLayout.LayoutParams(-1,-2));
  addAvatar(header,value,40);
  LinearLayout copy=column();header.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
  TextView name=text(identity(value),16,Color.WHITE,true);name.setTag("island-caller");
  name.setTextDirection(TEXT_DIRECTION_FIRST_STRONG_LTR);
  name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);copy.addView(name,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout detail=row();detail.setPadding(0,dp(5),0,0);copy.addView(detail,new LinearLayout.LayoutParams(-1,-2));
  TextView status=text(phase(value),12,value.state==Call.STATE_HOLDING?0xFFFFCA6B:0xFFB8BCBF,false);status.setTag("island-phase");
  status.setSingleLine(true);status.setEllipsize(TextUtils.TruncateAt.END);detail.addView(status,new LinearLayout.LayoutParams(0,-2,1));
  if(setting("island_show_timer",true)&&running(value)&&value.connectedAtMillis>0){
   elapsed=clock();LinearLayout.LayoutParams time=new LinearLayout.LayoutParams(-2,-2);time.setMarginStart(dp(6));detail.addView(elapsed,time);
  }
  ImageView chevron=icon(R.drawable.ic_call_chevron,0xFFC7CACE);chevron.setRotation(180);chevron.setPadding(dp(13),dp(13),dp(13),dp(13));
  chevron.setTag("island-collapse");chevron.setContentDescription(tr("צמצום האי","Collapse island"));
  chevron.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);chevron.setBackground(shape(0x112D3034,24));
  LinearLayout.LayoutParams close=new LinearLayout.LayoutParams(dp(48),dp(48));close.setMarginStart(dp(5));header.addView(chevron,close);
  chevron.setOnClickListener(v->collapse(token));
  header.setOnClickListener(v->collapse(token));header.setOnLongClickListener(v->{open(token);return true;});

  AdaptiveControls controls=new AdaptiveControls(getContext());controls.setTag("island-controls");
  LinearLayout.LayoutParams controlsParams=new LinearLayout.LayoutParams(-1,-2);controlsParams.topMargin=dp(8);
  content.addView(controls,controlsParams);
  if(value.state==Call.STATE_RINGING){
   controls.addView(control(token,"DECLINE",R.drawable.ic_call_hangup,tr("דחה","Decline"),tr("דחיית השיחה","Decline call"),0xFFFF454F,Color.WHITE,56));
   controls.addView(control(token,"ANSWER",R.drawable.ic_phone_line,tr("ענה","Answer"),tr("מענה לשיחה","Answer call"),0xFF30D56D,Color.WHITE,56));
  }else{
   boolean usable=CallNotifications.controllable(value.state);
   if(usable&&value.canMute&&setting("island_show_mute",true))controls.addView(control(token,value.muted?"MUTE_OFF":"MUTE_ON",
     value.muted?R.drawable.ic_control_mic_off:R.drawable.ic_control_mic,tr("השתקה","Mute"),
     tr(value.muted?"ביטול השתקת המיקרופון":"השתקת המיקרופון",value.muted?"Unmute microphone":"Mute microphone"),
     value.muted?0xFFF2F4F6:0xFF2B2D31,value.muted?0xFF111214:Color.WHITE,48));
   boolean speaker=value.route==CallAudioState.ROUTE_SPEAKER;
   if(usable&&setting("island_show_speaker",true)&&CallNotifications.speakerDestination(!speaker,value.supportedRoutes)!=0)
    controls.addView(control(token,speaker?"SPEAKER_OFF":"SPEAKER_ON",R.drawable.ic_control_speaker,tr("רמקול","Speaker"),
      tr(speaker?"כיבוי רמקול":"הפעלת רמקול",speaker?"Turn speaker off":"Turn speaker on"),
      speaker?0xFFF2F4F6:0xFF2B2D31,speaker?0xFF111214:Color.WHITE,48));
   if(value.canHold&&setting("island_show_hold",true))controls.addView(control(token,value.state==Call.STATE_HOLDING?"RESUME":"HOLD",
     value.state==Call.STATE_HOLDING?R.drawable.ic_control_play:R.drawable.ic_control_pause,
     tr(value.state==Call.STATE_HOLDING?"המשך":"המתנה",value.state==Call.STATE_HOLDING?"Resume":"Hold"),
     tr(value.state==Call.STATE_HOLDING?"המשך השיחה":"העברה להמתנה",value.state==Call.STATE_HOLDING?"Resume call":"Hold call"),
     value.state==Call.STATE_HOLDING?0xFFF2F4F6:0xFF2B2D31,value.state==Call.STATE_HOLDING?0xFF111214:Color.WHITE,48));
   if(value.state!=Call.STATE_DISCONNECTED&&value.state!=Call.STATE_DISCONNECTING)
    controls.addView(control(token,"END",R.drawable.ic_call_hangup,tr("סיום","End"),tr("סיום השיחה","End call"),0xFFFF454F,Color.WHITE,48));
  }
  if(controls.getChildCount()==0)controls.setVisibility(GONE);

  LinearLayout full=row();full.setTag("island-open");full.setGravity(Gravity.CENTER);full.setMinimumHeight(dp(48));
  full.setPadding(dp(12),dp(5),dp(12),dp(5));full.setBackground(shape(0xFF25282A,20));
  ImageView handset=icon(R.drawable.ic_phone_line,accent);full.addView(handset,new LinearLayout.LayoutParams(dp(21),dp(21)));
  TextView title=text(tr("למסך השיחה","Open call"),13,0xFFF1F5F3,true);title.setGravity(Gravity.CENTER);
  title.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
  title.setSingleLine(true);title.setEllipsize(TextUtils.TruncateAt.END);LinearLayout.LayoutParams titleParams=new LinearLayout.LayoutParams(0,-2,1);
  titleParams.setMarginStart(dp(8));titleParams.setMarginEnd(dp(8));full.addView(title,titleParams);
  TextView arrow=text(I18n.isEnglish(getContext())?"↗":"↖",18,accent,false);arrow.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
  full.addView(arrow,new LinearLayout.LayoutParams(dp(22),ViewGroup.LayoutParams.WRAP_CONTENT));
  full.setContentDescription(tr("חזרה למסך השיחה המלא","Return to full call screen"));
  full.setOnClickListener(v->open(token));
  LinearLayout.LayoutParams fullParams=new LinearLayout.LayoutParams(-1,-2);fullParams.topMargin=dp(6);content.addView(full,fullParams);
 }
 private View control(String token,String action,int resource,String label,String description,int color,int tint,int diameter){
  LinearLayout slot=column();slot.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);slot.setPadding(dp(2),0,dp(2),0);
  slot.setTag("island-action-"+action);slot.setContentDescription(description);slot.setMinimumHeight(dp(diameter+20));
  slot.setClickable(true);slot.setFocusable(true);slot.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
  FrameLayout circle=new FrameLayout(getContext());circle.setBackground(shape(color,100));circle.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
  ImageView glyph=icon(resource,tint);int inset=diameter==56?15:12;glyph.setPadding(dp(inset),dp(inset),dp(inset),dp(inset));
  circle.addView(glyph,new FrameLayout.LayoutParams(-1,-1));slot.addView(circle,new LinearLayout.LayoutParams(dp(diameter),dp(diameter)));
  TextView caption=text(label,10.5f,0xFFDEE2E1,false);caption.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);caption.setMaxLines(2);
  caption.setPadding(0,dp(6),0,0);caption.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
  slot.addView(caption,new LinearLayout.LayoutParams(-1,-2));slot.setOnClickListener(v->action(token,action));return slot;
 }
 void tick(long nowMillis){
  if(elapsed==null||state==null)return;
  long seconds=Math.max(0,(nowMillis-state.connectedAtMillis)/1000);
  String value=seconds>=3600?String.format(Locale.ROOT,"%d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60):
    String.format(Locale.ROOT,"%02d:%02d",seconds/60,seconds%60);
  if(!value.contentEquals(elapsed.getText())){
   elapsed.setText(value);elapsed.setContentDescription(tr("משך השיחה: ","Call duration: ")+value);
  }
 }
 @Override protected void onDetachedFromWindow(){animate().cancel();super.onDetachedFromWindow();}

 /** A 48dp control never shrinks to fit a narrow phone: extra controls wrap. */
 private final class AdaptiveControls extends ViewGroup {
  private int columns=1,cellWidth,rowHeight;
  AdaptiveControls(Context context){super(context);setClipChildren(false);}
  @Override protected void onMeasure(int widthSpec,int heightSpec){
   int width=MeasureSpec.getSize(widthSpec),count=getChildCount();
   if(width==0)width=Math.max(dp(184),getResources().getDisplayMetrics().widthPixels-dp(54));
   columns=Math.max(1,Math.min(count,Math.max(1,width/dp(60))));
   cellWidth=width/columns;rowHeight=0;
   for(int i=0;i<count;i++){
    View child=getChildAt(i);child.measure(MeasureSpec.makeMeasureSpec(cellWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
    rowHeight=Math.max(rowHeight,child.getMeasuredHeight());
   }
   int rows=(count+columns-1)/columns;
   setMeasuredDimension(resolveSize(width,widthSpec),resolveSize(rows*rowHeight+Math.max(0,rows-1)*dp(8),heightSpec));
  }
  @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){
   int width=right-left,count=getChildCount();boolean rtl=getLayoutDirection()==LAYOUT_DIRECTION_RTL;
   for(int i=0;i<count;i++){
    int row=i/columns,column=i%columns,inRow=Math.min(columns,count-row*columns);
    int start=(width-inRow*cellWidth)/2;
    int x=rtl?width-start-(column+1)*cellWidth:start+column*cellWidth;
    View child=getChildAt(i);int y=row*(rowHeight+dp(8));child.layout(x,y,x+cellWidth,y+rowHeight);
   }
  }
 }
}
