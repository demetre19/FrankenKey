package juloo.keyboard2.suggestions.email;

import org.junit.Test;
import static org.junit.Assert.*;

public class EmailTokenTest
{
  private static EmailToken p(String before, String after)
  {
    return EmailToken.parse(before, after);
  }

  @Test
  public void token_without_at_is_a_context_with_empty_domain()
  {
    EmailToken t = p("john", "");
    assertNotNull(t);
    assertEquals("john", t.token);
    assertEquals("john", t.localPart);
    assertFalse(t.hasAt);
    assertEquals("", t.domainPrefix);
    assertEquals(0, t.tokenStartOffset);
  }

  @Test
  public void full_address_before_cursor()
  {
    EmailToken t = p("user@gmail.com", "");
    assertNotNull(t);
    assertEquals("user@gmail.com", t.token);
    assertEquals("user", t.localPart);
    assertTrue(t.hasAt);
    assertEquals("gmail.com", t.domainPrefix);
    assertEquals(0, t.tokenStartOffset);
  }

  @Test
  public void token_start_offset_points_at_token_start()
  {
    assertEquals(7, p("hello, user@gmail.com", "").tokenStartOffset);
    assertEquals(2, p("a b@gmail.com", "").tokenStartOffset);
    assertEquals(4, p("x; (a@b.co", ")").tokenStartOffset);
  }

  @Test
  public void cursor_right_after_at_has_empty_domain_prefix()
  {
    EmailToken t = p("user@", "");
    assertNotNull(t);
    assertTrue(t.hasAt);
    assertEquals("user", t.localPart);
    assertEquals("", t.domainPrefix);
  }

  @Test
  public void empty_token_is_a_context_without_at()
  {
    EmailToken t = p("", "");
    assertNotNull(t);
    assertEquals("", t.token);
    assertFalse(t.hasAt);
    assertEquals(0, t.tokenStartOffset);
    assertNotNull(p("hi ", ""));
    assertEquals(3, p("hi ", "").tokenStartOffset);
  }

  @Test
  public void mid_token_after_cursor_is_no_context()
  {
    assertNull(p("user", "@gmail.com"));
    assertNull(p("user@gm", "ail.com"));
    assertNull(p("", "abc"));
    assertNull(p("hello wor", "ld"));
  }

  @Test
  public void mention_is_never_an_email_context()
  {
    assertNull(p("@john", ""));
    assertNull(p(" @john", ""));
    assertNull(p("@x.com", ""));
  }

  @Test
  public void two_at_signs_produce_no_context()
  {
    assertNull(p("a@b@c", ""));
    assertNull(p("a@b@", ""));
  }

  @Test
  public void whitespace_and_delimiters_end_the_token_scan()
  {
    assertEquals("b@gmail.com", p("a b@gmail.com", "").token);
    assertEquals("john@gmail.com", p("hi, john@gmail.com", "").token);
    assertEquals("user@x.com", p("(user@x.com", ")").token);
    assertEquals("user@x.com", p("[user@x.com", "]").token);
    assertEquals("user@x.com", p("\"user@x.com", "\"").token);
    assertEquals("a@b.co", p("x; (a@b.co", ")").token);
    // A closing delimiter before the cursor ends the token: empty context there.
    EmailToken closed = p("x; (a@b.co)", "");
    assertNotNull(closed);
    assertEquals("", closed.token);
    assertEquals(11, closed.tokenStartOffset);
    assertEquals("b@c.d", p("a\nb@c.d", "").token);
    assertEquals("b@c.d", p("a\tb@c.d", "").token);
  }

  @Test
  public void apostrophe_is_a_local_char_not_a_delimiter()
  {
    EmailToken t = p("john's@gmail.com", "");
    assertNotNull(t);
    assertEquals("john's@gmail.com", t.token);
    assertEquals("john's", t.localPart);
    assertTrue(t.hasAt);
  }

  @Test
  public void non_address_char_ends_the_email_context()
  {
    assertNull(p("ab!cd@gmail.com", ""));
    assertNull(p("a#b@x.com", ""));
    assertNull(p("a$b@x.com", ""));
    assertNull(p("a/b@x.com", ""));
    assertNull(p("a?b@x.com", ""));
  }

  @Test
  public void local_only_chars_after_at_end_the_context()
  {
    assertNull(p("a@x%y.com", ""));
    assertNull(p("a@x+y.com", ""));
    assertNull(p("a@x_y.com", ""));
    assertNull(p("a@x'y.com", ""));
  }

  @Test
  public void unicode_letters_in_domain_are_domain_chars()
  {
    EmailToken t = p("a@x\u4f60.com", "");
    assertNotNull(t);
    assertTrue(t.hasAt);
    assertEquals("x\u4f60.com", t.domainPrefix);
  }

  @Test
  public void unicode_letters_before_at_end_the_context()
  {
    assertNull(p("\u4f60@x.com", ""));
  }

  @Test
  public void token_length_cap_is_254_utf16_units()
  {
    StringBuilder ok = new StringBuilder();
    for (int i = 0; i < 248; i++) ok.append('a');
    ok.append("@x.com"); // 254 units
    EmailToken t = p(ok.toString(), "");
    assertNotNull(t);
    assertEquals(254, t.token.length());
    StringBuilder tooLong = new StringBuilder();
    for (int i = 0; i < 249; i++) tooLong.append('a');
    tooLong.append("@x.com"); // 255 units
    assertNull(p(tooLong.toString(), ""));
  }

  @Test
  public void after_cursor_delimiters_do_not_block_the_context()
  {
    assertNotNull(p("user@gmail.com", " "));
    assertNotNull(p("user@gmail.com", ","));
    assertNotNull(p("user@gmail.com", "!"));
    EmailToken t = p("user@gmail.com", " ");
    assertTrue(t.hasAt);
    assertEquals("gmail.com", t.domainPrefix);
  }

  @Test
  public void case_is_kept_in_local_part_and_domain_prefix()
  {
    EmailToken t = p("User@Gmail.com", "");
    assertEquals("User", t.localPart);
    assertEquals("Gmail.com", t.domainPrefix);
  }

  @Test
  public void digits_hyphens_and_dots_are_allowed_around_the_at()
  {
    assertNotNull(p("1.2.3@xn--e1a.org", ""));
    assertNotNull(p("a-b.c_d%e+f'g@x-y.co", ""));
  }

  @Test
  public void char_class_helpers()
  {
    assertTrue(EmailToken.isLocalChar('_'));
    assertTrue(EmailToken.isLocalChar('%'));
    assertTrue(EmailToken.isLocalChar('+'));
    assertTrue(EmailToken.isLocalChar('\''));
    assertTrue(EmailToken.isLocalChar('.'));
    assertFalse(EmailToken.isLocalChar('@'));
    assertFalse(EmailToken.isLocalChar('!'));
    assertTrue(EmailToken.isDomainChar('-'));
    assertTrue(EmailToken.isDomainChar('.'));
    assertTrue(EmailToken.isDomainChar('\u00e9'));
    assertFalse(EmailToken.isDomainChar('_'));
    assertFalse(EmailToken.isDomainChar('%'));
    assertTrue(EmailToken.isAddressChar('@'));
    assertFalse(EmailToken.isAddressChar(' '));
  }

  @Test
  public void nfc_normalization_composes_the_local_part()
  {
    assertEquals("caf\u00e9", EmailToken.normalizeLocalPart("cafe\u0301"));
    assertEquals("user", EmailToken.normalizeLocalPart("user"));
    assertEquals("caf\u00e9@x.com",
        EmailAddressValidator.normalize("cafe\u0301@X.COM"));
  }

  @Test
  public void null_inputs_have_no_context()
  {
    assertNull(p(null, ""));
    assertNull(p("user@", null));
  }
}
