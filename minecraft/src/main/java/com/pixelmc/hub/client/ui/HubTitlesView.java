/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.ui;

import com.google.gson.JsonObject;
import com.pixelmc.hub.client.render.HubDraw;

import io.github.humbleui.skija.Matrix33;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Titles catalogue, detail preview, and winter album page managed as a {@link HubBuiltInPage}. */
final class HubTitlesView implements HubBuiltInPage {
    static final float COLLECTION_LEFT = 215,
            COLLECTION_WIDTH = 1016,
            COLLECTION_TOP = 123.3f,
            ALBUM_SILL = 332.4f,
            ALBUM_SPREAD = 384.4f,
            ALBUM_END = 777;
    private static final float ALBUM_SPLIT = COLLECTION_LEFT + COLLECTION_WIDTH * 1.46f / 2.46f;

    private final HubView ui;
    private final HubView.NavigationEntry entry;
    private final HubMotion.Navigation nav = new HubMotion.Navigation();

    private boolean titleSortOpen;
    private int filter, searchCaret;
    private String query = "";
    private boolean searchSelectAll;
    private String albumListKey, lastAlbumTitle;
    private long trayAt, inkAt, albumStatusAt, titleSortAt, albumVisibleAt, albumEquippedAt;
    private String albumStatus, albumEquippedState;
    private Boolean albumVisibleState;
    private int trayPage, trayDirection;
    private float albumRecordScroll, albumRecordOverflow;

    HubTitlesView(HubView ui) {
        this.ui = ui;
        this.entry = ui.builtInEntry(1, "我的称号", "tag", 0xffbf72f3, 0xff6f34d5);
    }

    @Override
    public int index() {
        return 1;
    }

    @Override
    public String id() {
        return "titles";
    }

    @Override
    public String label() {
        return "我的称号";
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
        ui.titleScroll = 0;
        lastAlbumTitle = null;
        albumStatus = null;
        albumVisibleState = null;
        albumEquippedState = null;
    }

    @Override
    public void onLeave(int nextDestination) {
        titleSortOpen = false;
        ui.titleScroll = 0;
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
        titles();
    }

    @Override
    public boolean dismissOverlay() {
        if (ui.winter && titleSortOpen) {
            titleSortOpen = false;
            ui.focusId = "sort";
            return true;
        }
        return false;
    }

    @Override
    public boolean pointerDown(float x, float y, float pageX, float pageY, boolean shift) {
        if (ui.winter && titleSortOpen) {
            boolean insideMenu =
                    ui.anyHitMatches(
                            x, y, id -> id.equals("sort") || id.startsWith("sort-option-"));
            if (!insideMenu) titleSortOpen = false;
        }
        return false;
    }

    @Override
    public boolean scroll(float pageX, float pageY, double amount) {
        if (ui.winter) {
            if (pageX >= ALBUM_SPLIT
                    && pageX <= COLLECTION_LEFT + COLLECTION_WIDTH
                    && pageY >= ALBUM_SPREAD
                    && pageY < ALBUM_END - 90
                    && albumRecordOverflow > 0) {
                albumRecordScroll =
                        HubView.clamp(
                                albumRecordScroll - (float) amount * 46, 0, albumRecordOverflow);
                return true;
            }
            return false;
        }
        if (pageX >= 230 && pageX <= 908 && pageY >= 272 && pageY <= 796) {
            ui.titleScroll -= amount * 52;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int mods, String focused) {
        return handleSearchKey(key, mods, focused);
    }

    @Override
    public boolean charTyped(char c, int mods, String focused) {
        pasteText(Character.toString(c), focused);
        return true;
    }

    @Override
    public boolean textFocused(String focused) {
        return "search".equals(focused);
    }

    @Override
    public String selectedText(String focused) {
        return "search".equals(focused) && searchSelectAll ? query : "";
    }

    @Override
    public String cutText(String focused) {
        String value = selectedText(focused);
        if (!value.isEmpty()) {
            query = "";
            searchCaret = 0;
            searchSelectAll = false;
        }
        return value;
    }

    @Override
    public void pasteText(String value, String focused) {
        pasteSearchText(value);
    }

    boolean handleSortMenuKey(int keyCode) {
        if (!(ui.winter && titleSortOpen)) return false;
        if (keyCode == 258) {
            titleSortOpen = false;
            ui.focusId = "sort";
            return false;
        }
        if (keyCode == 264 || keyCode == 265 || keyCode == 268 || keyCode == 269) {
            ui.keyboardFocus = true;
            ui.focusId = "sort-option-" + (keyCode == 264 || keyCode == 269 ? 1 : 0);
            return true;
        }
        return false;
    }

    int titleCount() {
        return (int) ui.data.titles.stream().filter(HubData.Title::owned).count();
    }

    List<HubData.Title> titleList() {
        List<HubData.Title> list =
                new ArrayList<>(
                        ui.data.titles.stream()
                                .filter(
                                        t ->
                                                (filter == 1
                                                                || (filter == 0
                                                                        ? t.owned()
                                                                        : !t.owned()))
                                                        && (t.name() + t.category())
                                                                .contains(query.strip()))
                                .toList());
        if (ui.sort == 0)
            list.sort(
                    ui.live()
                            ? Comparator.comparingLong(HubData.Title::acquiredAt).reversed()
                            : Comparator.comparing(HubData.Title::date).reversed());
        else {
            Collator collator = Collator.getInstance(Locale.CHINA);
            list.sort((a, b) -> collator.compare(a.name(), b.name()));
        }
        return list;
    }

    boolean privacyKnown() {
        String equipped = ui.equipped;
        return !ui.live()
                || ui.catalogueComplete
                        && (equipped == null
                                || equipped.isEmpty()
                                || ui.data.title(equipped) != null);
    }

    boolean canChangeVisibility() {
        String equipped = ui.equipped;
        return !ui.live()
                || privacyKnown()
                        && ui.ready("titles")
                        && equipped != null
                        && !equipped.isEmpty()
                        && !ui.pendingModules.contains("titles");
    }

    void changeVisibility() {
        if (ui.live()) {
            if (!canChangeVisibility()) return;
            JsonObject p = ui.mutation("titles");
            p.addProperty("visible", !ui.visible);
            ui.request("titles", "visibility", p);
            return;
        }
        ui.visible = !ui.visible;
        ui.feedbackSuccess(ui.visible ? "已开启称号展示 · 本地演示" : "展示已隐藏，佩戴状态保留");
    }

    void renderTitleNameModal(float modalX, float modalY, float modalW) {
        HubData.Title title = ui.data.title(ui.selectedTitle);
        if (title == null) return;
        HubDraw d = ui.d;
        float left = modalX + 28, right = modalX + modalW - 28, x = left, y = modalY + 137;
        for (HubData.Glyph glyph : title.glyphs()) {
            float advance = d.textWidth(glyph.text(), 18, glyph.bold() ? 700 : 400);
            if (x + advance > right) {
                x = left;
                y += 29;
            }
            d.richGlyph(
                    glyph.text(),
                    x,
                    y,
                    18,
                    glyph.color(),
                    glyph.bold(),
                    glyph.italic(),
                    glyph.underline(),
                    glyph.strike());
            x += advance + 1;
        }
    }

    void renderTitleDisplayModal(float modalX, float modalY, float modalW) {
        HubData data = ui.data;
        String[] keys = {"head", "chat", "tab"}, labels = {"头顶称号", "聊天称号", "玩家列表称号"};
        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            float y = modalY + 122 + i * 66;
            boolean allowed = HubData.bool(data.globalDisplay, key, true),
                    shown = HubData.bool(data.displayPreferences, key, true);
            ui.text(labels[i], modalX + 28, y, 14, HubView.INK, true);
            ui.text(
                    allowed ? "使用个人展示偏好" : "管理员已关闭此位置",
                    modalX + 28,
                    y + 22,
                    11,
                    HubView.MUTED,
                    false);
            ui.toggle(
                    "title-location-" + key,
                    shown,
                    modalX + modalW - 61,
                    y - 12,
                    () -> {
                        if (ui.pendingModules.contains("titles")) {
                            ui.notifyUser("正在等待服务器确认");
                            return;
                        }
                        JsonObject p = ui.mutation("titles");
                        for (String location : keys)
                            p.addProperty(
                                    location,
                                    location.equals(key)
                                            ? !shown
                                            : HubData.bool(
                                                    data.displayPreferences, location, true));
                        ui.request("titles", "displayPreferences", p);
                    });
        }
    }

    private int winterTone(int day, int night) {
        return ui.dark ? night : day;
    }

    private void winterAlbum() {
        HubDraw d = ui.d;
        HubData data = ui.data;
        boolean live = ui.live(), catalogueComplete = ui.catalogueComplete;
        long now = ui.now;
        ui.group(0, 240);
        boolean previous = d.literalColors(true);
        ui.collectionHeading(
                "我的称号",
                live && (!ui.ready("titles") || !catalogueComplete)
                        ? ui.moduleLabel("titles")
                        : "已收藏  " + titleCount() + " / " + data.titles.size());
        if (live && !ui.ready("titles")) {
            ui.unavailable("titles");
        } else {
            List<HubData.Title> list = titleList();
            String key = filter + "\n" + ui.sort + "\n" + query.strip();
            boolean changed = albumListKey != null && !albumListKey.equals(key);
            HubData.Title selected = changed ? null : data.title(ui.selectedTitle);
            int index = list.indexOf(selected);
            if (index < 0 && !list.isEmpty()) {
                index = 0;
                selected = list.get(0);
                ui.selectedTitle = selected.id();
            }
            if (index < 0) selected = null;
            int pageIndex = Math.max(0, index) / 6;
            if (changed || pageIndex != trayPage) {
                trayAt = now;
                trayDirection = changed ? 0 : Integer.compare(pageIndex, trayPage);
            }
            String id = selected == null ? "" : selected.id();
            if (!Objects.equals(lastAlbumTitle, id)) {
                inkAt = now;
                albumRecordScroll = 0;
                lastAlbumTitle = id;
            }
            albumListKey = key;
            trayPage = pageIndex;
            float bottom = selected == null ? ALBUM_SPREAD + 204 : ALBUM_END;
            d.boxShadow(
                    COLLECTION_LEFT,
                    COLLECTION_TOP,
                    COLLECTION_WIDTH,
                    bottom - COLLECTION_TOP,
                    21,
                    0,
                    5,
                    10,
                    -7,
                    0x66171f16);
            d.save();
            d.clipRoundRect(
                    COLLECTION_LEFT, COLLECTION_TOP, COLLECTION_WIDTH, bottom - COLLECTION_TOP, 21);
            d.rect(
                    COLLECTION_LEFT,
                    COLLECTION_TOP,
                    COLLECTION_WIDTH,
                    bottom - COLLECTION_TOP,
                    winterTone(0xfffbf6e8, 0xff343c34));
            HubCollectionArt.cover(
                    d,
                    true,
                    COLLECTION_LEFT,
                    COLLECTION_TOP,
                    COLLECTION_WIDTH,
                    ALBUM_SILL - COLLECTION_TOP);
            albumInscription(selected);
            albumToolbar();
            if (selected != null) {
                albumTray(list, pageIndex);
                albumRecord(selected);
            } else {
                int muted = d.winterPaper().muted();
                boolean searching = !query.isBlank();
                String emptyIcon =
                        !catalogueComplete || (!searching && filter != 2)
                                ? "tag"
                                : searching ? "search" : "check";
                String emptyTitle =
                        !catalogueComplete
                                ? "正在读取称号目录…"
                                : searching
                                        ? "没有找到这个称号"
                                        : filter == 2 ? "已集齐全部称号" : filter == 0 ? "暂未获得称号" : "暂无称号";
                String emptyHint =
                        searching
                                ? "换个关键词，或查看全部称号。"
                                : filter == 2
                                        ? "你已经收藏了当前全部称号。"
                                        : filter == 0
                                                ? "继续探索旅程解锁更多称号，也可以先查看全部称号。"
                                                : "当前目录里还没有可展示的称号。";
                ui.icon(emptyIcon, 720, ALBUM_SPREAD + 27, 27, muted);
                ui.serifCenter(emptyTitle, 734, ALBUM_SPREAD + 88, 18, muted, true);
                if (catalogueComplete) {
                    ui.textCenter(emptyHint, 734, ALBUM_SPREAD + 117, 12, muted, false);
                    if (searching || filter != 1)
                        ui.button(
                                "reset-filter",
                                "查看全部",
                                683,
                                ALBUM_SPREAD + 135,
                                102,
                                34,
                                false,
                                true,
                                () -> {
                                    filter = 1;
                                    query = "";
                                    searchCaret = 0;
                                });
                }
            }
            d.restore();
            HubCollectionArt.brooch(d, 618, bottom + (827 - bottom - 30) / 2, 210, 30);
            if (titleSortOpen) albumSortPicker();
        }
        d.literalColors(previous);
        ui.ungroup();
    }

    private float albumEase(long delay, int ms) {
        return ui.reducedMotion
                ? 1
                : HubMotion.easeOut(
                        HubView.progress(ui.now - inkAt - delay * 1_000_000L, ms * 1_000_000L));
    }

    private void albumInscription(HubData.Title t) {
        HubDraw d = ui.d;
        long now = ui.now;
        int ink = winterTone(0xff304a3d, 0xfff3e8cf), sub = winterTone(0xff445646, 0xffc6cdb5);
        float mid = (COLLECTION_TOP + ALBUM_SILL) / 2, x = COLLECTION_LEFT + 140;
        d.radialGradientRoundRect(
                COLLECTION_LEFT + 47,
                mid + 28,
                62,
                16,
                0,
                COLLECTION_LEFT + 78,
                mid + 36,
                38,
                9,
                new int[] {0x40233f35, 0x00233f35},
                new float[] {0, .68f});
        HubHead.draw(d, COLLECTION_LEFT + 55, mid - 23.5f, 46.7f, -23);
        String monoName = ui.playerName;
        if (d.textWidthMono(monoName, 18, .45f) > 345) {
            int end = monoName.length();
            while (end > 0 && d.textWidthMono(monoName.substring(0, end) + "…", 18, .45f) > 345)
                end--;
            monoName = monoName.substring(0, end) + "…";
        }
        d.textMono(monoName, x, mid - (t == null ? 2 : 30), 18, ink, .45f);
        boolean worn = t != null && Objects.equals(ui.equipped, t.id());
        if (t != null) {
            float e = albumEase(0, 220);
            ui.beginAlpha(.38f + .62f * e, x - 2, mid - 24, 425, 66);
            float baseline = mid + 18 + 4 * (1 - e);
            if (ui.live()) {
                ui.titleLabel(t, x, baseline, 42.67f, 1.4f, false, false, 370, true, 700);
            } else {
                boolean previous = d.literalColors(false);
                try {
                    ui.paintDemoTitle(t, x, baseline, 42.67f, 1.4f, 700, true);
                } finally {
                    d.literalColors(previous);
                }
            }
            ui.endAlpha();
        }
        float y = mid + (t == null ? 18 : 39);
        boolean visible = ui.visible;
        String statusKey = (t == null ? "" : t.id()) + "|" + worn + "|" + visible;
        if (!Objects.equals(statusKey, albumStatus)) {
            albumStatus = statusKey;
            albumStatusAt = now;
        }
        float press =
                ui.reducedMotion
                        ? 1
                        : HubMotion.easeOut(HubView.progress(now - albumStatusAt, 230_000_000L));
        ui.beginAlpha(.6f + .4f * press, x - 3, y - 14, 19, 20);
        ui.push(
                HubDraw.cssPerspective(
                        x + 6.5f,
                        y - 3.5f,
                        800,
                        0,
                        0,
                        -4 * (1 - press),
                        1 + .12f * (1 - press),
                        0,
                        0));
        ui.icon(worn ? visible ? "check" : "eye-off" : "tag", x, y - 10, 13, sub);
        ui.pop();
        ui.endAlpha();
        String status = t == null ? "称号收藏匣" : worn ? visible ? "正在佩戴" : "已佩戴 · 已隐藏" : "试戴预览";
        ui.text(status, x + 19, y + 1, 11, sub, false);
        float line = x + 26 + d.textWidth(status, 11, false);
        d.linearGradientRoundRect(
                line,
                y - 3,
                29,
                1,
                0,
                90,
                new int[] {HubView.withAlpha(sub, .65f), sub & 0xffffff},
                new float[] {0, 1});
    }

    private void albumToolbar() {
        HubDraw d = ui.d;
        String focusId = ui.focusId;
        long now = ui.now;
        int sort = ui.sort;
        float x = COLLECTION_LEFT + 22, y = ALBUM_SILL + 10;
        int sill = winterTone(0xffded6bd, 0xff38483e), ink = winterTone(0xff354c40, 0xfff0eddc);
        int field = winterTone(0xfff9f6e9, 0xff283b32), line = winterTone(0xff9ca38e, 0xff748576);
        d.rect(COLLECTION_LEFT, ALBUM_SILL, COLLECTION_WIDTH, 52, sill);
        HubWinterSheet.grain(d, COLLECTION_LEFT, ALBUM_SILL, COLLECTION_WIDTH, 52, 0, 1);
        d.line(
                COLLECTION_LEFT,
                ALBUM_SILL,
                COLLECTION_LEFT + COLLECTION_WIDTH,
                ALBUM_SILL,
                1,
                HubView.withAlpha(ink, .25f));
        d.line(
                COLLECTION_LEFT,
                ALBUM_SPREAD - 1,
                COLLECTION_LEFT + COLLECTION_WIDTH,
                ALBUM_SPREAD - 1,
                1,
                HubView.withAlpha(ink, .25f));
        d.roundRect(x, y, 178, 32, 11, field);
        d.strokeRoundRect(x + .5f, y + .5f, 177, 31, 11, 1, line);
        String[] filters = {"已拥有", "全部", "未获得"};
        float px = x + 3;
        for (int i = 0; i < filters.length; i++) {
            final int choice = i;
            float w = i == 1 ? 48 : 60;
            boolean active = filter == i;
            ui.plainControl(
                    "filter-" + i,
                    filters[i],
                    null,
                    filters[i],
                    px,
                    y + 3,
                    w,
                    26,
                    active ? winterTone(0xfffff9e4, 0xff233a30) : ink,
                    active ? winterTone(0xff365a49, 0xffc5d5b5) : 0,
                    true,
                    () -> filter = choice);
            px += w + 2;
        }
        float sx = COLLECTION_LEFT + COLLECTION_WIDTH - 22 - 100 - 9 - 200;
        d.roundRect(sx, y, 200, 32, 10, field);
        d.strokeRoundRect(sx + .5f, y + .5f, 199, 31, 10, 1, line);
        ui.icon("search", sx + 10, y + 8, 16, ink);
        float tx = sx + 33, available = query.isEmpty() ? 153 : 133;
        d.save();
        d.clipRoundRect(tx, y + 2, available, 29, 0);
        int caretIndex = Math.min(searchCaret, query.length());
        float caret = d.textWidth(query.substring(0, caretIndex), 12, false);
        float start = tx - ("search".equals(focusId) ? Math.max(0, caret - available + 2) : 0);
        if (searchSelectAll && "search".equals(focusId))
            d.rect(start, y + 6, d.textWidth(query, 12, false), 20, HubView.withAlpha(ink, .22f));
        ui.text(
                query.isEmpty() ? "搜索称号" : query,
                start,
                y + 21,
                12,
                query.isEmpty() ? HubView.withAlpha(ink, .8f) : ink,
                false);
        if ("search".equals(focusId) && !searchSelectAll && (now / 500_000_000L) % 2 == 0)
            d.line(start + caret, y + 7, start + caret, y + 25, 1, ink);
        d.restore();
        ui.hit(
                "search",
                "搜索称号",
                sx,
                y,
                200,
                32,
                () -> {
                    searchCaret = query.length();
                    searchSelectAll = false;
                });
        if (!query.isEmpty())
            ui.plainControl(
                    "clear-search",
                    "清空搜索",
                    "x",
                    "",
                    sx + 169,
                    y + 3,
                    26,
                    26,
                    ink,
                    0,
                    true,
                    () -> {
                        query = "";
                        searchCaret = 0;
                        searchSelectAll = false;
                        ui.focusId = "search";
                    });
        float sortX = COLLECTION_LEFT + COLLECTION_WIDTH - 122;
        d.roundRect(sortX, y, 100, 32, 10, field);
        d.strokeRoundRect(sortX + .5f, y + .5f, 99, 31, 10, 1, line);
        if (ui.inside(sortX, y, 100, 32))
            d.roundRect(sortX + 1, y + 1, 98, 30, 9, HubView.withAlpha(ink, .06f));
        ui.text(sort == 0 ? "最近获得" : "名称排序", sortX + 11, y + 21, 12, ink, false);
        ui.icon(titleSortOpen ? "chevron-up" : "chevron-down", sortX + 76, y + 10, 13, ink);
        ui.hit(
                "sort",
                "称号排序",
                sortX,
                y,
                100,
                32,
                () -> {
                    titleSortOpen = !titleSortOpen;
                    if (titleSortOpen) titleSortAt = now;
                    ui.focusId = titleSortOpen ? "sort-option-" + sort : "sort";
                });
    }

    private void albumSortPicker() {
        HubDraw d = ui.d;
        int sort = ui.sort;
        float x = COLLECTION_LEFT + COLLECTION_WIDTH - 150, y = ALBUM_SILL + 46;
        int fill = winterTone(0xfffbf6e8, 0xff343c34), ink = winterTone(0xff35463a, 0xfff3e8cc);
        float open =
                ui.reducedMotion
                        ? 1
                        : HubMotion.easeOut(HubView.progress(ui.now - titleSortAt, 140_000_000L));
        ui.beginAlpha(.38f + .62f * open, x - 14, y - 14, 156, 108);
        ui.push(Matrix33.makeTranslate(0, 4 * (1 - open)));
        d.boxShadow(x, y, 128, 74, 12, 0, 5, 14, 0, 0x26122b20);
        HubWinterSheet.panel(d, x, y, 128, 74, 12, fill, 1);
        for (int i = 0; i < 2; i++) {
            final int choice = i;
            boolean selected = i == sort;
            String label = i == 0 ? "最近获得" : "名称排序";
            int tint = selected ? winterTone(0xfffff9e4, 0xff233a30) : ink;
            float row = y + 4 + 33 * i;
            if (selected || ui.inside(x + 4, row, 120, 33))
                d.roundRect(
                        x + 4,
                        row,
                        120,
                        33,
                        8,
                        selected
                                ? winterTone(0xff365a49, 0xffc5d5b5)
                                : HubView.withAlpha(ink, .10f));
            ui.text(label, x + 14, row + 21, 12, tint, false);
            if (selected) ui.icon("check", x + 101, row + 10, 13, tint);
            ui.hit("sort-option-" + i, label, x + 4, row, 120, 33, () -> chooseTitleSort(choice));
        }
        ui.pop();
        ui.endAlpha();
    }

    private void albumTray(List<HubData.Title> list, int pageIndex) {
        HubDraw d = ui.d;
        long now = ui.now;
        boolean reducedMotion = ui.reducedMotion;
        String equipped = ui.equipped;
        if (!Objects.equals(equipped, albumEquippedState)) {
            if (albumEquippedState != null) albumEquippedAt = now;
            albumEquippedState = equipped;
        }
        float w = ALBUM_SPLIT - COLLECTION_LEFT,
                pad = 20.32f,
                gridX = COLLECTION_LEFT + pad,
                top = ALBUM_SPREAD + 8;
        float cw = (w - 2 * pad - 12) / 2, rh = (ALBUM_END - top - 44) / 3;
        int thread = winterTone(0x58d5ca9e, 0x4dabc0a2),
                ink = winterTone(0xffe0e6cf, 0xffcad9c8),
                gold = winterTone(0xffedcf87, 0xffe5c788);
        d.linearGradientRoundRect(
                COLLECTION_LEFT,
                ALBUM_SPREAD,
                w,
                ALBUM_END - ALBUM_SPREAD,
                0,
                115,
                new int[] {winterTone(0xff456453, 0xff2d4940), winterTone(0xff334f43, 0xff233a33)},
                new float[] {0, 1});
        d.radialGradientRoundRect(
                COLLECTION_LEFT,
                ALBUM_SPREAD,
                w,
                ALBUM_END - ALBUM_SPREAD,
                0,
                COLLECTION_LEFT + w * .16f,
                ALBUM_SPREAD + 35,
                w * .9f,
                390,
                new int[] {0x1cc2d1a9, 0x00c2d1a9},
                new float[] {0, .76f});
        HubWinterSheet.grain(d, COLLECTION_LEFT, ALBUM_SPREAD, w, ALBUM_END - ALBUM_SPREAD, 0, 1);
        int first = pageIndex * 6, end = Math.min(first + 6, list.size());
        float e =
                reducedMotion ? 1 : HubMotion.easeOut(HubView.progress(now - trayAt, 200_000_000L));
        ui.beginAlpha(
                (trayDirection == 0 ? .45f : .55f) + (trayDirection == 0 ? .55f : .45f) * e,
                COLLECTION_LEFT,
                top,
                w,
                3 * rh);
        ui.push(Matrix33.makeTranslate(trayDirection * 8 * (1 - e), 0));
        for (int i = first; i < end; i++) {
            int slot = i - first;
            float x = gridX + slot % 2 * (cw + 12), y = top + slot / 2 * rh;
            HubData.Title t = list.get(i);
            if (slot >= 2) d.line(x, y, x + cw, y, 1, thread);
            String subtitle = t.category() + " · " + t.rarity();
            float nameWidth =
                    Math.min(
                            139,
                            Math.max(
                                    d.textWidthSerif(t.name(), 20, true),
                                    d.textWidth(subtitle, 11, false)));
            float mountSize = Math.min(90.42f, rh - 20), gap = 12;
            float left = x + (cw - mountSize - gap - nameWidth) / 2,
                    cy = y + rh / 2,
                    cx = left + mountSize / 2;
            d.linearGradientRoundRect(
                    left + mountSize * .03f,
                    cy - mountSize * .47f,
                    mountSize * .94f,
                    mountSize * .94f,
                    mountSize * .47f,
                    155,
                    new int[] {0x661c332b, 0x3062806b},
                    new float[] {0, 1});
            d.strokeOval(
                    left + mountSize * .03f,
                    cy - mountSize * .47f,
                    mountSize * .94f,
                    mountSize * .94f,
                    1,
                    0x3d10281f);
            boolean selected = t.id().equals(ui.selectedTitle),
                    worn = Objects.equals(t.id(), equipped);
            if (selected)
                HubWinterSheet.stitchedCircle(d, cx, cy, mountSize * .47f + 2, gold, true);
            float hover =
                    ui.transition("album-hover-" + t.id(), ui.inside(x, y, cw, rh) ? 1 : 0, 160);
            d.save();
            d.canvas().concat(Matrix33.makeRotate(reducedMotion ? 0 : -2.5f * hover, cx, cy));
            float size = mountSize * .86f;
            HubCollectionArt.pin(
                    d,
                    HubCollectionArt.styleForBadge(t.icon()),
                    t.icon(),
                    cx - size / 2,
                    cy - size / 2,
                    size,
                    !t.owned(),
                    1);
            d.restore();
            float caption = left + mountSize + gap, ty = cy - (worn || !t.owned() ? 11 : 1);
            ui.serif(
                    ui.fitTracked(t.name(), Math.min(148, x + cw - caption - 2), 20, 0, 700, true),
                    caption,
                    ty,
                    20,
                    selected ? gold : 0xfffff8de,
                    true);
            ui.text(
                    ui.fit(subtitle, x + cw - caption, 11, false),
                    caption,
                    ty + 21,
                    11,
                    ink,
                    false);
            if (worn || !t.owned()) {
                float impress =
                        worn && !reducedMotion
                                ? HubMotion.easeOut(
                                        HubView.progress(now - albumEquippedAt, 230_000_000L))
                                : 1;
                if (worn && impress < 1) {
                    ui.beginAlpha(.6f + .4f * impress, caption - 4, ty + 25, 56, 20);
                    ui.push(
                            HubDraw.cssPerspective(
                                    caption + 22,
                                    ty + 34.5f,
                                    800,
                                    0,
                                    0,
                                    -4 * (1 - impress),
                                    1 + .12f * (1 - impress),
                                    0,
                                    0));
                }
                ui.icon(worn ? "check" : "lock", caption, ty + 29, 11, gold);
                ui.text(worn ? "已佩戴" : "未获得", caption + 15, ty + 39, 10, gold, false);
                if (worn && impress < 1) {
                    ui.pop();
                    ui.endAlpha();
                }
            }
            ui.hit("album-pin-" + t.id(), t.name(), x, y, cw, rh, () -> ui.selectedTitle = t.id());
        }
        ui.pop();
        ui.endAlpha();
        float y = ALBUM_END - 44;
        d.line(gridX, y, gridX + cw, y, 1, thread);
        d.line(gridX + cw + 12, y, gridX + 2 * cw + 12, y, 1, thread);
        ui.text(
                (first + 1) + "–" + end + " / " + list.size() + " 枚",
                gridX,
                y + 26,
                12,
                ink,
                false);
        int pages = (list.size() + 5) / 6;
        if (pages > 1) {
            float right = ALBUM_SPLIT - pad;
            ui.plainControl(
                    "album-previous",
                    "上一页称号",
                    "chevron-left",
                    "",
                    right - 130,
                    y + 7,
                    30,
                    30,
                    ink,
                    0,
                    pageIndex > 0,
                    () -> ui.selectedTitle = list.get(first - 6).id());
            ui.textCenter(
                    "第 " + (pageIndex + 1) + " / " + pages + " 页",
                    right - 65,
                    y + 26,
                    12,
                    ink,
                    false);
            ui.plainControl(
                    "album-next",
                    "下一页称号",
                    "chevron-right",
                    "",
                    right - 30,
                    y + 7,
                    30,
                    30,
                    ink,
                    0,
                    pageIndex + 1 < pages,
                    () -> ui.selectedTitle = list.get(first + 6).id());
        }
    }

    private void albumRecord(HubData.Title t) {
        HubDraw d = ui.d;
        long now = ui.now;
        boolean reducedMotion = ui.reducedMotion,
                live = ui.live(),
                visible = ui.visible,
                catalogueComplete = ui.catalogueComplete;
        String equipped = ui.equipped;
        float x = ALBUM_SPLIT + 30,
                y = ALBUM_SPREAD,
                w = COLLECTION_LEFT + COLLECTION_WIDTH - x - 30;
        int folio = winterTone(0xfffbf6e8, 0xff343c34),
                ink = winterTone(0xff35463a, 0xfff3e8cc),
                sub = winterTone(0xff5e6858, 0xffc0c4af),
                rule = winterTone(0xffc9c5ae, 0xff606754);
        d.rect(
                ALBUM_SPLIT,
                y,
                COLLECTION_LEFT + COLLECTION_WIDTH - ALBUM_SPLIT,
                ALBUM_END - y,
                folio);
        d.linearGradientRoundRect(
                ALBUM_SPLIT,
                y,
                18,
                ALBUM_END - y,
                0,
                90,
                new int[] {HubView.mix(folio, rule, .68f), folio},
                new float[] {0, 1});
        d.line(ALBUM_SPLIT + 5, y, ALBUM_SPLIT + 5, ALBUM_END, 1, HubView.withAlpha(rule, .55f));
        HubWinterSheet.grain(
                d,
                ALBUM_SPLIT,
                y,
                COLLECTION_LEFT + COLLECTION_WIDTH - ALBUM_SPLIT,
                ALBUM_END - y,
                0,
                1);
        List<String> lines = ui.trackedLines(t.description(), w - 13, 17, 0, 500, true);
        float descriptionHeight = Math.max(1, lines.size()) * 29.75f;
        String date = t.owned() ? ui.titleDate(t) : "";
        boolean stub = !t.owned() || !date.isBlank();
        float copyW = w - (stub ? 66 : 0) - 24;
        List<String> cond = ui.trackedLines(t.condition(), copyW, 13, 0, 400, false);
        float ticketH = Math.max(68.3f, 47 + cond.size() * 20f);
        float clipBottom = ALBUM_END - 90;
        albumRecordOverflow =
                Math.max(0, y + 104 + descriptionHeight + 16 + ticketH + 5 - clipBottom);
        albumRecordScroll = HubView.clamp(albumRecordScroll, 0, albumRecordOverflow);
        d.save();
        d.clipRoundRect(
                ALBUM_SPLIT,
                y,
                COLLECTION_LEFT + COLLECTION_WIDTH - ALBUM_SPLIT,
                clipBottom - y,
                0);
        ui.push(Matrix33.makeTranslate(0, -albumRecordScroll));
        float e = albumEase(0, 220);
        ui.beginAlpha(.38f + .62f * e, x - 2, y, w + 4, 110 + descriptionHeight);
        ui.push(Matrix33.makeTranslate(0, 4 * (1 - e)));
        ui.icon("bookmark-check", x, y + 30, 14, sub);
        ui.text(
                ui.fit(t.category() + " · " + t.rarity(), w - 92, 11, false),
                x + 19,
                y + 42,
                11,
                sub,
                false);
        ui.serif(ui.fitTracked(t.name(), w - 66, 30, 1.6f, 700, true), x, y + 79, 30, ink, true);
        float hitTop = Math.max(y + 45, y + albumRecordScroll);
        if (hitTop < y + 85)
            ui.hit(
                    "full-title",
                    "查看称号全文",
                    x,
                    hitTop,
                    w - 65,
                    y + 85 - hitTop,
                    () -> ui.openOverlay("title-name"));
        d.rect(x, y + 104, 2, descriptionHeight, HubView.mix(folio, d.winterPaper().mail(), .56f));
        for (int i = 0; i < lines.size(); i++)
            ui.serif(lines.get(i), x + 13, y + 122 + i * 29.75f, 17, ink, false);
        ui.pop();
        ui.endAlpha();
        float phase = reducedMotion ? 1 : HubView.progress(now - inkAt, 480_000_000L);
        float angle, ribbonScale;
        if (phase < .46f) {
            float e1 = HubMotion.easeOut(phase / .46f);
            angle = -8 + 12 * e1;
            ribbonScale = .92f + .09f * e1;
        } else if (phase < .75f) {
            float e2 = HubMotion.easeOut((phase - .46f) / .29f);
            angle = 4 - 5.7f * e2;
            ribbonScale = 1.01f - .01f * e2;
        } else {
            angle = -1.7f * (1 - HubMotion.easeOut((phase - .75f) / .25f));
            ribbonScale = 1;
        }
        d.save();
        float pinX = x + w - 65;
        d.canvas().concat(Matrix33.makeRotate(angle, pinX + 32.5f, y));
        d.translate(0, y);
        d.canvas().scale(1, ribbonScale);
        d.translate(0, -y);
        d.rect(pinX + 18, y, 29, 67, 0xff844c40);
        for (float stripe : new float[] {21, 25, 39, 43})
            d.rect(pinX + stripe, y, 1, 65, 0x4deddec3);
        HubCollectionArt.pin(
                d, HubCollectionArt.styleForBadge(t.icon()), t.icon(), pinX, y + 20, 65, false, 1);
        d.restore();
        float ticketY = y + 104 + descriptionHeight + 16;
        e = albumEase(35, 220);
        ui.beginAlpha(.38f + .62f * e, x - 1, ticketY - 1, w + 2, ticketH + 6);
        ui.push(Matrix33.makeTranslate(0, 4 * (1 - e)));
        d.linearGradientRoundRect(
                x,
                ticketY,
                w,
                ticketH,
                6,
                100,
                new int[] {HubView.mix(folio, rule, .23f), HubView.mix(folio, rule, .12f)},
                new float[] {0, 1});
        d.line(x + 3, ticketY, x + w - 3, ticketY, 1, HubView.withAlpha(rule, .72f));
        d.line(
                x + 3,
                ticketY + ticketH,
                x + w - 3,
                ticketY + ticketH,
                1,
                HubView.withAlpha(rule, .72f));
        ui.text(t.owned() ? "获得方式" : "解锁条件", x + 12, ticketY + 21, 11, sub, true);
        for (int i = 0; i < cond.size(); i++)
            ui.text(cond.get(i), x + 12, ticketY + 44 + i * 20, 13, ink, false);
        if (stub) {
            float sx = x + w - 66, midY = ticketY + ticketH / 2;
            for (float dy = 0; dy < ticketH; dy += 5)
                d.line(sx, ticketY + dy, sx, ticketY + Math.min(ticketH, dy + 3), 1, rule);
            d.circle(sx, ticketY, 3.5f, folio);
            d.circle(sx, ticketY + ticketH, 3.5f, folio);
            int red = d.winterPaper().mailInk();
            if (!t.owned()) ui.icon("lock", sx + 25, midY - 17, 16, red);
            else if (date.matches("[0-9]{4}[-/][0-9]{2}[-/][0-9]{2}")) {
                ui.textCenter(date.substring(0, 4), sx + 33, midY - 14, 10, red, false);
                ui.serifCenter(
                        date.substring(5).replace('/', '.').replace('-', '.'),
                        sx + 33,
                        midY + 7,
                        19,
                        red,
                        true);
            } else ui.textCenter(ui.fit(date, 60, 10, false), sx + 33, midY - 2, 10, red, false);
            ui.textCenter(
                    t.owned() ? "入藏" : "未解锁",
                    sx + 33,
                    midY
                            + (t.owned() && !date.matches("[0-9]{4}[-/][0-9]{2}[-/][0-9]{2}")
                                    ? 14
                                    : 22),
                    10,
                    red,
                    false);
        }
        ui.pop();
        ui.endAlpha();
        ui.pop();
        d.restore();
        boolean worn = Objects.equals(t.id(), equipped),
                waiting = ui.pendingModules.contains("titles");
        String label = waiting ? "等待服务器确认…" : !t.owned() ? "尚未获得" : worn ? "已佩戴 · 点击摘下" : "佩戴这个称号";
        float bw = worn || waiting ? 142.5f : 121;
        float etchW = Math.min(196, w + 12 - bw - 40 - (live ? 31 : 0));
        float by = ALBUM_END - 16 - Math.max(34, etchW * 74 / 180) / 2 - 17;
        ui.button(
                "equip",
                label,
                x,
                by,
                bw,
                33,
                !worn && t.owned(),
                t.owned() && !waiting && catalogueComplete,
                () -> equipTitle(t),
                !t.owned() ? "lock" : worn ? "check" : "arrow-right");
        if (!Objects.equals(visible, albumVisibleState)) {
            if (albumVisibleState != null) albumVisibleAt = now;
            albumVisibleState = visible;
        }
        float vx = x + bw + 3;
        boolean visEnabled = canChangeVisibility();
        float visHover = ui.extensionHover("visibility", visEnabled && ui.inside(vx, by, 34, 34));
        if (visHover > 0) d.roundRect(vx, by, 34, 34, 10, HubView.withAlpha(sub, .10f * visHover));
        float visPress =
                reducedMotion
                        ? 1
                        : HubMotion.easeOut(HubView.progress(now - albumVisibleAt, 230_000_000L));
        int visInk = visEnabled ? HubView.mix(sub, ink, visHover) : HubView.withAlpha(sub, .40f);
        ui.beginAlpha(.6f + .4f * visPress, vx, by, 34, 34);
        ui.push(
                HubDraw.cssPerspective(
                        vx + 17,
                        by + 17,
                        800,
                        0,
                        0,
                        -4 * (1 - visPress),
                        1 + .12f * (1 - visPress),
                        0,
                        0));
        ui.icon(visible ? "eye" : "eye-off", vx + 10, by + 10, 14, visInk);
        ui.pop();
        ui.endAlpha();
        if (visEnabled) ui.hit("visibility", "展示给其他玩家", vx, by, 34, 34, this::changeVisibility);
        float rest = x + bw + 41;
        if (live) {
            ui.plainControl(
                    "display-settings",
                    "称号展示位置",
                    "settings",
                    "",
                    rest,
                    by,
                    27,
                    34,
                    sub,
                    0,
                    !waiting,
                    () -> ui.openOverlay("title-display"));
            rest += 31;
        }
        float ew = Math.min(196, x + w + 12 - rest), eh = ew * 74 / 180;
        e = albumEase(0, 220);
        ui.beginAlpha(.38f + .62f * e, rest, by + 16 - eh / 2, ew, eh + 6);
        HubCollectionArt.etching(
                d,
                HubCollectionArt.styleForBadge(t.icon()),
                rest,
                by + 17 - eh / 2 + 4 * (1 - e),
                ew,
                eh,
                sub);
        ui.endAlpha();
    }

    private void equipTitle(HubData.Title t) {
        String equipped = ui.equipped;
        if (ui.live()) {
            JsonObject payload = ui.mutation("titles");
            payload.addProperty("title", Objects.equals(equipped, t.id()) ? "" : t.id());
            ui.request("titles", "equip", payload);
        } else {
            ui.equipped = Objects.equals(equipped, t.id()) ? null : t.id();
            ui.feedbackSuccess(
                    ui.equipped == null ? "已摘下称号 · 本地演示" : "已佩戴「" + t.name() + "」· 本地演示");
        }
    }

    private void titles() {
        if (ui.winter) {
            winterAlbum();
            return;
        }
        HubDraw d = ui.d;
        boolean live = ui.live(), catalogueComplete = ui.catalogueComplete, compact = ui.compact;
        long now = ui.now;
        String focusId = ui.focusId;
        int sort = ui.sort;
        ui.heading(
                "我的称号",
                "选一个喜欢的称号，让名字多一点自己的颜色。",
                "MemorialTitles · " + ui.moduleLabel("titles"),
                live && !catalogueComplete ? -1 : titleCount());
        if (live && !ui.ready("titles")) {
            ui.unavailable("titles");
            return;
        }
        ui.group(30, 350);
        ui.glass(HubView.LEFT, 174, 234, 36, 13, 0x5cdce0f0, 0x5cdce0f0);
        String[] labels = {"已拥有", "全部", "未获得"};
        for (int i = 0; i < 3; i++) {
            final int value = i;
            float x = HubView.LEFT + 3 + i * 76;
            float chosen =
                    ui.stateTransition("title-filter-selected-" + i, filter == i ? 1 : 0, 140);
            if (chosen > 0) {
                ui.beginAlpha(chosen, x, 177, 74, 30);
                ui.glass(x, 177, 74, 30, 10, 0xffffffff, 0xffffffff);
                ui.endAlpha();
            }
            ui.textCenter(
                    labels[i],
                    x + 37,
                    197,
                    11,
                    HubView.mix(0xff78839b, 0xff6650a7, chosen),
                    filter == i);
            ui.hit(
                    "filter-" + i,
                    labels[i],
                    x,
                    177,
                    74,
                    30,
                    () -> {
                        filter = value;
                        ui.titleScroll = 0;
                    });
        }
        ui.iconButton(
                "density",
                compact ? "切换卡片显示" : "切换紧凑显示",
                compact ? "grid-2x2" : "list",
                864,
                176,
                32,
                () -> {
                    ui.compact = !compact;
                    ui.titleScroll = 0;
                },
                true);
        ui.glass(HubView.LEFT, 223, 518, 36, 11, 0xb3ffffff, 0xb3ffffff);
        ui.icon("search", 248, 233, 16, 0xff929bb1);
        if (searchSelectAll && !query.isEmpty() && "search".equals(focusId))
            d.roundRect(
                    273,
                    230,
                    Math.min(438, d.textWidth(query, 12, false)) + 2,
                    21,
                    3,
                    ui.a(0xffd4c9ff));
        ui.text(
                query.isEmpty() ? "搜索称号" : ui.fit(query, 439, 12, false),
                274,
                247,
                12,
                query.isEmpty() ? 0xff949caf : HubView.INK,
                false);
        if ("search".equals(focusId)) {
            d.boxShadow(HubView.LEFT, 223, 518, 36, 11, 0, 0, 0, 3, 0x1fab92ef);
            d.strokeRoundRect(HubView.LEFT, 223, 518, 36, 11, 1, ui.a(0xffa391dc));
            if (!searchSelectAll && (now / 500_000_000L) % 2 == 0) {
                float caret =
                        274
                                + d.textWidth(
                                        query.substring(0, Math.min(searchCaret, query.length())),
                                        12,
                                        false);
                if (caret < 735) d.line(caret, 232, caret, 250, 1, ui.a(HubView.INK));
            }
        }
        ui.hit(
                "search",
                "搜索称号",
                HubView.LEFT,
                223,
                518,
                36,
                () -> {
                    searchCaret = query.length();
                    searchSelectAll = false;
                });
        if (!query.isEmpty())
            ui.iconButton(
                    "clear-search",
                    "清空搜索",
                    "x",
                    720,
                    228,
                    26,
                    () -> {
                        query = "";
                        searchCaret = 0;
                        ui.titleScroll = 0;
                        searchSelectAll = false;
                        ui.focusId = "search";
                    },
                    true);
        ui.button(
                "sort",
                sort == 0 ? "最近获得  ⇅" : "名称排序  ⇅",
                766,
                223,
                132,
                36,
                false,
                true,
                () -> {
                    ui.sort = 1 - sort;
                    ui.titleScroll = 0;
                });
        List<HubData.Title> list = titleList();
        float contentHeight = compact ? list.size() * 80f : ((list.size() + 1) / 2) * 174f - 16;
        float viewportHeight = 510;
        ui.titleScroll =
                Math.max(0, Math.min(ui.titleScroll, Math.max(0, contentHeight - viewportHeight)));
        d.save();
        d.clipRoundRect(230, 272, 674, 522, 0);
        ui.markTitleClipTransform();
        for (int i = 0; i < list.size(); i++) {
            float x = compact ? HubView.LEFT : HubView.LEFT + (i % 2) * 339;
            float y = 278 + (compact ? i * 80 : (i / 2) * 174) - ui.titleScroll;
            if (y + (compact ? 68 : 158) < 274 || y > 790) continue;
            titleTile(list.get(i), x, y, compact ? 662 : 323, compact ? 68 : 158);
        }
        d.restore();
        if (contentHeight > viewportHeight) {
            float thumb = Math.max(45, viewportHeight * viewportHeight / contentHeight);
            d.roundRect(
                    902,
                    278
                            + ui.titleScroll
                                    / (contentHeight - viewportHeight)
                                    * (viewportHeight - thumb),
                    3,
                    thumb,
                    2,
                    ui.a(0x99a6b0cf));
        }
        if (list.isEmpty() && !catalogueComplete) {
            ui.icon("tag", 548, 374, 28, 0xff9d90b4);
            ui.textCenter("正在读取称号目录…", 567, 437, 17, 0xff8c8495, true);
        } else if (list.isEmpty()) {
            boolean searching = !query.isBlank();
            ui.icon(searching ? "search" : filter == 2 ? "check" : "tag", 548, 374, 28, 0xff9d90b4);
            ui.textCenter(
                    searching
                            ? "没有找到这个称号"
                            : filter == 2 ? "已集齐全部称号" : filter == 0 ? "暂未获得称号" : "暂无称号",
                    567,
                    437,
                    17,
                    0xff8c8495,
                    true);
            ui.textCenter(
                    searching
                            ? "换个关键词，或查看全部称号。"
                            : filter == 2
                                    ? "你已经收藏了当前全部称号。"
                                    : filter == 0 ? "继续探索旅程解锁更多称号，也可以先查看全部称号。" : "当前目录里还没有可展示的称号。",
                    567,
                    466,
                    12,
                    HubView.MUTED,
                    false);
            if (searching || filter != 1)
                ui.button(
                        "reset-filter",
                        "查看全部",
                        512,
                        492,
                        110,
                        37,
                        false,
                        true,
                        () -> {
                            filter = 1;
                            query = "";
                            ui.titleScroll = 0;
                        });
        }
        ui.text(
                list.size()
                        + " 枚称号 · "
                        + (!catalogueComplete
                                ? "目录正在加载，请稍候"
                                : filter == 2 ? "还有新的旅程等你开启" : "每一个名字，都有它的故事"),
                HubView.LEFT,
                816,
                11,
                0xff8993a9,
                false);
        titleDetail();
        ui.ungroup();
    }

    private void chooseTitleSort(int choice) {
        if (ui.sort != choice) ui.titleScroll = 0;
        ui.sort = choice;
        titleSortOpen = false;
        ui.focusId = "sort";
    }

    private void titleTile(HubData.Title t, float x, float y, float w, float h) {
        HubDraw d = ui.d;
        boolean dark = ui.dark, compact = ui.compact;
        String equipped = ui.equipped;
        boolean selected = ui.selectedTitle.equals(t.id());
        ui.beginAlpha(t.owned() ? 1 : .8f, x, y, w, h);
        float radius = 18;
        HubMotion.Tilt tilt =
                ui.beginTilt("title-" + t.id(), x, y, w, h, 5, 1.018f, false, .12f, radius);
        boolean previousColors = d.literalColors(true);
        d.boxShadow(
                x,
                y,
                w,
                h,
                18,
                0,
                selected ? 5 : 3,
                selected ? 16 : 10,
                0,
                ui.a(
                        dark
                                ? (selected ? 0x4d0b142d : 0x290c122a)
                                : selected ? HubView.withAlpha(t.color(), .15f) : 0x0c607097));
        int wash = dark ? HubView.mix(t.color(), 0xff273148, .77f) : t.light();
        d.radialGradientRoundRect(
                x,
                y,
                w,
                h,
                18,
                x + w,
                y + h * .1f,
                w * 1.41421356f,
                h * .9f * 1.41421356f,
                new int[] {ui.a(wash), ui.a(dark ? 0xe8293247 : 0xd9ffffff)},
                new float[] {0, .74f});
        int edge =
                dark
                        ? (selected
                                ? HubView.mix(t.color(), 0xffd5c7fb, .28f)
                                : HubView.cssMix(t.color(), 0x3b7987a6, .76f))
                        : selected ? HubView.mix(t.color(), 0xff6452ad, .2f) : 0xfaffffff;
        d.strokeRoundRect(x + .5f, y + .5f, w - 1, h - 1, 18, 1, ui.a(edge));
        if (selected)
            d.strokeRoundRect(
                    x + 1.5f,
                    y + 1.5f,
                    w - 3,
                    h - 3,
                    17,
                    1,
                    ui.a(HubView.withAlpha(t.color(), dark ? .25f : .48f)));
        else if (dark) d.insetBoxShadow(x, y, w, h, 18, 0, 1, 0, 0, ui.a(0x12deebff));
        else d.strokeRoundRect(x + 1.5f, y + 1.5f, w - 3, h - 3, 17, 1, ui.a(0xccffffff));
        d.literalColors(previousColors);

        int muted = 0xff7c859a;
        int quality = HubView.mix(t.color(), HubView.INK, .4f);
        if (compact) {
            emblem(t, x + 16, y + 13, 42);
            float copyX = x + 75;
            ui.titleLabel(t, copyX, y + 30, 17, 1, false, !t.owned(), w - 178);
            ui.text(t.category(), copyX, y + 50, 10, muted, false);
            if (Objects.equals(equipped, t.id())) {
                ui.pill("✓ 已佩戴", x + w - 84, y + 22, 0xa3ffffff, 0xff448772, false);
            } else if (!t.owned()) ui.icon("lock", x + w - 36, y + 26, 14, 0xff8d97ad);
        } else {
            ui.text(t.category(), x + 20, y + 32, 10, muted, false);
            ui.titleLabel(t, x + 20, y + 66, 20, 1, false, !t.owned(), w - 111);
            if (!t.rarity().isEmpty()) {
                d.circle(x + 23, y + 88, 2, ui.a(t.rarityColor()));
                ui.text(t.rarity(), x + 32, y + 92, 10, t.rarityColor(), false);
            }
            emblem(t, x + w - 81, y + 43, 60);
            if (Objects.equals(equipped, t.id())) {
                ui.pill("✓ 已佩戴", x + w - 86, y + 12, 0xa3ffffff, 0xff448772, false);
            } else if (!t.owned()) ui.icon("lock", x + w - 31, y + 17, 14, 0xff8d97ad);
            d.line(
                    x + 20,
                    y + 124,
                    x + w - 20,
                    y + 124,
                    1,
                    ui.a(HubView.mix(t.color(), 0x00ffffff, .75f)));
            ui.icon("calendar-days", x + 20, y + 136, 12, 0xff7b859c);
            ui.text(t.owned() ? ui.titleDate(t) : "尚未解锁", x + 38, y + 146, 10, 0xff7b859c, false);
            if (selected) {
                ui.icon("check", x + w - 77, y + 135, 12, quality);
                ui.text("已选中", x + w - 60, y + 146, 10, quality, false);
            } else ui.icon("arrow-up-right", x + w - 34, y + 135, 14, quality);
        }
        ui.hit(
                "title-" + t.id(),
                t.name() + (t.owned() ? "" : "，未获得"),
                x,
                y,
                w,
                h,
                () -> ui.selectedTitle = t.id());
        ui.endTilt(tilt, x, y, w, h, radius, 5, .5f);
        ui.endAlpha();
    }

    private void emblem(HubData.Title t, float x, float y, float size) {
        HubDraw d = ui.d;
        boolean dark = ui.dark;
        float radius = size * .28f;
        d.save();
        d.translate(x + size / 2, y + size / 2);
        d.canvas().rotate(-9);
        d.save();
        d.canvas().rotate(12);
        d.translate(4, -5);
        int back = t.owned() ? t.color() : HubView.desaturate(t.color(), .35f);
        boolean previousColors = d.literalColors(true);
        d.boxShadow(
                -size / 2,
                -size / 2,
                size,
                size,
                radius,
                0,
                9,
                18,
                0,
                ui.a(dark ? 0x400b152e : HubView.withAlpha(back, .35f)));
        d.linearGradientRoundRect(
                -size / 2,
                -size / 2,
                size,
                size,
                radius,
                145,
                new int[] {ui.a(back), ui.a(HubView.mix(back, 0xff463169, .17f))},
                new float[] {0, 1});
        d.restore();
        d.captureGlassBackdrop(-size / 2, -size / 2, size, size);
        d.blurBackdrop(-size / 2, -size / 2, size, size, radius, 4, 1);
        d.linearGradientRoundRect(
                -size / 2,
                -size / 2,
                size,
                size,
                radius,
                145,
                dark
                        ? new int[] {ui.a(0x29d3defc), ui.a(0x0ec8d2f2)}
                        : new int[] {ui.a(0xa6ffffff), ui.a(0x47ffffff)},
                new float[] {0, 1});
        d.strokeRoundRect(
                -size / 2, -size / 2, size, size, radius, 1, ui.a(dark ? 0x47d9e4ff : 0xd9ffffff));
        d.insetBoxShadow(
                -size / 2,
                -size / 2,
                size,
                size,
                radius,
                1,
                1,
                1,
                0,
                ui.a(dark ? 0x36e7eeff : 0xffffffff));
        d.literalColors(previousColors);
        ui.icon(
                "stroke-1.5/" + t.icon(),
                -size * .27f,
                -size * .27f,
                size * .54f,
                dark
                        ? HubView.mix(t.color(), 0xfff4f7ff, .45f)
                        : HubView.mix(t.color(), 0xff25364c, .32f));
        d.restore();
    }

    private void titleDetail() {
        HubDraw d = ui.d;
        HubData data = ui.data;
        boolean dark = ui.dark,
                live = ui.live(),
                visible = ui.visible,
                catalogueComplete = ui.catalogueComplete;
        String equipped = ui.equipped;
        HubData.Title t = data.title(ui.selectedTitle);
        if (t == null && !data.titles.isEmpty()) t = data.titles.get(0);
        if (t == null) {
            ui.glass(HubView.RIGHT, 174, 310, 626, 22, 0xecffffff, 0xabffffff);
            ui.textCenter("选择称号查看详情", 1077, 450, 15, HubView.MUTED, false);
            return;
        }
        final HubData.Title selected = t;
        ui.glass(HubView.RIGHT, 174, 310, 626, 22, 0xecffffff, 0xabffffff);
        ui.text("佩戴预览", 940, 207, 13, 0xff4f5a73, true);
        boolean worn = Objects.equals(equipped, t.id());
        ui.titleStatus(worn ? "✓ 已佩戴" : "◉ 仅预览", 1144, 188, worn);
        boolean previousColors = d.literalColors(true);
        d.boxShadow(940, 225, 274, 226, 18, 0, 4, 12, 0, ui.a(dark ? 0x2e0b142f : 0x108f85ad));
        d.linearGradientRoundRect(
                940,
                225,
                274,
                226,
                18,
                135,
                new int[] {
                    ui.a(dark ? HubView.mix(t.color(), 0xff263349, .77f) : t.light()),
                    ui.a(dark ? 0x99423a5c : 0xb0e8e3ff)
                },
                new float[] {0, 1});
        int sheen = dark ? 0x24bfcef2 : 0xecffffff;
        d.radialGradientRoundRect(
                940,
                225,
                274,
                226,
                18,
                1077,
                225,
                137 * 1.41421356f,
                226 * 1.41421356f,
                new int[] {ui.a(sheen), sheen & 0xffffff},
                new float[] {0, .75f});
        d.insetBoxShadow(940, 225, 274, 226, 18, 0, 1, 3, 0, ui.a(dark ? 0x14d7e6ff : 0xffffffff));
        d.strokeRoundRect(940.5f, 225.5f, 273, 225, 18, 1, ui.a(dark ? 0x2ec5d6f4 : 0xffffffff));
        d.strokeRoundRect(948, 233, 258, 210, 11, 1, ui.a(dark ? 0x16d3e3ff : 0x8cffffff));
        d.literalColors(previousColors);
        d.save();
        d.clipRoundRect(940, 225, 274, 226, 18);
        d.translate(1077, 338);
        d.canvas().rotate(-30);
        d.strokeRoundRect(-134, -49, 268, 98, 49, 1, ui.a(dark ? 0x26cadaff : 0xb8ffffff));
        d.restore();
        ui.icon("stroke-1.0/" + t.icon(), 931, 292, 106, HubView.withAlpha(t.color(), .14f));
        ui.titleLabel(t, 1077, 275, 21, 2, true, false);
        String previewPlayer = ui.fit(ui.playerName, 230, 13, true);
        float previewPlayerWidth = d.textWidthTracked(previewPlayer, 13, 500, .5f, false);
        d.textTracked(
                previewPlayer,
                1077 - previewPlayerWidth * .5f,
                301.5f,
                13,
                ui.a(0xff62718f),
                500,
                .5f,
                false);
        previousColors = d.literalColors(true);
        d.ovalShadow(1007, 377, 140, 42, 0, 5, 12, ui.a(dark ? 0x4d101e3d : 0x277184b8));
        d.oval(1007, 377, 140, 42, ui.a(dark ? 0x21adbed5 : 0x9cf6f8ff));
        d.insetOvalShadow(1008, 378, 138, 40, 0, -5, 7, ui.a(dark ? 0x10a9a0d4 : 0x17b0a8d0));
        d.strokeOval(1007.5f, 377.5f, 139, 41, 1, ui.a(dark ? 0x30d1e2ff : 0xffffffff));
        d.oval(1021.5f, 379, 111, 26, ui.a(dark ? 0x400f1b33 : 0x4da3b4d5));
        d.literalColors(previousColors);
        if (ui.opacity > .04f) HubHead.draw(d, 1051, 335, 51, -23);
        ui.textCenter("游戏内效果示意", 1077, 419.5f, 10, 0xff7d87a1, false);
        ui.icon(t.icon(), 940, 473, 18, HubView.mix(t.color(), HubView.INK, .55f));
        ui.titleLabel(t, 966, 488, 14, 0, false, false);
        ui.hit("full-title", "查看称号全文", 940, 466, 274, 28, () -> ui.openOverlay("title-name"));
        ui.wrap(t.description(), 940, 516, 274, 12, 22, 0xff748098, false);
        ui.glass(940, 568, 274, 73, 10, 0xfff7f7fc, 0xfff7f7fc);
        ui.icon("gift", 952, 580, 13, 0xff8990a5);
        ui.text(t.owned() ? "获得方式" : "解锁条件", 973, 591, 10, 0xff8990a5, false);
        ui.wrap(t.condition(), 952, 615, 250, 11, 18, 0xff6d7890, false);
        if (t.owned()) {
            ui.icon("calendar-days", 942, 655, 13, HubView.MUTED);
            ui.text(ui.titleDate(t) + "  获得", 962, 667, 10, HubView.MUTED, false);
        }
        String equipText = !t.owned() ? "尚未获得" : worn ? "✓ 已佩戴 · 点击摘下" : "佩戴这个称号  →";
        ui.button(
                "equip",
                ui.pendingModules.contains("titles") ? "等待服务器确认…" : equipText,
                940,
                687,
                274,
                38,
                !worn,
                t.owned() && !ui.pendingModules.contains("titles") && catalogueComplete,
                () -> equipTitle(selected));
        ui.text("展示给其他玩家", 940, 754, 11, 0xff65718b, false);
        if (live)
            ui.iconButton(
                    "display-settings",
                    "称号展示位置",
                    "settings",
                    1128,
                    742,
                    25,
                    () -> ui.openOverlay("title-display"),
                    !ui.pendingModules.contains("titles"));
        String visibilityNote =
                !privacyKnown()
                        ? (catalogueComplete ? "称号展示状态暂不可用" : "正在读取称号展示状态…")
                        : live && (equipped == null || equipped.isEmpty())
                                ? "先佩戴称号后可设置"
                                : visible ? (live ? "仅修改当前佩戴称号的展示" : "本地展示示意") : "已隐藏展示，佩戴状态保留";
        ui.text(visibilityNote, 940, 774, 10, 0xff8a94aa, false);
        ui.toggle(
                "visibility",
                privacyKnown() && visible,
                1174,
                746,
                this::changeVisibility,
                canChangeVisibility());
    }

    private boolean handleSearchKey(int keyCode, int modifiers, String focused) {
        if (!"search".equals(focused)) return false;
        if ((keyCode == 259 || keyCode == 261) && searchSelectAll) {
            query = "";
            searchCaret = 0;
            searchSelectAll = false;
            ui.titleScroll = 0;
            return true;
        }
        if (keyCode == 259 && searchCaret > 0) {
            int at = query.offsetByCodePoints(searchCaret, -1);
            query = query.substring(0, at) + query.substring(searchCaret);
            searchCaret = at;
            ui.titleScroll = 0;
            return true;
        }
        if (keyCode == 261 && searchCaret < query.length()) {
            int end = query.offsetByCodePoints(searchCaret, 1);
            query = query.substring(0, searchCaret) + query.substring(end);
            ui.titleScroll = 0;
            return true;
        }
        if (keyCode == 263) {
            searchCaret =
                    searchSelectAll
                            ? 0
                            : searchCaret > 0 ? query.offsetByCodePoints(searchCaret, -1) : 0;
            searchSelectAll = false;
            return true;
        }
        if (keyCode == 262) {
            searchCaret =
                    searchSelectAll
                            ? query.length()
                            : searchCaret < query.length()
                                    ? query.offsetByCodePoints(searchCaret, 1)
                                    : query.length();
            searchSelectAll = false;
            return true;
        }
        if (keyCode == 268) {
            searchCaret = 0;
            searchSelectAll = false;
            return true;
        }
        if (keyCode == 269) {
            searchCaret = query.length();
            searchSelectAll = false;
            return true;
        }
        if (keyCode == 65 && (modifiers & 2) != 0) {
            searchSelectAll = true;
            searchCaret = query.length();
            return true;
        }
        return keyCode == 259
                || keyCode == 261
                || keyCode == 263
                || keyCode == 262
                || keyCode == 268
                || keyCode == 269;
    }

    private void pasteSearchText(String value) {
        int available = 60 - (searchSelectAll ? 0 : query.length());
        StringBuilder sanitized = new StringBuilder();
        value.codePoints()
                .filter(codePoint -> !Character.isISOControl(codePoint) && codePoint != 0x7f)
                .forEach(
                        codePoint -> {
                            if (sanitized.length() + Character.charCount(codePoint) <= available)
                                sanitized.appendCodePoint(codePoint);
                        });
        if (sanitized.isEmpty()) return;
        if (searchSelectAll) {
            query = "";
            searchCaret = 0;
            searchSelectAll = false;
        }
        query = query.substring(0, searchCaret) + sanitized + query.substring(searchCaret);
        searchCaret += sanitized.length();
        ui.titleScroll = 0;
    }
}
