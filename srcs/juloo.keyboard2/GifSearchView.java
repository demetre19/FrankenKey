package juloo.keyboard2;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

public final class GifSearchView extends LinearLayout
  implements AdapterView.OnItemClickListener
{
  private EditText _query;
  private Button _search;
  private Button _close;
  private GridView _grid;
  private TextView _status;
  private GifAdapter _adapter;
  private Handler _handler;
  private Keyboard2 _keyboard;
  private Keyboard2View _typingKeyboard;
  private final Theme _theme;


  public GifSearchView(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    _theme = new Theme(getContext(), attrs);
    _handler = new Handler();
  }

  @Override
  protected void onFinishInflate()
  {
    super.onFinishInflate();
    _query = (EditText)findViewById(R.id.gif_search_query);
    _search = (Button)findViewById(R.id.gif_search_button);
    _close = (Button)findViewById(R.id.gif_close_button);
    _grid = (GridView)findViewById(R.id.gif_grid);
    _status = (TextView)findViewById(R.id.gif_status);
    _typingKeyboard = (Keyboard2View)findViewById(R.id.gif_keyboard_view);
    applyContrastColors();
    _adapter = new GifAdapter(getContext());
    _grid.setAdapter(_adapter);
    _grid.setOnItemClickListener(this);
    _search.setOnClickListener(new OnClickListener() {
      public void onClick(View view) { refreshResults(); }
    });
    _close.setOnClickListener(new OnClickListener() {
      public void onClick(View view) {
        if (_keyboard != null)
          _keyboard.showKeyboardView();
      }
    });
    _query.setOnEditorActionListener((view, actionId, event) -> {
      refreshResults();
      return true;
    });
  }

  private void applyContrastColors()
  {
    int textColor = Theme.contrastTextColor(_theme.colorKeyboard);
    int mutedTextColor = mutedTextColor(_theme);
    _query.setTextColor(textColor);
    _query.setHintTextColor(mutedTextColor);
    _status.setTextColor(mutedTextColor);
  }

  private static int mutedTextColor(Theme theme)
  {
    return Theme.ensureTextContrast(
        Theme.adjustLight(Theme.contrastTextColor(theme.colorKeyboard), 0.25f),
        theme.colorKeyboard);
  }

  void setKeyboard(Keyboard2 keyboard, KeyboardData typingLayout)
  {
    _keyboard = keyboard;
    if (_typingKeyboard != null)
      _typingKeyboard.setKeyboard(typingLayout);
    if (_query != null)
      _query.requestFocus();
    refreshResults();
  }

  void setTypingKeyboard(KeyboardData typingLayout)
  {
    if (_typingKeyboard != null)
      _typingKeyboard.setKeyboard(typingLayout);
  }

  InputConnection getSearchInputConnection()
  {
    if (_query == null)
      return null;
    _query.requestFocus();
    EditorInfo info = new EditorInfo();
    info.imeOptions = EditorInfo.IME_ACTION_SEARCH;
    info.inputType = _query.getInputType();
    InputConnection conn = _query.onCreateInputConnection(info);
    return conn == null ? null : new SearchInputConnection(conn,
        new Runnable() {
          public void run() { refreshAfterSpace(); }
        });
  }

  private void refreshAfterSpace()
  {
    if (_query != null && _query.getText().toString().trim().length() > 0)
      refreshResults();
  }


  static final class SearchInputConnection extends InputConnectionWrapper
  {
    private final Runnable _afterSpace;

    SearchInputConnection(InputConnection target, Runnable afterSpace)
    {
      super(target, true);
      _afterSpace = afterSpace;
    }

    @Override
    public boolean commitText(CharSequence text, int newCursorPosition)
    {
      boolean handled = super.commitText(text, newCursorPosition);
      if (handled && text != null && text.toString().indexOf(' ') >= 0)
        _afterSpace.run();
      return handled;
    }

    @Override
    public boolean sendKeyEvent(KeyEvent event)
    {
      if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_DEL)
      {
        if (event.getAction() == KeyEvent.ACTION_DOWN)
          return deleteSurroundingText(1, 0);
        if (event.getAction() == KeyEvent.ACTION_UP)
          return true;
      }
      return super.sendKeyEvent(event);
    }
  }

  void refreshResults()
  {
    final String query = _query == null ? "" : _query.getText().toString();
    final String apiKey = apiKey();
    if (apiKey.length() == 0)
    {
      showLocal(query, getContext().getString(R.string.gif_status_no_api_key));
      return;
    }
    setStatus(R.string.gif_status_searching);
    new Thread(new Runnable() {
      public void run()
      {
        try
        {
          final List<GifResult> results = GiphyClient.search(apiKey, query);
          _handler.post(new Runnable() {
            public void run()
            {
              if (results.isEmpty())
                showLocal(query, getContext().getString(R.string.gif_status_no_results));
              else
              {
                _adapter.setResults(results);
                _status.setText(R.string.gif_status_giphy_results);
              }
            }
          });
        }
        catch (final Exception e)
        {
          _handler.post(new Runnable() {
            public void run()
            {
              showLocal(query, getContext().getString(R.string.gif_status_search_failed));
            }
          });
        }
      }
    }).start();
  }

  private void showLocal(String query, String message)
  {
    _adapter.setResults(GifLibrary.searchLocal(query));
    _status.setText(message);
  }

  private void setStatus(int resId)
  {
    _status.setText(resId);
  }

  private String apiKey()
  {
    try
    {
      SharedPreferences prefs = DirectBootAwarePreferences.get_shared_preferences(getContext());
      return prefs.getString(GiphyClient.PREF_API_KEY, "").trim();
    }
    catch (Exception _e)
    {
      return "";
    }
  }

  public void onItemClick(AdapterView<?> parent, View view, int pos, long id)
  {
    final GifResult item = (GifResult)_adapter.getItem(pos);
    if (_keyboard == null)
      return;
    if (!GifInserter.editorAcceptsGif(_keyboard.getCurrentInputEditorInfo()))
    {
      Toast.makeText(getContext(), R.string.toast_gif_unsupported,
          Toast.LENGTH_SHORT).show();
      return;
    }
    if (!item.isRemote())
    {
      commit(item);
      return;
    }
    setStatus(R.string.gif_status_downloading);
    new Thread(new Runnable() {
      public void run()
      {
        try
        {
          final GifResult cached = GiphyClient.downloadToCache(getContext(), item);
          _handler.post(new Runnable() {
            public void run() { commit(cached); }
          });
        }
        catch (Exception _e)
        {
          _handler.post(new Runnable() {
            public void run()
            {
              _status.setText(R.string.gif_status_download_failed);
            }
          });
        }
      }
    }).start();
  }

  private void commit(GifResult item)
  {
    InputConnection conn = _keyboard.getCurrentInputConnection();
    EditorInfo info = _keyboard.getCurrentInputEditorInfo();
    if (!GifInserter.insertGif(getContext(), conn, info, item))
    {
      Toast.makeText(getContext(), R.string.toast_gif_unsupported,
          Toast.LENGTH_SHORT).show();
      return;
    }
    _keyboard.showKeyboardView();
  }

  static final class GifAdapter extends BaseAdapter
  {
    private final Context _context;
    private final Handler _handler = new Handler();
    private List<GifResult> _results = new ArrayList<GifResult>();

    GifAdapter(Context context)
    {
      _context = context;
    }

    void setResults(List<GifResult> results)
    {
      _results = results == null ? new ArrayList<GifResult>() : results;
      notifyDataSetChanged();
    }

    public int getCount() { return _results.size(); }
    public Object getItem(int pos) { return _results.get(pos); }
    public long getItemId(int pos) { return pos; }

    public View getView(int pos, View convertView, ViewGroup parent)
    {
      GifTile tile = (GifTile)convertView;
      if (tile == null)
        tile = new GifTile(_context);
      tile.setResult(_results.get(pos), _handler);
      return tile;
    }
  }

  static final class GifTile extends LinearLayout
  {
    private final ImageView _image;
    private final TextView _title;

    GifTile(Context context)
    {
      super(context);
      setOrientation(VERTICAL);
      int pad = (int)(6 * getResources().getDisplayMetrics().density);
      setPadding(pad, pad, pad, pad);
      _image = new ImageView(context);
      _image.setScaleType(ImageView.ScaleType.CENTER_CROP);
      addView(_image, new LayoutParams(LayoutParams.MATCH_PARENT,
            (int)(72 * getResources().getDisplayMetrics().density)));
      _title = new TextView(context);
      _title.setSingleLine(true);
      _title.setTextColor(mutedTextColor(new Theme(context, null)));
      _title.setTextSize(12);
      addView(_title, new LayoutParams(LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT));
    }

    private void setLocalPreview(int rawResId)
    {
      try
      {
        _image.setImageBitmap(BitmapFactory.decodeStream(
              getResources().openRawResource(rawResId)));
      }
      catch (Exception _e)
      {
        _image.setImageDrawable(null);
      }
    }

    void setResult(final GifResult result, final Handler handler)
    {
      _title.setText(result.title);
      if (result.isLocal())
      {
        _image.setTag(null);
        setLocalPreview(result.rawResId);
        return;
      }
      setLocalPreview(R.raw.frankenkey_gif);
      final String preview = result.previewUrl;
      _image.setTag(preview);
      if (preview == null)
        return;
      new Thread(new Runnable() {
        public void run()
        {
          try
          {
            final Bitmap bitmap = GiphyClient.downloadBitmap(preview);
            handler.post(new Runnable() {
              public void run()
              {
                if (preview.equals(_image.getTag()) && bitmap != null)
                  _image.setImageBitmap(bitmap);
              }
            });
          }
          catch (Exception _e)
          {
          }
        }
      }).start();
    }
  }
}
