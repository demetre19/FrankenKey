package juloo.keyboard2.snippets;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

  private static final String SNIPPET_STORE_SOURCE =
      "srcs/juloo.keyboard2/snippets/SnippetStore.java";

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

  @Test
  public void context_load_migrates_legacy_preferences_to_no_backup_file_then_removes_key()
      throws Exception
  {
    String source = readSource(SNIPPET_STORE_SOURCE);
    String contextLoad = methodBody(source,
        "public static List<SnippetSlot> loadSlots(Context context)");
    String contextSave = methodBody(source,
        "public static void saveSlots(Context context");
    String migration = methodBody(source,
        "private static void migrateLegacySlots(Context context)");
    String slotsFile = methodBody(source,
        "private static File slotsFile(Context context)");

    int credentialGate = contextLoad.indexOf(
        "canAccessCredentialProtectedStorage(context)");
    int lockedReturn = contextLoad.indexOf("return emptySlots", credentialGate);
    int migrationCall = contextLoad.indexOf("migrateLegacySlots(context)");
    int noBackupRead = contextLoad.indexOf("readFile(slotsFile(context))");

    assertTrue("loadSlots(Context) must keep legacy migration behind the credential-protected storage gate.",
        credentialGate >= 0 && lockedReturn > credentialGate
        && migrationCall > lockedReturn);
    assertTrue("Legacy default preferences may be consulted only before loading the no-backup snippet file.",
        migrationCall >= 0 && migrationCall < noBackupRead);

    int defaultPrefs = migration.indexOf(
        "PreferenceManager.getDefaultSharedPreferences(context)");
    int containsLegacy = migration.indexOf("prefs.contains(PREF_SLOTS)");
    int readLegacy = migration.indexOf("prefs.getString(PREF_SLOTS, null)");
    int noBackupFile = migration.indexOf("slotsFile(context)");
    int writeMigrated = migration.indexOf("writeFile(file, encoded)");
    int existingStore = migration.indexOf("file.isFile()");
    int removalGate = migration.indexOf("if (migrated)");
    int removeLegacy = migration.indexOf(".remove(PREF_SLOTS)", removalGate);
    int applyRemoval = migration.indexOf(".apply()", removeLegacy);

    assertTrue("Legacy snippets must be read from default SharedPreferences only inside the one-shot migration helper.",
        defaultPrefs >= 0 && containsLegacy > defaultPrefs
        && readLegacy > containsLegacy);
    assertEquals("Default SharedPreferences access for raw snippet phrases must stay isolated to the migration helper.",
        1,
        countOccurrences(source,
            "PreferenceManager.getDefaultSharedPreferences(context)"));
    assertOrdered("Migrated phrases must be written through the no-backup snippet file path before legacy removal.",
        noBackupFile, writeMigrated, removalGate, removeLegacy, applyRemoval);
    assertTrue("If the no-backup file already exists, legacy raw preferences still must be treated as removable.",
        existingStore >= 0 && existingStore < removalGate);
    assertTrue("The snippet storage file must live under Context.getNoBackupFilesDir().",
        slotsFile.contains("context.getNoBackupFilesDir()"));
    assertFalse("New snippet saves must not write raw phrases through backup-eligible default SharedPreferences.",
        contextSave.contains("PreferenceManager.getDefaultSharedPreferences")
        || contextSave.contains("PREF_SLOTS")
        || contextSave.contains("putString("));
  }

  private static String readSource(String path)
      throws Exception
  {
    Path sourcePath = Paths.get(path);
    assertTrue("Expected production source file: " + path,
        Files.isRegularFile(sourcePath));
    return new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
  }

  private static String methodBody(String source, String methodSignature)
  {
    int methodIndex = source.indexOf(methodSignature);
    assertTrue("Expected method in source: " + methodSignature, methodIndex >= 0);
    int openBrace = source.indexOf('{', methodIndex);
    assertTrue("Expected method body for: " + methodSignature, openBrace >= 0);

    int depth = 0;
    for (int i = openBrace; i < source.length(); i++)
    {
      char c = source.charAt(i);
      if (c == '{')
        depth++;
      else if (c == '}')
      {
        depth--;
        if (depth == 0)
          return source.substring(openBrace + 1, i);
      }
    }
    fail("Expected closing brace for: " + methodSignature);
    return "";
  }

  private static void assertOrdered(String message, int... indexes)
  {
    for (int i = 0; i < indexes.length; ++i)
      assertTrue(message + " (missing step " + i + ")", indexes[i] >= 0);
    for (int i = 1; i < indexes.length; ++i)
      assertTrue(message + " (step " + i + " out of order)",
          indexes[i] > indexes[i - 1]);
  }

  private static int countOccurrences(String source, String needle)
  {
    int count = 0;
    int index = source.indexOf(needle);
    while (index >= 0)
    {
      count++;
      index = source.indexOf(needle, index + needle.length());
    }
    return count;
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
