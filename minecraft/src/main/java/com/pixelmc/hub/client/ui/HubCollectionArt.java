/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.ui;

import com.pixelmc.hub.client.render.HubDraw;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;
import java.util.List;

/** The engraved artwork used by the winter album, collected slips and public profiles. */
final class HubCollectionArt {
    private static final String ROOT = "images/winter/";
    private static final List<String> STYLES =
            List.of("dew", "star", "wood", "moon", "harvest", "mountain", "dawn", "cloud");
    private static final float[] DX = {-3.26f, -1.09f, 1.63f, 3.26f, -.82f, 1.09f, 1.90f, 2.45f};
    private static final float[] DY = {-1.09f, -.27f, -1.36f, -1.90f, .54f, 0, 1.36f, 0};
    private static final SamplingMode SAMPLING =
            new FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR);

    private HubCollectionArt() {}

    static String styleForBadge(String badge) {
        return switch (badge) {
            case "leaf" -> "dew";
            case "house" -> "wood";
            case "wheat" -> "harvest";
            case "sun" -> "dawn";
            default -> badge;
        };
    }

    static void pin(
            HubDraw d,
            String style,
            String fallbackIcon,
            float x,
            float y,
            float size,
            boolean locked,
            float opacity) {
        int cell = STYLES.indexOf(style);
        if (cell < 0) {
            d.circle(
                    x + size / 2,
                    y + size / 2,
                    size / 2,
                    HubWinterSheet.alpha(0xffedcf87, opacity));
            d.icon(
                    fallbackIcon == null || fallbackIcon.isBlank() ? "tag" : fallbackIcon,
                    x + size * .29f,
                    y + size * .29f,
                    size * .42f,
                    HubWinterSheet.alpha(0xff334f43, opacity));
            return;
        }
        x += size * DX[cell] / 100;
        y += size * DY[cell] / 100;
        try (ColorFilter color = locked ? shade(1, .45f) : null) {
            atlas(d, "title-pins", cell, x, y, size, size, opacity, color);
        }
    }

    static void etching(HubDraw d, String style, float x, float y, float w, float h, int tint) {
        int cell = STYLES.indexOf(style);
        try (ColorFilter color = ColorFilter.makeBlend(tint, BlendMode.SRC_IN)) {
            atlas(d, "title-etchings", cell < 0 ? 5 : cell, x, y, w, h, .69f, color);
        }
    }

    private static void atlas(
            HubDraw d,
            String name,
            int cell,
            float x,
            float y,
            float w,
            float h,
            float opacity,
            ColorFilter color) {
        var image = d.imageResource(ROOT + name + ".png");
        float sw = image.getWidth() / 4f, sh = image.getHeight() / 2f;
        try (ImageFilter shadow = name.equals("title-pins")
                        ? ImageFilter.makeDropShadow(0,2,.5f,.5f,0x8510271c) : null;
                Paint paint = new Paint().setAntiAlias(true).setAlphaf(opacity).setColorFilter(color).setImageFilter(shadow)) {
            d.canvas()
                    .drawImageRect(
                            image,
                            Rect.makeXYWH(cell % 4 * sw, cell / 4 * sh, sw, sh),
                            Rect.makeXYWH(x, y, w, h),
                            SAMPLING,
                            paint,
                            true);
        }
    }

    private static ColorFilter shade(float brightness, float saturation) {
        float r = .2126f * (1 - saturation) * brightness,
                g = .7152f * (1 - saturation) * brightness,
                b = .0722f * (1 - saturation) * brightness;
        float s = saturation * brightness;
        return ColorFilter.makeMatrix(
                new ColorMatrix(
                        r + s, g, b, 0, 0, r, g + s, b, 0, 0, r, g, b + s, 0, 0, 0, 0, 0, 1, 0));
    }

    static void filtered(
            HubDraw d,
            String name,
            float x,
            float y,
            float w,
            float h,
            float brightness,
            float saturation) {
        var img = d.imageResource(ROOT + name + ".png");
        boolean slip = name.equals("journal-slip");
        try (ColorFilter color = brightness == 1 && saturation == 1 ? null : shade(brightness, saturation);
                ImageFilter shadow = slip ? ImageFilter.makeDropShadow(1,3,1,1,d.dark()?0x80101719:0x60192325) : null;
                Paint paint = new Paint().setColorFilter(color).setImageFilter(shadow)) {
            d.canvas()
                    .drawImageRect(
                            img,
                            Rect.makeWH(img.getWidth(), img.getHeight()),
                            Rect.makeXYWH(x, y, w, h),
                            SAMPLING,
                            paint,
                            false);
        }
    }

    static void brooch(HubDraw d, float x, float y, float w, float h) {
        var img = d.imageResource(ROOT + "title-brooch.png");
        float scale = Math.min(w / img.getWidth(), h / img.getHeight());
        float dw = img.getWidth() * scale, dh = img.getHeight() * scale;
        int tint = HubWinterSheet.mix(d.winterPaper().pine(), d.winterPaper().mail(), .63f);
        try (var filter = ColorFilter.makeBlend(tint, BlendMode.SRC_IN);
                var paint = new Paint().setColorFilter(filter).setAlphaf(.72f)) {
            d.canvas()
                    .drawImageRect(
                            img,
                            Rect.makeWH(img.getWidth(), img.getHeight()),
                            Rect.makeXYWH(x + (w - dw) / 2, y + (h - dh) / 2, dw, dh),
                            SAMPLING,
                            paint,
                            false);
        }
    }

    static void desk(HubDraw d, float x, float y, float w, float h) {
        d.save();
        d.clipRoundRect(x, y, w, h, 0);
        d.image(
                ROOT + "journal-spread.png",
                x - w * .05f * .579f,
                y - h * .035f,
                w * 1.05f,
                h * 1.07f,
                1);
        if (d.dark()) {
            try (Paint paint = new Paint().setColor(0xff596563).setBlendMode(BlendMode.MULTIPLY)) {
                d.canvas().drawRect(Rect.makeXYWH(x, y, w, h), paint);
            }
        }
        d.restore();
    }

    static void cover(HubDraw d, boolean album, float x, float y, float w, float h) {
        String image =
                ROOT
                        + (album
                                ? "title-case-" + (d.dark() ? "night" : "day")
                                : "fortune-counter" + (d.dark() ? "-night" : ""))
                        + ".png";
        int base =
                album ? (d.dark() ? 0xff24392e : 0xffebe6c9) : (d.dark() ? 0xff303d35 : 0xffe5dfc7);
        d.rect(x, y, w, h, base);
        if (!album) HubWinterSheet.grain(d, x, y, w, h, 0, 1);
        var art = d.imageResource(image);
        float ratio = art.getWidth() / (float) art.getHeight();
        float dw = album ? Math.min(3 * h, w) : Math.max(w, h * ratio), dh = dw / ratio;
        float dx = album ? x + w - dw : x + (w - dw) / 2, dy = y + (h - dh) * (album ? .5f : .66f);
        d.save();
        d.clipRoundRect(x, y, w, h, 0);
        d.image(image, dx, dy, dw, dh, 1);
        if (album) {
            d.linearGradientRoundRect(
                    dx, y, dw, h, 0, 90, new int[] {base, base & 0xffffff}, new float[] {0, .27f});
            d.linearGradientRoundRect(
                    x,
                    y,
                    w,
                    h,
                    0,
                    90,
                    new int[] {base, HubWinterSheet.alpha(base, .84f), base & 0xffffff},
                    new float[] {0, .27f, .62f});
        } else {
            d.linearGradientRoundRect(
                    x,
                    y,
                    w,
                    h,
                    0,
                    90,
                    new int[] {base, base, base & 0xffffff},
                    new float[] {0, .10f, .49f});
        }
        d.restore();
    }
}
