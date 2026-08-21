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

import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import io.github.huber_and.atlassian.wiki.Configuration;

/**
 * Combines {@link CliOptions}, an optional {@link ConfigFile} and resolved
 * {@link Credentials} into a {@link Configuration} for {@code Publisher}.
 *
 * <p>
 * A pure function with no I/O, so it is testable without a filesystem or a
 * terminal.
 *
 * @author Andreas Huber
 */
public final class ConfigurationBuilder {

	private ConfigurationBuilder() {
	}

	/**
	 * Builds the {@link Configuration} to hand to {@code Publisher}.
	 *
	 * @param options     the parsed command-line options
	 * @param fileConfig  the parsed {@code --config} file, or an empty
	 *                    {@link ConfigFile} if none was given
	 * @param credentials the resolved credentials
	 * @return the assembled configuration
	 */
	public static Configuration build(final CliOptions options, final ConfigFile fileConfig,
			final Credentials credentials) {
		final var config = new Configuration();
		config.setUrl(StringUtils.defaultIfBlank(options.getUrl(), fileConfig.getUrl()));
		final var parserClass = StringUtils.defaultIfBlank(options.getParserClass(), fileConfig.getParserClass());
		if (StringUtils.isNotBlank(parserClass)) {
			config.setParserClass(parserClass);
		}
		config.setDebug(options.isDebug());
		config.setUsername(credentials.username());
		config.setPassword(credentials.password());
		config.setMappers(options.getConfigFile() != null ? new LinkedHashSet<>(fileConfig.getMappers())
				: Set.of(singleMapper(options)));
		return config;
	}

	private static Configuration.Mapper singleMapper(final CliOptions options) {
		final var mapper = new Configuration.Mapper();
		mapper.setSpaceKey(options.getSpaceKey());
		mapper.setPath(options.getPath());
		mapper.setRoot(options.getRoot());
		mapper.setDeleteOrphans(options.getDeleteOrphans() == null || options.getDeleteOrphans());
		return mapper;
	}
}
