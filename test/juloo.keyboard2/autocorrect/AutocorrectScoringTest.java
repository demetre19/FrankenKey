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
        Decoder.SOURCE_CDICT_SPATIAL, 7400, 1, Decoder.EDIT_TRANSPOSITION,
        true, false, true, Decoder.Role.WORD);
    assertNull("A candidate without a four-point literal margin is too risky to commit automatically.",
        choose(request, Arrays.asList(weakWinner, literal), literal, true,
          Decoder.Failure.NONE));

    Decoder.Candidate closeRunner = candidate("ten", "ten",
        Decoder.SOURCE_CDICT_SPATIAL, 256, 1, Decoder.EDIT_SUBSTITUTION,
        true, false, true, Decoder.Role.WORD);
    assertNull("Two nearly tied corrections are ambiguous and must leave the user's text unchanged.",
        choose(request, Arrays.asList(winner, closeRunner, literal), literal,
          true, Decoder.Failure.NONE));
    assertNull("A resource failure must disable automatic replacement rather than guessing from incomplete evidence.",
        choose(request, Arrays.asList(winner, literal), literal, true,
          Decoder.Failure.RESOURCE));
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
  public void repeated_exact_choice_beats_nearer_guess_and_unlocks_two_edits()
      throws Exception
  {
    Decoder.Request thysRequest = request("thys");
    Decoder.Candidate thysLiteral = candidate("thys", "thys",
        Decoder.SOURCE_LITERAL, 12 * 256, 0, 0, false, false, true,
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
        Decoder.SOURCE_LITERAL, 12 * 256, 0, 0, false, false, true,
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
  public void autocorrect_preserves_initial_and_all_caps_but_rejects_mixed_case()
      throws Exception
  {
    assertEquals("Capitalized input must keep its leading capital after correction.",
        "The", clearCorrection("Teh").surface);
    assertEquals("All-caps input must remain all caps after correction.",
        "THE", clearCorrection("TEH").surface);

    Decoder.Request mixed = request("tEh");
    Decoder.Candidate literal = candidate("teh", "tEh", Decoder.SOURCE_LITERAL,
        8192, 0, 0, false, false, true, Decoder.Role.ENTERED_LITERAL);
    Decoder.Candidate winner = candidate("the", "the",
        Decoder.SOURCE_CDICT_SPATIAL, 0, 1, Decoder.EDIT_TRANSPOSITION,
        true, false, true, Decoder.Role.WORD);
    assertNull("Mixed-case identifiers and names are not safe commit-boundary autocorrect targets.",
        choose(mixed, Arrays.asList(winner, literal), literal, true,
          Decoder.Failure.NONE));
  }

  @Test
  public void short_words_and_non_words_are_never_autocorrected()
      throws Exception
  {
    for (String typed : new String[] { "to", "te!", "t'E" })
    {
      Decoder.Request request = request(typed);
      Decoder.Candidate literal = candidate(Decoder.normalize(typed), typed,
          Decoder.SOURCE_LITERAL, 8192, 0, 0, false, false, true,
          Decoder.Role.ENTERED_LITERAL);
      Decoder.Candidate winner = candidate("the", "the",
          Decoder.SOURCE_CDICT_SPATIAL, 0, 1, Decoder.EDIT_SUBSTITUTION,
          true, false, true, Decoder.Role.WORD);
      assertNull("Short words, punctuation, and technical tokens must remain literal: " + typed,
          choose(request, Arrays.asList(winner, literal), literal, true,
            Decoder.Failure.NONE));
    }
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
