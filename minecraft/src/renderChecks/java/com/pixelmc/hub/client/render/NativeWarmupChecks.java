/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.render;

import com.google.gson.*;
import com.pixelmc.hub.client.HubSkinState;
import com.pixelmc.hub.client.ui.HubHead;
import com.pixelmc.hub.client.ui.HubView;

import java.nio.*;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.zip.CRC32;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL21.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.*;
import static org.lwjgl.opengl.GL32.*;
import static org.lwjgl.opengl.GL40.*;

/** Synthetic framebuffer only. No Minecraft launch, service, live player, network or skin file. */
public final class NativeWarmupChecks {
    private static int assertions,width=1440,height=1000,gameFbo,gameTexture,vao,ubo;
    private static long gameWindow;
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static void check(boolean ok,String reason){assertions++;if(!ok)throw new AssertionError(reason);}
    private static Field field(Class<?> c,String name)throws Exception{Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}
    private static Object value(Object o,String name)throws Exception{return field(o.getClass(),name).get(o);}
    private static void reject(Runnable call,String reason){try{call.run();throw new AssertionError(reason);}catch(IllegalStateException expected){assertions++;}}
    private static double ms(long start){return (System.nanoTime()-start)/1_000_000.0;}
    private record Pack(int active,int binding0,int pbo,int alignment,int row,int rows,int pixels,int height,int images,int swap,int lsb){
        static Pack read(){int active=glGetInteger(GL_ACTIVE_TEXTURE);glActiveTexture(GL_TEXTURE0);int binding=glGetInteger(GL_TEXTURE_BINDING_2D);glActiveTexture(active);return new Pack(active,binding,glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING),glGetInteger(GL_PACK_ALIGNMENT),glGetInteger(GL_PACK_ROW_LENGTH),glGetInteger(GL_PACK_SKIP_ROWS),glGetInteger(GL_PACK_SKIP_PIXELS),glGetInteger(GL_PACK_IMAGE_HEIGHT),glGetInteger(GL_PACK_SKIP_IMAGES),glGetInteger(GL_PACK_SWAP_BYTES),glGetInteger(GL_PACK_LSB_FIRST));}
        void restore(){glBindBuffer(GL_PIXEL_PACK_BUFFER,pbo);glPixelStorei(GL_PACK_ALIGNMENT,alignment);glPixelStorei(GL_PACK_ROW_LENGTH,row);glPixelStorei(GL_PACK_SKIP_ROWS,rows);glPixelStorei(GL_PACK_SKIP_PIXELS,pixels);glPixelStorei(GL_PACK_IMAGE_HEIGHT,height);glPixelStorei(GL_PACK_SKIP_IMAGES,images);glPixelStorei(GL_PACK_SWAP_BYTES,swap);glPixelStorei(GL_PACK_LSB_FIRST,lsb);glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,binding0);glActiveTexture(active);}
    }
    private static void cleanPack(){glBindBuffer(GL_PIXEL_PACK_BUFFER,0);glPixelStorei(GL_PACK_ALIGNMENT,1);glPixelStorei(GL_PACK_ROW_LENGTH,0);glPixelStorei(GL_PACK_SKIP_ROWS,0);glPixelStorei(GL_PACK_SKIP_PIXELS,0);glPixelStorei(GL_PACK_IMAGE_HEIGHT,0);glPixelStorei(GL_PACK_SKIP_IMAGES,0);glPixelStorei(GL_PACK_SWAP_BYTES,0);glPixelStorei(GL_PACK_LSB_FIRST,0);}
    private static void dirtyPack(int pbo){glBindBuffer(GL_PIXEL_PACK_BUFFER,pbo);glPixelStorei(GL_PACK_ALIGNMENT,8);glPixelStorei(GL_PACK_ROW_LENGTH,128);glPixelStorei(GL_PACK_SKIP_ROWS,5);glPixelStorei(GL_PACK_SKIP_PIXELS,7);glPixelStorei(GL_PACK_IMAGE_HEIGHT,256);glPixelStorei(GL_PACK_SKIP_IMAGES,2);glPixelStorei(GL_PACK_SWAP_BYTES,1);glPixelStorei(GL_PACK_LSB_FIRST,1);glActiveTexture(GL_TEXTURE3);}
    private static long pixelsCrc(){
        Pack saved=Pack.read();ByteBuffer pixels=MemoryUtil.memAlloc(width*height*4);
        try{cleanPack();glReadPixels(0,0,width,height,GL_RGBA,GL_UNSIGNED_BYTE,pixels);CRC32 crc=new CRC32();byte[] row=new byte[width*4];for(int y=0;y<height;y++){pixels.get(row);crc.update(row);}return crc.getValue();}
        finally{MemoryUtil.memFree(pixels);saved.restore();}
    }
    private static int pixel(int x,int y){
        Pack saved=Pack.read();ByteBuffer pixel=MemoryUtil.memAlloc(4);try{cleanPack();glReadPixels(x,height-y-1,1,1,GL_RGBA,GL_UNSIGNED_BYTE,pixel);return Byte.toUnsignedInt(pixel.get(0))<<16|Byte.toUnsignedInt(pixel.get(1))<<8|Byte.toUnsignedInt(pixel.get(2));}finally{MemoryUtil.memFree(pixel);saved.restore();}
    }
    private static double gpuComplete(){
        long began=System.nanoTime(),fence=glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE,0);check(fence!=0,"No diagnostic fence");
        try{int result=glClientWaitSync(fence,GL_SYNC_FLUSH_COMMANDS_BIT,5_000_000_000L);check(result==GL_ALREADY_SIGNALED||result==GL_CONDITION_SATISFIED,"GPU diagnostic fence timed out");}finally{glDeleteSync(fence);}return ms(began);
    }
    private static void scene(){glDisable(GL_SCISSOR_TEST);glClearColor(.28f,.48f,.66f,1);glClear(GL_COLOR_BUFFER_BIT);}
    private static void allocate(int w,int h,int format){
        width=w;height=h;glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,gameTexture);glBindBuffer(GL_PIXEL_UNPACK_BUFFER,0);glTexImage2D(GL_TEXTURE_2D,0,format,w,h,0,GL_RGBA,GL_UNSIGNED_BYTE,(ByteBuffer)null);glBindFramebuffer(GL_FRAMEBUFFER,gameFbo);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,gameTexture,0);glViewport(0,0,w,h);check(glCheckFramebufferStatus(GL_FRAMEBUFFER)==GL_FRAMEBUFFER_COMPLETE,"Synthetic FBO incomplete");
    }
    private static void gameState(){
        glBindBufferBase(GL_UNIFORM_BUFFER,0,ubo);glDisablei(GL_BLEND,0);glEnablei(GL_BLEND,1);glBlendFuncSeparatei(1,GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA,GL_ONE,GL_ZERO);glDisable(GL_PROGRAM_POINT_SIZE);glEnable(GL_SCISSOR_TEST);glScissor(3,5,width-12,height-16);glViewport(7,9,width-20,height-20);
    }
    private static void sameState(GlState before)throws Exception{
        Method compare=NativeRendererChecks.class.getDeclaredMethod("compare",Object.class,Object.class);compare.setAccessible(true);compare.invoke(null,before,new GlState());
        check(glfwGetCurrentContext()==gameWindow,"Original context not restored");check(glGetIntegeri(GL_UNIFORM_BUFFER_BINDING,0)==ubo,"Indexed UBO changed");check(!glIsEnabledi(GL_BLEND,0)&&glIsEnabledi(GL_BLEND,1)&&glGetIntegeri(GL_BLEND_SRC_RGB,1)==GL_SRC_ALPHA,"Indexed blend changed");check(glGetError()==GL_NO_ERROR,"GL error");
    }
    private static HubView fixture(){HubView view=new HubView();view.setSettings("login",true);view.setViewportBounds(-90,-75,1440,1000);view.open(false);return view;}
    private static JsonObject firstFrame(HubRenderer renderer)throws Exception{
        HubView view=fixture();scene();gameState();GlState before=new GlState();long began=System.nanoTime();renderer.render(width,height,90,75,1,true,d->view.render(d,-100,-100,System.nanoTime()));double cpu=ms(began);sameState(before);double gpu=gpuComplete();JsonObject data=JSON.toJsonTree(renderer.lastTiming()).getAsJsonObject();data.addProperty("callWallMs",cpu);data.addProperty("diagnosticGpuFenceMs",gpu);return data;
    }
    private static JsonObject warm(HubRenderResources pool)throws Exception{
        scene();gameState();long crc=pixelsCrc();GlState before=new GlState();int steps=0;long began=System.nanoTime();
        while(pool.current()==null||!pool.current().warmupProgress().complete()){pool.tick();steps++;check(steps<100,"Warmup did not terminate");sameState(before);check(!pool.warmupFailed(),"Warmup failed");}
        double wall=ms(began),gpu=gpuComplete();check(crc==pixelsCrc(),"Startup warmup modified game target pixels");HubRenderer renderer=pool.current();
        check(value(renderer,"surface")==null&&value(renderer,"target")==null,"Warmup borrowed the real target");check(value(renderer,"warmSurface")==null,"Warmup retained its scratch surface");
        JsonObject data=new JsonObject();data.addProperty("steps",steps);data.addProperty("wallWithoutFrameGapsMs",wall);data.addProperty("diagnosticGpuFenceMs",gpu);data.add("progress",JSON.toJsonTree(renderer.warmupProgress()));data.add("phasesMs",JSON.toJsonTree(renderer.initializationMillis()));return data;
    }
    private static void cleanPanel(HubRenderer renderer)throws Exception{
        HubDraw draw=(HubDraw)value(renderer,"draw");check(value(renderer,"surface")==null&&value(renderer,"target")==null&&((Number)value(renderer,"aliasFramebuffer")).intValue()==0,"Panel target survived release");
        for(String map:new String[]{"lines","trackedLines","sansRuns","layerPool"})check(((Map<?,?>)value(draw,map)).isEmpty(),"Private/frame map survived: "+map);
        check(((Map<?,?>)value(draw,"images")).keySet().stream().noneMatch(k->k.toString().startsWith("memory:")),"Runtime server icon survived");
        Object glass=value(draw,"glass");check(((Map<?,?>)value(glass,"filterPool")).isEmpty()&&((List<?>)value(glass,"frameBackdrops")).isEmpty(),"Frame glass survived");
        check(!((Map<?,?>)value(draw,"images")).isEmpty()&&!((Map<?,?>)value(draw,"fonts")).isEmpty(),"General public resources were thrown away");
    }
    private static void skinSchedule(){
        HubSkinState skin=new HubSkinState();UUID a=UUID.randomUUID(),b=UUID.randomUUID();HubSkinState.Key key=new HubSkinState.Key(a,"synthetic",5,1);skin.session(a,0);skin.observe(key,0);
        HubSkinState.Capture one=skin.claim(10_000_000_000L);check(one!=null,"Late first capture unavailable");skin.confirm(one,"default");check(skin.claim(10_000_000_001L)==null,"Late readiness consumed delay slots consecutively");
        HubSkinState.Capture two=skin.claim(11_000_000_000L);check(two!=null&&skin.changed(two,"real"),"Same-id default->real retry unavailable");skin.confirm(two,"real");check(skin.claim(11_000_000_001L)==null,"Retry flooded next frame");
        HubSkinState.Capture three=skin.claim(15_000_000_000L);check(three!=null&&!skin.changed(three,"real"),"Unchanged RGBA would replace image");skin.confirm(three,"real");check(skin.claim(1_000_000_000_000L)==null,"Unbounded recapture polling");
        skin.retryWindow(20_250_000_000L);check(skin.claim(20_000_000_000L)==null,"Open recapture ran on initial frame");check(skin.claim(20_250_000_000L)!=null,"Open did not provide finite retry");
        skin.session(b,21_000_000_000L);check(!skin.current(one)&&!skin.confirmed()&&skin.wanted()==null,"Player switch retained stale skin");
        skin.observe(new HubSkinState.Key(b,"synthetic-b",7,1),21_000_000_000L);var stale=skin.claim(21_000_000_000L);skin.clear();check(!skin.current(stale)&&skin.player()==null,"Logout retained capture ticket");
    }
    private static void skinPixels(HubRenderer renderer)throws Exception{
        Pack original=Pack.read();cleanPack();glActiveTexture(GL_TEXTURE0);int texture=glGenTextures();glBindTexture(GL_TEXTURE_2D,texture);byte[] rgba=new byte[64*64*4];
        for(int y=0;y<64;y++)for(int x=0;x<64;x++){int i=(y*64+x)*4;rgba[i+3]=(byte)255;}
        for(int y=8;y<16;y++)for(int x=8;x<16;x++){int i=(y*64+x)*4;rgba[i]=(byte)255;}
        for(int y=8;y<16;y++)for(int x=40;x<48;x++){int i=(y*64+x)*4;rgba[i+2]=(byte)255;rgba[i+3]=(byte)128;}
        ByteBuffer source=MemoryUtil.memAlloc(rgba.length);source.put(rgba).flip();glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,64,64,0,GL_RGBA,GL_UNSIGNED_BYTE,source);MemoryUtil.memFree(source);
        int pbo=glGenBuffers();glBindBuffer(GL_PIXEL_PACK_BUFFER,pbo);glBufferData(GL_PIXEL_PACK_BUFFER,65536,GL_STATIC_READ);dirtyPack(pbo);Pack gamePack=Pack.read();GlState gameBefore=new GlState();
        try {
            try(HubRenderer.SkinPixels read=renderer.readSkinTexture(texture)){check(Arrays.equals(read.rgba(),rgba),"Cross-context RGBA differs");}
            sameState(gameBefore);check(Pack.read().equals(gamePack),"Main-context pack/active texture changed");
            AtomicReference<byte[]> wiped=new AtomicReference<>();
            renderer.withIsolated(()->{
                int privatePbo=glGenBuffers();glBindBuffer(GL_PIXEL_PACK_BUFFER,privatePbo);glBufferData(GL_PIXEL_PACK_BUFFER,65536,GL_STATIC_READ);dirtyPack(privatePbo);Pack expected=Pack.read();
                try(HubRenderer.SkinPixels read=HubRenderer.readRgba(texture)){check(Arrays.equals(read.rgba(),rgba),"Dirty isolated pack corrupted skin");HubHead.setSkinRgba(64,64,read.rgba());wiped.set(read.rgba());}
                check(Pack.read().equals(expected),"Isolated pack/PBO/texture state not restored");for(byte value:wiped.get())check(value==0,"Raw private RGBA was not wiped");
                cleanPack();glDeleteBuffers(privatePbo);
            });
            scene();renderer.render(width,height,0,0,1,d->{d.rect(0,0,width,height,0xff000000);HubHead.draw2D(d,20,20,64);HubHead.draw(d,120,40,80,-22);});
            int mixed=pixel(48,48);check((mixed>>16&255)>80&&(mixed&255)>80&&(mixed>>8&255)<20,"Copied RGBA/transparent hat failed after source byte wipe: "+Integer.toHexString(mixed));
            renderer.withIsolated(HubHead::clearSkin);check(field(HubHead.class,"skin").get(null)==null,"Session skin not cleared");
        }finally{original.restore();glDeleteBuffers(pbo);glDeleteTextures(texture);}
    }
    private static void lifecycle()throws Exception{
        HubRenderResources early=new HubRenderResources();early.tick();HubRenderResources.Lease lease=early.acquire();int stage=lease.renderer().warmupProgress().stage();early.tick();check(lease.renderer().warmupProgress().stage()==stage,"Background warmup ran while borrowed");firstFrame(lease.renderer());early.release(lease);HubRenderer retained=early.current();warm(early);var next=early.acquire();check(next.renderer()==retained,"Early open lost common resources");early.release(next);
        var beforeReload=early.acquire();early.requestReload();check(!early.valid(beforeReload),"Reload left old lease valid before next render");var afterReload=early.acquire();check(afterReload.generation()!=beforeReload.generation()&&afterReload.renderer()!=beforeReload.renderer(),"Reload reused old renderer");early.release(beforeReload);check(early.valid(afterReload),"Old lease released new borrower");firstFrame(afterReload.renderer());early.release(afterReload);early.close();early.close();reject(early::acquire,"Shutdown allowed another screen");
        AtomicInteger creates=new AtomicInteger();HubRenderResources fault=new HubRenderResources(()->{creates.incrementAndGet();return new HubRenderer();});fault.tick();fault.beforeDispose(r->{throw new IllegalStateException("injected beforeDispose failure");});fault.requestReload();fault.tick();check(fault.warmupFailed()&&fault.current()==null&&!fault.borrowed(),"Cleanup failure retained renderer/lease");int count=creates.get();for(int i=0;i<20;i++)fault.tick();check(creates.get()==count,"Cleanup failure retried every frame");fault.beforeDispose(r->{});var recovered=fault.acquire();check(creates.get()==count+1,"Explicit open could not recover");firstFrame(recovered.renderer());fault.release(recovered);fault.close();
        HubRenderResources closing=new HubRenderResources();closing.tick();closing.beforeDispose(r->{throw new IllegalStateException("injected shutdown failure");});reject(closing::close,"Close fault not observable");check(closing.current()==null&&!closing.borrowed(),"Shutdown fault retained resources");closing.close();reject(closing::acquire,"Shutdown fault did not become terminal");
        HubRenderResources partial=new HubRenderResources();partial.tick();partial.close();check(partial.current()==null,"Partial startup close retained resources");
    }
    public static void main(String[] args)throws Exception{
        String mode=args.length==0?"comparison":args[0];Path output=Path.of(args.length>1?args[1]:"../.runtime/native-previews/warmup");Files.createDirectories(output);skinSchedule();
        GLFWErrorCallback errors=GLFWErrorCallback.createPrint(System.err);errors.set();check(glfwInit(),"GLFW failed");glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,4);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,6);glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);gameWindow=glfwCreateWindow(640,480,"Hidden resource warmup checks",0,0);check(gameWindow!=0,"No hidden context");glfwMakeContextCurrent(gameWindow);GL.createCapabilities();
        gameTexture=glGenTextures();gameFbo=glGenFramebuffers();vao=glGenVertexArrays();glBindVertexArray(vao);ubo=glGenBuffers();glBindBuffer(GL_UNIFORM_BUFFER,ubo);glBufferData(GL_UNIFORM_BUFFER,1024,GL_STATIC_DRAW);allocate(1440,1000,GL_RGBA8);
        JsonObject report=new JsonObject();report.addProperty("mode",mode);report.addProperty("freshJvmForThisMode",true);report.addProperty("warmupUsesOwnedSurface",true);report.addProperty("gameClientStarted",false);
        try {
            if(mode.equals("comparison"))try(HubRenderer cold=new HubRenderer()){report.add("coldFirstFrame",firstFrame(cold));report.add("coldInitializationPhasesMs",JSON.toJsonTree(cold.initializationMillis()));}
            try(HubRenderResources pool=new HubRenderResources()){
                report.add("startupWarmup",warm(pool));HubRenderResources.Lease lease=pool.acquire();HubRenderer renderer=lease.renderer();report.add("warmFirstFrame",firstFrame(renderer));
                renderer.render(width,height,90,75,1,d->{d.text("synthetic private player record",20,40,13,0xff26344f,400);d.textTracked("private",20,70,13,0xff26344f,400,1,false);d.saveLayerAlpha(0,0,300,200,.5f);d.roundRect(0,0,300,200,12,0xffffffff);d.restore();});
                pool.release(lease);cleanPanel(renderer);var reopened=pool.acquire();check(reopened.renderer()==renderer,"Close/reopen recreated common renderer");report.add("reopenedFirstFrame",firstFrame(renderer));skinPixels(renderer);
                for(int format:new int[]{GL_RGBA8,GL_SRGB8_ALPHA8,GL_RGBA16F,GL_RGB10_A2}){allocate(640,480,format);scene();renderer.render(width,height,0,0,.5f,d->d.roundRect(8,8,80,60,8,0xffedf4ff));allocate(320,240,format);scene();renderer.render(width,height,0,0,.25f,d->d.circle(30,30,15,0xff987aca));check(glGetError()==GL_NO_ERROR,"Resize/format GL error");}
                pool.release(reopened);
            }
            if(mode.equals("comparison")){allocate(1440,1000,GL_RGBA8);lifecycle();}
            report.addProperty("assertions",assertions);report.addProperty("timingQualification","CPU submission and diagnostic fence only; startup cost is included separately. Comparison cold/warm share one JVM; startup mode is a second fresh JVM. OS/driver disk caches are not purged. No performance threshold asserted.");
            Files.writeString(output.resolve("warmup-"+mode+".json"),JSON.toJson(report)+"\n");System.out.println("PASS warmup "+mode+" assertions="+assertions+" evidence="+output.resolve("warmup-"+mode+".json"));
        }finally{glActiveTexture(GL_TEXTURE0);cleanPack();glBindFramebuffer(GL_FRAMEBUFFER,0);glDeleteFramebuffers(gameFbo);glDeleteTextures(gameTexture);glDeleteBuffers(ubo);glDeleteVertexArrays(vao);glfwDestroyWindow(gameWindow);glfwTerminate();errors.close();}
    }
}
