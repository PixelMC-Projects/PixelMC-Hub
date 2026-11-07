/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.render;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import com.mojang.logging.LogUtils;

/** One render-thread owner, versioned screen leases, and no Minecraft/business-view dependency. */
public final class HubRenderResources implements AutoCloseable {
    public record Lease(long generation,long id,HubRenderer renderer){}
    private final Supplier<HubRenderer> factory;
    private final AtomicBoolean reload=new AtomicBoolean();
    private HubRenderer renderer;
    private Thread owner;
    private java.util.function.Consumer<HubRenderer> beforeDispose=renderer->{};
    public void beforeDispose(java.util.function.Consumer<HubRenderer> callback){beforeDispose=callback;}
    private void owner(){if(owner==null)owner=Thread.currentThread();else if(owner!=Thread.currentThread())throw new IllegalStateException("Hub resources require their original render thread");}
    private long generation=1,nextLease,borrowed;
    private boolean failed,closed;
    public HubRenderResources(){this(HubRenderer::new);}
    public HubRenderResources(Supplier<HubRenderer> factory){this.factory=factory;}
    public long generation(){return generation;}
    public HubRenderer current(){return renderer;}
    public boolean borrowed(){return borrowed!=0;}
    public boolean warmupFailed(){return failed;}
    public void requestReload(){reload.set(true);}
    public boolean reloadPending(){return reload.get();}
    private void resetIfNeeded(){
        if(!reload.getAndSet(false))return;
        try{dispose();failed=false;}finally{generation++;}
    }
    private HubRenderer ensure(){if(renderer==null)renderer=factory.get();return renderer;}
    /** Called from a validated render event; advances one phase only while no screen owns the renderer. */
    public void tick(){
        if(closed)return;owner();
        try{resetIfNeeded();if(borrowed!=0||failed)return;HubRenderer resource=ensure();if(!resource.warmupProgress().complete()&&resource.warmupStep())LogUtils.getLogger().info("PixelMC-Hub startup warmup ready totalWorkMs={} assets={} stages={}",resource.warmupProgress().totalMs(),resource.warmupProgress().assetCount(),resource.initializationMillis());}
        catch(Throwable failure){failed=true;try{dispose();}catch(Throwable cleanup){failure.addSuppressed(cleanup);}generation++;LogUtils.getLogger().warn("PixelMC-Hub startup warmup failed; explicit panel open may retry",failure);}
    }
    public Lease acquire(){
        if(closed)throw new IllegalStateException("Hub render resources are shut down");
        owner();try{resetIfNeeded();}catch(RuntimeException|Error failure){failed=true;throw failure;}HubRenderer resource=ensure();failed=false;borrowed=++nextLease;return new Lease(generation,borrowed,resource);
    }
    public boolean valid(Lease lease){return lease!=null&&!closed&&!reload.get()&&lease.generation==generation&&lease.id==borrowed&&lease.renderer==renderer&&!renderer.isClosed();}
    public void release(Lease lease){
        if(!valid(lease))return;owner();
        try{renderer.releasePanelResources();}catch(RuntimeException|Error failure){failed=true;try{dispose();}catch(Throwable cleanup){failure.addSuppressed(cleanup);}finally{generation++;}throw failure;}finally{borrowed=0;}
    }
    public void fail(Lease lease){
        if(!valid(lease))return;owner();failed=true;try{dispose();}finally{generation++;}
    }
    private void dispose(){
        HubRenderer previous=renderer;renderer=null;borrowed=0;
        if(previous!=null){try{beforeDispose.accept(previous);}finally{previous.close();}}
    }
    /** Shutdown happens while the original GL context is still current, never queued for a later tick. */
    @Override public void close(){if(closed)return;owner();try{dispose();}finally{generation++;closed=true;reload.set(false);}}
}
