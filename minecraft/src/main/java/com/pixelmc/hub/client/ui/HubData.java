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

/** Fixed, intentionally local sample data shared by all native prototype pages. */
final class HubData {
    static final LocalDate TODAY = LocalDate.of(2026, 11, 3);
    record Title(String id, String name, String icon, String category, String rarity,
                 boolean owned, String date, String description, String condition, int color, int light) {}
    record Fortune(int id, String title, String grade, List<String> poem, String interpretation) {}
    record Entry(LocalDate date, String time, Fortune fortune) {}

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
    static Title title(String id) { return TITLES.stream().filter(t -> t.id().equals(id)).findFirst().orElse(null); }
    static int titleText(Title title) {
        return switch (title.id()) {
            case "dew" -> 0xff168878; case "mountain" -> 0xff537786;
            case "star" -> 0xffa97929; case "wood" -> 0xffa36942;
            case "moon" -> 0xff7e5eb6; case "harvest" -> 0xffa68322;
            case "dawn" -> 0xffc17c43; default -> 0xff647dba;
        };
    }
}
