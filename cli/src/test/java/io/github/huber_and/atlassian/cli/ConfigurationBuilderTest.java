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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.huber_and.atlassian.wiki.Configuration;
import io.github.huber_and.atlassian.wiki.parser.AntoraParser;

/**
 * Tests that {@link CliOptions}/{@link ConfigFile}/{@link Credentials} land
 * in the right {@link Configuration} fields.
 *
 * @author Andreas Huber
 */
class ConfigurationBuilderTest {

	@Test
	void singleMapperModeBuildsOneMapperFromFlags() {
		final var options = new CliOptions();
		options.setUrl("https://confluence.example.com");
		options.setSpaceKey("DOCS");
		options.setPath("build/site");
		options.setRoot("Architecture");
		options.setDeleteOrphans(false);

		final var config = ConfigurationBuilder.build(options, new ConfigFile(), new Credentials("jane", "token"));

		assertEquals("https://confluence.example.com", config.getUrl());
		assertEquals("jane", config.getUsername());
		assertEquals("token", config.getPassword());
		assertEquals(AntoraParser.class.getName(), config.getParserClass());
		assertEquals(1, config.getMappers().size());
		final var mapper = config.getMappers().iterator().next();
		assertEquals("DOCS", mapper.getSpaceKey());
		assertEquals("build/site", mapper.getPath());
		assertEquals("Architecture", mapper.getRoot());
		assertFalse(mapper.isDeleteOrphans());
	}

	@Test
	void deleteOrphansDefaultsToTrueLikeConfigurationMapper() {
		final var options = new CliOptions();
		options.setUrl("https://confluence.example.com");
		options.setSpaceKey("DOCS");
		options.setPath("build/site");

		final var config = ConfigurationBuilder.build(options, new ConfigFile(), new Credentials("jane", "token"));

		assertTrue(config.getMappers().iterator().next().isDeleteOrphans());
	}

	@Test
	void configFileModeUsesFileMappers() {
		final var options = new CliOptions();
		options.setUrl("https://confluence.example.com");
		options.setConfigFile(Path.of("publish.yaml"));
		final var fileConfig = new ConfigFile();
		final var first = new Configuration.Mapper();
		first.setSpaceKey("DOCS");
		first.setPath("build/site");
		final var second = new Configuration.Mapper();
		second.setSpaceKey("API");
		second.setPath("build/api-site");
		fileConfig.setMappers(List.of(first, second));

		final var config = ConfigurationBuilder.build(options, fileConfig, new Credentials("jane", "token"));

		assertEquals(2, config.getMappers().size());
	}

	@Test
	void cliUrlOverridesFileUrl() {
		final var options = new CliOptions();
		options.setUrl("https://cli.example.com");
		options.setConfigFile(Path.of("publish.yaml"));
		final var fileConfig = new ConfigFile();
		fileConfig.setUrl("https://file.example.com");

		final var config = ConfigurationBuilder.build(options, fileConfig, new Credentials("jane", "token"));

		assertEquals("https://cli.example.com", config.getUrl());
	}

	@Test
	void fileUrlIsUsedWhenCliUrlIsAbsent() {
		final var options = new CliOptions();
		options.setConfigFile(Path.of("publish.yaml"));
		final var fileConfig = new ConfigFile();
		fileConfig.setUrl("https://file.example.com");

		final var config = ConfigurationBuilder.build(options, fileConfig, new Credentials("jane", "token"));

		assertEquals("https://file.example.com", config.getUrl());
	}
}
