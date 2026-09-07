package io.pdfkit.finder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.pdfkit.finder.Placeholder.PlaceholderKind;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Test;

class PdfFinderTest {

  @Test
  void findsPlaceholdersFromAllSourcesByDefault() throws IOException, URISyntaxException {
    // PdfFinder with no arguments combines COSPdfFinder (page text) and
    // AcroFormPdfFinder (field values - both TEXT and IMAGE kinds), so
    // nothing that either one alone is blind to gets missed.
    Path pdf = Paths.get(resource("contract-info-form.pdf").toURI());

    List<Placeholder> placeholders = new PdfFinder().findPlaceholders(pdf);

    assertEquals(
        List.of(
            new Placeholder("agentName", 1),
            new Placeholder("preparedDate", 1),
            new Placeholder("contractNumber", 1),
            new Placeholder("ownerFirstName", 1),
            new Placeholder("ownerLastName", 1),
            new Placeholder("coOwnerFirstName", 1),
            new Placeholder("coOwnerLastName", 1),
            new Placeholder("owner", 1, PlaceholderKind.IMAGE),
            new Placeholder("coOwner", 1, PlaceholderKind.IMAGE)),
        placeholders);
  }

  @Test
  void placeholderKindTellsCallersWhichPatchMethodToUse() throws IOException, URISyntaxException {
    // The merged list on its own doesn't say which entries need patch()
    // vs patchImages() - kind() is what makes that decision inspectable
    // without the caller having to already know the "${sign:...}"
    // convention.
    Path pdf = Paths.get(resource("contract-info-form.pdf").toURI());

    List<Placeholder> placeholders = new PdfFinder().findPlaceholders(pdf);

    List<String> imageKeys =
        placeholders.stream()
            .filter(p -> p.kind() == PlaceholderKind.IMAGE)
            .map(Placeholder::key)
            .toList();
    List<String> textKeys =
        placeholders.stream()
            .filter(p -> p.kind() == PlaceholderKind.TEXT)
            .map(Placeholder::key)
            .toList();

    assertEquals(List.of("owner", "coOwner"), imageKeys);
    assertEquals(
        List.of(
            "agentName",
            "preparedDate",
            "contractNumber",
            "ownerFirstName",
            "ownerLastName",
            "coOwnerFirstName",
            "coOwnerLastName"),
        textKeys);
  }

  @Test
  void restrictsToTheGivenImplementationsWhenSpecified() throws IOException, URISyntaxException {
    Path pdf = Paths.get(resource("contract-info-form.pdf").toURI());

    List<Placeholder> placeholders = new PdfFinder(new COSPdfFinder()).findPlaceholders(pdf);

    assertEquals(
        List.of(
            new Placeholder("agentName", 1),
            new Placeholder("preparedDate", 1),
            new Placeholder("contractNumber", 1)),
        placeholders);
  }

  private java.net.URL resource(String name) {
    java.net.URL url = getClass().getClassLoader().getResource(name);
    if (url == null) {
      throw new UncheckedIOException(new IOException("Resource not found: " + name));
    }
    return url;
  }
}
