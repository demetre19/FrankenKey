package juloo.keyboard2;

import android.content.Context;
import android.os.Handler;
import android.view.inputmethod.InputConnection;
import android.view.textservice.SentenceSuggestionsInfo;
import android.view.textservice.SpellCheckerSession;
import android.view.textservice.SuggestionsInfo;
import android.view.textservice.TextInfo;
import android.view.textservice.TextServicesManager;
import java.util.Locale;

/** Serializes Android text-service grammar checks and rejects stale editor text. */
public final class SystemGrammarChecker
    implements SpellCheckerSession.SpellCheckerSessionListener, AutoCloseable
{
  static final int MAX_TEXT_LENGTH = 500;
  static final long REQUEST_DELAY_MS = 350;
  static final long REQUEST_TIMEOUT_MS = 1500;

  public interface Callback
  {
    void on_correction(Correction correction);
  }

  public static final class Correction
  {
    public final String text;
    public final int cursor;
    public final int offset;
    public final int length;
    public final String replacement;
    public final int sequence;

    Correction(String text_, int cursor_, int offset_, int length_,
        String replacement_, int sequence_)
    {
      text = text_;
      cursor = cursor_;
      offset = offset_;
      length = length_;
      replacement = replacement_;
      sequence = sequence_;
    }

    public boolean apply(InputConnection connection, int currentCursor)
    {
      if (connection == null || currentCursor != cursor)
        return false;
      CharSequence current = connection.getTextBeforeCursor(MAX_TEXT_LENGTH, 0);
      if (current == null || !current.toString().endsWith(text))
        return false;
      int removeBefore = text.length() - offset;
      if (offset < 0 || length < 0 || offset + length > text.length()
          || removeBefore < 0)
        return false;
      String suffix = replacement + text.substring(offset + length);
      connection.beginBatchEdit();
      try
      {
        if (!connection.deleteSurroundingText(removeBefore, 0))
          return false;
        return connection.commitText(suffix, 1);
      }
      finally
      {
        connection.endBatchEdit();
      }
    }
  }

  static final class Request
  {
    final String text;
    final int cursor;
    final int sequence;

    Request(String text_, int cursor_, int sequence_)
    {
      text = text_;
      cursor = cursor_;
      sequence = sequence_;
    }
  }

  private final Context _context;
  private final Handler _handler;
  private final Callback _callback;
  private SpellCheckerSession _session;
  private Request _active;
  private Request _pending;
  private int _nextSequence = 1;
  private Runnable _debouncedSubmit;
  private Runnable _timeout;
  private boolean _enabled;

  public SystemGrammarChecker(Context context, Handler handler,
      Callback callback)
  {
    _context = context;
    _handler = handler;
    _callback = callback;
  }

  public void start(Locale locale, boolean enabled)
  {
    close_session();
    _enabled = enabled;
    if (!enabled)
      return;
    TextServicesManager manager = (TextServicesManager)_context
      .getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE);
    if (manager != null)
      _session = manager.newSpellCheckerSession(null, locale, this, false);
    if (_session == null)
      _enabled = false;
  }

  public void request(InputConnection connection, int selectionStart,
      int selectionEnd)
  {
    cancel_debounce();
    _callback.on_correction(null);
    if (!_enabled || _session == null || connection == null
        || selectionStart < 0 || selectionStart != selectionEnd)
      return;
    CharSequence before = connection.getTextBeforeCursor(MAX_TEXT_LENGTH, 0);
    if (before == null)
      return;
    String text = relevant_text(before.toString());
    if (text.length() < 3)
      return;
    Request request = new Request(text, selectionStart, _nextSequence++);
    _debouncedSubmit = () -> submit(request);
    _handler.postDelayed(_debouncedSubmit, REQUEST_DELAY_MS);
  }

  static String relevant_text(String beforeCursor)
  {
    int start = Math.max(beforeCursor.lastIndexOf('\n'),
        Math.max(beforeCursor.lastIndexOf('.'),
          Math.max(beforeCursor.lastIndexOf('!'),
            beforeCursor.lastIndexOf('?'))));
    String text = beforeCursor.substring(Math.min(beforeCursor.length(), start + 1));
    int leading = 0;
    while (leading < text.length() && Character.isWhitespace(text.charAt(leading)))
      leading++;
    return text.substring(leading);
  }

  private void submit(Request request)
  {
    _debouncedSubmit = null;
    if (!_enabled || _session == null)
      return;
    if (_active != null)
    {
      _pending = request;
      _session.cancel();
      return;
    }
    _active = request;
    _session.getSentenceSuggestions(
        new TextInfo[]{ new TextInfo(request.text, 0, request.sequence) }, 1);
    Request expected = request;
    _timeout = () -> finish_request(expected, null);
    _handler.postDelayed(_timeout, REQUEST_TIMEOUT_MS);
  }

  @Override
  public void onGetSentenceSuggestions(SentenceSuggestionsInfo[] results)
  {
    _handler.post(() -> {
        Request active = _active;
        if (active != null)
          finish_request(active, correction_from(active, results));
      });
  }

  @Override
  public void onGetSuggestions(SuggestionsInfo[] results) {}

  private void finish_request(Request request, Correction correction)
  {
    if (_active != request)
      return;
    cancel_timeout();
    _active = null;
    if (_pending == null)
      _callback.on_correction(correction);
    Request pending = _pending;
    _pending = null;
    if (pending != null)
      submit(pending);
  }

  static Correction correction_from(Request request,
      SentenceSuggestionsInfo[] results)
  {
    if (results == null || results.length != 1 || results[0] == null)
      return null;
    SentenceSuggestionsInfo sentence = results[0];
    for (int i = 0; i < sentence.getSuggestionsCount(); i++)
    {
      SuggestionsInfo info = sentence.getSuggestionsInfoAt(i);
      if (info == null || info.getSequence() != request.sequence
          || info.getSuggestionsCount() == 0)
        continue;
      int attributes = info.getSuggestionsAttributes();
      boolean grammar = (attributes
          & SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_GRAMMAR_ERROR) != 0;
      boolean typo = (attributes
          & SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO) != 0;
      if (!grammar && typo)
        continue;
      int offset = sentence.getOffsetAt(i);
      int length = sentence.getLengthAt(i);
      if (offset < 0 || length <= 0 || offset + length > request.text.length())
        continue;
      String replacement = info.getSuggestionAt(0);
      if (replacement == null || replacement.length() == 0
          || replacement.equals(request.text.substring(offset, offset + length)))
        continue;
      return new Correction(request.text, request.cursor, offset, length,
          replacement, request.sequence);
    }
    return null;
  }

  private void cancel_debounce()
  {
    if (_debouncedSubmit != null)
      _handler.removeCallbacks(_debouncedSubmit);
    _debouncedSubmit = null;
  }

  private void cancel_timeout()
  {
    if (_timeout != null)
      _handler.removeCallbacks(_timeout);
    _timeout = null;
  }

  private void close_session()
  {
    cancel_debounce();
    cancel_timeout();
    _active = null;
    _pending = null;
    if (_session != null)
    {
      _session.cancel();
      _session.close();
      _session = null;
    }
    _callback.on_correction(null);
  }

  @Override
  public void close()
  {
    _enabled = false;
    close_session();
  }
}
