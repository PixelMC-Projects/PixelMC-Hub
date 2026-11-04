/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.render;

import com.pixelmc.hub.client.ui.HubView;
import com.pixelmc.hub.client.ui.PlayerDataChecks;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

/** Hidden GPU snapshots from synthetic protocol responses; never starts Minecraft. */
public final class NativePlayerChecks {
    private static HubView view;
    private static HubRenderer renderer;
    private static Path output;
    private static void capture(String name)throws Exception {
        glClearColor(.36f,.43f,.61f,1);glClear(GL_COLOR_BUFFER_BIT);long now=System.nanoTime();
        renderer.render(1440,1000,90,75,1,draw->view.render(draw,-100,-100,now));
        ByteBuffer pixels=MemoryUtil.memAlloc(1440*1000*4);
        try {
            glReadPixels(0,0,1440,1000,GL_RGBA,GL_UNSIGNED_BYTE,pixels);BufferedImage image=new BufferedImage(1440,1000,BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<1000;y++)for(int x=0;x<1440;x++){int i=((999-y)*1440+x)*4;image.setRGB(x,y,0xff000000|Byte.toUnsignedInt(pixels.get(i))<<16|Byte.toUnsignedInt(pixels.get(i+1))<<8|Byte.toUnsignedInt(pixels.get(i+2)));}
            ImageIO.write(image,"PNG",output.resolve(name+".png").toFile());
        }finally{MemoryUtil.memFree(pixels);}
        if(glGetError()!=GL_NO_ERROR)throw new AssertionError("GL error in "+name);
    }
    private static void click(float x,float y){view.mouseClicked(x,y,0);view.mouseReleased(x,y,0);}
    private static void frame(){glClearColor(.36f,.43f,.61f,1);glClear(GL_COLOR_BUFFER_BIT);renderer.render(1440,1000,90,75,1,draw->view.render(draw,-100,-100,System.nanoTime()));}
    private static void page(int expected)throws Exception {var field=HubView.class.getDeclaredField("page");field.setAccessible(true);if(field.getInt(view)!=expected)throw new AssertionError("Wrong page captured");}
    public static void main(String[] args)throws Exception {
        output=Path.of("../.runtime/native-previews/player-integration").toAbsolutePath().normalize();Files.createDirectories(output);
        GLFWErrorCallback errors=GLFWErrorCallback.createPrint(System.err);errors.set();if(!glfwInit())throw new AssertionError("GLFW unavailable");
        glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,4);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,6);glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
        long window=glfwCreateWindow(640,480,"Hidden player contract checks",0,0);if(window==0)throw new AssertionError("Hidden GL unavailable");glfwMakeContextCurrent(window);GL.createCapabilities();
        int texture=glGenTextures();glBindTexture(GL_TEXTURE_2D,texture);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,1440,1000,0,GL_RGBA,GL_UNSIGNED_BYTE,(ByteBuffer)null);
        int fbo=glGenFramebuffers();glBindFramebuffer(GL_FRAMEBUFFER,fbo);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,texture,0);glViewport(0,0,1440,1000);
        try {
            renderer=new HubRenderer();view=new HubView();PlayerDataChecks.populate(view);view.setSettings("always",true);view.setViewportBounds(-90,-75,1440,1000);view.open(false);
            capture("home");page(0);click(110,244);page(1);capture("titles-rich");click(352,190);capture("titles-all");click(600,310);capture("titles-long");
            var selected=HubView.class.getDeclaredField("selectedTitle");selected.setAccessible(true);if(!selected.get(view).equals("long"))throw new AssertionError("Long title not selected");
            click(1070,483);capture("title-full-text");view.dismissOverlay();frame();
            click(110,320);page(2);capture("fortunes");view.acceptLive("fortunes","read",PlayerDataChecks.record());capture("reading");view.dismissOverlay();frame();click(100,810);capture("settings");view.dismissOverlay();frame();
            view.moduleStatus("fortunes","服务器未安装此模块或协议不兼容");page(2);capture("module-unavailable");
            renderer.close();renderer=null;
            System.out.println("PASS: hidden GL live-contract home, rich/long titles, full-text detail, calendar, reading, preferences and unavailable state; PNGs at "+output);
        }finally{if(renderer!=null)renderer.close();glBindFramebuffer(GL_FRAMEBUFFER,0);glDeleteFramebuffers(fbo);glDeleteTextures(texture);glfwDestroyWindow(window);glfwTerminate();errors.close();}
    }
}
