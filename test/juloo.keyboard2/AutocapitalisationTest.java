package juloo.keyboard2;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class AutocapitalisationTest
{
  @Test
  public void character_caps_mode_stays_enabled_after_typing_a_character()
  {
    RecordingCallback callback = new RecordingCallback();
    Autocapitalisation autocap = autocap(callback,
        TextUtils.CAP_MODE_CHARACTERS, TextUtils.CAP_MODE_CHARACTERS);

    autocap.type_one_char('a');
    autocap.delayed_callback.run();

    assertTrue("All-caps fields must keep shift enabled after every character.",
        callback.shouldEnable);
  }

  @Test
  public void sentence_caps_rechecks_after_sentence_punctuation_and_whitespace()
  {
    char[] triggers = new char[] {'\n', ' ', '.', '!', '?'};
    String[] names = new String[] {
      "newline", "plain space", "full stop", "exclamation mark",
      "question mark"
    };

    for (int i = 0; i < triggers.length; ++i)
    {
      RecordingCallback callback = new RecordingCallback();
      Autocapitalisation autocap = autocap(callback,
          TextUtils.CAP_MODE_SENTENCES, TextUtils.CAP_MODE_SENTENCES);

      autocap.type_one_char(triggers[i]);
      autocap.delayed_callback.run();

      assertTrue("Sentence caps must update after " + names[i] + ".",
          callback.shouldEnable);
    }
  }


  @Test
  public void sentence_initial_lowercase_letter_rewrites_after_editor_sync()
  {
    MutableInputConnection input = new MutableInputConnection("Done. ", 0);
    RecordingCallback callback = new RecordingCallback(input);
    Autocapitalisation autocap = autocap(callback, input,
        TextUtils.CAP_MODE_SENTENCES);

    autocap.typed("t");
    input.editorInsert("t");
    autocap.delayed_callback.run();

    assertEquals("A sentence's first letter must be repaired after the editor has accepted the keystroke.",
        "Done. T", input.text.toString());
    assertEquals("The repaired sentence-initial letter must leave the cursor at the end of the edited text.",
        7, input.cursor);
    assertEquals("Repairing the delayed sentence-initial letter must delete one verified suffix.",
        1, input.deleteSurroundingCalls);
    assertEquals("Repairing the delayed sentence-initial letter must commit one replacement suffix.",
        1, input.commitTextCalls);
  }

  @Test
  public void sentence_initial_rewrite_keeps_up_with_extra_letters_before_delay()
  {
    MutableInputConnection input = new MutableInputConnection("Done. ", 0);
    RecordingCallback callback = new RecordingCallback(input);
    Autocapitalisation autocap = autocap(callback, input,
        TextUtils.CAP_MODE_SENTENCES);

    autocap.typed("t");
    input.editorInsert("t");
    autocap.typed("e");
    input.editorInsert("e");
    autocap.delayed_callback.run();

    assertEquals("Fast typing after a period must capitalize the first letter without losing later letters.",
        "Done. Te", input.text.toString());
    assertEquals("The rewrite must preserve the cursor after the complete fast-typed word.",
        8, input.cursor);
    assertEquals("A delayed sentence repair must still be one atomic suffix deletion.",
        1, input.deleteSurroundingCalls);
    assertEquals("A delayed sentence repair must still be one atomic suffix commit.",
        1, input.commitTextCalls);
  }

  @Test
  public void moved_cursor_or_changed_suffix_cannot_apply_a_stale_sentence_rewrite()
  {
    MutableInputConnection movedInput = new MutableInputConnection("Done. ", 0);
    RecordingCallback movedCallback = new RecordingCallback(movedInput);
    Autocapitalisation movedAutocap = autocap(movedCallback, movedInput,
        TextUtils.CAP_MODE_SENTENCES);

    movedAutocap.typed("t");
    movedInput.editorInsert("t");
    int oldCursor = movedInput.cursor;
    movedInput.moveCursorTo(oldCursor - 1);
    notifyCursorMove(movedAutocap, oldCursor, movedInput.cursor);
    movedAutocap.delayed_callback.run();

    assertEquals("Moving the cursor while a rewrite waits must leave the original sentence text untouched.",
        "Done. t", movedInput.text.toString());
    assertEquals("Moving the cursor must keep the user's new cursor position.",
        6, movedInput.cursor);
    assertEquals("A cursor move must cancel the pending rewrite before any deletion.",
        0, movedInput.deleteSurroundingCalls);
    assertEquals("A cursor move must cancel the pending rewrite before any commit.",
        0, movedInput.commitTextCalls);

    MutableInputConnection changedInput = new MutableInputConnection("Done. ", 0);
    RecordingCallback changedCallback = new RecordingCallback(changedInput);
    Autocapitalisation changedAutocap = autocap(changedCallback, changedInput,
        TextUtils.CAP_MODE_SENTENCES);

    changedAutocap.typed("t");
    changedInput.editorInsert("x");
    changedAutocap.delayed_callback.run();

    assertEquals("An editor change that no longer matches the typed suffix must not be overwritten.",
        "Done. x", changedInput.text.toString());
    assertEquals("A stale suffix rejection must leave the cursor where the editor put it.",
        7, changedInput.cursor);
    assertEquals("A stale suffix rejection must not delete editor text.",
        0, changedInput.deleteSurroundingCalls);
    assertEquals("A stale suffix rejection must not commit replacement text.",
        0, changedInput.commitTextCalls);
  }

  private static Autocapitalisation autocap(RecordingCallback callback,
      int requestedMode, int cursorMode)
  {
    return autocap(callback, new MutableInputConnection("", cursorMode),
        requestedMode);
  }

  private static Autocapitalisation autocap(RecordingCallback callback,
      MutableInputConnection input, int requestedMode)
  {
    Autocapitalisation autocap = new Autocapitalisation(
        new Handler(Looper.getMainLooper()), callback);
    autocap._enabled = true;
    autocap._caps_mode = requestedMode;
    autocap._ic = input;
    autocap._cursor = input.cursor;
    return autocap;
  }

  private static void notifyCursorMove(Autocapitalisation autocap,
      int oldCursor, int newCursor)
  {
    try
    {
      for (java.lang.reflect.Method method :
          Autocapitalisation.class.getDeclaredMethods())
      {
        if (!method.getName().equals("selection_updated"))
          continue;
        if (method.getParameterTypes().length == 5)
        {
          method.invoke(autocap, oldCursor, newCursor, newCursor, newCursor,
              newCursor);
          return;
        }
        if (method.getParameterTypes().length == 2)
        {
          method.invoke(autocap, oldCursor, newCursor);
          return;
        }
      }
      throw new AssertionError("Autocapitalisation has no supported selection update seam.");
    }
    catch (AssertionError e)
    {
      throw e;
    }
    catch (Exception e)
    {
      throw new AssertionError("Could not notify autocapitalisation about a cursor move: " + e);
    }
  }

  private static class RecordingCallback implements Autocapitalisation.Callback
  {
    boolean shouldEnable = false;
    final MutableInputConnection input;

    RecordingCallback()
    {
      this(null);
    }

    RecordingCallback(MutableInputConnection input_)
    {
      input = input_;
    }

    @Override
    public void update_shift_state(boolean should_enable, boolean should_disable)
    {
      shouldEnable = should_enable;
    }

    /*
     * Declared without @Override so this focused test continues to compile
     * before the production callback contract is added.
     */
    public boolean replace_recent_text(String expectedSuffix,
        String replacementSuffix)
    {
      if (input == null || input.selectionStart != input.selectionEnd)
        return false;
      if (!expectedSuffix.equals(input.getTextBeforeCursor(
              expectedSuffix.length(), 0).toString()))
        return false;
      input.beginBatchEdit();
      input.deleteSurroundingText(expectedSuffix.length(), 0);
      input.commitText(replacementSuffix, 1);
      input.endBatchEdit();
      return true;
    }
  }

  private static class MutableInputConnection extends BaseInputConnection
  {
    final StringBuilder text = new StringBuilder();
    int cursor;
    int selectionStart;
    int selectionEnd;
    int cursorMode;
    int commitTextCalls;
    int deleteSurroundingCalls;

    MutableInputConnection(String initial, int cursorMode_)
    {
      super(new View(RuntimeEnvironment.getApplication()), false);
      text.append(initial);
      cursor = text.length();
      selectionStart = cursor;
      selectionEnd = cursor;
      cursorMode = cursorMode_;
    }

    void editorInsert(String value)
    {
      int start = Math.min(selectionStart, selectionEnd);
      int end = Math.max(selectionStart, selectionEnd);
      text.replace(start, end, value);
      cursor = start + value.length();
      selectionStart = cursor;
      selectionEnd = cursor;
    }

    void moveCursorTo(int position)
    {
      cursor = position;
      selectionStart = position;
      selectionEnd = position;
    }

    @Override
    public int getCursorCapsMode(int reqModes)
    {
      return cursorMode & reqModes;
    }

    @Override public CharSequence getTextBeforeCursor(int length, int flags)
    {
      return text.substring(Math.max(0, cursor - length), cursor);
    }

    @Override public CharSequence getTextAfterCursor(int length, int flags)
    {
      return text.substring(cursor, Math.min(text.length(), cursor + length));
    }

    @Override public CharSequence getSelectedText(int flags)
    {
      return text.substring(Math.min(selectionStart, selectionEnd),
          Math.max(selectionStart, selectionEnd));
    }

    @Override public boolean setSelection(int start, int end)
    {
      if (start < 0 || end < 0 || start > text.length() || end > text.length())
        return false;
      selectionStart = start;
      selectionEnd = end;
      cursor = end;
      return true;
    }

    @Override
    public boolean deleteSurroundingText(int before, int after)
    {
      deleteSurroundingCalls++;
      int start = Math.max(0, cursor - before);
      int end = Math.min(text.length(), cursor + after);
      text.delete(start, end);
      cursor = start;
      selectionStart = cursor;
      selectionEnd = cursor;
      return true;
    }

    @Override
    public boolean commitText(CharSequence value, int newCursorPosition)
    {
      commitTextCalls++;
      int start = Math.min(selectionStart, selectionEnd);
      int end = Math.max(selectionStart, selectionEnd);
      text.replace(start, end, value.toString());
      cursor = start + value.length();
      selectionStart = cursor;
      selectionEnd = cursor;
      return true;
    }

    @Override public boolean beginBatchEdit() { return true; }
    @Override public boolean endBatchEdit() { return true; }
  }
}
