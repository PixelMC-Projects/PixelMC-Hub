/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client;

import com.google.gson.JsonObject;
import com.pixelmc.hub.client.ui.HubView;
import com.pixelmc.hub.network.HubNetwork;
import com.pixelmc.hub.network.HubProtocol;
import com.pixelmc.hub.network.HubRequestQueue;
import com.pixelmc.hub.network.HubRefreshGate;
import com.pixelmc.hub.network.HubSyncCache;
import com.pixelmc.hub.network.HubCalendarDelta;
import java.util.*;
import net.minecraft.client.Minecraft;

/** Connection-owned data; hiding the panel never closes this session. Logout invalidates all callbacks. */
public final class ClientHubSession {
    private static ClientHubSession current;
    private final long generation = java.util.concurrent.ThreadLocalRandom.current().nextLong();
    private final HubView view;
    private final java.util.function.Consumer<HubNetwork.Request> transport;
    private final java.util.function.LongSupplier nanoTime;
    private final HubRequestQueue queue=new HubRequestQueue();
    private final HubRefreshGate refresh=new HubRefreshGate();
    private final HubSyncCache incoming=new HubSyncCache();
    private final Map<String,JsonObject> calendars=new HashMap<>();
    private final Map<Long,JsonObject> calendarBases=new HashMap<>();
    private final HubCalendarCache disk;
    private final java.util.concurrent.Executor io,client;
    private final java.util.function.Consumer<JsonObject> scopeSink;
    private static final java.util.concurrent.ExecutorService CACHE_IO=new java.util.concurrent.ThreadPoolExecutor(1,1,30,java.util.concurrent.TimeUnit.SECONDS,
        new java.util.concurrent.ArrayBlockingQueue<>(32),task->{Thread t=new Thread(task,"PixelMC-Hub archive cache");t.setDaemon(true);return t;},new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
    private String cacheScope="";
    private long monthRequest;
    private boolean incremental,resyncAttempted,panelVisible;
    private final Set<String> available=new HashSet<>();
    private long nextSendAt,serverClockMillis,serverClockAt,midnightAt=Long.MAX_VALUE;
    private String midnightKey="";
    private boolean midnightFired;
    private boolean bridgeAvailable;
    private int cataloguePages;
    private ClientHubSession(HubView view) {this(view,request->HubNetwork.CHANNEL.sendToServer(request),System::nanoTime,
        new HubCalendarCache(Minecraft.getInstance().gameDirectory.toPath().resolve("cache/pixelmc-hub/archives")),CACHE_IO,Minecraft.getInstance()::execute,ClientBootstrap::acceptScope);}
    ClientHubSession(HubView view,java.util.function.Consumer<HubNetwork.Request> transport,java.util.function.LongSupplier nanoTime) {
        this(view,transport,nanoTime,null,Runnable::run,Runnable::run,ignored->{});
    }
    ClientHubSession(HubView view,java.util.function.Consumer<HubNetwork.Request> transport,java.util.function.LongSupplier nanoTime,
                     HubCalendarCache disk,java.util.concurrent.Executor io,java.util.concurrent.Executor client,java.util.function.Consumer<JsonObject> scopeSink) {
        this.disk=disk;this.io=io;this.client=client;this.scopeSink=scopeSink;
        this.view=view;this.transport=transport;this.nanoTime=nanoTime;view.live(this::action);
    }
    public static void open(HubView view) {
        if(current!=null&&current.view==view)return;
        close();current=new ClientHubSession(view);
        var connection=Minecraft.getInstance().getConnection();
        if(connection==null||!HubNetwork.CHANNEL.isRemotePresent(connection.getConnection())) {
            for(String module:HubProtocol.PROVIDERS.keySet())view.moduleStatus(module,"服务器未启用手册连接");return;
        }
        current.bridgeAvailable=true;
        current.send("hub","discover",new JsonObject());
    }
    public static void close() {ClientHubSession old=current;current=null;if(old!=null){old.incoming.clear();old.calendars.clear();old.calendarBases.clear();old.available.clear();old.cacheScope="";old.monthRequest++;old.view.clearSession();}}
    public static void panelClosed(){if(current!=null)current.panelVisible=false;}
    public static void panelOpened(){
        ClientHubSession session=current;if(session==null)return;session.panelVisible=true;
        if(!session.bridgeAvailable)return;
        if(session.available.isEmpty()){if(!session.queue.reading("hub"))session.send("hub","discover",new JsonObject());return;}
        for(String module:session.available)if(!session.view.hasModuleSnapshot(module))session.send(module,"snapshot",new JsonObject());
    }
    /** Optional client-thread callback from MemorialTitles' existing display/reset sync. No IO here. */
    public static void titlesChanged(){ClientHubSession session=current;if(session!=null)session.invalidate("titles");}
    /** Optional client-thread callback from DailyFortunes' confirmed physical result path. */
    public static void fortunesChanged(){ClientHubSession session=current;if(session!=null)session.invalidate("fortunes");}
    private void invalidate(String module){if(available.contains(module))refresh.invalidate(module,nanoTime.getAsLong());}
    public static void tick() {
        ClientHubSession session=current;if(session==null)return;long now=session.nanoTime.getAsLong();
        for(var p:session.queue.expire(now)){session.calendarBases.remove(p.id());if(p.write())session.view.pending(p.module(),false);session.error(p.module(),"请求超时，请稍后重试");}
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
        if(current!=this)return;
        if(!available.contains(module)){
            if(bridgeAvailable&&HubProtocol.PROVIDERS.containsKey(module)&&(action.equals("refresh")||action.equals("snapshot"))){send("hub","discover",new JsonObject());return;}
            view.feedback("此模块暂不可用");return;
        }
        if(action.equals("refresh")){invalidate(module);return;}
        if(module.equals("fortunes")&&action.equals("calendar")){calendar(payload.get("month").getAsString());return;}
        send(module,action,payload);
    }
    private void calendar(String month) {
        HubCalendarDelta.month(month);long request=++monthRequest;
        JsonObject cached=calendars.get(month);
        if(cached!=null){view.acceptLive("fortunes","calendar",cached);sendCalendar(month);return;}
        if(disk==null||cacheScope.isEmpty()){sendCalendar(month);return;}
        String scope=cacheScope;
        try{io.execute(()->{JsonObject saved=disk.load(scope,month);client.execute(()->{
            if(current!=this||request!=monthRequest||!scope.equals(cacheScope))return;
            if(saved!=null){calendars.put(month,saved);view.acceptLive("fortunes","calendar",saved);}
            sendCalendar(month);
        });});}catch(java.util.concurrent.RejectedExecutionException busy){sendCalendar(month);}
    }
    private void sendCalendar(String month) {
        JsonObject payload=new JsonObject();payload.addProperty("month",month);
        if(incremental)payload.addProperty("known","{}");
        send("fortunes",incremental?"calendarSync":"calendar",payload);
    }
    private void rememberCalendar(JsonObject calendar) {
        String month=calendar.get("month").getAsString();calendars.put(month,calendar.deepCopy());
        if(disk==null||cacheScope.isEmpty())return;String scope=cacheScope;JsonObject stable=calendar.deepCopy();
        try{io.execute(()->disk.save(scope,stable));}catch(java.util.concurrent.RejectedExecutionException busy){/* Keep the in-memory authoritative result. */}
    }
    private boolean send(String module,String action,JsonObject payload) {
        try {
            queue.offer(module,action,payload,nanoTime.getAsLong());
            view.pending(module,queue.writing(module));drain();return true;
        }catch(IllegalStateException busy){view.feedback(busy.getMessage());return false;}
    }
    private void drain() {
        long now=nanoTime.getAsLong();if(now<nextSendAt)return;
        for(var p:queue.ready(now,1)){
            nextSendAt=now+500_000_000L;
            if(p.action().equals("calendarSync")) {
                JsonObject base=calendars.get(p.payload().get("month").getAsString());calendarBases.put(p.id(),base==null?null:base.deepCopy());
                p.payload().addProperty("known",base==null?"{}":HubCalendarDelta.known(base).toString());
            }
            transport.accept(new HubNetwork.Request(generation,p.id(),p.module(),p.action(),p.payload().toString()));
        }
    }
    private void error(String module,String message){if(module.equals("hub"))for(String provider:HubProtocol.PROVIDERS.keySet())view.moduleStatus(provider,message);else view.moduleStatus(module,message);}
    public static void receive(HubNetwork.Response response) {
        ClientHubSession session=current;if(session==null||session.generation!=response.session())return;
        JsonObject result=null;
        if(response.success())try{
            // Decode before discarding stale UI replies: the ordered wire baseline must still advance.
            result=session.incoming.decode(HubProtocol.object(response.payload(),HubProtocol.RESPONSE_BYTES));
        }catch(RuntimeException invalid){
            session.queue.complete(response.id(),response.module(),response.action());session.calendarBases.remove(response.id());
            session.error(response.module(),"同步缓存已失效，正在重新读取");
            if(session.incremental&&!session.resyncAttempted){session.resyncAttempted=true;session.send("hub","subscribe",new JsonObject());}
            return;
        }
        var ticket=session.queue.complete(response.id(),response.module(),response.action());session.drain();
        JsonObject calendarBase=session.calendarBases.remove(response.id());
        if(ticket==null)return;
        String module=ticket.module(),action=ticket.action();boolean write=ticket.write();
        if(write)session.view.pending(module,false);
        if(!response.success()) {
            session.error(module,response.payload());if(write)session.view.feedbackError(response.payload());return;
        }
        try {
            if(module.equals("hub")) {
                if(action.equals("subscribe"))session.incoming.clear();
                if(action.equals("discover")&&result.has("sessionSync")&&result.get("sessionSync").getAsInt()==1) {
                    session.incremental=true;session.send("hub","subscribe",new JsonObject());return;
                }
                JsonObject modules=result.getAsJsonObject("modules");
                for(String provider:HubProtocol.PROVIDERS.keySet()) {
                    JsonObject state=modules.getAsJsonObject(provider);
                    if(state!=null&&state.has("available")&&state.get("available").getAsBoolean()){session.available.add(provider);session.send(provider,"snapshot",new JsonObject());}
                    else session.view.moduleStatus(provider,"服务器未安装此模块或协议不兼容");
                }
                return;
            }
            session.view.moduleStatus(module,"");
            if(action.equals("calendarSync")) {
                try{result=HubCalendarDelta.apply(calendarBase,result);}
                catch(RuntimeException badCache){
                    String month=ticket.payload().get("month").getAsString();session.calendars.remove(month);
                    if(calendarBase!=null){session.sendCalendar(month);return;}throw badCache;
                }
                action="calendar";
            }
            session.view.acceptLive(module,action,result);
            if(module.equals("fortunes")&&action.equals("calendar"))session.rememberCalendar(result);
            if(module.equals("manager")) {
                if(result.has("serverTime")&&!result.get("serverTime").isJsonNull()){session.serverClockMillis=result.get("serverTime").getAsLong();session.serverClockAt=session.nanoTime.getAsLong();}
                String scope=HubCalendarCache.identity(result);
                if(!session.cacheScope.equals(scope)){session.calendars.clear();session.calendarBases.clear();session.monthRequest++;session.cacheScope=scope;}
                session.scopeSink.accept(result);session.scheduleMidnight();
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
                if(session.panelVisible&&session.view.needsCalendar())session.calendar(session.view.currentMonth());
            }
            if(write)session.view.feedbackSuccess("服务器已确认更新");
        } catch(RuntimeException invalid) {session.error(module,"模块返回了无法读取的数据");}
    }
}
