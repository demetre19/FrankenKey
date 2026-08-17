package juloo.keyboard2;

import android.app.AlertDialog;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowContentResolver;
import org.robolectric.shadows.ShadowLooper;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
public class ReaderEpubShareTest
{
  private static final String AUTHORITY = "share-books.documents";
  private static final Uri TREE_URI = Uri.parse(
      "content://share-books.documents/tree/primary%3ABooks");
  private static final Uri TARGET_URI = Uri.parse(
      "content://share-books.documents/document/primary%3ABooks%2FShared.epub");
  private Context _context;

  @Before
  public void setUp()
  {
    _context = RuntimeEnvironment.getApplication();
    _context.deleteDatabase("reader_library.db");
    releasePersistedPermissions();
  }

  @After
  public void tearDown()
  {
    releasePersistedPermissions();
    _context.deleteDatabase("reader_library.db");
  }

  @Test
  public void manifest_exports_exact_epub_send_and_open_with_target()
  {
    Intent share = new Intent(Intent.ACTION_SEND)
      .setType(ReaderBooksFolder.EPUB_MIME)
      .putExtra(Intent.EXTRA_STREAM,
          Uri.parse("content://sender.example/book.epub"));
    Intent view = new Intent(Intent.ACTION_VIEW).setDataAndType(
        Uri.parse("content://sender.example/book.epub"),
        ReaderBooksFolder.EPUB_MIME);

    assertTrue("Android's EPUB share sheet must resolve directly to FrankenKey Reader.",
        resolvesToReaderShare(share));
    assertTrue("Android's EPUB Open with chooser must resolve to FrankenKey Reader.",
        resolvesToReaderShare(view));
    assertFalse("The Open with target must remain exact to EPUB MIME.",
        resolvesToReaderShare(new Intent(Intent.ACTION_VIEW).setDataAndType(
              Uri.parse("content://sender.example/book.pdf"),
              "application/pdf")));
  }

  @Test
  public void first_epub_open_with_requests_the_scoped_books_tree()
  {
    Uri opened = Uri.parse("content://sender.example/opened.epub");
    Intent view = new Intent(Intent.ACTION_VIEW)
      .setDataAndType(opened, ReaderBooksFolder.EPUB_MIME)
      .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    ActivityController<ReaderShareActivity> controller =
      Robolectric.buildActivity(ReaderShareActivity.class, view)
      .create().start().resume().visible();

    Intent picker = Shadows.shadowOf(controller.get()).getNextStartedActivity();

    assertNotNull("First Open with use must visibly request a Books folder.",
        picker);
    assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, picker.getAction());
    assertNull("EPUB Open with must not show the text/article confirmation dialog.",
        ShadowAlertDialog.getLatestAlertDialog());
    controller.pause().stop().destroy();
  }

  @Test
  public void first_epub_share_requests_the_scoped_books_tree()
  {
    Uri shared = Uri.parse("content://sender.example/first.epub");
    Intent share = new Intent(Intent.ACTION_SEND)
      .setType(ReaderBooksFolder.EPUB_MIME)
      .putExtra(Intent.EXTRA_STREAM, shared)
      .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    ActivityController<ReaderShareActivity> controller =
      Robolectric.buildActivity(ReaderShareActivity.class, share)
      .create().start().resume().visible();

    Intent picker = Shadows.shadowOf(controller.get()).getNextStartedActivity();

    assertNotNull("First use must visibly request a Books folder.", picker);
    assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, picker.getAction());
    assertNull("EPUB intake must not show the text/article confirmation dialog.",
        ShadowAlertDialog.getLatestAlertDialog());
    controller.pause().stop().destroy();
  }

  @Test
  public void valid_epub_share_copies_once_imports_directly_and_opens_book()
      throws Exception
  {
    ContentResolver resolver = _context.getContentResolver();
    ShadowContentResolver shadow = Shadows.shadowOf(resolver);
    Robolectric.setupContentProvider(ShareDocumentsProvider.class, AUTHORITY);
    Uri shared = Uri.parse("content://sender.example/Shared.epub");
    byte[] epub = epubBytes();
    ByteArrayOutputStream storedBytes = new ByteArrayOutputStream();
    shadow.registerInputStreamSupplier(shared,
        () -> new ByteArrayInputStream(epub));
    shadow.registerOutputStreamSupplier(TARGET_URI, () -> storedBytes);
    Intent grant = new Intent().setData(TREE_URI).addFlags(
        Intent.FLAG_GRANT_READ_URI_PERMISSION |
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
    ReaderBooksFolder.retain(_context, grant);

    Intent share = new Intent(Intent.ACTION_SEND)
      .setType(ReaderBooksFolder.EPUB_MIME)
      .putExtra(Intent.EXTRA_STREAM, shared)
      .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    ActivityController<ReaderShareActivity> controller =
      Robolectric.buildActivity(ReaderShareActivity.class, share)
      .create().start().resume().visible();
    ReaderLibrary.Item stored = awaitImportedBook();
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

    assertNotNull("A valid shared EPUB must be imported without a second confirmation step.",
        stored);
    assertEquals("Proof Book", stored.title);
    assertEquals(TARGET_URI.toString(), stored.sourceUri);
    assertEquals(TREE_URI.toString(), stored.treeUri);
    assertEquals(ReaderLibrary.SourceState.AVAILABLE, stored.sourceState);
    assertArrayEquals("The imported Library record must reference the exact canonical Books copy.",
        epub, storedBytes.toByteArray());
    assertNull("EPUB intake must bypass the text/article confirmation dialog.",
        ShadowAlertDialog.getLatestAlertDialog());
    Intent opened = Shadows.shadowOf(controller.get()).getNextStartedActivity();
    assertNotNull("Successful direct intake must open the imported Reader item.", opened);
    assertEquals(ReaderEpubActivity.class.getName(),
        opened.getComponent().getClassName());
    controller.pause().stop().destroy();
  }

  @Test
  public void duplicate_epub_share_reuses_readable_canonical_copy()
      throws Exception
  {
    ContentResolver resolver = _context.getContentResolver();
    ShadowContentResolver shadow = Shadows.shadowOf(resolver);
    ShareDocumentsProvider provider =
      Robolectric.setupContentProvider(ShareDocumentsProvider.class, AUTHORITY);
    byte[] epub = epubBytes();
    Uri shared = Uri.parse("content://sender.example/Duplicate.epub");
    shadow.registerInputStreamSupplier(shared,
        () -> new ByteArrayInputStream(epub));
    shadow.registerInputStreamSupplier(TARGET_URI,
        () -> new ByteArrayInputStream(epub));
    Intent grant = new Intent().setData(TREE_URI).addFlags(
        Intent.FLAG_GRANT_READ_URI_PERMISSION |
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
    ReaderBooksFolder.retain(_context, grant);
    File temporary = File.createTempFile("reader-duplicate-", ".epub",
        _context.getCacheDir());
    try (FileOutputStream output = new FileOutputStream(temporary))
    {
      output.write(epub);
    }
    ReaderImportPipeline.Candidate parsed = ReaderEpubImporter.importFile(
        temporary, TARGET_URI.toString(), "Duplicate.epub");
    ReaderLibrary.Item original = ReaderImportPipeline.importNow(_context,
        parsed.withBookSource(TARGET_URI.toString(), TREE_URI.toString(),
          epub.length, 1L));
    assertTrue(temporary.delete());

    Intent share = new Intent(Intent.ACTION_SEND)
      .setType(ReaderBooksFolder.EPUB_MIME)
      .putExtra(Intent.EXTRA_STREAM, shared)
      .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    ActivityController<ReaderShareActivity> controller =
      Robolectric.buildActivity(ReaderShareActivity.class, share)
      .create().start().resume().visible();
    Intent opened = awaitStartedActivity(controller.get());

    assertNotNull("A duplicate EPUB share must still open the existing book.",
        opened);
    assertEquals(0, provider.createCount);
    try (ReaderLibrary library = new ReaderLibrary(_context))
    {
      List<ReaderLibrary.Item> items = library.list();
      assertEquals("Content deduplication must not add a second Library item.",
          1, items.size());
      assertEquals("The existing stable Library id must be reused.",
          original.id, items.get(0).id);
      assertEquals("The existing readable canonical Books file must be reused.",
          TARGET_URI.toString(), items.get(0).sourceUri);
    }
    controller.pause().stop().destroy();
  }

  private boolean resolvesToReaderShare(Intent intent)
  {
    for (ResolveInfo match :
        _context.getPackageManager().queryIntentActivities(intent, 0))
      if (match.activityInfo != null && ReaderShareActivity.class.getName()
          .equals(match.activityInfo.name))
        return true;
    return false;
  }

  private ReaderLibrary.Item awaitImportedBook() throws Exception
  {
    for (int attempt = 0; attempt < 200; attempt++)
    {
      ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
      try (ReaderLibrary library = new ReaderLibrary(_context))
      {
        List<ReaderLibrary.Item> items = library.list();
        if (!items.isEmpty())
          return items.get(0);
      }
      Thread.sleep(10L);
    }
    return null;
  }

  private Intent awaitStartedActivity(ReaderShareActivity activity)
      throws Exception
  {
    for (int attempt = 0; attempt < 200; attempt++)
    {
      ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
      Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
      if (started != null)
        return started;
      Thread.sleep(10L);
    }
    return null;
  }

  private void releasePersistedPermissions()
  {
    ContentResolver resolver = _context.getContentResolver();
    for (UriPermission permission : new ArrayList<>(
          resolver.getPersistedUriPermissions()))
    {
      int flags = (permission.isReadPermission()
          ? Intent.FLAG_GRANT_READ_URI_PERMISSION : 0) |
        (permission.isWritePermission()
          ? Intent.FLAG_GRANT_WRITE_URI_PERMISSION : 0);
      if (flags != 0)
        resolver.releasePersistableUriPermission(permission.getUri(), flags);
    }
  }

  private static byte[] epubBytes() throws Exception
  {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes))
    {
      put(zip, "mimetype", ReaderBooksFolder.EPUB_MIME);
      put(zip, "META-INF/container.xml",
          "<?xml version=\"1.0\"?><container xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">" +
          "<rootfiles><rootfile full-path=\"book/package.opf\" media-type=\"application/oebps-package+xml\"/>" +
          "</rootfiles></container>");
      put(zip, "book/package.opf",
          "<?xml version=\"1.0\"?><package version=\"3.0\" xmlns=\"http://www.idpf.org/2007/opf\" " +
          "xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><metadata>" +
          "<dc:title>Proof Book</dc:title><dc:creator>Proof Author</dc:creator>" +
          "<dc:language>en</dc:language></metadata><manifest>" +
          "<item id=\"chapter\" href=\"chapter.xhtml\" media-type=\"application/xhtml+xml\"/>" +
          "</manifest><spine><itemref idref=\"chapter\"/></spine></package>");
      put(zip, "book/chapter.xhtml",
          "<html><body><h1>Chapter One</h1><p>Direct EPUB share proof.</p></body></html>");
    }
    return bytes.toByteArray();
  }

  private static void put(ZipOutputStream zip, String path, String value)
      throws Exception
  {
    zip.putNextEntry(new ZipEntry(path));
    zip.write(value.getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
  }

  public static final class ShareDocumentsProvider extends ContentProvider
  {
    int createCount;
    @Override public boolean onCreate() { return true; }

    @Override
    public Bundle call(String method, String arg, Bundle extras)
    {
      if ("android:createDocument".equals(method))
      {
        createCount++;
        Bundle result = new Bundle();
        result.putParcelable("uri", TARGET_URI);
        return result;
      }
      return super.call(method, arg, extras);
    }

    @Override public Cursor query(Uri uri, String[] projection,
        String selection, String[] selectionArgs, String sortOrder)
    {
      return null;
    }

    @Override public String getType(Uri uri)
    {
      return ReaderBooksFolder.EPUB_MIME;
    }

    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection,
        String[] selectionArgs) { return 1; }
    @Override public int update(Uri uri, ContentValues values,
        String selection, String[] selectionArgs) { return 0; }
  }
}
