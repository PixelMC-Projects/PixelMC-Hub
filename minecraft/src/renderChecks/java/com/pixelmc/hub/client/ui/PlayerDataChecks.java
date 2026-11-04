/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Synthetic contracts only; no provider, database or real player data. */
public final class PlayerDataChecks {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static JsonObject manager(){return JsonParser.parseString("""
        {"revision":"m1","player":{"uuid":"00000000-0000-4000-8000-000000000001","name":"ContractTester","firstJoinedAt":null,"previousLoginAt":0,"joinCount":4,"totalOnlineMillis":3661000},
        "scope":{"serverId":"synthetic-server","archiveId":"synthetic-archive","packName":"契约测试存档","packVersion":"1.0","timezone":"Asia/Shanghai"},"serverTime":1793755800000}
        """).getAsJsonObject();}
    public static JsonObject titles(){return JsonParser.parseString("""
        {"revision":"t1","equippedTitle":"rich","displayPreferences":{"head":true,"chat":false,"tab":true},"globalDisplay":{"head":true,"chat":true,"tab":false},"nextCursor":null,
        "titles":[{"id":"rich","name":{"segments":[{"text":"拾星","color":12630072,"bold":true,"italic":true,"underline":true,"strike":false},{"text":"之人","color":10312149,"bold":false,"italic":false,"underline":false,"strike":true}],"gradients":[{"positions":[0,1,2,3],"from":1586808,"to":10312149}]},"owned":true,"acquiredAt":1793664000123,"publicDisplay":false,"category":"活动纪念","description":"测试所有字形样式与主题色独立。","acquisition":"参与服务器活动。","presentation":{"badge":"personal","rarity":"rare","theme":"jade"},"palette":{"accent":6534813,"light":14218473,"rarityColor":9475234,"rarityLabel":"稀有"}},
        {"id":"long","name":{"segments":[{"text":"长称号：山川星河与旅途纪念，愿每一个名字都有故事，完整文字可打开详情阅读。","color":5339014,"bold":true,"italic":false,"underline":false,"strike":false}],"gradients":[]},"owned":false,"acquiredAt":0,"publicDisplay":true,"category":"旅途纪念","description":"未拥有的称号也保留原始字形颜色。","acquisition":"未来的旅程。","presentation":{"badge":"mountain","rarity":"none","theme":"slate"},"palette":{"accent":7891404,"light":14738937,"rarityColor":7891404,"rarityLabel":""}}]}
        """).getAsJsonObject();}
    public static JsonObject record(){return JsonParser.parseString("""
        {"id":"synthetic-record","date":"2026-11-04","at":1793795174123,"fortune":{"id":1,"title":"灯认归人","grade":"上上签","poem":["远岫云收见旧津","一灯隔水待归人","柴门未启梅先觉","暗递清香到客巾"],"interpretation":"灯守旧津，梅迎未至。久别之人、搁置之约，皆有重续的余地。"}}
        """).getAsJsonObject();}
    public static JsonObject fortunes(){JsonObject root=new JsonObject();root.addProperty("revision","f1");root.addProperty("timezone","Asia/Shanghai");root.addProperty("today","2026-11-04");root.addProperty("todayState","archived");root.addProperty("archiveCount",1);root.add("todayRecord",record());root.add("featuredRecord",record());root.addProperty("featuredDate","2026-11-04");var recent=new com.google.gson.JsonArray();recent.add(record());root.add("recent",recent);return root;}
    public static JsonObject calendar(){JsonObject root=new JsonObject();root.addProperty("revision","f1");root.addProperty("month","2026-11");root.addProperty("today","2026-11-04");root.addProperty("timezone","Asia/Shanghai");var records=new com.google.gson.JsonArray();records.add(record());root.add("records",records);return root;}
    public static void populate(HubView view){view.live((module,action,payload)->{if(module.equals("fortunes")&&action.equals("calendar")){JsonObject response=calendar();String month=payload.get("month").getAsString();response.addProperty("month",month);if(!month.equals("2026-11"))response.add("records",new com.google.gson.JsonArray());view.acceptLive(module,action,response);}});for(String module:java.util.List.of("manager","titles","fortunes"))view.moduleStatus(module,"");view.acceptLive("manager","snapshot",manager());view.acceptLive("titles","snapshot",titles());view.catalogueComplete();view.acceptLive("fortunes","snapshot",fortunes());view.acceptLive("fortunes","calendar",calendar());}
    public static void main(String[] args) throws Exception {
        HubData data=new HubData();data.clearLive();check(data.titles.isEmpty()&&data.archive.isEmpty(),"Demo values survived real mode");
        data.manager(manager());check(data.firstJoinedAt==0&&data.previousLoginAt==0&&data.totalOnlineMillis==3661000,"Unknown epochs/stats changed");
        data.titles(titles(),false);var rich=data.title("rich");check(rich!=null&&rich.glyphs().size()==4&&rich.glyphs().get(0).bold()&&rich.glyphs().get(0).italic()&&rich.glyphs().get(0).underline()&&rich.glyphs().get(3).strike(),"Rich glyph styles lost");
        check(rich.glyphs().get(0).color()==(0xff000000|1586808)&&rich.glyphs().get(3).color()==(0xff000000|10312149),"Gradient endpoints changed");
        check(rich.icon().equals("personal")&&rich.rarity().equals("稀有")&&!rich.publicDisplay(),"Provider presentation/privacy ignored");
        data.fortunes(fortunes());check(data.archiveCount==1&&data.archive.size()==1&&data.featured!=null,"Archive/feature state lost");
        check(data.todayRecord.time().matches("\\d{2}:\\d{2}:\\d{2}"),"Draw timestamp lost seconds");
        String fortuneTime=data.todayRecord.time();JsonObject utcManager=manager();utcManager.getAsJsonObject("scope").addProperty("timezone","UTC");data.manager(utcManager);
        check(data.managerTimezone.equals("UTC")&&data.timezone.equals("Asia/Shanghai")&&data.record(record()).time().equals(fortuneTime),"Manager refresh overwrote fortune timezone");
        JsonObject unread=fortunes();unread.addProperty("todayState","unread");data.fortunes(unread);check(data.todayRecord==null,"Unread result leaked");
        JsonObject deleted=fortunes();deleted.add("recent",new com.google.gson.JsonArray());deleted.add("featuredRecord",com.google.gson.JsonNull.INSTANCE);deleted.addProperty("archiveCount",0);data.fortunes(deleted);check(data.archive.isEmpty()&&data.featured==null,"Deleted archive/feature remained cached");
        JsonObject cleared=fortunes();cleared.add("featuredDate",com.google.gson.JsonNull.INSTANCE);data.fortunes(cleared);check(data.featured==null,"Server null featuredDate was ignored");
        HubView paged=new HubView();java.util.List<JsonObject> writes=new java.util.ArrayList<>();paged.live((module,action,payload)->writes.add(payload));paged.moduleStatus("titles","");
        JsonObject first=titles();var firstRows=new com.google.gson.JsonArray();firstRows.add(first.getAsJsonArray("titles").get(1));first.add("titles",firstRows);first.addProperty("nextCursor","revision:1");paged.acceptLive("titles","snapshot",first);
        var known=HubView.class.getDeclaredMethod("privacyKnown");known.setAccessible(true);var change=HubView.class.getDeclaredMethod("changeVisibility");change.setAccessible(true);
        check(!(boolean)known.invoke(paged),"Missing equipped title was presented as known privacy");change.invoke(paged);check(writes.isEmpty(),"Visibility write sent before paging complete");
        JsonObject second=titles();var secondRows=new com.google.gson.JsonArray();secondRows.add(second.getAsJsonArray("titles").get(0));second.add("titles",secondRows);paged.acceptLive("titles","catalogue",second);
        check(paged.isCatalogueComplete(),"Terminal page did not atomically publish the complete catalogue");
        paged.catalogueComplete();check((boolean)known.invoke(paged),"Later-page equipped title privacy unresolved");change.invoke(paged);check(writes.size()==1&&writes.get(0).get("visible").getAsBoolean(),"Later-page hidden equipped title did not produce explicit visible=true");
        if(args.length==1) {
            JsonObject actual;try(var input=java.nio.file.Files.newBufferedReader(java.nio.file.Path.of(args[0]))){actual=JsonParser.parseReader(input).getAsJsonObject();}
            data.clearLive();JsonObject titles=actual.getAsJsonObject("titles");data.titles(titles,false);
            check(!data.titles.isEmpty(),"Actual provider title array not decoded");
            check(data.titles.get(0).color()==(0xff000000|titles.getAsJsonArray("titles").get(0).getAsJsonObject().getAsJsonObject("palette").get("accent").getAsInt()),"Actual resolved RGB color changed");
            data.fortunes(actual.getAsJsonObject("fortuneUndrawn"));check(data.todayRecord==null&&data.archiveCount==0,"Actual undrawn state leaked content");
            data.fortunes(actual.getAsJsonObject("fortuneUnread"));check(data.todayRecord==null&&data.archiveCount==0,"Actual unread state leaked content");
            data.fortunes(actual.getAsJsonObject("fortuneArchived"));check(data.todayRecord!=null&&data.archiveCount>0&&!data.archive.isEmpty(),"Actual archived projection lost");
            data.fortunes(actual.getAsJsonObject("fortuneFeatured"));check(data.featured!=null&&data.featured.date().toString().equals(actual.getAsJsonObject("fortuneFeatured").get("featuredDate").getAsString()),"Actual committed feature not decoded");
            data.calendar(actual.getAsJsonObject("fortuneCalendar"));check(!data.archive.isEmpty(),"Actual month array not decoded");
            check(data.record(actual.getAsJsonObject("fortuneRead"))!=null,"Actual direct read record not decoded");
            System.out.println("PASS: actual safe serialized Titles/Fortunes responses from isolated managed HTTP chain decoded by Hub");
        }
        System.out.println("PASS: live/demo isolation, unknown historical values, glyph styles/gradients, resolved metadata, archived-only state, exact draw seconds and deleted references");
    }
}
