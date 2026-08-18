/*
 * Copyright 2025-2026 Andreas Huber
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
package io.github.huber_and.atlassian.wiki.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class SafePathsTest {

	private final Path root = Path.of("/var/site/space");

	@Test
	void resolvesPlainRelativePath() {
		final var resolved = SafePaths.resolveWithin(root, "page.html");
		assertEquals(root.resolve("page.html").normalize(), resolved);
		assertTrue(resolved.startsWith(root));
	}

	@Test
	void resolvesNestedRelativePath() {
		final var resolved = SafePaths.resolveWithin(root, "_images/foo.png");
		assertEquals(root.resolve("_images/foo.png").normalize(), resolved);
	}

	@Test
	void rejectsParentTraversal() {
		assertThrows(IllegalArgumentException.class,
				() -> SafePaths.resolveWithin(root, "../../etc/passwd"));
	}

	@Test
	void rejectsParentTraversalWithMixedSegments() {
		assertThrows(IllegalArgumentException.class,
				() -> SafePaths.resolveWithin(root, "sub/../../etc/passwd"));
	}

	@Test
	void rejectsAbsolutePathOutsideRoot() {
		assertThrows(IllegalArgumentException.class,
				() -> SafePaths.resolveWithin(root, "/etc/passwd"));
	}

	@Test
	void rejectsNullInput() {
		assertThrows(IllegalArgumentException.class, () -> SafePaths.resolveWithin(root, null));
	}

	@Test
	void allowsCurrentDirectoryReference() {
		final var resolved = SafePaths.resolveWithin(root, "./page.html");
		assertEquals(root.resolve("page.html").normalize(), resolved);
	}

	@Test
	void isWithinAcceptsDescendantsAndTheRootItself() {
		assertTrue(SafePaths.isWithin(root, root.resolve("sub/page.html")));
		assertTrue(SafePaths.isWithin(root, root));
	}

	@Test
	void isWithinRejectsSiblingsAndEscapes() {
		assertFalse(SafePaths.isWithin(root, root.resolveSibling("other/page.html")));
		assertFalse(SafePaths.isWithin(root, root.resolve("../outside.pdf")));
	}

	@Test
	void isWithinNormalizesBeforeComparing() {
		assertTrue(SafePaths.isWithin(root, root.resolve("sub/../page.html")));
	}

	@Test
	void isWithinTreatsNullAsOutside() {
		assertFalse(SafePaths.isWithin(root, null));
		assertFalse(SafePaths.isWithin(null, root));
	}
}
