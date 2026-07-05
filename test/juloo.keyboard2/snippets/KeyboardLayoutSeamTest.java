package juloo.keyboard2.snippets;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import static org.junit.Assert.*;

public class KeyboardLayoutSeamTest
{
  private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

  public KeyboardLayoutSeamTest() {}

  @Test
  public void keyboard_view_remains_direct_child_with_insertable_sibling_seam()
      throws Exception
  {
    Document layout = parseLayout("res/layout/keyboard.xml");
    Element root = layout.getDocumentElement();

    assertEquals("The keyboard layout root must stay the direct vertical parent for rows.",
        "LinearLayout", root.getTagName());
    assertEquals("A row inserted before Keyboard2View only stays above it if the parent is vertical.",
        "vertical", root.getAttributeNS(ANDROID_NS, "orientation"));

    List<Element> rows = directElementChildren(root);
    int keyboardRowIndex = indexOfTag(rows, "juloo.keyboard2.Keyboard2View");

    assertTrue("Keyboard2View must be a direct child so an additive row can be inserted before it without wrapping or replacing the baseline view.",
        keyboardRowIndex >= 0);
    assertEquals("The baseline Keyboard2View id is the runtime lookup contract that must survive the seam.",
        "@+id/keyboard_view",
        rows.get(keyboardRowIndex).getAttributeNS(ANDROID_NS, "id"));
    assertTrue("There must be a direct sibling position before Keyboard2View for the additive row.",
        keyboardRowIndex > 0);
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
}
