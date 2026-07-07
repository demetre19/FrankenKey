package juloo.keyboard2.snippets;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build.VERSION;
import android.os.UserManager;
import android.preference.PreferenceManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class SnippetStore
{
  public static final String PREF_ENABLED = "frankenkey_snippets_enabled";
  public static final String PREF_SLOTS = "frankenkey_snippet_slots";
  public static final int DEFAULT_SLOT_COUNT = SnippetSlot.PAGE_SIZE;
  private static final String STORE_FILE = "frankenkey_snippets.json";

  private SnippetStore() {}

  public static boolean isEnabled(SharedPreferences prefs)
  {
    return prefs.getBoolean(PREF_ENABLED, true);
  }

  public static void setEnabled(SharedPreferences.Editor editor, boolean enabled)
  {
    editor.putBoolean(PREF_ENABLED, enabled);
  }

  public static List<SnippetSlot> loadSlots(Context context)
  {
    if (!canAccessCredentialProtectedStorage(context))
      return loadDirectBootSlots(context);
    migrateNoBackupSlots(context);
    migrateLegacySlots(context);
    try
    {
      String encoded = readFile(slotsFile(context));
      mirrorSlotsToDirectBoot(context, encoded);
      return loadSlots(encoded, DEFAULT_SLOT_COUNT);
    }
    catch (IOException _e)
    {
      return emptySlots(DEFAULT_SLOT_COUNT);
    }
  }

  public static void saveSlots(Context context, List<SnippetSlot> slots)
  {
    if (!canAccessCredentialProtectedStorage(context))
      return;
    String encoded = saveSlots(slots);
    try
    {
      writeFile(slotsFile(context), encoded);
      mirrorSlotsToDirectBoot(context, encoded);
    }
    catch (IOException _e) {}
  }

  private static List<SnippetSlot> loadDirectBootSlots(Context context)
  {
    try
    {
      return loadSlots(readFile(directBootSlotsFile(context)),
          DEFAULT_SLOT_COUNT);
    }
    catch (IOException _e)
    {
      return emptySlots(DEFAULT_SLOT_COUNT);
    }
  }

  private static void mirrorSlotsToDirectBoot(Context context, String encoded)
  {
    if (VERSION.SDK_INT < 24)
      return;
    try
    {
      writeFile(directBootSlotsFile(context), encoded == null ? "" : encoded);
    }
    catch (IOException _e) {}
  }

  private static void migrateNoBackupSlots(Context context)
  {
    File file = slotsFile(context);
    if (file.isFile())
      return;
    File noBackupFile = legacyNoBackupSlotsFile(context);
    if (!noBackupFile.isFile())
      return;
    try
    {
      writeFile(file, readFile(noBackupFile));
    }
    catch (IOException _e) {}
  }

  private static void migrateLegacySlots(Context context)
  {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    if (!prefs.contains(PREF_SLOTS))
      return;
    String encoded = prefs.getString(PREF_SLOTS, null);
    boolean migrated = false;
    try
    {
      File file = slotsFile(context);
      if (file.isFile())
        migrated = true;
      else if (encoded != null)
      {
        writeFile(file, encoded);
        migrated = true;
      }
      else
        migrated = true;
    }
    catch (IOException _e) {}
    if (migrated)
      prefs.edit().remove(PREF_SLOTS).apply();
  }

  public static List<SnippetSlot> loadSlots(String encoded, int minimumSlots)
  {
    if (encoded == null || encoded.isEmpty())
      return emptySlots(minimumSlots);
    try
    {
      JSONArray arr = new JSONArray(encoded);
      List<SnippetSlot> slots = emptySlots(Math.max(minimumSlots, arr.length()));
      for (int i = 0; i < arr.length(); ++i)
      {
        JSONObject obj = arr.getJSONObject(i);
        int index = obj.optInt("index", i);
        if (index < 0)
          continue;
        slots = replaceSlot(slots, SnippetSlot.of(index,
              obj.optString("phrase", ""),
              obj.optString("label", ""),
              obj.optBoolean("iconLabel", false)));
      }
      return withMinimumSlots(slots, minimumSlots);
    }
    catch (JSONException _e)
    {
      return emptySlots(minimumSlots);
    }
  }

  public static String saveSlots(List<SnippetSlot> slots)
  {
    JSONArray arr = new JSONArray();
    if (slots == null)
      return arr.toString();
    for (SnippetSlot slot : slots)
    {
      JSONObject obj = new JSONObject();
      try
      {
        obj.put("index", slot.getIndex());
        obj.put("phrase", slot.getPhrase());
        obj.put("label", slot.getCustomLabel());
        obj.put("iconLabel", slot.isIconLabel());
        arr.put(obj);
      }
      catch (JSONException _e) {}
    }
    return arr.toString();
  }

  public static List<SnippetSlot> withMinimumSlots(List<SnippetSlot> slots,
      int minimumSlots)
  {
    int count = Math.max(0, minimumSlots);
    if (slots != null)
      count = Math.max(count, slots.size());
    List<SnippetSlot> out = emptySlots(count);
    if (slots == null)
      return out;
    for (SnippetSlot slot : slots)
      out = replaceSlot(out, slot);
    return out;
  }

  public static List<SnippetSlot> replaceSlot(List<SnippetSlot> slots,
      SnippetSlot slot)
  {
    List<SnippetSlot> out = new ArrayList<>();
    int count = slot.getIndex() + 1;
    if (slots != null)
      count = Math.max(count, slots.size());
    for (int i = 0; i < count; ++i)
    {
      if (slots != null && i < slots.size())
        out.add(slots.get(i));
      else
        out.add(emptySlot(i));
    }
    out.set(slot.getIndex(), slot);
    return out;
  }

  private static boolean canAccessCredentialProtectedStorage(Context context)
  {
    if (VERSION.SDK_INT < 24)
      return true;
    UserManager um = (UserManager)context.getSystemService(Context.USER_SERVICE);
    return um == null || um.isUserUnlocked();
  }

  private static File slotsFile(Context context)
  {
    return new File(context.getFilesDir(), STORE_FILE);
  }

  private static File directBootSlotsFile(Context context)
  {
    return new File(context.createDeviceProtectedStorageContext().getFilesDir(),
        STORE_FILE);
  }

  private static File legacyNoBackupSlotsFile(Context context)
  {
    return new File(context.getNoBackupFilesDir(), STORE_FILE);
  }

  private static String readFile(File file)
      throws IOException
  {
    if (!file.isFile())
      return null;
    long length = file.length();
    if (length <= 0)
      return "";
    byte[] bytes = new byte[(int)length];
    try (FileInputStream input = new FileInputStream(file))
    {
      int offset = 0;
      while (offset < bytes.length)
      {
        int read = input.read(bytes, offset, bytes.length - offset);
        if (read < 0)
          break;
        offset += read;
      }
      return new String(bytes, 0, offset, StandardCharsets.UTF_8);
    }
  }

  private static void writeFile(File file, String content)
      throws IOException
  {
    File dir = file.getParentFile();
    if (dir != null)
      dir.mkdirs();
    try (FileOutputStream output = new FileOutputStream(file))
    {
      output.write(content.getBytes(StandardCharsets.UTF_8));
    }
  }

  private static List<SnippetSlot> emptySlots(int count)
  {
    List<SnippetSlot> slots = new ArrayList<>();
    for (int i = 0; i < count; ++i)
      slots.add(emptySlot(i));
    return slots;
  }

  private static SnippetSlot emptySlot(int index)
  {
    return SnippetSlot.of(index, "", "", false);
  }
}
