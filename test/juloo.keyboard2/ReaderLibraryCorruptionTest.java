package juloo.keyboard2;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
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
public class ReaderLibraryCorruptionTest
{
  private static final String PRIVATE_MARKER = "private-record-secret";
  private Context _context;
  private ReaderLibrary _library;

  @Before
  public void setUp() throws Exception
  {
    _context = RuntimeEnvironment.getApplication();
    _context.deleteDatabase("reader_library.db");
    _library = new ReaderLibrary(_context);
    _library.importItem(item());
    _library.close();
    _library = null;
  }

  @After
  public void tearDown()
  {
    if (_library != null)
      _library.close();
    _context.deleteDatabase("reader_library.db");
  }

  @Test
  public void corrupt_record_fails_visibly_without_echoing_private_data()
      throws Exception
  {
    SQLiteDatabase raw = SQLiteDatabase.openDatabase(
        _context.getDatabasePath("reader_library.db").getPath(), null,
        SQLiteDatabase.OPEN_READWRITE);
    raw.execSQL("UPDATE reader_items SET source_type = ? WHERE id = ?",
        new Object[] { PRIVATE_MARKER, "corrupt-item" });
    raw.close();

    ReaderLibrary.LibraryException error = readFailure();

    assertEquals("A corrupt persisted record must produce a visible safe failure.",
        "Reader Library record is corrupt.", error.getMessage());
    assertFalse("A failure message must not expose persisted private content.",
        error.toString().contains(PRIVATE_MARKER));
  }

  @Test
  public void unsupported_database_version_fails_visibly_without_data_leakage()
      throws Exception
  {
    SQLiteDatabase raw = SQLiteDatabase.openDatabase(
        _context.getDatabasePath("reader_library.db").getPath(), null,
        SQLiteDatabase.OPEN_READWRITE);
    raw.setVersion(3);
    raw.close();

    ReaderLibrary.LibraryException error = readFailure();

    assertNotNull("An unsupported Library version must produce a visible failure.",
        error.getMessage());
    assertFalse("Unsupported-version errors must not expose stored private content.",
        error.toString().contains(PRIVATE_MARKER));
  }

  private ReaderLibrary.LibraryException readFailure()
  {
    _library = new ReaderLibrary(_context);
    try
    {
      _library.get("corrupt-item");
      fail("Persisted corrupt or unsupported records must not be returned.");
      return null;
    }
    catch (ReaderLibrary.LibraryException expected)
    {
      return expected;
    }
  }

  private ReaderLibrary.Item item() throws Exception
  {
    ReaderLibrary.ContentUnit unit = new ReaderLibrary.ContentUnit(
        0, "paragraph", PRIVATE_MARKER, "en", null);
    return new ReaderLibrary.Item("corrupt-item", "Private title",
        ReaderLibrary.SourceType.EPUB, null, "text/plain", null, "en",
        100L, 200L, 0L, null, 0f, false,
        ReaderLibrary.contentHash(Arrays.asList(unit)),
        ReaderLibrary.ImportState.READY, null, Arrays.asList(unit));
  }
}
