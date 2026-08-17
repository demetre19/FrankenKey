package juloo.keyboard2;

import android.content.Context;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.parsers.DocumentBuilderFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.w3c.dom.NodeList;

/** Defensive non-DRM EPUB parser for transient rich reading and Library metadata. */
final class ReaderEpubImporter
{
  private static final int MAX_ENTRIES = 2048;
  private static final int MAX_ENTRY_BYTES = 4 * 1024 * 1024;
  private static final int MAX_TOTAL_BYTES = 64 * 1024 * 1024;
  private static final int MAX_COMPRESSION_RATIO = 100;
  private static final int MAX_IMAGE_DIMENSION = 8192;
  private static final long MAX_IMAGE_PIXELS = 24L * 1024L * 1024L;
  private static final int COVER_WIDTH = 280;
  private static final int COVER_HEIGHT = 420;
  private static final String DOCTYPE_DECLARATION = "<!DOCTYPE";

  static final class Chapter
  {
    final int ordinal;
    final String path;
    final String text;
    final String html;
    final int rawWordStart;
    final int rawWordCount;

    Chapter(int ordinal, String path, String text, String html,
        int rawWordStart, int rawWordCount)
    {
      this.ordinal = ordinal;
      this.path = path;
      this.text = text;
      this.html = html;
      this.rawWordStart = rawWordStart;
      this.rawWordCount = rawWordCount;
    }
  }

  static final class Book
  {
    final String title;
    final String author;
    final String language;
    final String publisher;
    final String identifier;
    final List<Chapter> chapters;
    final byte[] coverBytes;
    final String coverMimeType;

    Book(String title, String author, String language, String publisher,
        String identifier, List<Chapter> chapters, byte[] coverBytes,
        String coverMimeType)
    {
      this.title = title;
      this.author = author;
      this.language = language;
      this.publisher = publisher;
      this.identifier = identifier;
      this.chapters = Collections.unmodifiableList(new ArrayList<>(chapters));
      this.coverBytes = coverBytes == null ? null : coverBytes.clone();
      this.coverMimeType = coverMimeType;
    }

    List<ReaderLibrary.ContentUnit> contentUnits()
    {
      ArrayList<ReaderLibrary.ContentUnit> units = new ArrayList<>();
      for (Chapter chapter : chapters)
        units.add(new ReaderLibrary.ContentUnit(chapter.ordinal, "chapter",
              chapter.text, language, chapter.path));
      return units;
    }
  }

  private static final class ManifestItem
  {
    final String id;
    final String path;
    final String mediaType;
    final String properties;

    ManifestItem(String id, String path, String mediaType, String properties)
    {
      this.id = id;
      this.path = path;
      this.mediaType = mediaType;
      this.properties = properties == null ? "" : properties;
    }
  }

  private ReaderEpubImporter() {}

  static ReaderImportPipeline.Candidate importFile(File file, String sourceUri,
      String fallbackTitle) throws ReaderImportPipeline.ImportException
  {
    Book book = readFile(file);
    return ReaderImportPipeline.Candidate.epub(
        empty(book.title) ? fallbackTitle : book.title, sourceUri, book.author,
        book.language, book.publisher, book.identifier, book.contentUnits(),
        book.coverBytes, book.coverMimeType);
  }


  static Book readUri(Context context, Uri uri)
      throws ReaderImportPipeline.ImportException
  {
    if (context == null || uri == null || !"content".equals(uri.getScheme()))
      throw new ReaderImportPipeline.ImportException(
          "The EPUB file is unavailable.");
    File temporary = null;
    try
    {
      temporary = File.createTempFile("reader-epub-", ".epub",
          context.getCacheDir());
      try (InputStream input = context.getContentResolver()
            .openInputStream(uri);
           FileOutputStream output = new FileOutputStream(temporary))
      {
        if (input == null)
          throw new IOException("provider did not open EPUB");
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1)
        {
          total += read;
          if (total > ReaderImportPipeline.MAX_DOCUMENT_BYTES)
            throw new ReaderImportPipeline.ImportException(
                "This EPUB is too large to open safely.");
          output.write(buffer, 0, read);
        }
      }
      return readFile(temporary);
    }
    catch (IOException | RuntimeException error)
    {
      throw new ReaderImportPipeline.ImportException(
          "The EPUB file could not be opened.", error);
    }
    finally
    {
      if (temporary != null && temporary.exists() && !temporary.delete())
        temporary.deleteOnExit();
    }
  }
  static Book readFile(File file) throws ReaderImportPipeline.ImportException
  {
    if (file == null || !file.isFile())
      throw new ReaderImportPipeline.ImportException(
          "The EPUB file is unavailable.");
    try (ZipFile zip = new ZipFile(file))
    {
      validateArchive(zip);
      ZipEntry mimetype = zip.getEntry("mimetype");
      if (mimetype == null || !"application/epub+zip".equals(
            new String(readEntry(zip, mimetype, new long[] {0}),
              StandardCharsets.US_ASCII).trim()))
        throw unsupported("This is not a supported EPUB container.");
      if (zip.getEntry("META-INF/encryption.xml") != null)
        throw unsupported("DRM or encrypted EPUB books are not supported.");

      long[] expandedTotal = {0};
      org.w3c.dom.Document container = parseXml(readRequired(zip,
            "META-INF/container.xml", expandedTotal));
      NodeList rootfiles = container.getElementsByTagNameNS("*", "rootfile");
      if (rootfiles.getLength() != 1)
        throw unsupported("The EPUB container has no unique package document.");
      String packagePath = ((org.w3c.dom.Element)rootfiles.item(0))
        .getAttribute("full-path");
      requireSafePath(packagePath);

      org.w3c.dom.Document publication = parseXml(readRequired(zip,
            packagePath, expandedTotal));
      String title = firstText(publication, "title");
      String author = firstText(publication, "creator");
      String language = firstText(publication, "language");
      String publisher = firstText(publication, "publisher");
      String identifier = firstText(publication, "identifier");

      Map<String, ManifestItem> manifestById = new HashMap<>();
      Map<String, ManifestItem> manifestByPath = new HashMap<>();
      NodeList items = publication.getElementsByTagNameNS("*", "item");
      for (int i = 0; i < items.getLength(); i++)
      {
        org.w3c.dom.Element item = (org.w3c.dom.Element)items.item(i);
        String id = item.getAttribute("id");
        String href = item.getAttribute("href");
        if (id.isEmpty() || href.isEmpty())
          continue;
        ManifestItem value = new ManifestItem(id,
            resolvePath(packagePath, href), item.getAttribute("media-type"),
            item.getAttribute("properties"));
        manifestById.put(id, value);
        manifestByPath.put(value.path, value);
      }

      ArrayList<Chapter> chapters = new ArrayList<>();
      int rawWordStart = 0;
      NodeList spine = publication.getElementsByTagNameNS("*", "itemref");
      for (int i = 0; i < spine.getLength(); i++)
      {
        String idref = ((org.w3c.dom.Element)spine.item(i))
          .getAttribute("idref");
        ManifestItem item = manifestById.get(idref);
        if (item == null)
          throw unsupported("The EPUB spine references missing content.");
        byte[] chapterBytes = readRequired(zip, item.path, expandedTotal);
        String html = sanitizeChapter(zip, chapterBytes, item.path,
            manifestByPath, expandedTotal);
        org.jsoup.nodes.Document clean = Jsoup.parseBodyFragment(html);
        String chapterText = ReaderLibrary.normalizeText(
            clean.body() == null ? "" : clean.body().wholeText());
        if (chapterText.isEmpty())
          continue;
        int rawWordCount = countWords(chapterText);
        chapters.add(new Chapter(chapters.size(), item.path, chapterText, html,
              rawWordStart, rawWordCount));
        rawWordStart += rawWordCount;
      }
      if (chapters.isEmpty())
        throw unsupported("The EPUB contains no readable spine text.");

      ManifestItem cover = findCover(publication, manifestById);
      byte[] coverBytes = null;
      String coverMimeType = null;
      if (cover != null && supportedImageType(cover.mediaType))
      {
        byte[] candidate = readRequired(zip, cover.path, expandedTotal);
        if (validImage(candidate, cover.mediaType))
        {
          coverBytes = candidate;
          coverMimeType = normalizedImageType(cover.mediaType);
        }
      }
      return new Book(emptyToNull(title), emptyToNull(author),
          emptyToNull(language), emptyToNull(publisher),
          emptyToNull(identifier), chapters, coverBytes, coverMimeType);
    }
    catch (ReaderImportPipeline.ImportException error)
    {
      throw error;
    }
    catch (Exception error)
    {
      throw new ReaderImportPipeline.ImportException(
          "This EPUB is malformed or unsupported.", error);
    }
  }

  static String cacheCover(Context context, byte[] bytes, String itemId)
  {
    if (context == null || bytes == null || bytes.length == 0 ||
        itemId == null || !itemId.matches("[0-9a-fA-F-]{36}") ||
        !hasSupportedImageSignature(bytes, null))
      return null;
    BitmapFactory.Options bounds = new BitmapFactory.Options();
    bounds.inJustDecodeBounds = true;
    BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
    if (!validDimensions(bounds.outWidth, bounds.outHeight))
      return null;

    int sample = 1;
    while (bounds.outWidth / sample > COVER_WIDTH * 2 ||
        bounds.outHeight / sample > COVER_HEIGHT * 2)
      sample *= 2;
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inSampleSize = sample;
    Bitmap decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.length,
        options);
    if (decoded == null)
      return null;
    Bitmap output = decoded;
    File target = null;
    try
    {
      float scale = Math.min(1f, Math.min(
            COVER_WIDTH / (float)decoded.getWidth(),
            COVER_HEIGHT / (float)decoded.getHeight()));
      if (scale < 1f)
        output = Bitmap.createScaledBitmap(decoded,
            Math.max(1, Math.round(decoded.getWidth() * scale)),
            Math.max(1, Math.round(decoded.getHeight() * scale)), true);
      File root = new File(context.getFilesDir(), "reader_library/previews");
      if (!root.isDirectory() && !root.mkdirs())
        return null;
      target = new File(root, itemId + ".jpg");
      try (FileOutputStream stream = new FileOutputStream(target))
      {
        if (!output.compress(Bitmap.CompressFormat.JPEG, 88, stream))
        {
          target.delete();
          return null;
        }
      }
      return "private:previews/" + itemId + ".jpg";
    }
    catch (IOException | RuntimeException error)
    {
      if (target != null)
        target.delete();
      return null;
    }
    finally
    {
      if (output != decoded)
        output.recycle();
      decoded.recycle();
    }
  }

  private static String sanitizeChapter(ZipFile zip, byte[] chapterBytes,
      String chapterPath, Map<String, ManifestItem> manifestByPath,
      long[] expandedTotal) throws Exception
  {
    org.jsoup.nodes.Document chapter = Jsoup.parse(
        new ByteArrayInputStream(chapterBytes), null,
        "epub:///" + chapterPath);
    chapter.select("script,style,noscript,nav,form,iframe,object,embed,svg," +
        "math,audio,video,source,picture,canvas,link,meta").remove();
    for (Element image : new ArrayList<>(chapter.select("img")))
    {
      String path = resolvePackagedPath(chapterPath, image.attr("src"));
      ManifestItem packaged = path == null ? null : manifestByPath.get(path);
      if (packaged == null || !supportedImageType(packaged.mediaType))
      {
        image.remove();
        continue;
      }
      byte[] bytes = readRequired(zip, packaged.path, expandedTotal);
      if (!validImage(bytes, packaged.mediaType))
      {
        image.remove();
        continue;
      }
      image.attr("src", "data:" + normalizedImageType(packaged.mediaType) +
          ";base64," + Base64.encodeToString(bytes, Base64.NO_WRAP));
    }

    Safelist allowed = new Safelist()
      .addTags("p", "br", "h1", "h2", "h3", "h4", "h5", "h6",
          "ul", "ol", "li", "blockquote", "pre", "code", "strong",
          "b", "em", "i", "u", "s", "sub", "sup", "span", "div",
          "section", "article", "figure", "figcaption", "table",
          "thead", "tbody", "tfoot", "tr", "th", "td", "img", "a")
      .addAttributes("img", "src", "alt", "title", "width", "height")
      .addAttributes("td", "colspan", "rowspan")
      .addAttributes("th", "colspan", "rowspan")
      .addProtocols("img", "src", "data");
    org.jsoup.nodes.Document.OutputSettings output =
      new org.jsoup.nodes.Document.OutputSettings().prettyPrint(false);
    return Jsoup.clean(chapter.body() == null ? "" : chapter.body().html(),
        "", allowed, output);
  }

  private static ManifestItem findCover(org.w3c.dom.Document publication,
      Map<String, ManifestItem> manifest)
  {
    for (ManifestItem item : manifest.values())
      if (containsToken(item.properties, "cover-image") &&
          supportedImageType(item.mediaType))
        return item;
    NodeList metadata = publication.getElementsByTagNameNS("*", "meta");
    for (int i = 0; i < metadata.getLength(); i++)
    {
      org.w3c.dom.Element meta = (org.w3c.dom.Element)metadata.item(i);
      if ("cover".equalsIgnoreCase(meta.getAttribute("name")))
      {
        ManifestItem item = manifest.get(meta.getAttribute("content"));
        if (item != null && supportedImageType(item.mediaType))
          return item;
      }
    }
    for (ManifestItem item : manifest.values())
      if (item.id.toLowerCase(Locale.ROOT).contains("cover") &&
          supportedImageType(item.mediaType))
        return item;
    return null;
  }

  private static boolean containsToken(String value, String token)
  {
    if (value == null)
      return false;
    for (String part : value.trim().split("\\s+"))
      if (token.equals(part))
        return true;
    return false;
  }

  private static String resolvePackagedPath(String chapterPath, String source)
      throws Exception
  {
    if (empty(source))
      return null;
    URI relative = new URI(source.trim());
    if (relative.isAbsolute() || relative.getRawAuthority() != null)
      return null;
    URI resolved = new URI("epub:///" + chapterPath).resolve(relative)
      .normalize();
    String path = resolved.getPath();
    if (path == null || !path.startsWith("/") || path.contains("/../"))
      return null;
    path = path.substring(1);
    requireSafePath(path);
    return path;
  }

  private static int countWords(String text)
  {
    int words = 0;
    boolean inWord = false;
    for (int i = 0; i < text.length(); i++)
    {
      boolean whitespace = Character.isWhitespace(text.charAt(i));
      if (!whitespace && !inWord)
        words++;
      inWord = !whitespace;
    }
    return words;
  }

  private static boolean supportedImageType(String mediaType)
  {
    String normalized = normalizedImageType(mediaType);
    return "image/jpeg".equals(normalized) || "image/png".equals(normalized) ||
      "image/webp".equals(normalized);
  }

  private static String normalizedImageType(String mediaType)
  {
    if (mediaType == null)
      return "";
    String normalized = mediaType.trim().toLowerCase(Locale.ROOT);
    return "image/jpg".equals(normalized) ? "image/jpeg" : normalized;
  }

  private static boolean validImage(byte[] bytes, String mediaType)
  {
    if (bytes == null || bytes.length == 0 || bytes.length > MAX_ENTRY_BYTES ||
        !hasSupportedImageSignature(bytes, mediaType))
      return false;
    BitmapFactory.Options bounds = new BitmapFactory.Options();
    bounds.inJustDecodeBounds = true;
    BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
    return validDimensions(bounds.outWidth, bounds.outHeight);
  }

  private static boolean hasSupportedImageSignature(byte[] bytes,
      String mediaType)
  {
    if (bytes == null)
      return false;
    String type = normalizedImageType(mediaType);
    boolean png = bytes.length >= 8 &&
      (bytes[0] & 0xff) == 0x89 && bytes[1] == 'P' &&
      bytes[2] == 'N' && bytes[3] == 'G' &&
      bytes[4] == 0x0d && bytes[5] == 0x0a &&
      bytes[6] == 0x1a && bytes[7] == 0x0a;
    boolean jpeg = bytes.length >= 3 &&
      (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 &&
      (bytes[2] & 0xff) == 0xff;
    boolean webp = bytes.length >= 12 &&
      bytes[0] == 'R' && bytes[1] == 'I' &&
      bytes[2] == 'F' && bytes[3] == 'F' &&
      bytes[8] == 'W' && bytes[9] == 'E' &&
      bytes[10] == 'B' && bytes[11] == 'P';
    if ("image/png".equals(type))
      return png;
    if ("image/jpeg".equals(type))
      return jpeg;
    if ("image/webp".equals(type))
      return webp;
    return type.isEmpty() && (png || jpeg || webp);
  }

  private static boolean validDimensions(int width, int height)
  {
    return width > 0 && height > 0 && width <= MAX_IMAGE_DIMENSION &&
      height <= MAX_IMAGE_DIMENSION && (long)width * height <= MAX_IMAGE_PIXELS;
  }

  private static void validateArchive(ZipFile zip)
      throws ReaderImportPipeline.ImportException
  {
    int count = 0;
    Enumeration<? extends ZipEntry> entries = zip.entries();
    while (entries.hasMoreElements())
    {
      ZipEntry entry = entries.nextElement();
      if (++count > MAX_ENTRIES)
        throw unsupported("The EPUB contains too many files.");
      requireSafePath(entry.getName());
      long expanded = entry.getSize();
      long compressed = entry.getCompressedSize();
      if (expanded > MAX_ENTRY_BYTES)
        throw unsupported("An EPUB entry is too large.");
      if (compressed > 0 && expanded > compressed * MAX_COMPRESSION_RATIO)
        throw unsupported("The EPUB uses unsafe compression.");
    }
  }

  private static byte[] readRequired(ZipFile zip, String name, long[] total)
      throws IOException, ReaderImportPipeline.ImportException
  {
    ZipEntry entry = zip.getEntry(name);
    if (entry == null || entry.isDirectory())
      throw unsupported("The EPUB is missing required content.");
    return readEntry(zip, entry, total);
  }

  private static byte[] readEntry(ZipFile zip, ZipEntry entry, long[] total)
      throws IOException, ReaderImportPipeline.ImportException
  {
    try (InputStream input = zip.getInputStream(entry))
    {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      byte[] buffer = new byte[8192];
      int entryTotal = 0;
      int read;
      while ((read = input.read(buffer)) != -1)
      {
        entryTotal += read;
        total[0] += read;
        if (entryTotal > MAX_ENTRY_BYTES || total[0] > MAX_TOTAL_BYTES)
          throw unsupported("The EPUB expands beyond the safe import limit.");
        output.write(buffer, 0, read);
      }
      return output.toByteArray();
    }
  }

  private static org.w3c.dom.Document parseXml(byte[] bytes)
      throws Exception
  {
    if (containsDoctype(bytes))
      throw unsupported("The EPUB contains unsafe XML declarations.");
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setExpandEntityReferences(false);
    return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
  }

  private static boolean containsDoctype(byte[] bytes)
  {
    if (containsEncodedMarker(bytes, 1, 0))
      return true;
    for (int offset = 0; offset < 2; offset++)
      if (containsEncodedMarker(bytes, 2, offset))
        return true;
    for (int offset = 0; offset < 4; offset++)
      if (containsEncodedMarker(bytes, 4, offset))
        return true;
    return false;
  }

  private static boolean containsEncodedMarker(byte[] bytes, int stride,
      int characterOffset)
  {
    int encodedLength = DOCTYPE_DECLARATION.length() * stride;
    for (int start = 0; start + encodedLength <= bytes.length; start++)
    {
      boolean matches = true;
      for (int index = 0; index < DOCTYPE_DECLARATION.length(); index++)
      {
        int group = start + index * stride;
        for (int byteOffset = 0; byteOffset < stride; byteOffset++)
        {
          int expected = byteOffset == characterOffset
            ? DOCTYPE_DECLARATION.charAt(index) : 0;
          if ((bytes[group + byteOffset] & 0xff) != expected)
          {
            matches = false;
            break;
          }
        }
        if (!matches)
          break;
      }
      if (matches)
        return true;
    }
    return false;
  }

  private static String resolvePath(String packagePath, String href)
      throws Exception
  {
    int fragment = href.indexOf('#');
    String value = fragment < 0 ? href : href.substring(0, fragment);
    URI base = new URI("file", null, "/" + packagePath, null);
    String path = base.resolve(value).normalize().getPath();
    if (path == null || !path.startsWith("/"))
      throw unsupported("The EPUB contains an unsafe content path.");
    path = path.substring(1);
    requireSafePath(path);
    return path;
  }

  private static void requireSafePath(String path)
      throws ReaderImportPipeline.ImportException
  {
    if (path == null || path.isEmpty() || path.startsWith("/") ||
        path.startsWith("\\") || path.contains("\\") ||
        path.equals("..") || path.startsWith("../") || path.contains("/../"))
      throw unsupported("The EPUB contains an unsafe content path.");
  }

  private static String firstText(org.w3c.dom.Document document,
      String localName)
  {
    NodeList values = document.getElementsByTagNameNS("*", localName);
    return values.getLength() == 0 ? null :
      values.item(0).getTextContent().trim();
  }

  private static ReaderImportPipeline.ImportException unsupported(
      String message)
  {
    return new ReaderImportPipeline.ImportException(message);
  }

  private static boolean empty(String value)
  {
    return value == null || value.trim().isEmpty();
  }

  private static String emptyToNull(String value)
  {
    return empty(value) ? null : value.trim();
  }
}
