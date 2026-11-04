/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.ui;

import com.google.gson.*;
import com.pixelmc.hub.client.render.HubRenderer;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.lang.reflect.*;
import javax.imageio.ImageIO;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

/** Synthetic hidden rendering only. Does not start or control Minecraft. */
public final class NativePolishChecks {
    private static final Path OUTPUT=Path.of("../.runtime/native-previews/polish-0.2.2").toAbsolutePath().normalize();
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static Object field(HubView view,String name)throws Exception{Field f=HubView.class.getDeclaredField(name);f.setAccessible(true);return f.get(view);}
    private static void page(HubView view,int page)throws Exception{Method m=HubView.class.getDeclaredMethod("navigate",int.class);m.setAccessible(true);m.invoke(view,page);}
    private static BufferedImage render(HubRenderer renderer,HubView view,long now){
        glDisable(GL_SCISSOR_TEST);glClearColor(.52f,.64f,.82f,1);glClear(GL_COLOR_BUFFER_BIT);
        renderer.render(1440,1000,90,75,1,d->view.render(d,-100,-100,now));
        ByteBuffer bytes=MemoryUtil.memAlloc(1440*1000*4);
        try {glReadPixels(0,0,1440,1000,GL_RGBA,GL_UNSIGNED_BYTE,bytes);BufferedImage image=new BufferedImage(1440,1000,BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<1000;y++)for(int x=0;x<1440;x++){int i=((999-y)*1440+x)*4;image.setRGB(x,y,0xff000000|Byte.toUnsignedInt(bytes.get(i))<<16|Byte.toUnsignedInt(bytes.get(i+1))<<8|Byte.toUnsignedInt(bytes.get(i+2)));}
            check(glGetError()==GL_NO_ERROR,"Toast or refresh caused a GL error");return image;
        }finally{MemoryUtil.memFree(bytes);}
    }
    private static void save(BufferedImage image,String name)throws Exception{ImageIO.write(image,"PNG",OUTPUT.resolve(name+".png").toFile());}
    private static void same(BufferedImage a,BufferedImage b,int x,int y,int w,int h){
        for(int yy=y;yy<y+h;yy++)for(int xx=x;xx<x+w;xx++)if(a.getRGB(xx,yy)!=b.getRGB(xx,yy))throw new AssertionError("Cached UI flashed at "+xx+","+yy);
    }
    public static void main(String[] args)throws Exception{
        Files.createDirectories(OUTPUT);GLFWErrorCallback errors=GLFWErrorCallback.createPrint(System.err);errors.set();check(glfwInit(),"GLFW failed");
        glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,4);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,6);glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
        long window=glfwCreateWindow(640,480,"Hidden polish checks",0,0);check(window!=0,"No hidden context");glfwMakeContextCurrent(window);GL.createCapabilities();
        int texture=glGenTextures(),fbo=glGenFramebuffers(),vao=glGenVertexArrays();glBindVertexArray(vao);glBindTexture(GL_TEXTURE_2D,texture);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,1440,1000,0,GL_RGBA,GL_UNSIGNED_BYTE,(ByteBuffer)null);
        glBindFramebuffer(GL_FRAMEBUFFER,fbo);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,texture,0);glViewport(0,0,1440,1000);
        try(HubRenderer renderer=new HubRenderer()){
            HubView view=new HubView();PlayerDataChecks.populate(view);view.setSettings("login",true);view.setViewportBounds(-90,-75,1440,1000);view.open(false);page(view,1);long now=System.nanoTime();
            BufferedImage before=render(renderer,view,now);view.catalogueInvalidated();same(before,render(renderer,view,now),326,175,996,720);
            JsonObject first=PlayerDataChecks.titles();first.add("titles",new JsonArray());first.addProperty("nextCursor","next");view.acceptLive("titles","snapshot",first);
            BufferedImage pending=render(renderer,view,now);same(before,pending,326,175,996,720);save(pending,"titles-refresh-retained");
            view.acceptLive("titles","catalogue",PlayerDataChecks.titles());view.catalogueComplete();same(before,render(renderer,view,now),326,175,996,720);
            page(view,2);JsonObject calendar=new JsonObject();calendar.addProperty("month","2026-11");JsonArray rows=new JsonArray();rows.add(PlayerDataChecks.record());calendar.add("records",rows);view.acceptLive("fortunes","calendar",calendar);
            before=render(renderer,view,now);view.pending("fortunes",true);view.acceptLive("fortunes","feature",PlayerDataChecks.fortunes());
            pending=render(renderer,view,now);same(before,pending,326,249,662,626);save(pending,"calendar-feature-retained");view.pending("fortunes",false);
            view.setSettings("login",false);HubToast toast=(HubToast)field(view,"toast");now=System.nanoTime()+1_000_000_000L;toast.show("服务器已确认更新",HubToast.Kind.SUCCESS,now);
            save(render(renderer,view,now+160_000_000L),"toast-entering");
            BufferedImage shown=render(renderer,view,now+600_000_000L);save(shown,"toast-white-gradient");check(toast.opacity()>.99f,"Toast entry did not settle");
            // Check the shrinking 2px fuse is blue on the left and purple on its right.
            int left=shown.getRGB(1005,899),right=shown.getRGB(1225,899);
            check((left&255)>((left>>16)&255)&&((right>>16)&255)>((left>>16)&255),"Fuse is not left-blue/right-purple");
            render(renderer,view,now+4_000_000_000L);save(render(renderer,view,now+4_140_000_000L),"toast-leaving");check(toast.active()&&toast.opacity()>0&&toast.opacity()<1,"No full fade-out interval");
            render(renderer,view,now+4_300_000_000L);check(!toast.active(),"Expired toast remained visible");
            toast.show("请求未完成，请稍后重试",HubToast.Kind.ERROR,now+5_000_000_000L);save(render(renderer,view,now+5_600_000_000L),"toast-white-error");
            check(view.mouseClicked(930,795,0),"Toast did not capture mouse");view.mouseDragged(930,850,0,0,55);view.mouseReleased(930,850,0);
            check(glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)==fbo,"Wrong framebuffer after overlay");
            System.out.println("PASS: unchanged titles/count/buttons pixel-identical through refresh/paging; calendar pixels retained during feature; white blue-purple toast entry/exit, input and GL checks. PNGs: "+OUTPUT);
        }finally{glBindFramebuffer(GL_FRAMEBUFFER,0);glDeleteVertexArrays(vao);glDeleteFramebuffers(fbo);glDeleteTextures(texture);glfwDestroyWindow(window);glfwTerminate();errors.close();}
    }
}
