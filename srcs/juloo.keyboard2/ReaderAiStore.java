package juloo.keyboard2;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** App-private durable Reader AI outputs. Disposable cache lives separately. */
final class ReaderAiStore extends SQLiteOpenHelper
{
  static final String DATABASE_NAME = "reader_ai_saved.db";
  private static final int DATABASE_VERSION = 3;
  private static final String TABLE = "saved_ai";
  private static final int MAX_TEXT_LENGTH = 2 * 1024 * 1024;
  private static final int MAX_ROWS = 2000;

  enum Type
  {
    SUMMARY_ONE("Summary One"),
    SUMMARY_TWO("Summary Two"),
    ARTICLE_CHAT("Article Chat"),
    ARTICLE_QUIZ("Article Quiz");

    final String label;
    Type(String label) { this.label = label; }
  }

  enum SourceType { ARTICLE, CLIPBOARD, BOOK }
  enum SourceFilter { ALL, ARTICLES, BOOKS }
  enum OutputFilter { ALL, SUMMARY, QUIZ, CHAT }

  static final class Entry
  {
    final long id;
    final String readerItemId;
    final String articleTitle;
    final Type type;
    final String contentMarkdown;
    final String chatMarkdown;
    final String sourceUrl;
    final String sourceHost;
    final String author;
    final boolean favorite;
    final String modelId;
    final String promptIdentity;
    final SourceType sourceType;
    final String bookFingerprint;
    final String provenance;
    final long createdAt;
    final long updatedAt;

    Entry(long id, String readerItemId, String articleTitle, Type type,
        String contentMarkdown, String chatMarkdown, String sourceUrl,
        String sourceHost, String author, boolean favorite, String modelId,
        String promptIdentity, SourceType sourceType, String bookFingerprint,
        String provenance, long createdAt, long updatedAt)
    {
      this.id = id;
      this.readerItemId = readerItemId;
      this.articleTitle = articleTitle;
      this.type = type;
      this.contentMarkdown = contentMarkdown;
      this.chatMarkdown = chatMarkdown;
      this.sourceUrl = sourceUrl;
      this.sourceHost = sourceHost;
      this.author = author;
      this.favorite = favorite;
      this.modelId = modelId;
      this.promptIdentity = promptIdentity;
      this.sourceType = sourceType;
      this.bookFingerprint = bookFingerprint;
      this.provenance = provenance;
      this.createdAt = createdAt;
      this.updatedAt = updatedAt;
    }

    boolean includesChat() { return !chatMarkdown.isEmpty(); }
  }

  enum BookJobStatus
  {
    PENDING, RUNNING, CANCELLED, FAILED, COMPLETE
  }

  static final class BookJob
  {
    final String jobId;
    final String readerItemId;
    final String bookFingerprint;
    final String featureType;
    final String promptHash;
    final String modelId;
    final String pipelineVersion;
    final String chunkPlanHash;
    final BookJobStatus status;
    final Set<String> completedEvidenceIds;
    final long createdAt;
    final long updatedAt;

    BookJob(String jobId, String readerItemId, String bookFingerprint,
        String featureType, String promptHash, String modelId,
        String pipelineVersion, String chunkPlanHash, BookJobStatus status,
        Set<String> completedEvidenceIds, long createdAt, long updatedAt)
    {
      this.jobId = jobId;
      this.readerItemId = readerItemId;
      this.bookFingerprint = bookFingerprint;
      this.featureType = featureType;
      this.promptHash = promptHash;
      this.modelId = modelId;
      this.pipelineVersion = pipelineVersion;
      this.chunkPlanHash = chunkPlanHash;
      this.status = status;
      this.completedEvidenceIds = Collections.unmodifiableSet(
          new LinkedHashSet<>(completedEvidenceIds));
      this.createdAt = createdAt;
      this.updatedAt = updatedAt;
    }

    BookJob withState(BookJobStatus nextStatus, Set<String> completed,
        long now)
    {
      return new BookJob(jobId, readerItemId, bookFingerprint, featureType,
          promptHash, modelId, pipelineVersion, chunkPlanHash, nextStatus,
          completed, createdAt, now);
    }
  }

  static final class BookEvidence
  {
    final String evidenceId;
    final String evidenceIdentity;
    final String bookFingerprint;
    final String modelId;
    final String pipelineVersion;
    final String chunkPlanHash;
    final int chapterIndex;
    final int paragraphStart;
    final int paragraphEnd;
    final int rawWordStart;
    final int rawWordEnd;
    final String neutralEvidence;
    final String provenance;
    final long createdAt;

    BookEvidence(String evidenceId, String evidenceIdentity,
        String bookFingerprint, String modelId, String pipelineVersion,
        String chunkPlanHash, int chapterIndex, int paragraphStart,
        int paragraphEnd, int rawWordStart, int rawWordEnd,
        String neutralEvidence, String provenance, long createdAt)
    {
      this.evidenceId = evidenceId;
      this.evidenceIdentity = evidenceIdentity;
      this.bookFingerprint = bookFingerprint;
      this.modelId = modelId;
      this.pipelineVersion = pipelineVersion;
      this.chunkPlanHash = chunkPlanHash;
      this.chapterIndex = chapterIndex;
      this.paragraphStart = paragraphStart;
      this.paragraphEnd = paragraphEnd;
      this.rawWordStart = rawWordStart;
      this.rawWordEnd = rawWordEnd;
      this.neutralEvidence = neutralEvidence;
      this.provenance = provenance;
      this.createdAt = createdAt;
    }
  }

  private final ReaderBookAiWorkStore workStore;

  ReaderAiStore(Context context)
  {
    super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    workStore = new ReaderBookAiWorkStore(context.getApplicationContext());
  }

  @Override public void onCreate(SQLiteDatabase database)
  {
    database.execSQL("CREATE TABLE " + TABLE + " ("
      + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
      + "reader_item_id TEXT, article_title TEXT NOT NULL,"
      + "content_type TEXT NOT NULL, content_markdown TEXT NOT NULL,"
      + "chat_markdown TEXT NOT NULL DEFAULT '', source_url TEXT NOT NULL,"
      + "source_host TEXT NOT NULL, author TEXT NOT NULL DEFAULT '',"
      + "favorite INTEGER NOT NULL DEFAULT 0, model_id TEXT NOT NULL,"
      + "prompt_identity TEXT NOT NULL, source_type TEXT NOT NULL "
      + "DEFAULT 'ARTICLE', book_fingerprint TEXT, provenance TEXT NOT NULL "
      + "DEFAULT '', created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
    database.execSQL("CREATE INDEX saved_ai_created ON " + TABLE
        + " (created_at DESC)");
    database.execSQL("CREATE INDEX saved_ai_favorite ON " + TABLE
        + " (favorite, created_at DESC)");
    database.execSQL("CREATE INDEX saved_ai_source_type ON " + TABLE
        + " (source_type, created_at DESC)");
  }

  @Override public void onUpgrade(SQLiteDatabase database, int oldVersion,
      int newVersion)
  {
    if (oldVersion == 1)
    {
      database.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN source_type TEXT "
          + "NOT NULL DEFAULT 'ARTICLE'");
      database.execSQL("ALTER TABLE " + TABLE
          + " ADD COLUMN book_fingerprint TEXT");
      database.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN provenance TEXT "
          + "NOT NULL DEFAULT ''");
      database.execSQL("CREATE INDEX saved_ai_source_type ON " + TABLE
          + " (source_type, created_at DESC)");
      oldVersion = 2;
    }
    if (oldVersion == 2)
    {
      database.execSQL("DROP TABLE IF EXISTS book_ai_jobs");
      database.execSQL("DROP TABLE IF EXISTS book_ai_evidence");
      oldVersion = 3;
    }
    if (oldVersion != newVersion)
      throw new IllegalStateException("Unsupported Reader AI database upgrade "
          + oldVersion + " to " + newVersion);
  }


  synchronized long save(String readerItemId, String articleTitle, Type type,
      String contentMarkdown, String chatMarkdown, String sourceUrl,
      String sourceHost, String author, String modelId, String promptIdentity,
      SourceType sourceType, String bookFingerprint, String provenance,
      boolean favorite)
  {
    require(type != null, "content type");
    require(sourceType != null, "source type");
    requireText(articleTitle, "article title", 1000);
    requireText(contentMarkdown, "AI content", MAX_TEXT_LENGTH);
    String normalizedSource = nullToEmpty(sourceUrl).trim();
    if (!normalizedSource.isEmpty() && !isHttpUrl(normalizedSource))
      throw new IllegalArgumentException("Saved AI source must be HTTP(S)");
    if (chatMarkdown != null && chatMarkdown.length() > MAX_TEXT_LENGTH)
      throw new IllegalArgumentException("Saved AI chat is too large");
    long now = System.currentTimeMillis();
    ContentValues values = new ContentValues();
    values.put("reader_item_id", emptyToNull(readerItemId));
    values.put("article_title", articleTitle.trim());
    values.put("content_type", type.name());
    values.put("content_markdown", contentMarkdown);
    values.put("chat_markdown", nullToEmpty(chatMarkdown));
    values.put("source_url", normalizedSource);
    values.put("source_host", nullToEmpty(sourceHost).trim());
    values.put("author", nullToEmpty(author).trim());
    values.put("favorite", favorite ? 1 : 0);
    values.put("model_id", nullToEmpty(modelId).trim());
    values.put("prompt_identity", nullToEmpty(promptIdentity));
    values.put("source_type", sourceType.name());
    values.put("book_fingerprint", emptyToNull(bookFingerprint));
    values.put("provenance", nullToEmpty(provenance));
    values.put("created_at", now);
    values.put("updated_at", now);
    SQLiteDatabase database = getWritableDatabase();
    database.beginTransaction();
    try
    {
      long id = database.insertOrThrow(TABLE, null, values);
      database.execSQL("DELETE FROM " + TABLE + " WHERE id IN (SELECT id FROM "
          + TABLE + " ORDER BY favorite DESC, created_at DESC LIMIT -1 OFFSET "
          + MAX_ROWS + ")");
      database.setTransactionSuccessful();
      return id;
    }
    finally
    {
      database.endTransaction();
    }
  }

  synchronized Entry load(long id)
  {
    try (Cursor cursor = getReadableDatabase().query(TABLE, null, "id = ?",
          new String[]{Long.toString(id)}, null, null, null, "1"))
    {
      return cursor.moveToFirst() ? read(cursor) : null;
    }
  }

  synchronized List<Entry> search(String query, boolean favoritesOnly,
      boolean oldestFirst, SourceFilter sourceFilter, OutputFilter outputFilter)
  {
    String normalized = query == null ? "" : query.trim();
    String selection = null;
    List<String> args = new ArrayList<>();
    if (favoritesOnly)
      selection = "favorite = 1";
    if (sourceFilter == SourceFilter.ARTICLES)
      selection = appendSelection(selection, "source_type != 'BOOK'");
    else if (sourceFilter == SourceFilter.BOOKS)
      selection = appendSelection(selection, "source_type = 'BOOK'");
    if (outputFilter == OutputFilter.SUMMARY)
      selection = appendSelection(selection,
          "content_type IN ('SUMMARY_ONE','SUMMARY_TWO')");
    else if (outputFilter == OutputFilter.QUIZ)
      selection = appendSelection(selection, "content_type = 'ARTICLE_QUIZ'");
    else if (outputFilter == OutputFilter.CHAT)
      selection = appendSelection(selection, "content_type = 'ARTICLE_CHAT'");
    if (!normalized.isEmpty())
    {
      String like = "%" + escapeLike(normalized) + "%";
      String search = "(article_title LIKE ? ESCAPE '\\' OR author LIKE ? ESCAPE '\\' OR content_markdown LIKE ? ESCAPE '\\' OR chat_markdown LIKE ? ESCAPE '\\' OR source_host LIKE ? ESCAPE '\\' OR provenance LIKE ? ESCAPE '\\')";
      selection = appendSelection(selection, search);
      for (int index = 0; index < 6; index++)
        args.add(like);
    }
    List<Entry> result = new ArrayList<>();
    try (Cursor cursor = getReadableDatabase().query(TABLE, null, selection,
          args.isEmpty() ? null : args.toArray(new String[0]), null, null,
          "created_at " + (oldestFirst ? "ASC" : "DESC"),
          Integer.toString(MAX_ROWS)))
    {
      while (cursor.moveToNext())
        result.add(read(cursor));
    }
    return result;
  }

  synchronized boolean setFavorite(long id, boolean favorite)
  {
    ContentValues values = new ContentValues();
    values.put("favorite", favorite ? 1 : 0);
    values.put("updated_at", System.currentTimeMillis());
    return getWritableDatabase().update(TABLE, values, "id = ?",
        new String[]{Long.toString(id)}) > 0;
  }

  synchronized boolean delete(long id)
  {
    return id > 0 && getWritableDatabase().delete(TABLE, "id = ?",
        new String[]{Long.toString(id)}) > 0;
  }

  synchronized BookJob loadBookJob(String jobId)
  {
    return workStore.loadBookJob(jobId);
  }

  synchronized void saveBookJob(BookJob job)
  {
    workStore.saveBookJob(job);
  }

  synchronized boolean saveBookEvidence(BookEvidence evidence)
  {
    return workStore.saveBookEvidence(evidence);
  }

  synchronized List<BookEvidence> loadBookEvidence(String bookFingerprint,
      String modelId, String pipelineVersion, String chunkPlanHash)
  {
    return workStore.loadBookEvidence(bookFingerprint, modelId,
        pipelineVersion, chunkPlanHash);
  }

  synchronized Set<String> completedBookEvidence(String bookFingerprint,
      String modelId, String pipelineVersion, String chunkPlanHash)
  {
    return workStore.completedBookEvidence(bookFingerprint, modelId,
        pipelineVersion, chunkPlanHash);
  }

  @Override public synchronized void close()
  {
    workStore.close();
    super.close();
  }

  private static Entry read(Cursor cursor)
  {
    Type type;
    try
    {
      type = Type.valueOf(text(cursor, "content_type"));
    }
    catch (IllegalArgumentException invalid)
    {
      type = Type.ARTICLE_CHAT;
    }
    SourceType sourceType;
    try
    {
      sourceType = SourceType.valueOf(text(cursor, "source_type"));
    }
    catch (IllegalArgumentException invalid)
    {
      sourceType = SourceType.ARTICLE;
    }
    return new Entry(number(cursor, "id"), nullableText(cursor,
          "reader_item_id"), text(cursor, "article_title"), type,
        text(cursor, "content_markdown"), text(cursor, "chat_markdown"),
        text(cursor, "source_url"), text(cursor, "source_host"),
        text(cursor, "author"), number(cursor, "favorite") != 0,
        text(cursor, "model_id"), text(cursor, "prompt_identity"), sourceType,
        nullableText(cursor, "book_fingerprint"), text(cursor, "provenance"),
        number(cursor, "created_at"), number(cursor, "updated_at"));
  }


  private static String text(Cursor cursor, String name)
  {
    String value = cursor.getString(cursor.getColumnIndexOrThrow(name));
    return value == null ? "" : value;
  }

  private static String nullableText(Cursor cursor, String name)
  {
    int index = cursor.getColumnIndexOrThrow(name);
    return cursor.isNull(index) ? null : cursor.getString(index);
  }

  private static long number(Cursor cursor, String name)
  {
    return cursor.getLong(cursor.getColumnIndexOrThrow(name));
  }

  private static String appendSelection(String selection, String clause)
  {
    return selection == null ? clause : selection + " AND " + clause;
  }

  private static String escapeLike(String value)
  {
    return value.replace("\\", "\\\\").replace("%", "\\%")
      .replace("_", "\\_");
  }

  private static boolean isHttpUrl(String value)
  {
    String lower = value.trim().toLowerCase(Locale.US);
    return lower.startsWith("https://") || lower.startsWith("http://");
  }

  private static void requireText(String value, String label, int max)
  {
    if (value == null || value.trim().isEmpty())
      throw new IllegalArgumentException(label + " is required");
    if (value.length() > max)
      throw new IllegalArgumentException(label + " is too large");
  }

  private static void require(boolean condition, String label)
  {
    if (!condition)
      throw new IllegalArgumentException(label + " is required");
  }

  private static String emptyToNull(String value)
  {
    return value == null || value.trim().isEmpty() ? null : value.trim();
  }

  private static String nullToEmpty(String value)
  {
    return value == null ? "" : value;
  }
}
