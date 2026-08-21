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
import java.io.PrintStream;
import java.util.Objects;

import io.github.huber_and.atlassian.wiki.Publisher;

/**
 * Orchestrates one CLI invocation: resolves a plugin directory and
 * credentials, builds a {@code Configuration}, and runs {@code Publisher}.
 *
 * <p>
 * Kept separate from {@link Main} so it is testable without exiting the JVM.
 *
 * @author Andreas Huber
 */
public class PublishCommand {

	/** Usage/argument errors, and "no credentials found". */
	public static final int EXIT_USAGE_ERROR = 2;

	/** {@code Publisher}'s constructor rejected the configuration. */
	public static final int EXIT_CONFIGURATION_ERROR = 3;

	/** {@code Publisher.publish()} failed for at least one mapper. */
	public static final int EXIT_PUBLISH_FAILED = 4;

	/** Any other unexpected failure. */
	public static final int EXIT_UNEXPECTED_ERROR = 1;

	private final PrintStream out;
	private final PrintStream err;

	public PublishCommand(final PrintStream out, final PrintStream err) {
		this.out = out;
		this.err = err;
	}

	/**
	 * Runs the command for the given, already-parsed options.
	 *
	 * @param options the parsed command-line options
	 * @return the process exit code
	 */
	public int run(final CliOptions options) {
		if (options.isHelp()) {
			out.print(ArgumentParser.USAGE);
			return 0;
		}
		if (options.isVersion()) {
			out.println("atlassian-cli " + version());
			return 0;
		}

		final ConfigFile fileConfig;
		final Credentials credentials;
		try {
			if (options.getPluginDir() != null) {
				final var loader = PluginClassLoaderFactory.forDirectory(options.getPluginDir(),
						Thread.currentThread().getContextClassLoader());
				Thread.currentThread().setContextClassLoader(loader);
			}
			fileConfig = options.getConfigFile() != null ? ConfigFileLoader.load(options.getConfigFile())
					: new ConfigFile();
			credentials = CredentialResolver.resolve(options, System.getenv(), System.console());
		} catch (final CredentialResolutionException e) {
			printError(e, options.isVerbose());
			return EXIT_USAGE_ERROR;
		} catch (final IOException e) {
			printError(e, options.isVerbose());
			return EXIT_UNEXPECTED_ERROR;
		}

		final var config = ConfigurationBuilder.build(options, fileConfig, credentials);
		final Publisher publisher;
		try {
			publisher = new Publisher(config);
		} catch (final IllegalStateException e) {
			printError(e, options.isVerbose());
			return EXIT_CONFIGURATION_ERROR;
		}
		try {
			publisher.publish();
		} catch (final IllegalStateException e) {
			printError(e, options.isVerbose());
			return EXIT_PUBLISH_FAILED;
		}
		return 0;
	}

	private void printError(final Exception e, final boolean verbose) {
		err.println("Error: " + e.getMessage());
		if (verbose) {
			e.printStackTrace(err);
		}
	}

	private static String version() {
		return Objects.requireNonNullElse(PublishCommand.class.getPackage().getImplementationVersion(),
				"development");
	}
}
