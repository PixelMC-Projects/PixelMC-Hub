/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.ui;

import com.google.gson.JsonObject;
import com.pixelmc.hub.client.render.HubDraw;

import io.github.humbleui.skija.Matrix33;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Fortunes calendar, collection counter, and reading dialogs managed as a {@link HubBuiltInPage}. */
final class HubFortunesView implements HubBuiltInPage {
    interface Host {
        long modalAt();

        void setModalBounds(float x, float y, float w, float h);

        void showReading(HubData.Entry value);
    }

    private static final float COUNTER_BAR = 295,
            DESK_TOP = 331,
            DESK_END = 777.75f,
            COUNTER_SPLIT = HubTitlesView.COLLECTION_LEFT + HubTitlesView.COLLECTION_WIDTH * .58f;
    private static final float MONTH_X = HubTitlesView.COLLECTION_LEFT + 33.5f,
            MONTH_RIGHT = COUNTER_SPLIT - 34.55f,
            MONTH_GRID = 446.1f,
            MONTH_END = 764;
    private static final String[] WINTER_MONTHS = {
        "一月", "二月", "三月", "四月", "五月", "六月", "七月", "八月", "九月", "十月", "十一月", "十二月"
    };

    private final HubView ui;
    private final Host host;
    private final HubView.NavigationEntry entry;
    private final HubMotion.Navigation nav = new HubMotion.Navigation();

    private int fortuneState = 3;
    private int monthDirection = 1;
    private long monthAt = System.nanoTime();
    private float historyScroll;
    private boolean history, calendarLoading, chooseLatestOnCalendar;
    private LocalDate displayDate;
    private LocalDate unfoldedDate;
    private long unfoldedAt;
    private float readingScroll, readingOverflow;

    HubFortunesView(HubView ui, Host host) {
        this.ui = ui;
        this.host = host;
        this.displayDate = ui.data.today;
        this.entry = ui.builtInEntry(2, "我的签笺", "book-open", 0xffffb83d, 0xfff8651b);
    }

    void resetLive() {
        displayDate = null;
        fortuneState = 0;
        calendarLoading = true;
    }

    void resetDemo() {
        fortuneState = 3;
        displayDate = ui.data.today;
    }

    void resetReadingScroll() {
        readingScroll = 0;
    }

    int fortuneState() {
        return fortuneState;
    }

    void setFortuneState(int state) {
        fortuneState = state;
    }

    String stateLabel() {
        return List.of("尚未抽签", "等待解签", "已经解签", "已收进藏签匣").get(fortuneState);
    }

    int archiveCount() {
        return ui.live() ? ui.data.archiveCount : archive().size();
    }

    Map<LocalDate, HubData.Entry> archive() {
        HubData data = ui.data;
        if ((ui.live() && !ui.winter) || fortuneState == 3 || !data.archive.containsKey(data.today))
            return data.archive;
        Map<LocalDate, HubData.Entry> copy = new LinkedHashMap<>(data.archive);
        copy.remove(data.today);
        return copy;
    }

    HubData.Entry todayEntry() {
        HubData data = ui.data;
        return ui.live()
                ? data.todayRecord
                : new HubData.Entry(data.today, "20:26", data.fortune(1));
    }

    void calendarRequest() {
        if (ui.live() && ui.ready("fortunes")) {
            calendarLoading = true;
            JsonObject p = new JsonObject();
            p.addProperty("month", ui.month.toString());
            ui.request("fortunes", "calendar", p);
        }
    }

    boolean calendarInitialLoading() {
        return ui.live() && calendarLoading && !ui.data.calendarMonths.contains(ui.month);
    }

    void acceptLive(String action, JsonObject result) {
        HubData data = ui.data;
        if (action.equals("calendar")) {
            data.calendar(result);
            YearMonth month = ui.month;
            if (month.toString().equals(HubData.string(result, "month", ""))) {
                calendarLoading = false;
                if (chooseLatestOnCalendar)
                    ui.selectedDate =
                            archive().keySet().stream()
                                    .filter(date -> YearMonth.from(date).equals(month))
                                    .max(Comparator.naturalOrder())
                                    .orElse(month.atDay(1));
                chooseLatestOnCalendar = false;
            }
            HubData.Entry reading = ui.reading;
            if (reading != null
                    && YearMonth.from(reading.date())
                            .toString()
                            .equals(HubData.string(result, "month", ""))) {
                HubData.Entry updated = data.archive.get(reading.date());
                if (ui.winter && reading.date().equals(data.today) && fortuneState == 2)
                    updated = data.todayRecord;
                if (updated == null) ui.dismissOverlay();
                else ui.reading = updated;
            }
        } else if (action.equals("read")) {
            HubData.Entry record =
                    data.record(result.has("record") ? result.get("record") : result);
            if (record != null) host.showReading(record);
        } else {
            boolean initial = !data.fortunesLoaded;
            data.fortunes(result);
            calendarLoading = true;
            fortuneState =
                    List.of("undrawn", "unread", "read", "archived")
                            .indexOf(HubData.string(result, "todayState", "undrawn"));
            if (fortuneState < 0) fortuneState = 0;
            if (initial) {
                ui.month = YearMonth.from(data.today);
                ui.selectedDate = data.today;
            }
            displayDate = data.featured == null ? null : data.featured.date();
        }
    }

    @Override
    public int index() {
        return 2;
    }

    @Override
    public String id() {
        return "fortunes";
    }

    @Override
    public String label() {
        return "我的签笺";
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
        calendarRequest();
        monthAt = now;
        unfoldedDate = null;
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
        fortunes();
    }

    @Override
    public boolean scroll(float pageX, float pageY, double amount) {
        if (ui.winter) {
            if (history
                    && pageX >= MONTH_X
                    && pageX < MONTH_RIGHT
                    && pageY >= MONTH_GRID
                    && pageY < MONTH_END) {
                historyScroll -= (float) (amount * 42);
                return true;
            }
            return false;
        }
        if (history && pageX >= 254 && pageX <= 898 && pageY >= 252 && pageY <= 746) {
            historyScroll -= (float) (amount * 48);
            return true;
        }
        return false;
    }

    void scrollReading(double amount) {
        readingScroll = HubView.clamp(readingScroll - (float) amount * 46, 0, readingOverflow);
    }

    boolean keyScrollReading(int keyCode, float modalH) {
        if (!(ui.winter && ui.reading != null && readingOverflow > 0)) return false;
        switch (keyCode) {
            case 264 -> readingScroll = Math.min(readingOverflow, readingScroll + 32);
            case 265 -> readingScroll = Math.max(0, readingScroll - 32);
            case 267 -> readingScroll = Math.min(readingOverflow, readingScroll + modalH - 110);
            case 266 -> readingScroll = Math.max(0, readingScroll - modalH + 110);
            case 269 -> readingScroll = readingOverflow;
            case 268 -> readingScroll = 0;
            default -> {
                return false;
            }
        }
        return true;
    }

    void renderWinterFortuneDialog() {
        HubDraw d = ui.d;
        HubData.Entry reading = ui.reading;
        boolean isReading = reading != null;
        var paper = d.winterPaper();
        boolean previous = d.literalColors(true);
        ui.opacity = ui.interactiveAlpha = 1;
        ui.offsetY = 0;
        ui.nextHits.clear();
        float headH = isReading ? 88 : 76;
        float modalW = isReading ? 720 : 440;
        List<String> lines =
                isReading
                        ? ui.trackedLines(reading.fortune().interpretation(), 333, 16, 1, 400, true)
                        : List.of();
        float contentH =
                isReading ? 40 + Math.max(385, 191 + lines.size() * 31.2f) : 22 + 82 + 18 + 35 + 22;
        float maxH = Math.max(headH + 90, Math.min(790, ui.viewportH - 40));
        float modalH = Math.min(maxH, headH + contentH + 2);
        float modalX = (1260 - modalW) / 2;
        float modalY = (850 - modalH) / 2;
        host.setModalBounds(modalX, modalY, modalW, modalH);
        float bodyH = modalH - headH - 2;
        readingOverflow = isReading ? Math.max(0, contentH - bodyH) : 0;
        readingScroll = HubView.clamp(readingScroll, 0, readingOverflow);
        long now = ui.now, modalAt = host.modalAt();
        boolean reducedMotion = ui.reducedMotion;
        float enter =
                reducedMotion
                        ? 1
                        : HubMotion.bezier(
                                HubView.progress(now - modalAt, 220_000_000L), .2f, .8f, .2f, 1);
        float fade =
                reducedMotion ? 1 : HubMotion.ease(HubView.progress(now - modalAt, 180_000_000L));
        float viewportX = ui.viewportX,
                viewportY = ui.viewportY,
                viewportW = ui.viewportW,
                viewportH = ui.viewportH;
        d.captureGlassBackdrop(viewportX, viewportY, viewportW, viewportH);
        ui.beginAlpha(fade, viewportX, viewportY, viewportW, viewportH);
        d.blurBackdrop(viewportX, viewportY, viewportW, viewportH, 0, 5, 1);
        d.rect(viewportX, viewportY, viewportW, viewportH, 0x4a192c29);
        ui.beginAlpha(enter, modalX - 24, modalY - 24, modalW + 48, modalH + 64);
        ui.push(
                HubDraw.cssPerspective(
                        630, 425, 800, 0, 0, 0, .985f + .015f * enter, 0, 9 * (1 - enter)));
        d.boxShadow(modalX, modalY, modalW, modalH, 23, 0, 18, 65, -25, 0x8915261e);
        d.save();
        d.clipRoundRect(modalX, modalY, modalW, modalH, 23);
        d.rect(modalX, modalY, modalW, modalH, paper.sheet());
        float bodyTop = modalY + headH + 1;
        d.linearGradientRoundRect(
                modalX,
                modalY,
                modalW,
                headH,
                0,
                105,
                new int[] {paper.soft(), paper.sheet()},
                new float[] {0, 1});
        HubWinterSheet.grain(d, modalX, modalY, modalW, headH, 0, 1);
        ui.serif(
                isReading ? "签笺详情" : fortuneState == 0 ? "用签筒开启今日一签" : "前往解签龛",
                modalX + 25,
                modalY + (isReading ? 40 : 45),
                20,
                paper.ink(),
                true);
        if (isReading)
            ui.text(
                    reading.date().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
                            + " · "
                            + ui.playerName
                            + " 所得",
                    modalX + 25,
                    modalY + 65,
                    12,
                    paper.muted(),
                    false);
        else
            HubWinterSheet.illustration(
                    d, "fortune-tailpiece", modalX + modalW - 148, modalY + 17, 77, 41, 1);
        float closeX = modalX + modalW - 55, closeY = modalY + 20;
        d.circle(
                closeX + 15,
                closeY + 15,
                15,
                ui.inside(closeX, closeY, 30, 30) ? paper.ruling() : paper.cover());
        ui.icon("x", closeX + 6, closeY + 6, 18, paper.pine());
        ui.hit("modal-close", "关闭弹窗", closeX, closeY, 30, 30, ui::dismissOverlay);
        d.line(modalX, bodyTop - 1, modalX + modalW, bodyTop - 1, 1, paper.ruling());
        if (isReading) {
            d.save();
            d.clipRoundRect(modalX + 1, bodyTop, modalW - 2, bodyH, 0);
            d.rect(modalX + 1, bodyTop, modalW - 2, bodyH, paper.paper());
            HubWinterSheet.grain(d, modalX + 1, bodyTop, modalW - 2, bodyH, 0, 1);
            float x = modalX + 25, y = bodyTop + 20 - readingScroll, w = 313;
            d.radialGradientRoundRect(
                    x,
                    y,
                    w,
                    385,
                    14,
                    x + w * .62f,
                    y + 385 * .35f,
                    w * .8f,
                    385 * .95f,
                    new int[] {paper.soft(), paper.cover()},
                    new float[] {0, 1});
            HubWinterSheet.grain(d, x, y, w, 385, 14, 1);
            journalSlip(reading, x + 4, y + 8, w - 8, 369, modalAt, false);
            float rx = x + w + 24;
            ui.icon("leaf", rx, y + 16, 15, paper.pine());
            ui.serif("解签", rx + 23, y + 31, 17, paper.pine(), true);
            for (int i = 0; i < lines.size(); i++) {
                float baseline = y + 68 + i * 31.2f;
                if (baseline >= bodyTop - 6 && baseline <= bodyTop + bodyH + 20)
                    ui.tracked(lines.get(i), rx, baseline, 16, 1, paper.ink(), 400, true);
            }
            boolean archived = confirmedArchived(reading),
                    current = Objects.equals(displayDate, reading.date());
            float foot = y + contentH - 40 - 115.2f;
            d.line(rx, foot, rx + 333, foot, 1, paper.stitch());
            ui.icon("bookmark", rx, foot + 15, 16, paper.muted());
            ui.text(
                    archived ? "已收藏于你的藏签匣" : "已解签，尚未收藏",
                    rx + 24,
                    foot + 28,
                    12,
                    paper.ink(),
                    false);
            ui.text(
                    archived ? "签笺一经收藏，便成为旅途的一部分。" : "在游戏中打开藏签匣，即可收存这张签。",
                    rx + 24,
                    foot + 47,
                    11,
                    paper.muted(),
                    false);
            float by = foot + 73.2f;
            boolean waiting = ui.pendingModules.contains("fortunes");
            String label =
                    waiting ? "等待服务器确认…" : !archived ? "收藏后可设为展示" : current ? "当前展示的签笺" : "设为手册展示";
            boolean enabled = archived && !waiting;
            if (by + 36 > bodyTop && by < bodyTop + bodyH) {
                float btnHover =
                        ui.extensionHover("reading-display", enabled && ui.inside(rx, by, 333, 36));
                float btnMove = ui.extensionButtonMove("reading-display", enabled, btnHover > .5f);
                float btnY = by + btnMove;
                d.roundRect(
                        rx,
                        btnY,
                        333,
                        36,
                        10,
                        enabled
                                ? HubView.mix(paper.pine(), paper.ink(), .11f * btnHover)
                                : HubView.withAlpha(paper.pine(), .60f));
                float tw = d.textWidth(label, 12, true), ix = rx + (333 - tw - 22) / 2;
                ui.icon("sparkles", ix, btnY + 10, 16, paper.sheet());
                ui.text(label, ix + 22, btnY + 23, 12, paper.sheet(), true);
                if (enabled) {
                    float clippedTop = Math.max(bodyTop, btnY);
                    ui.hit(
                            "reading-display",
                            label,
                            rx,
                            clippedTop,
                            333,
                            Math.min(btnY + 36, bodyTop + bodyH) - clippedTop,
                            () -> {
                                if (current) ui.notifyUser("手册正在展示这张签笺");
                                else displaySelected(reading);
                            });
                }
            }
            if (readingOverflow > 0) {
                float thumb = bodyH * bodyH / contentH;
                d.roundRect(
                        modalX + modalW - 6,
                        bodyTop + (bodyH - thumb) * readingScroll / readingOverflow,
                        3,
                        thumb,
                        2,
                        paper.stitch());
            }
            d.restore();
        } else {
            float x = modalX + 25, y = bodyTop + 22;
            d.roundRect(x, y, 80, 82, 13, paper.soft());
            HubWinterSheet.grain(d, x, y, 80, 82, 13, 1);
            String image = fortuneState == 0 ? "fortune-barrel" : "fortune-slip";
            var img = d.imageResource("textures/ui/" + image + ".png");
            float scale = Math.min(60f / img.getWidth(), 62f / img.getHeight()),
                    iw = img.getWidth() * scale,
                    ih = img.getHeight() * scale;
            ui.image(image, x + (80 - iw) / 2, y + (82 - ih) / 2, iw, ih, 1);
            ui.wrap(
                    fortuneState == 0 ? "在游戏中手持签筒，右键抽取今天的签。" : "在游戏中拿着未解签，对解签龛使用，即可读到签文。",
                    x + 101,
                    y + 29,
                    286,
                    14,
                    21,
                    paper.ink(),
                    false);
            ui.text("手册会在操作完成后更新你的签笺状态。", x + 101, y + 72, 11, paper.muted(), false);
            ui.button(
                    "fortune-help-ok",
                    "知道了",
                    modalX + modalW - 109,
                    y + 100,
                    84,
                    35,
                    true,
                    true,
                    ui::dismissOverlay);
        }
        d.restore();
        d.strokeRoundRect(
                modalX + .5f, modalY + .5f, modalW - 1, modalH - 1, 23, 1, paper.ruling());
        ui.pop();
        ui.endAlpha();
        ui.endAlpha();
        d.literalColors(previous);
    }

    void renderFortuneHelp(float modalX, float modalY, float modalH) {
        float x = modalX + 30, y = modalY + 125;
        ui.image(fortuneState == 0 ? "fortune-barrel" : "fortune-slip", x, y, 94, 106, 1);
        ui.wrap(
                fortuneState == 0 ? "拿起签筒，为今天留一份期许。" : "带上未解的签，前往解签龛。",
                x + 119,
                y + 20,
                235,
                13,
                21,
                0xff807789,
                true);
        ui.wrap(
                fortuneState == 0
                        ? "在游戏中手持签筒使用，即可抽取今日的签。抽到之后仍需解签，才会看见结果。"
                        : "签文尚未解读，因此手册不会提前展示内容。完成解签后，打开藏签匣即可归档收藏。",
                x + 119,
                y + 68,
                235,
                12,
                23,
                0xff807789,
                false);
        ui.text(
                ui.live() ? "请在游戏内完成抽签、解签与归档。" : "这里是本地界面原型，不会触发游戏抽签。",
                x,
                modalY + modalH - 40,
                11,
                0xff9992a4,
                false);
    }

    void renderReadingModal(float modalX, float modalY, float modalW, float modalH) {
        HubDraw d = ui.d;
        boolean winter = ui.winter;
        HubData.Entry entry = ui.reading;
        HubData.Fortune f = entry.fortune();
        float x = modalX + 26, y = modalY + 121, w = 319.5f, h = modalH - 147;
        var paper = d.winterPaper();
        boolean winterColors = winter && d.literalColors(true);
        if (winter) HubWinterSheet.letter(d, x, y, w, h, false, ui.opacity);
        else {
            d.linearGradientRoundRect(
                    x, y, w, h, 6, 110, new int[] {0xfff7f0dd, 0xfffffaf0}, new float[] {0, 1});
            d.strokeRoundRect(x, y, w, h, 6, 1, 0xffe6dcc1);
            d.boxShadow(x, y, w, h, 6, 0, 6, 16, 0, 0x12867955);
        }
        ui.trackedCenter(
                "第" + f.id() + "签 · " + f.grade(),
                x + w / 2,
                y + 39,
                11,
                .5f,
                HubData.gradeColor(f.grade()),
                false,
                false);
        ui.trackedCenter(
                f.title(), x + w / 2, y + 89, 29, 4, winter ? paper.ink() : 0xff68604c, true, true);
        for (int i = 0; i < f.poem().size(); i++)
            ui.trackedCenter(
                    f.poem().get(i),
                    x + w / 2,
                    y + 133 + i * 35.2f,
                    16,
                    2,
                    winter ? paper.ink() : 0xff84806c,
                    false,
                    true);
        int corner = winter ? paper.stitch() : 0xffcbbc93;
        d.line(x + 8, y + 8, x + 26, y + 8, 1, corner);
        d.line(x + 8, y + 8, x + 8, y + 26, 1, corner);
        d.line(x + w - 8, y + h - 8, x + w - 26, y + h - 8, 1, corner);
        d.line(x + w - 8, y + h - 8, x + w - 8, y + h - 26, 1, corner);
        ui.push(Matrix33.makeRotate(-5, x + w / 2, y + 292));
        ui.beginAlpha(.7f, x + w / 2 - 11, y + 281, 22, 22);
        d.strokeRoundRect(
                x + w / 2 - 11, y + 281, 22, 22, 0, 1, winter ? paper.mail() : 0xffb58b6b);
        ui.serifCenter("签", x + w / 2, y + 297, 12, winter ? paper.mailInk() : 0xffb08767, false);
        ui.endAlpha();
        ui.pop();
        ui.trackedCenter(
                "DAILY FORTUNES",
                x + w / 2,
                y + 333,
                10,
                2,
                winter ? paper.muted() : 0xffc1b693,
                false,
                false);
        if (winter) HubWinterSheet.illustration(d, "sleeping-fox", x, y + 341, w, 31, ui.opacity);
        float rx = x + w + 29;
        ui.icon("leaf", rx, y + 8, 15, winter ? paper.muted() : 0xff8f7da7);
        ui.tracked("解签", rx + 23, y + 21, 12, 2, winter ? paper.muted() : 0xff8f7da7, 500, false);
        ui.trackedWrap(
                f.interpretation(),
                rx,
                y + 57,
                w,
                15,
                31.5f,
                1,
                winter ? paper.ink() : 0xff7b7883,
                400,
                true);
        boolean archived = archive().containsKey(entry.date());
        d.line(rx, y + h - 111, rx + w, y + h - 111, 1, winter ? paper.ruling() : 0xffe6e2ef);
        ui.icon("bookmark", rx, y + h - 97, 16, winter ? paper.muted() : 0xff8f91a2);
        ui.text(
                archived ? "已收藏于你的藏签匣" : "已解签，尚未收藏",
                rx + 24,
                y + h - 84,
                10,
                winter ? paper.muted() : 0xff8f91a2,
                false);
        ui.text(
                archived ? "签笺一经收藏，便成为旅途的一部分。" : "在游戏中打开藏签匣，即可收存这张签。",
                rx + 24,
                y + h - 66,
                10,
                winter ? paper.muted() : 0xff9997aa,
                false);
        boolean current = Objects.equals(displayDate, entry.date());
        ui.button(
                "reading-display",
                !archived ? "收藏后可设为展示" : current ? "当前展示的签笺" : "设为手册展示  ✧",
                rx,
                y + h - 45,
                w,
                37,
                true,
                archived && (winter || !current),
                () -> {
                    if (current) ui.notifyUser("手册正在展示这张签笺");
                    else displaySelected(entry);
                });
        if (winter) d.literalColors(winterColors);
    }

    private int winterTone(int day, int night) {
        return ui.dark ? night : day;
    }

    private void winterCounter() {
        HubDraw d = ui.d;
        HubData data = ui.data;
        boolean live = ui.live();
        YearMonth month = ui.month;
        LocalDate selectedDate = ui.selectedDate;
        ui.group(0, 240);
        boolean previous = d.literalColors(true);
        ui.collectionHeading(
                "我的签笺",
                live && !ui.ready("fortunes")
                        ? ui.moduleLabel("fortunes")
                        : "已收藏  " + archiveCount() + " 张");
        if (live && !ui.ready("fortunes")) {
            ui.unavailable("fortunes");
        } else {
            Map<LocalDate, HubData.Entry> records = archive();
            HubData.Entry featured = live ? data.featured : records.get(displayDate);
            HubData.Entry selected = records.get(selectedDate);
            d.boxShadow(
                    HubTitlesView.COLLECTION_LEFT,
                    HubTitlesView.COLLECTION_TOP,
                    HubTitlesView.COLLECTION_WIDTH,
                    DESK_END - HubTitlesView.COLLECTION_TOP,
                    21,
                    0,
                    5,
                    10,
                    -7,
                    0x66171f16);
            d.save();
            d.clipRoundRect(
                    HubTitlesView.COLLECTION_LEFT,
                    HubTitlesView.COLLECTION_TOP,
                    HubTitlesView.COLLECTION_WIDTH,
                    DESK_END - HubTitlesView.COLLECTION_TOP,
                    21);
            HubCollectionArt.cover(
                    d,
                    false,
                    HubTitlesView.COLLECTION_LEFT,
                    HubTitlesView.COLLECTION_TOP,
                    HubTitlesView.COLLECTION_WIDTH,
                    COUNTER_BAR - HubTitlesView.COLLECTION_TOP);
            counterFeature(featured);
            counterBar();
            HubCollectionArt.desk(
                    d,
                    HubTitlesView.COLLECTION_LEFT,
                    DESK_TOP,
                    HubTitlesView.COLLECTION_WIDTH,
                    DESK_END - DESK_TOP);
            List<HubData.Entry> stored =
                    records.values().stream()
                            .filter(e -> YearMonth.from(e.date()).equals(month))
                            .sorted(Comparator.comparing(HubData.Entry::date).reversed())
                            .toList();
            counterMonth(stored, records);
            float sx = COUNTER_SPLIT + 22,
                    sw = HubTitlesView.COLLECTION_LEFT + HubTitlesView.COLLECTION_WIDTH - 22 - sx;
            if (!Objects.equals(unfoldedDate, selectedDate)) {
                unfoldedDate = selectedDate;
                unfoldedAt = ui.now;
            }
            journalSlip(
                    selected,
                    sx,
                    DESK_TOP + 17,
                    sw,
                    DESK_END - 52 - (DESK_TOP + 17),
                    unfoldedAt,
                    calendarInitialLoading());
            boolean displayed = selected != null && Objects.equals(selected.date(), displayDate),
                    pending = ui.pendingModules.contains("fortunes");
            String displayLabel = pending ? "等待服务器确认…" : displayed ? "当前选择已展示" : "展示选中的签";
            float by = DESK_END - 40;
            int chalk = 0xfff2e9d5;
            boolean canDisplay = selected != null && !displayed && !pending;
            ui.icon(
                    displayed ? "check" : "bookmark",
                    sx + 2,
                    by + 7,
                    14,
                    canDisplay ? chalk : HubView.withAlpha(chalk, .72f));
            ui.text(
                    displayLabel,
                    sx + 21,
                    by + 19,
                    12,
                    canDisplay ? chalk : HubView.withAlpha(chalk, .72f),
                    false);
            if (canDisplay) {
                if (ui.inside(sx, by, 111, 29))
                    d.line(
                            sx + 21,
                            by + 23,
                            sx + 21 + d.textWidth(displayLabel, 12, false),
                            by + 23,
                            1,
                            chalk);
                ui.hit(
                        "display-selected",
                        "展示选中的签",
                        sx,
                        by,
                        111,
                        29,
                        () -> displaySelected(selected));
            }
            float rx = sx + sw - 103;
            ui.beginAlpha(selected == null ? .72f : 1, rx, by - 1, 103, 34);
            d.roundRect(rx, by, 103, 31, 4, 0xffe8ddba);
            HubWinterSheet.grain(d, rx, by, 103, 31, 4, 1);
            ui.serif("阅读解签", rx + 12, by + 21, 13, 0xff493d29, true);
            ui.icon("arrow-right", rx + 75, by + 8, 15, 0xff493d29);
            if (selected != null) {
                if (ui.inside(rx, by, 103, 31))
                    d.line(rx + 12, by + 24, rx + 65, by + 24, 1, 0xff493d29);
                ui.hit("read-selected", "阅读解签", rx, by, 103, 31, () -> ui.openReading(selected));
            }
            ui.endAlpha();
            d.restore();
            HubWinterSheet.illustration(d, "fortune-tailpiece", 651, DESK_END + 9, 144, 40, 1);
        }
        d.literalColors(previous);
        ui.ungroup();
    }

    private void counterFeature(HubData.Entry featured) {
        HubDraw d = ui.d;
        int ink = winterTone(0xff304534, 0xfff4ead0);
        float x = HubTitlesView.COLLECTION_LEFT + 39,
                mid = (COUNTER_BAR + HubTitlesView.COLLECTION_TOP) / 2;
        ui.icon("bookmark", x, mid - 49, 14, ink);
        ui.tracked("手册展示", x + 22, mid - 37, 12, .84f, ink, 450, false);
        if (featured == null) {
            ui.serif("尚未选择展示签笺", x, mid + 12, 25, ink, true);
            ui.text(
                    archiveCount() > 0 ? "在下方选中一张收藏，设为展示。" : "收藏签笺后，可在这里展示。",
                    x,
                    mid + 46,
                    12,
                    ink,
                    false);
        } else {
            String title = ui.fitTracked(featured.fortune().title(), 421, 41, 2.25f, 700, true);
            float tw = ui.trackedWidth(title, 41, 2.25f, 700, true);
            ui.tracked(title, x, mid + 19, 41, 2.25f, ink, 700, true);
            ui.icon("chevron-right", x + tw + 8, mid - 6, 18, HubView.withAlpha(ink, .65f));
            if (ui.inside(x, mid - 24, tw + 30, 52)) d.line(x, mid + 24, x + tw, mid + 24, 1, ink);
            ui.hit(
                    "read-feature",
                    "阅读手册展示的签笺",
                    x,
                    mid - 24,
                    tw + 30,
                    52,
                    () -> ui.openReading(featured));
            String grade = featured.fortune().grade();
            ui.text(grade, x, mid + 46, 12, winterTone(0xff884633, 0xfff0ac88), true);
            float gx = x + d.textWidth(grade, 12, true) + 8;
            d.line(gx, mid + 34, gx, mid + 48, 1, winterTone(0xffa69e81, 0xff73816b));
            ui.text(featured.date().format(HubView.DATE), gx + 8, mid + 46, 12, ink, false);
            if (ui.live())
                ui.plainControl(
                        "clear-feature",
                        "清除手册展示",
                        "x",
                        "",
                        gx + 115,
                        mid + 29,
                        24,
                        24,
                        ink,
                        0,
                        !ui.pendingModules.contains("fortunes"),
                        () -> {
                            JsonObject payload = ui.mutation("fortunes");
                            payload.addProperty("date", "");
                            ui.request("fortunes", "feature", payload);
                        });
        }
    }

    private void setCounterMode(boolean list) {
        if (history == list) return;
        history = list;
        historyScroll = 0;
        monthAt = ui.now;
    }

    private void returnToCurrentMonth() {
        HubData data = ui.data;
        boolean changed = !ui.month.equals(YearMonth.from(data.today));
        monthDirection = 1;
        ui.month = YearMonth.from(data.today);
        ui.selectedDate = data.today;
        chooseLatestOnCalendar = false;
        historyScroll = 0;
        if (changed) monthAt = ui.now;
        if (changed || calendarInitialLoading()) calendarRequest();
    }

    private void counterBar() {
        HubDraw d = ui.d;
        HubData data = ui.data;
        LocalDate selectedDate = ui.selectedDate;
        int sill = winterTone(0xffe5dfcc, 0xff37463c),
                ink = winterTone(0xff38483c, 0xfff0e9d0),
                rule = winterTone(0xffa69e81, 0xff73816b);
        d.rect(
                HubTitlesView.COLLECTION_LEFT,
                COUNTER_BAR,
                HubTitlesView.COLLECTION_WIDTH,
                36,
                sill);
        HubWinterSheet.grain(
                d,
                HubTitlesView.COLLECTION_LEFT,
                COUNTER_BAR,
                HubTitlesView.COLLECTION_WIDTH,
                36,
                0,
                1);
        d.line(
                HubTitlesView.COLLECTION_LEFT,
                COUNTER_BAR,
                HubTitlesView.COLLECTION_LEFT + HubTitlesView.COLLECTION_WIDTH,
                COUNTER_BAR,
                1,
                rule);
        d.line(
                HubTitlesView.COLLECTION_LEFT,
                DESK_TOP - 1,
                HubTitlesView.COLLECTION_LEFT + HubTitlesView.COLLECTION_WIDTH,
                DESK_TOP - 1,
                1,
                rule);
        float y = COUNTER_BAR + 5;
        ui.plainControl(
                "counter-calendar",
                "切换月历显示",
                "calendar-days",
                "月历",
                MONTH_X,
                y,
                61,
                26,
                history ? ink : sill,
                history ? 0 : ink,
                true,
                () -> setCounterMode(false));
        ui.plainControl(
                "counter-list",
                "切换签笺列表",
                "list",
                "列表",
                MONTH_X + 64,
                y,
                61,
                26,
                history ? sill : ink,
                history ? ink : 0,
                true,
                () -> setCounterMode(true));
        ui.plainControl(
                "previous-month",
                "上个月",
                "chevron-left",
                "",
                MONTH_RIGHT - 96,
                y,
                26,
                26,
                ink,
                0,
                true,
                () -> shiftMonth(-1));
        ui.plainControl(
                "current-month",
                "本月",
                null,
                "本月",
                MONTH_RIGHT - 67,
                y,
                38,
                26,
                ink,
                0,
                true,
                this::returnToCurrentMonth);
        ui.plainControl(
                "next-month",
                "下个月",
                "chevron-right",
                "",
                MONTH_RIGHT - 26,
                y,
                26,
                26,
                ink,
                0,
                ui.month.isBefore(YearMonth.from(data.today)),
                () -> shiftMonth(1));
        ui.trackedCenter(
                selectedDate.format(HubView.DATE)
                        + (selectedDate.equals(data.today) ? "   今天" : ""),
                (COUNTER_SPLIT + HubTitlesView.COLLECTION_LEFT + HubTitlesView.COLLECTION_WIDTH)
                        / 2,
                y + 18,
                12,
                .96f,
                ink,
                true,
                true);
    }

    private void counterMonth(List<HubData.Entry> stored, Map<LocalDate, HubData.Entry> records) {
        HubDraw d = ui.d;
        HubData data = ui.data;
        YearMonth month = ui.month;
        LocalDate selectedDate = ui.selectedDate;
        boolean dark = ui.dark;
        int ink = winterTone(0xff313f34, 0xfff2e7cc),
                sub = winterTone(0xff5d6355, 0xffceccb6),
                red = winterTone(0xff8b412f, 0xfff0aa89),
                rule = winterTone(0x587e8069, 0x4ad0c5a5);
        float w = MONTH_RIGHT - MONTH_X, top = DESK_TOP + 18, cy = (MONTH_GRID + top) / 2;
        d.strokeRoundRect(MONTH_X, cy - 27, 15, 52, 2, 1, red);
        String year = month.getYear() + "年";
        for (int i = 0; i < year.length(); i++)
            ui.serifCenter(year.substring(i, i + 1), MONTH_X + 7.5f, cy - 16 + i * 9, 9, red, true);
        ui.tracked(
                WINTER_MONTHS[month.getMonthValue() - 1],
                MONTH_X + 26,
                cy + 3,
                31,
                1.86f,
                ink,
                700,
                true);
        ui.text(
                calendarInitialLoading() ? "正在读取本月收藏…" : "本月收签  " + stored.size() + " 张",
                MONTH_X + 26,
                cy + 25,
                11,
                sub,
                false);
        var wood = d.imageResource(HubView.WINTER_ART + "calendar-woodcut.png");
        float ws = Math.min(260f / wood.getWidth(), 90f / wood.getHeight()),
                dw = wood.getWidth() * ws,
                dh = wood.getHeight() * ws;
        HubCollectionArt.filtered(
                d,
                "calendar-woodcut",
                MONTH_RIGHT - dw,
                MONTH_GRID - 6 - dh,
                dw,
                dh,
                dark ? 1.17f : 1,
                dark ? .8f : 1);
        d.line(MONTH_X, MONTH_GRID - 3, MONTH_RIGHT, MONTH_GRID - 3, 1, rule);
        d.line(MONTH_X, MONTH_GRID - 1, MONTH_RIGHT, MONTH_GRID - 1, 1, rule);
        float e =
                ui.reducedMotion
                        ? 1
                        : HubMotion.easeOut(HubView.progress(ui.now - monthAt, 240_000_000L));
        d.save();
        d.clipRoundRect(MONTH_X, MONTH_GRID, w, MONTH_END - MONTH_GRID, 0);
        ui.beginAlpha(.5f + .5f * e, MONTH_X, MONTH_GRID, w, MONTH_END - MONTH_GRID);
        ui.push(Matrix33.makeTranslate(monthDirection * 8 * (1 - e), 0));
        if (calendarInitialLoading())
            ui.textCenter(
                    "正在读取本月收藏…", MONTH_X + w / 2, (MONTH_END + MONTH_GRID) / 2, 13, sub, false);
        else if (history) counterList(stored, ink, sub, rule);
        else {
            float cellW = w / 7, startY = MONTH_GRID + 25.5f;
            int offset = month.atDay(1).getDayOfWeek().getValue() % 7,
                    rows = (offset + month.lengthOfMonth() + 6) / 7;
            float cellH = (MONTH_END - startY) / rows;
            for (int i = 0; i < 7; i++)
                ui.serifCenter(
                        "日一二三四五六".substring(i, i + 1),
                        MONTH_X + (i + .5f) * cellW,
                        MONTH_GRID + 17,
                        11,
                        i == 0 || i == 6 ? red : sub,
                        true);
            for (int i = 0; i < rows * 7; i++) {
                float x = MONTH_X + i % 7 * cellW, y = startY + i / 7 * cellH;
                d.line(x, y, x + cellW, y, 1, rule);
                int day = i - offset + 1;
                if (day < 1 || day > month.lengthOfMonth()) continue;
                LocalDate date = month.atDay(day);
                HubData.Entry entry = records.get(date);
                boolean future = date.isAfter(data.today), selected = date.equals(selectedDate);
                if (!future && ui.inside(x, y, cellW, cellH))
                    d.rect(x, y + 1, cellW, cellH - 1, HubView.withAlpha(ink, .08f));
                float fs = cellH <= 45 ? 13 : cellH <= 54 ? 15 : 17.5f,
                        cyDay = y + cellH / 2,
                        numberH = fs * (cellH <= 45 ? 1.16f : cellH <= 54 ? 1.26f : 1.32f);
                float
                        ny =
                                cellH <= 45
                                        ? Math.max(y + 2, cyDay - 16)
                                        : y + Math.max(4f, (cellH - numberH) * 0.30f),
                        cx = x + cellW / 2,
                        badgeHalfW = fs * 0.86f;
                if (selected)
                    d.roundRect(cx - badgeHalfW, ny, badgeHalfW * 2, numberH, 3, 0xff934835);
                ui.serifCenter(
                        Integer.toString(day),
                        cx,
                        ny + (numberH + fs * .69f) / 2,
                        fs,
                        selected ? 0xfffff3d8 : future ? HubView.withAlpha(ink, .63f) : ink,
                        true);
                if (date.equals(data.today))
                    ui.serif("今", cx + badgeHalfW + 3.5f, ny + 8.5f, 9, red, true);
                if (entry != null) {
                    float labelSize = cellH <= 54 ? 10 : 11.5f;
                    String label =
                            ui.fitTracked(
                                    entry.fortune().title(), cellW - 9, labelSize, 0, 550, true);
                    float lw = d.textWidthSerif(label, labelSize, false);
                    float gapBottom = y + cellH - (ny + numberH);
                    float baseline = ny + numberH + (gapBottom + labelSize * 0.78f) * 0.5f;
                    d.roundRect(
                            cx - (lw + 5) / 2,
                            baseline - labelSize * .77f,
                            3,
                            labelSize * .7f,
                            1,
                            HubData.gradeColor(entry.fortune().grade()));
                    ui.serif(label, cx - (lw + 5) / 2 + 5, baseline, labelSize, ink, false);
                }
                if (!future)
                    ui.hit(
                            "day-" + date,
                            date + (entry == null ? " 未收藏" : " " + entry.fortune().title()),
                            x,
                            y,
                            cellW,
                            cellH,
                            () -> ui.selectedDate = date);
            }
        }
        ui.pop();
        ui.endAlpha();
        d.restore();
    }

    private void counterList(List<HubData.Entry> entries, int ink, int sub, int rule) {
        HubDraw d = ui.d;
        LocalDate selectedDate = ui.selectedDate;
        float w = MONTH_RIGHT - MONTH_X, h = MONTH_END - MONTH_GRID;
        if (entries.isEmpty()) {
            float cx = (MONTH_X + MONTH_RIGHT) / 2, cy = (MONTH_GRID + MONTH_END) / 2;
            ui.icon("inbox", cx - 13, cy - 42, 26, sub);
            ui.serifCenter("这个月还没有签笺", cx, cy + 9, 18, ink, true);
            ui.textCenter("已收藏的签会按抽签日期出现在这里。", cx, cy + 36, 12, sub, false);
            return;
        }
        float rowH = 42, overflow = Math.max(0, entries.size() * rowH - h);
        historyScroll = HubView.clamp(historyScroll, 0, overflow);
        for (int i = 0; i < entries.size(); i++) {
            float y = MONTH_GRID + i * rowH - historyScroll,
                    top = Math.max(MONTH_GRID, y),
                    bottom = Math.min(MONTH_END, y + rowH);
            if (top >= bottom) continue;
            var entry = entries.get(i);
            if (selectedDate.equals(entry.date()) || ui.inside(MONTH_X, top, w, bottom - top))
                d.rect(MONTH_X, y, w, rowH, HubView.withAlpha(ink, .08f));
            d.line(MONTH_X, y + rowH - 1, MONTH_RIGHT, y + rowH - 1, 1, rule);
            ui.text(
                    entry.date().format(DateTimeFormatter.ofPattern("MM / dd")),
                    MONTH_X + 7,
                    y + 26,
                    12,
                    sub,
                    false);
            ui.serif(
                    ui.fitTracked(entry.fortune().title(), w - 155, 16, 0, 700, true),
                    MONTH_X + 70,
                    y + 27,
                    16,
                    ink,
                    true);
            float gx = MONTH_RIGHT - 81;
            d.rect(gx, y + 14, 3, 14, HubData.gradeColor(entry.fortune().grade()));
            ui.text(entry.fortune().grade(), gx + 8, y + 25, 11, sub, false);
            ui.icon("chevron-right", MONTH_RIGHT - 21, y + 13, 15, sub);
            ui.hit(
                    "history-" + entry.date(),
                    entry.date() + " " + entry.fortune().title(),
                    MONTH_X,
                    top,
                    w,
                    bottom - top,
                    () -> ui.selectedDate = entry.date());
        }
        if (overflow > 0) {
            float thumb = h * h / (overflow + h);
            d.roundRect(
                    MONTH_RIGHT - 3,
                    MONTH_GRID + historyScroll / overflow * (h - thumb),
                    3,
                    thumb,
                    2,
                    rule);
        }
    }

    private boolean confirmedArchived(HubData.Entry entry) {
        HubData data = ui.data;
        if (entry.date().equals(data.today) && fortuneState < 3) return false;
        if (archive().containsKey(entry.date())) return true;
        return ui.live()
                && (data.featured != null && entry.date().equals(data.featured.date())
                        || fortuneState == 3 && entry.date().equals(data.today));
    }

    private void journalSlip(
            HubData.Entry entry, float x, float y, float w, float h, long start, boolean loading) {
        HubDraw d = ui.d;
        boolean dark = ui.dark;
        float sw = Math.min(300, Math.min(w, (h - 2) * 580f / 927)), sh = sw * 927 / 580;
        float cx = x + w / 2, cy = y + h / 2, lx = cx - sw / 2, ly = cy - sh / 2;
        float e =
                ui.reducedMotion
                        ? 1
                        : HubMotion.bezier(
                                HubView.progress(ui.now - start, 320_000_000L), .2f, .68f, .3f, 1);
        ui.beginAlpha(.65f + .35f * e, x, y - 3, w, h + 6);
        ui.push(
                HubDraw.cssPerspective(
                        cx, ly + sh * .05f, 700, -5 * (1 - e), 0, 0, 1, 0, -2 * (1 - e)));
        HubCollectionArt.filtered(
                d, "journal-slip", lx, ly, sw, sh, dark ? .87f : 1, dark ? .84f : 1);
        int ink = 0xff3f372a;
        if (entry == null) {
            slipImprint(null, cx, cy - 80);
            ui.icon("scroll-text", cx - 14, cy - 37, 28, ink);
            ui.serifCenter(loading ? "正在读取签笺" : "这一天未留签", cx, cy + 25, 22, ink, true);
            List<String> lines =
                    ui.trackedLines(
                            loading ? "等待服务器确认收藏记录。" : "在游戏中打开藏签匣，可将已解的签按日期归档。",
                            Math.min(190, sw * .73f),
                            12,
                            0,
                            400,
                            false);
            for (int i = 0; i < lines.size(); i++)
                ui.textCenter(lines.get(i), cx, cy + 53 + i * 18.6f, 12, 0xff534932, false);
        } else {
            HubData.Fortune fortune = entry.fortune();
            float scriptH = sh * .56f;
            int maxPoem =
                    fortune.poem().stream()
                            .mapToInt(line -> line.codePointCount(0, line.length()))
                            .max()
                            .orElse(0);
            float fs =
                    Math.min(
                            HubView.clamp(h * .0465f, 12, 20),
                            scriptH / Math.max(1, maxPoem) / 1.1f);
            int count = fortune.poem().size(),
                    titleLen = fortune.title().codePointCount(0, fortune.title().length());
            float ts =
                    Math.min(
                            HubView.clamp(h * .075f, 18, 34),
                            scriptH / Math.max(1, titleLen) / 1.11f);
            float gap = Math.min(9, w * .007f),
                    sep = Math.min(9, w * .0085f),
                    mainGap = Math.min(18, w * .0145f);
            float poemW = count * fs * 1.21f + Math.max(0, count - 1) * gap,
                    scriptW = poemW + sep + mainGap + ts * 1.2f;
            float s = Math.min(1, (sw * .86f) / Math.max(1, scriptW));
            fs *= s;
            ts *= s;
            gap *= s;
            sep *= s;
            mainGap *= s;
            scriptW *= s;
            poemW *= s;
            float actualH = Math.max(maxPoem * fs * 1.1f, titleLen * ts * 1.11f),
                    top = cy - actualH / 2 - 5;
            slipImprint(fortune, cx, top - 31);
            float right = cx + scriptW / 2;
            verticalText(fortune.title(), right - ts * .6f, top, ts, 1.11f, ink, true);
            float divider = right - ts * 1.2f - mainGap;
            d.line(divider, top, divider, top + maxPoem * fs * 1.1f, 1, 0x69af9273);
            for (int i = 0; i < count; i++)
                verticalText(
                        fortune.poem().get(i),
                        divider - sep - fs * .605f - i * (fs * 1.21f + gap),
                        top,
                        fs,
                        1.1f,
                        ink,
                        false);
            boolean archived = confirmedArchived(entry);
            String footer = ui.fit(ui.playerName, sw * .37f, 10, false) + " 所得  " + entry.time();
            float fy = top + actualH + 21,
                    fw = d.textWidth(footer, 10, false),
                    fx = cx - (fw + (archived ? 23 : 0)) / 2;
            ui.text(footer, fx, fy, 10, 0xff514832, false);
            if (archived) {
                float stampX = fx + fw + 6, stampY = fy - 13;
                d.save();
                d.canvas().concat(Matrix33.makeRotate(-5, stampX + 8, stampY + 8));
                d.strokeRoundRect(stampX, stampY, 17, 17, 3, 1, 0xff924736);
                d.strokeRoundRect(stampX + 2, stampY + 2, 13, 13, 2, 1, 0xff924736);
                ui.serifCenter("藏", stampX + 8.5f, stampY + 12.5f, 11, 0xff924736, true);
                d.restore();
            }
        }
        ui.pop();
        ui.endAlpha();
    }

    private void slipImprint(HubData.Fortune fortune, float cx, float y) {
        HubDraw d = ui.d;
        String grade = fortune == null ? "未收藏" : fortune.grade(),
                number = fortune == null ? "" : "第 " + fortune.id() + " 签";
        float gw = ui.trackedWidth(grade, 12, 1.4f, 650, true) + 12,
                nw = d.textWidthSerif(number, 11, false);
        float x = cx - (gw + (number.isEmpty() ? 0 : 8 + nw)) / 2;
        d.roundRect(x, y, gw, 22, 3, 0xff994c38);
        ui.tracked(grade, x + 6, y + 15.5f, 12, 1.4f, 0xfffff0d2, 650, true);
        if (!number.isEmpty()) ui.serif(number, x + gw + 8, y + 15, 11, 0xff8b402f, false);
    }

    private void verticalText(
            String value, float cx, float top, float size, float spacing, int ink, boolean bold) {
        for (int i = 0; i < value.length(); ) {
            int cp = value.codePointAt(i);
            ui.serifCenter(
                    new String(Character.toChars(cp)), cx, top + size * .88f, size, ink, bold);
            i += Character.charCount(cp);
            top += size * spacing;
        }
    }

    private void fortunes() {
        if (ui.winter) {
            winterCounter();
            return;
        }
        HubDraw d = ui.d;
        HubData data = ui.data;
        YearMonth month = ui.month;
        long now = ui.now;
        Map<LocalDate, HubData.Entry> records = archive();
        ui.heading(
                "我的签笺",
                "一日一签，把沿途的机缘慢慢收藏。",
                "DailyFortunes · " + ui.moduleLabel("fortunes"),
                archiveCount());
        if (ui.live() && !ui.ready("fortunes")) {
            ui.unavailable("fortunes");
            return;
        }
        ui.group(35, 360);
        ui.glass(HubView.LEFT, 174, 662, 626, 22, 0xecffffff, 0xabffffff);
        int stored =
                (int)
                        records.keySet().stream()
                                .filter(date -> YearMonth.from(date).equals(month))
                                .count();
        float monthProgress = HubView.progress(now - monthAt, 260_000_000L);
        float monthHeading = ui.reducedMotion ? 1 : HubMotion.easeOut(monthProgress);
        float monthOffset = monthDirection * (28f / 3) * (1 - monthHeading);
        ui.beginAlpha(monthHeading, HubView.LEFT, 174, 430, 78);
        ui.text(
                String.format(Locale.ROOT, "%02d", month.getMonthValue()),
                258 + monthOffset,
                231,
                39,
                0xff67549b,
                true);
        ui.text(
                month.getYear() + " 年 " + month.getMonthValue() + " 月",
                320 + monthOffset,
                211,
                13,
                0xff505b76,
                true);
        ui.icon("bookmark", 321 + monthOffset, 223, 12, 0xff8a8fa5);
        ui.text(
                calendarInitialLoading() ? "正在读取本月收藏…" : "本月收藏 " + stored + " 张",
                339 + monthOffset,
                234,
                10,
                0xff8a8fa5,
                false);
        ui.endAlpha();
        ui.glass(704, 195, 171, 37, 11, 0xfff0edf8, 0xfff0edf8);
        ui.textButton(
                "current-month",
                "本月",
                707,
                198,
                45,
                30,
                () -> {
                    monthDirection = 1;
                    ui.month = YearMonth.from(data.today);
                    ui.selectedDate = data.today;
                    chooseLatestOnCalendar = false;
                    monthAt = System.nanoTime();
                    historyScroll = 0;
                    calendarRequest();
                },
                true);
        ui.iconButton(
                "previous-month", "上个月", "chevron-left", 752, 198, 29, () -> shiftMonth(-1), true);
        ui.iconButton(
                "next-month",
                "下个月",
                "chevron-right",
                782,
                198,
                29,
                () -> shiftMonth(1),
                month.isBefore(YearMonth.from(data.today)));
        d.line(816, 205, 816, 225, 1, ui.a(0xffdbd7e7));
        ui.iconButton(
                "fortune-mode",
                history ? "切换月历显示" : "切换签笺列表",
                history ? "grid-2x2" : "list",
                829,
                198,
                30,
                () -> {
                    history = !history;
                    historyScroll = 0;
                    monthAt = System.nanoTime();
                },
                true);
        d.save();
        d.clipRoundRect(255, 252, 624, 498, 0);
        float monthBody =
                ui.reducedMotion ? 1 : HubMotion.bezier(monthProgress, .18f, .75f, .22f, 1);
        float slide = monthDirection * 28 * (1 - monthBody);
        ui.beginAlpha(monthBody);
        ui.push(Matrix33.makeTranslate(slide, 0));
        if (calendarInitialLoading())
            ui.textCenter("正在读取本月收藏…", 567, 485, 14, HubView.MUTED, false);
        else if (history) fortuneHistory(records, 0);
        else calendar(records, 0);
        ui.pop();
        ui.endAlpha();
        d.restore();
        String[] grades = {"上上签", "上吉签", "中平签", "小凶签", "下下签"};
        for (int i = 0; i < grades.length; i++) {
            float x = 258 + i * 122;
            d.circle(x + 3, 775, 3, ui.a(HubData.gradeColor(grades[i])));
            ui.text(grades[i], x + 15, 779, 10, 0xff808aa0, false);
        }
        fortuneSelection(records);
        ui.ungroup();
    }

    private void calendar(Map<LocalDate, HubData.Entry> records, float slide) {
        HubDraw d = ui.d;
        HubData data = ui.data;
        YearMonth month = ui.month;
        LocalDate selectedDate = ui.selectedDate;
        boolean dark = ui.dark;
        String weekdays = "日一二三四五六";
        for (int i = 0; i < 7; i++)
            ui.textCenter(
                    weekdays.substring(i, i + 1),
                    298 + i * 88,
                    273,
                    11,
                    i == 0 || i == 6 ? 0xffae92af : 0xff8c92a8,
                    false);
        int start = month.atDay(1).getDayOfWeek().getValue() % 7;
        int cells = ((start + month.lengthOfMonth() + 6) / 7) * 7, rows = cells / 7;
        float gap = 7, cellW = (618 - gap * 6) / 7, cellH = (449 - gap * (rows - 1)) / rows;
        for (int i = 0; i < cells; i++) {
            int day = i - start + 1;
            float x = 258 + (i % 7) * (cellW + gap), y = 292 + (i / 7) * (cellH + gap);
            if (day < 1 || day > month.lengthOfMonth()) continue;
            LocalDate date = month.atDay(day);
            HubData.Entry entry = records.get(date);
            boolean future = date.isAfter(data.today),
                    selected = date.equals(selectedDate),
                    today = date.equals(data.today);
            int grade = entry == null ? 0xffa0a7bb : HubData.gradeColor(entry.fortune().grade());
            boolean previousColors = d.literalColors(true);
            if (entry != null) {
                int[] colors =
                        dark
                                ? new int[] {
                                    0xff354058,
                                    0xff303d51,
                                    HubView.mix(grade, 0xff30394a, .74f),
                                    HubView.mix(grade, 0xff30394a, .44f)
                                }
                                : new int[] {
                                    0xffffffff,
                                    0xcfffffff,
                                    HubView.lighten(grade, .8f),
                                    HubView.lighten(grade, .43f)
                                };
                for (int index = 0; index < colors.length; index++)
                    colors[index] = ui.a(colors[index]);
                d.linearGradientRoundRect(
                        x, y, cellW, cellH, 11, 140, colors, new float[] {.08f, .4f, .69f, 1});
                d.insetBoxShadow(
                        x, y, cellW, cellH, 11, 0, 1, 0, 0, ui.a(dark ? 0x14d8e3f5 : 0xffffffff));
            } else
                d.roundRect(
                        x,
                        y,
                        cellW,
                        cellH,
                        11,
                        ui.a(
                                dark
                                        ? (future ? 0x3820283d : 0x5c202b40)
                                        : (future ? 0xfff5f5fa : 0xfff4f4fa)));
            float selection = ui.stateTransition("day-selected-" + date, selected ? 1 : 0, 160);
            int edge =
                    dark ? (future ? 0x0caabade : 0x20adbfde) : (future ? 0xffeeedf5 : 0xffe6e7f1);
            d.strokeRoundRect(
                    x + .5f,
                    y + .5f,
                    cellW - 1,
                    cellH - 1,
                    11,
                    1,
                    ui.a(HubView.mix(edge, dark ? 0xffc2a6e6 : 0xff8b6bb9, selection)));
            if (selection > 0) {
                d.boxShadow(
                        x,
                        y,
                        cellW,
                        cellH,
                        11,
                        0,
                        0,
                        0,
                        2,
                        ui.a(HubView.withAlpha(dark ? 0x2bb395d6 : 0x279a7bc3, selection)));
                d.boxShadow(
                        x,
                        y,
                        cellW,
                        cellH,
                        11,
                        0,
                        4,
                        9,
                        0,
                        ui.a(HubView.withAlpha(dark ? 0x38121932 : 0x198876aa, selection)));
            }
            float hover =
                    ui.transition(
                            "day-hover-" + date,
                            !future && ui.inside(x + slide, y, cellW, cellH) ? 1 : 0,
                            160);
            if (hover > 0)
                d.strokeRoundRect(
                        x + .5f,
                        y + .5f,
                        cellW - 1,
                        cellH - 1,
                        11,
                        1,
                        ui.a(HubView.withAlpha(dark ? 0xffb39ace : 0xffb396d8, hover)));
            d.literalColors(previousColors);
            ui.text(
                    Integer.toString(day),
                    x + 10,
                    y + 20,
                    12,
                    future ? 0xffacb2c2 : 0xff65728b,
                    false);
            if (today) ui.textRight("今天", x + cellW - 7, y + 18, 9, 0xff8f70ad, false);
            float slipH = Math.min(38, cellH - 26), slipW = slipH * .83f;
            if (!future) {
                d.save();
                d.translate(x + cellW / 2, y + cellH / 2 + slipH / 2 - 10);
                d.canvas().rotate(-8);
                ui.image(
                        "fortune-slip",
                        -slipW / 2,
                        -slipH / 2,
                        slipW,
                        slipH,
                        entry != null ? 1 : .14f);
                d.restore();
            }
            if (entry != null) {
                String label = entry.fortune().grade();
                float labelWidth = d.textWidth(label, 9, 400) + 6;
                previousColors = d.literalColors(true);
                d.roundRect(
                        x + cellW - 3 - labelWidth,
                        y + cellH - 17,
                        labelWidth,
                        14,
                        3,
                        ui.a(dark ? 0x9c172335 : 0x80ffffff));
                ui.textRight(
                        label,
                        x + cellW - 6,
                        y + cellH - 6,
                        9,
                        dark
                                ? HubView.mix(grade, 0xfff4edda, .48f)
                                : HubView.mix(grade, 0xff24352e, .1f),
                        false);
                d.literalColors(previousColors);
            }
            if (!future)
                ui.hit(
                        "day-" + date,
                        date + (entry == null ? " 未收藏" : " " + entry.fortune().title()),
                        x + slide,
                        y,
                        cellW,
                        cellH,
                        () -> ui.selectedDate = date);
        }
    }

    private void fortuneHistory(Map<LocalDate, HubData.Entry> records, float slide) {
        YearMonth month = ui.month;
        LocalDate selectedDate = ui.selectedDate;
        List<HubData.Entry> entries =
                records.values().stream()
                        .filter(e -> YearMonth.from(e.date()).equals(month))
                        .sorted(Comparator.comparing(HubData.Entry::date).reversed())
                        .toList();
        historyScroll =
                Math.max(0, Math.min(historyScroll, Math.max(0, entries.size() * 80 - 488)));
        if (entries.isEmpty()) {
            ui.icon("book-open", 547, 382, 30, 0xffa49bb3);
            ui.textCenter("这个月还没有签笺", 567, 443, 17, 0xff8c8495, true);
            ui.textCenter("已收藏的签会按抽签日期出现在这里。", 567, 474, 12, HubView.MUTED, false);
            return;
        }
        HubDraw d = ui.d;
        for (int i = 0; i < entries.size(); i++) {
            HubData.Entry entry = entries.get(i);
            float y = 256 + i * 80 - historyScroll;
            if (y + 70 < 252 || y > 746) continue;
            boolean selected = entry.date().equals(selectedDate);
            d.roundRect(258, y, 618, 70, 11, ui.a(selected ? 0xffeee7fb : 0x90ffffff));
            d.strokeRoundRect(258, y, 618, 70, 11, 1, ui.a(0xffe7e5f2));
            ui.image("fortune-slip", 270, y + 14, 32, 41, 1);
            ui.text(entry.fortune().title(), 319, y + 29, 13, 0xff55617b, true);
            ui.text(
                    entry.date().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
                            + "  "
                            + entry.time(),
                    319,
                    y + 51,
                    10,
                    0xff8d96ac,
                    false);
            ui.textRight(
                    entry.fortune().grade(),
                    835,
                    y + 37,
                    12,
                    HubData.gradeColor(entry.fortune().grade()),
                    false);
            ui.icon("chevron-right", 848, y + 26, 16, 0xff9989b2);
            float top = Math.max(252, y), bottom = Math.min(746, y + 70);
            if (bottom > top)
                ui.hit(
                        "history-" + entry.date(),
                        entry.date() + " " + entry.fortune().title(),
                        258 + slide,
                        top,
                        618,
                        bottom - top,
                        () -> ui.selectedDate = entry.date());
        }
    }

    private void fortuneSelection(Map<LocalDate, HubData.Entry> records) {
        HubDraw d = ui.d;
        HubData data = ui.data;
        LocalDate selectedDate = ui.selectedDate;
        boolean dark = ui.dark, live = ui.live();
        HubData.Entry entry = records.get(selectedDate);
        ui.glass(HubView.RIGHT, 174, 310, 450, 22, 0xf2fffdf9, 0xc7ffffff);
        ui.text(selectedDate.format(HubView.DATE), 939, 204, 11, 0xff85879a, false);
        if (selectedDate.equals(data.today)) ui.pill("今天", 1160, 187, 0xbceffff2, 0xff25816a, true);
        if (entry == null) {
            ui.image("fortune-slip", 1039, 314, 76, 97, .3f);
            ui.textCenter(
                    calendarInitialLoading() ? "正在读取这一天的签笺…" : "这一天，没有留下签笺",
                    1077,
                    456,
                    15,
                    0xff8c8495,
                    true);
            ui.wrap(
                    calendarInitialLoading() ? "等待服务器确认收藏记录。" : "打开藏签匣时，背包中属于你的已解签会按对应日期归档。",
                    957,
                    491,
                    240,
                    12,
                    22,
                    0xffa39bab,
                    false);
            if (selectedDate.equals(data.today) && fortuneState < 3)
                ui.textCenter(stateLabel() + " · 尚未归档", 1077, 562, 11, 0xffa39bab, false);
        } else {
            HubData.Fortune f = entry.fortune();
            paper(937, 219, 280, 331);
            boolean previousColors = d.literalColors(true);
            d.save();
            d.translate(1077, 262.5f);
            d.canvas().rotate(-6);
            d.boxShadow(-24, -24.5f, 48, 49, 13, 0, 3, 8, 0, ui.a(dark ? 0x40161126 : 0x29b69c64));
            d.linearGradientRoundRect(
                    -24,
                    -24.5f,
                    48,
                    49,
                    13,
                    145,
                    new int[] {
                        ui.a(dark ? 0x33c7a360 : 0x94f2deac), ui.a(dark ? 0x12e3c47d : 0xfffff7e6)
                    },
                    new float[] {0, 1});
            d.insetBoxShadow(
                    -24, -24.5f, 48, 49, 13, 1, 1, 1, 0, ui.a(dark ? 0x1ff1ddb5 : 0xffffffff));
            d.strokeRoundRect(-23.5f, -24, 47, 48, 13, 1, ui.a(dark ? 0x2bdac18d : 0x99ffffff));
            ui.image("fortune-slip", -15.5f, -21.5f, 31, 43, 1);
            d.restore();
            d.literalColors(previousColors);
            ui.textCenter(
                    f.grade() + " · 第" + f.id() + "签",
                    1077,
                    310,
                    11,
                    HubData.gradeColor(f.grade()),
                    false);
            ui.trackedCenter(f.title(), 1077, 348, 25, 3, 0xff615b4f, true, true);
            previousColors = d.literalColors(true);
            d.line(961, 365, 1193, 365, 1, ui.a(dark ? 0x29d3bd8a : 0x36c9b78d));
            d.literalColors(previousColors);
            for (int i = 0; i < f.poem().size(); i++)
                ui.trackedCenter(
                        f.poem().get(i), 1077, 391 + i * 30.55f, 13, 1.6f, 0xff817967, false, true);
            previousColors = d.literalColors(true);
            d.line(961, 494, 1193, 494, 1, ui.a(dark ? 0x29d3bd8a : 0x36c9b78d));
            d.literalColors(previousColors);
            ui.text(ui.fit(ui.playerName, 190, 9, false) + " 所得", 960, 526, 9, 0xffae9565, false);
            ui.textRight(entry.time(), 1195, 526, 9, 0xffae9565, false);
            ui.icon("check-check", 940, 582, 14, 0xff7f8f87);
            ui.text("已收藏", 962, 594, 10, 0xff7f8f87, false);
            ui.button(
                    "read-selected",
                    "阅读解签  →",
                    1093,
                    572,
                    123,
                    35,
                    true,
                    true,
                    () -> ui.openReading(entry));
        }
        ui.glass(HubView.RIGHT, 640, 310, 160, 19, 0x99e6eaff, 0xd1f6edff);
        ui.glass(938, 656, 53, 56, 14, 0xd4ffffff, 0x45ffffff);
        ui.image("collection-chest", 936, 653, 57, 61, 1);
        HubData.Entry display = live ? data.featured : records.get(displayDate);
        ui.text("手册展示", 1004, 668, 10, 0xff7b869b, false);
        if (live && data.featured != null)
            ui.iconButton(
                    "clear-feature",
                    "清除手册展示",
                    "x",
                    1187,
                    651,
                    24,
                    () -> {
                        JsonObject p = ui.mutation("fortunes");
                        p.addProperty("date", "");
                        ui.request("fortunes", "feature", p);
                    },
                    !ui.pendingModules.contains("fortunes"));
        ui.text(
                display == null ? "还没有选择签笺" : display.fortune().title(),
                1004,
                691,
                13,
                0xff625c80,
                true);
        ui.text(
                display == null ? "选择一张已收藏的签" : HubView.shortDate(display.date()) + " · 已收藏",
                1004,
                709,
                10,
                0xff9290aa,
                false);
        boolean current = Objects.equals(displayDate, selectedDate) && entry != null;
        ui.button(
                "display-selected",
                ui.pendingModules.contains("fortunes")
                        ? "等待服务器确认…"
                        : current ? "当前选择已展示  ✓" : "展示选中的签  ↗",
                938,
                725,
                278,
                33,
                false,
                entry != null && !current && !ui.pendingModules.contains("fortunes"),
                () -> displaySelected(entry));
        ui.textCenter("仅用于手册，方块展示在游戏内设置", 1077, 783, 9, 0xff948ba6, false);
    }

    private void paper(float x, float y, float w, float h) {
        HubDraw d = ui.d;
        boolean dark = ui.dark;
        boolean previousColors = d.literalColors(true);
        d.boxShadow(x, y, w, h, 14, 0, 4, 12, 0, ui.a(dark ? 0x261a152a : 0x088c704e));
        d.linearGradientRoundRect(
                x,
                y,
                w,
                h,
                14,
                120,
                new int[] {
                    ui.a(dark ? 0xff3e3932 : 0xfffff9ec), ui.a(dark ? 0xff34343b : 0xfffffef9)
                },
                new float[] {0, 1});
        int sheen = dark ? 0x30b39049 : 0x52f2dda8;
        d.radialGradientRoundRect(
                x,
                y,
                w,
                h,
                14,
                x + w * .5f,
                y + h * .04f,
                w * .5f * 1.41421356f,
                h * .96f * 1.41421356f,
                new int[] {ui.a(sheen), sheen & 0xffffff},
                new float[] {0, .7f});
        d.insetBoxShadow(x, y, w, h, 14, 0, 1, 2, 0, ui.a(dark ? 0x1aeadcc1 : 0xffffffff));
        d.strokeRoundRect(
                x + .5f, y + .5f, w - 1, h - 1, 14, 1, ui.a(dark ? 0x3dc7ad71 : 0xffe9dcbf));
        d.strokeRoundRect(x + 6, y + 6, w - 12, h - 12, 9, 1, ui.a(dark ? 0x1fdcc08c : 0x2bcdb57e));
        d.literalColors(previousColors);
    }

    private void shiftMonth(int amount) {
        HubData data = ui.data;
        YearMonth target = ui.month.plusMonths(amount);
        if (target.isAfter(YearMonth.from(data.today))) return;
        ui.month = target;
        monthDirection = Integer.signum(amount);
        monthAt = System.nanoTime();
        historyScroll = 0;
        chooseLatestOnCalendar = true;
        ui.selectedDate =
                archive().keySet().stream()
                        .filter(date -> YearMonth.from(date).equals(target))
                        .max(Comparator.naturalOrder())
                        .orElse(target.atDay(1));
        calendarRequest();
    }

    private void displaySelected(HubData.Entry entry) {
        if (entry == null
                || !(ui.winter ? confirmedArchived(entry) : archive().containsKey(entry.date()))
                || Objects.equals(displayDate, entry.date())) return;
        if (ui.live()) {
            JsonObject p = ui.mutation("fortunes");
            p.addProperty("date", entry.date().toString());
            ui.request("fortunes", "feature", p);
            return;
        }
        displayDate = entry.date();
        ui.feedbackSuccess("已更新手册展示的签笺 · 本地演示");
    }
}
