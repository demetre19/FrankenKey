package juloo.keyboard2.snippets;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import static org.junit.Assert.*;

public class KeyboardLayoutSeamTest
{
  private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
  private static final String KEYBOARD_VIEW_TAG = "juloo.keyboard2.Keyboard2View";
  private static final String SNIPPET_ROW_TAG = "juloo.keyboard2.snippets.SnippetRowView";

  public KeyboardLayoutSeamTest() {}

  @Test
  public void snippet_row_is_direct_sibling_immediately_above_keyboard_view()
      throws Exception
  {
    Document layout = parseLayout("res/layout/keyboard.xml");
    Element root = layout.getDocumentElement();

    assertEquals("The keyboard layout root must stay the direct vertical parent for rows.",
        "LinearLayout", root.getTagName());
    assertEquals("A row inserted before Keyboard2View only stays above it if the parent is vertical.",
        "vertical", root.getAttributeNS(ANDROID_NS, "orientation"));

    List<Element> rows = directElementChildren(root);
    int snippetRowIndex = indexOfTag(rows, SNIPPET_ROW_TAG);
    int keyboardRowIndex = indexOfTag(rows, KEYBOARD_VIEW_TAG);

    assertTrue("SnippetRowView must be an additive direct child of the keyboard root.",
        snippetRowIndex >= 0);
    assertTrue("Keyboard2View must remain a direct child; the snippet row must not wrap or replace it.",
        keyboardRowIndex >= 0);
    assertEquals("SnippetRowView must be the direct sibling immediately above Keyboard2View.",
        keyboardRowIndex - 1, snippetRowIndex);
    assertEquals("SnippetRowView id is the runtime lookup contract.",
        "@+id/snippet_row",
        rows.get(snippetRowIndex).getAttributeNS(ANDROID_NS, "id"));
    assertEquals("Keyboard2View must retain the runtime lookup id.",
        "@+id/keyboard_view",
        rows.get(keyboardRowIndex).getAttributeNS(ANDROID_NS, "id"));
    assertEquals("The layout must contain exactly one Keyboard2View.",
        1, countTag(rows, KEYBOARD_VIEW_TAG));
  }

  @Test
  public void snippet_row_uses_shared_page_size_constant()
      throws Exception
  {
    String source = readSource("srcs/juloo.keyboard2/snippets/SnippetRowView.java");

    assertTrue("SnippetRowView must use SnippetPages.PAGE_SIZE so runtime slots stay in seven-control pages.",
        source.contains("SnippetPages.PAGE_SIZE"));
  }

  @Test
  public void keyboard_service_finds_and_refreshes_snippet_row()
      throws Exception
  {
    String source = readSource("srcs/juloo.keyboard2/Keyboard2.java");
    String createKeyboardView = methodBody(source, "private void create_keyboard_view(");
    String refreshConfig = methodBody(source, "private void refresh_config(");

    assertTrue("create_keyboard_view() must bind the snippet row by R.id.snippet_row.",
        containsLineWithAll(createKeyboardView, "findViewById", "R.id.snippet_row"));
    assertTrue("refresh_config() must refresh the snippet row when keyboard config changes.",
        containsLineWithAll(refreshConfig, "snippet", "refresh_config("));
  }

  @Test
  public void keyboard_service_uses_secure_editor_snippet_policy_for_snippet_row()
      throws Exception
  {
    String source = readSource("srcs/juloo.keyboard2/Keyboard2.java");
    String refreshConfig = methodBody(source, "private void refresh_config(");

    int snippetRefresh = refreshConfig.indexOf("_snippet_row_view.refresh_config(");
    assertTrue("refresh_config() must refresh the snippet row.",
        snippetRefresh >= 0);

    int snippetPolicy = refreshConfig.indexOf(
        "_config.editor_config.should_show_snippet_row", snippetRefresh);
    int clickListener = refreshConfig.indexOf("slot ->", snippetRefresh);

    assertTrue("SnippetRowView must receive the editor snippet policy as its secure-editor visibility gate.",
        snippetPolicy > snippetRefresh);
    assertTrue("The snippet policy must be the visibility argument, not dead code elsewhere in refresh_config().",
        clickListener > snippetPolicy);
  }

  @Test
  public void snippet_row_removes_snippets_when_editor_disallows_user_visible_text()
      throws Exception
  {
    String source = readSource("srcs/juloo.keyboard2/snippets/SnippetRowView.java");
    String refreshConfig = methodBody(source, "public void refresh_config(");

    int denialGate = refreshConfig.indexOf("!editorAllowsText");
    int enabledGate = refreshConfig.indexOf("SnippetStore.isEnabled", denialGate);
    int loadSlots = refreshConfig.indexOf("SnippetStore.loadSlots");
    int removePages = refreshConfig.indexOf("_pages.removeAllViews()", denialGate);
    int hideRow = refreshConfig.indexOf("setVisibility(GONE)", denialGate);
    int returnFromDeniedEditor = refreshConfig.indexOf("return;", denialGate);

    assertTrue("SnippetRowView must have an explicit editorAllowsText denial gate.",
        denialGate >= 0);
    assertTrue("The local enable toggle may share the denial branch but must be checked before any slot load.",
        enabledGate >= 0 && enabledGate < loadSlots);
    assertTrue("Denied editors must not load persisted snippet phrases.",
        loadSlots > 0);
    assertTrue("Denied editors must remove already-rendered snippet pages before any slot load.",
        removePages > denialGate && removePages < loadSlots);
    assertTrue("Denied editors must hide the snippet row before any slot load.",
        hideRow > denialGate && hideRow < loadSlots);
    assertTrue("Denied editors must return before any persisted snippet phrases are read.",
        returnFromDeniedEditor > hideRow && returnFromDeniedEditor < loadSlots);
  }

  private static String readSource(String path)
      throws Exception
  {
    return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
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

  private static boolean containsLineWithAll(String source, String... parts)
  {
    String[] lines = source.split("\\R");
    for (String line : lines)
    {
      String lowerLine = line.toLowerCase();
      boolean hasAll = true;
      for (String part : parts)
        hasAll &= lowerLine.contains(part.toLowerCase());
      if (hasAll)
        return true;
    }
    return false;
  }

  private static Document parseLayout(String path)
      throws Exception
  {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    return factory.newDocumentBuilder().parse(new File(path));
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

  private static int indexOfTag(List<Element> elements, String tagName)
  {
    for (int i = 0; i < elements.size(); i++)
      if (tagName.equals(elements.get(i).getTagName()))
        return i;
    return -1;
  }

  private static int countTag(List<Element> elements, String tagName)
  {
    int count = 0;
    for (Element element : elements)
      if (tagName.equals(element.getTagName()))
        count++;
    return count;
  }
}
