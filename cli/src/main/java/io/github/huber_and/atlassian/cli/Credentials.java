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
 * Resolved Confluence credentials, from whichever source
 * {@link CredentialResolver} found them.
 *
 * <p>
 * Overrides the generated {@link #toString()} so the password can never end
 * up in a log line, mirroring {@code Configuration}'s
 * {@code @ToString.Exclude}.
 *
 * @param username the Confluence account name
 * @param password the password or API token; never included in
 *                  {@link #toString()}
 * @author Andreas Huber
 */
public record Credentials(String username, String password) {

	@Override
	public String toString() {
		return "Credentials[username=" + username + ", password=***]";
	}
}
