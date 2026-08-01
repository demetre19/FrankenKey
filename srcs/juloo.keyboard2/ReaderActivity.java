package juloo.keyboard2;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.IBinder;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.ImageSpan;
import android.text.method.KeyListener;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.BreakIterator;
import java.io.File;
import java.net.URI;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Native control surface for the single app-wide Reader playback session. */
public final class ReaderActivity extends Activity
    implements ReaderPlaybackService.Listener
{
  private static final int REQUEST_NOTIFICATIONS = 81;
  private static final int BASE_WORDS_PER_MINUTE = 180;
  private static final int MIN_WORDS_PER_MINUTE = 45;
  private static final int MAX_WORDS_PER_MINUTE = 800;
  private static final String UI_STORE_NAME = "reader_ui";
  private static final String STORE_FOLLOW_WORDS = "follow_words";
  private static final String STORE_DARK_MODE = "dark_mode";
  static int themeResource(Context context)
  {
    boolean darkMode = context.getSharedPreferences(
        UI_STORE_NAME, Context.MODE_PRIVATE).getBoolean(STORE_DARK_MODE, true);
    return darkMode ? R.style.readerThemeDark : R.style.readerThemeLight;
  }

  // The visible icon names the current mode, as requested; the content
  // description names the mode that activating the toggle will enter.
  static int themeIconResource(boolean darkMode)
  {
    return darkMode ? R.drawable.ic_reader_dark_mode
      : R.drawable.ic_reader_light_mode;
  }
  private static final String EXTRA_QUICK_READ_TOKEN =
    "juloo.keyboard2.extra.QUICK_READ_TOKEN";
  private static final String EXTRA_REQUEST_PLAY =
    "juloo.keyboard2.extra.REQUEST_PLAY";
  private static final String EXTRA_LIBRARY_ITEM_ID =
    "juloo.keyboard2.extra.LIBRARY_ITEM_ID";
  private static final Object QUICK_READ_LOCK = new Object();
  private static PendingQuickRead _pendingQuickRead;

  private static final class PendingQuickRead
  {
    final String token;
    final String itemId;
    final String title;
    final String text;

    PendingQuickRead(String token, String itemId, String title, String text)
    {
      this.token = token;
      this.itemId = itemId;
      this.title = title;
      this.text = text;
    }
  }

  private static final class ArticleImageSpan extends ImageSpan
  {
    final String assetUri;
    private final int _sourceWidth;
    private final int _sourceHeight;

    ArticleImageSpan(BitmapDrawable drawable, String assetUri,
        int sourceWidth, int sourceHeight, int targetWidth)
    {
      super(drawable, ImageSpan.ALIGN_BOTTOM);
      this.assetUri = assetUri;
      _sourceWidth = sourceWidth;
      _sourceHeight = sourceHeight;
      fitToWidth(targetWidth);
    }

    boolean fitToWidth(int targetWidth)
    {
      if (targetWidth <= 0)
        return false;
      int width = Math.max(1, Math.min(targetWidth, _sourceWidth));
      int height = Math.max(1, Math.round(
            _sourceHeight * (width / (float)_sourceWidth)));
      if (getDrawable().getBounds().width() == width &&
          getDrawable().getBounds().height() == height)
        return false;
      getDrawable().setBounds(0, 0, width, height);
      return true;
    }
  }
  private static final class VoicePresentation
  {
    final String label;
    final String accessibility;
    final int flagResource;
    final int statusResource;
    final String groupHeader;

    VoicePresentation(String label, String accessibility, int flagResource,
        int statusResource, String groupHeader)
    {
      this.label = label;
      this.accessibility = accessibility;
      this.flagResource = flagResource;
      this.statusResource = statusResource;
      this.groupHeader = groupHeader;
    }

    @Override public String toString() { return label; }
  }

  static final class VoiceIdentity
  {
    final String name;
    final String gender;

    VoiceIdentity(String name, String gender)
    {
      this.name = name;
      this.gender = gender;
    }
  }


  static void startQuickRead(Context context, String itemId, String title,
      String text)
  {
    String token = UUID.randomUUID().toString();
    synchronized (QUICK_READ_LOCK)
    {
      _pendingQuickRead = new PendingQuickRead(token, itemId, title, text);
    }
    Intent intent = new Intent(context, ReaderActivity.class)
      .putExtra(EXTRA_QUICK_READ_TOKEN, token)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    try
    {
      context.startActivity(intent);
    }
    catch (RuntimeException error)
    {
      discardQuickRead(token);
      throw error;
    }
  }

  static void startPlaybackRequest(Context context)
  {
    context.startActivity(new Intent(context, ReaderActivity.class)
        .putExtra(EXTRA_REQUEST_PLAY, true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  }

  static void startLibraryItem(Context context, String itemId)
  {
    context.startActivity(new Intent(context, ReaderActivity.class)
        .putExtra(EXTRA_LIBRARY_ITEM_ID, itemId));
  }

  private static PendingQuickRead pendingQuickRead(String token)
  {
    synchronized (QUICK_READ_LOCK)
    {
      return _pendingQuickRead != null &&
        _pendingQuickRead.token.equals(token) ? _pendingQuickRead : null;
    }
  }

  private static PendingQuickRead consumeQuickRead(String token)
  {
    synchronized (QUICK_READ_LOCK)
    {
      PendingQuickRead pending = _pendingQuickRead != null &&
        _pendingQuickRead.token.equals(token) ? _pendingQuickRead : null;
      if (pending != null)
        _pendingQuickRead = null;
      return pending;
    }
  }

  private static void discardQuickRead(String token)
  {
    consumeQuickRead(token);
  }

  private final List<ReaderPlaybackService.VoiceOption> _voiceOptions =
    new ArrayList<ReaderPlaybackService.VoiceOption>();
  private ReaderPlaybackService _service;
  private boolean _bound;
  private boolean _pendingPlay;
  private boolean _requestPlayOnConnect;
  private boolean _updatingControls;
  private String _displayedItemId;
  private String _displayedVoiceName;
  private boolean _displayedAllowNetworkVoices;
  private String _quickReadToken;
  private String _libraryItemId;
  private String _unsavedItemId;
  private String _unsavedTitle;
  private ReaderLibrary.Item _libraryItem;
  private int _libraryUnitIndex;
  private int _librarySegmentStart;
  private int _librarySeekOffset = -1;
  private boolean _loadingLibraryItem;
  private boolean _followWords;
  private boolean _darkMode;
  private String _speechText = "";
  private int _tapOffset = -1;
  private float _tapDownX;
  private float _tapDownY;
  private int _tapSlop;

  private EditText _text;
  private KeyListener _editableKeyListener;
  private TextView _status;
  private TextView _documentTitle;
  private ScrollView _articleScroll;
  private View _jumpToBottom;
  private TextView _itemMetadata;
  private TextView _unitLabel;
  private TextView _speedLabel;
  private TextView _pitchLabel;
  private SeekBar _speed;
  private SeekBar _pitch;
  private ImageButton _playPause;
  private Spinner _voice;
  private Spinner _followMode;
  private Switch _networkVoices;
  private final ServiceConnection _connection = new ServiceConnection()
  {
    @Override public void onServiceConnected(ComponentName name, IBinder binder)
    {
      _service = ((ReaderPlaybackService.LocalBinder)binder).service();
      _bound = true;
      _service.addListener(ReaderActivity.this);
      showPendingQuickRead();
      if (_libraryItemId != null)
        loadLibraryItem();
      if (_requestPlayOnConnect && _quickReadToken == null)
      {
        _requestPlayOnConnect = false;
        requestPlayOrPause();
      }
    }

    @Override public void onServiceDisconnected(ComponentName name)
    {
      _bound = false;
      _service = null;
      setControlsEnabled(false);
    }
  };

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    _darkMode = getSharedPreferences(
        UI_STORE_NAME, Context.MODE_PRIVATE).getBoolean(
        STORE_DARK_MODE, true);
    setTheme(themeResource(this));
    super.onCreate(savedInstanceState);
    if (getActionBar() != null)
      getActionBar().hide();
    setContentView(R.layout.reader_activity);
    _followWords = getSharedPreferences(
        UI_STORE_NAME, Context.MODE_PRIVATE).getBoolean(
        STORE_FOLLOW_WORDS, false);
    bindViews();
    wireControls();
    setControlsEnabled(false);
    _quickReadToken = getIntent().getStringExtra(EXTRA_QUICK_READ_TOKEN);
    _libraryItemId = getIntent().getStringExtra(EXTRA_LIBRARY_ITEM_ID);
    _requestPlayOnConnect =
      getIntent().getBooleanExtra(EXTRA_REQUEST_PLAY, false);
    bindService(new Intent(this, ReaderPlaybackService.class), _connection,
        Context.BIND_AUTO_CREATE);
  }

  private void bindViews()
  {
    _text = (EditText)findViewById(R.id.reader_text);
    _editableKeyListener = _text.getKeyListener();
    _text.setSaveEnabled(false);
    _status = (TextView)findViewById(R.id.reader_status);
    _documentTitle = (TextView)findViewById(R.id.reader_document_title);
    updateTitleMarquee(_documentTitle);
    _articleScroll = (ScrollView)findViewById(R.id.reader_article_scroll);
    _jumpToBottom = findViewById(R.id.reader_jump_bottom);
    _itemMetadata = (TextView)findViewById(R.id.reader_item_metadata);
    _unitLabel = (TextView)findViewById(R.id.reader_unit_label);
    _speedLabel = (TextView)findViewById(R.id.reader_speed_label);
    _pitchLabel = (TextView)findViewById(R.id.reader_pitch_label);
    _speed = (SeekBar)findViewById(R.id.reader_speed);
    _pitch = (SeekBar)findViewById(R.id.reader_pitch);
    _playPause = (ImageButton)findViewById(R.id.reader_play_pause);
    _voice = (Spinner)findViewById(R.id.reader_voice);
    _followMode = (Spinner)findViewById(R.id.reader_follow_mode);
    _networkVoices = (Switch)findViewById(R.id.reader_network_voices);
    _tapSlop = ViewConfiguration.get(this).getScaledTouchSlop();
  }

  private void wireControls()
  {
    findViewById(R.id.reader_back).setOnClickListener(_view -> finish());
    ImageButton theme = (ImageButton)findViewById(R.id.reader_theme);
    theme.setImageResource(themeIconResource(_darkMode));
    theme.setContentDescription(getString(_darkMode
          ? R.string.reader_use_light_mode : R.string.reader_use_dark_mode));
    theme.setOnClickListener(_view -> {
      _darkMode = !_darkMode;
      getSharedPreferences(UI_STORE_NAME, Context.MODE_PRIVATE).edit()
        .putBoolean(STORE_DARK_MODE, _darkMode)
        .apply();
      recreate();
    });
    findViewById(R.id.reader_library).setOnClickListener(_view ->
        startActivity(new Intent(this, ReaderLibraryActivity.class)));
    findViewById(R.id.reader_clipboard).setOnClickListener(
        _view -> readClipboardNow());
    findViewById(R.id.reader_unit_previous).setOnClickListener(
        _view -> moveLibrarySection(-1));
    findViewById(R.id.reader_unit_next).setOnClickListener(
        _view -> moveLibrarySection(1));
    _playPause.setOnClickListener(_view -> requestPlayOrPause());
    findViewById(R.id.reader_previous).setOnClickListener(
        _view -> withService(ReaderPlaybackService.ACTION_PREVIOUS));
    findViewById(R.id.reader_next).setOnClickListener(
        _view -> withService(ReaderPlaybackService.ACTION_NEXT));
    findViewById(R.id.reader_stop).setOnClickListener(
        _view -> withService(ReaderPlaybackService.ACTION_STOP));
    findViewById(R.id.reader_preview_voice).setOnClickListener(
        _view -> previewSelectedVoice());
    findViewById(R.id.reader_open_original).setOnClickListener(
        _view -> openOriginal());
    _jumpToBottom.setOnClickListener(_view ->
    {
      _articleScroll.fullScroll(View.FOCUS_DOWN);
      _articleScroll.post(this::updateJumpToBottom);
    });
    _articleScroll.getViewTreeObserver().addOnScrollChangedListener(
        this::updateJumpToBottom);
    _articleScroll.addOnLayoutChangeListener(
        (_view, left, top, right, bottom, oldLeft, oldTop, oldRight,
          oldBottom) -> updateJumpToBottom());
    _text.addOnLayoutChangeListener(
        (_view, left, top, right, bottom, oldLeft, oldTop, oldRight,
          oldBottom) ->
        {
          if (right - left != oldRight - oldLeft)
            resizeArticleImages();
          updateJumpToBottom();
        });
    _text.setOnTouchListener((_view, event) ->
    {
      if (event.getActionMasked() == MotionEvent.ACTION_DOWN)
      {
        _tapDownX = event.getX();
        _tapDownY = event.getY();
      }
      else if (event.getActionMasked() == MotionEvent.ACTION_UP &&
          Math.abs(event.getX() - _tapDownX) <= _tapSlop &&
          Math.abs(event.getY() - _tapDownY) <= _tapSlop)
      {
        int offset = _text.getOffsetForPosition(
            event.getX(), event.getY());
        ArticleImageSpan image = articleImageAt(offset);
        if (image != null)
        {
          showArticleImage(image.assetUri);
          return true;
        }
        setPlaybackPoint(offset);
      }
      return false;
    });


    _speed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
    {
      @Override public void onProgressChanged(SeekBar bar, int progress,
          boolean fromUser)
      {
        int wordsPerMinute = MIN_WORDS_PER_MINUTE + progress;
        float rate = wordsPerMinute / (float)BASE_WORDS_PER_MINUTE;
        updateSpeedLabel(rate);
        if (fromUser && _service != null)
          _service.setSpeechRate(rate);
      }
      @Override public void onStartTrackingTouch(SeekBar bar) {}
      @Override public void onStopTrackingTouch(SeekBar bar) {}
    });

    _pitch.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
    {
      @Override public void onProgressChanged(SeekBar bar, int progress,
          boolean fromUser)
      {
        float pitch = 0.5f + progress / 100f;
        updatePitchLabel(pitch);
        if (fromUser && _service != null)
          _service.setPitch(pitch);
      }
      @Override public void onStartTrackingTouch(SeekBar bar) {}
      @Override public void onStopTrackingTouch(SeekBar bar) {}
    });

    ArrayAdapter<String> followAdapter = new ArrayAdapter<String>(this,
        android.R.layout.simple_spinner_item,
        new String[] {
          getString(R.string.reader_follow_sentence),
          getString(R.string.reader_follow_word)
        });
    followAdapter.setDropDownViewResource(
        android.R.layout.simple_spinner_dropdown_item);
    _followMode.setAdapter(followAdapter);
    _followMode.setSelection(_followWords ? 1 : 0);
    _followMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
    {
      @Override public void onItemSelected(AdapterView<?> parent, View view,
          int position, long id)
      {
        boolean followWords = position == 1;
        if (_followWords == followWords)
          return;
        _followWords = followWords;
        getSharedPreferences(UI_STORE_NAME, Context.MODE_PRIVATE).edit()
          .putBoolean(STORE_FOLLOW_WORDS, _followWords)
          .apply();
        if (_service != null)
          applyFollowAlong(_service.snapshot());
      }
      @Override public void onNothingSelected(AdapterView<?> parent) {}
    });

    _networkVoices.setOnCheckedChangeListener((_button, checked) ->
    {
      if (_updatingControls || _service == null)
        return;
      _service.setAllowNetworkVoices(checked);
      refreshVoices(_service.snapshot().voiceName);
    });

    _voice.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
    {
      @Override public void onItemSelected(AdapterView<?> parent, View view,
          int position, long id)
      {
        if (_updatingControls || _service == null || position < 0 ||
            position >= _voiceOptions.size())
          return;
        String selected = _voiceOptions.get(position).name;
        if (!selected.equals(_service.snapshot().voiceName))
          _service.setVoice(selected);
      }
      @Override public void onNothingSelected(AdapterView<?> parent) {}
    });
  }

  private void setDocumentTitle(CharSequence title)
  {
    _documentTitle.setSelected(false);
    _documentTitle.setText(title);
    updateTitleMarquee(_documentTitle);
  }

  private static void updateTitleMarquee(TextView title)
  {
    title.post(() ->
    {
      int available = title.getWidth() - title.getPaddingLeft() -
        title.getPaddingRight();
      boolean overflow = available > 0 &&
        title.getPaint().measureText(title.getText().toString()) > available;
      title.setGravity((overflow ? Gravity.START : Gravity.CENTER) |
          Gravity.CENTER_VERTICAL);
      title.setSelected(overflow);
    });
  }

  private void updateJumpToBottom()
  {
    boolean articleExceedsViewport = _text.getLayout() != null &&
      _text.getLayout().getHeight() > _articleScroll.getHeight();
    _jumpToBottom.setVisibility(articleExceedsViewport &&
        _articleScroll.canScrollVertically(1) ? View.VISIBLE : View.GONE);
  }

  private void readClipboardNow()
  {
    ReaderTextAccess.Result result = ReaderTextAccess.readClipboard(this);
    if (!result.isSuccess())
    {
      _status.setText(readerErrorMessage(result.failure));
      return;
    }
    if (_service != null && _libraryItem != null)
      saveLibraryProgress(_service.snapshot());
    if (_quickReadToken != null)
    {
      discardQuickRead(_quickReadToken);
      clearQuickReadToken();
    }
    _libraryItem = null;
    _libraryItemId = null;
    _speechText = result.text;
    _tapOffset = -1;
    setDocumentTitle(getString(R.string.reader_title_clipboard));
    _unsavedItemId = "clipboard:" + System.currentTimeMillis();
    _unsavedTitle = getString(R.string.reader_title_clipboard);
    _text.setKeyListener(_editableKeyListener);
    _text.setFocusableInTouchMode(true);
    _text.setFocusable(true);
    _text.setText(result.text);
    findViewById(R.id.reader_source_metadata).setVisibility(View.GONE);
    findViewById(R.id.reader_unit_navigation).setVisibility(View.GONE);
    findViewById(R.id.reader_open_original).setVisibility(View.GONE);
    if (_service == null)
    {
      _requestPlayOnConnect = true;
      return;
    }
    if (ReaderPlaybackService.needsNotificationPermission(this))
    {
      _pendingPlay = true;
      requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
          REQUEST_NOTIFICATIONS);
    }
    else
      performPlay();
  }

  private int readerErrorMessage(ReaderTextAccess.Failure failure)
  {
    switch (failure)
    {
      case USER_LOCKED: return R.string.reader_error_user_locked;
      case SENSITIVE: return R.string.reader_error_sensitive_clipboard;
      case TOO_LARGE: return R.string.reader_error_text_too_long;
      case EMPTY: return R.string.reader_error_empty_text;
      default: return R.string.reader_error_unavailable_text;
    }
  }

  private void updateSpeedLabel(float rate)
  {
    int wordsPerMinute = Math.max(MIN_WORDS_PER_MINUTE,
        Math.min(MAX_WORDS_PER_MINUTE,
          Math.round(BASE_WORDS_PER_MINUTE * rate)));
    _speedLabel.setText(getString(R.string.reader_speed_value, wordsPerMinute));
    _speed.setContentDescription(getString(
        R.string.reader_speed_accessibility, wordsPerMinute, rate));
  }

  private void updatePitchLabel(float pitch)
  {
    String label = getString(R.string.reader_pitch_value, pitch);
    _pitchLabel.setText(label);
    _pitch.setContentDescription(label);
  }

  private void showPendingQuickRead()
  {
    if (_quickReadToken == null)
      return;
    PendingQuickRead pending = pendingQuickRead(_quickReadToken);
    if (pending == null)
    {
      clearQuickReadToken();
      _status.setText(R.string.reader_error_unavailable_text);
      return;
    }
    _speechText = pending.text;
    _text.setText(pending.text);
    requestPlayOrPause();
  }

  private void loadLibraryItem()
  {
    try (ReaderLibrary library = new ReaderLibrary(this))
    {
      ReaderLibrary.Item item = library.get(_libraryItemId);
      if (item == null || item.importState != ReaderLibrary.ImportState.READY ||
          item.units.isEmpty())
      {
        _status.setText(R.string.reader_library_item_unavailable);
        return;
      }
      _libraryItem = item;
      setDocumentTitle(item.title);
      _text.setKeyListener(null);
      _text.setFocusable(false);
      findViewById(R.id.reader_source_metadata).setVisibility(View.VISIBLE);
      String detail = item.author == null || item.author.trim().isEmpty()
        ? DateFormat.getDateInstance(DateFormat.MEDIUM)
            .format(new Date(item.createdAt))
        : item.author;
      _itemMetadata.setText(getString(R.string.reader_item_metadata,
            item.sourceType.name().replace('_', ' ')
              .toLowerCase(Locale.getDefault()), detail));
      View original = findViewById(R.id.reader_open_original);
      original.setVisibility(isSafeOriginalUri(item.sourceUri)
          ? View.VISIBLE : View.GONE);
      if (item.sourceType == ReaderLibrary.SourceType.URL)
      {
        findViewById(R.id.reader_unit_navigation).setVisibility(View.GONE);
        loadArticleDocument(library, false);
        return;
      }
      int[] locator = parseLibraryLocator(item);
      _libraryUnitIndex = locator[0];
      int unitOffset = locator[1];
      _librarySegmentStart = segmentStart(
          item.units.get(_libraryUnitIndex).text.length(), unitOffset);
      _librarySeekOffset = unitOffset - _librarySegmentStart;
      findViewById(R.id.reader_unit_navigation).setVisibility(View.VISIBLE);
      loadLibrarySegment(false);
    }
    catch (ReaderLibrary.LibraryException error)
    {
      _status.setText(R.string.reader_library_error);
    }
  }

  private void loadArticleDocument(ReaderLibrary library, boolean play)
      throws ReaderLibrary.LibraryException
  {
    SpannableStringBuilder display = new SpannableStringBuilder();
    StringBuilder speech = new StringBuilder();
    for (ReaderLibrary.ContentUnit unit : _libraryItem.units)
    {
      if (display.length() > 0)
      {
        display.append("\n\n");
        speech.append("\n\n");
      }
      if ("image".equals(unit.kind))
      {
        ArticleImageSpan image = articleImage(library, unit.assetUri);
        if (image == null)
        {
          display.append(unit.text);
          speech.append(unit.text);
          continue;
        }
        int start = display.length();
        display.append('\uFFFC');
        speech.append('\n');
        display.setSpan(image, start, start + 1,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
      else
      {
        display.append(unit.text);
        speech.append(unit.text);
      }
    }
    _speechText = speech.toString();
    _text.setText(display);
    int seekOffset = articleProgressOffset(_libraryItem, _speechText.length());
    _loadingLibraryItem = true;
    ReaderPlaybackService.Snapshot current = _service.snapshot();
    boolean replacing = !_libraryItem.id.equals(current.itemId) ||
      !_service.activeText().equals(_speechText);
    if (replacing)
      _service.load(_libraryItem.id, _libraryItem.title, _speechText, play);
    if (seekOffset > 0)
      _service.seekToCharacter(seekOffset, play);
    _loadingLibraryItem = false;
    onReaderPlaybackChanged(_service.snapshot());
  }

  private ArticleImageSpan articleImage(ReaderLibrary library, String assetUri)
      throws ReaderLibrary.LibraryException
  {
    File file = articleImageFile(library, assetUri);
    if (file == null)
      return null;
    BitmapFactory.Options bounds = new BitmapFactory.Options();
    bounds.inJustDecodeBounds = true;
    BitmapFactory.decodeFile(file.getPath(), bounds);
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
      return null;
    int targetWidth = articleContentWidth();
    int sample = 1;
    while (bounds.outWidth / sample > targetWidth * 2)
      sample *= 2;
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inSampleSize = sample;
    Bitmap bitmap = BitmapFactory.decodeFile(file.getPath(), options);
    if (bitmap == null)
      return null;
    BitmapDrawable drawable = new BitmapDrawable(getResources(), bitmap);
    return new ArticleImageSpan(drawable, assetUri, bounds.outWidth,
        bounds.outHeight, targetWidth);
  }

  private File articleImageFile(ReaderLibrary library, String assetUri)
      throws ReaderLibrary.LibraryException
  {
    if (assetUri == null || !assetUri.startsWith("private:"))
      return null;
    return library.privateSourceFile(
        assetUri.substring("private:".length()));
  }

  private int articleContentWidth()
  {
    int width = _text.getWidth() - _text.getPaddingLeft() -
      _text.getPaddingRight();
    if (width > 0)
      return width;
    width = _articleScroll.getWidth() - _articleScroll.getPaddingLeft() -
      _articleScroll.getPaddingRight() - _text.getPaddingLeft() -
      _text.getPaddingRight();
    if (width > 0)
      return width;
    float density = getResources().getDisplayMetrics().density;
    return Math.max(1, getResources().getDisplayMetrics().widthPixels -
        Math.round(32f * density));
  }

  private void resizeArticleImages()
  {
    Spannable text = _text.getText();
    ArticleImageSpan[] images = text.getSpans(
        0, text.length(), ArticleImageSpan.class);
    boolean changed = false;
    int width = articleContentWidth();
    for (ArticleImageSpan image : images)
      changed |= image.fitToWidth(width);
    if (changed)
    {
      _text.requestLayout();
      _text.invalidate();
    }
  }

  private ArticleImageSpan articleImageAt(int displayOffset)
  {
    Spannable text = _text.getText();
    if (text.length() == 0)
      return null;
    int offset = Math.max(0, Math.min(displayOffset, text.length() - 1));
    if (text.charAt(offset) != '\uFFFC' && offset > 0 &&
        text.charAt(offset - 1) == '\uFFFC')
      offset--;
    if (text.charAt(offset) != '\uFFFC')
      return null;
    ArticleImageSpan[] images = text.getSpans(
        offset, offset + 1, ArticleImageSpan.class);
    return images.length == 0 ? null : images[0];
  }

  private void showArticleImage(String assetUri)
  {
    try (ReaderLibrary library = new ReaderLibrary(this))
    {
      File file = articleImageFile(library, assetUri);
      if (file == null)
      {
        _status.setText(R.string.reader_image_unavailable);
        return;
      }
      ReaderImageViewer.show(this, file);
    }
    catch (ReaderLibrary.LibraryException error)
    {
      _status.setText(R.string.reader_image_unavailable);
    }
  }

  private int articleProgressOffset(ReaderLibrary.Item item, int length)
  {
    String locator = item.progressLocator;
    if (locator != null && locator.startsWith("article:"))
      try
      {
        return Math.max(0, Math.min(length,
              Integer.parseInt(locator.substring("article:".length()))));
      }
      catch (NumberFormatException ignored) {}
    return Math.max(0, Math.min(length,
          Math.round(length * item.progressFraction)));
  }

  private void setPlaybackPoint(int displayOffset)
  {
    if (_service == null || _speechText.isEmpty())
      return;
    int offset = Math.max(0, Math.min(displayOffset, _speechText.length()));
    while (offset < _speechText.length() &&
        Character.isWhitespace(_speechText.charAt(offset)))
      offset++;
    ReaderPlaybackService.Snapshot snapshot = _service.snapshot();
    boolean resume = snapshot.status == ReaderPlaybackService.Status.PLAYING ||
      snapshot.status == ReaderPlaybackService.Status.PREPARING;
    if (!_speechText.equals(_service.activeText()))
    {
      String id = _libraryItem == null
        ? "reader-screen:" + System.currentTimeMillis() : _libraryItem.id;
      String title = _libraryItem == null
        ? getString(R.string.reader_default_title) : _libraryItem.title;
      _service.load(id, title, _speechText, false);
    }
    _tapOffset = offset;
    _service.seekToCharacter(offset, resume);
  }

  private void openOriginal()
  {
    if (_libraryItem == null ||
        !isSafeOriginalUri(_libraryItem.sourceUri))
      return;
    try
    {
      startActivity(new Intent(Intent.ACTION_VIEW,
            Uri.parse(_libraryItem.sourceUri)));
    }
    catch (RuntimeException error)
    {
      _status.setText(R.string.reader_open_original_error);
    }
  }

  static boolean isSafeOriginalUri(String value)
  {
    try
    {
      URI uri = new URI(value == null ? "" : value.trim());
      String scheme = uri.getScheme();
      int port = uri.getPort();
      return ("http".equalsIgnoreCase(scheme) ||
          "https".equalsIgnoreCase(scheme)) &&
        uri.getHost() != null && uri.getUserInfo() == null &&
        (port == -1 || port == 80 || port == 443);
    }
    catch (Exception error)
    {
      return false;
    }
  }

  private int[] parseLibraryLocator(ReaderLibrary.Item item)
  {
    int unitIndex = 0;
    int unitOffset = Math.round(item.units.get(0).text.length() *
        item.progressFraction);
    String locator = item.progressLocator;
    if (locator != null && locator.startsWith("unit:"))
    {
      String[] parts = locator.split(":", 3);
      try
      {
        unitIndex = Math.max(0, Math.min(Integer.parseInt(parts[1]),
              item.units.size() - 1));
        unitOffset = parts.length < 3 ? 0 : Integer.parseInt(parts[2]);
      }
      catch (NumberFormatException ignored)
      {
        unitIndex = 0;
        unitOffset = 0;
      }
    }
    else if (locator != null)
    {
      for (int i = 0; i < item.units.size(); i++)
      {
        if (locator.equals(item.units.get(i).sourceLocator))
        {
          unitIndex = i;
          unitOffset = 0;
          break;
        }
      }
    }
    unitOffset = Math.max(0,
        Math.min(unitOffset, item.units.get(unitIndex).text.length()));
    return new int[] { unitIndex, unitOffset };
  }

  private int segmentStart(int textLength, int offset)
  {
    int maximum = ReaderPlaybackService.MAX_ACTIVE_TEXT_LENGTH;
    if (textLength <= maximum)
      return 0;
    int clamped = Math.max(0, Math.min(offset, textLength - 1));
    return (clamped / maximum) * maximum;
  }

  private void loadLibrarySegment(boolean play)
  {
    if (_service == null || _libraryItem == null)
      return;
    ReaderLibrary.ContentUnit unit =
      _libraryItem.units.get(_libraryUnitIndex);
    int end = Math.min(unit.text.length(), _librarySegmentStart +
        ReaderPlaybackService.MAX_ACTIVE_TEXT_LENGTH);
    String text = unit.text.substring(_librarySegmentStart, end);
    _speechText = text;
    _text.setText(text);
    updateLibraryUnitLabel();
    _loadingLibraryItem = true;
    ReaderPlaybackService.Snapshot current = _service.snapshot();
    boolean replacing = !_libraryItem.id.equals(current.itemId) ||
      !_service.activeText().equals(text);
    if (replacing)
    {
      _service.load(_libraryItem.id, _libraryItem.title, text, play);
      if (_librarySeekOffset > 0)
        _service.seekToCharacter(_librarySeekOffset, play);
    }
    _librarySeekOffset = -1;
    _loadingLibraryItem = false;
    onReaderPlaybackChanged(_service.snapshot());
  }

  private void moveLibrarySection(int direction)
  {
    if (_libraryItem == null || _service == null || direction == 0)
      return;
    ReaderPlaybackService.Snapshot current = _service.snapshot();
    saveLibraryProgress(current);
    boolean play = current.status == ReaderPlaybackService.Status.PLAYING ||
      current.status == ReaderPlaybackService.Status.PREPARING;
    ReaderLibrary.ContentUnit unit =
      _libraryItem.units.get(_libraryUnitIndex);
    if (direction > 0)
    {
      int next = _librarySegmentStart +
        ReaderPlaybackService.MAX_ACTIVE_TEXT_LENGTH;
      if (next < unit.text.length())
        _librarySegmentStart = next;
      else if (_libraryUnitIndex + 1 < _libraryItem.units.size())
      {
        _libraryUnitIndex++;
        _librarySegmentStart = 0;
      }
      else
        return;
    }
    else if (_librarySegmentStart > 0)
      _librarySegmentStart = Math.max(0, _librarySegmentStart -
          ReaderPlaybackService.MAX_ACTIVE_TEXT_LENGTH);
    else if (_libraryUnitIndex > 0)
    {
      _libraryUnitIndex--;
      int length = _libraryItem.units.get(_libraryUnitIndex).text.length();
      _librarySegmentStart = segmentStart(length, length);
    }
    else
      return;
    _librarySeekOffset = 0;
    loadLibrarySegment(play);
  }

  private void updateLibraryUnitLabel()
  {
    ReaderLibrary.ContentUnit unit =
      _libraryItem.units.get(_libraryUnitIndex);
    String label;
    if ("page".equals(unit.kind) && unit.sourceLocator != null &&
        unit.sourceLocator.startsWith("page:"))
      label = getString(R.string.reader_unit_page,
          unit.sourceLocator.substring("page:".length()));
    else if ("chapter".equals(unit.kind))
      label = getString(R.string.reader_unit_chapter,
          _libraryUnitIndex + 1, _libraryItem.units.size());
    else
      label = getString(R.string.reader_unit_plain);
    int parts = Math.max(1, (unit.text.length() +
          ReaderPlaybackService.MAX_ACTIVE_TEXT_LENGTH - 1) /
        ReaderPlaybackService.MAX_ACTIVE_TEXT_LENGTH);
    int part = _librarySegmentStart /
      ReaderPlaybackService.MAX_ACTIVE_TEXT_LENGTH + 1;
    if (parts > 1)
      label = getString(R.string.reader_unit_part, label, part, parts);
    _unitLabel.setText(label);
    findViewById(R.id.reader_unit_previous).setEnabled(
        _libraryUnitIndex > 0 || _librarySegmentStart > 0);
    findViewById(R.id.reader_unit_next).setEnabled(
        _libraryUnitIndex + 1 < _libraryItem.units.size() ||
        _librarySegmentStart + ReaderPlaybackService.MAX_ACTIVE_TEXT_LENGTH <
          unit.text.length());
  }

  private float libraryProgress(ReaderPlaybackService.Snapshot snapshot)
  {
    if (_libraryItem.sourceType == ReaderLibrary.SourceType.URL)
      return _speechText.isEmpty() ? 0f :
        Math.max(0f, Math.min(1f,
              snapshot.characterOffset / (float)_speechText.length()));
    long complete = 0L;
    long total = 0L;
    for (int i = 0; i < _libraryItem.units.size(); i++)
    {
      int length = _libraryItem.units.get(i).text.length();
      total += length;
      if (i < _libraryUnitIndex)
        complete += length;
    }
    complete += Math.min(_libraryItem.units.get(_libraryUnitIndex).text.length(),
        _librarySegmentStart + snapshot.characterOffset);
    return total == 0L ? 0f : (float)complete / (float)total;
  }

  private void saveLibraryProgress(ReaderPlaybackService.Snapshot snapshot)
  {
    if (_loadingLibraryItem || _libraryItem == null ||
        !_libraryItem.id.equals(snapshot.itemId))
      return;
    if (_libraryItem.sourceType == ReaderLibrary.SourceType.URL)
    {
      int offset = Math.max(0, Math.min(
            snapshot.characterOffset, _speechText.length()));
      float fraction = _speechText.isEmpty() ? 0f :
        offset / (float)_speechText.length();
      try (ReaderLibrary library = new ReaderLibrary(this))
      {
        library.updateProgress(_libraryItem.id, "article:" + offset,
            fraction, offset >= _speechText.length(),
            System.currentTimeMillis());
      }
      catch (ReaderLibrary.LibraryException error)
      {
        _status.setText(R.string.reader_library_error);
      }
      return;
    }
    ReaderLibrary.ContentUnit unit =
      _libraryItem.units.get(_libraryUnitIndex);
    int unitOffset = Math.min(unit.text.length(),
        _librarySegmentStart + snapshot.characterOffset);
    float fraction = libraryProgress(snapshot);
    boolean finished = _libraryUnitIndex == _libraryItem.units.size() - 1 &&
      unitOffset >= unit.text.length();
    try (ReaderLibrary library = new ReaderLibrary(this))
    {
      library.updateProgress(_libraryItem.id,
          "unit:" + _libraryUnitIndex + ":" + unitOffset,
          fraction, finished, System.currentTimeMillis());
    }
    catch (ReaderLibrary.LibraryException error)
    {
      _status.setText(R.string.reader_library_error);
    }
  }

  private void requestPlayOrPause()
  {
    if (_service == null)
      return;
    if (_quickReadToken != null)
    {
      if (ReaderPlaybackService.needsNotificationPermission(this))
      {
        _pendingPlay = true;
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
            REQUEST_NOTIFICATIONS);
      }
      else
        performPlay();
      return;
    }
    ReaderPlaybackService.Snapshot snapshot = _service.snapshot();
    if (snapshot.status == ReaderPlaybackService.Status.PLAYING ||
        snapshot.status == ReaderPlaybackService.Status.PREPARING)
    {
      _service.pause(false);
      return;
    }
    if (ReaderPlaybackService.needsNotificationPermission(this))
    {
      _pendingPlay = true;
      requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
          REQUEST_NOTIFICATIONS);
      return;
    }
    performPlay();
  }

  private void performPlay()
  {
    if (_service == null)
      return;
    if (_quickReadToken != null)
    {
      PendingQuickRead pending = consumeQuickRead(_quickReadToken);
      clearQuickReadToken();
      if (pending == null)
      {
        _status.setText(R.string.reader_error_unavailable_text);
        return;
      }
      ReaderPlaybackService.playText(this, pending.itemId, pending.title,
          pending.text);
      return;
    }
    String text = _libraryItem == null
      ? _text.getText().toString() : _speechText;
    if (text.trim().isEmpty())
    {
      _status.setText(R.string.reader_error_empty_text);
      return;
    }
    String title = _libraryItem == null
      ? (_unsavedTitle == null
          ? getString(R.string.reader_default_title) : _unsavedTitle)
      : _libraryItem.title;
    String itemId = _libraryItem == null
      ? (_unsavedItemId == null
          ? "reader-screen:" + System.currentTimeMillis() : _unsavedItemId)
      : _libraryItem.id;
    if (!text.equals(_service.activeText()))
      ReaderPlaybackService.playText(this, itemId, title, text);
    else
      ReaderPlaybackService.sendAction(this,
          ReaderPlaybackService.ACTION_PLAY);
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions,
      int[] grantResults)
  {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode != REQUEST_NOTIFICATIONS || !_pendingPlay)
      return;
    _pendingPlay = false;
    if (grantResults.length > 0 &&
        grantResults[0] == PackageManager.PERMISSION_GRANTED)
      performPlay();
    else
    {
      if (_quickReadToken != null)
      {
        discardQuickRead(_quickReadToken);
        clearQuickReadToken();
        _text.setText("");
      }
      _status.setText(R.string.reader_error_notification_permission);
    }
  }

  private void clearQuickReadToken()
  {
    _quickReadToken = null;
    getIntent().removeExtra(EXTRA_QUICK_READ_TOKEN);
  }

  private void withService(String action)
  {
    if (_service != null)
      _service.handleAction(action);
  }

  private void previewSelectedVoice()
  {
    int position = _voice.getSelectedItemPosition();
    if (_service == null || position < 0 || position >= _voiceOptions.size())
      return;
    if (!_service.previewVoice(_voiceOptions.get(position).name,
          getString(R.string.reader_voice_preview_sample)))
      _status.setText(R.string.reader_error_voice_preview);
  }

  @Override
  public void onReaderPlaybackChanged(ReaderPlaybackService.Snapshot snapshot)
  {
    _updatingControls = true;
    setControlsEnabled(true);
    if (_quickReadToken == null && (_displayedItemId == null ||
        !_displayedItemId.equals(snapshot.itemId)))
    {
      _displayedItemId = snapshot.itemId;
      if (_libraryItem == null && !_service.activeText().isEmpty())
      {
        _speechText = _service.activeText();
        _text.setText(_speechText);
      }
    }
    _status.setText(statusText(snapshot));
    boolean playing =
      snapshot.status == ReaderPlaybackService.Status.PLAYING ||
      snapshot.status == ReaderPlaybackService.Status.PREPARING;
    _playPause.setImageResource(playing
        ? R.drawable.ic_reader_pause : R.drawable.ic_reader_play);
    _playPause.setContentDescription(getString(
        playing ? R.string.reader_pause : R.string.reader_play));
    int speedProgress = Math.round(
        snapshot.speechRate * BASE_WORDS_PER_MINUTE) -
      MIN_WORDS_PER_MINUTE;
    _speed.setProgress(Math.max(0,
          Math.min(MAX_WORDS_PER_MINUTE - MIN_WORDS_PER_MINUTE,
            speedProgress)));
    updateSpeedLabel(snapshot.speechRate);
    int pitchProgress = Math.round((snapshot.pitch - 0.5f) * 100f);
    _pitch.setProgress(pitchProgress);
    updatePitchLabel(snapshot.pitch);
    boolean allowNetworkVoices = _service.allowNetworkVoices();
    _networkVoices.setChecked(allowNetworkVoices);
    _updatingControls = false;
    if (_displayedVoiceName == null ||
        !_displayedVoiceName.equals(snapshot.voiceName) ||
        _displayedAllowNetworkVoices != allowNetworkVoices)
      refreshVoices(snapshot.voiceName);
    saveLibraryProgress(snapshot);
    applyFollowAlong(snapshot);
  }

  private void applyFollowAlong(ReaderPlaybackService.Snapshot snapshot)
  {
    Spannable text = _text.getText();
    BackgroundColorSpan[] existing = text.getSpans(
        0, text.length(), BackgroundColorSpan.class);
    for (BackgroundColorSpan span : existing)
      text.removeSpan(span);
    int start;
    int end;
    if (snapshot.status == ReaderPlaybackService.Status.PLAYING &&
        snapshot.highlightStart >= 0 &&
        snapshot.highlightEnd > snapshot.highlightStart &&
        snapshot.highlightEnd <= text.length())
    {
      _tapOffset = -1;
      start = snapshot.highlightStart;
      end = snapshot.highlightEnd;
      if (!_followWords)
      {
        BreakIterator sentences = BreakIterator.getSentenceInstance();
        sentences.setText(text.toString());
        int sentenceStart = sentences.preceding(
            Math.min(text.length(), start + 1));
        int sentenceEnd = sentences.following(Math.max(start, end - 1));
        start = sentenceStart == BreakIterator.DONE ? 0 : sentenceStart;
        end = sentenceEnd == BreakIterator.DONE ? text.length() : sentenceEnd;
      }
    }
    else if (_tapOffset >= 0 && _tapOffset < text.length())
    {
      start = _tapOffset;
      while (start < text.length() &&
          Character.isWhitespace(text.charAt(start)))
        start++;
      if (start >= text.length())
        return;
      BreakIterator words = BreakIterator.getWordInstance();
      words.setText(text.toString());
      int wordStart = words.preceding(Math.min(text.length(), start + 1));
      int wordEnd = words.following(start);
      start = wordStart == BreakIterator.DONE ? start : wordStart;
      end = wordEnd == BreakIterator.DONE ? Math.min(text.length(), start + 1)
        : wordEnd;
    }
    else
      return;
    text.setSpan(new BackgroundColorSpan(themeColor(
          R.attr.readerHighlightColor)),
        start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    if (snapshot.status == ReaderPlaybackService.Status.PLAYING)
      scrollHighlightIntoReadingBand(start, end);
  }

  private int themeColor(int attribute)
  {
    TypedValue value = new TypedValue();
    getTheme().resolveAttribute(attribute, value, true);
    return value.data;
  }

  private void scrollHighlightIntoReadingBand(int start, int end)
  {
    _text.post(() -> {
      Layout layout = _text.getLayout();
      if (layout == null || _articleScroll.getHeight() <= 0)
        return;
      int startLine = layout.getLineForOffset(start);
      int endLine = layout.getLineForOffset(Math.max(start, end - 1));
      int textTop = _text.getTop() + _text.getTotalPaddingTop();
      int lineTop = textTop + layout.getLineTop(startLine);
      int lineBottom = textTop + layout.getLineBottom(endLine);
      int target = readingScrollTarget(_articleScroll.getScrollY(),
          lineTop, lineBottom, _articleScroll.getHeight(),
          _articleScroll.getChildAt(0).getHeight());
      if (target != _articleScroll.getScrollY())
        _articleScroll.smoothScrollTo(0, target);
    });
  }

  static int readingScrollTarget(int currentScroll, int lineTop,
      int lineBottom, int viewportHeight, int contentHeight)
  {
    if (viewportHeight <= 0 || contentHeight <= viewportHeight)
      return 0;
    int bandTop = currentScroll + viewportHeight / 3;
    int bandBottom = currentScroll + viewportHeight * 2 / 3;
    if (lineTop >= bandTop && lineBottom <= bandBottom)
      return currentScroll;
    int target = lineTop - viewportHeight / 3;
    return Math.max(0, Math.min(target, contentHeight - viewportHeight));
  }

  private CharSequence statusText(ReaderPlaybackService.Snapshot snapshot)
  {
    if (snapshot.status == ReaderPlaybackService.Status.ERROR &&
        !snapshot.error.isEmpty())
      return snapshot.error;
    switch (snapshot.status)
    {
      case PREPARING: return getString(R.string.reader_status_preparing);
      case PLAYING: return getString(R.string.reader_status_playing);
      case PAUSED: return getString(R.string.reader_status_paused);
      case STOPPED: return getString(R.string.reader_status_stopped);
      case ERROR: return getString(R.string.reader_status_error);
      default: return getString(R.string.reader_status_ready);
    }
  }

  private void refreshVoices(String selectedVoice)
  {
    if (_service == null)
      return;
    List<ReaderPlaybackService.VoiceOption> available =
      _service.availableVoices();
    List<ReaderPlaybackService.VoiceOption> voices = new ArrayList<>();
    for (ReaderPlaybackService.VoiceOption option : available)
      if (!option.networkRequired)
        voices.add(option);
    for (ReaderPlaybackService.VoiceOption option : available)
      if (option.networkRequired)
        voices.add(option);
    List<VoicePresentation> presentations = new ArrayList<>();
    _voiceOptions.clear();
    _voiceOptions.addAll(voices);
    int selected = -1;
    boolean offlineHeader = false;
    boolean onlineHeader = false;
    for (int i = 0; i < voices.size(); i++)
    {
      ReaderPlaybackService.VoiceOption option = voices.get(i);
      Locale locale = option.localeTag.isEmpty() ? Locale.ROOT :
        Locale.forLanguageTag(option.localeTag);
      VoiceIdentity identity = voiceIdentity(option.name, locale);
      String availability = getString(option.networkRequired
          ? R.string.reader_voice_online : R.string.reader_voice_offline);
      int labelResource = identity.gender.isEmpty()
        ? (option.networkRequired
            ? R.string.reader_voice_network_unknown_label
            : R.string.reader_voice_offline_unknown_label)
        : (option.networkRequired
            ? R.string.reader_voice_network_label
            : R.string.reader_voice_offline_label);
      String label = identity.gender.isEmpty()
        ? getString(labelResource, identity.name)
        : getString(labelResource, identity.name, identity.gender);
      String accessibility = identity.gender.isEmpty()
        ? getString(R.string.reader_voice_accessibility_unknown,
            voiceCountry(locale), identity.name, availability)
        : getString(R.string.reader_voice_accessibility,
            voiceCountry(locale), identity.name, identity.gender,
            availability);
      String groupHeader = null;
      if (option.networkRequired && !onlineHeader)
      {
        onlineHeader = true;
        groupHeader = getString(R.string.reader_voice_group_online);
      }
      else if (!option.networkRequired && !offlineHeader)
      {
        offlineHeader = true;
        groupHeader = getString(R.string.reader_voice_group_offline);
      }
      presentations.add(new VoicePresentation(label, accessibility,
            voiceFlagResource(locale),
            option.networkRequired ? R.drawable.ic_voice_online
              : R.drawable.ic_voice_offline,
            groupHeader));
      if (option.name.equals(selectedVoice))
        selected = i;
    }
    _updatingControls = true;
    ArrayAdapter<VoicePresentation> adapter =
      new ArrayAdapter<VoicePresentation>(this,
          R.layout.reader_voice_option, presentations)
      {
        @Override public View getView(int position, View recycled,
            ViewGroup parent)
        {
          return bindVoiceRow(recycled, parent, getItem(position), false);
        }

        @Override public View getDropDownView(int position, View recycled,
            ViewGroup parent)
        {
          return bindVoiceRow(recycled, parent, getItem(position), true);
        }
      };
    _voice.setAdapter(adapter);
    _voice.setEnabled(!voices.isEmpty());
    findViewById(R.id.reader_preview_voice).setEnabled(!voices.isEmpty());
    if (!voices.isEmpty())
      _voice.setSelection(selected >= 0 ? selected : 0);
    _updatingControls = false;
    _displayedVoiceName = selectedVoice;
    _displayedAllowNetworkVoices = _service.allowNetworkVoices();
  }

  private View bindVoiceRow(View recycled, ViewGroup parent,
      VoicePresentation presentation, boolean showGroup)
  {
    View row = recycled == null
      ? LayoutInflater.from(this).inflate(
          R.layout.reader_voice_option, parent, false)
      : recycled;
    TextView group = (TextView)row.findViewById(R.id.reader_voice_group);
    boolean hasGroup = showGroup && presentation != null &&
      presentation.groupHeader != null;
    group.setVisibility(hasGroup ? View.VISIBLE : View.GONE);
    if (hasGroup)
      group.setText(presentation.groupHeader);
    TextView label = (TextView)row.findViewById(R.id.reader_voice_label);
    ImageView flag = (ImageView)row.findViewById(R.id.reader_voice_flag);
    ImageView status =
      (ImageView)row.findViewById(R.id.reader_voice_status_icon);
    if (presentation != null)
    {
      label.setText(presentation.label);
      flag.setImageResource(presentation.flagResource);
      flag.setVisibility(presentation.flagResource == 0
          ? View.INVISIBLE : View.VISIBLE);
      status.setImageResource(presentation.statusResource);
      row.setContentDescription(presentation.accessibility);
    }
    return row;
  }


  static VoiceIdentity voiceIdentity(String voiceName, Locale locale)
  {
    String id = voiceName == null ? "" :
      voiceName.toLowerCase(Locale.ROOT);
    if (id.endsWith("-network"))
      id = id.substring(0, id.length() - "-network".length()) + "-local";
    switch (id)
    {
      case "en-au-language":
        return new VoiceIdentity("Matilda", "Female");
      case "en-au-x-afh-local":
        return new VoiceIdentity("Ruby", "Female");
      case "en-au-x-aua-local":
        return new VoiceIdentity("Isla", "Female");
      case "en-au-x-aub-local":
        return new VoiceIdentity("William", "Male");
      case "en-au-x-auc-local":
        return new VoiceIdentity("Chloe", "Female");
      case "en-au-x-aud-local":
        return new VoiceIdentity("Jack", "Male");
      case "en-us-language":
        return new VoiceIdentity("Ava", "Female");
      case "en-us-x-iob-local":
        return new VoiceIdentity("Mia", "Female");
      case "en-us-x-iog-local":
        return new VoiceIdentity("Emma", "Female");
      case "en-us-x-iol-local":
        return new VoiceIdentity("Liam", "Male");
      case "en-us-x-iom-local":
        return new VoiceIdentity("Ethan", "Male");
      case "en-us-x-sfg-local":
        return new VoiceIdentity("Sophia", "Female");
      case "en-us-x-tpc-local":
        return new VoiceIdentity("Isabella", "Female");
      case "en-us-x-tpd-local":
        return new VoiceIdentity("James", "Male");
      case "en-us-x-tpf-local":
        return new VoiceIdentity("Harper", "Female");
      default:
        return new VoiceIdentity(voiceRegion(locale) + " voice", "");
    }
  }

  private static int voiceFlagResource(Locale locale)
  {
    String country = locale == null ? "" : locale.getCountry();
    if ("AU".equalsIgnoreCase(country))
      return R.drawable.ic_flag_au;
    if ("GB".equalsIgnoreCase(country))
      return R.drawable.ic_flag_gb;
    if ("US".equalsIgnoreCase(country))
      return R.drawable.ic_flag_us;
    return 0;
  }

  private static String voiceCountry(Locale locale)
  {
    String country = locale == null ? "" : locale.getCountry();
    if ("AU".equalsIgnoreCase(country))
      return "Australia";
    if ("GB".equalsIgnoreCase(country))
      return "United Kingdom";
    if ("US".equalsIgnoreCase(country))
      return "United States";
    String display = locale == null ? "" : locale.getDisplayCountry();
    return display.isEmpty() ? "System" : display;
  }

  static String voiceRegion(Locale locale)
  {
    if (locale == null)
      return "System";
    String country = locale.getCountry();
    if ("GB".equalsIgnoreCase(country))
      return "British";
    if ("AU".equalsIgnoreCase(country))
      return "Australian";
    if ("US".equalsIgnoreCase(country))
      return "American";
    String region = locale.getDisplayCountry();
    return region.isEmpty() ? locale.getDisplayLanguage() : region;
  }

  private void setControlsEnabled(boolean enabled)
  {
    _playPause.setEnabled(enabled);
    findViewById(R.id.reader_previous).setEnabled(enabled);
    findViewById(R.id.reader_next).setEnabled(enabled);
    findViewById(R.id.reader_stop).setEnabled(enabled);
    findViewById(R.id.reader_preview_voice).setEnabled(enabled);
    _speed.setEnabled(enabled);
    _pitch.setEnabled(enabled);
    _voice.setEnabled(enabled);
    _networkVoices.setEnabled(enabled);
  }

  @Override
  protected void onDestroy()
  {
    if (_service != null)
      saveLibraryProgress(_service.snapshot());
    if (!isChangingConfigurations())
    {
      discardQuickRead(_quickReadToken);
      clearQuickReadToken();
    }
    if (_service != null)
      _service.removeListener(this);
    if (_bound)
      unbindService(_connection);
    _bound = false;
    _service = null;
    super.onDestroy();
  }
}
