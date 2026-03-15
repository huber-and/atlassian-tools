/*
 * Copyright 2002-2017 the original author or authors.
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

import org.junit.jupiter.api.Test;

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
}
