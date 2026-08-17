package juloo.keyboard2;

import android.content.Context;
import android.content.res.Configuration;
import android.view.View;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ListView;
import java.util.Arrays;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w360dp-h740dp-port")
public final class ReaderLibraryActivityTest
{
  private Context _context;
  private ActivityController<ReaderLibraryActivity> _controller;

  @Before
  public void setUp() throws Exception
  {
    _context = RuntimeEnvironment.getApplication();
    _context.deleteDatabase("reader_library.db");
    try (ReaderLibrary library = new ReaderLibrary(_context))
    {
      ReaderLibrary.ContentUnit chapter = new ReaderLibrary.ContentUnit(
          0, "chapter", "A compact test book", "en", "chapter.xhtml");
      library.importItem(new ReaderLibrary.Item("book", "Compact Book",
            ReaderLibrary.SourceType.EPUB,
            "content://books/document/compact.epub",
            ReaderBooksFolder.EPUB_MIME, "Ada Author", "en", 1L, 2L, 0L,
            null, 0.35f, false,
            ReaderLibrary.contentHash(Arrays.asList(chapter)),
            ReaderLibrary.ImportState.READY, null, Arrays.asList(chapter), null,
            "content://books/tree/Books", ReaderLibrary.SourceState.AVAILABLE,
            false, ReaderLibrary.ReaderMode.CLASSIC, 0L, 0, 0, null,
            100L, 200L, null, null));
      ReaderLibrary.ContentUnit article = new ReaderLibrary.ContentUnit(
          0, "paragraph", "Saved article text", "en", null);
      library.importItem(new ReaderLibrary.Item("article", "Saved Article",
            ReaderLibrary.SourceType.URL, "https://example.com/article",
            "text/plain", "Editorial Team", "en", 3L, 4L, 0L, null, 0f,
            false, ReaderLibrary.contentHash(Arrays.asList(article)),
            ReaderLibrary.ImportState.READY, null, Arrays.asList(article), null,
            null, ReaderLibrary.SourceState.AVAILABLE, false,
            ReaderLibrary.ReaderMode.CLASSIC, 0L, 0, 0, null,
            0L, 0L, null, null));
    }
  }

  @After
  public void tearDown()
  {
    if (_controller != null)
      _controller.destroy();
    _context.deleteDatabase("reader_library.db");
  }

  @Test
  public void books_are_default_and_articles_remain_in_their_own_tab()
  {
    ReaderLibraryActivity activity = activity();
    GridView books = activity.findViewById(R.id.reader_library_books_grid);
    ListView items = activity.findViewById(R.id.reader_library_list);

    assertEquals("A phone library must never exceed two cover columns in portrait.",
        2, books.getNumColumns());
    assertEquals("Books are the primary Reader Library surface.",
        View.VISIBLE, books.getVisibility());
    assertEquals(1, books.getAdapter().getCount());
    assertEquals(View.GONE, items.getVisibility());

    Button itemTab = activity.findViewById(R.id.reader_library_items_tab);
    itemTab.performClick();

    assertEquals("Existing articles and text retain their list presentation.",
        View.VISIBLE, items.getVisibility());
    assertEquals(1, items.getAdapter().getCount());
    assertEquals(View.GONE, books.getVisibility());
    assertEquals(View.GONE, activity.findViewById(
          R.id.reader_library_book_controls).getVisibility());
    assertEquals(View.GONE, activity.findViewById(
          R.id.reader_library_collection_scroller).getVisibility());
  }

  @Test
  public void responsive_cover_columns_are_capped_at_three()
  {
    Configuration narrow = new Configuration();
    narrow.orientation = Configuration.ORIENTATION_PORTRAIT;
    narrow.screenWidthDp = 360;
    assertEquals(2, ReaderLibraryActivity.bookColumnCount(narrow));

    Configuration wide = new Configuration();
    wide.orientation = Configuration.ORIENTATION_PORTRAIT;
    wide.screenWidthDp = 700;
    assertEquals(3, ReaderLibraryActivity.bookColumnCount(wide));

    Configuration landscape = new Configuration();
    landscape.orientation = Configuration.ORIENTATION_LANDSCAPE;
    landscape.screenWidthDp = 500;
    assertEquals(3, ReaderLibraryActivity.bookColumnCount(landscape));
  }

  private ReaderLibraryActivity activity()
  {
    _controller = Robolectric.buildActivity(ReaderLibraryActivity.class)
      .create().start().resume().visible();
    return _controller.get();
  }
}