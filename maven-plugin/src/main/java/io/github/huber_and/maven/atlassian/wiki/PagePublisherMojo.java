/*
 * Copyright 2002-2017 the original author or authors.
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

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import io.github.huber_and.atlassian.wiki.Configuration;
import io.github.huber_and.atlassian.wiki.Publisher;

/**
 * Maven Mojo for publishing pages to Confluence.
 *
 * This Mojo integrates Confluence page publishing into the Maven build lifecycle,
 * allowing documentation to be published to Confluence as part of the build process.
 *
 * The Mojo can be configured with credentials directly or by using Maven server configuration.
 * If no username is provided, it will attempt to retrieve credentials from the Maven settings
 * for the host specified in the URL.
 *
 * Usage in pom.xml:
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
@Mojo(name = "publish", defaultPhase = LifecyclePhase.NONE)
public class PagePublisherMojo extends AbstractMojo {

	/** The base URL of the Confluence instance (e.g., https://confluence.example.com). */
	@Parameter(property = "url", required = true)
	private String url;

	/** The username for authentication. If not provided, will use Maven server configuration. */
	@Parameter(property = "username")
	private String username;

	/** The password or API token for authentication. */
	@Parameter(property = "password")
	private String password;

	/** The current Maven session, used to access server configuration. */
	@Parameter(defaultValue = "${session}", readonly = true, required = true)
	private MavenSession session;

	/** The set of mappers defining how local content maps to Confluence spaces. */
	@Parameter(required = true)
	private Set<Configuration.Mapper> mappers;

	/**
	 * Executes the Maven Mojo to publish pages to Confluence.
	 *
	 * Builds the configuration from parameters and Maven settings, then runs the publisher.
	 *
	 * @throws MojoExecutionException if an error occurs during execution
	 * @throws MojoFailureException if the publication fails
	 */
	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		final var uri = URI.create(url);
		getLog().info("Publish pages to " + uri.getHost());
		final var config = new Configuration();
		config.setUrl(url);
		config.setMappers(mappers);
		if (StringUtils.isBlank(username)) {
			final var server = session.getSettings().getServer(uri.getHost());
			if (server != null) {
				config.setUsername(server.getUsername());
				config.setPassword(server.getPassword());
			}
		} else {
			config.setUsername(username);
			config.setPassword(password);
		}
		final var publisher = new Publisher(config);
		publisher.publish();

	}

}
