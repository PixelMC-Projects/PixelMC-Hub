/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client;

import com.google.gson.*;
import com.pixelmc.hub.client.ui.*;
import com.pixelmc.hub.network.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Synthetic connection + disk + delta checks, without starting Minecraft or touching player data. */
public final class SessionCacheChecks {
    static final List<HubNetwork.Request> sent=new ArrayList<>();static long now;
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void set(Object object,String field,Object value)throws Exception{Field f=ClientHubSession.class.getDeclaredField(field);f.setAccessible(true);f.set(object,value);}
    static void bind(ClientHubSession session)throws Exception{set(null,"current",session);}
    static void available(ClientHubSession session,String module)throws Exception{Field f=ClientHubSession.class.getDeclaredField("available");f.setAccessible(true);((Set<String>)f.get(session)).add(module);}
    static void tick(){now+=1_000_000_000L;ClientHubSession.tick();}
    static HubNetwork.Request last(){return sent.get(sent.size()-1);}
    static void reply(HubNetwork.Request r,JsonObject value){ClientHubSession.receive(new HubNetwork.Response(r.session(),r.id(),r.module(),r.action(),true,value.toString()));}
    static int count(HubView view)throws Exception{Method m=HubView.class.getDeclaredMethod("titleCount");m.setAccessible(true);return (int)m.invoke(view);}
    static void reject(Runnable action){try{action.run();throw new AssertionError("Invalid cache accepted");}catch(IllegalArgumentException expected){}}
    static JsonObject title(String id,boolean owned){JsonObject root=PlayerDataChecks.titles();JsonArray rows=new JsonArray();JsonObject one=root.getAsJsonArray("titles").get(0).getAsJsonObject().deepCopy();one.addProperty("id",id);one.addProperty("owned",owned);rows.add(one);root.add("titles",rows);root.addProperty("equippedTitle",id);return root;}
    public static void main(String[] args)throws Exception {
        HubSyncCache server=new HubSyncCache(),client=new HubSyncCache();JsonObject initial=title("kept-title",true);
        check(client.decode(server.encode("titles/snapshot",initial)).equals(initial),"Initial baseline lost");
        JsonObject changed=initial.deepCopy();changed.getAsJsonArray("titles").get(0).getAsJsonObject().addProperty("owned",false);changed.addProperty("revision","t2");
        JsonObject patch=server.encode("titles/snapshot",changed);
        check(!patch.has("full")&&!patch.toString().contains("kept-title")&&client.decode(patch).equals(changed),"Unchanged title fields were resent");
        check(server.encode("titles/snapshot",changed).getAsJsonObject("patch").size()==0,"No-op update contained data");
        // A separate complete sequence for removals/nulls/array length and bounded eviction.
        server.clear();client.clear();client.decode(server.encode("titles/snapshot",changed));
        JsonObject empty=changed.deepCopy();empty.add("titles",new JsonArray());empty.remove("equippedTitle");empty.add("globalDisplay",JsonNull.INSTANCE);
        check(client.decode(server.encode("titles/snapshot",empty)).equals(empty),"Removal/null/array delta incorrect");
        for(int i=0;i<150;i++)check(client.decode(server.encode("titles/page/"+i,initial)).equals(initial),"Eviction diverged");
        check(server.size()<=128&&client.size()<=128,"Connection cache unbounded");
        check(client.decode(server.encode("titles/snapshot",empty)).equals(empty),"Evicted stream did not receive a full baseline");
        server.clear();client.clear();check(server.size()==0&&client.size()==0,"Logout left transport baselines");

        JsonObject month=PlayerDataChecks.calendar(),known=HubCalendarDelta.known(month);
        JsonObject equal=HubCalendarDelta.encode(month,known);
        check(equal.getAsJsonArray("updated").isEmpty()&&equal.get("unchanged").getAsInt()==1,"Cached month resent records");
        check(HubCalendarDelta.apply(month,equal).equals(month),"Unchanged month not reconstructed");
        JsonObject noCache=HubCalendarDelta.encode(month,new JsonObject());
        check(noCache.getAsJsonArray("updated").size()==1&&HubCalendarDelta.apply(null,noCache).equals(month),"New computer did not receive complete month");
        JsonObject next=month.deepCopy();JsonObject row=next.getAsJsonArray("records").get(0).getAsJsonObject();row.addProperty("date","2026-11-05");row.getAsJsonObject("fortune").addProperty("title","更新签文");
        JsonObject update=HubCalendarDelta.encode(next,known);
        check(update.getAsJsonArray("updated").size()==1&&update.getAsJsonArray("removed").size()==1&&HubCalendarDelta.apply(month,update).equals(next),"Archive changes/deletions not applied");
        reject(()->HubCalendarDelta.parseKnown("2026-11","{\"2026-10-01\":\""+"a".repeat(64)+"\"}"));
        reject(()->HubProtocol.payload("fortunes","calendarSync","{\"month\":\"2026-11\",\"known\":\"{}\",\"uuid\":\"other\"}"));

        Path root=Files.createTempDirectory("pixelmc-hub-cache-check-");
        HubCalendarCache disk=new HubCalendarCache(root);String scope=HubCalendarCache.identity(PlayerDataChecks.manager());
        check(scope.length()==64,"Stable scope absent");disk.save(scope,month);check(disk.load(scope,"2026-11").equals(month),"Persistent month not read back");
        JsonObject other=PlayerDataChecks.manager();other.getAsJsonObject("scope").addProperty("archiveId","new-save");String otherScope=HubCalendarCache.identity(other);
        check(!scope.equals(otherScope)&&disk.load(otherScope,"2026-11")==null,"History leaked between worlds");
        Path file=root.resolve(scope).resolve("2026-11.json");Files.writeString(file,"{broken");check(disk.load(scope,"2026-11")==null,"Damaged cache reported as complete");disk.save(scope,month);

        ClientHubSession.close();sent.clear();HubView view=new HubView();ClientHubSession session=new ClientHubSession(view,sent::add,()->now,disk,Runnable::run,Runnable::run,ignored->{});
        bind(session);available(session,"titles");available(session,"fortunes");set(session,"incremental",true);set(session,"cacheScope",scope);
        // Initial data can populate before any screen is opened.
        session.action("titles","snapshot",new JsonObject());reply(last(),server.encode("titles/snapshot",initial));check(count(view)==1,"Login preload did not populate view");
        int sends=sent.size();ClientHubSession.open(view);ClientHubSession.panelClosed();view.open(false);check(count(view)==1&&sent.size()==sends,"Reopening cleared or refetched confirmed data");
        ClientHubSession.titlesChanged();tick();reply(last(),server.encode("titles/snapshot",changed));check(count(view)==0,"Changes while panel hidden were not merged");
        sends=sent.size();for(int i=0;i<1000;i++)tick();check(sent.size()==sends,"Idle session began polling");
        JsonObject request=new JsonObject();request.addProperty("month","2026-11");session.action("fortunes","calendar",request);tick();HubNetwork.Request sync=last();
        JsonObject body=HubProtocol.object(sync.payload(),8192);check(sync.action().equals("calendarSync")&&HubProtocol.object(body.get("known").getAsString(),4096).size()==1,"Loaded disk records were not negotiated");
        reply(sync,update);check(disk.load(scope,"2026-11").equals(next),"Confirmed archive delta not persisted");
        ClientHubSession.close();sends=sent.size();ClientHubSession.titlesChanged();tick();reply(sync,noCache);check(sent.size()==sends,"Logout accepted an old callback");
        check(disk.load(scope,"2026-11").equals(next),"Logout deleted persistent history");
        // Missing file: another machine must send an empty known map, not an account-level remembered flag.
        Files.delete(file);HubView freshView=new HubView();ClientHubSession fresh=new ClientHubSession(freshView,sent::add,()->now,disk,Runnable::run,Runnable::run,ignored->{});
        bind(fresh);available(fresh,"fortunes");set(fresh,"incremental",true);set(fresh,"cacheScope",scope);fresh.action("fortunes","calendar",request);tick();
        check(HubProtocol.object(HubProtocol.object(last().payload(),8192).get("known").getAsString(),4096).size()==0,"Missing cache suppressed download");reply(last(),noCache);check(disk.load(scope,"2026-11").equals(month),"Fresh machine month not restored");
        ClientHubSession.close();
        List<Runnable> queued=new ArrayList<>();ClientHubSession stale=new ClientHubSession(new HubView(),sent::add,()->now,disk,queued::add,Runnable::run,ignored->{});
        bind(stale);available(stale,"fortunes");set(stale,"incremental",true);set(stale,"cacheScope",scope);stale.action("fortunes","calendar",request);ClientHubSession.close();sends=sent.size();queued.forEach(Runnable::run);check(sent.size()==sends,"Late disk read crossed logout");
        // Only remove the exact temporary fixture tree created above.
        try(var files=Files.walk(root)){for(Path path:files.sorted(Comparator.reverseOrder()).toList()){check(path.toAbsolutePath().normalize().startsWith(root.toAbsolutePath().normalize()),"Cleanup escaped fixture");Files.delete(path);}}
        System.out.println("PASS: login warm cache, hidden-panel events, no idle polling, per-field wire deltas, logout isolation, scoped durable history, missing/corrupt cache full recovery, month record changes/deletions, late disk callbacks.");
    }
}
