/*
 * Copyright 2024-2026 Andreas Huber
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.huber_and.atlassian.wiki.transformer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.huber_and.atlassian.wiki.Page;
import io.github.huber_and.atlassian.wiki.transformer.LinkResolver.Kind;

/**
 * Unit tests for {@link LinkResolver}.
 *
 * Builds a two-mapper layout on disk so that both the same-space and the
 * cross-space case are covered without a live Confluence instance.
 *
 * @author Andreas Huber
 */
class LinkResolverTest {

	private Path chapter9;
	private Path adr;
	private Path other;
	private Path spec;
	private LinkResolver resolver;

	@BeforeEach
	void setUp(@TempDir final Path tmp) throws IOException {
		final var docsA = Files.createDirectories(tmp.resolve("docsA"));
		final var docsB = Files.createDirectories(tmp.resolve("docsB"));
		Files.writeString(docsA.resolve("index.html"), "");
		chapter9 = Files.writeString(docsA.resolve("09_decisions.html"), "");
		adr = Files.writeString(Files.createDirectories(docsA.resolve("adr")).resolve("0001.html"), "");
		spec = Files.writeString(docsA.resolve("spec.pdf"), "pdf");
		other = Files.writeString(docsB.resolve("other.html"), "");
		// exists on disk but outside every mapper path
		Files.writeString(tmp.resolve("outside.pdf"), "pdf");

		resolver = LinkResolver.of(List.of(
				new LinkResolver.MapperPages("SPACEA", docsA, "Architecture",
						List.of(new Page("Chapter 9", chapter9, null), new Page("ADR 1", adr, null))),
				new LinkResolver.MapperPages("SPACEB", docsB, null,
						List.of(new Page("Other Doc", other, null)))));
	}

	/** A link inside the same mapper resolves to a page without a space key. */
	@Test
	void linkWithinSameSpaceResolvesToPage() {
		final var target = resolver.resolve(chapter9, "adr/0001.html").orElseThrow();

		assertEquals(Kind.PAGE, target.kind());
		assertEquals("ADR 1", target.title());
		assertFalse(target.foreign(), "Target in the same space must not carry a space key");
	}

	/**
	 * The case the second mapper exists for: the target is known, but lives in
	 * another space and therefore needs the space key.
	 */
	@Test
	void linkIntoOtherMapperIsMarkedForeign() {
		final var target = resolver.resolve(chapter9, "../docsB/other.html").orElseThrow();

		assertEquals(Kind.PAGE, target.kind());
		assertEquals("Other Doc", target.title());
		assertEquals("SPACEB", target.spaceKey());
		assertTrue(target.foreign(), "Cross-space target must be marked foreign");
	}

	/** A fragment on a page link is carried over as the anchor. */
	@Test
	void fragmentIsCarriedOver() {
		final var target = resolver.resolve(chapter9, "adr/0001.html#_context").orElseThrow();

		assertEquals(Kind.PAGE, target.kind());
		assertEquals("_context", target.anchor());
	}

	/** A pure fragment addresses the page the link appears on. */
	@Test
	void pureFragmentResolvesToAnchorOnSamePage() {
		final var target = resolver.resolve(chapter9, "#_quality_goals").orElseThrow();

		assertEquals(Kind.ANCHOR, target.kind());
		assertEquals("_quality_goals", target.anchor());
	}

	/** The synthetic root page makes a link to index.html resolvable. */
	@Test
	void linkToIndexResolvesToConfiguredRootPage() {
		final var target = resolver.resolve(chapter9, "index.html").orElseThrow();

		assertEquals(Kind.PAGE, target.kind());
		assertEquals("Architecture", target.title());
	}

	/** Everything with a scheme, and protocol-relative URLs, must be left alone. */
	@Test
	void externalLinksAreRecognized() {
		assertEquals(Kind.EXTERNAL, resolver.resolve(chapter9, "https://example.org/a").orElseThrow().kind());
		assertEquals(Kind.EXTERNAL, resolver.resolve(chapter9, "mailto:someone@example.org").orElseThrow().kind());
		assertEquals(Kind.EXTERNAL, resolver.resolve(chapter9, "//cdn.example.org/x.js").orElseThrow().kind());
	}

	/** A blank href carries no information and is not resolved at all. */
	@Test
	void blankHrefIsNotResolved() {
		assertTrue(resolver.resolve(chapter9, "").isEmpty());
		assertTrue(resolver.resolve(chapter9, "   ").isEmpty());
	}

	/** An HTML page that was never published cannot be linked. */
	@Test
	void unknownPageIsUnresolved() {
		assertEquals(Kind.UNRESOLVED, resolver.resolve(chapter9, "10_not_in_nav.html").orElseThrow().kind());
	}

	/** A non-HTML file inside a mapper path becomes an attachment. */
	@Test
	void fileInsideMapperBecomesAttachment() {
		final var target = resolver.resolve(chapter9, "spec.pdf").orElseThrow();

		assertEquals(Kind.ATTACHMENT, target.kind());
		assertEquals(spec, target.file());
		assertEquals("spec.pdf", target.fileName());
	}

	/**
	 * The security boundary: the file exists, but outside every mapper path. It must
	 * not be pulled into the wiki as an attachment.
	 */
	@Test
	void fileOutsideEveryMapperIsUnresolved() {
		assertEquals(Kind.UNRESOLVED, resolver.resolve(chapter9, "../outside.pdf").orElseThrow().kind());
	}

	/** An empty resolver resolves no internal link, but still detects external ones. */
	@Test
	void emptyResolverResolvesNothingInternal() {
		final var empty = LinkResolver.empty();

		assertEquals(Kind.UNRESOLVED, empty.resolve(chapter9, "adr/0001.html").orElseThrow().kind());
		assertEquals(Kind.EXTERNAL, empty.resolve(chapter9, "https://example.org").orElseThrow().kind());
	}
}
