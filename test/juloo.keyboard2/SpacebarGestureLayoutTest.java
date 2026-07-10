package juloo.keyboard2;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import static org.junit.Assert.*;

public class SpacebarGestureLayoutTest
{
  private static final String CLEAN = "res/xml/clean_text.xml";
  private static final String DENSE_LAYOUT =
    "srcs/layouts/latn_qwerty_us.xml";
  private static final String DENSE_BOTTOM_ROW = "res/xml/bottom_row.xml";

  @Test
  public void clean_spacebar_maps_clipboard_emoji_and_gif_to_requested_corners()
      throws Exception
  {
    Element keyboard = parse(CLEAN).getDocumentElement();
    List<Element> rows = directChildren(keyboard, "row");
    List<Element> keys = directChildren(rows.get(rows.size() - 1), "key");
    Element space = key(keys, "key0", "space");

    assertEquals("Clean spacebar top-left must open clipboard history.",
        "switch_clipboard", space.getAttribute("key1"));
    assertEquals("Clean spacebar top-right must switch Everyday and Coding layouts.",
        "toggle_clean_mode", space.getAttribute("key2"));
    assertEquals("Clean spacebar bottom-left must open emoji search.",
        "switch_emoji", space.getAttribute("key3"));
    assertEquals("Clean spacebar bottom-right must open GIF search.",
        "gif", space.getAttribute("key4"));
    assertFalse("Voice typing belongs on the far-right Done/Enter key, never on the spacebar.",
        attributes(space).contains("voice_typing"));
  }

  @Test
  public void welcome_animation_uses_the_spacebars_real_corner_labels()
  {
    assertEquals("Clipboard icon must match the keyboard key label.",
        KeyValue.getSpecialKeyByName("switch_clipboard").getString(),
        SpacebarGestureView.ICONS[0]);
    assertEquals("Mode icon must match the keyboard key label.",
        KeyValue.getSpecialKeyByName("toggle_clean_mode").getString(),
        SpacebarGestureView.ICONS[1]);
    assertEquals("Emoji icon must match the keyboard key label.",
        KeyValue.getSpecialKeyByName("switch_emoji").getString(),
        SpacebarGestureView.ICONS[2]);
    assertEquals("GIF label must match the keyboard key label.",
        KeyValue.getSpecialKeyByName("gif").getString(),
        SpacebarGestureView.ICONS[3]);
  }

  @Test
  public void dense_spacebar_inherited_bottom_row_uses_the_same_corner_mapping()
      throws Exception
  {
    Element dense = parse(DENSE_LAYOUT).getDocumentElement();
    assertNotEquals("Dense QWERTY must continue inheriting the shared FrankenKey bottom row.",
        "false", dense.getAttribute("bottom_row"));

    Element row = parse(DENSE_BOTTOM_ROW).getDocumentElement();
    List<Element> keys = directChildren(row, "key");
    Element space = key(keys, "key0", "space");

    assertEquals("Dense spacebar top-left must open clipboard history.",
        "switch_clipboard", space.getAttribute("key1"));
    assertEquals("Dense spacebar bottom-left must open emoji search.",
        "switch_emoji", space.getAttribute("key3"));
    assertEquals("Dense spacebar bottom-right must open GIF search.",
        "gif", space.getAttribute("key4"));
  }

  @Test
  public void voice_typing_remains_on_far_right_done_enter_top_left_gesture()
      throws Exception
  {
    assertVoiceGestureOnLastEnter(
        directChildren(lastRow(parse(CLEAN).getDocumentElement()), "key"),
        "clean Fleksy");
    assertVoiceGestureOnLastEnter(
        directChildren(parse(DENSE_BOTTOM_ROW).getDocumentElement(), "key"),
        "dense FrankenKey");
  }

  private static void assertVoiceGestureOnLastEnter(List<Element> keys,
      String layout)
  {
    Element enter = key(keys, "key0", "enter");
    assertSame(layout + " Done/Enter must remain the far-right action key.",
        keys.get(keys.size() - 1), enter);
    assertEquals(layout
        + " Done/Enter top-left gesture must remain localized voice typing.",
        "loc voice_typing", enter.getAttribute("key1"));
    assertEquals(layout
        + " Done/Enter top-right gesture must retain the editor action.",
        "action", enter.getAttribute("key2"));
    assertEquals(layout + " Done/Enter must remain visually identified as an action key.",
        "action", enter.getAttribute("role"));
  }

  private static Element lastRow(Element keyboard)
  {
    List<Element> rows = directChildren(keyboard, "row");
    assertFalse("Keyboard fixture must contain rows.", rows.isEmpty());
    return rows.get(rows.size() - 1);
  }

  private static Element key(List<Element> keys, String attribute,
      String value)
  {
    for (Element key : keys)
      if (value.equals(key.getAttribute(attribute)))
        return key;
    fail("Missing key " + attribute + "=" + value);
    return null;
  }

  private static String attributes(Element element)
  {
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < element.getAttributes().getLength(); i++)
      out.append(element.getAttributes().item(i).getNodeValue()).append(' ');
    return out.toString();
  }

  private static List<Element> directChildren(Element parent, String tag)
  {
    List<Element> out = new ArrayList<Element>();
    NodeList children = parent.getChildNodes();
    for (int i = 0; i < children.getLength(); i++)
    {
      Node child = children.item(i);
      if (child.getNodeType() == Node.ELEMENT_NODE
          && tag.equals(child.getNodeName()))
        out.add((Element)child);
    }
    return out;
  }

  private static Document parse(String path)
      throws Exception
  {
    return DocumentBuilderFactory.newInstance().newDocumentBuilder()
      .parse(new File(path));
  }
}
