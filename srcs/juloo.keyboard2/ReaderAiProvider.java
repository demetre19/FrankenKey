package juloo.keyboard2;

import org.json.JSONException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * Routes Reader AI traffic through the selected provider. Cloudflare
 * Workers AI is the default; OpenRouter stays available as a selectable
 * fallback. Credential reads happen off the IME main thread — callers
 * invoke this from a worker executor.
 */
final class ReaderAiProvider
{
  /** Resolved backend for one generation attempt. */
  static final class Resolved
  {
    final String provider;
    final String bearer;    // API key or API token
    final String accountId; // Cloudflare only

    Resolved(String provider_, String bearer_, String accountId_)
    {
      provider = provider_;
      bearer = bearer_;
      accountId = accountId_;
    }
  }

  private final ReaderAiSettings settings;
  private final ReaderAiOpenRouter openRouter = new ReaderAiOpenRouter();
  private final ReaderAiCloudflare cloudflare = new ReaderAiCloudflare();

  ReaderAiProvider(ReaderAiSettings settings)
  {
    this.settings = settings;
  }

  /**
   * Provider to use now: the selected provider when its credential is
   * configured, otherwise whichever provider is configured.
   */
  String activeProvider() throws GeneralSecurityException
  {
    String selected = settings.getProvider();
    if (configured(selected))
      return selected;
    String other = ReaderAiSettings.Provider.OPENROUTER.equals(selected)
      ? ReaderAiSettings.Provider.CLOUDFLARE
      : ReaderAiSettings.Provider.OPENROUTER;
    return configured(other) ? other : selected;
  }

  private boolean configured(String provider) throws GeneralSecurityException
  {
    if (ReaderAiSettings.Provider.OPENROUTER.equals(provider))
      return !settings.getApiKey().isEmpty();
    return !settings.getCloudflareAccountId().isEmpty()
      && !settings.getCloudflareApiToken().isEmpty();
  }

  /** Whether any provider is ready to answer a request. */
  boolean isConfigured() throws GeneralSecurityException
  {
    return configured(ReaderAiSettings.Provider.CLOUDFLARE)
      || configured(ReaderAiSettings.Provider.OPENROUTER);
  }

  Resolved resolve() throws GeneralSecurityException, IOException
  {
    String provider = activeProvider();
    if (ReaderAiSettings.Provider.OPENROUTER.equals(provider))
    {
      String key = settings.getApiKey();
      if (key.isEmpty())
        throw new IOException(
            "Add an OpenRouter API key in Reader AI settings");
      return new Resolved(provider, key, "");
    }
    String token = settings.getCloudflareApiToken();
    String accountId = settings.getCloudflareAccountId();
    if (accountId.isEmpty())
      throw new IOException(
          "Add a Cloudflare Account ID in Reader AI settings");
    if (token.isEmpty())
      throw new IOException(
          "Add a Cloudflare API token in Reader AI settings");
    return new Resolved(provider, token, accountId);
  }

  /** Generator that resolves provider and credentials per call. */
  ReaderAiService.Generator generator()
  {
    return new ReaderAiService.Generator()
    {
      @Override public String generate(String apiKey, String modelId,
          List<ReaderAiOpenRouter.Message> messages) throws IOException,
          JSONException
      {
        try
        {
          Resolved resolved = resolve();
          if (ReaderAiSettings.Provider.OPENROUTER.equals(resolved.provider))
            return openRouter.generate(resolved.bearer, modelId, messages);
          return cloudflare.generate(resolved.accountId, resolved.bearer,
              modelId, messages);
        }
        catch (GeneralSecurityException error)
        {
          throw new IOException("Reader AI credential is unavailable", error);
        }
      }

      @Override public void cancel()
      {
        openRouter.cancel();
        cloudflare.cancel();
      }
    };
  }

  /**
   * Grammar correction request: bounded tokens, zero temperature. Returns
   * the raw model text for [AiGrammarFixer] guards.
   */
  String fixGrammar(String prompt, String input)
      throws IOException, JSONException, GeneralSecurityException
  {
    Resolved resolved = resolve();
    String modelId = settings.getActiveModelId();
    if (modelId.isEmpty())
      modelId = ReaderAiSettings.Provider.OPENROUTER
          .equals(resolved.provider)
        ? ReaderAiOpenRouter.PREFERRED_MODEL_ID
        : ReaderAiCloudflare.PREFERRED_MODEL_ID;
    org.json.JSONObject body = AiGrammarFixer.buildRequest(modelId, prompt,
        input, AiGrammarFixer.maxTokensFor(input));
    if (ReaderAiSettings.Provider.OPENROUTER.equals(resolved.provider))
      return openRouter.generate(resolved.bearer, body, 30_000);
    return cloudflare.generate(resolved.accountId, resolved.bearer, body,
        30_000);
  }

  /**
   * Model to run with: for Cloudflare the static catalog; for OpenRouter
   * the configured id (network catalog stays in the settings dialog).
   */
  ReaderAiOpenRouter.Model selectedModel() throws GeneralSecurityException
  {
    String provider = activeProvider();
    if (ReaderAiSettings.Provider.CLOUDFLARE.equals(provider))
    {
      String id = settings.getCloudflareModelId();
      for (ReaderAiOpenRouter.Model model : ReaderAiCloudflare.catalog())
        if (model.id.equals(id))
          return model;
      return new ReaderAiOpenRouter.Model(
          ReaderAiCloudflare.PREFERRED_MODEL_ID,
          ReaderAiCloudflare.PREFERRED_MODEL_ID, 0, Double.NaN, Double.NaN);
    }
    String id = settings.getModelId();
    return new ReaderAiOpenRouter.Model(id.isEmpty()
        ? ReaderAiOpenRouter.PREFERRED_MODEL_ID : id,
        id.isEmpty() ? ReaderAiOpenRouter.PREFERRED_MODEL_ID : id,
        0, Double.NaN, Double.NaN);
  }
}
