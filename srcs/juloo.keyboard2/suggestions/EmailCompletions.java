package juloo.keyboard2.suggestions;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Domain completions after '@' in the candidate strip — the Gboard email-LM
    behavior: learned domains first, then popular providers filtered by the
    partial domain the user has typed so far. */
public final class EmailCompletions
{
  public static final String[] POPULAR_DOMAINS =
  {
    "gmail.com", "outlook.com", "yahoo.com", "icloud.com",
    "hotmail.com", "proton.me", "aol.com", "live.com"
  };

  private EmailCompletions() {}

  /** Matches the tail of [text] against "localpart@partialdomain". Returns
      the partial domain (possibly empty), or null when the text does not end
      in an email address being typed. */
  public static String partial_domain(CharSequence text)
  {
    if (text == null)
      return null;
    int end = text.length();
    int at = -1;
    for (int i = end - 1; i >= 0; i--)
    {
      char c = text.charAt(i);
      if (c == '@')
      {
        at = i;
        break;
      }
      if (!Character.isLetterOrDigit(c) && c != '.' && c != '-')
        return null;
    }
    if (at < 0)
      return null;
    if (!PersonalizationStore.is_email_local(
          local_part_before(text, at)))
      return null;
    return text.subSequence(at + 1, end).toString();
  }

  static String local_part_before(CharSequence text, int at)
  {
    int start = at;
    while (start > 0)
    {
      char c = text.charAt(start - 1);
      if (!Character.isLetterOrDigit(c) && c != '.' && c != '_' && c != '%'
          && c != '+' && c != '-')
        break;
      start--;
    }
    return text.subSequence(start, at).toString();
  }

  /** A complete email ending right before [end] of [text], or null. Used to
      learn addresses once they are committed. */
  public static String trailing_email(CharSequence text)
  {
    if (text == null)
      return null;
    int end = text.length();
    while (end > 0 && Character.isWhitespace(text.charAt(end - 1)))
      end--;
    int start = end;
    while (start > 0)
    {
      char c = text.charAt(start - 1);
      if (!Character.isLetterOrDigit(c) && c != '.' && c != '_' && c != '%'
          && c != '+' && c != '-' && c != '@')
        break;
      start--;
    }
    String token = text.subSequence(start, end).toString().toLowerCase();
    return PersonalizationStore.is_email_token(token)
        && token.indexOf('@') >= 0 ? token : null;
  }

  public static String domain_of(String email)
  {
    int at = email.indexOf('@');
    return at < 0 ? null : email.substring(at + 1);
  }

  /** Domain pills for the strip: learned domains matching the prefix first,
      then popular domains matching, then remaining popular domains as a
      fallback so pills are always present after '@'. */
  public static List<String> suggest(CharSequence textBeforeCursor,
      List<String> learnedWords)
  {
    String partial = partial_domain(textBeforeCursor);
    if (partial == null)
      return new ArrayList<String>();
    Set<String> out = new LinkedHashSet<String>();
    if (learnedWords != null)
      for (String word : learnedWords)
        if (PersonalizationStore.is_domainish(word)
            && word.startsWith(partial))
          out.add(word);
    for (String domain : POPULAR_DOMAINS)
      if (domain.startsWith(partial))
        out.add(domain);
    if (out.isEmpty() && partial.length() > 0)
      for (String domain : POPULAR_DOMAINS)
        out.add(domain);
    List<String> result = new ArrayList<String>(out);
    return result.subList(0, Math.min(result.size(), 6));
  }
}
