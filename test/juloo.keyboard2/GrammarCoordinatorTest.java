package juloo.keyboard2;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import juloo.keyboard2.grammar.GrammarCoordinator;
import juloo.keyboard2.grammar.GrammarData;
import juloo.keyboard2.grammar.GrammarIssue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
public class GrammarCoordinatorTest
{
  private GrammarData data;
  private Recorder recorder;
  private GrammarCoordinator coordinator;

  static final class Recorder implements GrammarCoordinator.Presenter
  {
    List<GrammarIssue> presented = new ArrayList<>();
    List<GrammarIssue> applied = new ArrayList<>();
    List<GrammarIssue> undone = new ArrayList<>();
    List<String> disabled = new ArrayList<>();

    public void present(GrammarIssue issue) { presented.add(issue); }
    public void apply(GrammarIssue issue) { applied.add(issue); }
    public void undo(GrammarIssue issue) { undone.add(issue); }
    public void disableRule(String ruleId) { disabled.add(ruleId); }
  }

  @Before
  public void setUp() throws IOException
  {
    try (FileInputStream input = new FileInputStream("assets/grammar/en.json"))
    {
      data = GrammarData.load(input);
    }
    recorder = new Recorder();
    coordinator = new GrammarCoordinator(data, recorder);
    coordinator.setEnabled(true);
  }

  @Test
  public void your_welcome_boundary_presents_prompt()
  {
    coordinator.onBoundary("thank you, your welcome ");
    GrammarIssue showing = coordinator.showing();
    assertNotNull("your welcome<space> must prompt", showing);
    assertEquals("your", showing.matched);
    assertEquals("you're", showing.replacement);
    assertEquals("\"your\" → \"you're\"", showing.message());
  }

  @Test
  public void no_prompt_without_boundary_call()
  {
    GrammarIssue showing = coordinator.showing();
    assertNull(showing);
  }

  @Test
  public void fix_applies_and_offers_undo()
  {
    coordinator.onBoundary("your welcome ");
    coordinator.fixShowing();
    assertEquals(1, recorder.applied.size());
    assertNotNull(coordinator.undoCandidate());
    coordinator.undoShowing();
    assertEquals(1, recorder.undone.size());
    assertNull(coordinator.undoCandidate());
  }

  @Test
  public void ignore_suppresses_same_match_for_session()
  {
    coordinator.onBoundary("your welcome ");
    coordinator.ignoreShowing();
    coordinator.onBoundary("later your welcome ");
    assertNull("Ignored match must not reappear this session",
        coordinator.showing());
  }

  @Test
  public void new_session_clears_ignores()
  {
    coordinator.onBoundary("your welcome ");
    coordinator.ignoreShowing();
    coordinator.resetSession();
    coordinator.onBoundary("your welcome ");
    assertNotNull(coordinator.showing());
  }

  @Test
  public void disable_rule_stops_future_issues_of_that_rule()
  {
    coordinator.onBoundary("your welcome ");
    coordinator.disableShowingRule();
    assertTrue(recorder.disabled.contains(
        juloo.keyboard2.grammar.GrammarRules.RULE_YOUR_YOURE));
    coordinator.onBoundary("again your welcome ");
    assertNull(coordinator.showing());
  }

  @Test
  public void queue_is_bounded_to_three()
  {
    coordinator.onBoundary("the the your welcome alot of work ");
    assertTrue("queue must stay <=3",
        coordinator.queueSize() <= GrammarCoordinator.MAX_QUEUED_ISSUES);
  }

  @Test
  public void offline_issues_not_duplicated_by_system_correction()
  {
    /* System checker already reported the same span+replacement. */
    coordinator.onSystemCorrection(0, 4, "you're");
    coordinator.onBoundary("your welcome ");
    assertNull("deduped issue must not prompt", coordinator.showing());
  }

  @Test
  public void spans_fire_only_once()
  {
    coordinator.onBoundary("your welcome ");
    coordinator.ignoreShowing();
    /* Same span re-checked: fired-spans + ignore both suppress. */
    coordinator.onBoundary("your welcome ");
    assertNull(coordinator.showing());
  }

  @Test
  public void sliding_window_only_checks_last_words()
  {
    String window = GrammarCoordinator.windowOf(
        "one two three four five six seven eight nine your ");
    /* Window must cover at most the 8 last words. */
    assertFalse(window.startsWith("one"));
  }
}
