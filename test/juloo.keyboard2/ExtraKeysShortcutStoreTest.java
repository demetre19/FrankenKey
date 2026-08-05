package juloo.keyboard2;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
public class ExtraKeysShortcutStoreTest
{
  @Test
  public void visibility_order_and_custom_chords_round_trip()
  {
    SharedPreferences preferences = RuntimeEnvironment.getApplication()
      .getSharedPreferences("extra_keys_store_round_trip", Context.MODE_PRIVATE);
    preferences.edit().clear().commit();
    List<ExtraKeysShortcutStore.Shortcut> shortcuts =
      new ArrayList<ExtraKeysShortcutStore.Shortcut>(
          ExtraKeysShortcutStore.defaults());
    ExtraKeysShortcutStore.Shortcut esc = shortcuts.remove(0);
    shortcuts.add(4, esc.withEnabled(false));
    ExtraKeysShortcutStore.Shortcut chord = ExtraKeysShortcutStore.custom(
        "r", "R", ExtraKeysShortcutStore.CTRL | ExtraKeysShortcutStore.SHIFT);
    shortcuts.add(0, chord);

    ExtraKeysShortcutStore.save(preferences, shortcuts);
    List<ExtraKeysShortcutStore.Shortcut> restored =
      ExtraKeysShortcutStore.load(preferences);

    assertEquals("Saved order must drive the shortcut bar.",
        chord.id, restored.get(0).id);
    assertEquals("Custom modifier combinations must retain their label.",
        "Ctrl+Shift+R", restored.get(0).label);
    assertEquals("Custom modifier combinations must retain every modifier.",
        ExtraKeysShortcutStore.CTRL | ExtraKeysShortcutStore.SHIFT,
        restored.get(0).modifiers);
    assertFalse("Hidden shortcuts must stay hidden after reload.",
        restored.get(5).enabled);
  }

  @Test
  public void corrupt_configuration_fails_closed_to_complete_defaults()
  {
    SharedPreferences preferences = RuntimeEnvironment.getApplication()
      .getSharedPreferences("extra_keys_store_corrupt", Context.MODE_PRIVATE);
    preferences.edit().putString(ExtraKeysShortcutStore.PREF_KEY,
        "[{\"id\":\"bad\",\"label\":\"Bad\",\"action\":\"key\",\"key\":\"unsupported\"}]")
      .commit();

    List<ExtraKeysShortcutStore.Shortcut> restored =
      ExtraKeysShortcutStore.load(preferences);

    assertEquals("Invalid configuration must restore the complete default set.",
        ExtraKeysShortcutStore.defaults().size(), restored.size());
    assertEquals("Recovery must retain the utility-first default order.",
        "esc", restored.get(0).id);
  }
}
