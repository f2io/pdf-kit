package io.pdfkit.patcher;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;

/**
 * Draws images into AcroForm fields marked as image slots via a "${sign:key}" value (e.g.
 * "${sign:owner}") - the image-typed counterpart to AcroFormPdfPatcher's text placeholders.
 * Supports multiple slots (owner, co-owner, ...) distinguished by key. Once a slot's image is
 * drawn, its placeholder value is cleared.
 *
 * <p>The "sign:" prefix mirrors pdf-finder's PlaceholderPattern.classify() rule; pdf-finder and
 * pdf-patcher are separate Maven modules with no dependency between them, so this convention is
 * duplicated rather than shared - keep the two in sync if it ever changes.
 */
public class SignaturePdfPatcher implements PlaceholderImagePatcher {

  private static final Pattern SIGN_PATTERN = Pattern.compile("\\$\\{sign:([^}]+)}");

  @Override
  public void patchImage(Path input, Path output, Map<String, Path> images) throws IOException {
    try (PDDocument document =
        Loader.loadPDF(input.toFile(), IOUtils.createTempFileOnlyStreamCache())) {
      PDAcroForm acroForm = document.getDocumentCatalog().getAcroForm();
      if (acroForm != null) {
        for (PDField field : acroForm.getFieldTree()) {
          patchField(document, field, images);
        }
      }
      document.save(output.toFile());
    }
  }

  private void patchField(PDDocument document, PDField field, Map<String, Path> images)
      throws IOException {
    if (field.getWidgets().isEmpty()) {
      return;
    }
    String value = field.getValueAsString();
    if (value == null || value.isEmpty()) {
      return;
    }

    Matcher matcher = SIGN_PATTERN.matcher(value);
    if (!matcher.matches()) {
      return;
    }

    Path imagePath = images.get(matcher.group(1).trim());
    if (imagePath == null) {
      return;
    }

    PDAnnotationWidget widget = field.getWidgets().get(0);
    drawImage(document, widget.getPage(), imagePath, widget.getRectangle());
    field.setValue("");
  }

  private void drawImage(PDDocument document, PDPage page, Path imagePath, PDRectangle rect)
      throws IOException {
    PDImageXObject image = PDImageXObject.createFromFile(imagePath.toString(), document);

    float scale =
        Math.min(rect.getWidth() / image.getWidth(), rect.getHeight() / image.getHeight());
    float width = image.getWidth() * scale;
    float height = image.getHeight() * scale;
    float x = rect.getLowerLeftX() + (rect.getWidth() - width) / 2;
    float y = rect.getLowerLeftY() + (rect.getHeight() - height) / 2;

    try (PDPageContentStream stream =
        new PDPageContentStream(
            document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
      stream.drawImage(image, x, y, width, height);
    }
  }
}
