package juloo.keyboard2.prefs;

import java.util.Arrays;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;
import static org.junit.Assert.*;

public class CustomThemesPreferenceTest
{
  public CustomThemesPreferenceTest() {}
  private static final String CUSTOM_THEMES_SOURCE =
      "srcs/juloo.keyboard2/prefs/CustomThemesPreference.java";
  private static final String[] COLOR_FIELD_NAMES = {
      "keyboard", "key", "keyAlternate", "action", "actionAlternate",
      "space", "spaceAlternate", "label", "subLabel"
  };


  @Test
  public void parse_color_accepts_hashless_hex_and_falls_back_loudly()
  {
    assertEquals("Custom theme colors should accept compact user input without #.",
        0xff12abef, CustomThemesPreference.parseColor("12ABEF", 0xff000000));
    assertEquals("Invalid custom theme colors should keep the existing fallback instead of crashing.",
        0xff123456, CustomThemesPreference.parseColor("not-a-color", 0xff123456));
    assertEquals("Blank custom theme colors should keep the existing fallback instead of blacking out keys.",
        0xff654321, CustomThemesPreference.parseColor("", 0xff654321));
  }

  @Test
  public void serializer_round_trips_multiple_custom_themes()
  {
    List<CustomThemesPreference.CustomTheme> themes = Arrays.asList(
        new CustomThemesPreference.CustomTheme("Night Gold", 0xff101010,
          0xff202020, 0xff303030, 0xff404040, 0xff505050,
          0xff606060, 0xff707070, 0xfff6d365, 0xffd0a933),
        new CustomThemesPreference.CustomTheme("Day Blue", 0xfff4f7fb,
          0xffe7edf6, 0xffdce6f2, 0xffcbd9eb, 0xffbdcde2,
          0xffd7e2f0, 0xffc9d7e8, 0xff1c2a3a, 0xff40536b));

    String serialized = ListGroupPreference.save_to_string(themes,
        CustomThemesPreference.SERIALIZER);
    List<CustomThemesPreference.CustomTheme> restored =
      ListGroupPreference.load_from_string(serialized,
          CustomThemesPreference.SERIALIZER);

    assertNotNull("Saved custom themes should decode back into editable theme rows.",
        restored);
    assertEquals("Users should be able to keep more than one custom theme.",
        2, restored.size());
    assertThemeEquals(themes.get(0), restored.get(0));
    assertThemeEquals(themes.get(1), restored.get(1));
  }

  @Test
  public void custom_theme_color_fields_are_reusable_editors_not_plain_edittexts()
      throws Exception
  {
    String source = readSource(CUSTOM_THEMES_SOURCE);
    String themeFields = classBody(source, "static class ThemeFields");
    String constructor = methodBody(themeFields,
        "ThemeFields(Context context, CustomTheme initial)");

    for (String field : COLOR_FIELD_NAMES)
    {
      assertFalse("Custom theme color '" + field + "' must be a reusable color editor, not a direct EditText-only field.",
          Pattern.compile("\\bEditText\\s+" + field + "\\b")
            .matcher(themeFields).find());
      assertFalse("Custom theme color '" + field + "' must be built through a color editor/helper, not the name/text addField helper.",
          Pattern.compile("\\b" + field + "\\s*=\\s*addField\\s*\\(")
            .matcher(constructor).find());
    }

    assertEquals("Every editable theme color must be created through the reusable color editor path.",
        COLOR_FIELD_NAMES.length, countColorEditorCreations(constructor));
  }

  @Test
  public void custom_theme_color_editor_exposes_swatch_hex_text_and_rgb_sliders()
      throws Exception
  {
    String source = readSource(CUSTOM_THEMES_SOURCE);
    String themeFields = classBody(source, "static class ThemeFields");

    assertTrue("The reusable color editor must keep hex text visible/editable.",
        themeFields.contains("EditText")
        && themeFields.contains("toHex("));
    assertTrue("The reusable color editor must expose RGB SeekBar sliders for each color.",
        themeFields.contains("SeekBar")
        && (countOccurrences(themeFields, "new SeekBar") >= 3
          || countOccurrences(themeFields, "SeekBar") >= 3
          || mentionsRgbChannels(themeFields)));
    assertTrue("The color editor swatch must be at least 48dp so the selected color is visibly reviewable.",
        hasDpAtLeast(themeFields, 48));
    assertTrue("The color editor swatch should be oval/circular, or use an equivalent shaped drawable.",
        containsAny(themeFields, "GradientDrawable.OVAL", ".setShape(GradientDrawable.OVAL)",
          "OvalShape", "ShapeDrawable", "setCornerRadius"));
  }

  @Test
  public void custom_theme_rgb_sliders_update_hex_used_by_theme_fields_theme()
      throws Exception
  {
    String source = readSource(CUSTOM_THEMES_SOURCE);
    String themeFields = classBody(source, "static class ThemeFields");
    String theme = methodBody(themeFields, "CustomTheme theme()");

    assertTrue("RGB slider changes must update the same hex text/value that ThemeFields.theme() persists.",
        containsAny(themeFields, "OnSeekBarChangeListener",
          "setOnSeekBarChangeListener")
        && containsLineWithAll(themeFields, "setText", "toHex"));

    for (String field : COLOR_FIELD_NAMES)
      assertFalse("ThemeFields.theme() must read color '" + field + "' from the color editor's synchronized value, not from a plain EditText.getText() field.",
          Pattern.compile("\\b" + field + "\\.getText\\s*\\(")
            .matcher(theme).find());
  }

  private static String readSource(String path)
      throws Exception
  {
    return new String(Files.readAllBytes(Paths.get(path)),
        StandardCharsets.UTF_8);
  }

  private static String classBody(String source, String classSignature)
  {
    return blockBody(source, classSignature);
  }

  private static String methodBody(String source, String methodSignature)
  {
    return blockBody(source, methodSignature);
  }

  private static String blockBody(String source, String signature)
  {
    int start = source.indexOf(signature);
    assertTrue("Expected source block: " + signature, start >= 0);
    int open = source.indexOf('{', start);
    assertTrue("Expected block body for: " + signature, open >= 0);
    int depth = 0;
    for (int i = open; i < source.length(); ++i)
    {
      char c = source.charAt(i);
      if (c == '{')
        ++depth;
      else if (c == '}')
      {
        --depth;
        if (depth == 0)
          return source.substring(open + 1, i);
      }
    }
    fail("Unclosed source block: " + signature);
    return "";
  }

  private static int countColorEditorCreations(String constructor)
  {
    int count = 0;
    for (String field : COLOR_FIELD_NAMES)
    {
      if (Pattern.compile("\\b" + field + "\\s*=\\s*(new\\s+\\w+|\\w*(?:Color|color|Editor|editor)\\w*\\s*\\()")
          .matcher(constructor).find())
        ++count;
    }
    return count;
  }

  private static boolean mentionsRgbChannels(String source)
  {
    String lower = source.toLowerCase(Locale.ROOT);
    return lower.contains("red") && lower.contains("green")
      && lower.contains("blue") && source.contains("SeekBar");
  }

  private static boolean hasDpAtLeast(String source, int minimum)
  {
    Matcher matcher = Pattern.compile("dp\\s*\\([^,]+,\\s*(\\d+)\\s*\\)|dp\\s*\\(\\s*(\\d+)\\s*\\)")
      .matcher(source);
    while (matcher.find())
    {
      String value = matcher.group(1) == null ? matcher.group(2) : matcher.group(1);
      if (Integer.parseInt(value) >= minimum)
        return true;
    }
    return false;
  }

  private static boolean containsAny(String source, String... needles)
  {
    for (String needle : needles)
      if (source.contains(needle))
        return true;
    return false;
  }

  private static boolean containsLineWithAll(String source, String... parts)
  {
    String[] lines = source.split("\\r?\\n");
    for (String line : lines)
    {
      boolean foundAll = true;
      for (String part : parts)
        foundAll = foundAll && line.contains(part);
      if (foundAll)
        return true;
    }
    return false;
  }

  private static int countOccurrences(String source, String needle)
  {
    int count = 0;
    int index = 0;
    while ((index = source.indexOf(needle, index)) >= 0)
    {
      ++count;
      index += needle.length();
    }
    return count;
  }

  private static void assertThemeEquals(CustomThemesPreference.CustomTheme expected,
      CustomThemesPreference.CustomTheme actual)
  {
    assertEquals(expected.name, actual.name);
    assertEquals(expected.colorKeyboard, actual.colorKeyboard);
    assertEquals(expected.colorKey, actual.colorKey);
    assertEquals(expected.colorKeyAlternate, actual.colorKeyAlternate);
    assertEquals(expected.colorKeyAction, actual.colorKeyAction);
    assertEquals(expected.colorKeyActionAlternate, actual.colorKeyActionAlternate);
    assertEquals(expected.colorKeySpaceBar, actual.colorKeySpaceBar);
    assertEquals(expected.colorKeySpaceBarAlternate, actual.colorKeySpaceBarAlternate);
    assertEquals(expected.colorLabel, actual.colorLabel);
    assertEquals(expected.colorSubLabel, actual.colorSubLabel);
  }
}
