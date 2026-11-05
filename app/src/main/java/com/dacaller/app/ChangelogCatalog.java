package com.dacaller.app;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Local, bilingual version notes. No publication dates are inferred from commit dates. */
final class ChangelogCatalog {
 static final class Change {
  private final String he,en;
  Change(String he,String en){this.he=he;this.en=en;}
  String text(boolean english){return english?en:he;}
 }
 static final class Entry {
  final String version;
  private final String he,en;
  final List<Change> changes;
  Entry(String version,String he,String en,Change... changes){
   this.version=version;this.he=he;this.en=en;
   this.changes=Collections.unmodifiableList(Arrays.asList(changes.clone()));
  }
  String title(boolean english){return english?en:he;}
  boolean preview(){return version.contains("beta");}
 }
 private static Change change(String he,String en){return new Change(he,en);}
 // Version boundaries and notes are grounded in app/build.gradle history and
 // docs/DA_CALLER_{VISUAL_VERIFICATION,PERSONALIZATION_VERIFICATION,DYNAMIC_ISLAND}.
 private static final List<Entry> ENTRIES=Collections.unmodifiableList(Arrays.asList(
  new Entry("0.11.0-beta.10","תזכורת לחזרה לשיחות","A reminder to return calls",
   change("יצירת איש קשר חדש ממספר לא מוכר בשיחות האחרונות, בלי חיוג אוטומטי.",
    "Create a new contact from an unknown recent caller without automatically dialing."),
   change("מענה בהחלקה מלאה, עם חלופת נגישות. סידור Nothing ו־Xiaomi וספרות חיוג גדולות יותר; בגוגל גופן עגול ומודגש מקומי.",
    "Answer with a complete swipe or the explicit accessibility action. Tidier Nothing and Xiaomi layouts and larger keypad digits, with a bundled rounded, heavier Google typeface."),
   change("תזכורת יומית אופציונלית למתקשרים שלא נענו היום, לפי חיבור מאוחר יותר של יותר משנייה; קיבוץ ומניין הפספוסים וניסיונות החזרה.",
    "Optional daily reminders for today's missed callers without a later connection longer than one second, with grouped missed calls and callback attempts."),
   change("בחירת שעת התזכורת וגיבוי ההגדרה. התזכורת אינה מוחקת או משנה את יומן השיחות והארכיון.",
    "A selectable reminder time and settings backup. The reminder never deletes or changes call history or the archive."),
   change("לשונית בתי עסק נפרדת עם חיפוש מקומי ומקור הנתונים. הזמינות והכיסוי תלויים במאגר מורשה שנכלל בגרסה; נתוני דוגמה אינם נכללים.",
    "A separate business tab with offline search and source details. Availability and coverage depend on a licensed dataset included in the build; no sample records are bundled."),
   change("הגנות חדשות מפני פרסום בחתימה מתחלפת. עדכון התקנת beta.9 עדיין דורש את מפתח החתימה המקורי שאינו זמין כרגע.",
    "New guards against publication with changing signing keys. Updating an existing beta.9 install still requires its original private signing key, which is currently unavailable.")),
  new Entry("0.11.0-beta.9","האי יוצא למסך הבית","The island comes to Home",
   change("אפשרות לאי שיחה במסך הבית ובאפליקציות אחרות בזמן חיוג, שיחה פעילה או המתנה, לאחר אישור הרשאת Android.",
    "Optional call island on Home and above other apps while dialing, active or on hold, after granting Android's display permission."),
   change("התראות DA CALLER לשיחות שלא נענו, עם שם איש הקשר ופתיחה לשיחות האחרונות.",
    "DA CALLER missed-call notifications with contact identity and a shortcut to recent calls."),
   change("יומן שינויים מקומי בהגדרות, מסודר לפי גרסה וזמין גם ללא אינטרנט.",
    "An offline changelog in Settings, organized by app version.")),
  new Entry("0.11.0-beta.8","אי דינמי בתוך האפליקציה","An in-app dynamic call island",
   change("קפסולת שיחה קומפקטית ומורחבת מעל מסכי האפליקציה, עם חזרה למסך השיחה.",
    "Compact and expanded call capsules above app screens, with a return-to-call shortcut."),
   change("שלושה סגנונות, שני גדלים, טיימר, תמונה ואפשרויות לפקדי השתקה, רמקול והמתנה.",
    "Three styles, two sizes, a timer, photo and customizable mute, speaker and hold controls."),
   change("אישור לפני סיום שיחה, תצוגה מקדימה ושמירת מיקום הרשימה בזמן הרחבה וצמצום.",
    "End-call confirmation, a live preview and preserved list position when expanding or collapsing.")),
  new Entry("0.11.0-beta.7","התאמה אישית וניווט בשיחה","Personalization and call navigation",
   change("גיבוי ושחזור הגדרות לקובץ, חמישה סמלי אפליקציה ובחירת צורת תמונות אנשי קשר.",
    "Settings backup and restore, five launcher icons and selectable contact-photo shapes."),
   change("רצועת מועדפים נגללת מעל אנשי קשר ושיחות אחרונות, וסיכום שיחות שניתן לצמצם ולהרחיב.",
    "A scrollable favorites strip above contacts and recents, plus a collapsible call summary."),
   change("זיהוי אנשי קשר בארכיון, התראות שיחה ו־Back אל המסך הקודם בלי לנתק או לחייג שוב.",
    "Contact identities in the archive, call notifications and Back to the previous screen without ending or redialing.")),
  new Entry("0.11.0-beta.6","חמישה עיצובים ושיפור המסך הקטן","Five designs and small-screen refinements",
   change("עיצוב מחדש למסכי Google Phone, Samsung One UI, iOS, HyperOS ו־Nothing, עם מצבים בהירים וכהים.",
    "Redesigned Google Phone, Samsung One UI, iOS, HyperOS and Nothing-inspired call screens in light and dark modes."),
   change("מגשי שמע, אפשרויות נוספות ולוח מקשים, עם פקדים המותאמים ליכולות השיחה.",
    "Audio, More and keypad sheets with controls adapted to the call's available capabilities."),
   change("ווידג׳טים קומפקטיים, חזרה יציבה לאנשי קשר והגדרות הרשאות ברורות יותר.",
    "Compact widgets, preserved contact-list return positions and clearer permission settings.")),
  new Entry("0.11.0-beta.5","עידון התצוגה","Visual refinements",
   change("תיקון רקע משטח הפקדים בעיצוב Google Phone ועידון הסברי העיצובים.",
    "Corrected the Google Phone control-dock surface and refined the design descriptions."),
   change("בדיקת מסכי השיחה בצילומי Android, כולל איתור מסכים ריקים במעבר בין מסכים.",
    "Android screenshot checks for call layouts, including blank frames during screen transitions.")),
  new Entry("0.11.0-beta.4","מבנה חדש למסכי שיחה והגדרות","A new call and settings layout",
   change("מבנה חדש לתמונת המתקשר, לפרטי השיחה ולפקדים התחתונים במסך קטן.",
    "Reworked caller portrait, call details and bottom controls for small screens."),
   change("קטגוריות הגדרות נפתחות וסיכום שיחות עם תקופה, כמות ומשך.",
    "Expandable settings categories and a call summary with period, count and duration.")),
  new Entry("0.11.0-beta.3","חיזוק שירות השיחות","Call-service resilience",
   change("הגנות נוספות מפני שגיאות מערכת במסך השיחה, בהודעות השיחה ובטיפול בשמע.",
    "Additional guards for system errors in call screens, call notifications and audio handling."),
   change("אבחון תקלה מקומי בהגדרות, ללא שמירת מספר הטלפון בדוח.",
    "Local call diagnostics in Settings, without including the phone number in the report.")),
  new Entry("0.11.0-beta.2","ניגודיות ותרגום פקדים","Control contrast and translation",
   change("שיפור ניגודיות בעיצוב ברירת המחדל, סמל אפשרויות נוספות ותרגום פקדי השיחה לאנגלית.",
    "Improved default-theme contrast, a More-options icon and English call-control labels."),
   change("שמירת בקשת החיוג עד לאישור תפקיד חייגן ברירת המחדל.",
    "Preserved the intended call until the default-dialer role is granted.")),
  new Entry("0.11.0-beta.1","מסכי שיחה מובחנים","Distinct call layouts",
   change("חמש פריסות שיחה נפרדות, עם טיפוגרפיה, צבעים וסידור פקדים שונים.",
    "Five distinct call layouts with different typography, palettes and control arrangements."),
   change("לוח מקשים משופר וזרימת הרשאת החייגן גם בחיוג מווידג׳ט.",
    "Refined keypad geometry and default-dialer setup for widget calls.")),
  new Entry("0.10.0-beta.1","תיקוני נתונים ויציבות","Data and reliability fixes",
   change("תיקונים לזיהוי מספרים ולחיפוש, וחיזוק בדיקות החיוג ושמירת הנתונים.",
    "Number-identity and search fixes, with stronger dialing and data-preservation checks.")),
  new Entry("0.9.0","שמירת מיקום ותמונות","List positions and photos",
   change("שמירת מיקום הגלילה בשיחות ובאנשי קשר בזמן רענון וחזרה למסך.",
    "Preserved recents and contact-list positions across refreshes and navigation."),
   change("תמונות במועדפים והרחבת התרגום למסכי אנשי קשר, ארכיון והגדרות.",
    "Photos in favorites and broader translations for contacts, archive and settings.")),
  new Entry("0.8.0","ארכיון ומראה שיחה","Archive and call appearance",
   change("ארכיון שיחות אישי, ערכות מראה לשיחה ובחירת מחוות מענה.",
    "A private call archive, call appearance themes and answer-gesture choices."),
   change("תמונות אנשי קשר וכרטיס איש קשר משולב באפליקציה.",
    "Contact photos and an integrated contact-details screen.")),
  new Entry("0.7.0","יומן מלא ועריכת אנשי קשר","Full history and contact editing",
   change("רשימות שיחות ואנשי קשר ממוחזרות להצגת ההיסטוריה המלאה.",
    "Virtualized call and contact lists for the complete local history."),
   change("עריכת אנשי קשר והוספת מספר לאיש קשר קיים מתוך לוח החיוג.",
    "Contact editing and adding a dialpad number to an existing contact.")),
  new Entry("0.6.0","ניווט ומשוב מגע","Navigation and touch feedback",
   change("שיפורים למעבר בין מסכים, אייקוני פקדים ומשוב הקשה שניתן להתאים.",
    "Refined navigation, control icons and adjustable touch feedback.")),
  new Entry("0.5.0","פקדי שיחה להתאמה אישית","Customizable call controls",
   change("פקדי שיחה קטנים יותר, התאמת גובהם ופעולות שיחה נוספות.",
    "Smaller call controls, adjustable control height and additional call actions.")),
  new Entry("0.4.0","סמל זהב ומראה זכוכית","A gold icon and glass appearance",
   change("סמל אפליקציה זהוב, מראה זכוכית ללוח החיוג ותיקונים לאנשי קשר.",
    "A gold app icon, a glass-style dialpad and contact fixes.")),
  new Entry("0.3.0","התאמה למסך קטן","Small-screen adjustments",
   change("התאמת פרטי השיחה והפקדים למסך קטן ושיפור בחירת איש קשר לווידג׳ט.",
    "Adjusted call details and controls for small screens, and improved the widget contact picker.")),
  new Entry("0.2.0","מסך ושירות שיחה","Call screens and service",
   change("מסך שיחות נכנסות ויוצאות, שירות שיחה ויומן שיחות מקומי.",
    "Incoming and outgoing call screens, call service and local call history.")),
  new Entry("0.1.0","הגרסה הראשונה","The initial app",
   change("שלד אפליקציית החייגן, רשימת אנשי קשר ווידג׳טים למסך הבית.",
    "The initial dialer app, contact list and Home-screen widgets."))
 ));
 static List<Entry> entries(){return ENTRIES;}
 private ChangelogCatalog(){}
}
