package io.pdfkit.patcher;

import static io.pdfkit.patcher.TestPdfs.readFieldValue;
import static io.pdfkit.patcher.TestPdfs.readText;
import static io.pdfkit.patcher.TestPdfs.tmpOutput;
import static io.pdfkit.patcher.TestPdfs.writeAcroFormPdf;
import static io.pdfkit.patcher.TestPdfs.writeFormXObjectPdf;
import static io.pdfkit.patcher.TestPdfs.writeSamplePdf;
import static io.pdfkit.patcher.TestPdfs.writeSplitRunPdf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class COSPdfPatcherTest {

  @Test
  void replacesKnownPlaceholdersWithValues(@TempDir Path tempDir) throws IOException {
    Path syntheticInput = tempDir.resolve("input.pdf");
    Path output = tmpOutput("replaces-known-placeholders.pdf");
    writeSamplePdf(syntheticInput, "Hello ${name}, your order ${orderId} is ready.");

    new COSPdfPatcher()
        .patchText(
            syntheticInput,
            output,
            Map.of(
                "name", "Alice",
                "orderId", "12345"));

    assertEquals("Hello Alice, your order 12345 is ready.", readText(output).trim());
  }

  @Test
  void replacesPlaceholderWithEmptyStringWhenValueIsExplicitlyEmpty(@TempDir Path tempDir)
      throws IOException {
    // An explicit "" value is a known answer (e.g. "no middle initial"),
    // distinct from an unresolved placeholder whose key is simply absent
    // from the values map - the former must be blanked out, not left as
    // literal "${...}" text like the latter.
    Path syntheticInput = tempDir.resolve("input.pdf");
    Path output = tmpOutput("replaces-placeholder-with-empty-string.pdf");
    writeSamplePdf(syntheticInput, "Hello ${name}, your order ${orderId} is ready.");

    new COSPdfPatcher()
        .patchText(
            syntheticInput,
            output,
            Map.of(
                "name", "",
                "orderId", "12345"));

    assertEquals("Hello , your order 12345 is ready.", readText(output).trim());
  }

  @Test
  void leavesUnresolvedPlaceholdersUntouched(@TempDir Path tempDir) throws IOException {
    Path syntheticInput = tempDir.resolve("input.pdf");
    Path output = tmpOutput("leaves-unresolved-placeholders-untouched.pdf");
    writeSamplePdf(syntheticInput, "Hello ${name}, your order ${orderId} is ready.");

    new COSPdfPatcher().patchText(syntheticInput, output, Map.of("name", "Alice"));

    assertEquals("Hello Alice, your order ${orderId} is ready.", readText(output).trim());
  }

  @Test
  void leavesTextWithoutPlaceholdersUnchanged(@TempDir Path tempDir) throws IOException {
    Path syntheticInput = tempDir.resolve("input.pdf");
    Path output = tmpOutput("leaves-text-without-placeholders-unchanged.pdf");
    writeSamplePdf(syntheticInput, "No placeholders here.");

    new COSPdfPatcher().patchText(syntheticInput, output, Map.of("name", "Alice"));

    assertTrue(readText(output).contains("No placeholders here."));
  }

  @Test
  void leavesPlaceholderSplitAcrossContentStreamRunsUntouched(@TempDir Path tempDir)
      throws IOException {
    // Mirrors COSPdfFinder's split-run limitation: a placeholder spread
    // across two Tj/TJ runs can't be matched or replaced run-by-run, so
    // patching must leave both runs as they are instead of corrupting
    // the content.
    Path syntheticInput = tempDir.resolve("input.pdf");
    Path output = tmpOutput("leaves-split-run-placeholder-untouched.pdf");
    writeSplitRunPdf(syntheticInput, "Hello ${na", "me}, welcome.");

    new COSPdfPatcher().patchText(syntheticInput, output, Map.of("name", "Alice"));

    assertEquals("Hello ${name}, welcome.", readText(output).trim());
  }

  @Test
  void leavesAcroFormFieldValuesUntouchedWhilePatchingPageText(@TempDir Path tempDir)
      throws IOException {
    // Mirrors COSPdfFinder's AcroForm limitation: a placeholder living
    // only in a form field's value isn't visible to content-stream
    // scanning, so COSPdfPatcher must leave it as-is rather than
    // silently dropping or corrupting it, while still patching the
    // placeholders in page text.
    Path syntheticInput = tempDir.resolve("input.pdf");
    Path output = tmpOutput("leaves-acroform-field-value-untouched.pdf");
    writeAcroFormPdf(syntheticInput, "Prepared on ${date}", "customerName", "${customerName}");

    new COSPdfPatcher()
        .patchText(
            syntheticInput,
            output,
            Map.of(
                "date", "2026-09-06",
                "customerName", "Alice"));

    assertEquals("Prepared on 2026-09-06", readText(output).trim());
    assertEquals("${customerName}", readFieldValue(output, "customerName"));
  }

  @Test
  void patchesPlaceholderDrawnInsideFormXObject(@TempDir Path tempDir) throws IOException {
    // Some PDF generators (e.g. templating tools, reused letterhead/logo blocks)
    // draw text via a Form XObject rather than directly in the page's content
    // stream. COSPdfPatcher must recurse into Form XObjects to patch that text too.
    Path syntheticInput = tempDir.resolve("input.pdf");
    Path output = tmpOutput("patches-placeholder-inside-form-xobject.pdf");
    writeFormXObjectPdf(syntheticInput, "Hello ${name}, welcome.");

    new COSPdfPatcher().patchText(syntheticInput, output, Map.of("name", "Alice"));

    assertEquals("Hello Alice, welcome.", readText(output).trim());
  }
}
