/*
 * Native application adaptation of React Bits SwipeToast.jsx / SwipeToast.css.
 * Copyright (c) 2026 David Haz. MIT + Commons Clause License Condition v1.0.
 * Source: https://reactbits.dev/micro/swipe-toast ; retained license:
 * assets/pixelmc_hub/licenses/React-Bits-LICENSE.md. Not a standalone component.
 */
package com.pixelmc.hub.client.ui;

import com.pixelmc.hub.client.render.HubDraw;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** White in-panel overlay with the upstream lift/fade, fuse, hover pause and downward swipe. */
final class HubToast {
    enum Kind {
        INFO,
        SUCCESS,
        ERROR
    }

    private record Message(String text, Kind kind) {}

    private final ArrayDeque<Message> queue = new ArrayDeque<>();
    private final ArrayDeque<double[]> samples = new ArrayDeque<>();
    private final HubMotion.Spring returnY = new HubMotion.Spring();
    private Message current;
    private long entered, last, closingAt = -1;
    private double remaining = 4;
    private boolean dragging, closePressed, moved;
    private float x, y, w, h, grabY, dragY, closeY, alpha = 1;
    private boolean visibleBounds, winterReceipt;

    void show(String text, Kind kind, long now) {
        if (text == null || text.isBlank()) return;
        Message message = new Message(text, kind);
        if (message.equals(current) || queue.contains(message)) return;
        if (current == null) start(message, now);
        else {
            if (queue.size() == 4) queue.removeFirst();
            queue.addLast(message);
        }
    }

    private void start(Message message, long now) {
        current = message;
        entered = last = now;
        closingAt = -1;
        remaining = 4;
        dragging = false;
        closePressed = false;
        dragY = 0;
        returnY.snap(0);
        visibleBounds = false;
    }

    void clear() {
        current = null;
        queue.clear();
        dragging = closePressed = visibleBounds = false;
    }

    boolean active() {
        return current != null;
    }

    double remaining() {
        return remaining;
    }

    float opacity() {
        return alpha;
    }

    void dismiss(long now) {
        if (current != null && closingAt < 0) {
            closingAt = now;
            closeY = dragY;
            dragging = false;
            closePressed = false;
        }
    }

    private boolean contains(float px, float py) {
        return current != null && visibleBounds && px >= x && px <= x + w && py >= y && py <= y + h;
    }

    boolean pointer(float px, float py) {
        return contains(px, py);
    }

    void advance(long now, boolean hover, boolean reduced) {
        if (current == null) return;
        double elapsed = Math.max(0, (now - last) / 1_000_000_000.0);
        last = now;
        if (closingAt < 0) {
            if (!hover && !dragging && !closePressed) remaining = Math.max(0, remaining - elapsed);
            if (remaining == 0) dismiss(now);
        }
        if (!dragging && closingAt < 0) {
            returnY.drive(
                    0, reduced ? 300 : 200, reduced ? 40 : 23, (float) Math.min(.05, elapsed));
            dragY = returnY.value;
        }
        if (closingAt >= 0 && now - closingAt >= (reduced ? 200_000_000L : 280_000_000L)) {
            current = null;
            visibleBounds = false;
            if (!queue.isEmpty()) start(queue.removeFirst(), now);
        }
    }

    void render(
            HubDraw d,
            float mx,
            float my,
            long now,
            boolean reduced,
            float viewportX,
            float viewportY,
            float viewportW,
            float viewportH,
            boolean keyboardFocus) {
        advance(now, contains(mx, my) || keyboardFocus, reduced);
        if (current == null) return;
        winterReceipt = d.winter();
        float right = winterReceipt ? viewportX + viewportW - 24 : Math.min(1236, viewportX + viewportW - 16);
        float bottom = winterReceipt ? viewportY + viewportH - 24 : Math.min(826, viewportY + viewportH - 16);
        w = winterReceipt ? Math.min(356, viewportW - 48) : Math.min(356, right - Math.max(16, viewportX + 16));
        if (w < 120) { visibleBounds = false; return; }
        List<String> lines = lines(d, current.text, w - (winterReceipt ? 111 : 86));
        h = winterReceipt ? Math.max(60, 26 + lines.size() * 19) : Math.max(56, 32 + lines.size() * 19);
        x = right - w;
        float enter = HubMotion.bezier((now - entered) / 400_000_000f, .23f, 1, .32f, 1);
        float exit =
                closingAt < 0
                        ? 0
                        : HubMotion.bezier((now - closingAt) / 280_000_000f, .23f, 1, .32f, 1);
        alpha =
                closingAt < 0
                        ? HubMotion.ease((now - entered) / (reduced ? 200_000_000f : 240_000_000f))
                        : 1 - HubMotion.ease((now - closingAt) / 200_000_000f);
        y =
                bottom
                        - h
                        + (reduced
                                ? 0
                                : closingAt < 0 ? (1 - enter) * h + dragY : closeY + exit * h);
        visibleBounds = alpha > .05f;
        if (!winterReceipt) d.captureGlassBackdrop(x, y, w, h);
        d.saveLayerAlpha(x - 24, y - 24, w + 48, h + 60, alpha);
        try {
            if (winterReceipt) {
                boolean prior = d.literalColors(true);
                try { winterReceipt(d, lines, mx, my); } finally { d.literalColors(prior); }
                return;
            }
            d.boxShadow(x, y, w, h, 12, 0, 4, 12, 0, 0x294a4d75);
            d.blurBackdrop(x, y, w, h, 12, 16, 1);
            d.roundRect(x, y, w, h, 12, 0xf5ffffff);
            d.strokeRoundRect(x + .5f, y + .5f, w - 1, h - 1, 12, 1, 0xffffffff);
            int accent =
                    current.kind == Kind.ERROR
                            ? 0xffb77886
                            : current.kind == Kind.SUCCESS ? 0xff5c9d8a : 0xff7d6bc5;
            d.circle(
                    x + 25,
                    y + 26,
                    12,
                    current.kind == Kind.ERROR
                            ? 0xfff9edf0
                            : current.kind == Kind.SUCCESS ? 0xffe8f5ef : 0xffeeebfb);
            d.icon(current.kind == Kind.SUCCESS ? "check" : "info", x + 17, y + 18, 16, accent);
            for (int i = 0; i < lines.size(); i++)
                d.text(lines.get(i), x + 44, y + 30 + i * 19, 12, 0xff505875, 500);
            boolean closeHover =
                    mx >= x + w - 34 && mx <= x + w - 10 && my >= y + 14 && my <= y + 38;
            if (closeHover) d.roundRect(x + w - 34, y + 14, 24, 24, 5, 0xffeeedf7);
            d.icon("x", x + w - 28, y + 20, 12, 0xff8990a7);
            d.save();
            d.clipRoundRect(x, y, w, h, 12);
            float fuse = (float) (w * remaining / 4);
            if (fuse > 0)
                d.linearGradientRoundRect(
                        x,
                        y + h - 2,
                        fuse,
                        2,
                        0,
                        90,
                        new int[] {0x005d84d6, 0xff5d84d6, 0xff8970d8, 0x008970d8},
                        new float[] {0, .12f, .88f, 1});
            d.restore();
        } finally {
            d.restore();
        }
    }

    private void winterReceipt(HubDraw d, List<String> lines, float mx, float my) {
        var p = d.winterPaper();
        int accent = switch (current.kind) {
            case SUCCESS -> p.pine();
            case ERROR -> p.mailInk();
            case INFO -> d.dark() ? 0xffabc8d0 : 0xff4b6874;
        };
        float mid = y + h / 2;
        d.boxShadow(x, y, w, h, 14, 0, 5, 15, -5, 0x52182e28);
        d.roundRect(x, y + 2, w, h, 14, p.ruling());
        HubWinterSheet.panel(d, x, y, w, h, 14, p.sheet(), 1);
        HubWinterSheet.grain(d, x, y, w, h, 14, 1);
        d.save();
        d.canvas().concat(io.github.humbleui.skija.Matrix33.makeRotate(-5, x + 30.5f, mid));
        float stamp = x + 16;
        d.roundRect(stamp, mid - 15, 29, 30, 6, HubWinterSheet.mix(p.sheet(), accent, .10f));
        d.strokeRoundRect(stamp + .5f, mid - 14.5f, 28, 29, 6, 1, HubWinterSheet.mix(p.sheet(), accent, .56f));
        try (var dash = io.github.humbleui.skija.PathEffect.makeDash(new float[]{2, 2}, 0);
                var paint = new io.github.humbleui.skija.Paint().setAntiAlias(true)
                        .setColor(HubWinterSheet.alpha(accent, .47f))
                        .setMode(io.github.humbleui.skija.PaintMode.STROKE).setStrokeWidth(1).setPathEffect(dash)) {
            d.canvas().drawRRect(io.github.humbleui.types.RRect.makeXYWH(stamp + 3, mid - 12, 23, 24, 4), paint);
        }
        if (current.kind == Kind.ERROR) {
            d.roundRect(stamp + 13, mid - 6.5f, 2.5f, 8, 1.2f, accent);
            d.circle(stamp + 14.25f, mid + 5, 1.55f, accent);
        } else d.icon(current.kind == Kind.SUCCESS ? "check" : "info", stamp + 7, mid - 7.5f, 15, accent);
        d.restore();
        float textHeight = Math.max(32, lines.size() * 19);
        for (float at = mid - textHeight / 2; at < mid + textHeight / 2; at += 5)
            d.rect(x + 57, at, 1, Math.min(3, mid + textHeight / 2 - at), p.stitch());
        float baseline = mid - lines.size() * 9.5f + 14;
        for (int i = 0; i < lines.size(); i++) d.textSerif(lines.get(i), x + 70, baseline + 19 * i, 13, p.ink(), true);
        if (overClose(mx, my)) d.circle(x + w - 23, mid, 12, p.cover());
        d.icon("x", x + w - 29, mid - 6, 12, p.muted());
        d.roundRect(x + 69, y + h - 7, Math.max(0, (w - 89) * (float) remaining / 4),
                2, 1, HubWinterSheet.mix(p.sheet(), accent, .68f));
    }

    boolean canClose() { return visibleBounds && current != null && closingAt < 0; }
    float closeLeft() { return x + w - 35; }
    float closeTop() { return y + h / 2 - 12; }

    private boolean overClose(float px, float py) {
        float top = winterReceipt ? y + h / 2 - 12 : y + 14;
        return px >= x + w - (winterReceipt ? 35 : 34) && px <= x + w - (winterReceipt ? 11 : 10)
                && py >= top && py <= top + 24;
    }

    private List<String> lines(HubDraw d, String text, float maxWidth) {
        List<String> result = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (int cp : text.codePoints().toArray()) {
            String next = new String(Character.toChars(cp));
            if (cp == '\n' || line.length() > 0 && (winterReceipt ? d.textWidthSerif(line + next, 13, true) : d.textWidth(line + next, 12, 500)) > maxWidth) {
                result.add(line.toString());
                line.setLength(0);
            }
            if (cp != '\n') line.append(next);
        }
        if (line.length() > 0) result.add(line.toString());
        return result;
    }

    boolean press(float px, float py, long now) {
        if (!contains(px, py) || closingAt >= 0) return false;
        closePressed = overClose(px, py);
        if (!closePressed) {
            dragging = true;
            grabY = py - dragY;
            moved = false;
            samples.clear();
            sample(now, dragY);
        }
        return true;
    }

    boolean drag(float py, long now) {
        if (!dragging) return closePressed;
        float raw = py - grabY;
        if (!moved && Math.abs(raw - dragY) < 3) return true;
        moved = true;
        dragY = raw >= 0 ? raw : (raw * 24 * .55f) / (24 + .55f * Math.abs(raw));
        returnY.snap(dragY);
        sample(now, dragY);
        return true;
    }

    private void sample(long now, float value) {
        samples.addLast(new double[] {now / 1_000_000.0, value});
        while (samples.size() > 4) samples.removeFirst();
    }

    boolean release(float px, float py, long now) {
        if (closePressed) {
            closePressed = false;
            if (overClose(px, py)) dismiss(now);
            return true;
        }
        if (!dragging) return false;
        dragging = false;
        double velocity = 0;
        if (samples.size() > 1 && now / 1_000_000.0 - samples.getLast()[0] <= 100) {
            double[] first = samples.getFirst(), lastSample = samples.getLast();
            velocity = (lastSample[1] - first[1]) / Math.max(1, lastSample[0] - first[0]);
        }
        if (dragY > 0 && (velocity > .11 || dragY >= 40 && velocity >= 0)) dismiss(now);
        else {
            returnY.snap(dragY);
            returnY.velocity = (float) velocity * 1000;
        }
        return true;
    }
}
