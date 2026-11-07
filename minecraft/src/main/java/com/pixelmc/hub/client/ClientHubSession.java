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
import com.pixelmc.hub.network.HubCommunityProtocol;
import com.pixelmc.hub.network.HubUpdatesDelta;
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
    private final com.pixelmc.hub.network.HubFragmentBuffer fragments=new com.pixelmc.hub.network.HubFragmentBuffer();
    private final Map<String,JsonObject> calendars=new HashMap<>();
    private final Map<Long,JsonObject> calendarBases=new HashMap<>();
    private final HubCalendarCache disk;
    private final HubUpdatesCache updatesDisk;
    private final Map<String,JsonObject> updatesMemory=new LinkedHashMap<>();
    private final Map<Long,JsonObject> updatesBases=new HashMap<>();
    private final Map<String,Long> updatesLoads=new HashMap<>();
    private final Set<String> updatesFallback=new HashSet<>();
    private String updatesScope="";
    private boolean updatesIncremental,managerScopeResolved,updatesDeferred;
    private long updateLoadEpoch,updateLoadSequence;
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
    private final Map<String,JsonObject> communityMemory=new LinkedHashMap<>();
    private final Map<Long,Long> communityRequests=new HashMap<>();
    private long communityVersion;
    private boolean communityDirty;
    private int rankingsDirty;
    private boolean rankingsWarmRetried;
    private final long[] rankingVersions=new long[3];
    private final Map<Long,long[]> rankingRequests=new HashMap<>();
    private ClientHubSession(HubView view) {this(view,request->HubNetwork.CHANNEL.sendToServer(request),System::nanoTime,
        new HubCalendarCache(Minecraft.getInstance().gameDirectory.toPath().resolve("cache/pixelmc-hub/archives")),CACHE_IO,Minecraft.getInstance()::execute,ClientBootstrap::acceptScope,new HubUpdatesCache(Minecraft.getInstance().gameDirectory.toPath().resolve("cache/pixelmc-hub/updates")));}
    ClientHubSession(HubView view,java.util.function.Consumer<HubNetwork.Request> transport,java.util.function.LongSupplier nanoTime) {
        this(view,transport,nanoTime,null,Runnable::run,Runnable::run,ignored->{});
    }
    ClientHubSession(HubView view,java.util.function.Consumer<HubNetwork.Request> transport,java.util.function.LongSupplier nanoTime,
                     HubCalendarCache disk,java.util.concurrent.Executor io,java.util.concurrent.Executor client,java.util.function.Consumer<JsonObject> scopeSink) {
        this(view,transport,nanoTime,disk,io,client,scopeSink,null);
    }
    ClientHubSession(HubView view,java.util.function.Consumer<HubNetwork.Request> transport,java.util.function.LongSupplier nanoTime,
                     HubCalendarCache disk,java.util.concurrent.Executor io,java.util.concurrent.Executor client,java.util.function.Consumer<JsonObject> scopeSink,HubUpdatesCache updatesDisk) {
        this.updatesDisk=updatesDisk;this.disk=disk;this.io=io;this.client=client;this.scopeSink=scopeSink;
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
    public static void close() {ClientHubSession old=current;current=null;if(old!=null){old.incoming.clear();old.fragments.clear();old.communityMemory.clear();old.communityRequests.clear();old.communityDirty=false;old.rankingRequests.clear();old.rankingsDirty=0;old.queue.clear("updates");old.updatesMemory.clear();old.updatesBases.clear();old.updatesLoads.clear();old.updatesFallback.clear();old.updateLoadEpoch++;old.updatesScope="";old.calendars.clear();old.calendarBases.clear();old.available.clear();old.cacheScope="";old.monthRequest++;old.view.clearSession();}}
    public static void panelClosed(){if(current!=null){current.panelVisible=false;current.view.panelVisibility(false);}}
    public static void panelOpened(){
        ClientHubSession session=current;if(session==null)return;session.panelVisible=true;session.view.panelVisibility(true);
        if(!session.bridgeAvailable)return;
        if(session.available.isEmpty()){if(!session.queue.reading("hub"))session.send("hub","discover",new JsonObject());return;}
        for(String module:session.available)if(!session.view.hasModuleSnapshot(module))session.send(module,"snapshot",new JsonObject());
    }
    /** Optional client-thread callback from MemorialTitles' existing display/reset sync. No IO here. */
    public static void titlesChanged(){ClientHubSession session=current;if(session!=null)session.invalidate("titles");}
    /** Optional client-thread callback from DailyFortunes' confirmed physical result path. */
    public static void fortunesChanged(){ClientHubSession session=current;if(session!=null)session.invalidate("fortunes");}
    public static void updatesChanged(long generation){ClientHubSession session=current;if(session!=null&&session.generation==generation)session.invalidate("updates");}
    public static void rankingsChanged(long generation,int mask) {
        ClientHubSession session=current;
        if(session==null || session.generation!=generation || !session.available.contains("rankings"))return;
        session.rankingsDirty|=mask&7;
        for(int index=0;index<3;index++)if((mask&(1<<index))!=0)session.rankingVersions[index]++;
    }
    public static void communityChanged(long generation) {
        ClientHubSession session=current;
        if(session==null||session.generation!=generation||!session.available.contains("community"))return;
        session.communityVersion++;
        session.communityDirty=true;
        session.communityMemory.clear();
    }
    private void rememberCommunity(String action,JsonObject query,JsonObject result) {
        String key=HubCommunityProtocol.stream(action,query);
        communityMemory.remove(key);
        communityMemory.put(key,result.deepCopy());
        long weight=communityMemory.values().stream().mapToLong(value->value.toString().length()*2L).sum();
        while(communityMemory.size()>48||weight>8*1024*1024) {
            String first=communityMemory.keySet().iterator().next();
            weight-=communityMemory.remove(first).toString().length()*2L;
        }
    }
    private void failedCommunityWrite() { communityMemory.clear();communityDirty=true; }
    private void finishRanking(HubRequestQueue.Ticket ticket,long[] captured) {
        if(!ticket.module().equals("rankings") || captured==null)return;
        int mask=ticket.action().equals("snapshot")?7:1<<List.of("food","fortunes","online").indexOf(ticket.payload().get("board").getAsString());
        for(int index=0;index<3;index++)if((mask&(1<<index))!=0 && captured[index]==rankingVersions[index])rankingsDirty&=~(1<<index);
    }
    private void invalidate(String module){if(available.contains(module)){if(module.equals("updates")){invalidateUpdateReadLoads();if(!queue.writing(module))queue.invalidateReads(module);}refresh.invalidate(module,nanoTime.getAsLong());}}
    public static void tick() {
        ClientHubSession session=current;if(session==null)return;long now=session.nanoTime.getAsLong();
        session.fragments.expire(now);
        for(var p:session.queue.expire(now)){session.fragments.remove(p.id());session.finishRanking(p,session.rankingRequests.remove(p.id()));session.calendarBases.remove(p.id());session.updatesBases.remove(p.id());if(p.write())session.view.pending(p.module(),false);if(p.module().equals("community")){if(p.write())session.failedCommunityWrite();session.view.communityFailure(p.action(),"请求超时，请稍后重试",p.payload());}else session.error(p.module(),"请求超时，请稍后重试");}
        session.updatesBases.keySet().retainAll(session.queue.pendingIds());
        session.communityRequests.keySet().retainAll(session.queue.pendingIds());
        session.rankingRequests.keySet().retainAll(session.queue.pendingIds());
        if(now>=session.midnightAt&&!session.midnightFired){session.midnightFired=true;session.midnightAt=Long.MAX_VALUE;session.view.advanceDay(session.clockMillis(now));session.invalidate("fortunes");}
        for(String module:session.available)if(session.refresh.due(module,now,session.queue.writing(module)||session.queue.reading(module))) {
            session.queue.invalidateReads(module);
            if(module.equals("titles"))session.view.catalogueInvalidated();
            if(session.send(module,"snapshot",new JsonObject()))session.refresh.requested(module,now);
        }
        if(session.rankingsDirty!=0 && !session.rankingsWarmRetried && !session.view.hasModuleSnapshot("rankings") && !session.queue.reading("rankings")) {
            // One committed hint can repair a login read that raced bridge admission.
            session.rankingsWarmRetried=true;session.send("rankings","snapshot",new JsonObject());
        } else if(session.panelVisible && (session.rankingsDirty&session.view.rankingVisibleMask())!=0 && !session.queue.reading("rankings"))
            session.send("rankings","window",session.view.rankingRefreshQuery());
        if(session.communityDirty&&session.available.contains("community")
                &&(session.panelVisible||!session.view.hasModuleSnapshot("community"))
                &&!session.queue.writing("community")&&!session.queue.reading("community")) {
            session.communityDirty=false;
            session.send("community","snapshot",new JsonObject());
            if(session.panelVisible)session.view.refreshCommunity();
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
            if(module.equals("community"))view.communityFailure(action,"此模块暂不可用，请确认服务器已启用社区资料。",payload);else view.feedback("此模块暂不可用");return;
        }
        if(module.equals("community")) {
            if(action.equals("refresh")){communityDirty=true;return;}
            if(!HubCommunityProtocol.write(action)) {
                JsonObject cached=communityMemory.get(HubCommunityProtocol.stream(action,payload));
                if(cached!=null){JsonObject confirmed=cached.deepCopy();confirmed.add("_hubRequest",payload.deepCopy());view.acceptLive(module,action,confirmed);}
            }
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
    private static boolean updatesWrite(String action){return Set.of("like","comment","deleteComment").contains(HubProtocol.updatesPlain(action));}
    private void invalidateUpdateReadLoads(){for(String read:Set.of("snapshot","packs","list","read"))updatesLoads.put(read,++updateLoadSequence);}
    private static boolean updatesRead(String action){return Set.of("snapshot","packs","list","read","comment","deleteComment").contains(action);}
    private JsonObject updatesQuery(String action,JsonObject payload){
        JsonObject query=payload.deepCopy();query.remove("known");
        if(action.equals("comment")||action.equals("deleteComment")){query=new JsonObject();query.add("id",payload.get("id"));query.addProperty("commentsPage",0);}
        if(action.equals("read")&&query.has("replyPages")&&query.getAsJsonObject("replyPages").size()==0)query.remove("replyPages");
        return query;
    }
    private String updatesKey(String action,JsonObject payload){String plain=HubProtocol.updatesPlain(action);return HubUpdatesCache.key(plain.equals("comment")||plain.equals("deleteComment")?"read":plain,updatesQuery(plain,payload));}
    private String articleKey(JsonObject payload){JsonObject id=new JsonObject();id.add("id",payload.get("id"));return HubUpdatesCache.key("articleBase",id);}
    private void rememberUpdates(String action,JsonObject query,JsonObject data){
        String key=updatesKey(action,query);putUpdates(key,data);
        if(data.has("entry")&&data.getAsJsonObject("entry").has("id")){JsonObject id=new JsonObject();id.add("id",data.getAsJsonObject("entry").get("id"));putUpdates(articleKey(id),data);}
        if(updatesDisk==null||updatesScope.isEmpty())return;String scope=updatesScope;JsonObject copy=data.deepCopy();
        try{io.execute(()->{updatesDisk.save(scope,key,copy);if(copy.has("entry")){JsonObject id=new JsonObject();id.add("id",copy.getAsJsonObject("entry").get("id"));updatesDisk.save(scope,articleKey(id),copy);}});}catch(java.util.concurrent.RejectedExecutionException ignored){}
    }
    private void putUpdates(String key,JsonObject data){updatesMemory.remove(key);updatesMemory.put(key,data.deepCopy());while(updatesMemory.size()>24)updatesMemory.remove(updatesMemory.keySet().iterator().next());}
    private void purgeUpdates(){
        updatesMemory.clear();updatesBases.clear();updatesLoads.clear();updatesFallback.clear();fragments.clear();updateLoadEpoch++;queue.clear("updates");view.pending("updates",false);
        view.purgeCachedUpdates(Set.of(),Set.of());
        if(updatesDisk!=null&&!updatesScope.isEmpty()){String scope=updatesScope;try{io.execute(()->updatesDisk.clear(scope));}catch(java.util.concurrent.RejectedExecutionException ignored){}}
    }
    private void resolveUpdatesScope(JsonObject manager){
        String scope=HubUpdatesCache.identity(manager);boolean first=!managerScopeResolved,changed=!updatesScope.equals(scope);managerScopeResolved=true;
        if(!updatesScope.equals(scope)){updatesMemory.clear();updatesBases.clear();updatesLoads.clear();updatesFallback.clear();fragments.clear();updateLoadEpoch++;queue.clear("updates");view.pending("updates",false);view.purgeCachedUpdates(Set.of(),Set.of());updatesScope=scope;}
        if((updatesDeferred||!first&&changed)&&available.contains("updates")){updatesDeferred=false;send("updates","snapshot",new JsonObject());}
    }
    private boolean sendUpdate(String action,JsonObject payload){
        if(action.equals("snapshot")){invalidateUpdateReadLoads();if(!queue.writing("updates"))queue.invalidateReads("updates");}
        long load=++updateLoadSequence;updatesLoads.put(action,load);
        if(!managerScopeResolved){updatesDeferred=true;return true;}
        String key=updatesKey(action,payload),article=payload.has("id")?articleKey(payload):"";
        JsonObject memory=updatesMemory.get(key);if(memory==null&&!article.isEmpty())memory=updatesMemory.get(article);
        if(memory!=null)putUpdates(key,memory);
        if(action.equals("comment")||action.equals("deleteComment")||memory!=null||updatesDisk==null||updatesScope.isEmpty()||updatesFallback.contains(key)){JsonObject wire=payload.deepCopy();wire.addProperty("known","[]");return sendRaw("updates",action+"Sync",wire);}
        String scope=updatesScope;long epoch=updateLoadEpoch;JsonObject query=payload.deepCopy();
        try{io.execute(()->{JsonObject cached=updatesDisk.load(scope,key);if(cached==null&&!article.isEmpty())cached=updatesDisk.load(scope,article);JsonObject saved=cached;client.execute(()->{
            if(current!=this||epoch!=updateLoadEpoch||!scope.equals(updatesScope)||!Objects.equals(updatesLoads.get(action),load))return;
            if(saved!=null)putUpdates(key,saved);JsonObject wire=query.deepCopy();wire.addProperty("known","[]");sendRaw("updates",action+"Sync",wire);
        });});return true;}catch(java.util.concurrent.RejectedExecutionException busy){JsonObject wire=query.deepCopy();wire.addProperty("known","[]");return sendRaw("updates",action+"Sync",wire);}
    }

    private boolean send(String module,String action,JsonObject payload) {
        if(module.equals("updates")&&updatesIncremental&&updatesRead(action))return sendUpdate(action,payload);
        return sendRaw(module,action,payload);
    }
    private boolean sendRaw(String module,String action,JsonObject payload) {
        try {
            if(module.equals("updates")&&updatesWrite(action)){
                if(queue.writing(module))throw new IllegalStateException("正在等待服务器确认");
                invalidateUpdateReadLoads();
            }
            queue.offer(module,action,payload,nanoTime.getAsLong());
            view.pending(module,queue.writing(module));drain();return true;
        }catch(IllegalStateException busy){if(module.equals("community"))view.communityFailure(action,busy.getMessage(),payload);else view.feedback(busy.getMessage());return false;}
    }
    private void drain() {
        long now=nanoTime.getAsLong();if(now<nextSendAt)return;
        for(var p:queue.ready(now,1)){
            if(p.module().equals("community"))communityRequests.put(p.id(),communityVersion);
            nextSendAt=now+500_000_000L;
            if(p.action().equals("calendarSync")) {
                JsonObject base=calendars.get(p.payload().get("month").getAsString());calendarBases.put(p.id(),base==null?null:base.deepCopy());
                p.payload().addProperty("known",base==null?"{}":HubCalendarDelta.known(base).toString());
            }
            if(HubProtocol.updatesSync(p.module(),p.action())){
                String key=updatesKey(p.action(),p.payload());JsonObject base=updatesFallback.contains(key)?null:updatesMemory.get(key);
                updatesBases.put(p.id(),base==null?null:base.deepCopy());p.payload().addProperty("known",HubUpdatesDelta.known(base));
            }
            if(p.module().equals("rankings"))rankingRequests.put(p.id(),rankingVersions.clone());
            JsonObject wire=p.payload().deepCopy();
            if(p.module().equals("community"))wire.remove("_hubViewRequestId");
            try{transport.accept(new HubNetwork.Request(generation,p.id(),p.module(),p.action(),wire.toString()));}
            catch(RuntimeException failed){
                queue.complete(p.id(),p.module(),p.action());
                communityRequests.remove(p.id());rankingRequests.remove(p.id());
                if(p.write())view.pending(p.module(),false);
                if(p.module().equals("community")){if(p.write())failedCommunityWrite();view.communityFailure(p.action(),"连接中断，请稍后重试。",p.payload());}
                else error(p.module(),"连接中断，请稍后重试。");
            }
        }
    }
    private void error(String module,String message){if(module.equals("manager")&&!managerScopeResolved)resolveUpdatesScope(new JsonObject());if(module.equals("hub"))for(String provider:HubProtocol.PROVIDERS.keySet())view.moduleStatus(provider,message);else view.moduleStatus(module,message);}
    public static void receiveUpdatesPart(HubNetwork.UpdatesPart part){
        ClientHubSession session=current;
        if(session==null||part.session()!=session.generation||!session.queue.awaiting(part.id(),"updates",part.action()))return;
        try{String payload=session.fragments.accept(part.id(),part.action(),part.index(),part.count(),part.bytes(),part.data(),session.nanoTime.getAsLong());if(payload!=null)receive(new HubNetwork.Response(part.session(),part.id(),"updates",part.action(),true,payload));}
        catch(RuntimeException bad){session.fragments.remove(part.id());var ticket=session.queue.complete(part.id(),"updates",part.action());if(ticket!=null){if(ticket.write())session.view.pending("updates",false);session.error("updates","更新日志同步失败，请重新读取");}}
    }
    public static void receive(HubNetwork.Response response) {
        ClientHubSession session=current;if(session==null||session.generation!=response.session())return;
        JsonObject result=null;
        if(response.success())try{
            // Decode before discarding stale UI replies: the ordered wire baseline must still advance.
            result=session.incoming.decode(HubProtocol.object(response.payload(),HubProtocol.maximumResponse(response.module())));
        }catch(RuntimeException invalid){
            var failed=session.queue.complete(response.id(),response.module(),response.action());
            long[] captured=session.rankingRequests.remove(response.id());
            if(failed!=null){
                session.finishRanking(failed,captured);
                if(failed.write())session.view.pending(failed.module(),false);
                if(failed.module().equals("community")){if(failed.write())session.failedCommunityWrite();session.view.communityFailure(failed.action(),"同步缓存已失效，请重新读取。",failed.payload());}
            }
            session.communityRequests.remove(response.id());
            session.calendarBases.remove(response.id());session.updatesBases.remove(response.id());
            if(!response.module().equals("community"))session.error(response.module(),"同步缓存已失效，正在重新读取");
            if(session.incremental&&!session.resyncAttempted){session.resyncAttempted=true;session.send("hub","subscribe",new JsonObject());}
            return;
        }
        var ticket=session.queue.complete(response.id(),response.module(),response.action());session.drain();
        JsonObject calendarBase=session.calendarBases.remove(response.id());
        JsonObject updateBase=session.updatesBases.remove(response.id());
        long[] rankingVersion=session.rankingRequests.remove(response.id());
        Long communityVersion=session.communityRequests.remove(response.id());
        if(ticket==null)return;
        String module=ticket.module(),action=ticket.action();boolean updateSync=HubProtocol.updatesSync(module,action);if(updateSync)action=HubProtocol.updatesPlain(action);boolean write=ticket.write();
        if(write)session.view.pending(module,false);
        if(!response.success()) {
            // A stale immutable page reconciles once through a current window. Other failures wait
            // for a new committed hint or explicit user action, avoiding an idle retry loop.
            if(module.equals("rankings") && Set.of("page","aroundMe").contains(action)
                    && response.payload().contains("排行已更新")) {
                int index=List.of("food","fortunes","online").indexOf(ticket.payload().get("board").getAsString());
                session.rankingsDirty|=1<<index;
            } else session.finishRanking(ticket,rankingVersion);
            if(module.equals("updates")){if(response.payload().contains("不可见")||response.payload().contains("不存在")||response.payload().contains("入口暂未开放")||response.payload().contains("会话已失效"))session.purgeUpdates();session.view.updatesFailure(action,ticket.payload(),response.payload());}
            if(module.equals("community")){if(write)session.failedCommunityWrite();session.view.communityFailure(action,response.payload(),ticket.payload());}
            else {session.error(module,response.payload());if(write)session.view.feedbackError(response.payload());}return;
        }
        try {
            if(module.equals("hub")) {
                if(result.has("updatesCache")&&result.get("updatesCache").getAsInt()==1)session.updatesIncremental=true;
                if(action.equals("subscribe"))session.incoming.clear();
                if(action.equals("discover")&&result.has("sessionSync")&&result.get("sessionSync").getAsInt()==1) {
                    session.incremental=true;session.send("hub","subscribe",new JsonObject());return;
                }
                JsonObject modules=result.getAsJsonObject("modules");
                for(String provider:HubProtocol.PROVIDERS.keySet()) {
                    JsonObject state=modules.getAsJsonObject(provider);
                    if(state!=null&&state.has("available")&&state.get("available").getAsBoolean()){session.available.add(provider);if(provider.equals("updates")&&!session.managerScopeResolved)session.updatesDeferred=true;else session.send(provider,"snapshot",new JsonObject());}
                    else session.view.moduleStatus(provider,"服务器未安装此模块或协议不兼容");
                }
                JsonObject managerState=modules.getAsJsonObject("manager");if(managerState==null||!managerState.has("available")||!managerState.get("available").getAsBoolean())session.resolveUpdatesScope(new JsonObject());
                return;
            }
            if(module.equals("updates")&&updateSync){
                try{result=HubUpdatesDelta.apply(updateBase,result);session.updatesFallback.remove(session.updatesKey(action,ticket.payload()));}
                catch(RuntimeException corrupt){String key=session.updatesKey(action,ticket.payload());if(session.updatesFallback.add(key)){session.updatesMemory.remove(key);JsonObject retry=ticket.payload().deepCopy();retry.remove("known");session.sendUpdate(action,retry);return;}throw corrupt;}
            }
            if(module.equals("updates")){
                if(updatesRead(action))session.rememberUpdates(action,ticket.payload(),result);
                if(action.equals("snapshot")&&result.has("settings")&&result.getAsJsonObject("settings").has("updatesEnabled")&&!result.getAsJsonObject("settings").get("updatesEnabled").getAsBoolean())session.purgeUpdates();
            }
            if(module.equals("manager"))session.resolveUpdatesScope(result);
            session.view.moduleStatus(module,"");
            if(action.equals("calendarSync")) {
                try{result=HubCalendarDelta.apply(calendarBase,result);}
                catch(RuntimeException badCache){
                    String month=ticket.payload().get("month").getAsString();session.calendars.remove(month);
                    if(calendarBase!=null){session.sendCalendar(month);return;}throw badCache;
                }
                action="calendar";
            }
            if(module.equals("updates")||module.equals("rankings")||module.equals("community"))result.add("_hubRequest",ticket.payload().deepCopy());
            if(module.equals("community")) {
                if(write) {
                    session.communityMemory.clear();
                    session.communityDirty=true;
                    if(action.equals("saveProfile"))session.invalidate("fortunes");
                } else {
                    if(communityVersion!=null&&communityVersion<session.communityVersion){session.communityDirty=true;return;}
                    session.rememberCommunity(action,ticket.payload(),result);
                    if(action.equals("snapshot")&&result.has("profile")){
                        String owner=result.getAsJsonObject("profile").get("playerId").getAsString();
                        JsonObject query=new JsonObject();query.addProperty("playerId",owner);
                        JsonObject profile=result.deepCopy();profile.remove("memories");
                        session.rememberCommunity("profile",query,profile);
                        if(result.has("memories")&&result.get("memories").isJsonObject()){
                            query.addProperty("page",0);
                            session.rememberCommunity("memories",query,result.getAsJsonObject("memories"));
                        }
                    }
                }
            }
            session.view.acceptLive(module,action,result);
            if(module.equals("rankings")&&(action.equals("snapshot")||action.equals("window")))session.finishRanking(ticket,rankingVersion);
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
            if(write&&!module.equals("community"))session.view.feedbackSuccess(module.equals("updates")?switch(action){case "comment"->"留言已发送，仅你和管理员可见";case "deleteComment"->"留言已删除";case "like"->ticket.payload().get("liked").getAsBoolean()?"感谢你的支持":"已取消点赞";default->"服务器已确认更新";}:"服务器已确认更新");
        } catch(RuntimeException invalid) {session.finishRanking(ticket,rankingVersion);if(module.equals("community")){if(write)session.failedCommunityWrite();session.view.communityFailure(action,"模块返回了无法读取的数据",ticket.payload());}else session.error(module,"模块返回了无法读取的数据");}
    }
}
