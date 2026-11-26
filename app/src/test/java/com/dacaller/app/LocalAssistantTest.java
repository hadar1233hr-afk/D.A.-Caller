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
 @Test public void ordinaryPhoneNumbersOnly(){
  assertEquals("0501234567",LocalAssistant.safeNumber("050-123 4567"));
  assertEquals("+972501234567",LocalAssistant.safeNumber("+972 (50) 1234567"));
  for(String invalid:new String[]{null,"","-1","*123#","0501234567,12","0501234567;12","number 0501234567","050+1234567","0501234567 / 0521234567"})assertNull(invalid,LocalAssistant.safeNumber(invalid));
 }
 @Test public void rejectNegatedCompoundAndUnsafeInput(){
  for(String input:new String[]{null,"","call","אל תתקשר לדוד","לא להתקשר לדוד","don't call Dana","call Dana and then Bob","חייג דוד ואז משה","call Dana; call Bob","call Dana\nBob","call *123#","call \u202e123","call Dana!"})
   intent(input,LocalAssistant.Kind.UNKNOWN,"",false);
 }
}
