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
 private static final Pattern CALL=Pattern.compile("^(?:התקשר(?:י)?(?:\\s+אל)?|חייג(?:י)?(?:\\s+אל)?|call|dial)\\s+(.+)$");
 private static final Pattern CONTACT=Pattern.compile("^(?:חפש(?:י)?(?:\\s+איש קשר)?|מצא(?:י)?(?:\\s+איש קשר)?|find contact|search contacts?|find)\\s+(.+)$");
 private static final Pattern BUSINESS=Pattern.compile("^(?:חפש(?:י)? עסק|מצא(?:י)? עסק|בתי עסק|find business|search businesses)\\s+(.+)$");
 private static final Pattern NEGATION=Pattern.compile("(?:^|\\s)(?:לא|don't|dont|not|never)(?:\\s|$)");
 private LocalAssistant(){}
 static Intent parse(String raw){
  if(raw==null||raw.length()>512||raw.matches("(?s).*[\\p{Cc}\\p{Cf};!?].*"))return unknown();
  String query=Normalizer.normalize(raw,Normalizer.Form.NFKC)
   .replaceAll("[\\u0591-\\u05BD\\u05BF-\\u05C7]","")
   .replaceAll("[\\p{Z}\\s]+"," ").trim().toLowerCase(Locale.ROOT);
  query=PREFIX.matcher(query).replaceFirst("").trim();
  if(query.isEmpty()||NEGATION.matcher(query).find()||query.matches(".*[;!?\\r\\n].*"))return unknown();
  if(query.matches("(?:שיחות שלא נענו|שיחות שלא עניתי|שיחות שלא עניתי להן|מי חיפש אותי|missed calls|show missed calls)"))
   return new Intent(Kind.MISSED_CALLS,"");
  if(query.matches("(?:שיחות אחרונות|יומן שיחות|recent calls|call history|show recent calls)"))
   return new Intent(Kind.RECENTS,"");
  if(query.matches("(?:חזור למתקשר האחרון|חזור לשיחה האחרונה|call back last caller|return last call)"))
   return new Intent(Kind.CALLBACK_PROPOSAL,"");
  Matcher match=BUSINESS.matcher(query);if(match.matches())return target(Kind.BUSINESS_SEARCH,match.group(1));
  match=CALL.matcher(query);if(match.matches())return target(Kind.CALL_PROPOSAL,match.group(1));
  match=CONTACT.matcher(query);if(match.matches())return target(Kind.CONTACT_SEARCH,match.group(1));
  return unknown();
 }
 private static Intent target(Kind kind,String value){
  String target=value.trim();
  if(target.isEmpty()||target.length()>120||target.matches(".*(?:\\s+(?:ואז|ואחר כך|and then|then)\\s+).*"))return unknown();
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
 private static Intent unknown(){return new Intent(Kind.UNKNOWN,"");}
}
