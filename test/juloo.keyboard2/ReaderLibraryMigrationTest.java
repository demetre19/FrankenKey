package juloo.keyboard2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import org.robolectric.RuntimeEnvironment;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ReaderLibraryMigrationTest
{
  private Context _context;

  @Before public void setUp()
  {
    _context = RuntimeEnvironment.getApplication();
    _context.deleteDatabase("reader_library.db");
    _context.deleteDatabase(ReaderAiStore.DATABASE_NAME);
    _context.deleteDatabase(ReaderBookAiWorkStore.DATABASE_NAME);
  }

  @After public void tearDown()
  {
    _context.deleteDatabase("reader_library.db");
    _context.deleteDatabase(ReaderAiStore.DATABASE_NAME);
    _context.deleteDatabase(ReaderBookAiWorkStore.DATABASE_NAME);
  }

  @Test public void reader_v4_migrates_books_schema_without_losing_content()
      throws Exception
  {
    try (SQLiteDatabase database = open("reader_library.db"))
    {
      createReaderV4(database);
      database.execSQL("INSERT INTO reader_items (id, title, source_type, "
          + "created_at, updated_at, last_opened_at, content_hash, "
          + "import_state) VALUES ('legacy', 'Legacy EPUB', 'EPUB', 1, 2, 3, "
          + "'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', "
          + "'READY')");
      database.execSQL("INSERT INTO reader_content_units (item_id, ordinal, "
          + "kind, text) VALUES ('legacy', 0, 'chapter', 'kept text')");
      database.setVersion(4);
    }

    try (ReaderLibrary library = new ReaderLibrary(_context))
    {
      ReaderLibrary.Item item = library.get("legacy");
      assertNotNull("Migration must preserve the existing Reader item.", item);
      assertEquals("Migration must preserve private Reader content.",
          "kept text", item.units.get(0).text);
      SQLiteDatabase database = library.getReadableDatabase();
      assertEquals(5, database.getVersion());
      assertTrue(hasColumn(database, "reader_items", "tree_uri"));
      assertTrue(hasColumn(database, "reader_items", "raw_word_index"));
      assertTrue(hasColumn(database, "reader_items", "last_reader_mode"));
      assertTrue(tableExists(database, "reader_collections"));
      assertTrue(tableExists(database, "reader_item_collections"));
      assertTrue(tableExists(database, "reader_epub_settings"));
      try (Cursor cursor = database.rawQuery("SELECT source_state, favorite, "
          + "last_reader_mode, raw_word_index FROM reader_items WHERE id = ?",
          new String[]{"legacy"}))
      {
        assertTrue(cursor.moveToFirst());
        assertEquals("AVAILABLE", cursor.getString(0));
        assertEquals(0, cursor.getInt(1));
        assertEquals("CLASSIC", cursor.getString(2));
        assertEquals(0L, cursor.getLong(3));
      }
      try (Cursor cursor = database.rawQuery("SELECT global_last_reader_mode, "
          + "classic_theme, classic_text_size, classic_font_family, "
          + "classic_images_enabled FROM reader_epub_settings WHERE id = 1",
          null))
      {
        assertTrue("Migration must create exactly one valid settings record.",
            cursor.moveToFirst());
        assertEquals("CLASSIC", cursor.getString(0));
        assertEquals("DARK", cursor.getString(1));
        assertEquals(18f, cursor.getFloat(2), 0f);
        assertEquals("SERIF", cursor.getString(3));
        assertEquals(1, cursor.getInt(4));
        assertFalse(cursor.moveToNext());
      }
    }
  }

  @Test public void reader_migration_failure_rolls_back_every_schema_change()
  {
    try (SQLiteDatabase database = open("reader_library.db"))
    {
      createReaderV4(database);
      database.execSQL("CREATE TABLE reader_collections (id TEXT PRIMARY KEY)");
      database.setVersion(4);
    }

    try (ReaderLibrary library = new ReaderLibrary(_context))
    {
      library.getWritableDatabase();
      fail("A conflicting migration must fail instead of partially upgrading.");
    }
    catch (SQLiteException expected)
    {
      assertNotNull(expected.getMessage());
    }

    try (SQLiteDatabase database = open("reader_library.db"))
    {
      assertEquals("SQLiteOpenHelper must retain the old version after rollback.",
          4, database.getVersion());
      assertFalse("ALTER TABLE changes must roll back with the failed migration.",
          hasColumn(database, "reader_items", "tree_uri"));
      assertFalse(tableExists(database, "reader_epub_settings"));
    }
  }

  @Test public void reader_ai_v1_migrates_provenance_and_separates_work_cache()
  {
    try (SQLiteDatabase database = open(ReaderAiStore.DATABASE_NAME))
    {
      createReaderAiV1(database);
      database.execSQL("INSERT INTO saved_ai (reader_item_id, article_title, "
          + "content_type, content_markdown, source_url, source_host, model_id, "
          + "prompt_identity, created_at, updated_at) VALUES ('article', "
          + "'Saved article', 'SUMMARY_ONE', 'kept output', "
          + "'https://example.com/article', 'example.com', 'model', 'prompt', "
          + "1, 2)");
      database.setVersion(1);
    }

    try (ReaderAiStore store = new ReaderAiStore(_context))
    {
      ReaderAiStore.Entry entry = store.load(1L);
      assertNotNull("Migration must preserve saved Reader AI output.", entry);
      assertEquals("kept output", entry.contentMarkdown);
      SQLiteDatabase database = store.getReadableDatabase();
      assertEquals(3, database.getVersion());
      assertTrue(hasColumn(database, "saved_ai", "source_type"));
      assertTrue(hasColumn(database, "saved_ai", "book_fingerprint"));
      assertTrue(hasColumn(database, "saved_ai", "provenance"));
      assertFalse(tableExists(database, "book_ai_jobs"));
      assertFalse(tableExists(database, "book_ai_evidence"));
      try (Cursor cursor = database.rawQuery(
          "SELECT source_type, provenance FROM saved_ai WHERE id = 1", null))
      {
        assertTrue(cursor.moveToFirst());
        assertEquals("ARTICLE", cursor.getString(0));
        assertEquals("", cursor.getString(1));
      }
    }
  }

  @Test public void reader_ai_v2_migration_drops_only_disposable_work_tables()
  {
    try (SQLiteDatabase database = open(ReaderAiStore.DATABASE_NAME))
    {
      createReaderAiV1(database);
      database.execSQL("ALTER TABLE saved_ai ADD COLUMN source_type TEXT "
          + "NOT NULL DEFAULT 'ARTICLE'");
      database.execSQL("ALTER TABLE saved_ai ADD COLUMN book_fingerprint TEXT");
      database.execSQL("ALTER TABLE saved_ai ADD COLUMN provenance TEXT "
          + "NOT NULL DEFAULT ''");
      database.execSQL("CREATE TABLE book_ai_jobs (job_id TEXT PRIMARY KEY)");
      database.execSQL("CREATE TABLE book_ai_evidence "
          + "(evidence_id TEXT PRIMARY KEY)");
      database.execSQL("INSERT INTO saved_ai (article_title, content_type, "
          + "content_markdown, source_url, source_host, model_id, "
          + "prompt_identity, created_at, updated_at) VALUES "
          + "('Saved book', 'SUMMARY_ONE', 'durable output', '', '', "
          + "'model', 'prompt', 1, 2)");
      database.setVersion(2);
    }

    try (ReaderAiStore store = new ReaderAiStore(_context))
    {
      assertEquals("durable output", store.load(1L).contentMarkdown);
      SQLiteDatabase database = store.getReadableDatabase();
      assertEquals(3, database.getVersion());
      assertFalse(tableExists(database, "book_ai_jobs"));
      assertFalse(tableExists(database, "book_ai_evidence"));
    }
  }

  private SQLiteDatabase open(String name)
  {
    return _context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null);
  }

  private static void createReaderV4(SQLiteDatabase database)
  {
    database.execSQL("CREATE TABLE reader_items ("
        + "id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, "
        + "source_type TEXT NOT NULL, source_uri TEXT, mime_type TEXT, "
        + "author TEXT, language_tag TEXT, created_at INTEGER NOT NULL, "
        + "updated_at INTEGER NOT NULL, last_opened_at INTEGER NOT NULL, "
        + "progress_locator TEXT, progress_fraction REAL NOT NULL DEFAULT 0, "
        + "finished INTEGER NOT NULL DEFAULT 0, content_hash TEXT NOT NULL "
        + "UNIQUE, import_state TEXT NOT NULL, error_message TEXT, "
        + "image_uri TEXT)");
    database.execSQL("CREATE TABLE reader_content_units ("
        + "item_id TEXT NOT NULL REFERENCES reader_items(id) ON DELETE CASCADE, "
        + "ordinal INTEGER NOT NULL, kind TEXT NOT NULL, text TEXT NOT NULL, "
        + "language_tag TEXT, source_locator TEXT, asset_uri TEXT, "
        + "PRIMARY KEY(item_id, ordinal))");
  }

  private static void createReaderAiV1(SQLiteDatabase database)
  {
    database.execSQL("CREATE TABLE saved_ai ("
        + "id INTEGER PRIMARY KEY AUTOINCREMENT, reader_item_id TEXT, "
        + "article_title TEXT NOT NULL, content_type TEXT NOT NULL, "
        + "content_markdown TEXT NOT NULL, chat_markdown TEXT NOT NULL "
        + "DEFAULT '', source_url TEXT NOT NULL, source_host TEXT NOT NULL, "
        + "author TEXT NOT NULL DEFAULT '', favorite INTEGER NOT NULL "
        + "DEFAULT 0, model_id TEXT NOT NULL, prompt_identity TEXT NOT NULL, "
        + "created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
  }

  private static boolean hasColumn(SQLiteDatabase database, String table,
      String column)
  {
    try (Cursor cursor = database.rawQuery("PRAGMA table_info(" + table + ")",
          null))
    {
      int name = cursor.getColumnIndexOrThrow("name");
      while (cursor.moveToNext())
        if (column.equals(cursor.getString(name)))
          return true;
      return false;
    }
  }

  private static boolean tableExists(SQLiteDatabase database, String table)
  {
    try (Cursor cursor = database.rawQuery("SELECT 1 FROM sqlite_master "
        + "WHERE type = 'table' AND name = ?", new String[]{table}))
    {
      return cursor.moveToFirst();
    }
  }
}
