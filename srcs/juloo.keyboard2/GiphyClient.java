package juloo.keyboard2;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

final class GiphyClient
{
  static final String PREF_API_KEY = "giphy_api_key";
  static final String DASHBOARD_URL = "https://developers.giphy.com/dashboard/?create=true";

  private GiphyClient() {}

  static List<GifResult> search(String apiKey, String query) throws Exception
  {
    String key = apiKey == null ? "" : apiKey.trim();
    if (key.length() == 0)
      return new ArrayList<GifResult>();
    String q = query == null ? "" : query.trim();
    String endpoint;
    if (q.length() == 0)
      endpoint = "https://api.giphy.com/v1/gifs/trending?api_key=" +
        encode(key) + "&limit=12&rating=pg&bundle=messaging_non_clips";
    else
      endpoint = "https://api.giphy.com/v1/gifs/search?api_key=" +
        encode(key) + "&q=" + encode(q) +
        "&limit=12&rating=pg&bundle=messaging_non_clips";

    JSONObject root = new JSONObject(readUrl(endpoint));
    JSONArray data = root.getJSONArray("data");
    ArrayList<GifResult> results = new ArrayList<GifResult>();
    for (int i = 0; i < data.length(); i++)
    {
      JSONObject gif = data.getJSONObject(i);
      JSONObject images = gif.getJSONObject("images");
      String preview = imageUrl(images, "fixed_width_small_still", "url");
      if (preview == null)
        preview = imageUrl(images, "fixed_width_small", "url");
      String full = imageUrl(images, "fixed_height_small", "url");
      if (full == null)
        full = imageUrl(images, "original", "url");
      if (full == null)
        continue;
      results.add(GifResult.remote(gif.optString("id", "giphy_" + i),
            gif.optString("title", "GIPHY GIF"), preview, full));
    }
    return results;
  }

  static Bitmap downloadBitmap(String url) throws Exception
  {
    if (url == null || url.length() == 0)
      return null;
    HttpURLConnection conn = open(url);
    try
    {
      try (InputStream input = conn.getInputStream())
      {
        return BitmapFactory.decodeStream(input);
      }
    }
    finally
    {
      conn.disconnect();
    }
  }

  static GifResult downloadToCache(Context context, GifResult result)
      throws Exception
  {
    File dir = new File(context.getCacheDir(), "giphy_gifs");
    if (!dir.exists() && !dir.mkdirs())
      throw new Exception("Unable to create GIF cache");
    File out = new File(dir, result.fileName());
    HttpURLConnection conn = open(result.gifUrl);
    try
    {
      try (InputStream input = conn.getInputStream();
          OutputStream output = new FileOutputStream(out))
      {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1)
          output.write(buffer, 0, read);
      }
    }
    finally
    {
      conn.disconnect();
    }
    return GifResult.cached(result.id, result.title, out);
  }

  private static String imageUrl(JSONObject images, String rendition,
      String field)
  {
    JSONObject image = images.optJSONObject(rendition);
    if (image == null)
      return null;
    String url = image.optString(field, null);
    return url == null || url.length() == 0 ? null : url;
  }

  private static String readUrl(String url) throws Exception
  {
    HttpURLConnection conn = open(url);
    try
    {
      StringBuilder out = new StringBuilder();
      byte[] buffer = new byte[8192];
      try (InputStream input = conn.getInputStream())
      {
        int read;
        while ((read = input.read(buffer)) != -1)
          out.append(new String(buffer, 0, read, "UTF-8"));
      }
      return out.toString();
    }
    finally
    {
      conn.disconnect();
    }
  }

  private static HttpURLConnection open(String url) throws Exception
  {
    HttpURLConnection conn = (HttpURLConnection)new URL(url).openConnection();
    conn.setConnectTimeout(8000);
    conn.setReadTimeout(12000);
    conn.setRequestProperty("User-Agent", "FrankenKey GIF Search");
    int status = conn.getResponseCode();
    if (status < 200 || status >= 300)
      throw new Exception("HTTP " + status);
    return conn;
  }

  private static String encode(String value) throws Exception
  {
    return URLEncoder.encode(value, "UTF-8");
  }
}
