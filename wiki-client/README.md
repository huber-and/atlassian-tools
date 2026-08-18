# Confluence REST API Client

[![Maven Central](https://img.shields.io/maven-central/v/io.github.huber-and.atlassian/wiki-client?label=Maven%20Central)](https://mvnrepository.com/artifact/io.github.huber-and.atlassian/wiki-client)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](../LICENSE)

The **wiki-client** is a Java library that provides a comprehensive client for the Atlassian Confluence Cloud REST API. It is primarily generated from the Confluence OpenAPI specification (v2), ensuring up-to-date coverage of the API endpoints, with additional support for specific v1 operations like attachment management.

## Features

- **Comprehensive API Coverage:** Generated from the official Confluence OpenAPI v2 specification.
- **Attachment Support:** Includes a specialized `ContentAttachmentsApi` for managing attachments (based on v1 API).
- **Modern Java:** Built for Java 21+.
- **Strongly Typed:** Uses Jackson for JSON serialization/deserialization and provides strongly typed models for all API resources.
- **HTTP Client 5:** Powered by Apache HttpClient 5 for robust network communication.

## Installation

Add the dependency to your `pom.xml`. The badge above always shows the latest released version:

```xml
<dependency>
    <groupId>io.github.huber-and.atlassian</groupId>
    <artifactId>wiki-client</artifactId>
    <version>0.2.2</version>
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

Use the `ContentAttachmentsApi` for uploading files. This is the one place where the
client falls back to the v1 API: REST API v2 has no endpoint for creating an
attachment — its attachment resources are read-only apart from properties. Atlassian
tracks the gap as [CONFCLOUD-77196](https://jira.atlassian.com/browse/CONFCLOUD-77196),
which sits in *Future Consideration* without an assignee or ETA (as of July 2026), so
`PUT /rest/api/content/{id}/child/attachment` stays the only option. That v1 operation
is not marked as deprecated.

Because the endpoint accepts `multipart/form-data`, Confluence guards it against XSRF
and rejects requests without an `X-Atlassian-Token: nocheck` header.

```java
import net.atlassian.wiki.rest.v1.api.ContentAttachmentsApi;
import java.io.File;
import java.util.Map;

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
    Map.of("X-Atlassian-Token", "nocheck") // Additional headers, required
);
```

## Generation

This client is generated using the [OpenAPI Generator](https://openapi-generator.tech) maven plugin.
- **Input Spec:** `src/main/openapi/ConfluenceV2.json`
- **Output Package:** `net.atlassian.wiki.rest`

### Updating the OpenAPI spec

The bundled spec comes from
<https://developer.atlassian.com/cloud/confluence/openapi-v2.v3.json> (upstream version
2.0.0, server `https://{your-domain}/wiki/api/v2`).

The local copy is **patched** and must not simply be overwritten. After replacing it,
re-apply the following change, otherwise the generated sources do not compile:

- In the `200` response schema of `/pages/{id}/likes/count`, `/blogposts/{id}/likes/count`,
  `/footer-comments/{id}/likes/count` and `/inline-comments/{id}/likes/count`, change
  `"title": "Integer"` to `"title": "Count"` (4 occurrences). Upstream's title makes the
  generator emit a model class named `Integer`, which clashes with `java.lang.Integer`.

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](../LICENSE) file for details.