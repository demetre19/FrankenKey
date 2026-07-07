package juloo.keyboard2;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.inputmethod.BaseInputConnection;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import juloo.keyboard2.suggestions.Suggestions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
public class ClipboardParityTest
{
  private static final String BOTTOM_ROW = "res/xml/bottom_row.xml";
  private static final String CLEAN_TEXT = "res/xml/clean_text.xml";

  private SharedPreferences _prefs;

  @Before
  public void setUp()
  {
    Context context = RuntimeEnvironment.getApplication();
    _prefs = context.getSharedPreferences(
        "clipboard_parity_test", Context.MODE_PRIVATE);
    _prefs.edit().clear()
      .putBoolean("clipboard_history_enabled", true)
      .putString("clipboard_history_duration", "-1")
      .commit();
    clearPinnedClipboards(context);
    installTestConfig();
    ClipboardHistoryService._service = null;
    ClipboardHistoryService._paste_callback = null;
  }

  @After
  public void tearDown()
  {
    if (ClipboardHistoryService._service != null)
      ClipboardHistoryService._service.clear_history();
    ClipboardHistoryService._service = null;
    ClipboardHistoryService._paste_callback = null;
    if (_prefs != null)
      _prefs.edit().clear().commit();
    clearPinnedClipboards(RuntimeEnvironment.getApplication());
  }

  @Test
  public void paste_and_plain_paste_insert_clipboard_text_when_context_menu_paste_is_rejected()
  {
    for (PasteCase pasteCase : new PasteCase[] {
        new PasteCase("paste", KeyValue.Editing.PASTE),
        new PasteCase("plain paste", KeyValue.Editing.PASTE_PLAIN)
    })
    {
      String clipboardText = "Clipboard " + pasteCase.name + " 😀\nsecond line";
      RecordingReceiver receiver = new RecordingReceiver("before ");
      KeyEventHandler handler = new KeyEventHandler(receiver, null);
      installClipboard(clipboardText, handler);

      handler.handle_editing_key(pasteCase.editingKey);

      assertEquals(pasteCase.name
          + " must insert the current clipboard text through the IME commit path when the editor rejects context-menu paste.",
          "before " + clipboardText, receiver.input.text.toString());
      assertEquals(pasteCase.name
          + " must make one semantic text commit; a rejected performContextMenuAction alone leaves the editor unchanged.",
          1, receiver.input.commitTextCalls);
      assertEquals(clipboardText, receiver.input.lastCommittedText.toString());
      assertEquals(1, receiver.input.lastCommitNewCursorPosition);
    }
  }

  @Test
  public void bottom_rows_expose_clipboard_pane_as_visible_affordance()
      throws Exception
  {
    Element frankenBottomRow = parseLayout(BOTTOM_ROW).getDocumentElement();
    Element cleanBottomRow = lastDirectRow(parseLayout(CLEAN_TEXT).getDocumentElement());

    Element frankenClipboard = keyWithVisibleValue(frankenBottomRow,
        "switch_clipboard");
    Element cleanClipboard = keyWithVisibleValue(cleanBottomRow,
        "switch_clipboard");

    assertEquals("The normal FrankenKey bottom row must expose the clipboard pane with a visible key value, not loc-only hidden text.",
        "switch_clipboard", visibleValue(frankenClipboard, "switch_clipboard"));
    assertEquals("The Clean/Fleksy bottom row must expose the clipboard pane with a visible key value, not loc-only hidden text.",
        "switch_clipboard", visibleValue(cleanClipboard, "switch_clipboard"));
    assertNoLocOnlyClipboard(frankenBottomRow, BOTTOM_ROW);
    assertNoLocOnlyClipboard(cleanBottomRow, CLEAN_TEXT + " bottom row");
  }

  @Test
  public void clipboard_tips_hide_after_three_pinned_clips_and_return_below_limit()
  {
    Context context = RuntimeEnvironment.getApplication();
    clearPinnedClipboards(context);
    LinearLayout pane = new LinearLayout(context);
    View tips = new View(context);
    tips.setId(R.id.clipboard_tips_section);
    pane.addView(tips);
    ClipboardPinView pins = new ClipboardPinView(context, null);
    pane.addView(pins);

    pins.add_entry("first pinned clip");
    assertEquals("Clipboard tips should stay visible while fewer than three clips are pinned.",
        View.VISIBLE, tips.getVisibility());

    pins.add_entry("second pinned clip");
    assertEquals("Clipboard tips should still be visible at two pinned clips.",
        View.VISIBLE, tips.getVisibility());

    pins.add_entry("third pinned clip");
    assertEquals("Clipboard tips should hide once the pinned section has enough examples.",
        View.GONE, tips.getVisibility());

    pins.remove_entry(0);
    assertEquals("Clipboard tips should come back when pinned clips fall below the hide threshold.",
        View.VISIBLE, tips.getVisibility());
  }

  @Test
  public void clipboard_history_row_tap_pastes_the_row_clip()
  {
    Context context = RuntimeEnvironment.getApplication();
    RecordingPasteCallback callback = new RecordingPasteCallback();
    ClipboardHistoryService.on_startup(context, callback);
    ClipboardHistoryService._service.add_clip("older history clip");
    ClipboardHistoryService._service.add_clip("newer history clip");
    ClipboardHistoryView history = new ClipboardHistoryView(context, null);

    View row = history.getAdapter().getView(1, clipboardHistoryRow(context),
        new LinearLayout(context));

    assertTrue("The whole clipboard history row must be clickable, not just the paste icon.",
        row.performClick());
    assertEquals("Tapping a history row should paste the clip at that row's adapter position.",
        "older history clip", callback.lastPasted);
  }

  @Test
  public void pinned_clipboard_card_tap_pastes_the_card_clip()
  {
    Context context = RuntimeEnvironment.getApplication();
    clearPinnedClipboards(context);
    RecordingPasteCallback callback = new RecordingPasteCallback();
    ClipboardHistoryService.on_startup(context, callback);
    ClipboardPinView pins = new ClipboardPinView(context, null);
    pins.add_entry("older pinned clip");
    pins.add_entry("newer pinned clip");

    View row = pins.getAdapter().getView(1, clipboardPinRow(context),
        new LinearLayout(context));

    assertTrue("The whole pinned clipboard card must be clickable, not just the paste icon.",
        row.performClick());
    assertEquals("Tapping a pinned card should paste the clip at that card's adapter position.",
        "older pinned clip", callback.lastPasted);
  }

  private void installClipboard(String text, KeyEventHandler handler)
  {
    Context context = RuntimeEnvironment.getApplication();
    ClipboardManager clipboard = (ClipboardManager)context.getSystemService(
        Context.CLIPBOARD_SERVICE);
    clipboard.setPrimaryClip(ClipData.newPlainText("frankenkey-test", text));
    ClipboardHistoryService.on_startup(context, handler);
  }

  private static final class PasteCase
  {
    final String name;
    final KeyValue.Editing editingKey;

    PasteCase(String name_, KeyValue.Editing editingKey_)
    {
      name = name_;
      editingKey = editingKey_;
    }
  }

  private static final class RecordingPasteCallback
      implements ClipboardHistoryService.ClipboardPasteCallback
  {
    String lastPasted = null;

    @Override
    public void paste_from_clipboard_pane(String content)
    {
      lastPasted = content;
    }
  }

  private static final class RecordingReceiver implements KeyEventHandler.IReceiver
  {
    final Handler handler = new Handler(Looper.getMainLooper());
    final RecordingInputConnection input = new RecordingInputConnection();

    RecordingReceiver(String text)
    {
      input.resetText(text);
    }

    @Override
    public void handle_event_key(KeyValue.Event ev) {}

    @Override
    public void set_shift_state(boolean state, boolean lock) {}

    @Override
    public void set_compose_pending(boolean pending) {}

    @Override
    public void selection_state_changed(boolean selection_is_ongoing) {}

    @Override
    public RecordingInputConnection getCurrentInputConnection()
    {
      return input;
    }

    @Override
    public Handler getHandler()
    {
      return handler;
    }

    @Override
    public void set_suggestions(Suggestions suggestions) {}
  }

  private static final class RecordingInputConnection extends BaseInputConnection
  {
    final StringBuilder text = new StringBuilder();
    final List<Integer> contextMenuActions = new ArrayList<Integer>();
    int selectionStart = 0;
    int selectionEnd = 0;
    int commitTextCalls = 0;
    int lastCommitNewCursorPosition = 0;
    CharSequence lastCommittedText = "";

    RecordingInputConnection()
    {
      super(new View(RuntimeEnvironment.getApplication()), false);
    }

    void resetText(String value)
    {
      text.setLength(0);
      text.append(value);
      selectionStart = text.length();
      selectionEnd = text.length();
    }

    @Override
    public boolean performContextMenuAction(int id)
    {
      contextMenuActions.add(id);
      return false;
    }

    @Override
    public boolean commitText(CharSequence committedText,
        int newCursorPosition)
    {
      ++commitTextCalls;
      lastCommittedText = committedText;
      lastCommitNewCursorPosition = newCursorPosition;
      int start = Math.min(selectionStart, selectionEnd);
      int end = Math.max(selectionStart, selectionEnd);
      text.replace(start, end, committedText.toString());
      selectionStart = start + committedText.length();
      selectionEnd = selectionStart;
      return true;
    }
  }

  private static Document parseLayout(String path)
      throws Exception
  {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",
        true);
    return factory.newDocumentBuilder().parse(new File(path));
  }

  private static Element lastDirectRow(Element keyboard)
  {
    List<Element> rows = directRows(keyboard);
    assertFalse("Layout must contain at least one direct row.", rows.isEmpty());
    return rows.get(rows.size() - 1);
  }

  private static List<Element> directRows(Element keyboard)
  {
    List<Element> rows = new ArrayList<Element>();
    for (Element child : directElementChildren(keyboard))
      if ("row".equals(child.getTagName()))
        rows.add(child);
    return rows;
  }

  private static Element keyWithVisibleValue(Element parent, String value)
  {
    for (Element key : directKeys(parent))
      if (visibleValue(key, value) != null)
        return key;
    fail("Missing visible key value \"" + value + "\" in "
        + parent.getTagName());
    return null;
  }

  private static String visibleValue(Element key, String value)
  {
    for (String attribute : keyValueAttributes())
      if (value.equals(key.getAttribute(attribute)))
        return value;
    return null;
  }

  private static void assertNoLocOnlyClipboard(Element row, String path)
  {
    for (Element key : directKeys(row))
      for (String attribute : keyValueAttributes())
        assertNotEquals(path + " must not hide the clipboard pane behind a loc-only side label.",
            "loc switch_clipboard", key.getAttribute(attribute));
  }

  private static List<Element> directKeys(Element row)
  {
    List<Element> keys = new ArrayList<Element>();
    for (Element child : directElementChildren(row))
      if ("key".equals(child.getTagName()))
        keys.add(child);
    return keys;
  }

  private static List<Element> directElementChildren(Element parent)
  {
    NodeList children = parent.getChildNodes();
    List<Element> elements = new ArrayList<Element>();
    for (int i = 0; i < children.getLength(); i++)
    {
      Node child = children.item(i);
      if (child instanceof Element)
        elements.add((Element)child);
    }
    return elements;
  }

  private static String[] keyValueAttributes()
  {
    return new String[]{"key0", "key1", "key2", "key3", "key4",
        "key5", "key6", "key7", "key8"};
  }



  private static void clearPinnedClipboards(Context context)
  {
    context.getSharedPreferences("pinned_clipboards", Context.MODE_PRIVATE)
      .edit().clear().commit();
  }

  private static View clipboardHistoryRow(Context context)
  {
    LinearLayout row = new LinearLayout(context);
    TextView text = new TextView(context);
    text.setId(R.id.clipboard_entry_text);
    row.addView(text);
    View addPin = new View(context);
    addPin.setId(R.id.clipboard_entry_addpin);
    row.addView(addPin);
    View paste = new View(context);
    paste.setId(R.id.clipboard_entry_paste);
    row.addView(paste);
    return row;
  }

  private static View clipboardPinRow(Context context)
  {
    LinearLayout row = new LinearLayout(context);
    TextView text = new TextView(context);
    text.setId(R.id.clipboard_pin_text);
    row.addView(text);
    View paste = new View(context);
    paste.setId(R.id.clipboard_pin_paste);
    row.addView(paste);
    View remove = new View(context);
    remove.setId(R.id.clipboard_pin_remove);
    row.addView(remove);
    return row;
  }
  private void installTestConfig()
  {
    try
    {
      java.lang.reflect.Constructor<Config> ctor =
        Config.class.getDeclaredConstructor(SharedPreferences.class,
            Resources.class, Boolean.class,
            juloo.keyboard2.dict.Dictionaries.class);
      ctor.setAccessible(true);
      Config config = ctor.newInstance(_prefs, testResources(), Boolean.FALSE,
          null);
      java.lang.reflect.Field globalConfig =
        Config.class.getDeclaredField("_globalConfig");
      globalConfig.setAccessible(true);
      globalConfig.set(null, config);
    }
    catch (Exception e)
    {
      throw new AssertionError("Clipboard parity tests need Config preferences for clipboard listener setup only.", e);
    }
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
      super(base.getAssets(), base.getDisplayMetrics(),
          base.getConfiguration());
    }

    @Override
    public float getDimension(int id)
    {
      return 1f;
    }
  }
}
