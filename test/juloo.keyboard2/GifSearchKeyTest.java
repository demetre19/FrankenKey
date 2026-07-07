package juloo.keyboard2;

import android.content.res.Resources;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.os.Handler;
import android.view.inputmethod.InputConnection;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import juloo.keyboard2.suggestions.Suggestions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import java.io.File;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
public class GifSearchKeyTest
{
  public GifSearchKeyTest() {}

  @Test
  public void refresh_keeps_corner_gif_key_available_for_action_editors()
      throws Exception
  {
    EditorConfig config = new EditorConfig();

    config.refresh(editor(EditorInfo.IME_ACTION_SEARCH), testResources());
    assertTrue("Search editors must keep the GIF corner available.",
        config.gif_action_key);

    config.refresh(editor(EditorInfo.IME_ACTION_GO), testResources());
    assertTrue("Go editors must keep the GIF corner available.",
        config.gif_action_key);

    config.refresh(editor(EditorInfo.IME_ACTION_NEXT), testResources());
    assertTrue("Next editors must also keep the GIF corner available.",
        config.gif_action_key);

    config.refresh(editor(EditorInfo.IME_ACTION_DONE), testResources());
    assertTrue("Done editors must not clear the universal GIF corner.",
        config.gif_action_key);
  }

  @Test
  public void gif_special_key_is_event_gif()
  {
    KeyValue gif = KeyValue.getSpecialKeyByName("gif");

    assertNotNull("The bottom-row gif name must resolve to a special key.", gif);
    assertEquals(KeyValue.Kind.Event, gif.getKind());
    assertEquals(KeyValue.Event.GIF, gif.getEvent());
    assertEquals("The GIF corner label should stay neutral and subtle.",
        "gif", gif.getString());
    assertTrue("The GIF corner must use muted secondary-label styling.",
        gif.hasFlagsAny(KeyValue.FLAG_SECONDARY));
  }

  @Test
  public void switch_back_gif_special_key_returns_to_keyboard()
  {
    KeyValue back = KeyValue.getSpecialKeyByName("switch_back_gif");

    assertNotNull("The GIF pane bottom-row key must resolve.", back);
    assertEquals(KeyValue.Kind.Event, back.getKind());
    assertEquals(KeyValue.Event.SWITCH_BACK_GIF, back.getEvent());
  }

  @Test
  public void gif_mime_matching_accepts_exact_and_wildcard_image_types()
  {
    assertTrue(GifInserter.acceptsGifMimeType("image/gif"));
    assertTrue(GifInserter.acceptsGifMimeType("image/*"));
    assertTrue(GifInserter.acceptsGifMimeType("*/*"));
    assertFalse(GifInserter.acceptsGifMimeType("image/png"));
    assertFalse(GifInserter.editorAcceptsGif(null));
    assertFalse(GifInserter.editorAcceptsGif(new EditorInfo()));
  }

  @Test
  public void bottom_right_keys_wire_gif_across_keyboard_configs()
      throws Exception
  {
    String[] layouts = new String[] {
      "res/xml/numeric.xml",
      "res/xml/numeric_landscape.xml",
      "res/xml/numpad.xml",
      "res/xml/pin.xml",
      "res/xml/pin_landscape.xml",
      "res/xml/greekmath.xml",
      "res/xml/emoji_bottom_row.xml",
      "res/xml/clipboard_bottom_row.xml",
      "res/xml/gif_bottom_row.xml",
    };

    for (String layout : layouts)
      assertEquals(layout + " must expose GIF from its bottom-right key.",
          "gif", bottomRightKey(layout).getAttribute("key4"));
  }

  @Test
  public void normal_bottom_row_keeps_voice_on_spacebar_and_gif_on_action_corner()
      throws Exception
  {
    Element spacebar = keyByRole("res/xml/bottom_row.xml", "space_bar");

    assertEquals("Normal bottom-row spacebar southeast corner must open voice typing.",
        "voice_typing", spacebar.getAttribute("key4"));
    assertEquals("Normal bottom-right action key southeast corner must remain GIF.",
        "gif", bottomRightKey("res/xml/bottom_row.xml").getAttribute("key4"));
    KeyValue voice = KeyValue.getSpecialKeyByName("voice_typing");
    assertNotNull("The spacebar voice corner must resolve to a special key.", voice);
    assertEquals(KeyValue.Kind.Event, voice.getKind());
    assertEquals(KeyValue.Event.SWITCH_VOICE_TYPING, voice.getEvent());
    KeyValue legacyChooser = KeyValue.getSpecialKeyByName("voice_typing_chooser");
    assertNotNull("The legacy voice chooser name must still resolve.", legacyChooser);
    assertEquals("The legacy voice chooser key must now go straight to voice typing.",
        KeyValue.Event.SWITCH_VOICE_TYPING, legacyChooser.getEvent());
  }

  @Test
  public void layout_modifier_keeps_gif_corner_for_all_editor_actions()
  {
    assertTrue(LayoutModifier.should_show_gif_key(new EditorConfig()));
  }

  @Test
  public void local_gif_library_has_searchable_fallbacks()
  {
    assertFalse("The picker must have offline GIFs before a GIPHY key is set.",
        GifLibrary.searchLocal("").isEmpty());
    assertEquals("frankenkey_yes", GifLibrary.searchLocal("agree").get(0).id);
  }

  @Test
  public void settings_exposes_giphy_api_key_and_dashboard_link()
      throws Exception
  {
    Element settings = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(new File("res/xml/settings.xml"))
        .getDocumentElement();
    NodeList preferences = settings.getElementsByTagName("*");

    assertTrue("Settings must include a user-owned GIPHY API key field.",
        hasPreferenceKey(preferences, "giphy_api_key"));
    assertTrue("Settings must include the GIPHY dashboard link preference.",
        hasPreferenceKey(preferences, "giphy_api_dashboard"));
  }

  @Test
  public void gif_pane_embeds_typing_keyboard_for_search_field()
      throws Exception
  {
    Element pane = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(new File("res/layout/gif_pane.xml"))
        .getDocumentElement();
    NodeList views = pane.getElementsByTagName("*");

    assertTrue("GIF pane must include the normal keyboard for query typing.",
        hasAndroidId(views, "@+id/gif_keyboard_view"));
    assertTrue("GIF pane needs an explicit ABC button to return to app typing.",
        hasAndroidId(views, "@+id/gif_close_button"));
  }

  @Test
  public void gif_search_backspace_key_deletes_previous_query_character()
  {
    GifSearchHarness search = GifSearchHarness.create();

    search.type("catx");
    search.press("backspace");

    assertEquals("The embedded GIF keyboard backspace must edit the search query text.",
        "cat", search.query());
  }

  @Test
  public void gif_search_space_key_runs_search_for_each_completed_query()
  {
    final List<String> searches = new ArrayList<String>();
    final MutableSearchInput input = MutableSearchInput.create(new SearchAction() {
      @Override
      public void search() {}
    });
    InputConnection conn = new GifSearchView.SearchInputConnection(
        input.connection, new Runnable() {
          @Override
          public void run()
          {
            String query = input.text().trim();
            if (query.length() > 0)
              searches.add(query);
          }
        });

    conn.commitText("agree", 1);
    assertTrue("Typing without a completed term must not refresh GIF results.",
        searches.isEmpty());

    conn.commitText(" ", 1);
    conn.commitText("wow", 1);
    conn.commitText(" ", 1);

    assertEquals("A space in GIF search mode must search each completed query.",
        "[agree, agree wow]", searches.toString());
  }


  private static final class GifSearchHarness
  {
    private final SearchReceiver receiver;
    private final KeyEventHandler handler;

    private GifSearchHarness(SearchReceiver receiver, KeyEventHandler handler)
    {
      this.receiver = receiver;
      this.handler = handler;
    }

    static GifSearchHarness create()
    {
      SearchReceiver receiver = SearchReceiver.create();
      KeyEventHandler handler = new KeyEventHandler(receiver, null);
      return new GifSearchHarness(receiver, handler);
    }

    void type(String text)
    {
      for (int i = 0; i < text.length(); i++)
        press(String.valueOf(text.charAt(i)));
    }

    void press(String keyName)
    {
      handler.key_up(KeyValue.getKeyByName(keyName), Pointers.Modifiers.EMPTY);
    }

    String query()
    {
      return receiver.input.text();
    }

    List<String> searches()
    {
      return receiver.searches;
    }
  }

  private static final class SearchReceiver implements KeyEventHandler.IReceiver
  {
    final MutableSearchInput input;
    final List<String> searches = new ArrayList<String>();

    private SearchReceiver(MutableSearchInput input)
    {
      this.input = input;
    }

    static SearchReceiver create()
    {
      final SearchReceiver[] holder = new SearchReceiver[1];
      MutableSearchInput input = MutableSearchInput.create(new SearchAction()
      {
        @Override
        public void search()
        {
          holder[0].searchCurrentQuery();
        }
      });
      holder[0] = new SearchReceiver(input);
      return holder[0];
    }

    void searchCurrentQuery()
    {
      String query = input.text().trim();
      if (query.length() > 0)
        searches.add(query);
    }

    @Override
    public void handle_event_key(KeyValue.Event ev)
    {
      if (ev == KeyValue.Event.ACTION)
        searchCurrentQuery();
    }

    @Override
    public void set_shift_state(boolean state, boolean lock) {}

    @Override
    public void set_compose_pending(boolean pending) {}

    @Override
    public void selection_state_changed(boolean selection_is_ongoing) {}

    @Override
    public InputConnection getCurrentInputConnection()
    {
      return input.connection;
    }

    @Override
    public Handler getHandler()
    {
      return new Handler();
    }

    @Override
    public void set_suggestions(Suggestions suggestions) {}

  }

  private static final class MutableSearchInput
  {
    final InputConnection connection;
    private final StringBuilder text = new StringBuilder();
    private final SearchAction searchAction;
    private int cursor = 0;

    private MutableSearchInput(InputConnection connection,
        SearchAction searchAction)
    {
      this.connection = connection;
      this.searchAction = searchAction;
    }

    static MutableSearchInput create(SearchAction searchAction)
    {
      final MutableSearchInput[] holder = new MutableSearchInput[1];
      InvocationHandler handler = new InvocationHandler()
      {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
        {
          MutableSearchInput input = holder[0];
          if ("commitText".equals(method.getName()))
          {
            input.commit((CharSequence)args[0]);
            return Boolean.TRUE;
          }
          if ("deleteSurroundingText".equals(method.getName()))
          {
            input.delete(((Integer)args[0]).intValue(),
                ((Integer)args[1]).intValue());
            return Boolean.TRUE;
          }
          if ("performEditorAction".equals(method.getName()))
          {
            input.searchAction.search();
            return Boolean.TRUE;
          }
          if ("sendKeyEvent".equals(method.getName()))
            return Boolean.TRUE;
          return defaultValue(method.getReturnType());
        }
      };
      InputConnection connection = (InputConnection)Proxy.newProxyInstance(
          InputConnection.class.getClassLoader(),
          new Class<?>[] { InputConnection.class }, handler);
      holder[0] = new MutableSearchInput(connection, searchAction);
      return holder[0];
    }

    String text()
    {
      return text.toString();
    }

    private void commit(CharSequence value)
    {
      text.insert(cursor, value);
      cursor += value.length();
    }

    private void delete(int beforeLength, int afterLength)
    {
      int start = Math.max(0, cursor - beforeLength);
      int end = Math.min(text.length(), cursor + afterLength);
      text.delete(start, end);
      cursor = start;
    }

    private static Object defaultValue(Class<?> returnType)
    {
      if (returnType == Void.TYPE)
        return null;
      if (returnType == Boolean.TYPE)
        return Boolean.FALSE;
      if (returnType == Integer.TYPE)
        return Integer.valueOf(0);
      if (returnType == Long.TYPE)
        return Long.valueOf(0L);
      if (returnType == Float.TYPE)
        return Float.valueOf(0.0f);
      if (returnType == Double.TYPE)
        return Double.valueOf(0.0d);
      if (returnType == Short.TYPE)
        return Short.valueOf((short)0);
      if (returnType == Byte.TYPE)
        return Byte.valueOf((byte)0);
      if (returnType == Character.TYPE)
        return Character.valueOf('\0');
      return null;
    }
  }

  private static interface SearchAction
  {
    public void search();
  }


  private static EditorInfo editor(int imeAction)
  {
    EditorInfo info = new EditorInfo();
    info.inputType = InputType.TYPE_CLASS_TEXT;
    info.imeOptions = imeAction;
    info.packageName = "juloo.keyboard2.test";
    return info;
  }



  private static Element bottomRightKey(String path)
      throws Exception
  {
    Element root = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(new File(path))
        .getDocumentElement();
    NodeList rows = root.getElementsByTagName("row");
    Element row = rows.getLength() == 0 ? root :
      (Element)rows.item(rows.getLength() - 1);
    NodeList keys = row.getElementsByTagName("key");
    assertTrue(path + " must have a bottom-right key.", keys.getLength() > 0);
    return (Element)keys.item(keys.getLength() - 1);
  }

  private static Element keyByRole(String path, String role)
      throws Exception
  {
    Element root = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(new File(path))
        .getDocumentElement();
    NodeList keys = root.getElementsByTagName("key");
    for (int i = 0; i < keys.getLength(); i++)
    {
      Element key = (Element)keys.item(i);
      if (role.equals(key.getAttribute("role")))
        return key;
    }
    fail(path + " must have a key with role " + role + ".");
    return null;
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
    public String getString(int id)
    {
      if (id == R.string.key_action_search)
        return "Search";
      if (id == R.string.key_action_go)
        return "Go";
      if (id == R.string.key_action_done)
        return "Done";
      return "Action";
    }
  }


  private static boolean hasPreferenceKey(NodeList preferences, String key)
  {
    for (int i = 0; i < preferences.getLength(); i++)
    {
      Element pref = (Element)preferences.item(i);
      if (key.equals(pref.getAttribute("android:key")))
        return true;
    }
    return false;
  }

  private static boolean hasAndroidId(NodeList views, String id)
  {
    for (int i = 0; i < views.getLength(); i++)
    {
      Element view = (Element)views.item(i);
      if (id.equals(view.getAttribute("android:id")))
        return true;
    }
    return false;
  }
}
