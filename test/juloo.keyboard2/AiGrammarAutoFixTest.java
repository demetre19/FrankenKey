package juloo.keyboard2;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import android.os.Handler;
import android.os.Looper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import static org.junit.Assert.*;

/**
 * Locks the auto-grammar contract: a fix never lands over text typed
 * after the request fired, and the one-at-a-time busy latch always
 * resets — on executor rejection, job failure, and success alike.
 */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
public class AiGrammarAutoFixTest
{
  private static final String SENTENCE = "their going to the park.";

  @Test
  public void sentence_tail_match_rejects_typing_after_request()
  {
    assertEquals(0, AiGrammarFixer.matchSentenceTail(SENTENCE + " ",
        SENTENCE));
    assertEquals(5, AiGrammarFixer.matchSentenceTail(
        "Hey, " + SENTENCE + "  ", SENTENCE));
    assertEquals(-1, AiGrammarFixer.matchSentenceTail(
        SENTENCE + " more words", SENTENCE));
    assertEquals(-1, AiGrammarFixer.matchSentenceTail(
        SENTENCE + "more", SENTENCE));
    assertEquals(-1, AiGrammarFixer.matchSentenceTail(
        "completely different text.", SENTENCE));
    assertEquals(-1, AiGrammarFixer.matchSentenceTail(null, SENTENCE));
    assertEquals(-1, AiGrammarFixer.matchSentenceTail(SENTENCE, null));
  }

  @Test
  public void runner_busy_resets_when_job_fails()
  {
    Handler handler = new Handler(Looper.getMainLooper());
    AiGrammarFixer.Runner runner = new AiGrammarFixer.Runner(
        Runnable::run, handler);
    AtomicBoolean delivered = new AtomicBoolean(false);
    runner.submit(() -> { throw new java.io.IOException("offline"); },
        outcome -> delivered.set(true));
    assertFalse(runner.busy);
    assertFalse(delivered.get());
  }

  @Test
  public void runner_busy_resets_when_executor_rejects()
  {
    Handler handler = new Handler(Looper.getMainLooper());
    AiGrammarFixer.Runner runner = new AiGrammarFixer.Runner(
        task -> { throw new RejectedExecutionException("shutdown"); },
        handler);
    runner.submit(() -> new AiGrammarFixer.Outcome("x", 0),
        outcome -> {});
    assertFalse(runner.busy);
  }

  @Test
  public void runner_delivers_fix_and_resets()
  {
    Handler handler = new Handler(Looper.getMainLooper());
    AiGrammarFixer.Runner runner = new AiGrammarFixer.Runner(
        Runnable::run, handler);
    AtomicReference<AiGrammarFixer.Outcome> seen = new AtomicReference<>();
    runner.submit(() -> new AiGrammarFixer.Outcome("fixed.", 0),
        seen::set);
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    assertFalse(runner.busy);
    assertEquals("fixed.", seen.get().corrected);
  }

  @Test
  public void page_actions_prefer_capture_over_clipboard()
  {
    assertTrue(ReaderAiQuickActivity.prefersPage(ReaderAiAction.OPEN_CHAT));
    assertTrue(ReaderAiQuickActivity.prefersPage(ReaderAiAction.SUMMARY_ONE));
    assertTrue(ReaderAiQuickActivity.prefersPage(ReaderAiAction.SUMMARY_TWO));
    assertTrue(ReaderAiQuickActivity.prefersPage(ReaderAiAction.QUIZ));
    assertFalse(ReaderAiQuickActivity.prefersPage(
        ReaderAiAction.LOAD_CLIPBOARD));
    assertFalse(ReaderAiQuickActivity.prefersPage(
        ReaderAiAction.READ_CLIPBOARD));
    assertFalse(ReaderAiQuickActivity.prefersPage(ReaderAiAction.SHARE));
    assertFalse(ReaderAiQuickActivity.prefersPage(ReaderAiAction.SAVED));
  }
}
