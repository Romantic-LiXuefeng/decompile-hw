package tmsdkobf;

import android.database.sqlite.SQLiteDatabase;
import tmsdkobf.ko.a;

/* compiled from: Unknown */
public class ii extends ko {
    private static final a rE = new a() {
        public void onCreate(SQLiteDatabase sQLiteDatabase) {
            sQLiteDatabase.execSQL("CREATE TABLE IF NOT EXISTS rqd_thread (_id INTEGER PRIMARY KEY,mid LONG,name TEXT,ut LONG,ct LONG)");
            sQLiteDatabase.execSQL("CREATE TABLE IF NOT EXISTS rqd_ui (_id INTEGER PRIMARY KEY,name TEXT,ut LONG)");
            sQLiteDatabase.execSQL("CREATE TABLE IF NOT EXISTS rqd_dev (_id INTEGER PRIMARY KEY,id LONG,vl TEXT)");
            sQLiteDatabase.execSQL("CREATE TABLE IF NOT EXISTS rqd_mod (_id INTEGER PRIMARY KEY,mid INTEGER,ut LONG,sz INTEGER)");
            sQLiteDatabase.execSQL("CREATE TABLE IF NOT EXISTS gd_info (_id INTEGER PRIMARY KEY,type INTEGER,data TEXT)");
        }

        public void onDowngrade(SQLiteDatabase sQLiteDatabase, int i, int i2) {
            sQLiteDatabase.execSQL("CREATE TABLE IF NOT EXISTS rqd_thread (_id INTEGER PRIMARY KEY,mid LONG,name TEXT,ut LONG,ct LONG)");
            sQLiteDatabase.execSQL("CREATE TABLE IF NOT EXISTS rqd_ui (_id INTEGER PRIMARY KEY,name TEXT,ut LONG)");
            sQLiteDatabase.execSQL("CREATE TABLE IF NOT EXISTS rqd_dev (_id INTEGER PRIMARY KEY,id LONG,vl TEXT)");
            sQLiteDatabase.execSQL("CREATE TABLE IF NOT EXISTS rqd_mod (_id INTEGER PRIMARY KEY,mid INTEGER,ut LONG,sz INTEGER)");
            sQLiteDatabase.execSQL("CREATE TABLE IF NOT EXISTS gd_info (_id INTEGER PRIMARY KEY,type INTEGER,data TEXT)");
        }

        public void onUpgrade(SQLiteDatabase sQLiteDatabase, int i, int i2) {
            sQLiteDatabase.execSQL("CREATE TABLE IF NOT EXISTS rqd_thread (_id INTEGER PRIMARY KEY,mid LONG,name TEXT,ut LONG,ct LONG)");
            sQLiteDatabase.execSQL("CREATE TABLE IF NOT EXISTS rqd_ui (_id INTEGER PRIMARY KEY,name TEXT,ut LONG)");
            sQLiteDatabase.execSQL("CREATE TABLE IF NOT EXISTS rqd_dev (_id INTEGER PRIMARY KEY,id LONG,vl TEXT)");
            sQLiteDatabase.execSQL("CREATE TABLE IF NOT EXISTS rqd_mod (_id INTEGER PRIMARY KEY,mid INTEGER,ut LONG,sz INTEGER)");
            sQLiteDatabase.execSQL("CREATE TABLE IF NOT EXISTS gd_info (_id INTEGER PRIMARY KEY,type INTEGER,data TEXT)");
        }
    };

    public ii() {
        super("meriExt.db", 4, rE);
    }
}
