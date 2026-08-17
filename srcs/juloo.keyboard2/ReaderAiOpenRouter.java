package juloo.keyboard2;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Bounded OpenRouter text-model client shared by Reader AI surfaces. */
final class ReaderAiOpenRouter
{
  static final String PREFERRED_MODEL_ID = "inception/mercury-2";
  private static final String MODELS_URL = "https://openrouter.ai/api/v1/models";
  private static final String CHAT_URL = "https://openrouter.ai/api/v1/chat/completions";
  private static final int MAX_RESPONSE_BYTES = 16 * 1024 * 1024;

  static final class Model
  {
    final String id;
    final String name;
    final int contextLength;
    final double promptPrice;
    final double completionPrice;

    Model(String id, String name, int contextLength, double promptPrice,
        double completionPrice)
    {
      this.id = id;
      this.name = name;
      this.contextLength = contextLength;
      this.promptPrice = promptPrice;
      this.completionPrice = completionPrice;
    }

    boolean isFree()
    {
      return Double.isFinite(promptPrice) && Double.isFinite(completionPrice)
        && promptPrice == 0 && completionPrice == 0;
    }

    boolean hasLongContext()
    {
      return contextLength >= 100_000;
    }

    String guidance()
    {
      return contextLabel() + " • " + pricingLabel();
    }

    String searchText()
    {
      return (name + " " + id + " " + guidance()
          + (isFree() ? " free" : " paid")).toLowerCase(Locale.US);
    }

    private String contextLabel()
    {
      if (contextLength <= 0)
        return "Context unknown";
      if (contextLength >= 1_000_000)
        return String.format(Locale.US, "%.1fM context",
            contextLength / 1_000_000d);
      return Math.round(contextLength / 1_000d) + "K context";
    }

    private String pricingLabel()
    {
      if (!Double.isFinite(promptPrice) || !Double.isFinite(completionPrice))
        return "Pricing unavailable";
      if (isFree())
        return "Free";
      return "$" + pricePerMillion(promptPrice) + "/M input • $"
        + pricePerMillion(completionPrice) + "/M output";
    }

    private static String pricePerMillion(double perToken)
    {
      String result = String.format(Locale.US,
          perToken * 1_000_000d >= 10 ? "%.2f" : "%.4f",
          perToken * 1_000_000d);
      return result.replaceAll("0+$", "").replaceAll("\\.$", "");
    }
  }

  static final class Message
  {
    final String role;
    final String content;

    Message(String role, String content)
    {
      this.role = role;
      this.content = content;
    }
  }

  private volatile HttpURLConnection activeConnection;

  List<Model> fetchModels(String apiKey) throws IOException, JSONException
  {
    HttpURLConnection connection = openConnection(MODELS_URL, "GET", apiKey,
        30_000);
    activeConnection = connection;
    try
    {
      return parseModels(readJsonResponse(connection).toString());
    }
    finally
    {
      connection.disconnect();
      if (activeConnection == connection)
        activeConnection = null;
    }
  }

  String generate(String apiKey, String modelId, List<Message> messages)
      throws IOException, JSONException
  {
    if (apiKey == null || apiKey.trim().isEmpty())
      throw new IOException("Add an OpenRouter API key in Reader AI settings");
    if (modelId == null || modelId.trim().isEmpty())
      throw new IOException("Choose an OpenRouter model in Reader AI settings");
    if (messages == null || messages.isEmpty())
      throw new IOException("OpenRouter request has no messages");

    JSONObject body = buildRequest(modelId, messages);
    HttpURLConnection connection = openConnection(CHAT_URL, "POST", apiKey,
        120_000);
    activeConnection = connection;
    try
    {
      byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
      connection.setFixedLengthStreamingMode(payload.length);
      connection.getOutputStream().write(payload);
      return parseCompletion(readJsonResponse(connection).toString());
    }
    finally
    {
      connection.disconnect();
      if (activeConnection == connection)
        activeConnection = null;
    }
  }

  void cancel()
  {
    HttpURLConnection connection = activeConnection;
    if (connection != null)
      connection.disconnect();
  }

  static JSONObject buildRequest(String modelId, List<Message> messages)
      throws JSONException
  {
    JSONObject body = new JSONObject();
    body.put("model", modelId.trim());
    body.put("max_tokens", 4096);
    body.put("temperature", 0.7);
    JSONArray payloadMessages = new JSONArray();
    for (Message message : messages)
      payloadMessages.put(new JSONObject().put("role", message.role)
          .put("content", message.content));
    body.put("messages", payloadMessages);
    return body;
  }

  static List<Model> parseModels(String json) throws JSONException, IOException
  {
    JSONArray data = new JSONObject(json).optJSONArray("data");
    if (data == null)
      throw new IOException("OpenRouter returned no model catalog");
    List<Model> models = new ArrayList<>();
    for (int index = 0; index < data.length(); index++)
    {
      JSONObject item = data.optJSONObject(index);
      if (item == null || !supportsTextOutput(item))
        continue;
      String id = item.optString("id", "").trim();
      if (id.isEmpty())
        continue;
      String name = item.optString("name", id).trim();
      JSONObject pricing = item.optJSONObject("pricing");
      models.add(new Model(id, name.isEmpty() ? id : name,
            item.optInt("context_length", 0), parsePrice(pricing, "prompt"),
            parsePrice(pricing, "completion")));
    }
    models.sort(Comparator.comparing(model -> model.name.toLowerCase(Locale.US)));
    return models;
  }

  static String parseCompletion(String json) throws JSONException, IOException
  {
    JSONArray choices = new JSONObject(json).optJSONArray("choices");
    JSONObject choice = choices == null || choices.length() == 0
      ? null : choices.optJSONObject(0);
    JSONObject message = choice == null ? null : choice.optJSONObject("message");
    if (message == null)
      throw new IOException("OpenRouter returned no result");
    String content = extractContent(message.opt("content")).trim();
    if (content.isEmpty())
      throw new IOException("OpenRouter returned an empty result");
    return content;
  }

  private static HttpURLConnection openConnection(String endpoint, String method,
      String apiKey, int readTimeout) throws IOException
  {
    HttpURLConnection connection = (HttpURLConnection)new URL(endpoint)
      .openConnection();
    connection.setRequestMethod(method);
    connection.setConnectTimeout(20_000);
    connection.setReadTimeout(readTimeout);
    connection.setUseCaches(false);
    connection.setRequestProperty("Accept", "application/json");
    connection.setRequestProperty("X-OpenRouter-Title", "FrankenKey");
    if (apiKey != null && !apiKey.trim().isEmpty())
      connection.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
    if ("POST".equals(method))
    {
      connection.setDoOutput(true);
      connection.setRequestProperty("Content-Type",
          "application/json; charset=utf-8");
    }
    return connection;
  }

  private static JSONObject readJsonResponse(HttpURLConnection connection)
      throws IOException, JSONException
  {
    int status = connection.getResponseCode();
    InputStream stream = status >= 200 && status < 300
      ? connection.getInputStream() : connection.getErrorStream();
    String text = stream == null ? "" : readBounded(stream);
    JSONObject response;
    try
    {
      response = text.trim().isEmpty() ? new JSONObject() : new JSONObject(text);
    }
    catch (JSONException error)
    {
      throw new IOException("OpenRouter returned an invalid response (HTTP "
          + status + ")", error);
    }
    if (status < 200 || status >= 300 || response.has("error"))
    {
      JSONObject error = response.optJSONObject("error");
      String message = error == null ? ""
        : error.optString("message", "").trim();
      throw new IOException(message.isEmpty()
          ? "OpenRouter request failed (HTTP " + status + ")" : message);
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

  private static double parsePrice(JSONObject pricing, String key)
  {
    if (pricing == null)
      return Double.NaN;
    try
    {
      double value = Double.parseDouble(pricing.optString(key, ""));
      return Double.isFinite(value) && value >= 0 ? value : Double.NaN;
    }
    catch (NumberFormatException ignored)
    {
      return Double.NaN;
    }
  }

  private static boolean supportsTextOutput(JSONObject model)
  {
    JSONObject architecture = model.optJSONObject("architecture");
    if (architecture == null)
      return true;
    JSONArray outputs = architecture.optJSONArray("output_modalities");
    if (outputs != null)
    {
      for (int index = 0; index < outputs.length(); index++)
        if ("text".equalsIgnoreCase(outputs.optString(index)))
          return true;
      return false;
    }
    String modality = architecture.optString("modality", "");
    return modality.isEmpty() || modality.endsWith("->text");
  }

  private static String extractContent(Object content)
  {
    if (content instanceof String)
      return (String)content;
    if (content instanceof JSONArray)
    {
      JSONArray parts = (JSONArray)content;
      StringBuilder combined = new StringBuilder();
      for (int index = 0; index < parts.length(); index++)
      {
        JSONObject part = parts.optJSONObject(index);
        if (part != null && "text".equals(part.optString("type")))
          combined.append(part.optString("text"));
      }
      return combined.toString();
    }
    return "";
  }
}
