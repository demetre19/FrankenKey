package juloo.keyboard2.suggestions.email;

import java.text.Normalizer;

/**
 * Email token parsing (B-F1).
 *
 * Parses the token ending at the cursor from {@code textBeforeCursor} and
 * reports whether it opens an email context. The scan runs backward from the
 * cursor through address characters until whitespace, start of text, or a
 * token delimiter, up to 254 UTF-16 units (no valid address is longer). A
 * character that is not an address character ends the email context entirely:
 * the real token is larger than the scanned suffix, so offering candidates
 * would corrupt text — same reasoning as the mid-token rule.
 *
 * {@code hasAt} is true only with exactly one {@code @} and a non-empty local
 * part before it. A token with two {@code @} produces no context. A token
 * starting with {@code @} (a mention such as {@code @john}) is never an email
 * context. If {@code textAfterCursor} begins with an address character the
 * cursor is mid-token and no context is reported, so text is never corrupted.
 *
 * Allowed local characters: {@code A-Z a-z 0-9 . _ % + - '}. Allowed domain
 * characters: letters, digits, {@code -}, {@code .}. Anything else ends the
 * email context. The apostrophe is an allowed local character, so it does not
 * delimit the scan even though it delimits generic word tokens.
 */
public final class EmailToken
{
  /** The scanned token, possibly empty; ends at the cursor. */
  public final String token;
  /** Text before the {@code @}, or the whole token when there is no {@code @}. */
  public final String localPart;
  /** Exactly one {@code @} with a non-empty local part before it. */
  public final boolean hasAt;
  /** Text between the {@code @} and the cursor; empty when there is no {@code @}. */
  public final String domainPrefix;
  /** Offset of the token start in the editor text. */
  public final int tokenStartOffset;

  private EmailToken(String token, String localPart, boolean hasAt,
      String domainPrefix, int tokenStartOffset)
  {
    this.token = token;
    this.localPart = localPart;
    this.hasAt = hasAt;
    this.domainPrefix = domainPrefix;
    this.tokenStartOffset = tokenStartOffset;
  }

  /** Maximum token length to scan, in UTF-16 units; also the address cap. */
  public static final int MAX_TOKEN_UNITS = 254;

  /**
   * Parse the token ending at the cursor. Returns null when there is no email
   * context: mid-token cursor, mention, two {@code @} tokens, a
   * non-address character inside the token, or a token longer than 254 units.
   * An empty token (cursor at whitespace or text start) is a context with
   * {@code hasAt == false}, so an email field can list top addresses.
   */
  public static EmailToken parse(String textBeforeCursor, String textAfterCursor)
  {
    if (textBeforeCursor == null || textAfterCursor == null)
      return null;
    if (!textAfterCursor.isEmpty() && isAddressChar(textAfterCursor.charAt(0)))
      return null; // mid-token: the token continues past the cursor
    int end = textBeforeCursor.length();
    int start = end;
    int units = 0;
    while (start > 0)
    {
      char c = textBeforeCursor.charAt(start - 1);
      if (Character.isWhitespace(c) || isTokenDelimiter(c))
        break;
      if (!isAddressChar(c))
        return null; // ends the email context
      start--;
      units++;
      if (units > MAX_TOKEN_UNITS)
        return null; // longer than any valid address
    }
    String token = textBeforeCursor.substring(start, end);
    if (token.startsWith("@"))
      return null; // mention, never an email context
    int at = -1;
    for (int i = 0; i < token.length(); i++)
    {
      char c = token.charAt(i);
      if (c == '@')
      {
        if (at >= 0)
          return null; // two '@': no candidates
        at = i;
      }
      else if (!(at >= 0 ? isDomainChar(c) : isLocalChar(c)))
        return null; // ends the email context
    }
    boolean hasAt = at >= 0; // local part is non-empty: a leading '@' returned above
    String localPart = at >= 0 ? token.substring(0, at) : token;
    String domainPrefix = at >= 0 ? token.substring(at + 1) : "";
    return new EmailToken(token, localPart, hasAt, domainPrefix, start);
  }

  /** Whitespace and token delimiters end the token scan; see class comment. */
  static boolean isTokenDelimiter(char c)
  {
    switch (c)
    {
      case ',': case ';': case '<': case '>': case '(': case ')':
      case '[': case ']': case '"':
        return true;
      default:
        return false;
    }
  }

  static boolean isAddressChar(char c)
  {
    return c == '@' || isLocalChar(c) || isDomainChar(c);
  }

  /** {@code A-Z a-z 0-9 . _ % + - '} */
  static boolean isLocalChar(char c)
  {
    return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
      || c == '.' || c == '_' || c == '%' || c == '+' || c == '-' || c == '\'';
  }

  /** Letters, digits, {@code -}, {@code .} (Unicode letters and digits). */
  static boolean isDomainChar(char c)
  {
    return Character.isLetter(c) || Character.isDigit(c) || c == '-' || c == '.';
  }

  /** NFC-normalize a local part; case is kept as typed. */
  static String normalizeLocalPart(String local)
  {
    return Normalizer.normalize(local, Normalizer.Form.NFC);
  }
}
