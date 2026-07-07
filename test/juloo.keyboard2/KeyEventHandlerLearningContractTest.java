package juloo.keyboard2;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;
import static org.junit.Assert.*;

public class KeyEventHandlerLearningContractTest
{
  public KeyEventHandlerLearningContractTest() {}

  @Test
  public void committed_word_learning_resets_context_when_suggestions_or_safe_editor_gate_is_disabled()
      throws Exception
  {
    String body = methodBody(readSource("srcs/juloo.keyboard2/KeyEventHandler.java"),
        "void learn_committed_word");

    int nullConfigGuard = body.indexOf("_config == null");
    int suggestionsDisabledGuard = body.indexOf("!_config.suggestions_enabled",
        nullConfigGuard);
    int unsafeEditorGuard = body.indexOf(
        "!_config.editor_config.should_use_typing_assistance",
        suggestionsDisabledGuard);
    int resetContext = body.indexOf("_config.personalization.reset_context()",
        unsafeEditorGuard);
    int earlyReturn = body.indexOf("return", resetContext);
    int recordWord = body.indexOf("_config.personalization.record_word(word)",
        earlyReturn);

    assertOrdered("Committed words must be learned only after KeyEventHandler proves Suggestions are enabled and the editor is safe for typing assistance; disabled or unsafe contexts must reset personalization context before returning.",
        nullConfigGuard, suggestionsDisabledGuard, unsafeEditorGuard,
        resetContext, earlyReturn, recordWord);
    assertEquals("Learning must have one record_word call inside the guarded method body; extra calls risk bypassing disabled Suggestions or unsafe editor contexts.",
        1, countOccurrences(body, "record_word("));
    assertEquals("Disabled Suggestions or unsafe editor contexts must reset the bigram context exactly once before returning.",
        1, countOccurrences(body, "reset_context("));
  }

  @Test
  public void candidate_row_learning_action_toggles_personalization_without_committing_text()
      throws Exception
  {
    String source = readSource("srcs/juloo.keyboard2/KeyEventHandler.java");
    String body = methodBody(source, "public void suggestion_swiped_up");
    String suggestionEntered = methodBody(source, "public void suggestion_entered");

    int nullConfigGuard = body.indexOf("_config == null");
    int suggestionsDisabledGuard = body.indexOf("!_config.suggestions_enabled",
        nullConfigGuard);
    int unsafeEditorGuard = body.indexOf(
        "!_config.editor_config.should_use_typing_assistance",
        suggestionsDisabledGuard);
    int learnedCheck = body.indexOf("_config.personalization.is_learned(text)",
        unsafeEditorGuard);
    int unlearn = body.indexOf("_config.personalization.unlearn_word(text)",
        learnedCheck);
    int record = body.indexOf("_config.personalization.record_word(text)",
        unlearn);
    int refresh = body.indexOf("_suggestions.currently_typed_word(_typedword.get(), _typedword.touch_trace())",
        record);

    assertOrdered("Candidate-row learn actions must pass through suggestion_swiped_up, respect Suggestions and safe-editor gates, toggle an already-learned word off, learn an unknown word on, and refresh the candidate row around the current typed word.",
        nullConfigGuard, suggestionsDisabledGuard, unsafeEditorGuard,
        learnedCheck, unlearn, record, refresh);
    assertFalse("The learn/unlearn action must not commit the action label or typed word; committing remains the job of suggestion_entered.",
        body.contains("commit_correction("));
    assertFalse("The learn/unlearn action must not send visible text directly.",
        body.contains("send_text("));
    assertTrue("Normal suggestion acceptance must remain the separate path that commits text plus its trailing space.",
        suggestionEntered.contains("commit_correction(text, \" \")"));
  }

  private static String readSource(String path)
      throws Exception
  {
    return new String(Files.readAllBytes(Paths.get(path)),
        StandardCharsets.UTF_8);
  }

  private static String methodBody(String source, String methodSignature)
  {
    int start = source.indexOf(methodSignature);
    assertTrue("Missing method: " + methodSignature, start >= 0);
    int open = source.indexOf('{', start);
    assertTrue(open >= 0);
    int depth = 0;
    for (int i = open; i < source.length(); ++i)
    {
      char c = source.charAt(i);
      if (c == '{')
        ++depth;
      else if (c == '}')
      {
        --depth;
        if (depth == 0)
          return source.substring(open + 1, i);
      }
    }
    fail("Unclosed method: " + methodSignature);
    return "";
  }

  private static void assertOrdered(String message, int... indexes)
  {
    int previous = -1;
    for (int index : indexes)
    {
      assertTrue(message + " missing index", index >= 0);
      assertTrue(message + " out of order", index > previous);
      previous = index;
    }
  }

  private static int countOccurrences(String text, String needle)
  {
    int count = 0;
    int index = 0;
    while (true)
    {
      index = text.indexOf(needle, index);
      if (index < 0)
        return count;
      ++count;
      index += needle.length();
    }
  }
}
