package com.dacaller.offline;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public final class CallerDb extends SQLiteOpenHelper {
    public static final class Entry {
        public final String phone, name, kind, source;
        Entry(String p, String n, String k, String s) { phone=p;name=n;kind=k;source=s; }
    }
    public CallerDb(Context context) {
        super(context, "da_id_offline.db", null, 1);
        // Bundle a real offline directory; install it on first run only.
        // Never overwrite user's own imported data when upgrading the APK.
        java.io.File dst=context.getDatabasePath("da_id_offline.db");
        if(!dst.exists()) {
            try {
                java.io.File parent=dst.getParentFile();
                if(parent!=null && !parent.exists()) parent.mkdirs();
                try(java.io.InputStream in=context.getAssets().open("bundled_calls.db");
                    java.io.FileOutputStream out=new java.io.FileOutputStream(dst)){
                    byte[] buf=new byte[65536];int n;
                    while((n=in.read(buf))!=-1) out.write(buf,0,n);
                }
            }catch(java.io.IOException e) {
                // Graceful fallback for developer builds without bundled data.
                // SQLiteOpenHelper creates the schema on first database opening.
                dst.delete();
            }
        }
    }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE entries (phone TEXT PRIMARY KEY, name TEXT NOT NULL, kind TEXT NOT NULL DEFAULT 'business', source TEXT NOT NULL DEFAULT 'local', updated INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_name ON entries(name)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}
    public static String canonical(String raw) {
        if (raw==null) return "";
        String p=raw.replaceAll("[^0-9+]", "");
        if (p.startsWith("00972")) p="0"+p.substring(5);
        else if(p.startsWith("+972")) p="0"+p.substring(4);
        else if(p.startsWith("972") && p.length()>9) p="0"+p.substring(3);
        else if(p.length()==9 && p.charAt(0)=='5') p="0"+p;
        return p.replace("+","");
    }
    public static boolean plausible(String phone) { return phone.length()>=3 && phone.length()<=15; }
    public static String category(String raw) {
        if (raw==null) return "business";
        String k=raw.toLowerCase(java.util.Locale.ROOT).trim();
        if(k.contains("spam")||k.contains("ספאם")||k.contains("block"))return "spam";
        if(k.contains("personal")||k.contains("אישי"))return "personal";
        if(k.contains("service")||k.contains("שירות"))return "service";
        return "business";
    }
    public void add(String phone, String name, String kind, String source) {
        String p=canonical(phone);
        if(!plausible(p)||name==null||name.trim().isEmpty())return;
        ContentValues v=new ContentValues();
        v.put("phone",p);
        v.put("name",name.trim());
        v.put("kind",category(kind));
        v.put("source",source==null?"local":source);
        v.put("updated",System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("entries",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    public Entry find(String phone) {
        String n=canonical(phone);if(n.isEmpty())return null;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT phone,name,kind,source FROM entries WHERE phone=? LIMIT 1",new String[]{n})){
            return c.moveToFirst()?row(c):null;
        }
    }
    private Entry row(Cursor c){return new Entry(c.getString(0),c.getString(1),c.getString(2),c.getString(3));}
    public List<Entry> search(String needle) {
        List<Entry> out=new ArrayList<>();
        String q=needle==null?"":needle.trim();
        String normalized=canonical(q);
        String sql=q.isEmpty()?"SELECT phone,name,kind,source FROM entries ORDER BY updated DESC LIMIT 40":
            "SELECT phone,name,kind,source FROM entries WHERE phone LIKE ? OR name LIKE ? ORDER BY name LIMIT 40";
        String[] args=q.isEmpty()?new String[0]:new String[]{"%"+normalized+"%","%"+q+"%"};
        try(Cursor c=getReadableDatabase().rawQuery(sql,args)){
            while(c.moveToNext())out.add(row(c));
        }
        return out;
    }
    public long count(){return DatabaseUtils.queryNumEntries(getReadableDatabase(),"entries");}
    public void setSpam(String number, boolean spam) {
        Entry e=find(number);if(e==null)return;
        ContentValues v=new ContentValues();
        v.put("kind",spam?"spam":"business");
        getWritableDatabase().update("entries",v,"phone=?",new String[]{e.phone});
    }
}
