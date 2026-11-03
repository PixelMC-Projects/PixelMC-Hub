/* Copyright 2026 zhan_cpu / PixelMC Projects. SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.ui;

import com.pixelmc.hub.client.render.HubDraw;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ColorFilter;
import io.github.humbleui.skija.ColorMatrix;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Matrix33;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.types.Rect;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pixel-nearest, affine textured cube matching PlayerHead.jsx/player-head.css.
 * Coordinates are the original CSS cube box; projection may extend beyond it.
 * All methods must run on Minecraft's render thread. The runtime skin is memory-only.
 */
public final class HubHead {
    private static final float PITCH = (float) Math.toRadians(-12);
    private static final float HAT_SCALE = 9f / 8f;
    private static final String FALLBACK = "textures/ui/avatar.png";
    private static Image skin;

    // Each face lists the UV top-left, 3D top-left, right and down unit vectors.
    // This is the same CSS face transform order as the reference's six spans.
    private static final Face[] FACES = {
        new Face(8, 8, 1f,   -1,-1, 1,   1,0,0,   0,1,0), // front
        new Face(24,8, 1f,    1,-1,-1,  -1,0,0,   0,1,0), // back
        new Face(0, 8,.82f,  -1,-1,-1,   0,0,1,   0,1,0), // left
        new Face(16,8,.88f,   1,-1, 1,   0,0,-1,  0,1,0), // right
        new Face(8, 0,1.04f, -1,-1,-1,   1,0,0,   0,0,1), // top
        new Face(16,0,.70f,  -1, 1, 1,   1,0,0,   0,0,-1) // bottom
    };

    private HubHead() {}

    /** Replace a cached 64x64/64x32 skin, including integer-scale HD variants. */
    public static void setSkinPng(byte[] encoded) {
        Image candidate = null;
        try {
            if (encoded == null || encoded.length == 0) {
                clearSkin();
                return;
            }
            candidate = Image.makeFromEncoded(encoded);
            int width = candidate.getWidth(), height = candidate.getHeight();
            if (width < 64 || width % 64 != 0 || (height != width && height * 2 != width)) {
                throw new IllegalArgumentException("Expected a 64x64 or 64x32 Minecraft skin (or integer-scale HD skin)");
            }
            Image previous = skin;
            skin = candidate;
            candidate = null;
            if (previous != null) previous.close();
        } finally {
            if (candidate != null) candidate.close();
        }
    }

    /** Release cached skin when the player/session changes or the renderer closes. */
    public static void clearSkin() {
        if (skin != null) {
            skin.close();
            skin = null;
        }
    }

    public static void draw(HubDraw draw, float x, float y, float size, float yawDegrees) {
        if (!(size > 0) || !Float.isFinite(size) || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(yawDegrees)) return;
        float yaw = (float) Math.toRadians(yawDegrees % 360);
        Image texture = skin;
        drawLayer(draw, texture, x, y, size, yaw, false);
        // Do not merge the hat into the skin: transparent texels expose the real base cube.
        if (texture != null) drawLayer(draw, texture, x, y, size, yaw, true);
    }

    private static void drawLayer(HubDraw draw, Image texture, float x, float y, float size, float yaw, boolean hat) {
        float layerSize = size * (hat ? HAT_SCALE : 1);
        float half = layerSize / 2;
        float centerX = x + size / 2, centerY = y + size / 2;
        List<ProjectedFace> visible = new ArrayList<>(3);
        for (Face face : FACES) {
            Point origin = project(face.x * half, face.y * half, face.z * half, yaw);
            Point right = project(face.rx * layerSize, face.ry * layerSize, face.rz * layerSize, yaw);
            Point down = project(face.dx * layerSize, face.dy * layerSize, face.dz * layerSize, yaw);
            float determinant = right.x * down.y - right.y * down.x;
            if (determinant <= 0.0001f) continue; // CSS backface-visibility:hidden.
            visible.add(new ProjectedFace(face, origin, right, down, origin.z + (right.z + down.z) / 2));
        }
        visible.sort(Comparator.comparingDouble(ProjectedFace::depth));
        Canvas canvas = draw.canvas();
        for (ProjectedFace projected : visible) {
            Face face = projected.face;
            Point p = projected.origin, r = projected.right, d = projected.down;
            canvas.save();
            try (Paint paint = new Paint().setAntiAlias(false);
                 ColorFilter light = brightness(face.brightness)) {
                paint.setColorFilter(light);
                canvas.concat(new Matrix33(
                    r.x, d.x, centerX + p.x,
                    r.y, d.y, centerY + p.y,
                    0, 0, 1));
                Rect destination = Rect.makeXYWH(0, 0, 1, 1);
                if (texture != null) {
                    float scale = texture.getWidth() / 64f;
                    Rect source = Rect.makeXYWH((face.u + (hat ? 32 : 0)) * scale, face.v * scale, 8 * scale, 8 * scale);
                    canvas.drawImageRect(texture, source, destination, SamplingMode.DEFAULT, paint, true);
                } else if (face == FACES[0]) {
                    // Existing, licensed demo avatar; never package a personal test skin.
                    Image fallback = draw.imageResource(FALLBACK);
                    if (fallback != null) canvas.drawImageRect(fallback, Rect.makeXYWH(0, 0, fallback.getWidth(), fallback.getHeight()), destination, SamplingMode.DEFAULT, paint, true);
                    else canvas.drawRect(destination, paint.setColor(0xFF69523A));
                } else {
                    canvas.drawRect(destination, paint.setColor(face == FACES[4] ? 0xFF47372A : 0xFF69523A));
                }
            } finally {
                canvas.restore();
            }
        }
    }

    private static ColorFilter brightness(float amount) {
        return ColorFilter.makeMatrix(new ColorMatrix(
            amount,0,0,0,0,
            0,amount,0,0,0,
            0,0,amount,0,0,
            0,0,0,1,0));
    }

    private static Point project(float x, float y, float z, float yaw) {
        float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
        float cp = (float) Math.cos(PITCH), sp = (float) Math.sin(PITCH);
        float rotatedX = cy * x + sy * z;
        float rotatedZ = -sy * x + cy * z;
        // CSS transform: rotateX(-12deg) rotateY(yaw); orthographic, without perspective.
        return new Point(rotatedX, cp * y - sp * rotatedZ, sp * y + cp * rotatedZ);
    }

    private record Point(float x, float y, float z) {}
    private record ProjectedFace(Face face, Point origin, Point right, Point down, float depth) {}
    private record Face(int u, int v, float brightness,
                        float x, float y, float z,
                        float rx, float ry, float rz,
                        float dx, float dy, float dz) {}
}
