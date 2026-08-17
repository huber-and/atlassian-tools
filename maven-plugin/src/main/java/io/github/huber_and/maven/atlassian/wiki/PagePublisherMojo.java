/*
 * Copyright 2024-2026 Andreas Huber
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
package io.github.huber_and.maven.atlassian.wiki;

import java.net.URI;
import java.util.Set;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.building.SettingsProblem;
import org.apache.maven.settings.crypto.DefaultSettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecrypter;

import io.github.huber_and.atlassian.wiki.Configuration;
import io.github.huber_and.atlassian.wiki.Publisher;

/**
 * Maven Mojo for publishing pages to Confluence.
 *
 * This Mojo integrates Confluence page publishing into the Maven build
 * lifecycle, allowing documentation to be published to Confluence as part of
 * the build process.
 *
 * The Mojo can be configured with credentials directly or by using Maven server
 * configuration. If no username is provided, it will attempt to retrieve
 * credentials from the Maven settings for the host specified in the URL.
 *
 * Usage in pom.xml:
 *
 * <pre>
 * &lt;plugin&gt;
 *   &lt;groupId&gt;com.github.huber-and.atlassian&lt;/groupId&gt;
 *   &lt;artifactId&gt;atlassian-maven-plugin&lt;/artifactId&gt;
 *   &lt;configuration&gt;
 *     &lt;url&gt;https://confluence.example.com&lt;/url&gt;
 *     &lt;mappers&gt;
 *       &lt;mapper&gt;
 *         &lt;spaceKey&gt;MYSPACE&lt;/spaceKey&gt;
 *         &lt;path&gt;docs&lt;/path&gt;
 *       &lt;/mapper&gt;
 *     &lt;/mappers&gt;
 *   &lt;/configuration&gt;
 * &lt;/plugin&gt;
 * </pre>
 *
 * @author Andreas Huber
 */
@Mojo(name = "publish", defaultPhase = LifecyclePhase.NONE, threadSafe = true)
public class PagePublisherMojo extends AbstractMojo {

	/**
	 * The base URL of the Confluence instance (e.g.,
	 * https://confluence.example.com).
	 */
	@Parameter(property = "url", required = true)
	private String url;

	/**
	 * The server ID for Maven server configuration. If not provided, will use the
	 * host of url.
	 */
	@Parameter(property = "serverId")
	private String serverId;

	/**
	 * The username for authentication. If not provided, will use Maven server
	 * configuration. Configure in {@code <configuration>}; cannot be set via CLI
	 * property to avoid leaking credentials into build logs.
	 */
	@Parameter
	private String username;

	/**
	 * The password or API token for authentication. Configure in
	 * {@code <configuration>} or — preferred — via Maven server configuration in
	 * {@code settings.xml}. Cannot be set via CLI property to avoid leaking
	 * credentials into build logs.
	 */
	@Parameter
	private String password;

	/** The current Maven session, used to access server configuration. */
	@Parameter(defaultValue = "${session}", readonly = true, required = true)
	private MavenSession session;

	/** The set of mappers defining how local content maps to Confluence spaces. */
	@Parameter(required = true)
	private Set<Configuration.Mapper> mappers;

	/**
	 * If {@code true}, skip the Confluence publish step. Can be set on the CLI via
	 * {@code -Datlassian.skip=true} or in {@code <configuration>}.
	 */
	@Parameter(property = "atlassian.skip", defaultValue = "false")
	private boolean skip;

	/**
	 * Fully qualified class name of the parser used to read the local content. The
	 * class must implement
	 * {@code io.github.huber_and.atlassian.wiki.parser.Parser} and provide a public
	 * no-argument constructor. Defaults to the built-in Antora parser.
	 */
	@Parameter(property = "page.parser", defaultValue = "io.github.huber_and.atlassian.wiki.parser.AntoraParser", required = true)
	private String parserClass;

	/** Maven helper for decrypting passwords stored in {@code settings.xml}. */
	private final SettingsDecrypter settingsDecrypter;

	@Inject
	public PagePublisherMojo(final SettingsDecrypter settingsDecrypter) {
		this.settingsDecrypter = settingsDecrypter;
	}

	/**
	 * Executes the Maven Mojo to publish pages to Confluence.
	 *
	 * Builds the configuration from parameters and Maven settings, then runs the
	 * publisher.
	 *
	 * @throws MojoExecutionException if an error occurs during execution
	 * @throws MojoFailureException   if the publication fails
	 */
	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		if (skip) {
			getLog().info("Skipping atlassian:publish (atlassian.skip=true)");
			return;
		}
		if (mappers == null || mappers.isEmpty()) {
			throw new MojoExecutionException("At least one <mapper> must be configured");
		}
		final URI uri;
		try {
			uri = URI.create(url);
		} catch (final IllegalArgumentException e) {
			throw new MojoExecutionException("Invalid <url>: " + url, e);
		}
		getLog().info("Publish pages to " + uri.getHost());
		final var config = new Configuration();
		config.setParserClass(parserClass);
		config.setUrl(url);
		config.setMappers(mappers);
		if (StringUtils.isBlank(username)) {
			final var server = resolveServer(uri);
			if (server != null) {
				config.setUsername(server.getUsername());
				config.setPassword(server.getPassword());
			}
		} else {
			config.setUsername(username);
			config.setPassword(password);
		}
		try {
			new Publisher(config).publish();
		} catch (final RuntimeException e) {
			throw new MojoFailureException("Confluence publish failed: " + e.getMessage(), e);
		}
	}

	private Server resolveServer(final URI uri) {
		var id = serverId;
		if (StringUtils.isBlank(serverId)) {
			id = uri.getHost();
		}
		final var server = session.getSettings().getServer(id);
		if (server == null) {
			return null;
		}
		return decrypt(server);
	}

	private Server decrypt(final Server server) {
		final var result = settingsDecrypter.decrypt(new DefaultSettingsDecryptionRequest(server));
		for (final SettingsProblem problem : result.getProblems()) {
			getLog().warn("Settings decryption: " + problem.getMessage());
		}
		final var decrypted = result.getServer();
		return decrypted != null ? decrypted : server;
	}
}
