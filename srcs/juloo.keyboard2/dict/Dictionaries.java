package juloo.keyboard2.dict;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import juloo.cdict.Cdict;
import juloo.keyboard2.Logs;
import juloo.keyboard2.Utils;

/** Manage and load installed dictionaries. */
public final class Dictionaries
{
  public static Dictionaries instance(Context ctx)
  {
    if (_instance == null)
      _instance = new Dictionaries(ctx);
    return _instance;
  }

  /** Util for finding a dictionary by name. Returns [null] if not found. */
  public static Cdict find_by_name(Cdict[] dicts, String name)
  {
    for (Cdict d : dicts)
      if (d.name.equals(name))
        return d;
    return null;
  }

  /** Load an installed dictionary. Return [null] if the requested dictionary
      is not installed or the dictionary couldn't be loaded. */
  public Cdict[] load(String dict_name)
  {
    if (_loaded_dictionaries.containsKey(dict_name))
      return _loaded_dictionaries.get(dict_name);
    ensure_bundled_dictionary_file(dict_name);
    Cdict[] dict = load_uncached(dict_name);
    if (dict == null && is_bundled_english(dict_name)
        && _installed_dictionaries.contains(dict_name)
        && install_bundled_dictionary(dict_name))
      dict = load_uncached(dict_name);
    _loaded_dictionaries.put(dict_name, dict);
    return dict;
  }

  public Set<String> get_installed() { return _installed_dictionaries; }

  public void install(String dict_name, byte[] data) throws IOException
  {
    FileOutputStream outp = _context.openFileOutput(dict_file_name(dict_name),
        Context.MODE_PRIVATE);
    outp.write(data);
    outp.close();
    set_installed(dict_name);
  }

  /** Return the absolute path used to store the dictionary with the given
      name. Return the same result whether the dictionary is installed or not. */
  public File get_install_location(String dict_name)
  {
    return _context.getFileStreamPath(dict_file_name(dict_name));
  }

  /** Declare a dictionary as installed. A dictionary file must exist at the
      path returned by [get_install_location(dict_name)]. */
  public void set_installed(String dict_name)
  {
    _installed_dictionaries.add(dict_name);
    _loaded_dictionaries.remove(dict_name);
    save();
  }

  public void uninstall(String dict_name)
  {
    _context.deleteFile(dict_file_name(dict_name));
    _installed_dictionaries.remove(dict_name);
    _loaded_dictionaries.remove(dict_name);
    save();
  }

  /** Private */

  Context _context;
  Set<String> _installed_dictionaries;
  /** Might be 'null' when safe storage is not available. */
  SharedPreferences _shared_prefs;
  Map<String, Cdict[]> _loaded_dictionaries;

  static Dictionaries _instance = null;

  static final String PREF_INSTALLED_DICTS = "installed";
  static final String PREF_BUNDLED_ENGLISH_SEEDED =
    "bundled_english_au_gb_v1_seeded";
  static final String PREF_BUNDLED_ENGLISH_US_SEEDED =
    "bundled_english_us_v1_seeded";
  static final String[] BUNDLED_ENGLISH_DICTIONARIES =
    { "en_AU", "en_GB", "en_US" };
  static final String[] BUNDLED_ENGLISH_AU_GB = { "en_AU", "en_GB" };
  static final String[] BUNDLED_ENGLISH_US = { "en_US" };

  Dictionaries(Context ctx)
  {
    _context = ctx;
    _installed_dictionaries = new HashSet();
    _loaded_dictionaries = new TreeMap<String, Cdict[]>();
    load_prefs();
    seed_bundled_english_dictionaries();
  }

  void load_prefs()
  {
    _shared_prefs = null;
    try
    {
      _shared_prefs =
        _context.getSharedPreferences("dictionaries", Context.MODE_PRIVATE);
      Set<String> s = _shared_prefs.getStringSet(PREF_INSTALLED_DICTS, null);
      if (s != null)
        _installed_dictionaries.addAll(s);
    }
    catch (Exception e)
    {
      Logs.exn("", e);
    }
  }

  void seed_bundled_english_dictionaries()
  {
    seed_bundled_dictionary_generation(PREF_BUNDLED_ENGLISH_SEEDED,
        BUNDLED_ENGLISH_AU_GB);
    seed_bundled_dictionary_generation(PREF_BUNDLED_ENGLISH_US_SEEDED,
        BUNDLED_ENGLISH_US);
  }

  void seed_bundled_dictionary_generation(String preference,
      String[] dictionaries)
  {
    if (_shared_prefs == null
        || _shared_prefs.getBoolean(preference, false))
      return;

    for (String name : dictionaries)
      if ((!_installed_dictionaries.contains(name)
            || !get_install_location(name).isFile())
          && !install_bundled_dictionary(name))
        return;

    _shared_prefs.edit().putBoolean(preference, true).commit();
  }

  boolean install_bundled_dictionary(String name)
  {
    InputStream input = null;
    try
    {
      input = _context.getAssets().open("dictionaries/" + dict_file_name(name));
      install(name, Utils.read_all_bytes(input));
      return true;
    }
    catch (IOException e)
    {
      Logs.exn("Unable to seed bundled dictionary " + name, e);
      return false;
    }
    finally
    {
      if (input != null)
      {
        try { input.close(); }
        catch (IOException e) { Logs.exn("", e); }
      }
    }
  }

  static boolean is_bundled_english(String name)
  {
    for (String bundled : BUNDLED_ENGLISH_DICTIONARIES)
      if (bundled.equals(name))
        return true;
    return false;
  }

  boolean ensure_bundled_dictionary_file(String name)
  {
    if (!is_bundled_english(name)
        || !_installed_dictionaries.contains(name))
      return false;
    return get_install_location(name).isFile()
      || install_bundled_dictionary(name);
  }

  Cdict[] load_uncached(String dict_name)
  {
    if (!_installed_dictionaries.contains(dict_name))
      return null;
    try
    {
      FileInputStream inp = _context.openFileInput(dict_file_name(dict_name));
      byte[] data = Utils.read_all_bytes(inp);
      inp.close();
      return Cdict.of_bytes(data);
    }
    catch (IOException e) { return null; }
    catch (Cdict.ConstructionError e) { return null; }
  }

  void save()
  {
    if (_shared_prefs == null)
      return;
    _shared_prefs.edit()
      .putStringSet(PREF_INSTALLED_DICTS, _installed_dictionaries)
      .commit();
  }

  static String dict_file_name(String dict_name)
  {
    return dict_name + ".dict";
  }
}
