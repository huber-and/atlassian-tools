# Atlassian Tools

A comprehensive suite of Java tools and libraries for integrating with Atlassian Confluence, including a REST API client, HTML publishing utility, and Maven plugin.

## Project Structure

This is a multi-module Maven project containing:

### 1. **wiki-client** - Confluence REST API Client
A generated Java client library for the Confluence REST API v2, built with OpenAPI Generator.
- **Description**: Confluence REST API wrapper
- **Package**: `net.atlassian.wiki.rest`
- **Key Dependencies**: Apache HttpClient 5, Jackson, Swagger Annotations
- **Features**:
  - Auto-generated from OpenAPI specification (ConfluenceV2.json)
  - RESTful API client for Confluence operations
  - Full Java 21 compatibility
  - Uses Jakarta EE annotations

### 2. **wiki-publisher** - HTML to Confluence Publisher
Utility library for parsing HTML files and publishing them to Confluence in Confluence Storage Format.
- **Description**: Helper to parse HTML files and upload to Confluence
- **Key Dependencies**: wiki-client, jsoup, Jackson
- **Features**:
  - HTML parsing and conversion
  - Confluence Storage Format support
  - Direct dependency on wiki-client for API operations

### 3. **atlassian-maven-plugin** - Maven Integration Plugin
A Maven plugin that integrates the wiki-publisher functionality into the Maven build lifecycle.
- **Description**: Atlassian tools Maven plugin
- **Package Name**: `atlassian-maven-plugin`
- **Key Dependencies**: Maven Plugin API, wiki-publisher
- **Features**:
  - Maven mojo implementations for automating Confluence operations
  - Integration with Maven build process
  - Auto-generated help mojo

### 4. **arc42-sample** - Sample Project
A sample project demonstrating the usage of the Atlassian tools.

## Requirements

- **Java**: 21+
- **Maven**: 3.6+

## Building

Build the entire project:

```bash
mvn clean install
```

Build a specific module:

```bash
mvn -pl wiki-client clean install
mvn -pl wiki-publisher clean install
mvn -pl atlassian-maven-plugin clean install
```

## Usage

### As a Library (wiki-client or wiki-publisher)

Add the dependency to your `pom.xml`:

```xml
<!-- For REST API client -->
<dependency>
    <groupId>com.github.huber-and.atlassian</groupId>
    <artifactId>wiki-client</artifactId>
    <version>0.1-SNAPSHOT</version>
</dependency>

<!-- For HTML publishing -->
<dependency>
    <groupId>com.github.huber-and.atlassian</groupId>
    <artifactId>wiki-publisher</artifactId>
    <version>0.1-SNAPSHOT</version>
</dependency>
```

### As a Maven Plugin

Add the plugin to your `pom.xml`:

```xml
<plugin>
    <groupId>com.github.huber-and.atlassian</groupId>
    <artifactId>atlassian-maven-plugin</artifactId>
    <version>0.1-SNAPSHOT</version>
    <!-- Configuration here -->
</plugin>
```

## References

- [Confluence REST API Documentation](https://developer.atlassian.com/cloud/confluence/rest/v2/)
- [Confluence Storage Format](https://confluence.atlassian.com/doc/confluence-storage-format-790796544.html)
- [OpenAPI Generator](https://openapi-generator.tech/)

## License

See LICENSE file for details.