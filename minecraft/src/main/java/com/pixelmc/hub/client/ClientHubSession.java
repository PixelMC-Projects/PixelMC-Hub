/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client;

import com.google.gson.JsonObject;
import com.pixelmc.hub.client.ui.HubView;
import com.pixelmc.hub.network.HubNetwork;
import com.pixelmc.hub.network.HubProtocol;
import com.pixelmc.hub.network.HubRequestQueue;
import com.pixelmc.hub.network.HubRefreshGate;
import java.util.*;
import net.minecraft.client.Minecraft;

/** All callbacks run on the client thread. Closing/logging out invalidates the entire session. */
public final class ClientHubSession {
    private static ClientHubSession current;
    private final long generation = java.util.concurrent.ThreadLocalRandom.current().nextLong();
    private final HubView view;
    private final java.util.function.Consumer<HubNetwork.Request> transport;
    private final java.util.function.LongSupplier nanoTime;
    private final HubRequestQueue queue=new HubRequestQueue();
    private final HubRefreshGate refresh=new HubRefreshGate();
    private final Set<String> available=new HashSet<>();
    private long nextSendAt,serverClockMillis,serverClockAt,midnightAt=Long.MAX_VALUE;
    private String midnightKey="";
    private boolean midnightFired;
    private boolean bridgeAvailable;
    private int cataloguePages;
    private ClientHubSession(HubView view) {this(view,request->HubNetwork.CHANNEL.sendToServer(request),System::nanoTime);}
    ClientHubSession(HubView view,java.util.function.Consumer<HubNetwork.Request> transport,java.util.function.LongSupplier nanoTime) {
        this.view=view;this.transport=transport;this.nanoTime=nanoTime;view.live(this::action);
    }
    public static void open(HubView view) {
        close();current=new ClientHubSession(view);
        var connection=Minecraft.getInstance().getConnection();
        if(connection==null||!HubNetwork.CHANNEL.isRemotePresent(connection.getConnection())) {
            for(String module:HubProtocol.PROVIDERS.keySet())view.moduleStatus(module,"服务器未启用手册连接");return;
        }
        current.bridgeAvailable=true;
        current.send("hub","discover",new JsonObject());
    }
    public static void close() {current=null;}
    /** Optional client-thread callback from MemorialTitles' existing display/reset sync. No IO here. */
    public static void titlesChanged(){ClientHubSession session=current;if(session!=null)session.invalidate("titles");}
    /** Optional client-thread callback from DailyFortunes' confirmed physical result path. */
    public static void fortunesChanged(){ClientHubSession session=current;if(session!=null)session.invalidate("fortunes");}
    private void invalidate(String module){if(available.contains(module))refresh.invalidate(module,nanoTime.getAsLong());}
    public static void tick() {
        ClientHubSession session=current;if(session==null)return;long now=session.nanoTime.getAsLong();
        for(var p:session.queue.expire(now)){if(p.write())session.view.pending(p.module(),false);session.error(p.module(),"请求超时，请稍后重试");}
        if(now>=session.midnightAt&&!session.midnightFired){session.midnightFired=true;session.midnightAt=Long.MAX_VALUE;session.view.advanceDay(session.clockMillis(now));session.invalidate("fortunes");}
        for(String module:session.available)if(session.refresh.due(module,now,session.queue.writing(module)||session.queue.reading(module))) {
            session.queue.invalidateReads(module);
            if(module.equals("titles"))session.view.catalogueInvalidated();
            if(session.send(module,"snapshot",new JsonObject()))session.refresh.requested(module,now);
        }
        session.drain();
    }
    private long clockMillis(long now){return serverClockMillis>0?serverClockMillis+Math.max(0,(now-serverClockAt)/1_000_000L):System.currentTimeMillis();}
    private void scheduleMidnight(){
        String key=view.dayIdentity();if(!key.equals(midnightKey)){midnightKey=key;midnightFired=false;}
        if(midnightFired)return;long now=nanoTime.getAsLong(),delay=Math.max(1_000L,view.nextMidnightEpochMillis()-clockMillis(now));
        midnightAt=now+Math.min(delay,27*3_600_000L)*1_000_000L;
    }
    void action(String module,String action,JsonObject payload) {
        if(!available.contains(module)){
            if(bridgeAvailable&&HubProtocol.PROVIDERS.containsKey(module)&&(action.equals("refresh")||action.equals("snapshot"))){send("hub","discover",new JsonObject());return;}
            view.feedback("此模块暂不可用");return;
        }
        if(action.equals("refresh")){invalidate(module);return;}
        send(module,action,payload);
    }
    private boolean send(String module,String action,JsonObject payload) {
        try {
            queue.offer(module,action,payload,nanoTime.getAsLong());
            view.pending(module,queue.writing(module));drain();return true;
        }catch(IllegalStateException busy){view.feedback(busy.getMessage());return false;}
    }
    private void drain() {
        long now=nanoTime.getAsLong();if(now<nextSendAt)return;
        for(var p:queue.ready(now,1)){nextSendAt=now+500_000_000L;transport.accept(new HubNetwork.Request(generation,p.id(),p.module(),p.action(),p.payload().toString()));}
    }
    private void error(String module,String message){if(module.equals("hub"))for(String provider:HubProtocol.PROVIDERS.keySet())view.moduleStatus(provider,message);else view.moduleStatus(module,message);}
    public static void receive(HubNetwork.Response response) {
        ClientHubSession session=current;if(session==null||session.generation!=response.session())return;
        var ticket=session.queue.complete(response.id(),response.module(),response.action());session.drain();
        if(ticket==null)return;
        String module=ticket.module(),action=ticket.action();boolean write=ticket.write();
        if(write)session.view.pending(module,false);
        if(!response.success()) {
            session.error(module,response.payload());if(write)session.view.feedbackError(response.payload());return;
        }
        try {
            JsonObject result=HubProtocol.object(response.payload(),HubProtocol.RESPONSE_BYTES);
            if(module.equals("hub")) {
                JsonObject modules=result.getAsJsonObject("modules");
                for(String provider:HubProtocol.PROVIDERS.keySet()) {
                    JsonObject state=modules.getAsJsonObject(provider);
                    if(state!=null&&state.has("available")&&state.get("available").getAsBoolean()){session.available.add(provider);session.send(provider,"snapshot",new JsonObject());}
                    else session.view.moduleStatus(provider,"服务器未安装此模块或协议不兼容");
                }
                return;
            }
            session.view.moduleStatus(module,"");
            session.view.acceptLive(module,action,result);
            if(module.equals("manager")) {
                if(result.has("serverTime")&&!result.get("serverTime").isJsonNull()){session.serverClockMillis=result.get("serverTime").getAsLong();session.serverClockAt=session.nanoTime.getAsLong();}
                ClientBootstrap.acceptScope(result);session.scheduleMidnight();
            }
            if(module.equals("titles")) {
                if(!action.equals("catalogue"))session.cataloguePages=0;
                String cursor=result.has("nextCursor")&&!result.get("nextCursor").isJsonNull()?result.get("nextCursor").getAsString():"";
                if(!cursor.isEmpty()) {
                    if(++session.cataloguePages>100){session.view.moduleStatus("titles","称号目录未加载完整，请联系管理员检查目录规模");return;}
                    JsonObject page=new JsonObject();page.addProperty("cursor",cursor);page.addProperty("limit",20);session.send("titles","catalogue",page);
                }else session.view.catalogueComplete();
            }
            if(module.equals("fortunes")&&!action.equals("calendar")&&!action.equals("read")) {
                session.scheduleMidnight();
                if(session.view.needsCalendar()){JsonObject month=new JsonObject();month.addProperty("month",session.view.currentMonth());session.send("fortunes","calendar",month);}
            }
            if(write)session.view.feedbackSuccess("服务器已确认更新");
        } catch(RuntimeException invalid) {session.error(module,"模块返回了无法读取的数据");}
    }
}
