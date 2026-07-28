package juloo.keyboard2.suggestions;

import android.content.SharedPreferences;
import juloo.keyboard2.TouchTrace;
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

  public static final class ScoredContextualCorrection
  {
    public final String target;
    public final int count;

    private ScoredContextualCorrection(String target_, int count_)
    {
      target = target_;
      count = count_;
    }
  }

  public static final class Stats
  {
    public final int learnedWords;
    public final int nextWordPairs;
    public final int correctionPatterns;
    public final int calibratedTouches;

    private Stats(int learnedWords_, int nextWordPairs_,
        int correctionPatterns_, int calibratedTouches_)
    {
      learnedWords = learnedWords_;
      nextWordPairs = nextWordPairs_;
      correctionPatterns = correctionPatterns_;
      calibratedTouches = calibratedTouches_;
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

  private static final class ContextualCorrection
  {
    final String previous;
    final String source;
    final String target;

    ContextualCorrection(String previous_, String source_, String target_)
    {
      previous = previous_;
      source = source_;
      target = target_;
    }

    @Override
    public boolean equals(Object other)
    {
      if (this == other)
        return true;
      if (!(other instanceof ContextualCorrection))
        return false;
      ContextualCorrection correction = (ContextualCorrection)other;
      return previous.equals(correction.previous)
        && source.equals(correction.source)
        && target.equals(correction.target);
    }

    @Override
    public int hashCode()
    {
      return (previous.hashCode() * 31 + source.hashCode()) * 31
        + target.hashCode();
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
    _contextual_correction_counts = prefs == null
      ? new HashMap<ContextualCorrection, Integer>()
      : load_contextual_corrections();
    if (prefs != null)
    {
      _touch_samples = Math.max(0, Math.min(MAX_TOUCH_SAMPLES,
            prefs.getInt(PREF_TOUCH_SAMPLES, 0)));
      _touch_offset_x = clamp_touch_offset(
          prefs.getFloat(PREF_TOUCH_OFFSET_X, 0f));
      _touch_offset_y = clamp_touch_offset(
          prefs.getFloat(PREF_TOUCH_OFFSET_Y, 0f));
    }
  }

  public static PersonalizationStore empty()
  {
    return new PersonalizationStore(null);
  }

  public void record_word(String word)
  {
    record_commit(word, null, null, null);
  }

  /**
   * Record one accepted word and its optional typo source in one preference
   * transaction. Invalid correction pairs never prevent normal word/context
   * learning.
   */
  public void record_commit(String word, String correctedFrom)
  {
    record_commit(word, correctedFrom, null, null);
  }

  void record_commit(String word, String correctedFrom, String typedWord,
      TouchTrace.Snapshot touches)
  {
    String normalizedWord = normalize(word);
    if (!is_learnable(normalizedWord))
      return;
    String normalizedCorrection = normalize(correctedFrom);
    String previousWord = _last_word;
    boolean changed = increment(_word_counts, normalizedWord);
    if (previousWord != null)
      changed |= increment(_bigram_counts, previousWord + " " + normalizedWord);
    if (!normalizedWord.equals(previousWord))
      changed = true;
    _last_word = normalizedWord;

    if (is_plausible_correction(normalizedCorrection, normalizedWord))
      changed |= increment_correction(new CorrectionPair(
            normalizedCorrection, normalizedWord));
    if (previousWord != null
        && is_plausible_contextual_correction(normalizedCorrection,
          normalizedWord))
      changed |= increment_contextual_correction(new ContextualCorrection(
            previousWord, normalizedCorrection, normalizedWord));
    changed |= record_touch_calibration(typedWord, normalizedWord,
        normalizedCorrection, touches);

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
    boolean changed = false;
    if (is_plausible_correction(source, target))
      changed |= increment_correction(new CorrectionPair(source, target));
    if (_last_word != null
        && is_plausible_contextual_correction(source, target))
      changed |= increment_contextual_correction(new ContextualCorrection(
            _last_word, source, target));
    if (!changed)
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
    changed |= remove_contextual_corrections_involving(word);
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

  int contextual_correction_count(String previous, String source,
      String target)
  {
    ContextualCorrection correction = new ContextualCorrection(
        normalize(previous), normalize(source), normalize(target));
    Integer count = _contextual_correction_counts.get(correction);
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

  List<ScoredContextualCorrection>
      suggest_contextual_corrections_with_counts(String source, int count)
  {
    if (_last_word == null || count <= 0
        || _contextual_correction_counts.isEmpty())
      return new ArrayList<ScoredContextualCorrection>();
    source = normalize(source);
    if (!is_learnable(source))
      return new ArrayList<ScoredContextualCorrection>();
    TopWords matches = new TopWords(Math.min(count,
          _contextual_correction_counts.size()));
    for (Map.Entry<ContextualCorrection, Integer> entry
        : _contextual_correction_counts.entrySet())
    {
      ContextualCorrection correction = entry.getKey();
      if (correction.previous.equals(_last_word)
          && correction.source.equals(source))
        matches.offer(correction.target, 0, entry.getValue());
    }
    List<ScoredWord> scored = matches.scored_words();
    List<ScoredContextualCorrection> out =
      new ArrayList<ScoredContextualCorrection>(scored.size());
    for (ScoredWord word : scored)
      out.add(new ScoredContextualCorrection(word.word, word.count));
    return out;
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

  float touch_offset_x()
  {
    return _touch_samples >= MIN_TOUCH_SAMPLES ? _touch_offset_x : 0f;
  }

  float touch_offset_y()
  {
    return _touch_samples >= MIN_TOUCH_SAMPLES ? _touch_offset_y : 0f;
  }

  public static Stats stats(SharedPreferences prefs)
  {
    PersonalizationStore store = new PersonalizationStore(prefs);
    return new Stats(store._word_counts.size(), store._bigram_counts.size(),
        store._correction_counts.size()
          + store._contextual_correction_counts.size(),
        store._touch_samples);
  }

  private boolean record_touch_calibration(String typedWord,
      String normalizedWord, String normalizedCorrection,
      TouchTrace.Snapshot touches)
  {
    if (typedWord == null || touches == null
        || normalizedCorrection.length() != 0
        || !normalize(typedWord).equals(normalizedWord)
        || typedWord.codePointCount(0, typedWord.length()) != touches.size())
      return false;
    boolean changed = false;
    for (int i = 0; i < touches.size(); ++i)
    {
      TouchTrace.Entry touch = touches.get(i);
      if (touch == null || touch.keyWidth <= 0f || touch.keyHeight <= 0f)
        continue;
      float x = (touch.touchX - touch.keyCenterX) / touch.keyWidth;
      float y = (touch.touchY - touch.keyCenterY) / touch.keyHeight;
      if (!finite(x) || !finite(y) || Math.abs(x) > 0.6f
          || Math.abs(y) > 0.6f)
        continue;
      int denominator = Math.min(_touch_samples + 1, 256);
      _touch_offset_x += (clamp_touch_offset(x) - _touch_offset_x)
        / denominator;
      _touch_offset_y += (clamp_touch_offset(y) - _touch_offset_y)
        / denominator;
      if (_touch_samples < MAX_TOUCH_SAMPLES)
        ++_touch_samples;
      changed = true;
    }
    return changed;
  }

  private static float clamp_touch_offset(float value)
  {
    if (!finite(value))
      return 0f;
    return Math.max(-MAX_TOUCH_OFFSET, Math.min(MAX_TOUCH_OFFSET, value));
  }

  private static boolean finite(float value)
  {
    return !Float.isNaN(value) && !Float.isInfinite(value);
  }

  public void clear()
  {
    boolean changed = !_word_counts.isEmpty() || !_bigram_counts.isEmpty()
      || !_correction_counts.isEmpty()
      || !_contextual_correction_counts.isEmpty() || _last_word != null
      || _touch_samples != 0 || has_data(_prefs);
    _word_counts.clear();
    _bigram_counts.clear();
    _correction_counts.clear();
    _contextual_correction_counts.clear();
    _last_word = null;
    _touch_samples = 0;
    _touch_offset_x = 0f;
    _touch_offset_y = 0f;
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
      .remove(PREF_CONTEXTUAL_CORRECTIONS)
      .remove(PREF_TOUCH_SAMPLES)
      .remove(PREF_TOUCH_OFFSET_X)
      .remove(PREF_TOUCH_OFFSET_Y)
      .apply();
  }

  public static boolean has_data(SharedPreferences prefs)
  {
    return prefs != null
      && (prefs.contains(PREF_WORDS) || prefs.contains(PREF_BIGRAMS)
        || prefs.contains(PREF_CORRECTIONS)
        || prefs.contains(PREF_CONTEXTUAL_CORRECTIONS)
        || prefs.getInt(PREF_TOUCH_SAMPLES, 0) > 0);
  }

  public static boolean is_learnable(String word)
  {
    if (word == null)
      return false;
    int count = word.codePointCount(0, word.length());
    if (count < 2 || count > 32)
      return false;
    boolean apostrophe = false;
    for (int offset = 0; offset < word.length();)
    {
      int codePoint = word.codePointAt(offset);
      int next = offset + Character.charCount(codePoint);
      if (Character.isLetter(codePoint))
      {
        offset = next;
        continue;
      }
      if ((codePoint != '\'' && codePoint != '’') || apostrophe
          || offset == 0 || next == word.length()
          || !Character.isLetter(word.codePointBefore(offset))
          || !Character.isLetter(word.codePointAt(next)))
        return false;
      apostrophe = true;
      offset = next;
    }
    return true;
  }

  public static boolean is_plausible_correction(String source, String target)
  {
    return is_plausible_correction(source, target,
        MAX_EXACT_CORRECTION_EDITS);
  }

  static boolean is_plausible_contextual_correction(String source,
      String target)
  {
    return is_plausible_correction(source, target,
        MAX_CONTEXTUAL_CORRECTION_EDITS);
  }

  private static boolean is_plausible_correction(String source, String target,
      int editLimit)
  {
    source = normalize(source);
    target = normalize(target);
    if (!is_learnable(source) || !is_learnable(target)
        || source.equals(target))
      return false;
    int[] sourceCodePoints = source.codePoints().toArray();
    int[] targetCodePoints = target.codePoints().toArray();
    if (Math.abs(sourceCodePoints.length - targetCodePoints.length) > editLimit)
      return false;
    return within_exact_correction_distance(sourceCodePoints,
        targetCodePoints, editLimit);
  }

  /**
   * Bounded optimal-string-alignment distance for exact editor-verified pairs.
   * Geometry remains exclusive to weaker related-source generalization.
   */
  private static boolean within_exact_correction_distance(int[] source,
      int[] target, int limit)
  {
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

  private Map<ContextualCorrection, Integer> load_contextual_corrections()
  {
    Map<ContextualCorrection, Integer> out =
      new HashMap<ContextualCorrection, Integer>();
    Set<String> entries = _prefs.getStringSet(
        PREF_CONTEXTUAL_CORRECTIONS, null);
    if (entries == null)
      return out;
    for (String entry : entries)
    {
      if (entry == null)
        continue;
      int first = entry.indexOf('\t');
      int second = first < 0 ? -1 : entry.indexOf('\t', first + 1);
      int third = second < 0 ? -1 : entry.indexOf('\t', second + 1);
      if (first <= 0 || second <= first + 1 || third <= second + 1
          || entry.indexOf('\t', third + 1) >= 0)
        continue;
      String previous = normalize(entry.substring(0, first));
      String source = normalize(entry.substring(first + 1, second));
      String target = normalize(entry.substring(second + 1, third));
      if (!is_learnable(previous)
          || !is_plausible_contextual_correction(source, target))
        continue;
      try
      {
        int count = Integer.parseInt(entry.substring(third + 1));
        if (count <= 0 || count > MAX_CORRECTION_COUNT)
          continue;
        ContextualCorrection correction =
          new ContextualCorrection(previous, source, target);
        Integer existing = out.get(correction);
        if (existing != null)
        {
          if (count > existing)
            out.put(correction, Integer.valueOf(count));
        }
        else if (out.size() < MAX_CONTEXTUAL_CORRECTIONS)
          out.put(correction, Integer.valueOf(count));
        else
        {
          ContextualCorrection weakest =
            weakest_contextual_correction(out);
          int weakestCount = out.get(weakest);
          if (count > weakestCount || (count == weakestCount
                && compare_contextual_corrections(correction, weakest) < 0))
          {
            out.remove(weakest);
            out.put(correction, Integer.valueOf(count));
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
    if (_contextual_correction_counts.isEmpty())
      editor.remove(PREF_CONTEXTUAL_CORRECTIONS);
    else
      editor.putStringSet(PREF_CONTEXTUAL_CORRECTIONS,
          encode_contextual_corrections(_contextual_correction_counts));
    if (_touch_samples == 0)
    {
      editor.remove(PREF_TOUCH_SAMPLES);
      editor.remove(PREF_TOUCH_OFFSET_X);
      editor.remove(PREF_TOUCH_OFFSET_Y);
    }
    else
    {
      editor.putInt(PREF_TOUCH_SAMPLES, _touch_samples);
      editor.putFloat(PREF_TOUCH_OFFSET_X, _touch_offset_x);
      editor.putFloat(PREF_TOUCH_OFFSET_Y, _touch_offset_y);
    }
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

  private static Set<String> encode_contextual_corrections(
      Map<ContextualCorrection, Integer> counts)
  {
    Set<String> out = new HashSet<String>();
    for (Map.Entry<ContextualCorrection, Integer> entry : counts.entrySet())
    {
      ContextualCorrection correction = entry.getKey();
      out.add(correction.previous + "\t" + correction.source + "\t"
          + correction.target + "\t" + entry.getValue());
    }
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

  private boolean increment_contextual_correction(
      ContextualCorrection correction)
  {
    Integer previous = _contextual_correction_counts.get(correction);
    if (previous != null)
    {
      if (previous >= MAX_CORRECTION_COUNT)
        return false;
      _contextual_correction_counts.put(correction, previous + 1);
      return true;
    }
    if (_contextual_correction_counts.size() >= MAX_CONTEXTUAL_CORRECTIONS)
      _contextual_correction_counts.remove(
          weakest_contextual_correction(_contextual_correction_counts));
    _contextual_correction_counts.put(correction, 1);
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

  private static ContextualCorrection weakest_contextual_correction(
      Map<ContextualCorrection, Integer> counts)
  {
    ContextualCorrection weakest = null;
    int weakestCount = Integer.MAX_VALUE;
    for (Map.Entry<ContextualCorrection, Integer> entry : counts.entrySet())
    {
      int count = entry.getValue();
      if (count < weakestCount || (count == weakestCount
            && (weakest == null || compare_contextual_corrections(
                entry.getKey(), weakest) > 0)))
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

  private boolean remove_contextual_corrections_involving(String word)
  {
    boolean changed = false;
    List<ContextualCorrection> toRemove =
      new ArrayList<ContextualCorrection>();
    for (ContextualCorrection correction
        : _contextual_correction_counts.keySet())
      if (correction.previous.equals(word) || correction.source.equals(word)
          || correction.target.equals(word))
        toRemove.add(correction);
    for (ContextualCorrection correction : toRemove)
      changed |= _contextual_correction_counts.remove(correction) != null;
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

  private static int compare_contextual_corrections(
      ContextualCorrection left, ContextualCorrection right)
  {
    int previous = compare_suffixes(left.previous, 0, right.previous, 0);
    if (previous != 0)
      return previous;
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
  private final Map<ContextualCorrection, Integer>
    _contextual_correction_counts;
  private String _last_word = null;
  private int _touch_samples = 0;
  private float _touch_offset_x = 0f;
  private float _touch_offset_y = 0f;
  private long _generation = 0;

  static final int MAX_EXACT_CORRECTION_EDITS = 2;
  static final int MAX_CONTEXTUAL_CORRECTION_EDITS = 3;
  private static final int MAX_COUNT = 10000;
  private static final int MAX_CORRECTION_COUNT = 15;
  private static final int MAX_CORRECTION_PAIRS = 512;
  private static final int MAX_CONTEXTUAL_CORRECTIONS = 512;
  private static final int CORRECTION_WEIGHT_CAP = 8;
  private static final int RELATED_SUBSTITUTION_COST_Q8 = 5 * 256;
  private static final int MIN_TOUCH_SAMPLES = 20;
  private static final int MAX_TOUCH_SAMPLES = 4096;
  private static final float MAX_TOUCH_OFFSET = 0.2f;
  public static final String PREF_WORDS = "typing_model_words";
  public static final String PREF_BIGRAMS = "typing_model_bigrams";
  public static final String PREF_CORRECTIONS =
    "typing_model_corrections_v1";
  public static final String PREF_CONTEXTUAL_CORRECTIONS =
    "typing_model_contextual_corrections_v1";
  public static final String PREF_TOUCH_SAMPLES =
    "typing_model_touch_samples_v1";
  public static final String PREF_TOUCH_OFFSET_X =
    "typing_model_touch_offset_x_v1";
  public static final String PREF_TOUCH_OFFSET_Y =
    "typing_model_touch_offset_y_v1";
}
