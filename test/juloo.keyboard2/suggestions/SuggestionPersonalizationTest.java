package juloo.keyboard2.suggestions;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.view.View;
import android.widget.TextView;
import android.widget.LinearLayout;
import juloo.keyboard2.Config;
import juloo.keyboard2.KeyValue;
import juloo.keyboard2.KeyboardData;
import juloo.keyboard2.Pointers;
import juloo.keyboard2.R;
import juloo.keyboard2.TouchTrace;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
public class SuggestionPersonalizationTest
{
  private SharedPreferences _prefs;

  public SuggestionPersonalizationTest() {}

  @Before
  public void setUp()
  {
    _prefs = RuntimeEnvironment.getApplication()
      .getSharedPreferences("suggestion_personalization_test", Context.MODE_PRIVATE);
    _prefs.edit().clear().commit();
  }

  @After
  public void tearDown()
      throws Exception
  {
    _prefs.edit().clear().commit();
    resetGlobalConfig();
  }

  @Test
  public void learned_prefix_suggestions_persist_and_rank_by_frequency()
  {
    PersonalizationStore store = new PersonalizationStore(_prefs);

    store.record_word("Cazoo");
    store.record_word("cabin");
    store.record_word("cazoo");
    store.record_word("cazoo");

    PersonalizationStore reloaded = new PersonalizationStore(_prefs);

    assertEquals("Learned prefix suggestions must be persisted locally and ordered by learned frequency before alphabetical tie-breaking.",
        Arrays.asList("cazoo", "cabin"),
        reloaded.suggest_words("CA", 3));
  }

  @Test
  public void next_word_suggestions_use_previous_committed_word_bigram_counts()
  {
    PersonalizationStore store = new PersonalizationStore(_prefs);
    store.record_word("good");
    store.record_word("morning");
    store.record_word("good");
    store.record_word("night");
    store.record_word("good");
    store.record_word("morning");

    PersonalizationStore reloaded = new PersonalizationStore(_prefs);
    reloaded.reset_context();
    reloaded.record_word("good");

    assertEquals("After a committed word, next-word suggestions must come from persisted bigram counts for that previous word.",
        Arrays.asList("morning", "night"),
        reloaded.suggest_next_words(5));
  }

  @Test
  public void unlearned_word_is_removed_from_prefix_suggestions_without_forgetting_unrelated_words()
  {
    PersonalizationStore store = new PersonalizationStore(_prefs);
    store.record_word("Cazoo");
    store.record_word("cabin");
    store.record_word("cazoo");

    store.unlearn_word("CAZOO");
    PersonalizationStore reloaded = new PersonalizationStore(_prefs);

    assertFalse("Unlearning must remove the selected word from the persisted learned-word set.",
        reloaded.is_learned("cazoo"));
    assertTrue("Unlearning one word must not clear unrelated learned words.",
        reloaded.is_learned("cabin"));
    assertEquals("Prefix suggestions must stop surfacing the unlearned word while preserving other matching learned words.",
        Arrays.asList("cabin"),
        reloaded.suggest_words("ca", 3));
  }

  @Test
  public void unlearning_word_removes_next_word_predictions_that_contain_it()
  {
    PersonalizationStore store = new PersonalizationStore(_prefs);
    store.record_word("good");
    store.record_word("morning");
    store.record_word("good");
    store.record_word("night");
    store.record_word("good");
    store.record_word("morning");

    store.unlearn_word("morning");
    PersonalizationStore reloaded = new PersonalizationStore(_prefs);
    reloaded.reset_context();
    reloaded.record_word("good");

    assertEquals("Unlearning a word must remove persisted bigrams containing it so it no longer appears as a next-word prediction, while unrelated next words remain.",
        Arrays.asList("night"),
        reloaded.suggest_next_words(5));
  }

  @Test
  public void clear_removes_persisted_word_and_bigram_learning()
  {
    PersonalizationStore store = new PersonalizationStore(_prefs);
    store.record_word("cazoo");
    store.record_word("good");
    store.record_word("morning");

    assertTrue("Fixture must create persisted personalization data before clear is exercised.",
        PersonalizationStore.has_data(_prefs));

    PersonalizationStore.clear(_prefs);
    PersonalizationStore reloaded = new PersonalizationStore(_prefs);

    assertFalse("Clearing learned typing-assistance data must remove both persisted word and bigram stores.",
        PersonalizationStore.has_data(_prefs));
    assertTrue("Cleared word counts must not produce learned prefix suggestions.",
        reloaded.suggest_words("ca", 3).isEmpty());
    reloaded.record_word("good");
    assertTrue("Cleared bigram counts must not produce next-word suggestions for the previously learned pair.",
        reloaded.suggest_next_words(3).isEmpty());
  }

  @Test
  public void learnable_words_exclude_punctuation_technical_tokens_and_length_edges()
  {
    assertTrue("Plain alphabetic words are the only inputs eligible for the local typing model.",
        PersonalizationStore.is_learnable("Keyboard"));

    String[] rejected = {
      null,
      "",
      "a",
      "abcdefghijklmnopqrstuvwxyzabcdefg",
      "hello!",
      "can't",
      "foo_bar",
      "abc123",
      "user@example.com",
      "https://example.com"
    };
    for (String word : rejected)
      assertFalse("The local typing model must reject non-word or unsafe token: " + word,
          PersonalizationStore.is_learnable(word));
  }

  @Test
  public void empty_store_records_suggests_and_clears_without_persistent_preferences()
  {
    PersonalizationStore store = PersonalizationStore.empty();

    store.record_word("Cazoo");
    store.record_word("cabin");
    store.record_word("cazoo");

    assertEquals("An empty/nonpersistent store must still learn in memory for the active keyboard session.",
        Arrays.asList("cazoo", "cabin"),
        store.suggest_words("CA", 3));

    store.clear();
    assertTrue("Clearing a nonpersistent store must remove in-memory learned words without requiring SharedPreferences.",
        store.suggest_words("ca", 3).isEmpty());

    store.record_word("good");
    store.record_word("morning");
    store.record_word("good");
    store.record_word("night");
    store.record_word("good");
    store.record_word("morning");
    store.reset_context();
    store.record_word("good");

    assertEquals("A nonpersistent store must learn next-word pairs in memory for the active keyboard session.",
        Arrays.asList("morning", "night"),
        store.suggest_next_words(3));

    PersonalizationStore fresh = PersonalizationStore.empty();
    assertTrue("A fresh nonpersistent store must not reload words learned by a previous empty() store.",
        fresh.suggest_words("ca", 3).isEmpty());
  }

  @Test
  public void disabled_suggestions_clear_stale_candidates_and_publish_empty_state()
      throws Exception
  {
    Config config = testConfig(_prefs);
    config.suggestions_enabled = true;
    config.editor_config.should_show_candidates_view = true;
    config.personalization = PersonalizationStore.empty();
    config.personalization.record_word("cazoo");

    RecordingCallback callback = new RecordingCallback();
    Suggestions suggestions = new Suggestions(callback, config);
    suggestions.started();
    suggestions.currently_typed_word("ca", null);
    assertEquals("Fixture must publish a learned candidate before suggestions are disabled.",
        "cazoo", callback.suggestions[0]);

    config.suggestions_enabled = false;
    suggestions.started();
    assertEquals("started() must clear any currently held suggestions when Config disables suggestion display.",
        0, suggestions.count);

    suggestions.currently_typed_word("ca", null);

    assertEquals("currently_typed_word() must publish the cleared state when Config disables suggestion display.",
        2, callback.publish_count);
    assertEquals("Disabled suggestions must not leave stale candidates visible.",
        0, callback.count);
    assertNull("Disabled suggestions must clear the first published candidate slot.",
        callback.suggestions[0]);
  }

  @Test
  public void qwerty_geometry_prefers_the_candidate_closer_to_the_typed_typo()
  {
    List<String> candidates = new ArrayList<String>(Arrays.asList("cello", "hello"));

    TouchGeometry.rerank("gello", candidates);

    assertEquals("For the typo gello, h is geometrically closer to the touched g key than c, so hello must outrank the equal-edit-distance alternative cello.",
        Arrays.asList("hello", "cello"), candidates);
  }
  @Test
  public void active_layout_geometry_can_override_fixed_qwerty_reranking()
      throws Exception
  {
    KeyboardData layout = KeyboardData.load_string_exn(
        "<keyboard bottom_row=\"false\" width=\"4\">"
        + "<row>"
        + "<key c=\"g\"/>"
        + "<key c=\"c\"/>"
        + "<key c=\"x\"/>"
        + "<key c=\"h\"/>"
        + "</row>"
        + "</keyboard>");
    List<String> candidates = new ArrayList<String>(Arrays.asList("hello", "cello"));

    TouchGeometry.rerank("gello", candidates, layout);

    assertEquals("The active layout places c next to g and h far away, so layout-aware reranking must prefer cello even though fixed QWERTY prefers hello.",
        Arrays.asList("cello", "hello"), candidates);
  }

  @Test
  public void visible_suggestions_surface_exact_unknown_word_as_entered_text_for_learning()
      throws Exception
  {
    Config config = testConfig(_prefs);
    config.suggestions_enabled = true;
    config.editor_config.should_show_candidates_view = true;
    config.personalization = PersonalizationStore.empty();
    config.current_dictionary = null;
    config.current_hunspell = null;

    RecordingCallback callback = new RecordingCallback();
    Suggestions suggestions = new Suggestions(callback, config);
    suggestions.started();
    suggestions.currently_typed_word("cazoo", null);

    assertEquals("A learnable word that neither Hunspell nor the active dictionary recognizes must remain visible as the exact typed text so the candidate row can offer an explicit learn action.",
        "cazoo", callback.suggestions[0]);
    assertEquals("The exact typed unknown word must carry ENTERED_TEXT source metadata rather than pretending to be a learned, dictionary, or Hunspell suggestion.",
        Suggestions.Source.ENTERED_TEXT, callback.sources[0]);
  }

  @Test
  public void exact_learned_word_remains_entered_text_source_for_unlearning()
      throws Exception
  {
    Config config = testConfig(_prefs);
    config.suggestions_enabled = true;
    config.editor_config.should_show_candidates_view = true;
    config.personalization = PersonalizationStore.empty();
    config.personalization.record_word("cazoo");
    config.current_dictionary = null;
    config.current_hunspell = null;

    RecordingCallback callback = new RecordingCallback();
    Suggestions suggestions = new Suggestions(callback, config);
    suggestions.started();
    suggestions.currently_typed_word("cazoo", null);

    assertEquals("Typing an already-learned word exactly must still surface that typed text so the same candidate-row action can unlearn it.",
        "cazoo", callback.suggestions[0]);
    assertEquals("Exact learned typed text must be marked ENTERED_TEXT, not LEARNED, so CandidatesView exposes the learn/unlearn action instead of a plain learned candidate.",
        Suggestions.Source.ENTERED_TEXT, callback.sources[0]);
  }

  @Test
  public void learn_action_label_keeps_typed_item_and_tap_routes_to_learning()
      throws Exception
  {
    Context context = RuntimeEnvironment.getApplication();
    RecordingHandler handler = new RecordingHandler();
    Config config = testConfig(_prefs);
    config.handler = handler;
    setGlobalConfig(config);
    CandidatesView view = new CandidatesView(context, null);
    TextView middle = candidateTextView(context, R.id.candidates_middle);
    TextView right = candidateTextView(context, R.id.candidates_right);
    TextView left = candidateTextView(context, R.id.candidates_left);
    TextView emoji = candidateTextView(context, R.id.candidates_emoji);
    view.addView(middle);
    view.addView(right);
    view.addView(left);
    view.addView(emoji);
    view.onFinishInflate();
    Suggestions published = new Suggestions(null, null);
    published.count = 1;
    published.suggestions[0] = "cazoo";
    published.sources[0] = Suggestions.Source.ENTERED_TEXT;

    view.set_candidates(published);
    left.performClick();

    assertEquals("The learn/unlearn action must render the compact unlearned book action label instead of committing the typed word text from the action slot.",
        "📖+", left.getText().toString());
    assertEquals("The learn/unlearn action must keep the typed word in accessibility text so users can tell which word will be toggled.",
        "Learn or unlearn cazoo", left.getContentDescription().toString());
    assertNull("Tapping the book action must not commit the label or typed word through suggestion_entered.",
        handler.entered);
    assertEquals("Tapping the book action must route the underlying typed word to suggestion_swiped_up so KeyEventHandler toggles personalization.",
        "cazoo", handler.swiped);
  }

  @Test
  public void dictionary_action_icon_reflects_learn_forgot_and_learned_feedback_states()
      throws Exception
  {
    Context context = RuntimeEnvironment.getApplication();
    Config config = testConfig(_prefs);
    config.personalization = PersonalizationStore.empty();
    setGlobalConfig(config);
    CandidatesView view = candidatesView(context);
    TextView middle = view.findViewById(R.id.candidates_middle);
    TextView left = view.findViewById(R.id.candidates_left);

    view.set_candidates(singleSuggestion("cazoo", Suggestions.Source.ENTERED_TEXT));
    assertEquals("Unlearned typed words must expose the learn affordance immediately in the dictionary action slot.",
        "cazoo", middle.getText().toString());
    assertEquals("Unlearned typed words must render the learn dictionary icon state.",
        "📖+", left.getText().toString());
    view.set_candidates(singleSuggestionWithFeedback("cazoo",
          Suggestions.LearnFeedback.LEARNED));
    assertEquals("A word learned through a keyboard-wide gesture must keep the typed word visible while the action slot confirms it is learned.",
        "cazoo", middle.getText().toString());
    assertEquals("A word learned through a keyboard-wide gesture must render learned feedback without waiting for another keystroke.",
        "📖✓", left.getText().toString());

    view.set_candidates(singleSuggestionWithFeedback("cazoo",
          Suggestions.LearnFeedback.FORGOT));
    assertEquals("A word unlearned through a keyboard-wide gesture must keep the typed word visible while the action slot confirms it was forgotten.",
        "cazoo", middle.getText().toString());
    assertEquals("A word unlearned through a keyboard-wide gesture must render forgot feedback without waiting for another keystroke.",
        "📖−", left.getText().toString());
  }

  @Test
  public void visible_candidate_labels_do_not_append_source_suffixes()
  {
    Context context = RuntimeEnvironment.getApplication();
    CandidatesView view = new CandidatesView(context, null);
    TextView middle = candidateTextView(context, R.id.candidates_middle);
    TextView right = candidateTextView(context, R.id.candidates_right);
    TextView left = candidateTextView(context, R.id.candidates_left);
    TextView emoji = candidateTextView(context, R.id.candidates_emoji);
    view.addView(middle);
    view.addView(right);
    view.addView(left);
    view.addView(emoji);
    view.onFinishInflate();
    Suggestions published = new Suggestions(null, null);
    published.count = 3;
    published.suggestions[0] = "cazoo";
    published.sources[0] = Suggestions.Source.LEARNED;
    published.suggestions[1] = "hello";
    published.sources[1] = Suggestions.Source.HUNSPELL;
    published.suggestions[2] = "cabin";
    published.sources[2] = Suggestions.Source.DICTIONARY;
    published.emoji_suggestion = "😄";

    view.set_candidates(published);

    assertEquals("Learned candidates must display as the candidate text only; source metadata is for behavior, not visible suffix labels.",
        "cazoo", middle.getText().toString());
    assertEquals("Hunspell candidates must display as the candidate text only; source metadata must not leak into the visible label.",
        "hello", right.getText().toString());
    assertEquals("Dictionary candidates must display as the candidate text only; source metadata must not leak into the visible label.",
        "cabin", left.getText().toString());
    assertEquals("Emoji candidates must display as the emoji only, without a source suffix.",
        "😄", emoji.getText().toString());
  }

  @Test
  public void candidate_separators_track_visible_word_suggestion_boundaries()
  {
    Context context = RuntimeEnvironment.getApplication();
    CandidatesView view = candidatesViewWithSeparators(context);
    View leftSeparator = view.findViewById(
        requiredResourceId(context, "candidates_separator_left"));
    View rightSeparator = view.findViewById(
        requiredResourceId(context, "candidates_separator_right"));

    view.set_candidates(wordSuggestions("to", "do", "done"));
    assertEquals("With three visible word suggestions, the separator between the third and primary word candidates must be visible.",
        View.VISIBLE, leftSeparator.getVisibility());
    assertEquals("With three visible word suggestions, the separator between the primary and second word candidates must be visible.",
        View.VISIBLE, rightSeparator.getVisibility());

    view.set_candidates(wordSuggestions("to", "do", null));
    assertEquals("The third-word separator must hide when there is no third visible word candidate.",
        View.GONE, leftSeparator.getVisibility());
    assertEquals("The separator between the primary and second word candidates must remain visible for two visible word suggestions.",
        View.VISIBLE, rightSeparator.getVisibility());

    Suggestions oneWordAndEmoji = wordSuggestions("to", null, null);
    oneWordAndEmoji.emoji_suggestion = "🙂";
    view.set_candidates(oneWordAndEmoji);
    assertEquals("Emoji candidates are not word suggestions, so they must not make the third-word separator visible.",
        View.GONE, leftSeparator.getVisibility());
    assertEquals("A single visible word candidate has no word boundary separator to show, even when an emoji candidate is visible.",
        View.GONE, rightSeparator.getVisibility());
  }

  @Test
  public void long_visible_candidate_labels_stay_source_free_single_line_and_autosized()
      throws Exception
  {
    Context context = RuntimeEnvironment.getApplication();
    CandidatesView view = candidatesView(context);
    Config config = testConfig(_prefs);
    view.set_sizes(config);
    TextView middle = view.findViewById(R.id.candidates_middle);
    TextView right = view.findViewById(R.id.candidates_right);
    TextView left = view.findViewById(R.id.candidates_left);
    int normalMaxTextSize = middle.getAutoSizeMaxTextSize();
    String learned = "pneumonoultramicroscopicsilicovolcanoconiosis";
    String hunspell = "antidisestablishmentarianism";
    String dictionary = "floccinaucinihilipilification";
    Suggestions published = new Suggestions(null, null);
    published.count = 3;
    published.suggestions[0] = learned;
    published.sources[0] = Suggestions.Source.LEARNED;
    published.suggestions[1] = hunspell;
    published.sources[1] = Suggestions.Source.HUNSPELL;
    published.suggestions[2] = dictionary;
    published.sources[2] = Suggestions.Source.DICTIONARY;

    view.set_candidates(published);

    assertLongCandidateLabel("Learned", middle, learned, normalMaxTextSize);
    assertLongCandidateLabel("Hunspell", right, hunspell, normalMaxTextSize);
    assertLongCandidateLabel("Dictionary", left, dictionary, normalMaxTextSize);
    view.set_candidates(wordSuggestions("short", "tiny", "brief"));
    assertEquals("Short candidate labels must restore the normal candidate text size after a long-word shrink.",
        normalMaxTextSize, middle.getAutoSizeMaxTextSize());
  }

  @Test
  public void touch_trace_can_outrank_the_candidate_closer_to_the_literal_key()
  {
    List<String> candidates = new ArrayList<String>(Arrays.asList("hello", "jello"));
    TouchTrace touches = new TouchTrace();
    touches.add(TouchTrace.entry(140f, 100f, 100f, 100f, 20f, 20f));

    TouchGeometry.rerank("gello", candidates, touches, null);

    assertEquals("Ignoring touch locations would prefer hello because h is closer to the literal g key; an actual tap near j must make jello rank first.",
        Arrays.asList("jello", "hello"), candidates);
  }

  @Test
  public void visible_suggestions_mark_learned_candidates_with_learned_source()
      throws Exception
  {
    Config config = testConfig(_prefs);
    config.suggestions_enabled = true;
    config.editor_config.should_show_candidates_view = true;
    config.personalization = PersonalizationStore.empty();
    config.personalization.record_word("cazoo");

    RecordingCallback callback = new RecordingCallback();
    Suggestions suggestions = new Suggestions(callback, config);
    suggestions.started();
    suggestions.currently_typed_word("ca", null);

    assertEquals("Fixture must surface the learned prefix candidate through the visible suggestion API.",
        "cazoo", callback.suggestions[0]);
    assertEquals("Visible learned candidates must carry LEARNED source metadata so CandidatesView can distinguish user-learned words from dictionary suggestions.",
        Suggestions.Source.LEARNED, callback.sources[0]);
  }


  private static void setGlobalConfig(Config config)
      throws Exception
  {
    java.lang.reflect.Field field = Config.class.getDeclaredField("_globalConfig");
    field.setAccessible(true);
    field.set(null, config);
  }

  private static void resetGlobalConfig()
      throws Exception
  {
    java.lang.reflect.Field field = Config.class.getDeclaredField("_globalConfig");
    field.setAccessible(true);
    field.set(null, null);
  }

  private static Config testConfig(SharedPreferences prefs)
      throws Exception
  {
    java.lang.reflect.Constructor<Config> ctor =
      Config.class.getDeclaredConstructor(SharedPreferences.class,
          Resources.class, Boolean.class,
          juloo.keyboard2.dict.Dictionaries.class);
    ctor.setAccessible(true);
    return ctor.newInstance(prefs, testResources(), Boolean.FALSE, null);
  }

  private static Resources testResources()
  {
    Resources base = RuntimeEnvironment.getApplication().getResources();
    return new TestResources(base);
  }

  private static CandidatesView candidatesView(Context context)
  {
    CandidatesView view = new CandidatesView(context, null);
    view.setLayoutParams(new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT));
    view.addView(candidateTextView(context, R.id.candidates_middle));
    view.addView(candidateTextView(context, R.id.candidates_right));
    view.addView(candidateTextView(context, R.id.candidates_left));
    view.addView(candidateTextView(context, R.id.candidates_emoji));
    view.onFinishInflate();
    return view;
  }

  private static CandidatesView candidatesViewWithSeparators(Context context)
  {
    CandidatesView view = new CandidatesView(context, null);
    view.setLayoutParams(new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT));
    view.addView(candidateTextView(context, R.id.candidates_emoji));
    view.addView(candidateTextView(context, R.id.candidates_left));
    view.addView(candidateSeparatorView(context, "candidates_separator_left"));
    view.addView(candidateTextView(context, R.id.candidates_middle));
    view.addView(candidateSeparatorView(context, "candidates_separator_right"));
    view.addView(candidateTextView(context, R.id.candidates_right));
    view.onFinishInflate();
    return view;
  }

  private static View candidateSeparatorView(Context context, String name)
  {
    View view = new View(context);
    view.setId(requiredResourceId(context, name));
    view.setVisibility(View.GONE);
    return view;
  }

  private static int requiredResourceId(Context context, String name)
  {
    int id = context.getResources().getIdentifier(name, "id",
        context.getPackageName());
    if (id == 0 && "candidates_separator_left".equals(name))
      id = R.id.candidates_separator_left;
    if (id == 0 && "candidates_separator_right".equals(name))
      id = R.id.candidates_separator_right;
    assertTrue("CandidatesView separator contract requires R.id." + name
        + " so optional separator children can be discovered without breaking candidate rows that omit them.",
        id != 0);
    return id;
  }

  private static Suggestions wordSuggestions(String first, String second,
      String third)
  {
    Suggestions s = new Suggestions(null, null);
    String[] words = { first, second, third };
    for (int i = 0; i < words.length; i++)
      if (words[i] != null)
      {
        s.suggestions[s.count] = words[i];
        s.sources[s.count] = Suggestions.Source.DICTIONARY;
        s.count++;
      }
    return s;
  }

  private static Suggestions singleSuggestion(String text, Suggestions.Source source)
  {
    Suggestions s = new Suggestions(null, null);
    s.count = 1;
    s.suggestions[0] = text;
    s.sources[0] = source;
    return s;
  }

  private static Suggestions singleSuggestionWithFeedback(String text,
      Suggestions.LearnFeedback feedback)
  {
    Suggestions s = singleSuggestion(text, Suggestions.Source.ENTERED_TEXT);
    s.learn_feedback = feedback;
    s.learn_feedback_word = text;
    return s;
  }

  private static void assertLongCandidateLabel(String label, TextView view,
      String expected, int normalMaxTextSize)
  {
    assertEquals(label + " candidate must display the source-free word itself; source metadata must not be appended as overflow-visible suffix text.",
        expected, view.getText().toString());
    assertEquals(label + " candidate labels must stay single-line so long suggestions shrink instead of wrapping into the keyboard row.",
        1, view.getMaxLines());
    assertEquals(label + " candidate labels must use uniform autosizing so long suggestions shrink inside the visible strip instead of overflowing.",
        TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM, view.getAutoSizeTextType());
    assertTrue(label + " candidate autosizing must use a smaller max size for words over ten letters instead of waiting for truncation.",
        view.getAutoSizeMaxTextSize() < normalMaxTextSize);
  }

  private static TextView candidateTextView(Context context, int id)
  {
    TextView view = new TextView(context);
    view.setId(id);
    return view;
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

  private static final class RecordingHandler implements Config.IKeyEventHandler
  {
    String entered = null;
    String swiped = null;

    public void key_down(KeyValue value, boolean is_swipe) {}
    public void key_up(KeyValue value, Pointers.Modifiers mods, TouchTrace.Entry touch) {}
    public void key_cancel(KeyValue value, Pointers.Modifiers mods) {}
    public void key_hold(KeyValue value, Pointers.Modifiers mods, int hold_count) {}
    public void mods_changed(Pointers.Modifiers mods) {}

    public void suggestion_entered(String text)
    {
      entered = text;
    }

    public void suggestion_swiped_up(String text)
    {
      swiped = text;
    }

    public void keyboard_swiped_up() {}

    public void keyboard_swiped_down() {}
  }

  private static final class RecordingCallback implements Suggestions.Callback
  {
    int publish_count = 0;
    int count = -1;
    String[] suggestions = new String[Suggestions.MAX_COUNT];
    Suggestions.Source[] sources = new Suggestions.Source[Suggestions.MAX_COUNT];

    public void set_suggestions(Suggestions published)
    {
      publish_count++;
      count = published.count;
      suggestions = Arrays.copyOf(published.suggestions,
          published.suggestions.length);
      sources = Arrays.copyOf(published.sources, published.sources.length);
    }
  }
}
