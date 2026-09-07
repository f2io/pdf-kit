package io.pdfkit.finder;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Regenerates contract-info-form.pdf, the fixture shared by pdf-finder and pdf-patcher: a black
 * section-header bar, underlined fields with small labels beneath them, and grouped owner/co-owner
 * sections - mirroring the John Hancock "contract information" page example (without the branded
 * carrier name).
 *
 * <p>Not a normal test: run generate() manually from the IDE (module working directory pdf-finder/)
 * after changing the layout below, then commit the regenerated PDF in both modules'
 * src/test/resources/.
 */
@Disabled
class GenerateContractInfoFormFixture {

  private static final float MARGIN = 40;
  private static final float PAGE_WIDTH = 792;
  private static final float PAGE_HEIGHT = 660;
  private static final PDFont LABEL_FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
  private static final PDFont HEADER_FONT =
      new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

  @Test
  void generate() throws IOException {
    try (PDDocument document = new PDDocument()) {
      PDPage page = new PDPage(new PDRectangle(PAGE_WIDTH, PAGE_HEIGHT));
      document.addPage(page);

      PDAcroForm acroForm = new PDAcroForm(document);
      document.getDocumentCatalog().setAcroForm(acroForm);
      PDResources resources = new PDResources();
      resources.put(COSName.getPDFName("Helv"), LABEL_FONT);
      acroForm.setDefaultResources(resources);

      try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
        float y = PAGE_HEIGHT - 40;

        y = sectionHeader(stream, y, "1. Contract information");
        y -= 14;

        stream.beginText();
        stream.setFont(LABEL_FONT, 9);
        stream.newLineAtOffset(MARGIN, y);
        stream.showText("Prepared by ${agentName} on ${preparedDate}");
        stream.endText();
        y -= 22;

        y = field(stream, acroForm, page, y, "contractNumber", "Contract number", 40, 712, null);
        y -= 10;

        stream.beginText();
        stream.setFont(HEADER_FONT, 11);
        stream.newLineAtOffset(MARGIN, y);
        stream.showText("Owner information:");
        stream.endText();
        y -= 22;

        y = ownerRows(stream, acroForm, page, y, "owner");

        y =
            checkboxRow(
                stream,
                acroForm,
                page,
                y,
                "permanentAddressChange",
                "Check here if address provided is permanent address change for your annuity contracts.");
        y -= 6;

        stream.beginText();
        stream.setFont(HEADER_FONT, 11);
        stream.newLineAtOffset(MARGIN, y);
        stream.showText("Co-owner information (if applicable):");
        stream.endText();
        y -= 22;

        y = ownerRows(stream, acroForm, page, y, "coOwner");
        y -= 20;

        signatureField(
            stream,
            acroForm,
            page,
            y,
            "ownerSignature",
            "Owner signature",
            "${sign:owner}",
            40,
            300,
            30);
        y =
            signatureField(
                stream,
                acroForm,
                page,
                y,
                "coOwnerSignature",
                "Co-owner signature",
                "${sign:coOwner}",
                372,
                300,
                30);
        y -= 6;

        stream.beginText();
        stream.setFont(LABEL_FONT, 9);
        stream.newLineAtOffset(MARGIN, y);
        stream.showText("Owner authorizes updates to contract ${contractNumber}.");
        stream.endText();
      }

      for (Path output : outputPaths()) {
        document.save(output.toFile());
        System.out.println("Wrote " + output.toAbsolutePath());
      }
    }
  }

  private static java.util.List<Path> outputPaths() {
    Path repoRoot = Path.of("..").normalize();
    return java.util.List.of(
        repoRoot.resolve("pdf-finder/src/test/resources/contract-info-form.pdf"),
        repoRoot.resolve("pdf-patcher/src/test/resources/contract-info-form.pdf"));
  }

  private static float ownerRows(
      PDPageContentStream stream, PDAcroForm acroForm, PDPage page, float y, String prefix)
      throws IOException {
    y =
        row(
            stream,
            acroForm,
            page,
            y,
            col(prefix + "FirstName", "Owner name (First)", 40, 220, "${" + prefix + "FirstName}"),
            col(prefix + "MiddleInitial", "MI", 275, 40, null),
            col(prefix + "LastName", "Last", 330, 220, "${" + prefix + "LastName}"),
            col(prefix + "DateOfBirth", "Date of birth (mm/dd/yyyy)", 565, 130, null));

    y =
        row(
            stream,
            acroForm,
            page,
            y,
            col(prefix + "Phone", "Phone number", 40, 200, null),
            col(prefix + "Email", "Email address", 255, 430, null));

    y = field(stream, acroForm, page, y, prefix + "Street", "Address (Street)", 40, 645, null);

    y =
        row(
            stream,
            acroForm,
            page,
            y,
            col(prefix + "City", "City", 40, 200, null),
            col(prefix + "State", "State", 255, 200, null),
            col(prefix + "Zip", "Zip code", 470, 120, null),
            col(prefix + "Country", "Country (if outside the U.S.)", 605, 95, null));

    return y;
  }

  private record Col(String name, String label, float x, float width, String value) {}

  private static Col col(String name, String label, float x, float width, String value) {
    return new Col(name, label, x, width, value);
  }

  private static float row(
      PDPageContentStream stream, PDAcroForm acroForm, PDPage page, float y, Col... cols)
      throws IOException {
    for (Col c : cols) {
      drawLine(stream, c.x, y, c.x + c.width);
      drawLabel(stream, c.x, y, c.label);
      addTextField(acroForm, page, c.name, c.value == null ? "" : c.value, y, c.x, c.width);
    }
    return y - 40;
  }

  private static float field(
      PDPageContentStream stream,
      PDAcroForm acroForm,
      PDPage page,
      float y,
      String name,
      String label,
      float x,
      float width,
      String value)
      throws IOException {
    drawLine(stream, x, y, x + width);
    drawLabel(stream, x, y, label);
    addTextField(acroForm, page, name, value == null ? "" : value, y, x, width);
    return y - 40;
  }

  private static float checkboxRow(
      PDPageContentStream stream,
      PDAcroForm acroForm,
      PDPage page,
      float y,
      String name,
      String label)
      throws IOException {
    float boxSize = 10;
    stream.setLineWidth(1);
    stream.addRect(MARGIN, y, boxSize, boxSize);
    stream.stroke();

    stream.beginText();
    stream.setFont(LABEL_FONT, 9);
    stream.newLineAtOffset(MARGIN + boxSize + 8, y + 1);
    stream.showText(label);
    stream.endText();

    PDCheckBox checkBox = new PDCheckBox(acroForm);
    checkBox.setPartialName(name);
    acroForm.getFields().add(checkBox);

    PDAnnotationWidget widget = checkBox.getWidgets().get(0);
    widget.setRectangle(new PDRectangle(MARGIN, y, boxSize, boxSize));
    widget.setPage(page);
    page.getAnnotations().add(widget);

    return y - 28;
  }

  private static void drawLine(PDPageContentStream stream, float x1, float y, float x2)
      throws IOException {
    stream.setLineWidth(0.75f);
    stream.setStrokingColor(Color.BLACK);
    stream.moveTo(x1, y);
    stream.lineTo(x2, y);
    stream.stroke();
  }

  private static void drawLabel(PDPageContentStream stream, float x, float y, String text)
      throws IOException {
    stream.beginText();
    stream.setFont(LABEL_FONT, 8);
    stream.newLineAtOffset(x, y - 10);
    stream.showText(text);
    stream.endText();
  }

  private static float sectionHeader(PDPageContentStream stream, float y, String text)
      throws IOException {
    stream.setNonStrokingColor(Color.BLACK);
    stream.addRect(MARGIN, y - 4, PAGE_WIDTH - 2 * MARGIN, 24);
    stream.fill();

    stream.setNonStrokingColor(Color.WHITE);
    stream.beginText();
    stream.setFont(HEADER_FONT, 13);
    stream.newLineAtOffset(MARGIN + 10, y + 3);
    stream.showText(text);
    stream.endText();
    stream.setNonStrokingColor(Color.BLACK);

    return y - 24;
  }

  private static float signatureField(
      PDPageContentStream stream,
      PDAcroForm acroForm,
      PDPage page,
      float y,
      String name,
      String label,
      String value,
      float x,
      float width,
      float boxHeight)
      throws IOException {
    drawLine(stream, x, y, x + width);
    drawLabel(stream, x, y, label);

    PDTextField field = new PDTextField(acroForm);
    field.setPartialName(name);
    field.setDefaultAppearance("/Helv 9 Tf 0 g");
    acroForm.getFields().add(field);

    PDAnnotationWidget widget = field.getWidgets().get(0);
    widget.setRectangle(new PDRectangle(x, y + 2, width, boxHeight));
    widget.setPage(page);
    page.getAnnotations().add(widget);

    field.setValue(value);

    return y - 40;
  }

  private static void addTextField(
      PDAcroForm acroForm, PDPage page, String name, String value, float y, float x, float width)
      throws IOException {
    PDTextField field = new PDTextField(acroForm);
    field.setPartialName(name);
    field.setDefaultAppearance("/Helv 9 Tf 0 g");
    acroForm.getFields().add(field);

    PDAnnotationWidget widget = field.getWidgets().get(0);
    widget.setRectangle(new PDRectangle(x, y + 2, width, 14));
    widget.setPage(page);
    page.getAnnotations().add(widget);

    if (!value.isEmpty()) {
      field.setValue(value);
    }
  }
}
