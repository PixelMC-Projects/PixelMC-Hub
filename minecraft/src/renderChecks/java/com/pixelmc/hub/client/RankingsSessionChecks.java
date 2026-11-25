package com.pixelmc.hub.client;

import com.google.gson.*;
import com.pixelmc.hub.client.ui.HubView;
import com.pixelmc.hub.network.*;
import java.lang.reflect.*;
import java.util.*;

/** Real connection controller with a synthetic transport, without a running game. */
public final class RankingsSessionChecks {
    private static int checks;
    private static long now;
    private static final List<HubNetwork.Request> sent=new ArrayList<>();
    private static Field field(Class<?> type,String name)throws Exception{Field field=type.getDeclaredField(name);field.setAccessible(true);return field;}
    private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);checks++;}
    private static void tick(){now+=1_000_000_000L;ClientHubSession.tick();}
    private static HubNetwork.Request last(){return sent.get(sent.size()-1);}
    private static void reply(JsonObject value){var request=last();ClientHubSession.receive(new HubNetwork.Response(request.session(),request.id(),request.module(),request.action(),true,value.toString()));}
    private static void navigate(HubView view,int page)throws Exception{Method method=HubView.class.getDeclaredMethod("navigate",int.class);method.setAccessible(true);method.invoke(view,page);}
    private static JsonObject window(JsonObject snapshot,String board){JsonObject result=new JsonObject();var header=snapshot.getAsJsonObject("boards").getAsJsonObject(board);result.add("header",header.deepCopy());JsonArray pages=new JsonArray();pages.add(header.deepCopy());result.add("pages",pages);return result;}
    @SuppressWarnings("unchecked")
    public static void main(String[] args)throws Exception {
        JsonObject snapshot;
        try(var stream=RankingsSessionChecks.class.getResourceAsStream("/assets/pixelmc_hub/rankings-preview.json")){
            snapshot=JsonParser.parseReader(new java.io.InputStreamReader(Objects.requireNonNull(stream),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("snapshots").get(0).getAsJsonObject();
        }
        HubView view=new HubView();ClientHubSession session=new ClientHubSession(view,sent::add,()->now);
        field(ClientHubSession.class,"current").set(null,session);field(ClientHubSession.class,"bridgeAvailable").setBoolean(session,true);
        ((Set<String>)field(ClientHubSession.class,"available").get(session)).add("rankings");
        session.action("rankings","snapshot",new JsonObject());reply(snapshot);long generation=last().session();
        int before=sent.size();ClientHubSession.rankingsChanged(generation,7);for(int i=0;i<30;i++)tick();
        check(sent.size()==before,"Closed panel did not defer hints");
        ClientHubSession.panelOpened();tick();check(sent.size()==before,"Home page fetched invisible rankings");
        navigate(view,4);tick();check(sent.size()==before+1&&last().action().equals("window"),"Visible dirty board did not reconcile once");
        check(JsonParser.parseString(last().payload()).getAsJsonObject().get("board").getAsString().equals("food"),"Unrelated boards fetched");
        reply(window(snapshot,"food"));before=sent.size();for(int i=0;i<30;i++)tick();check(sent.size()==before,"Inactive dirty boards created a polling loop");
        ClientHubSession.rankingsChanged(generation,4);tick();check(sent.size()==before,"Online hint fetched the food board");
        ClientHubSession.rankingsChanged(generation,1);tick();check(sent.size()==before+1,"Food hint was lost");
        ClientHubSession.rankingsChanged(generation,1);reply(window(snapshot,"food"));tick();check(sent.size()==before+2,"In-flight notification was cleared by an older response");
        reply(window(snapshot,"food"));before=sent.size();tick();check(sent.size()==before,"Settled reconciliation repeated");
        ClientHubSession.rankingsChanged(generation+1,7);tick();check(sent.size()==before,"Foreign generation hint accepted");
        ClientHubSession.rankingsChanged(generation,1);tick();var failed=last();
        ClientHubSession.receive(new HubNetwork.Response(failed.session(),failed.id(),failed.module(),failed.action(),false,"连接暂不可用"));
        before=sent.size();for(int i=0;i<20;i++)tick();check(sent.size()==before,"Failure created an idle retry loop");
        ClientHubSession.rankingsChanged(generation,1);tick();failed=last();
        ClientHubSession.receive(new HubNetwork.Response(failed.session(),failed.id(),failed.module(),failed.action(),true,"bad json"));
        check(((Map<?,?>)field(ClientHubSession.class,"rankingRequests").get(session)).isEmpty(),"Malformed response retained captured request versions");
        before=sent.size();tick();check(sent.size()==before,"Malformed response retried forever");
        ClientHubSession.panelClosed();ClientHubSession.rankingsChanged(generation,1);tick();check(sent.size()==before,"Closed panel fetched data");
        ClientHubSession.close();check(!view.hasModuleSnapshot("rankings"),"Logout retained leaderboard data");
        ClientHubSession.rankingsChanged(generation,7);reply(snapshot);tick();check(sent.size()==before&&!view.hasModuleSnapshot("rankings"),"Late response repopulated a logged-out session");
        HubView freshView=new HubView();ClientHubSession fresh=new ClientHubSession(freshView,sent::add,()->now);
        field(ClientHubSession.class,"current").set(null,fresh);
        ((Set<String>)field(ClientHubSession.class,"available").get(fresh)).add("rankings");
        fresh.action("rankings","snapshot",new JsonObject());var loginRead=last();
        ClientHubSession.receive(new HubNetwork.Response(loginRead.session(),loginRead.id(),"rankings","snapshot",false,"玩家资料尚未完成入服确认"));
        before=sent.size();ClientHubSession.rankingsChanged(loginRead.session(),7);tick();
        check(sent.size()==before+1&&last().action().equals("snapshot"),"Committed admission hint did not repair login preload");
        var warm=last();ClientHubSession.receive(new HubNetwork.Response(warm.session(),warm.id(),"rankings","snapshot",false,"连接暂不可用"));
        before=sent.size();for(int index=0;index<5;index++){ClientHubSession.rankingsChanged(loginRead.session(),7);tick();}
        check(sent.size()==before,"Hidden login fallback exceeded one automatic retry");ClientHubSession.close();
        System.out.println("PASS rankings session assertions: "+checks);
    }
}
