package io.pdfkit.finder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds placeholders wherever they live in a PDF. By default runs every PlaceholderFinder
 * implementation (page text and AcroForm field values - the latter reporting both TEXT and IMAGE
 * kinds) and merges their results; pass specific implementations to restrict it to just those.
 */
public class PdfFinder implements PlaceholderFinder {

  private final List<PlaceholderFinder> finders;

  public PdfFinder() {
    this(new COSPdfFinder(), new AcroFormPdfFinder());
  }

  public PdfFinder(PlaceholderFinder... finders) {
    this.finders = List.of(finders);
  }

  @Override
  public List<Placeholder> findPlaceholders(Path pdf) throws IOException {
    List<Placeholder> placeholders = new ArrayList<>();
    for (PlaceholderFinder finder : finders) {
      placeholders.addAll(finder.findPlaceholders(pdf));
    }
    return placeholders;
  }
}
