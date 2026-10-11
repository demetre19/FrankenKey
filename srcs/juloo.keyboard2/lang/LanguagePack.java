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
  /** Optional 8-bit unigram table aligned to the bundled Cdict. */
  public final File word_frequencies;

  LanguagePack(String id, File root, File hunspell_aff, File hunspell_dic,
      File suggestions, File next_words)
  {
    this(id, root, hunspell_aff, hunspell_dic, suggestions, next_words, null);
  }

  LanguagePack(String id, File root, File hunspell_aff, File hunspell_dic,
      File suggestions, File next_words, File word_frequencies)
  {
    this.id = id;
    this.root = root;
    this.hunspell_aff = hunspell_aff;
    this.hunspell_dic = hunspell_dic;
    this.suggestions = suggestions;
    this.next_words = next_words;
    this.word_frequencies = word_frequencies;
  }
}
