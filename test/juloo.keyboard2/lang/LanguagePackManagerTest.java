package juloo.keyboard2.lang;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class LanguagePackManagerTest
{
  public LanguagePackManagerTest() {}

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void manifest_string_resolves_all_paths_relative_to_pack_root()
      throws Exception
  {
    File root = temp.newFolder("en_US");
    File aff = touch(root, "hunspell.aff");
    File dic = touch(root, "hunspell.dic");
    File suggestions = touch(root, "suggestions.txt");
    File nextWords = touch(root, "next_words.bin");

    LanguagePack pack = LanguagePackManager.from_manifest("en_US", root,
        manifest("hunspell.aff", "hunspell.dic", "suggestions.txt",
          "next_words.bin"));

    assertEquals("en_US", pack.id);
    assertSameCanonicalFile(root, pack.root);
    assertSameCanonicalFile(aff, pack.hunspell_aff);
    assertSameCanonicalFile(dic, pack.hunspell_dic);
    assertSameCanonicalFile(suggestions, pack.suggestions);
    assertSameCanonicalFile(nextWords, pack.next_words);
  }

  @Test
  public void manifest_file_resolves_required_hunspell_paths_relative_to_pack_root()
      throws Exception
  {
    File root = temp.newFolder("en_US");
    File aff = touch(root, "hunspell.aff");
    File dic = touch(root, "hunspell.dic");
    write(new File(root, LanguagePackManager.MANIFEST_FILE),
        manifest("hunspell.aff", "hunspell.dic", null, null));

    LanguagePack pack = LanguagePackManager.load_from_directory("en_US", root);

    assertEquals("en_US", pack.id);
    assertSameCanonicalFile(root, pack.root);
    assertSameCanonicalFile(aff, pack.hunspell_aff);
    assertSameCanonicalFile(dic, pack.hunspell_dic);
    assertNull(pack.suggestions);
    assertNull(pack.next_words);
  }

  @Test
  public void missing_required_hunspell_files_fail_with_pack_id_role_and_path()
      throws Exception
  {
    assertMissingRequiredFile("hunspell_aff", "missing.aff",
        "missing.aff", "hunspell.dic");
    assertMissingRequiredFile("hunspell_dic", "missing.dic",
        "hunspell.aff", "missing.dic");
  }

  @Test
  public void unsafe_absolute_or_traversing_manifest_paths_are_rejected()
      throws Exception
  {
    File root = temp.newFolder("en_US");
    touch(root, "hunspell.aff");
    touch(root, "hunspell.dic");
    String absolute = absolutePath("outside.aff");

    assertUnsafePath(root, "hunspell_aff", absolute, absolute,
        "hunspell.dic");
    assertUnsafePath(root, "hunspell_dic", "../outside.dic",
        "hunspell.aff", "../outside.dic");
  }

  @Test
  public void lookup_by_subtype_dictionary_id_returns_pack_or_null_when_absent()
      throws Exception
  {
    File enUsRoot = temp.newFolder("en_US_lookup");
    File aff = touch(enUsRoot, "hunspell.aff");
    File dic = touch(enUsRoot, "hunspell.dic");
    write(new File(enUsRoot, LanguagePackManager.MANIFEST_FILE),
        manifest("hunspell.aff", "hunspell.dic", null, null));

    LanguagePack enUs = LanguagePackManager.load_from_directory("en_US",
        enUsRoot);
    LanguagePackManager manager = new LanguagePackManager(
        RuntimeEnvironment.getApplication());

    assertEquals("en_US", enUs.id);
    assertSameCanonicalFile(enUsRoot, enUs.root);
    assertSameCanonicalFile(aff, enUs.hunspell_aff);
    assertSameCanonicalFile(dic, enUs.hunspell_dic);
    assertNull(manager.find("fr_FR"));
  }

  private void assertMissingRequiredFile(String role, String missingPath,
      String affPath, String dicPath) throws Exception
  {
    File root = temp.newFolder("missing-" + role);
    if (!"hunspell_aff".equals(role))
      touch(root, affPath);
    if (!"hunspell_dic".equals(role))
      touch(root, dicPath);

    try
    {
      LanguagePackManager.from_manifest("en_US", root,
          manifest(affPath, dicPath, null, null));
      fail("missing " + role + " must fail");
    }
    catch (IllegalArgumentException e)
    {
      assertMessageContains(e, "en_US", "missing", role, missingPath);
    }
  }

  private void assertUnsafePath(File root, String role, String unsafePath,
      String affPath, String dicPath) throws Exception
  {
    try
    {
      LanguagePackManager.from_manifest("en_US", root,
          manifest(affPath, dicPath, null, null));
      fail("unsafe " + role + " path must fail");
    }
    catch (IllegalArgumentException e)
    {
      assertMessageContains(e, "en_US", "unsafe", role, unsafePath);
    }
  }

  private static void assertMessageContains(Exception e, String... parts)
  {
    String message = e.getMessage();
    for (String part : parts)
      assertTrue("message <" + message + "> must contain <" + part + ">",
          message != null && message.contains(part));
  }

  private static void assertSameCanonicalFile(File expected, File actual)
      throws Exception
  {
    assertEquals(expected.getCanonicalFile(), actual.getCanonicalFile());
  }

  private File touch(File root, String relativePath) throws Exception
  {
    File file = new File(root, relativePath);
    File parent = file.getParentFile();
    if (parent != null)
      assertTrue(parent.mkdirs() || parent.isDirectory());
    assertTrue(file.createNewFile());
    return file;
  }

  private static void write(File file, String content) throws Exception
  {
    Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
  }

  private String absolutePath(String name) throws Exception
  {
    return new File(temp.newFolder(), name).getAbsolutePath();
  }

  private static String manifest(String affPath, String dicPath,
      String suggestionsPath, String nextWordsPath)
  {
    StringBuilder out = new StringBuilder();
    out.append("{");
    out.append("\"hunspell_aff\":").append(jsonString(affPath));
    out.append(",\"hunspell_dic\":").append(jsonString(dicPath));
    if (suggestionsPath != null)
      out.append(",\"suggestions\":").append(jsonString(suggestionsPath));
    if (nextWordsPath != null)
      out.append(",\"next_words\":").append(jsonString(nextWordsPath));
    out.append("}");
    return out.toString();
  }

  private static String jsonString(String value)
  {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
      + "\"";
  }
}
