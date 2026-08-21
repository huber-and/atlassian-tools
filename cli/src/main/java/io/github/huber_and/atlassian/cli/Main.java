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

/**
 * Entry point for the {@code atlassian-cli} executable.
 *
 * @author Andreas Huber
 */
public final class Main {

	private Main() {
	}

	public static void main(final String[] args) {
		System.exit(run(args));
	}

	/**
	 * Parses {@code args} and runs the publish command, without exiting the JVM.
	 *
	 * @param args the raw command-line arguments
	 * @return the process exit code
	 */
	static int run(final String[] args) {
		final CliOptions options;
		try {
			options = ArgumentParser.parse(args);
		} catch (final ArgumentParseException e) {
			System.err.println("Error: " + e.getMessage());
			return PublishCommand.EXIT_USAGE_ERROR;
		}
		return new PublishCommand(System.out, System.err).run(options);
	}
}
