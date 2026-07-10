package juloo.cdict;

public class Cdict
{
  public static final int CDICT_SPATIAL_MAX_INPUT = 48;
  public static final int CDICT_SPATIAL_MAX_SYMBOLS = 96;
  public static final int CDICT_SPATIAL_MAX_RESULTS = 16;
  public static final int CDICT_SPATIAL_MAX_EDITS = 2;
  public static final int CDICT_SPATIAL_MAX_EXPANSIONS = 32768;
  public static final int CDICT_SPATIAL_COST_INF = 0x3fffffff;

  public static final int CDICT_SPATIAL_OK = 0;
  public static final int CDICT_SPATIAL_TRUNCATED = 1;
  public static final int CDICT_SPATIAL_INVALID_ARGUMENT = 2;
  public static final int CDICT_SPATIAL_INVALID_UTF8 = 3;
  public static final int CDICT_SPATIAL_CORRUPT_DICTIONARY = 4;

  public static final int CDICT_EDIT_SUBSTITUTION = 0x01;
  public static final int CDICT_EDIT_OMISSION = 0x02;
  public static final int CDICT_EDIT_EXTRA_TAP = 0x04;
  public static final int CDICT_EDIT_TRANSPOSITION = 0x08;

  public static final class Result
  {
    /** Whether the word is recognized. */
    public final boolean found;
    /** Unique index of the word within the dictionary.
      Ranges from 0 to the number of words in the dictionary - 1. Can be used
      to lookup word metadata. */
    public final int index;
    /** Internal pointer used by the suffixes function. */
    public final long prefix_ptr;
    /** Index before alias resolution. Used internally. */
    public final int original_index;
    /** Native dictionary owner identity. */
    private final long owner;

    // Constructed from C code.
    private Result()
    { found = false; index = -1; prefix_ptr = 0; original_index = -1; owner = 0; }
  }
  /** Immutable, bounded coordinate-first native query. */
  public static final class SpatialQuery
  {
    public final long sequence;
    public final int[] literalCodePoints;
    public final int[] symbolCodePoints;
    public final int[] substitutionCostsQ8;
    public final int maxEdits;
    public final int maxResults;
    public final int omissionCostQ8;
    public final int extraTapCostQ8;
    public final int transpositionCostQ8;
    public final int unknownSubstitutionCostQ8;
    public final int beamCostQ8;
    public final int expansionBudget;

    public SpatialQuery(long sequence_, int[] literalCodePoints_,
        int[] symbolCodePoints_, int[] substitutionCostsQ8_, int maxEdits_,
        int maxResults_, int omissionCostQ8_, int extraTapCostQ8_,
        int transpositionCostQ8_, int unknownSubstitutionCostQ8_,
        int beamCostQ8_, int expansionBudget_)
    {
      if (literalCodePoints_ == null || symbolCodePoints_ == null ||
          substitutionCostsQ8_ == null)
        throw new IllegalArgumentException("spatial arrays must not be null");
      if (literalCodePoints_.length > CDICT_SPATIAL_MAX_INPUT)
        throw new IllegalArgumentException("too many spatial input code points");
      if (symbolCodePoints_.length > CDICT_SPATIAL_MAX_SYMBOLS ||
          (literalCodePoints_.length != 0 && symbolCodePoints_.length == 0))
        throw new IllegalArgumentException("invalid spatial symbol count");
      long costCount = (long)literalCodePoints_.length *
        symbolCodePoints_.length;
      if (costCount != substitutionCostsQ8_.length)
        throw new IllegalArgumentException("spatial cost matrix shape mismatch");
      if (maxEdits_ < 0 || maxEdits_ > CDICT_SPATIAL_MAX_EDITS)
        throw new IllegalArgumentException("invalid spatial edit limit");
      if (maxResults_ < 1 || maxResults_ > CDICT_SPATIAL_MAX_RESULTS)
        throw new IllegalArgumentException("invalid spatial result limit");
      check_q8_16("omissionCostQ8", omissionCostQ8_);
      check_q8_16("extraTapCostQ8", extraTapCostQ8_);
      check_q8_16("transpositionCostQ8", transpositionCostQ8_);
      check_q8_16("unknownSubstitutionCostQ8",
          unknownSubstitutionCostQ8_);
      if (beamCostQ8_ < 0 || beamCostQ8_ >= CDICT_SPATIAL_COST_INF)
        throw new IllegalArgumentException("invalid spatial beam cost");
      if (expansionBudget_ < 1 ||
          expansionBudget_ > CDICT_SPATIAL_MAX_EXPANSIONS)
        throw new IllegalArgumentException("invalid spatial expansion budget");
      for (int codePoint : literalCodePoints_)
        check_scalar(codePoint);
      for (int i = 0; i < symbolCodePoints_.length; i++)
      {
        check_scalar(symbolCodePoints_[i]);
        for (int j = 0; j < i; j++)
          if (symbolCodePoints_[i] == symbolCodePoints_[j])
            throw new IllegalArgumentException("spatial symbols must be unique");
      }
      for (int cost : substitutionCostsQ8_)
        check_q8_16("substitutionCostsQ8", cost);

      sequence = sequence_;
      literalCodePoints = literalCodePoints_.clone();
      symbolCodePoints = symbolCodePoints_.clone();
      substitutionCostsQ8 = substitutionCostsQ8_.clone();
      maxEdits = maxEdits_;
      maxResults = maxResults_;
      omissionCostQ8 = omissionCostQ8_;
      extraTapCostQ8 = extraTapCostQ8_;
      transpositionCostQ8 = transpositionCostQ8_;
      unknownSubstitutionCostQ8 = unknownSubstitutionCostQ8_;
      beamCostQ8 = beamCostQ8_;
      expansionBudget = expansionBudget_;
    }

    private static void check_scalar(int codePoint)
    {
      if (codePoint < 0 || codePoint > Character.MAX_CODE_POINT ||
          (codePoint >= Character.MIN_SURROGATE &&
            codePoint <= Character.MAX_SURROGATE))
        throw new IllegalArgumentException("invalid Unicode scalar");
    }

    private static void check_q8_16(String field, int value)
    {
      if (value < 0 || value > 0xffff)
        throw new IllegalArgumentException("invalid " + field);
    }
  }

  public static final class SpatialCandidate
  {
    public final int index;
    public final int spatialCostQ8;
    public final int editCount;
    public final int editMask;
    public final int frequency;

    private SpatialCandidate()
    {
      index = -1;
      spatialCostQ8 = 0;
      editCount = 0;
      editMask = 0;
      frequency = 0;
    }
  }

  public static final class SpatialResult
  {
    public final long sequence;
    public final int status;
    public final SpatialCandidate[] candidates;

    private SpatialResult()
    {
      sequence = 0;
      status = CDICT_SPATIAL_INVALID_ARGUMENT;
      candidates = new SpatialCandidate[0];
    }
  }


  /** Dictionary name. */
  public final String name;

  /** Load a dictionary file stored in a string. The dictionaries contained in
      the file are returned as an array. They can be distinguished using the
      [name] field, the main dictionary is named "main". The data is copied and
      not modified. Use [cdict-tool] to construct the dictionary. */
  public static Cdict[] of_bytes(byte[] data) throws ConstructionError
  {
    if (data == null)
      throw new IllegalArgumentException("dictionary bytes must not be null");
    return new Header(of_bytes_native(data)).get_dicts();
  }

  /** Check whether the given word is recognized by the dictionary. Never
      return null. */
  public Result find(String word)
  {
    if (word == null)
      throw new IllegalArgumentException("word must not be null");
    return find_native(_ptr, word);
  }

  /** Coordinate-first bounded dictionary candidate recall. */
  public SpatialResult spatial(SpatialQuery query)
  {
    if (query == null)
      throw new IllegalArgumentException("spatial query must not be null");
    SpatialResult result = spatial_native(_ptr, query);
    // Keep the native buffer owner strongly reachable through the JNI call.
    if (_header._ptr == 0)
      throw new IllegalStateException("dictionary is closed");
    return result;
  }

  /** Lookup the frequency of a word. The frequency ranges from 0 to 15
      included and is used to sort words returned by bounded dictionary
      searches. A higher value means a more frequent word in usage. [index]
      is a word index returned by [find], [suffixes], [distance], or [spatial]. */
  public int freq(int index)
  { return freq_native(_ptr, index); }

  /** Lookup the word at a given index returned by find, suffixes, or spatial. */
  public String word(int index)
  { return word_native(_ptr, index); }

  /** List words that starts with the query passed to [find]. This can be called
      even if [result.found] is false. The returned array cannot contain more than
      [count] elements but might be smaller. */
  public int[] suffixes(Result result, int count)
  {
    if (result == null)
      throw new IllegalArgumentException("result must not be null");
    check_result_count(count);
    return suffixes_native(_ptr, result, count);
  }


  /** Version of the dictionary's format. Dictionaries built for a different
      version are not compatible. */
  public static native int format_version();

  private static void check_result_count(int count)
  {
    if (count < 1 || count > CDICT_SPATIAL_MAX_RESULTS)
      throw new IllegalArgumentException("result count must be in 1..16");
  }

  /** Thrown during construction. */
  public static class ConstructionError extends Exception
  {
    public ConstructionError(String msg) { super(msg); }
  }

  /** Internals */

  // A pointer to C allocated memory.
  private final long _ptr;
  private final Header _header;

  private Cdict(String n, long p, Header h)
  {
    name = n;
    _ptr = p;
    _header = h;
  }

  static
  {
    System.loadLibrary("cdict_java");
    init();
  }

  private static class Header
  {
    private long _ptr;
    private Header(long p) { _ptr = p; }

    public synchronized Cdict[] get_dicts()
    {
      if (_ptr == 0)
        throw new IllegalStateException("dictionary header is closed");
      return get_dicts_native(_ptr);
    }
    public native Cdict[] get_dicts_native(long header_ptr);
    @Override
    protected synchronized void finalize() throws Throwable
    {
      long pointer = _ptr;
      _ptr = 0;
      if (pointer != 0)
        finalize_header(pointer);
      super.finalize();
    }
  }

  private static native void init();
  private static native long of_bytes_native(byte[] data);
  private static native void finalize_header(long header);
  private static native Result find_native(long dict, String word);
  private static native int freq_native(long dict, int index);
  private static native String word_native(long dict, int index);
  private static native int[] suffixes_native(long dict, Result result,
      int count);
  private static native SpatialResult spatial_native(long dict,
      SpatialQuery query);
}
