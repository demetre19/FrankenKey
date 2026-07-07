package juloo.keyboard2;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
public class ClipboardHistoryServiceTest
{
  private SharedPreferences _prefs;
  private ClipboardHistoryService _service;

  public ClipboardHistoryServiceTest() {}

  @Before
  public void setUp()
  {
    Context context = RuntimeEnvironment.getApplication();
    _prefs = context.getSharedPreferences(
        "clipboard_history_service_test", Context.MODE_PRIVATE);
    _prefs.edit().clear()
      .putBoolean("clipboard_history_enabled", true)
      .putString("clipboard_history_duration", "-1")
      .commit();
    Config.initGlobalConfig(_prefs, testResources(), false, null);
    ClipboardHistoryService._service = null;
    ClipboardHistoryService._paste_callback = null;
    _service = new ClipboardHistoryService(context);
    ClipboardHistoryService._service = _service;
    _service.clear_history();
  }

  @After
  public void tearDown()
  {
    if (_service != null)
      _service.clear_history();
    ClipboardHistoryService._service = null;
    ClipboardHistoryService._paste_callback = null;
    if (_prefs != null)
      _prefs.edit().clear().commit();
  }

  @Test
  public void retains_only_the_50_newest_non_empty_non_duplicate_clips_newest_first()
  {
    _service.add_clip("");
    for (int i = 1; i <= 55; i++)
    {
      _service.add_clip(clip(i));
      if (i == 12 || i == 40)
        _service.add_clip("");
      if (i == 30)
        _service.add_clip(clip(i));
    }

    List<String> history = _service.clear_expired_and_get_history();

    assertEquals("Clipboard history must expose exactly the 50 retained clips.",
        50, history.size());
    assertEquals("Clipboard history must return newest clips first, ignore empty/consecutive duplicate clips, and evict clips older than the newest 50.",
        clipsDescending(55, 6), history);
    assertTrue("The oldest retained value proves the limit is exactly 50, not fewer.",
        history.contains(clip(6)));
    assertFalse("Values older than the newest 50 must be evicted.",
        history.contains(clip(5)));
    assertFalse("The oldest clip must be evicted once more than 50 clips are retained.",
        history.contains(clip(1)));
    assertEquals("A consecutive duplicate clipboard value must not create a second retained row.",
        1, Collections.frequency(history, clip(30)));
  }

  private static List<String> clipsDescending(int newest, int oldest)
  {
    List<String> clips = new ArrayList<>();
    for (int i = newest; i >= oldest; i--)
      clips.add(clip(i));
    return clips;
  }

  private static String clip(int index)
  {
    return String.format("clip-%03d", index);
  }

  private static Resources testResources()
  {
    Resources base = RuntimeEnvironment.getApplication().getResources();
    return new Resources(base.getAssets(), base.getDisplayMetrics(),
        base.getConfiguration());
  }
}
