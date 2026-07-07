package juloo.keyboard2;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class AutocapitalisationTest
{
  @Test
  public void character_caps_mode_stays_enabled_after_typing_a_character()
  {
    RecordingCallback callback = new RecordingCallback();
    Autocapitalisation autocap = autocap(callback,
        TextUtils.CAP_MODE_CHARACTERS, TextUtils.CAP_MODE_CHARACTERS);

    autocap.type_one_char('a');
    autocap.delayed_callback.run();

    assertTrue("All-caps fields must keep shift enabled after every character.",
        callback.shouldEnable);
  }

  @Test
  public void sentence_caps_rechecks_after_sentence_punctuation_and_whitespace()
  {
    char[] triggers = new char[] {'\n', ' ', '.', '!', '?'};
    String[] names = new String[] {
      "newline", "plain space", "full stop", "exclamation mark",
      "question mark"
    };

    for (int i = 0; i < triggers.length; ++i)
    {
      RecordingCallback callback = new RecordingCallback();
      Autocapitalisation autocap = autocap(callback,
          TextUtils.CAP_MODE_SENTENCES, TextUtils.CAP_MODE_SENTENCES);

      autocap.type_one_char(triggers[i]);
      autocap.delayed_callback.run();

      assertTrue("Sentence caps must update after " + names[i] + ".",
          callback.shouldEnable);
    }
  }

  private static Autocapitalisation autocap(RecordingCallback callback,
      int requestedMode, int cursorMode)
  {
    Autocapitalisation autocap = new Autocapitalisation(
        new Handler(Looper.getMainLooper()), callback);
    autocap._enabled = true;
    autocap._caps_mode = requestedMode;
    autocap._ic = new CapsInputConnection(cursorMode);
    return autocap;
  }

  private static class RecordingCallback implements Autocapitalisation.Callback
  {
    boolean shouldEnable = false;

    @Override
    public void update_shift_state(boolean should_enable, boolean should_disable)
    {
      shouldEnable = should_enable;
    }
  }

  private static class CapsInputConnection extends BaseInputConnection
  {
    private final int _cursorMode;

    CapsInputConnection(int cursorMode)
    {
      super(new View(RuntimeEnvironment.getApplication()), false);
      _cursorMode = cursorMode;
    }

    @Override
    public int getCursorCapsMode(int reqModes)
    {
      return _cursorMode & reqModes;
    }
  }
}
