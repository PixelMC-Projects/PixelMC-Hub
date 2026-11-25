/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.ui;

import com.pixelmc.hub.client.render.HubDraw;
import com.pixelmc.hub.client.render.WinterPaper;

import io.github.humbleui.skija.BlendMode;
import io.github.humbleui.skija.FilterTileMode;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.PathEffect;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;

/** Drawing primitives shared by the winter collection pages. */
final class HubWinterSheet {
    private static final String ART = "images/winter/";
    private static final String ROSETTE =
            "M50 0L62 7 75 6 82 17 94 23 94 37 100 50 94 63 94 77 82 83 75 94 62 93 50 100 "
                    + "38 93 25 94 18 83 6 77 6 63 0 50 6 37 6 23 18 17 25 6 38 7Z";
    private static final String SHIELD =
            "M50 0L94 13 96 47 87 70 68 87 50 100 32 87 13 70 4 47 6 13Z";
    private static final String STAR =
            "M50 0L66 10 83 12 88 29 100 50 88 71 83 88 66 90 50 100 34 90 17 88 12 71 "
                    + "0 50 12 29 17 12 34 10Z";
    private static final String RIBBON = "M0 0H100V100L48 80 0 100Z";

    private HubWinterSheet() {}

    static int mix(int from, int to, float amount) {
        int color = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int first = from >>> shift & 255, last = to >>> shift & 255;
            color |= Math.round(first + (last - first) * amount) << shift;
        }
        return color;
    }

    static int alpha(int color, float amount) {
        return Math.round((color >>> 24) * amount) << 24 | color & 0x00ffffff;
    }

    static void illustration(
            HubDraw d, String name, float x, float y, float w, float h, float opacity) {
        String resource = ART + name + ".png";
        var image = d.imageResource(resource);
        float scale = Math.min(w / image.getWidth(), h / image.getHeight());
        float imageW = image.getWidth() * scale, imageH = image.getHeight() * scale;
        d.image(resource, x + (w - imageW) / 2, y + (h - imageH) / 2, imageW, imageH, opacity);
    }

    static void grain(HubDraw d, float x, float y, float w, float h, float r, float opacity) {
        d.save();
        d.translate(x, y);
        try (Shader fibre =
                        d.imageResource(ART + "paper-fibre.png")
                                .makeShader(FilterTileMode.REPEAT, FilterTileMode.REPEAT);
                Paint paint = new Paint().setShader(fibre).setAlphaf(opacity).setAntiAlias(true)) {
            d.canvas().drawRRect(RRect.makeXYWH(0, 0, w, h, r), paint);
        } finally {
            d.restore();
        }
    }

    static void panel(
            HubDraw d, float x, float y, float w, float h, float r, int fill, float opacity) {
        boolean previous = d.literalColors(true);
        d.roundRect(x, y, w, h, r, alpha(fill, opacity));
        d.strokeRoundRect(
                x + .5f, y + .5f, w - 1, h - 1, r, 1, alpha(d.winterPaper().ruling(), opacity));
        d.literalColors(previous);
    }

    static void dashedLine(HubDraw d, float x, float y, float w, int color) {
        try (PathEffect effect = PathEffect.makeDash(new float[] {3, 2}, 0);
                Paint paint = new Paint().setColor(color).setStrokeWidth(1).setPathEffect(effect)) {
            d.canvas().drawLine(x, y, x + w, y, paint);
        }
    }

    static void stitchedCircle(HubDraw d, float cx, float cy, float r, int color, boolean dashed) {
        try (PathEffect effect = dashed ? PathEffect.makeDash(new float[] {3, 2}, 0) : null;
                Paint paint =
                        new Paint()
                                .setAntiAlias(true)
                                .setColor(color)
                                .setMode(PaintMode.STROKE)
                                .setStrokeWidth(1)
                                .setPathEffect(effect)) {
            d.canvas().drawCircle(cx, cy, r, paint);
        }
    }

    static void letter(
            HubDraw d, float x, float y, float w, float h, boolean stitched, float opacity) {
        WinterPaper paper = d.winterPaper();
        boolean previous = d.literalColors(true);
        d.roundRect(x + 3, y + 4, w, h, 5, alpha(paper.ruling(), opacity));
        d.roundRect(x + 2, y + 3, w, h, 5, alpha(paper.sheet(), opacity));
        d.roundRect(x, y, w, h, 5, alpha(paper.soft(), opacity));
        grain(d, x, y, w, h, 5, opacity);
        d.strokeRoundRect(x + .5f, y + .5f, w - 1, h - 1, 5, 1, alpha(paper.stitch(), opacity));
        if (stitched) {
            try (PathEffect effect = PathEffect.makeDash(new float[] {3, 2}, 0);
                    Paint paint =
                            new Paint()
                                    .setAntiAlias(true)
                                    .setColor(alpha(paper.stitch(), opacity))
                                    .setMode(PaintMode.STROKE)
                                    .setStrokeWidth(1)
                                    .setPathEffect(effect)) {
                d.canvas().drawRRect(RRect.makeXYWH(x + 6, y + 6, w - 12, h - 12, 2), paint);
            }
        }
        d.literalColors(previous);
    }

    static void snowCover(HubDraw d, float x, float y, float w, float h, float r, float opacity) {
        WinterPaper paper = d.winterPaper();
        boolean previous = d.literalColors(true);
        d.linearGradientRoundRect(
                x,
                y,
                w,
                h,
                r,
                130,
                new int[] {alpha(paper.sheet(), opacity), alpha(paper.cover(), opacity)},
                new float[] {0, 1});
        int shadow = d.dark() ? 0xff69858b : 0xffc1d7d4;
        int snow = d.dark() ? 0xffc5d9df : 0xfff8ffff;
        d.save();
        d.clipRoundRect(x, y, w, h, r);
        d.oval(
                x + w * .18f,
                y + h * .90f,
                w * 1.2f,
                h * .68f,
                alpha(mix(paper.cover(), shadow, .48f), opacity));
        d.oval(
                x + w * .51f,
                y + h * .86f,
                w * 1.04f,
                h * .60f,
                alpha(mix(paper.sheet(), snow, .72f), opacity));
        d.restore();
        d.literalColors(previous);
    }

    static void letterEdge(HubDraw d, float x, float y, float w) {
        WinterPaper paper = d.winterPaper();
        // The repeating stops are measured along the 130-degree stripe, as on the web cover.
        float rad = (float) Math.toRadians(130 - 90);
        float dx = (float) Math.cos(rad) * 24, dy = (float) Math.sin(rad) * 24;
        try (Shader shader =
                        Shader.makeLinearGradient(
                                x,
                                y,
                                x + dx,
                                y + dy,
                                new int[] {
                                    paper.wool(), paper.wool(), paper.sheet(), paper.sheet(),
                                    paper.mail(), paper.mail(), paper.sheet(), paper.sheet()
                                },
                                new float[] {0, .25f, .25f, .5f, .5f, .75f, .75f, 1},
                                new io.github.humbleui.skija.GradientStyle(
                                        FilterTileMode.REPEAT, false, null));
                Paint paint = new Paint().setShader(shader)) {
            d.canvas().drawRect(Rect.makeXYWH(x, y, w, 3), paint);
        }
    }

    static void selectedSpine(HubDraw d, float x, float y, float w, float h, float r, int color) {
        d.save();
        d.clipRoundRect(x, y, w, h, r);
        d.rect(x, y, 3, h, color);
        d.restore();
    }

    static void titleTile(
            HubDraw d,
            HubData.Title title,
            float x,
            float y,
            float w,
            float h,
            boolean compact,
            boolean selected,
            float opacity) {
        WinterPaper paper = d.winterPaper();
        boolean previous = d.literalColors(true);
        if (!selected) d.boxShadow(x, y, w, h, 12, 0, 3, 10, -7, alpha(0x4815261e, opacity));
        d.linearGradientRoundRect(
                x,
                y,
                w,
                h,
                12,
                105,
                new int[] {
                    alpha(paper.sheet(), opacity),
                    alpha(mix(paper.sheet(), title.color(), .14f), opacity)
                },
                new float[] {.45f, 1});
        grain(d, x, y, w, h, 12, opacity);
        if (!compact) {
            String print = ART + "frost-print-" + (d.dark() ? "dark" : "light") + ".png";
            var image = d.imageResource(print);
            float imageH = h * .63f, imageW = imageH * image.getWidth() / image.getHeight();
            float left = x + w - 4 - imageW;
            d.saveLayerAlpha(left, y + 1, imageW, imageH, .25f * opacity);
            d.image(print, left, y + 1, imageW, imageH, 1);
            try (Paint tint =
                    new Paint()
                            .setColor(mix(paper.wool(), title.color(), .30f))
                            .setBlendMode(BlendMode.SRC_IN)) {
                d.canvas().drawRect(Rect.makeXYWH(left, y + 1, imageW, imageH), tint);
            }
            d.restore();
        }
        int edge = alpha(selected ? paper.pine() : paper.ruling(), opacity);
        if (selected) selectedSpine(d, x, y, w, h, 12, edge);
        d.strokeRoundRect(x + .5f, y + .5f, w - 1, h - 1, 12, 1, edge);
        d.literalColors(previous);
    }

    static void emblem(
            HubDraw d,
            HubData.Title title,
            float x,
            float y,
            float size,
            boolean compact,
            float opacity) {
        String shape =
                switch (title.icon()) {
                    case "house", "mountain", "cloud" -> SHIELD;
                    case "star", "moon", "sun" -> STAR;
                    default -> ROSETTE;
                };
        int tile = title.color() | 0xff000000;
        int metal = mix(0xff7c6949, tile, .47f);
        int light = mix(0xfffff1c9, tile, .22f);
        int top = mix(0xfffff3dc, tile, .34f);
        int ribbon = mix(0xff38504a, tile, .61f);
        int glyph = mix(0xff263c35, tile, .20f);
        if (!title.owned()) {
            metal = mutedMetal(metal);
            light = mutedMetal(light);
            top = mutedMetal(top);
            tile = mutedMetal(tile);
            glyph = mutedMetal(glyph);
        }
        d.save();
        d.translate(x + size / 2, y + size / 2);
        d.canvas().rotate(4);
        float ribbonW = size * .33f, ribbonH = size * (compact ? .50f : .61f);
        ribbon(d, -size * .35f, size * .12f, ribbonW, ribbonH, 15, ribbon, light, opacity);
        ribbon(d, size * .02f, size * .12f, ribbonW, ribbonH, -15, ribbon, light, opacity);
        face(
                d,
                shape,
                -size / 2,
                -size / 2,
                size,
                new int[] {light, metal, light, metal},
                new float[] {0, .45f, .73f, 1},
                opacity);
        face(
                d,
                shape,
                2 - size / 2,
                2 - size / 2,
                size - 4,
                new int[] {top, tile},
                new float[] {0, 1},
                opacity);
        float rim = size * .36f;
        if (!compact) stitchedCircle(d, 0, 0, rim + 1, alpha(light, opacity * .7f), false);
        stitchedCircle(d, 0, 0, rim, alpha(metal, opacity), !compact);
        boolean previous = d.literalColors(true);
        d.icon(
                "stroke-1.5/" + title.icon(),
                -size * .23f,
                -size * .23f,
                size * .46f,
                alpha(glyph, opacity));
        d.literalColors(previous);
        d.restore();
    }

    private static int mutedMetal(int color) {
        int gray =
                Math.round(
                        ((color >>> 16) & 255) * .2126f
                                + ((color >>> 8) & 255) * .7152f
                                + (color & 255) * .0722f);
        return mix(0xff000000 | gray << 16 | gray << 8 | gray, color, .35f);
    }

    private static void face(
            HubDraw d,
            String outline,
            float x,
            float y,
            float size,
            int[] colors,
            float[] stops,
            float opacity) {
        d.save();
        d.translate(x, y);
        d.canvas().scale(size / 100, size / 100);
        try (Path path = Path.makeFromSVGString(outline);
                Shader gradient = Shader.makeLinearGradient(9, -8, 91, 108, colors, stops);
                Paint paint =
                        new Paint().setShader(gradient).setAlphaf(opacity).setAntiAlias(true)) {
            d.canvas().drawPath(path, paint);
        }
        d.restore();
    }

    private static void ribbon(
            HubDraw d,
            float x,
            float y,
            float w,
            float h,
            float angle,
            int fill,
            int stitch,
            float opacity) {
        d.save();
        d.translate(x + w / 2, y);
        d.canvas().rotate(angle);
        d.translate(-w / 2, 0);
        d.canvas().scale(w / 100, h / 100);
        try (Path path = Path.makeFromSVGString(RIBBON);
                Paint paint = new Paint().setColor(alpha(fill, opacity)).setAntiAlias(true)) {
            d.canvas().drawPath(path, paint);
            d.canvas().clipPath(path, true);
            paint.setColor(alpha(stitch, opacity));
            d.canvas().drawRect(Rect.makeXYWH(15, 0, 5, 100), paint);
            d.canvas().drawRect(Rect.makeXYWH(80, 0, 5, 100), paint);
        }
        d.restore();
    }
}
