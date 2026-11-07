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
        long generation;boolean subscribed,updatesCapable,rankingsCapable,communityCapable;
        Admission(net.minecraft.server.level.ServerPlayer owner){this.owner=owner;}
        void clear(){cache.clear();pages.clear();subscribed=false;updatesCapable=false;rankingsCapable=false;communityCapable=false;}
        void subscribe(long value){generation=value;subscribed=true;cache.clear();pages.clear();}
        String stream(Request request){
            if(request.module.equals("titles")&&request.action.equals("catalogue")) {
                String cursor=HubProtocol.object(request.payload,HubProtocol.REQUEST_BYTES).get("cursor").getAsString();
                return "titles/page/"+pages.getOrDefault(cursor,-1);
            }
            if(request.module.equals("rankings")&&!request.action.equals("snapshot")) {
                var query=HubProtocol.payload(request.module,request.action,request.payload);
                if(request.action.equals("window"))return "rankings/"+query.get("board").getAsString()+"/window/"+query.get("page").getAsString()+"/"+query.get("count").getAsString();
                return "rankings/"+query.get("board").getAsString()+"/"+(request.action.equals("page")?query.get("page").getAsString():"self");
            }
            if(request.module.equals("community")) return HubCommunityProtocol.stream(request.action,HubProtocol.payload(request.module,request.action,request.payload));
            return request.module+"/snapshot";
        }
        String response(Request request,String stream,String payload) {
            if(request.module.equals("community")&&HubCommunityProtocol.write(request.action))return payload;
            if(!subscribed||request.module.equals("hub")||request.module.equals("updates")||java.util.Set.of("calendar","calendarSync","read").contains(request.action))return payload;
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
        static Request decode(FriendlyByteBuf b) {long session=b.readLong(),id=b.readLong();String module=b.readUtf(16),action=b.readUtf(32);return new Request(session,id,module,action,b.readUtf(HubProtocol.maximumRequest(module,action)));}
        void encode(FriendlyByteBuf b) {b.writeLong(session);b.writeLong(id);b.writeUtf(module,16);b.writeUtf(action,32);b.writeUtf(payload,HubProtocol.maximumRequest(module,action));}
    }
    public record Response(long session, long id, String module, String action, boolean success, String payload) {
        static Response decode(FriendlyByteBuf b) {return new Response(b.readLong(),b.readLong(),b.readUtf(16),b.readUtf(32),b.readBoolean(),b.readUtf(HubProtocol.RESPONSE_BYTES));}
        void encode(FriendlyByteBuf b) {b.writeLong(session);b.writeLong(id);b.writeUtf(module,16);b.writeUtf(action,32);b.writeBoolean(success);b.writeUtf(payload,HubProtocol.RESPONSE_BYTES);}
    }
    public record UpdatesPart(long session,long id,String action,int index,int count,int bytes,byte[] data) {
        static UpdatesPart decode(FriendlyByteBuf b){return new UpdatesPart(b.readLong(),b.readLong(),b.readUtf(32),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readByteArray(HubFragmentBuffer.PART_BYTES));}
        void encode(FriendlyByteBuf b){b.writeLong(session);b.writeLong(id);b.writeUtf(action,32);b.writeVarInt(index);b.writeVarInt(count);b.writeVarInt(bytes);b.writeByteArray(data);}
    }
    private static java.util.function.Consumer<UpdatesPart> partsReceiver=part->{};
    public static void partsReceiver(java.util.function.Consumer<UpdatesPart> receiver){partsReceiver=receiver;}
    public record UpdatesChanged(long session) {
        static UpdatesChanged decode(FriendlyByteBuf buffer){return new UpdatesChanged(buffer.readLong());}
        void encode(FriendlyByteBuf buffer){buffer.writeLong(session);}
    }
    private static java.util.function.LongConsumer updatesReceiver=session->{};
    public static void updatesReceiver(java.util.function.LongConsumer receiver){updatesReceiver=receiver;}
    /** Called only after a confirmed Manager change. Connection generation prevents cross-session hints. */
    public static void updatesChanged(net.minecraft.server.MinecraftServer server){updatesChanged(server,null);}
    public static void updatesChanged(net.minecraft.server.MinecraftServer server,UUID playerId){
        if(server==null)return;
        server.execute(()->{for(var item:ADMISSION.entrySet()){
            Admission state=item.getValue();if(!state.subscribed||!state.updatesCapable||playerId!=null&&!playerId.equals(item.getKey()))continue;
            var player=server.getPlayerList().getPlayer(item.getKey());if(player!=state.owner||player.hasDisconnected())continue;
            CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new UpdatesChanged(state.generation));
        }});
    }
    public record RankingsChanged(long session,int mask) {
        static RankingsChanged decode(FriendlyByteBuf buffer){return new RankingsChanged(buffer.readLong(),buffer.readUnsignedByte()&7);}
        void encode(FriendlyByteBuf buffer){buffer.writeLong(session);buffer.writeByte(mask);}
    }
    private static java.util.function.BiConsumer<Long,Integer> rankingsReceiver=(session,mask)->{};
    public static void rankingsReceiver(java.util.function.BiConsumer<Long,Integer> receiver){rankingsReceiver=receiver;}
    public static void rankingsChanged(net.minecraft.server.MinecraftServer server) {rankingsChanged(server,7);}
    public static void rankingsChanged(net.minecraft.server.MinecraftServer server,int mask) {
        if(server==null)return;
        server.execute(()->{
            for(var entry:ADMISSION.entrySet()) {
                Admission state=entry.getValue();
                if(!state.subscribed || !state.rankingsCapable)continue;
                var player=server.getPlayerList().getPlayer(entry.getKey());
                if(player!=state.owner || player.hasDisconnected())continue;
                CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new RankingsChanged(state.generation,mask&7));
            }
        });
    }
    public record CommunityChanged(long session) {
        static CommunityChanged decode(FriendlyByteBuf buffer){return new CommunityChanged(buffer.readLong());}
        void encode(FriendlyByteBuf buffer){buffer.writeLong(session);}
    }
    private static java.util.function.LongConsumer communityReceiver=session->{};
    public static void communityReceiver(java.util.function.LongConsumer receiver){communityReceiver=receiver;}
    public static void communityChanged(net.minecraft.server.MinecraftServer server) {
        if(server==null)return;
        server.execute(()->{
            for(var entry:ADMISSION.entrySet()) {
                Admission state=entry.getValue();
                if(!state.subscribed||!state.communityCapable)continue;
                var player=server.getPlayerList().getPlayer(entry.getKey());
                if(player!=state.owner||player.hasDisconnected())continue;
                CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new CommunityChanged(state.generation));
            }
        });
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
                if(request.module.equals("updates"))admission.updatesCapable=true;if(request.module.equals("rankings"))admission.rankingsCapable=true;if(request.module.equals("community"))admission.communityCapable=true;
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
        CHANNEL.messageBuilder(UpdatesChanged.class,2,NetworkDirection.PLAY_TO_CLIENT).encoder(UpdatesChanged::encode).decoder(UpdatesChanged::decode)
            .consumerMainThread((hint,context)->updatesReceiver.accept(hint.session())).add();
        CHANNEL.messageBuilder(UpdatesPart.class,3,NetworkDirection.PLAY_TO_CLIENT).encoder(UpdatesPart::encode).decoder(UpdatesPart::decode)
            .consumerMainThread((part,context)->partsReceiver.accept(part)).add();
        CHANNEL.messageBuilder(RankingsChanged.class,4,NetworkDirection.PLAY_TO_CLIENT).encoder(RankingsChanged::encode).decoder(RankingsChanged::decode)
            .consumerMainThread((hint,context)->rankingsReceiver.accept(hint.session(),hint.mask())).add();
        CHANNEL.messageBuilder(CommunityChanged.class,5,NetworkDirection.PLAY_TO_CLIENT).encoder(CommunityChanged::encode).decoder(CommunityChanged::decode)
            .consumerMainThread((hint,context)->communityReceiver.accept(hint.session())).add();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event)->
            ADMISSION.computeIfPresent(event.getEntity().getUUID(),(id,state)->{if(state.owner!=event.getEntity())return state;state.clear();return null;}));
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.server.ServerStoppedEvent event)->{ADMISSION.values().forEach(Admission::clear);ADMISSION.clear();});
    }
    private static void reply(net.minecraft.server.level.ServerPlayer player,Request request,boolean success,String payload) {
        if(success&&request.module.equals("updates")){
            byte[] bytes=payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if(bytes.length>HubProtocol.RESPONSE_BYTES){
                if(bytes.length>HubFragmentBuffer.MAX_BYTES)throw new IllegalArgumentException("更新日志响应过大，请缩小查询范围");
                int count=(bytes.length+HubFragmentBuffer.PART_BYTES-1)/HubFragmentBuffer.PART_BYTES;
                for(int i=0;i<count;i++){int start=i*HubFragmentBuffer.PART_BYTES;byte[] part=java.util.Arrays.copyOfRange(bytes,start,Math.min(bytes.length,start+HubFragmentBuffer.PART_BYTES));CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new UpdatesPart(request.session,request.id,request.action,i,count,bytes.length,part));}
                return;
            }
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new Response(request.session,request.id,request.module,request.action,success,payload));
    }
}
