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
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Native control surface for the single app-wide Reader playback session. */
public final class ReaderActivity extends Activity
    implements ReaderPlaybackService.Listener
{
  private static final int REQUEST_NOTIFICATIONS = 81;
  private static final String EXTRA_QUICK_READ_TOKEN =
    "juloo.keyboard2.extra.QUICK_READ_TOKEN";
  private static final String EXTRA_REQUEST_PLAY =
    "juloo.keyboard2.extra.REQUEST_PLAY";
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

  private EditText _title;
  private EditText _text;
  private TextView _status;
  private TextView _progressLabel;
  private TextView _speedLabel;
  private SeekBar _progress;
  private SeekBar _speed;
  private Button _playPause;
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
    _requestPlayOnConnect =
      getIntent().getBooleanExtra(EXTRA_REQUEST_PLAY, false);
    bindService(new Intent(this, ReaderPlaybackService.class), _connection,
        Context.BIND_AUTO_CREATE);
  }

  private void bindViews()
  {
    _title = (EditText)findViewById(R.id.reader_title);
    _text = (EditText)findViewById(R.id.reader_text);
    _title.setSaveEnabled(false);
    _text.setSaveEnabled(false);
    _status = (TextView)findViewById(R.id.reader_status);
    _progressLabel = (TextView)findViewById(R.id.reader_progress_label);
    _speedLabel = (TextView)findViewById(R.id.reader_speed_label);
    _progress = (SeekBar)findViewById(R.id.reader_progress);
    _speed = (SeekBar)findViewById(R.id.reader_speed);
    _playPause = (Button)findViewById(R.id.reader_play_pause);
    _voice = (Spinner)findViewById(R.id.reader_voice);
    _networkVoices = (Switch)findViewById(R.id.reader_network_voices);
  }

  private void wireControls()
  {
    findViewById(R.id.reader_back).setOnClickListener(_view -> finish());
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
        _speedLabel.setText(getString(R.string.reader_speed_value, rate));
        if (fromUser && _service != null)
          _service.setSpeechRate(rate);
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
    _title.setText(pending.title);
    _text.setText(pending.text);
    requestPlayOrPause();
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
    String title = _title.getText().toString().trim();
    if (title.isEmpty())
      title = getString(R.string.reader_default_title);
    ReaderPlaybackService.Snapshot snapshot = _service.snapshot();
    if (!text.equals(_service.activeText()) || !title.equals(snapshot.title))
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
        _title.setText("");
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
        _title.setText(snapshot.title);
        _text.setText(_service.activeText());
      }
    }
    _status.setText(statusText(snapshot));
    _playPause.setText(snapshot.status == ReaderPlaybackService.Status.PLAYING ||
        snapshot.status == ReaderPlaybackService.Status.PREPARING
        ? R.string.reader_pause : R.string.reader_play);
    int progress = snapshot.textLength == 0 ? 0 :
      Math.round(1000f * snapshot.characterOffset / snapshot.textLength);
    _progress.setProgress(progress);
    int percent = Math.round(snapshot.progress() * 100f);
    _progressLabel.setText(getString(R.string.reader_progress_value, percent));
    int speedProgress = Math.round((snapshot.speechRate - 0.25f) * 100f);
    _speed.setProgress(speedProgress);
    _speedLabel.setText(getString(R.string.reader_speed_value,
        snapshot.speechRate));
    _networkVoices.setChecked(_service.allowNetworkVoices());
    _updatingControls = false;
    refreshVoices(snapshot.voiceName);
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
    _voice.setEnabled(enabled);
    _networkVoices.setEnabled(enabled);
  }

  @Override
  protected void onDestroy()
  {
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
