package com.app.puzzle;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class ScoreDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "puzzle_scores.db";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_NAME = "scores";
    private static final String COLUMN_ID = "_id";
    private static final String COLUMN_NAME = "player";
    private static final String COLUMN_MOVES = "moves";
    private static final String COLUMN_TIMESTAMP = "timestamp";

    public ScoreDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createStatement = "CREATE TABLE " + TABLE_NAME + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_NAME + " TEXT NOT NULL, " +
                COLUMN_MOVES + " INTEGER NOT NULL, " +
                COLUMN_TIMESTAMP + " INTEGER NOT NULL" +
                ")";
        db.execSQL(createStatement);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

    public long insertScore(String name, int moves) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_NAME, name);
        values.put(COLUMN_MOVES, moves);
        values.put(COLUMN_TIMESTAMP, System.currentTimeMillis());
        return db.insert(TABLE_NAME, null, values);
    }

    public List<ScoreEntry> getTopScores(int limit) {
        SQLiteDatabase db = getReadableDatabase();
        List<ScoreEntry> scores = new ArrayList<>();
        Cursor cursor = db.query(TABLE_NAME,
                null,
                null,
                null,
                null,
                null,
                COLUMN_MOVES + " ASC, " + COLUMN_TIMESTAMP + " ASC",
                String.valueOf(limit));
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    String name = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME));
                    int moves = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_MOVES));
                    long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP));
                    scores.add(new ScoreEntry(name, moves, timestamp));
                }
            } finally {
                cursor.close();
            }
        }
        return scores;
    }
}
