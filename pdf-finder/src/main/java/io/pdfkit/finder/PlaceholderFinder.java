package io.pdfkit.finder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public interface PlaceholderFinder {

  List<Placeholder> findPlaceholders(Path pdf) throws IOException;

  default List<Placeholder> findPlaceholders(InputStream pdf) throws IOException {
    Path temp = Files.createTempFile("pdfkit-find-", ".pdf");
    try {
      Files.copy(pdf, temp, StandardCopyOption.REPLACE_EXISTING);
      return findPlaceholders(temp);
    } finally {
      Files.deleteIfExists(temp);
    }
  }
}
