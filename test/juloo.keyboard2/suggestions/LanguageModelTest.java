package juloo.keyboard2.suggestions;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class LanguageModelTest
{
  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void loadsNormalizedBoundedPreviousWordPriors() throws Exception
  {
    LanguageModel model = load("# previous\\tnext\\tweight\n"
        + "examples\tof\t11\n"
        + "kind\tof\t12\n"
        + "me\tthe\t9\n"
        + "show\tme\tthe\t14\n");

    assertEquals(11, model.weight("examples", "of"));
    assertEquals(12, model.weight("kind", "of"));
    assertEquals("A matching trigram must outrank its weaker bigram.",
        14, model.weight("show", "me", "the"));
    assertEquals("A missing trigram must fall back to the bigram.",
        9, model.weight("please", "me", "the"));
    assertEquals("Unlisted contexts must not influence ordinary English text.",
        0, model.weight("either", "or"));
    assertEquals(0, model.weight(null, "of"));
    assertEquals(0, model.weight("please", "show", "the"));
  }

  @Test
  public void emptyRequestPredictsNextWordsFromPackagedPriors() throws Exception
  {
    LanguageModel model = load("good\tmorning\t12\n"
        + "good\tnight\t9\n"
        + "good\tluck\t12\n"
        + "very\tgood\tidea\t14\n"
        + "very\tgood\tmorning\t3\n");
    PersonalizationStore store = PersonalizationStore.empty();
    store.record_word("very");
    store.record_word("good");

    Decoder.Result result = new Decoder().decode(request(""), null, null,
        null, store, model, false);

    String[] words = new String[result.words().length];
    for (int i = 0; i < words.length; i++)
    {
      words[i] = result.words()[i].surface;
      assertEquals(Decoder.Role.NEXT_WORD, result.words()[i].role);
    }
    assertArrayEquals("Trigram rows lead, then bigram rows by weight, without duplicates.",
        new String[] { "idea", "morning", "luck", "night" }, words);

    PersonalizationStore fresh = PersonalizationStore.empty();
    assertEquals("Without a previous word there is nothing to predict.",
        0, new Decoder().decode(request(""), null, null, null, fresh, model,
          false).words().length);
  }

  private static Decoder.Request request(String typed)
  {
    return new Decoder.Request(new Decoder.RequestKey(1, 1, 1, 1, 1, 1, 1),
        typed, (juloo.keyboard2.TouchTrace.Snapshot)null,
        Decoder.Geometry.from(null),
        new Decoder.DecoderConfig(true, true, true, true));
  }

  @Test
  public void rejectsMalformedDuplicateAndOutOfRangeRows() throws Exception
  {
    assertMalformed("examples of 15\n", "three or four tab-separated fields");
    assertMalformed("Examples\tof\t15\n", "normalized and non-empty");
    assertMalformed("examples\tof\t16\n", "between 1 and 15");
    assertMalformed("examples\tof\t15\nexamples\tof\t14\n",
        "duplicate bigram");
    assertMalformed("show\tme\tthe\t14\nshow\tme\tthe\t13\n",
        "duplicate trigram");
  }

  @Test
  public void rejectsModelsBeyondTheWorkerMemoryBound() throws Exception
  {
    StringBuilder rows = new StringBuilder();
    for (int i = 0; i <= 4096; i++)
      rows.append("word").append(i).append("\tnext\t1\n");

    assertMalformed(rows.toString(), "exceeds 4096 ngrams");
  }

  private LanguageModel load(String content) throws Exception
  {
    File file = temp.newFile();
    Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    return LanguageModel.load(file);
  }

  private void assertMalformed(String content, String reason) throws Exception
  {
    try
    {
      load(content);
      fail("Malformed language model must fail: " + reason);
    }
    catch (IOException e)
    {
      assertTrue("Failure must identify the violated model contract: "
          + e.getMessage(), e.getMessage() != null
            && e.getMessage().contains(reason));
    }
  }
}
