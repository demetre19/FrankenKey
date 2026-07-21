package juloo.keyboard2;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import java.util.Iterator;
import juloo.keyboard2.suggestions.Decoder;
import juloo.keyboard2.suggestions.PersonalizationStore;
import juloo.keyboard2.suggestions.SharedDecoder;
import juloo.keyboard2.snippets.SnippetInserter;

public final class KeyEventHandler
  implements Config.IKeyEventHandler,
             ClipboardHistoryService.ClipboardPasteCallback,
             CurrentlyTypedWord.Callback
{
  IReceiver _recv;
  Autocapitalisation _autocap;
  SharedDecoder _decoder;
  Config _config;
  long _decoder_session = 0;
  Decoder.RequestKey _current_request_key = null;
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
  private PendingReplacement _pending_replacement = null;
  private ManualCorrection _manual_correction = null;
  private boolean _preserve_manual_correction_transition = false;
  private PendingAutocorrectBoundary _pending_autocorrect_boundary = null;
  private boolean _preserve_autocorrect_boundary_transition = false;
  private DeleteSelection _delete_selection = null;
  private long _backspace_fallback_generation = 0;
  private static final long BACKSPACE_FALLBACK_DELAY_MS = 24;
  private static final int DELETE_WORDS_CONTEXT_LIMIT = 4096;
  private static final int DELETE_WORDS_CURSOR_LOCAL = -1;
  private static final int DELETE_WORDS_TRACKED_TERMINAL = -2;

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

  private static final class PendingAutocorrectBoundary
  {
    final long sessionEpoch;
    final Decoder.RequestKey key;
    final SharedDecoder.CommitToken literalToken;
    final String source;
    final String separator;
    final InputConnection connection;
    final int cursor;
    final int correctionOffset;
    final boolean mayLearnSourceOnUndo;

    PendingAutocorrectBoundary(long session_epoch_, Decoder.RequestKey key_,
        SharedDecoder.CommitToken literal_token_, String source_,
        String separator_, InputConnection connection_, int cursor_,
        int correction_offset_, boolean may_learn_source_on_undo_)
    {
      sessionEpoch = session_epoch_;
      key = key_;
      literalToken = literal_token_;
      source = source_;
      separator = separator_;
      connection = connection_;
      cursor = cursor_;
      correctionOffset = correction_offset_;
      mayLearnSourceOnUndo = may_learn_source_on_undo_;
    }
  }

  private static final class PendingReplacement
  {
    final SharedDecoder.CommitToken token;
    final SharedDecoder.CommitToken undoToken;
    final String source;
    final String target;
    final String separator;
    final InputConnection connection;
    final int cursor;
    final long typedWordRevision;
    final boolean learnSourceOnUndo;

    PendingReplacement(SharedDecoder.CommitToken token_,
        SharedDecoder.CommitToken undo_token_, String source_,
        String target_, String separator_, InputConnection connection_,
        int cursor_, long typed_word_revision_, boolean learn_source_on_undo_)
    {
      token = token_;
      undoToken = undo_token_;
      source = source_;
      target = target_;
      separator = separator_;
      connection = connection_;
      cursor = cursor_;
      typedWordRevision = typed_word_revision_;
      learnSourceOnUndo = learn_source_on_undo_;
    }
  }

  private static final class ManualCorrection
  {
    final String source;
    final InputConnection connection;
    final int boundaryStart;

    ManualCorrection(String source_, InputConnection connection_,
        int boundary_start_)
    {
      source = source_;
      connection = connection_;
      boundaryStart = boundary_start_;
    }
  }

  private static final class EditorWord
  {
    final String word;
    final int boundaryStart;
    final boolean hasSelection;

    EditorWord(String word_, int boundary_start_, boolean has_selection_)
    {
      word = word_;
      boundaryStart = boundary_start_;
      hasSelection = has_selection_;
    }
  }

  public KeyEventHandler(IReceiver recv, SharedDecoder decoder)
  {
    _recv = recv;
    Handler handler = recv.getHandler();
    _autocap = new Autocapitalisation(handler,
        this.new Autocapitalisation_callback());
    _mods = Pointers.Modifiers.EMPTY;
    _decoder = decoder;
    _typedword = new CurrentlyTypedWord(handler, this);
  }

  /** Editing just started. */
  public void started(Config conf, long decoder_session)
  {
    cancel_pending_backspace_fallback();
    _config = conf;
    _decoder_session = decoder_session;
    _current_request_key = null;
    _pending_replacement = null;
    _manual_correction = null;
    _preserve_manual_correction_transition = false;
    _pending_autocorrect_boundary = null;
    _preserve_autocorrect_boundary_transition = false;
    InputConnection ic = _recv.getCurrentInputConnection();
    _autocap.started(conf, ic);
    _typedword.started(conf, ic);
    _move_cursor_force_fallback =
      conf.editor_config.should_move_cursor_force_fallback;
    _autocorrect_enabled = conf.autocorrect_enabled;
    _delete_selection = null;
  }

  public void finished()
  {
    cancel_pending_backspace_fallback();
    commit_pending_replacement();
    _manual_correction = null;
    _delete_selection = null;
    _autocap.finished();
    _typedword.finished();
    if (_decoder_session != 0)
      _decoder.finish_session(_decoder_session);
    _decoder_session = 0;
    _current_request_key = null;
  }

  private boolean pending_autocorrect_boundary_matches_editor(
      PendingAutocorrectBoundary boundary)
  {
    if (boundary == null || boundary.cursor < 0
        || boundary.connection != _recv.getCurrentInputConnection())
      return false;
    ExtractedText actual = get_cursor_pos(boundary.connection);
    if (actual == null || actual.selectionStart != boundary.cursor
        || actual.selectionEnd != boundary.cursor)
      return false;
    String expected = boundary.source + boundary.separator;
    CharSequence suffix = boundary.connection.getTextBeforeCursor(
        expected.length(), 0);
    return suffix != null && expected.contentEquals(suffix);
  }

  /** Selection has been updated. */
  public void selection_updated(int oldSelStart, int newSelStart, int newSelEnd)
  {
    cancel_pending_backspace_fallback();
    PendingAutocorrectBoundary boundary = _pending_autocorrect_boundary;
    if (boundary != null)
    {
      boolean exact_boundary = boundary.connection
        == _recv.getCurrentInputConnection()
        && boundary.cursor >= 0
        && newSelStart == newSelEnd
        && newSelStart == boundary.cursor;
      boolean live_boundary =
        pending_autocorrect_boundary_matches_editor(boundary);
      if (live_boundary)
        return;
      if (!exact_boundary)
        commit_pending_autocorrect_boundary();
    }

    PendingReplacement replacement = _pending_replacement;
    if (replacement != null
        && (replacement.cursor < 0 || newSelStart != newSelEnd
          || newSelStart != replacement.cursor))
    {
      int replacement_start = replacement.cursor
        - replacement.target.length() - replacement.separator.length();
      int selection_start = Math.min(newSelStart, newSelEnd);
      int selection_end = Math.max(newSelStart, newSelEnd);
      boolean moved_into_replacement = replacement.cursor >= 0
        && oldSelStart == replacement.cursor
        && selection_start >= replacement_start
        && selection_end <= replacement.cursor
        && selection_end >= replacement_start;
      if (moved_into_replacement
          || !pending_replacement_matches_editor(replacement))
        commit_pending_replacement();
    }

    if (newSelStart != newSelEnd)
    {
      InputConnection conn = _recv.getCurrentInputConnection();
      EditorWord selected = editor_word(conn);
      if (_manual_correction != null
          && (selected == null || conn != _manual_correction.connection
            || selected.boundaryStart != _manual_correction.boundaryStart))
        _manual_correction = null;
      capture_manual_correction_source();
    }

    _autocap.selection_updated(oldSelStart, newSelStart);
    _typedword.selection_updated(oldSelStart, newSelStart, newSelEnd);

    if (_manual_correction != null && newSelStart == newSelEnd)
    {
      EditorWord current = editor_word(_manual_correction.connection);
      if (current == null || current.hasSelection
          || current.boundaryStart != _manual_correction.boundaryStart)
        _manual_correction = null;
    }
  }

  /** A key is being pressed. There will not necessarily be a corresponding
      [key_up] event. */
  @Override
  public void key_down(KeyValue key, boolean isSwipe)
  {
    if (key == null)
      return;
    if (!is_backspace_action(key))
    {
      cancel_pending_backspace_fallback();
      commit_pending_replacement();
    }
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
    if (!is_backspace_action(key))
      commit_pending_replacement();
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
      case Event:
        clear_manual_correction();
        _recv.handle_event_key(key.getEvent());
        break;
      case Keyevent:
        if (key.getKeyevent() == KeyEvent.KEYCODE_ENTER)
          handle_word_separator("\n");
        else
        {
          clear_manual_correction();
          send_key_down_up(key.getKeyevent());
        }
        break;
      case Modifier: break;
      case Editing: handle_editing_key(key.getEditing()); break;
      case Compose_pending:
        clear_manual_correction();
        _recv.set_compose_pending(true);
        break;
      case Slider: handle_slider(key.getSlider(), key.getSliderRepeat(), false); break;
      case Macro:
        clear_manual_correction();
        evaluate_macro(key.getMacro());
        break;
      case Stateful: handle_stateful(key.getStateful()); break;
    }
    update_meta_state(old_mods);
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

  private boolean is_backspace_action(KeyValue key)
  {
    return key != null && key.getKind() == KeyValue.Kind.Editing
      && key.getEditing() == KeyValue.Editing.BACKSPACE;
  }

  @Override
  public void mods_changed(Pointers.Modifiers mods)
  {
    update_meta_state(mods);
  }

  @Override
  public void suggestion_entered(Decoder.RequestKey key, String text)
  {
    cancel_pending_backspace_fallback();
    commit_pending_replacement();
    if (!_decoder.is_current(key))
      return;
    Decoder.Result result = _decoder.current_result(key);
    CurrentlyTypedWord.Snapshot snapshot = _typedword.snapshot();
    String corrected_from = plausible_correction_source(snapshot.word, text);
    if (corrected_from == null)
      corrected_from = manual_correction_source(snapshot, text);
    String undo_corrected_from = manual_correction_source(
        snapshot, snapshot.word);
    boolean should_record = should_use_personalization();
    SharedDecoder.CommitToken token = should_record
      ? _decoder.prepare_commit(_decoder_session, key, text, corrected_from)
      : null;
    SharedDecoder.CommitToken undo_token =
      should_record && !text.equals(snapshot.word)
      ? _decoder.prepare_commit(_decoder_session, key, snapshot.word,
          undo_corrected_from)
      : null;
    boolean learn_source_on_undo = undo_corrected_from == null
      && should_learn_source_on_undo(result == null ? null : result.literal);
    if (!commit_correction(text, " ", false))
      return;
    if (text.equals(snapshot.word))
      commit_prepared(token);
    else
      stage_pending_replacement(token, undo_token, snapshot.word, text, " ",
          learn_source_on_undo);
  }

  @Override
  public void suggestion_swiped_up(Decoder.RequestKey key, String text)
  {
    commit_pending_replacement();
    clear_manual_correction();
    if (_decoder.is_current(key))
      toggle_learned_word(key, text);
  }

  @Override
  public void keyboard_swiped_up()
  {
    commit_pending_replacement();
    clear_manual_correction();
    learn_current_word();
  }

  @Override
  public void keyboard_swiped_down()
  {
    commit_pending_replacement();
    clear_manual_correction();
    unlearn_current_word();
  }

  void learn_current_word()
  {
    learn_word(_typedword.get());
  }

  void unlearn_current_word()
  {
    confirm_unlearn_word(_decoder.current_key(), _typedword.get());
  }

  boolean can_change_learning(String word)
  {
    return should_use_personalization()
      && _decoder_session != 0
      && _config.suggestions_enabled
      && _config.editor_config.should_use_typing_assistance
      && PersonalizationStore.is_learnable(word);
  }

  void learn_word(String word)
  {
    if (can_change_learning(word))
      _decoder.learn_word(_decoder_session, word);
  }

  void confirm_unlearn_word(final Decoder.RequestKey key, final String word)
  {
    if (key == null || !_decoder.is_current(key) || !can_change_learning(word))
      return;
    final long session = _decoder_session;
    _recv.confirm_unlearn_word(word, new Runnable()
        {
          @Override
          public void run()
          {
            if (_decoder_session == session && _decoder.is_current(key)
                && can_change_learning(word))
              _decoder.unlearn_word(session, key, word);
          }
        });
  }

  void toggle_learned_word(Decoder.RequestKey key, String word)
  {
    if (!can_change_learning(word))
      return;
    Decoder.Result result = _decoder.current_result(key);
    boolean learned = false;
    if (result != null)
      for (Decoder.Candidate candidate : result.words())
        if (candidate.surface.equalsIgnoreCase(word)
            || candidate.canonical.equals(Decoder.normalize(word)))
        {
          learned = candidate.learned;
          break;
        }
    if (learned)
      confirm_unlearn_word(key, word);
    else
      _decoder.learn_word(_decoder_session, word);
  }

  private boolean should_use_personalization()
  {
    return _config != null
      && _config.editor_config.should_use_personalization;
  }

  private int correction_offset(InputConnection conn, int remove_before)
  {
    ExtractedText before = get_cursor_pos(conn);
    if (before == null || before.startOffset < 0
        || before.selectionStart != before.selectionEnd
        || before.selectionStart < remove_before)
      return -1;
    long absolute_offset = (long)before.startOffset
      + before.selectionStart - remove_before;
    return absolute_offset <= Integer.MAX_VALUE ? (int)absolute_offset : -1;
  }

  private void report_editor_correction(InputConnection conn, int offset,
      String old_text, String new_text)
  {
    if (offset < 0 || conn != _recv.getCurrentInputConnection())
      return;
    try
    {
      conn.commitCorrection(new CorrectionInfo(offset, old_text, new_text));
    }
    catch (RuntimeException ignored)
    {
      // Editor feedback is best-effort; the text replacement already succeeded.
    }
  }

  boolean commit_correction(String text, String separator,
      boolean report_editor_correction)
  {
    String old = _typedword.get();
    int cur_rel = _typedword.cursor_relative();
    int remove_before = old.length() + cur_rel;
    String replacement = text + separator;
    boolean termux_raw_events = uses_termux_raw_events();
    InputConnection conn = _recv.getCurrentInputConnection();
    if (conn == null)
      return false;

    int correction_offset = report_editor_correction
      && !termux_raw_events && !old.equals(text)
      ? correction_offset(conn, remove_before) : -1;

    boolean replaced = termux_raw_events
      ? cur_rel == 0 && replace_termux_suffix(old, replacement)
      : replace_surrounding_text(conn, remove_before, -cur_rel, replacement);
    if (!replaced)
      return false;
    _autocap.text_replaced(remove_before, replacement);
    _typedword.typed(separator);
    report_editor_correction(conn, correction_offset, old, text);
    return true;
  }

  @Override
  public void paste_from_clipboard_pane(String content)
  {
    commit_pending_replacement();
    clear_manual_correction();
    send_text(content);
  }

  @Override
  public void paste_image_from_clipboard_pane(String uri, String mimeType,
      String description)
  {
    commit_pending_replacement();
    clear_manual_correction();
    InputConnection conn = _recv.getCurrentInputConnection();
    if (conn == null)
      return;
    GifInserter.insertImage(null, conn, _recv.getCurrentInputEditorInfo(),
        Uri.parse(uri), mimeType, description);
  }

  public void snippet_entered(String phrase)
  {
    commit_pending_replacement();
    clear_manual_correction();
    send_text(phrase);
  }

  @Override
  public void currently_typed_word(CurrentlyTypedWord.Snapshot snapshot)
  {
    boolean empty_boundary = snapshot.word.length() == 0
      && !snapshot.hasSelection && snapshot.cursorRelative == 0;
    if ((_preserve_autocorrect_boundary_transition
          || pending_autocorrect_boundary_matches_editor(
            _pending_autocorrect_boundary))
        && empty_boundary)
      return;
    if (_pending_autocorrect_boundary != null)
      commit_pending_autocorrect_boundary();

    if (snapshot.word.length() == 0 && !snapshot.hasSelection
        && !_preserve_manual_correction_transition)
      clear_manual_correction();
    if (_decoder_session == 0 || _config == null
        || !_config.editor_config.should_use_typing_assistance
        || (!_config.suggestions_enabled && !_config.autocorrect_enabled))
    {
      if (_decoder_session != 0)
        _decoder.invalidate(_decoder_session);
      _current_request_key = null;
      return;
    }
    _current_request_key = _decoder.request(_decoder_session, snapshot);
  }

  @Override
  public void typing_assistance_data_cleared()
  {
    _pending_replacement = null;
    _pending_autocorrect_boundary = null;
    _preserve_autocorrect_boundary_transition = false;
    clear_manual_correction();
    _decoder.clear_personalization(_decoder_session);
  }

  private void clear_manual_correction()
  {
    _manual_correction = null;
  }

  private EditorWord editor_word(InputConnection conn)
  {
    if (conn == null || conn != _recv.getCurrentInputConnection())
      return null;
    ExtractedTextRequest req = new ExtractedTextRequest();
    req.hintMaxChars = 4096;
    ExtractedText et = conn.getExtractedText(req, 0);
    if (et == null || et.text == null)
      return null;
    String text = et.text.toString();
    int selection_start = Math.min(et.selectionStart, et.selectionEnd);
    int selection_end = Math.max(et.selectionStart, et.selectionEnd);
    int local_start = selection_start - et.startOffset;
    int local_end = selection_end - et.startOffset;
    if (local_start < 0 || local_end < local_start || local_end > text.length())
      return null;
    for (int i = local_start; i < local_end;)
    {
      int cp = text.codePointAt(i);
      if (!CurrentlyTypedWord.is_word_char(cp))
        return null;
      i += Character.charCount(cp);
    }
    int word_start = local_start;
    while (word_start > 0)
    {
      int cp = text.codePointBefore(word_start);
      if (!CurrentlyTypedWord.is_word_char(cp))
        break;
      word_start -= Character.charCount(cp);
    }
    int word_end = local_end;
    while (word_end < text.length())
    {
      int cp = text.codePointAt(word_end);
      if (!CurrentlyTypedWord.is_word_char(cp))
        break;
      word_end += Character.charCount(cp);
    }
    if (word_start == word_end)
      return null;
    return new EditorWord(text.substring(word_start, word_end),
        et.startOffset + word_start, selection_start != selection_end);
  }

  private void capture_manual_correction_source()
  {
    if (_manual_correction != null)
      return;
    InputConnection conn = _recv.getCurrentInputConnection();
    EditorWord current = editor_word(conn);
    if (current == null || current.word.length() == 0)
      return;
    _manual_correction = new ManualCorrection(current.word, conn,
        current.boundaryStart);
  }

  private String plausible_correction_source(String source, String target)
  {
    return source != null && target != null && !source.equals(target)
      && PersonalizationStore.is_plausible_correction(source, target)
      ? source : null;
  }

  private boolean manual_target_matches_snapshot(
      CurrentlyTypedWord.Snapshot snapshot)
  {
    if (_manual_correction == null || snapshot == null || snapshot.hasSelection
        || snapshot.cursorRelative != 0
        || _manual_correction.connection != _recv.getCurrentInputConnection())
      return false;
    EditorWord current = editor_word(_manual_correction.connection);
    return current != null && !current.hasSelection
      && current.boundaryStart == _manual_correction.boundaryStart
      && current.word.equals(snapshot.word);
  }

  private String manual_correction_source(CurrentlyTypedWord.Snapshot snapshot,
      String target)
  {
    if (!manual_target_matches_snapshot(snapshot)
        || !target.equals(snapshot.word))
      return null;
    return plausible_correction_source(_manual_correction.source, target);
  }

  private void commit_prepared(SharedDecoder.CommitToken token)
  {
    if (token != null)
      _decoder.commit_prepared(token);
  }

  private void advance_past_pending_autocorrect_request(
      PendingAutocorrectBoundary pending)
  {
    if (pending == null || pending.sessionEpoch != _decoder_session)
      return;
    if (_decoder.is_current(pending.key))
      _current_request_key = _decoder.request(
          pending.sessionEpoch, _typedword.snapshot());
    else if (pending.key.equals(_current_request_key))
      _current_request_key = null;
  }

  private void commit_pending_autocorrect_boundary()
  {
    PendingAutocorrectBoundary pending = _pending_autocorrect_boundary;
    _pending_autocorrect_boundary = null;
    _preserve_autocorrect_boundary_transition = false;
    if (pending == null)
      return;
    advance_past_pending_autocorrect_request(pending);
    commit_prepared(pending.literalToken);
  }

  private void commit_pending_candidate_replacement()
  {
    PendingReplacement pending = _pending_replacement;
    _pending_replacement = null;
    if (pending != null)
      commit_prepared(pending.token);
  }

  private void commit_pending_replacement()
  {
    commit_pending_autocorrect_boundary();
    commit_pending_candidate_replacement();
  }

  private boolean should_learn_source_on_undo(Decoder.Candidate literal)
  {
    return literal != null && !literal.recognized && !literal.learned
      && can_change_learning(literal.surface);
  }

  private void stage_pending_replacement(SharedDecoder.CommitToken token,
      SharedDecoder.CommitToken undoToken, String source, String target,
      String separator, boolean learnSourceOnUndo)
  {
    InputConnection conn = _recv.getCurrentInputConnection();
    ExtractedText et = conn == null ? null : get_cursor_pos(conn);
    int cursor = et != null && et.selectionStart == et.selectionEnd
      ? et.selectionStart : -1;
    _pending_replacement = new PendingReplacement(token, undoToken, source,
        target, separator, conn, cursor, _typedword.snapshot().revision,
        learnSourceOnUndo);
  }

  private boolean pending_cursor_matches(PendingReplacement pending)
  {
    InputConnection conn = _recv.getCurrentInputConnection();
    if (pending == null || pending.cursor < 0 || conn == null
        || conn != pending.connection)
      return false;
    ExtractedText et = get_cursor_pos(conn);
    return et != null && et.selectionStart == pending.cursor
      && et.selectionEnd == pending.cursor;
  }

  private boolean pending_replacement_matches_editor(
      PendingReplacement pending)
  {
    if (!pending_cursor_matches(pending))
      return false;
    String expected = pending.target + pending.separator;
    CharSequence suffix = pending.connection.getTextBeforeCursor(
        expected.length(), 0);
    return suffix != null && expected.contentEquals(suffix);
  }
  private boolean stage_pending_autocorrect_boundary(
      CurrentlyTypedWord.Snapshot snapshot, Decoder.RequestKey key,
      SharedDecoder.CommitToken literal_token, String separator,
      boolean may_learn_source_on_undo)
  {
    if (snapshot == null || snapshot.word.length() == 0 || key == null
        || uses_termux_raw_events() || !_decoder.is_current(key))
      return false;
    InputConnection conn = _recv.getCurrentInputConnection();
    if (conn == null)
      return false;
    int correction_offset = correction_offset(conn, snapshot.word.length());
    _preserve_autocorrect_boundary_transition = true;
    boolean inserted;
    try
    {
      inserted = send_text(separator);
    }
    finally
    {
      _preserve_autocorrect_boundary_transition = false;
    }
    if (!inserted)
      return false;
    ExtractedText after = conn == _recv.getCurrentInputConnection()
      ? get_cursor_pos(conn) : null;
    int cursor = after != null && after.selectionStart == after.selectionEnd
      ? after.selectionStart : -1;
    _pending_autocorrect_boundary = new PendingAutocorrectBoundary(
        _decoder_session, key, literal_token, snapshot.word, separator, conn,
        cursor, correction_offset, may_learn_source_on_undo);
    if (cursor < 0)
      commit_pending_autocorrect_boundary();
    return true;
  }

  void decoder_result_ready(Decoder.Result result)
  {
    PendingAutocorrectBoundary pending = _pending_autocorrect_boundary;
    if (pending == null || result == null || result.key == null
        || !pending.key.equals(result.key)
        || pending.sessionEpoch != _decoder_session
        || !_decoder.is_current(pending.key))
      return;

    _pending_autocorrect_boundary = null;
    CurrentlyTypedWord.Snapshot boundary_snapshot = _typedword.snapshot();
    Decoder.Candidate correction = result.autocorrection;
    boolean empty_boundary = boundary_snapshot.word.length() == 0
      && !boundary_snapshot.hasSelection
      && boundary_snapshot.cursorRelative == 0;
    boolean suffix_matches = pending_autocorrect_boundary_matches_editor(pending);
    boolean editor_matches = empty_boundary && suffix_matches;
    if (correction == null || correction.surface.equals(pending.source)
        || !editor_matches)
    {
      advance_past_pending_autocorrect_request(pending);
      commit_prepared(pending.literalToken);
      return;
    }

    String corrected_from = plausible_correction_source(pending.source,
        correction.surface);
    SharedDecoder.CommitToken correction_token = should_use_personalization()
      ? _decoder.prepare_commit(pending.sessionEpoch, pending.key,
          correction.surface, corrected_from)
      : null;
    String expected = pending.source + pending.separator;
    String replacement = correction.surface + pending.separator;
    if (replace_recent_text(expected, replacement))
    {
      _autocap.text_replaced(expected.length(), replacement);
      report_editor_correction(pending.connection, pending.correctionOffset,
          pending.source, correction.surface);
      stage_pending_replacement(correction_token, pending.literalToken,
          pending.source, correction.surface, pending.separator,
          pending.mayLearnSourceOnUndo
          && should_learn_source_on_undo(result.literal));
      advance_past_pending_autocorrect_request(pending);
    }
    else
    {
      advance_past_pending_autocorrect_request(pending);
      commit_prepared(pending.literalToken);
    }
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

  private boolean uses_termux_raw_events()
  {
    return EditorConfig.is_termux_raw_editor(
        _recv.getCurrentInputEditorInfo());
  }

  private boolean send_untracked_key_down_up(int keyCode)
  {
    InputConnection conn = _recv.getCurrentInputConnection();
    if (conn == null)
      return false;
    boolean down = conn.sendKeyEvent(new KeyEvent(1, 1, KeyEvent.ACTION_DOWN,
          keyCode, 0, _meta_state, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
          KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));
    boolean up = conn.sendKeyEvent(new KeyEvent(1, 1, KeyEvent.ACTION_UP,
          keyCode, 0, _meta_state, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
          KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));
    return down && up;
  }

  private boolean replace_termux_suffix(String expected, String replacement)
  {
    InputConnection conn = _recv.getCurrentInputConnection();
    if (conn == null || expected.length() == 0)
      return false;
    int delete_count = expected.codePointCount(0, expected.length());
    for (int i = 0; i < delete_count; ++i)
      if (!send_untracked_key_down_up(KeyEvent.KEYCODE_DEL))
        return false;
    return SnippetInserter.insert(conn, replacement);
  }

  boolean send_text(String text)
  {
    return send_text(text, null);
  }

  boolean send_text(String text, TouchTrace.Entry touch)
  {
    cancel_pending_backspace_fallback();
    InputConnection conn = _recv.getCurrentInputConnection();
    if (conn == null)
      return false;
    CharSequence selection = conn.getSelectedText(0);
    boolean replacing_selection = selection != null && selection.length() > 0;
    if (replacing_selection)
      capture_manual_correction_source();
    _autocap.typed(text);
    if (!replacing_selection)
      _typedword.typed(text, touch);
    boolean inserted = SnippetInserter.insert(conn, text);
    if (inserted && replacing_selection)
    {
      _typedword.typed(text, touch);
      _typedword.refresh_current_word();
    }
    return inserted;
  }

  boolean replace_surrounding_text(InputConnection conn, int remove_before,
      int remove_after, String new_text)
  {
    conn.beginBatchEdit();
    try
    {
      return conn.deleteSurroundingText(remove_before, remove_after)
        && conn.commitText(new_text, 1);
    }
    finally
    {
      conn.endBatchEdit();
    }
  }

  /** Replace an exact unselected suffix, preserving the rest of the editor. */
  boolean replace_recent_text(String expected_suffix, String replacement_suffix)
  {
    if (expected_suffix.length() == 0)
      return false;
    InputConnection conn = _recv.getCurrentInputConnection();
    if (conn == null)
      return false;
    CharSequence selection = conn.getSelectedText(0);
    if (selection != null && selection.length() != 0)
      return false;
    CharSequence before = conn.getTextBeforeCursor(expected_suffix.length(), 0);
    if (before == null || !expected_suffix.contentEquals(before))
      return false;
    conn.beginBatchEdit();
    try
    {
      return conn.deleteSurroundingText(expected_suffix.length(), 0)
        && conn.commitText(replacement_suffix, 1);
    }
    finally
    {
      conn.endBatchEdit();
    }
  }

  /** Promote only an editor-verified standalone lowercase i at a boundary. */
  boolean replace_standalone_i(String separator)
  {
    InputConnection conn = _recv.getCurrentInputConnection();
    if (conn == null)
      return false;
    CharSequence before = conn.getTextBeforeCursor(2, 0);
    if (before == null || before.length() == 0
        || before.charAt(before.length() - 1) != 'i')
      return false;
    if (before.length() > 1
        && CurrentlyTypedWord.is_word_char(before.charAt(before.length() - 2)))
      return false;
    return replace_recent_text("i", "I" + separator);
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
    if (ev != KeyValue.Editing.SPACE_BAR
        && ev != KeyValue.Editing.BACKSPACE)
      clear_manual_correction();
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
    if (s == KeyValue.Slider.Delete_words_left)
      clear_manual_correction();
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
    if (EditorConfig.is_cmux_terminal_editor(
          _recv.getCurrentInputEditorInfo()))
    {
      String tracked_word = _typedword.get();
      if (tracked_word.isEmpty())
        return false;
      _delete_selection = new DeleteSelection(
          tracked_word, DELETE_WORDS_TRACKED_TERMINAL);
      return true;
    }
    ExtractedTextRequest req = new ExtractedTextRequest();
    req.hintMaxChars = DELETE_WORDS_CONTEXT_LIMIT;
    ExtractedText et = conn.getExtractedText(req, 0);
    if (et != null && et.text != null && can_set_selection(conn))
    {
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
    CharSequence before = conn.getTextBeforeCursor(
        DELETE_WORDS_CONTEXT_LIMIT, 0);
    if (before == null)
      return false;
    _delete_selection = new DeleteSelection(
        before.toString(), DELETE_WORDS_CURSOR_LOCAL);
    return true;
  }

  private void apply_delete_words_selection(InputConnection conn)
  {
    if (_delete_selection == null || _delete_selection.cursor < 0)
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
    int remove_before = sel.textBeforeCursor.length() - start;
    if (remove_before == 0)
    {
      if (sel.cursor >= 0)
        conn.setSelection(sel.cursor, sel.cursor);
      _recv.selection_state_changed(false);
      return;
    }
    if (sel.cursor == DELETE_WORDS_TRACKED_TERMINAL)
    {
      int code_points = sel.textBeforeCursor.codePointCount(
          start, sel.textBeforeCursor.length());
      for (int i = 0; i < code_points; ++i)
        if (!send_cmux_terminal_backspace(conn))
          break;
      _recv.selection_state_changed(false);
      return;
    }
    boolean deleted;
    if (sel.cursor < 0)
      deleted = delete_cursor_local_text(conn, sel, start);
    else
    {
      deleted = false;
      conn.beginBatchEdit();
      try
      {
        int selection_start = sel.cursor - remove_before;
        if (conn.setSelection(selection_start, sel.cursor))
          deleted = conn.commitText("", 1);
      }
      finally
      {
        conn.endBatchEdit();
      }
    }
    if (deleted)
    {
      _autocap.event_sent(KeyEvent.KEYCODE_DEL, 0);
      _typedword.event_sent(KeyEvent.KEYCODE_DEL, 0);
    }
    _recv.selection_state_changed(false);
  }

  private boolean send_cmux_terminal_backspace(InputConnection conn)
  {
    boolean accepted;
    conn.beginBatchEdit();
    try
    {
      conn.finishComposingText();
      accepted = conn.deleteSurroundingText(1, 0);
    }
    finally
    {
      conn.endBatchEdit();
    }
    if (!accepted)
      return false;
    _autocap.event_sent(KeyEvent.KEYCODE_DEL, 0);
    _typedword.raw_backspace();
    return true;
  }

  private boolean delete_cursor_local_text(InputConnection conn,
      DeleteSelection sel, int start)
  {
    CharSequence current = conn.getTextBeforeCursor(
        sel.textBeforeCursor.length(), 0);
    if (current == null || !sel.textBeforeCursor.contentEquals(current))
      return false;
    String before = extracted_text_snapshot(conn);
    boolean accepted;
    conn.beginBatchEdit();
    try
    {
      conn.finishComposingText();
      accepted = conn.deleteSurroundingText(
          sel.textBeforeCursor.length() - start, 0);
    }
    finally
    {
      conn.endBatchEdit();
    }
    String after = extracted_text_snapshot(conn);
    boolean deleted = before == null || after == null
      ? accepted : !before.equals(after);
    if (deleted)
      return true;
    int code_points = sel.textBeforeCursor.codePointCount(
        start, sel.textBeforeCursor.length());
    for (int i = 0; i < code_points; ++i)
      if (!send_untracked_key_down_up(KeyEvent.KEYCODE_DEL))
        return false;
    return code_points > 0;
  }

  private void cancel_delete_words_selection()
  {
    DeleteSelection sel = _delete_selection;
    _delete_selection = null;
    if (sel == null)
      return;
    InputConnection conn = _recv.getCurrentInputConnection();
    if (conn != null && sel.cursor >= 0)
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
        suggestion_entered(_decoder.current_key(), st.toString());
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

  private boolean should_record_personalization()
  {
    return should_commit_typed_word() && should_use_personalization();
  }

  void handle_word_separator(String separator)
  {
    if (_manual_correction != null
        && !manual_target_matches_snapshot(_typedword.snapshot()))
      _typedword.refresh_current_word();
    CurrentlyTypedWord.Snapshot snapshot = _typedword.snapshot();
    Decoder.RequestKey key = _current_request_key;
    Decoder.Result result = key == null ? null : _decoder.current_result(key);
    Decoder.Candidate correction = should_try_autocorrect() && result != null
      ? result.autocorrection : null;
    boolean should_record = should_record_personalization() && key != null;
    String literal_corrected_from = manual_correction_source(snapshot,
        snapshot.word);
    SharedDecoder.CommitToken literal_token = should_record
      ? _decoder.prepare_commit(_decoder_session, key, snapshot.word,
          literal_corrected_from)
      : null;
    SharedDecoder.CommitToken correction_token = null;
    if (should_record && correction != null)
    {
      String corrected_from = plausible_correction_source(snapshot.word,
          correction.surface);
      if (corrected_from == null)
        corrected_from = manual_correction_source(snapshot, correction.surface);
      correction_token = _decoder.prepare_commit(_decoder_session, key,
          correction.surface, corrected_from);
    }
    boolean should_capitalize_i = correction == null && _config != null
      && _config.autocapitalisation
      && _config.editor_config.autocapitalise_standalone_i
      && should_commit_typed_word() && "i".equals(snapshot.word);
    SharedDecoder.CommitToken capitalized_token =
      should_record && should_capitalize_i
      ? _decoder.prepare_commit(_decoder_session, key, "I", null)
      : null;
    if (correction != null)
    {
      _current_request_key = null;
      if (commit_correction(correction.surface, separator,
            !correction.surface.equals(snapshot.word)))
      {
        if (correction.surface.equals(snapshot.word))
          commit_prepared(correction_token);
        else
          stage_pending_replacement(correction_token, literal_token,
              snapshot.word, correction.surface, separator,
              literal_corrected_from == null
              && should_learn_source_on_undo(result.literal));
      }
      else if (send_text(separator))
        commit_prepared(literal_token);
    }
    else if (should_capitalize_i)
    {
      _current_request_key = null;
      if (replace_standalone_i(separator))
      {
        _autocap.text_replaced(1, "I" + separator);
        _typedword.typed(separator);
        commit_prepared(capitalized_token);
      }
      else if (send_text(separator))
        commit_prepared(literal_token);
    }
    else if (should_try_autocorrect()
        && stage_pending_autocorrect_boundary(
          snapshot, key, literal_token, separator,
          literal_corrected_from == null))
    {
      // The literal separator is already visible; READY may safely refine it.
    }
    else
    {
      _current_request_key = null;
      if (send_text(separator))
        commit_prepared(literal_token);
    }

    if (!should_record && _pending_autocorrect_boundary == null
        && _decoder_session != 0)
      _decoder.invalidate(_decoder_session);
    clear_manual_correction();
  }

  /** Implement autocorrect when enabled in the settings. */
  void handle_space_bar()
  {
    handle_word_separator(" ");
  }

  void send_backspace()
  {
    if (uses_termux_raw_events())
    {
      if (send_untracked_key_down_up(KeyEvent.KEYCODE_DEL))
      {
        _autocap.event_sent(KeyEvent.KEYCODE_DEL, 0);
        _typedword.raw_backspace();
      }
      return;
    }
    InputConnection conn = _recv.getCurrentInputConnection();
    if (EditorConfig.is_cmux_terminal_editor(
          _recv.getCurrentInputEditorInfo()))
    {
      if (conn != null)
        send_cmux_terminal_backspace(conn);
      return;
    }
    boolean deleted = false;
    String deferred_before = null;
    if (conn != null)
    {
      conn.beginBatchEdit();
      try
      {
        conn.finishComposingText();
        CharSequence selection = conn.getSelectedText(0);
        if (selection != null && selection.length() > 0)
          deleted = conn.commitText("", 1);
        else
        {
          String before = extracted_text_snapshot(conn);
          boolean accepted = delete_previous_codepoint(conn);
          String after = extracted_text_snapshot(conn);
          deleted = before == null || after == null
            ? accepted : !before.equals(after);
          if (!deleted && accepted && before != null && before.equals(after))
            deferred_before = before;
          if (!deleted && !accepted && before != null && before.equals(after))
          {
            boolean fallback_accepted = conn.deleteSurroundingText(1, 0);
            String fallback_after = extracted_text_snapshot(conn);
            deleted = fallback_after == null
              ? fallback_accepted : !after.equals(fallback_after);
          }
        }
      }
      finally
      {
        conn.endBatchEdit();
      }
    }
    if (deferred_before != null)
    {
      schedule_backspace_fallback(conn, deferred_before);
      return;
    }
    if (deleted)
    {
      record_backspace();
      return;
    }
    send_key_down_up(KeyEvent.KEYCODE_DEL);
  }

  private void cancel_pending_backspace_fallback()
  {
    ++_backspace_fallback_generation;
  }

  private void schedule_backspace_fallback(InputConnection conn,
      String before)
  {
    long generation = _backspace_fallback_generation;
    _recv.getHandler().postDelayed(() -> {
      if (generation != _backspace_fallback_generation
          || conn != _recv.getCurrentInputConnection())
        return;
      String after = extracted_text_snapshot(conn);
      if (after == null || !before.equals(after))
        record_backspace();
      else
        send_key_down_up(KeyEvent.KEYCODE_DEL);
    }, BACKSPACE_FALLBACK_DELAY_MS);
  }

  private String extracted_text_snapshot(InputConnection conn)
  {
    ExtractedTextRequest req = new ExtractedTextRequest();
    ExtractedText et = conn.getExtractedText(req, 0);
    if (et != null && et.text != null)
      return et.startOffset + ":" + et.selectionStart + ":" + et.selectionEnd
        + ":" + et.text.toString();
    CharSequence before = conn.getTextBeforeCursor(64, 0);
    CharSequence after = conn.getTextAfterCursor(1, 0);
    if (before == null && after == null)
      return null;
    return String.valueOf(before) + "|" + String.valueOf(after);
  }

  private boolean delete_previous_codepoint(InputConnection conn)
  {
    if (VERSION.SDK_INT >= VERSION_CODES.N)
      return conn.deleteSurroundingTextInCodePoints(1, 0);
    return delete_previous_codepoint_if_available(conn);
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
    if (_manual_correction == null)
    {
      _typedword.event_sent(KeyEvent.KEYCODE_DEL, 0);
      return;
    }
    InputConnection conn = _recv.getCurrentInputConnection();
    ExtractedText et = conn == null ? null : get_cursor_pos(conn);
    if (et == null || et.selectionStart != et.selectionEnd)
    {
      _typedword.event_sent(KeyEvent.KEYCODE_DEL, 0);
      return;
    }
    _preserve_manual_correction_transition = true;
    try
    {
      if (_typedword.is_selection_not_empty())
        _typedword.selection_updated(et.selectionStart, et.selectionStart,
            et.selectionEnd);
      else
        _typedword.refresh_current_word();
    }
    finally
    {
      _preserve_manual_correction_transition = false;
    }
  }

  /** Undo a pending changed candidate only at its exact insertion point. */
  void handle_backspace()
  {
    commit_pending_autocorrect_boundary();
    PendingReplacement pending = _pending_replacement;
    _pending_replacement = null;
    if (pending != null)
    {
      String expected = pending.target + pending.separator;
      String replacement = pending.source + pending.separator;
      boolean restored = uses_termux_raw_events()
        ? pending.connection == _recv.getCurrentInputConnection()
          && pending.typedWordRevision == _typedword.snapshot().revision
          && replace_termux_suffix(expected, replacement)
        : pending_cursor_matches(pending)
          && replace_recent_text(expected, replacement);
      if (restored)
      {
        _autocap.text_replaced(expected.length(), replacement);
        _typedword.typed(pending.separator);
        commit_prepared(pending.undoToken);
        if (pending.learnSourceOnUndo)
          learn_word(pending.source);
        clear_manual_correction();
        return;
      }
      commit_prepared(pending.token);
    }
    capture_manual_correction_source();
    send_backspace();
  }

  public static interface IReceiver
  {
    public void handle_event_key(KeyValue.Event ev);
    public void set_shift_state(boolean state, boolean lock);
    public void set_compose_pending(boolean pending);
    public void selection_state_changed(boolean selection_is_ongoing);
    public default void confirm_unlearn_word(String word,
        Runnable positive_action) {}
    public InputConnection getCurrentInputConnection();
    public EditorInfo getCurrentInputEditorInfo();
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

    @Override
    public boolean replace_recent_text(String expected_suffix,
        String replacement_suffix)
    {
      boolean replaced = KeyEventHandler.this.replace_recent_text(
          expected_suffix, replacement_suffix);
      if (replaced && expected_suffix.length() == replacement_suffix.length())
        _typedword.rewrite_current_suffix(expected_suffix, replacement_suffix);
      return replaced;
    }
  }

}
