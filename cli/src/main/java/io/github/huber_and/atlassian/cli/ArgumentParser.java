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
import java.util.Arrays;
import java.util.Iterator;

import org.apache.commons.lang3.StringUtils;

/**
 * Hand-written command-line parser: turns {@code argv} into a
 * {@link CliOptions}.
 *
 * <p>
 * Written by hand rather than via a parsing library, consistent with the
 * project's otherwise minimal dependency footprint; see
 * {@code architecture-docs} ADR-8 for the reasoning.
 *
 * @author Andreas Huber
 */
public final class ArgumentParser {

	/** Printed on {@code --help} and prefixed to every usage error. */
	public static final String USAGE = """
			Usage: atlassian-cli --url <url> [--space-key <key> --path <path> [--root <title>] [--no-delete-orphans] | --config <file.yaml>]
			                     [--parser-class <fqcn>] [--plugin-dir <dir>] [--username <name>] [--credentials-file <file>]
			                     [--debug] [--verbose|-v] [--help|-h] [--version]

			  --url <url>              Base URL of the Confluence instance. Required.
			  --space-key <key>        Confluence space key. Single-mapper mode.
			  --path <path>            Local directory to publish. Single-mapper mode.
			  --root <title>           Title of the root page to publish under. Optional.
			  --delete-orphans         Move pages that no longer exist locally to the trash (default).
			  --no-delete-orphans      Only report orphaned pages, do not move them.
			  --config <file.yaml>     Read one or more mappers from a YAML file. Cannot be combined
			                           with --space-key/--path/--root/--delete-orphans.
			  --parser-class <fqcn>    Fully qualified name of the Parser implementation to use.
			  --plugin-dir <dir>       Directory of additional jars to search for --parser-class.
			  --username <name>       Confluence account name. Not required with an env-var token.
			  --credentials-file <file> File with 'username'/'password' entries; must be chmod 600.
			  --debug                  Dry run: resolve everything, but do not write to Confluence.
			  --verbose, -v            Print stack traces and enable debug logging.
			  --help, -h               Print this message and exit.
			  --version                Print the version and exit.

			Credentials are never accepted as a command-line flag. In order of precedence:
			  1. CONFLUENCE_USERNAME / CONFLUENCE_API_TOKEN environment variables.
			  2. --credentials-file (rejected unless it is readable/writable by its owner only).
			  3. An interactive prompt, only when a terminal is attached.
			""";

	private ArgumentParser() {
	}

	/**
	 * Parses the given arguments.
	 *
	 * @param args the raw command-line arguments
	 * @return the parsed options
	 * @throws ArgumentParseException if the arguments are invalid
	 */
	public static CliOptions parse(final String[] args) throws ArgumentParseException {
		final var options = new CliOptions();
		final Iterator<String> it = Arrays.asList(args).iterator();
		while (it.hasNext()) {
			final var arg = it.next();
			switch (arg) {
				case "--help", "-h" -> options.setHelp(true);
				case "--version" -> options.setVersion(true);
				case "--debug" -> options.setDebug(true);
				case "--verbose", "-v" -> options.setVerbose(true);
				case "--delete-orphans" -> options.setDeleteOrphans(true);
				case "--no-delete-orphans" -> options.setDeleteOrphans(false);
				case "--url" -> options.setUrl(nextValue(it, arg));
				case "--space-key" -> options.setSpaceKey(nextValue(it, arg));
				case "--path" -> options.setPath(nextValue(it, arg));
				case "--root" -> options.setRoot(nextValue(it, arg));
				case "--parser-class" -> options.setParserClass(nextValue(it, arg));
				case "--username" -> options.setUsername(nextValue(it, arg));
				case "--config" -> options.setConfigFile(Path.of(nextValue(it, arg)));
				case "--plugin-dir" -> options.setPluginDir(Path.of(nextValue(it, arg)));
				case "--credentials-file" -> options.setCredentialsFile(Path.of(nextValue(it, arg)));
				default -> throw usageError("Unknown option: " + arg);
			}
		}
		if (!options.isHelp() && !options.isVersion()) {
			validate(options);
		}
		return options;
	}

	private static String nextValue(final Iterator<String> it, final String flag) throws ArgumentParseException {
		if (!it.hasNext()) {
			throw usageError(flag + " requires a value");
		}
		return it.next();
	}

	private static void validate(final CliOptions options) throws ArgumentParseException {
		if (StringUtils.isBlank(options.getUrl())) {
			throw usageError("--url is required");
		}
		final var singleMapperFlagsSet = options.getSpaceKey() != null || options.getPath() != null
				|| options.getRoot() != null || options.getDeleteOrphans() != null;
		if (options.getConfigFile() != null) {
			if (singleMapperFlagsSet) {
				throw usageError(
						"--config cannot be combined with --space-key/--path/--root/--delete-orphans");
			}
		} else if (StringUtils.isBlank(options.getSpaceKey()) || StringUtils.isBlank(options.getPath())) {
			throw usageError("--space-key and --path are required unless --config is given");
		}
	}

	private static ArgumentParseException usageError(final String message) {
		return new ArgumentParseException(message + "\n\n" + USAGE);
	}
}
