/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.render;

import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import java.nio.ByteBuffer;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.*;
import static org.lwjgl.opengl.GL40.*;

/** Shared-texture visibility and containment under non-default third-party renderer state. */
public final class NativeIsolationChecks {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        check(glfwInit(),"GLFW failed");glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,4);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,6);
        glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
        long game=glfwCreateWindow(640,480,"Hidden context containment",0,0);
        check(game!=0,"Game GL context failed");glfwMakeContextCurrent(game);var caps=GL.createCapabilities();
        int texture=glGenTextures();glBindTexture(GL_TEXTURE_2D,texture);
        int fbo=glGenFramebuffers();glBindFramebuffer(GL_FRAMEBUFFER,fbo);
        int vao=glGenVertexArrays();glBindVertexArray(vao);
        int gameUniform=glGenBuffers(),otherUniform=glGenBuffers();
        for(int buffer:new int[]{gameUniform,otherUniform}){glBindBuffer(GL_UNIFORM_BUFFER,buffer);glBufferData(GL_UNIFORM_BUFFER,1024,GL_STATIC_DRAW);}
        long[] hubWindow={0};
        try(HubRenderer renderer=new HubRenderer()) {
            for(int format:new int[]{GL_RGBA8,GL_SRGB8_ALPHA8,GL_RGBA16F,GL_RGB10_A2}) {
                System.out.println("Checking shared color format 0x"+Integer.toHexString(format));
                glBindTexture(GL_TEXTURE_2D,texture);glTexImage2D(GL_TEXTURE_2D,0,format,640,480,0,GL_RGBA,GL_UNSIGNED_BYTE,(ByteBuffer)null);
                glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,texture,0);
                check(glCheckFramebufferStatus(GL_FRAMEBUFFER)==GL_FRAMEBUFFER_COMPLETE,"Game FBO failed");
                for(int frame=0;frame<90;frame++) {
                    glDisable(GL_SCISSOR_TEST);glClearColor(.15f,.25f,.35f,1);glClear(GL_COLOR_BUFFER_BIT);
                    glBindBufferBase(GL_UNIFORM_BUFFER,0,gameUniform);
                    // Independent blend state used by multiple-render-target pipelines.
                    glDisablei(GL_BLEND,0);glEnablei(GL_BLEND,1);
                    glBlendFuncSeparatei(0,GL_ONE,GL_ZERO,GL_ONE,GL_ZERO);
                    glBlendFuncSeparatei(1,GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA,GL_ONE,GL_ZERO);
                    glEnable(GL_SCISSOR_TEST);glScissor(7,9,500,400);glViewport(3,4,600,420);
                    int expected=(frame&1)==0?0xffff0000:0xff0000ff;
                    renderer.render(640,480,0,0,1,d->{
                        check(glfwGetCurrentContext()!=game,"Hub still executes inside the game's OpenGL context");
                        check(GL.getCapabilities()!=caps,"Game and Hub share the LWJGL state table");
                        if(hubWindow[0]==0)hubWindow[0]=glfwGetCurrentContext();
                        check(glfwGetCurrentContext()==hubWindow[0],"Hub context recreated per frame");
                        d.roundRect(30,30,400,300,22,0xe6ffffff);
                        d.insetBoxShadow(30,30,400,300,22,0,1,10,1,0x405d75bb);
                        d.text("独立 GPU / shared texture",50,100,20,0xff35486e,true);
                        d.rect(0,0,16,16,expected);
                        // Indexed UBO state is not part of the old GlState snapshot.
                        glBindBufferBase(GL_UNIFORM_BUFFER,0,otherUniform);
                    });
                    check(glfwGetCurrentContext()==game&&GL.getCapabilities()==caps,"Game context was not restored");
                    check(glGetIntegeri(GL_UNIFORM_BUFFER_BINDING,0)==gameUniform,"Foreign indexed buffer state leaked into the game");
                    check(!glIsEnabledi(GL_BLEND,0)&&glIsEnabledi(GL_BLEND,1)
                        &&glGetIntegeri(GL_BLEND_SRC_RGB,1)==GL_SRC_ALPHA,"Game's indexed blend state was overwritten");
                    check(glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)==fbo&&glGetInteger(GL_VERTEX_ARRAY_BINDING)==vao,"Game containers changed");
                    try(MemoryStack stack=MemoryStack.stackPush()) {
                        ByteBuffer pixel=stack.malloc(4);glReadPixels(4,475,1,1,GL_RGBA,GL_UNSIGNED_BYTE,pixel);
                        check(Byte.toUnsignedInt(pixel.get((frame&1)==0?0:2))>240,"Cross-context synchronization returned a stale frame");
                    }
                    check(glGetError()==GL_NO_ERROR,"GL error during context handoff");
                }
            }
            try {
                renderer.render(640,480,0,0,1,d->{d.saveLayerAlpha(0,0,200,200,.5f);throw new IllegalArgumentException("injected draw failure");});
                throw new AssertionError("Expected exception");
            } catch(IllegalArgumentException expected) {check(glfwGetCurrentContext()==game&&GL.getCapabilities()==caps,"Exception stranded the game in Hub's context");}
        } finally {
            check(glfwGetCurrentContext()==game,"Close stranded the game in Hub's context");
            check(glIsEnabledi(GL_BLEND,1)&&glGetIntegeri(GL_BLEND_SRC_RGB,1)==GL_SRC_ALPHA,"Close overwrote indexed blend state");
            glBindFramebuffer(GL_FRAMEBUFFER,0);glDeleteFramebuffers(fbo);glDeleteTextures(texture);
            glDeleteBuffers(gameUniform);glDeleteBuffers(otherUniform);glDeleteVertexArrays(vao);
            glfwDestroyWindow(game);glfwTerminate();
        }
        System.out.println("PASS: 360 frames, isolated GL/capabilities/indexed buffers and blend state, shared-color fences, RGBA8/sRGB/F16/10-bit targets, exception and close recovery.");
    }
}
