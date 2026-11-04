/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.render;

import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import java.nio.ByteBuffer;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL21.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL32.*;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.opengl.GL40.*;

/** Physical GL snapshot; bypasses Minecraft's caches, then restores the values those caches describe. */
final class GlState {
    final int drawFramebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
    private final int readFramebuffer = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
    private final int renderbuffer = glGetInteger(GL_RENDERBUFFER_BINDING);
    private final int program = glGetInteger(GL_CURRENT_PROGRAM);
    private final int vertexArray = glGetInteger(GL_VERTEX_ARRAY_BINDING);
    private final int arrayBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING);
    private final int elementBuffer = glGetInteger(GL_ELEMENT_ARRAY_BUFFER_BINDING);
    private final int packBuffer = glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING);
    private final int unpackBuffer = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
    private final boolean indirectBuffers = GL.getCapabilities().OpenGL40 || GL.getCapabilities().GL_ARB_draw_indirect;
    private final int indirectBuffer = indirectBuffers ? glGetInteger(GL_DRAW_INDIRECT_BUFFER_BINDING) : 0;
    private final int activeTexture = glGetInteger(GL_ACTIVE_TEXTURE);
    private final int[] viewport = integers(GL_VIEWPORT, 4);
    private final int[] scissorBox = integers(GL_SCISSOR_BOX, 4);
    private final int[] polygonMode = integers(GL_POLYGON_MODE, 2);
    private final int blendSrcRgb = glGetInteger(GL_BLEND_SRC_RGB), blendDstRgb = glGetInteger(GL_BLEND_DST_RGB);
    private final int blendSrcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA), blendDstAlpha = glGetInteger(GL_BLEND_DST_ALPHA);
    private final int blendEquationRgb = glGetInteger(GL_BLEND_EQUATION_RGB), blendEquationAlpha = glGetInteger(GL_BLEND_EQUATION_ALPHA);
    private final float[] blendColor = new float[4];
    private final float[] clearColor = new float[4];
    private final double clearDepth = glGetDouble(GL_DEPTH_CLEAR_VALUE);
    private final int clearStencil = glGetInteger(GL_STENCIL_CLEAR_VALUE);
    private final boolean[] colorMask = new boolean[4];
    private final boolean depthMask = glGetBoolean(GL_DEPTH_WRITEMASK);
    private final int depthFunc = glGetInteger(GL_DEPTH_FUNC);
    private final int cullFace = glGetInteger(GL_CULL_FACE_MODE), frontFace = glGetInteger(GL_FRONT_FACE);
    private final Stencil frontStencil = new Stencil(false), backStencil = new Stencil(true);
    private final float lineWidth = glGetFloat(GL_LINE_WIDTH);
    private final int packAlignment = glGetInteger(GL_PACK_ALIGNMENT), unpackAlignment = glGetInteger(GL_UNPACK_ALIGNMENT);
    private final int packRowLength = glGetInteger(GL_PACK_ROW_LENGTH), unpackRowLength = glGetInteger(GL_UNPACK_ROW_LENGTH);
    private final int packSkipPixels = glGetInteger(GL_PACK_SKIP_PIXELS), unpackSkipPixels = glGetInteger(GL_UNPACK_SKIP_PIXELS);
    private final int packSkipRows = glGetInteger(GL_PACK_SKIP_ROWS), unpackSkipRows = glGetInteger(GL_UNPACK_SKIP_ROWS);
    private final boolean samplerObjects = GL.getCapabilities().OpenGL33 || GL.getCapabilities().GL_ARB_sampler_objects;
    private final int[] texture2d = new int[glGetInteger(GL_MAX_TEXTURE_IMAGE_UNITS)];
    private final int[] samplers = new int[texture2d.length];
    private final int[] capabilities = {GL_BLEND, GL_DEPTH_TEST, GL_STENCIL_TEST, GL_SCISSOR_TEST, GL_CULL_FACE,
        GL_FRAMEBUFFER_SRGB, GL_MULTISAMPLE, GL_DITHER, GL_POLYGON_OFFSET_FILL, GL_RASTERIZER_DISCARD,
        GL_SAMPLE_ALPHA_TO_COVERAGE, GL_SAMPLE_COVERAGE, GL_PROGRAM_POINT_SIZE};
    private final boolean[] enabled = new boolean[capabilities.length];

    GlState() {
        glGetFloatv(0x8005 /* GL_BLEND_COLOR, core since 1.4 */, blendColor);
        glGetFloatv(GL_COLOR_CLEAR_VALUE, clearColor);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer values = stack.malloc(4);
            glGetBooleanv(GL_COLOR_WRITEMASK, values);
            for (int i = 0; i < 4; i++) colorMask[i] = values.get(i) != 0;
        }
        for (int i = 0; i < capabilities.length; i++) enabled[i] = glIsEnabled(capabilities[i]);
        for (int i = 0; i < texture2d.length; i++) {
            glActiveTexture(GL_TEXTURE0 + i);
            texture2d[i] = glGetInteger(GL_TEXTURE_BINDING_2D);
            if (samplerObjects) samplers[i] = glGetIntegeri(GL_SAMPLER_BINDING, i);
        }
        glActiveTexture(activeTexture);
    }

    void restore() {
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawFramebuffer);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, readFramebuffer);
        glBindRenderbuffer(GL_RENDERBUFFER, renderbuffer);
        glUseProgram(program);
        glBindVertexArray(vertexArray);
        glBindBuffer(GL_ARRAY_BUFFER, arrayBuffer);
        // Element binding belongs to the VAO and must be restored after that VAO.
        if (vertexArray != 0 || GL.getCapabilities().GL_ARB_compatibility) glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, elementBuffer);
        glBindBuffer(GL_PIXEL_PACK_BUFFER, packBuffer);
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
        if (indirectBuffers) glBindBuffer(GL_DRAW_INDIRECT_BUFFER, indirectBuffer);
        for (int i = 0; i < texture2d.length; i++) {
            glActiveTexture(GL_TEXTURE0 + i);
            glBindTexture(GL_TEXTURE_2D, texture2d[i]);
            if (samplerObjects) glBindSampler(i, samplers[i]);
        }
        glActiveTexture(activeTexture);
        glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
        glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
        glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha);
        glBlendColor(blendColor[0], blendColor[1], blendColor[2], blendColor[3]);
        glClearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
        glClearDepth(clearDepth); glClearStencil(clearStencil);
        glColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
        glDepthMask(depthMask); glDepthFunc(depthFunc);
        frontStencil.restore(GL_FRONT); backStencil.restore(GL_BACK);
        glCullFace(cullFace); glFrontFace(frontFace);
        glPolygonMode(GL_FRONT_AND_BACK, polygonMode[0]);
        glLineWidth(lineWidth);
        glPixelStorei(GL_PACK_ALIGNMENT, packAlignment); glPixelStorei(GL_UNPACK_ALIGNMENT, unpackAlignment);
        glPixelStorei(GL_PACK_ROW_LENGTH, packRowLength); glPixelStorei(GL_UNPACK_ROW_LENGTH, unpackRowLength);
        glPixelStorei(GL_PACK_SKIP_PIXELS, packSkipPixels); glPixelStorei(GL_UNPACK_SKIP_PIXELS, unpackSkipPixels);
        glPixelStorei(GL_PACK_SKIP_ROWS, packSkipRows); glPixelStorei(GL_UNPACK_SKIP_ROWS, unpackSkipRows);
        for (int i = 0; i < capabilities.length; i++) {
            if (enabled[i]) glEnable(capabilities[i]); else glDisable(capabilities[i]);
        }
    }

    private static int[] integers(int key, int count) { int[] values = new int[count]; glGetIntegerv(key, values); return values; }

    private static final class Stencil {
        final int func, ref, valueMask, writeMask, fail, depthFail, depthPass;
        Stencil(boolean back) {
            func = glGetInteger(back ? GL_STENCIL_BACK_FUNC : GL_STENCIL_FUNC);
            ref = glGetInteger(back ? GL_STENCIL_BACK_REF : GL_STENCIL_REF);
            valueMask = glGetInteger(back ? GL_STENCIL_BACK_VALUE_MASK : GL_STENCIL_VALUE_MASK);
            writeMask = glGetInteger(back ? GL_STENCIL_BACK_WRITEMASK : GL_STENCIL_WRITEMASK);
            fail = glGetInteger(back ? GL_STENCIL_BACK_FAIL : GL_STENCIL_FAIL);
            depthFail = glGetInteger(back ? GL_STENCIL_BACK_PASS_DEPTH_FAIL : GL_STENCIL_PASS_DEPTH_FAIL);
            depthPass = glGetInteger(back ? GL_STENCIL_BACK_PASS_DEPTH_PASS : GL_STENCIL_PASS_DEPTH_PASS);
        }
        void restore(int face) {
            glStencilFuncSeparate(face, func, ref, valueMask);
            glStencilMaskSeparate(face, writeMask);
            glStencilOpSeparate(face, fail, depthFail, depthPass);
        }
    }
}
