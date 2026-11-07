/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client;

import com.google.gson.*;
import com.pixelmc.hub.network.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Disposable 64MiB/256-query LRU. Use only on the session's existing single IO worker. */
final class HubUpdatesCache {
    static final long MAX_BYTES=64L*1024*1024;static final int MAX_FILES=256;
    private final Path root;
    HubUpdatesCache(Path root){this.root=root.toAbsolutePath().normalize();}
    static String identity(JsonObject manager){try{JsonObject key=new JsonObject();String player=UUID.fromString(manager.getAsJsonObject("player").get("uuid").getAsString()).toString(),server=manager.getAsJsonObject("scope").get("serverId").getAsString();if(server.isBlank()||server.length()>512)return "";key.addProperty("player",player);key.addProperty("serverId",server);return HubUpdatesDelta.hash(key);}catch(RuntimeException unavailable){return "";}}
    static String key(String action,JsonObject query){JsonObject canonical=query.deepCopy();canonical.remove("known");JsonObject key=new JsonObject();key.addProperty("action",action);key.add("query",canonical);return HubUpdatesDelta.hash(key);}
    private Path file(String scope,String key){if(!scope.matches("[a-f0-9]{64}")||!key.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("缓存作用域无效");return root.resolve(scope).resolve(key+".json");}
    JsonObject load(String scope,String key){if(scope.isEmpty())return null;try{Path file=file(scope,key);if(!Files.isRegularFile(file)||Files.size(file)>HubFragmentBuffer.MAX_BYTES+1024)return null;JsonObject saved=HubProtocol.object(Files.readString(file,StandardCharsets.UTF_8),HubFragmentBuffer.MAX_BYTES+1024);JsonObject data=saved.getAsJsonObject("data");if(saved.get("schema").getAsInt()!=1||!scope.equals(saved.get("scope").getAsString())||!key.equals(saved.get("key").getAsString())||!HubUpdatesDelta.hash(data).equals(saved.get("checksum").getAsString()))return null;HubUpdatesDelta.pack(data);Files.setLastModifiedTime(file,java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));return data.deepCopy();}catch(Exception bad){return null;}}
    void save(String scope,String key,JsonObject data){if(scope.isEmpty())return;Path temp=null;try{HubProtocol.object(data.toString(),HubFragmentBuffer.MAX_BYTES);JsonObject saved=new JsonObject();saved.addProperty("schema",1);saved.addProperty("scope",scope);saved.addProperty("key",key);saved.addProperty("checksum",HubUpdatesDelta.hash(data));saved.add("data",data.deepCopy());Path file=file(scope,key);Files.createDirectories(file.getParent());temp=Files.createTempFile(file.getParent(),"updates-",".tmp");Files.writeString(temp,saved.toString(),StandardCharsets.UTF_8);try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException unsupported){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}prune();}catch(Exception ignored){}finally{if(temp!=null)try{Files.deleteIfExists(temp);}catch(Exception ignored){}}}
    void remove(String scope,String key){if(scope.isEmpty())return;try{Files.deleteIfExists(file(scope,key));}catch(Exception ignored){}}
    void clear(String scope){if(scope.isEmpty())return;try(var files=Files.list(file(scope,"0".repeat(64)).getParent())){for(Path file:files.filter(p->p.getFileName().toString().matches("[a-f0-9]{64}\\.json")).toList())Files.deleteIfExists(file);}catch(Exception ignored){}}
    private void prune()throws java.io.IOException{if(!Files.exists(root))return;List<Path> files;try(var paths=Files.walk(root,2)){files=paths.filter(Files::isRegularFile).filter(p->p.getFileName().toString().matches("[a-f0-9]{64}\\.json")).sorted(Comparator.comparingLong(this::modified)).toList();}long bytes=0;for(Path p:files)bytes+=Files.size(p);int count=files.size();for(Path p:files){if(bytes<=MAX_BYTES&&count<=MAX_FILES)break;long length=Files.size(p);Files.deleteIfExists(p);bytes-=length;count--;}}
    private long modified(Path p){try{return Files.getLastModifiedTime(p).toMillis();}catch(Exception ignored){return 0;}}
}
