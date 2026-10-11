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
  private static final String CF_KEY_ALIAS = "frankenkey_reader_cloudflare_key";
  private static final String API_KEY_CIPHERTEXT = "openrouter_key_ciphertext";
  private static final String API_KEY_IV = "openrouter_key_iv";
  private static final String MODEL_ID = "openrouter_model_id";
  private static final String SUMMARY_ONE = "summary_one_prompt";
  private static final String SUMMARY_TWO = "summary_two_prompt";
  private static final String QUIZ = "quiz_prompt";
  private static final String DISCLOSURE_ACCEPTED = "disclosure_accepted_v3";
  private static final String PROVIDER = "ai_provider";
  private static final String CF_ACCOUNT_ID = "cloudflare_account_id";
  private static final String CF_TOKEN_CIPHERTEXT =
    "cloudflare_token_ciphertext";
  private static final String CF_TOKEN_IV = "cloudflare_token_iv";
  private static final String CF_MODEL_ID = "cloudflare_model_id";

  /** Selectable AI backends; Cloudflare is the default provider. */
  static final class Provider
  {
    static final String CLOUDFLARE = "cloudflare";
    static final String OPENROUTER = "openrouter";

    private Provider() {}

    static String normalize(String value)
    {
      return OPENROUTER.equals(value) ? OPENROUTER : CLOUDFLARE;
    }

    static String label(String value)
    {
      return OPENROUTER.equals(normalize(value))
        ? "OpenRouter" : "Cloudflare Workers AI";
    }
  }

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
    return getSecret(KEY_ALIAS, API_KEY_CIPHERTEXT, API_KEY_IV);
  }

  synchronized void setApiKey(String apiKey) throws GeneralSecurityException
  {
    setSecret(KEY_ALIAS, API_KEY_CIPHERTEXT, API_KEY_IV, apiKey,
        "OpenRouter API key");
  }

  String getProvider()
  {
    return Provider.normalize(preferences.getString(PROVIDER,
        Provider.CLOUDFLARE));
  }

  void setProvider(String provider)
  {
    preferences.edit().putString(PROVIDER,
        Provider.normalize(provider)).apply();
  }

  String getCloudflareAccountId()
  {
    String value = preferences.getString(CF_ACCOUNT_ID, "");
    return value == null ? "" : value.trim();
  }

  void setCloudflareAccountId(String accountId)
  {
    preferences.edit().putString(CF_ACCOUNT_ID,
        accountId == null ? "" : accountId.trim()).apply();
  }

  synchronized String getCloudflareApiToken() throws GeneralSecurityException
  {
    return getSecret(CF_KEY_ALIAS, CF_TOKEN_CIPHERTEXT, CF_TOKEN_IV);
  }

  synchronized void setCloudflareApiToken(String apiToken)
      throws GeneralSecurityException
  {
    setSecret(CF_KEY_ALIAS, CF_TOKEN_CIPHERTEXT, CF_TOKEN_IV, apiToken,
        "Cloudflare API token");
  }

  String getCloudflareModelId()
  {
    String value = preferences.getString(CF_MODEL_ID,
        ReaderAiCloudflare.PREFERRED_MODEL_ID);
    return value == null || value.trim().isEmpty()
      ? ReaderAiCloudflare.PREFERRED_MODEL_ID : value.trim();
  }

  void setCloudflareModelId(String modelId)
  {
    preferences.edit().putString(CF_MODEL_ID,
        modelId == null ? "" : modelId.trim()).apply();
  }

  /** Model id used by the selected provider. */
  String getActiveModelId()
  {
    return Provider.OPENROUTER.equals(getProvider())
      ? getModelId() : getCloudflareModelId();
  }

  private synchronized String getSecret(String alias, String ciphertextKey,
      String ivKey) throws GeneralSecurityException
  {
    requireSecureKeystore();
    String ciphertext = secrets.getString(ciphertextKey, "");
    String encodedIv = secrets.getString(ivKey, "");
    if (ciphertext == null || ciphertext.isEmpty()
        || encodedIv == null || encodedIv.isEmpty())
      return "";
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    byte[] iv = Base64.decode(encodedIv, Base64.NO_WRAP);
    cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(alias),
        new GCMParameterSpec(128, iv));
    byte[] plaintext = cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP));
    return new String(plaintext, StandardCharsets.UTF_8);
  }

  private synchronized void setSecret(String alias, String ciphertextKey,
      String ivKey, String value, String label)
      throws GeneralSecurityException
  {
    requireSecureKeystore();
    String normalized = value == null ? "" : value.trim();
    if (normalized.isEmpty())
    {
      secrets.edit().remove(ciphertextKey).remove(ivKey).commit();
      return;
    }
    if (normalized.length() > 1000)
      throw new GeneralSecurityException(label + " is too long");
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey(alias));
    byte[] ciphertext = cipher.doFinal(
        normalized.getBytes(StandardCharsets.UTF_8));
    if (!secrets.edit().putString(ciphertextKey,
          Base64.encodeToString(ciphertext, Base64.NO_WRAP))
        .putString(ivKey,
          Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)).commit())
      throw new GeneralSecurityException("Could not save " + label);
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

  private SecretKey getOrCreateSecretKey(String alias)
      throws GeneralSecurityException
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
    KeyStore.Entry existing = keyStore.getEntry(alias, null);
    if (existing instanceof KeyStore.SecretKeyEntry)
      return ((KeyStore.SecretKeyEntry)existing).getSecretKey();

    KeyGenerator generator = KeyGenerator.getInstance(
        KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
    generator.init(new KeyGenParameterSpec.Builder(alias,
          KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setRandomizedEncryptionRequired(true)
        .build());
    return generator.generateKey();
  }
}
