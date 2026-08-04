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
package io.github.huber_and.atlassian.wiki;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;

import io.github.huber_and.atlassian.wiki.Configuration.Mapper;
import io.github.huber_and.atlassian.wiki.parser.Parser;
import io.github.huber_and.atlassian.wiki.transformer.Transformer;
import lombok.extern.slf4j.Slf4j;
import net.atlassian.wiki.rest.ApiClient;
import net.atlassian.wiki.rest.ServerConfiguration;
import net.atlassian.wiki.rest.v1.api.ContentAttachmentsApi;
import net.atlassian.wiki.rest.v2.api.AttachmentApi;
import net.atlassian.wiki.rest.v2.api.ContentPropertiesApi;
import net.atlassian.wiki.rest.v2.api.PageApi;
import net.atlassian.wiki.rest.v2.api.SpaceApi;
import net.atlassian.wiki.rest.v2.model.AttachmentBulk;
import net.atlassian.wiki.rest.v2.model.ContentPropertyCreateRequest;
import net.atlassian.wiki.rest.v2.model.MultiEntityLinks;
import net.atlassian.wiki.rest.v2.model.ContentPropertyUpdateRequest;
import net.atlassian.wiki.rest.v2.model.ContentPropertyUpdateRequestVersion;
import net.atlassian.wiki.rest.v2.model.CreatePageRequest;
import net.atlassian.wiki.rest.v2.model.CreatePageRequestBody;
import net.atlassian.wiki.rest.v2.model.PageBulk;
import net.atlassian.wiki.rest.v2.model.UpdatePageRequest;
import net.atlassian.wiki.rest.v2.model.UpdatePageRequestVersion;

/**
 * Client for publishing content to Confluence.
 *
 * This class manages the interaction with the Confluence REST API, handling the
 * creation, update, and publishing of pages and attachments to a Confluence
 * instance. It coordinates with parsers and transformers to convert source
 * content into Confluence storage format.
 *
 * @author Andreas Huber
 */
@Slf4j
public class ConfluenceClient {
	/**
	 * Constant for the Confluence page property key used to store the content hash.
	 *
	 * This property is written after every successful page update and is read
	 * before the next update to determine whether the content has actually changed.
	 */
	public static final String PROPERTY_KEY_CONTENT_HASH = "page-content-hash";

	/**
	 * Prefix written into the {@code comment} field of an uploaded attachment,
	 * followed by the SHA-256 hash of the uploaded file.
	 *
	 * The comment is read back before the next upload to decide whether the file
	 * has changed. The prefix keeps our marker distinguishable from comments a user
	 * may have set manually.
	 */
	static final String ATTACHMENT_HASH_PREFIX = "sha256:";

	/** Page size used for the cursor-based descendant and attachment queries. */
	private static final int PAGE_SIZE = 250;

	/** Configuration containing Confluence credentials and settings. */
	private final Configuration config;

	/** REST API client for Confluence v1 endpoints. */
	private final ApiClient clientV1;

	/** REST API client for Confluence v2 endpoints. */
	private final ApiClient clientV2;

	/** API for uploading content attachments (v1). */
	private final ContentAttachmentsApi attachmentsApi;

	/** API for listing and deleting attachments (v2). */
	private final AttachmentApi attachmentApi;

	/** API for managing content properties. */
	private final ContentPropertiesApi propertiesApi;

	/** API for managing Confluence spaces. */
	private final SpaceApi spaceApi;

	/** API for managing Confluence pages. */
	private final PageApi pageApi;

	/** Parser for extracting page content from source files. */
	private final Parser parser;

	/** Transformer for converting content to Confluence storage format. */
	private final Transformer transformer;

	/**
	 * Constructs a ConfluenceClient with the given configuration and converters.
	 *
	 * @param config      the Confluence configuration
	 * @param parser      the content parser
	 * @param transformer the content transformer
	 */
	public ConfluenceClient(final Configuration config, final Parser parser, final Transformer transformer) {
		this.config = config;
		this.parser = parser;
		this.transformer = transformer;
		clientV1 = new ApiClient(buildHttpClient());
		clientV1.setUsername(config.getUsername());
		clientV1.setPassword(config.getPassword());
		final var serverV1 = new ServerConfiguration(config.getUrl() + "/rest/api", null, Collections.emptyMap());
		clientV1.setServers(Collections.singletonList(serverV1));
		clientV1.setServerIndex(0);

		clientV2 = new ApiClient(buildHttpClient());
		clientV2.setUsername(config.getUsername());
		clientV2.setPassword(config.getPassword());
		final var serverV2 = new ServerConfiguration(config.getUrl() + "/api/v2", null, Collections.emptyMap());
		clientV2.setServers(Collections.singletonList(serverV2));
		clientV2.setServerIndex(0);
		attachmentsApi = new ContentAttachmentsApi(clientV1);
		attachmentApi = new AttachmentApi(clientV2);
		propertiesApi = new ContentPropertiesApi(clientV2);
		spaceApi = new SpaceApi(clientV2);
		pageApi = new PageApi(clientV2);
	}

	/**
	 * Builds a {@link CloseableHttpClient} with explicit timeouts and a bounded
	 * connection pool so the publisher cannot hang indefinitely on an unresponsive
	 * Confluence instance. TLS uses the JVM defaults — no custom trust manager.
	 */
	private static CloseableHttpClient buildHttpClient() {
		final ConnectionConfig connectionConfig = ConnectionConfig.custom()
				.setConnectTimeout(Timeout.of(15, TimeUnit.SECONDS))
				.setSocketTimeout(Timeout.of(60, TimeUnit.SECONDS))
				.build();
		final PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder
				.create()
				.setDefaultConnectionConfig(connectionConfig)
				.setMaxConnTotal(20)
				.setMaxConnPerRoute(10)
				.build();
		final RequestConfig requestConfig = RequestConfig.custom()
				.setConnectionRequestTimeout(Timeout.of(15, TimeUnit.SECONDS))
				.setResponseTimeout(Timeout.of(60, TimeUnit.SECONDS))
				.build();
		return HttpClients.custom()
				.setConnectionManager(connectionManager)
				.setDefaultRequestConfig(requestConfig)
				.build();
	}

	/**
	 * Updates or creates the given list of pages in the specified Confluence space.
	 *
	 * If a root page is configured, all pages are created under it. Otherwise, they
	 * are created at the space root level. Each page and its children are
	 * recursively processed.
	 *
	 * @param mapper the space mapper defining the target space and configuration
	 * @param pages  the list of pages to update or create
	 * @throws Exception if an error occurs during the update operation
	 */
	public void updatePages(final Mapper mapper, final List<Page> pages) throws Exception {
		var spaceId = mapper.getSpaceKey();
		final List<PageBulk> list = new ArrayList<>();
		if (!config.isDebug()) {
			final List<String> keys = List.of(spaceId);
			final var space = Utils.retry(
					() -> spaceApi.getSpaces(null, keys, null, null, null, null, null, null, null, null, null, null)
							.getResults().getFirst(),
					1);
			spaceId = space.getId();
		}
		PageBulk root = null;
		if (StringUtils.isNotBlank(mapper.getRoot())) {
			root = createOrUpdatePage(new Page(mapper.getRoot(), Path.of(mapper.getPath(), "index.html"), null), null,
					spaceId, list);
		}
		for (final Page page : pages) {
			createOrUpdatePage(page, root != null ? root.getId() : null, spaceId, list);
		}
	}

	/**
	 * Creates or updates a page in Confluence with its content and attachments.
	 *
	 * The body and the attachments are synchronized independently: an unchanged body
	 * no longer suppresses the attachment upload, so a page whose only change is a
	 * replaced image still gets that image published.
	 *
	 * @param page     the page to create or update
	 * @param parentId the parent page ID, or null if at root level
	 * @param spaceId  the target space ID
	 * @param list     existing pages in the space for lookup
	 * @return the created or updated page
	 * @throws Exception if an error occurs during the operation
	 */
	protected PageBulk createOrUpdatePage(final Page page, final String parentId, final String spaceId,
			final List<PageBulk> list) throws Exception {
		log.info("Create or update page {} ", page.getTitle());
		final var remote = getOrCreatePage(page, parentId, spaceId, list);
		if (page.getSource() != null) {
			final var content = parser.loadContent(page);
			final var result = transformer.transform(page, content);
			final var contentHash = computeHash(result.getContent());
			updateBody(page, remote, result.getContent(), contentHash);
			syncAttachments(remote.getId(), result.getAttachments());
		}
		for (final Page child : page.getChildren()) {
			createOrUpdatePage(child, remote.getId(), spaceId, list);
		}
		return remote;

	}

	private PageBulk getOrCreatePage(final Page page, final String parentId, final String spaceId,
			final List<PageBulk> list) throws Exception {
		final var title = page.getTitle();
		var remote = Utils.retry(() -> pageApi
				.getPagesInSpace(Long.parseLong(spaceId), "all", null, List.of("current"), title, null, null, 1)
				.getResults().stream().findFirst().orElse(null), 1);

		if (remote != null) {
			log.info("Page {} with id {} found", title, remote.getId());
			return remote;
		}
		if (!config.isDebug()) {
			final var response = Utils.retry(() -> pageApi.createPage(
					CreatePageRequest.builder().parentId(parentId).spaceId(spaceId).title(title)
							.body(CreatePageRequestBody.builder().value(page.getTitle())
									.representation(CreatePageRequestBody.RepresentationEnum.STORAGE).build())
							.build(),
					null, null, null), 1);
			remote = new PageBulk().id(response.getId()).title(response.getTitle()).spaceId(response.getSpaceId())
					.parentId(response.getSpaceId()).version(response.getVersion());
		} else {
			final var pageId = UUID.randomUUID().toString();
			remote = new PageBulk();
			remote.setId(pageId);
			remote.setSpaceId(spaceId);
			remote.setParentId(pageId);

		}
		log.info(" Page {} created with id {}", title, remote.getId());
		return remote;
	}

	/**
	 * Updates the body of a Confluence page if the content has changed.
	 *
	 * Computes a SHA-256 hash of the transformed content and compares it with the
	 * hash stored as a Confluence page property. If the hashes are identical the
	 * body update is skipped. Otherwise the page body is updated and the stored hash
	 * property is created or updated.
	 *
	 * Attachments are not affected by this decision — they are synchronized
	 * separately by {@link #syncAttachments(String, List)}.
	 *
	 * @param page        the page being published
	 * @param remote      the existing Confluence page
	 * @param body        the transformed Confluence Storage Format content
	 * @param contentHash the SHA-256 hex hash of {@code body}
	 * @throws Exception if an API call fails
	 */
	private void updateBody(final Page page, final PageBulk remote, final String body, final String contentHash)
			throws Exception {
		if (config.isDebug()) {
			return;
		}
		try {
			final var pageId = Long.parseLong(remote.getId());

			// Load existing properties before deciding whether to update.
			final var properties = Utils
					.retry(() -> propertiesApi.getPageContentProperties(pageId, null, null, null, null), 1);
			final Map<String, net.atlassian.wiki.rest.v2.model.ContentProperty> propMap = new HashMap<>();
			properties.getResults().forEach(p -> propMap.put(p.getKey(), p));

			// Skip update when the content hash matches the stored one.
			final var storedHashProp = propMap.get(PROPERTY_KEY_CONTENT_HASH);
			if (storedHashProp != null && contentHash.equals(storedHashProp.getValue())) {
				log.info("Page {} is up-to-date, skipping update", page.getTitle());
				return;
			}

			var version = (int) remote.getVersion().getNumber();
			version++;

			final var request = UpdatePageRequest.builder().id(remote.getId()).title(remote.getTitle())
					.status(UpdatePageRequest.StatusEnum.CURRENT)
					.version(UpdatePageRequestVersion.builder().number(version).build())
					.body(CreatePageRequestBody.builder()
							.representation(CreatePageRequestBody.RepresentationEnum.STORAGE).value(body).build())
					.build();
			Utils.retry(() -> pageApi.updatePage(pageId, request), 1);

			if (!propMap.containsKey("content-appearance-draft")) {
				Utils.retry(() -> propertiesApi.createPageProperty(pageId, ContentPropertyCreateRequest.builder()
						.key("content-appearance-draft").value("full-width").build()), 1);
			}
			if (!propMap.containsKey("content-appearance-published")) {
				Utils.retry(() -> propertiesApi.createPageProperty(pageId, ContentPropertyCreateRequest.builder()
						.key("content-appearance-published").value("full-width").build()), 1);
			}

			// Persist the new content hash so subsequent runs can skip unchanged pages.
			if (storedHashProp == null) {
				Utils.retry(() -> propertiesApi.createPageProperty(pageId, ContentPropertyCreateRequest.builder()
						.key(PROPERTY_KEY_CONTENT_HASH).value(contentHash).build()), 1);
			} else {
				final var propId = Long.parseLong(storedHashProp.getId());
				final var nextPropVersion = storedHashProp.getVersion().getNumber() + 1;
				Utils.retry(() -> propertiesApi.updatePagePropertyById(pageId, propId,
						ContentPropertyUpdateRequest.builder().key(PROPERTY_KEY_CONTENT_HASH).value(contentHash)
								.version(ContentPropertyUpdateRequestVersion.builder().number(nextPropVersion).build())
								.build()),
						1);
			}
		} catch (final Exception e) {
			log.warn("Failed to update page body for {}", page.getTitle(), e);
			throw e;
		}

	}

	/**
	 * Computes a SHA-256 hash of the given string content.
	 *
	 * The hash is used to determine whether the transformed page content has
	 * changed since the last publish run. Identical input always produces the same
	 * hex string.
	 *
	 * @param content the content to hash
	 * @return the SHA-256 hash as a lowercase hex string
	 */
	static String computeHash(final String content) {
		try {
			final var digest = MessageDigest.getInstance("SHA-256");
			final var hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (final NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 not available", e);
		}
	}

	/**
	 * Computes a SHA-256 hash of a file's content.
	 *
	 * Streamed rather than read into memory, because attachments are not only images
	 * but can be arbitrary documents.
	 *
	 * @param source the file to hash
	 * @return the SHA-256 hash as a lowercase hex string
	 * @throws IOException if the file cannot be read
	 */
	static String computeFileHash(final Path source) throws IOException {
		try {
			final var digest = MessageDigest.getInstance("SHA-256");
			try (var in = new DigestInputStream(Files.newInputStream(source), digest)) {
				final var buffer = new byte[8192];
				while (in.read(buffer) != -1) {
					// reading updates the digest
				}
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (final NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 not available", e);
		}
	}

	/**
	 * Builds the marker stored in an attachment's comment field.
	 *
	 * @param hash the SHA-256 hex hash of the uploaded file
	 * @return the comment value to send along with the upload
	 */
	static String attachmentComment(final String hash) {
		return ATTACHMENT_HASH_PREFIX + hash;
	}

	/**
	 * Parses a Confluence attachment ID into the numeric form the delete endpoint
	 * expects.
	 *
	 * The v2 API returns attachment IDs as strings matching {@code (att)?[0-9]+},
	 * so the optional {@code att} prefix has to be stripped before parsing.
	 *
	 * @param id the attachment ID as returned by the API
	 * @return the numeric attachment ID
	 * @throws IllegalArgumentException if {@code id} is null or not a valid ID
	 */
	static long parseAttachmentId(final String id) {
		if (StringUtils.isBlank(id)) {
			throw new IllegalArgumentException("Attachment id must not be blank");
		}
		final var digits = id.startsWith("att") ? id.substring(3) : id;
		try {
			return Long.parseLong(digits);
		} catch (final NumberFormatException e) {
			throw new IllegalArgumentException("Unexpected attachment id: " + id, e);
		}
	}

	/**
	 * Extracts the {@code cursor} parameter from a paginated response's next link.
	 *
	 * @param links the links of a multi-entity response, may be null
	 * @return the cursor for the next page, or null if there is none
	 */
	static String nextCursor(final MultiEntityLinks links) {
		if (links == null || StringUtils.isBlank(links.getNext())) {
			return null;
		}
		return extractQueryParam(links.getNext(), "cursor");
	}

	/**
	 * Reads a single query parameter from a URL.
	 *
	 * Operates on the raw query and splits at the first {@code =} only, because
	 * Confluence cursors are base64 values that may contain padding characters.
	 *
	 * @param url  the URL to read from
	 * @param name the parameter name
	 * @return the decoded parameter value, or null if absent or unparsable
	 */
	static String extractQueryParam(final String url, final String name) {
		final String query;
		try {
			query = URI.create(url).getRawQuery();
		} catch (final IllegalArgumentException e) {
			log.warn("Cannot parse pagination link '{}'", url, e);
			return null;
		}
		if (query == null) {
			return null;
		}
		for (final String pair : query.split("&")) {
			final var separator = pair.indexOf('=');
			if (separator > 0 && name.equals(pair.substring(0, separator))) {
				return URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8);
			}
		}
		return null;
	}

	/**
	 * Synchronizes the attachments of a page.
	 *
	 * Uploads attachments that are new or whose content hash differs from the one
	 * stored in the remote attachment's comment, and moves attachments that no
	 * longer exist locally to the trash. Runs independently of the page body, so a
	 * changed image is published even when the body is unchanged.
	 *
	 * @param pageId the Confluence page ID
	 * @param local  the attachments referenced by the transformed page
	 * @throws Exception if listing the existing attachments fails
	 */
	private void syncAttachments(final String pageId, final List<Attachment> local) throws Exception {
		if (config.isDebug()) {
			log.info("Debug mode, skipping {} attachment(s) for page {}", local.size(), pageId);
			return;
		}
		final var remote = loadAttachments(pageId);
		final Set<String> localNames = new LinkedHashSet<>();
		for (final Attachment attachment : local) {
			localNames.add(attachment.getFileName());
			try {
				final var hash = computeFileHash(attachment.getSource());
				final var existing = remote.get(attachment.getFileName());
				if (existing == null) {
					log.info("Uploading new attachment {}", attachment.getFileName());
				} else if (!attachmentComment(hash).equals(existing.getComment())) {
					log.info("Attachment {} changed, uploading new version", attachment.getFileName());
				} else {
					log.debug("Attachment {} is up-to-date, skipping upload", attachment.getFileName());
					continue;
				}
				createOrUpdateAttachment(pageId, attachment, hash);
			} catch (final IOException e) {
				log.error("Failed to read attachment {}", attachment.getSource(), e);
			}
		}
		remote.forEach((fileName, existing) -> {
			if (!localNames.contains(fileName)) {
				log.info("Attachment {} no longer exists locally, moving to trash", fileName);
				deleteAttachment(existing);
			}
		});
	}

	/**
	 * Loads all current attachments of a page, following cursor-based pagination.
	 *
	 * @param pageId the Confluence page ID
	 * @return the attachments keyed by file name
	 * @throws Exception if an API call fails
	 */
	private Map<String, AttachmentBulk> loadAttachments(final String pageId) throws Exception {
		final var id = Long.parseLong(pageId);
		final Map<String, AttachmentBulk> result = new LinkedHashMap<>();
		String cursor = null;
		do {
			final var current = cursor;
			final var response = Utils.retry(() -> attachmentApi.getPageAttachments(id, null, current,
					List.of("current"), null, null, PAGE_SIZE), 1);
			response.getResults().forEach(a -> result.put(a.getTitle(), a));
			cursor = advanceCursor(current, nextCursor(response.getLinks()));
		} while (cursor != null);
		return result;
	}

	/**
	 * Guards the pagination loops against an API that keeps returning the same
	 * cursor, which would otherwise spin forever.
	 *
	 * @param current the cursor used for the request just made
	 * @param next    the cursor advertised by the response
	 * @return {@code next}, or null if it did not advance
	 */
	private static String advanceCursor(final String current, final String next) {
		if (next != null && next.equals(current)) {
			log.warn("Pagination cursor did not advance, stopping");
			return null;
		}
		return next;
	}

	private void createOrUpdateAttachment(final String contentId, final Attachment attachment, final String hash) {
		try {
			Utils.retry(() -> {
				attachmentsApi.createOrUpdateAttachments(contentId, attachment.getSource().toFile(), "binary",
						"current", attachmentComment(hash));
				return null;
			}, 1);
		} catch (final Exception e) {
			log.error("Failed to upload attachment {} to {}", attachment.getFileName(), contentId, e);
		}
	}

	private void deleteAttachment(final AttachmentBulk attachment) {
		try {
			final var id = parseAttachmentId(attachment.getId());
			Utils.retry(() -> attachmentApi.deleteAttachment(id, false), 1);
		} catch (final Exception e) {
			log.error("Failed to delete attachment {} ({})", attachment.getTitle(), attachment.getId(), e);
		}
	}

}
