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

public class WordBigramsTest
{
  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void looks_up_ranked_followers_by_head_index() throws Exception
  {
    WordBigrams table = WordBigrams.load(write(table(
            new int[][] { { 7, 70, 71, 72 }, { 9, 90, NONE, NONE } })), null);

    assertArrayEquals(new int[] { 70, 71, 72 }, table.followers(7));
    assertArrayEquals("Missing continuations are dropped.",
        new int[] { 90 }, table.followers(9));
    assertArrayEquals(new int[0], table.followers(8));
    assertArrayEquals(new int[0], table.followers(-1));

    LanguageModel model = LanguageModel.empty().with_word_bigrams(table);
    assertArrayEquals(new int[] { 90 }, model.cdict_followers(9));
    assertArrayEquals("Adding unigrams keeps the continuation table.",
        new int[] { 90 }, model.with_unigrams(null).cdict_followers(9));
  }

  @Test
  public void rejects_unsorted_or_truncated_tables() throws Exception
  {
    assertRejected(table(new int[][] { { 9, 1, 2, 3 }, { 7, 1, 2, 3 } }));
    byte[] valid = table(new int[][] { { 7, 1, 2, 3 } });
    assertRejected(java.util.Arrays.copyOf(valid, valid.length - 1));
    assertRejected(java.util.Arrays.copyOf(valid, valid.length + 1));
  }

  @Test
  public void bundled_tables_parse() throws Exception
  {
    for (String locale : new String[] { "en_US", "en_GB", "en_AU" })
      WordBigrams.load(new File("assets/language_packs/" + locale
            + "/word_bigrams.bin"), null);
  }

  private static final int NONE = 0xFFFFFF;

  private void assertRejected(byte[] data) throws Exception
  {
    try
    {
      WordBigrams.load(write(data), null);
      fail("A corrupt word bigram table must be refused.");
    }
    catch (IOException e) {}
  }

  private File write(byte[] data) throws IOException
  {
    File file = temp.newFile();
    Files.write(file.toPath(), data);
    return file;
  }

  private static byte[] table(int[][] rows) throws IOException
  {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    DataOutputStream out = new DataOutputStream(bytes);
    out.write("FKWB".getBytes(StandardCharsets.US_ASCII));
    out.writeByte(1);
    out.writeInt(rows.length);
    out.writeByte(0);
    for (int[] row : rows)
      for (int value : row)
      {
        out.writeByte(value >> 16);
        out.writeByte(value >> 8);
        out.writeByte(value);
      }
    out.flush();
    return bytes.toByteArray();
  }
}
