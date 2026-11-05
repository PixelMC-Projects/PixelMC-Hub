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
        "fortunes", "cn.pixelmc.dailyfortunes.api.PlayerHubApi");
    private static final Map<String, Set<String>> ACTIONS = Map.of(
        "hub", Set.of("discover", "subscribe"), "manager", Set.of("snapshot"),
        "titles", Set.of("snapshot", "catalogue", "equip", "visibility", "displayPreferences"),
        "fortunes", Set.of("snapshot", "calendar", "calendarSync", "read", "feature"));
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
        JsonObject payload=object(json,REQUEST_BYTES);
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
}
