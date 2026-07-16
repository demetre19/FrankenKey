package juloo.keyboard2.autocorrect;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Locale;
import juloo.cdict.Cdict;
import juloo.keyboard2.TouchTrace;
import juloo.keyboard2.dict.Dictionaries;
import juloo.keyboard2.lang.LanguagePack;
import juloo.keyboard2.lang.LanguagePackManager;
import juloo.keyboard2.suggestions.Decoder;
import juloo.keyboard2.suggestions.PersonalizationStore;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class SwiftKeyParityInstrumentedTest
{
  private Hunspell _hunspell;
  private Cdict _dictionary;

  @Before
  public void setUp() throws Exception
  {
    Context target = InstrumentationRegistry.getInstrumentation()
      .getTargetContext();
    LanguagePack pack = new LanguagePackManager(target).find("en_AU");
    assertNotNull("The bundled Australian pack is required for parity.", pack);
    _hunspell = Hunspell.load(pack);

    InputStream input = target.getAssets().open("dictionaries/en_AU.dict");
    byte[] bytes;
    try { bytes = readAll(input); }
    finally { input.close(); }
    _dictionary = Dictionaries.find_by_name(Cdict.of_bytes(bytes), "main");
    assertNotNull("The bundled Australian Cdict must be loadable.", _dictionary);
  }

  @After
  public void tearDown()
  {
    if (_hunspell != null)
      _hunspell.close();
  }

  @Test
  public void maintainsObservedSwiftKeyAutocorrectionParityFloor()
      throws Exception
  {
    JSONArray corpus = new JSONArray(readOracle());
    StringBuilder mismatches = new StringBuilder();
    int finalMatches = 0;
    int primaryMatches = 0;

    for (int i = 0; i < corpus.length(); i++)
    {
      JSONObject item = corpus.getJSONObject(i);
      String typed = item.getString("typed");
      String expectedFinal = normalized(item.getString("swiftFinal"));
      JSONArray swiftSuggestions = item.getJSONArray("swiftSuggestions");
      String expectedPrimary = swiftSuggestions.length() > 1
        ? normalized(swiftSuggestions.getString(1)) : expectedFinal;

      Decoder.Result result = decode(i + 1, typed);
      String actualFinal = result.autocorrection == null
        ? normalized(typed) : normalized(result.autocorrection.surface);
      Decoder.Candidate[] words = result.words();
      String actualPrimary = words.length == 0 ? ""
        : normalized(words[0].surface);

      if (expectedFinal.equals(actualFinal))
        finalMatches++;
      else
        mismatches.append("final ").append(typed).append(": expected ")
          .append(expectedFinal).append(", got ").append(actualFinal)
          .append('\n');

      if (expectedPrimary.equals(actualPrimary))
        primaryMatches++;
      else
        mismatches.append("primary ").append(typed).append(": expected ")
          .append(expectedPrimary).append(", got ").append(actualPrimary)
          .append('\n');

      if (result.autocorrection != null)
      {
        assertTrue("A committed correction must remain visible for " + typed,
            words.length > 0);
        assertEquals("Display and commit must share one primary candidate for "
            + typed, actualFinal, actualPrimary);
      }
    }

    String summary = "SwiftKey parity: final " + finalMatches + "/"
      + corpus.length() + ", primary " + primaryMatches + "/"
      + corpus.length();
    assertTrue(summary + "\n" + mismatches, finalMatches >= 82);
    assertTrue(summary + "\n" + mismatches, primaryMatches >= 78);
  }

  @Test
  public void sameLengthTwoEditRepairCommitsHello()
  {
    Decoder.Result result = decode(1000, "hrllp");
    assertNotNull("hrllp must autocorrect under the Australian dictionary.",
        result.autocorrection);
    assertEquals("hello", normalized(result.autocorrection.surface));
    assertTrue(result.words().length > 0);
    assertEquals("hello", normalized(result.words()[0].surface));
  }


  @Test
  public void generatesCommonMissingApostropheContractions()
  {
    Decoder.Result missing = decode(1001, "theyll");
    assertNotNull("theyll must autocorrect with the bundled dictionary.",
        missing.autocorrection);
    assertEquals("they'll", normalized(missing.autocorrection.surface));

    Decoder.Result ambiguous = decode(1002, "well");
    assertNull("Recognized well must remain literal until repeated choices establish intent.",
        ambiguous.autocorrection);
    boolean includesContraction = false;
    for (Decoder.Candidate candidate : ambiguous.words())
      includesContraction |= "we'll".equals(normalized(candidate.surface));
    assertTrue("well must offer we'll without forcing an ambiguous correction.",
        includesContraction);

    Decoder.Result shortContraction = decode(1003, "im");
    assertNotNull("Unlearned im must autocorrect with the bundled dictionary.",
        shortContraction.autocorrection);
    assertEquals("I'm", shortContraction.autocorrection.surface);
  }

  @Test
  public void repeatedApostrophePreferenceAlwaysCapitalizesFirstPersonI()
  {
    PersonalizationStore store = PersonalizationStore.empty();
    store.record_word("im");
    for (int count = 1; count < 4; count++)
    {
      store.record_commit("i'm", "Im");
      assertNull("Learned apostrophe preference must retain the four-choice threshold.",
          decode(1100 + count, "Im", store).autocorrection);
    }
    store.record_commit("i'm", "Im");
    Decoder.Result learned = decode(1104, "Im", store);
    assertNotNull("The fourth accepted Im to I'm choice must autocorrect on Space.",
        learned.autocorrection);
    assertEquals("I'm", learned.autocorrection.surface);
    Decoder.Result lowercase = decode(1105, "im", store);
    assertNotNull("Learned lowercase im must autocorrect on Space.",
        lowercase.autocorrection);
    assertEquals("I'm", lowercase.autocorrection.surface);
  }

  private Decoder.Result decode(long generation, String typed)
  {
    return decode(generation, typed, PersonalizationStore.empty());
  }

  private Decoder.Result decode(long generation, String typed,
      PersonalizationStore personalization)
  {
    TouchTrace touches = new TouchTrace();
    int count = typed.codePointCount(0, typed.length());
    for (int i = 0; i < count; i++)
      touches.add(TouchTrace.entry(100f, 100f, 100f, 100f, 20f, 20f));
    Decoder.Request request = new Decoder.Request(
        new Decoder.RequestKey(1, generation, generation, 1, 1, 1, 1),
        typed, touches.snapshot(), Decoder.Geometry.from(null),
        new Decoder.DecoderConfig(true, true, true, true));
    return new Decoder().decode(request, _dictionary, null, _hunspell,
        personalization, false);
  }

  private String readOracle() throws Exception
  {
    InputStream input = InstrumentationRegistry.getInstrumentation()
      .getContext().getAssets().open("swiftkey_autocorrection_oracle.json");
    try { return new String(readAll(input), "UTF-8"); }
    finally { input.close(); }
  }

  private static byte[] readAll(InputStream input) throws Exception
  {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[4096];
    int read;
    while ((read = input.read(buffer)) != -1)
      output.write(buffer, 0, read);
    return output.toByteArray();
  }

  private static String normalized(String value)
  {
    return value.toLowerCase(Locale.ROOT).replace('’', '\'');
  }
}
