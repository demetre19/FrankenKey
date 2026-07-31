package juloo.keyboard2;

import android.app.Notification;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.os.UserManager;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.shadows.ShadowLog;
import org.robolectric.shadows.ShadowUserManager;
import org.xmlpull.v1.XmlPullParser;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
public class ReaderSecurityAuditTest
{
  private Context _context;
  private UserManager _userManager;

  @Before
  public void setUp()
  {
    _context = RuntimeEnvironment.getApplication();
    _userManager = (UserManager)_context.getSystemService(Context.USER_SERVICE);
    shadowOf(_userManager).setUserUnlocked(true);
    _context.getSharedPreferences("reader_playback", Context.MODE_PRIVATE)
      .edit().clear().commit();
    ShadowLog.clear();
  }

  @After
  public void tearDown()
  {
    shadowOf(_userManager).setUserUnlocked(true);
    _context.getSharedPreferences("reader_playback", Context.MODE_PRIVATE)
      .edit().clear().commit();
  }

  @Test
  public void direct_boot_rejects_reader_sources_before_content_access()
  {
    String secret = "locked-reader-secret-4821";
    ClipboardManager clipboard = (ClipboardManager)_context.getSystemService(
        Context.CLIPBOARD_SERVICE);
    clipboard.setPrimaryClip(ClipData.newPlainText("private", secret));
    shadowOf(_userManager).setUserUnlocked(false);

    EditorInfo editor = new EditorInfo();
    editor.inputType = InputType.TYPE_CLASS_TEXT |
      InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE;
    assertEquals(ReaderTextAccess.Failure.USER_LOCKED,
        ReaderTextAccess.readClipboard(_context).failure);
    assertEquals(ReaderTextAccess.Failure.USER_LOCKED,
        ReaderTextAccess.readSelection(_context, editor, null).failure);
    assertEquals(ReaderTextAccess.Failure.USER_LOCKED,
        ReaderTextAccess.readCurrentField(_context, editor, null).failure);
    for (ShadowLog.LogItem item : ShadowLog.getLogs())
      assertFalse(item.msg != null && item.msg.contains(secret));
  }

  @Test
  public void reader_components_are_private_and_not_direct_boot_aware()
      throws Exception
  {
    PackageManager manager = _context.getPackageManager();
    ServiceInfo service = manager.getServiceInfo(new ComponentName(_context,
          ReaderPlaybackService.class), 0);
    ActivityInfo activity = manager.getActivityInfo(new ComponentName(_context,
          ReaderActivity.class), 0);

    assertFalse(service.exported);
    assertFalse(service.directBootAware);
    assertFalse(activity.exported);
    assertFalse(activity.directBootAware);
  }

  @Test
  public void reader_session_is_credential_private_backup_excluded_and_redacted()
      throws Exception
  {
    String secret = "reader-persistence-secret-7330";
    String privateTitle = "Private medical notes";
    ServiceController<ReaderPlaybackService> controller =
      Robolectric.buildService(ReaderPlaybackService.class).create();
    ReaderPlaybackService service = controller.get();
    try
    {
      service.load("private-item", privateTitle, secret, false);
      assertFalse("Reader persistence must remain unavailable during direct boot.",
          service.isDeviceProtectedStorage());
      assertEquals("App-private persistence is required for process recreation.",
          secret, service.getSharedPreferences("reader_playback", Context.MODE_PRIVATE)
            .getString("text", ""));

      Method buildNotification = ReaderPlaybackService.class
        .getDeclaredMethod("buildNotification");
      buildNotification.setAccessible(true);
      Notification notification = (Notification)buildNotification.invoke(service);
      assertEquals("Lock-screen notification title must not expose the document title.",
          service.getString(R.string.reader_default_title),
          notification.extras.getString(Notification.EXTRA_TITLE));
      assertNotEquals(privateTitle,
          notification.extras.getString(Notification.EXTRA_TITLE));
      for (ShadowLog.LogItem item : ShadowLog.getLogs())
        assertFalse(item.msg != null && item.msg.contains(secret));
    }
    finally
    {
      controller.destroy();
    }

    Resources resources = _context.getResources();
    int legacy = resources.getIdentifier("backup_rules", "xml",
        _context.getPackageName());
    int modern = resources.getIdentifier("data_extraction_rules", "xml",
        _context.getPackageName());
    assertNotEquals(0, legacy);
    assertNotEquals(0, modern);
    assertEquals("Legacy backup rules must use the full-backup-content schema.",
        "full-backup-content", rootName(resources.getXml(legacy)));
    assertEquals("Android 12+ rules must use the data-extraction-rules schema.",
        "data-extraction-rules", rootName(resources.getXml(modern)));
    assertEquals(1, countReaderExclusions(resources.getXml(legacy)));
    assertEquals(2, countReaderExclusions(resources.getXml(modern)));

    ApplicationInfo application = _context.getApplicationInfo();
    Field fullBackup = ApplicationInfo.class.getField("fullBackupContent");
    assertEquals(legacy, fullBackup.getInt(application));
  }

  @Test
  public void service_bounds_intent_metadata_and_rejects_oversized_content()
  {
    ServiceController<ReaderPlaybackService> controller =
      Robolectric.buildService(ReaderPlaybackService.class).create();
    ReaderPlaybackService service = controller.get();
    try
    {
      service.load(repeat('i', 400), repeat('t', 400), "safe text", false);
      ReaderPlaybackService.Snapshot bounded = service.snapshot();
      assertEquals(ReaderPlaybackService.MAX_ITEM_ID_LENGTH,
          bounded.itemId.length());
      assertEquals(ReaderPlaybackService.MAX_TITLE_LENGTH,
          bounded.title.length());

      service.load("item", "title",
          repeat('x', ReaderPlaybackService.MAX_ACTIVE_TEXT_LENGTH + 1), false);
      assertEquals(ReaderPlaybackService.Status.ERROR, service.snapshot().status);
      assertEquals("Previously accepted text must not be replaced by invalid input.",
          "safe text", service.activeText());
    }
    finally
    {
      controller.destroy();
    }
  }

  @Test
  public void article_preview_metadata_is_resolved_and_private_addresses_fail()
      throws Exception
  {
    String html = "<html><head><title>Safe article</title>" +
      "<meta property='og:image' content='/media/card.png'></head>" +
      "<body><article>Readable article text that is long enough.</article></body></html>";
    ReaderImportPipeline.Candidate candidate = ReaderArticleImporter.extract(
        html.getBytes(StandardCharsets.UTF_8), "text/html; charset=utf-8",
        "https://example.com/news/story");

    assertEquals("Relative Open Graph previews resolve against the approved article URL.",
        "https://example.com/media/card.png", candidate.imageUrl);
    assertFalse("Loopback preview targets must remain blocked by the SSRF policy.",
        ReaderArticleImporter.isPublicAddress(
          InetAddress.getByName("127.0.0.1")));
    assertFalse("Private preview targets must remain blocked by the SSRF policy.",
        ReaderArticleImporter.isPublicAddress(
          InetAddress.getByName("192.168.1.10")));
    assertTrue("Ordinary public addresses remain eligible for validated fetching.",
        ReaderArticleImporter.isPublicAddress(
          InetAddress.getByName("93.184.216.34")));
  }

  @Test
  public void article_body_selection_skips_page_chrome_and_keeps_inline_images()
      throws Exception
  {
    String html = "<html><head><title>Focused article</title></head><body>" +
      "<main><section><h2>Languages</h2><ul><li>Page chrome</li></ul></section>" +
      "<div id='mw-content-text'><div class='mw-parser-output'>" +
      "<div role='navigation'><ul><li>Related topic chrome</li></ul></div>" +
      "<h1>Focused story</h1>" +
      "<p>The complete article body remains readable without navigation noise.</p>" +
      "<img data-original='/media/body.webp' alt='Body diagram'>" +
      "</div></div></main></body></html>";
    ReaderImportPipeline.Candidate candidate = ReaderArticleImporter.extract(
        html.getBytes(StandardCharsets.UTF_8), "text/html; charset=utf-8",
        "https://example.com/news/story");

    assertFalse("Article extraction must not speak page chrome before the article body.",
        candidate.readingText().contains("Page chrome"));
    assertFalse("Navigation widgets inside the article container must not be spoken.",
        candidate.readingText().contains("Related topic chrome"));
    assertEquals("The article heading remains first.", "Focused story",
        candidate.units.get(0).text);
    assertEquals("An inline article image remains in reading order.", "image",
        candidate.units.get(2).kind);
    assertEquals("Lazy image sources resolve against the approved article URL.",
        "https://example.com/media/body.webp",
        candidate.units.get(2).sourceLocator);
  }

  private static String rootName(XmlResourceParser parser) throws Exception
  {
    try
    {
      for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT;
          event = parser.next())
      {
        if (event == XmlPullParser.START_TAG)
          return parser.getName();
      }
      return "";
    }
    finally
    {
      parser.close();
    }
  }

  private static int countReaderExclusions(XmlResourceParser parser)
      throws Exception
  {
    int count = 0;
    try
    {
      for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT;
          event = parser.next())
      {
        if (event == XmlPullParser.START_TAG && "exclude".equals(parser.getName()) &&
            "sharedpref".equals(parser.getAttributeValue(null, "domain")) &&
            "reader_playback.xml".equals(
              parser.getAttributeValue(null, "path")))
          count++;
      }
    }
    finally
    {
      parser.close();
    }
    return count;
  }

  private static String repeat(char value, int count)
  {
    StringBuilder out = new StringBuilder(count);
    for (int i = 0; i < count; i++)
      out.append(value);
    return out.toString();
  }
}
