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

/** ProfileBackdrop's original SVG paths, masks, palette arithmetic and CSS placement. */
public final class HubProfileArt implements AutoCloseable {
    public static final String[] COLORS = {"mist", "lilac", "sage", "sand", "blush", "apricot", "tide", "twilight", "frost", "stone"};
    public static final String[] COLOR_NAMES = {"雾蓝", "浅藤", "青苔", "麦穗", "蔷薇", "杏光", "浅汐", "暮云", "霜青", "雨石"};
    public static final String[] PATTERNS = {"classic", "doodle", "tech", "architecture", "nature", "cosmos"};
    public static final String[] PATTERN_NAMES = {"流光", "云间露营", "星际电路", "方块小城", "风过叶梢", "星河漫游"};
    private record Key(String asset, int light, int mid, int deep, int highlight) {}
    private final Map<Key, SVGDOM> documents = new LinkedHashMap<>(16, .75f, true);
    private final Map<String, String> templates = new LinkedHashMap<>();
    private final HubMemoryArt shared = new HubMemoryArt();

    public void cover(HubDraw draw, float x, float y, float width, float height,
                      String color, String pattern, boolean compact, boolean dark) {
        int[] colors = palette(color, dark);
        draw.linearGradientRoundRect(x, y, width, height, 0, 118,
            new int[]{colors[1], colors[0], colors[0]}, new float[]{0, .76f, 1});
        if (!compact) {
            draw.radialGradientRoundRect(x, y, width, height, 0, x + width * .73f, y + height * 1.05f,
                width * .599f, height * .861f, new int[]{(colors[1] & 0xffffff) | 0xcc000000, 0}, new float[]{0, 1});
            int glint = dark ? 0x24c9d3f0 : 0x8cffffff;
            draw.radialGradientRoundRect(x, y, width, height, 0, x + width * .59f, y + height * .71f,
                2, 2, new int[]{glint, glint, 0}, new float[]{0, .7f, 1});
            draw.radialGradientRoundRect(x, y, width, height, 0, x + width * .91f, y + height * .19f,
                2.6f, 2.6f, new int[]{glint, glint, 0}, new float[]{0, .77f, 1});
        }
        artwork(draw, x, y, width, height, color, pattern, compact, dark);
    }

    public void artwork(HubDraw draw, float x, float y, float width, float height,
                        String color, String pattern, boolean compact, boolean dark) {
        int[] colors = palette(color, dark);
        int deep = dark ? mix(colors[0], 0xffb4c8e1, .65f) : mix(colors[0], 0xff425b7b, .48f);
        int light = dark ? mix(colors[1], 0xff8eabc0, .3f) : mix(colors[1], 0xffffffff, .2f);
        int mid = mix(colors[0], deep, .4f), highlight = dark ? 0xffd8e2dc : 0xfffff7e8;
        String id = normalizePattern(pattern);
        boolean sharedPattern = id.equals("nature") || id.equals("architecture") || id.equals("cosmos");
        float artHeight = compact ? Math.min(height, width / 2) : height;
        float artWidth = artHeight * 2;
        float artX = compact ? x + (width - artWidth) / 2 : x + width - 48 - artWidth;
        float artY = compact ? y + (height - artHeight) / 2 : y;
        draw.save();
        draw.clipRoundRect(x, y, width, height, 0);
        draw.canvas().saveLayerAlpha(Rect.makeXYWH(x, y, width, height), compact ? 255 : 204);
        if (sharedPattern) {
            draw.canvas().saveLayer(Rect.makeXYWH(x, y, width, height), null);
            String sharedId = id.equals("nature") ? "arcs" : id.equals("architecture") ? "grid" : "stars";
            shared.drawArtworkColors(draw, artX, artY, artWidth, artHeight, sharedId, light, mid, deep, highlight);
            mask(draw, artX, y, artWidth, height, 0, .24f);
            draw.canvas().restore();
        } else {
            render(draw, id, artX, artY, artWidth, artHeight, light, mid, deep, highlight);
        }
        if (!compact) mask(draw, x, y, width, height, .12f, .61f);
        draw.canvas().restore();
        draw.restore();
    }

    public void icon(HubDraw draw, String name, float x, float y, float size, int color) {
        if (!name.equals("pencil") && !name.equals("feather")) {
            draw.icon(name, x, y, size, color);
            return;
        }
        render(draw, name, x, y, size, size, color, color, color, color);
    }

    private void render(HubDraw draw, String asset, float x, float y, float width, float height,
                        int light, int mid, int deep, int highlight) {
        Key key = new Key(asset, light, mid, deep, highlight);
        SVGDOM document = documents.get(key);
        if (document == null) {
            String xml = templates.computeIfAbsent(asset, HubProfileArt::read)
                .replace("@LIGHT@", rgb(light)).replace("@MID@", rgb(mid))
                .replace("@DEEP@", rgb(deep)).replace("@HIGHLIGHT@", rgb(highlight));
            try (Data bytes = Data.makeFromBytes(xml.getBytes(StandardCharsets.UTF_8))) {
                document = new SVGDOM(bytes);
            }
            documents.put(key, document);
            while (documents.size() > 32) documents.remove(documents.keySet().iterator().next()).close();
        }
        draw.save();
        draw.translate(x, y);
        document.setContainerSize(width, height).render(draw.canvas());
        draw.restore();
    }

    private static String read(String name) {
        try (InputStream input = HubProfileArt.class.getResourceAsStream("/assets/pixelmc_hub/profile-art/" + name + ".svg")) {
            if (input == null) throw new IllegalStateException("Missing profile artwork " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) { throw new IllegalStateException("Cannot read profile artwork", error); }
    }

    private static void mask(HubDraw draw, float x, float y, float width, float height, float from, float to) {
        try (Shader shader = Shader.makeLinearGradient(x, y, x + width, y,
                new int[]{0x00ffffff, 0x00ffffff, 0xffffffff, 0xffffffff}, new float[]{0, from, to, 1});
             Paint paint = new Paint().setBlendMode(BlendMode.DST_IN).setShader(shader)) {
            draw.canvas().drawRect(Rect.makeXYWH(x, y, width, height), paint);
        }
    }

    public static int[] palette(String name, boolean dark) {
        if (dark) return switch (name == null ? "" : name) {
            case "lilac" -> new int[]{0xff49415f, 0xff37334c};
            case "sage" -> new int[]{0xff394e4b, 0xff303d42};
            case "sand" -> new int[]{0xff504a43, 0xff3d3b42};
            case "blush" -> new int[]{0xff55404d, 0xff3b3444};
            case "apricot" -> new int[]{0xff57473e, 0xff40383d};
            case "tide" -> new int[]{0xff345152, 0xff2e3c45};
            case "twilight" -> new int[]{0xff45435e, 0xff32364b};
            case "frost" -> new int[]{0xff3b4e5b, 0xff303d4b};
            case "stone" -> new int[]{0xff454b4c, 0xff353c42};
            default -> new int[]{0xff39445f, 0xff30384f};
        };
        return switch (name == null ? "" : name) {
            case "lilac" -> new int[]{0xffd9cfe9, 0xffeee9f3};
            case "sage" -> new int[]{0xffccdcd2, 0xffe8efea};
            case "sand" -> new int[]{0xffe2d7c3, 0xfff0ece2};
            case "blush" -> new int[]{0xffe6ced5, 0xfff2e9ed};
            case "apricot" -> new int[]{0xffe7d1bd, 0xfff3eae0};
            case "tide" -> new int[]{0xffc2dcd9, 0xffe5eeed};
            case "twilight" -> new int[]{0xffd0cfe1, 0xffe8e8f0};
            case "frost" -> new int[]{0xffcedee4, 0xffeaf0f2};
            case "stone" -> new int[]{0xffd5d6d2, 0xffeceee9};
            default -> new int[]{0xffc8d6ec, 0xffe7edf5};
        };
    }

    public static String normalizePattern(String value) {
        for (String pattern : PATTERNS) if (pattern.equals(value)) return value;
        return "classic";
    }

    public static int mix(int first, int second, float amount) {
        float f = Math.max(0, Math.min(1, amount));
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8)
            result |= Math.round(((first >>> shift) & 255) * (1 - f) + ((second >>> shift) & 255) * f) << shift;
        return result;
    }
    private static String rgb(int color) { return String.format(java.util.Locale.ROOT, "#%06x", color & 0xffffff); }
    @Override public void close() { documents.values().forEach(SVGDOM::close); documents.clear(); shared.close(); }
}
