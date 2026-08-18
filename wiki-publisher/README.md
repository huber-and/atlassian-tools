# Wiki Publisher

[![Maven Central](https://img.shields.io/maven-central/v/io.github.huber-and.atlassian/wiki-publisher?label=Maven%20Central)](https://mvnrepository.com/artifact/io.github.huber-and.atlassian/wiki-publisher)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](../LICENSE)

**Wiki Publisher** is a Java library designed to automate the publishing of documentation to Atlassian Confluence. It specifically supports parsing content structured for Antora and transforming it for Confluence's storage format.

> **Live example:** this plugin publishes its own project's architecture documentation.
> Compare the [generated Antora site](https://huber-and.github.io/atlassian-tools) with the
> [resulting Confluence pages](https://the-hubers.atlassian.net/wiki/spaces/AT/pages/65437698/Architecture) — same source, both targets, publicly readable.

## Features

- **Automated Publishing:** seamless integration to publish local documentation to Confluence.
- **Antora Support:** Built-in `AntoraParser` to handle Antora-generated content structures.
- **Pluggable Parser:** Replace the built-in parser with your own `Parser` implementation.
- **Multi-Space Mapping:** Configure multiple mappings to publish different documentation sets to different Confluence spaces.
- **Hierarchy Preservation:** Maintains the page hierarchy from your local documentation when publishing to Confluence.
- **Content Transformation:** Automatically transforms local content into the Confluence Storage Format, including internal links, attachments and admonitions.
- **Orphan Cleanup:** Optionally moves pages that no longer exist locally to the Confluence trash.

## Installation

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.huber-and.atlassian</groupId>
    <artifactId>wiki-publisher</artifactId>
    <version>${atlassian-tools.version}</version>
</dependency>
```

## Usage

The core entry point is the `Publisher` class, which requires a `Configuration` object.

### 1. Configure the Publisher

Create a `Configuration` object and set your Confluence credentials and the `Mapper`s that define where your content lives and where it should go.

```java
import io.github.huber_and.atlassian.wiki.Configuration;
import io.github.huber_and.atlassian.wiki.Configuration.Mapper;
import io.github.huber_and.atlassian.wiki.Publisher;
import java.util.Set;

public class DocPublisher {
    public static void main(String[] args) {
        // 1. Setup Configuration
        Configuration config = new Configuration();
        config.setUrl("https://confluence.example.com");
        config.setUsername("your-username");
        config.setPassword("your-password-or-api-token");
        
        // Optional: Enable debug mode for dry-runs
        // config.setDebug(true);

        // 2. Define Mappings
        Mapper mainDocs = new Mapper();
        mainDocs.setSpaceKey("DOCS");      // Target Confluence Space Key
        mainDocs.setPath("build/site");    // Local path to Antora output or docs
        // mainDocs.setRoot("Home");       // Optional: Root page to publish under

        config.setMappers(Set.of(mainDocs));

        // 3. Run Publisher
        Publisher publisher = new Publisher(config);
        publisher.publish();
    }
}
```

### Configuration Options

| Option | Type | Description |
| :--- | :--- | :--- |
| `url` | `String` | Base URL of your Confluence instance. |
| `username` | `String` | Username for authentication. Excluded from `toString()` so it cannot end up in a log. |
| `password` | `String` | Password or API Token. Excluded from `toString()`, `equals()` and `hashCode()`. |
| `debug` | `boolean` | If `true`, performs a dry-run without making changes to Confluence. |
| `mappers` | `Set<Mapper>` | Set of mapping rules for publishing. |
| `parserClass` | `String` | Fully qualified class name of the `Parser` implementation. Defaults to `AntoraParser`. |

### Mapper Options

| Option | Type | Description |
| :--- | :--- | :--- |
| `spaceKey` | `String` | **Required.** The Key of the Confluence Space to publish to. |
| `path` | `String` | **Required.** Local filesystem path to the documentation root. |
| `root` | `String` | Optional. The title of a root page in the space to publish under. |
| `deleteOrphans` | `boolean` | Defaults to `true`: pages below `root` that no longer exist locally are moved to the Confluence trash. With `false` they are only reported in the log. Requires `root`. |

### Custom Parser

Set `parserClass` to publish content that is not an Antora site. The class must implement
`io.github.huber_and.atlassian.wiki.parser.Parser` and provide a public no-argument constructor;
`Publisher` instantiates it and calls `init(Configuration)` once before use.

```java
config.setParserClass("com.example.docs.MyParser");
```

The name is resolved via the thread context class loader first — so a parser supplied by the
surrounding build is found — and falls back to this library's own class loader. Resolution happens
without running the class's static initializers, and the class is only instantiated after it has
been verified to implement `Parser`; anything else fails with an `IllegalStateException`.

## Architecture

- **Parser:** Reads local files and constructs a page hierarchy. `AntoraParser` is the built-in implementation of the `Parser` interface; the implementation is selectable via `parserClass`.
- **LinkResolver:** Builds an index over the page trees of all mappers so internal and cross-space links can be resolved by title and space key.
- **Transformer:** Converts HTML/AsciiDoc content into Confluence Storage Format (`ConfluenceTransformer`).
- **Client:** Handles REST API communication with Confluence (`ConfluenceClient`).

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](../LICENSE) file for details.