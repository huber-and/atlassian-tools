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
package io.github.huber_and.atlassian.wiki.parser;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;

import io.github.huber_and.atlassian.wiki.Configuration;
import io.github.huber_and.atlassian.wiki.Page;

import lombok.extern.slf4j.Slf4j;

/**
 * Parser for Antora-based documentation.
 *
 * This parser is specifically designed to handle Antora-generated documentation,
 * extracting page hierarchies from the Antora HTML structure by parsing navigation menus
 * and source files.
 *
 * @author Andreas Huber
 */
@Slf4j
public class AntoraParser implements Parser {

	/** The configuration for parser behavior. */
	private final Configuration config;

	/**
	 * Constructs an AntoraParser with the given configuration.
	 *
	 * @param config the parser configuration
	 */
	public AntoraParser(final Configuration config) {
		this.config = config;
	}

	/**
	 * Resolves the hierarchical list of pages from Antora documentation.
	 *
	 * Parses the index.html file to extract the navigation menu and page structure,
	 * constructing a tree of Page objects based on the menu hierarchy.
	 *
	 * @param root the root path of the Antora documentation
	 * @return a list of root-level pages from the navigation structure
	 * @throws IOException if an error occurs while reading the index.html file
	 */
	@Override
	public List<Page> resolvePages(final Path root) throws IOException {

		final var index = root.resolve("index.html");
		final var doc = load(index);

		final var menus = doc.getElementsByAttributeValue("data-panel", "menu");
		log.debug("Found {} menu elements", menus.size());
		final var menu = menus.getFirst();

		final var path = new Page[10];
		final List<Page> roots = new ArrayList<>();

		for (final Element child : menu.getElementsByTag("a")) {
			if (!child.parent().hasAttr("data-depth")) {
				continue;
			}
			final var depth = Integer.parseInt(child.parent().attr("data-depth"));
			final var href = child.attr("href");
			Path source = null;
			if (StringUtils.isNotBlank(href)) {
				source = root.resolve(href);
			}
			final var item = new Page(child.text(), source, path[depth - 1]);
			path[depth] = item;
			if (item.getParent() == null) {
				roots.add(item);
			}
			log.info("NavItem {}", item);

		}
		return roots;
	}

	/**
	 * Load the Content from the provides source
	 */
	@Override
	public Element loadContent(final Page page) throws IOException {
		log.info("Load page from {}", page.getSource());
		final var doc = load(page.getSource());
		return doc.selectFirst("article.doc");
	}

	private Document load(final Path file) throws IOException {
		final var doc = Jsoup.parse(file, "UTF-8", "", org.jsoup.parser.Parser.xmlParser());
		doc.outputSettings().prettyPrint(false);// makes html() preserve linebreaks and spacing
		doc.outputSettings().escapeMode(Entities.EscapeMode.xhtml); // This will ensure xhtml validity regarding
																	// entities
		doc.outputSettings().charset("UTF-8"); // does no harm :-)
		return doc;
	}
}
