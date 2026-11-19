package com.dacaller.app;

/** Display-only labels. The source taxonomy and search database remain unchanged. */
final class BusinessCategoryLabels {
 static String label(String source,boolean english){
  if(source==null||source.isEmpty())return "";
  int separator=source.indexOf(':');
  if(separator<0)return source;
  String type=source.substring(separator+1);
  String he=null,en=null;
  switch(type){
   case "mobile_phone_accessories":he="אביזרי סלולר";en="Mobile accessories";break;
   case "mobile_phone":he="סלולר";en="Mobile phones";break;
   case "hostel":he="אכסניה";en="Hostel";break;
   case "hotel":he="מלון";en="Hotel";break;
   case "guest_house":he="בית הארחה";en="Guest house";break;
   case "restaurant":he="מסעדה";en="Restaurant";break;
   case "fast_food":he="מזון מהיר";en="Fast food";break;
   case "cafe":he="בית קפה";en="Café";break;
   case "ice_cream":he="גלידרייה";en="Ice cream";break;
   case "bar":he="בר";en="Bar";break;
   case "pub":he="פאב";en="Pub";break;
   case "bakery":he="מאפייה";en="Bakery";break;
   case "supermarket":he="מרכול";en="Supermarket";break;
   case "convenience":he="מכולת";en="Convenience store";break;
   case "books":he="ספרים";en="Bookshop";break;
   case "clothes":he="ביגוד";en="Clothing";break;
   case "shoes":he="נעליים";en="Shoes";break;
   case "bicycle":he="אופניים";en="Bicycles";break;
   case "car":he="מכירת רכב";en="Car dealer";break;
   case "car_repair":he="מוסך";en="Car repair";break;
   case "car_parts":he="חלקי חילוף";en="Car parts";break;
   case "hairdresser":he="מספרה";en="Hair salon";break;
   case "beauty":he="טיפוח";en="Beauty";break;
   case "jewelry":he="תכשיטים";en="Jewelry";break;
   case "furniture":he="רהיטים";en="Furniture";break;
   case "toys":he="צעצועים";en="Toys";break;
   case "computer":he="מחשבים";en="Computers";break;
   case "electronics":he="אלקטרוניקה";en="Electronics";break;
   case "hardware":he="כלי עבודה";en="Hardware";break;
   case "building_materials":he="חומרי בניין";en="Building supplies";break;
   case "butcher":he="אטליז";en="Butcher";break;
   case "florist":he="פרחים";en="Florist";break;
   case "laundry":he="מכבסה";en="Laundry";break;
   case "pharmacy":he="בית מרקחת";en="Pharmacy";break;
   case "clinic":case "doctors":he="מרפאה";en="Clinic";break;
   case "doctor":case "physician":he="רופא";en="Doctor";break;
   case "dentist":he="מרפאת שיניים";en="Dental clinic";break;
   case "hospital":he="בית חולים";en="Hospital";break;
   case "optician":case "optometrist":he="אופטיקה";en="Optical services";break;
   case "veterinary":he="וטרינר";en="Veterinary clinic";break;
   case "bank":he="בנק";en="Bank";break;
   case "library":he="ספרייה";en="Library";break;
   case "fuel":he="תחנת דלק";en="Fuel station";break;
   case "cinema":he="קולנוע";en="Cinema";break;
   case "theatre":he="תיאטרון";en="Theatre";break;
   case "post_office":he="דואר";en="Post office";break;
   case "police":he="תחנת משטרה";en="Police station";break;
   case "fire_station":he="תחנת כיבוי";en="Fire station";break;
   case "fitness_centre":he="חדר כושר";en="Fitness centre";break;
   case "lawyer":he="עורך דין";en="Law office";break;
   case "estate_agent":he="תיווך";en="Real estate";break;
  }
  if(he!=null)return english?en:he;
  switch(source.substring(0,separator)){
   case "shop":return english?"Shop":"חנות";
   case "office":return english?"Office":"משרד";
   case "craft":return english?"Local trade":"בעל מקצוע";
   case "healthcare":return english?"Health services":"שירותי בריאות";
   case "tourism":return english?"Hospitality":"אירוח";
   case "leisure":return english?"Leisure and sport":"פנאי וספורט";
   case "amenity":return english?"Local service":"שירות מקומי";
   default:return english?"Business or service":"עסק או שירות";
  }
 }
 private BusinessCategoryLabels(){}
}
