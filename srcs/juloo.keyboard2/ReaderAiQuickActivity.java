package juloo.keyboard2;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Thin private host for the keyboard AI omnibutton. Builds a CLIPBOARD
 * article from the current clipboard and shows the shared ReaderAiDialog
 * surface; swipe actions either auto-run inside the dialog (summaries, quiz,
 * chat, load clipboard) or dispatch directly (saved, share, speed read, read
 * clipboard, AI settings).
 */
public final class ReaderAiQuickActivity extends Activity
{
  private static final String EXTRA_ACTION = "juloo.keyboard2.extra.AI_ACTION";

  static Intent intent(Context context, ReaderAiAction action)
  {
    return new Intent(context, ReaderAiQuickActivity.class)
      .putExtra(EXTRA_ACTION, action.id)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    ReaderAiAction action = ReaderAiAction.ofId(
        getIntent().getStringExtra(EXTRA_ACTION));
    if (action == null)
      action = ReaderAiAction.OPEN_CHAT;
    dispatch(action);
  }

  private void dispatch(ReaderAiAction action)
  {
    switch (action)
    {
      case NONE:
        Toast.makeText(this, R.string.reader_ai_no_action_assigned,
            Toast.LENGTH_SHORT).show();
        finish();
        return;
      case VOICE:
        Toast.makeText(this, R.string.reader_ai_voice_keyboard_only,
            Toast.LENGTH_SHORT).show();
        finish();
        return;
      case SAVED:
        startActivity(new Intent(this, ReaderAiLibraryActivity.class));
        finish();
        return;
      case SHARE:
        shareClipboard();
        return;
      case SPEED_READ:
        speedReadClipboard();
        return;
      case READ_CLIPBOARD:
        readClipboardAloud();
        return;
      case AI_SETTINGS:
        ReaderAiSettingsDialog.show(this, null, this::finish);
        return;
      default:
        openDialog(action);
        return;
    }
  }

  private void openDialog(ReaderAiAction autoAction)
  {
    ReaderAiDialog.show(this, clipboardArticle(), autoAction,
        this::clipboardArticle, this::finish);
  }

  private ReaderAiService.Article clipboardArticle()
  {
    ReaderTextAccess.Result result = ReaderTextAccess.readClipboard(this);
    if (result.isSuccess() && !result.text.trim().isEmpty())
      return new ReaderAiService.Article(null,
          getString(R.string.reader_title_clipboard), "", "", "",
          ReaderAiRequest.contentHash(result.text), result.text);
    String page = ReaderPageCaptureService.captureNow();
    if (!page.trim().isEmpty())
    {
      String pkg = ReaderPageCaptureService.latestPackage();
      String label = pkg.isEmpty() ? getString(R.string.reader_title_page)
        : getString(R.string.reader_title_page) + " · " + pkg;
      return ReaderAiService.Article.page(label, page);
    }
    return new ReaderAiService.Article(null,
        getString(R.string.reader_title_clipboard), "", "", "",
        ReaderAiRequest.contentHash(""), "");
  }

  private void shareClipboard()
  {
    ReaderTextAccess.Result result = ReaderTextAccess.readClipboardOrPage(this);
    if (!result.isSuccess())
    {
      Toast.makeText(this, R.string.reader_ai_clipboard_empty,
          Toast.LENGTH_SHORT).show();
      finish();
      return;
    }
    Intent send = new Intent(Intent.ACTION_SEND)
      .setType("text/plain")
      .putExtra(Intent.EXTRA_TEXT, result.text);
    startActivity(Intent.createChooser(send,
          getString(R.string.reader_ai_share_clipboard)));
    finish();
  }

  private void speedReadClipboard()
  {
    ReaderTextAccess.Result result = ReaderTextAccess.readClipboardOrPage(this);
    if (!result.isSuccess())
    {
      Toast.makeText(this, R.string.reader_ai_clipboard_empty,
          Toast.LENGTH_SHORT).show();
      finish();
      return;
    }
    ReaderActivity.startQuickRead(this,
        "quick-read:" + System.currentTimeMillis(),
        getString(R.string.reader_title_clipboard), result.text);
    finish();
  }

  private void readClipboardAloud()
  {
    ReaderTextAccess.Result result = ReaderTextAccess.readClipboardOrPage(this);
    if (!result.isSuccess())
    {
      Toast.makeText(this, R.string.reader_ai_clipboard_empty,
          Toast.LENGTH_SHORT).show();
      finish();
      return;
    }
    Keyboard2.startReaderText(this,
        "quick-read:" + System.currentTimeMillis(),
        getString(R.string.reader_title_clipboard), result.text);
    finish();
  }
}
