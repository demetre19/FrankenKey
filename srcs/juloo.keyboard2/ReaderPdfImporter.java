package juloo.keyboard2;

import android.content.Context;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/** Extracts bounded text-layer PDF pages without attempting OCR. */
final class ReaderPdfImporter
{
  private static final int MAX_PAGES = 2000;
  private static final int MAX_PAGE_CHARACTERS = 250000;
  private static final int SCRATCH_MEMORY_BYTES = 2 * 1024 * 1024;

  static final class OcrRequiredException
      extends ReaderImportPipeline.ImportException
  {
    OcrRequiredException()
    {
      super("This PDF has no readable text layer. OCR is required.");
    }
  }

  private ReaderPdfImporter() {}

  static ReaderImportPipeline.Candidate importFile(Context context, File file,
      String sourceUri, String fallbackTitle)
      throws ReaderImportPipeline.ImportException
  {
    if (context == null || file == null || !file.isFile())
      throw new ReaderImportPipeline.ImportException(
          "The PDF file is unavailable.");
    PDFBoxResourceLoader.init(context.getApplicationContext());
    MemoryUsageSetting memory = MemoryUsageSetting
      .setupMixed(SCRATCH_MEMORY_BYTES).setTempDir(context.getCacheDir());
    try (PDDocument document = PDDocument.load(file, memory))
    {
      if (document.isEncrypted())
        throw new ReaderImportPipeline.ImportException(
            "Encrypted PDF files are not supported.");
      int pageCount = document.getNumberOfPages();
      if (pageCount <= 0)
        throw new ReaderImportPipeline.ImportException(
            "This PDF contains no pages.");
      if (pageCount > MAX_PAGES)
        throw new ReaderImportPipeline.ImportException(
            "This PDF has too many pages to import safely.");

      PDFTextStripper stripper = new PDFTextStripper();
      ArrayList<ReaderLibrary.ContentUnit> units = new ArrayList<>();
      int totalCharacters = 0;
      for (int page = 1; page <= pageCount; page++)
      {
        if (Thread.currentThread().isInterrupted())
          throw new ReaderImportPipeline.ImportException(
              "PDF import was cancelled.");
        stripper.setStartPage(page);
        stripper.setEndPage(page);
        String text = ReaderLibrary.normalizeText(stripper.getText(document));
        if (text.length() > MAX_PAGE_CHARACTERS)
          throw new ReaderImportPipeline.ImportException(
              "A PDF page is too large to import safely.");
        if (text.isEmpty())
          continue;
        totalCharacters += text.length();
        if (totalCharacters > ReaderImportPipeline.MAX_IMPORTED_TEXT_CHARACTERS)
          throw new ReaderImportPipeline.ImportException(
              "This PDF contains too much text to import safely.");
        units.add(new ReaderLibrary.ContentUnit(units.size(), "page", text,
              null, "page:" + page));
      }
      if (units.isEmpty())
        throw new OcrRequiredException();
      return new ReaderImportPipeline.Candidate(fallbackTitle,
          ReaderLibrary.SourceType.PDF, sourceUri, "application/pdf", null,
          null, units);
    }
    catch (InvalidPasswordException error)
    {
      throw new ReaderImportPipeline.ImportException(
          "Encrypted PDF files are not supported.", error);
    }
    catch (ReaderImportPipeline.ImportException error)
    {
      throw error;
    }
    catch (IOException | RuntimeException error)
    {
      throw new ReaderImportPipeline.ImportException(
          "This PDF is malformed or could not be read.", error);
    }
  }
}
