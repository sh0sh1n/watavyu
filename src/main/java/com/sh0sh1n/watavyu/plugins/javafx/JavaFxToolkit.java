/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sh0sh1n.watavyu.plugins.javafx;

import javafx.application.Platform;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.SwingUtilities;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

/**
 * Starts the JavaFX runtime once for the whole application and moves work between
 * the JavaFX Application Thread, the Swing EDT and background threads.
 */
public final class JavaFxToolkit {

    private static final Logger logger = LogManager.getLogger(JavaFxToolkit.class);

    private static final Object START_LOCK = new Object();
    private static boolean started;

    private JavaFxToolkit() {
    }

    /**
     * Starts JavaFX if it is not running. Closing the last video window must not stop
     * JavaFX, because the next video opened in the same session needs it again.
     */
    public static void ensureStarted() {
        synchronized (START_LOCK) {
            if (started) {
                return;
            }
            CountDownLatch latch = new CountDownLatch(1);
            try {
                Platform.startup(latch::countDown);
                latch.await();
            } catch (IllegalStateException alreadyRunning) {
                // Started elsewhere, for example by a JFXPanel, which is as good.
                logger.debug("JavaFX was already running", alreadyRunning);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while starting JavaFX", e);
            }
            Platform.setImplicitExit(false);
            started = true;
        }
    }

    /** Runs a task on the JavaFX Application Thread; the future holds its result, or what it threw. */
    public static <T> Future<T> onFx(final Callable<T> task) {
        FutureTask<T> future = new FutureTask<>(task);
        if (Platform.isFxApplicationThread()) {
            future.run();
        } else {
            Platform.runLater(future);
        }
        return future;
    }

    /**
     * Runs a task on the Swing EDT and waits for it. Must be called off the EDT.
     *
     * @throws ExecutionException   holding what the task threw
     * @throws InterruptedException if the wait is interrupted
     */
    public static <T> T onEdtAndWait(final Callable<T> task) throws InterruptedException, ExecutionException {
        requireBackgroundThread();
        FutureTask<T> future = new FutureTask<>(task);
        SwingUtilities.invokeLater(future);
        return future.get();
    }

    /** Fails fast if a caller would block a UI thread while media loads. */
    public static void requireBackgroundThread() {
        if (SwingUtilities.isEventDispatchThread() || Platform.isFxApplicationThread()) {
            throw new IllegalStateException("Media must be opened off the Swing and JavaFX UI threads");
        }
    }

}
