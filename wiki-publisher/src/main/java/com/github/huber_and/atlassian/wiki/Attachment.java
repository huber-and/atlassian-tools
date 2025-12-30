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

import lombok.Data;

/**
 * Represents a file attachment for Confluence pages.
 *
 * This class encapsulates the information needed to upload a file as an attachment
 * to a Confluence page, including the file name and the source path.
 *
 * @author Andreas Huber
 */
@Data
public class Attachment {

	/** The name of the attachment file. */
	private String fileName;

	/** The source path to the attachment file. */
	private Path source;

}
