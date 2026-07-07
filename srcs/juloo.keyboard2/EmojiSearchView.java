package juloo.keyboard2;

import android.content.Context;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

public final class EmojiSearchView extends LinearLayout
{
  private EditText _query;
  private Button _close;
  private Button _clear;
  private EmojiGridView _grid;
  private Keyboard2View _typingKeyboard;
  private Keyboard2 _keyboard;
  private Handler _handler;
  private final Theme _theme;

  public EmojiSearchView(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    _theme = new Theme(getContext(), attrs);
    _handler = new Handler();
  }

  @Override
  protected void onFinishInflate()
  {
    super.onFinishInflate();
    _query = (EditText)findViewById(R.id.emoji_search_query);
    _close = (Button)findViewById(R.id.emoji_close_button);
    _clear = (Button)findViewById(R.id.emoji_clear_button);
    _grid = (EmojiGridView)findViewById(R.id.emoji_grid);
    _typingKeyboard = (Keyboard2View)findViewById(R.id.emoji_keyboard_view);
    applyContrastColors();
    _close.setOnClickListener(view -> {
      if (_keyboard != null)
        _keyboard.showKeyboardView();
    });
    _clear.setOnClickListener(view -> {
      _query.setText("");
      _query.requestFocus();
    });
    _query.addTextChangedListener(new TextWatcher() {
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
      public void onTextChanged(CharSequence s, int start, int before, int count) {}
      public void afterTextChanged(Editable s) { refreshResults(); }
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
    _clear.setTextColor(textColor);
    _close.setTextColor(textColor);
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
          public void run() { refreshResultsSoon(); }
        });
  }

  void refreshResults()
  {
    if (_grid == null || _query == null)
      return;
    _grid.setSearchQuery(_query.getText().toString());
  }

  private void refreshResultsSoon()
  {
    _handler.post(new Runnable() {
      public void run() { refreshResults(); }
    });
  }

  boolean insertEmoji(Emoji emoji)
  {
    if (_keyboard == null || emoji == null)
      return false;
    _keyboard.insertEmojiFromPane(emoji.kv());
    return true;
  }

  static final class SearchInputConnection extends InputConnectionWrapper
  {
    private final Runnable _afterEdit;

    SearchInputConnection(InputConnection target, Runnable afterEdit)
    {
      super(target, true);
      _afterEdit = afterEdit;
    }

    @Override
    public boolean commitText(CharSequence text, int newCursorPosition)
    {
      boolean handled = super.commitText(text, newCursorPosition);
      if (handled)
        _afterEdit.run();
      return handled;
    }

    @Override
    public boolean deleteSurroundingText(int beforeLength, int afterLength)
    {
      boolean handled = super.deleteSurroundingText(beforeLength, afterLength);
      if (handled)
        _afterEdit.run();
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
}
