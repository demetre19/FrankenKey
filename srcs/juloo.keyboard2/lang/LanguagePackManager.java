package juloo.keyboard2.lang;

import android.content.Context;
import android.content.res.AssetManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.json.JSONException;
import org.json.JSONObject;
import juloo.keyboard2.Utils;

/** Load manifest-backed typing-assistance language packs. */
public final class LanguagePackManager
{
  public LanguagePackManager(Context ctx)
  {
    _assets = ctx.getAssets();
    _storage_dir = new File(ctx.getNoBackupFilesDir(), PACKS_DIR);
  }

  /** Return [null] when no bundled pack exists for [pack_id]. */
  public LanguagePack find(String pack_id)
  {
    try
    {
      if (!is_safe_id(pack_id))
        return null;
      String asset_root = PACKS_DIR + "/" + pack_id;
      String manifest = read_asset(asset_root + "/" + MANIFEST_FILE);
      File root = materialize_pack(pack_id, asset_root, new JSONObject(manifest));
      return from_manifest(pack_id, root, manifest);
    }
    catch (Exception e) { return null; }
  }

  public static LanguagePack load_from_directory(String pack_id, File root)
      throws Exception
  {
    InputStream inp = new java.io.FileInputStream(new File(root, MANIFEST_FILE));
    String manifest = Utils.read_all_utf8(inp);
    inp.close();
    return from_manifest(pack_id, root, manifest);
  }

  public static LanguagePack from_manifest(String pack_id, File root,
      String manifest) throws JSONException
  {
    JSONObject obj = new JSONObject(manifest);
    File aff = required_path(pack_id, root, obj, "hunspell_aff");
    File dic = required_path(pack_id, root, obj, "hunspell_dic");
    File suggestions = optional_path(pack_id, root, obj, "suggestions");
    File next_words = optional_path(pack_id, root, obj, "next_words");
    return new LanguagePack(pack_id, root, aff, dic, suggestions, next_words);
  }

  private static File required_path(String pack_id, File root, JSONObject obj,
      String role)
  {
    String path = obj.optString(role, null);
    if (path == null || path.length() == 0)
      throw new IllegalArgumentException(pack_id + ": missing " + role);
    File file = resolve_path(pack_id, root, role, path);
    if (!file.isFile())
      throw new IllegalArgumentException(pack_id + ": missing " + role
          + ": " + path);
    return file;
  }

  private static File optional_path(String pack_id, File root, JSONObject obj,
      String role)
  {
    String path = obj.optString(role, null);
    if (path == null || path.length() == 0)
      return null;
    File file = resolve_path(pack_id, root, role, path);
    if (!file.isFile())
      throw new IllegalArgumentException(pack_id + ": missing " + role
          + ": " + path);
    return file;
  }

  private static File resolve_path(String pack_id, File root, String role,
      String path)
  {
    if (new File(path).isAbsolute() || path.equals("..")
        || path.startsWith("../") || path.endsWith("/..")
        || path.indexOf("/../") >= 0)
      throw new IllegalArgumentException(pack_id + ": unsafe " + role
          + ": " + path);
    try
    {
      File file = new File(root, path);
      String root_path = root.getCanonicalPath() + File.separator;
      String file_path = file.getCanonicalPath();
      if (!file_path.startsWith(root_path))
        throw new IllegalArgumentException(pack_id + ": unsafe " + role
            + ": " + path);
      return file;
    }
    catch (IOException e)
    {
      throw new IllegalArgumentException(pack_id + ": invalid " + role
          + ": " + path);
    }
  }

  private File materialize_pack(String pack_id, String asset_root, JSONObject obj)
      throws Exception
  {
    File root = new File(_storage_dir, pack_id);
    root.mkdirs();
    copy_asset(asset_root + "/" + MANIFEST_FILE, new File(root, MANIFEST_FILE));
    copy_manifest_asset(asset_root, root, obj, "hunspell_aff");
    copy_manifest_asset(asset_root, root, obj, "hunspell_dic");
    copy_manifest_asset(asset_root, root, obj, "suggestions");
    copy_manifest_asset(asset_root, root, obj, "next_words");
    return root;
  }

  private void copy_manifest_asset(String asset_root, File root, JSONObject obj,
      String role) throws IOException
  {
    String path = obj.optString(role, null);
    if (path == null || path.length() == 0)
      return;
    File out = resolve_path(asset_root, root, role, path);
    copy_asset(asset_root + "/" + path, out);
  }

  private String read_asset(String path) throws Exception
  {
    InputStream inp = _assets.open(path);
    String out = Utils.read_all_utf8(inp);
    inp.close();
    return out;
  }

  private void copy_asset(String asset_path, File out) throws IOException
  {
    File parent = out.getParentFile();
    if (parent != null)
      parent.mkdirs();
    InputStream inp = _assets.open(asset_path);
    FileOutputStream outp = new FileOutputStream(out);
    byte[] buff = new byte[16000];
    int len;
    while ((len = inp.read(buff)) != -1)
      outp.write(buff, 0, len);
    inp.close();
    outp.close();
  }

  private static boolean is_safe_id(String pack_id)
  {
    return pack_id != null && pack_id.matches("[A-Za-z0-9_:-]+");
  }

  private final AssetManager _assets;
  private final File _storage_dir;

  public static final String PACKS_DIR = "language_packs";
  public static final String MANIFEST_FILE = "manifest.json";
}
