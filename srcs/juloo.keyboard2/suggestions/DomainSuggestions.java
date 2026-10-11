package juloo.keyboard2.suggestions;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Gboard-style email domain completion after "@".
 *
 * A static, frequency-ordered domain list is pinned while the text before the
 * cursor ends with "@" plus a partial domain suffix: no suffix yet pins the
 * top domains, "g" pins "gmail.com" first, "gmail.c" keeps "gmail.com" on top.
 * The [CandidateRole.EMAIL_DOMAIN] role commits by replacing the whole
 * "@…" span with the full local-part + domain, never appending.
 *
 * The scan is bounded to [MAX_SCAN] UTF-16 units and pure CPU; it satisfies
 * the [CandidateSource] budget (≤2 ms, no I/O). Detection is restricted to
 * [EditorContext.EditorClass.EMAIL] editors so prose keeps its ordinary
 * suggestions, and nothing here ever touches the learned-words store.
 */
public final class DomainSuggestions implements CandidateSource
{
  /** Ranked by rough global frequency; order is the tie-break display order. */
  public static final String[] DOMAINS = new String[] {
      "gmail.com", "outlook.com", "hotmail.com", "yahoo.com", "icloud.com",
      "proton.me", "protonmail.com", "aol.com", "live.com", "me.com" };

  /** How far before the cursor the "@" scan may look. Generous for any real
      email address while keeping the per-decode scan trivially bounded. */
  public static final int MAX_SCAN = 128;

  private static final Candidate[] EMPTY = new Candidate[0];

  @Override
  public Candidate[] pinned(Decoder.Request request, EditorContext ctx)
  {
    if (ctx == null || ctx.textBeforeCursor == null
        || ctx.editorClass != EditorContext.EditorClass.EMAIL)
      return EMPTY;
    int span = domain_span_start(ctx.textBeforeCursor);
    if (span < 0)
      return EMPTY;
    String prefix = ctx.textBeforeCursor.subSequence(
        span, ctx.textBeforeCursor.length()).toString()
        .toLowerCase(Locale.ROOT);
    ArrayList<Candidate> out = new ArrayList<Candidate>();
    for (String domain : DOMAINS)
      if (domain.equals(prefix))
      {
        out.add(new Candidate(domain, CandidateRole.EMAIL_DOMAIN));
        break;
      }
    for (String domain : DOMAINS)
    {
      if (out.size() >= Decoder.MAX_VISIBLE_WORDS)
        break;
      if (domain.startsWith(prefix) && !domain.equals(prefix))
        out.add(new Candidate(domain, CandidateRole.EMAIL_DOMAIN));
    }
    return out.toArray(new Candidate[0]);
  }

  /**
   * Return the index of the first character after "@" when [text] ends with
   * "@" followed by a valid partial domain suffix, or -1. The suffix accepts
   * ASCII letters, digits, hyphens and single dot separators so "gmail." and
   * "gmail.c" still complete. Anything else — whitespace, a second "@", a
   * leading/doubled dot — means the token is not a completing domain.
   */
  public static int domain_span_start(CharSequence text)
  {
    if (text == null)
      return -1;
    int i = text.length();
    while (i > 0 && i >= text.length() - MAX_SCAN
        && is_domain_char(text.charAt(i - 1)))
      --i;
    if (i == 0 || text.charAt(i - 1) != '@')
      return -1;
    if (i - 2 >= 0 && !is_email_boundary_char(text.charAt(i - 2)))
      return -1;
    return segment_is_valid(text, i) ? i : -1;
  }

  /** The suffix after "@" must be labels joined by single dots; a trailing
      dot is fine (the user is mid-label). */
  private static boolean segment_is_valid(CharSequence text, int start)
  {
    boolean label_start = true;
    for (int i = start; i < text.length(); ++i)
    {
      char c = text.charAt(i);
      if (c == '.')
      {
        if (label_start)
          return false;
        label_start = true;
      }
      else if (is_domain_char(c))
        label_start = false;
      else
        return false;
    }
    return true;
  }

  private static boolean is_domain_char(char c)
  {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
        || (c >= '0' && c <= '9') || c == '.' || c == '-';
  }

  /** The character before "@" must plausibly end a local part (a second "@"
      or a boundary dot would make the domain ambiguous). */
  private static boolean is_email_boundary_char(char c)
  {
    return c != '@' && c != '.';
  }
}
