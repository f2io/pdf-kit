package io.pdfkit.finder;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

class COSPdfFinderTest {

  @Test
  void findsPlaceholdersInFixturePdf() throws IOException, URISyntaxException {
    Path pdf = Paths.get(resource("sample.pdf").toURI());

    List<Placeholder> placeholders = new COSPdfFinder().findPlaceholders(pdf);

    assertEquals(
        List.of(
            new Placeholder("customerName", 1),
            new Placeholder("orderId", 1),
            new Placeholder("totalAmount", 1),
            new Placeholder("shippingAddress", 2),
            new Placeholder("deliveryDate", 2)),
        placeholders);
  }

  @Test
  void findsOnlyContentStreamPlaceholdersInAnnuityFormFixture()
      throws IOException, URISyntaxException {
    // Real-world fixture: a "Fixed Annuity Application" mixing static
    // boilerplate text with 7 fillable AcroForm fields. COSPdfFinder only
    // sees the 2 placeholders drawn as page text, plus "policyNumber"
    // (which also appears a second time in the static disclaimer line) -
    // the placeholders living solely inside form field values are missed.
    Path pdf = Paths.get(resource("annuity-form.pdf").toURI());

    List<Placeholder> placeholders = new COSPdfFinder().findPlaceholders(pdf);

    assertEquals(
        List.of(
            new Placeholder("agentName", 1),
            new Placeholder("preparedDate", 1),
            new Placeholder("policyNumber", 1)),
        placeholders);
  }

  @Test
  void findsOnlyContentStreamPlaceholdersInTransferFormFixture()
      throws IOException, URISyntaxException {
    // Synthetic fixture modeled on a typical carrier transfer/exchange
    // request form: text fields (some pre-filled, some blank), a checkbox,
    // and a radio-style choice field, plus static text placeholders.
    // COSPdfFinder still only sees the 3 placeholders drawn as page text -
    // the 5 living inside text field values are missed, and the checkbox
    // / radio fields have no placeholder text at all (state-based, not
    // string-based), so patching those would need a different mechanism
    // entirely rather than an extension of this regex-based approach.
    Path pdf = Paths.get(resource("transfer-form.pdf").toURI());

    List<Placeholder> placeholders = new COSPdfFinder().findPlaceholders(pdf);

    assertEquals(
        List.of(
            new Placeholder("agentName", 1),
            new Placeholder("preparedDate", 1),
            new Placeholder("contractNumber", 2)),
        placeholders);
  }

  @Test
  void findsOnlyContentStreamPlaceholdersInContractInfoFormFixture()
      throws IOException, URISyntaxException {
    // Synthetic fixture modeled on a John Hancock-style "contract
    // information" page: owner and co-owner AcroForm sections (name,
    // DOB, phone, email, address, a checkbox) plus static boilerplate
    // text. COSPdfFinder still only sees the 3 placeholders drawn as page
    // text - the owner/co-owner name placeholders living inside text
    // field values are missed, just like the other AcroForm fixtures.
    Path pdf = Paths.get(resource("contract-info-form.pdf").toURI());

    List<Placeholder> placeholders = new COSPdfFinder().findPlaceholders(pdf);

    assertEquals(
        List.of(
            new Placeholder("agentName", 1),
            new Placeholder("preparedDate", 1),
            new Placeholder("contractNumber", 1)),
        placeholders);
  }

  @Test
  void findsPlaceholdersInPdfText(@TempDir Path tempDir) throws IOException {
    Path pdf = tempDir.resolve("sample.pdf");
    writeSamplePdf(pdf, "Hello ${name}, your order ${orderId} is ready.");

    List<Placeholder> placeholders = new COSPdfFinder().findPlaceholders(pdf);

    assertEquals(List.of(new Placeholder("name", 1), new Placeholder("orderId", 1)), placeholders);
  }

  @Test
  void returnsEmptyListWhenNoPlaceholdersPresent(@TempDir Path tempDir) throws IOException {
    Path pdf = tempDir.resolve("plain.pdf");
    writeSamplePdf(pdf, "No placeholders here.");

    List<Placeholder> placeholders = new COSPdfFinder().findPlaceholders(pdf);

    assertEquals(List.of(), placeholders);
  }

  @Test
  void doesNotFindPlaceholdersSplitAcrossContentStreamRuns(@TempDir Path tempDir)
      throws IOException {
    // Some PDF generators emit a single visual word as multiple Tj/TJ runs
    // (e.g. kerning-adjusted TJ arrays). PDFTextStripper would still join
    // "${na" + "me}" into "${name}" when reading the page as text, but
    // PdfPatcher can only patch a placeholder that lives inside one run -
    // so COSPdfFinder must not report a match here either.
    Path pdf = tempDir.resolve("split.pdf");
    writeSplitRunPdf(pdf, "Hello ${na", "me}, welcome.");

    List<Placeholder> placeholders = new COSPdfFinder().findPlaceholders(pdf);

    assertEquals(List.of(), placeholders);
  }

  @Test
  void doesNotFindPlaceholdersLivingInAcroFormFieldValues(@TempDir Path tempDir)
      throws IOException {
    // Real company templates are often an AcroForm: static boilerplate text
    // plus fillable fields (name, date, amount, ...) whose value is set via
    // the field's /V entry, not drawn with Tj/TJ. COSPdfFinder only scans page
    // content streams, so it can see the boilerplate placeholder but is
    // blind to one that lives solely inside a form field's value.
    Path pdf = tempDir.resolve("form.pdf");
    writeAcroFormPdf(pdf, "Prepared on ${date}", "customerName", "${customerName}");

    List<Placeholder> placeholders = new COSPdfFinder().findPlaceholders(pdf);

    assertEquals(List.of(new Placeholder("date", 1)), placeholders);
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

  private void writeSplitRunPdf(Path path, String firstRun, String secondRun) throws IOException {
    try (PDDocument document = new PDDocument()) {
      PDPage page = new PDPage();
      document.addPage(page);

      try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
        stream.beginText();
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        stream.newLineAtOffset(50, 700);
        stream.showTextWithPositioning(new Object[] {firstRun, -100f, secondRun});
        stream.endText();
      }

      document.save(path.toFile());
    }
  }

  private void writeSamplePdf(Path path, String text) throws IOException {
    try (PDDocument document = new PDDocument()) {
      PDPage page = new PDPage();
      document.addPage(page);

      try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
        stream.beginText();
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        stream.newLineAtOffset(50, 700);
        stream.showText(text);
        stream.endText();
      }

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
