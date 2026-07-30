package juloo.keyboard2;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

/** Validates Android share and Process Text intents before private Reader handoff. */
public final class ReaderShareActivity extends Activity
{
  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    CharSequence shared = sharedText(getIntent());
    if (shared == null || shared.toString().trim().isEmpty())
    {
      reject(R.string.reader_error_unavailable_text);
      return;
    }
    String text = shared.toString();
    if (text.length() > ReaderPlaybackService.MAX_ACTIVE_TEXT_LENGTH)
    {
      reject(R.string.reader_error_text_too_long);
      return;
    }
    boolean selection = Intent.ACTION_PROCESS_TEXT.equals(getIntent().getAction());
    if (!selection && isHttpUrlOnly(text))
    {
      reject(R.string.reader_error_url_import_unavailable);
      return;
    }
    ReaderActivity.startQuickRead(this,
        (selection ? "process-text:" : "android-share:") +
          System.currentTimeMillis(),
        getString(selection ? R.string.reader_title_selection :
          R.string.reader_title_share),
        text);
    finish();
  }

  private static CharSequence sharedText(Intent intent)
  {
    if (intent == null || !"text/plain".equals(intent.getType()))
      return null;
    try
    {
      if (Intent.ACTION_SEND.equals(intent.getAction()))
        return intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
      if (Intent.ACTION_PROCESS_TEXT.equals(intent.getAction()))
        return intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
    }
    catch (RuntimeException malformedExtra)
    {
      return null;
    }
    return null;
  }
  private static boolean isHttpUrlOnly(String text)
  {
    String value = text.trim();
    if (value.indexOf(' ') >= 0 || value.indexOf('\n') >= 0 ||
        value.indexOf('\t') >= 0)
      return false;
    Uri uri = Uri.parse(value);
    String scheme = uri.getScheme();
    return uri.getHost() != null &&
      ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
  }

  private void reject(int message)
  {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    finish();
  }
}
