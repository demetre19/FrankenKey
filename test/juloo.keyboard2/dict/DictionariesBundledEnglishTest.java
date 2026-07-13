package juloo.keyboard2.dict;

import android.content.Context;
import java.io.InputStream;
import java.nio.file.Files;
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
  public void first_run_seeds_removable_australian_and_british_dictionaries()
      throws Exception
  {
    Context context = RuntimeEnvironment.getApplication();
    context.getSharedPreferences("dictionaries", Context.MODE_PRIVATE)
      .edit().clear().commit();
    context.deleteFile("en_AU.dict");
    context.deleteFile("en_GB.dict");

    Dictionaries dictionaries = new Dictionaries(context);

    assertTrue(dictionaries.get_installed().contains("en_AU"));
    assertTrue(dictionaries.get_installed().contains("en_GB"));
    assertAssetCopiedExactly(context, "en_AU");
    assertAssetCopiedExactly(context, "en_GB");

    dictionaries.uninstall("en_AU");
    assertFalse(dictionaries.get_installed().contains("en_AU"));
    assertFalse(dictionaries.get_install_location("en_AU").exists());

    Dictionaries afterRestart = new Dictionaries(context);
    assertFalse("A user-removed bundled dictionary must stay removed.",
        afterRestart.get_installed().contains("en_AU"));
    assertTrue(afterRestart.get_installed().contains("en_GB"));
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
