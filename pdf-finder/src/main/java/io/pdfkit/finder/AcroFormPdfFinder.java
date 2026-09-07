package io.pdfkit.finder;

import static io.pdfkit.finder.PlaceholderPattern.PLACEHOLDER_PATTERN;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;

/**
 * Counterpart to COSPdfFinder that looks for placeholders inside AcroForm field values instead of
 * page content streams - the exact spots COSPdfFinder is blind to. Classification into TEXT vs
 * IMAGE (the "${sign:key}" rule) is PlaceholderPattern's job, not this class's.
 */
public class AcroFormPdfFinder implements PlaceholderFinder {

  @Override
  public List<Placeholder> findPlaceholders(Path pdf) throws IOException {
    try (PDDocument document =
        Loader.loadPDF(pdf.toFile(), IOUtils.createTempFileOnlyStreamCache())) {
      List<Placeholder> placeholders = new ArrayList<>();
      PDAcroForm acroForm = document.getDocumentCatalog().getAcroForm();
      if (acroForm == null) {
        return placeholders;
      }

      for (PDField field : acroForm.getFieldTree()) {
        if (field.getWidgets().isEmpty()) {
          continue;
        }
        String value = field.getValueAsString();
        if (value == null || value.isEmpty()) {
          continue;
        }
        int pageNumber = pageNumberOf(document, field.getWidgets().get(0));
        collect(value, pageNumber, placeholders);
      }
      return placeholders;
    }
  }

  private int pageNumberOf(PDDocument document, PDAnnotationWidget widget) {
    PDPage widgetPage = widget.getPage();
    int pageNumber = 0;
    for (PDPage page : document.getPages()) {
      pageNumber++;
      if (widgetPage != null && page.getCOSObject().equals(widgetPage.getCOSObject())) {
        return pageNumber;
      }
    }
    return 0;
  }

  private void collect(String value, int pageNumber, List<Placeholder> placeholders) {
    Matcher matcher = PLACEHOLDER_PATTERN.matcher(value);
    while (matcher.find()) {
      placeholders.add(PlaceholderPattern.classify(matcher.group(1).trim(), pageNumber));
    }
  }
}
