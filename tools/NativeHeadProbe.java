/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.render;

import com.pixelmc.hub.client.ui.HubHead;
import io.github.humbleui.skija.Data;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Surface;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Offline raster proof only; never reads a private skin or installed Minecraft instance. */
public final class NativeHeadProbe {
    private static final int[][] UV = {{8,8},{24,8},{0,8},{16,8},{8,0},{16,0}};
    private static final int[] COLORS = {0xFF31A6CB,0xFFAD68C8,0xFFDD5B7A,0xFFEDA745,0xFF7BC46A,0xFF555566};

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        byte[] base = skin(64, false, false);
        byte[] hat = skin(64, true, false);
        float[] yaws = {-90,-23,0,23,90,180};
        try (Surface surface = Surface.makeRasterN32Premul(1200, 620); HubDraw draw = new HubDraw()) {
            surface.getCanvas().clear(0xFFF3F5F3);
            draw.begin(surface.getCanvas());
            for (int row = 0; row < 3; row++) {
                if (row == 0) HubHead.setSkinPng(base);
                else if (row == 1) HubHead.setSkinPng(hat);
                else HubHead.clearSkin();
                for (int col = 0; col < yaws.length; col++) {
                    float left = col * 200 + 54, top = row * 200 + 36;
                    HubHead.draw(draw, left, top, 112, yaws[col]);
                    draw.text((row == 0 ? "base " : row == 1 ? "hat 9/8 " : "fallback ") + yaws[col] + "°", col * 200 + 20, row * 200 + 185, 14, 0xFF334433, false);
                }
            }
            draw.end();
            try (Image image = surface.makeImageSnapshot(); Data encoded = image.encodeToData()) {
                Files.write(output.resolve("head-faces.png"), encoded.getBytes());
            }
        }
        BufferedImage plain = render(base);
        BufferedImage layered = render(hat);
        if (plain.getRGB(128,140) != COLORS[0]) throw new AssertionError("Front UV/nearest color changed");
        if (plain.getRGB(128, 140) != layered.getRGB(128, 140)) throw new AssertionError("Transparent front hat must preserve base face");
        int alphaSample = layered.getRGB(68,140);
        int[] expectedBlend = {152,125,110};
        for (int channel=0;channel<3;channel++) if (Math.abs(((alphaSample >>> (16-channel*8))&255)-expectedBlend[channel])>2)
            throw new AssertionError("Half-transparent hat must alpha blend over base; got " + Integer.toHexString(alphaSample));
        int differences = 0, beyondBase = 0;
        for (int y = 0; y < 256; y++) for (int x = 0; x < 256; x++) {
            int a = plain.getRGB(x,y), b = layered.getRGB(x,y);
            if (a != b) differences++;
            if ((a >>> 24) == 0 && (b >>> 24) != 0) beyondBase++;
        }
        if (differences < 1000 || beyondBase < 500) throw new AssertionError("Hat shell must visibly expand beyond base: " + differences + "/" + beyondBase);
        // Both legacy layout and integer-scale HD layout must decode and render.
        render(skin(64, true, true));
        render(skin(128, true, false));
        boolean rejected = false;
        try { HubHead.setSkinPng(png(new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB))); }
        catch (IllegalArgumentException expected) { rejected = true; }
        if (!rejected) throw new AssertionError("Invalid skin dimensions were accepted");
        HubHead.clearSkin();
        System.out.println("Head raster checks passed: transparent hat center preserved; " + beyondBase + " outer pixels beyond base; legacy/HD accepted; malformed dimensions rejected.");
    }

    private static BufferedImage render(byte[] png) throws Exception {
        HubHead.setSkinPng(png);
        try (Surface surface = Surface.makeRasterN32Premul(256,256); HubDraw draw = new HubDraw()) {
            surface.getCanvas().clear(0x00000000);
            draw.begin(surface.getCanvas());
            HubHead.draw(draw,64,64,128,0);
            draw.end();
            try (Image image = surface.makeImageSnapshot(); Data data = image.encodeToData()) {
                return ImageIO.read(new java.io.ByteArrayInputStream(data.getBytes()));
            }
        }
    }

    private static byte[] skin(int width, boolean hat, boolean legacy) throws Exception {
        int scale = width / 64;
        BufferedImage image = new BufferedImage(width, legacy ? width / 2 : width, BufferedImage.TYPE_INT_ARGB);
        for (int face = 0; face < UV.length; face++) {
            for (int y = 0; y < 8 * scale; y++) for (int x = 0; x < 8 * scale; x++) {
                int marker = x < scale && y < scale ? 0xFFFFFFFF : COLORS[face];
                image.setRGB(UV[face][0] * scale + x, UV[face][1] * scale + y, marker);
                if (hat && (x < scale || x >= 7 * scale || y < scale || y >= 7 * scale))
                    image.setRGB((UV[face][0]+32) * scale + x, UV[face][1] * scale + y, x < scale ? 0x80FF5511 : 0xFFFFCC22);
            }
        }
        return png(image);
    }

    private static byte[] png(BufferedImage image) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image,"png",output);
        return output.toByteArray();
    }
}
