package juloo.keyboard2;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import java.util.ArrayList;
import java.util.List;
import juloo.keyboard2.suggestions.PersonalizationStore;
import juloo.keyboard2.suggestions.Suggestions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
public class FleksyLearningGestureTest
{
  private SharedPreferences _prefs;

  @Before
  public void setUp()
  {
    _prefs = RuntimeEnvironment.getApplication()
      .getSharedPreferences("fleksy_learning_gesture_test", Context.MODE_PRIVATE);
    _prefs.edit().clear().commit();
  }

  @After
  public void tearDown()
  {
    _prefs.edit().clear().commit();
  }

  @Test
  public void global_vertical_swipes_dispatch_keyboard_learning_hooks_instead_of_side_key_values()
      throws Exception
  {
    Config config = testConfig("", true, true);
    config.swipe_dist_px = 8f;
    config.longPressTimeout = 60000;
    RecordingPointerHandler pointerHandler = new RecordingPointerHandler();
    Pointers pointers = new Pointers(pointerHandler, config);
    KeyboardData.Key key = verticalSideLabelKey();

    pointers.onTouchDown(50f, 50f, 1, key, null);
    pointers.onTouchMove(50f, 20f, 1);
    pointers.onTouchUp(1);

    assertEquals("A keyboard-wide upward swipe from any key must dispatch the learn-current-word hook.",
        1, pointerHandler.keyboardSwipeUpCount);
    assertEquals(0, pointerHandler.keyboardSwipeDownCount);
    assertFalse("The upward global learning gesture must not release the key's north side-label value as typed text.",
        pointerHandler.releasedValues.contains("b"));

    pointers.onTouchDown(50f, 50f, 2, key, null);
    pointers.onTouchMove(50f, 80f, 2);
    pointers.onTouchUp(2);

    assertEquals(1, pointerHandler.keyboardSwipeUpCount);
    assertEquals("A keyboard-wide downward swipe from any key must dispatch the unlearn-current-word hook.",
        1, pointerHandler.keyboardSwipeDownCount);
    assertFalse("The downward global learning gesture must not release the key's south side-label value as typed text.",
        pointerHandler.releasedValues.contains("c"));
  }

  @Test
  public void keyboard_swipe_up_learns_current_typed_word_not_visible_candidate_and_publishes_learned_feedback()
      throws Exception
  {
    Harness harness = startedHarness("caz", true, true);
    harness.config.personalization.record_word("cazoo");
    harness.handler.currently_typed_word("caz", null);
    harness.receiver.publishCount = 0;

    harness.handler.keyboard_swiped_up();

    assertTrue("Global swipe-up must learn the current typed token from CurrentlyTypedWord, even when a different learned prefix candidate is visible.",
        harness.config.personalization.is_learned("caz"));
    assertTrue("Learning the typed token must not unlearn or replace the visible candidate that happened to share its prefix.",
        harness.config.personalization.is_learned("cazoo"));
    assertTrue("Learning through the global gesture must refresh suggestions immediately so the candidate-row dictionary icon changes without another keystroke.",
        harness.receiver.publishCount > 0);
    assertEquals("The immediate refresh after learning must expose the learned dictionary-icon feedback state, not a plain learned candidate.",
        Suggestions.LearnFeedback.LEARNED, harness.receiver.learnFeedback);
    assertEquals("caz", harness.receiver.learnFeedbackWord);
  }

  @Test
  public void keyboard_swipe_down_unlearns_current_typed_word_and_publishes_forgot_feedback()
      throws Exception
  {
    Harness harness = startedHarness("cazoo", true, true);
    harness.config.personalization.record_word("cazoo");
    harness.receiver.publishCount = 0;

    harness.handler.keyboard_swiped_down();

    assertFalse("Global swipe-down must unlearn the current typed token from CurrentlyTypedWord.",
        harness.config.personalization.is_learned("cazoo"));
    assertTrue("Unlearning through the global gesture must refresh suggestions immediately so the candidate-row dictionary icon changes without another keystroke.",
        harness.receiver.publishCount > 0);
    assertEquals("The immediate refresh after unlearning must expose the forgot dictionary-icon feedback state.",
        Suggestions.LearnFeedback.FORGOT, harness.receiver.learnFeedback);
    assertEquals("cazoo", harness.receiver.learnFeedbackWord);
  }

  @Test
  public void keyboard_learning_gestures_do_not_mutate_or_refresh_when_suggestions_or_editor_gate_is_disabled()
      throws Exception
  {
    assertLearningGesturesAreGated("disabled suggestions", false, true);
    assertLearningGesturesAreGated("unsafe editor", true, false);
  }

  private void assertLearningGesturesAreGated(String caseName,
      boolean suggestionsEnabled, boolean safeEditor)
      throws Exception
  {
    Harness learnHarness = startedHarness("cazoo", suggestionsEnabled, safeEditor);
    learnHarness.receiver.publishCount = 0;

    learnHarness.handler.keyboard_swiped_up();

    assertFalse(caseName + ": swipe-up must not learn while typing assistance is gated off.",
        learnHarness.config.personalization.is_learned("cazoo"));
    assertEquals(caseName + ": gated swipe-up must not publish learn feedback for a mutation that was refused.",
        0, learnHarness.receiver.publishCount);

    Harness unlearnHarness = startedHarness("cazoo", suggestionsEnabled, safeEditor);
    unlearnHarness.config.personalization.record_word("cazoo");
    unlearnHarness.receiver.publishCount = 0;

    unlearnHarness.handler.keyboard_swiped_down();

    assertTrue(caseName + ": swipe-down must not unlearn while typing assistance is gated off.",
        unlearnHarness.config.personalization.is_learned("cazoo"));
    assertEquals(caseName + ": gated swipe-down must not publish forgot feedback for a mutation that was refused.",
        0, unlearnHarness.receiver.publishCount);
  }

  private Harness startedHarness(String typedWord, boolean suggestionsEnabled,
      boolean safeEditor)
      throws Exception
  {
    Config config = testConfig(typedWord, suggestionsEnabled, safeEditor);
    RecordingReceiver receiver = new RecordingReceiver(typedWord);
    Suggestions suggestions = new Suggestions(receiver, config);
    KeyEventHandler handler = new KeyEventHandler(receiver, suggestions);
    config.handler = handler;
    handler.started(config);
    return new Harness(config, receiver, handler);
  }

  private Config testConfig(String typedWord, boolean suggestionsEnabled,
      boolean safeEditor)
      throws Exception
  {
    java.lang.reflect.Constructor<Config> ctor =
      Config.class.getDeclaredConstructor(SharedPreferences.class,
          Resources.class, Boolean.class,
          juloo.keyboard2.dict.Dictionaries.class);
    ctor.setAccessible(true);
    Config config = ctor.newInstance(_prefs, testResources(), Boolean.FALSE, null);
    config.suggestions_enabled = suggestionsEnabled;
    config.editor_config.should_show_candidates_view = true;
    config.editor_config.should_use_typing_assistance = safeEditor;
    config.editor_config.initial_text_before_cursor = typedWord;
    config.editor_config.initial_text_after_cursor = "";
    config.editor_config.initial_sel_start = typedWord.length();
    config.editor_config.initial_sel_end = typedWord.length();
    config.personalization = PersonalizationStore.empty();
    config.current_dictionary = null;
    config.current_hunspell = null;
    return config;
  }

  private static Resources testResources()
  {
    Resources base = RuntimeEnvironment.getApplication().getResources();
    return new TestResources(base);
  }

  private static KeyboardData.Key verticalSideLabelKey()
      throws Exception
  {
    KeyboardData keyboard = KeyboardData.load_string_exn(
        "<keyboard bottom_row=\"false\" width=\"1\">"
        + "<row>"
        + "<key c=\"a\" n=\"b\" s=\"c\"/>"
        + "</row>"
        + "</keyboard>");
    return keyboard.rows.get(0).keys.get(0);
  }

  private static final class Harness
  {
    final Config config;
    final RecordingReceiver receiver;
    final KeyEventHandler handler;

    Harness(Config config_, RecordingReceiver receiver_, KeyEventHandler handler_)
    {
      config = config_;
      receiver = receiver_;
      handler = handler_;
    }
  }

  private static final class RecordingPointerHandler
      implements Pointers.IPointerEventHandler
  {
    int keyboardSwipeUpCount = 0;
    int keyboardSwipeDownCount = 0;
    final List<String> releasedValues = new ArrayList<String>();

    @Override
    public KeyValue modifyKey(KeyValue k, Pointers.Modifiers mods)
    {
      return k;
    }

    @Override
    public void onPointerDown(KeyValue k, boolean isSwipe) {}

    @Override
    public void onPointerUp(KeyValue k, Pointers.Modifiers mods,
        TouchTrace.Entry touch)
    {
      if (k != null && k.getKind() == KeyValue.Kind.Char)
        releasedValues.add(String.valueOf(k.getChar()));
    }

    @Override
    public void onPointerFlagsChanged(boolean shouldVibrate) {}

    @Override
    public void onPointerHold(KeyValue k, Pointers.Modifiers mods,
        int holdCount) {}

    @Override
    public void onPointerCancel(KeyValue k, Pointers.Modifiers mods) {}

    @Override
    public void onKeyboardSwipeUp()
    {
      keyboardSwipeUpCount++;
    }

    @Override
    public void onKeyboardSwipeDown()
    {
      keyboardSwipeDownCount++;
    }
  }

  private static final class RecordingReceiver implements KeyEventHandler.IReceiver
  {
    final Handler handler = new Handler(Looper.getMainLooper());
    final RecordingInputConnection input = new RecordingInputConnection();
    int publishCount = 0;
    int count = -1;
    String[] suggestions = new String[Suggestions.MAX_COUNT];
    Suggestions.Source[] sources = new Suggestions.Source[Suggestions.MAX_COUNT];
    Suggestions.LearnFeedback learnFeedback = Suggestions.LearnFeedback.NONE;
    String learnFeedbackWord = null;

    RecordingReceiver(String text)
    {
      input.resetText(text);
    }

    @Override
    public void handle_event_key(KeyValue.Event ev) {}

    @Override
    public void set_shift_state(boolean state, boolean lock) {}

    @Override
    public void set_compose_pending(boolean pending) {}

    @Override
    public void selection_state_changed(boolean selection_is_ongoing) {}

    @Override
    public RecordingInputConnection getCurrentInputConnection()
    {
      return input;
    }

    @Override
    public Handler getHandler()
    {
      return handler;
    }

    @Override
    public void set_suggestions(Suggestions published)
    {
      publishCount++;
      count = published.count;
      suggestions = java.util.Arrays.copyOf(published.suggestions,
          published.suggestions.length);
      sources = java.util.Arrays.copyOf(published.sources,
          published.sources.length);
      learnFeedback = published.learn_feedback;
      learnFeedbackWord = published.learn_feedback_word;
    }
  }

  private static final class RecordingInputConnection extends BaseInputConnection
  {
    final StringBuilder text = new StringBuilder();
    int selectionStart = 0;
    int selectionEnd = 0;

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
    public CharSequence getTextBeforeCursor(int n, int flags)
    {
      int start = Math.max(0, selectionStart - n);
      return text.substring(start, selectionStart);
    }

    @Override
    public boolean commitText(CharSequence committedText, int newCursorPosition)
    {
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
      return true;
    }
  }

  private static final class TestResources extends Resources
  {
    TestResources(Resources base)
    {
      super(base.getAssets(), base.getDisplayMetrics(), base.getConfiguration());
    }

    @Override
    public float getDimension(int id)
    {
      return 1f;
    }
  }
}
