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
package io.github.huber_and.atlassian.wiki;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import io.github.huber_and.atlassian.wiki.Configuration.Mapper;
import io.github.huber_and.atlassian.wiki.parser.AntoraParser;
import io.github.huber_and.atlassian.wiki.parser.Parser;
import io.github.huber_and.atlassian.wiki.transformer.ConfluenceTransformer;
import io.github.huber_and.atlassian.wiki.transformer.LinkResolver;
import io.github.huber_and.atlassian.wiki.transformer.LinkResolver.MapperPages;
import lombok.extern.slf4j.Slf4j;

/**
 * Main publisher class for publishing content to Confluence.
 *
 * This class orchestrates the entire publishing process, coordinating between
 * the parser, transformer, and Confluence client. It processes all configured
 * mappers and publishes content to their respective Confluence spaces.
 *
 * @author Andreas Huber
 */
@Slf4j
public class Publisher {

	/** Parser used when {@link Configuration#getParserClass()} is blank. */
	private static final String DEFAULT_PARSER_CLASS = AntoraParser.class.getName();

	/** The configuration containing space mappings and authentication details. */
	private final Configuration config;

	/** The parser for extracting page content from source files. */
	private final Parser parser;

	/**
	 * Constructs a Publisher with the given configuration.
	 *
	 * @param config the publisher configuration
	 */
	public Publisher(final Configuration config) {
		this.config = config;
		this.parser = createParser(config);
	}

	/**
	 * Returns the parser resolved from the configuration.
	 *
	 * @return the parser used to read the local content
	 */
	protected Parser getParser() {
		return parser;
	}

	/**
	 * Resolves and initializes the {@link Parser} configured via
	 * {@link Configuration#getParserClass()}.
	 *
	 * The class is resolved without running its static initializers and is only
	 * instantiated after it has been verified to implement {@link Parser}, so that
	 * a misconfigured class name cannot execute arbitrary code.
	 *
	 * @param config the publisher configuration
	 * @return the initialized parser
	 * @throws IllegalStateException if the class cannot be resolved, does not
	 *                               implement {@link Parser}, or cannot be
	 *                               instantiated
	 */
	private static Parser createParser(final Configuration config) {
		final var className = StringUtils.defaultIfBlank(config.getParserClass(), DEFAULT_PARSER_CLASS);
		final Class<?> type;
		try {
			type = loadClass(className);
		} catch (final ClassNotFoundException | LinkageError e) {
			throw new IllegalStateException("Parser class not found on the classpath: " + className, e);
		}
		if (!Parser.class.isAssignableFrom(type)) {
			throw new IllegalStateException(
					"Parser class " + type.getName() + " does not implement " + Parser.class.getName());
		}
		try {
			final var parser = type.asSubclass(Parser.class).getDeclaredConstructor().newInstance();
			parser.init(config);
			log.info("Using parser {}", type.getName());
			return parser;
		} catch (final ReflectiveOperationException | IOException e) {
			throw new IllegalStateException("Failed to instantiate parser class: " + type.getName(), e);
		}
	}

	/**
	 * Loads the given class without initializing it.
	 *
	 * The thread context class loader is tried first so that a parser supplied by
	 * the surrounding build — a Maven plugin class realm, for instance — is found;
	 * the class loader of this library is used as a fallback.
	 *
	 * @param className the fully qualified class name to load
	 * @return the loaded, uninitialized class
	 * @throws ClassNotFoundException if no class loader can resolve the name
	 */
	private static Class<?> loadClass(final String className) throws ClassNotFoundException {
		final var contextLoader = Thread.currentThread().getContextClassLoader();
		if (contextLoader != null) {
			try {
				return Class.forName(className, false, contextLoader);
			} catch (final ClassNotFoundException e) {
				log.debug("Parser class {} not visible to the context class loader, falling back", className);
			}
		}
		return Class.forName(className, false, Parser.class.getClassLoader());
	}

	/**
	 * Publishes content to all configured Confluence spaces.
	 *
	 * Runs in two phases: every mapper is parsed first, then the resulting page
	 * trees are combined into a single link index and the content is published. The
	 * order matters for cross-space links — a page can only be linked by title and
	 * space key once the page trees of all mappers are known.
	 *
	 * A mapper that fails to parse is reported and skipped; the remaining mappers
	 * are still published, but without its pages in the index.
	 */
	public void publish() {
		final List<String> failed = new ArrayList<>();
		final Map<Mapper, List<Page>> parsed = new LinkedHashMap<>();
		for (final Mapper mapper : config.getMappers()) {
			try {
				final var pages = parser.resolvePages(Path.of(mapper.getPath()));
				pages.forEach(p -> dump(p, 1));
				parsed.put(mapper, pages);
			} catch (final Exception e) {
				log.error("Failed to parse content for space {}", mapper.getSpaceKey(), e);
				failed.add(mapper.getSpaceKey());
			}
		}

		final var resolver = LinkResolver
				.of(parsed.entrySet().stream().map(e -> new MapperPages(e.getKey().getSpaceKey(),
						Path.of(e.getKey().getPath()), e.getKey().getRoot(), e.getValue())).toList());
		final var client = new ConfluenceClient(config, parser, new ConfluenceTransformer(resolver));

		for (final var entry : parsed.entrySet()) {
			if (!publish(entry.getKey(), entry.getValue(), client)) {
				failed.add(entry.getKey().getSpaceKey());
			}
		}
		if (!failed.isEmpty()) {
			throw new IllegalStateException("Failed to publish to spaces: " + failed);
		}
	}

	/**
	 * Publishes already parsed content to a specific Confluence space.
	 *
	 * Errors are logged and reported via the return value so the surrounding loop
	 * can continue with the remaining mappers.
	 *
	 * @param mapper the space mapper defining the target space and source path
	 * @param pages  the page tree parsed for this mapper
	 * @param client the client to publish through
	 * @return {@code true} on success, {@code false} if publishing this mapper
	 *         failed
	 */
	protected boolean publish(final Mapper mapper, final List<Page> pages, final ConfluenceClient client) {
		try {
			client.updatePages(mapper, pages);
			return true;
		} catch (final Exception e) {
			log.error("Failed to publish to space {}", mapper.getSpaceKey(), e);
			return false;
		}
	}

	/**
	 * Logs the page hierarchy for debugging purposes.
	 *
	 * Recursively prints the page structure with indentation based on hierarchy
	 * depth.
	 *
	 * @param page  the page to log
	 * @param depth the current depth in the hierarchy
	 */
	private void dump(final Page page, final int depth) {
		log.info("{}> {}", StringUtils.repeat('-', depth), page.getTitle());
		page.getChildren().forEach(p -> dump(p, depth + 1));
	}
}
