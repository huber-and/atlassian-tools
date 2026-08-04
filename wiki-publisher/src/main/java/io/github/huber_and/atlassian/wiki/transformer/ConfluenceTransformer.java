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
package io.github.huber_and.atlassian.wiki.transformer;

import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;

import io.github.huber_and.atlassian.wiki.Attachment;
import io.github.huber_and.atlassian.wiki.Page;
import io.github.huber_and.atlassian.wiki.util.SafePaths;

import lombok.extern.slf4j.Slf4j;

/**
 * Transformer to convert HTML content to Confluence storage format.
 *
 * This implementation handles the transformation of HTML content into the Confluence Storage Format,
 * including:
 * <ul>
 *   <li>Converting image references to Confluence attachment references</li>
 *   <li>Transforming code blocks into Confluence code macros</li>
 *   <li>Sanitizing HTML content by removing unnecessary attributes</li>
 *   <li>Handling CDATA sections for proper XML structure</li>
 * </ul>
 *
 * @author Andreas Huber
 */
@Slf4j
public class ConfluenceTransformer implements Transformer {

	/** Placeholder for CDATA section start. */
	private static final String CDATA_PLACEHOLDER_START = "<cdata-placeholder>";

	/** Placeholder for CDATA section end. */
	private static final String CDATA_PLACEHOLDER_END = "</cdata-placeholder>";

	/** Allowed values for the {@code ac:width} attribute on images. */
	private static final Pattern IMG_WIDTH_PATTERN = Pattern.compile("[0-9]+(px|%)?");

	/**
	 * Languages accepted as {@code language} parameter of the Confluence code
	 * macro. Unknown values are dropped to avoid passing arbitrary strings into
	 * the Confluence Storage Format.
	 */
	private static final Set<String> ALLOWED_CODE_LANGUAGES = Set.of("actionscript3", "applescript", "bash",
			"coldfusion", "cpp", "csharp", "css", "delphi", "diff", "erlang", "go", "groovy", "haskell", "html",
			"java", "javafx", "javascript", "json", "kotlin", "perl", "php", "powershell", "py", "python", "ruby",
			"sass", "scala", "shell", "sql", "swift", "tex", "text", "typescript", "vb", "xml", "yaml", "yml");

	/**
	 * Maps the five Asciidoctor admonition types onto the four Confluence
	 * admonition macros, keeping the urgency levels visually distinct: informational
	 * blue, advisory yellow, critical red.
	 */
	private static final Map<String, String> ADMONITION_MACROS = Map.of("note", "info", "tip", "tip", "important",
			"warning", "warning", "warning", "caution", "note");

	/** Resolves hrefs to Confluence link targets. */
	private final LinkResolver resolver;

	/**
	 * Creates a transformer that cannot resolve internal links.
	 *
	 * Kept so embedders of the library that construct the transformer themselves
	 * keep compiling; internal links are then reported as unresolved.
	 */
	public ConfluenceTransformer() {
		this(LinkResolver.empty());
	}

	/**
	 * Creates a transformer that resolves internal links through the given resolver.
	 *
	 * @param resolver the link index built for this publish run
	 */
	public ConfluenceTransformer(final LinkResolver resolver) {
		this.resolver = resolver;
	}

	/**
	 * Transforms page content to Confluence storage format.
	 *
	 * Removes the page title, converts admonitions, anchors, links, images and code
	 * blocks, and sanitizes the content. Code blocks come last because they freeze
	 * their subtree by serializing it.
	 *
	 * @param page the page being transformed
	 * @param content the HTML content to transform
	 * @return a Result containing the transformed content and discovered attachments
	 */
	@Override
	public Result transform(final Page page, final Element content) {
		final var result = new Result();
		log.info("Transform Page {} from {}", page.getTitle(), page.getSource());
		final Element title = content.selectFirst("h1.page");
		if (title != null) {
			title.remove();
		}
		transformAdmonitions(content);
		transformAnchors(content);
		transformLinks(page, content, result);
		transformImages(page, content, result);
		transformCodeBlocks(content);
		result.setContent(sanitizeBody(content));
		return result;
	}

	/**
	 * Converts Asciidoctor admonition blocks into the matching Confluence macro.
	 *
	 * An admonition is a {@code div.admonitionblock TYPE} holding a table with an
	 * icon and a content cell; the content may start with a {@code div.title}, which
	 * becomes the macro title. An unknown type is left untouched rather than guessed
	 * at.
	 *
	 * @param content the HTML content to scan
	 */
	private void transformAdmonitions(final Element content) {
		content.select("div.admonitionblock").forEach(block -> {
			final var type = block.classNames().stream().filter(c -> !"admonitionblock".equals(c)).findFirst()
					.orElse("");
			final var macroName = ADMONITION_MACROS.get(type.toLowerCase());
			if (macroName == null) {
				log.warn("Leaving admonition of unknown type '{}' unchanged", type);
				return;
			}
			final var cell = block.selectFirst("td.content");
			if (cell == null) {
				log.warn("Admonition of type '{}' has no content cell, leaving it unchanged", type);
				return;
			}
			final var macro = new Element("ac:structured-macro", "ac");
			macro.attr("ac:name", macroName);
			final var titleDiv = cell.selectFirst("div.title");
			if (titleDiv != null) {
				titleDiv.remove();
				macro.appendElement("ac:parameter", "ac").attr("ac:name", "title").appendText(titleDiv.text());
			}
			final var body = macro.appendElement("ac:rich-text-body", "ac");
			// Move the child nodes instead of serializing them, so the later steps still
			// see links, images and code blocks inside the admonition.
			cell.childNodes().stream().toList().forEach(body::appendChild);
			block.replaceWith(macro);
		});
	}

	/**
	 * Emits an explicit Confluence anchor macro for every element carrying an id.
	 *
	 * Confluence anchors refer to heading texts or to anchor macros, never to HTML
	 * ids, so Antora's generated ids such as {@code _requirements_overview} would
	 * not be reachable otherwise. The id is dropped afterwards because it has no
	 * function in Confluence.
	 *
	 * @param content the HTML content to scan
	 */
	private void transformAnchors(final Element content) {
		content.select("[id]").forEach(element -> {
			if (element == content) {
				return;
			}
			final var id = element.attr("id");
			if (StringUtils.isBlank(id)) {
				return;
			}
			final var macro = new Element("ac:structured-macro", "ac");
			macro.attr("ac:name", "anchor");
			// The anchor macro takes its value as the unnamed default parameter,
			// hence the deliberately empty ac:name.
			macro.appendElement("ac:parameter", "ac").attr("ac:name", "").appendText(id);
			element.before(macro);
			element.removeAttr("id");
		});
	}

	/**
	 * Rewrites the links of a page to Confluence link elements.
	 *
	 * A textless anchor pointing at a pure fragment is Antora's empty section anchor.
	 * Those are skipped so {@link #sanitizeBody(Element)} can keep removing them — the
	 * anchor macros made them redundant, and converting them would leave empty link
	 * shells behind that the removal no longer matches.
	 *
	 * @param page    the page being transformed
	 * @param content the HTML content to scan
	 * @param result  the transformation result to add attachments to
	 */
	private void transformLinks(final Page page, final Element content, final Result result) {
		content.select("a[href]").forEach(link -> {
			final var href = link.attr("href");
			if (StringUtils.isBlank(link.text()) && href.startsWith("#")) {
				return;
			}
			final var target = resolver.resolve(page.getSource(), href).orElse(null);
			if (target == null) {
				return;
			}
			switch (target.kind()) {
			case PAGE -> link.replaceWith(pageLink(link.text(), target));
			case ANCHOR -> link.replaceWith(anchorLink(link.text(), target.anchor()));
			case ATTACHMENT -> link.replaceWith(attachmentLink(link.text(), target, result));
			case EXTERNAL -> log.debug("Leaving external link '{}' unchanged", href);
			case UNRESOLVED -> {
				log.warn("Cannot resolve link '{}' on page {}, keeping the text only", href, page.getTitle());
				link.replaceWith(new Element("span").appendText(link.text()));
			}
			}
		});
	}

	private Element pageLink(final String text, final LinkResolver.Target target) {
		final var acLink = newLink(target.anchor());
		final var riPage = acLink.appendElement("ri:page", "ri").attr("ri:content-title", target.title());
		if (target.foreign()) {
			riPage.attr("ri:space-key", target.spaceKey());
		}
		return appendBody(acLink, StringUtils.defaultIfBlank(text, target.title()));
	}

	private Element anchorLink(final String text, final String anchor) {
		return appendBody(newLink(anchor), text);
	}

	private Element attachmentLink(final String text, final LinkResolver.Target target, final Result result) {
		final var attachment = new Attachment();
		attachment.setFileName(target.fileName());
		attachment.setSource(target.file());
		result.add(attachment);
		log.info("Transform file link to attachment {}", attachment.getFileName());
		final var acLink = newLink(null);
		acLink.appendElement("ri:attachment", "ri").attr("ri:filename", attachment.getFileName());
		return appendBody(acLink, StringUtils.defaultIfBlank(text, attachment.getFileName()));
	}

	private static Element newLink(final String anchor) {
		final var acLink = new Element("ac:link", "ac");
		if (StringUtils.isNotBlank(anchor)) {
			acLink.attr("ac:anchor", anchor);
		}
		return acLink;
	}

	/**
	 * Adds the link text wrapped in the CDATA placeholder that
	 * {@link #sanitizeBody(Element)} later turns into a real CDATA section.
	 */
	private static Element appendBody(final Element acLink, final String text) {
		acLink.appendElement("ac:plain-text-link-body", "ac").appendElement("cdata-placeholder").appendText(text);
		return acLink;
	}

	/**
	 * Transforms all image elements in the content to Confluence attachment references.
	 *
	 * @param page the page containing the images
	 * @param content the HTML content containing image elements
	 * @param result the transformation result to add attachments to
	 */
	private void transformImages(final Page page, final Element content, final Result result) {
		content.getElementsByTag("img").forEach(i -> transformImage(page, i, result));
	}

	/**
	 * Transforms a single image element to a Confluence attachment reference.
	 *
	 * Extracts image metadata, registers the image file as an attachment,
	 * and replaces the HTML img tag with a Confluence ac:image element.
	 *
	 * @param page the page containing the image
	 * @param image the image element to transform
	 * @param result the transformation result to add the attachment to
	 */
	private void transformImage(final Page page, final Element image, final Result result) {
		final var src = image.attr("src");
		final var imgWidth = image.attr("width");
		final var imgAlign = StringUtils.defaultIfBlank(image.attr("align"), "center");

		if (StringUtils.isBlank(src)) {
			return;
		}
		final java.nio.file.Path source;
		try {
			source = SafePaths.resolveWithin(page.getSource().getParent(), src);
		} catch (final IllegalArgumentException e) {
			log.warn("Removing unsafe image src '{}': {}", src, e.getMessage());
			image.remove();
			return;
		}
		if (!Files.exists(source)) {
			log.info("Image {} does not exists", source);
			return;
		}
		// Add the Image as attachment which will be uploaded
		final var attachment = new Attachment();
		attachment.setFileName(source.getFileName().toString());
		attachment.setSource(source);
		log.info("Transform image {}", attachment);
		result.add(attachment);
		final var acImage = new Element("ac:image", "ac");
		acImage.attr("ac:align", imgAlign);
		if (StringUtils.isNotBlank(imgWidth) && IMG_WIDTH_PATTERN.matcher(imgWidth).matches()) {
			acImage.attr("ac:width", imgWidth);
		} else if (StringUtils.isNotBlank(imgWidth)) {
			log.warn("Dropping non-numeric image width '{}' on {}", imgWidth, attachment.getFileName());
		}
		acImage.appendElement("ri:attachment", "ri").attr("ri:filename", attachment.getFileName());
		image.replaceWith(acImage);
		log.info("Image is now {}", acImage.parent());
	}

	/**
	 * Transforms all code block elements to Confluence code macros.
	 *
	 * Extracts the programming language from data attributes and wraps
	 * the code content in a Confluence structured code macro.
	 *
	 * @param content the HTML content containing code blocks
	 */
	private void transformCodeBlocks(final Element content) {
		content.select("pre > code").forEach(code -> {
			final var parent = code.parent();
			final var rawLanguage = code.attr("data-lang");
			final var language = sanitizeLanguage(rawLanguage);
			final var codeMacro = new Element("ac:structured-macro", "ac");
			codeMacro.attr("ac:name", "code");
			if (language != null) {
				codeMacro.appendElement("ac:parameter", "ac").attr("ac:name", "language").appendText(language);
			} else if (StringUtils.isNotBlank(rawLanguage)) {
				log.warn("Dropping unknown code language '{}'", rawLanguage);
			}
			codeMacro.appendElement("ac:plain-text-body", "ac").appendElement("cdata-placeholder")
					.appendText(code.html());
			parent.replaceWith(codeMacro);
		});
	}

	private static String sanitizeLanguage(final String raw) {
		if (StringUtils.isBlank(raw)) {
			return null;
		}
		final var normalized = raw.trim().toLowerCase();
		return ALLOWED_CODE_LANGUAGES.contains(normalized) ? normalized : null;
	}

	/**
	 * Sanitizes the HTML body by removing unnecessary attributes and handling CDATA sections.
	 *
	 * Removes all class attributes and wraps CDATA content with proper markers.
	 *
	 * @param body the HTML body element to sanitize
	 * @return the sanitized HTML as a string
	 */
	private String sanitizeBody(final Element body) {
		body.forEach(e -> e.removeAttr("class"));
		var html = body.html().trim();
		var start = html.indexOf(CDATA_PLACEHOLDER_START);
		while (start > -1) {
			final var end = html.indexOf(CDATA_PLACEHOLDER_END, start);
			if (end > -1) {
				final var prefix = html.substring(0, start) + CDATA_PLACEHOLDER_START;
				final var suffix = html.substring(end);
				final var unescaped = html.substring(start + CDATA_PLACEHOLDER_START.length(), end).replace("&lt;", "<")
						.replace("&gt;", ">").replace("&amp;", "&");
				html = prefix + unescaped + suffix;
			}
			start = html.indexOf(CDATA_PLACEHOLDER_START, start + 1);
		}
		return html.replace("<br>", "<br />").replace("</br>", "<br />").replaceAll("<a([^>]*)></a>", "")
				.replace(CDATA_PLACEHOLDER_START, "<![CDATA[").replace(CDATA_PLACEHOLDER_END, "]]>")
				// workaround for #402
				.replaceAll("(?m)(ac:name=\"language\">)([\n\r\t ]*)([a-z]+)([\n\r\t ]*)(</ac)", "$1$3$5");
	}
}
