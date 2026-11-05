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
        final net.minecraft.server.level.ServerPlayer owner;
        final HubSyncCache cache=new HubSyncCache();
        final Map<String,Integer> pages=new java.util.HashMap<>();
        long generation;boolean subscribed;
        Admission(net.minecraft.server.level.ServerPlayer owner){this.owner=owner;}
        void clear(){cache.clear();pages.clear();subscribed=false;}
        void subscribe(long value){generation=value;subscribed=true;cache.clear();pages.clear();}
        String stream(Request request){
            if(request.module.equals("titles")&&request.action.equals("catalogue")) {
                String cursor=HubProtocol.object(request.payload,HubProtocol.REQUEST_BYTES).get("cursor").getAsString();
                return "titles/page/"+pages.getOrDefault(cursor,-1);
            }
            return request.module+"/snapshot";
        }
        String response(Request request,String stream,String payload) {
            if(!subscribed||request.module.equals("hub")||java.util.Set.of("calendar","calendarSync","read").contains(request.action))return payload;
            var data=HubProtocol.object(payload,HubProtocol.RESPONSE_BYTES);
            if(request.module.equals("titles")) {
                int page=0;if(request.action.equals("catalogue"))page=Integer.parseInt(stream.substring(stream.lastIndexOf('/')+1));else pages.clear();
                if(data.has("nextCursor")&&!data.get("nextCursor").isJsonNull()&&pages.size()<100)
                    pages.put(data.get("nextCursor").getAsString(),page+1);
            }
            return cache.encode(stream,data).toString();
        }
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
                try{HubProtocol.payload(request.module,request.action,request.payload);}catch(RuntimeException invalid){reply(player,request,false,"玩家操作参数无效");return;}
                Admission admission=ADMISSION.compute(player.getUUID(),(id,old)->old!=null&&old.owner==player?old:new Admission(player));
                if(request.module.equals("hub")&&request.action.equals("subscribe")) {
                    if(!admission.enter()){reply(player,request,false,"请求过于频繁，请稍后重试");return;}
                    admission.subscribe(request.session);admission.leave();reply(player,request,true,HubProviderBridge.discover().toString());return;
                }
                if(admission.subscribed&&admission.generation!=request.session){reply(player,request,false,"玩家同步会话已失效");return;}
                if (!admission.enter()) {reply(player,request,false,"请求过于频繁，请稍后重试");return;}
                String stream;
                try{stream=admission.stream(request);}catch(RuntimeException invalid){admission.leave();reply(player,request,false,"玩家操作参数无效");return;}
                HubProviderBridge.request(player.getUUID(),request.module,request.action,request.payload).whenComplete((result,error)-> {
                    admission.leave(); var server=player.getServer(); if (server==null) return;
                    server.execute(()-> {
                        // Captured player object must still be the current authenticated connection.
                        if (server.getPlayerList().getPlayer(player.getUUID()) != player || player.hasDisconnected()
                            || ADMISSION.get(player.getUUID())!=admission || admission.subscribed&&admission.generation!=request.session) return;
                        if(error!=null){reply(player,request,false,HubProviderBridge.safeError(error));return;}
                        try{reply(player,request,true,admission.response(request,stream,result));}
                        catch(RuntimeException invalid){reply(player,request,false,"玩家同步数据无法处理，请重新读取");}
                    });
                });
            }).add();
        CHANNEL.messageBuilder(Response.class,1,NetworkDirection.PLAY_TO_CLIENT).encoder(Response::encode).decoder(Response::decode)
            .consumerMainThread((response,context)->clientReceiver.accept(response)).add();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event)->
            ADMISSION.computeIfPresent(event.getEntity().getUUID(),(id,state)->{if(state.owner!=event.getEntity())return state;state.clear();return null;}));
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.server.ServerStoppedEvent event)->{ADMISSION.values().forEach(Admission::clear);ADMISSION.clear();});
    }
    private static void reply(net.minecraft.server.level.ServerPlayer player,Request request,boolean success,String payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new Response(request.session,request.id,request.module,request.action,success,payload));
    }
}
