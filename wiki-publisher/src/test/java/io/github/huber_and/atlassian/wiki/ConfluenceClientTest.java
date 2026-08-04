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
package io.github.huber_and.atlassian.wiki;

import static io.github.huber_and.atlassian.wiki.ConfluenceClient.PROPERTY_KEY_CONTENT_HASH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.atlassian.wiki.rest.v2.model.MultiEntityLinks;

/**
 * Unit tests for {@link ConfluenceClient}.
 *
 * Tests the hash-based change detection helpers without requiring a live
 * Confluence instance.
 *
 * @author Andreas Huber
 */
class ConfluenceClientTest {

	/**
	 * The property key constant must not change — it is used to look up the stored
	 * hash on existing Confluence pages.
	 */
	@Test
	void propertyKeyConstantHasExpectedValue() {
		assertEquals(PROPERTY_KEY_CONTENT_HASH, ConfluenceClient.PROPERTY_KEY_CONTENT_HASH);
	}

	/**
	 * {@code computeHash} must return a non-null, non-empty hex string.
	 */
	@Test
	void computeHashReturnsNonEmptyResult() {
		final var hash = ConfluenceClient.computeHash("some content");
		assertNotNull(hash);
		assertNotEquals("", hash);
	}

	/**
	 * {@code computeHash} must be deterministic: the same input always produces the
	 * same hash.
	 */
	@Test
	void computeHashIsDeterministic() {
		final var content = "<div>Hello Confluence</div>";
		assertEquals(ConfluenceClient.computeHash(content), ConfluenceClient.computeHash(content));
	}

	/**
	 * {@code computeHash} must produce different hashes for different inputs.
	 */
	@Test
	void computeHashDifferentiatesContent() {
		assertNotEquals(ConfluenceClient.computeHash("<div>version 1</div>"),
				ConfluenceClient.computeHash("<div>version 2</div>"));
	}

	/**
	 * The SHA-256 hash of an empty string must equal the well-known value.
	 */
	@Test
	void computeHashOfEmptyStringMatchesKnownSha256() {
		// well-known SHA-256 of ""
		assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
				ConfluenceClient.computeHash(""));
	}

	/**
	 * An empty file must hash to the same well-known value as an empty string —
	 * proof that the streaming file variant produces plain SHA-256.
	 */
	@Test
	void computeFileHashOfEmptyFileMatchesKnownSha256(@TempDir final Path dir) throws IOException {
		final var file = Files.createFile(dir.resolve("empty.png"));
		assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
				ConfluenceClient.computeFileHash(file));
	}

	/**
	 * {@code computeFileHash} must be deterministic for identical content and differ
	 * as soon as a single byte changes — this is what makes a replaced image
	 * detectable.
	 */
	@Test
	void computeFileHashDetectsChangedContent(@TempDir final Path dir) throws IOException {
		final var first = Files.writeString(dir.resolve("a.txt"), "image bytes");
		final var copy = Files.writeString(dir.resolve("b.txt"), "image bytes");
		final var changed = Files.writeString(dir.resolve("c.txt"), "image byteS");

		assertEquals(ConfluenceClient.computeFileHash(first), ConfluenceClient.computeFileHash(copy));
		assertNotEquals(ConfluenceClient.computeFileHash(first), ConfluenceClient.computeFileHash(changed));
	}

	/**
	 * The digest must cover the whole file, not just the first chunk read into the
	 * 8 KB buffer. A change beyond that boundary therefore has to alter the hash.
	 */
	@Test
	void computeFileHashCoversContentBeyondTheBuffer(@TempDir final Path dir) throws IOException {
		final var bytes = new byte[20000];
		final var original = Files.write(dir.resolve("original.bin"), bytes);

		bytes[19000] = 1;
		final var changedLate = Files.write(dir.resolve("changed.bin"), bytes);

		assertEquals(64, ConfluenceClient.computeFileHash(original).length());
		assertNotEquals(ConfluenceClient.computeFileHash(original), ConfluenceClient.computeFileHash(changedLate));
	}

	/** The comment marker must carry the documented prefix. */
	@Test
	void attachmentCommentCarriesPrefix() {
		final var comment = ConfluenceClient.attachmentComment("abc123");
		assertEquals("sha256:abc123", comment);
		assertTrue(comment.startsWith(ConfluenceClient.ATTACHMENT_HASH_PREFIX));
	}

	/**
	 * A comment written by a human must not be mistaken for our marker, otherwise a
	 * changed file would silently not be uploaded.
	 */
	@Test
	void attachmentCommentDiffersFromForeignComment() {
		assertNotEquals("uploaded manually", ConfluenceClient.attachmentComment("abc123"));
		assertNotEquals(ConfluenceClient.attachmentComment("abc123"),
				ConfluenceClient.attachmentComment("def456"));
	}

	/** Attachment IDs arrive either bare or with the {@code att} prefix. */
	@Test
	void parseAttachmentIdAcceptsBothForms() {
		assertEquals(123456L, ConfluenceClient.parseAttachmentId("att123456"));
		assertEquals(123456L, ConfluenceClient.parseAttachmentId("123456"));
	}

	/** Anything else must fail loudly rather than produce a wrong ID. */
	@Test
	void parseAttachmentIdRejectsUnexpectedInput() {
		assertThrows(IllegalArgumentException.class, () -> ConfluenceClient.parseAttachmentId(null));
		assertThrows(IllegalArgumentException.class, () -> ConfluenceClient.parseAttachmentId(""));
		assertThrows(IllegalArgumentException.class, () -> ConfluenceClient.parseAttachmentId("attachment-7"));
	}

	/**
	 * Confluence cursors are base64 values that may contain {@code =} padding, so
	 * the parameter must be split at the first separator only.
	 */
	@Test
	void nextCursorExtractsCursorIncludingPadding() {
		final var links = new MultiEntityLinks()
				.next("/wiki/api/v2/pages/42/attachments?cursor=bWFyaw%3D%3D&limit=250");
		assertEquals("bWFyaw==", ConfluenceClient.nextCursor(links));
	}

	/** No links or no next link means the last page has been reached. */
	@Test
	void nextCursorReturnsNullWhenThereIsNoNextPage() {
		assertNull(ConfluenceClient.nextCursor(null));
		assertNull(ConfluenceClient.nextCursor(new MultiEntityLinks()));
		assertNull(ConfluenceClient.nextCursor(new MultiEntityLinks().next("/wiki/api/v2/pages/42/attachments")));
	}

}
