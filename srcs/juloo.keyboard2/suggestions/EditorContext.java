package juloo.keyboard2.suggestions;

import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import java.util.Locale;

/**
 * Immutable snapshot of the editor an IME request was made for.
 *
 * Filled on the main thread at request time and carried with the request so
 * worker-side [CandidateSource]s see the exact editor the candidates will be
 * shown in. Derives its classification from the same [EditorInfo] the
 * decoder's [EditorConfig] decisions already use; it does not reimplement
 * typing-assistance policy.
 */
public final class EditorContext
{
  public static enum EditorClass
  {
    PROSE,
    EMAIL,
    URI,
    SEARCH,
    TERMINAL,
    OTHER
  }

  public final EditorClass editorClass;
  public final boolean noPersonalizedLearning;
  /** Up to 256 UTF-16 units of text before the cursor. Never null. */
  public final String textBeforeCursor;
  /** Up to 64 UTF-16 units of text after the cursor. Never null. */
  public final String textAfterCursor;
  /** Might be null when no subtype locale is selected. */
  public final Locale locale;

  static final int BEFORE_LIMIT = 256;
  static final int AFTER_LIMIT = 64;

  public static final EditorContext EMPTY = new EditorContext(
      EditorClass.OTHER, false, "", "", null);

  public EditorContext(EditorClass editorClass_, boolean noPersonalizedLearning_,
      String textBeforeCursor_, String textAfterCursor_, Locale locale_)
  {
    editorClass = editorClass_;
    noPersonalizedLearning = noPersonalizedLearning_;
    textBeforeCursor = textBeforeCursor_ == null ? "" : textBeforeCursor_;
    textAfterCursor = textAfterCursor_ == null ? "" : textAfterCursor_;
    locale = locale_;
  }

  /** Read the editor state. Must be called on the IME main thread. */
  public static EditorContext capture(EditorInfo info, InputConnection conn,
      Locale locale)
  {
    EditorClass cls = classify(info);
    boolean noLearning = info != null
      && (info.imeOptions
        & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0;
    CharSequence before = null;
    CharSequence after = null;
    if (conn != null)
    {
      before = conn.getTextBeforeCursor(BEFORE_LIMIT, 0);
      after = conn.getTextAfterCursor(AFTER_LIMIT, 0);
    }
    return new EditorContext(cls, noLearning,
        before == null ? "" : before.toString(),
        after == null ? "" : after.toString(), locale);
  }

  static EditorClass classify(EditorInfo info)
  {
    if (info == null)
      return EditorClass.OTHER;
    if ((info.inputType & InputType.TYPE_MASK_CLASS)
        == InputType.TYPE_NULL)
      return EditorClass.TERMINAL;
    if ((info.inputType & InputType.TYPE_MASK_CLASS)
        != InputType.TYPE_CLASS_TEXT)
      return EditorClass.OTHER;
    switch (info.inputType & InputType.TYPE_MASK_VARIATION)
    {
      case InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS:
      case InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS:
        return EditorClass.EMAIL;
      case InputType.TYPE_TEXT_VARIATION_URI:
        return EditorClass.URI;
      case InputType.TYPE_TEXT_VARIATION_FILTER:
        return EditorClass.SEARCH;
      case InputType.TYPE_TEXT_VARIATION_NORMAL:
      case InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE:
      case InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE:
      case InputType.TYPE_TEXT_VARIATION_PERSON_NAME:
      case InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS:
      case InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT:
      case InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT:
      case InputType.TYPE_TEXT_VARIATION_PHONETIC:
        return EditorClass.PROSE;
      default:
        return EditorClass.OTHER;
    }
  }
}
