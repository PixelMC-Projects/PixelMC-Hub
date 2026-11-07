/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.network;

import com.google.gson.*;
import java.util.*;

/** Semantic content units for authenticated updates DTOs. Client hashes never authorize access. */
public final class HubUpdatesDelta {
    public static final int KNOWN_BYTES=60000, MAX_UNITS=800;
    public record Packed(JsonObject root,Map<String,JsonElement> units) {}
    private HubUpdatesDelta(){}
    public static String hash(JsonElement value){return HubCalendarDelta.hash(value);}
    private static JsonObject ref(JsonElement value,Map<String,JsonElement> units){String hash=hash(value);units.put(hash,value.deepCopy());JsonObject ref=new JsonObject();ref.addProperty("_hubRef",hash);return ref;}
    private static void rows(JsonObject parent,String key,Map<String,JsonElement> units){if(!parent.has(key)||!parent.get(key).isJsonArray())return;JsonArray rows=new JsonArray();for(JsonElement row:parent.getAsJsonArray(key))rows.add(ref(row,units));parent.add(key,rows);}
    public static Packed pack(JsonObject dto){
        JsonObject root=dto.deepCopy();Map<String,JsonElement> units=new LinkedHashMap<>();
        if(root.has("entry")&&root.get("entry").isJsonObject()){
            JsonObject entry=root.getAsJsonObject("entry"),meta=entry.deepCopy(),parts=new JsonObject();
            for(String key:List.of("body","bodyDocument"))if(meta.has(key))parts.add(key,ref(meta.remove(key),units));
            String metaHash=hash(meta);units.put(metaHash,meta);parts.addProperty("_hubMerge",metaHash);root.add("entry",parts);
        }
        rows(root,"rows",units);rows(root,"packs",units);
        if(root.has("list")&&root.get("list").isJsonObject())rows(root.getAsJsonObject("list"),"rows",units);
        if(root.has("comments")&&root.get("comments").isJsonObject()){
            JsonObject comments=root.getAsJsonObject("comments");JsonArray result=new JsonArray();
            if(comments.has("rows"))for(JsonElement value:comments.getAsJsonArray("rows")){
                JsonObject meta=value.getAsJsonObject().deepCopy(),parts=new JsonObject();JsonElement replies=meta.remove("replies");
                String metaHash=hash(meta);units.put(metaHash,meta);parts.addProperty("_hubMerge",metaHash);
                if(replies!=null){JsonArray replyRefs=new JsonArray();for(JsonElement reply:replies.getAsJsonArray())replyRefs.add(ref(reply,units));parts.add("replies",replyRefs);}result.add(parts);
            }
            comments.add("rows",result);
        }
        if(units.size()>MAX_UNITS)throw new IllegalArgumentException("更新日志缓存单元过多");
        return new Packed(root,Map.copyOf(units));
    }
    public static Set<String> parseKnown(String value){
        if(value==null||value.length()>KNOWN_BYTES)throw new IllegalArgumentException("更新日志缓存摘要过大");
        // Flat array of literal lowercase hashes only: never feed attacker-controlled nesting to Gson.
        Set<String> result=new HashSet<>();int at=whitespace(value,0);
        if(at>=value.length()||value.charAt(at++)!='[')throw new IllegalArgumentException("更新日志缓存摘要无效");
        at=whitespace(value,at);if(at<value.length()&&value.charAt(at)==']'){at=whitespace(value,at+1);if(at!=value.length())throw new IllegalArgumentException("更新日志缓存摘要无效");return result;}
        while(true){
            if(result.size()>=MAX_UNITS||at+66>value.length()||value.charAt(at)!='"'||value.charAt(at+65)!='"')throw new IllegalArgumentException("更新日志缓存摘要无效");
            String hash=value.substring(at+1,at+65);for(int i=0;i<64;i++){char ch=hash.charAt(i);if(!(ch>='0'&&ch<='9'||ch>='a'&&ch<='f'))throw new IllegalArgumentException("更新日志缓存摘要无效");}
            if(!result.add(hash))throw new IllegalArgumentException("更新日志缓存摘要重复");at=whitespace(value,at+66);
            if(at>=value.length())throw new IllegalArgumentException("更新日志缓存摘要无效");char delimiter=value.charAt(at++);
            if(delimiter==']'){if(whitespace(value,at)!=value.length())throw new IllegalArgumentException("更新日志缓存摘要无效");return result;}
            if(delimiter!=',')throw new IllegalArgumentException("更新日志缓存摘要无效");at=whitespace(value,at);
        }
    }
    private static int whitespace(String value,int at){while(at<value.length()&&" \n\r\t".indexOf(value.charAt(at))>=0)at++;return at;}
    public static String known(JsonObject base){JsonArray result=new JsonArray();if(base!=null)new TreeSet<>(pack(base).units.keySet()).forEach(result::add);return result.toString();}
    public static JsonObject encode(JsonObject dto,Set<String> known){
        Packed packed=pack(dto);JsonObject changed=new JsonObject();packed.units.forEach((hash,value)->{if(!known.contains(hash))changed.add(hash,value);});
        JsonObject out=new JsonObject();out.addProperty("updatesCache",1);out.add("root",packed.root);out.add("units",changed);out.addProperty("checksum",hash(dto));
        // A bounded plain response is the compatible full fallback when ref overhead would exceed transport.
        if(out.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>HubFragmentBuffer.MAX_BYTES)return dto.deepCopy();return out;
    }
    public static JsonObject apply(JsonObject base,JsonObject delta){
        if(!delta.has("updatesCache"))return delta.deepCopy();
        if(delta.get("updatesCache").getAsInt()!=1||!delta.has("units")||delta.getAsJsonObject("units").size()>MAX_UNITS)throw new IllegalArgumentException("更新日志缓存格式无效");
        Map<String,JsonElement> units=new HashMap<>();if(base!=null)units.putAll(pack(base).units);
        for(var item:delta.getAsJsonObject("units").entrySet()){
            if(!item.getKey().matches("[a-f0-9]{64}")||!hash(item.getValue()).equals(item.getKey()))throw new IllegalArgumentException("更新日志缓存单元校验失败");units.put(item.getKey(),item.getValue());
        }
        Budget budget=new Budget();budget.bytes=delta.get("root").toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;JsonObject dto=expand(delta.get("root"),units,0,budget).getAsJsonObject();
        HubProtocol.object(dto.toString(),HubFragmentBuffer.MAX_BYTES);
        if(!hash(dto).equals(delta.get("checksum").getAsString()))throw new IllegalArgumentException("更新日志缓存重建校验失败");return dto;
    }
    private static JsonElement expand(JsonElement value,Map<String,JsonElement> units,int depth,Budget budget){
        if(depth>32||++budget.nodes>40000)throw new IllegalArgumentException("更新日志缓存结构过大");
        if(value.isJsonObject()){
            JsonObject source=value.getAsJsonObject();if(source.has("_hubRef")){if(source.size()!=1)throw new IllegalArgumentException("缓存引用无效");return unit(source.get("_hubRef").getAsString(),units,budget);}
            JsonObject out=source.has("_hubMerge")?unit(source.get("_hubMerge").getAsString(),units,budget).getAsJsonObject():new JsonObject();
            for(var item:source.entrySet())if(!item.getKey().equals("_hubMerge"))out.add(item.getKey(),expand(item.getValue(),units,depth+1,budget));return out;
        }
        if(value.isJsonArray()){JsonArray out=new JsonArray();for(JsonElement item:value.getAsJsonArray())out.add(expand(item,units,depth+1,budget));return out;}return value.deepCopy();
    }
    private static final class Budget {long bytes;int nodes;final Map<String,Integer> sizes=new HashMap<>(),counts=new HashMap<>();}
    private static JsonElement unit(String hash,Map<String,JsonElement> units,Budget budget){
        JsonElement value=units.get(hash);if(value==null)throw new IllegalArgumentException("本机缺少更新日志缓存单元");
        int bytes=budget.sizes.computeIfAbsent(hash,key->value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        int nodes=budget.counts.computeIfAbsent(hash,key->countNodes(value,0));
        budget.bytes+=bytes;budget.nodes+=nodes;
        if(budget.bytes>HubFragmentBuffer.MAX_BYTES||budget.nodes>40000)throw new IllegalArgumentException("更新日志缓存展开过大");
        return value.deepCopy();
    }
    private static int countNodes(JsonElement value,int depth){if(depth>32)throw new IllegalArgumentException("更新日志缓存层级过深");int count=1;if(value.isJsonObject())for(var item:value.getAsJsonObject().entrySet()){count+=countNodes(item.getValue(),depth+1);if(count>40000)throw new IllegalArgumentException("更新日志缓存结构过大");}else if(value.isJsonArray())for(JsonElement item:value.getAsJsonArray()){count+=countNodes(item,depth+1);if(count>40000)throw new IllegalArgumentException("更新日志缓存结构过大");}return count;}
}
