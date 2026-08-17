package juloo.keyboard2;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** Small disposable exact-request summary cache, excluded from backup. */
final class ReaderAiCache extends SQLiteOpenHelper
{
  static final String DATABASE_NAME = "reader_ai_cache.db";
  private static final int DATABASE_VERSION = 1;
  private static final String TABLE = "summary_cache";
  private static final int MAX_ROWS = 50;
  private static final int MAX_RESULT_LENGTH = 2 * 1024 * 1024;

  ReaderAiCache(Context context)
  {
    super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
  }

  @Override public void onCreate(SQLiteDatabase database)
  {
    database.execSQL("CREATE TABLE " + TABLE + " ("
      + "cache_key TEXT PRIMARY KEY, result_markdown TEXT NOT NULL,"
      + "created_at INTEGER NOT NULL, last_accessed_at INTEGER NOT NULL)");
    database.execSQL("CREATE INDEX summary_cache_access ON " + TABLE
        + " (last_accessed_at DESC)");
  }

  @Override public void onUpgrade(SQLiteDatabase database, int oldVersion,
      int newVersion)
  {
    database.execSQL("DROP TABLE IF EXISTS " + TABLE);
    onCreate(database);
  }

  synchronized void put(String cacheKey, String resultMarkdown)
  {
    if (cacheKey == null || cacheKey.length() != 64)
      throw new IllegalArgumentException("Invalid Reader AI cache key");
    if (resultMarkdown == null || resultMarkdown.trim().isEmpty()
        || resultMarkdown.length() > MAX_RESULT_LENGTH)
      throw new IllegalArgumentException("Invalid Reader AI cache result");
    long now = System.currentTimeMillis();
    ContentValues values = new ContentValues();
    values.put("cache_key", cacheKey);
    values.put("result_markdown", resultMarkdown);
    values.put("created_at", now);
    values.put("last_accessed_at", now);
    SQLiteDatabase database = getWritableDatabase();
    database.beginTransaction();
    try
    {
      database.insertWithOnConflict(TABLE, null, values,
          SQLiteDatabase.CONFLICT_REPLACE);
      database.execSQL("DELETE FROM " + TABLE + " WHERE cache_key IN ("
          + "SELECT cache_key FROM " + TABLE
          + " ORDER BY last_accessed_at DESC LIMIT -1 OFFSET " + MAX_ROWS + ")");
      database.setTransactionSuccessful();
    }
    finally
    {
      database.endTransaction();
    }
  }

  synchronized String get(String cacheKey)
  {
    if (cacheKey == null || cacheKey.length() != 64)
      return null;
    String result = null;
    try (Cursor cursor = getReadableDatabase().query(TABLE,
          new String[]{"result_markdown"}, "cache_key = ?",
          new String[]{cacheKey}, null, null, null, "1"))
    {
      if (cursor.moveToFirst())
        result = cursor.getString(0);
    }
    if (result != null)
    {
      ContentValues values = new ContentValues();
      values.put("last_accessed_at", System.currentTimeMillis());
      getWritableDatabase().update(TABLE, values, "cache_key = ?",
          new String[]{cacheKey});
    }
    return result;
  }

  synchronized void clear()
  {
    getWritableDatabase().delete(TABLE, null, null);
  }
}
