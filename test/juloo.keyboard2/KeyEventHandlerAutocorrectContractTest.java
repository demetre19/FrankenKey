package juloo.keyboard2;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;
import static org.junit.Assert.*;

public class KeyEventHandlerAutocorrectContractTest
{
  public KeyEventHandlerAutocorrectContractTest() {}

  @Test
  public void commit_boundary_autocorrect_uses_active_speller_and_dictionary_not_visible_suggestions()
      throws Exception
  {
    String source = readSource("srcs/juloo.keyboard2/KeyEventHandler.java");
    String separatorBody = methodBody(source, "void handle_word_separator");
    String spaceBody = methodBody(source, "void handle_space_bar");

    assertFalse("Commit-boundary autocorrect must not be gated on visible candidate-strip suggestions; the Suggestions toggle is separate from Autocorrect.",
        separatorBody.contains("_suggestions.count"));
    assertFalse("Commit-boundary autocorrect must not directly accept the first visible suggestion; boundary autocorrect comes from the active Hunspell/Cdict scoring path.",
        separatorBody.contains("suggestion_entered(_suggestions.suggestions[0])"));
    assertFalse("Commit-boundary autocorrect must not read visible suggestion entries as its correction source.",
        separatorBody.contains("_suggestions.suggestions"));

    int delegate = spaceBody.indexOf("handle_word_separator(\" \")");
    assertTrue("Spacebar must delegate to the shared word-separator path so punctuation and Enter share the same autocorrect behavior.",
        delegate >= 0);

    int autocorrectGate = separatorBody.indexOf("should_try_autocorrect()");
    int correctionCall = separatorBody.indexOf("Autocorrect.correction", autocorrectGate);
    int hunspellArg = separatorBody.indexOf("_config.current_hunspell", correctionCall);
    int dictionaryArg = separatorBody.indexOf("_config.current_dictionary", hunspellArg);
    int typedWordArg = separatorBody.indexOf("_typedword.get()", dictionaryArg);
    int touchTraceArg = separatorBody.indexOf("_typedword.touch_trace()", typedWordArg);
    int layoutArg = separatorBody.indexOf("_config.current_layout_geometry", touchTraceArg);
    int correctionNullCheck = separatorBody.indexOf("correction != null", layoutArg);
    int commitCorrection = separatorBody.indexOf("commit_correction(correction, separator)", correctionNullCheck);
    int returnAfterCorrection = separatorBody.indexOf("return", commitCorrection);
    int commitTypedWord = separatorBody.indexOf("should_commit_typed_word()", returnAfterCorrection);
    int plainSeparatorFallback = separatorBody.indexOf("send_text(separator)", commitTypedWord);

    assertOrdered("Commit boundaries must attempt touch-aware Autocorrect.correction with the active Hunspell pack, active Cdict dictionary, current typed word, touch trace, and active layout geometry before falling back to inserting the literal separator.",
        autocorrectGate, correctionCall, hunspellArg, dictionaryArg,
        typedWordArg, touchTraceArg, layoutArg, correctionNullCheck,
        commitCorrection, returnAfterCorrection, commitTypedWord,
        plainSeparatorFallback);
  }

  @Test
  public void commit_boundary_autocorrect_requires_safe_typing_assistance_context()
      throws Exception
  {
    String source = readSource("srcs/juloo.keyboard2/KeyEventHandler.java");
    String gateBody = methodBody(source, "boolean should_try_autocorrect");
    String separatorBody = methodBody(source, "void handle_word_separator");

    int enabledGate = gateBody.indexOf("_autocorrect_enabled");
    int nonNullConfigGuard = gateBody.indexOf("_config != null", enabledGate);
    int safeContextGuard = gateBody.indexOf(
        "_config.editor_config.should_use_typing_assistance", nonNullConfigGuard);
    int selectionGuard = gateBody.indexOf("!_typedword.is_selection_not_empty()",
        safeContextGuard);
    int cursorGuard = gateBody.indexOf("_typedword.cursor_relative() == 0",
        selectionGuard);
    int separatorGate = separatorBody.indexOf("should_try_autocorrect()");
    int correctionCall = separatorBody.indexOf("Autocorrect.correction",
        separatorGate);

    assertOrdered("Autocorrect must require enabled settings, a non-null config, the safe typing-assistance editor predicate, no selection, and cursor-at-end before Hunspell/Cdict correction can run.",
        enabledGate, nonNullConfigGuard, safeContextGuard, selectionGuard,
        cursorGuard);
    assertOrdered("The shared separator handler must call the active Hunspell/Cdict correction path only after the safe autocorrect gate.",
        separatorGate, correctionCall);
    assertEquals("The separator handler must have exactly one correction path; extra calls risk bypassing the safe typing-assistance privacy gate.",
        1, countOccurrences(separatorBody, "Autocorrect.correction"));
    assertEquals("The separator handler must commit exactly one correction result; extra commits risk bypassing the safe typing-assistance privacy gate.",
        1, countOccurrences(separatorBody, "commit_correction(correction, separator)"));
  }

  @Test
  public void correction_commit_records_separator_for_immediate_backspace_undo()
      throws Exception
  {
    String source = readSource("srcs/juloo.keyboard2/KeyEventHandler.java");
    String suggestionBody = methodBody(source, "public void suggestion_entered");
    String commitBody = methodBody(source, "void commit_correction");

    assertTrue("Candidate-strip acceptance must keep the historical trailing-space behavior while using the shared correction commit path.",
        suggestionBody.contains("commit_correction(text, \" \")"));

    int currentWordSnapshot = commitBody.indexOf("String old = _typedword.get()");
    int replacementCommit = commitBody.indexOf("replace_surrounding_text", currentWordSnapshot);
    int committedTextWithSeparator = commitBody.indexOf("text + separator",
        replacementCommit);
    int rememberReplacedWord = commitBody.indexOf("last_replaced_word = old",
        committedTextWithSeparator);
    int rememberReplacementLength = commitBody.indexOf(
        "last_replacement_word_len = text.length() + separator.length()",
        rememberReplacedWord);
    int rememberSeparator = commitBody.indexOf(
        "last_replacement_separator = separator", rememberReplacementLength);
    int rememberSuggestionAction = commitBody.indexOf(
        "_next_last_action = LastAction.SUGGESTION_ENTERED", rememberSeparator);

    assertOrdered("Accepting a correction must snapshot the replaced word, commit the corrected text with the actual separator, remember the correction-plus-separator length, remember that separator, and mark the next action so immediate Backspace can undo the autocorrect.",
        currentWordSnapshot, replacementCommit, committedTextWithSeparator,
        rememberReplacedWord, rememberReplacementLength, rememberSeparator,
        rememberSuggestionAction);
    assertEquals("Correction commits must have exactly one undo word snapshot.",
        1, countOccurrences(commitBody, "last_replaced_word ="));
    assertEquals("Correction commits must have exactly one undo replacement-length snapshot.",
        1, countOccurrences(commitBody, "last_replacement_word_len ="));
    assertEquals("Correction commits must have exactly one separator snapshot.",
        1, countOccurrences(commitBody, "last_replacement_separator ="));
    assertEquals("Correction commits must have exactly one immediate-undo action marker.",
        1, countOccurrences(commitBody, "_next_last_action = LastAction.SUGGESTION_ENTERED"));
  }

  @Test
  public void backspace_after_correction_restores_original_word_plus_separator()
      throws Exception
  {
    String body = methodBody(readSource("srcs/juloo.keyboard2/KeyEventHandler.java"),
        "void handle_backspace");

    int actionGuard = body.indexOf("_last_action == LastAction.SUGGESTION_ENTERED");
    int replacedWordGuard = body.indexOf("last_replaced_word != null",
        actionGuard);
    int undoReplace = body.indexOf("replace_surrounding_text",
        replacedWordGuard);
    int removeCorrectionLength = body.indexOf("last_replacement_word_len",
        undoReplace);
    int restoreOriginalWithSeparator = body.indexOf(
        "last_replaced_word + last_replacement_separator", removeCorrectionLength);
    int clearUndoWord = body.indexOf("last_replaced_word = null",
        restoreOriginalWithSeparator);
    int normalBackspaceFallback = body.indexOf("send_backspace()",
        clearUndoWord);

    assertOrdered("Immediate Backspace after a correction must take the autocorrect undo branch, replace the correction span with the original word plus the exact committed separator, clear the undo snapshot, and only then expose the normal delete fallback.",
        actionGuard, replacedWordGuard, undoReplace, removeCorrectionLength,
        restoreOriginalWithSeparator, clearUndoWord, normalBackspaceFallback);
    assertEquals("The autocorrect undo branch must restore exactly last_replaced_word plus the separator that was committed with the correction.",
        1, countOccurrences(body, "last_replaced_word + last_replacement_separator"));
  }

  @Test
  public void punctuation_and_enter_are_autocorrect_commit_boundaries()
      throws Exception
  {
    String source = readSource("srcs/juloo.keyboard2/KeyEventHandler.java");
    String keyUpBody = methodBody(source, "public void key_up");
    String separatorBody = methodBody(source, "boolean is_autocorrect_separator");

    int charCase = keyUpBody.indexOf("case Char:");
    int charValue = keyUpBody.indexOf("char c = key.getChar()", charCase);
    int separatorGuard = keyUpBody.indexOf("is_autocorrect_separator(c)",
        charValue);
    int charSeparatorCommit = keyUpBody.indexOf(
        "handle_word_separator(String.valueOf(c))", separatorGuard);
    int literalCharFallback = keyUpBody.indexOf("send_text(String.valueOf(c), touch)",
        charSeparatorCommit);
    int keyeventCase = keyUpBody.indexOf("case Keyevent:", literalCharFallback);
    int enterGuard = keyUpBody.indexOf("key.getKeyevent() == KeyEvent.KEYCODE_ENTER",
        keyeventCase);
    int enterSeparatorCommit = keyUpBody.indexOf("handle_word_separator(\"\\n\")",
        enterGuard);
    int keyeventFallback = keyUpBody.indexOf("send_key_down_up(key.getKeyevent())",
        enterSeparatorCommit);

    assertOrdered("Released punctuation characters and Enter must route through the shared word-separator autocorrect path before falling back to literal text/key events.",
        charCase, charValue, separatorGuard, charSeparatorCommit,
        literalCharFallback, keyeventCase, enterGuard, enterSeparatorCommit,
        keyeventFallback);
    assertTrue("Period must be an autocorrect separator.",
        separatorBody.contains("case '.':"));
    assertTrue("Comma must be an autocorrect separator.",
        separatorBody.contains("case ',':"));
    assertTrue("Exclamation mark must be an autocorrect separator.",
        separatorBody.contains("case '!':"));
    assertTrue("Question mark must be an autocorrect separator.",
        separatorBody.contains("case '?':"));
    assertTrue("Semicolon must be an autocorrect separator.",
        separatorBody.contains("case ';':"));
    assertTrue("Colon must be an autocorrect separator.",
        separatorBody.contains("case ':':"));
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
