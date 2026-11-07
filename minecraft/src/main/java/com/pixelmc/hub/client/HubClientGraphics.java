/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.pixelmc.hub.client.render.HubRenderResources;
import com.pixelmc.hub.client.render.HubRenderer;
import com.pixelmc.hub.client.ui.HubHead;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.event.TickEvent;
import static org.lwjgl.glfw.GLFW.glfwGetCurrentContext;

/** Resource readiness only. Never creates/renders a business HubView or consumes welcome state. */
public final class HubClientGraphics {
    private static final HubRenderResources RESOURCES=new HubRenderResources();
    private static final HubSkinState SKIN=new HubSkinState();
    private static final AtomicBoolean CLEAR_SKIN=new AtomicBoolean(),SKIN_WINDOW=new AtomicBoolean();
    private static long observeUntil,nextObservation;
    private static boolean initialized;
    private HubClientGraphics(){}
    public static void initialize(){
        if(initialized)return;initialized=true;
        RESOURCES.beforeDispose(renderer->{renderer.withIsolated(HubHead::clearSkin);HubRankingSkins.clear();});
        MinecraftForge.EVENT_BUS.addListener(HubClientGraphics::renderTick);
        MinecraftForge.EVENT_BUS.addListener(HubClientGraphics::shutdown);
    }
    public static void registerReload(RegisterClientReloadListenersEvent event){
        event.registerReloadListener(new SimplePreparableReloadListener<Void>(){
            @Override protected Void prepare(ResourceManager manager,ProfilerFiller profiler){return null;}
            @Override protected void apply(Void ignored,ResourceManager manager,ProfilerFiller profiler){
                // Reload preparation/apply need not own a current GL context.
                CLEAR_SKIN.set(true);RESOURCES.requestReload();
            }
        });
    }
    public static HubRenderResources.Lease acquire(){
        clearSkinIfRequested();return RESOURCES.acquire();
    }
    public static boolean valid(HubRenderResources.Lease lease){return RESOURCES.valid(lease);}
    public static void release(HubRenderResources.Lease lease){RESOURCES.release(lease);}
    public static void failed(HubRenderResources.Lease lease){try{RESOURCES.fail(lease);}finally{SKIN.clear();observeUntil=0;}}
    public static void sessionChanged(){CLEAR_SKIN.set(true);}
    public static void panelOpening(){SKIN_WINDOW.set(true);}
    public static void requestRankingSkins(java.util.Map<String,String> players) {
        if(players.isEmpty())return;
        try { HubRankingSkins.request(players); }
        catch(RuntimeException unavailable) { LogUtils.getLogger().debug("Leaderboard skins are temporarily unavailable",unavailable); }
    }
    private static void clearSkinIfRequested(){
        if(!CLEAR_SKIN.getAndSet(false))return;
        HubRenderer renderer=RESOURCES.current();
        try{if(renderer!=null&&!renderer.isClosed())renderer.withIsolated(HubHead::clearSkin);else HubHead.clearSkin();}
        finally{SKIN.clear();HubRankingSkins.clear();observeUntil=nextObservation=0;}
    }
    private static boolean ownsGameContext(Minecraft minecraft){
        return RenderSystem.isOnRenderThread()&&minecraft.getWindow()!=null&&glfwGetCurrentContext()==minecraft.getWindow().getWindow();
    }
    private static void renderTick(TickEvent.RenderTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        Minecraft minecraft=Minecraft.getInstance();
        if(!ownsGameContext(minecraft))return;
        try{
            clearSkinIfRequested();if(minecraft.getOverlay()!=null)return;RESOURCES.tick();prepareSkin(minecraft,System.nanoTime());HubRankingSkins.capture(RESOURCES.current(),System.nanoTime());
        }catch(Throwable failure){LogUtils.getLogger().warn("PixelMC-Hub resource readiness step failed; game rendering continues",failure);}
    }
    private static void prepareSkin(Minecraft minecraft,long now){
        if(minecraft.player==null||minecraft.level==null){
            if(SKIN.player()!=null){CLEAR_SKIN.set(true);clearSkinIfRequested();}
            return;
        }
        UUID player=minecraft.player.getUUID();
        if(!player.equals(SKIN.player())){
            HubRenderer resource=RESOURCES.current();
            try{if(resource!=null)resource.withIsolated(HubHead::clearSkin);else HubHead.clearSkin();}
            finally{SKIN.session(player,now);observeUntil=now+30_000_000_000L;nextObservation=0;}
        }
        if(SKIN_WINDOW.getAndSet(false)){
            // A repeated H open retains the current avatar; recapture starts after its initial display frame.
            SKIN.retryWindow(now+250_000_000L);observeUntil=now+30_000_000_000L;nextObservation=0;
        }
        if(now<=observeUntil&&now>=nextObservation){
            nextObservation=now+500_000_000L;
            // Uses only vanilla's existing skin flow. This getter MAY start vanilla's normal asynchronous
            // skin registration; Hub adds no HTTP client/download service and never waits for that network.
            var location=minecraft.player.getSkinTextureLocation();
            var texture=minecraft.getTextureManager().getTexture(location,null);
            if(texture!=null)SKIN.observe(new HubSkinState.Key(player,location.toString(),texture.getId(),RESOURCES.generation()),now);
        }
        HubRenderer renderer=RESOURCES.current();if(renderer==null||!renderer.commonReady())return;
        HubSkinState.Capture capture=SKIN.claim(now);if(capture==null)return;
        long began=System.nanoTime();boolean changed=false;
        try(HubRenderer.SkinPixels pixels=renderer.readSkinTexture(capture.key().texture())){
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pixels.rgba()));
            if(!SKIN.current(capture))return;
            changed=SKIN.changed(capture,hash);
            if(changed)renderer.prepareSkin(draw->{
                HubHead.setSkinRgba(pixels.width(),pixels.height(),pixels.rgba());
                HubHead.draw2D(draw,8,8,32);HubHead.draw(draw,55,55,64,-22);
            });
            SKIN.confirm(capture,hash);
            LogUtils.getLogger().info("PixelMC-Hub session skin preparation attempt={} changed={} elapsedMs={}",
                capture.attempt()+1,changed,String.format(Locale.ROOT,"%.3f",(System.nanoTime()-began)/1_000_000.0));
        }catch(Exception unavailable){
            // Not uploaded / missing dimensions is not a successful capture. A finite later slot or
            // next panel-open/reload can retry, including same-location/same-id default->real replacement.
            LogUtils.getLogger().debug("PixelMC-Hub existing skin texture is not ready for this finite attempt",unavailable);
        }
    }
    private static void shutdown(GameShuttingDownEvent event){
        Minecraft minecraft=Minecraft.getInstance();
        if(!ownsGameContext(minecraft))throw new IllegalStateException("Hub shutdown requires the current game render context");
        CLEAR_SKIN.set(true);try{try{clearSkinIfRequested();}finally{try{RESOURCES.close();}finally{SKIN.clear();}}}catch(Throwable failure){LogUtils.getLogger().warn("PixelMC-Hub shutdown cleanup reported a failure",failure);}
    }
}
