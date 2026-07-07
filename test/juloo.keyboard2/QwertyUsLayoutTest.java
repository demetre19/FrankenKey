package juloo.keyboard2;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import java.io.File;
import javax.xml.parsers.DocumentBuilderFactory;
import static org.junit.Assert.*;

public class QwertyUsLayoutTest
{
  @Test
  public void qwerty_us_places_comma_and_period_on_adjacent_thumb_reachable_keys()
      throws Exception
  {
    Document layout = parseLayout("srcs/layouts/latn_qwerty_us.xml");

    assertEquals("Period must be the southwest side label on n.",
        ".", key(layout, "n").getAttribute("sw"));
    assertEquals("Comma must be the southwest side label on m.",
        ",", key(layout, "m").getAttribute("sw"));

    assertNoPunctuationSideLabel(key(layout, "c"));
    assertNoPunctuationSideLabel(key(layout, "v"));
  }

  private static void assertNoPunctuationSideLabel(Element key)
  {
    for (String position : new String[]{"nw", "n", "ne", "w", "e", "sw", "s", "se"})
    {
      String value = key.getAttribute(position);
      assertNotEquals("Period must not remain on " + key.getAttribute("c")
          + "." + position + ".", ".", value);
      assertNotEquals("Comma must not remain on " + key.getAttribute("c")
          + "." + position + ".", ",", value);
    }
  }

  private static Element key(Document layout, String center)
  {
    NodeList keys = layout.getElementsByTagName("key");
    for (int i = 0; i < keys.getLength(); i++)
    {
      Node node = keys.item(i);
      if (node.getNodeType() == Node.ELEMENT_NODE)
      {
        Element key = (Element)node;
        if (center.equals(key.getAttribute("c")))
          return key;
      }
    }
    fail("Missing key with c=\"" + center + "\"");
    return null;
  }

  private static Document parseLayout(String path)
      throws Exception
  {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    return factory.newDocumentBuilder().parse(new File(path));
  }
}
