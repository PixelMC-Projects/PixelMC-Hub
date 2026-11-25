/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.ui;

import com.pixelmc.hub.client.render.HubDraw;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;
import java.util.*;

/** Orthographic, skinned podium figure. Coordinates and joint frames follow summitMesh.js. */
final class SummitPlayer {
    static final int WIDTH = 288, HEIGHT = 296;
    private static final int SCALE = WIDTH / 36;
    private static final String OUTFIT = "images/winter/summit-traveler.png";
    private static final int MAX_FRAMES = 12;
    private static final LinkedHashMap<Key, Image> frames = new LinkedHashMap<>(16, .75f, true);

    private record Key(Image skin, int pose) {
        @Override
        public boolean equals(Object other) {
            return other instanceof Key k && k.skin == skin && k.pose == pose;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(skin) * 31 + pose;
        }
    }

    private record Arm(double swing, double reach, double bend, double fold) {}

    private record Pose(
            double turn,
            double waist,
            double lean,
            Arm right,
            Arm left,
            double rightStep,
            double leftStep) {}

    private static final Pose FIRST =
            new Pose(-16, 5, -4, new Arm(22, 4, 22, -44), new Arm(-127, 10, 8, -25), 8, -12);
    private static final Pose SECOND =
            new Pose(18, 6, 4, new Arm(36, 6, 24, -70), new Arm(-8, -14, 18, 4), 12, -3);
    private static final Pose THIRD =
            new Pose(-24, -4, -2, new Arm(10, 43, 64, -78), new Arm(-10, 38, 42, 84), 3, -9);
    private static final Vec WAIST = new Vec(0, 20, 0), FEET = new Vec(0, 32, 0);

    private record Vec(double x, double y, double z) {
        Vec add(Vec v) {
            return new Vec(x + v.x, y + v.y, z + v.z);
        }

        Vec sub(Vec v) {
            return new Vec(x - v.x, y - v.y, z - v.z);
        }

        Vec rx(double deg) {
            double a = Math.toRadians(deg), c = Math.cos(a), s = Math.sin(a);
            return new Vec(x, y * c - z * s, y * s + z * c);
        }

        Vec ry(double deg) {
            double a = Math.toRadians(deg), c = Math.cos(a), s = Math.sin(a);
            return new Vec(x * c + z * s, y, -x * s + z * c);
        }

        Vec rz(double deg) {
            double a = Math.toRadians(deg), c = Math.cos(a), s = Math.sin(a);
            return new Vec(x * c - y * s, x * s + y * c, z);
        }
    }

    private interface Ring {
        Vec at(double y, Vec rim, double edge);
    }

    private record UV(double u, double v) {}

    private record Face(Vec a, Vec b, Vec c, Vec d, UV ta, UV tb, UV tc, UV td) {
        double depth() {
            return (a.z + b.z + c.z + d.z) / 4;
        }
    }

    private final List<Face> faces = new ArrayList<>();
    private final Pose pose;
    private final boolean modern;
    private final int armWidth;

    static void draw(
            HubDraw draw,
            String player,
            boolean own,
            int rank,
            float center,
            float bottom,
            float unit) {
        Image skin = HubHead.playerSkin(player, own);
        if (skin == null) skin = draw.imageResource(OUTFIT);
        int pose = rank == 1 ? 1 : rank == 3 ? 3 : 2;
        Key key = new Key(skin, pose);
        Image frame = frames.get(key);
        if (frame == null) {
            frame = render(skin, pose);
            if (frames.size() >= MAX_FRAMES)
                frames.remove(frames.keySet().iterator().next()).close();
            frames.put(key, frame);
        }
        draw.canvas()
                .drawImageRect(
                        frame,
                        Rect.makeWH(WIDTH, HEIGHT),
                        Rect.makeXYWH(center - 18 * unit, bottom - 37 * unit, 36 * unit, 37 * unit),
                        SamplingMode.LINEAR,
                        null,
                        true);
    }

    static void clear() {
        SummitStage.clear();
        frames.values().forEach(Image::close);
        frames.clear();
    }

    private static ImageInfo rgba(int width, int height) {
        return new ImageInfo(
                width, height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL, ColorSpace.getSRGB());
    }

    static Image render(Image skin, int rank) {
        int width = skin.getWidth(), height = skin.getHeight();
        byte[] texture;
        try (Bitmap pixels = Bitmap.makeFromImage(skin)) {
            texture = pixels.readPixels(rgba(width, height), (long) width * 4, 0, 0);
        }
        SummitPlayer model = new SummitPlayer(texture, width, height, rank);
        return Image.makeRaster(
                rgba(WIDTH, HEIGHT), model.rasterize(texture, width, height), WIDTH * 4L);
    }

    private SummitPlayer(byte[] skin, int width, int height, int rank) {
        pose = rank == 1 ? FIRST : rank == 3 ? THIRD : SECOND;
        modern = width == height;
        armWidth = modern && slim(skin, width) ? 3 : 4;
        rigid(8, 8, 12, 4, 16, 16, 16, 32, .18, false);
        rigid(.18, 8, 8, 8, 0, 0, 32, 0, .45, true);
        for (boolean arm : new boolean[] {false, true})
            for (boolean left : new boolean[] {false, true}) limb(arm, left);
    }

    private static boolean slim(byte[] texture, int width) {
        int f = width / 64;
        for (int y = 20 * f; y < 32 * f; y++)
            for (int x = 54 * f; x < 56 * f; x++)
                if (texture[(y * width + x) * 4 + 3] != 0) return false;
        return true;
    }

    private Vec torso(Vec p) {
        return WAIST.add(p.sub(WAIST).ry(pose.waist).rz(pose.lean));
    }

    private void rigid(
            double top,
            int w,
            int h,
            int d,
            int u,
            int v,
            int outerU,
            int outerV,
            double puff,
            boolean hat) {
        Ring ring = (y, r, edge) -> torso(new Vec(0, top + y + edge, 0).add(r));
        double[] stops = {0, h};
        surface(w, d, u, v, 0, stops, ring, false);
        if (modern || hat) surface(w, d, outerU, outerV, puff, stops, ring, false);
    }

    private void limb(boolean arm, boolean left) {
        int width = arm ? armWidth : 4, sign = left ? 1 : -1;
        boolean mirror = left && !modern;
        double cx = sign * (arm ? 4 + width / 2.0 - .45 : 2), top = arm ? 8 : 19.75;
        Arm angles =
                arm
                        ? (left ? pose.left : pose.right)
                        : new Arm(left ? pose.leftStep : pose.rightStep, left ? -5 : 5, -7, 0);
        Vec pivot = new Vec(arm ? -sign * (width / 2.0 - .45) : 0, arm ? .85 : .5, 0);
        double band =
                Math.min(3.6, Math.max(1, (Math.abs(angles.bend) + Math.abs(angles.fold)) / 37));
        double[] stops = {0, 6 - band, 6, 6 + band, 12};
        Ring ring =
                (y, r, edge) -> {
                    Vec center =
                            y <= 6
                                    ? new Vec(0, y, 0)
                                    : new Vec(0, 6, 0)
                                            .add(
                                                    new Vec(0, y - 6, 0)
                                                            .rz(angles.fold)
                                                            .rx(angles.bend));
                    double weight = Math.max(0, Math.min(1, (y - 6 + band) / (2 * band)));
                    Vec local =
                            center.add(
                                    new Vec(r.x, edge, r.z)
                                            .rz(angles.fold * weight)
                                            .rx(angles.bend * weight));
                    Vec point =
                            new Vec(cx, top, 0)
                                    .add(
                                            pivot.add(
                                                    local.sub(pivot)
                                                            .rx(angles.reach)
                                                            .rz(angles.swing)));
                    return arm ? torso(point) : point;
                };
        int u = arm ? (left && !mirror ? 32 : 40) : (left && !mirror ? 16 : 0),
                v = left && !mirror ? 48 : 16;
        surface(width, 4, u, v, 0, stops, ring, mirror);
        if (modern)
            surface(width, 4, arm ? (left ? 48 : 40) : 0, left ? 48 : 32, .18, stops, ring, false);
    }

    private Vec project(Vec p) {
        Vec view = FEET.add(p.sub(FEET).ry(pose.turn).rx(-5));
        return new Vec((18 + view.x) * SCALE, (3.6 + view.y) * SCALE, view.z);
    }

    // Shared rings bend a continuous sleeve/pant leg. Only its two ends receive caps.
    private void surface(
            int w, int d, int u, int v, double puff, double[] stops, Ring ring, boolean mirror) {
        double halfW = w / 2.0 + puff, halfD = d / 2.0 + puff;
        Vec[] rim = {
            new Vec(-halfW, 0, halfD),
            new Vec(halfW, 0, halfD),
            new Vec(halfW, 0, -halfD),
            new Vec(-halfW, 0, -halfD)
        };
        Vec[][] points = new Vec[stops.length][4];
        for (int j = 0; j < stops.length; j++)
            for (int k = 0; k < 4; k++) {
                double edge = j == 0 ? -puff : j == stops.length - 1 ? puff : 0;
                points[j][k] = project(ring.at(stops[j], rim[k], edge));
            }
        int[] starts = {u + d, u + d + w, u + 2 * d + w, u};
        for (int j = 0; j < stops.length - 1; j++)
            for (int k = 0; k < 4; k++) {
                int n = (k + 1) % 4, mapped = mirror && k % 2 == 1 ? (k + 2) % 4 : k;
                quad(
                        points[j][k],
                        points[j][n],
                        points[j + 1][n],
                        points[j + 1][k],
                        starts[mapped],
                        v + d + stops[j],
                        mapped % 2 == 0 ? w : d,
                        stops[j + 1] - stops[j],
                        mirror);
            }
        Vec[] first = points[0], last = points[points.length - 1];
        quad(first[3], first[2], first[1], first[0], u + d, v, w, d, mirror);
        quad(last[0], last[1], last[2], last[3], u + d + w, v, w, d, mirror);
    }

    private void quad(
            Vec a,
            Vec b,
            Vec c,
            Vec d,
            double u,
            double v,
            double width,
            double height,
            boolean flip) {
        double left = flip ? u + width : u, right = flip ? u : u + width;
        faces.add(
                new Face(
                        a,
                        b,
                        c,
                        d,
                        new UV(left, v),
                        new UV(right, v),
                        new UV(right, v + height),
                        new UV(left, v + height)));
    }

    private byte[] rasterize(byte[] skin, int width, int height) {
        byte[] frame = new byte[WIDTH * HEIGHT * 4];
        float[] depth = new float[WIDTH * HEIGHT];
        Arrays.fill(depth, Float.NEGATIVE_INFINITY);
        faces.sort(Comparator.comparingDouble(Face::depth));
        for (Face face : faces) {
            Vec ab = face.b.sub(face.a), ad = face.d.sub(face.a);
            double nx = (ab.y * ad.z - ab.z * ad.y) / SCALE,
                    ny = (ab.z * ad.x - ab.x * ad.z) / SCALE;
            double nz = (ab.x * ad.y - ab.y * ad.x) / (SCALE * SCALE);
            double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
            double shade =
                    Math.min(
                            1.07,
                            Math.max(
                                    .72,
                                    .85
                                            + (.16 * nz - .14 * ny - .07 * nx)
                                                    / (length == 0 ? 1 : length)));
            triangle(
                    face.a, face.b, face.c, face.ta, face.tb, face.tc, shade, skin, width, height,
                    frame, depth);
            triangle(
                    face.a, face.c, face.d, face.ta, face.tc, face.td, shade, skin, width, height,
                    frame, depth);
        }
        return frame;
    }

    private static void triangle(
            Vec a,
            Vec b,
            Vec c,
            UV ta,
            UV tb,
            UV tc,
            double shade,
            byte[] skin,
            int width,
            int height,
            byte[] frame,
            float[] depth) {
        double dx = b.x - a.x,
                dy = b.y - a.y,
                ex = c.x - a.x,
                ey = c.y - a.y,
                area = dx * ey - dy * ex;
        if (area < 1e-5) return;
        int x0 = Math.max(0, (int) Math.floor(Math.min(a.x, Math.min(b.x, c.x)))),
                x1 = Math.min(WIDTH, (int) Math.ceil(Math.max(a.x, Math.max(b.x, c.x))));
        int y0 = Math.max(0, (int) Math.floor(Math.min(a.y, Math.min(b.y, c.y)))),
                y1 = Math.min(HEIGHT, (int) Math.ceil(Math.max(a.y, Math.max(b.y, c.y))));
        double factor = width / 64.0;
        for (int y = y0; y < y1; y++)
            for (int x = x0; x < x1; x++) {
                double rx = x + .5 - a.x,
                        ry = y + .5 - a.y,
                        s = (rx * ey - ry * ex) / area,
                        t = (ry * dx - rx * dy) / area;
                if (s < 0 || t < 0 || s + t > 1) continue;
                int pixel = y * WIDTH + x;
                double z = a.z + s * (b.z - a.z) + t * (c.z - a.z);
                if (z <= depth[pixel]) continue;
                int u =
                        Math.min(
                                width - 1,
                                (int)
                                        Math.floor(
                                                (ta.u + s * (tb.u - ta.u) + t * (tc.u - ta.u))
                                                        * factor));
                int v =
                        Math.min(
                                height - 1,
                                (int)
                                        Math.floor(
                                                (ta.v + s * (tb.v - ta.v) + t * (tc.v - ta.v))
                                                        * factor));
                int source = (v * width + u) * 4, out = pixel * 4;
                double alpha = (skin[source + 3] & 255) / 255.0;
                if (alpha == 0) continue;
                double old = (frame[out + 3] & 255) / 255.0 * (1 - alpha), opacity = alpha + old;
                for (int ch = 0; ch < 3; ch++) {
                    double color =
                            ((skin[source + ch] & 255) * shade * alpha
                                            + (frame[out + ch] & 255) * old)
                                    / opacity;
                    frame[out + ch] = (byte) Math.min(255, Math.rint(color));
                }
                frame[out + 3] = (byte) Math.rint(opacity * 255);
                depth[pixel] = (float) z;
            }
    }
}
