package juloo.keyboard2;

import android.os.Build.VERSION;
import android.os.Handler;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.SurroundingText;

/** Keep track of the word being typed. This also tracks whether the selection
    is empty. */
public final class CurrentlyTypedWord
{
  InputConnection _ic = null;
  Handler _handler;
  Callback _callback;

  /** The currently typed word. */
  StringBuilder _w = new StringBuilder();
  TouchTrace _touch_trace = new TouchTrace();
  /** This can be disabled if the editor doesn't support looking at the text
      before the cursor. */
  boolean _enabled = false;
  /** The current word is empty while the selection is ongoing. */
  boolean _has_selection = false;
  /** Used to avoid concurrent refreshes in [delayed_refresh()]. */
  boolean _refresh_pending = false;

  /** Monotonic identity for the validity of the current state. */
  long _revision = 0;

  /** The estimated cursor position in code points. Used to avoid expensive IPC
      calls when the typed word can be estimated locally with [typed]. When the
      cursor position gets out of sync, the text before the cursor is queried
      again to the editor. */
  int _cursor;
  /** The cursor position within the current word relative to the end of the
      word in UTF-16 chars. Equal to [0] when the cursor is at the end. */
  int _w_cursor;

  public CurrentlyTypedWord(Handler h, Callback cb)
  {
    _handler = h;
    _callback = cb;
  }

  public String get()
  {
    return _w.toString();
  }

  public TouchTrace.Snapshot touch_trace()
  {
    return _touch_trace.snapshot();
  }

  public Snapshot snapshot()
  {
    return new Snapshot(_revision, _w.toString(), _w_cursor, _has_selection,
        _touch_trace.snapshot());
  }

  public boolean is_selection_not_empty()
  {
    return _has_selection;
  }

  /** The cursor position relative to the end of the word. */
  public int cursor_relative()
  {
    return _w_cursor;
  }

  public void started(Config conf, InputConnection ic)
  {
    cancel_delayed_refresh();
    _ic = ic;
    _enabled = true;
    EditorConfig e = conf.editor_config;
    _has_selection = e.initial_sel_start != e.initial_sel_end;
    _cursor = e.initial_sel_start;
    _w_cursor = 0;
    _w.setLength(0);
    _touch_trace.clear();
    if (!_has_selection)
    {
      replace_current_word(e.initial_text_before_cursor);
      _w_cursor = (e.initial_text_after_cursor == null) ? 0 :
        -append_chars(e.initial_text_after_cursor);
    }
    publish_transition();
  }

  /** Stop tracking the current editor and cancel its delayed refresh. */
  public void finished()
  {
    boolean had_state = _enabled || _ic != null || _refresh_pending
      || _w.length() != 0 || _has_selection;
    cancel_delayed_refresh();
    _enabled = false;
    _ic = null;
    _w.setLength(0);
    _touch_trace.clear();
    _has_selection = false;
    _cursor = 0;
    _w_cursor = 0;
    if (had_state)
      ++_revision;
  }

  public void typed(String s)
  {
    typed(s, null);
  }

  public void typed(String s, TouchTrace.Entry touch)
  {
    if (!_enabled)
      return;
    _has_selection = false;
    type_chars(s, touch);
    publish_transition();
  }

  /** Update a verified, same-length editor rewrite without refreshing it. */
  boolean rewrite_current_suffix(String expected_suffix, String replacement_suffix)
  {
    if (!_enabled || _has_selection || _w_cursor != 0
        || expected_suffix.length() == 0
        || expected_suffix.length() != replacement_suffix.length())
      return false;
    int replace_start = _w.length() - expected_suffix.length();
    if (replace_start < 0)
      return false;
    for (int i = 0; i < expected_suffix.length(); i++)
      if (_w.charAt(replace_start + i) != expected_suffix.charAt(i))
        return false;
    _w.replace(replace_start, _w.length(), replacement_suffix);
    publish_transition();
    return true;
  }

  public void selection_updated(int oldSelStart, int newSelStart, int newSelEnd)
  {
    // Avoid the expensive [refresh_current_word] call when [typed] was called
    // before.
    if (!_enabled)
      return;
    boolean new_has_sel = newSelStart != newSelEnd;
    if (new_has_sel || _has_selection) // Selection was on or is now on.
    {
      _cursor = newSelStart;
      _has_selection = new_has_sel;
      refresh_current_word();
    }
    else if (newSelStart != _cursor)
    {
      _cursor = newSelStart;
      _w_cursor += newSelStart - oldSelStart;
      if (_w_cursor < -_w.length() || _w_cursor > 0)
        refresh_current_word();
      else
        publish_transition();
    }
  }

  public void event_sent(int code, int meta)
  {
    if (!_enabled)
      return;
    // Invalidate any exact decoder result immediately. The editor-derived word
    // is published only after the editor has processed the event.
    ++_revision;
    delayed_refresh();
  }

  /** Update local word state after a raw terminal DEL event. */
  public void raw_backspace()
  {
    if (!_enabled)
      return;
    cancel_delayed_refresh();
    if (_has_selection || _w_cursor != 0)
    {
      _w.setLength(0);
      _touch_trace.clear();
      _w_cursor = 0;
      publish_transition();
      return;
    }
    if (_w.length() > 0)
    {
      int delete_at = _w.offsetByCodePoints(_w.length(), -1);
      _w.delete(delete_at, _w.length());
      _touch_trace.removeFrom(_w.codePointCount(0, _w.length()));
      if (_cursor > 0)
        --_cursor;
    }
    publish_transition();
  }

  void publish_transition()
  {
    ++_revision;
    _callback.currently_typed_word(snapshot());
  }

  /** Estimate the currently typed word after [chars] has been typed. */
  void type_chars(CharSequence s, int start, int end, TouchTrace.Entry touch)
  {
    int insert_start = 0;
    // Iterate over code points as that's the unit of [_cursor].
    for (int i = start; i < end;)
    {
      int c = Character.codePointAt(s, i);
      i += Character.charCount(c);
      _cursor++;
      // [i >= end] might happen when the cursor is in the middle of a
      // surrogate pair
      if (!is_word_char(c) && i <= end)
        insert_start = i;
    }
    int insert_at = Math.max(_w.length() + _w_cursor, 0);
    if (insert_start > 0)
    {
      _touch_trace.clear();
      _w.delete(0, insert_at);
      insert_at = 0;
    }

    int insert_at_code_point = _w.codePointCount(0, insert_at);
    int inserted_code_points = Character.codePointCount(s, insert_start, end);
    _w.insert(insert_at, s, insert_start, end);

    if (_touch_trace.size() < insert_at_code_point)
      _touch_trace.addNulls(insert_at_code_point - _touch_trace.size());
    _touch_trace.removeFrom(insert_at_code_point);
    _touch_trace.addNulls(_w.codePointCount(0, _w.length())
        - _touch_trace.size());
    if (touch != null && inserted_code_points == 1 && _w_cursor == 0)
      _touch_trace.set(insert_at_code_point, touch);
  }

  void type_chars(CharSequence s)
  {
    type_chars(s, 0, s.length(), null);
  }

  void type_chars(CharSequence s, TouchTrace.Entry touch)
  {
    type_chars(s, 0, s.length(), touch);
  }

  /** Append chars to the current word without moving the cursor. Return the
      number of UTF-16 characters that were added in the current word. */
  int append_chars(CharSequence s, int start, int end)
  {
    int i = start;
    while (i < end)
    {
      int c = Character.codePointAt(s, i);
      if (!is_word_char(c))
        break;
      _w.appendCodePoint(c);
      _touch_trace.add(null);
      i += Character.charCount(c);
    }
    return i - start;
  }

  int append_chars(CharSequence s)
  {
    return append_chars(s, 0, s.length());
  }

  /** Refresh the current word by immediately querying the editor. */
  void refresh_current_word()
  {
    if (!_enabled)
      return;
    Logs.debug("Refresh current word");
    _refresh_pending = false;
    _w_cursor = 0;
    if (_has_selection || _ic == null)
      set_current_word((CharSequence)null);
    else if (VERSION.SDK_INT >= 31)
      set_current_word(_ic.getSurroundingText(20, 20, 0));
    else
      set_current_word(_ic.getTextBeforeCursor(20, 0));
  }

  /** Refresh from editor text before the cursor. */
  void set_current_word(CharSequence text_before_cursor)
  {
    replace_current_word(text_before_cursor);
    publish_transition();
  }

  void replace_current_word(CharSequence text_before_cursor)
  {
    _w.setLength(0);
    _touch_trace.clear();
    _w_cursor = 0;
    if (text_before_cursor == null)
      return;
    int saved_cursor = _cursor;
    type_chars(text_before_cursor.toString());
    _cursor = saved_cursor;
  }

  /** Like above but take the text after the cursor into account. */
  void set_current_word(SurroundingText st)
  {
    _w.setLength(0);
    _touch_trace.clear();
    _w_cursor = 0;
    if (st != null)
    {
      int saved_cursor = _cursor;
      int st_sel = st.getSelectionStart();
      CharSequence st_text = st.getText();
      type_chars(st_text, 0, st_sel, null);
      _w_cursor = -append_chars(st_text, st_sel, st_text.length());
      _cursor = saved_cursor;
    }
    publish_transition();
  }

  /** Wait some time to let the editor finish reacting to changes and call
      [refresh_current_word]. */
  void delayed_refresh()
  {
    _refresh_pending = true;
    if (_handler == null)
      return;
    _handler.removeCallbacks(delayed_refresh_run);
    _handler.postDelayed(delayed_refresh_run, 50);
  }

  void cancel_delayed_refresh()
  {
    _refresh_pending = false;
    if (_handler != null)
      _handler.removeCallbacks(delayed_refresh_run);
  }

  final Runnable delayed_refresh_run = new Runnable()
  {
    public void run()
    {
      if (_enabled && _refresh_pending)
        refresh_current_word();
    }
  };

  /** A word is the longest consecutive sequence for which [is_word_char]
      returns [true]. */
  public static boolean is_word_char(int c)
  {
    return Character.isLetterOrDigit(c) || (c == '\'');
  }

  public static final class Snapshot
  {
    public final long revision;
    public final String word;
    public final int cursorRelative;
    public final boolean hasSelection;
    public final TouchTrace.Snapshot touches;

    private Snapshot(long revision_, String word_, int cursor_relative_,
        boolean has_selection_, TouchTrace.Snapshot touches_)
    {
      revision = revision_;
      word = word_;
      cursorRelative = cursor_relative_;
      hasSelection = has_selection_;
      touches = touches_;
    }
  }

  public static interface Callback
  {
    public void currently_typed_word(Snapshot snapshot);
  }
}
