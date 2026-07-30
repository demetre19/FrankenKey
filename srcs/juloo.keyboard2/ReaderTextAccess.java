package juloo.keyboard2;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.UserManager;
import android.os.PersistableBundle;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;

/** Explicit, fail-closed access to text that the Reader may speak. */
final class ReaderTextAccess
{
  static final String EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE";
  private static final int MAX_TEXT_LENGTH =
    ReaderPlaybackService.MAX_ACTIVE_TEXT_LENGTH;

  enum Failure
  {
    NONE,
    EMPTY,
    SENSITIVE,
    UNSAFE_EDITOR,
    USER_LOCKED,
    UNAVAILABLE,
    TOO_LARGE
  }

  static final class Result
  {
    final String text;
    final Failure failure;

    private Result(String text, Failure failure)
    {
      this.text = text;
      this.failure = failure;
    }

    boolean isSuccess() { return failure == Failure.NONE; }

    static Result failure(Failure failure)
    {
      return new Result(null, failure);
    }

    static Result text(CharSequence value)
    {
      if (value == null || value.toString().trim().isEmpty())
        return failure(Failure.EMPTY);
      if (value.length() > MAX_TEXT_LENGTH)
        return failure(Failure.TOO_LARGE);
      return new Result(value.toString(), Failure.NONE);
    }
  }

  static Result readClipboard(Context context)
  {
    if (!isUserUnlocked(context))
      return Result.failure(Failure.USER_LOCKED);
    ClipboardManager manager = (ClipboardManager)context.getSystemService(
        Context.CLIPBOARD_SERVICE);
    ClipData clip;
    try
    {
      clip = manager == null ? null : manager.getPrimaryClip();
    }
    catch (Exception e)
    {
      return Result.failure(Failure.UNAVAILABLE);
    }
    if (clip == null || clip.getItemCount() == 0)
      return Result.failure(Failure.EMPTY);
    if (isSensitive(clip.getDescription()))
      return Result.failure(Failure.SENSITIVE);
    for (int i = 0; i < clip.getItemCount(); i++)
    {
      ClipData.Item item = clip.getItemAt(i);
      if (item == null || item.getText() == null)
        continue;
      Result result = Result.text(item.getText());
      if (result.failure != Failure.EMPTY)
        return result;
    }
    return Result.failure(Failure.UNAVAILABLE);
  }

  static Result readSelection(Context context, EditorInfo editor,
      InputConnection connection)
  {
    if (!isUserUnlocked(context))
      return Result.failure(Failure.USER_LOCKED);
    return readSelection(editor, connection);
  }

  static Result readSelection(EditorInfo editor, InputConnection connection)
  {
    if (!isReadableEditor(editor))
      return Result.failure(Failure.UNSAFE_EDITOR);
    if (connection == null)
      return Result.failure(Failure.UNAVAILABLE);
    try
    {
      return Result.text(connection.getSelectedText(0));
    }
    catch (Exception e)
    {
      return Result.failure(Failure.UNAVAILABLE);
    }
  }

  static Result readCurrentField(Context context, EditorInfo editor,
      InputConnection connection)
  {
    if (!isUserUnlocked(context))
      return Result.failure(Failure.USER_LOCKED);
    return readCurrentField(editor, connection);
  }

  static Result readCurrentField(EditorInfo editor, InputConnection connection)
  {
    if (!isReadableEditor(editor))
      return Result.failure(Failure.UNSAFE_EDITOR);
    if (connection == null)
      return Result.failure(Failure.UNAVAILABLE);
    ExtractedTextRequest request = new ExtractedTextRequest();
    request.hintMaxChars = MAX_TEXT_LENGTH + 1;
    request.hintMaxLines = 10000;
    try
    {
      ExtractedText extracted = connection.getExtractedText(request, 0);
      if (extracted == null || extracted.text == null)
        return Result.failure(Failure.UNAVAILABLE);
      if (extracted.partialStartOffset >= 0 || extracted.startOffset != 0)
        return Result.failure(Failure.UNAVAILABLE);
      return Result.text(extracted.text);
    }
    catch (Exception e)
    {
      return Result.failure(Failure.UNAVAILABLE);
    }
  }

  static boolean isReadableEditor(EditorInfo editor)
  {
    if (editor == null || EditorConfig.is_termux_raw_editor(editor) ||
        EditorConfig.is_cmux_terminal_editor(editor))
      return false;
    if ((editor.inputType & InputType.TYPE_MASK_CLASS) !=
        InputType.TYPE_CLASS_TEXT)
      return false;
    switch (editor.inputType & InputType.TYPE_MASK_VARIATION)
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
        return true;
      default:
        return false;
    }
  }

  static boolean isUserUnlocked(Context context)
  {
    if (Build.VERSION.SDK_INT < 24)
      return true;
    UserManager manager = (UserManager)context.getSystemService(
        Context.USER_SERVICE);
    return manager != null && manager.isUserUnlocked();
  }

  static boolean isSensitive(ClipDescription description)
  {
    if (description == null || Build.VERSION.SDK_INT < 24)
      return false;
    PersistableBundle extras = description.getExtras();
    return extras != null && extras.getBoolean(EXTRA_IS_SENSITIVE, false);
  }

  private ReaderTextAccess() {}
}
