/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.render;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.IRect;

/** A short-lived outgoing-panel GPU image; the incoming UI and the outside world stay live. */
final class HubThemeTransition implements AutoCloseable {
    private static final long DURATION_NANOS = 220_000_000L;
    private final Paint paint = new Paint();
    private final HubCorners corners = new HubCorners();
    private Image outgoing;
    private float x, y, scale, panelWidth, panelHeight, radius;
    private int cropX, cropY;
    private long startedAt;

    void capture(
            Surface source,
            float x,
            float y,
            float scale,
            float width,
            float height,
            float radius,
            long now) {
        int left = Math.max(0, (int) Math.floor(x)), top = Math.max(0, (int) Math.floor(y));
        int right = Math.min(source.getWidth(), (int) Math.ceil(x + width * scale));
        int bottom = Math.min(source.getHeight(), (int) Math.ceil(y + height * scale));
        if (right <= left || bottom <= top) return;
        Image next = source.makeImageSnapshot(IRect.makeLTRB(left, top, right, bottom));
        if (next == null) throw new IllegalStateException("Cannot retain the outgoing Hub panel");
        if (outgoing != null) outgoing.close();
        outgoing = next;
        this.x = x;
        this.y = y;
        this.scale = scale;
        panelWidth = width;
        panelHeight = height;
        this.radius = radius;
        cropX = left;
        cropY = top;
        startedAt = now;
    }

    boolean matchesLayout(float x, float y, float scale) {
        return this.x == x && this.y == y && this.scale == scale;
    }

    boolean draw(Canvas canvas, long now) {
        if (outgoing == null) return false;
        float t = Math.max(0, (now - startedAt) / (float) DURATION_NANOS);
        if (t >= 1) return false;
        float alpha = 1 - t * t * (3 - 2 * t);
        int save = canvas.save();
        try {
            canvas.resetMatrix();
            canvas.translate(x, y).scale(scale, scale);
            // Clip in logical coordinates, preserving the shell's continuous corner contour.
            corners.clip(canvas, 0, 0, panelWidth, panelHeight, radius);
            canvas.resetMatrix();
            canvas.drawImage(outgoing, cropX, cropY, paint.setAlphaf(alpha));
        } finally {
            canvas.restoreToCount(save);
        }
        return true;
    }

    @Override
    public void close() {
        if (outgoing != null) {
            outgoing.close();
            outgoing = null;
        }
        corners.close();
        paint.close();
    }
}
