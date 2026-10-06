package juloo.keyboard2;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
public class ReaderAiButtonTest
{
  private SharedPreferences _prefs;

  @Before
  public void setUp()
  {
    _prefs = RuntimeEnvironment.getApplication().getSharedPreferences(
        "reader_ai_button_test", Context.MODE_PRIVATE);
    _prefs.edit().clear().commit();
  }

  @Test
  public void sectorFor_maps_compass_directions()
  {
    assertEquals(ReaderAiAction.Sector.RIGHT,
        ReaderAiAction.sectorFor(100f, 0f));
    assertEquals(ReaderAiAction.Sector.UP_RIGHT,
        ReaderAiAction.sectorFor(100f, -100f));
    assertEquals(ReaderAiAction.Sector.UP,
        ReaderAiAction.sectorFor(0f, -100f));
    assertEquals(ReaderAiAction.Sector.UP_LEFT,
        ReaderAiAction.sectorFor(-100f, -100f));
    assertEquals(ReaderAiAction.Sector.LEFT,
        ReaderAiAction.sectorFor(-100f, 0f));
    assertEquals(ReaderAiAction.Sector.DOWN_LEFT,
        ReaderAiAction.sectorFor(-100f, 100f));
    assertEquals(ReaderAiAction.Sector.DOWN,
        ReaderAiAction.sectorFor(0f, 100f));
    assertEquals(ReaderAiAction.Sector.DOWN_RIGHT,
        ReaderAiAction.sectorFor(100f, 100f));
  }

  @Test
  public void sectorFor_holds_sector_boundaries()
  {
    // 22 degrees above right must stay RIGHT; 23 degrees must flip to UP_RIGHT.
    double justInside = Math.toRadians(22.0);
    double justOutside = Math.toRadians(23.0);
    assertEquals(ReaderAiAction.Sector.RIGHT,
        ReaderAiAction.sectorFor((float)Math.cos(justInside) * 100f,
          -(float)Math.sin(justInside) * 100f));
    assertEquals(ReaderAiAction.Sector.UP_RIGHT,
        ReaderAiAction.sectorFor((float)Math.cos(justOutside) * 100f,
          -(float)Math.sin(justOutside) * 100f));
    // Straight down is DOWN, not DOWN_RIGHT/DOWN_LEFT.
    assertEquals(ReaderAiAction.Sector.DOWN,
        ReaderAiAction.sectorFor(1f, 100f));
  }

  @Test
  public void actionFor_returns_sector_defaults_without_preferences()
  {
    assertEquals(ReaderAiAction.OPEN_CHAT,
        ReaderAiAction.actionFor(_prefs, ReaderAiAction.Sector.TAP));
    assertEquals(ReaderAiAction.SUMMARY_ONE,
        ReaderAiAction.actionFor(_prefs, ReaderAiAction.Sector.UP));
    assertEquals(ReaderAiAction.SUMMARY_TWO,
        ReaderAiAction.actionFor(_prefs, ReaderAiAction.Sector.UP_RIGHT));
    assertEquals(ReaderAiAction.QUIZ,
        ReaderAiAction.actionFor(_prefs, ReaderAiAction.Sector.RIGHT));
    assertEquals(ReaderAiAction.SHARE,
        ReaderAiAction.actionFor(_prefs, ReaderAiAction.Sector.DOWN_RIGHT));
    assertEquals(ReaderAiAction.SAVED,
        ReaderAiAction.actionFor(_prefs, ReaderAiAction.Sector.DOWN));
    assertEquals(ReaderAiAction.SPEED_READ,
        ReaderAiAction.actionFor(_prefs, ReaderAiAction.Sector.DOWN_LEFT));
    assertEquals(ReaderAiAction.AI_SETTINGS,
        ReaderAiAction.actionFor(_prefs, ReaderAiAction.Sector.LEFT));
    assertEquals(ReaderAiAction.OPEN_CHAT,
        ReaderAiAction.actionFor(_prefs, ReaderAiAction.Sector.UP_LEFT));
  }

  @Test
  public void actionFor_honors_saved_overrides_and_ignores_unknown_ids()
  {
    ReaderAiAction.setAction(_prefs, ReaderAiAction.Sector.UP,
        ReaderAiAction.QUIZ);
    assertEquals(ReaderAiAction.QUIZ,
        ReaderAiAction.actionFor(_prefs, ReaderAiAction.Sector.UP));
    _prefs.edit().putString(ReaderAiAction.Sector.RIGHT.prefKey(),
        "not_a_real_action").commit();
    assertEquals("Unknown persisted ids must fall back to the sector default.",
        ReaderAiAction.QUIZ,
        ReaderAiAction.actionFor(_prefs, ReaderAiAction.Sector.RIGHT));
  }

  @Test
  public void controller_tap_opens_chat()
  {
    List<ReaderAiAction> dispatched = new ArrayList<>();
    ReaderAiButtonController controller = new ReaderAiButtonController(
        _prefs, 40f, dispatched::add);
    View view = new View(RuntimeEnvironment.getApplication());
    long now = 1000;
    controller.onTouch(view, MotionEvent.obtain(now, now,
          MotionEvent.ACTION_DOWN, 200f, 300f, 0));
    controller.onTouch(view, MotionEvent.obtain(now, now + 10,
          MotionEvent.ACTION_UP, 205f, 302f, 0));
    assertEquals(1, dispatched.size());
    assertEquals(ReaderAiAction.OPEN_CHAT, dispatched.get(0));
  }

  @Test
  public void controller_tap_uses_saved_tap_override()
  {
    ReaderAiAction.setAction(_prefs, ReaderAiAction.Sector.TAP,
        ReaderAiAction.SAVED);
    List<ReaderAiAction> dispatched = new ArrayList<>();
    ReaderAiButtonController controller = new ReaderAiButtonController(
        _prefs, 40f, dispatched::add);
    View view = new View(RuntimeEnvironment.getApplication());
    long now = 1000;
    controller.onTouch(view, MotionEvent.obtain(now, now,
          MotionEvent.ACTION_DOWN, 200f, 300f, 0));
    controller.onTouch(view, MotionEvent.obtain(now, now + 10,
          MotionEvent.ACTION_UP, 205f, 302f, 0));
    assertEquals(1, dispatched.size());
    assertEquals(ReaderAiAction.SAVED, dispatched.get(0));
  }

  @Test
  public void controller_dispatches_none_when_sector_unassigned()
  {
    ReaderAiAction.setAction(_prefs, ReaderAiAction.Sector.UP,
        ReaderAiAction.NONE);
    List<ReaderAiAction> dispatched = new ArrayList<>();
    ReaderAiButtonController controller = new ReaderAiButtonController(
        _prefs, 40f, dispatched::add);
    View view = new View(RuntimeEnvironment.getApplication());
    long now = 1000;
    controller.onTouch(view, MotionEvent.obtain(now, now,
          MotionEvent.ACTION_DOWN, 200f, 300f, 0));
    controller.onTouch(view, MotionEvent.obtain(now, now + 10,
          MotionEvent.ACTION_UP, 200f, 180f, 0));
    assertEquals(1, dispatched.size());
    assertEquals(ReaderAiAction.NONE, dispatched.get(0));
  }


  @Test
  public void controller_swipe_dispatches_sector_action()
  {
    List<ReaderAiAction> dispatched = new ArrayList<>();
    ReaderAiButtonController controller = new ReaderAiButtonController(
        _prefs, 40f, dispatched::add);
    View view = new View(RuntimeEnvironment.getApplication());
    long now = 1000;
    controller.onTouch(view, MotionEvent.obtain(now, now,
          MotionEvent.ACTION_DOWN, 200f, 300f, 0));
    // Straight up beyond the threshold -> UP sector -> SUMMARY_ONE default.
    controller.onTouch(view, MotionEvent.obtain(now, now + 10,
          MotionEvent.ACTION_UP, 200f, 180f, 0));
    assertEquals(1, dispatched.size());
    assertEquals(ReaderAiAction.SUMMARY_ONE, dispatched.get(0));
  }

  @Test
  public void controller_swipe_uses_saved_sector_override()
  {
    ReaderAiAction.setAction(_prefs, ReaderAiAction.Sector.DOWN,
        ReaderAiAction.SPEED_READ);
    List<ReaderAiAction> dispatched = new ArrayList<>();
    ReaderAiButtonController controller = new ReaderAiButtonController(
        _prefs, 40f, dispatched::add);
    View view = new View(RuntimeEnvironment.getApplication());
    long now = 1000;
    controller.onTouch(view, MotionEvent.obtain(now, now,
          MotionEvent.ACTION_DOWN, 200f, 300f, 0));
    controller.onTouch(view, MotionEvent.obtain(now, now + 10,
          MotionEvent.ACTION_UP, 200f, 420f, 0));
    assertEquals(1, dispatched.size());
    assertEquals(ReaderAiAction.SPEED_READ, dispatched.get(0));
  }

  @Test
  public void controller_cancel_dispatches_nothing()
  {
    List<ReaderAiAction> dispatched = new ArrayList<>();
    ReaderAiButtonController controller = new ReaderAiButtonController(
        _prefs, 40f, dispatched::add);
    View view = new View(RuntimeEnvironment.getApplication());
    long now = 1000;
    controller.onTouch(view, MotionEvent.obtain(now, now,
          MotionEvent.ACTION_DOWN, 200f, 300f, 0));
    controller.onTouch(view, MotionEvent.obtain(now, now + 10,
          MotionEvent.ACTION_CANCEL, 200f, 300f, 0));
    assertTrue(dispatched.isEmpty());
    // A later UP without a new DOWN must not dispatch either.
    controller.onTouch(view, MotionEvent.obtain(now, now + 20,
          MotionEvent.ACTION_UP, 200f, 100f, 0));
    assertTrue(dispatched.isEmpty());
  }

  @Test
  public void quick_activity_intent_carries_action_and_new_task()
  {
    Context context = RuntimeEnvironment.getApplication();
    Intent intent = ReaderAiQuickActivity.intent(context,
        ReaderAiAction.SUMMARY_TWO);
    assertEquals(ReaderAiQuickActivity.class.getName(),
        intent.getComponent().getClassName());
    assertTrue((intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
    assertEquals(ReaderAiAction.SUMMARY_TWO.id,
        intent.getStringExtra("juloo.keyboard2.extra.AI_ACTION"));
  }
  @Test
  public void omnibutton_edge_swap_defaults_right_and_persists()
  {
    Context context = RuntimeEnvironment.getApplication();
    SharedPreferences prefs =
      context.getSharedPreferences("test", Context.MODE_PRIVATE);
    prefs.edit().clear().commit();
    assertTrue(ReaderAiAction.aiOnRight(prefs));
    ReaderAiAction.setAiOnRight(prefs, false);
    assertFalse(ReaderAiAction.aiOnRight(prefs));
    assertFalse(prefs.getBoolean("reader_ai_button_ai_right", true));
  }

  @Test
  public void config_loads_omnibutton_edge_swap()
  {
    Context context = RuntimeEnvironment.getApplication();
    SharedPreferences prefs =
      context.getSharedPreferences("test", Context.MODE_PRIVATE);
    prefs.edit().putBoolean("reader_ai_button_ai_right", false).commit();
    Config.initGlobalConfig(prefs, context.getResources(), null, null);
    assertFalse(Config.globalConfig().reader_ai_button_ai_right);
  }
}
