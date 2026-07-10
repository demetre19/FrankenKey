package juloo.keyboard2;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import juloo.keyboard2.snippets.SnippetStore;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class SettingsBackup
{
  static final String FORMAT = "dev.frankenkey.keyboard.settings-backup";
  static final int VERSION = 1;
  private static final String[] NAMED_PREFS = new String[]{
    "pinned_clipboards",
    "emoji_last_use",
    "dictionaries"
  };

  private SettingsBackup() {}

  public static void exportToUri(Context context, Uri uri,
      SharedPreferences defaultPrefs)
      throws IOException, JSONException
  {
    OutputStream output = context.getContentResolver().openOutputStream(uri);
    if (output == null)
      throw new IOException("Unable to open backup destination");
    try
    {
      output.write(exportToJson(context, defaultPrefs)
          .toString(2).getBytes(StandardCharsets.UTF_8));
    }
    finally
    {
      output.close();
    }
  }

  public static void importFromUri(Context context, Uri uri,
      SharedPreferences defaultPrefs)
      throws IOException, JSONException
  {
    importFromJson(context, new JSONObject(readAll(context, uri)), defaultPrefs);
  }

  static JSONObject exportToJson(Context context, SharedPreferences defaultPrefs)
      throws JSONException
  {
    JSONObject root = new JSONObject();
    root.put("format", FORMAT);
    root.put("version", VERSION);
    root.put("package", context.getPackageName());
    root.put("sharedPreferences", exportSharedPreferences(context, defaultPrefs));
    root.put("snippetSlots", SnippetStore.exportSlots(context));
    return root;
  }

  static void importFromJson(Context context, JSONObject root,
      SharedPreferences defaultPrefs)
      throws JSONException
  {
    if (!FORMAT.equals(root.optString("format", "")))
      throw new JSONException("Not a FrankenKey settings backup");
    JSONObject stores = root.getJSONObject("sharedPreferences");
    importStore(defaultPrefs, stores.optJSONObject("default"));
    Config.migrate(defaultPrefs);
    for (String name : NAMED_PREFS)
      importStore(context.getSharedPreferences(name, Context.MODE_PRIVATE),
          stores.optJSONObject(name));
    SnippetStore.importSlots(context, root.optString("snippetSlots", ""));
    if (Build.VERSION.SDK_INT >= 24)
    {
      JSONObject protectedStore = stores.optJSONObject("directBootDefault");
      if (protectedStore != null)
      {
        SharedPreferences protectedPrefs =
          DirectBootAwarePreferences.get_protected_prefs(context);
        importStore(protectedPrefs, protectedStore);
        Config.migrate(protectedPrefs);
      }
      else
        DirectBootAwarePreferences.copy_preferences_to_protected_storage(
            context, defaultPrefs);
    }
  }

  private static JSONObject exportSharedPreferences(Context context,
      SharedPreferences defaultPrefs)
      throws JSONException
  {
    JSONObject stores = new JSONObject();
    stores.put("default", exportStore(defaultPrefs));
    if (Build.VERSION.SDK_INT >= 24)
      stores.put("directBootDefault", exportStore(
            DirectBootAwarePreferences.get_protected_prefs(context)));
    for (String name : NAMED_PREFS)
      stores.put(name, exportStore(
            context.getSharedPreferences(name, Context.MODE_PRIVATE)));
    return stores;
  }

  private static JSONObject exportStore(SharedPreferences prefs)
      throws JSONException
  {
    JSONObject out = new JSONObject();
    Map<String, ?> entries = prefs.getAll();
    for (String key : entries.keySet())
    {
      JSONObject value = encodeValue(entries.get(key));
      if (value != null)
        out.put(key, value);
    }
    return out;
  }

  private static JSONObject encodeValue(Object value)
      throws JSONException
  {
    JSONObject out = new JSONObject();
    if (value instanceof Boolean)
    {
      out.put("type", "boolean");
      out.put("value", (Boolean)value);
    }
    else if (value instanceof Float)
    {
      out.put("type", "float");
      out.put("value", ((Float)value).doubleValue());
    }
    else if (value instanceof Integer)
    {
      out.put("type", "int");
      out.put("value", (Integer)value);
    }
    else if (value instanceof Long)
    {
      out.put("type", "long");
      out.put("value", (Long)value);
    }
    else if (value instanceof String)
    {
      out.put("type", "string");
      out.put("value", (String)value);
    }
    else if (value instanceof Set)
    {
      out.put("type", "stringSet");
      JSONArray arr = new JSONArray();
      for (String item : (Set<String>)value)
        arr.put(item);
      out.put("value", arr);
    }
    else
      return null;
    return out;
  }

  private static void importStore(SharedPreferences prefs, JSONObject values)
      throws JSONException
  {
    SharedPreferences.Editor editor = prefs.edit().clear();
    if (values != null)
    {
      Iterator<String> keys = values.keys();
      while (keys.hasNext())
      {
        String key = keys.next();
        applyValue(editor, key, values.getJSONObject(key));
      }
    }
    editor.commit();
  }

  private static void applyValue(SharedPreferences.Editor editor, String key,
      JSONObject encoded)
      throws JSONException
  {
    String type = encoded.getString("type");
    if ("boolean".equals(type))
      editor.putBoolean(key, encoded.getBoolean("value"));
    else if ("float".equals(type))
      editor.putFloat(key, (float)encoded.getDouble("value"));
    else if ("int".equals(type))
      editor.putInt(key, encoded.getInt("value"));
    else if ("long".equals(type))
      editor.putLong(key, encoded.getLong("value"));
    else if ("string".equals(type))
      editor.putString(key, encoded.getString("value"));
    else if ("stringSet".equals(type))
    {
      java.util.HashSet<String> set = new java.util.HashSet<String>();
      JSONArray arr = encoded.getJSONArray("value");
      for (int i = 0; i < arr.length(); ++i)
        set.add(arr.getString(i));
      editor.putStringSet(key, set);
    }
    else
      throw new JSONException("Unknown preference value type: " + type);
  }

  private static String readAll(Context context, Uri uri)
      throws IOException
  {
    InputStream input = context.getContentResolver().openInputStream(uri);
    if (input == null)
      throw new IOException("Unable to open backup file");
    try
    {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      byte[] buf = new byte[4096];
      int len;
      while ((len = input.read(buf)) >= 0)
        output.write(buf, 0, len);
      return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
    finally
    {
      input.close();
    }
  }
}
