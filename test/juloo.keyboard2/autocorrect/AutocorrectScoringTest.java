package juloo.keyboard2.autocorrect;

import org.junit.Test;
import static org.junit.Assert.*;

public class AutocorrectScoringTest
{
  public AutocorrectScoringTest() {}

  @Test
  public void adjacent_transposition_is_high_confidence_and_preserves_typed_case()
  {
    assertTrue("A single adjacent transposition such as teh -> the is safe enough to autocorrect at commit time.",
        Autocorrect.is_high_confidence("teh", "the"));
    assertEquals("Capitalized typed words must keep their leading capital after correction.",
        "The", Autocorrect.match_case("Teh", "the"));
    assertEquals("All-caps typed words must remain all-caps after correction.",
        "THE", Autocorrect.match_case("TEH", "the"));
  }

  @Test
  public void low_confidence_or_unsafe_suggestions_are_rejected()
  {
    assertFalse("An already-correct word must not be replaced with itself.",
        Autocorrect.is_high_confidence("the", "the"));
    assertFalse("Suggestions containing punctuation are not plain word corrections.",
        Autocorrect.is_high_confidence("teh", "the!"));
    assertFalse("Larger edit-distance rewrites are too risky for automatic spacebar correction.",
        Autocorrect.is_high_confidence("colour", "calendar"));
  }

  @Test
  public void two_letter_whole_word_rewrites_are_not_commit_boundary_autocorrect()
  {
    String[][] risky_rewrites = {
      { "do", "so" },
      { "do", "fo" },
      { "to", "do" },
      { "in", "on" }
    };
    for (String[] rewrite : risky_rewrites)
      assertFalse("Two-letter words carry too little context for safe spacebar autocorrect; common phrases such as 'to do' must remain typable without rewriting "
          + rewrite[0] + " -> " + rewrite[1] + ".",
          Autocorrect.is_high_confidence(rewrite[0], rewrite[1]));
  }
}
