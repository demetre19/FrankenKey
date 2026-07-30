package juloo.keyboard2;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.PersistableBundle;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowLog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
public class ReaderTextAccessTest
{
  private Context _context;
  private ClipboardManager _clipboard;
  private SharedPreferences _prefs;
  private ClipboardHistoryService _history;

  @Before
  public void setUp()
  {
    _context = RuntimeEnvironment.getApplication();
    _clipboard = (ClipboardManager)_context.getSystemService(
        Context.CLIPBOARD_SERVICE);
    _prefs = _context.getSharedPreferences(
        "reader_text_access_test", Context.MODE_PRIVATE);
    _prefs.edit().clear()
      .putBoolean("clipboard_history_enabled", true)
      .putBoolean("clipboard_save_screenshots", true)
      .putString("clipboard_history_duration", "-1")
      .commit();
    installTestConfig();
    ClipboardHistoryService._service = null;
    ClipboardHistoryService._paste_callback = null;
    _history = new ClipboardHistoryService(_context);
    ClipboardHistoryService._service = _history;
    _history.clear_history();
    ShadowLog.clear();
  }

  @After
  public void tearDown()
  {
    if (_history != null)
      _history.clear_history();
    ClipboardHistoryService._service = null;
    ClipboardHistoryService._paste_callback = null;
    if (_prefs != null)
      _prefs.edit().clear().commit();
  }

  @Test
  public void sensitive_clipboard_is_neither_exposed_logged_nor_saved_to_history()
  {
    String secret = "reader-secret-sentinel-9384";
    ClipData sensitive = ClipData.newPlainText("password", secret);
    PersistableBundle extras = new PersistableBundle();
    extras.putBoolean(ReaderTextAccess.EXTRA_IS_SENSITIVE, true);
    sensitive.getDescription().setExtras(extras);
    _clipboard.setPrimaryClip(sensitive);

    ReaderTextAccess.Result result = ReaderTextAccess.readClipboard(_context);
    _history.add_current_clip();

    assertEquals(ReaderTextAccess.Failure.SENSITIVE, result.failure);
    assertNull("Sensitive clipboard text must never leave the access boundary.",
        result.text);
    assertTrue("Sensitive clipboard text must never appear in clipboard history.",
        _history.clear_expired_and_get_entries().isEmpty());
    for (ShadowLog.LogItem item : ShadowLog.getLogs())
      assertFalse("Reader text must never be written to Android logs.",
          item.msg != null && item.msg.contains(secret));
  }

  @Test
  public void ordinary_plain_text_clipboard_is_available_without_coercing_non_text()
  {
    _clipboard.setPrimaryClip(ClipData.newPlainText("note", "Read this note"));
    ReaderTextAccess.Result plain = ReaderTextAccess.readClipboard(_context);
    assertTrue(plain.isSuccess());
    assertEquals("Read this note", plain.text);

    _clipboard.setPrimaryClip(new ClipData("image", new String[]{"image/png"},
        new ClipData.Item(android.net.Uri.parse("content://private/image"))));
    ReaderTextAccess.Result image = ReaderTextAccess.readClipboard(_context);
    assertEquals("Reader must not coerce a URI or image into speakable text.",
        ReaderTextAccess.Failure.UNAVAILABLE, image.failure);
    assertNull(image.text);
  }

  @Test
  public void opening_source_dialog_defers_clipboard_read_until_user_selects_it()
  {
    final int[] clipboardReads = {0};
    Keyboard2.ReaderSourceAccess access = new Keyboard2.ReaderSourceAccess()
    {
      @Override public ReaderTextAccess.Result readClipboard()
      {
        clipboardReads[0]++;
        return ReaderTextAccess.readClipboard(_context);
      }

      @Override public ReaderTextAccess.Result readSelection()
      {
        throw new AssertionError("Selection was not chosen.");
      }

      @Override public ReaderTextAccess.Result readCurrentField()
      {
        throw new AssertionError("Current field was not chosen.");
      }
    };
    _clipboard.setPrimaryClip(ClipData.newPlainText("old", "Old safe text"));

    List<Keyboard2.ReaderSourceOption> options =
      Keyboard2.reader_source_options(false, false, access);

    assertEquals("Constructing the actual dialog options must not read clipboard data.",
        0, clipboardReads[0]);
    assertEquals("Even without editor text, the menu keeps Clipboard and Open Reader.",
        2, options.size());
    Keyboard2.ReaderSourceOption openReader = options.get(1);
    assertEquals(R.string.launcher_button_reader, openReader.label);
    assertNull("Open Reader is a navigation option, not a text read action.",
        openReader.action);
    ClipData sensitive = ClipData.newPlainText("password", "Changed secret");
    PersistableBundle extras = new PersistableBundle();
    extras.putBoolean(ReaderTextAccess.EXTRA_IS_SENSITIVE, true);
    sensitive.getDescription().setExtras(extras);
    _clipboard.setPrimaryClip(sensitive);

    ReaderTextAccess.Result result = options.get(0).action.read();

    assertEquals(1, clipboardReads[0]);
    assertEquals("The chooser must not cache clipboard text when it opens.",
        ReaderTextAccess.Failure.SENSITIVE, result.failure);
    assertNull("Selection-time validation must reject the latest sensitive clip.",
        result.text);
  }

  @Test
  public void editor_policy_allows_prose_and_rejects_secrets_terminals_and_structured_fields()
  {
    assertTrue(ReaderTextAccess.isReadableEditor(editor(
        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL,
        "com.example.notes", 0)));
    assertTrue(ReaderTextAccess.isReadableEditor(editor(
        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE,
        "com.example.notes", 0)));

    assertFalse(ReaderTextAccess.isReadableEditor(editor(
        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
        "com.example.login", 0)));
    assertFalse(ReaderTextAccess.isReadableEditor(editor(
        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
        "com.example.login", 0)));
    assertFalse(ReaderTextAccess.isReadableEditor(editor(
        InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD,
        "com.example.login", 0)));
    assertFalse(ReaderTextAccess.isReadableEditor(editor(InputType.TYPE_NULL,
        "com.termux", 0)));
    assertFalse(ReaderTextAccess.isReadableEditor(editor(
        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE |
          InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
        "dev.cmux.connector.shell",
        EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN)));
    assertFalse(ReaderTextAccess.isReadableEditor(editor(
        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI,
        "com.example.browser", 0)));
    assertFalse(ReaderTextAccess.isReadableEditor(editor(
        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
        "com.example.mail", 0)));
    assertFalse(ReaderTextAccess.isReadableEditor(editor(
        InputType.TYPE_CLASS_NUMBER, "com.example.calculator", 0)));
  }

  @Test
  public void unsafe_editors_are_rejected_before_requesting_any_text()
  {
    RecordingInputConnection connection = new RecordingInputConnection();
    EditorInfo password = editor(
        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
        "com.example.login", 0);

    assertEquals(ReaderTextAccess.Failure.UNSAFE_EDITOR,
        ReaderTextAccess.readSelection(password, connection).failure);
    assertEquals(ReaderTextAccess.Failure.UNSAFE_EDITOR,
        ReaderTextAccess.readCurrentField(password, connection).failure);
    assertEquals("Fail-closed privacy must happen before selection readback.",
        0, connection.selectionRequests);
    assertEquals("Fail-closed privacy must happen before field readback.",
        0, connection.extractedRequests);
  }

  @Test
  public void selection_and_complete_current_field_are_read_but_partial_readback_fails_closed()
  {
    EditorInfo prose = editor(
        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE,
        "com.example.notes", 0);
    RecordingInputConnection connection = new RecordingInputConnection();
    connection.selection = "selected words";
    connection.extracted = extracted("Complete current field", -1, 0);

    assertEquals("selected words",
        ReaderTextAccess.readSelection(prose, connection).text);
    assertEquals("Complete current field",
        ReaderTextAccess.readCurrentField(prose, connection).text);

    connection.extracted = extracted("only a fragment", 4, 4);
    ReaderTextAccess.Result partial =
      ReaderTextAccess.readCurrentField(prose, connection);
    assertEquals("Reader must not silently speak a partial current field.",
        ReaderTextAccess.Failure.UNAVAILABLE, partial.failure);
    assertNull(partial.text);
  }

  private static EditorInfo editor(int inputType, String packageName,
      int imeOptions)
  {
    EditorInfo editor = new EditorInfo();
    editor.inputType = inputType;
    editor.packageName = packageName;
    editor.imeOptions = imeOptions;
    return editor;
  }

  private static ExtractedText extracted(String text, int partialStart,
      int startOffset)
  {
    ExtractedText extracted = new ExtractedText();
    extracted.text = text;
    extracted.partialStartOffset = partialStart;
    extracted.partialEndOffset = partialStart;
    extracted.startOffset = startOffset;
    return extracted;
  }

  private static final class RecordingInputConnection
      extends BaseInputConnection
  {
    CharSequence selection;
    ExtractedText extracted;
    int selectionRequests;
    int extractedRequests;

    RecordingInputConnection()
    {
      super(new View(RuntimeEnvironment.getApplication()), false);
    }

    @Override
    public CharSequence getSelectedText(int flags)
    {
      selectionRequests++;
      return selection;
    }

    @Override
    public ExtractedText getExtractedText(ExtractedTextRequest request,
        int flags)
    {
      extractedRequests++;
      return extracted;
    }
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
      throw new AssertionError("Reader tests need clipboard preferences but not keyboard layout XML initialization.", e);
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
      super(base.getAssets(), base.getDisplayMetrics(), base.getConfiguration());
    }
  }
}
