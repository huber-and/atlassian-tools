# Security Policy

## Supported Versions

Security fixes go into the next release. There are no backports to earlier versions — with a
single maintainer, a backport branch would be a promise that could not be kept reliably.

| Version | Supported |
| :--- | :--- |
| latest release on [Maven Central](https://mvnrepository.com/artifact/io.github.huber-and.atlassian) | ✅ |
| any earlier version | ❌ upgrade to the latest |

Versions are `0.x`, so breaking changes can occur between minor versions. See the
[release notes](https://github.com/huber-and/atlassian-tools/releases) before upgrading.

## Reporting a Vulnerability

Please report security issues **privately**, not as a public issue:

> **[Report a vulnerability](https://github.com/huber-and/atlassian-tools/security/advisories/new)**

This uses GitHub's private vulnerability reporting. Only the maintainer sees the report, and it
can be turned into a published advisory once a fix is available.

What helps in a report: the affected version, the configuration involved, and what an attacker
would gain. A proof of concept is welcome but not required.

What to expect: an acknowledgement within 14 days at the latest, and an honest assessment of when
a fix can happen. This is a single-maintainer project without a paid support commitment — if something is
urgent for you, say so in the report.

Please do not disclose the issue publicly until a fixed version is released, or until we agree
that no fix is needed.

## Threat Model

The tools in this repository run at **build time**, not as a service. There is no listening
socket, no request handling and no multi-tenant state — so the interesting attack surface is not
a remote one. What matters instead:

**Credentials.** The publisher authenticates against Confluence with an account that can create,
change and delete pages. Anything that could expose those credentials — a log line, an exception
message, a configuration dump — is treated as a security issue. Accordingly, `username` and
`password` are configuration-only parameters with no CLI property, so they cannot be passed as
`-D` arguments and end up in a CI log; both are excluded from `Configuration.toString()`; and only
the host of the target URL is logged, never the full URL.

**Untrusted content from the documentation source.** The parser reads HTML that the project did
not write: hrefs in the navigation, `img src` attributes in page bodies. Every one of those values
is resolved through `SafePaths`, which rejects absolute paths and paths escaping the configured
source directory. A bypass of that guard is a security issue.

**Dynamic class loading.** `parserClass` names a class that is loaded at runtime. It is resolved
without running its static initializers and is only instantiated after it has been verified to
implement `Parser`. The value comes from build configuration, which already implies code execution
in a Maven build — but a way to execute code from a source that is *not* build configuration would
be a security issue.

**Destructive operations against the target space.** With `deleteOrphans` enabled, pages that no
longer exist locally are moved to the Confluence trash. This is scoped to the descendants of the
configured root page and uses the trash rather than a purge. A way to make the tool delete outside
that scope, or to purge irrecoverably, is a security issue.

### Out of scope

* Findings that require control over the build configuration, `settings.xml`, or the Maven
  command line — these already grant code execution in any Maven build.
* Vulnerabilities in Confluence itself. Please report those to
  [Atlassian](https://www.atlassian.com/trust/security/report-a-vulnerability).
* Outdated transitive dependencies without a demonstrated impact on this project. Dependency
  updates are handled by Dependabot; a report is welcome if you can show how a known
  vulnerability is actually reachable here.
