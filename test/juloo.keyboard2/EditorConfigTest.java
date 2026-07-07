package juloo.keyboard2;

import android.text.TextUtils;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import org.junit.Test;
import static org.junit.Assert.*;

public class EditorConfigTest
{
  public EditorConfigTest() {}

  @Test
  public void should_show_snippet_row_allows_password_editors()
  {
    int[] passwordInputTypes = {
      InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
      InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
      InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
      InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD,
    };

    for (int inputType : passwordInputTypes)
      assertTrue("Snippet row must remain available for password editor inputType " + inputType,
          EditorConfig.should_show_snippet_row(editor(inputType)));
  }

  @Test
  public void should_show_snippet_row_allows_non_password_editors()
  {
    int[] ordinaryInputTypes = {
      InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
      InputType.TYPE_CLASS_NUMBER,
      InputType.TYPE_CLASS_PHONE,
      InputType.TYPE_CLASS_DATETIME,
    };

    for (int inputType : ordinaryInputTypes)
      assertTrue("Snippet row should remain available for non-password editor inputType " + inputType,
          EditorConfig.should_show_snippet_row(editor(inputType)));
  }

  @Test
  public void caps_mode_keeps_all_editor_requested_capitalisation_modes()
  {
    EditorConfig config = new EditorConfig();
    EditorInfo info = editor(InputType.TYPE_CLASS_TEXT
        | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        | InputType.TYPE_TEXT_FLAG_CAP_WORDS
        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);

    config.refresh(info, null);

    assertEquals(TextUtils.CAP_MODE_CHARACTERS | TextUtils.CAP_MODE_WORDS
        | TextUtils.CAP_MODE_SENTENCES, config.caps_mode);
  }

  private static EditorInfo editor(int inputType)
  {
    EditorInfo info = new EditorInfo();
    info.inputType = inputType;
    info.packageName = "dev.frankenkey.keyboard.test";
    return info;
  }
}
