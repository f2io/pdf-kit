package io.pdfkit.patcher;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public interface PlaceholderPatcher {

  void patchText(Path input, Path output, Map<String, String> values) throws IOException;
}
