package juloo.keyboard2;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import juloo.keyboard2.suggestions.Decoder;
import juloo.keyboard2.suggestions.PersonalizationStore;
import juloo.keyboard2.suggestions.SharedDecoder;
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
  private final List<SharedDecoder> _decoders = new ArrayList<SharedDecoder>();

  @Before
  public void setUp()
  {
    _prefs = RuntimeEnvironment.getApplication().getSharedPreferences(
        "fleksy_learning_gesture_test", Context.MODE_PRIVATE);
    _prefs.edit().clear().commit();
  }

  @After
  public void tearDown()
  {
    for (SharedDecoder decoder : _decoders)
      decoder.close();
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
    pointers.onTouchDown(50f, 50f, 2, key, null);
    pointers.onTouchMove(50f, 80f, 2);
    pointers.onTouchUp(2);

    assertEquals("A keyboard-wide upward swipe from any key must dispatch the learn-current-word hook.",
        1, pointerHandler.keyboardSwipeUpCount);
    assertEquals("A keyboard-wide downward swipe from any key must dispatch the unlearn-current-word hook.",
        1, pointerHandler.keyboardSwipeDownCount);
    assertFalse("Global learning gestures must not release the touched key's north side label as text.",
        pointerHandler.releasedValues.contains("b"));
    assertFalse("Global learning gestures must not release the touched key's south side label as text.",
        pointerHandler.releasedValues.contains("c"));
  }

  @Test
  public void swipe_up_learns_and_keyboard_down_requires_positive_confirmation()
      throws Exception
  {
    Harness harness = harness("cazoo", true, true);

    harness.handler.keyboard_swiped_up();
    awaitLearned("cazoo", true);
    SharedDecoder.Presentation learned = awaitFeedback(harness.decoder,
        SharedDecoder.Presentation.Feedback.LEARNED, "cazoo");
    assertEquals("Learning feedback must belong to the current immutable request key.",
        harness.decoder.current_key(), learned.key);

    harness.handler.keyboard_swiped_down();

    assertEquals("Keyboard-down unlearn must ask once before mutating learned data.",
        1, harness.receiver.confirmationCalls);
    assertEquals("Keyboard-down confirmation must name the exact current word.",
        "cazoo", harness.receiver.confirmationWord);
    assertLearnedRemains("cazoo", true);

    harness.receiver.cancelConfirmation();
    assertLearnedRemains("cazoo", true);

    harness.handler.keyboard_swiped_down();
    harness.receiver.confirmPositive();

    awaitLearned("cazoo", false);
    SharedDecoder.Presentation forgot = awaitFeedback(harness.decoder,
        SharedDecoder.Presentation.Feedback.FORGOT, "cazoo");
    assertEquals("Confirmed unlearning feedback must belong to the exact current request key.",
        harness.decoder.current_key(), forgot.key);
  }

  @Test
  public void delayed_keyboard_down_confirmation_for_stale_key_is_nondestructive()
      throws Exception
  {
    new PersonalizationStore(_prefs).record_word("cazoo");
    Harness harness = harness("cazoo", true, true);

    harness.handler.keyboard_swiped_down();
    Decoder.RequestKey staleKey = harness.decoder.current_key();
    Decoder.RequestKey replacementKey = harness.decoder.request(harness.session,
        snapshot(77, "cazoon"));
    assertFalse("The stale-confirmation fixture must advance to a distinct immutable request key.",
        staleKey.equals(replacementKey));

    harness.receiver.confirmPositive();

    assertLearnedRemains("cazoo", true);
    assertNotEquals("A stale positive action must not publish destructive feedback.",
        SharedDecoder.Presentation.Feedback.FORGOT,
        harness.decoder.current_presentation().feedback);
  }

  @Test
  public void disabled_or_unsafe_learning_gestures_fail_closed()
      throws Exception
  {
    for (Gate gate : new Gate[] {
        new Gate("disabled Suggestions", false, true),
        new Gate("unsafe editor", true, false) })
    {
      Harness harness = harness("cazoo", gate.suggestions, gate.safeEditor);

      harness.handler.keyboard_swiped_up();
      Thread.sleep(30L);

      assertFalse(gate.name
          + " must not write the current token into personalization.",
          new PersonalizationStore(_prefs).is_learned("cazoo"));
      assertNotEquals(gate.name
          + " must not show feedback for a mutation that was refused.",
          SharedDecoder.Presentation.Feedback.LEARNED,
          harness.decoder.current_presentation().feedback);
    }
  }

  private Harness harness(String word, boolean suggestions, boolean safeEditor)
      throws Exception
  {
    Config config = testConfig(word, suggestions, safeEditor);
    RecordingReceiver receiver = new RecordingReceiver();
    SharedDecoder decoder = new SharedDecoder(receiver.handler,
        new SharedDecoder.Callback()
        {
          @Override
          public void decoder_state_changed(SharedDecoder.Presentation state) {}
        });
    _decoders.add(decoder);
    long session = decoder.start_session(
        new Decoder.DecoderConfig(suggestions, false, true, safeEditor),
        SharedDecoder.ResourceSpec.empty("empty"), null,
        new SharedDecoder.PersonalizationSpec("gesture", _prefs));
    KeyEventHandler handler = new KeyEventHandler(receiver, decoder);
    config.handler = handler;
    handler.started(config, session);
    return new Harness(receiver, decoder, handler, session);
  }

  private Config testConfig(String word, boolean suggestions,
      boolean safeEditor)
      throws Exception
  {
    Constructor<Config> constructor = Config.class.getDeclaredConstructor(
        SharedPreferences.class, Resources.class, Boolean.class,
        juloo.keyboard2.dict.Dictionaries.class);
    constructor.setAccessible(true);
    Config config = constructor.newInstance(_prefs,
        new TestResources(RuntimeEnvironment.getApplication().getResources()),
        Boolean.FALSE, null);
    config.suggestions_enabled = suggestions;
    config.autocorrect_enabled = false;
    config.editor_config.should_show_candidates_view = true;
    config.editor_config.should_use_typing_assistance = safeEditor;
    config.editor_config.initial_text_before_cursor = word;
    config.editor_config.initial_text_after_cursor = "";
    config.editor_config.initial_sel_start = word.length();
    config.editor_config.initial_sel_end = word.length();
    return config;
  }

  private void awaitLearned(String word, boolean expected)
      throws Exception
  {
    long deadline = System.nanoTime() + 3_000_000_000L;
    do
    {
      if (new PersonalizationStore(_prefs).is_learned(word) == expected)
        return;
      Thread.sleep(2L);
    }
    while (System.nanoTime() < deadline);
    fail("Timed out waiting for learned=" + expected + " for " + word);
  }

  private void assertLearnedRemains(String word, boolean expected)
      throws Exception
  {
    long deadline = System.nanoTime() + 100_000_000L;
    do
    {
      assertEquals("Cancel, no callback, and stale callbacks must not mutate learned data.",
          expected, new PersonalizationStore(_prefs).is_learned(word));
      Thread.sleep(2L);
    }
    while (System.nanoTime() < deadline);
  }

  private static SharedDecoder.Presentation awaitFeedback(
      SharedDecoder decoder, SharedDecoder.Presentation.Feedback feedback,
      String word)
      throws Exception
  {
    long deadline = System.nanoTime() + 3_000_000_000L;
    do
    {
      SharedDecoder.Presentation state = decoder.current_presentation();
      if (state.state == SharedDecoder.Presentation.State.READY
          && state.feedback == feedback && word.equals(state.feedbackWord))
        return state;
      Thread.sleep(2L);
    }
    while (System.nanoTime() < deadline);
    fail("Timed out waiting for " + feedback + " feedback");
    return null;
  }

  private static CurrentlyTypedWord.Snapshot snapshot(long revision,
      String word)
      throws Exception
  {
    Constructor<CurrentlyTypedWord.Snapshot> constructor =
      CurrentlyTypedWord.Snapshot.class.getDeclaredConstructor(long.class,
          String.class, int.class, boolean.class, TouchTrace.Snapshot.class);
    constructor.setAccessible(true);
    return constructor.newInstance(revision, word, 0, false,
        new TouchTrace().snapshot());
  }

  private static KeyboardData.Key verticalSideLabelKey()
      throws Exception
  {
    KeyboardData keyboard = KeyboardData.load_string_exn(
        "<keyboard bottom_row=\"false\" width=\"1\">"
        + "<row><key c=\"a\" n=\"b\" s=\"c\"/></row></keyboard>");
    return keyboard.rows.get(0).keys.get(0);
  }

  private static final class Harness
  {
    final RecordingReceiver receiver;
    final SharedDecoder decoder;
    final KeyEventHandler handler;
    final long session;

    Harness(RecordingReceiver receiver_, SharedDecoder decoder_,
        KeyEventHandler handler_, long session_)
    {
      receiver = receiver_;
      decoder = decoder_;
      handler = handler_;
      session = session_;
    }
  }

  private static final class Gate
  {
    final String name;
    final boolean suggestions;
    final boolean safeEditor;

    Gate(String name_, boolean suggestions_, boolean safeEditor_)
    {
      name = name_;
      suggestions = suggestions_;
      safeEditor = safeEditor_;
    }
  }

  private static final class RecordingPointerHandler
      implements Pointers.IPointerEventHandler
  {
    int keyboardSwipeUpCount;
    int keyboardSwipeDownCount;
    final List<String> releasedValues = new ArrayList<String>();

    @Override public KeyValue modifyKey(KeyValue key,
        Pointers.Modifiers modifiers) { return key; }
    @Override public void onPointerDown(KeyValue key, boolean swipe) {}
    @Override public void onPointerFlagsChanged(boolean vibrate) {}
    @Override public void onPointerHold(KeyValue key,
        Pointers.Modifiers modifiers, int count) {}
    @Override public void onPointerCancel(KeyValue key,
        Pointers.Modifiers modifiers) {}

    @Override
    public void onPointerUp(KeyValue key, Pointers.Modifiers modifiers,
        TouchTrace.Entry touch)
    {
      if (key != null && key.getKind() == KeyValue.Kind.Char)
        releasedValues.add(String.valueOf(key.getChar()));
    }

    @Override public void onKeyboardSwipeUp() { keyboardSwipeUpCount++; }
    @Override public void onKeyboardSwipeDown() { keyboardSwipeDownCount++; }
  }

  private static final class RecordingReceiver
      implements KeyEventHandler.IReceiver
  {
    final Handler handler = new Handler(Looper.getMainLooper());
    final BaseInputConnection input = new BaseInputConnection(
        new View(RuntimeEnvironment.getApplication()), false);
    String confirmationWord;
    Runnable confirmationAction;
    int confirmationCalls;

    void cancelConfirmation()
    {
      confirmationAction = null;
    }

    void confirmPositive()
    {
      Runnable action = confirmationAction;
      confirmationAction = null;
      assertNotNull("A positive confirmation requires a captured destructive action.",
          action);
      action.run();
    }

    @Override public void handle_event_key(KeyValue.Event event) {}
    @Override public void set_shift_state(boolean state, boolean lock) {}
    @Override public void set_compose_pending(boolean pending) {}
    @Override public void selection_state_changed(boolean ongoing) {}
    @Override public void confirm_unlearn_word(String word,
        Runnable positiveAction)
    {
      confirmationWord = word;
      confirmationAction = positiveAction;
      ++confirmationCalls;
    }
    @Override public BaseInputConnection getCurrentInputConnection()
    {
      return input;
    }
    @Override public EditorInfo getCurrentInputEditorInfo()
    {
      return new EditorInfo();
    }
    @Override public Handler getHandler() { return handler; }
  }

  private static final class TestResources extends Resources
  {
    TestResources(Resources base)
    {
      super(base.getAssets(), base.getDisplayMetrics(), base.getConfiguration());
    }

    @Override public float getDimension(int id) { return 1f; }
  }
}
