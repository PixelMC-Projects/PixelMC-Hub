/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.render;

import io.github.humbleui.skija.*;
import io.github.humbleui.skija.impl.Library;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import java.util.*;
import java.util.function.Consumer;
import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.glfw.GLFW.glfwGetCurrentContext;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL21.*;
import static org.lwjgl.opengl.GL30.*;

/** Skija stays in a dedicated shared context; startup uses only an owned small offscreen surface. */
public final class HubRenderer implements AutoCloseable {
    private static final Logger LOGGER=LogUtils.getLogger();
    private static final int WARM_SIZE=192,ASSET_BATCH=6;
    private DirectContext context;
    private HubGlContext isolated;
    private BackendRenderTarget target;
    private Surface surface,warmSurface;
    private HubDraw draw;
    private int width,height,sourceFramebuffer=-1,sourceColor=-1,sourceFormat=-1;
    private int aliasFramebuffer,stencilBuffer;
    private long glContext;
    private Thread renderThread;
    private boolean closed,libraryReady,firstFrame=true;
    private int warmStage,warmAsset;
    private final Map<String,Double> initializationMillis=new LinkedHashMap<>();
    private double warmupMillis;
    private FrameTiming lastTiming=new FrameTiming(0,0,0,0,0,0,false);
    public record FrameTiming(double initializationMs,double targetMs,double backdropMs,double contentMs,double submitMs,double totalMs,boolean commonReadyBeforeFrame){}
    public record WarmupProgress(int stage,String name,boolean complete,double totalMs,int assetsUploaded,int assetCount){}
    public record SkinPixels(int width,int height,byte[] rgba) implements AutoCloseable {
        @Override public void close(){Arrays.fill(rgba,(byte)0);}
    }
    private static double ms(long from){return (System.nanoTime()-from)/1_000_000.0;}
    private void timed(String stage,Runnable action){
        long began=System.nanoTime();try{action.run();}finally{double elapsed=ms(began);initializationMillis.merge(stage,elapsed,Double::sum);LOGGER.info("PixelMC-Hub resource stage={} elapsedMs={}",stage,String.format(Locale.ROOT,"%.3f",elapsed));}
    }
    private void requireGame(){
        if(closed)throw new IllegalStateException("HubRenderer was closed");
        long current=glfwGetCurrentContext();if(current==0)throw new IllegalStateException("Hub rendering requires the current Minecraft OpenGL context");
        if(renderThread==null){renderThread=Thread.currentThread();glContext=current;}
        else if(renderThread!=Thread.currentThread()||current!=glContext)throw new IllegalStateException("HubRenderer must stay on its original render thread and GL context");
    }
    private void library(){if(!libraryReady)timed("jni",()->{Library.load();libraryReady=true;});}
    private void shared(){if(isolated==null)timed("shared-context",()->isolated=new HubGlContext(glContext));}
    private void skia(){if(context==null)timed("skia-context",()->{context=DirectContext.makeGL();if(context==null)throw new IllegalStateException("Cannot create Hub Skija context");checkGl("Skija creation");});}
    private void fonts(){if(draw==null)timed("fonts",()->draw=new HubDraw());}
    public boolean commonReady(){return !closed&&context!=null&&draw!=null;}
    public boolean isClosed(){return closed;}
    public FrameTiming lastTiming(){return lastTiming;}
    public Map<String,Double> initializationMillis(){return Map.copyOf(initializationMillis);}
    public WarmupProgress warmupProgress(){
        String name=switch(warmStage){case 0->"jni";case 1->"shared-context";case 2->"skia-context";case 3->"fonts";case 4->"pipelines";case 5->"assets";default->"ready";};
        return new WarmupProgress(warmStage,name,warmStage>=6,warmupMillis,warmAsset,draw==null?0:draw.warmAssetCount());
    }
    /** One indivisible phase per caller render frame. JNI/driver phases cannot promise a small time slice. */
    public boolean warmupStep(){
        requireGame();if(warmStage>=6)return true;long began=System.nanoTime();
        try {
            switch(warmStage){
                case 0 -> {library();warmStage++;}
                case 1 -> {shared();warmStage++;}
                case 2 -> isolated(()->{skia();warmStage++;});
                case 3 -> isolated(()->{skia();fonts();warmStage++;});
                case 4 -> isolated(()->{skia();fonts();timed("pipeline-fixture",()->warmDraw(this::pipelineFixture));warmStage++;});
                case 5 -> isolated(()->{
                    skia();fonts();int before=warmAsset;
                    timed("assets-"+before,()->warmDraw(d->warmAsset=d.warmAssetBatch(warmAsset,ASSET_BATCH)));
                    if(warmAsset>=draw.warmAssetCount()){releaseWarmSurface();draw.releasePanelResources();warmStage++;}
                });
                default -> throw new IllegalStateException("Unknown warmup phase");
            }
        }finally{warmupMillis+=ms(began);}
        return warmStage>=6;
    }
    private void isolated(Runnable action){library();shared();isolated.enter();try{action.run();}finally{isolated.leave();}}
    private void warmDraw(Consumer<HubDraw> content){
        context.resetGLAll();
        if(warmSurface==null){warmSurface=Surface.makeRenderTarget(context,true,ImageInfo.makeN32Premul(WARM_SIZE,WARM_SIZE));if(warmSurface==null)throw new IllegalStateException("Cannot create Hub owned warmup surface");}
        Canvas canvas=warmSurface.getCanvas();int saved=canvas.save();
        try{canvas.resetMatrix();canvas.clear(0xffdfe9f6);draw.begin(canvas,warmSurface);content.accept(draw);}
        finally{try{draw.end();}finally{canvas.restoreToCount(saved);context.flushAndSubmit(warmSurface);}}
        checkGl("owned warmup drawing");
    }
    private void pipelineFixture(HubDraw d){
        d.linearGradientRoundRect(3,3,186,186,20,120,new int[]{0xffd9f0ff,0xffdbc9f9},new float[]{0,1});
        d.captureGlassBackdrop(0,0,WARM_SIZE,WARM_SIZE);d.blurGameScene(3);
        d.glassSurface(4,4,90,80,12,GlassStyle.SIDEBAR);
        d.glassSurface(98,4,90,80,12,GlassStyle.IDENTITY);
        d.glassSurface(6,88,84,70,12,GlassStyle.DEFAULT);
        d.glassIcon(126,104,46,0xff3cd77f,0xff109e73,HubDraw.iconPose(46,1,.5f,0,.2f,-.2f,false),1);
        d.boxShadow(12,142,76,26,8,0,3,8,0,0x227658bd);d.roundRect(12,142,76,26,8,0xfff8f6ff);
        d.saveLayerAlpha(0,0,190,190,.75f);d.text("旅人手册 Aa0123",8,32,13,0xff26344f,400);d.restore();
        for(int weight:new int[]{400,500,550,600,650,700,720})d.text("Aa中文0123",8,62,13,0xff26344f,weight);
        for(float size:new float[]{10,11,12,14,18,20,23,25,35})d.text("手册 Ab9",8,100,size,0xff26344f,600);
        d.textSerif("签笺 归人",8,130,13,0xff886d43,400);d.textSerif("签笺 归人",8,153,16,0xff886d43,600);
        d.textGradient("你好 PixelMC",10,180,18,700,0,110,new int[]{0xff5d84d6,0xff8970d8},new float[]{0,1},false);
    }
    public void render(int w,int h,float x,float y,float scale,Consumer<HubDraw> content){render(w,h,x,y,scale,false,content);}
    public void render(int w,int h,float x,float y,float scale,boolean blurGameScene,Consumer<HubDraw> content){
        Objects.requireNonNull(content,"content");if(w<=0||h<=0||!Float.isFinite(scale)||scale<=0)return;requireGame();
        long began=System.nanoTime(),phase=began;boolean wasReady=commonReady();double init=0,targetMs=0,backdrop=0,contentMs=0,submit=0;
        Source source=source(glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING));Canvas canvas=null;int saved=0;
        library();shared();isolated.enter();
        try{
            skia();fonts();init=ms(phase);context.resetGLAll();
            phase=System.nanoTime();prepareTarget(source,w,h);glBindFramebuffer(GL_DRAW_FRAMEBUFFER,aliasFramebuffer);glViewport(0,0,w,h);
            surface.notifyContentWillChange(ContentChangeMode.RETAIN);canvas=surface.getCanvas();saved=canvas.save();canvas.resetMatrix();canvas.translate(x,y).scale(scale,scale);targetMs=ms(phase);
            phase=System.nanoTime();draw.begin(canvas,surface);if(blurGameScene)draw.blurGameScene(3f*scale);backdrop=ms(phase);
            phase=System.nanoTime();content.accept(draw);contentMs=ms(phase);
        }finally{
            phase=System.nanoTime();
            try{try{if(draw!=null)draw.end();}finally{if(canvas!=null)canvas.restoreToCount(saved);}}
            finally{try{if(context!=null&&surface!=null)context.flushAndSubmit(surface);}finally{isolated.leave();submit=ms(phase);lastTiming=new FrameTiming(init,targetMs,backdrop,contentMs,submit,ms(began),wasReady);}}
        }
        if(firstFrame){LOGGER.info("PixelMC-Hub displayed native frame commonReady={} initMs={} targetMs={} backdropMs={} contentMs={} submitMs={} totalMs={}",wasReady,fmt(init),fmt(targetMs),fmt(backdrop),fmt(contentMs),fmt(submit),fmt(lastTiming.totalMs()));firstFrame=false;}
    }
    private static String fmt(double millis){return String.format(Locale.ROOT,"%.3f",millis);}
    private static void checkGl(String stage){int error=glGetError();if(error!=GL_NO_ERROR)throw new IllegalStateException("OpenGL error after Hub "+stage+": 0x"+Integer.toHexString(error));}
    private record Source(int framebuffer,int color,int level){}
    private static Source source(int framebuffer){
        if(framebuffer==0)throw new IllegalStateException("Hub requires Minecraft's texture-backed framebuffer");
        int type=glGetFramebufferAttachmentParameteri(GL_DRAW_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
        if(type!=GL_TEXTURE)throw new IllegalStateException("Hub expects a texture-backed game color attachment");
        return new Source(framebuffer,glGetFramebufferAttachmentParameteri(GL_DRAW_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME),glGetFramebufferAttachmentParameteri(GL_DRAW_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL));
    }
    private void prepareTarget(Source source,int newWidth,int newHeight){
        int framebuffer=source.framebuffer,color=source.color;if(!glIsTexture(color))throw new IllegalStateException("Game color texture is not shared with the Hub context");
        int bound=glGetInteger(GL_TEXTURE_BINDING_2D),format;
        try{glBindTexture(GL_TEXTURE_2D,color);format=glGetTexLevelParameteri(GL_TEXTURE_2D,source.level,GL_TEXTURE_INTERNAL_FORMAT);if(glGetTexLevelParameteri(GL_TEXTURE_2D,source.level,GL_TEXTURE_WIDTH)<newWidth||glGetTexLevelParameteri(GL_TEXTURE_2D,source.level,GL_TEXTURE_HEIGHT)<newHeight)throw new IllegalStateException("Hub viewport exceeds actual game texture");}
        finally{glBindTexture(GL_TEXTURE_2D,bound);}
        ColorType type=switch(format){case GL_RGBA8->ColorType.RGBA_8888;case GL_SRGB8_ALPHA8->ColorType.SRGBA_8888;case GL_RGBA16F->ColorType.RGBA_F16;case GL_RGB10_A2->ColorType.RGBA_1010102;default->throw new IllegalStateException("Unsupported game framebuffer format: 0x"+Integer.toHexString(format));};
        if(surface!=null&&width==newWidth&&height==newHeight&&sourceFramebuffer==framebuffer&&sourceColor==color&&sourceFormat==format)return;
        releaseTarget();width=newWidth;height=newHeight;sourceFramebuffer=framebuffer;sourceColor=color;sourceFormat=format;
        aliasFramebuffer=glGenFramebuffers();glBindFramebuffer(GL_FRAMEBUFFER,aliasFramebuffer);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,color,source.level);
        stencilBuffer=glGenRenderbuffers();glBindRenderbuffer(GL_RENDERBUFFER,stencilBuffer);glRenderbufferStorage(GL_RENDERBUFFER,GL_STENCIL_INDEX8,width,height);glFramebufferRenderbuffer(GL_FRAMEBUFFER,GL_STENCIL_ATTACHMENT,GL_RENDERBUFFER,stencilBuffer);
        if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Cannot create Hub isolated stencil framebuffer");
        target=BackendRenderTarget.makeGL(width,height,0,8,aliasFramebuffer,format);surface=Surface.wrapBackendRenderTarget(context,target,SurfaceOrigin.BOTTOM_LEFT,type,ColorSpace.getSRGB());
        if(surface==null)throw new IllegalStateException("Cannot wrap actual game framebuffer format");checkGl("target preparation");
    }
    /** End a screen lease, retaining only general fonts/classpath assets/context/pipelines. */
    public void releasePanelResources(){
        requireGame();if(isolated==null)return;isolated.enter();try{
            if(context!=null){context.resetGLAll();if(surface!=null)context.flushAndSubmit(surface);}
            if(draw!=null)draw.releasePanelResources();releaseTarget();releaseWarmSurface();if(context!=null)context.resetGLAll();
        }finally{isolated.leave();}
    }
    private void releaseWarmSurface(){if(warmSurface!=null){warmSurface.close();warmSurface=null;}}
    private void releaseTarget(){
        if(surface!=null){surface.close();surface=null;}if(target!=null){target.close();target=null;}
        if(glfwGetCurrentContext()!=0&&glfwGetCurrentContext()!=glContext){if(aliasFramebuffer!=0)glDeleteFramebuffers(aliasFramebuffer);if(stencilBuffer!=0)glDeleteRenderbuffers(stencilBuffer);}
        aliasFramebuffer=stencilBuffer=0;width=height=0;sourceFramebuffer=sourceColor=sourceFormat=-1;
    }
    /** General-resource cleanup callback, always in this renderer's context if one exists. */
    public void withIsolated(Runnable action){requireGame();if(isolated==null){action.run();return;}isolated.enter();try{action.run();}finally{isolated.leave();}}
    /** Existing Minecraft texture only; raw RGBA in the Hub context, never RenderSystem's global texture cache. */
    public SkinPixels readSkinTexture(int texture){
        requireGame();if(!commonReady())throw new IllegalStateException("Hub core must be ready before skin preparation");
        isolated.enter();try{context.resetGLAll();return readRgba(texture);}finally{try{context.resetGLAll();}finally{isolated.leave();}}
    }
    static SkinPixels readRgba(int texture){
        int active=glGetInteger(GL_ACTIVE_TEXTURE);glActiveTexture(GL_TEXTURE0);int binding=glGetInteger(GL_TEXTURE_BINDING_2D);
        int pbo=glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING),alignment=glGetInteger(GL_PACK_ALIGNMENT),row=glGetInteger(GL_PACK_ROW_LENGTH),skipRows=glGetInteger(GL_PACK_SKIP_ROWS),skipPixels=glGetInteger(GL_PACK_SKIP_PIXELS),imageHeight=glGetInteger(GL_PACK_IMAGE_HEIGHT),skipImages=glGetInteger(GL_PACK_SKIP_IMAGES),swap=glGetInteger(GL_PACK_SWAP_BYTES),lsb=glGetInteger(GL_PACK_LSB_FIRST);
        ByteBuffer pixels=null;
        try{
            if(!glIsTexture(texture))throw new IllegalArgumentException("Skin texture unavailable");glBindTexture(GL_TEXTURE_2D,texture);
            int w=glGetTexLevelParameteri(GL_TEXTURE_2D,0,GL_TEXTURE_WIDTH),h=glGetTexLevelParameteri(GL_TEXTURE_2D,0,GL_TEXTURE_HEIGHT);
            if(w<64||w>4096||w%64!=0||h>4096||(h!=w&&h*2!=w))throw new IllegalArgumentException("Unsupported skin dimensions");
            glBindBuffer(GL_PIXEL_PACK_BUFFER,0);glPixelStorei(GL_PACK_ALIGNMENT,1);glPixelStorei(GL_PACK_ROW_LENGTH,0);glPixelStorei(GL_PACK_SKIP_ROWS,0);glPixelStorei(GL_PACK_SKIP_PIXELS,0);glPixelStorei(GL_PACK_IMAGE_HEIGHT,0);glPixelStorei(GL_PACK_SKIP_IMAGES,0);glPixelStorei(GL_PACK_SWAP_BYTES,0);glPixelStorei(GL_PACK_LSB_FIRST,0);
            pixels=MemoryUtil.memAlloc(Math.multiplyExact(Math.multiplyExact(w,h),4));glGetTexImage(GL_TEXTURE_2D,0,GL_RGBA,GL_UNSIGNED_BYTE,pixels);checkGl("skin RGBA read");byte[] rgba=new byte[pixels.remaining()];pixels.get(rgba);return new SkinPixels(w,h,rgba);
        }finally{
            if(pixels!=null){pixels.clear();MemoryUtil.memSet(MemoryUtil.memAddress(pixels),0,pixels.capacity());MemoryUtil.memFree(pixels);}
            glBindBuffer(GL_PIXEL_PACK_BUFFER,pbo);glPixelStorei(GL_PACK_ALIGNMENT,alignment);glPixelStorei(GL_PACK_ROW_LENGTH,row);glPixelStorei(GL_PACK_SKIP_ROWS,skipRows);glPixelStorei(GL_PACK_SKIP_PIXELS,skipPixels);glPixelStorei(GL_PACK_IMAGE_HEIGHT,imageHeight);glPixelStorei(GL_PACK_SKIP_IMAGES,skipImages);glPixelStorei(GL_PACK_SWAP_BYTES,swap);glPixelStorei(GL_PACK_LSB_FIRST,lsb);glBindTexture(GL_TEXTURE_2D,binding);glActiveTexture(active);
        }
    }
    /** Upload the session skin on an owned surface before a later handbook frame; no private scratch survives. */
    public void prepareSkin(Consumer<HubDraw> content){requireGame();if(!commonReady())return;isolated.enter();try{warmDraw(content);}finally{try{releaseWarmSurface();draw.releasePanelResources();}finally{isolated.leave();}}}
    @Override public void close(){
        if(closed)return;requireGame();if(isolated==null){closed=true;return;}
        isolated.enter();try{
            try{if(context!=null){context.resetGLAll();context.flushAndSubmit(true);}}
            finally{try{if(draw!=null)draw.close();}finally{try{releaseTarget();releaseWarmSurface();}finally{if(context!=null)context.close();}}}
        }finally{draw=null;context=null;try{isolated.leave();}finally{try{isolated.close();}finally{isolated=null;closed=true;}}}
    }
}
