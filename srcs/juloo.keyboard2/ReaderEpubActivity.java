package juloo.keyboard2;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Private, transient rich-reading surface for Library EPUB books. */
public final class ReaderEpubActivity extends Activity
{
  private static final String EXTRA_ITEM_ID =
    "juloo.keyboard2.extra.READER_EPUB_ITEM_ID";
  private static final String EXTRA_FORCE_CLASSIC =
    "juloo.keyboard2.extra.READER_EPUB_FORCE_CLASSIC";
  private static final String STATE_PROGRESS = "reader_epub_progress";
  private static final String STATE_RAW_WORD = "reader_epub_raw_word";
  private static final byte[] BLOCKED_RESPONSE = new byte[0];
  private static final int MIN_TEXT_SIZE = 14;
  private static final int MAX_TEXT_SIZE = 32;
  private static final long PROGRESS_WRITE_INTERVAL_MS = 2000L;
  private static final Pattern WORD_PATTERN = Pattern.compile("\\S+");

  private final ExecutorService _loader = Executors.newSingleThreadExecutor();
  private WebView _webView;
  private ReaderLibrary.Item _item;
  private ReaderEpubImporter.Book _book;
  private ReaderLibrary.EpubSettings _settings;
  private ProgressBar _progress;
  private TextView _percent;
  private int _latestProgress;
  private int _latestRawWordIndex;
  private int _latestChapter;
  private int _latestCharOffset;
  private String _latestAnchor;
  private long _lastProgressWriteAt;
  private boolean _opening3d;
  private boolean _pageReady;
  private boolean _destroyed;

  static Intent intent(Context context, String itemId)
  {
    return new Intent(context, ReaderEpubActivity.class)
      .putExtra(EXTRA_ITEM_ID, itemId);
  }

  static Intent intent(Context context, String itemId, boolean forceClassic)
  {
    return intent(context, itemId)
      .putExtra(EXTRA_FORCE_CLASSIC, forceClassic);
  }

  static boolean isReadableItem(ReaderLibrary.Item item)
  {
    return item != null && item.sourceType == ReaderLibrary.SourceType.EPUB &&
      item.importState == ReaderLibrary.ImportState.READY &&
      item.sourceState == ReaderLibrary.SourceState.AVAILABLE &&
      item.sourceUri != null && "content".equals(Uri.parse(item.sourceUri).getScheme());
  }

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    if (getActionBar() != null)
      getActionBar().hide();
    setContentView(R.layout.reader_epub_activity);

    _webView = findViewById(R.id.reader_epub_webview);
    _progress = findViewById(R.id.reader_epub_progress);
    _percent = findViewById(R.id.reader_epub_percent);
    configureWebView(_webView);
    configureControls();

    if (!loadLibraryState())
    {
      showError(R.string.reader_epub_unavailable);
      return;
    }
    _latestProgress = savedInstanceState == null
      ? Math.round(clamp(_item.progressFraction) * 10000f)
      : Math.max(0, Math.min(10000,
          savedInstanceState.getInt(STATE_PROGRESS, 0)));
    _latestRawWordIndex = savedInstanceState == null
      ? (int)Math.min(Integer.MAX_VALUE, _item.rawWordIndex)
      : Math.max(0, savedInstanceState.getInt(STATE_RAW_WORD,
            (int)Math.min(Integer.MAX_VALUE, _item.rawWordIndex)));
    _latestChapter = _item.progressChapter;
    _latestCharOffset = _item.progressCharOffset;
    _latestAnchor = _item.progressAnchor;
    applyNativeTheme();
    updateControlState();
    loadBook();
  }

  private boolean loadLibraryState()
  {
    String itemId = getIntent().getStringExtra(EXTRA_ITEM_ID);
    if (itemId == null || itemId.isEmpty())
      return false;
    try (ReaderLibrary library = new ReaderLibrary(this))
    {
      _item = library.get(itemId);
      _settings = library.getEpubSettings();
    }
    catch (ReaderLibrary.LibraryException error)
    {
      return false;
    }
    if (!isReadableItem(_item))
      return false;
    ((TextView)findViewById(R.id.reader_epub_title)).setText(_item.title);
    TextView author = findViewById(R.id.reader_epub_author);
    author.setText(_item.author == null ? "" : _item.author);
    author.setVisibility(_item.author == null || _item.author.trim().isEmpty()
        ? View.GONE : View.VISIBLE);
    return true;
  }

  private void loadBook()
  {
    Uri source = Uri.parse(_item.sourceUri);
    _loader.execute(() ->
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
          showError(R.string.reader_epub_open_error);
          return;
        }
        _book = result;
        findViewById(R.id.reader_epub_ai).setEnabled(true);
        _latestRawWordIndex = initialRawWordIndex(_item, result,
            _latestRawWordIndex);
        updatePosition(_latestRawWordIndex);
        if (!getIntent().getBooleanExtra(EXTRA_FORCE_CLASSIC, false) &&
            _item.lastReaderMode == ReaderLibrary.ReaderMode.THREE_D)
        {
          launch3d();
          return;
        }
        persistLatest(ReaderLibrary.ReaderMode.CLASSIC, true);
        String html = buildDocumentHtml(result);
        _webView.loadDataWithBaseURL("https://frankenkey.local/reader/", html,
            "text/html", StandardCharsets.UTF_8.name(), null);
      });
    });
  }

  static void configureWebView(WebView webView)
  {
    WebSettings settings = webView.getSettings();
    settings.setJavaScriptEnabled(true);
    settings.setDomStorageEnabled(false);
    settings.setDatabaseEnabled(false);
    settings.setGeolocationEnabled(false);
    settings.setAllowFileAccess(false);
    settings.setAllowContentAccess(false);
    settings.setBlockNetworkLoads(true);
    settings.setJavaScriptCanOpenWindowsAutomatically(false);
    settings.setSupportMultipleWindows(false);
    settings.setAllowFileAccessFromFileURLs(false);
    settings.setAllowUniversalAccessFromFileURLs(false);
    settings.setSaveFormData(false);
    settings.setSafeBrowsingEnabled(true);
    settings.setMediaPlaybackRequiresUserGesture(true);
    settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
    settings.setLoadsImagesAutomatically(true);
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
        return blockedResponse(request == null ? null : request.getUrl());
      }

      @SuppressWarnings("deprecation")
      @Override public WebResourceResponse shouldInterceptRequest(WebView view,
          String url)
      {
        return blockedResponse(url == null ? null : Uri.parse(url));
      }

      @Override public void onPageFinished(WebView view, String url)
      {
        ReaderEpubActivity activity = (ReaderEpubActivity)view.getContext();
        activity._pageReady = true;
        activity.applyWebPreferences(true);
        activity.findViewById(R.id.reader_epub_loading)
          .setVisibility(View.GONE);
      }
    });
    webView.addJavascriptInterface(new ProgressBridge(webView),
        "ReaderProgress");
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

  static String buildDocumentHtml(ReaderEpubImporter.Book book)
  {
    StringBuilder html = new StringBuilder(4096);
    html.append("<!doctype html><html><head><meta charset=\"utf-8\">")
      .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
      .append("<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; img-src data:; style-src 'unsafe-inline'; script-src 'unsafe-inline'\">")
      .append("<style>:root{--bg:#000;--fg:#f8fafc;--muted:#94a3b8;--size:18px;--font:Georgia,serif}")
      .append("*{box-sizing:border-box}html,body{margin:0;padding:0;background:var(--bg);color:var(--fg)}")
      .append("body{font-family:var(--font);font-size:var(--size);line-height:1.72;padding:22px 18px 45vh;overflow-wrap:anywhere}")
      .append("section{max-width:46rem;margin:0 auto 2.5rem}img{display:block;max-width:100%;height:auto;margin:1rem auto}")
      .append("body.images-off img{display:none}a{color:inherit;text-decoration:underline}hr{border:0;border-top:1px solid var(--muted)}")
      .append("</style></head><body>");
    for (ReaderEpubImporter.Chapter chapter : book.chapters)
    {
      html.append("<section data-chapter=\"").append(chapter.ordinal)
        .append("\" data-raw-start=\"").append(chapter.rawWordStart)
        .append("\" data-raw-count=\"").append(chapter.rawWordCount)
        .append("\">");
      if (chapter.html != null)
        html.append(chapter.html);
      html.append("</section>");
    }
    html.append("<script>(()=>{'use strict';let ticking=false,nodes=[],meta=new WeakMap(),total=0;")
      .append("const words=s=>(String(s||'').match(/\\S+/g)||[]).length;")
      .append("const indexText=()=>{nodes=[];meta=new WeakMap();total=0;document.querySelectorAll('section').forEach(section=>{let raw=Number(section.dataset.rawStart)||0,w=document.createTreeWalker(section,NodeFilter.SHOW_TEXT);for(let n=w.nextNode();n;n=w.nextNode()){const count=words(n.data);if(!count)continue;const item={node:n,start:raw,count};nodes.push(item);meta.set(n,item);raw+=count}total=Math.max(total,raw)});};")
      .append("const percent=()=>{const d=Math.max(1,document.documentElement.scrollHeight-innerHeight);return Math.max(0,Math.min(10000,Math.round(scrollY/d*10000)))};")
      .append("const current=()=>{let node=null,offset=0;if(document.caretRangeFromPoint){const r=document.caretRangeFromPoint(innerWidth/2,Math.max(1,innerHeight/3));if(r){node=r.startContainer;offset=r.startOffset}}else if(document.caretPositionFromPoint){const p=document.caretPositionFromPoint(innerWidth/2,Math.max(1,innerHeight/3));if(p){node=p.offsetNode;offset=p.offset}}const item=node&&meta.get(node);const raw=item?item.start+words(node.data.slice(0,offset)):Math.round(total*percent()/10000);return Math.max(0,Math.min(total,raw))};")
      .append("const send=()=>{ticking=false;const raw=current();ReaderProgress.onProgress(raw,total,percent());return raw};")
      .append("const restoreRaw=(raw,fallback)=>requestAnimationFrame(()=>requestAnimationFrame(()=>{raw=Math.max(0,Math.min(total,Number(raw)||0));let item=null;for(const candidate of nodes){if(raw>=candidate.start&&raw<candidate.start+candidate.count){item=candidate;break}}if(item){let at=0,match,re=/\\S+/g,target=Math.max(0,raw-item.start);while((match=re.exec(item.node.data))){if(at++===target){const range=document.createRange();range.setStart(item.node,match.index);range.collapse(true);scrollBy(0,range.getBoundingClientRect().top-innerHeight/3);break}}}else{const d=Math.max(0,document.documentElement.scrollHeight-innerHeight);scrollTo(0,d*Math.max(0,Math.min(1,Number(fallback)||0)))}send()}));")
      .append("addEventListener('scroll',()=>{if(!ticking){ticking=true;requestAnimationFrame(send)}},{passive:true});")
      .append("indexText();window.ReaderClassic={apply:(theme,size,font,images)=>{const raw=current(),r=document.documentElement.style,b=document.body;")
      .append("const colors=theme==='sepia'?['#f4ecd8','#3b3127','#6b5b4d']:theme==='light'?['#ffffff','#111827','#64748b']:['#000000','#f8fafc','#94a3b8'];")
      .append("r.setProperty('--bg',colors[0]);r.setProperty('--fg',colors[1]);r.setProperty('--muted',colors[2]);r.setProperty('--size',size+'px');r.setProperty('--font',font==='system'?'-apple-system,BlinkMacSystemFont,Roboto,sans-serif':'Georgia,serif');b.classList.toggle('images-off',!images);restoreRaw(raw,0)},")
      .append("restore:(raw,fallback)=>restoreRaw(raw,fallback),report:send,open3d:()=>{const raw=send();ReaderProgress.open3d(raw,total,percent())},close:()=>{const raw=send();ReaderProgress.close(raw,total,percent())}};")
      .append("})();</script></body></html>");
    return html.toString();
  }

  private void configureControls()
  {
    findViewById(R.id.reader_epub_back).setOnClickListener(
        view -> closeClassic());
    findViewById(R.id.reader_epub_smaller).setOnClickListener(view ->
        changeTextSize(-1));
    findViewById(R.id.reader_epub_larger).setOnClickListener(view ->
        changeTextSize(1));
    findViewById(R.id.reader_epub_font).setOnClickListener(view ->
    {
      _settings = copySettings(_settings, _settings.classicTheme,
          _settings.classicTextSize,
          _settings.classicFontFamily == ReaderLibrary.ClassicFontFamily.SERIF
            ? ReaderLibrary.ClassicFontFamily.SYSTEM
            : ReaderLibrary.ClassicFontFamily.SERIF,
          _settings.classicImagesEnabled);
      saveAndApplySettings();
    });
    findViewById(R.id.reader_epub_images).setOnClickListener(view ->
    {
      _settings = copySettings(_settings, _settings.classicTheme,
          _settings.classicTextSize, _settings.classicFontFamily,
          !_settings.classicImagesEnabled);
      saveAndApplySettings();
    });
    findViewById(R.id.reader_epub_theme).setOnClickListener(view ->
    {
      ReaderLibrary.ClassicTheme next;
      switch (_settings.classicTheme)
      {
        case DARK: next = ReaderLibrary.ClassicTheme.SEPIA; break;
        case SEPIA: next = ReaderLibrary.ClassicTheme.LIGHT; break;
        default: next = ReaderLibrary.ClassicTheme.DARK; break;
      }
      _settings = copySettings(_settings, next, _settings.classicTextSize,
          _settings.classicFontFamily, _settings.classicImagesEnabled);
      saveAndApplySettings();
    });
    View ai = findViewById(R.id.reader_epub_ai);
    ai.setEnabled(false);
    ai.setOnClickListener(view -> openBookAi());
    findViewById(R.id.reader_epub_open_3d).setOnClickListener(view -> open3d());
  }

  private void changeTextSize(int delta)
  {
    float size = Math.max(MIN_TEXT_SIZE,
        Math.min(MAX_TEXT_SIZE, _settings.classicTextSize + delta));
    if (size == _settings.classicTextSize)
      return;
    _settings = copySettings(_settings, _settings.classicTheme, size,
        _settings.classicFontFamily, _settings.classicImagesEnabled);
    saveAndApplySettings();
  }

  private static ReaderLibrary.EpubSettings copySettings(
      ReaderLibrary.EpubSettings source, ReaderLibrary.ClassicTheme theme,
      float size, ReaderLibrary.ClassicFontFamily font, boolean images)
  {
    return new ReaderLibrary.EpubSettings(source.booksTreeUri,
        source.globalLastReaderMode, theme, size, font, images);
  }

  private void saveAndApplySettings()
  {
    try (ReaderLibrary library = new ReaderLibrary(this))
    {
      library.updateEpubSettings(_settings);
    }
    catch (ReaderLibrary.LibraryException error)
    {
      showError(R.string.reader_library_error);
      return;
    }
    applyNativeTheme();
    updateControlState();
    applyWebPreferences(false);
  }

  private void applyWebPreferences(boolean restoreProgress)
  {
    if (!_pageReady || _settings == null)
      return;
    String theme = _settings.classicTheme.name().toLowerCase(Locale.US);
    String font = _settings.classicFontFamily.name().toLowerCase(Locale.US);
    String script = String.format(Locale.US,
        "window.ReaderClassic&&window.ReaderClassic.apply('%s',%.1f,'%s',%s);%s",
        theme, _settings.classicTextSize, font,
        _settings.classicImagesEnabled ? "true" : "false",
        restoreProgress
          ? String.format(Locale.US,
              "window.ReaderClassic.restore(%d,%.6f);",
              _latestRawWordIndex, _latestProgress / 10000f)
          : "");
    _webView.evaluateJavascript(script, null);
  }

  private void applyNativeTheme()
  {
    ThemeColors colors = ThemeColors.forTheme(_settings.classicTheme);
    getWindow().setStatusBarColor(colors.surface);
    getWindow().setNavigationBarColor(colors.surface);
    View decor = getWindow().getDecorView();
    int lightBars = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR |
      View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
    int visibility = decor.getSystemUiVisibility();
    decor.setSystemUiVisibility(colors.lightSystemBars
        ? visibility | lightBars : visibility & ~lightBars);
    findViewById(R.id.reader_epub_root).setBackgroundColor(colors.background);
    findViewById(R.id.reader_epub_header).setBackgroundColor(colors.surface);
    findViewById(R.id.reader_epub_toolbar).setBackgroundColor(colors.surface);
    _webView.setBackgroundColor(colors.background);
    ((TextView)findViewById(R.id.reader_epub_title)).setTextColor(colors.text);
    ((TextView)findViewById(R.id.reader_epub_author)).setTextColor(colors.secondary);
    _percent.setTextColor(colors.accent);
    _progress.setProgressTintList(ColorStateList.valueOf(colors.accent));
    _progress.setProgressBackgroundTintList(
        ColorStateList.valueOf(colors.progressTrack));
    int[] controlIds = { R.id.reader_epub_smaller, R.id.reader_epub_larger,
      R.id.reader_epub_font, R.id.reader_epub_images, R.id.reader_epub_theme,
      R.id.reader_epub_ai, R.id.reader_epub_open_3d };
    for (int id : controlIds)
      setControlColor(id, colors.text);
    setControlColor(R.id.reader_epub_font,
        _settings.classicFontFamily == ReaderLibrary.ClassicFontFamily.SERIF
        ? colors.accent : colors.text);
    setControlColor(R.id.reader_epub_images,
        _settings.classicImagesEnabled ? colors.accent : colors.secondary);
    setControlColor(R.id.reader_epub_theme, colors.accent);
    ((ImageButton)findViewById(R.id.reader_epub_back)).setImageTintList(
        ColorStateList.valueOf(colors.text));
  }

  private void setControlColor(int id, int color)
  {
    View control = findViewById(id);
    if (control instanceof ImageButton)
      ((ImageButton)control).setImageTintList(ColorStateList.valueOf(color));
    else
      ((TextView)control).setTextColor(color);
  }

  private void updateControlState()
  {
    View font = findViewById(R.id.reader_epub_font);
    String fontLabel = _settings.classicFontFamily ==
      ReaderLibrary.ClassicFontFamily.SERIF ? "Serif" : "System";
    font.setContentDescription(getString(
          R.string.reader_epub_font_accessibility, fontLabel));

    ImageButton images = findViewById(R.id.reader_epub_images);
    images.setContentDescription(getString(
          R.string.reader_epub_images_accessibility,
          _settings.classicImagesEnabled ? "off" : "on"));

    ImageButton theme = findViewById(R.id.reader_epub_theme);
    String themeLabel = titleCase(_settings.classicTheme.name());
    theme.setContentDescription(getString(
          R.string.reader_epub_theme_accessibility, themeLabel));
  }

  private void openBookAi()
  {
    if (_book == null || _item == null)
      return;
    ReaderAiDialog.show(this, ReaderAiService.Article.book(_item, _book));
  }

  private void open3d()
  {
    if (_book == null)
      return;
    if (_pageReady)
      _webView.evaluateJavascript(
          "window.ReaderClassic&&window.ReaderClassic.open3d()", null);
    else
      launch3d();
  }

  private void launch3d()
  {
    if (_book == null || _opening3d)
      return;
    persistLatest(ReaderLibrary.ReaderMode.THREE_D, true);
    StringBuilder text = new StringBuilder();
    JSONArray ranges = new JSONArray();
    for (ReaderEpubImporter.Chapter chapter : _book.chapters)
    {
      if (text.length() > 0)
        text.append("\n\n");
      text.append(chapter.text);
      JSONObject range = new JSONObject();
      try
      {
        range.put("start", chapter.rawWordStart);
        range.put("end", chapter.rawWordStart + chapter.rawWordCount);
        ranges.put(range);
      }
      catch (JSONException impossible) {}
    }
    _opening3d = true;
    if (!Reader3dActivity.startEpub(this, _item, _item.title, text.toString(),
          ranges.toString(), _latestRawWordIndex))
    {
      showError(R.string.reader_open_3d_error);
      _opening3d = false;
      return;
    }
    finish();
  }

  private void closeClassic()
  {
    if (_pageReady)
      _webView.evaluateJavascript(
          "window.ReaderClassic&&window.ReaderClassic.close()", null);
    else
    {
      persistLatest(ReaderLibrary.ReaderMode.CLASSIC, false);
      finish();
    }
  }

  @Override public void onBackPressed()
  {
    closeClassic();
  }

  private void acceptProgress(int rawWordIndex, int totalRawWords,
      int progress, boolean forceWrite)
  {
    if (_book == null || totalRawWords != totalRawWords(_book))
      return;
    _latestRawWordIndex = Math.max(0,
        Math.min(rawWordIndex, totalRawWords));
    updatePosition(_latestRawWordIndex);
    updateProgressUi(progress);
    long now = System.currentTimeMillis();
    if (forceWrite || now - _lastProgressWriteAt >=
        PROGRESS_WRITE_INTERVAL_MS)
      persistLatest(ReaderLibrary.ReaderMode.CLASSIC, false);
  }

  private void updatePosition(int rawWordIndex)
  {
    if (_book == null)
      return;
    Position position = positionForRawWord(_book, rawWordIndex);
    _latestChapter = position.chapter;
    _latestCharOffset = position.charOffset;
    _latestAnchor = position.anchor;
  }

  private void persistLatest(ReaderLibrary.ReaderMode mode,
      boolean updateGlobalMode)
  {
    if (_item == null || _book == null)
      return;
    int total = totalRawWords(_book);
    int raw = Math.max(0, Math.min(_latestRawWordIndex, total));
    float fraction = total == 0 ? 0f : raw / (float)total;
    try (ReaderLibrary library = new ReaderLibrary(this))
    {
      library.updateBookProgress(_item.id, raw, _latestChapter,
          _latestCharOffset, _latestAnchor, fraction, raw >= total, mode,
          System.currentTimeMillis());
      if (updateGlobalMode)
        library.updateGlobalLastReaderMode(mode);
      _lastProgressWriteAt = System.currentTimeMillis();
    }
    catch (ReaderLibrary.LibraryException ignored) {}
  }

  static int initialRawWordIndex(ReaderLibrary.Item item,
      ReaderEpubImporter.Book book, int savedRawWordIndex)
  {
    int total = totalRawWords(book);
    if (savedRawWordIndex > 0 || item == null ||
        (item.progressLocator != null &&
         item.progressLocator.startsWith("book:")) ||
        item.progressAnchor != null || item.progressFraction <= 0f)
      return Math.max(0, Math.min(savedRawWordIndex, total));
    if (item.progressLocator != null &&
        item.progressLocator.startsWith("unit:"))
    {
      String[] parts = item.progressLocator.split(":", 3);
      try
      {
        int chapter = Math.max(0, Math.min(book.chapters.size() - 1,
              Integer.parseInt(parts[1])));
        ReaderEpubImporter.Chapter entry = book.chapters.get(chapter);
        int charOffset = parts.length > 2
          ? Math.max(0, Math.min(entry.text.length(),
                Integer.parseInt(parts[2]))) : 0;
        return Math.min(total, entry.rawWordStart +
            countWords(entry.text.substring(0, charOffset)));
      }
      catch (NumberFormatException ignored) {}
    }
    return Math.max(0, Math.min(total,
          Math.round(total * clamp(item.progressFraction))));
  }

  static Position positionForRawWord(ReaderEpubImporter.Book book,
      int rawWordIndex)
  {
    int total = totalRawWords(book);
    int bounded = Math.max(0, Math.min(rawWordIndex, total));
    ReaderEpubImporter.Chapter selected = book.chapters.get(0);
    for (ReaderEpubImporter.Chapter chapter : book.chapters)
    {
      selected = chapter;
      if (bounded < chapter.rawWordStart + chapter.rawWordCount)
        break;
    }
    int localWord = Math.max(0, Math.min(selected.rawWordCount,
          bounded - selected.rawWordStart));
    Matcher matcher = WORD_PATTERN.matcher(selected.text);
    int seen = 0;
    int charOffset = selected.text.length();
    while (matcher.find())
      if (seen++ == localWord)
      {
        charOffset = matcher.start();
        break;
      }
    int anchorStart = Math.max(0, charOffset - 40);
    int anchorEnd = Math.min(selected.text.length(), charOffset + 80);
    String anchor = selected.text.substring(anchorStart, anchorEnd)
      .replaceAll("\\s+", " ").trim();
    return new Position(selected.ordinal, charOffset,
        anchor.isEmpty() ? null : anchor);
  }

  private static int totalRawWords(ReaderEpubImporter.Book book)
  {
    if (book == null || book.chapters.isEmpty())
      return 0;
    ReaderEpubImporter.Chapter last =
      book.chapters.get(book.chapters.size() - 1);
    return last.rawWordStart + last.rawWordCount;
  }

  private static int countWords(String text)
  {
    int count = 0;
    Matcher matcher = WORD_PATTERN.matcher(text == null ? "" : text);
    while (matcher.find())
      count++;
    return count;
  }

  static final class Position
  {
    final int chapter;
    final int charOffset;
    final String anchor;

    Position(int chapter, int charOffset, String anchor)
    {
      this.chapter = chapter;
      this.charOffset = charOffset;
      this.anchor = anchor;
    }
  }

  private void showError(int message)
  {
    findViewById(R.id.reader_epub_toolbar).setVisibility(View.GONE);
    findViewById(R.id.reader_epub_loading).setVisibility(View.GONE);
    _webView.setVisibility(View.GONE);
    TextView error = findViewById(R.id.reader_epub_error);
    error.setText(message);
    error.setVisibility(View.VISIBLE);
  }

  private void updateProgressUi(int progress)
  {
    _latestProgress = Math.max(0, Math.min(10000, progress));
    _progress.setProgress(_latestProgress);
    _percent.setText(getString(R.string.reader_progress_value,
          Math.round(_latestProgress / 100f)));
  }

  @Override
  protected void onPause()
  {
    if (!_opening3d)
    {
      if (_pageReady && _webView != null)
        _webView.evaluateJavascript(
            "window.ReaderClassic&&window.ReaderClassic.report()", null);
      persistLatest(ReaderLibrary.ReaderMode.CLASSIC, false);
    }
    super.onPause();
  }

  @Override
  protected void onSaveInstanceState(Bundle state)
  {
    state.putInt(STATE_PROGRESS, _latestProgress);
    state.putInt(STATE_RAW_WORD, _latestRawWordIndex);
    super.onSaveInstanceState(state);
  }

  @Override
  protected void onDestroy()
  {
    _destroyed = true;
    _loader.shutdownNow();
    if (_webView != null)
    {
      _webView.removeJavascriptInterface("ReaderProgress");
      _webView.stopLoading();
      _webView.loadUrl("about:blank");
      _webView.destroy();
      _webView = null;
    }
    super.onDestroy();
  }

  private static float clamp(float fraction)
  {
    if (!Float.isFinite(fraction))
      return 0f;
    return Math.max(0f, Math.min(1f, fraction));
  }

  private static String titleCase(String value)
  {
    String lower = value.toLowerCase(Locale.US);
    return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
  }

  private static final class ProgressBridge
  {
    private final WebView _host;

    ProgressBridge(WebView host)
    {
      _host = host;
    }

    @JavascriptInterface
    public void onProgress(int rawWordIndex, int totalRawWords, int progress)
    {
      postProgress(rawWordIndex, totalRawWords, progress, false, 0);
    }

    @JavascriptInterface
    public void open3d(int rawWordIndex, int totalRawWords, int progress)
    {
      postProgress(rawWordIndex, totalRawWords, progress, true, 1);
    }

    @JavascriptInterface
    public void close(int rawWordIndex, int totalRawWords, int progress)
    {
      postProgress(rawWordIndex, totalRawWords, progress, true, 2);
    }

    private void postProgress(int rawWordIndex, int totalRawWords,
        int progress, boolean forceWrite, int action)
    {
      _host.post(() ->
      {
        Context context = _host.getContext();
        if (!(context instanceof ReaderEpubActivity))
          return;
        ReaderEpubActivity activity = (ReaderEpubActivity)context;
        activity.acceptProgress(rawWordIndex, totalRawWords, progress,
            forceWrite);
        if (action == 1)
          activity.launch3d();
        else if (action == 2)
          activity.finish();
      });
    }
  }


  private static final class ThemeColors
  {
    final int background;
    final int surface;
    final int text;
    final int secondary;
    final int accent;
    final int progressTrack;
    final boolean lightSystemBars;

    ThemeColors(String background, String surface, String text,
        String secondary, String accent, String progressTrack,
        boolean lightSystemBars)
    {
      this.background = Color.parseColor(background);
      this.surface = Color.parseColor(surface);
      this.text = Color.parseColor(text);
      this.secondary = Color.parseColor(secondary);
      this.accent = Color.parseColor(accent);
      this.progressTrack = Color.parseColor(progressTrack);
      this.lightSystemBars = lightSystemBars;
    }

    static ThemeColors forTheme(ReaderLibrary.ClassicTheme theme)
    {
      switch (theme)
      {
        case SEPIA:
          return new ThemeColors("#f4ecd8", "#e7ddc4", "#3b3127",
              "#6b5b4d", "#087f6b", "#c9bea6", true);
        case LIGHT:
          return new ThemeColors("#ffffff", "#f8fafc", "#111827",
              "#64748b", "#087f6b", "#d7dee4", true);
        default:
          return new ThemeColors("#000000", "#0a0a0a", "#f8fafc",
              "#94a3b8", "#55d6be", "#263238", false);
      }
    }
  }
}
