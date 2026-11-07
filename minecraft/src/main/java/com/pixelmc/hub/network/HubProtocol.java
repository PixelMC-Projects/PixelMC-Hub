/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;
import java.util.Set;

/** The complete player action allowlist. No administrative operation is routable. */
public final class HubProtocol {
    public static final int REQUEST_BYTES = 8192, RESPONSE_BYTES = 524288;
    public static final Map<String, String> PROVIDERS = Map.of(
        "manager", "com.pixelmc.pixelmcmanager.api.PlayerHubApi",
        "titles", "cn.pixelmc.memorialtitles.api.PlayerHubApi",
        "fortunes", "cn.pixelmc.dailyfortunes.api.PlayerHubApi",
        "updates", "com.pixelmc.pixelmcmanager.api.PlayerHubUpdatesApi",
        "rankings", "com.pixelmc.pixelmcmanager.api.PlayerHubRankingsApi",
        "community", "com.pixelmc.pixelmcmanager.api.PlayerHubCommunityApi");
    private static final Map<String, Set<String>> ACTIONS = Map.of(
        "rankings", Set.of("snapshot","page","aroundMe","window"),
        "community", HubCommunityProtocol.ACTIONS,
        "hub", Set.of("discover", "subscribe"), "manager", Set.of("snapshot"),
        "titles", Set.of("snapshot", "catalogue", "equip", "visibility", "displayPreferences"),
        "fortunes", Set.of("snapshot", "calendar", "calendarSync", "read", "feature"),
        "updates", Set.of("snapshot", "packs", "list", "read", "like", "comment", "deleteComment", "snapshotSync", "packsSync", "listSync", "readSync", "commentSync", "deleteCommentSync"));
    public static int maximumResponse(String module){return module.equals("updates")?HubFragmentBuffer.MAX_BYTES:RESPONSE_BYTES;}
    public static boolean updatesSync(String module,String action){return module.equals("updates")&&Set.of("snapshotSync","packsSync","listSync","readSync","commentSync","deleteCommentSync").contains(action);}
    public static String updatesPlain(String action){return action.endsWith("Sync")?action.substring(0,action.length()-4):action;}
    public static int maximumRequest(String module,String action){return updatesSync(module,action)?65536:REQUEST_BYTES;}
    private HubProtocol() {}
    public static boolean allowed(String module, String action) {
        return ACTIONS.getOrDefault(module, Set.of()).contains(action);
    }
    public static JsonObject object(String json, int maximumBytes) {
        if (json == null || json.length() > maximumBytes || json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > maximumBytes)
            throw new IllegalArgumentException("响应过大，请缩小查询范围");
        int depth=0;boolean quoted=false,escaped=false;
        for(int i=0;i<json.length();i++) {
            char c=json.charAt(i);
            if(quoted){if(escaped)escaped=false;else if(c=='\\')escaped=true;else if(c=='"')quoted=false;continue;}
            if(c=='"')quoted=true;else if(c=='{'||c=='['){if(++depth>32)throw new IllegalArgumentException("数据层级过深");}
            else if(c=='}'||c==']')depth--;
        }
        var value = JsonParser.parseString(json);
        if (!value.isJsonObject()) throw new IllegalArgumentException("数据格式不正确");
        return value.getAsJsonObject();
    }
    public static JsonObject payload(String module,String action,String json) {
        if(!allowed(module,action))throw new IllegalArgumentException("不支持的玩家操作");
        JsonObject payload=object(json,maximumRequest(module,action));
        if(module.equals("community")){HubCommunityProtocol.validate(action,payload);return payload;}
        if(module.equals("rankings")){validateRankings(action,payload);return payload;}
        if(module.equals("updates")){if(updatesSync(module,action)){if(!payload.has("known")||!payload.get("known").isJsonPrimitive()||!payload.getAsJsonPrimitive("known").isString())throw new IllegalArgumentException("缓存摘要缺失");HubUpdatesDelta.parseKnown(payload.get("known").getAsString());JsonObject plain=payload.deepCopy();plain.remove("known");validateUpdates(updatesPlain(action),plain);}else validateUpdates(action,payload);return payload;}
        Set<String> fields=switch(action){
            case "snapshot","discover","subscribe" -> Set.of();
            case "catalogue" -> Set.of("cursor","limit");
            case "equip" -> Set.of("title","expectedRevision");
            case "visibility" -> Set.of("visible","expectedRevision");
            case "displayPreferences" -> Set.of("head","chat","tab","expectedRevision");
            case "calendar" -> Set.of("month");
            case "calendarSync" -> Set.of("month","known");
            case "read" -> Set.of("date");
            case "feature" -> Set.of("date","expectedRevision");
            default -> Set.of();
        };
        for(var entry:payload.entrySet())if(!fields.contains(entry.getKey())||!entry.getValue().isJsonPrimitive())throw new IllegalArgumentException("玩家操作包含不支持的字段");
        if(action.equals("calendarSync")) {
            if(!payload.keySet().equals(Set.of("month","known")))throw new IllegalArgumentException("签历同步参数不完整");
            HubCalendarDelta.parseKnown(payload.get("month").getAsString(),payload.get("known").getAsString());
        }
        return payload;
    }
    private static void validateRankings(String action,JsonObject payload) {
        Set<String> fields=switch(action){case "snapshot"->Set.of();case "page"->Set.of("board","page","revision");case "aroundMe"->Set.of("board","revision");case "window"->Set.of("board","page","count");default->throw new IllegalArgumentException("排行操作无效");};
        if(!fields.containsAll(payload.keySet()) || payload.entrySet().stream().anyMatch(e->!e.getValue().isJsonPrimitive()))throw new IllegalArgumentException("排行参数无效");
        if(!action.equals("snapshot")) {
            string(payload,"board",16,false);
            if(!Set.of("food","fortunes","online").contains(payload.get("board").getAsString()))throw new IllegalArgumentException("排行类别无效");
            if(payload.has("revision"))string(payload,"revision",160,true);
        }
        if(action.equals("page")||action.equals("window"))integer(payload,"page",0,100000);
        if(action.equals("window"))integer(payload,"count",1,5);
    }
    private static void validateUpdates(String action,JsonObject p) {
        Set<String> fields=switch(action){case "snapshot"->Set.of();case "packs"->Set.of("page");case "list"->Set.of("packId","page","search");case "read"->Set.of("id","commentsPage","replyPages");case "like"->Set.of("id","liked");case "comment"->Set.of("id","text","operationId");case "deleteComment"->Set.of("id","commentId");default->throw new IllegalArgumentException("不支持的更新日志操作");};
        for(var item:p.entrySet())if(!fields.contains(item.getKey()))throw new IllegalArgumentException("更新日志操作包含不支持的字段");
        switch(action){
            case "snapshot" -> {}
            case "packs" -> integer(p,"page",0,100000);
            case "list" -> {id(p,"packId");integer(p,"page",0,100000);string(p,"search",120,true);}
            case "read" -> {id(p,"id");integer(p,"commentsPage",0,100000);if(p.has("replyPages")){if(!p.get("replyPages").isJsonObject()||p.getAsJsonObject("replyPages").size()>12)throw new IllegalArgumentException("回复分页无效");for(var item:p.getAsJsonObject("replyPages").entrySet()){uuid(item.getKey());integerValue(item.getValue(),0,100000);}}}
            case "like" -> {id(p,"id");if(!p.has("liked")||!p.get("liked").isJsonPrimitive()||!p.getAsJsonPrimitive("liked").isBoolean())throw new IllegalArgumentException("点赞目标无效");}
            case "comment" -> {id(p,"id");string(p,"text",2000,false);id(p,"operationId");}
            case "deleteComment" -> {id(p,"id");id(p,"commentId");}
        }
    }
    private static void id(JsonObject p,String key){string(p,key,36,false);uuid(p.get(key).getAsString());}
    private static void uuid(String value){try{if(!java.util.UUID.fromString(value).toString().equalsIgnoreCase(value))throw new IllegalArgumentException();}catch(RuntimeException bad){throw new IllegalArgumentException("记录标识无效");}}
    private static void string(JsonObject p,String key,int max,boolean empty){if(!p.has(key)||!p.get(key).isJsonPrimitive()||!p.getAsJsonPrimitive(key).isString())throw new IllegalArgumentException("更新日志参数无效");String value=p.get(key).getAsString();if(value.length()>max||!empty&&value.isBlank()||value.indexOf(0)>=0)throw new IllegalArgumentException("更新日志参数超出范围");}
    private static void integer(JsonObject p,String key,int min,int max){if(!p.has(key))throw new IllegalArgumentException("分页参数缺失");integerValue(p.get(key),min,max);}
    private static void integerValue(com.google.gson.JsonElement value,int min,int max){try{if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException();java.math.BigDecimal number=value.getAsBigDecimal();int i=number.intValueExact();if(i<min||i>max)throw new IllegalArgumentException();}catch(RuntimeException bad){throw new IllegalArgumentException("分页参数无效");}}
}
