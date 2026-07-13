package juloo.keyboard2;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.util.Locale;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
public class MultimodalVoiceInputTest
{
  @Test
  public void dictation_preserves_word_boundaries_without_doubling_spaces()
  {
    assertEquals(" world",
        MultimodalVoiceInput.text_to_commit("hello", "world"));
    assertEquals("world",
        MultimodalVoiceInput.text_to_commit("hello ", " world "));
    assertEquals(", world",
        MultimodalVoiceInput.text_to_commit("hello", ", world"));
    assertEquals("hello",
        MultimodalVoiceInput.text_to_commit(null, " hello "));
  }

  @Test
  public void recognition_session_is_free_form_partial_and_long_lived()
  {
    Intent intent = MultimodalVoiceInput.recognition_intent(Locale.US);

    assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH,
        intent.getAction());
    assertEquals(RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
        intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL));
    assertEquals(1,
        intent.getIntExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 0));
    assertTrue(intent.getBooleanExtra(
          RecognizerIntent.EXTRA_PARTIAL_RESULTS, false));
    assertEquals("en-US",
        intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE));
    assertEquals(600000L, intent.getLongExtra(
          RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
          0));
  }
  @Test
  public void partial_and_final_results_keep_the_in_process_callback_path()
  {
    String[] listening = new String[1];
    String[] committed = new String[1];
    MultimodalVoiceInput input = new MultimodalVoiceInput(
        RuntimeEnvironment.getApplication(),
        new Handler(RuntimeEnvironment.getApplication().getMainLooper()),
        new MultimodalVoiceInput.Callback()
        {
          @Override public void on_listening(String text)
          {
            listening[0] = text;
          }

          @Override public void on_text(String text)
          {
            committed[0] = text;
          }

          @Override public void on_stopped(int errorCode) {}
        });
    Bundle result = new Bundle();
    ArrayList<String> values = new ArrayList<>();
    values.add("dictated text");
    result.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, values);

    input.onPartialResults(result);
    assertEquals("dictated text", listening[0]);
    input.onResults(result);
    assertEquals("dictated text", committed[0]);
    input.close();
  }

}
