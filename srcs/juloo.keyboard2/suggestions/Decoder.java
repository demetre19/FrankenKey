package juloo.keyboard2.suggestions;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
  public static final int MAX_VISIBLE_WORDS = 3;

  public static final int SOURCE_LITERAL = 1;
  public static final int SOURCE_CDICT_EXACT = 1 << 1;
  public static final int SOURCE_CDICT_SPATIAL = 1 << 2;
  public static final int SOURCE_CDICT_PREFIX = 1 << 3;
  public static final int SOURCE_HUNSPELL = 1 << 4;
  public static final int SOURCE_PERSONAL = 1 << 5;
  public static final int SOURCE_CONTEXT = 1 << 6;
  public static final int SOURCE_CORRECTION = 1 << 7;

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
  private static final int OMISSION_COST_Q8 = 6 * Q8;
  private static final int EXTRA_TAP_COST_Q8 = 6 * Q8;
  private static final int TRANSPOSITION_COST_Q8 = 2 * Q8;
  private static final int UNKNOWN_SUBSTITUTION_COST_Q8 = 12 * Q8;
  private static final int MAX_SUBSTITUTION_COST_Q8 = 64 * Q8;
  private static final int BEAM_COST_Q8 = 48 * Q8;
  private static final double TOUCH_SUBSTITUTION_COST_SCALE = 2.0;
  private static final int AUTOCORRECT_LITERAL_MARGIN_Q8 = 2 * Q8;
  private static final int AUTOCORRECT_RUNNER_MARGIN_Q8 = Q8 / 2;
  private static final int AUTOCORRECT_SHORT_WORD_SPATIAL_GAP_Q8 = 2 * Q8;
  private static final int LENGTH_DELTA_RANKING_PENALTY_Q8 = 4 * Q8;
  private static final int NEARBY_SUBSTITUTION_COST_Q8 = 8 * Q8;
  private static final int UNKNOWN_LITERAL_LENGTH_THRESHOLD = 5;
  private static final int UNKNOWN_LITERAL_LENGTH_PENALTY_Q8 = 3 * Q8 / 2;
  private static final int NEARBY_SPATIAL_EXPANSIONS = 4096;
  private static final int SCORE_LIMIT = 0x3fffffff;
  private static final int MAX_CORRECTION_WEIGHT = 8;
  private static final int CORRECTION_BONUS_Q8 = 3 * Q8;
  private static final int PROTECTED_LITERAL_EXACT_EVENTS = 4;

  public Decoder() {}

  /** Decode one immutable request. Each non-literal canonical candidate is
      collected first, then scored exactly once. */
  public Result decode(Request request, Cdict dictionary, Cdict emojiDictionary,
      Hunspell hunspell, PersonalizationStore personalization,
      boolean resourceFailure)
  {
    if (request == null)
      throw new IllegalArgumentException("request must not be null");

    FailureState failure = new FailureState(resourceFailure);
    boolean displayEnabled = request.config.useTypingAssistance
      && request.config.suggestionsEnabled && request.config.showCandidates;
    boolean autocorrectEnabled = request.config.useTypingAssistance
      && request.config.autocorrectEnabled;

    if (request.normalized.length() == 0)
      return decode_next_words(request, personalization, displayEnabled, failure);

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
          personalization, failure);
      return new Result(request.key, request.typed, new Candidate[0], null,
          literalCandidate, null, failure.complete(), failure.failure);
    }

    boolean boundedInput = inputLength <= MAX_WORD_CODEPOINTS
      && request.normalizedCodePointCount <= MAX_WORD_CODEPOINTS;
    boolean generateCandidates = inputLength >= 2 && boundedInput;
    CostTable costs = null;

    if (dictionary != null)
    {
      try
      {
        Cdict.Result exact = dictionary.find(request.normalized);
        if (exact.found)
        {
          literal.sourceMask |= SOURCE_CDICT_EXACT;
          literal.exactSurface = dictionary.word(exact.index);
          literal.set_cdict_exact(exact.index, dictionary.freq(exact.index));
        }

        if (generateCandidates)
        {
          costs = request.geometry.cost_table(request.code_points_internal(),
              request.touch_indexes_internal(), request.touches);
          Cdict.SpatialResult spatial = dictionary.spatial(
              costs.spatial_query(request.key.requestGeneration));
          collect_spatial(dictionary, spatial, request, merged, failure);

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

    if (costs == null && generateCandidates && merged.size() > 1)
      costs = request.geometry.cost_table(request.code_points_internal(),
          request.touch_indexes_internal(), request.touches);

    String previousWord = null;
    if (personalization != null)
    {
      try
      {
        previousWord = personalization.previous_word();
      }
      catch (RuntimeException e)
      {
        failure.resource();
      }
    }

    List<Candidate> ranked = rank_candidates(request, merged, costs,
        personalization, previousWord, failure);
    Candidate literalCandidate = candidate_for_canonical(ranked,
        request.normalized);
    Candidate autocorrection = choose_autocorrection(request, ranked,
        literalCandidate, autocorrectEnabled, failure.failure);
    if (autocorrection == null && dictionary != null && costs != null
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
          previousWord, failure);
      literalCandidate = candidate_for_canonical(ranked, request.normalized);
      autocorrection = choose_autocorrection(request, ranked,
          literalCandidate, autocorrectEnabled, failure.failure);
    }
    if (autocorrection == null && hunspell != null && generateCandidates
        && literalCandidate != null && !literalCandidate.recognized)
    {
      try
      {
        collect_hunspell_suggestions(hunspell, request.normalized, merged);
      }
      catch (RuntimeException e)
      {
        failure.resource();
      }
      ranked = rank_candidates(request, merged, costs, personalization,
          previousWord, failure);
      literalCandidate = candidate_for_canonical(ranked, request.normalized);
      autocorrection = choose_autocorrection(request, ranked,
          literalCandidate, autocorrectEnabled, failure.failure);
    }
    String emoji = displayEnabled && boundedInput
      ? query_emoji(emojiDictionary, request.normalized, failure) : null;
    if (autocorrection != null)
      promote_candidate(ranked, autocorrection.canonical);
    Candidate[] words = displayEnabled && inputLength >= 2
      ? visible_words(ranked, request) : new Candidate[0];

    return new Result(request.key, request.typed, words, emoji,
        present_candidate(literalCandidate, request, false), autocorrection,
        failure.complete(), failure.failure);
  }

  private static List<Candidate> rank_candidates(Request request,
      Map<String, Accumulator> merged, CostTable costs,
      PersonalizationStore personalization, String previousWord,
      FailureState failure)
  {
    Scorer scorer = costs == null ? null : new Scorer(costs);
    List<Candidate> ranked = new ArrayList<Candidate>(merged.size());
    for (Accumulator accumulator : merged.values())
    {
      add_personalization_metadata(accumulator, personalization, previousWord,
          failure);
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

  private static Result decode_next_words(Request request,
      PersonalizationStore personalization, boolean displayEnabled,
      FailureState failure)
  {
    if (!displayEnabled || personalization == null)
      return new Result(request.key, request.typed, new Candidate[0], null,
          null, null, failure.complete(), failure.failure);
    try
    {
      List<PersonalizationStore.ScoredWord> matches =
        personalization.suggest_next_words_with_counts(MAX_VISIBLE_WORDS);
      int count = Math.min(matches.size(), MAX_VISIBLE_WORDS);
      Candidate[] words = new Candidate[count];
      for (int i = 0; i < count; i++)
      {
        PersonalizationStore.ScoredWord match = matches.get(i);
        String canonical = normalize(match.word);
        int unigram = personalization.word_count(canonical);
        int sourceMask = SOURCE_CONTEXT;
        if (unigram > 0)
          sourceMask |= SOURCE_PERSONAL;
        int total = clamp_score(-(long)256 * count_level(unigram)
            - (long)384 * count_level(match.count));
        words[i] = new Candidate(canonical, match.word, sourceMask, -1, 0, i,
            unigram, match.count, 0, 0, 0, 0, 0, 0, total,
            Role.NEXT_WORD, false, unigram > 0, true);
      }
      return new Result(request.key, request.typed, words, null, null, null,
          failure.complete(), failure.failure);
    }
    catch (RuntimeException e)
    {
      failure.resource();
      return new Result(request.key, request.typed, new Candidate[0], null,
          null, null, failure.complete(), failure.failure);
    }
  }

  private static Candidate finish_literal_only(Accumulator literal,
      Request request, PersonalizationStore personalization,
      FailureState failure)
  {
    add_personalization_metadata(literal, personalization,
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

  private static void collect_hunspell_suggestions(Hunspell hunspell,
      String normalized, Map<String, Accumulator> merged)
  {
    String[] suggestions = hunspell.suggest(normalized, MAX_HUNSPELL_RESULTS);
    int count = Math.min(suggestions.length, MAX_HUNSPELL_RESULTS);
    for (int i = 0; i < count; i++)
    {
      Accumulator candidate = accumulator_for(merged, suggestions[i]);
      if (candidate == null)
        continue;
      candidate.sourceMask |= SOURCE_HUNSPELL;
      candidate.set_hunspell(suggestions[i], i);
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

  private static void add_personalization_metadata(Accumulator candidate,
      PersonalizationStore personalization, String previousWord,
      FailureState failure)
  {
    if (personalization == null)
      return;
    try
    {
      candidate.unigramCount = personalization.word_count(candidate.canonical);
      candidate.bigramCount = previousWord == null ? 0
        : personalization.bigram_count(previousWord, candidate.canonical);
      if (candidate.unigramCount > 0)
      {
        candidate.sourceMask |= SOURCE_PERSONAL;
        candidate.learned = true;
        if (candidate.personalRank == Integer.MAX_VALUE)
          candidate.personalRank = 0;
      }
      if (candidate.bigramCount > 0)
        candidate.sourceMask |= SOURCE_CONTEXT;
    }
    catch (RuntimeException e)
    {
      failure.resource();
    }
  }

  private static Candidate[] visible_words(List<Candidate> ranked,
      Request request)
  {
    Candidate[] out = new Candidate[Math.min(MAX_VISIBLE_WORDS, ranked.size())];
    int count = 0;
    for (Candidate candidate : ranked)
    {
      out[count++] = present_candidate(candidate, request, false);
      if (count == MAX_VISIBLE_WORDS)
        break;
    }
    if (count == out.length)
      return out;
    return Arrays.copyOf(out, count);
  }

  private static Candidate choose_autocorrection(Request request,
      List<Candidate> ranked, Candidate literal, boolean enabled,
      Failure failure)
  {
    if (!enabled || literal == null || request.codePointCount < 3
        || request.codePointCount > MAX_WORD_CODEPOINTS
        || !is_plain_word(request.typed)
        || casing(request.typed) == Casing.MIXED
        || (failure != Failure.NONE && failure != Failure.NATIVE_TRUNCATED))
      return null;

    Candidate repeatedExact = null;
    for (Candidate candidate : ranked)
    {
      if (!is_repeated_exact_correction(request, candidate))
        continue;
      if (repeatedExact == null || candidate.exactCorrectionCount
          > repeatedExact.exactCorrectionCount)
        repeatedExact = candidate;
    }
    if (repeatedExact != null)
      return present_candidate(repeatedExact, request, true);

    boolean protectedLiteral = literal.recognized || literal.learned;
    Candidate best = null;
    Candidate runner = null;
    Candidate closestSpatial = null;
    for (Candidate candidate : ranked)
    {
      if (!is_autocorrection_candidate_text(candidate, literal)
          || !is_safe_autocorrection_edit(candidate))
        continue;
      if (closestSpatial == null
          || candidate.spatialQ8 < closestSpatial.spatialQ8)
        closestSpatial = candidate;
      if (best == null)
        best = candidate;
      else if (runner == null)
        runner = candidate;
    }
    if (best != null && request.codePointCount == 3
        && closestSpatial != null && closestSpatial != best
        && (long)best.spatialQ8 - closestSpatial.spatialQ8
          >= AUTOCORRECT_SHORT_WORD_SPATIAL_GAP_Q8)
      return null;
    if (best == null
        || (long)literal.totalQ8 - best.totalQ8
          < AUTOCORRECT_LITERAL_MARGIN_Q8
        || (runner != null && ranking_total_q8(request, runner)
            - ranking_total_q8(request, best)
          < AUTOCORRECT_RUNNER_MARGIN_Q8))
      return null;
    if (protectedLiteral
        && best.exactCorrectionCount < PROTECTED_LITERAL_EXACT_EVENTS)
      return null;
    return present_candidate(best, request, true);
  }

  private static boolean is_safe_autocorrection_edit(Candidate candidate)
  {
    if (candidate.editCount == 1)
      return true;
    return candidate.editCount == 2 && candidate.recognized
      && candidate.completeEvidence;
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
      && is_plain_word(target)
      && PersonalizationStore.is_plausible_correction(
          request.correctionSource, target);
  }

  private static boolean is_autocorrection_candidate_text(
      Candidate candidate, Candidate literal)
  {
    return candidate != literal
      && !candidate.canonical.equals(literal.canonical)
      && candidate.canonical.codePointCount(0, candidate.canonical.length())
        >= 3
      && is_plain_word(candidate.canonical)
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
    if ((candidate.sourceMask & SOURCE_LITERAL) != 0 && !autocorrection)
      surface = request.typed;
    else if (autocorrection)
      surface = match_case(request.typed, candidate.surface);
    else
      surface = display_case(request.typed, candidate.surface);
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

  private static boolean is_plain_word(String word)
  {
    if (word == null || word.length() == 0)
      return false;
    for (int i = 0; i < word.length();)
    {
      int codePoint = word.codePointAt(i);
      if (!Character.isLetter(codePoint))
        return false;
      i += Character.charCount(codePoint);
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
    return result != 0 ? result : CANDIDATE_COMPARATOR.compare(a, b);
  }

  private static long ranking_total_q8(Request request, Candidate candidate)
  {
    int candidateLength = candidate.canonical.codePointCount(
        0, candidate.canonical.length());
    int lengthDelta = Math.abs(candidateLength - request.normalizedCodePointCount);
    return (long)candidate.totalQ8
      + (long)LENGTH_DELTA_RANKING_PENALTY_Q8 * lengthDelta;
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
      return substitution_cost_q8(sourceCodePoint, targetCodePoint, -1, null);
    }

    public Cdict.SpatialQuery spatial_query(long sequence,
        int[] typedCodePoints, TouchTrace.Snapshot touches)
    {
      if (typedCodePoints == null || typedCodePoints.length == 0
          || typedCodePoints.length > MAX_WORD_CODEPOINTS)
        throw new IllegalArgumentException("spatial input must contain 1..48 code points");
      return cost_table(typedCodePoints.clone(), null, touches)
        .spatial_query(sequence);
    }

    private CostTable cost_table(int[] typedCodePoints, int[] touchIndexes,
        TouchTrace.Snapshot touches)
    {
      int[] table = new int[typedCodePoints.length * _symbolCodePoints.length];
      for (int i = 0; i < typedCodePoints.length; i++)
      {
        int touchIndex = touchIndexes == null ? i : touchIndexes[i];
        for (int j = 0; j < _symbolCodePoints.length; j++)
          table[i * _symbolCodePoints.length + j] = substitution_cost_q8(
              typedCodePoints[i], _symbolCodePoints[j], touchIndex, touches);
      }
      return new CostTable(typedCodePoints, _symbolCodePoints, table);
    }

    private int substitution_cost_q8(int typedCodePoint,
        int candidateCodePoint, int typedIndex, TouchTrace.Snapshot touches)
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
        float sigmaX = Math.max(touch.keyWidth * 0.55f, 1f);
        float sigmaY = Math.max(touch.keyHeight * 0.55f, 1f);
        float dx = (touch.touchX - candidateCenterX) / sigmaX;
        float dy = (touch.touchY - candidateCenterY) / sigmaY;
        return round_q8(Math.min(64.0,
            TOUCH_SUBSTITUTION_COST_SCALE * (dx * dx + dy * dy)));
      }

      float dx = _x[typedPosition] - _x[candidatePosition];
      float dy = _y[typedPosition] - _y[candidatePosition];
      return round_q8(Math.min(64.0, dx * dx + dy * dy));
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
          consider_path(current, j, previous, j, EXTRA_TAP_COST_Q8, 1,
              EDIT_EXTRA_TAP);
          consider_path(current, j, current, j - 1, OMISSION_COST_Q8, 1,
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
      long total = (long)score.spatialQ8 + sourcePenalty
        + unknownLiteralPenalty - (long)128 * cdictFrequency
        - (long)256 * count_level(unigramCount)
        - (long)384 * count_level(bigramCount)
        - (long)CORRECTION_BONUS_Q8 * correctionWeight;
      Role role = literal && !recognized ? Role.ENTERED_LITERAL : Role.WORD;
      return new Candidate(canonical, surface(), sourceMask, cdict_index(),
          cdictFrequency, provider_rank(), unigramCount, bigramCount,
          exactCorrectionCount, relatedCorrectionCount, correctionWeight,
          score.spatialQ8, score.editCount, score.editMask,
          clamp_score(total), role, recognized, learnedEvidence,
          completeEvidence);
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
        return 0;
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

    private Candidate(String canonical_, String surface_, int sourceMask_,
        int cdictIndex_, int cdictFrequency_, int providerRank_,
        int unigramCount_, int bigramCount_, int exactCorrectionCount_,
        int relatedCorrectionCount_, int correctionWeight_, int spatialQ8_,
        int editCount_, int editMask_, int totalQ8_, Role role_,
        boolean recognized_, boolean learned_, boolean completeEvidence_)
    {
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
          completeEvidence);
    }
  }

  public static final class Result
  {
    public final RequestKey key;
    public final String queriedWord;
    public final String emoji;
    public final Candidate literal;
    public final Candidate autocorrection;
    public final boolean complete;
    public final Failure failure;

    private final Candidate[] _words;

    private Result(RequestKey key_, String queriedWord_, Candidate[] words_,
        String emoji_, Candidate literal_, Candidate autocorrection_,
        boolean complete_, Failure failure_)
    {
      key = key_;
      queriedWord = queriedWord_;
      _words = words_.clone();
      emoji = emoji_;
      literal = literal_;
      autocorrection = autocorrection_;
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
