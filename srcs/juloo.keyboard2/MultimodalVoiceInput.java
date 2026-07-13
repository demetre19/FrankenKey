package juloo.keyboard2;

import android.content.Context;
import android.content.Intent;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.util.ArrayList;
import java.util.Locale;

/** In-process dictation that leaves FrankenKey's typing layout visible. */
public final class MultimodalVoiceInput implements RecognitionListener,
    AutoCloseable
{
  static final long RESTART_DELAY_MS = 600;
  static final int MAX_TRANSIENT_RETRIES = 3;

  public interface Callback
  {
    void on_listening(String partialText);
    void on_text(String text);
    void on_stopped(int errorCode);
  }

  private final Context _context;
  private final Handler _handler;
  private final Callback _callback;
  private SpeechRecognizer _recognizer;
  private Intent _intent;
  private boolean _active;
  private boolean _restartScheduled;
  private int _transientRetries;

  public MultimodalVoiceInput(Context context, Handler handler,
      Callback callback)
  {
    _context = context;
    _handler = handler;
    _callback = callback;
  }

  public static boolean is_available(Context context)
  {
    return SpeechRecognizer.isRecognitionAvailable(context)
      || (VERSION.SDK_INT >= 31
        && SpeechRecognizer.isOnDeviceRecognitionAvailable(context));
  }

  public boolean start(Locale locale)
  {
    stop(false, SpeechRecognizer.ERROR_CLIENT);
    if (!is_available(_context))
      return false;
    try
    {
      _recognizer = VERSION.SDK_INT >= 31
        && SpeechRecognizer.isOnDeviceRecognitionAvailable(_context)
        ? SpeechRecognizer.createOnDeviceSpeechRecognizer(_context)
        : SpeechRecognizer.createSpeechRecognizer(_context);
      _recognizer.setRecognitionListener(this);
      _intent = recognition_intent(locale);
      _active = true;
      _transientRetries = 0;
      _recognizer.startListening(_intent);
      _callback.on_listening("");
      return true;
    }
    catch (RuntimeException _error)
    {
      stop(false, SpeechRecognizer.ERROR_CLIENT);
      return false;
    }
  }

  static Intent recognition_intent(Locale locale)
  {
    Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
    intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
    intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
    if (locale != null)
    {
      intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag());
      intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
          locale.toLanguageTag());
    }
    intent.putExtra(
        RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
        600000L);
    intent.putExtra(
        RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
        600000L);
    if (VERSION.SDK_INT >= 33)
      intent.putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION,
          RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS);
    return intent;
  }

  public boolean is_active()
  {
    return _active;
  }

  public void stop()
  {
    stop(true, 0);
  }

  private void stop(boolean notify, int errorCode)
  {
    _active = false;
    _restartScheduled = false;
    if (_recognizer != null)
    {
      try
      {
        _recognizer.cancel();
        _recognizer.destroy();
      }
      catch (IllegalArgumentException _ignored) {}
      _recognizer = null;
    }
    if (notify)
      _callback.on_stopped(errorCode);
  }

  private void restart()
  {
    if (!_active || _recognizer == null || _restartScheduled)
      return;
    _restartScheduled = true;
    _handler.postDelayed(() -> {
        _restartScheduled = false;
        if (_active && _recognizer != null)
          _recognizer.startListening(_intent);
      }, RESTART_DELAY_MS);
  }

  private static String first_result(Bundle results)
  {
    if (results == null)
      return null;
    ArrayList<String> values =
      results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
    if (values == null || values.isEmpty())
      return null;
    String result = values.get(0);
    return result == null || result.trim().length() == 0
      ? null : result.trim();
  }

  static String text_to_commit(CharSequence beforeCursor, String recognized)
  {
    if (recognized == null)
      return "";
    String text = recognized.trim();
    if (text.length() == 0 || beforeCursor == null
        || beforeCursor.length() == 0)
      return text;
    char before = beforeCursor.charAt(beforeCursor.length() - 1);
    char first = text.charAt(0);
    if (Character.isLetterOrDigit(before)
        && Character.isLetterOrDigit(first))
      return " " + text;
    return text;
  }

  private void publish_final(Bundle results, boolean restart)
  {
    String result = first_result(results);
    if (result != null)
    {
      _transientRetries = 0;
      _callback.on_text(result);
    }
    if (restart)
      restart();
  }

  @Override public void onReadyForSpeech(Bundle params)
  {
    _transientRetries = 0;
    _callback.on_listening("");
  }

  @Override public void onBeginningOfSpeech() {}
  @Override public void onRmsChanged(float rmsdB) {}
  @Override public void onBufferReceived(byte[] buffer) {}
  @Override public void onEndOfSpeech() {}

  @Override
  public void onError(int error)
  {
    if (!_active)
      return;
    if ((error == SpeechRecognizer.ERROR_NO_MATCH
        || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
        || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
        || error == SpeechRecognizer.ERROR_CLIENT)
        && _transientRetries++ < MAX_TRANSIENT_RETRIES)
    {
      restart();
      return;
    }
    stop(true, error);
  }

  @Override
  public void onResults(Bundle results)
  {
    publish_final(results, true);
  }

  @Override
  public void onPartialResults(Bundle partialResults)
  {
    String result = first_result(partialResults);
    if (result != null)
      _callback.on_listening(result);
  }

  @Override public void onEvent(int eventType, Bundle params) {}

  @Override
  public void onSegmentResults(Bundle segmentResults)
  {
    publish_final(segmentResults, false);
  }

  @Override
  public void onEndOfSegmentedSession()
  {
    restart();
  }

  @Override
  public void close()
  {
    stop(false, 0);
  }
}
