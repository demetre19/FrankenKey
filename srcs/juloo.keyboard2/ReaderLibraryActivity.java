package juloo.keyboard2;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Browses EPUB books separately from the existing article and text Library. */
public final class ReaderLibraryActivity extends Activity
    implements ReaderPlaybackService.Listener
{
  private static final int REQUEST_LOCATE_BOOK = 31;
  private static final int MENU_COLLECTIONS = 1;
  private static final int MENU_LOCATE = 2;
  private static final int MENU_DELETE = 3;

  private final ArrayList<ReaderLibrary.Item> _allItems = new ArrayList<>();
  private final ArrayList<ReaderLibrary.Item> _items = new ArrayList<>();
  private final ArrayList<ReaderLibrary.Item> _books = new ArrayList<>();
  private final ArrayList<ReaderLibrary.BookCollection> _collections =
    new ArrayList<>();
  private ReaderLibrary _library;
  private ReaderPlaybackService _service;
  private boolean _bound;
  private boolean _booksMode = true;
  private ReaderLibrary.BookSort _bookSort = ReaderLibrary.BookSort.RECENT;
  private boolean _favoritesOnly;
  private String _activeCollectionId;
  private String _pendingLocateId;
  private TextView _message;
  private EditText _search;
  private ListView _list;
  private GridView _grid;
  private Button _booksTab;
  private Button _itemsTab;
  private Button _sort;
  private Button _manageCollections;
  private LinearLayout _chips;
  private View _bookControls;
  private View _chipScroller;
  private final LibraryAdapter _adapter = new LibraryAdapter();
  private final BookAdapter _bookAdapter = new BookAdapter();
  private final ServiceConnection _connection = new ServiceConnection()
  {
    @Override public void onServiceConnected(ComponentName name, IBinder binder)
    {
      if (!(binder instanceof ReaderPlaybackService.LocalBinder))
        return;
      _service = ((ReaderPlaybackService.LocalBinder)binder).service();
      _bound = true;
      _service.addListener(ReaderLibraryActivity.this);
    }

    @Override public void onServiceDisconnected(ComponentName name)
    {
      _bound = false;
      _service = null;
    }
  };

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    setTheme(ReaderActivity.themeResource(this));
    super.onCreate(savedInstanceState);
    if (getActionBar() != null)
      getActionBar().hide();
    setContentView(R.layout.reader_library_activity);
    _message = (TextView)findViewById(R.id.reader_library_message);
    _list = (ListView)findViewById(R.id.reader_library_list);
    _grid = (GridView)findViewById(R.id.reader_library_books_grid);
    _search = (EditText)findViewById(R.id.reader_library_search);
    _booksTab = (Button)findViewById(R.id.reader_library_books_tab);
    _itemsTab = (Button)findViewById(R.id.reader_library_items_tab);
    _sort = (Button)findViewById(R.id.reader_library_sort);
    _manageCollections =
      (Button)findViewById(R.id.reader_library_manage_collections);
    _chips = (LinearLayout)findViewById(R.id.reader_library_collection_chips);
    _bookControls = findViewById(R.id.reader_library_book_controls);
    _chipScroller = findViewById(R.id.reader_library_collection_scroller);
    _grid.setNumColumns(bookColumnCount(getResources().getConfiguration()));
    _grid.setAdapter(_bookAdapter);
    _list.setAdapter(_adapter);
    _list.setEnabled(true);
    _search.addTextChangedListener(new TextWatcher()
    {
      @Override public void beforeTextChanged(CharSequence value, int start,
          int count, int after) {}
      @Override public void onTextChanged(CharSequence value, int start,
          int before, int count) { refreshActive(); }
      @Override public void afterTextChanged(Editable value) {}
    });
    findViewById(R.id.reader_library_back).setOnClickListener(
        _view -> finish());
    _booksTab.setOnClickListener(_view -> setBooksMode(true));
    _itemsTab.setOnClickListener(_view -> setBooksMode(false));
    _sort.setOnClickListener(_view -> cycleSort());
    _manageCollections.setOnClickListener(_view -> showManageCollections());
    _library = new ReaderLibrary(this);
    refresh();
    bindService(new Intent(this, ReaderPlaybackService.class), _connection,
        Context.BIND_AUTO_CREATE);
  }

  static int bookColumnCount(Configuration configuration)
  {
    if (configuration == null)
      return 2;
    return configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
      configuration.screenWidthDp >= 600 ? 3 : 2;
  }

  private void setBooksMode(boolean booksMode)
  {
    if (_booksMode == booksMode)
      return;
    _booksMode = booksMode;
    _search.setHint(booksMode ? R.string.reader_library_search_books_hint :
        R.string.reader_library_search_items_hint);
    updateTabs();
    refreshActive();
  }

  private void updateTabs()
  {
    tintTab(_booksTab, _booksMode);
    tintTab(_itemsTab, !_booksMode);
    _bookControls.setVisibility(_booksMode ? View.VISIBLE : View.GONE);
    _chipScroller.setVisibility(_booksMode ? View.VISIBLE : View.GONE);
    _chips.setVisibility(_booksMode ? View.VISIBLE : View.GONE);
  }

  private void tintTab(Button button, boolean selected)
  {
    button.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(
            selected ? "#8CE9D7" : "#20252B")));
    button.setTextColor(Color.parseColor(selected ? "#0B0D10" : "#C4CDD6"));
  }

  private void refresh()
  {
    try
    {
      _allItems.clear();
      _allItems.addAll(_library.listNonBooks());
      _collections.clear();
      _collections.addAll(_library.listCollections());
      rebuildCollectionChips();
      updateTabs();
      updateSortLabel();
      refreshActive();
    }
    catch (ReaderLibrary.LibraryException error)
    {
      showLibraryError();
    }
  }

  private void refreshActive()
  {
    if (_library == null || _search == null)
      return;
    if (_booksMode)
      refreshBooks();
    else
      filterItems(_search.getText().toString());
  }

  private void refreshBooks()
  {
    try
    {
      _books.clear();
      _books.addAll(_library.listBooks(_search.getText().toString(), _bookSort,
            _favoritesOnly, _activeCollectionId));
      _bookAdapter.notifyDataSetChanged();
      boolean empty = _library.listBooks("", ReaderLibrary.BookSort.RECENT,
          false, null).isEmpty();
      showResultState(empty, _books.isEmpty(), R.string.reader_library_books_empty,
          R.string.reader_library_books_no_matches, _grid);
    }
    catch (ReaderLibrary.LibraryException error)
    {
      showLibraryError();
    }
  }

  private void filterItems(String query)
  {
    String normalized = query == null ? "" :
      query.trim().toLowerCase(Locale.getDefault());
    _items.clear();
    for (ReaderLibrary.Item item : _allItems)
      if (normalized.isEmpty() || matches(item, normalized))
        _items.add(item);
    _adapter.notifyDataSetChanged();
    showResultState(_allItems.isEmpty(), _items.isEmpty(),
        R.string.reader_library_items_empty,
        R.string.reader_library_no_matches, _list);
  }

  private void showResultState(boolean empty, boolean noMatches,
      int emptyMessage, int noMatchesMessage, View content)
  {
    boolean showMessage = empty || noMatches;
    _message.setText(empty ? emptyMessage : noMatchesMessage);
    _message.setVisibility(showMessage ? View.VISIBLE : View.GONE);
    content.setVisibility(showMessage ? View.GONE : View.VISIBLE);
    View other = content == _grid ? _list : _grid;
    other.setVisibility(View.GONE);
  }

  private void showLibraryError()
  {
    _items.clear();
    _books.clear();
    _adapter.notifyDataSetChanged();
    _bookAdapter.notifyDataSetChanged();
    _list.setVisibility(View.GONE);
    _grid.setVisibility(View.GONE);
    _message.setVisibility(View.VISIBLE);
    _message.setText(R.string.reader_library_error);
  }

  private boolean matches(ReaderLibrary.Item item, String query)
  {
    if (contains(item.title, query) || contains(item.author, query))
      return true;
    for (ReaderLibrary.ContentUnit unit : item.units)
      if (contains(unit.text, query) || contains(unit.kind, query))
        return true;
    return false;
  }

  private boolean contains(String value, String query)
  {
    if (value == null || query == null || query.length() > value.length())
      return false;
    for (int i = 0; i <= value.length() - query.length(); i++)
      if (value.regionMatches(true, i, query, 0, query.length()))
        return true;
    return false;
  }

  private void cycleSort()
  {
    switch (_bookSort)
    {
      case RECENT: _bookSort = ReaderLibrary.BookSort.TITLE; break;
      case TITLE: _bookSort = ReaderLibrary.BookSort.AUTHOR; break;
      case AUTHOR: _bookSort = ReaderLibrary.BookSort.PROGRESS; break;
      case PROGRESS: default: _bookSort = ReaderLibrary.BookSort.RECENT; break;
    }
    updateSortLabel();
    refreshBooks();
  }

  private void updateSortLabel()
  {
    int label;
    switch (_bookSort)
    {
      case TITLE: label = R.string.reader_library_sort_title; break;
      case AUTHOR: label = R.string.reader_library_sort_author; break;
      case PROGRESS: label = R.string.reader_library_sort_progress; break;
      case RECENT: default: label = R.string.reader_library_sort_recent; break;
    }
    _sort.setText(getString(R.string.reader_library_sort, getString(label)));
  }

  private void rebuildCollectionChips()
  {
    _chips.removeAllViews();
    addChip(getString(R.string.reader_library_all_books),
        !_favoritesOnly && _activeCollectionId == null, () ->
        {
          _favoritesOnly = false;
          _activeCollectionId = null;
          rebuildCollectionChips();
          refreshBooks();
        });
    addChip(getString(R.string.reader_library_favorites), _favoritesOnly, () ->
        {
          _favoritesOnly = true;
          _activeCollectionId = null;
          rebuildCollectionChips();
          refreshBooks();
        });
    for (ReaderLibrary.BookCollection collection : _collections)
      addChip(collection.name,
          !_favoritesOnly && collection.id.equals(_activeCollectionId), () ->
          {
            _favoritesOnly = false;
            _activeCollectionId = collection.id;
            rebuildCollectionChips();
            refreshBooks();
          });
  }

  private void addChip(String label, boolean selected, Runnable action)
  {
    Button chip = new Button(this);
    chip.setAllCaps(false);
    chip.setMinHeight(dp(40));
    chip.setMinimumHeight(dp(40));
    chip.setMinWidth(0);
    chip.setMinimumWidth(0);
    chip.setPadding(dp(14), 0, dp(14), 0);
    chip.setText(label);
    chip.setTextSize(13f);
    chip.setTextColor(Color.parseColor(selected ? "#0B0D10" : "#C4CDD6"));
    chip.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(
            selected ? "#8CE9D7" : "#20252B")));
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
    params.setMarginEnd(dp(8));
    _chips.addView(chip, params);
    chip.setOnClickListener(_view -> action.run());
  }

  private void showManageCollections()
  {
    String[] names = new String[_collections.size() + 1];
    for (int i = 0; i < _collections.size(); i++)
      names[i] = _collections.get(i).name;
    names[names.length - 1] = getString(R.string.reader_library_new_collection);
    new AlertDialog.Builder(this)
      .setTitle(R.string.reader_library_manage_collections)
      .setItems(names, (_dialog, which) ->
      {
        if (which == _collections.size())
          showCollectionNameDialog(null);
        else
          showCollectionActions(_collections.get(which));
      })
      .setNegativeButton(android.R.string.cancel, null)
      .show();
  }

  private void showCollectionActions(ReaderLibrary.BookCollection collection)
  {
    new AlertDialog.Builder(this)
      .setTitle(collection.name)
      .setItems(new String[] {
          getString(R.string.reader_library_rename_collection),
          getString(R.string.reader_library_delete_collection)
        }, (_dialog, which) ->
        {
          if (which == 0)
            showCollectionNameDialog(collection);
          else
            confirmDeleteCollection(collection);
        })
      .show();
  }

  private void showCollectionNameDialog(ReaderLibrary.BookCollection collection)
  {
    EditText input = new EditText(this);
    input.setSingleLine(true);
    input.setInputType(InputType.TYPE_CLASS_TEXT |
        InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    input.setText(collection == null ? "" : collection.name);
    input.setSelectAllOnFocus(true);
    new AlertDialog.Builder(this)
      .setTitle(collection == null ? R.string.reader_library_new_collection :
          R.string.reader_library_rename_collection)
      .setView(input)
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton(android.R.string.ok, (_dialog, _which) ->
      {
        try
        {
          if (collection == null)
            _library.createCollection(input.getText().toString());
          else
            _library.renameCollection(collection.id, input.getText().toString());
          refresh();
        }
        catch (ReaderLibrary.LibraryException error)
        {
          showMessage(error.getMessage());
        }
      })
      .show();
  }

  private void confirmDeleteCollection(ReaderLibrary.BookCollection collection)
  {
    new AlertDialog.Builder(this)
      .setTitle(R.string.reader_library_delete_collection)
      .setMessage(getString(R.string.reader_library_delete_collection_message,
            collection.name))
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton(R.string.reader_library_delete, (_dialog, _which) ->
      {
        try
        {
          _library.deleteCollection(collection.id);
          if (collection.id.equals(_activeCollectionId))
            _activeCollectionId = null;
          refresh();
        }
        catch (ReaderLibrary.LibraryException error)
        {
          showMessage(getString(R.string.reader_library_collection_error));
        }
      })
      .show();
  }

  private void showBookMenu(View anchor, ReaderLibrary.Item item)
  {
    PopupMenu menu = new PopupMenu(this, anchor);
    menu.getMenu().add(0, MENU_COLLECTIONS, 0,
        R.string.reader_library_add_to_collections);
    if (item.sourceState != ReaderLibrary.SourceState.AVAILABLE)
      menu.getMenu().add(0, MENU_LOCATE, 1, R.string.reader_library_locate_book);
    menu.getMenu().add(0, MENU_DELETE, 2, R.string.reader_library_delete);
    menu.setOnMenuItemClickListener(choice ->
    {
      if (choice.getItemId() == MENU_COLLECTIONS)
        showCollectionMembership(item);
      else if (choice.getItemId() == MENU_LOCATE)
        locateBook(item);
      else if (choice.getItemId() == MENU_DELETE)
        confirmDelete(item);
      return true;
    });
    menu.show();
  }

  private void showCollectionMembership(ReaderLibrary.Item item)
  {
    if (_collections.isEmpty())
    {
      showCollectionNameDialog(null);
      return;
    }
    try
    {
      Set<String> selected = _library.collectionIdsForItem(item.id);
      String[] names = new String[_collections.size()];
      boolean[] checked = new boolean[_collections.size()];
      for (int i = 0; i < _collections.size(); i++)
      {
        names[i] = _collections.get(i).name;
        checked[i] = selected.contains(_collections.get(i).id);
      }
      new AlertDialog.Builder(this)
        .setTitle(R.string.reader_library_add_to_collections)
        .setMultiChoiceItems(names, checked, (_dialog, which, value) ->
            checked[which] = value)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(android.R.string.ok, (_dialog, _which) ->
        {
          LinkedHashSet<String> chosen = new LinkedHashSet<>();
          for (int i = 0; i < checked.length; i++)
            if (checked[i])
              chosen.add(_collections.get(i).id);
          try
          {
            _library.setItemCollections(item.id, chosen);
            refreshBooks();
          }
          catch (ReaderLibrary.LibraryException error)
          {
            showMessage(getString(R.string.reader_library_collection_error));
          }
        })
        .show();
    }
    catch (ReaderLibrary.LibraryException error)
    {
      showMessage(getString(R.string.reader_library_collection_error));
    }
  }

  private void toggleFavorite(ReaderLibrary.Item item)
  {
    try
    {
      _library.setFavorite(item.id, !item.favorite);
      refreshBooks();
    }
    catch (ReaderLibrary.LibraryException error)
    {
      showMessage(getString(R.string.reader_library_collection_error));
    }
  }

  private void open(ReaderLibrary.Item item)
  {
    if (item.importState != ReaderLibrary.ImportState.READY ||
        item.sourceState != ReaderLibrary.SourceState.AVAILABLE ||
        (item.sourceType != ReaderLibrary.SourceType.EPUB && item.units.isEmpty()))
    {
      showMessage(item.errorMessage == null
          ? getString(item.sourceType == ReaderLibrary.SourceType.EPUB
            ? R.string.reader_library_book_missing
            : R.string.reader_library_item_unavailable)
          : item.errorMessage);
      return;
    }
    ReaderActivity.startLibraryItem(this, item.id);
  }

  private void openOriginal(ReaderLibrary.Item item)
  {
    if (!ReaderActivity.isSafeOriginalUri(item.sourceUri))
      return;
    try
    {
      startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(item.sourceUri)));
    }
    catch (RuntimeException error)
    {
      showMessage(getString(R.string.reader_open_original_error));
    }
  }

  private void locateBook(ReaderLibrary.Item item)
  {
    _pendingLocateId = item.id;
    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
      .addCategory(Intent.CATEGORY_OPENABLE)
      .setType(ReaderBooksFolder.EPUB_MIME)
      .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
          Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
    startActivityForResult(intent, REQUEST_LOCATE_BOOK);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data)
  {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode != REQUEST_LOCATE_BOOK)
      return;
    String itemId = _pendingLocateId;
    _pendingLocateId = null;
    Uri source = data == null ? null : data.getData();
    if (resultCode != RESULT_OK || itemId == null || source == null)
      return;
    new Thread(() -> rebindBook(itemId, source), "ReaderLocateBook").start();
  }

  private void rebindBook(String itemId, Uri source)
  {
    File temporary = null;
    ReaderBooksFolder.Prepared prepared = null;
    try
    {
      ReaderLibrary.Item item = _library.get(itemId);
      if (item == null)
        throw new ReaderLibrary.LibraryException("This book is no longer saved.");
      temporary = File.createTempFile("reader-locate-", ".epub", getCacheDir());
      try (InputStream input = getContentResolver().openInputStream(source);
           FileOutputStream output = new FileOutputStream(temporary))
      {
        if (input == null)
          throw new IOException("missing content stream");
        copyBounded(input, output);
      }
      ReaderEpubImporter.Book parsed = ReaderEpubImporter.readFile(temporary);
      String hash = ReaderLibrary.contentHash(parsed.contentUnits());
      if (!item.contentHash.equals(hash))
        throw new ReaderLibrary.LibraryException(
            "That EPUB is a different book. Nothing was changed.");
      prepared = ReaderBooksFolder.prepare(this, source, item.title + ".epub");
      _library.rebindBookSource(item.id, prepared.documentUri.toString(),
          prepared.treeUri.toString(), hash, prepared.size,
          prepared.lastModified);
      runOnUiThread(() ->
      {
        refresh();
        showMessage(getString(R.string.reader_library_book_rebound));
      });
    }
    catch (IOException | ReaderLibrary.LibraryException |
        ReaderImportPipeline.ImportException | RuntimeException error)
    {
      ReaderBooksFolder.deleteCreatedQuietly(this, prepared);
      String message = error.getMessage();
      runOnUiThread(() -> showMessage(message == null
            ? getString(R.string.reader_library_locate_error) : message));
    }
    finally
    {
      if (temporary != null)
        temporary.delete();
    }
  }

  private static void copyBounded(InputStream input, FileOutputStream output)
      throws IOException, ReaderImportPipeline.ImportException
  {
    byte[] buffer = new byte[8192];
    int total = 0;
    int count;
    while ((count = input.read(buffer)) != -1)
    {
      total += count;
      if (total > ReaderImportPipeline.MAX_DOCUMENT_BYTES)
        throw new ReaderImportPipeline.ImportException(
            "This EPUB is too large to import safely.");
      output.write(buffer, 0, count);
    }
  }

  private void confirmDelete(ReaderLibrary.Item item)
  {
    new AlertDialog.Builder(this)
      .setTitle(R.string.reader_library_delete_title)
      .setMessage(getString(R.string.reader_library_delete_message, item.title))
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton(R.string.reader_library_delete, (_dialog, _which) ->
          delete(item))
      .show();
  }

  private void delete(ReaderLibrary.Item item)
  {
    try
    {
      if (_service != null && item.id.equals(_service.snapshot().itemId))
        _service.handleAction(ReaderPlaybackService.ACTION_STOP);
      _library.delete(item.id);
      refresh();
    }
    catch (ReaderLibrary.LibraryException error)
    {
      showMessage(getString(R.string.reader_library_delete_error));
    }
  }

  private void showMessage(String message)
  {
    _message.setVisibility(View.VISIBLE);
    _message.setText(message);
  }

  @Override public void onReaderPlaybackChanged(
      ReaderPlaybackService.Snapshot snapshot) {}

  @Override
  protected void onDestroy()
  {
    if (_service != null)
      _service.removeListener(this);
    if (_bound)
      unbindService(_connection);
    _bound = false;
    _service = null;
    if (_library != null)
      _library.close();
    super.onDestroy();
  }

  private final class BookAdapter extends BaseAdapter
  {
    @Override public int getCount() { return _books.size(); }
    @Override public ReaderLibrary.Item getItem(int position)
    {
      return _books.get(position);
    }
    @Override public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View recycled, ViewGroup parent)
    {
      View card = recycled == null
        ? LayoutInflater.from(ReaderLibraryActivity.this).inflate(
            R.layout.reader_book_card, parent, false)
        : recycled;
      ReaderLibrary.Item item = getItem(position);
      View coverFrame = card.findViewById(R.id.reader_book_cover_frame);
      int coverWidth = Math.max(dp(120), _grid.getColumnWidth() - dp(16));
      ViewGroup.LayoutParams coverParams = coverFrame.getLayoutParams();
      coverParams.height = Math.round(coverWidth * 1.45f);
      coverFrame.setLayoutParams(coverParams);
      TextView fallback =
        (TextView)card.findViewById(R.id.reader_book_cover_fallback);
      fallback.setText(bookInitial(item.title));
      bindPreview((ImageView)card.findViewById(R.id.reader_book_cover), item,
          Math.max(dp(140), _grid.getColumnWidth()));
      ImageView cover = (ImageView)card.findViewById(R.id.reader_book_cover);
      fallback.setVisibility(cover.getVisibility() == View.VISIBLE
          ? View.GONE : View.VISIBLE);
      ((TextView)card.findViewById(R.id.reader_book_title)).setText(item.title);
      TextView author = (TextView)card.findViewById(R.id.reader_book_author);
      author.setText(item.author == null || item.author.trim().isEmpty()
          ? getString(R.string.reader_library_unknown_author) : item.author);
      int percent = Math.max(0, Math.min(100,
            Math.round(item.progressFraction * 100f)));
      ((ProgressBar)card.findViewById(R.id.reader_book_progress_bar))
        .setProgress(percent);
      ((TextView)card.findViewById(R.id.reader_book_progress_text))
        .setText(item.finished ? getString(R.string.reader_library_finished) :
            getString(R.string.reader_library_progress, percent));
      TextView missing =
        (TextView)card.findViewById(R.id.reader_book_missing);
      missing.setVisibility(item.sourceState == ReaderLibrary.SourceState.AVAILABLE
          ? View.GONE : View.VISIBLE);
      ImageButton favorite =
        (ImageButton)card.findViewById(R.id.reader_book_favorite);
      favorite.setColorFilter(Color.parseColor(item.favorite
            ? "#F5C451" : "#C4CDD6"));
      favorite.setContentDescription(getString(item.favorite
            ? R.string.reader_library_unfavorite_accessibility
            : R.string.reader_library_favorite_accessibility, item.title));
      favorite.setOnClickListener(_view -> toggleFavorite(item));
      ImageButton more = (ImageButton)card.findViewById(R.id.reader_book_more);
      more.setContentDescription(getString(
            R.string.reader_library_book_menu_accessibility, item.title));
      more.setOnClickListener(view -> showBookMenu(view, item));
      card.setContentDescription(getString(
            R.string.reader_library_book_accessibility, item.title,
            author.getText(), percent));
      card.setOnClickListener(_view -> open(item));
      return card;
    }
  }

  private final class LibraryAdapter extends BaseAdapter
  {
    @Override public int getCount() { return _items.size(); }
    @Override public ReaderLibrary.Item getItem(int position)
    {
      return _items.get(position);
    }
    @Override public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View recycled, ViewGroup parent)
    {
      View row = recycled == null
        ? LayoutInflater.from(ReaderLibraryActivity.this).inflate(
            R.layout.reader_library_row, parent, false)
        : recycled;
      ReaderLibrary.Item item = getItem(position);
      ((TextView)row.findViewById(R.id.reader_library_row_title))
        .setText(item.title);
      bindPreview((ImageView)row.findViewById(
            R.id.reader_library_row_image), item, dp(72));
      ((TextView)row.findViewById(R.id.reader_library_row_metadata))
        .setText(metadata(item));
      ((TextView)row.findViewById(R.id.reader_library_row_progress))
        .setText(item.finished
            ? getString(R.string.reader_library_finished)
            : getString(R.string.reader_library_progress,
                Math.round(item.progressFraction * 100f)));
      Button original =
        (Button)row.findViewById(R.id.reader_library_row_original);
      boolean hasOriginal = ReaderActivity.isSafeOriginalUri(item.sourceUri);
      original.setVisibility(hasOriginal ? View.VISIBLE : View.GONE);
      original.setContentDescription(getString(
          R.string.reader_open_original_accessibility, item.title));
      original.setOnClickListener(_view -> openOriginal(item));
      Button open = (Button)row.findViewById(R.id.reader_library_row_open);
      open.setEnabled(item.importState == ReaderLibrary.ImportState.READY &&
          !item.units.isEmpty());
      open.setContentDescription(getString(
          R.string.reader_library_open_accessibility, item.title));
      open.setOnClickListener(_view -> open(item));
      Button delete = (Button)row.findViewById(R.id.reader_library_row_delete);
      delete.setContentDescription(getString(
          R.string.reader_library_delete_accessibility, item.title));
      delete.setOnClickListener(_view -> confirmDelete(item));
      return row;
    }
  }

  private void bindPreview(ImageView view, ReaderLibrary.Item item, int target)
  {
    view.setImageDrawable(null);
    view.setVisibility(View.GONE);
    if (item.imageUri == null || !item.imageUri.startsWith("private:"))
      return;
    try
    {
      File file = _library.privateSourceFile(
          item.imageUri.substring("private:".length()));
      BitmapFactory.Options bounds = new BitmapFactory.Options();
      bounds.inJustDecodeBounds = true;
      BitmapFactory.decodeFile(file.getPath(), bounds);
      if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
        return;
      int sample = 1;
      while (bounds.outWidth / sample > target * 2 ||
          bounds.outHeight / sample > target * 3)
        sample *= 2;
      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inSampleSize = sample;
      Bitmap image = BitmapFactory.decodeFile(file.getPath(), options);
      if (image != null)
      {
        view.setImageBitmap(image);
        view.setVisibility(View.VISIBLE);
      }
    }
    catch (ReaderLibrary.LibraryException | RuntimeException ignored)
    {
      // A missing or invalid optional cover must not hide the book.
    }
  }

  private String bookInitial(String title)
  {
    String value = title == null ? "" : title.trim();
    return value.isEmpty() ? "?" : value.substring(0, 1).toUpperCase(Locale.ROOT);
  }

  private int dp(int value)
  {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  private String metadata(ReaderLibrary.Item item)
  {
    DateFormat format = DateFormat.getDateTimeInstance(
        DateFormat.MEDIUM, DateFormat.SHORT);
    String source = sourceLabel(item);
    String imported = format.format(new Date(item.createdAt));
    String opened = item.lastOpenedAt == 0L
      ? getString(R.string.reader_library_never_opened)
      : format.format(new Date(item.lastOpenedAt));
    return getString(R.string.reader_library_metadata, source, imported, opened);
  }

  private String sourceLabel(ReaderLibrary.Item item)
  {
    if (item.sourceType == ReaderLibrary.SourceType.URL &&
        ReaderActivity.isSafeOriginalUri(item.sourceUri))
    {
      try
      {
        String host = new URI(item.sourceUri).getHost();
        if (host != null && !host.isEmpty())
          return host.startsWith("www.") ? host.substring(4) : host;
      }
      catch (Exception ignored) {}
    }
    return item.sourceType.name().replace('_', ' ')
      .toLowerCase(Locale.getDefault());
  }
}