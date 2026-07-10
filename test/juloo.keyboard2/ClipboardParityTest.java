package juloo.keyboard2;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.text.TextUtils;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.AbsListView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.inputmethod.BaseInputConnection;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
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
      .putBoolean("clipboard_save_screenshots", true)
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
  public void clipboard_history_text_card_tap_opens_editor_when_present_and_paste_button_still_pastes()
  {
    Context context = RuntimeEnvironment.getApplication();
    RecordingPasteCallback callback = new RecordingPasteCallback();
    ClipboardHistoryService.on_startup(context, callback);
    ClipboardHistoryService._service.add_clip("history draft");
    ClipboardHistoryView history = new ClipboardHistoryView(context, null);
    FrameLayout root = clipboardRootWithEditor(context);
    View row = history.getAdapter().getView(0, clipboardHistoryRow(context),
        root);
    root.addView(row);

    assertTrue("Tapping a text history card should be handled by the row.",
        row.performClick());
    ClipboardItemEditorView editor = (ClipboardItemEditorView)root
      .findViewById(R.id.clipboard_item_editor);
    EditText editText = editText(editor);
    assertEquals("Text card taps must open the inline editor instead of pasting immediately when the editor is present.",
        View.VISIBLE, editor.getVisibility());
    assertEquals("The editor must open on the selected clipboard text.",
        "history draft", editText.getText().toString());
    assertNull("Opening the editor must not paste until the user chooses paste.",
        callback.lastPasted);

    assertTrue("The explicit paste button must continue to paste directly even when card taps open the editor.",
        row.findViewById(R.id.clipboard_entry_paste).performClick());
    assertEquals("history draft", callback.lastPasted);

    callback.lastPasted = null;
    editText.setText("edited history draft");
    assertTrue("The editor paste action must commit the edited text.",
        editor.findViewById(R.id.clipboard_editor_paste).performClick());
    assertEquals("edited history draft", callback.lastPasted);

    callback.lastPasted = null;
    assertTrue("After editing, direct paste should use the replaced history entry.",
        row.findViewById(R.id.clipboard_entry_paste).performClick());
    assertEquals("edited history draft", callback.lastPasted);
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

  @Test
  public void pinned_clipboard_text_card_tap_opens_editor_and_edited_text_persists()
  {
    Context context = RuntimeEnvironment.getApplication();
    clearPinnedClipboards(context);
    RecordingPasteCallback callback = new RecordingPasteCallback();
    ClipboardHistoryService.on_startup(context, callback);
    ClipboardPinView pins = new ClipboardPinView(context, null);
    pins.add_entry("pinned draft");
    FrameLayout root = clipboardRootWithEditor(context);
    View row = pins.getAdapter().getView(0, clipboardPinRow(context), root);
    root.addView(row);

    assertTrue("Tapping a pinned text card should open the editor.",
        row.performClick());
    ClipboardItemEditorView editor = (ClipboardItemEditorView)root
      .findViewById(R.id.clipboard_item_editor);
    EditText editText = editText(editor);
    assertEquals(View.VISIBLE, editor.getVisibility());
    assertEquals("pinned draft", editText.getText().toString());
    assertNull("Opening a pinned clip for editing must not paste yet.",
        callback.lastPasted);

    editText.setText("edited pinned draft");
    assertTrue("Pasting from the editor must paste the edited pinned text.",
        editor.findViewById(R.id.clipboard_editor_paste).performClick());
    assertEquals("edited pinned draft", callback.lastPasted);

    callback.lastPasted = null;
    ClipboardPinView reloadedPins = new ClipboardPinView(context, null);
    View reloadedRow = reloadedPins.getAdapter().getView(0,
        clipboardPinRow(context), new LinearLayout(context));
    assertTrue("The edited pinned value must survive reload from pinned clipboard storage.",
        reloadedRow.findViewById(R.id.clipboard_pin_paste).performClick());
    assertEquals("edited pinned draft", callback.lastPasted);
  }

  @Test
  public void image_history_entries_pin_and_paste_through_image_callback()
  {
    Context context = RuntimeEnvironment.getApplication();
    clearPinnedClipboards(context);
    RecordingPasteCallback callback = new RecordingPasteCallback();
    ClipboardHistoryService.on_startup(context, callback);
    ClipboardHistoryService.ClipboardEntry image =
      ClipboardHistoryService.ClipboardEntry.image(
          "content://frankenkey.test/screenshot/7", "image/png",
          "Samsung screenshot");
    ClipboardHistoryService._service.add_clip(image);
    ClipboardHistoryView history = new ClipboardHistoryView(context, null);
    FrameLayout root = clipboardRootWithEditor(context);
    ClipboardPinView pins = new ClipboardPinView(context, null);
    pins.setId(R.id.clipboard_pin_view);
    root.addView(pins);
    root.addView(history);
    View row = history.getAdapter().getView(0, clipboardHistoryRow(context),
        root);
    root.addView(row);

    assertEquals("Image clipboard cards must show the screenshot label for accessibility and fallback text.",
        "Samsung screenshot",
        ((TextView)row.findViewById(R.id.clipboard_entry_text))
          .getText().toString());

    assertTrue("Tapping an image history card should paste the image directly, not open the text editor.",
        row.performClick());
    assertEquals("content://frankenkey.test/screenshot/7",
        callback.lastImageUri);
    assertEquals("image/png", callback.lastImageMimeType);
    assertEquals("Samsung screenshot", callback.lastImageDescription);
    assertEquals("Image card taps must not open the text editor.",
        View.GONE, root.findViewById(R.id.clipboard_item_editor)
          .getVisibility());

    callback.lastImageUri = null;
    assertTrue("The history pin button must pin image clips, not coerce them to text.",
        row.findViewById(R.id.clipboard_entry_addpin).performClick());
    View pinnedRow = pins.getAdapter().getView(0, clipboardPinRow(context),
        root);
    assertTrue("Tapping a pinned image card must paste through the image callback.",
        pinnedRow.performClick());
    assertEquals("content://frankenkey.test/screenshot/7",
        callback.lastImageUri);
    assertEquals("image/png", callback.lastImageMimeType);
    assertEquals("Samsung screenshot", callback.lastImageDescription);
  }

  @Test
  public void clipboard_xml_keeps_compact_grid_cards_and_visible_resize_affordances()
      throws Exception
  {
    Document styles = parseLayout("res/values/styles.xml");
    Document values = parseLayout("res/values/values.xml");
    Document pane = parseLayout("res/layout/clipboard_pane.xml");
    Document historyCard = parseLayout("res/layout/clipboard_history_entry.xml");
    Document pinCard = parseLayout("res/layout/clipboard_pin_entry.xml");

    assertEquals("@dimen/clipboard_grid_card_height",
        styleItem(styles, "clipboardGridCard", "android:layout_height"));
    assertEquals("@dimen/clipboard_grid_button_size",
        styleItem(styles, "clipboardGridButton", "android:layout_width"));
    assertEquals("@dimen/clipboard_grid_button_size",
        styleItem(styles, "clipboardGridButton", "android:layout_height"));
    assertTrue("Clipboard grid cards must stay compact enough to show a useful grid, not tall list rows.",
        dimenDp(values, "clipboard_grid_card_height") <= 112);
    assertTrue("Clipboard grid action buttons must remain compact overlays.",
        dimenDp(values, "clipboard_grid_button_size") <= 20);

    assertEquals("@style/clipboardGridCard",
        historyCard.getDocumentElement().getAttribute("style"));
    assertEquals("@style/clipboardGridCard",
        pinCard.getDocumentElement().getAttribute("style"));
    assertNotNull("History image cards must include an image preview surface.",
        elementWithAndroidId(historyCard, "@+id/clipboard_entry_image"));
    assertNotNull("Pinned image cards must include an image preview surface.",
        elementWithAndroidId(pinCard, "@+id/clipboard_pin_image"));
    assertNotNull("History cards must expose a paste overlay button.",
        elementWithAndroidId(historyCard, "@+id/clipboard_entry_paste"));
    assertNotNull("History cards must expose a pin overlay button.",
        elementWithAndroidId(historyCard, "@+id/clipboard_entry_addpin"));
    assertNotNull("Pinned cards must expose a paste overlay button.",
        elementWithAndroidId(pinCard, "@+id/clipboard_pin_paste"));
    assertNotNull("Pinned cards must expose a remove overlay button.",
        elementWithAndroidId(pinCard, "@+id/clipboard_pin_remove"));

    Element paneRoot = pane.getDocumentElement();
    assertEquals("juloo.keyboard2.ClipboardPanelView",
        paneRoot.getTagName());
    assertNotNull("Clipboard pane must keep a visible drag handle for resizing.",
        elementWithAndroidId(pane, "@+id/clipboard_drag_handle"));
    assertNotNull("Clipboard pane must keep a visible show-more affordance.",
        elementWithAndroidId(pane, "@+id/clipboard_show_more"));
    assertEquals("@dimen/clipboard_view_height",
        elementWithAndroidId(pane, "@+id/clipboard_scroll")
          .getAttribute("android:layout_height"));
    assertTrue("Expanded clipboard height must be larger than compact height.",
        dimenDp(values, "clipboard_expanded_view_height")
          > dimenDp(values, "clipboard_view_height"));
  }

  @Test
  public void clipboard_adapters_force_fixed_card_height_and_text_ellipsis()
  {
    Context context = RuntimeEnvironment.getApplication();
    clearPinnedClipboards(context);
    RecordingPasteCallback callback = new RecordingPasteCallback();
    ClipboardHistoryService.on_startup(context, callback);
    String tallClip = "first line\nsecond line\nthird line\nfourth line\nfifth line\nsixth line";
    ClipboardHistoryService._service.add_clip(tallClip);
    ClipboardHistoryView history = new ClipboardHistoryView(context, null);
    View historyRow = history.getAdapter().getView(0,
        clipboardHistoryRow(context), new LinearLayout(context));
    TextView historyText = (TextView)historyRow.findViewById(
        R.id.clipboard_entry_text);

    assertTrue("History cards must receive GridView layout params, not content-sized row params.",
        historyRow.getLayoutParams() instanceof AbsListView.LayoutParams);
    assertEquals("History cards must keep the same fixed screenshot-style height regardless of text length.",
        fixedClipboardCardHeightPx(context),
        historyRow.getLayoutParams().height);
    assertEquals("Tall history text must be clipped inside the fixed card.",
        5, historyText.getMaxLines());
    assertEquals("Tall history text must end with ellipsis instead of growing the card.",
        TextUtils.TruncateAt.END, historyText.getEllipsize());

    ClipboardPinView pins = new ClipboardPinView(context, null);
    pins.add_entry(tallClip);
    View pinRow = pins.getAdapter().getView(0, clipboardPinRow(context),
        new LinearLayout(context));
    TextView pinText = (TextView)pinRow.findViewById(R.id.clipboard_pin_text);
    assertTrue("Pinned cards must receive GridView layout params, not content-sized row params.",
        pinRow.getLayoutParams() instanceof AbsListView.LayoutParams);
    assertEquals("Pinned cards must keep the same fixed screenshot-style height regardless of text length.",
        fixedClipboardCardHeightPx(context),
        pinRow.getLayoutParams().height);
    assertEquals(5, pinText.getMaxLines());
    assertEquals(TextUtils.TruncateAt.END, pinText.getEllipsize());
  }

  @Test
  public void manifest_declares_image_permissions_for_recent_screenshot_clipboard()
      throws Exception
  {
    Document manifest = parseLayout("AndroidManifest.xml");
    Element readExternal = usesPermission(manifest,
        Manifest.permission.READ_EXTERNAL_STORAGE);
    Element readMedia = usesPermission(manifest,
        Manifest.permission.READ_MEDIA_IMAGES);

    Element readSelected = usesPermission(manifest,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED);
    assertNotNull("Android 12 and older need shared-image read permission to observe screenshot media.",
        readExternal);
    assertEquals("READ_EXTERNAL_STORAGE must not be requested on Android 13+.",
        "32", readExternal.getAttribute("android:maxSdkVersion"));
    assertNotNull("Android 13+ needs READ_MEDIA_IMAGES to observe screenshot media.",
        readMedia);
    assertNotNull("Android 14+ needs selected-visual-media permission declared like Gboard so the runtime prompt can grant media access cleanly.",
        readSelected);
  }

  private void installClipboard(String text, KeyEventHandler handler)
  {
    Context context = RuntimeEnvironment.getApplication();
    ClipboardManager clipboard = (ClipboardManager)context.getSystemService(
        Context.CLIPBOARD_SERVICE);
    clipboard.setPrimaryClip(ClipData.newPlainText("frankenkey-test", text));
    ClipboardHistoryService.on_startup(context, handler);
  }

  private static FrameLayout clipboardRootWithEditor(Context context)
  {
    FrameLayout root = new FrameLayout(context);
    root.addView(clipboardEditor(context));
    return root;
  }

  private static ClipboardItemEditorView clipboardEditor(Context context)
  {
    ClipboardItemEditorView editor = new ClipboardItemEditorView(context,
        null);
    editor.setId(R.id.clipboard_item_editor);
    editor.setVisibility(View.GONE);
    EditText text = new EditText(context);
    text.setId(R.id.clipboard_editor_text);
    editor.addView(text);
    TextView paste = new TextView(context);
    paste.setId(R.id.clipboard_editor_paste);
    editor.addView(paste);
    TextView close = new TextView(context);
    close.setId(R.id.clipboard_editor_close);
    editor.addView(close);
    editor.onFinishInflate();
    return editor;
  }

  private static EditText editText(ClipboardItemEditorView editor)
  {
    return (EditText)editor.findViewById(R.id.clipboard_editor_text);
  }

  private static String styleItem(Document styles, String styleName,
      String itemName)
  {
    Element style = styleByName(styles, styleName);
    NodeList items = style.getElementsByTagName("item");
    for (int i = 0; i < items.getLength(); i++)
    {
      Element item = (Element)items.item(i);
      if (itemName.equals(item.getAttribute("name")))
        return item.getTextContent();
    }
    fail("Missing style item " + styleName + "/" + itemName);
    return null;
  }

  private static Element styleByName(Document styles, String styleName)
  {
    NodeList styleNodes = styles.getElementsByTagName("style");
    for (int i = 0; i < styleNodes.getLength(); i++)
    {
      Element style = (Element)styleNodes.item(i);
      if (styleName.equals(style.getAttribute("name")))
        return style;
    }
    fail("Missing style " + styleName);
    return null;
  }

  private static int dimenDp(Document values, String dimenName)
  {
    NodeList dimenNodes = values.getElementsByTagName("dimen");
    for (int i = 0; i < dimenNodes.getLength(); i++)
    {
      Element dimen = (Element)dimenNodes.item(i);
      if (!dimenName.equals(dimen.getAttribute("name")))
        continue;
      String value = dimen.getTextContent();
      assertTrue("Expected " + dimenName + " to be expressed in dp.",
          value.endsWith("dp"));
      return Integer.parseInt(value.substring(0, value.length() - 2));
    }
    fail("Missing dimen " + dimenName);
    return -1;
  }

  private static Element elementWithAndroidId(Document doc, String id)
  {
    NodeList nodes = doc.getElementsByTagName("*");
    for (int i = 0; i < nodes.getLength(); i++)
    {
      Element element = (Element)nodes.item(i);
      if (id.equals(element.getAttribute("android:id")))
        return element;
    }
    return null;
  }

  private static Element usesPermission(Document manifest, String permission)
  {
    NodeList nodes = manifest.getElementsByTagName("uses-permission");
    for (int i = 0; i < nodes.getLength(); i++)
    {
      Element element = (Element)nodes.item(i);
      if (permission.equals(element.getAttribute("android:name")))
        return element;
    }
    return null;
  }

  private static int fixedClipboardCardHeightPx(Context context)
  {
    return Math.round(112 * context.getResources().getDisplayMetrics().density);
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

    String lastImageUri = null;
    String lastImageMimeType = null;
    String lastImageDescription = null;

    @Override
    public void paste_image_from_clipboard_pane(String uri, String mimeType,
        String description)
    {
      lastImageUri = uri;
      lastImageMimeType = mimeType;
      lastImageDescription = description;
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
    public android.view.inputmethod.EditorInfo getCurrentInputEditorInfo()
    {
      return new android.view.inputmethod.EditorInfo();
    }

    @Override
    public Handler getHandler()
    {
      return handler;
    }

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
    ImageView image = new ImageView(context);
    image.setId(R.id.clipboard_entry_image);
    row.addView(image);
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
    ImageView image = new ImageView(context);
    image.setId(R.id.clipboard_pin_image);
    row.addView(image);
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
