/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.network;

import com.pixelmc.hub.PixelMCHub;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Authenticated optional player-only Minecraft transport, never an HTTP/admin tunnel. */
public final class HubNetwork {
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(PixelMCHub.MOD_ID, "player"),
        () -> "1", version -> version.equals("1") || version.equals(NetworkRegistry.ABSENT) || version.equals(NetworkRegistry.ACCEPTVANILLA),
        version -> version.equals("1") || version.equals(NetworkRegistry.ABSENT) || version.equals(NetworkRegistry.ACCEPTVANILLA));
    private static final Map<UUID, Admission> ADMISSION = new ConcurrentHashMap<>();
    private static java.util.function.Consumer<Response> clientReceiver=response->{};
    private static final class Admission {
        long window = System.nanoTime(); int requests, active;
        synchronized boolean enter() {
            long now = System.nanoTime(); if (now-window > 10_000_000_000L) {window=now;requests=0;}
            if (requests >= 24 || active >= 4) return false; requests++; active++; return true;
        }
        synchronized void leave() {active=Math.max(0,active-1);}
    }
    public record Request(long session, long id, String module, String action, String payload) {
        static Request decode(FriendlyByteBuf b) {return new Request(b.readLong(), b.readLong(), b.readUtf(16), b.readUtf(32), b.readUtf(HubProtocol.REQUEST_BYTES));}
        void encode(FriendlyByteBuf b) {b.writeLong(session);b.writeLong(id);b.writeUtf(module,16);b.writeUtf(action,32);b.writeUtf(payload,HubProtocol.REQUEST_BYTES);}
    }
    public record Response(long session, long id, String module, String action, boolean success, String payload) {
        static Response decode(FriendlyByteBuf b) {return new Response(b.readLong(),b.readLong(),b.readUtf(16),b.readUtf(32),b.readBoolean(),b.readUtf(HubProtocol.RESPONSE_BYTES));}
        void encode(FriendlyByteBuf b) {b.writeLong(session);b.writeLong(id);b.writeUtf(module,16);b.writeUtf(action,32);b.writeBoolean(success);b.writeUtf(payload,HubProtocol.RESPONSE_BYTES);}
    }
    private HubNetwork() {}
    public static void clientReceiver(java.util.function.Consumer<Response> receiver){clientReceiver=receiver;}
    public static void initialize() {
        CHANNEL.messageBuilder(Request.class,0,NetworkDirection.PLAY_TO_SERVER).encoder(Request::encode).decoder(Request::decode)
            .consumerMainThread((request, supplier) -> {
                var context=supplier.get(); var player=context.getSender();
                if (player == null) return;
                if (!HubProtocol.allowed(request.module,request.action)) { reply(player,request,false,"不支持的玩家操作"); return; }
                Admission admission=ADMISSION.computeIfAbsent(player.getUUID(),id->new Admission());
                if (!admission.enter()) {reply(player,request,false,"请求过于频繁，请稍后重试");return;}
                HubProviderBridge.request(player.getUUID(),request.module,request.action,request.payload).whenComplete((result,error)-> {
                    admission.leave(); var server=player.getServer(); if (server==null) return;
                    server.execute(()-> {
                        // Captured player object must still be the current authenticated connection.
                        if (server.getPlayerList().getPlayer(player.getUUID()) != player || player.hasDisconnected()) return;
                        reply(player,request,error==null,error==null?result:HubProviderBridge.safeError(error));
                    });
                });
            }).add();
        CHANNEL.messageBuilder(Response.class,1,NetworkDirection.PLAY_TO_CLIENT).encoder(Response::encode).decoder(Response::decode)
            .consumerMainThread((response,context)->clientReceiver.accept(response)).add();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event)->ADMISSION.remove(event.getEntity().getUUID()));
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.server.ServerStoppedEvent event)->ADMISSION.clear());
    }
    private static void reply(net.minecraft.server.level.ServerPlayer player,Request request,boolean success,String payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new Response(request.session,request.id,request.module,request.action,success,payload));
    }
}
