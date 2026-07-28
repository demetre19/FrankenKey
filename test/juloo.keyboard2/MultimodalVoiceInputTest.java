package juloo.keyboard2;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.Editable;
import android.text.Selection;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
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
  public void voice_commands_require_explicit_prefix_for_editor_mutations()
  {
    assertEquals("A normally dictated command word must remain text.",
        MultimodalVoiceInput.VoiceAction.DICTATE,
        MultimodalVoiceInput.parse_command("send").action);
    assertEquals(MultimodalVoiceInput.VoiceAction.SEND,
        MultimodalVoiceInput.parse_command("command send").action);
    assertEquals(MultimodalVoiceInput.VoiceAction.DELETE_LAST_WORD,
        MultimodalVoiceInput.parse_command(
          "command delete last word").action);
    assertEquals("Safe structural dictation may remain concise.",
        MultimodalVoiceInput.VoiceAction.NEW_PARAGRAPH,
        MultimodalVoiceInput.parse_command("new paragraph").action);
  }

  @Test
  public void structured_voice_commands_preserve_user_text()
  {
    MultimodalVoiceInput.VoiceCommand replace =
      MultimodalVoiceInput.parse_command(
          "command replace blue car with red car");
    assertEquals(MultimodalVoiceInput.VoiceAction.REPLACE, replace.action);
    assertEquals("blue car", replace.first);
    assertEquals("red car", replace.second);

    MultimodalVoiceInput.VoiceCommand insert =
      MultimodalVoiceInput.parse_command(
          "command insert quickly after move");
    assertEquals(MultimodalVoiceInput.VoiceAction.INSERT_AFTER, insert.action);
    assertEquals("quickly", insert.first);
    assertEquals("move", insert.second);

    MultimodalVoiceInput.VoiceCommand escaped =
      MultimodalVoiceInput.parse_command("command type send");
    assertEquals(MultimodalVoiceInput.VoiceAction.DICTATE, escaped.action);
    assertEquals("send", escaped.first);
  }

  @Test
  public void spoken_punctuation_is_formatted_locally()
  {
    assertEquals("Hello, world. Really?",
        MultimodalVoiceInput.format_dictation(
          "Hello comma world period Really question mark"));
  }

  @Test
  public void structured_commands_apply_verified_recent_editor_edits()
  {
    BaseInputConnection replace = editor("blue car drives");
    Keyboard2.replace_recent_voice_text(replace, "blue car", "red car",
        false);
    assertEquals("red car drives", replace.getEditable().toString());

    BaseInputConnection insert = editor("move now");
    Keyboard2.replace_recent_voice_text(insert, "move", "quickly", true);
    assertEquals("move quickly now", insert.getEditable().toString());

    BaseInputConnection capitalize = editor("hello world ");
    Keyboard2.capitalize_last_voice_word(capitalize);
    assertEquals("hello World ", capitalize.getEditable().toString());

    BaseInputConnection delete = editor("hello world ");
    Keyboard2.delete_last_voice_word(delete);
    assertEquals("hello ", delete.getEditable().toString());
  }

  private static BaseInputConnection editor(String text)
  {
    BaseInputConnection connection = new BaseInputConnection(
        new View(RuntimeEnvironment.getApplication()), true);
    Editable editable = connection.getEditable();
    editable.append(text);
    Selection.setSelection(editable, editable.length());
    return connection;
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
    assertEquals(RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY,
        intent.getStringExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING));
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
