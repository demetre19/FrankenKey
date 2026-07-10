package juloo.cdict.tests;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import juloo.cdict.Cdict;

public class CdictJavaTests
{
  private static final int[] SYMBOLS = "abcdefghijklmnopqrstuvwxyz"
    .codePoints().toArray();
  private static final int SUBSTITUTION_COST_Q8 = 16 * 256;
  private static final int OMISSION_COST_Q8 = 8 * 256;
  private static final int EXTRA_TAP_COST_Q8 = 8 * 256;
  private static final int TRANSPOSITION_COST_Q8 = 1 * 256;

  static Cdict open_dict(String fname) throws Exception
  {
    byte[] data = Files.readAllBytes(FileSystems.getDefault().getPath(fname));
    Cdict[] dicts = Cdict.of_bytes(data);
    for (Cdict d : dicts)
      if (d.name.equals("main"))
        return d;
    throw new Exception("No dictionary named 'main'.");
  }

  static Cdict.SpatialQuery spatial_query(long sequence, String literal,
      int maxResults)
  {
    int[] codePoints = literal.codePoints().toArray();
    int[] costs = new int[codePoints.length * SYMBOLS.length];
    for (int i = 0; i < codePoints.length; i++)
      for (int j = 0; j < SYMBOLS.length; j++)
        costs[i * SYMBOLS.length + j] = codePoints[i] == SYMBOLS[j]
          ? 0 : SUBSTITUTION_COST_Q8;
    return new Cdict.SpatialQuery(sequence, codePoints, SYMBOLS, costs,
        Cdict.CDICT_SPATIAL_MAX_EDITS, maxResults, OMISSION_COST_Q8,
        EXTRA_TAP_COST_Q8, TRANSPOSITION_COST_Q8,
        SUBSTITUTION_COST_Q8, 64 * 256,
        Cdict.CDICT_SPATIAL_MAX_EXPANSIONS);
  }

  static Cdict.SpatialCandidate require_candidate(Cdict dict,
      Cdict.SpatialResult result, String expected)
  {
    require(result.status == Cdict.CDICT_SPATIAL_OK
        || result.status == Cdict.CDICT_SPATIAL_TRUNCATED,
        "spatial search returned status " + result.status);
    for (Cdict.SpatialCandidate candidate : result.candidates)
      if (dict.word(candidate.index).equals(expected))
        return candidate;
    throw new AssertionError("Missing spatial candidate: " + expected);
  }

  static void assert_edit_case(Cdict dict, long sequence, String literal,
      String expected, int expectedCost, int expectedMask, String label)
  {
    Cdict.SpatialResult result = dict.spatial(
        spatial_query(sequence, literal, Cdict.CDICT_SPATIAL_MAX_RESULTS));
    require(result.sequence == sequence,
        "native spatial result must echo request sequence");
    require(result.candidates.length <= Cdict.CDICT_SPATIAL_MAX_RESULTS,
        "native spatial result exceeded bounded capacity");
    Cdict.SpatialCandidate candidate = require_candidate(dict, result,
        expected);
    int expectedEdits = expectedMask == 0 ? 0 : 1;
    require(candidate.spatialCostQ8 == expectedCost,
        label + " cost mismatch: " + candidate.spatialCostQ8);
    require(candidate.editCount == expectedEdits,
        label + " edit count mismatch: " + candidate.editCount);
    require(candidate.editMask == expectedMask,
        label + " edit mask mismatch: " + candidate.editMask);
    require(candidate.frequency == dict.freq(candidate.index),
        label + " frequency metadata mismatch");
    System.out.printf("spatial %s: %s cost=%d edits=%d mask=%d%n",
        label, expected, candidate.spatialCostQ8, candidate.editCount,
        candidate.editMask);
  }

  static void assert_deterministic_bounded_ordering(Cdict dict)
  {
    Cdict.SpatialResult first = dict.spatial(spatial_query(100, "type", 7));
    Cdict.SpatialResult second = dict.spatial(spatial_query(101, "type", 7));
    require(first.sequence == 100 && second.sequence == 101,
        "independent native queries must preserve their own sequence");
    require(first.status == Cdict.CDICT_SPATIAL_OK
        || first.status == Cdict.CDICT_SPATIAL_TRUNCATED,
        "first bounded query returned invalid status");
    require(second.status == Cdict.CDICT_SPATIAL_OK
        || second.status == Cdict.CDICT_SPATIAL_TRUNCATED,
        "second bounded query returned invalid status");
    require(first.candidates.length > 0 && first.candidates.length <= 7,
        "bounded query must return between one and seven candidates");
    require(first.candidates.length == second.candidates.length,
        "repeated native query changed result count");

    long previousRank = Long.MIN_VALUE;
    for (int i = 0; i < first.candidates.length; i++)
    {
      Cdict.SpatialCandidate a = first.candidates[i];
      Cdict.SpatialCandidate b = second.candidates[i];
      require(a.index == b.index
          && a.spatialCostQ8 == b.spatialCostQ8
          && a.editCount == b.editCount
          && a.editMask == b.editMask
          && a.frequency == b.frequency,
          "repeated native query changed deterministic ordering at " + i);
      long rank = (long)a.spatialCostQ8 - 128L * a.frequency;
      require(rank >= previousRank,
          "native candidate rank decreased at " + i);
      previousRank = rank;
    }
    System.out.println("spatial bounded deterministic ordering: passed");
  }

  static void assert_query_defensive_copy_and_validation()
  {
    int[] literal = { 't', 'e', 'h' };
    int[] symbols = { 'e', 'h', 't' };
    int[] costs = new int[literal.length * symbols.length];
    Cdict.SpatialQuery query = new Cdict.SpatialQuery(200, literal, symbols,
        costs, 2, 3, 1, 2, 3, 4, 5, 6);
    literal[0] = 'x';
    symbols[0] = 'x';
    costs[0] = 99;
    require(query.literalCodePoints[0] == 't'
        && query.symbolCodePoints[0] == 'e'
        && query.substitutionCostsQ8[0] == 0,
        "SpatialQuery must own defensive copies of JNI arrays");

    boolean rejected = false;
    try
    {
      new Cdict.SpatialQuery(201, new int[] { 'a' },
          new int[] { 'a', 'b' }, new int[] { 0 }, 1, 1,
          1, 1, 1, 1, 1, 1);
    }
    catch (IllegalArgumentException expected)
    {
      rejected = true;
    }
    require(rejected,
        "SpatialQuery must reject a malformed substitution matrix before JNI");
    System.out.println("spatial immutable bounds and validation: passed");
  }

  static void require(boolean condition, String message)
  {
    if (!condition)
      throw new AssertionError(message);
  }

  public static void main(String[] args) throws Exception
  {
    Cdict dict = open_dict(args[1]);
    assert_edit_case(dict, 1, "types", "types", 0, 0, "exact");
    assert_edit_case(dict, 2, "tyqes", "types", SUBSTITUTION_COST_Q8,
        Cdict.CDICT_EDIT_SUBSTITUTION, "substitution");
    assert_edit_case(dict, 3, "tyes", "types", OMISSION_COST_Q8,
        Cdict.CDICT_EDIT_OMISSION, "omission");
    assert_edit_case(dict, 4, "typpes", "types", EXTRA_TAP_COST_Q8,
        Cdict.CDICT_EDIT_EXTRA_TAP, "extra-tap");
    assert_edit_case(dict, 5, "tyeps", "types", TRANSPOSITION_COST_Q8,
        Cdict.CDICT_EDIT_TRANSPOSITION, "transposition");
    assert_deterministic_bounded_ordering(dict);
    assert_query_defensive_copy_and_validation();
  }
}
