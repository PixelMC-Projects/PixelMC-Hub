/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.render;

import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Image;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Decode the immutable winter page illustrations without borrowing Minecraft's GL context. */
final class WinterRouteArt implements AutoCloseable {
    static final List<String> ROUTES = List.of(
            "workspace-light", "directory-light", "panorama-light", "stationery-light", "company-light", "bulletin-light",
            "title-case-day", "title-pins", "title-etchings", "title-brooch", "fortune-counter", "journal-spread",
            "calendar-woodcut", "journal-slip", "fortune-tailpiece", "gazette-press", "summit-day", "summit-traveler",
            "workspace-dark", "directory-dark", "panorama-dark", "stationery-dark", "company-dark", "bulletin-dark",
            "title-case-night", "fortune-counter-night", "summit-night")
            .stream().map(stem -> "images/winter/" + stem + ".png").toList();

    private final Set<String> pending = new HashSet<>(ROUTES);
    private final Map<String, Image> ready = new HashMap<>();
    private boolean closed;

    WinterRouteArt() {
        Thread worker = new Thread(this::decode, "PixelMC winter artwork");
        worker.setDaemon(true);
        worker.setPriority(Thread.NORM_PRIORITY - 1);
        worker.start();
    }

    private void decode() {
        for (String name : ROUTES) {
            synchronized (this) {
                if (closed) return;
                if (!pending.contains(name)) continue;
            }
            Image raster = null;
            try (Image encoded = HubDraw.bundledImage(name); Bitmap bitmap = Bitmap.makeFromImage(encoded)) {
                bitmap.setImmutable();
                raster = Image.makeRasterFromBitmap(bitmap);
            } catch (RuntimeException failure) {
                com.mojang.logging.LogUtils.getLogger().debug("Deferred winter image preparation: {}", name, failure);
            }
            synchronized (this) {
                if (pending.remove(name) && raster != null) ready.put(name, raster);
                else if (raster != null) raster.close();
            }
        }
    }

    synchronized boolean ready(String name) {
        return ready.containsKey(name);
    }

    synchronized Image take(String name) {
        pending.remove(name);
        return ready.remove(name);
    }

    @Override
    public synchronized void close() {
        closed = true;
        pending.clear();
        ready.values().forEach(Image::close);
        ready.clear();
    }
}
