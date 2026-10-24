package com.dacaller.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

/** Five distinct call-screen layouts, each with its own visual kit. */
final class CallThemes {
 static final String[] IDS={"graphite","pixel","emerald","frost","ivory"};
 static final String[] NAMES={"Graphite Luxe","Pixel Clean","Emerald AMOLED","Frosted Slate","Ivory Studio"};
 static final String[] DESCRIPTIONS={
  "עמדת שליטה: שתי עמודות, דיוק ושליטה","בהשראת Google Material 3 Expressive: כפתורי גלולה, פריסה רוחבית",
  "מיקוד: מקשים גדולים בטור אחד וסיום שיחה בולט","לוח פעולות: שלוש עמודות ומידע מיושר לצד","מינימליסטי: שתי עמודות, תמונת מתקשר קטנה"
 };
 final int background,surface,surface2,ink,muted,accent,call,decline,line;
 final int radius;
 final String name;
 final boolean dark,amoled;
 final int style;
 CallThemes(Context context){
  SharedPreferences p=context.getSharedPreferences("da_caller",Context.MODE_PRIVATE);
  amoled=p.getBoolean("amoled",false);
  dark=amoled||p.getBoolean("dark",false);
  String id=p.getString("call_theme","graphite");
  int index=0;for(int i=0;i<IDS.length;i++)if(IDS[i].equals(id))index=i;
  style=index;name=NAMES[index];radius=new int[]{20,40,14,28,18}[index];
  int[] lights={0xFFFAF8F4,0xFFF7F9FC,0xFFF4FAF7,0xFFF5F7FA,0xFFFAF7EF};
  int[] darks={0xFF121212,0xFF111820,0xFF0A1712,0xFF131A27,0xFF191611};
  int[] accents={0xFFCDA668,0xFF6EA2F4,0xFF28BB8C,0xFF9FA6E8,0xFFCD9664};
  int[] lightAccents={0xFF8B6026,0xFF285CB5,0xFF087451,0xFF565FA5,0xFF8D5B2F};
  background=amoled?Color.BLACK:dark?darks[index]:lights[index];
  surface=amoled?0xFF0C0C0C:dark?new int[]{0xFF22211F,0xFF1D2734,0xFF172820,0xFF263042,0xFF2A241E}[index]:Color.WHITE;
  surface2=amoled?0xFF151515:dark?new int[]{0xFF302E2A,0xFF283749,0xFF243B32,0xFF323F59,0xFF3A3027}[index]:
   new int[]{0xFFF2EDE5,0xFFE9F1FE,0xFFE7F6ED,0xFFEAEDFA,0xFFF7EDE0}[index];
  ink=dark?Color.WHITE:0xFF222323;
  muted=dark?0xFFB0B5BC:0xFF666D78;
  accent=dark?accents[index]:lightAccents[index];
  call=0xFF138D5D;
  decline=dark?0xFFB23E46:0xFFAF383C;
  line=dark?0xFF484A4A:0xFFE0E3E6;
 }
 GradientDrawable card(Context context,int kind){
  float density=context.getResources().getDisplayMetrics().density;
  // Selected controls retain readable surfaces; accent belongs to their border/icons.
  int front=kind==2?decline:surface;
  int back=kind==2?decline:kind==1?surface:surface2;
  int[] stops=new int[]{front,back};
  GradientDrawable drawable=new GradientDrawable(GradientDrawable.Orientation.TL_BR,stops);
  drawable.setCornerRadius(radius*density);
  drawable.setStroke(Math.max(1,(int)density),kind==1?accent:kind==2?decline:line);
  return drawable;
 }
}
