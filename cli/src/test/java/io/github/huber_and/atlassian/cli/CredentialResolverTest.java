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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * Tests the credential resolution precedence: environment variables, then a
 * {@code --credentials-file}, then an interactive prompt.
 *
 * @author Andreas Huber
 */
class CredentialResolverTest {

	@TempDir
	Path tempDir;

	@Test
	void resolvesFromEnvironmentVariables() throws Exception {
		final var options = new CliOptions();
		final var env = Map.of("CONFLUENCE_USERNAME", "jane", "CONFLUENCE_API_TOKEN", "token-123");

		final var credentials = CredentialResolver.resolve(options, env, null);

		assertEquals("jane", credentials.username());
		assertEquals("token-123", credentials.password());
	}

	@Test
	void cliUsernameOverridesEnvironmentUsername() throws Exception {
		final var options = new CliOptions();
		options.setUsername("cli-user");
		final var env = Map.of("CONFLUENCE_USERNAME", "env-user", "CONFLUENCE_API_TOKEN", "token-123");

		final var credentials = CredentialResolver.resolve(options, env, null);

		assertEquals("cli-user", credentials.username());
	}

	@Test
	@DisabledOnOs(OS.WINDOWS)
	void resolvesFromCredentialsFileWithSecurePermissions() throws Exception {
		final var file = tempDir.resolve("credentials.properties");
		Files.writeString(file, "username=jane\npassword=token-123\n");
		Files.setPosixFilePermissions(file,
				EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
		final var options = new CliOptions();
		options.setCredentialsFile(file);

		final var credentials = CredentialResolver.resolve(options, Map.of(), null);

		assertEquals("jane", credentials.username());
		assertEquals("token-123", credentials.password());
	}

	@Test
	@DisabledOnOs(OS.WINDOWS)
	void rejectsCredentialsFileReadableByGroup() throws Exception {
		final var file = tempDir.resolve("credentials.properties");
		Files.writeString(file, "username=jane\npassword=token-123\n");
		Files.setPosixFilePermissions(file,
				Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
						PosixFilePermission.GROUP_READ));
		final var options = new CliOptions();
		options.setCredentialsFile(file);

		final var e = assertThrows(CredentialResolutionException.class,
				() -> CredentialResolver.resolve(options, Map.of(), null));

		assertTrue(e.getMessage().contains("chmod 600"));
	}

	@Test
	@DisabledOnOs(OS.WINDOWS)
	void rejectsCredentialsFileWithoutPassword() throws Exception {
		final var file = tempDir.resolve("credentials.properties");
		Files.writeString(file, "username=jane\n");
		Files.setPosixFilePermissions(file,
				EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
		final var options = new CliOptions();
		options.setCredentialsFile(file);

		assertThrows(CredentialResolutionException.class, () -> CredentialResolver.resolve(options, Map.of(), null));
	}

	@Test
	void noSourceAndNoConsoleIsRejected() {
		final var options = new CliOptions();

		final var e = assertThrows(CredentialResolutionException.class,
				() -> CredentialResolver.resolve(options, Map.of(), null));

		assertTrue(e.getMessage().contains("No credentials found"));
	}
}
