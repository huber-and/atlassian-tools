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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests parsing of the {@code --config} YAML file into a {@link ConfigFile}.
 *
 * @author Andreas Huber
 */
class ConfigFileLoaderTest {

	@TempDir
	Path tempDir;

	@Test
	void parsesUrlParserClassAndMappers() throws IOException {
		final var file = writeYaml("""
				url: https://confluence.example.com
				parserClass: io.github.huber_and.atlassian.wiki.parser.AntoraParser
				mappers:
				  - spaceKey: DOCS
				    path: build/site
				    root: Architecture
				    deleteOrphans: true
				  - spaceKey: API
				    path: build/api-site
				""");

		final var config = ConfigFileLoader.load(file);

		assertEquals("https://confluence.example.com", config.getUrl());
		assertEquals("io.github.huber_and.atlassian.wiki.parser.AntoraParser", config.getParserClass());
		assertEquals(2, config.getMappers().size());
		final var first = config.getMappers().get(0);
		assertEquals("DOCS", first.getSpaceKey());
		assertEquals("build/site", first.getPath());
		assertEquals("Architecture", first.getRoot());
		assertTrue(first.isDeleteOrphans());
		final var second = config.getMappers().get(1);
		assertEquals("API", second.getSpaceKey());
		assertEquals("build/api-site", second.getPath());
	}

	@Test
	void mappersOnlyFileOmitsUrlAndParserClass() throws IOException {
		final var file = writeYaml("""
				mappers:
				  - spaceKey: DOCS
				    path: build/site
				""");

		final var config = ConfigFileLoader.load(file);

		assertNull(config.getUrl());
		assertNull(config.getParserClass());
		assertEquals(1, config.getMappers().size());
	}

	@Test
	void unparsableYamlIsRejected() throws IOException {
		final var file = writeYaml("this: [is not, valid: yaml");

		assertThrows(IOException.class, () -> ConfigFileLoader.load(file));
	}

	@Test
	void unknownFieldIsRejected() throws IOException {
		final var file = writeYaml("thisFieldDoesNotExist: true");

		assertThrows(IOException.class, () -> ConfigFileLoader.load(file));
	}

	private Path writeYaml(final String content) throws IOException {
		final var file = tempDir.resolve("publish.yaml");
		Files.writeString(file, content);
		return file;
	}
}
