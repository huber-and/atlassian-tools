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

import java.util.regex.Pattern;

/**
 * How Jira issue keys are found in the content and how they are rendered.
 *
 * @param url         base URL of Jira without trailing slash, or {@code null};
 *                    required for {@link Rendering#LINK} and for link detection
 * @param rendering   how a detected issue key is rendered
 * @param detectLinks also treat {@code <a href="<url>/browse/KEY">KEY</a>} as an
 *                    issue key, not only {@code span.jira}
 * @author Andreas Huber
 */
public record JiraOptions(String url, Rendering rendering, boolean detectLinks) {

	/** Rendering of a detected Jira issue key. */
	public enum Rendering {
		/** The Confluence Jira macro, showing key and status. */
		MACRO,
		/** A plain link to {@code <url>/browse/KEY}. */
		LINK,
		/** Leave the content unchanged. */
		NONE
	}

	/** Macro rendering, no link detection: the behaviour without any configuration. */
	public static final JiraOptions DEFAULT = new JiraOptions(null, Rendering.MACRO, false);

	/**
	 * Validates and normalizes the options.
	 *
	 * @throws IllegalArgumentException if the URL is not an http(s) URL, or is
	 *                                  missing although link rendering or link
	 *                                  detection needs it
	 */
	public JiraOptions {
		if (url != null && url.isBlank()) {
			url = null;
		}
		if (url != null) {
			url = url.strip();
			if (!url.startsWith("https://") && !url.startsWith("http://")) {
				throw new IllegalArgumentException("jiraUrl must be an http(s) URL: " + url);
			}
			if (url.endsWith("/")) {
				url = url.substring(0, url.length() - 1);
			}
		}
		if (rendering == null) {
			rendering = Rendering.MACRO;
		}
		if (url == null && (rendering == Rendering.LINK || detectLinks)) {
			throw new IllegalArgumentException("jiraUrl is required for link rendering and link detection");
		}
	}

	/**
	 * Matches an href pointing at an issue of this Jira, capturing the key.
	 *
	 * @return the pattern, or {@code null} without a URL
	 */
	Pattern issueHrefPattern() {
		return url == null ? null
				: Pattern.compile(Pattern.quote(url) + "/browse/([A-Z][A-Z0-9_]+-[0-9]+)(?:[?#].*)?");
	}
}
