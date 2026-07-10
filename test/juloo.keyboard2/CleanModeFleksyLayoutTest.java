package juloo.keyboard2;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
public class CleanModeFleksyLayoutTest
{
  private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
  private static final String CLEAN_TEXT = "res/xml/clean_text.xml";
  private static final String BOTTOM_ROW = "res/xml/bottom_row.xml";
  private static final String CLEAN_NUMERIC = "res/xml/clean_numeric.xml";
  private static final String CLEAN_SYMBOLS = "res/xml/clean_symbols.xml";
  private static final String UNTRANSLATED_STRINGS = "res/values/untranslated_strings.xml";
  private static final String[] BOTTOM_RIGHT_DICTATION_VARIATIONS = {
    BOTTOM_ROW,
    CLEAN_TEXT,
    CLEAN_NUMERIC,
    CLEAN_SYMBOLS,
    "res/xml/clipboard_bottom_row.xml",
    "res/xml/emoji_bottom_row.xml",
    "res/xml/gif_bottom_row.xml",
    "res/xml/greekmath.xml",
    "res/xml/numeric.xml",
    "res/xml/numeric_landscape.xml",
    "res/xml/numpad.xml",
    "res/xml/pin.xml",
    "res/xml/pin_landscape.xml",
  };

  public CleanModeFleksyLayoutTest() {}

  @Test
  public void clean_text_layout_is_fleksy_clean_letter_rows()
      throws Exception
  {
    Document layout = parseLayout(CLEAN_TEXT);
    Element keyboard = layout.getDocumentElement();
    List<Element> rows = directRows(keyboard);

    assertEquals("Clean text layout resource must expose Fleksy's three letter rows plus its own bottom row.",
        4, rows.size());
    assertEquals("Clean text top row must be the plain Fleksy QWERTY letters.",
        Arrays.asList("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        primaryNames(directKeys(rows.get(0))));
    assertEquals("Clean text home row must be the plain Fleksy ASDF row.",
        Arrays.asList("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        primaryNames(directKeys(rows.get(1))));
    assertEquals("Clean text bottom row must keep Fleksy's shift/letters/backspace shape.",
        Arrays.asList("shift", "z", "x", "c", "v", "b", "n", "m", "backspace"),
        primaryNames(directKeys(rows.get(2))));

    assertEquals("Clean text bottom row must be Fleksy's 123/Fn/space/punctuation/enter row with no Ctrl or arrow cluster.",
        Arrays.asList("switch_numeric", "fn", "space", ".", "enter"),
        primaryNames(directKeys(rows.get(3))));
    assertEquals("Fleksy 123 top-left swipe must retain the numeric-page action without rendering a secondary legend.",
        "hide switch_number", directKeys(rows.get(3)).get(0).getAttribute("key1"));
    assertEquals("Fleksy p top-right swipe must retain the clean-mode toggle without rendering a cog legend.",
        "hide toggle_clean_mode", key(rows.get(0), "p").getAttribute("key2"));
    assertEquals("Q top-left swipe must retain Settings access without rendering a secondary legend.",
        "hide config", key(rows.get(0), "q").getAttribute("key1"));
    assertEquals("D right swipe must retain Escape without rendering a secondary legend.",
        "hide esc", key(rows.get(1), "d").getAttribute("key4"));
    assertEquals("F right swipe must retain input-method selection without rendering a secondary legend.",
        "hide change_method", key(rows.get(1), "f").getAttribute("key4"));
    assertEquals("G right swipe must retain Compose without rendering a secondary legend.",
        "hide compose", key(rows.get(1), "g").getAttribute("key4"));
    assertEquals("H right swipe must retain Alt without rendering a secondary legend.",
        "hide alt", key(rows.get(1), "h").getAttribute("key4"));
    assertEquals("J right swipe must retain Meta without rendering a secondary legend.",
        "hide meta", key(rows.get(1), "j").getAttribute("key4"));
    assertEquals("Backspace left swipe must retain word deletion without rendering a secondary legend.",
        "hide delete_word", key(rows.get(2), "backspace").getAttribute("key3"));
    assertEquals("The hidden number-page gesture must open the normal numeric page from the Fleksy 123 key.",
        KeyValue.Event.SWITCH_NUMERIC,
        KeyValue.getSpecialKeyByName("switch_number").getEvent());
    assertEquals("The hidden clean-mode gesture must still toggle between Fleksy and FrankenKey layouts.",
        KeyValue.Event.TOGGLE_CLEAN_MODE,
        KeyValue.getSpecialKeyByName("toggle_clean_mode").getEvent());

    assertCleanLetterKey(rows.get(0), "q", "key1");
    assertCleanLetterKey(rows.get(0), "w");
    assertCleanLetterKey(rows.get(0), "e");
    assertCleanLetterKey(rows.get(0), "r");
    assertCleanLetterKey(rows.get(0), "t");
    assertCleanLetterKey(rows.get(0), "y");
    assertCleanLetterKey(rows.get(0), "u");
    assertCleanLetterKey(rows.get(0), "i");
    assertCleanLetterKey(rows.get(0), "o");
    assertCleanLetterKey(rows.get(0), "p", "key2");
    assertCleanLetterKey(rows.get(1), "a", "key5");
    assertCleanLetterKey(rows.get(1), "s", "key5");
    assertCleanLetterKey(rows.get(1), "d", "key4", "key5");
    assertCleanLetterKey(rows.get(1), "f", "key4", "key5");
    assertCleanLetterKey(rows.get(1), "g", "key4", "key5");
    assertCleanLetterKey(rows.get(1), "h", "key4", "key5");
    assertCleanLetterKey(rows.get(1), "j", "key4", "key5");
    assertCleanLetterKey(rows.get(1), "k", "key5");
    assertCleanLetterKey(rows.get(1), "l", "key5");
    assertCleanLetterKey(rows.get(2), "z", "key1");
    assertCleanLetterKey(rows.get(2), "x", "key1");
    assertCleanLetterKey(rows.get(2), "c", "key1");
    assertCleanLetterKey(rows.get(2), "v", "key1");
    assertCleanLetterKey(rows.get(2), "b");
    assertCleanLetterKey(rows.get(2), "n", "key3");
    assertCleanLetterKey(rows.get(2), "m", "key3");
  }

  @Test
  public void clean_text_bottom_letter_row_keeps_edit_shortcuts()
      throws Exception
  {
    Element bottomRow = directRows(parseLayout(CLEAN_TEXT).getDocumentElement()).get(2);

    assertEquals("z must keep the select-all shortcut on the bottom letter row.",
        "loc selectAll", key(bottomRow, "z").getAttribute("key1"));
    assertEquals("x must keep the cut shortcut on the bottom letter row.",
        "loc cut", key(bottomRow, "x").getAttribute("key1"));
    assertEquals("c must keep the copy shortcut on the bottom letter row.",
        "loc copy", key(bottomRow, "c").getAttribute("key1"));
    assertEquals("v must keep the paste shortcut on the bottom letter row.",
        "loc paste", key(bottomRow, "v").getAttribute("key1"));
  }

  @Test
  public void frankenkey_bottom_row_keeps_123_numeric_switch()
      throws Exception
  {
    Element row = parseLayout(BOTTOM_ROW).getDocumentElement();
    Element numericSwitch = keyWithAttribute(row, "key4", "switch_numeric");

    assertEquals("FrankenKey bottom row must retain a 123 numeric-layer switch while clean mode is optional.",
        "ctrl", numericSwitch.getAttribute("key0"));
    KeyValue switchNumeric = KeyValue.getSpecialKeyByName("switch_numeric");
    assertNotNull("switch_numeric must resolve to the visible 123 key value.",
        switchNumeric);
    assertEquals("FrankenKey numeric switch must render with Fleksy's compact 123 label.",
        "123", switchNumeric.getString());
  }

  @Test
  public void frankenkey_bottom_row_exposes_number_entry_and_layout_toggle_shortcuts()
      throws Exception
  {
    Element row = parseLayout(BOTTOM_ROW).getDocumentElement();
    Element ctrl = key(row, "ctrl");
    Element space = key(row, "space");

    assertEquals("FrankenKey Ctrl/123 key must offer the configured numeric-only field layout on upward swipe while preserving 123 on lower-right.",
        "switch_number_entry", ctrl.getAttribute("key7"));
    assertEquals("FrankenKey spacebar top-right swipe must toggle back to the Fleksy layout.",
        "toggle_clean_mode", space.getAttribute("key2"));
    assertNotNull("switch_number_entry must resolve to a visible event key.",
        KeyValue.getSpecialKeyByName("switch_number_entry"));
    assertNotNull("toggle_clean_mode must resolve to a visible event key.",
        KeyValue.getSpecialKeyByName("toggle_clean_mode"));
  }

  @Test
  public void numeric_only_field_layout_setting_is_easy_to_find_under_layout()
      throws Exception
  {
    Document settings = parseLayout("res/xml/settings.xml");
    Element layout = preferenceCategory(settings, "@string/pref_category_layout");
    Element behavior = preferenceCategory(settings, "@string/pref_category_behavior");

    assertEquals("number_entry_layout",
        directElementChildren(layout).get(2).getAttributeNS(ANDROID_NS, "key"));
    assertEquals("The numeric-only field layout setting must not stay buried under Behavior.",
        -1, indexOfDirectPreference(behavior, "number_entry_layout"));
  }


  @Test
  public void clean_text_bottom_row_matches_frankenkey_voice_emoji_and_gif_corner_positions()
      throws Exception
  {
    Element cleanRow = directRows(parseLayout(CLEAN_TEXT).getDocumentElement()).get(3);
    Element frankenRow = parseLayout(BOTTOM_ROW).getDocumentElement();
    List<Element> cleanKeys = directKeys(cleanRow);
    List<Element> frankenKeys = directKeys(frankenRow);
    Element frankenEnter = key(frankenRow, "enter");
    Element cleanEnter = key(cleanRow, "enter");

    assertEquals("Clean/Fleksy Fn key must stay in the same bottom-row slot as FrankenKey Fn.",
        indexOfKey(frankenKeys, "fn"), indexOfKey(cleanKeys, "fn"));
    assertEquals("FrankenKey Fn lower-left corner is the emoji switch reference.",
        "switch_emoji", key(frankenRow, "fn").getAttribute("key3"));
    assertEquals("Clean/Fleksy Fn must expose emoji from the same lower-left corner.",
        "switch_emoji", key(cleanRow, "fn").getAttribute("key3"));

    assertEquals("Clean/Fleksy Enter key must stay in the same bottom-row slot as FrankenKey Enter.",
        indexOfKey(frankenKeys, "enter"), indexOfKey(cleanKeys, "enter"));
    assertEquals("FrankenKey Enter top-left corner is the voice typing reference.",
        "loc voice_typing", frankenEnter.getAttribute("key1"));
    assertEquals("Clean/Fleksy Enter must expose voice typing from the same top-left corner.",
        frankenEnter.getAttribute("key1"), cleanEnter.getAttribute("key1"));
    assertEquals("Clean/Fleksy Enter must preserve the same Go/Done action corner as FrankenKey Enter.",
        frankenEnter.getAttribute("key2"), cleanEnter.getAttribute("key2"));
    assertEquals("FrankenKey Enter lower-right corner is the GIF reference.",
        "gif", frankenEnter.getAttribute("key4"));
    assertEquals("Clean/Fleksy Enter must expose GIF from the same lower-right corner.",
        "gif", cleanEnter.getAttribute("key4"));
  }

  @Test
  public void every_bottom_right_action_variation_has_dictation_on_top_left_corner()
      throws Exception
  {
    for (String path : BOTTOM_RIGHT_DICTATION_VARIATIONS)
    {
      Element bottomRight = bottomRightKey(path);
      assertEquals(path + " bottom-right key must expose dictation in the top-left corner regardless of whether it renders as Return, Go, Done, symbols, PIN, emoji, clipboard, GIF, or numpad.",
          "loc voice_typing", bottomRight.getAttribute("key1"));
    }
  }

  @Test
  public void clean_numeric_layout_matches_fleksy_symbol_rows()
      throws Exception
  {
    Document layout = parseLayout(CLEAN_NUMERIC);
    List<Element> rows = directRows(layout.getDocumentElement());

    assertEquals("Clean numeric must keep Fleksy's four-row symbol page shape.",
        4, rows.size());
    assertEquals("Clean numeric row 1 must be the Fleksy digit row.",
        Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        primaryLabels(rows.get(0)));
    assertEquals("Clean numeric row 2 must match Fleksy's arithmetic/punctuation order.",
        Arrays.asList("&", "/", ":", ";", "(", ")", "-", "+", "$"),
        primaryLabels(rows.get(1)));
    assertEquals("Clean numeric row 3 must expose the extra-symbol switch, punctuation cluster, and trailing Backspace in order.",
        Arrays.asList("=<", ".", ",", "?", "!", "'", "\"", "*", "#", "@", "backspace"),
        primaryLabels(rows.get(2)));
    assertBottomRowMirrorsFleksy(CLEAN_NUMERIC, rows.get(3));
  }

  @Test
  public void clean_symbols_layout_matches_fleksy_extra_symbol_rows()
      throws Exception
  {
    Document layout = parseLayout(CLEAN_SYMBOLS);
    List<Element> rows = directRows(layout.getDocumentElement());

    assertEquals("Clean extra symbols must keep Fleksy's four-row symbol page shape.",
        4, rows.size());
    assertEquals("Clean extra symbols row 1 must match Fleksy's order.",
        Arrays.asList("`", "~", "%", "=", "{", "}", "<", ">", "^", "°"),
        primaryLabels(rows.get(0)));
    assertEquals("Clean extra symbols row 2 must match Fleksy's currency/operator order.",
        Arrays.asList("—", "|", "\\", "_", "[", "]", "€", "£", "¥", "₹"),
        primaryLabels(rows.get(1)));
    assertEquals("Clean extra symbols row 3 must return to numeric symbols, keep the punctuation cluster, and end with Backspace.",
        Arrays.asList("switch_numeric", ".", ",", "?", "!", "'", "\"", ":", ";", ".", "backspace"),
        primaryLabels(rows.get(2)));
    assertBottomRowMirrorsFleksy(CLEAN_SYMBOLS, rows.get(3));
  }

  @Test
  public void launcher_source_code_link_points_to_frankenkey_repo()
      throws Exception
  {
    Element repoUrl = stringResource(parseLayout(UNTRANSLATED_STRINGS),
        "launcher_repo_url");

    assertEquals("The launcher source-code action must open the FrankenKey fork, not the upstream Unexpected Keyboard repo.",
        "https://github.com/demetre19/FrankenKey",
        repoUrl.getTextContent().trim());
  }

  @Test
  public void clean_symbol_middle_rows_wire_fleksy_left_swipe_word_delete()
      throws Exception
  {
    assertMiddleRowLeftSwipeDeletesWords(CLEAN_NUMERIC);
    assertMiddleRowLeftSwipeDeletesWords(CLEAN_SYMBOLS);

    KeyValue deleteWords = KeyValue.getKeyByName("delete_words_left");
    assertEquals("Clean Fleksy delete gesture must use the gradual word-delete slider, not a Backspace key repeat.",
        KeyValue.Kind.Slider, deleteWords.getKind());
    assertEquals(KeyValue.Slider.Delete_words_left, deleteWords.getSlider());
  }

  @Test
  public void keyboard_layout_keeps_snippet_row_above_keyboard_view()
      throws Exception
  {
    Document layout = parseLayout("res/layout/keyboard.xml");
    Element root = layout.getDocumentElement();
    List<Element> children = directElementChildren(root);

    int snippetRow = indexOfTag(children, "juloo.keyboard2.snippets.SnippetRowView");
    int keyboardView = indexOfTag(children, "juloo.keyboard2.Keyboard2View");

    assertEquals("Snippet row must remain a direct sibling immediately above Keyboard2View so Clean mode layout swaps cannot cover snippets.",
        keyboardView - 1, snippetRow);
    assertEquals("@+id/snippet_row",
        children.get(snippetRow).getAttributeNS(ANDROID_NS, "id"));
    assertEquals("@+id/keyboard_view",
        children.get(keyboardView).getAttributeNS(ANDROID_NS, "id"));
  }


  private static void assertCleanLetterKey(Element row, String key0,
      String... allowedSideKeys)
  {
    Element key = key(row, key0);
    for (String side : new String[]{"key1", "key2", "key3", "key4",
        "key5", "key6", "key7", "key8"})
    {
      if (contains(allowedSideKeys, side))
        continue;
      assertEquals("Clean/Fleksy text key " + key0 + " must not carry a computer-heavy "
          + side + " side label.", "", key.getAttribute(side));
    }
  }

  private static boolean contains(String[] values, String needle)
  {
    for (String value : values)
      if (needle.equals(value))
        return true;
    return false;
  }

  private static Element key(Element row, String key0)
  {
    return keyWithAttribute(row, "key0", key0);
  }

  private static Element keyWithAttribute(Element parent, String attribute,
      String value)
  {
    for (Element key : directKeys(parent))
      if (value.equals(key.getAttribute(attribute)))
        return key;
    fail("Missing key with " + attribute + "=\"" + value + "\"");
    return null;
  }

  private static int indexOfKey(List<Element> keys, String key0)
  {
    for (int i = 0; i < keys.size(); i++)
      if (key0.equals(keys.get(i).getAttribute("key0")))
        return i;
    fail("Missing key0=\"" + key0 + "\"");
    return -1;
  }


  private static void assertMiddleRowLeftSwipeDeletesWords(String path)
      throws Exception
  {
    List<Element> rows = directRows(parseLayout(path).getDocumentElement());
    Element middleRow = rows.get(1);
    for (Element key : directKeys(middleRow))
      assertEquals(path + " middle-row key " + key.getAttribute("key0")
          + " must wire Fleksy's left-swipe delete gesture through the gradual word-delete slider without showing a corner label.",
          "hide delete_words_left", key.getAttribute("key5"));
  }

  private static void assertBottomRowMirrorsFleksy(String path, Element row)
  {
    List<Element> keys = directKeys(row);
    assertEquals(path + " bottom row must expose text, the large voice key, space, period, and enter in order.",
        Arrays.asList("switch_text", "voice_typing", "space", ".", "enter"),
        primaryNames(keys));

    Element enter = key(row, "enter");
    assertEquals(path + " standalone voice key must remain immediately to the right of ABC.",
        "voice_typing", keys.get(1).getAttribute("key0"));
    assertEquals(path + " Enter top-left corner must also open voice typing.",
        "loc voice_typing", enter.getAttribute("key1"));
    assertEquals(path + " Enter must still be the Go/Done action key.",
        "action", enter.getAttribute("key2"));

    assertWidthBetween(path, keys.get(0), 1.3f, 2.0f);
    assertWidthBetween(path, keys.get(1), 0.8f, 1.4f);
    assertWidthBetween(path, keys.get(2), 4.0f, 5.5f);
    assertWidthBetween(path, keys.get(3), 0.8f, 1.4f);
    assertWidthBetween(path, keys.get(4), 1.3f, 2.0f);
    assertTrue(path + " space bar must remain visibly wider than the adjacent control keys.",
        width(keys.get(2)) > width(keys.get(0)) * 2
        && width(keys.get(2)) > width(keys.get(4)) * 2);
  }

  private static void assertWidthBetween(String path, Element key,
      float min, float max)
  {
    float width = width(key);
    assertTrue(path + " key " + key.getAttribute("key0")
        + " width " + width + " must stay in Fleksy's compact-control range.",
        width >= min && width <= max);
  }

  private static float width(Element key)
  {
    String width = key.getAttribute("width");
    assertFalse("Every clean bottom-row key must declare width explicitly.",
        width.isEmpty());
    return Float.parseFloat(width);
  }

  private static List<String> primaryLabels(Element row)
  {
    return displayNames(primaryNames(directKeys(row)));
  }

  private static List<String> primaryNames(List<Element> keys)
  {
    List<String> names = new ArrayList<>();
    for (Element key : keys)
      names.add(key.getAttribute("key0"));
    return names;
  }

  private static List<String> displayNames(List<String> names)
  {
    List<String> labels = new ArrayList<>();
    for (String name : names)
      labels.add(displayName(name));
    return labels;
  }

  private static String displayName(String name)
  {
    if ("switch_clean_symbols".equals(name))
    {
      KeyValue key = KeyValue.getSpecialKeyByName(name);
      assertNotNull("switch_clean_symbols must resolve to the clean extra-symbol page key.",
          key);
      assertEquals("Clean numeric extra-symbol switch should render as Fleksy's =< key.",
          "=<", key.getString());
      return key.getString();
    }
    if (name.startsWith("\\") && name.length() > 1)
      return name.substring(1);
    return name;
  }

  private static Element bottomRightKey(String path)
      throws Exception
  {
    Element root = parseLayout(path).getDocumentElement();
    List<Element> rows;
    if ("row".equals(root.getTagName()))
      rows = Arrays.asList(root);
    else
      rows = directRows(root);
    assertFalse(path + " must have at least one row.", rows.isEmpty());
    List<Element> keys = directKeys(rows.get(rows.size() - 1));
    assertFalse(path + " final row must have at least one key.", keys.isEmpty());
    return keys.get(keys.size() - 1);
  }

  private static Element stringResource(Document resources, String name)
  {
    NodeList strings = resources.getElementsByTagName("string");
    for (int i = 0; i < strings.getLength(); i++)
    {
      Element string = (Element)strings.item(i);
      if (name.equals(string.getAttribute("name")))
        return string;
    }
    fail("Missing string resource name=\"" + name + "\"");
    return null;
  }

  private static Document parseLayout(String path)
      throws Exception
  {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setNamespaceAware(true);
    return factory.newDocumentBuilder().parse(new File(path));
  }

  private static List<Element> directRows(Element keyboard)
  {
    List<Element> rows = new ArrayList<>();
    for (Element child : directElementChildren(keyboard))
      if ("row".equals(child.getTagName()))
        rows.add(child);
    return rows;
  }

  private static List<Element> directKeys(Element row)
  {
    List<Element> keys = new ArrayList<>();
    for (Element child : directElementChildren(row))
      if ("key".equals(child.getTagName()))
        keys.add(child);
    return keys;
  }

  private static List<Element> directElementChildren(Element parent)
  {
    NodeList children = parent.getChildNodes();
    List<Element> elements = new ArrayList<>();
    for (int i = 0; i < children.getLength(); i++)
    {
      Node child = children.item(i);
      if (child instanceof Element)
        elements.add((Element)child);
    }
    return elements;
  }

  private static int indexOfTag(List<Element> elements, String tagName)
  {
    for (int i = 0; i < elements.size(); i++)
      if (tagName.equals(elements.get(i).getTagName()))
        return i;
    fail("Missing direct child " + tagName);
    return -1;
  }

  private static Element preferenceCategory(Document settings, String title)
  {
    List<Element> categories = directElementChildren(settings.getDocumentElement());
    for (Element category : categories)
      if (title.equals(category.getAttributeNS(ANDROID_NS, "title")))
        return category;
    fail("Missing preference category " + title);
    return null;
  }

  private static int indexOfDirectPreference(Element parent, String key)
  {
    List<Element> children = directElementChildren(parent);
    for (int i = 0; i < children.size(); i++)
      if (key.equals(children.get(i).getAttributeNS(ANDROID_NS, "key")))
        return i;
    return -1;
  }

}
