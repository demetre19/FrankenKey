package juloo.keyboard2.dict;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.InputStream;
import juloo.cdict.Cdict;
import juloo.keyboard2.Utils;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class BundledEnglishDictionaryInstrumentedTest
{
  @Test
  public void every_bundled_english_cdict_loads_through_android_jni()
      throws Exception
  {
    Context context = InstrumentationRegistry.getInstrumentation()
      .getTargetContext();
    for (String locale : Dictionaries.BUNDLED_ENGLISH_DICTIONARIES)
    {
      InputStream input = context.getAssets().open(
          "dictionaries/" + locale + ".dict");
      byte[] bytes;
      try
      {
        bytes = Utils.read_all_bytes(input);
      }
      finally
      {
        input.close();
      }
      Cdict main = Dictionaries.find_by_name(Cdict.of_bytes(bytes), "main");
      assertNotNull(locale + " must expose a native-loadable main Cdict.", main);
    }
  }
}
