/*
 * Adapted from src/vendor/react-bits/GlassIcons.jsx, poseOf(), by David Haz (2026).
 * MIT + Commons Clause; NOT Apache-2.0. Full terms are retained in
 * assets/pixelmc_hub/licenses/React-Bits-LICENSE.md. Application-integrated port only.
 */
package com.pixelmc.hub.client.render;

/** Exact navigation poseOf output. Spring state is advanced by the UI, not by the renderer. */
public record IconPose(float liftY, float glassScale, float glassAlpha, float plateAlpha,
                       float faceY, float faceScale, float faceAlpha, float plateScale,
                       float shiftX, float shiftY, float angle, float offsetX, float offsetY,
                       float bend, float glow, float lightX, float lightY) {
    public static IconPose navigation(float size, float enter, float hover, float press,
                                      float pointerX, float pointerY, boolean reducedMotion) {
        if (reducedMotion) { enter = 1; hover = 0; press = 0; pointerX = 0; pointerY = 0; }
        enter = clamp(enter);
        float swing = ease((enter - .18f) / .82f);
        float liftY = -.08f * size * hover;
        float settle = .92f + .08f * ease(enter);
        float glyphRise = (1 - ease((enter - .25f) / .75f)) * .1f * size;
        float lx = -.55f + pointerX * .35f, ly = -.83f + pointerY * .35f;
        float length = (float) Math.hypot(lx, ly);
        return new IconPose(liftY, settle, fade(0, .55f, enter), fade(0, .35f, enter),
            liftY + glyphRise, 1, fade(.3f, .85f, enter), 1,
            -pointerX * .65f * .07f * size, -pointerY * .65f * .07f * size,
            (-16 + 10 * hover) * swing, -.08f * size * hover, -.08f * size * hover,
            1 + 1.2f * (1 - ease(enter)), 1 + .25f * hover, lx / length, ly / length);
    }
    private static float clamp(float value) { return Math.min(1, Math.max(0, value)); }
    private static float ease(float value) { return 1 - (float) Math.pow(1 - clamp(value), 3); }
    private static float fade(float start, float end, float value) {
        float x = clamp((value - start) / (end - start)); return x * x * (3 - 2 * x);
    }
}
