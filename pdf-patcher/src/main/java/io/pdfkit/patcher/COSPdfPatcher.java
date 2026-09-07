package io.pdfkit.patcher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;

/**
 * Replaces placeholders drawn as page content-stream text (Tj/TJ runs), including text nested
 * inside Form XObjects (recursively) - blind to placeholders living only inside AcroForm field
 * values, which is AcroFormPdfPatcher's job.
 */
public class COSPdfPatcher implements PlaceholderPatcher {

  private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

  @Override
  public void patchText(Path input, Path output, Map<String, String> values) throws IOException {
    try (PDDocument document = Loader.loadPDF(input.toFile())) {
      for (PDPage page : document.getPages()) {
        patchPage(document, page, values);
      }
      document.save(output.toFile());
    }
  }

  private void patchPage(PDDocument document, PDPage page, Map<String, String> values)
      throws IOException {
    List<Object> tokens = new PDFStreamParser(page).parse();
    patchTokens(tokens, values);

    ByteArrayOutputStream contentBytes = new ByteArrayOutputStream();
    new ContentStreamWriter(contentBytes).writeTokens(tokens);

    PDStream newContents = new PDStream(document);
    try (OutputStream out = newContents.createOutputStream()) {
      out.write(contentBytes.toByteArray());
    }
    page.setContents(newContents);

    patchXObjects(page.getResources(), values);
  }

  private void patchXObjects(PDResources resources, Map<String, String> values) throws IOException {
    if (resources == null) {
      return;
    }
    for (COSName name : resources.getXObjectNames()) {
      PDXObject xObject = resources.getXObject(name);
      if (xObject instanceof PDFormXObject formXObject) {
        patchFormXObject(formXObject, values);
        patchXObjects(formXObject.getResources(), values);
      }
    }
  }

  private void patchFormXObject(PDFormXObject formXObject, Map<String, String> values)
      throws IOException {
    List<Object> tokens = new PDFStreamParser(formXObject).parse();
    patchTokens(tokens, values);

    ByteArrayOutputStream contentBytes = new ByteArrayOutputStream();
    new ContentStreamWriter(contentBytes).writeTokens(tokens);

    try (OutputStream out = formXObject.getCOSObject().createOutputStream()) {
      out.write(contentBytes.toByteArray());
    }
  }

  private void patchTokens(List<Object> tokens, Map<String, String> values) {
    for (int i = 0; i < tokens.size(); i++) {
      Object token = tokens.get(i);
      if (token instanceof COSString cosString) {
        tokens.set(i, patchString(cosString, values));
      } else if (token instanceof COSArray array) {
        patchArray(array, values);
      }
    }
  }

  private void patchArray(COSArray array, Map<String, String> values) {
    for (int i = 0; i < array.size(); i++) {
      COSBase element = array.get(i);
      if (element instanceof COSString cosString) {
        array.set(i, patchString(cosString, values));
      }
    }
  }

  private COSString patchString(COSString cosString, Map<String, String> values) {
    String text = new String(cosString.getBytes(), StandardCharsets.ISO_8859_1);
    String replaced = replacePlaceholders(text, values);
    return replaced.equals(text)
        ? cosString
        : new COSString(replaced.getBytes(StandardCharsets.ISO_8859_1));
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
