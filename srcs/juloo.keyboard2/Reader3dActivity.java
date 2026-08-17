package juloo.keyboard2;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Private local host for the Private Drive mobile 3D Reader surface. */
public final class Reader3dActivity extends Activity
{
  private static final String EXTRA_ITEM_ID =
    "juloo.keyboard2.extra.READER_3D_ITEM_ID";
  private static final String EXTRA_HANDOFF_TOKEN =
    "juloo.keyboard2.extra.READER_3D_HANDOFF_TOKEN";
  private static final String HANDOFF_DIRECTORY = "reader_3d";
  private static final long HANDOFF_MAX_AGE_MS = 24L * 60L * 60L * 1000L;
  private static final int MAX_BRIDGE_CHUNK = 32768;
  private static final Pattern WORD_PATTERN = Pattern.compile("\\S+");
  private static final byte[] BLOCKED_RESPONSE = new byte[0];

  private final ExecutorService _bookAiLoader =
    Executors.newSingleThreadExecutor();
  private WebView _webView;
  private ReaderDocument _document;
  private File _handoffFile;
  private int _latestRawWordIndex;
  private boolean _latestFinished;
  private boolean _openingClassic;
  private boolean _destroyed;

  static boolean start(Activity host, String itemId, String title, String text)
  {
    Intent intent = new Intent(host, Reader3dActivity.class);
    if (itemId != null && !itemId.isEmpty())
      intent.putExtra(EXTRA_ITEM_ID, itemId);
    else
    {
      File handoff = createHandoff(host, title, text);
      if (handoff == null)
        return false;
      intent.putExtra(EXTRA_HANDOFF_TOKEN, handoff.getName());
    }
    host.startActivity(intent);
    return true;
  }

  static boolean startEpub(Activity host, ReaderLibrary.Item item, String title,
      String text, String chapterRangesJson, int rawWordIndex)
  {
    if (item == null || item.sourceType != ReaderLibrary.SourceType.EPUB)
      return false;
    File handoff = createHandoff(host, title, text, item.id,
        chapterRangesJson, rawWordIndex);
    if (handoff == null)
      return false;
    Intent intent = new Intent(host, Reader3dActivity.class)
      .putExtra(EXTRA_ITEM_ID, item.id)
      .putExtra(EXTRA_HANDOFF_TOKEN, handoff.getName());
    host.startActivity(intent);
    return true;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    if (getActionBar() != null)
      getActionBar().hide();
    getWindow().setStatusBarColor(Color.BLACK);
    getWindow().setNavigationBarColor(Color.BLACK);
    _document = loadDocument();
    if (_document == null || _document.text.trim().isEmpty())
    {
      finish();
      return;
    }
    _latestRawWordIndex = _document.progressRawWordIndex;
    _webView = new WebView(this);
    _webView.setBackgroundColor(Color.BLACK);
    configureWebView(_webView);
    _webView.setOnApplyWindowInsetsListener((View view, WindowInsets insets) ->
    {
      view.setPadding(insets.getSystemWindowInsetLeft(),
          insets.getSystemWindowInsetTop(),
          insets.getSystemWindowInsetRight(),
          insets.getSystemWindowInsetBottom());
      return insets;
    });
    setContentView(_webView, new ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT));
    String html = readAsset("reader_3d.html");
    if (html == null)
    {
      finish();
      return;
    }
    _webView.addJavascriptInterface(new NativeBridge(), "Native");
    _webView.loadDataWithBaseURL("https://frankenkey.local/reader/", html,
        "text/html", "UTF-8", null);
  }


  private void configureWebView(WebView webView)
  {
    WebSettings settings = webView.getSettings();
    settings.setJavaScriptEnabled(true);
    settings.setDomStorageEnabled(true);
    settings.setAllowFileAccess(false);
    settings.setAllowContentAccess(false);
    settings.setJavaScriptCanOpenWindowsAutomatically(false);
    settings.setSupportMultipleWindows(false);
    settings.setDatabaseEnabled(false);
    settings.setGeolocationEnabled(false);
    settings.setMediaPlaybackRequiresUserGesture(true);
    settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
    webView.setWebViewClient(new WebViewClient()
    {
      @Override public boolean shouldOverrideUrlLoading(WebView view,
          WebResourceRequest request)
      {
        return true;
      }

      @SuppressWarnings("deprecation")
      @Override public boolean shouldOverrideUrlLoading(WebView view,
          String url)
      {
        return true;
      }

      @Override public WebResourceResponse shouldInterceptRequest(WebView view,
          WebResourceRequest request)
      {
        return blockedResponse(request.getUrl());
      }

      @SuppressWarnings("deprecation")
      @Override public WebResourceResponse shouldInterceptRequest(WebView view,
          String url)
      {
        return blockedResponse(Uri.parse(url));
      }
    });
  }

  private static WebResourceResponse blockedResponse(Uri uri)
  {
    String scheme = uri == null ? null : uri.getScheme();
    if (scheme == null || "data".equalsIgnoreCase(scheme) ||
        "about".equalsIgnoreCase(scheme))
      return null;
    return new WebResourceResponse("text/plain", "UTF-8",
        new ByteArrayInputStream(BLOCKED_RESPONSE));
  }

  private ReaderDocument loadDocument()
  {
    String itemId = getIntent().getStringExtra(EXTRA_ITEM_ID);
    String token = getIntent().getStringExtra(EXTRA_HANDOFF_TOKEN);
    JSONObject payload = null;
    if (safeToken(token))
    {
      _handoffFile = new File(handoffDirectory(this), token);
      payload = readJson(_handoffFile);
      if (payload == null)
        return null;
    }
    if (itemId != null && !itemId.isEmpty())
    {
      try (ReaderLibrary library = new ReaderLibrary(this))
      {
        ReaderLibrary.Item item = library.get(itemId);
        if (item == null)
          return null;
        if (payload != null && item.sourceType == ReaderLibrary.SourceType.EPUB)
          return ReaderDocument.fromEpub(item, payload.optString("text"),
              payload.optString("chapterRanges"),
              payload.optInt("rawWordIndex", (int)item.rawWordIndex));
        return ReaderDocument.fromItem(item);
      }
      catch (ReaderLibrary.LibraryException error)
      {
        return null;
      }
    }
    if (payload == null)
      return null;
    return ReaderDocument.fromText(payload.optString("id"),
        payload.optString("title"), payload.optString("text"));
  }

  private String readAsset(String name)
  {
    try (InputStream input = getAssets().open(name))
    {
      char[] buffer = new char[8192];
      StringBuilder result = new StringBuilder();
      int count;
      InputStreamReader reader = new InputStreamReader(input,
          StandardCharsets.UTF_8);
      while ((count = reader.read(buffer)) != -1)
        result.append(buffer, 0, count);
      return result.toString();
    }
    catch (IOException error)
    {
      return null;
    }
  }

  private final class NativeBridge
  {
    @JavascriptInterface public int textLength()
    {
      return _document.text.length();
    }

    @JavascriptInterface public String textChunk(int start, int length)
    {
      int safeStart = Math.max(0, Math.min(start, _document.text.length()));
      int safeLength = Math.max(0, Math.min(length, MAX_BRIDGE_CHUNK));
      int end = Math.min(_document.text.length(), safeStart + safeLength);
      return _document.text.substring(safeStart, end);
    }

    @JavascriptInterface public boolean canOpenReaderAi()
    {
      return Reader3dActivity.this.canOpenReaderAi();
    }

    @JavascriptInterface public void openReaderAi()
    {
      runOnUiThread(() -> Reader3dActivity.this.openReaderAi());
    }

    @JavascriptInterface public boolean canOpenClassicReader()
    {
      return _document.item != null &&
        _document.item.sourceType == ReaderLibrary.SourceType.EPUB;
    }

    @JavascriptInterface public void openClassicReader()
    {
      runOnUiThread(() -> Reader3dActivity.this.openClassicReader());
    }

    @JavascriptInterface public String documentId()
    {
      return _document.id;
    }

    @JavascriptInterface public String documentTitle()
    {
      return _document.title;
    }

    @JavascriptInterface public String chapterRanges()
    {
      return _document.chapterRangesJson;
    }

    @JavascriptInterface public int progressRawWordIndex()
    {
      return _document.progressRawWordIndex;
    }

    @JavascriptInterface public void saveProgress(int rawWordIndex,
        int totalRawWords, boolean finished)
    {
      if (totalRawWords != _document.rawWordStarts.length)
        return;
      _latestRawWordIndex = Math.max(0,
          Math.min(rawWordIndex, _document.rawWordStarts.length));
      _latestFinished = finished;
      persistProgress();
    }

    @JavascriptInterface public void closeReader()
    {
      runOnUiThread(() -> finishWithProgress());
    }
  }

  private synchronized void persistProgress()
  {
    if (_document.item == null)
      return;
    int rawIndex = _latestFinished ? _document.rawWordStarts.length :
      Math.max(0, Math.min(_latestRawWordIndex,
            _document.rawWordStarts.length));
    boolean finished = _latestFinished ||
      rawIndex >= _document.rawWordStarts.length;
    try (ReaderLibrary library = new ReaderLibrary(this))
    {
      if (_document.item.sourceType == ReaderLibrary.SourceType.EPUB)
      {
        BookPosition position = _document.bookPosition(rawIndex);
        float fraction = _document.rawWordStarts.length == 0 ? 0f :
          rawIndex / (float)_document.rawWordStarts.length;
        library.updateBookProgress(_document.item.id, rawIndex,
            position.chapter, position.charOffset, position.anchor,
            Math.max(0f, Math.min(1f, fraction)), finished,
            ReaderLibrary.ReaderMode.THREE_D, System.currentTimeMillis());
      }
      else
      {
        int documentOffset = _document.characterOffsetForRawWord(rawIndex);
        String locator;
        float fraction;
        if (_document.item.sourceType == ReaderLibrary.SourceType.URL)
        {
          locator = "article:" + documentOffset;
          fraction = _document.text.isEmpty() ? 0f :
            documentOffset / (float)_document.text.length();
        }
        else
        {
          UnitRange unit = _document.unitForDocumentOffset(documentOffset);
          int localOffset = Math.max(0, Math.min(unit.sourceLength,
                documentOffset - unit.documentStart));
          locator = "unit:" + unit.index + ":" + localOffset;
          fraction = _document.sourceLength == 0 ? 0f :
            (unit.completedSourceLength + localOffset) /
            (float)_document.sourceLength;
        }
        library.updateProgress(_document.item.id, locator,
            Math.max(0f, Math.min(1f, fraction)), finished,
            System.currentTimeMillis());
      }
    }
    catch (ReaderLibrary.LibraryException ignored) {}
  }

  private void openClassicReader()
  {
    if (_document == null || _document.item == null ||
        _document.item.sourceType != ReaderLibrary.SourceType.EPUB)
      return;
    persistProgress();
    try (ReaderLibrary library = new ReaderLibrary(this))
    {
      BookPosition position = _document.bookPosition(_latestRawWordIndex);
      float fraction = _document.rawWordStarts.length == 0 ? 0f :
        _latestRawWordIndex / (float)_document.rawWordStarts.length;
      library.updateBookProgress(_document.item.id, _latestRawWordIndex,
          position.chapter, position.charOffset, position.anchor,
          Math.max(0f, Math.min(1f, fraction)), false,
          ReaderLibrary.ReaderMode.CLASSIC, System.currentTimeMillis());
      library.updateGlobalLastReaderMode(ReaderLibrary.ReaderMode.CLASSIC);
    }
    catch (ReaderLibrary.LibraryException error)
    {
      return;
    }
    _openingClassic = true;
    startActivity(ReaderEpubActivity.intent(this, _document.item.id, true));
    finish();
  }
  private void finishWithProgress()
  {
    persistProgress();
    finish();
  }

  @Override public void onBackPressed()
  {
    finishWithProgress();
  }

  @Override protected void onPause()
  {
    if (!_openingClassic && _webView != null)
      _webView.evaluateJavascript(
          "window.reader3dPause&&window.reader3dPause()", null);
    super.onPause();
  }

  @Override protected void onDestroy()
  {
    _destroyed = true;
    _bookAiLoader.shutdownNow();
    if (!_openingClassic)
      persistProgress();
    if (_webView != null)
    {
      _webView.removeJavascriptInterface("Native");
      _webView.stopLoading();
      _webView.destroy();
      _webView = null;
    }
    if (isFinishing() && _handoffFile != null)
      _handoffFile.delete();
    super.onDestroy();
  }

  private static File createHandoff(Context context, String title, String text)
  {
    return createHandoff(context, title, text, null, null, 0);
  }

  private static File createHandoff(Context context, String title, String text,
      String itemId, String chapterRangesJson, int rawWordIndex)
  {
    if (text == null || text.trim().isEmpty())
      return null;
    File directory = handoffDirectory(context);
    purgeOldHandoffs(directory);
    String token = UUID.randomUUID().toString() + ".json";
    File target = new File(directory, token);
    JSONObject payload = new JSONObject();
    try
    {
      payload.put("id", itemId == null ? "reader-3d:" + token : itemId);
      payload.put("title", title == null || title.trim().isEmpty()
          ? context.getString(R.string.reader_default_title) : title);
      payload.put("text", text);
      if (chapterRangesJson != null)
        payload.put("chapterRanges", chapterRangesJson);
      payload.put("rawWordIndex", Math.max(0, rawWordIndex));
      try (FileOutputStream output = new FileOutputStream(target))
      {
        output.write(payload.toString().getBytes(StandardCharsets.UTF_8));
      }
      return target;
    }
    catch (IOException | JSONException error)
    {
      target.delete();
      return null;
    }
  }

  private static File handoffDirectory(Context context)
  {
    File directory = new File(context.getCacheDir(), HANDOFF_DIRECTORY);
    directory.mkdirs();
    return directory;
  }

  private static void purgeOldHandoffs(File directory)
  {
    File[] files = directory.listFiles();
    if (files == null)
      return;
    long cutoff = System.currentTimeMillis() - HANDOFF_MAX_AGE_MS;
    for (File file : files)
      if (file.lastModified() < cutoff)
        file.delete();
  }

  private static boolean safeToken(String token)
  {
    return token != null && token.matches(
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.json");
  }

  private static JSONObject readJson(File file)
  {
    if (file == null || !file.isFile())
      return null;
    try (FileInputStream input = new FileInputStream(file))
    {
      char[] buffer = new char[8192];
      StringBuilder json = new StringBuilder();
      int count;
      InputStreamReader reader = new InputStreamReader(input,
          StandardCharsets.UTF_8);
      while ((count = reader.read(buffer)) != -1)
        json.append(buffer, 0, count);
      return new JSONObject(json.toString());
    }
    catch (IOException | JSONException error)
    {
      return null;
    }
  }

  static final class BookPosition
  {
    final int chapter;
    final int charOffset;
    final String anchor;

    BookPosition(int chapter, int charOffset, String anchor)
    {
      this.chapter = chapter;
      this.charOffset = charOffset;
      this.anchor = anchor;
    }
  }

  static final class UnitRange
  {
    final int index;
    final int documentStart;
    final int sourceLength;
    final int completedSourceLength;

    UnitRange(int index, int documentStart, int sourceLength,
        int completedSourceLength)
    {
      this.index = index;
      this.documentStart = documentStart;
      this.sourceLength = sourceLength;
      this.completedSourceLength = completedSourceLength;
    }
  }

  private boolean canOpenReaderAi()
  {
    ReaderLibrary.Item item = _document == null ? null : _document.item;
    return item != null && !_document.text.trim().isEmpty() &&
      isReaderAiItem(item);
  }

  static boolean isReaderAiItem(ReaderLibrary.Item item)
  {
    if (item == null)
      return false;
    if (item.sourceType == ReaderLibrary.SourceType.EPUB)
      return ReaderEpubActivity.isReadableItem(item);
    return item.sourceType == ReaderLibrary.SourceType.URL &&
      ReaderActivity.isSafeOriginalUri(item.sourceUri);
  }

  private void openReaderAi()
  {
    if (!canOpenReaderAi())
      return;
    ReaderLibrary.Item item = _document.item;
    if (item.sourceType == ReaderLibrary.SourceType.URL)
    {
      Uri source = Uri.parse(item.sourceUri);
      ReaderAiDialog.show(this, new ReaderAiService.Article(
            item.id, item.title, item.sourceUri,
            source.getHost() == null ? "" : source.getHost(),
            item.author, item.contentHash, _document.text));
      return;
    }
    Uri source = Uri.parse(item.sourceUri);
    _bookAiLoader.execute(() ->
    {
      ReaderEpubImporter.Book book = null;
      try
      {
        book = ReaderEpubImporter.readUri(this, source);
      }
      catch (ReaderImportPipeline.ImportException ignored) {}
      ReaderEpubImporter.Book result = book;
      runOnUiThread(() ->
      {
        if (_destroyed)
          return;
        if (result == null || result.chapters.isEmpty())
        {
          Toast.makeText(this, R.string.reader_epub_open_error,
              Toast.LENGTH_SHORT).show();
          return;
        }
        ReaderAiDialog.show(this, ReaderAiService.Article.book(item, result));
      });
    });
  }

  static final class ReaderDocument
  {
    final String id;
    final String title;
    final String text;
    final ReaderLibrary.Item item;
    final int[] rawWordStarts;
    final List<UnitRange> units;
    final int sourceLength;
    final String chapterRangesJson;
    final int progressRawWordIndex;

    private ReaderDocument(String id, String title, String text,
        ReaderLibrary.Item item, List<UnitRange> units, int sourceLength,
        int progressDocumentOffset, JSONArray chapterRanges,
        int explicitRawWordIndex)
    {
      this.id = id == null || id.isEmpty() ? "reader-3d" : id;
      this.title = title == null || title.trim().isEmpty() ? "Reader" : title;
      this.text = text == null ? "" : text;
      this.item = item;
      this.units = units;
      this.sourceLength = sourceLength;
      this.rawWordStarts = wordStarts(this.text);
      this.chapterRangesJson = chapterRanges.toString();
      this.progressRawWordIndex = explicitRawWordIndex >= 0
        ? Math.max(0, Math.min(explicitRawWordIndex, rawWordStarts.length))
        : rawWordIndexAt(progressDocumentOffset);
    }

    static ReaderDocument fromText(String id, String title, String text)
    {
      String safeText = text == null ? "" : text;
      ArrayList<UnitRange> units = new ArrayList<>();
      units.add(new UnitRange(0, 0, safeText.length(), 0));
      JSONArray ranges = new JSONArray();
      JSONObject range = new JSONObject();
      try
      {
        range.put("start", 0);
        range.put("end", wordStarts(safeText).length);
        ranges.put(range);
      }
      catch (JSONException impossible) {}
      return new ReaderDocument(id, title, safeText, null, units,
          safeText.length(), 0, ranges, -1);
    }

    static ReaderDocument fromEpub(ReaderLibrary.Item item, String text,
        String chapterRangesJson, int rawWordIndex)
    {
      if (item == null || item.sourceType != ReaderLibrary.SourceType.EPUB)
        return null;
      String safeText = text == null ? "" : text;
      JSONArray ranges;
      try
      {
        ranges = new JSONArray(chapterRangesJson == null
            ? "[]" : chapterRangesJson);
      }
      catch (JSONException error)
      {
        ranges = new JSONArray();
      }
      if (ranges.length() == 0)
      {
        JSONObject range = new JSONObject();
        try
        {
          range.put("start", 0);
          range.put("end", wordStarts(safeText).length);
          ranges.put(range);
        }
        catch (JSONException impossible) {}
      }
      ArrayList<UnitRange> units = new ArrayList<>();
      units.add(new UnitRange(0, 0, safeText.length(), 0));
      return new ReaderDocument(item.id, item.title, safeText, item, units,
          safeText.length(), 0, ranges, rawWordIndex);
    }

    static ReaderDocument fromItem(ReaderLibrary.Item item)
    {
      StringBuilder text = new StringBuilder();
      ArrayList<UnitRange> units = new ArrayList<>();
      int completedSourceLength = 0;
      ArrayList<Integer> chapterStarts = new ArrayList<>();
      int rawWordCount = 0;
      for (int i = 0; i < item.units.size(); i++)
      {
        ReaderLibrary.ContentUnit unit = item.units.get(i);
        if (text.length() > 0)
          text.append("\n\n");
        int documentStart = text.length();
        String sourceText = unit.text == null ? "" : unit.text;
        String renderedText = item.sourceType == ReaderLibrary.SourceType.URL &&
          "image".equals(unit.kind) ? "\n" : sourceText;
        if (item.sourceType == ReaderLibrary.SourceType.EPUB)
          chapterStarts.add(rawWordCount);
        text.append(renderedText);
        rawWordCount += wordStarts(renderedText).length;
        units.add(new UnitRange(i, documentStart, sourceText.length(),
              completedSourceLength));
        completedSourceLength += sourceText.length();
      }
      int progressOffset = progressDocumentOffset(item, units, text.length());
      JSONArray chapters = new JSONArray();
      int totalWords = rawWordCount;
      if (chapterStarts.isEmpty())
        chapterStarts.add(0);
      for (int i = 0; i < chapterStarts.size(); i++)
      {
        JSONObject range = new JSONObject();
        try
        {
          range.put("start", chapterStarts.get(i));
          range.put("end", i + 1 < chapterStarts.size()
              ? chapterStarts.get(i + 1) : totalWords);
          chapters.put(range);
        }
        catch (JSONException impossible) {}
      }
      return new ReaderDocument(item.id, item.title, text.toString(), item,
          units, completedSourceLength, progressOffset, chapters,
          item.sourceType == ReaderLibrary.SourceType.EPUB
            ? (int)Math.min(Integer.MAX_VALUE, item.rawWordIndex) : -1);
    }

    private static int progressDocumentOffset(ReaderLibrary.Item item,
        List<UnitRange> units, int documentLength)
    {
      String locator = item.progressLocator;
      if (item.sourceType == ReaderLibrary.SourceType.URL && locator != null &&
          locator.startsWith("article:"))
        return boundedInteger(locator.substring("article:".length()),
            documentLength);
      if (locator != null && locator.startsWith("unit:"))
      {
        String[] parts = locator.split(":", 3);
        int index = parts.length > 1 ? boundedInteger(parts[1],
            Math.max(0, units.size() - 1)) : 0;
        if (!units.isEmpty())
        {
          UnitRange unit = units.get(index);
          int offset = parts.length > 2 ? boundedInteger(parts[2],
              unit.sourceLength) : 0;
          return Math.min(documentLength, unit.documentStart + offset);
        }
      }
      return Math.max(0, Math.min(documentLength,
            Math.round(documentLength * item.progressFraction)));
    }

    private static int boundedInteger(String value, int maximum)
    {
      try
      {
        return Math.max(0, Math.min(maximum, Integer.parseInt(value)));
      }
      catch (NumberFormatException error)
      {
        return 0;
      }
    }

    private int rawWordIndexAt(int characterOffset)
    {
      int low = 0;
      int high = rawWordStarts.length;
      while (low < high)
      {
        int middle = (low + high) >>> 1;
        if (rawWordStarts[middle] < characterOffset)
          low = middle + 1;
        else
          high = middle;
      }
      return low;
    }

    int characterOffsetForRawWord(int rawWordIndex)
    {
      if (rawWordIndex <= 0 || rawWordStarts.length == 0)
        return 0;
      if (rawWordIndex >= rawWordStarts.length)
        return text.length();
      return rawWordStarts[rawWordIndex];
    }

    UnitRange unitForDocumentOffset(int documentOffset)
    {
      if (units.isEmpty())
        return new UnitRange(0, 0, text.length(), 0);
      UnitRange selected = units.get(0);
      for (UnitRange unit : units)
      {
        if (unit.documentStart > documentOffset)
          break;
        selected = unit;
      }
      return selected;
    }

    BookPosition bookPosition(int rawWordIndex)
    {
      int boundedRaw = Math.max(0,
          Math.min(rawWordIndex, rawWordStarts.length));
      int chapter = 0;
      int chapterStartRaw = 0;
      try
      {
        JSONArray ranges = new JSONArray(chapterRangesJson);
        for (int i = 0; i < ranges.length(); i++)
        {
          JSONObject range = ranges.optJSONObject(i);
          if (range == null)
            continue;
          int start = Math.max(0, range.optInt("start", 0));
          int end = Math.max(start, range.optInt("end", rawWordStarts.length));
          if (boundedRaw < end || i == ranges.length() - 1)
          {
            chapter = i;
            chapterStartRaw = start;
            break;
          }
        }
      }
      catch (JSONException ignored) {}
      int documentOffset = characterOffsetForRawWord(boundedRaw);
      int chapterOffset = characterOffsetForRawWord(chapterStartRaw);
      int charOffset = Math.max(0, documentOffset - chapterOffset);
      int anchorStart = Math.max(0, documentOffset - 40);
      int anchorEnd = Math.min(text.length(), documentOffset + 80);
      String anchor = text.substring(anchorStart, anchorEnd)
        .replaceAll("\\s+", " ").trim();
      return new BookPosition(chapter, charOffset,
          anchor.isEmpty() ? null : anchor);
    }

    private static int[] wordStarts(String text)
    {
      Matcher matcher = WORD_PATTERN.matcher(text == null ? "" : text);
      ArrayList<Integer> starts = new ArrayList<>();
      while (matcher.find())
        starts.add(matcher.start());
      int[] result = new int[starts.size()];
      for (int i = 0; i < starts.size(); i++)
        result[i] = starts.get(i);
      return result;
    }
  }
}
