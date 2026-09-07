package io.pdfkit.patcher;

import static io.pdfkit.patcher.TestPdfs.readFieldValue;
import static io.pdfkit.patcher.TestPdfs.resource;
import static io.pdfkit.patcher.TestPdfs.tmpOutput;
import static io.pdfkit.patcher.TestPdfs.writeSolidColorPng;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfPatcherImageTest {

  @Test
  void drawsSignatureImageIntoItsDesignatedFieldRectangle(@TempDir Path tempDir)
      throws IOException, URISyntaxException {
    // A signature is an image, not text, so it can't go through patchText()'s
    // COSString substitution. The "ownerSignature" field on
    // contract-info-form.pdf holds "${sign:owner}"; patchImage()
    // must draw the given image scaled into exactly that field's
    // rectangle, then clear the placeholder text.
    Path input = Paths.get(resource("contract-info-form.pdf").toURI());
    Path output = tmpOutput("draws-signature-image-into-field-rectangle.pdf");
    Path signature = tempDir.resolve("signature.png");
    writeSolidColorPng(signature, Color.BLUE, 300, 80);

    new PdfPatcher().patchImage(input, output, Map.of("owner", signature));

    assertEquals(
        Color.BLUE.getRGB() & 0xFFFFFF, renderPixelAtFieldCenter(output, "ownerSignature"));
    assertEquals("", readFieldValue(output, "ownerSignature"));
  }

  @Test
  void drawsMultipleSignaturesFromASingleImagesMap(@TempDir Path tempDir)
      throws IOException, URISyntaxException {
    // Two independent image slots - "${sign:owner}" and
    // "${sign:coOwner}" - distinguished only by their key, both
    // resolved from one images map in one pass.
    Path input = Paths.get(resource("contract-info-form.pdf").toURI());
    Path output = tmpOutput("draws-multiple-signatures.pdf");
    Path ownerSignature = tempDir.resolve("owner-signature.png");
    Path coOwnerSignature = tempDir.resolve("co-owner-signature.png");
    writeSolidColorPng(ownerSignature, Color.BLUE, 300, 80);
    writeSolidColorPng(coOwnerSignature, Color.RED, 300, 80);

    new PdfPatcher()
        .patchImage(
            input,
            output,
            Map.of(
                "owner", ownerSignature,
                "coOwner", coOwnerSignature));

    assertEquals(
        Color.BLUE.getRGB() & 0xFFFFFF, renderPixelAtFieldCenter(output, "ownerSignature"));
    assertEquals(
        Color.RED.getRGB() & 0xFFFFFF, renderPixelAtFieldCenter(output, "coOwnerSignature"));
  }

  private int renderPixelAtFieldCenter(Path pdf, String fieldName) throws IOException {
    try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
      PDAcroForm acroForm = document.getDocumentCatalog().getAcroForm();
      PDRectangle rect = acroForm.getField(fieldName).getWidgets().get(0).getRectangle();
      float pageHeight = document.getPage(0).getMediaBox().getHeight();

      BufferedImage rendered = new PDFRenderer(document).renderImageWithDPI(0, 72);
      int x = Math.round(rect.getLowerLeftX() + rect.getWidth() / 2);
      int y = Math.round(pageHeight - (rect.getLowerLeftY() + rect.getHeight() / 2));
      return rendered.getRGB(x, y) & 0xFFFFFF;
    }
  }
}
