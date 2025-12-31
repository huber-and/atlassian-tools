# Atlassian Tools Maven Plugin

The **Atlassian Tools Maven Plugin** integrates Confluence page publishing directly into your Maven build lifecycle. It allows you to automate the process of publishing documentation to Confluence spaces based on your project's content.

## Features

- **Automated Publishing:** Publish documentation to Confluence as part of your build.
- **Flexible Mapping:** Map different local paths to specific Confluence spaces using `mappers`.
- **Secure Authentication:** Supports credential management via Maven `settings.xml` or direct configuration.

## Requirements

- Java 17 or later
- Maven 3.x

## Usage

Add the plugin to your `pom.xml` build section. You can configure it to run during a specific phase or execute it manually.

```xml
<build>
    <plugins>
        <plugin>
            <groupId>io.github.huber-and.atlassian</groupId>
            <artifactId>atlassian-maven-plugin</artifactId>
            <version>${atlassian-tools.version}</version>
            <configuration>
                <url>https://confluence.example.com</url>
                <mappers>
                    <mapper>
                        <!-- The key of the Confluence space -->
                        <spaceKey>MYSPACE</spaceKey>
                        <!-- The local directory path containing the documentation -->
                        <path>src/docs</path>
                    </mapper>
                </mappers>
            </configuration>
            <executions>
                <execution>
                    <id>publish-docs</id>
                    <phase>site</phase> <!-- or any other phase like deploy -->
                    <goals>
                        <goal>publish</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

## Goals

### `publish`

The main goal of this plugin is `atlassian:publish`. It reads the configured local documentation and publishes it to the specified Confluence instance.

## Configuration Parameters

| Parameter | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `url` | `String` | **Yes** | The base URL of your Confluence instance (e.g., `https://confluence.example.com`). |
| `mappers` | `Set<Mapper>` | **Yes** | A list of mappings defining which local content goes to which Confluence space. |
| `username` | `String` | No | The username for authentication. If omitted, the plugin looks up credentials in Maven settings. |
| `password` | `String` | No | The password or API token for authentication. |

### Mapper Configuration

Each `mapper` element inside `mappers` requires:
- `spaceKey`: The Key of the Confluence Space where pages will be published.
- `path`: The path to the local directory containing the content to publish.

## Authentication

You can provide credentials in two ways:

### 1. Maven Settings (Recommended)

Configure your credentials in your `~/.m2/settings.xml`. The `<id>` of the server must match the **host** part of the Confluence URL configured in the plugin.

**Example `settings.xml`:**

```xml
<settings>
  <servers>
    <server>
      <id>confluence.example.com</id>
      <username>my-username</username>
      <password>my-secret-password-or-token</password>
    </server>
  </servers>
</settings>
```

**Plugin Config:**
```xml
<configuration>
    <url>https://confluence.example.com</url>
    <!-- username and password omitted here -->
    ...
</configuration>
```

### 2. Direct Configuration (Not Recommended for CI/CD)

You can specify the `username` and `password` directly in the plugin configuration.

```xml
<configuration>
    <url>https://confluence.example.com</url>
    <username>my-username</username>
    <password>my-secret-password-or-token</password>
    ...
</configuration>
```

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](../LICENSE) file for details.
