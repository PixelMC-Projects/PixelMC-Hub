package com.pixelmc.hub.client.ui;

import java.util.List;
import java.util.Random;

/** Pure Java behavior checks; no Minecraft, GPU, browser, clipboard or game process. */
public final class HubTextEditorChecks {
    private static int checks;
    private static final HubTextEditor.Measurer MONO = text -> text.codePointCount(0, text.length()) * 10f;
    private static void yes(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
    private static void eq(Object expected, Object actual, String label) { yes(expected.equals(actual), label + " expected=" + expected + " actual=" + actual); }
    private static void close(float expected, float actual, String label) { yes(Math.abs(expected - actual) < .001f, label + " expected=" + expected + " actual=" + actual); }
    private static HubTextEditor editor(boolean multi, String value, float width, float height) {
        HubTextEditor result = new HubTextEditor(multi, 2000); result.layout(MONO, width, height, 10); result.setText(value); return result;
    }
    public static void main(String[] args) {
        HubTextEditor search = editor(false, "abcd", 100, 10);
        search.pointerDown(14, 4, false); search.pointerUp(); eq(1, search.caret(), "click before midpoint");
        search.pointerDown(16, 4, false); search.pointerUp(); eq(2, search.caret(), "click after midpoint");
        search.pointerDown(10, 0, false); search.pointerDrag(35, 0); search.pointerUp(); eq("bcd", search.selectedText(), "drag exact partial selection");
        search.insert("XY"); eq("aXY", search.text(), "paste replaces only selected range");
        search.select(1, 2); eq("X", search.cutSelection(), "cut returns exact clipboard text"); eq("aY", search.text(), "cut preserves other characters");
        search.selectAll(); eq("aY", search.selectedText(), "copy select all"); search.backspace(); eq("", search.text(), "delete selected text");

        HubTextEditor unicode = editor(false, "A😀中B", 100, 10);
        unicode.select(2, 2); eq(1, unicode.caret(), "clamp programmatic caret out of surrogate pair");
        unicode.moveRight(false, false); eq(3, unicode.caret(), "right skips complete supplementary code point");
        unicode.moveLeft(false, true); eq("😀", unicode.selectedText(), "shift selects complete code point");
        unicode.backspace(); eq("A中B", unicode.text(), "selection delete never splits surrogate pair");
        unicode.select(1, 1); unicode.insert("😀"); unicode.deleteForward(); eq("A😀B", unicode.text(), "forward delete removes following code point");
        unicode.backspace(); eq("AB", unicode.text(), "backspace removes both surrogate halves");
        unicode.type('\ud83d'); eq("AB", unicode.text(), "typed high surrogate is buffered"); unicode.type('\ude00'); eq("A😀B", unicode.text(), "typed low surrogate completes one character");
        unicode.select(1, 1); unicode.insert("\ud83dBAD\ude00"); eq("ABAD😀B", unicode.text(), "malformed pasted surrogate halves are omitted");
        unicode.setMaxLength(2); eq("AB", unicode.text(), "changed server limit truncates at safe boundary");
        HubTextEditor limit = new HubTextEditor(false, 2); limit.insert("😀X"); eq("😀", limit.text(), "UTF16 cap keeps whole supplementary character");
        limit.selectAll(); limit.insert("中"); eq("中", limit.text(), "selection replacement reclaims UTF16 capacity");
        limit.setText("A😀"); eq("A", limit.text(), "UTF16 cap never stores partial pair");

        HubTextEditor words = editor(true, "alpha beta gamma", 70, 20);
        eq(List.of("alpha ", "beta ", "gamma"), words.lines().stream().map(HubTextEditor.Line::text).toList(), "wrap English words before splitting");
        words.moveHome(true, false); words.moveRight(true, false); eq(6, words.caret(), "CtrlRight reaches next word start");
        words.moveRight(true, true); eq("beta ", words.selectedText(), "CtrlShiftRight extends word selection");
        words.moveLeft(false, false); eq(6, words.caret(), "plain Left collapses selected range without extra movement");
        words.moveEnd(false, false); eq(11, words.caret(), "End uses current visual line"); close(50, words.caretRect().x(), "soft-wrap End retains previous-line affinity");
        words.moveHome(false, false); eq(6, words.caret(), "Home uses visual line affinity");
        words.moveEnd(true, true); eq("beta gamma", words.selectedText(), "CtrlShiftEnd selects to document end");
        words.moveLeft(true, false); eq(6, words.caret(), "Left collapses selected range even with Ctrl");
        words.moveEnd(true, false); words.backspace(true); eq("alpha beta ", words.text(), "CtrlBackspace deletes last word");

        HubTextEditor lines = editor(true, "abcd\nx\nabcdef", 100, 20);
        lines.select(3, 3); lines.moveVertical(1, false); eq(6, lines.caret(), "Down lands at short line end");
        lines.moveVertical(1, false); eq(10, lines.caret(), "second Down restores desired horizontal x");
        lines.moveVertical(-1, true); eq("\nabc", lines.selectedText(), "Up with Shift extends selection across newline");
        lines.moveHome(true, false); close(0, lines.scrollY(), "CtrlHome scrolls to caret at beginning");
        lines.moveEnd(true, false); yes(lines.scrollY() > 0, "CtrlEnd scrolls caret into viewport");
        HubTextEditor.Rect lastCaret = lines.caretRect(); yes(lastCaret.y() >= 0 && lastCaret.y() + lastCaret.height() <= 20, "caret visible vertically after move");
        lines.select(0, 5); eq(1, lines.selectionRects().size(), "newline selection belongs to preceding line"); yes(lines.selectionRects().get(0).width() >= 40, "selected newline preserves visible preceding width");
        HubTextEditor emptyLines = editor(true, "\n\n", 30, 10); eq(3, emptyLines.lines().size(), "consecutive and trailing newline keep empty visual lines");
        emptyLines.moveHome(true, false); emptyLines.deleteForward(); eq("\n", emptyLines.text(), "delete at empty line joins logical lines");
        emptyLines.setText("a\r\nb\rc"); eq("a\nb\nc", emptyLines.text(), "CRLF and CR normalize once");
        search.setText("a\r\nb\nc"); eq("abc", search.text(), "single line strips line breaks");

        HubTextEditor longSearch = editor(false, "abcdefgh", 25, 10);
        yes(longSearch.scrollX() > 0, "long single-line search scrolls horizontally"); close(24, longSearch.caretRect().x(), "single-line end caret stays inside right boundary");
        longSearch.moveHome(true, false); close(0, longSearch.scrollX(), "single-line Home reveals start");
        longSearch.setScroll(20, 0); longSearch.layout(MONO, 25, 10, 10); close(20, longSearch.scrollX(), "unchanged frame layout preserves explicit user scroll"); longSearch.moveHome(true, false); close(0, longSearch.scrollX(), "keyboard action follows caret after explicit scroll");
        longSearch.pointerDown(0, 0, false); longSearch.pointerDrag(70, 0); longSearch.pointerUp(); yes(longSearch.scrollX() > 0 && longSearch.hasSelection(), "drag outside horizontally auto scrolls with selection");
        HubTextEditor tall = editor(true, "one\ntwo\nthree\nfour\nfive", 100, 20);
        tall.moveHome(true, false); tall.pointerDown(0, 0, false); tall.pointerDrag(35, 80); tall.pointerUp(); yes(tall.scrollY() > 0 && tall.hasSelection(), "drag outside vertically follows caret");
        HubTextEditor wideWord = editor(true, "abcdef😀", 25, 20); yes(wideWord.lines().stream().allMatch(line -> wellFormed(line.text())), "overwide word splits only at code points");
        HubTextEditor variable = new HubTextEditor(false, 100); variable.layout(value -> value.chars().map(c -> c == 'W' ? 14 : 4).sum(), 100, 10, 10); variable.setText("Wii"); variable.pointerDown(15, 0, false); variable.pointerUp(); eq(1, variable.caret(), "pointer follows injected glyph measurement");
        int kept = variable.caret(); variable.setText(variable.text()); eq(kept, variable.caret(), "same draft value preserves caret");

        Random random = new Random(713001); HubTextEditor fuzz = editor(true, "", 47, 30);
        String[] insertions = {"a", "中", "😀", "\n", "abc def", "\ud83d", "\ude00", "\r\n", "<b>"};
        for (int i = 0; i < 1500; i++) {
            switch (random.nextInt(11)) {
                case 0 -> fuzz.insert(insertions[random.nextInt(insertions.length)]);
                case 1 -> fuzz.backspace(random.nextBoolean());
                case 2 -> fuzz.deleteForward(random.nextBoolean());
                case 3 -> fuzz.moveLeft(random.nextBoolean(), random.nextBoolean());
                case 4 -> fuzz.moveRight(random.nextBoolean(), random.nextBoolean());
                case 5 -> fuzz.moveVertical(random.nextBoolean() ? 1 : -1, random.nextBoolean());
                case 6 -> fuzz.moveHome(random.nextBoolean(), random.nextBoolean());
                case 7 -> fuzz.moveEnd(random.nextBoolean(), random.nextBoolean());
                case 8 -> fuzz.select(random.nextInt(fuzz.text().length() + 1), random.nextInt(fuzz.text().length() + 1));
                case 9 -> { fuzz.pointerDown(random.nextInt(80) - 10, random.nextInt(70) - 10, random.nextBoolean()); fuzz.pointerDrag(random.nextInt(80) - 10, random.nextInt(70) - 10); fuzz.pointerUp(); }
                default -> { fuzz.type('\ud83d'); fuzz.type('\ude00'); }
            }
            if (!wellFormed(fuzz.text()) || !atBoundary(fuzz.text(), fuzz.caret()) || !atBoundary(fuzz.text(), fuzz.anchor())) throw new AssertionError("Unicode invariant at operation " + i);
            HubTextEditor.Rect rect = fuzz.caretRect();
            if (!Float.isFinite(rect.x()) || !Float.isFinite(rect.y()) || fuzz.text().length() > 2000) throw new AssertionError("Geometry/length invariant at operation " + i);
        }
        yes(true, "1500 mixed edit, movement, pointer and clipboard-range operations preserve Unicode/geometry invariants");
        System.out.println("HubTextEditorChecks: " + checks + " behavior assertions passed; 1500 mixed operations; no game or GPU.");
    }
    private static boolean atBoundary(String text, int at) { return at >= 0 && at <= text.length() && !(at > 0 && at < text.length() && Character.isHighSurrogate(text.charAt(at - 1)) && Character.isLowSurrogate(text.charAt(at))); }
    private static boolean wellFormed(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) { if (i + 1 == text.length() || !Character.isLowSurrogate(text.charAt(++i))) return false; }
            else if (Character.isLowSurrogate(c)) return false;
        }
        return true;
    }
}
