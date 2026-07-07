package juloo.keyboard2.suggestions;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Small on-device typing model for learned words and next-word pairs. */
public final class PersonalizationStore
{
  public PersonalizationStore(SharedPreferences prefs)
  {
    _prefs = prefs;
    _word_counts = prefs == null ? new HashMap<String, Integer>()
      : load_counts(PREF_WORDS);
    _bigram_counts = prefs == null ? new HashMap<String, Integer>()
      : load_counts(PREF_BIGRAMS);
  }

  public static PersonalizationStore empty()
  {
    return new PersonalizationStore(null);
  }

  public void record_word(String word)
  {
    word = normalize(word);
    if (!is_learnable(word))
      return;
    increment(_word_counts, word);
    if (_last_word != null)
      increment(_bigram_counts, _last_word + " " + word);
    _last_word = word;
    save();
  }

  public boolean is_learned(String word)
  {
    word = normalize(word);
    return _word_counts.containsKey(word);
  }

  public boolean unlearn_word(String word)
  {
    word = normalize(word);
    if (!_word_counts.containsKey(word))
      return false;
    _word_counts.remove(word);
    remove_bigrams_containing(word);
    if (word.equals(_last_word))
      _last_word = null;
    save();
    return true;
  }


  public List<String> suggest_words(String prefix, int count)
  {
    prefix = normalize(prefix);
    List<ScoredWord> matches = new ArrayList<ScoredWord>();
    for (Map.Entry<String, Integer> entry : _word_counts.entrySet())
      if (entry.getKey().startsWith(prefix) && !entry.getKey().equals(prefix))
        matches.add(new ScoredWord(entry.getKey(), entry.getValue()));
    return top_words(matches, count);
  }

  public List<String> suggest_next_words(int count)
  {
    if (_last_word == null)
      return Collections.emptyList();
    String prefix = _last_word + " ";
    List<ScoredWord> matches = new ArrayList<ScoredWord>();
    for (Map.Entry<String, Integer> entry : _bigram_counts.entrySet())
      if (entry.getKey().startsWith(prefix))
        matches.add(new ScoredWord(entry.getKey().substring(prefix.length()),
              entry.getValue()));
    return top_words(matches, count);
  }

  public void reset_context()
  {
    _last_word = null;
  }

  public void clear()
  {
    _word_counts.clear();
    _bigram_counts.clear();
    _last_word = null;
    clear(_prefs);
  }

  public static void clear(SharedPreferences prefs)
  {
    if (prefs == null)
      return;
    prefs.edit()
      .remove(PREF_WORDS)
      .remove(PREF_BIGRAMS)
      .apply();
  }

  public static boolean has_data(SharedPreferences prefs)
  {
    return prefs != null
      && (prefs.contains(PREF_WORDS) || prefs.contains(PREF_BIGRAMS));
  }

  public static boolean is_learnable(String word)
  {
    if (word == null || word.length() < 2 || word.length() > 32)
      return false;
    for (int i = 0; i < word.length(); i++)
      if (!Character.isLetter(word.charAt(i)))
        return false;
    return true;
  }

  private Map<String, Integer> load_counts(String pref)
  {
    Map<String, Integer> out = new HashMap<String, Integer>();
    Set<String> entries = _prefs.getStringSet(pref, null);
    if (entries == null)
      return out;
    for (String entry : entries)
    {
      int sep = entry.lastIndexOf('\t');
      if (sep <= 0)
        continue;
      try
      {
        out.put(entry.substring(0, sep), Integer.valueOf(entry.substring(sep + 1)));
      }
      catch (NumberFormatException e) {}
    }
    return out;
  }

  private void save()
  {
    if (_prefs == null)
      return;
    _prefs.edit()
      .putStringSet(PREF_WORDS, encode_counts(_word_counts))
      .putStringSet(PREF_BIGRAMS, encode_counts(_bigram_counts))
      .apply();
  }

  private static Set<String> encode_counts(Map<String, Integer> counts)
  {
    Set<String> out = new HashSet<String>();
    for (Map.Entry<String, Integer> entry : counts.entrySet())
      out.add(entry.getKey() + "\t" + entry.getValue());
    return out;
  }

  private static void increment(Map<String, Integer> counts, String key)
  {
    Integer prev = counts.get(key);
    counts.put(key, prev == null ? 1 : Math.min(prev + 1, MAX_COUNT));
  }

  private void remove_bigrams_containing(String word)
  {
    List<String> to_remove = new ArrayList<String>();
    for (String bigram : _bigram_counts.keySet())
    {
      int sep = bigram.indexOf(' ');
      if (sep <= 0)
        continue;
      if (bigram.substring(0, sep).equals(word)
          || bigram.substring(sep + 1).equals(word))
        to_remove.add(bigram);
    }
    for (String bigram : to_remove)
      _bigram_counts.remove(bigram);
  }


  private static String normalize(String word)
  {
    return word == null ? "" : word.toLowerCase(Locale.ROOT);
  }

  private static List<String> top_words(List<ScoredWord> words, int count)
  {
    Collections.sort(words, new Comparator<ScoredWord>()
        {
          public int compare(ScoredWord a, ScoredWord b)
          {
            if (a.score != b.score)
              return b.score - a.score;
            return a.word.compareTo(b.word);
          }
        });
    List<String> out = new ArrayList<String>();
    for (int i = 0; i < words.size() && i < count; i++)
      out.add(words.get(i).word);
    return out;
  }

  private static final class ScoredWord
  {
    final String word;
    final int score;

    ScoredWord(String word_, int score_)
    {
      word = word_;
      score = score_;
    }
  }

  private final SharedPreferences _prefs;
  private final Map<String, Integer> _word_counts;
  private final Map<String, Integer> _bigram_counts;
  private String _last_word = null;

  private static final int MAX_COUNT = 10000;
  public static final String PREF_WORDS = "typing_model_words";
  public static final String PREF_BIGRAMS = "typing_model_bigrams";
}
