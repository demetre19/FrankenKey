package juloo.keyboard2;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Looper;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.util.TypedValue;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.ListView;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import juloo.keyboard2.suggestions.PersonalizationStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35, qualifiers = "notnight")
public class SettingsActivityTypingAssistanceTest
{
  private Context _context;
  private SharedPreferences _defaultPrefs;
  private SharedPreferences _protectedPrefs;
  private ActivityController<SettingsActivity> _controller;

  @Before
  public void setUp()
  {
    _context = RuntimeEnvironment.getApplication();
    _defaultPrefs = PreferenceManager.getDefaultSharedPreferences(_context);
    _protectedPrefs = DirectBootAwarePreferences
      .get_shared_preferences(_context);
    PersonalizationStore.clear(_defaultPrefs);
    PersonalizationStore.clear(_protectedPrefs);
  }

  @After
  public void tearDown()
  {
    if (_controller != null)
      _controller.pause().stop().destroy();
    PersonalizationStore.clear(_defaultPrefs);
    PersonalizationStore.clear(_protectedPrefs);
  }

  @Test
  public void opening_clear_confirmation_does_not_mutate_any_adaptive_rows()
  {
    SettingsActivity activity = launchSeededActivity();
    Map<String, Set<String>> defaultBefore = adaptiveSnapshot(_defaultPrefs);
    Map<String, Set<String>> protectedBefore = adaptiveSnapshot(_protectedPrefs);

    AlertDialog dialog = openClearDialog(activity);

    assertTrue("Opening Clear adaptive learning must leave the confirmation visible for an explicit decision.",
        dialog.isShowing());
    assertEquals("Opening the confirmation must preserve learned words, bigrams, and typo corrections in the primary store.",
        defaultBefore, adaptiveSnapshot(_defaultPrefs));
    assertEquals("Opening the confirmation must preserve learned words, bigrams, and typo corrections in device-protected storage.",
        protectedBefore, adaptiveSnapshot(_protectedPrefs));
    assertSeededAdaptiveData(_defaultPrefs);
    assertSeededAdaptiveData(_protectedPrefs);
  }

  @Test
  public void cancelling_clear_confirmation_is_nondestructive()
  {
    SettingsActivity activity = launchSeededActivity();
    Map<String, Set<String>> defaultBefore = adaptiveSnapshot(_defaultPrefs);
    Map<String, Set<String>> protectedBefore = adaptiveSnapshot(_protectedPrefs);
    AlertDialog dialog = openClearDialog(activity);

    assertTrue("Clear adaptive learning must expose a Cancel button.",
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick());
    shadowOf(Looper.getMainLooper()).idle();

    assertFalse("Cancel must close the destructive confirmation.",
        dialog.isShowing());
    assertEquals("Cancel must preserve every primary adaptive data row.",
        defaultBefore, adaptiveSnapshot(_defaultPrefs));
    assertEquals("Cancel must preserve every device-protected adaptive data row.",
        protectedBefore, adaptiveSnapshot(_protectedPrefs));
    assertSeededAdaptiveData(_defaultPrefs);
    assertSeededAdaptiveData(_protectedPrefs);
    assertStatus(activity, R.string.pref_typing_assistance_status_with_learning);
  }

  @Test
  public void positive_confirmation_clears_all_stores_and_refreshes_status()
  {
    SettingsActivity activity = launchSeededActivity();
    AlertDialog dialog = openClearDialog(activity);

    assertTrue("Clear adaptive learning must expose a positive destructive action.",
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick());
    shadowOf(Looper.getMainLooper()).idle();

    assertFalse("Positive confirmation must remove words, bigrams, and typo corrections from the primary store.",
        PersonalizationStore.has_data(_defaultPrefs));
    assertFalse("Positive confirmation must remove words, bigrams, and typo corrections from device-protected storage.",
        PersonalizationStore.has_data(_protectedPrefs));
    assertTrue("No primary adaptive preference rows may survive confirmed clearing.",
        adaptiveSnapshot(_defaultPrefs).isEmpty());
    assertTrue("No device-protected adaptive preference rows may survive confirmed clearing.",
        adaptiveSnapshot(_protectedPrefs).isEmpty());
    assertStatus(activity, R.string.pref_typing_assistance_status_empty);
  }

  @Test
  public void settings_activity_resolves_dark_theme_during_day_configuration()
  {
    SettingsActivity activity = launchActivity();
    int nightMode = activity.getResources().getConfiguration().uiMode
      & Configuration.UI_MODE_NIGHT_MASK;
    TypedValue isLightTheme = new TypedValue();

    assertEquals("The Robolectric fixture must be in daytime mode for this regression contract.",
        Configuration.UI_MODE_NIGHT_NO, nightMode);
    assertTrue("The platform Settings theme must publish android:isLightTheme.",
        activity.getTheme().resolveAttribute(android.R.attr.isLightTheme,
          isLightTheme, true));
    assertEquals("SettingsActivity must remain dark even when the system configuration is daytime.",
        0, isLightTheme.data);
  }

  private SettingsActivity launchSeededActivity()
  {
    seedAdaptiveData(_defaultPrefs);
    if (_protectedPrefs != _defaultPrefs)
      seedAdaptiveData(_protectedPrefs);
    SettingsActivity activity = launchActivity();
    assertSeededAdaptiveData(_defaultPrefs);
    assertSeededAdaptiveData(_protectedPrefs);
    assertStatus(activity, R.string.pref_typing_assistance_status_with_learning);
    return activity;
  }

  private SettingsActivity launchActivity()
  {
    _controller = Robolectric.buildActivity(SettingsActivity.class).setup();
    return _controller.get();
  }

  private static void seedAdaptiveData(SharedPreferences prefs)
  {
    PersonalizationStore store = new PersonalizationStore(prefs);
    store.record_commit("hello", null);
    store.record_commit("world", "wprld");
  }

  private static void assertSeededAdaptiveData(SharedPreferences prefs)
  {
    Map<String, Set<String>> rows = adaptiveSnapshot(prefs);
    assertEquals("The fixture must contain separate learned-word, bigram, and typo-correction rows.",
        3, rows.size());
    assertFalse("Learned-word rows must be populated.",
        rows.get(PersonalizationStore.PREF_WORDS).isEmpty());
    assertFalse("Bigram rows must be populated.",
        rows.get(PersonalizationStore.PREF_BIGRAMS).isEmpty());
    assertFalse("Typo-correction rows must be populated.",
        rows.get(PersonalizationStore.PREF_CORRECTIONS).isEmpty());
    PersonalizationStore reloaded = new PersonalizationStore(prefs);
    assertTrue("The seeded target word must be learned so its correction row is valid.",
        reloaded.is_learned("world"));
  }

  private static Map<String, Set<String>> adaptiveSnapshot(
      SharedPreferences prefs)
  {
    Map<String, Set<String>> snapshot =
      new HashMap<String, Set<String>>();
    for (String key : new String[] {
          PersonalizationStore.PREF_WORDS,
          PersonalizationStore.PREF_BIGRAMS,
          PersonalizationStore.PREF_CORRECTIONS
        })
    {
      Set<String> values = prefs.getStringSet(key, null);
      if (values != null)
        snapshot.put(key, new HashSet<String>(values));
    }
    return snapshot;
  }

  private static AlertDialog openClearDialog(SettingsActivity activity)
  {
    clickPreference(activity, "clear_typing_assistance_data");
    AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
    assertNotNull("Tapping Clear adaptive learning must open an AlertDialog.",
        dialog);
    return dialog;
  }

  private static void clickPreference(SettingsActivity activity, String key)
  {
    ListView list = activity.getListView();
    ListAdapter adapter = list.getAdapter();
    for (int position = 0; position < adapter.getCount(); ++position)
    {
      Object item = adapter.getItem(position);
      if (!(item instanceof Preference)
          || !key.equals(((Preference)item).getKey()))
        continue;
      View row = adapter.getView(position, null, list);
      assertTrue("The standalone preference row must handle a user click: " + key,
          list.performItemClick(row, position, adapter.getItemId(position)));
      return;
    }
    fail("Missing visible preference row: " + key);
  }

  private static void assertStatus(SettingsActivity activity, int expected)
  {
    Preference status = activity.findPreference("typing_assistance_status");
    assertNotNull("Settings must expose adaptive-learning status.", status);
    assertEquals("Adaptive-learning status must refresh from persisted data.",
        activity.getString(expected), status.getSummary().toString());
  }
}
