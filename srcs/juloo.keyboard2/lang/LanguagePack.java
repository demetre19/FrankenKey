package juloo.keyboard2.lang;

import java.io.File;

/** Files that make up one typing-assistance language pack. */
public final class LanguagePack
{
  public final String id;
  public final File root;
  public final File hunspell_aff;
  public final File hunspell_dic;
  public final File suggestions;
  public final File next_words;

  LanguagePack(String id, File root, File hunspell_aff, File hunspell_dic,
      File suggestions, File next_words)
  {
    this.id = id;
    this.root = root;
    this.hunspell_aff = hunspell_aff;
    this.hunspell_dic = hunspell_dic;
    this.suggestions = suggestions;
    this.next_words = next_words;
  }
}
