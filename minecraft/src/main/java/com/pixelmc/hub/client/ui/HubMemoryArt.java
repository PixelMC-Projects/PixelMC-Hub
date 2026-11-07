/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.ui;

import com.pixelmc.hub.client.render.HubDraw;
import io.github.humbleui.skija.BlendMode;
import io.github.humbleui.skija.Data;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.skija.svg.SVGDOM;
import io.github.humbleui.types.Rect;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Original Web SVG paths and gradients, recolored using the exact cover palette. */
public final class HubMemoryArt implements AutoCloseable {
    private record Key(String pattern, int light, int mid, int deep, int highlight) {}
    private final Map<Key, SVGDOM> scenes = new LinkedHashMap<>(32, .75f, true);
    private final Map<String, String> templates = new LinkedHashMap<>();

    public void draw(HubDraw draw, float x, float y, float width, float height,
                     String pattern, String color, boolean compact, boolean dark) {
        int[] palette = palette(color, dark);
        int base = palette[0], soft = palette[1], deep = palette[2];
        int highlight = dark ? 0xffdce3c6 : 0xfffff2d7;
        draw.linearGradientRoundRect(x, y, width, height, 0, 115,
            new int[]{soft, base}, new float[]{0, 1});
        draw.radialGradientRoundRect(x, y, width, height, 0, x + width * .82f, y + height * .04f,
            width * .6f, height * .6f, new int[]{dark ? 0x09cbd5f0 : 0x5cffffff, 0}, new float[]{0, 1});
        draw.save();
        draw.clipRoundRect(x, y, width, height, 0);
        draw.canvas().saveLayerAlpha(Rect.makeXYWH(x, y, width, height), dark ? 214 : 255);
        float artHeight = height * (compact ? 1.12f : 1);
        float artWidth = artHeight * 2;
        float artX = x + width - artWidth + 3;
        float artY = y - (compact ? height * .06f : 0);
        int mid = mix(base, deep, .58f);
        draw.linearGradientRoundRect(x + width * (compact ? 0 : .24f), y + height * .77f,
            width * (compact ? 1 : .76f), height * .23f, 0, 90,
            new int[]{0, withAlpha(mid, .2f)}, new float[]{0, 1});
        draw.canvas().saveLayer(Rect.makeXYWH(x, y, width, height), null);
        drawArtwork(draw, artX, artY, artWidth, artHeight, pattern, base, soft, deep, highlight, compact);
        mask(draw, artX, y, artWidth, height, 0, .28f);
        draw.canvas().restore();
        mask(draw, x, y, width, height, compact ? 0 : .18f, compact ? .48f : .57f);
        draw.canvas().restore();
        draw.restore();
    }

    /** Draws just the 480 by 240 illustration. Caller owns clipping, profile masks and opacity. */
    public void drawArtwork(HubDraw draw, float x, float y, float width, float height,
                            String pattern, int base, int soft, int deep, int highlight, boolean compact) {
        drawArtworkColors(draw, x, y, width, height, pattern, mix(soft, deep, .18f),
            mix(base, deep, .58f), deep, highlight);
    }

    public void drawArtworkColors(HubDraw draw, float x, float y, float width, float height,
                                  String pattern, int light, int mid, int deep, int highlight) {
        String safePattern = switch (pattern == null ? "" : pattern) {
            case "ridges", "grid", "stars" -> pattern;
            default -> "arcs";
        };
        Key key = new Key(safePattern, light, mid, deep, highlight);
        SVGDOM svg = scenes.get(key);
        if (svg == null) {
            String xml = template(safePattern)
                .replace("@LIGHT@", rgb(light))
                .replace("@MID@", rgb(mid))
                .replace("@DEEP@", rgb(deep)).replace("@HIGHLIGHT@", rgb(highlight));
            try (Data bytes = Data.makeFromBytes(xml.getBytes(StandardCharsets.UTF_8))) {
                svg = new SVGDOM(bytes);
            }
            scenes.put(key, svg);
            while (scenes.size() > 64) {
                Key eldest = scenes.keySet().iterator().next();
                scenes.remove(eldest).close();
            }
        }
        draw.save();
        draw.translate(x, y);
        svg.setContainerSize(width, height).render(draw.canvas());
        draw.restore();
    }

    private String template(String pattern) {
        return templates.computeIfAbsent(pattern, key -> {
            try (InputStream stream = HubMemoryArt.class.getResourceAsStream("/assets/pixelmc_hub/community-art/" + key + ".svg")) {
                if (stream == null) throw new IllegalStateException("Missing community artwork " + key);
                return circleEllipses(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException error) { throw new IllegalStateException("Cannot read community artwork", error); }
        });
    }

    private static String circleEllipses(String xml) {
        // Skia's SVG ellipse path drops object-bounding-box radial shaders and paints black.
        // A unit circle with the equivalent transform keeps both geometry and gradient stops.
        var ellipses = java.util.regex.Pattern.compile("<ellipse cx=\"([^\"]+)\" cy=\"([^\"]+)\" rx=\"([^\"]+)\" ry=\"([^\"]+)\"(?: transform=\"([^\"]+)\")?([^>]*)/>").matcher(xml);
        StringBuffer result = new StringBuffer();
        while (ellipses.find()) {
            String transform = ellipses.group(5) == null ? "" : ellipses.group(5) + " ";
            String circle = "<circle cx=\"0\" cy=\"0\" r=\"1\" transform=\"" + transform + "translate(" + ellipses.group(1) + " " + ellipses.group(2)
                + ") scale(" + ellipses.group(3) + " " + ellipses.group(4) + ")\"" + ellipses.group(6) + "/>";
            ellipses.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(circle));
        }
        ellipses.appendTail(result); return result.toString();
    }

    private static void mask(HubDraw draw, float x, float y, float width, float height, float start, float end) {
        try (Shader shader = Shader.makeLinearGradient(x, y, x + width, y,
                new int[]{0x00ffffff, 0x00ffffff, 0xffffffff, 0xffffffff}, new float[]{0, start, end, 1});
             Paint paint = new Paint().setAntiAlias(true).setBlendMode(BlendMode.DST_IN).setShader(shader)) {
            draw.canvas().drawRect(Rect.makeXYWH(x, y, width, height), paint);
        }
    }

    public static int[] palette(String color, boolean dark) {
        if (dark) return switch (color == null ? "" : color) {
            case "sage" -> new int[]{0xff3b514a, 0xff303f40, 0xff7b9d85};
            case "sand" -> new int[]{0xff554c3e, 0xff403d3f, 0xffb29c78};
            case "lilac" -> new int[]{0xff4e435f, 0xff39364c, 0xffa28fbc};
            case "tide" -> new int[]{0xff365352, 0xff303e46, 0xff79a7a3};
            case "rose" -> new int[]{0xff57434e, 0xff413743, 0xffb38d9c};
            default -> new int[]{0xff3b4b64, 0xff303b50, 0xff829dbd};
        };
        return switch (color == null ? "" : color) {
            case "sage" -> new int[]{0xffd3e2d8, 0xffedf3ee, 0xff789c88};
            case "sand" -> new int[]{0xffe7dac4, 0xfff5f0e5, 0xffad926c};
            case "lilac" -> new int[]{0xffddd4eb, 0xfff2edf7, 0xff9883b4};
            case "tide" -> new int[]{0xffc9e1df, 0xffe9f3f0, 0xff719e9b};
            case "rose" -> new int[]{0xffead3d8, 0xfff6ecee, 0xffb98d9b};
            default -> new int[]{0xffccdbea, 0xffedf1f7, 0xff7b98b5};
        };
    }

    private static int mix(int a, int b, float amount) {
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8)
            result |= Math.round(((a >>> shift) & 255) * (1 - amount) + ((b >>> shift) & 255) * amount) << shift;
        return result;
    }
    private static int withAlpha(int color, float alpha) { return (Math.round(alpha * (color >>> 24)) << 24) | (color & 0xffffff); }
    private static String rgb(int color) { return String.format(java.util.Locale.ROOT, "#%06x", color & 0xffffff); }
    @Override public void close() { scenes.values().forEach(SVGDOM::close); scenes.clear(); }
}
