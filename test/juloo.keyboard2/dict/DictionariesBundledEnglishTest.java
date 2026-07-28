package juloo.keyboard2.dict;

import android.content.Context;
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
