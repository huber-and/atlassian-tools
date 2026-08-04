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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
}
