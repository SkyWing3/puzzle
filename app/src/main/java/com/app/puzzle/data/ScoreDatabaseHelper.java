package com.app.puzzle.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class ScoreDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "puzzle_scores.db";
    private static final int DATABASE_VERSION = 2;

    public static final String TABLE_SCORES = "scores";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_NICKNAME = "nickname";
    public static final String COLUMN_DURATION = "duration";
    public static final String COLUMN_CREATED_AT = "created_at";

    private static final String SQL_CREATE =
            "CREATE TABLE " + TABLE_SCORES + " ("
                    + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + COLUMN_NICKNAME + " TEXT NOT NULL,"
                    + COLUMN_DURATION + " INTEGER NOT NULL,"
                    + COLUMN_CREATED_AT + " INTEGER NOT NULL"
                    + ")";

    public ScoreDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            addColumnIfMissing(db, COLUMN_DURATION, "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(db, COLUMN_CREATED_AT, "INTEGER NOT NULL DEFAULT 0");
        }
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        addColumnIfMissing(db, COLUMN_DURATION, "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(db, COLUMN_CREATED_AT, "INTEGER NOT NULL DEFAULT 0");
    }

    private void addColumnIfMissing(SQLiteDatabase db, String columnName, String columnDefinition) {
        if (!columnExists(db, TABLE_SCORES, columnName)) {
            db.execSQL("ALTER TABLE " + TABLE_SCORES + " ADD COLUMN " + columnName + " " + columnDefinition);
        }
    }

    private boolean columnExists(SQLiteDatabase db, String tableName, String columnName) {
        boolean exists = false;
        try (Cursor cursor = db.rawQuery(
                "PRAGMA table_info(" + tableName + ")",
                null
        )) {
            if (cursor != null) {
                int nameIndex = cursor.getColumnIndex("name");
                while (cursor.moveToNext()) {
                    if (columnName.equalsIgnoreCase(cursor.getString(nameIndex))) {
                        exists = true;
                        break;
                    }
                }
            }
        }
        return exists;
    }

    public long insertScore(String nickname, long durationMillis) {
        long timestamp = System.currentTimeMillis();
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_NICKNAME, nickname);
        values.put(COLUMN_DURATION, durationMillis);
        values.put(COLUMN_CREATED_AT, timestamp);
        return db.insert(TABLE_SCORES, null, values);
    }

    public List<ScoreEntry> getAllScores() {
        return queryScores(null, null);
    }

    public List<ScoreEntry> searchScores(String query) {
        if (query == null || query.trim().isEmpty()) {
            return getAllScores();
        }
        String selection = COLUMN_NICKNAME + " LIKE ?";
        String[] selectionArgs = new String[]{"%" + query.trim() + "%"};
        return queryScores(selection, selectionArgs);
    }

    private List<ScoreEntry> queryScores(String selection, String[] selectionArgs) {
        List<ScoreEntry> scores = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(
                TABLE_SCORES,
                null,
                selection,
                selectionArgs,
                null,
                null,
                COLUMN_DURATION + " ASC, " + COLUMN_CREATED_AT + " ASC"
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    long id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID));
                    String nickname = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NICKNAME));
                    long duration = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DURATION));
                    long createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT));
                    scores.add(new ScoreEntry(id, nickname, duration, createdAt));
                } while (cursor.moveToNext());
            }
        }
        return scores;
    }
}
