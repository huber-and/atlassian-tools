# Wiki Publisher

**Wiki Publisher** is a Java library designed to automate the publishing of documentation to Atlassian Confluence. It specifically supports parsing content structured for Antora and transforming it for Confluence's storage format.

## Features

- **Automated Publishing:** seamless integration to publish local documentation to Confluence.
- **Antora Support:** Built-in `AntoraParser` to handle Antora-generated content structures.
- **Multi-Space Mapping:** Configure multiple mappings to publish different documentation sets to different Confluence spaces.
- **Hierarchy Preservation:** Maintains the page hierarchy from your local documentation when publishing to Confluence.
- **Content Transformation:** Automatically transforms local content into the Confluence Storage Format.

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
| `username` | `String` | Username for authentication. |
| `password` | `String` | Password or API Token. |
| `debug` | `boolean` | If `true`, performs a dry-run without making changes to Confluence. |
| `mappers` | `Set<Mapper>` | Set of mapping rules for publishing. |

### Mapper Options

| Option | Type | Description |
| :--- | :--- | :--- |
| `spaceKey` | `String` | **Required.** The Key of the Confluence Space to publish to. |
| `path` | `String` | **Required.** Local filesystem path to the documentation root. |
| `root` | `String` | Optional. The title of a root page in the space to publish under. |

## Architecture

- **Parser:** Reads local files and constructs a page hierarchy (`AntoraParser`).
- **Transformer:** Converts HTML/AsciiDoc content into Confluence Storage Format (`ConfluenceTransformer`).
- **Client:** Handles REST API communication with Confluence (`ConfluenceClient`).

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](../LICENSE) file for details.