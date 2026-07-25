package juloo.keyboard2;

import android.view.inputmethod.SurroundingText;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
public class CurrentlyTypedWordTest
{
  public CurrentlyTypedWordTest() {}

  @Test
  public void separator_clears_previous_word_before_next_word_is_typed()
  {
    RecordingCallback callback = new RecordingCallback();
    CurrentlyTypedWord typedWord = enabledTypedWord(callback);

    typedWord.typed("this");
    typedWord.typed(" ");

    assertEquals("A typed separator ends the active word immediately so the next token cannot merge with it.",
        "", typedWord.get());
    assertEquals(Arrays.asList("this", ""), callback.words);

    typedWord.typed("is");

    assertEquals("The word after a separator starts from an empty boundary, not from the previous token.",
        "is", typedWord.get());
    assertEquals(Arrays.asList("this", "", "is"), callback.words);
  }

  @Test
  public void incremental_sentence_typing_publishes_only_current_token_after_boundaries()
  {
    RecordingCallback callback = new RecordingCallback();
    CurrentlyTypedWord typedWord = enabledTypedWord(callback);
    String input = "this is still not good.";
    List<String> expectedWords = Arrays.asList(
        "t", "th", "thi", "this", "",
        "i", "is", "",
        "s", "st", "sti", "stil", "still", "",
        "n", "no", "not", "",
        "g", "go", "goo", "good", "");

    for (int i = 0; i < input.length(); ++i)
      typedWord.typed(input.substring(i, i + 1));

    assertEquals("Each separator must publish an empty current word, and subsequent letters must publish only the new token.",
        expectedWords, callback.words);
    assertEquals("Sentence-ending punctuation leaves no active word.",
        "", typedWord.get());
  }

  @Test
  public void char_sequence_refresh_returns_only_final_word_before_cursor()
  {
    RecordingCallback callback = new RecordingCallback();
    CurrentlyTypedWord typedWord = new CurrentlyTypedWord(null, callback);

    typedWord.set_current_word("alpha beta gamma");

    assertEquals("Refreshing from editor text before the cursor exposes only the final token.",
        "gamma", typedWord.get());
    assertEquals(Arrays.asList("gamma"), callback.words);
  }

  @Test
  public void capped_readback_marks_suffix_of_25_character_editor_word_incomplete()
  {
    RecordingCallback callback = new RecordingCallback();
    CurrentlyTypedWord typedWord = enabledTypedWord(callback);
    String editorWord = repeated('a', 25);

    typedWord.set_current_word(editorWord.substring(5));

    CurrentlyTypedWord.Snapshot snapshot = callback.latest();
    assertEquals("A 20-character readback cap may expose only the suffix of a 25-character editor word.",
        editorWord.substring(5), snapshot.word);
    assertEquals("A word-character at the capped left edge must be reported as incomplete rather than trusted as the whole visible word.",
        CurrentlyTypedWord.WordCompleteness.INCOMPLETE,
        snapshot.completeness);
    assertFalse("Consumers must be able to fail closed when a capped editor suffix is incomplete.",
        snapshot.completeness.isComplete());

    typedWord.typed("b");

    assertEquals("Appending a locally typed letter cannot recover the prefix omitted by editor readback.",
        CurrentlyTypedWord.WordCompleteness.INCOMPLETE,
        callback.latest().completeness);
  }

  @Test
  public void capped_surrounding_readback_marks_suffix_of_40_character_editor_word_incomplete()
  {
    RecordingCallback callback = new RecordingCallback();
    CurrentlyTypedWord typedWord = new CurrentlyTypedWord(null, callback);
    String editorWord = repeated('b', 40);

    typedWord.set_current_word(new SurroundingText(
        editorWord.substring(5), 20, 20, 5));

    CurrentlyTypedWord.Snapshot snapshot = callback.latest();
    assertEquals("Surrounding readback may contain a long suffix around the cursor even though five leading characters were capped away.",
        editorWord.substring(5), snapshot.word);
    assertEquals("A nonzero surrounding-text offset and word-character left edge prove the 40-character editor word is incomplete.",
        CurrentlyTypedWord.WordCompleteness.INCOMPLETE,
        snapshot.completeness);
    assertFalse("An editor-derived long suffix must not advertise itself as a complete correction source.",
        snapshot.completeness.isComplete());
  }

  @Test
  public void capped_context_with_a_boundary_keeps_the_cursor_word_complete()
  {
    String cappedBefore = "1234567 typed fixinf";
    assertEquals(CurrentlyTypedWord.EDITOR_CONTEXT_LENGTH,
        cappedBefore.length());

    RecordingCallback beforeCallback = new RecordingCallback();
    CurrentlyTypedWord beforeWord =
      new CurrentlyTypedWord(null, beforeCallback);
    beforeWord.set_current_word(cappedBefore);
    assertEquals("fixinf", beforeCallback.latest().word);
    assertEquals("A capped readback that contains a separator still proves the final cursor word is complete.",
        CurrentlyTypedWord.WordCompleteness.COMPLETE,
        beforeCallback.latest().completeness);

    RecordingCallback surroundingCallback = new RecordingCallback();
    CurrentlyTypedWord surroundingWord =
      new CurrentlyTypedWord(null, surroundingCallback);
    surroundingWord.set_current_word(
        new SurroundingText(cappedBefore, cappedBefore.length(),
          cappedBefore.length(), 5));
    assertEquals("fixinf", surroundingCallback.latest().word);
    assertEquals("A nonzero surrounding-text offset only makes the cursor word incomplete when the cap cuts that same word.",
        CurrentlyTypedWord.WordCompleteness.COMPLETE,
        surroundingCallback.latest().completeness);

    String cappedAfter = "fixinf abcdefghijklmnopqrs";
    surroundingWord.set_current_word(
        new SurroundingText(cappedAfter, 6, 6, 0));
    assertEquals("fixinf", surroundingCallback.latest().word);
    assertEquals("A capped right context with an immediate separator proves the cursor is at the complete word boundary.",
        CurrentlyTypedWord.WordCompleteness.COMPLETE,
        surroundingCallback.latest().completeness);
  }

  @Test
  public void ordinary_short_editor_words_are_complete()
  {
    RecordingCallback beforeCallback = new RecordingCallback();
    CurrentlyTypedWord beforeWord =
        new CurrentlyTypedWord(null, beforeCallback);

    beforeWord.set_current_word("alpha beta");

    assertEquals("A short final token fits inside editor context and remains the current word.",
        "beta", beforeCallback.latest().word);
    assertEquals("Uncapped text-before-cursor readback proves an ordinary short word is complete.",
        CurrentlyTypedWord.WordCompleteness.COMPLETE,
        beforeCallback.latest().completeness);
    assertTrue("Complete short readback must remain eligible for existing consumers.",
        beforeCallback.latest().completeness.isComplete());

    RecordingCallback surroundingCallback = new RecordingCallback();
    CurrentlyTypedWord surroundingWord =
        new CurrentlyTypedWord(null, surroundingCallback);
    surroundingWord.set_current_word(
        new SurroundingText("alpha beta", 7, 7, 0));

    assertEquals("Short surrounding text still reconstructs the whole word around the cursor.",
        "beta", surroundingCallback.latest().word);
    assertEquals("Surrounding text beginning at the document boundary is complete even when the word crosses the cursor.",
        CurrentlyTypedWord.WordCompleteness.COMPLETE,
        surroundingCallback.latest().completeness);
  }

  @Test
  public void unavailable_editor_readback_remains_distinguishable()
  {
    RecordingCallback callback = new RecordingCallback();
    CurrentlyTypedWord typedWord = new CurrentlyTypedWord(null, callback);

    typedWord.set_current_word((CharSequence)null);

    CurrentlyTypedWord.Snapshot snapshot = callback.latest();
    assertEquals("Unavailable editor readback keeps the historical empty-word fallback.",
        "", snapshot.word);
    assertEquals("A null editor response must remain distinguishable from complete and incomplete readback.",
        CurrentlyTypedWord.WordCompleteness.UNAVAILABLE,
        snapshot.completeness);
    assertFalse("Unavailable readback cannot prove that an editor word is complete.",
        snapshot.completeness.isComplete());
  }

  @Test
  public void locally_typed_words_retain_complete_word_and_touch_semantics()
  {
    RecordingCallback callback = new RecordingCallback();
    CurrentlyTypedWord typedWord = enabledTypedWord(callback);
    TouchTrace.Entry touch = TouchTrace.entry(1, 1, 1, 1, 10, 10);

    typedWord.typed("a", touch);
    typedWord.typed("b");

    CurrentlyTypedWord.Snapshot snapshot = callback.latest();
    assertEquals("Direct typing must continue to accumulate the same local word.",
        "ab", snapshot.word);
    assertEquals("A locally tracked word must stay distinguishable from editor readback.",
        CurrentlyTypedWord.WordCompleteness.LOCAL,
        snapshot.completeness);
    assertTrue("A local word beginning at a known typing boundary retains complete-word semantics.",
        snapshot.completeness.isComplete());
    assertEquals("Local typing must preserve one touch slot per code point.",
        2, snapshot.touches.size());
    assertSame("The touched key remains aligned with the first locally typed code point.",
        touch, snapshot.touches.get(0));
    assertNull("Untouched local input retains a null geometry slot rather than shifting alignment.",
        snapshot.touches.get(1));
  }

  @Test
  public void identical_editor_refresh_preserves_local_touch_evidence()
  {
    RecordingCallback callback = new RecordingCallback();
    CurrentlyTypedWord typedWord = enabledTypedWord(callback);
    TouchTrace.Entry first = TouchTrace.entry(1, 1, 1, 1, 10, 10);
    TouchTrace.Entry second = TouchTrace.entry(2, 1, 2, 1, 10, 10);
    typedWord.typed("a", first);
    typedWord.typed("b", second);

    typedWord.set_current_word("prefix ab");

    CurrentlyTypedWord.Snapshot snapshot = callback.latest();
    assertEquals("An identical readback must keep the same cursor word.",
        "ab", snapshot.word);
    assertSame("A duplicate editor callback must not erase the first key's geometry.",
        first, snapshot.touches.get(0));
    assertSame("A duplicate editor callback must not erase the second key's geometry.",
        second, snapshot.touches.get(1));
  }

  @Test
  public void surrounding_text_refresh_returns_word_around_cursor_without_stale_touch_trace()
  {
    RecordingCallback callback = new RecordingCallback();
    CurrentlyTypedWord typedWord = enabledTypedWord(callback);
    typedWord.typed("o", TouchTrace.entry(1, 1, 1, 1, 10, 10));
    typedWord.typed("l", TouchTrace.entry(2, 1, 2, 1, 10, 10));
    typedWord.typed("d", TouchTrace.entry(3, 1, 3, 1, 10, 10));
    assertEquals("The setup must create a non-empty trace so the refresh can prove it clears stale touches.",
        3, typedWord.touch_trace().size());

    typedWord.set_current_word(new SurroundingText("world next", 0, 0, 0));

    assertEquals("SurroundingText refresh must include the word after the cursor when the cursor is at the word start.",
        "world", typedWord.get());
    assertEquals("The cursor is five characters before the end of the refreshed word.",
        -5, typedWord.cursor_relative());
    TouchTrace.Snapshot refreshedTouches = typedWord.touch_trace();
    assertEquals("Editor refresh must preserve one touch slot per code point so asynchronous spatial decoding keeps word/touch indexes aligned.",
        typedWord.get().codePointCount(0, typedWord.get().length()),
        refreshedTouches.size());
    for (int i = 0; i < refreshedTouches.size(); i++)
      assertNull("Editor-derived words have no per-key geometry; every aligned touch slot must be null rather than stale data from the old word.",
          refreshedTouches.get(i));
    assertEquals(Arrays.asList("o", "ol", "old", "world"), callback.words);
    assertEquals(Arrays.asList(1, 2, 3, 5), callback.traceSizes);
  }

  private static String repeated(char c, int length)
  {
    StringBuilder value = new StringBuilder(length);
    for (int i = 0; i < length; ++i)
      value.append(c);
    return value.toString();
  }

  private static CurrentlyTypedWord enabledTypedWord(RecordingCallback callback)
  {
    CurrentlyTypedWord typedWord = new CurrentlyTypedWord(null, callback);
    typedWord._enabled = true;
    return typedWord;
  }

  private static final class RecordingCallback
      implements CurrentlyTypedWord.Callback
  {
    final ArrayList<String> words = new ArrayList<String>();
    final ArrayList<Integer> traceSizes = new ArrayList<Integer>();
    final ArrayList<CurrentlyTypedWord.Snapshot> snapshots =
        new ArrayList<CurrentlyTypedWord.Snapshot>();

    @Override
    public void currently_typed_word(CurrentlyTypedWord.Snapshot snapshot)
    {
      words.add(snapshot.word);
      traceSizes.add(snapshot.touches.size());
      snapshots.add(snapshot);
    }

    CurrentlyTypedWord.Snapshot latest()
    {
      return snapshots.get(snapshots.size() - 1);
    }
  }
}
