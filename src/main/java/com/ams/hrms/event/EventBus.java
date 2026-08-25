package com.ams.hrms.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;

/**
 * Lightweight in-process pub/sub (architecture decision: modules react to
 * events instead of holding references to each other - prevents leaks and
 * coupling). Delivery always happens on the EDT.
 *
 * <pre>{@code
 * EventBus.subscribe(Events.DataChanged.class, e -> loadRows());
 * EventBus.publish(new Events.DataChanged("employees"));
 * }</pre>
 */
public final class EventBus {

    private static final Map<Class<?>, List<Consumer<?>>> SUBSCRIBERS = new ConcurrentHashMap<>();

    private EventBus() {
    }

    /** Registers a consumer for the given event type. */
    public static <T> void subscribe(Class<T> type, Consumer<T> consumer) {
        SUBSCRIBERS.computeIfAbsent(type, key -> new CopyOnWriteArrayList<>()).add(consumer);
    }

    /** Removes a previously registered consumer. */
    public static <T> void unsubscribe(Class<T> type, Consumer<T> consumer) {
        List<Consumer<?>> subscribers = SUBSCRIBERS.get(type);
        if (subscribers != null) {
            subscribers.remove(consumer);
        }
    }

    /** Publishes an event; handlers run on the EDT regardless of caller thread. */
    public static <T> void publish(T event) {
        if (SwingUtilities.isEventDispatchThread()) {
            deliver(event);
        } else {
            SwingUtilities.invokeLater(() -> deliver(event));
        }
    }

    @SuppressWarnings("unchecked")
    static <T> void deliver(T event) {
        List<Consumer<?>> subscribers = SUBSCRIBERS.get(event.getClass());
        if (subscribers == null) {
            return;
        }
        for (Consumer<?> subscriber : subscribers) {
            ((Consumer<T>) subscriber).accept(event);
        }
    }
}
