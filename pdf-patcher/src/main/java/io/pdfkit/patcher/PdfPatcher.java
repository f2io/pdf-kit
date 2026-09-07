package io.pdfkit.patcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Patches placeholders wherever they live in a PDF. By default runs every PlaceholderPatcher
 * implementation in turn (page text, then AcroForm field values), chaining each one's output into
 * the next; pass specific implementations to restrict it to just those.
 */
public class PdfPatcher implements PlaceholderPatcher, PlaceholderImagePatcher {

  private final List<PlaceholderPatcher> textPatchers;
  private final PlaceholderImagePatcher imagePatcher = new SignaturePdfPatcher();

  public PdfPatcher() {
    this(new COSPdfPatcher(), new AcroFormPdfPatcher());
  }

  public PdfPatcher(PlaceholderPatcher... textPatchers) {
    this.textPatchers = List.of(textPatchers);
  }

  @Override
  public void patchText(Path input, Path output, Map<String, String> values) throws IOException {
    Path current = input;
    List<Path> tempFiles = new ArrayList<>();
    try {
      for (int i = 0; i < textPatchers.size(); i++) {
        boolean last = i == textPatchers.size() - 1;
        Path stageOutput = last ? output : Files.createTempFile("pdfkit-patch-", ".pdf");
        if (!last) {
          tempFiles.add(stageOutput);
        }
        textPatchers.get(i).patchText(current, stageOutput, values);
        current = stageOutput;
      }
    } finally {
      for (Path tempFile : tempFiles) {
        Files.deleteIfExists(tempFile);
      }
    }
  }

  @Override
  public void patchImage(Path input, Path output, Map<String, Path> images) throws IOException {
    imagePatcher.patchImage(input, output, images);
  }
}
