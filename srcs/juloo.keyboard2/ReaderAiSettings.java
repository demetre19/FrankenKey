package juloo.keyboard2;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Reader AI preferences. Secret material is isolated for backup exclusion. */
final class ReaderAiSettings
{
  static final String PREFERENCES = "reader_ai_settings";
  static final String SECRET_PREFERENCES = "reader_ai_secret";
  private static final String KEY_ALIAS = "frankenkey_reader_openrouter_key";
  private static final String API_KEY_CIPHERTEXT = "openrouter_key_ciphertext";
  private static final String API_KEY_IV = "openrouter_key_iv";
  private static final String MODEL_ID = "openrouter_model_id";
  private static final String SUMMARY_ONE = "summary_one_prompt";
  private static final String SUMMARY_TWO = "summary_two_prompt";
  private static final String QUIZ = "quiz_prompt";
  private static final String DISCLOSURE_ACCEPTED = "disclosure_accepted_v2";
  private static final int MAX_PROMPT_LENGTH = 20_000;

  private final SharedPreferences preferences;
  private final SharedPreferences secrets;

  ReaderAiSettings(Context context)
  {
    Context app = context.getApplicationContext();
    preferences = app.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    secrets = app.getSharedPreferences(SECRET_PREFERENCES, Context.MODE_PRIVATE);
  }

  synchronized String getApiKey() throws GeneralSecurityException
  {
    requireSecureKeystore();
    String ciphertext = secrets.getString(API_KEY_CIPHERTEXT, "");
    String encodedIv = secrets.getString(API_KEY_IV, "");
    if (ciphertext == null || ciphertext.isEmpty()
        || encodedIv == null || encodedIv.isEmpty())
      return "";
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    byte[] iv = Base64.decode(encodedIv, Base64.NO_WRAP);
    cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(),
        new GCMParameterSpec(128, iv));
    byte[] plaintext = cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP));
    return new String(plaintext, StandardCharsets.UTF_8);
  }

  synchronized void setApiKey(String apiKey) throws GeneralSecurityException
  {
    requireSecureKeystore();
    String normalized = apiKey == null ? "" : apiKey.trim();
    if (normalized.isEmpty())
    {
      secrets.edit().remove(API_KEY_CIPHERTEXT).remove(API_KEY_IV).commit();
      return;
    }
    if (normalized.length() > 1000)
      throw new GeneralSecurityException("OpenRouter API key is too long");
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey());
    byte[] ciphertext = cipher.doFinal(
        normalized.getBytes(StandardCharsets.UTF_8));
    if (!secrets.edit().putString(API_KEY_CIPHERTEXT,
          Base64.encodeToString(ciphertext, Base64.NO_WRAP))
        .putString(API_KEY_IV,
          Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)).commit())
      throw new GeneralSecurityException("Could not save OpenRouter API key");
  }

  String getModelId()
  {
    String value = preferences.getString(MODEL_ID,
        ReaderAiOpenRouter.PREFERRED_MODEL_ID);
    return value == null ? ReaderAiOpenRouter.PREFERRED_MODEL_ID : value.trim();
  }

  void setModelId(String modelId)
  {
    preferences.edit().putString(MODEL_ID,
        modelId == null ? "" : modelId.trim()).apply();
  }

  String getSummaryOnePrompt()
  {
    return prompt(SUMMARY_ONE, ReaderAiRequest.SUMMARY_ONE_PROMPT);
  }

  String getSummaryTwoPrompt()
  {
    return prompt(SUMMARY_TWO, ReaderAiRequest.SUMMARY_TWO_PROMPT);
  }

  String getQuizPrompt()
  {
    return prompt(QUIZ, ReaderAiRequest.QUIZ_PROMPT);
  }

  void setPrompts(String summaryOne, String summaryTwo, String quiz)
  {
    preferences.edit()
      .putString(SUMMARY_ONE, validatedPrompt(summaryOne))
      .putString(SUMMARY_TWO, validatedPrompt(summaryTwo))
      .putString(QUIZ, validatedPrompt(quiz))
      .apply();
  }

  void restoreDefaultPrompts()
  {
    preferences.edit().remove(SUMMARY_ONE).remove(SUMMARY_TWO).remove(QUIZ)
      .apply();
  }

  boolean isDisclosureAccepted()
  {
    return preferences.getBoolean(DISCLOSURE_ACCEPTED, false);
  }

  void setDisclosureAccepted(boolean accepted)
  {
    preferences.edit().putBoolean(DISCLOSURE_ACCEPTED, accepted).apply();
  }

  private String prompt(String key, String fallback)
  {
    String value = preferences.getString(key, "");
    return value == null || value.trim().isEmpty() ? fallback : value;
  }

  private static String validatedPrompt(String value)
  {
    String normalized = value == null ? "" : value.trim();
    if (normalized.isEmpty())
      throw new IllegalArgumentException("AI prompts cannot be empty");
    if (normalized.length() > MAX_PROMPT_LENGTH)
      throw new IllegalArgumentException("AI prompt is too long");
    return normalized;
  }

  private static void requireSecureKeystore() throws GeneralSecurityException
  {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
      throw new GeneralSecurityException(
          "Reader AI requires Android 6 or newer for secure key storage");
  }

  private SecretKey getOrCreateSecretKey() throws GeneralSecurityException
  {
    KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
    try
    {
      keyStore.load(null);
    }
    catch (java.io.IOException error)
    {
      throw new GeneralSecurityException("Could not load Android Keystore", error);
    }
    KeyStore.Entry existing = keyStore.getEntry(KEY_ALIAS, null);
    if (existing instanceof KeyStore.SecretKeyEntry)
      return ((KeyStore.SecretKeyEntry)existing).getSecretKey();

    KeyGenerator generator = KeyGenerator.getInstance(
        KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
    generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
          KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setRandomizedEncryptionRequired(true)
        .build());
    return generator.generateKey();
  }
}
