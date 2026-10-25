package com.dacaller.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

/** Independent visual kits. Stable IDs preserve every existing user's choice. */
final class CallThemes {
 static final String[] IDS={"graphite","pixel","emerald","frost","ivory"};
 static final String[] NAMES={"Samsung · One UI","Google Phone","iOS 26 · Glass","Xiaomi · HyperOS","Nothing OS"};
 static final String[] DESCRIPTIONS={
  "פרופיל עגול ומערך פעולות רחב", "ארבע פעולות ומגשים מעוגלים בהשראת Google Phone",
  "משטחי זכוכית קלים ומערך סימטרי", "כרטיסי פעולה ופרופיל במבנה אופקי", "טיפוגרפיה טכנית, נקודות ושחור־לבן"
 };
 final int background,surface,surface2,ink,muted,accent,call,decline,line,selected,selectedInk,end,endInk;
 final int radius,style;
 final String name;
 final boolean dark,amoled;
 CallThemes(Context context){
  SharedPreferences p=context.getSharedPreferences("da_caller",Context.MODE_PRIVATE);
  amoled=p.getBoolean("amoled",false);dark=amoled||p.getBoolean("dark",false);
  String id=p.getString("call_theme","pixel");int index=1;
  for(int i=0;i<IDS.length;i++)if(IDS[i].equals(id))index=i;
  style=index;name=NAMES[index];radius=new int[]{28,32,30,22,12}[index];
  int[] lightBg={0xFFF5F7FC,0xFFFFF9F3,0xFFE9EDF4,0xFFF3F5F9,0xFFF8F8F6};
  int[] darkBg={0xFF10141B,0xFF1F2020,0xFF17212F,0xFF111620,0xFF101010};
  int[] lightSurface={0xFFFFFFFF,0xFFF1EFEB,0xFFF7F9FC,0xFFFFFFFF,0xFFEDEDEB};
  int[] darkSurface={0xFF202631,0xFF292A2A,0xFF293747,0xFF232C3A,0xFF222222};
  int[] lightButton={0xFFE7ECF6,0xFFE5E3DF,0xFFDFE6EF,0xFFE8EDF6,0xFFE5E5E2};
  int[] darkButton={0xFF2B3443,0xFF121313,0xFF354559,0xFF303D50,0xFF171717};
  background=amoled?Color.BLACK:dark?darkBg[index]:lightBg[index];
  surface=dark?darkSurface[index]:lightSurface[index];surface2=dark?darkButton[index]:lightButton[index];
  ink=dark?0xFFE4E5E7:0xFF1D2229;muted=dark?0xFFC3C6CB:0xFF555C66;
  accent=dark?new int[]{0xFFB6D1FF,0xFFFFDDBD,0xFFD5E5FF,0xFFC3D8FF,0xFFF3F3EF}[index]:
   new int[]{0xFF244E8A,0xFF655042,0xFF354F71,0xFF2E4F81,0xFF30302E}[index];
  selected=new int[]{0xFFBDD5FF,0xFFFFDDBD,0xFFEEF4FC,0xFFC5D8FF,0xFFECECEA}[index];
  selectedInk=0xFF25272B;call=0xFF14784D;decline=0xFFAC343D;
  end=index==1?0xFFF46359:index==4?0xFFE44343:0xFFE84F59;
  endInk=index==1?0xFF242222:Color.WHITE;
  line=dark?0xFF526074:0xFFB8C1CF;
 }
 GradientDrawable card(Context context,int kind){
  float density=context.getResources().getDisplayMetrics().density;
  int front=kind==2?decline:surface,back=kind==2?decline:surface2;
  GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{front,back});
  d.setCornerRadius(radius*density);d.setStroke(Math.max(1,(int)density),kind==1?accent:kind==2?decline:line);return d;
 }
}
