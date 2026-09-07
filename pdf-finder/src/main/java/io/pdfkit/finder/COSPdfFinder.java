package io.pdfkit.finder;

import static io.pdfkit.finder.PlaceholderPattern.PLACEHOLDER_PATTERN;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

/**
 * Finds placeholders drawn as page content-stream text (Tj/TJ runs) - blind to placeholders living
 * only inside AcroForm field values, which is AcroFormPdfFinder's job.
 */
public class COSPdfFinder implements PlaceholderFinder {

  @Override
  public List<Placeholder> findPlaceholders(Path pdf) throws IOException {
    try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
      List<Placeholder> placeholders = new ArrayList<>();
      int pageNumber = 0;
      for (PDPage page : document.getPages()) {
        pageNumber++;
        placeholders.addAll(findPlaceholdersOnPage(page, pageNumber));
      }
      return placeholders;
    }
  }

  private List<Placeholder> findPlaceholdersOnPage(PDPage page, int pageNumber) throws IOException {
    List<Placeholder> placeholders = new ArrayList<>();
    for (Object token : new PDFStreamParser(page).parse()) {
      if (token instanceof COSString cosString) {
        collect(cosString, pageNumber, placeholders);
      } else if (token instanceof COSArray array) {
        for (var element : array) {
          if (element instanceof COSString cosString) {
            collect(cosString, pageNumber, placeholders);
          }
        }
      }
    }
    return placeholders;
  }

  private void collect(COSString cosString, int pageNumber, List<Placeholder> placeholders) {
    String text = new String(cosString.getBytes(), StandardCharsets.ISO_8859_1);
    Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
    while (matcher.find()) {
      placeholders.add(new Placeholder(matcher.group(1).trim(), pageNumber));
    }
  }
}
