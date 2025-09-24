package com.app.puzzle.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ScoreDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "puzzle_scores.db";
    private static final int DATABASE_VERSION = 8;

    public static final String TABLE_SCORES = "scores";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_NICKNAME = "nickname";
    public static final String COLUMN_PLAYER = "player";
    public static final String COLUMN_DURATION = "duration";
    public static final String COLUMN_MOVES = "moves";
    public static final String COLUMN_CREATED_AT = "created_at";
    private static final String LEGACY_COLUMN_TIMESTAMP = "timestamp";
    public static final String COLUMN_LEVEL = "level";

    private static final String TEMP_TABLE_NAME = TABLE_SCORES + "_new";

    private static final String SQL_CREATE = createTableSql(TABLE_SCORES);

    public ScoreDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        ensureDatabaseReady(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        ensureDatabaseReady(db);
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        ensureDatabaseReady(db);
    }

    private boolean tableExists(SQLiteDatabase db, String tableName) {
        boolean exists = false;
        try (Cursor cursor = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                new String[]{tableName}
        )) {
            exists = cursor != null && cursor.moveToFirst();
        }
        return exists;
    }

    private void ensureScoresTableExists(SQLiteDatabase db) {
        if (!tableExists(db, TABLE_SCORES)) {
            db.execSQL(SQL_CREATE);
            ensureNicknameIndex(db);
        }
    }

    private void ensureDatabaseReady(SQLiteDatabase db) {
        if (db == null) {
            return;
        }
        ensureScoresTableExists(db);
        reconcileSchema(db);
        ensureNicknameIndex(db);
    }

    private void reconcileSchema(SQLiteDatabase db) {
        if (!tableExists(db, TABLE_SCORES)) {
            db.execSQL(SQL_CREATE);
            return;
        }
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("PRAGMA table_info(" + TABLE_SCORES + ")", null);
            Set<String> existingColumns = new HashSet<>();
            if (cursor != null) {
                int nameIndex = cursor.getColumnIndex("name");
                while (cursor.moveToNext()) {
                    existingColumns.add(cursor.getString(nameIndex));
                }
            }
            boolean hasLegacyTimestamp = existingColumns.contains(LEGACY_COLUMN_TIMESTAMP);
            if (!existingColumns.contains(COLUMN_ID)
                    || !existingColumns.contains(COLUMN_NICKNAME)
                    || !existingColumns.contains(COLUMN_PLAYER)
                    || !existingColumns.contains(COLUMN_DURATION)
                    || !existingColumns.contains(COLUMN_MOVES)
                    || !existingColumns.contains(COLUMN_LEVEL)
                    || !existingColumns.contains(COLUMN_CREATED_AT)
                    || hasLegacyTimestamp) {
                rebuildScoresTable(db, existingColumns);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void rebuildScoresTable(SQLiteDatabase db, Set<String> existingColumns) {
        db.execSQL("DROP TABLE IF EXISTS " + TEMP_TABLE_NAME);
        db.beginTransaction();
        try {
            db.execSQL(createTableSql(TEMP_TABLE_NAME));

            List<String> destinationColumns = new ArrayList<>();
            List<String> sourceExpressions = new ArrayList<>();
            addColumnMapping(existingColumns, destinationColumns, sourceExpressions, COLUMN_NICKNAME, "''");
            addColumnMapping(existingColumns, destinationColumns, sourceExpressions, COLUMN_PLAYER, "''");
            addColumnMapping(existingColumns, destinationColumns, sourceExpressions, COLUMN_DURATION, "0");
            addColumnMapping(existingColumns, destinationColumns, sourceExpressions, COLUMN_MOVES, "0");
            addColumnMapping(existingColumns, destinationColumns, sourceExpressions, COLUMN_LEVEL, "1");
            addColumnMapping(existingColumns, destinationColumns, sourceExpressions, COLUMN_CREATED_AT, String.valueOf(System.currentTimeMillis()));

            if (!destinationColumns.isEmpty()) {
                String insertSql = "INSERT INTO " + TEMP_TABLE_NAME + " (" + joinColumns(destinationColumns)
                        + ") SELECT " + joinColumns(sourceExpressions)
                        + " FROM " + TABLE_SCORES;
                db.execSQL(insertSql);
            }

            db.execSQL("DROP TABLE IF EXISTS " + TABLE_SCORES);
            db.execSQL("ALTER TABLE " + TEMP_TABLE_NAME + " RENAME TO " + TABLE_SCORES);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private static String createTableSql(String tableName) {
        return "CREATE TABLE " + tableName + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_NICKNAME + " TEXT NOT NULL,"
                + COLUMN_PLAYER + " TEXT NOT NULL,"
                + COLUMN_DURATION + " INTEGER NOT NULL,"
                + COLUMN_MOVES + " INTEGER NOT NULL,"
                + COLUMN_LEVEL + " INTEGER NOT NULL,"
                + COLUMN_CREATED_AT + " INTEGER NOT NULL"
                + ")";
    }

    private void addColumnMapping(Set<String> existingColumns,
                                  List<String> destinationColumns,
                                  List<String> sourceExpressions,
                                  String columnName,
                                  String defaultExpression) {
        String expression;
        if (existingColumns.contains(columnName)) {
            expression = columnName;
        } else if (COLUMN_CREATED_AT.equals(columnName) && existingColumns.contains(LEGACY_COLUMN_TIMESTAMP)) {
            expression = LEGACY_COLUMN_TIMESTAMP;
        } else if (COLUMN_PLAYER.equals(columnName) && existingColumns.contains(COLUMN_NICKNAME)) {
            expression = COLUMN_NICKNAME;
        } else if (COLUMN_NICKNAME.equals(columnName) && existingColumns.contains(COLUMN_PLAYER)) {
            expression = COLUMN_PLAYER;
        } else {
            expression = defaultExpression;
        }
        destinationColumns.add(columnName);
        sourceExpressions.add(expression);
    }

    private String joinColumns(List<String> columns) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(columns.get(i));
        }
        return builder.toString();
    }

    private void ensureNicknameIndex(SQLiteDatabase db) {
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_scores_nickname ON "
                + TABLE_SCORES + "(" + COLUMN_NICKNAME + ")");
    }

    public long upsertScore(String nickname, long totalDurationMillis, long totalMoves, int levelReached) {
        ScoreEntry existing = findScoreByNickname(nickname);
        return upsertScore(existing, nickname, totalDurationMillis, totalMoves, levelReached);
    }

    public long upsertScore(ScoreEntry existingScore, String nickname, long totalDurationMillis, long totalMoves, int levelReached) {
        SQLiteDatabase db = getWritableDatabase();
        ensureDatabaseReady(db);
        long now = System.currentTimeMillis();
        if (existingScore == null) {
            ContentValues values = new ContentValues();
            values.put(COLUMN_NICKNAME, nickname);
            values.put(COLUMN_PLAYER, nickname);
            values.put(COLUMN_DURATION, totalDurationMillis);
            values.put(COLUMN_MOVES, totalMoves);
            values.put(COLUMN_LEVEL, levelReached);
            values.put(COLUMN_CREATED_AT, now);
            return db.insert(TABLE_SCORES, null, values);
        } else {
            ContentValues values = new ContentValues();
            values.put(COLUMN_PLAYER, nickname);
            values.put(COLUMN_DURATION, totalDurationMillis);
            values.put(COLUMN_MOVES, totalMoves);
            values.put(COLUMN_LEVEL, levelReached);
            int updated = db.update(
                    TABLE_SCORES,
                    values,
                    COLUMN_ID + " = ?",
                    new String[]{String.valueOf(existingScore.getId())}
            );
            return updated > 0 ? existingScore.getId() : -1;
        }
    }

    public List<ScoreEntry> getAllScores() {
        return queryScores(null, null);
    }

    public List<ScoreEntry> searchScores(String query) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isEmpty()) {
            return getAllScores();
        }
        String likeExpression = "%" + normalizedQuery + "%";
        String selection = "(" + COLUMN_NICKNAME + " LIKE ? COLLATE NOCASE OR "
                + COLUMN_PLAYER + " LIKE ? COLLATE NOCASE)";
        String[] selectionArgs = new String[]{likeExpression, likeExpression};
        return queryScores(selection, selectionArgs);
    }

    private List<ScoreEntry> queryScores(String selection, String[] selectionArgs) {
        List<ScoreEntry> scores = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        ensureDatabaseReady(db);
        try (Cursor cursor = db.query(
                TABLE_SCORES,
                null,
                selection,
                selectionArgs,
                null,
                null,
                COLUMN_DURATION + " ASC, " + COLUMN_MOVES + " ASC, " + COLUMN_LEVEL + " DESC, " + COLUMN_CREATED_AT + " ASC"
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    long id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID));
                    String nickname = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NICKNAME));
                    long duration = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DURATION));
                    long moves = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_MOVES));
                    long createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT));
                    int level = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_LEVEL));
                    scores.add(new ScoreEntry(id, nickname, duration, moves, createdAt, level));
                } while (cursor.moveToNext());
            }
        }
        return scores;
    }

    public ScoreEntry findScoreByNickname(String nickname) {
        if (nickname == null || nickname.isEmpty()) {
            return null;
        }
        SQLiteDatabase db = getReadableDatabase();
        ensureDatabaseReady(db);
        try (Cursor cursor = db.query(
                TABLE_SCORES,
                null,
                COLUMN_NICKNAME + " = ?",
                new String[]{nickname},
                null,
                null,
                null,
                "1"
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID));
                long duration = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DURATION));
                long moves = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_MOVES));
                long createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT));
                int level = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_LEVEL));
                return new ScoreEntry(id, nickname, duration, moves, createdAt, level);
            }
        }
        return null;
    }
}
