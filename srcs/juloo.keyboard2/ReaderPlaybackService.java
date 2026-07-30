package juloo.keyboard2;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;

/** Owns the single Reader speech and media session shared by every app surface. */
public final class ReaderPlaybackService extends Service
{
  public static final String ACTION_PLAY = "juloo.keyboard2.reader.PLAY";
  public static final String ACTION_PAUSE = "juloo.keyboard2.reader.PAUSE";
  public static final String ACTION_PREVIOUS = "juloo.keyboard2.reader.PREVIOUS";
  public static final String ACTION_NEXT = "juloo.keyboard2.reader.NEXT";
  public static final String ACTION_STOP = "juloo.keyboard2.reader.STOP";
  public static final String ACTION_LOAD_AND_PLAY = "juloo.keyboard2.reader.LOAD_AND_PLAY";
  public static final String EXTRA_ITEM_ID = "reader_item_id";
  public static final String EXTRA_TITLE = "reader_title";
  public static final String EXTRA_TEXT = "reader_text";

  static final int NOTIFICATION_ID = 4041;
  static final int MAX_ACTIVE_TEXT_LENGTH = 200000;
  static final int MAX_TITLE_LENGTH = 200;
  static final int MAX_ITEM_ID_LENGTH = 256;
  private static final String CHANNEL_ID = "reader_playback";
  private static final String STORE_NAME = "reader_playback";
  private static final int QUEUE_CAPACITY = 2;
  private static final long MEDIA_ACTIONS = PlaybackState.ACTION_PLAY |
    PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_STOP |
    PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS;

  public enum Status { EMPTY, PREPARING, PLAYING, PAUSED, STOPPED, ERROR }

  public interface Listener
  {
    void onReaderPlaybackChanged(Snapshot snapshot);
  }

  public static final class Snapshot
  {
    public final String itemId;
    public final String title;
    public final Status status;
    public final int characterOffset;
    public final int textLength;
    public final float speechRate;
    public final float pitch;
    public final String voiceName;
    public final String error;

    Snapshot(String itemId, String title, Status status, int characterOffset,
        int textLength, float speechRate, float pitch, String voiceName,
        String error)
    {
      this.itemId = itemId;
      this.title = title;
      this.status = status;
      this.characterOffset = characterOffset;
      this.textLength = textLength;
      this.speechRate = speechRate;
      this.pitch = pitch;
      this.voiceName = voiceName;
      this.error = error;
    }

    public float progress()
    {
      return textLength == 0 ? 0f : (float)characterOffset / (float)textLength;
    }
  }

  public static final class VoiceOption
  {
    public final String name;
    public final String localeTag;
    public final boolean networkRequired;

    VoiceOption(String name, String localeTag, boolean networkRequired)
    {
      this.name = name;
      this.localeTag = localeTag;
      this.networkRequired = networkRequired;
    }
  }

  public final class LocalBinder extends Binder
  {
    public ReaderPlaybackService service() { return ReaderPlaybackService.this; }
  }

  private final IBinder _binder = new LocalBinder();
  private final List<Listener> _listeners = new ArrayList<Listener>();
  private final Handler _mainHandler = new Handler(Looper.getMainLooper());
  private final AudioManager.OnAudioFocusChangeListener _focusListener =
    this::onAudioFocusChanged;
  private final BroadcastReceiver _noisyReceiver = new BroadcastReceiver()
  {
    @Override public void onReceive(Context context, Intent intent)
    {
      if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction()))
        pause(false);
    }
  };

  private SharedPreferences _store;
  private TextToSpeech _tts;
  private MediaSession _mediaSession;
  private AudioManager _audioManager;
  private AudioFocusRequest _audioFocusRequest;
  private ReaderChunkQueue _chunks;
  private String _itemId = "";
  private String _title = "";
  private String _text = "";
  private String _voiceName = "";
  private String _error = "";
  private Status _status = Status.EMPTY;
  private int _characterOffset;
  private int _utteranceGeneration;
  private float _speechRate = 1f;
  private float _pitch = 1f;
  private boolean _ttsReady;
  private boolean _playWhenReady;
  private boolean _foreground;
  private boolean _resumeAfterFocusLoss;
  private boolean _ducked;
  private boolean _allowNetworkVoices;
  private boolean _destroyed;

  @Override
  public void onCreate()
  {
    super.onCreate();
    _store = getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE);
    restore();
    _audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
    createNotificationChannel();
    createMediaSession();
    registerNoisyReceiver();
    _tts = new TextToSpeech(this,
      status -> _mainHandler.post(() -> onTtsInitialized(status)));
  }

  @Override public IBinder onBind(Intent intent) { return _binder; }
  @Override public boolean onUnbind(Intent intent) { return true; }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId)
  {
    if (intent == null)
      return START_NOT_STICKY;
    String action = intent.getAction();
    boolean foregroundLaunch = ACTION_LOAD_AND_PLAY.equals(action) ||
      ACTION_PLAY.equals(action);
    if (foregroundLaunch)
      ensureForeground();
    if (ACTION_LOAD_AND_PLAY.equals(action))
    {
      load(intent.getStringExtra(EXTRA_ITEM_ID), intent.getStringExtra(EXTRA_TITLE),
          intent.getStringExtra(EXTRA_TEXT), true);
    }
    else
      handleAction(action);
    if (foregroundLaunch && (_text.isEmpty() || _status == Status.ERROR))
    {
      stopForegroundCompat();
      stopSelf();
    }
    return START_NOT_STICKY;
  }

  public static void playText(Context context, String itemId, String title,
      String text)
  {
    Intent intent = serviceIntent(context, ACTION_LOAD_AND_PLAY)
      .putExtra(EXTRA_ITEM_ID, bounded(itemId, MAX_ITEM_ID_LENGTH))
      .putExtra(EXTRA_TITLE, bounded(title, MAX_TITLE_LENGTH))
      .putExtra(EXTRA_TEXT, text);
    startExplicitService(context, intent);
  }

  public static void sendAction(Context context, String action)
  {
    Intent intent = serviceIntent(context, action);
    if (ACTION_PLAY.equals(action))
      startExplicitService(context, intent);
    else
      context.startService(intent);
  }

  public static boolean needsNotificationPermission(Context context)
  {
    return Build.VERSION.SDK_INT >= 33 &&
      context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED;
  }

  private static Intent serviceIntent(Context context, String action)
  {
    return new Intent(context, ReaderPlaybackService.class).setAction(action);
  }

  private static void startExplicitService(Context context, Intent intent)
  {
    if (Build.VERSION.SDK_INT >= 26)
      context.startForegroundService(intent);
    else
      context.startService(intent);
  }

  public void addListener(Listener listener)
  {
    if (listener == null || _listeners.contains(listener))
      return;
    _listeners.add(listener);
    listener.onReaderPlaybackChanged(snapshot());
  }

  public void removeListener(Listener listener)
  {
    _listeners.remove(listener);
  }

  public Snapshot snapshot()
  {
    return new Snapshot(_itemId, _title, _status, _characterOffset,
        _text.length(), _speechRate, _pitch, _voiceName, _error);
  }

  public void load(String itemId, String title, String text, boolean play)
  {
    if (text == null || text.trim().isEmpty())
    {
      fail(getString(R.string.reader_error_empty_text));
      return;
    }
    if (text.length() > MAX_ACTIVE_TEXT_LENGTH)
    {
      fail(getString(R.string.reader_error_text_too_long));
      return;
    }
    stopSpeech(false);
    _itemId = bounded(itemId, MAX_ITEM_ID_LENGTH);
    String safeTitle = bounded(title, MAX_TITLE_LENGTH).trim();
    _title = safeTitle.isEmpty()
      ? getString(R.string.reader_default_title) : safeTitle;
    _text = text;
    _characterOffset = 0;
    _chunks = new ReaderChunkQueue(_text, safeSpeechLimit(), QUEUE_CAPACITY, 0);
    _error = "";
    _playWhenReady = play;
    _status = play ? Status.PREPARING : Status.PAUSED;
    persistContent();
    publish();
    if (play)
      play();
  }

  public void handleAction(String action)
  {
    if (ACTION_PLAY.equals(action))
      play();
    else if (ACTION_PAUSE.equals(action))
      pause(false);
    else if (ACTION_PREVIOUS.equals(action))
      previous();
    else if (ACTION_NEXT.equals(action))
      next();
    else if (ACTION_STOP.equals(action))
      stopAndRemove();
  }

  public void setSpeechRate(float rate)
  {
    _speechRate = clamp(rate, 0.25f, 3f);
    if (_ttsReady)
      _tts.setSpeechRate(_speechRate);
    persistProgress();
    publish();
  }

  public void setPitch(float pitch)
  {
    _pitch = clamp(pitch, 0.5f, 2f);
    if (_ttsReady)
      _tts.setPitch(_pitch);
    persistProgress();
    publish();
  }

  public boolean setVoice(String voiceName)
  {
    if (!_ttsReady || voiceName == null)
      return false;
    Set<Voice> voices = _tts.getVoices();
    if (voices == null)
      return false;
    for (Voice voice : voices)
    {
      if (voiceName.equals(voice.getName()) && isInstalled(voice) &&
          (_allowNetworkVoices || !voice.isNetworkConnectionRequired()) &&
          _tts.setVoice(voice) == TextToSpeech.SUCCESS)
      {
        _voiceName = voiceName;
        persistProgress();
        publish();
        return true;
      }
    }
    return false;
  }

  public List<VoiceOption> availableVoices()
  {
    if (!_ttsReady || _tts == null)
      return Collections.emptyList();
    return visibleVoices(_tts.getVoices(), _allowNetworkVoices);
  }

  public boolean allowNetworkVoices()
  {
    return _allowNetworkVoices;
  }

  public void setAllowNetworkVoices(boolean allow)
  {
    _allowNetworkVoices = allow;
    if (!allow && _ttsReady && !hasAllowedVoice())
    {
      boolean resume = _playWhenReady;
      stopSpeech(false);
      _voiceName = "";
      selectDefaultOfflineVoice();
      if (!hasAllowedVoice())
      {
        fail(getString(R.string.reader_error_no_offline_voice));
        return;
      }
      persistProgress();
      if (resume)
        play();
      else
        publish();
      return;
    }
    persistProgress();
    publish();
  }

  public boolean previewVoice(String voiceName, String sample)
  {
    if (!_ttsReady || sample == null || sample.trim().isEmpty() ||
        !setVoice(voiceName))
      return false;
    pause(false);
    return _tts.speak(sample, TextToSpeech.QUEUE_FLUSH, null,
        "reader-voice-preview") == TextToSpeech.SUCCESS;
  }

  public String activeText()
  {
    return _text;
  }

  public void seekToCharacter(int characterOffset, boolean play)
  {
    if (_chunks == null)
      return;
    stopSpeech(false);
    _characterOffset = Math.max(0,
      Math.min(characterOffset, _text.length()));
    _chunks.seek(_characterOffset);
    _status = Status.PAUSED;
    persistProgress();
    publish();
    if (play)
      play();
  }

  static List<VoiceOption> visibleVoices(Set<Voice> voices,
      boolean includeNetwork)
  {
    List<VoiceOption> result = new ArrayList<VoiceOption>();
    if (voices == null)
      return result;
    for (Voice voice : voices)
    {
      Set<String> features = voice.getFeatures();
      if (features != null &&
          features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED))
        continue;
      if (voice.isNetworkConnectionRequired() && !includeNetwork)
        continue;
      Locale locale = voice.getLocale();
      result.add(new VoiceOption(voice.getName(),
          locale == null ? "" : locale.toLanguageTag(),
          voice.isNetworkConnectionRequired()));
    }
    Collections.sort(result, new Comparator<VoiceOption>()
    {
      @Override public int compare(VoiceOption left, VoiceOption right)
      {
        if (left.networkRequired != right.networkRequired)
          return left.networkRequired ? 1 : -1;
        int locale = left.localeTag.compareToIgnoreCase(right.localeTag);
        return locale == 0 ? left.name.compareToIgnoreCase(right.name) : locale;
      }
    });
    return result;
  }

  public void play()
  {
    if (_text.isEmpty())
      return;
    if (_ttsReady && !hasAllowedVoice())
    {
      _playWhenReady = false;
      fail(getString(R.string.reader_error_no_offline_voice));
      return;
    }
    ensureForeground();
    _playWhenReady = true;
    _resumeAfterFocusLoss = false;
    if (!_ttsReady)
    {
      _status = Status.PREPARING;
      publish();
      return;
    }
    if (!requestAudioFocus())
    {
      _playWhenReady = false;
      fail(getString(R.string.reader_error_audio_focus));
      return;
    }
    speakCurrent();
  }

  public void pause(boolean resumeAfterFocusLoss)
  {
    if (_status != Status.PLAYING && _status != Status.PREPARING)
      return;
    _resumeAfterFocusLoss = resumeAfterFocusLoss;
    _playWhenReady = false;
    _utteranceGeneration++;
    if (_tts != null)
      _tts.stop();
    _status = Status.PAUSED;
    persistProgress();
    publish();
  }

  public void next()
  {
    if (_chunks == null)
      return;
    boolean wasPlaying = _status == Status.PLAYING || _status == Status.PREPARING;
    _utteranceGeneration++;
    if (_tts != null)
      _tts.stop();
    ReaderChunkQueue.Span next = _chunks.advance();
    _characterOffset = next == null ? _text.length() : next.start;
    persistProgress();
    if (_characterOffset >= _text.length())
      finishPlayback();
    else if (wasPlaying)
      play();
    else
      publish();
  }

  public void previous()
  {
    if (_chunks == null)
      return;
    boolean wasPlaying = _status == Status.PLAYING || _status == Status.PREPARING;
    _utteranceGeneration++;
    if (_tts != null)
      _tts.stop();
    _characterOffset = _chunks.movePrevious();
    persistProgress();
    if (wasPlaying)
      play();
    else
      publish();
  }

  private void speakCurrent()
  {
    if (!_ttsReady || !_playWhenReady || _chunks == null)
      return;
    if (!hasAllowedVoice())
    {
      _playWhenReady = false;
      fail(getString(R.string.reader_error_no_offline_voice));
      return;
    }
    ReaderChunkQueue.Span span = _chunks.current();
    if (span == null)
    {
      finishPlayback();
      return;
    }
    _characterOffset = span.start;
    _status = Status.PLAYING;
    _error = "";
    int generation = ++_utteranceGeneration;
    String utteranceId = generation + ":" + span.start + ":" + span.end;
    Bundle parameters = new Bundle();
    parameters.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, _ducked ? 0.2f : 1f);
    int result = _tts.speak(_text.substring(span.start, span.end),
        TextToSpeech.QUEUE_FLUSH, parameters, utteranceId);
    if (result == TextToSpeech.ERROR)
    {
      _playWhenReady = false;
      fail(getString(R.string.reader_error_playback));
      return;
    }
    publish();
  }

  private void onUtteranceDone(String utteranceId)
  {
    String[] fields = utteranceId == null ? new String[0] : utteranceId.split(":");
    if (fields.length != 3)
      return;
    int generation;
    int end;
    try
    {
      generation = Integer.parseInt(fields[0]);
      end = Integer.parseInt(fields[2]);
    }
    catch (NumberFormatException e) { return; }
    if (_destroyed || generation != _utteranceGeneration ||
        !_playWhenReady || _chunks == null)
      return;
    _characterOffset = end;
    _chunks.advance();
    persistProgress();
    speakCurrent();
  }
  void onUtteranceError(String utteranceId)
  {
    if (_destroyed || !_playWhenReady || utteranceId == null)
      return;
    int separator = utteranceId.indexOf(':');
    if (separator <= 0)
      return;
    int generation;
    try
    {
      generation = Integer.parseInt(utteranceId.substring(0, separator));
    }
    catch (NumberFormatException e) { return; }
    if (generation != _utteranceGeneration)
      return;
    _playWhenReady = false;
    fail(getString(R.string.reader_error_playback));
  }

  void onTtsInitialized(int status)
  {
    if (_destroyed || _tts == null)
      return;
    if (status != TextToSpeech.SUCCESS)
    {
      _ttsReady = false;
      _playWhenReady = false;
      fail(getString(R.string.reader_error_tts_unavailable));
      return;
    }
    _ttsReady = true;
    _tts.setSpeechRate(_speechRate);
    _tts.setPitch(_pitch);
    _tts.setAudioAttributes(new AudioAttributes.Builder()
      .setUsage(AudioAttributes.USAGE_MEDIA)
      .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
      .build());
    restoreVoice();
    _tts.setOnUtteranceProgressListener(new UtteranceProgressListener()
    {
      @Override public void onStart(String utteranceId) {}
      @Override public void onDone(String utteranceId)
      {
        _mainHandler.post(() -> onUtteranceDone(utteranceId));
      }
      @Override public void onError(String utteranceId)
      {
        _mainHandler.post(() -> onUtteranceError(utteranceId));
      }
    });
    if (_playWhenReady)
      play();
    else
      publish();
  }

  private void restoreVoice()
  {
    Set<Voice> voices = _tts.getVoices();
    if (voices != null && !_voiceName.isEmpty())
    {
      for (Voice voice : voices)
      {
        if (_voiceName.equals(voice.getName()) && isInstalled(voice) &&
            (_allowNetworkVoices || !voice.isNetworkConnectionRequired()) &&
            _tts.setVoice(voice) == TextToSpeech.SUCCESS)
          return;
      }
    }
    _voiceName = "";
    selectDefaultOfflineVoice();
    persistProgress();
  }


  private void selectDefaultOfflineVoice()
  {
    if (_tts == null)
      return;
    Set<Voice> voices = _tts.getVoices();
    if (voices == null)
      return;
    Voice fallback = null;
    Locale preferred = Locale.getDefault();
    for (Voice voice : voices)
    {
      if (!isInstalled(voice) || voice.isNetworkConnectionRequired())
        continue;
      if (fallback == null)
        fallback = voice;
      Locale locale = voice.getLocale();
      if (locale != null &&
          locale.getLanguage().equals(preferred.getLanguage()))
      {
        fallback = voice;
        break;
      }
    }
    if (fallback != null && _tts.setVoice(fallback) == TextToSpeech.SUCCESS)
      _voiceName = fallback.getName();
  }

  private boolean hasAllowedVoice()
  {
    if (_tts == null)
      return false;
    Voice voice = _tts.getVoice();
    return voice != null && isInstalled(voice) &&
      (_allowNetworkVoices || !voice.isNetworkConnectionRequired());
  }

  private static boolean isInstalled(Voice voice)
  {
    Set<String> features = voice.getFeatures();
    return features == null ||
      !features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED);
  }

  private void finishPlayback()
  {
    _playWhenReady = false;
    _status = Status.STOPPED;
    _characterOffset = _text.length();
    abandonAudioFocus();
    persistProgress();
    publish();
  }

  private void stopAndRemove()
  {
    stopSpeech(true);
    _status = _text.isEmpty() ? Status.EMPTY : Status.STOPPED;
    _characterOffset = 0;
    if (_chunks != null)
      _chunks.seek(0);
    persistProgress();
    publish();
    stopForegroundCompat();
    stopSelf();
  }

  private void stopSpeech(boolean abandonFocus)
  {
    _playWhenReady = false;
    _resumeAfterFocusLoss = false;
    _utteranceGeneration++;
    if (_tts != null)
      _tts.stop();
    if (abandonFocus)
      abandonAudioFocus();
  }

  private void fail(String message)
  {
    _status = Status.ERROR;
    _error = message;
    persistProgress();
    publish();
  }

  private void restore()
  {
    _itemId = _store.getString("item_id", "");
    _title = _store.getString("title", "");
    _text = _store.getString("text", "");
    _voiceName = _store.getString("voice", "");
    _allowNetworkVoices = _store.getBoolean("allow_network_voices", false);
    _speechRate = clamp(_store.getFloat("rate", 1f), 0.25f, 3f);
    _pitch = clamp(_store.getFloat("pitch", 1f), 0.5f, 2f);
    _characterOffset = Math.max(0,
      Math.min(_store.getInt("offset", 0), _text.length()));
    _status = _text.isEmpty() ? Status.EMPTY : Status.PAUSED;
    if (!_text.isEmpty())
      _chunks = new ReaderChunkQueue(_text, safeSpeechLimit(), QUEUE_CAPACITY,
          _characterOffset);
  }

  private void persistContent()
  {
    _store.edit()
      .putString("item_id", _itemId)
      .putString("title", _title)
      .putString("text", _text)
      .putInt("offset", _characterOffset)
      .putFloat("rate", _speechRate)
      .putFloat("pitch", _pitch)
      .putString("voice", _voiceName)
      .putBoolean("allow_network_voices", _allowNetworkVoices)
      .apply();
  }

  private void persistProgress()
  {
    _store.edit()
      .putInt("offset", _characterOffset)
      .putFloat("rate", _speechRate)
      .putFloat("pitch", _pitch)
      .putString("voice", _voiceName)
      .putBoolean("allow_network_voices", _allowNetworkVoices)
      .apply();
  }

  private void createNotificationChannel()
  {
    if (Build.VERSION.SDK_INT < 26)
      return;
    NotificationManager manager =
      (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
    manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
        getString(R.string.reader_notification_channel),
        NotificationManager.IMPORTANCE_LOW));
  }

  private void createMediaSession()
  {
    _mediaSession = new MediaSession(this, "FrankenKeyReader");
    _mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS |
      MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
    _mediaSession.setCallback(new MediaSession.Callback()
    {
      @Override public void onPlay() { play(); }
      @Override public void onPause() { pause(false); }
      @Override public void onStop() { stopAndRemove(); }
      @Override public void onSkipToNext() { next(); }
      @Override public void onSkipToPrevious() { previous(); }
    }, _mainHandler);
    _mediaSession.setActive(true);
    updateMediaState();
  }

  private void publish()
  {
    updateMediaState();
    if (_foreground)
    {
      NotificationManager manager =
        (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
      manager.notify(NOTIFICATION_ID, buildNotification());
    }
    Snapshot snapshot = snapshot();
    for (Listener listener : new ArrayList<Listener>(_listeners))
      listener.onReaderPlaybackChanged(snapshot);
  }

  private void updateMediaState()
  {
    if (_mediaSession == null)
      return;
    int state;
    switch (_status)
    {
      case PLAYING: state = PlaybackState.STATE_PLAYING; break;
      case PREPARING: state = PlaybackState.STATE_BUFFERING; break;
      case PAUSED: state = PlaybackState.STATE_PAUSED; break;
      case ERROR: state = PlaybackState.STATE_ERROR; break;
      default: state = PlaybackState.STATE_STOPPED; break;
    }
    _mediaSession.setPlaybackState(new PlaybackState.Builder()
      .setActions(MEDIA_ACTIONS)
      .setState(state, _characterOffset,
          _status == Status.PLAYING ? _speechRate : 0f)
      .setErrorMessage(_status == Status.ERROR ? _error : null)
      .build());
  }

  private Notification buildNotification()
  {
    Notification.Builder builder = Build.VERSION.SDK_INT >= 26
      ? new Notification.Builder(this, CHANNEL_ID)
      : new Notification.Builder(this);
    boolean playing = _status == Status.PLAYING || _status == Status.PREPARING;
    PendingIntent previous = actionPendingIntent(ACTION_PREVIOUS, 1);
    PendingIntent toggle = actionPendingIntent(playing ? ACTION_PAUSE : ACTION_PLAY, 2);
    PendingIntent next = actionPendingIntent(ACTION_NEXT, 3);
    PendingIntent stop = actionPendingIntent(ACTION_STOP, 4);
    builder.setSmallIcon(R.drawable.ic_reader_notification)
      .setContentTitle(getString(R.string.reader_default_title))
      .setContentText(notificationStatus())
      .setOnlyAlertOnce(true)
      .setOngoing(playing)
      .setCategory(Notification.CATEGORY_TRANSPORT)
      .setVisibility(Notification.VISIBILITY_PRIVATE)
      .addAction(android.R.drawable.ic_media_previous,
          getString(R.string.reader_previous), previous)
      .addAction(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
          getString(playing ? R.string.reader_pause : R.string.reader_play), toggle)
      .addAction(android.R.drawable.ic_media_next,
          getString(R.string.reader_next), next)
      .addAction(android.R.drawable.ic_delete,
          getString(R.string.reader_stop), stop)
      .setStyle(new Notification.MediaStyle()
        .setMediaSession(_mediaSession.getSessionToken())
        .setShowActionsInCompactView(0, 1, 2));
    return builder.build();
  }

  private String notificationStatus()
  {
    switch (_status)
    {
      case PLAYING: return getString(R.string.reader_status_playing);
      case PREPARING: return getString(R.string.reader_status_preparing);
      case PAUSED: return getString(R.string.reader_status_paused);
      case ERROR: return _error;
      default: return getString(R.string.reader_status_stopped);
    }
  }

  private PendingIntent actionPendingIntent(String action, int requestCode)
  {
    int flags = PendingIntent.FLAG_UPDATE_CURRENT;
    if (Build.VERSION.SDK_INT >= 23)
      flags |= PendingIntent.FLAG_IMMUTABLE;
    return PendingIntent.getService(this, requestCode, serviceIntent(this, action), flags);
  }

  private void ensureForeground()
  {
    Notification notification = buildNotification();
    if (Build.VERSION.SDK_INT >= 29)
      startForeground(NOTIFICATION_ID, notification,
          ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
    else
      startForeground(NOTIFICATION_ID, notification);
    _foreground = true;
  }

  private void stopForegroundCompat()
  {
    if (!_foreground)
      return;
    if (Build.VERSION.SDK_INT >= 24)
      stopForeground(STOP_FOREGROUND_REMOVE);
    else
      stopForeground(true);
    _foreground = false;
  }

  private boolean requestAudioFocus()
  {
    if (_audioManager == null)
      return true;
    int result;
    if (Build.VERSION.SDK_INT >= 26)
    {
      if (_audioFocusRequest == null)
      {
        AudioAttributes attributes = new AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_MEDIA)
          .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
          .build();
        _audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
          .setAudioAttributes(attributes)
          .setOnAudioFocusChangeListener(_focusListener, _mainHandler)
          .setWillPauseWhenDucked(false)
          .build();
      }
      result = _audioManager.requestAudioFocus(_audioFocusRequest);
    }
    else
      result = _audioManager.requestAudioFocus(_focusListener,
          AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
    return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
  }

  private void abandonAudioFocus()
  {
    if (_audioManager == null)
      return;
    if (Build.VERSION.SDK_INT >= 26 && _audioFocusRequest != null)
      _audioManager.abandonAudioFocusRequest(_audioFocusRequest);
    else
      _audioManager.abandonAudioFocus(_focusListener);
  }

  private void onAudioFocusChanged(int change)
  {
    if (change == AudioManager.AUDIOFOCUS_GAIN)
    {
      if (_ducked)
      {
        _ducked = false;
        if (_status == Status.PLAYING)
        {
          _utteranceGeneration++;
          _tts.stop();
          speakCurrent();
        }
      }
      if (_resumeAfterFocusLoss)
      {
        _resumeAfterFocusLoss = false;
        play();
      }
    }
    else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
    {
      if (_status == Status.PLAYING)
      {
        _ducked = true;
        _utteranceGeneration++;
        _tts.stop();
        speakCurrent();
      }
    }
    else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
      pause(true);
    else if (change == AudioManager.AUDIOFOCUS_LOSS)
      pause(false);
  }

  private void registerNoisyReceiver()
  {
    IntentFilter filter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
    if (Build.VERSION.SDK_INT >= 33)
      registerReceiver(_noisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    else
      registerReceiver(_noisyReceiver, filter);
  }

  private int safeSpeechLimit()
  {
    return Math.max(1, Math.min(3800, TextToSpeech.getMaxSpeechInputLength()));
  }

  static String bounded(String value, int maximumLength)
  {
    if (value == null)
      return "";
    if (value.length() <= maximumLength)
      return value;
    int end = maximumLength;
    if (Character.isHighSurrogate(value.charAt(end - 1)) &&
        Character.isLowSurrogate(value.charAt(end)))
      end--;
    return value.substring(0, end);
  }

  private static float clamp(float value, float minimum, float maximum)
  {
    return Math.max(minimum, Math.min(maximum, value));
  }

  MediaSession mediaSessionForTest() { return _mediaSession; }
  TextToSpeech textToSpeechForTest() { return _tts; }
  int utteranceGenerationForTest() { return _utteranceGeneration; }

  @Override
  public void onDestroy()
  {
    _destroyed = true;
    stopSpeech(true);
    unregisterReceiver(_noisyReceiver);
    if (_tts != null)
    {
      _tts.shutdown();
      _tts = null;
    }
    _ttsReady = false;
    if (_mediaSession != null)
    {
      _mediaSession.setActive(false);
      _mediaSession.release();
    }
    stopForegroundCompat();
    _listeners.clear();
    super.onDestroy();
  }

  static final class ReaderChunkQueue
  {
    static final class Span
    {
      final int start;
      final int end;
      Span(int start, int end) { this.start = start; this.end = end; }
    }

    private final String _text;
    private final int _limit;
    private final int _capacity;
    private final ArrayDeque<Span> _queue = new ArrayDeque<Span>();
    private int _nextStart;

    ReaderChunkQueue(String text, int limit, int capacity, int offset)
    {
      _text = text;
      _limit = Math.max(1, limit);
      _capacity = Math.max(1, capacity);
      seek(offset);
    }

    Span current()
    {
      fill();
      return _queue.peekFirst();
    }

    Span advance()
    {
      fill();
      if (!_queue.isEmpty())
        _queue.removeFirst();
      fill();
      return _queue.peekFirst();
    }

    int movePrevious()
    {
      Span current = current();
      int currentStart = current == null ? _text.length() : current.start;
      int previousStart = 0;
      int cursor = 0;
      while (cursor < currentStart)
      {
        int end = nextBoundary(_text, cursor, _limit);
        if (end >= currentStart)
          break;
        previousStart = cursor;
        cursor = end;
      }
      seek(previousStart);
      return previousStart;
    }

    void seek(int offset)
    {
      _queue.clear();
      _nextStart = Math.max(0, Math.min(offset, _text.length()));
      if (_nextStart > 0 && _nextStart < _text.length() &&
          Character.isLowSurrogate(_text.charAt(_nextStart)) &&
          Character.isHighSurrogate(_text.charAt(_nextStart - 1)))
        _nextStart--;
      fill();
    }

    int queuedCount() { return _queue.size(); }

    private void fill()
    {
      while (_queue.size() < _capacity && _nextStart < _text.length())
      {
        int end = nextBoundary(_text, _nextStart, _limit);
        _queue.addLast(new Span(_nextStart, end));
        _nextStart = end;
      }
    }

    static int nextBoundary(String text, int start, int limit)
    {
      int end = Math.min(text.length(), start + Math.max(1, limit));
      if (end < text.length() && end > start &&
          Character.isHighSurrogate(text.charAt(end - 1)) &&
          Character.isLowSurrogate(text.charAt(end)))
        end--;
      if (end >= text.length())
        return text.length();
      int minimum = start + Math.max(1, limit / 2);
      for (int i = end - 1; i >= minimum; i--)
      {
        char value = text.charAt(i);
        if (Character.isWhitespace(value) || value == '.' || value == '!' ||
            value == '?' || value == ';' || value == ':' || value == ',' ||
            value == '\u3002' || value == '\uff01' || value == '\uff1f')
          return i + 1;
      }
      return end;
    }
  }
}
