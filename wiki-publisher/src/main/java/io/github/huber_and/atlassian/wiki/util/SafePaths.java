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

import java.nio.file.Path;

/**
 * Helpers for resolving file paths from untrusted input without allowing
 * directory traversal.
 */
public final class SafePaths {

	private SafePaths() {
	}

	/**
	 * Resolves {@code userInput} against {@code root} and verifies that the result
	 * stays within {@code root}.
	 *
	 * @param root      the directory the result must be a descendant of
	 * @param userInput a relative path coming from untrusted input (e.g. an HTML
	 *                  attribute)
	 * @return the resolved, normalized path
	 * @throws IllegalArgumentException if the input is absolute or escapes
	 *                                  {@code root} via {@code ..} segments
	 */
	public static Path resolveWithin(final Path root, final String userInput) {
		if (userInput == null) {
			throw new IllegalArgumentException("userInput must not be null");
		}
		final Path candidate = root.resolve(userInput).normalize();
		if (!isWithin(root, candidate)) {
			throw new IllegalArgumentException("Path escapes root: " + userInput);
		}
		return candidate;
	}

	/**
	 * Tests whether {@code candidate} lies inside {@code root}.
	 *
	 * Used where the containment boundary is not a single directory — a link may
	 * legitimately leave the directory of the page it appears on, as long as it
	 * stays inside one of the configured mapper paths.
	 *
	 * @param root      the directory the candidate must be a descendant of
	 * @param candidate the path to test
	 * @return {@code true} if {@code candidate} is {@code root} or below it
	 */
	public static boolean isWithin(final Path root, final Path candidate) {
		if (root == null || candidate == null) {
			return false;
		}
		return candidate.normalize().startsWith(root.normalize());
	}
}
