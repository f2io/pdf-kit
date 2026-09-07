package io.pdfkit.patcher;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDFormContentStream;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.util.Matrix;

/**
 * Shared PDF fixtures and readers for the PdfPatcher test classes: synthetic, single-purpose PDFs
 * built on the fly for one edge case each, plus the readers used to assert on patched output.
 */
final class TestPdfs {

  private static final Path TMP_DIR = Path.of(".tmp");

  private TestPdfs() {}

  static Path tmpOutput(String filename) throws IOException {
    Files.createDirectories(TMP_DIR);
    return TMP_DIR.resolve(filename);
  }

  static URL resource(String name) {
    URL url = TestPdfs.class.getClassLoader().getResource(name);
    if (url == null) {
      throw new UncheckedIOException(new IOException("Resource not found: " + name));
    }
    return url;
  }

  static String readText(Path pdf) throws IOException {
    try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
      return new PDFTextStripper().getText(document);
    }
  }

  static String readFieldValue(Path pdf, String fieldName) throws IOException {
    try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
      PDAcroForm acroForm = document.getDocumentCatalog().getAcroForm();
      return acroForm.getField(fieldName).getValueAsString();
    }
  }

  static Map<String, String> readAllFieldValues(Path pdf) throws IOException {
    try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
      PDAcroForm acroForm = document.getDocumentCatalog().getAcroForm();
      Map<String, String> values = new LinkedHashMap<>();
      for (PDField field : acroForm.getFieldTree()) {
        values.put(field.getFullyQualifiedName(), field.getValueAsString());
      }
      return values;
    }
  }

  static void writeSamplePdf(Path path, String text) throws IOException {
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

  static void writeSplitRunPdf(Path path, String firstRun, String secondRun) throws IOException {
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

  static void writeAcroFormPdf(Path path, String staticText, String fieldName, String fieldValue)
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

  static void writeFormXObjectPdf(Path path, String textInForm) throws IOException {
    try (PDDocument document = new PDDocument()) {
      PDPage page = new PDPage();
      document.addPage(page);

      PDFormXObject form = new PDFormXObject(document);
      form.setBBox(new PDRectangle(200, 50));
      form.setResources(new PDResources());
      try (PDFormContentStream formStream = new PDFormContentStream(form)) {
        formStream.beginText();
        formStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        formStream.newLineAtOffset(0, 0);
        formStream.showText(textInForm);
        formStream.endText();
      }

      try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
        stream.saveGraphicsState();
        stream.transform(Matrix.getTranslateInstance(50, 700));
        stream.drawForm(form);
        stream.restoreGraphicsState();
      }

      document.save(path.toFile());
    }
  }

  static void writeSolidColorPng(Path path, Color color, int width, int height) throws IOException {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    var graphics = image.createGraphics();
    graphics.setColor(color);
    graphics.fillRect(0, 0, width, height);
    graphics.dispose();
    ImageIO.write(image, "png", path.toFile());
  }
}
