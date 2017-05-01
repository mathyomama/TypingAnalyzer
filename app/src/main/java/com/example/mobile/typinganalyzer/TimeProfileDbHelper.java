package com.example.mobile.typinganalyzer;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import android.widget.Toast;


public class TimeProfileDbHelper extends SQLiteOpenHelper {
    public TimeProfileDbHelper(Context context) {
        super(context, TimeProfileContract.DATABASE_NAME, null, TimeProfileContract.DATABASE_VERSION);

    }

    public void onCreate(SQLiteDatabase db) {
        //Toast.makeText(this.context, "creating db helper", Toast.LENGTH_SHORT).show();
        db.execSQL(TimeProfileContract.SQL_CREATE_ENTRIES);
    }

    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.i("Deleting", "deleting the database");
        db.execSQL(TimeProfileContract.SQL_DELETE_ENTRIES);
        onCreate(db);
    }

    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }
}
