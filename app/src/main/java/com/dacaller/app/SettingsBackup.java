package com.dacaller.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.JsonWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
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

/** Portable, bounded settings only. Never reads or writes the contacts/call-log/archive databases. */
final class SettingsBackup {
 static final int MAX_BYTES=2*1024*1024;
 private static final int MAX_ENTRIES=4096,MAX_FAVORITES=10000;
 private static final String FORMAT="DA_CALLER_SETTINGS",PACKAGE="com.dacaller.app";
 private static final Map<String,Object> DEFAULTS=new LinkedHashMap<>();
 private static final Map<String,Set<String>> ENUMS=new HashMap<>();
 static {
  bool("dark",false);bool("amoled",false);bool("recents_green",false);bool("show_stats",true);
  bool("haptics",true);bool("favorites_expanded",true);bool("stats_expanded",false);
  option("language","he","he","en");option("accent","gold","gold","silver");
  option("call_theme","pixel",CallThemes.IDS);
  option("answer_mode","ios","right","left","vertical","ios");
  option("archive_extra",CallArchive.OFF,CallArchive.OPTIONS);
  option("swipe_direction","right","right","left","off");
  option("start_page","recents","recents","contacts","favorites");
  option("avatar_shape","circle","circle","rounded_square","square","hexagon");
  option("launcher_icon","crest","crest","orbit","monogram","prism","wave");
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
  Snapshot(Map<String,Object> values){
   LinkedHashMap<String,Object> copy=new LinkedHashMap<>();
   for(Map.Entry<String,Object> item:values.entrySet())copy.put(item.getKey(),item.getValue() instanceof Set?
     Collections.unmodifiableSet(new HashSet<>((Set<?>)item.getValue())):item.getValue());
   this.values=Collections.unmodifiableMap(copy);
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
 static byte[] encode(Snapshot snapshot)throws IOException {
  validate(snapshot.values);
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();
  try(JsonWriter json=new JsonWriter(new OutputStreamWriter(bytes,StandardCharsets.UTF_8))){
   json.setIndent("  ");json.beginObject();
   json.name("format").value(FORMAT);json.name("version").value(1);json.name("package").value(PACKAGE);
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
   json.endObject();json.endObject();
  }
  if(bytes.size()>MAX_BYTES)throw new InvalidBackup();return bytes.toByteArray();
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
   while(json.hasNext()){
    String name=json.nextName();if(!names.add(name))throw new InvalidBackup();
    switch(name){
     case "format":if(json.peek()!=JsonToken.STRING||!FORMAT.equals(json.nextString()))throw new InvalidBackup();break;
     case "package":if(json.peek()!=JsonToken.STRING||!PACKAGE.equals(json.nextString()))throw new InvalidBackup();break;
     case "version":if(json.peek()!=JsonToken.NUMBER||!"1".equals(json.nextString()))throw new InvalidBackup();break;
     case "createdAt":if(json.peek()!=JsonToken.STRING||json.nextString().length()>64)throw new InvalidBackup();break;
     case "settings":settings=readSettings(json);break;
     default:throw new InvalidBackup();
    }
   }
   json.endObject();if(json.peek()!=JsonToken.END_DOCUMENT||names.size()!=5||settings==null)throw new InvalidBackup();
   validate(settings);return new Snapshot(settings);
  }catch(IllegalStateException|NumberFormatException error){throw new InvalidBackup();}
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
   String key=item.getKey();Object value=item.getValue();if(!known(key)||value==null)throw new InvalidBackup();
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
   }
   if(key.equals("favorites")){
    if(!(value instanceof Set)||((Set<?>)value).size()>MAX_FAVORITES)throw new InvalidBackup();
    for(Object favorite:(Set<?>)value)if(!(favorite instanceof String)||((String)favorite).isEmpty()||((String)favorite).length()>160)throw new InvalidBackup();
   }
  }
 }
 interface IconChange {boolean apply(String id);}
 /** One disk commit; failure rolls back both memory prefs and launcher selection. No pruning side effects. */
 @android.annotation.SuppressLint("ApplySharedPref") // Background transaction needs a durable result, including rollback.
 static synchronized boolean restore(SharedPreferences prefs,Snapshot snapshot,IconChange icons)throws IOException {
  validate(snapshot.values);Map<String,?> old=prefs.getAll();String previous=(String)old.get("launcher_icon");
  if(previous==null||!ENUMS.get("launcher_icon").contains(previous))previous="crest";
  Object next=snapshot.values.get("launcher_icon");
  boolean iconChanged=next!=null&&!next.equals(previous);
  if(iconChanged&&!icons.apply((String)next))return false;
  boolean committed;
  try{SharedPreferences.Editor edit=prefs.edit();for(Map.Entry<String,Object> entry:snapshot.values.entrySet())put(edit,entry.getKey(),entry.getValue());committed=edit.commit();}
  catch(RuntimeException failure){committed=false;}
  if(committed)return true;
  // Android commit(false) can have changed its in-memory map: restore exactly the touched keys.
  try{
   SharedPreferences.Editor rollback=prefs.edit();
   for(String key:snapshot.values.keySet())if(old.containsKey(key))put(rollback,key,old.get(key));else rollback.remove(key);
   rollback.commit();
  }catch(RuntimeException ignored){}
  if(iconChanged)icons.apply(previous);return false;
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
