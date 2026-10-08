package juloo.keyboard2;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import juloo.keyboard2.suggestions.Decoder;
import juloo.keyboard2.suggestions.SharedDecoder;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import static org.junit.Assert.*;

/**
 * Typing an email address must never be rewritten by autocorrect. The "@"
 * sign splits the tracked word, so suppression is decided from the
 * whitespace-delimited token under the cursor in the editor, not from
 * [snapshot.word] alone.
 */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
public class KeyEventHandlerEmailSuppressionTest
{
  private final List<SharedDecoder> _decoders = new ArrayList<SharedDecoder>();

  @After
  public void tearDown()
  {
    for (SharedDecoder decoder : _decoders)
      decoder.close();
  }

  @Test
  public void ready_correction_is_suppressed_on_a_typed_email_token()
      throws Exception
  {
    Harness harness = harness("send to john.doe@gm", true, true);
    installCorrection(harness.decoder, harness.key, "gm", "gym");

    harness.handler.handle_space_bar();

    assertEquals("An email address token must never be autocorrected.",
        "send to john.doe@gm ", harness.receiver.input.text.toString());
    assertEquals("Suppression must not emit an editor correction flash.",
        0, harness.receiver.input.commitCorrectionCalls);
  }

  @Test
  public void pending_late_correction_is_suppressed_on_a_typed_email_token()
      throws Exception
  {
    Harness harness = harness("send to john.doe@gm", true, true);
    clearResult(harness.decoder);

    harness.handler.handle_space_bar();
    assertEquals("The separator must appear immediately while the result is pending.",
        "send to john.doe@gm ", harness.receiver.input.text.toString());

    Decoder.Result late = correctionResult(harness.key, "gm", "gym");
    installResult(harness.decoder, late);
    harness.handler.decoder_result_ready(late);

    assertEquals("A late READY result must not rewrite an email address token.",
        "send to john.doe@gm ", harness.receiver.input.text.toString());
    assertEquals(0, harness.receiver.input.commitCorrectionCalls);
  }

  @Test
  public void email_token_inside_a_url_is_also_suppressed()
      throws Exception
  {
    Harness harness = harness("open site.com/u@x", true, true);
    installCorrection(harness.decoder, harness.key, "x", "a");

    harness.handler.handle_space_bar();

    assertEquals("Any whitespace-delimited token containing '@' keeps its literal text.",
        "open site.com/u@x ", harness.receiver.input.text.toString());
    assertEquals(0, harness.receiver.input.commitCorrectionCalls);
  }

  @Test
  public void normal_prose_word_after_an_email_still_corrects()
      throws Exception
  {
    Harness harness = harness("mail john@doe.com teh", true, true);
    installCorrection(harness.decoder, harness.key, "teh", "the");

    harness.handler.handle_space_bar();

    assertEquals("Suppression is token-scoped: a plain word after an email still corrects.",
        "mail john@doe.com the ", harness.receiver.input.text.toString());
    assertEquals(1, harness.receiver.input.commitCorrectionCalls);
  }

  private Harness harness(String text, boolean autocorrect,
      boolean safeEditor)
      throws Exception
  {
    Context context = RuntimeEnvironment.getApplication();
    SharedPreferences prefs = context.getSharedPreferences(
        "key_event_email_suppression_" + _decoders.size(),
        Context.MODE_PRIVATE);
    prefs.edit().clear().commit();
    Constructor<Config> constructor = Config.class.getDeclaredConstructor(
        SharedPreferences.class, Resources.class, Boolean.class,
        juloo.keyboard2.dict.Dictionaries.class);
    constructor.setAccessible(true);
    Config config = constructor.newInstance(prefs,
        new TestResources(context.getResources()), Boolean.FALSE, null);
    config.suggestions_enabled = true;
    config.autocorrect_enabled = autocorrect;
    config.autocapitalisation = true;
    config.editor_config.caps_initially_updated = false;
    config.editor_config.should_show_candidates_view = true;
    config.editor_config.should_use_typing_assistance = safeEditor;
    config.editor_config.should_use_personalization = true;
    config.editor_config.should_use_sentence_assistance = safeEditor;
    config.editor_config.initial_text_before_cursor = text;
    config.editor_config.initial_text_after_cursor = "";
    config.editor_config.initial_sel_start = text.length();
    config.editor_config.initial_sel_end = text.length();

    RecordingReceiver receiver = new RecordingReceiver(text);
    final KeyEventHandler[] handler_ref = new KeyEventHandler[1];
    SharedDecoder decoder = new SharedDecoder(receiver.handler,
        new SharedDecoder.Callback()
        {
          @Override
          public void decoder_state_changed(SharedDecoder.Presentation state) {}

          @Override
          public void decoder_result_completed(Decoder.Result completed)
          {
            KeyEventHandler active_handler = handler_ref[0];
            if (completed != null && active_handler != null)
              active_handler.decoder_result_ready(completed);
          }
        });
    _decoders.add(decoder);
    long session = decoder.start_session(
        new Decoder.DecoderConfig(true, autocorrect, true, safeEditor),
        SharedDecoder.ResourceSpec.empty("empty"), null,
        new SharedDecoder.PersonalizationSpec(
          "test-" + _decoders.size(), prefs));
    KeyEventHandler handler = new KeyEventHandler(receiver, decoder);
    handler_ref[0] = handler;
    config.handler = handler;
    handler.started(config, session);

    Decoder.RequestKey key = decoder.current_key();
    if (key == null)
    {
      key = decoder.request(session, snapshot(1, text, false));
      handler._current_request_key = key;
    }
    awaitResult(decoder, key);
    return new Harness(config, receiver, decoder, handler, session, key);
  }

  private static Decoder.Result correctionResult(Decoder.RequestKey key,
      String queried, String corrected)
      throws Exception
  {
    Decoder.Candidate literal = candidate(Decoder.normalize(queried), queried,
        Decoder.SOURCE_LITERAL, 8192, 0, 0, false, false,
        Decoder.Role.ENTERED_LITERAL);
    Decoder.Candidate correction = candidate(Decoder.normalize(corrected),
        corrected, Decoder.SOURCE_CDICT_SPATIAL, 0, 1,
        Decoder.EDIT_TRANSPOSITION, true, false, Decoder.Role.WORD);
    return result(key, queried,
        new Decoder.Candidate[] { correction, literal }, literal, correction);
  }

  private static void installCorrection(SharedDecoder decoder,
      Decoder.RequestKey key, String queried, String corrected)
      throws Exception
  {
    installResult(decoder, correctionResult(key, queried, corrected));
  }

  private static Decoder.Result result(Decoder.RequestKey key, String queried,
      Decoder.Candidate[] words, Decoder.Candidate literal,
      Decoder.Candidate correction)
      throws Exception
  {
    Constructor<Decoder.Result> constructor = Decoder.Result.class
      .getDeclaredConstructor(Decoder.RequestKey.class, String.class,
          Decoder.Candidate[].class, String.class, Decoder.Candidate.class,
          Decoder.Candidate.class, boolean.class, boolean.class,
          Decoder.Failure.class);
    constructor.setAccessible(true);
    return constructor.newInstance(key, queried, words, null, literal,
        correction, true, true, Decoder.Failure.NONE);
  }

  private static void installResult(SharedDecoder decoder,
      Decoder.Result result)
      throws Exception
  {
    for (String fieldName : new String[] {
        "_acceptedResult", "_lastCompletedResult" })
    {
      Field field = SharedDecoder.class.getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(decoder, result);
    }
  }

  private static void clearResult(SharedDecoder decoder)
      throws Exception
  {
    installResult(decoder, null);
  }

  private static Decoder.Candidate candidate(String canonical, String surface,
      int sourceMask, int totalQ8, int editCount, int editMask,
      boolean recognized, boolean learned, Decoder.Role role)
      throws Exception
  {
    Constructor<Decoder.Candidate> constructor = Decoder.Candidate.class
      .getDeclaredConstructor(String.class, String.class, int.class, int.class,
          int.class, int.class, int.class, int.class, int.class, int.class,
          int.class, int.class, int.class, int.class, int.class,
          Decoder.Role.class, boolean.class, boolean.class, boolean.class);
    constructor.setAccessible(true);
    return constructor.newInstance(canonical, surface, sourceMask, -1, 0, 0,
        learned ? 1 : 0, 0, 0, 0, 0, 0, editCount, editMask, totalQ8, role,
        recognized, learned, true);
  }

  private static CurrentlyTypedWord.Snapshot snapshot(long revision,
      String word, boolean selection)
      throws Exception
  {
    Constructor<CurrentlyTypedWord.Snapshot> constructor =
      CurrentlyTypedWord.Snapshot.class.getDeclaredConstructor(long.class,
          String.class, int.class, boolean.class, TouchTrace.Snapshot.class);
    constructor.setAccessible(true);
    return constructor.newInstance(revision, word, 0, selection,
        new TouchTrace().snapshot());
  }

  private static void awaitResult(SharedDecoder decoder,
      Decoder.RequestKey key)
      throws Exception
  {
    long deadline = System.nanoTime() + 3_000_000_000L;
    do
    {
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      Decoder.Result result = decoder.current_result(key);
      if (result != null)
        return;
      Thread.sleep(2L);
    }
    while (System.nanoTime() < deadline);
    fail("Timed out waiting for decoder fixture result");
  }

  private static final class Harness
  {
    final Config config;
    final RecordingReceiver receiver;
    final SharedDecoder decoder;
    final KeyEventHandler handler;
    final long session;
    final Decoder.RequestKey key;

    Harness(Config config_, RecordingReceiver receiver_,
        SharedDecoder decoder_, KeyEventHandler handler_, long session_,
        Decoder.RequestKey key_)
    {
      config = config_;
      receiver = receiver_;
      decoder = decoder_;
      handler = handler_;
      session = session_;
      key = key_;
    }
  }

  private static final class RecordingReceiver
      implements KeyEventHandler.IReceiver
  {
    final Handler handler = new Handler(Looper.getMainLooper());
    final RecordingInputConnection input;
    final EditorInfo editorInfo = new EditorInfo();

    RecordingReceiver(String text)
    {
      input = new RecordingInputConnection(text);
    }

    @Override public void handle_event_key(KeyValue.Event event) {}
    @Override public void set_shift_state(boolean state, boolean lock) {}
    @Override public void set_compose_pending(boolean pending) {}
    @Override public void selection_state_changed(boolean ongoing) {}
    @Override public RecordingInputConnection getCurrentInputConnection()
    {
      return input;
    }
    @Override public EditorInfo getCurrentInputEditorInfo()
    {
      return editorInfo;
    }
    @Override public Handler getHandler()
    {
      return handler;
    }
  }

  private static final class RecordingInputConnection
      extends BaseInputConnection
  {
    final StringBuilder text = new StringBuilder();
    int cursor;
    int selectionStart;
    int selectionEnd;
    int commitCorrectionCalls;
    CorrectionInfo correctionInfo;

    RecordingInputConnection(String initial)
    {
      super(new View(RuntimeEnvironment.getApplication()), false);
      text.append(initial);
      cursor = text.length();
      selectionStart = cursor;
      selectionEnd = cursor;
    }

    @Override
    public ExtractedText getExtractedText(ExtractedTextRequest request,
        int flags)
    {
      ExtractedText out = new ExtractedText();
      out.text = text.toString();
      out.startOffset = 0;
      out.selectionStart = selectionStart;
      out.selectionEnd = selectionEnd;
      return out;
    }

    @Override
    public int getCursorCapsMode(int reqModes)
    {
      return 0;
    }

    @Override public CharSequence getTextBeforeCursor(int length, int flags)
    {
      return text.substring(Math.max(0, cursor - length), cursor);
    }

    @Override public CharSequence getTextAfterCursor(int length, int flags)
    {
      return text.substring(cursor, Math.min(text.length(), cursor + length));
    }

    @Override public CharSequence getSelectedText(int flags)
    {
      return text.substring(Math.min(selectionStart, selectionEnd),
          Math.max(selectionStart, selectionEnd));
    }

    @Override public boolean setSelection(int start, int end)
    {
      if (start < 0 || end < 0
          || start > text.length() || end > text.length())
        return false;
      selectionStart = start;
      selectionEnd = end;
      cursor = end;
      return true;
    }

    @Override
    public boolean deleteSurroundingText(int before, int after)
    {
      int start = Math.max(0, cursor - before);
      int end = Math.min(text.length(), cursor + after);
      text.delete(start, end);
      cursor = start;
      selectionStart = cursor;
      selectionEnd = cursor;
      return true;
    }

    @Override
    public boolean deleteSurroundingTextInCodePoints(int before, int after)
    {
      return deleteSurroundingText(before, after);
    }

    @Override
    public boolean commitText(CharSequence value, int newCursorPosition)
    {
      int start = Math.min(selectionStart, selectionEnd);
      int end = Math.max(selectionStart, selectionEnd);
      text.replace(start, end, value.toString());
      cursor = start + value.length();
      selectionStart = cursor;
      selectionEnd = cursor;
      return true;
    }

    @Override
    public boolean commitCorrection(CorrectionInfo info)
    {
      commitCorrectionCalls++;
      correctionInfo = info;
      return true;
    }

    @Override public boolean beginBatchEdit() { return true; }
    @Override public boolean endBatchEdit() { return true; }
    @Override public boolean finishComposingText() { return true; }
    @Override public boolean sendKeyEvent(KeyEvent event)
    {
      if (event.getKeyCode() == KeyEvent.KEYCODE_DEL
          && event.getAction() == KeyEvent.ACTION_DOWN && cursor > 0)
      {
        int start = text.offsetByCodePoints(cursor, -1);
        text.delete(start, cursor);
        cursor = start;
        selectionStart = cursor;
        selectionEnd = cursor;
      }
      return true;
    }
  }

  private static final class TestResources extends Resources
  {
    TestResources(Resources base)
    {
      super(base.getAssets(), base.getDisplayMetrics(), base.getConfiguration());
    }

    @Override public float getDimension(int id) { return 1f; }
  }
}
