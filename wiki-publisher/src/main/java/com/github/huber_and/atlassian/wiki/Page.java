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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import lombok.Getter;

@Getter
public class Page {

	private final String title;
	private final Path source;
	private final Page parent;
	private final List<Page> children = new ArrayList<>();

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
