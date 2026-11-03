/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.render;

import io.github.humbleui.skija.*;
import io.github.humbleui.skija.shaper.Shaper;
import io.github.humbleui.types.Rect;
import io.github.humbleui.types.RRect;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/** Logical, top-left drawing helpers. Native resources belong to the owning renderer. */
public final class HubDraw implements AutoCloseable {
    private static final String ROOT = "/assets/pixelmc_hub/";
    private static final int MAX_TEXT_LINES = 768;
    private final Paint paint = new Paint();
    private final FontMgr fontManager = FontMgr.getDefault();
    private final Shaper shaper = Shaper.makeBestAvailable(fontManager);
    private final Typeface regular;
    private final Typeface bold;
    private final Typeface serifRegular;
    private final Typeface serifBold;
    private final Typeface cjkRegular;
    private final Typeface cjkBold;
    private final Map<FontKey, Font> fonts = new LinkedHashMap<>();
    private final Map<TextKey, TextLine> lines = new LinkedHashMap<>(128, 0.75f, true);
    private final Map<String, Image> images = new LinkedHashMap<>();
    private final Map<RunKey, List<FontRunText>> sansRuns = new LinkedHashMap<>(128, 0.75f, true);
    private final Map<Integer, Boolean> primaryGlyphs = new LinkedHashMap<>();
    private Canvas canvas;
    private int baseSaveCount;
    private boolean closed;

    HubDraw() {
        regular = typeface("fonts/Hub-Regular.ttf", FontStyle.NORMAL);
        bold = typeface("fonts/Hub-Bold.ttf", FontStyle.BOLD);
        serifRegular = serifTypeface(FontStyle.NORMAL);
        serifBold = serifTypeface(FontStyle.BOLD);
        cjkRegular = cjkTypeface(FontStyle.NORMAL);
        cjkBold = cjkTypeface(FontStyle.BOLD);
    }

    private Typeface serifTypeface(FontStyle style) {
        Typeface result = fontManager.matchFamiliesStyleCharacter(
            new String[]{"SimSun", "Noto Serif CJK SC", "Source Han Serif SC", "serif"},
            style, new String[]{"zh-CN", "en"}, '签');
        if (result == null) result = fontManager.matchFamilyStyle("serif", style);
        if (result == null) throw new IllegalStateException("No usable local serif font is available");
        return result;
    }

    private Typeface typeface(String bundled, FontStyle style) {
        try (InputStream stream = HubDraw.class.getResourceAsStream(ROOT + bundled)) {
            if (stream != null) {
                try (Data data = Data.makeFromBytes(stream.readAllBytes())) {
                    Typeface result = fontManager.makeFromData(data);
                    if (result != null) return result;
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read bundled Hub font " + bundled, exception);
        }
        // Match the reference CSS's first family for Latin; unsupported CJK glyphs use explicit runs below.
        Typeface result = fontManager.matchFamiliesStyle(new String[]{"Segoe UI", "Microsoft YaHei UI", "sans-serif"}, style);
        if (result == null) result = fontManager.matchFamilyStyle("sans-serif", style);
        if (result == null) throw new IllegalStateException("No usable local font is available");
        return result;
    }

    private Typeface cjkTypeface(FontStyle style) {
        Typeface result = fontManager.matchFamiliesStyleCharacter(
            new String[]{"Microsoft YaHei UI", "Microsoft YaHei", "Noto Sans CJK SC", "PingFang SC", "sans-serif"},
            style, new String[]{"zh-CN", "en"}, '中');
        if (result == null) result = fontManager.matchFamilyStyle("sans-serif", style);
        if (result == null) throw new IllegalStateException("No usable local CJK font is available");
        return result;
    }

    void begin(Canvas value) {
        if (closed) throw new IllegalStateException("HubDraw was closed");
        canvas = value;
        baseSaveCount = canvas.getSaveCount();
    }

    void end() { canvas = null; }

    public Canvas canvas() {
        if (canvas == null) throw new IllegalStateException("HubDraw can only draw inside HubRenderer.render");
        return canvas;
    }

    private Paint fill(int color) { return paint.reset().setAntiAlias(true).setColor(color); }

    public void rect(float x, float y, float w, float h, int color) {
        if (w > 0 && h > 0) canvas().drawRect(Rect.makeXYWH(x, y, w, h), fill(color));
    }

    public void roundRect(float x, float y, float w, float h, float radius, int color) {
        if (w > 0 && h > 0) canvas().drawRRect(RRect.makeXYWH(x, y, w, h, radius), fill(color));
    }

    public void strokeRoundRect(float x, float y, float w, float h, float radius, float stroke, int color) {
        if (w > 0 && h > 0 && stroke > 0)
            canvas().drawRRect(RRect.makeXYWH(x, y, w, h, radius), fill(color).setMode(PaintMode.STROKE).setStrokeWidth(stroke));
    }

    public void gradientRoundRect(float x, float y, float w, float h, float radius, int topLeft, int bottomRight) {
        if (w <= 0 || h <= 0) return;
        try (Shader shader = Shader.makeLinearGradient(x, y, x + w, y + h, new int[]{topLeft, bottomRight})) {
            canvas().drawRRect(RRect.makeXYWH(x, y, w, h, radius), fill(0xFFFFFFFF).setShader(shader));
            paint.setShader(null);
        }
    }

    public void circle(float cx, float cy, float radius, int color) {
        if (radius > 0) canvas().drawCircle(cx, cy, radius, fill(color));
    }

    public void line(float x1, float y1, float x2, float y2, float stroke, int color) {
        if (stroke > 0) canvas().drawLine(x1, y1, x2, y2, fill(color).setStrokeWidth(stroke).setStrokeCap(PaintStrokeCap.ROUND));
    }

    private TextLine textLine(String text, float size, boolean heavy) { return textLine(text, size, heavy, false); }

    private TextLine textLine(String text, float size, boolean heavy, boolean serif) { return textLine(text, size, heavy, serif, false); }

    private TextLine textLine(String text, float size, boolean heavy, boolean serif, boolean cjk) {
        TextKey key = new TextKey(text, size, heavy, serif, cjk);
        TextLine existing = lines.get(key);
        if (existing != null) return existing;
        Typeface face = serif ? (heavy ? serifBold : serifRegular) : cjk ? (heavy ? cjkBold : cjkRegular) : (heavy ? bold : regular);
        Font font = fonts.computeIfAbsent(new FontKey(size, heavy, serif, cjk), k ->
            new Font(face, size).setSubpixel(true).setEdging(FontEdging.ANTI_ALIAS).setEmboldened(heavy && !face.isBold())
                .setHinting(FontHinting.SLIGHT).setBaselineSnapped(false));
        // HarfBuzz shaping and FontMgr fallback keep Chinese, punctuation and mixed-script metrics aligned.
        TextLine created = shaper.shapeLine(text, font);
        lines.put(key, created);
        if (fonts.size() > 96) {
            var iterator = fonts.entrySet().iterator();
            iterator.next().getValue().close();
            iterator.remove();
        }
        if (lines.size() > MAX_TEXT_LINES) {
            var iterator = lines.entrySet().iterator();
            iterator.next().getValue().close();
            iterator.remove();
        }
        return created;
    }

    public void text(String text, float x, float baseline, float size, int color, boolean bold) {
        if (text == null || text.isEmpty() || size <= 0) return;
        for (FontRunText run : sansRuns(text, bold)) {
            TextLine line = textLine(run.text, size, bold, false, run.cjk);
            canvas().drawTextLine(line, x, baseline, fill(color));
            x += line.getWidth();
        }
    }

    public float textWidth(String text, float size, boolean bold) {
        if (text == null || text.isEmpty() || size <= 0) return 0;
        float width = 0;
        for (FontRunText run : sansRuns(text, bold)) width += textLine(run.text, size, bold, false, run.cjk).getWidth();
        return width;
    }

    private List<FontRunText> sansRuns(String text, boolean heavy) {
        RunKey key = new RunKey(text, heavy);
        List<FontRunText> cached = sansRuns.get(key);
        if (cached != null) return cached;
        List<FontRunText> result = new ArrayList<>();
        int runStart = 0;
        boolean lastCjk = false;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            int glyphKey = codePoint * 2 + (heavy ? 1 : 0);
            boolean cjk = !primaryGlyphs.computeIfAbsent(glyphKey, ignored -> (heavy ? bold : regular).getUTF32Glyph(codePoint) != 0);
            // Keep combining marks and joiners with their preceding shaping run.
            int type = Character.getType(codePoint);
            if (offset > 0 && (type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK || codePoint == 0x200D)) cjk = lastCjk;
            if (offset > 0 && cjk != lastCjk) {
                result.add(new FontRunText(text.substring(runStart, offset), lastCjk));
                runStart = offset;
            }
            lastCjk = cjk;
            offset += Character.charCount(codePoint);
        }
        result.add(new FontRunText(text.substring(runStart), lastCjk));
        sansRuns.put(key, result);
        if (sansRuns.size() > 768) { var iterator = sansRuns.entrySet().iterator(); iterator.next(); iterator.remove(); }
        if (primaryGlyphs.size() > 4096) primaryGlyphs.clear();
        return result;
    }

    /** Local Song/serif typography for fortune poetry; no remote font is requested. */
    public void textSerif(String text, float x, float baseline, float size, int color, boolean bold) {
        if (text == null || text.isEmpty() || size <= 0) return;
        canvas().drawTextLine(textLine(text, size, bold, true), x, baseline, fill(color));
    }

    public float textWidthSerif(String text, float size, boolean bold) {
        if (text == null || text.isEmpty() || size <= 0) return 0;
        return textLine(text, size, bold, true).getWidth();
    }

    /** Cached classpath image, borrowed by the caller: do not close it. */
    public Image imageResource(String asset) {
        if (closed) throw new IllegalStateException("HubDraw was closed");
        String path = asset.replace('\\', '/');
        if (path.startsWith("/") || path.contains("..")) throw new IllegalArgumentException("Asset must be relative to " + ROOT);
        return images.computeIfAbsent(path, name -> {
            try (InputStream stream = HubDraw.class.getResourceAsStream(ROOT + name)) {
                if (stream == null) throw new IllegalArgumentException("Missing Hub image: " + name);
                return Image.makeFromEncoded(stream.readAllBytes());
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot decode Hub image " + name, exception);
            }
        });
    }

    public void image(String asset, float x, float y, float w, float h, float alpha) {
        if (w <= 0 || h <= 0 || alpha <= 0) return;
        Image image = imageResource(asset);
        canvas().drawImageRect(image, Rect.makeWH(image.getWidth(), image.getHeight()), Rect.makeXYWH(x, y, w, h),
            asset.startsWith("textures/ui/fortune-") || asset.equals("textures/ui/collection-chest.png") ? SamplingMode.DEFAULT : SamplingMode.LINEAR,
            fill(0xFFFFFFFF).setAlphaf(Math.min(1, alpha)), true);
    }

    public void icon(String name, float x, float y, float size, int color) {
        if (size <= 0) return;
        Image image = imageResource("textures/icons/" + name + ".png");
        try (ColorFilter tint = ColorFilter.makeBlend(color, BlendMode.SRC_IN)) {
            canvas().drawImageRect(image, Rect.makeWH(image.getWidth(), image.getHeight()), Rect.makeXYWH(x, y, size, size),
                SamplingMode.LINEAR, fill(0xFFFFFFFF).setColorFilter(tint), true);
            paint.setColorFilter(null);
        }
    }

    public void save() { canvas().save(); }
    public void restore() {
        if (canvas().getSaveCount() <= baseSaveCount) throw new IllegalStateException("Unbalanced HubDraw.restore");
        canvas.restore();
    }
    public void translate(float x, float y) { canvas().translate(x, y); }
    public void clipRoundRect(float x, float y, float w, float h, float radius) {
        canvas().clipRRect(RRect.makeXYWH(x, y, Math.max(0, w), Math.max(0, h), radius), true);
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        canvas = null;
        lines.values().forEach(TextLine::close);
        fonts.values().forEach(Font::close);
        images.values().forEach(Image::close);
        lines.clear(); fonts.clear(); images.clear(); sansRuns.clear(); primaryGlyphs.clear();
        paint.close(); shaper.close(); bold.close(); regular.close(); serifBold.close(); serifRegular.close(); cjkRegular.close(); cjkBold.close();
        // FontMgr.getDefault() is a borrowed singleton and must remain alive for the process.
    }

    private record FontKey(float size, boolean bold, boolean serif, boolean cjk) {}
    private record TextKey(String text, float size, boolean bold, boolean serif, boolean cjk) {}
    private record RunKey(String text, boolean bold) {}
    private record FontRunText(String text, boolean cjk) {}
}
