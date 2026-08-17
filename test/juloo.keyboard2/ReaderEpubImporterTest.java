package juloo.keyboard2;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ReaderEpubImporterTest
{
  private Context _context;

  @Before
  public void setUp()
  {
    _context = RuntimeEnvironment.getApplication();
    _context.deleteDatabase("reader_library.db");
  }

  @After
  public void tearDown()
  {
    _context.deleteDatabase("reader_library.db");
  }

  @Test
  public void epub3_metadata_cover_and_rich_spine_are_bounded_and_transient()
      throws Exception
  {
    byte[] image = image(600, 800);
    File epub = writeEpub(false, image, image);

    ReaderEpubImporter.Book book = ReaderEpubImporter.readFile(epub);
    assertEquals("Rich Proof", book.title);
    assertEquals("Ada Author", book.author);
    assertEquals("Proof Press", book.publisher);
    assertEquals("urn:isbn:123", book.identifier);
    assertEquals("en", book.language);
    assertArrayEquals(image, book.coverBytes);
    assertEquals("image/png", book.coverMimeType);
    assertEquals(2, book.chapters.size());
    assertEquals("OPS/text/one.xhtml", book.chapters.get(0).path);
    assertEquals("OPS/text/two.xhtml", book.chapters.get(1).path);
    assertEquals(0, book.chapters.get(0).rawWordStart);
    assertEquals(book.chapters.get(0).rawWordCount,
        book.chapters.get(1).rawWordStart);
    assertTrue(book.chapters.get(0).html.contains("<h1>First</h1>"));
    assertTrue(book.chapters.get(0).html.contains("<em>rich</em>"));
    assertTrue(book.chapters.get(0).html.contains("data:image/png;base64,"));
    assertFalse(book.chapters.get(0).html.contains("javascript:"));
    assertFalse(book.chapters.get(0).html.contains("tracker.example"));
    assertFalse(book.chapters.get(0).html.contains("onclick"));
    assertFalse(book.chapters.get(0).html.contains("<script"));
    assertFalse(book.chapters.get(0).html.contains("<iframe"));
    assertFalse(book.chapters.get(0).html.contains("<svg"));

    ReaderImportPipeline.Candidate candidate = ReaderEpubImporter.importFile(
        epub, "content://books/rich.epub", "Fallback");
    assertEquals("Proof Press", candidate.publisher);
    assertEquals("urn:isbn:123", candidate.bookIdentifier);
    assertEquals(2, candidate.units.size());

    try (ReaderLibrary library = new ReaderLibrary(_context))
    {
      library.updateGlobalLastReaderMode(ReaderLibrary.ReaderMode.THREE_D);
    }

    ReaderLibrary.Item stored = ReaderImportPipeline.importNow(_context,
        candidate.withBookSource("content://books/rich.epub",
          "content://books/tree", epub.length(), epub.lastModified()));
    assertEquals("Proof Press", stored.publisher);
    assertEquals("urn:isbn:123", stored.bookIdentifier);
    assertEquals("A new book must inherit the most recently used book mode.",
        ReaderLibrary.ReaderMode.THREE_D, stored.lastReaderMode);
    assertTrue("EPUB text and HTML must be parsed on demand, not duplicated in SQLite.",
        stored.units.isEmpty());
    assertNotNull(stored.imageUri);
    assertTrue(stored.imageUri.endsWith(".jpg"));

    try (ReaderLibrary library = new ReaderLibrary(_context))
    {
      ReaderLibrary.Item restored = library.get(stored.id);
      assertNotNull(restored);
      assertTrue(restored.units.isEmpty());
      File preview = library.privateSourceFile(
          restored.imageUri.substring("private:".length()));
      assertTrue(preview.isFile());
      BitmapFactory.Options bounds = new BitmapFactory.Options();
      bounds.inJustDecodeBounds = true;
      BitmapFactory.decodeFile(preview.getAbsolutePath(), bounds);
      assertTrue(bounds.outWidth <= 280);
      assertTrue(bounds.outHeight <= 420);
      assertTrue(library.delete(stored.id));
      assertFalse("Deleting a book must remove its derived cover cache.",
          preview.exists());
    }
  }

  @Test
  public void epub2_cover_metadata_is_supported_and_invalid_images_are_dropped()
      throws Exception
  {
    byte[] image = image(40, 60);
    ReaderEpubImporter.Book epub2 = ReaderEpubImporter.readFile(
        writeEpub(true, image, image));
    assertArrayEquals(image, epub2.coverBytes);
    assertEquals("image/png", epub2.coverMimeType);

    ReaderEpubImporter.Book invalid = ReaderEpubImporter.readFile(
        writeEpub(false, "not an image".getBytes(StandardCharsets.UTF_8),
          "also invalid".getBytes(StandardCharsets.UTF_8)));
    assertNull("Invalid cover bytes must never reach the thumbnail decoder.",
        invalid.coverBytes);
    assertFalse("Invalid packaged images must be removed from rich content.",
        invalid.chapters.get(0).html.contains("data:image/"));
    assertTrue(invalid.chapters.get(0).text.contains("First rich chapter"));
  }

  @Test
  public void packageXmlRejectsDoctypeBeforeEntityResolution() throws Exception
  {
    File epub = File.createTempFile("reader-unsafe-", ".epub",
        _context.getCacheDir());
    try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(epub)))
    {
      put(zip, "mimetype", "application/epub+zip");
      put(zip, "META-INF/container.xml",
          "<?xml version='1.0'?><!DOCTYPE container [" +
          "<!ENTITY leak SYSTEM 'file:///etc/passwd'>]>" +
          "<container xmlns='urn:oasis:names:tc:opendocument:xmlns:container'>" +
          "<rootfiles><rootfile full-path='OPS/package.opf'/>" +
          "</rootfiles></container>");
    }

    try
    {
      ReaderEpubImporter.readFile(epub);
      fail("Unsafe package XML must be rejected.");
    }
    catch (ReaderImportPipeline.ImportException error)
    {
      assertEquals("The EPUB contains unsafe XML declarations.",
          error.getMessage());
    }
  }

  private File writeEpub(boolean epub2, byte[] cover, byte[] inline)
      throws Exception
  {
    File file = File.createTempFile("reader-rich-", ".epub",
        _context.getCacheDir());
    try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file)))
    {
      put(zip, "mimetype", "application/epub+zip");
      put(zip, "META-INF/container.xml",
          "<?xml version='1.0'?><container xmlns='urn:oasis:names:tc:opendocument:xmlns:container'>" +
          "<rootfiles><rootfile full-path='OPS/package.opf'/></rootfiles></container>");
      String coverDeclaration = epub2
        ? "<meta name='cover' content='cover'/>"
        : "";
      String coverProperties = epub2 ? "" : " properties='cover-image'";
      put(zip, "OPS/package.opf",
          "<?xml version='1.0'?><package xmlns='http://www.idpf.org/2007/opf' " +
          "xmlns:dc='http://purl.org/dc/elements/1.1/' version='" +
          (epub2 ? "2.0" : "3.0") + "'><metadata>" + coverDeclaration +
          "<dc:title>Rich Proof</dc:title><dc:creator>Ada Author</dc:creator>" +
          "<dc:language>en</dc:language><dc:publisher>Proof Press</dc:publisher>" +
          "<dc:identifier>urn:isbn:123</dc:identifier></metadata><manifest>" +
          "<item id='one' href='text/one.xhtml' media-type='application/xhtml+xml'/>" +
          "<item id='two' href='text/two.xhtml' media-type='application/xhtml+xml'/>" +
          "<item id='cover' href='images/cover.png' media-type='image/png'" +
          coverProperties + "/>" +
          "<item id='figure' href='images/figure.png' media-type='image/png'/>" +
          "</manifest><spine><itemref idref='one'/><itemref idref='two'/></spine></package>");
      put(zip, "OPS/text/one.xhtml",
          "<html xmlns='http://www.w3.org/1999/xhtml'><body>" +
          "<h1 onclick='steal()'>First</h1><p>First <em>rich</em> chapter</p>" +
          "<img src='../images/figure.png' alt='Figure'/>" +
          "<img src='https://tracker.example/pixel.png'/>" +
          "<a href='javascript:steal()'>unsafe link</a>" +
          "<script>steal()</script><iframe src='https://tracker.example'></iframe>" +
          "<svg><script>steal()</script></svg></body></html>");
      put(zip, "OPS/text/two.xhtml",
          "<html xmlns='http://www.w3.org/1999/xhtml'><body>" +
          "<h2>Second</h2><p>Final chapter words</p></body></html>");
      put(zip, "OPS/images/cover.png", cover);
      put(zip, "OPS/images/figure.png", inline);
    }
    return file;
  }

  private static byte[] image(int width, int height) throws Exception
  {
    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    int[] pixels = new int[width * height];
    int value = 0x13579bdf;
    for (int i = 0; i < pixels.length; i++)
    {
      value ^= value << 13;
      value ^= value >>> 17;
      value ^= value << 5;
      pixels[i] = 0xff000000 | (value & 0x00ffffff);
    }
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
    bitmap.recycle();
    return output.toByteArray();
  }

  private static void put(ZipOutputStream zip, String path, String text)
      throws Exception
  {
    put(zip, path, text.getBytes(StandardCharsets.UTF_8));
  }

  private static void put(ZipOutputStream zip, String path, byte[] bytes)
      throws Exception
  {
    zip.putNextEntry(new ZipEntry(path));
    zip.write(bytes);
    zip.closeEntry();
  }
}
