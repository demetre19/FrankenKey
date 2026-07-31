package juloo.keyboard2.dict;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import juloo.keyboard2.Utils;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class DictionariesBundledEnglishTest
{
  @Test
  public void first_run_seeds_removable_australian_british_and_us_dictionaries()
      throws Exception
  {
    Context context = RuntimeEnvironment.getApplication();
    clearEnglishState(context);

    Dictionaries dictionaries = new Dictionaries(context);

    for (String locale : Dictionaries.BUNDLED_ENGLISH_DICTIONARIES)
    {
      assertTrue(locale + " must be installed on first run.",
          dictionaries.get_installed().contains(locale));
      assertAssetCopiedExactly(context, locale);
    }

    dictionaries.uninstall("en_AU");
    dictionaries.uninstall("en_US");
    Dictionaries afterRestart = new Dictionaries(context);

    assertFalse("A user-removed Australian dictionary must stay removed.",
        afterRestart.get_installed().contains("en_AU"));
    assertFalse("A user-removed US dictionary must stay removed.",
        afterRestart.get_installed().contains("en_US"));
    assertTrue(afterRestart.get_installed().contains("en_GB"));
  }

  @Test
  public void sequential_installs_persist_a_snapshot_for_process_restart()
  {
    Context context = RuntimeEnvironment.getApplication();
    clearEnglishState(context);

    new Dictionaries(context);

    Set<String> persisted = context.getSharedPreferences(
        "dictionaries", Context.MODE_PRIVATE).getStringSet(
        Dictionaries.PREF_INSTALLED_DICTS, new HashSet<String>());
    assertEquals("Every install must persist a defensive snapshot; mutating the live in-memory set for the next dictionary must not silently alter SharedPreferences.",
        new HashSet<String>(Arrays.asList(
            Dictionaries.BUNDLED_ENGLISH_DICTIONARIES)), persisted);

    Dictionaries afterProcessRestart = new Dictionaries(context);
    assertEquals("A fresh process must restore the complete installed dictionary set.",
        persisted, afterProcessRestart.get_installed());
  }

  @Test
  public void locked_startup_recovers_preferences_after_unlock()
  {
    Context context = RuntimeEnvironment.getApplication();
    clearEnglishState(context);
    Dictionaries dictionaries = new Dictionaries(context);
    Set<String> expected = new HashSet<String>(
        dictionaries.get_installed());

    dictionaries._shared_prefs = null;
    dictionaries._installed_dictionaries.clear();

    assertEquals("A singleton created while credential storage is unavailable must retry its preferences after unlock instead of remaining empty for the process lifetime.",
        expected, dictionaries.get_installed());
  }

  @Test
  public void locked_load_does_not_cache_a_false_missing_dictionary()
  {
    Context base = RuntimeEnvironment.getApplication();
    clearEnglishState(base);
    LockedThenUnlockedContext context =
      new LockedThenUnlockedContext(base);
    Dictionaries dictionaries = new Dictionaries(context);

    assertNull("Credential-protected state is unavailable before unlock.",
        dictionaries.load("en_AU"));
    assertFalse("A locked-state miss must not be cached as an installed-state result.",
        dictionaries._loaded_dictionaries.containsKey("en_AU"));

    context.unlock();
    assertTrue("The same singleton must reload preferences after unlock.",
        dictionaries.get_installed().contains("en_AU"));
    assertTrue("Reloading after unlock must restore the bundled private file.",
        dictionaries.ensure_bundled_dictionary_file("en_AU"));
    assertFalse("The pre-unlock miss must remain absent from the load cache.",
        dictionaries._loaded_dictionaries.containsKey("en_AU"));
  }

  @Test
  public void existing_au_gb_install_receives_us_dictionary_once()
      throws Exception
  {
    Context context = RuntimeEnvironment.getApplication();
    clearEnglishState(context);
    Set<String> installed = new HashSet<String>(
        Arrays.asList("en_AU", "en_GB"));
    context.getSharedPreferences("dictionaries", Context.MODE_PRIVATE).edit()
      .putStringSet(Dictionaries.PREF_INSTALLED_DICTS, installed)
      .putBoolean(Dictionaries.PREF_BUNDLED_ENGLISH_SEEDED, true)
      .commit();

    Dictionaries dictionaries = new Dictionaries(context);

    assertTrue("Existing installs must receive the newly bundled US dictionary.",
        dictionaries.get_installed().contains("en_US"));
    assertAssetCopiedExactly(context, "en_US");
  }

  @Test
  public void installed_bundled_dictionary_repairs_a_missing_private_file()
      throws Exception
  {
    Context context = RuntimeEnvironment.getApplication();
    clearEnglishState(context);
    Dictionaries dictionaries = new Dictionaries(context);
    assertTrue(context.deleteFile("en_AU.dict"));

    Dictionaries afterRestart = new Dictionaries(context);

    assertTrue("A dictionary still marked installed must repair its missing bundled file instead of showing a false install banner.",
        afterRestart.ensure_bundled_dictionary_file("en_AU"));
    assertAssetCopiedExactly(context, "en_AU");
  }

  @Test
  public void dictionary_manager_always_lists_all_three_english_options()
  {
    Context context = RuntimeEnvironment.getApplication();
    clearEnglishState(context);
    DictionaryListView view = new DictionaryListView(context, null);
    Set<String> names = new HashSet<String>();
    for (DictionaryListView.DictView dictionary : view._dict_views)
      names.add(dictionary.dict_name);

    assertTrue(names.contains("en_AU"));
    assertTrue("English (United Kingdom) must remain selectable.",
        names.contains("en_GB"));
    assertTrue("English (United States) must be selectable.",
        names.contains("en_US"));

    view._dictionaries.uninstall("en_US");
    assertTrue("Bundled US English must reinstall locally without an Internet download.",
        view.install_dictionary("en_US"));
    try
    {
      assertAssetCopiedExactly(context, "en_US");
    }
    catch (Exception e)
    {
      throw new AssertionError(e);
    }
  }

  private static final class LockedThenUnlockedContext
      extends ContextWrapper
  {
    private boolean _unlocked;

    LockedThenUnlockedContext(Context base)
    {
      super(base);
    }

    void unlock()
    {
      _unlocked = true;
    }

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode)
    {
      if (!_unlocked)
        throw new IllegalStateException("credential storage locked");
      return super.getSharedPreferences(name, mode);
    }
  }

  private static void clearEnglishState(Context context)
  {
    context.getSharedPreferences("dictionaries", Context.MODE_PRIVATE)
      .edit().clear().commit();
    for (String locale : Dictionaries.BUNDLED_ENGLISH_DICTIONARIES)
      context.deleteFile(locale + ".dict");
    Dictionaries._instance = null;
  }

  private static void assertAssetCopiedExactly(Context context, String locale)
      throws Exception
  {
    InputStream input = context.getAssets().open("dictionaries/" + locale
        + ".dict");
    byte[] asset;
    try
    {
      asset = Utils.read_all_bytes(input);
    }
    finally
    {
      input.close();
    }

    byte[] installed = Files.readAllBytes(
        context.getFileStreamPath(locale + ".dict").toPath());
    assertArrayEquals(locale
        + " must be seeded byte-for-byte from its bundled Cdict.",
        asset, installed);
  }
}
