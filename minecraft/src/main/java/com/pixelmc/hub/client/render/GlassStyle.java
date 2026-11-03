/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.render;

/** GlassSurface props in CSS pixels. opacity/blur describe its displacement MAP, not whole-element alpha. */
public record GlassStyle(float borderWidth, float brightness, float opacity, float blur, float displace,
                         float backgroundOpacity, float saturation, float distortionScale,
                         float redOffset, float greenOffset, float blueOffset, boolean refraction,
                         int tint, float alpha, float backdropBlur, boolean decorations) {
    public static final GlassStyle DEFAULT = new GlassStyle(.07f, 50, .93f, 11, 0, 0, 1, -180,
        0, 10, 20, true, 0xFFFFFFFF, 1, 0, true);
    public static final GlassStyle SIDEBAR = new GlassStyle(.08f, 65, .9f, 12, .5f, .32f, 1.15f, -12,
        0, 0, 0, false, 0x00FFFFFF, 1, 12, true);
    public static final GlassStyle IDENTITY = new GlassStyle(.04f, 65, .7f, 8, 0, .09f, 1.25f, -45,
        0, 0, 0, false, 0xFFFFFFFF, 1, 12, true);
    public static final GlassStyle PANEL = new GlassStyle(0, 50, 0, 0, 0, .6f, 1, 0,
        0, 0, 0, false, 0xFFF4F6FF, 1, 24, false);
    public static final GlassStyle MODAL_BACKDROP = new GlassStyle(0, 50, 0, 0, 0, 0, 1, 0,
        0, 0, 0, false, 0x00FFFFFF, 1, 9, false);

    public GlassStyle withAlpha(float value) {
        return new GlassStyle(borderWidth, brightness, opacity, blur, displace, backgroundOpacity, saturation,
            distortionScale, redOffset, greenOffset, blueOffset, refraction, tint, value, backdropBlur, decorations);
    }
    public GlassStyle withTint(int color, float amount) {
        return new GlassStyle(borderWidth, brightness, opacity, blur, displace, amount, saturation,
            distortionScale, redOffset, greenOffset, blueOffset, refraction, color, alpha, backdropBlur, decorations);
    }
}
