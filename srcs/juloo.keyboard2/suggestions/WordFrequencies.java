package juloo.keyboard2.suggestions;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import juloo.cdict.Cdict;

/**
 * Immutable 8-bit unigram frequencies indexed by Cdict word index, generated
 * by scripts/gen_word_frequencies.py from the wordlist the Cdict was built
 * from. Loaded on the decoder worker and validated against the dictionary it
 * will be used with; a mismatched table is refused.
 */
public final class WordFrequencies
{
  private static final int VERSION = 1;
  private static final int MAX_ENTRIES = 1 << 22;
  private static final int MAX_SAMPLES = 64;
  static final int LEVEL_MAX = 15;

  private final byte[] _frequencies;
  private final int _repairMin;

  WordFrequencies(byte[] frequencies, int repairMin)
  {
    _frequencies = frequencies;
    _repairMin = repairMin;
  }

  /** Load [file] and check its sample words against [dictionary]. */
  public static WordFrequencies load(File file, Cdict dictionary)
      throws IOException
  {
    DataInputStream input = new DataInputStream(new FileInputStream(file));
    try
    {
      byte[] magic = new byte[4];
      input.readFully(magic);
      if (!Arrays.equals(magic, "FKWF".getBytes(StandardCharsets.US_ASCII))
          || input.readUnsignedByte() != VERSION)
        throw new IOException("Unsupported word frequency table " + file);
      int count = input.readInt();
      int repairMin = input.readUnsignedByte();
      int samples = input.readUnsignedByte();
      if (count <= 0 || count > MAX_ENTRIES || samples > MAX_SAMPLES)
        throw new IOException("Word frequency table out of bounds " + file);
      for (int i = 0; i < samples; i++)
      {
        int index = input.readInt();
        byte[] word = new byte[input.readUnsignedByte()];
        input.readFully(word);
        String expected = new String(word, StandardCharsets.UTF_8);
        if (dictionary != null && (index < 0 || index >= count
              || !expected.equals(word_at(dictionary, index))))
          throw new IOException("Word frequency table does not match the "
              + "dictionary at index " + index);
      }
      byte[] frequencies = new byte[count];
      input.readFully(frequencies);
      if (input.read() != -1)
        throw new IOException("Trailing data in word frequency table " + file);
      return new WordFrequencies(frequencies, repairMin);
    }
    finally { input.close(); }
  }

  private static String word_at(Cdict dictionary, int index)
  {
    try { return dictionary.word(index); }
    catch (RuntimeException e) { return null; }
  }

  /** Source frequency 0..255 for a Cdict index, or -1 when unknown. */
  int frequency(int index)
  {
    if (index < 0 || index >= _frequencies.length)
      return -1;
    return _frequencies[index] & 0xff;
  }

  /** Correct a 4-bit Cdict level that the builder wrapped from 16 to 0. */
  int repaired_level(int index, int level)
  {
    if (level != 0)
      return level;
    int frequency = frequency(index);
    return frequency >= _repairMin && frequency > 0 ? LEVEL_MAX : level;
  }
}
