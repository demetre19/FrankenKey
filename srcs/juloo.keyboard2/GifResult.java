package juloo.keyboard2;

import java.io.File;

final class GifResult
{
  final String id;
  final String title;
  final String tags;
  final int rawResId;
  final int size;
  final String previewUrl;
  final String gifUrl;
  final File cacheFile;

  private GifResult(String id, String title, String tags, int rawResId,
      int size, String previewUrl, String gifUrl, File cacheFile)
  {
    this.id = id;
    this.title = title;
    this.tags = tags;
    this.rawResId = rawResId;
    this.size = size;
    this.previewUrl = previewUrl;
    this.gifUrl = gifUrl;
    this.cacheFile = cacheFile;
  }

  static GifResult local(String id, String title, String tags, int rawResId,
      int size)
  {
    return new GifResult(id, title, tags, rawResId, size, null, null, null);
  }

  static GifResult remote(String id, String title, String previewUrl,
      String gifUrl)
  {
    return new GifResult(sanitizeId(id), title, title, 0, 0, previewUrl,
        gifUrl, null);
  }

  static GifResult cached(String id, String title, File cacheFile)
  {
    return new GifResult(sanitizeId(id), title, title, 0,
        (int)Math.min(Integer.MAX_VALUE, cacheFile.length()), null, null,
        cacheFile);
  }

  boolean isLocal()
  {
    return rawResId != 0;
  }

  boolean isRemote()
  {
    return gifUrl != null;
  }

  boolean isCached()
  {
    return cacheFile != null;
  }

  String fileName()
  {
    return id + ".gif";
  }

  static String sanitizeId(String value)
  {
    if (value == null || value.length() == 0)
      return "gif";
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < value.length(); i++)
    {
      char c = value.charAt(i);
      if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
          (c >= '0' && c <= '9') || c == '_' || c == '-')
        out.append(c);
      else
        out.append('_');
    }
    return out.toString();
  }
}
