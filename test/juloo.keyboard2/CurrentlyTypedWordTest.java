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
    assertEquals("Editor refresh has no per-key touch geometry and must not retain trace entries from the old typed word.",
        0, typedWord.touch_trace().size());
    assertEquals(Arrays.asList("o", "ol", "old", "world"), callback.words);
    assertEquals(Arrays.asList(1, 2, 3, 0), callback.traceSizes);
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

    @Override
    public void currently_typed_word(String word, TouchTrace touchTrace)
    {
      words.add(word);
      traceSizes.add(touchTrace.size());
    }
  }
}
