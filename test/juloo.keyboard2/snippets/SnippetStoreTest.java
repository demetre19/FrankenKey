package juloo.keyboard2.snippets;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import static org.junit.Assert.*;

public class SnippetStoreTest
{
  public SnippetStoreTest() {}

  @Test
  public void json_roundtrip_preserves_order_and_slot_fields()
      throws Exception
  {
    List<SnippetSlot> slots = Arrays.asList(
        SnippetSlot.of(0, "alpha", "", false),
        SnippetSlot.of(1, "", "", false),
        SnippetSlot.of(2, "bravo", "B", true),
        SnippetSlot.of(3, "emoji \uD83D\uDE00", "face", false));

    List<SnippetSlot> loaded = SnippetStore.loadSlots(
        SnippetStore.saveSlots(slots), 0);

    assertSlots(loaded,
        SnippetSlot.of(0, "alpha", "", false),
        SnippetSlot.of(1, "", "", false),
        SnippetSlot.of(2, "bravo", "B", true),
        SnippetSlot.of(3, "emoji \uD83D\uDE00", "face", false));
  }

  @Test
  public void load_null_or_malformed_json_returns_visible_empty_slots()
  {
    assertEmptySlots(SnippetStore.loadSlots(null, 4), 4);
    assertEmptySlots(SnippetStore.loadSlots("not json", 4), 4);
    assertEmptySlots(SnippetStore.loadSlots("{\"slots\": true}", 4), 4);
    assertEmptySlots(SnippetStore.loadSlots("[1, 2, 3]", 4), 4);
  }

  @Test
  public void load_uses_default_minimum_slot_count_when_requested()
  {
    assertEmptySlots(SnippetStore.loadSlots(null, SnippetStore.DEFAULT_SLOT_COUNT),
        SnippetStore.DEFAULT_SLOT_COUNT);
  }

  @Test
  public void replace_slot_updates_matching_index_without_reordering()
  {
    List<SnippetSlot> replaced = SnippetStore.replaceSlot(Arrays.asList(
        SnippetSlot.of(0, "zero", "", false),
        SnippetSlot.of(1, "one", "old", false),
        SnippetSlot.of(2, "two", "", true)),
        SnippetSlot.of(1, "updated", "new", true));

    assertSlots(replaced,
        SnippetSlot.of(0, "zero", "", false),
        SnippetSlot.of(1, "updated", "new", true),
        SnippetSlot.of(2, "two", "", true));
  }

  @Test
  public void empty_slots_survive_save_load_as_visible_slots()
  {
    List<SnippetSlot> loaded = SnippetStore.loadSlots(SnippetStore.saveSlots(
        Arrays.asList(
          SnippetSlot.of(0, "", "", false),
          SnippetSlot.of(1, "filled", "", false),
          SnippetSlot.of(2, "", "", false))), 3);

    assertSlots(loaded,
        SnippetSlot.of(0, "", "", false),
        SnippetSlot.of(1, "filled", "", false),
        SnippetSlot.of(2, "", "", false));
  }

  @Test
  public void encoded_storage_is_local_slot_json_only()
      throws Exception
  {
    String encoded = SnippetStore.saveSlots(Arrays.asList(
        SnippetSlot.of(0, "short text", "label", true),
        SnippetSlot.of(1, "plain phrase", "", false)));

    JSONArray slots = new JSONArray(encoded);
    Set<String> allowedSlotFields = new HashSet<>(Arrays.asList(
        "index", "phrase", "label", "customLabel", "iconLabel"));
    Set<String> disallowedRemoteFields = new HashSet<>(Arrays.asList(
        "url", "uri", "endpoint", "host", "server", "network", "sync",
        "cloud", "account", "user", "userid", "user_id", "email",
        "token", "auth", "analytics", "permission", "file", "path"));

    for (int i = 0; i < slots.length(); ++i)
    {
      JSONObject slot = slots.getJSONObject(i);
      Iterator<String> keys = slot.keys();
      while (keys.hasNext())
      {
        String key = keys.next();
        assertTrue("unexpected storage field " + key,
            allowedSlotFields.contains(key));
        assertFalse("remote/account-style storage field " + key,
            disallowedRemoteFields.contains(key.toLowerCase(Locale.ROOT)));
      }
    }
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
    assertEquals(message + " icon label", expected.isIconLabel(),
        actual.isIconLabel());
  }

  private static void assertEmptySlots(List<SnippetSlot> slots, int expectedCount)
  {
    assertEquals("slot count", expectedCount, slots.size());
    for (int i = 0; i < expectedCount; ++i)
      assertSlot("slot offset " + i, SnippetSlot.of(i, "", "", false),
          slots.get(i));
  }
}
