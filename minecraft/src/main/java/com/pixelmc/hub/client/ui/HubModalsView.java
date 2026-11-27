/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.ui;

import com.pixelmc.hub.client.HubModpackSync;
import com.pixelmc.hub.client.render.HubDraw;

import io.github.humbleui.skija.Matrix33;

import java.time.format.DateTimeFormatter;
import java.util.List;

/** Shared modal shell and settings, modpack sync, profile, and preview overlays. */
final class HubModalsView {
    interface Host {
        int fortuneState();

        void setFortuneState(int state);

        String stateLabel();

        void resetDemoData();

        void playRankingDemo();

        void renderWinterFortuneDialog();

        void renderReadingModal(float modalX, float modalY, float modalW, float modalH);

        void renderFortuneHelp(float modalX, float modalY, float modalH);

        void renderTitleNameModal(float modalX, float modalY, float modalW);

        void renderTitleDisplayModal(float modalX, float modalY, float modalW);

        void scrollReading(double amount);

        boolean keyScrollReading(int keyCode, float modalH);
    }

    private final HubView ui;
    private final Host host;
    private long modalAt;
    private float modalX, modalY, modalW, modalH;
    private boolean sliderDragging;
    private Matrix33 sliderTransform = Matrix33.IDENTITY;

    HubModalsView(HubView ui, Host host) {
        this.ui = ui;
        this.host = host;
    }

    void resetOnOpen() {
        sliderDragging = false;
    }

    void markOpened(long nowNanos) {
        sliderDragging = false;
        modalAt = nowNanos;
    }

    long modalAt() {
        return modalAt;
    }

    void setBounds(float x, float y, float w, float h) {
        modalX = x;
        modalY = y;
        modalW = w;
        modalH = h;
    }

    boolean isSliderDragging() {
        return sliderDragging;
    }

    void setSliderDragging(boolean dragging) {
        sliderDragging = dragging;
    }

    void setSliderTransform(Matrix33 transform) {
        sliderTransform = transform == null ? Matrix33.IDENTITY : transform;
    }

    boolean outsideModal(float x, float y) {
        return x < modalX || x > modalX + modalW || y < modalY || y > modalY + modalH;
    }

    boolean insideReadingBody(float x, float y) {
        return x >= modalX && x <= modalX + modalW && y >= modalY + 88 && y <= modalY + modalH;
    }

    void setWelcomeFromX(float x) {
        float[] p = HubDraw.inversePoint(sliderTransform, x, ui.my);
        int index = Math.round(HubView.clamp((p[0] - frequencyStart()) / frequencyStep(), 0, 2));
        ui.welcomeMode = List.of("once", "login", "always").get(index);
    }

    boolean handleFrequencyKey(int keyCode) {
        int index = ui.welcomeIndex();
        if (keyCode == 263 || keyCode == 264) index--;
        else if (keyCode == 262 || keyCode == 265) index++;
        else if (keyCode == 268) index = 0;
        else if (keyCode == 269) index = 2;
        else index = -1;
        if (index >= 0) {
            ui.welcomeMode = List.of("once", "login", "always").get(Math.min(2, index));
            return true;
        }
        return keyCode == 263 || keyCode == 264;
    }

    boolean mouseScrolled(float x, float y, double amount) {
        if (ui.winter && ui.reading != null && insideReadingBody(x, y)) {
            host.scrollReading(amount);
            return true;
        }
        return false;
    }

    boolean keyScrollReading(int keyCode) {
        return host.keyScrollReading(keyCode, modalH);
    }

    String syncSidebarStatus() {
        boolean granted = HubModpackSync.granted();
        boolean enabled = HubModpackSync.enabled();
        boolean preview = "preview".equals(HubModpackSync.channel());
        if (!granted) return "未授权 · 点击设置";
        if (!enabled) return "自动同步已关闭";
        return preview ? "预览版 · 自动同步" : "正式版 · 自动同步";
    }

    int syncSidebarDot(boolean winterMode) {
        boolean granted = HubModpackSync.granted();
        boolean enabled = HubModpackSync.enabled();
        boolean preview = "preview".equals(HubModpackSync.channel());
        if (!granted || !enabled) return ui.dark ? 0xff6b768f : 0xff9aa3b2;
        if (preview) {
            if (winterMode) return ui.d.winterPaper().mail();
            return ui.dark ? 0xffb197fc : 0xff8a6ee3;
        }
        return winterMode ? ui.d.winterPaper().pine() : 0xff6a9c8b;
    }

    void render() {
        String overlay = ui.overlay;
        HubData.Entry reading = ui.reading;
        boolean winter = ui.winter;
        if (winter && (reading != null || "fortune-help".equals(overlay))) {
            host.renderWinterFortuneDialog();
            return;
        }
        HubDraw d = ui.d;
        long now = ui.now;
        boolean dark = ui.dark, reducedMotion = ui.reducedMotion;
        float viewportX = ui.viewportX,
                viewportY = ui.viewportY,
                viewportW = ui.viewportW,
                viewportH = ui.viewportH;
        ui.opacity = ui.interactiveAlpha = 1;
        ui.offsetY = 0;
        ui.nextHits.clear();
        d.captureGlassBackdrop(viewportX, viewportY, viewportW, viewportH);
        float backdrop =
                reducedMotion ? 1 : HubMotion.ease(HubView.progress(now - modalAt, 180_000_000L));
        ui.beginAlpha(backdrop, viewportX, viewportY, viewportW, viewportH);
        d.blurBackdrop(viewportX, viewportY, viewportW, viewportH, 0, winter ? 5 : 9, 1);
        d.rect(
                viewportX,
                viewportY,
                viewportW,
                viewportH,
                winter ? 0x4a192c29 : dark ? 0x590a1029 : 0x3634405c);
        boolean wide = reading != null;
        modalW =
                wide
                        ? 720
                        : winter && ("settings".equals(overlay) || "modpack-sync".equals(overlay))
                                ? 482
                                : 440;
        modalH =
                wide
                        ? 147
                                + Math.max(
                                        winter ? 390 : 357,
                                        178.5f
                                                + ui.trackedLines(
                                                                        reading.fortune()
                                                                                .interpretation(),
                                                                        319.5f,
                                                                        15,
                                                                        1,
                                                                        400,
                                                                        true)
                                                                .size()
                                                        * 31.5f)
                        : "settings".equals(overlay)
                                ? (winter ? 543.5f : 586)
                                : "modpack-sync".equals(overlay)
                                        ? (winter ? 388 : 396)
                                        : "profile".equals(overlay)
                                                ? (winter ? 399 : 590)
                                                : "preview".equals(overlay) ? 430 : 355;
        modalX = (1260 - modalW) / 2;
        modalY = (850 - modalH) / 2;
        float enter =
                reducedMotion
                        ? 1
                        : HubMotion.bezier(
                                HubView.progress(now - modalAt, 220_000_000L), .2f, .8f, .2f, 1);
        ui.beginAlpha(enter, modalX, modalY, modalW, modalH);
        ui.push(
                HubDraw.cssPerspective(
                        630, 425, 800, 0, 0, 0, .985f + .015f * enter, 0, 9 * (1 - enter)));
        d.boxShadow(
                modalX, modalY, modalW, modalH, 25, 0, 28, 76, -18, dark ? 0x99030920 : 0x4035416c);
        d.boxShadow(
                modalX, modalY, modalW, modalH, 25, 0, 5, 16, -8, dark ? 0x59060e27 : 0x2453658e);
        if (winter)
            HubWinterSheet.panel(d, modalX, modalY, modalW, modalH, 23, d.winterPaper().sheet(), 1);
        else
            d.linearGradientRoundRect(
                    modalX,
                    modalY,
                    modalW,
                    modalH,
                    25,
                    135,
                    new int[] {0xf5fcfdff, 0xeff5f3ff},
                    new float[] {0, 1});
        if (!winter) d.strokeRoundRect(modalX, modalY, modalW, modalH, 25, 1, 0xffffffff);
        if (winter) {
            boolean old = d.literalColors(true);
            d.save();
            d.clipRoundRect(modalX, modalY, modalW, modalH, 23);
            d.linearGradientRoundRect(
                    modalX,
                    modalY,
                    modalW,
                    88,
                    0,
                    105,
                    new int[] {d.winterPaper().soft(), d.winterPaper().sheet()},
                    new float[] {0, 1});
            HubWinterSheet.grain(d, modalX, modalY, modalW, 88, 0, .6f);
            if ("settings".equals(overlay)
                    || "modpack-sync".equals(overlay)
                    || "profile".equals(overlay))
                HubWinterSheet.illustration(
                        d,
                        "profile".equals(overlay) ? "travel-map" : "pine-lantern",
                        modalX + modalW - 146,
                        modalY + 19,
                        77,
                        50,
                        1);
            d.restore();
            d.literalColors(old);
        }
        String title =
                wide
                        ? "签笺详情"
                        : switch (overlay) {
                            case "settings" -> "界面设置";
                            case "modpack-sync" -> "整合包同步";
                            case "profile" -> winter ? "个人资料" : "账户信息";
                            case "preview" -> "预览选项";
                            case "title-name" -> "称号全文";
                            case "title-display" -> "称号展示位置";
                            default -> host.fortuneState() == 0 ? "如何抽签" : "如何解签";
                        };
        String subtitle =
                wide
                        ? reading.date().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
                                + " · "
                                + ui.playerName
                                + " 所得"
                        : switch (overlay) {
                            case "settings" -> "让手册更符合你的习惯";
                            case "modpack-sync" -> modpackSyncSubtitle();
                            case "profile" -> "你在这段旅程中留下的足迹";
                            case "preview" -> "仅用于体验不同状态，不会修改游戏数据";
                            case "title-name" -> "完整文字与原始样式";
                            case "title-display" -> "个人偏好与服务器展示规则";
                            default -> "DailyFortunes · 玩法提示";
                        };
        if (winter) ui.serif(title, modalX + 27, modalY + 39, 19, d.winterPaper().ink(), true);
        else
            ui.text(
                    title,
                    modalX + 27,
                    modalY + 39,
                    19,
                    (winter ? d.winterPaper().ink() : 0xff35415e),
                    true);
        ui.text(
                subtitle,
                modalX + 27,
                modalY + 63,
                12,
                (winter ? d.winterPaper().muted() : 0xff8c8fa5),
                false);
        if (winter) {
            float bx = modalX + modalW - 55, by = modalY + 20;
            var p = d.winterPaper();
            d.circle(bx + 15, by + 15, 15, ui.inside(bx, by, 30, 30) ? p.ruling() : p.cover());
            ui.icon("x", bx + 7.5f, by + 7.5f, 15, p.pine());
            ui.hit("modal-close", "关闭弹窗", bx, by, 30, 30, ui::dismissOverlay);
        } else
            ui.iconButton(
                    "modal-close",
                    "关闭弹窗",
                    "x",
                    modalX + modalW - 58,
                    modalY + 22,
                    32,
                    ui::dismissOverlay,
                    true);
        if (winter)
            d.line(
                    modalX + 1,
                    modalY + 88,
                    modalX + modalW - 1,
                    modalY + 88,
                    1,
                    d.winterPaper().ruling());
        else
            d.line(
                    modalX + 26,
                    modalY + 84,
                    modalX + modalW - 26,
                    modalY + 84,
                    1,
                    (winter ? d.winterPaper().ruling() : 0xffe7e6f1));
        if (wide) host.renderReadingModal(modalX, modalY, modalW, modalH);
        else
            switch (overlay) {
                case "settings" -> settingsModal();
                case "modpack-sync" -> modpackSyncModal();
                case "profile" -> profileModal();
                case "preview" -> previewModal();
                case "title-name" -> host.renderTitleNameModal(modalX, modalY, modalW);
                case "title-display" -> host.renderTitleDisplayModal(modalX, modalY, modalW);
                default -> host.renderFortuneHelp(modalX, modalY, modalH);
            }
        ui.pop();
        ui.endAlpha();
        ui.endAlpha();
    }

    private void handbookStyleChoice(float x, float y) {
        HubDraw d = ui.d;
        var p = d.winterPaper();
        boolean winter = ui.winter, dark = ui.dark;
        ui.text("手册主题", x, y + 21, 14, winter ? p.ink() : HubView.INK, true);
        ui.text(
                winter ? (dark ? "驿站夜灯" : "雪后清晨") : "经典旅人手册",
                x,
                y + 46,
                12,
                winter ? p.muted() : HubView.MUTED,
                false);
        float bx = x + 195;
        d.roundRect(bx, y + 6, 189, 40, 11, winter ? p.soft() : dark ? 0xff20283b : 0xfff5f6ff);
        float stylePosition = ui.selectorPosition("style-position", winter);
        if (!ui.styleTransition && stylePosition != (winter ? 1 : 0))
            d.keepThemeControlLive(bx, y + 6, 189, 40, 11);
        d.roundRect(
                bx + 4 + 90 * stylePosition,
                y + 10,
                90,
                32,
                8,
                winter ? p.sheet() : dark ? 0xff363c55 : 0xffffffff);
        for (int i = 0; i < 2; i++) {
            boolean choice = i == 1, selected = choice == winter;
            float left = bx + 4 + i * 90;
            int c = winter ? (selected ? p.pine() : p.muted()) : (dark ? 0xffc9b6ed : 0xff69529e);
            float iconX = left + (choice ? 10 : 18);
            ui.icon(choice ? "snowflake" : "book-open", iconX, y + 19, 14, c);
            ui.text(choice ? "冬日驿站" : "经典", iconX + 19, y + 31, 12, c, selected);
            ui.hit(
                    choice ? "style-winter" : "style-classic",
                    choice ? "冬日驿站" : "经典旅人手册",
                    left,
                    y + 10,
                    90,
                    32,
                    () -> ui.setWinter(choice));
        }
    }

    private void stationSettings() {
        HubDraw d = ui.d;
        var p = d.winterPaper();
        boolean dark = ui.dark, reducedMotion = ui.reducedMotion;
        float x = modalX, y = modalY;
        HubWinterSheet.panel(d, x + 25, y + 106, 432, 165.5f, 14, p.soft(), 1);
        HubWinterSheet.grain(d, x + 25, y + 106, 432, 165.5f, 14, .8f);
        ui.text("开屏动画", x + 42, y + 141, 13, p.ink(), true);
        ui.textRight("驿站亮灯", x + 440, y + 141, 11, p.muted(), false);
        HubWinterSheet.panel(d, x + 42, y + 154, 398, 76, 12, p.sheet(), 1);
        int index = ui.welcomeIndex();
        float start = frequencyStart(), step = frequencyStep(), cy = y + 179;
        d.roundRect(start, cy - 3, step * 2, 6, 3, p.ruling());
        if (index > 0) d.roundRect(start, cy - 3, step * index, 6, 3, p.pine());
        for (int i = 0; i < 3; i++) {
            float at = start + i * step;
            d.circle(at, cy, 3.5f, p.stitch());
            d.circle(at, cy, 2.5f, p.sheet());
        }
        float thumb = start + index * step;
        d.boxShadow(thumb - 11, cy - 11, 22, 22, 11, 0, 2, 5, 0, HubView.withAlpha(p.ink(), .2f));
        d.circle(thumb, cy, 11, p.sheet());
        d.circle(thumb, cy, 8.5f, p.pine());
        ui.hit(
                "frequency",
                "开屏动画频率",
                start - 12,
                cy - 14,
                step * 2 + 24,
                28,
                () -> setWelcomeFromX(ui.mx));
        String[] labels = {"仅初次", "每次登录", "每次打开"};
        for (int i = 0; i < labels.length; i++) {
            float bx = x + 51 + 128 * i;
            boolean selected = i == index;
            if (selected || ui.inside(bx, y + 195, 124, 27))
                d.roundRect(bx, y + 195, 124, 27, 8, selected ? p.cover() : p.soft());
            ui.textCenter(
                    labels[i], bx + 62, y + 213, 11, selected ? p.pine() : p.muted(), selected);
            String mode = List.of("once", "login", "always").get(i);
            ui.hit(
                    "frequency-label-" + i,
                    labels[i],
                    bx,
                    y + 195,
                    124,
                    27,
                    () -> ui.welcomeMode = mode);
        }
        String explanation =
                reducedMotion
                        ? "已开启减少动态效果，暂不播放开屏动画"
                        : List.of("仅第一次打开手册时播放，以后不再显示", "每次进服后，仅首次打开手册时播放", "每次打开手册时播放").get(index);
        ui.text(explanation, x + 42, y + 252, 11, p.muted(), false);
        ui.text("减少动态效果", x + 27, y + 302, 13, p.ink(), true);
        ui.text("关闭开屏动画、卡片倾斜与页面过渡", x + 27, y + 324, 11, p.muted(), false);
        ui.toggle(
                "reduce-motion",
                ui.localReducedMotion,
                x + 421,
                y + 300,
                () -> ui.setReducedMotion(!ui.localReducedMotion));
        d.line(x + 25, y + 346, x + 457, y + 346, 1, p.ruling());
        ui.text("界面主题", x + 27, y + 377, 13, p.ink(), true);
        ui.text(dark ? "松绿夜色与暖纸灯光" : "松针绿与雪色纸面", x + 27, y + 399, 11, p.muted(), false);
        stationThemeSelector(false, x + 315, y + 362.5f, 140);
        d.line(x + 25, y + 421, x + 457, y + 421, 1, p.ruling());
        ui.text("手册主题", x + 27, y + 451, 13, p.ink(), true);
        ui.text(dark ? "驿站夜灯" : "雪后清晨", x + 27, y + 474, 11, p.muted(), false);
        stationThemeSelector(true, x + 271, y + 437, 184);
        ui.icon("info", x + 25, y + 509, 15, p.muted());
        ui.text(
                ui.live() ? "偏好按玩家与当前服务器／存档隔离保存。" : "偏好保存在当前测试实例；统计与收藏为本地演示。",
                x + 49,
                y + 521,
                11,
                p.muted(),
                false);
    }

    private void stationThemeSelector(boolean style, float x, float y, float w) {
        HubDraw d = ui.d;
        var p = d.winterPaper();
        float cell = (w - 12) / 2;
        boolean right = style ? ui.winter : ui.dark;
        float pos = ui.selectorPosition(style ? "style-position" : "theme-position", right);
        HubWinterSheet.panel(d, x, y, w, 41, 11, p.soft(), 1);
        if (!ui.styleTransition && pos != (right ? 1 : 0)) d.keepThemeControlLive(x, y, w, 41, 11);
        d.roundRect(x + 4 + (cell + 4) * pos, y + 4, cell, 33, 8, p.sheet());
        for (int i = 0; i < 2; i++) {
            boolean second = i == 1;
            String label = style ? (second ? "冬日驿站" : "经典") : (second ? "深色" : "浅色");
            String id =
                    style
                            ? (second ? "style-winter" : "style-classic")
                            : (second ? "theme-dark" : "theme-light");
            float bx = x + 4 + (cell + 4) * i;
            float ix = bx + (cell - 20 - d.textWidth(label, 12, false)) / 2;
            int color = right == second ? p.pine() : p.muted();
            ui.icon(
                    style ? (second ? "snowflake" : "book-open") : (second ? "moon" : "sun"),
                    ix,
                    y + 13,
                    15,
                    color);
            ui.text(label, ix + 20, y + 25.5f, 12, color, false);
            ui.hit(
                    id,
                    label,
                    bx,
                    y + 4,
                    cell,
                    33,
                    () -> {
                        if (style) ui.setWinter(second);
                        else ui.setDark(second);
                    });
        }
    }

    private float frequencyStart() {
        return modalX + (ui.winter ? 114.3f : 92);
    }

    private float frequencyStep() {
        return ui.winter ? 126.65f : 128;
    }

    private void settingsModal() {
        if (ui.winter) {
            stationSettings();
            return;
        }
        HubDraw d = ui.d;
        boolean dark = ui.dark;
        float x = modalX + 28, y = modalY + 118;
        ui.text("开屏动画", x, y, 14, HubView.INK, true);
        ui.textRight(
                "手写 hello", modalX + modalW - 28, y, 10, dark ? 0xff9daac3 : 0xff9694ac, false);
        boolean previousColors = d.literalColors(true);
        d.boxShadow(x, y + 22, 384, 94, 14, 0, 2, 6, 0, ui.a(dark ? 0x1a0a1022 : 0x09767ca2));
        d.linearGradientRoundRect(
                x,
                y + 22,
                384,
                94,
                14,
                125,
                dark ? new int[] {0x80364568, 0x73443655} : new int[] {0x96eaf0ff, 0x8cf1eafe},
                new float[] {0, 1});
        d.insetBoxShadow(x, y + 22, 384, 94, 14, 0, 1, 2, 0, ui.a(dark ? 0x2e0a1533 : 0x18a7acd7));
        d.strokeRoundRect(x + .5f, y + 22.5f, 383, 93, 14, 1, ui.a(dark ? 0x2bb6c4e1 : 0xd9ffffff));
        int selected = ui.welcomeIndex();
        float start = x + 64, trackY = y + 48;
        d.roundRect(start, trackY, 256, 6, 4, ui.a(dark ? 0xff57607b : 0xffd9dced));
        if (selected > 0)
            d.linearGradientRoundRect(
                    start,
                    trackY,
                    selected * 128,
                    6,
                    4,
                    90,
                    dark ? new int[] {0xff8daee8, 0xffb595e8} : new int[] {0xff819de0, 0xff9575da},
                    new float[] {0, 1});
        for (int i = 0; i < 3; i++) {
            d.circle(start + i * 128, trackY + 3, 3, ui.a(dark ? 0xffccd5ec : 0xffffffff));
            d.strokeRoundRect(
                    start + i * 128 - 3, trackY, 6, 6, 3, 1, ui.a(dark ? 0xff64718f : 0xffbdc4dd));
        }
        float thumbX = start + selected * 128 - 11, thumbY = trackY - 8;
        d.boxShadow(thumbX, thumbY, 22, 22, 11, 0, 2, 6, 0, ui.a(dark ? 0x660d153b : 0x406b61ae));
        d.linearGradientRoundRect(
                thumbX,
                thumbY,
                22,
                22,
                11,
                135,
                dark ? new int[] {0xffa5c3f3, 0xffb194e3} : new int[] {0xff96b4f1, 0xff9172d8},
                new float[] {0, 1});
        d.strokeRoundRect(
                thumbX + 1.5f,
                thumbY + 1.5f,
                19,
                19,
                9.5f,
                3,
                ui.a(dark ? 0xffd5ddf2 : 0xffffffff));
        ui.hit(
                "frequency",
                "开屏动画频率",
                start - 15,
                trackY - 12,
                286,
                30,
                () -> setWelcomeFromX(ui.mx));
        String[] labels = {"仅初次", "每次登录", "每次打开"};
        for (int i = 0; i < 3; i++) {
            final int index = i;
            float bx = x + 5 + i * 125;
            if (i == selected) {
                d.boxShadow(
                        bx, y + 74, 124, 28, 8, 0, 1, 3, 0, ui.a(dark ? 0x2b0e1524 : 0x0c767ca2));
                d.roundRect(bx, y + 74, 124, 28, 8, ui.a(dark ? 0x1fc7b6ed : 0x8fffffff));
            }
            float hover =
                    ui.transition(
                            "frequency-label-" + i + "-hover",
                            ui.inside(bx, y + 74, 124, 28) ? 1 : 0,
                            180);
            if (hover > 0)
                d.roundRect(
                        bx,
                        y + 74,
                        124,
                        28,
                        8,
                        ui.a(HubView.withAlpha(dark ? 0x10d2c5ff : 0x66ffffff, hover)));
            ui.textCenter(
                    labels[i],
                    bx + 62,
                    y + 93,
                    11,
                    i == selected
                            ? (dark ? 0xffd5bff9 : 0xff7056af)
                            : HubView.mix(
                                    dark ? 0xffa5b0ca : 0xff81869e,
                                    dark ? 0xffd1bdf1 : 0xff67509d,
                                    hover),
                    i == selected);
            ui.hit(
                    "frequency-label-" + i,
                    labels[i],
                    bx,
                    y + 74,
                    124,
                    28,
                    () -> ui.welcomeMode = List.of("once", "login", "always").get(index));
        }
        d.literalColors(previousColors);
        String explanation =
                ui.reducedMotion
                        ? "减少动态效果已开启，开屏动画会跳过。"
                        : switch (ui.welcomeMode) {
                            case "once" -> "仅首次打开时播放；已经看过的记录会保留。";
                            case "login" -> "每次进入游戏后，首次打开手册时播放。";
                            default -> "每次打开手册时播放约 3 秒的手写问候。";
                        };
        ui.wrap(explanation, x, y + 139, 384, 12, 19.2f, dark ? 0xff9daac3 : 0xff8c8fa5, false);
        d.line(x, y + 169, x + 384, y + 169, 1, 0xffe7e6f1);
        ui.text("减少动态效果", x, y + 203, 14, HubView.INK, true);
        ui.text("关闭开屏动画、卡片倾斜与页面过渡", x, y + 229, 12, 0xff8c8fa5, false);
        ui.toggle(
                "reduce-motion",
                ui.localReducedMotion,
                x + 351,
                y + 190,
                () -> ui.setReducedMotion(!ui.localReducedMotion));
        d.line(x, y + 252, x + 384, y + 252, 1, 0xffe7e6f1);
        ui.text("界面主题", x, y + 284, 14, HubView.INK, true);
        ui.text(dark ? "深灰蓝紫玻璃" : "浅色液态玻璃", x, y + 309, 12, 0xff8c8fa5, false);
        float choiceX = x + 242, choiceY = y + 270;
        previousColors = d.literalColors(true);
        d.roundRect(choiceX, choiceY, 142, 42, 12, ui.a(dark ? 0xff20283b : 0xfff5f6ff));
        d.strokeRoundRect(
                choiceX + .5f, choiceY + .5f, 141, 41, 12, 1, ui.a(dark ? 0x29a5b7e0 : 0xffdfe4ef));
        float themePosition = ui.selectorPosition("theme-position", dark);
        if (!ui.styleTransition && themePosition != (dark ? 1 : 0))
            d.keepThemeControlLive(choiceX, choiceY, 142, 42, 12);
        float thumbLeft = choiceX + 5 + 68 * themePosition;
        d.boxShadow(thumbLeft, choiceY + 5, 64, 32, 8, 0, 2, 7, 0, ui.a(0x12303b64));
        d.roundRect(thumbLeft, choiceY + 5, 64, 32, 8, ui.a(dark ? 0xff252d44 : 0xffffffff));
        for (int index = 0; index < 2; index++) {
            boolean chosen = (index == 1) == dark;
            float bx = choiceX + 5 + index * 68;
            int color =
                    chosen ? (dark ? 0xffa28ae7 : 0xff765bd4) : (dark ? 0xffaeb9d0 : 0xff6c7891);
            ui.icon(index == 0 ? "sun" : "moon", bx + 9, choiceY + 13.5f, 15, color);
            ui.text(index == 0 ? "浅色" : "深色", bx + 30, choiceY + 26, 11, color, false);
            final boolean chooseDark = index == 1;
            ui.hit(
                    index == 0 ? "theme-light" : "theme-dark",
                    index == 0 ? "浅色主题" : "深色主题",
                    bx,
                    choiceY + 5,
                    64,
                    32,
                    () -> ui.setDark(chooseDark));
        }
        d.literalColors(previousColors);
        d.line(x, y + 335, x + 384, y + 335, 1, 0xffe7e6f1);
        handbookStyleChoice(x, y + 341);
        d.line(x, y + 402, x + 384, y + 402, 1, 0xffe7e6f1);
        ui.icon("info", x, y + 413, 15, 0xff8c8fa5);
        ui.wrap(
                ui.live() ? "偏好按玩家与当前服务器／存档隔离保存。" : "偏好保存在当前测试实例；统计与收藏为本地演示。",
                x + 25,
                y + 425,
                359,
                11,
                18,
                0xff8c8fa5,
                false);
    }

    private String modpackSyncSubtitle() {
        HubData data = ui.data;
        String pack =
                ui.live() ? (data.packName.isBlank() ? ui.connectionName : data.packName) : "香草纪元";
        String ver = ui.live() ? data.packVersion : "2.7.1";
        return (ver.isBlank() ? pack : pack + " " + ver) + " · 管理自动同步与推送频道";
    }

    private void modpackSyncModal() {
        if (!HubModpackSync.available()) {
            ui.dismissOverlay();
            return;
        }
        boolean enabled = HubModpackSync.enabled();
        boolean granted = HubModpackSync.granted();
        boolean preview = "preview".equals(HubModpackSync.channel());
        boolean active = enabled && granted;
        boolean winter = ui.winter, dark = ui.dark;
        HubDraw d = ui.d;
        var p = d.winterPaper();
        float x = modalX + (winter ? 27 : 28);
        float w = modalW - (winter ? 54 : 56);
        float y = modalY + (winter ? 112 : 114);
        int ink = winter ? p.ink() : HubView.INK;
        int muted = winter ? p.muted() : (dark ? 0xff9daac3 : 0xff8c8fa5);
        int rule = winter ? p.ruling() : 0xffe7e6f1;

        ui.text("自动同步整合包", x, y + 18, winter ? 13 : 14, ink, true);
        ui.text(
                active ? "连接服务器时自动从可信 OSS 校验并暂存更新" : "已暂停自动同步，不会改动本地模组与配置",
                x,
                y + 42,
                winter ? 11 : 12,
                muted,
                false);
        ui.toggle(
                "sync-enabled",
                active,
                x + w - 34,
                y + 10,
                () -> {
                    boolean next = !active;
                    if (!HubModpackSync.setEnabled(next)) {
                        ui.feedbackError("同步配置写入失败，请稍后重试");
                        return;
                    }
                    if (next && !granted) {
                        ui.feedbackSuccess("已开启自动同步，下次连接服务器时将确认授权");
                    } else {
                        ui.feedbackSuccess(next ? "已开启整合包自动同步" : "已关闭整合包自动同步");
                    }
                });

        float row2 = y + 66;
        d.line(x, row2, x + w, row2, 1, rule);
        ui.text("推送频道", x, row2 + 30, winter ? 13 : 14, ink, true);
        ui.text(
                preview ? "优先接收预览版更新；切回正式版后下次连服自动回退" : "仅接收稳定测试通过的正式版整合包更新（默认）",
                x,
                row2 + 54,
                winter ? 11 : 12,
                muted,
                false);
        float segW = 140;
        float segH = 40;
        float segX = x + w - segW;
        float segY = row2 + 16;
        float cell = (segW - 12) / 2;
        float pos = ui.selectorPosition("sync-channel-position", preview);
        if (winter) {
            HubWinterSheet.panel(d, segX, segY, segW, segH, 11, p.soft(), 1);
            if (pos != (preview ? 1 : 0)) d.keepThemeControlLive(segX, segY, segW, segH, 11);
            d.roundRect(segX + 4 + (cell + 4) * pos, segY + 4, cell, segH - 8, 8, p.sheet());
            for (int i = 0; i < 2; i++) {
                boolean isPrev = i == 1;
                String label = isPrev ? "预览版" : "正式版";
                float bx = segX + 4 + (cell + 4) * i;
                int color = preview == isPrev ? p.pine() : p.muted();
                ui.textCenter(label, bx + cell / 2, segY + 24.5f, 12, color, preview == isPrev);
                ui.hit(
                        isPrev ? "sync-channel-preview" : "sync-channel-stable",
                        "切换为" + label,
                        bx,
                        segY + 4,
                        cell,
                        segH - 8,
                        () -> switchSyncChannel(isPrev, preview));
            }
        } else {
            boolean prevLiteral = d.literalColors(true);
            d.roundRect(segX, segY, segW, segH, 12, ui.a(dark ? 0xff20283b : 0xfff5f6ff));
            d.strokeRoundRect(
                    segX + .5f,
                    segY + .5f,
                    segW - 1,
                    segH - 1,
                    12,
                    1,
                    ui.a(dark ? 0x29a5b7e0 : 0xffdfe4ef));
            if (pos != (preview ? 1 : 0)) d.keepThemeControlLive(segX, segY, segW, segH, 12);
            float thumbLeft = segX + 4 + (cell + 4) * pos;
            d.boxShadow(thumbLeft, segY + 4, cell, segH - 8, 8, 0, 2, 7, 0, ui.a(0x12303b64));
            d.roundRect(
                    thumbLeft, segY + 4, cell, segH - 8, 8, ui.a(dark ? 0xff252d44 : 0xffffffff));
            for (int i = 0; i < 2; i++) {
                boolean isPrev = i == 1;
                String label = isPrev ? "预览版" : "正式版";
                float bx = segX + 4 + (cell + 4) * i;
                int color =
                        preview == isPrev
                                ? (dark ? 0xffa28ae7 : 0xff765bd4)
                                : (dark ? 0xffaeb9d0 : 0xff6c7891);
                ui.textCenter(label, bx + cell / 2, segY + 24.5f, 11.5f, color, preview == isPrev);
                ui.hit(
                        isPrev ? "sync-channel-preview" : "sync-channel-stable",
                        "切换为" + label,
                        bx,
                        segY + 4,
                        cell,
                        segH - 8,
                        () -> switchSyncChannel(isPrev, preview));
            }
            d.literalColors(prevLiteral);
        }

        float row3 = row2 + 76;
        d.line(x, row3, x + w, row3, 1, rule);
        ui.text("本地同步授权", x, row3 + 30, winter ? 13 : 14, ink, true);
        ui.text(
                granted ? "已授权 · 仅处理签名清单内的受管目录，不触碰存档" : "未授权 · 下次连接服务器时将弹出首次授权确认",
                x,
                row3 + 54,
                winter ? 11 : 12,
                muted,
                false);
        float btnW = 84, btnH = 30;
        float btnX = x + w - btnW, btnY = row3 + 22;
        if (granted) {
            boolean hovered = ui.inside(btnX, btnY, btnW, btnH);
            boolean prevLiteral = d.literalColors(true);
            int bg =
                    winter
                            ? HubView.withAlpha(p.mail(), hovered ? .20f : .12f)
                            : dark
                                    ? (hovered ? 0x2eee8998 : 0x1cee8998)
                                    : (hovered ? 0x22d94c62 : 0x12d94c62);
            int border =
                    winter
                            ? HubView.withAlpha(p.mail(), .42f)
                            : dark ? (hovered ? 0x80ee8998 : 0x4dee8998) : 0x66d98290;
            int fg = winter ? p.mailInk() : dark ? 0xfff095a3 : 0xffb83d52;
            d.roundRect(btnX, btnY, btnW, btnH, 9, bg);
            d.strokeRoundRect(btnX + .5f, btnY + .5f, btnW - 1, btnH - 1, 9, 1, border);
            ui.textCenter("撤销授权", btnX + btnW / 2, btnY + 19.5f, 11.5f, fg, false);
            d.literalColors(prevLiteral);
            ui.hit(
                    "sync-revoke",
                    "撤销本地同步授权",
                    btnX,
                    btnY,
                    btnW,
                    btnH,
                    () -> {
                        if (HubModpackSync.revokeAll()) {
                            ui.feedback("已撤销本地同步授权并暂停自动同步");
                        } else {
                            ui.feedbackError("撤销失败，请检查本地配置权限");
                        }
                    });
        } else {
            boolean prevLiteral = d.literalColors(true);
            int bg = winter ? p.soft() : (dark ? 0x24ffffff : 0x14708098);
            d.roundRect(btnX, btnY, btnW, btnH, 9, bg);
            ui.textCenter("未授权", btnX + btnW / 2, btnY + 19.5f, 11.5f, muted, false);
            d.literalColors(prevLiteral);
        }

        float noteY = row3 + 78;
        d.line(x, noteY, x + w, noteY, 1, rule);
        ui.icon("info", x, noteY + 15, 15, muted);
        ui.text("修改会写入本地客户端同步配置，重新连接服务器后自动生效。", x + 24, noteY + 27, 11, muted, false);
    }

    private void switchSyncChannel(boolean toPreview, boolean currentPreview) {
        if (toPreview == currentPreview) return;
        if (!HubModpackSync.setChannel(toPreview ? "preview" : "stable")) {
            ui.feedbackError("频道切换失败，请稍后重试");
            return;
        }
        ui.feedbackSuccess(toPreview ? "已切换为预览版，下次连接服务器时生效" : "已切回正式版，下次连服将自动安全回退");
    }

    private void profileModal() {
        HubDraw d = ui.d;
        HubData data = ui.data;
        boolean winter = ui.winter, live = ui.live();
        long now = ui.now;
        float cx = 630, top = modalY + 115;
        if (winter) {
            var p = d.winterPaper();
            HubHead.draw(d, modalX + 33, modalY + 119, 48, -23);
            ui.text(
                    ui.fit(ui.playerName, 304, 22, true),
                    modalX + 106,
                    modalY + 141,
                    22,
                    p.ink(),
                    true);
            HubData.Title worn = data.title(ui.equipped);
            if (worn == null) ui.text("暂未佩戴称号", modalX + 106, modalY + 167, 14, p.muted(), false);
            else ui.titleLabel(worn, modalX + 106, modalY + 167, 14, 1, false, false, 298);
        } else {
            ui.glass(cx - 46, top, 92, 92, 24, 0xffe5ecff, 0xffeae1ff);
            HubHead.draw(d, cx - 27, top + 21, 54, -23);
            ui.textCenter(
                    ui.fit(ui.playerName, 480, 24, true), cx, top + 130, 24, HubView.INK, true);
            HubData.Title worn = data.title(ui.equipped);
            if (worn == null) ui.textCenter("暂未佩戴称号", cx, top + 160, 14, HubView.MUTED, true);
            else ui.titleLabel(worn, cx, top + 160, 14, 1.4f, true, false);
        }
        String[][] rows =
                live
                        ? new String[][] {
                            {"首次加入", ui.timestamp(data.firstJoinedAt, "yyyy/MM/dd HH:mm:ss")},
                            {
                                "当前存档 · 累计在线",
                                data.managerLoaded
                                        ? data.onlineMillis(now) / 3600000
                                                + " 小时 "
                                                + data.onlineMillis(now) / 60000 % 60
                                                + " 分钟"
                                        : "暂无记录"
                            },
                            {"当前存档 · 累计登录", data.managerLoaded ? data.joinCount + " 次" : "暂无记录"},
                            {"玩家 UUID", ui.selfPlayerId().isEmpty() ? "正在读取" : ui.selfPlayerId()}
                        }
                        : new String[][] {
                            {"首次加入 · 演示", "2026/10/17"},
                            {"累计在线 · 演示", "128 小时 42 分钟"},
                            {"累计登录 · 演示", "36 次"},
                            {"玩家 UUID · 演示身份", "7f1a0000-0000-4000-8000-000000000001"}
                        };
        if (winter) {
            var p = d.winterPaper();
            for (int i = 0; i < rows.length; i++) {
                float line = modalY + 193 + i * 36;
                d.line(modalX + 26, line, modalX + 414, line, 1, p.ruling());
                ui.text(rows[i][0], modalX + 26, line + 23, 12, p.muted(), false);
                if (i < 3) ui.textRight(rows[i][1], modalX + 414, line + 23, 13, p.ink(), false);
                else {
                    ui.text(rows[i][1], modalX + 26, line + 50, 13, p.ink(), false);
                    String uuid = rows[i][1];
                    if (uuid.length() == 36 && ui.copyToClipboard != null)
                        ui.iconButton(
                                "account-copy",
                                "复制 UUID",
                                "copy",
                                modalX + 378,
                                line + 30,
                                28,
                                () -> {
                                    ui.copyToClipboard.accept(uuid);
                                    ui.feedbackSuccess("UUID 已复制");
                                },
                                true);
                }
            }
            return;
        }
        for (int i = 0; i < rows.length; i++) {
            float y = top + 207 + i * 48;
            d.line(modalX + 28, y - 21, modalX + modalW - 28, y - 21, 1, 0xffe7e6f1);
            ui.text(rows[i][0], modalX + 28, y, 11, 0xff8c8fa5, false);
            ui.textRight(rows[i][1], modalX + modalW - 28, y, i == 3 ? 10 : 12, 0xff59657f, false);
        }
        ui.textCenter(
                live ? "统计来自当前服务器／存档。" : "名字与头像取自本地角色，其他资料均为样例。",
                cx,
                modalY + modalH - 28,
                11,
                HubView.MUTED,
                false);
    }

    private void previewModal() {
        float x = modalX + 28, y = modalY + 120;
        ui.text("今日抽签状态", x, y, 13, HubView.INK, true);
        String[] labels = {"尚未抽签", "等待解签", "已经解签", "已收进藏签匣"};
        int fortuneState = host.fortuneState();
        for (int i = 0; i < 4; i++) {
            final int state = i;
            float bx = x + (i % 2) * 197, by = y + 22 + (i / 2) * 55;
            ui.button(
                    "preview-state-" + i,
                    (fortuneState == i ? "✓ " : "") + labels[i],
                    bx,
                    by,
                    187,
                    42,
                    fortuneState == i,
                    true,
                    () -> {
                        host.setFortuneState(state);
                        ui.notifyUser("今日状态：" + host.stateLabel() + " · 本地演示");
                    });
        }
        ui.wrap("未解签不会显示结果；已解但未归档的签不会出现在收藏和月历中。", x, y + 164, 384, 12, 22, 0xff8c8fa5, false);
        ui.button(
                "reset-demo",
                "还原演示数据",
                x,
                y + 236,
                148,
                37,
                false,
                true,
                () -> {
                    host.resetDemoData();
                    ui.notifyUser("样例已还原，界面偏好保留");
                });
        ui.button(
                "ranking-demo",
                "播放排行变动",
                x + 164,
                y + 236,
                220,
                37,
                false,
                true,
                () -> {
                    ui.dismissOverlay();
                    ui.navigate(4);
                    host.playRankingDemo();
                });
        ui.text("F8 打开此面板 · 界面偏好不参与演示还原", x, y + 295, 11, HubView.MUTED, false);
    }
}
