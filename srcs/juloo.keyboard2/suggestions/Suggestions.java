package juloo.keyboard2.suggestions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import juloo.cdict.Cdict;
import juloo.keyboard2.dict.Dictionaries;
import juloo.keyboard2.Config;
import juloo.keyboard2.autocorrect.Hunspell;
import juloo.keyboard2.ComposeKey;
import juloo.keyboard2.ComposeKeyData;
import juloo.keyboard2.TouchTrace;

/** Keep track of the word being typed and provide suggestions for
    [CandidatesView]. */
public final class Suggestions
{
  Callback _callback;
  Config _config;
  boolean _enabled;

  /** Current suggestions. The best suggestion is at index [0]. */
  public String[] suggestions = new String[MAX_COUNT];
  public Source[] sources = new Source[MAX_COUNT];
  /** Number of suggestions at the beginning of the [suggestions] array that
      are not [null]. */
  public int count = 0;
  public String emoji_suggestion = null;
  public LearnFeedback learn_feedback = LearnFeedback.NONE;
  public String learn_feedback_word = null;
  /** Number of suggestions in [suggestions]. */
  public static final int MAX_COUNT = 3;

  public Suggestions(Callback c, Config conf)
  {
    _callback = c;
    _config = conf;
  }

  public void started()
  {
    _enabled = _config.suggestions_enabled
      && _config.editor_config.should_show_candidates_view;
    clear();
  }

  public void currently_typed_word(String word, TouchTrace touchTrace)
  {
    learn_feedback = LearnFeedback.NONE;
    learn_feedback_word = null;
    if (!_enabled)
    {
      clear();
      _callback.set_suggestions(this);
      return;
    }
    if (word.length() == 0)
      query_next_words();
    else if (word.length() < 2)
      clear();
    else
      query_suggestions(word, touchTrace);
    _callback.set_suggestions(this);
  }

  public void show_learn_feedback(String word, LearnFeedback feedback)
  {
    learn_feedback = feedback;
    learn_feedback_word = word;
    if (_callback != null)
      _callback.set_suggestions(this);
  }

  void clear()
  {
    count = 0;
    for (int i = 0; i < MAX_COUNT; i++)
    {
      suggestions[i] = null;
      sources[i] = Source.NONE;
    }
    emoji_suggestion = null;
    learn_feedback = LearnFeedback.NONE;
    learn_feedback_word = null;
  }

  int query_suggestions(String word, TouchTrace touchTrace)
  {
    Cdict dict = _config.current_dictionary;
    boolean first_char_upper = Character.isUpperCase(word.charAt(0));
    String query = apply_substitutions(word);
    List<String> candidates = new ArrayList<String>();
    Map<String, Source> candidate_sources = new HashMap<String, Source>();
    boolean exact_known_word = false;
    add_hunspell_candidates(candidates, candidate_sources, query);
    if (dict != null)
    {
      Cdict.Result r = dict.find(query);
      exact_known_word = r.found;
      if (r.found)
        add_candidate(candidates, candidate_sources, dict.word(r.index), Source.DICTIONARY);
      int[] suffixes = dict.suffixes(r, MAX_COUNT * 2);
      // Disable distance search for small words
      int[] dist = (query.length() < 3) ? NO_RESULTS :
        dict.distance(query, 1, MAX_COUNT * 2);
      for (int j = 0; j < MAX_COUNT * 2; j++)
      {
        if (suffixes.length > j)
          add_candidate(candidates, candidate_sources, dict.word(suffixes[j]), Source.DICTIONARY);
        if (dist.length > j)
          add_candidate(candidates, candidate_sources, dict.word(dist[j]), Source.DICTIONARY);
      }
    }
    List<String> learned_words = _config.personalization.suggest_words(query, MAX_COUNT);
    if (learned_words.isEmpty()
        && should_show_entered_text_candidate(query, exact_known_word))
      add_candidate(candidates, candidate_sources, query, Source.ENTERED_TEXT);
    for (String learned : learned_words)
      add_candidate(candidates, candidate_sources, learned, Source.LEARNED);
    TouchGeometry.rerank(query, candidates, touchTrace, _config.current_layout_geometry);
    demote_entered_text_candidate(candidates, candidate_sources);
    count = Math.min(candidates.size(), MAX_COUNT);
    for (int i = 0; i < count; i++)
    {
      String candidate = candidates.get(i);
      suggestions[i] = first_char_upper ? capitalize(candidate) : candidate;
      sources[i] = source_for(candidate_sources, candidate);
    }
    for (int i = count; i < MAX_COUNT; i++)
    {
      suggestions[i] = null;
      sources[i] = Source.NONE;
    }
    emoji_suggestion = query_emoji(query); // word with substitutions applied
    return count;
  }

  int query_next_words()
  {
    List<String> candidates = _config.personalization.suggest_next_words(MAX_COUNT);
    count = Math.min(candidates.size(), MAX_COUNT);
    for (int i = 0; i < count; i++)
    {
      suggestions[i] = candidates.get(i);
      sources[i] = Source.NEXT_WORD;
    }
    for (int i = count; i < MAX_COUNT; i++)
    {
      suggestions[i] = null;
      sources[i] = Source.NONE;
    }
    emoji_suggestion = null;
    return count;
  }

  void add_hunspell_candidates(List<String> candidates,
      Map<String, Source> sources, String query)
  {
    Hunspell speller = _config.current_hunspell;
    if (speller == null || speller.spell(query))
      return;
    String[] hunspell_suggestions = speller.suggest(query, MAX_COUNT * 3);
    for (String suggestion : hunspell_suggestions)
      add_candidate(candidates, sources, suggestion, Source.HUNSPELL);
  }

  void add_candidate(List<String> candidates, Map<String, Source> sources,
      String word, Source source)
  {
    if (word == null || word.length() == 0)
      return;
    for (String candidate : candidates)
      if (candidate.equalsIgnoreCase(word))
        return;
    candidates.add(word);
    sources.put(source_key(word), source);
  }

  Source source_for(Map<String, Source> sources, String word)
  {
    Source source = sources.get(source_key(word));
    return source == null ? Source.NONE : source;
  }

  boolean should_show_entered_text_candidate(String query, boolean exact_known_word)
  {
    if (!PersonalizationStore.is_learnable(query))
      return false;
    if (_config.personalization.is_learned(query))
      return true;
    if (exact_known_word)
      return false;
    Hunspell speller = _config.current_hunspell;
    return speller == null || !speller.spell(query);
  }

  void demote_entered_text_candidate(List<String> candidates,
      Map<String, Source> sources)
  {
    if (candidates.size() < 2)
      return;
    String first = candidates.get(0);
    if (source_for(sources, first) != Source.ENTERED_TEXT)
      return;
    candidates.remove(0);
    candidates.add(first);
  }

  static String source_key(String word)
  {
    return word.toLowerCase(Locale.ROOT);
  }

  String capitalize(String word)
  {
    return word.substring(0, 1).toUpperCase() + word.substring(1);
  }

  void capitalize_results()
  {
    for (int i = 0; i < count; i++)
      suggestions[i] = capitalize(suggestions[i]);
  }

  String query_emoji(String word)
  {
    Cdict dict = _config.emoji_dictionary;
    // Disable emoji suggestion for short words
    if (dict == null || word.length() < 3)
      return null;
    Cdict.Result r = dict.find(word);
    if (r.found)
      return dict.word(r.index);
    int[] s = dict.suffixes(r, 1);
    if (s.length > 0)
      return dict.word(s[0]);
    return null;
  }

  /** Apply the same substitutions that were used when building the
      dictionaries to find word aliases. This catches missing diacritics for
      example. */
  String apply_substitutions(String w)
  {
    StringBuilder b = new StringBuilder(w);
    int len = w.length();
    for (int i = 0; i < len; i++)
    {
      char r =
        ComposeKey.transform_char(ComposeKeyData.substitutions, b.charAt(i));
      if (r != 0) b.setCharAt(i, r);
    }
    return b.toString();
  }

  static final int[] NO_RESULTS = new int[0];

  public static enum Source
  {
    NONE,
    HUNSPELL,
    DICTIONARY,
    LEARNED,
    ENTERED_TEXT,
    LEARN_ACTION,
    LEARNED_FEEDBACK,
    UNLEARNED_FEEDBACK,
    NEXT_WORD,
    EMOJI
  }

  public static enum LearnFeedback
  {
    NONE,
    LEARNED,
    FORGOT
  }

  public static interface Callback
  {
    public void set_suggestions(Suggestions suggestions);
  }
}
