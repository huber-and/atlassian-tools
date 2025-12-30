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
package com.github.huber_and.atlassian.wiki.transformer;

import java.nio.file.Files;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;

import com.github.huber_and.atlassian.wiki.Attachment;
import com.github.huber_and.atlassian.wiki.Page;

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

	/**
	 * Transforms page content to Confluence storage format.
	 *
	 * Removes the page title, transforms images and code blocks, and sanitizes the content.
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
		transformImages(page, content, result);
		transformCodeBlocks(content);
		result.setContent(sanitizeBody(content));
		return result;
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

		final var source = page.getSource().getParent().resolve(src);
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
		if (StringUtils.isNotBlank(imgWidth)) {
			acImage.attr("ac:width", imgWidth);
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
			final var language = code.attr("data-lang");
			final var codeMacro = new Element("ac:structured-macro", "ac");
			codeMacro.attr("ac:name", "code");
			codeMacro.appendElement("ac:parameter", "ac").attr("ac:name", "language").appendText(language);
			codeMacro.appendElement("ac:plain-text-body", "ac").appendElement("cdata-placeholder")
					.appendText(code.html());
			parent.replaceWith(codeMacro);
		});
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
