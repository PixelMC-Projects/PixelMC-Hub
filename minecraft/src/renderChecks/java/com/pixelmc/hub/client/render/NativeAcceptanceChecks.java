/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.render;

import com.google.gson.*;
import com.pixelmc.hub.client.ui.HubView;
import com.pixelmc.hub.client.ui.PlayerDataChecks;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.*;
import javax.imageio.ImageIO;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

/** 02-05 acceptance regressions on a hidden GPU surface, using synthetic data only. */
public final class NativeAcceptanceChecks {
    private static int width=1440,height=1000,texture,fbo;
    private static final Path OUTPUT=Path.of("../.runtime/native-previews/acceptance-0.2.1").toAbsolutePath().normalize();
    private static void check(boolean result,String message){if(!result)throw new AssertionError(message);}
    private static void allocate(int w,int h){width=w;height=h;glBindTexture(GL_TEXTURE_2D,texture);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,w,h,0,GL_RGBA,GL_UNSIGNED_BYTE,(ByteBuffer)null);glBindFramebuffer(GL_FRAMEBUFFER,fbo);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,texture,0);glViewport(0,0,w,h);}
    private static void game(int phase){
        glDisable(GL_SCISSOR_TEST);glClearColor(phase==0?0:.14f,phase==0?0:.7f,phase==0?1:.4f,1);glClear(GL_COLOR_BUFFER_BIT);
        glEnable(GL_SCISSOR_TEST);for(int x=0;x<width;x+=64){glScissor(x,0,32,height);glClearColor(phase==0?1:.7f,phase==0?0:.2f,phase==0?0:.8f,1);glClear(GL_COLOR_BUFFER_BIT);}glDisable(GL_SCISSOR_TEST);
    }
    private static BufferedImage read(){
        ByteBuffer data=MemoryUtil.memAlloc(width*height*4);try{glReadPixels(0,0,width,height,GL_RGBA,GL_UNSIGNED_BYTE,data);BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<height;y++)for(int x=0;x<width;x++){int i=((height-y-1)*width+x)*4;image.setRGB(x,y,0xff000000|Byte.toUnsignedInt(data.get(i))<<16|Byte.toUnsignedInt(data.get(i+1))<<8|Byte.toUnsignedInt(data.get(i+2)));}return image;}finally{MemoryUtil.memFree(data);}
    }
    private static void save(BufferedImage image,String name)throws Exception{ImageIO.write(image,"PNG",OUTPUT.resolve(name+".png").toFile());}
    private static void panel(HubRenderer renderer,String name,JsonObject titles,JsonObject fortunes)throws Exception{
        HubView view=new HubView();PlayerDataChecks.populate(view);view.acceptLive("titles","snapshot",titles);view.catalogueComplete();view.acceptLive("fortunes","snapshot",fortunes);view.setSettings("always",true);view.setViewportBounds(-90,-75,1440,1000);view.open(false);game(1);
        float[] badge=new float[1];renderer.render(width,height,90,75,1,true,d->{view.render(d,-100,-100,System.nanoTime());badge[0]=d.textWidth("中平签",9,false)+10;});save(read(),name);
        check(badge[0]>10&&badge[0]<44,"Grade badge retained excessive fixed width");
        System.out.println(name+": grade width="+badge[0]+", left/right padding=5.0");
    }
    private static JsonObject titles(int count){JsonObject value=PlayerDataChecks.titles();JsonArray rows=new JsonArray();String[] names={"朝露行者","山野来客","林间筑梦"};for(int i=0;i<count;i++){JsonObject row=PlayerDataChecks.titles().getAsJsonArray("titles").get(0).getAsJsonObject().deepCopy();row.addProperty("id","owned_"+i);row.addProperty("acquiredAt",java.time.Instant.parse("2026-11-04T"+(10+i)+":30:00Z").toEpochMilli());JsonArray segments=new JsonArray();JsonObject segment=new JsonObject();segment.addProperty("text",names[i]);segment.addProperty("color",0x537786);segment.addProperty("bold",true);segments.add(segment);JsonObject name=new JsonObject();name.add("segments",segments);name.add("gradients",new JsonArray());row.add("name",name);rows.add(row);}value.add("titles",rows);value.addProperty("equippedTitle","");return value;}
    private static JsonObject fortunes(int count){JsonObject value=PlayerDataChecks.fortunes();JsonArray rows=new JsonArray();String[] grades={"上上签","小凶签","中平签"};for(int i=0;i<count;i++){JsonObject row=PlayerDataChecks.record();row.addProperty("id","archive_"+i);row.addProperty("date","2026-11-0"+(2+i));row.addProperty("at",java.time.Instant.parse("2026-11-0"+(2+i)+"T12:26:14Z").toEpochMilli());row.getAsJsonObject("fortune").addProperty("title",i==2?"半山泉":"归人"+(i+1));row.getAsJsonObject("fortune").addProperty("grade",grades[i]);rows.add(row);}value.add("recent",rows);value.addProperty("archiveCount",count);value.addProperty("todayState","undrawn");value.add("todayRecord",JsonNull.INSTANCE);value.add("featuredDate",JsonNull.INSTANCE);value.add("featuredRecord",JsonNull.INSTANCE);return value;}
    public static void main(String[] args)throws Exception {
        Files.createDirectories(OUTPUT);GLFWErrorCallback errors=GLFWErrorCallback.createPrint(System.err);errors.set();check(glfwInit(),"GLFW unavailable");glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,4);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,6);glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
        long window=glfwCreateWindow(640,480,"Hidden acceptance regressions",0,0);check(window!=0,"Hidden context unavailable");glfwMakeContextCurrent(window);GL.createCapabilities();int gameVao=glGenVertexArrays();glBindVertexArray(gameVao);texture=glGenTextures();fbo=glGenFramebuffers();allocate(1440,1000);
        try {
            try(HubRenderer renderer=new HubRenderer()) {
                for(int[] size:new int[][]{{1440,1000},{3840,2054}}) {
                    allocate(size[0],size[1]);game(0);GlState original=new GlState();
                    renderer.render(width,height,0,0,1,false,d->{d.roundRect(200,100,260,120,12,0xffffffff);d.text("清晰 UI / Sharp",215,146,22,0xff26344f,true);});BufferedImage sharp=read();
                    game(0);renderer.render(width,height,0,0,1,true,d->{d.roundRect(200,100,260,120,12,0xffffffff);d.text("清晰 UI / Sharp",215,146,22,0xff26344f,true);
                        try {var field=HubDraw.class.getDeclaredField("glass");field.setAccessible(true);HubGlass glass=(HubGlass)field.get(d);var pool=HubGlass.class.getDeclaredField("filterPool");pool.setAccessible(true);var surfaces=((java.util.Map<?,?>)pool.get(glass)).values();check(surfaces.size()==1,"Scene allocated a second filter chain");for(Object item:surfaces){var surface=(io.github.humbleui.skija.Surface)item;check(surface.getWidth()==(width+3)/4&&surface.getHeight()==(height+3)/4,"Scene used a full-resolution offscreen filter");}var captures=HubGlass.class.getDeclaredField("captures");captures.setAccessible(true);check(captures.getInt(glass)==0,"Scene captured an additional full backdrop");}catch(ReflectiveOperationException e){throw new AssertionError(e);}
                    });BufferedImage blurred=read();
                    int red=(blurred.getRGB(31,50)>>16)&255;check(red>70&&red<245,"World edge did not receive light blur");check(((blurred.getRGB(16,50)>>16)&255)>245,"Blur erased the recognizable world stripe");
                    for(int y=120;y<190;y++)for(int x=220;x<430;x++)check(sharp.getRGB(x,y)==blurred.getRGB(x,y),"UI text/controls were blurred");
                    var compare=NativeRendererChecks.class.getDeclaredMethod("compare",Object.class,Object.class);compare.setAccessible(true);compare.invoke(null,original,new GlState());
                    if(width==1440){save(sharp,"scene-unblurred");save(blurred,"scene-light-blur");}
                    game(1);renderer.render(width,height,0,0,1,true,d->{});BufferedImage changed=read();check(Math.abs(((changed.getRGB(16,300)>>8)&255)-((blurred.getRGB(16,300)>>8)&255))>30,"Scene blur used a stale frame");
                    check(glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)==fbo,"Scene changed game framebuffer");check(glGetError()==GL_NO_ERROR,"GL error after scene blur");
                    System.out.println("scene "+width+"x"+height+": dynamic blur, sharp UI, one quarter-size pooled target, zero added captures, GL state retained");
                }
            }
            allocate(1440,1000);try(HubRenderer renderer=new HubRenderer()) {panel(renderer,"recent-titles-only",titles(3),fortunes(0));panel(renderer,"recent-fortunes-only",titles(0),fortunes(3));panel(renderer,"recent-mixed",titles(3),fortunes(3));panel(renderer,"recent-empty",titles(0),fortunes(0));}
            System.out.println("PASS: hidden GPU acceptance 03-05; screenshots at "+OUTPUT);
        } finally {glBindFramebuffer(GL_FRAMEBUFFER,0);glDeleteVertexArrays(gameVao);glDeleteFramebuffers(fbo);glDeleteTextures(texture);glfwDestroyWindow(window);glfwTerminate();errors.close();}
    }
}
