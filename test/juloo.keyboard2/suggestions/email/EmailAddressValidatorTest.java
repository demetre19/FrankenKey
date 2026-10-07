package juloo.keyboard2.suggestions.email;

import org.junit.Test;
import static org.junit.Assert.*;

public class EmailAddressValidatorTest
{
  @Test
  public void valid_addresses()
  {
    String[] ok = {
      "simple@example.com",
      "very.common@example.com",
      "disposable.style.stripe.with+symbol@example.com",
      "other.email-with-hyphen@example.com",
      "user.name+tag+sorting@example.com",
      "x@example.com",
      "a+b@x.io",
      "first.last@company.co.uk",
      "user@sub.example.com",
      "user@xn--example-9ua.com",
      "user@Example.COM",
      "user@example.COM",
    };
    for (String a : ok)
      assertTrue("expected valid: " + a, EmailAddressValidator.isValid(a));
  }

  @Test
  public void invalid_addresses()
  {
    String[] bad = {
      null,
      "",
      "plainaddress",
      "@example.com",
      "email@example",
      "email@example@example.com",
      "email..email@example.com",
      ".email@example.com",
      "email.@example.com",
      "email@.example.com",
      "email@example..com",
      "email@example.com.",
      "email@-example.com",
      "email@example-.com",
      "email@111.222.333.444",
      "email@[123.123.123.123]",
      "this is" + new String(new char[250]).replace('\0', 'a') + "@toolong.com",
    };
    for (String a : bad)
      assertFalse("expected invalid: " + a, EmailAddressValidator.isValid(a));
  }

  @Test
  public void normalize_lowercases_domain_only()
  {
    assertEquals("Demetre@example.com",
        EmailAddressValidator.normalize("Demetre@Example.COM"));
    assertEquals("a+ b@x.io",
        EmailAddressValidator.normalize("a+ b@x.io"));
  }

  @Test
  public void too_long_overall()
  {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 240; i++) sb.append('a');
    sb.append("@example.com");
    assertFalse(EmailAddressValidator.isValid(sb.toString()));
  }
}
