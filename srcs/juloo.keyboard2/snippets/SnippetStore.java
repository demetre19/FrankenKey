package juloo.keyboard2.snippets;

import android.content.SharedPreferences;
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

  private SnippetStore() {}

  public static boolean isEnabled(SharedPreferences prefs)
  {
    return prefs.getBoolean(PREF_ENABLED, true);
  }

  public static void setEnabled(SharedPreferences.Editor editor, boolean enabled)
  {
    editor.putBoolean(PREF_ENABLED, enabled);
  }

  public static List<SnippetSlot> loadSlots(SharedPreferences prefs)
  {
    return loadSlots(prefs.getString(PREF_SLOTS, null), DEFAULT_SLOT_COUNT);
  }

  public static void saveSlots(SharedPreferences.Editor editor,
      List<SnippetSlot> slots)
  {
    editor.putString(PREF_SLOTS, saveSlots(slots));
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
