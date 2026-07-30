package juloo.keyboard2;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
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
  private String _quickReadToken;
  private String _libraryItemId;
  private ReaderLibrary.Item _libraryItem;
  private int _libraryUnitIndex;
  private int _librarySegmentStart;
  private int _librarySeekOffset = -1;
  private boolean _loadingLibraryItem;

  private EditText _text;
  private TextView _status;
  private TextView _itemMetadata;
  private TextView _unitLabel;
  private TextView _progressLabel;
  private TextView _speedLabel;
  private TextView _pitchLabel;
  private SeekBar _progress;
  private SeekBar _speed;
  private SeekBar _pitch;
  private ImageButton _playPause;
  private Spinner _voice;
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
    super.onCreate(savedInstanceState);
    if (getActionBar() != null)
      getActionBar().hide();
    setContentView(R.layout.reader_activity);
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
    _text.setSaveEnabled(false);
    _status = (TextView)findViewById(R.id.reader_status);
    _itemMetadata = (TextView)findViewById(R.id.reader_item_metadata);
    _unitLabel = (TextView)findViewById(R.id.reader_unit_label);
    _progressLabel = (TextView)findViewById(R.id.reader_progress_label);
    _speedLabel = (TextView)findViewById(R.id.reader_speed_label);
    _pitchLabel = (TextView)findViewById(R.id.reader_pitch_label);
    _progress = (SeekBar)findViewById(R.id.reader_progress);
    _speed = (SeekBar)findViewById(R.id.reader_speed);
    _pitch = (SeekBar)findViewById(R.id.reader_pitch);
    _playPause = (ImageButton)findViewById(R.id.reader_play_pause);
    _voice = (Spinner)findViewById(R.id.reader_voice);
    _networkVoices = (Switch)findViewById(R.id.reader_network_voices);
  }

  private void wireControls()
  {
    findViewById(R.id.reader_back).setOnClickListener(_view -> finish());
    findViewById(R.id.reader_library).setOnClickListener(_view ->
        startActivity(new Intent(this, ReaderLibraryActivity.class)));
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

    _progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
    {
      @Override public void onProgressChanged(SeekBar bar, int progress,
          boolean fromUser) {}
      @Override public void onStartTrackingTouch(SeekBar bar) {}
      @Override public void onStopTrackingTouch(SeekBar bar)
      {
        if (_service == null)
          return;
        ReaderPlaybackService.Snapshot snapshot = _service.snapshot();
        int offset = snapshot.textLength == 0 ? 0 :
          Math.round(snapshot.textLength * (bar.getProgress() / 1000f));
        boolean resume = snapshot.status == ReaderPlaybackService.Status.PLAYING ||
          snapshot.status == ReaderPlaybackService.Status.PREPARING;
        _service.seekToCharacter(offset, resume);
      }
    });

    _speed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
    {
      @Override public void onProgressChanged(SeekBar bar, int progress,
          boolean fromUser)
      {
        float rate = 0.25f + progress / 100f;
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

  private void updateSpeedLabel(float rate)
  {
    int wordsPerMinute = Math.round(BASE_WORDS_PER_MINUTE * rate);
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
      int[] locator = parseLibraryLocator(item);
      _libraryUnitIndex = locator[0];
      int unitOffset = locator[1];
      _librarySegmentStart = segmentStart(
          item.units.get(_libraryUnitIndex).text.length(), unitOffset);
      _librarySeekOffset = unitOffset - _librarySegmentStart;
      _text.setKeyListener(null);
      _text.setFocusable(false);
      _itemMetadata.setVisibility(View.VISIBLE);
      String detail = item.author == null || item.author.trim().isEmpty()
        ? DateFormat.getDateInstance(DateFormat.MEDIUM)
            .format(new Date(item.createdAt))
        : item.author;
      _itemMetadata.setText(getString(R.string.reader_item_metadata,
            item.sourceType.name().replace('_', ' ')
              .toLowerCase(Locale.getDefault()), detail));
      findViewById(R.id.reader_unit_navigation).setVisibility(View.VISIBLE);
      loadLibrarySegment(false);
    }
    catch (ReaderLibrary.LibraryException error)
    {
      _status.setText(R.string.reader_library_error);
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
    String text = _text.getText().toString();
    if (text.trim().isEmpty())
    {
      _status.setText(R.string.reader_error_empty_text);
      return;
    }
    String title = getString(R.string.reader_default_title);
    if (!text.equals(_service.activeText()))
      ReaderPlaybackService.playText(this,
          "reader-screen:" + System.currentTimeMillis(), title, text);
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
      if (!_service.activeText().isEmpty())
      {
        _text.setText(_service.activeText());
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
    int progress = snapshot.textLength == 0 ? 0 :
      Math.round(1000f * snapshot.characterOffset / snapshot.textLength);
    _progress.setProgress(progress);
    int percent = Math.round((_libraryItem != null &&
          _libraryItem.id.equals(snapshot.itemId)
          ? libraryProgress(snapshot) : snapshot.progress()) * 100f);
    _progressLabel.setText(getString(R.string.reader_progress_value, percent));
    int speedProgress = Math.round((snapshot.speechRate - 0.25f) * 100f);
    _speed.setProgress(speedProgress);
    updateSpeedLabel(snapshot.speechRate);
    int pitchProgress = Math.round((snapshot.pitch - 0.5f) * 100f);
    _pitch.setProgress(pitchProgress);
    updatePitchLabel(snapshot.pitch);
    _networkVoices.setChecked(_service.allowNetworkVoices());
    _updatingControls = false;
    refreshVoices(snapshot.voiceName);
    saveLibraryProgress(snapshot);
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
    List<ReaderPlaybackService.VoiceOption> voices =
      _service.availableVoices();
    List<String> labels = new ArrayList<String>();
    _voiceOptions.clear();
    _voiceOptions.addAll(voices);
    int selected = -1;
    for (int i = 0; i < voices.size(); i++)
    {
      ReaderPlaybackService.VoiceOption option = voices.get(i);
      Locale locale = option.localeTag.isEmpty() ? Locale.ROOT :
        Locale.forLanguageTag(option.localeTag);
      String language = locale == Locale.ROOT ? option.localeTag :
        locale.getDisplayName();
      labels.add(getString(option.networkRequired
          ? R.string.reader_voice_network_label
          : R.string.reader_voice_offline_label,
          language, option.name));
      if (option.name.equals(selectedVoice))
        selected = i;
    }
    _updatingControls = true;
    ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
        android.R.layout.simple_spinner_item, labels);
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    _voice.setAdapter(adapter);
    _voice.setEnabled(!voices.isEmpty());
    findViewById(R.id.reader_preview_voice).setEnabled(!voices.isEmpty());
    if (selected >= 0)
      _voice.setSelection(selected);
    _updatingControls = false;
  }

  private void setControlsEnabled(boolean enabled)
  {
    _playPause.setEnabled(enabled);
    findViewById(R.id.reader_previous).setEnabled(enabled);
    findViewById(R.id.reader_next).setEnabled(enabled);
    findViewById(R.id.reader_stop).setEnabled(enabled);
    findViewById(R.id.reader_preview_voice).setEnabled(enabled);
    _progress.setEnabled(enabled);
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
