/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.pixelmc.hub.client.render.HubRenderer;
import com.pixelmc.hub.client.ui.HubHead;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/** Bounded session cache using Minecraft's existing skin registration and render context. */
final class HubRankingSkins {
    private static final int MAX_PLAYERS=64;
    private static final Map<UUID,Entry> requests=new LinkedHashMap<>(16,.75f,true);
    private static long generation;
    private static Set<UUID> visible=Set.of();
    private static final class Entry {
        final UUID id;
        ResourceLocation location;
        int attempts;
        boolean captured;
        long dueAt;
        Entry(UUID id) { this.id=id; }
    }
    private HubRankingSkins() {}

    static void clear() { requests.clear();visible=Set.of();generation++; }

    static void request(Map<String,String> players) {
        Minecraft minecraft=Minecraft.getInstance();
        var connection=minecraft.getConnection();
        if(connection==null || minecraft.player==null)return;
        long expected=generation;
        Set<UUID> nextVisible=new HashSet<>();
        for(var player:players.entrySet()) {
            UUID id=UUID.fromString(player.getKey());
            nextVisible.add(id);
            if(id.equals(minecraft.player.getUUID()))continue;
            Entry cached=requests.get(id);
            if(cached!=null) {
                // Re-entering the viewport may need a new raster after LRU eviction. Failed
                // texture attempts stay finite while the visible set is unchanged.
                if(!visible.contains(id) && cached.captured && !HubHead.hasPlayerSkin(id.toString())) {
                    cached.captured=false;cached.attempts=0;cached.dueAt=0;
                }
                continue;
            }
            Entry entry=new Entry(id);requests.put(id,entry);
            while(requests.size()>MAX_PLAYERS)requests.remove(requests.keySet().iterator().next());
            var info=connection.getPlayerInfo(id);
            if(info!=null)entry.location=info.getSkinLocation();
            GameProfile profile=info==null?new GameProfile(id,player.getValue()):info.getProfile();
            minecraft.getSkinManager().registerSkins(profile,(type,location,texture)-> {
                if(type!=MinecraftProfileTexture.Type.SKIN)return;
                minecraft.execute(()-> {
                    if(expected!=generation || minecraft.getConnection()!=connection || requests.get(id)!=entry)return;
                    entry.location=location;entry.attempts=0;entry.dueAt=System.nanoTime();
                });
            },true);
        }
        visible=nextVisible;
    }

    static void capture(HubRenderer renderer,long now) {
        if(renderer==null || !renderer.commonReady())return;
        Minecraft minecraft=Minecraft.getInstance();
        var connection=minecraft.getConnection();
        if(connection==null)return;
        for(Entry entry:requests.values()) {
            if(!visible.contains(entry.id) || entry.location==null || entry.attempts>=3 || now<entry.dueAt)continue;
            var info=connection.getPlayerInfo(entry.id);
            if(info!=null)entry.location=info.getSkinLocation();
            entry.attempts++;
            entry.dueAt=now+(entry.attempts==1?1_000_000_000L:5_000_000_000L);
            var texture=minecraft.getTextureManager().getTexture(entry.location,null);
            if(texture!=null)try(var pixels=renderer.readSkinTexture(texture.getId())) {
                renderer.withIsolated(()->HubHead.setPlayerSkinRgba(entry.id.toString(),pixels.width(),pixels.height(),pixels.rgba()));
                entry.captured=true;
            } catch(RuntimeException unavailable) {
                // A later finite upload attempt or new connection may supply the texture.
            }
            return;
        }
    }
}
