/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Optional client-side plugin slot for external modpack synchronization mods (such as
 * AutoModpack-OSS). Uses only JDK functional interfaces so external mods can register cleanly
 * without a compile-time dependency on PixelMC-Hub or Skija.
 */
public final class HubModpackSync {
    private record Handler(
            BooleanSupplier enabled,
            Supplier<String> channel,
            BooleanSupplier granted,
            Consumer<Boolean> setEnabled,
            Consumer<String> setChannel,
            Runnable revokeAll) {}

    private static volatile Handler handler;

    private HubModpackSync() {}

    public static void register(
            BooleanSupplier enabled,
            Supplier<String> channel,
            BooleanSupplier granted,
            Consumer<Boolean> setEnabled,
            Consumer<String> setChannel,
            Runnable revokeAll) {
        handler =
                new Handler(
                        Objects.requireNonNull(enabled, "enabled"),
                        Objects.requireNonNull(channel, "channel"),
                        Objects.requireNonNull(granted, "granted"),
                        Objects.requireNonNull(setEnabled, "setEnabled"),
                        Objects.requireNonNull(setChannel, "setChannel"),
                        Objects.requireNonNull(revokeAll, "revokeAll"));
    }

    public static void unregister() {
        handler = null;
    }

    public static boolean available() {
        return handler != null;
    }

    public static boolean enabled() {
        Handler current = handler;
        if (current == null) return false;
        try {
            return current.enabled().getAsBoolean();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static String channel() {
        Handler current = handler;
        if (current == null) return "stable";
        try {
            String value = current.channel().get();
            return "preview".equalsIgnoreCase(value) ? "preview" : "stable";
        } catch (RuntimeException ignored) {
            return "stable";
        }
    }

    public static boolean granted() {
        Handler current = handler;
        if (current == null) return false;
        try {
            return current.granted().getAsBoolean();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static boolean setEnabled(boolean value) {
        Handler current = handler;
        if (current == null) return false;
        try {
            current.setEnabled().accept(value);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static boolean setChannel(String channel) {
        Handler current = handler;
        if (current == null) return false;
        String normalized = "preview".equalsIgnoreCase(channel) ? "preview" : "stable";
        try {
            current.setChannel().accept(normalized);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static boolean revokeAll() {
        Handler current = handler;
        if (current == null) return false;
        try {
            current.revokeAll().run();
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
