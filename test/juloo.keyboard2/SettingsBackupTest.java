package juloo.keyboard2;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import juloo.keyboard2.snippets.SnippetSlot;
import juloo.keyboard2.snippets.SnippetStore;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
public class SettingsBackupTest
{
  private Context _context;
  private SharedPreferences _defaultPrefs;

  @Before
  public void setUp()
  {
    _context = RuntimeEnvironment.getApplication();
    _defaultPrefs = PreferenceManager.getDefaultSharedPreferences(_context);
    clearStores();
    SnippetStore.saveSlots(_context, SnippetStore.loadSlots(null,
          SnippetStore.DEFAULT_SLOT_COUNT));
  }

  @Test
  public void export_import_restores_every_supported_type_in_all_backup_stores()
      throws Exception
  {
    Map<String, ExpectedPrefs> expected = new HashMap<>();
    expected.put("default", fillStore(_defaultPrefs, "default"));
    for (String name : namedStores())
      expected.put(name, fillStore(store(name), name));

    JSONObject exported = SettingsBackup.exportToJson(_context, _defaultPrefs);

    overwriteStoresWithWrongValues();
    SettingsBackup.importFromJson(_context, exported, _defaultPrefs);

    assertStore("default", _defaultPrefs, expected.get("default"));
    for (String name : namedStores())
      assertStore(name, store(name), expected.get(name));
  }

  @Test
  public void export_import_restores_device_protected_runtime_preferences()
      throws Exception
  {
    SharedPreferences protectedPrefs =
      DirectBootAwarePreferences.get_protected_prefs(_context);
    protectedPrefs.edit().clear()
      .putString("current_layout_portrait", "clean_text")
      .putString("current_layout_landscape", "latn_qwerty_us")
      .putBoolean("clean_mode", true)
      .putInt("layout_switcher_position", 7)
      .commit();

    JSONObject exported = SettingsBackup.exportToJson(_context, _defaultPrefs);

    protectedPrefs.edit().clear()
      .putString("current_layout_portrait", "wrong")
      .putString("stale", "must be removed")
      .commit();
    SettingsBackup.importFromJson(_context, exported, _defaultPrefs);

    assertEquals("portrait runtime layout", "clean_text",
        protectedPrefs.getString("current_layout_portrait", null));
    assertEquals("landscape runtime layout", "latn_qwerty_us",
        protectedPrefs.getString("current_layout_landscape", null));
    assertTrue("clean mode runtime flag",
        protectedPrefs.getBoolean("clean_mode", false));
    assertEquals("runtime function/layout position", 7,
        protectedPrefs.getInt("layout_switcher_position", 0));
    assertFalse("stale protected runtime key removed",
        protectedPrefs.contains("stale"));
  }

  @Test
  public void export_import_restores_snippet_slots_with_labels_and_indexes()
      throws Exception
  {
    List<SnippetSlot> originalSlots = Arrays.asList(
        SnippetSlot.of(0, "first phrase", "One"),
        SnippetSlot.of(1, "", ""),
        SnippetSlot.of(4, "emoji \uD83D\uDE00 phrase", "face"),
        SnippetSlot.of(8, "ninth slot", ""));
    SnippetStore.saveSlots(_context, originalSlots);

    JSONObject exported = SettingsBackup.exportToJson(_context, _defaultPrefs);

    SnippetStore.saveSlots(_context, Arrays.asList(
          SnippetSlot.of(0, "wrong", "Wrong"),
          SnippetSlot.of(4, "wrong", "Wrong"),
          SnippetSlot.of(8, "wrong", "Wrong")));
    SettingsBackup.importFromJson(_context, exported, _defaultPrefs);

    assertSlots(SnippetStore.loadSlots(_context),
        SnippetSlot.of(0, "first phrase", "One"),
        SnippetSlot.of(1, "", ""),
        SnippetSlot.of(2, "", ""),
        SnippetSlot.of(3, "", ""),
        SnippetSlot.of(4, "emoji \uD83D\uDE00 phrase", "face"),
        SnippetSlot.of(5, "", ""),
        SnippetSlot.of(6, "", ""),
        SnippetSlot.of(7, "", ""),
        SnippetSlot.of(8, "ninth slot", ""));
  }

  @Test
  public void settings_xml_exposes_backup_export_and_import_actions()
      throws Exception
  {
    Document document = DocumentBuilderFactory.newInstance()
      .newDocumentBuilder()
      .parse(new File("res/xml/settings.xml"));

    Element backupCategory = null;
    NodeList categories = document.getElementsByTagName("PreferenceCategory");
    for (int i = 0; i < categories.getLength(); ++i)
    {
      Element category = (Element)categories.item(i);
      if ("@string/pref_category_backup_restore".equals(
            category.getAttribute("android:title")))
      {
        backupCategory = category;
        break;
      }
    }
    assertNotNull("settings must have a Backup/Restore preference category",
        backupCategory);

    assertTrue("Backup/Restore settings must expose export before uninstall.",
        hasDirectPreferenceWithKey(backupCategory, "export_settings_backup"));
    assertTrue("Backup/Restore settings must expose import after reinstall.",
        hasDirectPreferenceWithKey(backupCategory, "import_settings_backup"));
  }

  private void clearStores()
  {
    _defaultPrefs.edit().clear().commit();
    DirectBootAwarePreferences.get_protected_prefs(_context)
      .edit().clear().commit();
    for (String name : namedStores())
      store(name).edit().clear().commit();
  }

  private SharedPreferences store(String name)
  {
    return _context.getSharedPreferences(name, Context.MODE_PRIVATE);
  }

  private static String[] namedStores()
  {
    return new String[]{"pinned_clipboards", "emoji_last_use", "dictionaries"};
  }

  private static ExpectedPrefs fillStore(SharedPreferences prefs, String prefix)
  {
    Set<String> tags = new HashSet<>(Arrays.asList(
          prefix + "-alpha", prefix + "-beta", "emoji-\uD83D\uDE00"));
    prefs.edit().clear()
      .putBoolean(prefix + ".boolean", true)
      .putInt(prefix + ".int", 42)
      .putLong(prefix + ".long", 9876543210L)
      .putFloat(prefix + ".float", 3.25f)
      .putString(prefix + ".string", "value for " + prefix + " \uD83D\uDE00")
      .putStringSet(prefix + ".stringSet", tags)
      .commit();
    return new ExpectedPrefs(prefix, tags);
  }

  private void overwriteStoresWithWrongValues()
  {
    overwriteStore(_defaultPrefs, "default");
    for (String name : namedStores())
      overwriteStore(store(name), name);
  }

  private static void overwriteStore(SharedPreferences prefs, String prefix)
  {
    prefs.edit().clear()
      .putBoolean(prefix + ".boolean", false)
      .putInt(prefix + ".int", -1)
      .putLong(prefix + ".long", -1L)
      .putFloat(prefix + ".float", -1.0f)
      .putString(prefix + ".string", "wrong")
      .putStringSet(prefix + ".stringSet", new HashSet<String>(Arrays.asList(
            "wrong")))
      .putString(prefix + ".stale", "must be removed")
      .commit();
  }

  private static void assertStore(String storeName, SharedPreferences prefs,
      ExpectedPrefs expected)
  {
    Map<String, ?> all = prefs.getAll();
    if (!"default".equals(storeName))
      assertEquals(storeName + " key count", 6, all.size());
    assertTrue(storeName + " boolean", prefs.getBoolean(
          expected.prefix + ".boolean", false));
    assertEquals(storeName + " int", 42, prefs.getInt(
          expected.prefix + ".int", 0));
    assertEquals(storeName + " long", 9876543210L, prefs.getLong(
          expected.prefix + ".long", 0L));
    assertEquals(storeName + " float", 3.25f, prefs.getFloat(
          expected.prefix + ".float", 0.0f), 0.0001f);
    assertEquals(storeName + " string", "value for " + expected.prefix + " \uD83D\uDE00",
        prefs.getString(expected.prefix + ".string", null));
    assertEquals(storeName + " string set", expected.stringSet,
        prefs.getStringSet(expected.prefix + ".stringSet", null));
    assertFalse(storeName + " stale key removed",
        prefs.contains(expected.prefix + ".stale"));
  }

  private static void assertSlots(List<SnippetSlot> actual,
      SnippetSlot... expected)
  {
    assertEquals("slot count", expected.length, actual.size());
    for (int i = 0; i < expected.length; ++i)
      assertSlot("slot offset " + i, expected[i], actual.get(i));
  }

  private static void assertSlot(String message, SnippetSlot expected,
      SnippetSlot actual)
  {
    assertEquals(message + " index", expected.getIndex(), actual.getIndex());
    assertEquals(message + " phrase", expected.getPhrase(), actual.getPhrase());
    assertEquals(message + " custom label", expected.getCustomLabel(),
        actual.getCustomLabel());
  }

  private static boolean hasDirectPreferenceWithKey(Element category, String key)
  {
    NodeList children = category.getChildNodes();
    for (int i = 0; i < children.getLength(); ++i)
    {
      Node node = children.item(i);
      if (node instanceof Element)
      {
        Element element = (Element)node;
        if ("Preference".equals(element.getTagName())
            && key.equals(element.getAttribute("android:key")))
          return true;
      }
    }
    return false;
  }

  private static final class ExpectedPrefs
  {
    final String prefix;
    final Set<String> stringSet;

    ExpectedPrefs(String prefix, Set<String> stringSet)
    {
      this.prefix = prefix;
      this.stringSet = stringSet;
    }
  }
}
