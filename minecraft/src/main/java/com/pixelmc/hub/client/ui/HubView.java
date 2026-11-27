/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.ui;

import com.google.gson.JsonObject;
import com.pixelmc.hub.client.HubModpackSync;
import com.pixelmc.hub.client.render.GlassStyle;
import com.pixelmc.hub.client.render.HubDraw;
import com.pixelmc.hub.client.render.IconPose;

import io.github.humbleui.skija.Matrix33;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/** Native rendering of the approved handbook, with explicitly separate live and demo data. */
public final class HubView {
    static final int INK = 0xff26344f, MUTED = 0xff73809a, PURPLE = 0xff765bd4;
    static final float LEFT = 236, WIDTH = 996, RIGHT = 922;
    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy / MM / dd");
    static final String WINTER_ART = "images/winter/";
    private static final int[] SNOW_X = {9, 17, 25, 32, 14, 43, 50, 57, 62, 69, 77, 84, 91, 54};
    private static final int[] SNOW_Y = {22, 55, 14, 36, 79, 20, 55, 10, 35, 18, 62, 29, 52, 76};
    private static final float[] FOOT_X = {61, 63.5f, 65.5f, 68}, FOOT_Y = {2, 3.3f, 5.4f, 7};
    private static final String HELLO_PATH =
            "M24 111 C42 99 63 77 72 48 C83 14 67 11 58 29 C48 49 49 86 41 121 C48 94 60 77 73 81"
                + " C92 88 64 127 88 124 C101 122 111 111 120 101 C139 80 143 95 128 104 C99 122"
                + " 112 139 139 123 C160 111 176 80 185 48 C194 13 177 12 170 33 C162 57 159 100"
                + " 157 113 C154 140 178 126 194 105 C211 82 222 51 224 32 C228 11 210 14 206 38"
                + " C201 65 196 102 199 116 C201 136 220 125 235 108 C253 85 268 91 262 112 C256"
                + " 136 231 136 229 116 C227 98 243 87 255 96 C270 107 284 109 303 93";
    private static final String FLOURISH_PATH =
            "M97 145 C153 133 229 134 283 142 C302 145 316 143 329 136";

    final HubData data = new HubData();
    final List<Hit> hits = new ArrayList<>();
    final List<Hit> nextHits = new ArrayList<>();
    private final Map<String, HubMotion.Transition> transitions = new HashMap<>();
    final Map<String, HubMotion.Tilt> tilts = new HashMap<>();
    private final Map<String, HubMotion.Navigation> extensionNavigation = new HashMap<>();

    private record ExternalSlot(HubPageRegistry.Registration registration, HubExternalPage page) {}

    private final List<ExternalSlot> externalSlots = new ArrayList<>();
    private long externalRegistryVersion = -1;
    private LongSupplier windowHandleProvider = () -> 0L;
    private final Deque<Matrix33> matrixStack = new ArrayDeque<>();
    private final Deque<Float> alphaStack = new ArrayDeque<>();
    private Matrix33 logical = Matrix33.IDENTITY;
    private Matrix33 titleClipTransform = Matrix33.IDENTITY, pageTransform = Matrix33.IDENTITY;
    HubDraw d;
    float mx, my, opacity = 1, offsetY, interactiveAlpha = 1, dt = 1f / 60;
    long now = System.nanoTime();
    private long openedAt = now, pageAt = now, lastFrame = now, navAt = now;
    int page, sort, stationPage;
    private String sessionPlayerId = "";
    String playerName = "zhan_cpu",
            equipped = "dew",
            selectedTitle = "dew",
            welcomeMode = "login",
            stationBio = "";
    boolean dark, winter;
    private boolean communityLoaded;
    boolean styleTransition;
    Runnable afterProfileClose, beforeThemeChange;
    boolean localReducedMotion;
    private boolean systemReducedMotion;
    boolean visible = true, reducedMotion;
    private boolean closeRequest, playWelcome;
    boolean compact;
    float titleScroll;
    YearMonth month = YearMonth.from(data.today);
    LocalDate selectedDate = data.today;
    String overlay, focusId, restoreFocus;
    private String pressedId;
    private final HubToast toast = new HubToast();
    private Hit pressedHit;
    boolean keyboardFocus;
    private boolean pressMoved, keyboardPress;
    private float pressX, pressY;
    HubData.Entry reading;
    float viewportX, viewportY, viewportW = 1260, viewportH = 850;
    private int memoriesReturnPage;
    private long rankingsPreviewAt;
    private final Map<String, String> moduleStates = new HashMap<>();
    final java.util.Set<String> pendingModules = new java.util.HashSet<>();
    boolean catalogueComplete = true;
    JsonObject titleDraft;
    String connectionName = "当前存档";
    byte[] serverIcon;
    Consumer<String> copyToClipboard;

    @FunctionalInterface
    public interface PlayerAction {
        void request(String module, String action, JsonObject payload);
    }

    private PlayerAction actions;

    private final HubHomeView homeView =
            new HubHomeView(
                    this,
                    new HubHomeView.Host() {
                        @Override
                        public void openPublicProfile(String playerId) {
                            HubView.this.openPublicProfile(playerId);
                        }

                        @Override
                        public void openMemories(String playerId, String archiveId) {
                            HubView.this.openMemories(playerId, archiveId);
                        }

                        @Override
                        public boolean updatesLoaded() {
                            return updates.loaded();
                        }

                        @Override
                        public boolean updatesEnabled() {
                            return updates.enabled();
                        }

                        @Override
                        public List<JsonObject> stationNotices() {
                            return updates.stationNotices();
                        }

                        @Override
                        public String stationNoticesEmptyText() {
                            return updates.stationNoticesEmptyText();
                        }

                        @Override
                        public void openStationNotice(JsonObject row) {
                            updates.openStationNotice(row);
                        }

                        @Override
                        public int fortuneState() {
                            return fortunesView.fortuneState();
                        }

                        @Override
                        public String stateLabel() {
                            return fortunesView.stateLabel();
                        }

                        @Override
                        public HubData.Entry todayEntry() {
                            return fortunesView.todayEntry();
                        }

                        @Override
                        public int titleCount() {
                            return HubView.this.titleCount();
                        }

                        @Override
                        public int archiveCount() {
                            return fortunesView.archiveCount();
                        }

                        @Override
                        public Map<LocalDate, HubData.Entry> archive() {
                            return fortunesView.archive();
                        }
                    });
    private final HubTitlesView titlesView = new HubTitlesView(this);
    private final HubFortunesView fortunesView =
            new HubFortunesView(
                    this,
                    new HubFortunesView.Host() {
                        @Override
                        public long modalAt() {
                            return modals.modalAt();
                        }

                        @Override
                        public void setModalBounds(float x, float y, float w, float h) {
                            modals.setBounds(x, y, w, h);
                        }

                        @Override
                        public void showReading(HubData.Entry entry) {
                            HubView.this.showReading(entry);
                        }
                    });
    private final HubModalsView modals =
            new HubModalsView(
                    this,
                    new HubModalsView.Host() {
                        @Override
                        public int fortuneState() {
                            return fortunesView.fortuneState();
                        }

                        @Override
                        public void setFortuneState(int state) {
                            fortunesView.setFortuneState(state);
                        }

                        @Override
                        public String stateLabel() {
                            return fortunesView.stateLabel();
                        }

                        @Override
                        public void resetDemoData() {
                            equipped = "dew";
                            visible = true;
                            fortunesView.resetDemo();
                            selectedDate = data.today;
                            rankings.demo();
                        }

                        @Override
                        public void playRankingDemo() {
                            rankingsPreviewAt = System.nanoTime() + 500_000_000L;
                        }

                        @Override
                        public void renderWinterFortuneDialog() {
                            fortunesView.renderWinterFortuneDialog();
                        }

                        @Override
                        public void renderReadingModal(
                                float modalX, float modalY, float modalW, float modalH) {
                            fortunesView.renderReadingModal(modalX, modalY, modalW, modalH);
                        }

                        @Override
                        public void renderFortuneHelp(float modalX, float modalY, float modalH) {
                            fortunesView.renderFortuneHelp(modalX, modalY, modalH);
                        }

                        @Override
                        public void renderTitleNameModal(float modalX, float modalY, float modalW) {
                            titlesView.renderTitleNameModal(modalX, modalY, modalW);
                        }

                        @Override
                        public void renderTitleDisplayModal(
                                float modalX, float modalY, float modalW) {
                            titlesView.renderTitleDisplayModal(modalX, modalY, modalW);
                        }

                        @Override
                        public void scrollReading(double amount) {
                            fortunesView.scrollReading(amount);
                        }

                        @Override
                        public boolean keyScrollReading(int keyCode, float modalH) {
                            return fortunesView.keyScrollReading(keyCode, modalH);
                        }
                    });

    private final HubUpdatesView updates =
            new HubUpdatesView(
                    new HubUpdatesView.Host() {
                        public void hit(
                                String id,
                                String label,
                                float x,
                                float y,
                                float w,
                                float h,
                                Runnable action) {
                            HubView.this.hit(id, label, x, y, w, h, action);
                        }

                        public void request(String action, JsonObject payload) {
                            HubView.this.request("updates", action, payload);
                        }

                        public void feedback(String text) {
                            HubView.this.feedbackSuccess(text);
                        }

                        public void focus(String id) {
                            focusId = id;
                        }
                    });
    private final HubLeaderboardsView rankings =
            new HubLeaderboardsView(
                    new HubLeaderboardsView.Host() {
                        public void hit(
                                String id,
                                String label,
                                float x,
                                float y,
                                float w,
                                float h,
                                Runnable action) {
                            HubView.this.hit(id, label, x, y, w, h, action);
                        }

                        public void request(String action, JsonObject payload) {
                            HubView.this.request("rankings", action, payload);
                        }

                        public void focus(String id) {
                            focusId = id;
                        }

                        public void feedback(String message) {
                            HubView.this.feedback(message);
                        }

                        public void openProfile(String playerId) {
                            HubView.this.openPublicProfile(playerId);
                        }
                    });
    private final HubProfileView profiles =
            new HubProfileView(
                    new HubProfileView.Host() {
                        public void hit(
                                String id,
                                String label,
                                float x,
                                float y,
                                float w,
                                float h,
                                Runnable action) {
                            HubView.this.hit(id, label, x, y, w, h, action);
                        }

                        public void request(String action, JsonObject payload) {
                            HubView.this.request("community", action, payload);
                        }

                        public void feedback(String message, boolean error) {
                            if (error) feedbackError(message);
                            else feedbackSuccess(message);
                        }

                        public void focus(String id) {
                            focusId = id;
                        }

                        public void openMemories(String playerId, String archiveId) {
                            afterProfileClose =
                                    () -> HubView.this.openMemories(playerId, archiveId);
                            profiles.requestClose();
                        }

                        public void closeProfile() {
                            focusId = restoreFocus;
                            restoreFocus = null;
                            hits.clear();
                            Runnable next = afterProfileClose;
                            afterProfileClose = null;
                            if (next != null) next.run();
                        }

                        public void openAccount() {
                            afterProfileClose = () -> openOverlay("profile");
                            profiles.requestClose();
                        }
                    });
    private final HubMemoriesView memories =
            new HubMemoriesView(
                    new HubMemoriesView.Host() {
                        public void hit(
                                String id,
                                String label,
                                float x,
                                float y,
                                float w,
                                float h,
                                Runnable action) {
                            HubView.this.hit(id, label, x, y, w, h, action);
                        }

                        public void request(String action, JsonObject payload) {
                            HubView.this.request("community", action, payload);
                        }

                        public void feedback(String message, boolean error) {
                            if (error) feedbackError(message);
                            else feedbackSuccess(message);
                        }

                        public void focus(String id) {
                            focusId = id;
                        }

                        public void back() {
                            leaveMemories();
                        }

                        public void openProfile(String playerId) {
                            openPublicProfile(playerId);
                        }
                    });

    private record Hit(
            String id,
            String label,
            float x,
            float y,
            float w,
            float h,
            Matrix33 transform,
            Matrix33 viewport,
            Runnable action) {
        boolean contains(float px, float py) {
            if (viewport != null) {
                float[] clip = HubDraw.inversePoint(viewport, px, py);
                if (clip[0] < 230 || clip[1] < 272 || clip[0] >= 904 || clip[1] >= 794)
                    return false;
            }
            float[] p = HubDraw.inversePoint(transform, px, py);
            return p[0] >= x && p[1] >= y && p[0] < x + w && p[1] < y + h;
        }
    }

    private abstract class BuiltInAdapter implements HubBuiltInPage {
        private final int index;
        private final String id;
        private final String label;
        private final String section;
        private final NavigationEntry entry;
        private final HubMotion.Navigation nav;

        BuiltInAdapter(
                int index,
                String id,
                String label,
                String section,
                String iconName,
                int topColor,
                int bottomColor) {
            this.index = index;
            this.id = id;
            this.label = label;
            this.section = section;
            this.entry =
                    iconName == null
                            ? null
                            : builtInEntry(index, label, iconName, topColor, bottomColor);
            this.nav = iconName == null ? null : new HubMotion.Navigation();
        }

        @Override
        public int index() {
            return index;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String label() {
            return label;
        }

        @Override
        public String section() {
            return section;
        }

        @Override
        public NavigationEntry navigationEntry() {
            return entry;
        }

        @Override
        public HubMotion.Navigation navigationState() {
            return nav;
        }
    }

    private final HubPageHost pageHost =
            new HubPageHost() {
                @Override
                public HubDraw draw() {
                    return d;
                }

                @Override
                public Matrix33 logical() {
                    return logical;
                }

                @Override
                public float mx() {
                    return mx;
                }

                @Override
                public float my() {
                    return my;
                }

                @Override
                public long now() {
                    return now;
                }

                @Override
                public boolean dark() {
                    return dark;
                }

                @Override
                public boolean winter() {
                    return winter;
                }

                @Override
                public boolean reducedMotion() {
                    return reducedMotion;
                }

                @Override
                public String focusId() {
                    return focusId;
                }

                @Override
                public void clearNextHits() {
                    nextHits.clear();
                }

                @Override
                public void hit(
                        String id,
                        String label,
                        float x,
                        float y,
                        float w,
                        float h,
                        Runnable action) {
                    HubView.this.hit(id, label, x, y, w, h, action);
                }

                @Override
                public void navigateBuiltIn(int destination) {
                    HubView.this.navigate(destination);
                }

                @Override
                public boolean navigatePage(String id) {
                    return HubView.this.navigatePage(id);
                }

                @Override
                public void drawExtensionNavigation(
                        NavigationEntry entry, int slot, boolean visible) {
                    HubView.this.drawExtensionNavigation(entry, slot, visible);
                }

                @Override
                public float extensionHover(String id, boolean hovered) {
                    return HubView.this.extensionHover(id, hovered);
                }

                @Override
                public float extensionButtonMove(String id, boolean enabled, boolean hovered) {
                    return HubView.this.extensionButtonMove(id, enabled, hovered);
                }

                @Override
                public void beginAlpha(float alpha, float x, float y, float w, float h) {
                    HubView.this.beginAlpha(alpha, x, y, w, h);
                }

                @Override
                public void endAlpha() {
                    HubView.this.endAlpha();
                }

                @Override
                public void push(Matrix33 transform) {
                    HubView.this.push(transform);
                }

                @Override
                public void pop() {
                    HubView.this.pop();
                }

                @Override
                public void focus(String id) {
                    focusId = id;
                }

                @Override
                public void feedback(String value) {
                    HubView.this.feedback(value);
                }

                @Override
                public void feedbackSuccess(String value) {
                    HubView.this.feedbackSuccess(value);
                }

                @Override
                public void feedbackError(String value) {
                    HubView.this.feedbackError(value);
                }

                @Override
                public void clearKeyboardFocus() {
                    keyboardFocus = false;
                }

                @Override
                public void setKeyboardFocus(boolean focused) {
                    keyboardFocus = focused;
                }

                @Override
                public long windowHandle() {
                    return windowHandleProvider.getAsLong();
                }
            };

    private final List<HubBuiltInPage> builtInPages =
            List.of(
                    homeView,
                    titlesView,
                    fortunesView,
                    new BuiltInAdapter(
                            3, "updates", "更新日志", "服务器", "newspaper", 0xff3cd77f, 0xff109e73) {
                        @Override
                        public boolean visible() {
                            return !(updates.loaded() && !updates.enabled());
                        }

                        @Override
                        public boolean unread() {
                            return updates.sectionUnread();
                        }

                        @Override
                        public void onEnter(long now) {
                            updates.activate();
                        }

                        @Override
                        public void onLeave(int nextDestination) {
                            updates.deactivate();
                        }

                        @Override
                        public void onPanelVisibility(boolean open) {
                            if (!open) updates.deactivate();
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
                            updates();
                        }

                        @Override
                        public boolean dismissOverlay() {
                            return updates.dismiss();
                        }

                        @Override
                        public boolean pointerDown(
                                float x, float y, float pageX, float pageY, boolean shift) {
                            return updates.pointerDown(pageX, pageY, shift);
                        }

                        @Override
                        public boolean pointerUp(float pageX, float pageY) {
                            if (overlay != null || reading != null) return false;
                            updates.pointerAt(pageX, pageY);
                            return updates.pointerUp();
                        }

                        @Override
                        public boolean pointerDrag(float pageX, float pageY) {
                            return updates.pointerDrag(pageX, pageY);
                        }

                        @Override
                        public boolean scroll(float pageX, float pageY, double amount) {
                            return updates.scroll(pageX, pageY, amount);
                        }

                        @Override
                        public boolean keyPressed(int key, int scanCode, int mods, String focused) {
                            return updates.key(key, mods, focused);
                        }

                        @Override
                        public boolean charTyped(char c, int mods, String focused) {
                            updates.type(c, focused);
                            return true;
                        }

                        @Override
                        public boolean textFocused(String focused) {
                            return updates.textFocused(focused);
                        }

                        @Override
                        public String selectedText(String focused) {
                            return updates.selectedText(focused);
                        }

                        @Override
                        public String cutText(String focused) {
                            return updates.cutText(focused);
                        }

                        @Override
                        public void pasteText(String value, String focused) {
                            updates.paste(value, focused);
                        }

                        @Override
                        public String pointerCursor(float pageX, float pageY) {
                            return updates.pointerOnRail(pageX, pageY) ? "pointer" : null;
                        }
                    },
                    new BuiltInAdapter(
                            4, "leaderboards", "玩家排行", "服务器", "trophy", 0xffff78c8, 0xffdf29a0) {
                        @Override
                        public void onEnter(long now) {
                            rankings.activate();
                        }

                        @Override
                        public void onLeave(int nextDestination) {
                            rankings.deactivate();
                        }

                        @Override
                        public void onPanelVisibility(boolean open) {
                            if (open) rankings.activate();
                            else rankings.deactivate();
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
                            leaderboards();
                        }

                        @Override
                        public boolean pointerDown(
                                float x, float y, float pageX, float pageY, boolean shift) {
                            return rankings.pointerDown(pageX, pageY);
                        }

                        @Override
                        public boolean pointerUp(float pageX, float pageY) {
                            return rankings.pointerUp();
                        }

                        @Override
                        public boolean pointerDrag(float pageX, float pageY) {
                            return rankings.pointerDrag(pageY);
                        }

                        @Override
                        public boolean scroll(float pageX, float pageY, double amount) {
                            return rankings.scroll(pageX, pageY, amount);
                        }

                        @Override
                        public boolean keyPressed(int key, int scanCode, int mods, String focused) {
                            return rankings.key(key, focused);
                        }

                        @Override
                        public String pointerCursor(float pageX, float pageY) {
                            return rankings.pointerOnRail(pageX, pageY) ? "pointer" : null;
                        }
                    },
                    new BuiltInAdapter(5, "memories", "旅程档案", "我的空间", null, 0, 0) {
                        @Override
                        public void onLeave(int nextDestination) {
                            memories.deactivate();
                        }

                        @Override
                        public void onPanelVisibility(boolean open) {
                            if (!open) memories.deactivate();
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
                            memories();
                        }

                        @Override
                        public boolean escape() {
                            leaveMemories();
                            return true;
                        }

                        @Override
                        public boolean dismissOverlay() {
                            return memories.dismiss();
                        }

                        @Override
                        public boolean pointerDown(
                                float x, float y, float pageX, float pageY, boolean shift) {
                            return memories.pointerDown(pageX, pageY, shift);
                        }

                        @Override
                        public boolean pointerUp(float pageX, float pageY) {
                            return memories.pointerUp();
                        }

                        @Override
                        public boolean pointerDrag(float pageX, float pageY) {
                            return memories.pointerDrag(pageX, pageY);
                        }

                        @Override
                        public boolean scroll(float pageX, float pageY, double amount) {
                            return memories.scroll(pageX, pageY, amount);
                        }

                        @Override
                        public boolean keyPressed(int key, int scanCode, int mods, String focused) {
                            return memories.key(key, mods, focused);
                        }

                        @Override
                        public boolean charTyped(char c, int mods, String focused) {
                            memories.type(c, focused);
                            return true;
                        }

                        @Override
                        public boolean textFocused(String focused) {
                            return memories.textFocused(focused);
                        }

                        @Override
                        public String selectedText(String focused) {
                            return memories.selectedText(focused);
                        }

                        @Override
                        public String cutText(String focused) {
                            return memories.cutText(focused);
                        }

                        @Override
                        public void pasteText(String value, String focused) {
                            memories.paste(value, focused);
                        }
                    });

    public HubView() {
        updates.demo();
        rankings.demo();
        syncExternalPages();
    }

    public boolean winter() {
        return winter;
    }

    public void setWinter(boolean value) {
        if (winter == value) return;
        styleTransition = true;
        if (beforeThemeChange != null) beforeThemeChange.run();
        winter = value;
        if (value) navAt = System.nanoTime();
    }

    public boolean dark() {
        return dark;
    }

    /** The screen may retain the displayed outgoing panel before its palette changes. */
    public void beforeThemeChange(Runnable listener) {
        beforeThemeChange = listener;
    }

    public void setDark(boolean value) {
        if (dark == value) return;
        styleTransition = false;
        if (beforeThemeChange != null) beforeThemeChange.run();
        dark = value;
    }

    public void refreshCommunity() {
        profiles.resetPendingReads();
        memories.resetPendingReads();
        if (profiles.isOpen() && !profiles.isClosing()) profiles.refresh();
        if (page == 5) memories.refresh();
    }

    public void communityFailure(String action, String message, JsonObject query) {
        profiles.failure(action, message, query);
        memories.failure(action, message, query);
        if (action.equals("snapshot")) moduleStates.put("community", message);
    }

    public void setSessionPlayerId(java.util.UUID playerId) {
        sessionPlayerId = playerId == null ? "" : playerId.toString();
    }

    String selfPlayerId() {
        return data.playerUuid.isBlank() ? sessionPlayerId : data.playerUuid;
    }

    void openPublicProfile(String playerId) {
        if (playerId == null || playerId.isBlank()) {
            feedbackError(live() ? "玩家身份仍在读取，请稍后重试。" : "公开资料需要连接服务器。");
            return;
        }
        restoreFocus = focusId;
        focusId = "profile-close";
        overlay = null;
        reading = null;
        afterProfileClose = null;
        profiles.open(playerId, playerId.equals(selfPlayerId()));
        hits.clear();
    }

    private void leaveMemories() {
        int destination = winter ? memoriesReturnPage : 0;
        if (destination == 3 && updates.loaded() && !updates.enabled()) destination = 0;
        navigate(destination);
    }

    void openMemories(String playerId, String archiveId) {
        if (playerId == null || playerId.isBlank()) {
            feedbackError(live() ? "玩家身份仍在读取，请稍后重试。" : "旅程档案需要连接服务器。");
            return;
        }
        if (page != 5) memoriesReturnPage = page;
        updates.deactivate();
        rankings.deactivate();
        page = 5;
        pageAt = System.nanoTime();
        focusId = null;
        memories.open(
                playerId,
                playerId.equals(selfPlayerId()) ? playerName : "玩家",
                playerId.equals(selfPlayerId()),
                archiveId);
        hits.clear();
    }

    public void releaseCommunityResources() {
        profiles.close();
        memories.closeResources();
        afterProfileClose = null;
    }

    public int rankingVisibleMask() {
        return page == 4 ? rankings.visibleMask() : 0;
    }

    public JsonObject rankingRefreshQuery() {
        return rankings.refreshQuery();
    }

    public Map<String, String> visiblePlayerNames() {
        if (!live()) return Map.of();
        Map<String, String> players = new HashMap<>();
        if (page == 4) players.putAll(rankings.playerNames());
        if (showingStation()) {
            List<StationPlayer> stationPlayers = homeView.stationPlayers();
            int first = stationPage * HubHomeView.STATION_PLAYERS_PER_PAGE;
            int end = Math.min(first + HubHomeView.STATION_PLAYERS_PER_PAGE, stationPlayers.size());
            for (int i = first; i < end; i++) {
                StationPlayer player = stationPlayers.get(i);
                players.put(player.id(), player.name());
            }
        }
        if (profiles.isOpen() && !profiles.playerId().isBlank())
            players.putIfAbsent(profiles.playerId(), profiles.playerName());
        return players;
    }

    public void setConnection(String name, byte[] icon) {
        connectionName = name == null || name.isBlank() ? "当前存档" : name;
        serverIcon = icon == null ? null : icon.clone();
    }

    public void live(PlayerAction actions) {
        sessionPlayerId = "";
        this.actions = actions;
        updates.live();
        rankings.live();
        profiles.clear();
        memories.live();
        stationBio = "";
        homeView.resetLive();
        communityLoaded = false;
        afterProfileClose = null;
        rankingsPreviewAt = 0;
        data.clearLive();
        equipped = null;
        selectedTitle = "";
        month = YearMonth.from(data.today);
        selectedDate = data.today;
        catalogueComplete = false;
        titleDraft = null;
        fortunesView.resetLive();
        pendingModules.clear();
        for (String module :
                List.of("manager", "titles", "fortunes", "updates", "rankings", "community"))
            moduleStates.put(module, "正在读取玩家资料…");
    }

    public void clearSession() {
        live((module, action, payload) -> {});
        reading = null;
        toast.clear();
    }

    public void moduleStatus(String module, String status) {
        moduleStates.put(module, status);
        if (module.equals("updates")) updates.status(status);
        if (module.equals("rankings") && !status.isEmpty()) rankings.status(status);
        if (!status.isEmpty()) {
            if (module.equals("titles")) titleDraft = null;
            if (hasSnapshot(module)) feedbackError(status);
        }
    }

    public void pending(String module, boolean pending) {
        if (module.equals("updates")) updates.pending(pending);
        if (pending) pendingModules.add(module);
        else pendingModules.remove(module);
    }

    public void feedback(String value) {
        notify(value);
    }

    public void acceptCachedUpdate(String id, JsonObject read) {
        updates.acceptCachedUpdate(id, read);
    }

    public void purgeCachedUpdates(
            java.util.Set<String> visibleIds, java.util.Set<String> visiblePackIds) {
        updates.purgeCachedUpdates(visibleIds, visiblePackIds);
    }

    public void updatesFailure(String action, JsonObject payload, String message) {
        updates.failure(action, payload, message);
        if (updates.loaded() && !updates.enabled() && page == 3) {
            page = 0;
            pageAt = System.nanoTime();
            focusId = null;
        }
    }

    public void feedbackError(String value) {
        toast.show(value, HubToast.Kind.ERROR, System.nanoTime());
    }

    public void feedbackSuccess(String value) {
        toast.show(value, HubToast.Kind.SUCCESS, System.nanoTime());
    }

    public String currentMonth() {
        return month.toString();
    }

    public boolean needsCalendar() {
        return page == 2;
    }

    public boolean hasModuleSnapshot(String module) {
        return hasSnapshot(module);
    }

    public String dayIdentity() {
        return (data.fortunesLoaded ? data.timezone : data.managerTimezone) + "|" + data.today;
    }

    public long nextMidnightEpochMillis() {
        return data.today
                .plusDays(1)
                .atStartOfDay(data.fortunesLoaded ? data.zone() : data.profileZone())
                .toInstant()
                .toEpochMilli();
    }

    public void advanceDay(long serverMillis) {
        if (!data.fortunesLoaded)
            data.today =
                    java.time.Instant.ofEpochMilli(serverMillis)
                            .atZone(data.profileZone())
                            .toLocalDate();
    }

    public boolean compact() {
        return compact;
    }

    public void setCompact(boolean value) {
        compact = value;
    }

    public void catalogueComplete() {
        if (titleDraft == null && data.titlesLoaded) catalogueComplete = true;
    }

    public void catalogueInvalidated() {
        titleDraft = null;
    }

    public boolean isCatalogueComplete() {
        return catalogueComplete;
    }

    boolean live() {
        return actions != null;
    }

    boolean hasSnapshot(String module) {
        return switch (module) {
            case "manager" -> data.managerLoaded;
            case "titles" -> data.titlesLoaded;
            case "fortunes" -> data.fortunesLoaded;
            case "updates" -> updates.loaded();
            case "rankings" -> rankings.loaded();
            case "community" -> communityLoaded;
            default -> false;
        };
    }

    boolean ready(String module) {
        return !live() || hasSnapshot(module) || moduleStates.getOrDefault(module, "").isEmpty();
    }

    boolean privacyKnown() {
        return titlesView.privacyKnown();
    }

    void changeVisibility() {
        titlesView.changeVisibility();
    }

    String moduleLabel(String module) {
        return !live()
                ? "演示"
                : moduleStates.getOrDefault(module, "").isEmpty()
                        ? "已连接"
                        : moduleStates.get(module);
    }

    int titleCount() {
        return titlesView.titleCount();
    }

    List<HubData.Title> titleList() {
        return titlesView.titleList();
    }

    String timestamp(long at, String pattern) {
        return at <= 0
                ? "暂无记录"
                : java.time.Instant.ofEpochMilli(at)
                        .atZone(data.profileZone())
                        .format(DateTimeFormatter.ofPattern(pattern));
    }

    String titleDate(HubData.Title title) {
        return live() ? timestamp(title.acquiredAt(), "yyyy/MM/dd") : title.date();
    }

    long journeyDays() {
        return data.firstJoinedAt <= 0
                ? 0
                : Math.max(
                        1,
                        java.time.temporal.ChronoUnit.DAYS.between(
                                        java.time.Instant.ofEpochMilli(data.firstJoinedAt)
                                                .atZone(data.profileZone())
                                                .toLocalDate(),
                                        data.today)
                                + 1);
    }

    void request(String module, String action, JsonObject payload) {
        if (actions != null) actions.request(module, action, payload);
        else if (module.equals("community")) communityFailure(action, "当前预览未连接服务器。", payload);
    }

    JsonObject mutation(String module) {
        JsonObject p = new JsonObject();
        p.addProperty("expectedRevision", data.revisions.getOrDefault(module, ""));
        return p;
    }

    private boolean calendarInitialLoading() {
        return fortunesView.calendarInitialLoading();
    }

    public void acceptLive(String module, String action, JsonObject result) {
        if (module.equals("community")) {
            if (action.equals("snapshot")
                    || action.equals("profile")
                    || action.equals("saveProfile")) {
                JsonObject profile = HubData.communityProfile(result);
                String owner = HubData.string(profile, "playerId", "");
                boolean snapshot = action.equals("snapshot");
                if (profile.has("bio")
                        && (snapshot || !owner.isEmpty() && owner.equals(selfPlayerId())))
                    stationBio = HubData.string(profile, "bio", "");
                if (snapshot) {
                    communityLoaded = true;
                    return;
                }
            }
            profiles.accept(action, result);
            memories.accept(action, result, HubData.object(result, "_hubRequest"));
            return;
        }
        if (module.equals("rankings")) {
            JsonObject body = result.deepCopy();
            JsonObject query =
                    body.has("_hubRequest")
                            ? body.remove("_hubRequest").getAsJsonObject()
                            : new JsonObject();
            rankings.accept(action, body, query);
            return;
        }
        if (module.equals("updates")) {
            updates.accept(action, result);
            if (updates.loaded() && !updates.enabled() && page == 3) {
                page = 0;
                pageAt = System.nanoTime();
                focusId = null;
            }
            return;
        }
        if (module.equals("manager")) {
            data.manager(result);
            homeView.setStationArchiveId(data.scopeId.substring(data.scopeId.indexOf('\n') + 1));
            setPlayerName(HubData.string(HubData.object(result, "player"), "name", playerName));
        }
        if (module.equals("titles")) {
            if (!action.equals("catalogue")) titleDraft = result.deepCopy();
            else {
                if (titleDraft == null
                        || !HubData.string(titleDraft, "revision", "")
                                .equals(HubData.string(result, "revision", ""))) {
                    titleDraft = null;
                    throw new IllegalStateException("称号资料在读取期间发生变化，请重新读取");
                }
                if (result.has("titles"))
                    titleDraft.getAsJsonArray("titles").addAll(result.getAsJsonArray("titles"));
            }
            if (!HubData.string(result, "nextCursor", "").isEmpty()) return;
            data.titles(titleDraft, false);
            equipped = HubData.string(titleDraft, "equippedTitle", "");
            titleDraft = null;
            catalogueComplete = true;
            HubData.Title worn = data.title(equipped);
            visible = worn != null && worn.publicDisplay();
            if (data.title(selectedTitle) == null && !data.titles.isEmpty())
                selectedTitle = data.titles.get(0).id();
        }
        if (module.equals("fortunes")) {
            fortunesView.acceptLive(action, result);
        }
    }

    public void onWindowHandle(LongSupplier provider) {
        windowHandleProvider = provider == null ? () -> 0L : provider;
    }

    public List<HubExternalPage> externalPages() {
        syncExternalPages();
        if (externalSlots.isEmpty()) return List.of();
        List<HubExternalPage> result = new ArrayList<>(externalSlots.size());
        for (ExternalSlot slot : externalSlots) result.add(slot.page());
        return List.copyOf(result);
    }

    public HubExternalPage externalPage(String id) {
        if (id == null || id.isBlank()) return null;
        syncExternalPages();
        for (ExternalSlot slot : externalSlots) {
            HubExternalPage ext = slot.page();
            if (id.equals(ext.id())) return ext;
        }
        return null;
    }

    public <T extends HubExternalPage> T externalPage(Class<T> type) {
        if (type == null) return null;
        syncExternalPages();
        for (ExternalSlot slot : externalSlots) {
            HubExternalPage ext = slot.page();
            if (type.isInstance(ext)) return type.cast(ext);
        }
        return null;
    }

    public void refreshExternalPages() {
        syncExternalPages();
        for (ExternalSlot slot : externalSlots) slot.page().refresh();
    }

    public void prefetchExternalPages() {
        syncExternalPages();
        for (ExternalSlot slot : externalSlots) slot.page().prefetchIfDue();
    }

    public boolean navigatePage(String id) {
        if (id == null || id.isBlank()) return false;
        for (HubBuiltInPage builtIn : builtInPages) {
            if (builtIn.id().equals(id) || ("nav-" + builtIn.index()).equals(id)) {
                navigate(builtIn.index());
                return true;
            }
        }
        syncExternalPages();
        for (ExternalSlot slot : externalSlots) {
            HubExternalPage ext = slot.page();
            NavigationEntry entry = ext.navigationEntry();
            if (id.equals(ext.id()) || (entry != null && id.equals(entry.id()))) {
                cancelExternalPendingOpenExcept(ext);
                if (entry != null && entry.action() != null) {
                    entry.action().run();
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    private void syncExternalPages() {
        long currentVersion = HubPageRegistry.version();
        if (externalRegistryVersion == currentVersion) return;
        List<HubPageRegistry.Registration> registrations = HubPageRegistry.externalPages();
        Map<Long, HubExternalPage> existing = new HashMap<>();
        for (ExternalSlot slot : externalSlots)
            existing.put(slot.registration().sequence(), slot.page());
        List<ExternalSlot> updated = new ArrayList<>(registrations.size());
        for (HubPageRegistry.Registration registration : registrations) {
            HubExternalPage ext = existing.get(registration.sequence());
            if (ext == null) {
                ext =
                        Objects.requireNonNull(
                                registration.factory().apply(pageHost),
                                "HubPageRegistry factory returned null");
            }
            updated.add(new ExternalSlot(registration, ext));
        }
        externalSlots.clear();
        externalSlots.addAll(updated);
        externalRegistryVersion = currentVersion;
    }

    private HubBuiltInPage builtInPage(int index) {
        for (HubBuiltInPage builtIn : builtInPages) {
            if (builtIn.index() == index) return builtIn;
        }
        return null;
    }

    private HubBuiltInPage currentBuiltInPage() {
        HubBuiltInPage current = builtInPage(page);
        return current != null ? current : builtInPages.get(0);
    }

    private void cancelExternalPendingOpen() {
        cancelExternalPendingOpenExcept(null);
    }

    private void cancelExternalPendingOpenExcept(HubExternalPage keep) {
        syncExternalPages();
        for (ExternalSlot slot : externalSlots) {
            if (slot.page() != keep) slot.page().cancelPendingOpen();
        }
    }

    private boolean externalCoversHandbook() {
        for (ExternalSlot slot : externalSlots) {
            if (slot.page().coverHandbook(now, reducedMotion)) return true;
        }
        return false;
    }

    private float externalHandbookAlpha() {
        float alpha = 1f;
        for (ExternalSlot slot : externalSlots) {
            alpha = Math.min(alpha, slot.page().handbookAlpha(now, reducedMotion));
        }
        return clamp(alpha, 0, 1);
    }

    public void setPlayerName(String name) {
        if (name != null && !name.isBlank()) playerName = name;
    }

    public void setViewportBounds(float x, float y, float w, float h) {
        if (w > 0 && h > 0) {
            viewportX = x;
            viewportY = y;
            viewportW = w;
            viewportH = h;
        }
    }

    public void setSettings(String mode, boolean reduced) {
        welcomeMode = List.of("once", "login", "always").contains(mode) ? mode : "login";
        setReducedMotion(reduced);
    }

    public String welcomeMode() {
        return welcomeMode;
    }

    public boolean reducedMotion() {
        return reducedMotion;
    }

    public boolean localReducedMotion() {
        return localReducedMotion;
    }

    public void setSystemReducedMotion(boolean value) {
        systemReducedMotion = value;
        updateReducedMotion();
    }

    public boolean consumeCloseRequest() {
        boolean value = closeRequest;
        closeRequest = false;
        return value;
    }

    public boolean isWelcomePlaying() {
        return playWelcome && !reducedMotion && System.nanoTime() - openedAt < 3_060_000_000L;
    }

    public void open(boolean welcome) {
        toast.clear();
        page = 0;
        for (HubBuiltInPage builtIn : builtInPages) builtIn.onPanelVisibility(false);
        profiles.clear();
        afterProfileClose = null;
        overlay = null;
        reading = null;
        focusId = null;
        modals.resetOnOpen();
        closeRequest = false;
        openedAt = System.nanoTime();
        navAt = openedAt;
        lastFrame = openedAt;
        boolean externalActive = false;
        for (HubExternalPage ext : externalPages()) {
            if (ext.active()) {
                externalActive = true;
                break;
            }
        }
        playWelcome = welcome && !reducedMotion && !externalActive;
        pageAt = openedAt + (playWelcome && !winter ? 3_060_000_000L : 0);
        hits.clear();
        transitions.clear();
        tilts.clear();
        extensionNavigation.clear();
        pressedId = null;
        snapBuiltInNavigation(reducedMotion);
    }

    public void render(HubDraw draw, float mouseX, float mouseY, long nowNanos) {
        refreshExternalPages();
        d = draw;
        d.setDark(dark);
        d.setWinter(winter);
        mx = mouseX;
        my = mouseY;
        now = nowNanos;
        dt = clamp((now - lastFrame) / 1_000_000_000f, 0, .05f);
        lastFrame = now;
        opacity = 1;
        offsetY = 0;
        interactiveAlpha = 1;
        logical = Matrix33.IDENTITY;
        matrixStack.clear();
        alphaStack.clear();
        nextHits.clear();
        float shell =
                reducedMotion
                        ? 1
                        : HubMotion.bezier(
                                progress(now - openedAt, 360_000_000L), .2f, .8f, .2f, 1);
        beginAlpha(shell);
        push(
                HubDraw.cssPerspective(
                        630, 425, 800, 0, 0, 0, .992f + .008f * shell, 0, 12 * (1 - shell)));
        float shellRadius = winter ? 19 : 28;
        d.boxShadow(0, 0, 1260, 850, shellRadius, 0, 30, 80, -32, dark ? 0xa1050b26 : 0x52435882);
        d.boxShadow(0, 0, 1260, 850, shellRadius, 0, 6, 18, -8, dark ? 0x66081127 : 0x1c53658e);
        d.save();
        d.clipRoundRect(0, 0, 1260, 850, shellRadius);
        d.blurBackdrop(0, 0, 1260, 850, shellRadius, 24, 1);
        d.roundRect(0, 0, 1260, 850, shellRadius, dark ? 0x9e253149 : 0x99f4f6ff);
        d.insetBoxShadow(0, 0, 1260, 850, shellRadius, 0, 1, 0, 0, dark ? 0x16dbe6ff : 0xffffffff);
        float handbookAlpha = externalHandbookAlpha();
        boolean handbookLayer = handbookAlpha > 0 && handbookAlpha < 1;
        if (handbookLayer) {
            beginAlpha(handbookAlpha, 0, 0, 1260, 850);
            push(Matrix33.makeTranslate(-22 * (1 - handbookAlpha), 0));
        }
        if (winter) {
            if (handbookAlpha > 0) winterWorkspace();
        } else if (handbookAlpha > 0) {
            d.linearGradientRoundRect(
                    208,
                    0,
                    1052,
                    850,
                    0,
                    135,
                    dark ? new int[] {0xe0202a40, 0xdb262b43} : new int[] {0xadffffff, 0x9cf5f6ff},
                    new float[] {0, 1});
        }
        d.captureGlassBackdrop(0, 0, 208, 850);
        boolean coverHandbook = externalCoversHandbook();
        if (!coverHandbook) {
            sidebar();
            topbar();
        }
        boolean welcomeActive = playWelcome && !reducedMotion && now - openedAt < 3_060_000_000L;
        if (!coverHandbook && (!welcomeActive || winter)) {
            currentBuiltInPage().render(d, mx, my, now, dark, winter, reducedMotion, focusId);
        }
        if (welcomeActive) {
            nextHits.clear();
            welcome();
        }
        if (handbookLayer) {
            pop();
            endAlpha();
        }
        for (ExternalSlot slot : externalSlots) {
            HubExternalPage ext = slot.page();
            if (!ext.routeVisible(now, reducedMotion)) continue;
            if (ext.active()) nextHits.clear();
            ext.render(d, logical, mx, my, now, dark, reducedMotion, focusId);
        }
        d.restore();
        d.strokeRoundRect(
                .5f,
                .5f,
                1259,
                849,
                shellRadius,
                1,
                winter ? d.winterPaper().ruling() : dark ? 0x38c4d7ff : 0xe6ffffff);
        pop();
        endAlpha();
        if (!welcomeActive && profiles.isOpen()) {
            nextHits.clear();
            profiles.draw(
                    d,
                    viewportX,
                    viewportY,
                    viewportW,
                    viewportH,
                    mx,
                    my,
                    now,
                    reducedMotion,
                    dark,
                    focusId,
                    pressedId);
            if (winter && keyboardFocus) {
                for (Hit region : nextHits)
                    if (Objects.equals(focusId, region.id()))
                        winterFocusMark(region.x(), region.y(), region.w(), region.h());
            }
        }
        if (!welcomeActive && !profiles.isOpen() && (overlay != null || reading != null))
            modals.render();
        boolean receiptFocused = keyboardFocus && "receipt-close".equals(focusId);
        toast.render(
                d,
                mx,
                my,
                now,
                reducedMotion,
                viewportX,
                viewportY,
                viewportW,
                viewportH,
                receiptFocused);
        if (winter && toast.canClose())
            hit(
                    "receipt-close",
                    "关闭反馈",
                    toast.closeLeft(),
                    toast.closeTop(),
                    24,
                    24,
                    this::dismissReceipt);
        else if ("receipt-close".equals(focusId)) focusId = null;
        hits.clear();
        hits.addAll(nextHits);
        d = null;
    }

    public void setStationSharedCounts(Map<String, Integer> counts) {
        homeView.setStationSharedCounts(counts);
    }

    public record StationPlayer(String id, String name) {}

    public void setStationPlayers(List<StationPlayer> players) {
        homeView.setStationPlayers(players);
    }

    public boolean showingStation() {
        return winter && page == 0;
    }

    void stock(String name, float x, float y, float w, float h, float alpha) {
        d.image(WINTER_ART + name + (dark ? "-dark.png" : "-light.png"), x, y, w, h, alpha);
    }

    private void winterWorkspace() {
        var p = d.winterPaper();
        d.rect(0, 0, 1260, 850, p.paper());
        stock("workspace", 187, 55, 1072, 794, 1);
    }

    private void winterSidebar() {
        var p = d.winterPaper();
        stock("directory", 1, 1, 186, 848, 1);
        if (page != 0) hit("brand", "旅人手册首页", 1, 1, 185, 77, () -> navigate(0));
        drawBuiltInNavigation(true);
        boolean syncPlugin = HubModpackSync.available();
        int indexRule = dark ? 0xff687765 : 0xffa6ad94;
        float syncHover =
                transition(
                        "winter-sync-hover", syncPlugin && inside(17, 760, 157, 50) ? 1 : 0, 150);
        if (syncHover > 0) d.roundRect(17, 761, 157, 48, 3, withAlpha(indexRule, .15f * syncHover));
        if (live() && serverIcon != null) d.serverIcon(serverIcon, 19, 769, 32);
        else image("server-logo", 19, 769, 32, 32, 1);
        String pack = live() ? (data.packName.isBlank() ? connectionName : data.packName) : "香草纪元";
        String version = live() ? data.packVersion : "2.7.1";
        String name = fit(pack, syncPlugin ? 96 : 108, 12, true);
        text(name, 57, 782, 12, p.ink(), true);
        float vx = 61 + d.textWidth(name, 12, true);
        if (vx + d.textWidth(version, 9, false) < (syncPlugin ? 154 : 168))
            text(version, vx, 782, 9, p.muted(), false);
        d.circle(59, 795, 2, syncPlugin ? modals.syncSidebarDot(true) : p.pine());
        String subLine =
                syncPlugin
                        ? fit(modals.syncSidebarStatus(), 92, 9.5f, false)
                        : (live() ? "当前存档" : "当前存档 · 演示");
        text(subLine, 65, 799, syncPlugin ? 9.5f : 10, p.muted(), false);
        if (syncPlugin) {
            icon(
                    "chevron-right",
                    158 + (reducedMotion ? 0 : 2 * syncHover),
                    778,
                    12,
                    withAlpha(p.muted(), .45f + .40f * syncHover));
            hit("modpack-sync", "打开整合包同步设置", 17, 760, 157, 50, () -> openOverlay("modpack-sync"));
        }
        float settingsHover =
                transition("winter-settings-hover", inside(17, 812, 157, 32) ? 1 : 0, 140);
        if (settingsHover > 0)
            d.roundRect(17, 812, 157, 32, 8, withAlpha(p.sheet(), .52f * settingsHover));
        icon("settings", 23, 820.5f, 15, p.pine());
        text("界面设置", 47, 833, 12, p.muted(), false);
        hit("settings", "界面设置", 17, 812, 157, 32, () -> openOverlay("settings"));
    }

    private void winterNav(NavigationEntry entry, int slot, boolean current) {
        long start =
                Math.max(navAt, playWelcome ? openedAt + 3_060_000_000L : openedAt)
                        + slot * 82_000_000L;
        float enter =
                reducedMotion || keyboardFocus && entry.id().equals(focusId)
                        ? 1
                        : HubMotion.bezier(
                                progress(now - start, 360_000_000L), .22f, .75f, .23f, 1);
        if (enter <= 0) return;
        var p = d.winterPaper();
        int indexRule = dark ? 0xff687765 : 0xffa6ad94;
        float y = 117.3f + 51 * slot + (slot >= 3 ? 37.3f : 0);
        beginAlpha(enter, 0, y, 186, 47);
        push(Matrix33.makeTranslate(-12 * (1 - enter), 0));
        boolean active = !current || page == 5;
        float hover = extensionHover(entry.id(), inside(13, y, 161, 47));
        float selection =
                stateTransition(entry.id() + "-station-selected", current ? 1 : 0, 200, true);
        if (hover > 0 && !current)
            d.roundRect(13, y + 2, 161, 43, 9, withAlpha(indexRule, .20f * hover));
        float ribbonAlpha = .42f + .58f * selection;
        if (current && selection > 0) {
            d.save();
            d.translate(4 - 5 * (1 - selection), y + 2);
            try (var shape =
                    io.github.humbleui.skija.Path.makeFromSVGString(
                            "M0 0H173L166 21.5L173 43H0Z")) {
                d.canvas().clipPath(shape, io.github.humbleui.skija.ClipMode.INTERSECT, true);
                d.linearGradientRoundRect(
                        0,
                        0,
                        173,
                        43,
                        0,
                        90,
                        new int[] {
                            withAlpha(dark ? 0xff623b34 : 0xff704035, ribbonAlpha),
                            withAlpha(dark ? 0xff804c41 : 0xff884f40, ribbonAlpha)
                        },
                        new float[] {0, .09f});
                for (int dash = 4; dash < 173; dash += 7) {
                    int thread = withAlpha(0x70efd8ad, ribbonAlpha);
                    d.rect(dash, 3, 3, 1, thread);
                    d.rect(dash, 39, 3, 1, thread);
                }
            } finally {
                d.restore();
            }
        }
        int baseInk = dark ? p.ink() : 0xff304638;
        int color = current ? mix(baseInk, 0xfffff2d7, ribbonAlpha) : baseInk;
        int cell =
                switch (entry.id()) {
                    case "nav-0" -> 0;
                    case "nav-1" -> 1;
                    case "nav-2" -> 2;
                    case "nav-3" -> 3;
                    case "nav-4" -> 4;
                    default -> entry.label().equals("问舟") ? 5 : -1;
                };
        if (cell >= 0) {
            d.save();
            d.canvas().concat(Matrix33.makeRotate(reducedMotion ? 0 : -3 * hover, 40, y + 23.5f));
            d.clipRoundRect(21, y + 4.5f, 38, 38, 0);
            d.image(
                    WINTER_ART + "directory-emblems.png",
                    21 - cell % 3 * 38,
                    y + 4.5f - cell / 3 * 38,
                    114,
                    76,
                    1);
            d.restore();
        } else entry.glyph().draw(d, 25, y + 10, 26);
        serif(entry.label(), 66, y + 29, 15, color, true);
        boolean unread = isNavigationUnread(entry);
        String badge = unread ? "NEW" : entry.badge();
        if (!badge.isBlank()) {
            float bx = 71 + d.textWidthSerif(entry.label(), 15, true),
                    w = d.textWidth(badge, 8, true) + 8;
            d.roundRect(bx, y + 17, w, 13, 4, unread ? 0xff26844e : 0xffffd547);
            text(badge, bx + 4, y + 27, 8, unread ? 0xffffffff : 0xff382e19, true);
        }
        if (active)
            hit(
                    entry.id(),
                    entry.accessibleLabel() + (unread ? "，有新发布" : ""),
                    13,
                    y,
                    161,
                    47,
                    entry.action());
        pop();
        endAlpha();
    }

    private void winterTopbar() {
        var p = d.winterPaper();
        d.linearGradientRoundRect(
                187,
                1,
                1072,
                54,
                0,
                180,
                new int[] {mix(p.sheet(), dark ? 0xff69858b : 0xffc1d7d4, .22f), p.paper()},
                new float[] {0, .88f});
        String section = currentBuiltInPage().section();
        text(section, 215, 33, 12, p.muted(), false);
        float arrowX = 226 + d.textWidth(section, 12, false);
        icon("chevron-right", arrowX, 21.5f, 13, p.muted());
        d.text(pageName(), arrowX + 24, 33, 12, a(p.muted()), 500);
        d.circle(1107.5f, 28, 2.5f, dark ? 0xff8bceb4 : 0xff529c8c);
        text(live() ? "玩家手册" : "本地预览", 1116, 32, 11, p.muted(), false);
        d.rect(1179, 21.5f, 1, 13, dark ? p.ruling() : 0x7ac7cfe0);
        iconButton("close", "收起手册", "x", 1199, 12, 32, () -> closeRequest = true, true);
        d.line(187, 55, 687, 55, 1, withAlpha(p.wool(), .30f));
        d.line(759, 55, 1259, 55, 1, withAlpha(p.wool(), .30f));
        stock("knit-rose", 687, 46.5f, 72, 17, .56f);
    }

    private void winterHeading(String title, String subtitle, String module, int count) {
        var p = d.winterPaper();
        group(0, 340);
        String stem = page == 5 ? "home" : currentBuiltInPage().id();
        d.image(WINTER_ART + "station-" + stem + ".png", LEFT, 83, 29, 29, 1);
        tracked(title, LEFT + 38, 107, 25, 1, p.ink(), 700, true);
        if (count >= 0) {
            String n = Integer.toString(count);
            float x = LEFT + 65 + trackedWidth(title, 25, 1, 720, false);
            d.roundRect(x, 84, 29, 26, 8, p.sheet());
            d.strokeRoundRect(x, 84, 29, 26, 8, 1, p.ruling());
            textCenter(n, x + 14.5f, 102, 12, p.pine(), true);
        }
        text(subtitle, LEFT, 134, 13, p.muted(), false);
        boolean showTrail = page == 1 || page == 4;
        stock(showTrail ? "trail" : "mail", 879, 92, 72, 36, .30f);
        if (showTrail) stock("frost-print", 966, 82, 57, 52, .30f);
        if (module != null) textRight(fit(module, 185, 10, false), 1232, 105, 10, p.muted(), false);
        ungroup();
    }

    // CSS applies ease-out separately between consecutive opacity keyframes.
    static float arrivalSnowOpacity(float phase) {
        if (phase <= 0 || phase >= 1) return 0;
        if (phase < .25f) return .75f * HubMotion.easeOut(phase / .25f);
        if (phase < .8f) return .75f - .20f * HubMotion.easeOut((phase - .25f) / .55f);
        return .55f * (1 - HubMotion.easeOut((phase - .8f) / .20f));
    }

    private void winterArrival() {
        var p = d.winterPaper();
        float seconds = (now - openedAt) / 1_000_000_000f;
        float fade = HubMotion.bezier(clamp((seconds - 2.6f) / .42f, 0, 1), .42f, 0, .58f, 1);
        beginAlpha(1 - fade);
        d.rect(0, 0, 1260, 850, mix(p.sheet(), p.cover(), .22f));
        d.radialGradientRoundRect(
                0,
                0,
                1260,
                850,
                19,
                630,
                348,
                760,
                550,
                new int[] {p.sheet(), p.sheet(), mix(p.sheet(), p.cover(), .22f)},
                new float[] {0, .24f, .84f});
        HubWinterSheet.grain(d, 0, 0, 1260, 850, 19, .66f);
        float print = HubMotion.bezier(clamp(seconds / .76f, 0, 1), .18f, .72f, .19f, 1);
        float light = HubMotion.easeOut(clamp((seconds - .57f) / .73f, 0, 1));
        float y = 263 + 5 * (1 - print);
        d.save();
        float inset = 197 * .47f * (1 - print);
        d.clipRoundRect(345, y + inset, 570, 197 - 2 * inset, 12 * (1 - print));
        beginAlpha(print, 345, y, 570, 197);
        stock("arch-unlit", 345, y, 570, 197, 1);
        if (light > 0) stock("arch-lit", 345, y, 570, 197, light);
        d.save();
        d.canvas()
                .clipRRect(
                        io.github.humbleui.types.RRect.makeComplexXYWH(
                                350,
                                y + 5,
                                560,
                                187,
                                new float[] {280, 50.5f, 280, 50.5f, 6, 6, 6, 6}),
                        io.github.humbleui.skija.ClipMode.INTERSECT,
                        true);
        for (int i = 0; i < SNOW_X.length; i++) {
            float phase = clamp((seconds - (i % 5) * .095f) / 1.72f, 0, 1);
            float opacity = arrivalSnowOpacity(phase);
            if (opacity <= 0) continue;
            float travel = HubMotion.easeOut(phase);
            float px = 350 + 560 * SNOW_X[i] / 100f + 8 - 17 * travel;
            float py = y + 5 + 187 * SNOW_Y[i] / 100f - 11 + 24 * travel;
            d.circle(px, py, (2 + i % 3) * .5f, withAlpha(0xfffff8e9, opacity));
        }
        for (int i = 0; i < 4; i++) {
            float shown = HubMotion.easeOut(clamp((seconds - .36f - i * .13f) / .17f, 0, 1));
            float px = 350 + 560 * FOOT_X[i] / 100, py = y + 5 + 187 * (1 - FOOT_Y[i] / 100) - 3;
            d.save();
            d.translate(px + 3, py + 1.5f);
            d.canvas().rotate(-15);
            d.oval(-3, -1.5f, 6.2f, 3.3f, withAlpha(0xff62757b, .55f * shown));
            d.restore();
        }
        d.restore();
        endAlpha();
        d.restore();
        float title = HubMotion.bezier(clamp((seconds - .92f) / .68f, 0, 1), .2f, .7f, .22f, 1);
        trackedCenter(
                "旅人手册",
                630,
                529 + 7 * (1 - title),
                46,
                7.36f,
                withAlpha(p.ink(), title),
                true,
                true);
        float stitch = HubMotion.easeOut(clamp((seconds - 1.17f) / .59f, 0, 1));
        d.save();
        d.clipRoundRect(630 - 64 * stitch, 557, 128 * stitch, 13, 0);
        HubWinterSheet.illustration(
                d, dark ? "knit-rose-dark" : "knit-rose-light", 566, 557, 128, 13, .8f * stitch);
        d.restore();
        endAlpha();
    }

    private void stationButton(
            String id,
            String label,
            float x,
            float y,
            float w,
            float h,
            boolean primary,
            boolean enabled,
            Runnable click,
            String glyph) {
        var p = d.winterPaper();
        float hover = extensionHover(id, enabled && inside(x, y, w, h));
        float move = extensionButtonMove(id, enabled, hover > .5f);
        int fg = primary ? p.sheet() : p.pine();
        float top = y + (primary ? move : 0);
        d.roundRect(
                x,
                top,
                w,
                h,
                8,
                withAlpha(
                        primary
                                ? mix(p.pine(), p.ink(), .11f * hover)
                                : mix(p.sheet(), p.cover(), .6f * hover),
                        enabled ? 1 : .50f));
        d.strokeRoundRect(x + .5f, top + .5f, w - 1, h - 1, 8, 1, primary ? p.pine() : p.ruling());
        int size = h <= 34 ? 12 : 13;
        String value = fit(label, w - (glyph == null ? 12 : 26), size, true);
        float tw = d.textWidth(value, size, true),
                left = x + (w - tw - (glyph == null ? 0 : 19)) / 2;
        boolean trailing = "arrow-right".equals(glyph) || "mail-open".equals(glyph);
        if (glyph != null)
            icon(
                    glyph,
                    trailing ? left + tw + 5 : left,
                    top + (h - 14) / 2,
                    14,
                    withAlpha(fg, enabled ? 1 : .55f));
        text(
                value,
                left + (glyph == null || trailing ? 0 : 19),
                top + (h + size * .72f) / 2,
                size,
                withAlpha(fg, enabled ? 1 : .55f),
                true);
        if (enabled) hit(id, label, x, y, w, h, click);
    }

    private void sidebar() {
        if (winter) {
            boolean previous = d.literalColors(true);
            try {
                winterSidebar();
            } finally {
                d.literalColors(previous);
            }
            return;
        }
        d.glassSurface(0, 0, 208, 850, 0, GlassStyle.SIDEBAR);
        d.linearGradientRoundRect(
                0,
                0,
                208,
                850,
                0,
                145,
                dark
                        ? new int[] {0x572b3c52, 0x3d273248, 0x463a3057}
                        : new int[] {0x40ffffff, 0x28e6edff, 0x35e9e3ff},
                new float[] {0, .52f, 1});
        d.linearGradientRoundRect(
                0,
                0,
                208,
                850,
                0,
                145,
                dark
                        ? new int[] {0x2e354a61, 0x20263e5b, 0x2943345c}
                        : new int[] {0x3df8fcff, 0x2beaf1ff, 0x38eee9ff},
                new float[] {0, .5f, 1});
        d.linearGradientRoundRect(
                0,
                0,
                208,
                850,
                0,
                118,
                dark
                        ? new int[] {0x08c7deff, 0x00ffffff, 0x00ffffff, 0x08aba3e3}
                        : new int[] {0x24ffffff, 0x00ffffff, 0x00ffffff, 0x14bcc0e3},
                new float[] {0, .32f, .72f, 1});
        d.insetBoxShadow(0, 0, 208, 850, 0, -1, 0, 3, 0, dark ? 0x129eaee5 : 0x2babb9e1);
        d.boxShadow(0, 0, 208, 850, 0, 4, 0, 16, 0, dark ? 0x1a0d1732 : 0x0865719e);
        d.rect(207, 0, 1, 850, dark ? 0x26a5b7e0 : 0x88b3bfdf);
        if (live() && serverIcon != null) d.serverIcon(serverIcon, 22, 27, 44);
        else image("server-logo", 22, 27, 44, 44, 1);
        tracked("旅人手册", 77, 46, 18, 1.2f, INK, 650, false);
        text("PixelMC-Hub", 77, 65, 10, 0xff687491, false);
        if (page != 0) hit("brand", "旅人手册首页", 18, 20, 174, 61, () -> navigate(0));
        text("我的空间", 25, 118, 11, 0xff677591, false);
        drawBuiltInNavigation(false);
        boolean syncPlugin = HubModpackSync.available();
        if (syncPlugin) {
            float syncHover =
                    transition("modpack-sync-hover", inside(16, 710, 176, 56) ? 1 : 0, 140);
            if (syncHover > 0)
                d.roundRect(
                        16, 710, 176, 56, 10, withAlpha(dark ? 0x24acbfff : 0x80ffffff, syncHover));
        }
        glass(24, 718, 36, 40, 12, 0xc9e7fff2, 0x7ab4e9d7);
        icon("flower-2", 32, 728, 21, 0xff458c80);
        String classicPack =
                live() ? (data.packName.isBlank() ? connectionName : data.packName) : "香草纪元";
        String classicVer = live() ? data.packVersion : "2.7.1";
        String classicName = fit(classicPack, syncPlugin ? 96 : 112, 12, true);
        text(classicName, 70, 731, 12, 0xff52627b, true);
        if (!classicVer.isBlank()) {
            float cvx = 74 + d.textWidth(classicName, 12, true);
            if (cvx + d.textWidth(classicVer, 9.5f, false) < (syncPlugin ? 174 : 186))
                text(classicVer, cvx, 731, 9.5f, 0xff708098, false);
        }
        d.circle(72, 748, 2, syncPlugin ? modals.syncSidebarDot(false) : 0xff6a9c8b);
        String classicSub =
                syncPlugin
                        ? fit(modals.syncSidebarStatus(), 96, 10, false)
                        : (live()
                                ? (data.packVersion.isBlank()
                                        ? "当前存档"
                                        : fit(data.packVersion, 105, 10, false))
                                : "当前存档 · 演示");
        text(classicSub, 80, 752, 10, 0xff708098, false);
        if (syncPlugin) {
            icon("chevron-right", 174, 732, 12, 0xff708098);
            hit("modpack-sync", "打开整合包同步设置", 16, 710, 176, 56, () -> openOverlay("modpack-sync"));
        }
        d.line(24, 777, 184, 777, 1, 0x55b7c2dc);
        float settingsHover = transition("settings-hover", inside(20, 791, 172, 40) ? 1 : 0, 140);
        if (settingsHover > 0)
            d.roundRect(20, 791, 172, 40, 10, withAlpha(0x99ffffff, settingsHover));
        icon("settings", 34, 801, 19, 0xff65738c);
        text("界面设置", 65, 816, 13, 0xff65738c, false);
        hit("settings", "界面设置", 20, 791, 172, 40, () -> openOverlay("settings"));
    }

    @FunctionalInterface
    public interface NavigationGlyph {
        void draw(HubDraw draw, float x, float y, float size);
    }

    public record NavigationEntry(
            String id,
            String label,
            String accessibleLabel,
            String badge,
            int topColor,
            int bottomColor,
            NavigationGlyph glyph,
            Runnable action) {}

    NavigationEntry builtInEntry(
            int destination, String label, String iconName, int topColor, int bottomColor) {
        return new NavigationEntry(
                "nav-" + destination,
                label,
                label,
                "",
                topColor,
                bottomColor,
                (draw, x, y, size) -> icon(iconName, x, y, size, 0xffffffff),
                () -> navigate(destination));
    }

    private void drawBuiltInNavigation(boolean winterMode) {
        int slot = 0;
        for (HubBuiltInPage builtIn : builtInPages) {
            NavigationEntry entry = builtIn.navigationEntry();
            if (entry == null || !builtIn.visible()) continue;
            boolean current = builtIn.index() == page || (page == 5 && builtIn.index() == 0);
            long enteredAt = winterMode ? navAt : navAt + slot * 70_000_000L;
            drawNavigationRow(entry, slot, current, builtIn.navigationState(), enteredAt);
            slot++;
        }
        drawExtraNavigation(slot);
    }

    private boolean isNavigationUnread(NavigationEntry entry) {
        for (HubBuiltInPage builtIn : builtInPages) {
            if (builtIn.navigationEntry() == entry) return builtIn.unread();
        }
        return false;
    }

    /** Hook for client extensions after the last visible built-in navigation row. */
    private void drawExtraNavigation(int firstSlot) {
        syncExternalPages();
        int slot = firstSlot;
        for (ExternalSlot extSlot : externalSlots) {
            HubExternalPage ext = extSlot.page();
            NavigationEntry entry = ext.navigationEntry();
            if (entry == null) continue;
            boolean visible = ext.navigationVisible();
            drawExtensionNavigation(entry, slot, visible);
            if (visible) slot++;
        }
    }

    public void drawExtensionNavigation(NavigationEntry entry, int slot, boolean visible) {
        if (!visible) {
            if (extensionNavigation.remove(entry.id()) != null)
                transitions.remove(entry.id() + "-nav-hover");
            return;
        }
        HubMotion.Navigation state =
                extensionNavigation.computeIfAbsent(entry.id(), id -> new HubMotion.Navigation());
        drawNavigationRow(entry, slot, false, state, navAt + slot * 70_000_000L);
    }

    private void drawNavigationRow(
            NavigationEntry entry,
            int slot,
            boolean current,
            HubMotion.Navigation state,
            long enteredAt) {
        if (winter) {
            boolean previous = d.literalColors(true);
            try {
                winterNav(entry, slot, current);
            } finally {
                d.literalColors(previous);
            }
            return;
        }
        String id = entry.id();
        float y = 145 + slot * 74;
        // Memories highlights Home, but Home must still take the player back to its main page.
        boolean canNavigate = !current || page == 5;
        float[] pointer = HubDraw.inversePoint(logical, mx, my);
        boolean hover =
                canNavigate
                        && pointer[0] >= 21
                        && pointer[0] < 193
                        && pointer[1] >= y - 8
                        && pointer[1] < y + 56;
        float h = transition(id + "-nav-hover", hover ? 1 : 0, 200);
        float nx = clamp((pointer[0] - 58) / 23, -1, 1);
        float ny = clamp((pointer[1] - y - 23) / 23, -1, 1);
        state.update(
                dt,
                reducedMotion,
                hover,
                canNavigate && Objects.equals(pressedId, id),
                nx,
                ny,
                now >= enteredAt);
        if (current) glass(20, y - 8, 173, 64, 18, 0xb8ffffff, 0x57ffffff);
        else if (h > 0)
            d.roundRect(20, y - 8, 173, 64, 18, withAlpha(dark ? 0x0cacbfff : 0x65ffffff, h));

        IconPose pose =
                HubDraw.iconPose(
                        46,
                        state.enter.value,
                        state.hover.value,
                        state.press.value,
                        state.x.value,
                        state.y.value,
                        reducedMotion);
        d.glassIcon(35, y, 46, entry.topColor(), entry.bottomColor(), pose, 1);
        if (pose.faceAlpha() > 0) {
            beginAlpha(pose.faceAlpha(), 35, y - 8, 46, 62);
            push(
                    HubDraw.cssPerspective(
                            58, y + 23, 800, 0, 0, 0, pose.faceScale(), 0, pose.faceY()));
            entry.glyph().draw(d, 48.8f, y + 13.8f, 18.4f);
            pop();
            endAlpha();
        }
        boolean unread = isNavigationUnread(entry);
        tracked(
                entry.label(),
                99,
                y + 28,
                14,
                .25f,
                current ? 0xff3a3590 : 0xff53627e,
                current ? 700 : 550,
                false);
        String badge = unread ? "NEW" : entry.badge();
        if (!badge.isEmpty()) {
            float badgeX = Math.max(132, 104 + trackedWidth(entry.label(), 14, .25f, 550, false));
            float badgeWidth = unread ? 30 : Math.max(28, d.textWidth(badge, 9, 600) + 6);
            d.roundRect(badgeX, y + 26, badgeWidth, 13, 4, unread ? 0xff238554 : 0xffffd547);
            d.text(
                    badge,
                    badgeX + (unread ? 4 : 3),
                    y + 35,
                    9,
                    unread ? 0xffffffff : 0xff101010,
                    true);
        }
        if (canNavigate) {
            String label = unread ? entry.accessibleLabel() + "，有新发布" : entry.accessibleLabel();
            hit(id, label, 20, y - 8, 173, 64, entry.action());
        }
    }

    /** Samples the same hover timing as the native icon and text buttons. */
    public float extensionHover(String id, boolean hovered) {
        return extensionTransition(id + "-hover", hovered ? 1 : 0);
    }

    public float extensionButtonMove(String id, boolean enabled, boolean hovered) {
        float target = !enabled ? 0 : Objects.equals(pressedId, id) ? 1 : hovered ? -1 : 0;
        return extensionTransition(id + "-move", reducedMotion ? 0 : target);
    }

    private float extensionTransition(String key, float target) {
        if (target == 0 && !transitions.containsKey(key)) return 0;
        float value = transition(key, target, 140);
        if (target == 0 && value == 0) transitions.remove(key);
        return value;
    }

    private void topbar() {
        if (winter) {
            boolean previous = d.literalColors(true);
            try {
                winterTopbar();
            } finally {
                d.literalColors(previous);
            }
            return;
        }
        d.rect(208, 0, 1052, 54, dark ? 0x47263149 : 0x36ffffff);
        d.line(208, 54, 1260, 54, 1, dark ? 0x11b6c9f0 : 0xb8ffffff);
        text("我的空间", 236, 33, 12, 0xff8a94aa, false);
        icon("chevron-right", 301, 22, 13, 0xffa1aac0);
        text(pageName(), 326, 33, 12, 0xff63718c, false);
        d.circle(1095, 28, 2.5f, 0xff529c8c);
        text(live() ? "玩家手册" : "本地原型", 1105, 32, 11, 0xff64748b, false);
        d.line(1183, 21, 1183, 35, 1, 0x7ac7cfe0);
        iconButton("close", "收起手册", "x", 1200, 12, 32, () -> closeRequest = true, true);
    }

    void heading(String title, String subtitle, String module, int count) {
        if (winter) {
            boolean previous = d.literalColors(true);
            try {
                winterHeading(title, subtitle, module, count);
            } finally {
                d.literalColors(previous);
            }
            return;
        }
        group(0, 340);
        tracked(title, LEFT, 108, 25, 1, INK, 720, false);
        if (count >= 0) {
            String number = Integer.toString(count);
            float x = LEFT + trackedWidth(title, 25, 1, 720, false) + 12,
                    w = d.textWidth(number, 13, 500) + 18;
            d.roundRect(x, 84, w, 28, 10, dark ? 0x299678d7 : 0xffe9e1ff);
            d.strokeRoundRect(x, 84, w, 28, 10, 1, dark ? 0x2bc9b3f6 : 0xffffffff);
            d.text(number, x + 9, 103, 13, dark ? 0xffcbb7ff : 0xff7361c3, 500);
        }
        text(subtitle, LEFT, 137, 13, 0xff6c7891, false);
        if (module != null)
            textRight(fit(module, 460, 10, false), 1232, 105, 10, 0xff7c72a6, false);
        ungroup();
    }

    void collectionHeading(String title, String count) {
        var paper = d.winterPaper();
        tracked(title, HubTitlesView.COLLECTION_LEFT + 1, 104, 25, 1.5f, paper.ink(), 700, true);
        String prefix = "已收藏  ";
        int end = count.indexOf(' ', prefix.length());
        if (!count.startsWith(prefix) || end < prefix.length()) {
            textRight(
                    count,
                    HubTitlesView.COLLECTION_LEFT + HubTitlesView.COLLECTION_WIDTH - 1,
                    100,
                    12,
                    paper.muted(),
                    false);
            return;
        }
        String number = count.substring(prefix.length(), end), suffix = count.substring(end);
        float right = HubTitlesView.COLLECTION_LEFT + HubTitlesView.COLLECTION_WIDTH - 1;
        textRight(suffix, right, 102, 12, paper.muted(), false);
        right -= d.textWidth(suffix, 12, false);
        textRight(number, right, 102, 16, paper.ink(), true);
        textRight("已收藏 ", right - d.textWidth(number, 16, true), 102, 12, paper.muted(), false);
    }

    void plainControl(
            String id,
            String label,
            String glyph,
            String caption,
            float x,
            float y,
            float w,
            float h,
            int ink,
            int fill,
            boolean enabled,
            Runnable action) {
        boolean hover = enabled && inside(x, y, w, h);
        if (fill != 0) d.roundRect(x, y, w, h, 7, fill);
        if (hover) d.roundRect(x, y, w, h, 7, withAlpha(ink, .10f));
        int tone = enabled ? ink : withAlpha(ink, .40f);
        float size = 14, textW = d.textWidth(caption, 12, 600);
        float cx = x + (w - textW - (glyph == null ? 0 : size + (caption.isEmpty() ? 0 : 5))) / 2;
        if (glyph != null) {
            icon(glyph, cx, y + (h - size) / 2, size, tone);
            cx += size + 5;
        }
        if (!caption.isEmpty()) d.text(caption, cx, y + h / 2 + 4.5f, 12, tone, 600);
        if (enabled) hit(id, label, x, y, w, h, action);
    }

    void unavailable(String module) {
        float left = winter ? HubTitlesView.COLLECTION_LEFT : LEFT;
        float width = winter ? HubTitlesView.COLLECTION_WIDTH : WIDTH;
        float top = winter ? HubTitlesView.COLLECTION_TOP : 174;
        float height =
                winter ? ("titles".equals(module) ? HubTitlesView.ALBUM_END : 777.75f) - top : 626;
        float center = left + width / 2, middle = top + height / 2;
        Runnable retry = () -> request(module, "snapshot", new JsonObject());
        var paper = d.winterPaper();
        if (winter) {
            HubWinterSheet.panel(d, left, top, width, height, 20, paper.sheet(), 1);
        } else {
            group(30, 350);
            glass(left, top, width, height, 22, 0xecffffff, 0xabffffff);
        }
        int ink = winter ? paper.ink() : MUTED;
        int muted = winter ? paper.muted() : MUTED;
        icon(
                "info",
                center - 17,
                winter ? middle - 84 : 351,
                34,
                winter ? paper.pine() : 0xff9d90b4);
        textCenter(
                fit(moduleLabel(module), width - 64, 16, true),
                center,
                winter ? middle - 18 : 422,
                16,
                ink,
                true);
        textCenter("其他页面仍可使用", center, winter ? middle + 9 : 455, 12, muted, false);
        if (winter) {
            plainControl(
                    "retry-" + module,
                    "重新读取",
                    null,
                    "重新读取",
                    center - 55,
                    middle + 32,
                    110,
                    37,
                    paper.sheet(),
                    paper.pine(),
                    true,
                    retry);
        } else {
            button("retry-" + module, "重新读取", center - 55, 485, 110, 37, false, true, retry);
            ungroup();
        }
    }

    private String pageName() {
        return currentBuiltInPage().label();
    }

    private void leaderboards() {
        if (rankingsPreviewAt > 0 && now >= rankingsPreviewAt) {
            rankingsPreviewAt = 0;
            if (!live()) rankings.demoUpdate();
        }
        if (!winter) heading("玩家排行", rankings.description(), null, -1);
        group(0, 340);
        float[] mouse = HubDraw.inversePoint(logical, mx, my);
        rankings.render(d, mouse[0], mouse[1], focusId, pressedId, reducedMotion, now);
        ungroup();
    }

    private void updates() {
        if (!winter)
            heading(
                    "更新日志",
                    "每一点变化，都在这里与你分享。",
                    live() ? "PixelMC-Hub" : "本地演示",
                    updates.loaded() ? updates.total() : -1);
        group(0, 340);
        float[] mouse = HubDraw.inversePoint(logical, mx, my);
        float readerAlpha =
                profiles.isOpen() || overlay != null || reading != null ? 0 : interactiveAlpha;
        updates.render(
                d,
                mouse[0],
                mouse[1],
                focusId,
                pressedId,
                reducedMotion,
                playerName,
                now,
                readerAlpha);
        ungroup();
    }

    private void memories() {
        group(0, 340);
        float[] mouse = HubDraw.inversePoint(logical, mx, my);
        memories.render(d, mouse[0], mouse[1], focusId, pressedId, reducedMotion, dark, now);
        ungroup();
    }

    private void welcome() {
        if (winter) {
            boolean previous = d.literalColors(true);
            try {
                winterArrival();
            } finally {
                d.literalColors(previous);
            }
            return;
        }
        float seconds = (now - openedAt) / 1_000_000_000f;
        float fade = HubMotion.bezier(clamp((seconds - 2.6f) / .42f, 0, 1), .42f, 0, .58f, 1);
        beginAlpha(1 - fade);
        boolean previousColors = d.literalColors(true);
        try {
            d.roundRect(0, 0, 1260, 850, 28, dark ? 0xff1e283d : 0xfff7f8ff);
            if (dark) {
                d.radialGradientRoundRect(
                        0,
                        0,
                        1260,
                        850,
                        28,
                        1008,
                        637.5f,
                        819,
                        552.5f,
                        new int[] {0xa64b3766, 0x004b3766},
                        new float[] {0, .65f});
                d.radialGradientRoundRect(
                        0,
                        0,
                        1260,
                        850,
                        28,
                        352.8f,
                        238,
                        756,
                        510,
                        new int[] {0xbb304e6c, 0x00304e6c},
                        new float[] {0, .6f});
            } else {
                d.radialGradientRoundRect(
                        0,
                        0,
                        1260,
                        850,
                        28,
                        352.8f,
                        238,
                        756,
                        510,
                        new int[] {0xc9d2ebff, 0x00d2ebff},
                        new float[] {0, .6f});
                d.radialGradientRoundRect(
                        0,
                        0,
                        1260,
                        850,
                        28,
                        1008,
                        637.5f,
                        819,
                        552.5f,
                        new int[] {0xb8e1d4f9, 0x00e1d4f9},
                        new float[] {0, .65f});
            }
            d.save();
            d.translate(420, 296);
            d.canvas().scale(420f / 330, 420f / 330);
            d.translate(165, 80);
            d.canvas().rotate(-5);
            d.translate(-165, -80);
            float write =
                    HubMotion.bezier(clamp((seconds - .12f) / 1.9f, 0, 1), .32f, .08f, .35f, 1);
            strokeSvg(HELLO_PATH, write, 6.8f, 1);
            strokeSvg(
                    FLOURISH_PATH,
                    HubMotion.easeOut(clamp((seconds - 1.81f) / .35f, 0, 1)),
                    2.1f,
                    .58f);
            d.restore();
            float signature = HubMotion.ease(clamp((seconds - 1.55f) / .5f, 0, 1));
            beginAlpha(signature, 420, 525, 420, 30);
            trackedCenter(
                    "PIXELMC",
                    632.5f,
                    535 + 5 * (1 - signature),
                    10,
                    5,
                    dark ? 0xffaca6c8 : 0xff9292ad,
                    false,
                    false);
            endAlpha();
            endAlpha();
        } finally {
            d.literalColors(previousColors);
        }
    }

    private void strokeSvg(String svg, float fraction, float width, float alpha) {
        if (fraction <= 0) return;
        try (io.github.humbleui.skija.Path full =
                        io.github.humbleui.skija.Path.makeFromSVGString(svg);
                io.github.humbleui.skija.PathMeasure measure =
                        new io.github.humbleui.skija.PathMeasure(full, false, 4);
                io.github.humbleui.skija.PathBuilder builder =
                        new io.github.humbleui.skija.PathBuilder();
                io.github.humbleui.skija.Paint paint = new io.github.humbleui.skija.Paint()) {
            measure.getSegment(0, measure.getLength() * fraction, builder, true);
            try (io.github.humbleui.skija.Path segment = builder.build();
                    io.github.humbleui.skija.Shader shader =
                            io.github.humbleui.skija.Shader.makeLinearGradient(
                                    full.getBounds().getLeft(),
                                    0,
                                    full.getBounds().getRight(),
                                    0,
                                    dark
                                            ? new int[] {0xff94b5f2, 0xffbaa0ed, 0xffd7adda}
                                            : new int[] {0xff5d84d6, 0xff8970d8, 0xffb184bf},
                                    new float[] {0, .55f, 1})) {
                paint.setAntiAlias(true)
                        .setMode(io.github.humbleui.skija.PaintMode.STROKE)
                        .setStrokeWidth(width)
                        .setStrokeCap(io.github.humbleui.skija.PaintStrokeCap.ROUND)
                        .setStrokeJoin(io.github.humbleui.skija.PaintStrokeJoin.ROUND)
                        .setColor(0xffffffff)
                        .setAlphaf(alpha)
                        .setShader(shader);
                d.canvas().drawPath(segment, paint);
            }
        }
    }

    void group(int delayMs, int durationMs) {
        float p;
        if (page != 0) {
            p = reducedMotion ? 1 : HubMotion.ease(progress(now - pageAt, 240_000_000L));
            offsetY = 5 * (1 - p);
        } else {
            float time = progress(now - pageAt - delayMs * 1_000_000L, durationMs * 1_000_000L);
            p =
                    reducedMotion
                            ? 1
                            : durationMs == 440
                                    ? HubMotion.bezier(time, .2f, .7f, .2f, 1)
                                    : HubMotion.easeOut(time);
            offsetY = 12 * (1 - p);
        }
        beginAlpha(p);
        push(Matrix33.makeTranslate(0, offsetY));
        pageTransform = logical;
        offsetY = 0;
    }

    void ungroup() {
        pop();
        endAlpha();
        opacity = 1;
        offsetY = 0;
    }

    HubMotion.Tilt beginTilt(
            String id,
            float x,
            float y,
            float w,
            float h,
            float amplitude,
            float hoverScale,
            boolean intro,
            float shadowStrength,
            float radius) {
        HubMotion.Tilt state =
                tilts.computeIfAbsent(id, ignored -> new HubMotion.Tilt(intro && !reducedMotion));
        boolean hover = inside(x, y, w, h);
        float[] mouse = HubDraw.inversePoint(logical, mx, my);
        float nx = clamp((mouse[0] - x - w / 2) / (w / 2), -1, 1),
                ny = clamp((mouse[1] - y - h / 2) / (h / 2), -1, 1);
        state.update(
                dt,
                reducedMotion,
                hover,
                keyboardFocus && Objects.equals(focusId, id),
                Objects.equals(pressedId, id) && !pressMoved,
                nx,
                ny,
                amplitude);
        float scale = state.scale(hoverScale),
                rx = (float) Math.toRadians(state.rx.value),
                ry = (float) Math.toRadians(state.ry.value);
        float spread = scale * (.86f + .04f * state.lift.value), sw = w * spread, sh = h * spread;
        float dx = -(float) Math.sin(ry) * 22,
                dy = 12 + 16 * state.lift.value + (float) Math.sin(rx) * 18 - state.ty.value * .6f;
        int shadow =
                withAlpha(
                        0x8c000000,
                        shadowStrength * state.shown() * (.6f + .4f * state.lift.value));
        d.boxShadow(x + (w - sw) / 2, y + (h - sh) / 2, sw, sh, radius, dx, dy, 40, 0, shadow);
        beginAlpha(state.shown(), x, y, w, h);
        push(
                HubDraw.cssPerspective(
                        x + w / 2,
                        y + h / 2,
                        800,
                        state.rx.value,
                        state.ry.value,
                        0,
                        scale,
                        0,
                        state.ty.value));
        return state;
    }

    void endTilt(
            HubMotion.Tilt state,
            float x,
            float y,
            float w,
            float h,
            float radius,
            float amplitude,
            float glare) {
        float gx = .5f + state.ry.value / Math.max(amplitude, 1) * .46f,
                gy = .5f - state.rx.value / Math.max(amplitude, 1) * .46f;
        d.tiltGlare(x, y, w, h, radius, gx, gy, state.glare(glare, amplitude));
        pop();
        endAlpha();
    }

    void push(Matrix33 matrix) {
        matrixStack.push(logical);
        logical = logical.makeConcat(matrix);
        d.save();
        d.canvas().concat(matrix);
    }

    void pop() {
        d.restore();
        logical = matrixStack.pop();
    }

    void beginAlpha(float alpha) {
        alphaStack.push(interactiveAlpha);
        interactiveAlpha *= alpha;
        d.saveLayerAlpha(-128, -128, 1516, 1106, alpha);
    }

    void beginAlpha(float alpha, float x, float y, float w, float h) {
        alphaStack.push(interactiveAlpha);
        interactiveAlpha *= alpha;
        d.saveLayerAlpha(x, y, w, h, alpha);
    }

    void endAlpha() {
        d.restore();
        interactiveAlpha = alphaStack.pop();
    }

    float transition(String id, float target, int milliseconds) {
        return transitions
                .computeIfAbsent(id, ignored -> new HubMotion.Transition())
                .sample(target, now, milliseconds * 1_000_000L, reducedMotion);
    }

    float stateTransition(String id, float target, int milliseconds) {
        return stateTransition(id, target, milliseconds, false);
    }

    float selectorPosition(String id, boolean second) {
        return stateTransition(id, second ? 1 : 0, 220, true);
    }

    float stateTransition(String id, float target, int milliseconds, boolean easeOut) {
        return transitions
                .computeIfAbsent(
                        id,
                        ignored -> {
                            HubMotion.Transition c = new HubMotion.Transition();
                            c.value = c.target = c.from = target;
                            c.initialized = true;
                            c.started = now;
                            return c;
                        })
                .sample(target, now, milliseconds * 1_000_000L, reducedMotion, easeOut);
    }

    void shadow(float x, float y, float w, float h, float r, int color) {
        d.boxShadow(x, y, w, h, r, 0, 6, 22, 0, color);
    }

    void glass(float x, float y, float w, float h, float radius, int from, int to) {
        if (winter) {
            boolean previous = d.literalColors(true);
            try {
                HubWinterSheet.panel(d, x, y, w, h, radius, d.winterPaper().sheet(), 1);
            } finally {
                d.literalColors(previous);
            }
            return;
        }
        shadow(x, y, w, h, radius, 0x104b5d85);
        d.linearGradientRoundRect(
                x, y, w, h, radius, 135, new int[] {from, to}, new float[] {0, 1});
        d.strokeRoundRect(x + .5f, y + .5f, w - 1, h - 1, radius, 1, a(0xe9ffffff));
        if (h > 28) d.line(x + radius, y + 1, x + w - radius, y + 1, 1, a(0xd9ffffff));
    }

    void text(String s, float x, float baseline, float size, int color, boolean bold) {
        d.text(s, x, baseline, size, a(color), bold ? 600 : 400);
    }

    void trackedCenter(
            String s,
            float x,
            float baseline,
            float size,
            float tracking,
            int color,
            boolean bold,
            boolean serif) {
        float width = trackedWidth(s, size, tracking, bold ? 600 : 400, serif);
        tracked(s, x - width / 2, baseline, size, tracking, color, bold ? 600 : 400, serif);
    }

    float trackedWidth(String s, float size, float tracking, int weight, boolean serif) {
        return d.textWidthTracked(s, size, weight, tracking, serif);
    }

    void tracked(
            String s,
            float x,
            float baseline,
            float size,
            float tracking,
            int color,
            int weight,
            boolean serif) {
        d.textTracked(s, x, baseline, size, color, weight, tracking, serif);
    }

    String fitTracked(
            String s, float width, float size, float tracking, int weight, boolean serif) {
        if (trackedWidth(s, size, tracking, weight, serif) <= width) return s;
        int end = s.length();
        while (end > 0
                && trackedWidth(s.substring(0, end) + "…", size, tracking, weight, serif) > width)
            end = s.offsetByCodePoints(end, -1);
        return s.substring(0, end) + "…";
    }

    void titleLabel(
            HubData.Title title,
            float x,
            float baseline,
            float size,
            float tracking,
            boolean center,
            boolean locked) {
        titleLabel(
                title,
                x,
                baseline,
                size,
                tracking,
                center,
                locked,
                center ? 250 : x >= RIGHT ? 246 : 450);
    }

    float titleLabel(
            HubData.Title title,
            float x,
            float baseline,
            float size,
            float tracking,
            boolean center,
            boolean locked,
            float maxWidth) {
        return titleLabel(title, x, baseline, size, tracking, center, locked, maxWidth, false, 400);
    }

    float titleLabel(
            HubData.Title title,
            float x,
            float baseline,
            float size,
            float tracking,
            boolean center,
            boolean locked,
            float maxWidth,
            boolean serif,
            int defaultWeight) {
        if (live()) {
            List<HubData.Glyph> glyphs = title.glyphs();
            float width = richWidth(glyphs, size, tracking, serif, defaultWeight);
            float actual = Math.max(11, Math.min(size, size * maxWidth / Math.max(1, width)));
            width = richWidth(glyphs, actual, tracking, serif, defaultWeight);
            boolean clipped = width > maxWidth;
            float start = center ? x - Math.min(width, maxWidth) * .5f : x;
            d.save();
            d.clipRoundRect(start - 2, baseline - actual * 1.3f, maxWidth + 4, actual * 1.7f, 0);
            float cursor = start;
            for (HubData.Glyph glyph : glyphs) {
                int weight = glyph.bold() ? 700 : defaultWeight;
                float advance =
                        serif
                                ? d.textWidthSerif(glyph.text(), actual, weight)
                                : d.textWidth(glyph.text(), actual, weight);
                if (clipped && cursor + advance > start + maxWidth - actual) {
                    if (serif) serif("…", cursor, baseline, actual, INK, weight >= 700);
                    else text("…", cursor, baseline, actual, INK, false);
                    break;
                }
                d.richGlyph(
                        glyph.text(),
                        cursor,
                        baseline,
                        actual,
                        a(glyph.color()),
                        glyph.bold(),
                        glyph.italic(),
                        glyph.underline(),
                        glyph.strike(),
                        serif,
                        defaultWeight);
                cursor += advance + tracking;
            }
            d.restore();
            return Math.min(width, maxWidth);
        }
        String label = title.name();
        float labelWidth = trackedWidth(label, size, tracking, 600, false);
        if (center) x -= labelWidth / 2;
        if (locked) {
            tracked(label, x, baseline, size, tracking, 0xff78859b, 600, false);
            return labelWidth;
        }
        paintDemoTitle(title, x, baseline, size, tracking, 600, false);
        return labelWidth;
    }

    void paintDemoTitle(
            HubData.Title title,
            float x,
            float baseline,
            float size,
            float tracking,
            int weight,
            boolean serif) {
        String label = title.name();
        if (title.id().equals("dew"))
            d.textGradient(
                    label,
                    x,
                    baseline,
                    size,
                    weight,
                    tracking,
                    95,
                    new int[] {0xff168878, 0xff69a648},
                    new float[] {0, 1},
                    serif);
        else if (title.id().equals("star"))
            d.textGradient(
                    label,
                    x,
                    baseline,
                    size,
                    weight,
                    tracking,
                    100,
                    new int[] {0xffa97929, 0xffbf854a, 0xff8a69bb},
                    new float[] {0, .5f, 1},
                    serif);
        else tracked(label, x, baseline, size, tracking, HubData.titleText(title), weight, serif);
    }

    private float richWidth(
            List<HubData.Glyph> glyphs,
            float size,
            float tracking,
            boolean serif,
            int defaultWeight) {
        float width = 0;
        for (HubData.Glyph glyph : glyphs) {
            int weight = glyph.bold() ? 700 : defaultWeight;
            width +=
                    (serif
                                    ? d.textWidthSerif(glyph.text(), size, weight)
                                    : d.textWidth(glyph.text(), size, weight))
                            + tracking;
        }
        return Math.max(0, width - tracking);
    }

    void titleStatus(String label, float x, float y, boolean worn) {
        if (!winter) {
            pill(
                    label,
                    x,
                    y,
                    worn ? 0xffeff9f4 : 0xfff6f1ff,
                    worn ? 0xff4c8573 : 0xff85709d,
                    false);
            return;
        }
        var paper = d.winterPaper();
        float w = d.textWidth(label, 10, false) + 17;
        boolean previous = d.literalColors(true);
        d.roundRect(x, y, w, 22, 11, a(worn ? paper.cover() : paper.paper()));
        d.strokeRoundRect(x, y, w, 22, 11, 1, a(worn ? paper.stitch() : paper.ruling()));
        text(label, x + 8, y + 15, 10, worn ? paper.pine() : paper.muted(), false);
        d.literalColors(previous);
    }

    List<String> trackedLines(
            String s, float width, float size, float tracking, int weight, boolean serif) {
        List<String> result = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (int offset = 0; offset < s.length(); ) {
            int cp = s.codePointAt(offset);
            String glyph = new String(Character.toChars(cp));
            if (!line.isEmpty()
                    && trackedWidth(line + glyph, size, tracking, weight, serif) > width
                    && "，。！？；：、）》】」』…,.!?;:".indexOf(cp) < 0) {
                result.add(line.toString());
                line.setLength(0);
            }
            line.append(glyph);
            offset += Character.charCount(cp);
        }
        if (!line.isEmpty()) result.add(line.toString());
        return result;
    }

    void trackedWrap(
            String s,
            float x,
            float baseline,
            float width,
            float size,
            float lineHeight,
            float tracking,
            int color,
            int weight,
            boolean serif) {
        List<String> lines = trackedLines(s, width, size, tracking, weight, serif);
        for (int i = 0; i < lines.size(); i++)
            tracked(
                    lines.get(i),
                    x,
                    baseline + i * lineHeight,
                    size,
                    tracking,
                    color,
                    weight,
                    serif);
    }

    void serif(String s, float x, float baseline, float size, int color, boolean bold) {
        d.textSerif(s, x, baseline, size, a(color), bold);
    }

    void serifCenter(String s, float x, float baseline, float size, int color, boolean bold) {
        serif(s, x - d.textWidthSerif(s, size, bold) / 2, baseline, size, color, bold);
    }

    void textCenter(String s, float x, float baseline, float size, int color, boolean bold) {
        text(s, x - d.textWidth(s, size, bold ? 600 : 400) / 2, baseline, size, color, bold);
    }

    void textRight(String s, float right, float baseline, float size, int color, boolean bold) {
        text(s, right - d.textWidth(s, size, bold ? 600 : 400), baseline, size, color, bold);
    }

    void icon(String name, float x, float y, float size, int color) {
        d.icon(name, x, y, size, a(color));
    }

    void image(String name, float x, float y, float w, float h, float alpha) {
        d.image("textures/ui/" + name + ".png", x, y, w, h, alpha * opacity);
    }

    String fit(String s, float width, float size, boolean bold) {
        if (d.textWidth(s, size, bold ? 600 : 400) <= width) return s;
        int length = s.length();
        while (length > 0
                && d.textWidth(s.substring(0, length) + "…", size, bold ? 600 : 400) > width)
            length--;
        return s.substring(0, length) + "…";
    }

    int wrap(
            String s,
            float x,
            float baseline,
            float width,
            float size,
            float lineHeight,
            int color,
            boolean bold) {
        StringBuilder line = new StringBuilder();
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n' || d.textWidth(line.toString() + c, size, bold ? 600 : 400) > width) {
                // Keep Chinese closing punctuation with its preceding text.
                if (c != '\n' && "，。！？；：、）》】」』…,.!?;:".indexOf(c) >= 0 && !line.isEmpty()) {
                    line.append(c);
                    continue;
                }
                lines.add(line.toString());
                line.setLength(0);
            }
            if (c != '\n') line.append(c);
        }
        if (!line.isEmpty()) lines.add(line.toString());
        if (lines.size() > 1 && lines.get(lines.size() - 1).length() < 3) {
            int previous = lines.size() - 2;
            String prev = lines.get(previous), last = lines.get(previous + 1);
            int move = Math.min(3 - last.length(), Math.max(0, prev.length() - 1));
            if (move > 0) {
                lines.set(previous, prev.substring(0, prev.length() - move));
                lines.set(previous + 1, prev.substring(prev.length() - move) + last);
            }
        }
        for (int i = 0; i < lines.size(); i++)
            text(lines.get(i), x, baseline + i * lineHeight, size, color, bold);
        return lines.size();
    }

    void pill(String label, float x, float y, int background, int color, boolean dot) {
        float w = d.textWidth(label, 10, false) + (dot ? 24 : 17);
        d.roundRect(x, y, w, 22, 11, a(background));
        d.strokeRoundRect(x, y, w, 22, 11, 1, a(0xb3ffffff));
        if (dot) d.circle(x + 9, y + 11, 2, a(color));
        text(label, x + (dot ? 17 : 8), y + 15, 10, color, false);
    }

    void button(
            String id,
            String label,
            float x,
            float y,
            float w,
            float h,
            boolean primary,
            boolean enabled,
            Runnable click) {
        button(id, label, x, y, w, h, primary, enabled, click, null);
    }

    void button(
            String id,
            String label,
            float x,
            float y,
            float w,
            float h,
            boolean primary,
            boolean enabled,
            Runnable click,
            String iconName) {
        if (winter) {
            boolean previous = d.literalColors(true);
            try {
                stationButton(id, label, x, y, w, h, primary, enabled, click, iconName);
            } finally {
                d.literalColors(previous);
            }
            return;
        }
        boolean hover = enabled && inside(x, y, w, h),
                press = enabled && Objects.equals(pressedId, id);
        float hval = transition(id + "-hover", hover ? 1 : 0, 140),
                move = transition(id + "-move", primary ? (press ? 1 : hover ? -1 : 0) : 0, 140);
        boolean displayDisabled = !enabled && id.equals("display-selected");
        beginAlpha(enabled || displayDisabled ? 1 : .42f, x, y, w, h);
        push(Matrix33.makeTranslate(0, move));
        // CSS gradient-image values are discrete; only the color/shadow/transform
        // longhands interpolate. Do not invent a gradient crossfade here.
        int from = primary ? (hover ? 0xff987af5 : 0xff8b6cf1) : mix(0xb8ffffff, 0xffffffff, hval);
        int to = primary ? (hover ? 0xff694acb : 0xff6d53d6) : mix(0xb8ffffff, 0xffffffff, hval);
        if (displayDisabled) {
            from = 0x59ffffff;
            to = 0x59ffffff;
        }
        boolean equippedButton = id.equals("equip") && !primary;
        boolean darkProfileLink = dark && (id.equals("profile") || id.equals("memories"));
        if (equippedButton) {
            from =
                    to =
                            dark
                                    ? mix(0x24ab8bdf, 0x38ab8bdf, hval)
                                    : mix(0xfff0ebff, 0xffe9e0ff, hval);
        }
        if (darkProfileLink) from = to = 0x14c5c7ef;
        d.boxShadow(
                x,
                y,
                w,
                h,
                12,
                0,
                darkProfileLink ? 2 + 2 * hval : primary ? 4 + 2 * hval : 3 + 2 * hval,
                darkProfileLink ? 7 + 5 * hval : 10 + 6 * hval,
                darkProfileLink ? -3 * hval : 0,
                darkProfileLink
                        ? mix(0x1f081127, 0x42080f2b, hval)
                        : primary
                                ? mix(0x337558d1, 0x4d7558d1, hval)
                                : mix(0x186c6588, 0x2b6c6588, hval));
        d.linearGradientRoundRect(
                x, y, w, h, 12, primary ? 130 : 135, new int[] {from, to}, new float[] {0, 1});
        d.insetBoxShadow(
                x,
                y,
                w,
                h,
                12,
                0,
                1,
                0,
                0,
                dark && !primary
                        ? mix(0x0cdbe6ff, 0x16dbe6ff, hval)
                        : primary ? mix(0x61ffffff, 0x70ffffff, hval) : 0xffffffff);
        d.strokeRoundRect(
                x,
                y,
                w,
                h,
                12,
                1,
                a(
                        darkProfileLink
                                ? mix(0x2ebeccec, 0x42c6d4f4, hval)
                                : equippedButton
                                        ? (dark ? 0x40b69be5 : 0xffddd4f5)
                                        : primary ? 0xff8c71e6 : 0xe6ffffff));
        int color =
                displayDisabled
                        ? 0xff9a90b3
                        : darkProfileLink
                                ? 0xffcbbbeb
                                : equippedButton
                                        ? (dark ? 0xffd1bbf5 : 0xff7964b6)
                                        : primary ? 0xffffffff : 0xff64539e;
        float size = iconName == null ? 12 : 11,
                labelWidth = d.textWidth(label, size, 500),
                contentWidth = labelWidth + (iconName == null ? 0 : 21);
        float labelX = x + (w - contentWidth) / 2;
        d.text(label, labelX, y + (h + 10) / 2, size, color, 500);
        if (iconName != null) icon(iconName, labelX + labelWidth + 6, y + (h - 15) / 2, 15, color);
        if (enabled) hit(id, label, x, y, w, h, click);
        pop();
        endAlpha();
    }

    void goldButton(String id, String label, float x, float y, float w, float h, Runnable click) {
        boolean hover = inside(x, y, w, h);
        float value = transition(id + "-hover", hover ? 1 : 0, 140);
        float move =
                transition(id + "-move", Objects.equals(pressedId, id) ? 1 : hover ? -1 : 0, 140);
        push(Matrix33.makeTranslate(0, move));
        d.boxShadow(
                x,
                y,
                w,
                h,
                12,
                0,
                4 + 2 * value,
                10 + 6 * value,
                0,
                mix(0x269c7e44, 0x4d7558d1, value));
        d.linearGradientRoundRect(
                x,
                y,
                w,
                h,
                12,
                hover ? 130 : 140,
                new int[] {hover ? 0xff987af5 : 0xffbe9455, hover ? 0xff694acb : 0xffa57e42},
                new float[] {0, 1});
        d.insetBoxShadow(x, y, w, h, 12, 0, 1, 0, 0, mix(0x66ffffff, 0x70ffffff, value));
        d.strokeRoundRect(x, y, w, h, 12, 1, a(0xffc5a477));
        d.text(label, x + (w - d.textWidth(label, 11, 500)) / 2, y + 24, 11, 0xffffffff, 500);
        hit(id, label, x, y, w, h, click);
        pop();
    }

    void textButton(
            String id,
            String label,
            float x,
            float y,
            float w,
            float h,
            Runnable click,
            boolean enabled) {
        if (winter) {
            boolean previous = d.literalColors(true);
            try {
                var p = d.winterPaper();
                float v = extensionHover(id, enabled && inside(x, y, w, h));
                if (v > 0) d.roundRect(x, y, w, h, 7, withAlpha(p.cover(), v));
                textCenter(
                        label,
                        x + w / 2,
                        y + (h + 9) / 2,
                        11,
                        enabled ? p.pine() : p.muted(),
                        false);
                if (enabled) hit(id, label, x, y, w, h, click);
            } finally {
                d.literalColors(previous);
            }
            return;
        }
        float value = transition(id + "-hover", enabled && inside(x, y, w, h) ? 1 : 0, 140);
        if (value > 0) d.roundRect(x, y, w, h, 8, withAlpha(0x88ffffff, value));
        textCenter(label, x + w / 2, y + (h + 9) / 2, 11, enabled ? 0xff7a6a99 : 0xffb2aabc, false);
        if (enabled) hit(id, label, x, y, w, h, click);
    }

    void iconButton(
            String id,
            String label,
            String name,
            float x,
            float y,
            float size,
            Runnable click,
            boolean enabled) {
        if (winter) {
            boolean previous = d.literalColors(true);
            try {
                var p = d.winterPaper();
                float v = extensionHover(id, enabled && inside(x, y, size, size));
                if (v > 0) d.roundRect(x, y, size, size, 7, withAlpha(p.cover(), v));
                icon(
                        name,
                        x + (size - 16) / 2,
                        y + (size - 16) / 2,
                        16,
                        withAlpha(p.pine(), enabled ? 1 : .35f));
                if (enabled) hit(id, label, x, y, size, size, click);
            } finally {
                d.literalColors(previous);
            }
            return;
        }
        float value = transition(id + "-hover", enabled && inside(x, y, size, size) ? 1 : 0, 140);
        if (value > 0) d.roundRect(x, y, size, size, 10, withAlpha(0xffe6e8f7, value));
        icon(
                name,
                x + (size - 18) / 2,
                y + (size - 18) / 2,
                18,
                enabled ? mix(0xff6f7a92, 0xff5844b4, value) : 0xffb9bdcd);
        if (enabled) hit(id, label, x, y, size, size, click);
    }

    void toggle(String id, boolean checked, float x, float y, Runnable click) {
        toggle(id, checked, x, y, click, true);
    }

    void toggle(String id, boolean checked, float x, float y, Runnable click, boolean enabled) {
        HubMotion.Transition channel =
                transitions.computeIfAbsent(
                        id + "-toggle",
                        ignored -> {
                            HubMotion.Transition c = new HubMotion.Transition();
                            c.value = c.target = c.from = checked ? 1 : 0;
                            c.initialized = true;
                            c.started = now;
                            return c;
                        });
        float value = channel.sample(checked ? 1 : 0, now, 180_000_000L, reducedMotion);
        if (winter) {
            boolean previous = d.literalColors(true);
            try {
                var p = d.winterPaper();
                d.roundRect(x, y, 33, 18, 10, mix(p.ruling(), p.pine(), value));
                d.circle(x + 9 + 15 * value, y + 9, 7, p.sheet());
                if (enabled) hit(id, checked ? "关闭" : "开启", x - 3, y - 5, 39, 28, click);
            } finally {
                d.literalColors(previous);
            }
            return;
        }
        d.roundRect(x, y, 33, 18, 20, mix(0xffd5daea, 0xff8e76d9, value));
        d.strokeRoundRect(x, y, 33, 18, 20, 1, checked ? 0xff8770ce : 0xffc5cbdd);
        d.circle(x + 9 + 15 * value, y + 9, 7, 0xffffffff);
        if (enabled) hit(id, checked ? "关闭" : "开启", x - 3, y - 5, 39, 28, click);
    }

    void hit(String id, String label, float x, float y, float w, float h, Runnable click) {
        if (interactiveAlpha < .2f) return;
        Hit region =
                new Hit(
                        id,
                        label,
                        x,
                        y,
                        w,
                        h,
                        logical,
                        id.startsWith("title-") ? titleClipTransform : null,
                        click);
        nextHits.add(region);
        if (keyboardFocus && Objects.equals(focusId, id)) {
            if (winter) {
                if (!profiles.isOpen()) winterFocusMark(x, y, w, h);
            } else d.strokeRoundRect(x - 4, y - 4, w + 8, h + 8, 12, 3, a(0xff7758e8));
        }
    }

    private void winterFocusMark(float x, float y, float w, float h) {
        float markWidth = Math.min(22, w * .55f);
        float left = x + (w - markWidth) / 2;
        float bottom = y + h - 3;
        var paper = d.winterPaper();
        boolean previous = d.literalColors(true);
        d.line(left, bottom, left + markWidth, bottom, 3.5f, a(paper.sheet()));
        d.line(left, bottom, left + markWidth, bottom, 1.8f, a(paper.pine()));
        d.literalColors(previous);
    }

    boolean inside(float x, float y, float w, float h) {
        float[] p = HubDraw.inversePoint(logical, mx, my);
        return p[0] >= x && p[1] >= y && p[0] < x + w && p[1] < y + h;
    }

    int a(int color) {
        return withAlpha(color, opacity);
    }

    static int withAlpha(int color, float alpha) {
        return (Math.round((color >>> 24) * clamp(alpha, 0, 1)) << 24) | (color & 0xffffff);
    }

    static int lighten(int color, float amount) {
        return amount >= 0 ? mix(color, 0xffffffff, amount) : mix(color, 0xff26344f, -amount);
    }

    static int desaturate(int color, float saturation) {
        int r = (color >>> 16) & 255, g = (color >>> 8) & 255, b = color & 255;
        int grey = Math.round(.2126f * r + .7152f * g + .0722f * b);
        return mix((color & 0xff000000) | (grey << 16) | (grey << 8) | grey, color, saturation);
    }

    static int mix(int from, int to, float fraction) {
        float f = clamp(fraction, 0, 1);
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int start = (from >>> shift) & 255, end = (to >>> shift) & 255;
            result |= Math.round(start + (end - start) * f) << shift;
        }
        return result;
    }

    static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    static float progress(long elapsed, long duration) {
        return clamp((float) elapsed / duration, 0, 1);
    }

    /** CSS color-mix(in srgb) interpolates premultiplied alpha. */
    static int cssMix(int from, int to, float amount) {
        float first = (from >>> 24) * (1 - amount),
                second = (to >>> 24) * amount,
                total = first + second;
        int result = Math.round(total) << 24;
        for (int shift = 0; shift <= 16; shift += 8)
            result |=
                    Math.round(
                                    (((from >>> shift) & 255) * first
                                                    + ((to >>> shift) & 255) * second)
                                            / total)
                            << shift;
        return result;
    }

    static String shortDate(LocalDate date) {
        return String.format(
                java.util.Locale.ROOT, "%02d/%02d", date.getMonthValue(), date.getDayOfMonth());
    }

    void setReducedMotion(boolean value) {
        localReducedMotion = value;
        updateReducedMotion();
    }

    private void updateReducedMotion() {
        boolean value = localReducedMotion || systemReducedMotion;
        if (reducedMotion == value) return;
        reducedMotion = value;
        if (value) playWelcome = false;
        navAt = System.nanoTime();
        tilts.clear();
        snapBuiltInNavigation(value);
    }

    private void snapBuiltInNavigation(boolean reduced) {
        for (HubBuiltInPage builtIn : builtInPages) {
            HubMotion.Navigation state = builtIn.navigationState();
            if (state == null) continue;
            state.enter.snap(reduced ? 1 : 0);
            state.hover.snap(0);
            state.press.snap(0);
            state.x.snap(0);
            state.y.snap(0);
        }
    }

    int welcomeIndex() {
        return Math.max(0, List.of("once", "login", "always").indexOf(welcomeMode));
    }

    private void notify(String value) {
        toast.show(value, HubToast.Kind.INFO, System.nanoTime());
    }

    void notifyUser(String value) {
        notify(value);
    }

    void markTitleClipTransform() {
        titleClipTransform = logical;
    }

    boolean anyHitMatches(float x, float y, Predicate<String> idFilter) {
        return hits.stream().anyMatch(hit -> idFilter.test(hit.id()) && hit.contains(x, y));
    }

    void navigate(int destination) {
        cancelExternalPendingOpen();
        if (page == destination) return;
        HubBuiltInPage target = builtInPage(destination);
        if (target == null) return;
        currentBuiltInPage().onLeave(destination);
        page = destination;
        pageAt = System.nanoTime();
        focusId = null;
        target.onEnter(pageAt);
    }

    void openOverlay(String value) {
        restoreFocus = focusId;
        overlay = value;
        reading = null;
        focusId = "modal-close";
        modals.markOpened(System.nanoTime());
        hits.clear();
    }

    void openReading(HubData.Entry value) {
        if (value != null) showReading(value);
    }

    void showReading(HubData.Entry value) {
        restoreFocus = focusId;
        fortunesView.resetReadingScroll();
        reading = value;
        overlay = null;
        focusId = "modal-close";
        modals.markOpened(System.nanoTime());
        hits.clear();
    }

    private void dismissReceipt() {
        toast.dismiss(System.nanoTime());
        if ("receipt-close".equals(focusId)) focusId = null;
    }

    public boolean escape() {
        if ("receipt-close".equals(focusId) && toast.canClose()) {
            dismissReceipt();
            return true;
        }
        if (profiles.isOpen()) {
            profiles.escape();
            return true;
        }
        if (dismissOverlay()) return true;
        return currentBuiltInPage().escape();
    }

    public boolean dismissOverlay() {
        syncExternalPages();
        for (ExternalSlot slot : externalSlots) {
            if (slot.page().escape()) return true;
        }
        if (profiles.isOpen()) {
            profiles.requestClose();
            return true;
        }
        if (overlay == null && reading == null && currentBuiltInPage().dismissOverlay())
            return true;
        if (overlay == null && reading == null) return false;
        overlay = null;
        reading = null;
        modals.resetOnOpen();
        focusId = restoreFocus;
        restoreFocus = null;
        hits.clear();
        return true;
    }

    public void onCopyToClipboard(Consumer<String> copy) {
        copyToClipboard = copy;
    }

    public boolean mouseClicked(float x, float y, int button) {
        return mouseClicked(x, y, button, false);
    }

    public boolean mouseClicked(float x, float y, int button, boolean shift) {
        syncExternalPages();
        if (button == 0) {
            for (ExternalSlot slot : externalSlots) {
                HubExternalPage ext = slot.page();
                if (ext.active() && ext.pointerDown(x, y, shift)) {
                    keyboardFocus = false;
                    return true;
                }
            }
        }
        if (button != 0) return false;
        if (toast.press(x, y, System.nanoTime())) return true;
        if (isWelcomePlaying()) return true;
        mx = x;
        my = y;
        keyboardFocus = false;
        keyboardPress = false;
        pressMoved = false;
        pressX = x;
        pressY = y;
        if (profiles.isOpen()) {
            if (profiles.pointerDown(x, y, shift)) {
                pressedId = null;
                pressedHit = null;
                return true;
            }
        }
        if (!profiles.isOpen() && overlay == null && reading == null) {
            float[] point = HubDraw.inversePoint(pageTransform, x, y);
            if (currentBuiltInPage().pointerDown(x, y, point[0], point[1], shift)) {
                pressedId = null;
                pressedHit = null;
                return true;
            }
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (!hit.contains(x, y)) continue;
            focusId = hit.id();
            pressedId = hit.id();
            pressedHit = hit;
            boolean dragging = "frequency".equals(hit.id());
            modals.setSliderDragging(dragging);
            modals.setSliderTransform(hit.transform());
            if (dragging || currentBuiltInPage().textFocused(hit.id())) hit.action().run();
            return true;
        }
        if (profiles.isOpen()) return true;
        if (overlay != null || reading != null) {
            if (modals.outsideModal(x, y)) dismissOverlay();
            return true;
        }
        focusId = null;
        return true;
    }

    public boolean mouseReleased(float x, float y, int button) {
        syncExternalPages();
        if (button == 0) {
            for (ExternalSlot slot : externalSlots) {
                HubExternalPage ext = slot.page();
                if (ext.active() && ext.pointerUp()) return true;
            }
        }
        if (button != 0) return false;
        if (toast.release(x, y, System.nanoTime())) return true;
        if (profiles.isOpen() && profiles.pointerUp()) {
            pressedId = null;
            pressedHit = null;
            return true;
        }
        if (!profiles.isOpen()) {
            float[] point = HubDraw.inversePoint(pageTransform, x, y);
            if (currentBuiltInPage().pointerUp(point[0], point[1])) {
                pressedId = null;
                pressedHit = null;
                return true;
            }
        }
        boolean active = modals.isSliderDragging() || pressedHit != null;
        Hit released =
                pressedHit == null
                        ? null
                        : hits.stream()
                                .filter(hit -> hit.id().equals(pressedHit.id()))
                                .findFirst()
                                .orElse(null);
        if (released != null
                && !modals.isSliderDragging()
                && !currentBuiltInPage().textFocused(released.id())
                && released.contains(x, y)
                && (!released.id().startsWith("title-") || !pressMoved)) released.action().run();
        modals.setSliderDragging(false);
        pressedId = null;
        pressedHit = null;
        return active;
    }

    public boolean mouseDragged(float x, float y, int button, float dx, float dy) {
        syncExternalPages();
        if (button == 0) {
            for (ExternalSlot slot : externalSlots) {
                HubExternalPage ext = slot.page();
                if (ext.active() && ext.pointerDrag(x, y)) return true;
            }
        }
        mx = x;
        my = y;
        if (button == 0 && toast.drag(y, System.nanoTime())) return true;
        if (button == 0 && profiles.isOpen() && profiles.pointerDrag(x, y)) return true;
        if (button == 0 && !profiles.isOpen() && overlay == null && reading == null) {
            float[] point = HubDraw.inversePoint(pageTransform, x, y);
            if (currentBuiltInPage().pointerDrag(point[0], point[1])) return true;
        }
        if (button == 0 && pressedId != null && Math.hypot(x - pressX, y - pressY) > 6)
            pressMoved = true;
        if (modals.isSliderDragging() && button == 0) {
            modals.setWelcomeFromX(x);
            return true;
        }
        return pressedId != null;
    }

    public boolean mouseScrolled(float x, float y, double amount) {
        syncExternalPages();
        for (ExternalSlot slot : externalSlots) {
            HubExternalPage ext = slot.page();
            if (ext.active()) return ext.scroll(x, y, amount);
        }
        if (profiles.isOpen()) {
            profiles.scroll(x, y, amount);
            return true;
        }
        if (modals.mouseScrolled(x, y, amount)) return true;
        if (isWelcomePlaying() || overlay != null || reading != null) return true;
        float[] p = HubDraw.inversePoint(pageTransform, x, y);
        return currentBuiltInPage().scroll(p[0], p[1], amount);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        syncExternalPages();
        for (ExternalSlot slot : externalSlots) {
            HubExternalPage ext = slot.page();
            if (ext.keyPressed(keyCode, scanCode, modifiers, focusId)) {
                if (keyCode == 258 && winter) keyboardFocus = true;
                return true;
            }
        }
        if (keyCode == 256) return escape();
        if (isWelcomePlaying()) return true;
        if (modals.keyScrollReading(keyCode)) return true;
        if (keyCode == 297 && !live()) {
            if (overlay == null && reading == null) openOverlay("preview");
            else if ("preview".equals(overlay)) dismissOverlay();
            return true;
        }
        if (page == 1 && titlesView.handleSortMenuKey(keyCode)) return true;
        if (keyCode == 258) {
            keyboardFocus = true;
            if (hits.isEmpty()) return true;
            int index = -1;
            for (int i = 0; i < hits.size(); i++)
                if (hits.get(i).id().equals(focusId)) {
                    index = i;
                    break;
                }
            boolean shift = (modifiers & 1) != 0;
            index = Math.floorMod(index + (shift ? -1 : 1), hits.size());
            focusId = hits.get(index).id();
            return true;
        }
        if (profiles.isOpen() && profiles.key(keyCode, modifiers, focusId)) return true;
        if (!profiles.isOpen()
                && overlay == null
                && reading == null
                && currentBuiltInPage().keyPressed(keyCode, scanCode, modifiers, focusId))
            return true;
        if ("frequency".equals(focusId)
                && "settings".equals(overlay)
                && modals.handleFrequencyKey(keyCode)) return true;
        if (keyCode == 257 || keyCode == 335 || (keyCode == 32 && !"search".equals(focusId))) {
            for (Hit hit : hits)
                if (hit.id().equals(focusId)) {
                    pressedId = hit.id();
                    pressedHit = hit;
                    keyboardPress = true;
                    keyboardFocus = true;
                    if (keyCode != 32) hit.action().run();
                    return true;
                }
        }
        return textInputFocused() || profiles.isOpen() || overlay != null || reading != null;
    }

    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (!keyboardPress || (keyCode != 257 && keyCode != 335 && keyCode != 32)) return false;
        if (keyCode == 32 && pressedHit != null) {
            Hit current =
                    hits.stream()
                            .filter(hit -> hit.id().equals(pressedHit.id()))
                            .findFirst()
                            .orElse(null);
            if (current != null) current.action().run();
        }
        pressedId = null;
        pressedHit = null;
        keyboardPress = false;
        return true;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        syncExternalPages();
        for (ExternalSlot slot : externalSlots) {
            HubExternalPage ext = slot.page();
            if (ext.textFocused(focusId)) return ext.charTyped(codePoint, modifiers, focusId);
        }
        if (textInputFocused() && !Character.isISOControl(codePoint) && (modifiers & 6) == 0) {
            if (profiles.isOpen()) {
                profiles.type(codePoint, focusId);
                return true;
            }
            if (currentBuiltInPage().charTyped(codePoint, modifiers, focusId)) return true;
            pasteText(Character.toString(codePoint));
            return true;
        }
        return overlay != null || reading != null;
    }

    public boolean textInputFocused() {
        syncExternalPages();
        for (ExternalSlot slot : externalSlots) {
            HubExternalPage ext = slot.page();
            if (ext.active()) return ext.textFocused(focusId);
        }
        if (isWelcomePlaying()) return false;
        if (profiles.isOpen()) return profiles.textFocused(focusId);
        return overlay == null && reading == null && currentBuiltInPage().textFocused(focusId);
    }

    public String selectedInputText() {
        syncExternalPages();
        for (ExternalSlot slot : externalSlots) {
            HubExternalPage ext = slot.page();
            if (ext.textFocused(focusId)) return ext.selectedText();
        }
        if (profiles.isOpen()) return profiles.selectedText(focusId);
        return currentBuiltInPage().selectedText(focusId);
    }

    public String cutInputText() {
        syncExternalPages();
        for (ExternalSlot slot : externalSlots) {
            HubExternalPage ext = slot.page();
            if (ext.textFocused(focusId)) return ext.cutText();
        }
        if (profiles.isOpen()) return profiles.cutText(focusId);
        return currentBuiltInPage().cutText(focusId);
    }

    public void panelVisibility(boolean open) {
        syncExternalPages();
        for (ExternalSlot slot : externalSlots) slot.page().onPanelVisibility(open);
        if (!open) {
            for (HubBuiltInPage builtIn : builtInPages) builtIn.onPanelVisibility(false);
        } else {
            currentBuiltInPage().onPanelVisibility(true);
        }
    }

    public String pointerCursor() {
        syncExternalPages();
        for (ExternalSlot slot : externalSlots) {
            String cursor = slot.page().pointerCursor(mx, my);
            if (cursor != null && !cursor.isEmpty()) return cursor;
        }
        if (toast.pointer(mx, my)) return "pointer";
        if (isWelcomePlaying()) return "default";
        if (!profiles.isOpen() && overlay == null && reading == null) {
            float[] point = HubDraw.inversePoint(pageTransform, mx, my);
            String cursor = currentBuiltInPage().pointerCursor(point[0], point[1]);
            if (cursor != null) return cursor;
        }
        for (int i = hits.size() - 1; i >= 0; i--)
            if (hits.get(i).contains(mx, my))
                return (currentBuiltInPage().textFocused(hits.get(i).id())
                                || profiles.textFocused(hits.get(i).id()))
                        ? "text"
                        : "pointer";
        return "default";
    }

    /** Clipboard access belongs to the Minecraft screen; only sanitized text enters this UI. */
    public void pasteText(String value) {
        syncExternalPages();
        for (ExternalSlot slot : externalSlots) {
            HubExternalPage ext = slot.page();
            if (ext.textFocused(focusId)) {
                if (value != null) ext.pasteText(value);
                return;
            }
        }
        if (!textInputFocused() || value == null) return;
        if (profiles.isOpen()) {
            profiles.paste(value, focusId);
            return;
        }
        currentBuiltInPage().pasteText(value, focusId);
    }
}
