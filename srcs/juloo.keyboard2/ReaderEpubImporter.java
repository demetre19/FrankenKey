package juloo.keyboard2;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.parsers.DocumentBuilderFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.w3c.dom.NodeList;

/** Minimal non-DRM EPUB parser with bounded ZIP and spine traversal. */
final class ReaderEpubImporter
{
  private static final int MAX_ENTRIES = 2048;
  private static final int MAX_ENTRY_BYTES = 4 * 1024 * 1024;
  private static final int MAX_TOTAL_BYTES = 64 * 1024 * 1024;
  private static final int MAX_COMPRESSION_RATIO = 100;

  private ReaderEpubImporter() {}

  static ReaderImportPipeline.Candidate importFile(File file, String sourceUri,
      String fallbackTitle) throws ReaderImportPipeline.ImportException
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

      Map<String, String> manifest = new HashMap<>();
      NodeList items = publication.getElementsByTagNameNS("*", "item");
      for (int i = 0; i < items.getLength(); i++)
      {
        org.w3c.dom.Element item = (org.w3c.dom.Element)items.item(i);
        String id = item.getAttribute("id");
        String href = item.getAttribute("href");
        if (!id.isEmpty() && !href.isEmpty())
          manifest.put(id, resolvePath(packagePath, href));
      }

      ArrayList<ReaderLibrary.ContentUnit> units = new ArrayList<>();
      NodeList spine = publication.getElementsByTagNameNS("*", "itemref");
      for (int i = 0; i < spine.getLength(); i++)
      {
        String idref = ((org.w3c.dom.Element)spine.item(i))
          .getAttribute("idref");
        String path = manifest.get(idref);
        if (path == null)
          throw unsupported("The EPUB spine references missing content.");
        byte[] chapterBytes = readRequired(zip, path, expandedTotal);
        org.jsoup.nodes.Document chapter = Jsoup.parse(
            new ByteArrayInputStream(chapterBytes), null, "file:///" + path);
        chapter.select("script,style,noscript,nav,form,iframe").remove();
        String chapterText = ReaderLibrary.normalizeText(
            chapter.body() == null ? "" : chapter.body().wholeText());
        if (chapterText.isEmpty())
          continue;
        units.add(new ReaderLibrary.ContentUnit(units.size(), "chapter",
              chapterText, emptyToNull(language), path));
      }
      if (units.isEmpty())
        throw unsupported("The EPUB contains no readable spine text.");
      return new ReaderImportPipeline.Candidate(
          empty(title) ? fallbackTitle : title,
          ReaderLibrary.SourceType.EPUB, sourceUri, "application/epub+zip",
          emptyToNull(author), emptyToNull(language), units);
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
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",
        true);
    factory.setFeature(
        "http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature(
        "http://xml.org/sax/features/external-parameter-entities", false);
    return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
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
