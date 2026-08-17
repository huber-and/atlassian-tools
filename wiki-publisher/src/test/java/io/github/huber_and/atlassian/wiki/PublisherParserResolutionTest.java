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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.huber_and.atlassian.wiki.parser.AntoraParser;
import io.github.huber_and.atlassian.wiki.parser.Parser;

/**
 * Tests the resolution of the configurable parser class.
 *
 * @author Andreas Huber
 */
class PublisherParserResolutionTest {

	/** Set by {@link Detonator}'s static initializer if it is ever initialized. */
	static boolean detonated = false;

	/** A class that is not a {@link Parser} and must never be initialized. */
	static class Detonator {
		static {
			detonated = true;
		}
	}

	/** A minimal parser implementation recording that {@code init} was called. */
	public static class RecordingParser implements Parser {

		Configuration config;

		@Override
		public void init(final Configuration config) throws IOException {
			this.config = config;
		}

		@Override
		public List<Page> resolvePages(final Path root) throws IOException {
			return List.of();
		}

		@Override
		public Element loadContent(final Page page) throws IOException {
			return null;
		}
	}

	private Configuration config;

	@BeforeEach
	void setUp() {
		detonated = false;
		config = new Configuration();
		config.setUrl("https://confluence.example.com");
	}

	@Test
	void defaultsToAntoraParser() {
		assertEquals(AntoraParser.class.getName(), new Configuration().getParserClass());
		assertEquals(AntoraParser.class, new Publisher(config).getParser().getClass());
	}

	@Test
	void blankParserClassFallsBackToDefault() {
		config.setParserClass("  ");

		assertEquals(AntoraParser.class, new Publisher(config).getParser().getClass());
	}

	@Test
	void customParserIsInstantiatedAndInitialized() {
		config.setParserClass(RecordingParser.class.getName());

		final var parser = new Publisher(config).getParser();

		assertEquals(RecordingParser.class, parser.getClass());
		assertEquals(config, ((RecordingParser) parser).config, "init() must receive the configuration");
	}

	@Test
	void unknownParserClassIsRejected() {
		config.setParserClass("com.example.DoesNotExist");

		final var e = assertThrows(IllegalStateException.class, () -> new Publisher(config));

		assertTrue(e.getMessage().contains("com.example.DoesNotExist"), () -> "unexpected message: " + e.getMessage());
	}

	@Test
	void classNotImplementingParserIsRejectedWithoutBeingInitialized() {
		config.setParserClass(Detonator.class.getName());

		final var e = assertThrows(IllegalStateException.class, () -> new Publisher(config));

		assertTrue(e.getMessage().contains("does not implement"), () -> "unexpected message: " + e.getMessage());
		assertFalse(detonated, "static initializer of a non-Parser class must not run");
	}
}
