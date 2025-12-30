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
package com.github.huber_and.atlassian.wiki;

import java.util.HashSet;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration for Confluence publishing operations.
 *
 * This class holds the necessary configuration parameters for connecting to and
 * publishing content to Confluence, including authentication credentials and space
 * mappings.
 *
 * @author Andreas Huber
 */
@Data
public class Configuration {

	/** The base URL of the Confluence instance. */
	private String url;

	/** The username for authentication with Confluence. */
	private String username;

	/** The password or API token for authentication with Confluence. */
	private String password;

	/** Enable debug mode for dry-run operations without actual publishing. */
	private boolean debug;

	/** Set of space mappers defining how content maps to Confluence spaces. */
	private Set<Mapper> mappers = new HashSet<>();

	/**
	 * Mapper configuration that defines how local content maps to Confluence spaces.
	 *
	 * Maps a local directory structure to a specific Confluence space with an optional
	 * root page.
	 */
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Mapper {
		/** The Confluence space key where content will be published. */
		private String spaceKey;

		/** The root page title under which content will be organized (optional). */
		private String root;

		/** The local file system path containing the content to publish. */
		private String path;
	}
}
