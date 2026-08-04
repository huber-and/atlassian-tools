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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class ConfigurationTest {

	private static final String SECRET = "super-secret-token-42";

	@Test
	void toStringDoesNotLeakPassword() {
		final var config = new Configuration();
		config.setUrl("https://confluence.example.com");
		config.setUsername("alice");
		config.setPassword(SECRET);

		final var rendered = config.toString();

		assertFalse(rendered.contains(SECRET),
				() -> "Configuration.toString() leaked password: " + rendered);
		assertFalse(rendered.toLowerCase().contains("password="),
				() -> "Configuration.toString() should not include the password field at all: " + rendered);
	}

	@Test
	void equalsIgnoresPassword() {
		final var a = new Configuration();
		a.setUrl("https://confluence.example.com");
		a.setUsername("alice");
		a.setPassword("one");

		final var b = new Configuration();
		b.setUrl("https://confluence.example.com");
		b.setUsername("alice");
		b.setPassword("two");

		assertEquals(a, b, "password must not influence equals()");
		assertEquals(a.hashCode(), b.hashCode(), "password must not influence hashCode()");

		b.setUsername("bob");
		assertNotEquals(a, b, "username should still influence equals()");
	}
}
