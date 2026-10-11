package juloo.keyboard2.suggestions;

/**
 * Worker-side contributor of pinned strip candidates.
 *
 * Sources are invoked on the [SharedDecoder] worker with the immutable decode
 * request and the [EditorContext] captured on the main thread when that
 * request was made. A source must return in under 2 ms and must not perform
 * I/O after warm-up. Returned candidates are merged ahead of the ranked words
 * in the READY presentation, deduplicated by NFC surface, within the existing
 * six-word strip bound.
 */
public interface CandidateSource
{
  /**
   * A pinned, editor-independent candidate. [Decoder.Candidate] is not used
   * here because its score fields only have meaning inside the decoder; pinned
   * candidates render by [CandidateRole] and always sort before ranked words.
   */
  public static final class Candidate
  {
    public final String surface;
    public final CandidateRole role;

    public Candidate(String surface_, CandidateRole role_)
    {
      if (surface_ == null || role_ == null)
        throw new IllegalArgumentException(
            "pinned candidate fields must not be null");
      surface = surface_;
      role = role_;
    }
  }

  /**
   * Return the candidates this source pins for [request] in [ctx], or an
   * empty array. Called for every decode; must be fast and allocation-light.
   */
  public Candidate[] pinned(Decoder.Request request, EditorContext ctx);
}
