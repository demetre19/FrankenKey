package juloo.keyboard2.grammar;

/** One deterministic grammar finding inside a sentence. */
public final class GrammarIssue
{
  public final String ruleId;
  /** UTF-16 offset of the flagged span inside the checked sentence. */
  public final int offset;
  public final int length;
  /** Replacement text with the source casing applied. */
  public final String replacement;
  /** Matched source text, for display and session-level ignores. */
  public final String matched;

  public GrammarIssue(String ruleId_, int offset_, int length_,
      String replacement_, String matched_)
  {
    ruleId = ruleId_;
    offset = offset_;
    length = length_;
    replacement = replacement_;
    matched = matched_;
  }

  public String message()
  {
    return "\"" + matched + "\" \u2192 \"" + replacement + "\"";
  }

  @Override
  public String toString()
  {
    return ruleId + "@" + offset + "+" + length + "=" + replacement;
  }
}
