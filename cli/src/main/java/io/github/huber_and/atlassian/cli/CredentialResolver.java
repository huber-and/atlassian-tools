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

import java.io.Console;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Resolves Confluence credentials without ever accepting a plaintext password
 * as a command-line argument.
 *
 * <p>
 * A CLI process's argument list stays visible for as long as it runs — via
 * shell history and via {@code ps}/{@code /proc/<pid>/cmdline} — which is a
 * broader exposure than the "leaks into a CI log" reasoning behind the Maven
 * plugin's "no CLI property" rule. Accordingly, there is deliberately no
 * {@code --password} option; only the sources below are tried, in order,
 * logging which one was used but never the value itself.
 *
 * @author Andreas Huber
 */
@Slf4j
public final class CredentialResolver {

	/** Only these permission bits may be set on a credentials file. */
	private static final EnumSet<PosixFilePermission> ALLOWED_PERMISSIONS = EnumSet
			.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

	private CredentialResolver() {
	}

	/**
	 * Resolves credentials, trying environment variables, then a
	 * {@code --credentials-file}, then an interactive prompt.
	 *
	 * @param options the parsed command-line options
	 * @param env     the process environment
	 * @param console the controlling terminal, or {@code null} if none is
	 *                attached
	 * @return the resolved credentials
	 * @throws CredentialResolutionException if no source yields credentials, or a
	 *                                        credentials file is rejected
	 * @throws IOException                   if the credentials file cannot be read
	 */
	public static Credentials resolve(final CliOptions options, final Map<String, String> env, final Console console)
			throws CredentialResolutionException, IOException {
		final var token = env.get("CONFLUENCE_API_TOKEN");
		if (StringUtils.isNotBlank(token)) {
			log.debug("Using credentials from CONFLUENCE_USERNAME/CONFLUENCE_API_TOKEN");
			final var username = StringUtils.defaultIfBlank(options.getUsername(), env.get("CONFLUENCE_USERNAME"));
			return new Credentials(username, token);
		}
		if (options.getCredentialsFile() != null) {
			return fromFile(options);
		}
		if (console != null) {
			log.debug("Using an interactive prompt for credentials");
			return fromPrompt(options, console);
		}
		throw new CredentialResolutionException("No credentials found. Set CONFLUENCE_USERNAME and "
				+ "CONFLUENCE_API_TOKEN, pass --credentials-file, or run in a terminal to be prompted. "
				+ "See --help.");
	}

	private static Credentials fromFile(final CliOptions options) throws CredentialResolutionException, IOException {
		final var file = options.getCredentialsFile();
		checkPermissions(file);
		final var properties = new Properties();
		try (var in = Files.newInputStream(file)) {
			properties.load(in);
		}
		final var password = properties.getProperty("password");
		if (StringUtils.isBlank(password)) {
			throw new CredentialResolutionException("Credentials file " + file + " has no 'password' entry");
		}
		log.debug("Using credentials from {}", file);
		final var username = StringUtils.defaultIfBlank(options.getUsername(), properties.getProperty("username"));
		return new Credentials(username, password);
	}

	private static void checkPermissions(final Path file) throws CredentialResolutionException, IOException {
		if (!file.getFileSystem().supportedFileAttributeViews().contains("posix")) {
			log.info("Skipping permission check for {}: not a POSIX file system", file);
			return;
		}
		final var permissions = Files.getPosixFilePermissions(file);
		if (!ALLOWED_PERMISSIONS.containsAll(permissions)) {
			throw new CredentialResolutionException(
					"Refusing to read " + file + ": must not be readable or writable by group or others (chmod 600)");
		}
	}

	private static Credentials fromPrompt(final CliOptions options, final Console console) {
		final var username = StringUtils.isNotBlank(options.getUsername()) ? options.getUsername()
				: console.readLine("Confluence username: ");
		final var password = new String(console.readPassword("Confluence password or API token: "));
		return new Credentials(username, password);
	}
}
