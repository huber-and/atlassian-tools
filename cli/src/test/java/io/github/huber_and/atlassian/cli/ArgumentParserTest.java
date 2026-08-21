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
package io.github.huber_and.atlassian.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Tests the hand-written command-line parser.
 *
 * @author Andreas Huber
 */
class ArgumentParserTest {

	@Test
	void parsesSingleMapperFlags() throws ArgumentParseException {
		final var options = ArgumentParser.parse(new String[] { "--url", "https://confluence.example.com",
				"--space-key", "DOCS", "--path", "build/site", "--root", "Architecture", "--no-delete-orphans" });

		assertEquals("https://confluence.example.com", options.getUrl());
		assertEquals("DOCS", options.getSpaceKey());
		assertEquals("build/site", options.getPath());
		assertEquals("Architecture", options.getRoot());
		assertEquals(Boolean.FALSE, options.getDeleteOrphans());
	}

	@Test
	void deleteOrphansDefaultsToUnset() throws ArgumentParseException {
		final var options = ArgumentParser.parse(
				new String[] { "--url", "https://confluence.example.com", "--space-key", "DOCS", "--path", "site" });

		assertNull(options.getDeleteOrphans());
	}

	@Test
	void parsesConfigFileMode() throws ArgumentParseException {
		final var options = ArgumentParser
				.parse(new String[] { "--url", "https://confluence.example.com", "--config", "publish.yaml" });

		assertEquals(Path.of("publish.yaml"), options.getConfigFile());
	}

	@Test
	void configAndSingleMapperFlagsAreMutuallyExclusive() {
		final var e = assertThrows(ArgumentParseException.class,
				() -> ArgumentParser.parse(new String[] { "--url", "https://confluence.example.com", "--config",
						"publish.yaml", "--space-key", "DOCS" }));

		assertTrue(e.getMessage().contains("--config cannot be combined"));
	}

	@Test
	void missingUrlIsRejected() {
		final var e = assertThrows(ArgumentParseException.class, () -> ArgumentParser
				.parse(new String[] { "--space-key", "DOCS", "--path", "site" }));

		assertTrue(e.getMessage().contains("--url is required"));
	}

	@Test
	void missingSpaceKeyOrPathIsRejectedWithoutConfig() {
		final var e = assertThrows(ArgumentParseException.class,
				() -> ArgumentParser.parse(new String[] { "--url", "https://confluence.example.com" }));

		assertTrue(e.getMessage().contains("--space-key and --path are required"));
	}

	@Test
	void unknownOptionIsRejected() {
		assertThrows(ArgumentParseException.class,
				() -> ArgumentParser.parse(new String[] { "--totally-made-up" }));
	}

	@Test
	void missingValueForOptionIsRejected() {
		assertThrows(ArgumentParseException.class, () -> ArgumentParser.parse(new String[] { "--url" }));
	}

	@Test
	void helpSkipsValidation() throws ArgumentParseException {
		final var options = ArgumentParser.parse(new String[] { "--help" });

		assertTrue(options.isHelp());
	}

	@Test
	void versionSkipsValidation() throws ArgumentParseException {
		final var options = ArgumentParser.parse(new String[] { "--version" });

		assertTrue(options.isVersion());
		assertFalse(options.isHelp());
	}
}
