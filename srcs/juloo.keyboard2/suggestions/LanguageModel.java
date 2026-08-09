package juloo.keyboard2.suggestions;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Immutable bounded one- and two-word priors loaded on the decoder worker. */
public final class LanguageModel
{
  private static final int MAX_NGRAMS = 4096;
  private static final int MAX_WEIGHT = 15;
  private static final String CONTEXT_SEPARATOR = "\u0000";
  private static final LanguageModel EMPTY = new LanguageModel(
      Collections.<String, Map<String, Integer>>emptyMap(),
      Collections.<String, Map<String, Integer>>emptyMap());

  private final Map<String, Map<String, Integer>> _bigrams;
  private final Map<String, Map<String, Integer>> _trigrams;

  private LanguageModel(Map<String, Map<String, Integer>> bigrams,
      Map<String, Map<String, Integer>> trigrams)
  {
    _bigrams = bigrams;
    _trigrams = trigrams;
  }

  static LanguageModel empty()
  {
    return EMPTY;
  }

  public static LanguageModel load(File file) throws IOException
  {
    if (file == null)
      return EMPTY;
    HashMap<String, Map<String, Integer>> bigrams =
      new HashMap<String, Map<String, Integer>>();
    HashMap<String, Map<String, Integer>> trigrams =
      new HashMap<String, Map<String, Integer>>();
    int count = 0;
    BufferedReader input = new BufferedReader(new InputStreamReader(
          new FileInputStream(file), StandardCharsets.UTF_8));
    try
    {
      String line;
      int lineNumber = 0;
      while ((line = input.readLine()) != null)
      {
        lineNumber++;
        if (line.length() == 0 || line.charAt(0) == '#')
          continue;
        String[] fields = line.split("\\t", -1);
        if (fields.length != 3 && fields.length != 4)
          throw malformed(file, lineNumber,
              "expected three or four tab-separated fields");
        int weightIndex = fields.length - 1;
        int weight;
        try { weight = Integer.parseInt(fields[weightIndex]); }
        catch (NumberFormatException e)
        {
          throw malformed(file, lineNumber, "weight must be an integer");
        }
        if (weight < 1 || weight > MAX_WEIGHT)
          throw malformed(file, lineNumber, "weight must be between 1 and 15");

        String context;
        String next;
        HashMap<String, Map<String, Integer>> rows;
        String duplicate;
        if (fields.length == 3)
        {
          context = normalized_word(file, lineNumber, fields[0]);
          next = normalized_word(file, lineNumber, fields[1]);
          rows = bigrams;
          duplicate = "duplicate bigram";
        }
        else
        {
          String prior = normalized_word(file, lineNumber, fields[0]);
          String previous = normalized_word(file, lineNumber, fields[1]);
          context = trigram_context(prior, previous);
          next = normalized_word(file, lineNumber, fields[2]);
          rows = trigrams;
          duplicate = "duplicate trigram";
        }
        Map<String, Integer> following = rows.get(context);
        if (following == null)
        {
          following = new HashMap<String, Integer>();
          rows.put(context, following);
        }
        if (following.put(next, weight) != null)
          throw malformed(file, lineNumber, duplicate);
        if (++count > MAX_NGRAMS)
          throw malformed(file, lineNumber, "model exceeds 4096 ngrams");
      }
    }
    finally { input.close(); }

    return new LanguageModel(immutable_rows(bigrams),
        immutable_rows(trigrams));
  }

  int weight(String previous, String next)
  {
    if (previous == null || next == null)
      return 0;
    Map<String, Integer> following = _bigrams.get(previous);
    if (following == null)
      return 0;
    Integer weight = following.get(next);
    return weight == null ? 0 : weight;
  }

  int weight(String prior, String previous, String next)
  {
    int bigram = weight(previous, next);
    if (prior == null || previous == null || next == null)
      return bigram;
    Map<String, Integer> following =
      _trigrams.get(trigram_context(prior, previous));
    if (following == null)
      return bigram;
    Integer trigram = following.get(next);
    return trigram == null ? bigram : Math.max(bigram, trigram);
  }

  Map<String, Integer> following(String previous)
  {
    if (previous == null)
      return Collections.emptyMap();
    Map<String, Integer> following = _bigrams.get(previous);
    return following == null ? Collections.<String, Integer>emptyMap()
      : following;
  }

  Map<String, Integer> following(String prior, String previous)
  {
    if (prior == null || previous == null)
      return Collections.emptyMap();
    Map<String, Integer> following =
      _trigrams.get(trigram_context(prior, previous));
    return following == null ? Collections.<String, Integer>emptyMap()
      : following;
  }

  private static String normalized_word(File file, int line, String word)
      throws IOException
  {
    String normalized = Decoder.normalize(word);
    if (!word.equals(normalized) || normalized.length() == 0)
      throw malformed(file, line, "words must be normalized and non-empty");
    return normalized;
  }

  private static String trigram_context(String prior, String previous)
  {
    return prior + CONTEXT_SEPARATOR + previous;
  }

  private static Map<String, Map<String, Integer>> immutable_rows(
      Map<String, Map<String, Integer>> rows)
  {
    HashMap<String, Map<String, Integer>> immutable =
      new HashMap<String, Map<String, Integer>>(rows.size());
    for (Map.Entry<String, Map<String, Integer>> row : rows.entrySet())
      immutable.put(row.getKey(), Collections.unmodifiableMap(row.getValue()));
    return Collections.unmodifiableMap(immutable);
  }

  private static IOException malformed(File file, int line, String reason)
  {
    return new IOException("Malformed language model " + file + ":" + line
        + ": " + reason);
  }
}
