package juloo.keyboard2;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Persistent, ordered configuration for the Extra Keys shortcut bar. */
public final class ExtraKeysShortcutStore
{
  public static final String PREF_KEY = "extra_keys_shortcuts_v1";
  public static final int CTRL = 1;
  public static final int ALT = 1 << 1;
  public static final int SHIFT = 1 << 2;
  public static final int META = 1 << 3;

  public static final String ACTION_KEY = "key";
  public static final String ACTION_RETURN = "return";
  public static final String ACTION_MODIFIER = "modifier";
  public static final String ACTION_PIN = "pin";

  private static final int MAX_SHORTCUTS = 100;
  private static final String[] CUSTOM_KEY_NAMES = {
    "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m",
    "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z",
    "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
    "esc", "tab", "enter", "space", "backspace", "delete", "insert",
    "home", "end", "page_up", "page_down", "left", "up", "down", "right",
    "f1", "f2", "f3", "f4", "f5", "f6", "f7", "f8", "f9", "f10", "f11", "f12",
  };
  private static final String[] CUSTOM_KEY_LABELS = {
    "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
    "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z",
    "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
    "Esc", "Tab", "Enter", "Space", "Backspace", "Delete", "Insert",
    "Home", "End", "Page Up", "Page Down", "Arrow Left", "Arrow Up", "Arrow Down", "Arrow Right",
    "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12",
  };

  public static final class Shortcut
  {
    public final String id;
    public final String label;
    public final String action;
    public final String keyName;
    public final int modifiers;
    public final boolean enabled;

    public Shortcut(String id, String label, String action, String keyName,
        int modifiers, boolean enabled)
    {
      this.id = id;
      this.label = label;
      this.action = action;
      this.keyName = keyName;
      this.modifiers = modifiers;
      this.enabled = enabled;
    }

    public Shortcut withEnabled(boolean value)
    {
      return new Shortcut(id, label, action, keyName, modifiers, value);
    }

    public boolean isCustom()
    {
      return id.startsWith("custom:");
    }
  }

  private ExtraKeysShortcutStore() {}

  public static List<Shortcut> defaults()
  {
    List<Shortcut> shortcuts = new ArrayList<Shortcut>();
    shortcuts.add(key("esc", "Esc", "esc", 0));
    shortcuts.add(key("cmd_c", "Cmd+C", "c", META));
    shortcuts.add(key("tab", "Tab", "tab", 0));
    shortcuts.add(new Shortcut("return", "Return", ACTION_RETURN, "", 0, true));
    shortcuts.add(key("shift_tab", "Shift+Tab", "tab", SHIFT));
    shortcuts.add(key("space", "Space", "space", 0));
    shortcuts.add(key("backspace", "Backspace", "backspace", 0));
    shortcuts.add(modifier("ctrl", "Ctrl", CTRL));
    shortcuts.add(modifier("alt", "Alt", ALT));
    shortcuts.add(modifier("shift", "Shift", SHIFT));
    shortcuts.add(modifier("meta", "Cmd", META));
    shortcuts.add(new Shortcut("pin", "Pin", ACTION_PIN, "", 0, true));
    shortcuts.add(key("home", "Home", "home", 0));
    shortcuts.add(key("end", "End", "end", 0));
    shortcuts.add(key("insert", "Ins", "insert", 0));
    shortcuts.add(key("delete", "Del", "delete", 0));
    shortcuts.add(key("page_up", "PgUp", "page_up", 0));
    shortcuts.add(key("page_down", "PgDn", "page_down", 0));
    shortcuts.add(key("left", "←", "left", 0));
    shortcuts.add(key("up", "↑", "up", 0));
    shortcuts.add(key("down", "↓", "down", 0));
    shortcuts.add(key("right", "→", "right", 0));
    shortcuts.add(key("cmd_v", "Cmd+V", "v", META));
    shortcuts.add(key("cmd_s", "Cmd+S", "s", META));
    for (int i = 1; i <= 12; i++)
      shortcuts.add(key("f" + i, "F" + i, "f" + i, 0));
    return shortcuts;
  }

  public static List<Shortcut> load(SharedPreferences preferences)
  {
    String encoded = preferences.getString(PREF_KEY, null);
    if (encoded == null)
      return defaults();
    List<Shortcut> loaded = decode(encoded);
    if (loaded == null)
      return defaults();
    mergeMissingDefaults(loaded);
    return loaded;
  }

  public static void save(SharedPreferences preferences, List<Shortcut> shortcuts)
  {
    preferences.edit().putString(PREF_KEY, encode(shortcuts)).apply();
  }

  public static Shortcut custom(String keyName, String keyLabel, int modifiers)
  {
    keyName = keyName == null ? "" : keyName.trim().toLowerCase(Locale.ROOT);
    if (!isSupportedKeyName(keyName) || modifiers == 0 ||
        (modifiers & ~15) != 0)
      throw new IllegalArgumentException("Invalid shortcut combination");
    if (keyLabel == null || keyLabel.trim().length() == 0)
      keyLabel = displayLabelForKey(keyName);
    return new Shortcut("custom:" + UUID.randomUUID().toString(),
        combinationLabel(modifiers, keyLabel), ACTION_KEY, keyName, modifiers, true);
  }

  public static String[] customKeyNames()
  {
    return CUSTOM_KEY_NAMES.clone();
  }

  public static String[] customKeyLabels()
  {
    return CUSTOM_KEY_LABELS.clone();
  }

  public static boolean isSupportedKeyName(String keyName)
  {
    if (keyName == null || keyName.length() == 0 || keyName.length() > 40)
      return false;
    if (keyName.codePointCount(0, keyName.length()) == 1)
      return !Character.isISOControl(keyName.codePointAt(0));
    KeyValue key = KeyValue.getSpecialKeyByName(keyName);
    if (key == null)
      return false;
    switch (key.getKind())
    {
      case Char:
      case Keyevent:
      case Editing:
      case String:
        return true;
      default:
        return false;
    }
  }

  public static String displayLabelForKey(String keyName)
  {
    for (int i = 0; i < CUSTOM_KEY_NAMES.length; i++)
      if (CUSTOM_KEY_NAMES[i].equals(keyName))
        return CUSTOM_KEY_LABELS[i];
    String[] words = keyName.replace('_', ' ').split(" ");
    StringBuilder label = new StringBuilder();
    for (String word : words)
    {
      if (word.length() == 0)
        continue;
      if (label.length() > 0)
        label.append(' ');
      if (word.length() == 1)
        label.append(word.toUpperCase(Locale.ROOT));
      else
        label.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
    }
    return label.toString();
  }

  public static String combinationLabel(int modifiers, String keyLabel)
  {
    StringBuilder label = new StringBuilder();
    appendModifier(label, modifiers, CTRL, "Ctrl");
    appendModifier(label, modifiers, ALT, "Alt");
    appendModifier(label, modifiers, SHIFT, "Shift");
    appendModifier(label, modifiers, META, "Cmd");
    if (label.length() > 0)
      label.append('+');
    label.append(keyLabel);
    return label.toString();
  }

  static String encode(List<Shortcut> shortcuts)
  {
    JSONArray array = new JSONArray();
    int count = Math.min(shortcuts.size(), MAX_SHORTCUTS);
    for (int i = 0; i < count; i++)
    {
      Shortcut shortcut = shortcuts.get(i);
      JSONObject item = new JSONObject();
      try
      {
        item.put("id", shortcut.id);
        item.put("label", shortcut.label);
        item.put("action", shortcut.action);
        item.put("key", shortcut.keyName);
        item.put("modifiers", shortcut.modifiers);
        item.put("enabled", shortcut.enabled);
        array.put(item);
      }
      catch (JSONException _e) {}
    }
    return array.toString();
  }

  static List<Shortcut> decode(String encoded)
  {
    try
    {
      JSONArray array = new JSONArray(encoded);
      if (array.length() > MAX_SHORTCUTS)
        return null;
      List<Shortcut> shortcuts = new ArrayList<Shortcut>();
      Set<String> ids = new HashSet<String>();
      for (int i = 0; i < array.length(); i++)
      {
        JSONObject item = array.getJSONObject(i);
        Shortcut shortcut = new Shortcut(
            item.getString("id"), item.getString("label"),
            item.getString("action"), item.optString("key", ""),
            item.optInt("modifiers", 0), item.optBoolean("enabled", true));
        if (!isValid(shortcut) || !ids.add(shortcut.id))
          return null;
        shortcuts.add(shortcut);
      }
      return shortcuts;
    }
    catch (JSONException _e)
    {
      return null;
    }
  }

  private static Shortcut key(String id, String label, String keyName, int modifiers)
  {
    return new Shortcut(id, label, ACTION_KEY, keyName, modifiers, true);
  }

  private static Shortcut modifier(String id, String label, int modifier)
  {
    return new Shortcut(id, label, ACTION_MODIFIER, "", modifier, true);
  }

  private static void mergeMissingDefaults(List<Shortcut> shortcuts)
  {
    Set<String> ids = new HashSet<String>();
    for (Shortcut shortcut : shortcuts)
      ids.add(shortcut.id);
    for (Shortcut shortcut : defaults())
      if (!ids.contains(shortcut.id))
        shortcuts.add(shortcut);
  }

  private static boolean isValid(Shortcut shortcut)
  {
    if (shortcut.id.length() == 0 || shortcut.id.length() > 80 ||
        shortcut.label.length() == 0 || shortcut.label.length() > 40 ||
        (shortcut.modifiers & ~15) != 0)
      return false;
    if (ACTION_KEY.equals(shortcut.action))
      return isSupportedKeyName(shortcut.keyName);
    if (ACTION_RETURN.equals(shortcut.action) || ACTION_PIN.equals(shortcut.action))
      return shortcut.keyName.length() == 0 && shortcut.modifiers == 0;
    if (ACTION_MODIFIER.equals(shortcut.action))
      return shortcut.keyName.length() == 0 && Integer.bitCount(shortcut.modifiers) == 1;
    return false;
  }


  private static void appendModifier(StringBuilder label, int modifiers,
      int bit, String name)
  {
    if ((modifiers & bit) == 0)
      return;
    if (label.length() > 0)
      label.append('+');
    label.append(name);
  }
}
