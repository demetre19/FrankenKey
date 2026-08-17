package juloo.keyboard2;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/** Owns the single persisted Storage Access Framework Books tree. */
final class ReaderBooksFolder
{
  static final String EPUB_MIME = "application/epub+zip";

  static final class Prepared
  {
    final Uri documentUri;
    final Uri treeUri;
    final long size;
    final long lastModified;
    final boolean createdCopy;

    Prepared(Uri documentUri, Uri treeUri, long size, long lastModified,
        boolean createdCopy)
    {
      this.documentUri = documentUri;
      this.treeUri = treeUri;
      this.size = size;
      this.lastModified = lastModified;
      this.createdCopy = createdCopy;
    }
  }

  private ReaderBooksFolder() {}

  static Intent pickerIntent()
  {
    return new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
      .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
          Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
          Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION |
          Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
  }

  static Uri retain(Context context, Intent result)
      throws ReaderImportPipeline.ImportException
  {
    if (context == null || result == null || result.getData() == null)
      throw failure("Choose a Books folder to continue.", null);
    Uri tree = result.getData();
    if (!"content".equals(tree.getScheme()))
      throw failure("The selected Books folder is unavailable.", null);
    int granted = result.getFlags() &
      (Intent.FLAG_GRANT_READ_URI_PERMISSION |
       Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
    int required = Intent.FLAG_GRANT_READ_URI_PERMISSION |
      Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
    if ((granted & required) != required)
      throw failure("FrankenKey needs read and write access to this Books folder.",
          null);
    try
    {
      context.getContentResolver().takePersistableUriPermission(tree, required);
      try (ReaderLibrary library = new ReaderLibrary(context))
      {
        library.setBooksTreeUri(tree.toString());
      }
      return tree;
    }
    catch (RuntimeException | ReaderLibrary.LibraryException error)
    {
      throw failure("The selected Books folder could not be remembered.", error);
    }
  }

  static Uri availableTree(Context context)
  {
    if (context == null)
      return null;
    try (ReaderLibrary library = new ReaderLibrary(context))
    {
      String value = library.getBooksTreeUri();
      if (value == null)
        return null;
      Uri tree = Uri.parse(value);
      return hasPersistedAccess(context.getContentResolver(), tree) ? tree : null;
    }
    catch (RuntimeException | ReaderLibrary.LibraryException error)
    {
      return null;
    }
  }

  static Prepared prepare(Context context, Uri source, String displayName)
      throws ReaderImportPipeline.ImportException
  {
    Uri tree = availableTree(context);
    if (tree == null)
      throw failure("Choose a Books folder before adding this EPUB.", null);
    if (source == null || !"content".equals(source.getScheme()))
      throw failure("This app did not share a readable EPUB.", null);
    if (isDescendant(tree, source))
    {
      long[] metadata = metadata(context.getContentResolver(), source);
      return new Prepared(source, tree, metadata[0], metadata[1], false);
    }

    ContentResolver resolver = context.getContentResolver();
    Uri parent;
    Uri created = null;
    try
    {
      parent = DocumentsContract.buildDocumentUriUsingTree(tree,
          DocumentsContract.getTreeDocumentId(tree));
      created = DocumentsContract.createDocument(resolver, parent, EPUB_MIME,
          safeName(displayName));
      if (created == null)
        throw new IOException("provider did not create document");
      try (InputStream input = resolver.openInputStream(source);
           OutputStream output = resolver.openOutputStream(created, "w"))
      {
        if (input == null || output == null)
          throw new IOException("provider did not open document stream");
        copyBounded(input, output, ReaderImportPipeline.MAX_DOCUMENT_BYTES);
      }
      long[] metadata = metadata(resolver, created);
      return new Prepared(created, tree, metadata[0], metadata[1], true);
    }
    catch (IOException | RuntimeException error)
    {
      deleteQuietly(resolver, created);
      throw failure("This EPUB could not be copied into the Books folder.", error);
    }
  }

  static boolean isReadable(Context context, String sourceUri)
  {
    if (context == null || sourceUri == null)
      return false;
    try (InputStream input = context.getContentResolver().openInputStream(
          Uri.parse(sourceUri)))
    {
      return input != null;
    }
    catch (IOException | RuntimeException error)
    {
      return false;
    }
  }

  static void deleteCreatedQuietly(Context context, Prepared prepared)
  {
    if (context != null && prepared != null && prepared.createdCopy)
      deleteQuietly(context.getContentResolver(), prepared.documentUri);
  }

  private static boolean hasPersistedAccess(ContentResolver resolver, Uri tree)
  {
    List<UriPermission> permissions = resolver.getPersistedUriPermissions();
    for (UriPermission permission : permissions)
      if (tree.equals(permission.getUri()) && permission.isReadPermission() &&
          permission.isWritePermission())
        return true;
    return false;
  }

  private static boolean isDescendant(Uri tree, Uri document)
  {
    try
    {
      if (!tree.getAuthority().equals(document.getAuthority()))
        return false;
      String root = DocumentsContract.getTreeDocumentId(tree);
      String child = DocumentsContract.getDocumentId(document);
      return child.equals(root) || child.startsWith(root + "/");
    }
    catch (IllegalArgumentException | NullPointerException error)
    {
      return false;
    }
  }

  private static long[] metadata(ContentResolver resolver, Uri uri)
  {
    long size = 0L;
    long modified = 0L;
    String[] projection = {
      OpenableColumns.SIZE,
      DocumentsContract.Document.COLUMN_LAST_MODIFIED
    };
    try (Cursor cursor = resolver.query(uri, projection, null, null, null))
    {
      if (cursor != null && cursor.moveToFirst())
      {
        int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
        int modifiedIndex = cursor.getColumnIndex(
            DocumentsContract.Document.COLUMN_LAST_MODIFIED);
        if (sizeIndex >= 0 && !cursor.isNull(sizeIndex))
          size = Math.max(0L, cursor.getLong(sizeIndex));
        if (modifiedIndex >= 0 && !cursor.isNull(modifiedIndex))
          modified = Math.max(0L, cursor.getLong(modifiedIndex));
      }
    }
    catch (RuntimeException ignored) {}
    return new long[] { size, modified };
  }

  private static String safeName(String displayName)
  {
    String value = displayName == null ? "Book.epub" : displayName.trim();
    value = value.replace('/', '_').replace('\\', '_')
      .replaceAll("[\\x00-\\x1f\\x7f]", "");
    if (value.isEmpty())
      value = "Book.epub";
    if (!value.toLowerCase(java.util.Locale.ROOT).endsWith(".epub"))
      value += ".epub";
    if (value.length() > 120)
      value = value.substring(0, 115) + ".epub";
    return value;
  }

  private static void copyBounded(InputStream input, OutputStream output,
      int maximum) throws IOException, ReaderImportPipeline.ImportException
  {
    byte[] buffer = new byte[8192];
    int total = 0;
    int read;
    while ((read = input.read(buffer)) != -1)
    {
      total += read;
      if (total > maximum)
        throw failure("This EPUB is too large to import safely.", null);
      output.write(buffer, 0, read);
    }
  }

  private static void deleteQuietly(ContentResolver resolver, Uri uri)
  {
    if (resolver == null || uri == null)
      return;
    try
    {
      DocumentsContract.deleteDocument(resolver, uri);
    }
    catch (IOException | RuntimeException ignored) {}
  }

  private static ReaderImportPipeline.ImportException failure(String message,
      Throwable cause)
  {
    return cause == null ? new ReaderImportPipeline.ImportException(message) :
      new ReaderImportPipeline.ImportException(message, cause);
  }
}
