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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests exit-code mapping for cases that need neither a real Confluence
 * instance nor a terminal: usage-level credential failures and a rejected
 * {@code parserClass}. Publishing itself is covered by
 * {@code PublishCommandTestLocal} against a real instance.
 *
 * @author Andreas Huber
 */
class PublishCommandTest {

	@TempDir
	Path tempDir;

	@Test
	void printsHelpAndExitsZero() {
		final var out = new ByteArrayOutputStream();
		final var command = new PublishCommand(new PrintStream(out), System.err);
		final var options = new CliOptions();
		options.setHelp(true);

		final var exitCode = command.run(options);

		assertEquals(0, exitCode);
		assertTrue(out.toString().contains("Usage: atlassian-cli"));
	}

	@Test
	void missingCredentialsExitsWithUsageError() {
		final var err = new ByteArrayOutputStream();
		final var command = new PublishCommand(System.out, new PrintStream(err));
		final var options = new CliOptions();
		options.setUrl("https://confluence.example.com");
		options.setSpaceKey("DOCS");
		options.setPath("build/site");
		// No CONFLUENCE_API_TOKEN in the test environment, no --credentials-file, and
		// no attached console: every credential source is unavailable.

		final var exitCode = command.run(options);

		assertEquals(PublishCommand.EXIT_USAGE_ERROR, exitCode);
		assertTrue(err.toString().contains("Error:"));
	}

	@Test
	@DisabledOnOs(OS.WINDOWS)
	void invalidParserClassExitsWithConfigurationError() throws IOException {
		final var err = new ByteArrayOutputStream();
		final var command = new PublishCommand(System.out, new PrintStream(err));
		final var options = new CliOptions();
		options.setUrl("https://confluence.example.com");
		options.setSpaceKey("DOCS");
		options.setPath("build/site");
		options.setParserClass("not.a.real.ClassName");
		options.setCredentialsFile(writeCredentialsFile());

		final var exitCode = command.run(options);

		assertEquals(PublishCommand.EXIT_CONFIGURATION_ERROR, exitCode);
	}

	private Path writeCredentialsFile() {
		try {
			final var file = tempDir.resolve("credentials.properties");
			Files.writeString(file, "username=jane\npassword=token\n");
			Files.setPosixFilePermissions(file,
					EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
			return file;
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
