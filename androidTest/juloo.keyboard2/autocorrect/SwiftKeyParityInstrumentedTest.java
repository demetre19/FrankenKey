package juloo.keyboard2.autocorrect;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import android.os.Handler;
import android.os.Looper;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.util.Locale;
import juloo.cdict.Cdict;
import juloo.keyboard2.KeyboardData;
import juloo.keyboard2.R;
import juloo.keyboard2.CurrentlyTypedWord;
import juloo.keyboard2.TouchTrace;
import juloo.keyboard2.dict.Dictionaries;
import juloo.keyboard2.lang.LanguagePack;
import juloo.keyboard2.lang.LanguagePackManager;
import juloo.keyboard2.suggestions.Decoder;
import juloo.keyboard2.suggestions.LanguageModel;
import juloo.keyboard2.suggestions.PersonalizationStore;
import juloo.keyboard2.suggestions.SharedDecoder;
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
  private Decoder.Geometry _geometry;
  private LanguagePack _languagePack;
  private LanguageModel _languageModel;
  private KeyboardData _layout;

  @Before
  public void setUp() throws Exception
  {
    Context target = InstrumentationRegistry.getInstrumentation()
      .getTargetContext();
    _languagePack = new LanguagePackManager(target).find("en_AU");
    assertNotNull("The bundled Australian pack is required for parity.",
        _languagePack);
    assertNotNull("The bundled Australian context model is required for parity.",
        _languagePack.next_words);
    _languageModel = LanguageModel.load(_languagePack.next_words);
    _hunspell = Hunspell.load(_languagePack);
    _layout = KeyboardData.load(target.getResources(), R.xml.clean_text);
    assertNotNull("The production clean layout is required for parity.",
        _layout);
    _geometry = Decoder.Geometry.from(_layout);

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
          .append("; ").append(candidateSummary(result)).append('\n');

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
  public void coversIsolatedEnglishOracleWithoutChangingValidWords()
      throws Exception
  {
    JSONArray corpus = new JSONArray(readOracle());
    StringBuilder failures = new StringBuilder();
    int contextRequired = 0;
    for (int i = 0; i < corpus.length(); ++i)
    {
      JSONObject item = corpus.getJSONObject(i);
      String typed = item.getString("typed");
      String literal = normalized(typed);
      String intended = normalized(item.getString("intended"));
      String swiftFinal = normalized(item.getString("swiftFinal"));
      boolean validLiteral = _hunspell.spell(typed);
      boolean ambiguousWithoutContext = item.optBoolean("requiresContext")
        || !swiftFinal.equals(intended);
      if (ambiguousWithoutContext)
        contextRequired++;
      if (ambiguousWithoutContext && !validLiteral)
        continue;
      Decoder.Result result = decodeFixed(10000 + i, typed);
      String actual = decodedSurface(result, typed);
      boolean accepted = validLiteral
        ? literal.equals(actual)
        : intended.equals(actual);
      if (!accepted)
        failures.append(typed).append(" -> ")
          .append(validLiteral ? literal : intended)
          .append(", got ").append(actual).append("; ")
          .append(candidateSummary(result)).append('\n');
    }
    assertTrue("The observed comparison must keep context-required cases explicit.",
        contextRequired > 0);
    assertEquals("Unambiguous isolated errors must recover and valid words must remain literal; context-required cases are covered separately:\n"
        + failures, 0, failures.length());
  }

  @Test
  public void exceedsValidatedGboardCorrectionAndSafetyScore()
      throws Exception
  {
    JSONObject oracle = new JSONObject(readAsset(
          "gboard_autocorrection_oracle.json"));
    JSONObject identity = oracle.getJSONObject("identity");
    assertEquals("com.google.android.inputmethod.latin",
        identity.getString("package"));
    assertEquals("15.1.08.726012951-preload-arm64-v8a",
        identity.getString("version_name"));
    assertEquals("636b16dde5a77b4e6ebf25be5cc1e2a799488905bc718420125857cba4203c11",
        identity.getString("sha256"));

    JSONArray corpus = oracle.getJSONArray("results");
    int gboardMatches = 0;
    int frankenKeyMatches = 0;
    int gboardHarmful = 0;
    int frankenKeyHarmful = 0;
    int literalCases = 0;
    StringBuilder failures = new StringBuilder();
    StringBuilder harmful = new StringBuilder();
    StringBuilder misses = new StringBuilder();
    for (int i = 0; i < corpus.length(); i++)
    {
      JSONObject item = corpus.getJSONObject(i);
      String typed = normalized(item.getString("typed"));
      String intended = normalized(item.getString("intended"));
      String gboard = normalized(item.getString("referenceFinal"));
      Decoder.Result decoded = decodeFixed(
          20000 + i, item.getString("typed"));
      String actual = decodedSurface(decoded, item.getString("typed"));

      if (gboard.equals(intended))
        gboardMatches++;
      if (actual.equals(intended))
        frankenKeyMatches++;
      else
        misses.append(typed).append("->").append(actual).append("; ");
      if (!typed.equals(intended))
      {
        if (!gboard.equals(typed) && !gboard.equals(intended))
        {
          gboardHarmful++;
          harmful.append("Gboard[").append(i).append("] ")
            .append(typed).append(" -> ").append(gboard)
            .append(" (expected ").append(intended).append("); ");
        }
        if (!actual.equals(typed) && !actual.equals(intended))
        {
          frankenKeyHarmful++;
          harmful.append("FrankenKey[").append(i).append("] ")
            .append(typed).append(" -> ").append(actual)
            .append(" (expected ").append(intended).append("); ")
            .append(candidateSummary(decoded)).append("; ");
        }
      }
      else
      {
        literalCases++;
        if (!actual.equals(typed))
          failures.append(typed).append(" changed to ").append(actual)
            .append('\n');
      }
    }
    android.util.Log.i("FrankenKeyParity",
        "Gboard corpus: FrankenKey intended=" + frankenKeyMatches
        + "/98 harmful=" + frankenKeyHarmful
        + "; Gboard intended=" + gboardMatches
        + "/98 harmful=" + gboardHarmful
        + "; literals=" + literalCases + "; misses=" + misses);

    assertEquals("The validated Gboard oracle must remain complete.",
        98, corpus.length());
    assertEquals("The shared corpus must retain every valid-word control.",
        19, literalCases);
    assertTrue("FrankenKey must recover at least as many intended outputs as the validated Gboard soft-key run: FrankenKey "
        + frankenKeyMatches + ", Gboard " + gboardMatches,
        frankenKeyMatches >= gboardMatches);
    assertEquals("FrankenKey must improve on the validated Gboard run by introducing no harmful replacements; Gboard harmful="
        + gboardHarmful + "\n" + harmful, 0, frankenKeyHarmful);
    assertEquals("FrankenKey must preserve all shared valid-word controls:\n"
        + failures, 0, failures.length());
  }

  @Test
  public void preservesCleanEnglishChapterTokens() throws Exception
  {
    JSONObject chapter = new JSONObject(readAsset(
          "keyboard_chapter_typo_corpus.json"));
    JSONArray sentences = chapter.getJSONArray("sentences");
    java.util.HashSet<String> checked = new java.util.HashSet<String>();
    StringBuilder failures = new StringBuilder();
    long generation = 11000;
    for (int i = 0; i < sentences.length(); ++i)
    {
      String[] words = sentences.getJSONObject(i).getString("expected")
        .split("[^A-Za-z']+");
      for (String word : words)
      {
        if (word.length() == 0 || !checked.add(normalized(word)))
          continue;
        Decoder.Result result = decodeFixed(generation++, word);
        String actual = decodedSurface(result, word);
        if (!normalized(word).equals(actual))
          failures.append(word).append(" changed to ").append(actual)
            .append("; ").append(candidateSummary(result)).append('\n');
      }
    }
    assertEquals("Correct English chapter words must remain unchanged before any personalization:\n"
        + failures, 0, failures.length());
  }

  @Test
  public void liveSentenceTokensSeparateColdAndLearnedReplayLanes()
      throws Exception
  {
    JSONArray sentences = new JSONArray(readAsset(
          "frankenkey_live_sentence_oracle.json"));
    PersonalizationStore profile = PersonalizationStore.empty();
    StringBuilder coldMismatches = new StringBuilder();
    StringBuilder learnedMismatches = new StringBuilder();
    StringBuilder outputOnlyCases = new StringBuilder();
    int coldRecovered = 0;
    int learnedRecovered = 0;
    int scoreable = 0;
    int outputOnly = 0;
    long generation = 2000;

    // Cold lane: score every isolated token before recording any correction.
    for (int i = 0; i < sentences.length(); i++)
    {
      JSONObject sentence = sentences.getJSONObject(i);
      JSONArray tokens = sentence.getJSONArray("tokens");
      for (int j = 0; j < tokens.length(); j++)
      {
        JSONObject token = tokens.getJSONObject(j);
        if (token.getString("kind").startsWith("output-only-"))
        {
          outputOnly++;
          outputOnlyCases.append(sentence.getString("id")).append(' ')
            .append(token.getString("previous")).append(" [")
            .append(token.getString("typed")).append("] ")
            .append(token.getString("next")).append('\n');
          continue;
        }
        profile.reset_context();
        String typed = token.getString("typed");
        String expected = normalized(token.getString("expected"));
        String actual = decodedSurface(decode(generation++, typed, profile),
            typed);
        scoreable++;
        if (expected.equals(actual))
          coldRecovered++;
        else
          appendMismatch(coldMismatches, sentence, token, expected, actual);
      }
    }

    // Training lane: completed prior occurrences are recorded as one
    // editor-verified event each. The replay does not happen in this loop.
    java.util.HashSet<String> completedOccurrences =
      new java.util.HashSet<String>();
    for (int i = 0; i < sentences.length(); i++)
    {
      JSONObject sentence = sentences.getJSONObject(i);
      JSONArray tokens = sentence.getJSONArray("tokens");
      for (int j = 0; j < tokens.length(); j++)
      {
        JSONObject token = tokens.getJSONObject(j);
        if (token.getString("kind").startsWith("output-only-"))
          continue;
        String previous = token.getString("previous");
        String typed = token.getString("typed");
        String expected = token.getString("expected");
        String occurrence = normalized(previous) + '\t' + normalized(typed)
          + '\t' + normalized(expected);
        if (!completedOccurrences.add(occurrence))
          continue;
        profile.reset_context();
        if (previous.length() > 0)
          profile.record_word(previous);
        profile.record_commit(expected, typed);
      }
    }

    // Learned-replay lane: use the same profile, resetting only active
    // sentence context and restoring the fixture's completed previous word.
    for (int i = 0; i < sentences.length(); i++)
    {
      JSONObject sentence = sentences.getJSONObject(i);
      JSONArray tokens = sentence.getJSONArray("tokens");
      for (int j = 0; j < tokens.length(); j++)
      {
        JSONObject token = tokens.getJSONObject(j);
        if (token.getString("kind").startsWith("output-only-"))
          continue;
        String previous = token.getString("previous");
        profile.reset_context();
        if (previous.length() > 0)
          profile.record_word(previous);
        String typed = token.getString("typed");
        String expected = normalized(token.getString("expected"));
        String actual = decodedSurface(decode(generation++, typed, profile),
            typed);
        if (expected.equals(actual))
          learnedRecovered++;
        else
          appendMismatch(learnedMismatches, sentence, token, expected, actual);
      }
    }

    String summary = "Live lanes: cold " + coldRecovered + "/" + scoreable
      + ", learned replay " + learnedRecovered + "/" + scoreable
      + ", output-only next-context " + outputOnly;
    assertTrue(summary + "\nCold mismatches:\n" + coldMismatches
        + "Learned mismatches:\n" + learnedMismatches
        + "Output-only cases (reported, not isolated-correction gated):\n"
        + outputOnlyCases,
        learnedRecovered * 100 >= scoreable * 95);
    assertTrue("A profile trained only from completed prior occurrences must not regress the honest cold lane. "
        + summary, learnedRecovered >= coldRecovered);
    assertTrue("The adaptive fixture must prove learned replay improves over the cold lane rather than silently pretraining each scored token. "
        + summary, learnedRecovered > coldRecovered);
    assertTrue("Observed-output next-context cases must be labeled and reported separately instead of treated as known raw keystrokes.",
        outputOnly > 0);
  }

  @Test
  public void coldPreviousWordContextResolvesObservedValidWordTypos()
      throws Exception
  {
    JSONArray sentences = new JSONArray(readAsset(
          "frankenkey_live_sentence_oracle.json"));
    StringBuilder failures = new StringBuilder();
    int scored = 0;
    long generation = 3000;
    for (int i = 0; i < sentences.length(); i++)
    {
      JSONObject sentence = sentences.getJSONObject(i);
      JSONArray tokens = sentence.getJSONArray("tokens");
      for (int j = 0; j < tokens.length(); j++)
      {
        JSONObject token = tokens.getJSONObject(j);
        if (!"previous-context-real-word".equals(token.getString("kind")))
          continue;
        String typed = token.getString("typed");
        String expected = normalized(token.getString("expected"));
        PersonalizationStore profile = PersonalizationStore.empty();
        profile.record_word(token.getString("previous"));
        String previous = normalized(token.getString("previous"));
        assertEquals("The profile must expose the completed previous word.",
            previous, previousWord(profile));
        assertEquals("The packaged model must expose this observed prior.",
            15, languageModelWeight(previous, expected));
        Decoder.Result result = decodeFixed(generation++, typed, profile);
        String actual = decodedSurface(result, typed);
        scored++;
        if (!expected.equals(actual))
        {
          appendMismatch(failures, sentence, token, expected, actual);
          failures.append(candidateSummary(result)).append('\n');
        }
      }
    }
    assertEquals("The observed cold-context lane must keep all three previous-word cases executable.",
        3, scored);
    assertEquals("Previous-word context must resolve observed valid-word typos without changing valid controls:\n"
        + failures, 0, failures.length());

    assertEquals("or", decodeWithPrevious(generation++, "either", "or"));
    assertEquals("us", decodeWithPrevious(generation++, "join", "us"));
    assertEquals("if", decodeWithPrevious(generation++, "ask", "if"));
  }

  @Test
  public void packagedContextRepairsReportedShortWordTypos()
  {
    PersonalizationStore going = PersonalizationStore.empty();
    going.record_word("going");
    Decoder.Result ti = decodeFixed(3050, "ti", going);
    assertEquals(candidateSummary(ti), "to", decodedSurface(ti, "ti"));

    PersonalizationStore how = PersonalizationStore.empty();
    how.record_word("how");
    Decoder.Result ut = decodeFixed(3051, "ut", how);
    assertEquals(candidateSummary(ut), "it", decodedSurface(ut, "ut"));
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
  public void coversCommonEnglishContractionsAndColdAmbiguityControls()
  {
    String[][] corrections = new String[][] {
      { "im", "i'm" },
      { "youre", "you're" },
      { "theyre", "they're" },
      { "weve", "we've" },
      { "youve", "you've" },
      { "theyve", "they've" },
      { "youll", "you'll" },
      { "theyll", "they'll" },
      { "dont", "don't" },
      { "doesnt", "doesn't" },
      { "didnt", "didn't" },
      { "isnt", "isn't" },
      { "wasnt", "wasn't" },
      { "werent", "weren't" },
      { "couldnt", "couldn't" },
      { "wouldnt", "wouldn't" },
      { "shouldnt", "shouldn't" },
      { "havent", "haven't" },
      { "hasnt", "hasn't" },
      { "hadnt", "hadn't" },
      { "mightnt", "mightn't" },
      { "mustnt", "mustn't" },
      { "neednt", "needn't" },
      { "whos", "who's" }
    };
    long generation = 12000;
    for (String[] pair : corrections)
    {
      Decoder.Result result = decode(generation++, pair[0]);
      assertNotNull(pair[0] + " must recover " + pair[1] + "; "
          + candidateSummary(result), result.autocorrection);
      assertEquals(pair[0] + " must recover the exact apostrophe form.",
          pair[1], normalized(result.autocorrection.surface));
    }

    for (String valid : new String[] {
        "well", "were", "shell", "hell", "ill", "wed", "shed", "lets",
        "its", "arent", "cant", "wont"
      })
    {
      Decoder.Result result = decode(generation++, valid);
      assertNull("A cold valid English word must not be forced into an ambiguous contraction: "
          + valid + "; " + candidateSummary(result), result.autocorrection);
    }
  }

  @Test
  public void preservesCorrectionCasingAcrossEnglishModes()
  {
    String[][] corrections = new String[][] {
      { "teh", "the" },
      { "Teh", "The" },
      { "helllo", "hello" },
      { "Helllo", "Hello" },
      { "HELLLO", "HELLO" },
      { "theyll", "they'll" },
      { "Theyll", "They'll" },
      { "THEYLL", "THEY'LL" }
    };
    long generation = 13000;
    for (String[] pair : corrections)
    {
      Decoder.Result result = decode(generation++, pair[0]);
      assertNotNull(pair[0] + " must retain its casing mode after correction; "
          + candidateSummary(result), result.autocorrection);
      assertEquals(pair[1], result.autocorrection.surface);
    }

    for (String protectedText : new String[] {
        "tEh", "hELllo", "STM", "GLM", "API", "AI", "OMP", "CMUX"
      })
    {
      Decoder.Result result = decode(generation++, protectedText);
      assertNull("Mixed-case text and cold technical acronyms must remain literal: "
          + protectedText + "; " + candidateSummary(result),
          result.autocorrection);
    }
  }


  @Test
  public void coversCommonEnglishInflectionAndSuffixTypos()
  {
    String[][] corrections = new String[][] {
      { "runing", "running" },
      { "stoped", "stopped" },
      { "begining", "beginning" },
      { "prefered", "preferred" },
      { "writting", "writing" },
      { "comming", "coming" },
      { "tryed", "tried" },
      { "carryed", "carried" },
      { "studys", "studies" },
      { "citys", "cities" },
      { "boxs", "boxes" },
      { "dishs", "dishes" },
      { "watchs", "watches" },
      { "buzzs", "buzzes" },
      { "classs", "classes" },
      { "happyness", "happiness" },
      { "usefull", "useful" },
      { "hopefull", "hopeful" },
      { "succesful", "successful" },
      { "ocurred", "occurred" }
    };
    long generation = 14000;
    for (String[] pair : corrections)
    {
      Decoder.Result result = decode(generation++, pair[0]);
      assertNotNull(pair[0] + " must recover the common inflected form "
          + pair[1] + "; " + candidateSummary(result),
          result.autocorrection);
      assertEquals(pair[0] + " must recover " + pair[1] + "; "
          + candidateSummary(result), pair[1],
          normalized(result.autocorrection.surface));
    }

    for (String valid : new String[] {
        "planing", "learned", "learnt", "focused", "focussed", "traveling",
        "travelling", "program", "programme"
      })
    {
      Decoder.Result result = decode(generation++, valid);
      assertNull("A valid English or locale-variant form must stay literal: "
          + valid + "; " + candidateSummary(result), result.autocorrection);
    }
  }

  @Test
  public void preservesNamesSlangAndTechnicalTokensCold() throws Exception
  {
    Context target = InstrumentationRegistry.getInstrumentation()
      .getTargetContext();
    LanguagePackManager manager = new LanguagePackManager(target);
    String[] sharedLexicon = new String[] {
      "gboard", "omp", "cmux", "npm", "pnpm", "adb", "localhost", "lol",
      "lmao", "brb", "idk", "tbh", "omg"
    };
    for (String id : new String[] { "en_AU", "en_GB", "en_US" })
    {
      LanguagePack pack = manager.find(id);
      assertNotNull(id + " must remain a bundled English pack.", pack);
      Hunspell checker = "en_AU".equals(id) ? _hunspell : Hunspell.load(pack);
      try
      {
        for (String word : sharedLexicon)
          assertTrue(id + " must recognize protected English token " + word,
              checker.spell(word));
      }
      finally
      {
        if (checker != _hunspell)
          checker.close();
      }
    }
    String[] tokens = new String[] {
      "Demetre", "Samsung", "Gboard", "FrankenKey", "OpenAI", "GitHub",
      "YouTube", "macOS", "iPhone", "Android", "Termux", "CMUX", "OMP",
      "SEO", "API", "JWT", "JSON", "Kotlin", "JavaScript", "TypeScript",
      "PostgreSQL", "gonna", "wanna", "kinda", "sorta", "yep", "nope",
      "okay", "ok", "lol", "lmao", "brb", "btw", "idk", "imo", "tbh",
      "omg", "foo_bar", "camelCase", "snake_case", "npm", "pnpm", "bun",
      "adb", "ssh", "localhost", "127.0.0.1", "user@example.com",
      "https://example.com", "dev.frankenkey.keyboard"
    };
    long generation = 15000;
    StringBuilder failures = new StringBuilder();
    for (String token : tokens)
    {
      Decoder.Result result = decode(generation++, token);
      if (result.autocorrection != null)
        failures.append(token).append(" changed to ")
          .append(result.autocorrection.surface).append("; ")
          .append(candidateSummary(result)).append('\n');
    }
    assertEquals("Cold names, common slang, and technical tokens must remain typeable without forced replacements:\n"
        + failures, 0, failures.length());
  }
  @Test
  public void repairsMissingNegativeContractionAndClearShortTypos()
  {
    assertTrue("The bundled Hunspell dictionary must recognize doesn't.",
        _hunspell.spell("doesn't"));
    Decoder.Result negative = decode(1004, "doest");
    StringBuilder offered = new StringBuilder();
    boolean offeredNegative = false;
    for (Decoder.Candidate candidate : negative.words())
    {
      if (offered.length() > 0)
        offered.append(", ");
      offered.append(normalized(candidate.surface));
      offeredNegative |= "doesn't".equals(normalized(candidate.surface));
    }
    assertTrue("The real decoder must collect doesn't before choosing; offered="
        + offered + ", literalMask=" + negative.literal.sourceMask,
        offeredNegative);
    assertNotNull("Bundled Hunspell generated doesn't but the commit chooser rejected it; offered="
        + offered + ", literalMask=" + negative.literal.sourceMask,
        negative.autocorrection);
    assertEquals("doesn't", normalized(negative.autocorrection.surface));

    Decoder.Result ordinary = decode(1005, "cat");
    assertNull("A common word ending in t must remain literal.",
        ordinary.autocorrection);

    Decoder.Result twoLetter = decode(1006, "br");
    assertNotNull("A clear neighboring-key typo must repair an unknown two-letter token.",
        twoLetter.autocorrection);
    assertEquals("be", normalized(twoLetter.autocorrection.surface));

    Decoder.Result firstPerson = decode(1007, "j");
    assertNotNull("A lowercase neighboring key must recover standalone first-person I.",
        firstPerson.autocorrection);
    assertEquals("i", normalized(firstPerson.autocorrection.surface));
    assertEquals("I", firstPerson.autocorrection.surface);
  }

  @Test
  public void repairsReportedShortWordAndTrailingLetterTypos()
  {
    String[][] cases = new String[][] {
      { "ia", "is" },
      { "ad", "as" },
      { "aa", "as" },
      { "fixinf", "fixing" },
      { "teh", "the" },
      { "ths", "this" },
      { "od", "od" },
      { "leter", "letter" },
      { "writr", "writer" },
      { "adn", "and" },
      { "shold", "should" }
    };
    StringBuilder failures = new StringBuilder();
    for (int i = 0; i < cases.length; i++)
    {
      Decoder.Result result = decode(1200 + i, cases[i][0]);
      String actual = decodedSurface(result, cases[i][0]);
      if (!cases[i][1].equals(actual))
        failures.append(cases[i][0]).append(" -> ")
          .append(cases[i][1]).append(", got ").append(actual)
          .append("; ").append(candidateSummary(result)).append('\n');
    }
    assertEquals("Reported correction failures:\n" + failures,
        0, failures.length());
  }

  @Test
  public void repairsReportedSingleTokenMisspellings()
  {
    String[][] cases = new String[][] {
      { "yhere", "there" },
      { "coccrected", "corrected" },
    };
    StringBuilder failures = new StringBuilder();
    for (int i = 0; i < cases.length; i++)
    {
      Decoder.Result result = decode(1250 + i, cases[i][0]);
      String actual = decodedSurface(result, cases[i][0]);
      if (!cases[i][1].equals(actual))
        failures.append(cases[i][0]).append(" -> ")
          .append(cases[i][1]).append(", got ").append(actual)
          .append("; ").append(candidateSummary(result)).append('\n');
    }
    assertEquals("Reported single-token spellcheck failures:\n" + failures,
        0, failures.length());
  }

  @Test
  public void rejectsAmbiguousReportedLengthRepair()
  {
    Decoder.Result result = decode(1259, "eech");
    assertEquals("Without following-word evidence, equally plausible beech/leech length repairs must stay literal instead of committing a harmful guess.",
        "eech", decodedSurface(result, "eech"));
  }

  @Test
  public void leavesExtremeUnprovenPhoneTypoLiteral()
  {
    Decoder.Result result = decode(1260, "Ecerytbjbg");
    assertEquals("A distant ten-letter input with no independent dictionary candidate must stay literal rather than guess a destructive replacement.",
        "ecerytbjbg", decodedSurface(result, "Ecerytbjbg"));
  }

  @Test
  public void documentsReportedProtectedAndConjoinedInputs()
  {
    String[][] cases = new String[][] {
      { "th", "th" },
      { "kr", "kr" },
      { "Thiwbus", "thiwbus" }
    };
    StringBuilder failures = new StringBuilder();
    for (int i = 0; i < cases.length; i++)
    {
      Decoder.Result result = decode(1275 + i, cases[i][0]);
      String actual = decodedSurface(result, cases[i][0]);
      if (!cases[i][1].equals(actual))
        failures.append(cases[i][0]).append(" -> ")
          .append(cases[i][1]).append(", got ").append(actual)
          .append("; ").append(candidateSummary(result)).append('\n');
    }
    assertEquals("Recognized short literals and unproven conjoined inputs must remain literal without contextual evidence:\n"
        + failures, 0, failures.length());
  }

  @Test
  public void fullBoundaryPassCoversSeparatorRepairs() throws Exception
  {
    String[][] cases = new String[][] {
      { "ia", "is" },
      { "ad", "as" },
      { "aa", "as" },
      { "fixinf", "fixing" },
      { "teh", "the" },
      { "ths", "this" },
      { "leter", "letter" },
      { "writr", "writer" },
      { "shold", "should" },
      { "aeem", "seem" },
      { "hellp", "hello" },
      { "od", "od" },
      { "STM", "stm" },
      { "CMUX", "cmux" }
    };
    StringBuilder failures = new StringBuilder();
    for (int i = 0; i < cases.length; i++)
    {
      Decoder.Result result = decode(1300 + i, cases[i][0]);
      String actual = decodedSurface(result, cases[i][0]);
      if (!cases[i][1].equals(actual))
        failures.append(cases[i][0]).append(" -> ")
          .append(cases[i][1]).append(", got ").append(actual)
          .append("; ").append(candidateSummary(result)).append('\n');
    }
    assertEquals("Full separator-boundary failures:\n" + failures,
        0, failures.length());

    assertEquals("The fast typing preview may leave a Hunspell-only short repair literal; the boundary escalation must recover it.",
        "ad", decodedSurface(decodeFast(1400, "ad"), "ad"));
    assertEquals("The full separator-boundary pass must recover the deferred short repair.",
        "as", decodedSurface(decode(1401, "ad"), "ad"));
  }

  @Test
  public void shortPrefixCompletionDoesNotBeatSameLengthRepair()
      throws Exception
  {
    Decoder.Result result = decode(1450, "twi");
    assertNotNull("A short unknown token must not expand to a longer prefix completion when a decisive same-length repair exists.",
        result.autocorrection);
    assertEquals("two", normalized(result.autocorrection.surface));
    Decoder.Result boundary = decodeBoundary(1451, "twi");
    assertTrue(boundary.autocorrectionComplete);
    assertNotNull(boundary.autocorrection);
    assertEquals("two", normalized(boundary.autocorrection.surface));
  }

  @Test
  public void earlyShortRepairsMatchExhaustiveProviderResults()
      throws Exception
  {
    String[] probes = {
      "twi", "Twi", "ths", "hwo", "oen", "cna", "teh", "adn", "wrd",
      "far", "the", "and", "toe", "its", "not", "how"
    };
    int earlyRepairs = 0;
    for (int i = 0; i < probes.length; ++i)
    {
      Decoder.Result early = decodeBoundary(1460 + i, probes[i]);
      if (early.autocorrection == null)
        continue;
      earlyRepairs++;
      Decoder.Result full = decode(1500 + i, probes[i]);
      assertNotNull("An early short repair must survive exhaustive provider expansion for "
          + probes[i], full.autocorrection);
      assertEquals("An early short repair must not change after Hunspell expansion for "
          + probes[i], normalized(full.autocorrection.surface),
          normalized(early.autocorrection.surface));
    }
    assertTrue("The safety probe must exercise at least the reported twi repair.",
        earlyRepairs > 0);
  }

  @Test
  public void verifiedLearningCompletesContextDependentRecognizedTargets()
  {
    PersonalizationStore profile = PersonalizationStore.empty();
    profile.record_word("no");
    profile.record_commit("two", "toe");
    profile.reset_context();
    profile.record_word("no");
    Decoder.Result contextual = decode(1580, "toe", profile);
    assertNotNull("One editor-verified exact context may resolve a recognized literal.",
        contextual.autocorrection);
    assertEquals("two", normalized(contextual.autocorrection.surface));

    for (int i = 0; i < 4; ++i)
    {
      profile.reset_context();
      profile.record_commit("It's", "Its");
    }
    profile.reset_context();
    Decoder.Result repeated = decode(1581, "Its", profile);
    assertNotNull("Four exact accepted contraction choices may resolve a recognized literal without phrase hardcoding.",
        repeated.autocorrection);
    assertEquals("it's", normalized(repeated.autocorrection.surface));
  }






  @Test
  public void productionBoundaryEscalationCompletesShortRepair()
      throws Exception
  {
    final java.util.ArrayList<Decoder.Result> completed =
      new java.util.ArrayList<Decoder.Result>();
    SharedDecoder decoder = new SharedDecoder(
        new Handler(Looper.getMainLooper()), new SharedDecoder.Callback()
        {
          @Override
          public void decoder_state_changed(SharedDecoder.Presentation state)
          {
          }

          @Override
          public void decoder_result_completed(Decoder.Result result)
          {
            synchronized (completed)
            {
              completed.add(result);
            }
          }
        });
    try
    {
      SharedDecoder.ResourceSpec resources = new SharedDecoder.ResourceSpec(
          "en_AU-test", new Cdict[] { _dictionary }, _dictionary, null,
          _languagePack);
      long session = decoder.start_session(
          new Decoder.DecoderConfig(true, true, true, true), resources,
          _layout, SharedDecoder.PersonalizationSpec.empty("parity-test"));
      Constructor<CurrentlyTypedWord.Snapshot> constructor =
        CurrentlyTypedWord.Snapshot.class.getDeclaredConstructor(long.class,
            String.class, int.class, boolean.class,
            TouchTrace.Snapshot.class);
      constructor.setAccessible(true);
      TouchTrace touches = new TouchTrace();
      for (int i = 0; i < 3; ++i)
        touches.add(TouchTrace.entry(100f, 100f, 100f, 100f, 20f, 20f));
      CurrentlyTypedWord.Snapshot word = constructor.newInstance(1L, "twi",
          0, false, touches.snapshot());
      Decoder.RequestKey key = decoder.request(session, word);

      Decoder.Result fast = awaitResult(decoder, key, false);
      assertFalse("The fast request must remain provisional before Space.",
          fast.autocorrectionComplete);
      assertTrue(decoder.retain_boundary_request(session, key));

      Decoder.Result full = awaitResult(decoder, key, true);
      assertNotNull("The production worker boundary pass must expose the decisive same-length repair.",
          full.autocorrection);
      assertEquals("two", normalized(full.autocorrection.surface));
    }
    finally
    {
      decoder.close();
    }
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

  private Decoder.Result decodeFixed(long generation, String typed)
  {
    return decodeFixed(generation, typed, PersonalizationStore.empty());
  }

  private Decoder.Result decodeFixed(long generation, String typed,
      PersonalizationStore personalization)
  {
    Decoder.Request request = new Decoder.Request(
        new Decoder.RequestKey(1, generation, generation, 1, 1, 1, 1),
        typed, (TouchTrace.Snapshot)null, _geometry,
        new Decoder.DecoderConfig(true, true, true, true));
    return new Decoder().decode(request, _dictionary, null, _hunspell,
        personalization, _languageModel, false);
  }

  private String decodeWithPrevious(long generation, String previous,
      String typed)
  {
    PersonalizationStore profile = PersonalizationStore.empty();
    profile.record_word(previous);
    return decodedSurface(decodeFixed(generation, typed, profile), typed);
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
        typed, touches.snapshot(), _geometry,
        new Decoder.DecoderConfig(true, true, true, true));
    return new Decoder().decode(request, _dictionary, null, _hunspell,
        personalization, _languageModel, false);
  }

  private Decoder.Result decodeFast(long generation, String typed)
      throws Exception
  {
    TouchTrace touches = new TouchTrace();
    int count = typed.codePointCount(0, typed.length());
    for (int i = 0; i < count; i++)
      touches.add(TouchTrace.entry(100f, 100f, 100f, 100f, 20f, 20f));
    Decoder.Request request = new Decoder.Request(
        new Decoder.RequestKey(1, generation, generation, 1, 1, 1, 1),
        typed, touches.snapshot(), _geometry,
        new Decoder.DecoderConfig(true, true, true, true));
    Method method = Decoder.class.getDeclaredMethod("decode_fast",
        Decoder.Request.class, Cdict.class, Cdict.class, Hunspell.class,
        PersonalizationStore.class, LanguageModel.class, boolean.class);
    method.setAccessible(true);
    return (Decoder.Result)method.invoke(new Decoder(), request, _dictionary,
        null, _hunspell, PersonalizationStore.empty(), _languageModel, false);
  }

  private Decoder.Result decodeBoundary(long generation, String typed)
      throws Exception
  {
    TouchTrace touches = new TouchTrace();
    int count = typed.codePointCount(0, typed.length());
    for (int i = 0; i < count; i++)
      touches.add(TouchTrace.entry(100f, 100f, 100f, 100f, 20f, 20f));
    Decoder.Request request = new Decoder.Request(
        new Decoder.RequestKey(1, generation, generation, 1, 1, 1, 1),
        typed, touches.snapshot(), _geometry,
        new Decoder.DecoderConfig(true, true, true, true));
    Method method = Decoder.class.getDeclaredMethod("decode_boundary",
        Decoder.Request.class, Cdict.class, Cdict.class, Hunspell.class,
        PersonalizationStore.class, LanguageModel.class, boolean.class);
    method.setAccessible(true);
    return (Decoder.Result)method.invoke(new Decoder(), request, _dictionary,
        null, _hunspell, PersonalizationStore.empty(), _languageModel, false);
  }




  private static Decoder.Result awaitResult(SharedDecoder decoder,
      Decoder.RequestKey key, boolean complete)
      throws Exception
  {
    long deadline = System.nanoTime() + 5_000_000_000L;
    do
    {
      Decoder.Result result = decoder.current_result(key);
      if (result != null && result.autocorrectionComplete == complete)
        return result;
      Thread.sleep(2L);
    }
    while (System.nanoTime() < deadline);
    fail("Timed out waiting for " + (complete ? "complete" : "provisional")
        + " result for generation " + key.requestGeneration);
    return null;
  }



  private String previousWord(PersonalizationStore profile) throws Exception
  {
    Method method = PersonalizationStore.class.getDeclaredMethod(
        "previous_word");
    method.setAccessible(true);
    return (String)method.invoke(profile);
  }

  private int languageModelWeight(String previous, String next)
      throws Exception
  {
    Method method = LanguageModel.class.getDeclaredMethod(
        "weight", String.class, String.class);
    method.setAccessible(true);
    return (Integer)method.invoke(_languageModel, previous, next);
  }

  private static String decodedSurface(Decoder.Result result, String typed)
  {
    return result.autocorrection == null ? normalized(typed)
      : normalized(result.autocorrection.surface);
  }

  private static String candidateSummary(Decoder.Result result)
  {
    StringBuilder out = new StringBuilder();
    out.append("literal=").append(result.literal.surface)
      .append("{recognized=").append(result.literal.recognized)
      .append(", learned=").append(result.literal.learned)
      .append(", mask=").append(result.literal.sourceMask)
      .append(", total=").append(result.literal.totalQ8)
      .append(", spatial=").append(result.literal.spatialQ8).append("}");
    out.append(", autocorrection=")
      .append(result.autocorrection == null
        ? "null" : result.autocorrection.surface);
    out.append(", words=[");
    Decoder.Candidate[] words = result.words();
    for (int i = 0; i < words.length; i++)
    {
      if (i > 0)
        out.append(", ");
      Decoder.Candidate candidate = words[i];
      out.append(candidate.surface)
        .append("{recognized=").append(candidate.recognized)
        .append(", mask=").append(candidate.sourceMask)
        .append(", editCount=").append(candidate.editCount)
        .append(", editMask=").append(candidate.editMask)
        .append(", frequency=").append(candidate.cdictFrequency)
        .append(", providerRank=").append(candidate.providerRank)
        .append(", total=").append(candidate.totalQ8)
        .append(", spatial=").append(candidate.spatialQ8).append("}");
    }
    return out.append(']').toString();
  }

  private static void appendMismatch(StringBuilder out, JSONObject sentence,
      JSONObject token, String expected, String actual)
      throws Exception
  {
    out.append(sentence.getString("id")).append(' ')
      .append(token.getString("previous")).append(" [")
      .append(token.getString("typed")).append("] ")
      .append(token.getString("next")).append(": expected ")
      .append(expected).append(", got ").append(actual).append('\n');
  }

  private String readOracle() throws Exception
  {
    return readAsset("swiftkey_autocorrection_oracle.json");
  }

  private String readAsset(String name) throws Exception
  {
    InputStream input = InstrumentationRegistry.getInstrumentation()
      .getContext().getAssets().open(name);
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
