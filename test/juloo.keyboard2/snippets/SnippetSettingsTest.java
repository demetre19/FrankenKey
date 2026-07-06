package juloo.keyboard2.snippets;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.xml.parsers.DocumentBuilderFactory;
import static org.junit.Assert.*;

public class SnippetSettingsTest
{
  private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
  private static final String SNIPPET_SLOTS_TAG =
      "juloo.keyboard2.snippets.SnippetSlotsPreference";
  private static final String SNIPPET_PREF_SOURCE =
      "srcs/juloo.keyboard2/snippets/SnippetSlotsPreference.java";

  public SnippetSettingsTest() {}

  @Test
  public void settings_xml_exposes_local_snippet_editor_controls()
      throws Exception
  {
    Document settings = parseXml("res/xml/settings.xml");
    Element root = settings.getDocumentElement();

    Element snippetsCategory = directCategoryContainingKey(root,
        "CheckBoxPreference", SnippetStore.PREF_ENABLED);
    assertNotNull("Snippet settings must expose the local enable toggle in a settings category.",
        snippetsCategory);

    Element enable = directChildWithKey(snippetsCategory, "CheckBoxPreference",
        SnippetStore.PREF_ENABLED);
    assertNotNull("Snippet settings must expose the local enable toggle using SnippetStore.PREF_ENABLED.",
        enable);

    assertNull("SnippetSlotsPreference extends PreferenceCategory; nesting it inside another PreferenceCategory crashes Settings on Android.",
        directChildWithTag(snippetsCategory, SNIPPET_SLOTS_TAG));
    Element slots = directChildWithTag(root, SNIPPET_SLOTS_TAG);
    assertNotNull("Snippet settings must expose the custom fixed-slot editor preference as a top-level settings section.",
        slots);

    assertEquals("There must be one snippets enable checkbox in settings.",
        1, countElementsWithKey(root, "CheckBoxPreference", SnippetStore.PREF_ENABLED));
    assertEquals("There must be one custom snippets slot editor in settings.",
        1, countElementsWithTag(root, SNIPPET_SLOTS_TAG));
  }

  @Test
  public void slots_preference_persists_edits_through_snippet_store()
      throws Exception
  {
    String source = readSource(SNIPPET_PREF_SOURCE);
    String loadAndReattach = methodBody(source, "private void load_and_reattach(");
    String changeSlot = methodBody(source, "private void change_slot(");
    String persistSlots = methodBody(source, "private void persist_slots(");

    assertTrue("SnippetSlotsPreference must load visible slots from SnippetStore's private snippet file.",
        loadAndReattach.contains("SnippetStore.loadSlots(getContext())"));
    assertTrue("Saving or clearing one slot must replace by slot index instead of rebuilding/reordering the list.",
        changeSlot.contains("SnippetStore.replaceSlot(_slots, slot)"));
    assertTrue("SnippetSlotsPreference must persist edited phrases through SnippetStore.saveSlots(getContext(), ...).",
        persistSlots.contains("SnippetStore.saveSlots(getContext(), _slots)"));
    assertFalse("SnippetSlotsPreference must not write raw phrases through a SharedPreferences editor.",
        persistSlots.contains(".edit(") || persistSlots.contains("putString(")
        || persistSlots.contains("SharedPreferences.Editor"));
    assertFalse("SnippetSlotsPreference must not address the legacy raw SharedPreferences slots key.",
        persistSlots.contains("SnippetStore.PREF_SLOTS")
        || source.contains("PreferenceManager.getDefaultSharedPreferences"));
  }

  @Test
  public void slots_preference_renders_fixed_indexed_slots_with_generated_preview_labels()
      throws Exception
  {
    String source = readSource(SNIPPET_PREF_SOURCE);

    assertTrue("SnippetSlotsPreference must keep the fixed visible slot count tied to the snippet slot/page constant.",
        containsAny(source, "SnippetStore.DEFAULT_SLOT_COUNT", "SnippetSlot.PAGE_SIZE",
            "SnippetPages.PAGE_SIZE"));
    assertTrue("Each rendered row must keep the slot index visible/editable rather than collapsing empty slots.",
        source.contains("getIndex()"));
    assertTrue("Slot previews must come from SnippetSlot.getDisplayLabel() so empty, phrase, and custom labels share one contract.",
        source.contains(".getDisplayLabel()"));
  }

  @Test
  public void slots_preference_has_edit_clear_and_custom_icon_label_paths()
      throws Exception
  {
    String source = readSource(SNIPPET_PREF_SOURCE);
    String lowerSource = source.toLowerCase(Locale.ROOT);

    assertTrue("Tapping a visible slot must open an editor path.",
        containsLineWithAll(source, "setOnPreferenceClickListener")
        || containsLineWithAll(source, "setOnClickListener")
        || containsLineWithAll(source, "AlertDialog"));
    assertTrue("The slot editor must provide a clear path that writes an empty slot.",
        lowerSource.contains("clear") && source.contains("SnippetSlot.of("));
    assertTrue("The editor must read existing custom label text into the edit path.",
        source.contains("getCustomLabel()"));
    assertTrue("The editor must read and save the icon-label flag.",
        source.contains("isIconLabel()") && lowerSource.contains("icon"));
    assertTrue("The icon-label option must be exposed as a checkbox or checkable editor control.",
        source.contains("CheckBox") || source.contains("setChecked("));
  }

  private static String readSource(String path)
      throws Exception
  {
    Path sourcePath = Paths.get(path);
    assertTrue("Expected production source file: " + path, Files.isRegularFile(sourcePath));
    return new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
  }

  private static String methodBody(String source, String methodSignature)
  {
    int methodIndex = source.indexOf(methodSignature);
    assertTrue("Expected method in source: " + methodSignature, methodIndex >= 0);
    int openBrace = source.indexOf('{', methodIndex);
    assertTrue("Expected method body for: " + methodSignature, openBrace >= 0);

    int depth = 0;
    for (int i = openBrace; i < source.length(); i++)
    {
      char c = source.charAt(i);
      if (c == '{')
        depth++;
      else if (c == '}')
      {
        depth--;
        if (depth == 0)
          return source.substring(openBrace + 1, i);
      }
    }
    fail("Expected closing brace for: " + methodSignature);
    return "";
  }

  private static Document parseXml(String path)
      throws Exception
  {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    return factory.newDocumentBuilder().parse(new File(path));
  }

  private static Element directCategoryContainingKey(Element root,
      String childTag, String key)
  {
    for (Element category : directElementChildren(root))
      if ("PreferenceCategory".equals(category.getTagName())
          && directChildWithKey(category, childTag, key) != null)
        return category;
    return null;
  }

  private static Element directCategoryContaining(Element root, String childTag)
  {
    for (Element category : directElementChildren(root))
      if ("PreferenceCategory".equals(category.getTagName())
          && directChildWithTag(category, childTag) != null)
        return category;
    return null;
  }

  private static Element directChildWithKey(Element parent, String tagName,
      String key)
  {
    for (Element element : directElementChildren(parent))
      if (tagName.equals(element.getTagName())
          && key.equals(element.getAttributeNS(ANDROID_NS, "key")))
        return element;
    return null;
  }

  private static Element directChildWithTag(Element parent, String tagName)
  {
    for (Element element : directElementChildren(parent))
      if (tagName.equals(element.getTagName()))
        return element;
    return null;
  }

  private static int countElementsWithTag(Element root, String tagName)
  {
    int count = 0;
    if (tagName.equals(root.getTagName()))
      count++;
    for (Element child : directElementChildren(root))
      count += countElementsWithTag(child, tagName);
    return count;
  }

  private static int countElementsWithKey(Element root, String tagName,
      String key)
  {
    int count = 0;
    if (tagName.equals(root.getTagName())
        && key.equals(root.getAttributeNS(ANDROID_NS, "key")))
      count++;
    for (Element child : directElementChildren(root))
      count += countElementsWithKey(child, tagName, key);
    return count;
  }

  private static List<Element> directElementChildren(Element parent)
  {
    NodeList nodes = parent.getChildNodes();
    List<Element> elements = new ArrayList<>();
    for (int i = 0; i < nodes.getLength(); i++)
    {
      Node node = nodes.item(i);
      if (node.getNodeType() == Node.ELEMENT_NODE)
        elements.add((Element)node);
    }
    return elements;
  }

  private static boolean containsAny(String source, String... parts)
  {
    for (String part : parts)
      if (source.contains(part))
        return true;
    return false;
  }

  private static boolean containsLineWithAll(String source, String... parts)
  {
    List<String> lines = Arrays.asList(source.split("\\R"));
    for (String line : lines)
    {
      String lowerLine = line.toLowerCase(Locale.ROOT);
      boolean hasAll = true;
      for (String part : parts)
        hasAll &= lowerLine.contains(part.toLowerCase(Locale.ROOT));
      if (hasAll)
        return true;
    }
    return false;
  }
}
