/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client;
import com.google.gson.*;
import com.pixelmc.hub.client.ui.HubView;
import com.pixelmc.hub.network.*;
import java.lang.reflect.*;
import java.util.*;

/** Synthetic transport against the actual client controller; no world, database or running game. */
public final class UpdatesSessionChecks {
    private static long now;
    private static final List<HubNetwork.Request> sent=new ArrayList<>();
    private static void check(boolean yes,String why){if(!yes)throw new AssertionError(why);}
    private static void rejects(Runnable f){try{f.run();throw new AssertionError("Invalid payload accepted");}catch(IllegalArgumentException expected){}}
    private static Field field(Class<?> c,String name)throws Exception{Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}
    private static void tick(long ms){now+=ms*1_000_000L;ClientHubSession.tick();}
    private static HubNetwork.Request last(){return sent.get(sent.size()-1);}
    private static void reply(HubNetwork.Request r,JsonObject value){ClientHubSession.receive(new HubNetwork.Response(r.session(),r.id(),r.module(),r.action(),true,value.toString()));}
    public static JsonObject snapshot(){return JsonParser.parseString("""
        {"settings":{"updatesEnabled":true,"likesEnabled":true,"commentsEnabled":true,"commentLimit":1000},
         "packs":[{"id":"00000000-0000-0000-0000-000000000001","name":"香草纪元","shortName":"香草纪元","version":"2.7.1","current":true}],
         "selectedPackId":"00000000-0000-0000-0000-000000000001","list":{"page":0,"size":12,"total":1,"rows":[
           {"id":"00000000-0000-0000-0000-000000000002","packId":"00000000-0000-0000-0000-000000000001","packName":"香草纪元","packVersion":"2.7.1","updateVersion":"1.0","title":"把每一次小改变，都好好记下来","summary":"更轻盈的手册，更清楚的旅途。这里记录我们的每一点变化。","category":"功能更新","pinned":true,"publishedAt":1793835000000,"liked":false,"likesCount":9,"commentsCount":1}]},"revision":"synthetic:1"}
        """).getAsJsonObject();}
    public static JsonObject reading(){JsonObject value=new JsonObject();JsonObject entry=snapshot().getAsJsonObject("list").getAsJsonArray("rows").get(0).getAsJsonObject().deepCopy();entry.addProperty("body","### 让变化有迹可循\n新版手册现在可以阅读**完整更新日志**，关注整合包的每一次成长。\n- 阅读清晰的结构化正文\n- 点赞支持喜欢的变化\n- 向管理员反馈遇到的问题\n### 一点点打磨，都是为了更顺手\n保留已经熟悉的交互，把细节做得更自然。\n文字中的 <script>alert(1)</script> 会原样显示，不会执行。\n");value.add("entry",entry);JsonObject page=new JsonObject();JsonArray comments=new JsonArray();JsonObject c=new JsonObject();c.addProperty("id","00000000-0000-0000-0000-000000000003");c.addProperty("authorName","SyntheticPlayer");c.addProperty("text","手册里的阅读体验很舒服，希望今后能继续看到每一次变化。\n这一条只属于当前测试玩家。");c.addProperty("createdAt",1793837000000L);JsonArray replies=new JsonArray();JsonObject r=new JsonObject();r.addProperty("text","谢谢你的反馈！会继续把细节打磨好。");r.addProperty("createdAt",1793838000000L);replies.add(r);c.add("replies",replies);c.addProperty("replyCount",1);comments.add(c);page.add("rows",comments);page.addProperty("total",1);page.addProperty("page",0);page.addProperty("size",12);value.add("comments",page);value.addProperty("revision","synthetic:1");return value;}

    private static final String TEST_PACK = "00000000-0000-0000-0000-000000000001";

    private static JsonObject noticePage(int total, int pinned, int index, String revision) {
        JsonObject page = new JsonObject();
        page.addProperty("page", index);
        page.addProperty("size", 12);
        page.addProperty("total", total);
        page.addProperty("revision", revision);
        JsonArray rows = new JsonArray();
        for (int i = index * 12; i < Math.min(total, (index + 1) * 12); i++) {
            JsonObject row = new JsonObject();
            row.addProperty("id", noticeId(i));
            row.addProperty("packId", TEST_PACK);
            row.addProperty("title", "第 " + i + " 条更新");
            row.addProperty("pinned", i < pinned);
            row.addProperty("publishedAt", (i < pinned ? 1_700_000L : 1_800_000L) - i);
            row.addProperty("body", "首页摘要无需正文");
            rows.add(row);
        }
        page.add("rows", rows);
        return page;
    }

    private static String noticeId(int index) {
        return String.format(Locale.ROOT, "00000000-0000-0000-0001-%012d", index);
    }

    private static JsonObject noticeSnapshot(int total, int pinned, String revision) {
        JsonObject snapshot = snapshot();
        snapshot.add("list", noticePage(total, pinned, 0, revision));
        snapshot.addProperty("revision", revision);
        return snapshot;
    }

    private static List<String> noticeIds(JsonArray rows) {
        List<String> ids = new ArrayList<>();
        for (var row : rows) ids.add(row.getAsJsonObject().get("id").getAsString());
        return ids;
    }

    private static void legacyStationChecks() throws Exception {
        LegacyStationNotices scan = LegacyStationNotices.begin(noticeSnapshot(26, 23, "pins:1"));
        check(scan.pending(), "Pin-only first page did not request the next page");
        check(scan.query().get("page").getAsInt() == 1, "Adjacent page was skipped");
        scan.accept(noticePage(26, 23, 1, "pins:1"));
        check(scan.query().get("page").getAsInt() == 2, "Single boundary row hid the second notice");
        scan.accept(noticePage(26, 23, 2, "pins:1"));
        JsonArray rows = scan.viewResult().getAsJsonArray("rows");
        check(noticeIds(rows).equals(List.of(noticeId(23), noticeId(24))), "Wrong boundary leaders");
        check(!rows.get(0).getAsJsonObject().has("body"), "Legacy feed retained body fields");

        scan = LegacyStationNotices.begin(noticeSnapshot(12_002, 10_811, "pins:2"));
        int requests = 0;
        while (scan.pending() && requests < 18) {
            int next = scan.query().get("page").getAsInt();
            scan.accept(noticePage(12_002, 10_811, next, "pins:2"));
            requests++;
        }
        check(scan.complete() && requests < 18, "Pin boundary search was not bounded logarithmically");
        check(noticeIds(scan.viewResult().getAsJsonArray("rows"))
                .equals(List.of(noticeId(10_811), noticeId(10_812))), "Wrong multi-page leaders");

        scan = LegacyStationNotices.begin(noticeSnapshot(45, 45, "pins:3"));
        while (scan.pending()) {
            int next = scan.query().get("page").getAsInt();
            scan.accept(noticePage(45, 45, next, "pins:3"));
        }
        check(noticeIds(scan.viewResult().getAsJsonArray("rows"))
                .equals(List.of(noticeId(0), noticeId(1))), "All-pinned list lost its leaders");
        scan = LegacyStationNotices.begin(noticeSnapshot(25, 10, "pins:4"));
        check(scan.complete(), "Already sufficient first page caused extra traffic");
        check(noticeIds(scan.viewResult().getAsJsonArray("rows"))
                .equals(List.of(noticeId(10), noticeId(11))), "Convenient first page returned wrong leaders");

        scan = LegacyStationNotices.begin(noticeSnapshot(26, 23, "pins:5"));
        scan.accept(noticePage(26, 23, 1, "pins:new"));
        check(!scan.pending() && !scan.complete(), "Revision mismatch published a mixed feed");
        scan = LegacyStationNotices.begin(noticeSnapshot(26, 23, "pins:6"));
        JsonObject wrongPack = noticePage(26, 23, 1, "pins:6");
        wrongPack.getAsJsonArray("rows").get(11).getAsJsonObject().addProperty("packId", noticeId(123));
        scan.accept(wrongPack);
        check(!scan.complete() && scan.viewResult().getAsJsonArray("rows").isEmpty(),
                "Foreign pack row entered station feed");
        JsonObject newService = noticeSnapshot(26, 23, "pins:7");
        newService.add("stationNotices", new JsonArray());
        check(LegacyStationNotices.begin(newService) == null, "New service with empty feed triggered scan");

        HubView view = new HubView();
        view.setWinter(true);
        ClientHubSession session = new ClientHubSession(view, sent::add, () -> now);
        field(ClientHubSession.class, "current").set(null, session);
        ((Set<String>) field(ClientHubSession.class, "available").get(session)).add("updates");
        session.action("updates", "snapshot", new JsonObject());
        reply(last(), noticeSnapshot(26, 23, "live:1"));
        int before = sent.size();
        tick(1000);
        check(sent.size() == before, "Closed panel fetched background pages");
        ClientHubSession.panelOpened();
        tick(500);
        HubNetwork.Request station = last();
        JsonObject query = HubProtocol.payload("updates", "list", station.payload());
        check(station.action().equals("list") && query.get("page").getAsInt() == 1,
                "Expected one permitted legacy list read");
        session.action("updates", "list", query);
        tick(500);
        HubNetwork.Request reader = last();
        check(reader.action().equals("list") && reader.id() != station.id(),
                "Background list deduplicated a foreground list request");
        reply(station, noticePage(26, 23, 1, "live:1"));
        tick(500);
        HubNetwork.Request tail = last();
        check(HubProtocol.payload("updates", "list", tail.payload()).get("page").getAsInt() == 2,
                "Foreground list canceled background boundary");
        reply(tail, noticePage(26, 23, 2, "live:1"));
        Object updateView = field(HubView.class, "updates").get(view);
        var stationNotices = updateView.getClass().getDeclaredMethod("stationNotices");
        stationNotices.setAccessible(true);
        List<?> notices = (List<?>) stationNotices.invoke(updateView);
        check(notices.size() == 2 && ((JsonObject) notices.get(0)).get("id").getAsString()
                .equals(noticeId(23)), "The latest legacy notice was not delivered to the homepage");
        reply(reader, noticePage(26, 23, 1, "live:1"));
        check(((List<?>) stationNotices.invoke(updateView)).equals(notices),
                "Foreground list displaced background notices");
        check(sent.stream().noneMatch(request -> request.action().startsWith("mark")
                || request.action().equals("read")), "Home scan loaded an article or marked it seen");

        var scanField = field(ClientHubSession.class, "stationNotices");
        Object finished = scanField.get(session);
        tick(500);
        session.action("updates", "snapshot", new JsonObject());
        reply(last(), noticeSnapshot(26, 23, "live:1"));
        before = sent.size();
        tick(1000);
        HubRequestQueue queue = (HubRequestQueue) field(ClientHubSession.class, "queue").get(session);
        check(finished == scanField.get(session) && !queue.readingStationNotices()
                && ((List<?>) stationNotices.invoke(updateView)).equals(notices),
                "Same-revision refresh repeated a finished scan");
        // Regular directory refresh can reconcile its previously loaded second page.
        if (sent.size() > before) {
            reply(last(), noticePage(26, 23, 1, "live:1"));
            tick(500);
        }

        session.action("updates", "snapshot", new JsonObject());
        reply(last(), noticeSnapshot(26, 23, "live:2"));
        tick(500);
        HubNetwork.Request stale = last();
        if (!queue.stationNoticeResponse(stale.id(), stale.module(), stale.action())) {
            reply(stale, noticePage(26, 23, 1, "live:2"));
            tick(500);
            stale = last();
        }
        check(queue.stationNoticeResponse(stale.id(), stale.module(), stale.action()),
                "New revision reused a stale station projection");
        tick(500);
        session.action("updates", "snapshot", new JsonObject());
        JsonObject authoritative = noticeSnapshot(26, 23, "live:3");
        authoritative.add("stationNotices", new JsonArray());
        reply(last(), authoritative);
        reply(stale, noticePage(26, 23, 1, "live:2"));
        before = sent.size();
        tick(1000);
        check(scanField.get(session) == null && !queue.readingStationNotices()
                && ((List<?>) stationNotices.invoke(updateView)).isEmpty(),
                "Delayed legacy page overrode the authoritative feed");
        ClientHubSession.close();
    }

    public static void main(String[] args)throws Exception{
        String id="00000000-0000-0000-0000-000000000002";
        HubProtocol.payload("updates","read","{\"id\":\""+id+"\",\"commentsPage\":0,\"replyPages\":{\"00000000-0000-0000-0000-000000000003\":1}}");
        rejects(()->HubProtocol.payload("updates","snapshot","{\"uuid\":\"other\"}"));
        rejects(()->HubProtocol.payload("updates","list","{\"packId\":\""+id+"\",\"page\":-1,\"search\":\"\"}"));
        rejects(()->HubProtocol.payload("updates","read","{\"id\":\""+id+"\",\"commentsPage\":0,\"replyPages\":{\"sql\":1}}"));
        rejects(()->HubProtocol.payload("updates","like","{\"id\":\""+id+"\",\"liked\":\"true\"}"));
        rejects(()->HubProtocol.payload("updates","comment","{\"id\":\""+id+"\",\"text\":\"x\",\"operationId\":\"not-uuid\"}"));
        rejects(()->HubProtocol.payload("updates","admin","{}"));
        HubProtocol.payload("updates","packs","{\"page\":0}");rejects(()->HubProtocol.payload("updates","packs","{\"page\":0,\"uuid\":\"other\"}"));
        HubFragmentBuffer fragments=new HubFragmentBuffer();
        rejects(()->fragments.accept(1,"read",0,100,HubFragmentBuffer.MAX_BYTES,new byte[0],0));
        check(fragments.activeCount()==0,"Invalid header allocated an assembly");
        String huge="{\"x\":\""+"中".repeat(500000)+"\"}";byte[] data=huge.getBytes(java.nio.charset.StandardCharsets.UTF_8);int parts=(data.length+HubFragmentBuffer.PART_BYTES-1)/HubFragmentBuffer.PART_BYTES;String assembled=null;
        for(int i=parts-1;i>=0;i--){int start=i*HubFragmentBuffer.PART_BYTES;String got=fragments.accept(1,"read",i,parts,data.length,Arrays.copyOfRange(data,start,Math.min(data.length,start+HubFragmentBuffer.PART_BYTES)),0);if(got!=null)assembled=got;}
        check(huge.equals(assembled)&&fragments.activeCount()==0,"Out-of-order UTF-8 fragment reassembly failed");
        byte[] first=Arrays.copyOfRange(data,0,HubFragmentBuffer.PART_BYTES);for(int i=1;i<=4;i++)fragments.accept(i,"read",0,parts,data.length,first,0);
        rejects(()->fragments.accept(5,"read",0,parts,data.length,first,0));check(fragments.activeCount()==4,"Assembly count unbounded");fragments.expire(10_000_000_000L);check(fragments.activeCount()==0,"Partial response expiry failed");
        ClientHubSession.close();HubView view=new HubView();ClientHubSession session=new ClientHubSession(view,sent::add,()->now);field(ClientHubSession.class,"current").set(null,session);((Set<String>)field(ClientHubSession.class,"available").get(session)).add("updates");
        session.action("updates","snapshot",new JsonObject());reply(last(),snapshot());check(view.hasModuleSnapshot("updates"),"Preload missing");int before=sent.size();
        for(int i=0;i<100;i++)tick(10000);check(before==sent.size(),"Updates idle polled");
        long generation=field(ClientHubSession.class,"generation").getLong(session);
        ClientHubSession.receiveUpdatesPart(new HubNetwork.UpdatesPart(generation,999,"read",0,parts,data.length,first));check(((HubFragmentBuffer)field(ClientHubSession.class,"fragments").get(session)).activeCount()==0,"Unrequested response allocated storage");
        for(int i=0;i<30;i++)ClientHubSession.updatesChanged(generation);tick(200);check(sent.size()==before+1&&last().action().equals("snapshot"),"Hint burst did not coalesce");
        check(view.hasModuleSnapshot("updates"),"Refresh blanked committed page");reply(last(),snapshot());tick(1000);
        JsonObject like=new JsonObject();like.addProperty("id",id);like.addProperty("liked",true);session.action("updates","like",like);HubNetwork.Request write=last();check(write.action().equals("like"),"Like not dispatched");
        for(int i=0;i<20;i++)ClientHubSession.updatesChanged(generation);before=sent.size();tick(1000);check(sent.size()==before,"Hint interrupted write");JsonObject liked=new JsonObject();liked.addProperty("id",id);liked.addProperty("liked",true);liked.addProperty("likesCount",10);reply(write,liked);tick(200);check(sent.size()==before+1,"Hint during write lost");reply(last(),snapshot());
        tick(1000);JsonObject query=new JsonObject();query.addProperty("id",id);query.addProperty("commentsPage",0);session.action("updates","read",query);HubNetwork.Request reading=last();
        JsonObject bigRead=reading();bigRead.getAsJsonObject("entry").addProperty("body","中".repeat(500000));byte[] large=bigRead.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);int count=(large.length+HubFragmentBuffer.PART_BYTES-1)/HubFragmentBuffer.PART_BYTES;
        ClientHubSession.receiveUpdatesPart(new HubNetwork.UpdatesPart(generation+1,reading.id(),"read",0,count,large.length,Arrays.copyOfRange(large,0,HubFragmentBuffer.PART_BYTES)));check(((HubFragmentBuffer)field(ClientHubSession.class,"fragments").get(session)).activeCount()==0,"Wrong session allocated storage");
        for(int i=count-1;i>=0;i--){int start=i*HubFragmentBuffer.PART_BYTES;ClientHubSession.receiveUpdatesPart(new HubNetwork.UpdatesPart(generation,reading.id(),"read",i,count,large.length,Arrays.copyOfRange(large,start,Math.min(large.length,start+HubFragmentBuffer.PART_BYTES))));}
        check(((HubFragmentBuffer)field(ClientHubSession.class,"fragments").get(session)).activeCount()==0,"Completed response retained fragments");
        before=sent.size();ClientHubSession.updatesChanged(generation+1);tick(1000);check(before==sent.size(),"Wrong generation hint accepted");
        ClientHubSession.close();check(!view.hasModuleSnapshot("updates"),"Logout retained private updates");before=sent.size();ClientHubSession.updatesChanged(generation);reply(last(),snapshot());tick(100000);check(before==sent.size()&&!view.hasModuleSnapshot("updates"),"Logout accepted callback");
        sent.clear();
        legacyStationChecks();
        System.out.println("PASS: updates allowlist/identity/types/paging, bounded login preload, no idle scans, coalesced change hints, visible refresh, write gating, large fragmented UTF-8 replies/limits/expiry and generation/logout isolation");
    }
}
