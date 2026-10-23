package com.dabusinessbook.app;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.text.NumberFormat;
import java.util.*;

public final class MainActivity extends Activity {
    private static final int INK=0xff162738, TEAL=0xff087e82, FAINT=0xffedf4f5, LIGHT=0xfff7fafb, GRAY=0xff637787;
    private DirectoryDb db;
    private SharedPreferences pref;
    private Set<String> favorites=new HashSet<>();
    private Spinner cities,categories;
    private EditText query;
    private LinearLayout list;
    private TextView resultCount;
    private Switch onlyFavorites;
    private int loaded=0;
    private static final int PAGE=35;
    private String lastQuery="",lastCity="",lastCategory="";
    private boolean lastOnlyFav=false, ready=false;
    private int px(int dp){return (int)(dp*getResources().getDisplayMetrics().density+0.5f);}
    private GradientDrawable bg(int color,int r){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(px(r));return g;}
    private LinearLayout column(){LinearLayout x=new LinearLayout(this);x.setOrientation(1);x.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);return x;}
    private LinearLayout row(){LinearLayout x=new LinearLayout(this);x.setOrientation(0);x.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);x.setGravity(Gravity.CENTER_VERTICAL);return x;}
    private TextView text(String msg,int sp,int color,boolean bold){
        TextView v=new TextView(this);v.setText(msg);v.setTextSize(sp);v.setTextColor(color);
        v.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);v.setTextDirection(View.TEXT_DIRECTION_RTL);
        if(bold)v.setTypeface(null,1);return v;
    }
    private void space(LinearLayout parent,int h){View s=new View(this);parent.addView(s,new LinearLayout.LayoutParams(1,px(h)));}
    private Button button(String label,int color){
        Button b=new Button(this);b.setText(label);b.setTextSize(14);b.setTextColor(-1);b.setAllCaps(false);
        b.setBackground(bg(color,13));b.setPadding(px(12),px(3),px(12),px(3));return b;
    }
    @Override public void onCreate(Bundle b){super.onCreate(b);
        getWindow().setStatusBarColor(INK);getWindow().setNavigationBarColor(LIGHT);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        db=new DirectoryDb(this);pref=getSharedPreferences("favorite_businesses",MODE_PRIVATE);
        favorites=new HashSet<>(pref.getStringSet("keys",Collections.emptySet()));
        render();
    }
    private void render(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(LIGHT);scroll.setFillViewport(true);
        LinearLayout root=column();root.setPadding(px(16),px(17),px(16),px(30));scroll.addView(root);setContentView(scroll);
        LinearLayout header=column();header.setPadding(px(19),px(19),px(19),px(21));header.setBackground(bg(INK,22));
        TextView brand=text("DA  |  ספר העסקים",25,-1,true);brand.setGravity(Gravity.CENTER);header.addView(brand);
        space(header,8);
        TextView sub=text("בתי עסק בישראל · מידע שמור בטלפון · ללא אינטרנט",13,0xffbde3e1,false);
        sub.setGravity(Gravity.CENTER);header.addView(sub);
        space(header,13);
        String c=NumberFormat.getIntegerInstance(new Locale("he","IL")).format(db.count());
        TextView total=text(c+" עסקים בעלי מספר טלפון",18,0xff75e0cd,true);
        total.setGravity(Gravity.CENTER);header.addView(total);
        root.addView(header);space(root,15);
        LinearLayout searchBar=column();searchBar.setBackground(bg(-1,16));searchBar.setPadding(px(10),px(4),px(10),px(4));
        query=new EditText(this);query.setSingleLine(true);query.setTextSize(17);
        query.setHint("חיפוש עסק, מסעדה, חנות או מספר");query.setPadding(px(10),px(10),px(10),px(10));
        query.setBackgroundColor(0x00000000);searchBar.addView(query);root.addView(searchBar);space(root,12);
        LinearLayout filterRow=row();
        categories=new Spinner(this);cities=new Spinner(this);
        ArrayAdapter<String> catAdapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,db.options("category"));
        ArrayAdapter<String> cityAdapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,db.options("city"));
        categories.setAdapter(catAdapter);cities.setAdapter(cityAdapter);
        LinearLayout catBox=column();catBox.setPadding(px(8),px(8),px(8),px(8));catBox.setBackground(bg(FAINT,13));
        catBox.addView(text("תחום",12,GRAY,true));catBox.addView(categories);
        LinearLayout cityBox=column();cityBox.setPadding(px(8),px(8),px(8),px(8));cityBox.setBackground(bg(FAINT,13));
        cityBox.addView(text("יישוב",12,GRAY,true));cityBox.addView(cities);
        filterRow.addView(catBox,new LinearLayout.LayoutParams(0,-2,1));View div=new View(this);
        filterRow.addView(div,new LinearLayout.LayoutParams(px(8),1));
        filterRow.addView(cityBox,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(filterRow);space(root,6);
        onlyFavorites=new Switch(this);onlyFavorites.setText("רק מועדפים ★");
        onlyFavorites.setTextSize(14);onlyFavorites.setTextColor(INK);
        root.addView(onlyFavorites);space(root,5);
        resultCount=text("",13,GRAY,false);root.addView(resultCount);space(root,8);
        list=column();root.addView(list);
        space(root,17);
        Button about=button("אודות המאגר ומקורות המידע",INK);
        root.addView(about);about.setOnClickListener(v->new AlertDialog.Builder(this)
            .setTitle("ספר עסקים אופליין")
            .setMessage("הספר כולל מספרי טלפון ציבוריים של עסקים ומוסדות בישראל בלבד.\n\nהנתונים נלקחו מ-OpenStreetMap וממאגרי עסקים עירוניים פתוחים, בהתאם למקורות שהיו זמינים בעת בניית האפליקציה.\n\n© OpenStreetMap contributors — ODbL 1.0.\nהגבול הגאוגרפי מבוסס על geoBoundaries ISR ADM0.\nנתוני עיריית כפר סבא: ODbL.\nנתוני עיריית באר שבע: data.gov.il.\n\nהמידע עלול להיות חלקי או לא עדכני, ומספרי טלפון עשויים להשתנות. האפליקציה אינה מתחברת לאינטרנט ואינה אוספת אנשי קשר.")
            .setPositiveButton("סגור",null).show());
        ready=true;
        query.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){reset();}
            public void afterTextChanged(Editable s){}
        });
        android.widget.AdapterView.OnItemSelectedListener listener=new android.widget.AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(android.widget.AdapterView<?> parent){}
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int position,long id){if(ready)reset();}
        };
        categories.setOnItemSelectedListener(listener);cities.setOnItemSelectedListener(listener);
        onlyFavorites.setOnCheckedChangeListener((b,on)->reset());
        reset();
    }
    private String selection(Spinner sp){
        if(sp==null||sp.getSelectedItemPosition()<=0)return "";
        return String.valueOf(sp.getSelectedItem());
    }
    private void reset(){
        if(!ready||list==null)return;
        lastQuery=query.getText().toString();lastCategory=selection(categories);lastCity=selection(cities);
        lastOnlyFav=onlyFavorites.isChecked();loaded=0;list.removeAllViews();append();
    }
    private void append(){
        // Remove previous pagination control.
        if(loaded>0 && list.getChildCount()>0)list.removeViewAt(list.getChildCount()-1);
        List<DirectoryDb.Record> records=db.search(lastQuery,lastCategory,lastCity,lastOnlyFav,favorites,loaded,PAGE);
        if(records.isEmpty()&&loaded==0){
            resultCount.setText("לא נמצאו עסקים המתאימים לחיפוש");
            list.addView(text("נסה שם אחר או בחר בכל היישובים והתחומים.",15,GRAY,false));return;
        }
        loaded+=records.size();
        resultCount.setText("מוצגות "+loaded+" רשומות • לחץ על 'הצג עוד' להמשך");
        for(DirectoryDb.Record r:records)addCard(r);
        if(records.size()==PAGE){
            Button more=button("הצג עוד עסקים ↓",TEAL);list.addView(more);
            more.setOnClickListener(v->append());
        }else{
            TextView done=text("סוף התוצאות",12,GRAY,false);done.setGravity(Gravity.CENTER);
            done.setPadding(0,px(11),0,px(11));list.addView(done);
            resultCount.setText("נמצאו "+loaded+" עסקים");
        }
    }
    private void addCard(DirectoryDb.Record r){
        LinearLayout card=column();card.setPadding(px(14),px(14),px(14),px(11));card.setBackground(bg(-1,16));
        card.setElevation(px(1));
        LinearLayout nameRow=row();
        TextView name=text(r.name,17,INK,true);name.setMaxLines(2);
        nameRow.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        Button fave=button(favorites.contains(DirectoryDb.favKey(r))?"★":"☆",0xff657a83);
        nameRow.addView(fave,new LinearLayout.LayoutParams(px(48),px(42)));
        card.addView(nameRow);space(card,3);
        TextView details=text(r.category+(r.city.isEmpty()?"":"  ·  "+r.city),13,GRAY,false);
        card.addView(details);
        if(r.address!=null&&!r.address.isEmpty()){TextView address=text(r.address,12,GRAY,false);card.addView(address);}
        space(card,6);
        TextView phone=text(r.phone,17,TEAL,true);
        phone.setTextDirection(View.TEXT_DIRECTION_LTR);phone.setGravity(Gravity.RIGHT);
        card.addView(phone);space(card,8);
        LinearLayout actions=row();
        Button dial=button("חיוג",TEAL);
        actions.addView(dial,new LinearLayout.LayoutParams(0,px(43),1));
        View s=new View(this);actions.addView(s,new LinearLayout.LayoutParams(px(8),1));
        Button copy=button("העתקת מספר",INK);actions.addView(copy,new LinearLayout.LayoutParams(0,px(43),1));
        card.addView(actions);
        dial.setOnClickListener(v->{
            try{Intent i=new Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+Uri.encode(r.phone)));startActivity(i);}
            catch(Exception e){Toast.makeText(this,"אין חייגן זמין במכשיר",Toast.LENGTH_SHORT).show();}
        });
        copy.setOnClickListener(v->{
            ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE))
                .setPrimaryClip(ClipData.newPlainText("טלפון עסק",r.phone));
            Toast.makeText(this,"המספר הועתק",Toast.LENGTH_SHORT).show();
        });
        fave.setOnClickListener(v->{
            String k=DirectoryDb.favKey(r);
            if(!favorites.add(k))favorites.remove(k);
            pref.edit().putStringSet("keys",new HashSet<>(favorites)).apply();
            fave.setText(favorites.contains(k)?"★":"☆");
            if(lastOnlyFav)reset();
        });
        list.addView(card);space(list,9);
    }
    @Override protected void onDestroy(){if(db!=null)db.close();super.onDestroy();}
}
