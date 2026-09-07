package io.pdfkit.patcher;

import static io.pdfkit.patcher.TestPdfs.readFieldValue;
import static io.pdfkit.patcher.TestPdfs.readText;
import static io.pdfkit.patcher.TestPdfs.resource;
import static io.pdfkit.patcher.TestPdfs.tmpOutput;
import static io.pdfkit.patcher.TestPdfs.writeAcroFormPdf;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AcroFormPdfPatcherTest {

  @Test
  void replacesPlaceholdersLivingInAcroFormFieldValuesWhileLeavingPageTextUntouched(
      @TempDir Path tempDir) throws IOException {
    // Mirrors PdfPatcher's blind spot: a placeholder living only in a
    // form field's value is exactly what AcroFormPdfPatcher is meant to
    // patch - the boilerplate page-text placeholder is the one it must
    // now leave alone.
    Path syntheticInput = tempDir.resolve("input.pdf");
    Path output = tmpOutput("replaces-acroform-field-value.pdf");
    writeAcroFormPdf(syntheticInput, "Prepared on ${date}", "customerName", "${customerName}");

    new AcroFormPdfPatcher()
        .patchText(
            syntheticInput,
            output,
            Map.of(
                "date", "2026-09-07",
                "customerName", "Alice"));

    assertEquals("Prepared on ${date}", readText(output).trim());
    assertEquals("Alice", readFieldValue(output, "customerName"));
  }

  @Test
  void patchesContractInfoFormOwnerFieldsWhileLeavingPageTextUntouched()
      throws IOException, URISyntaxException {
    // Real-world-shaped fixture: the owner/co-owner name fields hold
    // "${...}" placeholders as their AcroForm value. AcroFormPdfPatcher
    // must resolve those, while the page-text placeholders (agentName,
    // preparedDate, contractNumber) - PdfPatcher's job, not this one's -
    // stay exactly as they were.
    Path input = Paths.get(resource("contract-info-form.pdf").toURI());
    Path output = tmpOutput("patches-contract-info-form-owner-fields.pdf");

    new AcroFormPdfPatcher()
        .patchText(
            input,
            output,
            Map.of(
                "ownerFirstName", "Jane",
                "ownerLastName", "Doe",
                "coOwnerFirstName", "John",
                "coOwnerLastName", "Doe"));

    assertEquals("Jane", readFieldValue(output, "ownerFirstName"));
    assertEquals("Doe", readFieldValue(output, "ownerLastName"));
    assertEquals("John", readFieldValue(output, "coOwnerFirstName"));
    assertEquals("Doe", readFieldValue(output, "coOwnerLastName"));
    assertEquals(readText(input), readText(output));
  }
}
