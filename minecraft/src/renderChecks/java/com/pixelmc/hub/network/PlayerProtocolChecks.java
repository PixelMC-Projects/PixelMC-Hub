/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.network;

import com.google.gson.JsonObject;
import java.util.UUID;

/** Isolated protocol/freshness checks; no Minecraft world, server, database or client launch. */
public final class PlayerProtocolChecks {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void rejects(Runnable action){try{action.run();throw new AssertionError("Expected rejected input");}catch(IllegalArgumentException expected){}}
    private static JsonObject json(String value){return HubProtocol.object(value,HubProtocol.REQUEST_BYTES);}
    public static void main(String[] args) throws Exception {
        check(HubProtocol.allowed("titles","equip"),"Allowlisted action missing");
        rejects(()->HubProtocol.payload("manager","admin","{}"));
        rejects(()->HubProtocol.payload("manager","snapshot","{\"uuid\":\"other\"}"));
        rejects(()->HubProtocol.payload("fortunes","read","{\"date\":\"2026-11-04\",\"player\":\"other\"}"));
        rejects(()->HubProtocol.object("[".repeat(33)+"]".repeat(33),8192));
        rejects(()->HubProtocol.object("{\"x\":\""+"中".repeat(3000)+"\"}",8192));
        rejects(()->HubProtocol.object("[]",8192));
        check(HubProtocol.payload("titles","equip","{\"title\":\"a\",\"expectedRevision\":\"v1\"}").has("title"),"Valid payload rejected");
        HubRequestQueue queue=new HubRequestQueue();
        for(String module:new String[]{"manager","titles","fortunes","hub"})queue.offer(module,"snapshot",json("{}"),0);
        var initial=queue.ready(0);check(initial.size()==4,"In-flight bound");
        queue.offer("titles","equip",json("{\"title\":\"a\"}"),1);check(queue.writing("titles"),"Queued write lost");
        check(queue.ready(1).isEmpty(),"Queue exceeded in-flight bound");
        check(queue.complete(initial.get(1).id(),"titles","snapshot")==null,"Pre-write read accepted");
        var write=queue.ready(2).get(0);check(write.write(),"Write was not queued");
        queue.expire(10_000_000_001L);check(queue.writing("titles"),"Old read timeout cleared newer write");
        check(queue.complete(write.id(),"titles","equip")!=null&&!queue.writing("titles"),"Write confirmation not released");
        HubRequestQueue months=new HubRequestQueue();
        months.offer("fortunes","calendar",json("{\"month\":\"2026-10\"}"),0);var october=months.ready(0).get(0);
        months.offer("fortunes","calendar",json("{\"month\":\"2026-11\"}"),1);var november=months.ready(1).get(0);
        check(months.complete(october.id(),"fortunes","calendar")==null,"Delayed month accepted");
        check(months.complete(november.id(),"fortunes","calendar")!=null,"Current month rejected");
        months.offer("fortunes","read",json("{\"date\":\"2026-11-01\"}"),2);var first=months.ready(2).get(0);
        months.offer("fortunes","read",json("{\"date\":\"2026-11-02\"}"),3);var second=months.ready(3).get(0);
        check(months.complete(first.id(),"fortunes","read")==null,"Old reading accepted");
        check(months.complete(second.id(),"titles","read")==null,"Mismatched module accepted");
        check(months.complete(second.id(),"fortunes","read")!=null,"Latest reading rejected or wrong envelope consumed it");
        check(!HubProviderBridge.discover().getAsJsonObject("modules").getAsJsonObject("manager").get("available").getAsBoolean(),"Unexpected provider on isolated classpath");
        try{HubProviderBridge.request(UUID.randomUUID(),"manager","admin","{}").join();throw new AssertionError("Admin operation forwarded");}catch(java.util.concurrent.CompletionException expected){}
        check(HubProviderBridge.safeError(new RuntimeException("jdbc:private")).equals("读取玩家资料失败，请稍后重试"),"Internal error exposed");
        System.out.println("PASS: request/action/identity/depth/UTF-8 limits, optional providers, bounded scheduling, queued writes, stale reads/months, timeout isolation");
    }
}
