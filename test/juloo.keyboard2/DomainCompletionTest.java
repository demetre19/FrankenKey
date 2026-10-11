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
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import juloo.keyboard2.suggestions.CandidateSource;
import juloo.keyboard2.suggestions.Decoder;
import juloo.keyboard2.suggestions.DomainSuggestions;
import juloo.keyboard2.suggestions.EditorContext;
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
public class DomainCompletionTest
{
  private final List<SharedDecoder> _decoders = new ArrayList<SharedDecoder>();

  @After
  public void tearDown()
  {
    for (SharedDecoder decoder : _decoders)
      decoder.close();
  }

  // --- Span detection ----------------------------------------------------

  @Test
  public void domain_span_detection()
  {
    assertEquals(4, DomainSuggestions.domain_span_start("bob@"));
    assertEquals(4, DomainSuggestions.domain_span_start("bob@gm"));
    assertEquals(4, DomainSuggestions.domain_span_start("bob@gmail."));
    assertEquals(4, DomainSuggestions.domain_span_start("bob@gmail.c"));
    assertEquals(3, DomainSuggestions.domain_span_start(" x@a"));
    assertEquals(-1, DomainSuggestions.domain_span_start(""));
    assertEquals(-1, DomainSuggestions.domain_span_start("bob"));
    assertEquals("The last @ owns the domain segment, so a@b@c completes c.",
        4, DomainSuggestions.domain_span_start("a@b@c"));
    assertEquals("Whitespace after @ leaves the domain segment.",
        -1, DomainSuggestions.domain_span_start("bob@gm ail"));
    assertEquals("A leading dot is not a domain label.",
        -1, DomainSuggestions.domain_span_start("bob@.com"));
    assertEquals("Doubled dots are not a valid domain segment.",
        -1, DomainSuggestions.domain_span_start("bob@g..c"));
    assertEquals("A dot directly before @ belongs to the local part edge.",
        -1, DomainSuggestions.domain_span_start("bob.@g"));
  }

  // --- Pinned candidates -------------------------------------------------

  private static EditorContext ctx(String before,
      EditorContext.EditorClass editorClass)
  {
    return new EditorContext(editorClass, false, before, "", null);
  }

  private static List<String> surfaces(CandidateSource.Candidate[] pinned)
  {
    List<String> out = new ArrayList<String>();
    for (CandidateSource.Candidate c : pinned)
      out.add(c.surface);
    return out;
  }

  @Test
  public void pinned_offers_ranked_domains_for_email_editors()
  {
    DomainSuggestions source = new DomainSuggestions();
    List<String> empty = surfaces(source.pinned(null,
        ctx("bob@", EditorContext.EditorClass.EMAIL)));
    assertEquals("Nothing after @ must pin the top strip of ranked domains.",
        Decoder.MAX_VISIBLE_WORDS, empty.size());
    assertEquals("gmail.com", empty.get(0));
    assertEquals("outlook.com", empty.get(1));

    List<String> g = surfaces(source.pinned(null,
        ctx("bob@g", EditorContext.EditorClass.EMAIL)));
    assertEquals("gmail.com", g.get(0));
    assertFalse(g.contains("outlook.com"));

    List<String> partial = surfaces(source.pinned(null,
        ctx("bob@gmail.c", EditorContext.EditorClass.EMAIL)));
    assertEquals("gmail.com", partial.get(0));
    assertEquals(1, partial.size());

    List<String> exact = surfaces(source.pinned(null,
        ctx("bob@icloud.com", EditorContext.EditorClass.EMAIL)));
    assertEquals(java.util.Arrays.asList("icloud.com"), exact);

    assertTrue("No match means no pinned candidates.", surfaces(
        source.pinned(null, ctx("bob@zzz", EditorContext.EditorClass.EMAIL)))
        .isEmpty());
    assertTrue("Prose editors must keep ordinary suggestions.", surfaces(
        source.pinned(null, ctx("bob@g", EditorContext.EditorClass.PROSE)))
        .isEmpty());
    assertTrue("URI editors must keep ordinary suggestions.", surfaces(
        source.pinned(null, ctx("bob@g", EditorContext.EditorClass.URI)))
        .isEmpty());
    assertTrue("Malformed suffixes pin nothing.", surfaces(
        source.pinned(null, ctx("bob@x y", EditorContext.EditorClass.EMAIL)))
        .isEmpty());
  }

  // --- Commit behavior ----------------------------------------------------

  private static final class Harness
  {
    final Config config;
    final RecordingReceiver receiver;
    final SharedPreferences prefs;
    final SharedDecoder decoder;
    final KeyEventHandler handler;
    final long session;

    Harness(Config config_, RecordingReceiver receiver_,
        SharedPreferences prefs_, SharedDecoder decoder_,
        KeyEventHandler handler_, long session_)
    {
      config = config_;
      receiver = receiver_;
      prefs = prefs_;
      decoder = decoder_;
      handler = handler_;
      session = session_;
    }
  }

  private Harness harness(String text) throws Exception
  {
    Context context = RuntimeEnvironment.getApplication();
    SharedPreferences prefs = context.getSharedPreferences(
        "domain-test-" + _decoders.size(), Context.MODE_PRIVATE);
    Constructor<Config> constructor = Config.class.getDeclaredConstructor(
        SharedPreferences.class, Resources.class, Boolean.class,
        juloo.keyboard2.dict.Dictionaries.class);
    constructor.setAccessible(true);
    Config config = constructor.newInstance(prefs,
        new TestResources(context.getResources()), Boolean.FALSE, null);
    config.suggestions_enabled = true;
    config.autocorrect_enabled = true;
    config.editor_config.should_show_candidates_view = true;
    config.editor_config.should_use_typing_assistance = true;
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
    decoder.register_source(new DomainSuggestions());
    long session = decoder.start_session(
        new Decoder.DecoderConfig(true, true, true, true),
        SharedDecoder.ResourceSpec.empty("empty"), null,
        new SharedDecoder.PersonalizationSpec(
          "domain-" + _decoders.size(), prefs));
    receiver.decoder = decoder;
    receiver.session = session;
    KeyEventHandler handler = new KeyEventHandler(receiver, decoder);
    config.handler = handler;
    handler.started(config, session);
    return new Harness(config, receiver, prefs, decoder, handler, session);
  }

  private static void awaitResult(SharedDecoder decoder,
      Decoder.RequestKey key) throws Exception
  {
    long deadline = System.nanoTime() + 3_000_000_000L;
    do
    {
      if (decoder.current_result(key) != null)
        return;
      Thread.sleep(2L);
    }
    while (System.nanoTime() < deadline);
    fail("Timed out waiting for decoder fixture result");
  }

  private static CurrentlyTypedWord.Snapshot snapshot(long revision,
      String word) throws Exception
  {
    Constructor<CurrentlyTypedWord.Snapshot> constructor =
      CurrentlyTypedWord.Snapshot.class.getDeclaredConstructor(long.class,
          String.class, int.class, boolean.class, TouchTrace.Snapshot.class);
    constructor.setAccessible(true);
    return constructor.newInstance(revision, word, 0, false,
        new TouchTrace().snapshot());
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

  @Test
  public void domain_candidate_replaces_at_span_in_one_commit()
      throws Exception
  {
    Harness harness = harness("bob@gmail");
    Decoder.RequestKey key = harness.handler._current_request_key;
    assertNotNull(key);
    awaitResult(harness.decoder, key);

    harness.handler.domain_suggestion_entered(key, "gmail.com");

    assertEquals("The @ span must be replaced, never appended.",
        "bob@gmail.com ", harness.receiver.input.text.toString());
    assertEquals(1, harness.receiver.input.commitTextCalls);
    assertEquals("Provider domains must not enter the learned-words store.",
        0, wordCount(harness.prefs, "gmail.com"));
  }

  @Test
  public void domain_candidate_replaces_dotted_suffix()
      throws Exception
  {
    Harness harness = harness("bob@gmail.c");
    Decoder.RequestKey key = harness.handler._current_request_key;
    assertNotNull(key);
    awaitResult(harness.decoder, key);

    harness.handler.domain_suggestion_entered(key, "gmail.com");

    assertEquals("The whole suffix after @ must be replaced.",
        "bob@gmail.com ", harness.receiver.input.text.toString());
  }

  @Test
  public void domain_candidate_after_bare_at_commits_domain()
      throws Exception
  {
    Harness harness = harness("bob@");
    Decoder.RequestKey key = harness.handler._current_request_key;
    assertNotNull(key);
    awaitResult(harness.decoder, key);

    harness.handler.domain_suggestion_entered(key, "gmail.com");

    assertEquals("bob@gmail.com ", harness.receiver.input.text.toString());
  }

  @Test
  public void stale_domain_candidate_never_mutates_editor() throws Exception
  {
    Harness harness = harness("bob@gm");
    Decoder.RequestKey stale = harness.handler._current_request_key;
    assertNotNull(stale);
    awaitResult(harness.decoder, stale);
    Decoder.RequestKey current = harness.decoder.request(harness.session,
        snapshot(2, "gma"));

    harness.handler.domain_suggestion_entered(stale, "gmail.com");

    assertEquals("A stale candidate must not alter editor text.",
        "bob@gm", harness.receiver.input.text.toString());
    assertEquals(0, harness.receiver.input.commitTextCalls);
  }

  // --- Fixtures -----------------------------------------------------------

  private static final class RecordingReceiver
      implements KeyEventHandler.IReceiver
  {
    final Handler handler = new Handler(Looper.getMainLooper());
    final RecordingInputConnection input;
    SharedDecoder decoder;
    long session;

    RecordingReceiver(String text)
    {
      input = new RecordingInputConnection(text);
    }

    @Override public void handle_event_key(KeyValue.Event event) {}
    @Override public void set_shift_state(boolean state, boolean lock) {}
    @Override public void set_compose_pending(boolean pending) {}
    @Override public void selection_state_changed(boolean ongoing) {}
    @Override public void confirm_unlearn_word(String word,
        Runnable positiveAction) {}
    @Override public void review_unknown_word(String word,
        Runnable learnAction, Runnable bestAction) {}
    @Override public boolean explicitly_teach_word(Decoder.RequestKey key,
        String word) { return false; }
    @Override public boolean explicitly_set_replacement(
        String source, String target) { return false; }
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
