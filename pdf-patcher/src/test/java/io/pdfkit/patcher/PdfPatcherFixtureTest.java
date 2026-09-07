package io.pdfkit.patcher;

import static io.pdfkit.patcher.TestPdfs.readAllFieldValues;
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

class PdfPatcherFixtureTest {

  @Test
  void leavesContractInfoFormFieldsUntouchedWhilePatchingPageText()
      throws IOException, URISyntaxException {
    // Real-world-shaped fixture: owner/co-owner AcroForm sections (name,
    // DOB, phone, email, address, a checkbox) plus static boilerplate
    // text. patch() must only touch the page content - every field,
    // whether it holds an unrelated placeholder or is blank/unchecked,
    // stays exactly as it was.
    Path input = Paths.get(resource("contract-info-form.pdf").toURI());
    Path output = tmpOutput("leaves-contract-info-form-fields-untouched.pdf");

    Map<String, String> fieldValuesBefore = readAllFieldValues(input);

    new PdfPatcher()
        .patchText(
            input,
            output,
            Map.of(
                "agentName", "Jane Broker",
                "preparedDate", "2026-09-07",
                "contractNumber", "AC-12345"));

    String text = readText(output);
    assertTrue(text.contains("Prepared by Jane Broker on 2026-09-07"));
    assertTrue(text.contains("Owner authorizes updates to contract AC-12345."));
    assertEquals(fieldValuesBefore, readAllFieldValues(output));
  }
}
