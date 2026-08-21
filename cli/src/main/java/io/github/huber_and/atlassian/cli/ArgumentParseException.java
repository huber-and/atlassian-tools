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

/**
 * Thrown when the command line cannot be parsed: an unknown option, a missing
 * value, a missing required option, or two mutually exclusive options given
 * together.
 *
 * @author Andreas Huber
 */
public class ArgumentParseException extends Exception {

	private static final long serialVersionUID = 1L;

	public ArgumentParseException(final String message) {
		super(message);
	}
}
