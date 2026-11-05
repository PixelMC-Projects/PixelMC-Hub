/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.network;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/** Content negotiation for one owned archived month; a client's hashes never grant ownership. */
public final class HubCalendarDelta {
    private HubCalendarDelta() {}
    public static String hash(JsonElement value) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical(value).toString().getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private static JsonElement canonical(JsonElement value) {
        if(value.isJsonObject()){JsonObject out=new JsonObject();new TreeSet<>(value.getAsJsonObject().keySet()).forEach(k->out.add(k,canonical(value.getAsJsonObject().get(k))));return out;}
        if(value.isJsonArray()){JsonArray out=new JsonArray();value.getAsJsonArray().forEach(v->out.add(canonical(v)));return out;}
        return value.deepCopy();
    }
    public static String month(String text) {
        if(text==null||!text.matches("[0-9]{4}-[0-9]{2}"))throw new IllegalArgumentException("月份无效");
        YearMonth value=YearMonth.parse(text);if(value.getYear()<1970||value.getYear()>2200)throw new IllegalArgumentException("月份超出范围");return text;
    }
    public static JsonObject rows(JsonObject calendar) {
        String month=month(calendar.get("month").getAsString());JsonObject rows=new JsonObject();
        if(!calendar.has("records")||!calendar.get("records").isJsonArray()||calendar.getAsJsonArray("records").size()>31)throw new IllegalArgumentException("签历格式无效");
        for(JsonElement value:calendar.getAsJsonArray("records")) {
            JsonObject row=value.getAsJsonObject();String date=row.get("date").getAsString();
            if(!YearMonth.from(LocalDate.parse(date)).toString().equals(month)||rows.has(date)||!row.has("fortune"))throw new IllegalArgumentException("签历日期无效");
            rows.add(date,row.deepCopy());
        }
        return rows;
    }
    public static JsonObject known(JsonObject calendar) {JsonObject out=new JsonObject();rows(calendar).entrySet().forEach(e->out.addProperty(e.getKey(),hash(e.getValue())));return out;}
    public static JsonObject parseKnown(String month,String text) {
        month(month);JsonObject known=HubProtocol.object(text,4096);if(known.size()>31)throw new IllegalArgumentException("签历缓存摘要过大");
        for(var entry:known.entrySet()) {
            if(!YearMonth.from(LocalDate.parse(entry.getKey())).toString().equals(month)||!entry.getValue().isJsonPrimitive()
                ||!entry.getValue().getAsJsonPrimitive().isString()||!entry.getValue().getAsString().matches("[a-f0-9]{64}"))throw new IllegalArgumentException("签历缓存摘要无效");
        }
        return known;
    }
    public static JsonObject encode(JsonObject calendar,JsonObject known) {
        JsonObject rows=rows(calendar),out=calendar.deepCopy();out.remove("records");JsonArray changed=new JsonArray(),removed=new JsonArray();
        for(var entry:rows.entrySet())if(!known.has(entry.getKey())||!hash(entry.getValue()).equals(known.get(entry.getKey()).getAsString()))changed.add(entry.getValue().deepCopy());
        for(String date:known.keySet())if(!rows.has(date))removed.add(date);
        out.add("updated",changed);out.add("removed",removed);out.addProperty("total",rows.size());out.addProperty("unchanged",rows.size()-changed.size());out.addProperty("recordsHash",hash(rows));return out;
    }
    public static JsonObject apply(JsonObject cached,JsonObject delta) {
        String month=month(delta.get("month").getAsString());JsonObject rows=cached==null?new JsonObject():rows(cached);
        if(cached!=null&&!month.equals(cached.get("month").getAsString()))throw new IllegalArgumentException("签历缓存月份不匹配");
        for(JsonElement date:delta.getAsJsonArray("removed"))rows.remove(date.getAsString());
        for(JsonElement row:delta.getAsJsonArray("updated"))rows.add(row.getAsJsonObject().get("date").getAsString(),row.deepCopy());
        if(rows.size()!=delta.get("total").getAsInt()||!hash(rows).equals(delta.get("recordsHash").getAsString()))throw new IllegalArgumentException("签历缓存校验失败，请重新读取");
        JsonObject out=delta.deepCopy();for(String key:List.of("updated","removed","total","unchanged","recordsHash"))out.remove(key);
        JsonArray records=new JsonArray();new TreeSet<>(rows.keySet()).forEach(date->records.add(rows.get(date)));out.add("records",records);rows(out);return out;
    }
}
