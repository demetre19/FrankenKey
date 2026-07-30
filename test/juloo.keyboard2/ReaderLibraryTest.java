package juloo.keyboard2;

import android.content.ContentValues;
import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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
public class ReaderLibraryTest
{
  private Context _context;
  private ReaderLibrary _library;

  @Before
  public void setUp()
  {
    _context = RuntimeEnvironment.getApplication();
    _context.deleteDatabase("reader_library.db");
    _library = new ReaderLibrary(_context);
  }

  @After
  public void tearDown()
  {
    if (_library != null)
      _library.close();
    _context.deleteDatabase("reader_library.db");
  }

  @Test
  public void item_and_progress_survive_library_recreation() throws Exception
  {
    ReaderLibrary.Item original = item("persistent-item", null, "Persistent text");
    _library.importItem(original);
    _library.updateProgress(original.id, "unit:0:12", 0.625f, false, 900L);
    _library.close();

    _library = new ReaderLibrary(_context);
    ReaderLibrary.Item restored = _library.get(original.id);

    assertNotNull("Closing and reopening the Library must retain an imported item.",
        restored);
    assertEquals("Process recreation must preserve the stable Reader item id.",
        original.id, restored.id);
    assertEquals("Process recreation must preserve the saved progress locator.",
        "unit:0:12", restored.progressLocator);
    assertEquals("Process recreation must preserve the saved progress fraction.",
        0.625f, restored.progressFraction, 0f);
    assertEquals("Process recreation must preserve normalized content units.",
        "Persistent text", restored.units.get(0).text);
  }

  @Test
  public void delete_removes_owned_source_but_never_an_outside_file()
      throws Exception
  {
    File owned = _library.privateSourceFile("owned/document.txt");
    write(owned, "owned Reader content");
    ReaderLibrary.Item ownedItem = item("owned-item",
        "private:owned/document.txt", "Owned text");
    _library.importItem(ownedItem);

    File outside = new File(_context.getCacheDir(), "outside-reader-source.txt");
    write(outside, "outside content");
    ReaderLibrary.Item outsideItem = item("outside-item",
        outside.toURI().toString(), "Outside text");
    _library.importItem(outsideItem);

    assertTrue(_library.delete(ownedItem.id));
    assertFalse("Deleting a Library item must erase its app-owned source content.",
        owned.exists());
    assertTrue(_library.delete(outsideItem.id));
    assertTrue("Deleting metadata must never delete a source outside files/reader_library.",
        outside.exists());
    assertTrue(outside.delete());
  }

  @Test
  public void corrupt_record_fails_visibly_without_leaking_content()
      throws Exception
  {
    String secret = "private-reader-content-4821";
    ReaderLibrary.Item stored = item("corrupt-item", null, secret);
    _library.importItem(stored);

    ContentValues values = new ContentValues();
    values.put("text", "");
    _library.getWritableDatabase().update("reader_content_units", values,
        "item_id = ?", new String[] { stored.id });

    assertRejectedWithoutLeak(stored.id, secret,
        "A corrupt persisted record must fail visibly.");
  }

  @Test
  public void unsupported_record_type_fails_visibly_without_leaking_content()
      throws Exception
  {
    String secret = "unsupported-reader-content-5932";
    ReaderLibrary.Item stored = item("unsupported-item", null, secret);
    _library.importItem(stored);

    ContentValues values = new ContentValues();
    values.put("source_type", "FUTURE_PRIVATE_TYPE_" + secret);
    _library.getWritableDatabase().update("reader_items", values,
        "id = ?", new String[] { stored.id });

    assertRejectedWithoutLeak(stored.id, secret,
        "An unsupported persisted record type must fail visibly.");
  }

  private void assertRejectedWithoutLeak(String id, String secret,
      String message) throws Exception
  {
    try
    {
      _library.get(id);
      fail(message);
    }
    catch (ReaderLibrary.LibraryException error)
    {
      assertNotNull(message, error.getMessage());
      assertFalse("Reader failures must not expose persisted private content.",
          error.getMessage().contains(secret));
    }
  }

  private ReaderLibrary.Item item(String id, String sourceUri, String text)
      throws Exception
  {
    ReaderLibrary.ContentUnit unit = new ReaderLibrary.ContentUnit(
        0, "paragraph", text, "en", null);
    return new ReaderLibrary.Item(id, "Reader test", ReaderLibrary.SourceType.EPUB,
        sourceUri, "text/plain", null, "en", 100L, 200L, 0L, null, 0f,
        false, ReaderLibrary.contentHash(Arrays.asList(unit)),
        ReaderLibrary.ImportState.READY, null, Arrays.asList(unit));
  }

  private static void write(File file, String content) throws Exception
  {
    assertTrue(file.getParentFile().mkdirs() || file.getParentFile().isDirectory());
    try (FileOutputStream output = new FileOutputStream(file))
    {
      output.write(content.getBytes(StandardCharsets.UTF_8));
    }
  }
}
