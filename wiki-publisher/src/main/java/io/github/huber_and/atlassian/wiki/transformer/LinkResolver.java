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
package io.github.huber_and.atlassian.wiki.transformer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import io.github.huber_and.atlassian.wiki.Page;
import io.github.huber_and.atlassian.wiki.util.SafePaths;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves the hrefs found in the source documentation to Confluence link
 * targets.
 *
 * The resolver holds an index over the page trees of <em>all</em> configured
 * mappers, which is what allows a link to be followed into a different
 * Confluence space. It is built once per publish run, before any page is
 * transformed.
 *
 * @author Andreas Huber
 */
@Slf4j
public final class LinkResolver {

	/**
	 * Matches an href that addresses something outside the documentation: either a
	 * scheme such as {@code https:} or {@code mailto:}, or a protocol-relative URL.
	 */
	private static final Pattern EXTERNAL = Pattern.compile("^(?:[a-zA-Z][a-zA-Z0-9+.-]*:|//).*");

	/** The kind of target an href was resolved to. */
	public enum Kind {
		/** Addresses a published Confluence page. */
		PAGE,
		/** Addresses a position within the page the link appears on. */
		ANCHOR,
		/** Addresses a file that has to be published as an attachment. */
		ATTACHMENT,
		/** Points outside the documentation and must be left untouched. */
		EXTERNAL,
		/** Points into the documentation but cannot be mapped to anything published. */
		UNRESOLVED
	}

	/**
	 * A resolved link target.
	 *
	 * @param kind      what the href addresses
	 * @param title     the Confluence page title, for {@link Kind#PAGE}
	 * @param spaceKey  the space key of the target, for {@link Kind#PAGE}
	 * @param foreign   whether the target lives in a different space than the page
	 *                  the link appears on
	 * @param anchor    the fragment, without the {@code #}, or null
	 * @param file      the local file, for {@link Kind#ATTACHMENT}
	 */
	public record Target(Kind kind, String title, String spaceKey, boolean foreign, String anchor, Path file) {

		/** @return the file name of an attachment target */
		public String fileName() {
			return file.getFileName().toString();
		}
	}

	/** Where a published page came from. */
	private record PageRef(String title, String spaceKey) {
	}

	/** Normalized absolute source path of a published page to its coordinates. */
	private final Map<Path, PageRef> pages;

	/** The mapper paths, forming the boundary for attachment candidates. */
	private final List<Path> roots;

	private LinkResolver(final Map<Path, PageRef> pages, final List<Path> roots) {
		this.pages = pages;
		this.roots = roots;
	}

	/**
	 * Creates a resolver that resolves nothing.
	 *
	 * Used by the no-argument {@link ConfluenceTransformer} constructor so existing
	 * embedders of the library keep working; every internal link then ends up as
	 * {@link Kind#UNRESOLVED}.
	 *
	 * @return an empty resolver
	 */
	public static LinkResolver empty() {
		return new LinkResolver(Map.of(), List.of());
	}

	/**
	 * Builds the index from the parsed page trees of all mappers.
	 *
	 * @param sources one entry per successfully parsed mapper
	 * @return the resolver used for this publish run
	 */
	public static LinkResolver of(final Collection<MapperPages> sources) {
		final Map<Path, PageRef> index = new LinkedHashMap<>();
		final List<Path> roots = new ArrayList<>();
		for (final MapperPages source : sources) {
			final var root = source.path().normalize();
			roots.add(root);
			// The synthetic root page is registered first so that a real navigation
			// entry for index.html wins: it carries the title the page is published under.
			if (StringUtils.isNotBlank(source.rootTitle())) {
				index.put(root.resolve("index.html").normalize(),
						new PageRef(source.rootTitle(), source.spaceKey()));
			}
			source.pages().forEach(page -> register(index, page, source.spaceKey()));
		}
		log.info("Link index built from {} mapper(s) with {} page(s)", sources.size(), index.size());
		return new LinkResolver(index, roots);
	}

	private static void register(final Map<Path, PageRef> index, final Page page, final String spaceKey) {
		if (page.getSource() != null) {
			index.put(page.getSource().normalize(), new PageRef(page.getTitle(), spaceKey));
		}
		page.getChildren().forEach(child -> register(index, child, spaceKey));
	}

	/**
	 * Resolves a single href.
	 *
	 * @param currentPage the source path of the page the link appears on
	 * @param href        the raw href attribute
	 * @return the target, or empty if the href is blank and should be left alone
	 */
	public Optional<Target> resolve(final Path currentPage, final String href) {
		if (StringUtils.isBlank(href)) {
			return Optional.empty();
		}
		if (EXTERNAL.matcher(href).matches()) {
			return Optional.of(external());
		}
		final var hash = href.indexOf('#');
		final var anchor = hash < 0 ? null : StringUtils.defaultIfBlank(href.substring(hash + 1), null);
		final var pathPart = hash < 0 ? href : href.substring(0, hash);
		if (StringUtils.isBlank(pathPart)) {
			// A pure fragment addresses the page the link appears on.
			return Optional.of(new Target(Kind.ANCHOR, null, null, false, anchor, null));
		}
		if (currentPage == null || currentPage.getParent() == null) {
			return Optional.of(unresolved(anchor));
		}
		final Path candidate;
		try {
			candidate = currentPage.getParent().resolve(pathPart).normalize();
		} catch (final RuntimeException e) {
			log.warn("Cannot resolve href '{}' relative to {}", href, currentPage, e);
			return Optional.of(unresolved(anchor));
		}
		final var target = pages.get(candidate);
		if (target != null) {
			final var current = pages.get(currentPage.normalize());
			final var foreign = current != null && !target.spaceKey().equals(current.spaceKey());
			return Optional.of(new Target(Kind.PAGE, target.title(), target.spaceKey(), foreign, anchor, candidate));
		}
		if (isAttachmentCandidate(candidate)) {
			return Optional.of(new Target(Kind.ATTACHMENT, null, null, false, null, candidate));
		}
		return Optional.of(unresolved(anchor));
	}

	/**
	 * A file inside one of the mapper paths that is not an HTML page is published as
	 * an attachment. HTML files are excluded on purpose: an HTML file that is not in
	 * the index is a page that was never published, and turning it into a download
	 * would be worse than reporting it as unresolved.
	 */
	private boolean isAttachmentCandidate(final Path candidate) {
		final var name = candidate.getFileName();
		if (name == null) {
			return false;
		}
		final var lower = name.toString().toLowerCase(Locale.ROOT);
		if (lower.endsWith(".html") || lower.endsWith(".htm")) {
			return false;
		}
		return roots.stream().anyMatch(root -> SafePaths.isWithin(root, candidate)) && Files.isRegularFile(candidate);
	}

	private static Target external() {
		return new Target(Kind.EXTERNAL, null, null, false, null, null);
	}

	private static Target unresolved(final String anchor) {
		return new Target(Kind.UNRESOLVED, null, null, false, anchor, null);
	}

	/**
	 * The parsed content of one mapper, used to build the index.
	 *
	 * @param spaceKey  the target Confluence space
	 * @param path      the local root directory of the generated site
	 * @param rootTitle the title of the configured root page, may be blank
	 * @param pages     the parsed page tree
	 */
	public record MapperPages(String spaceKey, Path path, String rootTitle, List<Page> pages) {
	}
}
