/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.ui;

import com.pixelmc.hub.client.render.HubDraw;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;

/** The two intersecting CSS masks apply only to the landscape, underneath the players. */
final class SummitStage {
    private static Typeface numberFace;
    private static Paint numberInk;
    private static final java.util.Map<Float, Font> numberFonts = new java.util.HashMap<>();

    static Font numberFont(float size) {
        if (numberFace == null) {
            FontMgr mgr = FontMgr.getDefault();
            numberFace = mgr.matchFamilyStyle("Georgia", FontStyle.BOLD);
            if (numberFace == null) numberFace = mgr.matchFamilyStyle("serif", FontStyle.BOLD);
        }
        return numberFonts.computeIfAbsent(
                size,
                s ->
                        new Font(numberFace, s)
                                .setEdging(FontEdging.ANTI_ALIAS)
                                .setSubpixel(true)
                                .setBaselineSnapped(false));
    }

    // Compact Chinese units can follow the Latin digits. The page's normal CJK face covers those.
    static float number(
            HubDraw draw,
            String value,
            float size,
            int weight,
            float x,
            float y,
            int color,
            boolean render) {
        Font font = numberFont(size);
        int end = 0;
        while (end < value.length() && value.charAt(end) < 128) end++;
        String digits = value.substring(0, end), suffix = value.substring(end);
        float width = font.measureTextWidth(digits);
        if (render) {
            if (numberInk == null) numberInk = new Paint().setAntiAlias(true);
            draw.canvas().drawString(digits, x, y, font, numberInk.setColor(color));
            if (!suffix.isEmpty()) draw.textSerif(suffix, x + width, y, size, color, weight);
        }
        return suffix.isEmpty() ? width : width + draw.textWidthSerif(suffix, size, weight);
    }

    static void clear() {
        numberFonts.values().forEach(Font::close);
        numberFonts.clear();
        if (numberFace != null) {
            numberFace.close();
            numberFace = null;
        }
        if (numberInk != null) {
            numberInk.close();
            numberInk = null;
        }
    }

    private static final int[] EDGE = {0, 0xaa000000, 0xff000000, 0xff000000, 0xaa000000, 0};
    private static final float[] EDGE_STOPS = {0, .03f, .08f, .92f, .97f, 1};
    private static final int[] SNOW = {
        0, 0xaa000000, 0xff000000, 0xff000000, 0xcc000000, 0x44000000, 0
    };
    private static final float[] SNOW_STOPS = {0, .05f, .12f, .47f, .63f, .77f, .87f};

    private SummitStage() {}

    static void landscape(
            HubDraw draw, float x, float y, float width, float height, boolean state) {
        Image art =
                draw.imageResource(
                        "images/winter/summit-" + (draw.dark() ? "night" : "day") + ".png");
        Rect bounds = Rect.makeXYWH(x, y, width, height);
        float scale =
                state
                        ? width / art.getWidth()
                        : Math.max(width / art.getWidth(), height / art.getHeight());
        float w = art.getWidth() * scale, h = art.getHeight() * scale;
        Rect dest =
                Rect.makeXYWH(x + (width - w) * .5f, y + (height - h) * (state ? 1 : .38f), w, h);
        Canvas canvas = draw.canvas();
        canvas.saveLayer(bounds, null);
        try (Paint paint = new Paint();
                Shader vertical =
                        state
                                ? Shader.makeLinearGradient(
                                        x,
                                        y,
                                        x,
                                        y + height,
                                        new int[] {0, 0, 0xff000000},
                                        new float[] {0, .48f, 1})
                                : Shader.makeLinearGradient(
                                        x, y, x, y + height, SNOW, SNOW_STOPS)) {
            canvas.clipRect(bounds);
            canvas.drawImageRect(
                    art,
                    Rect.makeWH(art.getWidth(), art.getHeight()),
                    dest,
                    SamplingMode.LINEAR,
                    paint.setAlphaf(state ? .24f : 1),
                    true);
            paint.setAlphaf(1).setBlendMode(BlendMode.DST_IN).setShader(vertical);
            canvas.drawRect(bounds, paint);
            if (!state) {
                try (Shader horizontal =
                        Shader.makeLinearGradient(x, y, x + width, y, EDGE, EDGE_STOPS)) {
                    canvas.drawRect(bounds, paint.setShader(horizontal));
                }
            }
        } finally {
            canvas.restore();
        }
    }

    static void shadow(HubDraw draw, float cx, float bottom, float unit) {
        draw.radialGradientRoundRect(
                cx - 11.5f * unit,
                bottom - 1.65f * unit,
                23 * unit,
                3 * unit,
                1.5f * unit,
                cx,
                bottom - .15f * unit,
                11.5f * unit * .72f,
                1.5f * unit * .72f,
                new int[] {0xa3243d54, 0x00243d54},
                new float[] {0, 1});
    }

    static void bookmark(HubDraw draw, float x, float top, float width, float height, int color) {
        try (PathBuilder shape = new PathBuilder();
                Paint ink = new Paint().setColor(color).setAntiAlias(true)) {
            shape.moveTo(x, top)
                    .lineTo(x + width, top)
                    .lineTo(x + width, top + height)
                    .lineTo(x + width / 2, top + height - 5)
                    .lineTo(x, top + height)
                    .closePath();
            try (Path path = shape.snapshot()) {
                draw.canvas().drawPath(path, ink);
            }
        }
    }
}
