package com.dabusinessbook.app;

import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;
import java.io.*;
import java.util.*;

final class DirectoryDb extends SQLiteOpenHelper {
    static final class Record {
        long id;
        String name, phone, city, category, address, source;
        Record(Cursor c) {
            id=c.getLong(0); name=c.getString(1);phone=c.getString(2);
            city=c.getString(3);category=c.getString(4);address=c.getString(5);source=c.getString(6);
        }
    }
    static final String FILENAME="da_business_book.sqlite";
    private static final int VERSION=1;
    DirectoryDb(Context ctx) {
        super(ctx,FILENAME,null,VERSION);
        File dest=ctx.getDatabasePath(FILENAME);
        if(!dest.exists()) {
            try {
                File dir=dest.getParentFile();
                if(dir!=null)dir.mkdirs();
                try(InputStream in=ctx.getAssets().open("directory.sqlite");
                    OutputStream out=new FileOutputStream(dest)) {
                    byte[] b=new byte[65536];int n;
                    while((n=in.read(b))>0)out.write(b,0,n);
                }
            }catch(IOException e){dest.delete();}
        }
    }
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS businesses (id INTEGER PRIMARY KEY, name TEXT NOT NULL, phone TEXT NOT NULL, city TEXT NOT NULL DEFAULT '', category TEXT NOT NULL DEFAULT 'אחר', address TEXT NOT NULL DEFAULT '', source TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_business_name ON businesses(name)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_business_category_city ON businesses(category,city)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_business_phone ON businesses(phone)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldV,int newV){}
    long count(){return DatabaseUtils.queryNumEntries(getReadableDatabase(),"businesses");}
    List<String> options(String column){
        if(!column.equals("city")&&!column.equals("category"))return Collections.emptyList();
        List<String> out=new ArrayList<>();out.add(column.equals("city")?"כל היישובים":"כל התחומים");
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT DISTINCT "+column+" FROM businesses WHERE "+column+"<>'' ORDER BY "+column+" COLLATE NOCASE",null)) {
            while(c.moveToNext())out.add(c.getString(0));
        }
        return out;
    }
    List<Record> search(String q,String category,String city,boolean onlyFavorite,Set<String> favorites,int offset,int limit) {
        StringBuilder sql=new StringBuilder("SELECT id,name,phone,city,category,address,source FROM businesses WHERE 1=1");
        List<String> args=new ArrayList<>();
        q=q==null?"":q.trim();
        if(!q.isEmpty()){
            String[] words=q.split("\\s+");
            for(String word:words) {
                sql.append(" AND (name LIKE ? OR phone LIKE ? OR city LIKE ? OR address LIKE ? OR category LIKE ?)");
                for(int i=0;i<5;i++)args.add("%"+word+"%");
            }
        }
        if(category!=null&&!category.isEmpty()) {sql.append(" AND category=?");args.add(category);}
        if(city!=null&&!city.isEmpty()) {sql.append(" AND city=?");args.add(city);}
        // Favorite filtering across pages must happen before OFFSET, in Java for small collections.
        if(onlyFavorite){
            if(favorites.isEmpty())return Collections.emptyList();
            sql.append(" AND (");
            int i=0;for(String key:favorites) {
                if(i++>0)sql.append(" OR ");
                sql.append("(phone=? AND name=?)");
                int cut=key.indexOf('|');
                if(cut<0){args.add("");args.add("");}
                else {args.add(key.substring(0,cut));args.add(key.substring(cut+1));}
            }
            sql.append(")");
        }
        sql.append(" ORDER BY name COLLATE NOCASE ASC LIMIT ? OFFSET ?");
        args.add(String.valueOf(limit));args.add(String.valueOf(offset));
        List<Record> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery(sql.toString(),args.toArray(new String[0]))){
            while(c.moveToNext())out.add(new Record(c));
        }
        return out;
    }
    static String favKey(Record r){return r.phone+"|"+r.name;}
}
