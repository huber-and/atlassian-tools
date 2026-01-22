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
package io.github.huber_and.atlassian.wiki;

import io.github.huber_and.atlassian.wiki.util.ThrowableRunnable;
import io.github.huber_and.atlassian.wiki.util.ThrowableSupplier;

public class Utils {

	private Utils() {
		// Utility class
	}

	/**
	 * Retries the given operation up to maxRetries times.
	 *
	 * @param operation
	 * @param maxRetries
	 * @throws Exception
	 */
	public static void retry(final ThrowableRunnable operation, final int maxRetries) throws Exception {
		retry(() -> {
			operation.run();
			return null;
		}, maxRetries, 0);
	}

	/**
	 * Retries the given operation up to maxRetries times.
	 *
	 * @param operation
	 * @param maxRetries
	 * @param delayMillis
	 * @throws Exception
	 */
	public static void retry(final ThrowableRunnable operation, final int maxRetries, final long delayMillis)
			throws Exception {
		retry(() -> {
			operation.run();
			return null;
		}, maxRetries, delayMillis);
	}

	/**
	 * Retries the given operation up to maxRetries times.
	 *
	 * @param operation
	 * @param maxRetries
	 * @throws Exception
	 */
	public static <T> T retry(final ThrowableSupplier<T> operation, final int maxRetries) throws Exception {
		return retry(operation, maxRetries, 0);
	}

	/**
	 * Retries the given operation up to maxRetries times with a delay between
	 * attempts.
	 *
	 * @param operation
	 * @param maxRetries
	 * @param delayMillis
	 * @throws Exception
	 */
	public static <T> T retry(final ThrowableSupplier<T> operation, final int maxRetries, final long delayMillis)
			throws Exception {
		var attempt = 0;
		while (true) {
			try {
				return operation.get();
			} catch (final Exception e) {
				attempt++;
				if (attempt > maxRetries) {
					throw e; // Rethrow after max retries}
				}
				Thread.sleep(delayMillis);
			}
		}

	}
}