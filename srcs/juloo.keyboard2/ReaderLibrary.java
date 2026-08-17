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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** App-private persistence for normalized Reader documents and progress. */
public final class ReaderLibrary extends SQLiteOpenHelper
{
  private static final String DATABASE_NAME = "reader_library.db";
  private static final int DATABASE_VERSION = 5;
  private static final String CORRUPT_MESSAGE =
    "Reader Library record is corrupt.";
  private static final String UNSUPPORTED_MESSAGE =
    "This Reader Library record type is unsupported.";

  public enum SourceType
  {
    CLIPBOARD, SELECTED_TEXT, SHARED_TEXT, URL, EPUB, PDF, SCREEN_CAPTURE
  }

  public enum ImportState { IMPORTING, READY, FAILED, OCR_REQUIRED }
  public enum SourceState { AVAILABLE, MISSING, INVALID, CHANGED }
  public enum ReaderMode { CLASSIC, THREE_D }
  public enum BookSort { RECENT, TITLE, AUTHOR, PROGRESS }
  public enum ClassicTheme { DARK, SEPIA, LIGHT }
  public enum ClassicFontFamily { SERIF, SYSTEM }

  public static final class EpubSettings
  {
    public final String booksTreeUri;
    public final ReaderMode globalLastReaderMode;
    public final ClassicTheme classicTheme;
    public final float classicTextSize;
    public final ClassicFontFamily classicFontFamily;
    public final boolean classicImagesEnabled;

    public EpubSettings(String booksTreeUri, ReaderMode globalLastReaderMode,
        ClassicTheme classicTheme, float classicTextSize,
        ClassicFontFamily classicFontFamily, boolean classicImagesEnabled)
    {
      this.booksTreeUri = booksTreeUri;
      this.globalLastReaderMode = globalLastReaderMode;
      this.classicTheme = classicTheme;
      this.classicTextSize = classicTextSize;
      this.classicFontFamily = classicFontFamily;
      this.classicImagesEnabled = classicImagesEnabled;
    }
  }

  public static final class ContentUnit
  {
    public final int ordinal;
    public final String kind;
    public final String text;
    public final String languageTag;
    public final String sourceLocator;
    public final String assetUri;

    public ContentUnit(int ordinal, String kind, String text,
        String languageTag, String sourceLocator)
    {
      this(ordinal, kind, text, languageTag, sourceLocator, null);
    }

    public ContentUnit(int ordinal, String kind, String text,
        String languageTag, String sourceLocator, String assetUri)
    {
      this.ordinal = ordinal;
      this.kind = kind;
      this.text = text;
      this.languageTag = languageTag;
      this.sourceLocator = sourceLocator;
      this.assetUri = assetUri;
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
    public final String imageUri;
    public final String treeUri;
    public final SourceState sourceState;
    public final boolean favorite;
    public final ReaderMode lastReaderMode;
    public final long rawWordIndex;
    public final int progressChapter;
    public final int progressCharOffset;
    public final String progressAnchor;
    public final long fileSize;
    public final long fileLastModified;
    public final String publisher;
    public final String bookIdentifier;

    public Item(String id, String title, SourceType sourceType, String sourceUri,
        String mimeType, String author, String languageTag, long createdAt,
        long updatedAt, long lastOpenedAt, String progressLocator,
        float progressFraction, boolean finished, String contentHash,
        ImportState importState, String errorMessage, List<ContentUnit> units,
        String imageUri, String treeUri, SourceState sourceState,
        boolean favorite, ReaderMode lastReaderMode, long rawWordIndex,
        int progressChapter, int progressCharOffset, String progressAnchor,
        long fileSize, long fileLastModified, String publisher,
        String bookIdentifier)
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
      this.imageUri = imageUri;
      this.treeUri = treeUri;
      this.sourceState = sourceState;
      this.favorite = favorite;
      this.lastReaderMode = lastReaderMode;
      this.rawWordIndex = rawWordIndex;
      this.progressChapter = progressChapter;
      this.progressCharOffset = progressCharOffset;
      this.progressAnchor = progressAnchor;
      this.fileSize = fileSize;
      this.fileLastModified = fileLastModified;
      this.publisher = publisher;
      this.bookIdentifier = bookIdentifier;
    }
  }

  public static final class BookCollection
  {
    public final String id;
    public final String name;
    public final int sortOrder;

    BookCollection(String id, String name, int sortOrder)
    {
      this.id = id;
      this.name = name;
      this.sortOrder = sortOrder;
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
    createBookTables(db);
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
    if (oldVersion == 2)
    {
      db.execSQL("ALTER TABLE reader_items ADD COLUMN image_uri TEXT");
      oldVersion = 3;
    }
    if (oldVersion == 3)
    {
      db.execSQL(
          "ALTER TABLE reader_content_units ADD COLUMN asset_uri TEXT");
      oldVersion = 4;
    }
    if (oldVersion == 4)
    {
      db.execSQL("ALTER TABLE reader_items ADD COLUMN tree_uri TEXT");
      db.execSQL("ALTER TABLE reader_items ADD COLUMN source_state TEXT "
          + "NOT NULL DEFAULT 'AVAILABLE'");
      db.execSQL("ALTER TABLE reader_items ADD COLUMN favorite INTEGER "
          + "NOT NULL DEFAULT 0");
      db.execSQL("ALTER TABLE reader_items ADD COLUMN last_reader_mode TEXT "
          + "NOT NULL DEFAULT 'CLASSIC'");
      db.execSQL("ALTER TABLE reader_items ADD COLUMN raw_word_index INTEGER "
          + "NOT NULL DEFAULT 0");
      db.execSQL("ALTER TABLE reader_items ADD COLUMN progress_chapter INTEGER "
          + "NOT NULL DEFAULT 0");
      db.execSQL("ALTER TABLE reader_items ADD COLUMN progress_char_offset "
          + "INTEGER NOT NULL DEFAULT 0");
      db.execSQL("ALTER TABLE reader_items ADD COLUMN progress_anchor TEXT");
      db.execSQL("ALTER TABLE reader_items ADD COLUMN file_size INTEGER "
          + "NOT NULL DEFAULT 0");
      db.execSQL("ALTER TABLE reader_items ADD COLUMN file_last_modified "
          + "INTEGER NOT NULL DEFAULT 0");
      db.execSQL("ALTER TABLE reader_items ADD COLUMN publisher TEXT");
      db.execSQL("ALTER TABLE reader_items ADD COLUMN book_identifier TEXT");
      createBookTables(db);
      oldVersion = 5;
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
    Item stored;
    String obsoleteImageUri = null;
    ArrayList<String> obsoleteAssetUris = new ArrayList<>();
    db.beginTransaction();
    try
    {
      Item duplicate = findByHash(db, incoming.contentHash);
      String imageUri = duplicate != null && incoming.imageUri == null
        ? duplicate.imageUri : incoming.imageUri;
      stored = duplicate == null ? incoming :
        new Item(duplicate.id, incoming.title, incoming.sourceType,
            incoming.sourceUri, incoming.mimeType, incoming.author,
            incoming.languageTag, duplicate.createdAt, incoming.updatedAt,
            duplicate.lastOpenedAt, duplicate.progressLocator,
            duplicate.progressFraction, duplicate.finished,
            incoming.contentHash, incoming.importState, incoming.errorMessage,
            incoming.units, imageUri, incoming.treeUri, incoming.sourceState,
            duplicate.favorite, duplicate.lastReaderMode,
            duplicate.rawWordIndex, duplicate.progressChapter,
            duplicate.progressCharOffset, duplicate.progressAnchor,
            incoming.fileSize, incoming.fileLastModified, incoming.publisher,
            incoming.bookIdentifier);
      if (duplicate != null)
      {
        if (duplicate.imageUri != null && !duplicate.imageUri.equals(imageUri))
          obsoleteImageUri = duplicate.imageUri;
        for (ContentUnit unit : duplicate.units)
          if (unit.assetUri != null &&
              !containsAsset(stored.units, unit.assetUri))
            obsoleteAssetUris.add(unit.assetUri);
      }
      if (duplicate == null)
        deleteRows(db, incoming.id);
      putItem(db, stored);
      replaceUnits(db, stored.id, stored.units);
      db.setTransactionSuccessful();
    }
    catch (SQLiteException error)
    {
      throw new LibraryException(CORRUPT_MESSAGE, error);
    }
    finally
    {
      db.endTransaction();
    }
    if (obsoleteImageUri != null)
      deleteOwnedSourceQuietly(obsoleteImageUri);
    for (String assetUri : obsoleteAssetUris)
      deleteOwnedSourceQuietly(assetUri);
    return stored;
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

  public Item getByContentHash(String contentHash) throws LibraryException
  {
    if (empty(contentHash))
      return null;
    try
    {
      return findByHash(getReadableDatabase(), contentHash);
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
  public List<Item> listNonBooks() throws LibraryException
  {
    ArrayList<Item> result = new ArrayList<>();
    SQLiteDatabase db = getReadableDatabase();
    try (Cursor cursor = db.query("reader_items", null, "source_type != ?",
          new String[] { SourceType.EPUB.name() }, null, null,
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


  public List<Item> listBooks(String query, BookSort sort,
      boolean favoritesOnly, String collectionId) throws LibraryException
  {
    StringBuilder selection = new StringBuilder("source_type = ?");
    ArrayList<String> args = new ArrayList<>();
    args.add(SourceType.EPUB.name());
    String search = query == null ? "" : query.trim();
    if (!search.isEmpty())
    {
      selection.append(" AND (title LIKE ? ESCAPE '\\' COLLATE NOCASE OR ")
        .append("author LIKE ? ESCAPE '\\' COLLATE NOCASE)");
      String pattern = likePattern(search);
      args.add(pattern);
      args.add(pattern);
    }
    if (favoritesOnly)
      selection.append(" AND favorite = 1");
    if (!empty(collectionId))
    {
      selection.append(" AND EXISTS (SELECT 1 FROM reader_item_collections ")
        .append("WHERE item_id = reader_items.id AND collection_id = ?)");
      args.add(collectionId);
    }
    String order;
    switch (sort == null ? BookSort.RECENT : sort)
    {
      case TITLE:
        order = "title COLLATE NOCASE ASC, author COLLATE NOCASE ASC, id ASC";
        break;
      case AUTHOR:
        order = "CASE WHEN author IS NULL OR TRIM(author) = '' THEN 1 ELSE 0 " +
          "END, author COLLATE NOCASE ASC, title COLLATE NOCASE ASC, id ASC";
        break;
      case PROGRESS:
        order = "progress_fraction DESC, last_opened_at DESC, id ASC";
        break;
      case RECENT:
      default:
        order = "last_opened_at DESC, created_at DESC, id ASC";
        break;
    }
    ArrayList<Item> result = new ArrayList<>();
    SQLiteDatabase db = getReadableDatabase();
    try (Cursor cursor = db.query("reader_items", null,
          selection.toString(), args.toArray(new String[0]), null, null, order))
    {
      while (cursor.moveToNext())
        result.add(readItem(db, cursor, false));
      return result;
    }
    catch (SQLiteException error)
    {
      throw new LibraryException(CORRUPT_MESSAGE, error);
    }
  }

  public void setFavorite(String itemId, boolean favorite)
      throws LibraryException
  {
    ContentValues values = new ContentValues();
    values.put("favorite", favorite ? 1 : 0);
    try
    {
      if (getWritableDatabase().update("reader_items", values,
            "id = ? AND source_type = ?",
            new String[] { itemId, SourceType.EPUB.name() }) != 1)
        throw new LibraryException(CORRUPT_MESSAGE);
    }
    catch (SQLiteException error)
    {
      throw new LibraryException(CORRUPT_MESSAGE, error);
    }
  }

  public List<BookCollection> listCollections() throws LibraryException
  {
    ArrayList<BookCollection> result = new ArrayList<>();
    try (Cursor cursor = getReadableDatabase().query("reader_collections",
          new String[] { "id", "name", "sort_order" }, null, null, null, null,
          "sort_order ASC, normalized_name ASC"))
    {
      while (cursor.moveToNext())
        result.add(new BookCollection(cursor.getString(0), cursor.getString(1),
              cursor.getInt(2)));
      return result;
    }
    catch (SQLiteException error)
    {
      throw new LibraryException(CORRUPT_MESSAGE, error);
    }
  }

  public BookCollection createCollection(String name) throws LibraryException
  {
    String displayName = collectionName(name);
    long now = System.currentTimeMillis();
    String id = UUID.randomUUID().toString();
    ContentValues values = new ContentValues();
    values.put("id", id);
    values.put("name", displayName);
    values.put("normalized_name", displayName.toLowerCase(Locale.ROOT));
    values.put("sort_order", nextCollectionSortOrder());
    values.put("created_at", now);
    values.put("updated_at", now);
    try
    {
      getWritableDatabase().insertOrThrow("reader_collections", null, values);
      return new BookCollection(id, displayName,
          values.getAsInteger("sort_order"));
    }
    catch (SQLiteException error)
    {
      throw new LibraryException("A collection with this name already exists.",
          error);
    }
  }

  public void renameCollection(String id, String name) throws LibraryException
  {
    String displayName = collectionName(name);
    ContentValues values = new ContentValues();
    values.put("name", displayName);
    values.put("normalized_name", displayName.toLowerCase(Locale.ROOT));
    values.put("updated_at", System.currentTimeMillis());
    try
    {
      if (getWritableDatabase().update("reader_collections", values, "id = ?",
            new String[] { id }) != 1)
        throw new LibraryException(CORRUPT_MESSAGE);
    }
    catch (SQLiteException error)
    {
      throw new LibraryException("A collection with this name already exists.",
          error);
    }
  }

  public boolean deleteCollection(String id) throws LibraryException
  {
    try
    {
      return getWritableDatabase().delete("reader_collections", "id = ?",
          new String[] { id }) == 1;
    }
    catch (SQLiteException error)
    {
      throw new LibraryException(CORRUPT_MESSAGE, error);
    }
  }

  public Set<String> collectionIdsForItem(String itemId)
      throws LibraryException
  {
    LinkedHashSet<String> result = new LinkedHashSet<>();
    try (Cursor cursor = getReadableDatabase().query(
          "reader_item_collections", new String[] { "collection_id" },
          "item_id = ?", new String[] { itemId }, null, null,
          "collection_id ASC"))
    {
      while (cursor.moveToNext())
        result.add(cursor.getString(0));
      return result;
    }
    catch (SQLiteException error)
    {
      throw new LibraryException(CORRUPT_MESSAGE, error);
    }
  }

  public void setItemCollections(String itemId, Set<String> collectionIds)
      throws LibraryException
  {
    Item item = get(itemId);
    if (item == null || item.sourceType != SourceType.EPUB)
      throw new LibraryException(CORRUPT_MESSAGE);
    LinkedHashSet<String> unique = collectionIds == null
      ? new LinkedHashSet<>() : new LinkedHashSet<>(collectionIds);
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try
    {
      db.delete("reader_item_collections", "item_id = ?",
          new String[] { itemId });
      for (String collectionId : unique)
      {
        ContentValues values = new ContentValues();
        values.put("item_id", itemId);
        values.put("collection_id", collectionId);
        db.insertOrThrow("reader_item_collections", null, values);
      }
      db.setTransactionSuccessful();
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
  public String getBooksTreeUri() throws LibraryException
  {
    try (Cursor cursor = getReadableDatabase().query("reader_epub_settings",
          new String[] { "books_tree_uri" }, "id = 1", null, null, null, null))
    {
      return cursor.moveToFirst() && !cursor.isNull(0)
        ? cursor.getString(0) : null;
    }
    catch (SQLiteException error)
    {
      throw new LibraryException(CORRUPT_MESSAGE, error);
    }
  }

  public void setBooksTreeUri(String treeUri) throws LibraryException
  {
    ContentValues values = new ContentValues();
    values.put("books_tree_uri", treeUri);
    try
    {
      if (getWritableDatabase().update("reader_epub_settings", values,
            "id = 1", null) != 1)
        throw new LibraryException(CORRUPT_MESSAGE);
    }
    catch (SQLiteException error)
    {
      throw new LibraryException(CORRUPT_MESSAGE, error);
    }
  }

  public EpubSettings getEpubSettings() throws LibraryException
  {
    String[] columns = {
      "books_tree_uri", "global_last_reader_mode", "classic_theme",
      "classic_text_size", "classic_font_family", "classic_images_enabled"
    };
    try (Cursor cursor = getReadableDatabase().query("reader_epub_settings",
          columns, "id = 1", null, null, null, null))
    {
      if (!cursor.moveToFirst())
        throw new LibraryException(CORRUPT_MESSAGE);
      float textSize = cursor.getFloat(3);
      if (!Float.isFinite(textSize) || textSize < 14f || textSize > 32f)
        throw new LibraryException(CORRUPT_MESSAGE);
      return new EpubSettings(cursor.isNull(0) ? null : cursor.getString(0),
          ReaderMode.valueOf(cursor.getString(1)),
          ClassicTheme.valueOf(cursor.getString(2)), textSize,
          ClassicFontFamily.valueOf(cursor.getString(4)),
          cursor.getInt(5) != 0);
    }
    catch (IllegalArgumentException | SQLiteException error)
    {
      throw new LibraryException(CORRUPT_MESSAGE, error);
    }
  }

  public void updateEpubSettings(EpubSettings settings)
      throws LibraryException
  {
    if (settings == null || settings.globalLastReaderMode == null ||
        settings.classicTheme == null ||
        !Float.isFinite(settings.classicTextSize) ||
        settings.classicTextSize < 14f || settings.classicTextSize > 32f ||
        settings.classicFontFamily == null)
      throw new LibraryException(CORRUPT_MESSAGE);
    ContentValues values = new ContentValues();
    if (settings.booksTreeUri == null)
      values.putNull("books_tree_uri");
    else
      values.put("books_tree_uri", settings.booksTreeUri);
    values.put("global_last_reader_mode",
        settings.globalLastReaderMode.name());
    values.put("classic_theme", settings.classicTheme.name());
    values.put("classic_text_size", settings.classicTextSize);
    values.put("classic_font_family", settings.classicFontFamily.name());
    values.put("classic_images_enabled",
        settings.classicImagesEnabled ? 1 : 0);
    try
    {
      if (getWritableDatabase().update("reader_epub_settings", values,
            "id = 1", null) != 1)
        throw new LibraryException(CORRUPT_MESSAGE);
    }
    catch (SQLiteException error)
    {
      throw new LibraryException(CORRUPT_MESSAGE, error);
    }
  }

  public void updateGlobalLastReaderMode(ReaderMode mode)
      throws LibraryException
  {
    if (mode == null)
      throw new LibraryException(CORRUPT_MESSAGE);
    ContentValues values = new ContentValues();
    values.put("global_last_reader_mode", mode.name());
    try
    {
      if (getWritableDatabase().update("reader_epub_settings", values,
            "id = 1", null) != 1)
        throw new LibraryException(CORRUPT_MESSAGE);
    }
    catch (SQLiteException error)
    {
      throw new LibraryException(CORRUPT_MESSAGE, error);
    }
  }

  public void markSourceState(String id, SourceState sourceState)
      throws LibraryException
  {
    if (empty(id) || sourceState == null)
      throw new LibraryException(CORRUPT_MESSAGE);
    ContentValues values = new ContentValues();
    values.put("source_state", sourceState.name());
    if (getWritableDatabase().update("reader_items", values, "id = ?",
          new String[] { id }) != 1)
      throw new LibraryException(CORRUPT_MESSAGE);
  }

  public Item rebindBookSource(String id, String sourceUri, String treeUri,
      String contentHash, long fileSize, long fileLastModified)
      throws LibraryException
  {
    Item item = get(id);
    if (item == null || item.sourceType != SourceType.EPUB ||
        empty(sourceUri) || empty(treeUri) ||
        !item.contentHash.equals(contentHash) ||
        fileSize < 0L || fileLastModified < 0L)
      throw new LibraryException("The selected EPUB does not match this book.");
    ContentValues values = new ContentValues();
    values.put("source_uri", sourceUri);
    values.put("tree_uri", treeUri);
    values.put("source_state", SourceState.AVAILABLE.name());
    values.put("file_size", fileSize);
    values.put("file_last_modified", fileLastModified);
    values.put("updated_at", System.currentTimeMillis());
    if (getWritableDatabase().update("reader_items", values, "id = ?",
          new String[] { id }) != 1)
      throw new LibraryException(CORRUPT_MESSAGE);
    return get(id);
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

  public void updateBookProgress(String id, long rawWordIndex, int chapter,
      int charOffset, String anchor, float fraction, boolean finished,
      ReaderMode mode, long lastOpenedAt) throws LibraryException
  {
    if (empty(id) || rawWordIndex < 0L || chapter < 0 || charOffset < 0 ||
        !Float.isFinite(fraction) || fraction < 0f || fraction > 1f ||
        mode == null)
      throw new LibraryException(CORRUPT_MESSAGE);
    ContentValues values = new ContentValues();
    values.put("progress_locator", "book:" + chapter + ":" + charOffset);
    values.put("progress_fraction", fraction);
    values.put("finished", finished ? 1 : 0);
    values.put("last_opened_at", lastOpenedAt);
    values.put("raw_word_index", rawWordIndex);
    values.put("progress_chapter", chapter);
    values.put("progress_char_offset", charOffset);
    if (empty(anchor))
      values.putNull("progress_anchor");
    else
      values.put("progress_anchor", anchor);
    values.put("last_reader_mode", mode.name());
    try
    {
      if (getWritableDatabase().update("reader_items", values,
            "id = ? AND source_type = ?",
            new String[] { id, SourceType.EPUB.name() }) != 1)
        throw new LibraryException(CORRUPT_MESSAGE);
    }
    catch (SQLiteException error)
    {
      throw new LibraryException(CORRUPT_MESSAGE, error);
    }
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
      deleteOwnedSource(item.imageUri);
      for (ContentUnit unit : item.units)
        deleteOwnedSource(unit.assetUri);
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
      "import_state TEXT NOT NULL, error_message TEXT, image_uri TEXT, " +
      "tree_uri TEXT, source_state TEXT NOT NULL DEFAULT 'AVAILABLE', " +
      "favorite INTEGER NOT NULL DEFAULT 0, " +
      "last_reader_mode TEXT NOT NULL DEFAULT 'CLASSIC', " +
      "raw_word_index INTEGER NOT NULL DEFAULT 0, " +
      "progress_chapter INTEGER NOT NULL DEFAULT 0, " +
      "progress_char_offset INTEGER NOT NULL DEFAULT 0, progress_anchor TEXT, " +
      "file_size INTEGER NOT NULL DEFAULT 0, " +
      "file_last_modified INTEGER NOT NULL DEFAULT 0, " +
      "publisher TEXT, book_identifier TEXT)");
  }

  private static void createUnits(SQLiteDatabase db)
  {
    db.execSQL("CREATE TABLE reader_content_units (" +
      "item_id TEXT NOT NULL REFERENCES reader_items(id) ON DELETE CASCADE, " +
      "ordinal INTEGER NOT NULL, kind TEXT NOT NULL, text TEXT NOT NULL, " +
      "language_tag TEXT, source_locator TEXT, asset_uri TEXT, " +
      "PRIMARY KEY(item_id, ordinal))");
  }

  private static void createBookTables(SQLiteDatabase db)
  {
    db.execSQL("CREATE TABLE reader_collections (" +
      "id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, " +
      "normalized_name TEXT NOT NULL UNIQUE, sort_order INTEGER NOT NULL " +
      "DEFAULT 0, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
    db.execSQL("CREATE TABLE reader_item_collections (" +
      "item_id TEXT NOT NULL REFERENCES reader_items(id) ON DELETE CASCADE, " +
      "collection_id TEXT NOT NULL REFERENCES reader_collections(id) " +
      "ON DELETE CASCADE, PRIMARY KEY(item_id, collection_id))");
    db.execSQL("CREATE TABLE reader_epub_settings (" +
      "id INTEGER PRIMARY KEY CHECK(id = 1), books_tree_uri TEXT, " +
      "global_last_reader_mode TEXT NOT NULL DEFAULT 'CLASSIC', " +
      "classic_theme TEXT NOT NULL DEFAULT 'DARK', " +
      "classic_text_size REAL NOT NULL DEFAULT 18, " +
      "classic_font_family TEXT NOT NULL DEFAULT 'SERIF', " +
      "classic_images_enabled INTEGER NOT NULL DEFAULT 1)");
    db.execSQL("INSERT INTO reader_epub_settings (id) VALUES (1)");
    db.execSQL("CREATE INDEX reader_items_source_type ON reader_items " +
      "(source_type)");
    db.execSQL("CREATE INDEX reader_items_last_opened ON reader_items " +
      "(last_opened_at DESC)");
    db.execSQL("CREATE INDEX reader_collections_sort ON reader_collections " +
      "(sort_order, normalized_name)");
    db.execSQL("CREATE INDEX reader_item_collections_collection ON " +
      "reader_item_collections (collection_id, item_id)");
  }

  private static void validateIncoming(Item item) throws LibraryException
  {
    if (item == null || empty(item.id) || empty(item.title) ||
        item.sourceType == null || item.importState == null ||
        item.sourceState == null || item.lastReaderMode == null ||
        empty(item.contentHash) || item.contentHash.length() != 64 ||
        !Float.isFinite(item.progressFraction) || item.progressFraction < 0f ||
        item.progressFraction > 1f || item.rawWordIndex < 0L ||
        item.progressChapter < 0 || item.progressCharOffset < 0 ||
        item.fileSize < 0L || item.fileLastModified < 0L ||
        item.units == null)
      throw new LibraryException(CORRUPT_MESSAGE);
    int expected = 0;
    for (ContentUnit unit : item.units)
    {
      if (unit == null || unit.ordinal != expected++ || empty(unit.kind) ||
          empty(normalizeText(unit.text)) ||
          (unit.assetUri != null && !unit.assetUri.startsWith("private:")))
        throw new LibraryException(CORRUPT_MESSAGE);
    }
  }

  private int nextCollectionSortOrder() throws LibraryException
  {
    try (Cursor cursor = getReadableDatabase().rawQuery(
          "SELECT COALESCE(MAX(sort_order), -1) + 1 FROM reader_collections",
          null))
    {
      if (!cursor.moveToFirst())
        throw new LibraryException(CORRUPT_MESSAGE);
      return cursor.getInt(0);
    }
    catch (SQLiteException error)
    {
      throw new LibraryException(CORRUPT_MESSAGE, error);
    }
  }

  private static String collectionName(String value) throws LibraryException
  {
    if (value == null)
      throw new LibraryException("Collection name is required.");
    StringBuilder out = new StringBuilder();
    boolean pendingSpace = false;
    for (int i = 0; i < value.length(); i++)
    {
      char character = value.charAt(i);
      if (Character.isISOControl(character))
        throw new LibraryException("Collection name contains unsupported text.");
      if (Character.isWhitespace(character))
      {
        pendingSpace = out.length() > 0;
        continue;
      }
      if (pendingSpace)
        out.append(' ');
      out.append(character);
      pendingSpace = false;
      if (out.length() > 64)
        throw new LibraryException("Collection name is too long.");
    }
    if (out.length() == 0)
      throw new LibraryException("Collection name is required.");
    return out.toString();
  }

  private static String likePattern(String value)
  {
    return "%" + value.replace("\\", "\\\\")
      .replace("%", "\\%").replace("_", "\\_") + "%";
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
    v.put("image_uri", item.imageUri);
    v.put("tree_uri", item.treeUri);
    v.put("source_state", item.sourceState.name());
    v.put("favorite", item.favorite ? 1 : 0);
    v.put("last_reader_mode", item.lastReaderMode.name());
    v.put("raw_word_index", item.rawWordIndex);
    v.put("progress_chapter", item.progressChapter);
    v.put("progress_char_offset", item.progressCharOffset);
    v.put("progress_anchor", item.progressAnchor);
    v.put("file_size", item.fileSize);
    v.put("file_last_modified", item.fileLastModified);
    v.put("publisher", item.publisher);
    v.put("book_identifier", item.bookIdentifier);
    if (db.update("reader_items", v, "id = ?",
          new String[] { item.id }) == 0)
      db.insertOrThrow("reader_items", null, v);
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
      v.put("asset_uri", unit.assetUri);
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
    return readItem(db, c, true);
  }

  private static Item readItem(SQLiteDatabase db, Cursor c, boolean loadUnits)
      throws LibraryException
  {
    try
    {
      String id = string(c, "id");
      ArrayList<ContentUnit> units = new ArrayList<>();
      if (loadUnits)
      {
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
                  nullable(uc, "source_locator"),
                  nullable(uc, "asset_uri")));
          }
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
          nullable(c, "error_message"), units, nullable(c, "image_uri"),
          nullable(c, "tree_uri"),
          SourceState.valueOf(string(c, "source_state")),
          c.getInt(c.getColumnIndexOrThrow("favorite")) != 0,
          ReaderMode.valueOf(string(c, "last_reader_mode")),
          c.getLong(c.getColumnIndexOrThrow("raw_word_index")),
          c.getInt(c.getColumnIndexOrThrow("progress_chapter")),
          c.getInt(c.getColumnIndexOrThrow("progress_char_offset")),
          nullable(c, "progress_anchor"),
          c.getLong(c.getColumnIndexOrThrow("file_size")),
          c.getLong(c.getColumnIndexOrThrow("file_last_modified")),
          nullable(c, "publisher"), nullable(c, "book_identifier"));
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

  private static boolean containsAsset(List<ContentUnit> units,
      String assetUri)
  {
    for (ContentUnit unit : units)
      if (assetUri.equals(unit.assetUri))
        return true;
    return false;
  }

  private void deleteOwnedSourceQuietly(String sourceUri)
  {
    try
    {
      deleteOwnedSource(sourceUri);
    }
    catch (IOException ignored)
    {
      // A committed Library replacement remains valid if cleanup fails.
    }
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
