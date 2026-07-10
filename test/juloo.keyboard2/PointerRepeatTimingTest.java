package juloo.keyboard2;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Looper;
import android.view.KeyEvent;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
@LooperMode(LooperMode.Mode.PAUSED)
public class PointerRepeatTimingTest
{
  private static final long LONG_PRESS_TIMEOUT_MS = 400L;
  private static final long GLOBAL_REPEAT_INTERVAL_MS = 47L;
  private static final long PASTE_REPEAT_INTERVAL_MS = 173L;
  private static final long DELETE_REPEAT_INTERVAL_MS = 89L;

  private SharedPreferences _prefs;
  private final List<Pointers> _pointers = new ArrayList<Pointers>();

  @Before
  public void setUp()
  {
    _prefs = RuntimeEnvironment.getApplication().getSharedPreferences(
        "pointer_repeat_timing_test", Context.MODE_PRIVATE);
    _prefs.edit().clear().commit();
  }

  @After
  public void tearDown()
  {
    for (Pointers pointers : _pointers)
      pointers.clear();
    _prefs.edit().clear().commit();
  }

  @Test
  public void tapping_paste_or_delete_dispatches_exactly_one_release_action()
      throws Exception
  {
    Config config = configuredTiming();
    for (RepeatCase repeatCase : pasteAndDeleteCases())
    {
      Harness harness = harness(config, repeatCase);

      harness.down();
      idleFor(LONG_PRESS_TIMEOUT_MS - 1L);
      assertEquals(repeatCase.label
          + " must not repeat before the long-press timeout.",
          0, harness.handler.holds.size());
      harness.up();

      assertEquals("A quick " + repeatCase.label
          + " tap must emit exactly one release action so the user's paste/delete is neither lost nor duplicated.",
          1, harness.handler.ups.size());
      assertEquals("The quick " + repeatCase.label
          + " tap must release the same action the user pressed.",
          repeatCase.value, harness.handler.ups.get(0));
      assertEquals("A quick " + repeatCase.label
          + " tap must not also emit a hold repeat.",
          0, harness.handler.holds.size());
    }
  }

  @Test
  public void releasing_after_first_paste_or_delete_repeat_emits_no_extra_action()
      throws Exception
  {
    Config config = configuredTiming();
    for (RepeatCase repeatCase : pasteAndDeleteCases())
    {
      Harness harness = harness(config, repeatCase);

      harness.down();
      idleFor(LONG_PRESS_TIMEOUT_MS);
      assertEquals("Holding " + repeatCase.label
          + " through the timeout must emit the first intentional repeat.",
          1, harness.handler.holds.size());
      assertEquals("The first held " + repeatCase.label
          + " action must repeat the key the user is still pressing.",
          repeatCase.value, harness.handler.holds.get(0));
      harness.up();

      assertEquals("Releasing " + repeatCase.label
          + " after a hold repeat must not add a second destructive or duplicating release action.",
          0, harness.handler.ups.size());
      assertEquals("The hold-and-release gesture for " + repeatCase.label
          + " must still have performed exactly its one elapsed repeat.",
          1, harness.handler.holds.size());
    }
  }

  @Test
  public void paste_and_plain_paste_use_the_configured_paste_interval_without_acceleration()
      throws Exception
  {
    Config config = configuredTiming();
    for (RepeatCase repeatCase : pasteCases())
    {
      Harness harness = harness(config, repeatCase);
      harness.down();
      idleFor(LONG_PRESS_TIMEOUT_MS);
      assertEquals("Held " + repeatCase.label
          + " must emit its first repeat at the long-press timeout.",
          1, harness.handler.holds.size());

      for (int expectedRepeats = 2; expectedRepeats <= 6;
          ++expectedRepeats)
      {
        idleFor(PASTE_REPEAT_INTERVAL_MS - 1L);
        assertEquals("Held " + repeatCase.label
            + " must wait for the full configured paste interval before repeat "
            + expectedRepeats + "; paste must never accelerate into a burst.",
            expectedRepeats - 1, harness.handler.holds.size());
        idleFor(1L);
        assertEquals("Held " + repeatCase.label
            + " must emit one repeat when its configured paste interval elapses.",
            expectedRepeats, harness.handler.holds.size());
      }
      harness.up();
    }
  }

  @Test
  public void destructive_keys_start_repeating_from_the_configured_delete_interval()
      throws Exception
  {
    Config config = configuredTiming();
    for (RepeatCase repeatCase : deleteCases())
    {
      Harness harness = harness(config, repeatCase);
      harness.down();
      idleFor(LONG_PRESS_TIMEOUT_MS);
      assertEquals("Held " + repeatCase.label
          + " must emit its first delete at the long-press timeout.",
          1, harness.handler.holds.size());

      idleFor(DELETE_REPEAT_INTERVAL_MS - 1L);
      assertEquals("Held " + repeatCase.label
          + " must not inherit the faster global interval; its first paced repeat must wait for the configured delete interval.",
          1, harness.handler.holds.size());
      idleFor(1L);
      assertEquals("Held " + repeatCase.label
          + " must emit its next delete when the configured delete interval elapses.",
          2, harness.handler.holds.size());
      harness.up();
    }
  }

  @Test
  public void normal_repeatable_character_keeps_using_the_global_interval()
      throws Exception
  {
    RepeatCase character = new RepeatCase("character", KeyValue.getKeyByName("a"));
    Harness harness = harness(configuredTiming(), character);

    harness.down();
    idleFor(LONG_PRESS_TIMEOUT_MS);
    assertEquals("A held character must emit its first repeat at the long-press timeout.",
        1, harness.handler.holds.size());
    idleFor(GLOBAL_REPEAT_INTERVAL_MS - 1L);
    assertEquals("Ordinary characters must keep waiting on the global interval rather than adopting safer paste/delete pacing.",
        1, harness.handler.holds.size());
    idleFor(1L);
    assertEquals("An ordinary character must repeat when the configured global interval elapses.",
        2, harness.handler.holds.size());
    harness.up();
  }

  private Config configuredTiming()
      throws Exception
  {
    _prefs.edit()
      .putInt("longpress_timeout", (int)LONG_PRESS_TIMEOUT_MS)
      .putInt("longpress_interval", (int)GLOBAL_REPEAT_INTERVAL_MS)
      .putInt("paste_repeat_interval", (int)PASTE_REPEAT_INTERVAL_MS)
      .putInt("delete_repeat_interval", (int)DELETE_REPEAT_INTERVAL_MS)
      .putBoolean("keyrepeat_enabled", true)
      .commit();
    Constructor<Config> constructor = Config.class.getDeclaredConstructor(
        SharedPreferences.class, Resources.class, Boolean.class,
        juloo.keyboard2.dict.Dictionaries.class);
    constructor.setAccessible(true);
    return constructor.newInstance(_prefs,
        new TestResources(RuntimeEnvironment.getApplication().getResources()),
        Boolean.FALSE, null);
  }

  private Harness harness(Config config, RepeatCase repeatCase)
  {
    RecordingHandler handler = new RecordingHandler();
    Pointers pointers = new Pointers(handler, config);
    _pointers.add(pointers);
    return new Harness(pointers, handler, key(repeatCase.value));
  }

  private static List<RepeatCase> pasteAndDeleteCases()
  {
    List<RepeatCase> cases = new ArrayList<RepeatCase>();
    cases.addAll(pasteCases());
    cases.addAll(deleteCases());
    return cases;
  }

  private static List<RepeatCase> pasteCases()
  {
    return Arrays.asList(
        new RepeatCase("paste", KeyValue.getKeyByName("paste")),
        new RepeatCase("plain paste",
          KeyValue.getKeyByName("pasteAsPlainText")));
  }

  private static List<RepeatCase> deleteCases()
  {
    return Arrays.asList(
        new RepeatCase("Backspace", KeyValue.getKeyByName("backspace")),
        new RepeatCase("delete word", KeyValue.getKeyByName("delete_word")),
        new RepeatCase("forward delete word",
          KeyValue.getKeyByName("forward_delete_word")),
        new RepeatCase("KEYCODE_DEL",
          KeyValue.keyeventKey("Del", KeyEvent.KEYCODE_DEL, 0)),
        new RepeatCase("KEYCODE_FORWARD_DEL",
          KeyValue.keyeventKey("Forward Del", KeyEvent.KEYCODE_FORWARD_DEL, 0)));
  }

  private static KeyboardData.Key key(KeyValue value)
  {
    KeyValue[] values = new KeyValue[9];
    values[0] = value;
    return new KeyboardData.Key(values, null, 0, 1f, 0f, null,
        KeyboardData.Key.Role.Normal);
  }

  private static void idleFor(long milliseconds)
  {
    shadowOf(Looper.getMainLooper()).idleFor(milliseconds,
        TimeUnit.MILLISECONDS);
  }

  private static final class RepeatCase
  {
    final String label;
    final KeyValue value;

    RepeatCase(String label_, KeyValue value_)
    {
      label = label_;
      value = value_;
    }
  }

  private static final class Harness
  {
    final Pointers pointers;
    final RecordingHandler handler;
    final KeyboardData.Key key;
    final int pointerId;
    private static int nextPointerId = 1;

    Harness(Pointers pointers_, RecordingHandler handler_,
        KeyboardData.Key key_)
    {
      pointers = pointers_;
      handler = handler_;
      key = key_;
      pointerId = nextPointerId++;
    }

    void down()
    {
      pointers.onTouchDown(0f, 0f, pointerId, key, null);
    }

    void up()
    {
      pointers.onTouchUp(pointerId);
    }
  }

  private static final class RecordingHandler
      implements Pointers.IPointerEventHandler
  {
    final List<KeyValue> holds = new ArrayList<KeyValue>();
    final List<KeyValue> ups = new ArrayList<KeyValue>();

    @Override public KeyValue modifyKey(KeyValue key,
        Pointers.Modifiers modifiers) { return key; }
    @Override public void onPointerDown(KeyValue key, boolean swipe) {}
    @Override public void onPointerFlagsChanged(boolean vibrate) {}
    @Override public void onPointerCancel(KeyValue key,
        Pointers.Modifiers modifiers) {}
    @Override public void onKeyboardSwipeUp() {}
    @Override public void onKeyboardSwipeDown() {}

    @Override
    public void onPointerHold(KeyValue key, Pointers.Modifiers modifiers,
        int count)
    {
      holds.add(key);
    }

    @Override
    public void onPointerUp(KeyValue key, Pointers.Modifiers modifiers,
        TouchTrace.Entry touch)
    {
      ups.add(key);
    }
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
