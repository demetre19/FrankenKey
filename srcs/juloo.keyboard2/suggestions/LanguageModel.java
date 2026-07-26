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

/** Immutable bounded previous-word priors loaded on the decoder worker. */
public final class LanguageModel
{
  private static final int MAX_BIGRAMS = 4096;
  private static final int MAX_WEIGHT = 15;
  private static final LanguageModel EMPTY = new LanguageModel(
      Collections.<String, Map<String, Integer>>emptyMap());

  private final Map<String, Map<String, Integer>> _bigrams;

  private LanguageModel(Map<String, Map<String, Integer>> bigrams)
  {
    _bigrams = bigrams;
  }

  static LanguageModel empty()
  {
    return EMPTY;
  }

  public static LanguageModel load(File file) throws IOException
  {
    if (file == null)
      return EMPTY;
    HashMap<String, Map<String, Integer>> rows =
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
        if (fields.length != 3)
          throw malformed(file, lineNumber, "expected three tab-separated fields");
        String previous = Decoder.normalize(fields[0]);
        String next = Decoder.normalize(fields[1]);
        if (!fields[0].equals(previous) || !fields[1].equals(next)
            || previous.length() == 0 || next.length() == 0)
          throw malformed(file, lineNumber, "words must be normalized and non-empty");
        int weight;
        try { weight = Integer.parseInt(fields[2]); }
        catch (NumberFormatException e)
        {
          throw malformed(file, lineNumber, "weight must be an integer");
        }
        if (weight < 1 || weight > MAX_WEIGHT)
          throw malformed(file, lineNumber, "weight must be between 1 and 15");
        Map<String, Integer> following = rows.get(previous);
        if (following == null)
        {
          following = new HashMap<String, Integer>();
          rows.put(previous, following);
        }
        if (following.put(next, weight) != null)
          throw malformed(file, lineNumber, "duplicate bigram");
        if (++count > MAX_BIGRAMS)
          throw malformed(file, lineNumber, "model exceeds 4096 bigrams");
      }
    }
    finally { input.close(); }

    HashMap<String, Map<String, Integer>> immutable =
      new HashMap<String, Map<String, Integer>>(rows.size());
    for (Map.Entry<String, Map<String, Integer>> row : rows.entrySet())
      immutable.put(row.getKey(), Collections.unmodifiableMap(row.getValue()));
    return new LanguageModel(Collections.unmodifiableMap(immutable));
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

  Map<String, Integer> following(String previous)
  {
    if (previous == null)
      return Collections.emptyMap();
    Map<String, Integer> following = _bigrams.get(previous);
    return following == null ? Collections.<String, Integer>emptyMap()
      : following;
  }

  private static IOException malformed(File file, int line, String reason)
  {
    return new IOException("Malformed language model " + file + ":" + line
        + ": " + reason);
  }
}
