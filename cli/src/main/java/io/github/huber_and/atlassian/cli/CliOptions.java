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

import java.nio.file.Path;

import io.github.huber_and.atlassian.wiki.Configuration;
import lombok.Data;

/**
 * Parsed command-line options, before credentials or a {@code --config} file
 * have been resolved.
 *
 * @author Andreas Huber
 */
@Data
public class CliOptions {

	/** The base URL of the Confluence instance. */
	private String url;

	/** Single-mapper mode: the Confluence space key. */
	private String spaceKey;

	/** Single-mapper mode: the local path to publish. */
	private String path;

	/** Single-mapper mode: the optional root page title. */
	private String root;

	/**
	 * Single-mapper mode: whether orphaned pages are moved to the trash. {@code null}
	 * means "not set on the command line", so {@link Configuration.Mapper}'s own
	 * default applies.
	 */
	private Boolean deleteOrphans;

	/** Multi-mapper mode: the YAML file to read mappers from. */
	private Path configFile;

	/** Fully qualified name of the {@code Parser} implementation to use. */
	private String parserClass;

	/** Dry-run mode, passed straight through to {@code Configuration.debug}. */
	private boolean debug;

	/** Print stack traces on failure and enable debug logging. */
	private boolean verbose;

	/** The Confluence account name; not a secret, may be given on the command line. */
	private String username;

	/** Directory scanned for additional {@code Parser} implementations. */
	private Path pluginDir;

	/** File to read {@code username}/{@code password} from. */
	private Path credentialsFile;

	/** Whether {@code --help} was given. */
	private boolean help;

	/** Whether {@code --version} was given. */
	private boolean version;
}
