package juloo.keyboard2;

import android.content.Context;
import android.text.Editable;
import android.text.Selection;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.textservice.SentenceSuggestionsInfo;
import android.view.textservice.SuggestionsInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
public class SystemGrammarCheckerTest
{
  @Test
  public void sentence_result_preserves_exact_request_identity_and_range()
  {
    SystemGrammarChecker.Request request =
      new SystemGrammarChecker.Request("I has apples", 22, 17);
    SuggestionsInfo info = new SuggestionsInfo(
        SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_GRAMMAR_ERROR,
        new String[]{ "have" }, 0, 17);
    SentenceSuggestionsInfo sentence = new SentenceSuggestionsInfo(
        new SuggestionsInfo[]{ info }, new int[]{ 2 }, new int[]{ 3 });

    SystemGrammarChecker.Correction correction =
      SystemGrammarChecker.correction_from(request,
          new SentenceSuggestionsInfo[]{ sentence });

    assertNotNull("A current grammar result must become an actionable correction.",
        correction);
    assertEquals("have", correction.replacement);
    assertEquals(2, correction.offset);
    assertEquals(3, correction.length);

    SuggestionsInfo stale = new SuggestionsInfo(
        SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_GRAMMAR_ERROR,
        new String[]{ "have" }, 0, 16);
    assertNull("A result from an older text snapshot must never replace current editor text.",
        SystemGrammarChecker.correction_from(request,
          new SentenceSuggestionsInfo[]{ new SentenceSuggestionsInfo(
            new SuggestionsInfo[]{ stale }, new int[]{ 2 }, new int[]{ 3 }) }));
  }

  @Test
  public void applying_correction_revalidates_cursor_and_exact_suffix()
  {
    BaseInputConnection connection = connection("Earlier. I has apples");
    SystemGrammarChecker.Correction correction =
      new SystemGrammarChecker.Correction("I has apples", 21, 2, 3,
          "have", 1);

    assertTrue("A correction may apply only while its original sentence and cursor still match.",
        correction.apply(connection, 21));
    assertEquals("Earlier. I have apples",
        connection.getEditable().toString());

    BaseInputConnection moved = connection("Earlier. I has apples");
    assertFalse("Moving the cursor must make an asynchronous grammar action stale.",
        correction.apply(moved, 20));
    assertEquals("Earlier. I has apples", moved.getEditable().toString());
  }

  @Test
  public void grammar_requests_are_bounded_to_the_current_sentence()
  {
    assertEquals("I has apples",
        SystemGrammarChecker.relevant_text("Earlier text.  I has apples"));
    assertEquals("unfinished thought",
        SystemGrammarChecker.relevant_text("First line\n unfinished thought"));
  }

  private static BaseInputConnection connection(String text)
  {
    Context context = RuntimeEnvironment.getApplication();
    BaseInputConnection connection = new BaseInputConnection(new View(context), true);
    Editable editable = connection.getEditable();
    editable.replace(0, editable.length(), text);
    Selection.setSelection(editable, editable.length());
    return connection;
  }
}
