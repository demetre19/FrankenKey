package juloo.keyboard2.suggestions;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.view.View;
import android.widget.LinearLayout;
import android.view.MotionEvent;
import android.widget.TextView;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import juloo.keyboard2.CurrentlyTypedWord;
import juloo.keyboard2.Config;
import juloo.keyboard2.KeyValue;
import juloo.keyboard2.Pointers;
import juloo.keyboard2.R;
import juloo.keyboard2.TouchTrace;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;

import org.robolectric.Robolectric;
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
public class CandidatesViewPresentationTest
{
  private Config _config;
  private RecordingHandler _handler;

  @Before
  public void setUp()
      throws Exception
  {
    Context context = RuntimeEnvironment.getApplication();
    SharedPreferences prefs = context.getSharedPreferences(
        "candidate_presentation_test", Context.MODE_PRIVATE);
    prefs.edit().clear().commit();
    Constructor<Config> constructor = Config.class.getDeclaredConstructor(
        SharedPreferences.class, Resources.class, Boolean.class,
        juloo.keyboard2.dict.Dictionaries.class);
    constructor.setAccessible(true);
    _config = constructor.newInstance(prefs,
        new TestResources(context.getResources()), Boolean.FALSE, null);
    _handler = new RecordingHandler();
    _config.handler = _handler;
    setGlobalConfig(_config);
  }

  @After
  public void tearDown()
      throws Exception
  {
    setGlobalConfig(null);
  }

  @Test
  public void ready_state_presents_deterministic_words_and_routes_exact_request_key()
  {
    Context context = RuntimeEnvironment.getApplication();
    CandidatesView view = candidatesView(context);
    Decoder.Result result = result("ca", 11);
    SharedDecoder.Presentation ready = SharedDecoder.Presentation.ready(
        1, result, SharedDecoder.Presentation.Feedback.NONE, null,
        null, -1, -1);

    view.set_decoder_state(ready);
    TextView middle = view.findViewById(R.id.candidates_middle);
    TextView left = view.findViewById(R.id.candidates_left);
    middle.performClick();
    left.performClick();

    assertEquals("The primary READY word must be displayed without provider/source suffixes.",
        "ca", middle.getText().toString());
    assertEquals("An entered unlearned literal must expose the compact learn action in the third word slot.",
        "📖+", left.getText().toString());
    assertEquals("Learn ca", left.getContentDescription().toString());
    assertEquals("Candidate taps must carry the exact request key that produced the visible text.",
        result.key, _handler.enteredKey);
    assertEquals("ca", _handler.enteredText);
    assertEquals("The learn action must carry the same request key so stale rows cannot mutate personalization.",
        result.key, _handler.actionKey);
    assertEquals("ca", _handler.actionText);
    assertEquals(1, _handler.actionCalls);
  }

  @Test
  public void learned_literal_exposes_explicit_unlearn_action_with_exact_request_key()
  {
    Context context = RuntimeEnvironment.getApplication();
    CandidatesView view = candidatesView(context);
    Decoder.Result result = result("cazoo", 12);

    view.set_decoder_state(SharedDecoder.Presentation.ready(1, result,
          SharedDecoder.Presentation.Feedback.NONE, null, null, -1, -1));
    TextView middle = view.findViewById(R.id.candidates_middle);
    TextView left = view.findViewById(R.id.candidates_left);

    assertEquals("The entered learned literal must remain visible as the primary candidate.",
        "cazoo", middle.getText().toString());
    assertEquals("A learned literal needs an explicit unlearn action, not the ambiguous learned-status mark.",
        "📖−", left.getText().toString());
    assertEquals("The action description must distinguish forgetting from passive feedback.",
        "Forget cazoo", left.getContentDescription().toString());
    left.performClick();
    assertEquals("Unlearn must route through the exact RequestKey that produced the learned literal.",
        result.key, _handler.actionKey);
    assertEquals("cazoo", _handler.actionText);
    assertEquals(1, _handler.actionCalls);
  }

  @Test
  public void pending_for_same_slot_keeps_items_then_ready_restores_alpha()
      throws Exception
  {
    Context context = RuntimeEnvironment.getApplication();
    CandidatesView view = candidatesView(context);
    Decoder.Result first = result("ca", 21);
    CurrentlyTypedWord.Snapshot word = snapshot(1, "ca");
    view.set_decoder_state(SharedDecoder.Presentation.ready(1, first,
          SharedDecoder.Presentation.Feedback.NONE, null, word, 7, 500));
    TextView middle = view.findViewById(R.id.candidates_middle);

    Decoder.RequestKey replacement = new Decoder.RequestKey(
        1, 22, 22, 1, 1, 1, 1);
    view.set_decoder_state(SharedDecoder.Presentation.pending(1, replacement,
          snapshot(2, "cas"), 7, 500));
    assertEquals("A same-slot PENDING must keep the prior READY items visible.",
        View.VISIBLE, middle.getVisibility());
    assertEquals("ca", middle.getText().toString());
    assertEquals(1f, view.getAlpha(), 0.001f);

    middle.performClick();
    assertEquals("Items kept across PENDING stay tappable and insert once.",
        1, _handler.enteredCalls);
    assertEquals(first.key, _handler.enteredKey);
    assertEquals("ca", _handler.enteredText);

    Robolectric.getForegroundThreadScheduler().advanceBy(
        CandidatesView.PENDING_DIM_MS + 1, java.util.concurrent.TimeUnit.MILLISECONDS);
    assertEquals("A PENDING older than 150 ms dims the strip to 70%.",
        CandidatesView.PENDING_DIM_ALPHA, view.getAlpha(), 0.001f);

    Decoder.Result second = result("cas", 23);
    view.set_decoder_state(SharedDecoder.Presentation.ready(1, second,
          SharedDecoder.Presentation.Feedback.NONE, null,
          snapshot(2, "cas"), 7, 500));
    assertEquals("A READY presentation restores full alpha.",
        1f, view.getAlpha(), 0.001f);
    assertEquals("cas", middle.getText().toString());
  }

  @Test
  public void pending_for_different_slot_then_empty_and_null_clear_targets()
      throws Exception
  {
    Context context = RuntimeEnvironment.getApplication();
    CandidatesView view = candidatesView(context);
    view.set_decoder_state(SharedDecoder.Presentation.ready(1,
          result("ca", 21), SharedDecoder.Presentation.Feedback.NONE, null,
          snapshot(1, "ca"), 7, 500));
    TextView middle = view.findViewById(R.id.candidates_middle);
    int calls = _handler.enteredCalls;

    Decoder.RequestKey replacement = new Decoder.RequestKey(
        1, 22, 22, 1, 1, 1, 1);
    // The pending request targets a different absolute word start: the old
    // slot's candidates are stale and must go away immediately.
    view.set_decoder_state(SharedDecoder.Presentation.pending(1, replacement,
          snapshot(2, "next"), 7, 560));
    assertEquals("A slot change must remove old-word items immediately.",
        View.GONE, middle.getVisibility());
    assertEquals("", middle.getText().toString());
    middle.performClick();
    assertEquals("Cleared items are not tappable.",
        calls, _handler.enteredCalls);

    view.set_decoder_state(SharedDecoder.Presentation.empty(1, replacement));
    middle.performClick();
    assertEquals("EMPTY must remain non-clickable even if an old TextView instance is clicked programmatically.",
        calls, _handler.enteredCalls);

    view.set_decoder_state(null);
    assertEquals("A missing decoder presentation must fail closed.",
        View.GONE, middle.getVisibility());
  }

  @Test
  public void rerender_between_down_and_up_inserts_word_under_finger_at_down()
      throws Exception
  {
    Context context = RuntimeEnvironment.getApplication();
    CandidatesView view = candidatesView(context);
    Decoder.Result first = result("ca", 31);
    view.set_decoder_state(SharedDecoder.Presentation.ready(1, first,
          SharedDecoder.Presentation.Feedback.NONE, null,
          snapshot(1, "ca"), 7, 500));
    TextView middle = view.findViewById(R.id.candidates_middle);

    long now = android.os.SystemClock.uptimeMillis();
    middle.dispatchTouchEvent(MotionEvent.obtain(
          now, now, MotionEvent.ACTION_DOWN, 20f, 20f, 0));
    // A newer READY lands mid-press and re-renders the strip.
    view.set_decoder_state(SharedDecoder.Presentation.ready(1,
          result("cb", 32), SharedDecoder.Presentation.Feedback.NONE, null,
          snapshot(2, "cb"), 7, 500));
    middle.performClick();

    assertEquals("The word under the finger at DOWN is inserted, not the new occupant of the slot.",
        "ca", _handler.enteredText);
    assertEquals(1, _handler.enteredCalls);
  }

  @Test
  public void learning_feedback_updates_action_slot_without_hiding_typed_literal()
  {
    Context context = RuntimeEnvironment.getApplication();
    CandidatesView view = candidatesView(context);
    Decoder.Result result = result("cazoo", 31);
    TextView middle = view.findViewById(R.id.candidates_middle);
    TextView left = view.findViewById(R.id.candidates_left);

    view.set_decoder_state(SharedDecoder.Presentation.ready(1, result,
          SharedDecoder.Presentation.Feedback.LEARNED, "cazoo", null, -1, -1));
    assertEquals("Learning feedback must keep the typed token visible.",
        "cazoo", middle.getText().toString());
    assertEquals("Learning feedback must be immediate and explicit.",
        "📖✓", left.getText().toString());
    assertEquals("Learned cazoo", left.getContentDescription().toString());
    int enteredCalls = _handler.enteredCalls;
    int actionCalls = _handler.actionCalls;
    left.performClick();
    assertEquals("Learned feedback is status, not a candidate.",
        enteredCalls, _handler.enteredCalls);
    assertEquals("Learned feedback must not repeat the learning action.",
        actionCalls, _handler.actionCalls);

    view.set_decoder_state(SharedDecoder.Presentation.ready(1, result,
          SharedDecoder.Presentation.Feedback.FORGOT, "cazoo", null, -1, -1));
    assertEquals("Unlearning feedback must use the distinct forgot state.",
        "📖−", left.getText().toString());
    assertEquals("Forgot cazoo", left.getContentDescription().toString());
    left.performClick();
    assertEquals("Forgot feedback is status, not a candidate.",
        enteredCalls, _handler.enteredCalls);
    assertEquals("Forgot feedback must not behave like the visually similar unlearn action.",
        actionCalls, _handler.actionCalls);
  }

  @Test
  public void swipe_left_pages_to_next_three_words_entering_from_right()
  {
    Context context = RuntimeEnvironment.getApplication();
    CandidatesView view = candidatesView(context);
    Decoder.Result result = sixWordResult("ca", 40);
    assertEquals("The decoder must retain two ranked pages for the suggestion strip.",
        6, result.words().length);
    view.set_decoder_state(SharedDecoder.Presentation.ready(1, result,
          SharedDecoder.Presentation.Feedback.NONE, null, null, -1, -1));
    TextView middle = view.findViewById(R.id.candidates_middle);

    long now = android.os.SystemClock.uptimeMillis();
    middle.dispatchTouchEvent(MotionEvent.obtain(
          now, now, MotionEvent.ACTION_DOWN, 120f, 20f, 0));
    middle.dispatchTouchEvent(MotionEvent.obtain(
          now, now + 16, MotionEvent.ACTION_UP, 20f, 20f, 0));

    assertEquals("A left swipe must reveal the next suggestion page.",
        1, view._page);
    assertEquals(result.words()[3].surface,
        ((TextView)view.findViewById(R.id.candidates_middle))
          .getText().toString());
    assertEquals(result.words()[4].surface,
        ((TextView)view.findViewById(R.id.candidates_right))
          .getText().toString());
    assertEquals(result.words()[5].surface,
        ((TextView)view.findViewById(R.id.candidates_left))
          .getText().toString());
    middle.performClick();
    assertEquals("Second-page taps must retain the exact decoder request.",
        result.key, _handler.enteredKey);
    assertEquals(result.words()[3].surface, _handler.enteredText);

    now += 32;
    middle.dispatchTouchEvent(MotionEvent.obtain(
          now, now, MotionEvent.ACTION_DOWN, 20f, 20f, 0));
    middle.dispatchTouchEvent(MotionEvent.obtain(
          now, now + 16, MotionEvent.ACTION_UP, 120f, 20f, 0));
    assertEquals("A right swipe must return to the first suggestion page.",
        0, view._page);
    assertEquals(result.words()[0].surface, middle.getText().toString());
  }

  @Test
  public void separators_follow_only_visible_word_boundaries()
  {
    Context context = RuntimeEnvironment.getApplication();
    CandidatesView view = candidatesView(context);
    View leftSeparator = view.findViewById(R.id.candidates_separator_left);
    View rightSeparator = view.findViewById(R.id.candidates_separator_right);

    view.set_decoder_state(SharedDecoder.Presentation.ready(1,
          result("ca", 41), SharedDecoder.Presentation.Feedback.NONE, null,
          null, -1, -1));

    assertEquals("Three visible word slots require the left boundary separator.",
        View.VISIBLE, leftSeparator.getVisibility());
    assertEquals("Three visible word slots require the right boundary separator.",
        View.VISIBLE, rightSeparator.getVisibility());

    Decoder.Result oneWord = new Decoder().decode(
        new Decoder.Request(new Decoder.RequestKey(1, 42, 42, 1, 1, 1, 1),
          "literal", (TouchTrace.Snapshot)null, Decoder.Geometry.from(null),
          new Decoder.DecoderConfig(true, false, true, true)),
        null, null, null, PersonalizationStore.empty(), false);
    view.set_decoder_state(SharedDecoder.Presentation.ready(1, oneWord,
          SharedDecoder.Presentation.Feedback.NONE, null, null, -1, -1));
    assertEquals("A lone entered literal still has a visible boundary before its learn action.",
        View.VISIBLE, leftSeparator.getVisibility());
    assertEquals("No right separator may appear without a second word candidate.",
        View.GONE, rightSeparator.getVisibility());
  }

  private static Decoder.Result result(String typed, long generation,
      String... learn)
  {
    PersonalizationStore store = PersonalizationStore.empty();
    for (String word : learn)
      store.learn_word(word);
    Decoder.RequestKey key = new Decoder.RequestKey(
        1, generation, generation, 1, 1, 1, 1);
    Decoder.Request request = new Decoder.Request(key, typed,
        (TouchTrace.Snapshot)null, Decoder.Geometry.from(null),
        new Decoder.DecoderConfig(true, false, true, true));
    return new Decoder().decode(request, null, null, null, store, false);
  }

  private static Decoder.Result result(String typed, long generation)
  {
    return result(typed, generation, "cabin", "cazoo", "camel", "candle");
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

  private static Decoder.Result sixWordResult(String typed, long generation)
  {
    PersonalizationStore store = PersonalizationStore.empty();
    for (String word : new String[] {
          "cabin", "cacao", "cactus", "cadet", "cage", "cake", "camel"
        })
      store.learn_word(word);
    Decoder.RequestKey key = new Decoder.RequestKey(
        1, generation, generation, 1, 1, 1, 1);
    Decoder.Request request = new Decoder.Request(key, typed,
        (TouchTrace.Snapshot)null, Decoder.Geometry.from(null),
        new Decoder.DecoderConfig(true, false, true, true));
    return new Decoder().decode(request, null, null, null, store, false);
  }

  private static CandidatesView candidatesView(Context context)
  {
    CandidatesView view = new CandidatesView(context, null);
    view.setLayoutParams(new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT));
    view.addView(textView(context, R.id.candidates_middle));
    view.addView(textView(context, R.id.candidates_right));
    view.addView(textView(context, R.id.candidates_left));
    view.addView(textView(context, R.id.candidates_emoji));
    view.addView(separator(context, R.id.candidates_separator_left));
    view.addView(separator(context, R.id.candidates_separator_right));
    view.onFinishInflate();
    return view;
  }

  private static TextView textView(Context context, int id)
  {
    TextView view = new TextView(context);
    view.setId(id);
    return view;
  }

  private static View separator(Context context, int id)
  {
    View view = new View(context);
    view.setId(id);
    return view;
  }

  private static void setGlobalConfig(Config config)
      throws Exception
  {
    Field field = Config.class.getDeclaredField("_globalConfig");
    field.setAccessible(true);
    field.set(null, config);
  }

  private static final class RecordingHandler
      implements Config.IKeyEventHandler
  {
    Decoder.RequestKey enteredKey;
    String enteredText;
    int enteredCalls;
    Decoder.RequestKey actionKey;
    String actionText;
    int actionCalls;

    @Override public void key_down(KeyValue value, boolean isSwipe) {}
    @Override public void key_up(KeyValue value, Pointers.Modifiers mods,
        TouchTrace.Entry touch) {}
    @Override public void key_cancel(KeyValue value, Pointers.Modifiers mods) {}
    @Override public void key_hold(KeyValue value, Pointers.Modifiers mods,
        int holdCount) {}
    @Override public void mods_changed(Pointers.Modifiers mods) {}

    @Override
    public void suggestion_entered(Decoder.RequestKey key, String text)
    {
      enteredKey = key;
      enteredText = text;
      enteredCalls++;
    }

    @Override
    public void suggestion_swiped_up(Decoder.RequestKey key, String text)
    {
      actionKey = key;
      actionText = text;
      actionCalls++;
    }

    @Override public void typing_assistance_data_cleared() {}
    @Override public void keyboard_swiped_up() {}
    @Override public void keyboard_swiped_down() {}
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
