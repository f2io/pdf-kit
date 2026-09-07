# pdf-kit

Maven multi-module project for finding and patching placeholders in PDFs.

- `pdf-finder` — locates `${placeholder}` values in a PDF (page text and AcroForm fields).
- `pdf-patcher` — replaces those placeholders with real values or images.

## Project structure

```
pdf-kit/
├── pom.xml                        # parent POM (Java 21, Spotless)
├── lefthook.yml                   # pre-commit hook config
├── pdf-finder/                    # locates ${placeholder} values in a PDF
│   ├── pom.xml
│   └── src/
│       ├── main/java/io/pdfkit/finder/
│       │   ├── PdfFinder.java              # orchestrator
│       └── test/java/io/pdfkit/finder/
└── pdf-patcher/                   # replaces placeholders with values or images
    ├── pom.xml
    └── src/
        ├── main/java/io/pdfkit/patcher/
        │   └── PdfPatcher.java              # orchestrator
        └── test/java/io/pdfkit/patcher/
```

## Requirements

- Java 21 (`maven.compiler.source`/`target` in the parent POM). The codebase uses pattern-matching `instanceof` (e.g. `token instanceof COSString cosString`), which requires Java 16+.

## Development setup

Formatting is enforced by [Spotless](https://github.com/diffplug/spotless) (`google-java-format`) and checked on `mvn verify`. To auto-format before every commit, install [lefthook](https://github.com/evilmartians/lefthook) and wire it into this repo:

```
brew install lefthook
lefthook install
```

This installs a git hook (not tracked by git) that runs `mvn spotless:apply` on staged `.java` files before each commit. Each contributor needs to run `lefthook install` once after cloning.

## Publishing

Both workflows are manual (`workflow_dispatch`) and refuse to run from any branch other than `main`.

- **[Publish to GitHub Packages](.github/workflows/publish.yml)** — deploys to `https://maven.pkg.github.com/f2io/pdf-kit`. Uses the repo's built-in `GITHUB_TOKEN`, no extra setup needed.
- **[Publish to Maven Central](.github/workflows/publish-central.yml)** — deploys via the [Sonatype Central Publishing Portal](https://central.sonatype.com) under the `io.github.f2io` groupId (verified via GitHub OAuth, no domain needed). Requires one-time setup:
  1. Create an account at [central.sonatype.com](https://central.sonatype.com) and verify the `io.github.f2io` namespace (sign in with GitHub).
  2. Generate a user token (Account → Generate User Token) — this gives you a username/password pair.
  3. Generate a GPG keypair for signing artifacts and publish the public key to a keyserver (e.g. `keys.openpgp.org`):
     ```
     gpg --gen-key
     gpg --armor --export-secret-keys <key-id> > private.asc
     gpg --keyserver keys.openpgp.org --send-keys <key-id>
     ```
  4. Add these repo secrets (Settings → Secrets and variables → Actions):
     - `CENTRAL_TOKEN_USERNAME` / `CENTRAL_TOKEN_PASSWORD` — from step 2.
     - `GPG_PRIVATE_KEY` — contents of `private.asc` from step 3.
     - `GPG_PASSPHRASE` — the passphrase used when generating the key.

  The deploy runs with `-Pcentral-publish` and `autoPublish=false`, so each run lands as a pending deployment that needs a manual release at [central.sonatype.com/publishing/deployments](https://central.sonatype.com/publishing/deployments) before it's visible on Central.
