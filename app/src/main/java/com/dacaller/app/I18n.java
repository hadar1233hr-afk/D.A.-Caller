package com.dacaller.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.Map;

/** Small offline English/Hebrew language catalog, no cloud or runtime dependency. */
final class I18n {
 private static final Map<String,String> english=new HashMap<>();
 static{
  put("שיחות","Calls");put("אנשי קשר","Contacts");put("מועדפים","Favorites");put("הגדרות","Settings");
  put("שיחות אחרונות","Recents");put("כל השיחות","Recent calls");put("ארכיון שיחות","Call archive");
  put("שיחות שנשמרו לאחר שנעלמו מיומן המכשיר","Saved calls no longer in the system log");
  put("טוען נתוני ארכיון…","Loading archive…");
  put("אין עדיין רשומות בארכיון","No archived calls yet");
  put("לא נענתה","Missed");put("נדחתה","Rejected");put("נחסמה","Blocked");
  put("יוצאת","Outgoing");put("נכנסת","Incoming");
  put("יוצאת · ללא זמן שיחה","Outgoing · no call duration");
  put("שיחה","Call");put("היום","Today");put("השבוע","Week");put("החודש","Month");put("השנה","Year");
  put("דק׳","min");put("דקות","minutes");
  put("חייג","Call");put("סגור","Close");put("רענון יומן השיחות","Refresh call history");
  put("יומן השיחות דורש הרשאה","Call log permission required");
  put("אפשר גישה לשיחות","Allow call log access");
  put("כדי לראות אנשי קשר נדרשת הרשאה","Contacts permission required");
  put("אפשר גישה לאנשי קשר","Allow contacts access");
  put("חיוג מיידי למועדפים","Quick-call favorites");put("ניהול ★","Manage ★");
  put("עדיין אין מועדפים. סמן ★ באנשי קשר.","No favorites yet. Star a contact to add one.");
  put("חיפוש שם או מספר","Search name or number");
  put("הוספת איש קשר חדש","Add new contact");
  put("אנשי הקשר נטענים… אפשר גם לרענן.","Loading contacts… You can refresh too.");
  put("אין שיחות להצגה","No calls yet");put("מספר לא מזוהה","Unknown number");
  put("מספר חסוי","Private number");
  put("עריכת איש קשר","Edit contact");put("איש קשר חדש","New contact");
  put("הוספה לאיש קשר קיים","Add to existing contact");
  put("כרטיס איש קשר","Contact details");put("שמירה","Save");
  put("עריכה","Edit");put("התקשר","Call");put("תמונה","Photo");
  put("שיחה נכנסת","Incoming call");put("שיחה פעילה","Active call");
  put("מחייג…","Dialing…");put("מתחבר…","Connecting…");put("בהמתנה","On hold");
  put("מענה","Answer");put("ענה","Answer");put("דחה","Decline");put("סיום שיחה","End call");
  put("החלק למעלה כדי לענות","Swipe up to answer");
  put("↑  החלק למעלה כדי לענות  ↓","↑ Swipe up to answer ↓");
  put("←  החלק שמאלה כדי לענות","← Swipe left to answer");
  put("החלק ימינה כדי לענות  →","Swipe right to answer →");
  put("החלק בכרטיס כדי לענות","Swipe the panel to answer");
  put("השתק","Mute");put("בטל השתקה","Unmute");
  put("רמקול","Speaker");put("אפרכסת","Earpiece");
  put("מקשים","Keypad");put("המתן","Hold");put("המשך","Resume");
  put("אין אוזניית Bluetooth זמינה","No Bluetooth headset available");
  put("המתנה לא זמינה","Call hold unavailable");
  put("הוסף שיחה","Add call");put("הערה","Note");
  put("הערה לשיחה","Call note");put("כתוב הערה","Write a note");
  put("מקשי שיחה","In-call keypad");
  put("מראה","Appearance");put("שפה","Language");put("עברית","Hebrew");put("אנגלית","English");
  put("בהיר","Light");put("כהה","Dark");put("שחור AMOLED","AMOLED black");
  put("ערכות מסך שיחה","Call screen themes");
  put("אופן מענה לשיחה","Answer call gesture");
  put("החלקה ימינה למענה","Swipe right to answer");
  put("החלקה שמאלה למענה","Swipe left to answer");
  put("למעלה למענה, למטה לדחייה","Up to answer, down to decline");
  put("iOS · ימינה למענה, הקשה לדחייה","iOS · swipe right, tap decline");
  put("ארכיון אישי","Personal archive");
  put("שמירה מעבר ליומן אנדרואיד","Keep calls after the system log removes them");
  put("כבוי","Off");put("חודש נוסף","Extra month");put("3 חודשים נוספים","Extra 3 months");
  put("חצי שנה נוספת","Extra 6 months");put("שנה נוספת","Extra year");
  put("ללא הגבלת זמן","Keep forever");
  put("מחיקת ארכיון","Delete archive");
  put("מחיקת כל שיחות הארכיון?","Delete all archived calls?");
  put("הנתונים יימחקו מהמכשיר ולא ניתן יהיה לשחזרם.","Archived call data will be permanently deleted.");
  put("ביטול","Cancel");put("מחק","Delete");put("נמחק","Deleted");
  put("מחוות","Gestures");put("החלקה לחיוג","Swipe to call");
  put("ימינה","Right");put("שמאלה","Left");
  put("צבע לחצן חיוג ביומן","Call button color in recents");
  put("זהב","Gold");put("ירוק","Green");
  put("צלילים ורטט","Haptics");put("רטט בלחיצה","Vibrate on press");
  put("עוצמת רטט","Vibration intensity");put("חלש","Light");put("בינוני","Medium");
  put("חזק","Strong");
  put("ניהול כפתורים","Call buttons");put("כפתורים בזמן שיחה","In-call controls");
  put("מצב המערכת","Phone integration");put("הגדר כחייגן ברירת מחדל","Set as default phone app");
  put("פרטי שיחה","Call details");put("הצג מספר","Show number");
  put("אין איש קשר תואם","Contact not found");put("ערכות נושא","Themes");
  put("התאמה אישית","Customization");
  put("הצגת סיכום פעילות","Call summary strip");
  put("מסך פתיחה","Startup screen");put("מסך השיחה","Call screen");
  put("כל ההגדרות נשמרות במכשיר","All preferences stay on your device");
 }
 private static void put(String he,String en){english.put(he,en);}
 static boolean isEnglish(Context ctx){
  SharedPreferences p=ctx.getSharedPreferences("da_caller",Context.MODE_PRIVATE);
  return "en".equals(p.getString("language","he"));
 }
 static String text(Context ctx,String heb){return isEnglish(ctx)?english.getOrDefault(heb,heb):heb;}
 static String count(Context ctx,int count,String heb,String en){
  return count+" "+(isEnglish(ctx)?en:heb);
 }
 private I18n(){}
}
