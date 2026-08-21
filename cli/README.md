# Atlassian Tools CLI

[![Maven Central](https://img.shields.io/maven-central/v/io.github.huber-and.atlassian/atlassian-cli?label=Maven%20Central)](https://mvnrepository.com/artifact/io.github.huber-and.atlassian/atlassian-cli)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](../LICENSE)

A standalone command-line tool for publishing documentation to Confluence — for Gradle projects,
plain scripts, and CI pipelines that don't use Maven. It is a thin wrapper around
[`wiki-publisher`](../wiki-publisher): no functionality lives here that isn't already in that
library.

> **Live example:** this plugin publishes its own project's architecture documentation.
> Compare the [generated Antora site](https://huber-and.github.io/atlassian-tools) with the
> [resulting Confluence pages](https://the-hubers.atlassian.net/wiki/spaces/AT/pages/65437698/Architecture) — same source, both targets, publicly readable.

## Features

- **Single self-contained executable**: a shaded jar with no runtime dependencies to resolve.
- **Single- or multi-mapper publishing**: flags for one Confluence space, or a YAML `--config`
  file for several.
- **No plaintext password flag**: credentials come from environment variables, a permission-checked
  file, or an interactive prompt — never from a command-line argument. See "Authentication" below.
- **Pluggable parser**: the same `parserClass` mechanism as `wiki-publisher` and the Maven plugin,
  with a `--plugin-dir` option to extend the shaded jar's otherwise closed classpath.

## Requirements

- Java 21 or later.

## Installation

### Launcher script (recommended)

A small script that checks GitHub for the latest release every time it runs, downloads it into a
local cache if it is not already there or is out of date, and then runs it with whatever arguments
you gave the script. There is nothing to upgrade by hand.

**Linux / macOS** (needs `curl` and Java 21+ on `PATH`):

```bash
curl -fsSL https://raw.githubusercontent.com/huber-and/atlassian-tools/main/cli/scripts/atlassian-cli -o atlassian-cli
chmod +x atlassian-cli
./atlassian-cli --help
```

**Windows** (needs Java 21+ on `PATH`; PowerShell 5.1+ or PowerShell 7+):

```powershell
Invoke-WebRequest -Uri https://raw.githubusercontent.com/huber-and/atlassian-tools/main/cli/scripts/atlassian-cli.ps1 -OutFile atlassian-cli.ps1
powershell -ExecutionPolicy Bypass -File .\atlassian-cli.ps1 --help
```

`-ExecutionPolicy Bypass` only applies to this one invocation; it does not change any system-wide
setting. Both scripts cache the jar under `~/.atlassian-cli` (`$env:ATLASSIAN_CLI_HOME` /
`%USERPROFILE%\.atlassian-cli` if `ATLASSIAN_CLI_HOME` is unset) and re-check the latest release on
every run — set `GITHUB_TOKEN` in the environment to avoid GitHub's low unauthenticated API rate
limit if you invoke the script often (e.g. from CI). The scripts themselves change rarely; there is
no versioned copy to keep in sync — re-downloading the one above always gets the current logic.

### Download the executable jar manually

Download `atlassian-cli-<version>-shaded.jar` from the project's
[GitHub Releases](https://github.com/huber-and/atlassian-tools/releases) and run it directly:

```bash
java -jar atlassian-cli-<version>-shaded.jar --help
```

### As a library dependency

The plain (non-shaded) jar is published to Maven Central like the other modules:

```xml
<dependency>
    <groupId>io.github.huber-and.atlassian</groupId>
    <artifactId>atlassian-cli</artifactId>
    <version>${atlassian-tools.version}</version>
</dependency>
```

## Usage

Publish a single local site to one Confluence space:

```bash
java -jar atlassian-cli.jar \
  --url https://confluence.example.com \
  --space-key DOCS \
  --path build/site \
  --root "Architecture"
```

Publish several mappers from one YAML file:

```bash
java -jar atlassian-cli.jar --url https://confluence.example.com --config publish.yaml
```

```yaml
# publish.yaml
parserClass: io.github.huber_and.atlassian.wiki.parser.AntoraParser
mappers:
  - spaceKey: DOCS
    path: build/site
    root: Architecture
    deleteOrphans: true
  - spaceKey: API
    path: build/api-site
```

`--config` and the single-mapper flags (`--space-key`/`--path`/`--root`/`--delete-orphans`) are
mutually exclusive. `--url` and `--parser-class` given on the command line override the
corresponding value in the file.

## Options

| Option | Description |
| :--- | :--- |
| `--url <url>` | Base URL of the Confluence instance. Required. |
| `--space-key <key>` | Confluence space key. Single-mapper mode. |
| `--path <path>` | Local directory to publish. Single-mapper mode. |
| `--root <title>` | Title of the root page to publish under. Optional. |
| `--delete-orphans` / `--no-delete-orphans` | Whether pages that no longer exist locally are moved to the trash. Defaults to `true`, matching `wiki-publisher`. |
| `--config <file.yaml>` | Read one or more mappers from a YAML file. Cannot be combined with the single-mapper flags above. |
| `--parser-class <fqcn>` | Fully qualified name of the `Parser` implementation to use. Defaults to the built-in Antora parser. |
| `--plugin-dir <dir>` | Directory of additional jars searched for `--parser-class`. See "Custom Parser" below. |
| `--username <name>` | Confluence account name. Not a secret — may be given on the command line. |
| `--credentials-file <file>` | File with `username`/`password` entries. Rejected unless it is readable/writable by its owner only (`chmod 600`). |
| `--debug` | Dry run: resolve everything, but do not write to Confluence. |
| `--verbose`, `-v` | Print stack traces on failure and enable debug logging. |
| `--help`, `-h` | Print usage and exit. |
| `--version` | Print the version and exit. |

## Authentication

There is deliberately no `--password` flag. A command-line argument is not the same threat as a
Maven `-D` property leaking into a CI log — the reason `atlassian-maven-plugin` gives `username`
and `password` no CLI property — because a standalone process's argument list is *also* visible
through shell history and through `ps`/`/proc/<pid>/cmdline` for as long as the process runs. Both
are worse than a scrubbed CI log.

Credentials are resolved in this order:

1. **Environment variables** `CONFLUENCE_USERNAME` / `CONFLUENCE_API_TOKEN` — the CI-friendly path.
2. **`--credentials-file <file>`** — a small properties file:
   ```properties
   username=you@example.com
   password=your-api-token
   ```
   Rejected with a clear error unless its permissions are owner-only (`chmod 600 <file>`); the
   check is skipped, with a one-time notice, on file systems without POSIX permissions.
3. **An interactive prompt**, only when a real terminal is attached — never in a non-interactive
   environment, so the CLI fails fast instead of hanging.

`--username` may still be given on the command line in any of the three cases; only the password
or API token is never accepted that way.

## Custom Parser

`--parser-class` replaces the built-in Antora parser, exactly like in `wiki-publisher` and the
Maven plugin — see [`wiki-publisher`'s README](../wiki-publisher/README.md#custom-parser) for what
the class must implement.

- **Plain jar**: put your parser's jar on the classpath yourself, e.g.
  `java -cp atlassian-cli.jar:my-parser.jar io.github.huber_and.atlassian.cli.Main ...`.
- **Shaded jar**: its classpath is closed, so use `--plugin-dir <dir>` instead — every `*.jar`
  directly inside that directory is added to a class loader consulted before the shaded jar's own,
  the same class-loading order `Publisher` already uses for the Maven plugin's plugin realm.

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](../LICENSE) file for details.
