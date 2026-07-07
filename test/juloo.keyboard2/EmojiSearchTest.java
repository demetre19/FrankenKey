package juloo.keyboard2;

import android.content.Context;
import android.content.res.Resources;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
public class EmojiSearchTest
{
  @Before
  public void setUp()
  {
    RuntimeEnvironment.getApplication()
      .getSharedPreferences("emoji_last_use", Context.MODE_PRIVATE)
      .edit().clear().commit();
    Emoji.init(testResources());
  }

  @Test
  public void emoji_data_has_searchable_names()
  {
    List<Emoji> results = Emoji.search("grinning face");

    assertFalse("Emoji search must use generated Unicode names, not a no-op list.",
        results.isEmpty());
    assertEquals("😀", results.get(0).kv().getString());
  }

  @Test
  public void emoji_grid_filters_and_restores_current_group()
  {
    EmojiGridView grid = new EmojiGridView(RuntimeEnvironment.getApplication(), null);
    int groupCount = grid.getAdapter().getCount();

    grid.setSearchQuery("red heart");
    int filteredCount = grid.getAdapter().getCount();

    assertTrue("Search results must narrow the currently displayed emoji grid.",
        filteredCount > 0 && filteredCount < groupCount);
    assertEquals("❤️", ((Emoji)grid.getAdapter().getItem(0)).kv().getString());

    grid.setSearchQuery("");
    assertEquals("Clearing search must restore the existing empty-query category behavior.",
        groupCount, grid.getAdapter().getCount());
  }

  @Test
  public void emoji_pane_embeds_search_header_and_typing_keyboard()
      throws Exception
  {
    Element pane = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(new File("res/layout/emoji_pane.xml"))
        .getDocumentElement();
    NodeList views = pane.getElementsByTagName("*");

    assertEquals("juloo.keyboard2.EmojiSearchView", pane.getNodeName());
    assertTrue("Emoji pane must expose an editable search field.",
        hasAndroidId(views, "@+id/emoji_search_query"));
    assertTrue("Emoji pane must include the normal keyboard for query typing.",
        hasAndroidId(views, "@+id/emoji_keyboard_view"));
    assertTrue("Emoji pane needs an explicit ABC button to return to app typing.",
        hasAndroidId(views, "@+id/emoji_close_button"));
    assertTrue("Emoji pane must keep the category buttons for empty searches.",
        hasElement(views, "juloo.keyboard2.EmojiGroupButtonsBar"));
  }

  @Test
  public void emoji_results_region_stays_bounded_and_top_sticky()
      throws Exception
  {
    Element pane = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(new File("res/layout/emoji_pane.xml"))
        .getDocumentElement();
    NodeList views = pane.getElementsByTagName("*");
    Element grid = elementByAndroidId(views, "@+id/emoji_grid");
    String dimen = valueResource("dimen", "emoji_grid_height");

    assertEquals("emoji_grid_height should reserve about 40% of the pane for search results.",
        "200dp", dimen);

    assertEquals("Emoji results must have a fixed pane slice so they cannot consume the embedded keyboard.",
        "@dimen/emoji_grid_height", grid.getAttribute("android:layout_height"));
    assertEquals("Emoji results must not keep a weight that expands to fill the whole pane.",
        "", grid.getAttribute("android:layout_weight"));
    assertEquals("Emoji results should start at the top of the bounded region.",
        "top", grid.getAttribute("android:gravity"));
    assertEquals("Emoji results need top padding so the first row does not stick to the tabs.",
        "6dp", grid.getAttribute("android:paddingTop"));
    assertEquals("Emoji rows need visible vertical separation.",
        "4dp", grid.getAttribute("android:verticalSpacing"));
    assertEquals("Emoji columns need visible horizontal separation.",
        "4dp", grid.getAttribute("android:horizontalSpacing"));
    assertEquals("Emoji result padding must remain visible inside the bounded grid.",
        "false", grid.getAttribute("android:clipToPadding"));
  }

  @Test
  public void emoji_category_tabs_are_large_and_separated_enough_to_tap()
      throws Exception
  {
    Element pane = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(new File("res/layout/emoji_pane.xml"))
        .getDocumentElement();
    NodeList views = pane.getElementsByTagName("*");
    Element tabs = elementByName(views, "juloo.keyboard2.EmojiGroupButtonsBar");

    assertEquals("Emoji category tabs need top padding to separate them from the search header.",
        "4dp", tabs.getAttribute("android:paddingTop"));
    assertEquals("Emoji category tabs need left inset so edge buttons are not cramped.",
        "4dp", tabs.getAttribute("android:paddingLeft"));
    assertEquals("Emoji category tabs need right inset so edge buttons are not cramped.",
        "4dp", tabs.getAttribute("android:paddingRight"));
    assertEquals("Emoji tab buttons need a larger tap target than the old tiny tabs.",
        "36dp", styleItem("emojiTypeButton", "android:minHeight"));
    assertEquals("Emoji tab buttons need padding around the emoji glyph.",
        "4dp", styleItem("emojiTypeButton", "android:padding"));
    assertEquals("Emoji tab button text should be large enough to distinguish categories.",
        "22sp", styleItem("emojiTypeButton", "android:textSize"));
  }


  private static Element elementByAndroidId(NodeList views, String id)
  {
    for (int i = 0; i < views.getLength(); ++i)
    {
      Element view = (Element)views.item(i);
      if (id.equals(view.getAttribute("android:id")))
        return view;
    }
    fail("Expected a view with android:id " + id + ".");
    return null;
  }

  private static Element elementByName(NodeList views, String name)
  {
    for (int i = 0; i < views.getLength(); ++i)
      if (name.equals(views.item(i).getNodeName()))
        return (Element)views.item(i);
    fail("Expected an element named " + name + ".");
    return null;
  }

  private static String styleItem(String styleName, String itemName)
      throws Exception
  {
    Element styles = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(new File("res/values/styles.xml"))
        .getDocumentElement();
    NodeList styleNodes = styles.getElementsByTagName("style");
    for (int i = 0; i < styleNodes.getLength(); ++i)
    {
      Element style = (Element)styleNodes.item(i);
      if (!styleName.equals(style.getAttribute("name")))
        continue;
      NodeList items = style.getElementsByTagName("item");
      for (int j = 0; j < items.getLength(); ++j)
      {
        Element item = (Element)items.item(j);
        if (itemName.equals(item.getAttribute("name")))
          return item.getTextContent();
      }
      fail("Expected style " + styleName + " to define " + itemName + ".");
      return "";
    }
    fail("Expected style " + styleName + ".");
    return "";
  }

  private static String valueResource(String tagName, String name)
      throws Exception
  {
    NodeList values = DocumentBuilderFactory.newInstance()
      .newDocumentBuilder()
      .parse(new File("res/values/values.xml"))
      .getElementsByTagName(tagName);
    for (int i = 0; i < values.getLength(); i++)
    {
      Element value = (Element)values.item(i);
      if (name.equals(value.getAttribute("name")))
        return value.getTextContent();
    }
    fail("Missing value resource: " + tagName + "/" + name);
    return "";
  }

  private static Resources testResources()
  {
    Resources base = RuntimeEnvironment.getApplication().getResources();
    return new TestResources(base);
  }

  private static final class TestResources extends Resources
  {
    TestResources(Resources base)
    {
      super(base.getAssets(), base.getDisplayMetrics(), base.getConfiguration());
    }

    @Override
    public InputStream openRawResource(int id)
    {
      if (id == R.raw.emojis)
      {
        try { return new FileInputStream("res/raw/emojis.txt"); }
        catch (Exception e) { throw new RuntimeException(e); }
      }
      return super.openRawResource(id);
    }
  }

  private static boolean hasAndroidId(NodeList views, String id)
  {
    for (int i = 0; i < views.getLength(); ++i)
      if (id.equals(((Element)views.item(i)).getAttribute("android:id")))
        return true;
    return false;
  }

  private static boolean hasElement(NodeList views, String name)
  {
    for (int i = 0; i < views.getLength(); ++i)
      if (name.equals(views.item(i).getNodeName()))
        return true;
    return false;
  }
}
