/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.render;

import com.pixelmc.hub.client.ui.HubView;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import java.nio.ByteBuffer;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL32.*;
import static org.lwjgl.opengl.GL40.*;

/** Hidden real-GL lifecycle regression; does not launch or control Minecraft. */
public final class NativeLifecycleChecks {
    public static void main(String[] args) {
        GLFWErrorCallback errors = GLFWErrorCallback.createPrint(System.err);
        errors.set();
        if (!glfwInit()) throw new AssertionError("GLFW initialization failed");
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 6);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        long window = glfwCreateWindow(640, 480, "PixelMC-Hub hidden lifecycle checks", 0, 0);
        if (window == 0) throw new AssertionError("GL 4.6 context creation failed");
        glfwMakeContextCurrent(window); GL.createCapabilities();
        int texture = glGenTextures(); glBindTexture(GL_TEXTURE_2D, texture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 3840, 2054, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        int framebuffer = glGenFramebuffers(); glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) throw new AssertionError("Game FBO incomplete");
        int indirect = glGenBuffers(); glBindBuffer(GL_DRAW_INDIRECT_BUFFER, indirect);
        glBufferData(GL_DRAW_INDIRECT_BUFFER, 16, GL_STATIC_DRAW);
        HubView view = new HubView();
        view.setSettings("always", false);
        view.setViewportBounds(-650, -259.67f, 2560, 1369.33f);
        try {
            for (int cycle = 0; cycle < 8; cycle++) {
                view.open(true);
                long started = System.nanoTime();
                // Pretend the game last drew with a different state from Skia's defaults.
                glDisable(GL_PROGRAM_POINT_SIZE);
                glBindBuffer(GL_DRAW_INDIRECT_BUFFER, indirect);
                try (HubRenderer renderer = new HubRenderer()) {
                    for (int frame = 0; frame < 25; frame++) {
                        glClearColor(.35f, .5f, .6f, 1); glClear(GL_COLOR_BUFFER_BIT);
                        long tick = started + (frame < 20 ? frame * 16_666_667L : 3_100_000_000L + (frame - 20) * 16_666_667L);
                        renderer.render(3840, 2054, 975, 389.5f, 1.5f, draw -> view.render(draw, -100, -100, tick));
                        assertGameState(framebuffer, texture, indirect);
                    }
                    try {
                        renderer.render(3840, 2054, 975, 389.5f, 1.5f, draw -> {
                            draw.saveLayerAlpha(0, 0, 300, 220, .5f);
                            draw.save(); draw.clipRoundRect(10, 10, 200, 150, 20);
                            draw.saveLayerAlpha(20, 20, 160, 100, .4f);
                            draw.rect(20, 20, 100, 60, 0xff765bd4);
                            throw new IllegalArgumentException("intentional nested-layer failure");
                        });
                        throw new AssertionError("Drawing exception was swallowed");
                    } catch (IllegalArgumentException expected) {
                        if (!expected.getMessage().equals("intentional nested-layer failure")) throw expected;
                    }
                    assertGameState(framebuffer, texture, indirect);
                    renderer.render(3840, 2054, 975, 389.5f, 1.5f, draw -> {
                        if (draw.canvas().getSaveCount() != 2) throw new AssertionError("Root canvas save stack leaked");
                        draw.roundRect(0, 0, 150, 100, 12, 0xffebf3ff);
                    });
                }
                assertGameState(framebuffer, texture, indirect);
                System.gc();
            }
            System.out.println("PASS: GL 4.6 / 4K, eight reopen cycles, point-size and indirect-buffer isolation, nested-layer failure recovery, retained game attachments, teardown");
        } finally {
            glBindFramebuffer(GL_FRAMEBUFFER, 0); glBindBuffer(GL_DRAW_INDIRECT_BUFFER, 0);
            glDeleteBuffers(indirect); glDeleteFramebuffers(framebuffer); glDeleteTextures(texture);
            glfwDestroyWindow(window); glfwTerminate(); errors.close();
        }
    }

    private static void assertGameState(int framebuffer, int texture, int indirect) {
        if (glIsEnabled(GL_PROGRAM_POINT_SIZE)) throw new AssertionError("Hub changed GL_PROGRAM_POINT_SIZE");
        if (glGetInteger(GL_DRAW_INDIRECT_BUFFER_BINDING) != indirect) throw new AssertionError("Hub changed the game's indirect buffer");
        if (glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING) != framebuffer) throw new AssertionError("Hub changed the game's framebuffer");
        if (glGetFramebufferAttachmentParameteri(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME) != texture)
            throw new AssertionError("Hub changed the game's color attachment");
        if (glGetFramebufferAttachmentParameteri(GL_FRAMEBUFFER, GL_STENCIL_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME) != 0)
            throw new AssertionError("Hub changed the game's stencil attachment");
        int error = glGetError();
        if (error != GL_NO_ERROR) throw new AssertionError("GL error 0x" + Integer.toHexString(error));
    }
}
