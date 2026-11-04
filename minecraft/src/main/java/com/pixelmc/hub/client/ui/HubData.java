/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** UI records, safe provider DTO decoding, and explicit standalone demo fixtures. */
final class HubData {
    static final LocalDate TODAY = LocalDate.of(2026, 11, 3);
    record Glyph(String text,int color,boolean bold,boolean italic,boolean underline,boolean strike) {}
    record Title(String id, String name, String icon, String category, String rarity,
                 boolean owned, String date, String description, String condition, int color, int light,
                 int rarityColor,boolean publicDisplay,List<Glyph> glyphs,long acquiredAt) {
        Title(String id,String name,String icon,String category,String rarity,boolean owned,String date,String description,String condition,int color,int light) {
            this(id,name,icon,category,rarity,owned,date,description,condition,color,light,color,true,List.of(),0);
        }
    }
    record Fortune(int id, String title, String grade, List<String> poem, String interpretation) {}
    record Entry(LocalDate date, String time, Fortune fortune,long at) {
        Entry(LocalDate date,String time,Fortune fortune){this(date,time,fortune,date.atTime(java.time.LocalTime.parse(time)).atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli());}
    }
    record Keepsake(Title title,Entry entry,long at) {
        String key(){return title!=null?"title:"+title.id():"fortune:"+entry.date();}
    }
    List<Keepsake> recentKeepsakes(Map<LocalDate,Entry> visibleArchive) {
        List<Keepsake> rows=new ArrayList<>();
        for(Title title:titles)if(title.owned())rows.add(new Keepsake(title,null,title.acquiredAt()));
        for(Entry entry:visibleArchive.values())rows.add(new Keepsake(null,entry,entry.at()));
        return rows.stream().sorted(java.util.Comparator.comparingLong(Keepsake::at).reversed().thenComparing(Keepsake::key)).limit(3).toList();
    }

    static final List<Title> TITLES = List.of(
        new Title("dew", "朝露行者", "leaf", "旅途纪念", "珍藏", true, "2026/11/02", "晨露未晞，旅途初明。送给认真走过每一段旅程的你。", "在香草纪元累计游玩 100 小时。", 0xff63b69d, 0xffd8f4e9),
        new Title("mountain", "山野来客", "mountain", "初来乍到", "普通", true, "2026/10/17", "山川不问来处，风与草木皆是新朋友。", "第一次踏入香草纪元。", 0xff77a9cc, 0xffdcedfc),
        new Title("star", "拾星之人", "star", "活动纪念", "稀有", true, "2026/10/30", "把偶然遇见的微光，收成属于自己的一片星河。", "参与「星夜漫游」服务器活动。", 0xffb093e0, 0xffede1ff),
        new Title("wood", "林间筑梦", "house", "旅途纪念", "稀有", true, "2026/10/26", "一木一石，筑起日常；一窗一灯，等候归人。", "参与服务器公共建筑共建。", 0xffd5a178, 0xfff7e5d3),
        new Title("moon", "月下听风", "moon", "活动纪念", "珍藏", true, "2026/10/24", "留一盏灯，听一夜风，也把此刻留在旅途中。", "参与「秋夜相聚」服务器活动。", 0xff9b8cdb, 0xffe9e3ff),
        new Title("harvest", "丰收时节", "wheat", "旅途纪念", "普通", true, "2026/10/21", "播种的日子已有回响，满怀收获，赴下一程。", "完成第一季社区农场收获。", 0xffd0ae4f, 0xfffaf0ca),
        new Title("dawn", "曙光同行", "sun", "旅途纪念", "珍藏", false, "", "长路漫漫，总有人与你一同迎接曙光。", "在香草纪元累计游玩 300 小时。", 0xffdf9c79, 0xffffe7da),
        new Title("cloud", "云外归客", "cloud", "活动纪念", "稀有", false, "", "行过远山与流云，仍记得回家的路。", "参与后续开放的探索活动。", 0xff91a5cb, 0xffe6edfb)
    );
    final Map<Integer, Fortune> fortunes = new LinkedHashMap<>();
    final Map<LocalDate, Entry> archive = new LinkedHashMap<>();
    final Map<LocalDate, Entry> recentArchive = new LinkedHashMap<>();
    final java.util.Set<java.time.YearMonth> calendarMonths=new java.util.HashSet<>();
    final List<Title> titles = new ArrayList<>(TITLES);
    LocalDate today=TODAY;
    Entry todayRecord,featured;
    int archiveCount;
    String playerUuid="",packName="",packVersion="",scopeId="",timezone="Asia/Shanghai",managerTimezone=java.time.ZoneId.systemDefault().getId();
    long firstJoinedAt,previousLoginAt,joinCount,totalOnlineMillis,serverTime;
    long onlineBaselineAt;
    long onlineMillis(long now){return managerLoaded?totalOnlineMillis+Math.max(0,(now-onlineBaselineAt)/1_000_000L):0;}
    boolean managerLoaded,titlesLoaded,fortunesLoaded;
    final Map<String,String> revisions=new LinkedHashMap<>();
    JsonObject displayPreferences=new JsonObject(),globalDisplay=new JsonObject();

    HubData() {
        try (InputStream stream = HubData.class.getResourceAsStream("/assets/pixelmc_hub/data/fortunes.json")) {
            if (stream != null) {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                for (JsonElement element : root.getAsJsonArray("fortunes")) {
                    JsonObject f = element.getAsJsonObject();
                    List<String> poem = new ArrayList<>();
                    for (JsonElement line : f.getAsJsonArray("poem")) poem.add(line.getAsString());
                    Fortune fortune = new Fortune(f.get("id").getAsInt(), f.get("title").getAsString(),
                        f.get("grade").getAsString(), List.copyOf(poem), f.get("interpretation").getAsString());
                    fortunes.put(fortune.id(), fortune);
                }
            }
        } catch (Exception ignored) {
            // The UI stays usable if a development resources directory is incomplete.
        }
        fortunes.putIfAbsent(1, new Fortune(1, "灯认归人", "上上签", List.of("远岫云收见旧津", "一灯隔水待归人", "柴门未启梅先觉", "暗递清香到客巾"), "灯守旧津，梅迎未至，取旧缘未散、归有所依之意。久别之人、搁置之约，皆有重续的余地。此时宜修旧好、续前缘；所得不只在远行的行囊，也在有人为你留住的一盏灯。"));
        int[][] october = {{17,10},{18,22},{19,8},{20,32},{21,4},{22,18},{24,26},{25,2},{26,12},{27,34},{28,6},{29,15},{30,3},{31,24}};
        for (int[] pair : october) add(LocalDate.of(2026,10,pair[0]),pair[1]);
        add(LocalDate.of(2026,11,1),4); add(LocalDate.of(2026,11,2),17); add(TODAY,1);
    }
    Fortune fortune(int id) { return fortunes.getOrDefault(id, fortunes.get(1)); }
    private void add(LocalDate date, int id) { archive.put(date, new Entry(date,"20:26",fortune(id))); }
    static int gradeColor(String grade) {
        return switch (grade) {
            case "上上签" -> 0xffb48a41;
            case "上吉签" -> 0xff578c68;
            case "中平签" -> 0xff6a90a8;
            case "小凶签" -> 0xff9188a1;
            case "下下签" -> 0xffa27b7e;
            default -> 0xff7f9390;
        };
    }
    Title title(String id) { return titles.stream().filter(t -> t.id().equals(id)).findFirst().orElse(null); }
    void clearLive() { titles.clear();archive.clear();recentArchive.clear();calendarMonths.clear();fortunes.clear();todayRecord=null;featured=null;archiveCount=0;today=LocalDate.now();managerLoaded=false;titlesLoaded=false;fortunesLoaded=false;
        firstJoinedAt=0;previousLoginAt=0;joinCount=0;totalOnlineMillis=0;serverTime=0;packName="";packVersion="";playerUuid="";scopeId="";revisions.clear(); }
    static String string(JsonObject o,String key,String fallback) {try{return o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsString():fallback;}catch(RuntimeException e){return fallback;}}
    static long number(JsonObject o,String key,long fallback) {try{return o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsLong():fallback;}catch(RuntimeException e){return fallback;}}
    static boolean bool(JsonObject o,String key,boolean fallback) {try{return o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsBoolean():fallback;}catch(RuntimeException e){return fallback;}}
    static JsonObject object(JsonObject o,String key) {return o.has(key)&&o.get(key).isJsonObject()?o.getAsJsonObject(key):new JsonObject();}
    void manager(JsonObject root) {
        JsonObject p=object(root,"player"),s=object(root,"scope");
        playerUuid=string(p,"uuid","");firstJoinedAt=number(p,"firstJoinedAt",0);previousLoginAt=number(p,"previousLoginAt",0);
        joinCount=number(p,"joinCount",0);totalOnlineMillis=number(p,"totalOnlineMillis",0);onlineBaselineAt=System.nanoTime();serverTime=number(root,"serverTime",0);
        packName=string(s,"packName","");packVersion=string(s,"packVersion","");managerTimezone=string(s,"timezone",managerTimezone);
        if(serverTime>0&&!fortunesLoaded)today=java.time.Instant.ofEpochMilli(serverTime).atZone(profileZone()).toLocalDate();
        scopeId=string(s,"serverId","")+"\n"+string(s,"archiveId","");managerLoaded=true;
        revisions.put("manager",string(root,"revision",""));
    }
    void titles(JsonObject root,boolean append) {
        List<Title> next=new ArrayList<>(append?titles:List.of());
        if(root.has("titles"))for(JsonElement value:root.getAsJsonArray("titles")) {
            JsonObject t=value.getAsJsonObject(),p=object(t,"presentation"),c=object(t,"palette");
            List<Glyph> glyphs=rich(object(t,"name"));StringBuilder name=new StringBuilder();glyphs.forEach(g->name.append(g.text()));
            String badge=string(p,"badge","leaf");if(!List.of("leaf","mountain","star","house","moon","wheat","sun","cloud","personal").contains(badge))badge="leaf";
            long acquired=number(t,"acquiredAt",0);
            String id=string(t,"id","");next.removeIf(old->old.id().equals(id));
            next.add(new Title(id,name.toString(),badge,string(t,"category",""),string(c,"rarityLabel",""),bool(t,"owned",false),
                acquired<=0?"时间未知":java.time.Instant.ofEpochMilli(acquired).atZone(profileZone()).format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd")),
                string(t,"description",""),string(t,"acquisition",string(t,"source","暂无获得方式说明")),
                0xff000000|(int)number(c,"accent",0x7b879c),0xff000000|(int)number(c,"light",0xe9edf3),
                0xff000000|(int)number(c,"rarityColor",0x7b879c),bool(t,"publicDisplay",true),glyphs,acquired));
        }
        if(!titles.equals(next)){titles.clear();titles.addAll(next);}
        displayPreferences=object(root,"displayPreferences");globalDisplay=object(root,"globalDisplay");
        titlesLoaded=true;revisions.put("titles",string(root,"revision",""));
    }
    static List<Glyph> rich(JsonObject root) {
        List<Glyph> glyphs=new ArrayList<>();
        if(root.has("segments"))for(JsonElement value:root.getAsJsonArray("segments")) {
            JsonObject segment=value.getAsJsonObject();String text=string(segment,"text","");int color=0xff000000|(int)number(segment,"color",0x26344f);
            text.codePoints().forEach(cp->glyphs.add(new Glyph(new String(Character.toChars(cp)),color,bool(segment,"bold",false),bool(segment,"italic",false),bool(segment,"underline",false),bool(segment,"strike",false))));
        }
        if(root.has("gradients"))for(JsonElement value:root.getAsJsonArray("gradients")) {
            JsonObject gradient=value.getAsJsonObject();if(!gradient.has("positions"))continue;var positions=gradient.getAsJsonArray("positions");
            int from=(int)number(gradient,"from",0),to=(int)number(gradient,"to",0);
            for(int i=0;i<positions.size();i++) {
                int index=positions.get(i).getAsInt();if(index<0||index>=glyphs.size())continue;
                double fraction=positions.size()==1?0:(double)i/(positions.size()-1);int color=0xff000000;
                for(int shift:new int[]{0,8,16})color|=(int)Math.round(((from>>shift)&255)*(1-fraction)+((to>>shift)&255)*fraction)<<shift;
                Glyph g=glyphs.get(index);glyphs.set(index,new Glyph(g.text(),color,g.bold(),g.italic(),g.underline(),g.strike()));
            }
        }
        return List.copyOf(glyphs);
    }
    java.time.ZoneId zone() {try{return java.time.ZoneId.of(timezone);}catch(RuntimeException ignored){return java.time.ZoneId.of("UTC");}}
    java.time.ZoneId profileZone() {try{return java.time.ZoneId.of(managerTimezone);}catch(RuntimeException ignored){return java.time.ZoneId.of("UTC");}}
    Entry record(JsonElement value) {
        if(value==null||value.isJsonNull()||!value.isJsonObject())return null;JsonObject r=value.getAsJsonObject(),f=object(r,"fortune");
        if(!f.has("title"))return null;List<String> poem=new ArrayList<>();
        if(f.has("poem")&&f.get("poem").isJsonArray())for(JsonElement line:f.getAsJsonArray("poem"))poem.add(line.getAsString());
        else if(f.has("poem"))poem.addAll(List.of(f.get("poem").getAsString().split("\\n")));
        LocalDate date=LocalDate.parse(string(r,"date",""));long at=number(r,"at",0);
        return new Entry(date,at<=0?"时间未知":java.time.Instant.ofEpochMilli(at).atZone(zone()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")),
            new Fortune((int)number(f,"id",0),string(f,"title",""),string(f,"grade",""),List.copyOf(poem),string(f,"interpretation","")),at);
    }
    void fortunes(JsonObject root) {
        timezone=string(root,"timezone",timezone);today=LocalDate.parse(string(root,"today",today.toString()));
        archiveCount=(int)number(root,"archiveCount",0);todayRecord=record(root.get("todayRecord"));
        String featuredDate=string(root,"featuredDate","");featured=featuredDate.isEmpty()?null:record(root.get("featuredRecord"));
        if(featured!=null&&!featured.date().toString().equals(featuredDate))featured=null;
        recentArchive.clear();
        if(!List.of("read","archived").contains(string(root,"todayState","undrawn")))todayRecord=null;
        if(root.has("recent"))for(JsonElement value:root.getAsJsonArray("recent")){Entry entry=record(value);if(entry!=null){recentArchive.put(entry.date(),entry);archive.put(entry.date(),entry);}}
        if(archiveCount==0)archive.clear();
        fortunesLoaded=true;revisions.put("fortunes",string(root,"revision",""));
    }
    void calendar(JsonObject root) {
        java.time.YearMonth month=java.time.YearMonth.parse(string(root,"month",""));
        Map<LocalDate,Entry> next=new LinkedHashMap<>();
        if(root.has("records"))for(JsonElement value:root.getAsJsonArray("records")){Entry entry=record(value);if(entry!=null){
            if(!java.time.YearMonth.from(entry.date()).equals(month))throw new IllegalArgumentException("Calendar response crossed months");
            next.put(entry.date(),entry);
        }}
        YearMonthMarker.replaceMonth(archive,month.toString());archive.putAll(next);calendarMonths.add(month);
    }
    private static class YearMonthMarker {
        static void replaceMonth(Map<LocalDate,Entry> archive,String month){archive.keySet().removeIf(date->java.time.YearMonth.from(date).toString().equals(month));}
    }
    static int titleText(Title title) {
        return switch (title.id()) {
            case "dew" -> 0xff168878; case "mountain" -> 0xff537786;
            case "star" -> 0xffa97929; case "wood" -> 0xffa36942;
            case "moon" -> 0xff7e5eb6; case "harvest" -> 0xffa68322;
            case "dawn" -> 0xffc17c43; default -> 0xff647dba;
        };
    }
}
