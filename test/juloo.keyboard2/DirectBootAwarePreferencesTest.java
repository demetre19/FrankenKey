package juloo.keyboard2;

import android.content.SharedPreferences;
import juloo.keyboard2.snippets.SnippetStore;
import juloo.keyboard2.suggestions.PersonalizationStore;
import org.junit.Test;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.*;

public class DirectBootAwarePreferencesTest
{
  public DirectBootAwarePreferencesTest() {}

  @Test
  public void copy_shared_preferences_does_not_copy_or_keep_private_typing_data()
  {
    FakeSharedPreferences src = new FakeSharedPreferences()
      .put("bool_setting", true)
      .put("float_setting", 1.25f)
      .put("int_setting", 7)
      .put("long_setting", 42L)
      .put("string_setting", "layout-name")
      .put("set_setting", setOf("emoji", "latin"))
      .put(SnippetStore.PREF_SLOTS,
          "[{\"index\":0,\"phrase\":\"door code 1234\"}]")
      .put(PersonalizationStore.PREF_WORDS, setOf("cazoo:3"))
      .put(PersonalizationStore.PREF_BIGRAMS, setOf("good morning:2"))
      .put(PersonalizationStore.PREF_CORRECTIONS, setOf("cazoo\tcasino\t4"));
    FakeSharedPreferences dst = new FakeSharedPreferences()
      .put(SnippetStore.PREF_SLOTS,
          "[{\"index\":0,\"phrase\":\"old secret\"}]")
      .put(PersonalizationStore.PREF_WORDS, setOf("old:1"))
      .put(PersonalizationStore.PREF_BIGRAMS, setOf("old pair:1"))
      .put(PersonalizationStore.PREF_CORRECTIONS, setOf("old\todd\t2"));

    DirectBootAwarePreferences.copy_shared_preferences(src, dst);

    Map<String, ?> copied = dst.getAll();
    assertFalse("Snippet phrases must never be present in direct-boot shared preferences.",
        copied.containsKey(SnippetStore.PREF_SLOTS));
    assertFalse("Learned words must never be present in direct-boot shared preferences.",
        copied.containsKey(PersonalizationStore.PREF_WORDS));
    assertFalse("Learned next-word pairs must never be present in direct-boot shared preferences.",
        copied.containsKey(PersonalizationStore.PREF_BIGRAMS));
    assertFalse("Learned typo-correction pairs must never be present in direct-boot shared preferences.",
        copied.containsKey(PersonalizationStore.PREF_CORRECTIONS));
    assertEquals(true, copied.get("bool_setting"));
    assertEquals(1.25f, copied.get("float_setting"));
    assertEquals(7, copied.get("int_setting"));
    assertEquals(42L, copied.get("long_setting"));
    assertEquals("layout-name", copied.get("string_setting"));
    assertEquals(setOf("emoji", "latin"), copied.get("set_setting"));
  }

  private static Set<String> setOf(String... values)
  {
    Set<String> set = new HashSet<>();
    for (String value : values)
      set.add(value);
    return set;
  }

  private static final class FakeSharedPreferences implements SharedPreferences
  {
    private final Map<String, Object> values = new HashMap<>();

    FakeSharedPreferences put(String key, Object value)
    {
      values.put(key, value);
      return this;
    }

    @Override
    public Map<String, ?> getAll()
    {
      return new HashMap<>(values);
    }

    @Override
    public String getString(String key, String defValue)
    {
      Object value = values.get(key);
      return value instanceof String ? (String)value : defValue;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<String> getStringSet(String key, Set<String> defValues)
    {
      Object value = values.get(key);
      return value instanceof Set ? new HashSet<>((Set<String>)value) : defValues;
    }

    @Override
    public int getInt(String key, int defValue)
    {
      Object value = values.get(key);
      return value instanceof Integer ? (Integer)value : defValue;
    }

    @Override
    public long getLong(String key, long defValue)
    {
      Object value = values.get(key);
      return value instanceof Long ? (Long)value : defValue;
    }

    @Override
    public float getFloat(String key, float defValue)
    {
      Object value = values.get(key);
      return value instanceof Float ? (Float)value : defValue;
    }

    @Override
    public boolean getBoolean(String key, boolean defValue)
    {
      Object value = values.get(key);
      return value instanceof Boolean ? (Boolean)value : defValue;
    }

    @Override
    public boolean contains(String key)
    {
      return values.containsKey(key);
    }

    @Override
    public Editor edit()
    {
      return new FakeEditor();
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(
        OnSharedPreferenceChangeListener listener)
    {}

    @Override
    public void unregisterOnSharedPreferenceChangeListener(
        OnSharedPreferenceChangeListener listener)
    {}

    private final class FakeEditor implements SharedPreferences.Editor
    {
      private final Map<String, Object> pending = new HashMap<>();
      private final Set<String> removed = new HashSet<>();
      private boolean clear = false;

      @Override
      public Editor putString(String key, String value)
      {
        pending.put(key, value);
        removed.remove(key);
        return this;
      }

      @Override
      public Editor putStringSet(String key, Set<String> value)
      {
        pending.put(key, new HashSet<>(value));
        removed.remove(key);
        return this;
      }

      @Override
      public Editor putInt(String key, int value)
      {
        pending.put(key, value);
        removed.remove(key);
        return this;
      }

      @Override
      public Editor putLong(String key, long value)
      {
        pending.put(key, value);
        removed.remove(key);
        return this;
      }

      @Override
      public Editor putFloat(String key, float value)
      {
        pending.put(key, value);
        removed.remove(key);
        return this;
      }

      @Override
      public Editor putBoolean(String key, boolean value)
      {
        pending.put(key, value);
        removed.remove(key);
        return this;
      }

      @Override
      public Editor remove(String key)
      {
        pending.remove(key);
        removed.add(key);
        return this;
      }

      @Override
      public Editor clear()
      {
        clear = true;
        pending.clear();
        removed.clear();
        return this;
      }

      @Override
      public boolean commit()
      {
        apply();
        return true;
      }

      @Override
      public void apply()
      {
        if (clear)
          values.clear();
        for (String key : removed)
          values.remove(key);
        values.putAll(pending);
      }
    }
  }
}
