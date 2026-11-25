/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.ui;

import com.pixelmc.hub.client.HubModpackSync;
import com.pixelmc.hub.client.render.HubDraw;
import com.pixelmc.hub.client.render.GlassStyle;
import com.pixelmc.hub.client.render.IconPose;
import io.github.humbleui.skija.Matrix33;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.text.Collator;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import com.google.gson.JsonObject;

/** Native rendering of the approved handbook, with explicitly separate live and demo data. */
public final class HubView {
    private static final int INK = 0xff26344f, MUTED = 0xff73809a, PURPLE = 0xff765bd4;
    private static final float LEFT = 236, WIDTH = 996, RIGHT = 922;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy / MM / dd");
    private final HubData data = new HubData();
    private final List<Hit> hits = new ArrayList<>();
    private final List<Hit> nextHits = new ArrayList<>();
    private final Map<String, HubMotion.Transition> transitions = new HashMap<>();
    private final Map<String, HubMotion.Tilt> tilts = new HashMap<>();
    private final Map<String, HubMotion.Navigation> extensionNavigation = new HashMap<>();
    private final NavigationEntry[] builtInNavigation = {
        builtInEntry(0, "个人主页", "home", 0xff569dfb, 0xff1949e6),
        builtInEntry(1, "我的称号", "tag", 0xffbf72f3, 0xff6f34d5),
        builtInEntry(2, "我的签笺", "book-open", 0xffffb83d, 0xfff8651b),
        builtInEntry(3, "更新日志", "newspaper", 0xff3cd77f, 0xff109e73),
        builtInEntry(4, "玩家排行", "trophy", 0xffff78c8, 0xffdf29a0)
    };
    private final HubMotion.Navigation[] navigation = {
        new HubMotion.Navigation(),
        new HubMotion.Navigation(),
        new HubMotion.Navigation(),
        new HubMotion.Navigation(),
        new HubMotion.Navigation()
    };
    private final Deque<Matrix33> matrixStack = new ArrayDeque<>();
    private final Deque<Float> alphaStack = new ArrayDeque<>();
    private Matrix33 logical = Matrix33.IDENTITY;
    private Matrix33 titleClipTransform = Matrix33.IDENTITY,
            pageTransform = Matrix33.IDENTITY,
            sliderTransform = Matrix33.IDENTITY;
    private HubDraw d;
    private float mx, my, opacity = 1, offsetY, interactiveAlpha = 1, dt = 1f / 60;
    private long now = System.nanoTime(),
            openedAt = now,
            pageAt = now,
            monthAt = now,
            lastFrame = now,
            modalAt,
            navAt = now;
    private boolean titleSortOpen;
    private int page, filter, sort, fortuneState = 3, searchCaret;
    private String sessionPlayerId = "";
    private String playerName = "zhan_cpu",
            equipped = "dew",
            selectedTitle = "dew",
            query = "",
            welcomeMode = "login";
    private boolean dark, winter, communityLoaded;
    private Runnable afterProfileClose, beforeThemeChange;
    private boolean localReducedMotion, systemReducedMotion;
    private boolean visible = true,
            reducedMotion,
            closeRequest,
            playWelcome,
            compact,
            history,
            sliderDragging,
            searchSelectAll;
    private float titleScroll, historyScroll;
    private YearMonth month = YearMonth.from(data.today);
    private int monthDirection = 1;
    private LocalDate selectedDate = data.today, displayDate = data.today;
    private String overlay, focusId, restoreFocus, pressedId;
    private final HubToast toast = new HubToast();
    private Hit pressedHit;
    private boolean keyboardFocus, pressMoved, keyboardPress;
    private float pressX, pressY;
    private HubData.Entry reading;
    private float modalX, modalY, modalW, modalH;
    private float viewportX, viewportY, viewportW = 1260, viewportH = 850;

    @FunctionalInterface
    public interface PlayerAction {
        void request(String module, String action, JsonObject payload);
    }

    private PlayerAction actions;
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

    public boolean winter() {
        return winter;
    }

    public void setWinter(boolean value) {
        if (winter == value) return;
        if (beforeThemeChange != null) beforeThemeChange.run();
        winter = value;
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

    private String selfPlayerId() {
        return data.playerUuid.isBlank() ? sessionPlayerId : data.playerUuid;
    }

    private void openPublicProfile(String playerId) {
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

    private int memoriesReturnPage;

    private void leaveMemories() {
        int destination = winter ? memoriesReturnPage : 0;
        if (destination == 3 && updates.loaded() && !updates.enabled()) destination = 0;
        navigate(destination);
    }

    private void openMemories(String playerId, String archiveId) {
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

    private long rankingsPreviewAt;

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
            int first = stationPage * STATION_PLAYERS_PER_PAGE;
            int end = Math.min(first + STATION_PLAYERS_PER_PAGE, stationPlayers.size());
            for (int i = first; i < end; i++) {
                StationPlayer player = stationPlayers.get(i);
                players.put(player.id(), player.name());
            }
        }
        if (profiles.isOpen() && !profiles.playerId().isBlank())
            players.putIfAbsent(profiles.playerId(), profiles.playerName());
        return players;
    }

    private final Map<String, String> moduleStates = new HashMap<>();
    private final java.util.Set<String> pendingModules = new java.util.HashSet<>();
    private boolean catalogueComplete = true;
    private JsonObject titleDraft;
    private boolean calendarLoading, chooseLatestOnCalendar;
    private String connectionName = "当前存档";
    private byte[] serverIcon;

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
        stationArchiveId = "";
        stationPlayers = List.of();
        stationSharedCounts = Map.of();
        stationPage = 0;
        communityLoaded = false;
        afterProfileClose = null;
        rankingsPreviewAt = 0;
        data.clearLive();
        equipped = null;
        selectedTitle = "";
        displayDate = null;
        fortuneState = 0;
        month = YearMonth.from(data.today);
        selectedDate = data.today;
        catalogueComplete = false;
        titleDraft = null;
        calendarLoading = true;
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

    private boolean live() {
        return actions != null;
    }

    private boolean hasSnapshot(String module) {
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

    private boolean ready(String module) {
        return !live() || hasSnapshot(module) || moduleStates.getOrDefault(module, "").isEmpty();
    }

    private boolean privacyKnown() {
        return !live()
                || catalogueComplete
                        && (equipped == null || equipped.isEmpty() || data.title(equipped) != null);
    }

    private boolean canChangeVisibility() {
        return !live()
                || privacyKnown()
                        && ready("titles")
                        && equipped != null
                        && !equipped.isEmpty()
                        && !pendingModules.contains("titles");
    }

    private void changeVisibility() {
        if (live()) {
            if (!canChangeVisibility()) return;
            JsonObject p = mutation("titles");
            p.addProperty("visible", !visible);
            request("titles", "visibility", p);
            return;
        }
        visible = !visible;
        feedbackSuccess(visible ? "已开启称号展示 · 本地演示" : "展示已隐藏，佩戴状态保留");
    }

    private String moduleLabel(String module) {
        return !live()
                ? "演示"
                : moduleStates.getOrDefault(module, "").isEmpty()
                        ? "已连接"
                        : moduleStates.get(module);
    }

    private int titleCount() {
        return (int) data.titles.stream().filter(HubData.Title::owned).count();
    }

    private int archiveCount() {
        return live() ? data.archiveCount : archive().size();
    }

    private String timestamp(long at, String pattern) {
        return at <= 0
                ? "暂无记录"
                : java.time.Instant.ofEpochMilli(at)
                        .atZone(data.profileZone())
                        .format(DateTimeFormatter.ofPattern(pattern));
    }

    private String titleDate(HubData.Title title) {
        return live() ? timestamp(title.acquiredAt(), "yyyy/MM/dd") : title.date();
    }

    private long journeyDays() {
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

    private void request(String module, String action, JsonObject payload) {
        if (actions != null) actions.request(module, action, payload);
        else if (module.equals("community")) communityFailure(action, "当前预览未连接服务器。", payload);
    }

    private JsonObject mutation(String module) {
        JsonObject p = new JsonObject();
        p.addProperty("expectedRevision", data.revisions.getOrDefault(module, ""));
        return p;
    }

    private void calendarRequest() {
        if (live() && ready("fortunes")) {
            calendarLoading = true;
            JsonObject p = new JsonObject();
            p.addProperty("month", month.toString());
            request("fortunes", "calendar", p);
        }
    }

    private boolean calendarInitialLoading() {
        return live() && calendarLoading && !data.calendarMonths.contains(month);
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
            stationArchiveId = data.scopeId.substring(data.scopeId.indexOf('\n') + 1);
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
            if (action.equals("calendar")) {
                data.calendar(result);
                if (month.toString().equals(HubData.string(result, "month", ""))) {
                    calendarLoading = false;
                    if (chooseLatestOnCalendar)
                        selectedDate =
                                archive().keySet().stream()
                                        .filter(date -> YearMonth.from(date).equals(month))
                                        .max(Comparator.naturalOrder())
                                        .orElse(month.atDay(1));
                    chooseLatestOnCalendar = false;
                }
                if (reading != null
                        && YearMonth.from(reading.date())
                                .toString()
                                .equals(HubData.string(result, "month", ""))) {
                    HubData.Entry updated = data.archive.get(reading.date());
                    if (winter && reading.date().equals(data.today) && fortuneState == 2)
                        updated = data.todayRecord;
                    if (updated == null) dismissOverlay();
                    else reading = updated;
                }
            } else if (action.equals("read")) {
                HubData.Entry record =
                        data.record(result.has("record") ? result.get("record") : result);
                if (record != null) showReading(record);
            } else {
                boolean initial = !data.fortunesLoaded;
                data.fortunes(result);
                calendarLoading = true;
                fortuneState =
                        List.of("undrawn", "unread", "read", "archived")
                                .indexOf(HubData.string(result, "todayState", "undrawn"));
                if (fortuneState < 0) fortuneState = 0;
                if (initial) {
                    month = YearMonth.from(data.today);
                    selectedDate = data.today;
                }
                displayDate = data.featured == null ? null : data.featured.date();
            }
        }
    }

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

    public HubView() {
        updates.demo();
        rankings.demo();
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
        updates.deactivate();
        rankings.deactivate();
        memories.deactivate();
        profiles.clear();
        afterProfileClose = null;
        overlay = null;
        reading = null;
        focusId = null;
        closeRequest = false;
        openedAt = System.nanoTime();
        navAt = openedAt;
        lastFrame = openedAt;
        playWelcome = welcome && !reducedMotion;
        pageAt = openedAt + (playWelcome && !winter ? 3_060_000_000L : 0);
        hits.clear();
        transitions.clear();
        tilts.clear();
        extensionNavigation.clear();
        pressedId = null;
        for (HubMotion.Navigation state : navigation) {
            state.enter.snap(reducedMotion ? 1 : 0);
            state.hover.snap(0);
            state.press.snap(0);
            state.x.snap(0);
            state.y.snap(0);
        }
    }

    public void render(HubDraw draw, float mouseX, float mouseY, long nowNanos) {
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
        if (winter) winterWorkspace();
        else
            d.linearGradientRoundRect(
                    208,
                    0,
                    1052,
                    850,
                    0,
                    135,
                    dark ? new int[] {0xe0202a40, 0xdb262b43} : new int[] {0xadffffff, 0x9cf5f6ff},
                    new float[] {0, 1});
        d.captureGlassBackdrop(0, 0, 208, 850);
        sidebar();
        topbar();
        boolean welcomeActive = playWelcome && !reducedMotion && now - openedAt < 3_060_000_000L;
        if (!welcomeActive || winter) {
            if (page == 0) home();
            else if (page == 1) titles();
            else if (page == 2) fortunes();
            else if (page == 3) updates();
            else if (page == 4) leaderboards();
            else memories();
        }
        if (welcomeActive) {
            nextHits.clear();
            welcome();
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
        if (!welcomeActive && !profiles.isOpen() && (overlay != null || reading != null)) modal();
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

    private static final String WINTER_ART = "images/winter/";
    private String stationBio = "", stationArchiveId = "";
    private Map<String, Integer> stationSharedCounts = Map.of();

    public void setStationSharedCounts(Map<String, Integer> counts) {
        stationSharedCounts = Map.copyOf(counts);
    }

    private static final int STATION_PLAYERS_PER_PAGE = 4;
    private List<StationPlayer> stationPlayers = List.of();
    private int stationPage;

    public record StationPlayer(String id, String name) {}

    public void setStationPlayers(List<StationPlayer> players) {
        if (stationPlayers.equals(players)) return;
        stationPlayers = List.copyOf(players);
        stationPage =
                Math.min(
                        stationPage,
                        Math.max(0, (stationPlayers.size() - 1) / STATION_PLAYERS_PER_PAGE));
    }

    public boolean showingStation() {
        return winter && page == 0;
    }

    private void stock(String name, float x, float y, float w, float h, float alpha) {
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
        int[] destinations =
                updates.loaded() && !updates.enabled()
                        ? new int[] {0, 1, 2, 4}
                        : new int[] {0, 1, 2, 3, 4};
        for (int slot = 0; slot < destinations.length; slot++) {
            int i = destinations[slot];
            drawNavigationRow(
                    builtInNavigation[i],
                    slot,
                    page == i || page == 5 && i == 0,
                    navigation[i],
                    navAt);
        }
        drawExtraNavigation(destinations.length);
        boolean syncPlugin = HubModpackSync.available();
        if (syncPlugin && inside(17, 760, 157, 50))
            d.roundRect(17, 761, 157, 48, 3, withAlpha(p.ruling(), .2f));
        if (live() && serverIcon != null) d.serverIcon(serverIcon, 19, 769, 32);
        else image("server-logo", 19, 769, 32, 32, 1);
        String pack = live() ? (data.packName.isBlank() ? connectionName : data.packName) : "香草纪元";
        String version = live() ? data.packVersion : "2.7.1";
        String name = fit(pack, syncPlugin ? 96 : 108, 12, true);
        text(name, 57, 782, 12, p.ink(), true);
        float vx = 61 + d.textWidth(name, 12, true);
        if (vx + d.textWidth(version, 9, false) < (syncPlugin ? 154 : 168))
            text(version, vx, 782, 9, p.muted(), false);
        d.circle(59, 795, 2, syncPlugin ? syncSidebarDot(true) : p.pine());
        String subLine =
                syncPlugin
                        ? fit(syncSidebarStatus(), 92, 9.5f, false)
                        : (live() ? "当前存档" : "当前存档 · 演示");
        text(subLine, 65, 799, syncPlugin ? 9.5f : 10, p.muted(), false);
        if (syncPlugin) {
            icon("chevron-right", 158, 778, 12, p.muted());
            hit("modpack-sync", "打开整合包同步设置", 17, 760, 157, 50, () -> openOverlay("modpack-sync"));
        }
        if (inside(17, 812, 157, 32)) d.roundRect(17, 812, 157, 32, 8, withAlpha(p.sheet(), .52f));
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
        float y = 117.3f + 51 * slot + (slot >= 3 ? 37.3f : 0);
        beginAlpha(enter, 0, y, 186, 47);
        push(Matrix33.makeTranslate(-12 * (1 - enter), 0));
        boolean active = !current || page == 5;
        float hover = extensionHover(entry.id(), inside(13, y, 161, 47));
        float selection = stateTransition(entry.id() + "-station-selected", current ? 1 : 0, 200);
        if (hover > 0 && !current)
            d.roundRect(13, y + 2, 161, 43, 9, withAlpha(p.ruling(), .28f * hover));
        if (selection > 0) {
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
                            withAlpha(dark ? 0xff623b34 : 0xff704035, selection),
                            withAlpha(dark ? 0xff804c41 : 0xff884f40, selection)
                        },
                        new float[] {0, .09f});
                for (int dash = 4; dash < 173; dash += 7) {
                    int thread = withAlpha(0x70efd8ad, selection);
                    d.rect(dash, 3, 3, 1, thread);
                    d.rect(dash, 39, 3, 1, thread);
                }
            } finally {
                d.restore();
            }
        }
        int color = mix(dark ? p.ink() : 0xff304638, 0xfffff2d7, selection);
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
        boolean unread = entry == builtInNavigation[3] && updates.sectionUnread();
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
        String section = page == 3 || page == 4 ? "服务器" : "我的空间";
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
        String stem =
                switch (page) {
                    case 1 -> "titles";
                    case 2 -> "fortunes";
                    case 3 -> "updates";
                    case 4 -> "leaderboards";
                    default -> "home";
                };
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

    private void winterHome() {
        var p = d.winterPaper();
        group(0, 380);
        HubHead.draw(d, 227, 119, 39, -23);
        tracked(
                fitTracked(playerName, 680, 31, -.6f, 700, false),
                290,
                129,
                31,
                -.6f,
                p.ink(),
                700,
                false);
        HubData.Title worn = data.title(equipped);
        String label = worn == null ? "暂未佩戴称号" : fit(worn.name(), 325, 14, true);
        float nameWidth;
        if (worn != null && live()) {
            nameWidth = titleLabel(worn, 290, 157, 14, 0, false, false, 325);
        } else {
            text(label, 290, 157, 14, p.pine(), true);
            nameWidth = d.textWidth(label, 14, true);
        }
        float joinX = 304 + nameWidth;
        if (worn != null && privacyKnown() && !visible) {
            icon("eye-off", joinX, 145, 13, p.muted());
            text("展示已隐藏", joinX + 18, 156, 11, p.muted(), false);
            joinX += 29 + d.textWidth("展示已隐藏", 11, false);
        }
        d.line(joinX, 144, joinX, 158, 1, p.ruling());
        String joined = live() ? timestamp(data.firstJoinedAt, "yyyy/MM/dd") : "2026/10/17";
        if (!joined.isBlank() && (!live() || data.firstJoinedAt > 0))
            text(joined + " 加入", joinX + 10, 156, 11, p.muted(), false);
        String bio = live() ? stationBio : "喜欢沿着河岸慢慢走，也喜欢把偶然的相遇收进旅途。";
        if (!bio.isBlank()) text(fit(bio, 710, 12, false), 290, 181, 12, p.muted(), false);
        button(
                "profile",
                "公开资料",
                1042,
                123,
                89,
                33,
                true,
                true,
                () -> openPublicProfile(selfPlayerId()),
                "book-user");
        button(
                "memories",
                "旅程档案",
                1141,
                123,
                89,
                33,
                false,
                true,
                () -> openMemories(selfPlayerId(), ""),
                "map");
        d.line(216, 197, 1230, 197, 1, p.ruling());
        ungroup();
        group(0, 380);
        d.save();
        d.clipRoundRect(216, 219, 600, 354, 15);
        stock("panorama", 217, 220, 598, 352, 1);
        d.restore();
        d.strokeRoundRect(216.5f, 219.5f, 599, 353, 15, 1, p.ruling());
        icon(dark ? "moon" : "sun", 242, 409, 15, 0xfffffaf0);
        text(
                live() ? data.today.format(STATION_DATE) : "11 月 3 日，星期二",
                264,
                421,
                12,
                0xfffffaf0,
                true);
        String day =
                live() ? (journeyDays() > 0 ? "旅程第 " + journeyDays() + " 天" : "旅程记录") : "旅程第 18 天";
        tracked(day, 242, 465, 32, 1.2f, 0xfffffaf0, 600, true);
        text("累计在线", 235, 525, 11, p.muted(), false);
        text("登录次数", 444, 525, 11, p.muted(), false);
        text("上次登录", 585, 525, 11, p.muted(), false);
        d.line(431, 511, 431, 558, 1, p.ruling());
        d.line(572, 511, 572, 558, 1, p.ruling());
        boolean known = !live() || data.managerLoaded;
        long ms = live() ? data.onlineMillis(now) : 463320000L;
        String hrs = known ? Long.toString(ms / 3600000) : "—",
                min = known ? Long.toString(ms / 60000 % 60) : "—";
        float cursor = 235;
        text(hrs, cursor, 553, 21, p.ink(), true);
        cursor += d.textWidth(hrs, 21, true) + 3;
        text("小时", cursor, 553, 10, p.muted(), false);
        cursor += 25;
        text(min, cursor, 553, 21, p.ink(), true);
        cursor += d.textWidth(min, 21, true) + 3;
        text("分钟", cursor, 553, 10, p.muted(), false);
        String times = !live() ? "36" : known ? Long.toString(data.joinCount) : "—";
        text(times, 444, 553, 21, p.ink(), true);
        text("次", 448 + d.textWidth(times, 21, true), 553, 10, p.muted(), false);
        String date = live() ? timestamp(data.previousLoginAt, "yyyy/MM/dd") : "2026/11/02";
        text(date, 585, 552, 16, p.ink(), true);
        if (!live() || data.previousLoginAt > 0)
            text(
                    live() ? timestamp(data.previousLoginAt, "HH:mm:ss") : "20:26:14",
                    590 + d.textWidth(date, 16, true),
                    552,
                    11,
                    p.muted(),
                    false);
        stock("stationery", 838, 219, 392, 354, 1);
        icon("mail-open", 865, 250, 19, p.mailInk());
        text("今日签笺", 891, 266, 17, p.ink(), true);
        boolean canRead = !live() || ready("fortunes");
        HubData.Fortune f =
                live()
                        ? (data.todayRecord == null ? null : data.todayRecord.fortune())
                        : data.fortune(1);
        if (canRead && fortuneState >= 2 && f != null) {
            text(f.grade() + " · 第" + f.id() + "签", 865, 323, 12, p.mailInk(), true);
            tracked(
                    fitTracked(f.title(), 211, 27, 1.4f, 600, true),
                    865,
                    371,
                    27,
                    1.4f,
                    p.ink(),
                    600,
                    true);
            for (int i = 0; i < Math.min(2, f.poem().size()); i++)
                tracked(
                        fitTracked(f.poem().get(i), 210, 14, 1, 400, true),
                        865,
                        410 + i * 26,
                        14,
                        1,
                        p.muted(),
                        400,
                        true);
        } else {
            if (canRead) text("今日机缘", 865, 325, 12, p.muted(), false);
            else {
                String reason = moduleLabel("fortunes").replace('\n', ' ').strip();
                List<String> lines = trackedLines(reason, 192, 12, 0, 400, false);
                for (int i = 0; i < Math.min(2, lines.size()); i++) {
                    String line =
                            i == 1 && lines.size() > 2
                                    ? fit(lines.get(i) + "…", 192, 12, false)
                                    : lines.get(i);
                    text(line, 865, 315 + i * 18, 12, p.muted(), false);
                }
            }
            text(
                    canRead ? (fortuneState == 0 ? "有一签，等你开启" : "签已入手，静候解读") : "今日签笺暂不可用",
                    865,
                    374,
                    20,
                    p.ink(),
                    true);
            if (canRead)
                wrap(
                        fortuneState == 0 ? "拿起签筒，为今天留下一份期许。" : "带上未解的签，前往解签龛。",
                        865,
                        408,
                        194,
                        13,
                        22,
                        p.muted(),
                        false);
        }
        icon(canRead && fortuneState == 3 ? "check-check" : "info", 865, 520, 15, p.muted());
        text(canRead ? stateLabel() : "暂不可用", 886, 533, 11, p.muted(), false);
        if (canRead)
            button(
                    "today-read",
                    fortuneState >= 2 ? "查看签文" : fortuneState == 0 ? "如何抽签" : "如何解签",
                    1108,
                    509,
                    95,
                    37,
                    true,
                    canRead,
                    () -> {
                        if (fortuneState >= 2) openReading(todayEntry());
                        else openOverlay("fortune-help");
                    },
                    fortuneState >= 2 ? "mail-open" : "arrow-right");
        winterNews();
        ungroup();
        textRight("统计范围：当前存档", 1230, 827, 10, p.muted(), false);
    }

    private static final DateTimeFormatter STATION_DATE =
            DateTimeFormatter.ofPattern("M 月 d 日，EEEE", Locale.CHINA);

    private void winterNews() {
        var p = d.winterPaper();
        boolean showUpdates = !updates.loaded() || updates.enabled();
        float x = showUpdates ? 838 : 216, w = showUpdates ? 392 : 1014;
        if (showUpdates) {
            stock("bulletin", 216, 587, 600, 208, 1);
            icon("newspaper", 235, 606, 17, p.pine());
            text("最近更新", 261, 622, 17, p.ink(), true);
            textButton("station-all", "全部更新 ↗", 716, 599, 81, 28, () -> navigate(3), true);
            List<JsonObject> rows = updates.stationNotices();
            if (rows.isEmpty())
                text(
                        fit(updates.stationNoticesEmptyText().replace('\n', ' '), 562, 12, false),
                        235,
                        666,
                        12,
                        p.muted(),
                        false);
            for (int i = 0; i < Math.min(2, rows.size()); i++) {
                JsonObject row = rows.get(i);
                float y = 635 + 71 * i;
                if (i > 0) d.line(235, y, 797, y, 1, p.ruling());
                String title = fit(HubData.string(row, "title", ""), 425, 14, true);
                text(title, 235, y + 23, 14, p.ink(), true);
                String published = HubData.string(row, "publishedAt", "");
                if (!published.isBlank())
                    text(
                            stationShortDate(published),
                            245 + d.textWidth(title, 14, true),
                            y + 23,
                            11,
                            p.muted(),
                            false);
                text(
                        fit(HubData.string(row, "summary", ""), 532, 11, false),
                        235,
                        y + 41,
                        11,
                        p.muted(),
                        false);
                String version = HubData.string(row, "updateVersion", "");
                text(
                        HubData.string(row, "category", "")
                                + (version.isBlank() ? "" : " · v" + version),
                        235,
                        y + 59,
                        10,
                        p.mailInk(),
                        false);
                icon("chevron-right", 782, y + 26, 14, p.muted());
                hit(
                        "station-notice-" + i,
                        title,
                        235,
                        y,
                        562,
                        71,
                        () -> {
                            navigate(3);
                            updates.openStationNotice(row);
                        });
            }
        }
        stock("company", x, 587, w, 208, 1);
        icon("users", x + 19, 607, 17, p.pine());
        text("在线玩家", x + 46, 622, 17, p.ink(), true);
        String count = Integer.toString(stationPlayers.size());
        text(count, x + 122, 621, 11, p.pine(), true);
        int pages = 1 + Math.max(0, stationPlayers.size() - 1) / STATION_PLAYERS_PER_PAGE;
        if (pages > 1) {
            iconButton(
                    "station-prev",
                    "上一页在线玩家",
                    "chevron-left",
                    x + w - 107,
                    601,
                    24,
                    () -> stationPage--,
                    stationPage > 0);
            textCenter((stationPage + 1) + " / " + pages, x + w - 66, 617, 10, p.muted(), false);
            iconButton(
                    "station-next",
                    "下一页在线玩家",
                    "chevron-right",
                    x + w - 44,
                    601,
                    24,
                    () -> stationPage++,
                    stationPage + 1 < pages);
        }
        if (stationPlayers.isEmpty()) text("暂时没有其他玩家在线", x + 19, 666, 12, p.muted(), false);
        int first = stationPage * STATION_PLAYERS_PER_PAGE;
        for (int i = 0; i < STATION_PLAYERS_PER_PAGE && first + i < stationPlayers.size(); i++) {
            StationPlayer player = stationPlayers.get(first + i);
            float width = (w - 46) / 2,
                    px = x + 19 + (i % 2) * (width + 8),
                    py = 636 + (i / 2) * 72,
                    cardY = py + 9,
                    cardH = 52;
            if (inside(px, cardY, width, cardH))
                d.roundRect(px, cardY, width, cardH, 10, p.cover());
            d.save();
            d.clipRoundRect(px + 10, cardY + 10, 32, 32, 8);
            HubHead.drawPlayer2D(d, player.id(), false, px + 10, cardY + 10, 32);
            d.restore();
            d.circle(px + 41, cardY + 41, 4, p.sheet());
            d.circle(px + 41, cardY + 41, 2.5f, 0xff679476);
            text(fit(player.name(), width - 60, 12, true), px + 51, cardY + 23, 12, p.ink(), true);
            Integer shared = stationSharedCounts.get(player.id());
            text(
                    shared == null ? "当前存档" : ("共同存档 · " + shared + " 个"),
                    px + 51,
                    cardY + 40,
                    10,
                    p.muted(),
                    false);
            hit(
                    "station-peer-" + player.id(),
                    "查看 " + player.name() + " 的公开资料",
                    px,
                    cardY,
                    width,
                    cardH,
                    () -> openPublicProfile(player.id()));
        }
    }

    private String stationShortDate(String published) {
        try {
            return timestamp(Long.parseLong(published), "MM/dd");
        } catch (NumberFormatException ignored) {
            return published.length() >= 10 ? published.substring(5, 10).replace('-', '/') : "";
        }
    }

    private static final int[] SNOW_X = {9, 17, 25, 32, 14, 43, 50, 57, 62, 69, 77, 84, 91, 54};
    private static final int[] SNOW_Y = {22, 55, 14, 36, 79, 20, 55, 10, 35, 18, 62, 29, 52, 76};
    private static final float[] FOOT_X = {61, 63.5f, 65.5f, 68}, FOOT_Y = {2, 3.3f, 5.4f, 7};

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

    private void handbookStyleChoice(float x, float y) {
        var p = d.winterPaper();
        text("手册主题", x, y + 21, 14, winter ? p.ink() : INK, true);
        text(
                winter ? (dark ? "驿站夜灯" : "雪后清晨") : "经典旅人手册",
                x,
                y + 46,
                12,
                winter ? p.muted() : MUTED,
                false);
        float bx = x + 195;
        d.roundRect(bx, y + 6, 189, 40, 11, winter ? p.soft() : dark ? 0xff20283b : 0xfff5f6ff);
        float stylePosition = selectorPosition("style-position", winter);
        if (stylePosition != (winter ? 1 : 0)) d.keepThemeControlLive(bx, y + 6, 189, 40, 11);
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
            icon(choice ? "snowflake" : "book-open", iconX, y + 19, 14, c);
            text(choice ? "冬日驿站" : "经典", iconX + 19, y + 31, 12, c, selected);
            hit(
                    choice ? "style-winter" : "style-classic",
                    choice ? "冬日驿站" : "经典旅人手册",
                    left,
                    y + 10,
                    90,
                    32,
                    () -> setWinter(choice));
        }
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
        int[] destinations =
                updates.loaded() && !updates.enabled()
                        ? new int[] {0, 1, 2, 4}
                        : new int[] {0, 1, 2, 3, 4};
        for (int slot = 0; slot < destinations.length; slot++) {
            int i = destinations[slot];
            drawNavigationRow(
                    builtInNavigation[i],
                    slot,
                    page == i || page == 5 && i == 0,
                    navigation[i],
                    navAt + slot * 70_000_000L);
        }
        drawExtraNavigation(destinations.length);
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
        d.circle(72, 748, 2, syncPlugin ? syncSidebarDot(false) : 0xff6a9c8b);
        String classicSub =
                syncPlugin
                        ? fit(syncSidebarStatus(), 96, 10, false)
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

    private NavigationEntry builtInEntry(
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

    /** Hook for client extensions after the last visible built-in navigation row. */
    private void drawExtraNavigation(int firstSlot) {}

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
        boolean unread = entry == builtInNavigation[3] && updates.sectionUnread();
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

    private void heading(String title, String subtitle, String module, int count) {
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

    private String pageName() {
        return page == 0
                ? "个人主页"
                : page == 1
                        ? "我的称号"
                        : page == 2 ? "我的签笺" : page == 3 ? "更新日志" : page == 4 ? "玩家排行" : "旅程档案";
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

    private void home() {
        if (winter) {
            boolean previous = d.literalColors(true);
            try {
                winterHome();
            } finally {
                d.literalColors(previous);
            }
            return;
        }
        heading(
                "个人主页",
                live()
                        ? (journeyDays() > 0
                                ? "很高兴又见到你，今天是旅程的第 " + journeyDays() + " 天。"
                                : "很高兴又见到你。")
                        : "很高兴又见到你，今天是旅程的第 18 天。",
                null,
                -1);
        group(0, 340);
        icon("sun", 1062, 88, 18, 0xffbf8a37);
        text(
                live()
                        ? data.today.format(
                                DateTimeFormatter.ofPattern("M 月 d 日，EEEE", Locale.CHINA))
                        : "11 月 3 日，星期二",
                1089,
                103,
                12,
                0xff6e7990,
                false);
        ungroup();
        group(50, 440);
        d.boxShadow(
                LEFT,
                174,
                WIDTH,
                276,
                22,
                0,
                10,
                dark ? 28 : 30,
                dark ? -10 : 0,
                dark ? 0x700a102c : 0x288980c5);
        d.linearGradientRoundRect(
                LEFT,
                174,
                WIDTH,
                276,
                22,
                110,
                new int[] {0xffd9f0ff, 0xffe4e8ff, 0xffdbc9f9},
                new float[] {0, .45f, 1});
        d.radialGradientRoundRect(
                LEFT,
                174,
                WIDTH,
                276,
                22,
                LEFT + WIDTH * .78f,
                174 + 276 * .34f,
                WIDTH * .78f * 1.41421356f,
                276 * .66f * 1.41421356f,
                new int[] {0xffbcbbff, 0x00bcbbff},
                new float[] {0, .5f});
        d.captureGlassBackdrop(LEFT, 174, WIDTH, 276);
        d.glassSurface(LEFT, 174, WIDTH, 276, 22, GlassStyle.IDENTITY);
        d.insetBoxShadow(
                LEFT, 174, WIDTH, 276, 22, 0, 1, dark ? 0 : 2, 0, dark ? 0x1ae8efff : 0xffffffff);
        d.insetBoxShadow(
                LEFT,
                174,
                WIDTH,
                276,
                22,
                0,
                dark ? -1 : 0,
                dark ? 0 : 5,
                0,
                dark ? 0x0acbd3ff : 0x298c9ac3);
        if (!dark) d.boxShadow(LEFT, 174, WIDTH, 276, 22, 0, 8, 22, 0, 0x136c64aa);
        d.save();
        d.clipRoundRect(LEFT, 174, WIDTH, 276, 22);
        d.rect(LEFT, 366, WIDTH, 84, a(0x9effffff));
        d.line(LEFT, 366, 1232, 366, 1, a(0xc7ffffff));
        d.restore();
        if (dark) {
            boolean previousColors = d.literalColors(true);
            d.strokeRoundRect(LEFT + .5f, 174.5f, WIDTH - 1, 275, 22, 1, a(0x26c3cef6));
            d.line(LEFT + 22, 174.5f, 1232 - 22, 174.5f, 1, a(0x3dd3e4ff));
            d.line(LEFT + 22, 449.5f, 1232 - 22, 449.5f, 1, a(0x2ba3a4d3));
            d.literalColors(previousColors);
        } else d.strokeRoundRect(LEFT, 174, WIDTH, 276, 22, 1, a(0xf2ffffff));
        text("你好", 263, 211, 12, 0xff586987, false);
        String name = fitTracked(playerName, 390, 35, -1, 700, false);
        tracked(name, 263, 255, 35, -1, 0xff253657, 700, false);
        float nameWidth = trackedWidth(name, 35, -1, 700, false);
        pill(live() ? "我的角色" : "本地角色", 277 + nameWidth, 232, 0xbceffff2, 0xff25816a, true);
        HubData.Title worn = data.title(equipped);
        if (worn == null)
            text(
                    live() && !ready("titles")
                            ? "称号资料未连接"
                            : live() && !catalogueComplete ? "正在读取称号…" : "暂未佩戴称号",
                    263,
                    287,
                    14,
                    MUTED,
                    true);
        else titleLabel(worn, 263, 287, 14, 1.4f, false, false);
        if (privacyKnown() && !visible && worn != null) {
            icon("eye-off", live() ? 750 : 371, 276, 13, MUTED);
            text("展示已隐藏", live() ? 769 : 390, 287, 10, MUTED, false);
        }
        button(
                "profile",
                "公开资料",
                263,
                315,
                106,
                31,
                false,
                true,
                () -> openPublicProfile(selfPlayerId()),
                "arrow-up-right");
        button(
                "memories",
                "旅程档案",
                379,
                315,
                106,
                31,
                false,
                true,
                () -> openMemories(selfPlayerId(), ""),
                "bookmark");
        d.save();
        d.translate(1080, 268);
        d.canvas().rotate(-29);
        d.strokeRoundRect(-120, -57, 240, 114, 57, 2, a(0x70ffffff));
        d.restore();
        push(Matrix33.makeRotate(-5, 1082, 267));
        HubMotion.Tilt medallion =
                beginTilt("medallion", 987, 187, 190, 160, 10, 1.04f, true, .16f, 26);
        glass(987, 187, 190, 160, 26, 0x66ffffff, 0x24e7e5ff);
        HubHead.draw(d, 1055, 214, 54, -23, -2);
        String journeyLabel =
                live()
                        ? (journeyDays() > 0 ? "旅程第 " + journeyDays() + " 天" : "今天也有新旅程")
                        : "旅程第 18 天";
        float journeyWidth = d.textWidthTracked(journeyLabel, 10, 400, 1, false) + 36,
                journeyX = 1082 - journeyWidth / 2;
        boolean badgeColors = d.literalColors(true);
        d.roundRect(journeyX, 309, journeyWidth, 23, 12, a(dark ? 0x14b3a9d5 : 0x6bffffff));
        d.strokeRoundRect(
                journeyX + .5f,
                309.5f,
                journeyWidth - 1,
                22,
                12,
                1,
                a(dark ? 0x24dacfff : 0xaaffffff));
        int journeyColor = dark ? 0xffd0c7ed : 0xff5d5491;
        icon("sparkles", journeyX + 9, 314, 13, journeyColor);
        tracked(journeyLabel, journeyX + 27, 324.5f, 10, 1, journeyColor, 400, false);
        d.literalColors(badgeColors);
        endTilt(medallion, 987, 187, 190, 160, 26, 10, .75f);
        pop();
        text("✦", 1190, 222, 27, 0xffffffff, false);
        text("✧", 967, 321, 21, 0xffffffff, false);
        onlineStat(263, 391);
        d.line(566, 388, 566, 430, 1, a(0x4ab7c1e0));
        stat(
                590,
                391,
                "log-in",
                live() ? "当前存档 · 登录次数" : "登录次数 · 演示",
                live() ? (data.managerLoaded ? Long.toString(data.joinCount) : "—") : "36",
                "次",
                0xffaa75c6,
                0xfff1e9ff);
        d.line(887, 388, 887, 430, 1, a(0x4ab7c1e0));
        glass(910, 392, 33, 33, 11, 0xfff8f0df, 0xfff8f0df);
        icon("calendar-days", 918, 400, 17, 0xffb29047);
        text(live() ? "上次登录" : "上次登录 · 演示", 955, 400, 11, 0xff72809c, false);
        text(
                live() ? timestamp(data.previousLoginAt, "yyyy/MM/dd") : "2026/11/02",
                955,
                424,
                16,
                0xff344566,
                true);
        text(
                live()
                        ? (data.previousLoginAt <= 0
                                ? ""
                                : timestamp(data.previousLoginAt, "HH:mm:ss"))
                        : "20:26:14",
                1060,
                424,
                12,
                0xff71809b,
                false);
        ungroup();
        group(110, 440);
        homeToday();
        homeKeepsakes();
        ungroup();
        group(170, 340);
        icon("leaf", LEFT, 811, 13, 0xff8a93a8);
        text("每一份收藏，都记得来时的路。", 256, 823, 11, 0xff8a93a8, false);
        textRight(
                live()
                        ? (ready("manager") ? "当前存档的个人资料" : moduleLabel("manager"))
                        : "样例统计与收藏 · F8 预览选项",
                1232,
                823,
                10,
                0xff8a93a8,
                false);
        ungroup();
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
        d.roundRect(x, y, 33, 33, 11, a(background));
        icon(name, x + 8, y + 8, 17, color);
        text(label, x + 45, y + 9, 11, 0xff72809c, false);
        text(main, x + 45, y + 34, 21, 0xff344566, true);
        text(unit, x + 49 + d.textWidth(main, 21, 600), y + 33, 10, 0xff78869d, false);
    }

    private void onlineStat(float x, float y) {
        d.roundRect(x, y, 33, 33, 11, a(0xffe8eeff));
        icon("clock", x + 8, y + 8, 17, 0xff6b7dd6);
        text(live() ? "当前存档 · 累计在线" : "累计在线 · 演示", x + 45, y + 9, 11, 0xff72809c, false);
        boolean known = !live() || data.managerLoaded;
        long millis = live() ? data.onlineMillis(now) : ((128L * 60 + 42) * 60_000);
        String hours = known ? Long.toString(millis / 3_600_000) : "—",
                minutes = known ? Long.toString(millis / 60_000 % 60) : "—";
        float cursor = x + 45;
        text(hours, cursor, y + 34, 21, 0xff344566, true);
        cursor += d.textWidth(hours, 21, 600) + 4;
        text("小时", cursor, y + 33, 10, 0xff78869d, false);
        cursor += d.textWidth("小时", 10, 400) + 4;
        text(minutes, cursor, y + 34, 21, 0xff344566, true);
        cursor += d.textWidth(minutes, 21, 600) + 4;
        text("分钟", cursor, y + 33, 10, 0xff78869d, false);
    }

    private void homeToday() {
        shadow(LEFT, 470, 515, 310, 22, 0x104b5d85);
        d.linearGradientRoundRect(
                LEFT,
                470,
                515,
                310,
                22,
                120,
                new int[] {0xfffffefa, 0xfffff9ef},
                new float[] {0, 1});
        d.radialGradientRoundRect(
                LEFT,
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
        d.strokeRoundRect(LEFT + .5f, 470.5f, 514, 309, 22, 1, a(0xe9ffffff));
        d.line(LEFT + 22, 471, 729, 471, 1, a(0xd9ffffff));
        icon("leaf", 259, 493, 19, 0xffbc9345);
        text("今日签笺", 287, 510, 16, INK, true);
        textRight("每日一签", 728, 508, 10, MUTED, false);
        HubData.Fortune f =
                live()
                        ? (data.todayRecord == null ? null : data.todayRecord.fortune())
                        : data.fortune(1);
        if (live() && !ready("fortunes")) {
            text("今日签笺暂不可用", 259, 593, 23, 0xff4e4f42, true);
            wrap(moduleLabel("fortunes"), 259, 630, 280, 12, 23, 0xff928675, false);
        } else if (fortuneState >= 2 && f != null) {
            text(
                    f.grade() + "  ·  第" + f.id() + "签",
                    259,
                    551,
                    11,
                    HubData.gradeColor(f.grade()),
                    false);
            tracked(f.title(), 259, 597, 29, 2, 0xff4e4f42, 600, true);
            if (!f.poem().isEmpty())
                tracked(f.poem().get(0), 259, 630, 13, 1.3f, 0xff828069, 400, true);
            if (f.poem().size() > 1)
                tracked(f.poem().get(1), 259, 655, 13, 1.3f, 0xff828069, 400, true);
        } else {
            text("今日机缘", 259, 551, 11, 0xffae8132, false);
            text(fortuneState == 0 ? "有一签，等你开启" : "签已入手，静候解读", 259, 593, 24, 0xff4e4f42, true);
            wrap(
                    fortuneState == 0 ? "拿起签筒，为今天留下一份期许。" : "带上未解的签，前往解签龛。",
                    259,
                    629,
                    260,
                    13,
                    23,
                    0xff928675,
                    false);
        }
        d.circle(662, 606, 61, a(0x30ebcf87));
        float barrel = transition("fortune-barrel-hover", inside(LEFT, 470, 515, 310) ? 1 : 0, 350);
        if (reducedMotion) barrel = 0;
        push(HubDraw.cssPerspective(661, 611, 800, 0, 0, -8 + 5 * barrel, 1, 0, -4 * barrel));
        image("fortune-barrel", 598, 541, 126, 140, 1);
        pop();
        text("✦", 716, 556, 12, 0xffd5ae61, false);
        text("✧", 586, 675, 16, 0xffd5ae61, false);
        d.line(259, 706, 728, 706, 1, a(0x33cabc9b));
        icon(fortuneState == 3 ? "check-check" : "info", 259, 731, 15, 0xff7f8e81);
        text(stateLabel(), 281, 743, 10, 0xff7f8e81, false);
        goldButton(
                "today-read",
                fortuneState >= 2 ? "查看签文  →" : fortuneState == 0 ? "如何抽签  →" : "如何解签  →",
                608,
                721,
                120,
                37,
                () -> {
                    if (live() && !ready("fortunes")) {
                        notify(moduleLabel("fortunes"));
                        return;
                    }
                    if (fortuneState >= 2) openReading(todayEntry());
                    else openOverlay("fortune-help");
                });
    }

    private void homeKeepsakes() {
        glass(771, 470, 461, 310, 22, 0xecffffff, 0xabffffff);
        icon("bookmark", 793, 493, 18, 0xff8c74c4);
        text("最近收藏", 820, 510, 16, INK, true);
        textRight("旅途的印记", 1210, 508, 10, MUTED, false);
        List<HubData.Keepsake> recent =
                data.recentKeepsakes(live() ? data.recentArchive : archive());
        for (int i = 0; i < recent.size(); i++) {
            HubData.Keepsake item = recent.get(i);
            float y = 537 + i * 64;
            if (item.title() != null) {
                HubData.Title t = item.title();
                keepsake(
                        y,
                        fit(t.name(), 280, 13, false),
                        "获得称号 · " + titleDate(t),
                        t.icon(),
                        t.light(),
                        t.color(),
                        null,
                        () -> {
                            selectedTitle = t.id();
                            navigate(1);
                        },
                        t);
            } else {
                HubData.Entry entry = item.entry();
                keepsake(
                        y,
                        entry.fortune().title(),
                        "收藏签笺 · " + shortDate(entry.date()) + " " + entry.time(),
                        "fortune-slip",
                        0xfff8edda,
                        HubData.gradeColor(entry.fortune().grade()),
                        entry.fortune().grade(),
                        () -> openReading(entry));
            }
        }
        if (recent.isEmpty()) {
            boolean loading = live() && (!data.titlesLoaded || !data.fortunesLoaded);
            String empty = loading ? "正在读取收藏…" : "还没有留下收藏";
            if (live() && (!ready("titles") || !ready("fortunes"))) empty = "收藏资料尚未就绪";
            textCenter(empty, 1001, 619, 14, MUTED, false);
        }
        d.line(793, 731, 1210, 731, 1, a(0xffe4e5f1));
        text(
                live() && !catalogueComplete ? "—" : Integer.toString(titleCount()),
                793,
                760,
                17,
                0xff5c5790,
                true);
        text("枚称号", 810, 760, 10, 0xff80899f, false);
        d.line(866, 746, 866, 762, 1, a(0xffd9dcec));
        text(
                live() && !data.fortunesLoaded ? "—" : Integer.toString(archiveCount()),
                884,
                760,
                17,
                0xff5c5790,
                true);
        text("张签笺", 911, 760, 10, 0xff80899f, false);
        iconButton(
                "archive-link", "打开签笺月历", "arrow-up-right", 1177, 740, 30, () -> navigate(2), true);
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
        float hover = transition("keepsake-hover-" + y, inside(793, y - 1, 417, 60) ? 1 : 0, 140);
        if (hover > 0) d.roundRect(793, y - 1, 417, 60, 0, withAlpha(0x80f5f2ff, hover));
        float iconY = y + 6.5f;
        boolean previousColors = d.literalColors(true);
        d.boxShadow(793, iconY, 36, 36, 12, 0, 3, 8, 0, a(dark ? 0x220b1122 : 0x187e708b));
        if (art.equals("fortune-slip"))
            d.roundRect(793, iconY, 36, 36, 12, a(dark ? 0x24bd9c57 : 0xfff8edda));
        else
            d.linearGradientRoundRect(
                    793,
                    iconY,
                    36,
                    36,
                    12,
                    135,
                    dark
                            ? new int[] {withAlpha(color, .21f), withAlpha(color, .12f)}
                            : new int[] {bg, lighten(bg, .1f)},
                    new float[] {0, 1});
        d.insetBoxShadow(793, iconY, 36, 36, 12, 1, 1, 1, 0, a(dark ? 0x10eee3c7 : 0xffffffff));
        d.strokeRoundRect(793.5f, iconY + .5f, 35, 35, 12, 1, a(dark ? 0x21d9c498 : 0xffffffff));
        if (art.equals("fortune-slip")) {
            var artwork = d.imageResource("textures/ui/fortune-slip.png");
            float scale = 32f / Math.max(artwork.getWidth(), artwork.getHeight());
            float imageWidth = artwork.getWidth() * scale,
                    imageHeight = artwork.getHeight() * scale;
            image(
                    art,
                    793 + (36 - imageWidth) / 2,
                    iconY + (36 - imageHeight) / 2,
                    imageWidth,
                    imageHeight,
                    1);
        } else icon(art, 800, iconY + 7, 22, dark ? mix(color, 0xffe8eeff, .35f) : color);
        d.literalColors(previousColors);
        if (style == null) text(title, 841, y + 19, 13, 0xff4c5570, false);
        else titleLabel(style, 841, y + 19, 13, 0, false, false, 340);
        if (grade != null) {
            float x = 848 + d.textWidth(title, 13, false);
            d.strokeRoundRect(
                    x, y + 6, d.textWidth(grade, 9, false) + 10, 17, 4, .7f, a(0xffeadfc7));
            text(grade, x + 5, y + 18, 9, color, false);
        }
        text(subtitle, 841, y + 40, 10, 0xff8490a5, false);
        icon("chevron-right", 1195, y + 19, 16, 0xffa5a9c0);
        if (y < 665) d.line(793, y + 59, 1210, y + 59, 1, a(0x57e5e7f3));
        hit("keepsake-" + y, title, 793, y - 1, 417, 60, click);
    }

    private List<HubData.Title> titleList() {
        List<HubData.Title> list =
                new ArrayList<>(
                        data.titles.stream()
                                .filter(
                                        t ->
                                                (filter == 1
                                                                || (filter == 0
                                                                        ? t.owned()
                                                                        : !t.owned()))
                                                        && (t.name() + t.category())
                                                                .contains(query.strip()))
                                .toList());
        if (sort == 0)
            list.sort(
                    live()
                            ? Comparator.comparingLong(HubData.Title::acquiredAt).reversed()
                            : Comparator.comparing(HubData.Title::date).reversed());
        else {
            Collator collator = Collator.getInstance(Locale.CHINA);
            list.sort((a, b) -> collator.compare(a.name(), b.name()));
        }
        return list;
    }

    // CSS layout is evaluated on the same 1260 x 850 logical sheet as the shell.
    private static final float COLLECTION_LEFT = 215,
            COLLECTION_WIDTH = 1016,
            COLLECTION_TOP = 123.3f,
            ALBUM_SILL = 332.4f,
            ALBUM_SPREAD = 384.4f,
            ALBUM_END = 777;
    private static final float ALBUM_SPLIT = COLLECTION_LEFT + COLLECTION_WIDTH * 1.46f / 2.46f;
    private String albumListKey, lastAlbumTitle;
    private long trayAt, inkAt, albumStatusAt;
    private String albumStatus;
    private int trayPage, trayDirection;
    private LocalDate unfoldedDate;
    private long unfoldedAt;
    private float readingScroll, readingOverflow, albumRecordScroll, albumRecordOverflow;

    private int winterTone(int day, int night) {
        return dark ? night : day;
    }

    private void collectionHeading(String title, String count) {
        var paper = d.winterPaper();
        tracked(title, COLLECTION_LEFT + 1, 104, 25, 1.5f, paper.ink(), 700, true);
        String prefix = "已收藏  ";
        int end = count.indexOf(' ', prefix.length());
        if (!count.startsWith(prefix) || end < prefix.length()) {
            textRight(count, COLLECTION_LEFT + COLLECTION_WIDTH - 1, 100, 12, paper.muted(), false);
            return;
        }
        String number = count.substring(prefix.length(), end), suffix = count.substring(end);
        float right = COLLECTION_LEFT + COLLECTION_WIDTH - 1;
        textRight(suffix, right, 102, 12, paper.muted(), false);
        right -= d.textWidth(suffix, 12, false);
        textRight(number, right, 102, 16, paper.ink(), true);
        textRight("已收藏 ", right - d.textWidth(number, 16, true), 102, 12, paper.muted(), false);
    }

    private void plainControl(
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

    private void winterAlbum() {
        group(0, 240);
        boolean previous = d.literalColors(true);
        collectionHeading(
                "我的称号",
                live() && (!ready("titles") || !catalogueComplete)
                        ? moduleLabel("titles")
                        : "已收藏  " + titleCount() + " / " + data.titles.size());
        if (live() && !ready("titles")) {
            unavailable("titles");
        } else {
            List<HubData.Title> list = titleList();
            String key = filter + "\n" + sort + "\n" + query.strip();
            boolean changed = albumListKey != null && !albumListKey.equals(key);
            HubData.Title selected = changed ? null : data.title(selectedTitle);
            int index = list.indexOf(selected);
            if (index < 0 && !list.isEmpty()) {
                index = 0;
                selected = list.get(0);
                selectedTitle = selected.id();
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
                icon(emptyIcon, 720, ALBUM_SPREAD + 27, 27, muted);
                serifCenter(emptyTitle, 734, ALBUM_SPREAD + 88, 18, muted, true);
                if (catalogueComplete) {
                    textCenter(emptyHint, 734, ALBUM_SPREAD + 117, 12, muted, false);
                    if (searching || filter != 1)
                        button(
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
        ungroup();
    }

    private float albumEase(long delay, int ms) {
        return reducedMotion
                ? 1
                : HubMotion.easeOut(progress(now - inkAt - delay * 1_000_000L, ms * 1_000_000L));
    }

    private void albumInscription(HubData.Title t) {
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
        String monoName = playerName;
        if (d.textWidthMono(monoName, 18, .45f) > 345) {
            int end = monoName.length();
            while (end > 0 && d.textWidthMono(monoName.substring(0, end) + "…", 18, .45f) > 345)
                end--;
            monoName = monoName.substring(0, end) + "…";
        }
        d.textMono(monoName, x, mid - (t == null ? 2 : 30), 18, ink, .45f);
        boolean worn = t != null && Objects.equals(equipped, t.id());
        if (t != null) {
            float e = albumEase(0, 220);
            beginAlpha(.38f + .62f * e, x - 2, mid - 24, 425, 66);
            float baseline = mid + 18 + 4 * (1 - e);
            if (live()) {
                // The provider supplies the rich name independently of badge and card palette.
                titleLabel(t, x, baseline, 42.67f, 1.4f, false, false, 370, true, 700);
            } else {
                // The paper is painted with literal inks. The demo name inherits the handbook's
                // .title-text colors from glass-theme.css and dark-theme.css.
                boolean previous = d.literalColors(false);
                try {
                    paintDemoTitle(t, x, baseline, 42.67f, 1.4f, 700, true);
                } finally {
                    d.literalColors(previous);
                }
            }
            endAlpha();
        }
        float y = mid + (t == null ? 18 : 39);
        String statusKey = (t == null ? "" : t.id()) + "|" + worn + "|" + visible;
        if (!Objects.equals(statusKey, albumStatus)) {
            albumStatus = statusKey;
            albumStatusAt = now;
        }
        float press =
                reducedMotion ? 1 : HubMotion.easeOut(progress(now - albumStatusAt, 230_000_000L));
        beginAlpha(.6f + .4f * press, x - 3, y - 14, 19, 20);
        push(
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
        icon(worn ? visible ? "check" : "eye-off" : "tag", x, y - 10, 13, sub);
        pop();
        endAlpha();
        String status = t == null ? "称号收藏匣" : worn ? visible ? "正在佩戴" : "已佩戴 · 已隐藏" : "试戴预览";
        text(status, x + 19, y + 1, 11, sub, false);
        float line = x + 26 + d.textWidth(status, 11, false);
        d.linearGradientRoundRect(
                line,
                y - 3,
                29,
                1,
                0,
                90,
                new int[] {withAlpha(sub, .65f), sub & 0xffffff},
                new float[] {0, 1});
    }

    private void albumToolbar() {
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
                withAlpha(ink, .25f));
        d.line(
                COLLECTION_LEFT,
                ALBUM_SPREAD - 1,
                COLLECTION_LEFT + COLLECTION_WIDTH,
                ALBUM_SPREAD - 1,
                1,
                withAlpha(ink, .25f));
        d.roundRect(x, y, 178, 32, 11, field);
        d.strokeRoundRect(x + .5f, y + .5f, 177, 31, 11, 1, line);
        String[] filters = {"已拥有", "全部", "未获得"};
        float px = x + 3;
        for (int i = 0; i < filters.length; i++) {
            final int choice = i;
            float w = i == 1 ? 48 : 60;
            boolean active = filter == i;
            plainControl(
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
        icon("search", sx + 10, y + 8, 16, ink);
        float tx = sx + 33, available = query.isEmpty() ? 153 : 133;
        d.save();
        d.clipRoundRect(tx, y + 2, available, 29, 0);
        int caretIndex = Math.min(searchCaret, query.length());
        float caret = d.textWidth(query.substring(0, caretIndex), 12, false);
        float start = tx - ("search".equals(focusId) ? Math.max(0, caret - available + 2) : 0);
        if (searchSelectAll && "search".equals(focusId))
            d.rect(start, y + 6, d.textWidth(query, 12, false), 20, withAlpha(ink, .22f));
        text(
                query.isEmpty() ? "搜索称号" : query,
                start,
                y + 21,
                12,
                query.isEmpty() ? withAlpha(ink, .8f) : ink,
                false);
        if ("search".equals(focusId) && !searchSelectAll && (now / 500_000_000L) % 2 == 0)
            d.line(start + caret, y + 7, start + caret, y + 25, 1, ink);
        d.restore();
        hit(
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
            plainControl(
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
                        focusId = "search";
                    });
        float sortX = COLLECTION_LEFT + COLLECTION_WIDTH - 122;
        d.roundRect(sortX, y, 100, 32, 10, field);
        d.strokeRoundRect(sortX + .5f, y + .5f, 99, 31, 10, 1, line);
        if (inside(sortX, y, 100, 32))
            d.roundRect(sortX + 1, y + 1, 98, 30, 9, withAlpha(ink, .06f));
        text(sort == 0 ? "最近获得" : "名称排序", sortX + 11, y + 21, 12, ink, false);
        icon(titleSortOpen ? "chevron-up" : "chevron-down", sortX + 76, y + 10, 13, ink);
        hit(
                "sort",
                "称号排序",
                sortX,
                y,
                100,
                32,
                () -> {
                    titleSortOpen = !titleSortOpen;
                    focusId = titleSortOpen ? "sort-option-" + sort : "sort";
                });
    }

    private void albumSortPicker() {
        float x = COLLECTION_LEFT + COLLECTION_WIDTH - 150, y = ALBUM_SILL + 46;
        int fill = winterTone(0xfffbf6e8, 0xff343c34), ink = winterTone(0xff35463a, 0xfff3e8cc);
        d.boxShadow(x, y, 128, 74, 12, 0, 5, 14, 0, 0x26122b20);
        HubWinterSheet.panel(d, x, y, 128, 74, 12, fill, 1);
        for (int i = 0; i < 2; i++) {
            final int choice = i;
            boolean selected = i == sort;
            String label = i == 0 ? "最近获得" : "名称排序";
            int tint = selected ? winterTone(0xfffff9e4, 0xff233a30) : ink;
            float row = y + 4 + 33 * i;
            if (selected || inside(x + 4, row, 120, 33))
                d.roundRect(
                        x + 4,
                        row,
                        120,
                        33,
                        8,
                        selected ? winterTone(0xff365a49, 0xffc5d5b5) : withAlpha(ink, .10f));
            text(label, x + 14, row + 21, 12, tint, false);
            if (selected) icon("check", x + 101, row + 10, 13, tint);
            hit("sort-option-" + i, label, x + 4, row, 120, 33, () -> chooseTitleSort(choice));
        }
    }

    private void albumTray(List<HubData.Title> list, int pageIndex) {
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
        float e = reducedMotion ? 1 : HubMotion.easeOut(progress(now - trayAt, 200_000_000L));
        beginAlpha(
                (trayDirection == 0 ? .45f : .55f) + (trayDirection == 0 ? .55f : .45f) * e,
                COLLECTION_LEFT,
                top,
                w,
                3 * rh);
        push(Matrix33.makeTranslate(trayDirection * 8 * (1 - e), 0));
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
            boolean selected = t.id().equals(selectedTitle),
                    worn = Objects.equals(t.id(), equipped);
            if (selected)
                HubWinterSheet.stitchedCircle(d, cx, cy, mountSize * .47f + 2, gold, true);
            float hover = transition("album-hover-" + t.id(), inside(x, y, cw, rh) ? 1 : 0, 160);
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
            serif(
                    fitTracked(t.name(), Math.min(148, x + cw - caption - 2), 20, 0, 700, true),
                    caption,
                    ty,
                    20,
                    selected ? gold : 0xfffff8de,
                    true);
            text(fit(subtitle, x + cw - caption, 11, false), caption, ty + 21, 11, ink, false);
            if (worn || !t.owned()) {
                icon(worn ? "check" : "lock", caption, ty + 29, 11, gold);
                text(worn ? "已佩戴" : "未获得", caption + 15, ty + 39, 10, gold, false);
            }
            hit("album-pin-" + t.id(), t.name(), x, y, cw, rh, () -> selectedTitle = t.id());
        }
        pop();
        endAlpha();
        float y = ALBUM_END - 44;
        d.line(gridX, y, gridX + cw, y, 1, thread);
        d.line(gridX + cw + 12, y, gridX + 2 * cw + 12, y, 1, thread);
        text((first + 1) + "–" + end + " / " + list.size() + " 枚", gridX, y + 26, 12, ink, false);
        int pages = (list.size() + 5) / 6;
        if (pages > 1) {
            float right = ALBUM_SPLIT - pad;
            plainControl(
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
                    () -> selectedTitle = list.get(first - 6).id());
            textCenter(
                    "第 " + (pageIndex + 1) + " / " + pages + " 页",
                    right - 65,
                    y + 26,
                    12,
                    ink,
                    false);
            plainControl(
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
                    () -> selectedTitle = list.get(first + 6).id());
        }
    }

    private void albumRecord(HubData.Title t) {
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
                new int[] {mix(folio, rule, .68f), folio},
                new float[] {0, 1});
        d.line(ALBUM_SPLIT + 5, y, ALBUM_SPLIT + 5, ALBUM_END, 1, withAlpha(rule, .55f));
        HubWinterSheet.grain(
                d,
                ALBUM_SPLIT,
                y,
                COLLECTION_LEFT + COLLECTION_WIDTH - ALBUM_SPLIT,
                ALBUM_END - y,
                0,
                1);
        List<String> lines = trackedLines(t.description(), w - 13, 17, 0, 500, true);
        float descriptionHeight = Math.max(1, lines.size()) * 29.75f;
        String date = t.owned() ? titleDate(t) : "";
        boolean stub = !t.owned() || !date.isBlank();
        float copyW = w - (stub ? 66 : 0) - 24;
        List<String> cond = trackedLines(t.condition(), copyW, 13, 0, 400, false);
        float ticketH = Math.max(68.3f, 47 + cond.size() * 20f);
        float clipBottom = ALBUM_END - 90;
        albumRecordOverflow =
                Math.max(0, y + 104 + descriptionHeight + 16 + ticketH + 5 - clipBottom);
        albumRecordScroll = clamp(albumRecordScroll, 0, albumRecordOverflow);
        d.save();
        d.clipRoundRect(
                ALBUM_SPLIT,
                y,
                COLLECTION_LEFT + COLLECTION_WIDTH - ALBUM_SPLIT,
                clipBottom - y,
                0);
        push(Matrix33.makeTranslate(0, -albumRecordScroll));
        float e = albumEase(0, 220);
        beginAlpha(.38f + .62f * e, x - 2, y, w + 4, 110 + descriptionHeight);
        push(Matrix33.makeTranslate(0, 4 * (1 - e)));
        icon("bookmark-check", x, y + 30, 14, sub);
        text(
                fit(t.category() + " · " + t.rarity(), w - 92, 11, false),
                x + 19,
                y + 42,
                11,
                sub,
                false);
        serif(fitTracked(t.name(), w - 66, 30, 1.6f, 700, true), x, y + 79, 30, ink, true);
        float hitTop = Math.max(y + 45, y + albumRecordScroll);
        if (hitTop < y + 85)
            hit(
                    "full-title",
                    "查看称号全文",
                    x,
                    hitTop,
                    w - 65,
                    y + 85 - hitTop,
                    () -> openOverlay("title-name"));
        d.rect(x, y + 104, 2, descriptionHeight, mix(folio, d.winterPaper().mail(), .56f));
        for (int i = 0; i < lines.size(); i++)
            serif(lines.get(i), x + 13, y + 122 + i * 29.75f, 17, ink, false);
        pop();
        endAlpha();
        float phase = reducedMotion ? 1 : progress(now - inkAt, 480_000_000L);
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
        beginAlpha(.38f + .62f * e, x - 1, ticketY - 1, w + 2, ticketH + 6);
        push(Matrix33.makeTranslate(0, 4 * (1 - e)));
        d.linearGradientRoundRect(
                x,
                ticketY,
                w,
                ticketH,
                6,
                100,
                new int[] {mix(folio, rule, .23f), mix(folio, rule, .12f)},
                new float[] {0, 1});
        d.line(x + 3, ticketY, x + w - 3, ticketY, 1, withAlpha(rule, .72f));
        d.line(x + 3, ticketY + ticketH, x + w - 3, ticketY + ticketH, 1, withAlpha(rule, .72f));
        text(t.owned() ? "获得方式" : "解锁条件", x + 12, ticketY + 21, 11, sub, true);
        for (int i = 0; i < cond.size(); i++)
            text(cond.get(i), x + 12, ticketY + 44 + i * 20, 13, ink, false);
        if (stub) {
            float sx = x + w - 66, midY = ticketY + ticketH / 2;
            for (float dy = 0; dy < ticketH; dy += 5)
                d.line(sx, ticketY + dy, sx, ticketY + Math.min(ticketH, dy + 3), 1, rule);
            d.circle(sx, ticketY, 3.5f, folio);
            d.circle(sx, ticketY + ticketH, 3.5f, folio);
            int red = d.winterPaper().mailInk();
            if (!t.owned()) icon("lock", sx + 25, midY - 17, 16, red);
            else if (date.matches("[0-9]{4}[-/][0-9]{2}[-/][0-9]{2}")) {
                textCenter(date.substring(0, 4), sx + 33, midY - 14, 10, red, false);
                serifCenter(
                        date.substring(5).replace('/', '.').replace('-', '.'),
                        sx + 33,
                        midY + 7,
                        19,
                        red,
                        true);
            } else textCenter(fit(date, 60, 10, false), sx + 33, midY - 2, 10, red, false);
            textCenter(
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
        pop();
        endAlpha();
        pop();
        d.restore();
        boolean worn = Objects.equals(t.id(), equipped),
                waiting = pendingModules.contains("titles");
        String label = waiting ? "等待服务器确认…" : !t.owned() ? "尚未获得" : worn ? "已佩戴 · 点击摘下" : "佩戴这个称号";
        float bw = worn || waiting ? 142.5f : 121;
        float etchW = Math.min(196, w + 12 - bw - 40 - (live() ? 31 : 0));
        float by = ALBUM_END - 16 - Math.max(34, etchW * 74 / 180) / 2 - 17;
        button(
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
        plainControl(
                "visibility",
                "展示给其他玩家",
                visible ? "eye" : "eye-off",
                "",
                x + bw + 3,
                by,
                34,
                34,
                sub,
                0,
                canChangeVisibility(),
                this::changeVisibility);
        float rest = x + bw + 41;
        if (live()) {
            plainControl(
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
                    () -> openOverlay("title-display"));
            rest += 31;
        }
        float ew = Math.min(196, x + w + 12 - rest), eh = ew * 74 / 180;
        e = albumEase(0, 220);
        beginAlpha(.38f + .62f * e, rest, by + 16 - eh / 2, ew, eh + 6);
        HubCollectionArt.etching(
                d,
                HubCollectionArt.styleForBadge(t.icon()),
                rest,
                by + 17 - eh / 2 + 4 * (1 - e),
                ew,
                eh,
                sub);
        endAlpha();
    }

    private void equipTitle(HubData.Title t) {
        if (live()) {
            JsonObject payload = mutation("titles");
            payload.addProperty("title", Objects.equals(equipped, t.id()) ? "" : t.id());
            request("titles", "equip", payload);
        } else {
            equipped = Objects.equals(equipped, t.id()) ? null : t.id();
            feedbackSuccess(equipped == null ? "已摘下称号 · 本地演示" : "已佩戴「" + t.name() + "」· 本地演示");
        }
    }

    private static final float COUNTER_BAR = 295,
            DESK_TOP = 331,
            DESK_END = 777.75f,
            COUNTER_SPLIT = COLLECTION_LEFT + COLLECTION_WIDTH * .58f;
    private static final float MONTH_X = COLLECTION_LEFT + 33.5f,
            MONTH_RIGHT = COUNTER_SPLIT - 34.55f,
            MONTH_GRID = 446.1f,
            MONTH_END = 764;
    private static final String[] WINTER_MONTHS = {
        "一月", "二月", "三月", "四月", "五月", "六月", "七月", "八月", "九月", "十月", "十一月", "十二月"
    };

    private void winterCounter() {
        group(0, 240);
        boolean previous = d.literalColors(true);
        collectionHeading(
                "我的签笺",
                live() && !ready("fortunes")
                        ? moduleLabel("fortunes")
                        : "已收藏  " + archiveCount() + " 张");
        if (live() && !ready("fortunes")) {
            unavailable("fortunes");
        } else {
            Map<LocalDate, HubData.Entry> records = archive();
            HubData.Entry featured = live() ? data.featured : records.get(displayDate);
            HubData.Entry selected = records.get(selectedDate);
            d.boxShadow(
                    COLLECTION_LEFT,
                    COLLECTION_TOP,
                    COLLECTION_WIDTH,
                    DESK_END - COLLECTION_TOP,
                    21,
                    0,
                    5,
                    10,
                    -7,
                    0x66171f16);
            d.save();
            d.clipRoundRect(
                    COLLECTION_LEFT,
                    COLLECTION_TOP,
                    COLLECTION_WIDTH,
                    DESK_END - COLLECTION_TOP,
                    21);
            HubCollectionArt.cover(
                    d,
                    false,
                    COLLECTION_LEFT,
                    COLLECTION_TOP,
                    COLLECTION_WIDTH,
                    COUNTER_BAR - COLLECTION_TOP);
            counterFeature(featured);
            counterBar();
            HubCollectionArt.desk(
                    d, COLLECTION_LEFT, DESK_TOP, COLLECTION_WIDTH, DESK_END - DESK_TOP);
            List<HubData.Entry> stored =
                    records.values().stream()
                            .filter(e -> YearMonth.from(e.date()).equals(month))
                            .sorted(Comparator.comparing(HubData.Entry::date).reversed())
                            .toList();
            counterMonth(stored, records);
            float sx = COUNTER_SPLIT + 22, sw = COLLECTION_LEFT + COLLECTION_WIDTH - 22 - sx;
            if (!Objects.equals(unfoldedDate, selectedDate)) {
                unfoldedDate = selectedDate;
                unfoldedAt = now;
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
                    pending = pendingModules.contains("fortunes");
            String displayLabel = pending ? "等待服务器确认…" : displayed ? "当前选择已展示" : "展示选中的签";
            float by = DESK_END - 40;
            int chalk = 0xfff2e9d5;
            boolean canDisplay = selected != null && !displayed && !pending;
            icon(
                    displayed ? "check" : "bookmark",
                    sx + 2,
                    by + 7,
                    14,
                    canDisplay ? chalk : withAlpha(chalk, .72f));
            text(
                    displayLabel,
                    sx + 21,
                    by + 19,
                    12,
                    canDisplay ? chalk : withAlpha(chalk, .72f),
                    false);
            if (canDisplay) {
                if (inside(sx, by, 111, 29))
                    d.line(
                            sx + 21,
                            by + 23,
                            sx + 21 + d.textWidth(displayLabel, 12, false),
                            by + 23,
                            1,
                            chalk);
                hit("display-selected", "展示选中的签", sx, by, 111, 29, () -> displaySelected(selected));
            }
            float rx = sx + sw - 103;
            beginAlpha(selected == null ? .72f : 1, rx, by - 1, 103, 34);
            d.roundRect(rx, by, 103, 31, 4, 0xffe8ddba);
            HubWinterSheet.grain(d, rx, by, 103, 31, 4, 1);
            serif("阅读解签", rx + 12, by + 21, 13, 0xff493d29, true);
            icon("arrow-right", rx + 75, by + 8, 15, 0xff493d29);
            if (selected != null) {
                if (inside(rx, by, 103, 31))
                    d.line(rx + 12, by + 24, rx + 65, by + 24, 1, 0xff493d29);
                hit("read-selected", "阅读解签", rx, by, 103, 31, () -> openReading(selected));
            }
            endAlpha();
            d.restore();
            HubWinterSheet.illustration(d, "fortune-tailpiece", 651, DESK_END + 9, 144, 40, 1);
        }
        d.literalColors(previous);
        ungroup();
    }

    private void counterFeature(HubData.Entry featured) {
        int ink = winterTone(0xff304534, 0xfff4ead0);
        float x = COLLECTION_LEFT + 39, mid = (COUNTER_BAR + COLLECTION_TOP) / 2;
        icon("bookmark", x, mid - 49, 14, ink);
        tracked("手册展示", x + 22, mid - 37, 12, .84f, ink, 450, false);
        if (featured == null) {
            serif("尚未选择展示签笺", x, mid + 12, 25, ink, true);
            text(
                    archiveCount() > 0 ? "在下方选中一张收藏，设为展示。" : "收藏签笺后，可在这里展示。",
                    x,
                    mid + 46,
                    12,
                    ink,
                    false);
        } else {
            String title = fitTracked(featured.fortune().title(), 421, 41, 2.25f, 700, true);
            float tw = trackedWidth(title, 41, 2.25f, 700, true);
            tracked(title, x, mid + 19, 41, 2.25f, ink, 700, true);
            icon("chevron-right", x + tw + 8, mid - 6, 18, withAlpha(ink, .65f));
            if (inside(x, mid - 24, tw + 30, 52)) d.line(x, mid + 24, x + tw, mid + 24, 1, ink);
            hit("read-feature", "阅读手册展示的签笺", x, mid - 24, tw + 30, 52, () -> openReading(featured));
            String grade = featured.fortune().grade();
            text(grade, x, mid + 46, 12, winterTone(0xff884633, 0xfff0ac88), true);
            float gx = x + d.textWidth(grade, 12, true) + 8;
            d.line(gx, mid + 34, gx, mid + 48, 1, winterTone(0xffa69e81, 0xff73816b));
            text(featured.date().format(DATE), gx + 8, mid + 46, 12, ink, false);
            if (live())
                plainControl(
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
                        !pendingModules.contains("fortunes"),
                        () -> {
                            JsonObject payload = mutation("fortunes");
                            payload.addProperty("date", "");
                            request("fortunes", "feature", payload);
                        });
        }
    }

    private void setCounterMode(boolean list) {
        if (history == list) return;
        history = list;
        historyScroll = 0;
        monthAt = now;
    }

    private void returnToCurrentMonth() {
        boolean changed = !month.equals(YearMonth.from(data.today));
        monthDirection = 1;
        month = YearMonth.from(data.today);
        selectedDate = data.today;
        chooseLatestOnCalendar = false;
        historyScroll = 0;
        if (changed) monthAt = now;
        if (changed || calendarInitialLoading()) calendarRequest();
    }

    private void counterBar() {
        int sill = winterTone(0xffe5dfcc, 0xff37463c),
                ink = winterTone(0xff38483c, 0xfff0e9d0),
                rule = winterTone(0xffa69e81, 0xff73816b);
        d.rect(COLLECTION_LEFT, COUNTER_BAR, COLLECTION_WIDTH, 36, sill);
        HubWinterSheet.grain(d, COLLECTION_LEFT, COUNTER_BAR, COLLECTION_WIDTH, 36, 0, 1);
        d.line(
                COLLECTION_LEFT,
                COUNTER_BAR,
                COLLECTION_LEFT + COLLECTION_WIDTH,
                COUNTER_BAR,
                1,
                rule);
        d.line(
                COLLECTION_LEFT,
                DESK_TOP - 1,
                COLLECTION_LEFT + COLLECTION_WIDTH,
                DESK_TOP - 1,
                1,
                rule);
        float y = COUNTER_BAR + 5;
        plainControl(
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
        plainControl(
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
        plainControl(
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
        plainControl(
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
        plainControl(
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
                month.isBefore(YearMonth.from(data.today)),
                () -> shiftMonth(1));
        trackedCenter(
                selectedDate.format(DATE) + (selectedDate.equals(data.today) ? "   今天" : ""),
                (COUNTER_SPLIT + COLLECTION_LEFT + COLLECTION_WIDTH) / 2,
                y + 18,
                12,
                .96f,
                ink,
                true,
                true);
    }

    private void counterMonth(List<HubData.Entry> stored, Map<LocalDate, HubData.Entry> records) {
        int ink = winterTone(0xff313f34, 0xfff2e7cc),
                sub = winterTone(0xff5d6355, 0xffceccb6),
                red = winterTone(0xff8b412f, 0xfff0aa89),
                rule = winterTone(0x587e8069, 0x4ad0c5a5);
        float w = MONTH_RIGHT - MONTH_X, top = DESK_TOP + 18, cy = (MONTH_GRID + top) / 2;
        d.strokeRoundRect(MONTH_X, cy - 27, 15, 52, 2, 1, red);
        String year = month.getYear() + "年";
        for (int i = 0; i < year.length(); i++)
            serifCenter(year.substring(i, i + 1), MONTH_X + 7.5f, cy - 16 + i * 9, 9, red, true);
        tracked(
                WINTER_MONTHS[month.getMonthValue() - 1],
                MONTH_X + 26,
                cy + 3,
                31,
                1.86f,
                ink,
                700,
                true);
        text(
                calendarInitialLoading() ? "正在读取本月收藏…" : "本月收签  " + stored.size() + " 张",
                MONTH_X + 26,
                cy + 25,
                11,
                sub,
                false);
        var wood = d.imageResource(WINTER_ART + "calendar-woodcut.png");
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
        float e = reducedMotion ? 1 : HubMotion.easeOut(progress(now - monthAt, 240_000_000L));
        d.save();
        d.clipRoundRect(MONTH_X, MONTH_GRID, w, MONTH_END - MONTH_GRID, 0);
        beginAlpha(.5f + .5f * e, MONTH_X, MONTH_GRID, w, MONTH_END - MONTH_GRID);
        push(Matrix33.makeTranslate(monthDirection * 8 * (1 - e), 0));
        if (calendarInitialLoading())
            textCenter("正在读取本月收藏…", MONTH_X + w / 2, (MONTH_END + MONTH_GRID) / 2, 13, sub, false);
        else if (history) counterList(stored, ink, sub, rule);
        else {
            float cellW = w / 7, startY = MONTH_GRID + 25.5f;
            int offset = month.atDay(1).getDayOfWeek().getValue() % 7,
                    rows = (offset + month.lengthOfMonth() + 6) / 7;
            float cellH = (MONTH_END - startY) / rows;
            for (int i = 0; i < 7; i++)
                serifCenter(
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
                if (!future && inside(x, y, cellW, cellH))
                    d.rect(x, y + 1, cellW, cellH - 1, withAlpha(ink, .08f));
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
                serifCenter(
                        Integer.toString(day),
                        cx,
                        ny + (numberH + fs * .69f) / 2,
                        fs,
                        selected ? 0xfffff3d8 : future ? withAlpha(ink, .63f) : ink,
                        true);
                if (date.equals(data.today))
                    serif("今", cx + badgeHalfW + 3.5f, ny + 8.5f, 9, red, true);
                if (entry != null) {
                    float labelSize = cellH <= 54 ? 10 : 11.5f;
                    String label =
                            fitTracked(entry.fortune().title(), cellW - 9, labelSize, 0, 550, true);
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
                    serif(label, cx - (lw + 5) / 2 + 5, baseline, labelSize, ink, false);
                }
                if (!future)
                    hit(
                            "day-" + date,
                            date + (entry == null ? " 未收藏" : " " + entry.fortune().title()),
                            x,
                            y,
                            cellW,
                            cellH,
                            () -> selectedDate = date);
            }
        }
        pop();
        endAlpha();
        d.restore();
    }

    private void counterList(List<HubData.Entry> entries, int ink, int sub, int rule) {
        float w = MONTH_RIGHT - MONTH_X, h = MONTH_END - MONTH_GRID;
        if (entries.isEmpty()) {
            float cx = (MONTH_X + MONTH_RIGHT) / 2, cy = (MONTH_GRID + MONTH_END) / 2;
            icon("inbox", cx - 13, cy - 42, 26, sub);
            serifCenter("这个月还没有签笺", cx, cy + 9, 18, ink, true);
            textCenter("已收藏的签会按抽签日期出现在这里。", cx, cy + 36, 12, sub, false);
            return;
        }
        float rowH = 42, overflow = Math.max(0, entries.size() * rowH - h);
        historyScroll = clamp(historyScroll, 0, overflow);
        for (int i = 0; i < entries.size(); i++) {
            float y = MONTH_GRID + i * rowH - historyScroll,
                    top = Math.max(MONTH_GRID, y),
                    bottom = Math.min(MONTH_END, y + rowH);
            if (top >= bottom) continue;
            var entry = entries.get(i);
            if (selectedDate.equals(entry.date()) || inside(MONTH_X, top, w, bottom - top))
                d.rect(MONTH_X, y, w, rowH, withAlpha(ink, .08f));
            d.line(MONTH_X, y + rowH - 1, MONTH_RIGHT, y + rowH - 1, 1, rule);
            text(
                    entry.date().format(DateTimeFormatter.ofPattern("MM / dd")),
                    MONTH_X + 7,
                    y + 26,
                    12,
                    sub,
                    false);
            serif(
                    fitTracked(entry.fortune().title(), w - 155, 16, 0, 700, true),
                    MONTH_X + 70,
                    y + 27,
                    16,
                    ink,
                    true);
            float gx = MONTH_RIGHT - 81;
            d.rect(gx, y + 14, 3, 14, HubData.gradeColor(entry.fortune().grade()));
            text(entry.fortune().grade(), gx + 8, y + 25, 11, sub, false);
            icon("chevron-right", MONTH_RIGHT - 21, y + 13, 15, sub);
            hit(
                    "history-" + entry.date(),
                    entry.date() + " " + entry.fortune().title(),
                    MONTH_X,
                    top,
                    w,
                    bottom - top,
                    () -> selectedDate = entry.date());
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

    // A featured slip and today's archived state are independently confirmed by the server,
    // including before the corresponding month has been fetched.
    private boolean confirmedArchived(HubData.Entry entry) {
        if (entry.date().equals(data.today) && fortuneState < 3) return false;
        if (archive().containsKey(entry.date())) return true;
        return live()
                && (data.featured != null && entry.date().equals(data.featured.date())
                        || fortuneState == 3 && entry.date().equals(data.today));
    }

    private void journalSlip(
            HubData.Entry entry, float x, float y, float w, float h, long start, boolean loading) {
        float sw = Math.min(300, Math.min(w, (h - 2) * 580f / 927)), sh = sw * 927 / 580;
        float cx = x + w / 2, cy = y + h / 2, lx = cx - sw / 2, ly = cy - sh / 2;
        float e =
                reducedMotion
                        ? 1
                        : HubMotion.bezier(progress(now - start, 320_000_000L), .2f, .68f, .3f, 1);
        beginAlpha(.65f + .35f * e, x, y - 3, w, h + 6);
        push(
                HubDraw.cssPerspective(
                        cx, ly + sh * .05f, 700, -5 * (1 - e), 0, 0, 1, 0, -2 * (1 - e)));
        HubCollectionArt.filtered(
                d, "journal-slip", lx, ly, sw, sh, dark ? .87f : 1, dark ? .84f : 1);
        int ink = 0xff3f372a;
        if (entry == null) {
            slipImprint(null, cx, cy - 80);
            icon("scroll-text", cx - 14, cy - 37, 28, ink);
            serifCenter(loading ? "正在读取签笺" : "这一天未留签", cx, cy + 25, 22, ink, true);
            List<String> lines =
                    trackedLines(
                            loading ? "等待服务器确认收藏记录。" : "在游戏中打开藏签匣，可将已解的签按日期归档。",
                            Math.min(190, sw * .73f),
                            12,
                            0,
                            400,
                            false);
            for (int i = 0; i < lines.size(); i++)
                textCenter(lines.get(i), cx, cy + 53 + i * 18.6f, 12, 0xff534932, false);
        } else {
            HubData.Fortune fortune = entry.fortune();
            float scriptH = sh * .56f;
            int maxPoem =
                    fortune.poem().stream()
                            .mapToInt(line -> line.codePointCount(0, line.length()))
                            .max()
                            .orElse(0);
            float fs = Math.min(clamp(h * .0465f, 12, 20), scriptH / Math.max(1, maxPoem) / 1.1f);
            int count = fortune.poem().size(),
                    titleLen = fortune.title().codePointCount(0, fortune.title().length());
            float ts = Math.min(clamp(h * .075f, 18, 34), scriptH / Math.max(1, titleLen) / 1.11f);
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
            String footer = fit(playerName, sw * .37f, 10, false) + " 所得  " + entry.time();
            float fy = top + actualH + 21,
                    fw = d.textWidth(footer, 10, false),
                    fx = cx - (fw + (archived ? 23 : 0)) / 2;
            text(footer, fx, fy, 10, 0xff514832, false);
            if (archived) {
                float stampX = fx + fw + 6, stampY = fy - 13;
                d.save();
                d.canvas().concat(Matrix33.makeRotate(-5, stampX + 8, stampY + 8));
                d.strokeRoundRect(stampX, stampY, 17, 17, 3, 1, 0xff924736);
                d.strokeRoundRect(stampX + 2, stampY + 2, 13, 13, 2, 1, 0xff924736);
                serifCenter("藏", stampX + 8.5f, stampY + 12.5f, 11, 0xff924736, true);
                d.restore();
            }
        }
        pop();
        endAlpha();
    }

    private void slipImprint(HubData.Fortune fortune, float cx, float y) {
        String grade = fortune == null ? "未收藏" : fortune.grade(),
                number = fortune == null ? "" : "第 " + fortune.id() + " 签";
        float gw = trackedWidth(grade, 12, 1.4f, 650, true) + 12,
                nw = d.textWidthSerif(number, 11, false);
        float x = cx - (gw + (number.isEmpty() ? 0 : 8 + nw)) / 2;
        d.roundRect(x, y, gw, 22, 3, 0xff994c38);
        tracked(grade, x + 6, y + 15.5f, 12, 1.4f, 0xfffff0d2, 650, true);
        if (!number.isEmpty()) serif(number, x + gw + 8, y + 15, 11, 0xff8b402f, false);
    }

    private void verticalText(
            String value, float cx, float top, float size, float spacing, int ink, boolean bold) {
        for (int i = 0; i < value.length(); ) {
            int cp = value.codePointAt(i);
            serifCenter(new String(Character.toChars(cp)), cx, top + size * .88f, size, ink, bold);
            i += Character.charCount(cp);
            top += size * spacing;
        }
    }

    private void winterFortuneDialog() {
        boolean isReading = reading != null;
        var paper = d.winterPaper();
        boolean previous = d.literalColors(true);
        opacity = interactiveAlpha = 1;
        offsetY = 0;
        nextHits.clear();
        float headH = isReading ? 88 : 76;
        modalW = isReading ? 720 : 440;
        List<String> lines =
                isReading
                        ? trackedLines(reading.fortune().interpretation(), 333, 16, 1, 400, true)
                        : List.of();
        float contentH =
                isReading ? 40 + Math.max(385, 191 + lines.size() * 31.2f) : 22 + 82 + 18 + 35 + 22;
        float maxH = Math.max(headH + 90, Math.min(790, viewportH - 40));
        modalH = Math.min(maxH, headH + contentH + 2);
        modalX = (1260 - modalW) / 2;
        modalY = (850 - modalH) / 2;
        float bodyH = modalH - headH - 2;
        readingOverflow = isReading ? Math.max(0, contentH - bodyH) : 0;
        readingScroll = clamp(readingScroll, 0, readingOverflow);
        float enter =
                reducedMotion
                        ? 1
                        : HubMotion.bezier(progress(now - modalAt, 220_000_000L), .2f, .8f, .2f, 1);
        float fade = reducedMotion ? 1 : HubMotion.ease(progress(now - modalAt, 180_000_000L));
        d.captureGlassBackdrop(viewportX, viewportY, viewportW, viewportH);
        beginAlpha(fade, viewportX, viewportY, viewportW, viewportH);
        d.blurBackdrop(viewportX, viewportY, viewportW, viewportH, 0, 5, 1);
        d.rect(viewportX, viewportY, viewportW, viewportH, 0x4a192c29);
        beginAlpha(enter, modalX - 24, modalY - 24, modalW + 48, modalH + 64);
        push(
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
        serif(
                isReading ? "签笺详情" : fortuneState == 0 ? "用签筒开启今日一签" : "前往解签龛",
                modalX + 25,
                modalY + (isReading ? 40 : 45),
                20,
                paper.ink(),
                true);
        if (isReading)
            text(
                    reading.date().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
                            + " · "
                            + playerName
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
                inside(closeX, closeY, 30, 30) ? paper.ruling() : paper.cover());
        icon("x", closeX + 6, closeY + 6, 18, paper.pine());
        hit("modal-close", "关闭弹窗", closeX, closeY, 30, 30, this::dismissOverlay);
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
            icon("leaf", rx, y + 16, 15, paper.pine());
            serif("解签", rx + 23, y + 31, 17, paper.pine(), true);
            for (int i = 0; i < lines.size(); i++) {
                float baseline = y + 68 + i * 31.2f;
                if (baseline >= bodyTop - 6 && baseline <= bodyTop + bodyH + 20)
                    tracked(lines.get(i), rx, baseline, 16, 1, paper.ink(), 400, true);
            }
            boolean archived = confirmedArchived(reading),
                    current = Objects.equals(displayDate, reading.date());
            float foot = y + contentH - 40 - 115.2f;
            d.line(rx, foot, rx + 333, foot, 1, paper.stitch());
            icon("bookmark", rx, foot + 15, 16, paper.muted());
            text(archived ? "已收藏于你的藏签匣" : "已解签，尚未收藏", rx + 24, foot + 28, 12, paper.ink(), false);
            text(
                    archived ? "签笺一经收藏，便成为旅途的一部分。" : "在游戏中打开藏签匣，即可收存这张签。",
                    rx + 24,
                    foot + 47,
                    11,
                    paper.muted(),
                    false);
            float by = foot + 73.2f;
            boolean waiting = pendingModules.contains("fortunes");
            String label =
                    waiting ? "等待服务器确认…" : !archived ? "收藏后可设为展示" : current ? "当前展示的签笺" : "设为手册展示";
            boolean enabled = archived && !waiting;
            if (by + 36 > bodyTop && by < bodyTop + bodyH) {
                d.roundRect(
                        rx,
                        by,
                        333,
                        36,
                        10,
                        enabled ? paper.pine() : withAlpha(paper.pine(), .60f));
                float tw = d.textWidth(label, 12, true), ix = rx + (333 - tw - 22) / 2;
                icon("sparkles", ix, by + 10, 16, paper.sheet());
                text(label, ix + 22, by + 23, 12, paper.sheet(), true);
                if (enabled) {
                    float clippedTop = Math.max(bodyTop, by);
                    hit(
                            "reading-display",
                            label,
                            rx,
                            clippedTop,
                            333,
                            Math.min(by + 36, bodyTop + bodyH) - clippedTop,
                            () -> {
                                if (current) notify("手册正在展示这张签笺");
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
            image(image, x + (80 - iw) / 2, y + (82 - ih) / 2, iw, ih, 1);
            wrap(
                    fortuneState == 0 ? "在游戏中手持签筒，右键抽取今天的签。" : "在游戏中拿着未解签，对解签龛使用，即可读到签文。",
                    x + 101,
                    y + 29,
                    286,
                    14,
                    21,
                    paper.ink(),
                    false);
            text("手册会在操作完成后更新你的签笺状态。", x + 101, y + 72, 11, paper.muted(), false);
            button(
                    "fortune-help-ok",
                    "知道了",
                    modalX + modalW - 109,
                    y + 100,
                    84,
                    35,
                    true,
                    true,
                    this::dismissOverlay);
        }
        d.restore();
        d.strokeRoundRect(
                modalX + .5f, modalY + .5f, modalW - 1, modalH - 1, 23, 1, paper.ruling());
        pop();
        endAlpha();
        endAlpha();
        d.literalColors(previous);
    }

    private void titles() {
        if (winter) {
            winterAlbum();
            return;
        }
        heading(
                "我的称号",
                "选一个喜欢的称号，让名字多一点自己的颜色。",
                "MemorialTitles · " + moduleLabel("titles"),
                live() && !catalogueComplete ? -1 : titleCount());
        if (live() && !ready("titles")) {
            unavailable("titles");
            return;
        }
        group(30, 350);
        var paper = d.winterPaper();
        if (winter) HubWinterSheet.panel(d, LEFT, 174, 234, 36, 11, paper.soft(), opacity);
        else glass(LEFT, 174, 234, 36, 13, 0x5cdce0f0, 0x5cdce0f0);
        String[] labels = {"已拥有", "全部", "未获得"};
        for (int i = 0; i < 3; i++) {
            final int value = i;
            float x = LEFT + 3 + i * 76;
            float chosen = stateTransition("title-filter-selected-" + i, filter == i ? 1 : 0, 140);
            boolean hover = filter != i && inside(x, 177, 74, 30);
            if (winter) {
                boolean previous = d.literalColors(true);
                if (hover) d.roundRect(x, 177, 74, 30, 7, a(paper.sheet()));
                if (chosen > 0) d.roundRect(x, 177, 74, 30, 7, a(withAlpha(paper.pine(), chosen)));
                int tint = mix(hover ? paper.pine() : paper.muted(), paper.sheet(), chosen);
                textCenter(labels[i], x + 37, 197, 11, tint, filter == i);
                d.literalColors(previous);
            } else {
                if (chosen > 0) {
                    beginAlpha(chosen, x, 177, 74, 30);
                    glass(x, 177, 74, 30, 10, 0xffffffff, 0xffffffff);
                    endAlpha();
                }
                textCenter(
                        labels[i],
                        x + 37,
                        197,
                        11,
                        mix(0xff78839b, 0xff6650a7, chosen),
                        filter == i);
            }
            hit(
                    "filter-" + i,
                    labels[i],
                    x,
                    177,
                    74,
                    30,
                    () -> {
                        filter = value;
                        titleScroll = 0;
                    });
        }
        iconButton(
                "density",
                compact ? "切换卡片显示" : "切换紧凑显示",
                compact ? "grid-2x2" : "list",
                864,
                176,
                32,
                () -> {
                    compact = !compact;
                    titleScroll = 0;
                },
                true);
        if (winter) HubWinterSheet.panel(d, LEFT, 223, 518, 36, 11, paper.sheet(), opacity);
        else glass(LEFT, 223, 518, 36, 11, 0xb3ffffff, 0xb3ffffff);
        boolean previous = winter && d.literalColors(true);
        icon("search", 248, 233, 16, winter ? paper.muted() : 0xff929bb1);
        if (searchSelectAll && !query.isEmpty() && "search".equals(focusId))
            d.roundRect(
                    273,
                    230,
                    Math.min(438, d.textWidth(query, 12, false)) + 2,
                    21,
                    3,
                    a(winter ? paper.cover() : 0xffd4c9ff));
        text(
                query.isEmpty() ? "搜索称号" : fit(query, 439, 12, false),
                274,
                247,
                12,
                winter
                        ? (query.isEmpty() ? paper.muted() : paper.ink())
                        : (query.isEmpty() ? 0xff949caf : INK),
                false);
        if ("search".equals(focusId)) {
            if (!winter) {
                d.boxShadow(LEFT, 223, 518, 36, 11, 0, 0, 0, 3, 0x1fab92ef);
                d.strokeRoundRect(LEFT, 223, 518, 36, 11, 1, a(0xffa391dc));
            }
            if (!searchSelectAll && (now / 500_000_000L) % 2 == 0) {
                float caret =
                        274
                                + d.textWidth(
                                        query.substring(0, Math.min(searchCaret, query.length())),
                                        12,
                                        false);
                if (caret < 735) d.line(caret, 232, caret, 250, 1, a(winter ? paper.ink() : INK));
            }
        }
        if (winter) d.literalColors(previous);
        hit(
                "search",
                "搜索称号",
                LEFT,
                223,
                518,
                36,
                () -> {
                    searchCaret = query.length();
                    searchSelectAll = false;
                });
        if (!query.isEmpty())
            iconButton(
                    "clear-search",
                    "清空搜索",
                    "x",
                    720,
                    228,
                    26,
                    () -> {
                        query = "";
                        searchCaret = 0;
                        titleScroll = 0;
                        searchSelectAll = false;
                        focusId = "search";
                    },
                    true);
        if (winter) {
            HubWinterSheet.panel(d, 766, 223, 132, 36, 9, paper.sheet(), opacity);
            previous = d.literalColors(true);
            if (titleSortOpen || inside(766, 223, 132, 36))
                d.roundRect(767, 224, 130, 34, 8, a(paper.soft()));
            text(sort == 0 ? "最近获得" : "名称排序", 779, 246, 11, paper.ink(), false);
            icon("chevron-down", 874, 234, 14, paper.muted());
            d.literalColors(previous);
            hit(
                    "sort",
                    "称号排序",
                    766,
                    223,
                    132,
                    36,
                    () -> {
                        titleSortOpen = !titleSortOpen;
                        focusId = titleSortOpen ? "sort-option-" + sort : "sort";
                    });
        } else {
            button(
                    "sort",
                    sort == 0 ? "最近获得  ⇅" : "名称排序  ⇅",
                    766,
                    223,
                    132,
                    36,
                    false,
                    true,
                    () -> {
                        sort = 1 - sort;
                        titleScroll = 0;
                    });
        }
        List<HubData.Title> list = titleList();
        float contentHeight = compact ? list.size() * 80f : ((list.size() + 1) / 2) * 174f - 16;
        float viewportHeight = 510;
        titleScroll =
                Math.max(0, Math.min(titleScroll, Math.max(0, contentHeight - viewportHeight)));
        d.save();
        d.clipRoundRect(230, 272, 674, 522, 0);
        titleClipTransform = logical;
        for (int i = 0; i < list.size(); i++) {
            float x = compact ? LEFT : LEFT + (i % 2) * 339;
            float y = 278 + (compact ? i * 80 : (i / 2) * 174) - titleScroll;
            if (y + (compact ? 68 : 158) < 274 || y > 790) continue;
            titleTile(list.get(i), x, y, compact ? 662 : 323, compact ? 68 : 158);
        }
        d.restore();
        if (contentHeight > viewportHeight) {
            float thumb = Math.max(45, viewportHeight * viewportHeight / contentHeight);
            d.roundRect(
                    902,
                    278 + titleScroll / (contentHeight - viewportHeight) * (viewportHeight - thumb),
                    3,
                    thumb,
                    2,
                    a(winter ? withAlpha(paper.wool(), .52f) : 0x99a6b0cf));
        }
        if (list.isEmpty() && !catalogueComplete) {
            icon("tag", 548, 374, 28, 0xff9d90b4);
            textCenter("正在读取称号目录…", 567, 437, 17, 0xff8c8495, true);
        } else if (list.isEmpty()) {
            boolean searching = !query.isBlank();
            icon(searching ? "search" : filter == 2 ? "check" : "tag", 548, 374, 28, 0xff9d90b4);
            textCenter(
                    searching
                            ? "没有找到这个称号"
                            : filter == 2 ? "已集齐全部称号" : filter == 0 ? "暂未获得称号" : "暂无称号",
                    567,
                    437,
                    17,
                    0xff8c8495,
                    true);
            textCenter(
                    searching
                            ? "换个关键词，或查看全部称号。"
                            : filter == 2
                                    ? "你已经收藏了当前全部称号。"
                                    : filter == 0 ? "继续探索旅程解锁更多称号，也可以先查看全部称号。" : "当前目录里还没有可展示的称号。",
                    567,
                    466,
                    12,
                    MUTED,
                    false);
            if (searching || filter != 1)
                button(
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
                            titleScroll = 0;
                        });
        }
        text(
                list.size()
                        + " 枚称号 · "
                        + (!catalogueComplete
                                ? "目录正在加载，请稍候"
                                : filter == 2 ? "还有新的旅程等你开启" : "每一个名字，都有它的故事"),
                LEFT,
                816,
                11,
                winter ? paper.muted() : 0xff8993a9,
                false);
        titleDetail();
        if (winter && titleSortOpen) titleSortMenu();
        ungroup();
    }

    private void titleSortMenu() {
        var paper = d.winterPaper();
        boolean previous = d.literalColors(true);
        d.boxShadow(766, 263, 132, 70, 9, 0, 3, 10, -4, a(0x4815261e));
        HubWinterSheet.panel(d, 766, 263, 132, 70, 9, paper.sheet(), opacity);
        for (int index = 0; index < 2; index++) {
            final int choice = index;
            String label = index == 0 ? "最近获得" : "名称排序";
            float y = 267 + 31 * index;
            if (sort == index || inside(770, y, 124, 31))
                d.roundRect(770, y, 124, 31, 6, a(paper.cover()));
            text(label, 781, y + 20, 11, paper.ink(), sort == index);
            if (sort == index) icon("check", 873, y + 8, 14, paper.pine());
            hit("sort-option-" + index, label, 770, y, 124, 31, () -> chooseTitleSort(choice));
        }
        d.literalColors(previous);
    }

    private void chooseTitleSort(int choice) {
        if (sort != choice) titleScroll = 0;
        sort = choice;
        titleSortOpen = false;
        focusId = "sort";
    }

    private void titleTile(HubData.Title t, float x, float y, float w, float h) {
        boolean selected = selectedTitle.equals(t.id());
        beginAlpha(t.owned() ? 1 : .8f, x, y, w, h);
        float radius = winter ? 12 : 18;
        HubMotion.Tilt tilt =
                beginTilt(
                        "title-" + t.id(), x, y, w, h, 5, 1.018f, false, winter ? 0 : .12f, radius);
        if (winter) HubWinterSheet.titleTile(d, t, x, y, w, h, compact, selected, opacity);
        else {
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
                    a(
                            dark
                                    ? (selected ? 0x4d0b142d : 0x290c122a)
                                    : selected ? withAlpha(t.color(), .15f) : 0x0c607097));
            int wash = dark ? mix(t.color(), 0xff273148, .77f) : t.light();
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
                    new int[] {a(wash), a(dark ? 0xe8293247 : 0xd9ffffff)},
                    new float[] {0, .74f});
            int edge =
                    dark
                            ? (selected
                                    ? mix(t.color(), 0xffd5c7fb, .28f)
                                    : cssMix(t.color(), 0x3b7987a6, .76f))
                            : selected ? mix(t.color(), 0xff6452ad, .2f) : 0xfaffffff;
            d.strokeRoundRect(x + .5f, y + .5f, w - 1, h - 1, 18, 1, a(edge));
            if (selected)
                d.strokeRoundRect(
                        x + 1.5f,
                        y + 1.5f,
                        w - 3,
                        h - 3,
                        17,
                        1,
                        a(withAlpha(t.color(), dark ? .25f : .48f)));
            else if (dark) d.insetBoxShadow(x, y, w, h, 18, 0, 1, 0, 0, a(0x12deebff));
            else d.strokeRoundRect(x + 1.5f, y + 1.5f, w - 3, h - 3, 17, 1, a(0xccffffff));
            d.literalColors(previousColors);
        }
        var paper = d.winterPaper();
        boolean previous = winter && d.literalColors(true);
        int muted = winter ? paper.muted() : 0xff7c859a;
        int quality = winter ? mix(paper.ink(), t.color(), .23f) : mix(t.color(), INK, .4f);
        if (compact) {
            if (winter) HubWinterSheet.emblem(d, t, x + 16, y + 15, 33, true, opacity);
            else emblem(t, x + 16, y + 13, 42);
            float copyX = x + (winter ? 67 : 75);
            titleLabel(t, copyX, y + 30, 17, 1, false, !t.owned(), w - 178);
            text(t.category(), copyX, y + 50, 10, muted, false);
            if (Objects.equals(equipped, t.id())) {
                if (winter) titleStatus("✓ 已佩戴", x + w - 84, y + 22, true);
                else pill("✓ 已佩戴", x + w - 84, y + 22, 0xa3ffffff, 0xff448772, false);
            } else if (!t.owned()) icon("lock", x + w - 36, y + 26, 14, 0xff8d97ad);
        } else {
            text(t.category(), x + 20, y + 32, 10, muted, false);
            titleLabel(t, x + 20, y + 66, 20, 1, false, !t.owned(), w - 111);
            if (!t.rarity().isEmpty()) {
                if (winter) {
                    d.save();
                    d.canvas().concat(Matrix33.makeRotate(45, x + 23, y + 88));
                    d.roundRect(x + 21, y + 86, 4, 4, 1, a(t.color()));
                    d.restore();
                } else d.circle(x + 23, y + 88, 2, a(t.rarityColor()));
                text(t.rarity(), x + 32, y + 92, 10, winter ? quality : t.rarityColor(), false);
            }
            if (winter) HubWinterSheet.emblem(d, t, x + w - 74, y + 41, 54, false, opacity);
            else emblem(t, x + w - 81, y + 43, 60);
            if (Objects.equals(equipped, t.id())) {
                if (winter) titleStatus("✓ 已佩戴", x + w - 86, y + 12, true);
                else pill("✓ 已佩戴", x + w - 86, y + 12, 0xa3ffffff, 0xff448772, false);
            } else if (!t.owned()) icon("lock", x + w - 31, y + 17, 14, 0xff8d97ad);
            if (winter) HubWinterSheet.dashedLine(d, x + 20, y + 124, w - 40, a(paper.stitch()));
            else
                d.line(
                        x + 20,
                        y + 124,
                        x + w - 20,
                        y + 124,
                        1,
                        a(mix(t.color(), 0x00ffffff, .75f)));
            icon("calendar-days", x + 20, y + 136, 12, winter ? muted : 0xff7b859c);
            text(
                    t.owned() ? titleDate(t) : "尚未解锁",
                    x + 38,
                    y + 146,
                    10,
                    winter ? muted : 0xff7b859c,
                    false);
            if (selected) {
                icon("check", x + w - 77, y + 135, 12, quality);
                text("已选中", x + w - 60, y + 146, 10, quality, false);
            } else icon("arrow-up-right", x + w - 34, y + 135, 14, quality);
        }
        if (winter) d.literalColors(previous);
        // Clamp hit areas to the scroll viewport so clipped cards cannot steal input.
        hit(
                "title-" + t.id(),
                t.name() + (t.owned() ? "" : "，未获得"),
                x,
                y,
                w,
                h,
                () -> selectedTitle = t.id());
        endTilt(tilt, x, y, w, h, radius, 5, winter ? 0 : .5f);
        endAlpha();
    }

    private void emblem(HubData.Title t, float x, float y, float size) {
        float radius = size * .28f;
        d.save();
        d.translate(x + size / 2, y + size / 2);
        d.canvas().rotate(-9);
        d.save();
        d.canvas().rotate(12);
        d.translate(4, -5);
        int back = t.owned() ? t.color() : desaturate(t.color(), .35f);
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
                a(dark ? 0x400b152e : withAlpha(back, .35f)));
        d.linearGradientRoundRect(
                -size / 2,
                -size / 2,
                size,
                size,
                radius,
                145,
                new int[] {a(back), a(mix(back, 0xff463169, .17f))},
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
                        ? new int[] {a(0x29d3defc), a(0x0ec8d2f2)}
                        : new int[] {a(0xa6ffffff), a(0x47ffffff)},
                new float[] {0, 1});
        d.strokeRoundRect(
                -size / 2, -size / 2, size, size, radius, 1, a(dark ? 0x47d9e4ff : 0xd9ffffff));
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
                a(dark ? 0x36e7eeff : 0xffffffff));
        d.literalColors(previousColors);
        icon(
                "stroke-1.5/" + t.icon(),
                -size * .27f,
                -size * .27f,
                size * .54f,
                dark ? mix(t.color(), 0xfff4f7ff, .45f) : mix(t.color(), 0xff25364c, .32f));
        d.restore();
    }

    private void titleStatus(String label, float x, float y, boolean worn) {
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

    private void winterNameplate(HubData.Title title) {
        var paper = d.winterPaper();
        HubWinterSheet.snowCover(d, 940, 225, 274, 226, 18, opacity);
        boolean previous = d.literalColors(true);
        d.strokeRoundRect(940.5f, 225.5f, 273, 225, 18, 1, a(paper.ruling()));
        d.save();
        d.clipRoundRect(940, 225, 274, 226, 18);
        icon("stroke-1.0/" + title.icon(), 931, 292, 106, withAlpha(title.color(), .14f));
        d.restore();
        titleLabel(title, 1077, 275, 21, 2, true, false);
        String player = fit(playerName, 230, 13, true);
        d.textTracked(
                player,
                1077 - d.textWidthTracked(player, 13, 500, .5f, false) / 2,
                301.5f,
                13,
                a(paper.ink()),
                500,
                .5f,
                false);
        int snow = dark ? 0xffc5d9df : 0xfff8ffff;
        int shadow = dark ? 0xff69858b : 0xffc1d7d4;
        d.oval(1007, 381, 140, 42, a(shadow));
        d.oval(1007, 377, 140, 42, a(mix(paper.cover(), snow, .55f)));
        d.strokeOval(1007.5f, 377.5f, 139, 41, 1, a(shadow));
        d.oval(1021.5f, 379, 111, 26, a(withAlpha(shadow, .42f)));
        if (opacity > .04f) HubHead.draw(d, 1051, 335, 51, -23);
        textCenter("游戏内效果示意", 1077, 439.5f, 10, paper.muted(), false);
        HubWinterSheet.illustration(d, "grove", 1134, 371, 74, 72, opacity);
        d.literalColors(previous);
    }

    private void titleDetail() {
        var paper = d.winterPaper();
        HubData.Title t = data.title(selectedTitle);
        if (t == null && !data.titles.isEmpty()) t = data.titles.get(0);
        if (t == null) {
            if (winter) HubWinterSheet.panel(d, RIGHT, 174, 310, 626, 22, paper.sheet(), opacity);
            else glass(RIGHT, 174, 310, 626, 22, 0xecffffff, 0xabffffff);
            textCenter("选择称号查看详情", 1077, 450, 15, MUTED, false);
            return;
        }
        final HubData.Title selected = t;
        if (winter) HubWinterSheet.panel(d, RIGHT, 174, 310, 626, 22, paper.sheet(), opacity);
        else glass(RIGHT, 174, 310, 626, 22, 0xecffffff, 0xabffffff);
        text("佩戴预览", 940, 207, 13, winter ? paper.ink() : 0xff4f5a73, true);
        boolean worn = Objects.equals(equipped, t.id());
        titleStatus(worn ? "✓ 已佩戴" : "◉ 仅预览", 1144, 188, worn);
        if (winter) winterNameplate(t);
        else {
            boolean previousColors = d.literalColors(true);
            d.boxShadow(940, 225, 274, 226, 18, 0, 4, 12, 0, a(dark ? 0x2e0b142f : 0x108f85ad));
            d.linearGradientRoundRect(
                    940,
                    225,
                    274,
                    226,
                    18,
                    135,
                    new int[] {
                        a(dark ? mix(t.color(), 0xff263349, .77f) : t.light()),
                        a(dark ? 0x99423a5c : 0xb0e8e3ff)
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
                    new int[] {a(sheen), sheen & 0xffffff},
                    new float[] {0, .75f});
            d.insetBoxShadow(940, 225, 274, 226, 18, 0, 1, 3, 0, a(dark ? 0x14d7e6ff : 0xffffffff));
            d.strokeRoundRect(940.5f, 225.5f, 273, 225, 18, 1, a(dark ? 0x2ec5d6f4 : 0xffffffff));
            d.strokeRoundRect(948, 233, 258, 210, 11, 1, a(dark ? 0x16d3e3ff : 0x8cffffff));
            d.literalColors(previousColors);
            d.save();
            d.clipRoundRect(940, 225, 274, 226, 18);
            d.translate(1077, 338);
            d.canvas().rotate(-30);
            d.strokeRoundRect(-134, -49, 268, 98, 49, 1, a(dark ? 0x26cadaff : 0xb8ffffff));
            d.restore();
            icon("stroke-1.0/" + t.icon(), 931, 292, 106, withAlpha(t.color(), .14f));
            // Web line boxes: top26 + height31.5 + margin5, then the 17px player-name line.
            // Measured baselines relative to the preview top are 50 and 76.5 (not glyph-top
            // positions).
            titleLabel(t, 1077, 275, 21, 2, true, false);
            String previewPlayer = fit(playerName, 230, 13, true);
            float previewPlayerWidth = d.textWidthTracked(previewPlayer, 13, 500, .5f, false);
            d.textTracked(
                    previewPlayer,
                    1077 - previewPlayerWidth * .5f,
                    301.5f,
                    13,
                    a(0xff62718f),
                    500,
                    .5f,
                    false);
            // .nameplate-preview::before + .preview-ground: two true ellipses form the source
            // pedestal.
            previousColors = d.literalColors(true);
            d.ovalShadow(1007, 377, 140, 42, 0, 5, 12, a(dark ? 0x4d101e3d : 0x277184b8));
            d.oval(1007, 377, 140, 42, a(dark ? 0x21adbed5 : 0x9cf6f8ff));
            d.insetOvalShadow(1008, 378, 138, 40, 0, -5, 7, a(dark ? 0x10a9a0d4 : 0x17b0a8d0));
            d.strokeOval(1007.5f, 377.5f, 139, 41, 1, a(dark ? 0x30d1e2ff : 0xffffffff));
            d.oval(1021.5f, 379, 111, 26, a(dark ? 0x400f1b33 : 0x4da3b4d5));
            d.literalColors(previousColors);
            // The projected cube's bottom meets the ground ellipse at y=392, as in the web preview.
            if (opacity > .04f) HubHead.draw(d, 1051, 335, 51, -23);
            textCenter("游戏内效果示意", 1077, 419.5f, 10, 0xff7d87a1, false);
        }
        boolean winterColors = winter && d.literalColors(true);
        int detailInk = winter ? paper.ink() : INK;
        int detailMuted = winter ? paper.muted() : MUTED;
        icon(t.icon(), 940, 473, 18, mix(t.color(), detailInk, .55f));
        if (winter)
            text(fit(t.name(), 249, 14, true), 966, 488, 14, mix(detailInk, t.color(), .23f), true);
        else titleLabel(t, 966, 488, 14, 0, false, false);
        hit("full-title", "查看称号全文", 940, 466, 274, 28, () -> openOverlay("title-name"));
        wrap(t.description(), 940, 516, 274, 12, 22, winter ? detailMuted : 0xff748098, false);
        if (winter) HubWinterSheet.panel(d, 940, 568, 274, 73, 10, paper.soft(), opacity);
        else glass(940, 568, 274, 73, 10, 0xfff7f7fc, 0xfff7f7fc);
        icon("gift", 952, 580, 13, winter ? detailMuted : 0xff8990a5);
        text(t.owned() ? "获得方式" : "解锁条件", 973, 591, 10, winter ? detailMuted : 0xff8990a5, false);
        wrap(t.condition(), 952, 615, 250, 11, 18, winter ? detailInk : 0xff6d7890, false);
        if (t.owned()) {
            icon("calendar-days", 942, 655, 13, detailMuted);
            text(titleDate(t) + "  获得", 962, 667, 10, detailMuted, false);
        }
        if (winter) {
            d.save();
            d.clipRoundRect(RIGHT + 1, 175, 308, 624, 21);
            d.rect(RIGHT, 678, 310, 122, a(paper.soft()));
            d.line(RIGHT, 678, RIGHT + 310, 678, 1, a(paper.ruling()));
            d.restore();
            d.literalColors(winterColors);
        }
        String equipText = !t.owned() ? "尚未获得" : worn ? "✓ 已佩戴 · 点击摘下" : "佩戴这个称号  →";
        button(
                "equip",
                pendingModules.contains("titles") ? "等待服务器确认…" : equipText,
                940,
                687,
                274,
                38,
                !worn,
                t.owned() && !pendingModules.contains("titles") && catalogueComplete,
                () -> equipTitle(selected));
        text("展示给其他玩家", 940, 754, 11, winter ? paper.ink() : 0xff65718b, false);
        if (live())
            iconButton(
                    "display-settings",
                    "称号展示位置",
                    "settings",
                    1128,
                    742,
                    25,
                    () -> openOverlay("title-display"),
                    !pendingModules.contains("titles"));
        String visibilityNote =
                !privacyKnown()
                        ? (catalogueComplete ? "称号展示状态暂不可用" : "正在读取称号展示状态…")
                        : live() && (equipped == null || equipped.isEmpty())
                                ? "先佩戴称号后可设置"
                                : visible ? (live() ? "仅修改当前佩戴称号的展示" : "本地展示示意") : "已隐藏展示，佩戴状态保留";
        text(visibilityNote, 940, 774, 10, winter ? paper.muted() : 0xff8a94aa, false);
        toggle(
                "visibility",
                privacyKnown() && visible,
                1174,
                746,
                this::changeVisibility,
                canChangeVisibility());
    }

    private Map<LocalDate, HubData.Entry> archive() {
        if ((live() && !winter) || fortuneState == 3 || !data.archive.containsKey(data.today))
            return data.archive;
        Map<LocalDate, HubData.Entry> copy = new java.util.LinkedHashMap<>(data.archive);
        copy.remove(data.today);
        return copy;
    }

    private HubData.Entry todayEntry() {
        return live() ? data.todayRecord : new HubData.Entry(data.today, "20:26", data.fortune(1));
    }

    private void unavailable(String module) {
        float left = winter ? COLLECTION_LEFT : LEFT;
        float width = winter ? COLLECTION_WIDTH : WIDTH;
        float top = winter ? COLLECTION_TOP : 174;
        float height = winter ? ("titles".equals(module) ? ALBUM_END : DESK_END) - top : 626;
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

    private void fortunes() {
        if (winter) {
            winterCounter();
            return;
        }
        Map<LocalDate, HubData.Entry> records = archive();
        heading(
                "我的签笺",
                "一日一签，把沿途的机缘慢慢收藏。",
                "DailyFortunes · " + moduleLabel("fortunes"),
                archiveCount());
        if (live() && !ready("fortunes")) {
            unavailable("fortunes");
            return;
        }
        group(35, 360);
        var paper = d.winterPaper();
        if (winter) HubWinterSheet.panel(d, LEFT, 174, 662, 626, 22, paper.sheet(), opacity);
        else glass(LEFT, 174, 662, 626, 22, 0xecffffff, 0xabffffff);
        int stored =
                (int)
                        records.keySet().stream()
                                .filter(date -> YearMonth.from(date).equals(month))
                                .count();
        float monthProgress = progress(now - monthAt, 260_000_000L);
        float monthHeading = reducedMotion ? 1 : HubMotion.easeOut(monthProgress);
        float monthOffset = monthDirection * (28f / 3) * (1 - monthHeading);
        beginAlpha(monthHeading, LEFT, 174, 430, 78);
        boolean previous = winter && d.literalColors(true);
        String numeral = String.format(Locale.ROOT, "%02d", month.getMonthValue());
        if (winter) {
            float x = 258 + monthOffset;
            d.save();
            d.clipRoundRect(x, 189, 54, 55, 8);
            d.roundRect(x, 184, 54, 60, 2, a(paper.pine()));
            d.roundRect(x, 230, 54, 14, 8, a(paper.pine()));
            d.restore();
            serifCenter(numeral, x + 27, 228, 37, paper.sheet(), false);
        } else {
            text(
                    String.format(Locale.ROOT, "%02d", month.getMonthValue()),
                    258 + monthOffset,
                    231,
                    39,
                    0xff67549b,
                    true);
        }
        text(
                month.getYear() + " 年 " + month.getMonthValue() + " 月",
                320 + monthOffset,
                211,
                13,
                winter ? paper.ink() : 0xff505b76,
                true);
        icon("bookmark", 321 + monthOffset, 223, 12, winter ? paper.pine() : 0xff8a8fa5);
        text(
                calendarInitialLoading() ? "正在读取本月收藏…" : "本月收藏 " + stored + " 张",
                339 + monthOffset,
                234,
                10,
                winter ? paper.muted() : 0xff8a8fa5,
                false);
        if (winter) d.literalColors(previous);
        endAlpha();
        if (winter) {
            HubWinterSheet.dashedLine(d, 258, 248, 617, a(paper.stitch()));
            HubWinterSheet.illustration(d, "winter-post", 464, 185, 228, 60, opacity);
            HubWinterSheet.panel(d, 704, 195, 171, 37, 11, paper.soft(), opacity);
        } else glass(704, 195, 171, 37, 11, 0xfff0edf8, 0xfff0edf8);
        textButton(
                "current-month",
                "本月",
                707,
                198,
                45,
                30,
                () -> {
                    monthDirection = 1;
                    month = YearMonth.from(data.today);
                    selectedDate = data.today;
                    chooseLatestOnCalendar = false;
                    monthAt = System.nanoTime();
                    historyScroll = 0;
                    calendarRequest();
                },
                true);
        iconButton(
                "previous-month", "上个月", "chevron-left", 752, 198, 29, () -> shiftMonth(-1), true);
        iconButton(
                "next-month",
                "下个月",
                "chevron-right",
                782,
                198,
                29,
                () -> shiftMonth(1),
                month.isBefore(YearMonth.from(data.today)));
        previous = winter && d.literalColors(true);
        d.line(816, 205, 816, 225, 1, a(winter ? paper.ruling() : 0xffdbd7e7));
        if (winter) d.literalColors(previous);
        iconButton(
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
        float monthBody = reducedMotion ? 1 : HubMotion.bezier(monthProgress, .18f, .75f, .22f, 1);
        float slide = monthDirection * 28 * (1 - monthBody);
        beginAlpha(monthBody);
        push(Matrix33.makeTranslate(slide, 0));
        if (calendarInitialLoading()) textCenter("正在读取本月收藏…", 567, 485, 14, MUTED, false);
        else if (history) fortuneHistory(records, 0);
        else calendar(records, 0);
        pop();
        endAlpha();
        d.restore();
        previous = winter && d.literalColors(true);
        String[] grades = {"上上签", "上吉签", "中平签", "小凶签", "下下签"};
        for (int i = 0; i < grades.length; i++) {
            float x = 258 + i * 122;
            d.circle(x + 3, 775, 3, a(HubData.gradeColor(grades[i])));
            text(grades[i], x + 15, 779, 10, winter ? paper.muted() : 0xff808aa0, false);
        }
        if (winter) d.literalColors(previous);
        fortuneSelection(records);
        ungroup();
    }

    private void calendar(Map<LocalDate, HubData.Entry> records, float slide) {
        var paper = d.winterPaper();
        boolean previous = winter && d.literalColors(true);
        String weekdays = "日一二三四五六";
        for (int i = 0; i < 7; i++)
            textCenter(
                    weekdays.substring(i, i + 1),
                    298 + i * 88,
                    273,
                    11,
                    winter
                            ? (i == 0 || i == 6 ? paper.mailInk() : paper.muted())
                            : (i == 0 || i == 6 ? 0xffae92af : 0xff8c92a8),
                    false);
        int start = month.atDay(1).getDayOfWeek().getValue() % 7;
        int cells = ((start + month.lengthOfMonth() + 6) / 7) * 7, rows = cells / 7;
        float gap = 7, cellW = (618 - gap * 6) / 7, cellH = (449 - gap * (rows - 1)) / rows;
        for (int i = 0; i < cells; i++) {
            int day = i - start + 1;
            float x = 258 + (i % 7) * (cellW + gap), y = 292 + (i / 7) * (cellH + gap);
            if (day < 1 || day > month.lengthOfMonth()) {
                if (winter && i == cells - 1)
                    HubWinterSheet.illustration(
                            d, "sleeping-fox", x + cellW * .05f, y, cellW * .90f, cellH, opacity);
                continue;
            }
            LocalDate date = month.atDay(day);
            HubData.Entry entry = records.get(date);
            boolean future = date.isAfter(data.today),
                    selected = date.equals(selectedDate),
                    today = date.equals(data.today);
            int grade = entry == null ? 0xffa0a7bb : HubData.gradeColor(entry.fortune().grade());
            boolean previousColors = d.literalColors(true);
            if (winter) {
                winterCalendarDay(date, entry, grade, x, y, cellW, cellH, slide, future, selected);
            } else {
                if (entry != null) {
                    int[] colors =
                            dark
                                    ? new int[] {
                                        0xff354058,
                                        0xff303d51,
                                        mix(grade, 0xff30394a, .74f),
                                        mix(grade, 0xff30394a, .44f)
                                    }
                                    : new int[] {
                                        0xffffffff,
                                        0xcfffffff,
                                        lighten(grade, .8f),
                                        lighten(grade, .43f)
                                    };
                    for (int index = 0; index < colors.length; index++)
                        colors[index] = a(colors[index]);
                    d.linearGradientRoundRect(
                            x, y, cellW, cellH, 11, 140, colors, new float[] {.08f, .4f, .69f, 1});
                    d.insetBoxShadow(
                            x, y, cellW, cellH, 11, 0, 1, 0, 0, a(dark ? 0x14d8e3f5 : 0xffffffff));
                } else
                    d.roundRect(
                            x,
                            y,
                            cellW,
                            cellH,
                            11,
                            a(
                                    dark
                                            ? (future ? 0x3820283d : 0x5c202b40)
                                            : (future ? 0xfff5f5fa : 0xfff4f4fa)));
                float selection = stateTransition("day-selected-" + date, selected ? 1 : 0, 160);
                int edge =
                        dark
                                ? (future ? 0x0caabade : 0x20adbfde)
                                : (future ? 0xffeeedf5 : 0xffe6e7f1);
                d.strokeRoundRect(
                        x + .5f,
                        y + .5f,
                        cellW - 1,
                        cellH - 1,
                        11,
                        1,
                        a(mix(edge, dark ? 0xffc2a6e6 : 0xff8b6bb9, selection)));
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
                            a(withAlpha(dark ? 0x2bb395d6 : 0x279a7bc3, selection)));
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
                            a(withAlpha(dark ? 0x38121932 : 0x198876aa, selection)));
                }
                float hover =
                        transition(
                                "day-hover-" + date,
                                !future && inside(x + slide, y, cellW, cellH) ? 1 : 0,
                                160);
                if (hover > 0)
                    d.strokeRoundRect(
                            x + .5f,
                            y + .5f,
                            cellW - 1,
                            cellH - 1,
                            11,
                            1,
                            a(withAlpha(dark ? 0xffb39ace : 0xffb396d8, hover)));
            }
            d.literalColors(previousColors);
            text(
                    Integer.toString(day),
                    x + 10,
                    y + 20,
                    12,
                    winter
                            ? (future ? withAlpha(paper.muted(), .72f) : paper.ink())
                            : (future ? 0xffacb2c2 : 0xff65728b),
                    false);
            if (today)
                textRight(
                        "今天", x + cellW - 7, y + 18, 9, winter ? paper.pine() : 0xff8f70ad, false);
            float slipH = Math.min(38, cellH - 26), slipW = slipH * .83f;
            if (!future && (!winter || entry != null)) {
                d.save();
                d.translate(x + cellW / 2, y + cellH / 2 + slipH / 2 - 10);
                d.canvas().rotate(-8);
                image(
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
                        y + cellH - (winter ? 19 : 17),
                        labelWidth,
                        14,
                        3,
                        a(winter ? paper.sheet() : dark ? 0x9c172335 : 0x80ffffff));
                textRight(
                        label,
                        x + cellW - 6,
                        y + cellH - (winter ? 8 : 6),
                        9,
                        winter
                                ? paper.ink()
                                : dark ? mix(grade, 0xfff4edda, .48f) : mix(grade, 0xff24352e, .1f),
                        false);
                d.literalColors(previousColors);
            }
            if (!future)
                hit(
                        "day-" + date,
                        date + (entry == null ? " 未收藏" : " " + entry.fortune().title()),
                        x + slide,
                        y,
                        cellW,
                        cellH,
                        () -> selectedDate = date);
        }
        if (winter) d.literalColors(previous);
    }

    private void winterCalendarDay(
            LocalDate date,
            HubData.Entry entry,
            int grade,
            float x,
            float y,
            float w,
            float h,
            float slide,
            boolean future,
            boolean selected) {
        var paper = d.winterPaper();
        if (entry != null) {
            d.roundRect(x, y, w, h, 7, a(mix(paper.sheet(), grade, .09f)));
            d.save();
            d.clipRoundRect(x, y, w, h, 7);
            d.rect(x, y + h - 3, w, 3, a(grade));
            d.restore();
        } else if (!future) {
            d.roundRect(x, y, w, h, 7, a(paper.paper()));
        }
        float focus = stateTransition("day-selected-" + date, selected ? 1 : 0, 160);
        float hover =
                transition("day-hover-" + date, !future && inside(x + slide, y, w, h) ? 1 : 0, 160);
        int base = future ? withAlpha(paper.ruling(), .60f) : paper.ruling();
        if (hover > 0 && focus < 1)
            d.roundRect(x + 1, y + 1, w - 2, h - 4, 6, a(withAlpha(paper.cover(), .6f * hover)));
        int border = mix(base, paper.pine(), focus);
        d.strokeRoundRect(x + .5f, y + .5f, w - 1, h - 1, 7, 1, a(border));
        if (focus > 0)
            d.strokeRoundRect(
                    x + 1.5f, y + 1.5f, w - 3, h - 3, 6, 1, a(withAlpha(paper.pine(), focus)));
    }

    private void fortuneHistory(Map<LocalDate, HubData.Entry> records, float slide) {
        var paper = d.winterPaper();
        List<HubData.Entry> entries =
                records.values().stream()
                        .filter(e -> YearMonth.from(e.date()).equals(month))
                        .sorted(Comparator.comparing(HubData.Entry::date).reversed())
                        .toList();
        historyScroll =
                Math.max(0, Math.min(historyScroll, Math.max(0, entries.size() * 80 - 488)));
        if (entries.isEmpty()) {
            icon("book-open", 547, 382, 30, 0xffa49bb3);
            textCenter("这个月还没有签笺", 567, 443, 17, 0xff8c8495, true);
            textCenter("已收藏的签会按抽签日期出现在这里。", 567, 474, 12, MUTED, false);
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            HubData.Entry entry = entries.get(i);
            float y = 256 + i * 80 - historyScroll;
            if (y + 70 < 252 || y > 746) continue;
            boolean selected = entry.date().equals(selectedDate);
            boolean previous = winter && d.literalColors(true);
            if (winter)
                HubWinterSheet.panel(
                        d,
                        258,
                        y,
                        618,
                        70,
                        11,
                        selected || inside(258 + slide, y, 618, 70) ? paper.cover() : paper.sheet(),
                        opacity);
            else {
                d.roundRect(258, y, 618, 70, 11, a(selected ? 0xffeee7fb : 0x90ffffff));
                d.strokeRoundRect(258, y, 618, 70, 11, 1, a(0xffe7e5f2));
            }
            image("fortune-slip", 270, y + 14, 32, 41, 1);
            text(entry.fortune().title(), 319, y + 29, 13, winter ? paper.ink() : 0xff55617b, true);
            text(
                    entry.date().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
                            + "  "
                            + entry.time(),
                    319,
                    y + 51,
                    10,
                    winter ? paper.muted() : 0xff8d96ac,
                    false);
            textRight(
                    entry.fortune().grade(),
                    835,
                    y + 37,
                    12,
                    HubData.gradeColor(entry.fortune().grade()),
                    false);
            icon("chevron-right", 848, y + 26, 16, winter ? paper.muted() : 0xff9989b2);
            if (winter) d.literalColors(previous);
            float top = Math.max(252, y), bottom = Math.min(746, y + 70);
            if (bottom > top)
                hit(
                        "history-" + entry.date(),
                        entry.date() + " " + entry.fortune().title(),
                        258 + slide,
                        top,
                        618,
                        bottom - top,
                        () -> selectedDate = entry.date());
        }
    }

    private void fortuneSelection(Map<LocalDate, HubData.Entry> records) {
        HubData.Entry entry = records.get(selectedDate);
        var paper = d.winterPaper();
        if (winter) HubWinterSheet.panel(d, RIGHT, 174, 310, 450, 22, paper.sheet(), opacity);
        else glass(RIGHT, 174, 310, 450, 22, 0xf2fffdf9, 0xc7ffffff);
        boolean winterColors = winter && d.literalColors(true);
        text(selectedDate.format(DATE), 939, 204, 11, winter ? paper.muted() : 0xff85879a, false);
        if (selectedDate.equals(data.today)) {
            if (winter) titleStatus("今天", 1160, 187, true);
            else pill("今天", 1160, 187, 0xbceffff2, 0xff25816a, true);
        }
        if (entry == null) {
            image("fortune-slip", 1039, 314, 76, 97, .3f);
            textCenter(
                    calendarInitialLoading() ? "正在读取这一天的签笺…" : "这一天，没有留下签笺",
                    1077,
                    456,
                    15,
                    winter ? paper.ink() : 0xff8c8495,
                    true);
            wrap(
                    calendarInitialLoading() ? "等待服务器确认收藏记录。" : "打开藏签匣时，背包中属于你的已解签会按对应日期归档。",
                    957,
                    491,
                    240,
                    12,
                    22,
                    winter ? paper.muted() : 0xffa39bab,
                    false);
            if (selectedDate.equals(data.today) && fortuneState < 3)
                textCenter(
                        stateLabel() + " · 尚未归档",
                        1077,
                        562,
                        11,
                        winter ? paper.muted() : 0xffa39bab,
                        false);
        } else {
            HubData.Fortune f = entry.fortune();
            if (winter) HubWinterSheet.letter(d, 937, 219, 280, 331, true, opacity);
            else paper(937, 219, 280, 331);
            boolean previousColors = d.literalColors(true);
            d.save();
            d.translate(1077, 262.5f);
            d.canvas().rotate(-6);
            if (winter) {
                d.circle(0, 0, 24, a(paper.sheet()));
                HubWinterSheet.stitchedCircle(d, 0, 0, 23.5f, a(paper.mail()), true);
            } else {
                d.boxShadow(-24, -24.5f, 48, 49, 13, 0, 3, 8, 0, a(dark ? 0x40161126 : 0x29b69c64));
                d.linearGradientRoundRect(
                        -24,
                        -24.5f,
                        48,
                        49,
                        13,
                        145,
                        new int[] {
                            a(dark ? 0x33c7a360 : 0x94f2deac), a(dark ? 0x12e3c47d : 0xfffff7e6)
                        },
                        new float[] {0, 1});
                d.insetBoxShadow(
                        -24, -24.5f, 48, 49, 13, 1, 1, 1, 0, a(dark ? 0x1ff1ddb5 : 0xffffffff));
                d.strokeRoundRect(-23.5f, -24, 47, 48, 13, 1, a(dark ? 0x2bdac18d : 0x99ffffff));
            }
            image("fortune-slip", -15.5f, -21.5f, 31, 43, 1);
            d.restore();
            d.literalColors(previousColors);
            textCenter(
                    f.grade() + " · 第" + f.id() + "签",
                    1077,
                    310,
                    11,
                    HubData.gradeColor(f.grade()),
                    false);
            trackedCenter(
                    f.title(), 1077, 348, 25, 3, winter ? paper.ink() : 0xff615b4f, true, true);
            previousColors = d.literalColors(true);
            d.line(
                    961,
                    365,
                    1193,
                    365,
                    1,
                    a(winter ? paper.stitch() : dark ? 0x29d3bd8a : 0x36c9b78d));
            d.literalColors(previousColors);
            for (int i = 0; i < f.poem().size(); i++)
                trackedCenter(
                        f.poem().get(i),
                        1077,
                        391 + i * 30.55f,
                        13,
                        1.6f,
                        winter ? paper.ink() : 0xff817967,
                        false,
                        true);
            previousColors = d.literalColors(true);
            d.line(
                    961,
                    494,
                    1193,
                    494,
                    1,
                    a(winter ? paper.stitch() : dark ? 0x29d3bd8a : 0x36c9b78d));
            d.literalColors(previousColors);
            text(
                    fit(playerName, 190, 9, false) + " 所得",
                    960,
                    526,
                    9,
                    winter ? paper.muted() : 0xffae9565,
                    false);
            textRight(entry.time(), 1195, 526, 9, winter ? paper.muted() : 0xffae9565, false);
            icon("check-check", 940, 582, 14, winter ? paper.muted() : 0xff7f8f87);
            text("已收藏", 962, 594, 10, winter ? paper.muted() : 0xff7f8f87, false);
            button(
                    "read-selected",
                    "阅读解签  →",
                    1093,
                    572,
                    123,
                    35,
                    true,
                    true,
                    () -> openReading(entry));
        }
        if (winter) {
            HubWinterSheet.panel(d, RIGHT, 640, 310, 160, 19, paper.cover(), opacity);
            HubWinterSheet.panel(d, 938, 656, 53, 56, 14, paper.sheet(), opacity);
        } else {
            glass(RIGHT, 640, 310, 160, 19, 0x99e6eaff, 0xd1f6edff);
            glass(938, 656, 53, 56, 14, 0xd4ffffff, 0x45ffffff);
        }
        image("collection-chest", 936, 653, 57, 61, 1);
        HubData.Entry display = live() ? data.featured : records.get(displayDate);
        text("手册展示", 1004, 668, 10, winter ? paper.muted() : 0xff7b869b, false);
        if (live() && data.featured != null)
            iconButton(
                    "clear-feature",
                    "清除手册展示",
                    "x",
                    1187,
                    651,
                    24,
                    () -> {
                        JsonObject p = mutation("fortunes");
                        p.addProperty("date", "");
                        request("fortunes", "feature", p);
                    },
                    !pendingModules.contains("fortunes"));
        text(
                display == null ? "还没有选择签笺" : display.fortune().title(),
                1004,
                691,
                13,
                winter ? paper.ink() : 0xff625c80,
                true);
        text(
                display == null ? "选择一张已收藏的签" : shortDate(display.date()) + " · 已收藏",
                1004,
                709,
                10,
                winter ? paper.muted() : 0xff9290aa,
                false);
        boolean current = Objects.equals(displayDate, selectedDate) && entry != null;
        button(
                "display-selected",
                pendingModules.contains("fortunes")
                        ? "等待服务器确认…"
                        : current ? "当前选择已展示  ✓" : "展示选中的签  ↗",
                938,
                725,
                278,
                33,
                false,
                entry != null && !current && !pendingModules.contains("fortunes"),
                () -> displaySelected(entry));
        textCenter("仅用于手册，方块展示在游戏内设置", 1077, 783, 9, winter ? paper.muted() : 0xff948ba6, false);
        if (winter) d.literalColors(winterColors);
    }

    private void paper(float x, float y, float w, float h) {
        boolean previousColors = d.literalColors(true);
        d.boxShadow(x, y, w, h, 14, 0, 4, 12, 0, a(dark ? 0x261a152a : 0x088c704e));
        d.linearGradientRoundRect(
                x,
                y,
                w,
                h,
                14,
                120,
                new int[] {a(dark ? 0xff3e3932 : 0xfffff9ec), a(dark ? 0xff34343b : 0xfffffef9)},
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
                new int[] {a(sheen), sheen & 0xffffff},
                new float[] {0, .7f});
        d.insetBoxShadow(x, y, w, h, 14, 0, 1, 2, 0, a(dark ? 0x1aeadcc1 : 0xffffffff));
        d.strokeRoundRect(x + .5f, y + .5f, w - 1, h - 1, 14, 1, a(dark ? 0x3dc7ad71 : 0xffe9dcbf));
        d.strokeRoundRect(x + 6, y + 6, w - 12, h - 12, 9, 1, a(dark ? 0x1fdcc08c : 0x2bcdb57e));
        d.literalColors(previousColors);
    }

    private void shiftMonth(int amount) {
        YearMonth target = month.plusMonths(amount);
        if (target.isAfter(YearMonth.from(data.today))) return;
        month = target;
        monthDirection = Integer.signum(amount);
        monthAt = System.nanoTime();
        historyScroll = 0;
        chooseLatestOnCalendar = true;
        selectedDate =
                archive().keySet().stream()
                        .filter(date -> YearMonth.from(date).equals(month))
                        .max(Comparator.naturalOrder())
                        .orElse(month.atDay(1));
        calendarRequest();
    }

    private void displaySelected(HubData.Entry entry) {
        if (entry == null
                || !(winter ? confirmedArchived(entry) : archive().containsKey(entry.date()))
                || Objects.equals(displayDate, entry.date())) return;
        if (live()) {
            JsonObject p = mutation("fortunes");
            p.addProperty("date", entry.date().toString());
            request("fortunes", "feature", p);
            return;
        }
        displayDate = entry.date();
        feedbackSuccess("已更新手册展示的签笺 · 本地演示");
    }

    private void modal() {
        if (winter && (reading != null || "fortune-help".equals(overlay))) {
            winterFortuneDialog();
            return;
        }
        opacity = 1;
        offsetY = 0;
        nextHits.clear();
        d.captureGlassBackdrop(viewportX, viewportY, viewportW, viewportH);
        float backdrop = reducedMotion ? 1 : HubMotion.ease(progress(now - modalAt, 180_000_000L));
        beginAlpha(backdrop, viewportX, viewportY, viewportW, viewportH);
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
                                                + trackedLines(
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
                        : HubMotion.bezier(progress(now - modalAt, 220_000_000L), .2f, .8f, .2f, 1);
        beginAlpha(enter, modalX, modalY, modalW, modalH);
        push(
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
                            default -> fortuneState == 0 ? "如何抽签" : "如何解签";
                        };
        String subtitle =
                wide
                        ? reading.date().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
                                + " · "
                                + playerName
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
        if (winter) serif(title, modalX + 27, modalY + 39, 19, d.winterPaper().ink(), true);
        else
            text(
                    title,
                    modalX + 27,
                    modalY + 39,
                    19,
                    (winter ? d.winterPaper().ink() : 0xff35415e),
                    true);
        text(
                subtitle,
                modalX + 27,
                modalY + 63,
                12,
                (winter ? d.winterPaper().muted() : 0xff8c8fa5),
                false);
        if (winter) {
            float bx = modalX + modalW - 55, by = modalY + 20;
            var p = d.winterPaper();
            d.circle(bx + 15, by + 15, 15, inside(bx, by, 30, 30) ? p.ruling() : p.cover());
            icon("x", bx + 7.5f, by + 7.5f, 15, p.pine());
            hit("modal-close", "关闭弹窗", bx, by, 30, 30, this::dismissOverlay);
        } else
            iconButton(
                    "modal-close",
                    "关闭弹窗",
                    "x",
                    modalX + modalW - 58,
                    modalY + 22,
                    32,
                    this::dismissOverlay,
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
        if (wide) readingModal();
        else
            switch (overlay) {
                case "settings" -> settingsModal();
                case "modpack-sync" -> modpackSyncModal();
                case "profile" -> profileModal();
                case "preview" -> previewModal();
                case "title-name" -> titleNameModal();
                case "title-display" -> titleDisplayModal();
                default -> fortuneHelp();
            }
        pop();
        endAlpha();
        endAlpha();
    }

    private void stationSettings() {
        var p = d.winterPaper();
        float x = modalX, y = modalY;
        HubWinterSheet.panel(d, x + 25, y + 106, 432, 165.5f, 14, p.soft(), 1);
        HubWinterSheet.grain(d, x + 25, y + 106, 432, 165.5f, 14, .8f);
        text("开屏动画", x + 42, y + 141, 13, p.ink(), true);
        textRight("驿站亮灯", x + 440, y + 141, 11, p.muted(), false);
        HubWinterSheet.panel(d, x + 42, y + 154, 398, 76, 12, p.sheet(), 1);
        int index = welcomeIndex();
        float start = frequencyStart(), step = frequencyStep(), cy = y + 179;
        d.roundRect(start, cy - 3, step * 2, 6, 3, p.ruling());
        if (index > 0) d.roundRect(start, cy - 3, step * index, 6, 3, p.pine());
        for (int i = 0; i < 3; i++) {
            float at = start + i * step;
            d.circle(at, cy, 3.5f, p.stitch());
            d.circle(at, cy, 2.5f, p.sheet());
        }
        float thumb = start + index * step;
        d.boxShadow(thumb - 11, cy - 11, 22, 22, 11, 0, 2, 5, 0, withAlpha(p.ink(), .2f));
        d.circle(thumb, cy, 11, p.sheet());
        d.circle(thumb, cy, 8.5f, p.pine());
        hit(
                "frequency",
                "开屏动画频率",
                start - 12,
                cy - 14,
                step * 2 + 24,
                28,
                () -> setWelcomeFromX(mx));
        String[] labels = {"仅初次", "每次登录", "每次打开"};
        for (int i = 0; i < labels.length; i++) {
            float bx = x + 51 + 128 * i;
            boolean selected = i == index;
            if (selected || inside(bx, y + 195, 124, 27))
                d.roundRect(bx, y + 195, 124, 27, 8, selected ? p.cover() : p.soft());
            textCenter(labels[i], bx + 62, y + 213, 11, selected ? p.pine() : p.muted(), selected);
            String mode = List.of("once", "login", "always").get(i);
            hit("frequency-label-" + i, labels[i], bx, y + 195, 124, 27, () -> welcomeMode = mode);
        }
        String explanation =
                reducedMotion
                        ? "已开启减少动态效果，暂不播放开屏动画"
                        : List.of("仅第一次打开手册时播放，以后不再显示", "每次进服后，仅首次打开手册时播放", "每次打开手册时播放").get(index);
        text(explanation, x + 42, y + 252, 11, p.muted(), false);
        text("减少动态效果", x + 27, y + 302, 13, p.ink(), true);
        text("关闭开屏动画、卡片倾斜与页面过渡", x + 27, y + 324, 11, p.muted(), false);
        toggle(
                "reduce-motion",
                localReducedMotion,
                x + 421,
                y + 300,
                () -> setReducedMotion(!localReducedMotion));
        d.line(x + 25, y + 346, x + 457, y + 346, 1, p.ruling());
        text("界面主题", x + 27, y + 377, 13, p.ink(), true);
        text(dark ? "松绿夜色与暖纸灯光" : "松针绿与雪色纸面", x + 27, y + 399, 11, p.muted(), false);
        stationThemeSelector(false, x + 315, y + 362.5f, 140);
        d.line(x + 25, y + 421, x + 457, y + 421, 1, p.ruling());
        text("手册主题", x + 27, y + 451, 13, p.ink(), true);
        text(dark ? "驿站夜灯" : "雪后清晨", x + 27, y + 474, 11, p.muted(), false);
        stationThemeSelector(true, x + 271, y + 437, 184);
        icon("info", x + 25, y + 509, 15, p.muted());
        text(
                live() ? "偏好按玩家与当前服务器／存档隔离保存。" : "偏好保存在当前测试实例；统计与收藏为本地演示。",
                x + 49,
                y + 521,
                11,
                p.muted(),
                false);
    }

    private void stationThemeSelector(boolean style, float x, float y, float w) {
        var p = d.winterPaper();
        float cell = (w - 12) / 2;
        boolean right = style ? winter : dark;
        float pos = selectorPosition(style ? "style-position" : "theme-position", right);
        HubWinterSheet.panel(d, x, y, w, 41, 11, p.soft(), 1);
        if (pos != (right ? 1 : 0)) d.keepThemeControlLive(x, y, w, 41, 11);
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
            icon(
                    style ? (second ? "snowflake" : "book-open") : (second ? "moon" : "sun"),
                    ix,
                    y + 13,
                    15,
                    color);
            text(label, ix + 20, y + 25.5f, 12, color, false);
            hit(
                    id,
                    label,
                    bx,
                    y + 4,
                    cell,
                    33,
                    () -> {
                        if (style) setWinter(second);
                        else setDark(second);
                    });
        }
    }

    private float frequencyStart() {
        return modalX + (winter ? 114.3f : 92);
    }

    private float frequencyStep() {
        return winter ? 126.65f : 128;
    }

    private void settingsModal() {
        if (winter) {
            stationSettings();
            return;
        }
        float x = modalX + 28, y = modalY + 118;
        text("开屏动画", x, y, 14, winter ? d.winterPaper().ink() : INK, true);
        textRight(
                winter ? "驿站亮灯" : "手写 hello",
                modalX + modalW - 28,
                y,
                10,
                dark ? 0xff9daac3 : 0xff9694ac,
                false);
        boolean previousColors = d.literalColors(true);
        d.boxShadow(x, y + 22, 384, 94, 14, 0, 2, 6, 0, a(dark ? 0x1a0a1022 : 0x09767ca2));
        d.linearGradientRoundRect(
                x,
                y + 22,
                384,
                94,
                14,
                125,
                winter
                        ? new int[] {d.winterPaper().soft(), d.winterPaper().cover()}
                        : dark
                                ? new int[] {0x80364568, 0x73443655}
                                : new int[] {0x96eaf0ff, 0x8cf1eafe},
                new float[] {0, 1});
        d.insetBoxShadow(x, y + 22, 384, 94, 14, 0, 1, 2, 0, a(dark ? 0x2e0a1533 : 0x18a7acd7));
        d.strokeRoundRect(x + .5f, y + 22.5f, 383, 93, 14, 1, a(dark ? 0x2bb6c4e1 : 0xd9ffffff));
        int selected = welcomeIndex();
        float start = x + 64, trackY = y + 48;
        d.roundRect(
                start,
                trackY,
                256,
                6,
                4,
                a(
                        winter
                                ? d.winterPaper().ruling()
                                : dark
                                        ? 0xff57607b
                                        : (winter ? d.winterPaper().ruling() : 0xffd9dced)));
        if (selected > 0)
            d.linearGradientRoundRect(
                    start,
                    trackY,
                    selected * 128,
                    6,
                    4,
                    90,
                    winter
                            ? new int[] {d.winterPaper().wool(), d.winterPaper().pine()}
                            : dark
                                    ? new int[] {0xff8daee8, 0xffb595e8}
                                    : new int[] {
                                        (winter ? d.winterPaper().wool() : 0xff819de0),
                                        (winter ? d.winterPaper().pine() : 0xff9575da)
                                    },
                    new float[] {0, 1});
        for (int i = 0; i < 3; i++) {
            d.circle(start + i * 128, trackY + 3, 3, a(dark ? 0xffccd5ec : 0xffffffff));
            d.strokeRoundRect(
                    start + i * 128 - 3, trackY, 6, 6, 3, 1, a(dark ? 0xff64718f : 0xffbdc4dd));
        }
        float thumbX = start + selected * 128 - 11, thumbY = trackY - 8;
        d.boxShadow(thumbX, thumbY, 22, 22, 11, 0, 2, 6, 0, a(dark ? 0x660d153b : 0x406b61ae));
        d.linearGradientRoundRect(
                thumbX,
                thumbY,
                22,
                22,
                11,
                135,
                winter
                        ? new int[] {d.winterPaper().wool(), d.winterPaper().pine()}
                        : dark
                                ? new int[] {0xffa5c3f3, 0xffb194e3}
                                : new int[] {
                                    (winter ? d.winterPaper().wool() : 0xff96b4f1),
                                    (winter ? d.winterPaper().pine() : 0xff9172d8)
                                },
                new float[] {0, 1});
        d.strokeRoundRect(
                thumbX + 1.5f, thumbY + 1.5f, 19, 19, 9.5f, 3, a(dark ? 0xffd5ddf2 : 0xffffffff));
        hit("frequency", "开屏动画频率", start - 15, trackY - 12, 286, 30, () -> setWelcomeFromX(mx));
        String[] labels = {"仅初次", "每次登录", "每次打开"};
        for (int i = 0; i < 3; i++) {
            final int index = i;
            float bx = x + 5 + i * 125;
            if (i == selected) {
                d.boxShadow(bx, y + 74, 124, 28, 8, 0, 1, 3, 0, a(dark ? 0x2b0e1524 : 0x0c767ca2));
                d.roundRect(
                        bx,
                        y + 74,
                        124,
                        28,
                        8,
                        a(winter ? d.winterPaper().cover() : dark ? 0x1fc7b6ed : 0x8fffffff));
            }
            float hover =
                    transition(
                            "frequency-label-" + i + "-hover",
                            inside(bx, y + 74, 124, 28) ? 1 : 0,
                            180);
            if (hover > 0)
                d.roundRect(
                        bx,
                        y + 74,
                        124,
                        28,
                        8,
                        a(
                                withAlpha(
                                        winter
                                                ? withAlpha(d.winterPaper().sheet(), .36f)
                                                : dark ? 0x10d2c5ff : 0x66ffffff,
                                        hover)));
            textCenter(
                    labels[i],
                    bx + 62,
                    y + 93,
                    11,
                    winter
                            ? (i == selected ? d.winterPaper().pine() : d.winterPaper().muted())
                            : i == selected
                                    ? (dark ? 0xffd5bff9 : 0xff7056af)
                                    : mix(
                                            dark ? 0xffa5b0ca : 0xff81869e,
                                            dark ? 0xffd1bdf1 : 0xff67509d,
                                            hover),
                    i == selected);
            hit(
                    "frequency-label-" + i,
                    labels[i],
                    bx,
                    y + 74,
                    124,
                    28,
                    () -> welcomeMode = List.of("once", "login", "always").get(index));
        }
        d.literalColors(previousColors);
        String explanation =
                reducedMotion
                        ? "减少动态效果已开启，开屏动画会跳过。"
                        : switch (welcomeMode) {
                            case "once" -> "仅首次打开时播放；已经看过的记录会保留。";
                            case "login" -> "每次进入游戏后，首次打开手册时播放。";
                            default -> winter ? "每次打开手册时播放驿站亮灯动画。" : "每次打开手册时播放约 3 秒的手写问候。";
                        };
        wrap(
                explanation,
                x,
                y + 139,
                384,
                12,
                19.2f,
                dark ? 0xff9daac3 : (winter ? d.winterPaper().muted() : 0xff8c8fa5),
                false);
        d.line(x, y + 169, x + 384, y + 169, 1, (winter ? d.winterPaper().ruling() : 0xffe7e6f1));
        text("减少动态效果", x, y + 203, 14, winter ? d.winterPaper().ink() : INK, true);
        text(
                "关闭开屏动画、卡片倾斜与页面过渡",
                x,
                y + 229,
                12,
                (winter ? d.winterPaper().muted() : 0xff8c8fa5),
                false);
        toggle(
                "reduce-motion",
                localReducedMotion,
                x + 351,
                y + 190,
                () -> setReducedMotion(!localReducedMotion));
        d.line(x, y + 252, x + 384, y + 252, 1, (winter ? d.winterPaper().ruling() : 0xffe7e6f1));
        text("界面主题", x, y + 284, 14, winter ? d.winterPaper().ink() : INK, true);
        text(
                winter ? (dark ? "松绿夜色与暖纸灯光" : "松针绿与雪色纸面") : dark ? "深灰蓝紫玻璃" : "浅色液态玻璃",
                x,
                y + 309,
                12,
                (winter ? d.winterPaper().muted() : 0xff8c8fa5),
                false);
        float choiceX = x + 242, choiceY = y + 270;
        previousColors = d.literalColors(true);
        d.roundRect(
                choiceX,
                choiceY,
                142,
                42,
                12,
                a(winter ? d.winterPaper().soft() : dark ? 0xff20283b : 0xfff5f6ff));
        d.strokeRoundRect(
                choiceX + .5f, choiceY + .5f, 141, 41, 12, 1, a(dark ? 0x29a5b7e0 : 0xffdfe4ef));
        float themePosition = selectorPosition("theme-position", dark);
        if (themePosition != (dark ? 1 : 0)) d.keepThemeControlLive(choiceX, choiceY, 142, 42, 12);
        float thumbLeft = choiceX + 5 + 68 * themePosition;
        d.boxShadow(thumbLeft, choiceY + 5, 64, 32, 8, 0, 2, 7, 0, a(0x12303b64));
        d.roundRect(
                thumbLeft,
                choiceY + 5,
                64,
                32,
                8,
                a(winter ? d.winterPaper().sheet() : dark ? 0xff252d44 : 0xffffffff));
        for (int index = 0; index < 2; index++) {
            boolean chosen = (index == 1) == dark;
            float bx = choiceX + 5 + index * 68;
            int color =
                    winter
                            ? (chosen ? d.winterPaper().pine() : d.winterPaper().muted())
                            : chosen
                                    ? (dark ? 0xffa28ae7 : 0xff765bd4)
                                    : (dark ? 0xffaeb9d0 : 0xff6c7891);
            icon(index == 0 ? "sun" : "moon", bx + 9, choiceY + 13.5f, 15, color);
            text(index == 0 ? "浅色" : "深色", bx + 30, choiceY + 26, 11, color, false);
            final boolean chooseDark = index == 1;
            hit(
                    index == 0 ? "theme-light" : "theme-dark",
                    index == 0 ? "浅色主题" : "深色主题",
                    bx,
                    choiceY + 5,
                    64,
                    32,
                    () -> setDark(chooseDark));
        }
        d.literalColors(previousColors);
        d.line(x, y + 335, x + 384, y + 335, 1, (winter ? d.winterPaper().ruling() : 0xffe7e6f1));
        handbookStyleChoice(x, y + 341);
        d.line(
                x,
                y + 402,
                x + 384,
                y + 402,
                1,
                winter
                        ? d.winterPaper().ruling()
                        : (winter ? d.winterPaper().ruling() : 0xffe7e6f1));
        icon("info", x, y + 413, 15, (winter ? d.winterPaper().muted() : 0xff8c8fa5));
        wrap(
                live() ? "偏好按玩家与当前服务器／存档隔离保存。" : "偏好保存在当前测试实例；统计与收藏为本地演示。",
                x + 25,
                y + 425,
                359,
                11,
                18,
                (winter ? d.winterPaper().muted() : 0xff8c8fa5),
                false);
    }

    private String syncSidebarStatus() {
        boolean granted = HubModpackSync.granted();
        boolean enabled = HubModpackSync.enabled();
        boolean preview = "preview".equals(HubModpackSync.channel());
        if (!granted) return "未授权 · 点击设置";
        if (!enabled) return "自动同步已关闭";
        return preview ? "预览版 · 自动同步" : "正式版 · 自动同步";
    }

    private int syncSidebarDot(boolean winterMode) {
        boolean granted = HubModpackSync.granted();
        boolean enabled = HubModpackSync.enabled();
        boolean preview = "preview".equals(HubModpackSync.channel());
        if (!granted || !enabled) return dark ? 0xff6b768f : 0xff9aa3b2;
        if (preview) {
            if (winterMode) return d.winterPaper().mail();
            return dark ? 0xffb197fc : 0xff8a6ee3;
        }
        return winterMode ? d.winterPaper().pine() : 0xff6a9c8b;
    }

    private String modpackSyncSubtitle() {
        String pack = live() ? (data.packName.isBlank() ? connectionName : data.packName) : "香草纪元";
        String ver = live() ? data.packVersion : "2.7.1";
        return (ver.isBlank() ? pack : pack + " " + ver) + " · 管理自动同步与推送频道";
    }

    private void modpackSyncModal() {
        if (!HubModpackSync.available()) {
            dismissOverlay();
            return;
        }
        boolean enabled = HubModpackSync.enabled();
        boolean granted = HubModpackSync.granted();
        boolean preview = "preview".equals(HubModpackSync.channel());
        boolean active = enabled && granted;

        var p = d.winterPaper();
        float x = modalX + (winter ? 27 : 28);
        float w = modalW - (winter ? 54 : 56);
        float y = modalY + (winter ? 112 : 114);
        int ink = winter ? p.ink() : INK;
        int muted = winter ? p.muted() : (dark ? 0xff9daac3 : 0xff8c8fa5);
        int rule = winter ? p.ruling() : 0xffe7e6f1;

        // Row 1: Auto-sync switch
        text("自动同步整合包", x, y + 18, winter ? 13 : 14, ink, true);
        text(
                active ? "连接服务器时自动从可信 OSS 校验并暂存更新" : "已暂停自动同步，不会改动本地模组与配置",
                x,
                y + 42,
                winter ? 11 : 12,
                muted,
                false);
        toggle(
                "sync-enabled",
                active,
                x + w - 34,
                y + 10,
                () -> {
                    boolean next = !active;
                    if (!HubModpackSync.setEnabled(next)) {
                        feedbackError("同步配置写入失败，请稍后重试");
                        return;
                    }
                    if (next && !granted) {
                        feedbackSuccess("已开启自动同步，下次连接服务器时将确认授权");
                    } else {
                        feedbackSuccess(next ? "已开启整合包自动同步" : "已关闭整合包自动同步");
                    }
                });

        // Row 2: Channel segmented switch (正式版 / 预览版)
        float row2 = y + 66;
        d.line(x, row2, x + w, row2, 1, rule);
        text("推送频道", x, row2 + 30, winter ? 13 : 14, ink, true);
        text(
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
        float pos = selectorPosition("sync-channel-position", preview);
        if (winter) {
            HubWinterSheet.panel(d, segX, segY, segW, segH, 11, p.soft(), 1);
            if (pos != (preview ? 1 : 0)) d.keepThemeControlLive(segX, segY, segW, segH, 11);
            d.roundRect(segX + 4 + (cell + 4) * pos, segY + 4, cell, segH - 8, 8, p.sheet());
            for (int i = 0; i < 2; i++) {
                boolean isPrev = i == 1;
                String label = isPrev ? "预览版" : "正式版";
                float bx = segX + 4 + (cell + 4) * i;
                int color = preview == isPrev ? p.pine() : p.muted();
                textCenter(label, bx + cell / 2, segY + 24.5f, 12, color, preview == isPrev);
                hit(
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
            d.roundRect(segX, segY, segW, segH, 12, a(dark ? 0xff20283b : 0xfff5f6ff));
            d.strokeRoundRect(
                    segX + .5f,
                    segY + .5f,
                    segW - 1,
                    segH - 1,
                    12,
                    1,
                    a(dark ? 0x29a5b7e0 : 0xffdfe4ef));
            if (pos != (preview ? 1 : 0)) d.keepThemeControlLive(segX, segY, segW, segH, 12);
            float thumbLeft = segX + 4 + (cell + 4) * pos;
            d.boxShadow(thumbLeft, segY + 4, cell, segH - 8, 8, 0, 2, 7, 0, a(0x12303b64));
            d.roundRect(thumbLeft, segY + 4, cell, segH - 8, 8, a(dark ? 0xff252d44 : 0xffffffff));
            for (int i = 0; i < 2; i++) {
                boolean isPrev = i == 1;
                String label = isPrev ? "预览版" : "正式版";
                float bx = segX + 4 + (cell + 4) * i;
                int color =
                        preview == isPrev
                                ? (dark ? 0xffa28ae7 : 0xff765bd4)
                                : (dark ? 0xffaeb9d0 : 0xff6c7891);
                textCenter(label, bx + cell / 2, segY + 24.5f, 11.5f, color, preview == isPrev);
                hit(
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

        // Row 3: Local consent revocation
        float row3 = row2 + 76;
        d.line(x, row3, x + w, row3, 1, rule);
        text("本地同步授权", x, row3 + 30, winter ? 13 : 14, ink, true);
        text(
                granted ? "已授权 · 仅处理签名清单内的受管目录，不触碰存档" : "未授权 · 下次连接服务器时将弹出首次授权确认",
                x,
                row3 + 54,
                winter ? 11 : 12,
                muted,
                false);
        float btnW = 84, btnH = 30;
        float btnX = x + w - btnW, btnY = row3 + 22;
        if (granted) {
            boolean hovered = inside(btnX, btnY, btnW, btnH);
            boolean prevLiteral = d.literalColors(true);
            int bg =
                    winter
                            ? withAlpha(p.mail(), hovered ? .20f : .12f)
                            : dark
                                    ? (hovered ? 0x2eee8998 : 0x1cee8998)
                                    : (hovered ? 0x22d94c62 : 0x12d94c62);
            int border =
                    winter
                            ? withAlpha(p.mail(), .42f)
                            : dark ? (hovered ? 0x80ee8998 : 0x4dee8998) : 0x66d98290;
            int fg = winter ? p.mailInk() : dark ? 0xfff095a3 : 0xffb83d52;
            d.roundRect(btnX, btnY, btnW, btnH, 9, bg);
            d.strokeRoundRect(btnX + .5f, btnY + .5f, btnW - 1, btnH - 1, 9, 1, border);
            textCenter("撤销授权", btnX + btnW / 2, btnY + 19.5f, 11.5f, fg, false);
            d.literalColors(prevLiteral);
            hit(
                    "sync-revoke",
                    "撤销本地同步授权",
                    btnX,
                    btnY,
                    btnW,
                    btnH,
                    () -> {
                        if (HubModpackSync.revokeAll()) {
                            feedback("已撤销本地同步授权并暂停自动同步");
                        } else {
                            feedbackError("撤销失败，请检查本地配置权限");
                        }
                    });
        } else {
            boolean prevLiteral = d.literalColors(true);
            int bg = winter ? p.soft() : (dark ? 0x24ffffff : 0x14708098);
            d.roundRect(btnX, btnY, btnW, btnH, 9, bg);
            textCenter("未授权", btnX + btnW / 2, btnY + 19.5f, 11.5f, muted, false);
            d.literalColors(prevLiteral);
        }

        // Bottom info note
        float noteY = row3 + 78;
        d.line(x, noteY, x + w, noteY, 1, rule);
        icon("info", x, noteY + 15, 15, muted);
        text("修改会写入本地客户端同步配置，重新连接服务器后自动生效。", x + 24, noteY + 27, 11, muted, false);
    }

    private void switchSyncChannel(boolean toPreview, boolean currentPreview) {
        if (toPreview == currentPreview) return;
        if (!HubModpackSync.setChannel(toPreview ? "preview" : "stable")) {
            feedbackError("频道切换失败，请稍后重试");
            return;
        }
        feedbackSuccess(toPreview ? "已切换为预览版，下次连接服务器时生效" : "已切回正式版，下次连服将自动安全回退");
    }

    private java.util.function.Consumer<String> copyToClipboard;

    public void onCopyToClipboard(java.util.function.Consumer<String> copy) {
        copyToClipboard = copy;
    }

    private void profileModal() {
        float cx = 630, top = modalY + 115;
        if (winter) {
            var p = d.winterPaper();
            HubHead.draw(d, modalX + 33, modalY + 119, 48, -23);
            text(fit(playerName, 304, 22, true), modalX + 106, modalY + 141, 22, p.ink(), true);
            HubData.Title worn = data.title(equipped);
            if (worn == null) text("暂未佩戴称号", modalX + 106, modalY + 167, 14, p.muted(), false);
            else titleLabel(worn, modalX + 106, modalY + 167, 14, 1, false, false, 298);
        } else {
            glass(cx - 46, top, 92, 92, 24, 0xffe5ecff, 0xffeae1ff);
            HubHead.draw(d, cx - 27, top + 21, 54, -23);
            textCenter(
                    fit(playerName, 480, 24, true),
                    cx,
                    top + 130,
                    24,
                    winter ? d.winterPaper().ink() : INK,
                    true);
            HubData.Title worn = data.title(equipped);
            if (worn == null)
                textCenter(
                        "暂未佩戴称号",
                        cx,
                        top + 160,
                        14,
                        winter ? d.winterPaper().muted() : MUTED,
                        true);
            else titleLabel(worn, cx, top + 160, 14, 1.4f, true, false);
        }
        String[][] rows =
                live()
                        ? new String[][] {
                            {"首次加入", timestamp(data.firstJoinedAt, "yyyy/MM/dd HH:mm:ss")},
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
                            {"玩家 UUID", selfPlayerId().isEmpty() ? "正在读取" : selfPlayerId()}
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
                text(rows[i][0], modalX + 26, line + 23, 12, p.muted(), false);
                if (i < 3) textRight(rows[i][1], modalX + 414, line + 23, 13, p.ink(), false);
                else {
                    text(rows[i][1], modalX + 26, line + 50, 13, p.ink(), false);
                    String uuid = rows[i][1];
                    if (uuid.length() == 36 && copyToClipboard != null)
                        iconButton(
                                "account-copy",
                                "复制 UUID",
                                "copy",
                                modalX + 378,
                                line + 30,
                                28,
                                () -> {
                                    copyToClipboard.accept(uuid);
                                    feedbackSuccess("UUID 已复制");
                                },
                                true);
                }
            }
            return;
        }
        for (int i = 0; i < rows.length; i++) {
            float y = top + 207 + i * 48;
            d.line(
                    modalX + 28,
                    y - 21,
                    modalX + modalW - 28,
                    y - 21,
                    1,
                    (winter ? d.winterPaper().ruling() : 0xffe7e6f1));
            text(
                    rows[i][0],
                    modalX + 28,
                    y,
                    11,
                    (winter ? d.winterPaper().muted() : 0xff8c8fa5),
                    false);
            textRight(
                    rows[i][1],
                    modalX + modalW - 28,
                    y,
                    i == 3 ? 10 : 12,
                    (winter ? d.winterPaper().ink() : 0xff59657f),
                    false);
        }
        textCenter(
                live() ? "统计来自当前服务器／存档。" : "名字与头像取自本地角色，其他资料均为样例。",
                cx,
                modalY + modalH - 28,
                11,
                MUTED,
                false);
    }

    private void previewModal() {
        float x = modalX + 28, y = modalY + 120;
        text("今日抽签状态", x, y, 13, INK, true);
        String[] labels = {"尚未抽签", "等待解签", "已经解签", "已收进藏签匣"};
        for (int i = 0; i < 4; i++) {
            final int state = i;
            float bx = x + (i % 2) * 197, by = y + 22 + (i / 2) * 55;
            button(
                    "preview-state-" + i,
                    (fortuneState == i ? "✓ " : "") + labels[i],
                    bx,
                    by,
                    187,
                    42,
                    fortuneState == i,
                    true,
                    () -> {
                        fortuneState = state;
                        notify("今日状态：" + stateLabel() + " · 本地演示");
                    });
        }
        wrap("未解签不会显示结果；已解但未归档的签不会出现在收藏和月历中。", x, y + 164, 384, 12, 22, 0xff8c8fa5, false);
        button(
                "reset-demo",
                "还原演示数据",
                x,
                y + 236,
                148,
                37,
                false,
                true,
                () -> {
                    equipped = "dew";
                    visible = true;
                    fortuneState = 3;
                    displayDate = data.today;
                    selectedDate = data.today;
                    rankings.demo();
                    notify("样例已还原，界面偏好保留");
                });
        button(
                "ranking-demo",
                "播放排行变动",
                x + 164,
                y + 236,
                220,
                37,
                false,
                true,
                () -> {
                    dismissOverlay();
                    navigate(4);
                    rankingsPreviewAt = System.nanoTime() + 500_000_000L;
                });
        text("F8 打开此面板 · 界面偏好不参与演示还原", x, y + 295, 11, MUTED, false);
    }

    private void fortuneHelp() {
        float x = modalX + 30, y = modalY + 125;
        image(fortuneState == 0 ? "fortune-barrel" : "fortune-slip", x, y, 94, 106, 1);
        wrap(
                fortuneState == 0 ? "拿起签筒，为今天留一份期许。" : "带上未解的签，前往解签龛。",
                x + 119,
                y + 20,
                235,
                13,
                21,
                0xff807789,
                true);
        wrap(
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
        text(
                live() ? "请在游戏内完成抽签、解签与归档。" : "这里是本地界面原型，不会触发游戏抽签。",
                x,
                modalY + modalH - 40,
                11,
                0xff9992a4,
                false);
    }

    private void readingModal() {
        HubData.Entry entry = reading;
        HubData.Fortune f = entry.fortune();
        float x = modalX + 26, y = modalY + 121, w = 319.5f, h = modalH - 147;
        var paper = d.winterPaper();
        boolean winterColors = winter && d.literalColors(true);
        if (winter) HubWinterSheet.letter(d, x, y, w, h, false, opacity);
        else {
            d.linearGradientRoundRect(
                    x, y, w, h, 6, 110, new int[] {0xfff7f0dd, 0xfffffaf0}, new float[] {0, 1});
            d.strokeRoundRect(x, y, w, h, 6, 1, 0xffe6dcc1);
            d.boxShadow(x, y, w, h, 6, 0, 6, 16, 0, 0x12867955);
        }
        trackedCenter(
                "第" + f.id() + "签 · " + f.grade(),
                x + w / 2,
                y + 39,
                11,
                .5f,
                HubData.gradeColor(f.grade()),
                false,
                false);
        trackedCenter(
                f.title(), x + w / 2, y + 89, 29, 4, winter ? paper.ink() : 0xff68604c, true, true);
        for (int i = 0; i < f.poem().size(); i++)
            trackedCenter(
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
        push(Matrix33.makeRotate(-5, x + w / 2, y + 292));
        beginAlpha(.7f, x + w / 2 - 11, y + 281, 22, 22);
        d.strokeRoundRect(
                x + w / 2 - 11, y + 281, 22, 22, 0, 1, winter ? paper.mail() : 0xffb58b6b);
        serifCenter("签", x + w / 2, y + 297, 12, winter ? paper.mailInk() : 0xffb08767, false);
        endAlpha();
        pop();
        trackedCenter(
                "DAILY FORTUNES",
                x + w / 2,
                y + 333,
                10,
                2,
                winter ? paper.muted() : 0xffc1b693,
                false,
                false);
        if (winter) HubWinterSheet.illustration(d, "sleeping-fox", x, y + 341, w, 31, opacity);
        float rx = x + w + 29;
        icon("leaf", rx, y + 8, 15, winter ? paper.muted() : 0xff8f7da7);
        tracked("解签", rx + 23, y + 21, 12, 2, winter ? paper.muted() : 0xff8f7da7, 500, false);
        trackedWrap(
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
        icon("bookmark", rx, y + h - 97, 16, winter ? paper.muted() : 0xff8f91a2);
        text(
                archived ? "已收藏于你的藏签匣" : "已解签，尚未收藏",
                rx + 24,
                y + h - 84,
                10,
                winter ? paper.muted() : 0xff8f91a2,
                false);
        text(
                archived ? "签笺一经收藏，便成为旅途的一部分。" : "在游戏中打开藏签匣，即可收存这张签。",
                rx + 24,
                y + h - 66,
                10,
                winter ? paper.muted() : 0xff9997aa,
                false);
        boolean current = Objects.equals(displayDate, entry.date());
        button(
                "reading-display",
                !archived ? "收藏后可设为展示" : current ? "当前展示的签笺" : "设为手册展示  ✧",
                rx,
                y + h - 45,
                w,
                37,
                true,
                archived && (winter || !current),
                () -> {
                    if (current) notify("手册正在展示这张签笺");
                    else displaySelected(entry);
                });
        if (winter) d.literalColors(winterColors);
    }

    private static final String HELLO_PATH =
            "M24 111 C42 99 63 77 72 48 C83 14 67 11 58 29 C48 49 49 86 41 121 C48 94 60 77 73 81 C92 88 64 127 88 124 C101 122 111 111 120 101 C139 80 143 95 128 104 C99 122 112 139 139 123 C160 111 176 80 185 48 C194 13 177 12 170 33 C162 57 159 100 157 113 C154 140 178 126 194 105 C211 82 222 51 224 32 C228 11 210 14 206 38 C201 65 196 102 199 116 C201 136 220 125 235 108 C253 85 268 91 262 112 C256 136 231 136 229 116 C227 98 243 87 255 96 C270 107 284 109 303 93";
    private static final String FLOURISH_PATH =
            "M97 145 C153 133 229 134 283 142 C302 145 316 143 329 136";

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

    private void group(int delayMs, int durationMs) {
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

    private void ungroup() {
        pop();
        endAlpha();
        opacity = 1;
        offsetY = 0;
    }

    private HubMotion.Tilt beginTilt(
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

    private void endTilt(
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

    private void push(Matrix33 matrix) {
        matrixStack.push(logical);
        logical = logical.makeConcat(matrix);
        d.save();
        d.canvas().concat(matrix);
    }

    private void pop() {
        d.restore();
        logical = matrixStack.pop();
    }

    private void beginAlpha(float alpha) {
        alphaStack.push(interactiveAlpha);
        interactiveAlpha *= alpha;
        d.saveLayerAlpha(-128, -128, 1516, 1106, alpha);
    }

    private void beginAlpha(float alpha, float x, float y, float w, float h) {
        alphaStack.push(interactiveAlpha);
        interactiveAlpha *= alpha;
        d.saveLayerAlpha(x, y, w, h, alpha);
    }

    private void endAlpha() {
        d.restore();
        interactiveAlpha = alphaStack.pop();
    }

    private float transition(String id, float target, int milliseconds) {
        return transitions
                .computeIfAbsent(id, ignored -> new HubMotion.Transition())
                .sample(target, now, milliseconds * 1_000_000L, reducedMotion);
    }

    private float stateTransition(String id, float target, int milliseconds) {
        return stateTransition(id, target, milliseconds, false);
    }

    private float selectorPosition(String id, boolean second) {
        return stateTransition(id, second ? 1 : 0, 220, true);
    }

    private float stateTransition(String id, float target, int milliseconds, boolean easeOut) {
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

    private void shadow(float x, float y, float w, float h, float r, int color) {
        d.boxShadow(x, y, w, h, r, 0, 6, 22, 0, color);
    }

    private void glass(float x, float y, float w, float h, float radius, int from, int to) {
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

    private void text(String s, float x, float baseline, float size, int color, boolean bold) {
        d.text(s, x, baseline, size, a(color), bold ? 600 : 400);
    }

    private void trackedCenter(
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

    private float trackedWidth(String s, float size, float tracking, int weight, boolean serif) {
        return d.textWidthTracked(s, size, weight, tracking, serif);
    }

    private void tracked(
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

    private String fitTracked(
            String s, float width, float size, float tracking, int weight, boolean serif) {
        if (trackedWidth(s, size, tracking, weight, serif) <= width) return s;
        int end = s.length();
        while (end > 0
                && trackedWidth(s.substring(0, end) + "…", size, tracking, weight, serif) > width)
            end = s.offsetByCodePoints(end, -1);
        return s.substring(0, end) + "…";
    }

    private void titleLabel(
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

    private float titleLabel(
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

    private float titleLabel(
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

    private void paintDemoTitle(
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

    private float richWidth(List<HubData.Glyph> glyphs, float size, float tracking) {
        return richWidth(glyphs, size, tracking, false, 400);
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

    private void titleNameModal() {
        HubData.Title title = data.title(selectedTitle);
        if (title == null) return;
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

    private void titleDisplayModal() {
        String[] keys = {"head", "chat", "tab"}, labels = {"头顶称号", "聊天称号", "玩家列表称号"};
        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            float y = modalY + 122 + i * 66;
            boolean allowed = HubData.bool(data.globalDisplay, key, true),
                    shown = HubData.bool(data.displayPreferences, key, true);
            text(labels[i], modalX + 28, y, 14, INK, true);
            text(allowed ? "使用个人展示偏好" : "管理员已关闭此位置", modalX + 28, y + 22, 11, MUTED, false);
            toggle(
                    "title-location-" + key,
                    shown,
                    modalX + modalW - 61,
                    y - 12,
                    () -> {
                        if (pendingModules.contains("titles")) {
                            notify("正在等待服务器确认");
                            return;
                        }
                        JsonObject p = mutation("titles");
                        for (String location : keys)
                            p.addProperty(
                                    location,
                                    location.equals(key)
                                            ? !shown
                                            : HubData.bool(
                                                    data.displayPreferences, location, true));
                        request("titles", "displayPreferences", p);
                    });
        }
    }

    private List<String> trackedLines(
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

    private void trackedWrap(
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

    private void serif(String s, float x, float baseline, float size, int color, boolean bold) {
        d.textSerif(s, x, baseline, size, a(color), bold);
    }

    private void serifCenter(
            String s, float x, float baseline, float size, int color, boolean bold) {
        serif(s, x - d.textWidthSerif(s, size, bold) / 2, baseline, size, color, bold);
    }

    private void textCenter(
            String s, float x, float baseline, float size, int color, boolean bold) {
        text(s, x - d.textWidth(s, size, bold ? 600 : 400) / 2, baseline, size, color, bold);
    }

    private void textRight(
            String s, float right, float baseline, float size, int color, boolean bold) {
        text(s, right - d.textWidth(s, size, bold ? 600 : 400), baseline, size, color, bold);
    }

    private void icon(String name, float x, float y, float size, int color) {
        d.icon(name, x, y, size, a(color));
    }

    private void image(String name, float x, float y, float w, float h, float alpha) {
        d.image("textures/ui/" + name + ".png", x, y, w, h, alpha * opacity);
    }

    private String fit(String s, float width, float size, boolean bold) {
        if (d.textWidth(s, size, bold ? 600 : 400) <= width) return s;
        int length = s.length();
        while (length > 0
                && d.textWidth(s.substring(0, length) + "…", size, bold ? 600 : 400) > width)
            length--;
        return s.substring(0, length) + "…";
    }

    private int wrap(
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

    private void pill(String label, float x, float y, int background, int color, boolean dot) {
        float w = d.textWidth(label, 10, false) + (dot ? 24 : 17);
        d.roundRect(x, y, w, 22, 11, a(background));
        d.strokeRoundRect(x, y, w, 22, 11, 1, a(0xb3ffffff));
        if (dot) d.circle(x + 9, y + 11, 2, a(color));
        text(label, x + (dot ? 17 : 8), y + 15, 10, color, false);
    }

    private void button(
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

    private void button(
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

    private void goldButton(
            String id, String label, float x, float y, float w, float h, Runnable click) {
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

    private void textButton(
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

    private void iconButton(
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

    private void toggle(String id, boolean checked, float x, float y, Runnable click) {
        toggle(id, checked, x, y, click, true);
    }

    private void toggle(
            String id, boolean checked, float x, float y, Runnable click, boolean enabled) {
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

    private void hit(String id, String label, float x, float y, float w, float h, Runnable click) {
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

    private boolean inside(float x, float y, float w, float h) {
        float[] p = HubDraw.inversePoint(logical, mx, my);
        return p[0] >= x && p[1] >= y && p[0] < x + w && p[1] < y + h;
    }

    private int a(int color) {
        return withAlpha(color, opacity);
    }

    private static int withAlpha(int color, float alpha) {
        return (Math.round((color >>> 24) * clamp(alpha, 0, 1)) << 24) | (color & 0xffffff);
    }

    private static int lighten(int color, float amount) {
        return amount >= 0 ? mix(color, 0xffffffff, amount) : mix(color, 0xff26344f, -amount);
    }

    private static int desaturate(int color, float saturation) {
        int r = (color >>> 16) & 255, g = (color >>> 8) & 255, b = color & 255;
        int grey = Math.round(.2126f * r + .7152f * g + .0722f * b);
        return mix((color & 0xff000000) | (grey << 16) | (grey << 8) | grey, color, saturation);
    }

    private static int mix(int from, int to, float fraction) {
        float f = clamp(fraction, 0, 1);
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int start = (from >>> shift) & 255, end = (to >>> shift) & 255;
            result |= Math.round(start + (end - start) * f) << shift;
        }
        return result;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float progress(long elapsed, long duration) {
        return clamp((float) elapsed / duration, 0, 1);
    }

    /** CSS color-mix(in srgb) interpolates premultiplied alpha. */
    private static int cssMix(int from, int to, float amount) {
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

    private static float ease(float p) {
        return 1 - (1 - p) * (1 - p) * (1 - p);
    }

    private static String shortDate(LocalDate date) {
        return String.format(Locale.ROOT, "%02d/%02d", date.getMonthValue(), date.getDayOfMonth());
    }

    private String stateLabel() {
        return List.of("尚未抽签", "等待解签", "已经解签", "已收进藏签匣").get(fortuneState);
    }

    private void setReducedMotion(boolean value) {
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
        for (HubMotion.Navigation state : navigation) {
            state.enter.snap(value ? 1 : 0);
            state.hover.snap(0);
            state.press.snap(0);
            state.x.snap(0);
            state.y.snap(0);
        }
    }

    private int welcomeIndex() {
        return Math.max(0, List.of("once", "login", "always").indexOf(welcomeMode));
    }

    private void notify(String value) {
        toast.show(value, HubToast.Kind.INFO, System.nanoTime());
    }

    private void navigate(int destination) {
        if (page == destination) return;
        titleSortOpen = false;
        if (destination != 5) memories.deactivate();
        if (destination == 3) updates.activate();
        else updates.deactivate();
        if (destination == 4) rankings.activate();
        else rankings.deactivate();
        if ((destination == 0 || destination == 1) && !hasSnapshot("titles"))
            request("titles", "snapshot", new JsonObject());
        if (destination == 0 && !hasSnapshot("fortunes"))
            request("fortunes", "snapshot", new JsonObject());
        if (destination == 2) calendarRequest();
        page = destination;
        pageAt = System.nanoTime();
        focusId = null;
        titleScroll = 0;
        if (destination == 1) {
            lastAlbumTitle = null;
            albumStatus = null;
        }
        if (destination == 2) {
            monthAt = pageAt;
            unfoldedDate = null;
        }
        if (destination == 0) tilts.remove("medallion");
    }

    private void openOverlay(String value) {
        restoreFocus = focusId;
        overlay = value;
        reading = null;
        focusId = "modal-close";
        sliderDragging = false;
        modalAt = System.nanoTime();
        hits.clear();
    }

    private void openReading(HubData.Entry value) {
        if (value != null) showReading(value);
    }

    private void showReading(HubData.Entry value) {
        restoreFocus = focusId;
        readingScroll = 0;
        reading = value;
        overlay = null;
        focusId = "modal-close";
        modalAt = System.nanoTime();
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
        if (page != 5) return false;
        leaveMemories();
        return true;
    }

    public boolean dismissOverlay() {
        if (profiles.isOpen()) {
            profiles.requestClose();
            return true;
        }
        if (page == 5 && overlay == null && reading == null && memories.dismiss()) return true;
        if (page == 3
                && !profiles.isOpen()
                && overlay == null
                && reading == null
                && updates.dismiss()) return true;
        if (winter && page == 1 && titleSortOpen) {
            titleSortOpen = false;
            focusId = "sort";
            return true;
        }
        if (overlay == null && reading == null) return false;
        overlay = null;
        reading = null;
        sliderDragging = false;
        focusId = restoreFocus;
        restoreFocus = null;
        hits.clear();
        return true;
    }

    private void setWelcomeFromX(float x) {
        float[] p = HubDraw.inversePoint(sliderTransform, x, my);
        int index = Math.round(clamp((p[0] - frequencyStart()) / frequencyStep(), 0, 2));
        welcomeMode = List.of("once", "login", "always").get(index);
    }

    public boolean mouseClicked(float x, float y, int button) {
        return mouseClicked(x, y, button, false);
    }

    public boolean mouseClicked(float x, float y, int button, boolean shift) {
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
        if (page == 5 && !profiles.isOpen() && overlay == null && reading == null) {
            float[] point = HubDraw.inversePoint(pageTransform, x, y);
            if (memories.pointerDown(point[0], point[1], shift)) {
                pressedId = null;
                pressedHit = null;
                return true;
            }
        }
        if (page == 3 && !profiles.isOpen() && overlay == null && reading == null) {
            float[] point = HubDraw.inversePoint(pageTransform, x, y);
            if (updates.pointerDown(point[0], point[1], shift)) {
                pressedId = null;
                pressedHit = null;
                return true;
            }
        }
        if (page == 4 && !profiles.isOpen() && overlay == null && reading == null) {
            float[] point = HubDraw.inversePoint(pageTransform, x, y);
            if (rankings.pointerDown(point[0], point[1])) {
                pressedId = null;
                pressedHit = null;
                return true;
            }
        }
        if (winter && page == 1 && titleSortOpen) {
            boolean insideMenu =
                    hits.stream()
                            .anyMatch(
                                    hit ->
                                            (hit.id().equals("sort")
                                                            || hit.id().startsWith("sort-option-"))
                                                    && hit.contains(x, y));
            if (!insideMenu) titleSortOpen = false;
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (!hit.contains(x, y)) continue;
            focusId = hit.id();
            pressedId = hit.id();
            pressedHit = hit;
            sliderDragging = "frequency".equals(hit.id());
            sliderTransform = hit.transform();
            if (sliderDragging || "search".equals(hit.id()) || updates.textFocused(hit.id()))
                hit.action().run();
            return true;
        }
        if (profiles.isOpen()) return true;
        if (overlay != null || reading != null) {
            if (x < modalX || x > modalX + modalW || y < modalY || y > modalY + modalH)
                dismissOverlay();
            return true;
        }
        focusId = null;
        return true;
    }

    public boolean mouseReleased(float x, float y, int button) {
        if (button != 0) return false;
        if (toast.release(x, y, System.nanoTime())) return true;
        if (profiles.isOpen() && profiles.pointerUp()) {
            pressedId = null;
            pressedHit = null;
            return true;
        }
        if (page == 5 && !profiles.isOpen() && memories.pointerUp()) {
            pressedId = null;
            pressedHit = null;
            return true;
        }
        if (page == 4 && !profiles.isOpen() && rankings.pointerUp()) {
            pressedId = null;
            pressedHit = null;
            return true;
        }
        if (page == 3 && !profiles.isOpen() && overlay == null && reading == null) {
            float[] point = HubDraw.inversePoint(pageTransform, x, y);
            updates.pointerAt(point[0], point[1]);
            if (updates.pointerUp()) {
                pressedId = null;
                pressedHit = null;
                return true;
            }
        }
        boolean active = sliderDragging || pressedHit != null;
        Hit released =
                pressedHit == null
                        ? null
                        : hits.stream()
                                .filter(hit -> hit.id().equals(pressedHit.id()))
                                .findFirst()
                                .orElse(null);
        if (released != null
                && !sliderDragging
                && !"search".equals(released.id())
                && !updates.textFocused(released.id())
                && released.contains(x, y)
                && (!released.id().startsWith("title-") || !pressMoved)) released.action().run();
        sliderDragging = false;
        pressedId = null;
        pressedHit = null;
        return active;
    }

    public boolean mouseDragged(float x, float y, int button, float dx, float dy) {
        mx = x;
        my = y;
        if (button == 0 && toast.drag(y, System.nanoTime())) return true;
        if (button == 0 && profiles.isOpen() && profiles.pointerDrag(x, y)) return true;
        if (button == 0 && page == 5 && !profiles.isOpen() && overlay == null && reading == null) {
            float[] point = HubDraw.inversePoint(pageTransform, x, y);
            if (memories.pointerDrag(point[0], point[1])) return true;
        }
        if (button == 0 && page == 3 && !profiles.isOpen() && overlay == null && reading == null) {
            float[] point = HubDraw.inversePoint(pageTransform, x, y);
            if (updates.pointerDrag(point[0], point[1])) return true;
        }
        if (button == 0 && page == 4 && !profiles.isOpen() && overlay == null && reading == null) {
            float[] point = HubDraw.inversePoint(pageTransform, x, y);
            if (rankings.pointerDrag(point[1])) return true;
        }
        if (button == 0 && pressedId != null && Math.hypot(x - pressX, y - pressY) > 6)
            pressMoved = true;
        if (sliderDragging && button == 0) {
            setWelcomeFromX(x);
            return true;
        }
        return pressedId != null;
    }

    public boolean mouseScrolled(float x, float y, double amount) {
        if (profiles.isOpen()) {
            profiles.scroll(x, y, amount);
            return true;
        }
        if (winter
                && reading != null
                && x >= modalX
                && x <= modalX + modalW
                && y >= modalY + 88
                && y <= modalY + modalH) {
            readingScroll = clamp(readingScroll - (float) amount * 46, 0, readingOverflow);
            return true;
        }
        if (isWelcomePlaying() || overlay != null || reading != null) return true;
        float[] p = HubDraw.inversePoint(pageTransform, x, y);
        if (page == 5) return memories.scroll(p[0], p[1], amount);
        if (page == 3) return updates.scroll(p[0], p[1], amount);
        if (page == 4) return rankings.scroll(p[0], p[1], amount);
        if (winter && page == 1) {
            if (p[0] >= ALBUM_SPLIT
                    && p[0] <= COLLECTION_LEFT + COLLECTION_WIDTH
                    && p[1] >= ALBUM_SPREAD
                    && p[1] < ALBUM_END - 90
                    && albumRecordOverflow > 0) {
                albumRecordScroll =
                        clamp(albumRecordScroll - (float) amount * 46, 0, albumRecordOverflow);
                return true;
            }
            return false;
        }
        if (winter && page == 2) {
            if (history
                    && p[0] >= MONTH_X
                    && p[0] < MONTH_RIGHT
                    && p[1] >= MONTH_GRID
                    && p[1] < MONTH_END) {
                historyScroll -= amount * 42;
                return true;
            }
            return false;
        }
        if (page == 1 && p[0] >= 230 && p[0] <= 908 && p[1] >= 272 && p[1] <= 796) {
            titleScroll -= amount * 52;
            return true;
        }
        if (page == 2 && history && p[0] >= 254 && p[0] <= 898 && p[1] >= 252 && p[1] <= 746) {
            historyScroll -= amount * 48;
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) return escape();
        if (isWelcomePlaying()) return true;
        if (winter && reading != null && readingOverflow > 0) {
            switch (keyCode) {
                case 264 -> readingScroll = Math.min(readingOverflow, readingScroll + 32);
                case 265 -> readingScroll = Math.max(0, readingScroll - 32);
                case 267 -> readingScroll = Math.min(readingOverflow, readingScroll + modalH - 110);
                case 266 -> readingScroll = Math.max(0, readingScroll - modalH + 110);
                case 269 -> readingScroll = readingOverflow;
                case 268 -> readingScroll = 0;
                default -> {}
            }
            if (keyCode >= 264 && keyCode <= 269) return true;
        }
        if (keyCode == 297 && !live()) {
            if (overlay == null && reading == null) openOverlay("preview");
            else if ("preview".equals(overlay)) dismissOverlay();
            return true;
        }
        if (winter && page == 1 && titleSortOpen) {
            if (keyCode == 258) {
                titleSortOpen = false;
                focusId = "sort";
            } else if (keyCode == 264 || keyCode == 265 || keyCode == 268 || keyCode == 269) {
                keyboardFocus = true;
                focusId = "sort-option-" + (keyCode == 264 || keyCode == 269 ? 1 : 0);
                return true;
            }
        }
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
        if (page == 5
                && !profiles.isOpen()
                && overlay == null
                && reading == null
                && memories.key(keyCode, modifiers, focusId)) return true;
        if (page == 4
                && !profiles.isOpen()
                && overlay == null
                && reading == null
                && rankings.key(keyCode, focusId)) return true;
        if (page == 3
                && !profiles.isOpen()
                && overlay == null
                && reading == null
                && updates.key(keyCode, modifiers, focusId)) return true;
        if ("search".equals(focusId)
                && page == 1
                && !profiles.isOpen()
                && overlay == null
                && reading == null) {
            if ((keyCode == 259 || keyCode == 261) && searchSelectAll) {
                query = "";
                searchCaret = 0;
                searchSelectAll = false;
                titleScroll = 0;
                return true;
            }
            if (keyCode == 259 && searchCaret > 0) {
                int at = query.offsetByCodePoints(searchCaret, -1);
                query = query.substring(0, at) + query.substring(searchCaret);
                searchCaret = at;
                titleScroll = 0;
                return true;
            }
            if (keyCode == 261 && searchCaret < query.length()) {
                int end = query.offsetByCodePoints(searchCaret, 1);
                query = query.substring(0, searchCaret) + query.substring(end);
                titleScroll = 0;
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
            // Text editing consumes navigation keys even when the caret cannot move.
            if (keyCode == 259
                    || keyCode == 261
                    || keyCode == 263
                    || keyCode == 262
                    || keyCode == 268
                    || keyCode == 269) return true;
        }
        if ("frequency".equals(focusId) && "settings".equals(overlay)) {
            int index = welcomeIndex();
            if (keyCode == 263 || keyCode == 264) index--;
            else if (keyCode == 262 || keyCode == 265) index++;
            else if (keyCode == 268) index = 0;
            else if (keyCode == 269) index = 2;
            else index = -1;
            if (index >= 0) {
                welcomeMode = List.of("once", "login", "always").get(Math.min(2, index));
                return true;
            }
            if (keyCode == 263 || keyCode == 264) return true;
        }
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
        if (textInputFocused() && !Character.isISOControl(codePoint) && (modifiers & 6) == 0) {
            if (profiles.isOpen()) {
                profiles.type(codePoint, focusId);
                return true;
            }
            if (page == 5) {
                memories.type(codePoint, focusId);
                return true;
            }
            if (page == 3) {
                updates.type(codePoint, focusId);
                return true;
            }
            pasteText(Character.toString(codePoint));
            return true;
        }
        return overlay != null || reading != null;
    }

    public boolean textInputFocused() {
        if (isWelcomePlaying()) return false;
        if (profiles.isOpen()) return profiles.textFocused(focusId);
        return overlay == null
                && reading == null
                && (page == 1 && "search".equals(focusId)
                        || page == 3 && updates.textFocused(focusId)
                        || page == 5 && memories.textFocused(focusId));
    }

    public String selectedInputText() {
        if (profiles.isOpen()) return profiles.selectedText(focusId);
        if (page == 5) return memories.selectedText(focusId);
        if (page == 3) return updates.selectedText(focusId);
        return page == 1 && "search".equals(focusId) && searchSelectAll ? query : "";
    }

    public String cutInputText() {
        if (profiles.isOpen()) return profiles.cutText(focusId);
        if (page == 5) return memories.cutText(focusId);
        if (page == 3) return updates.cutText(focusId);
        String value = selectedInputText();
        if (!value.isEmpty()) {
            query = "";
            searchCaret = 0;
            searchSelectAll = false;
        }
        return value;
    }

    public void panelVisibility(boolean open) {
        if (!open) {
            updates.deactivate();
            rankings.deactivate();
            memories.deactivate();
        } else if (page == 4) rankings.activate();
    }

    public String pointerCursor() {
        if (toast.pointer(mx, my)) return "pointer";
        if (isWelcomePlaying()) return "default";
        if (page == 4 && !profiles.isOpen() && overlay == null && reading == null) {
            float[] point = HubDraw.inversePoint(pageTransform, mx, my);
            if (rankings.pointerOnRail(point[0], point[1])) return "pointer";
        }
        if (page == 3 && !profiles.isOpen() && overlay == null && reading == null) {
            float[] point = HubDraw.inversePoint(pageTransform, mx, my);
            if (updates.pointerOnRail(point[0], point[1])) return "pointer";
        }
        for (int i = hits.size() - 1; i >= 0; i--)
            if (hits.get(i).contains(mx, my))
                return (hits.get(i).id().equals("search")
                                || updates.textFocused(hits.get(i).id())
                                || profiles.textFocused(hits.get(i).id())
                                || memories.textFocused(hits.get(i).id()))
                        ? "text"
                        : "pointer";
        return "default";
    }

    /** Clipboard access belongs to the Minecraft screen; only sanitized text enters this UI. */
    public void pasteText(String value) {
        if (!textInputFocused() || value == null) return;
        if (profiles.isOpen()) {
            profiles.paste(value, focusId);
            return;
        }
        if (page == 5) {
            memories.paste(value, focusId);
            return;
        }
        if (page == 3) {
            updates.paste(value, focusId);
            return;
        }
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
        titleScroll = 0;
    }
}
