package juloo.keyboard2.suggestions;

import java.util.Arrays;
import juloo.cdict.Cdict;
import juloo.keyboard2.TouchTrace;
import org.junit.Test;
import static org.junit.Assert.*;

public class CdictSpatialQueryTest
{
  @Test
  public void decoder_builds_bounded_coordinate_first_native_query()
  {
    Decoder.Geometry geometry = Decoder.Geometry.from(null);
    TouchTrace touches = new TouchTrace();
    touches.add(TouchTrace.entry(140f, 100f, 100f, 100f, 20f, 20f));
    Cdict.SpatialQuery query = geometry.spatial_query(73,
        "gello".codePoints().toArray(), touches.snapshot());

    assertEquals("The native result sequence must echo the immutable request generation so stale results can be rejected.",
        73, query.sequence);
    assertEquals("Spatial search must stay within the native two-edit contract.",
        Cdict.CDICT_SPATIAL_MAX_EDITS, query.maxEdits);
    assertEquals("Spatial recall must stay allocation-bounded.",
        Decoder.MAX_NATIVE_RESULTS, query.maxResults);
    assertTrue("The coordinate-first query must have a finite beam smaller than the native infinity sentinel.",
        query.beamCostQ8 > 0
        && query.beamCostQ8 < Cdict.CDICT_SPATIAL_COST_INF);
    assertTrue("The native expansion budget must be positive and bounded by the ABI maximum.",
        query.expansionBudget > 0
        && query.expansionBudget <= Cdict.CDICT_SPATIAL_MAX_EXPANSIONS);
  }

  @Test
  public void actual_touch_near_j_changes_native_substitution_cost_before_search()
  {
    Decoder.Geometry geometry = Decoder.Geometry.from(null);
    int[] typed = "gello".codePoints().toArray();
    Cdict.SpatialQuery fixed = geometry.spatial_query(1, typed, null);
    TouchTrace touches = new TouchTrace();
    touches.add(TouchTrace.entry(140f, 100f, 100f, 100f, 20f, 20f));
    Cdict.SpatialQuery touched = geometry.spatial_query(2, typed,
        touches.snapshot());

    int h = indexOf(touched.symbolCodePoints, 'h');
    int j = indexOf(touched.symbolCodePoints, 'j');
    int width = touched.symbolCodePoints.length;
    assertTrue("The QWERTY fallback must prefer adjacent H to J when no coordinate was captured.",
        fixed.substitutionCostsQ8[h]
        < fixed.substitutionCostsQ8[j]);
    assertTrue("A first tap centered near J must reach native search with J cheaper than H before edit-distance/frequency tie-breaking.",
        touched.substitutionCostsQ8[j]
        < touched.substitutionCostsQ8[h]);
    assertEquals("Every input row must contain one substitution cost per native symbol.",
        typed.length * width, touched.substitutionCostsQ8.length);
  }

  @Test
  public void spatial_query_owns_immutable_array_copies()
  {
    int[] literal = { 't', 'e', 'h' };
    int[] symbols = { 'e', 'h', 't' };
    int[] costs = new int[literal.length * symbols.length];
    Cdict.SpatialQuery query = new Cdict.SpatialQuery(9, literal, symbols,
        costs, 2, 3, 100, 200, 300, 400, 500, 600);

    literal[0] = 'x';
    symbols[0] = 'x';
    costs[0] = 999;

    assertArrayEquals("Worker requests must retain their literal code points even if caller arrays are reused.",
        new int[] { 't', 'e', 'h' }, query.literalCodePoints);
    assertArrayEquals("The native symbol alphabet must be immutable for the lifetime of the query.",
        new int[] { 'e', 'h', 't' }, query.symbolCodePoints);
    assertEquals("The coordinate cost matrix must be copied rather than aliased.",
        0, query.substitutionCostsQ8[0]);
  }

  @Test
  public void Java_and_native_edit_masks_remain_exactly_aligned()
  {
    assertEquals("Substitution masks must cross JNI without translation.",
        Cdict.CDICT_EDIT_SUBSTITUTION, Decoder.EDIT_SUBSTITUTION);
    assertEquals("Omission masks must cross JNI without translation.",
        Cdict.CDICT_EDIT_OMISSION, Decoder.EDIT_OMISSION);
    assertEquals("Extra-tap masks must cross JNI without translation.",
        Cdict.CDICT_EDIT_EXTRA_TAP, Decoder.EDIT_EXTRA_TAP);
    assertEquals("Transposition masks must cross JNI without translation.",
        Cdict.CDICT_EDIT_TRANSPOSITION, Decoder.EDIT_TRANSPOSITION);
  }

  @Test
  public void malformed_or_unbounded_queries_are_rejected_before_JNI()
  {
    assertIllegal("A mismatched spatial matrix must never reach native memory.",
        new Runnable()
        {
          @Override
          public void run()
          {
            new Cdict.SpatialQuery(1, new int[] { 'a' },
                new int[] { 'a', 'b' }, new int[] { 0 }, 1, 1,
                1, 1, 1, 1, 1, 1);
          }
        });
    assertIllegal("Native search must reject more than two edits.",
        new Runnable()
        {
          @Override
          public void run()
          {
            new Cdict.SpatialQuery(1, new int[] { 'a' },
                new int[] { 'a' }, new int[] { 0 }, 3, 1,
                1, 1, 1, 1, 1, 1);
          }
        });
    assertIllegal("Duplicate native symbols would make substitution columns ambiguous.",
        new Runnable()
        {
          @Override
          public void run()
          {
            new Cdict.SpatialQuery(1, new int[] { 'a' },
                new int[] { 'a', 'a' }, new int[] { 0, 0 }, 1, 1,
                1, 1, 1, 1, 1, 1);
          }
        });
  }

  private static int indexOf(int[] values, int expected)
  {
    int index = Arrays.binarySearch(values, expected);
    assertTrue("Expected native spatial symbol " + (char)expected, index >= 0);
    return index;
  }

  private static void assertIllegal(String message, Runnable action)
  {
    try
    {
      action.run();
      fail(message);
    }
    catch (IllegalArgumentException expected) {}
  }
}
