/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.render;

import io.github.humbleui.skija.*;
import com.mojang.logging.LogUtils;
import io.github.humbleui.types.Rect;
import io.github.humbleui.types.IRect;
import io.github.humbleui.types.RRect;
import java.util.LinkedHashMap;
import java.util.Map;

/** Renderer-owned, bounded GPU cache of the original Gaussian shadow geometry. */
final class HubShadowCache implements AutoCloseable {
    static final int BOX = 0, OVAL = 1, INSET_BOX = 2, INSET_OVAL = 3;
    private static final long MAX_PIXELS = 24_000_000;
    private final Map<Key, Entry> shadows = new LinkedHashMap<>(32, .75f, true);
    private final Map<Integer, ColorFilter> tints = new LinkedHashMap<>(16, .75f, true);
    private final HubCorners corners = new HubCorners();
    private final Paint paint = new Paint();
    private long pixels;
    private RuntimeEffect ringEffect;
    private boolean ringDisabled;

    boolean draw(
            Canvas canvas,
            Surface surface,
            int kind,
            float x,
            float y,
            float w,
            float h,
            float radius,
            float offsetX,
            float offsetY,
            float blur,
            float spread,
            int color) {
        if (surface == null) return false;
        // A hard shadow is just a geometric fill: caching it would waste a texture
        // without avoiding a Gaussian pass.
        if (blur <= 0) return false;
        float alpha = (color >>> 24) / 255f;
        if (alpha == 0) return true;
        // Broad Gaussian masks need fewer samples. Bucket the source density upwards
        // before reducing it, so subpixel entrance/tilt steps reuse the same texture.
        float deviceDensity =
                HubGlass.deviceScale(canvas.getLocalToDeviceAsMatrix33(), x + w * .5f, y + h * .5f);
        float physicalSigma = blur * .5f * deviceDensity;
        // Normal GUI densities fit the bounded cache and can retain their already
        // clipped image. Only high-density soft masks need reduced sampling.
        int reduction =
                deviceDensity <= 1.5f ? 1 : physicalSigma >= 16 ? 4 : physicalSigma >= 8 ? 2 : 1;
        float density = Math.max(.25f, (float) Math.ceil(deviceDensity * 4) / 4) / reduction;
        float pad =
                kind < INSET_BOX
                        ? (float)
                                Math.ceil(
                                        Math.max(
                                                2,
                                                Math.max(0, blur) * 1.5f
                                                        + spread
                                                        + Math.max(
                                                                Math.abs(offsetX),
                                                                Math.abs(offsetY))
                                                        + 4))
                        : 2;
        int width = (int) Math.ceil((w + pad * 2) * density),
                height = (int) Math.ceil((h + pad * 2) * density);
        if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) return false;
        // A large high-density outer box can retain its sharp clip in a compact ring.
        // Small boxes and every other shape keep the baseline reduced-mask path.
        IRect hole = null;
        if (!ringDisabled && kind == BOX && reduction > 1) {
            float fullDensity = density * reduction;
            int fullWidth = (int) Math.ceil((w + pad * 2) * fullDensity);
            int fullHeight = (int) Math.ceil((h + pad * 2) * fullDensity);
            long fullPixels = (long) fullWidth * fullHeight;
            if (fullWidth > 0
                    && fullHeight > 0
                    && fullPixels >= 1_000_000
                    && fullPixels <= MAX_PIXELS) {
                hole = ringHole(fullWidth, fullHeight, pad, w, h, radius, fullDensity);
                if (hole != null) {
                    density = fullDensity;
                    width = fullWidth;
                    height = fullHeight;
                }
            }
        }
        boolean ring = hole != null;
        Key key =
                new Key(
                        kind,
                        w,
                        h,
                        radius,
                        offsetX,
                        offsetY,
                        blur,
                        spread,
                        density,
                        reduction > 1 && !ring,
                        ring);
        Entry cached = shadows.get(key);
        if (cached == null) {
            if (ring) {
                try {
                    cached =
                            renderRing(
                                    surface, hole, pad, w, h, radius, offsetX, offsetY, blur,
                                    spread, density, width, height);
                    if (cached == null)
                        throw new IllegalStateException(
                                "Shadow ring surface or shader unavailable");
                } catch (RuntimeException unavailable) {
                    // A failed optimization must not discard this shadow. Construction never
                    // touches the caller canvas, and renderRing resets paint and closes partial
                    // tiles.
                    // The lifetime gate bounds this retry to one baseline call and one warning.
                    ringDisabled = true;
                    clear();
                    LogUtils.getLogger()
                            .warn(
                                    "PixelMC-Hub ring shadow cache disabled for this renderer",
                                    unavailable);
                    return draw(
                            canvas, surface, kind, x, y, w, h, radius, offsetX, offsetY, blur,
                            spread, color);
                }
            } else {
                try (Surface raster = surface.makeSurface(width, height)) {
                    if (raster == null) return false;
                    Canvas c = raster.getCanvas();
                    c.clear(0);
                    c.scale(density, density);
                    c.translate(pad, pad);
                    if (reduction == 1) clip(c, kind, 0, 0, w, h, radius);
                    render(c, kind, w, h, radius, offsetX, offsetY, blur, spread, 0xffffffff);
                    Rect full = Rect.makeWH(width, height);
                    cached =
                            new Entry(
                                    new Tile[] {new Tile(raster.makeImageSnapshot(), full, full)},
                                    null,
                                    pad,
                                    width / density,
                                    height / density,
                                    width,
                                    height);
                }
            }
            if (cached == null) return false;
            shadows.put(key, cached);
            pixels += cached.pixels();
            var iterator = shadows.entrySet().iterator();
            while ((shadows.size() > 96 || pixels > MAX_PIXELS)
                    && shadows.size() > 1
                    && iterator.hasNext()) {
                Entry old = iterator.next().getValue();
                pixels -= old.pixels();
                iterator.remove();
                old.close();
            }
        }
        int saved = canvas.save();
        try {
            // Keep the sharp border at the original device density. Only the smooth
            // Gaussian mask is cached at reduced density; its sigma stays unchanged.
            if (reduction > 1 && !ring) clip(canvas, kind, x, y, w, h, radius);
            Rect destination =
                    Rect.makeXYWH(x - cached.pad, y - cached.pad, cached.width, cached.height);
            paint.reset().setAlphaf(alpha).setColorFilter(tint(color | 0xff000000));
            if (ring) {
                // All children sample in the original full-mask pixel coordinates.
                // One draw owns every fragment, including transformed partition boundaries.
                canvas.translate(destination.getLeft(), destination.getTop());
                canvas.scale(
                        destination.getWidth() / cached.pixelWidth,
                        destination.getHeight() / cached.pixelHeight);
                canvas.drawRect(
                        Rect.makeWH(cached.pixelWidth, cached.pixelHeight),
                        paint.setShader(cached.shader));
            } else {
                Tile tile = cached.tiles[0];
                canvas.drawImageRect(
                        tile.image, tile.source, destination, SamplingMode.LINEAR, paint, false);
            }
        } finally {
            paint.reset();
            canvas.restoreToCount(saved);
        }
        return true;
    }

    private IRect ringHole(
            int width, int height, float pad, float w, float h, float radius, float density) {
        float r = Math.max(0, Math.min(radius, Math.min(w, h) * .5f));
        // Round inward and keep another two physical pixels beyond the AA edge.
        int left = (int) Math.ceil((pad + r) * density + 2);
        int top = (int) Math.ceil((pad + r) * density + 2);
        int right = (int) Math.floor((pad + w - r) * density - 2);
        int bottom = (int) Math.floor((pad + h - r) * density - 2);
        if (left <= 0
                || top <= 0
                || right >= width
                || bottom >= height
                || right <= left
                || bottom <= top) return null;
        // This bound also covers all copied one-pixel gutters. Never grow an entry by splitting it.
        if ((long) (right - left) * (bottom - top) <= 4L * width + 2L * height + 4) return null;
        return IRect.makeLTRB(left, top, right, bottom);
    }

    private Entry renderRing(
            Surface surface,
            IRect hole,
            float pad,
            float w,
            float h,
            float radius,
            float offsetX,
            float offsetY,
            float blur,
            float spread,
            float density,
            int width,
            int height) {
        IRect[] cores = {
            IRect.makeLTRB(0, 0, width, hole.getTop()),
            IRect.makeLTRB(0, hole.getBottom(), width, height),
            IRect.makeLTRB(0, hole.getTop(), hole.getLeft(), hole.getBottom()),
            IRect.makeLTRB(hole.getRight(), hole.getTop(), width, hole.getBottom())
        };
        Tile[] tiles = new Tile[cores.length];
        boolean complete = false;
        Shader shader = null;
        // Render the original geometry directly into independent small targets. Integer
        // physical-pixel offsets preserve the full mask's raster phase without a full-size target.
        try {
            for (int i = 0; i < cores.length; i++) {
                IRect core = cores[i];
                IRect copy =
                        IRect.makeLTRB(
                                Math.max(0, core.getLeft() - 1),
                                Math.max(0, core.getTop() - 1),
                                Math.min(width, core.getRight() + 1),
                                Math.min(height, core.getBottom() + 1));
                try (Surface strip = surface.makeSurface(copy.getWidth(), copy.getHeight())) {
                    if (strip == null) return null;
                    Canvas c = strip.getCanvas();
                    c.clear(0);
                    c.translate(-copy.getLeft(), -copy.getTop());
                    c.scale(density, density);
                    c.translate(pad, pad);
                    clip(c, BOX, 0, 0, w, h, radius);
                    render(c, BOX, w, h, radius, offsetX, offsetY, blur, spread, 0xffffffff);
                    Rect source = core.offset(-copy.getLeft(), -copy.getTop()).toRect();
                    Image image = strip.makeImageSnapshot();
                    if (image == null) return null;
                    tiles[i] = new Tile(image, source, core.toRect());
                }
            }
            shader = ringShader(tiles, hole);
            if (shader == null) return null;
            Entry result =
                    new Entry(tiles, shader, pad, width / density, height / density, width, height);
            complete = true;
            return result;
        } finally {
            paint.reset();
            if (!complete) {
                if (shader != null) shader.close();
                for (Tile tile : tiles) if (tile != null) tile.image.close();
            }
        }
    }

    private Shader ringShader(Tile[] tiles, IRect hole) {
        if (ringEffect == null)
            ringEffect =
                    RuntimeEffect.makeForShader(
                            """
            uniform shader topStrip;
            uniform shader bottomStrip;
            uniform shader leftStrip;
            uniform shader rightStrip;
            uniform float4 hole;
            half4 main(float2 p) {
                if(p.y<hole.y)return topStrip.eval(p);
                if(p.y>=hole.w)return bottomStrip.eval(p);
                if(p.x<hole.x)return leftStrip.eval(p);
                if(p.x>=hole.z)return rightStrip.eval(p);
                return half4(0);
            }
            """);
        try (Shader top = stripShader(tiles[0]);
                Shader bottom = stripShader(tiles[1]);
                Shader left = stripShader(tiles[2]);
                Shader right = stripShader(tiles[3]);
                RuntimeEffectBuilder builder = new RuntimeEffectBuilder(ringEffect)) {
            builder.setChild("topStrip", top);
            builder.setChild("bottomStrip", bottom);
            builder.setChild("leftStrip", left);
            builder.setChild("rightStrip", right);
            builder.setUniform(
                    "hole",
                    (float) hole.getLeft(),
                    (float) hole.getTop(),
                    (float) hole.getRight(),
                    (float) hole.getBottom());
            return builder.makeShader();
        }
    }

    private Shader stripShader(Tile tile) {
        return tile.image.makeShader(
                FilterTileMode.CLAMP,
                FilterTileMode.CLAMP,
                SamplingMode.LINEAR,
                Matrix33.makeTranslate(
                        tile.destination.getLeft() - tile.source.getLeft(),
                        tile.destination.getTop() - tile.source.getTop()));
    }

    // A shadow's geometry is independent of its palette. Reuse the same opaque
    // white mask across light/dark themes, then tint without rebuilding Gaussian data.
    private ColorFilter tint(int color) {
        ColorFilter result = tints.get(color);
        if (result != null) return result;
        result = ColorFilter.makeBlend(color, BlendMode.SRC_IN);
        tints.put(color, result);
        if (tints.size() > 64) {
            var iterator = tints.entrySet().iterator();
            iterator.next().getValue().close();
            iterator.remove();
        }
        return result;
    }

    private void clip(Canvas canvas, int kind, float x, float y, float w, float h, float radius) {
        if (kind == OVAL)
            canvas.clipRRect(
                    RRect.makeXYWH(x, y, w, h, w * .5f, h * .5f), ClipMode.DIFFERENCE, true);
        else if (kind == BOX) corners.clip(canvas, x, y, w, h, radius, ClipMode.DIFFERENCE);
        else if (kind == INSET_OVAL)
            canvas.clipRRect(RRect.makeXYWH(x, y, w, h, w * .5f, h * .5f), true);
        else corners.clip(canvas, x, y, w, h, radius);
    }

    private void render(
            Canvas c,
            int kind,
            float w,
            float h,
            float radius,
            float offsetX,
            float offsetY,
            float blur,
            float spread,
            int color) {
        paint.reset().setAntiAlias(true);
        if (kind < INSET_BOX) {
            try (ImageFilter filter =
                    ImageFilter.makeDropShadowOnly(
                            offsetX,
                            offsetY,
                            Math.max(0, blur) * .5f,
                            Math.max(0, blur) * .5f,
                            color)) {
                paint.setColor(0xffffffff).setImageFilter(filter);
                if (kind == OVAL) c.drawOval(Rect.makeXYWH(0, 0, w, h), paint);
                else
                    corners.draw(
                            c,
                            -spread,
                            -spread,
                            w + spread * 2,
                            h + spread * 2,
                            Math.max(0, radius + spread),
                            paint,
                            radius >= 16);
            } finally {
                paint.reset();
            }
            return;
        }
        float pad =
                Math.max(
                        16,
                        blur * 3
                                + Math.abs(offsetX)
                                + Math.abs(offsetY)
                                + (kind == INSET_BOX ? Math.abs(spread) : 0));
        try (PathBuilder builder = new PathBuilder(PathFillMode.EVEN_ODD);
                MaskFilter filter =
                        blur > 0 ? MaskFilter.makeBlur(FilterBlurMode.NORMAL, blur * .5f) : null) {
            builder.addRect(Rect.makeXYWH(-pad, -pad, w + pad * 2, h + pad * 2));
            if (kind == INSET_OVAL) builder.addOval(Rect.makeXYWH(offsetX, offsetY, w, h));
            else
                corners.append(
                        builder,
                        spread + offsetX,
                        spread + offsetY,
                        Math.max(0, w - spread * 2),
                        Math.max(0, h - spread * 2),
                        Math.max(0, radius - spread),
                        radius >= 16);
            try (Path path = builder.build()) {
                c.drawPath(path, paint.setColor(color).setMaskFilter(filter));
            }
        } finally {
            paint.reset();
        }
    }

    void clear() {
        shadows.values().forEach(Entry::close);
        shadows.clear();
        pixels = 0;
        tints.values().forEach(ColorFilter::close);
        tints.clear();
        if (ringEffect != null) {
            ringEffect.close();
            ringEffect = null;
        }
    }

    @Override
    public void close() {
        clear();
        corners.close();
        paint.close();
    }

    private record Key(
            int kind,
            float width,
            float height,
            float radius,
            float offsetX,
            float offsetY,
            float blur,
            float spread,
            float density,
            boolean reduced,
            boolean ring) {}

    private record Tile(Image image, Rect source, Rect destination) {}

    private record Entry(
            Tile[] tiles,
            Shader shader,
            float pad,
            float width,
            float height,
            int pixelWidth,
            int pixelHeight) {
        long pixels() {
            long count = 0;
            for (Tile tile : tiles) count += (long) tile.image.getWidth() * tile.image.getHeight();
            return count;
        }

        void close() {
            if (shader != null) shader.close();
            for (Tile tile : tiles) tile.image.close();
        }
    }
}
