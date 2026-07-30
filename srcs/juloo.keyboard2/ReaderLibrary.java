package juloo.keyboard2;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** App-private persistence for normalized Reader documents and progress. */
public final class ReaderLibrary extends SQLiteOpenHelper
{
  private static final String DATABASE_NAME = "reader_library.db";
  private static final int DATABASE_VERSION = 2;
  private static final String CORRUPT_MESSAGE =
    "Reader Library record is corrupt.";
  private static final String UNSUPPORTED_MESSAGE =
    "This Reader Library record type is unsupported.";

  public enum SourceType
  {
    CLIPBOARD, SELECTED_TEXT, SHARED_TEXT, URL, EPUB, PDF, SCREEN_CAPTURE
  }

  public enum ImportState { IMPORTING, READY, FAILED, OCR_REQUIRED }

  public static final class ContentUnit
  {
    public final int ordinal;
    public final String kind;
    public final String text;
    public final String languageTag;
    public final String sourceLocator;

    public ContentUnit(int ordinal, String kind, String text,
        String languageTag, String sourceLocator)
    {
      this.ordinal = ordinal;
      this.kind = kind;
      this.text = text;
      this.languageTag = languageTag;
      this.sourceLocator = sourceLocator;
    }
  }

  public static final class Item
  {
    public final String id;
    public final String title;
    public final SourceType sourceType;
    public final String sourceUri;
    public final String mimeType;
    public final String author;
    public final String languageTag;
    public final long createdAt;
    public final long updatedAt;
    public final long lastOpenedAt;
    public final String progressLocator;
    public final float progressFraction;
    public final boolean finished;
    public final String contentHash;
    public final ImportState importState;
    public final String errorMessage;
    public final List<ContentUnit> units;

    public Item(String id, String title, SourceType sourceType, String sourceUri,
        String mimeType, String author, String languageTag, long createdAt,
        long updatedAt, long lastOpenedAt, String progressLocator,
        float progressFraction, boolean finished, String contentHash,
        ImportState importState, String errorMessage, List<ContentUnit> units)
    {
      this.id = id;
      this.title = title;
      this.sourceType = sourceType;
      this.sourceUri = sourceUri;
      this.mimeType = mimeType;
      this.author = author;
      this.languageTag = languageTag;
      this.createdAt = createdAt;
      this.updatedAt = updatedAt;
      this.lastOpenedAt = lastOpenedAt;
      this.progressLocator = progressLocator;
      this.progressFraction = progressFraction;
      this.finished = finished;
      this.contentHash = contentHash;
      this.importState = importState;
      this.errorMessage = errorMessage;
      this.units = Collections.unmodifiableList(new ArrayList<>(units));
    }
  }

  public static final class LibraryException extends Exception
  {
    LibraryException(String message) { super(message); }
    LibraryException(String message, Throwable cause) { super(message, cause); }
  }

  private final File _privateFilesRoot;

  public ReaderLibrary(Context context)
  {
    super(context.getApplicationContext(), DATABASE_NAME, null,
        DATABASE_VERSION);
    _privateFilesRoot = new File(context.getApplicationContext().getFilesDir(),
        "reader_library");
  }

  @Override public void onConfigure(SQLiteDatabase db)
  {
    db.setForeignKeyConstraintsEnabled(true);
  }

  @Override public void onCreate(SQLiteDatabase db)
  {
    createItems(db);
    createUnits(db);
  }

  @Override public void onUpgrade(SQLiteDatabase db, int oldVersion,
      int newVersion)
  {
    if (oldVersion < 1 || oldVersion > DATABASE_VERSION)
      throw new SQLiteException(UNSUPPORTED_MESSAGE);
    if (oldVersion == 1)
    {
      db.execSQL("ALTER TABLE reader_items ADD COLUMN error_message TEXT");
      oldVersion = 2;
    }
    if (oldVersion != newVersion)
      throw new SQLiteException(UNSUPPORTED_MESSAGE);
  }

  @Override public void onDowngrade(SQLiteDatabase db, int oldVersion,
      int newVersion)
  {
    throw new SQLiteException(UNSUPPORTED_MESSAGE);
  }

  /**
   * Imports normalized content. An existing content hash wins deterministically:
   * its stable id and progress are retained while metadata/content are replaced.
   */
  public Item importItem(Item incoming) throws LibraryException
  {
    validateIncoming(incoming);
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try
    {
      Item duplicate = findByHash(db, incoming.contentHash);
      Item stored = duplicate == null ? incoming :
        new Item(duplicate.id, incoming.title, incoming.sourceType,
            incoming.sourceUri, incoming.mimeType, incoming.author,
            incoming.languageTag, duplicate.createdAt, incoming.updatedAt,
            duplicate.lastOpenedAt, duplicate.progressLocator,
            duplicate.progressFraction, duplicate.finished,
            incoming.contentHash, incoming.importState, incoming.errorMessage,
            incoming.units);
      if (duplicate == null)
        deleteRows(db, incoming.id);
      putItem(db, stored);
      replaceUnits(db, stored.id, stored.units);
      db.setTransactionSuccessful();
      return stored;
    }
    catch (SQLiteException error)
    {
      throw new LibraryException(CORRUPT_MESSAGE, error);
    }
    finally
    {
      db.endTransaction();
    }
  }

  public Item get(String id) throws LibraryException
  {
    if (id == null || id.isEmpty())
      return null;
    try
    {
      return readItem(getReadableDatabase(), "id = ?", new String[] { id });
    }
    catch (SQLiteException error)
    {
      throw new LibraryException(CORRUPT_MESSAGE, error);
    }
  }

  public List<Item> list() throws LibraryException
  {
    ArrayList<Item> result = new ArrayList<>();
    SQLiteDatabase db = getReadableDatabase();
    try (Cursor cursor = db.query("reader_items", null, null, null, null, null,
        "last_opened_at DESC, created_at DESC, id ASC"))
    {
      while (cursor.moveToNext())
        result.add(readItem(db, cursor));
      return result;
    }
    catch (SQLiteException error)
    {
      throw new LibraryException(CORRUPT_MESSAGE, error);
    }
  }

  public void updateProgress(String id, String locator, float fraction,
      boolean finished, long lastOpenedAt) throws LibraryException
  {
    if (!Float.isFinite(fraction) || fraction < 0f || fraction > 1f)
      throw new LibraryException(CORRUPT_MESSAGE);
    ContentValues values = new ContentValues();
    values.put("progress_locator", locator);
    values.put("progress_fraction", fraction);
    values.put("finished", finished ? 1 : 0);
    values.put("last_opened_at", lastOpenedAt);
    if (getWritableDatabase().update("reader_items", values, "id = ?",
          new String[] { id }) != 1)
      throw new LibraryException(CORRUPT_MESSAGE);
  }

  public boolean delete(String id) throws LibraryException
  {
    Item item = get(id);
    if (item == null)
      return false;
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try
    {
      deleteRows(db, id);
      deleteOwnedSource(item.sourceUri);
      db.setTransactionSuccessful();
      return true;
    }
    catch (IOException | SQLiteException error)
    {
      throw new LibraryException("Reader item could not be deleted.", error);
    }
    finally
    {
      db.endTransaction();
    }
  }

  public int deleteAll() throws LibraryException
  {
    List<Item> items = list();
    for (Item item : items)
      delete(item.id);
    return items.size();
  }

  public File privateSourceFile(String relativeName) throws LibraryException
  {
    if (relativeName == null || relativeName.isEmpty())
      throw new LibraryException(UNSUPPORTED_MESSAGE);
    File candidate = new File(_privateFilesRoot, relativeName);
    try
    {
      String root = _privateFilesRoot.getCanonicalPath() + File.separator;
      if (!candidate.getCanonicalPath().startsWith(root))
        throw new LibraryException(UNSUPPORTED_MESSAGE);
      return candidate;
    }
    catch (IOException error)
    {
      throw new LibraryException(UNSUPPORTED_MESSAGE, error);
    }
  }

  public static String contentHash(List<ContentUnit> units)
      throws LibraryException
  {
    try
    {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (ContentUnit unit : units)
      {
        digest.update(normalizeText(unit.text).getBytes(
              java.nio.charset.StandardCharsets.UTF_8));
        digest.update((byte)0);
      }
      StringBuilder out = new StringBuilder();
      for (byte value : digest.digest())
        out.append(String.format(Locale.ROOT, "%02x", value & 0xff));
      return out.toString();
    }
    catch (NoSuchAlgorithmException impossible)
    {
      throw new LibraryException("Reader content identity is unavailable.",
          impossible);
    }
  }

  public static String normalizeText(String text)
  {
    return text == null ? "" : text.replace("\r\n", "\n")
      .replace('\r', '\n').replaceAll("[\\t\\x0B\\f ]+", " ").trim();
  }

  private static void createItems(SQLiteDatabase db)
  {
    db.execSQL("CREATE TABLE reader_items (" +
      "id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, " +
      "source_type TEXT NOT NULL, source_uri TEXT, mime_type TEXT, " +
      "author TEXT, language_tag TEXT, created_at INTEGER NOT NULL, " +
      "updated_at INTEGER NOT NULL, last_opened_at INTEGER NOT NULL, " +
      "progress_locator TEXT, progress_fraction REAL NOT NULL DEFAULT 0, " +
      "finished INTEGER NOT NULL DEFAULT 0, content_hash TEXT NOT NULL UNIQUE, " +
      "import_state TEXT NOT NULL, error_message TEXT)");
  }

  private static void createUnits(SQLiteDatabase db)
  {
    db.execSQL("CREATE TABLE reader_content_units (" +
      "item_id TEXT NOT NULL REFERENCES reader_items(id) ON DELETE CASCADE, " +
      "ordinal INTEGER NOT NULL, kind TEXT NOT NULL, text TEXT NOT NULL, " +
      "language_tag TEXT, source_locator TEXT, " +
      "PRIMARY KEY(item_id, ordinal))");
  }

  private static void validateIncoming(Item item) throws LibraryException
  {
    if (item == null || empty(item.id) || empty(item.title) ||
        item.sourceType == null || item.importState == null ||
        empty(item.contentHash) || item.contentHash.length() != 64 ||
        !Float.isFinite(item.progressFraction) || item.progressFraction < 0f ||
        item.progressFraction > 1f || item.units == null)
      throw new LibraryException(CORRUPT_MESSAGE);
    int expected = 0;
    for (ContentUnit unit : item.units)
    {
      if (unit == null || unit.ordinal != expected++ || empty(unit.kind) ||
          empty(normalizeText(unit.text)))
        throw new LibraryException(CORRUPT_MESSAGE);
    }
  }

  private static boolean empty(String value)
  {
    return value == null || value.isEmpty();
  }

  private static void putItem(SQLiteDatabase db, Item item)
  {
    ContentValues v = new ContentValues();
    v.put("id", item.id); v.put("title", item.title);
    v.put("source_type", item.sourceType.name());
    v.put("source_uri", item.sourceUri); v.put("mime_type", item.mimeType);
    v.put("author", item.author); v.put("language_tag", item.languageTag);
    v.put("created_at", item.createdAt); v.put("updated_at", item.updatedAt);
    v.put("last_opened_at", item.lastOpenedAt);
    v.put("progress_locator", item.progressLocator);
    v.put("progress_fraction", item.progressFraction);
    v.put("finished", item.finished ? 1 : 0);
    v.put("content_hash", item.contentHash);
    v.put("import_state", item.importState.name());
    v.put("error_message", item.errorMessage);
    db.insertWithOnConflict("reader_items", null, v,
        SQLiteDatabase.CONFLICT_REPLACE);
  }

  private static void replaceUnits(SQLiteDatabase db, String id,
      List<ContentUnit> units)
  {
    db.delete("reader_content_units", "item_id = ?", new String[] { id });
    for (ContentUnit unit : units)
    {
      ContentValues v = new ContentValues();
      v.put("item_id", id); v.put("ordinal", unit.ordinal);
      v.put("kind", unit.kind); v.put("text", normalizeText(unit.text));
      v.put("language_tag", unit.languageTag);
      v.put("source_locator", unit.sourceLocator);
      db.insertOrThrow("reader_content_units", null, v);
    }
  }

  private static Item findByHash(SQLiteDatabase db, String hash)
      throws LibraryException
  {
    return readItem(db, "content_hash = ?", new String[] { hash });
  }

  private static Item readItem(SQLiteDatabase db, String selection,
      String[] args) throws LibraryException
  {
    try (Cursor cursor = db.query("reader_items", null, selection, args, null,
        null, null, "1"))
    {
      return cursor.moveToFirst() ? readItem(db, cursor) : null;
    }
  }

  private static Item readItem(SQLiteDatabase db, Cursor c)
      throws LibraryException
  {
    try
    {
      String id = string(c, "id");
      ArrayList<ContentUnit> units = new ArrayList<>();
      try (Cursor uc = db.query("reader_content_units", null, "item_id = ?",
          new String[] { id }, null, null, "ordinal ASC"))
      {
        int expected = 0;
        while (uc.moveToNext())
        {
          int ordinal = uc.getInt(uc.getColumnIndexOrThrow("ordinal"));
          if (ordinal != expected++)
            throw new LibraryException(CORRUPT_MESSAGE);
          units.add(new ContentUnit(ordinal, string(uc, "kind"),
                string(uc, "text"), nullable(uc, "language_tag"),
                nullable(uc, "source_locator")));
        }
      }
      Item item = new Item(id, string(c, "title"),
          SourceType.valueOf(string(c, "source_type")),
          nullable(c, "source_uri"), nullable(c, "mime_type"),
          nullable(c, "author"), nullable(c, "language_tag"),
          c.getLong(c.getColumnIndexOrThrow("created_at")),
          c.getLong(c.getColumnIndexOrThrow("updated_at")),
          c.getLong(c.getColumnIndexOrThrow("last_opened_at")),
          nullable(c, "progress_locator"),
          c.getFloat(c.getColumnIndexOrThrow("progress_fraction")),
          c.getInt(c.getColumnIndexOrThrow("finished")) != 0,
          string(c, "content_hash"),
          ImportState.valueOf(string(c, "import_state")),
          nullable(c, "error_message"), units);
      validateIncoming(item);
      return item;
    }
    catch (IllegalArgumentException | IllegalStateException error)
    {
      throw new LibraryException(CORRUPT_MESSAGE, error);
    }
  }

  private static String string(Cursor c, String column)
      throws LibraryException
  {
    String value = nullable(c, column);
    if (empty(value))
      throw new LibraryException(CORRUPT_MESSAGE);
    return value;
  }

  private static String nullable(Cursor c, String column)
  {
    int index = c.getColumnIndexOrThrow(column);
    return c.isNull(index) ? null : c.getString(index);
  }

  private static void deleteRows(SQLiteDatabase db, String id)
  {
    db.delete("reader_content_units", "item_id = ?", new String[] { id });
    db.delete("reader_items", "id = ?", new String[] { id });
  }

  private void deleteOwnedSource(String sourceUri) throws IOException
  {
    if (sourceUri == null || !sourceUri.startsWith("private:"))
      return;
    File source;
    try
    {
      source = privateSourceFile(sourceUri.substring("private:".length()));
    }
    catch (LibraryException error)
    {
      throw new IOException("Invalid private Reader source path.", error);
    }
    if (source.exists() && !source.delete())
      throw new IOException("Private Reader source could not be deleted.");
  }
}
