/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.network;

import com.google.gson.*;
import java.util.*;

/** Ordered connection-local transport baselines. Never persisted or used as authority. */
public final class HubSyncCache {
    private record Entry(long sequence,JsonObject value,int weight) {
        Entry(long sequence,JsonObject value){this(sequence,value,value.toString().length()*2);}
    }
    private final Map<String,Entry> entries=new LinkedHashMap<>();
    private long sequence;
    private int weight;
    public void clear(){entries.clear();sequence=0;weight=0;}
    public int size(){return entries.size();}
    public JsonObject encode(String stream,JsonObject value) {
        Entry previous=entries.get(stream);JsonObject packet=new JsonObject();long next=++sequence;
        packet.addProperty("_hubSync",1);packet.addProperty("stream",stream);packet.addProperty("sequence",next);
        packet.addProperty("base",previous==null?0:previous.sequence());
        if(previous==null)packet.add("full",value.deepCopy());else packet.add("patch",difference(previous.value(),value));
        // Validate before committing a baseline that the receiver might never see.
        HubProtocol.object(packet.toString(),HubProtocol.RESPONSE_BYTES);
        remember(stream,new Entry(next,value.deepCopy()));return packet;
    }
    public JsonObject decode(JsonObject packet) {
        if(!packet.has("_hubSync"))return packet;
        String stream=packet.get("stream").getAsString();long next=packet.get("sequence").getAsLong();
        if(stream.length()>128||next<=sequence)throw new IllegalArgumentException("同步顺序无效");
        Entry previous=entries.get(stream);JsonObject value;
        if(packet.has("full")){if(packet.get("base").getAsLong()!=0)throw new IllegalArgumentException("同步基线无效");value=packet.getAsJsonObject("full").deepCopy();}
        else {
            if(previous==null||previous.sequence()!=packet.get("base").getAsLong())throw new IllegalArgumentException("同步缓存已失效");
            value=apply(previous.value(),packet.getAsJsonObject("patch")).getAsJsonObject();
        }
        HubProtocol.object(value.toString(),HubProtocol.RESPONSE_BYTES);remember(stream,new Entry(next,value.deepCopy()));sequence=next;return value;
    }
    private void remember(String stream,Entry value) {
        Entry previous=entries.remove(stream);if(previous!=null)weight-=previous.weight();entries.put(stream,value);weight+=value.weight();
        // Both ends consume every envelope in wire order, including UI-stale replies.
        // Identical bounds therefore evict identical baselines; next access is full.
        while(entries.size()>128||weight>8*1024*1024){var first=entries.entrySet().iterator();var old=first.next();weight-=old.getValue().weight();first.remove();}
    }
    private static JsonObject difference(JsonElement before,JsonElement after) {
        JsonObject out=new JsonObject();if(before.equals(after))return out;
        if(before.isJsonObject()&&after.isJsonObject()) {
            JsonObject fields=new JsonObject();JsonArray removed=new JsonArray();
            for(var entry:after.getAsJsonObject().entrySet()) {
                JsonElement old=before.getAsJsonObject().get(entry.getKey());JsonObject change=old==null?replacement(entry.getValue()):difference(old,entry.getValue());
                if(!change.entrySet().isEmpty())fields.add(entry.getKey(),change);
            }
            for(String key:before.getAsJsonObject().keySet())if(!after.getAsJsonObject().has(key))removed.add(key);
            out.add("fields",fields);out.add("removed",removed);
        } else if(before.isJsonArray()&&after.isJsonArray()) {
            JsonObject items=new JsonObject();var a=before.getAsJsonArray();var b=after.getAsJsonArray();
            for(int i=0;i<b.size();i++){JsonObject change=i>=a.size()?replacement(b.get(i)):difference(a.get(i),b.get(i));if(!change.entrySet().isEmpty())items.add(Integer.toString(i),change);}
            out.addProperty("length",b.size());out.add("items",items);
        } else return replacement(after);
        return out;
    }
    private static JsonObject replacement(JsonElement value){JsonObject out=new JsonObject();out.add("value",value.deepCopy());return out;}
    private static JsonElement apply(JsonElement old,JsonObject patch) {
        if(patch.has("value"))return patch.get("value").deepCopy();
        if(patch.has("fields")) {
            JsonObject out=old.getAsJsonObject().deepCopy();for(JsonElement key:patch.getAsJsonArray("removed"))out.remove(key.getAsString());
            for(var entry:patch.getAsJsonObject("fields").entrySet())out.add(entry.getKey(),apply(out.has(entry.getKey())?out.get(entry.getKey()):JsonNull.INSTANCE,entry.getValue().getAsJsonObject()));return out;
        }
        if(patch.has("items")) {
            int length=patch.get("length").getAsInt();if(length<0||length>10000)throw new IllegalArgumentException("同步数组超出范围");
            JsonArray out=new JsonArray(),previous=old.getAsJsonArray();JsonObject items=patch.getAsJsonObject("items");
            for(int i=0;i<length;i++){JsonElement value=i<previous.size()?previous.get(i):JsonNull.INSTANCE;out.add(items.has(""+i)?apply(value,items.getAsJsonObject(""+i)):value.deepCopy());}return out;
        }
        return old.deepCopy();
    }
}
