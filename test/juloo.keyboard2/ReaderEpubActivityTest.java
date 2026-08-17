package juloo.keyboard2;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ImageButton;
import android.widget.TextView;
import java.util.Arrays;
import java.util.Collections;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w360dp-h740dp-port")
public final class ReaderEpubActivityTest
{
  private Context _context;
  private ActivityController<ReaderEpubActivity> _controller;

  @Before
  public void setUp()
  {
    _context = RuntimeEnvironment.getApplication();
    _context.deleteDatabase("reader_library.db");
  }

  @After
  public void tearDown()
  {
    if (_controller != null)
      _controller.destroy();
    _context.deleteDatabase("reader_library.db");
  }

  @Test
  public void library_routes_epubs_to_private_classic_reader_only()
      throws Exception
  {
    insert(item("book", ReaderLibrary.SourceType.EPUB));
    insert(item("article", ReaderLibrary.SourceType.URL));

    ReaderActivity.startLibraryItem(_context, "book");
    Intent bookIntent = Shadows.shadowOf(RuntimeEnvironment.getApplication())
      .getNextStartedActivity();
    assertEquals(ReaderEpubActivity.class.getName(),
        bookIntent.getComponent().getClassName());

    ReaderActivity.startLibraryItem(_context, "article");
    Intent articleIntent = Shadows.shadowOf(RuntimeEnvironment.getApplication())
      .getNextStartedActivity();
    assertEquals(ReaderActivity.class.getName(),
        articleIntent.getComponent().getClassName());

    ActivityInfo info = _context.getPackageManager().getActivityInfo(
        new ComponentName(_context, ReaderEpubActivity.class), 0);
    assertFalse("The EPUB host must never be externally launchable.",
        info.exported);
  }

  @Test
  public void classic_webview_has_no_file_content_network_or_storage_access()
  {
    WebView webView = new WebView(_context);
    ReaderEpubActivity.configureWebView(webView);
    WebSettings settings = webView.getSettings();

    assertTrue("Only the authored progress and appearance controller needs JavaScript.",
        settings.getJavaScriptEnabled());
    assertFalse(settings.getDomStorageEnabled());
    assertFalse(settings.getDatabaseEnabled());
    assertFalse(settings.getAllowFileAccess());
    assertFalse(settings.getAllowContentAccess());
    assertTrue(settings.getBlockNetworkLoads());
    assertFalse(settings.getJavaScriptCanOpenWindowsAutomatically());
    assertFalse(settings.supportMultipleWindows());
    assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW,
        settings.getMixedContentMode());
    assertEquals(WebSettings.LOAD_NO_CACHE, settings.getCacheMode());
    webView.destroy();
  }

  @Test
  public void rich_spine_is_continuous_csp_locked_and_anchor_preserving()
  {
    ReaderEpubImporter.Book book = new ReaderEpubImporter.Book(
        "Proof", "Ada", "en", null, null, Arrays.asList(
          new ReaderEpubImporter.Chapter(0, "one.xhtml",
            "First chapter", "<h1>First</h1><p>Rich chapter</p>", 0, 2),
          new ReaderEpubImporter.Chapter(1, "two.xhtml",
            "Second chapter", "<h2>Second</h2><img src=\"data:image/png;base64,AA==\">", 2, 2)),
        null, null);

    String html = ReaderEpubActivity.buildDocumentHtml(book);

    assertTrue(html.contains("default-src 'none'"));
    assertTrue(html.contains("img-src data:"));
    assertFalse(html.contains("http://"));
    assertFalse(html.contains("https://"));
    assertTrue(html.indexOf("<h1>First</h1>") < html.indexOf("<h2>Second</h2>"));
    assertTrue(html.contains("data-raw-start=\"0\""));
    assertTrue(html.contains("data-raw-start=\"2\""));
    assertTrue("Control changes must retain the exact visible raw-word anchor.",
        html.contains("const raw=current()"));
    assertTrue(html.contains("document.caretRangeFromPoint"));
    assertTrue("Orientation and process restoration must prefer raw words.",
        html.contains("restore:(raw,fallback)=>restoreRaw(raw,fallback)"));
    assertTrue(html.contains(
          "ReaderProgress.onProgress(raw,total,percent())"));
    assertTrue(html.contains("ReaderProgress.open3d(raw,total,percent())"));
    assertTrue(html.contains("ReaderProgress.close(raw,total,percent())"));
    assertTrue(html.contains("body.images-off img{display:none}"));
  }

  @Test
  public void legacy_epub_locator_migrates_once_to_stable_raw_word_coordinates()
  {
    ReaderEpubImporter.Book book = progressBook();
    ReaderLibrary.Item legacy = progressItem("unit:1:6", 0.5f, 0L);

    int raw = ReaderEpubActivity.initialRawWordIndex(legacy, book, 0);
    assertEquals(4, raw);
    for (int reopen = 0; reopen < 20; reopen++)
    {
      ReaderEpubActivity.Position position =
        ReaderEpubActivity.positionForRawWord(book, raw);
      assertEquals(1, position.chapter);
      assertEquals(6, position.charOffset);
      raw = ReaderEpubActivity.initialRawWordIndex(
          progressItem("book:1:6", 4f / 7f, raw), book, raw);
      assertEquals("Reopen and orientation cycles must not accumulate drift.",
          4, raw);
    }
  }

  @Test
  public void classic_controls_match_private_drive_row_and_persist_preferences()
      throws Exception
  {
    insert(item("book", ReaderLibrary.SourceType.EPUB));
    Intent intent = ReaderEpubActivity.intent(_context, "book");
    _controller = Robolectric.buildActivity(ReaderEpubActivity.class, intent)
      .create().start().resume().visible();
    ReaderEpubActivity activity = _controller.get();

    int[] controlIds = { R.id.reader_epub_smaller, R.id.reader_epub_larger,
      R.id.reader_epub_font, R.id.reader_epub_images, R.id.reader_epub_theme,
      R.id.reader_epub_ai, R.id.reader_epub_open_3d };
    int minimumTarget = Math.round(48f * activity.getResources()
        .getDisplayMetrics().density);
    assertEquals(Math.round(52f * activity.getResources()
          .getDisplayMetrics().density), activity.findViewById(
            R.id.reader_epub_toolbar).getLayoutParams().height);
    for (int id : controlIds)
    {
      View control = activity.findViewById(id);
      assertTrue(control.getMinimumWidth() >= minimumTarget);
      assertTrue(control.getMinimumHeight() >= minimumTarget);
    }
    assertEquals("A-", ((TextView)activity.findViewById(
            R.id.reader_epub_smaller)).getText().toString());
    assertEquals("A+", ((TextView)activity.findViewById(
            R.id.reader_epub_larger)).getText().toString());
    assertEquals("Aa", ((TextView)activity.findViewById(
            R.id.reader_epub_font)).getText().toString());
    assertEquals("AI", ((TextView)activity.findViewById(
            R.id.reader_epub_ai)).getText().toString());
    assertEquals("3D", ((TextView)activity.findViewById(
            R.id.reader_epub_open_3d)).getText().toString());
    ImageButton images = activity.findViewById(R.id.reader_epub_images);
    ImageButton theme = activity.findViewById(R.id.reader_epub_theme);
    assertNotNull(images.getDrawable());
    assertNotNull(theme.getDrawable());
    int compactPadding = Math.round(15f * activity.getResources()
        .getDisplayMetrics().density);
    assertEquals(compactPadding, images.getPaddingLeft());
    assertEquals(compactPadding, theme.getPaddingLeft());

    View smaller = activity.findViewById(R.id.reader_epub_smaller);
    for (int i = 0; i < 20; i++)
      smaller.performClick();
    activity.findViewById(R.id.reader_epub_font).performClick();
    activity.findViewById(R.id.reader_epub_images).performClick();
    activity.findViewById(R.id.reader_epub_theme).performClick();

    try (ReaderLibrary library = new ReaderLibrary(_context))
    {
      ReaderLibrary.EpubSettings saved = library.getEpubSettings();
      assertEquals(14f, saved.classicTextSize, 0f);
      assertEquals(ReaderLibrary.ClassicFontFamily.SYSTEM,
          saved.classicFontFamily);
      assertFalse(saved.classicImagesEnabled);
      assertEquals(ReaderLibrary.ClassicTheme.SEPIA, saved.classicTheme);
    }

    Bundle state = new Bundle();
    _controller.saveInstanceState(state).pause().stop().destroy();
    _controller = Robolectric.buildActivity(ReaderEpubActivity.class, intent)
      .create(state).start().resume().visible();
    ReaderEpubActivity recreated = _controller.get();
    assertTrue(recreated.findViewById(R.id.reader_epub_font)
        .getContentDescription().toString().contains("System"));
    assertTrue(recreated.findViewById(R.id.reader_epub_images)
        .getContentDescription().toString().contains("on"));
    assertTrue(recreated.findViewById(R.id.reader_epub_theme)
        .getContentDescription().toString().contains("Sepia"));
    assertNotNull(recreated.findViewById(R.id.reader_epub_root));
  }

  @Test
  public void light_classic_theme_uses_dark_content_and_light_system_bars()
      throws Exception
  {
    insert(item("book-light", ReaderLibrary.SourceType.EPUB));
    _controller = Robolectric.buildActivity(ReaderEpubActivity.class,
        ReaderEpubActivity.intent(_context, "book-light"))
      .create().start().resume().visible();
    ReaderEpubActivity activity = _controller.get();

    activity.findViewById(R.id.reader_epub_theme).performClick();
    activity.findViewById(R.id.reader_epub_theme).performClick();

    TextView title = activity.findViewById(R.id.reader_epub_title);
    assertEquals(Color.parseColor("#111827"), title.getCurrentTextColor());
    TextView smaller = activity.findViewById(R.id.reader_epub_smaller);
    assertEquals(Color.parseColor("#111827"),
        smaller.getCurrentTextColor());
    int flags = activity.getWindow().getDecorView().getSystemUiVisibility();
    assertEquals(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR,
        flags & View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    assertEquals(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR,
        flags & View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
  }

  private static ReaderEpubImporter.Book progressBook()
  {
    return new ReaderEpubImporter.Book("Proof", "Ada", "en", null, null,
        Arrays.asList(
          new ReaderEpubImporter.Chapter(0, "one.xhtml",
            "zero one two", "<p>zero one two</p>", 0, 3),
          new ReaderEpubImporter.Chapter(1, "two.xhtml",
            "three four five six", "<p>three four five six</p>", 3, 4)),
        null, null);
  }

  private static ReaderLibrary.Item progressItem(String locator,
      float fraction, long rawWordIndex)
  {
    return new ReaderLibrary.Item("book-progress", "Book",
        ReaderLibrary.SourceType.EPUB,
        "content://books/document/book.epub", ReaderBooksFolder.EPUB_MIME,
        "Ada", "en", 1L, 2L, 3L, locator, fraction, false, "hash",
        ReaderLibrary.ImportState.READY, null, Collections.emptyList(), null,
        "content://books/tree/Books", ReaderLibrary.SourceState.AVAILABLE,
        false, ReaderLibrary.ReaderMode.CLASSIC, rawWordIndex, 1, 6,
        rawWordIndex > 0 ? "four five" : null, 100L, 200L, null, null);
  }

  private void insert(ReaderLibrary.Item item) throws Exception
  {
    try (ReaderLibrary library = new ReaderLibrary(_context))
    {
      library.importItem(item);
    }
  }

  private static ReaderLibrary.Item item(String id,
      ReaderLibrary.SourceType type) throws ReaderLibrary.LibraryException
  {
    ReaderLibrary.ContentUnit unit = new ReaderLibrary.ContentUnit(
        0, "paragraph", "Non-EPUB content " + id, "en", null);
    return new ReaderLibrary.Item(id, id, type,
        type == ReaderLibrary.SourceType.EPUB
          ? "content://books/document/" + id + ".epub"
          : "https://example.com/" + id,
        type == ReaderLibrary.SourceType.EPUB
          ? ReaderBooksFolder.EPUB_MIME : "text/plain",
        "Ada", "en", 1L, 2L, 0L, null, 0.4f, false,
        ReaderLibrary.contentHash(Arrays.asList(unit)),
        ReaderLibrary.ImportState.READY, null,
        type == ReaderLibrary.SourceType.EPUB
          ? Collections.emptyList() : Arrays.asList(unit),
        null, type == ReaderLibrary.SourceType.EPUB
          ? "content://books/tree/Books" : null,
        ReaderLibrary.SourceState.AVAILABLE, false,
        ReaderLibrary.ReaderMode.CLASSIC, 0L, 0, 0, null,
        100L, 200L, null, null);
  }
}
