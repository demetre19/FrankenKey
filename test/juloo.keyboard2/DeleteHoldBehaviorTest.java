package juloo.keyboard2;

import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
public class DeleteHoldBehaviorTest
{
  @Test
  public void held_backspace_repeats_single_codepoint_delete_only()
  {
    for (int holdCount : new int[]{1, 6, 7, 20})
    {
      FakeReceiver receiver = new FakeReceiver("https://example.test/\uD83D\uDE00");
      KeyEventHandler handler = new KeyEventHandler(receiver, null);

      handler.key_hold(backspace(), Pointers.Modifiers.EMPTY, holdCount);

      assertEquals("Backspace hold repeat " + holdCount
          + " must delete exactly one Unicode codepoint for web/url inputs.",
          "https://example.test/", receiver.input.text.toString());
      assertEquals("Backspace hold repeat " + holdCount
          + " must use one semantic codepoint deletion, not a UTF-16 char delete.",
          1, receiver.input.codePointDeleteBefore);
      assertEquals("Backspace hold repeat " + holdCount
          + " must not fall back after a successful codepoint deletion.",
          0, receiver.input.deleteSurroundingTextCalls);
      assertTrue("Backspace hold repeat " + holdCount
          + " must not send Ctrl+Backspace or any synthetic KEYCODE_DEL burst.",
          receiver.input.keyEvents.isEmpty());
    }
  }

  @Test
  public void selected_backspace_commits_empty_text_without_synthetic_key_events()
  {
    FakeReceiver receiver = new FakeReceiver("https://example.test/path");
    KeyEventHandler handler = new KeyEventHandler(receiver, null);
    assertTrue(receiver.input.setSelection("https://".length(),
          "https://example.test".length()));

    handler.handle_backspace();

    assertEquals("Backspace over a selected URL segment must delete the selection semantically.",
        "https:///path", receiver.input.text.toString());
    assertEquals("Selected text deletion must use commitText(\"\", 1) so web/url fields do not receive unreliable synthetic DEL events.",
        1, receiver.input.commitTextCalls);
    assertEquals("", receiver.input.lastCommittedText.toString());
    assertEquals(1, receiver.input.lastCommitNewCursorPosition);
    assertEquals("Selection deletion must not first delete a surrounding UTF-16 char.",
        0, receiver.input.deleteSurroundingTextCalls);
    assertEquals("Selection deletion must not use a normal codepoint backspace.",
        0, receiver.input.codePointDeleteCalls);
    assertTrue("Selection deletion must not emit synthetic KEYCODE_DEL events.",
        receiver.input.keyEvents.isEmpty());
  }

  @Test
  public void handle_backspace_prefers_codepoint_delete_and_only_then_utf16_fallback()
  {
    FakeReceiver receiver = new FakeReceiver("https://example.test/\uD83D\uDE00");
    KeyEventHandler handler = new KeyEventHandler(receiver, null);

    handler.handle_backspace();

    assertEquals("Semantic codepoint deletion must remove the whole emoji, not one surrogate char.",
        "https://example.test/", receiver.input.text.toString());
    assertEquals(1, receiver.input.codePointDeleteCalls);
    assertEquals(1, receiver.input.codePointDeleteBefore);
    assertEquals(0, receiver.input.codePointDeleteAfter);
    assertEquals("Successful codepoint deletion must not also call UTF-16 deleteSurroundingText.",
        0, receiver.input.deleteSurroundingTextCalls);
    assertTrue("Chrome/web URL fields need semantic deletion, not synthetic KEYCODE_DEL events.",
        receiver.input.keyEvents.isEmpty());

    receiver = new FakeReceiver("ab");
    receiver.input.codePointDeleteResult = false;
    handler = new KeyEventHandler(receiver, null);

    handler.handle_backspace();

    assertEquals("If the editor rejects codepoint deletion, the handler must fall back to the legacy UTF-16 delete.",
        "a", receiver.input.text.toString());
    assertEquals(1, receiver.input.codePointDeleteCalls);
    assertEquals(1, receiver.input.deleteSurroundingTextCalls);
    assertEquals(1, receiver.input.deleteBefore);
    assertTrue(receiver.input.keyEvents.isEmpty());
  }

  @Test
  public void false_codepoint_result_after_mutation_must_not_delete_twice()
  {
    FakeReceiver receiver = new FakeReceiver("omp ");
    receiver.input.codePointDeleteResult = false;
    receiver.input.codePointDeleteMutatesBeforeFalse = true;
    KeyEventHandler handler = new KeyEventHandler(receiver, null);

    handler.handle_backspace();

    assertEquals("An editor that performs codepoint deletion but reports false must lose only the requested trailing space.",
        "omp", receiver.input.text.toString());
    assertEquals(1, receiver.input.codePointDeleteCalls);
    assertEquals("Mutation must be verified before any destructive UTF-16 fallback runs.",
        0, receiver.input.deleteSurroundingTextCalls);
    assertTrue("A verified semantic deletion must not emit a second synthetic DEL.",
        receiver.input.keyEvents.isEmpty());
  }

  @Test
  public void handle_backspace_falls_back_to_del_key_when_web_input_acknowledges_without_mutating()
  {
    FakeReceiver receiver = new FakeReceiver("https://example.test/ab");
    receiver.input.codePointDeleteMutatesText = false;
    KeyEventHandler handler = new KeyEventHandler(receiver, null);

    handler.handle_backspace();

    assertEquals("Chrome/WebView-style inputs can acknowledge semantic codepoint delete without changing visible text; the fallback DEL key must do the visible deletion.",
        "https://example.test/a", receiver.input.text.toString());
    assertEquals("The handler must still try the semantic codepoint delete first.",
        1, receiver.input.codePointDeleteCalls);
    assertEquals(1, receiver.input.codePointDeleteBefore);
    assertEquals(0, receiver.input.codePointDeleteAfter);
    assertEquals("A successful-but-no-op codepoint delete must not be followed by UTF-16 deleteSurroundingText, which is the same unreliable adapter boundary.",
        0, receiver.input.deleteSurroundingTextCalls);
    assertEquals("The visible deletion must come from the synthetic DEL down/up fallback.",
        2, receiver.input.keyEvents.size());
    assertEquals(KeyEvent.ACTION_DOWN, receiver.input.keyEvents.get(0).getAction());
    assertEquals(KeyEvent.KEYCODE_DEL, receiver.input.keyEvents.get(0).getKeyCode());
    assertEquals(KeyEvent.ACTION_UP, receiver.input.keyEvents.get(1).getAction());
    assertEquals(KeyEvent.KEYCODE_DEL, receiver.input.keyEvents.get(1).getKeyCode());
  }

  @Test
  public void termux_backspace_uses_del_key_without_trusting_semantic_deletion()
  {
    FakeReceiver receiver = new FakeReceiver("ab");
    receiver.editorInfo.inputType = android.text.InputType.TYPE_NULL;
    receiver.editorInfo.packageName = "com.termux";
    receiver.input.codePointDeleteMutatesText = false;
    KeyEventHandler handler = new KeyEventHandler(receiver, null);

    handler.handle_backspace();

    assertEquals("Termux Backspace must visibly delete through its raw DEL event contract.",
        "a", receiver.input.text.toString());
    assertEquals("Termux must bypass semantic deletion methods that acknowledge without mutating.",
        0, receiver.input.codePointDeleteCalls);
    assertEquals(0, receiver.input.deleteSurroundingTextCalls);
    assertEquals("One Termux Backspace must send exactly DEL down and DEL up.",
        2, receiver.input.keyEvents.size());
  }

  @Test
  public void delete_words_left_slider_selects_resizes_and_commits_current_word_selection()
  {
    FakeReceiver receiver = new FakeReceiver("alpha beta gamma");
    KeyEventHandler handler = new KeyEventHandler(receiver, null);
    KeyValue grow = KeyValue.sliderKey(KeyValue.Slider.Delete_words_left, 1);
    KeyValue shrink = KeyValue.sliderKey(KeyValue.Slider.Delete_words_left, -1);
    KeyValue release = KeyValue.sliderKey(KeyValue.Slider.Delete_words_left, 0);

    handler.key_down(grow, true);

    assertEquals("Initial backspace swipe must select the previous word.",
        "gamma", receiver.input.selectedText());
    assertEquals(true, receiver.selectionStates.get(0));

    handler.key_hold(grow, Pointers.Modifiers.EMPTY, 0);

    assertEquals("Positive slider delta must grow to the next word chunk.",
        "beta gamma", receiver.input.selectedText());
    assertEquals(true, receiver.selectionStates.get(1));

    handler.key_hold(shrink, Pointers.Modifiers.EMPTY, 0);

    assertEquals("Negative slider delta must shrink the pending deletion.",
        "gamma", receiver.input.selectedText());
    assertEquals(true, receiver.selectionStates.get(2));

    handler.key_up(release, Pointers.Modifiers.EMPTY, null);

    assertEquals("Release with repeat 0 must delete the currently selected chunk.",
        "alpha beta ", receiver.input.text.toString());
    assertEquals("Commit must collapse the selection where the deleted chunk started.",
        receiver.input.selectionStart, receiver.input.selectionEnd);
    assertEquals(false, receiver.selectionStates.get(3));
  }

  @Test
  public void timed_key_repeat_accelerates_while_held()
      throws Exception
  {
    String source = readSource("srcs/juloo.keyboard2/Pointers.java");
    String repeatInterval = methodBody(source, "private long repeatInterval");
    String handleLongPress = methodBody(source, "private void handleLongPress");

    assertTrue("Held key repeat must shrink the interval as holdCount grows.",
        repeatInterval.contains("interval * 93 / 100"));
    assertTrue("Held key repeat needs a floor so it never schedules too aggressively.",
        repeatInterval.contains("Math.max(35"));
    assertTrue("Hold count must advance before scheduling the next accelerated repeat.",
        handleLongPress.indexOf("ptr.holdCount++")
        < handleLongPress.indexOf("repeatInterval(ptr)"));
  }

  @Test
  public void delete_words_left_slider_uses_gradual_accelerating_pacing()
      throws Exception
  {
    String source = readSource("srcs/juloo.keyboard2/Pointers.java");
    String onTouchMove = methodBody(source,
        "public void onTouchMove(Pointer ptr, float x, float y)");
    String pacedDeleteWordsDelta = methodBody(source,
        "private int pacedDeleteWordsDelta");
    String slidingConstructor = methodBody(source,
        "public Sliding(float x, float y, int dirx, int diry, KeyValue.Slider s)");

    int deleteWordsGate =
      onTouchMove.indexOf("slider == KeyValue.Slider.Delete_words_left");
    int pacedDeltaCall =
      onTouchMove.indexOf("pacedDeleteWordsDelta", deleteWordsGate);
    int emitStep = onTouchMove.indexOf("_handler.onPointerHold", pacedDeltaCall);
    assertOrdered("Delete-words slider steps must pass through the dedicated pacing helper before emitting repeat deltas.",
        deleteWordsGate, pacedDeltaCall, emitStep);

    assertTrue("Delete-words pacing must remain anchored by Config.deleteWordsInterval in milliseconds.",
        pacedDeleteWordsDelta.contains("_config.deleteWordsInterval"));
    assertTrue("Delete-words pacing must wait until the current interval has elapsed before emitting another step.",
        pacedDeleteWordsDelta.contains("elapsed")
        && pacedDeleteWordsDelta.contains("return 0"));
    assertTrue("Fleksy-style gradual delete must speed up from the slider movement speed instead of using one fixed word interval.",
        pacedDeleteWordsDelta.contains("speed")
        && pacedDeleteWordsDelta.contains("deleteWordsInterval"));
    assertTrue("Fleksy-style gradual delete must still cap emitted steps by elapsed paced intervals so a single move cannot burst-delete an unbounded selection.",
        pacedDeleteWordsDelta.contains("Math.min")
        && pacedDeleteWordsDelta.contains("Math.abs(delta)"));
    assertFalse("Delete-words slider pacing must not use the generic held-key repeat interval.",
        pacedDeleteWordsDelta.contains("repeatInterval")
        || pacedDeleteWordsDelta.contains("longPressInterval"));
    assertTrue("Starting a delete-words slide must initialize its pacing clock so opening the slider cannot immediately burst-delete words.",
        slidingConstructor.contains("s == KeyValue.Slider.Delete_words_left")
        && slidingConstructor.contains("System.currentTimeMillis()"));
  }

  private static void assertOrdered(String message, int... indexes)
  {
    int previous = -1;
    for (int index : indexes)
    {
      assertTrue(message + " missing index", index >= 0);
      assertTrue(message + " out of order", index > previous);
      previous = index;
    }
  }

  private static String readSource(String path)
      throws Exception
  {
    return new String(Files.readAllBytes(Paths.get(path)),
        StandardCharsets.UTF_8);
  }

  private static String methodBody(String source, String methodSignature)
  {
    int start = source.indexOf(methodSignature);
    assertTrue("Missing method: " + methodSignature, start >= 0);
    int open = source.indexOf('{', start);
    assertTrue(open >= 0);
    int depth = 0;
    for (int i = open; i < source.length(); ++i)
    {
      char c = source.charAt(i);
      if (c == '{')
        ++depth;
      else if (c == '}')
      {
        --depth;
        if (depth == 0)
          return source.substring(open + 1, i);
      }
    }
    fail("Unclosed method: " + methodSignature);
    return "";
  }

  private static KeyValue backspace()
  {
    return KeyValue.getSpecialKeyByName("backspace");
  }

  private static class FakeReceiver implements KeyEventHandler.IReceiver
  {
    final RecordingInputConnection input = new RecordingInputConnection();
    final android.view.inputmethod.EditorInfo editorInfo =
      new android.view.inputmethod.EditorInfo();
    FakeReceiver() {}


    FakeReceiver(String text)
    {
      input.resetText(text);
    }
    final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void handle_event_key(KeyValue.Event ev) {}

    @Override
    public void set_shift_state(boolean state, boolean lock) {}

    @Override
    public void set_compose_pending(boolean pending) {}

    @Override
    public void selection_state_changed(boolean selection_is_ongoing)
    {
      selectionStates.add(selection_is_ongoing);
    }

    @Override
    public RecordingInputConnection getCurrentInputConnection()
    {
      return input;
    }

    @Override
    public android.view.inputmethod.EditorInfo getCurrentInputEditorInfo()
    {
      return editorInfo;
    }

    @Override
    public Handler getHandler()
    {
      return handler;
    }


    final List<Boolean> selectionStates = new ArrayList<>();

  }

  private static class RecordingInputConnection extends BaseInputConnection
  {
    int deleteBefore = 0;
    int deleteAfter = 0;
    int deleteSurroundingTextCalls = 0;
    int codePointDeleteBefore = 0;
    int codePointDeleteAfter = 0;
    int codePointDeleteCalls = 0;
    int commitTextCalls = 0;
    int lastCommitNewCursorPosition = 0;
    int finishComposingTextCalls = 0;
    CharSequence lastCommittedText = "";
    boolean codePointDeleteResult = true;
    boolean codePointDeleteMutatesText = true;
    boolean codePointDeleteMutatesBeforeFalse = false;
    int selectionStart = 0;
    int selectionEnd = 0;
    final StringBuilder text = new StringBuilder();
    final List<KeyEvent> keyEvents = new ArrayList<>();

    RecordingInputConnection()
    {
      super(new View(RuntimeEnvironment.getApplication()), false);
    }

    void resetText(String value)
    {
      text.setLength(0);
      text.append(value);
      selectionStart = text.length();
      selectionEnd = text.length();
    }

    String selectedText()
    {
      int start = Math.min(selectionStart, selectionEnd);
      int end = Math.max(selectionStart, selectionEnd);
      return text.substring(start, end);
    }

    @Override
    public CharSequence getSelectedText(int flags)
    {
      return selectedText();
    }

    @Override
    public boolean finishComposingText()
    {
      ++finishComposingTextCalls;
      return true;
    }

    @Override
    public boolean deleteSurroundingText(int beforeLength, int afterLength)
    {
      ++deleteSurroundingTextCalls;
      deleteBefore += beforeLength;
      deleteAfter += afterLength;
      deleteCodeUnitsAroundSelection(beforeLength, afterLength);
      return true;
    }

    @Override
    public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength)
    {
      ++codePointDeleteCalls;
      codePointDeleteBefore += beforeLength;
      codePointDeleteAfter += afterLength;
      if (!codePointDeleteResult)
      {
        if (codePointDeleteMutatesBeforeFalse)
          deleteCodePointsAroundSelection(beforeLength, afterLength);
        return false;
      }
      if (codePointDeleteMutatesText)
        deleteCodePointsAroundSelection(beforeLength, afterLength);
      return true;
    }

    @Override
    public ExtractedText getExtractedText(ExtractedTextRequest request, int flags)
    {
      ExtractedText extracted = new ExtractedText();
      extracted.text = text.toString();
      extracted.startOffset = 0;
      extracted.selectionStart = selectionStart;
      extracted.selectionEnd = selectionEnd;
      return extracted;
    }

    @Override
    public boolean setSelection(int start, int end)
    {
      if (start < 0 || end < 0 || start > text.length() || end > text.length())
        return false;
      selectionStart = start;
      selectionEnd = end;
      return true;
    }

    @Override
    public boolean commitText(CharSequence committedText, int newCursorPosition)
    {
      ++commitTextCalls;
      lastCommittedText = committedText;
      lastCommitNewCursorPosition = newCursorPosition;
      int start = Math.min(selectionStart, selectionEnd);
      int end = Math.max(selectionStart, selectionEnd);
      text.replace(start, end, committedText.toString());
      selectionStart = start + committedText.length();
      selectionEnd = selectionStart;
      return true;
    }

    @Override
    public boolean sendKeyEvent(KeyEvent event)
    {
      keyEvents.add(event);
      if (event.getKeyCode() == KeyEvent.KEYCODE_DEL
          && event.getAction() == KeyEvent.ACTION_DOWN)
        deleteCodePointsAroundSelection(1, 0);
      return true;
    }

    private void deleteCodeUnitsAroundSelection(int beforeLength, int afterLength)
    {
      int start = Math.min(selectionStart, selectionEnd);
      int end = Math.max(selectionStart, selectionEnd);
      int deleteStart = Math.max(0, start - beforeLength);
      int deleteEnd = Math.min(text.length(), end + afterLength);
      text.delete(deleteStart, start);
      deleteEnd -= start - deleteStart;
      text.delete(end - (start - deleteStart), deleteEnd);
      selectionStart = deleteStart;
      selectionEnd = deleteStart;
    }

    private void deleteCodePointsAroundSelection(int beforeLength, int afterLength)
    {
      int start = Math.min(selectionStart, selectionEnd);
      int end = Math.max(selectionStart, selectionEnd);
      int deleteStart = start;
      for (int i = 0; i < beforeLength && deleteStart > 0; ++i)
        deleteStart = text.offsetByCodePoints(deleteStart, -1);
      int deleteEnd = end;
      for (int i = 0; i < afterLength && deleteEnd < text.length(); ++i)
        deleteEnd = text.offsetByCodePoints(deleteEnd, 1);
      text.delete(deleteStart, deleteEnd);
      selectionStart = deleteStart;
      selectionEnd = deleteStart;
    }
  }
}
