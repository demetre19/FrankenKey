package juloo.keyboard2;

import android.content.res.Resources;
import android.os.Build.VERSION;
import android.text.InputType;
import android.text.TextUtils;
import android.view.inputmethod.EditorInfo;

public final class EditorConfig
{
  /** Key that replaces the "ACTION" key. Might be [null] to remove that key. */
  public KeyValue action_key_replacement = null;
  /** Key that replaces the "ENTER" key. Might be [null] to not replace the
      enter key. */
  public KeyValue enter_key_replacement = null;
  public int actionId;
  /** Whether the corner GIF key should be shown on keyboard layouts. */
  public boolean gif_action_key = true;
  /** Whether selection mode turns on automatically when text is selected. */
  public boolean selection_mode_enabled = true;
  /** Whether the numeric layout should be shown by default. */
  public boolean numeric_layout = false;
  /** Workaround some apps which answers to [getExtractedText] but do not react
      to [setSelection] while returning [true]. */
  public boolean should_move_cursor_force_fallback = false;

  /** Autocapitalisation. */
  public int caps_mode; // Argument for [getCursorCapsMode()].
  // Whether caps state is on initially.
  public boolean caps_initially_enabled = false;
  // Whether caps state should be updated right away.
  public boolean caps_initially_updated = false;
  /** Whether an exact standalone lowercase i may be rewritten to I. */
  public boolean autocapitalise_standalone_i = true;

  /** CurrentlyTypedWord. */
  public CharSequence initial_text_before_cursor = null; // Might be [null].
  public CharSequence initial_text_after_cursor = null; // Might be [null].
  public int initial_sel_start;
  public int initial_sel_end;

  /** Suggestions. */
  // Doesn't override [_config.suggestions_enabled].
  public boolean should_show_candidates_view;
  public boolean should_show_snippet_row;
  /** Whether autocorrect and local learning are safe for this editor. */
  public boolean should_use_typing_assistance;
  /** Whether sentence-level grammar and voice assistance fit this editor. */
  public boolean should_use_sentence_assistance;
  /** Whether suggestions may read or write persistent learned words. */
  public boolean should_use_personalization = true;

  public EditorConfig() {}

  public void refresh(EditorInfo info, Resources res)
  {
    int inputType = info.inputType & InputType.TYPE_MASK_CLASS;
    int options = info.imeOptions;
    /* Selection mode.
       Editors with [TYPE_NULL] are for example Termux and Emacs. */
    selection_mode_enabled = inputType != InputType.TYPE_NULL;
    enter_key_replacement = null;
    gif_action_key = true;
    /* Action key. Looks at [info.actionLabel] first. */
    if (info.actionLabel != null)
    {
      actionId = info.actionId;
      action_key_replacement =
        KeyValue.makeActionKey(info.actionLabel.toString());
    }
    else
    {
      actionId = options & EditorInfo.IME_MASK_ACTION;
      String label = actionLabel_of_imeAction(actionId, res);
      action_key_replacement = null;
      if (label != null)
      {
        action_key_replacement = KeyValue.makeActionKey(label);
        // Swap the enter and action keys
        if ((options & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0)
        {
          enter_key_replacement = action_key_replacement;
          action_key_replacement = KeyValue.ENTER;
        }
      }
    }
    /* Numeric layout */
    switch (inputType)
    {
      case InputType.TYPE_CLASS_NUMBER:
      case InputType.TYPE_CLASS_PHONE:
      case InputType.TYPE_CLASS_DATETIME:
        numeric_layout = true;
        break;
      default:
        numeric_layout = false;
        break;
    }
    /* setSelection fallback */
    should_move_cursor_force_fallback = _should_move_cursor_force_fallback(info);
    /* Autocapitalisation */
    caps_mode = info.inputType & (TextUtils.CAP_MODE_CHARACTERS |
        TextUtils.CAP_MODE_WORDS | TextUtils.CAP_MODE_SENTENCES);
    if (caps_mode == 0 && should_fallback_sentence_caps(info))
      caps_mode = TextUtils.CAP_MODE_SENTENCES;
    caps_initially_enabled = (info.initialCapsMode != 0);
    caps_initially_updated = caps_should_update_state(info);
    autocapitalise_standalone_i = should_autocapitalise_standalone_i(info);
    /* CurrentlyTypedWord */
    if (VERSION.SDK_INT >= 30)
    {
      initial_text_before_cursor = info.getInitialTextBeforeCursor(20, 0);
      initial_text_after_cursor = info.getInitialTextAfterCursor(20, 0);
    }
    initial_sel_start = info.initialSelStart;
    initial_sel_end = info.initialSelEnd;
    boolean termux_raw_editor = is_termux_raw_editor(info);
    should_use_typing_assistance = should_use_typing_assistance(info);
    should_use_sentence_assistance = should_use_typing_assistance
      && !is_structured_text_editor(info);
    should_show_candidates_view =
      should_use_typing_assistance || termux_raw_editor;
    should_use_personalization = should_use_personalization(info)
      && !termux_raw_editor && !is_structured_text_editor(info);
    should_show_snippet_row = should_show_snippet_row(info);
  }

  static boolean should_show_snippet_row(EditorInfo info)
  {
    return true;
  }

  static boolean is_termux_raw_editor(EditorInfo info)
  {
    return info != null
      && (info.inputType & InputType.TYPE_MASK_CLASS) == InputType.TYPE_NULL
      && "com.termux".equals(info.packageName);
  }

  static boolean is_cmux_terminal_editor(EditorInfo info)
  {
    if (info == null || info.packageName == null
        || !info.packageName.startsWith("dev.cmux.connector"))
      return false;
    int terminal_flags = InputType.TYPE_TEXT_FLAG_MULTI_LINE
      | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
    int terminal_ime_flags = EditorInfo.IME_FLAG_NO_EXTRACT_UI
      | EditorInfo.IME_FLAG_NO_FULLSCREEN;
    return (info.inputType & InputType.TYPE_MASK_CLASS)
        == InputType.TYPE_CLASS_TEXT
      && (info.inputType & terminal_flags) == terminal_flags
      && (info.imeOptions & terminal_ime_flags) == terminal_ime_flags;
  }

  public static boolean should_use_typing_assistance(EditorInfo info)
  {
    if (is_termux_raw_editor(info))
      return true;
    if (info == null
        || (info.inputType & InputType.TYPE_MASK_CLASS)
          != InputType.TYPE_CLASS_TEXT)
      return false;
    switch (info.inputType & InputType.TYPE_MASK_VARIATION)
    {
      case InputType.TYPE_TEXT_VARIATION_NORMAL:
      case InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE:
      case InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE:
      case InputType.TYPE_TEXT_VARIATION_PERSON_NAME:
      case InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS:
      case InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT:
      case InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT:
      case InputType.TYPE_TEXT_VARIATION_FILTER:
      case InputType.TYPE_TEXT_VARIATION_PHONETIC:
      case InputType.TYPE_TEXT_VARIATION_URI:
      case InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS:
      case InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS:
        return true;
      default:
        return false;
    }
  }

  static boolean is_structured_text_editor(EditorInfo info)
  {
    if (info == null
        || (info.inputType & InputType.TYPE_MASK_CLASS)
          != InputType.TYPE_CLASS_TEXT)
      return false;
    switch (info.inputType & InputType.TYPE_MASK_VARIATION)
    {
      case InputType.TYPE_TEXT_VARIATION_URI:
      case InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS:
      case InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS:
        return true;
      default:
        return false;
    }
  }

  static boolean should_use_personalization(EditorInfo info)
  {
    return info != null
      && (info.imeOptions
          & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) == 0;
  }

  /** Generic plain-text fields that omit caps flags still need sentence repair. */
  static boolean should_fallback_sentence_caps(EditorInfo info)
  {
    if ((info.inputType & InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT)
      return false;
    switch (info.inputType & InputType.TYPE_MASK_VARIATION)
    {
      case InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE:
      case InputType.TYPE_TEXT_VARIATION_NORMAL:
      case InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE:
        return true;
      default:
        return false;
    }
  }

  /** Keep the standalone-I convenience out of fields that hold secrets. */
  static boolean should_autocapitalise_standalone_i(EditorInfo info)
  {
    if ((info.inputType & InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT)
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

  String actionLabel_of_imeAction(int action, Resources res)
  {
    int id;
    switch (action)
    {
      case EditorInfo.IME_ACTION_NEXT: id = R.string.key_action_next; break;
      case EditorInfo.IME_ACTION_DONE: id = R.string.key_action_done; break;
      case EditorInfo.IME_ACTION_GO: id = R.string.key_action_go; break;
      case EditorInfo.IME_ACTION_PREVIOUS: id = R.string.key_action_prev; break;
      case EditorInfo.IME_ACTION_SEARCH: id = R.string.key_action_search; break;
      case EditorInfo.IME_ACTION_SEND: id = R.string.key_action_send; break;
      case EditorInfo.IME_ACTION_UNSPECIFIED:
      case EditorInfo.IME_ACTION_NONE:
      default: return null;
    }
    return res.getString(id);
  }

  boolean _should_move_cursor_force_fallback(EditorInfo info)
  {
    // This catch Acode: which sets several variations at once.
    if ((info.inputType & InputType.TYPE_MASK_VARIATION &
          InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0)
      return true;
    // Godot editor: Doesn't handle setSelection() but returns true.
    return info.packageName.startsWith("org.godotengine.editor");
  }

  /** Whether the caps state should be updated when input starts. [inputType]
      is the field from the editor info object. */
  boolean caps_should_update_state(EditorInfo info)
  {
    int class_ = info.inputType & InputType.TYPE_MASK_CLASS;
    int variation = info.inputType & InputType.TYPE_MASK_VARIATION;
    if (class_ != InputType.TYPE_CLASS_TEXT)
      return false;
    switch (variation)
    {
      case InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE:
      case InputType.TYPE_TEXT_VARIATION_NORMAL:
      case InputType.TYPE_TEXT_VARIATION_PERSON_NAME:
      case InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE:
      case InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT:
      case InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT:
        return true;
      default:
        return false;
    }
  }
}
