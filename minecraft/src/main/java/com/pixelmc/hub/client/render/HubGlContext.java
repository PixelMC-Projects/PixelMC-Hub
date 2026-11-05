/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.render;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.glFlush;
import static org.lwjgl.opengl.GL11.glFinish;
import static org.lwjgl.opengl.GL32.*;

/** Separate GL state/VAO namespace; only texture storage and GPU fences are shared with Minecraft. */
final class HubGlContext implements AutoCloseable {
    private final long gameWindow;
    private final Thread owner = Thread.currentThread();
    private long window;
    private GLCapabilities capabilities, gameCapabilities;
    private boolean entered;

    HubGlContext(long gameWindow) {
        this.gameWindow = gameWindow;
        requireGame();
        gameCapabilities = GL.getCapabilities();
        try {
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            glfwWindowHint(GLFW_FOCUSED, GLFW_FALSE);
            glfwWindowHint(GLFW_FOCUS_ON_SHOW, GLFW_FALSE);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2);
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
            window = glfwCreateWindow(1, 1, "PixelMC-Hub GPU", 0, gameWindow);
            if (window == 0) throw new IllegalStateException("Cannot create an isolated Hub GPU context");
            glfwMakeContextCurrent(window);
            capabilities = GL.createCapabilities();
        } catch (Throwable failure) {
            glfwMakeContextCurrent(gameWindow);
            GL.setCapabilities(gameCapabilities);
            if (window != 0) glfwDestroyWindow(window);
            window = 0;
            throw failure;
        } finally {
            glfwMakeContextCurrent(gameWindow);
            GL.setCapabilities(gameCapabilities);
            glfwDefaultWindowHints();
        }
    }

    private void requireGame() {
        if (Thread.currentThread() != owner || glfwGetCurrentContext() != gameWindow)
            throw new IllegalStateException("Hub GPU handoff requires the original game render context");
    }

    void enter() {
        requireGame();
        if (entered || window == 0) throw new IllegalStateException("Hub GPU context unavailable or already entered");
        gameCapabilities = GL.getCapabilities();
        long fence = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        if (fence == 0) throw new IllegalStateException("Cannot synchronize the game color attachment");
        glFlush();
        try {
            glfwMakeContextCurrent(window);
            GL.setCapabilities(capabilities);
            glWaitSync(fence, 0, GL_TIMEOUT_IGNORED);
            entered = true;
        } finally {
            glDeleteSync(fence);
            if (!entered) {
                glfwMakeContextCurrent(gameWindow);
                GL.setCapabilities(gameCapabilities);
            }
        }
    }

    void leave() {
        if (!entered) return;
        if (Thread.currentThread() != owner || glfwGetCurrentContext() != window)
            throw new IllegalStateException("Hub GPU handoff is on the wrong context");
        long fence = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        if (fence == 0) {
            // A failed synchronization must not let the game sample unfinished Hub rendering.
            try { glFinish(); }
            finally { restoreGame(); }
            throw new IllegalStateException("Cannot synchronize the completed Hub frame");
        }
        glFlush();
        restoreGame();
        try { glWaitSync(fence, 0, GL_TIMEOUT_IGNORED); }
        finally { glDeleteSync(fence); }
    }

    private void restoreGame() {
        glfwMakeContextCurrent(gameWindow);
        GL.setCapabilities(gameCapabilities);
        entered = false;
    }

    @Override public void close() {
        if (window == 0) return;
        if (entered) leave();
        requireGame();
        glfwDestroyWindow(window);
        window = 0;
        capabilities = null;
    }
}
