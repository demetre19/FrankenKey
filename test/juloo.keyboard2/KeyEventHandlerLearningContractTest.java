package juloo.keyboard2;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.SurroundingText;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import juloo.keyboard2.suggestions.Decoder;
import juloo.keyboard2.suggestions.PersonalizationStore;
import juloo.keyboard2.suggestions.SharedDecoder;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
public class KeyEventHandlerLearningContractTest
{
  private final List<SharedDecoder> _decoders = new ArrayList<SharedDecoder>();

  @After
  public void tearDown()
  {
    for (SharedDecoder decoder : _decoders)
      decoder.close();
  }

  @Test
  public void stale_candidate_action_is_ignored_but_current_request_commits()
      throws Exception
  {
    Harness harness = harness("caz", true, true);
    Decoder.RequestKey stale = harness.decoder.current_key();
    Decoder.RequestKey current = harness.decoder.request(harness.session,
        snapshot(2, "cazo"));

    harness.handler.suggestion_entered(stale, "stale");
    assertEquals("A candidate from an older word revision must not alter editor text.",
        "caz", harness.receiver.input.text.toString());
    assertEquals("Stale candidate actions must not create a replacement commit.",
        0, harness.receiver.input.commitTextCalls);

    harness.handler.suggestion_entered(current, "cazoo");
    assertEquals("The exact current request key may commit its visible candidate and separator.",
        "cazoo ", harness.receiver.input.text.toString());
    assertEquals(1, harness.receiver.input.commitTextCalls);
    assertFalse("Committing a candidate must revoke the key so a repeated click cannot commit twice.",
        harness.decoder.is_current(current));
  }

  @Test
  public void learning_gate_requires_active_session_enabled_suggestions_safe_editor_and_plain_word()
      throws Exception
  {
    Harness harness = harness("cazoo", true, true);

    assertTrue("A plain current word in a safe active suggestions session may be learned or unlearned.",
        harness.handler.can_change_learning("cazoo"));
    assertFalse("Single-letter tokens are not stable personalization entries.",
        harness.handler.can_change_learning("a"));
    assertFalse("Punctuation-bearing tokens must not enter the typing model.",
        harness.handler.can_change_learning("can't"));
    assertFalse("Technical URL text must not enter personalization.",
        harness.handler.can_change_learning("https://example.test"));

    harness.config.suggestions_enabled = false;
    assertFalse("Turning Suggestions off must disable learning even if the decoder session remains alive.",
        harness.handler.can_change_learning("cazoo"));
    harness.config.suggestions_enabled = true;
    harness.config.editor_config.should_use_typing_assistance = false;
    assertFalse("Passwords and other unsafe editors must never feed personalization.",
        harness.handler.can_change_learning("cazoo"));

    harness.config.editor_config.should_use_typing_assistance = true;
    harness.handler.finished();
    assertFalse("Finishing the editor must revoke learning eligibility immediately.",
        harness.handler.can_change_learning("cazoo"));
  }

  @Test
  public void learned_candidate_toggle_requires_current_positive_confirmation()
      throws Exception
  {
    Harness current = harness("cazoo", true, true, "casoo");
    Decoder.RequestKey key = current.decoder.current_key();
    awaitResult(current.decoder, key);
    installLiteral(current.decoder, key, "cazoo", true, true);

    current.handler.suggestion_swiped_up(key, "cazoo");

    assertEquals("A learned candidate toggle must ask about the exact visible word once.",
        1, current.receiver.confirmationCalls);
    assertEquals("The confirmation prompt must identify the exact learned candidate.",
        "cazoo", current.receiver.confirmationWord);
    assertCountsRemain(current.prefs, "cazoo", "casoo", 1, 1);

    current.receiver.cancelConfirmation();
    assertCountsRemain(current.prefs, "cazoo", "casoo", 1, 1);

    current.handler.suggestion_swiped_up(key, "cazoo");
    current.receiver.confirmPositive();

    awaitCounts(current.prefs, "cazoo", "casoo", 0, 0);

    Harness stale = harness("cazoo", true, true, "casoo");
    Decoder.RequestKey staleKey = stale.decoder.current_key();
    awaitResult(stale.decoder, staleKey);
    installLiteral(stale.decoder, staleKey, "cazoo", true, true);
    stale.handler.suggestion_swiped_up(staleKey, "cazoo");
    Decoder.RequestKey replacementKey = stale.decoder.request(stale.session,
        snapshot(99, "cazoon"));
    assertFalse("The fixture must advance away from the key whose confirmation is delayed.",
        staleKey.equals(replacementKey));

    stale.receiver.confirmPositive();

    assertCountsRemain(stale.prefs, "cazoo", "casoo", 1, 1);
  }

  @Test
  public void manual_same_word_backspace_edit_boundary_records_one_pair()
      throws Exception
  {
    Harness harness = harness("thus", true, true);

    backspace(harness);
    backspace(harness);
    harness.handler.send_text("is");
    assertEquals("The manual edit fixture must visibly change thus to this within the same editor word.",
        "this", harness.receiver.input.text.toString());

    harness.handler.handle_space_bar();

    assertEquals("The edited word and its accepted boundary must remain visible.",
        "this ", harness.receiver.input.text.toString());
    awaitCounts(harness.prefs, "this", "thus", 0, 1);
    assertCountsRemain(harness.prefs, "this", "thus", 0, 1);
  }

  @Test
  public void ambiguous_broad_or_cross_boundary_manual_edits_fail_closed()
      throws Exception
  {
    Harness broad = harness("bread", true, true);
    backspace(broad);
    backspace(broad);
    backspace(broad);
    broad.handler.send_text("xyz");
    installCurrentLiteral(broad, "brxyz");

    broad.handler.handle_space_bar();

    awaitCounts(broad.prefs, "brxyz", "bread", 1, 0);
    assertEquals("A three-substitution edit may learn its accepted target but must not invent a typo pair.",
        0, correctionCount(broad.prefs, "bread", "brxyz"));

    Harness crossed = harness("thus", true, true);
    backspace(crossed);
    backspace(crossed);
    crossed.handler.handle_space_bar();
    crossed.handler.send_text("this");
    installCurrentLiteral(crossed, "this");

    crossed.handler.handle_space_bar();

    assertEquals("The cross-boundary fixture must preserve both distinct editor tokens.",
        "th this ", crossed.receiver.input.text.toString());
    awaitCounts(crossed.prefs, "this", "thus", 1, 0);
    assertCountsRemain(crossed.prefs, "this", "thus", 1, 0);
  }

  private static void backspace(Harness harness)
  {
    harness.handler.key_up(KeyValue.getSpecialKeyByName("backspace"),
        Pointers.Modifiers.EMPTY, null);
  }

  private Harness harness(String text, boolean suggestions, boolean safeEditor)
      throws Exception
  {
    return harness(text, suggestions, safeEditor, null);
  }

  private Harness harness(String text, boolean suggestions, boolean safeEditor,
      String seededCorrectionSource)
      throws Exception
  {
    Context context = RuntimeEnvironment.getApplication();
    SharedPreferences prefs = context.getSharedPreferences(
        "key_event_learning_" + _decoders.size(), Context.MODE_PRIVATE);
    prefs.edit().clear().commit();
    if (seededCorrectionSource != null)
      new PersonalizationStore(prefs).record_commit(text,
          seededCorrectionSource);
    Constructor<Config> constructor = Config.class.getDeclaredConstructor(
        SharedPreferences.class, Resources.class, Boolean.class,
        juloo.keyboard2.dict.Dictionaries.class);
    constructor.setAccessible(true);
    Config config = constructor.newInstance(prefs,
        new TestResources(context.getResources()), Boolean.FALSE, null);
    config.suggestions_enabled = suggestions;
    config.autocorrect_enabled = false;
    config.editor_config.should_show_candidates_view = true;
    config.editor_config.should_use_typing_assistance = safeEditor;
    config.editor_config.initial_text_before_cursor = text;
    config.editor_config.initial_text_after_cursor = "";
    config.editor_config.initial_sel_start = text.length();
    config.editor_config.initial_sel_end = text.length();

    RecordingReceiver receiver = new RecordingReceiver(text);
    SharedDecoder decoder = new SharedDecoder(receiver.handler,
        new SharedDecoder.Callback()
        {
          @Override
          public void decoder_state_changed(SharedDecoder.Presentation state) {}
        });
    _decoders.add(decoder);
    long session = decoder.start_session(
        new Decoder.DecoderConfig(suggestions, false, true, safeEditor),
        SharedDecoder.ResourceSpec.empty("empty"), null,
        new SharedDecoder.PersonalizationSpec(
          "learning-" + _decoders.size(), prefs));
    KeyEventHandler handler = new KeyEventHandler(receiver, decoder);
    config.handler = handler;
    handler.started(config, session);
    return new Harness(config, receiver, prefs, decoder, handler, session);
  }

  private static CurrentlyTypedWord.Snapshot snapshot(long revision,
      String word)
      throws Exception
  {
    Constructor<CurrentlyTypedWord.Snapshot> constructor =
      CurrentlyTypedWord.Snapshot.class.getDeclaredConstructor(long.class,
          String.class, int.class, boolean.class, TouchTrace.Snapshot.class);
    constructor.setAccessible(true);
    return constructor.newInstance(revision, word, 0, false,
        new TouchTrace().snapshot());
  }

  private static Decoder.RequestKey installCurrentLiteral(Harness harness,
      String word)
      throws Exception
  {
    Decoder.RequestKey key = harness.handler._current_request_key;
    assertNotNull("A completed manual word must own a current decoder request.",
        key);
    awaitResult(harness.decoder, key);
    installLiteral(harness.decoder, key, word, true, false);
    return key;
  }

  private static void installLiteral(SharedDecoder decoder,
      Decoder.RequestKey key, String word, boolean recognized, boolean learned)
      throws Exception
  {
    Decoder.Candidate literal = candidate(Decoder.normalize(word), word,
        Decoder.SOURCE_LITERAL, recognized, learned,
        Decoder.Role.ENTERED_LITERAL);
    Constructor<Decoder.Result> constructor = Decoder.Result.class
      .getDeclaredConstructor(Decoder.RequestKey.class, String.class,
          Decoder.Candidate[].class, String.class, Decoder.Candidate.class,
          Decoder.Candidate.class, boolean.class, Decoder.Failure.class);
    constructor.setAccessible(true);
    Decoder.Result result = constructor.newInstance(key, word,
        new Decoder.Candidate[] { literal }, null, literal, null, true,
        Decoder.Failure.NONE);
    for (String fieldName : new String[] {
        "_acceptedResult", "_lastCompletedResult" })
    {
      Field field = SharedDecoder.class.getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(decoder, result);
    }
  }

  private static Decoder.Candidate candidate(String canonical, String surface,
      int sourceMask, boolean recognized, boolean learned, Decoder.Role role)
      throws Exception
  {
    Constructor<Decoder.Candidate> constructor = Decoder.Candidate.class
      .getDeclaredConstructor(String.class, String.class, int.class, int.class,
          int.class, int.class, int.class, int.class, int.class, int.class,
          int.class, int.class, int.class, int.class, int.class,
          Decoder.Role.class, boolean.class, boolean.class, boolean.class);
    constructor.setAccessible(true);
    return constructor.newInstance(canonical, surface, sourceMask, -1, 0, 0,
        learned ? 1 : 0, 0, 0, 0, 0, 0, 0, 0, 0, role, recognized, learned,
        true);
  }

  private static Decoder.Result awaitResult(SharedDecoder decoder,
      Decoder.RequestKey key)
      throws Exception
  {
    long deadline = System.nanoTime() + 3_000_000_000L;
    do
    {
      Decoder.Result result = decoder.current_result(key);
      if (result != null)
        return result;
      Thread.sleep(2L);
    }
    while (System.nanoTime() < deadline);
    fail("Timed out waiting for decoder fixture result");
    return null;
  }

  private static void awaitCounts(SharedPreferences prefs, String target,
      String source, int expectedWordCount, int expectedCorrectionCount)
      throws Exception
  {
    long deadline = System.nanoTime() + 3_000_000_000L;
    do
    {
      if (wordCount(prefs, target) == expectedWordCount
          && correctionCount(prefs, source, target) == expectedCorrectionCount)
        return;
      Thread.sleep(2L);
    }
    while (System.nanoTime() < deadline);
    fail("Timed out waiting for personalization counts target=" + target
        + " source=" + source + " expected=" + expectedWordCount + "/"
        + expectedCorrectionCount + " actual=" + wordCount(prefs, target)
        + "/" + correctionCount(prefs, source, target));
  }

  private static void assertCountsRemain(SharedPreferences prefs,
      String target, String source, int expectedWordCount,
      int expectedCorrectionCount)
      throws Exception
  {
    long deadline = System.nanoTime() + 100_000_000L;
    do
    {
      assertEquals("The accepted word count must remain stable.",
          expectedWordCount, wordCount(prefs, target));
      assertEquals("The correction-pair count must remain stable.",
          expectedCorrectionCount, correctionCount(prefs, source, target));
      Thread.sleep(2L);
    }
    while (System.nanoTime() < deadline);
  }

  private static int wordCount(SharedPreferences prefs, String word)
      throws Exception
  {
    PersonalizationStore store = new PersonalizationStore(prefs);
    Method method = PersonalizationStore.class.getDeclaredMethod(
        "word_count", String.class);
    method.setAccessible(true);
    return ((Integer)method.invoke(store, word)).intValue();
  }

  private static int correctionCount(SharedPreferences prefs, String source,
      String target)
      throws Exception
  {
    PersonalizationStore store = new PersonalizationStore(prefs);
    Method method = PersonalizationStore.class.getDeclaredMethod(
        "correction_count", String.class, String.class);
    method.setAccessible(true);
    return ((Integer)method.invoke(store, source, target)).intValue();
  }

  private static final class Harness
  {
    final Config config;
    final RecordingReceiver receiver;
    final SharedPreferences prefs;
    final SharedDecoder decoder;
    final KeyEventHandler handler;
    final long session;

    Harness(Config config_, RecordingReceiver receiver_, SharedPreferences prefs_,
        SharedDecoder decoder_, KeyEventHandler handler_, long session_)
    {
      config = config_;
      receiver = receiver_;
      prefs = prefs_;
      decoder = decoder_;
      handler = handler_;
      session = session_;
    }
  }

  private static final class RecordingReceiver
      implements KeyEventHandler.IReceiver
  {
    final Handler handler = new Handler(Looper.getMainLooper());
    final RecordingInputConnection input;
    String confirmationWord;
    Runnable confirmationAction;
    int confirmationCalls;

    RecordingReceiver(String text)
    {
      input = new RecordingInputConnection(text);
    }

    void cancelConfirmation()
    {
      confirmationAction = null;
    }

    void confirmPositive()
    {
      Runnable action = confirmationAction;
      confirmationAction = null;
      assertNotNull("A positive confirmation requires a captured destructive action.",
          action);
      action.run();
    }

    @Override public void handle_event_key(KeyValue.Event event) {}
    @Override public void set_shift_state(boolean state, boolean lock) {}
    @Override public void set_compose_pending(boolean pending) {}
    @Override public void selection_state_changed(boolean ongoing) {}
    @Override public void confirm_unlearn_word(String word,
        Runnable positiveAction)
    {
      confirmationWord = word;
      confirmationAction = positiveAction;
      ++confirmationCalls;
    }
    @Override public RecordingInputConnection getCurrentInputConnection()
    {
      return input;
    }
    @Override public EditorInfo getCurrentInputEditorInfo()
    {
      return new EditorInfo();
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
    int commitTextCalls;

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
    public SurroundingText getSurroundingText(int beforeLength,
        int afterLength, int flags)
    {
      int start = Math.max(0, cursor - beforeLength);
      int end = Math.min(text.length(), cursor + afterLength);
      return new SurroundingText(text.substring(start, end),
          cursor - start, cursor - start, start);
    }

    @Override public boolean setSelection(int start, int end)
    {
      if (start < 0 || end < 0 || start > text.length() || end > text.length())
        return false;
      selectionStart = start;
      selectionEnd = end;
      cursor = end;
      return true;
    }

    @Override public boolean deleteSurroundingText(int before, int after)
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

    @Override public boolean commitText(CharSequence value,
        int newCursorPosition)
    {
      commitTextCalls++;
      int start = Math.min(selectionStart, selectionEnd);
      int end = Math.max(selectionStart, selectionEnd);
      text.replace(start, end, value.toString());
      cursor = start + value.length();
      selectionStart = cursor;
      selectionEnd = cursor;
      return true;
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
    @Override public boolean beginBatchEdit() { return true; }
    @Override public boolean endBatchEdit() { return true; }
    @Override public boolean finishComposingText() { return true; }
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
