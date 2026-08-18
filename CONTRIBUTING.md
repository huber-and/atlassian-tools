# Contributing

Thanks for looking. Issues, questions and pull requests are all welcome — including "this does not
work for my Antora setup", which is the most useful kind of report for a project this young.

## Building

```bash
./mvnw clean install
```

Requirements: **JDK 21** and **Maven 3.9.2** or later (the Maven floor comes from the release
tooling, not from the code). Node and Antora are downloaded into the build by
`frontend-maven-plugin` where they are needed — no global installation required.

The reactor has four modules, in dependency order:

| Module | What it knows about |
| :--- | :--- |
| `wiki-client` | the Confluence HTTP API, nothing about documentation |
| `wiki-publisher` | documentation structure and the publishing workflow, nothing about Maven |
| `atlassian-maven-plugin` | Maven parameters, `settings.xml`, the build lifecycle |
| `architecture-docs` | neither — it is the documentation and the end-to-end example |

Please keep that separation: no Maven type may leak into `wiki-publisher`, because that is what
keeps the library usable outside a Maven build.

## Tests

New functionality gets a test in the same pull request — a feature without one is not considered done. `parserClass`, `deleteOrphans` and the link resolver each landed with tests covering the new behaviour; that is the standard to match, not a discussion point.

`./mvnw test` runs the full suite. It needs no network and no Confluence instance.

Tests whose name ends in `TestLocal` are integration tests against a live Confluence instance or a
locally built Antora site. They are excluded from the repository via `.gitignore` and never run in
CI. If you write one, expect it to stay on your machine.

There is a known gap: `updatePages` and `syncPageStructure` in `ConfluenceClient` have no covering
unit test. CI publishes this project's own documentation to a real space on every relevant push,
which exercises the API interaction — but nothing asserts the result. Tests that close that gap are
very welcome.

## Code style

* Tabs for indentation, matching the surrounding code.
* `final` on locals and parameters where it fits the existing style.
* Javadoc on every public type and method. Explain *why*, not *what* — the signature already says
  what.
* Apache 2.0 licence header in every new source file; copy it from a neighbouring file.
* SLF4J with parameterised messages, and the log levels carry meaning: `info` for what happened to
  the space, `debug` for diagnostic detail, `warn` for skipped-but-continued, `error` for a failed
  unit of work.
* Never log credentials, and do not add a CLI property to a parameter that carries one. See
  `SECURITY.md` for why.

## Two things that will surprise you

**The OpenAPI specification is patched.** `wiki-client` is generated from
`src/main/openapi/ConfluenceV2.json`, and that file is *not* upstream verbatim. After refreshing it
from Atlassian, four `likes/count` response schemas need their title changed from `Integer` to
`Count`, otherwise the generator emits a model class that clashes with `java.lang.Integer` and the
build does not compile. The `wiki-client` README documents the exact change.

**The documentation publishes itself.** `architecture-docs` holds this project's arc42
documentation, which is built with Antora and published by this very plugin. To see it locally:

```bash
./mvnw -pl architecture-docs clean compile
```

The site lands in `architecture-docs/target/docs`. If you change behaviour that shows up in the
published output, please update the corresponding chapter in the same pull request.

## Pull requests

`main` accepts direct pushes from the maintainer, but pull requests are the way to contribute.
Nothing fancy is required:

* one topic per pull request, so it can be reviewed and reverted on its own,
* `./mvnw clean install` green before you open it,
* commit messages in the imperative mood, first line under about 72 characters, and a body that
  explains the reasoning when the change is not obvious.

CI runs the build and CodeQL on every pull request. Force pushes to `main` and moving existing tags
are blocked by rulesets — released tags stay immutable, because they identify the code behind a
signed artifact on Maven Central.
