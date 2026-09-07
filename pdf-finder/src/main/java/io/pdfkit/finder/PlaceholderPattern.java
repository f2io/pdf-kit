package io.pdfkit.finder;

import io.pdfkit.finder.Placeholder.PlaceholderKind;
import java.util.regex.Pattern;

/**
 * The "${key}" text-placeholder syntax shared by COSPdfFinder and AcroFormPdfFinder, so both scan
 * for exactly the same thing - plus the "sign:" prefix rule that reclassifies a match as an IMAGE
 * placeholder (e.g. "${sign:owner}") instead of a TEXT one. Centralized here so the rule lives in
 * exactly one place rather than as an inline check wherever a placeholder gets classified.
 */
final class PlaceholderPattern {

  static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

  private static final String SIGN_PREFIX = "sign:";

  private PlaceholderPattern() {}

  static Placeholder classify(String rawKey, int page) {
    if (rawKey.startsWith(SIGN_PREFIX)) {
      return new Placeholder(rawKey.substring(SIGN_PREFIX.length()), page, PlaceholderKind.IMAGE);
    }
    return new Placeholder(rawKey, page);
  }
}
