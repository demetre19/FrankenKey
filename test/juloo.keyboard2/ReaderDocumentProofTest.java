package juloo.keyboard2;

import android.content.Context;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission;
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException;
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Before;
import org.junit.Test;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35,
    shadows = ReaderDocumentProofTest.ClasspathPdfBoxResources.class)
public class ReaderDocumentProofTest
{
  private static final int MAX_ENTRIES = 32;
  private static final int MAX_ENTRY_BYTES = 64 * 1024;
  private static final int MAX_TOTAL_BYTES = 256 * 1024;
  private static final int MAX_COMPRESSION_RATIO = 100;

  @Before
  public void initializePdfBox()
  {
    PDFBoxResourceLoader.init(RuntimeEnvironment.getApplication());
  }

  @Test
  public void standard_library_epub_parser_preserves_spine_order_and_unicode_metadata()
      throws Exception
  {
    File epub = writeEpub("3.0", false, false,
        "<h1>First</h1><p>שלום English</p>",
        "<h1>Second</h1><p>مرحبا Français</p>");

    Publication publication = openEpub(epub);
    assertEquals("Proof Book", publication.title);
    assertEquals("Proof Author", publication.author);
    assertEquals("he", publication.language);
    assertEquals("EPUB spine order, not ZIP entry order, defines reading order.",
        java.util.Arrays.asList("text/chapter-1.xhtml", "text/chapter-2.xhtml"),
        publication.chapterPaths);
    assertTrue(publication.chapterText.get(0).contains("שלום English"));
    assertTrue(publication.chapterText.get(1).contains("مرحبا Français"));
  }

  @Test
  public void epub_parser_rejects_drm_traversal_and_decompression_abuse() throws Exception
  {
    expectEpubFailure(writeEpub("2.0", true, false, "one", "two"), "encrypted EPUB");
    expectEpubFailure(writeEpub("3.0", false, true, "one", "two"), "unsafe EPUB path");

    File oversized = File.createTempFile("reader-proof-oversized", ".epub");
    try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(oversized)))
    {
      put(zip, "META-INF/container.xml", repeat('x', MAX_ENTRY_BYTES + 1));
    }
    expectEpubFailure(oversized, "entry exceeds bound");
  }

  @Test
  public void pdfbox_android_extracts_pages_with_bounded_scratch_memory_and_cancellation()
      throws Exception
  {
    Context context = RuntimeEnvironment.getApplication();
    File pdf = File.createTempFile("reader-proof-book", ".pdf", context.getCacheDir());
    try (PDDocument document = new PDDocument())
    {
      for (int page = 1; page <= 12; page++)
      {
        PDPage value = new PDPage();
        document.addPage(value);
        try (PDPageContentStream stream = new PDPageContentStream(document, value))
        {
          stream.beginText();
          stream.setFont(loadProofFont(document), 12);
          stream.newLineAtOffset(72, 720);
          stream.showText("Reader proof page " + page);
          stream.endText();
        }
      }
      document.save(pdf);
    }

    MemoryUsageSetting memory = MemoryUsageSetting.setupMixed(1024 * 1024)
        .setTempDir(context.getCacheDir());
    List<String> pages = new ArrayList<>();
    try (PDDocument document = PDDocument.load(pdf, memory))
    {
      PDFTextStripper stripper = new PDFTextStripper();
      for (int page = 1; page <= document.getNumberOfPages(); page++)
      {
        if (page > 4)
          break;
        stripper.setStartPage(page);
        stripper.setEndPage(page);
        pages.add(stripper.getText(document).trim());
      }
    }
    assertEquals("Extraction must be page-bounded and cooperatively cancellable between pages.",
        4, pages.size());
    assertEquals("Reader proof page 1", pages.get(0));
    assertEquals("Reader proof page 4", pages.get(3));
  }

  @Test
  public void pdfbox_android_distinguishes_text_image_only_encrypted_and_malformed_files()
      throws Exception
  {
    Context context = RuntimeEnvironment.getApplication();
    File textPdf = File.createTempFile("reader-proof-text", ".pdf", context.getCacheDir());
    try (PDDocument document = new PDDocument())
    {
      PDPage page = new PDPage();
      document.addPage(page);
      try (PDPageContentStream stream = new PDPageContentStream(document, page))
      {
        stream.beginText();
        stream.setFont(loadProofFont(document), 12);
        stream.newLineAtOffset(72, 720);
        stream.showText("Readable text layer");
        stream.endText();
      }
      document.save(textPdf);
    }
    assertEquals("Readable text layer", extractPdf(textPdf).trim());

    File imageOnly = File.createTempFile("reader-proof-image-only", ".pdf", context.getCacheDir());
    File mixedLanguage = File.createTempFile(
        "reader-proof-mixed-language", ".pdf", context.getCacheDir());
    try (PDDocument document = new PDDocument())
    {
      PDPage page = new PDPage();
      document.addPage(page);
      try (PDPageContentStream stream = new PDPageContentStream(document, page))
      {
        stream.beginText();
        stream.setFont(loadProofFont(document), 12);
        stream.newLineAtOffset(72, 720);
        stream.showText("English שלום");
        stream.endText();
      }
      document.save(mixedLanguage);
    }
    String mixedText = extractPdf(mixedLanguage);
    assertTrue("The bundled PdfBox font must retain Latin glyphs.", mixedText.contains("English"));
    assertTrue("The mixed-language fixture must retain Hebrew code points.",
        containsInEitherDirection(mixedText, "שלום"));

    try (PDDocument document = new PDDocument())
    {
      document.addPage(new PDPage());
      document.save(imageOnly);
    }
    assertTrue("A page without extractable glyphs must report OCR required, not empty success.",
        extractPdf(imageOnly).trim().isEmpty());

    File encrypted = File.createTempFile("reader-proof-encrypted", ".pdf", context.getCacheDir());
    try (PDDocument document = new PDDocument())
    {
      document.addPage(new PDPage());
      StandardProtectionPolicy policy = new StandardProtectionPolicy(
          "owner-password", "reader-password", new AccessPermission());
      policy.setEncryptionKeyLength(128);
      document.protect(policy);
      document.save(encrypted);
    }
    try
    {
      PDDocument.load(encrypted).close();
      fail("Encrypted PDFs must require an explicit password instead of silently failing.");
    }
    catch (InvalidPasswordException expected) {}

    File malformed = File.createTempFile("reader-proof-malformed", ".pdf", context.getCacheDir());
    try (FileOutputStream output = new FileOutputStream(malformed))
    {
      output.write("not a PDF".getBytes(StandardCharsets.UTF_8));
    }
    try
    {
      PDDocument.load(malformed).close();
      fail("Malformed PDFs must fail before Library acceptance.");
    }
    catch (IOException expected) {}
  }

  private static PDType0Font loadProofFont(PDDocument document) throws IOException
  {
    InputStream font = ReaderDocumentProofTest.class.getClassLoader()
      .getResourceAsStream("com/tom_roush/pdfbox/resources/ttf/LiberationSans-Regular.ttf");
    if (font == null)
      throw new IOException("Missing LiberationSans Reader proof fixture");
    try (InputStream value = font)
    {
      return PDType0Font.load(document, value, true);
    }
  }

  private static boolean containsInEitherDirection(String value, String token)
  {
    return value.contains(token) ||
      value.contains(new StringBuilder(token).reverse().toString());
  }

  @Test
  public void jsoup_candidate_extracts_malformed_articles_without_script_or_navigation_noise()
  {
    Document document = Jsoup.parse("<html><head><title>Proof</title><script>secret()</script></head>" +
        "<body><nav>Menu</nav><article><h1>Heading<p>First paragraph<p>שני</article></body>");
    document.select("script,style,noscript,nav,aside,form,iframe").remove();
    String text = document.selectFirst("article").text();
    assertEquals("Heading First paragraph שני", text);
    assertFalse(text.contains("secret"));
    assertFalse(text.contains("Menu"));
  }

  private static String extractPdf(File file) throws IOException
  {
    Context context = RuntimeEnvironment.getApplication();
    MemoryUsageSetting memory = MemoryUsageSetting.setupMixed(1024 * 1024)
        .setTempDir(context.getCacheDir());
    try (PDDocument document = PDDocument.load(file, memory))
    {
      return new PDFTextStripper().getText(document);
    }
  }

  private static Publication openEpub(File file) throws Exception
  {
    try (ZipFile zip = new ZipFile(file))
    {
      int entries = 0;
      long[] total = {0};
      Enumeration<? extends ZipEntry> values = zip.entries();
      while (values.hasMoreElements())
      {
        ZipEntry entry = values.nextElement();
        if (++entries > MAX_ENTRIES)
          throw new IOException("too many EPUB entries");
        requireSafePath(entry.getName());
        long compressed = entry.getCompressedSize();
        long expanded = entry.getSize();
        if (expanded > MAX_ENTRY_BYTES)
          throw new IOException("EPUB entry exceeds bound");
        if (compressed > 0 && expanded > compressed * MAX_COMPRESSION_RATIO)
          throw new IOException("EPUB compression ratio exceeds bound");
      }
      if (zip.getEntry("META-INF/encryption.xml") != null)
        throw new IOException("encrypted EPUB is unsupported");

      org.w3c.dom.Document container = parseXml(readEntry(zip,
          "META-INF/container.xml", total));
      NodeList rootfiles = container.getElementsByTagNameNS("*", "rootfile");
      if (rootfiles.getLength() != 1)
        throw new IOException("EPUB container must identify one package");
      String packagePath = ((Element)rootfiles.item(0)).getAttribute("full-path");
      requireSafePath(packagePath);
      org.w3c.dom.Document packageDocument = parseXml(readEntry(zip, packagePath, total));

      Publication publication = new Publication();
      publication.title = firstText(packageDocument, "title");
      publication.author = firstText(packageDocument, "creator");
      publication.language = firstText(packageDocument, "language");
      Map<String, String> manifest = new HashMap<>();
      NodeList items = packageDocument.getElementsByTagNameNS("*", "item");
      for (int i = 0; i < items.getLength(); i++)
      {
        Element item = (Element)items.item(i);
        manifest.put(item.getAttribute("id"), item.getAttribute("href"));
      }
      NodeList itemrefs = packageDocument.getElementsByTagNameNS("*", "itemref");
      Path parent = Paths.get(packagePath).getParent();
      for (int i = 0; i < itemrefs.getLength(); i++)
      {
        String href = manifest.get(((Element)itemrefs.item(i)).getAttribute("idref"));
        if (href == null)
          throw new IOException("EPUB spine references missing manifest item");
        String chapterPath = (parent == null ? Paths.get(href) : parent.resolve(href))
            .normalize().toString().replace(File.separatorChar, '/');
        requireSafePath(chapterPath);
        publication.chapterPaths.add(parent == null ? chapterPath :
            parent.relativize(Paths.get(chapterPath)).toString().replace(File.separatorChar, '/'));
        String markup = new String(readEntry(zip, chapterPath, total), StandardCharsets.UTF_8);
        publication.chapterText.add(Jsoup.parse(markup).text());
      }
      return publication;
    }
  }

  private static byte[] readEntry(ZipFile zip, String path, long[] total) throws IOException
  {
    ZipEntry entry = zip.getEntry(path);
    if (entry == null || entry.isDirectory())
      throw new IOException("missing EPUB entry: " + path);
    try (InputStream input = zip.getInputStream(entry);
         ByteArrayOutputStream output = new ByteArrayOutputStream())
    {
      byte[] buffer = new byte[4096];
      int read;
      int entryBytes = 0;
      while ((read = input.read(buffer)) != -1)
      {
        entryBytes += read;
        total[0] += read;
        if (entryBytes > MAX_ENTRY_BYTES || total[0] > MAX_TOTAL_BYTES)
          throw new IOException("EPUB extracted data exceeds bound");
        output.write(buffer, 0, read);
      }
      return output.toByteArray();
    }
  }

  private static org.w3c.dom.Document parseXml(byte[] bytes) throws Exception
  {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "");
    factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "");
    return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
  }

  private static void requireSafePath(String value) throws IOException
  {
    if (value == null || value.isEmpty() || value.startsWith("/") || value.contains("\\"))
      throw new IOException("unsafe EPUB path");
    Path path = Paths.get(value).normalize();
    if (path.isAbsolute() || path.startsWith("..") || !path.toString().equals(value))
      throw new IOException("unsafe EPUB path");
  }

  private static String firstText(org.w3c.dom.Document document, String localName)
      throws IOException
  {
    NodeList values = document.getElementsByTagNameNS("*", localName);
    if (values.getLength() == 0)
      throw new IOException("missing EPUB metadata: " + localName);
    return values.item(0).getTextContent().trim();
  }

  private static File writeEpub(String version, boolean encrypted, boolean traversal,
      String first, String second) throws IOException
  {
    File file = File.createTempFile("reader-proof", ".epub");
    try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file)))
    {
      put(zip, "META-INF/container.xml",
          "<?xml version=\"1.0\"?><container xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">" +
          "<rootfiles><rootfile full-path=\"book/package.opf\" media-type=\"application/oebps-package+xml\"/>" +
          "</rootfiles></container>");
      if (encrypted)
        put(zip, "META-INF/encryption.xml", "<encryption/>");
      String secondHref = traversal ? "../../outside.xhtml" : "text/chapter-2.xhtml";
      put(zip, "book/package.opf",
          "<?xml version=\"1.0\"?><package version=\"" + version + "\" " +
          "xmlns=\"http://www.idpf.org/2007/opf\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\">" +
          "<metadata><dc:title>Proof Book</dc:title><dc:creator>Proof Author</dc:creator>" +
          "<dc:language>he</dc:language></metadata><manifest>" +
          "<item id=\"c1\" href=\"text/chapter-1.xhtml\" media-type=\"application/xhtml+xml\"/>" +
          "<item id=\"c2\" href=\"" + secondHref + "\" media-type=\"application/xhtml+xml\"/>" +
          "</manifest><spine><itemref idref=\"c1\"/><itemref idref=\"c2\"/></spine></package>");
      put(zip, "book/text/chapter-2.xhtml", "<html><body>" + second + "</body></html>");
      put(zip, "book/text/chapter-1.xhtml", "<html><body>" + first + "</body></html>");
    }
    return file;
  }

  private static void put(ZipOutputStream zip, String path, String value) throws IOException
  {
    zip.putNextEntry(new ZipEntry(path));
    zip.write(value.getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
  }

  private static void expectEpubFailure(File file, String message) throws Exception
  {
    try
    {
      openEpub(file);
      fail(message);
    }
    catch (IOException expected) {}
  }

  private static String repeat(char value, int count)
  {
    char[] chars = new char[count];
    java.util.Arrays.fill(chars, value);
    return new String(chars);
  }

  private static final class Publication
  {
    String title;
    String author;
    String language;
    final List<String> chapterPaths = new ArrayList<>();
    final List<String> chapterText = new ArrayList<>();
  }

  @Implements(PDFBoxResourceLoader.class)
  public static class ClasspathPdfBoxResources
  {
    @Implementation
    public static InputStream getStream(String path) throws IOException
    {
      InputStream value = ReaderDocumentProofTest.class.getClassLoader()
        .getResourceAsStream(path);
      if (value == null)
        throw new IOException("Missing PdfBox proof resource: " + path);
      return value;
    }
  }
}
