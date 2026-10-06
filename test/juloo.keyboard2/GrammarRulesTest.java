package juloo.keyboard2;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import juloo.keyboard2.grammar.GrammarData;
import juloo.keyboard2.grammar.GrammarIssue;
import juloo.keyboard2.grammar.GrammarRules;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.*;

/** Fixture-driven precision gate: every rule must score 100% on its
    positive table and ≥98% on its negative table. */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
public class GrammarRulesTest
{
  private static GrammarData data;

  @BeforeClass
  public static void loadData() throws IOException
  {
    try (FileInputStream input = new FileInputStream("assets/grammar/en.json"))
    {
      data = GrammarData.load(input);
    }
  }

  @Test
  public void double_word_fixtures() throws IOException
  {
    assertPrecision(GrammarRules.RULE_DOUBLE_WORD);
  }

  @Test
  public void a_an_fixtures() throws IOException
  {
    assertPrecision(GrammarRules.RULE_A_AN);
  }

  @Test
  public void could_of_fixtures() throws IOException
  {
    assertPrecision(GrammarRules.RULE_COULD_OF);
  }

  @Test
  public void your_youre_fixtures() throws IOException
  {
    assertPrecision(GrammarRules.RULE_YOUR_YOURE);
  }

  @Test
  public void its_its_fixtures() throws IOException
  {
    assertPrecision(GrammarRules.RULE_ITS_ITS);
  }

  @Test
  public void their_there_fixtures() throws IOException
  {
    assertPrecision(GrammarRules.RULE_THEIR_THERE);
  }

  @Test
  public void then_than_fixtures() throws IOException
  {
    assertPrecision(GrammarRules.RULE_THEN_THAN);
  }

  @Test
  public void alot_fixtures() throws IOException
  {
    assertPrecision(GrammarRules.RULE_ALOT);
  }

  @Test
  public void casing_is_preserved()
  {
    List<GrammarIssue> issues = GrammarRules.check(
        "I could of gone.", data);
    GrammarIssue issue = only(issues);
    assertEquals("could of", issue.matched);
    assertEquals("could have", issue.replacement);

    issues = GrammarRules.check("Your welcome friend", data);
    assertEquals("You're", only(issues).replacement);

    issues = GrammarRules.check("I saw the the cat", data);
    assertEquals("the", only(issues).replacement);
    issues = GrammarRules.check("I saw The The cat", data);
    assertEquals("The", only(issues).replacement);
  }

  @Test
  public void offsets_are_utf16_exact_with_emoji()
  {
    String sentence = "\uD83D\uDE00 hi its a trap \uD83C\uDF89";
    /* Emoji before the flagged span takes two UTF-16 units. */
    int expected = 6; /* "😀" (2 UTF-16 units) + " hi " → "its" at 6 */
    List<GrammarIssue> issues = GrammarRules.check(sentence, data);
    GrammarIssue issue = only(issues);
    assertEquals(expected, issue.offset);
    assertEquals(3, issue.length);
    assertEquals("its", issue.matched);
    assertEquals("it's", issue.replacement);
  }

  @Test
  public void sentences_longer_than_max_are_skipped()
  {
    StringBuilder sentence = new StringBuilder();
    while (sentence.length() <= GrammarRules.MAX_LENGTH)
      sentence.append("word ");
    assertTrue(GrammarRules.check(sentence.toString(), data).isEmpty());
  }

  @Test
  public void issues_never_overlap_and_stay_ordered()
  {
    String sentence = "the the the your welcome alot could of went";
    List<GrammarIssue> issues = GrammarRules.check(sentence, data);
    int cursor = -1;
    for (GrammarIssue issue : issues)
    {
      assertTrue("Issues must be ordered and non-overlapping",
          issue.offset > cursor);
      cursor = issue.offset + issue.length;
    }
  }

  @Test
  public void check_is_fast_enough_for_every_word_boundary()
  {
    StringBuilder builder = new StringBuilder();
    while (builder.length() < GrammarRules.MAX_LENGTH)
      builder.append("the quick brown fox jumps over the lazy dog ");
    String sentence = builder.substring(0, GrammarRules.MAX_LENGTH);
    int iterations = 400;
    for (int i = 0; i < 50; i++)
      GrammarRules.check(sentence, data); /* warmup */
    long[] times = new long[iterations];
    for (int i = 0; i < iterations; i++)
    {
      long start = System.nanoTime();
      GrammarRules.check(sentence, data);
      times[i] = System.nanoTime() - start;
    }
    java.util.Arrays.sort(times);
    long p95 = times[(int)(iterations * 0.95)];
    assertTrue("GrammarRules.check p95 must stay under 2 ms, was "
        + (p95 / 1_000_000.0) + " ms", p95 < 2_000_000);
  }

  private void assertPrecision(String ruleId) throws IOException
  {
    List<String[]> positive = fixtures(ruleId + "_positive.tsv");
    assertTrue(ruleId + " needs >=40 positive fixtures, found "
        + positive.size(), positive.size() >= 40);
    List<String> positiveFailures = new ArrayList<>();
    for (String[] row : positive)
    {
      String corrected = apply(row[0]);
      if (!row[1].equals(corrected))
        positiveFailures.add(row[0] + " -> " + corrected);
    }
    assertTrue(ruleId + " positive failures: "
        + positiveFailures, positiveFailures.isEmpty());

    List<String> negative = lines(ruleId + "_negative.tsv");
    assertTrue(ruleId + " needs >=80 negative fixtures, found "
        + negative.size(), negative.size() >= 80);
    int falsePositives = 0;
    List<String> flagged = new ArrayList<>();
    for (String line : negative)
    {
      for (GrammarIssue issue : GrammarRules.check(line, data))
      {
        if (issue.ruleId.equals(ruleId))
        {
          falsePositives++;
          flagged.add(line + " -> " + issue);
        }
      }
    }
    double precision = 1.0 - (double)falsePositives / negative.size();
    assertTrue(ruleId + " negative precision " + precision
        + " must be >=0.98; flagged: " + flagged,
        precision >= 0.98);
  }

  /** Apply every returned issue back-to-front and return the result. */
  private static String apply(String sentence)
  {
    List<GrammarIssue> issues = GrammarRules.check(sentence, data);
    StringBuilder out = new StringBuilder(sentence);
    for (int i = issues.size() - 1; i >= 0; i--)
    {
      GrammarIssue issue = issues.get(i);
      out.replace(issue.offset, issue.offset + issue.length,
          issue.replacement);
    }
    return out.toString();
  }

  private static GrammarIssue only(List<GrammarIssue> issues)
  {
    assertEquals("expected exactly one issue, got " + issues, 1,
        issues.size());
    return issues.get(0);
  }

  private static List<String[]> fixtures(String name) throws IOException
  {
    List<String[]> rows = new ArrayList<>();
    for (String line : lines(name))
      rows.add(line.split("\t", -1));
    return rows;
  }

  private static List<String> lines(String name) throws IOException
  {
    List<String> lines = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(
          new FileInputStream("test/resources/grammar/" + name),
          StandardCharsets.UTF_8)))
    {
      String line;
      while ((line = reader.readLine()) != null)
      {
        if (!line.trim().isEmpty())
          lines.add(line);
      }
    }
    return lines;
  }
}
