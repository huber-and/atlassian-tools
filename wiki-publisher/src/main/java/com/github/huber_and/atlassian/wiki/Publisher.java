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

import java.nio.file.Path;

import org.apache.commons.lang3.StringUtils;

import com.github.huber_and.atlassian.wiki.Configuration.Mapper;
import com.github.huber_and.atlassian.wiki.parser.AntoraParser;
import com.github.huber_and.atlassian.wiki.parser.Parser;
import com.github.huber_and.atlassian.wiki.transformer.ConfluenceTransformer;

import lombok.extern.slf4j.Slf4j;

/**
 * Main publisher class for publishing content to Confluence.
 *
 * This class orchestrates the entire publishing process, coordinating between the parser,
 * transformer, and Confluence client. It processes all configured mappers and publishes
 * content to their respective Confluence spaces.
 *
 * @author Andreas Huber
 */
@Slf4j
public class Publisher {

	/** The configuration containing space mappings and authentication details. */
	private final Configuration config;

	/** The Confluence client for API interactions. */
	private final ConfluenceClient client;

	/** The parser for extracting page content from source files. */
	private final Parser parser;

	/**
	 * Constructs a Publisher with the given configuration.
	 *
	 * Initializes the parser and Confluence client based on the provided configuration.
	 *
	 * @param config the publisher configuration
	 */
	public Publisher(final Configuration config) {
		this.config = config;
		parser = new AntoraParser(config);
		client = new ConfluenceClient(config, parser, new ConfluenceTransformer());
	}

	/**
	 * Publishes content to all configured Confluence spaces.
	 *
	 * Iterates through all mappers in the configuration and publishes content to each
	 * specified space.
	 */
	public void publish() {

		config.getMappers().forEach(this::publish);
	}

	/**
	 * Publishes content to a specific Confluence space using the given mapper.
	 *
	 * Parses the source content, logs the page hierarchy, and updates pages in the
	 * target Confluence space. Any errors are logged without stopping the process.
	 *
	 * @param mapper the space mapper defining the target space and source path
	 */
	protected void publish(final Mapper mapper) {
		try {
			final var pages = parser.resolvePages(Path.of(mapper.getPath()));
			pages.forEach(p -> dump(p, 1));
			client.updatePages(mapper, pages);
		} catch (final Exception e) {
			log.error("Failed to publish to space {}", mapper.getSpaceKey(), e);
		}

	}

	/**
	 * Logs the page hierarchy for debugging purposes.
	 *
	 * Recursively prints the page structure with indentation based on hierarchy depth.
	 *
	 * @param page the page to log
	 * @param depth the current depth in the hierarchy
	 */
	private void dump(final Page page, final int depth) {
		log.info("{}> {}", StringUtils.repeat('-', depth), page.getTitle());
		page.getChildren().forEach(p -> dump(p, depth + 1));
	}
}
