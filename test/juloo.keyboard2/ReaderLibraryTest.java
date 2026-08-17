package juloo.keyboard2;

import android.content.ContentValues;
import android.database.Cursor;
import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
  public void canonical_book_progress_and_mode_survive_process_recreation()
      throws Exception
  {
    ReaderLibrary.Item book = bookItem("book-progress",
        "content://books/document/book-progress.epub",
        "one two three four five", false,
        ReaderLibrary.ReaderMode.CLASSIC, 0L,
        "content://books/tree/Books");
    _library.importItem(book);
    _library.updateBookProgress(book.id, 4L, 1, 6, "four five",
        0.8f, false, ReaderLibrary.ReaderMode.THREE_D, 900L);
    _library.updateGlobalLastReaderMode(ReaderLibrary.ReaderMode.THREE_D);
    _library.close();

    _library = new ReaderLibrary(_context);
    ReaderLibrary.Item restored = _library.get(book.id);
    assertEquals(4L, restored.rawWordIndex);
    assertEquals("book:1:6", restored.progressLocator);
    assertEquals(1, restored.progressChapter);
    assertEquals(6, restored.progressCharOffset);
    assertEquals("four five", restored.progressAnchor);
    assertEquals(0.8f, restored.progressFraction, 0f);
    assertEquals(ReaderLibrary.ReaderMode.THREE_D,
        restored.lastReaderMode);
    assertEquals(ReaderLibrary.ReaderMode.THREE_D,
        _library.getEpubSettings().globalLastReaderMode);
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
  public void duplicate_import_replaces_preview_without_leaking_old_file()
      throws Exception
  {
    File oldPreview = _library.privateSourceFile("previews/old.img");
    File newPreview = _library.privateSourceFile("previews/new.img");
    write(oldPreview, "old preview");
    write(newPreview, "new preview");
    ReaderLibrary.Item original = item("original-item", null,
        "Duplicate content", "private:previews/old.img");
    _library.importItem(original);

    ReaderLibrary.Item replacement = item("replacement-item", null,
        "Duplicate content", "private:previews/new.img");
    ReaderLibrary.Item stored = _library.importItem(replacement);

    assertEquals("Duplicate content keeps its stable Library id.",
        original.id, stored.id);
    assertEquals("The refreshed article card uses the new preview.",
        "private:previews/new.img", stored.imageUri);
    assertFalse("Replacing a duplicate preview removes the obsolete private file.",
        oldPreview.exists());
    assertTrue("The replacement preview remains available to the Library card.",
        newPreview.exists());

    ReaderLibrary.Item withoutPreview = item("third-item", null,
        "Duplicate content", null);
    ReaderLibrary.Item preserved = _library.importItem(withoutPreview);
    assertEquals("A later metadata refresh without an image preserves the card preview.",
        stored.imageUri, preserved.imageUri);
    assertTrue("Preserving a preview must not delete its private file.",
        newPreview.exists());
  }

  @Test
  public void duplicate_epub_refresh_preserves_stable_reader_state_and_collection()
      throws Exception
  {
    ReaderLibrary.Item original = bookItem("stable-book",
        "content://books.documents/document/primary%3ABooks%2FOld.epub",
        "Same EPUB content", true, ReaderLibrary.ReaderMode.THREE_D, 7L,
        "content://books.documents/tree/primary%3ABooks");
    _library.importItem(original);
    _library.updateProgress(original.id, "unit:0:4", 0.75f, false, 900L);
    ContentValues collection = new ContentValues();
    collection.put("id", "collection-a");
    collection.put("name", "Reference");
    collection.put("normalized_name", "reference");
    collection.put("sort_order", 0);
    collection.put("created_at", 1L);
    collection.put("updated_at", 1L);
    _library.getWritableDatabase().insertOrThrow(
        "reader_collections", null, collection);
    ContentValues membership = new ContentValues();
    membership.put("item_id", original.id);
    membership.put("collection_id", "collection-a");
    _library.getWritableDatabase().insertOrThrow(
        "reader_item_collections", null, membership);

    ReaderLibrary.Item incoming = bookItem("incoming-id",
        "content://books.documents/document/primary%3ABooks%2FNew.epub",
        "Same EPUB content", false, ReaderLibrary.ReaderMode.CLASSIC, 0L,
        "content://books.documents/tree/primary%3ABooks");
    ReaderLibrary.Item stored = _library.importItem(incoming);

    assertEquals("Content deduplication must retain the original stable id.",
        original.id, stored.id);
    assertEquals("The usable canonical EPUB source may be refreshed.",
        incoming.sourceUri, stored.sourceUri);
    assertTrue("A duplicate import must preserve favorite state.",
        stored.favorite);
    assertEquals("A duplicate import must preserve the last Reader mode.",
        ReaderLibrary.ReaderMode.THREE_D, stored.lastReaderMode);
    assertEquals("A duplicate import must preserve exact raw-word progress.",
        7L, stored.rawWordIndex);
    assertEquals("A duplicate import must preserve Classic progress.",
        "unit:0:4", stored.progressLocator);
    try (Cursor cursor = _library.getReadableDatabase().rawQuery(
          "SELECT COUNT(*) FROM reader_item_collections WHERE item_id = ?",
          new String[] { original.id }))
    {
      assertTrue(cursor.moveToFirst());
      assertEquals("Refreshing duplicate metadata must not cascade-delete collection membership.",
          1, cursor.getInt(0));
    }
  }

  @Test
  public void locate_book_rebind_requires_same_content_and_preserves_state()
      throws Exception
  {
    ReaderLibrary.Item original = bookItem("missing-book",
        "content://books.documents/document/primary%3ABooks%2FMissing.epub",
        "Rebind content", true, ReaderLibrary.ReaderMode.THREE_D, 5L,
        "content://books.documents/tree/primary%3ABooks");
    _library.importItem(original);
    _library.updateProgress(original.id, "unit:0:3", 0.5f, false, 700L);
    _library.markSourceState(original.id, ReaderLibrary.SourceState.MISSING);

    try
    {
      _library.rebindBookSource(original.id,
          "content://books.documents/document/primary%3ABooks%2FWrong.epub",
          original.treeUri, repeat('0', 64), 100L, 200L);
      fail("Locate Book must reject a different EPUB without mutating the item.");
    }
    catch (ReaderLibrary.LibraryException expected) {}
    assertEquals(ReaderLibrary.SourceState.MISSING,
        _library.get(original.id).sourceState);

    ReaderLibrary.Item rebound = _library.rebindBookSource(original.id,
        "content://books.documents/document/primary%3ABooks%2FFound.epub",
        original.treeUri, original.contentHash, 123L, 456L);

    assertEquals(original.id, rebound.id);
    assertEquals(ReaderLibrary.SourceState.AVAILABLE, rebound.sourceState);
    assertEquals("unit:0:3", rebound.progressLocator);
    assertEquals(0.5f, rebound.progressFraction, 0f);
    assertTrue(rebound.favorite);
    assertEquals(ReaderLibrary.ReaderMode.THREE_D, rebound.lastReaderMode);
    assertEquals(5L, rebound.rawWordIndex);
    assertEquals(123L, rebound.fileSize);
    assertEquals(456L, rebound.fileLastModified);
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

  @Test
  public void books_support_metadata_search_sort_favorites_and_collections()
      throws Exception
  {
    ReaderLibrary.Item zulu = namedBook("zulu", "Zulu Manual", "Beta Writer",
        "zulu body", 10L, 0.25f, false);
    ReaderLibrary.Item alpha = namedBook("alpha", "Alpha Guide", "Ada Writer",
        "alpha body", 30L, 0.80f, true);
    ReaderLibrary.Item percent = namedBook("percent", "100% Real", null,
        "percent body", 20L, 0.50f, false);
    _library.importItem(zulu);
    _library.importItem(alpha);
    _library.importItem(percent);

    List<ReaderLibrary.Item> byTitle = _library.listBooks("guide",
        ReaderLibrary.BookSort.TITLE, false, null);
    assertEquals(1, byTitle.size());
    assertEquals("alpha", byTitle.get(0).id);
    assertTrue("Book grid queries stay metadata-only and never hydrate chapters.",
        byTitle.get(0).units.isEmpty());
    assertEquals("A percent sign in search is literal, not a wildcard.",
        "percent", _library.listBooks("%", ReaderLibrary.BookSort.TITLE,
          false, null).get(0).id);
    assertEquals("Title sort is deterministic and case-insensitive.",
        Arrays.asList("percent", "alpha", "zulu"),
        ids(_library.listBooks("", ReaderLibrary.BookSort.TITLE, false, null)));
    assertEquals("Books without an author sort after named authors.",
        Arrays.asList("alpha", "zulu", "percent"),
        ids(_library.listBooks("", ReaderLibrary.BookSort.AUTHOR, false, null)));
    assertEquals("Progress sort exposes the furthest-read book first.",
        Arrays.asList("alpha", "percent", "zulu"),
        ids(_library.listBooks("", ReaderLibrary.BookSort.PROGRESS, false, null)));
    assertEquals(Arrays.asList("alpha"),
        ids(_library.listBooks("", ReaderLibrary.BookSort.RECENT, true, null)));

    ReaderLibrary.BookCollection reference =
      _library.createCollection(" Reference ");
    ReaderLibrary.BookCollection work = _library.createCollection("Work");
    _library.setItemCollections(alpha.id,
        new LinkedHashSet<>(Arrays.asList(reference.id, work.id)));
    assertEquals(Arrays.asList("alpha"), ids(_library.listBooks("",
          ReaderLibrary.BookSort.RECENT, false, reference.id)));
    assertEquals(new LinkedHashSet<>(Arrays.asList(reference.id, work.id)),
        _library.collectionIdsForItem(alpha.id));

    _library.renameCollection(reference.id, "Research");
    assertEquals("Research", _library.listCollections().get(0).name);
    assertTrue(_library.deleteCollection(reference.id));
    assertFalse(_library.collectionIdsForItem(alpha.id).contains(reference.id));
    assertTrue("Deleting a collection must never delete its books.",
        _library.get(alpha.id) != null);
  }

  @Test
  public void five_hundred_books_remain_queryable_without_loading_chapters()
      throws Exception
  {
    for (int i = 0; i < 500; i++)
      _library.importItem(namedBook("book-" + i,
            String.format("Book %03d", i), "Author " + (i % 17),
            "unique body " + i, i, i / 500f, (i % 11) == 0));

    List<ReaderLibrary.Item> books = _library.listBooks("",
        ReaderLibrary.BookSort.TITLE, false, null);

    assertEquals("The personal Books library target is 500 books.",
        500, books.size());
    assertEquals("Book 000", books.get(0).title);
    assertEquals("Book 499", books.get(499).title);
    for (ReaderLibrary.Item book : books)
      assertTrue("Grid queries must not allocate retained chapter text.",
          book.units.isEmpty());
    assertEquals(46, _library.listBooks("", ReaderLibrary.BookSort.RECENT,
          true, null).size());
  }

  private static List<String> ids(List<ReaderLibrary.Item> items)
  {
    java.util.ArrayList<String> ids = new java.util.ArrayList<>();
    for (ReaderLibrary.Item item : items)
      ids.add(item.id);
    return ids;
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
        ReaderLibrary.ImportState.READY, null, Arrays.asList(unit), null,
        null, ReaderLibrary.SourceState.AVAILABLE, false,
        ReaderLibrary.ReaderMode.CLASSIC, 0L, 0, 0, null, 0L, 0L, null, null);
  }

  private ReaderLibrary.Item item(String id, String sourceUri, String text,
      String imageUri) throws Exception
  {
    ReaderLibrary.Item base = item(id, sourceUri, text);
    return new ReaderLibrary.Item(base.id, base.title, base.sourceType,
        base.sourceUri, base.mimeType, base.author, base.languageTag,
        base.createdAt, base.updatedAt, base.lastOpenedAt,
        base.progressLocator, base.progressFraction, base.finished,
        base.contentHash, base.importState, base.errorMessage, base.units,
        imageUri, base.treeUri, base.sourceState, base.favorite,
        base.lastReaderMode, base.rawWordIndex, base.progressChapter,
        base.progressCharOffset, base.progressAnchor, base.fileSize,
        base.fileLastModified, base.publisher, base.bookIdentifier);
  }

  private ReaderLibrary.Item bookItem(String id, String sourceUri, String text,
      boolean favorite, ReaderLibrary.ReaderMode readerMode, long rawWordIndex,
      String treeUri) throws Exception
  {
    ReaderLibrary.ContentUnit unit = new ReaderLibrary.ContentUnit(
        0, "chapter", text, "en", "chapter-1");
    return new ReaderLibrary.Item(id, "Reader book",
        ReaderLibrary.SourceType.EPUB, sourceUri,
        ReaderBooksFolder.EPUB_MIME, "Author", "en", 100L, 200L, 0L,
        null, 0f, false, ReaderLibrary.contentHash(Arrays.asList(unit)),
        ReaderLibrary.ImportState.READY, null, Arrays.asList(unit), null,
        treeUri, ReaderLibrary.SourceState.AVAILABLE, favorite, readerMode,
        rawWordIndex, 0, 0, "chapter-1", 10L, 20L, "Publisher", "book-id");
  }

  private ReaderLibrary.Item namedBook(String id, String title, String author,
      String text, long lastOpenedAt, float progress, boolean favorite)
      throws Exception
  {
    ReaderLibrary.ContentUnit unit = new ReaderLibrary.ContentUnit(
        0, "chapter", text, "en", "chapter.xhtml");
    return new ReaderLibrary.Item(id, title, ReaderLibrary.SourceType.EPUB,
        "content://books/document/" + id + ".epub", ReaderBooksFolder.EPUB_MIME,
        author, "en", lastOpenedAt, lastOpenedAt, lastOpenedAt, null, progress,
        false, ReaderLibrary.contentHash(Arrays.asList(unit)),
        ReaderLibrary.ImportState.READY, null, Arrays.asList(unit), null,
        "content://books/tree/Books", ReaderLibrary.SourceState.AVAILABLE,
        favorite, ReaderLibrary.ReaderMode.CLASSIC, 0L, 0, 0,
        "chapter.xhtml", 100L, lastOpenedAt, null, id);
  }

  private static String repeat(char value, int count)
  {
    char[] result = new char[count];
    Arrays.fill(result, value);
    return new String(result);
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
