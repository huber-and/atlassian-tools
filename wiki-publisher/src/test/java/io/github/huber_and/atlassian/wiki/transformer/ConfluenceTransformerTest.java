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

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.huber_and.atlassian.wiki.Page;

class ConfluenceTransformerTest {

	private final ConfluenceTransformer transformer = new ConfluenceTransformer();

	@Test
	void pathTraversalImageIsSkipped(@TempDir final Path tmp) throws IOException {
		final var pageDir = Files.createDirectories(tmp.resolve("space/section"));
		final var pageFile = Files.writeString(pageDir.resolve("page.html"), "");
		final var page = new Page("Page", pageFile, null);

		final var html = "<div><img src=\"../../../etc/passwd\" alt=\"x\"/></div>";
		final var content = Jsoup.parse(html).body();

		final var result = transformer.transform(page, content);

		assertTrue(result.getAttachments().isEmpty(),
				"Path-traversal image must not produce an attachment");
		assertFalse(result.getContent().contains("etc/passwd"),
				() -> "Storage format leaked traversal target: " + result.getContent());
	}

	@Test
	void unknownLanguageIsDropped() {
		final var page = new Page("Page", Path.of("/tmp/page.html"), null);
		final var html = "<pre><code data-lang=\"&lt;script&gt;\">x = 1</code></pre>";
		final var content = Jsoup.parse(html).body();

		final var result = transformer.transform(page, content);

		assertFalse(result.getContent().contains("<script>"),
				() -> "language attribute leaked: " + result.getContent());
		assertFalse(result.getContent().contains("ac:name=\"language\""),
				() -> "Unknown language should drop the language parameter, but got: "
						+ result.getContent());
	}

	@Test
	void knownLanguageIsKept() {
		final var page = new Page("Page", Path.of("/tmp/page.html"), null);
		final var html = "<pre><code data-lang=\"java\">x = 1</code></pre>";
		final var content = Jsoup.parse(html).body();

		final var result = transformer.transform(page, content);

		assertTrue(result.getContent().contains("ac:name=\"language\""));
		assertTrue(result.getContent().contains(">java<"),
				() -> "Expected language 'java' in storage format: " + result.getContent());
	}

	@Test
	void nonNumericWidthIsDropped(@TempDir final Path tmp) throws IOException {
		final var pageDir = Files.createDirectories(tmp.resolve("space"));
		final var pageFile = Files.writeString(pageDir.resolve("page.html"), "");
		Files.writeString(pageDir.resolve("foo.png"), "x");
		final var page = new Page("Page", pageFile, null);

		final var html = "<div><img src=\"foo.png\" width=\"100;background:url(javascript:alert(1))\"/></div>";
		final var content = Jsoup.parse(html).body();

		final var result = transformer.transform(page, content);

		assertFalse(result.getContent().contains("javascript"),
				() -> "Width attribute injected unsafe content: " + result.getContent());
		assertFalse(result.getContent().contains("ac:width"),
				() -> "Non-numeric width must be dropped: " + result.getContent());
	}

	@Test
	void numericWidthIsKept(@TempDir final Path tmp) throws IOException {
		final var pageDir = Files.createDirectories(tmp.resolve("space"));
		final var pageFile = Files.writeString(pageDir.resolve("page.html"), "");
		Files.writeString(pageDir.resolve("foo.png"), "x");
		final var page = new Page("Page", pageFile, null);

		final var html = "<div><img src=\"foo.png\" width=\"480px\"/></div>";
		final var content = Jsoup.parse(html).body();

		final var result = transformer.transform(page, content);

		assertTrue(result.getContent().contains("ac:width=\"480px\""),
				() -> "Numeric width must be preserved: " + result.getContent());
	}

	// --- link resolution -------------------------------------------------

	/** A two-mapper fixture, mirroring the layout {@link LinkResolverTest} uses. */
	private record Fixture(ConfluenceTransformer transformer, Page chapter, Path spec) {
	}

	private static Fixture fixture(final Path tmp) throws IOException {
		final var docsA = Files.createDirectories(tmp.resolve("docsA"));
		final var docsB = Files.createDirectories(tmp.resolve("docsB"));
		final var chapterFile = Files.writeString(docsA.resolve("09_decisions.html"), "");
		final var adrFile = Files.writeString(Files.createDirectories(docsA.resolve("adr")).resolve("0001.html"), "");
		final var specFile = Files.writeString(docsA.resolve("spec.pdf"), "pdf");
		final var otherFile = Files.writeString(docsB.resolve("other.html"), "");
		final var chapter = new Page("Chapter 9", chapterFile, null);

		final var resolver = LinkResolver.of(List.of(
				new LinkResolver.MapperPages("SPACEA", docsA, "Architecture",
						List.of(chapter, new Page("ADR 1", adrFile, null))),
				new LinkResolver.MapperPages("SPACEB", docsB, null,
						List.of(new Page("Other Doc", otherFile, null)))));
		return new Fixture(new ConfluenceTransformer(resolver), chapter, specFile);
	}

	@Test
	void internalLinkBecomesPageLinkWithoutSpaceKey(@TempDir final Path tmp) throws IOException {
		final var f = fixture(tmp);
		final var content = Jsoup.parse("<p><a href=\"adr/0001.html\">Decision One</a></p>").body();

		final var storage = f.transformer().transform(f.chapter(), content).getContent();

		assertTrue(storage.contains("<ac:link"), () -> storage);
		assertTrue(storage.contains("ri:content-title=\"ADR 1\""), () -> storage);
		assertTrue(storage.contains("<![CDATA[Decision One]]>"), () -> storage);
		assertFalse(storage.contains("ri:space-key"), () -> "Same-space link must not carry a space key: " + storage);
		assertFalse(storage.contains("href="), () -> "The relative href must be gone: " + storage);
	}

	@Test
	void crossSpaceLinkCarriesSpaceKey(@TempDir final Path tmp) throws IOException {
		final var f = fixture(tmp);
		final var content = Jsoup.parse("<p><a href=\"../docsB/other.html\">Other</a></p>").body();

		final var storage = f.transformer().transform(f.chapter(), content).getContent();

		assertTrue(storage.contains("ri:content-title=\"Other Doc\""), () -> storage);
		assertTrue(storage.contains("ri:space-key=\"SPACEB\""), () -> storage);
	}

	@Test
	void fragmentBecomesAcAnchor(@TempDir final Path tmp) throws IOException {
		final var f = fixture(tmp);
		final var content = Jsoup.parse("<p><a href=\"adr/0001.html#_context\">Context</a></p>").body();

		final var storage = f.transformer().transform(f.chapter(), content).getContent();

		assertTrue(storage.contains("ac:anchor=\"_context\""), () -> storage);
	}

	@Test
	void unresolvableLinkKeepsOnlyItsText(@TempDir final Path tmp) throws IOException {
		final var f = fixture(tmp);
		final var content = Jsoup.parse("<p><a href=\"99_missing.html\">Missing Page</a></p>").body();

		final var storage = f.transformer().transform(f.chapter(), content).getContent();

		assertTrue(storage.contains("Missing Page"), () -> "The link text must survive: " + storage);
		assertFalse(storage.contains("99_missing.html"), () -> "Dead href must be dropped: " + storage);
		assertFalse(storage.contains("<ac:link"), () -> storage);
	}

	@Test
	void externalLinkIsLeftUntouched(@TempDir final Path tmp) throws IOException {
		final var f = fixture(tmp);
		final var content = Jsoup.parse("<p><a href=\"https://arc42.org/\">arc42</a></p>").body();

		final var storage = f.transformer().transform(f.chapter(), content).getContent();

		assertTrue(storage.contains("href=\"https://arc42.org/\""), () -> storage);
		assertFalse(storage.contains("<ac:link"), () -> storage);
	}

	/**
	 * Antora's empty section anchors must keep being removed. Turning them into link
	 * elements would leave empty shells that the removal no longer matches.
	 */
	@Test
	void emptySectionAnchorIsStillRemoved(@TempDir final Path tmp) throws IOException {
		final var f = fixture(tmp);
		final var content = Jsoup.parse("<h2>Goals<a class=\"anchor\" href=\"#_goals\"></a></h2>").body();

		final var storage = f.transformer().transform(f.chapter(), content).getContent();

		assertFalse(storage.contains("<ac:link"), () -> "Empty anchor must not become a link: " + storage);
		assertFalse(storage.contains("<a "), () -> "Empty anchor must be removed: " + storage);
	}

	/**
	 * A textless anchor that points at a real page still deserves a link — unlike the
	 * section anchors, which point at a pure fragment. The target title fills in, which
	 * is what Antora renders for {@code xref:page.adoc[]} anyway.
	 */
	@Test
	void emptyLinkTextFallsBackToTargetTitle(@TempDir final Path tmp) throws IOException {
		final var f = fixture(tmp);
		final var content = Jsoup.parse("<p><a href=\"adr/0001.html\"></a></p>").body();

		final var storage = f.transformer().transform(f.chapter(), content).getContent();

		assertTrue(storage.contains("<ac:link"), () -> storage);
		assertTrue(storage.contains("<![CDATA[ADR 1]]>"),
				() -> "Empty text must fall back to the target title: " + storage);
	}

	@Test
	void fileLinkBecomesAttachmentLink(@TempDir final Path tmp) throws IOException {
		final var f = fixture(tmp);
		final var content = Jsoup.parse("<p><a href=\"spec.pdf\">The Specification</a></p>").body();

		final var result = f.transformer().transform(f.chapter(), content);

		assertTrue(result.getContent().contains("ri:filename=\"spec.pdf\""), result::getContent);
		assertEquals(1, result.getAttachments().size());
		assertEquals(f.spec(), result.getAttachments().getFirst().getSource());
	}

	// --- anchors and admonitions -----------------------------------------

	@Test
	void idBecomesAnchorMacroAndIsRemoved() {
		final var page = new Page("Page", Path.of("/tmp/page.html"), null);
		final var content = Jsoup.parse("<h2 id=\"_quality_goals\">Quality Goals</h2>").body();

		final var storage = transformer.transform(page, content).getContent();

		assertTrue(storage.contains("ac:name=\"anchor\""), () -> storage);
		assertTrue(storage.contains(">_quality_goals<"), () -> storage);
		assertFalse(storage.contains("id=\"_quality_goals\""), () -> "The HTML id has no purpose left: " + storage);
	}

	@Test
	void admonitionTypesMapToConfluenceMacros() {
		final var page = new Page("Page", Path.of("/tmp/page.html"), null);
		assertEquals("info", macroOf(page, "note"));
		assertEquals("tip", macroOf(page, "tip"));
		assertEquals("warning", macroOf(page, "important"));
		assertEquals("warning", macroOf(page, "warning"));
		assertEquals("note", macroOf(page, "caution"));
	}

	@Test
	void unknownAdmonitionTypeIsLeftUnchanged() {
		final var page = new Page("Page", Path.of("/tmp/page.html"), null);
		final var storage = transformer.transform(page, admonition("mystery", null)).getContent();

		assertFalse(storage.contains("ac:structured-macro"), () -> storage);
		assertTrue(storage.contains("Body text"), () -> storage);
	}

	@Test
	void admonitionTitleBecomesMacroParameter() {
		final var page = new Page("Page", Path.of("/tmp/page.html"), null);
		final var storage = transformer.transform(page, admonition("note", "Please note")).getContent();

		assertTrue(storage.contains("ac:name=\"title\""), () -> storage);
		assertTrue(storage.contains("Please note"), () -> storage);
		assertTrue(storage.contains("<ac:rich-text-body>"), () -> storage);
	}

	/**
	 * Content inside an admonition is moved, not serialized, so the later steps must
	 * still reach it.
	 */
	@Test
	void linkInsideAdmonitionIsResolved(@TempDir final Path tmp) throws IOException {
		final var f = fixture(tmp);
		final var html = "<div class=\"admonitionblock note\"><table><tr><td class=\"icon\"></td>"
				+ "<td class=\"content\"><p>See <a href=\"adr/0001.html\">Decision One</a></p></td></tr></table></div>";

		final var storage = f.transformer().transform(f.chapter(), Jsoup.parse(html).body()).getContent();

		assertTrue(storage.contains("ac:name=\"info\""), () -> storage);
		assertTrue(storage.contains("ri:content-title=\"ADR 1\""),
				() -> "Link inside the admonition must be resolved too: " + storage);
	}

	private String macroOf(final Page page, final String type) {
		final var storage = transformer.transform(page, admonition(type, null)).getContent();
		final var marker = "ac:name=\"";
		final var start = storage.indexOf(marker) + marker.length();
		return storage.substring(start, storage.indexOf('"', start));
	}

	private static org.jsoup.nodes.Element admonition(final String type, final String title) {
		final var titleDiv = title == null ? "" : "<div class=\"title\">" + title + "</div>";
		return Jsoup.parse("<div class=\"admonitionblock " + type + "\"><table><tr><td class=\"icon\"></td>"
				+ "<td class=\"content\">" + titleDiv + "<p>Body text</p></td></tr></table></div>").body();
	}
}
