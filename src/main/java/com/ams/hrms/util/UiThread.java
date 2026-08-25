package com.ams.hrms.util;

import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.hrms.exception.ErrorHandler;

/**
 * Single entry point for background work in the UI layer (spec sections 44
 * and 45). Every database-touching UI action must go through
 * {@link #executeAsync} so the EDT is never blocked and errors are funneled
 * into the central {@link ErrorHandler}.
 *
 * <pre>{@code
 * UiThread.executeAsync(
 *         "Load employees",
 *         () -> employeeService.search(filter),
 *         rows -> model.setRows(rows));
 * }</pre>
 */
public final class UiThread {

    private static final Logger LOG = LoggerFactory.getLogger(UiThread.class);

    private UiThread() {
    }

    /** Asserts the current thread is the EDT (dev guard for UI code). */
    public static void ensureEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("UI state may only be modified on the EDT");
        }
    }

    /** Runs a task on the EDT at a later time. */
    public static void runLater(Runnable action) {
        SwingUtilities.invokeLater(action);
    }

    /**
     * Runs {@code work} off the EDT. On success {@code onSuccess} receives the
     * result on the EDT; on failure the exception is routed to the central
     * {@link ErrorHandler}.
     */
    public static <T> void executeAsync(String taskName, Supplier<T> work, Consumer<T> onSuccess) {
        executeAsync(taskName, work, onSuccess, null);
    }

    /**
     * Runs {@code work} off the EDT with explicit error handling.
     *
     * @param onError receives the exception on the EDT; when null, errors go
     *                to the central {@link ErrorHandler}
     */
    public static <T> void executeAsync(String taskName, Supplier<T> work, Consumer<T> onSuccess,
                                        Consumer<Exception> onError) {
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() {
                return work.get();
            }

            @Override
            protected void done() {
                try {
                    T result = get();
                    if (onSuccess != null) {
                        onSuccess.accept(result);
                    }
                } catch (java.util.concurrent.ExecutionException e) {
                    // Unwrap so callers see the original typed exception.
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    LOG.debug("Background task '{}' failed", taskName, cause);
                    if (cause instanceof Exception typed) {
                        if (onError != null) {
                            onError.accept(typed);
                        } else {
                            ErrorHandler.handle(typed);
                        }
                    } else {
                        LOG.error("Background task '{}' failed with a non-exception throwable", taskName, cause);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOG.warn("Background task '{}' was interrupted", taskName);
                }
            }
        }.execute();
    }

    /** Runs a result-less task off the EDT with default error handling. */
    public static void runAsync(String taskName, Runnable work) {
        executeAsync(taskName, () -> {
            work.run();
            return null;
        }, result -> {
        });
    }
}
