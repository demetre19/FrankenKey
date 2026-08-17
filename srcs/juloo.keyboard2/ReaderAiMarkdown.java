package juloo.keyboard2;

import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BulletSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Renders the small, safe Markdown subset used by Reader AI while preserving plain text. */
final class ReaderAiMarkdown
{
  private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+)$");
  private static final Pattern BULLET = Pattern.compile("^(\\s*)[-+*]\\s+(.+)$");
  private static final Pattern NUMBERED = Pattern.compile("^(\\s*)(\\d+)[.)]\\s+(.+)$");
  private static final Pattern QUOTE = Pattern.compile("^\\s*>\\s?(.*)$");
  private static final Pattern RULE = Pattern.compile("^\\s*([-*_])(?:\\s*\\1){2,}\\s*$");

  private ReaderAiMarkdown() {}

  static CharSequence render(String markdown, float density)
  {
    SpannableStringBuilder output = new SpannableStringBuilder();
    String normalized = markdown == null ? ""
      : markdown.replace("\r\n", "\n").replace('\r', '\n');
    String[] lines = normalized.split("\n", -1);
    boolean fencedCode = false;
    for (int index = 0; index < lines.length; index++)
    {
      String line = lines[index];
      if (line.trim().startsWith("```"))
      {
        fencedCode = !fencedCode;
      }
      else if (fencedCode)
      {
        int start = output.length();
        output.append(line);
        output.setSpan(new TypefaceSpan("monospace"), start, output.length(),
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
      else
      {
        appendLine(output, line, density);
      }
      if (index < lines.length - 1 && !line.trim().startsWith("```"))
        output.append('\n');
    }
    return output;
  }

  static String plainText(String markdown)
  {
    return render(markdown, 1f).toString();
  }

  private static void appendLine(SpannableStringBuilder output, String line,
      float density)
  {
    Matcher heading = HEADING.matcher(line);
    if (heading.matches())
    {
      int start = output.length();
      appendInline(output, heading.group(2));
      int end = output.length();
      output.setSpan(new StyleSpan(Typeface.BOLD), start, end,
          Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      output.setSpan(new RelativeSizeSpan(heading.group(1).length() == 1
            ? 1.35f : 1.18f), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      return;
    }

    Matcher bullet = BULLET.matcher(line);
    if (bullet.matches())
    {
      int start = output.length();
      appendInline(output, bullet.group(2));
      int indent = bullet.group(1).length() / 2;
      output.setSpan(new BulletSpan(Math.max(6,
              Math.round((8 + indent * 8) * density))), start, output.length(),
          Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      return;
    }

    Matcher numbered = NUMBERED.matcher(line);
    if (numbered.matches())
    {
      int start = output.length();
      output.append(numbered.group(2)).append(". ");
      appendInline(output, numbered.group(3));
      int indent = numbered.group(1).length() / 2;
      output.setSpan(new LeadingMarginSpan.Standard(Math.max(12,
              Math.round((16 + indent * 8) * density))), start, output.length(),
          Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      return;
    }

    Matcher quote = QUOTE.matcher(line);
    if (quote.matches())
    {
      int start = output.length();
      appendInline(output, quote.group(1));
      output.setSpan(new LeadingMarginSpan.Standard(Math.max(12,
              Math.round(16 * density))), start, output.length(),
          Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      output.setSpan(new StyleSpan(Typeface.ITALIC), start, output.length(),
          Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      return;
    }

    if (!RULE.matcher(line).matches())
      appendInline(output, line);
  }

  private static void appendInline(SpannableStringBuilder output, String text)
  {
    int index = 0;
    while (index < text.length())
    {
      if (text.startsWith("**", index) || text.startsWith("__", index))
      {
        String marker = text.substring(index, index + 2);
        int closing = text.indexOf(marker, index + 2);
        if (closing > index + 2)
        {
          int start = output.length();
          appendInline(output, text.substring(index + 2, closing));
          output.setSpan(new StyleSpan(Typeface.BOLD), start, output.length(),
              Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
          index = closing + 2;
          continue;
        }
      }

      char current = text.charAt(index);
      if (current == '`')
      {
        int closing = text.indexOf('`', index + 1);
        if (closing > index + 1)
        {
          int start = output.length();
          output.append(text, index + 1, closing);
          output.setSpan(new TypefaceSpan("monospace"), start, output.length(),
              Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
          index = closing + 1;
          continue;
        }
      }

      if (current == '[')
      {
        int labelEnd = text.indexOf("](", index + 1);
        int urlEnd = labelEnd < 0 ? -1 : text.indexOf(')', labelEnd + 2);
        if (labelEnd > index + 1 && urlEnd > labelEnd + 2)
        {
          int start = output.length();
          appendInline(output, text.substring(index + 1, labelEnd));
          String url = text.substring(labelEnd + 2, urlEnd).trim();
          if (isSafeUrl(url))
            output.setSpan(new URLSpan(url), start, output.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
          index = urlEnd + 1;
          continue;
        }
      }

      if (current == '*' || current == '_')
      {
        int closing = text.indexOf(current, index + 1);
        if (closing > index + 1)
        {
          int start = output.length();
          appendInline(output, text.substring(index + 1, closing));
          output.setSpan(new StyleSpan(Typeface.ITALIC), start, output.length(),
              Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
          index = closing + 1;
          continue;
        }
      }

      output.append(current);
      index++;
    }
  }

  private static boolean isSafeUrl(String value)
  {
    String lower = value.toLowerCase(Locale.US);
    return lower.startsWith("https://") || lower.startsWith("http://");
  }
}
