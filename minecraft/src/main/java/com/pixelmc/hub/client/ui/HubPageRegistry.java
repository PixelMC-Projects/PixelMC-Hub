/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Thread-safe registration entry point for external sibling pages displayed inside {@link HubView}.
 */
public final class HubPageRegistry {
    public record Registration(
            String id,
            int order,
            long sequence,
            Function<HubPageHost, ? extends HubExternalPage> factory) {}

    private static final Object LOCK = new Object();
    private static final List<Registration> REGISTRATIONS = new ArrayList<>();
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final AtomicLong VERSION = new AtomicLong();
    private static volatile List<Registration> snapshot = List.of();

    private HubPageRegistry() {}

    public static void registerExternalPage(
            Function<HubPageHost, ? extends HubExternalPage> factory) {
        registerExternalPage("", 0, factory);
    }

    public static void registerExternalPage(
            String id, Function<HubPageHost, ? extends HubExternalPage> factory) {
        registerExternalPage(id, 0, factory);
    }

    public static void registerExternalPage(
            String id, int order, Function<HubPageHost, ? extends HubExternalPage> factory) {
        Objects.requireNonNull(factory, "factory");
        String normalizedId = id == null ? "" : id.strip();
        synchronized (LOCK) {
            if (!normalizedId.isEmpty()) {
                REGISTRATIONS.removeIf(item -> item.id().equals(normalizedId));
            }
            REGISTRATIONS.add(
                    new Registration(normalizedId, order, SEQUENCE.incrementAndGet(), factory));
            REGISTRATIONS.sort(
                    Comparator.comparingInt(Registration::order)
                            .thenComparingLong(Registration::sequence));
            snapshot = List.copyOf(REGISTRATIONS);
            VERSION.incrementAndGet();
        }
    }

    public static boolean unregisterExternalPage(String id) {
        if (id == null || id.isBlank()) return false;
        String normalizedId = id.strip();
        synchronized (LOCK) {
            boolean removed = REGISTRATIONS.removeIf(item -> item.id().equals(normalizedId));
            if (removed) {
                snapshot = List.copyOf(REGISTRATIONS);
                VERSION.incrementAndGet();
            }
            return removed;
        }
    }

    public static List<Registration> externalPages() {
        return snapshot;
    }

    static long version() {
        return VERSION.get();
    }
}
