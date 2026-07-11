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
        SnippetSlot.of(0, "alpha", ""),
        SnippetSlot.of(1, "", ""),
        SnippetSlot.of(2, "bravo", "B", "briefcase"),
        SnippetSlot.of(3, "emoji \uD83D\uDE00", "face", "heart"));

    List<SnippetSlot> loaded = SnippetStore.loadSlots(
        SnippetStore.saveSlots(slots), 0);

    assertSlots(loaded,
        SnippetSlot.of(0, "alpha", ""),
        SnippetSlot.of(1, "", ""),
        SnippetSlot.of(2, "bravo", "B", "briefcase"),
        SnippetSlot.of(3, "emoji \uD83D\uDE00", "face", "heart"));
  }

  @Test
  public void json_roundtrip_preserves_spaces_and_emoji_without_percent_encoding()
      throws Exception
  {
    String phrase = "Hello there 😀 — normal text";
    String encoded = SnippetStore.saveSlots(Arrays.asList(
        SnippetSlot.of(0, phrase, "👋")));

    assertFalse("Snippet storage must not URL-encode spaces as %20.",
        encoded.contains("%20"));
    assertSlots(SnippetStore.loadSlots(encoded, 1),
        SnippetSlot.of(0, phrase, "👋"));
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
  public void load_accepts_legacy_icon_label_field_without_losing_snippet_data()
  {
    String legacy = "[{\"index\":0,\"phrase\":\"kept phrase\","
      + "\"label\":\"★\",\"iconLabel\":true}]";

    assertSlots(SnippetStore.loadSlots(legacy, 1),
        SnippetSlot.of(0, "kept phrase", "★"));
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
        SnippetSlot.of(0, "zero", ""),
        SnippetSlot.of(1, "one", "old"),
        SnippetSlot.of(2, "two", "")),
        SnippetSlot.of(1, "updated", "new"));

    assertSlots(replaced,
        SnippetSlot.of(0, "zero", ""),
        SnippetSlot.of(1, "updated", "new"),
        SnippetSlot.of(2, "two", ""));
  }

  @Test
  public void empty_slots_survive_save_load_as_visible_slots()
  {
    List<SnippetSlot> loaded = SnippetStore.loadSlots(SnippetStore.saveSlots(
        Arrays.asList(
          SnippetSlot.of(0, "", ""),
          SnippetSlot.of(1, "filled", ""),
          SnippetSlot.of(2, "", ""))), 3);

    assertSlots(loaded,
        SnippetSlot.of(0, "", ""),
        SnippetSlot.of(1, "filled", ""),
        SnippetSlot.of(2, "", ""));
  }

  @Test
  public void encoded_storage_is_local_slot_json_only()
      throws Exception
  {
    String encoded = SnippetStore.saveSlots(Arrays.asList(
        SnippetSlot.of(0, "short text", "label"),
        SnippetSlot.of(1, "plain phrase", "")));

    JSONArray slots = new JSONArray(encoded);
    Set<String> allowedSlotFields = new HashSet<>(Arrays.asList(
        "index", "phrase", "label", "customLabel", "icon"));
    Set<String> disallowedRemoteFields = new HashSet<>(Arrays.asList(
        "url", "uri", "endpoint", "host", "server", "network", "sync",
        "cloud", "account", "user", "userid", "user_id", "email",
        "token", "auth", "analytics", "permission", "file", "path"));
    assertFalse("New snippet data must not retain the removed no-op icon-label field.",
        encoded.contains("\"iconLabel\""));

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
  public void locked_context_load_uses_device_protected_slots_file()
      throws Exception
  {
    String source = readSource(SNIPPET_STORE_SOURCE);
    String contextLoad = methodBody(source,
        "public static List<SnippetSlot> loadSlots(Context context)");
    String loadDirectBoot = methodBody(source,
        "private static List<SnippetSlot> loadDirectBootSlots(Context context)");
    String directBootSlotsFile = methodBody(source,
        "private static File directBootSlotsFile(Context context)");

    int credentialGate = contextLoad.indexOf(
        "canAccessCredentialProtectedStorage(context)");
    int directBootReturn = contextLoad.indexOf(
        "return loadDirectBootSlots(context)", credentialGate);
    int noBackupMigrationCall =
      contextLoad.indexOf("migrateNoBackupSlots(context)");
    int lockedEmptyReturn = contextLoad.indexOf("return emptySlots",
        credentialGate);

    assertOrdered("When the user is still locked, loadSlots(Context) must route to device-protected snippets before touching credential-protected migrations.",
        credentialGate, directBootReturn, noBackupMigrationCall);
    assertTrue("Locked load must not short-circuit to empty slots before it has tried the direct-boot snippet mirror.",
        lockedEmptyReturn < 0 || lockedEmptyReturn > noBackupMigrationCall);
    assertTrue("The direct-boot load helper must read the mirrored snippet slot JSON from its own file path.",
        loadDirectBoot.contains("readFile(directBootSlotsFile(context))")
        && loadDirectBoot.contains("loadSlots(readFile(directBootSlotsFile(context))"));
    assertTrue("Missing mirrored direct-boot snippets may fall back to visible empty slots, but only inside the direct-boot helper.",
        loadDirectBoot.contains("catch (IOException _e)")
        && loadDirectBoot.contains("return emptySlots(DEFAULT_SLOT_COUNT)"));
    assertTrue("Direct-boot snippets must live in device-protected storage so the keyboard can read them before first unlock.",
        directBootSlotsFile.contains(
          "context.createDeviceProtectedStorageContext().getFilesDir()"));
  }

  @Test
  public void unlocked_context_save_and_load_mirror_encoded_slots_to_direct_boot()
      throws Exception
  {
    String source = readSource(SNIPPET_STORE_SOURCE);
    String contextLoad = methodBody(source,
        "public static List<SnippetSlot> loadSlots(Context context)");
    String contextSave = methodBody(source,
        "public static void saveSlots(Context context");
    String mirrorSlots = methodBody(source,
        "private static void mirrorSlotsToDirectBoot(Context context");
    String noBackupMigration = methodBody(source,
        "private static void migrateNoBackupSlots(Context context)");
    String legacyMigration = methodBody(source,
        "private static void migrateLegacySlots(Context context)");
    String slotsFile = methodBody(source,
        "private static File slotsFile(Context context)");
    String legacyNoBackupSlotsFile = methodBody(source,
        "private static File legacyNoBackupSlotsFile(Context context)");

    int noBackupMigrationCall =
      contextLoad.indexOf("migrateNoBackupSlots(context)");
    int legacyMigrationCall = contextLoad.indexOf("migrateLegacySlots(context)");
    int backedUpRead = contextLoad.indexOf(
        "String encoded = readFile(slotsFile(context))");
    int mirrorAfterRead =
      contextLoad.indexOf("mirrorSlotsToDirectBoot(context, encoded)", backedUpRead);
    int loadEncoded = contextLoad.indexOf("return loadSlots(encoded",
        mirrorAfterRead);
    assertOrdered("Unlocked loads must migrate, read the credential-protected slot JSON, mirror those exact bytes for direct boot, then decode them.",
        noBackupMigrationCall, legacyMigrationCall, backedUpRead,
        mirrorAfterRead, loadEncoded);

    int encodeSlots = contextSave.indexOf("String encoded = saveSlots(slots)");
    int writeCredentialFile =
      contextSave.indexOf("writeFile(slotsFile(context), encoded)", encodeSlots);
    int mirrorAfterSave =
      contextSave.indexOf("mirrorSlotsToDirectBoot(context, encoded)",
          writeCredentialFile);
    assertOrdered("Unlocked saves must write encoded slot JSON to the credential-protected file and mirror the same encoded bytes to direct boot.",
        encodeSlots, writeCredentialFile, mirrorAfterSave);

    assertTrue("The direct-boot mirror must write only the encoded slot JSON through the device-protected slot file.",
        mirrorSlots.contains("writeFile(directBootSlotsFile(context), encoded == null ? \"\" : encoded)"));

    int targetFile = noBackupMigration.indexOf("slotsFile(context)");
    int targetExists = noBackupMigration.indexOf("file.isFile()", targetFile);
    int sourceFile = noBackupMigration.indexOf("legacyNoBackupSlotsFile(context)");
    int sourceExists = noBackupMigration.indexOf("noBackupFile.isFile()", sourceFile);
    int copyLegacyFile = noBackupMigration.indexOf(
        "writeFile(file, readFile(noBackupFile))");
    assertOrdered("No-backup snippets must be copied only when the new backup-eligible file is missing and the old file exists.",
        targetFile, targetExists, sourceFile, sourceExists, copyLegacyFile);

    int defaultPrefs = legacyMigration.indexOf(
        "PreferenceManager.getDefaultSharedPreferences(context)");
    int containsLegacy = legacyMigration.indexOf("prefs.contains(PREF_SLOTS)");
    int readLegacy = legacyMigration.indexOf("prefs.getString(PREF_SLOTS, null)");
    int backedUpFile = legacyMigration.indexOf("slotsFile(context)");
    int writeMigrated = legacyMigration.indexOf("writeFile(file, encoded)");
    int existingStore = legacyMigration.indexOf("file.isFile()");
    int removalGate = legacyMigration.indexOf("if (migrated)");
    int removeLegacy = legacyMigration.indexOf(".remove(PREF_SLOTS)", removalGate);
    int applyRemoval = legacyMigration.indexOf(".apply()", removeLegacy);

    assertTrue("Legacy snippets must be read from default SharedPreferences only inside the one-shot migration helper.",
        defaultPrefs >= 0 && containsLegacy > defaultPrefs
        && readLegacy > containsLegacy);
    assertEquals("Default SharedPreferences access for raw snippet phrases must stay isolated to the migration helper.",
        1,
        countOccurrences(source,
            "PreferenceManager.getDefaultSharedPreferences(context)"));
    assertOrdered("Migrated phrases must be written through the backup-eligible snippet file path before legacy removal.",
        backedUpFile, writeMigrated, removalGate, removeLegacy, applyRemoval);
    assertTrue("If the new file already exists, legacy raw preferences still must be treated as removable.",
        existingStore >= 0 && existingStore < removalGate);
    assertTrue("The active snippet storage file must live under Context.getFilesDir() so Android backup can include it.",
        slotsFile.contains("context.getFilesDir()"));
    assertTrue("The old no-backup file path must remain readable only as a migration source.",
        legacyNoBackupSlotsFile.contains("context.getNoBackupFilesDir()"));
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
    assertEquals(message + " icon id", expected.getIconId(),
        actual.getIconId());
  }

  private static void assertEmptySlots(List<SnippetSlot> slots, int expectedCount)
  {
    assertEquals("slot count", expectedCount, slots.size());
    for (int i = 0; i < expectedCount; ++i)
      assertSlot("slot offset " + i, SnippetSlot.of(i, "", ""),
          slots.get(i));
  }
}
