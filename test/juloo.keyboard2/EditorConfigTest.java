package juloo.keyboard2;

import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import org.junit.Test;
import static org.junit.Assert.*;

public class EditorConfigTest
{
  public EditorConfigTest() {}

  @Test
  public void should_show_snippet_row_rejects_password_editors()
  {
    int[] passwordInputTypes = {
      InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
      InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD |
          InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE,
      InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
      InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
      InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD,
    };

    for (int inputType : passwordInputTypes)
      assertFalse("Snippet row must stay hidden for password editor inputType " + inputType,
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

  private static EditorInfo editor(int inputType)
  {
    EditorInfo info = new EditorInfo();
    info.inputType = inputType;
    return info;
  }
}
