package com.dacaller.app;

import org.junit.Test;
import static org.junit.Assert.*;

public final class LocalAssistantTest {
 private void intent(String input,LocalAssistant.Kind kind,String query,boolean confirmation){
  LocalAssistant.Intent result=LocalAssistant.parse(input);
  assertEquals(kind,result.kind);assertEquals(query,result.query);
  assertEquals(confirmation,result.requiresConfirmation());
 }
 @Test public void hebrewAndEnglishCallProposalsNeverExecute(){
  intent("התקשר אל דוד",LocalAssistant.Kind.CALL_PROPOSAL,"דוד",true);
  intent("בבקשה חייג 0501234567",LocalAssistant.Kind.CALL_PROPOSAL,"0501234567",true);
  intent("Please CALL Dana",LocalAssistant.Kind.CALL_PROPOSAL,"dana",true);
  intent("חזור למתקשר האחרון",LocalAssistant.Kind.CALLBACK_PROPOSAL,"",true);
 }
 @Test public void searchAndHistoryAreReadOnlyIntents(){
  intent("חפש איש קשר דנה",LocalAssistant.Kind.CONTACT_SEARCH,"דנה",false);
  intent("חפש עסק חשמלאי",LocalAssistant.Kind.BUSINESS_SEARCH,"חשמלאי",false);
  intent("Find business plumber",LocalAssistant.Kind.BUSINESS_SEARCH,"plumber",false);
  intent("שיחות שלא נענו",LocalAssistant.Kind.MISSED_CALLS,"",false);
  intent("מי חיפש אותי",LocalAssistant.Kind.MISSED_CALLS,"",false);
  intent("recent calls",LocalAssistant.Kind.RECENTS,"",false);
 }
 @Test public void naturalSearchPrefixesKeepTheirIntendedReadOnlyScope(){
  intent("Search for contact Dana",LocalAssistant.Kind.CONTACT_SEARCH,"dana",false);
  intent("find contacts Dana",LocalAssistant.Kind.CONTACT_SEARCH,"dana",false);
  intent("search for Dana",LocalAssistant.Kind.CONTACT_SEARCH,"dana",false);
  intent("search business plumber",LocalAssistant.Kind.BUSINESS_SEARCH,"plumber",false);
  intent("find businesses plumber",LocalAssistant.Kind.BUSINESS_SEARCH,"plumber",false);
  intent("search for businesses plumber",LocalAssistant.Kind.BUSINESS_SEARCH,"plumber",false);
  intent("הצג שיחות שלא נענו",LocalAssistant.Kind.MISSED_CALLS,"",false);
  intent("show my recent calls",LocalAssistant.Kind.RECENTS,"",false);
  intent("call to Dana",LocalAssistant.Kind.CALL_PROPOSAL,"dana",true);
  intent("dial contact Dana",LocalAssistant.Kind.CALL_PROPOSAL,"dana",true);
  intent("call back the last caller",LocalAssistant.Kind.CALLBACK_PROPOSAL,"",true);
 }
 @Test public void missingTargetsCannotFallBackToGenericContactSearch(){
  for(String input:new String[]{"find contact","find business","search contacts","search for businesses","חפש איש קשר","מצאי עסק","call to","dial contact","call back"})
   intent(input,LocalAssistant.Kind.UNKNOWN,"",false);
  intent("find contact Contacts",LocalAssistant.Kind.CONTACT_SEARCH,"contacts",false);
 }
 @Test public void contactNamesUseTheSameHebrewAndUnicodeNormalizationAsCommands(){
  LocalAssistant.Intent request=LocalAssistant.parse("בבקשה חפש איש קשר דנה לוי");
  assertTrue(LocalAssistant.matchesContact("דָּנָה לֵוִי","0501234567",request.query));
  assertTrue(LocalAssistant.matchesContact("Ｄａｎａ\u00a0  Levi","0501234567","Dana Levi"));
  assertTrue(LocalAssistant.matchesContact("DANA Levi",null,"dana"));
  assertFalse(LocalAssistant.matchesContact("דינה לוי","0501234567",request.query));
  assertFalse(LocalAssistant.matchesContact("Dana",null,"\u202edana"));
  assertFalse(LocalAssistant.matchesContact("Dana",null,"   "));
  assertFalse(LocalAssistant.matchesContact(null,null,"dana"));
 }
 @Test public void formattedPhoneSearchDoesNotRequireIdenticalProviderPunctuation(){
  assertTrue(LocalAssistant.matchesContact("Desk","050-123 4567","050123"));
  assertTrue(LocalAssistant.matchesContact("Desk"," (050) 123-4567 ","050 (123)"));
  assertTrue(LocalAssistant.matchesContact("Desk","0501234567","(050) 123"));
  assertTrue(LocalAssistant.matchesContact("Desk","０５０-１２３４５６７","050123"));
  assertFalse(LocalAssistant.matchesContact("Desk","0501234567","Dana050"));
  assertFalse(LocalAssistant.matchesContact("Desk","*123#","123"));
  assertFalse(LocalAssistant.matchesContact("Desk","0501234567,12","050123"));
  assertFalse(LocalAssistant.matchesContact("Desk","050\u202e1234567","050123"));
 }
 @Test public void recognizedIsraeliSearchAliasesNeverReplaceTheActualDialIdentity(){
  String international="+972 (50) 123-4567";
  assertTrue(LocalAssistant.matchesContact("Dana",international,"050123"));
  assertTrue(LocalAssistant.matchesContact("Dana","(+972) 50-1234567","050123"));
  assertTrue(LocalAssistant.matchesContact("Dana","0501234567","+97250123"));
  assertTrue(LocalAssistant.matchesContact("Dana","0501234567","00972501234567"));
  assertTrue(LocalAssistant.matchesContact("Dana","00972 50 1234567","050123"));
  assertEquals("+972501234567",LocalAssistant.safeNumber(international));
  assertFalse(LocalAssistant.matchesContact("Dana","+44 20 11112222","0501234567"));
  assertFalse(LocalAssistant.matchesContact("Dana","972123","0123"));
  assertFalse(LocalAssistant.matchesContact("Dana","050+1234567","050123"));
  // Read-only normalization is not permission to transform arbitrary dial input.
  assertNull(LocalAssistant.safeNumber("０５０-１２３４５６７"));
  assertTrue(LocalAssistant.matchesContact("Service","*123#","service"));
  assertNull(LocalAssistant.safeNumber("*123#"));
 }
 @Test public void compatibilitySearchCannotManufactureADirectDialIdentity(){
  for(String command:new String[]{"call ０５０-１２３４５６７","call ①②③","call ¹²³",
   "call ＋972501234567","call ＋９７２５０１２３４５６７","call （050）1234567"}){
   LocalAssistant.Intent result=LocalAssistant.parse(command);
   assertEquals(command,LocalAssistant.Kind.UNKNOWN,result.kind);
   assertFalse(command,result.requiresConfirmation());
   assertNull(command,LocalAssistant.safeNumber(result.query));
  }
  for(String command:new String[]{"call 050-123 4567","Please CALL (050) 123-4567"}){
   LocalAssistant.Intent result=LocalAssistant.parse(command);
   assertEquals(LocalAssistant.Kind.CALL_PROPOSAL,result.kind);
   assertEquals("0501234567",LocalAssistant.safeNumber(result.query));
   assertTrue(result.requiresConfirmation());
  }
  LocalAssistant.Intent international=LocalAssistant.parse("חייג +972 (50) 123-4567");
  assertEquals(LocalAssistant.Kind.CALL_PROPOSAL,international.kind);
  assertEquals("+972501234567",LocalAssistant.safeNumber(international.query));
  // Read-only/name search may still normalize; provider numbers remain raw and
  // independently pass safeNumber before their separate confirmation flow.
  intent("find contact ０５０１２３４５６７",LocalAssistant.Kind.CONTACT_SEARCH,"0501234567",false);
  intent("call Ｄａｎａ",LocalAssistant.Kind.CALL_PROPOSAL,"dana",true);
  intent("call דָּנָה",LocalAssistant.Kind.CALL_PROPOSAL,"דנה",true);
 }
 @Test public void ordinaryPhoneNumbersOnly(){
  assertEquals("0501234567",LocalAssistant.safeNumber("050-123 4567"));
  assertEquals("+972501234567",LocalAssistant.safeNumber("+972 (50) 1234567"));
  for(String invalid:new String[]{null,"","-1","*123#","0501234567,12","0501234567;12","number 0501234567","050+1234567","0501234567 / 0521234567"})assertNull(invalid,LocalAssistant.safeNumber(invalid));
 }
 @Test public void rejectNegatedCompoundAndUnsafeInput(){
  for(String input:new String[]{null,"","call","אל תתקשר לדוד","לא להתקשר לדוד","don't call Dana","call Dana and then Bob","חייג דוד ואז משה","call Dana; call Bob","call Dana\nBob","call *123#","call \u202e123","call Dana!",
   "call Dana don’t dial Bob","call Dana and call Bob","חייג דוד וחייג משה","call Dana then","call Dana；call Bob","call ＊123＃"})
   intent(input,LocalAssistant.Kind.UNKNOWN,"",false);
  intent("call Dana and Sons",LocalAssistant.Kind.CALL_PROPOSAL,"dana and sons",true);
 }
}
