package juloo.keyboard2;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.net.Uri;
import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.net.URI;

/** Browses app-private Reader items and hands selections to ReaderActivity. */
public final class ReaderLibraryActivity extends Activity
    implements ReaderPlaybackService.Listener
{
  private final ArrayList<ReaderLibrary.Item> _allItems = new ArrayList<>();
  private final ArrayList<ReaderLibrary.Item> _items = new ArrayList<>();
  private ReaderLibrary _library;
  private ReaderPlaybackService _service;
  private boolean _bound;
  private TextView _message;
  private EditText _search;
  private ListView _list;
  private final LibraryAdapter _adapter = new LibraryAdapter();
  private final ServiceConnection _connection = new ServiceConnection()
  {
    @Override public void onServiceConnected(ComponentName name, IBinder binder)
    {
      _service = ((ReaderPlaybackService.LocalBinder)binder).service();
      _bound = true;
      _service.addListener(ReaderLibraryActivity.this);
      _list.setEnabled(true);
    }

    @Override public void onServiceDisconnected(ComponentName name)
    {
      _bound = false;
      _service = null;
      _list.setEnabled(false);
    }
  };

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    if (getActionBar() != null)
      getActionBar().hide();
    setContentView(R.layout.reader_library_activity);
    _message = (TextView)findViewById(R.id.reader_library_message);
    _list = (ListView)findViewById(R.id.reader_library_list);
    _search = (EditText)findViewById(R.id.reader_library_search);
    _search.addTextChangedListener(new TextWatcher()
    {
      @Override public void beforeTextChanged(CharSequence value, int start,
          int count, int after) {}
      @Override public void onTextChanged(CharSequence value, int start,
          int before, int count)
      {
        filter(value == null ? "" : value.toString());
      }
      @Override public void afterTextChanged(Editable value) {}
    });
    _list.setAdapter(_adapter);
    _list.setEnabled(false);
    findViewById(R.id.reader_library_back).setOnClickListener(
        _view -> finish());
    _library = new ReaderLibrary(this);
    refresh();
    bindService(new Intent(this, ReaderPlaybackService.class), _connection,
        Context.BIND_AUTO_CREATE);
  }

  private void refresh()
  {
    try
    {
      _allItems.clear();
      _allItems.addAll(_library.list());
      filter(_search == null ? "" : _search.getText().toString());
    }
    catch (ReaderLibrary.LibraryException error)
    {
      _items.clear();
      _adapter.notifyDataSetChanged();
      _list.setVisibility(View.GONE);
      _message.setVisibility(View.VISIBLE);
      _message.setText(R.string.reader_library_error);
    }
  }

  private void filter(String query)
  {
    String normalized = query == null ? "" :
      query.trim().toLowerCase(Locale.getDefault());
    _items.clear();
    for (ReaderLibrary.Item item : _allItems)
      if (normalized.isEmpty() || matches(item, normalized))
        _items.add(item);
    _adapter.notifyDataSetChanged();
    boolean noItems = _allItems.isEmpty();
    boolean noMatches = !noItems && _items.isEmpty();
    _message.setText(noItems
        ? R.string.reader_library_empty : R.string.reader_library_no_matches);
    _message.setVisibility(noItems || noMatches ? View.VISIBLE : View.GONE);
    _list.setVisibility(noItems || noMatches ? View.GONE : View.VISIBLE);
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

  private void open(ReaderLibrary.Item item)
  {
    if (item.importState != ReaderLibrary.ImportState.READY ||
        item.units.isEmpty())
    {
      _message.setVisibility(View.VISIBLE);
      _message.setText(item.errorMessage == null
          ? getString(R.string.reader_library_item_unavailable)
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
      _message.setVisibility(View.VISIBLE);
      _message.setText(R.string.reader_open_original_error);
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
      _message.setVisibility(View.VISIBLE);
      _message.setText(R.string.reader_library_delete_error);
    }
  }

  @Override
  public void onReaderPlaybackChanged(ReaderPlaybackService.Snapshot snapshot)
  {
  }

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
            R.id.reader_library_row_image), item);
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

  private void bindPreview(ImageView view, ReaderLibrary.Item item)
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
      int target = Math.max(1, Math.round(72f *
            getResources().getDisplayMetrics().density));
      int sample = 1;
      while (bounds.outWidth / sample > target * 2 ||
          bounds.outHeight / sample > target * 2)
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
      // A missing or invalid optional preview must not hide the Library item.
    }
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
      catch (Exception ignored)
      {
      }
    }
    return item.sourceType.name().replace('_', ' ')
      .toLowerCase(Locale.getDefault());
  }
}
