package io.pdfkit.finder;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Accessors;

@Getter
@ToString
@EqualsAndHashCode
@AllArgsConstructor
@Accessors(fluent = true)
public final class Placeholder {

  private final String key;
  private final int page;
  private final PlaceholderKind kind;

  public Placeholder(String key, int page) {
    this(key, page, PlaceholderKind.TEXT);
  }

  /**
   * What a placeholder expects to be filled in with - a string (via patch()) or an image (via
   * patchImages()).
   */
  public enum PlaceholderKind {
    TEXT,
    IMAGE
  }
}
