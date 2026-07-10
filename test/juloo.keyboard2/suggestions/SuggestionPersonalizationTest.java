package juloo.keyboard2.suggestions;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
public class SuggestionPersonalizationTest
{
  private SharedPreferences _prefs;

  @Before
  public void setUp()
  {
    _prefs = RuntimeEnvironment.getApplication().getSharedPreferences(
        "decoder_personalization_test", Context.MODE_PRIVATE);
    _prefs.edit().clear().commit();
  }

  @After
  public void tearDown()
  {
    _prefs.edit().clear().commit();
  }

  @Test
  public void learned_prefixes_persist_and_order_by_count_then_text()
  {
    PersonalizationStore store = new PersonalizationStore(_prefs);
    record(store, "cazoo", 3);
    record(store, "cabin", 2);
    record(store, "camel", 2);

    PersonalizationStore reloaded = new PersonalizationStore(_prefs);

    assertEquals("Learned completions must survive a keyboard restart and use count-first, deterministic alphabetical tie-breaking.",
        Arrays.asList("cazoo", "cabin", "camel"),
        reloaded.suggest_words("CA", 3));
  }

  @Test
  public void next_word_predictions_expose_bigram_counts_in_deterministic_order()
  {
    PersonalizationStore store = PersonalizationStore.empty();
    pair(store, "good", "morning", 3);
    pair(store, "good", "night", 2);
    pair(store, "good", "noon", 2);
    store.reset_context();
    store.record_word("good");

    Decoder.Result result = decode("", store, enabledConfig(), 1);
    Decoder.Candidate[] words = result.words();

    assertEquals("Only the deterministic top three next words belong in the candidate strip.",
        3, words.length);
    assertEquals("The strongest learned phrase must be first.",
        "morning", words[0].surface);
    assertEquals("Equal-count next words must use stable lexical ordering.",
        "night", words[1].surface);
    assertEquals("Equal-count next words must use stable lexical ordering.",
        "noon", words[2].surface);
    assertEquals(3, words[0].bigramCount);
    assertEquals(2, words[1].bigramCount);
    assertEquals(Decoder.Role.NEXT_WORD, words[0].role);
  }

  @Test
  public void decoder_keeps_literal_and_returns_the_same_top_three_every_time()
  {
    PersonalizationStore store = PersonalizationStore.empty();
    record(store, "cazoo", 5);
    record(store, "cabin", 4);
    record(store, "camel", 3);
    record(store, "candle", 2);

    Decoder.Result first = decode("ca", store, enabledConfig(), 7);
    List<String> expected = surfaces(first);

    assertEquals("The visible decoder contract is exactly three deterministic word slots.",
        3, expected.size());
    assertTrue("The exact text the user typed must remain available even when learned completions compete for the top slots.",
        expected.contains("ca"));
    assertNotNull("Literal metadata must remain available for commit and learning decisions.",
        first.literal);
    assertEquals("ca", first.literal.surface);
    assertEquals(Decoder.Role.ENTERED_LITERAL, first.literal.role);

    for (int generation = 8; generation < 28; generation++)
      assertEquals("Hash-map iteration or provider timing must never reshuffle the same top-three result.",
          expected, surfaces(decode("ca", store, enabledConfig(), generation)));
  }

  @Test
  public void displayed_candidates_preserve_initial_case_without_mutating_canonical_text()
  {
    PersonalizationStore store = PersonalizationStore.empty();
    record(store, "cazoo", 3);

    Decoder.Result result = decode("Ca", store, enabledConfig(), 31);
    Decoder.Candidate learned = find(result, "cazoo");

    assertNotNull("A learned completion must remain visible for a capitalized prefix.",
        learned);
    assertEquals("Visible candidate casing must follow the user's initial capital.",
        "Cazoo", learned.surface);
    assertEquals("Ranking and personalization must continue using normalized canonical text.",
        "cazoo", learned.canonical);
  }

  @Test
  public void disabled_or_unsafe_decoder_gates_publish_no_candidates_but_retain_literal()
  {
    PersonalizationStore store = PersonalizationStore.empty();
    record(store, "cazoo", 4);
    Decoder.DecoderConfig disabled = new Decoder.DecoderConfig(
        false, false, false, true);
    Decoder.DecoderConfig unsafe = new Decoder.DecoderConfig(
        true, true, true, false);

    for (Decoder.DecoderConfig config : new Decoder.DecoderConfig[] {
        disabled, unsafe })
    {
      Decoder.Result result = decode("Ca", store, config, 41);
      assertEquals("Typing assistance gates must prevent stale or private-context candidates from being published.",
          0, result.words().length);
      assertNull("Typing assistance gates must prevent commit-boundary autocorrect.",
          result.autocorrection);
      assertEquals("Even when assistance is gated, the immutable result must retain the exact literal for safe fallback commit.",
          "Ca", result.literal.surface);
    }
  }

  @Test
  public void correction_pairs_persist_and_reload_with_exact_counts()
  {
    PersonalizationStore store = new PersonalizationStore(_prefs);
    correction(store, "GELLO", "Hello", 3);

    assertEquals("Correction evidence must be attached to the normalized source-target pair.",
        3, store.correction_count("gello", "hello"));

    PersonalizationStore reloaded = new PersonalizationStore(_prefs);
    List<PersonalizationStore.ScoredCorrection> corrections =
      reloaded.suggest_corrections_with_counts("GELLO",
          Decoder.Geometry.from(null), 3);

    assertEquals("Persisted correction evidence must survive a keyboard restart without creating unrelated targets.",
        1, corrections.size());
    assertEquals("hello", corrections.get(0).target);
    assertEquals(3, corrections.get(0).exactCount);
    assertEquals(0, corrections.get(0).relatedCount);
  }

  @Test
  public void correction_only_evidence_survives_restart_and_can_fix_unknown_text()
  {
    PersonalizationStore store = new PersonalizationStore(_prefs);
    for (int i = 0; i < 4; i++)
      store.record_correction("thus", "this");

    assertEquals("Editor-verified typo evidence must not teach every unrecognized target as an ordinary unigram.",
        0, store.word_count("this"));
    PersonalizationStore reloaded = new PersonalizationStore(_prefs);
    assertEquals("Correction-only evidence must survive restart without a separate learned-word row.",
        4, reloaded.correction_count("thus", "this"));

    Decoder.Result result = decode("thus", reloaded, enabledConfig(), 99);
    Decoder.Candidate candidate = find(result, "this");
    assertNotNull("Repeated correction-only evidence must create a usable target even when no dictionary recognizes it.",
        candidate);
    assertTrue("A correction target must be reversible through the learned-candidate affordance.",
        candidate.learned);
    assertNotNull("Four exact manual corrections must make the unknown target actionable at the next boundary.",
        result.autocorrection);
    assertEquals("this", result.autocorrection.canonical);

    Decoder.Result related = decode("thos", reloaded, enabledConfig(), 100);
    Decoder.Candidate relatedCandidate = find(related, "this");
    assertNotNull("A same-index adjacent typo must inherit weaker evidence from the correction-only model.",
        relatedCandidate);
    assertEquals(0, relatedCandidate.exactCorrectionCount);
    assertEquals(4, relatedCandidate.relatedCorrectionCount);
    assertNotNull("Repeated exact learning must make the adjacent unknown typo actionable without a dictionary.",
        related.autocorrection);
    assertEquals("this", related.autocorrection.canonical);
  }

  @Test
  public void exact_user_corrections_ignore_geometry_and_allow_two_edits()
  {
    PersonalizationStore store = PersonalizationStore.empty();
    correction(store, "thys", "this", 4);

    assertEquals("A repeated exact correction must be stored even when the substituted keys are not adjacent.",
        4, store.correction_count("thys", "this"));
    Decoder.Result distant = decode("thys", store, enabledConfig(), 101);
    assertNotNull("Four explicit thys-to-this corrections must make this actionable without relying on key distance.",
        distant.autocorrection);
    assertEquals("this", distant.autocorrection.canonical);
    assertEquals(4, distant.autocorrection.exactCorrectionCount);

    correction(store, "thxz", "this", 4);
    assertEquals("Editor-verified exact pairs may contain two bounded edits rather than only one substitution or transposition.",
        4, store.correction_count("thxz", "this"));
    Decoder.Result twoEdits = decode("thxz", store, enabledConfig(), 102);
    Decoder.Candidate candidate = find(twoEdits, "this");
    assertNotNull(candidate);
    assertNotNull("Four exact two-edit corrections must become actionable, while ordinary unlearned two-edit guesses remain disabled.",
        twoEdits.autocorrection);
    assertEquals("this", twoEdits.autocorrection.canonical);

    correction(store, "txxz", "this", 4);
    assertEquals("Three-edit rewrites remain outside the bounded exact-correction model.",
        0, store.correction_count("txxz", "this"));
    assertNull("Rejected three-edit evidence must not leak through another source's correction target.",
        find(decode("txxz", store, enabledConfig(), 103), "this"));
  }

  @Test
  public void textual_correction_bound_covers_two_edit_combinations()
  {
    assertTrue("Two substitutions are valid exact user evidence.",
        PersonalizationStore.is_plausible_correction("thxz", "this"));
    assertTrue("One substitution plus one insertion is valid exact user evidence.",
        PersonalizationStore.is_plausible_correction("thx", "this"));
    assertTrue("Two inserted letters are valid exact user evidence.",
        PersonalizationStore.is_plausible_correction("th", "this"));
    assertTrue("Two deleted letters are valid exact user evidence.",
        PersonalizationStore.is_plausible_correction("thiiss", "this"));
    assertTrue("The existing adjacent-transposition case remains one valid edit.",
        PersonalizationStore.is_plausible_correction("htis", "this"));
    assertFalse("Three substitutions remain too broad for automatic learning.",
        PersonalizationStore.is_plausible_correction("txxz", "this"));
    assertFalse("A three-letter length difference remains too broad.",
        PersonalizationStore.is_plausible_correction("thisabc", "this"));
  }

  @Test
  public void unicode_correction_identity_survives_reload_and_lookup_folding()
  {
    _prefs.edit()
      .putStringSet(PersonalizationStore.PREF_WORDS, setOf(
            "re\u0301sume\u0301\t2", "résumé\t1"))
      .putStringSet(PersonalizationStore.PREF_BIGRAMS, setOf(
            "cafe\u0301 re\u0301sume\u0301\t3"))
      .putStringSet(PersonalizationStore.PREF_CORRECTIONS, setOf(
            "thy\u0301s\tthis\t4", "thýs\tthis\t3"))
      .commit();

    PersonalizationStore store = new PersonalizationStore(_prefs);
    assertEquals("Reload must NFC-normalize equivalent learned-word rows without losing the stronger count.",
        2, store.word_count("résumé"));
    assertEquals("Reload must NFC-normalize both words in persisted bigram keys.",
        3, store.bigram_count("café", "résumé"));
    assertEquals("Equivalent decomposed and precomposed correction rows must merge without inflating observations.",
        4, store.correction_count("thýs", "this"));

    Decoder.Result result = decode("thýs", store, enabledConfig(), 104);
    assertNotNull("The decoder must query exact corrections with the persisted source identity rather than its accent-folded dictionary key.",
        result.autocorrection);
    assertEquals("this", result.autocorrection.surface);
    assertEquals(4, result.autocorrection.exactCorrectionCount);
  }

  @Test
  public void exact_accent_target_remains_distinct_from_folded_literal()
  {
    PersonalizationStore store = PersonalizationStore.empty();
    correction(store, "resume", "re\u0301sume\u0301", 4);

    assertEquals("Correction storage must preserve the NFC target surface.",
        4, store.correction_count("resume", "résumé"));
    assertEquals("Dictionary lookup must retain compose-substitution folding.",
        "resume", Decoder.normalize("résumé"));

    Decoder.Result result = decode("resume", store, enabledConfig(), 105);
    assertNotNull("Four exact accent-bearing corrections must remain actionable even when source and target share a folded lookup key.",
        result.autocorrection);
    assertEquals("The correction must emit the learned target surface rather than the typed literal.",
        "résumé", result.autocorrection.surface);
    assertEquals("Scoring still uses the folded canonical key.",
        "resume", result.autocorrection.canonical);
    assertEquals("Literal presentation must remain the exact entered text.",
        "resume", result.literal.surface);

    int foldedCandidates = 0;
    for (Decoder.Candidate candidate : result.words())
      if ("resume".equals(candidate.canonical))
        foldedCandidates++;
    assertEquals("Fold-equivalent providers must remain one ranked candidate rather than duplicate strip entries.",
        1, foldedCandidates);
  }

  @Test
  public void malformed_and_duplicate_correction_rows_are_sanitized_on_reload()
  {
    _prefs.edit()
      .putStringSet(PersonalizationStore.PREF_WORDS, setOf("hello\t2"))
      .putStringSet(PersonalizationStore.PREF_CORRECTIONS, setOf(
            "gello\thello\t2",
            "gello\thello\t1",
            "broken",
            "\thello\t2",
            "gello\t\t2",
            "gello\thello",
            "gello\thello\t0",
            "gello\thello\t16",
            "gello\thello\t2\textra",
            "Gello\thello\t2",
            "wello\tjello\t5"))
      .commit();

    PersonalizationStore store = new PersonalizationStore(_prefs);
    List<PersonalizationStore.ScoredCorrection> corrections =
      store.suggest_corrections_with_counts("gello",
          Decoder.Geometry.from(null), 5);

    assertEquals("Only normalized, plausible rows with valid bounded counts may load.",
        1, corrections.size());
    assertEquals("hello", corrections.get(0).target);
    assertEquals("Duplicate persisted rows must deterministically retain the strongest valid count.",
        2, corrections.get(0).exactCount);
    assertEquals("A valid editor-verified correction row must remain usable without a separate learned-word row.",
        5, store.correction_count("wello", "jello"));
  }

  @Test
  public void correction_counts_cap_and_the_513th_pair_evicts_a_weakest_pair()
  {
    PersonalizationStore store = new PersonalizationStore(_prefs);
    correction(store, "gello", "hello", 20);
    assertEquals("A noisy typo pair must saturate instead of growing without bound.",
        15, store.correction_count("gello", "hello"));

    store.clear();
    for (int i = 0; i < 512; i++)
    {
      String suffix = alphaSuffix(i);
      correction(store, "m" + suffix, "n" + suffix, 1);
    }
    correction(store, "aaaa", "baaa", 2);

    Set<String> persisted = _prefs.getStringSet(
        PersonalizationStore.PREF_CORRECTIONS, null);
    assertNotNull(persisted);
    assertEquals("The persisted correction model must remain bounded to 512 source-target pairs.",
        512, persisted.size());
    assertEquals("A stronger new pair must survive bounded-model eviction.",
        2, store.correction_count("aaaa", "baaa"));
    assertEquals("Equal weakest pairs use deterministic lexical eviction rather than unbounded growth.",
        0, store.correction_count("matr", "natr"));
    assertEquals("Eviction must not erase unrelated pairs that precede the deterministic weakest entry.",
        1, store.correction_count("maaa", "naaa"));

    PersonalizationStore reloaded = new PersonalizationStore(_prefs);
    assertEquals("The bounded eviction result must survive reload.",
        2, reloaded.correction_count("aaaa", "baaa"));
    assertEquals(0, reloaded.correction_count("matr", "natr"));
  }

  @Test
  public void unigram_repetition_does_not_become_global_pair_evidence()
  {
    PersonalizationStore store = PersonalizationStore.empty();
    correction(store, "gello", "hello", 1);
    record(store, "hello", 5);

    assertEquals("Accepted target words continue to build independent unigram evidence.",
        6, store.word_count("hello"));
    assertEquals("Repeating the target literally must not inflate its typo-pair count.",
        1, store.correction_count("gello", "hello"));
    PersonalizationStore.ScoredCorrection exact =
      store.suggest_corrections_with_counts("gello",
          Decoder.Geometry.from(null), 3).get(0);
    assertEquals(1, exact.exactCount);
    assertTrue("Pair evidence must never be offered globally to an unrelated source.",
        store.suggest_corrections_with_counts("teh",
          Decoder.Geometry.from(null), 3).isEmpty());
  }

  @Test
  public void unlearning_removes_corrections_where_word_is_source_or_target()
  {
    PersonalizationStore store = new PersonalizationStore(_prefs);
    correction(store, "gello", "hello", 2);
    correction(store, "hello", "jello", 2);
    correction(store, "wrold", "world", 1);

    assertTrue(store.unlearn_word("HELLO"));

    PersonalizationStore reloaded = new PersonalizationStore(_prefs);
    assertEquals("Unlearning must remove pairs whose target is the selected word.",
        0, reloaded.correction_count("gello", "hello"));
    assertEquals("Unlearning must also remove pairs whose source is the selected word.",
        0, reloaded.correction_count("hello", "jello"));
    assertEquals("Unlearning one word must preserve unrelated typo evidence.",
        1, reloaded.correction_count("wrold", "world"));
  }

  @Test
  public void clear_removes_all_model_keys_and_resets_context()
  {
    PersonalizationStore store = new PersonalizationStore(_prefs);
    store.record_word("alpha");
    store.record_commit("beta", "beto");

    assertTrue(_prefs.contains(PersonalizationStore.PREF_WORDS));
    assertTrue(_prefs.contains(PersonalizationStore.PREF_BIGRAMS));
    assertTrue(_prefs.contains(PersonalizationStore.PREF_CORRECTIONS));
    assertEquals("beta", store.previous_word());

    store.clear();

    assertFalse("Clear must remove learned words from persistent storage.",
        _prefs.contains(PersonalizationStore.PREF_WORDS));
    assertFalse("Clear must remove next-word pairs from persistent storage.",
        _prefs.contains(PersonalizationStore.PREF_BIGRAMS));
    assertFalse("Clear must remove typo pairs from persistent storage.",
        _prefs.contains(PersonalizationStore.PREF_CORRECTIONS));
    assertNull("Clear must reset the active in-memory context as well as persisted data.",
        store.previous_word());
    assertFalse(PersonalizationStore.has_data(_prefs));

    PersonalizationStore reloaded = new PersonalizationStore(_prefs);
    assertFalse(reloaded.is_learned("alpha"));
    assertTrue(reloaded.suggest_corrections_with_counts("beto",
          Decoder.Geometry.from(null), 3).isEmpty());
    assertNull(reloaded.previous_word());
  }

  @Test
  public void exact_pair_observations_one_through_four_raise_weight_and_rank_monotonically()
  {
    PersonalizationStore store = PersonalizationStore.empty();
    int previousScore = Integer.MAX_VALUE;
    int previousRank = Integer.MAX_VALUE;
    for (int count = 1; count <= 4; count++)
    {
      correction(store, "gello", "hello", 1);
      Decoder.Result result =
        decode("gello", store, enabledConfig(), 100 + count);
      Decoder.Candidate candidate = find(result, "hello");
      int rank = rank(result, "hello");

      assertNotNull(candidate);
      assertTrue("Every exact observation must keep or improve the target's visible rank.",
          rank >= 0 && rank <= previousRank);
      assertEquals("Exact correction metadata must expose the actual pair count.",
          count, candidate.exactCorrectionCount);
      assertEquals(0, candidate.relatedCorrectionCount);
      assertEquals("Each exact observation contributes twice the related-evidence weight through the four-event threshold.",
          count * 2, candidate.correctionWeight);
      assertTrue("Every additional exact pair observation must strictly improve the target's deterministic score.",
          candidate.totalQ8 < previousScore);
      previousScore = candidate.totalQ8;
      previousRank = rank;
    }
    assertEquals("Four exact observations must promote the correction target to the first visible rank.",
        0, previousRank);
  }

  @Test
  public void protected_learned_literal_changes_only_on_the_fourth_exact_pair()
  {
    PersonalizationStore store = PersonalizationStore.empty();
    store.record_word("gello");

    for (int count = 1; count <= 4; count++)
    {
      correction(store, "gello", "hello", 1);
      Decoder.Result result =
        decode("gello", store, enabledConfig(), 300 + count);
      if (count < 4)
      {
        assertNull("A learned literal must remain unchanged before four exact pair observations.",
            result.autocorrection);
      }
      else
      {
        assertNotNull("The fourth exact pair observation must unlock a decisive learned correction.",
            result.autocorrection);
        assertEquals("hello", result.autocorrection.canonical);
        assertEquals(4, result.autocorrection.exactCorrectionCount);
      }
    }
  }


  @Test
  public void only_same_index_adjacent_sources_share_weaker_related_evidence()
  {
    PersonalizationStore store = PersonalizationStore.empty();
    correction(store, "gello", "hello", 4);
    Decoder.Geometry geometry = Decoder.Geometry.from(null);

    PersonalizationStore.ScoredCorrection exact =
      store.suggest_corrections_with_counts("gello", geometry, 3).get(0);
    PersonalizationStore.ScoredCorrection related =
      store.suggest_corrections_with_counts("jello", geometry, 3).get(0);
    assertEquals(4, exact.exactCount);
    assertEquals(0, exact.relatedCount);
    assertEquals("A same-index adjacent variant may share the learned target only as weaker related evidence.",
        0, related.exactCount);
    assertEquals(4, related.relatedCount);

    Decoder.Candidate exactCandidate = find(
        decode("gello", store, enabledConfig(), 201), "hello");
    Decoder.Candidate relatedCandidate = find(
        decode("jello", store, enabledConfig(), 202), "hello");
    assertNotNull(exactCandidate);
    assertNotNull(relatedCandidate);
    assertEquals(8, exactCandidate.correctionWeight);
    assertEquals(4, relatedCandidate.correctionWeight);
    assertTrue("Exact source evidence must outrank equally shaped related evidence.",
        exactCandidate.totalQ8 < relatedCandidate.totalQ8);

    assertTrue("A distant key at the learned index must not inherit correction evidence.",
        store.suggest_corrections_with_counts("mello", geometry, 3).isEmpty());
    assertTrue("An adjacent typo at a different character index must not inherit correction evidence.",
        store.suggest_corrections_with_counts("helko", geometry, 3).isEmpty());
    assertTrue("Related evidence requires current keyboard geometry rather than global string similarity.",
        store.suggest_corrections_with_counts("jello", null, 3).isEmpty());
  }

  @Test
  public void exact_source_recall_precedes_stronger_related_only_targets()
  {
    PersonalizationStore store = PersonalizationStore.empty();
    String[][] relatedPairs = new String[][] {
      { "jello", "hello" },
      { "dello", "fello" },
      { "rello", "tello" },
      { "cello", "vello" },
      { "gtllo", "grllo" },
      { "uello", "yello" }
    };
    for (String[] pair : relatedPairs)
      correction(store, pair[0], pair[1], 8);
    correction(store, "gello", "xello", 1);

    List<PersonalizationStore.ScoredCorrection> all =
      store.suggest_corrections_with_counts("gello",
          Decoder.Geometry.from(null), 10);
    assertEquals("The fixture must expose one exact target and six stronger related-only targets.",
        7, all.size());
    assertEquals("Any exact source observation must survive the bounded recall set ahead of even saturated related-only evidence.",
        "xello", all.get(0).target);
    assertEquals(1, all.get(0).exactCount);
    assertEquals(0, all.get(0).relatedCount);
  }

  @Test
  public void unlearning_removes_word_and_every_bigram_that_contains_it()
  {
    PersonalizationStore store = new PersonalizationStore(_prefs);
    pair(store, "good", "morning", 2);
    pair(store, "good", "night", 1);

    assertTrue(store.unlearn_word("MORNING"));
    PersonalizationStore reloaded = new PersonalizationStore(_prefs);
    reloaded.reset_context();
    reloaded.record_word("good");

    assertFalse("Unlearning is case-insensitive and must remove the selected word itself.",
        reloaded.is_learned("morning"));
    assertEquals("Unlearning one word must remove phrases containing it without erasing unrelated next-word history.",
        Arrays.asList("night"), reloaded.suggest_next_words(3));
  }

  private static Decoder.Result decode(String typed, PersonalizationStore store,
      Decoder.DecoderConfig config, long generation)
  {
    Decoder.RequestKey key = new Decoder.RequestKey(
        1, generation, generation, 1, 1, 1, 1);
    Decoder.Request request = new Decoder.Request(key, typed,
        (TouchTrace.Snapshot)null, Decoder.Geometry.from(null), config);
    return new Decoder().decode(request, null, null, null, store, false);
  }

  private static Decoder.DecoderConfig enabledConfig()
  {
    return new Decoder.DecoderConfig(true, true, true, true);
  }

  private static Decoder.Candidate find(Decoder.Result result, String canonical)
  {
    for (Decoder.Candidate candidate : result.words())
      if (canonical.equals(candidate.canonical))
        return candidate;
    return null;
  }

  private static int rank(Decoder.Result result, String canonical)
  {
    Decoder.Candidate[] words = result.words();
    for (int i = 0; i < words.length; i++)
      if (canonical.equals(words[i].canonical))
        return i;
    return -1;
  }

  private static List<String> surfaces(Decoder.Result result)
  {
    List<String> out = new ArrayList<String>();
    for (Decoder.Candidate candidate : result.words())
      out.add(candidate.surface);
    return out;
  }

  private static void record(PersonalizationStore store, String word, int count)
  {
    for (int i = 0; i < count; i++)
    {
      store.reset_context();
      store.record_word(word);
    }
  }

  private static void pair(PersonalizationStore store, String first,
      String second, int count)
  {
    for (int i = 0; i < count; i++)
    {
      store.reset_context();
      store.record_word(first);
      store.record_word(second);
    }
  }

  private static void correction(PersonalizationStore store, String source,
      String target, int count)
  {
    for (int i = 0; i < count; i++)
    {
      store.reset_context();
      store.record_commit(target, source);
    }
  }

  private static String alphaSuffix(int value)
  {
    char first = (char)('a' + (value / (26 * 26)) % 26);
    char second = (char)('a' + (value / 26) % 26);
    char third = (char)('a' + value % 26);
    return new String(new char[] { first, second, third });
  }

  private static Set<String> setOf(String... values)
  {
    return new HashSet<String>(Arrays.asList(values));
  }

}
