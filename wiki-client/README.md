# Confluence REST API Client

The **wiki-client** is a Java library that provides a comprehensive client for the Atlassian Confluence Cloud REST API. It is primarily generated from the Confluence OpenAPI specification (v2), ensuring up-to-date coverage of the API endpoints, with additional support for specific v1 operations like attachment management.

## Features

- **Comprehensive API Coverage:** Generated from the official Confluence OpenAPI v2 specification.
- **Attachment Support:** Includes a specialized `ContentAttachmentsApi` for managing attachments (based on v1 API).
- **Modern Java:** Built for Java 21+.
- **Strongly Typed:** Uses Jackson for JSON serialization/deserialization and provides strongly typed models for all API resources.
- **HTTP Client 5:** Powered by Apache HttpClient 5 for robust network communication.

## Installation

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.huber-and.atlassian</groupId>
    <artifactId>wiki-client</artifactId>
    <version>${atlassian-tools.version}</version>
</dependency>
```

## Usage

### Initialization

Create an instance of `ApiClient` and configure it with your Confluence instance details and credentials.

```java
import net.atlassian.wiki.rest.ApiClient;
import net.atlassian.wiki.rest.v2.api.PageApi;
import net.atlassian.wiki.rest.v2.model.Page;

public class ConfluenceExample {
    public static void main(String[] args) {
        // 1. Configure Client
        ApiClient client = new ApiClient();
        client.setBasePath("https://your-domain.atlassian.net/wiki");
        client.setUsername("your-email@example.com");
        client.setPassword("your-api-token"); // Use an API Token, not your password!

        // 2. Instantiate an API class
        PageApi pageApi = new PageApi(client);

        try {
            // 3. Call the API
            Page page = pageApi.getPageById("123456");
            System.out.println("Page Title: " + page.getTitle());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

### Managing Attachments

Use the `ContentAttachmentsApi` for uploading files.

```java
import net.atlassian.wiki.rest.v1.api.ContentAttachmentsApi;
import java.io.File;

// ... inside your method
ContentAttachmentsApi attachmentApi = new ContentAttachmentsApi(client);
File file = new File("path/to/image.png");

// Upload attachment
attachmentApi.createOrUpdateAttachments(
    "123456",               // Content ID
    file,                   // File object
    "true",                 // Minor edit (no notification)
    "current",              // Status
    "Uploaded via API",     // Comment
    null                    // Additional headers
);
```

## Generation

This client is generated using the [OpenAPI Generator](https://openapi-generator.tech) maven plugin.
- **Input Spec:** `src/main/openapi/ConfluenceV2.json`
- **Output Package:** `net.atlassian.wiki.rest`

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](../LICENSE) file for details.