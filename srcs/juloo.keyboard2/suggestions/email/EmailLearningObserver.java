package juloo.keyboard2.suggestions.email;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import java.util.regex.Pattern;

/**
 * Email learning events (B-F4).
 *
 * Records FIELD, PROSE and SELECTED learning events into
 * [EmailMemoryStore]. All store calls run on a dedicated worker thread; the
 * methods below are invoked on the IME main thread and only capture and
 * enqueue immutable state there.
 *
 * Learning requires:
 * - {@code ti_email_memory_enabled} ON (default ON),
 * - the editor is not password/numeric/phone/unknown/terminal class,
 * - {@link EditorInfo#IME_FLAG_NO_PERSONALIZED_LEARNING} is not set.
 *
 * FIELD (+2) additionally requires an email-eligible editor (email
 * variation or soft hint) and fires only when the whole field text is one
 * valid address that changed since focus (pre-filled unchanged text is
 * skipped). PROSE (+1) fires when a space, comma, semicolon or Enter
 * completes a token that is one valid address in a typing-assistance prose
 * field. SELECTED (+2) fires when an {@code EMAIL_ADDRESS} candidate is
 * tapped.
 */
public final class EmailLearningObserver
{
  /** Setting name and its read seam (decouples the observer from Config). */
  public static final String PREF_MEMORY_ENABLED = "ti_email_memory_enabled";

  public static interface MemoryEnabled
  {
    /** True while the *Remember email addresses* switch is ON. */
    public boolean get();
  }

  /** Clock seam for tests; returns ms since epoch. */
  public static interface Clock
  {
    public long now();
  }

  private final EmailMemoryStore _store;
  private final MemoryEnabled _enabled;
  private final Handler _worker;
  private final Clock _clock;

  /** Package-visible seam for tests: drive store writes on the caller's
      handler (or a Robolectric looper) with injected settings and clock. */
  public EmailLearningObserver(EmailMemoryStore store, Handler worker,
      MemoryEnabled enabled, Clock clock)
  {
    _store = store;
    _worker = worker;
    _enabled = enabled;
    _clock = clock;
  }

  /**
   * Production entry point: a credential-protected {@code email_memory}
   * store driven by its own worker thread, gated by the default
   * shared preferences. Returns null only when {@code context} is null.
   */
  public static EmailLearningObserver create(Context context,
      final SharedPreferences defaultPrefs)
  {
    if (context == null)
      return null;
    HandlerThread thread = new HandlerThread("email-learning");
    thread.start();
    Handler worker = new Handler(thread.getLooper());
    EmailMemoryStore store = EmailMemoryStore.create(context, worker);
    return new EmailLearningObserver(store, worker, new MemoryEnabled()
    {
      public boolean get()
      {
        return defaultPrefs == null
          || defaultPrefs.getBoolean(PREF_MEMORY_ENABLED, true);
      }
    }, new Clock()
    {
      public long now() { return System.currentTimeMillis(); }
    });
  }

  /** Editors that never produce learning events, in any row of the table. */
  static boolean base_editor_guard(EditorInfo info)
  {
    if (info == null)
      return false;
    if ((info.imeOptions
        & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0)
      return false;
    if ((info.inputType & InputType.TYPE_MASK_CLASS)
        != InputType.TYPE_CLASS_TEXT)
      return false;
    switch (info.inputType & InputType.TYPE_MASK_VARIATION)
    {
      case InputType.TYPE_TEXT_VARIATION_PASSWORD:
      case InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD:
      case InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD:
        return false;
      default:
        return true;
    }
  }

  /**
   * Email-eligible field: an email variation, or a single-line text field
   * whose hint is "email"/"email address" (soft field, B-F9).
   */
  public static boolean is_email_field(EditorInfo info)
  {
    if (!base_editor_guard(info))
      return false;
    switch (info.inputType & InputType.TYPE_MASK_VARIATION)
    {
      case InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS:
      case InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS:
        return true;
      default:
        break;
    }
    return is_soft_email_field(info);
  }

  static final Pattern EMAIL_HINT = Pattern.compile(
      "^\\s*(your\\s+)?e-?mail(\\s+address)?\\s*$",
      Pattern.CASE_INSENSITIVE);

  /** Normal single-line text field labelled "Email"; "Email subject" doesn't qualify. */
  static boolean is_soft_email_field(EditorInfo info)
  {
    if ((info.inputType & InputType.TYPE_MASK_VARIATION)
        != InputType.TYPE_TEXT_VARIATION_NORMAL)
      return false;
    if ((info.inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0)
      return false;
    CharSequence hint = info.hintText != null ? info.hintText : info.label;
    return hint != null && EMAIL_HINT.matcher(hint).matches();
  }

  /** Prose learning eligibility: typing-assistance fields, excluding
      structured (email/URI) variations which follow the FIELD row. */
  static boolean is_prose_field(EditorInfo info)
  {
    if (!juloo.keyboard2.EditorConfig.should_use_typing_assistance(info))
      return false;
    if ((info.imeOptions
        & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0)
      return false;
    switch (info.inputType & InputType.TYPE_MASK_VARIATION)
    {
      case InputType.TYPE_TEXT_VARIATION_URI:
      case InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS:
      case InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS:
        return false;
      default:
        return true;
    }
  }

  private boolean enabled(EditorInfo info)
  {
    return _enabled.get() && base_editor_guard(info);
  }

  /** Normalizes and validates; null when the text is not one whole address. */
  static String valid_address_or_null(String text)
  {
    if (text == null || text.length() > EmailToken.MAX_TOKEN_UNITS)
      return null;
    String normalized = EmailAddressValidator.normalize(text);
    if (normalized == null
        || EmailAddressValidator.validate(normalized) != null)
      return null;
    return normalized;
  }

  /**
   * FIELD (+2): the field finished; if it is email-eligible and its whole
   * text is one valid address that changed during this focus, record it.
   * {@code startText}/{@code finishText} may be null when extraction failed;
   * nothing is learned then.
   */
  public void onFieldFinished(EditorInfo info, String startText,
      String finishText)
  {
    if (finishText == null || finishText.equals(startText))
      return;
    if (!enabled(info) || !is_email_field(info))
      return;
    final String address = valid_address_or_null(finishText);
    if (address == null)
      return;
    final long now = _clock.now();
    _worker.post(new Runnable()
    {
      public void run()
      {
        _store.record_use(address, 2, EmailMemoryStore.SOURCE_FIELD, now);
      }
    });
  }

  /**
   * PROSE (+1): a separator completed {@code token} in a prose field; record
   * it when the token is one valid address. Called after the separator was
   * committed, so the token only ever contains typed text.
   */
  public void onProseSeparator(EditorInfo info, String token)
  {
    if (!enabled(info) || !is_prose_field(info))
      return;
    final String address = valid_address_or_null(token);
    if (address == null)
      return;
    final long now = _clock.now();
    _worker.post(new Runnable()
    {
      public void run()
      {
        _store.record_use(address, 1, EmailMemoryStore.SOURCE_PROSE, now);
      }
    });
  }

  /** SELECTED (+2): the user tapped an {@code EMAIL_ADDRESS} candidate. */
  public void onAddressSelected(EditorInfo info, final String address)
  {
    if (address == null || !enabled(info))
      return;
    final long now = _clock.now();
    _worker.post(new Runnable()
    {
      public void run()
      {
        _store.record_use(address, 2, EmailMemoryStore.SOURCE_SELECTED, now);
      }
    });
  }
}
