package juloo.keyboard2.suggestions.email;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import juloo.keyboard2.SettingsBackup;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.annotation.Config;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class EmailMemoryStoreTest
{
  private SharedPreferences prefs()
  {
    return RuntimeEnvironment.getApplication()
      .getSharedPreferences(EmailMemoryStore.PREF_NAME, Context.MODE_PRIVATE);
  }

  private EmailMemoryStore store()
  {
    final SharedPreferences prefs = prefs();
    return new EmailMemoryStore(new EmailMemoryStore.PrefsSource()
    {
      public SharedPreferences get() { return prefs; }
    }, new Handler(Looper.getMainLooper()));
  }

  private static long dayMs(int day)
  {
    return day * EmailMemoryStore.DAY_MS;
  }

  @Test
  public void record_use_round_trips_through_json_v1()
  {
    EmailMemoryStore store = store();
    store.record_use("a@x.io", 2, EmailMemoryStore.SOURCE_FIELD, dayMs(10));
    store.flush();

    String json = prefs().getString(EmailMemoryStore.KEY_ENTRIES, "");
    assertTrue("payload must carry the v:1 format marker",
        json.contains("\"v\":1"));

    assertEquals("a@x.io", store.entries(dayMs(10)).get(0).address);
    EmailMemoryStore.Entry e = store.entries(dayMs(10)).get(0);
    assertEquals(2, e.count);
    assertEquals(10, e.firstSeenDay);
    assertEquals(10, e.lastUsedDay);
    assertEquals(EmailMemoryStore.SOURCE_FIELD, e.sources);
  }

  @Test
  public void entries_rank_by_frecency()
  {
    EmailMemoryStore store = store();
    // old but frequent beats recent but rare
    store.record_use("frequent@x.io", 8, EmailMemoryStore.SOURCE_PROSE,
        dayMs(1000));
    store.record_use("rare@x.io", 1, EmailMemoryStore.SOURCE_PROSE,
        dayMs(1090));
    List<EmailMemoryStore.Entry> ranked = store.entries(dayMs(1100));
    assertEquals("frequent@x.io", ranked.get(0).address);
    assertEquals("rare@x.io", ranked.get(1).address);
  }

  @Test
  public void ties_break_by_later_last_used_then_alphabetical()
  {
    EmailMemoryStore store = store();
    store.record_use("z@x.io", 1, EmailMemoryStore.SOURCE_PROSE, dayMs(5));
    store.record_use("a@x.io", 1, EmailMemoryStore.SOURCE_PROSE, dayMs(5));
    store.record_use("m@x.io", 1, EmailMemoryStore.SOURCE_PROSE, dayMs(9));
    List<EmailMemoryStore.Entry> ranked = store.entries(dayMs(10));
    assertEquals("m@x.io", ranked.get(0).address);
    assertEquals("a@x.io", ranked.get(1).address);
    assertEquals("z@x.io", ranked.get(2).address);
  }

  @Test
  public void entry_201_evicts_lowest_frecency_oldest_last_used()
  {
    EmailMemoryStore store = store();
    for (int i = 0; i < 200; i++)
      store.record_use(String.format("old%03d@x.io", i), 1,
          EmailMemoryStore.SOURCE_PROSE, dayMs(1000 + i));
    /* Equal counts: old000 (oldest lastUsedDay) must be evicted first. */
    store.record_use("new@x.io", 1, EmailMemoryStore.SOURCE_PROSE,
        dayMs(1200));
    List<EmailMemoryStore.Entry> kept = store.entries(dayMs(1200));
    assertEquals(200, kept.size());
    for (EmailMemoryStore.Entry e : kept)
      assertNotEquals(
          "the oldest, lowest-frecency entry must be evicted",
          "old000@x.io", e.address);
    assertEquals("new@x.io", kept.get(0).address);
  }

  @Test
  public void corrupt_json_loads_empty_and_is_rewritten_on_save()
  {
    prefs().edit().putString(EmailMemoryStore.KEY_ENTRIES, "{not json").apply();
    EmailMemoryStore store = store();
    assertTrue(store.entries(dayMs(1)).isEmpty());

    store.record_use("a@x.io", 1, EmailMemoryStore.SOURCE_PROSE, dayMs(1));
    store.flush();
    String json = prefs().getString(EmailMemoryStore.KEY_ENTRIES, "");
    assertTrue(json.contains("\"v\":1"));
    assertTrue(json.contains("a@x.io"));
  }

  @Test
  public void saves_are_debounced_two_seconds()
  {
    EmailMemoryStore store = store();
    store.record_use("a@x.io", 1, EmailMemoryStore.SOURCE_PROSE, dayMs(1));
    store.record_use("b@x.io", 1, EmailMemoryStore.SOURCE_PROSE, dayMs(1));
    assertNull("apply() must be debounced, not immediate",
        prefs().getString(EmailMemoryStore.KEY_ENTRIES, null));

    ShadowLooper.idleMainLooper(
        EmailMemoryStore.SAVE_DEBOUNCE_MS - 1, TimeUnit.MILLISECONDS);
    assertNull(prefs().getString(EmailMemoryStore.KEY_ENTRIES, null));

    ShadowLooper.idleMainLooper(1, TimeUnit.MILLISECONDS);
    String json = prefs().getString(EmailMemoryStore.KEY_ENTRIES, "");
    assertTrue(json.contains("a@x.io"));
    assertTrue(json.contains("b@x.io"));
  }

  @Test
  public void unavailable_before_unlock_never_throws_and_recovers()
  {
    final SharedPreferences prefs = prefs();
    final boolean[] unlocked = new boolean[]{false};
    /* A null PrefsSource models the production wrapper in create(), which
       converts a locked credential-protected read into "unavailable" and
       retries on every call. */
    EmailMemoryStore safe = new EmailMemoryStore(new EmailMemoryStore.PrefsSource()
    {
      public SharedPreferences get()
      {
        return unlocked[0] ? prefs : null;
      }
    }, new Handler(Looper.getMainLooper()));

    assertFalse(safe.is_available());
    assertTrue(safe.entries(dayMs(1)).isEmpty());
    safe.record_use("a@x.io", 1, EmailMemoryStore.SOURCE_PROSE, dayMs(1));
    safe.flush();
    assertNull(prefs.getString(EmailMemoryStore.KEY_ENTRIES, null));

    unlocked[0] = true;
    assertTrue(safe.is_available());
    safe.record_use("a@x.io", 1, EmailMemoryStore.SOURCE_PROSE, dayMs(1));
    safe.flush();
    assertEquals("a@x.io", safe.entries(dayMs(1)).get(0).address);
  }

  @Test
  public void remove_and_clear_all()
  {
    EmailMemoryStore store = store();
    store.record_use("a@x.io", 1, EmailMemoryStore.SOURCE_PROSE, dayMs(1));
    store.record_use("b@x.io", 1, EmailMemoryStore.SOURCE_PROSE, dayMs(1));
    store.remove("a@x.io");
    List<EmailMemoryStore.Entry> left = store.entries(dayMs(1));
    assertEquals(1, left.size());
    assertEquals("b@x.io", left.get(0).address);
    store.clear_all();
    assertTrue(store.entries(dayMs(1)).isEmpty());
    store.flush();
  }

  @Test
  public void email_memory_is_not_exported_by_settings_backup() throws Exception
  {
    java.lang.reflect.Field f =
      SettingsBackup.class.getDeclaredField("NAMED_PREFS");
    f.setAccessible(true);
    for (String name : (String[])f.get(null))
      assertNotEquals("email_memory must never be exported by SettingsBackup",
          EmailMemoryStore.PREF_NAME, name);
  }
}
