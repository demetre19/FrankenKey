package juloo.keyboard2;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Validates a complete import candidate before the user may add it to Library. */
final class ReaderImportPipeline
{
  static final int MAX_INLINE_TEXT_CHARACTERS =
    ReaderPlaybackService.MAX_ACTIVE_TEXT_LENGTH;
  static final int MAX_IMPORTED_TEXT_CHARACTERS = 8 * 1024 * 1024;
  static final int MAX_DOCUMENT_BYTES = 32 * 1024 * 1024;

  static final class Candidate
  {
    final String title;
    final ReaderLibrary.SourceType sourceType;
    final String sourceUri;
    final String mimeType;
    final String author;
    final String languageTag;
    final List<ReaderLibrary.ContentUnit> units;

    Candidate(String title, ReaderLibrary.SourceType sourceType,
        String sourceUri, String mimeType, String author, String languageTag,
        List<ReaderLibrary.ContentUnit> units) throws ImportException
    {
      this.title = cleanTitle(title);
      this.sourceType = sourceType;
      this.sourceUri = emptyToNull(sourceUri);
      this.mimeType = emptyToNull(mimeType);
      this.author = emptyToNull(author);
      this.languageTag = emptyToNull(languageTag);
      this.units = Collections.unmodifiableList(
          new ArrayList<ReaderLibrary.ContentUnit>(units));
      validate();
    }

    static Candidate text(String title, ReaderLibrary.SourceType sourceType,
        String sourceUri, String text) throws ImportException
    {
      String normalized = ReaderLibrary.normalizeText(text);
      ArrayList<ReaderLibrary.ContentUnit> units = new ArrayList<>();
      units.add(new ReaderLibrary.ContentUnit(0, "text", normalized, null,
            sourceUri));
      return new Candidate(title, sourceType, sourceUri, "text/plain", null,
          null, units);
    }

    String readingText()
    {
      StringBuilder text = new StringBuilder();
      for (ReaderLibrary.ContentUnit unit : units)
      {
        if (text.length() > 0)
          text.append("\n\n");
        text.append(unit.text);
      }
      return text.toString();
    }

    private void validate() throws ImportException
    {
      if (sourceType == null || units.isEmpty())
        throw new ImportException("The imported item has no readable text.");
      int ordinal = 0;
      int total = 0;
      for (ReaderLibrary.ContentUnit unit : units)
      {
        if (unit == null || unit.ordinal != ordinal++ ||
            ReaderLibrary.normalizeText(unit.text).isEmpty())
          throw new ImportException("The imported item is malformed.");
        total += unit.text.length();
        if (total > MAX_IMPORTED_TEXT_CHARACTERS)
          throw new ImportException("The imported text is too large.");
      }
    }
  }

  static class ImportException extends Exception
  {
    ImportException(String message) { super(message); }
    ImportException(String message, Throwable cause) { super(message, cause); }
  }

  private ReaderImportPipeline() {}

  static void confirmAndImport(Activity activity, Candidate candidate)
  {
    if (activity == null || candidate == null || activity.isFinishing())
      return;
    new AlertDialog.Builder(activity)
      .setTitle(R.string.reader_import_confirm_title)
      .setMessage(activity.getString(R.string.reader_import_confirm_message,
            candidate.title))
      .setNegativeButton(R.string.reader_import_confirm_cancel,
          (_dialog, _which) -> activity.finish())
      .setOnCancelListener(_dialog -> activity.finish())
      .setPositiveButton(R.string.reader_import_confirm_add,
          (_dialog, _which) -> importInBackground(activity, candidate))
      .show();
  }

  static ReaderLibrary.Item importNow(Context context, Candidate candidate)
      throws ImportException
  {
    long now = System.currentTimeMillis();
    try (ReaderLibrary library = new ReaderLibrary(context))
    {
      String hash = ReaderLibrary.contentHash(candidate.units);
      ReaderLibrary.Item incoming = new ReaderLibrary.Item(
          UUID.randomUUID().toString(), candidate.title, candidate.sourceType,
          candidate.sourceUri, candidate.mimeType, candidate.author,
          candidate.languageTag, now, now, 0L, null, 0f, false, hash,
          ReaderLibrary.ImportState.READY, null, candidate.units);
      return library.importItem(incoming);
    }
    catch (ReaderLibrary.LibraryException error)
    {
      throw new ImportException("Reader Library could not save this item.",
          error);
    }
  }

  private static void importInBackground(Activity activity, Candidate candidate)
  {
    new Thread(() ->
    {
      try
      {
        ReaderLibrary.Item stored = importNow(activity, candidate);
        activity.runOnUiThread(() ->
        {
          String text = candidate.readingText();
          Toast.makeText(activity, R.string.reader_import_saved,
              Toast.LENGTH_SHORT).show();
          if (text.length() <= ReaderPlaybackService.MAX_ACTIVE_TEXT_LENGTH)
            ReaderActivity.startQuickRead(activity, stored.id, stored.title,
                text);
          activity.finish();
        });
      }
      catch (ImportException error)
      {
        activity.runOnUiThread(() ->
        {
          Toast.makeText(activity, safeMessage(error), Toast.LENGTH_LONG).show();
          activity.finish();
        });
      }
    }, "ReaderLibraryImport").start();
  }

  static String safeMessage(Exception error)
  {
    String message = error == null ? null : error.getMessage();
    return message == null || message.trim().isEmpty()
      ? "This item could not be imported." : message;
  }

  private static String cleanTitle(String title)
  {
    String value = title == null ? "" : title.trim();
    if (value.isEmpty())
      return "Imported item";
    return value.length() > 200 ? value.substring(0, 200) : value;
  }

  private static String emptyToNull(String value)
  {
    return value == null || value.trim().isEmpty() ? null : value.trim();
  }
}
