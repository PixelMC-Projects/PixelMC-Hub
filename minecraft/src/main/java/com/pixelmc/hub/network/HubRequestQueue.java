/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.network;

import com.google.gson.JsonObject;
import java.util.*;

/** Bounded client scheduling and freshness, independent of Minecraft for protocol checks. */
public final class HubRequestQueue {
    public record Ticket(long id,String module,String action,JsonObject payload,long epoch,long at,boolean write) {}
    private final Map<Long,Ticket> active=new LinkedHashMap<>();
    private final Deque<Ticket> queued=new ArrayDeque<>();
    private final Map<String,Long> epochs=new HashMap<>(),latest=new HashMap<>(),writes=new HashMap<>();
    private long nextId;
    public boolean writing(String module){return writes.containsKey(module);}
    public boolean reading(String module){return java.util.stream.Stream.concat(active.values().stream(),queued.stream()).anyMatch(t->!t.write&&t.module.equals(module)&&current(t));}
    public void invalidateReads(String module){
        if(writing(module))throw new IllegalStateException("正在等待服务器确认");
        epochs.merge(module,1L,Long::sum);queued.removeIf(t->!t.write&&t.module.equals(module));
    }
    public void offer(String module,String action,JsonObject payload,long now) {
        boolean write=Set.of("equip","visibility","displayPreferences","feature").contains(action);
        if(write&&writing(module))throw new IllegalStateException("正在等待服务器确认");
        String key=module+":"+action;
        if(!write&&java.util.stream.Stream.concat(active.values().stream(),queued.stream()).anyMatch(t->current(t)&&t.module.equals(module)&&t.action.equals(action)&&t.payload.equals(payload)))return;
        queued.removeIf(t->!t.write&&t.module.equals(module)&&t.action.equals(action));
        if(queued.size()>=16)throw new IllegalStateException("请求较多，请稍后重试");
        if(write){epochs.merge(module,1L,Long::sum);queued.removeIf(t->t.module.equals(module)&&!t.write);}
        Ticket ticket=new Ticket(++nextId,module,action,payload.deepCopy(),epochs.getOrDefault(module,0L),now,write);
        latest.put(key,ticket.id);if(write){writes.put(module,ticket.id);queued.addFirst(ticket);}else queued.addLast(ticket);
    }
    public List<Ticket> ready(long now) {
        return ready(now,4);
    }
    public List<Ticket> ready(long now,int maximum) {
        List<Ticket> result=new ArrayList<>();
        while(active.size()<4&&result.size()<maximum&&!queued.isEmpty()) {
            Ticket ticket=queued.removeFirst();if(!current(ticket))continue;
            Ticket sent=new Ticket(ticket.id,ticket.module,ticket.action,ticket.payload,ticket.epoch,now,ticket.write);
            active.put(sent.id,sent);result.add(sent);
        }
        return result;
    }
    private boolean current(Ticket t){return t.epoch==epochs.getOrDefault(t.module,0L)&&latest.getOrDefault(t.module+":"+t.action,0L)==t.id;}
    public Ticket complete(long id,String module,String action) {
        Ticket ticket=active.get(id);if(ticket==null)return null;
        if(!ticket.module.equals(module)||!ticket.action.equals(action))return null;
        active.remove(id);if(!current(ticket))return null;
        if(ticket.write)writes.remove(module,ticket.id);return ticket;
    }
    public List<Ticket> expire(long now) {
        List<Ticket> result=new ArrayList<>();
        active.values().removeIf(t->{if(now-t.at<10_000_000_000L)return false;if(current(t)){if(t.write)writes.remove(t.module,t.id);result.add(t);}return true;});
        return result;
    }
}
