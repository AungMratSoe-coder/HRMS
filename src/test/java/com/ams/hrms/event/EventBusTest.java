package com.ams.hrms.event;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure-logic tests for the in-process event bus.
 */
class EventBusTest {

    record TestEvent(String payload) {
    }

    record OtherEvent(String payload) {
    }

    @Test
    @DisplayName("delivers events to subscribers of the matching type")
    void deliversToMatchingSubscribers() {
        AtomicInteger hits = new AtomicInteger();
        EventBus.subscribe(TestEvent.class, event -> hits.addAndGet(event.payload().length()));
        EventBus.deliver(new TestEvent("employees"));

        assertThat(hits.get()).isEqualTo(9);
    }

    @Test
    @DisplayName("does not deliver to subscribers of other types")
    void ignoresOtherTypes() {
        AtomicInteger hits = new AtomicInteger();
        EventBus.subscribe(OtherEvent.class, event -> hits.incrementAndGet());

        EventBus.deliver(new TestEvent("leave"));

        assertThat(hits.get()).isZero();
    }

    @Test
    @DisplayName("unsubscribe stops delivery")
    void unsubscribeStopsDelivery() {
        AtomicInteger hits = new AtomicInteger();
        java.util.function.Consumer<TestEvent> consumer = event -> hits.incrementAndGet();

        EventBus.subscribe(TestEvent.class, consumer);
        EventBus.deliver(new TestEvent("x"));
        EventBus.unsubscribe(TestEvent.class, consumer);
        EventBus.deliver(new TestEvent("y"));

        assertThat(hits.get()).isEqualTo(1);
    }
}
