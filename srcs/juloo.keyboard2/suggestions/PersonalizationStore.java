package juloo.keyboard2.suggestions;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bounded on-device model for words, next-word pairs, and typo corrections. */
public final class PersonalizationStore
{
  public static final class ScoredWord
  {
    public final String word;
    public final int count;

    private ScoredWord(String word_, int count_)
    {
      word = word_;
      count = count_;
    }
  }

  public static final class ScoredCorrection
  {
    public final String target;
    public final int exactCount;
    public final int relatedCount;

    private ScoredCorrection(String target_, int exactCount_, int relatedCount_)
    {
      target = target_;
      exactCount = exactCount_;
      relatedCount = relatedCount_;
    }
  }

  private static final class TopWords
  {
    private final String[] _values;
    private final int[] _offsets;
    private final int[] _counts;
    private int _size = 0;

    TopWords(int limit)
    {
      _values = new String[limit];
      _offsets = new int[limit];
      _counts = new int[limit];
    }

    void offer(String value, int offset, int count)
    {
      int index = 0;
      while (index < _size && !comes_before(value, offset, count,
            _values[index], _offsets[index], _counts[index]))
        index++;
      if (index >= _values.length)
        return;
      int last = Math.min(_size, _values.length - 1);
      for (int i = last; i > index; i--)
      {
        _values[i] = _values[i - 1];
        _offsets[i] = _offsets[i - 1];
        _counts[i] = _counts[i - 1];
      }
      _values[index] = value;
      _offsets[index] = offset;
      _counts[index] = count;
      if (_size < _values.length)
        _size++;
    }

    List<ScoredWord> scored_words()
    {
      List<ScoredWord> out = new ArrayList<ScoredWord>(_size);
      for (int i = 0; i < _size; i++)
      {
        String word = _offsets[i] == 0 ? _values[i]
          : _values[i].substring(_offsets[i]);
        out.add(new ScoredWord(word, _counts[i]));
      }
      return out;
    }
  }

  private static final class MutableCorrection
  {
    int exactCount;
    int relatedCount;
  }

  private static final class TopCorrections
  {
    private final String[] _targets;
    private final int[] _exactCounts;
    private final int[] _relatedCounts;
    private int _size = 0;

    TopCorrections(int limit)
    {
      _targets = new String[limit];
      _exactCounts = new int[limit];
      _relatedCounts = new int[limit];
    }

    void offer(String target, int exactCount, int relatedCount)
    {
      int index = 0;
      while (index < _size && !correction_comes_before(target, exactCount,
            relatedCount, _targets[index], _exactCounts[index],
            _relatedCounts[index]))
        index++;
      if (index >= _targets.length)
        return;
      int last = Math.min(_size, _targets.length - 1);
      for (int i = last; i > index; i--)
      {
        _targets[i] = _targets[i - 1];
        _exactCounts[i] = _exactCounts[i - 1];
        _relatedCounts[i] = _relatedCounts[i - 1];
      }
      _targets[index] = target;
      _exactCounts[index] = exactCount;
      _relatedCounts[index] = relatedCount;
      if (_size < _targets.length)
        _size++;
    }

    List<ScoredCorrection> scored_corrections()
    {
      List<ScoredCorrection> out = new ArrayList<ScoredCorrection>(_size);
      for (int i = 0; i < _size; i++)
        out.add(new ScoredCorrection(_targets[i], _exactCounts[i],
              _relatedCounts[i]));
      return out;
    }
  }

  private static final class CorrectionPair
  {
    final String source;
    final String target;
    final int[] sourceCodePoints;
    final int[] targetCodePoints;

    CorrectionPair(String source_, String target_)
    {
      source = source_;
      target = target_;
      sourceCodePoints = source.codePoints().toArray();
      targetCodePoints = target.codePoints().toArray();
    }

    @Override
    public boolean equals(Object other)
    {
      if (this == other)
        return true;
      if (!(other instanceof CorrectionPair))
        return false;
      CorrectionPair pair = (CorrectionPair)other;
      return source.equals(pair.source) && target.equals(pair.target);
    }

    @Override
    public int hashCode()
    {
      return source.hashCode() * 31 + target.hashCode();
    }
  }


  public PersonalizationStore(SharedPreferences prefs)
  {
    _prefs = prefs;
    _word_counts = prefs == null ? new HashMap<String, Integer>()
      : load_counts(PREF_WORDS);
    _bigram_counts = prefs == null ? new HashMap<String, Integer>()
      : load_counts(PREF_BIGRAMS);
    _correction_counts = prefs == null
      ? new HashMap<CorrectionPair, Integer>() : load_corrections();
  }

  public static PersonalizationStore empty()
  {
    return new PersonalizationStore(null);
  }

  public void record_word(String word)
  {
    record_commit(word, null);
  }

  /**
   * Record one accepted word and its optional typo source in one preference
   * transaction. Invalid correction pairs never prevent normal word/context
   * learning.
   */
  public void record_commit(String word, String correctedFrom)
  {
    word = normalize(word);
    if (!is_learnable(word))
      return;
    boolean changed = increment(_word_counts, word);
    if (_last_word != null)
      changed |= increment(_bigram_counts, _last_word + " " + word);
    if (!word.equals(_last_word))
      changed = true;
    _last_word = word;

    correctedFrom = normalize(correctedFrom);
    if (is_plausible_correction(correctedFrom, word))
      changed |= increment_correction(new CorrectionPair(correctedFrom, word));

    if (changed)
    {
      _generation++;
      save();
    }
  }

  /**
   * Record only editor-verified typo evidence. Unlike a normal accepted commit,
   * this does not teach an unrecognized target as a standalone word or phrase.
   */
  public void record_correction(String source, String target)
  {
    source = normalize(source);
    target = normalize(target);
    if (!is_plausible_correction(source, target)
        || !increment_correction(new CorrectionPair(source, target)))
      return;
    _generation++;
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
    boolean changed = _word_counts.remove(word) != null;
    changed |= remove_bigrams_containing(word);
    changed |= remove_corrections_involving(word);
    if (word.equals(_last_word))
    {
      _last_word = null;
      changed = true;
    }
    if (!changed)
      return false;
    _generation++;
    save();
    return true;
  }

  int word_count(String normalizedWord)
  {
    Integer count = _word_counts.get(normalize(normalizedWord));
    return count == null ? 0 : count;
  }

  int bigram_count(String previousWord, String normalizedWord)
  {
    previousWord = normalize(previousWord);
    normalizedWord = normalize(normalizedWord);
    if (previousWord.length() == 0 || normalizedWord.length() == 0)
      return 0;
    Integer count = _bigram_counts.get(previousWord + " " + normalizedWord);
    return count == null ? 0 : count;
  }

  int correction_count(String source, String target)
  {
    Integer count = _correction_counts.get(new CorrectionPair(normalize(source),
          normalize(target)));
    return count == null ? 0 : count;
  }

  public List<String> suggest_words(String prefix, int count)
  {
    return words_only(suggest_words_with_counts(prefix, count));
  }

  List<ScoredWord> suggest_words_with_counts(String prefix, int count)
  {
    if (count <= 0)
      return new ArrayList<ScoredWord>();
    prefix = normalize(prefix);
    TopWords matches = new TopWords(Math.min(count, _word_counts.size()));
    for (Map.Entry<String, Integer> entry : _word_counts.entrySet())
      if (entry.getKey().startsWith(prefix) && !entry.getKey().equals(prefix))
        matches.offer(entry.getKey(), 0, entry.getValue());
    return matches.scored_words();
  }

  public List<String> suggest_next_words(int count)
  {
    return words_only(suggest_next_words_with_counts(count));
  }

  List<ScoredWord> suggest_next_words_with_counts(int count)
  {
    if (_last_word == null || count <= 0)
      return new ArrayList<ScoredWord>();
    String prefix = _last_word + " ";
    TopWords matches = new TopWords(Math.min(count, _bigram_counts.size()));
    for (Map.Entry<String, Integer> entry : _bigram_counts.entrySet())
      if (entry.getKey().startsWith(prefix))
        matches.offer(entry.getKey(), prefix.length(), entry.getValue());
    return matches.scored_words();
  }

  List<ScoredCorrection> suggest_corrections_with_counts(String source,
      Decoder.Geometry geometry, int count)
  {
    if (count <= 0 || _correction_counts.isEmpty())
      return new ArrayList<ScoredCorrection>();
    source = normalize(source);
    if (!is_learnable(source))
      return new ArrayList<ScoredCorrection>();
    int[] sourceCodePoints = source.codePoints().toArray();

    Map<String, MutableCorrection> totals =
      new HashMap<String, MutableCorrection>();
    for (Map.Entry<CorrectionPair, Integer> entry
        : _correction_counts.entrySet())
    {
      CorrectionPair pair = entry.getKey();
      boolean exact = pair.source.equals(source);
      if (!exact && !is_related_source(sourceCodePoints, pair, geometry))
        continue;
      MutableCorrection total = totals.get(pair.target);
      if (total == null)
      {
        total = new MutableCorrection();
        totals.put(pair.target, total);
      }
      if (exact)
        total.exactCount = saturating_count_add(total.exactCount,
            entry.getValue());
      else
        total.relatedCount = saturating_count_add(total.relatedCount,
            entry.getValue());
    }

    TopCorrections top = new TopCorrections(Math.min(count, totals.size()));
    for (Map.Entry<String, MutableCorrection> entry : totals.entrySet())
    {
      MutableCorrection total = entry.getValue();
      top.offer(entry.getKey(), total.exactCount, total.relatedCount);
    }
    return top.scored_corrections();
  }

  long generation()
  {
    return _generation;
  }

  String previous_word()
  {
    return _last_word;
  }

  public void reset_context()
  {
    if (_last_word == null)
      return;
    _last_word = null;
    _generation++;
  }

  public void clear()
  {
    boolean changed = !_word_counts.isEmpty() || !_bigram_counts.isEmpty()
      || !_correction_counts.isEmpty() || _last_word != null
      || has_data(_prefs);
    _word_counts.clear();
    _bigram_counts.clear();
    _correction_counts.clear();
    _last_word = null;
    clear(_prefs);
    if (changed)
      _generation++;
  }

  public static void clear(SharedPreferences prefs)
  {
    if (prefs == null)
      return;
    prefs.edit()
      .remove(PREF_WORDS)
      .remove(PREF_BIGRAMS)
      .remove(PREF_CORRECTIONS)
      .apply();
  }

  public static boolean has_data(SharedPreferences prefs)
  {
    return prefs != null
      && (prefs.contains(PREF_WORDS) || prefs.contains(PREF_BIGRAMS)
        || prefs.contains(PREF_CORRECTIONS));
  }

  public static boolean is_learnable(String word)
  {
    if (word == null)
      return false;
    int count = word.codePointCount(0, word.length());
    if (count < 2 || count > 32)
      return false;
    for (int offset = 0; offset < word.length();)
    {
      int codePoint = word.codePointAt(offset);
      if (!Character.isLetter(codePoint))
        return false;
      offset += Character.charCount(codePoint);
    }
    return true;
  }

  public static boolean is_plausible_correction(String source, String target)
  {
    source = normalize(source);
    target = normalize(target);
    if (!is_learnable(source) || !is_learnable(target)
        || source.equals(target))
      return false;
    int[] sourceCodePoints = source.codePoints().toArray();
    int[] targetCodePoints = target.codePoints().toArray();
    if (Math.abs(sourceCodePoints.length - targetCodePoints.length)
        > MAX_EXACT_CORRECTION_EDITS)
      return false;
    return within_exact_correction_distance(sourceCodePoints,
        targetCodePoints);
  }

  /**
   * Bounded optimal-string-alignment distance for exact editor-verified pairs.
   * Geometry remains exclusive to weaker related-source generalization.
   */
  private static boolean within_exact_correction_distance(int[] source,
      int[] target)
  {
    final int limit = MAX_EXACT_CORRECTION_EDITS;
    final int overLimit = limit + 1;
    int[] previousPrevious = new int[target.length + 1];
    int[] previous = new int[target.length + 1];
    int[] current = new int[target.length + 1];
    for (int j = 0; j <= target.length; j++)
    {
      previousPrevious[j] = overLimit;
      previous[j] = j <= limit ? j : overLimit;
      current[j] = overLimit;
    }

    for (int i = 1; i <= source.length; i++)
    {
      int from = Math.max(1, i - limit);
      int to = Math.min(target.length, i + limit);
      current[0] = i <= limit ? i : overLimit;
      if (from > 1)
        current[from - 1] = overLimit;
      if (to < target.length)
        current[to + 1] = overLimit;

      for (int j = from; j <= to; j++)
      {
        int substitution = previous[j - 1]
          + (source[i - 1] == target[j - 1] ? 0 : 1);
        int value = Math.min(substitution,
            Math.min(previous[j] + 1, current[j - 1] + 1));
        if (i > 1 && j > 1
            && source[i - 1] == target[j - 2]
            && source[i - 2] == target[j - 1])
          value = Math.min(value, previousPrevious[j - 2] + 1);
        current[j] = Math.min(value, overLimit);
      }

      int[] swap = previousPrevious;
      previousPrevious = previous;
      previous = current;
      current = swap;
    }
    return previous[target.length] <= limit;
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
        String key = normalize(entry.substring(0, sep));
        int wordSep = key.indexOf(' ');
        boolean valid = PREF_WORDS.equals(pref) ? is_learnable(key)
          : PREF_BIGRAMS.equals(pref) && wordSep > 0
            && wordSep == key.lastIndexOf(' ')
            && is_learnable(key.substring(0, wordSep))
            && is_learnable(key.substring(wordSep + 1));
        int count = Integer.parseInt(entry.substring(sep + 1));
        Integer previous = out.get(key);
        if (valid && count > 0 && count <= MAX_COUNT
            && (previous == null || count > previous))
          out.put(key, Integer.valueOf(count));
      }
      catch (NumberFormatException e) {}
    }
    return out;
  }

  private Map<CorrectionPair, Integer> load_corrections()
  {
    Map<CorrectionPair, Integer> out =
      new HashMap<CorrectionPair, Integer>();
    Set<String> entries = _prefs.getStringSet(PREF_CORRECTIONS, null);
    if (entries == null)
      return out;
    for (String entry : entries)
    {
      if (entry == null)
        continue;
      int first = entry.indexOf('\t');
      int second = first < 0 ? -1 : entry.indexOf('\t', first + 1);
      if (first <= 0 || second <= first + 1
          || entry.indexOf('\t', second + 1) >= 0)
        continue;
      String source = normalize(entry.substring(0, first));
      String target = normalize(entry.substring(first + 1, second));
      if (!is_plausible_correction(source, target))
        continue;
      try
      {
        int count = Integer.parseInt(entry.substring(second + 1));
        if (count <= 0 || count > MAX_CORRECTION_COUNT)
          continue;
        CorrectionPair pair = new CorrectionPair(source, target);
        if (out.containsKey(pair))
        {
          if (count > out.get(pair))
            out.put(pair, Integer.valueOf(count));
        }
        else if (out.size() < MAX_CORRECTION_PAIRS)
          out.put(pair, Integer.valueOf(count));
        else
        {
          CorrectionPair weakest = weakest_correction(out);
          int weakestCount = out.get(weakest);
          if (count > weakestCount || (count == weakestCount
                && compare_pairs(pair, weakest) < 0))
          {
            out.remove(weakest);
            out.put(pair, Integer.valueOf(count));
          }
        }
      }
      catch (NumberFormatException e) {}
    }
    return out;
  }

  private void save()
  {
    if (_prefs == null)
      return;
    SharedPreferences.Editor editor = _prefs.edit();
    save_counts(editor, PREF_WORDS, _word_counts);
    save_counts(editor, PREF_BIGRAMS, _bigram_counts);
    if (_correction_counts.isEmpty())
      editor.remove(PREF_CORRECTIONS);
    else
      editor.putStringSet(PREF_CORRECTIONS,
          encode_corrections(_correction_counts));
    editor.apply();
  }

  private static void save_counts(SharedPreferences.Editor editor,
      String preference, Map<String, Integer> counts)
  {
    if (counts.isEmpty())
      editor.remove(preference);
    else
      editor.putStringSet(preference, encode_counts(counts));
  }

  private static Set<String> encode_counts(Map<String, Integer> counts)
  {
    Set<String> out = new HashSet<String>();
    for (Map.Entry<String, Integer> entry : counts.entrySet())
      out.add(entry.getKey() + "\t" + entry.getValue());
    return out;
  }

  private static Set<String> encode_corrections(
      Map<CorrectionPair, Integer> counts)
  {
    Set<String> out = new HashSet<String>();
    for (Map.Entry<CorrectionPair, Integer> entry : counts.entrySet())
      out.add(entry.getKey().source + "\t" + entry.getKey().target + "\t"
          + entry.getValue());
    return out;
  }

  private static boolean increment(Map<String, Integer> counts, String key)
  {
    Integer prev = counts.get(key);
    if (prev != null && prev >= MAX_COUNT)
      return false;
    counts.put(key, prev == null ? 1 : prev + 1);
    return true;
  }

  private boolean increment_correction(CorrectionPair pair)
  {
    Integer previous = _correction_counts.get(pair);
    if (previous != null)
    {
      if (previous >= MAX_CORRECTION_COUNT)
        return false;
      _correction_counts.put(pair, previous + 1);
      return true;
    }
    if (_correction_counts.size() >= MAX_CORRECTION_PAIRS)
      _correction_counts.remove(weakest_correction());
    _correction_counts.put(pair, 1);
    return true;
  }

  private CorrectionPair weakest_correction()
  {
    return weakest_correction(_correction_counts);
  }

  private static CorrectionPair weakest_correction(
      Map<CorrectionPair, Integer> counts)
  {
    CorrectionPair weakest = null;
    int weakestCount = Integer.MAX_VALUE;
    for (Map.Entry<CorrectionPair, Integer> entry : counts.entrySet())
    {
      int count = entry.getValue();
      if (count < weakestCount || (count == weakestCount
            && (weakest == null
              || compare_pairs(entry.getKey(), weakest) > 0)))
      {
        weakest = entry.getKey();
        weakestCount = count;
      }
    }
    return weakest;
  }

  private boolean remove_bigrams_containing(String word)
  {
    boolean changed = false;
    List<String> toRemove = new ArrayList<String>();
    for (String bigram : _bigram_counts.keySet())
    {
      int sep = bigram.indexOf(' ');
      if (sep <= 0)
        continue;
      if (bigram.substring(0, sep).equals(word)
          || bigram.substring(sep + 1).equals(word))
        toRemove.add(bigram);
    }
    for (String bigram : toRemove)
      changed |= _bigram_counts.remove(bigram) != null;
    return changed;
  }

  private boolean remove_corrections_involving(String word)
  {
    boolean changed = false;
    List<CorrectionPair> toRemove = new ArrayList<CorrectionPair>();
    for (CorrectionPair pair : _correction_counts.keySet())
      if (pair.source.equals(word) || pair.target.equals(word))
        toRemove.add(pair);
    for (CorrectionPair pair : toRemove)
      changed |= _correction_counts.remove(pair) != null;
    return changed;
  }

  private static boolean is_related_source(int[] sourceCodePoints,
      CorrectionPair pair, Decoder.Geometry geometry)
  {
    if (geometry == null)
      return false;
    int current = single_substitution_index(sourceCodePoints,
        pair.targetCodePoints);
    if (current < 0)
      return false;
    int learned = single_substitution_index(pair.sourceCodePoints,
        pair.targetCodePoints);
    return current == learned
      && geometry.fixed_substitution_cost_q8(sourceCodePoints[current],
          pair.targetCodePoints[current]) <= RELATED_SUBSTITUTION_COST_Q8
      && geometry.fixed_substitution_cost_q8(
          pair.sourceCodePoints[learned], pair.targetCodePoints[learned])
        <= RELATED_SUBSTITUTION_COST_Q8;
  }

  private static int single_substitution_index(int[] source, int[] target)
  {
    if (source.length != target.length)
      return -1;
    int mismatch = -1;
    for (int i = 0; i < source.length; i++)
    {
      if (source[i] == target[i])
        continue;
      if (mismatch >= 0)
        return -1;
      mismatch = i;
    }
    return mismatch;
  }

  private static int saturating_count_add(int left, int right)
  {
    return (int)Math.min((long)left + right, MAX_COUNT);
  }

  private static String normalize(String word)
  {
    return Decoder.normalize_correction_text(word);
  }

  private static boolean comes_before(String value, int offset, int count,
      String otherValue, int otherOffset, int otherCount)
  {
    if (count != otherCount)
      return count > otherCount;
    return compare_suffixes(value, offset, otherValue, otherOffset) < 0;
  }

  private static boolean correction_comes_before(String target, int exactCount,
      int relatedCount, String otherTarget, int otherExactCount,
      int otherRelatedCount)
  {
    boolean exact = exactCount > 0;
    boolean otherExact = otherExactCount > 0;
    if (exact != otherExact)
      return exact;
    int weight = correction_weight(exactCount, relatedCount);
    int otherWeight = correction_weight(otherExactCount, otherRelatedCount);
    if (weight != otherWeight)
      return weight > otherWeight;
    if (exactCount != otherExactCount)
      return exactCount > otherExactCount;
    if (relatedCount != otherRelatedCount)
      return relatedCount > otherRelatedCount;
    return compare_suffixes(target, 0, otherTarget, 0) < 0;
  }

  private static int correction_weight(int exactCount, int relatedCount)
  {
    return Math.min(CORRECTION_WEIGHT_CAP,
        exactCount * 2 + relatedCount);
  }

  private static int compare_pairs(CorrectionPair left, CorrectionPair right)
  {
    int source = compare_suffixes(left.source, 0, right.source, 0);
    return source != 0 ? source
      : compare_suffixes(left.target, 0, right.target, 0);
  }

  private static int compare_suffixes(String value, int offset,
      String otherValue, int otherOffset)
  {
    int length = value.length() - offset;
    int otherLength = otherValue.length() - otherOffset;
    int commonLength = Math.min(length, otherLength);
    for (int i = 0; i < commonLength; i++)
    {
      int difference = value.charAt(offset + i)
        - otherValue.charAt(otherOffset + i);
      if (difference != 0)
        return difference;
    }
    return length - otherLength;
  }

  private static List<String> words_only(List<ScoredWord> words)
  {
    List<String> out = new ArrayList<String>(words.size());
    for (ScoredWord word : words)
      out.add(word.word);
    return out;
  }

  private final SharedPreferences _prefs;
  private final Map<String, Integer> _word_counts;
  private final Map<String, Integer> _bigram_counts;
  private final Map<CorrectionPair, Integer> _correction_counts;
  private String _last_word = null;
  private long _generation = 0;

  static final int MAX_EXACT_CORRECTION_EDITS = 2;
  private static final int MAX_COUNT = 10000;
  private static final int MAX_CORRECTION_COUNT = 15;
  private static final int MAX_CORRECTION_PAIRS = 512;
  private static final int CORRECTION_WEIGHT_CAP = 8;
  private static final int RELATED_SUBSTITUTION_COST_Q8 = 5 * 256;
  public static final String PREF_WORDS = "typing_model_words";
  public static final String PREF_BIGRAMS = "typing_model_bigrams";
  public static final String PREF_CORRECTIONS =
    "typing_model_corrections_v1";
}
