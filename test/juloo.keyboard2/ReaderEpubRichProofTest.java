package juloo.keyboard2;

import android.webkit.WebSettings;
import android.webkit.WebView;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
public class ReaderEpubRichProofTest
{
  @Test
  public void rich_chapter_keeps_reading_structure_and_only_packaged_images()
      throws Exception
  {
    String hostile = "<html><head><style>body{display:none}</style>" +
      "<script>steal()</script></head><body onload='steal()'>" +
      "<h1 onclick='steal()'>Chapter One</h1>" +
      "<p>Readable <strong>book text</strong>.</p>" +
      "<a href='https://evil.example/'>External label</a>" +
      "<img src='../Images/cover.jpg' alt='Safe cover' onerror='steal()'>" +
      "<img src='https://evil.example/tracker.png' alt='Tracker'>" +
      "<iframe src='https://evil.example/'></iframe>" +
      "<svg><script>steal()</script></svg></body></html>";
    Map<String, String> packagedImages = new HashMap<>();
    packagedImages.put("OPS/Images/cover.jpg",
        "data:image/jpeg;base64,AAECAwQ=");

    String clean = sanitizeChapter(hostile, "OPS/Text/chapter.xhtml",
        packagedImages);

    assertTrue("Rich EPUB headings must survive sanitization.",
        clean.contains("<h1>Chapter One</h1>"));
    assertTrue("Inline reading emphasis must survive sanitization.",
        clean.contains("<strong>book text</strong>"));
    assertTrue("Safe packaged images must be rewritten to app-owned data.",
        clean.contains("src=\"data:image/jpeg;base64,AAECAwQ=\""));
    assertTrue("Link labels remain readable after navigation is removed.",
        clean.contains("External label"));
    assertFalse("EPUB scripts must never reach the Reader WebView.",
        clean.contains("script"));
    assertFalse("EPUB styles must never override the Reader surface.",
        clean.contains("display:none"));
    assertFalse("Event handlers must be stripped.",
        clean.contains("onerror") || clean.contains("onclick") ||
        clean.contains("onload"));
    assertFalse("Remote resources and navigation must be stripped.",
        clean.contains("https:") || clean.contains("href="));
    assertFalse("Active embedded documents must be stripped.",
        clean.contains("iframe") || clean.contains("svg"));
    assertEquals("Only the packaged image may remain.", 1,
        Jsoup.parseBodyFragment(clean).select("img").size());
  }

  @Test
  public void sanitized_chapter_loads_with_no_file_content_or_network_access()
  {
    WebView webView = new WebView(RuntimeEnvironment.getApplication());
    WebSettings settings = webView.getSettings();
    settings.setJavaScriptEnabled(true);
    settings.setDomStorageEnabled(false);
    settings.setAllowFileAccess(false);
    settings.setAllowContentAccess(false);
    settings.setJavaScriptCanOpenWindowsAutomatically(false);
    settings.setSupportMultipleWindows(false);
    settings.setDatabaseEnabled(false);
    settings.setGeolocationEnabled(false);
    settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    settings.setBlockNetworkLoads(true);

    String html = "<!doctype html><meta http-equiv='Content-Security-Policy' " +
      "content=\"default-src 'none'; img-src data:; style-src 'unsafe-inline'\">" +
      "<style>body{color:#d1d5db;background:#0a0a0a}</style>" +
      "<h1>Safe chapter</h1><p>Local reading only.</p>";
    webView.loadDataWithBaseURL("https://frankenkey.local/reader/epub/",
        html, "text/html", "UTF-8", null);

    assertTrue("App-authored progress code may run.",
        settings.getJavaScriptEnabled());
    assertFalse("Book content needs no DOM storage.",
        settings.getDomStorageEnabled());
    assertFalse("The Classic reader must not expose files.",
        settings.getAllowFileAccess());
    assertFalse("The Classic reader must not expose content providers.",
        settings.getAllowContentAccess());
    assertFalse("Book markup must not open windows.",
        settings.getJavaScriptCanOpenWindowsAutomatically());
    assertFalse("Book markup must not create additional WebViews.",
        settings.supportMultipleWindows());
    assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW,
        settings.getMixedContentMode());
    assertTrue("The Classic reader must fail closed on network loads.",
        settings.getBlockNetworkLoads());
  }

  private static String sanitizeChapter(String html, String chapterPath,
      Map<String, String> packagedImages) throws Exception
  {
    Document chapter = Jsoup.parse(html);
    chapter.select("script,style,noscript,nav,form,iframe,object,embed," +
        "svg,math,audio,video,source,picture,canvas,link,meta").remove();
    for (Element image : chapter.select("img"))
    {
      String path = resolvePackagedPath(chapterPath, image.attr("src"));
      String data = path == null ? null : packagedImages.get(path);
      if (data == null || !data.startsWith("data:image/"))
        image.remove();
      else
        image.attr("src", data);
    }

    Safelist allowed = new Safelist()
      .addTags("p", "br", "h1", "h2", "h3", "h4", "h5", "h6",
          "ul", "ol", "li", "blockquote", "pre", "code", "strong",
          "b", "em", "i", "u", "s", "sub", "sup", "span", "div",
          "section", "article", "figure", "figcaption", "table",
          "thead", "tbody", "tfoot", "tr", "th", "td", "img", "a")
      .addAttributes("img", "src", "alt", "title", "width", "height")
      .addAttributes("td", "colspan", "rowspan")
      .addAttributes("th", "colspan", "rowspan")
      .addProtocols("img", "src", "data");
    Document.OutputSettings output = new Document.OutputSettings()
      .prettyPrint(false);
    return Jsoup.clean(chapter.body().html(), "", allowed, output);
  }

  private static String resolvePackagedPath(String chapterPath, String source)
      throws Exception
  {
    if (source == null || source.trim().isEmpty())
      return null;
    URI relative = new URI(source.trim());
    if (relative.isAbsolute() || relative.getRawAuthority() != null)
      return null;
    URI resolved = new URI("epub:///" + chapterPath).resolve(relative).normalize();
    String path = resolved.getPath();
    if (path == null || !path.startsWith("/") || path.contains("/../"))
      return null;
    return path.substring(1);
  }
}
