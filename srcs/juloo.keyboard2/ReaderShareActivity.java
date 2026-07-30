package juloo.keyboard2;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Validates bounded external Reader inputs before confirmation and persistence. */
public final class ReaderShareActivity extends Activity
{
  private static final int MAX_TEXT_BYTES = 2 * 1024 * 1024;

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    Intent intent = getIntent();
    if (intent == null)
    {
      reject(getString(R.string.reader_error_unavailable_text));
      return;
    }

    String action = intent.getAction();
    if (Intent.ACTION_PROCESS_TEXT.equals(action))
    {
      acceptProcessText(intent);
      return;
    }
    if (Intent.ACTION_SEND.equals(action))
    {
      acceptShare(intent);
      return;
    }
    if (Intent.ACTION_OPEN_DOCUMENT.equals(action))
    {
      acceptDocument(intent);
      return;
    }
    reject(getString(R.string.reader_import_unsupported));
  }

  private void acceptProcessText(Intent intent)
  {
    if (!"text/plain".equals(intent.getType()))
    {
      reject(getString(R.string.reader_import_unsupported));
      return;
    }
    try
    {
      CharSequence value =
        intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
      ReaderImportPipeline.Candidate candidate =
        ReaderImportPipeline.Candidate.text(
            getString(R.string.reader_title_selection),
            ReaderLibrary.SourceType.SELECTED_TEXT, "process-text",
            boundedText(value));
      ReaderImportPipeline.confirmAndImport(this, candidate);
    }
    catch (RuntimeException | ReaderImportPipeline.ImportException error)
    {
      reject(ReaderImportPipeline.safeMessage(error));
    }
  }

  private void acceptShare(Intent intent)
  {
    if (!"text/plain".equals(intent.getType()))
    {
      reject(getString(R.string.reader_import_unsupported));
      return;
    }
    try
    {
      CharSequence value = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
      if (value != null && !value.toString().trim().isEmpty())
      {
        String text = boundedText(value);
        if (isHttpUrlOnly(text))
          runAsync(() -> ReaderArticleImporter.importUrl(text));
        else
          ReaderImportPipeline.confirmAndImport(this,
              ReaderImportPipeline.Candidate.text(
                preferredTitle(intent, R.string.reader_title_share),
                ReaderLibrary.SourceType.SHARED_TEXT, "android-share", text));
        return;
      }
      Uri stream = streamUri(intent);
      if (stream != null)
      {
        acceptGrantedDocument(intent, stream, "text/plain");
        return;
      }
      reject(getString(R.string.reader_error_unavailable_text));
    }
    catch (RuntimeException | ReaderImportPipeline.ImportException error)
    {
      reject(ReaderImportPipeline.safeMessage(error));
    }
  }

  private void acceptDocument(Intent intent)
  {
    Uri uri = intent.getData();
    if (!intent.hasCategory(Intent.CATEGORY_OPENABLE) || uri == null)
    {
      reject(getString(R.string.reader_import_unsupported));
      return;
    }
    acceptGrantedDocument(intent, uri, resolvedType(intent, uri));
  }

  private void acceptGrantedDocument(Intent intent, Uri uri, String mimeType)
  {
    if (!"content".equals(uri.getScheme()) ||
        (intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0)
    {
      reject(getString(R.string.reader_import_unsupported));
      return;
    }
    if (!"text/plain".equals(mimeType) &&
        !"application/pdf".equals(mimeType) &&
        !"application/epub+zip".equals(mimeType))
    {
      reject(getString(R.string.reader_import_unsupported));
      return;
    }
    runAsync(() -> importDocument(uri, mimeType));
  }

  private ReaderImportPipeline.Candidate importDocument(Uri uri,
      String mimeType) throws ReaderImportPipeline.ImportException
  {
    String title = displayName(uri);
    if ("text/plain".equals(mimeType))
    {
      try (InputStream input = getContentResolver().openInputStream(uri))
      {
        if (input == null)
          throw new IOException("missing content stream");
        byte[] bytes = readBounded(input, MAX_TEXT_BYTES);
        String text = decodeUtf8(bytes);
        return ReaderImportPipeline.Candidate.text(title,
            ReaderLibrary.SourceType.SHARED_TEXT, uri.toString(), text);
      }
      catch (IOException error)
      {
        throw new ReaderImportPipeline.ImportException(
            "This text document could not be read.", error);
      }
    }

    File temporary = null;
    try
    {
      temporary = File.createTempFile("reader-import-", ".tmp",
          getCacheDir());
      try (InputStream input = getContentResolver().openInputStream(uri);
           FileOutputStream output = new FileOutputStream(temporary))
      {
        if (input == null)
          throw new IOException("missing content stream");
        copyBounded(input, output, ReaderImportPipeline.MAX_DOCUMENT_BYTES);
      }
      if ("application/pdf".equals(mimeType))
        return ReaderPdfImporter.importFile(this, temporary, uri.toString(),
            title);
      return ReaderEpubImporter.importFile(temporary, uri.toString(), title);
    }
    catch (ReaderImportPipeline.ImportException error)
    {
      throw error;
    }
    catch (IOException error)
    {
      throw new ReaderImportPipeline.ImportException(
          "This document could not be read.", error);
    }
    finally
    {
      if (temporary != null)
        temporary.delete();
    }
  }

  private void runAsync(ImportOperation operation)
  {
    new Thread(() ->
    {
      try
      {
        ReaderImportPipeline.Candidate candidate = operation.run();
        runOnUiThread(() ->
            ReaderImportPipeline.confirmAndImport(this, candidate));
      }
      catch (ReaderImportPipeline.ImportException error)
      {
        runOnUiThread(() -> reject(ReaderImportPipeline.safeMessage(error)));
      }
      catch (RuntimeException error)
      {
        runOnUiThread(() -> reject(
              getString(R.string.reader_import_failed)));
      }
    }, "ReaderIntake").start();
  }

  private String displayName(Uri uri)
  {
    try (Cursor cursor = getContentResolver().query(uri,
          new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null))
    {
      if (cursor != null && cursor.moveToFirst())
      {
        String value = cursor.getString(0);
        if (value != null && !value.trim().isEmpty())
          return value.trim();
      }
    }
    catch (RuntimeException ignored) {}
    String segment = uri.getLastPathSegment();
    return segment == null || segment.trim().isEmpty()
      ? getString(R.string.reader_default_title) : segment;
  }

  private String preferredTitle(Intent intent, int fallback)
  {
    try
    {
      String subject = intent.getStringExtra(Intent.EXTRA_SUBJECT);
      return subject == null || subject.trim().isEmpty()
        ? getString(fallback) : subject.trim();
    }
    catch (RuntimeException malformed)
    {
      return getString(fallback);
    }
  }

  private String resolvedType(Intent intent, Uri uri)
  {
    String type = intent.getType();
    if (type == null || "*/*".equals(type))
      type = getContentResolver().getType(uri);
    return type;
  }

  private static Uri streamUri(Intent intent)
  {
    try
    {
      Object value = intent.getParcelableExtra(Intent.EXTRA_STREAM);
      return value instanceof Uri ? (Uri)value : null;
    }
    catch (RuntimeException malformed)
    {
      return null;
    }
  }

  private static String boundedText(CharSequence value)
      throws ReaderImportPipeline.ImportException
  {
    if (value == null)
      throw new ReaderImportPipeline.ImportException(
          "This app did not provide readable text.");
    String text = ReaderLibrary.normalizeText(value.toString());
    if (text.isEmpty())
      throw new ReaderImportPipeline.ImportException(
          "This app did not provide readable text.");
    if (text.length() > ReaderImportPipeline.MAX_INLINE_TEXT_CHARACTERS)
      throw new ReaderImportPipeline.ImportException(
          "This text is too large to import safely.");
    return text;
  }

  static boolean isHttpUrlOnly(String text)
  {
    String value = text.trim();
    if (value.indexOf(' ') >= 0 || value.indexOf('\n') >= 0 ||
        value.indexOf('\t') >= 0)
      return false;
    Uri uri = Uri.parse(value);
    String scheme = uri.getScheme();
    return uri.getHost() != null &&
      ("http".equalsIgnoreCase(scheme) ||
       "https".equalsIgnoreCase(scheme));
  }

  private static byte[] readBounded(InputStream input, int maximum)
      throws IOException, ReaderImportPipeline.ImportException
  {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    copyBounded(input, output, maximum);
    return output.toByteArray();
  }

  private static void copyBounded(InputStream input,
      java.io.OutputStream output, int maximum)
      throws IOException, ReaderImportPipeline.ImportException
  {
    byte[] buffer = new byte[8192];
    int total = 0;
    int read;
    while ((read = input.read(buffer)) != -1)
    {
      total += read;
      if (total > maximum)
        throw new ReaderImportPipeline.ImportException(
            "This document is too large to import safely.");
      output.write(buffer, 0, read);
    }
  }

  private static String decodeUtf8(byte[] bytes)
      throws ReaderImportPipeline.ImportException
  {
    try
    {
      String value = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString();
      if (!value.isEmpty() && value.charAt(0) == '\ufeff')
        value = value.substring(1);
      return boundedText(value);
    }
    catch (CharacterCodingException error)
    {
      throw new ReaderImportPipeline.ImportException(
          "This text document is not valid UTF-8.", error);
    }
  }

  private void reject(String message)
  {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    finish();
  }

  private interface ImportOperation
  {
    ReaderImportPipeline.Candidate run()
        throws ReaderImportPipeline.ImportException;
  }
}
