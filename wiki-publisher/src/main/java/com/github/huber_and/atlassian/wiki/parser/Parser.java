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
package com.github.huber_and.atlassian.wiki.parser;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.jsoup.nodes.Element;

import com.github.huber_and.atlassian.wiki.Page;

/**
 * Interface for parsing pages from various sources.
 *
 * Implementations of this interface handle the extraction and parsing of page content
 * from different source formats and locations, converting them into a structured Page hierarchy.
 *
 * @author Andreas Huber
 */
public interface Parser {

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
