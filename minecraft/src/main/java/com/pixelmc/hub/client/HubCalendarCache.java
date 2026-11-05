/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client;

import com.google.gson.*;
import com.pixelmc.hub.network.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Disposable, scoped archived-month cache. It is not an ownership or mutation authority. */
final class HubCalendarCache {
    private final Path root;
    HubCalendarCache(Path root){this.root=root.toAbsolutePath().normalize();}
    static String identity(JsonObject snapshot) {
        try {
            JsonObject key=new JsonObject(),scope=snapshot.getAsJsonObject("scope");
            String player=UUID.fromString(snapshot.getAsJsonObject("player").get("uuid").getAsString()).toString();
            key.addProperty("player",player);
            for(String name:new String[]{"serverId","archiveId"}){String value=scope.get(name).getAsString();if(value.isBlank()||value.length()>512)return "";key.addProperty(name,value);}
            return HubCalendarDelta.hash(key);
        }catch(RuntimeException unavailable){return "";}
    }
    private Path path(String scope,String month) {
        if(!scope.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Cache scope unavailable");
        return root.resolve(scope).resolve(HubCalendarDelta.month(month)+".json");
    }
    JsonObject load(String scope,String month) {
        if(scope.isEmpty())return null;
        try {
            Path file=path(scope,month);if(!Files.isRegularFile(file)||Files.size(file)>HubProtocol.RESPONSE_BYTES)return null;
            JsonObject stored=HubProtocol.object(Files.readString(file,StandardCharsets.UTF_8),HubProtocol.RESPONSE_BYTES);
            if(stored.get("schema").getAsInt()!=1||!scope.equals(stored.get("scope").getAsString())||!month.equals(stored.get("month").getAsString()))return null;
            JsonObject data=stored.getAsJsonObject("data");HubCalendarDelta.rows(data);
            if(!month.equals(data.get("month").getAsString())||!HubCalendarDelta.hash(data).equals(stored.get("checksum").getAsString()))return null;
            return data.deepCopy();
        }catch(Exception corrupt){return null;}
    }
    void save(String scope,JsonObject calendar) {
        if(scope.isEmpty())return;
        Path temp=null;
        try {
            HubCalendarDelta.rows(calendar);String month=calendar.get("month").getAsString();Path file=path(scope,month);
            JsonObject stored=new JsonObject();stored.addProperty("schema",1);stored.addProperty("scope",scope);stored.addProperty("month",month);
            stored.addProperty("checksum",HubCalendarDelta.hash(calendar));stored.add("data",calendar.deepCopy());
            String json=stored.toString();HubProtocol.object(json,HubProtocol.RESPONSE_BYTES);
            Files.createDirectories(file.getParent());temp=Files.createTempFile(file.getParent(),"month-",".tmp");Files.writeString(temp,json,StandardCharsets.UTF_8);
            try{Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException unavailable){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
        }catch(Exception unavailable){/* Cache failure never blocks authoritative online data. */}
        finally{if(temp!=null)try{Files.deleteIfExists(temp);}catch(Exception ignored){}}
    }
}
