package juloo.keyboard2.suggestions;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
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
        1, result, SharedDecoder.Presentation.Feedback.NONE, null);

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
          SharedDecoder.Presentation.Feedback.NONE, null));
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
  public void pending_empty_and_null_states_clear_text_and_disable_old_click_targets()
  {
    Context context = RuntimeEnvironment.getApplication();
    CandidatesView view = candidatesView(context);
    Decoder.Result result = result("ca", 21);
    view.set_decoder_state(SharedDecoder.Presentation.ready(1, result,
          SharedDecoder.Presentation.Feedback.NONE, null));
    TextView middle = view.findViewById(R.id.candidates_middle);
    middle.performClick();
    int calls = _handler.enteredCalls;

    Decoder.RequestKey replacement = new Decoder.RequestKey(
        1, 22, 22, 1, 1, 1, 1);
    view.set_decoder_state(SharedDecoder.Presentation.pending(1, replacement));
    assertEquals("PENDING must remove old text immediately instead of leaving a clickable stale row.",
        View.GONE, middle.getVisibility());
    assertEquals("", middle.getText().toString());
    middle.performClick();
    assertEquals("Candidate clicks are legal only for READY presentations.",
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
  public void learning_feedback_updates_action_slot_without_hiding_typed_literal()
  {
    Context context = RuntimeEnvironment.getApplication();
    CandidatesView view = candidatesView(context);
    Decoder.Result result = result("cazoo", 31);
    TextView middle = view.findViewById(R.id.candidates_middle);
    TextView left = view.findViewById(R.id.candidates_left);

    view.set_decoder_state(SharedDecoder.Presentation.ready(1, result,
          SharedDecoder.Presentation.Feedback.LEARNED, "cazoo"));
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
          SharedDecoder.Presentation.Feedback.FORGOT, "cazoo"));
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
  public void separators_follow_only_visible_word_boundaries()
  {
    Context context = RuntimeEnvironment.getApplication();
    CandidatesView view = candidatesView(context);
    View leftSeparator = view.findViewById(R.id.candidates_separator_left);
    View rightSeparator = view.findViewById(R.id.candidates_separator_right);

    view.set_decoder_state(SharedDecoder.Presentation.ready(1,
          result("ca", 41), SharedDecoder.Presentation.Feedback.NONE, null));

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
          SharedDecoder.Presentation.Feedback.NONE, null));
    assertEquals("A lone entered literal still has a visible boundary before its learn action.",
        View.VISIBLE, leftSeparator.getVisibility());
    assertEquals("No right separator may appear without a second word candidate.",
        View.GONE, rightSeparator.getVisibility());
  }

  private static Decoder.Result result(String typed, long generation)
  {
    PersonalizationStore store = PersonalizationStore.empty();
    for (String word : new String[] { "cabin", "cazoo", "camel", "candle" })
      for (int i = 0; i < 4; i++)
      {
        store.reset_context();
        store.record_word(word);
      }
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
