package com.dacaller.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.JsonWriter;
import android.util.AtomicFile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.OutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

/** Portable bounded settings, with explicitly selected private archive metadata. No contacts/CallLog access. */
final class SettingsBackup {
 static final int MAX_BYTES=16*1024*1024;
 private static final int MAX_SETTINGS_BYTES=2*1024*1024;
 private static final int MAX_ENTRIES=4096,MAX_FAVORITES=10000;
 private static final String FORMAT="DA_CALLER_SETTINGS",PACKAGE="com.dacaller.app";
 private static final Map<String,Object> DEFAULTS=new LinkedHashMap<>();
 private static final Map<String,Set<String>> ENUMS=new HashMap<>();
 static {
  bool("dark",false);bool("amoled",false);bool("recents_green",false);bool("show_stats",true);
  bool("haptics",true);bool("favorites_expanded",true);bool("stats_expanded",false);
  bool("island_enabled",true);bool("island_show_timer",true);bool("island_show_photo",true);
  bool("island_animations",true);bool("island_show_mute",true);bool("island_show_speaker",true);
  bool("island_show_hold",true);bool("island_confirm_end",true);
  bool("island_outside_app",false);
  bool("callback_reminder_enabled",false);DEFAULTS.put("callback_reminder_minute",21*60);
  option("island_style","apple","apple","glass","emerald","transparent");
  DEFAULTS.put("island_background_opacity",35);
  option("island_position_mode","below","below","status_area");bool("island_position_set",false);
  DEFAULTS.put("island_position_x",5000);DEFAULTS.put("island_position_y",0);
  option("island_size","compact","compact","comfortable");
  option("language","he","he","en");option("accent","gold","gold","silver");
  option("call_theme","pixel",CallThemes.IDS);
  option("answer_mode","ios","right","left","vertical","ios");
  option("archive_extra",CallArchive.OFF,CallArchive.OPTIONS);
  option("swipe_direction","right","right","left","off");
  option("start_page","recents","recents","contacts","favorites");
  option("avatar_shape","circle","circle","rounded_square","square","hexagon");
  option("launcher_icon",AppIcons.DEFAULT,AppIcons.IDS);
  DEFAULTS.put("call_button_height",65);DEFAULTS.put("haptic_strength",2);
  for(String page:new String[]{"recents","contacts","favorites","settings"})DEFAULTS.put("font_"+page,100);
  for(String id:InCallControls.IDS)bool("call_btn_"+id,
    id.equals("mute")||id.equals("speaker")||id.equals("keypad")||id.equals("hold")||id.equals("addcall"));
  DEFAULTS.put("favorites",Collections.emptySet());
 }
 private static void bool(String key,boolean value){DEFAULTS.put(key,value);}
 private static void option(String key,String value,String... allowed){DEFAULTS.put(key,value);ENUMS.put(key,new HashSet<>(Arrays.asList(allowed)));}
 static final class Snapshot {
  final Map<String,Object> values;
  final CallArchive.Backup archive;
  Snapshot(Map<String,Object> values){this(values,null);}
  Snapshot(Map<String,Object> values,CallArchive.Backup archive){
   LinkedHashMap<String,Object> copy=new LinkedHashMap<>();
   for(Map.Entry<String,Object> item:values.entrySet())copy.put(item.getKey(),item.getValue() instanceof Set?
     Collections.unmodifiableSet(new HashSet<>((Set<?>)item.getValue())):item.getValue());
   this.values=Collections.unmodifiableMap(copy);
   this.archive=archive;
  }
  int favorites(){Object value=values.get("favorites");return value instanceof Set?((Set<?>)value).size():0;}
  int notes(){int count=0;for(String key:values.keySet())if(key.startsWith("note_"))count++;return count;}
  int settings(){return values.size()-(values.containsKey("favorites")?1:0)-notes();}
 }
 static final class InvalidBackup extends IOException {
  InvalidBackup(){super("Invalid or unsupported DA CALLER settings backup");}
 }
 static Snapshot capture(SharedPreferences prefs)throws IOException {
  Map<String,?> actual=prefs.getAll();LinkedHashMap<String,Object> selected=new LinkedHashMap<>();
  for(Map.Entry<String,Object> item:DEFAULTS.entrySet())selected.put(item.getKey(),actual.containsKey(item.getKey())?actual.get(item.getKey()):item.getValue());
  for(Map.Entry<String,?> item:actual.entrySet())if(dynamic(item.getKey()))selected.put(item.getKey(),item.getValue());
  validate(selected);return new Snapshot(selected);
 }
 static synchronized Snapshot capture(Context app,boolean includeArchive)throws IOException {
  if(hasPendingRestore(app))throw new IOException("Previous backup restore needs recovery");
  Snapshot settings=capture(preferences(app));
  return includeArchive?new Snapshot(settings.values,CallArchive.captureBackup(app)):settings;
 }
 static byte[] encode(Snapshot snapshot)throws IOException {
  validate(snapshot.values);
  if(snapshot.archive!=null){CallArchive.validateBackup(snapshot.archive);encode(new Snapshot(snapshot.values));}
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();
  try(JsonWriter json=new JsonWriter(new OutputStreamWriter(new BoundedOutput(bytes,snapshot.archive==null?MAX_SETTINGS_BYTES:MAX_BYTES),StandardCharsets.UTF_8))){
   json.setIndent("  ");json.beginObject();
   json.name("format").value(FORMAT);json.name("version").value(snapshot.archive==null?1:2);json.name("package").value(PACKAGE);
   SimpleDateFormat date=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",java.util.Locale.US);date.setTimeZone(TimeZone.getTimeZone("UTC"));
   json.name("createdAt").value(date.format(new Date()));json.name("settings").beginObject();
   ArrayList<String> keys=new ArrayList<>(snapshot.values.keySet());Collections.sort(keys);
   for(String key:keys){
    Object value=snapshot.values.get(key);json.name(key);
    if(value instanceof Boolean)json.value((Boolean)value);
    else if(value instanceof Integer)json.value((Integer)value);
    else if(value instanceof String)json.value((String)value);
    else if(value instanceof Set){
     json.beginArray();ArrayList<String> strings=new ArrayList<>();for(Object item:(Set<?>)value)strings.add((String)item);
     Collections.sort(strings);for(String item:strings)json.value(item);json.endArray();
    }else throw new InvalidBackup();
   }
   json.endObject();
   if(snapshot.archive!=null)writeArchive(json,snapshot.archive);
   json.endObject();
  }
  if(bytes.size()>(snapshot.archive==null?MAX_SETTINGS_BYTES:MAX_BYTES))throw new InvalidBackup();return bytes.toByteArray();
 }
 private static final class BoundedOutput extends OutputStream {
  final ByteArrayOutputStream bytes;final int limit;
  BoundedOutput(ByteArrayOutputStream bytes,int limit){this.bytes=bytes;this.limit=limit;}
  @Override public void write(int value)throws IOException {if(bytes.size()>=limit)throw new InvalidBackup();bytes.write(value);}
  @Override public void write(byte[] values,int offset,int length)throws IOException {
   if(length>limit-bytes.size())throw new InvalidBackup();bytes.write(values,offset,length);
  }
 }
 static Snapshot read(InputStream input)throws IOException {
  if(input==null)throw new InvalidBackup();
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
  while((count=input.read(buffer))!=-1){if(bytes.size()+count>MAX_BYTES)throw new InvalidBackup();bytes.write(buffer,0,count);}
  return decode(bytes.toByteArray());
 }
 static Snapshot decode(byte[] bytes)throws IOException {
  if(bytes==null||bytes.length==0||bytes.length>MAX_BYTES)throw new InvalidBackup();
  try(JsonReader json=new JsonReader(new InputStreamReader(new ByteArrayInputStream(bytes),
    StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)))){
   json.setLenient(false);json.beginObject();Set<String> names=new HashSet<>();Map<String,Object> settings=null;
   String version=null;CallArchive.Backup archive=null;
   while(json.hasNext()){
    String name=json.nextName();if(!names.add(name))throw new InvalidBackup();
    switch(name){
     case "format":if(json.peek()!=JsonToken.STRING||!FORMAT.equals(json.nextString()))throw new InvalidBackup();break;
     case "package":if(json.peek()!=JsonToken.STRING||!PACKAGE.equals(json.nextString()))throw new InvalidBackup();break;
     case "version":if(json.peek()!=JsonToken.NUMBER)throw new InvalidBackup();version=json.nextString();if(!"1".equals(version)&&!"2".equals(version))throw new InvalidBackup();break;
     case "createdAt":if(json.peek()!=JsonToken.STRING||!validText(json.nextString(),64))throw new InvalidBackup();break;
     case "settings":settings=readSettings(json);break;
     case "archive":archive=readArchive(json);break;
     default:throw new InvalidBackup();
    }
   }
   json.endObject();if(json.peek()!=JsonToken.END_DOCUMENT||settings==null||version==null||
     ("1".equals(version)?names.size()!=5||archive!=null||bytes.length>MAX_SETTINGS_BYTES:names.size()!=6||archive==null))throw new InvalidBackup();
   validate(settings);if(archive!=null)encode(new Snapshot(settings));return new Snapshot(settings,archive);
  }catch(IllegalStateException|NumberFormatException error){throw new InvalidBackup();}
 }
 private static void writeArchive(JsonWriter json,CallArchive.Backup archive)throws IOException {
  json.name("archive").beginObject();json.name("schema").value(2);json.name("capturedAtMs").value(archive.capturedAtMs);
  json.name("records").beginArray();
  for(CallArchive.BackupRow row:archive.rows){
   json.beginObject();json.name("key").value(row.key);json.name("callId").value(row.callId);json.name("number").value(row.number);
   json.name("atMs").value(row.atMs);json.name("seconds").value(row.seconds);json.name("kind").value(row.kind);
   json.name("lastSeenMs").value(row.lastSeenMs);json.name("missingSinceMs").value(row.missingSinceMs);json.name("seenGeneration").value(row.seenGeneration);json.endObject();
  }
  json.endArray();json.endObject();
 }
 private static long readLong(JsonReader json)throws IOException {
  if(json.peek()!=JsonToken.NUMBER)throw new InvalidBackup();String value=json.nextString();
  if(!value.matches("0|[1-9][0-9]{0,18}"))throw new InvalidBackup();
  try{return Long.parseLong(value);}catch(NumberFormatException failure){throw new InvalidBackup();}
 }
 private static CallArchive.Backup readArchive(JsonReader json)throws IOException {
  if(json.peek()!=JsonToken.BEGIN_OBJECT)throw new InvalidBackup();json.beginObject();Set<String> names=new HashSet<>();
  long captured=-1;ArrayList<CallArchive.BackupRow> rows=null;
  while(json.hasNext()){
   String name=json.nextName();if(!names.add(name))throw new InvalidBackup();
   switch(name){
    case "schema":if(readLong(json)!=2)throw new InvalidBackup();break;
    case "capturedAtMs":captured=readLong(json);break;
    case "records":
     if(json.peek()!=JsonToken.BEGIN_ARRAY)throw new InvalidBackup();rows=new ArrayList<>();json.beginArray();
     while(json.hasNext()){if(rows.size()>=CallArchive.Backup.MAX_ROWS)throw new InvalidBackup();rows.add(readArchiveRow(json));}
     json.endArray();break;
    default:throw new InvalidBackup();
   }
  }
  json.endObject();if(names.size()!=3||captured<0||rows==null)throw new InvalidBackup();
  CallArchive.Backup archive=new CallArchive.Backup(captured,rows);CallArchive.validateBackup(archive);return archive;
 }
 private static CallArchive.BackupRow readArchiveRow(JsonReader json)throws IOException {
  if(json.peek()!=JsonToken.BEGIN_OBJECT)throw new InvalidBackup();json.beginObject();Set<String> names=new HashSet<>();
  String key=null,number=null;Map<String,Long> fields=new HashMap<>();
  while(json.hasNext()){
   String name=json.nextName();if(!names.add(name))throw new InvalidBackup();
   if("key".equals(name)||"number".equals(name)){
    if(json.peek()!=JsonToken.STRING)throw new InvalidBackup();String value=json.nextString();
    if("key".equals(name))key=value;else number=value;
   }else if(Arrays.asList("callId","atMs","seconds","kind","lastSeenMs","missingSinceMs","seenGeneration").contains(name))fields.put(name,readLong(json));
   else throw new InvalidBackup();
  }
  json.endObject();if(names.size()!=9||key==null||number==null||fields.size()!=7||fields.get("kind")>7)throw new InvalidBackup();
  return new CallArchive.BackupRow(key,fields.get("callId"),number,fields.get("atMs"),fields.get("seconds"),fields.get("kind").intValue(),
    fields.get("lastSeenMs"),fields.get("missingSinceMs"),fields.get("seenGeneration"));
 }
 private static Map<String,Object> readSettings(JsonReader json)throws IOException {
  if(json.peek()!=JsonToken.BEGIN_OBJECT)throw new InvalidBackup();json.beginObject();Map<String,Object> settings=new LinkedHashMap<>();
  while(json.hasNext()){
   String key=json.nextName();if(settings.size()>=MAX_ENTRIES||settings.containsKey(key)||!known(key))throw new InvalidBackup();
   Object value;
   switch(json.peek()){
    case BOOLEAN:value=json.nextBoolean();break;
    case STRING:value=json.nextString();break;
    case NUMBER:
     String number=json.nextString();if(!number.matches("-?[0-9]{1,9}"))throw new InvalidBackup();value=Integer.valueOf(number);break;
    case BEGIN_ARRAY:
     if(!"favorites".equals(key))throw new InvalidBackup();json.beginArray();Set<String> favorites=new HashSet<>();int length=0;
     while(json.hasNext()){
      if(++length>MAX_FAVORITES||json.peek()!=JsonToken.STRING)throw new InvalidBackup();
      String favorite=json.nextString();if(!favorites.add(favorite))throw new InvalidBackup();
     }
     json.endArray();value=favorites;break;
    default:throw new InvalidBackup();
   }
   settings.put(key,value);
  }
  json.endObject();return settings;
 }
 private static boolean dynamic(String key){
  return key!=null&&(key.matches("settings_group_open_[0-9]{1,2}")||key.matches("note_[+0-9*#,;]{0,128}"));
 }
 private static boolean known(String key){return DEFAULTS.containsKey(key)||dynamic(key);}
 private static void validate(Map<String,Object> values)throws IOException {
  if(values.isEmpty()||values.size()>MAX_ENTRIES)throw new InvalidBackup();
  for(Map.Entry<String,Object> item:values.entrySet()){
   String key=item.getKey();Object value=item.getValue();if(!known(key)||!validText(key,256)||value==null)throw new InvalidBackup();
   if(value instanceof String&&!validText((String)value,20000))throw new InvalidBackup();
   if(key.startsWith("note_")){if(!(value instanceof String)||((String)value).length()>20000)throw new InvalidBackup();continue;}
   if(key.startsWith("settings_group_open_")){if(!(value instanceof Boolean))throw new InvalidBackup();continue;}
   Object model=DEFAULTS.get(key);
   if(model instanceof Boolean&&!(value instanceof Boolean))throw new InvalidBackup();
   if(model instanceof String){if(!(value instanceof String)||!ENUMS.get(key).contains(value))throw new InvalidBackup();}
   if(model instanceof Integer){
    if(!(value instanceof Integer))throw new InvalidBackup();int n=(Integer)value;
    if(key.equals("call_button_height")&&n!=56&&n!=65&&n!=75)throw new InvalidBackup();
    if(key.equals("haptic_strength")&&(n<1||n>3))throw new InvalidBackup();
    if(key.startsWith("font_")&&(n<60||n>200))throw new InvalidBackup();
    if(key.equals("callback_reminder_minute")&&(n<0||n>=24*60))throw new InvalidBackup();
    if(key.equals("island_background_opacity")&&(n<0||n>100))throw new InvalidBackup();
    if((key.equals("island_position_x")||key.equals("island_position_y"))&&(n<0||n>10000))throw new InvalidBackup();
   }
   if(key.equals("favorites")){
    if(!(value instanceof Set)||((Set<?>)value).size()>MAX_FAVORITES)throw new InvalidBackup();
    for(Object favorite:(Set<?>)value)if(!(favorite instanceof String)||((String)favorite).isEmpty()||!validText((String)favorite,160))throw new InvalidBackup();
   }
  }
 }
 /** Strict UTF-8 also needs scalar validation for JSON's escaped UTF-16 surrogate form. */
 static boolean validText(String value,int max){
  if(value==null||value.length()>max)return false;
  for(int i=0;i<value.length();i++){
   char c=value.charAt(i);if(c==0)return false;
   if(Character.isHighSurrogate(c)){if(++i>=value.length()||!Character.isLowSurrogate(value.charAt(i)))return false;}
   else if(Character.isLowSurrogate(c))return false;
  }
  return true;
 }
 interface IconChange {boolean apply(String id);}
 /** Full parsing/validation precedes any writes, even when archive merge was declined. */
 static synchronized CallArchive.MergeResult restore(Context app,SharedPreferences prefs,Snapshot snapshot,boolean includeArchive,IconChange icons)throws IOException {
  validate(snapshot.values);if(snapshot.archive!=null)CallArchive.validateBackup(snapshot.archive);
  recoverPendingRestore(app,icons);
  PreferenceRestore change=new PreferenceRestore(prefs,snapshot,icons);
  if(includeArchive&&snapshot.archive!=null){
   PendingRestore pending=PendingRestore.prepare(app,change);
   try{return CallArchive.mergeBackup(app,snapshot.archive,pending.token,change);}
   finally{if(!change.touched&&!change.iconApplied)pending.clear();else recoverPendingRestore(app,icons);}
  }
  return change.apply()?new CallArchive.MergeResult(0,0,0):null;
 }
 /** Must run at application startup, before preferences or archive observation is used. */
 static boolean hasPendingRestore(Context app){
  java.io.File file=new java.io.File(app.getFilesDir(),"settings_archive_restore.pending");
  return file.exists()||new java.io.File(file+".bak").exists();
 }
 /** Restore side effects from the resolved preferences; a pending journal must stay inert. */
 static void reconcileAfterRecovery(Context context){
  if(hasPendingRestore(context))return;
  Context app=context.getApplicationContext();AppIcons.ensure(app);CallArchive.schedule(app);
  CallbackReminder.schedule(app);ContactPhotos.clearCache();WidgetSupport.refreshAll(app);
 }
 /** Pause explicit preference edits while an interrupted restore still owns its undo keys. */
 static boolean allowPreferenceChange(Context context){
  if(!hasPendingRestore(context))return true;
  Context app=context.getApplicationContext();
  Runnable notice=()->android.widget.Toast.makeText(app,I18n.isEnglish(app)?
    "Backup recovery is pending. Try saving again after it completes.":
    "שחזור הגיבוי ממתין להשלמה. נסה לשמור שוב לאחר השלמתו.",android.widget.Toast.LENGTH_SHORT).show();
  if(android.os.Looper.myLooper()==android.os.Looper.getMainLooper())notice.run();
  else new android.os.Handler(android.os.Looper.getMainLooper()).post(notice);
  return false;
 }
 static synchronized void recoverPendingRestore(Context app,IconChange icons)throws IOException {
  PendingRestore pending=PendingRestore.read(app);if(pending==null)return;
  try{CallArchive.recoverBackup(app,pending.token,new CallArchive.RestoreAction(){
   @Override @android.annotation.SuppressLint("ApplySharedPref") public boolean apply(){
    if(!icons.apply(pending.previousIcon))return false;
    try{
     SharedPreferences.Editor edit=preferences(app).edit();for(Map.Entry<String,Object> item:pending.prior.values.entrySet())put(edit,item.getKey(),item.getValue());
     for(String key:pending.absent)edit.remove(key);return edit.commit();
    }catch(RuntimeException failure){return false;}
   }
  },pending::clear);}
  catch(RuntimeException failure){throw new IOException("Cannot recover previous backup restore",failure);}
 }
 /** Private undo journal; the archive commit token decides whether recovery keeps or undoes settings. */
 static final class PendingRestore {
  final Context app;final AtomicFile file;final String token,previousIcon;final Snapshot prior;final Set<String> absent;
  PendingRestore(Context app,String token,String previousIcon,Snapshot prior,Set<String> absent){
   this.app=app.getApplicationContext();file=new AtomicFile(new java.io.File(app.getFilesDir(),"settings_archive_restore.pending"));this.token=token;this.previousIcon=previousIcon;this.prior=prior;this.absent=Collections.unmodifiableSet(new HashSet<>(absent));
  }
  static PendingRestore prepare(Context app,PreferenceRestore change)throws IOException {
   Map<String,Object> prior=new LinkedHashMap<>();Set<String> absent=new HashSet<>();
   for(String key:change.snapshot.values.keySet())if(change.old.containsKey(key))prior.put(key,change.old.get(key));else absent.add(key);
   if(prior.isEmpty()){
    prior.put("dark",change.old.containsKey("dark")?change.old.get("dark"):false);
    if(!change.old.containsKey("dark"))absent.add("dark");
   }
   PendingRestore pending=new PendingRestore(app,UUID.randomUUID().toString(),change.previous,new Snapshot(prior),absent);pending.write();return pending;
  }
  void write()throws IOException {
   String settings=new String(encode(prior),StandardCharsets.UTF_8);ByteArrayOutputStream bytes=new ByteArrayOutputStream();
   try(JsonWriter json=new JsonWriter(new OutputStreamWriter(bytes,StandardCharsets.UTF_8))){
    json.beginObject();json.name("token").value(token);json.name("previousIcon").value(previousIcon);json.name("prior").value(settings);
    json.name("absent").beginArray();for(String key:absent)json.value(key);json.endArray();json.endObject();
   }
   FileOutputStream output=null;
   try{
    output=file.startWrite();output.write(bytes.toByteArray());output.getFD().sync();file.finishWrite(output);output=null;
    // AtomicFile logs a failed final rename instead of throwing. Never change either
    // store until the published journal can be read back in its entirety.
    PendingRestore saved=read(app);
    if(saved==null||!token.equals(saved.token)||!previousIcon.equals(saved.previousIcon)||
      !prior.values.equals(saved.prior.values)||!absent.equals(saved.absent))throw new IOException("Private restore journal was not published");
   }
   catch(IOException|RuntimeException failure){if(output!=null)file.failWrite(output);throw new IOException("Cannot save private restore journal",failure);}
  }
  void clear()throws IOException {
   file.delete();if(hasPendingRestore(app))throw new IOException("Cannot clear private restore journal");
  }
  static PendingRestore read(Context app)throws IOException {
   AtomicFile file=new AtomicFile(new java.io.File(app.getFilesDir(),"settings_archive_restore.pending"));
   if(!file.getBaseFile().exists()&&!new java.io.File(file.getBaseFile()+".bak").exists())return null;
   byte[] bytes;
   try(FileInputStream input=file.openRead()){
    ByteArrayOutputStream result=new ByteArrayOutputStream();byte[] block=new byte[8192];int n;
    while((n=input.read(block))!=-1){if(result.size()+n>MAX_SETTINGS_BYTES*3)throw new InvalidBackup();result.write(block,0,n);}bytes=result.toByteArray();
   }
   try(JsonReader json=new JsonReader(new InputStreamReader(new ByteArrayInputStream(bytes),StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)))){
    json.setLenient(false);json.beginObject();Set<String> names=new HashSet<>(),absent=new HashSet<>();String token=null,icon=null;Snapshot prior=null;
    while(json.hasNext()){
     String name=json.nextName();if(!names.add(name))throw new InvalidBackup();
     switch(name){
      case "token":if(json.peek()!=JsonToken.STRING)throw new InvalidBackup();token=json.nextString();if(!token.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))throw new InvalidBackup();break;
      case "previousIcon":if(json.peek()!=JsonToken.STRING)throw new InvalidBackup();icon=json.nextString();if(!ENUMS.get("launcher_icon").contains(icon))throw new InvalidBackup();break;
      case "prior":
       if(json.peek()!=JsonToken.STRING)throw new InvalidBackup();String saved=json.nextString();if(!validText(saved,MAX_SETTINGS_BYTES*2))throw new InvalidBackup();
       prior=decode(saved.getBytes(StandardCharsets.UTF_8));if(prior.archive!=null)throw new InvalidBackup();break;
      case "absent":
       if(json.peek()!=JsonToken.BEGIN_ARRAY)throw new InvalidBackup();json.beginArray();
       while(json.hasNext()){
        if(absent.size()>=MAX_ENTRIES||json.peek()!=JsonToken.STRING)throw new InvalidBackup();String key=json.nextString();
        if(!known(key)||!validText(key,256)||!absent.add(key))throw new InvalidBackup();
       }
       json.endArray();break;
      default:throw new InvalidBackup();
     }
    }
    json.endObject();if(json.peek()!=JsonToken.END_DOCUMENT||names.size()!=4||prior==null||token==null||icon==null)throw new InvalidBackup();
    return new PendingRestore(app,token,icon,prior,absent);
   }catch(IllegalStateException|NumberFormatException failure){throw new InvalidBackup();}
  }
 }
 /** One disk commit; failure rolls back both memory prefs and launcher selection. No pruning side effects. */
 @android.annotation.SuppressLint("ApplySharedPref") // Background transaction needs a durable result, including rollback.
 static synchronized boolean restore(SharedPreferences prefs,Snapshot snapshot,IconChange icons)throws IOException {
  validate(snapshot.values);if(snapshot.archive!=null)throw new InvalidBackup();
  return new PreferenceRestore(prefs,snapshot,icons).apply();
 }
 private static final class PreferenceRestore implements CallArchive.RestoreAction {
  final SharedPreferences prefs;final Snapshot snapshot;final IconChange icons;final Map<String,?> old;
  final String previous;final Object next;final boolean iconChanged;
  boolean touched,iconApplied;
  PreferenceRestore(SharedPreferences prefs,Snapshot snapshot,IconChange icons){
   this.prefs=prefs;this.snapshot=snapshot;this.icons=icons;old=new HashMap<>(prefs.getAll());Object previousValue=old.get("launcher_icon");
   previous=previousValue instanceof String&&ENUMS.get("launcher_icon").contains(previousValue)?(String)previousValue:"crest";
   next=snapshot.values.get("launcher_icon");iconChanged=next!=null&&!next.equals(previous);
  }
  @Override @android.annotation.SuppressLint("ApplySharedPref") public boolean apply(){
   if(iconChanged){
    iconApplied=true;
    try{if(!icons.apply((String)next)){rollback();return false;}}
    catch(RuntimeException failure){rollback();return false;}
   }
   try{
    SharedPreferences.Editor edit=prefs.edit();for(Map.Entry<String,Object> entry:snapshot.values.entrySet())put(edit,entry.getKey(),entry.getValue());
    touched=true;
    if(edit.commit())return true;
   }catch(RuntimeException ignored){}
   rollback();return false;
  }
  @android.annotation.SuppressLint("ApplySharedPref") public void rollback(){
   // commit(false) can change memory. Restore exactly the touched keys, retaining unrelated writes.
   try{
    SharedPreferences.Editor rollback=prefs.edit();
    for(String key:snapshot.values.keySet())if(old.containsKey(key))put(rollback,key,old.get(key));else rollback.remove(key);
    if(rollback.commit())touched=false;
   }catch(RuntimeException ignored){}
   if(iconApplied)try{if(icons.apply(previous))iconApplied=false;}catch(RuntimeException ignored){}
  }
 }
 @SuppressWarnings("unchecked") private static void put(SharedPreferences.Editor edit,String key,Object value){
  if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);
  else if(value instanceof Integer)edit.putInt(key,(Integer)value);
  else if(value instanceof String)edit.putString(key,(String)value);
  else if(value instanceof Set)edit.putStringSet(key,new HashSet<>((Set<String>)value));
  else throw new IllegalArgumentException("Unsupported settings value");
 }
 static SharedPreferences preferences(Context context){return context.getSharedPreferences("da_caller",Context.MODE_PRIVATE);}
 private SettingsBackup(){}
}
