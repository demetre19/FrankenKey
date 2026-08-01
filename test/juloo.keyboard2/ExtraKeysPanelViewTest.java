package juloo.keyboard2;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
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
  public void modifiers_support_tap_latch_and_press_hold_combinations()
  {
    ExtraKeysPanelView panel = panel();
    TextView ctrl = findText(panel, "Ctrl");

    touch(ctrl, MotionEvent.ACTION_DOWN);
    assertTrue("Ctrl must become active immediately so another finger can press a combo key.",
        panel.currentModifiers().has(KeyValue.Modifier.CTRL));
    touch(ctrl, MotionEvent.ACTION_UP);
    assertTrue("A Ctrl tap arms the next key.",
        panel.currentModifiers().has(KeyValue.Modifier.CTRL));
    panel.consumeLatchedModifiers();
    assertFalse("The next dispatched key consumes a tapped Ctrl latch.",
        panel.currentModifiers().has(KeyValue.Modifier.CTRL));

    touch(ctrl, MotionEvent.ACTION_DOWN);
    touch(ctrl, MotionEvent.ACTION_UP);
    touch(ctrl, MotionEvent.ACTION_DOWN);
    touch(ctrl, MotionEvent.ACTION_UP);
    assertFalse("A second Ctrl tap cancels an unused latch.",
        panel.currentModifiers().has(KeyValue.Modifier.CTRL));

    touch(ctrl, MotionEvent.ACTION_DOWN);
    panel.markHeldModifiersUsed();
    touch(ctrl, MotionEvent.ACTION_UP);
    assertFalse("A held Ctrl releases after the combo key instead of becoming stuck.",
        panel.currentModifiers().has(KeyValue.Modifier.CTRL));
  }

  @Test
  public void panel_exposes_navigation_shortcuts_and_function_keys()
  {
    ExtraKeysPanelView panel = panel();
    String[] more = {
      "Esc", "Tab", "Home", "End", "Ins", "Del", "PgUp", "PgDn",
      "Enter", "←", "↑", "↓", "→", "Cmd+C", "Cmd+V", "Cmd+S",
    };
    for (String label : more)
      assertNotNull("The More panel must expose " + label + ".",
          findText(panel, label));

    findText(panel, "Fn").performClick();

    assertNull("Function mode replaces the More-key grid.", findText(panel, "Esc"));
    for (int i = 1; i <= 12; i++)
      assertNotNull("Function mode must expose F" + i + ".",
          findText(panel, "F" + i));
  }

  private static ExtraKeysPanelView panel()
  {
    Context context = new ContextThemeWrapper(
        RuntimeEnvironment.getApplication(), R.style.Dark);
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

  private static void touch(View view, int action)
  {
    MotionEvent event = MotionEvent.obtain(0, 0, action, 1f, 1f, 0);
    view.dispatchTouchEvent(event);
    event.recycle();
  }
}
