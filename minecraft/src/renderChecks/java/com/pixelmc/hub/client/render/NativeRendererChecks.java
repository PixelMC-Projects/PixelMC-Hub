/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.render;

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.Arrays;
import io.github.humbleui.skija.TextLine;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/** Hidden real GPU check, deliberately outside the packaged mod source set. */
public final class NativeRendererChecks {
    public static void main(String[] args) throws Exception {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new AssertionError("GLFW initialization failed");
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        long window = glfwCreateWindow(640, 480, "PixelMC-Hub hidden renderer checks", 0, 0);
        if (window == 0) throw new AssertionError("GLFW context creation failed");
        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        System.out.println("GPU: " + glGetString(GL_RENDERER) + "; GL " + glGetString(GL_VERSION));
        int texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 640, 480, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        int framebuffer = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) throw new AssertionError("Test FBO incomplete");
        glViewport(0, 0, 640, 480);
        glClearColor(0.12f, 0.22f, 0.32f, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        int vao = glGenVertexArrays(); glBindVertexArray(vao);
        glEnable(GL_SCISSOR_TEST); glScissor(7, 8, 620, 450);
        glEnable(GL_BLEND); glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO);
        glEnable(GL_DEPTH_TEST); glDepthMask(true); glDepthFunc(GL_LEQUAL);
        glActiveTexture(GL_TEXTURE3); glBindTexture(GL_TEXTURE_2D, texture);
        int initialStencil = glGetFramebufferAttachmentParameteri(GL_FRAMEBUFFER, GL_STENCIL_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        GlState before = new GlState();
        HubRenderer renderer = new HubRenderer();
        renderer.render(640, 480, 10, 10, 0.5f, d -> {
            d.roundRect(0, 0, 400, 220, 20, 0xCCFFFFFF);
            d.gradientRoundRect(20, 20, 150, 80, 12, 0xFFFF0000, 0xFF0000FF);
            d.save(); d.clipRoundRect(180, 20, 60, 60, 12);
            d.circle(210, 50, 45, 0xFF00FF00); d.restore();
            d.strokeRoundRect(20, 120, 200, 60, 8, 2, 0xFF112233);
            d.line(25, 150, 200, 150, 2, 0xFF112233);
            d.text("PixelMC 中文签笺", 250, 70, 28, 0xFF203040, false);
            d.text("粗体与字体回退", 250, 105, 24, 0xFF203040, true);
            d.textSerif("签笺诗文", 250, 140, 24, 0xFF203040, false);
            d.icon("home", 480, 160, 32, 0xFF4466CC);
            d.image("textures/ui/avatar.png", 530, 120, 48, 48, 0.7f);
            if (!(d.textWidth("中文签笺", 24, false) > 70)) throw new AssertionError("CJK text measurement failed");
            try {
                var method = HubDraw.class.getDeclaredMethod("textLine", String.class, float.class, boolean.class, boolean.class);
                method.setAccessible(true);
                for (boolean serif : new boolean[]{false, true}) {
                    TextLine line = (TextLine) method.invoke(d, "签笺 PixelMC，星光眷顾", 24f, false, serif);
                    for (short glyph : line.getGlyphs()) if (glyph == 0) throw new AssertionError("Missing CJK glyph, serif=" + serif);
                }
            } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
        });
        compare(before, new GlState());
        if (glGetFramebufferAttachmentParameteri(GL_FRAMEBUFFER, GL_STENCIL_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME) != initialStencil)
            throw new AssertionError("Renderer changed game's stencil attachment");
        // Readback is verification only; the production renderer has no CPU framebuffer readback.
        glDisable(GL_SCISSOR_TEST);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer pixel = stack.malloc(4);
            glReadPixels(20, 480 - 20, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
            if (Byte.toUnsignedInt(pixel.get(0)) < 180) throw new AssertionError("Skija draw did not reach shared game color texture");
        }
        // Mutate the game color texture between frames, then verify the live GPU backdrop is actually blurred.
        glClearColor(0.12f, 0.22f, 0.32f, 1); glClear(GL_COLOR_BUFFER_BIT);
        glEnable(GL_SCISSOR_TEST); glScissor(200, 100, 20, 20);
        glClearColor(1, 0, 0, 1); glClear(GL_COLOR_BUFFER_BIT); glDisable(GL_SCISSOR_TEST);
        renderer.render(640, 480, 10, 10, 0.5f, d -> d.blurBackdrop(0,0,1260,850,0,24,1));
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer pixel = stack.malloc(4);
            glReadPixels(210, 110, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
            int red = Byte.toUnsignedInt(pixel.get(0));
            if (red < 80 || red > 235) throw new AssertionError("Live backdrop blur did not mix the patch: red=" + red);
        }
        GlState beforeException = new GlState();
        try {
            renderer.render(640, 480, 10, 10, 0.5f, d -> { d.save(); throw new IllegalArgumentException("check recovery"); });
            throw new AssertionError("Expected drawing failure was swallowed");
        } catch (IllegalArgumentException expected) { /* The caller still sees its drawing failure. */ }
        compare(beforeException, new GlState());
        for (int i = 0; i < 20; i++) renderer.render(640, 480, 10, 10, 0.5f, d -> d.rect(0, 0, 10, 10, 0xFF112233));
        renderer.render(320, 240, 4, 4, 0.25f, d -> d.circle(40, 40, 10, 0xFF556677));
        renderer.close(); renderer.close();
        int error = glGetError();
        if (error != GL_NO_ERROR) throw new AssertionError("GL error after lifecycle checks: 0x" + Integer.toHexString(error));
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glDeleteFramebuffers(framebuffer); glDeleteTextures(texture); glDeleteVertexArrays(vao);
        glfwDestroyWindow(window); glfwTerminate();
        System.out.println("PASS: real JNI/GPU draw, CJK/serif glyphs, image/icon tint, live backdrop blur, rounded clip, gradient, state/exception recovery, untouched game stencil, resize, repeated close");
    }

    private static void compare(Object before, Object after) throws Exception {
        for (Field field : before.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            Object left = field.get(before), right = field.get(after);
            boolean equal;
            if (left instanceof int[] a) equal = Arrays.equals(a, (int[]) right);
            else if (left instanceof float[] a) equal = Arrays.equals(a, (float[]) right);
            else if (left instanceof boolean[] a) equal = Arrays.equals(a, (boolean[]) right);
            else if (left.getClass().getName().contains("GlState$")) { compare(left, right); continue; }
            else equal = left.equals(right);
            if (!equal) throw new AssertionError("GL state was not restored: " + field.getName() + ": " + left + " -> " + right);
        }
    }
}
