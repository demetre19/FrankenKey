package juloo.keyboard2.autocorrect;

import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import juloo.cdict.Cdict;
import juloo.keyboard2.KeyboardData;
import juloo.keyboard2.TouchTrace;
import juloo.keyboard2.suggestions.TouchGeometry;

/** Commit-boundary autocorrect using Hunspell suggestions. */
public final class Autocorrect
{
  public static String correction(Hunspell speller, String word)
  {
    return correction(speller, null, word, null, null);
  }

  public static String correction(Hunspell speller, String word,
      TouchTrace touchTrace, KeyboardData layout)
  {
    return correction(speller, null, word, touchTrace, layout);
  }

  public static String correction(Hunspell speller, Cdict dict, String word,
      TouchTrace touchTrace, KeyboardData layout)
  {
    if (word == null || word.length() < 2)
      return null;
    if (!is_plain_word(word))
      return null;
    String lower = word.toLowerCase(Locale.ROOT);
    if ((speller != null && speller.spell(lower))
        || (dict != null && dict.find(lower).found))
      return null;
    List<String> candidates = new ArrayList<String>();
    if (speller != null)
      add_candidates(candidates, speller.suggest(lower, 6));
    if (dict != null)
    {
      int[] dist = dict.distance(lower, 1, 6);
      for (int i = 0; i < dist.length; i++)
        add_candidate(candidates, dict.word(dist[i]));
    }
    String best = null;
    double best_score = Double.MAX_VALUE;
    for (String suggestion : candidates)
      if (is_high_confidence(lower, suggestion))
      {
        if (touchTrace == null || touchTrace.size() == 0)
          return match_case(word, suggestion);
        double score = TouchGeometry.score(lower, suggestion, touchTrace, layout);
        if (score < best_score)
        {
          best = suggestion;
          best_score = score;
        }
      }
    return best == null ? null : match_case(word, best);
  }

  private static void add_candidates(List<String> candidates, String[] words)
  {
    for (String word : words)
      add_candidate(candidates, word);
  }

  private static void add_candidate(List<String> candidates, String word)
  {
    if (word == null)
      return;
    for (String candidate : candidates)
      if (candidate.equalsIgnoreCase(word))
        return;
    candidates.add(word);
  }

  static boolean is_high_confidence(String word, String suggestion)
  {
    if (word == null || suggestion == null || word.length() < 3 || suggestion.length() < 3)
      return false;
    suggestion = suggestion.toLowerCase(Locale.ROOT);
    if (!is_plain_word(word) || !is_plain_word(suggestion) || word.equals(suggestion))
      return false;
    int distance = damerau_levenshtein_at_most(word, suggestion, 2);
    if (distance <= 0 || distance > 2)
      return false;
    if (distance == 1)
      return true;
    return word.length() >= 4 && is_adjacent_transposition(word, suggestion);
  }

  static String match_case(String typed, String suggestion)
  {
    if (typed.length() == 0 || suggestion.length() == 0)
      return suggestion;
    if (typed.equals(typed.toUpperCase(Locale.ROOT)))
      return suggestion.toUpperCase(Locale.ROOT);
    if (Character.isUpperCase(typed.charAt(0)))
      return suggestion.substring(0, 1).toUpperCase(Locale.ROOT)
        + suggestion.substring(1);
    return suggestion;
  }

  private static boolean is_plain_word(String word)
  {
    for (int i = 0; i < word.length(); i++)
      if (!Character.isLetter(word.charAt(i)))
        return false;
    return true;
  }

  private static boolean is_adjacent_transposition(String a, String b)
  {
    if (a.length() != b.length())
      return false;
    int first = -1;
    int second = -1;
    for (int i = 0; i < a.length(); i++)
      if (a.charAt(i) != b.charAt(i))
      {
        if (first == -1)
          first = i;
        else if (second == -1)
          second = i;
        else
          return false;
      }
    return first >= 0 && second == first + 1
      && a.charAt(first) == b.charAt(second)
      && a.charAt(second) == b.charAt(first);
  }

  private static int damerau_levenshtein_at_most(String a, String b, int max)
  {
    int[][] d = new int[a.length() + 1][b.length() + 1];
    for (int i = 0; i <= a.length(); i++)
      d[i][0] = i;
    for (int j = 0; j <= b.length(); j++)
      d[0][j] = j;
    for (int i = 1; i <= a.length(); i++)
    {
      int best = max + 1;
      for (int j = 1; j <= b.length(); j++)
      {
        int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
        int value = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1),
            d[i - 1][j - 1] + cost);
        if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2)
            && a.charAt(i - 2) == b.charAt(j - 1))
          value = Math.min(value, d[i - 2][j - 2] + 1);
        d[i][j] = value;
        if (value < best)
          best = value;
      }
      if (best > max)
        return max + 1;
    }
    return d[a.length()][b.length()];
  }
}
