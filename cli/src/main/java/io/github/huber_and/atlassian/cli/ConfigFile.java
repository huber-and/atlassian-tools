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
package io.github.huber_and.atlassian.cli;

import java.util.ArrayList;
import java.util.List;

import io.github.huber_and.atlassian.wiki.Configuration;
import lombok.Data;

/**
 * Shape of a {@code --config} YAML file: mirrors {@link Configuration} itself
 * so there is only one schema to document, not a second one invented for the
 * CLI.
 *
 * @author Andreas Huber
 */
@Data
public class ConfigFile {

	/** Overridden by {@code --url} when both are given. */
	private String url;

	/** Overridden by {@code --parser-class} when both are given. */
	private String parserClass;

	/** The mappers to publish; used as-is in multi-mapper mode. */
	private List<Configuration.Mapper> mappers = new ArrayList<>();
}
