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
package io.github.huber_and.atlassian.wiki.parser;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.jsoup.nodes.Element;

import io.github.huber_and.atlassian.wiki.Configuration;
import io.github.huber_and.atlassian.wiki.Page;

/**
 * Interface for parsing pages from various sources.
 *
 * Implementations of this interface handle the extraction and parsing of page
 * content from different source formats and locations, converting them into a
 * structured Page hierarchy.
 *
 * @author Andreas Huber
 */
public interface Parser {

	/**
	 * Initializes the parser with the given configuration.
	 *
	 * Called once by the publisher directly after the implementation has been
	 * instantiated and before any other method is used. Implementations must provide
	 * a public no-argument constructor.
	 *
	 * @param config the configuration to use for parsing
	 * @throws IOException if an error occurs during initialization
	 */
	void init(Configuration config) throws IOException;

	/**
	 * Resolves the hierarchical list of pages from the given root path.
	 *
	 * Parses the source files at the specified root path and constructs a tree of
	 * Page objects representing the page hierarchy.
	 *
	 * @param root the root path containing the source files
	 * @return a list of root-level pages parsed from the source
	 * @throws IOException if an error occurs while reading source files
	 */
	List<Page> resolvePages(Path root) throws IOException;

	/**
	 * Loads the content of the given page from its source file.
	 *
	 * Reads and parses the HTML content for the specified page, returning it as a
	 * JSoup Element for further processing.
	 *
	 * @param page the page whose content should be loaded
	 * @return the parsed HTML content as a JSoup Element
	 * @throws IOException if an error occurs while reading the source file
	 */
	Element loadContent(Page page) throws IOException;
}
