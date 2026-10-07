package juloo.keyboard2.suggestions.email;

import java.text.Normalizer;

/**
 * RFC-ish email address validation for local storage and candidate generation.
 *
 * Local part: 1–64 characters from [A-Za-z0-9._%+'-]; no leading, trailing, or
 * consecutive dots.  Quoted local parts are rejected.
 * Domain part: 2+ labels, each 1–63 chars of [A-Za-z0-9-], no leading or
 * trailing hyphen, TLD ≥2 letters.  Total domain ≤253.  Total address ≤254.
 */
public final class EmailAddressValidator
{
  public static boolean isValid(String address)
  {
    return validate(address) == null;
  }

  /** Return null if valid, otherwise a short reason string for tests. */
  public static String validate(String address)
  {
    if (address == null)
      return "null";
    if (address.length() > 254)
      return "too_long";
    int at = address.indexOf('@');
    if (at <= 0 || at != address.lastIndexOf('@'))
      return "missing_or_multiple_at";
    String local = address.substring(0, at);
    String domain = address.substring(at + 1);
    String localErr = validateLocal(local);
    if (localErr != null)
      return localErr;
    String domainErr = validateDomain(domain);
    if (domainErr != null)
      return domainErr;
    return null;
  }

  static String validateLocal(String local)
  {
    String nfc = EmailToken.normalizeLocalPart(local);
    int len = nfc.length();
    if (len < 1 || len > 64)
      return "local_length";
    for (int i = 0; i < len; i++)
    {
      char c = nfc.charAt(i);
      if (EmailToken.isLocalChar(c))
        continue;
      if (Character.isWhitespace(c))
        return "local_whitespace";
      return "local_char";
    }
    if (nfc.charAt(0) == '.' || nfc.charAt(len - 1) == '.')
      return "local_dot_boundary";
    for (int i = 1; i < len; i++)
      if (nfc.charAt(i) == '.' && nfc.charAt(i - 1) == '.')
        return "local_consecutive_dots";
    return null;
  }

  static String validateDomain(String domain)
  {
    int len = domain.length();
    if (len < 1 || len > 253)
      return "domain_length";
    if (domain.startsWith("[") || domain.indexOf(':') >= 0)
      return "ip_literal";
    if (domain.startsWith(".") || domain.endsWith("."))
      return "domain_dot_boundary";
    String[] labels = domain.split("\\.");
    if (labels.length < 2)
      return "domain_label_count";
    String tld = labels[labels.length - 1];
    if (tld.length() < 2 || !allLetters(tld))
      return "domain_tld";
    for (String label : labels)
    {
      int llen = label.length();
      if (llen < 1 || llen > 63)
        return "domain_label_length";
      if (label.charAt(0) == '-' || label.charAt(llen - 1) == '-')
        return "domain_hyphen_boundary";
      for (int i = 0; i < llen; i++)
      {
        char c = label.charAt(i);
        if (!EmailToken.isDomainChar(c) || c == '.')
          return "domain_label_char";
      }
    }
    return null;
  }

  static boolean allLetters(String s)
  {
    for (int i = 0; i < s.length(); i++)
    {
      char c = s.charAt(i);
      if (!Character.isLetter(c))
        return false;
    }
    return true;
  }

  /** Normalize an address for storage: NFC local part, lower-case domain. */
  public static String normalize(String address)
  {
    if (address == null)
      return null;
    int at = address.indexOf('@');
    if (at <= 0)
      return address;
    return EmailToken.normalizeLocalPart(address.substring(0, at))
      + address.substring(at).toLowerCase();
  }
}
