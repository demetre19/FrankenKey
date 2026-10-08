package juloo.keyboard2;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Bounded Cloudflare Workers AI client. Cloudflare exposes the same
 * OpenAI-shaped chat/completions contract as OpenRouter, so request and
 * response handling is shared with [ReaderAiOpenRouter].
 */
final class ReaderAiCloudflare
{
  static final String PREFERRED_MODEL_ID =
    "@cf/meta/llama-3.1-8b-instruct-fp8-fast";
  private static final String CHAT_BASE =
    "https://api.cloudflare.com/client/v4/accounts/%s/ai/v1/chat/completions";
  private static final int MAX_RESPONSE_BYTES = 16 * 1024 * 1024;

  /** Curated Workers AI catalog; the model picker is static, no request. */
  static List<ReaderAiOpenRouter.Model> catalog()
  {
    List<ReaderAiOpenRouter.Model> models = new ArrayList<>();
    models.add(new ReaderAiOpenRouter.Model(
        "@cf/meta/llama-3.1-8b-instruct-fp8-fast",
        "Llama 3.1 8B Instruct (fast)", 0, Double.NaN, Double.NaN));
    models.add(new ReaderAiOpenRouter.Model(
        "@cf/meta/llama-3.3-70b-instruct-fp8-fast",
        "Llama 3.3 70B Instruct (fast)", 0, Double.NaN, Double.NaN));
    models.add(new ReaderAiOpenRouter.Model("@cf/openai/gpt-oss-20b",
        "GPT OSS 20B", 0, Double.NaN, Double.NaN));
    models.add(new ReaderAiOpenRouter.Model("@cf/openai/gpt-oss-120b",
        "GPT OSS 120B", 0, Double.NaN, Double.NaN));
    models.add(new ReaderAiOpenRouter.Model(
        "@cf/mistralai/mistral-small-3.1-24b-instruct",
        "Mistral Small 3.1 24B Instruct", 0, Double.NaN, Double.NaN));
    return models;
  }

  static boolean isCatalogModel(String id)
  {
    if (id == null)
      return false;
    for (ReaderAiOpenRouter.Model model : catalog())
      if (model.id.equals(id))
        return true;
    return false;
  }

  private final Set<HttpURLConnection> activeConnections =
    Collections.synchronizedSet(new HashSet<>());

  String generate(String accountId, String apiToken, String modelId,
      List<ReaderAiOpenRouter.Message> messages)
      throws IOException, JSONException
  {
    String id = accountId == null ? "" : accountId.trim();
    String token = apiToken == null ? "" : apiToken.trim();
    if (id.isEmpty())
      throw new IOException(
          "Add a Cloudflare Account ID in Reader AI settings");
    if (token.isEmpty())
      throw new IOException(
          "Add a Cloudflare API token in Reader AI settings");
    if (modelId == null || modelId.trim().isEmpty())
      throw new IOException(
          "Choose a Cloudflare model in Reader AI settings");
    if (messages == null || messages.isEmpty())
      throw new IOException("Cloudflare request has no messages");

    org.json.JSONObject body = ReaderAiOpenRouter.buildRequest(modelId,
        messages);
    return generate(id, token, body, 120_000);
  }

  /** Sends a pre-built request body (custom max_tokens/temperature). */
  String generate(String accountId, String apiToken,
      org.json.JSONObject body, int readTimeout)
      throws IOException, JSONException
  {
    String id = accountId == null ? "" : accountId.trim();
    String token = apiToken == null ? "" : apiToken.trim();
    if (id.isEmpty())
      throw new IOException(
          "Add a Cloudflare Account ID in Reader AI settings");
    if (token.isEmpty())
      throw new IOException(
          "Add a Cloudflare API token in Reader AI settings");
    if (body == null)
      throw new IOException("Cloudflare request has no body");

    HttpURLConnection connection = openConnection(
        String.format(java.util.Locale.US, CHAT_BASE, id), token,
        readTimeout);
    activeConnections.add(connection);
    try
    {
      byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
      connection.setFixedLengthStreamingMode(payload.length);
      connection.getOutputStream().write(payload);
      return ReaderAiOpenRouter.parseCompletion(
          readJsonResponse(connection).toString());
    }
    finally
    {
      connection.disconnect();
      activeConnections.remove(connection);
    }
  }

  void cancel()
  {
    List<HttpURLConnection> connections;
    synchronized (activeConnections)
    {
      connections = new ArrayList<>(activeConnections);
    }
    for (HttpURLConnection connection : connections)
      connection.disconnect();
  }

  private static HttpURLConnection openConnection(String endpoint,
      String apiToken, int readTimeout) throws IOException
  {
    HttpURLConnection connection = (HttpURLConnection)new URL(endpoint)
      .openConnection();
    connection.setRequestMethod("POST");
    connection.setConnectTimeout(20_000);
    connection.setReadTimeout(readTimeout);
    connection.setUseCaches(false);
    connection.setRequestProperty("Accept", "application/json");
    connection.setRequestProperty("Authorization",
        "Bearer " + apiToken);
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type",
        "application/json; charset=utf-8");
    return connection;
  }

  private static org.json.JSONObject readJsonResponse(
      HttpURLConnection connection) throws IOException, JSONException
  {
    int status = connection.getResponseCode();
    InputStream stream = status >= 200 && status < 300
      ? connection.getInputStream() : connection.getErrorStream();
    String text = stream == null ? "" : readBounded(stream);
    org.json.JSONObject response;
    try
    {
      response = text.trim().isEmpty()
        ? new org.json.JSONObject() : new org.json.JSONObject(text);
    }
    catch (JSONException error)
    {
      throw new IOException("Cloudflare returned an invalid response (HTTP "
          + status + ")", error);
    }
    if (status < 200 || status >= 300 || response.has("error")
        || (response.has("success") && !response.optBoolean("success", false)))
    {
      String message = "";
      org.json.JSONArray errors = response.optJSONArray("errors");
      if (errors != null && errors.length() > 0)
      {
        org.json.JSONObject first = errors.optJSONObject(0);
        if (first != null)
          message = first.optString("message", "").trim();
      }
      org.json.JSONObject error = response.optJSONObject("error");
      if (message.isEmpty() && error != null)
        message = error.optString("message", "").trim();
      throw new IOException(message.isEmpty()
          ? "Cloudflare request failed (HTTP " + status + ")" : message);
    }
    return response;
  }

  private static String readBounded(InputStream stream) throws IOException
  {
    try (InputStream input = stream;
        ByteArrayOutputStream output = new ByteArrayOutputStream())
    {
      byte[] buffer = new byte[8192];
      int total = 0;
      int count;
      while ((count = input.read(buffer)) != -1)
      {
        total += count;
        if (total > MAX_RESPONSE_BYTES)
          throw new IOException("Response exceeded the allowed size");
        output.write(buffer, 0, count);
      }
      return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
  }
}
