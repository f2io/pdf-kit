package io.pdfkit.finder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.pdfkit.finder.Placeholder.PlaceholderKind;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AcroFormPdfFinderTest {

  @Test
  void findsPlaceholdersLivingInAcroFormFieldValues(@TempDir Path tempDir) throws IOException {
    // Mirrors PdfFinder's blind spot: a placeholder living only in a form
    // field's value is exactly what AcroFormPdfFinder is meant to see -
    // the boilerplate page-text placeholder is the one it's blind to now.
    Path pdf = tempDir.resolve("form.pdf");
    writeAcroFormPdf(pdf, "Prepared on ${date}", "customerName", "${customerName}");

    List<Placeholder> placeholders = new AcroFormPdfFinder().findPlaceholders(pdf);

    assertEquals(List.of(new Placeholder("customerName", 1)), placeholders);
  }

  @Test
  void findsPlaceholdersInAcroFormFieldValuesInContractInfoFormFixture()
      throws IOException, URISyntaxException {
    // Real-world-shaped fixture: owner/co-owner name fields hold
    // "${...}" TEXT placeholders as their AcroForm value, invisible to
    // PdfFinder's content-stream scan. Blank fields and the checkbox
    // contribute nothing. The two "${sign:...}" fields come back
    // too, but as IMAGE-kind entries with the prefix stripped from the
    // key - one call, two kinds of result.
    Path pdf = Paths.get(resource("contract-info-form.pdf").toURI());

    List<Placeholder> placeholders = new AcroFormPdfFinder().findPlaceholders(pdf);

    assertEquals(
        List.of(
            new Placeholder("ownerFirstName", 1),
            new Placeholder("ownerLastName", 1),
            new Placeholder("coOwnerFirstName", 1),
            new Placeholder("coOwnerLastName", 1),
            new Placeholder("owner", 1, PlaceholderKind.IMAGE),
            new Placeholder("coOwner", 1, PlaceholderKind.IMAGE)),
        placeholders);
  }

  @Test
  void findsSignatureImageSlotsAsImageKindWithThePrefixStripped(@TempDir Path tempDir)
      throws IOException {
    // "${sign:owner}" looks like a normal "${key}" placeholder to
    // the regex, but AcroFormPdfFinder recognizes the prefix and reports
    // it as an IMAGE-kind placeholder keyed "owner" (prefix stripped) -
    // patchImages()'s job to resolve, not patch()'s.
    Path pdf = tempDir.resolve("form.pdf");
    writeAcroFormPdf(pdf, "Prepared on ${date}", "signatureField", "${sign:owner}");

    List<Placeholder> placeholders = new AcroFormPdfFinder().findPlaceholders(pdf);

    assertEquals(List.of(new Placeholder("owner", 1, PlaceholderKind.IMAGE)), placeholders);
  }

  private void writeAcroFormPdf(Path path, String staticText, String fieldName, String fieldValue)
      throws IOException {
    try (PDDocument document = new PDDocument()) {
      PDPage page = new PDPage();
      document.addPage(page);

      try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
        stream.beginText();
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        stream.newLineAtOffset(50, 750);
        stream.showText(staticText);
        stream.endText();
      }

      PDAcroForm acroForm = new PDAcroForm(document);
      document.getDocumentCatalog().setAcroForm(acroForm);

      PDResources resources = new PDResources();
      resources.put(
          COSName.getPDFName("Helv"), new PDType1Font(Standard14Fonts.FontName.HELVETICA));
      acroForm.setDefaultResources(resources);

      PDTextField field = new PDTextField(acroForm);
      field.setPartialName(fieldName);
      field.setDefaultAppearance("/Helv 12 Tf 0 g");
      acroForm.getFields().add(field);

      PDAnnotationWidget widget = field.getWidgets().get(0);
      widget.setRectangle(new PDRectangle(150, 700, 200, 20));
      widget.setPage(page);
      page.getAnnotations().add(widget);

      field.setValue(fieldValue);

      document.save(path.toFile());
    }
  }

  private java.net.URL resource(String name) {
    java.net.URL url = getClass().getClassLoader().getResource(name);
    if (url == null) {
      throw new UncheckedIOException(new IOException("Resource not found: " + name));
    }
    return url;
  }
}
