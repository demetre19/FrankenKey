package juloo.keyboard2;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Locale;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** Fetches a user-approved public web article without permitting SSRF pivots. */
final class ReaderArticleImporter
{
  private static final int MAX_REDIRECTS = 5;
  private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
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

  private static ReaderImportPipeline.Candidate extract(byte[] response,
      String contentType, String sourceUrl)
      throws ReaderImportPipeline.ImportException
  {
    try
    {
      Document document = Jsoup.parse(new ByteArrayInputStream(response), null,
          sourceUrl);
      document.select(
          "script,style,noscript,nav,aside,form,iframe,svg,canvas").remove();
      Element content = document.selectFirst("article");
      if (content == null)
        content = document.selectFirst("main,[role=main]");
      if (content == null)
        content = document.body();
      String text = content == null ? "" :
        ReaderLibrary.normalizeText(content.wholeText());
      if (text.length() < 20)
        throw new ReaderImportPipeline.ImportException(
            "No readable article text was found at this link.");
      if (text.length() > ReaderImportPipeline.MAX_IMPORTED_TEXT_CHARACTERS)
        throw new ReaderImportPipeline.ImportException(
            "This article is too large to import safely.");

      String title = document.title();
      Element heading = content.selectFirst("h1");
      if ((title == null || title.trim().isEmpty()) && heading != null)
        title = heading.text();
      if (title == null || title.trim().isEmpty())
        title = new URL(sourceUrl).getHost();
      return ReaderImportPipeline.Candidate.text(title,
          ReaderLibrary.SourceType.URL, sourceUrl, text);
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

  private static boolean supportedContentType(String value)
  {
    if (value == null)
      return false;
    String type = value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    return "text/html".equals(type) ||
      "application/xhtml+xml".equals(type) ||
      "text/plain".equals(type);
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
