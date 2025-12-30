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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import com.github.huber_and.atlassian.wiki.Configuration.Mapper;
import com.github.huber_and.atlassian.wiki.parser.Parser;
import com.github.huber_and.atlassian.wiki.transformer.Transformer;

import lombok.extern.slf4j.Slf4j;
import net.atlassian.wiki.rest.ApiClient;
import net.atlassian.wiki.rest.ApiException;
import net.atlassian.wiki.rest.ServerConfiguration;
import net.atlassian.wiki.rest.v1.api.ContentAttachmentsApi;
import net.atlassian.wiki.rest.v2.api.ContentPropertiesApi;
import net.atlassian.wiki.rest.v2.api.PageApi;
import net.atlassian.wiki.rest.v2.api.SpaceApi;
import net.atlassian.wiki.rest.v2.model.ContentPropertyCreateRequest;
import net.atlassian.wiki.rest.v2.model.CreatePageRequest;
import net.atlassian.wiki.rest.v2.model.CreatePageRequestBody;
import net.atlassian.wiki.rest.v2.model.PageBulk;
import net.atlassian.wiki.rest.v2.model.UpdatePageRequest;
import net.atlassian.wiki.rest.v2.model.UpdatePageRequestVersion;

@Slf4j
public class ConfluenceClient {

	private final Configuration config;
	private final ApiClient clientV1;
	private final ApiClient clientV2;
	private final ContentAttachmentsApi attachmentsApi;
	private final ContentPropertiesApi propertiesApi;
	private final SpaceApi spaceApi;
	private final PageApi pageApi;
	private final Parser parser;
	private final Transformer transformer;

	public ConfluenceClient(final Configuration config, final Parser parser, final Transformer transformer) {
		this.config = config;
		this.parser = parser;
		this.transformer = transformer;
		clientV1 = new ApiClient();
		clientV1.setUsername(config.getUsername());
		clientV1.setPassword(config.getPassword());
		final var serverV1 = new ServerConfiguration(config.getUrl() + "/rest/api", null, Collections.emptyMap());
		clientV1.setServers(Collections.singletonList(serverV1));
		clientV1.setServerIndex(0);

		clientV2 = new ApiClient();
		clientV2.setUsername(config.getUsername());
		clientV2.setPassword(config.getPassword());
		final var serverV2 = new ServerConfiguration(config.getUrl() + "/api/v2", null, Collections.emptyMap());
		clientV2.setServers(Collections.singletonList(serverV2));
		clientV2.setServerIndex(0);
		attachmentsApi = new ContentAttachmentsApi(clientV1);
		propertiesApi = new ContentPropertiesApi(clientV2);
		spaceApi = new SpaceApi(clientV2);
		pageApi = new PageApi(clientV2);
	}

	/**
	 * Update the given list of pages in the given confluence space
	 *
	 * @param mapper
	 * @param pages
	 * @throws Exception
	 */
	public void updatePages(final Mapper mapper, final List<Page> pages) throws Exception {
		var spaceId = mapper.getSpaceKey();
		List<PageBulk> list = Collections.emptyList();
		if (!config.isDebug()) {
			final var space = spaceApi
					.getSpaces(null, List.of(spaceId), null, null, null, null, null, null, null, null, null, null)
					.getResults().getFirst();
			spaceId = space.getId();
			list = pageApi.getPagesInSpace(Long.parseLong(space.getId()), "all", null, List.of("current"), null, null,
					null, null).getResults().stream().toList();
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

	protected PageBulk createOrUpdatePage(final Page page, final String parentId, final String spaceId,
			final List<PageBulk> list) throws Exception {
		log.info("Create or update page {} ", page.getTitle());
		final var remote = getOrCreatePage(page, parentId, spaceId, list);
		if (page.getSource() != null) {
			final var content = parser.loadContent(page);
			final var result = transformer.transform(page, content);
			updateBody(page, remote, result.getContent());
			result.getAttachments().forEach(a -> createOrUpdateAttachment(remote.getId(), a));
		}
		for (final Page child : page.getChildren()) {
			createOrUpdatePage(child, remote.getId(), spaceId, list);
		}
		return remote;

	}

	private PageBulk getOrCreatePage(final Page page, final String parentId, final String spaceId,
			final List<PageBulk> list) throws Exception {
		final var title = page.getTitle();
		final var result = list.stream().filter(r -> Strings.CS.equals(page.getTitle(), r.getTitle())).findFirst();

		PageBulk remote = null;
		if (result.isPresent()) {
			remote = result.get();
			log.info("Root Page {} with id {} found", title, remote.getId());
		} else {
			String pageId;
			if (!config.isDebug()) {
				final var response = pageApi.createPage(CreatePageRequest.builder().parentId(parentId).spaceId(spaceId)
						.title(title)
						.body(CreatePageRequestBody.builder().value(page.getTitle())
								.representation(CreatePageRequestBody.RepresentationEnum.STORAGE).build())
						.build(), null, null, null);
				remote = new PageBulk().id(response.getId()).title(response.getTitle()).spaceId(response.getSpaceId())
						.parentId(response.getSpaceId()).version(response.getVersion());
			} else {
				pageId = UUID.randomUUID().toString();
				remote = new PageBulk();
				remote.setId(pageId);
				remote.setSpaceId(spaceId);
				remote.setParentId(pageId);

			}
			log.info(" Page {} created with id {}", title, remote.getId());
		}
		return remote;
	}

	private void updateBody(final Page page, final PageBulk remote, final String body) throws Exception {
		if (config.isDebug()) {
			return;
		}
		try {
			var version = (int) remote.getVersion().getNumber();
			version++;

			final var request = UpdatePageRequest.builder().id(remote.getId()).title(remote.getTitle())
					.status(UpdatePageRequest.StatusEnum.CURRENT)
					.version(UpdatePageRequestVersion.builder().number(version).build())
					.body(CreatePageRequestBody.builder()
							.representation(CreatePageRequestBody.RepresentationEnum.STORAGE).value(body).build())
					.build();
			pageApi.updatePage(Long.parseLong(remote.getId()), request);
			final var properties = propertiesApi.getPageContentProperties(Long.parseLong(remote.getId()), null, null,
					null, null);
			final Map<String, Object> list = new HashMap<>();
			properties.getResults().forEach(p -> list.put(p.getKey(), p));
			if (!list.containsKey("content-appearance-draft")) {
				propertiesApi.createPageProperty(Long.parseLong(remote.getId()), ContentPropertyCreateRequest.builder()
						.key("content-appearance-draft").value("full-width").build());
			}
			if (!list.containsKey("content-appearance-published")) {
				propertiesApi.createPageProperty(Long.parseLong(remote.getId()), ContentPropertyCreateRequest.builder()
						.key("content-appearance-published").value("full-width").build());
			}
		} catch (final Exception e) {
			log.warn("Failed to update page body for {}", page.getTitle(), e);
			throw e;
		}

	}

	private void createOrUpdateAttachment(final String contentId, final Attachment attachment) {
		if (config.isDebug()) {
			return;
		}
		try {
			attachmentsApi.createOrUpdateAttachments(contentId, attachment.getSource().toFile(), "binary", "current",
					null);
		} catch (final ApiException e) {
			log.error("Failed to upload attachment {} to {}", attachment.getFileName(), contentId, e);
		}
	}

}
