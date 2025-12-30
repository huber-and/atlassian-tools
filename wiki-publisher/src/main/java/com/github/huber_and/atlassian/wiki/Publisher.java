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

@Slf4j
public class Publisher {

	private final Configuration config;
	private final ConfluenceClient client;
	private final Parser parser;

	public Publisher(final Configuration config) {
		this.config = config;
		parser = new AntoraParser(config);
		client = new ConfluenceClient(config, parser, new ConfluenceTransformer());
	}

	public void publish() {

		config.getMappers().forEach(this::publish);
	}

	protected void publish(final Mapper mapper) {
		try {
			final var pages = parser.resolvePages(Path.of(mapper.getPath()));
			pages.forEach(p -> dump(p, 1));
			client.updatePages(mapper, pages);
		} catch (final Exception e) {
			log.error("Failed to publish to space {}", mapper.getSpaceKey(), e);
		}

	}

	private void dump(final Page page, final int depth) {
		log.info("{}> {}", StringUtils.repeat('-', depth), page.getTitle());
		page.getChildren().forEach(p -> dump(p, depth + 1));
	}
}
