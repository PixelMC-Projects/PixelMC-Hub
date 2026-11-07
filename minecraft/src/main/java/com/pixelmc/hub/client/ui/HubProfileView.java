/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.pixelmc.hub.client.render.HubDraw;
import io.github.humbleui.skija.Matrix33;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontMgr;
import io.github.humbleui.skija.FontStyle;
import io.github.humbleui.skija.Typeface;
import io.github.humbleui.skija.TextLine;
import io.github.humbleui.types.Rect;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Native, connection-scoped counterpart of PlayerProfileCard and its bounded modal. */
public final class HubProfileView implements AutoCloseable {
    public interface Host {
        void hit(String id, String label, float x, float y, float width, float height, Runnable action);
        void request(String action, JsonObject payload);
        void feedback(String message, boolean error);
        void focus(String id);
        void openMemories(String playerId, String archiveId);
        void closeProfile();
        void openAccount();
    }

    private static final float MODAL_WIDTH = 760, HEADER_HEIGHT = 63, COVER_HEIGHT = 194;
    private static final float BIO_SIZE = 15.5f, BIO_LINE = BIO_SIZE * 1.95f, BIO_TRACKING = BIO_SIZE * .035f;
    private static final int OPTION_PAGE_SIZE = 100, MAX_OPTION_PAGES = 3;
    private static final Set<String> TITLE_ICONS = Set.of("leaf", "mountain", "moon", "star", "wheat", "house", "wind", "flower-2", "sun", "gift", "sparkles", "cloud", "book-open", "trophy", "utensils", "personal");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private record Box(float x, float y, float width, float height) {
        boolean contains(float xx, float yy) { return xx >= x && yy >= y && xx < x + width && yy < y + height; }
        float right() { return x + width; }
        float bottom() { return y + height; }
    }
    private record Rail(String kind, Box track, float thumbStart, float thumbLength, float content, boolean horizontal) {}
    private record EditState(String text, int anchor, int caret) {}
    private static final class MotionValue {
        float value, from, target;
        long started;
        boolean ready;
        float sample(float destination, long time, long duration, boolean reduced) {
            if (!ready || reduced) { value = from = target = destination; started = time; ready = true; return value; }
            float p = clamp((float) (time - started) / duration, 0, 1);
            value = from + (target - from) * curve(p);
            if (target != destination) { from = value; target = destination; started = time; }
            return value;
        }
        void snap(float value) { this.value = from = target = value; ready = true; started = System.nanoTime(); }
    }
    private static final class Options {
        final String kind;
        final TreeMap<Integer, List<JsonObject>> pages = new TreeMap<>();
        int total, loading = -1;
        String error = "", requestId = "";
        boolean available = true;
        Options(String kind) { this.kind = kind; }
        void reset() { pages.clear(); total = 0; loading = -1; error = requestId = ""; available = true; }
        void first(JsonObject source) {
            reset();
            List<JsonObject> rows = objects(source, kind);
            pages.put(0, rows);
            total = integer(source, kind + "Total", rows.size());
            if (bool(source, kind + "HasMore", false) && total <= rows.size()) total = rows.size() + 1;
            available = "ready".equals(string(source, kind + "Status", "ready"));
        }
        JsonObject at(int index) {
            List<JsonObject> rows = pages.get(index / OPTION_PAGE_SIZE);
            return rows == null || index % OPTION_PAGE_SIZE >= rows.size() ? null : rows.get(index % OPTION_PAGE_SIZE);
        }
        JsonObject find(String key, String value) {
            if (value == null || value.isEmpty()) return null;
            for (List<JsonObject> rows : pages.values()) for (JsonObject row : rows)
                if (value.equals(string(row, key, ""))) return row;
            return null;
        }
        void accept(JsonObject response, int page) {
            List<JsonObject> rows = objects(response, "rows");
            pages.put(page, rows);
            total = integer(response, "total", Math.max(total, page * OPTION_PAGE_SIZE + rows.size()));
            loading = -1;
            error = requestId = "";
            while (pages.size() > MAX_OPTION_PAGES) {
                int first = pages.firstKey(), last = pages.lastKey();
                pages.remove(page - first > last - page ? first : last);
            }
        }
    }

    private final Host host;
    private final HubProfileArt art = new HubProfileArt();
    private final HubTextEditor biography = new HubTextEditor(true, 120);
    private final Options titleOptions = new Options("titles"), fortuneOptions = new Options("fortunes"), memoryOptions = new Options("memories");
    private final Map<String, Rail> rails = new LinkedHashMap<>();
    private final Map<String, HubMotion.Transition> controlMotion = new HashMap<>();
    private final MotionValue biographyHeight = new MotionValue(), memoryMovement = new MotionValue();
    private final Deque<EditState> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private JsonObject profile, latestProfile, draft;
    private String playerId = "", status = "正在读取玩家资料…", focus = "", pressed = "", draggingRail = "";
    private String expectedRevision = "", inFlightPlayer = "";
    private String profileRequestId = "", saveRequestId = "";
    private long requestCounter;
    private JsonObject savePatch;
    private boolean open, closing, self, editing, saving, reduced, dark, preview, staleDraft;
    private long openedAt, closedAt, modeAt, now, lastEditAt;
    private float modeFrom, modeTarget, modeAmount, opacity = 1, scale = 1, slide;
    private float mx, my, x, y, width = MODAL_WIDTH, height, bodyTop, bodyHeight, contentHeight;
    private float mainScroll, titleScroll, fortuneScroll, memoryScroll, memoryTarget, railGrab;
    private float currentBioHeight, currentCollectionHeight, currentMemoryHeight, naturalBodyHeight;
    private Box contentClip, bioBox, titleBox, fortuneBox, memoryBox;
    private HubDraw draw;
    private Typeface quoteFace;
    private Font quoteFont;
    private TextLine quoteLine;

    public HubProfileView(Host host) { this.host = Objects.requireNonNull(host); }
    public boolean isOpen() { return open; }
    public boolean isClosing() { return closing; }
    public boolean editing() { return editing; }
    public boolean saving() { return saving; }
    public String playerId() { return playerId; }
    public String playerName() { return string(profile, "name", ""); }
    public boolean self() { return self; }
    public float scrollPosition() { return mainScroll; }
    public void setPreview(boolean value) { preview = value; }
    public void resetPendingReads() {
        profileRequestId = "";
        for (Options options : List.of(titleOptions, fortuneOptions, memoryOptions)) {
            options.loading = -1; options.requestId = "";
        }
    }
    public void refresh() {
        if (!open || closing || playerId.isBlank()) return;
        JsonObject request = new JsonObject(); request.addProperty("playerId", playerId);
        profileRequestId = tagRequest(request); host.request("profile", request);
    }

    public void open(String id, boolean own) {
        if (id == null || id.isBlank()) return;
        boolean samePlayer = id.equals(playerId);
        playerId = id;
        self = own;
        open = true;
        closing = editing = saving = staleDraft = false;
        inFlightPlayer = "";
        saveRequestId = ""; savePatch = null;
        openedAt = System.nanoTime();
        modeAmount = modeFrom = modeTarget = 0;
        mainScroll = titleScroll = fortuneScroll = memoryScroll = memoryTarget = 0;
        memoryMovement.snap(0);
        focus = pressed = draggingRail = "";
        undo.clear(); redo.clear(); controlMotion.clear();
        if (!samePlayer) { profile = latestProfile = draft = null; titleOptions.reset(); fortuneOptions.reset(); memoryOptions.reset(); }
        status = "正在读取玩家资料…";
        JsonObject request = new JsonObject();
        request.addProperty("playerId", id);
        profileRequestId = tagRequest(request);
        host.request("profile", request);
    }

    public void requestClose() {
        if (!open || closing) return;
        closing = true;
        closedAt = System.nanoTime();
        draggingRail = "";
        biography.pointerUp();
    }

    public void clear() {
        open = closing = self = editing = saving = staleDraft = false;
        profile = latestProfile = draft = null;
        playerId = inFlightPlayer = expectedRevision = "";
        profileRequestId = saveRequestId = ""; savePatch = null;
        focus = pressed = draggingRail = "";
        titleOptions.reset(); fortuneOptions.reset(); memoryOptions.reset();
        undo.clear(); redo.clear(); rails.clear(); controlMotion.clear();
        biography.setText("");
        mainScroll = titleScroll = fortuneScroll = memoryScroll = memoryTarget = 0;
    }

    /** Receives only whitelisted community data. Parent associates responses with their request. */
    public void accept(String action, JsonObject response) {
        if (response == null) return;
        JsonObject request = object(response, "_hubRequest");
        if (action.equals("profileOptions")) {
            String owner = string(response, "playerId", "");
            if (!owner.equals(playerId) || !self || profile == null) return;
            String kind = string(response, "kind", string(request, "kind", ""));
            Options options = options(kind);
            if (options != null) {
                String replyId = string(request, "_hubViewRequestId", "");
                if (!replyId.isEmpty() && !replyId.equals(options.requestId)) return;
                options.accept(response, integer(response, "page", integer(request, "page", 0)));
            }
            return;
        }
        if (!action.equals("profile") && !action.equals("saveProfile")) return;
        JsonObject next = object(response, "profile");
        if (next.size() == 0 && response.has("playerId")) next = response;
        if (!playerId.equals(string(next, "playerId", ""))) return;
        if (action.equals("saveProfile") && (!saving || !inFlightPlayer.equals(playerId)
                || !saveRequestId.equals(string(request, "_hubViewRequestId", ""))
                || !expectedRevision.equals(string(request, "expectedRevision", ""))
                || savePatch == null || !savePatch.equals(object(request, "patch")))) return;
        latestProfile = next.deepCopy();
        status = "";
        JsonObject options = object(response, "options");
        if (self && options.size() != 0) {
            titleOptions.first(options); fortuneOptions.first(options); memoryOptions.first(options);
        }
        if (action.equals("saveProfile")) {
            saving = false;
            inFlightPlayer = "";
            saveRequestId = ""; savePatch = null;
            profile = latestProfile.deepCopy();
            draft = profile.deepCopy();
            staleDraft = false;
            endEditing();
            host.feedback("资料已保存", false);
            return;
        }
        if (editing && profile != null && !string(profile, "revision", "").equals(string(next, "revision", ""))) {
            if (!staleDraft) host.feedback("资料已更新，当前草稿已保留。取消编辑可载入最新资料。", true);
            staleDraft = true;
            return;
        }
        profile = latestProfile.deepCopy();
        if (!editing) { draft = profile.deepCopy(); biography.setText(string(profile, "bio", "")); }
    }

    public void failure(String action, String message) {
        failure(action, message, null);
    }

    /** Root retains client-only query metadata after removing it from the provider payload. */
    public void failure(String action, String message, JsonObject query) {
        boolean sessionEnded = message != null && message.contains("会话已失效");
        if (sessionEnded || "资料不存在或不可见".equals(message)) {
            if (query != null && query.has("playerId") && !playerId.equals(string(query, "playerId", ""))) return;
            if (action.equals("profile") && query != null && !profileRequestId.equals(string(query, "_hubViewRequestId", ""))) return;
            if (action.equals("saveProfile") && (!saving || !inFlightPlayer.equals(playerId)
                    || query != null && !saveRequestId.equals(string(query, "_hubViewRequestId", "")))) return;
            if (action.equals("profileOptions") && query != null && List.of(titleOptions, fortuneOptions, memoryOptions).stream().noneMatch(options ->
                    options.loading >= 0 && options.kind.equals(string(query, "kind", "")) && options.requestId.equals(string(query, "_hubViewRequestId", "")))) return;
            if (!sessionEnded && !List.of("profile", "profileOptions", "saveProfile").contains(action)) return;
            if (sessionEnded && query != null && !query.has("playerId") && action.equals("memoryOptions") && !self) return;
            profile = latestProfile = draft = null;
            editing = saving = staleDraft = false;
            inFlightPlayer = expectedRevision = saveRequestId = ""; savePatch = null;
            titleOptions.reset(); fortuneOptions.reset(); memoryOptions.reset();
            undo.clear(); redo.clear(); biography.setText("");
            status = message; mainScroll = titleScroll = fortuneScroll = memoryScroll = memoryTarget = 0;
            return;
        }
        if (action.equals("profileOptions")) {
            if (!self || profile == null) return;
            for (Options options : List.of(titleOptions, fortuneOptions, memoryOptions)) if (options.loading >= 0) {
                if (query != null && (!options.kind.equals(string(query, "kind", ""))
                        || !options.requestId.equals(string(query, "_hubViewRequestId", "")))) continue;
                options.loading = -1;
                options.requestId = "";
                options.error = message == null || message.isBlank() ? "候选暂不可用，请重试。" : message;
            }
        } else if (action.equals("saveProfile")) {
            if (!saving || !inFlightPlayer.equals(playerId) || query != null && !saveRequestId.equals(string(query, "_hubViewRequestId", ""))) return;
            saving = false;
            inFlightPlayer = "";
            saveRequestId = ""; savePatch = null;
            host.feedback(message, true);
        } else if (action.equals("profile")) {
            if (query != null && (!playerId.equals(string(query, "playerId", ""))
                    || !profileRequestId.equals(string(query, "_hubViewRequestId", "")))) return;
            status = message == null || message.isBlank() ? "玩家资料暂不可用，请重试。" : message;
            if (profile != null) host.feedback(status, true);
        }
    }

    public void draw(HubDraw graphics, float viewportX, float viewportY, float viewportWidth, float viewportHeight,
                     float mouseX, float mouseY, long time, boolean reduceMotion, boolean darkTheme,
                     String focused, String pressedId) {
        if (!open) return;
        draw = graphics; now = time; reduced = reduceMotion; dark = darkTheme;
        focus = focused == null ? "" : focused;
        pressed = pressedId == null ? "" : pressedId;
        float entering = reduced ? 1 : curve(progress(now - openedAt, 200_000_000L));
        float leaving = !closing ? 0 : reduced ? 1 : curve(progress(now - closedAt, 200_000_000L));
        if (closing && leaving >= 1) { open = false; closing = false; host.closeProfile(); return; }
        opacity = entering * (1 - leaving);
        scale = reduced ? 1 : .985f + .015f * opacity;
        slide = reduced ? 0 : 10 * (1 - entering) + 7 * leaving;
        modeAmount = modeFrom + (modeTarget - modeFrom) * (reduced ? 1 : curve(progress(now - modeAt, 280_000_000L)));
        width = Math.min(MODAL_WIDTH, Math.max(320, viewportWidth - 60));
        measure();
        float accountHeight = self ? 40 : 0;
        contentHeight = COVER_HEIGHT + naturalBodyHeight + accountHeight;
        height = Math.min(Math.min(780, Math.max(240, viewportHeight - 60)), HEADER_HEIGHT + 2 + contentHeight);
        x = viewportX + (viewportWidth - width) / 2;
        y = viewportY + (viewportHeight - height) / 2;
        bodyTop = y + HEADER_HEIGHT + 1;
        bodyHeight = height - HEADER_HEIGHT - 2;
        mainScroll = clamp(mainScroll, 0, Math.max(0, contentHeight - bodyHeight));
        mx = x + width / 2 + (mouseX - x - width / 2) / scale;
        my = y + height / 2 + (mouseY - slide - y - height / 2) / scale;
        contentClip = new Box(x + 1, bodyTop, width - 2, bodyHeight);
        rails.clear(); bioBox = titleBox = fortuneBox = memoryBox = null;

        draw.captureGlassBackdrop(viewportX, viewportY, viewportWidth, viewportHeight);
        float veil = reduced ? 1 : curve(progress(now - openedAt, 180_000_000L)) * (1 - leaving);
        draw.saveLayerAlpha(viewportX, viewportY, viewportWidth, viewportHeight, veil);
        draw.blurBackdrop(viewportX, viewportY, viewportWidth, viewportHeight, 0, 9, 1);
        draw.rect(viewportX, viewportY, viewportWidth, viewportHeight, dark ? 0x590a1029 : 0x3634405c);
        draw.restore();
        draw.saveLayerAlpha(x - 100, y - 100, width + 200, height + 200, opacity);
        draw.save();
        draw.canvas().concat(Matrix33.makeTranslate(x + width / 2, y + height / 2 + slide)
            .makeConcat(Matrix33.makeScale(scale)).makeConcat(Matrix33.makeTranslate(-x - width / 2, -y - height / 2)));
        draw.boxShadow(x, y, width, height, 25, 0, 28, 76, -18, dark ? 0x99030920 : 0x4035416c);
        draw.boxShadow(x, y, width, height, 25, 0, 5, 16, -8, dark ? 0x59060e27 : 0x2453658e);
        draw.linearGradientRoundRect(x, y, width, height, 25, 135,
            dark ? new int[]{0xf52b364d, 0xf5322e46} : new int[]{0xf5fcfdff, 0xeff5f3ff}, new float[]{0, 1});
        draw.save(); draw.clipRoundRect(x, y, width, height, 25);
        draw.text("玩家资料", x + 26, y + 38, 15, ink(), 600);
        button("profile-close", "关闭弹窗", "x", x + width - 50, y + 15, 32, 32, this::requestClose, true, false, null);
        draw.line(x + 1, y + HEADER_HEIGHT, x + width - 1, y + HEADER_HEIGHT, 1, line());
        draw.save(); draw.clipRoundRect(contentClip.x, contentClip.y, contentClip.width, contentClip.height, 0);
        if (profile == null) drawLoading(); else drawContent();
        draw.restore();
        scrollbar("body", new Box(x + width - 5, bodyTop + 5, 3, bodyHeight - 10), contentHeight, mainScroll, false);
        draw.restore();
        draw.strokeRoundRect(x, y, width, height, 25, 1, dark ? 0x38d4e1ff : 0xe6ffffff);
        draw.restore(); draw.restore();
    }

    private void measure() {
        if (profile == null) { naturalBodyHeight = 128; return; }
        float bioWidth = Math.min(510, width - 62 - 114) - 46;
        String text = editing ? biography.text() : string(profile, "bio", "");
        biography.layout(value -> draw.textWidthTracked(value, BIO_SIZE, 400, BIO_TRACKING, true), Math.max(60, bioWidth), 4000, BIO_LINE);
        float textHeight = text.isBlank() && !editing ? 17 : Math.max(BIO_LINE, wrap(text, bioWidth, BIO_SIZE, 400, true, BIO_TRACKING).size() * BIO_LINE);
        currentBioHeight = biographyHeight.sample(Math.max(62, textHeight + 17), now, 280_000_000L, reduced);
        float readingCollection = selected("featuredFortune", "fortunes", "date", "featuredFortuneDate", false) == null ? 76 : 99;
        currentCollectionHeight = readingCollection + (212 - readingCollection) * modeAmount;
        boolean hasMemory = selected("featuredMemory", "memories", "id", "featuredMemoryId", false) != null;
        float readMemory = hasMemory ? 98 : 8;
        currentMemoryHeight = readMemory + ((memoryOptions.total > 0 ? 118 : 8) - readMemory) * modeAmount;
        naturalBodyHeight = 23 + currentBioHeight + 194 * modeAmount + 19 + 32 + currentCollectionHeight - 8 + 25
            + currentMemoryHeight - 8 + 57 * modeAmount + (preview ? 38 : 0) + 18;
    }

    private void drawLoading() {
        float baseline = bodyTop + 99 - mainScroll;
        draw.icon("user-round", x + width / 2 - 16, baseline - 50, 32, muted());
        center(status, x + width / 2, baseline + 10, 12, muted(), 400);
        if (!status.equals("正在读取玩家资料…")) button("profile-retry", "重新读取", null,
            x + width / 2 - 44, baseline + 30, 88, 34, () -> {
                JsonObject payload = new JsonObject(); payload.addProperty("playerId", playerId);
                profileRequestId = tagRequest(payload); host.request("profile", payload);
            }, true, false, contentClip);
    }

    private void drawContent() {
        JsonObject visible = editing ? draft : profile;
        String background = string(visible, "background", "mist"), pattern = string(visible, "profilePattern", "classic");
        float coverY = bodyTop - mainScroll;
        art.cover(draw, x + 1, coverY, width - 2, COVER_HEIGHT, background, pattern, false, dark);
        float left = x + 31;
        draw.text(self ? "我的公开资料" : "玩家公开资料", left, coverY + 45, 10, muted(), 400);
        if (preview) draw.text("Web 演示", left + 77, coverY + 45, 10, muted(), 400);
        draw.text(fit(string(profile, "name", "玩家"), width - 230, 31, 680), left, coverY + 92, 31, ink(), 680);
        JsonObject equipped = object(profile, "title");
        if (equipped.size() > 0) titleText(equipped, left, coverY + 121, 14, width - 230);
        draw.icon("calendar-days", left, coverY + 148, 13, muted());
        draw.text(date(profile, "joinedAt", "joined") + " 加入旅程", left + 20, coverY + 159, 10, muted(), 400);
        HubHead.drawPlayer(draw, playerId, self, x + width - 38 - 128 + 25.5f, coverY + 58.5f, 77, -25, -12);
        float bodyY = coverY + COVER_HEIGHT;
        float cursor = bodyY + 23;
        drawBiography(left, cursor, width - 62, visible, background);
        cursor += currentBioHeight;
        drawAppearance(left, cursor, width - 62, background, pattern);
        cursor += 194 * modeAmount + 19;
        draw.text("精选收藏", left, cursor + 12, 12, muted(), 600);
        cursor += 32;
        drawCollections(left, cursor, width - 62);
        cursor += currentCollectionHeight - 8 + 25;
        draw.line(left, cursor, x + width - 31, cursor, 1, line());
        drawMemory(left, cursor - 4, width - 62);
        cursor += currentMemoryHeight - 8;
        drawFooter(left, cursor, width - 62);
        cursor += 57 * modeAmount;
        if (preview) {
            art.icon(draw, "feather", left, cursor + 24, 12, muted());
            draw.text("公开展示的资料与收藏均为演示样例", left + 18, cursor + 35, 10, muted(), 400);
        }
        if (self) {
            float accountY = bodyY + naturalBodyHeight;
            button("profile-account", "账户信息", "chevron-right", x + width - 113, accountY,
                87, 24, host::openAccount, !editing && !saving, false, contentClip);
        }
    }

    private void drawBiography(float left, float top, float available, JsonObject visible, String background) {
        float statementWidth = Math.min(510, available - 114);
        boolean empty = string(profile, "bio", "").isBlank();
        int[] colors = HubProfileArt.palette(background, dark);
        float statementHeight = currentBioHeight - 15;
        if (!empty || editing || modeAmount > 0) {
            draw.linearGradientRoundRect(left, top, statementWidth, statementHeight, 11, 100,
                new int[]{alpha(colors[0], .24f), 0, 0}, new float[]{0, .84f, 1});
            if (quoteLine == null) {
                quoteFace = FontMgr.getDefault().matchFamilyStyle("Georgia", FontStyle.NORMAL);
                if (quoteFace == null) quoteFace = FontMgr.getDefault().matchFamilyStyle("serif", FontStyle.NORMAL);
                quoteFont = new Font(quoteFace, 44).setSubpixel(true);
                quoteLine = TextLine.make("“", quoteFont);
            }
            var metrics = quoteFont.getMetrics();
            float baseline = top - 4 + (44 - (metrics.getDescent() - metrics.getAscent())) / 2 - metrics.getAscent();
            try (Paint quotePaint = new Paint().setAntiAlias(true).setColor(alpha(accent(), editing ? .58f : .42f))) {
                draw.canvas().drawTextLine(quoteLine, left + 6, baseline, quotePaint);
            }
        }
        float tx = left + 29, ty = top + 8, textWidth = statementWidth - 46;
        bioBox = editing ? new Box(tx, ty, textWidth, Math.max(BIO_LINE, currentBioHeight - 17)) : null;
        float readOpacity = modeAlpha(false), writeOpacity = modeAlpha(true);
        if (readOpacity > 0) {
            beginLocalAlpha(new Box(left, top, statementWidth, currentBioHeight), readOpacity);
            if (empty) draw.text("还没有写下简介。", left, top + 23, 12, muted(), 400);
            else wrapped(string(profile, "bio", ""), tx, ty + (BIO_LINE + BIO_SIZE) / 2, textWidth, BIO_SIZE, BIO_LINE, ink(), 400, true, BIO_TRACKING);
            draw.canvas().restore();
        }
        if (writeOpacity > 0) {
            beginLocalAlpha(new Box(left, top, statementWidth, currentBioHeight), writeOpacity);
            biography.layout(value -> draw.textWidthTracked(value, BIO_SIZE, 400, BIO_TRACKING, true), textWidth, Math.max(BIO_LINE, currentBioHeight - 17), BIO_LINE);
            for (HubTextEditor.Rect selection : biography.selectionRects())
                draw.roundRect(tx + selection.x(), ty + selection.y(), selection.width(), selection.height(), 2, alpha(accent(), .22f));
            if (biography.text().isEmpty()) draw.textSerif("写下一点关于自己……", tx, ty + (BIO_LINE + BIO_SIZE) / 2, BIO_SIZE, alpha(muted(), .7f), 400);
            for (HubTextEditor.Line row : biography.lines())
                draw.textTracked(row.text(), tx, ty + row.y() + (BIO_LINE + BIO_SIZE) / 2, BIO_SIZE, ink(), 400, BIO_TRACKING, true);
            if (editing && focus.equals("profile-bio") && (now / 500_000_000L) % 2 == 0) {
                HubTextEditor.Rect caret = biography.caretRect();
                draw.rect(tx + caret.x(), ty + caret.y() + (BIO_LINE - BIO_SIZE) / 2, 1, BIO_SIZE + 4, accent());
            }
            if (focus.equals("profile-bio")) draw.line(left + 11, top + statementHeight, left + statementWidth - 11, top + statementHeight, 1, alpha(accent(), .22f));
            draw.canvas().restore();
        }
        if (self) {
            if (editing) right(biography.text().length() + " / 120", left + available, top + 22, 10, muted(), 400);
            else button("profile-edit", "编辑资料", "pencil", left + available - 92, top + 3, 102, 34, this::beginEditing, true, false, contentClip);
        }
        if (editing && !saving) hit("profile-bio", "个人简介", bioBox, () -> host.focus("profile-bio"), contentClip);
    }

    private void drawAppearance(float left, float top, float available, String background, String pattern) {
        if (modeAmount <= 0 || !self) return;
        Box region = new Box(left, top, available, 194 * modeAmount);
        draw.save(); draw.clipRoundRect(region.x, region.y, region.width, region.height, 0);
        beginLocalAlpha(region, modeAlpha(true));
        float gx = left + 7, legendY = top + 30;
        draw.text("资料背景", gx, legendY, 11, muted(), 400);
        float gap = 9, offset = 0;
        for (int index = 0; index < HubProfileArt.COLORS.length; index++) {
            String id = HubProfileArt.COLORS[index], name = HubProfileArt.COLOR_NAMES[index];
            int[] colors = HubProfileArt.palette(id, dark);
            float bx = gx + offset, by = legendY + 10;
            draw.linearGradientRoundRect(bx, by, 30, 25, 7, 120, new int[]{colors[1], colors[0]}, new float[]{0, 1});
            draw.radialGradientRoundRect(bx, by, 30, 25, 7, bx + 27, by + 1, 20, 20,
                new int[]{dark ? 0x14d0d9f0 : 0x6bffffff, 0}, new float[]{0, 1});
            boolean selected = id.equals(background);
            draw.strokeRoundRect(bx, by, 30, 25, 7, 1, selected ? accent() : edge());
            if (selected) draw.icon("check", bx + 8, by + 5.5f, 14, ink());
            draw.text(name, bx + 36, by + 17, 11, ink(), 400);
            float controlWidth = 36 + draw.textWidth(name, 11, 400);
            int selection = index;
            hit("profile-color-" + id, name, new Box(bx - 3, by - 3, controlWidth + 4, 31), () -> change("background", HubProfileArt.COLORS[selection]), intersect(region, contentClip));
            focusRing("profile-color-" + id, bx - 2, by - 2, 34, 29, 9);
            offset += controlWidth + gap;
        }
        float py = top + 104;
        draw.text("背景图案", gx, py - 10, 11, muted(), 400);
        float cellWidth = (available - 14 - 40) / 6;
        for (int index = 0; index < HubProfileArt.PATTERNS.length; index++) {
            String id = HubProfileArt.PATTERNS[index];
            float bx = gx + index * (cellWidth + 8);
            draw.save(); draw.clipRoundRect(bx, py, cellWidth, 49, 9);
            art.cover(draw, bx, py, cellWidth, 49, background, id, true, dark);
            draw.restore();
            draw.strokeRoundRect(bx, py, cellWidth, 49, 9, id.equals(pattern) ? 2 : 1, id.equals(pattern) ? accent() : line());
            center(HubProfileArt.PATTERN_NAMES[index], bx + cellWidth / 2, py + 66, 10, id.equals(pattern) ? ink() : muted(), 400);
            int selection = index;
            hit("profile-pattern-" + id, HubProfileArt.PATTERN_NAMES[index], new Box(bx, py, cellWidth, 70),
                () -> change("profilePattern", HubProfileArt.PATTERNS[selection]), intersect(region, contentClip));
            focusRing("profile-pattern-" + id, bx - 3, py - 3, cellWidth + 6, 55, 11);
        }
        draw.canvas().restore(); draw.restore();
    }

    private void drawCollections(float left, float top, float available) {
        float cell = (available - 20) / 2;
        Box region = new Box(left - 4, top - 4, available + 8, currentCollectionHeight);
        draw.save(); draw.clipRoundRect(region.x, region.y, region.width, region.height, 0);
        if (modeAlpha(false) > 0) {
            beginLocalAlpha(region, modeAlpha(false));
            JsonObject title = selected("featuredTitle", "titles", "id", "featuredTitleId", false);
            if (title == null) emptyCollection("精选称号", "尚未展示称号", left, top);
            else {
                int[] colors = titlePalette(title);
                draw.boxShadow(left, top + 2, 60, 65, 18, 0, 6, 14, 0, 0x1a8878a5);
                draw.linearGradientRoundRect(left, top + 2, 60, 65, 18, 145, new int[]{colors[0], colors[1]}, new float[]{0, 1});
                draw.strokeRoundRect(left, top + 2, 60, 65, 18, 1, dark ? 0x28959cc0 : 0xbfffffff);
                draw.icon(titleIcon(title), left + 16.5f, top + 21, 27, colors[2]);
                String rarity = rarityLabel(title);
                draw.text("称号" + (rarity.isEmpty() ? "" : " · " + rarity), left + 76, top + 14, 11, muted(), 400);
                titleText(title, left + 76, top + 39, 17, cell - 76);
                draw.text(acquired(title), left + 76, top + 62, 11, muted(), 400);
            }
            float fx = left + cell + 20;
            JsonObject fortune = selected("featuredFortune", "fortunes", "date", "featuredFortuneDate", false);
            if (fortune == null) emptyCollection("精选签笺", "尚未展示签笺", fx, top);
            else {
                // CSS object-fit:contain keeps the square source inside the 47x65 image box.
                var slip = draw.imageResource("textures/ui/fortune-slip.png");
                float imageScale = Math.min(47f / slip.getWidth(), 65f / slip.getHeight());
                float imageWidth = slip.getWidth() * imageScale, imageHeight = slip.getHeight() * imageScale;
                try (Paint imagePaint = new Paint().setAntiAlias(false).setAlphaf(.92f)) {
                    draw.canvas().drawImageRect(slip, Rect.makeWH(slip.getWidth(), slip.getHeight()),
                        Rect.makeXYWH(fx + (47 - imageWidth) / 2, top + 10 + (65 - imageHeight) / 2, imageWidth, imageHeight),
                        SamplingMode.DEFAULT, imagePaint, true);
                }
                draw.text(string(fortune, "grade", "") + " · 第 " + string(fortune, "id", "") + " 签", fx + 63, top + 12, 11, muted(), 400);
                draw.textSerif(fit(string(fortune, "title", ""), cell - 63, 17, 600), fx + 63, top + 38, 17, ink(), 600);
                draw.textSerif(fit(poem(fortune), cell - 63, 11, 400), fx + 63, top + 61, 11, muted(), 400);
                draw.text(acquired(fortune), fx + 63, top + 85, 11, muted(), 400);
            }
            draw.canvas().restore();
        }
        if (self && modeAlpha(true) > 0) {
            beginLocalAlpha(region, modeAlpha(true));
            drawOptions(titleOptions, left, top, cell, true);
            drawOptions(fortuneOptions, left + cell + 20, top, cell, false);
            draw.canvas().restore();
        }
        draw.restore();
    }

    private void emptyCollection(String category, String message, float x, float y) {
        draw.text(category, x, y + 15, 11, muted(), 400);
        draw.text(message, x, y + 38, 13, muted(), 500);
        if (self) draw.text("在编辑资料中选择", x, y + 60, 11, muted(), 400);
    }

    private void drawOptions(Options options, float left, float top, float available, boolean titles) {
        draw.text(titles ? "精选称号" : "精选签笺", left, top + 12, 11, muted(), 400);
        if (!options.available) {
            draw.text(titles ? "称号收藏暂不可用" : "签笺收藏暂不可用", left + 4, top + 49, 12, muted(), 400);
            draw.text("已选收藏与当前草稿已保留。", left + 4, top + 73, 10, muted(), 400);
            button("profile-" + options.kind + "-provider-retry", "重新读取", null, left + 4, top + 89, 88, 32,
                this::refresh, !saving, false, contentClip);
            return;
        }
        Box box = new Box(left, top + 24, available, 180);
        if (titles) titleBox = box; else fortuneBox = box;
        int columns = titles ? 2 : 1;
        float rowHeight = titles ? 62 : 58;
        float content = (float) Math.ceil((options.total + 1) / (double) columns) * rowHeight + 1;
        float offset = clamp(titles ? titleScroll : fortuneScroll, 0, Math.max(0, content - box.height));
        if (titles) titleScroll = offset; else fortuneScroll = offset;
        float cardWidth = (available - 8 - (columns - 1) * 7) / columns;
        draw.save(); draw.clipRoundRect(box.x, box.y, box.width, box.height, 0);
        int first = Math.max(0, (int) (offset / rowHeight) * columns);
        int last = Math.min(options.total, (int) Math.ceil((offset + box.height) / rowHeight) * columns);
        for (int item = first; item <= last; item++) {
            float bx = left + 4 + (item % columns) * (cardWidth + 7);
            float by = box.y + 4 + (item / columns) * rowHeight - offset;
            JsonObject value = item == 0 ? null : options.at(item - 1);
            if (item > 0 && value == null) {
                draw.roundRect(bx, by, cardWidth, rowHeight - 7, 10, surface());
                if (options.error.isEmpty()) {
                    draw.text("读取中…", bx + 10, by + 29, 11, muted(), 400);
                    ensureOptions(options, item - 1);
                } else {
                    draw.text("读取失败，点击重试", bx + 10, by + 29, 11, muted(), 400);
                    int missing = item - 1;
                    hit("profile-" + options.kind + "-retry", options.error + " 重新读取", new Box(bx, by, cardWidth, rowHeight - 7), () -> {
                        options.error = ""; ensureOptions(options, missing);
                    }, intersect(box, contentClip));
                }
                continue;
            }
            String field = titles ? "featuredTitleId" : "featuredFortuneDate";
            String id = item == 0 ? "" : string(value, titles ? "id" : "date", "");
            boolean checked = Objects.equals(nullable(draft, field), id.isEmpty() ? null : id);
            String hitId = "profile-" + options.kind + "-" + item;
            boolean hover = new Box(bx, by, cardWidth, rowHeight - 7).contains(mx, my);
            draw.roundRect(bx, by, cardWidth, rowHeight - 7, 10, checked || hover ? soft() : surface());
            draw.strokeRoundRect(bx, by, cardWidth, rowHeight - 7, 10, 1, checked ? accent() : line());
            if (item == 0) draw.text("不展示", bx + 10, by + 30, 11, ink(), 550);
            else if (titles) {
                titleText(value, bx + 10, by + 22, 11, cardWidth - 20);
                draw.text(acquired(value) + " 获得", bx + 10, by + 43, 9, muted(), 400);
            } else {
                String text = fit(string(value, "title", ""), cardWidth * .49f, 11, 550);
                draw.text(text, bx + 10, by + 20, 11, ink(), 550);
                draw.text(string(value, "grade", "") + " · 第 " + string(value, "id", "") + " 签",
                    bx + 16 + draw.textWidth(text, 11, 550), by + 20, 9, muted(), 400);
                draw.text(acquired(value) + " 获得", bx + 10, by + 40, 9, muted(), 400);
            }
            hit(hitId, item == 0 ? "不展示" : titles ? titleName(value) : string(value, "title", ""),
                new Box(bx, by, cardWidth, rowHeight - 7), () -> change(field, id.isEmpty() ? null : id), intersect(box, contentClip));
            focusRing(hitId, bx, by, cardWidth, rowHeight - 7, 10);
        }
        draw.restore();
        scrollbar(options.kind, new Box(box.right() - 3, box.y, 3, box.height), content, offset, false);
        if (options.total > 0) ensureOptions(options, Math.min(options.total - 1, Math.max(0, last - 1)));
    }

    private void drawMemory(float left, float top, float available) {
        Box frame = new Box(left - 4, top, available + 8, currentMemoryHeight);
        draw.save(); draw.clipRoundRect(frame.x, frame.y, frame.width, frame.height, 0);
        if (modeAlpha(false) > 0) {
            beginLocalAlpha(frame, modeAlpha(false));
            JsonObject memory = selected("featuredMemory", "memories", "id", "featuredMemoryId", false);
            if (memory != null) {
                float ry = top + 26;
                draw.text("精选纪念", left, ry + 11, 11, muted(), 400);
                String name = string(memory, "name", "");
                draw.text(fit(name, available * .46f, 16, 600), left, ry + 35, 16, ink(), 600);
                draw.text(string(memory, "version", ""), left + Math.min(available * .46f, draw.textWidth(name, 16, 600)) + 7, ry + 35, 11, muted(), 400);
                draw.text(fit(string(memory, "description", ""), available * .51f, 11, 400), left, ry + 58, 11, muted(), 400);
                String period = period(memory), hours = hours(memory);
                float periodWidth = Math.max(draw.textWidth(period, 10, 400), draw.textWidth("停留 " + hours + " 小时", 10, 400));
                float periodX = left + available - 96 - 17 - periodWidth;
                draw.text(period, periodX, ry + 21, 10, muted(), 400);
                draw.text("停留 ", periodX, ry + 44, 10, muted(), 400);
                float hoursX = periodX + draw.textWidth("停留 ", 10, 400);
                draw.text(hours, hoursX, ry + 44, 10, ink(), 550);
                draw.text(" 小时", hoursX + draw.textWidth(hours, 10, 550), ry + 44, 10, muted(), 400);
                String archive = string(memory, "id", "");
                button("profile-open-memory", "翻开纪念", "arrow-up-right", left + available - 96, ry + 18, 96, 34,
                    () -> host.openMemories(playerId, archive), true, false, intersect(frame, contentClip));
            }
            draw.canvas().restore();
        }
        if (self && modeAlpha(true) > 0 && memoryOptions.total > 0) {
            beginLocalAlpha(frame, modeAlpha(true));
            float ry = top + 23;
            draw.text("精选纪念", left, ry + 11, 11, muted(), 400);
            float step = (available - 16) / 3 + 8;
            float content = 8 + memoryOptions.total * step - 8;
            memoryTarget = clamp(memoryTarget, 0, Math.max(0, content - available));
            memoryScroll = memoryMovement.sample(memoryTarget, now, 280_000_000L, reduced);
            memoryBox = new Box(left, ry + 29, available, 66);
            button("profile-memory-prev", "上一页整合包", "chevron-left", left + available - 52, ry - 7, 24, 22,
                () -> scrollMemories(-available), memoryTarget > 2, false, intersect(frame, contentClip));
            button("profile-memory-next", "下一页整合包", "chevron-right", left + available - 24, ry - 7, 24, 22,
                () -> scrollMemories(available), memoryTarget < content - available - 2, false, intersect(frame, contentClip));
            draw.save(); draw.clipRoundRect(memoryBox.x, memoryBox.y - 3, memoryBox.width, memoryBox.height + 3, 0);
            int first = Math.max(0, (int) (memoryScroll / step));
            int last = Math.min(memoryOptions.total - 1, (int) Math.ceil((memoryScroll + available) / step));
            for (int index = first; index <= last; index++) {
                JsonObject memory = memoryOptions.at(index);
                float bx = left + 4 + index * step - memoryScroll, by = memoryBox.y;
                float cardWidth = step - 8;
                boolean selected = memory != null && Objects.equals(nullable(draft, "featuredMemoryId"), string(memory, "id", ""));
                draw.roundRect(bx, by, cardWidth, 55, 11, selected ? soft() : surface());
                draw.strokeRoundRect(bx, by, cardWidth, 55, 11, 1, selected ? accent() : line());
                if (memory == null) {
                    if (memoryOptions.error.isEmpty()) { draw.text("读取中…", bx + 11, by + 30, 11, muted(), 400); ensureOptions(memoryOptions, index); }
                    else {
                        draw.text("读取失败，点击重试", bx + 11, by + 30, 11, muted(), 400);
                        int missing = index;
                        hit("profile-memories-retry", memoryOptions.error + " 重新读取", new Box(bx, by, cardWidth, 55), () -> {
                            memoryOptions.error = ""; ensureOptions(memoryOptions, missing);
                        }, intersect(memoryBox, contentClip));
                    }
                    continue;
                }
                String name = fit(string(memory, "name", ""), cardWidth - 63, 11, 550);
                draw.text(name, bx + 11, by + 21, 11, ink(), 550);
                draw.text(string(memory, "version", ""), bx + 16 + draw.textWidth(name, 11, 550), by + 21, 9, muted(), 400);
                draw.text(period(memory), bx + 11, by + 42, 9, muted(), 400);
                String id = string(memory, "id", ""), hitId = "profile-memory-" + index;
                hit(hitId, name, new Box(bx, by, cardWidth, 55), () -> change("featuredMemoryId", id), intersect(memoryBox, contentClip));
                focusRing(hitId, bx, by, cardWidth, 55, 11);
            }
            draw.restore();
            scrollbar("memories", new Box(left + 4, memoryBox.y + 60, available - 8, 3), content, memoryScroll, true);
            draw.canvas().restore();
        }
        draw.restore();
    }

    private void drawFooter(float left, float top, float available) {
        if (!self || modeAmount <= 0) return;
        Box region = new Box(left, top, available, 57 * modeAmount);
        draw.save(); draw.clipRoundRect(region.x, region.y, region.width, region.height, 0);
        beginLocalAlpha(region, modeAlpha(true));
        if (preview) draw.text("仅保存到此浏览器的 Web 演示", left + 4, top + 42, 10, muted(), 400);
        else if (staleDraft) draw.text("资料已有更新，当前草稿已保留", left + 4, top + 42, 10, muted(), 400);
        button("profile-cancel", "取消", null, left + available - 127, top + 21, 50, 34, this::cancelEditing, !saving, false, intersect(region, contentClip));
        button("profile-save", saving ? "保存中…" : "保存", "check", left + available - 71, top + 21, 67, 34, this::save,
            !saving, true, intersect(region, contentClip));
        draw.canvas().restore(); draw.restore();
    }

    private void beginEditing() {
        if (!self || profile == null || saving) return;
        draft = profile.deepCopy();
        expectedRevision = string(profile, "revision", "");
        biography.setText(string(draft, "bio", ""));
        biography.select(biography.text().length(), biography.text().length());
        titleScroll = fortuneScroll = memoryScroll = memoryTarget = 0;
        memoryMovement.snap(0);
        undo.clear(); redo.clear(); staleDraft = false;
        setEditing(true);
        host.focus("profile-bio");
    }

    private void cancelEditing() {
        if (saving) return;
        if (latestProfile != null) profile = latestProfile.deepCopy();
        draft = profile == null ? null : profile.deepCopy();
        biography.setText(string(profile, "bio", ""));
        staleDraft = false;
        endEditing();
    }

    private void endEditing() { setEditing(false); host.focus("profile-edit"); }
    private void setEditing(boolean value) {
        modeFrom = modeAmount;
        modeTarget = value ? 1 : 0;
        modeAt = System.nanoTime();
        editing = value;
        biography.pointerUp();
    }

    private void change(String field, String value) {
        if (!editing || saving || draft == null) return;
        if (value == null) draft.add(field, JsonNull.INSTANCE); else draft.addProperty(field, value);
    }

    private void save() {
        if (!editing || saving || profile == null || !self) return;
        JsonObject patch = new JsonObject();
        patch.addProperty("bio", biography.text().trim());
        patch.addProperty("background", string(draft, "background", "mist"));
        patch.addProperty("profilePattern", string(draft, "profilePattern", "classic"));
        for (String key : List.of("featuredTitleId", "featuredFortuneDate", "featuredMemoryId"))
            patch.add(key, draft.has(key) ? draft.get(key).deepCopy() : JsonNull.INSTANCE);
        JsonObject payload = new JsonObject();
        payload.add("patch", patch);
        payload.addProperty("expectedRevision", expectedRevision);
        saveRequestId = tagRequest(payload);
        savePatch = patch.deepCopy();
        inFlightPlayer = playerId;
        saving = true;
        host.request("saveProfile", payload);
    }

    private void ensureOptions(Options options, int item) {
        if (!self || !editing || !options.available || options.loading >= 0 || !options.error.isEmpty() || item < 0 || item >= options.total) return;
        int page = item / OPTION_PAGE_SIZE;
        if (options.pages.containsKey(page)) return;
        options.loading = page;
        JsonObject request = new JsonObject(); request.addProperty("kind", options.kind); request.addProperty("page", page);
        options.requestId = tagRequest(request);
        host.request("profileOptions", request);
    }

    private String tagRequest(JsonObject payload) {
        String id = Long.toString(++requestCounter);
        payload.addProperty("_hubViewRequestId", id);
        return id;
    }

    private Options options(String kind) {
        return switch (kind) { case "titles" -> titleOptions; case "fortunes" -> fortuneOptions; case "memories" -> memoryOptions; default -> null; };
    }

    private JsonObject selected(String objectKey, String kind, String optionKey, String selectionKey, boolean editingValue) {
        JsonObject source = editingValue && editing ? draft : profile;
        if (source == null) return null;
        String wanted = nullable(source, selectionKey);
        if (source.has(selectionKey) && wanted == null) return null;
        JsonObject full = object(source, objectKey);
        if (full.size() > 0 && (wanted == null || wanted.equals(string(full, optionKey, "")))) return full;
        Options choices = options(kind);
        return choices == null ? null : choices.find(optionKey, wanted);
    }

    public boolean scroll(float px, float py, double amount) {
        if (!open || closing) return false;
        float[] point = localPoint(px, py);
        if (editing && titleBox != null && titleBox.contains(point[0], point[1])) titleScroll -= amount * 48;
        else if (editing && fortuneBox != null && fortuneBox.contains(point[0], point[1])) fortuneScroll -= amount * 48;
        else if (editing && memoryBox != null && memoryBox.contains(point[0], point[1])) scrollMemories((float) -amount * 64);
        else if (contentClip != null && contentClip.contains(point[0], point[1])) mainScroll -= amount * 48;
        return true;
    }

    private void scrollMemories(float amount) { memoryTarget += amount; }

    public boolean pointerDown(float px, float py, boolean shift) {
        if (!open || closing) return false;
        float[] point = localPoint(px, py);
        if (!new Box(x, y, width, height).contains(point[0], point[1])) { requestClose(); return true; }
        for (Rail rail : rails.values()) {
            Box track = rail.track;
            Box target = rail.horizontal ? new Box(track.x, track.y - 4, track.width, 11) : new Box(track.x - 4, track.y, 11, track.height);
            if (target.contains(point[0], point[1]) && contentClip.contains(point[0], point[1])) {
                draggingRail = rail.kind;
                float coordinate = rail.horizontal ? point[0] : point[1];
                railGrab = coordinate >= rail.thumbStart && coordinate <= rail.thumbStart + rail.thumbLength ? coordinate - rail.thumbStart : rail.thumbLength / 2;
                dragRail(point[0], point[1]); return true;
            }
        }
        if (editing && !saving && bioBox != null && bioBox.contains(point[0], point[1]) && contentClip.contains(point[0], point[1])) {
            host.focus("profile-bio");
            biography.pointerDown(point[0] - bioBox.x, point[1] - bioBox.y, shift);
            lastEditAt = 0;
            return true;
        }
        return false;
    }

    public boolean pointerDrag(float px, float py) {
        if (!open) return false;
        float[] point = localPoint(px, py);
        if (!draggingRail.isEmpty()) { dragRail(point[0], point[1]); return true; }
        if (biography.dragging() && bioBox != null) {
            biography.pointerDrag(point[0] - bioBox.x, point[1] - bioBox.y);
            ensureCaretVisible(); return true;
        }
        return false;
    }

    public boolean pointerUp() {
        boolean handled = !draggingRail.isEmpty() || biography.dragging();
        draggingRail = "";
        biography.pointerUp();
        return handled;
    }

    private void dragRail(float px, float py) {
        Rail rail = rails.get(draggingRail);
        if (rail == null) return;
        float coordinate = rail.horizontal ? px : py;
        float start = rail.horizontal ? rail.track.x : rail.track.y;
        float length = rail.horizontal ? rail.track.width : rail.track.height;
        float value = clamp((coordinate - start - railGrab) / Math.max(1, length - rail.thumbLength), 0, 1) * Math.max(0, rail.content - length);
        switch (rail.kind) {
            case "body" -> mainScroll = value;
            case "titles" -> titleScroll = value;
            case "fortunes" -> fortuneScroll = value;
            case "memories" -> { memoryTarget = memoryScroll = value; memoryMovement.snap(value); }
        }
    }

    public boolean textFocused(String id) { return open && editing && !saving && "profile-bio".equals(id); }
    public boolean key(int key, int modifiers, String id) {
        if (!open || closing) return false;
        if (key == 256) { requestClose(); return true; }
        if (!textFocused(id)) {
            if (editing && !saving && radioKey(key, id)) return true;
            if (key == 266 || key == 267) { mainScroll += key == 266 ? -bodyHeight * .8f : bodyHeight * .8f; return true; }
            return false;
        }
        boolean ctrl = (modifiers & 2) != 0, shift = (modifiers & 1) != 0;
        if (ctrl && key == 90) { restoreEdit(shift ? redo : undo, shift ? undo : redo); return true; }
        if (ctrl && key == 89) { restoreEdit(redo, undo); return true; }
        EditState before = editState();
        if (ctrl && key == 65) biography.selectAll();
        else if (key == 259) biography.backspace(ctrl);
        else if (key == 261) biography.deleteForward(ctrl);
        else if (key == 263) biography.moveLeft(ctrl, shift);
        else if (key == 262) biography.moveRight(ctrl, shift);
        else if (key == 268) biography.moveHome(ctrl, shift);
        else if (key == 269) biography.moveEnd(ctrl, shift);
        else if (key == 265) biography.moveVertical(-1, shift);
        else if (key == 264) biography.moveVertical(1, shift);
        else if (key == 257 || key == 335) biography.insert("\n");
        else if (key != 32) return false;
        edited(before, false); ensureCaretVisible(); return true;
    }
    private boolean radioKey(int key, String id) {
        if (key != 263 && key != 262 && key != 265 && key != 264 && key != 268 && key != 269) return false;
        int direction = key == 263 || key == 265 ? -1 : 1;
        for (boolean colors : new boolean[]{true, false}) {
            String prefix = colors ? "profile-color-" : "profile-pattern-";
            if (!id.startsWith(prefix)) continue;
            String[] choices = colors ? HubProfileArt.COLORS : HubProfileArt.PATTERNS;
            for (int index = 0; index < choices.length; index++) if (id.equals(prefix + choices[index])) {
                int next = key == 268 ? 0 : key == 269 ? choices.length - 1 : Math.floorMod(index + direction, choices.length);
                change(colors ? "background" : "profilePattern", choices[next]);
                host.focus(prefix + choices[next]);
                return true;
            }
        }
        boolean titles = id.startsWith("profile-titles-"), fortunes = id.startsWith("profile-fortunes-");
        boolean memories = id.matches("profile-memory-\\d+");
        if (!titles && !fortunes && !memories) return false;
        String prefix = titles ? "profile-titles-" : fortunes ? "profile-fortunes-" : "profile-memory-";
        int index;
        try { index = Integer.parseInt(id.substring(prefix.length())); } catch (NumberFormatException error) { return false; }
        Options choices = titles ? titleOptions : fortunes ? fortuneOptions : memoryOptions;
        if (!choices.available) return true;
        int count = choices.total + (memories ? 0 : 1);
        if (count == 0) return true;
        int step = titles && (key == 265 || key == 264) ? 2 : 1;
        int next = key == 268 ? 0 : key == 269 ? count - 1 : Math.floorMod(index + direction * step, count);
        int optionIndex = next - (memories ? 0 : 1);
        JsonObject value = optionIndex < 0 ? null : choices.at(optionIndex);
        if (optionIndex >= 0 && value == null) { ensureOptions(choices, optionIndex); return true; }
        change(titles ? "featuredTitleId" : fortunes ? "featuredFortuneDate" : "featuredMemoryId",
            value == null ? null : string(value, fortunes ? "date" : "id", ""));
        host.focus(prefix + next);
        if (memories && memoryBox != null) {
            float cardStep = (memoryBox.width - 16) / 3 + 8;
            float start = next * cardStep;
            if (start < memoryTarget) memoryTarget = start;
            else if (start + cardStep > memoryTarget + memoryBox.width) memoryTarget = start + cardStep - memoryBox.width;
        } else {
            float row = (titles ? next / 2 : next) * (titles ? 62 : 58);
            float offset = titles ? titleScroll : fortuneScroll;
            if (row < offset) offset = row;
            else if (row + (titles ? 62 : 58) > offset + 180) offset = row + (titles ? 62 : 58) - 180;
            if (titles) titleScroll = offset; else fortuneScroll = offset;
        }
        return true;
    }
    public void type(char value, String id) {
        if (!textFocused(id)) return;
        EditState before = editState(); biography.type(value); edited(before, true); ensureCaretVisible();
    }
    public void paste(String value, String id) {
        if (!textFocused(id) || value == null) return;
        EditState before = editState(); biography.insert(value); edited(before, false); ensureCaretVisible();
    }
    public String selectedText(String id) { return textFocused(id) ? biography.selectedText() : ""; }
    public String cutText(String id) {
        if (!textFocused(id)) return "";
        EditState before = editState(); String text = biography.cutSelection(); edited(before, false); return text;
    }
    private EditState editState() { return new EditState(biography.text(), biography.anchor(), biography.caret()); }
    private void edited(EditState before, boolean coalesceTyping) {
        if (before.text.equals(biography.text())) return;
        long time = System.nanoTime();
        if (!coalesceTyping || time - lastEditAt > 500_000_000L || undo.isEmpty()) undo.addLast(before);
        while (undo.size() > 64) undo.removeFirst();
        redo.clear(); lastEditAt = coalesceTyping ? time : 0;
        if (draft != null) draft.addProperty("bio", biography.text());
    }
    private void restoreEdit(Deque<EditState> from, Deque<EditState> to) {
        if (from.isEmpty()) return;
        to.addLast(editState()); EditState target = from.removeLast();
        biography.setText(target.text); biography.select(target.anchor, target.caret);
        if (draft != null) draft.addProperty("bio", target.text);
        lastEditAt = 0; ensureCaretVisible();
    }
    private void ensureCaretVisible() {
        if (bioBox == null || contentClip == null) return;
        HubTextEditor.Rect caret = biography.caretRect();
        float top = bioBox.y + caret.y(), bottom = top + BIO_LINE;
        if (top < contentClip.y + 8) mainScroll -= contentClip.y + 8 - top;
        else if (bottom > contentClip.bottom() - 8) mainScroll += bottom - contentClip.bottom() + 8;
    }

    private void button(String id, String label, String icon, float bx, float by, float w, float h,
                        Runnable action, boolean enabled, boolean primary, Box clip) {
        float hover = controlMotion.computeIfAbsent(id, ignored -> new HubMotion.Transition())
            .sample(enabled && new Box(bx, by, w, h).contains(mx, my) ? 1 : 0, now, 160_000_000L, reduced);
        float down = !reduced && pressed.equals(id) ? 1 : 0;
        Box bounds = new Box(bx, by, w, h);
        beginLocalAlpha(bounds, enabled ? 1 : .5f);
        boolean subtle = id.equals("profile-edit") || id.equals("profile-cancel") || id.equals("profile-account") || id.equals("profile-close");
        if (primary) draw.roundRect(bx, by + down, w, h, 11, hover > .01f ? 0xff6b58a8 : 0xff7965b6);
        else if (!subtle) {
            draw.roundRect(bx, by + down, w, h, 11, hover > .01f ? soft() : surface());
            draw.strokeRoundRect(bx, by + down, w, h, 11, 1, line());
        } else if (hover > 0) draw.roundRect(bx, by + down, w, h, 11, alpha(soft(), hover));
        int color = primary ? 0xffffffff : id.equals("profile-open-memory") ? accent() : !subtle || hover > .5f ? ink() : muted();
        boolean iconOnly = id.equals("profile-close") || id.equals("profile-memory-prev") || id.equals("profile-memory-next");
        if (iconOnly) art.icon(draw, icon, bx + (w - 16) / 2, by + (h - 16) / 2 + down, 16, color);
        else {
            float textSize = id.equals("profile-account") ? 10 : 12;
            float textWidth = draw.textWidth(label, textSize, 400), combined = textWidth + (icon == null ? 0 : 20);
            float lx = bx + (w - combined) / 2;
            if (icon != null && !icon.equals("chevron-right") && !icon.equals("arrow-up-right")) { art.icon(draw, icon, lx, by + (h - 13) / 2 + down, 13, color); lx += 20; }
            draw.text(label, lx, by + h / 2 + textSize * .36f + down, textSize, color, 400);
            if (icon != null && (icon.equals("chevron-right") || icon.equals("arrow-up-right"))) art.icon(draw, icon, lx + textWidth + 7, by + (h - 13) / 2 + down, 13, color);
        }
        draw.canvas().restore();
        if (enabled) hit(id, label, bounds, action, clip);
        focusRing(id, bx, by, w, h, 11);
    }

    private void hit(String id, String label, Box bounds, Runnable action, Box clip) {
        if (closing || opacity < .95f || saving && !id.equals("profile-close")) return;
        if (id.startsWith("profile-color-") || id.startsWith("profile-pattern-") || id.startsWith("profile-titles-") || id.startsWith("profile-fortunes-") || id.matches("profile-memory-\\d+")) {
            if (!editing || modeAmount < .95f) return;
        }
        Box visible = clip == null ? bounds : intersect(bounds, clip);
        if (visible == null || visible.width <= 0 || visible.height <= 0) return;
        float centerX = x + width / 2, centerY = y + height / 2;
        host.hit(id, label, centerX + (visible.x - centerX) * scale,
            centerY + (visible.y - centerY) * scale + slide, visible.width * scale, visible.height * scale, action);
    }

    private void focusRing(String id, float x, float y, float width, float height, float radius) {
        if (id.equals(focus)) draw.strokeRoundRect(x - 2, y - 2, width + 4, height + 4, radius + 2, 2, accent());
    }
    private void scrollbar(String kind, Box track, float content, float offset, boolean horizontal) {
        float length = horizontal ? track.width : track.height;
        if (content <= length + 1 || length <= 0) return;
        float thumbLength = Math.min(length, Math.max(25, length * length / content));
        float start = horizontal ? track.x : track.y;
        float thumb = start + clamp(offset / Math.max(1, content - length), 0, 1) * (length - thumbLength);
        int color = dark ? 0x4daaa6d3 : 0x459290ad;
        if (horizontal) draw.roundRect(thumb, track.y, thumbLength, 3, 2, color);
        else draw.roundRect(track.x, thumb, 3, thumbLength, 2, color);
        rails.put(kind, new Rail(kind, track, thumb, thumbLength, content, horizontal));
    }
    private float[] localPoint(float px, float py) {
        return new float[]{x + width / 2 + (px - x - width / 2) / scale,
            y + height / 2 + (py - slide - y - height / 2) / scale};
    }
    private float modeAlpha(boolean edit) {
        float p = reduced ? 1 : curve(progress(now - modeAt, 160_000_000L));
        float start = modeFrom;
        float amount = start + (modeTarget - start) * p;
        return edit ? amount : 1 - amount;
    }
    private void beginLocalAlpha(Box bounds, float opacity) {
        draw.canvas().saveLayerAlpha(Rect.makeXYWH(bounds.x - 5, bounds.y - 5, bounds.width + 10, bounds.height + 10), Math.round(255 * clamp(opacity, 0, 1)));
    }
    private static Box intersect(Box first, Box second) {
        if (first == null) return second;
        if (second == null) return first;
        float left = Math.max(first.x, second.x), top = Math.max(first.y, second.y);
        float right = Math.min(first.right(), second.right()), bottom = Math.min(first.bottom(), second.bottom());
        return new Box(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
    }

    private int ink() { return dark ? 0xffe0e3f3 : 0xff34415f; }
    private int muted() { return dark ? 0xff9ca6c1 : 0xff77819a; }
    private int accent() { return dark ? 0xffb2a1e4 : 0xff7861b2; }
    private int line() { return dark ? 0x288791b7 : 0xffdce1ee; }
    private int edge() { return dark ? 0x28959cc0 : 0xd9ffffff; }
    private int surface() { return dark ? 0xcf282c40 : 0xb8ffffff; }
    private int soft() { return dark ? 0x8c38374e : 0xfff0eff8; }
    private void center(String text, float cx, float baseline, float size, int color, int weight) { draw.text(text, cx - draw.textWidth(text, size, weight) / 2, baseline, size, color, weight); }
    private void right(String text, float right, float baseline, float size, int color, int weight) { draw.text(text, right - draw.textWidth(text, size, weight), baseline, size, color, weight); }
    private String fit(String text, float limit, float size, int weight) {
        if (limit <= 0) return "";
        int end = text.length();
        while (end > 0 && draw.textWidth(text.substring(0, end) + (end == text.length() ? "" : "…"), size, weight) > limit)
            end = text.offsetByCodePoints(end, -1);
        return text.substring(0, end) + (end < text.length() ? "…" : "");
    }
    private List<String> wrap(String text, float width, float size, int weight, boolean serif, float tracking) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int offset = 0; offset < text.length();) {
            int cp = text.codePointAt(offset); offset += Character.charCount(cp);
            if (cp == '\n') { lines.add(current.toString()); current.setLength(0); continue; }
            String glyph = new String(Character.toChars(cp));
            if (!current.isEmpty() && draw.textWidthTracked(current + glyph, size, weight, tracking, serif) > width) {
                lines.add(current.toString()); current.setLength(0);
            }
            current.append(glyph);
        }
        if (!current.isEmpty() || lines.isEmpty() || text.endsWith("\n")) lines.add(current.toString());
        return lines;
    }
    private void wrapped(String text, float x, float baseline, float width, float size, float lineHeight, int color, int weight, boolean serif, float tracking) {
        List<String> lines = wrap(text, width, size, weight, serif, tracking);
        for (int index = 0; index < lines.size(); index++) draw.textTracked(lines.get(index), x, baseline + index * lineHeight, size, color, weight, tracking, serif);
    }
    private void titleText(JsonObject title, float x, float baseline, float size, float width) {
        JsonElement name = title.has("richName") ? title.get("richName") : title.get("name");
        if (name != null && name.isJsonObject()) {
            float cursor = x;
            for (HubData.Glyph glyph : HubData.rich(name.getAsJsonObject())) {
                float advance = draw.textWidth(glyph.text(), size, glyph.bold() ? 700 : 400);
                if (cursor + advance > x + width) { draw.text("…", cursor, baseline, size, muted(), 400); break; }
                int color=glyph.color();
                float brightness=(((color>>16)&255)*.2126f+((color>>8)&255)*.7152f+(color&255)*.0722f)/255;
                int outline=!dark&&brightness>.78f?0xb347536b:0;
                draw.richGlyph(glyph.text(), cursor, baseline, size, color, glyph.bold(), glyph.italic(), glyph.underline(), glyph.strike(),outline);
                cursor += advance;
            }
            return;
        }
        String text = fit(titleName(title), width, size, 600);
        String style = string(title, "style", string(title, "id", ""));
        if (style.equals("dew")) draw.textGradient(text, x, baseline, size, 600, 0, 95, new int[]{0xff168878, 0xff69a648}, new float[]{0, 1}, false);
        else if (style.equals("star")) draw.textGradient(text, x, baseline, size, 600, 0, 100, new int[]{0xffa97929, 0xffbf854a, 0xff8a69bb}, new float[]{0, .5f, 1}, false);
        else draw.text(text, x, baseline, size, titleColor(style), 600);
    }
    private int titleColor(String style) {
        return switch (style) { case "wood" -> dark ? 0xffdbaf8c : 0xffa56d40; case "harvest" -> dark ? 0xffdfc66f : 0xffb58b38; case "moon" -> dark ? 0xffbba5ed : 0xff8962c8; case "mountain" -> dark ? 0xffa0c9dd : 0xff507b8c; default -> ink(); };
    }
    private int[] titlePalette(JsonObject title) {
        String style = string(title, "style", string(title, "id", ""));
        if (style.equals("wood") || style.equals("dew")) return dark ? new int[]{0xbd445d53, 0xff303c3d, 0xffa0c5b3} : new int[]{0xffe7f0e8, 0xadcbded3, 0xff739789};
        if (style.equals("harvest")) return dark ? new int[]{0xbd64563e, 0xff3e3b38, 0xffd5bd89} : new int[]{0xfff1e7d1, 0xade6d9b8, 0xffad8c55};
        return dark ? new int[]{0xb3514965, 0xff373549, 0xffbaabda} : new int[]{0xc9e8e1f5, 0x94d3cae9, 0xff8d76b4};
    }
    private static String titleIcon(JsonObject title) { String value = string(title, "icon", "leaf"); return TITLE_ICONS.contains(value) ? value : "leaf"; }
    private static String rarityLabel(JsonObject title) {
        return switch (string(title, "rarity", "")) {
            case "none" -> ""; case "common" -> "普通"; case "rare" -> "稀有"; case "treasured" -> "珍藏";
            default -> string(title, "rarity", "");
        };
    }
    private static String titleName(JsonObject title) {
        JsonElement value = title.has("richName") ? title.get("richName") : title.get("name");
        if (value != null && value.isJsonObject()) {
            StringBuilder name = new StringBuilder(); HubData.rich(value.getAsJsonObject()).forEach(glyph -> name.append(glyph.text())); return name.toString();
        }
        return string(title, "name", string(title, "displayName", ""));
    }
    private static String poem(JsonObject fortune) {
        JsonElement value = fortune.get("poem");
        if (value == null) return "";
        if (value.isJsonArray()) { JsonArray lines = value.getAsJsonArray(); return lines.size() > 1 ? lines.get(1).getAsString() : lines.size() == 1 ? lines.get(0).getAsString() : ""; }
        String[] lines = value.getAsString().split("\n"); return lines.length > 1 ? lines[1] : lines[0];
    }
    private static String acquired(JsonObject item) { return date(item, "obtainedAt", "date"); }
    private static String date(JsonObject item, String timestamp, String fallback) {
        if (item != null && item.has(timestamp) && !item.get(timestamp).isJsonNull()) {
            try { long at = item.get(timestamp).getAsLong(); if (at > 0) return Instant.ofEpochMilli(at).atZone(ZONE).format(DATE); } catch (RuntimeException ignored) { }
        }
        String value = string(item, fallback, "");
        if (value.matches("\\d{4}[-/.]\\d{2}[-/.]\\d{2}")) return value.replace('-', '/').replace('.', '/');
        return "日期未记录";
    }
    private static String period(JsonObject memory) {
        String start = date(memory, "startedAt", "start").replace('/', '.');
        String end = date(memory, "endedAt", "end").replace('/', '.');
        return start + " — " + (end.equals("日期未记录") ? "至今" : end);
    }
    private static String hours(JsonObject memory) {
        if (memory.has("onlineMillis")) { try { return String.format(java.util.Locale.ROOT, "%.1f", memory.get("onlineMillis").getAsDouble() / 3_600_000d); } catch (RuntimeException ignored) { } }
        return string(memory, "hours", "—");
    }
    private static String nullable(JsonObject object, String key) { return object == null || !object.has(key) || object.get(key).isJsonNull() ? null : object.get(key).getAsString(); }
    private static String string(JsonObject object, String key, String fallback) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) return fallback;
        return object.get(key).getAsString();
    }
    private static JsonObject object(JsonObject object, String key) { return object != null && object.has(key) && object.get(key).isJsonObject() ? object.getAsJsonObject(key) : new JsonObject(); }
    private static int integer(JsonObject object, String key, int fallback) { try { return object != null && object.has(key) ? Math.max(0, Math.min(1_000_000, object.get(key).getAsInt())) : fallback; } catch (RuntimeException ignored) { return fallback; } }
    private static boolean bool(JsonObject object, String key, boolean fallback) { try { return object.has(key) ? object.get(key).getAsBoolean() : fallback; } catch (RuntimeException ignored) { return fallback; } }
    private static List<JsonObject> objects(JsonObject object, String key) {
        List<JsonObject> result = new ArrayList<>();
        if (object != null && object.has(key) && object.get(key).isJsonArray()) for (JsonElement item : object.getAsJsonArray(key))
            if (item.isJsonObject()) result.add(item.getAsJsonObject().deepCopy());
        return result;
    }
    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
    private static float curve(float progress) { return HubMotion.bezier(progress, .22f, 1, .36f, 1); }
    private static float progress(long elapsed, long duration) { return clamp((float) elapsed / duration, 0, 1); }
    private static int alpha(int color, float alpha) { return (Math.round((color >>> 24) * clamp(alpha, 0, 1)) << 24) | (color & 0xffffff); }
    public void closeResources() {
        art.close();
        if (quoteLine != null) { quoteLine.close(); quoteLine = null; }
        if (quoteFont != null) { quoteFont.close(); quoteFont = null; }
        if (quoteFace != null) { quoteFace.close(); quoteFace = null; }
    }
    @Override public void close() { clear(); closeResources(); }
}
