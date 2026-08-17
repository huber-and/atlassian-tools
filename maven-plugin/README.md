# Atlassian Tools Maven Plugin

The **Atlassian Tools Maven Plugin** integrates Confluence page publishing directly into your Maven build lifecycle. It allows you to automate the process of publishing documentation to Confluence spaces based on your project's content.

## Features

- **Automated Publishing:** Publish documentation to Confluence as part of your build.
- **Flexible Mapping:** Map different local paths to specific Confluence spaces using `mappers`.
- **Secure Authentication:** Supports credential management via Maven `settings.xml` or direct configuration.
- **Orphan Cleanup:** Optionally move Confluence pages that no longer exist locally to the trash.
- **Pluggable Parser:** Swap the built-in Antora parser for your own implementation.

## Requirements

- Java 21 or later
- Maven 3.9.2 or later

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

| Parameter | Type | Required | CLI property | Description |
| :--- | :--- | :--- | :--- | :--- |
| `url` | `String` | **Yes** | `url` | The base URL of your Confluence instance (e.g., `https://confluence.example.com`). |
| `mappers` | `Set<Mapper>` | **Yes** | – | A list of mappings defining which local content goes to which Confluence space. |
| `username` | `String` | No | – | The username for authentication. If omitted, the plugin looks up credentials in Maven settings. Deliberately has no CLI property so credentials cannot leak into build logs. |
| `password` | `String` | No | – | The password or API token for authentication. Deliberately has no CLI property. |
| `serverId` | `String` | No | `serverId` | ID of the `<server>` entry in `settings.xml` to read credentials from. Defaults to the host part of `url`. |
| `skip` | `boolean` | No | `atlassian.skip` | If `true`, the goal does nothing. Defaults to `false`. |
| `parserClass` | `String` | No | `page.parser` | Fully qualified class name of the parser used to read the local content. Defaults to `io.github.huber_and.atlassian.wiki.parser.AntoraParser`. |

### Mapper Configuration

Each `mapper` element inside `mappers` supports:

| Option | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `spaceKey` | `String` | **Yes** | The Key of the Confluence Space where pages will be published. |
| `path` | `String` | **Yes** | The path to the local directory containing the content to publish. |
| `root` | `String` | No | Title of an existing page in the space to publish the content under. |
| `deleteOrphans` | `boolean` | No | If `true` (the default), pages below `root` that no longer exist locally are moved to the Confluence trash. If `false`, they are only reported in the build log, which lets you preview the effect first. Requires `root` to be set — without it the scope would be the whole space. |

### Custom Parser

`parserClass` lets you replace the built-in Antora parser. The class must implement
`io.github.huber_and.atlassian.wiki.parser.Parser`, provide a public no-argument constructor,
and be visible on the plugin's classpath — add it as a `<dependency>` of the plugin declaration:

```xml
<plugin>
    <groupId>io.github.huber-and.atlassian</groupId>
    <artifactId>atlassian-maven-plugin</artifactId>
    <version>${atlassian-tools.version}</version>
    <configuration>
        <parserClass>com.example.docs.MyParser</parserClass>
    </configuration>
    <dependencies>
        <dependency>
            <groupId>com.example</groupId>
            <artifactId>my-parser</artifactId>
            <version>1.0.0</version>
        </dependency>
    </dependencies>
</plugin>
```

The class is resolved without running its static initializers and is only instantiated after it
has been verified to implement `Parser`, so a typo in the name fails the build instead of
executing unrelated code.

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
