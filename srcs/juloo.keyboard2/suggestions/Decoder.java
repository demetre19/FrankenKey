package juloo.keyboard2.suggestions;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import juloo.cdict.Cdict;
import juloo.keyboard2.ComposeKey;
import juloo.keyboard2.ComposeKeyData;
import juloo.keyboard2.CurrentlyTypedWord;
import juloo.keyboard2.KeyboardData;
import juloo.keyboard2.KeyValue;
import juloo.keyboard2.TouchTrace;
import juloo.keyboard2.autocorrect.Hunspell;

/** Pure synchronous candidate decoder. Resource lifetime and threading are
    owned by SharedDecoder; a request never reads mutable Config state. */
public final class Decoder
{
  public static final int MAX_WORD_CODEPOINTS = 48;
  public static final int MAX_NATIVE_RESULTS = 16;
  public static final int MAX_MERGED_CANDIDATES = 80;
  public static final int MAX_VISIBLE_WORDS = 6;

  public static final int SOURCE_LITERAL = 1;
  public static final int SOURCE_CDICT_EXACT = 1 << 1;
  public static final int SOURCE_CDICT_SPATIAL = 1 << 2;
  public static final int SOURCE_CDICT_PREFIX = 1 << 3;
  public static final int SOURCE_HUNSPELL = 1 << 4;
  public static final int SOURCE_PERSONAL = 1 << 5;
  public static final int SOURCE_CONTEXT = 1 << 6;
  public static final int SOURCE_CORRECTION = 1 << 7;
  public static final int SOURCE_CONTRACTION = 1 << 8;
  public static final int SOURCE_CONTEXTUAL_CORRECTION = 1 << 9;
  public static final int SOURCE_HUNSPELL_PRIMARY = 1 << 10;
  public static final int SOURCE_REPLACEMENT = 1 << 11;
  /** Packaged dictionary shortcut: the typed form is a known misspelling. */
  public static final int SOURCE_WHITELIST = 1 << 12;

  public static final int EDIT_SUBSTITUTION = 1;
  public static final int EDIT_OMISSION = 1 << 1;
  public static final int EDIT_EXTRA_TAP = 1 << 2;
  public static final int EDIT_TRANSPOSITION = 1 << 3;

  private static final int MAX_CANDIDATE_CODEPOINTS = 50;
  private static final int MAX_PREFIX_RESULTS = 6;
  private static final int MAX_HUNSPELL_RESULTS = 9;
  private static final int MAX_PERSONAL_RESULTS = 6;
  private static final int MAX_CORRECTION_RESULTS = 6;

  private static final int Q8 = 256;
  // Edit costs follow AOSP LatinIME's typing weights, scaled so that an
  // omission costs 6 Q8: insertion 0.72, same-key insertion 0.55, omission
  // 0.47, doubled-letter omission 0.35 and a 0.15 proximity base. Two
  // deliberate departures, tuned on the androidTest oracles: transposition
  // stays cheaper than LatinIME's 0.56 because fast typing swaps letters
  // often, and the per-squared-key distance cost is steeper so an accurate
  // tap on the neighbouring key (mistaks) prefers a missed letter
  // (mistakes) over a substitution (mistake).
  private static final int OMISSION_COST_Q8 = 6 * Q8;
  private static final int SAME_LETTER_OMISSION_COST_Q8 = 1135;
  private static final int EXTRA_TAP_COST_Q8 = 2384;
  private static final int SAME_LETTER_EXTRA_TAP_COST_Q8 = 1811;
  private static final int TRANSPOSITION_COST_Q8 = 1024;
  private static final double PROXIMITY_BASE_COST = 1.9;
  private static final double PROXIMITY_DISTANCE_COST = 4.0;
  private static final int UNKNOWN_SUBSTITUTION_COST_Q8 = 12 * Q8;
  private static final int MAX_SUBSTITUTION_COST_Q8 = 64 * Q8;
  private static final int BEAM_COST_Q8 = 48 * Q8;
  private static final int AUTOCORRECT_LITERAL_MARGIN_Q8 = 2 * Q8;
  private static final int AUTOCORRECT_RUNNER_MARGIN_Q8 = Q8 / 2;
  private static final int AUTOCORRECT_SHORT_WORD_SPATIAL_GAP_Q8 = 2 * Q8;
  private static final int DECISIVE_SAME_LENGTH_MARGIN_Q8 = 2 * Q8;
  private static final int LENGTH_DELTA_RANKING_PENALTY_Q8 = 4 * Q8;
  /** A same-length repair yields to a one-edit length repair only within
      this ranking gap; word frequency already separates the two. */
  private static final int COMPETING_LENGTH_REPAIR_GAP_Q8 = Q8;
  private static final int NEARBY_SUBSTITUTION_COST_Q8 = 8 * Q8;
  private static final int UNKNOWN_LITERAL_LENGTH_THRESHOLD = 5;
  private static final int UNKNOWN_LITERAL_LENGTH_PENALTY_Q8 = 3 * Q8 / 2;
  private static final int NEARBY_SPATIAL_EXPANSIONS = 4096;
  private static final int BOUNDARY_PREVIEW_MIN_CDICT_FREQUENCY = 15;
  private static final int BOUNDARY_PREVIEW_LITERAL_MARGIN_Q8 = 8 * Q8;
  private static final int BOUNDARY_PREVIEW_RUNNER_MARGIN_Q8 = Q8;
  private static final int SCORE_LIMIT = 0x3fffffff;
  /** Fixed-point score per source frequency step (0..255 wordlist scale). */
  private static final int UNIGRAM_FREQUENCY_WEIGHT = 14;
  /** Fixed-point score per 4-bit Cdict level when no unigram table exists. */
  private static final int CDICT_LEVEL_WEIGHT = 128;
  /** Unigram lead a transposition needs over a lowercase-typed acronym. */
  private static final int ACRONYM_REPAIR_FREQUENCY_GAP = 50;
  /** Context weights for the previous word's ranked dictionary continuations;
      they stay below the decisive-context threshold of 8 so continuations
      alone never override a valid literal. */
  private static final int[] CONTEXT_FOLLOWER_WEIGHTS = { 3, 2, 1 };
  /** Next-word strip order for those continuations on a doubled 1..15 prior
      scale (14.5, 13.5, 12.5): strong short-word priors (14+) still lead,
      the bulk of weaker ones follow; trigram context is added first. */
  private static final int[] NEXT_WORD_FOLLOWER_WEIGHTS = { 29, 27, 25 };
  private static final int MAX_CORRECTION_WEIGHT = 8;
  private static final int CORRECTION_BONUS_Q8 = 3 * Q8;
  private static final int PROTECTED_LITERAL_EXACT_EVENTS = 4;
  private static final int SHORT_ALL_CAPS_CODEPOINTS = 4;
  private static final String[] CONTRACTION_SUFFIXES =
    { "t", "s", "d", "m", "re", "ve", "ll" };
  private static final String[] NEGATIVE_CONTRACTION_BASES =
    { "are", "could", "did", "does", "had", "has", "have", "is", "might",
      "must", "need", "should", "was", "were", "would" };


  /** Common contractions that MUST autocorrect even when the hunspell
      dictionary lacks the surface (ive→I've, were→we're, well→we'll, ill→I'll,
      cant→can't, dont→don't, isnt→isn't, wasnt→wasn't, wont→won't,
      couldnt→couldn't, shouldnt→shouldn't, wouldnt→wouldn't, didnt→didn't,
      doesnt→doesn't, havent→haven't, hasnt→hasn't, hadnt→hadn't, im→I'm). */
  private static final String[][] CANONICAL_CONTRACTIONS = {
    { "ive", "I've" }, { "im", "I'm" }, { "ill", "I'll" },
    { "were", "we're" }, { "well", "we'll" }, { "cant", "can't" },
    { "dont", "don't" }, { "isnt", "isn't" }, { "wasnt", "wasn't" },
    { "wont", "won't" }, { "didnt", "didn't" }, { "doesnt", "doesn't" },
    { "couldnt", "couldn't" }, { "shouldnt", "shouldn't" },
    { "wouldnt", "wouldn't" }, { "havent", "haven't" },
    { "hasnt", "hasn't" }, { "hadnt", "hadn't" }, { "aint", "ain't" },
    { "lets", "let's" }, { "hes", "he's" }, { "shes", "she's" },
    { "its", "it's" }, { "thats", "that's" }, { "whats", "what's" },
    { "heres", "here's" }, { "theres", "there's" }, { "youre", "you're" },
    { "youll", "you'll" }, { "youve", "you've" }, { "theyre", "they're" },
    { "theyll", "they'll" }, { "theyve", "they've" }, { "id", "I'd" }
  };
  public Decoder() {}

  /** Decode one immutable request. Each non-literal canonical candidate is
      collected first, then scored exactly once. */
  public Result decode(Request request, Cdict dictionary, Cdict emojiDictionary,
      Hunspell hunspell, PersonalizationStore personalization,
      boolean resourceFailure)
  {
    return decode(request, dictionary, emojiDictionary, hunspell,
        personalization, LanguageModel.empty(), resourceFailure);
  }

  public Result decode(Request request, Cdict dictionary, Cdict emojiDictionary,
      Hunspell hunspell, PersonalizationStore personalization,
      LanguageModel languageModel, boolean resourceFailure)
  {
    return decode_internal(request, dictionary, emojiDictionary, hunspell,
        personalization, languageModel, resourceFailure, false, false);
  }

  Result decode_fast(Request request, Cdict dictionary,
      Cdict emojiDictionary, Hunspell hunspell,
      PersonalizationStore personalization, boolean resourceFailure)
  {
    return decode_fast(request, dictionary, emojiDictionary, hunspell,
        personalization, LanguageModel.empty(), resourceFailure);
  }

  Result decode_fast(Request request, Cdict dictionary,
      Cdict emojiDictionary, Hunspell hunspell,
      PersonalizationStore personalization, LanguageModel languageModel,
      boolean resourceFailure)
  {
    return decode_internal(request, dictionary, emojiDictionary, hunspell,
        personalization, languageModel, resourceFailure, true, false);
  }

  Result decode_boundary(Request request, Cdict dictionary,
      Cdict emojiDictionary, Hunspell hunspell,
      PersonalizationStore personalization, boolean resourceFailure)
  {
    return decode_boundary(request, dictionary, emojiDictionary, hunspell,
        personalization, LanguageModel.empty(), resourceFailure);
  }

  Result decode_boundary(Request request, Cdict dictionary,
      Cdict emojiDictionary, Hunspell hunspell,
      PersonalizationStore personalization, LanguageModel languageModel,
      boolean resourceFailure)
  {
    return decode_internal(request, dictionary, emojiDictionary, hunspell,
        personalization, languageModel, resourceFailure, false, true);
  }

  private Result decode_internal(Request request, Cdict dictionary,
      Cdict emojiDictionary, Hunspell hunspell,
      PersonalizationStore personalization, LanguageModel languageModel,
      boolean resourceFailure, boolean fast, boolean boundaryPreview)
  {
    if (request == null)
      throw new IllegalArgumentException("request must not be null");

    FailureState failure = new FailureState(resourceFailure);
    boolean displayEnabled = request.config.useTypingAssistance
      && request.config.suggestionsEnabled && request.config.showCandidates;
    boolean autocorrectEnabled = request.config.useTypingAssistance
      && request.config.autocorrectEnabled;

    if (request.normalized.length() == 0)
      return decode_next_words(request, dictionary, personalization,
          languageModel, displayEnabled, failure);

    Map<String, Accumulator> merged = new HashMap<String, Accumulator>();
    Accumulator literal = new Accumulator(request.normalized,
        request.code_points_internal(), request.typed);
    literal.sourceMask = SOURCE_LITERAL;
    literal.literalSurface = request.typed;
    literal.literal = true;
    merged.put(literal.canonical, literal);

    int inputLength = request.codePointCount;
    if (!request.config.useTypingAssistance || (!displayEnabled && !autocorrectEnabled))
    {
      Candidate literalCandidate = finish_literal_only(literal, request,
          personalization, languageModel, failure);
      return new Result(request.key, request.typed, new Candidate[0], null,
          literalCandidate, null, !fast && !boundaryPreview,
          failure.complete(), failure.failure);
    }

    boolean boundedInput = inputLength <= MAX_WORD_CODEPOINTS
      && request.normalizedCodePointCount <= MAX_WORD_CODEPOINTS;
    boolean generateCandidates = inputLength >= 1 && boundedInput;
    PersonalizationStore.ReplacementRule replacementRule = null;
    CostTable costs = null;
    String whitelistCanonical = null;
    boolean literalAcronymOnly = false;
    if (generateCandidates && personalization != null)
    {
      try
      {
        replacementRule = personalization.replacement_rule(
            request.correctionSource);
        if (replacementRule != null && !replacementRule.uses_best_match())
        {
          // Reserve explicit user intent before bounded heuristic providers
          // can fill the merged candidate set.
          Accumulator replacement =
            accumulator_for(merged, replacementRule.target);
          if (replacement != null)
          {
            replacement.sourceMask |= SOURCE_CORRECTION | SOURCE_REPLACEMENT;
            replacement.learned = true;
            replacement.set_correction(replacementRule.target, 0, 0, 0);
          }
        }
      }
      catch (RuntimeException e)
      {
        failure.resource();
      }
    }


    if (dictionary != null)
    {
      try
      {
        Cdict.Result exact = dictionary.find(request.normalized);
        if (exact.found)
        {
          String exactWord = dictionary.word(exact.index);
          int exactFrequency = dictionary.freq(exact.index);
          if (exact.original_index != exact.index
              && !normalize(exactWord).equals(request.normalized))
          {
            // The typed form only exists as a shortcut to another word
            // (wierd -> weird): it is a misspelling, not a valid literal.
            Accumulator target = accumulator_for(merged, exactWord);
            if (target != null && is_whitelist_text(exactWord))
            {
              target.sourceMask |= SOURCE_CDICT_EXACT | SOURCE_WHITELIST;
              target.exactSurface = exactWord;
              target.set_cdict_exact(exact.index, exactFrequency);
              whitelistCanonical = target.canonical;
            }
          }
          else
          {
            literal.sourceMask |= SOURCE_CDICT_EXACT;
            literal.exactSurface = exactWord;
            literal.set_cdict_exact(exact.index, exactFrequency);
            literalAcronymOnly = casing(request.typed) == Casing.LOWER
              && exactWord.codePointCount(0, exactWord.length()) >= 2
              && casing(exactWord) == Casing.UPPER;
          }
        }


        if (generateCandidates)
        {
          float touchOffsetX = personalization == null
            ? 0f : personalization.touch_offset_x();
          float touchOffsetY = personalization == null
            ? 0f : personalization.touch_offset_y();
          costs = request.geometry.cost_table(request.code_points_internal(),
              request.touch_indexes_internal(), request.touches,
              touchOffsetX, touchOffsetY);
          Cdict.SpatialResult spatial = dictionary.spatial(
              costs.spatial_query(request.key.requestGeneration));
          collect_spatial(dictionary, spatial, request, merged, failure);
          collect_adjacent_transpositions(dictionary, request, merged);

          int[] prefixIndexes = dictionary.suffixes(exact, MAX_PREFIX_RESULTS);
          for (int i = 0; i < prefixIndexes.length
              && i < MAX_PREFIX_RESULTS; i++)
          {
            int index = prefixIndexes[i];
            String surface = dictionary.word(index);
            Accumulator candidate = accumulator_for(merged, surface);
            if (candidate == null)
              continue;
            candidate.sourceMask |= SOURCE_CDICT_PREFIX;
            candidate.set_prefix(surface, i, index, dictionary.freq(index));
          }
          collect_regular_inflection_candidates(dictionary, hunspell,
              request.normalized, merged);
        }
      }
      catch (IllegalStateException e)
      {
        failure.corrupt();
      }
      catch (RuntimeException e)
      {
        failure.resource();
      }
    }

    if (hunspell != null)
    {
      try
      {
        boolean hunspellExact = hunspell.spell(request.normalized);
        if (hunspellExact)
        {
          literal.sourceMask |= SOURCE_HUNSPELL;
          literal.hunspellSurface = request.typed;
          literal.hunspellRank = 0;
        }
        if (generateCandidates && inputLength == 1
            && !"i".equals(request.normalized) && hunspell.spell("I"))
        {
          Accumulator firstPerson = accumulator_for(merged, "I");
          if (firstPerson != null)
          {
            firstPerson.sourceMask |= SOURCE_HUNSPELL;
            firstPerson.set_hunspell("I", 0);
          }
        }
        if (generateCandidates)
        {
          collect_adjacent_transpositions(hunspell, request, merged);
          collect_hunspell_contractions(
              hunspell, request.normalized, merged);
        }
        collect_canonical_contractions(request.normalized, merged);
      }
      catch (RuntimeException e)
      {
        failure.resource();
      }
    }

    if (generateCandidates && personalization != null)
    {
      try
      {
        List<PersonalizationStore.ScoredCorrection> corrections =
          personalization.suggest_corrections_with_counts(
              request.correctionSource,
              request.geometry, MAX_CORRECTION_RESULTS);
        int count = Math.min(corrections.size(), MAX_CORRECTION_RESULTS);
        for (int i = 0; i < count; i++)
        {
          PersonalizationStore.ScoredCorrection correction =
            corrections.get(i);
          Accumulator candidate = accumulator_for(merged, correction.target);
          if (candidate == null)
            continue;
          candidate.sourceMask |= SOURCE_CORRECTION;
          candidate.set_correction(correction.target, i,
              correction.exactCount, correction.relatedCount);
        }
        List<PersonalizationStore.ScoredContextualCorrection>
          contextualCorrections =
            personalization.suggest_contextual_corrections_with_counts(
                request.correctionSource, MAX_CORRECTION_RESULTS);
        count = Math.min(contextualCorrections.size(),
            MAX_CORRECTION_RESULTS);
        for (int i = 0; i < count; i++)
        {
          PersonalizationStore.ScoredContextualCorrection correction =
            contextualCorrections.get(i);
          Accumulator candidate = accumulator_for(merged, correction.target);
          if (candidate == null)
            continue;
          candidate.sourceMask |= SOURCE_CORRECTION
            | SOURCE_CONTEXTUAL_CORRECTION;
          candidate.learned = true;
          candidate.set_correction(correction.target, i, 0, 0);
        }
        List<PersonalizationStore.ScoredWord> words =
          personalization.suggest_words_with_counts(request.normalized,
              MAX_PERSONAL_RESULTS);
        count = Math.min(words.size(), MAX_PERSONAL_RESULTS);
        for (int i = 0; i < count; i++)
        {
          PersonalizationStore.ScoredWord word = words.get(i);
          Accumulator candidate = accumulator_for(merged, word.word);
          if (candidate == null)
            continue;
          candidate.sourceMask |= SOURCE_PERSONAL;
          candidate.set_personal(word.word, i);
        }
      }
      catch (RuntimeException e)
      {
        failure.resource();
      }
    }


    String previousWord = null;
    String priorWord = null;
    if (personalization != null)
    {
      try
      {
        previousWord = personalization.previous_word();
        priorWord = personalization.previous_previous_word();
      }
      catch (RuntimeException e)
      {
        failure.resource();
      }
    }

    int[] previousFollowers = context_followers(dictionary, languageModel,
        previousWord, failure);
    if (generateCandidates && previousWord != null && languageModel != null)
    {
      try
      {
        collect_language_model_candidates(dictionary, hunspell, languageModel,
            priorWord, previousWord, merged);
        collect_context_followers(dictionary, previousFollowers, merged);
      }
      catch (IllegalStateException e)
      {
        failure.corrupt();
      }
      catch (RuntimeException e)
      {
        failure.resource();
      }
    }
    if (costs == null && generateCandidates && merged.size() > 1)
      costs = request.geometry.cost_table(request.code_points_internal(),
          request.touch_indexes_internal(), request.touches,
          personalization == null ? 0f : personalization.touch_offset_x(),
          personalization == null ? 0f : personalization.touch_offset_y());

    List<Candidate> ranked = rank_candidates(request, merged, costs,
        personalization, languageModel, priorWord, previousWord,
        previousFollowers, failure);
    Candidate literalCandidate = candidate_for_canonical(ranked,
        request.normalized);
    Candidate autocorrection = choose_autocorrection(request, ranked,
        literalCandidate, autocorrectEnabled, failure.failure,
        literalAcronymOnly);
    if (fast && !is_proven_fast_autocorrection(autocorrection))
      autocorrection = null;
    if (!fast && dictionary != null && costs != null
        && literalCandidate != null && !literalCandidate.recognized)
    {
      try
      {
        Cdict.SpatialResult nearby = dictionary.spatial(
            costs.nearby_spatial_query(request.key.requestGeneration));
        collect_spatial(dictionary, nearby, request, merged, failure);
      }
      catch (IllegalStateException e)
      {
        failure.corrupt();
      }
      catch (RuntimeException e)
      {
        failure.resource();
      }
      ranked = rank_candidates(request, merged, costs, personalization,
          languageModel, priorWord, previousWord, previousFollowers, failure);
      literalCandidate = candidate_for_canonical(ranked, request.normalized);
      autocorrection = choose_autocorrection(request, ranked,
          literalCandidate, autocorrectEnabled, failure.failure,
          literalAcronymOnly);
    }
    if (!fast && !boundaryPreview && hunspell != null && generateCandidates
        && literalCandidate != null
        && (!literalCandidate.recognized || inputLength == 2))
    {
      try
      {
        collect_hunspell_suggestions(hunspell, dictionary,
            request.normalized, merged);
      }
      catch (IllegalStateException e)
      {
        failure.corrupt();
      }
      catch (RuntimeException e)
      {
        failure.resource();
      }
      ranked = rank_candidates(request, merged, costs, personalization,
          languageModel, priorWord, previousWord, previousFollowers, failure);
      literalCandidate = candidate_for_canonical(ranked, request.normalized);
      autocorrection = choose_autocorrection(request, ranked,
          literalCandidate, autocorrectEnabled, failure.failure,
          literalAcronymOnly);
    }
    if (replacementRule != null)
      autocorrection = choose_replacement_autocorrection(
          replacementRule, request, ranked, literalCandidate,
          autocorrectEnabled);
    boolean replacementApplied = replacementRule != null
      && autocorrection != null;
    boolean whitelistApplied = autocorrection != null
      && (autocorrection.sourceMask & SOURCE_WHITELIST) != 0;
    if (boundaryPreview && !replacementApplied && !whitelistApplied
        && !is_safe_boundary_autocorrection(
          request, literalCandidate, autocorrection, ranked))
      autocorrection = null;
    String preferredShortSurface = preferred_short_surface(
        request, literalCandidate, personalization);
    if (replacementRule == null && autocorrection == null
        && autocorrectEnabled && preferredShortSurface != null
        && !preferredShortSurface.equals(request.typed))
      autocorrection = literalCandidate.with_surface(preferredShortSurface);
    String emoji = displayEnabled && boundedInput
      ? query_emoji(emojiDictionary, request.normalized, failure) : null;
    if (autocorrection != null)
      promote_candidate(ranked, autocorrection.canonical);
    else if (whitelistCanonical != null && literalCandidate != null
        && !literalCandidate.learned)
      // A valid literal with a packaged shortcut (cant -> can't) is kept on
      // separators but its shortcut leads the strip for explicit acceptance.
      promote_candidate(ranked, whitelistCanonical);
    Candidate[] words = displayEnabled && inputLength >= 2
      ? visible_words(ranked, request, personalization) : new Candidate[0];

    boolean autocorrectionComplete = fast
      ? is_proven_fast_autocorrection(autocorrection)
      : !boundaryPreview || autocorrection != null;
    return new Result(request.key, request.typed, words, emoji,
        present_candidate(literalCandidate, request, false), autocorrection,
        autocorrectionComplete, failure.complete(), failure.failure);
  }

  private static List<Candidate> rank_candidates(Request request,
      Map<String, Accumulator> merged, CostTable costs,
      PersonalizationStore personalization, LanguageModel languageModel,
      String priorWord, String previousWord, int[] previousFollowers,
      FailureState failure)
  {
    Scorer scorer = costs == null ? null : new Scorer(costs);
    List<Candidate> ranked = new ArrayList<Candidate>(merged.size());
    for (Accumulator accumulator : merged.values())
    {
      add_unigram_frequency(accumulator, languageModel);
      add_personalization_metadata(accumulator, personalization, languageModel,
          priorWord, previousWord, failure);
      add_context_follower_weight(accumulator, previousFollowers);
      Score score;
      if (accumulator.literal)
        score = Score.EXACT;
      else if (scorer == null)
        continue;
      else
        score = scorer.score(accumulator.codePoints);
      ranked.add(accumulator.to_candidate(score));
    }
    sort_candidates_for_request(request, ranked);
    return ranked;
  }

  private static Result decode_next_words(Request request, Cdict dictionary,
      PersonalizationStore personalization, LanguageModel languageModel,
      boolean displayEnabled, FailureState failure)
  {
    if (!displayEnabled || personalization == null)
      return new Result(request.key, request.typed, new Candidate[0], null,
          null, null, true, failure.complete(), failure.failure);
    try
    {
      List<Candidate> words = new ArrayList<Candidate>(MAX_VISIBLE_WORDS);
      Set<String> seen = new HashSet<String>();
      List<PersonalizationStore.ScoredWord> matches =
        personalization.suggest_next_words_with_counts(MAX_VISIBLE_WORDS);
      for (PersonalizationStore.ScoredWord match : matches)
      {
        if (words.size() == MAX_VISIBLE_WORDS)
          break;
        String canonical = normalize(match.word);
        if (!seen.add(canonical))
          continue;
        int unigram = personalization.word_count(canonical);
        int sourceMask = SOURCE_CONTEXT;
        if (unigram > 0)
          sourceMask |= SOURCE_PERSONAL;
        int total = clamp_score(-(long)256 * count_level(unigram)
            - (long)384 * count_level(match.count));
        words.add(new Candidate(canonical, match.word, sourceMask, -1, 0,
            words.size(), unigram, match.count, 0, 0, 0, 0, 0, 0, total,
            Role.NEXT_WORD, false, unigram > 0, true));
      }
      // Packaged context priors fill the remaining slots: trigram rows for
      // the two previous words first, then bigram rows for the last word.
      String previous = personalization.previous_word();
      if (languageModel != null && previous != null)
      {
        String prior = personalization.previous_previous_word();
        add_model_next_words(words, seen,
            languageModel.following(prior, previous));
        Map<String, Integer> following = new HashMap<String, Integer>();
        for (Map.Entry<String, Integer> row
            : languageModel.following(previous).entrySet())
          following.put(row.getKey(), 2 * row.getValue());
        int[] followers = context_followers(dictionary, languageModel,
            previous, failure);
        for (int rank = 0; rank < followers.length
            && rank < NEXT_WORD_FOLLOWER_WEIGHTS.length; rank++)
        {
          String surface = dictionary.word(followers[rank]);
          following.remove(normalize(surface));
          following.put(surface, NEXT_WORD_FOLLOWER_WEIGHTS[rank]);
        }
        add_model_next_words(words, seen, following);
      }
      return new Result(request.key, request.typed,
          words.toArray(new Candidate[0]), null, null, null, true,
          failure.complete(), failure.failure);
    }
    catch (RuntimeException e)
    {
      failure.resource();
      return new Result(request.key, request.typed, new Candidate[0], null,
          null, null, true, failure.complete(), failure.failure);
    }
  }

  private static void add_model_next_words(List<Candidate> words,
      Set<String> seen, Map<String, Integer> following)
  {
    if (words.size() >= MAX_VISIBLE_WORDS || following.isEmpty())
      return;
    List<Map.Entry<String, Integer>> rows =
      new ArrayList<Map.Entry<String, Integer>>(following.entrySet());
    Collections.sort(rows, new Comparator<Map.Entry<String, Integer>>()
        {
          @Override
          public int compare(Map.Entry<String, Integer> a,
              Map.Entry<String, Integer> b)
          {
            int result = Integer.compare(b.getValue(), a.getValue());
            return result != 0 ? result
              : code_point_compare(a.getKey(), b.getKey());
          }
        });
    for (Map.Entry<String, Integer> row : rows)
    {
      if (words.size() >= MAX_VISIBLE_WORDS)
        return;
      String canonical = normalize(row.getKey());
      if (!seen.add(canonical))
        continue;
      String surface = "i'm".equals(canonical) ? "I'm"
        : ("i".equals(canonical) ? "I" : row.getKey());
      int weight = row.getValue();
      words.add(new Candidate(canonical, surface, SOURCE_CONTEXT, -1, 0,
          words.size(), 0, weight, 0, 0, 0, 0, 0, 0,
          clamp_score(-(long)384 * count_level(weight)), Role.NEXT_WORD,
          false, false, true));
    }
  }

  private static Candidate finish_literal_only(Accumulator literal,
      Request request, PersonalizationStore personalization,
      LanguageModel languageModel, FailureState failure)
  {
    add_personalization_metadata(literal, personalization, languageModel,
        personalization == null ? null : safe_previous_previous_word(
          personalization, failure),
        personalization == null ? null : safe_previous_word(personalization,
          failure), failure);
    return present_candidate(literal.to_candidate(Score.EXACT), request, false);
  }

  private static String safe_previous_word(PersonalizationStore personalization,
      FailureState failure)
  {
    try
    {
      return personalization.previous_word();
    }
    catch (RuntimeException e)
    {
      failure.resource();
      return null;
    }
  }

  private static String safe_previous_previous_word(
      PersonalizationStore personalization, FailureState failure)
  {
    try
    {
      return personalization.previous_previous_word();
    }
    catch (RuntimeException e)
    {
      failure.resource();
      return null;
    }
  }

  private static void collect_hunspell_suggestions(Hunspell hunspell,
      Cdict dictionary, String normalized, Map<String, Accumulator> merged)
  {
    String[] suggestions = hunspell.suggest(normalized, MAX_HUNSPELL_RESULTS);
    boolean primaryRepairAssigned = false;
    int count = Math.min(suggestions.length, MAX_HUNSPELL_RESULTS);
    int normalizedLength = normalized.codePointCount(0, normalized.length());
    for (int i = 0; i < count; i++)
    {
      Accumulator candidate = accumulator_for(merged, suggestions[i]);
      if (candidate == null)
        continue;
      candidate.sourceMask |= SOURCE_HUNSPELL;
      if (!primaryRepairAssigned && !candidate.canonical.equals(normalized)
          && candidate.codePoints.length >= normalizedLength)
      {
        candidate.sourceMask |= SOURCE_HUNSPELL_PRIMARY;
        primaryRepairAssigned = true;
      }
      candidate.set_hunspell(suggestions[i], i);
      if (dictionary != null)
      {
        Cdict.Result exact = dictionary.find(candidate.canonical);
        if (exact.found)
        {
          candidate.sourceMask |= SOURCE_CDICT_EXACT;
          candidate.set_cdict_exact(exact.index, dictionary.freq(exact.index));
        }
      }
    }
  }
  private static void collect_hunspell_contractions(Hunspell hunspell,
      String normalized, Map<String, Accumulator> merged)
  {
    if (!is_letters_only(normalized))
      return;
    int rank = 0;
    for (String suffix : CONTRACTION_SUFFIXES)
    {
      int offset = normalized.length() - suffix.length();
      if (offset <= 0 || !normalized.endsWith(suffix))
        continue;
      String contraction = normalized.substring(0, offset) + "'"
        + normalized.substring(offset);
      String surface = contraction.startsWith("i'")
        ? "I" + contraction.substring(1) : contraction;
      if (!hunspell.spell(surface))
        continue;
      Accumulator candidate = accumulator_for(merged, surface);
      if (candidate != null)
      {
        candidate.sourceMask |= SOURCE_HUNSPELL | SOURCE_CONTRACTION;
        candidate.set_hunspell(surface, rank++);
      }
    }
    String negative = missing_negative_contraction(normalized);
    if (negative != null && hunspell.spell(negative))
    {
      Accumulator candidate = accumulator_for(merged, negative);
      if (candidate != null)
      {
        candidate.sourceMask |= SOURCE_HUNSPELL | SOURCE_CONTRACTION;
        candidate.set_hunspell(negative, rank);
      }
    }
  }

  /** Inject canonical contractions when the typed word is exactly a known
      uncontracted spelling. Whitelist-masked so autocorrect may pick them
      even when hunspell lacks the surface (ive→I've, im→I'm). */
  private static void collect_canonical_contractions(String normalized,
      Map<String, Accumulator> merged)
  {
    if (normalized == null || !is_letters_only(normalized))
      return;
    for (String[] pair : CANONICAL_CONTRACTIONS)
    {
      if (!normalized.equalsIgnoreCase(pair[0]))
        continue;
      Accumulator candidate = accumulator_for(merged, pair[1]);
      if (candidate == null)
        continue;
      candidate.sourceMask |= SOURCE_WHITELIST | SOURCE_CONTRACTION;
      candidate.exactSurface = pair[1];
      return;
    }
  }

  private static String missing_negative_contraction(String normalized)
  {
    if (!is_letters_only(normalized) || normalized.length() < 3
        || !normalized.endsWith("t") || normalized.endsWith("nt"))
      return null;
    String base = normalized.substring(0, normalized.length() - 1);
    for (String candidate : NEGATIVE_CONTRACTION_BASES)
      if (candidate.equals(base))
        return base + "n't";
    return null;
  }



  private static void collect_adjacent_transpositions(Cdict dictionary,
      Request request, Map<String, Accumulator> merged)
  {
    if (request.codePointCount != 3 || !is_letters_only(request.normalized))
      return;
    int[] transposed = request.code_points_internal().clone();
    for (int i = 0; i + 1 < transposed.length; i++)
    {
      int left = transposed[i];
      transposed[i] = transposed[i + 1];
      transposed[i + 1] = left;
      String canonical = new String(transposed, 0, transposed.length);
      Cdict.Result exact = dictionary.find(canonical);
      if (exact.found)
      {
        String surface = dictionary.word(exact.index);
        Accumulator candidate = accumulator_for(merged, surface);
        if (candidate != null)
        {
          candidate.sourceMask |= SOURCE_CDICT_SPATIAL;
          candidate.set_spatial(surface, 0, exact.index,
              dictionary.freq(exact.index), true);
        }
      }
      transposed[i + 1] = transposed[i];
      transposed[i] = left;
    }
  }

  private static void collect_adjacent_transpositions(Hunspell hunspell,
      Request request, Map<String, Accumulator> merged)
  {
    if (request.codePointCount != 3 || !is_letters_only(request.normalized))
      return;
    int[] transposed = request.code_points_internal().clone();
    for (int i = 0; i + 1 < transposed.length; i++)
    {
      int left = transposed[i];
      transposed[i] = transposed[i + 1];
      transposed[i + 1] = left;
      String canonical = new String(transposed, 0, transposed.length);
      if (hunspell.spell(canonical))
      {
        Accumulator candidate = accumulator_for(merged, canonical);
        if (candidate != null)
        {
          candidate.sourceMask |= SOURCE_HUNSPELL;
          candidate.set_hunspell(canonical, 0);
        }
      }
      transposed[i + 1] = transposed[i];
      transposed[i] = left;
    }
  }


  private static void collect_spatial(Cdict dictionary,
      Cdict.SpatialResult result, Request request,
      Map<String, Accumulator> merged, FailureState failure)
  {
    if (result == null || result.sequence != request.key.requestGeneration)
    {
      failure.resource();
      return;
    }
    boolean completeEvidence;
    if (result.status == Cdict.CDICT_SPATIAL_OK)
      completeEvidence = true;
    else if (result.status == Cdict.CDICT_SPATIAL_TRUNCATED)
    {
      completeEvidence = false;
      failure.truncated();
    }
    else if (result.status == Cdict.CDICT_SPATIAL_CORRUPT_DICTIONARY
        || result.status == Cdict.CDICT_SPATIAL_INVALID_UTF8)
    {
      failure.corrupt();
      return;
    }
    else
    {
      failure.resource();
      return;
    }

    int count = Math.min(result.candidates.length, MAX_NATIVE_RESULTS);
    for (int i = 0; i < count; i++)
    {
      Cdict.SpatialCandidate nativeCandidate = result.candidates[i];
      String surface = dictionary.word(nativeCandidate.index);
      Accumulator candidate = accumulator_for(merged, surface);
      if (candidate == null)
        continue;
      candidate.sourceMask |= SOURCE_CDICT_SPATIAL;
      candidate.set_spatial(surface, i, nativeCandidate.index,
          nativeCandidate.frequency, completeEvidence);
    }
  }



  private static void collect_regular_inflection_candidates(
      Cdict dictionary, Hunspell hunspell, String typed,
      Map<String, Accumulator> merged)
  {
    collect_regular_inflection_candidate(dictionary, hunspell,
        regular_y_inflection_target(typed), merged);
    collect_regular_inflection_candidate(dictionary, hunspell,
        regular_es_plural_target(typed), merged);
    collect_regular_inflection_candidate(dictionary, hunspell,
        regular_suffix_target(typed), merged);
  }

  private static void collect_regular_inflection_candidate(
      Cdict dictionary, Hunspell hunspell, String target,
      Map<String, Accumulator> merged)
  {
    if (target == null)
      return;
    Cdict.Result exact = dictionary.find(target);
    String surface = exact.found ? dictionary.word(exact.index) : target;
    if (!exact.found && (hunspell == null || !hunspell.spell(surface)))
      return;
    Accumulator candidate = accumulator_for(merged, surface);
    if (candidate == null)
      return;
    if (exact.found)
    {
      candidate.sourceMask |= SOURCE_CDICT_EXACT;
      candidate.set_cdict_exact(exact.index, dictionary.freq(exact.index));
    }
    else
    {
      candidate.sourceMask |= SOURCE_HUNSPELL;
      candidate.set_hunspell(surface, 0);
    }
  }

  private static void collect_language_model_candidates(Cdict dictionary,
      Hunspell hunspell, LanguageModel languageModel, String priorWord,
      String previousWord, Map<String, Accumulator> merged)
  {
    collect_language_model_candidates(dictionary, hunspell,
        languageModel.following(priorWord, previousWord), merged);
    collect_language_model_candidates(dictionary, hunspell,
        languageModel.following(previousWord), merged);
  }

  private static void collect_language_model_candidates(Cdict dictionary,
      Hunspell hunspell, Map<String, Integer> following,
      Map<String, Accumulator> merged)
  {
    for (String target : following.keySet())
    {
      String surface = target;
      Cdict.Result exact = null;
      if (dictionary != null)
      {
        exact = dictionary.find(target);
        if (exact.found)
          surface = dictionary.word(exact.index);
      }
      boolean hunspellExact = exact == null || !exact.found;
      if (hunspellExact && (hunspell == null || !hunspell.spell(target)))
        continue;

      Accumulator candidate = accumulator_for(merged, surface);
      if (candidate == null)
        continue;
      if (exact != null && exact.found)
      {
        candidate.sourceMask |= SOURCE_CDICT_EXACT;
        candidate.set_cdict_exact(exact.index, dictionary.freq(exact.index));
      }
      else
      {
        candidate.sourceMask |= SOURCE_HUNSPELL;
        candidate.set_hunspell(surface, 0);
      }
    }
  }

  private static Accumulator accumulator_for(Map<String, Accumulator> merged,
      String surface)
  {
    if (surface == null || surface.length() == 0)
      return null;
    String canonical = normalize(surface);
    if (canonical.length() == 0
        || canonical.codePointCount(0, canonical.length())
          > MAX_CANDIDATE_CODEPOINTS)
      return null;
    Accumulator candidate = merged.get(canonical);
    if (candidate != null)
      return candidate;
    if (merged.size() >= MAX_MERGED_CANDIDATES)
      return null;
    candidate = new Accumulator(canonical,
        canonical.codePoints().toArray(), surface);
    merged.put(canonical, candidate);
    return candidate;
  }

  /** Ranked dictionary continuations of [previousWord], or none. */
  private static int[] context_followers(Cdict dictionary,
      LanguageModel languageModel, String previousWord, FailureState failure)
  {
    if (dictionary == null || languageModel == null || previousWord == null)
      return new int[0];
    try
    {
      Cdict.Result head = dictionary.find(previousWord);
      return head.found ? languageModel.cdict_followers(head.index)
        : new int[0];
    }
    catch (RuntimeException e)
    {
      failure.resource();
      return new int[0];
    }
  }

  private static void collect_context_followers(Cdict dictionary,
      int[] followers, Map<String, Accumulator> merged)
  {
    for (int index : followers)
    {
      String surface = dictionary.word(index);
      Accumulator candidate = accumulator_for(merged, surface);
      if (candidate == null)
        continue;
      candidate.sourceMask |= SOURCE_CDICT_EXACT;
      candidate.set_cdict_exact(index, dictionary.freq(index));
    }
  }

  private static void add_context_follower_weight(Accumulator candidate,
      int[] followers)
  {
    if (followers == null)
      return;
    for (int rank = 0; rank < followers.length
        && rank < CONTEXT_FOLLOWER_WEIGHTS.length; rank++)
    {
      int index = followers[rank];
      if (index != candidate.exactIndex && index != candidate.spatialIndex
          && index != candidate.prefixIndex)
        continue;
      candidate.bigramCount = Math.max(candidate.bigramCount,
          CONTEXT_FOLLOWER_WEIGHTS[rank]);
      candidate.sourceMask |= SOURCE_CONTEXT;
      return;
    }
  }

  private static void add_unigram_frequency(Accumulator candidate,
      LanguageModel languageModel)
  {
    if (languageModel == null)
      return;
    for (int index : new int[] { candidate.exactIndex, candidate.spatialIndex,
        candidate.prefixIndex })
    {
      if (index < 0)
        continue;
      candidate.frequency8 = Math.max(candidate.frequency8,
          languageModel.unigram(index));
      if (candidate.cdictFrequency == 0)
        candidate.cdictFrequency = languageModel.repaired_level(index, 0);
    }
  }

  private static void add_personalization_metadata(Accumulator candidate,
      PersonalizationStore personalization, LanguageModel languageModel,
      String priorWord, String previousWord, FailureState failure)
  {
    try
    {
      if (personalization != null)
      {
        candidate.unigramCount =
          personalization.word_count(candidate.canonical);
        candidate.bigramCount = previousWord == null ? 0
          : personalization.bigram_count(previousWord, candidate.canonical);
        if (candidate.unigramCount > 0)
        {
          candidate.sourceMask |= SOURCE_PERSONAL;
          candidate.learned = true;
          if (candidate.personalRank == Integer.MAX_VALUE)
            candidate.personalRank = 0;
        }
      }
      if (languageModel != null && previousWord != null)
        candidate.bigramCount = Math.max(candidate.bigramCount,
            languageModel.weight(priorWord, previousWord,
              candidate.canonical));
      if (candidate.bigramCount > 0)
        candidate.sourceMask |= SOURCE_CONTEXT;
    }
    catch (RuntimeException e)
    {
      failure.resource();
    }
  }

  private static Candidate[] visible_words(List<Candidate> ranked,
      Request request, PersonalizationStore personalization)
  {
    Candidate[] out = new Candidate[Math.min(MAX_VISIBLE_WORDS, ranked.size())];
    int count = 0;
    for (Candidate candidate : ranked)
    {
      Candidate presented = present_candidate(candidate, request, false);
      String preferred = preferred_short_surface(
          request, candidate, personalization);
      out[count++] = preferred == null
        ? presented : presented.with_surface(preferred);
      if (count == MAX_VISIBLE_WORDS)
        break;
    }
    if (count == out.length)
      return out;
    return Arrays.copyOf(out, count);
  }

  private static String preferred_short_surface(Request request,
      Candidate candidate, PersonalizationStore personalization)
  {
    if (request == null || candidate == null || personalization == null)
      return null;
    int count = candidate.canonical.codePointCount(
        0, candidate.canonical.length());
    Casing typedCasing = casing(request.typed);
    if (count < 2 || count > 3 || typedCasing == Casing.UPPER
        || typedCasing == Casing.MIXED)
      return null;
    return personalization.preferred_surface(candidate.canonical);
  }



  private static boolean is_proven_fast_autocorrection(Candidate candidate)
  {
    return candidate != null
      && ((candidate.sourceMask & (SOURCE_REPLACEMENT | SOURCE_WHITELIST)) != 0
        || candidate.exactCorrectionCount > 0
        || (candidate.sourceMask & SOURCE_CONTEXTUAL_CORRECTION) != 0);
  }

  private static Candidate choose_replacement_autocorrection(
      PersonalizationStore.ReplacementRule rule, Request request,
      List<Candidate> ranked, Candidate literal, boolean enabled)
  {
    if (rule == null || !enabled)
      return null;
    if (!rule.uses_best_match())
    {
      Candidate fixed = candidate_for_canonical(ranked,
          normalize(rule.target));
      return fixed == null ? null : present_candidate(fixed, request, true);
    }
    if (literal == null)
      return null;
    for (Candidate candidate : ranked)
      if (candidate != literal && (candidate.recognized || candidate.learned)
          && candidate.completeEvidence
          && is_autocorrection_candidate_text(candidate, literal)
          && is_case_compatible_autocorrection(request, candidate))
        return present_candidate(candidate, request, true);
    return null;
  }

  private static boolean is_safe_boundary_autocorrection(Request request,
      Candidate literal, Candidate correction, List<Candidate> ranked)
  {
    if (request.codePointCount != 3 || literal == null
        || literal.recognized || literal.learned || correction == null
        || correction.editCount != 1
        || correction.editMask != EDIT_SUBSTITUTION
        || correction.canonical.codePointCount(
          0, correction.canonical.length()) != request.normalizedCodePointCount
        || (correction.sourceMask & SOURCE_CDICT_SPATIAL) == 0
        || correction.cdictFrequency < BOUNDARY_PREVIEW_MIN_CDICT_FREQUENCY
        || !correction.completeEvidence
        || (long)literal.totalQ8 - correction.totalQ8
          < BOUNDARY_PREVIEW_LITERAL_MARGIN_Q8)
      return false;
    long correctionScore = ranking_total_q8(request, correction);
    for (Candidate candidate : ranked)
    {
      if (candidate == literal || candidate == correction
          || !is_autocorrection_candidate_text(candidate, literal)
          || !is_case_compatible_autocorrection(request, candidate)
          || candidate.editCount != 1
          || candidate.editMask != EDIT_SUBSTITUTION
          || candidate.canonical.codePointCount(
            0, candidate.canonical.length()) != request.normalizedCodePointCount)
        continue;
      if (ranking_total_q8(request, candidate) - correctionScore
          < BOUNDARY_PREVIEW_RUNNER_MARGIN_Q8)
        return false;
    }
    return true;
  }



  private static Candidate choose_autocorrection(Request request,
      List<Candidate> ranked, Candidate literal, boolean enabled,
      Failure failure)
  {
    return choose_autocorrection(request, ranked, literal, enabled, failure,
        false);
  }

  private static Candidate choose_autocorrection(Request request,
      List<Candidate> ranked, Candidate literal, boolean enabled,
      Failure failure, boolean literalAcronymOnly)
  {
    if (!enabled || literal == null || request.codePointCount < 1
        || request.codePointCount > MAX_WORD_CODEPOINTS
        || !is_autocorrection_source_text(request)
        || casing(request.typed) == Casing.MIXED
        || (failure != Failure.NONE && failure != Failure.NATIVE_TRUNCATED))
      return null;
    Candidate contextualExact = null;
    Candidate repeatedExact = null;
    Candidate missingNegative = null;
    for (Candidate candidate : ranked)
    {
      if (is_contextual_exact_correction(request, candidate)
          && contextualExact == null)
        contextualExact = candidate;
      if (missingNegative == null
          && is_missing_negative_contraction(request, candidate))
        missingNegative = candidate;
      if (!is_repeated_exact_correction(request, candidate))
        continue;
      if (repeatedExact == null || candidate.exactCorrectionCount
          > repeatedExact.exactCorrectionCount)
        repeatedExact = candidate;
    }
    if (request.codePointCount == 1)
    {
      if ("j".equals(request.normalized))
        for (Candidate candidate : ranked)
          if ("i".equals(candidate.canonical) && candidate.recognized
              && candidate.editCount == 1
              && candidate.editMask == EDIT_SUBSTITUTION)
            return present_candidate(candidate, request, true);
      return null;
    }
    if (literal.unigramCount > 0)
      return repeatedExact == null ? null
        : present_candidate(repeatedExact, request, true);
    Candidate whitelist = whitelist_correction(request, literal, ranked);
    if (whitelist != null)
      return present_candidate(whitelist, request, true);
    if (literalAcronymOnly)
    {
      Candidate acronymRepair =
        acronym_transposition_repair(request, literal, ranked);
      if (acronymRepair != null)
        return present_candidate(acronymRepair, request, true);
    }
    if (contextualExact != null)
      return present_candidate(contextualExact, request, true);
    if (repeatedExact != null)
      return present_candidate(repeatedExact, request, true);
    if (missingNegative != null)
      return present_candidate(missingNegative, request, true);
    if (request.codePointCount == 2 && "im".equals(request.normalized))
    {
      for (Candidate candidate : ranked)
        if (candidate != literal
            && "i'm".equals(candidate.canonical)
            && (candidate.sourceMask & SOURCE_CONTRACTION) != 0
            && candidate.recognized
            && candidate.editCount == 1)
          return present_candidate(candidate, request, true);
      return null;
    }
    Candidate contextRepair =
      decisive_context_repair(request, literal, ranked);
    if (contextRepair != null)
      return present_candidate(contextRepair, request, true);
    Candidate inflectionRepair =
      regular_inflection_repair(request, literal, ranked);
    if (inflectionRepair != null)
      return present_candidate(inflectionRepair, request, true);
    Candidate repeatedLetterRepair =
      repeated_letter_omission_repair(request, literal, ranked);
    if (repeatedLetterRepair != null)
      return present_candidate(repeatedLetterRepair, request, true);
    Candidate shortTransposition =
      clear_short_transposition(request, literal, ranked);
    if (shortTransposition != null)
      return present_candidate(shortTransposition, request, true);
    Candidate shortLexicalRepair =
      primary_short_lexical_repair(request, literal, ranked);
    if (shortLexicalRepair != null)
      return present_candidate(shortLexicalRepair, request, true);

    boolean protectedLiteral = literal.recognized || literal.learned;
    Candidate best = null;
    Candidate runner = null;
    Candidate closestSpatial = null;
    boolean betterCaseOnlyCandidate = false;
    for (Candidate candidate : ranked)
    {
      // A stronger capitalized word (englis -> English) must not hand the
      // correction to a weaker lowercase one (envois); keep the literal and
      // let the strip offer the capitalized word.
      if (best == null && candidate != literal
          && casing(request.typed) == Casing.LOWER
          && casing(candidate.surface) == Casing.INITIAL
          && is_autocorrection_candidate_text(candidate, literal)
          && candidate.recognized && is_safe_autocorrection_edit(candidate))
        betterCaseOnlyCandidate = true;
      if (!is_autocorrection_candidate_text(candidate, literal)
          || !is_case_compatible_autocorrection(request, candidate)
          || !is_safe_autocorrection_edit(request, candidate)
          || is_short_prefix_completion(request, candidate)
          || is_cold_short_all_caps_correction(request, candidate))
        continue;
      if (closestSpatial == null
          || candidate.spatialQ8 < closestSpatial.spatialQ8)
        closestSpatial = candidate;
      if ((request.codePointCount == 1
            && (!"i".equals(candidate.canonical)
              || candidate.editMask != EDIT_SUBSTITUTION))
          || (request.codePointCount == 2
            && candidate.editMask != EDIT_SUBSTITUTION))
        continue;
      if (best == null)
        best = candidate;
      else if (runner == null)
        runner = candidate;
    }
    if (betterCaseOnlyCandidate)
      return null;
    Candidate primarySameLengthRepair =
      primary_same_length_repair_over_length_guesses(
        request, literal, best, ranked);
    if (primarySameLengthRepair != null)
      return present_candidate(primarySameLengthRepair, request, true);
    if (best != null && request.codePointCount <= 3
        && closestSpatial != null && closestSpatial != best
        && (long)best.spatialQ8 - closestSpatial.spatialQ8
          >= AUTOCORRECT_SHORT_WORD_SPATIAL_GAP_Q8
        && !is_complete_one_edit_length_repair(best))
      return null;
    if (best == null
        || (long)literal.totalQ8 - best.totalQ8
          < autocorrect_literal_margin_q8(best)
        || (runner != null && ranking_total_q8(request, runner)
            - ranking_total_q8(request, best)
          < ((request.codePointCount >= 4
                && best.editMask == EDIT_SUBSTITUTION
                && runner.editMask == EDIT_SUBSTITUTION)
              ? Q8 : AUTOCORRECT_RUNNER_MARGIN_Q8)
          && !primary_resolves_same_length_substitution(
            request, best, runner)
          && !provider_rank_resolves_length_tie(request, best, runner)
          && !omission_resolves_unknown_length_tie(
            request, literal, best, runner, ranked)
          && !morphology_resolves_omission_tie(
            request, literal, best, runner)))
      return null;
    if (has_ambiguous_length_repair_pair(
          request, literal, best, ranked))
      return null;
    if (has_competing_length_repair(request, literal, best, ranked))
      return null;
    if (protectedLiteral
        && !is_missing_negative_contraction(request, best)
        && !has_decisive_context_prior(literal, best)
        && best.exactCorrectionCount < PROTECTED_LITERAL_EXACT_EVENTS)
      return null;
    return present_candidate(best, request, true);
  }

  /** A packaged shortcut target replaces an unrecognized, untaught literal
      (LatinIME whitelist semantics). */
  private static Candidate whitelist_correction(Request request,
      Candidate literal, List<Candidate> ranked)
  {
    if (literal.recognized || literal.learned)
      return null;
    for (Candidate candidate : ranked)
      if (candidate != literal
          && (candidate.sourceMask & SOURCE_WHITELIST) != 0
          && is_case_compatible_autocorrection(request, candidate)
          && !is_cold_short_all_caps_correction(request, candidate))
        return candidate;
    return null;
  }

  /** Lowercase input matching only an all-caps entry (nad -> NAD) yields to
      a decisively more frequent adjacent transposition (nad -> and), but
      not when the acronym is itself common (usb, rna, byu stay). */
  private static Candidate acronym_transposition_repair(Request request,
      Candidate literal, List<Candidate> ranked)
  {
    if (literal.learned || (literal.sourceMask & SOURCE_HUNSPELL) != 0)
      return null;
    for (Candidate candidate : ranked)
      if (is_autocorrection_candidate_text(candidate, literal)
          && is_case_compatible_autocorrection(request, candidate)
          && candidate.recognized && candidate.completeEvidence
          && candidate.editCount == 1
          && candidate.editMask == EDIT_TRANSPOSITION
          && candidate.cdictFrequency >= literal.cdictFrequency
          && (candidate.frequency8 < 0 || literal.frequency8 < 0
            || candidate.frequency8 >= literal.frequency8
              + ACRONYM_REPAIR_FREQUENCY_GAP))
        return candidate;
    return null;
  }

  private static boolean is_whitelist_text(String word)
  {
    if (word.length() == 0 || word.startsWith(" ") || word.endsWith(" ")
        || word.contains("  "))
      return false;
    for (String part : word.split(" "))
      if (!(part.codePointCount(0, part.length()) == 1
            ? is_letters_only(part) : is_word_text(part)))
        return false;
    return true;
  }

  private static Candidate regular_inflection_repair(Request request,
      Candidate literal, List<Candidate> ranked)
  {
    if (literal.recognized || literal.learned
        || !is_letters_only(request.normalized))
      return null;
    Candidate repair = validated_regular_inflection(ranked,
        regular_y_inflection_target(request.normalized), 12, 3, request);
    if (repair != null)
      return repair;
    repair = validated_regular_inflection(ranked,
        regular_es_plural_target(request.normalized), 10, 2, request);
    if (repair != null)
      return repair;
    return validated_regular_inflection(ranked,
        regular_suffix_target(request.normalized), 10, 2, request);
  }

  private static Candidate repeated_letter_omission_repair(
      Request request, Candidate literal, List<Candidate> ranked)
  {
    if (literal.recognized || literal.learned || request.codePointCount < 3)
      return null;
    Candidate bestRecognized = null;
    Candidate repeatedRepair = null;
    int strongestOtherOmissionFrequency = -1;
    for (Candidate candidate : ranked)
    {
      if (!is_autocorrection_candidate_text(candidate, literal)
          || !is_case_compatible_autocorrection(request, candidate)
          || !candidate.recognized || !candidate.completeEvidence)
        continue;
      if (bestRecognized == null)
        bestRecognized = candidate;
      if (candidate.editCount != 1 || candidate.editMask != EDIT_OMISSION)
        continue;
      if (is_repeated_letter_completion(
            request.normalized, candidate.canonical)
          && (repeatedRepair == null || candidate.cdictFrequency
            > repeatedRepair.cdictFrequency))
      {
        if (repeatedRepair != null)
          strongestOtherOmissionFrequency = Math.max(
              strongestOtherOmissionFrequency,
              repeatedRepair.cdictFrequency);
        repeatedRepair = candidate;
      }
      else
        strongestOtherOmissionFrequency = Math.max(
            strongestOtherOmissionFrequency, candidate.cdictFrequency);
    }
    if (repeatedRepair == null
        || (bestRecognized != null && repeatedRepair.cdictFrequency
          < bestRecognized.cdictFrequency)
        || repeatedRepair.cdictFrequency
          < strongestOtherOmissionFrequency + 2)
      return null;
    return repeatedRepair;
  }

  private static boolean is_repeated_letter_completion(
      String source, String target)
  {
    int[] sourcePoints = source.codePoints().toArray();
    int[] targetPoints = target.codePoints().toArray();
    if (targetPoints.length != sourcePoints.length + 1)
      return false;
    for (int skip = 0; skip < targetPoints.length; skip++)
    {
      boolean repeated = (skip > 0
          && targetPoints[skip] == targetPoints[skip - 1])
        || (skip + 1 < targetPoints.length
          && targetPoints[skip] == targetPoints[skip + 1]);
      if (!repeated)
        continue;
      boolean matches = true;
      for (int i = 0; i < sourcePoints.length; i++)
        if (sourcePoints[i] != targetPoints[i < skip ? i : i + 1])
        {
          matches = false;
          break;
        }
      if (matches)
        return true;
    }
    return false;
  }

  private static Candidate validated_regular_inflection(
      List<Candidate> ranked, String target, int minimumFrequency,
      int maximumEdits, Request request)
  {
    if (target == null)
      return null;
    for (Candidate candidate : ranked)
      if (target.equals(candidate.canonical)
          && candidate.recognized && candidate.completeEvidence
          && (((candidate.sourceMask & SOURCE_CDICT_EXACT) != 0
                && candidate.cdictFrequency >= minimumFrequency)
            || (candidate.sourceMask & SOURCE_HUNSPELL) != 0)
          && is_case_compatible_autocorrection(request, candidate)
          && candidate.editCount >= 1
          && candidate.editCount <= maximumEdits)
        return candidate;
    return null;
  }

  private static String regular_y_inflection_target(String typed)
  {
    if (typed.length() > 3 && typed.endsWith("yed"))
      return typed.substring(0, typed.length() - 3) + "ied";
    if (typed.length() > 2 && typed.endsWith("ys"))
      return typed.substring(0, typed.length() - 2) + "ies";
    return null;
  }

  private static String regular_es_plural_target(String typed)
  {
    if (typed.length() <= 2 || !typed.endsWith("s"))
      return null;
    String base = typed.substring(0, typed.length() - 1);
    if (base.endsWith("s") || base.endsWith("x") || base.endsWith("z")
        || base.endsWith("ch") || base.endsWith("sh"))
      return base + "es";
    return null;
  }

  private static String regular_suffix_target(String typed)
  {
    if (typed.length() > 4 && typed.endsWith("full"))
      return typed.substring(0, typed.length() - 1);
    if (typed.length() > 5 && typed.endsWith("yness"))
      return typed.substring(0, typed.length() - 5) + "iness";
    return null;
  }

  private static Candidate decisive_context_repair(Request request,
      Candidate literal, List<Candidate> ranked)
  {
    Candidate selected = null;
    int strongest = -1;
    boolean tied = false;
    for (Candidate candidate : ranked)
    {
      if (!is_autocorrection_candidate_text(candidate, literal)
          || !is_case_compatible_autocorrection(request, candidate)
          || !is_safe_autocorrection_edit(request, candidate)
          || is_short_prefix_completion(request, candidate)
          || is_cold_short_all_caps_correction(request, candidate)
          || candidate.editMask != EDIT_SUBSTITUTION
          || candidate.spatialQ8 > NEARBY_SUBSTITUTION_COST_Q8
          || !has_decisive_context_prior(literal, candidate))
        continue;
      if (candidate.bigramCount > strongest)
      {
        selected = candidate;
        strongest = candidate.bigramCount;
        tied = false;
      }
      else if (candidate.bigramCount == strongest)
        tied = true;
    }
    return tied ? null : selected;
  }

  private static boolean has_decisive_context_prior(Candidate literal,
      Candidate candidate)
  {
    return candidate != null && candidate.recognized
      && candidate.completeEvidence && candidate.editCount == 1
      && (candidate.sourceMask & SOURCE_CONTEXT) != 0
      && candidate.bigramCount >= 8
      && count_level(candidate.bigramCount)
        >= count_level(literal.bigramCount) + 3;
  }

  private static boolean is_short_prefix_completion(Request request,
      Candidate candidate)
  {
    if (request.codePointCount >= 4)
      return false;
    int targetLength = candidate.canonical.codePointCount(
        0, candidate.canonical.length());
    return targetLength > request.normalizedCodePointCount
      && candidate.canonical.startsWith(request.normalized);
  }

  private static boolean is_safe_autocorrection_edit(Candidate candidate)
  {
    if (candidate.editCount == 1)
      return true;
    return candidate.editCount == 2 && candidate.recognized
      && candidate.completeEvidence;
  }

  private static boolean is_safe_autocorrection_edit(Request request,
      Candidate candidate)
  {
    if (is_safe_autocorrection_edit(candidate))
      return true;
    int targetCodePoints = candidate.canonical.codePointCount(
        0, candidate.canonical.length());
    int independentProviders = SOURCE_CDICT_EXACT | SOURCE_HUNSPELL;
    return request.codePointCount >= 8 && targetCodePoints >= 8
      && candidate.editCount == 3 && candidate.recognized
      && candidate.completeEvidence
      && (candidate.sourceMask & independentProviders)
        == independentProviders;
  }

  private static int autocorrect_literal_margin_q8(Candidate candidate)
  {
    boolean clearPrimaryTwoEdit =
      candidate != null && candidate.editCount == 2
      && candidate.recognized && candidate.completeEvidence
      && (candidate.sourceMask & SOURCE_HUNSPELL_PRIMARY) != 0;
    return clearPrimaryTwoEdit ? Q8 : AUTOCORRECT_LITERAL_MARGIN_Q8;
  }

  private static boolean is_missing_negative_contraction(Request request,
      Candidate candidate)
  {
    if (candidate == null
        || (candidate.sourceMask & SOURCE_CONTRACTION) == 0
        || !candidate.recognized || candidate.editCount != 2)
      return false;
    String target = normalize_correction_text(candidate.surface)
      .replace('\u2019', '\'');
    String expected = missing_negative_contraction(
        normalize_correction_text(request.correctionSource));
    return expected != null && expected.equals(target);
  }

  private static boolean is_contextual_exact_correction(Request request,
      Candidate candidate)
  {
    if (candidate == null
        || (candidate.sourceMask & SOURCE_CONTEXTUAL_CORRECTION) == 0
        || !candidate.learned || !candidate.completeEvidence)
      return false;
    String target = normalize_correction_text(candidate.surface);
    return target.codePointCount(0, target.length()) >= 2
      && is_word_text(target)
      && PersonalizationStore.is_plausible_contextual_correction(
          request.correctionSource, target);
  }

  private static boolean is_cold_short_all_caps_correction(Request request,
      Candidate candidate)
  {
    if (casing(request.typed) != Casing.UPPER
        || candidate.exactCorrectionCount != 0)
      return false;
    return request.codePointCount <= SHORT_ALL_CAPS_CODEPOINTS
      && candidate.editCount <= 2;
  }

  private static boolean has_decisively_closer_short_competitor(
      Request request, Candidate literal, Candidate selected,
      List<Candidate> ranked)
  {
    for (Candidate candidate : ranked)
    {
      int candidateLength = candidate.canonical.codePointCount(
          0, candidate.canonical.length());
      if (candidate == selected || candidate == literal
          || candidateLength != request.normalizedCodePointCount
          || !is_autocorrection_candidate_text(candidate, literal)
          || !is_case_compatible_autocorrection(request, candidate)
          || !is_safe_autocorrection_edit(candidate)
          || is_cold_short_all_caps_correction(request, candidate))
        continue;
      if ((long)selected.spatialQ8 - candidate.spatialQ8
          >= AUTOCORRECT_SHORT_WORD_SPATIAL_GAP_Q8)
        return true;
    }
    return false;
  }


  private static Candidate primary_short_lexical_repair(Request request,
      Candidate literal, List<Candidate> ranked)
  {
    Casing requestCasing = casing(request.typed);
    if (request.codePointCount < 2 || request.codePointCount > 3
        || (requestCasing != Casing.LOWER
          && requestCasing != Casing.INITIAL)
        || literal.learned
        || (literal.sourceMask & SOURCE_CDICT_EXACT) != 0)
      return null;
    if (request.codePointCount == 2 && literal.recognized)
      for (Candidate candidate : ranked)
        if (is_autocorrection_candidate_text(candidate, literal)
            && is_case_compatible_autocorrection(request, candidate)
            && (candidate.sourceMask & SOURCE_CDICT_EXACT) != 0
            && (candidate.sourceMask & SOURCE_HUNSPELL_PRIMARY) != 0
            && candidate.completeEvidence
            && candidate.editCount == 1
            && candidate.editMask == EDIT_SUBSTITUTION
            && candidate.spatialQ8 <= NEARBY_SUBSTITUTION_COST_Q8)
          return candidate;
    Candidate selected = null;
    for (Candidate candidate : ranked)
    {
      int candidateLength = candidate.canonical.codePointCount(
          0, candidate.canonical.length());
      if (!is_autocorrection_candidate_text(candidate, literal)
          || !is_case_compatible_autocorrection(request, candidate)
          || candidateLength != request.normalizedCodePointCount
          || (candidate.sourceMask & SOURCE_CDICT_SPATIAL) == 0
          || candidate.cdictFrequency < BOUNDARY_PREVIEW_MIN_CDICT_FREQUENCY
          || !candidate.completeEvidence
          || candidate.editCount != 1
          || candidate.editMask != EDIT_SUBSTITUTION
          || candidate.spatialQ8 > NEARBY_SUBSTITUTION_COST_Q8
          || (long)literal.totalQ8 - candidate.totalQ8
            < AUTOCORRECT_LITERAL_MARGIN_Q8)
        continue;
      if (selected == null
          || ranking_total_q8(request, candidate)
            < ranking_total_q8(request, selected))
        selected = candidate;
    }
    if (selected == null)
      return null;
    long selectedScore = ranking_total_q8(request, selected);
    for (Candidate candidate : ranked)
    {
      int candidateLength = candidate.canonical.codePointCount(
          0, candidate.canonical.length());
      // A close one-edit length repair (ths: the vs this) is as much a
      // competitor as a same-length word.
      if (candidate == selected || candidate == literal
          || (candidateLength != request.normalizedCodePointCount
            && !is_complete_one_edit_length_repair(candidate))
          || !is_autocorrection_candidate_text(candidate, literal)
          || !is_case_compatible_autocorrection(request, candidate)
          || !is_safe_autocorrection_edit(candidate))
        continue;
      if (ranking_total_q8(request, candidate) - selectedScore
          < AUTOCORRECT_RUNNER_MARGIN_Q8)
        return null;
    }
    return selected;
  }

  private static Candidate clear_short_transposition(Request request,
      Candidate literal, List<Candidate> ranked)
  {
    Casing requestCasing = casing(request.typed);
    if (request.codePointCount != 3 || literal.recognized || literal.learned
        || (requestCasing != Casing.LOWER
          && requestCasing != Casing.INITIAL))
      return null;
    Candidate closest = null;
    boolean primary = false;
    for (Candidate candidate : ranked)
    {
      if (!is_autocorrection_candidate_text(candidate, literal)
          || !is_case_compatible_autocorrection(request, candidate)
          || !candidate.recognized || !candidate.completeEvidence
          || candidate.editCount != 1
          || candidate.editMask != EDIT_TRANSPOSITION
          || (candidate.sourceMask
            & (SOURCE_CDICT_SPATIAL | SOURCE_HUNSPELL)) == 0)
        continue;
      boolean candidatePrimary =
        (candidate.sourceMask & SOURCE_HUNSPELL_PRIMARY) != 0;
      if (closest == null || (candidatePrimary && !primary)
          || (candidatePrimary == primary
            && (candidate.spatialQ8 < closest.spatialQ8
              || (candidate.spatialQ8 == closest.spatialQ8
                && candidate.totalQ8 < closest.totalQ8))))
      {
        closest = candidate;
        primary = candidatePrimary;
      }
    }
    if (closest == null
        || (long)literal.totalQ8 - closest.totalQ8
          < AUTOCORRECT_LITERAL_MARGIN_Q8)
      return null;
    for (Candidate candidate : ranked)
    {
      int length = candidate.canonical.codePointCount(
          0, candidate.canonical.length());
      if (candidate == closest || candidate == literal
          || length != request.normalizedCodePointCount
          || !is_autocorrection_candidate_text(candidate, literal)
          || !is_case_compatible_autocorrection(request, candidate)
          || !is_safe_autocorrection_edit(candidate))
        continue;
      if ((long)candidate.spatialQ8 - closest.spatialQ8
          >= AUTOCORRECT_SHORT_WORD_SPATIAL_GAP_Q8)
        continue;
      if (ranking_total_q8(request, candidate)
          - ranking_total_q8(request, closest)
          < AUTOCORRECT_RUNNER_MARGIN_Q8)
        return null;
    }
    return closest;
  }

  private static boolean is_case_compatible_autocorrection(Request request,
      Candidate candidate)
  {
    if (candidate.canonical.codePointCount(
          0, candidate.canonical.length()) < 2)
      return true;
    Casing requestCasing = casing(request.typed);
    Casing candidateCasing = casing(candidate.surface);
    if (requestCasing == Casing.LOWER)
      return candidateCasing == Casing.LOWER;
    if (requestCasing == Casing.INITIAL)
      return candidateCasing != Casing.UPPER;
    return true;
  }

  private static boolean is_repeated_exact_correction(Request request,
      Candidate candidate)
  {
    if (candidate == null
        || candidate.exactCorrectionCount < PROTECTED_LITERAL_EXACT_EVENTS
        || !candidate.learned || !candidate.completeEvidence)
      return false;
    String target = normalize_correction_text(candidate.surface);
    return target.codePointCount(0, target.length()) >= 3
      && is_word_text(target)
      && PersonalizationStore.is_plausible_correction(
          request.correctionSource, target);
  }



  private static boolean is_autocorrection_source_text(Request request)
  {
    if (request.codePointCount == 1)
      return casing(request.typed) == Casing.LOWER
        && is_letters_only(request.typed);
    return is_word_text(request.typed);
  }

  private static boolean is_autocorrection_candidate_text(
      Candidate candidate, Candidate literal)
  {
    int length = candidate.canonical.codePointCount(
        0, candidate.canonical.length());
    return candidate != literal
      && !candidate.canonical.equals(literal.canonical)
      && (length >= 2 ? is_word_text(candidate.canonical)
        : "i".equals(candidate.canonical))
      && ((candidate.recognized
          && (candidate.editCount == 1 || candidate.completeEvidence))
        || candidate.learned);
  }

  private static String query_emoji(Cdict dictionary, String normalized,
      FailureState failure)
  {
    if (dictionary == null
        || normalized.codePointCount(0, normalized.length()) < 3)
      return null;
    try
    {
      Cdict.Result exact = dictionary.find(normalized);
      if (exact.found)
        return dictionary.word(exact.index);
      int[] prefix = dictionary.suffixes(exact, 1);
      return prefix.length == 0 ? null : dictionary.word(prefix[0]);
    }
    catch (IllegalStateException e)
    {
      failure.corrupt();
      return null;
    }
    catch (RuntimeException e)
    {
      failure.resource();
      return null;
    }
  }

  private static Candidate candidate_for_canonical(List<Candidate> candidates,
      String canonical)
  {
    for (Candidate candidate : candidates)
      if (candidate.canonical.equals(canonical))
        return candidate;
    return null;
  }

  private static Candidate present_candidate(Candidate candidate,
      Request request, boolean autocorrection)
  {
    if (candidate == null)
      return null;
    String surface;
    String preferredSurface = "i'm".equals(candidate.canonical)
      ? "I'm" : ("i".equals(candidate.canonical) ? "I" : candidate.surface);
    if ((candidate.sourceMask & SOURCE_LITERAL) != 0 && !autocorrection)
      surface = request.typed;
    else if (autocorrection)
      surface = match_case(request.typed, preferredSurface);
    else
      surface = display_case(request.typed, preferredSurface);
    return candidate.with_surface(surface);
  }

  private static String display_case(String typed, String surface)
  {
    if (typed.length() == 0 || surface.length() == 0)
      return surface;
    int first = typed.codePointAt(0);
    if (!Character.isUpperCase(first) && !Character.isTitleCase(first))
      return surface;
    int surfaceFirstLength = Character.charCount(surface.codePointAt(0));
    return surface.substring(0, surfaceFirstLength).toUpperCase(Locale.ROOT)
      + surface.substring(surfaceFirstLength);
  }

  private static String match_case(String typed, String surface)
  {
    Casing casing = casing(typed);
    if (casing == Casing.UPPER)
      return surface.toUpperCase(Locale.ROOT);
    if (casing == Casing.INITIAL)
      return display_case(typed, surface);
    return surface;
  }

  private static Casing casing(String word)
  {
    if (word.equals(word.toLowerCase(Locale.ROOT)))
      return Casing.LOWER;
    if (word.equals(word.toUpperCase(Locale.ROOT)))
      return Casing.UPPER;
    int first = word.codePointAt(0);
    int firstLength = Character.charCount(first);
    String tail = word.substring(firstLength);
    if ((Character.isUpperCase(first) || Character.isTitleCase(first))
        && tail.equals(tail.toLowerCase(Locale.ROOT)))
      return Casing.INITIAL;
    return Casing.MIXED;
  }

  private static boolean is_word_text(String word)
  {
    return PersonalizationStore.is_learnable(word);
  }

  private static boolean is_letters_only(String word)
  {
    if (word == null || word.length() == 0)
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

  public static String normalize(String word)
  {
    return fold_substitutions(normalize_correction_text(word));
  }

  static String normalize_correction_text(String word)
  {
    if (word == null)
      return "";
    return Normalizer.normalize(word.toLowerCase(Locale.ROOT),
        Normalizer.Form.NFC);
  }

  private static String fold_substitutions(String normalized)
  {
    StringBuilder out = null;
    for (int i = 0; i < normalized.length(); i++)
    {
      char replacement = ComposeKey.transform_char(ComposeKeyData.substitutions,
          normalized.charAt(i));
      if (replacement != 0 && replacement != normalized.charAt(i))
      {
        if (out == null)
          out = new StringBuilder(normalized);
        out.setCharAt(i, replacement);
      }
    }
    return out == null ? normalized : out.toString();
  }

  private static int count_level(int count)
  {
    if (count <= 0)
      return 0;
    int value = count == Integer.MAX_VALUE ? Integer.MAX_VALUE : count + 1;
    return Math.min(6, 31 - Integer.numberOfLeadingZeros(value));
  }

  private static int clamp_score(long value)
  {
    if (value > SCORE_LIMIT)
      return SCORE_LIMIT;
    if (value < -SCORE_LIMIT)
      return -SCORE_LIMIT;
    return (int)value;
  }

  private static int code_point_compare(String a, String b)
  {
    int ai = 0;
    int bi = 0;
    while (ai < a.length() && bi < b.length())
    {
      int ac = a.codePointAt(ai);
      int bc = b.codePointAt(bi);
      if (ac != bc)
        return ac < bc ? -1 : 1;
      ai += Character.charCount(ac);
      bi += Character.charCount(bc);
    }
    if (ai == a.length() && bi == b.length())
      return 0;
    return ai == a.length() ? -1 : 1;
  }

  private static int source_priority(int sourceMask)
  {
    if ((sourceMask & SOURCE_CDICT_EXACT) != 0)
      return 0;
    if ((sourceMask & SOURCE_CDICT_SPATIAL) != 0)
      return 1;
    if ((sourceMask & SOURCE_HUNSPELL) != 0)
      return 2;
    if ((sourceMask & SOURCE_CDICT_PREFIX) != 0)
      return 3;
    if ((sourceMask & (SOURCE_CORRECTION | SOURCE_PERSONAL
          | SOURCE_CONTEXT)) != 0)
      return 4;
    return 5;
  }

  private static final Comparator<Candidate> CANDIDATE_COMPARATOR =
    new Comparator<Candidate>()
    {
      @Override
      public int compare(Candidate a, Candidate b)
      {
        int result = Integer.compare(a.totalQ8, b.totalQ8);
        if (result != 0) return result;
        result = Integer.compare(a.spatialQ8, b.spatialQ8);
        if (result != 0) return result;
        result = Integer.compare(a.editCount, b.editCount);
        if (result != 0) return result;
        result = Integer.compare(b.cdictFrequency, a.cdictFrequency);
        if (result != 0) return result;
        result = Integer.compare(b.bigramCount, a.bigramCount);
        if (result != 0) return result;
        result = Integer.compare(b.unigramCount, a.unigramCount);
        if (result != 0) return result;
        result = Integer.compare(b.exactCorrectionCount,
            a.exactCorrectionCount);
        if (result != 0) return result;
        result = Integer.compare(b.relatedCorrectionCount,
            a.relatedCorrectionCount);
        if (result != 0) return result;
        result = Integer.compare(source_priority(a.sourceMask),
            source_priority(b.sourceMask));
        if (result != 0) return result;
        result = Integer.compare(a.providerRank, b.providerRank);
        if (result != 0) return result;
        result = code_point_compare(a.canonical, b.canonical);
        if (result != 0) return result;
        return code_point_compare(a.surface, b.surface);
      }
    };

  private static void sort_candidates_for_request(Request request,
      List<Candidate> ranked)
  {
    for (int i = 1; i < ranked.size(); i++)
    {
      Candidate candidate = ranked.get(i);
      int j = i - 1;
      while (j >= 0
          && candidate_compare_for_request(request, candidate, ranked.get(j))
            < 0)
      {
        ranked.set(j + 1, ranked.get(j));
        j--;
      }
      ranked.set(j + 1, candidate);
    }
  }

  private static int candidate_compare_for_request(Request request,
      Candidate a, Candidate b)
  {
    int result = Long.compare(ranking_total_q8(request, a),
        ranking_total_q8(request, b));
    if (result != 0)
      return result;
    result = Integer.compare(unknown_length_repair_priority(request, a),
        unknown_length_repair_priority(request, b));
    return result != 0 ? result : CANDIDATE_COMPARATOR.compare(a, b);
  }

  private static boolean has_competing_length_repair(Request request,
      Candidate literal, Candidate best, List<Candidate> ranked)
  {
    if (best == null || literal.recognized || literal.learned
        || request.codePointCount < 3
        || best.canonical.codePointCount(0, best.canonical.length())
          != request.normalizedCodePointCount
        || (best.editMask != EDIT_SUBSTITUTION
          && (best.editMask != EDIT_TRANSPOSITION
            || request.codePointCount < 4)))
      return false;
    long bestScore = ranking_total_q8(request, best);
    for (Candidate candidate : ranked)
    {
      if (candidate == best || candidate == literal
          || !is_autocorrection_candidate_text(candidate, literal)
          || !is_case_compatible_autocorrection(request, candidate)
          || !is_safe_autocorrection_edit(candidate)
          || is_short_prefix_completion(request, candidate)
          || !is_complete_one_edit_length_repair(candidate))
        continue;
      long candidateGap = ranking_total_q8(request, candidate) - bestScore;
      if (best.recognized && best.completeEvidence
          && best.cdictFrequency >= candidate.cdictFrequency
          && candidateGap >= DECISIVE_SAME_LENGTH_MARGIN_Q8
          && candidate.spatialQ8 - best.spatialQ8
            >= DECISIVE_SAME_LENGTH_MARGIN_Q8)
        continue;
      if (best.recognized && best.completeEvidence
          && best.cdictFrequency >= candidate.cdictFrequency + 4
          && candidateGap >= DECISIVE_SAME_LENGTH_MARGIN_Q8)
        continue;
      if ((best.sourceMask & SOURCE_HUNSPELL_PRIMARY) != 0
          && best.cdictFrequency >= candidate.cdictFrequency + 2)
        continue;
      if (candidateGap <= COMPETING_LENGTH_REPAIR_GAP_Q8)
        return true;
    }
    return false;
  }

  private static Candidate primary_same_length_repair_over_length_guesses(
      Request request, Candidate literal, Candidate best,
      List<Candidate> ranked)
  {
    if (best == null || literal.recognized || literal.learned
        || request.codePointCount < 4
        || !is_complete_one_edit_length_repair(best))
      return null;
    long bestScore = ranking_total_q8(request, best);
    for (Candidate candidate : ranked)
    {
      if (candidate == literal
          || !is_autocorrection_candidate_text(candidate, literal)
          || !is_case_compatible_autocorrection(request, candidate)
          || !candidate.recognized || !candidate.completeEvidence
          || (candidate.editCount != 1 && candidate.editCount != 2)
          || (long)literal.totalQ8 - candidate.totalQ8
            < AUTOCORRECT_LITERAL_MARGIN_Q8)
        continue;
      long gap = ranking_total_q8(request, candidate) - bestScore;
      if (gap < 0)
        continue;
      boolean sameLength =
        candidate.canonical.codePointCount(0, candidate.canonical.length())
          == request.normalizedCodePointCount;
      boolean sameLengthOneEditRepair = sameLength
        && candidate.editCount == 1
        && (candidate.editMask == EDIT_SUBSTITUTION
          || candidate.editMask == EDIT_TRANSPOSITION);
      boolean sameLengthTwoEditSubstitution = sameLength
        && candidate.editCount == 2
        && candidate.editMask == EDIT_SUBSTITUTION;
      if (best.editMask == EDIT_EXTRA_TAP
          && sameLengthOneEditRepair
          && (candidate.sourceMask & SOURCE_HUNSPELL_PRIMARY) != 0
          && gap <= Q8)
        return candidate;
      if (best.editMask == EDIT_EXTRA_TAP
          && request.codePointCount >= 6
          && sameLengthTwoEditSubstitution
          && candidate.cdictFrequency >= best.cdictFrequency + 3
          && gap <= AUTOCORRECT_RUNNER_MARGIN_Q8
          && (long)candidate.spatialQ8 - best.spatialQ8
            <= DECISIVE_SAME_LENGTH_MARGIN_Q8)
        return candidate;
      if (sameLengthOneEditRepair
          && candidate.cdictFrequency >= best.cdictFrequency + 4
          && gap <= 2L * Q8)
        return candidate;
      if (best.editMask == EDIT_EXTRA_TAP
          && candidate.editMask == EDIT_OMISSION
          && is_complete_one_edit_length_repair(candidate)
          && gap <= Q8)
        return candidate;
    }
    return null;
  }

  private static boolean morphology_resolves_omission_tie(Request request,
      Candidate literal, Candidate best, Candidate runner)
  {
    return request.codePointCount >= 4
      && !literal.recognized && !literal.learned
      && is_complete_one_edit_length_repair(best)
      && is_complete_one_edit_length_repair(runner)
      && best.editMask == EDIT_OMISSION
      && runner.editMask == EDIT_OMISSION
      && best.cdictFrequency >= runner.cdictFrequency
      && is_doubled_consonant_inflection(request, best)
      && !is_doubled_consonant_inflection(request, runner);
  }

  private static boolean is_doubled_consonant_inflection(Request request,
      Candidate candidate)
  {
    String typed = request.normalized;
    int suffixLength;
    if (typed.endsWith("ing"))
      suffixLength = 3;
    else if (typed.endsWith("ed"))
      suffixLength = 2;
    else
      return false;
    int stemEnd = typed.length() - suffixLength;
    if (stemEnd < 2)
      return false;
    char consonant = typed.charAt(stemEnd - 1);
    if (consonant < 'a' || consonant > 'z'
        || "aeiou".indexOf(consonant) >= 0)
      return false;
    String doubled = typed.substring(0, stemEnd) + consonant
      + typed.substring(stemEnd);
    return doubled.equals(candidate.canonical);
  }

  private static boolean has_ambiguous_length_repair_pair(Request request,
      Candidate literal, Candidate best, List<Candidate> ranked)
  {
    if (best == null || !best.recognized || best.editCount != 1
        || (best.editMask != EDIT_OMISSION
          && best.editMask != EDIT_EXTRA_TAP)
        || (best.sourceMask & SOURCE_HUNSPELL_PRIMARY) != 0)
      return false;
    int bestLength = best.canonical.codePointCount(
        0, best.canonical.length());
    long bestScore = ranking_total_q8(request, best);
    for (Candidate candidate : ranked)
    {
      if (candidate == best
          || !is_autocorrection_candidate_text(candidate, literal)
          || !is_case_compatible_autocorrection(request, candidate)
          || is_cold_short_all_caps_correction(request, candidate)
          || !candidate.recognized || candidate.editCount != 1
          || candidate.editMask != best.editMask
          || candidate.canonical.codePointCount(
            0, candidate.canonical.length()) != bestLength)
        continue;
      long gap = ranking_total_q8(request, candidate) - bestScore;
      if (gap >= 0 && gap <= Q8)
      {
        if (morphology_resolves_omission_tie(
              request, literal, best, candidate))
          continue;
        return true;
      }
    }
    return false;
  }

  private static boolean is_complete_one_edit_length_repair(
      Candidate candidate)
  {
    boolean completeSpatial =
      (candidate.sourceMask & SOURCE_CDICT_SPATIAL) != 0
      && candidate.completeEvidence;
    boolean completeProvider =
      (candidate.sourceMask & SOURCE_CDICT_EXACT) != 0
      && (candidate.sourceMask & SOURCE_HUNSPELL) != 0;
    return candidate.recognized && candidate.editCount == 1
      && (candidate.editMask == EDIT_OMISSION
        || candidate.editMask == EDIT_EXTRA_TAP)
      && (completeSpatial || completeProvider);
  }

  private static boolean provider_rank_resolves_length_tie(
      Request request, Candidate best, Candidate runner)
  {
    boolean explicitPrimary =
      (best.sourceMask & SOURCE_HUNSPELL_PRIMARY) != 0
      && (runner.sourceMask & SOURCE_HUNSPELL_PRIMARY) == 0;
    return is_complete_one_edit_length_repair(best)
      && is_complete_one_edit_length_repair(runner)
      && explicitPrimary
      && best.totalQ8 == runner.totalQ8
      && best.spatialQ8 == runner.spatialQ8
      && best.cdictFrequency == runner.cdictFrequency
      && best.providerRank < runner.providerRank;
  }

  private static boolean primary_resolves_same_length_substitution(
      Request request, Candidate best, Candidate runner)
  {
    if (request.codePointCount < 4 || best == null || runner == null
        || best.editCount != 1 || runner.editCount != 1
        || best.editMask != EDIT_SUBSTITUTION
        || runner.editMask != EDIT_SUBSTITUTION
        || !best.recognized || !best.completeEvidence
        || !runner.recognized || !runner.completeEvidence
        || (best.sourceMask & SOURCE_HUNSPELL_PRIMARY) == 0
        || (runner.sourceMask & SOURCE_HUNSPELL_PRIMARY) != 0
        || best.cdictFrequency < runner.cdictFrequency + 1
        || best.canonical.codePointCount(0, best.canonical.length())
          != request.normalizedCodePointCount
        || runner.canonical.codePointCount(0, runner.canonical.length())
          != request.normalizedCodePointCount)
      return false;
    long gap = ranking_total_q8(request, runner)
      - ranking_total_q8(request, best);
    return gap >= AUTOCORRECT_RUNNER_MARGIN_Q8
      && best.spatialQ8 <= runner.spatialQ8;
  }

  private static int unknown_length_repair_priority(Request request,
      Candidate candidate)
  {
    if (request.codePointCount < 3
        || !is_complete_one_edit_length_repair(candidate))
      return 2;
    return candidate.editMask == EDIT_OMISSION ? 0 : 1;
  }

  private static boolean omission_resolves_unknown_length_tie(
      Request request, Candidate literal, Candidate best, Candidate runner,
      List<Candidate> ranked)
  {
    if (request.codePointCount < 3
        || literal.recognized || literal.learned
        || !is_complete_one_edit_length_repair(best)
        || !is_complete_one_edit_length_repair(runner)
        || best.editMask != EDIT_OMISSION
        || runner.editMask != EDIT_EXTRA_TAP
        || best.totalQ8 != runner.totalQ8
        || best.spatialQ8 != runner.spatialQ8
        || best.cdictFrequency != runner.cdictFrequency)
      return false;
    int tiedExtraTapCandidates = 0;
    for (Candidate candidate : ranked)
    {
      if (!is_autocorrection_candidate_text(candidate, literal)
          || !is_case_compatible_autocorrection(request, candidate)
          || !is_safe_autocorrection_edit(request, candidate)
          || !is_complete_one_edit_length_repair(candidate)
          || candidate.editMask != EDIT_EXTRA_TAP)
        continue;
      if (candidate.totalQ8 == best.totalQ8
          && candidate.spatialQ8 == best.spatialQ8
          && candidate.cdictFrequency == best.cdictFrequency)
        tiedExtraTapCandidates++;
    }
    return tiedExtraTapCandidates == 1 || request.codePointCount >= 5;
  }

  private static long ranking_total_q8(Request request, Candidate candidate)
  {
    int candidateLength = candidate.canonical.codePointCount(
        0, candidate.canonical.length());
    int lengthDelta = Math.abs(candidateLength - request.normalizedCodePointCount);
    return (long)candidate.totalQ8
      + (is_complete_one_edit_length_repair(candidate) ? 0L
        : (long)LENGTH_DELTA_RANKING_PENALTY_Q8 * lengthDelta);
  }

  private static void promote_candidate(List<Candidate> ranked,
      String canonical)
  {
    for (int i = 1; i < ranked.size(); i++)
    {
      Candidate candidate = ranked.get(i);
      if (!candidate.canonical.equals(canonical))
        continue;
      for (int j = i; j > 0; j--)
        ranked.set(j, ranked.get(j - 1));
      ranked.set(0, candidate);
      return;
    }
  }

  public static final class RequestKey
  {
    public final long sessionEpoch;
    public final long requestGeneration;
    public final long wordRevision;
    public final long resourceEpoch;
    public final long layoutEpoch;
    public final long configEpoch;
    public final long personalizationEpoch;

    public RequestKey(long sessionEpoch_, long requestGeneration_,
        long wordRevision_, long resourceEpoch_, long layoutEpoch_,
        long configEpoch_, long personalizationEpoch_)
    {
      sessionEpoch = sessionEpoch_;
      requestGeneration = requestGeneration_;
      wordRevision = wordRevision_;
      resourceEpoch = resourceEpoch_;
      layoutEpoch = layoutEpoch_;
      configEpoch = configEpoch_;
      personalizationEpoch = personalizationEpoch_;
    }

    @Override
    public boolean equals(Object other)
    {
      if (this == other)
        return true;
      if (!(other instanceof RequestKey))
        return false;
      RequestKey key = (RequestKey)other;
      return sessionEpoch == key.sessionEpoch
        && requestGeneration == key.requestGeneration
        && wordRevision == key.wordRevision
        && resourceEpoch == key.resourceEpoch
        && layoutEpoch == key.layoutEpoch
        && configEpoch == key.configEpoch
        && personalizationEpoch == key.personalizationEpoch;
    }

    @Override
    public int hashCode()
    {
      long hash = sessionEpoch;
      hash = hash * 31 + requestGeneration;
      hash = hash * 31 + wordRevision;
      hash = hash * 31 + resourceEpoch;
      hash = hash * 31 + layoutEpoch;
      hash = hash * 31 + configEpoch;
      hash = hash * 31 + personalizationEpoch;
      return (int)(hash ^ (hash >>> 32));
    }
  }

  public static final class DecoderConfig
  {
    public final boolean suggestionsEnabled;
    public final boolean autocorrectEnabled;
    public final boolean showCandidates;
    public final boolean useTypingAssistance;

    public DecoderConfig(boolean suggestionsEnabled_, boolean autocorrectEnabled_,
        boolean showCandidates_, boolean useTypingAssistance_)
    {
      suggestionsEnabled = suggestionsEnabled_;
      autocorrectEnabled = autocorrectEnabled_;
      showCandidates = showCandidates_;
      useTypingAssistance = useTypingAssistance_;
    }

    @Override
    public boolean equals(Object other)
    {
      if (this == other)
        return true;
      if (!(other instanceof DecoderConfig))
        return false;
      DecoderConfig config = (DecoderConfig)other;
      return suggestionsEnabled == config.suggestionsEnabled
        && autocorrectEnabled == config.autocorrectEnabled
        && showCandidates == config.showCandidates
        && useTypingAssistance == config.useTypingAssistance;
    }

    @Override
    public int hashCode()
    {
      int hash = suggestionsEnabled ? 1 : 0;
      hash = hash * 31 + (autocorrectEnabled ? 1 : 0);
      hash = hash * 31 + (showCandidates ? 1 : 0);
      hash = hash * 31 + (useTypingAssistance ? 1 : 0);
      return hash;
    }
  }

  public static final class Request
  {
    public final RequestKey key;
    public final String typed;
    public final String normalized;
    final String correctionSource;
    public final int codePointCount;
    public final int normalizedCodePointCount;
    public final TouchTrace.Snapshot touches;
    public final Geometry geometry;
    public final DecoderConfig config;

    private final int[] _typedCodePoints;
    private final int[] _touchIndexes;

    public Request(RequestKey key_, CurrentlyTypedWord.Snapshot word,
        Geometry geometry_, DecoderConfig config_)
    {
      this(key_, require_word(word).word, word.touches, geometry_, config_);
    }

    public Request(RequestKey key_, String typed_, TouchTrace.Snapshot touches_,
        Geometry geometry_, DecoderConfig config_)
    {
      if (key_ == null || typed_ == null || config_ == null)
        throw new IllegalArgumentException("request fields must not be null");
      key = key_;
      typed = typed_;
      correctionSource = normalize_correction_text(typed_);
      normalized = fold_substitutions(correctionSource);
      codePointCount = typed_.codePointCount(0, typed_.length());
      normalizedCodePointCount = normalized.codePointCount(0,
          normalized.length());
      if (normalizedCodePointCount <= MAX_WORD_CODEPOINTS)
      {
        _typedCodePoints = normalized.codePoints().toArray();
        _touchIndexes = touch_indexes(typed_, codePointCount,
            normalizedCodePointCount);
      }
      else
      {
        _typedCodePoints = new int[0];
        _touchIndexes = new int[0];
      }
      touches = touches_;
      geometry = geometry_ == null ? Geometry.from(null) : geometry_;
      config = config_;
    }

    private static CurrentlyTypedWord.Snapshot require_word(
        CurrentlyTypedWord.Snapshot word)
    {
      if (word == null)
        throw new IllegalArgumentException("word snapshot must not be null");
      return word;
    }

    public int[] typed_code_points()
    {
      return _typedCodePoints.clone();
    }

    private int[] code_points_internal()
    {
      return _typedCodePoints;
    }

    private int[] touch_indexes_internal()
    {
      return _touchIndexes;
    }

    private static int[] touch_indexes(String typed, int typedCount,
        int normalizedCount)
    {
      int[] indexes = new int[normalizedCount];
      if (typedCount == normalizedCount)
      {
        for (int i = 0; i < normalizedCount; i++)
          indexes[i] = i;
        return indexes;
      }
      Arrays.fill(indexes, -1);
      int normalizedIndex = 0;
      int typedIndex = 0;
      for (int offset = 0; offset < typed.length(); typedIndex++)
      {
        int codePoint = typed.codePointAt(offset);
        String lowered = new String(Character.toChars(codePoint))
          .toLowerCase(Locale.ROOT);
        int emitted = lowered.codePointCount(0, lowered.length());
        if (normalizedIndex < normalizedCount)
          indexes[normalizedIndex] = typedIndex;
        normalizedIndex += emitted;
        offset += Character.charCount(codePoint);
      }
      if (normalizedIndex != normalizedCount)
      {
        Arrays.fill(indexes, -1);
        for (int i = 0; i < Math.min(typedCount, normalizedCount); i++)
          indexes[i] = i;
      }
      return indexes;
    }
  }

  /** Immutable geometry extracted from the exact layout shown to the user. */
  public static final class Geometry
  {
    private final int[] _symbolCodePoints;
    private final float[] _x;
    private final float[] _y;

    private Geometry(int[] symbolCodePoints, float[] x, float[] y)
    {
      _symbolCodePoints = symbolCodePoints.clone();
      _x = x.clone();
      _y = y.clone();
    }

    public static Geometry from(KeyboardData layout)
    {
      Map<Integer, Pos> positions = new HashMap<Integer, Pos>();
      if (layout != null)
      {
        float y = 0f;
        for (KeyboardData.Row row : layout.rows)
        {
          y += row.shift;
          float x = Math.max(0f, (layout.keysWidth - row.keysWidth) / 2f);
          for (KeyboardData.Key key : row.keys)
          {
            x += key.shift;
            float centerX = (x + key.width / 2f) * 2f;
            float centerY = (y + row.height / 2f) * 2f;
            for (int i = 0; i < key.keys.length; i++)
              add_key_position(positions, key.getKeyValue(i), centerX, centerY);
            x += key.width;
          }
          y += row.height;
        }
      }
      if (positions.isEmpty())
        add_qwerty_positions(positions);

      List<Integer> symbols = new ArrayList<Integer>(positions.keySet());
      Collections.sort(symbols);
      int count = Math.min(symbols.size(), Cdict.CDICT_SPATIAL_MAX_SYMBOLS);
      int[] codePoints = new int[count];
      float[] xs = new float[count];
      float[] ys = new float[count];
      for (int i = 0; i < count; i++)
      {
        codePoints[i] = symbols.get(i);
        Pos position = positions.get(symbols.get(i));
        xs[i] = position.x;
        ys[i] = position.y;
      }
      return new Geometry(codePoints, xs, ys);
    }

    public int[] symbol_code_points()
    {
      return _symbolCodePoints.clone();
    }

    int fixed_substitution_cost_q8(int sourceCodePoint,
        int targetCodePoint)
    {
      return substitution_cost_q8(sourceCodePoint, targetCodePoint, -1, null,
          0f, 0f);
    }

    public Cdict.SpatialQuery spatial_query(long sequence,
        int[] typedCodePoints, TouchTrace.Snapshot touches)
    {
      if (typedCodePoints == null || typedCodePoints.length == 0
          || typedCodePoints.length > MAX_WORD_CODEPOINTS)
        throw new IllegalArgumentException("spatial input must contain 1..48 code points");
      return cost_table(typedCodePoints.clone(), null, touches, 0f, 0f)
        .spatial_query(sequence);
    }

    private CostTable cost_table(int[] typedCodePoints, int[] touchIndexes,
        TouchTrace.Snapshot touches)
    {
      return cost_table(typedCodePoints, touchIndexes, touches, 0f, 0f);
    }

    private CostTable cost_table(int[] typedCodePoints, int[] touchIndexes,
        TouchTrace.Snapshot touches, float touchOffsetX, float touchOffsetY)
    {
      int[] table = new int[typedCodePoints.length * _symbolCodePoints.length];
      for (int i = 0; i < typedCodePoints.length; i++)
      {
        int touchIndex = touchIndexes == null ? i : touchIndexes[i];
        for (int j = 0; j < _symbolCodePoints.length; j++)
          table[i * _symbolCodePoints.length + j] = substitution_cost_q8(
              typedCodePoints[i], _symbolCodePoints[j], touchIndex, touches,
              touchOffsetX, touchOffsetY);
      }
      return new CostTable(typedCodePoints, _symbolCodePoints, table);
    }

    private int substitution_cost_q8(int typedCodePoint,
        int candidateCodePoint, int typedIndex, TouchTrace.Snapshot touches,
        float touchOffsetX, float touchOffsetY)
    {
      if (typedCodePoint == candidateCodePoint)
        return 0;
      int typedPosition = Arrays.binarySearch(_symbolCodePoints,
          typedCodePoint);
      int candidatePosition = Arrays.binarySearch(_symbolCodePoints,
          candidateCodePoint);
      if (typedPosition < 0 || candidatePosition < 0)
        return UNKNOWN_SUBSTITUTION_COST_Q8;

      TouchTrace.Entry touch = touches != null && typedIndex >= 0
          && typedIndex < touches.size() ? touches.get(typedIndex) : null;
      if (valid_touch(touch))
      {
        float unitX = Math.max(touch.keyWidth / 2f, 1f);
        float unitY = Math.max(touch.keyHeight / 2f, 1f);
        float candidateCenterX = touch.keyCenterX
          + (_x[candidatePosition] - _x[typedPosition]) * unitX;
        float candidateCenterY = touch.keyCenterY
          + (_y[candidatePosition] - _y[typedPosition]) * unitY;
        float dx = (touch.touchX - touch.keyWidth * touchOffsetX
            - candidateCenterX) / Math.max(touch.keyWidth, 1f);
        float dy = (touch.touchY - touch.keyHeight * touchOffsetY
            - candidateCenterY) / Math.max(touch.keyHeight, 1f);
        return proximity_cost_q8(dx * dx + dy * dy);
      }

      // Layout coordinates use two units per key.
      float dx = _x[typedPosition] - _x[candidatePosition];
      float dy = _y[typedPosition] - _y[candidatePosition];
      return proximity_cost_q8((dx * dx + dy * dy) / 4.0);
    }

    /** [squaredKeys] is the squared distance in key widths. */
    private static int proximity_cost_q8(double squaredKeys)
    {
      return round_q8(Math.min(64.0,
          PROXIMITY_BASE_COST + PROXIMITY_DISTANCE_COST * squaredKeys));
    }

    private static boolean valid_touch(TouchTrace.Entry touch)
    {
      return touch != null && touch.keyWidth > 0f && touch.keyHeight > 0f
        && finite(touch.touchX) && finite(touch.touchY)
        && finite(touch.keyCenterX) && finite(touch.keyCenterY)
        && finite(touch.keyWidth) && finite(touch.keyHeight);
    }

    private static boolean finite(float value)
    {
      return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private static int round_q8(double value)
    {
      long rounded = Math.round(value * Q8);
      if (rounded < 0)
        return 0;
      return (int)Math.min(rounded, MAX_SUBSTITUTION_COST_Q8);
    }

    private static void add_key_position(Map<Integer, Pos> positions,
        KeyValue value, float x, float y)
    {
      if (value == null || value.getKind() != KeyValue.Kind.Char)
        return;
      int codePoint = Character.toLowerCase((int)value.getChar());
      if (!Character.isLetter(codePoint)
          || positions.containsKey(Integer.valueOf(codePoint)))
        return;
      positions.put(Integer.valueOf(codePoint), new Pos(x, y));
    }

    private static void add_qwerty_positions(Map<Integer, Pos> positions)
    {
      add_qwerty_row(positions, "qwertyuiop", 0, 0);
      add_qwerty_row(positions, "asdfghjkl", 2, 1);
      add_qwerty_row(positions, "zxcvbnm", 4, 3);
    }

    private static void add_qwerty_row(Map<Integer, Pos> positions,
        String row, int y, int xOffset)
    {
      for (int i = 0; i < row.length(); i++)
        positions.put(Integer.valueOf(row.charAt(i)),
            new Pos(xOffset + i * 2, y));
    }
  }

  private static final class Pos
  {
    final float x;
    final float y;

    Pos(float x_, float y_)
    {
      x = x_;
      y = y_;
    }
  }

  private static final class CostTable
  {
    final int[] typed;
    final int[] symbols;
    final int[] substitutions;

    CostTable(int[] typed_, int[] symbols_, int[] substitutions_)
    {
      typed = typed_.clone();
      symbols = symbols_.clone();
      substitutions = substitutions_;
    }

    int substitution(int typedIndex, int candidateCodePoint)
    {
      if (typed[typedIndex] == candidateCodePoint)
        return 0;
      int symbol = Arrays.binarySearch(symbols, candidateCodePoint);
      if (symbol < 0)
        return UNKNOWN_SUBSTITUTION_COST_Q8;
      return substitutions[typedIndex * symbols.length + symbol];
    }

    Cdict.SpatialQuery spatial_query(long sequence)
    {
      return new Cdict.SpatialQuery(sequence, typed, symbols, substitutions,
          Cdict.CDICT_SPATIAL_MAX_EDITS, MAX_NATIVE_RESULTS,
          OMISSION_COST_Q8, EXTRA_TAP_COST_Q8, TRANSPOSITION_COST_Q8,
          UNKNOWN_SUBSTITUTION_COST_Q8, BEAM_COST_Q8,
          Cdict.CDICT_SPATIAL_MAX_EXPANSIONS);
    }

    Cdict.SpatialQuery nearby_spatial_query(long sequence)
    {
      int[] nearbySubstitutions = substitutions.clone();
      for (int i = 0; i < nearbySubstitutions.length; i++)
        if (nearbySubstitutions[i] > NEARBY_SUBSTITUTION_COST_Q8)
          nearbySubstitutions[i] = 0xffff;
      return new Cdict.SpatialQuery(sequence, typed, symbols,
          nearbySubstitutions, Cdict.CDICT_SPATIAL_MAX_EDITS,
          MAX_NATIVE_RESULTS, 0xffff, 0xffff, 0xffff, 0xffff,
          2 * NEARBY_SUBSTITUTION_COST_Q8 + Q8,
          NEARBY_SPATIAL_EXPANSIONS);
    }
  }

  /** Allocation-bounded three-row optimal-string-alignment scorer. */
  private static final class Scorer
  {
    final CostTable costs;
    final int[][] values = new int[3][MAX_CANDIDATE_CODEPOINTS + 1];
    final int[][] edits = new int[3][MAX_CANDIDATE_CODEPOINTS + 1];
    final int[][] masks = new int[3][MAX_CANDIDATE_CODEPOINTS + 1];

    Scorer(CostTable costs_)
    {
      costs = costs_;
    }

    Score score(int[] candidate)
    {
      int m = candidate.length;
      int previousPrevious = 0;
      int previous = 1;
      int current = 2;
      for (int j = 0; j <= m; j++)
      {
        values[previous][j] = saturating_multiply(j, OMISSION_COST_Q8);
        edits[previous][j] = j;
        masks[previous][j] = j == 0 ? 0 : EDIT_OMISSION;
      }

      for (int i = 1; i <= costs.typed.length; i++)
      {
        values[current][0] = saturating_multiply(i, EXTRA_TAP_COST_Q8);
        edits[current][0] = i;
        masks[current][0] = EDIT_EXTRA_TAP;
        for (int j = 1; j <= m; j++)
        {
          boolean exact = costs.typed[i - 1] == candidate[j - 1];
          int substitution = exact ? 0
            : costs.substitution(i - 1, candidate[j - 1]);
          set_path(current, j, previous, j - 1, substitution,
              exact ? 0 : 1, exact ? 0 : EDIT_SUBSTITUTION);
          consider_path(current, j, previous, j,
              i > 1 && costs.typed[i - 1] == costs.typed[i - 2]
                ? SAME_LETTER_EXTRA_TAP_COST_Q8 : EXTRA_TAP_COST_Q8, 1,
              EDIT_EXTRA_TAP);
          consider_path(current, j, current, j - 1,
              j > 1 && candidate[j - 1] == candidate[j - 2]
                ? SAME_LETTER_OMISSION_COST_Q8 : OMISSION_COST_Q8, 1,
              EDIT_OMISSION);
          if (i > 1 && j > 1
              && costs.typed[i - 2] == candidate[j - 1]
              && costs.typed[i - 1] == candidate[j - 2])
          {
            int transposition = saturating_add(
                costs.substitution(i - 2, candidate[j - 1]),
                costs.substitution(i - 1, candidate[j - 2]));
            transposition = saturating_add(transposition,
                TRANSPOSITION_COST_Q8);
            consider_path(current, j, previousPrevious, j - 2,
                transposition, 1, EDIT_TRANSPOSITION);
          }
        }
        int swap = previousPrevious;
        previousPrevious = previous;
        previous = current;
        current = swap;
      }
      return new Score(values[previous][m], edits[previous][m],
          masks[previous][m]);
    }

    private void set_path(int dstRow, int dstColumn, int srcRow,
        int srcColumn, int addedCost, int addedEdits, int addedMask)
    {
      values[dstRow][dstColumn] = saturating_add(
          values[srcRow][srcColumn], addedCost);
      edits[dstRow][dstColumn] = edits[srcRow][srcColumn] + addedEdits;
      masks[dstRow][dstColumn] = masks[srcRow][srcColumn] | addedMask;
    }

    private void consider_path(int dstRow, int dstColumn, int srcRow,
        int srcColumn, int addedCost, int addedEdits, int addedMask)
    {
      int value = saturating_add(values[srcRow][srcColumn], addedCost);
      int editCount = edits[srcRow][srcColumn] + addedEdits;
      int mask = masks[srcRow][srcColumn] | addedMask;
      if (value < values[dstRow][dstColumn]
          || (value == values[dstRow][dstColumn]
            && (editCount < edits[dstRow][dstColumn]
              || (editCount == edits[dstRow][dstColumn]
                && mask < masks[dstRow][dstColumn]))))
      {
        values[dstRow][dstColumn] = value;
        edits[dstRow][dstColumn] = editCount;
        masks[dstRow][dstColumn] = mask;
      }
    }

    private static int saturating_multiply(int a, int b)
    {
      return (int)Math.min((long)a * b, SCORE_LIMIT);
    }

    private static int saturating_add(int a, int b)
    {
      return (int)Math.min((long)a + b, SCORE_LIMIT);
    }
  }

  private static final class Score
  {
    static final Score EXACT = new Score(0, 0, 0);

    final int spatialQ8;
    final int editCount;
    final int editMask;

    Score(int spatialQ8_, int editCount_, int editMask_)
    {
      spatialQ8 = spatialQ8_;
      editCount = editCount_;
      editMask = editMask_;
    }
  }

  private static final class Accumulator
  {
    final String canonical;
    final int[] codePoints;
    final String firstSurface;

    int sourceMask;
    boolean literal;
    boolean learned;
    boolean spatialComplete;
    String literalSurface;
    String exactSurface;
    String hunspellSurface;
    String spatialSurface;
    String prefixSurface;
    String personalSurface;
    String correctionSurface;
    int exactIndex = -1;
    int spatialIndex = -1;
    int prefixIndex = -1;
    int cdictFrequency;
    /** Source unigram frequency 0..255, or -1 without a unigram table. */
    int frequency8 = -1;
    int spatialRank = Integer.MAX_VALUE;
    int hunspellRank = Integer.MAX_VALUE;
    int prefixRank = Integer.MAX_VALUE;
    int personalRank = Integer.MAX_VALUE;
    int correctionRank = Integer.MAX_VALUE;
    int unigramCount;
    int bigramCount;
    int exactCorrectionCount;
    int relatedCorrectionCount;

    Accumulator(String canonical_, int[] codePoints_, String firstSurface_)
    {
      canonical = canonical_;
      codePoints = codePoints_.clone();
      firstSurface = firstSurface_;
    }

    void set_cdict_exact(int index, int frequency)
    {
      exactIndex = index;
      cdictFrequency = Math.max(cdictFrequency, frequency);
    }

    void set_spatial(String surface, int rank, int index, int frequency,
        boolean complete)
    {
      if (rank < spatialRank)
      {
        spatialRank = rank;
        spatialSurface = surface;
        spatialIndex = index;
      }
      cdictFrequency = Math.max(cdictFrequency, frequency);
      spatialComplete |= complete;
    }

    void set_hunspell(String surface, int rank)
    {
      if (rank < hunspellRank)
      {
        hunspellRank = rank;
        hunspellSurface = surface;
      }
    }

    void set_prefix(String surface, int rank, int index, int frequency)
    {
      if (rank < prefixRank)
      {
        prefixRank = rank;
        prefixSurface = surface;
        prefixIndex = index;
      }
      cdictFrequency = Math.max(cdictFrequency, frequency);
    }

    void set_personal(String surface, int rank)
    {
      if (rank < personalRank)
      {
        personalRank = rank;
        personalSurface = surface;
      }
    }

    void set_correction(String surface, int rank, int exactCount,
        int relatedCount)
    {
      if (rank < correctionRank)
      {
        correctionRank = rank;
        correctionSurface = surface;
      }
      exactCorrectionCount = Math.max(exactCorrectionCount, exactCount);
      relatedCorrectionCount = Math.max(relatedCorrectionCount, relatedCount);
    }

    Candidate to_candidate(Score score)
    {
      boolean recognized = (sourceMask & (SOURCE_CDICT_EXACT
          | SOURCE_CDICT_SPATIAL | SOURCE_CDICT_PREFIX | SOURCE_HUNSPELL)) != 0;
      boolean learnedEvidence = learned || exactCorrectionCount > 0
        || relatedCorrectionCount > 0;
      boolean completeEvidence = (sourceMask & (SOURCE_CDICT_EXACT
          | SOURCE_CDICT_PREFIX | SOURCE_HUNSPELL)) != 0 || learnedEvidence
        || spatialComplete;
      int sourcePenalty = source_penalty();
      int unknownLiteralPenalty = 0;
      if (literal && !recognized && !learnedEvidence)
      {
        int excessLength = Math.max(0,
            codePoints.length - UNKNOWN_LITERAL_LENGTH_THRESHOLD);
        unknownLiteralPenalty = UNKNOWN_SUBSTITUTION_COST_Q8
          + excessLength * UNKNOWN_LITERAL_LENGTH_PENALTY_Q8;
      }
      int correctionWeight = Math.min(MAX_CORRECTION_WEIGHT,
          exactCorrectionCount * 2 + relatedCorrectionCount);
      int contractionBonus = (sourceMask & SOURCE_CONTRACTION) != 0
        ? OMISSION_COST_Q8 : 0;
      long total = (long)score.spatialQ8 + sourcePenalty
        + unknownLiteralPenalty - frequency_bonus()
        - (long)256 * count_level(unigramCount)
        - (long)384 * count_level(bigramCount)
        - (long)CORRECTION_BONUS_Q8 * correctionWeight
        - contractionBonus;
      Role role = literal && !recognized ? Role.ENTERED_LITERAL : Role.WORD;
      return new Candidate(canonical, surface(), sourceMask, cdict_index(),
          cdictFrequency, provider_rank(), unigramCount, bigramCount,
          exactCorrectionCount, relatedCorrectionCount, correctionWeight,
          score.spatialQ8, score.editCount, score.editMask,
          clamp_score(total), role, recognized, learnedEvidence,
          completeEvidence, frequency8);
    }

    private long frequency_bonus()
    {
      return frequency8 >= 0 ? (long)UNIGRAM_FREQUENCY_WEIGHT * frequency8
        : (long)CDICT_LEVEL_WEIGHT * cdictFrequency;
    }

    private String surface()
    {
      if (exactCorrectionCount > 0 && correctionSurface != null)
        return correctionSurface;
      if (literalSurface != null)
        return literalSurface;
      if (exactSurface != null)
        return exactSurface;
      if (hunspellSurface != null)
        return hunspellSurface;
      if (spatialSurface != null)
        return spatialSurface;
      if (prefixSurface != null)
        return prefixSurface;
      if (correctionSurface != null)
        return correctionSurface;
      if (personalSurface != null)
        return personalSurface;
      return firstSurface;
    }

    private int cdict_index()
    {
      if (exactIndex >= 0)
        return exactIndex;
      if (spatialIndex >= 0)
        return spatialIndex;
      return prefixIndex;
    }

    private int source_penalty()
    {
      int best = Integer.MAX_VALUE;
      if ((sourceMask & (SOURCE_CDICT_EXACT | SOURCE_CDICT_SPATIAL
            | SOURCE_LITERAL)) != 0)
        best = 0;
      if ((sourceMask & SOURCE_HUNSPELL) != 0)
        best = Math.min(best, 256 + 64 * Math.min(hunspellRank, 31));
      if ((sourceMask & SOURCE_CDICT_PREFIX) != 0)
        best = Math.min(best, 512 + 64 * Math.min(prefixRank, 31));
      if ((sourceMask & SOURCE_PERSONAL) != 0)
        best = Math.min(best, 768);
      if ((sourceMask & SOURCE_CORRECTION) != 0)
        best = Math.min(best, 768);
      return best == Integer.MAX_VALUE ? 768 : best;
    }

    private int provider_rank()
    {
      if ((sourceMask & SOURCE_CDICT_EXACT) != 0)
        return hunspellRank == Integer.MAX_VALUE ? 0 : hunspellRank;
      if ((sourceMask & SOURCE_CDICT_SPATIAL) != 0)
        return spatialRank == Integer.MAX_VALUE ? 0 : spatialRank;
      if ((sourceMask & SOURCE_HUNSPELL) != 0)
        return hunspellRank == Integer.MAX_VALUE ? 0 : hunspellRank;
      if ((sourceMask & SOURCE_CDICT_PREFIX) != 0)
        return prefixRank == Integer.MAX_VALUE ? 0 : prefixRank;
      if ((sourceMask & SOURCE_CORRECTION) != 0)
        return correctionRank == Integer.MAX_VALUE ? 0 : correctionRank;
      if ((sourceMask & SOURCE_PERSONAL) != 0)
        return personalRank == Integer.MAX_VALUE ? 0 : personalRank;
      return 0;
    }
  }

  public static final class Candidate
  {
    public final String canonical;
    public final String surface;
    public final int sourceMask;
    public final int cdictIndex;
    public final int cdictFrequency;
    public final int providerRank;
    public final int unigramCount;
    public final int bigramCount;
    public final int exactCorrectionCount;
    public final int relatedCorrectionCount;
    public final int correctionWeight;
    public final int spatialQ8;
    public final int editCount;
    public final int editMask;
    public final int totalQ8;
    public final Role role;
    public final boolean recognized;
    public final boolean learned;
    public final boolean completeEvidence;
    /** Pack unigram frequency 0..255, or -1 without a unigram table. */
    public final int frequency8;

    private Candidate(String canonical_, String surface_, int sourceMask_,
        int cdictIndex_, int cdictFrequency_, int providerRank_,
        int unigramCount_, int bigramCount_, int exactCorrectionCount_,
        int relatedCorrectionCount_, int correctionWeight_, int spatialQ8_,
        int editCount_, int editMask_, int totalQ8_, Role role_,
        boolean recognized_, boolean learned_, boolean completeEvidence_)
    {
      this(canonical_, surface_, sourceMask_, cdictIndex_, cdictFrequency_,
          providerRank_, unigramCount_, bigramCount_, exactCorrectionCount_,
          relatedCorrectionCount_, correctionWeight_, spatialQ8_, editCount_,
          editMask_, totalQ8_, role_, recognized_, learned_,
          completeEvidence_, -1);
    }

    private Candidate(String canonical_, String surface_, int sourceMask_,
        int cdictIndex_, int cdictFrequency_, int providerRank_,
        int unigramCount_, int bigramCount_, int exactCorrectionCount_,
        int relatedCorrectionCount_, int correctionWeight_, int spatialQ8_,
        int editCount_, int editMask_, int totalQ8_, Role role_,
        boolean recognized_, boolean learned_, boolean completeEvidence_,
        int frequency8_)
    {
      frequency8 = frequency8_;
      canonical = canonical_;
      surface = surface_;
      sourceMask = sourceMask_;
      cdictIndex = cdictIndex_;
      cdictFrequency = cdictFrequency_;
      providerRank = providerRank_;
      unigramCount = unigramCount_;
      bigramCount = bigramCount_;
      exactCorrectionCount = exactCorrectionCount_;
      relatedCorrectionCount = relatedCorrectionCount_;
      correctionWeight = correctionWeight_;
      spatialQ8 = spatialQ8_;
      editCount = editCount_;
      editMask = editMask_;
      totalQ8 = totalQ8_;
      role = role_;
      recognized = recognized_;
      learned = learned_;
      completeEvidence = completeEvidence_;
    }

    private Candidate with_surface(String surface_)
    {
      if (surface.equals(surface_))
        return this;
      return new Candidate(canonical, surface_, sourceMask, cdictIndex,
          cdictFrequency, providerRank, unigramCount, bigramCount,
          exactCorrectionCount, relatedCorrectionCount, correctionWeight,
          spatialQ8, editCount, editMask, totalQ8, role, recognized, learned,
          completeEvidence, frequency8);
    }
  }
  public static final class Result
  {
    public final RequestKey key;
    public final String queriedWord;
    public final String emoji;
    public final Candidate literal;
    public final Candidate autocorrection;
    public final boolean autocorrectionComplete;
    public final boolean complete;
    public final Failure failure;

    private final Candidate[] _words;

    private Result(RequestKey key_, String queriedWord_, Candidate[] words_,
        String emoji_, Candidate literal_, Candidate autocorrection_,
        boolean autocorrectionComplete_, boolean complete_, Failure failure_)
    {
      key = key_;
      queriedWord = queriedWord_;
      _words = words_.clone();
      emoji = emoji_;
      literal = literal_;
      autocorrection = autocorrection_;
      autocorrectionComplete = autocorrectionComplete_;
      complete = complete_;
      failure = failure_;
    }

    public Candidate[] words()
    {
      return _words.clone();
    }
  }

  public static enum Role
  {
    WORD,
    ENTERED_LITERAL,
    NEXT_WORD
  }

  public static enum Failure
  {
    NONE,
    RESOURCE,
    NATIVE_TRUNCATED,
    NATIVE_CORRUPT
  }

  private static enum Casing
  {
    LOWER,
    INITIAL,
    UPPER,
    MIXED
  }

  private static final class FailureState
  {
    Failure failure;

    FailureState(boolean resourceFailure)
    {
      failure = resourceFailure ? Failure.RESOURCE : Failure.NONE;
    }

    void resource()
    {
      if (failure != Failure.NATIVE_CORRUPT)
        failure = Failure.RESOURCE;
    }

    void truncated()
    {
      if (failure == Failure.NONE)
        failure = Failure.NATIVE_TRUNCATED;
    }

    void corrupt()
    {
      failure = Failure.NATIVE_CORRUPT;
    }

    boolean complete()
    {
      return failure == Failure.NONE;
    }
  }
}
