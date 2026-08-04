# Copilot Instructions

## Build & Test

```bash
# Full build (all modules)
mvn clean install

# Single module
mvn -pl wiki-publisher clean install

# Tests only
mvn test

# Single test class
mvn -pl wiki-publisher test -Dtest=AntoraParserTestLocal

# Release build (no SNAPSHOTs allowed)
mvn clean enforcer:enforce package -Prelease -Drevision=X.Y.Z -Dchangelist=
```

> **Note:** Tests in `wiki-publisher` (e.g., `AntoraParserTestLocal`, `ConfluenceApiTestLocal`) are integration tests that require a locally built Antora site or a live Confluence instance. They are not intended to run automatically in CI.

## Architecture

This is a four-module Maven project (`groupId: io.github.huber-and.atlassian`). Modules depend on each other in this order:

```
wiki-client  →  wiki-publisher  →  maven-plugin
                                        ↑
                                   arc42-sample (demo, excluded from Central publishing)
```

### `wiki-client`
- Contains the **Confluence REST API client**.
- The **V2 API** (`net.atlassian.wiki.rest.v2`) is **auto-generated** at build time from `src/main/openapi/ConfluenceV2.json` using the OpenAPI Generator Maven plugin (generator: `java`, library: `apache-httpclient`). **Do not edit generated sources** under `target/generated-sources/`.
- The **V1 API** (`net.atlassian.wiki.rest.v1`) (used only for attachment uploads) is manually maintained in `src/main/java`.

### `wiki-publisher`
- Core publishing logic. Entry point is `Publisher`, which wires together three collaborators:
  - `Parser` (interface) / `AntoraParser` (impl) — reads an Antora HTML site, parses the navigation menu from `index.html`, and builds a `Page` tree.
  - `Transformer` (interface) / `ConfluenceTransformer` (impl) — converts Jsoup `Element` content into Confluence Storage Format (handles images→`ac:image`, code blocks→`ac:structured-macro`, CDATA wrapping, class attribute removal).
  - `ConfluenceClient` — orchestrates REST calls using the `wiki-client`; uses V2 for pages/spaces/properties and attachment listing/deletion, and V1 for attachment uploads.
- Change detection is hash-based and split in two: the page body is compared against the `page-content-hash` page property, each attachment against the SHA-256 stored in its own `comment` field (`sha256:<hex>`). The two decisions are independent — a changed image is published even when the body is unchanged.
- `Configuration` / `Configuration.Mapper` are plain Lombok `@Data` classes; `Configuration.debug = true` enables a **dry-run** that skips all actual writes to Confluence.
- `Utils.retry(operation, maxRetries)` is used around every API call; always wrap new API calls with it.

### `maven-plugin`
- Single Mojo: `PagePublisherMojo` (goal `atlassian:publish`, default phase `NONE`).
- Reads credentials from Maven `settings.xml` using the Confluence host as server `<id>` when `username` is not set explicitly.

## Key Conventions

### Package structure
| Module | Package |
|---|---|
| `wiki-client` (generated) | `net.atlassian.wiki.rest` |
| `wiki-publisher` | `io.github.huber_and.atlassian.wiki` |
| `maven-plugin` | `io.github.huber_and.maven.atlassian.wiki` |
| Tests | `io.github.huber_and.test` |

### Code style
- **Java 21**, **Jakarta EE** (not `javax`).
- **Lombok** is used throughout: `@Data`, `@Getter`, `@Slf4j`, `@NoArgsConstructor`, `@AllArgsConstructor`. Use Lombok annotations instead of manual boilerplate.
- Every public class and method has a **Javadoc comment**. Add Javadoc when creating new public API.
- Every source file carries the **Apache License 2.0** header (copy from any existing file).

### HTML parsing
- Jsoup is used for both parsing and producing output. Always use `org.jsoup.parser.Parser.xmlParser()` (not the HTML parser) to preserve XHTML validity.
- `doc.outputSettings().prettyPrint(false)` must be set to avoid whitespace mangling.

### Version management
- Version follows the Maven CI-friendly pattern: `${revision}${changelist}` in the root POM. The default is `0.2.0-SNAPSHOT`.
- The `arc42-sample` module is excluded from Maven Central publishing via `<excludeArtifacts>` in the `central-publishing-maven-plugin`.
