package juloo.keyboard2;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import static org.junit.Assert.*;

public class ThemeRowsTest
{
  private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

  public ThemeRowsTest() {}

  @Test
  public void theme_values_expose_curated_preconfigured_themes()
      throws Exception
  {
    List<String> themeValues = stringArrayValues(parseXml("res/values/arrays.xml"),
        "pref_theme_values");

    assertEquals("Theme picker should expose curated palettes plus custom themes.",
        Arrays.asList("system", "dark", "light", "forest", "blue", "gray",
          "custom"), themeValues);
  }

  @Test
  public void settings_xml_does_not_expose_exact_color_overrides()
      throws Exception
  {
    Element settings = parseXml("res/xml/settings.xml").getDocumentElement();

    for (String key : Arrays.asList("keyboard_row_color",
        "keyboard_alternate_row_color", "keyboard_corner_label_color"))
      assertEquals("Simple Light/Dark themes must not be overridden by saved color preference " + key + ".",
          0, elementsWithAndroidKey(settings, key).size());
  }

  @Test
  public void settings_xml_exposes_custom_theme_manager()
      throws Exception
  {
    Element settings = parseXml("res/xml/settings.xml").getDocumentElement();
    List<Element> preferences = elementsWithAndroidKey(settings, "custom_themes");

    assertEquals("Settings must expose one custom themes manager.",
        1, preferences.size());
    assertEquals("Custom themes manager must support adding multiple saved themes.",
        "juloo.keyboard2.prefs.CustomThemesPreference",
        preferences.get(0).getTagName());
  }

  @Test
  public void physical_keyboard_default_keeps_software_keyboard_visible()
      throws Exception
  {
    Element settings = parseXml("res/xml/settings.xml").getDocumentElement();
    List<Element> preferences = elementsWithAndroidKey(settings,
        "physical_keyboard_behavior");

    assertEquals("Expected exactly one physical keyboard behavior preference.",
        1, preferences.size());
    assertEquals("Software keyboard should stay visible by default, even when Android reports a hardware keyboard.",
        "show", preferences.get(0).getAttributeNS(ANDROID_NS, "defaultValue"));
  }

  @Test
  public void themes_xml_declares_alternate_row_color_attrs()
      throws Exception
  {
    Element keyboardStyleable = styleable(parseXml("res/values/themes.xml"), "keyboard");

    for (String attr : Arrays.asList("colorKeyAlternate",
        "colorKeyActionAlternate", "colorKeySpaceBarAlternate"))
    {
      Element declaration = directChildAttr(keyboardStyleable, attr);
      assertNotNull("Keyboard themes must declare alternate row attr " + attr + ".",
          declaration);
      assertEquals("Alternate row attr must be declared as a color: " + attr + ".",
          "color", declaration.getAttribute("format"));
    }
  }

  @Test
  public void curated_themes_make_primary_labels_stronger_than_corner_labels()
      throws Exception
  {
    Document themes = parseXml("res/values/themes.xml");

    for (String theme : Arrays.asList("Dark", "Light", "Forest", "Blue", "Gray"))
      assertPrimaryLabelsOutrankCornerLabels(themes, theme);
  }

  @Test
  public void curated_themes_use_alternating_key_surfaces_with_readable_labels()
      throws Exception
  {
    Document themes = parseXml("res/values/themes.xml");

    for (String styleName : Arrays.asList("Dark", "Light", "Forest", "Blue", "Gray"))
    {
      Element theme = style(themes, styleName);
      int primaryLabel = parseHexColor(styleItem(theme, "colorLabel"));
      int cornerLabel = parseHexColor(styleItem(theme, "colorSubLabel"));

      assertNotEquals(styleName + " must alternate regular key rows.",
          styleItem(theme, "colorKey"), styleItem(theme, "colorKeyAlternate"));
      assertNotEquals(styleName + " must alternate action key rows.",
          styleItem(theme, "colorKeyAction"), styleItem(theme, "colorKeyActionAlternate"));
      assertNotEquals(styleName + " must alternate spacebar rows.",
          styleItem(theme, "colorKeySpaceBar"), styleItem(theme, "colorKeySpaceBarAlternate"));

      for (String keyAttr : Arrays.asList("colorKey", "colorKeyAction",
          "colorKeySpaceBar", "colorKeyAlternate", "colorKeyActionAlternate",
          "colorKeySpaceBarAlternate"))
      {
        int keyColor = parseHexColor(styleItem(theme, keyAttr));
        assertTrue(styleName + " primary labels must be readable on " + keyAttr + ".",
            contrastRatio(primaryLabel, keyColor) >= 4.5);
        assertTrue(styleName + " corner labels must be readable on " + keyAttr + ".",
            contrastRatio(cornerLabel, keyColor) >= 4.5);
      }
    }
  }
 
  @Test
  public void light_theme_uses_light_neutral_surfaces()
      throws Exception
  {
    Element light = style(parseXml("res/values/themes.xml"), "Light");

    assertEquals("Light theme keyboard background must be a pale neutral.",
        "#f4f5f7", styleItem(light, "colorKeyboard"));
    for (String keyAttr : Arrays.asList("colorKey", "colorKeyAction",
        "colorKeySpaceBar", "colorKeyAlternate"))
    {
      int keyColor = parseHexColor(styleItem(light, keyAttr));
      assertTrue("Light theme key surface must stay genuinely light: " + keyAttr + ".",
          relativeLuminance(keyColor) > 0.80);
    }
  }


  @Test
  public void contrast_text_color_flips_with_background_lightness()
  {
    assertEquals("Dark backgrounds need light text.",
        0xffffffff, Theme.contrastTextColor(0xff000000));
    assertEquals("Light backgrounds need dark text.",
        0xff000000, Theme.contrastTextColor(0xffffffff));
  }

  @Test
  public void ensure_text_contrast_replaces_low_contrast_theme_colors()
  {
    int lightOnDark = Theme.ensureTextContrast(0xff202020, 0xff000000);
    int darkOnLight = Theme.ensureTextContrast(0xffeeeeee, 0xffffffff);

    assertTrue("Fallback text on dark backgrounds must meet body-text contrast.",
        Theme.contrastRatio(lightOnDark, 0xff000000) >= 4.5);
    assertTrue("Fallback text on light backgrounds must meet body-text contrast.",
        Theme.contrastRatio(darkOnLight, 0xffffffff) >= 4.5);
    assertEquals("Dark backgrounds should fail over to light text.",
        0xffffffff, lightOnDark);
    assertEquals("Light backgrounds should fail over to dark text.",
        0xff000000, darkOnLight);
  }

  @Test
  public void ensure_text_contrast_keeps_readable_theme_colors()
  {
    assertEquals("Readable light text on a dark background should be preserved.",
        0xffaeb2b8, Theme.ensureTextContrast(0xffaeb2b8, 0xff050708));
    assertEquals("Readable dark text on a light background should be preserved.",
        0xff202328, Theme.ensureTextContrast(0xff202328, 0xffffffff));
  }

  private static Document parseXml(String path)
      throws Exception
  {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    return factory.newDocumentBuilder().parse(new File(path));
  }

  private static List<String> stringArrayValues(Document document, String name)
  {
    Element array = null;
    NodeList arrays = document.getElementsByTagName("string-array");
    for (int i = 0; i < arrays.getLength(); i++)
    {
      Element candidate = (Element)arrays.item(i);
      if (name.equals(candidate.getAttribute("name")))
      {
        array = candidate;
        break;
      }
    }
    assertNotNull("Expected string-array " + name + ".", array);

    List<String> values = new ArrayList<>();
    for (Element child : directElementChildren(array))
      if ("item".equals(child.getTagName()))
        values.add(child.getTextContent().trim());
    return values;
  }

  private static Element style(Document document, String name)
  {
    NodeList styles = document.getElementsByTagName("style");
    for (int i = 0; i < styles.getLength(); i++)
    {
      Element style = (Element)styles.item(i);
      if (name.equals(style.getAttribute("name")))
        return style;
    }
    fail("Expected style " + name + ".");
    return null;
  }

  private static Element styleable(Document document, String name)
  {
    NodeList styleables = document.getElementsByTagName("declare-styleable");
    for (int i = 0; i < styleables.getLength(); i++)
    {
      Element styleable = (Element)styleables.item(i);
      if (name.equals(styleable.getAttribute("name")))
        return styleable;
    }
    fail("Expected declare-styleable " + name + ".");
    return null;
  }

  private static Element directChildAttr(Element parent, String name)
  {
    for (Element child : directElementChildren(parent))
      if ("attr".equals(child.getTagName()) && name.equals(child.getAttribute("name")))
        return child;
    return null;
  }

  private static void assertPrimaryLabelsOutrankCornerLabels(Document document,
      String styleName)
  {
    Element style = style(document, styleName);
    int primaryLabel = parseHexColor(styleItem(style, "colorLabel"));
    int cornerLabel = parseHexColor(styleItem(style, "colorSubLabel"));

    for (String backgroundAttr : Arrays.asList("colorKey", "colorKeyAlternate"))
    {
      int background = parseHexColor(styleItem(style, backgroundAttr));
      double primaryContrast = contrastRatio(primaryLabel, background);
      double cornerContrast = contrastRatio(cornerLabel, background);
      assertTrue(styleName + " must make primary labels visually stronger than"
          + " corner labels on " + backgroundAttr + ".",
          primaryContrast > cornerContrast);
    }
  }

  private static String styleItem(Element style, String name)
  {
    for (Element child : directElementChildren(style))
      if ("item".equals(child.getTagName()) && name.equals(child.getAttribute("name")))
        return child.getTextContent().trim();
    fail("Expected item " + name + " in style " + style.getAttribute("name") + ".");
    return null;
  }

  private static int parseHexColor(String value)
  {
    assertTrue("Expected #RRGGBB color value, got " + value + ".",
        value.matches("#[0-9a-fA-F]{6}"));
    return (int)Long.parseLong(value.substring(1), 16);
  }

  private static double contrastRatio(int foreground, int background)
  {
    double foregroundLuminance = relativeLuminance(foreground);
    double backgroundLuminance = relativeLuminance(background);
    double lighter = Math.max(foregroundLuminance, backgroundLuminance);
    double darker = Math.min(foregroundLuminance, backgroundLuminance);
    return (lighter + 0.05) / (darker + 0.05);
  }

  private static double relativeLuminance(int color)
  {
    double red = linearColor((color >> 16) & 0xff);
    double green = linearColor((color >> 8) & 0xff);
    double blue = linearColor(color & 0xff);
    return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
  }

  private static double linearColor(int channel)
  {
    double value = channel / 255.0;
    if (value <= 0.03928)
      return value / 12.92;
    return Math.pow((value + 0.055) / 1.055, 2.4);
  }

  private static List<Element> elementsWithAndroidKey(Element root, String key)
  {
    List<Element> matches = new ArrayList<>();
    NodeList elements = root.getElementsByTagName("*");
    for (int i = 0; i < elements.getLength(); i++)
    {
      Element element = (Element)elements.item(i);
      if (key.equals(element.getAttributeNS(ANDROID_NS, "key")))
        matches.add(element);
    }
    return matches;
  }

  private static List<Element> directElementChildren(Element parent)
  {
    List<Element> children = new ArrayList<>();
    NodeList nodes = parent.getChildNodes();
    for (int i = 0; i < nodes.getLength(); i++)
    {
      Node node = nodes.item(i);
      if (node instanceof Element)
        children.add((Element)node);
    }
    return children;
  }
}
