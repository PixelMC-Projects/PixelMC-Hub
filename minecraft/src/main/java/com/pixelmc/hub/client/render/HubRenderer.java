/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.render;

import io.github.humbleui.skija.*;
import io.github.humbleui.skija.impl.Library;
import io.github.humbleui.types.RRect;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import java.util.Objects;
import java.util.function.Consumer;
import static org.lwjgl.glfw.GLFW.glfwGetCurrentContext;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

/** One lazy Skija GPU context. Call render/close on Minecraft's render thread. */
public final class HubRenderer implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private DirectContext context;
    private BackendRenderTarget target;
    private Surface surface;
    private HubDraw draw;
    private Paint backdropPaint;
    private ImageFilter backdropBlur;
    private float blurScale = -1;
    private int width, height, sourceFramebuffer = -1, sourceColor = -1;
    private int aliasFramebuffer, stencilBuffer;
    private long glContext;
    private Thread renderThread;
    private boolean closed;
    private boolean firstFrame = true;

    public void render(int framebufferWidth, int framebufferHeight, float panelX, float panelY,
                       float scale, Consumer<HubDraw> content) {
        Objects.requireNonNull(content, "content");
        if (closed) throw new IllegalStateException("HubRenderer was closed");
        if (framebufferWidth <= 0 || framebufferHeight <= 0 || !Float.isFinite(scale) || scale <= 0) return;
        long currentContext = glfwGetCurrentContext();
        if (currentContext == 0) throw new IllegalStateException("Hub rendering requires the current Minecraft OpenGL context");
        if (renderThread != null && (renderThread != Thread.currentThread() || currentContext != glContext))
            throw new IllegalStateException("HubRenderer must stay on its original render thread and GL context");

        stage("capturing Minecraft GL state");
        GlState previous = new GlState();
        checkFirstFrameGl("state capture");
        Canvas canvas = null;
        int saveCount = 0;
        try {
            if (context == null) {
                stage("loading bundled Skija JNI");
                Library.load(); // Upstream loader extracts the bundled DLL and ICU data, without network access.
                stage("creating Skija OpenGL context");
                context = DirectContext.makeGL();
                checkFirstFrameGl("Skija context creation");
                renderThread = Thread.currentThread(); glContext = currentContext;
                stage("creating local font and image resources");
                draw = new HubDraw();
                backdropPaint = new Paint().setAntiAlias(true);
            }
            context.resetGLAll();
            stage("preparing Minecraft color target");
            prepareTarget(previous.drawFramebuffer, framebufferWidth, framebufferHeight);
            checkFirstFrameGl("target preparation");
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, aliasFramebuffer != 0 ? aliasFramebuffer : previous.drawFramebuffer);
            glViewport(0, 0, framebufferWidth, framebufferHeight);
            // The game writes this color attachment between frames. Invalidate Skia's previous snapshot cache.
            surface.notifyContentWillChange(ContentChangeMode.RETAIN);
            canvas = surface.getCanvas();
            saveCount = canvas.save();
            canvas.resetMatrix();
            stage("capturing GPU backdrop and scheduling blur");
            backdrop(canvas, panelX, panelY, scale);
            checkFirstFrameGl("GPU backdrop");
            canvas.translate(panelX, panelY).scale(scale, scale);
            draw.begin(canvas);
            stage("drawing Hub content");
            content.accept(draw);
            stage("Hub content scheduled");
        } finally {
            try {
                if (canvas != null) canvas.restoreToCount(saveCount);
                if (draw != null) draw.end();
                if (context != null && surface != null) {
                    stage("flushing Skija GPU commands");
                    context.flushAndSubmit(surface);
                    checkFirstFrameGl("GPU flush");
                }
            } finally {
                previous.restore();
            }
        }
        stage("first native frame complete");
        firstFrame = false;
    }

    private void stage(String message) {
        if (firstFrame) LOGGER.info("PixelMC-Hub native initialization: {}", message);
    }

    private void checkFirstFrameGl(String stage) {
        if (!firstFrame) return;
        int error = glGetError();
        if (error != GL_NO_ERROR)
            throw new IllegalStateException("OpenGL error after Hub " + stage + ": 0x" + Integer.toHexString(error));
    }

    private void backdrop(Canvas canvas, float x, float y, float scale) {
        if (Float.compare(blurScale, scale) != 0) {
            backdropPaint.setImageFilter(null);
            if (backdropBlur != null) backdropBlur.close();
            float sigma = Math.min(24, Math.max(1, 15 * scale));
            backdropBlur = ImageFilter.makeBlur(sigma, sigma, FilterTileMode.CLAMP);
            backdropPaint.setImageFilter(backdropBlur);
            blurScale = scale;
        }
        // GPU copy-on-write snapshot: no glReadPixels, CPU readback, or framebuffer-size upload.
        // Skia limits the blur result to the rounded panel clip and its required filter padding.
        try (Image background = surface.makeImageSnapshot()) {
            int count = canvas.save();
            canvas.clipRRect(RRect.makeXYWH(x, y, 1260 * scale, 850 * scale, 28 * scale), true);
            canvas.drawImage(background, 0, 0, backdropPaint);
            canvas.restoreToCount(count);
        }
    }

    private void prepareTarget(int framebuffer, int newWidth, int newHeight) {
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
        int color = framebuffer == 0 ? 0 : glGetFramebufferAttachmentParameteri(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        if (surface != null && width == newWidth && height == newHeight && sourceFramebuffer == framebuffer && sourceColor == color) return;
        releaseTarget();
        width = newWidth; height = newHeight; sourceFramebuffer = framebuffer; sourceColor = color;
        int targetFramebuffer = framebuffer;
        // GL_STENCIL_BITS is a removed legacy glGet query in Minecraft's core profile.
        // Our alias FBO owns an 8-bit stencil; only the default framebuffer needs an attachment query.
        int stencilBits = framebuffer == 0 ? glGetFramebufferAttachmentParameteri(
            GL_FRAMEBUFFER, GL_STENCIL, GL_FRAMEBUFFER_ATTACHMENT_STENCIL_SIZE) : 8;
        if (framebuffer != 0) {
            int type = glGetFramebufferAttachmentParameteri(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
            if (type != GL_TEXTURE) throw new IllegalStateException("Hub expects Minecraft's texture-backed main framebuffer");
            int level = glGetFramebufferAttachmentParameteri(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL);
            // A separate FBO shares ONLY the game's color texture. The game's depth/stencil attachments stay untouched.
            aliasFramebuffer = glGenFramebuffers();
            glBindFramebuffer(GL_FRAMEBUFFER, aliasFramebuffer);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, color, level);
            stencilBuffer = glGenRenderbuffers();
            glBindRenderbuffer(GL_RENDERBUFFER, stencilBuffer);
            glRenderbufferStorage(GL_RENDERBUFFER, GL_STENCIL_INDEX8, width, height);
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_STENCIL_ATTACHMENT, GL_RENDERBUFFER, stencilBuffer);
            if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
                throw new IllegalStateException("Cannot create Hub's isolated stencil framebuffer");
            targetFramebuffer = aliasFramebuffer;
        }
        if (firstFrame) LOGGER.info("PixelMC-Hub native target: sourceFbo={}, aliasFbo={}, colorTexture={}, size={}x{}, stencil={}",
            framebuffer, aliasFramebuffer, color, width, height, stencilBits);
        checkFirstFrameGl("framebuffer attachment setup");
        target = BackendRenderTarget.makeGL(width, height, 0, stencilBits, targetFramebuffer, FramebufferFormat.GR_GL_RGBA8);
        stage("wrapping Skija GPU surface");
        surface = Surface.wrapBackendRenderTarget(context, target, SurfaceOrigin.BOTTOM_LEFT, ColorType.RGBA_8888, ColorSpace.getSRGB());
    }

    private void releaseTarget() {
        if (surface != null) { surface.close(); surface = null; }
        if (target != null) { target.close(); target = null; }
        if (glfwGetCurrentContext() == glContext) {
            if (aliasFramebuffer != 0) glDeleteFramebuffers(aliasFramebuffer);
            if (stencilBuffer != 0) glDeleteRenderbuffers(stencilBuffer);
        }
        aliasFramebuffer = 0; stencilBuffer = 0;
    }

    @Override public void close() {
        if (closed) return;
        if (renderThread != null && Thread.currentThread() != renderThread)
            throw new IllegalStateException("Close HubRenderer on its render thread");
        closed = true;
        if (context == null) return;
        boolean hasContext = glfwGetCurrentContext() == glContext;
        GlState previous = hasContext ? new GlState() : null;
        try {
            if (!hasContext) context.abandon();
            if (draw != null) draw.close();
            if (backdropPaint != null) backdropPaint.close();
            if (backdropBlur != null) backdropBlur.close();
            releaseTarget();
            context.close();
        } finally {
            if (previous != null) previous.restore();
        }
    }
}
