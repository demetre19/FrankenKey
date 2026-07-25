package juloo.keyboard2.autocorrect;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import juloo.keyboard2.TouchTrace;
import juloo.keyboard2.suggestions.Decoder;
import org.junit.Test;
import static org.junit.Assert.*;

public class AutocorrectScoringTest
{
  private static final int UNKNOWN_LITERAL_TOTAL_Q8 = 12 * 256;
  @Test
  public void scorer_identifies_transposition_omission_extra_tap_and_substitution()
      throws Exception
  {
    assertEdit("teh", "the", Decoder.EDIT_TRANSPOSITION,
        "A neighboring transposition must be one bounded edit, not two unrelated substitutions.");
    assertEdit("helo", "hello", Decoder.EDIT_OMISSION,
        "A missed key must be represented as an omission so the decoder can price it conservatively.");
    assertEdit("helllo", "hello", Decoder.EDIT_EXTRA_TAP,
        "A duplicated tap must be represented as an extra tap rather than a broad rewrite.");
    assertEdit("gello", "hello", Decoder.EDIT_SUBSTITUTION,
        "A one-key typo must remain a substitution with exactly one edit.");
  }

  @Test
  public void touch_coordinates_outrank_fixed_qwerty_distance()
      throws Exception
  {
    Score fixedHello = score("gello", "hello", null);
    Score fixedJello = score("gello", "jello", null);
    assertTrue("Without touch coordinates, the adjacent H key must beat the more distant J key.",
        fixedHello.spatialQ8 < fixedJello.spatialQ8);

    TouchTrace touches = new TouchTrace();
    touches.add(TouchTrace.entry(140f, 100f, 100f, 100f, 20f, 20f));
    for (int i = 1; i < 5; i++)
      touches.add(null);
    Score touchedHello = score("gello", "hello", touches.snapshot());
    Score touchedJello = score("gello", "jello", touches.snapshot());

    assertTrue("An actual first tap near J must override the literal G-key neighborhood and rank jello ahead of hello.",
        touchedJello.spatialQ8 < touchedHello.spatialQ8);
  }

  @Test
  public void swiftkey_parity_accepts_clear_single_edit_word_repairs()
      throws Exception
  {
    assertClearHunspellCorrection("helllo", "hello",
        Decoder.EDIT_EXTRA_TAP,
        "A repeated key must not outweigh a complete dictionary repair when it is the clear one-edit winner.");
    assertClearHunspellCorrection("corrextion", "correction",
        Decoder.EDIT_SUBSTITUTION,
        "A centered tap on the adjacent wrong key must still allow a decisive lexical correction.");
    assertClearHunspellCorrection("definitly", "definitely",
        Decoder.EDIT_OMISSION,
        "A single omitted letter must be correctable without prior personalization.");
    assertClearHunspellCorrection("neccessary", "necessary",
        Decoder.EDIT_EXTRA_TAP,
        "A single extra letter must be correctable without prior personalization.");
  }

  @Test
  public void swiftkey_parity_accepts_clear_two_edit_word_repairs()
      throws Exception
  {
    String typed = "kybiard";
    String target = "keyboard";
    Score score = score(typed, target, centeredTouches(typed));
    assertEquals("The SwiftKey reference repairs this bounded two-edit misspelling.",
        2, score.editCount);

    Decoder.Request request = request(typed);
    Decoder.Candidate literal = candidate(typed, typed, Decoder.SOURCE_LITERAL,
        UNKNOWN_LITERAL_TOTAL_Q8, 0, 0, false, false, true,
        Decoder.Role.ENTERED_LITERAL);
    Decoder.Candidate rewrite = candidate(target, target,
        Decoder.SOURCE_HUNSPELL, 0, score.editCount, score.editMask,
        true, false, true, Decoder.Role.WORD);
    assertEquals("A clear recognized two-edit repair must autocorrect without requiring prior personalization.",
        target, choose(request, Arrays.asList(rewrite, literal), literal, true,
          Decoder.Failure.NONE).canonical);
  }

  @Test
  public void swiftkey_parity_ranks_same_length_hello_before_shorter_help()
      throws Exception
  {
    Decoder.Request request = request("hrllp");
    Decoder.Candidate literal = candidate("hrllp", "hrllp",
        Decoder.SOURCE_LITERAL, UNKNOWN_LITERAL_TOTAL_Q8, 0, 0, false, false,
        true, Decoder.Role.ENTERED_LITERAL);
    Score helloScore = score("hrllp", "hello", centeredTouches("hrllp"));
    Score helpScore = score("hrllp", "help", centeredTouches("hrllp"));
    Decoder.Candidate hello = candidate("hello", "hello",
        Decoder.SOURCE_CDICT_SPATIAL, 0, helloScore.editCount,
        helloScore.editMask, true, false, true, Decoder.Role.WORD);
    Decoder.Candidate help = candidate("help", "help",
        Decoder.SOURCE_CDICT_SPATIAL, 0, helpScore.editCount,
        helpScore.editMask, true, false, true, Decoder.Role.WORD);
    List<Decoder.Candidate> ranked =
      new java.util.ArrayList<Decoder.Candidate>(
          Arrays.asList(help, hello, literal));

    sortForRequest(request, ranked);

    assertEquals("Same-length hello must be the primary prediction before shorter help.",
        "hello", ranked.get(0).canonical);
    assertEquals("The primary same-length prediction must also be the committed correction.",
        "hello", choose(request, ranked, literal, true,
          Decoder.Failure.NONE).canonical);
  }

  @Test
  public void conservative_autocorrect_requires_clear_one_edit_winner()
      throws Exception
  {
    Decoder.Request request = request("teh");
    Decoder.Candidate literal = candidate("teh", "teh", Decoder.SOURCE_LITERAL,
        8192, 0, 0, false, false, true, Decoder.Role.ENTERED_LITERAL);
    Decoder.Candidate winner = candidate("the", "the",
        Decoder.SOURCE_CDICT_SPATIAL, 0, 1, Decoder.EDIT_TRANSPOSITION,
        true, false, true, Decoder.Role.WORD);

    assertEquals("A clear recognized one-edit transposition may be corrected at the commit boundary.",
        "the", choose(request, Arrays.asList(winner, literal), literal, true,
          Decoder.Failure.NONE).surface);
    assertNull("Turning autocorrect off must keep the literal even when a strong candidate exists.",
        choose(request, Arrays.asList(winner, literal), literal, false,
          Decoder.Failure.NONE));

    Decoder.Candidate weakWinner = candidate("the", "the",
        Decoder.SOURCE_CDICT_SPATIAL, 7800, 1, Decoder.EDIT_TRANSPOSITION,
        true, false, true, Decoder.Role.WORD);
    assertNull("A candidate without a two-point literal margin is too risky to commit automatically.",
        choose(request, Arrays.asList(weakWinner, literal), literal, true,
          Decoder.Failure.NONE));

    Decoder.Candidate closeRunner = candidate("ten", "ten",
        Decoder.SOURCE_CDICT_SPATIAL, 64, 1, Decoder.EDIT_SUBSTITUTION,
        true, false, true, Decoder.Role.WORD);
    assertNull("Two nearly tied corrections are ambiguous and must leave the user's text unchanged.",
        choose(request, Arrays.asList(winner, closeRunner, literal), literal,
          true, Decoder.Failure.NONE));
    assertNull("A resource failure must disable automatic replacement rather than guessing from incomplete evidence.",
        choose(request, Arrays.asList(winner, literal), literal, true,
          Decoder.Failure.RESOURCE));
  }

  @Test
  public void truncated_search_still_accepts_clear_recognized_one_edit_winner()
      throws Exception
  {
    Decoder.Request request = request("wirld");
    Decoder.Candidate literal = candidate("wirld", "wirld",
        Decoder.SOURCE_LITERAL, 8192, 0, 0, false, false, true,
        Decoder.Role.ENTERED_LITERAL);
    Decoder.Candidate winner = candidate("world", "world",
        Decoder.SOURCE_CDICT_SPATIAL, 0, 1, Decoder.EDIT_SUBSTITUTION,
        true, false, false, Decoder.Role.WORD);

    Decoder.Candidate correction = choose(request,
        Arrays.asList(winner, literal), literal, true,
        Decoder.Failure.NATIVE_TRUNCATED);

    assertNotNull("A truncated search may still commit its clear recognized one-edit winner; refusing it reproduces the shown-but-not-applied Space bug.",
        correction);
    assertEquals("world", correction.surface);
  }

  @Test
  public void short_word_uses_clear_transposition_over_distant_frequency_winner()
      throws Exception
  {
    Decoder.Request request = request("teh");
    Decoder.Candidate literal = candidate("teh", "teh", Decoder.SOURCE_LITERAL,
        8192, 0, 0, false, false, true, Decoder.Role.ENTERED_LITERAL);
    Decoder.Candidate frequentButDistant = spatialCandidate("tech", "tech",
        Decoder.SOURCE_CDICT_SPATIAL, -2560, 6 * 256, 1,
        Decoder.EDIT_OMISSION, true, false, true, Decoder.Role.WORD);
    Decoder.Candidate closerAlternative = spatialCandidate("the", "the",
        Decoder.SOURCE_CDICT_SPATIAL, 512, 2 * 256, 1,
        Decoder.EDIT_TRANSPOSITION, true, false, true, Decoder.Role.WORD);

    assertEquals("A clear short transposition must beat a geometrically worse insertion even when dictionary frequency ranks the insertion first.",
        "the", choose(request,
          Arrays.asList(frequentButDistant, closerAlternative, literal),
          literal, true, Decoder.Failure.NONE).canonical);
  }

  @Test
  public void ambiguous_primary_short_transposition_remains_literal()
      throws Exception
  {
    Decoder.Request request = request("hwo");
    Decoder.Candidate literal = candidate("hwo", "hwo",
        Decoder.SOURCE_LITERAL, 8192, 0, 0, false, false, true,
        Decoder.Role.ENTERED_LITERAL);
    Decoder.Candidate primary = spatialCandidate("who", "who",
        Decoder.SOURCE_CDICT_EXACT | Decoder.SOURCE_CDICT_SPATIAL
          | Decoder.SOURCE_HUNSPELL | Decoder.SOURCE_HUNSPELL_PRIMARY,
        -1408, 2 * 256, 1, Decoder.EDIT_TRANSPOSITION,
        true, false, true, Decoder.Role.WORD);
    Decoder.Candidate alternate = spatialCandidate("how", "how",
        Decoder.SOURCE_CDICT_EXACT | Decoder.SOURCE_CDICT_SPATIAL
          | Decoder.SOURCE_HUNSPELL,
        -1408, 2 * 256, 1, Decoder.EDIT_TRANSPOSITION,
        true, false, true, Decoder.Role.WORD);

    assertNull("A provider's primary result must not force an ambiguous short transposition when another complete candidate is tied.",
        choose(request, Arrays.asList(primary, alternate, literal), literal,
          true, Decoder.Failure.NONE));
  }

  @Test
  public void same_length_guess_yields_to_plausible_length_repair()
      throws Exception
  {
    Decoder.Request request = request("curor");
    Decoder.Candidate literal = candidate("curor", "curor",
        Decoder.SOURCE_LITERAL, 8192, 0, 0, false, false, true,
        Decoder.Role.ENTERED_LITERAL);
    Decoder.Candidate substitution = spatialCandidate("furor", "furor",
        Decoder.SOURCE_CDICT_EXACT | Decoder.SOURCE_CDICT_SPATIAL
          | Decoder.SOURCE_HUNSPELL | Decoder.SOURCE_HUNSPELL_PRIMARY,
        -448, 1088, 1, Decoder.EDIT_SUBSTITUTION,
        true, false, true, Decoder.Role.WORD);
    Decoder.Candidate missingTap = spatialCandidate("cursor", "cursor",
        Decoder.SOURCE_CDICT_EXACT | Decoder.SOURCE_CDICT_SPATIAL
          | Decoder.SOURCE_HUNSPELL,
        -256, 1536, 1, Decoder.EDIT_OMISSION,
        true, false, true, Decoder.Role.WORD);

    assertNull("A same-length guess must not replace an unknown word when a complete one-tap length repair remains plausible.",
        choose(request, Arrays.asList(substitution, missingTap, literal),
          literal, true, Decoder.Failure.NONE));
  }

  @Test
  public void primary_same_length_repair_beats_close_deletion_guess()
      throws Exception
  {
    Decoder.Request request = request("hellp");
    Decoder.Candidate literal = candidate("hellp", "hellp",
        Decoder.SOURCE_LITERAL, 3072, 0, 0, false, false, true,
        Decoder.Role.ENTERED_LITERAL);
    Decoder.Candidate deletionGuess = spatialCandidate("hell", "hell",
        Decoder.SOURCE_CDICT_EXACT | Decoder.SOURCE_CDICT_SPATIAL
          | Decoder.SOURCE_HUNSPELL,
        -384, 1536, 1, Decoder.EDIT_EXTRA_TAP,
        true, false, true, Decoder.Role.WORD);
    Decoder.Candidate primarySameLengthRepair =
      spatialCandidate("hello", "hello",
        Decoder.SOURCE_CDICT_EXACT | Decoder.SOURCE_CDICT_SPATIAL
          | Decoder.SOURCE_HUNSPELL | Decoder.SOURCE_HUNSPELL_PRIMARY,
        -227, 1693, 1, Decoder.EDIT_SUBSTITUTION,
        true, false, true, Decoder.Role.WORD);

    List<Decoder.Candidate> ranked =
      new java.util.ArrayList<Decoder.Candidate>(
        Arrays.asList(primarySameLengthRepair, literal, deletionGuess));
    sortForRequest(request, ranked);

    assertEquals("A provider's primary same-length repair must beat a close deletion guess.",
        "hello", choose(request, ranked, literal, true,
          Decoder.Failure.NONE).canonical);
  }

  @Test
  public void primary_same_length_repair_does_not_override_omission()
      throws Exception
  {
    Decoder.Request request = request("writr");
    Decoder.Candidate literal = candidate("writr", "writr",
        Decoder.SOURCE_LITERAL, 3072, 0, 0, false, false, true,
        Decoder.Role.ENTERED_LITERAL);
    Decoder.Candidate omissionRepair = spatialCandidate("writer", "writer",
        Decoder.SOURCE_CDICT_EXACT | Decoder.SOURCE_CDICT_SPATIAL
          | Decoder.SOURCE_HUNSPELL,
        -384, 1536, 1, Decoder.EDIT_OMISSION,
        true, false, true, Decoder.Role.WORD);
    Decoder.Candidate primarySameLengthRepair =
      spatialCandidate("write", "write",
        Decoder.SOURCE_CDICT_EXACT | Decoder.SOURCE_CDICT_SPATIAL
          | Decoder.SOURCE_HUNSPELL | Decoder.SOURCE_HUNSPELL_PRIMARY,
        -227, 1693, 1, Decoder.EDIT_SUBSTITUTION,
        true, false, true, Decoder.Role.WORD);
    List<Decoder.Candidate> ranked =
      new java.util.ArrayList<Decoder.Candidate>(
        Arrays.asList(primarySameLengthRepair, literal, omissionRepair));
    sortForRequest(request, ranked);

    assertEquals("A missing-letter repair must not be replaced by a primary same-length guess.",
        "writer", choose(request, ranked, literal, true,
          Decoder.Failure.NONE).canonical);
  }

  @Test
  public void complete_one_edit_length_repair_is_not_penalized_twice()
      throws Exception
  {
    Decoder.Request request = request("ths");
    Decoder.Candidate literal = candidate("ths", "ths", Decoder.SOURCE_LITERAL,
        3072, 0, 0, false, false, true, Decoder.Role.ENTERED_LITERAL);
    Decoder.Candidate sameLength = spatialCandidate("tbs", "tbs",
        Decoder.SOURCE_CDICT_EXACT | Decoder.SOURCE_HUNSPELL,
        -99, 1693, 1, Decoder.EDIT_SUBSTITUTION,
        true, false, true, Decoder.Role.WORD);
    Decoder.Candidate missingTap = spatialCandidateWithProviderRank(
        "this", "this", Decoder.SOURCE_CDICT_EXACT | Decoder.SOURCE_HUNSPELL
          | Decoder.SOURCE_HUNSPELL_PRIMARY,
        -384, 1536, 1, 1, Decoder.EDIT_OMISSION);
    Decoder.Candidate alternateMissingTap = spatialCandidateWithProviderRank(
        "thus", "thus", Decoder.SOURCE_CDICT_EXACT | Decoder.SOURCE_HUNSPELL,
        -384, 1536, 2, 1, Decoder.EDIT_OMISSION);
    List<Decoder.Candidate> ranked =
      Arrays.asList(sameLength, alternateMissingTap, missingTap, literal);
    sortForRequest(request, ranked);

    assertEquals("A complete main-dictionary and Hunspell one-tap length repair must keep its existing edit cost instead of receiving a second generic length penalty.",
        "this", ranked.get(0).canonical);
    assertEquals("An explicit primary provider may resolve an otherwise tied complete missing-tap repair.",
        "this", choose(request, ranked, literal, true,
          Decoder.Failure.NONE).canonical);
  }

  @Test
  public void longer_unknown_word_prefers_tied_omission_over_deletion()
      throws Exception
  {
    Decoder.Request request = request("shold");
    Decoder.Candidate literal = candidate("shold", "shold",
        Decoder.SOURCE_LITERAL, 3072, 0, 0, false, false, true,
        Decoder.Role.ENTERED_LITERAL);
    Decoder.Candidate deletion = spatialCandidateWithProviderRank(
        "sold", "sold", Decoder.SOURCE_CDICT_EXACT | Decoder.SOURCE_HUNSPELL,
        -384, 1536, 1, 1, Decoder.EDIT_EXTRA_TAP);
    Decoder.Candidate omission = spatialCandidateWithProviderRank(
        "should", "should",
        Decoder.SOURCE_CDICT_EXACT | Decoder.SOURCE_HUNSPELL,
        -384, 1536, 3, 1, Decoder.EDIT_OMISSION);
    List<Decoder.Candidate> ranked =
      Arrays.asList(deletion, omission, literal);
    sortForRequest(request, ranked);

    assertEquals("For a longer unknown word, an equally supported missing-tap repair must outrank deleting a typed letter.",
        "should", ranked.get(0).canonical);
    assertEquals("Provider order alone must not veto the equally scored omission repair.",
        "should", choose(request, ranked, literal, true,
          Decoder.Failure.NONE).canonical);
  }

  @Test
  public void protected_literal_requires_four_exact_events_not_related_weight()
      throws Exception
  {
    Decoder.Request request = request("teh");
    Decoder.Candidate literal = candidate("teh", "teh",
        Decoder.SOURCE_LITERAL | Decoder.SOURCE_CDICT_EXACT,
        8192, 0, 0, true, false, true, Decoder.Role.WORD);
    Decoder.Candidate threeExact = candidate("the", "the",
        Decoder.SOURCE_CDICT_SPATIAL | Decoder.SOURCE_CORRECTION,
        0, 1, Decoder.EDIT_TRANSPOSITION, 3, 0, 6,
        true, false, true, Decoder.Role.WORD);
    Decoder.Candidate fourExact = candidate("the", "the",
        Decoder.SOURCE_CDICT_SPATIAL | Decoder.SOURCE_CORRECTION,
        0, 1, Decoder.EDIT_TRANSPOSITION, 4, 0, 8,
        true, false, true, Decoder.Role.WORD);
    Decoder.Candidate relatedOnly = candidate("the", "the",
        Decoder.SOURCE_CDICT_SPATIAL | Decoder.SOURCE_CORRECTION,
        0, 1, Decoder.EDIT_TRANSPOSITION, 0, 8, 8,
        true, false, true, Decoder.Role.WORD);

    assertNull("Three exact corrections must not override a recognized literal, even with an otherwise decisive score.",
        choose(request, Arrays.asList(threeExact, literal), literal, true,
          Decoder.Failure.NONE));
    assertEquals("The fourth exact correction is the first event allowed to override a recognized literal.",
        "the", choose(request, Arrays.asList(fourExact, literal), literal, true,
          Decoder.Failure.NONE).surface);
    assertNull("Related observations may improve ranking but must never unlock protected-literal autocorrect.",
        choose(request, Arrays.asList(relatedOnly, literal), literal, true,
          Decoder.Failure.NONE));
  }

  @Test
  public void one_exact_pair_only_overrides_in_its_learned_previous_word_context()
      throws Exception
  {
    Decoder.Request request = request("vax");
    Decoder.Candidate recognizedLiteral = candidate("vax", "vax",
        Decoder.SOURCE_LITERAL | Decoder.SOURCE_CDICT_EXACT,
        8192, 0, 0, true, false, true, Decoder.Role.WORD);
    Decoder.Candidate matchingContext = contextualCandidate("vox", "vox",
        1, 1, true, 1, Decoder.EDIT_SUBSTITUTION);
    Decoder.Candidate crossProductOnly = contextualCandidate("vox", "vox",
        1, 1, false, 1, Decoder.EDIT_SUBSTITUTION);

    assertEquals("One exact editor-verified previous-source-target triple may override a recognized literal in that exact context.",
        "vox", choose(request, Arrays.asList(matchingContext,
              recognizedLiteral), recognizedLiteral, true,
            Decoder.Failure.NONE).canonical);
    assertNull("A global exact pair plus a separately learned previous-target bigram must not synthesize contextual evidence.",
        choose(request, Arrays.asList(crossProductOnly, recognizedLiteral),
          recognizedLiteral, true, Decoder.Failure.NONE));
  }

  @Test
  public void matching_context_can_unlock_a_two_letter_exact_pair()
      throws Exception
  {
    Decoder.Request request = request("zi");
    Decoder.Candidate literal = candidate("zi", "zi",
        Decoder.SOURCE_LITERAL | Decoder.SOURCE_CDICT_EXACT,
        8192, 0, 0, true, false, true, Decoder.Role.WORD);
    Decoder.Candidate learned = contextualCandidate("zo", "zo",
        1, 1, true, 1, Decoder.EDIT_SUBSTITUTION);

    Decoder.Candidate chosen = choose(request, Arrays.asList(learned, literal),
        literal, true, Decoder.Failure.NONE);
    assertNotNull("A matching learned previous-word context must make a bounded two-letter exact correction actionable after one verified event.",
        chosen);
    assertEquals("zo", chosen.canonical);
  }

  @Test
  public void exact_contextual_triple_can_unlock_three_edits_without_global_leakage()
      throws Exception
  {
    Decoder.Request request = request("aww");
    Decoder.Candidate literal = candidate("aww", "aww",
        Decoder.SOURCE_LITERAL, 8192, 0, 0, false, false, true,
        Decoder.Role.ENTERED_LITERAL);
    Decoder.Candidate contextual = contextualCandidate("see", "see",
        0, 0, true, 3, Decoder.EDIT_SUBSTITUTION);
    Decoder.Candidate globalOnly = contextualCandidate("see", "see",
        4, 0, false, 3, Decoder.EDIT_SUBSTITUTION);

    assertEquals("One exact contextual triple may replay a three-edit key mash because both source and previous word match the verified occurrence.",
        "see", choose(request, Arrays.asList(contextual, literal), literal,
          true, Decoder.Failure.NONE).canonical);
    assertNull("Three-edit evidence must never enter the four-event global pair override.",
        choose(request, Arrays.asList(globalOnly, literal), literal, true,
          Decoder.Failure.NONE));
  }

  @Test
  public void unlearned_im_autocorrects_to_contraction_but_learned_im_does_not()
      throws Exception
  {
    Decoder.Request request = request("im");
    Decoder.Candidate literal = candidate("im", "im", Decoder.SOURCE_LITERAL,
        8192, 0, 0, false, false, true, Decoder.Role.ENTERED_LITERAL);
    Decoder.Candidate contraction = candidate("i'm", "I'm",
        Decoder.SOURCE_HUNSPELL | Decoder.SOURCE_CONTRACTION,
        0, 1, Decoder.EDIT_OMISSION, true, false, true, Decoder.Role.WORD);

    Decoder.Candidate chosen = choose(request,
        Arrays.asList(contraction, literal), literal, true,
        Decoder.Failure.NONE);
    assertNotNull("Unlearned im must allow its validated contraction.", chosen);
    assertEquals("I'm", chosen.surface);

    Decoder.Candidate learnedLiteral = candidate("im", "im",
        Decoder.SOURCE_LITERAL | Decoder.SOURCE_PERSONAL,
        0, 0, 0, false, true, true, Decoder.Role.ENTERED_LITERAL);
    assertNull("Learned im must remain literal.",
        choose(request, Arrays.asList(contraction, learnedLiteral),
          learnedLiteral, true, Decoder.Failure.NONE));
  }


  @Test
  public void learned_literal_yields_only_to_repeated_exact_training()
      throws Exception
  {
    Decoder.Request request = request("thus");
    Decoder.Candidate learnedLiteral = candidate("thus", "thus",
        Decoder.SOURCE_LITERAL | Decoder.SOURCE_PERSONAL,
        0, 0, 0, false, true, true, Decoder.Role.ENTERED_LITERAL);
    Decoder.Candidate threeExact = candidate("this", "this",
        Decoder.SOURCE_CDICT_SPATIAL | Decoder.SOURCE_CORRECTION,
        -16 * 256, 1, Decoder.EDIT_SUBSTITUTION, 3, 0, 6,
        true, true, true, Decoder.Role.WORD);
    Decoder.Candidate fourExact = candidate("this", "this",
        Decoder.SOURCE_CDICT_SPATIAL | Decoder.SOURCE_CORRECTION,
        -16 * 256, 1, Decoder.EDIT_SUBSTITUTION, 4, 0, 8,
        true, true, true, Decoder.Role.WORD);

    assertNull("Passive learned-word history must remain protected before the explicit correction threshold.",
        choose(request, Arrays.asList(threeExact, learnedLiteral),
          learnedLiteral, true, Decoder.Failure.NONE));
    assertEquals("Four exact source-to-target corrections are explicit training and must override stale learned-literal history.",
        "this", choose(request, Arrays.asList(fourExact, learnedLiteral),
          learnedLiteral, true, Decoder.Failure.NONE).canonical);
  }

  @Test
  public void repeated_exact_choice_beats_nearer_guess_and_unlocks_two_edits()
      throws Exception
  {
    Decoder.Request thysRequest = request("thys");
    Decoder.Candidate thysLiteral = candidate("thys", "thys",
        Decoder.SOURCE_LITERAL, UNKNOWN_LITERAL_TOTAL_Q8, 0, 0, false, false, true,
        Decoder.Role.ENTERED_LITERAL);
    Decoder.Candidate nearerThus = candidate("thus", "thus",
        Decoder.SOURCE_CDICT_SPATIAL, -16 * 256, 1,
        Decoder.EDIT_SUBSTITUTION, true, false, true, Decoder.Role.WORD);
    Decoder.Candidate learnedThis = candidate("this", "this",
        Decoder.SOURCE_CORRECTION, 8 * 256, 1, Decoder.EDIT_SUBSTITUTION,
        4, 0, 8, false, true, true, Decoder.Role.WORD);

    assertEquals("Four exact thys-to-this choices must override the geometrically nearer thus guess.",
        "this", choose(thysRequest,
          Arrays.asList(nearerThus, learnedThis, thysLiteral), thysLiteral,
          true, Decoder.Failure.NONE).canonical);

    Decoder.Request twoEditRequest = request("thxz");
    Decoder.Candidate twoEditLiteral = candidate("thxz", "thxz",
        Decoder.SOURCE_LITERAL, UNKNOWN_LITERAL_TOTAL_Q8, 0, 0, false, false, true,
        Decoder.Role.ENTERED_LITERAL);
    Decoder.Candidate ordinaryOneEdit = candidate("thez", "thez",
        Decoder.SOURCE_CDICT_SPATIAL, -16 * 256, 1,
        Decoder.EDIT_SUBSTITUTION, true, false, true, Decoder.Role.WORD);
    Decoder.Candidate threeExactTwoEdit = candidate("this", "this",
        Decoder.SOURCE_CORRECTION, 8 * 256, 2, Decoder.EDIT_SUBSTITUTION,
        3, 0, 6, false, true, true, Decoder.Role.WORD);
    Decoder.Candidate fourExactTwoEdit = candidate("this", "this",
        Decoder.SOURCE_CORRECTION, 8 * 256, 2, Decoder.EDIT_SUBSTITUTION,
        4, 0, 8, false, true, true, Decoder.Role.WORD);
    Decoder.Candidate relatedOnlyTwoEdit = candidate("this", "this",
        Decoder.SOURCE_CORRECTION, -16 * 256, 2, Decoder.EDIT_SUBSTITUTION,
        0, 8, 8, false, true, true, Decoder.Role.WORD);

    assertEquals("Before four exact observations, ordinary autocorrect must remain limited to one edit.",
        "thez", choose(twoEditRequest,
          Arrays.asList(ordinaryOneEdit, threeExactTwoEdit, twoEditLiteral),
          twoEditLiteral, true, Decoder.Failure.NONE).canonical);
    assertEquals("The fourth exact two-edit choice must override a closer ordinary guess.",
        "this", choose(twoEditRequest,
          Arrays.asList(ordinaryOneEdit, fourExactTwoEdit, twoEditLiteral),
          twoEditLiteral, true, Decoder.Failure.NONE).canonical);
    assertNull("Related-only evidence must never unlock automatic two-edit replacement.",
        choose(twoEditRequest, Arrays.asList(relatedOnlyTwoEdit, twoEditLiteral),
          twoEditLiteral, true, Decoder.Failure.NONE));
  }

  @Test
  public void autocorrect_preserves_initial_case_and_rejects_case_unlike_targets()
      throws Exception
  {
    assertEquals("Capitalized input must keep its leading capital after correction.",
        "The", clearCorrection("Teh").surface);

    Decoder.Request mixed = request("tEh");
    Decoder.Candidate literal = candidate("teh", "tEh", Decoder.SOURCE_LITERAL,
        8192, 0, 0, false, false, true, Decoder.Role.ENTERED_LITERAL);
    Decoder.Candidate winner = candidate("the", "the",
        Decoder.SOURCE_CDICT_SPATIAL, 0, 1, Decoder.EDIT_TRANSPOSITION,
        true, false, true, Decoder.Role.WORD);
    assertNull("Mixed-case identifiers and names are not safe commit-boundary autocorrect targets.",
        choose(mixed, Arrays.asList(winner, literal), literal, true,
          Decoder.Failure.NONE));

    Decoder.Request lowercase = request("ths");
    Decoder.Candidate lowerLiteral = candidate("ths", "ths",
        Decoder.SOURCE_LITERAL, 8192, 0, 0, false, false, true,
        Decoder.Role.ENTERED_LITERAL);
    Decoder.Candidate acronym = candidate("hts", "HTS",
        Decoder.SOURCE_CDICT_EXACT | Decoder.SOURCE_HUNSPELL,
        -4096, 1, Decoder.EDIT_TRANSPOSITION,
        true, false, true, Decoder.Role.WORD);
    assertNull("Lowercase prose must never autocorrect to an unrelated all-caps acronym.",
        choose(lowercase, Arrays.asList(acronym, lowerLiteral), lowerLiteral,
          true, Decoder.Failure.NONE));

    Decoder.Request initial = request("Ths");
    Decoder.Candidate initialLiteral = candidate("ths", "Ths",
        Decoder.SOURCE_LITERAL, 8192, 0, 0, false, false, true,
        Decoder.Role.ENTERED_LITERAL);
    assertNull("Sentence-initial prose must not autocorrect to an all-caps acronym.",
        choose(initial, Arrays.asList(acronym, initialLiteral), initialLiteral,
          true, Decoder.Failure.NONE));
  }

  @Test
  public void cold_short_all_caps_preserves_two_and_three_letter_tokens()
      throws Exception
  {
    Decoder.Request acronymRequest = request("STM");
    Decoder.Candidate acronymLiteral = candidate("stm", "STM",
        Decoder.SOURCE_LITERAL, 8192, 0, 0, false, false, true,
        Decoder.Role.ENTERED_LITERAL);
    Decoder.Candidate substitution = candidate("atm", "atm",
        Decoder.SOURCE_CDICT_SPATIAL, 0, 1, Decoder.EDIT_SUBSTITUTION,
        true, false, true, Decoder.Role.WORD);
    assertNull("A cold short all-caps token may be an acronym, so a one-letter substitution must preserve STM rather than force ATM.",
        choose(acronymRequest, Arrays.asList(substitution, acronymLiteral),
          acronymLiteral, true, Decoder.Failure.NONE));

    Decoder.Candidate transposition = candidate("the", "the",
        Decoder.SOURCE_CDICT_SPATIAL, 0, 1, Decoder.EDIT_TRANSPOSITION,
        true, false, true, Decoder.Role.WORD);
    assertNull("Any cold two- or three-letter all-caps token must remain typeable even when it resembles a transposition typo.",
        choose(request("TEH"), Arrays.asList(transposition,
          candidate("teh", "TEH", Decoder.SOURCE_LITERAL, 8192, 0, 0,
            false, false, true, Decoder.Role.ENTERED_LITERAL)),
          candidate("teh", "TEH", Decoder.SOURCE_LITERAL, 8192, 0, 0,
            false, false, true, Decoder.Role.ENTERED_LITERAL),
          true, Decoder.Failure.NONE));
  }

  @Test
  public void missing_apostrophe_contraction_can_autocorrect()
      throws Exception
  {
    Decoder.Request request = request("theyll");
    Decoder.Candidate literal = candidate("theyll", "theyll",
        Decoder.SOURCE_LITERAL, 8192, 0, 0, false, false, true,
        Decoder.Role.ENTERED_LITERAL);
    Decoder.Candidate contraction = candidate("they'll", "they'll",
        Decoder.SOURCE_HUNSPELL, 0, 1, Decoder.EDIT_OMISSION,
        true, false, true, Decoder.Role.WORD);

    Decoder.Candidate chosen = choose(request,
        Arrays.asList(contraction, literal), literal, true,
        Decoder.Failure.NONE);

    assertNotNull("A recognized contraction must not be discarded merely because its correction inserts an apostrophe.",
        chosen);
    assertEquals("they'll", chosen.surface);
  }


  @Test
  public void lowercase_neighbor_can_fix_standalone_first_person_i_only()
      throws Exception
  {
    Decoder.Request typo = request("j");
    Decoder.Candidate unknownLiteral = candidate("j", "j",
        Decoder.SOURCE_LITERAL, 8192, 0, 0, false, false, true,
        Decoder.Role.ENTERED_LITERAL);
    Decoder.Candidate firstPerson = spatialCandidate("i", "i",
        Decoder.SOURCE_CDICT_SPATIAL, 0, 2 * 256, 1,
        Decoder.EDIT_SUBSTITUTION, true, false, true, Decoder.Role.WORD);
    assertEquals("A lowercase key adjacent to I may repair the standalone first-person pronoun.",
        "I", choose(typo, Arrays.asList(firstPerson, unknownLiteral),
          unknownLiteral, true, Decoder.Failure.NONE).surface);

    Decoder.Request technical = request("J");
    Decoder.Candidate upperLiteral = candidate("j", "J",
        Decoder.SOURCE_LITERAL, 8192, 0, 0, false, false, true,
        Decoder.Role.ENTERED_LITERAL);
    assertNull("An uppercase one-letter token may be technical text and must remain unchanged.",
        choose(technical, Arrays.asList(firstPerson, upperLiteral),
          upperLiteral, true, Decoder.Failure.NONE));
  }
  @Test
  public void primary_lexical_two_letter_repair_may_override_fallback_literal()
      throws Exception
  {
    Decoder.Request typo = request("br");
    Decoder.Candidate unknownLiteral = candidate("br", "br",
        Decoder.SOURCE_LITERAL, 8192, 0, 0, false, false, true,
        Decoder.Role.ENTERED_LITERAL);
    Decoder.Candidate be = spatialCandidate("be", "be",
        Decoder.SOURCE_CDICT_SPATIAL, 0, 2 * 256, 1,
        Decoder.EDIT_SUBSTITUTION, true, false, true, Decoder.Role.WORD);
    assertEquals("An unrecognized two-letter token with one clear neighboring-key replacement must autocorrect.",
        "be", choose(typo, Arrays.asList(be, unknownLiteral), unknownLiteral,
          true, Decoder.Failure.NONE).canonical);

    Decoder.Request recognizedTypo = request("ad");
    Decoder.Candidate fallbackLiteral = candidate("ad", "ad",
        Decoder.SOURCE_LITERAL | Decoder.SOURCE_HUNSPELL,
        0, 0, 0, true, false, true, Decoder.Role.WORD);
    Decoder.Candidate closerTransposition = spatialCandidate("da", "da",
        Decoder.SOURCE_CDICT_SPATIAL, -256, 2 * 256, 1,
        Decoder.EDIT_TRANSPOSITION, true, false, false, Decoder.Role.WORD);
    Decoder.Candidate primaryExact = spatialCandidate("as", "as",
        Decoder.SOURCE_CDICT_EXACT | Decoder.SOURCE_CDICT_SPATIAL
          | Decoder.SOURCE_HUNSPELL | Decoder.SOURCE_HUNSPELL_PRIMARY,
        -227, 1693, 1, Decoder.EDIT_SUBSTITUTION,
        true, false, true, Decoder.Role.WORD);
    assertEquals("A lowercase two-letter token recognized only by the fallback lexicon may use its primary nearby repair when that target is exact in the main dictionary.",
        "as", choose(recognizedTypo,
          Arrays.asList(closerTransposition, primaryExact, fallbackLiteral),
          fallbackLiteral, true, Decoder.Failure.NONE).canonical);

    Decoder.Candidate exactLiteral = candidate("to", "to",
        Decoder.SOURCE_LITERAL | Decoder.SOURCE_CDICT_EXACT,
        0, 0, 0, true, false, true, Decoder.Role.WORD);
    assertNull("A main-dictionary two-letter literal must remain protected from the fallback lexical override.",
        choose(request("to"), Arrays.asList(primaryExact, exactLiteral),
          exactLiteral, true, Decoder.Failure.NONE));

    Decoder.Request upper = request("AD");
    Decoder.Candidate upperLiteral = candidate("ad", "AD",
        Decoder.SOURCE_LITERAL | Decoder.SOURCE_HUNSPELL,
        0, 0, 0, true, false, true, Decoder.Role.WORD);
    assertNull("Uppercase two-letter tokens remain protected from the same lexical repair.",
        choose(upper, Arrays.asList(primaryExact, upperLiteral), upperLiteral,
          true, Decoder.Failure.NONE));
    for (String typed : new String[] { "te!", "t'E" })
    {
      Decoder.Request request = request(typed);
      Decoder.Candidate literal = candidate(Decoder.normalize(typed), typed,
          Decoder.SOURCE_LITERAL, 8192, 0, 0, false, false, true,
          Decoder.Role.ENTERED_LITERAL);
      assertNull("Punctuation and technical tokens must remain literal: " + typed,
          choose(request, Arrays.asList(be, literal), literal, true,
            Decoder.Failure.NONE));
    }
  }

  @Test
  public void missing_negative_n_and_apostrophe_can_fix_archaic_literal()
      throws Exception
  {
    Method generator = Decoder.class.getDeclaredMethod(
        "missing_negative_contraction", String.class);
    generator.setAccessible(true);
    assertEquals("The real contraction collector must synthesize the missing n and apostrophe before Hunspell validation.",
        "doesn't", generator.invoke(null, "doest"));
    assertNull("Words that already contain n use the ordinary missing-apostrophe path.",
        generator.invoke(null, "doesnt"));
    assertNull("Ordinary words without a negative-contraction shape must not gain punctuation.",
        generator.invoke(null, "well"));
    assertNull("A common word ending in t must never be expanded into an unrelated negative contraction.",
        generator.invoke(null, "cat"));
    assertNull("The common word dot must never synthesize don't.",
        generator.invoke(null, "dot"));
    Decoder.Request request = request("doest");
    Decoder.Candidate literal = candidate("doest", "doest",
        Decoder.SOURCE_LITERAL | Decoder.SOURCE_HUNSPELL,
        8192, 0, 0, true, false, true, Decoder.Role.WORD);
    Decoder.Candidate negative = candidate("doesn't", "doesn't",
        Decoder.SOURCE_HUNSPELL | Decoder.SOURCE_CONTRACTION,
        0, 2, Decoder.EDIT_OMISSION, true, false, true, Decoder.Role.WORD);
    assertEquals("A validated negative contraction may recover both the missing n and apostrophe from an archaic literal.",
        "doesn't", choose(request, Arrays.asList(negative, literal), literal,
          true, Decoder.Failure.NONE).surface);

    Decoder.Request ambiguousRequest = request("well");
    Decoder.Candidate ambiguousLiteral = candidate("well", "well",
        Decoder.SOURCE_LITERAL | Decoder.SOURCE_HUNSPELL,
        8192, 0, 0, true, false, true, Decoder.Role.WORD);
    Decoder.Candidate apostropheOnly = candidate("we'll", "we'll",
        Decoder.SOURCE_HUNSPELL | Decoder.SOURCE_CONTRACTION,
        0, 1, Decoder.EDIT_OMISSION, true, false, true, Decoder.Role.WORD);
    assertNull("A cold apostrophe-only contraction must not replace an ordinary valid word without explicit training.",
        choose(ambiguousRequest, Arrays.asList(apostropheOnly,
              ambiguousLiteral), ambiguousLiteral, true,
            Decoder.Failure.NONE));
  }

  private static Decoder.Candidate clearCorrection(String typed)
      throws Exception
  {
    Decoder.Request request = request(typed);
    Decoder.Candidate literal = candidate("teh", typed, Decoder.SOURCE_LITERAL,
        8192, 0, 0, false, false, true, Decoder.Role.ENTERED_LITERAL);
    Decoder.Candidate winner = candidate("the", "the",
        Decoder.SOURCE_CDICT_SPATIAL, 0, 1, Decoder.EDIT_TRANSPOSITION,
        true, false, true, Decoder.Role.WORD);
    return choose(request, Arrays.asList(winner, literal), literal, true,
        Decoder.Failure.NONE);
  }

  private static void assertEdit(String typed, String candidate,
      int expectedMask, String message)
      throws Exception
  {
    Score score = score(typed, candidate, null);
    assertEquals(message, 1, score.editCount);
    assertEquals(message, expectedMask, score.editMask);
  }

  private static void assertClearHunspellCorrection(String typed,
      String target, int expectedMask, String message)
      throws Exception
  {
    Score score = score(typed, target, centeredTouches(typed));
    assertEquals(message, 1, score.editCount);
    assertEquals(message, expectedMask, score.editMask);

    Decoder.Request request = request(typed);
    Decoder.Candidate literal = candidate(typed, typed, Decoder.SOURCE_LITERAL,
        UNKNOWN_LITERAL_TOTAL_Q8, 0, 0, false, false, true,
        Decoder.Role.ENTERED_LITERAL);
    Decoder.Candidate repair = candidate(target, target,
        Decoder.SOURCE_HUNSPELL, score.spatialQ8 + 256, score.editCount,
        score.editMask, true, false, true, Decoder.Role.WORD);
    Decoder.Candidate chosen = choose(request,
        Arrays.asList(repair, literal), literal, true, Decoder.Failure.NONE);
    assertNotNull(message, chosen);
    assertEquals(message, target, chosen.canonical);
  }

  private static TouchTrace.Snapshot centeredTouches(String typed)
  {
    TouchTrace touches = new TouchTrace();
    int count = typed.codePointCount(0, typed.length());
    for (int i = 0; i < count; i++)
      touches.add(TouchTrace.entry(100f, 100f, 100f, 100f, 20f, 20f));
    return touches.snapshot();
  }

  private static Score score(String typed, String candidate,
      TouchTrace.Snapshot touches)
      throws Exception
  {
    Decoder.Geometry geometry = Decoder.Geometry.from(null);
    int[] typedCodePoints = Decoder.normalize(typed).codePoints().toArray();
    int[] touchIndexes = new int[typedCodePoints.length];
    for (int i = 0; i < touchIndexes.length; i++)
      touchIndexes[i] = i;

    Method costTableMethod = Decoder.Geometry.class.getDeclaredMethod(
        "cost_table", int[].class, int[].class, TouchTrace.Snapshot.class);
    costTableMethod.setAccessible(true);
    Object costTable = costTableMethod.invoke(geometry, typedCodePoints,
        touchIndexes, touches);

    Class<?> scorerClass = Class.forName(
        "juloo.keyboard2.suggestions.Decoder$Scorer");
    Constructor<?> scorerConstructor = scorerClass.getDeclaredConstructor(
        costTable.getClass());
    scorerConstructor.setAccessible(true);
    Object scorer = scorerConstructor.newInstance(costTable);
    Method scoreMethod = scorerClass.getDeclaredMethod("score", int[].class);
    scoreMethod.setAccessible(true);
    Object raw = scoreMethod.invoke(scorer,
        new Object[] { Decoder.normalize(candidate).codePoints().toArray() });
    return new Score(intField(raw, "spatialQ8"), intField(raw, "editCount"),
        intField(raw, "editMask"));
  }

  private static void sortForRequest(Decoder.Request request,
      List<Decoder.Candidate> ranked)
      throws Exception
  {
    Method method = Decoder.class.getDeclaredMethod(
        "sort_candidates_for_request", Decoder.Request.class, List.class);
    method.setAccessible(true);
    method.invoke(null, request, ranked);
  }

  private static Decoder.Candidate choose(Decoder.Request request,
      List<Decoder.Candidate> ranked, Decoder.Candidate literal,
      boolean enabled, Decoder.Failure failure)
      throws Exception
  {
    Method method = Decoder.class.getDeclaredMethod("choose_autocorrection",
        Decoder.Request.class, List.class, Decoder.Candidate.class,
        boolean.class, Decoder.Failure.class);
    method.setAccessible(true);
    return (Decoder.Candidate)method.invoke(null, request, ranked, literal,
        enabled, failure);
  }

  private static Decoder.Candidate candidate(String canonical, String surface,
      int sourceMask, int totalQ8, int editCount, int editMask,
      boolean recognized, boolean learned, boolean completeEvidence,
      Decoder.Role role)
      throws Exception
  {
    return candidate(canonical, surface, sourceMask, totalQ8, editCount,
        editMask, 0, 0, 0, recognized, learned, completeEvidence, role);
  }

  private static Decoder.Candidate candidate(String canonical, String surface,
      int sourceMask, int totalQ8, int editCount, int editMask,
      int exactCorrectionCount, int relatedCorrectionCount,
      int correctionWeight, boolean recognized, boolean learned,
      boolean completeEvidence, Decoder.Role role)
      throws Exception
  {
    Constructor<Decoder.Candidate> constructor =
      Decoder.Candidate.class.getDeclaredConstructor(String.class,
          String.class, int.class, int.class, int.class, int.class,
          int.class, int.class, int.class, int.class, int.class, int.class,
          int.class, int.class, int.class, Decoder.Role.class, boolean.class,
          boolean.class, boolean.class);
    constructor.setAccessible(true);
    return constructor.newInstance(canonical, surface, sourceMask, -1, 0, 0,
        learned ? 1 : 0, 0, exactCorrectionCount, relatedCorrectionCount,
        correctionWeight, 0, editCount, editMask, totalQ8, role, recognized,
        learned, completeEvidence);
  }

  private static Decoder.Candidate contextualCandidate(String canonical,
      String surface, int exactCorrectionCount, int bigramCount,
      boolean contextual, int editCount, int editMask)
      throws Exception
  {
    Constructor<Decoder.Candidate> constructor =
      Decoder.Candidate.class.getDeclaredConstructor(String.class,
          String.class, int.class, int.class, int.class, int.class,
          int.class, int.class, int.class, int.class, int.class, int.class,
          int.class, int.class, int.class, Decoder.Role.class, boolean.class,
          boolean.class, boolean.class);
    constructor.setAccessible(true);
    int sourceMask = Decoder.SOURCE_CORRECTION | Decoder.SOURCE_CONTEXT;
    if (contextual)
      sourceMask |= Decoder.SOURCE_CONTEXTUAL_CORRECTION;
    return constructor.newInstance(canonical, surface, sourceMask,
        -1, 0, 0, 1, bigramCount, exactCorrectionCount, 0,
        exactCorrectionCount * 2, 0, editCount, editMask, 0,
        Decoder.Role.WORD, false, true, true);
  }

  private static Decoder.Candidate spatialCandidate(String canonical,
      String surface, int sourceMask, int totalQ8, int spatialQ8,
      int editCount, int editMask, boolean recognized, boolean learned,
      boolean completeEvidence, Decoder.Role role)
      throws Exception
  {
    Constructor<Decoder.Candidate> constructor =
      Decoder.Candidate.class.getDeclaredConstructor(String.class,
          String.class, int.class, int.class, int.class, int.class,
          int.class, int.class, int.class, int.class, int.class, int.class,
          int.class, int.class, int.class, Decoder.Role.class, boolean.class,
          boolean.class, boolean.class);
    constructor.setAccessible(true);
    return constructor.newInstance(canonical, surface, sourceMask, -1, 0, 0,
        learned ? 1 : 0, 0, 0, 0, 0, spatialQ8, editCount, editMask, totalQ8,
        role, recognized, learned, completeEvidence);
  }

  private static Decoder.Candidate spatialCandidateWithProviderRank(
      String canonical, String surface, int sourceMask, int totalQ8,
      int spatialQ8, int providerRank, int editCount, int editMask)
      throws Exception
  {
    Constructor<Decoder.Candidate> constructor =
      Decoder.Candidate.class.getDeclaredConstructor(String.class,
          String.class, int.class, int.class, int.class, int.class,
          int.class, int.class, int.class, int.class, int.class, int.class,
          int.class, int.class, int.class, Decoder.Role.class, boolean.class,
          boolean.class, boolean.class);
    constructor.setAccessible(true);
    return constructor.newInstance(canonical, surface, sourceMask, -1, 15,
        providerRank, 0, 0, 0, 0, 0, spatialQ8, editCount, editMask, totalQ8,
        Decoder.Role.WORD, true, false, true);
  }

  private static Decoder.Request request(String typed)
  {
    Decoder.RequestKey key = new Decoder.RequestKey(1, 1, 1, 1, 1, 1, 1);
    return new Decoder.Request(key, typed, (TouchTrace.Snapshot)null,
        Decoder.Geometry.from(null),
        new Decoder.DecoderConfig(true, true, true, true));
  }

  private static int intField(Object value, String name)
      throws Exception
  {
    Field field = value.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.getInt(value);
  }

  private static final class Score
  {
    final int spatialQ8;
    final int editCount;
    final int editMask;

    Score(int spatialQ8_, int editCount_, int editMask_)
    {
      spatialQ8 = spatialQ8_;
      editCount = editCount_;
      editMask = editMask_;
    }
  }
}
