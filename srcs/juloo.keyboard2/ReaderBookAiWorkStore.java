package juloo.keyboard2;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Disposable, rebuildable Book AI job and evidence cache. */
final class ReaderBookAiWorkStore extends SQLiteOpenHelper
{
  static final String DATABASE_NAME = "reader_ai_book_work.db";
  private static final int DATABASE_VERSION = 1;
  private static final int MAX_TEXT_LENGTH = 2 * 1024 * 1024;
  private static final int MAX_BOOK_JOBS = 250;
  private static final int MAX_BOOK_EVIDENCE = 5000;

  ReaderBookAiWorkStore(Context context)
  {
    super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
  }

  @Override public void onCreate(SQLiteDatabase database)
  {
    database.execSQL("CREATE TABLE book_ai_jobs ("
      + "job_id TEXT PRIMARY KEY NOT NULL, reader_item_id TEXT,"
      + "book_fingerprint TEXT NOT NULL, feature_type TEXT NOT NULL,"
      + "prompt_hash TEXT NOT NULL, model_id TEXT NOT NULL,"
      + "pipeline_version TEXT NOT NULL, chunk_plan_hash TEXT NOT NULL,"
      + "status TEXT NOT NULL, completed_chunk_ids TEXT NOT NULL DEFAULT '',"
      + "created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
    database.execSQL("CREATE TABLE book_ai_evidence ("
      + "evidence_id TEXT PRIMARY KEY NOT NULL, evidence_identity TEXT NOT NULL,"
      + "book_fingerprint TEXT NOT NULL, model_id TEXT NOT NULL,"
      + "pipeline_version TEXT NOT NULL, chunk_plan_hash TEXT NOT NULL,"
      + "chapter_index INTEGER NOT NULL, paragraph_start INTEGER NOT NULL,"
      + "paragraph_end INTEGER NOT NULL, raw_word_start INTEGER NOT NULL,"
      + "raw_word_end INTEGER NOT NULL, neutral_evidence TEXT NOT NULL,"
      + "provenance TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL)");
    database.execSQL("CREATE INDEX book_ai_jobs_status ON book_ai_jobs "
      + "(status, updated_at)");
    database.execSQL("CREATE INDEX book_ai_evidence_identity ON "
      + "book_ai_evidence (evidence_identity, chapter_index)");
  }

  @Override public void onUpgrade(SQLiteDatabase database, int oldVersion,
      int newVersion)
  {
    throw new IllegalStateException("Unsupported Book AI work cache upgrade "
        + oldVersion + " to " + newVersion);
  }

  synchronized ReaderAiStore.BookJob loadBookJob(String jobId)
  {
    try (Cursor cursor = getReadableDatabase().query("book_ai_jobs", null,
          "job_id = ?", new String[]{jobId}, null, null, null, "1"))
    {
      return cursor.moveToFirst() ? readBookJob(cursor) : null;
    }
  }

  synchronized void saveBookJob(ReaderAiStore.BookJob job)
  {
    require(job != null, "book AI job");
    requireText(job.jobId, "job ID", 200);
    requireText(job.bookFingerprint, "book fingerprint", 200);
    requireText(job.featureType, "feature type", 100);
    requireText(job.promptHash, "prompt hash", 200);
    requireText(job.modelId, "model ID", 1000);
    requireText(job.pipelineVersion, "pipeline version", 100);
    requireText(job.chunkPlanHash, "chunk plan hash", 200);
    require(job.status != null, "job status");
    ContentValues values = new ContentValues();
    values.put("reader_item_id", emptyToNull(job.readerItemId));
    values.put("book_fingerprint", job.bookFingerprint);
    values.put("feature_type", job.featureType);
    values.put("prompt_hash", job.promptHash);
    values.put("model_id", job.modelId);
    values.put("pipeline_version", job.pipelineVersion);
    values.put("chunk_plan_hash", job.chunkPlanHash);
    values.put("status", job.status.name());
    values.put("completed_chunk_ids", joinIds(job.completedEvidenceIds));
    values.put("created_at", job.createdAt);
    values.put("updated_at", job.updatedAt);
    SQLiteDatabase database = getWritableDatabase();
    database.beginTransaction();
    try
    {
      int updated = database.update("book_ai_jobs", values, "job_id = ?",
          new String[]{job.jobId});
      if (updated == 0)
      {
        values.put("job_id", job.jobId);
        database.insertOrThrow("book_ai_jobs", null, values);
      }
      prune(database);
      database.setTransactionSuccessful();
    }
    finally
    {
      database.endTransaction();
    }
  }

  synchronized boolean saveBookEvidence(ReaderAiStore.BookEvidence evidence)
  {
    require(evidence != null, "book AI evidence");
    requireText(evidence.evidenceId, "evidence ID", 200);
    requireText(evidence.evidenceIdentity, "evidence identity", 200);
    requireText(evidence.bookFingerprint, "book fingerprint", 200);
    requireText(evidence.modelId, "model ID", 1000);
    requireText(evidence.pipelineVersion, "pipeline version", 100);
    requireText(evidence.chunkPlanHash, "chunk plan hash", 200);
    requireText(evidence.neutralEvidence, "neutral evidence", MAX_TEXT_LENGTH);
    ContentValues values = new ContentValues();
    values.put("evidence_id", evidence.evidenceId);
    values.put("evidence_identity", evidence.evidenceIdentity);
    values.put("book_fingerprint", evidence.bookFingerprint);
    values.put("model_id", evidence.modelId);
    values.put("pipeline_version", evidence.pipelineVersion);
    values.put("chunk_plan_hash", evidence.chunkPlanHash);
    values.put("chapter_index", evidence.chapterIndex);
    values.put("paragraph_start", evidence.paragraphStart);
    values.put("paragraph_end", evidence.paragraphEnd);
    values.put("raw_word_start", evidence.rawWordStart);
    values.put("raw_word_end", evidence.rawWordEnd);
    values.put("neutral_evidence", evidence.neutralEvidence);
    values.put("provenance", nullToEmpty(evidence.provenance));
    values.put("created_at", evidence.createdAt);
    SQLiteDatabase database = getWritableDatabase();
    long inserted = database.insertWithOnConflict("book_ai_evidence", null,
        values, SQLiteDatabase.CONFLICT_IGNORE);
    prune(database);
    return inserted != -1;
  }

  synchronized List<ReaderAiStore.BookEvidence> loadBookEvidence(
      String bookFingerprint, String modelId, String pipelineVersion,
      String chunkPlanHash)
  {
    List<ReaderAiStore.BookEvidence> result = new ArrayList<>();
    try (Cursor cursor = getReadableDatabase().query("book_ai_evidence", null,
          "book_fingerprint = ? AND model_id = ? AND pipeline_version = ? "
            + "AND chunk_plan_hash = ?",
          new String[]{bookFingerprint, modelId, pipelineVersion,
            chunkPlanHash}, null, null, "chapter_index ASC, raw_word_start ASC",
          Integer.toString(MAX_BOOK_EVIDENCE)))
    {
      while (cursor.moveToNext())
        result.add(readBookEvidence(cursor));
    }
    return result;
  }

  synchronized Set<String> completedBookEvidence(String bookFingerprint,
      String modelId, String pipelineVersion, String chunkPlanHash)
  {
    LinkedHashSet<String> result = new LinkedHashSet<>();
    try (Cursor cursor = getReadableDatabase().query("book_ai_evidence",
          new String[]{"evidence_identity"},
          "book_fingerprint = ? AND model_id = ? AND pipeline_version = ? "
            + "AND chunk_plan_hash = ?",
          new String[]{bookFingerprint, modelId, pipelineVersion,
            chunkPlanHash}, null, null, "chapter_index ASC",
          Integer.toString(MAX_BOOK_EVIDENCE)))
    {
      while (cursor.moveToNext())
        result.add(cursor.getString(0));
    }
    return result;
  }

  private static ReaderAiStore.BookJob readBookJob(Cursor cursor)
  {
    ReaderAiStore.BookJobStatus status;
    try
    {
      status = ReaderAiStore.BookJobStatus.valueOf(text(cursor, "status"));
    }
    catch (IllegalArgumentException invalid)
    {
      status = ReaderAiStore.BookJobStatus.FAILED;
    }
    return new ReaderAiStore.BookJob(text(cursor, "job_id"),
        nullableText(cursor, "reader_item_id"),
        text(cursor, "book_fingerprint"), text(cursor, "feature_type"),
        text(cursor, "prompt_hash"), text(cursor, "model_id"),
        text(cursor, "pipeline_version"), text(cursor, "chunk_plan_hash"),
        status, splitIds(text(cursor, "completed_chunk_ids")),
        number(cursor, "created_at"), number(cursor, "updated_at"));
  }

  private static ReaderAiStore.BookEvidence readBookEvidence(Cursor cursor)
  {
    return new ReaderAiStore.BookEvidence(text(cursor, "evidence_id"),
        text(cursor, "evidence_identity"),
        text(cursor, "book_fingerprint"), text(cursor, "model_id"),
        text(cursor, "pipeline_version"), text(cursor, "chunk_plan_hash"),
        (int)number(cursor, "chapter_index"),
        (int)number(cursor, "paragraph_start"),
        (int)number(cursor, "paragraph_end"),
        (int)number(cursor, "raw_word_start"),
        (int)number(cursor, "raw_word_end"),
        text(cursor, "neutral_evidence"), text(cursor, "provenance"),
        number(cursor, "created_at"));
  }

  private static void prune(SQLiteDatabase database)
  {
    database.execSQL("DELETE FROM book_ai_jobs WHERE job_id IN "
      + "(SELECT job_id FROM book_ai_jobs ORDER BY updated_at DESC "
      + "LIMIT -1 OFFSET " + MAX_BOOK_JOBS + ")");
    database.execSQL("DELETE FROM book_ai_evidence WHERE evidence_id IN "
      + "(SELECT evidence_id FROM book_ai_evidence ORDER BY created_at DESC "
      + "LIMIT -1 OFFSET " + MAX_BOOK_EVIDENCE + ")");
  }

  private static String joinIds(Set<String> ids)
  {
    if (ids == null || ids.isEmpty())
      return "";
    StringBuilder result = new StringBuilder();
    for (String id : ids)
    {
      if (id == null || id.isEmpty() || id.indexOf('\n') >= 0)
        throw new IllegalArgumentException("Invalid completed evidence ID");
      if (result.length() > 0)
        result.append('\n');
      result.append(id);
    }
    if (result.length() > 100_000)
      throw new IllegalArgumentException("Completed evidence list is too large");
    return result.toString();
  }

  private static Set<String> splitIds(String value)
  {
    LinkedHashSet<String> result = new LinkedHashSet<>();
    if (value == null || value.isEmpty())
      return result;
    for (String id : value.split("\\n"))
      if (!id.isEmpty())
        result.add(id);
    return result;
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
