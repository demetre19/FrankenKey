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
  public void typing_assistance_only_runs_in_safe_text_editors()
  {
    int[] safeInputTypes = {
      InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL,
      InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE,
      InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE,
      InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME,
      InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT,
    };
    for (int inputType : safeInputTypes)
      assertTrue("Typing assistance should be enabled for safe editor inputType " + inputType,
          EditorConfig.should_use_typing_assistance(editor(inputType)));

    int[] unsafeInputTypes = {
      InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI,
      InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
      InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
      InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
      InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
      InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
      InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
      InputType.TYPE_CLASS_NUMBER,
      InputType.TYPE_CLASS_PHONE,
      InputType.TYPE_NULL,
    };
    for (int inputType : unsafeInputTypes)
      assertFalse("Typing assistance must stay disabled for unsafe editor inputType " + inputType,
          EditorConfig.should_use_typing_assistance(editor(inputType)));
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

  @Test
  public void caps_mode_fallback_and_standalone_i_follow_editor_policy()
  {
    EditorConfig plainText = new EditorConfig();
    plainText.refresh(editor(InputType.TYPE_CLASS_TEXT), null);

    assertEquals("Plain text editors that omit caps flags must receive sentence capitalization repair.",
        TextUtils.CAP_MODE_SENTENCES, plainText.caps_mode);
    assertTrue("Plain text editors must allow standalone i capitalization.",
        plainText.autocapitalise_standalone_i);

    EditorConfig uri = new EditorConfig();
    uri.refresh(editor(InputType.TYPE_CLASS_TEXT
        | InputType.TYPE_TEXT_VARIATION_URI), null);

    assertEquals("URI editors must not receive the generic sentence-capitalization fallback.",
        0, uri.caps_mode);
    assertTrue("URI editors are not password editors and must still allow standalone i capitalization.",
        uri.autocapitalise_standalone_i);

    EditorConfig webEdit = new EditorConfig();
    webEdit.refresh(editor(InputType.TYPE_CLASS_TEXT
        | InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT), null);

    assertEquals("Web-edit text editors must not receive the generic sentence-capitalization fallback.",
        0, webEdit.caps_mode);

    EditorConfig email = new EditorConfig();
    email.refresh(editor(InputType.TYPE_CLASS_TEXT
        | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS), null);

    assertEquals("Email-address editors must not receive the generic sentence-capitalization fallback.",
        0, email.caps_mode);

    EditorConfig password = new EditorConfig();
    password.refresh(editor(InputType.TYPE_CLASS_TEXT
        | InputType.TYPE_TEXT_VARIATION_PASSWORD), null);

    assertEquals("Password editors must not receive the generic sentence-capitalization fallback.",
        0, password.caps_mode);
    assertFalse("Password editors must not rewrite a user's standalone lowercase i.",
        password.autocapitalise_standalone_i);
  }

  private static EditorInfo editor(int inputType)
  {
    EditorInfo info = new EditorInfo();
    info.inputType = inputType;
    info.packageName = "dev.frankenkey.keyboard.test";
    return info;
  }
}
