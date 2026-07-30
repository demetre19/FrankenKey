package juloo.keyboard2;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/** Browses app-private Reader items and hands selections to ReaderActivity. */
public final class ReaderLibraryActivity extends Activity
    implements ReaderPlaybackService.Listener
{
  private final ArrayList<ReaderLibrary.Item> _items = new ArrayList<>();
  private ReaderLibrary _library;
  private ReaderPlaybackService _service;
  private boolean _bound;
  private TextView _message;
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
      _items.clear();
      _items.addAll(_library.list());
      _adapter.notifyDataSetChanged();
      _message.setText(_items.isEmpty()
          ? R.string.reader_library_empty : R.string.reader_library_title);
      _message.setVisibility(_items.isEmpty() ? View.VISIBLE : View.GONE);
      _list.setVisibility(_items.isEmpty() ? View.GONE : View.VISIBLE);
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
      ((TextView)row.findViewById(R.id.reader_library_row_metadata))
        .setText(metadata(item));
      ((TextView)row.findViewById(R.id.reader_library_row_progress))
        .setText(item.finished
            ? getString(R.string.reader_library_finished)
            : getString(R.string.reader_library_progress,
                Math.round(item.progressFraction * 100f)));
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

  private String metadata(ReaderLibrary.Item item)
  {
    DateFormat format = DateFormat.getDateTimeInstance(
        DateFormat.MEDIUM, DateFormat.SHORT);
    String source = item.sourceType.name().replace('_', ' ')
      .toLowerCase(Locale.getDefault());
    String imported = format.format(new Date(item.createdAt));
    String opened = item.lastOpenedAt == 0L
      ? getString(R.string.reader_library_never_opened)
      : format.format(new Date(item.lastOpenedAt));
    return getString(R.string.reader_library_metadata, source, imported, opened);
  }
}
