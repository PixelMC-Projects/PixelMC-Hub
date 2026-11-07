package com.pixelmc.hub.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Renderer-independent text editing state. Copyright 2026 PixelMC Projects, Apache-2.0.
 * Indices and limits use UTF-16, matching the service; every public caret is a code-point boundary.
 * Line y positions are content coordinates. Caret/selection rectangles are viewport-local,
 * already offset by scrollX/scrollY. The caller owns clipping, drawing, clipboard and IME.
 */
public final class HubTextEditor {
    @FunctionalInterface public interface Measurer { float width(String text); }
    public record Line(int start, int end, String text, float width, float y, boolean hardBreak) {}
    public record Rect(float x, float y, float width, float height) {}
    private record Geometry(Line line, int[] boundaries, float[] x) {}

    private final boolean multiline;
    private int maxLength, caret, anchor, caretLineHint = -1;
    private String text = "", cachedText;
    private char pendingHigh;
    private boolean dragging;
    private float viewportWidth = 1, viewportHeight = 1, lineHeight = 1, scrollX, scrollY;
    private float cachedWidth = -1, cachedLineHeight = -1, contentWidth, preferredX = Float.NaN;
    private Measurer measurer = value -> value.codePointCount(0, value.length());
    private List<Geometry> geometry = List.of();
    private List<Line> lines = List.of();

    public HubTextEditor(boolean multiline, int maxLengthUtf16) {
        this.multiline = multiline;
        this.maxLength = Math.max(0, maxLengthUtf16);
        rebuild();
    }
    public String text() { return text; }
    public int caret() { return caret; }
    public int anchor() { return anchor; }
    public int selectionStart() { return Math.min(anchor, caret); }
    public int selectionEnd() { return Math.max(anchor, caret); }
    public boolean hasSelection() { return caret != anchor; }
    public boolean multiline() { return multiline; }
    public int maxLength() { return maxLength; }
    public float scrollX() { return scrollX; }
    public float scrollY() { return scrollY; }
    public float contentWidth() { ensureLayout(); return contentWidth; }
    public float contentHeight() { ensureLayout(); return lines.size() * lineHeight; }
    public List<Line> lines() { ensureLayout(); return lines; }
    public String selectedText() { return text.substring(selectionStart(), selectionEnd()); }

    /** External draft changes move to its end; repeating the same value preserves selection/scroll. */
    public boolean setText(String value) {
        String next = sanitize(value, maxLength);
        if (next.equals(text)) return false;
        text = next; caret = anchor = text.length(); edited(); return true;
    }
    public void setMaxLength(int maxLengthUtf16) {
        maxLength = Math.max(0, maxLengthUtf16);
        if (text.length() > maxLength) {
            text = text.substring(0, boundary(maxLength));
            caret = Math.min(caret, text.length()); anchor = Math.min(anchor, text.length()); edited();
        }
    }
    public void select(int anchor, int caret) {
        this.anchor = boundary(anchor); this.caret = boundary(caret);
        moved(-1, true); ensureCaretVisible();
    }
    public void selectAll() { select(0, text.length()); }
    public String cutSelection() {
        String selected = selectedText();
        if (hasSelection()) replaceSelection("");
        return selected;
    }
    public boolean insert(String value) {
        pendingHigh = 0;
        int available = maxLength - (text.length() - selectionEnd() + selectionStart());
        String clean = sanitize(value, Math.max(0, available));
        if (clean.isEmpty()) return false;
        return replaceSelection(clean);
    }
    /** Minecraft's charTyped bridge can deliver a supplementary character as two char calls. */
    public boolean type(char value) {
        if (Character.isHighSurrogate(value)) { pendingHigh = value; return false; }
        if (Character.isLowSurrogate(value)) {
            char high = pendingHigh; pendingHigh = 0;
            return high != 0 && insert(new String(new char[]{high, value}));
        }
        pendingHigh = 0;
        return insert(String.valueOf(value));
    }
    public boolean backspace() { return backspace(false); }
    public boolean backspace(boolean word) {
        pendingHigh = 0;
        if (!hasSelection() && caret == 0) return false;
        if (!hasSelection()) anchor = word ? wordLeft(caret) : previous(caret);
        return replaceSelection("");
    }
    public boolean deleteForward() { return deleteForward(false); }
    public boolean deleteForward(boolean word) {
        pendingHigh = 0;
        if (!hasSelection() && caret == text.length()) return false;
        if (!hasSelection()) anchor = word ? wordRight(caret) : next(caret);
        return replaceSelection("");
    }
    public boolean moveLeft(boolean ctrl, boolean shift) {
        int target = !shift && hasSelection() ? selectionStart() : ctrl ? wordLeft(caret) : previous(caret);
        return move(target, shift, -1, true);
    }
    public boolean moveRight(boolean ctrl, boolean shift) {
        int target = !shift && hasSelection() ? selectionEnd() : ctrl ? wordRight(caret) : next(caret);
        return move(target, shift, -1, true);
    }
    public boolean moveHome(boolean ctrl, boolean shift) {
        ensureLayout(); int row = caretRow();
        return move(ctrl ? 0 : lines.get(row).start(), shift, ctrl ? 0 : row, true);
    }
    public boolean moveEnd(boolean ctrl, boolean shift) {
        ensureLayout(); int row = caretRow();
        return move(ctrl ? text.length() : lines.get(row).end(), shift, ctrl ? lines.size() - 1 : row, true);
    }
    public boolean moveVertical(int delta, boolean shift) {
        ensureLayout(); int row = caretRow(), targetRow = Math.max(0, Math.min(lines.size() - 1, row + delta));
        if (Float.isNaN(preferredX)) preferredX = xAt(geometry.get(row), caret);
        return move(indexAt(geometry.get(targetRow), preferredX), shift, targetRow, false);
    }
    public void pointerDown(float x, float y, boolean shift) {
        ensureLayout(); dragging = true;
        int row = rowAt(y + scrollY), target = indexAt(geometry.get(row), x + scrollX);
        move(target, shift, row, true);
    }
    public void pointerDrag(float x, float y) {
        if (!dragging) return;
        if (x < 0) scrollX += x; else if (x > viewportWidth) scrollX += x - viewportWidth;
        if (y < 0) scrollY += y; else if (y > viewportHeight) scrollY += y - viewportHeight;
        clampScroll();
        int row = rowAt(y + scrollY);
        move(indexAt(geometry.get(row), x + scrollX), true, row, true);
    }
    public void pointerUp() { dragging = false; }
    public boolean dragging() { return dragging; }

    public void layout(Measurer measure, float width, float height, float lineHeight) {
        boolean changed = cachedText == null || positive(width) != viewportWidth || positive(height) != viewportHeight || positive(lineHeight) != this.lineHeight;
        this.measurer = Objects.requireNonNull(measure, "measure");
        this.viewportWidth = positive(width); this.viewportHeight = positive(height); this.lineHeight = positive(lineHeight);
        ensureLayout(); if (changed) ensureCaretVisible(); else clampScroll();
    }
    public void layout(Measurer measure, float width, float lineHeight) { layout(measure, width, lineHeight, lineHeight); }
    /** Call when font/scale changes without changing the logical viewport. */
    public void invalidateLayout() { cachedText = null; }
    public void setScroll(float x, float y) { ensureLayout(); scrollX = finite(x); scrollY = finite(y); clampScroll(); }
    public void scrollBy(float x, float y) { setScroll(scrollX + x, scrollY + y); }
    public Rect caretRect() {
        ensureLayout(); Geometry row = geometry.get(caretRow());
        return new Rect(xAt(row, caret) - scrollX, row.line.y() - scrollY, 1, lineHeight);
    }
    public List<Rect> selectionRects() {
        ensureLayout(); if (!hasSelection()) return List.of();
        int start = selectionStart(), end = selectionEnd(); List<Rect> result = new ArrayList<>();
        for (Geometry row : geometry) {
            Line line = row.line;
            int from = Math.max(start, line.start()), to = Math.min(end, line.end());
            boolean newline = line.hardBreak() && start <= line.end() && end > line.end();
            if (from < to || newline) {
                float left = xAt(row, from), right = xAt(row, Math.max(from, to));
                result.add(new Rect(left - scrollX, line.y() - scrollY, Math.max(newline ? 5 : 0, right - left), lineHeight));
            }
        }
        return List.copyOf(result);
    }
    public void ensureCaretVisible() {
        ensureLayout(); Geometry row = geometry.get(caretRow()); float x = xAt(row, caret), y = row.line.y();
        if (x < scrollX) scrollX = x;
        else if (x + 1 > scrollX + viewportWidth) scrollX = x + 1 - viewportWidth;
        if (y < scrollY) scrollY = y;
        else if (y + lineHeight > scrollY + viewportHeight) scrollY = y + lineHeight - viewportHeight;
        clampScroll();
    }

    private boolean replaceSelection(String value) {
        int from = selectionStart(), to = selectionEnd();
        String next = text.substring(0, from) + value + text.substring(to);
        if (next.equals(text)) { caret = anchor = from + value.length(); moved(-1, true); ensureCaretVisible(); return false; }
        text = next; caret = anchor = from + value.length(); edited(); return true;
    }
    private boolean move(int target, boolean shift, int row, boolean clearPreferred) {
        int oldCaret = caret, oldAnchor = anchor, oldRow = caretLineHint;
        caret = boundary(target); if (!shift) anchor = caret;
        moved(row, clearPreferred); ensureCaretVisible();
        return caret != oldCaret || anchor != oldAnchor || oldRow != row;
    }
    private void moved(int row, boolean clearPreferred) {
        pendingHigh = 0; caretLineHint = row; if (clearPreferred) preferredX = Float.NaN;
    }
    private void edited() { moved(-1, true); cachedText = null; ensureLayout(); ensureCaretVisible(); }
    private int boundary(int index) {
        int result = Math.max(0, Math.min(text.length(), index));
        if (result > 0 && result < text.length() && Character.isHighSurrogate(text.charAt(result - 1)) && Character.isLowSurrogate(text.charAt(result))) result--;
        return result;
    }
    private int previous(int index) { return index <= 0 ? 0 : text.offsetByCodePoints(index, -1); }
    private int next(int index) { return index >= text.length() ? text.length() : text.offsetByCodePoints(index, 1); }
    private int wordLeft(int index) {
        while (index > 0 && Character.isWhitespace(text.codePointBefore(index))) index = previous(index);
        if (index == 0) return 0; boolean word = wordCharacter(text.codePointBefore(index));
        while (index > 0 && !Character.isWhitespace(text.codePointBefore(index)) && wordCharacter(text.codePointBefore(index)) == word) index = previous(index);
        return index;
    }
    private int wordRight(int index) {
        if (index < text.length() && !Character.isWhitespace(text.codePointAt(index))) {
            boolean word = wordCharacter(text.codePointAt(index));
            while (index < text.length() && !Character.isWhitespace(text.codePointAt(index)) && wordCharacter(text.codePointAt(index)) == word) index = next(index);
        }
        while (index < text.length() && Character.isWhitespace(text.codePointAt(index))) index = next(index);
        return index;
    }
    private static boolean wordCharacter(int cp) { return Character.isLetterOrDigit(cp) || cp == '_'; }
    private void ensureLayout() {
        if (!text.equals(cachedText) || viewportWidth != cachedWidth || lineHeight != cachedLineHeight) rebuild();
    }
    private void rebuild() {
        List<Line> result = new ArrayList<>();
        if (!multiline) addLine(result, 0, text.length(), false);
        else {
            int start = 0;
            while (true) {
                int newline = text.indexOf('\n', start), end = newline < 0 ? text.length() : newline;
                if (start == end) addLine(result, start, end, newline >= 0);
                else while (start < end) {
                    int at = start, lastBreak = -1, fit = start;
                    while (at < end) {
                        int cp = text.codePointAt(at), next = at + Character.charCount(cp);
                        if (measure(text.substring(start, next)) > viewportWidth && at > start) { fit = lastBreak > start ? lastBreak : at; break; }
                        fit = next; at = next;
                        if (wrapOpportunity(cp)) lastBreak = at;
                    }
                    addLine(result, start, fit, fit == end && newline >= 0); start = fit;
                }
                if (newline < 0) break;
                start = newline + 1;
            }
        }
        List<Geometry> metrics = new ArrayList<>(); contentWidth = 0;
        for (Line line : result) {
            int count = line.text().codePointCount(0, line.text().length()), at = line.start();
            int[] boundaries = new int[count + 1]; float[] x = new float[count + 1]; boundaries[0] = at;
            for (int i = 1; i <= count; i++) { at += Character.charCount(text.codePointAt(at)); boundaries[i] = at; x[i] = measure(text.substring(line.start(), at)); }
            metrics.add(new Geometry(line, boundaries, x)); contentWidth = Math.max(contentWidth, line.width());
        }
        lines = List.copyOf(result); geometry = List.copyOf(metrics);
        cachedText = text; cachedWidth = viewportWidth; cachedLineHeight = lineHeight; clampScroll();
    }
    private void addLine(List<Line> result, int start, int end, boolean hardBreak) {
        String value = text.substring(start, end);
        result.add(new Line(start, end, value, measure(value), result.size() * lineHeight, hardBreak));
    }
    private static boolean wrapOpportunity(int cp) {
        Character.UnicodeScript script = Character.UnicodeScript.of(cp);
        return Character.isWhitespace(cp) || cp == '-' || cp == '/' || script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.HIRAGANA || script == Character.UnicodeScript.KATAKANA || script == Character.UnicodeScript.HANGUL;
    }
    private float measure(String value) { return Math.max(0, finite(measurer.width(value))); }
    private int caretRow() {
        if (caretLineHint >= 0 && caretLineHint < lines.size()) {
            Line hint = lines.get(caretLineHint);
            if (caret >= hint.start() && caret <= hint.end()) return caretLineHint;
        }
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            if (caret >= line.start() && (caret < line.end() || caret == line.end() && (line.hardBreak() || i == lines.size() - 1))) return i;
        }
        return lines.size() - 1;
    }
    private int rowAt(float y) { return Math.max(0, Math.min(lines.size() - 1, (int)Math.floor(y / lineHeight))); }
    private static float xAt(Geometry row, int index) {
        for (int i = 0; i < row.boundaries.length; i++) if (row.boundaries[i] >= index) return row.x[i];
        return row.line.width();
    }
    private static int indexAt(Geometry row, float x) {
        for (int i = 1; i < row.boundaries.length; i++) if (x < (row.x[i - 1] + row.x[i]) / 2) return row.boundaries[i - 1];
        return row.line.end();
    }
    private void clampScroll() {
        scrollX = Math.max(0, Math.min(scrollX, Math.max(0, contentWidth + 1 - viewportWidth)));
        scrollY = Math.max(0, Math.min(scrollY, Math.max(0, lines.size() * lineHeight - viewportHeight)));
    }
    private String sanitize(String value, int limit) {
        if (value == null || value.isEmpty() || limit <= 0) return "";
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n'); StringBuilder clean = new StringBuilder();
        for (int at = 0; at < normalized.length();) {
            int cp = normalized.codePointAt(at); at += Character.charCount(cp);
            if (cp >= Character.MIN_SURROGATE && cp <= Character.MAX_SURROGATE || Character.isISOControl(cp) && !(multiline && cp == '\n')) continue;
            if (clean.length() + Character.charCount(cp) > limit) break;
            clean.appendCodePoint(cp);
        }
        return clean.toString();
    }
    private static float finite(float value) { return Float.isFinite(value) ? value : 0; }
    private static float positive(float value) { return Math.max(1, finite(value)); }
}
