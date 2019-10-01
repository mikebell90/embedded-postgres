/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zonky.test.db.postgres.embedded;

import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Read standard output of process and write lines to given {@link Logger} as INFO;
 * depends on {@link ProcessBuilder#redirectErrorStream(boolean)} being set to {@code true} (since only stdout is
 * read).
 *
 * <p>
 * The use of the input stream is threadsafe since it's used only in a single thread&mdash;the one launched by this
 * code.
 */
final class ProcessOutputLogger implements Runnable {
    @SuppressWarnings("PMD.LoggerIsNotStaticFinal")
    private final Logger logger;
    private final BufferedReader reader;

    private ProcessOutputLogger(final Logger logger, final Process process) {
        this.logger = logger;
        this.reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
    }

    @Override
    public void run() {
        try {
            try {
                reader.lines().forEach(logger::info);
            } catch (final UncheckedIOException e) {
                logger.error("while reading output", e);
            }
        } finally {
            try {
                reader.close();
            } catch (final IOException e) {
                logger.error("caught i/o exception closing reader", e);
            }
        }
    }

    static void logOutput(final Logger logger, final Process process, final String processName) {
        final String threadName = (isNotBlank(processName) ? processName : "unknown") + ":" + processId(process);
        final Thread t = new Thread(new ProcessOutputLogger(logger, process));
        t.setName(threadName);
        t.setDaemon(true);
        t.start();
    }

    private static String processId(Process process) {
        try { // java 9+
            return String.format("pid(%s)", MethodHandles.lookup().findVirtual(Process.class, "pid", MethodType.methodType(long.class)).invoke(process));
        } catch (Throwable ignored) {} // NOPMD since MethodHandles.invoke throws Throwable

        try { // openjdk / oraclejdk 8
            final Field pid = process.getClass().getDeclaredField("pid");
            pid.setAccessible(true);
            return String.format("pid(%s)", pid.getInt(process));
        } catch (Exception ignored) {} // NOPMD

        return String.format("id(%s)", process.hashCode());
    }
}
