package io.pdfkit.patcher;

import static io.pdfkit.patcher.TestPdfs.readFieldValue;
import static io.pdfkit.patcher.TestPdfs.readText;
import static io.pdfkit.patcher.TestPdfs.resource;
import static io.pdfkit.patcher.TestPdfs.tmpOutput;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PdfPatcherTest {

  @Test
  void patchesBothPageTextAndAcroFormFieldValuesByDefault() throws IOException, URISyntaxException {
    // PdfPatcher with no arguments chains COSPdfPatcher (page text) into
    // AcroFormPdfPatcher (field values), so nothing that either one
    // alone is blind to gets left unresolved.
    Path input = Paths.get(resource("contract-info-form.pdf").toURI());
    Path output = tmpOutput("patches-both-page-text-and-acroform-fields.pdf");

    new PdfPatcher()
        .patchText(
            input,
            output,
            Map.of(
                "agentName", "Jane Broker",
                "preparedDate", "2026-09-07",
                "contractNumber", "AC-12345",
                "ownerFirstName", "Jane",
                "ownerLastName", "Doe",
                "coOwnerFirstName", "John",
                "coOwnerLastName", "Doe"));

    String text = readText(output);
    assertTrue(text.contains("Prepared by Jane Broker on 2026-09-07"));
    assertTrue(text.contains("Owner authorizes updates to contract AC-12345."));
    assertEquals("Jane", readFieldValue(output, "ownerFirstName"));
    assertEquals("Doe", readFieldValue(output, "ownerLastName"));
    assertEquals("John", readFieldValue(output, "coOwnerFirstName"));
    assertEquals("Doe", readFieldValue(output, "coOwnerLastName"));
  }

  @Test
  void restrictsToTheGivenImplementationsWhenSpecified() throws IOException, URISyntaxException {
    Path input = Paths.get(resource("contract-info-form.pdf").toURI());
    Path output = tmpOutput("patches-only-acroform-fields.pdf");

    new PdfPatcher(new AcroFormPdfPatcher())
        .patchText(
            input,
            output,
            Map.of(
                "agentName", "Jane Broker",
                "ownerFirstName", "Jane"));

    // Restricted to AcroFormPdfPatcher: the field is patched, but the
    // page-text placeholder - COSPdfPatcher's job - is left untouched.
    assertEquals("Jane", readFieldValue(output, "ownerFirstName"));
    assertTrue(readText(output).contains("Prepared by ${agentName}"));
  }
}
