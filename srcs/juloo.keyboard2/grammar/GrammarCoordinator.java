package juloo.keyboard2.grammar;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Runs [GrammarRules] on the last words of the current sentence at every
 * word boundary, merges them with the platform grammar checker, and feeds
 * at most three issues to the strip one at a time. Pure state machine;
 * callers pump [onBoundary] and [onSystemCorrection] on the UI thread.
 */
public final class GrammarCoordinator
{
  public static final int WINDOW_WORDS = 8;
  public static final int MAX_QUEUED_ISSUES = 3;

  /** Presents one issue at a time. [present] with null clears the strip. */
  public interface Presenter
  {
    void present(GrammarIssue issue);
    void apply(GrammarIssue issue);
    void undo(GrammarIssue issue);
    void disableRule(String ruleId);
  }

  private final GrammarData _data;
  private final Presenter _presenter;
  private final Set<String> _sessionIgnores = new HashSet<>();
  private final Set<String> _disabledRules = new HashSet<>();
  private final Set<String> _firedSpans = new HashSet<>();
  private final Deque<GrammarIssue> _queue = new ArrayDeque<>();
  private final List<SystemGrammarCheckerFacade> _systemCorrections =
    new ArrayList<>();
  private GrammarIssue _showing;
  private GrammarIssue _undoing;
  private boolean _enabled;

  /** Minimal view of a system correction for dedupe. */
  public interface SystemGrammarCheckerFacade
  {
    int offset();
    int length();
    String replacement();
  }

  public GrammarCoordinator(GrammarData data, Presenter presenter)
  {
    _data = data;
    _presenter = presenter;
  }

  public void setEnabled(boolean enabled)
  {
    _enabled = enabled;
    if (!enabled)
      resetSession();
  }

  /** Editor session switched: ignore list and queue reset. */
  public void resetSession()
  {
    _sessionIgnores.clear();
    _firedSpans.clear();
    _systemCorrections.clear();
    _queue.clear();
    _showing = null;
    _undoing = null;
    _presenter.present(null);
  }

  /**
   * Called at each word boundary. [beforeCursor] is the text before the
   * cursor ending at the boundary; the coordinator re-checks the current
   * sentence's last [WINDOW_WORDS] words.
   */
  public void onBoundary(String beforeCursor)
  {
    if (!_enabled || _data == null || beforeCursor == null)
      return;
    String window = windowOf(beforeCursor);
    if (window.isEmpty())
      return;
    List<GrammarIssue> issues = GrammarRules.check(window, _data);
    int base = beforeCursor.length() - window.length();
    for (GrammarIssue issue : issues)
    {
      if (_disabledRules.contains(issue.ruleId))
        continue;
      /* Issues carry absolute editor offsets so apply can target them. */
      GrammarIssue absolute = new GrammarIssue(issue.ruleId,
          base + issue.offset, issue.length, issue.replacement,
          issue.matched);
      String key = issue.ruleId + "@" + absolute.offset + "+"
        + issue.length;
      String ignoreKey = issue.ruleId + ":"
        + issue.matched.toLowerCase(java.util.Locale.ROOT);
      if (_firedSpans.contains(key) || _sessionIgnores.contains(ignoreKey))
        continue;
      _firedSpans.add(key);
      if (dedupedBySystem(absolute))
        continue;
      enqueue(absolute);
    }
    presentNext();
  }

  /** System checker delivered a correction; record it for dedupe. */
  public void onSystemCorrection(int offset, int length, String replacement)
  {
    _systemCorrections.add(new SystemGrammarCheckerFacade()
    {
      public int offset() { return offset; }
      public int length() { return length; }
      public String replacement() { return replacement; }
    });
  }

  public void fixShowing()
  {
    if (_showing == null)
      return;
    _presenter.apply(_showing);
    _undoing = _showing;
    _showing = null;
    presentNext();
  }

  public void ignoreShowing()
  {
    if (_showing == null)
      return;
    _sessionIgnores.add(_showing.ruleId + ":"
        + _showing.matched.toLowerCase());
    _showing = null;
    presentNext();
  }

  public void disableShowingRule()
  {
    if (_showing == null)
      return;
    _disabledRules.add(_showing.ruleId);
    _showing = null;
    presentNext();
  }

  /** Undo restores the original span within 5 s of a Fix. */
  public void undoShowing()
  {
    if (_undoing == null)
      return;
    _presenter.undo(_undoing);
    _undoing = null;
  }

  public GrammarIssue showing() { return _showing; }
  public GrammarIssue undoCandidate() { return _undoing; }
  public int queueSize() { return _queue.size(); }

  static String windowOf(String beforeCursor)
  {
    int end = beforeCursor.length();
    int words = 0;
    int i = end;
    while (i > 0 && words < WINDOW_WORDS)
    {
      while (i > 0 && !isWordChar(beforeCursor.charAt(i - 1)))
        i--;
      if (i == 0)
        break;
      while (i > 0 && isWordChar(beforeCursor.charAt(i - 1)))
        i--;
      words++;
    }
    /* Back up to the sentence start (after ., !, ?, newline) if within window. */
    int sentenceStart = 0;
    for (int j = i - 1; j >= Math.max(0, end - 500); j--)
    {
      char c = beforeCursor.charAt(j);
      if (c == '.' || c == '!' || c == '?' || c == '\n')
      {
        sentenceStart = j + 1;
        break;
      }
    }
    int start = Math.max(i, sentenceStart);
    while (start < end && !isWordChar(beforeCursor.charAt(start)))
      start++;
    return beforeCursor.substring(start, end);
  }

  private void enqueue(GrammarIssue issue)
  {
    if (_queue.size() < MAX_QUEUED_ISSUES)
      _queue.add(issue);
  }

  private void presentNext()
  {
    if (_showing == null && !_queue.isEmpty())
      _showing = _queue.poll();
    _presenter.present(_showing);
  }

  private boolean dedupedBySystem(GrammarIssue issue)
  {
    for (SystemGrammarCheckerFacade correction : _systemCorrections)
    {
      if (correction.offset() == issue.offset
          && correction.length() == issue.length
          && correction.replacement().equals(issue.replacement))
        return true;
    }
    return false;
  }

  private static boolean isWordChar(char c)
  {
    return Character.isLetterOrDigit(c) || c == '\'' || c == '\u2019';
  }
}
