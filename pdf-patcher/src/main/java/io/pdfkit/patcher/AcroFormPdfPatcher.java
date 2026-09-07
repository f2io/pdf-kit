package io.pdfkit.patcher;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;

/**
 * Counterpart to PdfPatcher that replaces placeholders living inside AcroForm field values instead
 * of page content streams - the exact spots PdfPatcher is blind to.
 */
public class AcroFormPdfPatcher implements PlaceholderPatcher {

  private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

  @Override
  public void patchText(Path input, Path output, Map<String, String> values) throws IOException {
    try (PDDocument document =
        Loader.loadPDF(input.toFile(), IOUtils.createTempFileOnlyStreamCache())) {
      PDAcroForm acroForm = document.getDocumentCatalog().getAcroForm();
      if (acroForm != null) {
        for (PDField field : acroForm.getFieldTree()) {
          if (field.getWidgets().isEmpty()) {
            continue;
          }
          String value = field.getValueAsString();
          if (value == null || value.isEmpty()) {
            continue;
          }
          String replaced = replacePlaceholders(value, values);
          if (!replaced.equals(value)) {
            field.setValue(replaced);
          }
        }
      }
      document.save(output.toFile());
    }
  }

  private String replacePlaceholders(String text, Map<String, String> values) {
    Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
    StringBuilder result = new StringBuilder();
    while (matcher.find()) {
      String value = values.get(matcher.group(1).trim());
      matcher.appendReplacement(
          result, Matcher.quoteReplacement(value != null ? value : matcher.group()));
    }
    matcher.appendTail(result);
    return result.toString();
  }
}
