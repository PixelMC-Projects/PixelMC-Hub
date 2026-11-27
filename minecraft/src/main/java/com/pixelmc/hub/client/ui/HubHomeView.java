/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.ui;

import com.google.gson.JsonObject;
import com.pixelmc.hub.client.render.GlassStyle;
import com.pixelmc.hub.client.render.HubDraw;

import io.github.humbleui.skija.Matrix33;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Personal home page (classic glass & winter station) managed as a {@link HubBuiltInPage}. */
final class HubHomeView implements HubBuiltInPage {
    interface Host {
        void openPublicProfile(String playerId);

        void openMemories(String playerId, String archiveId);

        boolean updatesLoaded();

        boolean updatesEnabled();

        List<JsonObject> stationNotices();

        String stationNoticesEmptyText();

        void openStationNotice(JsonObject row);

        int fortuneState();

        String stateLabel();

        HubData.Entry todayEntry();

        int titleCount();

        int archiveCount();

        Map<LocalDate, HubData.Entry> archive();
    }

    static final int STATION_PLAYERS_PER_PAGE = 4;
    private static final DateTimeFormatter STATION_DATE =
            DateTimeFormatter.ofPattern("M 月 d 日，EEEE", Locale.CHINA);

    private final HubView ui;
    private final Host host;
    private final HubView.NavigationEntry entry;
    private final HubMotion.Navigation nav = new HubMotion.Navigation();
    private String stationArchiveId = "";
    private Map<String, Integer> stationSharedCounts = Map.of();
    private List<HubView.StationPlayer> stationPlayers = List.of();

    HubHomeView(HubView ui, Host host) {
        this.ui = ui;
        this.host = host;
        this.entry = ui.builtInEntry(0, "个人主页", "home", 0xff569dfb, 0xff1949e6);
    }

    void resetLive() {
        stationArchiveId = "";
        stationPlayers = List.of();
        stationSharedCounts = Map.of();
        ui.stationPage = 0;
    }

    void setStationArchiveId(String archiveId) {
        stationArchiveId = archiveId == null ? "" : archiveId;
    }

    void setStationSharedCounts(Map<String, Integer> counts) {
        stationSharedCounts = Map.copyOf(counts);
    }

    List<HubView.StationPlayer> stationPlayers() {
        return stationPlayers;
    }

    void setStationPlayers(List<HubView.StationPlayer> players) {
        if (stationPlayers.equals(players)) return;
        stationPlayers = List.copyOf(players);
        ui.stationPage =
                Math.min(
                        ui.stationPage,
                        Math.max(0, (stationPlayers.size() - 1) / STATION_PLAYERS_PER_PAGE));
    }

    @Override
    public int index() {
        return 0;
    }

    @Override
    public String id() {
        return "home";
    }

    @Override
    public String label() {
        return "个人主页";
    }

    @Override
    public HubView.NavigationEntry navigationEntry() {
        return entry;
    }

    @Override
    public HubMotion.Navigation navigationState() {
        return nav;
    }

    @Override
    public void onEnter(long now) {
        if (!ui.hasSnapshot("titles")) ui.request("titles", "snapshot", new JsonObject());
        if (!ui.hasSnapshot("fortunes")) ui.request("fortunes", "snapshot", new JsonObject());
        ui.tilts.remove("medallion");
    }

    @Override
    public void render(
            HubDraw draw,
            float mx,
            float my,
            long now,
            boolean dark,
            boolean winter,
            boolean reducedMotion,
            String focusId) {
        if (winter) {
            boolean previous = draw.literalColors(true);
            try {
                winterHome();
            } finally {
                draw.literalColors(previous);
            }
            return;
        }
        classicHome();
    }

    private void winterHome() {
        HubDraw d = ui.d;
        HubData data = ui.data;
        var p = d.winterPaper();
        boolean live = ui.live(), dark = ui.dark;
        long now = ui.now;
        ui.group(0, 380);
        HubHead.draw(d, 227, 119, 39, -23);
        ui.tracked(
                ui.fitTracked(ui.playerName, 680, 31, -.6f, 700, false),
                290,
                129,
                31,
                -.6f,
                p.ink(),
                700,
                false);
        HubData.Title worn = data.title(ui.equipped);
        String label = worn == null ? "暂未佩戴称号" : ui.fit(worn.name(), 325, 14, true);
        float nameWidth;
        if (worn != null && live) {
            nameWidth = ui.titleLabel(worn, 290, 157, 14, 0, false, false, 325);
        } else {
            ui.text(label, 290, 157, 14, p.pine(), true);
            nameWidth = d.textWidth(label, 14, true);
        }
        float joinX = 304 + nameWidth;
        if (worn != null && ui.privacyKnown() && !ui.visible) {
            ui.icon("eye-off", joinX, 145, 13, p.muted());
            ui.text("展示已隐藏", joinX + 18, 156, 11, p.muted(), false);
            joinX += 29 + d.textWidth("展示已隐藏", 11, false);
        }
        d.line(joinX, 144, joinX, 158, 1, p.ruling());
        String joined = live ? ui.timestamp(data.firstJoinedAt, "yyyy/MM/dd") : "2026/10/17";
        if (!joined.isBlank() && (!live || data.firstJoinedAt > 0))
            ui.text(joined + " 加入", joinX + 10, 156, 11, p.muted(), false);
        String bio = live ? ui.stationBio : "喜欢沿着河岸慢慢走，也喜欢把偶然的相遇收进旅途。";
        if (!bio.isBlank()) ui.text(ui.fit(bio, 710, 12, false), 290, 181, 12, p.muted(), false);
        ui.button(
                "profile",
                "公开资料",
                1042,
                123,
                89,
                33,
                true,
                true,
                () -> host.openPublicProfile(ui.selfPlayerId()),
                "book-user");
        ui.button(
                "memories",
                "旅程档案",
                1141,
                123,
                89,
                33,
                false,
                true,
                () -> host.openMemories(ui.selfPlayerId(), ""),
                "map");
        d.line(216, 197, 1230, 197, 1, p.ruling());
        ui.ungroup();
        ui.group(0, 380);
        d.save();
        d.clipRoundRect(216, 219, 600, 354, 15);
        ui.stock("panorama", 217, 220, 598, 352, 1);
        d.restore();
        d.strokeRoundRect(216.5f, 219.5f, 599, 353, 15, 1, p.ruling());
        ui.icon(dark ? "moon" : "sun", 242, 409, 15, 0xfffffaf0);
        ui.text(
                live ? data.today.format(STATION_DATE) : "11 月 3 日，星期二",
                264,
                421,
                12,
                0xfffffaf0,
                true);
        String day =
                live
                        ? (ui.journeyDays() > 0 ? "旅程第 " + ui.journeyDays() + " 天" : "旅程记录")
                        : "旅程第 18 天";
        ui.tracked(day, 242, 465, 32, 1.2f, 0xfffffaf0, 600, true);
        ui.text("累计在线", 235, 525, 11, p.muted(), false);
        ui.text("登录次数", 444, 525, 11, p.muted(), false);
        ui.text("上次登录", 585, 525, 11, p.muted(), false);
        d.line(431, 511, 431, 558, 1, p.ruling());
        d.line(572, 511, 572, 558, 1, p.ruling());
        boolean known = !live || data.managerLoaded;
        long ms = live ? data.onlineMillis(now) : 463320000L;
        String hrs = known ? Long.toString(ms / 3600000) : "—",
                min = known ? Long.toString(ms / 60000 % 60) : "—";
        float cursor = 235;
        ui.text(hrs, cursor, 553, 21, p.ink(), true);
        cursor += d.textWidth(hrs, 21, true) + 3;
        ui.text("小时", cursor, 553, 10, p.muted(), false);
        cursor += 25;
        ui.text(min, cursor, 553, 21, p.ink(), true);
        cursor += d.textWidth(min, 21, true) + 3;
        ui.text("分钟", cursor, 553, 10, p.muted(), false);
        String times = !live ? "36" : known ? Long.toString(data.joinCount) : "—";
        ui.text(times, 444, 553, 21, p.ink(), true);
        ui.text("次", 448 + d.textWidth(times, 21, true), 553, 10, p.muted(), false);
        String date = live ? ui.timestamp(data.previousLoginAt, "yyyy/MM/dd") : "2026/11/02";
        ui.text(date, 585, 552, 16, p.ink(), true);
        if (!live || data.previousLoginAt > 0)
            ui.text(
                    live ? ui.timestamp(data.previousLoginAt, "HH:mm:ss") : "20:26:14",
                    590 + d.textWidth(date, 16, true),
                    552,
                    11,
                    p.muted(),
                    false);
        ui.stock("stationery", 838, 219, 392, 354, 1);
        ui.icon("mail-open", 865, 250, 19, p.mailInk());
        ui.text("今日签笺", 891, 266, 17, p.ink(), true);
        boolean canRead = !live || ui.ready("fortunes");
        int fortuneState = host.fortuneState();
        HubData.Fortune f =
                live
                        ? (data.todayRecord == null ? null : data.todayRecord.fortune())
                        : data.fortune(1);
        if (canRead && fortuneState >= 2 && f != null) {
            ui.text(f.grade() + " · 第" + f.id() + "签", 865, 323, 12, p.mailInk(), true);
            ui.tracked(
                    ui.fitTracked(f.title(), 211, 27, 1.4f, 600, true),
                    865,
                    371,
                    27,
                    1.4f,
                    p.ink(),
                    600,
                    true);
            for (int i = 0; i < Math.min(2, f.poem().size()); i++)
                ui.tracked(
                        ui.fitTracked(f.poem().get(i), 210, 14, 1, 400, true),
                        865,
                        410 + i * 26,
                        14,
                        1,
                        p.muted(),
                        400,
                        true);
        } else {
            if (canRead) ui.text("今日机缘", 865, 325, 12, p.muted(), false);
            else {
                String reason = ui.moduleLabel("fortunes").replace('\n', ' ').strip();
                List<String> lines = ui.trackedLines(reason, 192, 12, 0, 400, false);
                for (int i = 0; i < Math.min(2, lines.size()); i++) {
                    String line =
                            i == 1 && lines.size() > 2
                                    ? ui.fit(lines.get(i) + "…", 192, 12, false)
                                    : lines.get(i);
                    ui.text(line, 865, 315 + i * 18, 12, p.muted(), false);
                }
            }
            ui.text(
                    canRead ? (fortuneState == 0 ? "有一签，等你开启" : "签已入手，静候解读") : "今日签笺暂不可用",
                    865,
                    374,
                    20,
                    p.ink(),
                    true);
            if (canRead)
                ui.wrap(
                        fortuneState == 0 ? "拿起签筒，为今天留下一份期许。" : "带上未解的签，前往解签龛。",
                        865,
                        408,
                        194,
                        13,
                        22,
                        p.muted(),
                        false);
        }
        ui.icon(canRead && fortuneState == 3 ? "check-check" : "info", 865, 520, 15, p.muted());
        ui.text(canRead ? host.stateLabel() : "暂不可用", 886, 533, 11, p.muted(), false);
        if (canRead)
            ui.button(
                    "today-read",
                    fortuneState >= 2 ? "查看签文" : fortuneState == 0 ? "如何抽签" : "如何解签",
                    1108,
                    509,
                    95,
                    37,
                    true,
                    canRead,
                    () -> {
                        if (fortuneState >= 2) ui.openReading(host.todayEntry());
                        else ui.openOverlay("fortune-help");
                    },
                    fortuneState >= 2 ? "mail-open" : "arrow-right");
        winterNews();
        ui.ungroup();
        ui.textRight("统计范围：当前存档", 1230, 827, 10, p.muted(), false);
    }

    private void winterNews() {
        HubDraw d = ui.d;
        var p = d.winterPaper();
        boolean dark = ui.dark;
        boolean showUpdates = !host.updatesLoaded() || host.updatesEnabled();
        float x = showUpdates ? 838 : 216, w = showUpdates ? 392 : 1014;
        if (showUpdates) {
            ui.stock("bulletin", 216, 587, 600, 208, 1);
            ui.icon("newspaper", 235, 606, 17, p.pine());
            ui.text("最近更新", 261, 622, 17, p.ink(), true);
            float allHover = ui.extensionHover("station-all", ui.inside(716, 599, 81, 28));
            ui.textCenter("全部更新 ↗", 756.5f, 617.5f, 11, p.pine(), false);
            if (allHover > 0) {
                float allWidth = d.textWidth("全部更新 ↗", 11, false);
                float allLeft = 756.5f - allWidth / 2;
                d.line(
                        allLeft,
                        620.5f,
                        allLeft + allWidth,
                        620.5f,
                        1,
                        HubView.withAlpha(p.pine(), allHover));
            }
            ui.hit("station-all", "全部更新 ↗", 716, 599, 81, 28, () -> ui.navigate(3));
            List<JsonObject> rows = host.stationNotices();
            if (rows.isEmpty())
                ui.text(
                        ui.fit(host.stationNoticesEmptyText().replace('\n', ' '), 562, 12, false),
                        235,
                        666,
                        12,
                        p.muted(),
                        false);
            for (int i = 0; i < Math.min(2, rows.size()); i++) {
                JsonObject row = rows.get(i);
                float y = 635 + 71 * i;
                if (i > 0) d.line(235, y, 797, y, 1, p.ruling());
                float rowHover =
                        ui.extensionHover("station-notice-" + i, ui.inside(235, y, 562, 71));
                String title = ui.fit(HubData.string(row, "title", ""), 425, 14, true);
                ui.text(title, 235, y + 23, 14, p.ink(), true);
                if (rowHover > 0)
                    d.line(
                            235,
                            y + 26,
                            235 + d.textWidth(title, 14, true),
                            y + 26,
                            1,
                            HubView.withAlpha(p.ink(), rowHover));
                String published = HubData.string(row, "publishedAt", "");
                if (!published.isBlank())
                    ui.text(
                            stationShortDate(published),
                            245 + d.textWidth(title, 14, true),
                            y + 23,
                            11,
                            p.muted(),
                            false);
                ui.text(
                        ui.fit(HubData.string(row, "summary", ""), 532, 11, false),
                        235,
                        y + 41,
                        11,
                        p.muted(),
                        false);
                String version = HubData.string(row, "updateVersion", "");
                ui.text(
                        HubData.string(row, "category", "")
                                + (version.isBlank() ? "" : " · v" + version),
                        235,
                        y + 59,
                        10,
                        p.mailInk(),
                        false);
                ui.icon("chevron-right", 782, y + 26, 14, p.muted());
                ui.hit(
                        "station-notice-" + i,
                        title,
                        235,
                        y,
                        562,
                        71,
                        () -> {
                            ui.navigate(3);
                            host.openStationNotice(row);
                        });
            }
        }
        ui.stock("company", x, 587, w, 208, 1);
        ui.icon("users", x + 19, 607, 17, p.pine());
        ui.text("在线玩家", x + 46, 622, 17, p.ink(), true);
        String count = Integer.toString(stationPlayers.size());
        float countW = d.textWidth(count, 11, true);
        d.roundRect(x + 116, 608, countW + 12, 17, 8, p.sheet());
        ui.text(count, x + 122, 621, 11, p.pine(), true);
        int labelBg = dark ? 0xff414943 : 0xffe8e4cd;
        int pages = 1 + Math.max(0, stationPlayers.size() - 1) / STATION_PLAYERS_PER_PAGE;
        if (pages > 1) {
            stationPagerButton(
                    "station-prev",
                    "上一页在线玩家",
                    "chevron-left",
                    x + w - 107,
                    601,
                    ui.stationPage > 0,
                    labelBg,
                    () -> ui.stationPage--);
            ui.textCenter(
                    (ui.stationPage + 1) + " / " + pages, x + w - 66, 617, 10, p.muted(), false);
            stationPagerButton(
                    "station-next",
                    "下一页在线玩家",
                    "chevron-right",
                    x + w - 44,
                    601,
                    ui.stationPage + 1 < pages,
                    labelBg,
                    () -> ui.stationPage++);
        }
        if (stationPlayers.isEmpty()) ui.text("暂时没有其他玩家在线", x + 19, 666, 12, p.muted(), false);
        int first = ui.stationPage * STATION_PLAYERS_PER_PAGE;
        for (int i = 0; i < STATION_PLAYERS_PER_PAGE && first + i < stationPlayers.size(); i++) {
            HubView.StationPlayer player = stationPlayers.get(first + i);
            float width = (w - 46) / 2,
                    px = x + 19 + (i % 2) * (width + 8),
                    py = 636 + (i / 2) * 72,
                    cardY = py + 9,
                    cardH = 52;
            float peerHover =
                    ui.extensionHover(
                            "station-peer-" + player.id(), ui.inside(px, cardY, width, cardH));
            if (peerHover > 0)
                d.roundRect(px, cardY, width, cardH, 10, HubView.withAlpha(labelBg, peerHover));
            d.save();
            d.clipRoundRect(px + 10, cardY + 10, 32, 32, 8);
            HubHead.drawPlayer2D(d, player.id(), false, px + 10, cardY + 10, 32);
            d.restore();
            d.circle(px + 41, cardY + 41, 4, p.sheet());
            d.circle(px + 41, cardY + 41, 2.5f, 0xff679476);
            ui.text(
                    ui.fit(player.name(), width - 60, 12, true),
                    px + 51,
                    cardY + 23,
                    12,
                    p.ink(),
                    true);
            Integer shared = stationSharedCounts.get(player.id());
            ui.text(
                    shared == null ? "当前存档" : ("共同存档 · " + shared + " 个"),
                    px + 51,
                    cardY + 40,
                    10,
                    p.muted(),
                    false);
            ui.hit(
                    "station-peer-" + player.id(),
                    "查看 " + player.name() + " 的公开资料",
                    px,
                    cardY,
                    width,
                    cardH,
                    () -> host.openPublicProfile(player.id()));
        }
    }

    private void stationPagerButton(
            String id,
            String label,
            String glyph,
            float x,
            float y,
            boolean enabled,
            int labelBg,
            Runnable click) {
        HubDraw d = ui.d;
        var p = d.winterPaper();
        float hover = ui.extensionHover(id, enabled && ui.inside(x, y, 24, 24));
        if (hover > 0) d.roundRect(x, y, 24, 24, 6, HubView.withAlpha(labelBg, hover));
        d.strokeRoundRect(
                x + .5f, y + .5f, 23, 23, 6, 1, HubView.withAlpha(p.ruling(), enabled ? 1 : .35f));
        ui.icon(glyph, x + 4, y + 4, 16, HubView.withAlpha(p.pine(), enabled ? 1 : .35f));
        if (enabled) ui.hit(id, label, x, y, 24, 24, click);
    }

    private String stationShortDate(String published) {
        try {
            return ui.timestamp(Long.parseLong(published), "MM.dd");
        } catch (NumberFormatException ignored) {
            return published.length() >= 10 ? published.substring(5, 10).replace('-', '.') : "";
        }
    }

    private void classicHome() {
        HubDraw d = ui.d;
        HubData data = ui.data;
        boolean live = ui.live(), dark = ui.dark;
        ui.heading(
                "个人主页",
                live
                        ? (ui.journeyDays() > 0
                                ? "很高兴又见到你，今天是旅程的第 " + ui.journeyDays() + " 天。"
                                : "很高兴又见到你。")
                        : "很高兴又见到你，今天是旅程的第 18 天。",
                null,
                -1);
        ui.group(0, 340);
        ui.icon("sun", 1062, 88, 18, 0xffbf8a37);
        ui.text(
                live
                        ? data.today.format(
                                DateTimeFormatter.ofPattern("M 月 d 日，EEEE", Locale.CHINA))
                        : "11 月 3 日，星期二",
                1089,
                103,
                12,
                0xff6e7990,
                false);
        ui.ungroup();
        ui.group(50, 440);
        d.boxShadow(
                HubView.LEFT,
                174,
                HubView.WIDTH,
                276,
                22,
                0,
                10,
                dark ? 28 : 30,
                dark ? -10 : 0,
                dark ? 0x700a102c : 0x288980c5);
        d.linearGradientRoundRect(
                HubView.LEFT,
                174,
                HubView.WIDTH,
                276,
                22,
                110,
                new int[] {0xffd9f0ff, 0xffe4e8ff, 0xffdbc9f9},
                new float[] {0, .45f, 1});
        d.radialGradientRoundRect(
                HubView.LEFT,
                174,
                HubView.WIDTH,
                276,
                22,
                HubView.LEFT + HubView.WIDTH * .78f,
                174 + 276 * .34f,
                HubView.WIDTH * .78f * 1.41421356f,
                276 * .66f * 1.41421356f,
                new int[] {0xffbcbbff, 0x00bcbbff},
                new float[] {0, .5f});
        d.captureGlassBackdrop(HubView.LEFT, 174, HubView.WIDTH, 276);
        d.glassSurface(HubView.LEFT, 174, HubView.WIDTH, 276, 22, GlassStyle.IDENTITY);
        d.insetBoxShadow(
                HubView.LEFT,
                174,
                HubView.WIDTH,
                276,
                22,
                0,
                1,
                dark ? 0 : 2,
                0,
                dark ? 0x1ae8efff : 0xffffffff);
        d.insetBoxShadow(
                HubView.LEFT,
                174,
                HubView.WIDTH,
                276,
                22,
                0,
                dark ? -1 : 0,
                dark ? 0 : 5,
                0,
                dark ? 0x0acbd3ff : 0x298c9ac3);
        if (!dark) d.boxShadow(HubView.LEFT, 174, HubView.WIDTH, 276, 22, 0, 8, 22, 0, 0x136c64aa);
        d.save();
        d.clipRoundRect(HubView.LEFT, 174, HubView.WIDTH, 276, 22);
        d.rect(HubView.LEFT, 366, HubView.WIDTH, 84, ui.a(0x9effffff));
        d.line(HubView.LEFT, 366, 1232, 366, 1, ui.a(0xc7ffffff));
        d.restore();
        if (dark) {
            boolean previousColors = d.literalColors(true);
            d.strokeRoundRect(
                    HubView.LEFT + .5f, 174.5f, HubView.WIDTH - 1, 275, 22, 1, ui.a(0x26c3cef6));
            d.line(HubView.LEFT + 22, 174.5f, 1232 - 22, 174.5f, 1, ui.a(0x3dd3e4ff));
            d.line(HubView.LEFT + 22, 449.5f, 1232 - 22, 449.5f, 1, ui.a(0x2ba3a4d3));
            d.literalColors(previousColors);
        } else d.strokeRoundRect(HubView.LEFT, 174, HubView.WIDTH, 276, 22, 1, ui.a(0xf2ffffff));
        ui.text("你好", 263, 211, 12, 0xff586987, false);
        String name = ui.fitTracked(ui.playerName, 390, 35, -1, 700, false);
        ui.tracked(name, 263, 255, 35, -1, 0xff253657, 700, false);
        float nameWidth = ui.trackedWidth(name, 35, -1, 700, false);
        ui.pill(live ? "我的角色" : "本地角色", 277 + nameWidth, 232, 0xbceffff2, 0xff25816a, true);
        HubData.Title worn = data.title(ui.equipped);
        if (worn == null)
            ui.text(
                    live && !ui.ready("titles")
                            ? "称号资料未连接"
                            : live && !ui.catalogueComplete ? "正在读取称号…" : "暂未佩戴称号",
                    263,
                    287,
                    14,
                    HubView.MUTED,
                    true);
        else ui.titleLabel(worn, 263, 287, 14, 1.4f, false, false);
        if (ui.privacyKnown() && !ui.visible && worn != null) {
            ui.icon("eye-off", live ? 750 : 371, 276, 13, HubView.MUTED);
            ui.text("展示已隐藏", live ? 769 : 390, 287, 10, HubView.MUTED, false);
        }
        ui.button(
                "profile",
                "公开资料",
                263,
                315,
                106,
                31,
                false,
                true,
                () -> host.openPublicProfile(ui.selfPlayerId()),
                "arrow-up-right");
        ui.button(
                "memories",
                "旅程档案",
                379,
                315,
                106,
                31,
                false,
                true,
                () -> host.openMemories(ui.selfPlayerId(), ""),
                "bookmark");
        d.save();
        d.translate(1080, 268);
        d.canvas().rotate(-29);
        d.strokeRoundRect(-120, -57, 240, 114, 57, 2, ui.a(0x70ffffff));
        d.restore();
        ui.push(Matrix33.makeRotate(-5, 1082, 267));
        HubMotion.Tilt medallion =
                ui.beginTilt("medallion", 987, 187, 190, 160, 10, 1.04f, true, .16f, 26);
        ui.glass(987, 187, 190, 160, 26, 0x66ffffff, 0x24e7e5ff);
        HubHead.draw(d, 1055, 214, 54, -23, -2);
        String journeyLabel =
                live
                        ? (ui.journeyDays() > 0 ? "旅程第 " + ui.journeyDays() + " 天" : "今天也有新旅程")
                        : "旅程第 18 天";
        float journeyWidth = d.textWidthTracked(journeyLabel, 10, 400, 1, false) + 36,
                journeyX = 1082 - journeyWidth / 2;
        boolean badgeColors = d.literalColors(true);
        d.roundRect(journeyX, 309, journeyWidth, 23, 12, ui.a(dark ? 0x14b3a9d5 : 0x6bffffff));
        d.strokeRoundRect(
                journeyX + .5f,
                309.5f,
                journeyWidth - 1,
                22,
                12,
                1,
                ui.a(dark ? 0x24dacfff : 0xaaffffff));
        int journeyColor = dark ? 0xffd0c7ed : 0xff5d5491;
        ui.icon("sparkles", journeyX + 9, 314, 13, journeyColor);
        ui.tracked(journeyLabel, journeyX + 27, 324.5f, 10, 1, journeyColor, 400, false);
        d.literalColors(badgeColors);
        ui.endTilt(medallion, 987, 187, 190, 160, 26, 10, .75f);
        ui.pop();
        ui.text("✦", 1190, 222, 27, 0xffffffff, false);
        ui.text("✧", 967, 321, 21, 0xffffffff, false);
        onlineStat(263, 391);
        d.line(566, 388, 566, 430, 1, ui.a(0x4ab7c1e0));
        stat(
                590,
                391,
                "log-in",
                live ? "当前存档 · 登录次数" : "登录次数 · 演示",
                live ? (data.managerLoaded ? Long.toString(data.joinCount) : "—") : "36",
                "次",
                0xffaa75c6,
                0xfff1e9ff);
        d.line(887, 388, 887, 430, 1, ui.a(0x4ab7c1e0));
        ui.glass(910, 392, 33, 33, 11, 0xfff8f0df, 0xfff8f0df);
        ui.icon("calendar-days", 918, 400, 17, 0xffb29047);
        ui.text(live ? "上次登录" : "上次登录 · 演示", 955, 400, 11, 0xff72809c, false);
        ui.text(
                live ? ui.timestamp(data.previousLoginAt, "yyyy/MM/dd") : "2026/11/02",
                955,
                424,
                16,
                0xff344566,
                true);
        ui.text(
                live
                        ? (data.previousLoginAt <= 0
                                ? ""
                                : ui.timestamp(data.previousLoginAt, "HH:mm:ss"))
                        : "20:26:14",
                1060,
                424,
                12,
                0xff71809b,
                false);
        ui.ungroup();
        ui.group(110, 440);
        homeToday();
        homeKeepsakes();
        ui.ungroup();
        ui.group(170, 340);
        ui.icon("leaf", HubView.LEFT, 811, 13, 0xff8a93a8);
        ui.text("每一份收藏，都记得来时的路。", 256, 823, 11, 0xff8a93a8, false);
        ui.textRight(
                live
                        ? (ui.ready("manager") ? "当前存档的个人资料" : ui.moduleLabel("manager"))
                        : "样例统计与收藏 · F8 预览选项",
                1232,
                823,
                10,
                0xff8a93a8,
                false);
        ui.ungroup();
    }

    private void stat(
            float x,
            float y,
            String name,
            String label,
            String main,
            String unit,
            int color,
            int background) {
        HubDraw d = ui.d;
        d.roundRect(x, y, 33, 33, 11, ui.a(background));
        ui.icon(name, x + 8, y + 8, 17, color);
        ui.text(label, x + 45, y + 9, 11, 0xff72809c, false);
        ui.text(main, x + 45, y + 34, 21, 0xff344566, true);
        ui.text(unit, x + 49 + d.textWidth(main, 21, 600), y + 33, 10, 0xff78869d, false);
    }

    private void onlineStat(float x, float y) {
        HubDraw d = ui.d;
        HubData data = ui.data;
        boolean live = ui.live();
        d.roundRect(x, y, 33, 33, 11, ui.a(0xffe8eeff));
        ui.icon("clock", x + 8, y + 8, 17, 0xff6b7dd6);
        ui.text(live ? "当前存档 · 累计在线" : "累计在线 · 演示", x + 45, y + 9, 11, 0xff72809c, false);
        boolean known = !live || data.managerLoaded;
        long millis = live ? data.onlineMillis(ui.now) : ((128L * 60 + 42) * 60_000);
        String hours = known ? Long.toString(millis / 3_600_000) : "—",
                minutes = known ? Long.toString(millis / 60_000 % 60) : "—";
        float cursor = x + 45;
        ui.text(hours, cursor, y + 34, 21, 0xff344566, true);
        cursor += d.textWidth(hours, 21, 600) + 4;
        ui.text("小时", cursor, y + 33, 10, 0xff78869d, false);
        cursor += d.textWidth("小时", 10, 400) + 4;
        ui.text(minutes, cursor, y + 34, 21, 0xff344566, true);
        cursor += d.textWidth(minutes, 21, 600) + 4;
        ui.text("分钟", cursor, y + 33, 10, 0xff78869d, false);
    }

    private void homeToday() {
        HubDraw d = ui.d;
        HubData data = ui.data;
        boolean live = ui.live();
        int fortuneState = host.fortuneState();
        ui.shadow(HubView.LEFT, 470, 515, 310, 22, 0x104b5d85);
        d.linearGradientRoundRect(
                HubView.LEFT,
                470,
                515,
                310,
                22,
                120,
                new int[] {0xfffffefa, 0xfffff9ef},
                new float[] {0, 1});
        d.radialGradientRoundRect(
                HubView.LEFT,
                470,
                515,
                310,
                22,
                751,
                470 + 310 * .42f,
                515 * 1.41421356f,
                310 * .58f * 1.41421356f,
                new int[] {0x9cffe9bf, 0x00ffe9bf},
                new float[] {0, .65f});
        d.strokeRoundRect(HubView.LEFT + .5f, 470.5f, 514, 309, 22, 1, ui.a(0xe9ffffff));
        d.line(HubView.LEFT + 22, 471, 729, 471, 1, ui.a(0xd9ffffff));
        ui.icon("leaf", 259, 493, 19, 0xffbc9345);
        ui.text("今日签笺", 287, 510, 16, HubView.INK, true);
        ui.textRight("每日一签", 728, 508, 10, HubView.MUTED, false);
        HubData.Fortune f =
                live
                        ? (data.todayRecord == null ? null : data.todayRecord.fortune())
                        : data.fortune(1);
        if (live && !ui.ready("fortunes")) {
            ui.text("今日签笺暂不可用", 259, 593, 23, 0xff4e4f42, true);
            ui.wrap(ui.moduleLabel("fortunes"), 259, 630, 280, 12, 23, 0xff928675, false);
        } else if (fortuneState >= 2 && f != null) {
            ui.text(
                    f.grade() + "  ·  第" + f.id() + "签",
                    259,
                    551,
                    11,
                    HubData.gradeColor(f.grade()),
                    false);
            ui.tracked(f.title(), 259, 597, 29, 2, 0xff4e4f42, 600, true);
            if (!f.poem().isEmpty())
                ui.tracked(f.poem().get(0), 259, 630, 13, 1.3f, 0xff828069, 400, true);
            if (f.poem().size() > 1)
                ui.tracked(f.poem().get(1), 259, 655, 13, 1.3f, 0xff828069, 400, true);
        } else {
            ui.text("今日机缘", 259, 551, 11, 0xffae8132, false);
            ui.text(fortuneState == 0 ? "有一签，等你开启" : "签已入手，静候解读", 259, 593, 24, 0xff4e4f42, true);
            ui.wrap(
                    fortuneState == 0 ? "拿起签筒，为今天留下一份期许。" : "带上未解的签，前往解签龛。",
                    259,
                    629,
                    260,
                    13,
                    23,
                    0xff928675,
                    false);
        }
        d.circle(662, 606, 61, ui.a(0x30ebcf87));
        float barrel =
                ui.transition(
                        "fortune-barrel-hover",
                        ui.inside(HubView.LEFT, 470, 515, 310) ? 1 : 0,
                        350);
        if (ui.reducedMotion) barrel = 0;
        ui.push(HubDraw.cssPerspective(661, 611, 800, 0, 0, -8 + 5 * barrel, 1, 0, -4 * barrel));
        ui.image("fortune-barrel", 598, 541, 126, 140, 1);
        ui.pop();
        ui.text("✦", 716, 556, 12, 0xffd5ae61, false);
        ui.text("✧", 586, 675, 16, 0xffd5ae61, false);
        d.line(259, 706, 728, 706, 1, ui.a(0x33cabc9b));
        ui.icon(fortuneState == 3 ? "check-check" : "info", 259, 731, 15, 0xff7f8e81);
        ui.text(host.stateLabel(), 281, 743, 10, 0xff7f8e81, false);
        ui.goldButton(
                "today-read",
                fortuneState >= 2 ? "查看签文  →" : fortuneState == 0 ? "如何抽签  →" : "如何解签  →",
                608,
                721,
                120,
                37,
                () -> {
                    if (live && !ui.ready("fortunes")) {
                        ui.notifyUser(ui.moduleLabel("fortunes"));
                        return;
                    }
                    if (fortuneState >= 2) ui.openReading(host.todayEntry());
                    else ui.openOverlay("fortune-help");
                });
    }

    private void homeKeepsakes() {
        HubDraw d = ui.d;
        HubData data = ui.data;
        boolean live = ui.live();
        ui.glass(771, 470, 461, 310, 22, 0xecffffff, 0xabffffff);
        ui.icon("bookmark", 793, 493, 18, 0xff8c74c4);
        ui.text("最近收藏", 820, 510, 16, HubView.INK, true);
        ui.textRight("旅途的印记", 1210, 508, 10, HubView.MUTED, false);
        List<HubData.Keepsake> recent =
                data.recentKeepsakes(live ? data.recentArchive : host.archive());
        for (int i = 0; i < recent.size(); i++) {
            HubData.Keepsake item = recent.get(i);
            float y = 537 + i * 64;
            if (item.title() != null) {
                HubData.Title t = item.title();
                keepsake(
                        y,
                        ui.fit(t.name(), 280, 13, false),
                        "获得称号 · " + ui.titleDate(t),
                        t.icon(),
                        t.light(),
                        t.color(),
                        null,
                        () -> {
                            ui.selectedTitle = t.id();
                            ui.navigate(1);
                        },
                        t);
            } else {
                HubData.Entry e = item.entry();
                keepsake(
                        y,
                        e.fortune().title(),
                        "收藏签笺 · " + HubView.shortDate(e.date()) + " " + e.time(),
                        "fortune-slip",
                        0xfff8edda,
                        HubData.gradeColor(e.fortune().grade()),
                        e.fortune().grade(),
                        () -> ui.openReading(e));
            }
        }
        if (recent.isEmpty()) {
            boolean loading = live && (!data.titlesLoaded || !data.fortunesLoaded);
            String empty = loading ? "正在读取收藏…" : "还没有留下收藏";
            if (live && (!ui.ready("titles") || !ui.ready("fortunes"))) empty = "收藏资料尚未就绪";
            ui.textCenter(empty, 1001, 619, 14, HubView.MUTED, false);
        }
        d.line(793, 731, 1210, 731, 1, ui.a(0xffe4e5f1));
        ui.text(
                live && !ui.catalogueComplete ? "—" : Integer.toString(host.titleCount()),
                793,
                760,
                17,
                0xff5c5790,
                true);
        ui.text("枚称号", 810, 760, 10, 0xff80899f, false);
        d.line(866, 746, 866, 762, 1, ui.a(0xffd9dcec));
        ui.text(
                live && !data.fortunesLoaded ? "—" : Integer.toString(host.archiveCount()),
                884,
                760,
                17,
                0xff5c5790,
                true);
        ui.text("张签笺", 911, 760, 10, 0xff80899f, false);
        ui.iconButton(
                "archive-link",
                "打开签笺月历",
                "arrow-up-right",
                1177,
                740,
                30,
                () -> ui.navigate(2),
                true);
    }

    private void keepsake(
            float y,
            String title,
            String subtitle,
            String art,
            int bg,
            int color,
            String grade,
            Runnable click) {
        keepsake(y, title, subtitle, art, bg, color, grade, click, null);
    }

    private void keepsake(
            float y,
            String title,
            String subtitle,
            String art,
            int bg,
            int color,
            String grade,
            Runnable click,
            HubData.Title style) {
        HubDraw d = ui.d;
        boolean dark = ui.dark;
        float hover =
                ui.transition("keepsake-hover-" + y, ui.inside(793, y - 1, 417, 60) ? 1 : 0, 140);
        if (hover > 0) d.roundRect(793, y - 1, 417, 60, 0, HubView.withAlpha(0x80f5f2ff, hover));
        float iconY = y + 6.5f;
        boolean previousColors = d.literalColors(true);
        d.boxShadow(793, iconY, 36, 36, 12, 0, 3, 8, 0, ui.a(dark ? 0x220b1122 : 0x187e708b));
        if (art.equals("fortune-slip"))
            d.roundRect(793, iconY, 36, 36, 12, ui.a(dark ? 0x24bd9c57 : 0xfff8edda));
        else
            d.linearGradientRoundRect(
                    793,
                    iconY,
                    36,
                    36,
                    12,
                    135,
                    dark
                            ? new int[] {
                                HubView.withAlpha(color, .21f), HubView.withAlpha(color, .12f)
                            }
                            : new int[] {bg, HubView.lighten(bg, .1f)},
                    new float[] {0, 1});
        d.insetBoxShadow(793, iconY, 36, 36, 12, 1, 1, 1, 0, ui.a(dark ? 0x10eee3c7 : 0xffffffff));
        d.strokeRoundRect(793.5f, iconY + .5f, 35, 35, 12, 1, ui.a(dark ? 0x21d9c498 : 0xffffffff));
        if (art.equals("fortune-slip")) {
            var artwork = d.imageResource("textures/ui/fortune-slip.png");
            float scale = 32f / Math.max(artwork.getWidth(), artwork.getHeight());
            float imageWidth = artwork.getWidth() * scale,
                    imageHeight = artwork.getHeight() * scale;
            ui.image(
                    art,
                    793 + (36 - imageWidth) / 2,
                    iconY + (36 - imageHeight) / 2,
                    imageWidth,
                    imageHeight,
                    1);
        } else
            ui.icon(art, 800, iconY + 7, 22, dark ? HubView.mix(color, 0xffe8eeff, .35f) : color);
        d.literalColors(previousColors);
        if (style == null) ui.text(title, 841, y + 19, 13, 0xff4c5570, false);
        else ui.titleLabel(style, 841, y + 19, 13, 0, false, false, 340);
        if (grade != null) {
            float x = 848 + d.textWidth(title, 13, false);
            d.strokeRoundRect(
                    x, y + 6, d.textWidth(grade, 9, false) + 10, 17, 4, .7f, ui.a(0xffeadfc7));
            ui.text(grade, x + 5, y + 18, 9, color, false);
        }
        ui.text(subtitle, 841, y + 40, 10, 0xff8490a5, false);
        ui.icon("chevron-right", 1195, y + 19, 16, 0xffa5a9c0);
        if (y < 665) d.line(793, y + 59, 1210, y + 59, 1, ui.a(0x57e5e7f3));
        ui.hit("keepsake-" + y, title, 793, y - 1, 417, 60, click);
    }
}
