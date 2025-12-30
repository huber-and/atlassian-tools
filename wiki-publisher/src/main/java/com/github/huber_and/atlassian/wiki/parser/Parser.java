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

public interface Parser {

	/**
	 * Resolve the list of pages from the given root path
	 *
	 * @param root
	 * @return
	 * @throws IOException
	 */
	List<Page> resolvePages(Path root) throws IOException;

	/**
	 * Load the content of the given page
	 *
	 * @param page
	 * @return
	 * @throws IOException
	 */
	Element loadContent(Page page) throws IOException;
}
