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

import java.util.ArrayList;
import java.util.List;

import org.jsoup.nodes.Element;

import com.github.huber_and.atlassian.wiki.Attachment;
import com.github.huber_and.atlassian.wiki.Page;

import lombok.Data;

/**
 * Interface for transforming page content to Confluence format.
 *
 * Implementations of this interface handle the conversion of HTML content from
 * various source formats into the Confluence Storage Format, along with extracting
 * and managing associated attachments.
 *
 * @author Andreas Huber
 */
public interface Transformer {

	/**
	 * Transforms the given page content to Confluence storage format.
	 *
	 * @param page the page being transformed
	 * @param content the HTML content to transform
	 * @return a Result containing the transformed content and any extracted attachments
	 */
	Result transform(Page page, Element content);

	/**
	 * Encapsulates the result of content transformation.
	 *
	 * Contains the transformed content in Confluence storage format and a list of
	 * attachments that were discovered during transformation.
	 */
	@Data
	public static class Result {
		/** The transformed content in Confluence storage format. */
		private String content;

		/** The list of attachments discovered in the content. */
		private List<Attachment> attachments = new ArrayList<>();

		/**
		 * Adds an attachment to the result if it's not already present.
		 *
		 * @param attachment the attachment to add
		 */
		public void add(final Attachment attachment) {
			if (!attachments.contains(attachment)) {
				attachments.add(attachment);
			}
		}
	}

}
