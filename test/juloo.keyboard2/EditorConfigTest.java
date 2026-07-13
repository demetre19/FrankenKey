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
  public void typing_assistance_runs_in_every_safe_text_editor()
  {
    int[] safeInputTypes = {
      InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL,
      InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE,
      InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE,
      InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME,
      InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS,
      InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT,
      InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT,
      InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_FILTER,
      InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PHONETIC,
      InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
      InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI,
      InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
      InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
    };
    for (int inputType : safeInputTypes)
      assertTrue("Non-secret text fields must keep autocorrect even when apps suppress suggestions or use structured variations: " + inputType,
          EditorConfig.should_use_typing_assistance(editor(inputType)));

    int[] unsafeInputTypes = {
      InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
      InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
      InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
      InputType.TYPE_CLASS_NUMBER,
      InputType.TYPE_CLASS_PHONE,
      InputType.TYPE_NULL,
    };
    for (int inputType : unsafeInputTypes)
      assertFalse("Credentials and non-text fields must not be rewritten: " + inputType,
          EditorConfig.should_use_typing_assistance(editor(inputType)));
  }

  @Test
  public void no_personalized_learning_keeps_correction_but_disables_learning()
  {
    EditorInfo info = editor(InputType.TYPE_CLASS_TEXT
        | InputType.TYPE_TEXT_VARIATION_NORMAL
        | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    info.imeOptions |= EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
    EditorConfig config = new EditorConfig();

    config.refresh(info, null);

    assertTrue("An editor may suppress its own suggestions without disabling FrankenKey correction.",
        config.should_use_typing_assistance);
    assertTrue("Safe fields must show FrankenKey candidates consistently with the correction backend.",
        config.should_show_candidates_view);
    assertFalse("Fields that prohibit personalized learning must never read or write learned text.",
        config.should_use_personalization);
  }

  @Test
  public void structured_address_fields_correct_without_learning_or_sentence_tools()
  {
    int[] structuredInputTypes = {
      InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI,
      InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
      InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
    };
    for (int inputType : structuredInputTypes)
    {
      EditorConfig config = new EditorConfig();
      config.refresh(editor(inputType), null);

      assertTrue("URL and address fields must show suggestions like SwiftKey.",
          config.should_show_candidates_view);
      assertTrue("URL and address fields must run autocorrect like SwiftKey.",
          config.should_use_typing_assistance);
      assertFalse("URLs and email addresses must not enter persistent adaptive learning.",
          config.should_use_personalization);
      assertFalse("Structured fields must not run sentence grammar or multimodal dictation.",
          config.should_use_sentence_assistance);
    }
  }

  @Test
  public void google_search_and_browser_omnibox_keep_candidates_and_autocorrect()
  {
    EditorInfo googleSearch = editor(InputType.TYPE_CLASS_TEXT
        | InputType.TYPE_TEXT_VARIATION_NORMAL
        | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    googleSearch.packageName = "com.google.android.googlequicksearchbox";
    EditorConfig googleConfig = new EditorConfig();
    googleConfig.refresh(googleSearch, null);

    assertTrue("Google search must show FrankenKey candidates even when the app suppresses suggestions.",
        googleConfig.should_show_candidates_view);
    assertTrue("Google search must run the same autocorrect path as normal text.",
        googleConfig.should_use_typing_assistance);

    EditorInfo omnibox = editor(InputType.TYPE_CLASS_TEXT
        | InputType.TYPE_TEXT_VARIATION_URI
        | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    omnibox.packageName = "com.android.chrome";
    omnibox.imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
    EditorConfig omniboxConfig = new EditorConfig();
    omniboxConfig.refresh(omnibox, null);

    assertTrue("Browser URL/search bars must show FrankenKey candidates.",
        omniboxConfig.should_show_candidates_view);
    assertTrue("Browser URL/search bars must run FrankenKey autocorrect.",
        omniboxConfig.should_use_typing_assistance);
    assertFalse("Browser URL/search text must never enter persistent learning.",
        omniboxConfig.should_use_personalization);
  }

  @Test
  public void termux_raw_editor_allows_stateless_typing_assistance()
  {
    EditorInfo termux = editor(InputType.TYPE_NULL);
    termux.packageName = "com.termux";
    EditorConfig config = new EditorConfig();

    config.refresh(termux, null);

    assertTrue("Termux TYPE_NULL editors must show dictionary suggestions.",
        config.should_show_candidates_view);
    assertTrue("Termux TYPE_NULL editors must allow suggestions and autocorrect.",
        config.should_use_typing_assistance);
    assertFalse("Terminal input can contain hidden passwords, so it must not use persistent personalization.",
        config.should_use_personalization);
    assertTrue("The official Termux package must use raw key events for Backspace.",
        EditorConfig.is_termux_raw_editor(termux));

    EditorInfo otherRawEditor = editor(InputType.TYPE_NULL);
    assertFalse("Unknown TYPE_NULL editors must keep the conservative typing-assistance policy.",
        EditorConfig.should_use_typing_assistance(otherRawEditor));
    assertFalse("Raw-event behavior must be scoped to the official Termux package.",
        EditorConfig.is_termux_raw_editor(otherRawEditor));
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
