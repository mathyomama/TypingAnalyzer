package com.example.mobile.typinganalyzer;

import android.provider.BaseColumns;


public final class TimeProfileContract {
    public static final String DATABASE_NAME = "time_profiles.db";
    public static final int DATABASE_VERSION = 1;

    public static final String SQL_CREATE_ENTRIES =
            "CREATE TABLE " + TimeProfile.TABLE_NAME + " (" +
                    TimeProfile._ID + " INTEGER PRIMARY KEY," +
                    TimeProfile.C_WORD + " TEXT," +
                    TimeProfile.C_COUNT + " INTEGER," +
                    " d0_avg REAL," +
                    " d0_var REAL," +
                    " l0_avg REAL," +
                    " l0_var REAL," +
                    " d1_avg REAL," +
                    " d1_var REAL," +
                    " l1_avg REAL," +
                    " l1_var REAL," +
                    " d2_avg REAL," +
                    " d2_var REAL," +
                    " l2_avg REAL," +
                    " l2_var REAL," +
                    " d3_avg REAL," +
                    " d3_var REAL," +
                    " l3_avg REAL," +
                    " l3_var REAL," +
                    " d4_avg REAL," +
                    " d4_var REAL," +
                    " l4_avg REAL," +
                    " l4_var REAL," +
                    " d5_avg REAL," +
                    " d5_var REAL," +
                    " l5_avg REAL," +
                    " l5_var REAL," +
                    " d6_avg REAL," +
                    " d6_var REAL," +
                    " l6_avg REAL," +
                    " l6_var REAL," +
                    " d7_avg REAL," +
                    " d7_var REAL," +
                    " l7_avg REAL," +
                    " l7_var REAL," +
                    " d8_avg REAL," +
                    " d8_var REAL," +
                    " l8_avg REAL," +
                    " l8_var REAL," +
                    " d9_avg REAL," +
                    " d9_var REAL," +
                    " l9_avg REAL," +
                    " l9_var REAL," +
                    " d10_avg REAL," +
                    " d10_var REAL," +
                    " l10_avg REAL," +
                    " l10_var REAL," +
                    " d11_avg REAL," +
                    " d11_var REAL," +
                    " l11_avg REAL," +
                    " l11_var REAL," +
                    " d12_avg REAL," +
                    " d12_var REAL," +
                    " l12_avg REAL," +
                    " l12_var REAL," +
                    " d13_avg REAL," +
                    " d13_var REAL," +
                    " UNIQUE(" + TimeProfile.C_WORD + "))";

    public static final String SQL_DELETE_ENTRIES =
            "DROP TABLE IF EXISTS " + TimeProfile.TABLE_NAME;

    private TimeProfileContract() {}

    public static class TimeProfile implements BaseColumns {
        public static final String TABLE_NAME = "profiles";
        public static final String C_WORD = "word";
        public static final String C_COUNT = "count";
    }
}
