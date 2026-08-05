package juloo.keyboard2;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.ContextThemeWrapper;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.TextView;
import juloo.keyboard2.suggestions.Decoder;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
public class ExtraKeysPanelViewTest
{
  @Test
  public void modifier_tap_stays_active_until_the_same_button_is_tapped_again()
  {
    ExtraKeysPanelView panel = panel();
    TextView ctrl = findText(panel, "Ctrl");

    tap(ctrl, 100);
    assertTrue("One Ctrl tap must keep the modifier active.",
        panel.currentModifiers().has(KeyValue.Modifier.CTRL));
    assertTrue("An active modifier must keep visible selected feedback.",
        ctrl.isActivated());

    tap(ctrl, 1_000);
    assertFalse("Ctrl must turn off only when Ctrl is tapped again.",
        panel.currentModifiers().has(KeyValue.Modifier.CTRL));
    assertFalse("Turning off a modifier must remove selected feedback.",
        ctrl.isActivated());
  }

  @Test
  public void multiple_modifiers_survive_key_dispatch_until_individually_tapped_off()
      throws Exception
  {
    Context context = context();
    Config.initGlobalConfig(
        context.getSharedPreferences("extra_keys_panel_test", Context.MODE_PRIVATE),
        context.getResources(), false, null);
    RecordingHandler handler = new RecordingHandler();
    Config.globalConfig().handler = handler;
    TestKeyboard2View keyboard = new TestKeyboard2View(context);
    keyboard.setKeyboard(KeyboardData.load_string_exn(
        "<keyboard bottom_row=\"false\" width=\"1\"><row><key c=\"space\"/></row></keyboard>"));
    ExtraKeysPanelView panel = new ExtraKeysPanelView(context);
    panel.bind(keyboard, handler);
    TextView command = findText(panel, "Cmd");
    TextView shift = findText(panel, "Shift");

    tap(command, 2_000);
    tap(shift, 3_000);

    KeyValue space = KeyValue.getSpecialKeyByName("space");
    Pointers.Modifiers modifiers = keyboardModifiers(keyboard);
    keyboard.onPointerUp(space, modifiers, null);
    keyboard.onPointerUp(space, modifiers, null);
    assertEquals("The keyboard must dispatch the Space key.", space,
        handler.releasedKey);
    assertTrue("Repeated keys must continue carrying Cmd.",
        handler.releasedModifiers.has(KeyValue.Modifier.META));
    assertTrue("Repeated keys must continue carrying Shift.",
        handler.releasedModifiers.has(KeyValue.Modifier.SHIFT));
    assertTrue("Key dispatch must not clear Cmd.",
        panel.currentModifiers().has(KeyValue.Modifier.META));
    assertTrue("Key dispatch must not clear Shift.",
        panel.currentModifiers().has(KeyValue.Modifier.SHIFT));

    tap(command, 4_000);
    assertFalse("Cmd must turn off when Cmd is tapped again.",
        panel.currentModifiers().has(KeyValue.Modifier.META));
    assertTrue("Turning off Cmd must leave Shift active.",
        panel.currentModifiers().has(KeyValue.Modifier.SHIFT));
    tap(shift, 5_000);
    assertFalse("Shift must turn off when Shift is tapped again.",
        panel.currentModifiers().has(KeyValue.Modifier.SHIFT));
  }

  @Test
  public void utility_first_buttons_dispatch_return_and_shift_tab() throws Exception
  {
    Context context = context();
    Config.initGlobalConfig(
        context.getSharedPreferences("extra_keys_actions_test", Context.MODE_PRIVATE),
        context.getResources(), false, null);
    RecordingHandler handler = new RecordingHandler();
    Config.globalConfig().handler = handler;
    TestKeyboard2View keyboard = new TestKeyboard2View(context);
    keyboard.setKeyboard(KeyboardData.load_string_exn(
        "<keyboard bottom_row=\"false\" width=\"1\"><row><key c=\"space\"/></row></keyboard>"));
    ExtraKeysPanelView panel = new ExtraKeysPanelView(context);
    panel.bind(keyboard, handler);
    ViewGroup row = rowAt(panel, 0);
    String[] firstLabels = {
      "…", "Esc", "Cmd+C", "Tab", "Return", "Shift+Tab", "Space",
    };
    for (int i = 0; i < firstLabels.length; i++)
      assertEquals("The compact row must put useful terminal actions first.",
          firstLabels[i], ((TextView)row.getChildAt(i)).getText().toString());

    findText(panel, "Return").performClick();
    assertEquals("Return must dispatch literal text, not the editor action key.",
        KeyValue.Kind.Char, handler.releasedKey.getKind());
    assertEquals("Return must insert a newline without requiring Shift.",
        '\n', handler.releasedKey.getChar());
    assertFalse("Return must not synthesize Shift.",
        handler.releasedModifiers.has(KeyValue.Modifier.SHIFT));

    findText(panel, "Shift+Tab").performClick();
    assertEquals("Shift+Tab must dispatch the Tab key event.",
        KeyEvent.KEYCODE_TAB, handler.releasedKey.getKeyevent());
    assertTrue("Shift+Tab must carry Shift.",
        handler.releasedModifiers.has(KeyValue.Modifier.SHIFT));
    assertFalse("Temporary Shift+Tab must not leave Shift selected.",
        panel.currentModifiers().has(KeyValue.Modifier.SHIFT));

    tap(findText(panel, "Shift"), 7_000);
    findText(panel, "Return").performClick();
    assertEquals("Return must remain a newline while persistent Shift is selected.",
        '\n', handler.releasedKey.getChar());
    assertTrue("Return must not clear persistent Shift.",
        panel.currentModifiers().has(KeyValue.Modifier.SHIFT));
  }

  @Test
  public void configured_visibility_order_and_custom_chord_drive_the_panel()
      throws Exception
  {
    Context context = context();
    SharedPreferences preferences = context.getSharedPreferences(
        "extra_keys_configured_test", Context.MODE_PRIVATE);
    preferences.edit().clear().commit();
    List<ExtraKeysShortcutStore.Shortcut> shortcuts =
        new ArrayList<ExtraKeysShortcutStore.Shortcut>(
            ExtraKeysShortcutStore.defaults());
    for (int i = 0; i < shortcuts.size(); i++)
      if (shortcuts.get(i).id.equals("esc"))
        shortcuts.set(i, shortcuts.get(i).withEnabled(false));
    ExtraKeysShortcutStore.Shortcut custom = ExtraKeysShortcutStore.custom(
        "r", "R", ExtraKeysShortcutStore.CTRL);
    shortcuts.add(0, custom);
    ExtraKeysShortcutStore.save(preferences, shortcuts);
    Config.initGlobalConfig(preferences, context.getResources(), false, null);

    RecordingHandler handler = new RecordingHandler();
    Config.globalConfig().handler = handler;
    TestKeyboard2View keyboard = new TestKeyboard2View(context);
    keyboard.setKeyboard(KeyboardData.load_string_exn(
        "<keyboard bottom_row=\"false\" width=\"1\"><row><key c=\"space\"/></row></keyboard>"));
    ExtraKeysPanelView panel = new ExtraKeysPanelView(context);
    panel.bind(keyboard, handler);
    ViewGroup row = rowAt(panel, 0);

    assertEquals("The first saved shortcut must render first after the ellipsis.",
        "Ctrl+R", ((TextView)row.getChildAt(1)).getText().toString());
    assertNull("A disabled shortcut must not render.", findText(panel, "Esc"));

    findText(panel, "Ctrl+R").performClick();
    assertEquals("A custom chord must dispatch its selected key.",
        KeyEvent.KEYCODE_R, handler.releasedKey.getKeyevent());
    assertTrue("A custom chord must dispatch its selected modifier.",
        handler.releasedModifiers.has(KeyValue.Modifier.CTRL));
    assertFalse("A custom chord must not leave its modifier selected.",
        panel.currentModifiers().has(KeyValue.Modifier.CTRL));
  }

  @Test
  public void compact_strip_expands_to_three_scrollable_rows_and_collapses()
  {
    ExtraKeysPanelView panel = panel();
    assertEquals("The keyboard ellipsis must reveal one compact Extra Keys tier.",
        1, panel.getChildCount());
    assertTrue("The compact tier must swipe horizontally.",
        panel.getChildAt(0) instanceof HorizontalScrollView);
    ViewGroup row = rowAt(panel, 0);
    assertEquals("The compact swipe lane must wrap one button.",
        ViewGroup.LayoutParams.WRAP_CONTENT, row.getLayoutParams().height);

    String[] labels = {
      "Ctrl", "Alt", "Shift", "Cmd", "Pin", "…", "+",
      "Esc", "Tab", "Home", "End", "Ins", "Del", "PgUp", "PgDn",
      "Return", "Shift+Tab", "Space", "Backspace", "←", "↑", "↓", "→",
      "Cmd+C", "Cmd+V", "Cmd+S",
    };
    for (String label : labels)
      assertSame(label + " must stay directly reachable in the compact lane.",
          row, findText(panel, label).getParent());
    for (int i = 1; i <= 12; i++)
      assertSame("F" + i + " must be directly reachable by swiping.",
          row, findText(panel, "F" + i).getParent());

    TextView ctrl = findText(panel, "Ctrl");
    assertEquals("Compact keys use 8dp internal padding.", dp(panel, 8),
        ctrl.getPaddingTop());
    ViewGroup.MarginLayoutParams margins =
        (ViewGroup.MarginLayoutParams)ctrl.getLayoutParams();
    assertEquals("Buttons keep 4dp clear above adjacent UI.", dp(panel, 4),
        margins.topMargin);
    assertEquals("Buttons keep 4dp clear below adjacent UI.", dp(panel, 4),
        margins.bottomMargin);
    tap(ctrl, 8_000);

    findText(panel, "…").performClick();

    assertEquals("Show all must never use more than three rows.",
        3, panel.getChildCount());
    for (int i = 0; i < panel.getChildCount(); i++)
      assertTrue("Every expanded row must scroll horizontally.",
          panel.getChildAt(i) instanceof HorizontalScrollView);
    assertSame("The first configured group must stay in row one.",
        rowAt(panel, 0), findText(panel, "Esc").getParent());
    assertSame("The next configured group must stay in row two.",
        rowAt(panel, 1), findText(panel, "Home").getParent());
    assertSame("The final configured group must stay in row three.",
        rowAt(panel, 2), findText(panel, "F12").getParent());
    assertTrue("Expanding must preserve an active modifier.",
        panel.currentModifiers().has(KeyValue.Modifier.CTRL));
    assertTrue("Expanded controls must preserve selected feedback.",
        findText(panel, "Ctrl").isActivated());

    int phoneWidth = dp(panel, 360);
    panel.measure(View.MeasureSpec.makeMeasureSpec(phoneWidth, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
    assertTrue("At least one expanded row must expose horizontal overflow.",
        occupiedWidth(rowAt(panel, 0)) > phoneWidth);

    findText(panel, "…").performClick();
    assertEquals("The second ellipsis must collapse back to one tier.",
        1, panel.getChildCount());
  }

  private static Context context()
  {
    return new ContextThemeWrapper(
        RuntimeEnvironment.getApplication(), R.style.Dark);
  }

  private static ExtraKeysPanelView panel()
  {
    Context context = context();
    SharedPreferences preferences = context.getSharedPreferences(
        "extra_keys_default_panel_test", Context.MODE_PRIVATE);
    preferences.edit().clear().commit();
    Config.initGlobalConfig(preferences, context.getResources(), false, null);
    return new ExtraKeysPanelView(context);
  }

  private static TextView findText(View root, String text)
  {
    if (root instanceof TextView && text.contentEquals(((TextView)root).getText()))
      return (TextView)root;
    if (root instanceof ViewGroup)
    {
      ViewGroup group = (ViewGroup)root;
      for (int i = 0; i < group.getChildCount(); i++)
      {
        TextView result = findText(group.getChildAt(i), text);
        if (result != null)
          return result;
      }
    }
    return null;
  }

  private static int dp(View view, int value)
  {
    return Math.round(value * view.getResources().getDisplayMetrics().density);
  }

  private static ViewGroup rowAt(ExtraKeysPanelView panel, int index)
  {
    View child = panel.getChildAt(index);
    if (child instanceof HorizontalScrollView)
      return (ViewGroup)((HorizontalScrollView)child).getChildAt(0);
    return (ViewGroup)child;
  }

  private static int occupiedWidth(ViewGroup row)
  {
    int width = row.getPaddingLeft() + row.getPaddingRight();
    for (int i = 0; i < row.getChildCount(); i++)
    {
      View child = row.getChildAt(i);
      ViewGroup.MarginLayoutParams margins =
          (ViewGroup.MarginLayoutParams)child.getLayoutParams();
      width += margins.leftMargin + child.getMeasuredWidth() + margins.rightMargin;
    }
    return width;
  }


  private static void tap(View view, long start)
  {
    touch(view, MotionEvent.ACTION_DOWN, start);
    touch(view, MotionEvent.ACTION_UP, start + 50);
  }

  private static void touch(View view, int action, long eventTime)
  {
    MotionEvent event = MotionEvent.obtain(
        eventTime, eventTime, action, 1f, 1f, 0);
    view.dispatchTouchEvent(event);
    event.recycle();
  }

  private static Pointers.Modifiers keyboardModifiers(Keyboard2View keyboard)
      throws Exception
  {
    Field field = Keyboard2View.class.getDeclaredField("_mods");
    field.setAccessible(true);
    return (Pointers.Modifiers)field.get(keyboard);
  }

  private static final class TestKeyboard2View extends Keyboard2View
  {
    TestKeyboard2View(Context context)
    {
      super(context, null);
    }

    @Override
    public void refresh_navigation_bar(Context context)
    {
    }
  }

  private static final class RecordingHandler
      implements Config.IKeyEventHandler
  {
    KeyValue releasedKey;
    Pointers.Modifiers releasedModifiers;

    @Override public void key_down(KeyValue value, boolean isSwipe) {}

    @Override
    public void key_up(KeyValue value, Pointers.Modifiers mods,
        TouchTrace.Entry touch)
    {
      releasedKey = value;
      releasedModifiers = mods;
    }

    @Override public void key_cancel(KeyValue value, Pointers.Modifiers mods) {}
    @Override public void key_hold(KeyValue value, Pointers.Modifiers mods,
        int holdCount) {}
    @Override public void mods_changed(Pointers.Modifiers mods) {}
    @Override public void suggestion_entered(Decoder.RequestKey key, String text) {}
    @Override public void suggestion_swiped_up(Decoder.RequestKey key, String text) {}
    @Override public void typing_assistance_data_cleared() {}
    @Override public void keyboard_swiped_up() {}
    @Override public void keyboard_swiped_down() {}
  }
}
