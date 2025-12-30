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
package io.github.huber_and.atlassian.wiki;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import lombok.Getter;

/**
 * Represents a Confluence page with hierarchical structure.
 *
 * A page can have a parent page and multiple child pages, forming a tree structure that
 * mirrors the page hierarchy in Confluence. Each page has a title, an optional source file,
 * and maintains references to its parent and children.
 *
 * @author Andreas Huber
 */
@Getter
public class Page {

	/** The title of the page. */
	private final String title;

	/** The source file path for the page content. */
	private final Path source;

	/** The parent page in the hierarchy, or null if this is a root page. */
	private final Page parent;

	/** The list of child pages under this page. */
	private final List<Page> children = new ArrayList<>();

	/**
	 * Constructs a new Page with the given title, source, and parent.
	 *
	 * If a parent is provided and this page is not already in the parent's children list,
	 * it will be added automatically.
	 *
	 * @param title the title of the page
	 * @param source the source file path containing the page content
	 * @param parent the parent page, or null if this is a root page
	 */
	public Page(final String title, final Path source, final Page parent) {
		this.title = title;
		this.source = source;
		this.parent = parent;
		if (parent != null && !parent.children.contains(this)) {
			parent.children.add(this);
		}
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if ((obj == null) || (getClass() != obj.getClass())) {
			return false;
		}
		final var other = (Page) obj;
		return Objects.equals(parent, other.parent) && Objects.equals(source, other.source)
				&& Objects.equals(title, other.title);
	}

	@Override
	public int hashCode() {
		return Objects.hash(parent, source, title);
	}

	@Override
	public String toString() {
		return "NavItem [title=" + title + ", source=" + source + ", parent=" + parent + "]";
	}

}
