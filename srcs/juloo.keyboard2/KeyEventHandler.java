package juloo.keyboard2;

import android.annotation.SuppressLint;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import java.util.Iterator;
import juloo.keyboard2.autocorrect.Autocorrect;
import juloo.keyboard2.suggestions.Suggestions;
import juloo.keyboard2.suggestions.PersonalizationStore;
import juloo.keyboard2.snippets.SnippetInserter;

public final class KeyEventHandler
  implements Config.IKeyEventHandler,
             ClipboardHistoryService.ClipboardPasteCallback,
             CurrentlyTypedWord.Callback
{
  IReceiver _recv;
  Autocapitalisation _autocap;
  Suggestions _suggestions;
  Config _config;
  CurrentlyTypedWord _typedword;
  /** State of the system modifiers. It is updated whether a modifier is down
      or up and a corresponding key event is sent. */
  Pointers.Modifiers _mods;
  /** Consistent with [_mods]. This is a mutable state rather than computed
      from [_mods] to ensure that the meta state is correct while up and down
      events are sent for the modifier keys. */
  int _meta_state = 0;
  /** Whether to force sending arrow keys to move the cursor when
      [setSelection] could be used instead. */
  boolean _move_cursor_force_fallback = false;
  /** Whether pressing space should automatically commit a correction. */
  boolean _autocorrect_enabled = false;
  /** Remember the action that was handled. This is used by autocorrect. */
  LastAction _last_action = null;
  LastAction _next_last_action = null;
  private DeleteSelection _delete_selection = null;

  private static final class DeleteSelection
  {
    final String textBeforeCursor;
    final int cursor;
    int steps;

    DeleteSelection(String textBeforeCursor_, int cursor_)
    {
      textBeforeCursor = textBeforeCursor_;
      cursor = cursor_;
      steps = 0;
    }
  }

  public KeyEventHandler(IReceiver recv, Suggestions sg)
  {
    _recv = recv;
    Handler handler = recv.getHandler();
    _autocap = new Autocapitalisation(handler,
        this.new Autocapitalisation_callback());
    _mods = Pointers.Modifiers.EMPTY;
    _suggestions = sg;
    _typedword = new CurrentlyTypedWord(handler, this);
  }

  /** Editing just started. */
  public void started(Config conf)
  {
    _config = conf;
    InputConnection ic = _recv.getCurrentInputConnection();
    _autocap.started(conf, ic);
    _typedword.started(conf, ic);
    _suggestions.started();
    _move_cursor_force_fallback =
      conf.editor_config.should_move_cursor_force_fallback;
    _autocorrect_enabled = conf.autocorrect_enabled;
    _last_action = null;
  }

  /** Selection has been updated. */
  public void selection_updated(int oldSelStart, int newSelStart, int newSelEnd)
  {
    _autocap.selection_updated(oldSelStart, newSelStart);
    _typedword.selection_updated(oldSelStart, newSelStart, newSelEnd);
  }

  /** A key is being pressed. There will not necessarily be a corresponding
      [key_up] event. */
  @Override
  public void key_down(KeyValue key, boolean isSwipe)
  {
    if (key == null)
      return;
    // Stop auto capitalisation when pressing some keys
    switch (key.getKind())
    {
      case Modifier:
        switch (key.getModifier())
        {
          case CTRL:
          case ALT:
          case META:
            _autocap.stop();
            break;
        }
        break;
      case Compose_pending:
        _autocap.stop();
        break;
      case Slider:
        // Don't wait for the next key_up and move the cursor right away. This
        // is called after the trigger distance have been travelled.
        handle_slider(key.getSlider(), key.getSliderRepeat(), true);
        break;
      default: break;
    }
  }

  /** A key has been released. */
  @Override
  public void key_up(KeyValue key, Pointers.Modifiers mods, TouchTrace.Entry touch)
  {
    if (key == null)
      return;
    _next_last_action = LastAction.OTHER;
    Pointers.Modifiers old_mods = _mods;
    update_meta_state(mods);
    switch (key.getKind())
    {
      case Char:
        char c = key.getChar();
        if (is_autocorrect_separator(c))
          handle_word_separator(String.valueOf(c));
        else
          send_text(String.valueOf(c), touch);
        break;
      case String: send_text(key.getString(), null); break;
      case Event: _recv.handle_event_key(key.getEvent()); break;
      case Keyevent:
        if (key.getKeyevent() == KeyEvent.KEYCODE_ENTER)
          handle_word_separator("\n");
        else
          send_key_down_up(key.getKeyevent());
        break;
      case Modifier: break;
      case Editing: handle_editing_key(key.getEditing()); break;
      case Compose_pending: _recv.set_compose_pending(true); break;
      case Slider: handle_slider(key.getSlider(), key.getSliderRepeat(), false); break;
      case Macro: evaluate_macro(key.getMacro()); break;
      case Stateful: handle_stateful(key.getStateful()); break;
    }
    update_meta_state(old_mods);
    _last_action = _next_last_action;
  }

  @Override
  public void key_hold(KeyValue key, Pointers.Modifiers mods, int holdCount)
  {
    key_up(key, mods, null);
  }

  @Override
  public void key_cancel(KeyValue key, Pointers.Modifiers mods)
  {
    if (is_delete_words_slider(key))
      cancel_delete_words_selection();
  }

  @Override
  public void mods_changed(Pointers.Modifiers mods)
  {
    update_meta_state(mods);
  }

  @Override
  public void suggestion_entered(String text)
  {
    commit_correction(text, " ");
  }

  @Override
  public void suggestion_swiped_up(String text)
  {
    toggle_learned_word(text);
  }

  @Override
  public void keyboard_swiped_up()
  {
    learn_current_word();
  }

  @Override
  public void keyboard_swiped_down()
  {
    unlearn_current_word();
  }

  void learn_current_word()
  {
    learn_word(_typedword.get());
  }

  void unlearn_current_word()
  {
    unlearn_word(_typedword.get());
  }

  boolean can_change_learning(String word)
  {
    return _config != null
      && _config.suggestions_enabled
      && _config.editor_config.should_use_typing_assistance
      && PersonalizationStore.is_learnable(word);
  }

  void learn_word(String word)
  {
    if (!can_change_learning(word))
      return;
    _config.personalization.record_word(word);
    refresh_learning_feedback(word, Suggestions.LearnFeedback.LEARNED);
  }

  void unlearn_word(String word)
  {
    if (!can_change_learning(word))
      return;
    if (!_config.personalization.unlearn_word(word))
      return;
    refresh_learning_feedback(word, Suggestions.LearnFeedback.FORGOT);
  }

  void toggle_learned_word(String word)
  {
    if (!can_change_learning(word))
      return;
    if (_config.personalization.is_learned(word))
    {
      _config.personalization.unlearn_word(word);
      refresh_learning_feedback(word, Suggestions.LearnFeedback.FORGOT);
    }
    else
    {
      _config.personalization.record_word(word);
      refresh_learning_feedback(word, Suggestions.LearnFeedback.LEARNED);
    }
  }

  void refresh_learning_feedback(String word, Suggestions.LearnFeedback feedback)
  {
    _suggestions.currently_typed_word(_typedword.get(), _typedword.touch_trace());
    _suggestions.show_learn_feedback(word, feedback);
  }

  void commit_correction(String text, String separator)
  {
    learn_committed_word(text);
    String old = _typedword.get();
    int cur_rel = _typedword.cursor_relative();
    replace_surrounding_text(old.length() + cur_rel, -cur_rel,
        text + separator);
    last_replaced_word = old;
    last_replacement_word_len = text.length() + separator.length();
    last_replacement_separator = separator;
    _next_last_action = LastAction.SUGGESTION_ENTERED;
  }

  void learn_committed_word(String word)
  {
    if (_config == null)
      return;
    if (!_config.suggestions_enabled
        || !_config.editor_config.should_use_typing_assistance)
    {
      _config.personalization.reset_context();
      return;
    }
    if (!is_recognized_or_learned_word(word))
      return;
    _config.personalization.record_word(word);
  }

  boolean is_recognized_or_learned_word(String word)
  {
    if (word == null || word.length() == 0)
      return false;
    if (_config.personalization.is_learned(word))
      return true;
    if (_config.current_hunspell != null && _config.current_hunspell.spell(word))
      return true;
    return _config.current_dictionary != null
      && _config.current_dictionary.find(word.toLowerCase(java.util.Locale.ROOT)).found;
  }

  @Override
  public void paste_from_clipboard_pane(String content)
  {
    send_text(content);
  }

  public void snippet_entered(String phrase)
  {
    send_text(phrase);
  }

  @Override
  public void currently_typed_word(String word, TouchTrace touchTrace)
  {
    _suggestions.currently_typed_word(word, touchTrace);
  }

  public void ime_subtype_changed()
  {
    // Refresh the suggestions immediately after dictionary changed.
    _suggestions.currently_typed_word(_typedword.get(), _typedword.touch_trace());
  }

  /** Update [_mods] to be consistent with the [mods], sending key events if
      needed. */
  void update_meta_state(Pointers.Modifiers mods)
  {
    // Released modifiers
    Iterator<KeyValue> it = _mods.diff(mods);
    while (it.hasNext())
      sendMetaKeyForModifier(it.next(), false);
    // Activated modifiers
    it = mods.diff(_mods);
    while (it.hasNext())
      sendMetaKeyForModifier(it.next(), true);
    _mods = mods;
  }

  // private void handleDelKey(int before, int after)
  // {
  //  CharSequence selection = getCurrentInputConnection().getSelectedText(0);

  //  if (selection != null && selection.length() > 0)
  //  getCurrentInputConnection().commitText("", 1);
  //  else
  //  getCurrentInputConnection().deleteSurroundingText(before, after);
  // }

  void sendMetaKey(int eventCode, int meta_flags, boolean down)
  {
    if (down)
    {
      _meta_state = _meta_state | meta_flags;
      send_keyevent(KeyEvent.ACTION_DOWN, eventCode, _meta_state);
    }
    else
    {
      send_keyevent(KeyEvent.ACTION_UP, eventCode, _meta_state);
      _meta_state = _meta_state & ~meta_flags;
    }
  }

  void sendMetaKeyForModifier(KeyValue kv, boolean down)
  {
    switch (kv.getKind())
    {
      case Modifier:
        switch (kv.getModifier())
        {
          case CTRL:
            sendMetaKey(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.META_CTRL_LEFT_ON | KeyEvent.META_CTRL_ON, down);
            break;
          case ALT:
            sendMetaKey(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.META_ALT_LEFT_ON | KeyEvent.META_ALT_ON, down);
            break;
          case SHIFT:
            sendMetaKey(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.META_SHIFT_LEFT_ON | KeyEvent.META_SHIFT_ON, down);
            break;
          case META:
            sendMetaKey(KeyEvent.KEYCODE_META_LEFT, KeyEvent.META_META_LEFT_ON | KeyEvent.META_META_ON, down);
            break;
          default:
            break;
        }
        break;
    }
  }

  void send_key_down_up(int keyCode)
  {
    send_key_down_up(keyCode, _meta_state);
  }

  /** Ignores currently pressed system modifiers. */
  void send_key_down_up(int keyCode, int metaState)
  {
    send_keyevent(KeyEvent.ACTION_DOWN, keyCode, metaState);
    send_keyevent(KeyEvent.ACTION_UP, keyCode, metaState);
  }

  void send_keyevent(int eventAction, int eventCode, int metaState)
  {
    InputConnection conn = _recv.getCurrentInputConnection();
    if (conn == null)
      return;
    conn.sendKeyEvent(new KeyEvent(1, 1, eventAction, eventCode, 0,
          metaState, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
          KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));
    if (eventAction == KeyEvent.ACTION_UP)
    {
      _autocap.event_sent(eventCode, metaState);
      _typedword.event_sent(eventCode, metaState);
    }
  }

  void send_text(String text)
  {
    send_text(text, null);
  }

  void send_text(String text, TouchTrace.Entry touch)
  {
    InputConnection conn = _recv.getCurrentInputConnection();
    if (conn == null)
      return;
    _autocap.typed(text);
    _typedword.typed(text, touch);
    SnippetInserter.insert(conn, text);
  }

  void replace_surrounding_text(int remove_before, int remove_after,
      String new_text)
  {
    InputConnection conn = _recv.getCurrentInputConnection();
    if (conn == null)
      return;
    conn.beginBatchEdit();
    conn.deleteSurroundingText(remove_before, remove_after);
    conn.commitText(new_text, 1);
    conn.endBatchEdit();
  }

  /** See {!InputConnection.performContextMenuAction}. */
  void send_context_menu_action(int id)
  {
    InputConnection conn = _recv.getCurrentInputConnection();
    if (conn == null)
      return;
    conn.performContextMenuAction(id);
  }

  @SuppressLint("InlinedApi")
  void handle_editing_key(KeyValue.Editing ev)
  {
    switch (ev)
    {
      case COPY: send_context_menu_action(android.R.id.copy); break;
      case PASTE: if (!ClipboardHistoryService.paste_current_clip()) send_context_menu_action(android.R.id.paste); break;
      case CUT: send_context_menu_action(android.R.id.cut); break;
      case SELECT_ALL: send_context_menu_action(android.R.id.selectAll); break;
      case SHARE: send_context_menu_action(android.R.id.shareText); break;
      case PASTE_PLAIN: if (!ClipboardHistoryService.paste_current_clip()) send_context_menu_action(android.R.id.pasteAsPlainText); break;
      case UNDO: send_context_menu_action(android.R.id.undo); break;
      case REDO: send_context_menu_action(android.R.id.redo); break;
      case REPLACE: send_context_menu_action(android.R.id.replaceText); break;
      case ASSIST: send_context_menu_action(android.R.id.textAssist); break;
      case AUTOFILL: send_context_menu_action(android.R.id.autofill); break;
      case DELETE_WORD: send_key_down_up(KeyEvent.KEYCODE_DEL, KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON); break;
      case FORWARD_DELETE_WORD: send_key_down_up(KeyEvent.KEYCODE_FORWARD_DEL, KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON); break;
      case SELECTION_CANCEL: cancel_selection(); break;
      case SPACE_BAR: handle_space_bar(); break;
      case BACKSPACE: handle_backspace(); break;
    }
  }

  static ExtractedTextRequest _move_cursor_req = null;

  /** Query the cursor position. The extracted text is empty. Returns [null] if
      the editor doesn't support this operation. */
  ExtractedText get_cursor_pos(InputConnection conn)
  {
    if (_move_cursor_req == null)
    {
      _move_cursor_req = new ExtractedTextRequest();
      _move_cursor_req.hintMaxChars = 0;
    }
    return conn.getExtractedText(_move_cursor_req, 0);
  }

  /** [r] might be negative, in which case the direction is reversed. */
  void handle_slider(KeyValue.Slider s, int r, boolean key_down)
  {
    switch (s)
    {
      case Cursor_left: move_cursor(-r); break;
      case Cursor_right: move_cursor(r); break;
      case Cursor_up: move_cursor_vertical(-r); break;
      case Cursor_down: move_cursor_vertical(r); break;
      case Selection_cursor_left: move_cursor_sel(r, true, key_down); break;
      case Selection_cursor_right: move_cursor_sel(r, false, key_down); break;
      case Delete_words_left: handle_delete_words_slider(r, key_down); break;
    }
  }

  private boolean is_delete_words_slider(KeyValue key)
  {
    return key != null
      && key.getKind() == KeyValue.Kind.Slider
      && key.getSlider() == KeyValue.Slider.Delete_words_left;
  }

  private void handle_delete_words_slider(int r, boolean key_down)
  {
    if (r == 0 && !key_down)
    {
      commit_delete_words_selection();
      return;
    }
    InputConnection conn = _recv.getCurrentInputConnection();
    if (conn == null)
      return;
    if (key_down)
      _delete_selection = null;
    if (!ensure_delete_words_selection(conn))
      return;
    _delete_selection.steps = Math.max(0, _delete_selection.steps + r);
    apply_delete_words_selection(conn);
  }

  private boolean ensure_delete_words_selection(InputConnection conn)
  {
    if (_delete_selection != null)
      return true;
    ExtractedTextRequest req = new ExtractedTextRequest();
    req.hintMaxChars = 4096;
    ExtractedText et = conn.getExtractedText(req, 0);
    if (et == null || et.text == null || !can_set_selection(conn))
      return false;
    int cursor = Math.max(et.selectionStart, et.selectionEnd);
    int local_cursor = cursor - et.startOffset;
    if (local_cursor < 0)
      local_cursor = 0;
    if (local_cursor > et.text.length())
      local_cursor = et.text.length();
    _delete_selection = new DeleteSelection(
        et.text.subSequence(0, local_cursor).toString(), cursor);
    return true;
  }

  private void apply_delete_words_selection(InputConnection conn)
  {
    if (_delete_selection == null)
      return;
    int start = delete_start_for_steps(_delete_selection.textBeforeCursor,
        _delete_selection.steps);
    int selection_start = _delete_selection.cursor
      - (_delete_selection.textBeforeCursor.length() - start);
    if (conn.setSelection(selection_start, _delete_selection.cursor))
      _recv.selection_state_changed(_delete_selection.steps > 0);
  }

  private void commit_delete_words_selection()
  {
    DeleteSelection sel = _delete_selection;
    _delete_selection = null;
    if (sel == null)
      return;
    InputConnection conn = _recv.getCurrentInputConnection();
    if (conn == null)
      return;
    int start = delete_start_for_steps(sel.textBeforeCursor, sel.steps);
    int selection_start = sel.cursor - (sel.textBeforeCursor.length() - start);
    if (selection_start == sel.cursor)
    {
      conn.setSelection(sel.cursor, sel.cursor);
      _recv.selection_state_changed(false);
      return;
    }
    conn.beginBatchEdit();
    if (conn.setSelection(selection_start, sel.cursor))
    {
      conn.commitText("", 1);
      _autocap.event_sent(KeyEvent.KEYCODE_DEL, 0);
      _typedword.event_sent(KeyEvent.KEYCODE_DEL, 0);
    }
    conn.endBatchEdit();
    _recv.selection_state_changed(false);
  }

  private void cancel_delete_words_selection()
  {
    DeleteSelection sel = _delete_selection;
    _delete_selection = null;
    if (sel == null)
      return;
    InputConnection conn = _recv.getCurrentInputConnection();
    if (conn != null)
      conn.setSelection(sel.cursor, sel.cursor);
    _recv.selection_state_changed(false);
  }

  static int delete_start_for_steps(String text, int steps)
  {
    int pos = text.length();
    for (int i = 0; i < steps; ++i)
    {
      int next = previous_delete_start(text, pos);
      if (next == pos)
        break;
      pos = next;
    }
    return pos;
  }

  private static int previous_delete_start(String text, int pos)
  {
    int p = Math.max(0, Math.min(pos, text.length()));
    while (p > 0 && Character.isWhitespace(text.charAt(p - 1)))
      --p;
    if (p == 0)
      return 0;
    if (is_word_char(text.charAt(p - 1)))
      while (p > 0 && is_word_char(text.charAt(p - 1)))
        --p;
    else
      while (p > 0
          && !Character.isWhitespace(text.charAt(p - 1))
          && !is_word_char(text.charAt(p - 1)))
        --p;
    return p;
  }

  private static boolean is_word_char(char c)
  {
    return Character.isLetterOrDigit(c) || c == '\'' || c == '’';
  }

  void handle_stateful(KeyValue.Stateful st)
  {
    switch (st)
    {
      case Complete_first:
      case Complete_second:
      case Complete_third:
      case Complete_emoji:
        suggestion_entered(st.toString());
        break;
    }
  }

  /** Move the cursor right or left, if possible without sending key events.
      Unlike arrow keys, the selection is not removed even if shift is not on.
      Falls back to sending arrow keys events if the editor do not support
      moving the cursor or a modifier other than shift is pressed. */
  void move_cursor(int d)
  {
    InputConnection conn = _recv.getCurrentInputConnection();
    if (conn == null)
      return;
    ExtractedText et = get_cursor_pos(conn);
    if (et != null && can_set_selection(conn))
    {
      int sel_start = et.selectionStart;
      int sel_end = et.selectionEnd;
      // Continue expanding the selection even if shift is not pressed
      if (sel_end != sel_start)
      {
        sel_end += d;
        if (sel_end == sel_start) // Avoid making the selection empty
          sel_end += d;
      }
      else
      {
        sel_end += d;
        // Leave 'sel_start' where it is if shift is pressed
        if ((_meta_state & KeyEvent.META_SHIFT_ON) == 0)
          sel_start = sel_end;
      }
      if (conn.setSelection(sel_start, sel_end))
        return; // Fallback to sending key events if [setSelection] failed
    }
    move_cursor_fallback(d);
  }

  /** Move one of the two side of a selection. If [sel_left] is true, the left
      position is moved, otherwise the right position is moved. */
  void move_cursor_sel(int d, boolean sel_left, boolean key_down)
  {
    InputConnection conn = _recv.getCurrentInputConnection();
    if (conn == null)
      return;
    ExtractedText et = get_cursor_pos(conn);
    if (et != null && can_set_selection(conn))
    {
      int sel_start = et.selectionStart;
      int sel_end = et.selectionEnd;
      // Reorder the selection when the slider has just been pressed. The
      // selection might have been reversed if one end crossed the other end
      // with a previous slider.
      if (key_down && sel_start > sel_end)
      {
        sel_start = et.selectionEnd;
        sel_end = et.selectionStart;
      }
      do
      {
        if (sel_left)
          sel_start += d;
        else
          sel_end += d;
        // Move the cursor twice if moving it once would make the selection
        // empty and stop selection mode.
      } while (sel_start == sel_end);
      if (conn.setSelection(sel_start, sel_end))
        return; // Fallback to sending key events if [setSelection] failed
    }
    move_cursor_fallback(d);
  }

  /** Returns whether the selection can be set using [conn.setSelection()].
      This can happen on Termux or when system modifiers are activated for
      example. */
  boolean can_set_selection(InputConnection conn)
  {
    final int system_mods =
      KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_ON | KeyEvent.META_META_ON;
    return !_move_cursor_force_fallback && (_meta_state & system_mods) == 0;
  }

  void move_cursor_fallback(int d)
  {
    if (d < 0)
      send_key_down_up_repeat(KeyEvent.KEYCODE_DPAD_LEFT, -d);
    else
      send_key_down_up_repeat(KeyEvent.KEYCODE_DPAD_RIGHT, d);
  }

  /** Move the cursor up and down. This sends UP and DOWN key events that might
      make the focus exit the text box. */
  void move_cursor_vertical(int d)
  {
    if (d < 0)
      send_key_down_up_repeat(KeyEvent.KEYCODE_DPAD_UP, -d);
    else
      send_key_down_up_repeat(KeyEvent.KEYCODE_DPAD_DOWN, d);
  }

  void evaluate_macro(KeyValue[] keys)
  {
    if (keys.length == 0)
      return;
    // Ignore modifiers that are activated at the time the macro is evaluated
    mods_changed(Pointers.Modifiers.EMPTY);
    evaluate_macro_loop(keys, 0, Pointers.Modifiers.EMPTY, _autocap.pause());
  }

  /** Evaluate the macro asynchronously to make sure event are processed in the
      right order. */
  void evaluate_macro_loop(final KeyValue[] keys, int i, Pointers.Modifiers mods, final boolean autocap_paused)
  {
    boolean should_delay = false;
    KeyValue kv = KeyModifier.modify_no_modmap(keys[i], mods);
    if (kv != null)
    {
      if (kv.hasFlagsAny(KeyValue.FLAG_LATCH))
      {
        // Non-special latchable keys clear latched modifiers
        if (!kv.hasFlagsAny(KeyValue.FLAG_SPECIAL))
          mods = Pointers.Modifiers.EMPTY;
        mods = mods.with_extra_mod(kv);
      }
      else
      {
        key_down(kv, false);
        key_up(kv, mods, null);
        mods = Pointers.Modifiers.EMPTY;
      }
      should_delay = wait_after_macro_key(kv);
    }
    i++;
    if (i >= keys.length) // Stop looping
    {
      _autocap.unpause(autocap_paused);
    }
    else if (should_delay)
    {
      // Add a delay before sending the next key to avoid race conditions
      // causing keys to be handled in the wrong order. Notably, KeyEvent keys
      // handling is scheduled differently than the other edit functions.
      final int i_ = i;
      final Pointers.Modifiers mods_ = mods;
      _recv.getHandler().postDelayed(new Runnable() {
        public void run()
        {
          evaluate_macro_loop(keys, i_, mods_, autocap_paused);
        }
      }, 1000/30);
    }
    else
      evaluate_macro_loop(keys, i, mods, autocap_paused);
  }

  boolean wait_after_macro_key(KeyValue kv)
  {
    switch (kv.getKind())
    {
      case Keyevent:
      case Editing:
      case Event:
        return true;
      case Slider:
        return _move_cursor_force_fallback;
      default:
        return false;
    }
  }

  /** Repeat calls to [send_key_down_up]. */
  void send_key_down_up_repeat(int event_code, int repeat)
  {
    while (repeat-- > 0)
      send_key_down_up(event_code);
  }

  void cancel_selection()
  {
    InputConnection conn = _recv.getCurrentInputConnection();
    if (conn == null)
      return;
    ExtractedText et = get_cursor_pos(conn);
    if (et == null) return;
    final int curs = et.selectionStart;
    // Notify the receiver as Android's [onUpdateSelection] is not triggered.
    if (conn.setSelection(curs, curs))
      _recv.selection_state_changed(false);
  }

  /** The word that was replaced by a suggestion when the last action was to
      enter a suggestion (with the space bar or the candidates view) or [null]
      otherwise. */
  String last_replaced_word = null;
  /** Length of the correction text plus separator before the cursor that
      should be replaced by Backspace. */
  int last_replacement_word_len = 0;
  String last_replacement_separator = " ";

  boolean is_autocorrect_separator(char c)
  {
    switch (c)
    {
      case '.':
      case ',':
      case '!':
      case '?':
      case ';':
      case ':':
        return true;
      default:
        return false;
    }
  }

  boolean should_try_autocorrect()
  {
    return _autocorrect_enabled
      && _config != null
      && _config.editor_config.should_use_typing_assistance
      && !_typedword.is_selection_not_empty()
      && _typedword.cursor_relative() == 0;
  }

  boolean should_commit_typed_word()
  {
    return !_typedword.is_selection_not_empty()
      && _typedword.cursor_relative() == 0;
  }

  void handle_word_separator(String separator)
  {
    if (should_try_autocorrect())
    {
      String correction = Autocorrect.correction(_config.current_hunspell,
          _config.current_dictionary, _typedword.get(), _typedword.touch_trace(),
          _config.current_layout_geometry);
      if (correction != null)
      {
        commit_correction(correction, separator);
        return;
      }
    }
    if (should_commit_typed_word())
      learn_committed_word(_typedword.get());
    send_text(separator);
  }

  /** Implement autocorrect when enabled in the settings. */
  void handle_space_bar()
  {
    handle_word_separator(" ");
  }

  void send_backspace()
  {
    InputConnection conn = _recv.getCurrentInputConnection();
    if (conn != null)
    {
      conn.beginBatchEdit();
      try
      {
        conn.finishComposingText();
        CharSequence selection = conn.getSelectedText(0);
        if (selection != null && selection.length() > 0)
        {
          if (conn.commitText("", 1))
          {
            record_backspace();
            return;
          }
        }
        else
        {
          String before = extracted_text_snapshot(conn);
          if (delete_previous_codepoint(conn))
          {
            String after = extracted_text_snapshot(conn);
            if (before == null || after == null || !before.equals(after))
            {
              record_backspace();
              return;
            }
          }
        }
      }
      finally
      {
        conn.endBatchEdit();
      }
    }
    send_key_down_up(KeyEvent.KEYCODE_DEL);
  }

  private String extracted_text_snapshot(InputConnection conn)
  {
    ExtractedTextRequest req = new ExtractedTextRequest();
    ExtractedText et = conn.getExtractedText(req, 0);
    if (et == null || et.text == null)
      return null;
    return et.startOffset + ":" + et.selectionStart + ":" + et.selectionEnd
      + ":" + et.text.toString();
  }

  private boolean delete_previous_codepoint(InputConnection conn)
  {
    if (VERSION.SDK_INT >= VERSION_CODES.N)
      return conn.deleteSurroundingTextInCodePoints(1, 0)
        || conn.deleteSurroundingText(1, 0);
    if (delete_previous_codepoint_if_available(conn))
      return true;
    return conn.deleteSurroundingText(1, 0);
  }

  private boolean delete_previous_codepoint_if_available(InputConnection conn)
  {
    try
    {
      java.lang.reflect.Method method = conn.getClass().getMethod(
          "deleteSurroundingTextInCodePoints", int.class, int.class);
      return (Boolean)method.invoke(conn, 1, 0);
    }
    catch (Exception _e) { return false; }
  }

  private void record_backspace()
  {
    _autocap.event_sent(KeyEvent.KEYCODE_DEL, 0);
    _typedword.event_sent(KeyEvent.KEYCODE_DEL, 0);
  }

  /** Undo the last autocorrect. */
  void handle_backspace()
  {
    if (_last_action == LastAction.SUGGESTION_ENTERED
        && last_replaced_word != null)
    {
      replace_surrounding_text(last_replacement_word_len, 0,
          last_replaced_word + last_replacement_separator);
      last_replaced_word = null;
    }
    else
      send_backspace();
  }

  public static interface IReceiver extends Suggestions.Callback
  {
    public void handle_event_key(KeyValue.Event ev);
    public void set_shift_state(boolean state, boolean lock);
    public void set_compose_pending(boolean pending);
    public void selection_state_changed(boolean selection_is_ongoing);
    public InputConnection getCurrentInputConnection();
    public Handler getHandler();
  }

  class Autocapitalisation_callback implements Autocapitalisation.Callback
  {
    @Override
    public void update_shift_state(boolean should_enable, boolean should_disable)
    {
      if (should_enable)
        _recv.set_shift_state(true, false);
      else if (should_disable)
        _recv.set_shift_state(false, false);
    }
  }

  public static enum LastAction
  {
    SUGGESTION_ENTERED,
    OTHER
  }
}
