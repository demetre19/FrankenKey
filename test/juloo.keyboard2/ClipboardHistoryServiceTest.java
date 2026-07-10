package juloo.keyboard2;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
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
      .putBoolean("clipboard_save_screenshots", true)
      .putString("clipboard_history_duration", "-1")
      .commit();
    installTestConfig();
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

  @Test
  public void image_clipdata_is_saved_as_image_entry_and_pasted_through_image_callback()
  {
    Context context = RuntimeEnvironment.getApplication();
    RecordingPasteCallback callback = new RecordingPasteCallback();
    ClipboardHistoryService.on_startup(context, callback);
    ClipboardManager clipboard = (ClipboardManager)context.getSystemService(
        Context.CLIPBOARD_SERVICE);
    Uri uri = Uri.parse("content://frankenkey.test/screenshot/42");
    clipboard.setPrimaryClip(new ClipData("Samsung screenshot",
        new String[]{"image/png"}, new ClipData.Item(uri)));
    _service.clear_history();

    _service.add_current_clip();

    List<ClipboardHistoryService.ClipboardEntry> entries =
      _service.clear_expired_and_get_entries();
    assertEquals("A screenshot ClipData item must create one image history entry.",
        1, entries.size());
    ClipboardHistoryService.ClipboardEntry entry = entries.get(0);
    assertTrue("Screenshot ClipData must be retained as an image entry, not coerced to URI text.",
        entry.isImage());
    assertEquals(uri.toString(), entry.uri);
    assertEquals("image/png", entry.mimeType);
    assertEquals("Samsung screenshot", entry.displayText());
    assertTrue("Text-only history callers must not receive image entries as fake text clips.",
        _service.clear_expired_and_get_history().isEmpty());

    assertTrue("Pasting the current screenshot clipboard must route through the image callback.",
        ClipboardHistoryService.paste_current_clip());
    assertEquals(uri.toString(), callback.lastImageUri);
    assertEquals("image/png", callback.lastImageMimeType);
    assertEquals("Samsung screenshot", callback.lastImageDescription);
    assertNull("Image paste must not also emit a text paste callback.",
        callback.lastPasted);
  }

  @Test
  public void startup_screenshot_history_entry_does_not_require_published_service()
  {
    ClipboardHistoryService._service = null;

    ClipboardHistoryService.HistoryEntry entry =
      new ClipboardHistoryService.HistoryEntry(
          ClipboardHistoryService.ClipboardEntry.text("startup screenshot"));

    assertEquals("Startup screenshot ingestion can happen inside the service constructor before the static service field is published; expiry must read Config directly instead of dereferencing the unpublished service.",
        "startup screenshot", entry.content.text);
  }

  @Test
  public void screenshot_media_store_filter_accepts_screenshot_paths_only()
  {
    assertTrue("Samsung-style screenshot display names must be accepted.",
        ClipboardHistoryService.is_screenshot_candidate(
          "Screenshot_20260708_101010.png", "Pictures/Screenshots/"));
    assertTrue("Spaced screen-shot names must be accepted.",
        ClipboardHistoryService.is_screenshot_candidate(
          "Screen shot 2026-07-08.png", "Pictures/"));
    assertFalse("Normal gallery images must not be added to clipboard history as screenshots.",
        ClipboardHistoryService.is_screenshot_candidate(
          "IMG_20260708_101010.jpg", "DCIM/Camera/"));
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

  private static final class RecordingPasteCallback
      implements ClipboardHistoryService.ClipboardPasteCallback
  {
    String lastPasted = null;
    String lastImageUri = null;
    String lastImageMimeType = null;
    String lastImageDescription = null;

    @Override
    public void paste_from_clipboard_pane(String content)
    {
      lastPasted = content;
    }

    @Override
    public void paste_image_from_clipboard_pane(String uri, String mimeType,
        String description)
    {
      lastImageUri = uri;
      lastImageMimeType = mimeType;
      lastImageDescription = description;
    }
  }

  private void installTestConfig()
  {
    try
    {
      java.lang.reflect.Constructor<Config> ctor =
        Config.class.getDeclaredConstructor(SharedPreferences.class,
            Resources.class, Boolean.class,
            juloo.keyboard2.dict.Dictionaries.class);
      ctor.setAccessible(true);
      Config config = ctor.newInstance(_prefs, testResources(), Boolean.FALSE,
          null);
      java.lang.reflect.Field globalConfig =
        Config.class.getDeclaredField("_globalConfig");
      globalConfig.setAccessible(true);
      globalConfig.set(null, config);
    }
    catch (Exception e)
    {
      throw new AssertionError("Clipboard history tests need Config preferences but not keyboard layout XML initialization.", e);
    }
  }

  private static Resources testResources()
  {
    Resources base = RuntimeEnvironment.getApplication().getResources();
    return new TestResources(base);
  }

  private static final class TestResources extends Resources
  {
    TestResources(Resources base)
    {
      super(base.getAssets(), base.getDisplayMetrics(),
          base.getConfiguration());
    }

    @Override
    public float getDimension(int id)
    {
      return 1f;
    }
  }
}
