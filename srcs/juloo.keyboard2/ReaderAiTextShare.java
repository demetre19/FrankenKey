package juloo.keyboard2;

import android.app.Activity;
import android.content.Intent;

/** Plain-text/Markdown sharing with durable original-link provenance. */
final class ReaderAiTextShare
{
  private ReaderAiTextShare() {}

  static String format(String title, String type, String content, String chat,
      String sourceUrl)
  {
    StringBuilder text = new StringBuilder();
    if (title != null && !title.trim().isEmpty())
      text.append(title.trim()).append('\n');
    if (type != null && !type.trim().isEmpty())
      text.append(type.trim()).append("\n\n");
    text.append(content == null ? "" : content.trim());
    if (chat != null && !chat.trim().isEmpty())
      text.append("\n\n").append(chat.trim());
    String source = sourceUrl == null ? "" : sourceUrl.trim();
    if (!source.isEmpty())
      text.append("\n\nOriginal URL:\n").append(source);
    return text.toString();
  }

  static void share(Activity activity, String title, String type,
      String content, String chat, String sourceUrl)
  {
    Intent intent = new Intent(Intent.ACTION_SEND);
    intent.setType("text/plain");
    intent.putExtra(Intent.EXTRA_SUBJECT,
        (title == null || title.trim().isEmpty() ? "Reader AI" : title.trim())
          + " — " + type);
    intent.putExtra(Intent.EXTRA_TEXT,
        format(title, type, content, chat, sourceUrl));
    activity.startActivity(Intent.createChooser(intent, "Share Reader AI result"));
  }
}
