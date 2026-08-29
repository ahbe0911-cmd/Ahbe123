package ir.ahmad.speechtexter.twa;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TranscriptRepository extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "speechtexter.db";
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE = "transcripts";
    private static final int MAX_HISTORY_ITEMS = 500;

    public TranscriptRepository(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL(
                "CREATE TABLE " + TABLE + " ("
                        + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "content TEXT NOT NULL,"
                        + "content_hash TEXT NOT NULL UNIQUE,"
                        + "language TEXT NOT NULL,"
                        + "created_at INTEGER NOT NULL,"
                        + "updated_at INTEGER NOT NULL)"
        );
        database.execSQL(
                "CREATE INDEX transcripts_updated_at ON " + TABLE + " (updated_at DESC)"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        // Version one is the initial local-only schema. Future migrations stay additive here.
    }

    public long save(String rawContent, String language) {
        String content = rawContent == null ? "" : rawContent.trim();
        if (content.isEmpty()) {
            return -1L;
        }

        String hash = sha256(content);
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("content", content);
        values.put("content_hash", hash);
        values.put("language", language == null ? "" : language);
        values.put("created_at", now);
        values.put("updated_at", now);

        SQLiteDatabase database = getWritableDatabase();
        long id = database.insertWithOnConflict(
                TABLE,
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE
        );
        if (id == -1L) {
            ContentValues update = new ContentValues();
            update.put("language", language == null ? "" : language);
            update.put("updated_at", now);
            database.update(TABLE, update, "content_hash = ?", new String[]{hash});
            id = findIdByHash(database, hash);
        }
        trimOldRows(database);
        return id;
    }

    @NonNull
    public List<Entry> search(String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        String selection = null;
        String[] arguments = null;
        if (!query.isEmpty()) {
            selection = "content LIKE ? ESCAPE '!'";
            arguments = new String[]{"%" + escapeLike(query) + "%"};
        }

        ArrayList<Entry> entries = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                TABLE,
                new String[]{"_id", "content", "language", "created_at", "updated_at"},
                selection,
                arguments,
                null,
                null,
                "updated_at DESC",
                "200"
        )) {
            int idColumn = cursor.getColumnIndexOrThrow("_id");
            int contentColumn = cursor.getColumnIndexOrThrow("content");
            int languageColumn = cursor.getColumnIndexOrThrow("language");
            int createdColumn = cursor.getColumnIndexOrThrow("created_at");
            int updatedColumn = cursor.getColumnIndexOrThrow("updated_at");
            while (cursor.moveToNext()) {
                entries.add(new Entry(
                        cursor.getLong(idColumn),
                        cursor.getString(contentColumn),
                        cursor.getString(languageColumn),
                        cursor.getLong(createdColumn),
                        cursor.getLong(updatedColumn)
                ));
            }
        }
        return Collections.unmodifiableList(entries);
    }

    public void delete(long id) {
        getWritableDatabase().delete(TABLE, "_id = ?", new String[]{Long.toString(id)});
    }

    public void deleteAll() {
        getWritableDatabase().delete(TABLE, null, null);
    }

    private static long findIdByHash(SQLiteDatabase database, String hash) {
        try (Cursor cursor = database.query(
                TABLE,
                new String[]{"_id"},
                "content_hash = ?",
                new String[]{hash},
                null,
                null,
                null,
                "1"
        )) {
            return cursor.moveToFirst() ? cursor.getLong(0) : -1L;
        }
    }

    private static void trimOldRows(SQLiteDatabase database) {
        database.execSQL(
                "DELETE FROM " + TABLE + " WHERE _id NOT IN ("
                        + "SELECT _id FROM " + TABLE
                        + " ORDER BY updated_at DESC LIMIT " + MAX_HISTORY_ITEMS + ")"
        );
    }

    private static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            char[] characters = new char[digest.length * 2];
            char[] alphabet = "0123456789abcdef".toCharArray();
            for (int index = 0; index < digest.length; index++) {
                int unsigned = digest[index] & 0xff;
                characters[index * 2] = alphabet[unsigned >>> 4];
                characters[index * 2 + 1] = alphabet[unsigned & 0x0f];
            }
            return new String(characters);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public static final class Entry {
        public final long id;
        public final String content;
        public final String language;
        public final long createdAt;
        public final long updatedAt;

        private Entry(
                long id,
                String content,
                String language,
                long createdAt,
                long updatedAt
        ) {
            this.id = id;
            this.content = content;
            this.language = language;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }
    }
}
