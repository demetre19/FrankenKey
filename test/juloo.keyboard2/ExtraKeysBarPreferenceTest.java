package juloo.keyboard2;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Looper;
import android.view.DragEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import java.lang.reflect.Method;
import java.util.List;
import juloo.keyboard2.prefs.ExtraKeysBarPreference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.DragEventBuilder;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
public class ExtraKeysBarPreferenceTest
{
  @Test
  public void keyboard_launch_waits_until_settings_window_is_frontmost()
  {
    Intent intent = new Intent(RuntimeEnvironment.getApplication(),
        SettingsActivity.class);
    intent.putExtra(SettingsActivity.EXTRA_OPEN_EXTRA_KEYS_BAR, true);
    ActivityController<SettingsActivity> controller =
      Robolectric.buildActivity(SettingsActivity.class, intent)
      .create().start().resume();
    shadowOf(Looper.getMainLooper()).idle();

    assertNull("The manager must not be inserted behind Settings before its window owns focus.",
        ShadowAlertDialog.getLatestAlertDialog());

    controller.get().onWindowFocusChanged(true);
    shadowOf(Looper.getMainLooper()).idle();

    AlertDialog manager = ShadowAlertDialog.getLatestAlertDialog();
    assertNotNull("The keyboard + action must show the Extra Keys manager.",
        manager);
    assertTrue("The Extra Keys manager must be the visible foreground window.",
        manager.isShowing());
  }

  @Test
  public void manager_toggles_defaults_and_adds_custom_modifier_chords()
      throws Exception
  {
    SettingsActivity activity = Robolectric.buildActivity(
        SettingsActivity.class).setup().get();
    SharedPreferences preferences = activity.getPreferenceManager()
      .getSharedPreferences();
    preferences.edit().remove(ExtraKeysShortcutStore.PREF_KEY).commit();
    ExtraKeysBarPreference preference = (ExtraKeysBarPreference)
      activity.findPreference("extra_keys_bar");

    preference.showManager();
    AlertDialog manager = ShadowAlertDialog.getLatestAlertDialog();
    assertNotNull("The settings row must open the shortcut manager.", manager);
    Switch esc = (Switch)findDescription(manager.getWindow().getDecorView(), "Esc");
    View escGrip = findDescription(manager.getWindow().getDecorView(),
        activity.getString(R.string.extra_keys_reorder, "Esc"));
    assertNotNull("Every shortcut must expose a drag handle.", escGrip);
    View escRow = (View)escGrip.getParent();
    View rows = (View)escRow.getParent();
    Method handleDrag = ExtraKeysBarPreference.class.getDeclaredMethod(
        "handleDrag", View.class, DragEvent.class);
    handleDrag.setAccessible(true);
    handleDrag.invoke(preference, rows,
        dragEvent(DragEvent.ACTION_DRAG_STARTED, 0, escRow));
    handleDrag.invoke(preference, rows,
        dragEvent(DragEvent.ACTION_DRAG_LOCATION, Float.MAX_VALUE, escRow));
    handleDrag.invoke(preference, rows,
        dragEvent(DragEvent.ACTION_DROP, Float.MAX_VALUE, escRow));
    handleDrag.invoke(preference, rows,
        dragEvent(DragEvent.ACTION_DRAG_ENDED, Float.MAX_VALUE, escRow));
    assertEquals("Dropping a row below its neighbor must persist the new order.",
        "cmd_c", ExtraKeysShortcutStore.load(preferences).get(0).id);
    assertNotNull("Every shortcut must expose a visibility switch.", esc);
    esc.performClick();
    assertFalse("Turning Esc off must persist immediately.",
        shortcut(preferences, "esc").enabled);

    Button add = (Button)findText(manager.getWindow().getDecorView(),
        activity.getString(R.string.extra_keys_add_shortcut), Button.class);
    assertNotNull("The manager must expose Add shortcut.", add);
    add.performClick();
    shadowOf(Looper.getMainLooper()).idle();
    AlertDialog addDialog = ShadowAlertDialog.getLatestAlertDialog();
    assertFalse("Opening the add flow must remove the manager from the window stack.",
        manager.isShowing());
    assertTrue("The add dialog must be the only visible foreground modal.",
        addDialog.isShowing());
    CheckBox ctrl = (CheckBox)findText(addDialog.getWindow().getDecorView(),
        "Ctrl", CheckBox.class);
    assertNotNull("The chord flow must offer Ctrl.", ctrl);
    ctrl.performClick();
    EditText customKey = (EditText)findDescription(
        addDialog.getWindow().getDecorView(),
        activity.getString(R.string.extra_keys_custom_key));
    assertNotNull("The chord flow must accept a key or command outside the preset picker.",
        customKey);
    customKey.setText("menu");
    addDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
    shadowOf(Looper.getMainLooper()).idle();
    assertTrue("Completing the add flow must restore the shortcut manager.",
        manager.isShowing());

    List<ExtraKeysShortcutStore.Shortcut> restored =
        ExtraKeysShortcutStore.load(preferences);

    ExtraKeysShortcutStore.Shortcut added = restored.get(restored.size() - 1);
    assertTrue("The add flow must append a custom shortcut.", added.isCustom());
    assertEquals("An arbitrary supported command name must create its chord.",
        "Ctrl+Menu", added.label);
    assertEquals("The custom command name must persist for dispatch.",
        "menu", added.keyName);
    assertEquals(ExtraKeysShortcutStore.CTRL, added.modifiers);
  }
  private static DragEvent dragEvent(int action, float y, View localState)
  {
    return DragEventBuilder.newBuilder()
      .setAction(action)
      .setY(y)
      .setLocalState(localState)
      .setResult(true)
      .build();
  }

  private static ExtraKeysShortcutStore.Shortcut shortcut(
      SharedPreferences preferences, String id)
  {
    for (ExtraKeysShortcutStore.Shortcut shortcut :
        ExtraKeysShortcutStore.load(preferences))
      if (shortcut.id.equals(id))
        return shortcut;
    return null;
  }

  private static View findDescription(View root, String description)
  {
    if (root == null)
      return null;
    if (description.equals(root.getContentDescription()))
      return root;
    if (root instanceof ViewGroup)
    {
      ViewGroup group = (ViewGroup)root;
      for (int i = 0; i < group.getChildCount(); i++)
      {
        View result = findDescription(group.getChildAt(i), description);
        if (result != null)
          return result;
      }
    }
    return null;
  }

  private static View findText(View root, String text, Class<?> type)
  {
    if (type.isInstance(root) && root instanceof TextView &&
        text.contentEquals(((TextView)root).getText()))
      return root;
    if (root instanceof ViewGroup)
    {
      ViewGroup group = (ViewGroup)root;
      for (int i = 0; i < group.getChildCount(); i++)
      {
        View result = findText(group.getChildAt(i), text, type);
        if (result != null)
          return result;
      }
    }
    return null;
  }
}
