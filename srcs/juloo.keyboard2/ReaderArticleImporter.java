package juloo.keyboard2;
import android.content.Context;
import android.graphics.BitmapFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** Fetches a user-approved public web article without permitting SSRF pivots. */
final class ReaderArticleImporter
{
  private static final int MAX_REDIRECTS = 5;
  private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
  private static final int MAX_PREVIEW_BYTES = 512 * 1024;
  private static final int MAX_INLINE_IMAGES = 8;
  private static final int MAX_PREVIEW_DIMENSION = 8192;
  private static final int CONNECT_TIMEOUT_MILLIS = 8000;
  private static final int READ_TIMEOUT_MILLIS = 8000;

  private ReaderArticleImporter() {}

  static ReaderImportPipeline.Candidate importUrl(String requestedUrl)
      throws ReaderImportPipeline.ImportException
  {
    URL current = parsePublicUrl(requestedUrl);
    for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++)
    {
      validatePublicTarget(current);
      HttpURLConnection connection = null;
      try
      {
        connection = (HttpURLConnection)current.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(READ_TIMEOUT_MILLIS);
        connection.setRequestProperty("Accept",
            "text/html,application/xhtml+xml,text/plain;q=0.8");
        connection.setRequestProperty("User-Agent", "FrankenKey Reader/1");

        int status = connection.getResponseCode();
        if (status >= 300 && status < 400)
        {
          if (redirects == MAX_REDIRECTS)
            throw new ReaderImportPipeline.ImportException(
                "This link redirects too many times.");
          String location = connection.getHeaderField("Location");
          if (location == null || location.trim().isEmpty())
            throw new ReaderImportPipeline.ImportException(
                "This link returned an invalid redirect.");
          current = parsePublicUrl(
              new URL(current, location).toExternalForm());
          continue;
        }
        if (status < 200 || status >= 300)
          throw new ReaderImportPipeline.ImportException(
              "The article server returned HTTP " + status + ".");

        String contentType = connection.getContentType();
        if (!supportedContentType(contentType))
          throw new ReaderImportPipeline.ImportException(
              "This link did not return a readable web article.");
        long declaredLength = connection.getContentLengthLong();
        if (declaredLength > MAX_RESPONSE_BYTES)
          throw new ReaderImportPipeline.ImportException(
              "This article is too large to import safely.");

        byte[] response;
        try (InputStream input = connection.getInputStream())
        {
          response = readBounded(input, MAX_RESPONSE_BYTES);
        }
        return extract(response, contentType, current.toExternalForm());
      }
      catch (ReaderImportPipeline.ImportException error)
      {
        throw error;
      }
      catch (IOException | RuntimeException error)
      {
        throw new ReaderImportPipeline.ImportException(
            "The article could not be downloaded. Check the link and try again.",
            error);
      }
      finally
      {
        if (connection != null)
          connection.disconnect();
      }
    }
    throw new ReaderImportPipeline.ImportException(
        "This link redirects too many times.");
  }

  static String cachePreviewImage(Context context, String requestedUrl,
      String itemId)
  {
    if (context == null || requestedUrl == null || itemId == null ||
        !itemId.matches("[0-9a-fA-F-]{36}"))
      return null;
    return cacheImage(context, requestedUrl,
        "previews/" + itemId + ".img");
  }

  static List<ReaderLibrary.ContentUnit> cacheInlineImages(Context context,
      List<ReaderLibrary.ContentUnit> units, String itemId)
  {
    ArrayList<ReaderLibrary.ContentUnit> stored = new ArrayList<>();
    if (units == null || itemId == null ||
        !itemId.matches("[0-9a-fA-F-]{36}"))
      return stored;
    for (ReaderLibrary.ContentUnit unit : units)
    {
      String assetUri = unit.assetUri;
      if ("image".equals(unit.kind))
      {
        assetUri = cacheImage(context, unit.sourceLocator,
            "articles/" + itemId + "/image-" + unit.ordinal + ".img");
        if (assetUri == null)
          continue;
      }
      stored.add(new ReaderLibrary.ContentUnit(stored.size(), unit.kind,
            unit.text, unit.languageTag, unit.sourceLocator, assetUri));
    }
    return stored;
  }

  static void deleteCachedInlineImages(Context context,
      List<ReaderLibrary.ContentUnit> units)
  {
    if (context == null || units == null)
      return;
    for (ReaderLibrary.ContentUnit unit : units)
    {
      if (unit.assetUri == null ||
          !unit.assetUri.matches(
            "private:articles/[0-9a-fA-F-]{36}/image-[0-9]+\\.img"))
        continue;
      File file = new File(context.getFilesDir(),
          "reader_library/" +
          unit.assetUri.substring("private:".length()));
      if (file.isFile())
        file.delete();
    }
  }

  private static String cacheImage(Context context, String requestedUrl,
      String relativeName)
  {
    if (context == null || requestedUrl == null || relativeName == null)
      return null;
    File output = new File(new File(context.getFilesDir(), "reader_library"),
        relativeName);
    try
    {
      URL current = parsePublicUrl(requestedUrl);
      for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++)
      {
        validatePublicTarget(current);
        HttpURLConnection connection = null;
        try
        {
          connection = (HttpURLConnection)current.openConnection();
          connection.setInstanceFollowRedirects(false);
          connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
          connection.setReadTimeout(READ_TIMEOUT_MILLIS);
          connection.setRequestProperty("Accept",
              "image/avif,image/webp,image/png,image/jpeg");
          connection.setRequestProperty("User-Agent", "FrankenKey Reader/1");
          int status = connection.getResponseCode();
          if (status >= 300 && status < 400)
          {
            if (redirects == MAX_REDIRECTS)
              return null;
            String location = connection.getHeaderField("Location");
            if (location == null || location.trim().isEmpty())
              return null;
            current = parsePublicUrl(
                new URL(current, location).toExternalForm());
            continue;
          }
          if (status < 200 || status >= 300 ||
              !supportedImageType(connection.getContentType()) ||
              connection.getContentLengthLong() > MAX_PREVIEW_BYTES)
            return null;
          byte[] image;
          try (InputStream input = connection.getInputStream())
          {
            image = readBounded(input, MAX_PREVIEW_BYTES);
          }
          BitmapFactory.Options bounds = new BitmapFactory.Options();
          bounds.inJustDecodeBounds = true;
          BitmapFactory.decodeByteArray(image, 0, image.length, bounds);
          if (bounds.outWidth <= 0 || bounds.outHeight <= 0 ||
              bounds.outWidth > MAX_PREVIEW_DIMENSION ||
              bounds.outHeight > MAX_PREVIEW_DIMENSION)
            return null;
          File parent = output.getParentFile();
          if (parent == null || (!parent.isDirectory() && !parent.mkdirs()))
            return null;
          try (FileOutputStream stream = new FileOutputStream(output))
          {
            stream.write(image);
          }
          return "private:" + relativeName;
        }
        finally
        {
          if (connection != null)
            connection.disconnect();
        }
      }
    }
    catch (IOException | RuntimeException |
        ReaderImportPipeline.ImportException error)
    {
      if (output.exists())
        output.delete();
    }
    return null;
  }

  static void deleteCachedPreview(Context context, String imageUri)
  {
    if (context == null || imageUri == null ||
        !imageUri.matches("private:previews/[0-9a-fA-F-]{36}\\.(?:img|jpg)"))
      return;
    File file = new File(context.getFilesDir(),
        "reader_library/" + imageUri.substring("private:".length()));
    if (file.isFile())
      file.delete();
  }

  static boolean isPublicAddress(InetAddress address)
  {
    if (address == null || address.isAnyLocalAddress() ||
        address.isLoopbackAddress() || address.isLinkLocalAddress() ||
        address.isSiteLocalAddress() || address.isMulticastAddress())
      return false;
    byte[] bytes = address.getAddress();
    if (bytes.length == 4)
    {
      int a = bytes[0] & 0xff;
      int b = bytes[1] & 0xff;
      if (a == 0 || a == 10 || a == 127 || a >= 224 ||
          (a == 100 && b >= 64 && b <= 127) ||
          (a == 169 && b == 254) ||
          (a == 172 && b >= 16 && b <= 31) ||
          (a == 192 && b == 168) ||
          (a == 198 && (b == 18 || b == 19)))
        return false;
    }
    else if (bytes.length == 16)
    {
      int first = bytes[0] & 0xff;
      if ((first & 0xfe) == 0xfc)
        return false;
    }
    return true;
  }

  private static URL parsePublicUrl(String value)
      throws ReaderImportPipeline.ImportException
  {
    try
    {
      URI uri = new URI(value == null ? "" : value.trim());
      String scheme = uri.getScheme();
      if (scheme == null ||
          (!"http".equalsIgnoreCase(scheme) &&
           !"https".equalsIgnoreCase(scheme)) ||
          uri.getHost() == null || uri.getUserInfo() != null)
        throw new ReaderImportPipeline.ImportException(
            "Only public http or https article links are supported.");
      int port = uri.getPort();
      if (port != -1 && port != 80 && port != 443)
        throw new ReaderImportPipeline.ImportException(
            "This article link uses an unsupported network port.");
      return uri.toURL();
    }
    catch (ReaderImportPipeline.ImportException error)
    {
      throw error;
    }
    catch (Exception error)
    {
      throw new ReaderImportPipeline.ImportException(
          "This article link is malformed.", error);
    }
  }

  private static void validatePublicTarget(URL url)
      throws ReaderImportPipeline.ImportException
  {
    String host = url.getHost().toLowerCase(Locale.ROOT);
    if ("localhost".equals(host) || host.endsWith(".localhost") ||
        host.endsWith(".local"))
      throw new ReaderImportPipeline.ImportException(
          "Local and private network links cannot be imported.");
    try
    {
      InetAddress[] addresses = InetAddress.getAllByName(host);
      if (addresses.length == 0)
        throw new UnknownHostException(host);
      for (InetAddress address : addresses)
        if (!isPublicAddress(address))
          throw new ReaderImportPipeline.ImportException(
              "Local and private network links cannot be imported.");
    }
    catch (ReaderImportPipeline.ImportException error)
    {
      throw error;
    }
    catch (UnknownHostException error)
    {
      throw new ReaderImportPipeline.ImportException(
          "The article host could not be found.", error);
    }
  }

  static ReaderImportPipeline.Candidate extract(byte[] response,
      String contentType, String sourceUrl)
      throws ReaderImportPipeline.ImportException
  {
    try
    {
      Document document = Jsoup.parse(new ByteArrayInputStream(response), null,
          sourceUrl);
      document.select(
          "script,style,noscript,nav,aside,form,iframe,svg,canvas," +
          "[role=navigation]").remove();
      Element content = selectArticleContent(document);
      ArrayList<ReaderLibrary.ContentUnit> units =
        extractArticleUnits(content, sourceUrl);
      int textLength = 0;
      for (ReaderLibrary.ContentUnit unit : units)
        if (!"image".equals(unit.kind))
          textLength += unit.text.length();
      if (textLength < 20)
        throw new ReaderImportPipeline.ImportException(
            "No readable article text was found at this link.");
      if (textLength > ReaderImportPipeline.MAX_IMPORTED_TEXT_CHARACTERS)
        throw new ReaderImportPipeline.ImportException(
            "This article is too large to import safely.");

      String title = document.title();
      Element heading = content == null ? null : content.selectFirst("h1");
      if ((title == null || title.trim().isEmpty()) && heading != null)
        title = heading.text();
      if (title == null || title.trim().isEmpty())
        title = new URL(sourceUrl).getHost();
      Element preview = document.selectFirst(
          "meta[property=og:image],meta[name=twitter:image]");
      String imageUrl = null;
      if (preview != null)
        imageUrl = absoluteImageUrl(sourceUrl, preview.attr("content"));
      if (imageUrl == null)
        for (ReaderLibrary.ContentUnit unit : units)
          if ("image".equals(unit.kind))
          {
            imageUrl = unit.sourceLocator;
            break;
          }
      return ReaderImportPipeline.Candidate.article(
          title, sourceUrl, units, imageUrl);
    }
    catch (ReaderImportPipeline.ImportException error)
    {
      throw error;
    }
    catch (IOException | RuntimeException error)
    {
      throw new ReaderImportPipeline.ImportException(
          "The downloaded article was malformed.", error);
    }
  }

  private static Element selectArticleContent(Document document)
  {
    String[] selectors = {
      "article",
      "[itemprop=articleBody]",
      "main .article-body,main .article-content,main .entry-content," +
        "main .post-content",
      "#mw-content-text > .mw-parser-output",
      "main",
      "[role=main]"
    };
    for (String selector : selectors)
    {
      Element content = document.selectFirst(selector);
      if (content != null)
        return content;
    }
    return document.body();
  }

  private static ArrayList<ReaderLibrary.ContentUnit> extractArticleUnits(
      Element content, String sourceUrl)
  {
    ArrayList<ReaderLibrary.ContentUnit> units = new ArrayList<>();
    if (content == null)
      return units;
    Set<String> images = new HashSet<>();
    for (Element element : content.select(
          "h1,h2,h3,h4,h5,h6,p,blockquote,pre,li,img"))
    {
      if ("img".equals(element.tagName()))
      {
        if (!isLikelyArticleImage(element))
          continue;
        if (images.size() >= MAX_INLINE_IMAGES)
          continue;
        String imageUrl = imageUrl(element, sourceUrl);
        if (imageUrl == null || !images.add(imageUrl))
          continue;
        String alt = ReaderLibrary.normalizeText(element.attr("alt"));
        if (alt.isEmpty())
          alt = "Article image";
        units.add(new ReaderLibrary.ContentUnit(units.size(), "image", alt,
              null, imageUrl));
        continue;
      }
      if (hasBlockAncestor(element, content))
        continue;
      String text = ReaderLibrary.normalizeText(element.wholeText());
      if (!text.isEmpty())
        units.add(new ReaderLibrary.ContentUnit(units.size(),
              element.tagName(), text, null, sourceUrl));
    }
    if (units.isEmpty())
    {
      String text = ReaderLibrary.normalizeText(content.wholeText());
      if (!text.isEmpty())
        units.add(new ReaderLibrary.ContentUnit(0, "article", text, null,
              sourceUrl));
    }
    return units;
  }

  private static boolean hasBlockAncestor(Element element, Element root)
  {
    for (Element parent = element.parent(); parent != null && parent != root;
        parent = parent.parent())
      if (parent.is("p,blockquote,pre,li"))
        return true;
    return false;
  }

  private static boolean isLikelyArticleImage(Element image)
  {
    int width = imageDimension(image, "width", "data-file-width");
    int height = imageDimension(image, "height", "data-file-height");
    if ((width > 0 && width < 64) || (height > 0 && height < 48))
      return false;
    String marker = (image.className() + " " + image.id()).toLowerCase(Locale.ROOT);
    String role = image.attr("role");
    if ("presentation".equalsIgnoreCase(role) ||
        "true".equalsIgnoreCase(image.attr("aria-hidden")) ||
        marker.contains("icon") || marker.contains("logo") ||
        marker.contains("avatar") || marker.contains("badge") ||
        marker.contains("flag") || marker.contains("sprite"))
      return false;
    return true;
  }

  private static int imageDimension(Element image, String primary,
      String fallback)
  {
    String value = firstNonEmpty(image.attr(primary), image.attr(fallback));
    if (value == null)
      return 0;
    try
    {
      return Integer.parseInt(value.replaceAll("[^0-9].*$", ""));
    }
    catch (NumberFormatException error)
    {
      return 0;
    }
  }

  private static String imageUrl(Element image, String sourceUrl)
  {
    String source = firstNonEmpty(image.attr("data-src"),
        image.attr("data-lazy-src"), image.attr("data-original"),
        image.attr("data-lazy"), image.attr("data-url"), image.attr("src"));
    if (source == null)
    {
      String srcset = firstNonEmpty(image.attr("data-srcset"),
          image.attr("srcset"));
      if (srcset != null)
        source = srcset.split(",", 2)[0].trim().split("\\s+", 2)[0];
    }
    return absoluteImageUrl(sourceUrl, source);
  }

  private static String firstNonEmpty(String... values)
  {
    for (String value : values)
      if (value != null && !value.trim().isEmpty())
        return value.trim();
    return null;
  }

  private static String absoluteImageUrl(String sourceUrl, String candidate)
  {
    if (candidate == null || candidate.trim().isEmpty() ||
        candidate.startsWith("data:") || candidate.startsWith("blob:"))
      return null;
    try
    {
      URL resolved = new URL(new URL(sourceUrl), candidate.trim());
      String scheme = resolved.getProtocol();
      return "http".equalsIgnoreCase(scheme) ||
        "https".equalsIgnoreCase(scheme)
        ? resolved.toExternalForm() : null;
    }
    catch (IOException error)
    {
      return null;
    }
  }

  private static boolean supportedContentType(String value)
  {
    if (value == null)
      return false;
    String type = value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    return "text/html".equals(type) ||
      "application/xhtml+xml".equals(type) ||
      "text/plain".equals(type);
  }

  private static boolean supportedImageType(String value)
  {
    if (value == null)
      return false;
    String type = value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    return "image/jpeg".equals(type) || "image/png".equals(type) ||
      "image/webp".equals(type) || "image/avif".equals(type);
  }

  private static byte[] readBounded(InputStream input, int maximum)
      throws IOException, ReaderImportPipeline.ImportException
  {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int total = 0;
    int read;
    while ((read = input.read(buffer)) != -1)
    {
      total += read;
      if (total > maximum)
        throw new ReaderImportPipeline.ImportException(
            "This article is too large to import safely.");
      output.write(buffer, 0, read);
    }
    return output.toByteArray();
  }
}
