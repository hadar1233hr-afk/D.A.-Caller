package com.dacaller.offline;

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int DOCUMENT=201, ROLE=202;
    private final int DARK=0xff10243a, BLUE=0xff1a5578, AQUA=0xff27b6ac;
    private CallerDb db;
    private SharedPreferences settings;
    private LinearLayout page, results;
    private TextView count, status;
    private EditText search;
    private final Handler ui=new Handler(Looper.getMainLooper());

    private int dp(int a){return (int)(a*getResources().getDisplayMetrics().density+0.5f);}
    private GradientDrawable rounded(int c,int radius){GradientDrawable b=new GradientDrawable();b.setColor(c);b.setCornerRadius(dp(radius));return b;}
    private TextView text(String s,int sp,int color,boolean bold){
        TextView v=new TextView(this);v.setText(s);v.setTextColor(color);v.setTextSize(sp);
        v.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        if(bold)v.setTypeface(null,Typeface.BOLD);
        return v;
    }
    private void gap(LinearLayout p,int h){View v=new View(this);p.addView(v,new LinearLayout.LayoutParams(1,dp(h)));}
    private LinearLayout col(){LinearLayout v=new LinearLayout(this);v.setOrientation(1);v.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);return v;}
    private Button button(String label,int color){
        Button b=new Button(this);b.setText(label);b.setTextColor(Color.WHITE);b.setAllCaps(false);
        b.setBackground(rounded(color,15));return b;
    }
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        db=new CallerDb(this);
        settings=getSharedPreferences("da_offline_id",MODE_PRIVATE);
        render();
    }
    private void render(){
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setBackgroundColor(0xfff3f7fa);
        page=col();page.setPadding(dp(17),dp(23),dp(17),dp(28));sc.addView(page);setContentView(sc);
        LinearLayout hero=col();hero.setPadding(dp(20),dp(20),dp(20),dp(22));hero.setBackground(rounded(DARK,25));
        TextView brand=text("DA  ID  OFFLINE",25,Color.WHITE,true);brand.setGravity(Gravity.CENTER);hero.addView(brand);
        gap(hero,7);
        TextView tag=text("זיהוי מתקשרים מקומי • ללא אינטרנט",15,0xffb8d5e3,false);
        tag.setGravity(Gravity.CENTER);hero.addView(tag);
        gap(hero,12);
        count=text("טוען מאגר…",15,0xff77f1de,true);count.setGravity(Gravity.CENTER);hero.addView(count);
        page.addView(hero);gap(page,17);
        setCount();

        page.addView(text("חיפוש במאגר",20,DARK,true));gap(page,9);
        search=new EditText(this);search.setSingleLine(true);
        search.setTextSize(17);search.setHint("מספר טלפון או שם עסק");
        search.setPadding(dp(15),dp(9),dp(15),dp(9));
        search.setBackground(rounded(Color.WHITE,14));page.addView(search);
        gap(page,10);
        results=col();page.addView(results);
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int before,int count){refreshResults(s.toString());}
            public void afterTextChanged(Editable e){}
        });
        refreshResults("");

        gap(page,17);
        Button activate=button("הפעלת זיהוי שיחות נכנסות",BLUE);
        page.addView(activate);activate.setOnClickListener(v->requestRole());
        gap(page,8);
        status=text("כדי לזהות מתקשרים יש לבחור באפליקציה כשירות זיהוי השיחות של Android.",13,0xff4f6477,false);
        page.addView(status);updateRoleText();
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            gap(page,8);
            Button notif=button("אישור הצגת התראות זיהוי",AQUA);
            page.addView(notif);notif.setOnClickListener(v->requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},44));
        }
        gap(page,18);
        page.addView(text("הרחבת המאגר",20,DARK,true));gap(page,7);
        TextView tip=text("אפשר לייבא מאגר CSV / TSV / SQLite. המידע נשמר רק במכשיר. אין שליחה לשרת.",14,0xff415a6c,false);
        page.addView(tip);gap(page,8);
        Button importBtn=button("ייבוא מאגר מספרים מקובץ",BLUE);page.addView(importBtn);
        importBtn.setOnClickListener(v->openFile());
        gap(page,10);

        Switch block=new Switch(this);block.setText("לחסום אוטומטית מספרים שסומנו כספאם");
        block.setTextColor(DARK);block.setTextSize(15);
        block.setChecked(settings.getBoolean("block_spam",false));
        block.setOnCheckedChangeListener((v,checked)->settings.edit().putBoolean("block_spam",checked).apply());
        page.addView(block);
        gap(page,18);
        page.addView(text("הוספת מספר למאגר",20,DARK,true));gap(page,7);
        EditText phone=new EditText(this);phone.setHint("מספר, למשל 03-1234567");
        phone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);phone.setSingleLine(true);
        phone.setBackground(rounded(Color.WHITE,12));phone.setPadding(dp(12),dp(9),dp(12),dp(9));page.addView(phone);
        gap(page,8);
        EditText name=new EditText(this);name.setHint("שם לזיהוי");
        name.setSingleLine(true);name.setBackground(rounded(Color.WHITE,12));
        name.setPadding(dp(12),dp(9),dp(12),dp(9));page.addView(name);
        gap(page,8);
        Button add=button("שמור מספר",AQUA);page.addView(add);
        add.setOnClickListener(v->{
            if(!CallerDb.plausible(CallerDb.canonical(phone.getText().toString()))||name.getText().toString().trim().isEmpty()){
                Toast.makeText(this,"יש להזין מספר ושם תקינים",Toast.LENGTH_SHORT).show();return;
            }
            db.add(phone.getText().toString(),name.getText().toString(),"business","manual");
            setCount();refreshResults(search.getText().toString());phone.setText("");name.setText("");
            Toast.makeText(this,"נשמר במאגר המקומי",Toast.LENGTH_SHORT).show();
        });
        gap(page,17);
        page.addView(text("גרסה 0.1.0 • פיתוח ניסיוני. המאגר אינו מכיל ספר טלפונים פרטי ואינו מעדכן את עצמו דרך הרשת.",12,0xff647987,false));
    }
    private void setCount(){if(count!=null)count.setText(db.count()+" מספרים במאגר המקומי");}
    private void refreshResults(String needle){
        if(results==null)return;results.removeAllViews();
        List<CallerDb.Entry> rows=db.search(needle);
        if(rows.isEmpty()){
            results.addView(text(needle.isEmpty()?"המאגר כרגע ריק. אפשר לייבא קובץ נתונים.":"לא נמצאה התאמה במאגר.",14,0xff687b8b,false));
            return;
        }
        for(CallerDb.Entry e:rows){
            LinearLayout item=col();item.setPadding(dp(12),dp(10),dp(12),dp(10));
            item.setBackground(rounded(Color.WHITE,13));
            item.addView(text(e.name,17,DARK,true));
            item.addView(text(e.phone+"   •   "+(e.kind.equals("spam")?"ספאם":e.kind.equals("service")?"שירות":e.kind.equals("personal")?"אישי":"עסק"),13,0xff617587,false));
            Button toggle=button(e.kind.equals("spam")?"בטל סימון כספאם":"סמן כספאם",e.kind.equals("spam")?AQUA:BLUE);
            item.addView(toggle);toggle.setOnClickListener(v->{db.setSpam(e.phone,!e.kind.equals("spam"));refreshResults(search.getText().toString());});
            results.addView(item);gap(results,7);
        }
    }
    private void updateRoleText(){
        if(status==null)return;
        boolean enabled=false;
        if(Build.VERSION.SDK_INT>=29){
            RoleManager rm=(RoleManager)getSystemService(ROLE_SERVICE);
            enabled=rm!=null && rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING);
        }
        status.setText(enabled?"✓ זיהוי השיחות הופעל בהגדרות Android":"טרם הופעל זיהוי שיחות. לחץ על הכפתור ובחר DA ID Offline.");
    }
    private void requestRole(){
        if(Build.VERSION.SDK_INT<29){
            Toast.makeText(this,"גרסה זו מיועדת לזיהוי שיחות באנדרואיד 10 ומעלה",Toast.LENGTH_LONG).show();return;
        }
        try{
            RoleManager rm=(RoleManager)getSystemService(ROLE_SERVICE);
            if(rm==null || !rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)){Toast.makeText(this,"שירות זיהוי השיחות אינו זמין במכשיר",Toast.LENGTH_LONG).show();return;}
            if(rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)){Toast.makeText(this,"זיהוי שיחות כבר הופעל",Toast.LENGTH_SHORT).show();return;}
            startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING),ROLE);
        }catch(Exception e){Toast.makeText(this,"לא ניתן לפתוח הרשאת זיהוי שיחות: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    private void openFile(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(i,DOCUMENT);
    }
    @Override protected void onActivityResult(int r,int c,Intent data){
        super.onActivityResult(r,c,data);
        if(r==ROLE){updateRoleText();}
        if(r==DOCUMENT&&c==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri uri=data.getData();
            Toast.makeText(this,"מייבא ברקע…",Toast.LENGTH_LONG).show();
            new Thread(()->{
                String msg;
                try{
                    int n=importFile(uri);
                    msg="הייבוא הסתיים: "+n+" מספרים נקלטו";
                }catch(Exception e){msg="שגיאת ייבוא: "+e.getMessage();}
                final String output=msg;
                ui.post(()->{setCount();refreshResults(search.getText().toString());Toast.makeText(this,output,Toast.LENGTH_LONG).show();});
            },"da-id-import").start();
        }
    }
    private int importFile(Uri uri)throws Exception{
        String filename="";
        try(Cursor c=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(c!=null&&c.moveToFirst())filename=c.getString(0).toLowerCase(java.util.Locale.ROOT);
        }
        if(filename.endsWith(".db")||filename.endsWith(".sqlite")||filename.endsWith(".sqlite3"))return importSqlite(uri);
        if(filename.endsWith(".xlsx")||filename.endsWith(".xls")||filename.endsWith(".pbf"))
            throw new IllegalArgumentException("יש להמיר קובץ Excel או PBF ל-CSV לפני הייבוא");
        return importCsv(uri);
    }
    private int importSqlite(Uri uri)throws Exception{
        File temp=new File(getCacheDir(),"external_import.db");
        try(InputStream in=getContentResolver().openInputStream(uri);FileOutputStream out=new FileOutputStream(temp)){
            if(in==null)throw new IllegalArgumentException("לא ניתן לפתוח את הקובץ");
            byte[] buf=new byte[32768];int n;long bytes=0;
            while((n=in.read(buf))!=-1){bytes+=n;if(bytes>500000000)throw new IllegalArgumentException("קובץ גדול מדי");out.write(buf,0,n);}
        }
        int n=0;
        SQLiteDatabase ext=SQLiteDatabase.openDatabase(temp.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY);
        try(Cursor c=ext.rawQuery("SELECT phone,name,category,source FROM caller_names",null)){
            SQLiteDatabase target=db.getWritableDatabase();target.beginTransaction();
            try{
                while(c.moveToNext()){
                    if(CallerDb.plausible(CallerDb.canonical(c.getString(0)))){db.add(c.getString(0),c.getString(1),c.getString(2),c.getString(3));n++;}
                    if(n>1000000)break;
                }
                target.setTransactionSuccessful();
            }finally{target.endTransaction();}
        }finally{ext.close();temp.delete();}
        return n;
    }
    private static List<String> split(String line,char delim){
        List<String> parts=new ArrayList<>();StringBuilder b=new StringBuilder();boolean quoted=false;
        for(int i=0;i<line.length();i++){
            char ch=line.charAt(i);
            if(ch=='"'){if(quoted&&i+1<line.length()&&line.charAt(i+1)=='"'){b.append('"');i++;}else quoted=!quoted;}
            else if(ch==delim&&!quoted){parts.add(b.toString().trim());b.setLength(0);}
            else b.append(ch);
        }
        parts.add(b.toString().trim());return parts;
    }
    private static int idx(List<String> header,String... tokens){
        for(int i=0;i<header.size();i++){String h=header.get(i).replace("\uFEFF","").toLowerCase(java.util.Locale.ROOT).trim();
            for(String token:tokens)if(h.equals(token)||h.contains(token))return i;}
        return -1;
    }
    private static String at(List<String> xs,int i){return i>=0&&i<xs.size()?xs.get(i):"";}
    private int importCsv(Uri uri)throws Exception{
        int count=0;
        try(InputStream in=getContentResolver().openInputStream(uri);
            BufferedReader b=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){
            String head=b.readLine();if(head==null)return 0;
            char delim=head.indexOf('\t')>=0?'\t':head.indexOf(';')>=0?';':',';
            List<String> headers=split(head,delim);
            int phoneIndex=idx(headers,"phone","טלפון","mobile","number","מספר","contact:phone");
            int nameIndex=idx(headers,"name","שם","business","עסק","תיאור");
            if(phoneIndex<0||nameIndex<0)throw new IllegalArgumentException("נדרשות עמודות phone/name או טלפון/שם");
            int kindIndex=idx(headers,"category","סוג","kind","classification");
            SQLiteDatabase target=db.getWritableDatabase();target.beginTransaction();
            try{
                String line;int lines=0;
                while((line=b.readLine())!=null&&lines++<1000000){
                    List<String> values=split(line,delim);
                    String phone=at(values,phoneIndex),name=at(values,nameIndex);
                    if(!CallerDb.plausible(CallerDb.canonical(phone))||name.trim().isEmpty())continue;
                    db.add(phone,name,at(values,kindIndex),"local_csv");count++;
                }
                target.setTransactionSuccessful();
            }finally{target.endTransaction();}
        }
        return count;
    }
    @Override protected void onResume(){super.onResume();if(status!=null)updateRoleText();}
    @Override protected void onDestroy(){if(db!=null)db.close();super.onDestroy();}
}
