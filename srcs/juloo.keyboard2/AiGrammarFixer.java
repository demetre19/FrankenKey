package juloo.keyboard2;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

/**
 * "Fix grammar" flow: eligibility, bounded input capture, response
 * guards, and request building. Network calls are injected so tests run
 * fully offline.
 */
final class AiGrammarFixer
{
  static final int MAX_INPUT_LENGTH = 4000;
  static final int MIN_SENTENCE_LENGTH = 8;
  static final double MIN_RATIO = 0.5;
  static final double MAX_RATIO = 1.5;
  /** Minimum share of input words that must survive in the correction. */
  static final double MIN_WORD_OVERLAP = 0.6;

  /** Editor classification for the eligibility matrix. */
  enum EditorClass
  {
    PROSE, EMAIL, URI, PASSWORD, NUMERIC, PHONE, TERMINAL, UNREADABLE
  }

  /** Classify an editor's inputType for eligibility checks. */
  static EditorClass classify(android.view.inputmethod.EditorInfo info)
  {
    if (info == null)
      return EditorClass.UNREADABLE;
    int type = info.inputType
      & android.text.InputType.TYPE_MASK_CLASS;
    int variation = info.inputType
      & android.text.InputType.TYPE_MASK_VARIATION;
    switch (type)
    {
      case android.text.InputType.TYPE_CLASS_TEXT:
        if (variation == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            || variation == android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            || variation == android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
          return EditorClass.PASSWORD;
        if (variation == android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            || variation == android.text.InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)
          return EditorClass.EMAIL;
        if (variation == android.text.InputType.TYPE_TEXT_VARIATION_URI)
          return EditorClass.URI;
        return EditorClass.PROSE;
      case android.text.InputType.TYPE_CLASS_NUMBER:
      case android.text.InputType.TYPE_CLASS_DATETIME:
        if (variation == android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD)
          return EditorClass.PASSWORD;
        return EditorClass.NUMERIC;
      case android.text.InputType.TYPE_CLASS_PHONE:
        return EditorClass.PHONE;
      case android.text.InputType.TYPE_NULL:
        return EditorClass.UNREADABLE;
      default:
        return EditorClass.UNREADABLE;
    }
  }

  /** Distinguishes "nothing to fix" from a malformed model answer. */
  enum Verdict { OK, NO_CHANGE, BAD }

  static Verdict verdict(String response, String input)
  {
    String out = response == null ? null : response.trim();
    if (out == null || out.isEmpty() || out.equals(input))
      return Verdict.NO_CHANGE;
    return sanitizeResponse(response, input) == null
      ? Verdict.BAD : Verdict.OK;
  }

  enum Refusal
  {
    NONE, PASSWORD, NUMERIC, PHONE, EMAIL, URI, TERMINAL, UNREADABLE
  }

  static Refusal eligibility(EditorClass editorClass)
  {
    switch (editorClass)
    {
      case PROSE: return Refusal.NONE;
      case PASSWORD: return Refusal.PASSWORD;
      case NUMERIC: return Refusal.NUMERIC;
      case PHONE: return Refusal.PHONE;
      case EMAIL: return Refusal.EMAIL;
      case URI: return Refusal.URI;
      case TERMINAL: return Refusal.TERMINAL;
      default: return Refusal.UNREADABLE;
    }
  }

  /** A bounded sentence ending with '.', '?', or '!'. */
  static final class Sentence
  {
    final int start;
    final int end;
    final String text;

    Sentence(int start_, int end_, String text_)
    {
      start = start_;
      end = end_;
      text = text_;
    }
  }

  /**
   * The sentence ending at [end] within [beforeCursor] (text before the
   * cursor). [end] is the index just after the sentence terminator.
   * Returns null when no bounded, punctuated sentence qualifies.
   */
  static Sentence sentenceEndingAt(CharSequence beforeCursor, int end)
  {
    if (beforeCursor == null || end <= 0 || end > beforeCursor.length())
      return null;
    CharSequence before = beforeCursor.subSequence(0, end);
    int i = before.length() - 1;
    if (!isSentenceTerminator(before.charAt(i)))
      return null;
    int start = 0;
    for (int j = i - 1; j >= 0; --j)
    {
      char c = before.charAt(j);
      if (c == '\n' || isSentenceTerminator(c))
      {
        start = j + 1;
        break;
      }
    }
    String text = before.subSequence(start, i + 1).toString().trim();
    if (text.length() < MIN_SENTENCE_LENGTH
        || text.length() > MAX_INPUT_LENGTH)
      return null;
    int leading = before.subSequence(start, i + 1).toString()
      .indexOf(text.charAt(0));
    int base = start + Math.max(0, leading);
    if (!hasLetter(text))
      return null;
    return new Sentence(base, i + 1, text);
  }

  /**
   * Revalidation guard for a pending sentence fix: returns the index where
   * [expected] starts in [beforeCursor] when the sentence still ends the
   * user's input, or -1 when anything else sits between the sentence and
   * the cursor. Only whitespace may trail the sentence — any non-whitespace
   * character means the user kept typing after the request fired and the
   * fix must be dropped so it can never overwrite new characters.
   */
  static int matchSentenceTail(CharSequence beforeCursor, String expected)
  {
    if (beforeCursor == null || expected == null)
      return -1;
    int end = beforeCursor.length();
    while (end > 0
        && Character.isWhitespace(beforeCursor.charAt(end - 1)))
      --end;
    if (end < expected.length()
        || !beforeCursor.subSequence(end - expected.length(), end)
            .toString().equals(expected))
      return -1;
    return end - expected.length();
  }

  /** Result of a grammar request: a fix plus an optional toast message. */
  static final class Outcome
  {
    final String corrected;
    final int toastRes;

    Outcome(String corrected_, int toastRes_)
    {
      corrected = corrected_;
      toastRes = toastRes_;
    }
  }

  /**
   * One-at-a-time executor for automatic and manual grammar requests.
   * [busy] is volatile and resets on every exit path — a rejected
   * submission, a failing job, or a refused handler post can never leave
   * the keyboard permanently locked out of grammar fixing.
   */
  static final class Runner
  {
    interface Job
    {
      Outcome run() throws Exception;
    }

    interface Sink
    {
      void accept(Outcome outcome);
    }

    private final java.util.concurrent.Executor executor;
    private final android.os.Handler handler;
    volatile boolean busy;

    Runner(java.util.concurrent.Executor executor_, android.os.Handler handler_)
    {
      executor = executor_;
      handler = handler_;
    }

    void submit(final Job job, final Sink sink)
    {
      busy = true;
      try
      {
        executor.execute(() ->
          {
            Outcome outcome = null;
            try
            {
              outcome = job.run();
            }
            catch (Exception error)
            {
              /* Reported through a null outcome below. */
            }
            final Outcome delivered = outcome;
            if (!handler.post(() -> {
                busy = false;
                sink.accept(delivered);
              }))
              busy = false;
          });
      }
      catch (RuntimeException rejected)
      {
        busy = false;
      }
    }
  }

  private static boolean isSentenceTerminator(char c)
  {
    return c == '.' || c == '?' || c == '!';
  }

  private static boolean hasLetter(String text)
  {
    for (int i = 0; i < text.length(); ++i)
      if (Character.isLetter(text.charAt(i)))
        return true;
    return false;
  }

  /**
   * Conservative-edit guard: the correction must keep most of the input's
   * words. Guards the automatic pass against suggestions that wildly
   * differ from what was typed.
   */
  static boolean conservative(String input, String corrected)
  {
    if (input == null || corrected == null)
      return false;
    double ratio = (double)corrected.length() / Math.max(1, input.length());
    if (ratio < MIN_RATIO || ratio > MAX_RATIO)
      return false;
    java.util.HashSet<String> inputWords = new java.util.HashSet<>();
    int total = 0;
    for (String word : input.toLowerCase(java.util.Locale.ROOT)
        .split("[^\\p{L}\\p{N}'’]+"))
    {
      if (word.isEmpty())
        continue;
      inputWords.add(word);
      total++;
    }
    if (total == 0)
      return false;
    int kept = 0;
    for (String word : corrected.toLowerCase(java.util.Locale.ROOT)
        .split("[^\\p{L}\\p{N}'’]+"))
    {
      if (!word.isEmpty() && inputWords.contains(word))
        kept++;
    }
    return (double)kept / (double)total >= MIN_WORD_OVERLAP;
  }

  /** Quote/fence strip plus empty/identical/ratio guards. */
  static String sanitizeResponse(String response, String input)
  {
    if (response == null)
      return null;
    String out = response.trim();
    if (out.startsWith("```"))
    {
      int first = out.indexOf('\n');
      int last = out.lastIndexOf("```");
      if (first >= 0 && last > first)
        out = out.substring(first + 1, last).trim();
    }
    if (out.length() >= 2 && out.startsWith("\"")
        && out.endsWith("\""))
      out = out.substring(1, out.length() - 1);
    if (out.isEmpty() || out.equals(input))
      return null;
    double ratio = (double)out.length() / Math.max(1, input.length());
    if (ratio < MIN_RATIO || ratio > MAX_RATIO)
      return null;
    return out;
  }

  static JSONObject buildRequest(String modelId, String systemPrompt,
      String input, int maxTokens) throws org.json.JSONException
  {
    List<ReaderAiOpenRouter.Message> messages = new ArrayList<>();
    messages.add(new ReaderAiOpenRouter.Message("system", systemPrompt));
    messages.add(new ReaderAiOpenRouter.Message("user", input));
    return ReaderAiOpenRouter.buildRequest(modelId, messages, maxTokens,
        0.0);
  }

  static int maxTokensFor(String input)
  {
    /* Rough token estimate ~4 chars/token; ask for ~1.5x input. */
    int tokens = Math.max(1, input.length() / 4);
    return (int)Math.ceil(tokens * MAX_RATIO) + 8;
  }

  static String sha256(String text)
  {
    try
    {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(
          text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(hash.length * 2);
      for (byte b : hash)
        hex.append(String.format("%02x", b));
      return hex.toString();
    }
    catch (NoSuchAlgorithmException error)
    {
      throw new RuntimeException(error);
    }
  }
}
