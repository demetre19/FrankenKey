package juloo.keyboard2.suggestions.email;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Bounded credential-protected store for remembered email addresses.
 *
 * Backed by the named SharedPreferences {@code email_memory} on the default
 * (credential-protected) storage, so it is unavailable before the first user
 * unlock. {@link #is_available()} reports that state without throwing and every
 * call retries, so the store starts working after ACTION_USER_UNLOCKED with no
 * receiver.
 *
 * All methods must run on the SharedDecoder worker thread. Writes are applied
 * through {@code apply()} debounced by {@link #SAVE_DEBOUNCE_MS}.
 */
public final class EmailMemoryStore
{
  public static final String PREF_NAME = "email_memory";
  static final String KEY_ENTRIES = "entries";
  static final int FORMAT_VERSION = 1;
  static final int MAX_ENTRIES = 200;
  static final long SAVE_DEBOUNCE_MS = 2000L;

  public static final int SOURCE_FIELD = 1;
  public static final int SOURCE_PROSE = 2;
  public static final int SOURCE_SELECTED = 4;

  /** Frecency half-life in days. */
  static final double HALF_LIFE_DAYS = 90.0;
  static final long DAY_MS = 24L * 60L * 60L * 1000L;

  public static final class Entry
  {
    public final String address;
    public final int count;
    public final int firstSeenDay;
    public final int lastUsedDay;
    /** Bitmask of SOURCE_* values. */
    public final int sources;

    Entry(String address_, int count_, int firstSeenDay_, int lastUsedDay_,
        int sources_)
    {
      address = address_;
      count = count_;
      firstSeenDay = firstSeenDay_;
      lastUsedDay = lastUsedDay_;
      sources = sources_;
    }

    double score(long day)
    {
      return count * Math.pow(0.5, (day - lastUsedDay) / HALF_LIFE_DAYS);
    }
  }

  public interface PrefsSource
  {
    /**
     * Return the credential-protected {@code email_memory} preferences, or
     * null when they can't be read yet (device locked). Must not throw.
     */
    SharedPreferences get();
  }

  private final PrefsSource _source;
  private final Handler _handler;
  private final Map<String, Entry> _entries = new HashMap<>();
  private boolean _loaded = false;
  private boolean _savePending = false;

  private final Runnable _saveTask = new Runnable()
  {
    public void run()
    {
      _savePending = false;
      save_now();
    }
  };

  /** Package-visible for tests; production uses {@link #create}. */
  EmailMemoryStore(PrefsSource source, Handler handler)
  {
    _source = source;
    _handler = handler;
  }

  /**
   * Store on the default credential-protected SharedPreferences file
   * {@code email_memory}. All calls go on the worker thread driven by
   * {@code handler}.
   */
  public static EmailMemoryStore create(final Context context, Handler handler)
  {
    final Context app = context.getApplicationContext();
    return new EmailMemoryStore(new PrefsSource()
    {
      public SharedPreferences get()
      {
        try
        {
          return app.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        }
        catch (RuntimeException e)
        {
          /* Credential-protected storage is unreadable before the first
             unlock. Report unavailable; every call retries. */
          return null;
        }
      }
    }, handler);
  }

  /** False before the first user unlock. Never throws. */
  public boolean is_available()
  {
    return _source.get() != null;
  }

  private SharedPreferences prefs()
  {
    return _source.get();
  }

  /** Lazily load the JSON payload once; corrupt payloads start empty. */
  private void ensure_loaded(SharedPreferences prefs)
  {
    if (_loaded)
      return;
    _loaded = true;
    String json = prefs.getString(KEY_ENTRIES, null);
    if (json == null)
      return;
    try
    {
      JSONObject root = new JSONObject(json);
      if (root.optInt("v") != FORMAT_VERSION)
        return;
      JSONArray arr = root.getJSONArray("addresses");
      for (int i = 0; i < arr.length(); i++)
      {
        JSONObject obj = arr.getJSONObject(i);
        Entry e = new Entry(
            obj.getString("address"),
            obj.getInt("count"),
            obj.getInt("firstSeenDay"),
            obj.getInt("lastUsedDay"),
            obj.optInt("sources"));
        _entries.put(e.address, e);
      }
    }
    catch (JSONException e)
    {
      /* Corrupt payload: start empty. It is rewritten on the next save. */
      _entries.clear();
    }
  }

  /**
   * Record one learning event for {@code address} with the given day (ms since
   * epoch / {@link #DAY_MS}) and {@code SOURCE_*} bit, adding {@code delta} to
   * its count. No-op while the store is unavailable.
   */
  public void record_use(String address, int delta, int source, long nowMs)
  {
    SharedPreferences prefs = prefs();
    if (prefs == null)
      return;
    ensure_loaded(prefs);
    int day = (int)(nowMs / DAY_MS);
    Entry prev = _entries.get(address);
    Entry next = (prev == null)
      ? new Entry(address, delta, day, day, source)
      : new Entry(address, prev.count + delta, prev.firstSeenDay, day,
          prev.sources | source);
    _entries.put(address, next);
    evict_if_needed(day);
    schedule_save();
  }

  /** Saved addresses ranked by frecency; empty while unavailable. */
  public List<Entry> entries(long nowMs)
  {
    SharedPreferences prefs = prefs();
    if (prefs == null)
      return Collections.emptyList();
    ensure_loaded(prefs);
    final long day = nowMs / DAY_MS;
    List<Entry> out = new ArrayList<>(_entries.values());
    Collections.sort(out, ranking(day));
    return out;
  }

  public void remove(String address)
  {
    SharedPreferences prefs = prefs();
    if (prefs == null)
      return;
    ensure_loaded(prefs);
    if (_entries.remove(address) != null)
      schedule_save();
  }

  public void clear_all()
  {
    SharedPreferences prefs = prefs();
    if (prefs == null)
      return;
    ensure_loaded(prefs);
    if (!_entries.isEmpty())
    {
      _entries.clear();
      schedule_save();
    }
  }

  private Comparator<Entry> ranking(final long day)
  {
    return new Comparator<Entry>()
    {
      public int compare(Entry a, Entry b)
      {
        int c = Double.compare(b.score(day), a.score(day));
        if (c != 0)
          return c;
        c = Integer.compare(b.lastUsedDay, a.lastUsedDay);
        if (c != 0)
          return c;
        return a.address.compareTo(b.address);
      }
    };
  }

  /** Drop the lowest-frecency entries (ties: oldest lastUsedDay) past the cap. */
  private void evict_if_needed(long day)
  {
    while (_entries.size() > MAX_ENTRIES)
    {
      Entry worst = null;
      for (Entry e : _entries.values())
      {
        if (worst == null
            || e.score(day) < worst.score(day)
            || (e.score(day) == worst.score(day)
                && e.lastUsedDay < worst.lastUsedDay))
          worst = e;
      }
      _entries.remove(worst.address);
    }
  }

  private void schedule_save()
  {
    if (_savePending)
      return;
    _savePending = true;
    _handler.postDelayed(_saveTask, SAVE_DEBOUNCE_MS);
  }

  /** Write immediately, cancelling the pending debounced save. */
  public void flush()
  {
    if (!_savePending)
      return;
    _handler.removeCallbacks(_saveTask);
    _savePending = false;
    save_now();
  }

  private void save_now()
  {
    SharedPreferences prefs = prefs();
    if (prefs == null)
      return;
    JSONArray arr = new JSONArray();
    for (Entry e : _entries.values())
    {
      JSONObject obj = new JSONObject();
      try
      {
        obj.put("address", e.address);
        obj.put("count", e.count);
        obj.put("firstSeenDay", e.firstSeenDay);
        obj.put("lastUsedDay", e.lastUsedDay);
        obj.put("sources", e.sources);
        arr.put(obj);
      }
      catch (JSONException impossible) {}
    }
    JSONObject root = new JSONObject();
    try
    {
      root.put("v", FORMAT_VERSION);
      root.put("addresses", arr);
    }
    catch (JSONException impossible) {}
    prefs.edit().putString(KEY_ENTRIES, root.toString()).apply();
  }
}
