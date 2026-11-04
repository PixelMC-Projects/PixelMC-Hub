/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client;

import com.google.gson.*;
import com.pixelmc.hub.client.ui.HubView;
import com.pixelmc.hub.client.ui.PlayerDataChecks;
import com.pixelmc.hub.network.HubNetwork;
import java.lang.reflect.*;
import java.util.*;

/** Actual client controller with synthetic clock/transport; no Minecraft/client/server launch. */
public final class AcceptanceRefreshChecks {
    private static final List<HubNetwork.Request> sent=new ArrayList<>();
    private static long now;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void tick(long millis){now+=millis*1_000_000L;ClientHubSession.tick();}
    private static void bind(ClientHubSession session)throws Exception{Field current=ClientHubSession.class.getDeclaredField("current");current.setAccessible(true);current.set(null,session);}
    private static void available(ClientHubSession session,String module)throws Exception{Field f=ClientHubSession.class.getDeclaredField("available");f.setAccessible(true);((Set<String>)f.get(session)).add(module);}
    private static HubNetwork.Request last(){return sent.get(sent.size()-1);}
    private static void reply(HubNetwork.Request request,JsonObject value){ClientHubSession.receive(new HubNetwork.Response(request.session(),request.id(),request.module(),request.action(),true,value.toString()));}
    private static JsonObject empty(){JsonObject value=PlayerDataChecks.titles();value.add("titles",new JsonArray());value.addProperty("equippedTitle","");return value;}
    private static Object call(HubView view,String method,Class<?>[] parameters,Object... args)throws Exception{Method f=HubView.class.getDeclaredMethod(method,parameters);f.setAccessible(true);return f.invoke(view,args);}
    private static void samePages(HubView view,int count)throws Exception{
        check((int)call(view,"titleCount",new Class<?>[0])==count,"Home count is stale");
        check(((List<?>)call(view,"titleList",new Class<?>[0])).size()==count,"Titles list is stale");
    }
    public static void main(String[] args)throws Exception {
        ClientHubSession.close();ClientHubSession.titlesChanged();ClientHubSession.fortunesChanged();ClientHubSession.tick();check(sent.isEmpty(),"Closed panel emitted IO");
        HubView view=new HubView();ClientHubSession session=new ClientHubSession(view,sent::add,()->now);bind(session);available(session,"titles");
        session.action("titles","snapshot",new JsonObject());HubNetwork.Request initial=last();
        ClientHubSession.receive(new HubNetwork.Response(initial.session(),initial.id(),"titles","snapshot",false,"玩家资料正在准备，请稍后重试"));
        tick(120_000);check(sent.size()==1,"Idle or initial failure started automatic scans/retries");
        // Explicit retry after transient readiness failure succeeds without a reconnect.
        session.action("titles","snapshot",new JsonObject());reply(last(),empty());samePages(view,0);int before=sent.size();
        for(int i=0;i<1000;i++)tick(1000);check(sent.size()==before,"Long idle triggered a periodic request");
        // One reset notification for a multi-page upstream display sync; repeated changes coalesce.
        for(int i=0;i<30;i++)ClientHubSession.titlesChanged();check(sent.size()==before,"Notification performed immediate IO");
        tick(200);check(sent.size()==before+1&&last().action().equals("snapshot"),"Burst was not one merged refresh");
        JsonObject owned=PlayerDataChecks.titles();JsonArray one=new JsonArray();one.add(owned.getAsJsonArray("titles").get(0));owned.add("titles",one);reply(last(),owned);samePages(view,1);
        tick(120_000);before=sent.size();check(before==3,"Confirmed grant started a timer scan");
        for(int i=0;i<30;i++)call(view,"navigate",new Class<?>[]{int.class},i%2);tick(200);check(sent.size()==before+1,"Navigation refresh was lost or unbounded");reply(last(),owned);samePages(view,1);
        // A read from before a local write cannot overwrite the committed new state.
        tick(1000);session.action("titles","snapshot",new JsonObject());HubNetwork.Request stale=last();
        JsonObject mutation=new JsonObject();mutation.addProperty("visible",false);mutation.addProperty("expectedRevision","t1");session.action("titles","visibility",mutation);tick(600);HubNetwork.Request write=last();check(write.action().equals("visibility"),"Write not dispatched");
        for(int i=0;i<20;i++)ClientHubSession.titlesChanged();before=sent.size();tick(1000);check(sent.size()==before,"Notification overwrote an in-flight write");
        reply(stale,empty());samePages(view,1);reply(write,owned);samePages(view,1);tick(200);check(sent.size()==before+1,"Change during write was lost");reply(last(),owned);
        // Changes during catalogue paging wait for that transaction, then issue one fresh snapshot.
        tick(1000);session.action("titles","snapshot",new JsonObject());JsonObject page1=owned.deepCopy();page1.addProperty("nextCursor","page2");reply(last(),page1);tick(600);HubNetwork.Request page2=last();check(page2.action().equals("catalogue"),"Expected catalogue continuation");
        for(int i=0;i<20;i++)ClientHubSession.titlesChanged();before=sent.size();tick(1000);check(sent.size()==before,"Change interrupted catalogue paging");reply(page2,owned);tick(200);check(sent.size()==before+1&&last().action().equals("snapshot"),"Change during paging was lost");reply(last(),empty());samePages(view,0);
        // No change hints are forwarded into a new/closed session.
        ClientHubSession.close();before=sent.size();ClientHubSession.titlesChanged();ClientHubSession.fortunesChanged();tick(120_000);check(sent.size()==before,"Closed panel processed a hint");reply(last(),owned);samePages(view,0);
        // A server-day deadline expires once, even if an early reply still carries the old day.
        now=0;HubView calendar=new HubView();ClientHubSession fortuneSession=new ClientHubSession(calendar,sent::add,()->now);bind(fortuneSession);available(fortuneSession,"fortunes");
        calendar.moduleStatus("fortunes","");calendar.acceptLive("fortunes","snapshot",PlayerDataChecks.fortunes());
        Field epoch=ClientHubSession.class.getDeclaredField("serverClockMillis");epoch.setAccessible(true);epoch.setLong(fortuneSession,java.time.Instant.parse("2026-11-04T15:59:59Z").toEpochMilli());
        Method midnight=ClientHubSession.class.getDeclaredMethod("scheduleMidnight");midnight.setAccessible(true);midnight.invoke(fortuneSession);before=sent.size();
        tick(500);check(sent.size()==before,"Midnight fired early");tick(700);tick(200);check(sent.size()==before+1&&last().module().equals("fortunes"),"Next midnight did not invalidate once");
        reply(last(),PlayerDataChecks.fortunes());tick(3_600_000);check(sent.size()==before+1,"Old-day reply rescheduled repeated midnight scans");
        for(int i=0;i<20;i++)ClientHubSession.fortunesChanged();tick(200);check(sent.size()==before+2,"Fortune confirmed-result hints were not merged");reply(last(),PlayerDataChecks.fortunes());
        ClientHubSession.close();
        HubView discovering=new HubView();ClientHubSession recovery=new ClientHubSession(discovering,sent::add,()->now);bind(recovery);
        Field bridge=ClientHubSession.class.getDeclaredField("bridgeAvailable");bridge.setAccessible(true);bridge.setBoolean(recovery,true);
        recovery.action("titles","snapshot",new JsonObject());HubNetwork.Request discovery=last();check(discovery.module().equals("hub")&&discovery.action().equals("discover"),"Explicit retry did not rediscover unknown modules");
        ClientHubSession.receive(new HubNetwork.Response(discovery.session(),discovery.id(),"hub","discover",false,"请求超时"));before=sent.size();tick(120_000);check(sent.size()==before,"Discovery error started automatic polling");
        call(discovering,"navigate",new Class<?>[]{int.class},1);HubNetwork.Request repeated=last();check(repeated.action().equals("discover")&&repeated.id()!=discovery.id(),"Navigation could not retry failed discovery");
        JsonObject modules=new JsonObject();for(String name:List.of("titles","manager","fortunes")){JsonObject item=new JsonObject();item.addProperty("available",name.equals("titles"));modules.add(name,item);}JsonObject discovered=new JsonObject();discovered.add("modules",modules);reply(repeated,discovered);tick(600);check(last().module().equals("titles")&&last().action().equals("snapshot"),"Rediscovery did not read recovered provider");reply(last(),empty());samePages(discovering,0);ClientHubSession.close();
        System.out.println("PASS: no idle scanning/retries, explicit readiness retry, coalesced change/navigation refresh, shared home/title data, safe writes/paging, stale replies and closed hooks");
    }
}
