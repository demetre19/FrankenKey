package juloo.keyboard2.suggestions;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class WordFrequenciesTest
{
  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void reads_frequencies_and_repairs_wrapped_levels() throws Exception
  {
    WordFrequencies table = WordFrequencies.load(
        write(table(new int[] { 222, 0, 37, 140 }, 101)), null);

    assertEquals(222, table.frequency(0));
    assertEquals(37, table.frequency(2));
    assertEquals("Indexes outside the table are unknown.",
        -1, table.frequency(4));
    assertEquals("A top-frequency word stored as level 0 was wrapped by the builder.",
        WordFrequencies.LEVEL_MAX, table.repaired_level(0, 0));
    assertEquals("A genuinely rare level-0 word stays rare.",
        0, table.repaired_level(2, 0));
    assertEquals("Non-zero levels are never rewritten.",
        9, table.repaired_level(3, 9));

    LanguageModel model = LanguageModel.empty().with_unigrams(table);
    assertEquals(140, model.unigram(3));
    assertEquals(-1, LanguageModel.empty().unigram(3));
  }

  @Test
  public void rejects_corrupt_tables() throws Exception
  {
    byte[] valid = table(new int[] { 1, 2 }, 1);
    byte[] badMagic = valid.clone();
    badMagic[0] = 'X';
    assertRejected(badMagic);
    byte[] truncated = java.util.Arrays.copyOf(valid, valid.length - 1);
    assertRejected(truncated);
    byte[] trailing = java.util.Arrays.copyOf(valid, valid.length + 1);
    assertRejected(trailing);
  }

  @Test
  public void bundled_tables_parse_and_cover_the_dictionaries() throws Exception
  {
    for (String locale : new String[] { "en_US", "en_GB", "en_AU" })
    {
      File file = new File("assets/language_packs/" + locale
          + "/word_frequencies.bin");
      WordFrequencies table = WordFrequencies.load(file, null);
      assertTrue(locale + " must cover the bundled Cdict indexes.",
          table.frequency(150000) >= 0);
    }
  }

  private void assertRejected(byte[] data) throws Exception
  {
    try
    {
      WordFrequencies.load(write(data), null);
      fail("A corrupt word frequency table must be refused.");
    }
    catch (IOException e) {}
  }

  private File write(byte[] data) throws IOException
  {
    File file = temp.newFile();
    Files.write(file.toPath(), data);
    return file;
  }

  private static byte[] table(int[] frequencies, int repairMin)
      throws IOException
  {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    DataOutputStream out = new DataOutputStream(bytes);
    out.write("FKWF".getBytes(StandardCharsets.US_ASCII));
    out.writeByte(1);
    out.writeInt(frequencies.length);
    out.writeByte(repairMin);
    out.writeByte(1);
    out.writeInt(0);
    out.writeByte(3);
    out.write("the".getBytes(StandardCharsets.UTF_8));
    for (int frequency : frequencies)
      out.writeByte(frequency);
    out.flush();
    return bytes.toByteArray();
  }
}
