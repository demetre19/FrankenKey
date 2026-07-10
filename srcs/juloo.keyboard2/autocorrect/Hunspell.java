package juloo.keyboard2.autocorrect;

import java.io.File;
import juloo.keyboard2.lang.LanguagePack;

/** Thin JNI wrapper around Hunspell. */
public final class Hunspell implements AutoCloseable
{
  public static Hunspell load(LanguagePack pack) throws ConstructionError
  {
    return new Hunspell(pack.hunspell_aff, pack.hunspell_dic);
  }

  public boolean spell(String word)
  {
    return spell_native(_ptr, word);
  }

  public String[] suggest(String word, int max_count)
  {
    if (max_count <= 0)
      return new String[0];
    return suggest_native(_ptr, word, max_count);
  }

  @Override
  public void close()
  {
    if (_ptr != 0)
    {
      destroy_native(_ptr);
      _ptr = 0;
    }
  }


  private long _ptr;

  private Hunspell(File aff, File dic) throws ConstructionError
  {
    _ptr = construct_native(aff.getAbsolutePath(), dic.getAbsolutePath());
  }

  public static class ConstructionError extends Exception
  {
    public ConstructionError(String msg) { super(msg); }
  }

  static
  {
    System.loadLibrary("hunspell_java");
  }

  private static native long construct_native(String aff_path, String dic_path)
      throws ConstructionError;
  private static native void destroy_native(long ptr);
  private static native boolean spell_native(long ptr, String word);
  private static native String[] suggest_native(long ptr, String word,
      int max_count);
}
