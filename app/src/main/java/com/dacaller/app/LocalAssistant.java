package com.dacaller.app;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Offline intent recognition. Returns proposals only; never reads contacts or executes an action. */
final class LocalAssistant {
 enum Kind { CONTACT_SEARCH, BUSINESS_SEARCH, CALL_PROPOSAL, MISSED_CALLS, RECENTS, CALLBACK_PROPOSAL, UNKNOWN }
 static final class Intent {
  final Kind kind;
  final String query;
  Intent(Kind kind,String query){this.kind=kind;this.query=query;}
  boolean requiresConfirmation(){return kind==Kind.CALL_PROPOSAL||kind==Kind.CALLBACK_PROPOSAL;}
 }
 private static final Pattern PREFIX=Pattern.compile("^(?:בבקשה\\s+|please\\s+)",Pattern.CASE_INSENSITIVE);
 private static final Pattern CALL=Pattern.compile("^(?:התקשר(?:י)?(?:\\s+אל)?|חייג(?:י)?(?:\\s+אל)?|(?:call|dial)(?:\\s+(?:to|contact))?)\\s+(.+)$");
 private static final Pattern CONTACT=Pattern.compile("^(?:חפש(?:י)?(?:\\s+איש קשר)?|מצא(?:י)?(?:\\s+איש קשר)?|(?:find|search)(?:\\s+for)?(?:\\s+contacts?)?)\\s+(.+)$");
 private static final Pattern BUSINESS=Pattern.compile("^(?:חפש(?:י)? עסק|מצא(?:י)? עסק|בתי עסק|(?:find|search)(?:\\s+for)?\\s+business(?:es)?)\\s+(.+)$");
 private static final Pattern NEGATION=Pattern.compile("(?:^|\\s)(?:לא|don['’]?t|not|never)(?:\\s|$)");
 private static final Pattern MULTIPLE_COMMANDS=Pattern.compile("(?:^|\\s)(?:ואז|ואחר כך|and then|then)(?:\\s|$)|\\s+(?:and\\s+(?:call|dial|find|search)\\s+|ו(?:חייג(?:י)?|התקשר(?:י)?|חפש(?:י)?|מצא(?:י)?)\\s+)");
 private static final Pattern HEBREW_MARKS=Pattern.compile("[\\u0591-\\u05BD\\u05BF\\u05C1\\u05C2\\u05C4\\u05C5\\u05C7]");
 private static final Pattern MISSING_TARGET=Pattern.compile("(?:find|search)(?:\\s+for)?\\s+(?:contacts?|business(?:es)?)|(?:חפש(?:י)?|מצא(?:י)?)\\s+(?:איש קשר|עסק)|(?:call|dial)\\s+(?:to|contact|back)");
 private LocalAssistant(){}
 static Intent parse(String raw){
  if(raw==null||raw.length()>512||raw.matches("(?s).*[\\p{Cc}\\p{Cf};!?].*"))return unknown();
  String query=fold(raw);
  query=PREFIX.matcher(query).replaceFirst("").trim();
  if(query.isEmpty()||NEGATION.matcher(query).find()||query.matches(".*[;!?\\r\\n].*")||MISSING_TARGET.matcher(query).matches())return unknown();
  if(query.matches("(?:שיחות שלא נענו|שיחות שלא עניתי|שיחות שלא עניתי להן|מי חיפש אותי|הצג שיחות שלא נענו|missed calls|show (?:my )?missed calls)"))
   return new Intent(Kind.MISSED_CALLS,"");
  if(query.matches("(?:שיחות אחרונות|יומן שיחות|הצג שיחות אחרונות|recent calls|call history|show (?:my )?recent calls)"))
   return new Intent(Kind.RECENTS,"");
  if(query.matches("(?:חזור למתקשר האחרון|חזור לשיחה האחרונה|call back (?:the )?last caller|return (?:the )?last call)"))
   return new Intent(Kind.CALLBACK_PROPOSAL,"");
  Matcher match=BUSINESS.matcher(query);if(match.matches())return target(Kind.BUSINESS_SEARCH,match.group(1));
  match=CALL.matcher(query);if(match.matches()){
   String value=match.group(1);
   // Compatibility folding is useful for search, but must not manufacture a
   // direct dial identity that the original ordinary-number grammar rejected.
   // Conservatively require an NFKC-stable command for numeric proposals.
   if(safeNumber(value)!=null&&!Normalizer.isNormalized(raw,Normalizer.Form.NFKC))return unknown();
   return target(Kind.CALL_PROPOSAL,value);
  }
  match=CONTACT.matcher(query);if(match.matches())return target(Kind.CONTACT_SEARCH,match.group(1));
  return unknown();
 }
 private static Intent target(Kind kind,String value){
  String target=value.trim();
  if(target.isEmpty()||target.length()>120||MULTIPLE_COMMANDS.matcher(target).find())return unknown();
  // Reject controls, bidi overrides and service-code characters; never silently transform them into a number.
  if(target.matches(".*[\\p{Cc}\\p{Cf}*#].*"))return unknown();
  return new Intent(kind,target);
 }
 /** Accept ordinary phone-number formatting only, never MMI, post-dial or arbitrary text. */
 static String safeNumber(String raw){
  if(raw==null||raw.length()>64||!raw.matches("[+0-9() .\\-]+"))return null;
  String dial=raw.replaceAll("[() .\\-]","");
  return dial.matches("\\+?[0-9]{3,20}")?dial:null;
 }
 /** Search normalization only. The original provider number is still shown and
  * must independently pass safeNumber before any explicit call proposal. */
 static boolean matchesContact(String name,String number,String query){
  if(query==null||query.length()>120||query.matches("(?s).*[\\p{Cc}\\p{Cf}].*"))return false;
  String needle=fold(query);if(needle.isEmpty())return false;
  if(name!=null&&fold(name).contains(needle))return true;
  String numberDigits=searchDigits(number),queryDigits=searchDigits(needle);
  if(numberDigits.isEmpty()||queryDigits.isEmpty())return false;
  String internationalQuery=queryDigits.startsWith("00972")?queryDigits.substring(2):queryDigits;
  if(numberDigits.contains(queryDigits)||numberDigits.contains(internationalQuery))return true;
  // Contacts can store the same Israeli identity nationally or internationally.
  // Only complete recognized Israeli forms get an alias, never another country
  // or an arbitrary suffix; this does not replace the stored/dialed number.
  String national=israeliNational(numberDigits);
  return !national.isEmpty()&&(national.contains(internationalQuery)||("972"+national.substring(1)).contains(internationalQuery));
 }
 private static String fold(String value){
  return HEBREW_MARKS.matcher(Normalizer.normalize(value,Normalizer.Form.NFKC)).replaceAll("")
   .replaceAll("[\\p{Z}\\s]+"," ").trim().toLowerCase(Locale.ROOT);
 }
 private static String searchDigits(String value){
  if(value==null||value.length()>64)return "";
  String normalized=Normalizer.normalize(value,Normalizer.Form.NFKC).trim();
  if(!normalized.matches("[+0-9() .\\-]+"))return "";
  String formatted=normalized.replaceAll("[() .\\-]","");
  if(!formatted.matches("\\+?[0-9]{1,20}"))return "";
  return formatted.charAt(0)=='+'?formatted.substring(1):formatted;
 }
 private static String israeliNational(String digits){
  if(digits.matches("0[1-9][0-9]{7,8}"))return digits;
  if(digits.matches("972[1-9][0-9]{7,8}"))return "0"+digits.substring(3);
  if(digits.matches("00972[1-9][0-9]{7,8}"))return "0"+digits.substring(5);
  return "";
 }
 private static Intent unknown(){return new Intent(Kind.UNKNOWN,"");}
}
