package juloo.keyboard2;

import android.os.Handler;
import android.text.TextUtils;
import android.view.inputmethod.InputConnection;
import android.view.KeyEvent;

public final class Autocapitalisation
{
  boolean _enabled = false;
  boolean _should_enable_shift = false;
  boolean _should_disable_shift = false;
  boolean _should_update_caps_mode = false;

  Handler _handler;
  InputConnection _ic;
  Callback _callback;
  int _caps_mode;

  /** Keep track of the cursor to recognize cursor movements from typing. */
  int _cursor;
  /** One lowercase character whose exact editor suffix may be safely rewritten. */
  int _pending_rewrite_cursor = -1;
  char _pending_rewrite_original = 0;
  char _pending_rewrite_replacement = 0;

  private static final int REWRITE_CONTEXT_LIMIT = 64;


  public Autocapitalisation(Handler h, Callback cb)
  {
    _handler = h;
    _callback = cb;
  }

  /**
   * The events are: started, typed, event sent, selection updated
   * [started] does initialisation work and must be called before any other
   * event.
   */
  public void started(Config config, InputConnection ic)
  {
    cancel_pending_rewrite();
    _ic = ic;
    EditorConfig ec = config.editor_config;
    _cursor = Math.max(0, ec.initial_sel_start);
    if (!config.autocapitalisation || ec.caps_mode == 0)
    {
      _enabled = false;
      return;
    }
    _enabled = true;
    _caps_mode = ec.caps_mode;
    _should_enable_shift = ec.caps_initially_enabled;
    _should_update_caps_mode = ec.caps_initially_updated;
    callback_now(true);
  }

  /** End an editor session and prevent delayed work touching its old connection. */
  public void finished()
  {
    cancel_pending_rewrite();
    _enabled = false;
    _should_enable_shift = false;
    _should_disable_shift = false;
    _should_update_caps_mode = false;
    _ic = null;
    _cursor = 0;
  }

  /** Re-evaluate caps mode after replacing [remove_before] preceding UTF-16
      units with [inserted] without individual key input. */
  public void text_replaced(int remove_before, CharSequence inserted)
  {
    cancel_pending_rewrite();
    _cursor = Math.max(0, _cursor - Math.max(0, remove_before)
        + (inserted == null ? 0 : inserted.length()));
    if (!_enabled)
      return;
    if (inserted != null)
      for (int i = 0; i < inserted.length(); i++)
        if (is_trigger_character(inserted.charAt(i)))
        {
          _should_update_caps_mode = true;
          break;
        }
    callback(true);
  }

  public void typed(CharSequence c)
  {
    if (c.length() == 1 && should_rewrite_lowercase(c.charAt(0)))
    {
      char original = c.charAt(0);
      type_one_char(original);
      request_rewrite(original, Character.toUpperCase(original));
    }
    else
      for (int i = 0; i < c.length(); i++)
        type_one_char(c.charAt(i));
    callback(false);
  }

  public void event_sent(int code, int meta)
  {
    cancel_pending_rewrite();
    if (meta != 0)
    {
      _should_enable_shift = false;
      _should_update_caps_mode = false;
      return;
    }
    switch (code)
    {
      case KeyEvent.KEYCODE_DEL:
        if (_cursor > 0) _cursor--;
        _should_update_caps_mode = true;
        break;
      case KeyEvent.KEYCODE_ENTER:
        _should_update_caps_mode = true;
        break;
    }
    callback(true);
  }

  public void stop()
  {
    cancel_pending_rewrite();
    _should_enable_shift = false;
    _should_update_caps_mode = false;
    callback_now(true);
  }

  /** Pause auto capitalisation until [unpause()] is called. */
  public boolean pause()
  {
    boolean was_enabled = _enabled;
    stop();
    _enabled = false;
    return was_enabled;
  }

  /** Continue auto capitalisation after [pause()] was called. Argument is the
      output of [pause()]. */
  public void unpause(boolean was_enabled)
  {
    _enabled = was_enabled;
    _should_update_caps_mode = true;
    callback_now(true);
  }

  public static interface Callback
  {
    public void update_shift_state(boolean should_enable, boolean should_disable);
    /** Replace the exact suffix before the cursor, returning whether it matched. */
    public boolean replace_recent_text(String expected_suffix,
        String replacement_suffix);
  }

  /** Returns [true] if shift might be disabled. */
  public void selection_updated(int old_cursor, int new_cursor)
  {
    if (new_cursor == _cursor) // Just typing
      return;
    cancel_pending_rewrite();
    if (new_cursor == 0 && _ic != null)
    {
      // Detect whether the input box has been cleared
      CharSequence t = _ic.getTextAfterCursor(1, 0);
      if (t != null && t.equals(""))
        _should_update_caps_mode = true;
    }
    _cursor = new_cursor;
    _should_enable_shift = false;
    callback(true);
  }

  Runnable delayed_callback = new Runnable()
  {
    public void run()
    {
      rewrite_pending_lowercase();
      if (_should_update_caps_mode && _ic != null)
      {
        _should_enable_shift = _enabled && (_ic.getCursorCapsMode(_caps_mode) != 0);
        _should_update_caps_mode = false;
      }
      _callback.update_shift_state(_should_enable_shift, _should_disable_shift);
    }
  };

  /** Update the shift state if [_should_update_caps_mode] is true, then call
      [_callback.update_shift_state]. This is done after a short delay to wait
      for the editor to handle the events, as this might be called before the
      corresponding event is sent. */
  void callback(boolean might_disable)
  {
    _should_disable_shift = might_disable;
    // The callback must be delayed because [getCursorCapsMode] would sometimes
    // be called before the editor finished handling the previous event.
    _handler.removeCallbacks(delayed_callback);
    _handler.postDelayed(delayed_callback, 50);
  }

  /** Like [callback] but runs immediately. */
  void callback_now(boolean might_disable)
  {
    _should_disable_shift = might_disable;
    delayed_callback.run();
  }

  boolean should_rewrite_lowercase(char c)
  {
    if (!_enabled || !Character.isLowerCase(c))
      return false;
    if ((_caps_mode & TextUtils.CAP_MODE_CHARACTERS) != 0
        || _should_enable_shift || _should_update_caps_mode)
      return true;
    if (_ic != null && _ic.getCursorCapsMode(_caps_mode) != 0)
      return true;
    if ((_caps_mode & TextUtils.CAP_MODE_SENTENCES) == 0 || _ic == null)
      return false;
    CharSequence before = _ic.getTextBeforeCursor(REWRITE_CONTEXT_LIMIT, 0);
    if (before == null)
      return false;
    int i = before.length() - 1;
    while (i >= 0 && Character.isWhitespace(before.charAt(i)))
      --i;
    return i < 0 || before.charAt(i) == '.' || before.charAt(i) == '!'
      || before.charAt(i) == '?';
  }


  void request_rewrite(char original, char replacement)
  {
    _pending_rewrite_cursor = _cursor;
    _pending_rewrite_original = original;
    _pending_rewrite_replacement = replacement;
  }

  void cancel_pending_rewrite()
  {
    _pending_rewrite_cursor = -1;
    _pending_rewrite_original = 0;
    _pending_rewrite_replacement = 0;
    if (_handler != null)
      _handler.removeCallbacks(delayed_callback);
  }

  void rewrite_pending_lowercase()
  {
    int target_cursor = _pending_rewrite_cursor;
    char original = _pending_rewrite_original;
    char replacement = _pending_rewrite_replacement;
    _pending_rewrite_cursor = -1;
    _pending_rewrite_original = 0;
    _pending_rewrite_replacement = 0;
    if (!_enabled || _ic == null || target_cursor < 1
        || target_cursor > _cursor)
      return;
    int chars_after_target = _cursor - target_cursor;
    if (chars_after_target > REWRITE_CONTEXT_LIMIT)
      return;
    CharSequence before = _ic.getTextBeforeCursor(
        chars_after_target + 1 + REWRITE_CONTEXT_LIMIT, 0);
    if (before == null)
      return;
    int target_index = before.length() - chars_after_target - 1;
    if (target_index < 0 || before.charAt(target_index) != original
        || TextUtils.getCapsMode(before, target_index, _caps_mode) == 0)
      return;
    String expected = before.subSequence(target_index, before.length()).toString();
    String replacement_text = replacement + expected.substring(1);
    _callback.replace_recent_text(expected, replacement_text);
  }

  void type_one_char(char c)
  {
    _cursor++;
    if (should_refresh_caps_mode_after(c))
      _should_update_caps_mode = true;
    else
      _should_enable_shift = false;
  }

  boolean should_refresh_caps_mode_after(char c)
  {
    return (_caps_mode & TextUtils.CAP_MODE_CHARACTERS) != 0
      || is_trigger_character(c);
  }

  boolean is_trigger_character(char c)
  {
    return Character.isWhitespace(c) || c == '.' || c == '!' || c == '?';
  }
}
