package tmsdkobf;

import android.database.sqlite.SQLiteDatabase;
import tmsdk.common.utils.d;
import tmsdkobf.ko.a;

/* compiled from: Unknown */
public class id extends ko {
    public static final a rE = new a() {
        public void onCreate(SQLiteDatabase sQLiteDatabase) {
            sQLiteDatabase.execSQL("CREATE TABLE IF NOT EXISTS team_tables(team_name TEXT primary key, team_version int not null)");
            d.d("QQSecureProvider", "onCreate");
            id.c(sQLiteDatabase);
        }

        public void onDowngrade(SQLiteDatabase sQLiteDatabase, int i, int i2) {
            sQLiteDatabase.execSQL("CREATE TABLE IF NOT EXISTS team_tables(team_name TEXT primary key, team_version int not null)");
            id.f(sQLiteDatabase, i, i2);
        }

        public void onUpgrade(SQLiteDatabase sQLiteDatabase, int i, int i2) {
            sQLiteDatabase.execSQL("CREATE TABLE IF NOT EXISTS team_tables(team_name TEXT primary key, team_version int not null)");
            if (i2 >= i) {
                id.c(sQLiteDatabase, i, i2);
            } else {
                id.f(sQLiteDatabase, i, i2);
            }
        }
    };

    public id() {
        super("qqsecure.db", 18, rE);
    }

    private static void c(SQLiteDatabase sQLiteDatabase) {
        d.e("QQSecureProvider", "invoke createPhoneSqliteData");
        d(sQLiteDatabase);
        gf.a(sQLiteDatabase);
        hs.a(sQLiteDatabase);
        kt.a(sQLiteDatabase);
        ky.a(sQLiteDatabase);
        hy.a(sQLiteDatabase);
    }

    private static void c(SQLiteDatabase sQLiteDatabase, int i, int i2) {
        d.e("QQSecureProvider", "invoke upgradePhoneSqliteData");
        d(sQLiteDatabase, i, i2);
        d(sQLiteDatabase);
        e(sQLiteDatabase, i, i2);
        gf.a(sQLiteDatabase, i, i2);
        hs.a(sQLiteDatabase, i, i2);
        kt.a(sQLiteDatabase, i, i2);
        ky.a(sQLiteDatabase, i, i2);
        hy.a(sQLiteDatabase, i, i2);
    }

    private static void d(SQLiteDatabase sQLiteDatabase) {
        d.d("QQSecureProvider", "createNetwork CREATE TABLE IF NOT EXISTS operator_data_sync_result (id INTEGER PRIMARY KEY,type INTEGER,error_code INTEGER,timestamp INTEGER,area_code TEXT,sim_type TEXT,query_code TEXT,sms TEXT,trigger_type INTEGER,total_setting INTEGER,used_setting INTEGER,fix_template_type INTEGER,value_old INTEGER,value_new INTEGER,software_version TEXT,addtion TEXT)");
        d.d("QQSecureProvider", "createNetwork CREATE TABLE IF NOT EXISTS network_shark_save (id INTEGER PRIMARY KEY,com INTEGER,str TEXT)");
        sQLiteDatabase.execSQL("CREATE TABLE IF NOT EXISTS network_filter (uid INTEGER,filter_ip TEXT,pkg_name TEXT,app_name TEXT,is_allow_network BOOLEAN,is_allow_network_wifi BOOLEAN,is_sys_app BOOLEAN)");
        sQLiteDatabase.execSQL("CREATE TABLE IF NOT EXISTS networK (id INTEGER PRIMARY KEY,date LONG,data LONG,type INTEGER,imsi TEXT,flag TEXT)");
        sQLiteDatabase.execSQL("CREATE TABLE IF NOT EXISTS operator_data_sync_result (id INTEGER PRIMARY KEY,type INTEGER,error_code INTEGER,timestamp INTEGER,area_code TEXT,sim_type TEXT,query_code TEXT,sms TEXT,trigger_type INTEGER,total_setting INTEGER,used_setting INTEGER,fix_template_type INTEGER,value_old INTEGER,value_new INTEGER,software_version TEXT,addtion TEXT)");
        sQLiteDatabase.execSQL("CREATE TABLE IF NOT EXISTS network_shark_save (id INTEGER PRIMARY KEY,com INTEGER,str TEXT)");
    }

    private static void d(SQLiteDatabase sQLiteDatabase, int i, int i2) {
        d.d("QQSecureProvider", "^^ upgradeNetworkFilter oldVersion " + i);
        if (i < 8) {
            d.d("QQSecureProvider", "^^ upgradeNetworkFilter newVersion " + i2);
            String str = "ALTER TABLE network_filter ADD COLUMN filter_ip TEXT";
            String str2 = "ALTER TABLE network_filter ADD COLUMN is_allow_network_wifi BOOLEAN";
            String str3 = "UPDATE network_filter SET is_allow_network_wifi = 1";
            d.e("QQSecureProvider", "when TB_NETWORK_FILTER, alter: " + str);
            d.e("QQSecureProvider", "when TB_NETWORK_FILTER, alter: " + str2);
            d.e("QQSecureProvider", "when TB_NETWORK_FILTER, update: " + str3);
            sQLiteDatabase.execSQL(str);
            sQLiteDatabase.execSQL(str2);
            sQLiteDatabase.execSQL(str3);
        }
    }

    private static void e(SQLiteDatabase sQLiteDatabase) {
        sQLiteDatabase.execSQL("DROP TABLE IF EXISTS network_filter");
        sQLiteDatabase.execSQL("DROP TABLE IF EXISTS networK");
        sQLiteDatabase.execSQL("DROP TABLE IF EXISTS operator_data_sync_result");
    }

    private static void e(SQLiteDatabase sQLiteDatabase, int i, int i2) {
        d.d("QQSecureProvider", "^^ upgradeTrafficReport oldVersion " + i);
        if (i < 8) {
            d.d("QQSecureProvider", "^^ upgradeTrafficReport newVersion " + i2);
            String str = "ALTER TABLE operator_data_sync_result ADD COLUMN addtion TEXT";
            d.e("QQSecureProvider", "when upgradeTrafficReport, alter: " + str);
            sQLiteDatabase.execSQL(str);
        }
    }

    private static void f(SQLiteDatabase sQLiteDatabase, int i, int i2) {
        d.e("QQSecureProvider", "invoke downgradePhoneSqliteData");
        e(sQLiteDatabase);
        d(sQLiteDatabase);
        gf.b(sQLiteDatabase, i, i2);
        hs.b(sQLiteDatabase, i, i2);
        kt.b(sQLiteDatabase, i, i2);
        ky.b(sQLiteDatabase, i, i2);
        hy.b(sQLiteDatabase, i, i2);
    }
}
