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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Builds a class loader for {@code --plugin-dir}, so a custom {@code Parser}
 * can be added to an otherwise closed shaded-jar classpath.
 *
 * <p>
 * {@code Publisher.loadClass()} already tries the thread context class
 * loader before falling back to its own, so setting the returned loader as
 * the context class loader is all that is needed — no change to
 * {@code wiki-publisher} is required.
 *
 * @author Andreas Huber
 */
public final class PluginClassLoaderFactory {

	private PluginClassLoaderFactory() {
	}

	/**
	 * Builds a class loader from every {@code *.jar} file directly inside the
	 * given directory.
	 *
	 * @param pluginDir the directory to scan
	 * @param parent    the parent class loader
	 * @return a class loader that can see the jars in {@code pluginDir}
	 * @throws IOException if the directory cannot be listed
	 */
	public static ClassLoader forDirectory(final Path pluginDir, final ClassLoader parent) throws IOException {
		try (var files = Files.list(pluginDir)) {
			final var urls = files.filter(p -> p.getFileName().toString().endsWith(".jar"))
					.map(PluginClassLoaderFactory::toUrl).toArray(URL[]::new);
			return new URLClassLoader(urls, parent);
		}
	}

	private static URL toUrl(final Path path) {
		try {
			return path.toUri().toURL();
		} catch (final MalformedURLException e) {
			throw new UncheckedIOException(e);
		}
	}
}
