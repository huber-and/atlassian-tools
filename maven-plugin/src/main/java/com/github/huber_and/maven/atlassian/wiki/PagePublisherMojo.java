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
package com.github.huber_and.maven.atlassian.wiki;

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

import com.github.huber_and.atlassian.wiki.Configuration;
import com.github.huber_and.atlassian.wiki.Publisher;

@Mojo(name = "publish", defaultPhase = LifecyclePhase.NONE)
public class PagePublisherMojo extends AbstractMojo {

	@Parameter(property = "url", required = true)
	private String url;
	@Parameter(property = "username")
	private String username;
	@Parameter(property = "password")
	private String password;

	@Parameter(defaultValue = "${session}", readonly = true, required = true)
	private MavenSession session;

	@Parameter(required = true)
	private Set<Configuration.Mapper> mappers;

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
