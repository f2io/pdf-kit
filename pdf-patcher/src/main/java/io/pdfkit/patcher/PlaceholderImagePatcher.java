package io.pdfkit.patcher;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public interface PlaceholderImagePatcher {

  void patchImage(Path input, Path output, Map<String, Path> images) throws IOException;
}
