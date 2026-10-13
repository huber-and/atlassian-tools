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

import java.io.IOException;
import java.nio.file.Path;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Reads a {@code --config} file into a {@link ConfigFile}.
 *
 * @author Andreas Huber
 */
public final class ConfigFileLoader {

	// Jackson 3 ignores unknown properties by default; a typo in the config file
	// must fail loudly instead of silently dropping the setting.
	private static final ObjectMapper MAPPER = YAMLMapper.builder()
			.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.build();

	private ConfigFileLoader() {
	}

	/**
	 * Reads and parses the given YAML file.
	 *
	 * @param file the file to read
	 * @return the parsed configuration
	 * @throws IOException if the file cannot be read or does not match the
	 *                      expected shape
	 */
	public static ConfigFile load(final Path file) throws IOException {
		try {
			return MAPPER.readValue(file.toFile(), ConfigFile.class);
		} catch (final JacksonException e) {
			// Jackson 3 exceptions are unchecked, read errors included
			throw new IOException(e.getMessage(), e);
		}
	}
}
