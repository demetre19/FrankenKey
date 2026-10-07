package juloo.keyboard2.grammar;

import android.content.Context;
import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.util.AttributeSet;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import juloo.keyboard2.R;

/**
 * Word-level diff preview for the AI "Fix grammar" action. Removed words
 * are struck through in red; added words are tinted with the accent color.
 * Replace applies minimal back-to-front segment edits inside one batch
 * edit so untouched rich spans survive.
 */
public final class GrammarDiffView extends LinearLayout
{
  public interface Listener
  {
    void onReplace();
    void onCancel();
  }

  private TextView _header;
  private TextView _diff;
  private Listener _listener;

  public GrammarDiffView(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    setOrientation(VERTICAL);
  }

  @Override
  protected void onFinishInflate()
  {
    super.onFinishInflate();
    _header = findViewById(R.id.grammar_diff_header);
    _diff = findViewById(R.id.grammar_diff_text);
    findViewById(R.id.grammar_diff_replace).setOnClickListener(
        _v -> { if (_listener != null) _listener.onReplace(); });
    findViewById(R.id.grammar_diff_cancel).setOnClickListener(
        _v -> { if (_listener != null) _listener.onCancel(); });
    findViewById(R.id.grammar_diff_copy).setOnClickListener(_v -> copy());
  }

  public void setListener(Listener listener) { _listener = listener; }

  private String _corrected = "";

  /** [private] shows the no-personalized-learning header. */
  public void show(String original, String corrected, boolean privateField)
  {
    _corrected = corrected;
    List<String[]> ops = diffWords(original, corrected);
    _diff.setText(render(original, ops));
    _diff.setContentDescription(describe(original, ops));
    _header.setVisibility(privateField ? View.VISIBLE : View.GONE);
    setVisibility(View.VISIBLE);
  }

  public void hide() { setVisibility(View.GONE); }

  private void copy()
  {
    android.content.ClipboardManager clipboard =
      (android.content.ClipboardManager)getContext()
        .getSystemService(Context.CLIPBOARD_SERVICE);
    if (clipboard != null)
      clipboard.setPrimaryClip(android.content.ClipData.newPlainText(
          "corrected", _corrected));
  }

  /** Word-level LCS diff; returns [kind, start, end] ops on the original. */
  public static List<String[]> diffOps(String original, String corrected)
  {
    List<String[]> ops = new ArrayList<>();
    List<String[]> diffs = diffWords(original, corrected);
    for (String[] d : diffs)
      ops.add(d);
    return ops;
  }

  /** Each op: {op, oldStart, oldEnd, newStart, newEnd}; word indices. */
  public static List<String[]> diffWords(String original, String corrected)
  {
    String[] a = original.split("\\s+");
    String[] b = corrected.split("\\s+");
    int n = a.length, m = b.length;
    int[][] dp = new int[n + 1][m + 1];
    for (int i = n - 1; i >= 0; i--)
      for (int j = m - 1; j >= 0; j--)
        dp[i][j] = a[i].equals(b[j])
          ? dp[i + 1][j + 1] + 1
          : Math.max(dp[i + 1][j], dp[i][j + 1]);
    List<String[]> ops = new ArrayList<>();
    int i = 0, j = 0;
    while (i < n && j < m)
    {
      if (a[i].equals(b[j])) { i++; j++; continue; }
      if (dp[i + 1][j] >= dp[i][j + 1])
        ops.add(new String[]{ "del", "" + i, "" + i, a[i], "" });
      else
        ops.add(new String[]{ "add", "" + i, "" + i, "", b[j] });
      if ("del".equals(ops.get(ops.size() - 1)[0])) i++; else j++;
    }
    while (i < n) { ops.add(new String[]{ "del", "" + i, "" + i, a[i], "" }); i++; }
    while (j < m) { ops.add(new String[]{ "add", "" + n, "" + n, "", b[j] }); j++; }
    return ops;
  }

  /** Render ops into a spannable: removed struck/red, added accent. */
  private CharSequence render(String original, List<String[]> ops)
  {
    SpannableStringBuilder builder = new SpannableStringBuilder();
    String[] words = original.split("\\s+");
    int accent = accentColor();
    int removed = 0xffff5252;
    int lastRendered = 0;
    for (String[] op : ops)
    {
      int wordIndex = Integer.parseInt(op[1]);
      while (lastRendered < wordIndex && lastRendered < words.length)
      {
        if (builder.length() > 0) builder.append(' ');
        builder.append(words[lastRendered++]);
      }
      if ("del".equals(op[0]))
      {
        int start = builder.length();
        if (builder.length() > 0) builder.append(' ');
        builder.append(op[3]);
        builder.setSpan(new StrikethroughSpan(), start, builder.length(),
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(new ForegroundColorSpan(removed), start,
            builder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        lastRendered = wordIndex + 1;
      }
      else
      {
        int start = builder.length();
        if (builder.length() > 0) builder.append(' ');
        builder.append(op[4]);
        builder.setSpan(new ForegroundColorSpan(accent), start,
            builder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
    }
    while (lastRendered < words.length)
    {
      if (builder.length() > 0) builder.append(' ');
      builder.append(words[lastRendered++]);
    }
    return builder;
  }

  /** "changed 'your' to 'you're'; removed 'alot'; added 'a lot'". */
  private static String describe(String original, List<String[]> ops)
  {
    StringBuilder out = new StringBuilder();
    for (String[] op : ops)
    {
      if (out.length() > 0)
        out.append("; ");
      if ("del".equals(op[0]))
        out.append("removed '").append(op[3]).append('\'');
      else
        out.append("added '").append(op[4]).append('\'');
    }
    return out.length() == 0 ? "No changes" : out.toString();
  }

  private int accentColor()
  {
    android.util.TypedValue value = new android.util.TypedValue();
    getContext().getTheme().resolveAttribute(
        android.R.attr.colorAccent, value, true);
    return value.data;
  }

  /** A changed span: replace [start,end) of the original with
      [repStart,repEnd) of the corrected text. */
  public static final class Segment
  {
    public final int start;
    public final int end;
    public final int repStart;
    public final int repEnd;

    Segment(int s, int e, int rs, int re)
    {
      start = s; end = e; repStart = rs; repEnd = re;
    }
  }

  /**
   * Build the minimal set of changed segments from the word-level diff.
   * Segments cover only the changed words plus their inner whitespace;
   * matching (anchor) regions are never rewritten, so rich spans in the
   * untouched text survive.
   */
  public static List<Segment> segments(String original, String corrected)
  {
    String[] a = original.split("\\s+");
    String[] b = corrected.split("\\s+");
    int[] aStart = wordOffsets(original, a);
    int[] aEnd = wordEnds(original, a, aStart);
    int[] bStart = wordOffsets(corrected, b);
    int[] bEnd = wordEnds(corrected, b, bStart);
    /* LCS alignment: matched pairs act as anchors. */
    List<int[]> anchors = align(a, b);
    List<Segment> segments = new ArrayList<>();
    int ai = 0, bi = 0;
    for (int[] pair : anchors)
    {
      int anchorA = pair[0], anchorB = pair[1];
      if (ai < anchorA || bi < anchorB)
      {
        int start = ai < aStart.length ? aStart[ai] : original.length();
        int end = anchorA < aStart.length ? aStart[anchorA]
          : original.length();
        int repStart = bi < bStart.length ? bStart[bi]
          : corrected.length();
        int repEnd = anchorB < bStart.length ? bStart[anchorB]
          : corrected.length();
        segments.add(new Segment(start, end, repStart, repEnd));
      }
      ai = anchorA + 1;
      bi = anchorB + 1;
    }
    if (ai < a.length || bi < b.length)
    {
      int start = ai < aStart.length ? aStart[ai] : original.length();
      int repStart = bi < bStart.length ? bStart[bi] : corrected.length();
      segments.add(new Segment(start, original.length(), repStart,
          corrected.length()));
    }
    return segments;
  }

  /** Matched (i,j) word pairs in order — the LCS alignment. */
  static List<int[]> align(String[] a, String[] b)
  {
    int n = a.length, m = b.length;
    int[][] dp = new int[n + 1][m + 1];
    for (int i = n - 1; i >= 0; i--)
      for (int j = m - 1; j >= 0; j--)
        dp[i][j] = a[i].equals(b[j])
          ? dp[i + 1][j + 1] + 1
          : Math.max(dp[i + 1][j], dp[i][j + 1]);
    List<int[]> pairs = new ArrayList<>();
    int i = 0, j = 0;
    while (i < n && j < m)
    {
      if (a[i].equals(b[j])) { pairs.add(new int[]{ i, j }); i++; j++; }
      else if (dp[i + 1][j] >= dp[i][j + 1]) i++;
      else j++;
    }
    return pairs;
  }

  static int[] wordOffsets(String text, String[] words)
  {
    int[] offsets = new int[words.length];
    int pos = 0;
    for (int i = 0; i < words.length; i++)
    {
      offsets[i] = text.indexOf(words[i], pos);
      pos = offsets[i] + words[i].length();
    }
    return offsets;
  }

  static int[] wordEnds(String text, String[] words, int[] starts)
  {
    int[] ends = new int[words.length];
    for (int i = 0; i < words.length; i++)
      ends[i] = starts[i] + words[i].length();
    return ends;
  }

  /**
   * Apply the correction as minimal back-to-front segment edits inside a
   * single batch edit. Each segment revalidates the text it expects to
   * find; a mismatch rolls back every applied segment and returns false.
   * [base] is the editor offset where [original] starts (0 for a whole
   * field, the selection start otherwise).
   */
  public static boolean applyCorrection(InputConnection connection,
      String original, String corrected, int base)
  {
    if (connection == null || original == null || corrected == null)
      return false;
    List<Segment> segments = segments(original, corrected);
    int cursor = extractCursor(connection);
    List<int[]> applied = new ArrayList<>();
    connection.beginBatchEdit();
    try
    {
      for (int s = segments.size() - 1; s >= 0; s--)
      {
        Segment seg = segments.get(s);
        String expected = original.substring(seg.start, seg.end);
        String replacement = corrected.substring(seg.repStart, seg.repEnd);
        if (!revalidateSegment(connection, base + seg.start, expected))
        {
          rollback(connection, original, base, applied);
          return false;
        }
        if (!connection.setSelection(base + seg.start, base + seg.end)
            || !connection.commitText(replacement, 1))
        {
          rollback(connection, original, base, applied);
          return false;
        }
        applied.add(new int[]{ base + seg.start, base + seg.end,
            replacement.length() });
      }
      if (cursor >= 0)
        connection.setSelection(cursor, cursor);
      return true;
    }
    finally
    {
      connection.endBatchEdit();
    }
  }

  private static boolean revalidateSegment(InputConnection connection,
      int start, String expected)
  {
    if (expected.isEmpty())
      return true;
    String text = fullTextBeforeCursor(connection);
    if (text == null || start + expected.length() > text.length())
      return false;
    return text.substring(start, start + expected.length())
      .equals(expected);
  }

  /** Whole text before the cursor when the editor provides it. */
  static String fullTextBeforeCursor(InputConnection connection)
  {
    CharSequence before = connection.getTextBeforeCursor(65536, 0);
    return before == null ? null : before.toString();
  }

  /** [applied] rows: {absStart, absEnd, replacementLength}. */
  private static void rollback(InputConnection connection, String original,
      int base, List<int[]> applied)
  {
    for (int i = 0; i < applied.size(); i++)
    {
      int absStart = applied.get(i)[0];
      int absEnd = applied.get(i)[1];
      int repLen = applied.get(i)[2];
      String originalText = original.substring(absStart - base,
          absEnd - base);
      connection.setSelection(absStart, absStart + repLen);
      connection.commitText(originalText, 1);
    }
  }

  private static int extractCursor(InputConnection connection)
  {
    android.view.inputmethod.ExtractedTextRequest request =
      new android.view.inputmethod.ExtractedTextRequest();
    android.view.inputmethod.ExtractedText extracted =
      connection.getExtractedText(request, 0);
    return extracted == null ? -1 : extracted.selectionStart;
  }
}
