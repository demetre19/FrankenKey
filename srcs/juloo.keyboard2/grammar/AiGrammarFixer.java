package juloo.keyboard2.grammar;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import juloo.keyboard2.ReaderAiOpenRouter;
import org.json.JSONObject;
/**
 * Explicit "Fix grammar" flow: eligibility, bounded input capture,
 * disclosure gating, request building, response guards. Network calls are
 * injected so tests run fully offline.
 */
public final class AiGrammarFixer
{
  public static final int MAX_INPUT_LENGTH = 4000;
  public static final double MIN_RATIO = 0.5;
  public static final double MAX_RATIO = 1.5;
  public static final String DISCLOSURE_KEY = "disclosure_accepted_v4";

  public enum Refusal
  {
    NONE, PASSWORD, NUMERIC, PHONE, EMAIL, URI, TERMINAL, UNREADABLE,
    NO_KEY, TOO_LONG, SELECTION_REQUIRED
  }

  /** Editor classification for the eligibility matrix (overview §7.4). */
  public enum EditorClass
  {
    PROSE, EMAIL, URI, PASSWORD, NUMERIC, PHONE, TERMINAL, UNREADABLE
  }

  public static final class Snapshot
  {
    public final String text;
    public final int selectionStart;
    public final int selectionEnd;
    public final int extractedStartOffset;
    public final String sha256;

    public Snapshot(String text_, int selStart, int selEnd,
        int extractedStart, String sha256_)
    {
      text = text_;
      selectionStart = selStart;
      selectionEnd = selEnd;
      extractedStartOffset = extractedStart;
      sha256 = sha256_;
    }
  }

  public static final class Outcome
  {
    public final Refusal refusal;
    public final Snapshot snapshot;

    Outcome(Refusal refusal_, Snapshot snapshot_)
    {
      refusal = refusal_;
      snapshot = snapshot_;
    }

    static Outcome refused(Refusal r) { return new Outcome(r, null); }
    static Outcome ok(Snapshot s) { return new Outcome(Refusal.NONE, s); }
  }

  /** ExtractedText essentials for completeness checks. */
  public interface ExtractedTextView
  {
    CharSequence text();
    int startOffset();
    int partialStartOffset();
    int partialEndOffset();
  }

  public static Refusal eligibility(EditorClass editorClass)
  {
    switch (editorClass)
    {
      case PROSE: return Refusal.NONE;
      case PASSWORD: return Refusal.PASSWORD;
      case NUMERIC: return Refusal.NUMERIC;
      case PHONE: return Refusal.PHONE;
      case EMAIL: return Refusal.EMAIL;
      case URI: return Refusal.URI;
      case TERMINAL: return Refusal.TERMINAL;
      default: return Refusal.UNREADABLE;
    }
  }

  /**
   * Selection wins. Whole-field text requires a provably complete
   * ExtractedText: startOffset 0 and no partial window.
   */
  public static Outcome capture(ExtractedTextView extracted,
      CharSequence selectedText, int selectionStart, int selectionEnd)
  {
    if (selectedText != null && selectionEnd > selectionStart)
    {
      String sel = selectedText.toString();
      if (sel.length() > MAX_INPUT_LENGTH)
        return Outcome.refused(Refusal.TOO_LONG);
      return Outcome.ok(new Snapshot(sel, selectionStart, selectionEnd,
          selectionStart, sha256(sel)));
    }
    if (extracted == null || extracted.text() == null)
      return Outcome.refused(Refusal.SELECTION_REQUIRED);
    boolean complete = extracted.startOffset() == 0
      && extracted.partialStartOffset() < 0;
    if (!complete)
      return Outcome.refused(Refusal.SELECTION_REQUIRED);
    String whole = extracted.text().toString();
    if (whole.length() > MAX_INPUT_LENGTH)
      return Outcome.refused(Refusal.TOO_LONG);
    return Outcome.ok(new Snapshot(whole, 0, whole.length(), 0,
        sha256(whole)));
  }

  /** Quote/fence strip plus empty/identical/ratio guards. */
  public static String sanitizeResponse(String response, String input)
  {
    if (response == null)
      return null;
    String out = response.trim();
    if (out.startsWith("```"))
    {
      int first = out.indexOf('\n');
      int last = out.lastIndexOf("```");
      if (first >= 0 && last > first)
        out = out.substring(first + 1, last).trim();
    }
    if (out.length() >= 2 && out.startsWith("\"")
        && out.endsWith("\""))
      out = out.substring(1, out.length() - 1);
    if (out.isEmpty() || out.equals(input))
      return null;
    double ratio = (double)out.length() / Math.max(1, input.length());
    if (ratio < MIN_RATIO || ratio > MAX_RATIO)
      return null;

    return out;
  }

  public static JSONObject buildRequest(String modelId, String systemPrompt,
      String input, int maxTokens) throws org.json.JSONException
  {
    List<ReaderAiOpenRouter.Message> messages = new ArrayList<>();
    messages.add(new ReaderAiOpenRouter.Message("system", systemPrompt));
    messages.add(new ReaderAiOpenRouter.Message("user", input));
    return ReaderAiOpenRouter.buildRequest(modelId, messages, maxTokens,
        0.0);
  }

  public static int maxTokensFor(String input)
  {
    /* Rough token estimate ~4 chars/token; ask for ~1.5x input. */
    int tokens = Math.max(1, input.length() / 4);
    return (int)Math.ceil(tokens * MAX_RATIO) + 8;
  }

  public static String sha256(String text)
  {
    try
    {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(
          text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(hash.length * 2);
      for (byte b : hash)
        hex.append(String.format("%02x", b));
      return hex.toString();
    }
    catch (NoSuchAlgorithmException error)
    {
      throw new RuntimeException(error);
    }
  }
}
